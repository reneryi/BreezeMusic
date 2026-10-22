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
    val autoReportEightSongs: Boolean = false,
    val running: Boolean = false,
    val lastRunDay: String = "",
    val lastRunTimeFormatted: String = "尚未执行",
    val lastResultSummary: String = "待触发每日自动领 VIP"
)

/**
 * 概念版 VIP 自动领取守护管理器：
 * 严格遵循「VIP 领取只负责将 VIP 权益正确领取，绝不伪造听歌记录或影响听歌时长与其他设置」的原则，
 * 彻底移除刷歌打卡、播放历史上传与听歌时长触发逻辑，并与账号级 autoClaim 开关保持双向同步。
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
        val accountAutoClaim = runCatching { VipManager.accounts.state.value.current?.autoClaim }.getOrNull()
        val enabled = accountAutoClaim ?: p.getBoolean("guardian_enabled", true)
        if (accountAutoClaim != null) {
            p.edit().putBoolean("guardian_enabled", accountAutoClaim).apply()
        }
        val lastDay = p.getString("last_run_day", "") ?: ""
        val lastTime = p.getString("last_run_time", "尚未执行") ?: "尚未执行"
        val lastSummary = p.getString("last_summary", "待触发每日自动领 VIP") ?: "待触发每日自动领 VIP"

        _state.value = VipGuardianState(
            enabled = enabled,
            autoReportEightSongs = false,
            running = false,
            lastRunDay = lastDay,
            lastRunTimeFormatted = lastTime,
            lastResultSummary = lastSummary
        )

        if (enabled) {
            schedulePeriodicJob(appCtx)
        } else {
            val scheduler = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.cancel(JOB_ID_VIP_GUARDIAN)
        }
    }

    /**
     * 与 VIP 权益中心「打开应用时自动领取」开关同步，无需显式传入 Context
     */
    fun syncAutoClaimPreference(enabled: Boolean) {
        _state.value = _state.value.copy(enabled = enabled)
        prefs?.edit()?.putBoolean("guardian_enabled", enabled)?.apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        _state.value = _state.value.copy(enabled = enabled)
        prefs?.edit()?.putBoolean("guardian_enabled", enabled)?.apply()
        runCatching { VipManager.accounts.setAuto(enabled) }
        if (enabled) {
            schedulePeriodicJob(appCtx)
            triggerGuardianNow(appCtx, isManual = false)
        } else {
            val scheduler = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.cancel(JOB_ID_VIP_GUARDIAN)
        }
    }

    fun setAutoReportEightSongs(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        _state.value = _state.value.copy(autoReportEightSongs = false)
        prefs?.edit()?.putBoolean("auto_report_songs", false)?.apply()
    }

    fun schedulePeriodicJob(context: Context) {
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val existing = scheduler.allPendingJobs.any { it.id == JOB_ID_VIP_GUARDIAN }
            if (existing) return
            val component = ComponentName(context, VipGuardianJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID_VIP_GUARDIAN, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6 * 60 * 60 * 1000L)
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
            onComplete?.invoke("正在领取中，请稍候")
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
                    "未登录账号"
                } else if (!isManual && !(VipManager.accounts.state.value.current?.autoClaim ?: _state.value.enabled)) {
                    "自动领取已关闭"
                } else if (VipManager.vip.state.value.busy) {
                    "正在同步 VIP 权益"
                } else {
                    // 仅执行纯净的概念版 VIP 权益领取，不触发任何刷歌或听歌时长修改
                    val claimRes = VipManager.vip.claim(accountCtx, automatic = !isManual)
                    when (claimRes.outcome) {
                        ClaimOutcome.SUCCESS, ClaimOutcome.ALREADY_DONE -> {
                            prefs?.edit()?.putString("last_run_day", currentDay)?.apply()
                            "今日概念版 VIP 已激活"
                        }
                        else -> claimRes.message
                    }
                }
            } catch (e: Exception) {
                "暂缓：${e.message ?: "网络波动"}"
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
