package com.concept.music.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.concept.music.MainActivity
import com.concept.music.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 音频焦点与多应用并发模式（三档可选）：
 * 1. EXCLUSIVE：独占焦点（默认），其他应用播放音视频时自动暂停本应用，导航/短通知音自动闪避；
 * 2. DUCK：压低音量同播（智能闪避），其他应用播放音频时不暂停本应用，而是将本应用音量平滑压低至 30% 作为背景伴奏，对方结束后恢复 100%；
 * 3. MIX：完全混音同播，不抢占音频焦点且不压低音量，与其他应用保持 100% 原音量同时播放。
 */
enum class AudioFocusMode(val label: String, val subtitle: String) {
    EXCLUSIVE("独占焦点", "其他应用播放音视频时自动暂停，导航与通知音自动闪避"),
    DUCK("压低音量同播", "其他应用发声时不暂停音乐，自动压低本应用音量至 30% 伴奏"),
    MIX("完全混音同播", "完全不抢占焦点，与其他应用保持 100% 原音量同时播放")
}

/**
 * 有线耳机线控连击灵敏度（多击判定时间窗）
 */
enum class HeadsetClickSensitivity(val label: String, val subtitle: String, val timeoutMs: Long) {
    FAST("紧凑 280ms", "响应干脆，适合快速连按", 280L),
    STANDARD("标准 350ms", "默认均衡手感", 350L),
    RELAXED("宽松 450ms", "适合偏硬按键或慢速连按", 450L)
}

/**
 * 有线耳机与媒体按键多击线控状态机：
 * 解决 AndroidX Media3 默认对有线耳机单键 (`KEYCODE_HEADSETHOOK`) 双击/三击无法切歌的问题，
 * 支持自定义连击灵敏度（280ms / 350ms / 450ms）与双击/三击切歌方向交换：
 * - 单击暂停/中键：播放 / 暂停切换
 * - 双击暂停/中键：默认切换下一首（开启方向交换后为切换上一首）
 * - 三击暂停/中键：默认切换上一首（开启方向交换后为切换下一首）
 * - 独立上下曲键 (`KEYCODE_MEDIA_NEXT` / `KEYCODE_MEDIA_PREVIOUS`)：立即切歌
 */
class HeadsetMediaButtonController(
    multiClickTimeoutMs: Long = 350L,
    private val duplicateGuardWindowMs: Long = 25L,
    private val tripleClickCooldownMs: Long = 220L,
    private val nowMsProvider: () -> Long = {
        runCatching { SystemClock.uptimeMillis() }.getOrDefault(0L).takeIf { it > 0L }
            ?: System.currentTimeMillis()
    },
    private val scheduleDelayed: (delayMs: Long, action: () -> Unit) -> Unit,
    private val cancelScheduled: () -> Unit,
    private val onTogglePlay: () -> Unit,
    private val onSkipToNext: () -> Unit,
    private val onSkipToPrevious: () -> Unit
) {
    @Volatile
    var multiClickTimeoutMs: Long = multiClickTimeoutMs

    @Volatile
    var multiClickSkipEnabled: Boolean = true

    @Volatile
    var swapDoubleTripleClick: Boolean = false

    private var pendingClickCount: Int = 0
    private var lastDownTimeMs: Long = -1L
    private var lastKeyCode: Int = -1
    private var lastHandledAtMs: Long = -1L
    private var lastTripleClickAtMs: Long = -1L

    fun isSupportedMediaKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> true
        else -> false
    }

    private fun isToggleOrHookKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE -> true
        else -> false
    }

    private fun triggerDoubleClickAction() {
        if (swapDoubleTripleClick) {
            onSkipToPrevious()
        } else {
            onSkipToNext()
        }
    }

    private fun triggerTripleClickAction() {
        if (swapDoubleTripleClick) {
            onSkipToNext()
        } else {
            onSkipToPrevious()
        }
    }

    fun handleKeyEvent(
        keyCode: Int,
        action: Int,
        repeatCount: Int = 0,
        downTimeMs: Long = 0L
    ): Boolean {
        if (!isSupportedMediaKey(keyCode)) return false
        if (action == KeyEvent.ACTION_UP) return true
        if (action != KeyEvent.ACTION_DOWN) return true
        if (repeatCount > 0) return true

        val now = nowMsProvider()
        if (downTimeMs > 0L && downTimeMs == lastDownTimeMs && keyCode == lastKeyCode) {
            return true
        }
        if (now > 0L && lastHandledAtMs > 0L &&
            (now - lastHandledAtMs) in 0L until duplicateGuardWindowMs &&
            keyCode == lastKeyCode
        ) {
            return true
        }
        lastDownTimeMs = downTimeMs
        lastKeyCode = keyCode
        lastHandledAtMs = now

        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                cancelScheduled()
                pendingClickCount = 0
                onSkipToNext()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                cancelScheduled()
                pendingClickCount = 0
                onSkipToPrevious()
                return true
            }
        }

        if (isToggleOrHookKey(keyCode)) {
            if (!multiClickSkipEnabled) {
                cancelScheduled()
                pendingClickCount = 0
                onTogglePlay()
                return true
            }
            if (now > 0L && lastTripleClickAtMs > 0L && (now - lastTripleClickAtMs) in 0L until tripleClickCooldownMs) {
                return true
            }

            cancelScheduled()
            pendingClickCount += 1
            if (pendingClickCount >= 3) {
                pendingClickCount = 0
                lastTripleClickAtMs = now
                triggerTripleClickAction()
            } else {
                val windowMs = multiClickTimeoutMs.coerceIn(180L, 800L)
                scheduleDelayed(windowMs) {
                    val count = pendingClickCount
                    pendingClickCount = 0
                    when (count) {
                        1 -> onTogglePlay()
                        2 -> triggerDoubleClickAction()
                        else -> if (count >= 3) triggerTripleClickAction()
                    }
                }
            }
            return true
        }
        return false
    }

    fun reset() {
        cancelScheduled()
        pendingClickCount = 0
        lastDownTimeMs = -1L
        lastKeyCode = -1
        lastHandledAtMs = -1L
        lastTripleClickAtMs = -1L
    }
}

/**
 * 64-bit 双精度系数双二阶滤波器 (Direct Form II Transposed Biquad Filter)
 * 用于 10 段参量均衡器 (PEQ)、重低音截止谐振 (45/60/85Hz)、人声通透激励与胆机温暖度塑形
 */
internal class BiquadStereoFilter {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var isBypass = true

    private var z1L = 0.0
    private var z2L = 0.0
    private var z1R = 0.0
    private var z2R = 0.0

    fun resetState() {
        z1L = 0.0
        z2L = 0.0
        z1R = 0.0
        z2R = 0.0
    }

    fun setBypass() {
        b0 = 1.0
        b1 = 0.0
        b2 = 0.0
        a1 = 0.0
        a2 = 0.0
        isBypass = true
    }

    fun configurePeaking(freqHz: Double, sampleRate: Int, gainDb: Double, q: Double = 1.414) {
        if (abs(gainDb) < 0.05 || sampleRate <= 0) {
            setBypass()
            return
        }
        val nyquist = sampleRate * 0.475
        val fc = freqHz.coerceIn(18.0, nyquist)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.25))

        val a0 = 1.0 + alpha / a
        b0 = (1.0 + alpha * a) / a0
        b1 = (-2.0 * cosW0) / a0
        b2 = (1.0 - alpha * a) / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha / a) / a0
        isBypass = false
    }

    fun configureLowShelf(freqHz: Double, sampleRate: Int, gainDb: Double, slope: Double = 0.90) {
        if (abs(gainDb) < 0.05 || sampleRate <= 0) {
            setBypass()
            return
        }
        val nyquist = sampleRate * 0.475
        val fc = freqHz.coerceIn(18.0, nyquist)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = (sinW0 / 2.0) * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0
        b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)) / a0
        b2 = (a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0
        a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosW0)) / a0
        a2 = ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
        isBypass = false
    }

    fun configureHighShelf(freqHz: Double, sampleRate: Int, gainDb: Double, slope: Double = 0.85) {
        if (abs(gainDb) < 0.05 || sampleRate <= 0) {
            setBypass()
            return
        }
        val nyquist = sampleRate * 0.475
        val fc = freqHz.coerceIn(200.0, nyquist)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = (sinW0 / 2.0) * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0
        b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)) / a0
        b2 = (a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0
        a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosW0)) / a0
        a2 = ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
        isBypass = false
    }

    fun processLeft(x: Float): Float {
        if (isBypass) return x
        val inD = x.toDouble()
        val y = b0 * inD + z1L
        z1L = b1 * inD - a1 * y + z2L
        z2L = b2 * inD - a2 * y
        return y.toFloat()
    }

    fun processRight(x: Float): Float {
        if (isBypass) return x
        val inD = x.toDouble()
        val y = b0 * inD + z1R
        z1R = b1 * inD - a1 * y + z2R
        z2R = b2 * inD - a2 * y
        return y.toFloat()
    }
}

