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

    /**
     * 播放心跳调用：累加在线播放秒数（内存原子累加 + 3s 防抖异步落盘，避免主线程高频 IO）
     */
    fun recordPlaybackSeconds(seconds: Long, @Suppress("UNUSED_PARAMETER") song: Song? = null) {
        if (currentUserId.isNullOrEmpty()) return
        if (seconds <= 0) return

        pendingDiffSec += seconds
        saveUserData(immediate = false)

        // 内存中乐观更新 UI 听歌时长，让用户看到实时增长
        val currentTotalSec = syncedDsec + pendingDiffSec
        val old = _gradeInfo.value
        if (currentTotalSec > old.totalListenSeconds) {
            _gradeInfo.value = old.copy(
                totalListenSeconds = currentTotalSec,
                totalListenMinutes = currentTotalSec / 60
            )
        }

        // 累计超过 30 秒自动触发一次增量上报
        if (pendingDiffSec >= 30L) {
            scope.launch {
                syncGrade(forceRefresh = false)
            }
        }
    }

    /**
     * 单曲播放结束时调用
     */
    fun onSongCompleted(@Suppress("UNUSED_PARAMETER") song: Song? = null) {
        if (pendingDiffSec > 0) {
            scope.launch {
                syncGrade(forceRefresh = false)
            }
        }
    }

    /**
     * 同步或查询云端听歌等级与数据
     * @param forceRefresh 是否强制向云端刷新基准
     */
    suspend fun syncGrade(forceRefresh: Boolean = false): Result<UserGradeInfo> = withContext(Dispatchers.IO) {
        if (currentUserId.isNullOrEmpty()) return@withContext Result.failure(Exception("请先登录"))
        if (_isSyncing.value) {
            return@withContext Result.success(_gradeInfo.value)
        }

        _isSyncing.value = true
        try {
            // 如果本地基准从未同步过，先查一次服务器当前值作为基准
            if (syncedDsec == 0L || forceRefresh) {
                val query = runCatching { VipManager.music.userGradeInfo() }.getOrNull()
                if (query != null && query.totalListenSeconds > 0L) {
                    syncedDsec = maxOf(syncedDsec, query.totalListenSeconds)
                    saveUserData()
                    _gradeInfo.value = query
                }
            }

            // 如果有增量秒数，发起增量记账上报
            if (pendingDiffSec > 0L) {
                val toReportDiff = pendingDiffSec
                val toReportDsec = syncedDsec + toReportDiff
                val reportResult = VipManager.music.userGradeInfo(dSec = toReportDsec, diffSec = toReportDiff)

                // 结转增量至已同步基准
                val newDsec = maxOf(toReportDsec, reportResult.totalListenSeconds)
                syncedDsec = newDsec
                pendingDiffSec = (pendingDiffSec - toReportDiff).coerceAtLeast(0L)
                saveUserData()

                _gradeInfo.value = reportResult.copy(
                    totalListenSeconds = maxOf(newDsec, reportResult.totalListenSeconds),
                    totalListenMinutes = maxOf(newDsec, reportResult.totalListenSeconds) / 60
                )
                android.util.Log.i("ListeningGrade", "增量同步成功: d_sec=$toReportDsec, diff_sec=$toReportDiff, 当前等级=Lv.${reportResult.level}")
            } else if (!forceRefresh) {
                // 没有增量时常规刷新一次
                val query = runCatching { VipManager.music.userGradeInfo() }.getOrNull()
                if (query != null) {
                    val actualTotalSec = maxOf(syncedDsec, query.totalListenSeconds)
                    _gradeInfo.value = query.copy(
                        totalListenSeconds = actualTotalSec,
                        totalListenMinutes = actualTotalSec / 60
                    )
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
