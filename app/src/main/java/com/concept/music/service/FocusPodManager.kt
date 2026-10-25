package com.concept.music.service

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.concept.music.data.kugou.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FocusPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val minutes: Int,
    val sceneId: String,
    val ambientSceneKey: String
) {
    POMODORO_25("pomo_25", "25分钟 · 标准专注", "心流轻音 · 雨夜窗台", 25, "STUDY", "rain_study"),
    DEEP_STUDY_45("deep_45", "45分钟 · 深度研习", "自愈旋律 · 黑胶底噪", 45, "HEALING", "retro_vinyl"),
    NIGHT_READ_30("night_30", "30分钟 · 静读伴眠", "晚安纯音 · 深海潮汐", 30, "SLEEP", "deep_ocean"),
    CAFE_FLOW_60("cafe_60", "60分钟 · 慢调漫游", "慵懒爵士 · 林间篝火", 60, "CAFE", "camp_night")
}

data class FocusPodState(
    val isRunning: Boolean = false,
    val selectedPreset: FocusPreset = FocusPreset.POMODORO_25,
    val remainingSeconds: Long = 25 * 60L,
    val totalSeconds: Long = 25 * 60L,
    val autoMixAmbient: Boolean = true,
    val autoPlaySceneMusic: Boolean = true,
    val completedPodsToday: Int = 0,
    val totalFocusMinutesToday: Int = 0,
    val activeTracks: List<String> = emptyList()
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

    private fun nowElapsedMs(): Long =
        runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L).takeIf { it > 0L } ?: System.currentTimeMillis()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("concept_focus_pod", Context.MODE_PRIVATE)
        prefs = p
        val savedDay = p.getString("stats_day", "") ?: ""
        val currentDay = today()
        val podsToday = if (savedDay == currentDay) p.getInt("pods_today", 0) else 0
        val minsToday = if (savedDay == currentDay) p.getInt("mins_today", 0) else 0
        val autoAmbient = p.getBoolean("auto_ambient", true)
        val autoScene = p.getBoolean("auto_scene_music", true)

        _state.value = FocusPodState(
            autoMixAmbient = autoAmbient,
            autoPlaySceneMusic = autoScene,
            completedPodsToday = podsToday,
            totalFocusMinutesToday = minsToday
        )
    }

    fun selectPreset(preset: FocusPreset) {
        if (_state.value.isRunning) return
        _state.update {
            it.copy(
                selectedPreset = preset,
                remainingSeconds = preset.minutes * 60L,
                totalSeconds = preset.minutes * 60L
            )
        }
    }

    fun setAutoMixAmbient(enabled: Boolean) {
        _state.update { it.copy(autoMixAmbient = enabled) }
        prefs?.edit()?.putBoolean("auto_ambient", enabled)?.apply()
    }

    fun setAutoPlaySceneMusic(enabled: Boolean) {
        _state.update { it.copy(autoPlaySceneMusic = enabled) }
        prefs?.edit()?.putBoolean("auto_scene_music", enabled)?.apply()
    }

    fun startFocusPod(onPlaySceneRequested: ((String) -> Unit)? = null) {
        timerJob?.cancel()
        val preset = _state.value.selectedPreset
        val durationSec = preset.minutes * 60L
        _state.update {
            it.copy(
                isRunning = true,
                totalSeconds = durationSec,
                remainingSeconds = durationSec
            )
        }

        if (_state.value.autoMixAmbient) {
            AmbientSoundManager.setSyncWithMusic(false)
            AmbientSoundManager.applyQuickScene(preset.ambientSceneKey)
        }
        if (_state.value.autoPlaySceneMusic) {
            onPlaySceneRequested?.invoke(preset.sceneId)
        }

        timerJob = scope.launch {
            val startMs = nowElapsedMs()
            val endMs = startMs + durationSec * 1000L
            var recordedMinutes = 0L

            while (_state.value.remainingSeconds > 0L) {
                delay(1_000L)
                val now = nowElapsedMs()
                val nextRem = if (now > startMs) {
                    ((endMs - now + 999L) / 1000L).coerceIn(0L, durationSec)
                } else {
                    (_state.value.remainingSeconds - 1L).coerceAtLeast(0L)
                }
                val elapsedMinutes = (durationSec - nextRem) / 60L
                val deltaMinutes = (elapsedMinutes - recordedMinutes).coerceAtLeast(0L).toInt()
                if (deltaMinutes > 0) {
                    recordedMinutes = elapsedMinutes
                }
                _state.update { cur ->
                    val newMins = cur.totalFocusMinutesToday + deltaMinutes
                    if (deltaMinutes > 0) saveStats(cur.completedPodsToday, newMins)
                    cur.copy(remainingSeconds = nextRem, totalFocusMinutesToday = newMins)
                }
            }

            _state.update { cur ->
                val newPods = cur.completedPodsToday + 1
                saveStats(newPods, cur.totalFocusMinutesToday)
                cur.copy(
                    isRunning = false,
                    remainingSeconds = preset.minutes * 60L,
                    completedPodsToday = newPods
                )
            }
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
        _state.update {
            it.copy(
                isRunning = false,
                remainingSeconds = preset.minutes * 60L
            )
        }
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
