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
 * 2. 以官方 /v2/get_grade_info 返回的真实听歌时长（duration 分钟）为显示权威基准，完全与 VIP 领取解耦；
 * 3. 达到阈值或切歌时将实际播放增量上报云端，支持手动“同步云端”刷新。
 */
object ListeningGradeManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefs: SharedPreferences? = null

    private var currentUserId: String? = null
    // 官方 App 展示的真实听歌秒数基准（优先取自接口 duration * 60）
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
        syncedOfficialListenSec = sp.getLong("official_listen_sec_$userId", 0L)
        syncedRawServerDsec = sp.getLong("raw_server_dsec_$userId", syncedOfficialListenSec)
        pendingDiffSec = sp.getLong("pending_diff_v2_$userId", 0L).coerceIn(0L, 600L)
    }

    private fun saveUserData(immediate: Boolean = false) {
        val userId = currentUserId ?: return
        val sp = prefs ?: return
        val officialSnapshot = syncedOfficialListenSec
        val rawDsecSnapshot = syncedRawServerDsec
        val diffSnapshot = pendingDiffSec
        if (immediate) {
            sp.edit()
                .putLong("official_listen_sec_$userId", officialSnapshot)
                .putLong("raw_server_dsec_$userId", rawDsecSnapshot)
                .putLong("pending_diff_v2_$userId", diffSnapshot)
                .apply()
            return
        }
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(3_000L)
            sp.edit()
                .putLong("official_listen_sec_$userId", officialSnapshot)
                .putLong("raw_server_dsec_$userId", rawDsecSnapshot)
                .putLong("pending_diff_v2_$userId", diffSnapshot)
                .apply()
        }
    }

    private var consecutiveReportFailures: Int = 0
    private const val MAX_CHUNK_SECONDS = 300L

    /**
     * 仅在播放器实际正在播放歌曲时由心跳调用（传入当前播放的 song）
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
     * 单曲实际播放结束时调用
     */
    fun onSongCompleted(song: Song? = null) {
        if (song != null && pendingDiffSec > 0L) {
            scope.launch {
                syncGrade(forceRefresh = false)
            }
        }
    }

    /**
     * 同步或查询云端听歌等级与数据（严格以官方 App 实际时长为准，不受 VIP 领取任务影响）
     */
    suspend fun syncGrade(@Suppress("UNUSED_PARAMETER") forceRefresh: Boolean = false): Result<UserGradeInfo> = withContext(Dispatchers.IO) {
        if (currentUserId.isNullOrEmpty()) return@withContext Result.failure(Exception("请先登录"))
        if (_isSyncing.value) {
            return@withContext Result.success(_gradeInfo.value)
        }

        _isSyncing.value = true
        try {
            // 1. 从云端拉取权威时长（优先采用官方 App 展示的 duration 分钟字段）
            val serverQuery = runCatching { VipManager.music.userGradeInfo() }.getOrNull()
            if (serverQuery != null && (serverQuery.level > 0 || serverQuery.totalListenSeconds > 0L)) {
                syncedOfficialListenSec = serverQuery.totalListenSeconds
                syncedRawServerDsec = VipManager.music.lastServerRawDsec.takeIf { it > 0L } ?: serverQuery.totalListenSeconds
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
                    val updatedOfficialSec = if (reportResult.totalListenSeconds >= syncedOfficialListenSec) {
                        maxOf(reportResult.totalListenSeconds, syncedOfficialListenSec + toReportDiff)
                    } else {
                        syncedOfficialListenSec + toReportDiff
                    }
                    syncedOfficialListenSec = updatedOfficialSec
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
                            _gradeInfo.value = serverQuery
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
