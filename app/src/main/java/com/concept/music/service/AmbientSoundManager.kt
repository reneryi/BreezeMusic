package com.concept.music.service

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 「心流环境音混音舱」实时程序化 DSP 合成引擎 ([INNOV-04])
 * 无需外部音频资源文件，使用 AudioTrack + 数字信号处理滤波算法实时合成 4 路高拟真白噪音环境声，
 * 可与主播放器音乐叠加混音播放，或独立用于专注/助眠场景。
 */
enum class AmbientSoundChannel(
    val id: String,
    val title: String,
    val subtitle: String
) {
    RAIN("rain", "雨夜窗台", "柔和粉红噪底 + 屋檐細雨微脉冲"),
    VINYL("vinyl", "黑胶底噪", "温暖模拟磁带底噪 + 复古黑胶炒豆声"),
    CAMPFIRE("campfire", "林间篝火", "低频暖流烘托 + 松木火星爆裂声"),
    OCEAN("ocean", "深海潮汐", "0.12Hz 慢呼吸海浪涌动与退潮包络")
}

data class AmbientMixerState(
    val masterEnabled: Boolean = false,
    val syncWithMusic: Boolean = true,
    val rainLevel: Float = 0.45f,
    val vinylLevel: Float = 0.25f,
    val campfireLevel: Float = 0.0f,
    val oceanLevel: Float = 0.0f
) {
    val hasActiveChannel: Boolean
        get() = rainLevel > 0.01f || vinylLevel > 0.01f || campfireLevel > 0.01f || oceanLevel > 0.01f

    fun levelFor(channel: AmbientSoundChannel): Float = when (channel) {
        AmbientSoundChannel.RAIN -> rainLevel
        AmbientSoundChannel.VINYL -> vinylLevel
        AmbientSoundChannel.CAMPFIRE -> campfireLevel
        AmbientSoundChannel.OCEAN -> oceanLevel
    }
}

object AmbientSoundManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var synthJob: Job? = null
    private var isMusicCurrentlyPlaying: Boolean = false

    @Volatile
    private var focusPaused: Boolean = false

    @Volatile
    private var focusDucked: Boolean = false

    private var audioFocusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                focusPaused = true
                focusDucked = false
                setMasterEnabled(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusPaused = true
                focusDucked = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                focusDucked = true
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusPaused = false
                focusDucked = false
            }
        }
    }

    private val _state = MutableStateFlow(AmbientMixerState())
    val state = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val p = ctx.getSharedPreferences("concept_ambient_mixer", Context.MODE_PRIVATE)
        prefs = p
        _state.value = AmbientMixerState(
            masterEnabled = p.getBoolean("master_enabled", false),
            syncWithMusic = p.getBoolean("sync_with_music", true),
            rainLevel = p.getFloat("rain_level", 0.45f).coerceIn(0f, 1f),
            vinylLevel = p.getFloat("vinyl_level", 0.25f).coerceIn(0f, 1f),
            campfireLevel = p.getFloat("campfire_level", 0f).coerceIn(0f, 1f),
            oceanLevel = p.getFloat("ocean_level", 0f).coerceIn(0f, 1f)
        )
        evaluateEngineLoop()
    }

    fun onMusicPlayStateChanged(isPlaying: Boolean) {
        if (isMusicCurrentlyPlaying == isPlaying) return
        isMusicCurrentlyPlaying = isPlaying
        evaluateEngineLoop()
    }

    fun setMasterEnabled(enabled: Boolean) {
        _state.update { it.copy(masterEnabled = enabled) }
        savePrefs()
        evaluateEngineLoop()
    }

    fun setSyncWithMusic(sync: Boolean) {
        _state.update { it.copy(syncWithMusic = sync) }
        savePrefs()
        evaluateEngineLoop()
    }

    fun setChannelLevel(channel: AmbientSoundChannel, level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        _state.update { cur ->
            val updated = when (channel) {
                AmbientSoundChannel.RAIN -> cur.copy(rainLevel = clamped)
                AmbientSoundChannel.VINYL -> cur.copy(vinylLevel = clamped)
                AmbientSoundChannel.CAMPFIRE -> cur.copy(campfireLevel = clamped)
                AmbientSoundChannel.OCEAN -> cur.copy(oceanLevel = clamped)
            }
            if (clamped > 0.02f && !updated.masterEnabled) {
                updated.copy(masterEnabled = true)
            } else {
                updated
            }
        }
        savePrefs()
        evaluateEngineLoop()
    }

    fun applyQuickScene(sceneName: String) {
        _state.update { cur ->
            when (sceneName) {
                "rain_study" -> cur.copy(
                    masterEnabled = true,
                    rainLevel = 0.55f,
                    vinylLevel = 0.20f,
                    campfireLevel = 0.0f,
                    oceanLevel = 0.0f
                )
                "retro_vinyl" -> cur.copy(
                    masterEnabled = true,
                    rainLevel = 0.0f,
                    vinylLevel = 0.50f,
                    campfireLevel = 0.15f,
                    oceanLevel = 0.0f
                )
                "camp_night" -> cur.copy(
                    masterEnabled = true,
                    rainLevel = 0.20f,
                    vinylLevel = 0.0f,
                    campfireLevel = 0.60f,
                    oceanLevel = 0.0f
                )
                "deep_ocean" -> cur.copy(
                    masterEnabled = true,
                    rainLevel = 0.15f,
                    vinylLevel = 0.0f,
                    campfireLevel = 0.0f,
                    oceanLevel = 0.65f
                )
                else -> cur
            }
        }
        savePrefs()
        evaluateEngineLoop()
    }

    private fun requestStandaloneAudioFocusIfNeeded(standalone: Boolean) {
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (standalone) {
            focusPaused = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
                    .also { audioFocusRequest = it }
                runCatching { am.requestAudioFocus(req) }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { req -> runCatching { am.abandonAudioFocusRequest(req) } }
            }
            focusPaused = false
            focusDucked = false
        }
    }

    private fun evaluateEngineLoop() {
        val st = _state.value
        val shouldRun = st.masterEnabled && st.hasActiveChannel && (!st.syncWithMusic || isMusicCurrentlyPlaying)
        if (shouldRun) {
            requestStandaloneAudioFocusIfNeeded(!st.syncWithMusic && !isMusicCurrentlyPlaying)
            if (synthJob?.isActive != true) {
                startSynthLoop()
            }
        } else {
            requestStandaloneAudioFocusIfNeeded(false)
            synthJob?.cancel()
            synthJob = null
        }
    }

    private fun buildAudioTrack(sampleRate: Int, minBuf: Int): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }.getOrNull()

    private fun startSynthLoop() {
        synthJob?.cancel()
        synthJob = scope.launch {
            val sampleRate = 24000
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var track = buildAudioTrack(sampleRate, minBuf) ?: return@launch
            var deadObjectRetried = false

            try {
                track.play()
                val buffer = ShortArray(1024)

                var b0 = 0f
                var b1 = 0f
                var b2 = 0f
                var rainLp = 0f
                var dropEnv = 0f
                var vinylPopEnv = 0f
                var vinylHissLp = 0f
                var fireRumble = 0f
                var fireCrackEnv = 0f
                var oceanPhase = 0.0
                var oceanBrown = 0f

                while (isActive) {
                    val s = _state.value
                    if (!s.masterEnabled || !s.hasActiveChannel || (s.syncWithMusic && !isMusicCurrentlyPlaying)) {
                        break
                    }
                    if (focusPaused && !s.syncWithMusic) {
                        delay(200L)
                        continue
                    }

                    val duckScale = if (focusDucked) 0.25f else 1.0f
                    val rGain = s.rainLevel * duckScale
                    val vGain = s.vinylLevel * duckScale
                    val cGain = s.campfireLevel * duckScale
                    val oGain = s.oceanLevel * duckScale

                    for (i in buffer.indices) {
                        val white = Random.nextFloat() * 2f - 1f

                        b0 = 0.99765f * b0 + white * 0.0990460f
                        b1 = 0.96300f * b1 + white * 0.2965164f
                        b2 = 0.57000f * b2 + white * 1.0526913f
                        val pink = (b0 + b1 + b2 + white * 0.1848f) * 0.12f

                        var sample = 0f

                        if (rGain > 0.01f) {
                            rainLp = rainLp * 0.82f + pink * 0.18f
                            if (Random.nextFloat() < 0.0022f) {
                                dropEnv = Random.nextFloat() * 0.35f
                            }
                            dropEnv *= 0.96f
                            val rainSig = rainLp * 0.85f + white * dropEnv * 0.3f
                            sample += rainSig * rGain * 0.32f
                        }

                        if (vGain > 0.01f) {
                            vinylHissLp = vinylHissLp * 0.90f + white * 0.10f
                            if (Random.nextFloat() < 0.0009f) {
                                vinylPopEnv = 0.45f + Random.nextFloat() * 0.45f
                            }
                            vinylPopEnv *= 0.78f
                            val vinylSig = vinylHissLp * 0.18f + (white * vinylPopEnv * 0.55f)
                            sample += vinylSig * vGain * 0.26f
                        }

                        if (cGain > 0.01f) {
                            fireRumble = (fireRumble + white * 0.04f).coerceIn(-0.6f, 0.6f) * 0.985f
                            if (Random.nextFloat() < 0.0014f) {
                                fireCrackEnv = 0.5f + Random.nextFloat() * 0.5f
                            }
                            fireCrackEnv *= 0.91f
                            val fireSig = fireRumble * 0.55f + (white * fireCrackEnv * 0.45f)
                            sample += fireSig * cGain * 0.30f
                        }

                        if (oGain > 0.01f) {
                            oceanPhase += (2.0 * PI * 0.11) / sampleRate
                            if (oceanPhase > 2.0 * PI) oceanPhase -= 2.0 * PI
                            val waveEnv = ((sin(oceanPhase) + 1.0) * 0.5).toFloat()
                            val shapedEnv = 0.18f + 0.82f * (waveEnv * waveEnv)
                            oceanBrown = (oceanBrown + pink * 0.16f).coerceIn(-0.8f, 0.8f) * 0.97f
                            val oceanSig = (oceanBrown * 0.75f + pink * 0.25f * waveEnv) * shapedEnv
                            sample += oceanSig * oGain * 0.35f
                        }

                        val clamped = sample.coerceIn(-0.75f, 0.75f)
                        buffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
                    }

                    val written = track.write(buffer, 0, buffer.size)
                    if (written < 0) {
                        if (written == AudioTrack.ERROR_DEAD_OBJECT && !deadObjectRetried) {
                            deadObjectRetried = true
                            runCatching {
                                track.stop()
                                track.release()
                            }
                            delay(300L)
                            val rebuilt = buildAudioTrack(sampleRate, minBuf)
                            if (rebuilt != null) {
                                track = rebuilt
                                track.play()
                                continue
                            }
                        }
                        _state.update { it.copy(masterEnabled = false) }
                        break
                    } else {
                        deadObjectRetried = false
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    private fun savePrefs() {
        val p = prefs ?: return
        val s = _state.value
        p.edit()
            .putBoolean("master_enabled", s.masterEnabled)
            .putBoolean("sync_with_music", s.syncWithMusic)
            .putFloat("rain_level", s.rainLevel)
            .putFloat("vinyl_level", s.vinylLevel)
            .putFloat("campfire_level", s.campfireLevel)
            .putFloat("ocean_level", s.oceanLevel)
            .apply()
    }
}
