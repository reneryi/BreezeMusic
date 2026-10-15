package com.concept.music.service

import android.content.Context
import android.content.SharedPreferences
import com.concept.music.data.kugou.today
import com.concept.music.data.vip.ListeningGradeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [创新1] 「概念胶囊工作台 (Concept Focus Pod)」
 * 番茄钟 × 概念版频道 × 程序化环境白噪音 × 酷狗听歌等级时长四位一体引擎
 */
enum class FocusPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val minutes: Int,
    val youthChannelId: String,
    val ambientSceneKey: String
) {
    POMODORO_25("pomo_25", "25分钟 · 标准心流番茄", "深度心流轻音 + 雨夜自习白噪音", 25, "ch_study", "rain_study"),
    DEEP_STUDY_45("deep_45", "45分钟 · 沉浸代码研习", "放空自愈旋律 + 温暖黑胶底噪", 45, "ch_healing", "retro_vinyl"),
    NIGHT_READ_30("night_30", "30分钟 · 深夜静读伴眠", "晚安梦游纯音 + 深海潮汐呼吸", 30, "ch_sleep", "deep_ocean"),
    CAFE_FLOW_60("cafe_60", "60分钟 · 街角黑胶漫游", "独立爵士慢调 + 林间篝火暖流", 60, "ch_cafe", "camp_night")
}

data class FocusPodState(
    val isRunning: Boolean = false,
    val selectedPreset: FocusPreset = FocusPreset.POMODORO_25,
    val remainingSeconds: Long = 25 * 60L,
    val totalSeconds: Long = 25 * 60L,
    val autoMixAmbient: Boolean = true,
    val autoPlayYouthChannel: Boolean = true,
    val completedPodsToday: Int = 0,
    val totalFocusMinutesToday: Int = 0
) {
    val progressFraction: Float
        get() = if (totalSeconds > 0L) {
            ((totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedRemaining: String
        get() {
            val mins = remainingSeconds / 60L
            val secs = remainingSeconds % 60L
            return String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)
        }
}

object FocusPodManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var prefs: SharedPreferences? = null
    private var timerJob: Job? = null

    private val _state = MutableStateFlow(FocusPodState())
    val state = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("concept_focus_pod", Context.MODE_PRIVATE)
        prefs = p
        val savedDay = p.getString("stats_day", "") ?: ""
        val currentDay = today()
        val podsToday = if (savedDay == currentDay) p.getInt("pods_today", 0) else 0
        val minsToday = if (savedDay == currentDay) p.getInt("mins_today", 0) else 0
        val autoAmbient = p.getBoolean("auto_ambient", true)
        val autoChannel = p.getBoolean("auto_channel", true)

        _state.value = FocusPodState(
            autoMixAmbient = autoAmbient,
            autoPlayYouthChannel = autoChannel,
            completedPodsToday = podsToday,
            totalFocusMinutesToday = minsToday
        )
    }

    fun selectPreset(preset: FocusPreset) {
        if (_state.value.isRunning) return
        _state.value = _state.value.copy(
            selectedPreset = preset,
            remainingSeconds = preset.minutes * 60L,
            totalSeconds = preset.minutes * 60L
        )
    }

    fun setAutoMixAmbient(enabled: Boolean) {
        _state.value = _state.value.copy(autoMixAmbient = enabled)
        prefs?.edit()?.putBoolean("auto_ambient", enabled)?.apply()
    }

    fun setAutoPlayYouthChannel(enabled: Boolean) {
        _state.value = _state.value.copy(autoPlayYouthChannel = enabled)
        prefs?.edit()?.putBoolean("auto_channel", enabled)?.apply()
    }

    fun startFocusPod(onPlayChannelRequested: ((String) -> Unit)? = null) {
        timerJob?.cancel()
        val preset = _state.value.selectedPreset
        val durationSec = preset.minutes * 60L
        _state.value = _state.value.copy(
            isRunning = true,
            totalSeconds = durationSec,
            remainingSeconds = durationSec
        )

        if (_state.value.autoMixAmbient) {
            AmbientSoundManager.setSyncWithMusic(false)
            AmbientSoundManager.applyQuickScene(preset.ambientSceneKey)
        }
        if (_state.value.autoPlayYouthChannel) {
            onPlayChannelRequested?.invoke(preset.youthChannelId)
        }

        timerJob = scope.launch {
            var elapsedSec = 0L
            while (_state.value.remainingSeconds > 0L) {
                delay(1_000L)
                val nextRem = (_state.value.remainingSeconds - 1L).coerceAtLeast(0L)
                elapsedSec += 1L
                _state.value = _state.value.copy(remainingSeconds = nextRem)

                // 每满 60 秒自动累计一次专注时长并同步至酷狗听歌等级管理器
                if (elapsedSec % 60L == 0L) {
                    val newMins = _state.value.totalFocusMinutesToday + 1
                    _state.value = _state.value.copy(totalFocusMinutesToday = newMins)
                    saveStats(_state.value.completedPodsToday, newMins)
                    ListeningGradeManager.recordPlaybackSeconds(60L)
                }
            }

            // 番茄钟完成：平滑关闭独立环境音并记录完成次数
            val newPods = _state.value.completedPodsToday + 1
            _state.value = _state.value.copy(
                isRunning = false,
                remainingSeconds = preset.minutes * 60L,
                completedPodsToday = newPods
            )
            saveStats(newPods, _state.value.totalFocusMinutesToday)
            if (_state.value.autoMixAmbient) {
                AmbientSoundManager.setSyncWithMusic(true)
                AmbientSoundManager.setMasterEnabled(false)
            }
        }
    }

    fun stopFocusPod() {
        timerJob?.cancel()
        timerJob = null
        val preset = _state.value.selectedPreset
        _state.value = _state.value.copy(
            isRunning = false,
            remainingSeconds = preset.minutes * 60L
        )
        if (_state.value.autoMixAmbient) {
            AmbientSoundManager.setSyncWithMusic(true)
            AmbientSoundManager.setMasterEnabled(false)
        }
    }

    private fun saveStats(pods: Int, mins: Int) {
        prefs?.edit()
            ?.putString("stats_day", today())
            ?.putInt("pods_today", pods)
            ?.putInt("mins_today", mins)
            ?.apply()
    }
}
