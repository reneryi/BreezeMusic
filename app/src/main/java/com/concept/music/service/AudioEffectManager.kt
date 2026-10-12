package com.concept.music.service

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReverbMode(val label: String, val preset: Short) {
    NONE("无混响", PresetReverb.PRESET_NONE),
    SMALL_ROOM("小型房间", PresetReverb.PRESET_SMALLROOM),
    MEDIUM_ROOM("中型房间", PresetReverb.PRESET_MEDIUMROOM),
    LARGE_ROOM("大型房间", PresetReverb.PRESET_LARGEROOM),
    MEDIUM_HALL("音乐大厅", PresetReverb.PRESET_MEDIUMHALL),
    LARGE_HALL("混响礼堂", PresetReverb.PRESET_LARGEHALL),
    PLATE("录音室板式", PresetReverb.PRESET_PLATE)
}

enum class EffectPreset(val label: String, val desc: String) {
    OFF("关闭音效", "原汁原味原生输出"),
    FLAT("平直均衡", "全频段平直响应"),
    VIPER_3D("蝰蛇·3D丽音", "增强空间声场与晶莹高音"),
    VIPER_VOCAL("蝰蛇·纯净人声", "突出甜美中频，通透温暖"),
    VIPER_BASS("蝰蛇·超重低音", "下潜深沉有力，节奏澎湃"),
    VIPER_HIFI("蝰蛇·HiFi现场", "开阔现场感与丰富微动态"),
    VIPER_VINYL("蝰蛇·黑胶唱片", "温润复古模拟声，柔和微动态"),
    VIPER_SURROUND("蝰蛇·全景环绕", "360°沉浸式声场，宽阔包裹感"),
    VIPER_DYNAMIC("蝰蛇·动态增强", "宽动态瞬态强化，清晰有力"),
    ROCK("摇滚动感", "V型频响，动感冲击力强"),
    POP("现代流行", "人声与伴奏黄金比例"),
    CUSTOM("自定义调节", "自由调节详细音效参数")
}

data class AudioEffectState(
    val isEnabled: Boolean = false,
    val currentPreset: EffectPreset = EffectPreset.OFF,
    val bassBoostStrength: Int = 0,       // 0 ~ 1000
    val virtualizerStrength: Int = 0,     // 0 ~ 1000
    val loudnessGainMb: Int = 0,          // 0 ~ 1500 mB (0 ~ 15 dB)
    val reverbMode: ReverbMode = ReverbMode.NONE,
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0)
)

