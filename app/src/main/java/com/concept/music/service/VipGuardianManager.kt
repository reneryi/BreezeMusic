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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    fun syncAutoClaimPreference(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        prefs?.edit()?.putBoolean("guardian_enabled", enabled)?.apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        val effectiveEnabled = enabled && AppPermissionManager.isPermissionEnabled(AppPermissionType.BACKGROUND_GUARDIAN)
        _state.update { it.copy(enabled = effectiveEnabled) }
        prefs?.edit()?.putBoolean("guardian_enabled", effectiveEnabled)?.apply()
        runCatching { VipManager.accounts.setAuto(effectiveEnabled) }
        if (effectiveEnabled) {
            schedulePeriodicJob(appCtx)
            triggerGuardianNow(appCtx, isManual = false)
        } else {
            val scheduler = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.cancel(JOB_ID_VIP_GUARDIAN)
        }
    }

    fun setAutoReportEightSongs(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        _state.update { it.copy(autoReportEightSongs = false) }
        prefs?.edit()?.putBoolean("auto_report_songs", false)?.apply()
    }

    fun schedulePeriodicJob(context: Context) {
        if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.BACKGROUND_GUARDIAN)) return
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val existing = scheduler.allPendingJobs.any { it.id == JOB_ID_VIP_GUARDIAN }
            if (existing) return
            val component = ComponentName(context, VipGuardianJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID_VIP_GUARDIAN, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6 * 60 * 60 * 1000L)
            runCatching { builder.setPersisted(true) }
            scheduler.schedule(builder.build())
        }
    }

    fun triggerGuardianNow(
        context: Context,
        isManual: Boolean = false,
        onComplete: ((String) -> Unit)? = null
    ): Job? {
        if (_state.value.running) {
            onComplete?.invoke("正在领取中，请稍候")
            return null
        }
        val appCtx = context.applicationContext
        return scope.launch {
            _state.update { it.copy(running = true) }
            val nowStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
            val currentDay = today()

            try {
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
                        val claimRes = VipManager.vip.claim(accountCtx, automatic = !isManual)
                        when (claimRes.outcome) {
                            ClaimOutcome.SUCCESS, ClaimOutcome.ALREADY_DONE -> {
                                prefs?.edit()?.putString("last_run_day", currentDay)?.apply()
                                "今日概念版 VIP 已激活"
                            }
                            else -> claimRes.message
                        }
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    "暂缓：${e.message ?: "网络波动"}"
                }

                val updatedDay = if (summary.contains("已激活")) currentDay else _state.value.lastRunDay
                prefs?.edit()
                    ?.putString("last_run_time", nowStr)
                    ?.putString("last_summary", summary)
                    ?.apply()

                _state.update {
                    it.copy(
                        running = false,
                        lastRunDay = updatedDay,
                        lastRunTimeFormatted = nowStr,
                        lastResultSummary = summary
                    )
                }
                ConceptMusicWidgetUpdater.requestUpdate(appCtx)
                if (onComplete != null) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onComplete(summary)
                    }
                }
            } finally {
                _state.update { it.copy(running = false) }
            }
        }
    }
}

class VipGuardianJobService : JobService() {
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        VipGuardianManager.init(applicationContext)
        if (!VipGuardianManager.state.value.enabled) {
            return false
        }
        activeJob = VipGuardianManager.triggerGuardianNow(applicationContext, isManual = false) {
            jobFinished(params, false)
        }
        return activeJob != null
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        activeJob?.cancel()
        activeJob = null
        return true
    }
}