/**
 * 概念版发烧级 64-bit 实时 PCM 母带音频处理器 (ConceptMasteringAudioProcessor v1.0.1)：
 * 直接在 Media3 音频解码管线内对 16-bit / 24-bit / 32-bit / Float PCM 实施全套数字信号处理，
 * 彻底解决 Android 系统硬件 AudioEffect 在无损 FLAC 与不同机型上效果微弱或失效的问题：
 * 1. 10 段双精度 Biquad 参量均衡器 (31Hz ~ 16kHz 独立双二阶滤波器级联)
 * 2. 动态超重低音引擎：支持 45Hz(深潜) / 60Hz(均衡) / 85Hz(紧凑) 频点谐振 + 偶/奇次亚低频泛音激励
 * 3. 人声通透度引擎：2.8kHz 贴耳存在感提亮 + 260Hz 鼻音去浊 + 9.5kHz 极高频空气感泛音
 * 4. 黑胶与黄金胆机温暖度引擎：175Hz 饱满下盘 + 甲类三极管非对称偶次谐波软饱和润色
 * 5. 3D 环绕声场引擎：低频锚定 M/S 声场扩展 + Haas 跨声道微延迟空间拓宽 + 360° 动态轨迹环绕
 * 6. 录音室立体声混响网络：内置 Schroeder 4并联梳状 + 2级全通扩散混响，支持小型房间/中型房间/大型房间/音乐大厅/混响礼堂/录音室板式 6 大空间声学模型
 * 7. 等响度听感曲线补偿 (Fletcher-Munson)、前置放大增益、动态响度增益、左右声道平衡与软膝防削波母带限幅器
 */
@OptIn(UnstableApi::class)
class ConceptMasteringAudioProcessor : BaseAudioProcessor() {
    private val eqFilters = Array(10) { BiquadStereoFilter() }
    private val bassShelfFilter = BiquadStereoFilter()
    private val bassPunchFilter = BiquadStereoFilter()
    private val vocalMudCutFilter = BiquadStereoFilter()
    private val vocalPresenceFilter = BiquadStereoFilter()
    private val vocalAirShelfFilter = BiquadStereoFilter()
    private val warmthBodyFilter = BiquadStereoFilter()

    private var lastConfigVersion: Long = -1L
    private var lastConfiguredSampleRate: Int = -1

    private var rotationPhase = 0.0
    private var smoothedEnvelope = 0.22f
    private var currentNormGain = 1.0f
    private var smoothLeftGain = 1.0f
    private var smoothRightGain = 1.0f

    // 低频追踪器（用于亚低频泛音合成与 M/S 低频居中保护）
    private var subLowpassL = 0f
    private var subLowpassR = 0f
    private var sideLowpass = 0f

    // Haas 跨声道空间微延迟环状缓冲（用于拓宽单声道/窄声场曲目的 3D 立体声结像）
    private val maxHaasSamples = 256
    private val haasBufferL = FloatArray(maxHaasSamples)
    private val haasBufferR = FloatArray(maxHaasSamples)
    private var haasWriteIdx = 0
    private var activeHaasDelay = 28

    // 立体声 Schroeder/Freeverb 房间/大厅混响缓冲（4 组立体声阻尼梳状滤波器 + 2 组全通扩散器）
    private val baseCombLengths = intArrayOf(1116, 1188, 1277, 1356)
    private val baseAllpassLengths = intArrayOf(556, 441)
    private val combBuffersL = Array(4) { FloatArray(4096) }
    private val combBuffersR = Array(4) { FloatArray(4096) }
    private val combFilterStoreL = FloatArray(4)
    private val combFilterStoreR = FloatArray(4)
    private val combWriteIdxL = IntArray(4)
    private val combWriteIdxR = IntArray(4)
    private val combEffectiveLenL = IntArray(4) { 1116 }
    private val combEffectiveLenR = IntArray(4) { 1139 }

    private val allpassBuffersL = Array(2) { FloatArray(2048) }
    private val allpassBuffersR = Array(2) { FloatArray(2048) }
    private val allpassWriteIdxL = IntArray(2)
    private val allpassWriteIdxR = IntArray(2)
    private val allpassEffectiveLenL = IntArray(2) { 556 }
    private val allpassEffectiveLenR = IntArray(2) { 579 }