object AudioEffectManager {
    private var prefs: SharedPreferences? = null
    private var currentSessionId: Int = 0

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val _state = MutableStateFlow(AudioEffectState())
    val state = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("concept_audio_effects", Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    fun attachSession(sessionId: Int) {
        if (sessionId <= 0) return
        if (sessionId != currentSessionId) {
            releaseEffects()
            currentSessionId = sessionId
        }
        // 仅在用户显式开启音效时才向 AudioFlinger 注册硬件效果器；
        // 默认关闭时不挂载任何 AudioEffect，保持 ExoPlayer AudioTrack 运行在 Android FastMixer (TRACK_FAST) 零抖动直通通路
        applyCurrentState()
    }

    private fun ensureEffectsCreated() {
        val sessionId = currentSessionId
        if (sessionId <= 0) return
        try {
            if (equalizer == null) equalizer = Equalizer(0, sessionId)
            if (bassBoost == null) bassBoost = BassBoost(0, sessionId)
            if (virtualizer == null) virtualizer = Virtualizer(0, sessionId)
            if (loudnessEnhancer == null) runCatching { loudnessEnhancer = LoudnessEnhancer(sessionId) }
        } catch (_: Exception) {
            // 部分定制 ROM 权限保护兜底
        }
    }

    fun setEnabled(enabled: Boolean) {
        val nextPreset = if (enabled && _state.value.currentPreset == EffectPreset.OFF) {
            EffectPreset.VIPER_3D
        } else if (!enabled) {
            EffectPreset.OFF
        } else {
            _state.value.currentPreset
        }
        if (enabled && _state.value.currentPreset == EffectPreset.OFF) {
            setPreset(nextPreset)
            return
        }
        _state.value = _state.value.copy(isEnabled = enabled, currentPreset = nextPreset)
        applyCurrentState()
        saveToPrefs()
    }

    fun setPreset(preset: EffectPreset) {
        // 声学防削波 (Anti-Clipping Headroom) 参数校准：
        // 确保高动态 Hi-Res 与常规音频在提升频响时不超过数字满度 0dBFS，杜绝噼啪杂音与毛刺
        val (bass, virt, loudness, reverb, bands) = when (preset) {
            EffectPreset.OFF -> Tuple5(0, 0, 0, ReverbMode.NONE, listOf(0, 0, 0, 0, 0))
            EffectPreset.FLAT -> Tuple5(0, 0, 0, ReverbMode.NONE, listOf(0, 0, 0, 0, 0))
            EffectPreset.VIPER_3D -> Tuple5(180, 300, 80, ReverbMode.NONE, listOf(60, 20, -40, 60, 90))
            EffectPreset.VIPER_VOCAL -> Tuple5(40, 150, 60, ReverbMode.NONE, listOf(-120, 40, 140, 100, 30))
            EffectPreset.VIPER_BASS -> Tuple5(300, 100, 100, ReverbMode.NONE, listOf(160, 90, -40, -60, 20))
            EffectPreset.VIPER_HIFI -> Tuple5(120, 220, 80, ReverbMode.NONE, listOf(60, 20, 20, 60, 100))
            EffectPreset.VIPER_VINYL -> Tuple5(150, 180, 60, ReverbMode.NONE, listOf(100, 40, 20, 20, -80))
            EffectPreset.VIPER_SURROUND -> Tuple5(200, 450, 100, ReverbMode.NONE, listOf(60, 20, -30, 90, 120))
            EffectPreset.VIPER_DYNAMIC -> Tuple5(260, 260, 120, ReverbMode.NONE, listOf(120, 60, 0, 60, 120))
            EffectPreset.ROCK -> Tuple5(200, 180, 100, ReverbMode.NONE, listOf(100, 60, -80, 60, 100))
            EffectPreset.POP -> Tuple5(120, 150, 60, ReverbMode.NONE, listOf(60, 60, 100, 60, 30))
            EffectPreset.CUSTOM -> Tuple5(_state.value.bassBoostStrength, _state.value.virtualizerStrength, _state.value.loudnessGainMb, _state.value.reverbMode, _state.value.bandLevels)
        }
        _state.value = _state.value.copy(
            isEnabled = preset != EffectPreset.OFF,
            currentPreset = preset,
            bassBoostStrength = bass,
            virtualizerStrength = virt,
            loudnessGainMb = loudness,
            reverbMode = reverb,
            bandLevels = bands
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setCustomBandLevel(bandIndex: Int, levelMb: Int) {
        val currentBands = _state.value.bandLevels.toMutableList()
        if (bandIndex in currentBands.indices) {
            currentBands[bandIndex] = levelMb.coerceIn(-1000, 1000)
            _state.value = _state.value.copy(
                isEnabled = true,
                currentPreset = EffectPreset.CUSTOM,
                bandLevels = currentBands
            )
            applyCurrentState()
            saveToPrefs()
        }
    }

    fun setBassBoostStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(
            isEnabled = true,
            bassBoostStrength = s,
            currentPreset = if (_state.value.currentPreset != EffectPreset.CUSTOM) EffectPreset.CUSTOM else _state.value.currentPreset
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setVirtualizerStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(
            isEnabled = true,
            virtualizerStrength = s,
            currentPreset = if (_state.value.currentPreset != EffectPreset.CUSTOM) EffectPreset.CUSTOM else _state.value.currentPreset
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setLoudnessGain(gainMb: Int) {
        val g = gainMb.coerceIn(0, 1500)
        _state.value = _state.value.copy(
            isEnabled = true,
            loudnessGainMb = g,
            currentPreset = if (_state.value.currentPreset != EffectPreset.CUSTOM) EffectPreset.CUSTOM else _state.value.currentPreset
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setReverbMode(mode: ReverbMode) {
        _state.value = _state.value.copy(
            reverbMode = mode,
            currentPreset = if (_state.value.currentPreset != EffectPreset.CUSTOM) EffectPreset.CUSTOM else _state.value.currentPreset
        )
        applyCurrentState()
        saveToPrefs()
    }

    private fun applyCurrentState() {
        val s = _state.value
        // 当音效关闭或处于原声模式时，彻底释放底层 AudioEffect 实例，让 AudioTrack 回到 FastMixer 硬件直通通道
        if (!s.isEnabled || s.currentPreset == EffectPreset.OFF) {
            releaseEffects()
            return
        }

        ensureEffectsCreated()

        val hasEqSignal = s.bandLevels.any { it != 0 }
        equalizer?.let { eq ->
            runCatching {
                val numBands = eq.numberOfBands.toInt()
                s.bandLevels.forEachIndexed { index, level ->
                    if (index < numBands) {
                        val min = eq.bandLevelRange[0]
                        val max = eq.bandLevelRange[1]
                        val target = level.toShort().coerceIn(min, max)
                        eq.setBandLevel(index.toShort(), target)
                    }
                }
                eq.enabled = hasEqSignal
            }
        }

        bassBoost?.let { bb ->
            runCatching {
                val active = s.bassBoostStrength > 0
                if (active && bb.strengthSupported) {
                    bb.setStrength(s.bassBoostStrength.toShort())
                }
                bb.enabled = active
            }
        }

        virtualizer?.let { vz ->
            runCatching {
                val active = s.virtualizerStrength > 0
                if (active && vz.strengthSupported) {
                    vz.setStrength(s.virtualizerStrength.toShort())
                }
                vz.enabled = active
            }
        }

        loudnessEnhancer?.let { le ->
            runCatching {
                val active = s.loudnessGainMb > 0
                le.setTargetGain(if (active) s.loudnessGainMb else 0)
                le.enabled = active
            }
        }
    }

    private fun releaseEffects() {
        runCatching {
            equalizer?.enabled = false
            equalizer?.release()
        }
        runCatching {
            bassBoost?.enabled = false
            bassBoost?.release()
        }
        runCatching {
            virtualizer?.enabled = false
            virtualizer?.release()
        }
        runCatching {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
    }

    private fun loadFromPrefs() {
        val p = prefs ?: return
        // 默认采用原生直通 (enabled = false, preset = OFF)，消除默认挂载音效引发的 AudioFlinger 欠载哒哒声
        val enabled = p.getBoolean("effect_enabled_v2", false)
        val presetName = p.getString("preset_v2", EffectPreset.OFF.name) ?: EffectPreset.OFF.name
        val preset = runCatching { EffectPreset.valueOf(presetName) }.getOrDefault(EffectPreset.OFF)
        val bass = p.getInt("bass_v2", 0)
        val virt = p.getInt("virt_v2", 0)
        val loudness = p.getInt("loudness_v2", 0)
        val reverbName = p.getString("reverb_v2", ReverbMode.NONE.name) ?: ReverbMode.NONE.name
        val reverb = runCatching { ReverbMode.valueOf(reverbName) }.getOrDefault(ReverbMode.NONE)

        _state.value = AudioEffectState(
            isEnabled = enabled && preset != EffectPreset.OFF,
            currentPreset = if (enabled) preset else EffectPreset.OFF,
            bassBoostStrength = bass,
            virtualizerStrength = virt,
            loudnessGainMb = loudness,
            reverbMode = reverb
        )
    }

    private fun saveToPrefs() {
        val p = prefs ?: return
        p.edit()
            .putBoolean("effect_enabled_v2", _state.value.isEnabled)
            .putString("preset_v2", _state.value.currentPreset.name)
            .putInt("bass_v2", _state.value.bassBoostStrength)
            .putInt("virt_v2", _state.value.virtualizerStrength)
            .putInt("loudness_v2", _state.value.loudnessGainMb)
            .putString("reverb_v2", _state.value.reverbMode.name)
            .apply()
    }
}

private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
