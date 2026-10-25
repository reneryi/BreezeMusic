package com.concept.music.data.vip

import android.content.Context
import android.content.SharedPreferences
import com.concept.music.data.kugou.AuthState
import com.concept.music.data.model.Song
import com.concept.music.data.model.UserGradeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 听歌时长与等级同步管理器：
 * 1. 严格仅在实际播放音乐过程中累计有效播放秒数并更新 UI；
 * 2. 以官方 /v3/get_my_info (module/user_detail.js) 与 /v2/get_grade_info (module/user_grade_info.js) 返回的真实听歌时长（r_duration / duration 分钟）为显示权威基准，完全与 VIP 领取解耦；
 * 3. 多账号切换时通过快照比对与锁保护严防 A 账号在途增量写入 B 账号。
 */
object ListeningGradeManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateLock = Any()
    private val syncMutex = Mutex()
    private var prefs: SharedPreferences? = null

    @Volatile
    private var currentUserId: String? = null
    private var syncedOfficialListenSec: Long = 0L
    private var syncedRawServerDsec: Long = 0L
    private var pendingDiffSec: Long = 0L

    private val _gradeInfo = MutableStateFlow(UserGradeInfo())
    val gradeInfo = _gradeInfo.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("listening_grade_prefs", Context.MODE_PRIVATE)
        scope.launch {
            VipManager.accounts.state.collect { state ->
                val newId = state.currentId
                if (newId != currentUserId) {
                    synchronized(stateLock) {
                        currentUserId = newId
                        loadUserDataLocked(newId)
                    }
                    if (state.auth == AuthState.READY && !newId.isNullOrEmpty()) {
                        syncGrade(forceRefresh = true)
                    } else if (newId.isNullOrEmpty()) {
                        _gradeInfo.value = UserGradeInfo()
                    }
                }
            }
        }
    }

    private var saveJob: Job? = null

    private fun loadUserDataLocked(userId: String?) {
        if (userId.isNullOrEmpty()) {
            syncedOfficialListenSec = 0L
            syncedRawServerDsec = 0L
            pendingDiffSec = 0L
            _gradeInfo.value = UserGradeInfo()
            return
        }
        val sp = prefs ?: return
        syncedOfficialListenSec = sp.getLong("official_listen_sec_v5_$userId", 0L)
        syncedRawServerDsec = sp.getLong("raw_server_dsec_v5_$userId", syncedOfficialListenSec)
        pendingDiffSec = sp.getLong("pending_diff_v5_$userId", 0L).coerceIn(0L, 600L)
        if (syncedOfficialListenSec > 0L) {
            _gradeInfo.update { it.withUpdatedSeconds(syncedOfficialListenSec + pendingDiffSec) }
        } else {
            _gradeInfo.value = UserGradeInfo()
        }
    }

    private fun saveUserDataForUser(
        userId: String,
        officialSnapshot: Long,
        rawDsecSnapshot: Long,
        diffSnapshot: Long,
        immediate: Boolean = false
    ) {
        if (userId.isBlank()) return
        val sp = prefs ?: return
        if (immediate) {
            sp.edit()
                .putLong("official_listen_sec_v5_$userId", officialSnapshot)
                .putLong("raw_server_dsec_v5_$userId", rawDsecSnapshot)
                .putLong("pending_diff_v5_$userId", diffSnapshot)
                .apply()
            return
        }
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(3_000L)
            sp.edit()
                .putLong("official_listen_sec_v5_$userId", officialSnapshot)
                .putLong("raw_server_dsec_v5_$userId", rawDsecSnapshot)
                .putLong("pending_diff_v5_$userId", diffSnapshot)
                .apply()
        }
    }

    fun onOfficialProfileSynced(userId: String, officialDurationMinutes: Long, officialLevel: Int = 0) {
        if (userId.isBlank() || officialDurationMinutes <= 0L) return
        synchronized(stateLock) {
            if (currentUserId == null) {
                currentUserId = userId
            }
            if (currentUserId != userId) return
            val profileSec = if (officialDurationMinutes > 2_000_000L) officialDurationMinutes else officialDurationMinutes * 60L
            val remainderSec = if (syncedOfficialListenSec > 0L && syncedOfficialListenSec / 60L == profileSec / 60L) {
                syncedOfficialListenSec % 60L
            } else 0L
            val newOfficialSec = maxOf(syncedOfficialListenSec, profileSec + remainderSec)
            syncedOfficialListenSec = newOfficialSec
            if (syncedRawServerDsec <= 0L) {
                syncedRawServerDsec = newOfficialSec
            }
            saveUserDataForUser(userId, syncedOfficialListenSec, syncedRawServerDsec, pendingDiffSec, immediate = true)
            val totalSec = newOfficialSec + pendingDiffSec
            _gradeInfo.update { cur ->
                val updatedLevel = if (officialLevel > 0) maxOf(cur.level, officialLevel) else cur.level
                cur.copy(level = updatedLevel).withUpdatedSeconds(totalSec)
            }
        }
    }

    private var consecutiveReportFailures: Int = 0
    private const val MAX_CHUNK_SECONDS = 300L

    fun recordPlaybackSeconds(seconds: Long, song: Song? = null) {
        if (song == null || seconds <= 0L) return
        val shouldSync: Boolean
        synchronized(stateLock) {
            val uid = currentUserId ?: return
            if (uid.isEmpty()) return
            val clampedSec = seconds.coerceAtMost(5L)
            pendingDiffSec += clampedSec
            saveUserDataForUser(uid, syncedOfficialListenSec, syncedRawServerDsec, pendingDiffSec, immediate = false)
            if (syncedOfficialListenSec > 0L) {
                val currentTotalSec = syncedOfficialListenSec + pendingDiffSec
                _gradeInfo.update { old ->
                    if (currentTotalSec != old.totalListenSeconds) old.withUpdatedSeconds(currentTotalSec) else old
                }
            }
            shouldSync = pendingDiffSec >= 60L
        }
        if (shouldSync) {
            scope.launch {
                syncGrade(forceRefresh = false)
            }
        }
    }

    fun onSongCompleted(song: Song? = null) {
        if (song != null) {
            scope.launch {
                runCatching { VipManager.music.uploadPlayHistory(song) }
                val hasPending = synchronized(stateLock) { pendingDiffSec > 0L }
                if (hasPending) {
                    syncGrade(forceRefresh = false)
                }
            }
        }
    }

    suspend fun syncGrade(forceRefresh: Boolean = false): Result<UserGradeInfo> = withContext(Dispatchers.IO) {
        val targetUserId = synchronized(stateLock) { currentUserId }
        if (targetUserId.isNullOrEmpty()) return@withContext Result.failure(Exception("请先登录"))
        if (_isSyncing.value && !forceRefresh) {
            return@withContext Result.success(_gradeInfo.value)
        }

        syncMutex.withLock {
            if (synchronized(stateLock) { currentUserId } != targetUserId) {
                return@withLock Result.failure(CancellationException("账号已切换"))
            }
            _isSyncing.value = true
            try {
                val serverQuery = runCatching { VipManager.music.userGradeInfo() }.getOrNull()
                if (synchronized(stateLock) { currentUserId } != targetUserId) {
                    return@withLock Result.failure(CancellationException("账号已切换"))
                }
                if (serverQuery != null && (serverQuery.level > 0 || serverQuery.totalListenSeconds > 0L)) {
                    synchronized(stateLock) {
                        if (currentUserId == targetUserId) {
                            val remainderSec = if (syncedOfficialListenSec > 0L &&
                                syncedOfficialListenSec / 60L == serverQuery.totalListenSeconds / 60L
                            ) {
                                syncedOfficialListenSec % 60L
                            } else 0L
                            val candidateSec = serverQuery.totalListenSeconds + remainderSec
                            syncedOfficialListenSec = if (syncedOfficialListenSec > candidateSec && syncedOfficialListenSec - candidateSec <= 36_000L) {
                                syncedOfficialListenSec
                            } else {
                                candidateSec
                            }
                            syncedRawServerDsec = VipManager.music.lastServerRawDsec.takeIf { it > 0L } ?: syncedOfficialListenSec
                            saveUserDataForUser(targetUserId, syncedOfficialListenSec, syncedRawServerDsec, pendingDiffSec, immediate = true)
                            val optimisticSec = syncedOfficialListenSec + pendingDiffSec
                            _gradeInfo.value = serverQuery.withUpdatedSeconds(optimisticSec)
                        }
                    }
                }

                val (toReportDiff, toReportDsec) = synchronized(stateLock) {
                    if (currentUserId == targetUserId && pendingDiffSec > 0L && syncedRawServerDsec > 0L) {
                        val diff = pendingDiffSec.coerceIn(1L, MAX_CHUNK_SECONDS)
                        diff to (syncedRawServerDsec + diff)
                    } else 0L to 0L
                }

                if (toReportDiff > 0L && toReportDsec > 0L) {
                    val reportResult = VipManager.music.userGradeInfo(dSec = toReportDsec, diffSec = toReportDiff)
                    synchronized(stateLock) {
                        if (currentUserId == targetUserId) {
                            if (reportResult.level > 0 || reportResult.totalListenSeconds > 0L) {
                                consecutiveReportFailures = 0
                                syncedRawServerDsec = VipManager.music.lastServerRawDsec.takeIf { it >= toReportDsec } ?: toReportDsec
                                val advancedOfficialSec = syncedOfficialListenSec + toReportDiff
                                syncedOfficialListenSec = maxOf(advancedOfficialSec, reportResult.totalListenSeconds)
                                pendingDiffSec = (pendingDiffSec - toReportDiff).coerceAtLeast(0L)
                                saveUserDataForUser(targetUserId, syncedOfficialListenSec, syncedRawServerDsec, pendingDiffSec, immediate = true)
                                val displaySec = syncedOfficialListenSec + pendingDiffSec
                                _gradeInfo.value = reportResult.withUpdatedSeconds(displaySec)
                            } else {
                                consecutiveReportFailures += 1
                                if (consecutiveReportFailures >= 3) {
                                    consecutiveReportFailures = 0
                                    pendingDiffSec = 0L
                                    saveUserDataForUser(targetUserId, syncedOfficialListenSec, syncedRawServerDsec, pendingDiffSec, immediate = true)
                                    if (serverQuery != null && serverQuery.totalListenSeconds > 0L) {
                                        _gradeInfo.value = serverQuery.withUpdatedSeconds(syncedOfficialListenSec)
                                    }
                                }
                            }
                        }
                    }
                }

                Result.success(_gradeInfo.value)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