    private var cachedLinearPreampAndLoudnessGain = 1.0f
    private var cachedSubHarmonicDrive = 0f
    private var cachedSubLowpassAlpha = 0.008f
    private var cachedSideLowpassAlpha = 0.018f
    private var cachedWarmthDrive = 0f
    private var cachedWidthFactor = 0f
    private var cachedHaasCrossMix = 0f
    private var cachedReverbMode = ReverbMode.NONE

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val validEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_32BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val validChannels = inputAudioFormat.channelCount == 1 || inputAudioFormat.channelCount == 2
        return if (validEncoding && validChannels) {
            lastConfigVersion = -1L
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun onFlush() {
        rotationPhase = 0.0
        smoothedEnvelope = 0.22f
        currentNormGain = 1.0f
        smoothLeftGain = 1.0f
        smoothRightGain = 1.0f
        subLowpassL = 0f
        subLowpassR = 0f
        sideLowpass = 0f
        haasWriteIdx = 0
        haasBufferL.fill(0f)
        haasBufferR.fill(0f)
        eqFilters.forEach { it.resetState() }
        bassShelfFilter.resetState()
        bassPunchFilter.resetState()
        vocalMudCutFilter.resetState()
        vocalPresenceFilter.resetState()
        vocalAirShelfFilter.resetState()
        warmthBodyFilter.resetState()
        clearReverbBuffers()
    }

    private fun clearReverbBuffers() {
        for (i in 0 until 4) {
            combBuffersL[i].fill(0f)
            combBuffersR[i].fill(0f)
            combFilterStoreL[i] = 0f
            combFilterStoreR[i] = 0f
            combWriteIdxL[i] = 0
            combWriteIdxR[i] = 0
        }
        for (i in 0 until 2) {
            allpassBuffersL[i].fill(0f)
            allpassBuffersR[i].fill(0f)
            allpassWriteIdxL[i] = 0
            allpassWriteIdxR[i] = 0
        }
    }

    override fun onReset() {
        onFlush()
        lastConfigVersion = -1L
        lastConfiguredSampleRate = -1
    }

    private fun updateDspCoefficientsIfNeeded(state: AudioEffectState, sampleRate: Int) {
        val currentVer = AudioEffectManager.dspConfigVersion.get()
        if (currentVer == lastConfigVersion && sampleRate == lastConfiguredSampleRate) return
        lastConfigVersion = currentVer
        lastConfiguredSampleRate = sampleRate

        val sr = sampleRate.coerceAtLeast(44100)
        val dspActive = state.isEnabled && state.currentPreset != EffectPreset.OFF

        if (!dspActive && !state.equalLoudnessCompensation) {
            eqFilters.forEach { it.setBypass() }
            bassShelfFilter.setBypass()
            bassPunchFilter.setBypass()
            vocalMudCutFilter.setBypass()
            vocalPresenceFilter.setBypass()
            vocalAirShelfFilter.setBypass()
            warmthBodyFilter.setBypass()
            cachedLinearPreampAndLoudnessGain = 1.0f
            cachedSubHarmonicDrive = 0f
            cachedWarmthDrive = 0f
            cachedWidthFactor = 0f
            cachedHaasCrossMix = 0f
            cachedReverbMode = ReverbMode.NONE
            return
        }

        // 1. 配置 10 段参量均衡器 Biquad 级联 (31Hz ~ 16kHz)
        val tenBands = state.tenBandLevels
        var maxPositiveEqDb = 0.0
        for (i in 0 until 10) {
            val freq = AudioEffectManager.TEN_BAND_CENTER_HZ[i]
            val rawMb = if (dspActive && i < tenBands.size) tenBands[i] else 0
            val gainDb = (rawMb / 100.0).coerceIn(-12.0, 12.0)
            if (gainDb > maxPositiveEqDb) maxPositiveEqDb = gainDb
            when (i) {
                0 -> eqFilters[i].configureLowShelf(freq, sr, gainDb, slope = 0.85)
                9 -> eqFilters[i].configureHighShelf(freq, sr, gainDb, slope = 0.85)
                else -> eqFilters[i].configurePeaking(freq, sr, gainDb, q = 1.35)
            }
        }

        // 2. 配置重低音下潜与低频频点滤波器 (45Hz 深潜 / 60Hz 均衡 / 85Hz 紧凑) + 等响度低频补偿
        val bassNorm = if (dspActive) (state.bassBoostStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val cutoffHz = state.bassCutoffHz.coerceIn(40, 120).toDouble()
        val loudnessLowDb = if (state.equalLoudnessCompensation) 4.2 else 0.0
        val shelfGainDb = bassNorm * (if (cutoffHz <= 50.0) 8.5 else if (cutoffHz <= 65.0) 7.5 else 6.5) + loudnessLowDb
        val punchGainDb = bassNorm * (if (cutoffHz <= 50.0) 4.2 else if (cutoffHz <= 65.0) 4.8 else 5.5)
        val punchQ = if (cutoffHz <= 50.0) 0.90 else if (cutoffHz <= 65.0) 1.05 else 1.25

        bassShelfFilter.configureLowShelf(cutoffHz * 1.15, sr, shelfGainDb, slope = 0.92)
        bassPunchFilter.configurePeaking(cutoffHz, sr, punchGainDb, q = punchQ)
        cachedSubHarmonicDrive = (bassNorm * 0.28).toFloat()
        cachedSubLowpassAlpha = ((2.0 * PI * (cutoffHz * 1.25)) / sr).coerceIn(0.002, 0.04).toFloat()
        cachedSideLowpassAlpha = ((2.0 * PI * 135.0) / sr).coerceIn(0.005, 0.06).toFloat()

        // 3. 配置人声通透度与泛音激励器 (260Hz 去浊 + 2.7kHz 人声贴耳 + 9.5kHz 空气感)
        val clarityNorm = if (dspActive) (state.vocalClarityStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val warmthNorm = if (dspActive) (state.analogWarmthStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val loudnessHighDb = if (state.equalLoudnessCompensation) 3.4 else 0.0

        vocalMudCutFilter.configurePeaking(265.0, sr, -clarityNorm * 2.8, q = 1.15)
        vocalPresenceFilter.configurePeaking(2750.0, sr, clarityNorm * 6.2, q = 1.05)

        // 高频空气感同时融合：人声通透提亮 (+4.8dB)、等响度高频补偿 (+3.4dB) 与复古黑胶/磁带高频柔化 (-3.2dB)
        val netAirShelfDb = (clarityNorm * 4.8) + loudnessHighDb - (warmthNorm * 3.2)
        vocalAirShelfFilter.configureHighShelf(9200.0, sr, netAirShelfDb, slope = 0.82)

        // 4. 配置黑胶与黄金胆机温暖度 (175Hz 醇厚中低频 + 甲类电子管偶次谐波驱动)
        warmthBodyFilter.configurePeaking(175.0, sr, warmthNorm * 5.2, q = 0.85)
        cachedWarmthDrive = (warmthNorm * 0.55).toFloat()

        // 5. 配置 3D 环绕声场宽度与 Haas 跨声道微延迟
        val virtNorm = if (dspActive) (state.virtualizerStrength / 1000f).coerceIn(0f, 1f) else 0f
        cachedWidthFactor = virtNorm * 1.45f
        cachedHaasCrossMix = virtNorm * 0.34f
        activeHaasDelay = ((0.00062f * sr).toInt()).coerceIn(12, maxHaasSamples - 2)

        // 6. 配置空间混响房间尺寸与抽头长度
        val newReverb = if (dspActive) state.reverbMode else ReverbMode.NONE
        if (newReverb != cachedReverbMode) {
            if (newReverb == ReverbMode.NONE) {
                clearReverbBuffers()
            }
            cachedReverbMode = newReverb
        }
        if (newReverb != ReverbMode.NONE) {
            val srScale = (sr / 44100f) * newReverb.roomScale.coerceIn(0.4f, 1.4f)
            val stereoSpread = (23 * (sr / 44100f)).toInt().coerceAtLeast(11)
            for (i in 0 until 4) {
                combEffectiveLenL[i] = (baseCombLengths[i] * srScale).toInt().coerceIn(128, combBuffersL[i].size - 4)
                combEffectiveLenR[i] = ((baseCombLengths[i] + stereoSpread) * srScale).toInt().coerceIn(128, combBuffersR[i].size - 4)
                if (combWriteIdxL[i] >= combEffectiveLenL[i]) combWriteIdxL[i] = 0
                if (combWriteIdxR[i] >= combEffectiveLenR[i]) combWriteIdxR[i] = 0
            }
            for (i in 0 until 2) {
                allpassEffectiveLenL[i] = (baseAllpassLengths[i] * srScale).toInt().coerceIn(64, allpassBuffersL[i].size - 4)
                allpassEffectiveLenR[i] = ((baseAllpassLengths[i] + stereoSpread) * srScale).toInt().coerceIn(64, allpassBuffersR[i].size - 4)
                if (allpassWriteIdxL[i] >= allpassEffectiveLenL[i]) allpassWriteIdxL[i] = 0
                if (allpassWriteIdxR[i] >= allpassEffectiveLenR[i]) allpassWriteIdxR[i] = 0
            }
        }

        // 7. 计算前置放大增益 + 动态响度增益及防破音智能余量补偿
        val preampDb = if (dspActive) state.preampGainDb.toDouble() else 0.0
        val loudnessDb = if (dspActive) (state.loudnessGainMb / 100.0).coerceIn(0.0, 15.0) * 0.72 else 0.0
        val totalBoostPeakDb = maxPositiveEqDb + maxOf(0.0, shelfGainDb * 0.65) + maxOf(0.0, clarityNorm * 3.5)
        val headroomPadDb = if (state.antiClippingEnabled && totalBoostPeakDb > 3.5) {
            -((totalBoostPeakDb - 3.5) * 0.42).coerceAtMost(5.5)
        } else 0.0
        val netGainDb = (preampDb + loudnessDb + headroomPadDb).coerceIn(-12.0, 14.0)
        cachedLinearPreampAndLoudnessGain = (10.0.pow(netGainDb / 20.0)).toFloat()
    }

    private fun processStereoReverb(inL: Float, inR: Float, mode: ReverbMode, outPair: FloatArray) {
        val inputMono = (inL + inR) * 0.25f
        val feedback = mode.decayFeedback
        val damp1 = mode.damping
        val damp2 = 1f - damp1

        var sumL = 0f
        var sumR = 0f

        for (i in 0 until 4) {
            val bufL = combBuffersL[i]
            val idxL = combWriteIdxL[i]
            val delayedL = bufL[idxL]
            val storeL = delayedL * damp2 + combFilterStoreL[i] * damp1
            combFilterStoreL[i] = storeL
            bufL[idxL] = (inputMono + storeL * feedback).coerceIn(-2.5f, 2.5f)
            val nextIdxL = idxL + 1
            combWriteIdxL[i] = if (nextIdxL >= combEffectiveLenL[i]) 0 else nextIdxL
            sumL += delayedL

            val bufR = combBuffersR[i]
            val idxR = combWriteIdxR[i]
            val delayedR = bufR[idxR]
            val storeR = delayedR * damp2 + combFilterStoreR[i] * damp1
            combFilterStoreR[i] = storeR
            bufR[idxR] = (inputMono + storeR * feedback).coerceIn(-2.5f, 2.5f)
            val nextIdxR = idxR + 1
            combWriteIdxR[i] = if (nextIdxR >= combEffectiveLenR[i]) 0 else nextIdxR
            sumR += delayedR
        }

        var revL = sumL * 0.28f
        var revR = sumR * 0.28f

        for (i in 0 until 2) {
            val apBufL = allpassBuffersL[i]
            val apIdxL = allpassWriteIdxL[i]
            val bufOutL = apBufL[apIdxL]
            val newInL = (revL + bufOutL * 0.5f).coerceIn(-2.5f, 2.5f)
            apBufL[apIdxL] = newInL
            revL = bufOutL - revL * 0.5f
            val nextApL = apIdxL + 1
            allpassWriteIdxL[i] = if (nextApL >= allpassEffectiveLenL[i]) 0 else nextApL

            val apBufR = allpassBuffersR[i]
            val apIdxR = allpassWriteIdxR[i]
            val bufOutR = apBufR[apIdxR]
            val newInR = (revR + bufOutR * 0.5f).coerceIn(-2.5f, 2.5f)
            apBufR[apIdxR] = newInR
            revR = bufOutR - revR * 0.5f
            val nextApR = apIdxR + 1
            allpassWriteIdxR[i] = if (nextApR >= allpassEffectiveLenR[i]) 0 else nextApR
        }

        val wet = mode.wetMix
        val dry = 1f - wet * 0.28f
        outPair[0] = inL * dry + revL * wet
        outPair[1] = inR * dry + revR * wet
    }

    private val reverbScratchPair = FloatArray(2)

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val state = AudioEffectManager.state.value
        val balance = state.channelBalance.coerceIn(-1f, 1f)
        val dspActive = state.isEnabled && state.currentPreset != EffectPreset.OFF
        val eqLoudnessComp = state.isEnabled && state.equalLoudnessCompensation
        val volNorm = state.isEnabled && state.volumeNormalizationEnabled
        val antiClip = state.antiClippingEnabled && (dspActive || eqLoudnessComp || volNorm)
        val focusDuckGain = PlaybackService.audioFocusDuckGain.coerceIn(0.15f, 1.0f)

        // 若处于 0 音染 / Bit-Perfect 原生直通态且声道平衡居中，直接零拷贝透传
        if (abs(balance) < 0.01f && !dspActive && !eqLoudnessComp && !volNorm && focusDuckGain >= 0.99f) {
            val out = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(44100)
        updateDspCoefficientsIfNeeded(state, sampleRate)

        val out = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
        val preGain = cachedLinearPreampAndLoudnessGain
        val targetLeftGain = (if (balance > 0f) (1f - balance).coerceIn(0f, 1f) else 1f) * preGain * focusDuckGain
        val targetRightGain = (if (balance < 0f) (1f + balance).coerceIn(0f, 1f) else 1f) * preGain * focusDuckGain

        val rotSpeed = if (dspActive) state.surroundRotationSpeed else 0
        val phaseStep = if (rotSpeed > 0) (2.0 * PI * (0.22 + rotSpeed * 0.018)) / sampleRate else 0.0

        val subDrive = cachedSubHarmonicDrive
        val subAlpha = cachedSubLowpassAlpha
        val sideLpAlpha = cachedSideLowpassAlpha
        val warmthDrive = cachedWarmthDrive
        val widthFactor = cachedWidthFactor
        val haasMix = cachedHaasCrossMix
        val haasDelay = activeHaasDelay
        val reverbMode = cachedReverbMode

        val encoding = inputAudioFormat.encoding
        val channels = inputAudioFormat.channelCount
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        val frameSize = bytesPerSample * channels

        while (inputBuffer.remaining() >= frameSize) {
            val rawL = readNormalizedSample(inputBuffer, encoding)
            val rawR = if (channels == 2) readNormalizedSample(inputBuffer, encoding) else rawL

            // 64 采样线性增益平滑，防止音效开关或调节瞬间产生阶跃爆音
            smoothLeftGain += (targetLeftGain - smoothLeftGain) * 0.03125f
            smoothRightGain += (targetRightGain - smoothRightGain) * 0.03125f

            val normGain = if (volNorm) {
                val frameEnergy = (abs(rawL) + abs(rawR)) * 0.5f
                if (frameEnergy > 0.015f) {
                    val alpha = if (frameEnergy > smoothedEnvelope) 0.0005f else 0.00015f
                    smoothedEnvelope += alpha * (frameEnergy - smoothedEnvelope)
                    val targetEnv = 0.25f
                    val minClamp = if (dspActive) 0.96f else 0.85f
                    val desiredGain = (targetEnv / smoothedEnvelope.coerceAtLeast(0.06f)).coerceIn(minClamp, 1.20f)
                    currentNormGain += 0.0004f * (desiredGain - currentNormGain)
                }
                currentNormGain
            } else 1.0f

            var sL = rawL * normGain
            var sR = rawR * normGain

            if (dspActive || eqLoudnessComp) {
                // 1. 10段 Biquad 参量均衡滤波级联
                for (b in 0 until 10) {
                    val f = eqFilters[b]
                    sL = f.processLeft(sL)
                    sR = f.processRight(sR)
                }

                // 2. 重低音下潜低架 + 截止频点谐振峰 (45Hz / 60Hz / 85Hz) + 亚低频泛音激励
                sL = bassPunchFilter.processLeft(bassShelfFilter.processLeft(sL))
                sR = bassPunchFilter.processRight(bassShelfFilter.processRight(sR))
                if (subDrive > 0.005f) {
                    subLowpassL += subAlpha * (sL - subLowpassL)
                    subLowpassR += subAlpha * (sR - subLowpassR)
                    val subHarmL = subLowpassL * abs(subLowpassL) * 1.6f
                    val subHarmR = subLowpassR * abs(subLowpassR) * 1.6f
                    sL += subHarmL * subDrive
                    sR += subHarmR * subDrive
                }

                // 3. 人声通透去浊 (265Hz)、中高频贴耳存在感 (2.75kHz) 与极高频空气泛音 (9.2kHz)
                sL = vocalAirShelfFilter.processLeft(vocalPresenceFilter.processLeft(vocalMudCutFilter.processLeft(sL)))
                sR = vocalAirShelfFilter.processRight(vocalPresenceFilter.processRight(vocalMudCutFilter.processRight(sR)))

                // 4. 黑胶与黄金胆机温暖度 (175Hz 醇厚共振 + 甲类电子管非对称偶次谐波软饱和)
                sL = warmthBodyFilter.processLeft(sL)
                sR = warmthBodyFilter.processRight(sR)
                if (warmthDrive > 0.005f) {
                    val evenL = sL * sL * (if (sL >= 0f) 0.55f else -0.32f)
                    val evenR = sR * sR * (if (sR >= 0f) 0.55f else -0.32f)
                    val cubicL = sL - (sL * sL * sL) * 0.18f
                    val cubicR = sR - (sR * sR * sR) * 0.18f
                    sL = sL * (1f - warmthDrive * 0.35f) + cubicL * (warmthDrive * 0.35f) + evenL * (warmthDrive * 0.45f)
                    sR = sR * (1f - warmthDrive * 0.35f) + cubicR * (warmthDrive * 0.35f) + evenR * (warmthDrive * 0.45f)
                }

                // 5. 3D 环绕声场扩展 (低频居中保护 M/S 拓宽 + Haas 跨声道微延迟空间成像)
                if (channels == 2 && widthFactor > 0.01f) {
                    val readIdx = (haasWriteIdx - haasDelay + maxHaasSamples) % maxHaasSamples
                    val delayedL = haasBufferL[readIdx]
                    val delayedR = haasBufferR[readIdx]
                    haasBufferL[haasWriteIdx] = sL
                    haasBufferR[haasWriteIdx] = sR
                    haasWriteIdx = (haasWriteIdx + 1) % maxHaasSamples

                    val mid = (sL + sR) * 0.5f
                    val rawSide = (sL - sR) * 0.5f
                    sideLowpass += sideLpAlpha * (rawSide - sideLowpass)
                    val highSide = rawSide - sideLowpass
                    val haasDiff = (delayedL - delayedR) * 0.5f * haasMix
                    val expandedSide = sideLowpass + highSide * (1f + widthFactor) + haasDiff
                    sL = mid + expandedSide
                    sR = mid - expandedSide
                }

                // 6. 360° 动态轨迹声场旋转 (3D旋转模式)
                if (phaseStep > 0.0 && channels == 2) {
                    rotationPhase += phaseStep
                    if (rotationPhase > 2.0 * PI) rotationPhase -= 2.0 * PI
                    val pan = sin(rotationPhase).toFloat() * 0.42f
                    val depthMod = (0.92f + 0.08f * cos(rotationPhase).toFloat())
                    sL *= (1f - pan.coerceAtLeast(0f)) * depthMod
                    sR *= (1f + pan.coerceAtMost(0f)) * depthMod
                }

                // 7. 立体声空间混响 (小型房间 / 中型房间 / 大型房间 / 音乐大厅 / 混响礼堂 / 录音室板式)
                if (reverbMode != ReverbMode.NONE) {
                    processStereoReverb(sL, sR, reverbMode, reverbScratchPair)
                    sL = reverbScratchPair[0]
                    sR = reverbScratchPair[1]
                }
            }

            var outL = sL * smoothLeftGain
            var outR = sR * smoothRightGain

            if (antiClip || dspActive || volNorm) {
                outL = softLimit(outL)
                outR = softLimit(outR)
            } else {
                outL = outL.coerceIn(-1f, 1f)
                outR = outR.coerceIn(-1f, 1f)
            }

            if (channels == 1) {
                val monoOut = (outL + outR) * 0.5f
                writeNormalizedSample(out, monoOut, encoding)
            } else {
                writeNormalizedSample(out, outL, encoding)
                writeNormalizedSample(out, outR, encoding)
            }
        }
        while (inputBuffer.hasRemaining()) {
            out.put(inputBuffer.get())
        }
        out.flip()
    }

    private fun readNormalizedSample(buf: ByteBuffer, encoding: Int): Float = when (encoding) {
        C.ENCODING_PCM_16BIT -> buf.short.toFloat() / 32768f
        C.ENCODING_PCM_24BIT -> {
            val b0 = buf.get().toInt() and 0xFF
            val b1 = buf.get().toInt() and 0xFF
            val b2 = buf.get().toInt() // sign-extended
            val sample24 = (b2 shl 16) or (b1 shl 8) or b0
            sample24.toFloat() / 8388608f
        }
        C.ENCODING_PCM_32BIT -> (buf.int.toDouble() / 2147483648.0).toFloat()
        C.ENCODING_PCM_FLOAT -> buf.float.coerceIn(-1f, 1f)
        else -> buf.short.toFloat() / 32768f
    }

    private fun writeNormalizedSample(out: ByteBuffer, sample: Float, encoding: Int) {
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                out.putShort((sample * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
            C.ENCODING_PCM_24BIT -> {
                val v = (sample * 8388607f).toInt().coerceIn(-8388608, 8388607)
                out.put((v and 0xFF).toByte())
                out.put(((v shr 8) and 0xFF).toByte())
                out.put(((v shr 16) and 0xFF).toByte())
            }
            C.ENCODING_PCM_32BIT -> {
                val v = (sample.toDouble() * 2147483647.0).toLong().coerceIn(-2147483648L, 2147483647L).toInt()
                out.putInt(v)
            }
            C.ENCODING_PCM_FLOAT -> {
                out.putFloat(sample.coerceIn(-1f, 1f))
            }
            else -> {
                out.putShort((sample * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
    }

    internal fun softLimit(x: Float): Float {
        val threshold = 0.85f
        val absX = abs(x)
        if (absX <= threshold) return x
        val sign = if (x >= 0f) 1f else -1f
        val excess = absX - threshold
        val headroom = 0.995f - threshold
        val compressed = threshold + headroom * (excess / (excess + headroom))
        return (sign * compressed).coerceIn(-0.995f, 0.995f)
    }
}

/**
 * AndroidX Media3 官方后台播放服务
 * 提供锁屏控制、通知栏控制面板、有线/蓝牙耳机按键拦截、三档音频焦点与闪避管理、耳机连接自动播放与蓝牙连接检测
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var duckFocusRequest: AudioFocusRequest? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var initialDeviceEnumerationDone: Boolean = false
    private val connectedWiredIds = mutableSetOf<Int>()
    private val connectedBluetoothIds = mutableSetOf<Int>()

    private val duckAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (currentAudioFocusMode != AudioFocusMode.DUCK) {
            audioFocusDuckGain = 1.0f
            return@OnAudioFocusChangeListener
        }
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 压低音量同播模式：不暂停音乐，平滑压低至 30% 音量
                audioFocusDuckGain = 0.30f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocusDuckGain = 1.0f
            }
        }
    }

    companion object {
        const val ACTION_CUSTOM_TOGGLE_DESKTOP_LYRIC = "com.concept.music.action.TOGGLE_DESKTOP_LYRIC"
        const val ACTION_CUSTOM_TOGGLE_DESKTOP_LYRIC_LOCK = "com.concept.music.action.TOGGLE_DESKTOP_LYRIC_LOCK"

        val SESSION_COMMAND_TOGGLE_DESKTOP_LYRIC: SessionCommand
            get() = SessionCommand(ACTION_CUSTOM_TOGGLE_DESKTOP_LYRIC, Bundle())
        val SESSION_COMMAND_TOGGLE_DESKTOP_LYRIC_LOCK: SessionCommand
            get() = SessionCommand(ACTION_CUSTOM_TOGGLE_DESKTOP_LYRIC_LOCK, Bundle())

        /**
         * 构建适配 Android 13~16 系统媒体通知栏与 OPPO ColorOS 流体云（Fluid Cloud）/ 灵动岛的自定义控制按钮布局：
         * - 按钮 1（左侧扩展槽位）：桌面歌词开关（词），支持 App 置于后台时快捷开启/关闭桌面歌词；
         * - 按钮 2（右侧扩展槽位）：桌面歌词锁定/解锁（锁），支持通过流体云或通知栏一键解锁/锁定桌面歌词，从而省去屏幕上的悬浮小锁图标。
         */
        fun buildNotificationCustomLayout(
            lyricEnabled: Boolean = SystemLyricBridge.state.value.floatingCapsuleEnabled,
            lyricLocked: Boolean = SystemLyricBridge.state.value.floatingCapsuleLocked
        ): List<CommandButton> {
            val toggleLyricBtn = CommandButton.Builder()
                .setDisplayName(if (lyricEnabled) "关闭桌面歌词" else "开启桌面歌词")
                .setSessionCommand(SESSION_COMMAND_TOGGLE_DESKTOP_LYRIC)
                .setIconResId(if (lyricEnabled) R.drawable.ic_notif_lyric_on else R.drawable.ic_notif_lyric_off)
                .setEnabled(true)
                .build()

            val toggleLockBtn = CommandButton.Builder()
                .setDisplayName(if (lyricLocked) "解锁桌面歌词" else "锁定桌面歌词")
                .setSessionCommand(SESSION_COMMAND_TOGGLE_DESKTOP_LYRIC_LOCK)
                .setIconResId(if (lyricLocked) R.drawable.ic_notif_lyric_locked else R.drawable.ic_notif_lyric_unlocked)
                .setEnabled(true)
                .build()

            return listOf(toggleLyricBtn, toggleLockBtn)
        }

        fun refreshMediaNotificationCustomLayout() {
            val svc = activeServiceInstance ?: return
            runCatching {
                val mainLooper = Looper.getMainLooper()
                if (mainLooper == null || Looper.myLooper() == mainLooper) {
                    svc.refreshMediaSessionCustomLayoutInternal()
                } else {
                    mainHandler?.post {
                        svc.refreshMediaSessionCustomLayoutInternal()
                    }
                }
            }
        }

        private val mainHandler: Handler? by lazy {
            runCatching {
                val looper = Looper.getMainLooper()
                if (looper != null) Handler(looper) else null
            }.getOrNull()
        }
        private var pendingHeadsetRunnable: Runnable? = null

        @Volatile
        private var activeServiceInstance: PlaybackService? = null

        @Volatile
        private var fallbackAppContext: Context? = null

        @Volatile
        var currentAudioFocusMode: AudioFocusMode = AudioFocusMode.EXCLUSIVE
            private set

        @Volatile
        var audioFocusDuckGain: Float = 1.0f
            internal set

        @Volatile
        var autoPlayOnHeadsetConnectEnabled: Boolean = false

        @Volatile
        var autoPlayOnWiredConnectEnabled: Boolean = true

        @Volatile
        var autoPlayOnBluetoothConnectEnabled: Boolean = true

        @Volatile
        var keepPlayingOnTaskRemovedEnabled: Boolean = true

        @Volatile
        var isBluetoothAudioOutputConnected: Boolean = false
            internal set

        @Volatile
        var onBluetoothConnectionChanged: ((Boolean) -> Unit)? = null

        @Volatile
        var onSkipToPreviousIntercepted: (() -> Boolean)? = null
            set(value) {
                field = value
                PlaybackCoordinator.onSkipToPreviousDelegate = value
            }

        @Volatile
        var onSkipToNextIntercepted: (() -> Boolean)? = null
            set(value) {
                field = value
                PlaybackCoordinator.onSkipToNextDelegate = value
            }

        internal val headsetButtonController = HeadsetMediaButtonController(
            scheduleDelayed = { delayMs, action ->
                val handler = mainHandler
                if (handler != null) {
                    pendingHeadsetRunnable?.let { handler.removeCallbacks(it) }
                    val r = Runnable { action() }
                    pendingHeadsetRunnable = r
                    handler.postDelayed(r, delayMs)
                } else {
                    action()
                }
            },
            cancelScheduled = {
                val handler = mainHandler
                pendingHeadsetRunnable?.let { r -> handler?.removeCallbacks(r) }
                pendingHeadsetRunnable = null
            },
            onTogglePlay = {
                val ctx = activeServiceInstance?.applicationContext ?: fallbackAppContext
                if (ctx != null) {
                    PlaybackCoordinator.handleTogglePlay(ctx, activeServiceInstance?.player)
                } else {
                    PlaybackCoordinator.onTogglePlayDelegate?.invoke()
                }
            },
            onSkipToNext = {
                val ctx = activeServiceInstance?.applicationContext ?: fallbackAppContext
                if (ctx != null) {
                    PlaybackCoordinator.handleSkipToNext(ctx, activeServiceInstance?.player)
                } else {
                    PlaybackCoordinator.onSkipToNextDelegate?.invoke()
                }
            },
            onSkipToPrevious = {
                val ctx = activeServiceInstance?.applicationContext ?: fallbackAppContext
                if (ctx != null) {
                    PlaybackCoordinator.handleSkipToPrevious(ctx, activeServiceInstance?.player)
                } else {
                    PlaybackCoordinator.onSkipToPreviousDelegate?.invoke()
                }
            }
        )

        internal fun isWiredHeadsetDeviceType(type: Int): Boolean = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> true
            else -> false
        }

        internal fun isBluetoothAudioDeviceType(type: Int): Boolean = when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
            else -> false
        }

        fun updateHandleAudioBecomingNoisy(enabled: Boolean) {
            val svc = activeServiceInstance ?: return
            runCatching {
                val mainLooper = Looper.getMainLooper()
                if (mainLooper == null || Looper.myLooper() == mainLooper) {
                    svc.player?.setHandleAudioBecomingNoisy(enabled)
                } else {
                    mainHandler?.post {
                        svc.player?.setHandleAudioBecomingNoisy(enabled)
                    }
                }
            }
        }

        fun updateAudioFocusDucking(enabled: Boolean) {
            val svc = activeServiceInstance ?: return
            runCatching {
                val mainLooper = Looper.getMainLooper()
                if (mainLooper == null || Looper.myLooper() == mainLooper) {
                    val p = svc.player ?: return
                    p.setAudioAttributes(p.audioAttributes, enabled)
                } else {
                    mainHandler?.post {
                        val p = svc.player ?: return@post
                        p.setAudioAttributes(p.audioAttributes, enabled)
                    }
                }
            }
        }

        /**
         * 更新三档音频焦点与混音闪避模式
         */
        fun updateAudioFocusMode(mode: AudioFocusMode) {
            currentAudioFocusMode = mode
            if (mode != AudioFocusMode.DUCK) {
                audioFocusDuckGain = 1.0f
            }
            val svc = activeServiceInstance ?: return
            runCatching {
                val mainLooper = Looper.getMainLooper()
                val applyBlock = {
                    svc.applyAudioFocusModeInternal(mode)
                }
                if (mainLooper == null || Looper.myLooper() == mainLooper) {
                    applyBlock()
                } else {
                    mainHandler?.post(applyBlock)
                }
            }
        }

        /**
         * 设置是否允许与其他应用同时播放（兼容布尔开关与三档模式联动）
         */
        fun updateAllowPlayWithOtherApps(allowMix: Boolean) {
            val targetMode = if (allowMix) {
                if (currentAudioFocusMode == AudioFocusMode.EXCLUSIVE) AudioFocusMode.MIX else currentAudioFocusMode
            } else {
                AudioFocusMode.EXCLUSIVE
            }
            updateAudioFocusMode(targetMode)
        }

        /**
         * 设置是否启用有线耳机线控多击切歌（双击暂停键下一首 / 三击暂停键上一首）
         */
        fun updateWiredHeadsetControlEnabled(enabled: Boolean) {
            headsetButtonController.multiClickSkipEnabled = enabled
        }

        /**
         * 设置是否交换有线耳机双击与三击的切歌方向
         */
        fun updateSwapHeadsetDoubleTripleClick(swapped: Boolean) {
            headsetButtonController.swapDoubleTripleClick = swapped
        }

        /**
         * 设置有线耳机多击判定灵敏度
         */
        fun updateHeadsetClickSensitivity(sensitivity: HeadsetClickSensitivity) {
            headsetButtonController.multiClickTimeoutMs = sensitivity.timeoutMs
        }

        fun updateHeadsetAutoPlayConfig(
            enabled: Boolean,
            wiredEnabled: Boolean = autoPlayOnWiredConnectEnabled,
            bluetoothEnabled: Boolean = autoPlayOnBluetoothConnectEnabled
        ) {
            autoPlayOnHeadsetConnectEnabled = enabled
            autoPlayOnWiredConnectEnabled = wiredEnabled
            autoPlayOnBluetoothConnectEnabled = bluetoothEnabled
        }

        fun updateKeepPlayingOnTaskRemoved(enabled: Boolean) {
            keepPlayingOnTaskRemovedEnabled = enabled
        }

        fun handleHeadsetKeyEvent(context: Context?, event: KeyEvent?): Boolean {
            if (event == null) return false
            if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.AUDIO_HARDWARE)) return false
            if (context != null) {
                fallbackAppContext = context.applicationContext ?: context
            }
            return headsetButtonController.handleKeyEvent(
                keyCode = event.keyCode,
                action = event.action,
                repeatCount = event.repeatCount,
                downTimeMs = event.downTime
            )
        }

        internal fun extractMediaKeyEvent(intent: Intent?): KeyEvent? {
            if (intent == null || intent.action != Intent.ACTION_MEDIA_BUTTON) return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
        }

        internal fun toggleActivePlayerPlayPause(): Boolean {
            val svc = activeServiceInstance ?: return false
            return PlaybackCoordinator.handleTogglePlay(svc.applicationContext, svc.player)
        }
    }

    private fun applyAudioFocusModeInternal(mode: AudioFocusMode) {
        val p = player ?: return
        when (mode) {
            AudioFocusMode.EXCLUSIVE -> {
                abandonDuckAudioFocusRequest()
                audioFocusDuckGain = 1.0f
                p.setAudioAttributes(p.audioAttributes, true)
            }
            AudioFocusMode.DUCK -> {
                p.setAudioAttributes(p.audioAttributes, false)
                if (p.isPlaying || p.playWhenReady) {
                    requestDuckAudioFocus()
                }
            }
            AudioFocusMode.MIX -> {
                abandonDuckAudioFocusRequest()
                audioFocusDuckGain = 1.0f
                p.setAudioAttributes(p.audioAttributes, false)
            }
        }
    }

    private fun requestDuckAudioFocus() {
        val am = audioManager ?: (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.also { audioManager = it } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = duckFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(duckAudioFocusChangeListener, mainHandler ?: Handler(Looper.getMainLooper()))
                .build()
                .also { duckFocusRequest = it }
            runCatching { am.requestAudioFocus(req) }
        }
    }

    private fun abandonDuckAudioFocusRequest() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            duckFocusRequest?.let { req ->
                runCatching { am.abandonAudioFocusRequest(req) }
            }
        }
    }

    private fun setupAudioDeviceMonitor() {
        val am = (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.also { audioManager = it } ?: return
        // 初始化当前已连接设备快照
        runCatching {
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            connectedWiredIds.clear()
            connectedBluetoothIds.clear()
            for (dev in outputs) {
                if (!dev.isSink) continue
                if (isWiredHeadsetDeviceType(dev.type)) connectedWiredIds.add(dev.id)
                if (isBluetoothAudioDeviceType(dev.type)) connectedBluetoothIds.add(dev.id)
            }
            val btConnected = connectedBluetoothIds.isNotEmpty()
            isBluetoothAudioOutputConnected = btConnected
            onBluetoothConnectionChanged?.invoke(btConnected)
        }

        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                if (addedDevices == null) return
                var newlyAddedWired = false
                var newlyAddedBluetooth = false
                for (dev in addedDevices) {
                    if (!dev.isSink) continue
                    if (isWiredHeadsetDeviceType(dev.type)) {
                        if (connectedWiredIds.add(dev.id) && initialDeviceEnumerationDone) {
                            newlyAddedWired = true
                        }
                    }
                    if (isBluetoothAudioDeviceType(dev.type)) {
                        if (connectedBluetoothIds.add(dev.id) && initialDeviceEnumerationDone) {
                            newlyAddedBluetooth = true
                        }
                    }
                }
                initialDeviceEnumerationDone = true
                val btConnectedNow = connectedBluetoothIds.isNotEmpty()
                if (isBluetoothAudioOutputConnected != btConnectedNow) {
                    isBluetoothAudioOutputConnected = btConnectedNow
                    onBluetoothConnectionChanged?.invoke(btConnectedNow)
                }

                if (autoPlayOnHeadsetConnectEnabled && AppPermissionManager.isPermissionEnabled(AppPermissionType.AUDIO_HARDWARE)) {
                    val shouldTriggerWired = newlyAddedWired && autoPlayOnWiredConnectEnabled
                    val shouldTriggerBt = newlyAddedBluetooth && autoPlayOnBluetoothConnectEnabled
                    if (shouldTriggerWired || shouldTriggerBt) {
                        PlaybackCoordinator.handleResumeOnHeadsetConnected(applicationContext, player)
                    }
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                if (removedDevices == null) return
                for (dev in removedDevices) {
                    connectedWiredIds.remove(dev.id)
                    connectedBluetoothIds.remove(dev.id)
                }
                val btConnectedNow = connectedBluetoothIds.isNotEmpty()
                if (isBluetoothAudioOutputConnected != btConnectedNow) {
                    isBluetoothAudioOutputConnected = btConnectedNow
                    onBluetoothConnectionChanged?.invoke(btConnectedNow)
                }
            }
        }
        audioDeviceCallback = callback
        runCatching {
            am.registerAudioDeviceCallback(callback, mainHandler)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        fallbackAppContext = applicationContext

        val prefs = applicationContext.getSharedPreferences("concept_player_prefs", Context.MODE_PRIVATE)
        val pauseOnHeadsetDisconnect = prefs.getBoolean("pause_on_headset_disconnect", true)
        val allowPlayWithOtherApps = prefs.getBoolean("allow_play_with_other_apps", false)
        val savedFocusModeName = prefs.getString("audio_focus_mode", null)
        val initialFocusMode = if (savedFocusModeName != null) {
            runCatching { AudioFocusMode.valueOf(savedFocusModeName) }.getOrDefault(
                if (allowPlayWithOtherApps) AudioFocusMode.MIX else AudioFocusMode.EXCLUSIVE
            )
        } else {
            if (allowPlayWithOtherApps) AudioFocusMode.MIX else AudioFocusMode.EXCLUSIVE
        }
        currentAudioFocusMode = initialFocusMode

        val wiredHeadsetControlEnabled = prefs.getBoolean("wired_headset_control_enabled", true)
        val swapHeadsetButtons = prefs.getBoolean("swap_headset_double_triple_click", false)
        val savedSensitivityName = prefs.getString("headset_click_sensitivity", HeadsetClickSensitivity.STANDARD.name)
            ?: HeadsetClickSensitivity.STANDARD.name
        val sensitivity = runCatching { HeadsetClickSensitivity.valueOf(savedSensitivityName) }
            .getOrDefault(HeadsetClickSensitivity.STANDARD)

        headsetButtonController.multiClickSkipEnabled = wiredHeadsetControlEnabled
        headsetButtonController.swapDoubleTripleClick = swapHeadsetButtons
        headsetButtonController.multiClickTimeoutMs = sensitivity.timeoutMs

        autoPlayOnHeadsetConnectEnabled = prefs.getBoolean("auto_play_on_headset_connect", false)
        autoPlayOnWiredConnectEnabled = prefs.getBoolean("auto_play_on_wired_connect", true)
        autoPlayOnBluetoothConnectEnabled = prefs.getBoolean("auto_play_on_bluetooth_connect", true)
        keepPlayingOnTaskRemovedEnabled = prefs.getBoolean("keep_playing_on_task_removed", true)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 优化 AudioTrack 硬件 PCM 缓冲窗口 (120ms~360ms)，确保切换蝰蛇音效预设与拖动调音台滑杆时零延迟即时生效
        val bufferSizeProvider = DefaultAudioTrackBufferSizeProvider.Builder()
            .setMinPcmBufferDurationUs(120_000)
            .setMaxPcmBufferDurationUs(360_000)
            .setPcmBufferMultiplicationFactor(2)
            .build()

        val masteringProcessor = ConceptMasteringAudioProcessor()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParameters: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioTrackBufferSizeProvider(bufferSizeProvider)
                    .setAudioProcessors(arrayOf(masteringProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(false)
                    .build()
            }
        }.setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 280,
                /* bufferForPlaybackAfterRebufferMs = */ 800
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(AudioCacheManager.getDataSourceFactory(this))

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, initialFocusMode == AudioFocusMode.EXCLUSIVE)
            .setHandleAudioBecomingNoisy(pauseOnHeadsetDisconnect)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
        player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (currentAudioFocusMode == AudioFocusMode.DUCK) {
                        audioFocusDuckGain = 1.0f
                        requestDuckAudioFocus()
                    } else {
                        audioFocusDuckGain = 1.0f
                    }
                    startBackgroundDesktopLyricProgressTicker()
                } else {
                    stopBackgroundDesktopLyricProgressTicker()
                    SystemLyricBridge.syncProgressFromService(exoPlayer.currentPosition, false)
                }
                refreshMediaSessionCustomLayoutInternal()
            }
        })

        setupAudioDeviceMonitor()

        AudioEffectManager.init(applicationContext)
        AudioEffectManager.onAuxEffectChanged = { effectId, sendLevel ->
            runCatching {
                player?.setAuxEffectInfo(AuxEffectInfo(effectId, sendLevel))
            }
        }
        AudioEffectManager.attachSession(exoPlayer.audioSessionId)
        exoPlayer.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                AudioEffectManager.attachSession(audioSessionId)
            }
        })

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands()
                    .buildUpon()
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasPreviousMediaItem(): Boolean = true

            override fun hasNextMediaItem(): Boolean = true

            override fun play() {
                if (exoPlayer.mediaItemCount == 0) {
                    if (!PlaybackCoordinator.handleTogglePlay(applicationContext, exoPlayer)) {
                        super.play()
                    }
                } else {
                    super.play()
                }
            }

            override fun seekToPrevious() {
                if (!PlaybackCoordinator.handleSkipToPrevious(applicationContext, exoPlayer)) {
                    super.seekToPrevious()
                }
            }

            override fun seekToPreviousMediaItem() {
                if (!PlaybackCoordinator.handleSkipToPrevious(applicationContext, exoPlayer)) {
                    super.seekToPreviousMediaItem()
                }
            }

            override fun seekToNext() {
                if (!PlaybackCoordinator.handleSkipToNext(applicationContext, exoPlayer)) {
                    super.seekToNext()
                }
            }

            override fun seekToNextMediaItem() {
                if (!PlaybackCoordinator.handleSkipToNext(applicationContext, exoPlayer)) {
                    super.seekToNextMediaItem()
                }
            }
        }

        runCatching {
            val notificationProvider = DefaultMediaNotificationProvider.Builder(applicationContext)
                .build()
                .apply {
                    setSmallIcon(R.drawable.ic_launcher_concept)
                }
            setMediaNotificationProvider(notificationProvider)
        }

        SystemLyricBridge.init(applicationContext)
        SystemLyricBridge.onDesktopLyricNotificationStateChanged = {
            refreshMediaSessionCustomLayoutInternal()
            if (SystemLyricBridge.state.value.floatingCapsuleEnabled && exoPlayer.isPlaying) {
                startBackgroundDesktopLyricProgressTicker()
            }
        }

        val sessionCallback = object : MediaSession.Callback {
            @OptIn(UnstableApi::class)
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val isSelfOrSystem = controller.packageName == packageName ||
                    controller.packageName == "android" ||
                    controller.packageName.startsWith("com.android.") ||
                    controller.packageName.startsWith("com.oplus.") ||
                    controller.packageName.startsWith("com.coloros.") ||
                    controller.packageName.startsWith("com.heytap.") ||
                    controller.isTrusted
                val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(SESSION_COMMAND_TOGGLE_DESKTOP_LYRIC)
                    .add(SESSION_COMMAND_TOGGLE_DESKTOP_LYRIC_LOCK)
                    .build()
                val customButtons = buildNotificationCustomLayout()
                return if (isSelfOrSystem) {
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(availableSessionCommands)
                        .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                        .setCustomLayout(customButtons)
                        .build()
                } else {
                    val restrictedPlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                        .buildUpon()
                        .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                        .remove(Player.COMMAND_SET_MEDIA_ITEM)
                        .build()
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(availableSessionCommands)
                        .setAvailablePlayerCommands(restrictedPlayerCommands)
                        .setCustomLayout(customButtons)
                        .build()
                }
            }

            override fun onPostConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ) {
                super.onPostConnect(session, controller)
                val customButtons = buildNotificationCustomLayout()
                runCatching {
                    session.setCustomLayout(controller, customButtons)
                    session.setCustomLayout(customButtons)
                }
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_CUSTOM_TOGGLE_DESKTOP_LYRIC -> {
                        SystemLyricBridge.handleNotificationToggleDesktopLyric(applicationContext)
                        refreshMediaSessionCustomLayoutInternal()
                        if (SystemLyricBridge.state.value.floatingCapsuleEnabled && exoPlayer.isPlaying) {
                            startBackgroundDesktopLyricProgressTicker()
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    ACTION_CUSTOM_TOGGLE_DESKTOP_LYRIC_LOCK -> {
                        SystemLyricBridge.handleNotificationToggleLock(applicationContext)
                        refreshMediaSessionCustomLayoutInternal()
                        if (SystemLyricBridge.state.value.floatingCapsuleEnabled && exoPlayer.isPlaying) {
                            startBackgroundDesktopLyricProgressTicker()
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }

            override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                val keyEvent = extractMediaKeyEvent(intent)
                if (keyEvent != null && handleHeadsetKeyEvent(applicationContext, keyEvent)) {
                    return true
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }
        }

        val initialCustomButtons = buildNotificationCustomLayout()
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(sessionCallback)
            .setCustomLayout(initialCustomButtons)
            .build()
            .also { session ->
                runCatching { session.setCustomLayout(initialCustomButtons) }
            }
    }

    private val bgLyricProgressRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (p.isPlaying && SystemLyricBridge.state.value.floatingCapsuleEnabled) {
                SystemLyricBridge.syncProgressFromService(p.currentPosition, true)
                mainHandler?.postDelayed(this, 160L)
            }
        }
    }

    private fun startBackgroundDesktopLyricProgressTicker() {
        val h = mainHandler ?: return
        h.removeCallbacks(bgLyricProgressRunnable)
        if (player?.isPlaying == true && SystemLyricBridge.state.value.floatingCapsuleEnabled) {
            h.post(bgLyricProgressRunnable)
        }
    }

    private fun stopBackgroundDesktopLyricProgressTicker() {
        mainHandler?.removeCallbacks(bgLyricProgressRunnable)
    }

    @OptIn(UnstableApi::class)
    internal fun refreshMediaSessionCustomLayoutInternal() {
        val session = mediaSession ?: return
        val buttons = buildNotificationCustomLayout()
        runCatching {
            session.setCustomLayout(buttons)
            for (controller in session.connectedControllers) {
                session.setCustomLayout(controller, buttons)
            }
            onUpdateNotification(session, player?.isPlaying == true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent = extractMediaKeyEvent(intent)
            if (keyEvent != null && handleHeadsetKeyEvent(applicationContext, keyEvent)) {
                return START_STICKY
            }
        }
        val res = super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ConceptMusicWidgetUpdater.ACTION_WIDGET_TOGGLE_PLAY -> {
                PlaybackCoordinator.handleTogglePlay(applicationContext, player)
            }
            ConceptMusicWidgetUpdater.ACTION_WIDGET_NEXT -> {
                PlaybackCoordinator.handleSkipToNext(applicationContext, player)
            }
            ConceptMusicWidgetUpdater.ACTION_WIDGET_PREV -> {
                PlaybackCoordinator.handleSkipToPrevious(applicationContext, player)
            }
            ACTION_CUSTOM_TOGGLE_DESKTOP_LYRIC -> {
                SystemLyricBridge.handleNotificationToggleDesktopLyric(applicationContext)
                refreshMediaSessionCustomLayoutInternal()
            }
            ACTION_CUSTOM_TOGGLE_DESKTOP_LYRIC_LOCK -> {
                SystemLyricBridge.handleNotificationToggleLock(applicationContext)
                refreshMediaSessionCustomLayoutInternal()
            }
        }
        return res
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        val canKeepBackground = keepPlayingOnTaskRemovedEnabled &&
            AppPermissionManager.isPermissionEnabled(AppPermissionType.BACKGROUND_GUARDIAN) &&
            AppPermissionManager.isPermissionEnabled(AppPermissionType.NOTIFICATION_MEDIA)
        if (!canKeepBackground || p == null || (!p.isPlaying && !p.playWhenReady)) {
            super.onTaskRemoved(rootIntent)
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        stopBackgroundDesktopLyricProgressTicker()
        SystemLyricBridge.onDesktopLyricNotificationStateChanged = null
        headsetButtonController.reset()
        abandonDuckAudioFocusRequest()
        audioFocusDuckGain = 1.0f
        audioDeviceCallback?.let { cb ->
            runCatching { audioManager?.unregisterAudioDeviceCallback(cb) }
        }
        audioDeviceCallback = null
        if (activeServiceInstance === this) {
            activeServiceInstance = null
        }
        AudioEffectManager.onAuxEffectChanged = null
        AudioEffectManager.releaseEffects()
        runCatching { mediaSession?.release() }
        mediaSession = null
        runCatching { player?.release() }
        player = null
        super.onDestroy()
    }
}
