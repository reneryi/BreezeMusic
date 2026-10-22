package com.concept.music.data.vip

import android.content.Context
import android.content.SharedPreferences
import com.concept.music.data.kugou.AuthState
import com.concept.music.data.model.Song
import com.concept.music.data.model.UserGradeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 听歌时长与等级同步管理器：
 * 1. 严格仅在实际播放音乐过程中累计有效播放秒数并更新 UI；
 * 2. 以官方 /v3/get_my_info (module/user_detail.js) 与 /v2/get_grade_info (module/user_grade_info.js) 返回的真实听歌时长（r_duration / duration 分钟）为显示权威基准，完全与 VIP 领取解耦；
 * 3. 达到阈值或切歌时将实际播放增量上报云端，并同步上报云端播放历史，支持手动“同步云端”刷新。
 */
object ListeningGradeManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefs: SharedPreferences? = null

    private var currentUserId: String? = null
    // 官方 App 展示的真实听歌秒数基准（严格取自官方账号 r_duration/duration * 60 + 真实播放增量）
    private var syncedOfficialListenSec: Long = 0L
    // 接口底层用于校验上报的 d_sec 基准
    private var syncedRawServerDsec: Long = 0L
    // 仅在播放器实际播放音乐时累加的待上报秒数
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
                    currentUserId = newId
                    loadUserData(newId)
                    if (state.auth == AuthState.READY) {
                        syncGrade(forceRefresh = true)
                    } else {
                        _gradeInfo.value = UserGradeInfo()
                    }
                }
            }
        }
    }

    private var saveJob: Job? = null

    private fun loadUserData(userId: String?) {
        if (userId.isNullOrEmpty()) {
            syncedOfficialListenSec = 0L
            syncedRawServerDsec = 0L
            pendingDiffSec = 0L
            return
        }
        val sp = prefs ?: return
        // 使用 v4 键名隔离旧版本因 /v3/get_my_info 缺少 visit_time/usertype 导致回退到较低 grade_info 时长的旧缓存
        syncedOfficialListenSec = sp.getLong("official_listen_sec_v4_$userId", 0L)
        syncedRawServerDsec = sp.getLong("raw_server_dsec_v4_$userId", syncedOfficialListenSec)
        pendingDiffSec = sp.getLong("pending_diff_v4_$userId", 0L).coerceIn(0L, 600L)
        if (syncedOfficialListenSec > 0L) {
            _gradeInfo.value = _gradeInfo.value.withUpdatedSeconds(syncedOfficialListenSec + pendingDiffSec)
        }
    }

    private fun saveUserData(immediate: Boolean = false) {
        val userId = currentUserId ?: return
        val sp = prefs ?: return
        val officialSnapshot = syncedOfficialListenSec
        val rawDsecSnapshot = syncedRawServerDsec
        val diffSnapshot = pendingDiffSec
        if (immediate) {
            sp.edit()
                .putLong("official_listen_sec_v4_$userId", officialSnapshot)
                .putLong("raw_server_dsec_v4_$userId", rawDsecSnapshot)
                .putLong("pending_diff_v4_$userId", diffSnapshot)
                .apply()
            return
        }
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(3_000L)
            sp.edit()
                .putLong("official_listen_sec_v4_$userId", officialSnapshot)
                .putLong("raw_server_dsec_v4_$userId", rawDsecSnapshot)
                .putLong("pending_diff_v4_$userId", diffSnapshot)
                .apply()
        }
    }

    /**
     * 当 AccountRepository.refreshProfile() 从 /v3/get_my_info 获取到官方个人资料卡的听歌时长（分钟）时，
     * 立即校准本地权威听歌时长基准，消除与官方 App 个人资料页的分钟数偏差。
     */
    fun onOfficialProfileSynced(userId: String, officialDurationMinutes: Long, officialLevel: Int = 0) {
        if (userId.isBlank() || officialDurationMinutes <= 0L) return
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
        saveUserData(immediate = true)
        val cur = _gradeInfo.value
        val updatedLevel = if (officialLevel > 0) maxOf(cur.level, officialLevel) else cur.level
        _gradeInfo.value = cur.copy(level = updatedLevel).withUpdatedSeconds(newOfficialSec + pendingDiffSec)
    }

    private var consecutiveReportFailures: Int = 0
    private const val MAX_CHUNK_SECONDS = 300L

    /**
     * 仅在播放器实际正在播放歌曲时由心跳调用（传入当前播放的 song）
     * VIP 领取任务不会调用此函数，保证时长增加只与实际播放歌曲有关。
     */
    fun recordPlaybackSeconds(seconds: Long, song: Song? = null) {
        if (currentUserId.isNullOrEmpty()) return
        if (song == null || seconds <= 0L) return

        val clampedSec = seconds.coerceAtMost(5L)
        pendingDiffSec += clampedSec
        saveUserData(immediate = false)

        if (syncedOfficialListenSec > 0L) {
            val currentTotalSec = syncedOfficialListenSec + pendingDiffSec
            val old = _gradeInfo.value
            if (currentTotalSec != old.totalListenSeconds) {
                _gradeInfo.value = old.withUpdatedSeconds(currentTotalSec)
            }
        }

        if (pendingDiffSec >= 60L) {
            scope.launch {
                syncGrade(forceRefresh = false)
            }
        }
    }

    /**
     * 单曲实际播放结束时调用：同步待上报听歌秒数与云端听歌历史
     */
    fun onSongCompleted(song: Song? = null) {
        if (song != null) {
            scope.launch {
                runCatching { VipManager.music.uploadPlayHistory(song) }
                if (pendingDiffSec > 0L) {
                    syncGrade(forceRefresh = false)
                }
            }
        }
    }

    /**
     * 同步或查询云端听歌等级与数据（严格以官方账号实际 r_duration / duration 分钟数为准，完全不受 VIP 领取任务影响）
     */
    suspend fun syncGrade(forceRefresh: Boolean = false): Result<UserGradeInfo> = withContext(Dispatchers.IO) {
        if (currentUserId.isNullOrEmpty()) return@withContext Result.failure(Exception("请先登录"))
        if (_isSyncing.value && !forceRefresh) {
            return@withContext Result.success(_gradeInfo.value)
        }

        _isSyncing.value = true
        try {
            // 1. 从云端拉取权威时长（内部同时融合 /v3/get_my_info、/v1/get_my_userinfo 与 /v2/get_grade_info 的官方 r_duration / duration 字段）
            val serverQuery = runCatching { VipManager.music.userGradeInfo() }.getOrNull()
            if (serverQuery != null && (serverQuery.level > 0 || serverQuery.totalListenSeconds > 0L)) {
                val remainderSec = if (syncedOfficialListenSec > 0L &&
                    syncedOfficialListenSec / 60L == serverQuery.totalListenSeconds / 60L
                ) {
                    syncedOfficialListenSec % 60L
                } else 0L
                val candidateSec = serverQuery.totalListenSeconds + remainderSec
                // 若此前已从 /v3/get_my_info 获取到更高的官方个人资料卡分钟数（如 220904 分钟），防止因单次请求波动回退到较低的 grade_info 时长（如 220630 分钟）
                syncedOfficialListenSec = if (syncedOfficialListenSec > candidateSec && syncedOfficialListenSec - candidateSec <= 36_000L) {
                    syncedOfficialListenSec
                } else {
                    candidateSec
                }
                syncedRawServerDsec = VipManager.music.lastServerRawDsec.takeIf { it > 0L } ?: syncedOfficialListenSec
                saveUserData(immediate = true)
                val optimisticSec = syncedOfficialListenSec + pendingDiffSec
                _gradeInfo.value = serverQuery.withUpdatedSeconds(optimisticSec)
            }

            // 2. 若有真实播放产生的待上报增量，向云端增量同步
            if (pendingDiffSec > 0L && syncedRawServerDsec > 0L) {
                val toReportDiff = pendingDiffSec.coerceIn(1L, MAX_CHUNK_SECONDS)
                val toReportDsec = syncedRawServerDsec + toReportDiff
                val reportResult = VipManager.music.userGradeInfo(dSec = toReportDsec, diffSec = toReportDiff)

                if (reportResult.level > 0 || reportResult.totalListenSeconds > 0L) {
                    consecutiveReportFailures = 0
                    syncedRawServerDsec = VipManager.music.lastServerRawDsec.takeIf { it >= toReportDsec } ?: toReportDsec
                    // 仅将本次实际播放的秒数增量累加至权威时长，或采纳更高的官方返回时长
                    val advancedOfficialSec = syncedOfficialListenSec + toReportDiff
                    syncedOfficialListenSec = maxOf(advancedOfficialSec, reportResult.totalListenSeconds)
                    pendingDiffSec = (pendingDiffSec - toReportDiff).coerceAtLeast(0L)
                    saveUserData(immediate = true)

                    val displaySec = syncedOfficialListenSec + pendingDiffSec
                    _gradeInfo.value = reportResult.withUpdatedSeconds(displaySec)
                } else {
                    consecutiveReportFailures += 1
                    if (consecutiveReportFailures >= 3) {
                        consecutiveReportFailures = 0
                        pendingDiffSec = 0L
                        saveUserData(immediate = true)
                        if (serverQuery != null && serverQuery.totalListenSeconds > 0L) {
                            _gradeInfo.value = serverQuery.withUpdatedSeconds(syncedOfficialListenSec)
                        }
                    }
                }
            }

            Result.success(_gradeInfo.value)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
}
