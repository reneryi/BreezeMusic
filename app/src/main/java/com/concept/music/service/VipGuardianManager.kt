package com.concept.music.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import com.concept.music.data.kugou.ClaimOutcome
import com.concept.music.data.kugou.today
import com.concept.music.data.vip.ListeningGradeManager
import com.concept.music.data.vip.VipManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VipGuardianState(
    val enabled: Boolean = true,
    val autoReportEightSongs: Boolean = true,
    val running: Boolean = false,
    val lastRunDay: String = "",
    val lastRunTimeFormatted: String = "尚未执行",
    val lastResultSummary: String = "待触发每日静默领 VIP 守护"
)

/**
 * 全自动静默养号守护管理器 ([INNOV-03])
 * 结合 Android JobScheduler 周期后台任务与应用启动自检：
 * 每日自动执行概念版 VIP 签到领取 + 升级 SVIP + 8 首听歌任务上报，无需手动点开福利中心。
 */
object VipGuardianManager {
    private const val JOB_ID_VIP_GUARDIAN = 311601
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(VipGuardianState())
    val state = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val appCtx = context.applicationContext
        val p = appCtx.getSharedPreferences("concept_vip_guardian", Context.MODE_PRIVATE)
        prefs = p
        val enabled = p.getBoolean("guardian_enabled", true)
        val autoSongs = p.getBoolean("auto_report_songs", true)
        val lastDay = p.getString("last_run_day", "") ?: ""
        val lastTime = p.getString("last_run_time", "尚未执行") ?: "尚未执行"
        val lastSummary = p.getString("last_summary", "待触发每日静默领 VIP 守护") ?: "待触发每日静默领 VIP 守护"

        _state.value = VipGuardianState(
            enabled = enabled,
            autoReportEightSongs = autoSongs,
            running = false,
            lastRunDay = lastDay,
            lastRunTimeFormatted = lastTime,
            lastResultSummary = lastSummary
        )

        if (enabled) {
            schedulePeriodicJob(appCtx)
            // 若今日尚未成功执行过静默守护，则在后台协程静默触发一次检查
            val currentDay = today()
            if (lastDay != currentDay) {
                triggerGuardianNow(appCtx, isManual = false)
            }
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        _state.value = _state.value.copy(enabled = enabled)
        prefs?.edit()?.putBoolean("guardian_enabled", enabled)?.apply()
        if (enabled) {
            schedulePeriodicJob(appCtx)
            triggerGuardianNow(appCtx, isManual = false)
        } else {
            val scheduler = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.cancel(JOB_ID_VIP_GUARDIAN)
        }
    }

    fun setAutoReportEightSongs(enabled: Boolean) {
        _state.value = _state.value.copy(autoReportEightSongs = enabled)
        prefs?.edit()?.putBoolean("auto_report_songs", enabled)?.apply()
    }

    fun schedulePeriodicJob(context: Context) {
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val existing = scheduler.allPendingJobs.any { it.id == JOB_ID_VIP_GUARDIAN }
            if (existing) return
            val component = ComponentName(context, VipGuardianJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID_VIP_GUARDIAN, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6 * 60 * 60 * 1000L) // 每 6 小时巡检一次当日权益
                .build()
            scheduler.schedule(jobInfo)
        }
    }

    fun triggerGuardianNow(
        context: Context,
        isManual: Boolean = false,
        onComplete: ((String) -> Unit)? = null
    ) {
        if (_state.value.running) {
            onComplete?.invoke("静默养号守护正在执行中，请稍候")
            return
        }
        val appCtx = context.applicationContext
        scope.launch {
            _state.value = _state.value.copy(running = true)
            val nowStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
            val currentDay = today()

            val summary = try {
                VipManager.initialize(appCtx)
                val accountCtx = VipManager.accounts.context()
                if (accountCtx == null) {
                    "未登录酷狗账号，待登录后自动守护"
                } else {
                    val claimRes = VipManager.vip.claim(accountCtx, automatic = !isManual)
                    if (_state.value.autoReportEightSongs) {
                        runCatching {
                            val dailySongs = VipManager.music.dailyRecommend().take(8)
                            dailySongs.forEach { song ->
                                VipManager.music.uploadPlayHistory(song)
                                ListeningGradeManager.onSongCompleted(song)
                            }
                        }
                    }
                    when (claimRes.outcome) {
                        ClaimOutcome.SUCCESS, ClaimOutcome.ALREADY_DONE -> {
                            prefs?.edit()?.putString("last_run_day", currentDay)?.apply()
                            "今日概念版 VIP 已激活 · 听歌满级任务已同步"
                        }
                        else -> claimRes.message
                    }
                }
            } catch (e: Exception) {
                "静默巡检暂缓：${e.message ?: "网络波动"}"
            }

            val updatedDay = if (summary.contains("已激活")) currentDay else _state.value.lastRunDay
            prefs?.edit()
                ?.putString("last_run_time", nowStr)
                ?.putString("last_summary", summary)
                ?.apply()

            _state.value = _state.value.copy(
                running = false,
                lastRunDay = updatedDay,
                lastRunTimeFormatted = nowStr,
                lastResultSummary = summary
            )
            ConceptMusicWidgetUpdater.requestUpdate(appCtx)
            if (onComplete != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(summary)
                }
            }
        }
    }
}

/**
 * 系统级 JobService 守护进程，由 Android JobScheduler 定时唤醒执行
 */
class VipGuardianJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        VipGuardianManager.init(applicationContext)
        if (!VipGuardianManager.state.value.enabled) {
            return false
        }
        VipGuardianManager.triggerGuardianNow(applicationContext, isManual = false) {
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
