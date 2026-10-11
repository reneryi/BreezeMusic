package com.concept.music.service

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EffectPreset(val label: String, val desc: String) {
    OFF("关闭音效", "原汁原味原生输出"),
    FLAT("平直均衡", "全频段平直响应"),
    VIPER_3D("蝰蛇·3D丽音", "增强空间声场与晶莹高音"),
    VIPER_VOCAL("蝰蛇·纯净人声", "突出甜美中频，通透温暖"),
    VIPER_BASS("蝰蛇·超重低音", "下潜深沉有力，节奏澎湃"),
    VIPER_HIFI("蝰蛇·HiFi现场", "开阔现场感与丰富微动态"),
    ROCK("摇滚动感", "V型频响，动感冲击力强"),
    POP("现代流行", "人声与伴奏黄金比例"),
    CUSTOM("自定义调节", "自由调节5频段均衡")
}

data class AudioEffectState(
    val isEnabled: Boolean = true,
    val currentPreset: EffectPreset = EffectPreset.VIPER_3D,
    val bassBoostStrength: Int = 500,     // 0 ~ 1000
    val virtualizerStrength: Int = 600,   // 0 ~ 1000
    val bandLevels: List<Int> = listOf(200, 100, 0, 150, 300) // 5个频段增益 (毫贝尔 mB, -1000 ~ +1000)
)

object AudioEffectManager {
    private var prefs: SharedPreferences? = null
    private var currentSessionId: Int = 0

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val _state = MutableStateFlow(AudioEffectState())
    val state = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("concept_audio_effects", Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    fun attachSession(sessionId: Int) {
        if (sessionId <= 0) return
        if (sessionId == currentSessionId && equalizer != null && bassBoost != null && virtualizer != null) return
        releaseEffects()
        currentSessionId = sessionId
        try {
            // 使用 priority = 100 获取优先音频效果器控制权，避免被系统默认效果器拦截
            equalizer = Equalizer(100, sessionId)
            bassBoost = BassBoost(100, sessionId)
            virtualizer = Virtualizer(100, sessionId)
            applyCurrentState()
        } catch (_: Exception) {
            // 部分定制 ROM 权限保护兜底
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = enabled)
        applyCurrentState()
        saveToPrefs()
    }

    fun setPreset(preset: EffectPreset) {
        // 声学防削波 (Anti-Clipping Headroom) 参数校准：
        // 确保高动态 Hi-Res 与常规音频在提升频响时不超过数字满度 0dBFS，杜绝噼啪杂音与毛刺
        val (bass, virt, bands) = when (preset) {
            EffectPreset.OFF -> Triple(0, 0, listOf(0, 0, 0, 0, 0))
            EffectPreset.FLAT -> Triple(0, 0, listOf(0, 0, 0, 0, 0))
            EffectPreset.VIPER_3D -> Triple(250, 450, listOf(100, 50, 0, 100, 150))
            EffectPreset.VIPER_VOCAL -> Triple(50, 200, listOf(-100, 50, 200, 150, 50))
            EffectPreset.VIPER_BASS -> Triple(400, 150, listOf(250, 150, 0, -50, 50))
            EffectPreset.VIPER_HIFI -> Triple(150, 300, listOf(100, 50, 50, 100, 150))
            EffectPreset.ROCK -> Triple(250, 250, listOf(150, 100, -50, 100, 150))
            EffectPreset.POP -> Triple(150, 200, listOf(100, 100, 150, 100, 50))
            EffectPreset.CUSTOM -> Triple(_state.value.bassBoostStrength, _state.value.virtualizerStrength, _state.value.bandLevels)
        }
        _state.value = _state.value.copy(
            isEnabled = preset != EffectPreset.OFF,
            currentPreset = preset,
            bassBoostStrength = bass,
            virtualizerStrength = virt,
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
            bassBoostStrength = s,
            currentPreset = if (_state.value.currentPreset != EffectPreset.CUSTOM) EffectPreset.CUSTOM else _state.value.currentPreset
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setVirtualizerStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(
            virtualizerStrength = s,
            currentPreset = if (_state.value.currentPreset != EffectPreset.CUSTOM) EffectPreset.CUSTOM else _state.value.currentPreset
        )
        applyCurrentState()
        saveToPrefs()
    }

    private fun applyCurrentState() {
        val s = _state.value

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
                eq.enabled = s.isEnabled
            }
        }

        bassBoost?.let { bb ->
            runCatching {
                if (bb.strengthSupported) {
                    bb.setStrength(s.bassBoostStrength.toShort())
                }
                bb.enabled = s.isEnabled && s.bassBoostStrength > 0
            }
        }

        virtualizer?.let { vz ->
            runCatching {
                if (vz.strengthSupported) {
                    vz.setStrength(s.virtualizerStrength.toShort())
                }
                vz.enabled = s.isEnabled && s.virtualizerStrength > 0
            }
        }
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    private fun loadFromPrefs() {
        val p = prefs ?: return
        val enabled = p.getBoolean("enabled", true)
        val presetName = p.getString("preset", EffectPreset.VIPER_3D.name) ?: EffectPreset.VIPER_3D.name
        val preset = runCatching { EffectPreset.valueOf(presetName) }.getOrDefault(EffectPreset.VIPER_3D)
        val bass = p.getInt("bass", 400)
        val virt = p.getInt("virt", 700)
        _state.value = AudioEffectState(
            isEnabled = enabled,
            currentPreset = preset,
            bassBoostStrength = bass,
            virtualizerStrength = virt
        )
    }

    private fun saveToPrefs() {
        val p = prefs ?: return
        p.edit()
            .putBoolean("enabled", _state.value.isEnabled)
            .putString("preset", _state.value.currentPreset.name)
            .putInt("bass", _state.value.bassBoostStrength)
            .putInt("virt", _state.value.virtualizerStrength)
            .apply()
    }
}
