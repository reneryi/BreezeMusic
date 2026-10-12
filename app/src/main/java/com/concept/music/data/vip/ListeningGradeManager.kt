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
 * 1. 负责在播放过程中实时累计有效播放时长并乐观更新 UI；
 * 2. 达到阈值或切歌时自动增量上报官方 /v2/get_grade_info (d_sec + diff_sec)；
 * 3. 听歌等级与 VIP 领取独立解耦；
 * 4. 支持手动“同步云端”一键刷新和记账。
 */
object ListeningGradeManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefs: SharedPreferences? = null

    private var currentUserId: String? = null
    private var syncedDsec: Long = 0L
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
            syncedDsec = 0L
            pendingDiffSec = 0L
            return
        }
        val sp = prefs ?: return
        syncedDsec = sp.getLong("synced_dsec_$userId", 0L)
        pendingDiffSec = sp.getLong("pending_diff_$userId", 0L)
    }

    private fun saveUserData(immediate: Boolean = false) {
        val userId = currentUserId ?: return
        val sp = prefs ?: return
        val dsecSnapshot = syncedDsec
        val diffSnapshot = pendingDiffSec
        if (immediate) {
            sp.edit()
                .putLong("synced_dsec_$userId", dsecSnapshot)
                .putLong("pending_diff_$userId", diffSnapshot)
                .apply()
            return
        }
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(3_000L)
            sp.edit()
                .putLong("synced_dsec_$userId", dsecSnapshot)
                .putLong("pending_diff_$userId", diffSnapshot)
                .apply()
        }
    }

    private var consecutiveReportFailures: Int = 0
    private const val MAX_CHUNK_SECONDS = 300L

    /**
     * 播放心跳调用：累加在线播放秒数（内存原子累加 + 3s 防抖异步落盘，避免主线程高频 IO）
     */
    fun recordPlaybackSeconds(seconds: Long, @Suppress("UNUSED_PARAMETER") song: Song? = null) {
        if (currentUserId.isNullOrEmpty()) return
        if (seconds <= 0) return

        pendingDiffSec += seconds
        saveUserData(immediate = false)

        // 仅在已取得云端权威基准 (syncedDsec > 0) 时乐观叠加待同步增量
        if (syncedDsec > 0L) {
            val currentTotalSec = syncedDsec + pendingDiffSec
            val old = _gradeInfo.value
            if (currentTotalSec != old.totalListenSeconds) {
                _gradeInfo.value = old.copy(
                    totalListenSeconds = currentTotalSec,
                    totalListenMinutes = currentTotalSec / 60
                )
            }
        }

        // 累计达到 60 秒自动触发一次云端增量上报 (对齐 repo_md3 60s 阈值)
        if (pendingDiffSec >= 60L) {
            scope.launch {
                syncGrade(forceRefresh = false)
            }
        }
    }

    /**
     * 单曲播放结束时调用
     */
    fun onSongCompleted(@Suppress("UNUSED_PARAMETER") song: Song? = null) {
        if (pendingDiffSec > 0L) {
            scope.launch {
                syncGrade(forceRefresh = false)
            }
        }
    }

    /**
     * 同步或查询云端听歌等级与数据（严格以云端权威 d_sec 为基准，对齐 repo_md3 listening_grade_service.dart）
     * @param forceRefresh 是否强制向云端刷新基准
     */
    suspend fun syncGrade(forceRefresh: Boolean = false): Result<UserGradeInfo> = withContext(Dispatchers.IO) {
        if (currentUserId.isNullOrEmpty()) return@withContext Result.failure(Exception("请先登录"))
        if (_isSyncing.value) {
            return@withContext Result.success(_gradeInfo.value)
        }

        _isSyncing.value = true
        try {
            // 1. 始终先从云端拉取权威 d_sec 基准（修复旧版本本地缓存虚高导致与官方 App 时长不一致及上报被拒的问题）
            val serverQuery = runCatching { VipManager.music.userGradeInfo() }.getOrNull()
            if (serverQuery != null && (serverQuery.level > 0 || serverQuery.totalListenSeconds > 0L)) {
                syncedDsec = serverQuery.totalListenSeconds
                saveUserData(immediate = true)
                val optimisticSec = syncedDsec + pendingDiffSec
                _gradeInfo.value = serverQuery.copy(
                    totalListenSeconds = optimisticSec,
                    totalListenMinutes = optimisticSec / 60
                )
            }

            // 2. 若存在待上报增量且已拿到云端有效基准，按单次不超过 300 秒向云端增量上报
            if (pendingDiffSec > 0L && syncedDsec > 0L) {
                val toReportDiff = pendingDiffSec.coerceIn(1L, MAX_CHUNK_SECONDS)
                val toReportDsec = syncedDsec + toReportDiff
                val reportResult = VipManager.music.userGradeInfo(dSec = toReportDsec, diffSec = toReportDiff)

                if (reportResult.level > 0 || reportResult.totalListenSeconds > 0L) {
                    // 云端校验通过并采纳增量
                    consecutiveReportFailures = 0
                    val acceptedDsec = if (reportResult.totalListenSeconds >= toReportDsec) {
                        reportResult.totalListenSeconds
                    } else {
                        toReportDsec
                    }
                    syncedDsec = acceptedDsec
                    pendingDiffSec = (pendingDiffSec - toReportDiff).coerceAtLeast(0L)
                    saveUserData(immediate = true)

                    val displaySec = syncedDsec + pendingDiffSec
                    _gradeInfo.value = reportResult.copy(
                        totalListenSeconds = displaySec,
                        totalListenMinutes = displaySec / 60
                    )
                    android.util.Log.i(
                        "ListeningGrade",
                        "云端听歌时长同步成功: d_sec=$acceptedDsec, diff_sec=$toReportDiff, 剩余待报=$pendingDiffSec, 等级=Lv.${reportResult.level}"
                    )
                } else {
                    consecutiveReportFailures += 1
                    android.util.Log.w(
                        "ListeningGrade",
                        "云端拒绝本次增量上报 (failures=$consecutiveReportFailures): d_sec=$toReportDsec, diff_sec=$toReportDiff"
                    )
                    if (consecutiveReportFailures >= 3) {
                        // 连续 3 次被拒则丢弃异常增量，恢复与云端权威时长严格一致
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
            android.util.Log.e("ListeningGrade", "听歌时长同步失败: ${e.message}")
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
}
