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
import kotlin.math.roundToInt
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

    var isBypass: Boolean = true
        private set

    // 采用 Direct Form I 状态变量（直接存储真实历史输入/输出采样 x1,x2,y1,y2），
    // 当用户在播放途中实时拖动 PEQ 推杆、修改 Q 值或切换音效预设时，系数更新不会引发内部状态阶跃爆音 (Zero-Zipper Noise)
    private var x1L = 0.0
    private var x2L = 0.0
    private var y1L = 0.0
    private var y2L = 0.0

    private var x1R = 0.0
    private var x2R = 0.0
    private var y1R = 0.0
    private var y2R = 0.0

    fun resetState() {
        x1L = 0.0; x2L = 0.0; y1L = 0.0; y2L = 0.0
        x1R = 0.0; x2R = 0.0; y1R = 0.0; y2R = 0.0
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
        if (abs(gainDb) < 0.04 || sampleRate <= 0) {
            setBypass()
            return
        }
        val nyquist = sampleRate * 0.475
        val fc = freqHz.coerceIn(18.0, nyquist)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceIn(0.25, 10.0))

        val a0 = 1.0 + alpha / a
        b0 = (1.0 + alpha * a) / a0
        b1 = (-2.0 * cosW0) / a0
        b2 = (1.0 - alpha * a) / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha / a) / a0
        isBypass = false
    }

    fun configureLowShelf(freqHz: Double, sampleRate: Int, gainDb: Double, slope: Double = 0.90) {
        if (abs(gainDb) < 0.04 || sampleRate <= 0) {
            setBypass()
            return
        }
        val nyquist = sampleRate * 0.475
        val fc = freqHz.coerceIn(18.0, nyquist)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val s = slope.coerceIn(0.35, 2.5)
        val alpha = (sinW0 / 2.0) * sqrt((a + 1.0 / a) * (1.0 / s - 1.0) + 2.0)
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
        if (abs(gainDb) < 0.04 || sampleRate <= 0) {
            setBypass()
            return
        }
        val nyquist = sampleRate * 0.475
        val fc = freqHz.coerceIn(200.0, nyquist)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val s = slope.coerceIn(0.35, 2.5)
        val alpha = (sinW0 / 2.0) * sqrt((a + 1.0 / a) * (1.0 / s - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0
        b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)) / a0
        b2 = (a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0
        a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosW0)) / a0
        a2 = ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
        isBypass = false
    }

    fun configureNotch(freqHz: Double, sampleRate: Int, gainDb: Double, q: Double = 2.5) {
        if (sampleRate <= 0) {
            setBypass()
            return
        }
        val cutDb = if (gainDb < -0.5) gainDb else -10.0
        configurePeaking(freqHz, sampleRate, cutDb, (q * 2.2).coerceIn(1.8, 10.0))
    }

    fun configureLowPass(freqHz: Double, sampleRate: Int, q: Double = 0.7071) {
        if (sampleRate <= 0) {
            setBypass()
            return
        }
        val nyquist = sampleRate * 0.475
        val fc = freqHz.coerceIn(20.0, nyquist)
        val w0 = 2.0 * PI * fc / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceIn(0.4, 4.0))

        val a0 = 1.0 + alpha
        b0 = ((1.0 - cosW0) * 0.5) / a0
        b1 = (1.0 - cosW0) / a0
        b2 = ((1.0 - cosW0) * 0.5) / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha) / a0
        isBypass = false
    }

    fun configureHighPass(freqHz: Double, sampleRate: Int, q: Double = 0.7071) {
        if (sampleRate <= 0) {
            setBypass()
            return
        }
        val nyquist = sampleRate * 0.475
        val fc = freqHz.coerceIn(16.0, nyquist)
        val w0 = 2.0 * PI * fc / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceIn(0.4, 4.0))

        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosW0) * 0.5) / a0
        b1 = -(1.0 + cosW0) / a0
        b2 = ((1.0 + cosW0) * 0.5) / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha) / a0
        isBypass = false
    }

    fun configureBandPass(freqHz: Double, sampleRate: Int, q: Double = 1.0) {
        if (sampleRate <= 0) {
            setBypass()
            return
        }
        val nyquist = sampleRate * 0.475
        val fc = freqHz.coerceIn(25.0, nyquist)
        val w0 = 2.0 * PI * fc / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceIn(0.35, 6.0))

        val a0 = 1.0 + alpha
        b0 = alpha / a0
        b1 = 0.0
        b2 = -alpha / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha) / a0
        isBypass = false
    }

    fun processLeft(x: Float): Float {
        if (isBypass) return x
        val inD = x.toDouble()
        val y = b0 * inD + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L
        x1L = inD
        y2L = y1L
        y1L = if (y.isFinite()) y.coerceIn(-4.0, 4.0) else 0.0
        return y1L.toFloat()
    }

    fun processRight(x: Float): Float {
        if (isBypass) return x
        val inD = x.toDouble()
        val y = b0 * inD + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R
        x1R = inD
        y2R = y1R
        y1R = if (y.isFinite()) y.coerceIn(-4.0, 4.0) else 0.0
        return y1R.toFloat()
    }
}

/**
 * 概念版发烧级 64-bit 实时 PCM 母带音频处理器 (ConceptMasteringAudioProcessor v2.0)：
 * 直接在 Media3 音频解码管线内对 16-bit / 24-bit / 32-bit / Float PCM 实施零杂音、高动态余量数字信号处理：
 * 1. 10 段全参数 Biquad 参量均衡器 (支持独立中心频率 20Hz~20kHz、Q 值带宽 0.3~8.0、Peak/Low-Shelf/High-Shelf/Notch/Band-Pass 拓扑 + 独立高通/低通滤波器)
 * 2. 纯净分频超重低音引擎：二阶 Biquad 低通输入隔离 + 平滑 C∞ 双曲正切泛音合成 + 二阶镜像低通抗混叠输出滤波 + 235Hz 自动净空防浑浊补偿
 * 3. 人声通透与空气泛音引擎：265Hz 鼻音去浊 + 2.75kHz 贴耳存在感 + 6.2kHz 齿音动态抑制 (De-Esser) + 12.5kHz 极高频空气感激励 (Air Exciter)
 * 4. 带通隔离黑胶与黄金胆机引擎：仅对 110Hz~680Hz 中低频段实施平滑偶次谐波润色并经低通回混，彻底根除全频段非线性谐波造成的高频电流滋滋声 (IMD = 0)
 * 5. 全维空间声场引擎：低频居中保护 M/S 声场拓宽 + Bauer 耳机交叉馈送 (Crossfeed) + Haas 跨声道微延迟 + 舞台纵深早期反射 + 360° 动态轨迹环绕
 * 6. 频带净化立体声混响网络：输入经 180Hz~6.2kHz 带通净化 + 0~80ms 独立预延迟 (Pre-Delay) + Schroeder 4并联阻尼梳状 + 2级全通扩散
 * 7. 双声道耦合真包络峰值限制器 (True Stereo-Linked Envelope Peak Limiter) + 频域峰值增益自动预衰减 (Auto-Headroom) + TPDF 抖动量化，确保低频拉满 100% 依然清澈通透不破音
 */
@OptIn(UnstableApi::class)
class ConceptMasteringAudioProcessor : BaseAudioProcessor() {
    private val eqFilters = Array(10) { BiquadStereoFilter() }
    private val hpfFilter = BiquadStereoFilter()
    private val lpfFilter = BiquadStereoFilter()

    private val bassShelfFilter = BiquadStereoFilter()
    private val bassPunchFilter = BiquadStereoFilter()
    private val bassCleanDipFilter = BiquadStereoFilter()
    private val subIsoLowpassFilter = BiquadStereoFilter()
    private val subPostLowpassFilter = BiquadStereoFilter()

    private val vocalMudCutFilter = BiquadStereoFilter()
    private val vocalPresenceFilter = BiquadStereoFilter()
    private val deEsserFilter = BiquadStereoFilter()
    private val vocalAirShelfFilter = BiquadStereoFilter()

    private val warmthBodyFilter = BiquadStereoFilter()
    private val warmthBandpassFilter = BiquadStereoFilter()
    private val warmthPostLowpassFilter = BiquadStereoFilter()

    private val reverbInputHpf = BiquadStereoFilter()
    private val reverbInputLpf = BiquadStereoFilter()

    private var lastConfigVersion: Long = -1L
    private var lastConfiguredSampleRate: Int = -1

    private var rotationPhase = 0.0
    private var smoothedEnvelope = 0.22f
    private var currentNormGain = 1.0f
    private var smoothLeftGain = 1.0f
    private var smoothRightGain = 1.0f

    // 双声道耦合包络峰值限制器状态 (True Envelope Peak Limiter)
    private var limiterEnvelope = 0f

    // 瞬态微动态包络检测器状态
    private var transientFastEnv = 0f
    private var transientSlowEnv = 0f

    // DC 偏置消除器状态（用于谐波合成后的直流阻断）
    private var subDcPrevInL = 0f
    private var subDcPrevOutL = 0f
    private var subDcPrevInR = 0f
    private var subDcPrevOutR = 0f
    private var warmthDcPrevInL = 0f
    private var warmthDcPrevOutL = 0f
    private var warmthDcPrevInR = 0f
    private var warmthDcPrevOutR = 0f

    // M/S 侧声道低频锚定与 Bauer 交叉馈送低通状态
    private var sideLowpass = 0f
    private var crossfeedLpL = 0f
    private var crossfeedLpR = 0f

    // Haas 跨声道微延迟与 Bauer 交叉馈送延迟缓冲
    private val maxHaasSamples = 512
    private val haasBufferL = FloatArray(maxHaasSamples)
    private val haasBufferR = FloatArray(maxHaasSamples)
    private var haasWriteIdx = 0
    private var activeHaasDelay = 28
    private var activeCrossfeedDelay = 14
    private var activeDepthDelayL = 160
    private var activeDepthDelayR = 210

    // 混响预延迟环状缓冲 (支持 0 ~ 80ms Pre-Delay)
    private val maxPreDelaySamples = 8192
    private val preDelayBuffer = FloatArray(maxPreDelaySamples)
    private var preDelayWriteIdx = 0
    private var activePreDelaySamples = 790

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
    private var cachedSideLowpassAlpha = 0.018f
    private var cachedCrossfeedAlpha = 0.08f
    private var cachedCrossfeedMix = 0f
    private var cachedWarmthDrive = 0f
    private var cachedTransientDrive = 0f
    private var cachedWidthFactor = 0f
    private var cachedHaasCrossMix = 0f
    private var cachedDepthMix = 0f
    private var cachedReverbMode = ReverbMode.NONE
    private var cachedReverbWet = 0f
    private var cachedReverbDamping = 0.35f
    private var hasActiveEqBands = false
    private var hasActiveHpfLpf = false
    private var hasActiveBassFilter = false
    private var hasActiveVocalFilter = false
    private var hasActiveWarmthFilter = false

    // 实时音频律动遥测状态（低频/中频/高频分频包络与节拍脉冲检测）
    private var telLpBass = 0f
    private var telLpMid = 0f
    private var telSmoothBass = 0f
    private var telSmoothMid = 0f
    private var telSmoothHigh = 0f
    private var telPrevBass = 0f
    private var telBeatImpulse = 0f
    private val telBinsScratch = FloatArray(10)

    // 极轻量级 LCG 伪随机数发生器（用于 TPDF 三角概率密度抖动，零对象分配）
    private var ditherSeed: Int = 0x6D2B79F5

    private fun nextTpdfDither(): Float {
        ditherSeed = ditherSeed * 1664525 + 1013904223
        val r1 = (ditherSeed ushr 8).toFloat() * (1f / 16777216f)
        ditherSeed = ditherSeed * 1664525 + 1013904223
        val r2 = (ditherSeed ushr 8).toFloat() * (1f / 16777216f)
        return (r1 - r2) * 0.5f
    }

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
        limiterEnvelope = 0f
        transientFastEnv = 0f
        transientSlowEnv = 0f
        subDcPrevInL = 0f; subDcPrevOutL = 0f; subDcPrevInR = 0f; subDcPrevOutR = 0f
        warmthDcPrevInL = 0f; warmthDcPrevOutL = 0f; warmthDcPrevInR = 0f; warmthDcPrevOutR = 0f
        sideLowpass = 0f
        crossfeedLpL = 0f
        crossfeedLpR = 0f
        haasWriteIdx = 0
        haasBufferL.fill(0f)
        haasBufferR.fill(0f)
        preDelayWriteIdx = 0
        preDelayBuffer.fill(0f)
        eqFilters.forEach { it.resetState() }
        hpfFilter.resetState()
        lpfFilter.resetState()
        bassShelfFilter.resetState()
        bassPunchFilter.resetState()
        bassCleanDipFilter.resetState()
        subIsoLowpassFilter.resetState()
        subPostLowpassFilter.resetState()
        vocalMudCutFilter.resetState()
        vocalPresenceFilter.resetState()
        deEsserFilter.resetState()
        vocalAirShelfFilter.resetState()
        warmthBodyFilter.resetState()
        warmthBandpassFilter.resetState()
        warmthPostLowpassFilter.resetState()
        reverbInputHpf.resetState()
        reverbInputLpf.resetState()
        clearReverbBuffers()
    }

    private fun clearReverbBuffers() {
        preDelayBuffer.fill(0f)
        preDelayWriteIdx = 0
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
        val dspActive = state.isEnabled && state.currentPreset != EffectPreset.OFF && !state.isAbBypassActive

        if (!dspActive && !state.equalLoudnessCompensation) {
            eqFilters.forEach { it.setBypass() }
            hpfFilter.setBypass()
            lpfFilter.setBypass()
            bassShelfFilter.setBypass()
            bassPunchFilter.setBypass()
            bassCleanDipFilter.setBypass()
            subIsoLowpassFilter.setBypass()
            subPostLowpassFilter.setBypass()
            vocalMudCutFilter.setBypass()
            vocalPresenceFilter.setBypass()
            deEsserFilter.setBypass()
            vocalAirShelfFilter.setBypass()
            warmthBodyFilter.setBypass()
            warmthBandpassFilter.setBypass()
            warmthPostLowpassFilter.setBypass()
            reverbInputHpf.setBypass()
            reverbInputLpf.setBypass()
            cachedLinearPreampAndLoudnessGain = 1.0f
            cachedSubHarmonicDrive = 0f
            cachedWarmthDrive = 0f
            cachedTransientDrive = 0f
            cachedWidthFactor = 0f
            cachedHaasCrossMix = 0f
            cachedCrossfeedMix = 0f
            cachedDepthMix = 0f
            cachedReverbMode = ReverbMode.NONE
            cachedReverbWet = 0f
            hasActiveEqBands = false
            hasActiveHpfLpf = false
            hasActiveBassFilter = false
            hasActiveVocalFilter = false
            hasActiveWarmthFilter = false
            AudioRhythmTelemetry.headroomGainDb = 0f
            return
        }

        // 1. 配置 10 段全参数均衡器 Biquad 级联 (支持独立频点、Q 值与 5 种滤波器拓扑)
        val tenBands = state.tenBandLevels
        val tenQs = state.tenBandQFactors
        val tenCenters = state.tenBandCenterHz
        val tenTypes = state.tenBandFilterTypes
        for (i in 0 until 10) {
            val freq = (tenCenters.getOrElse(i) { AudioEffectManager.DEFAULT_TEN_BAND_HZ[i] }).toDouble()
            val qVal = (tenQs.getOrElse(i) { 1.20f }).toDouble().coerceIn(0.30, 8.0)
            val fType = tenTypes.getOrElse(i) { PeqFilterType.PEAK }
            val rawMb = if (dspActive && i < tenBands.size) tenBands[i] else 0
            val gainDb = (rawMb / 100.0).coerceIn(-12.0, 12.0)

            when (fType) {
                PeqFilterType.PEAK -> eqFilters[i].configurePeaking(freq, sr, gainDb, q = qVal)
                PeqFilterType.LOW_SHELF -> eqFilters[i].configureLowShelf(freq, sr, gainDb, slope = (qVal / 1.2).coerceIn(0.4, 2.2))
                PeqFilterType.HIGH_SHELF -> eqFilters[i].configureHighShelf(freq, sr, gainDb, slope = (qVal / 1.2).coerceIn(0.4, 2.2))
                PeqFilterType.NOTCH -> {
                    if (dspActive) eqFilters[i].configureNotch(freq, sr, gainDb, q = qVal)
                    else eqFilters[i].setBypass()
                }
                PeqFilterType.BAND_PASS -> eqFilters[i].configurePeaking(freq, sr, gainDb.coerceIn(-8.0, 8.0), q = (qVal * 1.35).coerceIn(0.5, 8.0))
            }
        }

        // 高通 (HPF) 与低通 (LPF) 滤波器配置
        if (dspActive && state.highPassCutoffHz in 20..120) {
            hpfFilter.configureHighPass(state.highPassCutoffHz.toDouble(), sr, q = 0.7071)
        } else {
            hpfFilter.setBypass()
        }
        if (dspActive && state.lowPassCutoffHz in 8000..19500) {
            lpfFilter.configureLowPass(state.lowPassCutoffHz.toDouble(), sr, q = 0.7071)
        } else {
            lpfFilter.setBypass()
        }

        // 2. 配置纯净分频重低音引擎：低架下潜 + 独立瞬态冲击 + 235Hz 防浑浊自动净空 + 二阶低通隔离泛音合成
        val bassNorm = if (dspActive) (state.bassBoostStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val punchNorm = if (dspActive) (state.bassPunchStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val cutoffHz = state.bassCutoffHz.coerceIn(40, 120).toDouble()
        val loudnessLowDb = if (state.equalLoudnessCompensation) 3.6 else 0.0

        val shelfGainDb = bassNorm * (if (cutoffHz <= 50.0) 6.5 else if (cutoffHz <= 65.0) 5.8 else 5.0) + loudnessLowDb
        val punchGainDb = (punchNorm * 3.8) + (bassNorm * 1.4)
        val punchFreq = (cutoffHz * 1.28).coerceIn(48.0, 135.0)
        val cleanMudCutDb = -((bassNorm * 1.5) + (punchNorm * 0.9)).coerceIn(0.0, 2.2)

        bassShelfFilter.configureLowShelf(cutoffHz * 1.08, sr, shelfGainDb, slope = 0.94)
        bassPunchFilter.configurePeaking(punchFreq, sr, punchGainDb, q = 1.35)
        bassCleanDipFilter.configurePeaking(235.0, sr, cleanMudCutDb, q = 1.45)

        cachedSubHarmonicDrive = (bassNorm * 0.16).toFloat()
        if (cachedSubHarmonicDrive > 0.003f) {
            subIsoLowpassFilter.configureLowPass(cutoffHz * 1.05, sr, q = 0.7071)
            subPostLowpassFilter.configureLowPass(cutoffHz * 1.65, sr, q = 0.7071)
        } else {
            subIsoLowpassFilter.setBypass()
            subPostLowpassFilter.setBypass()
        }

        cachedSideLowpassAlpha = ((2.0 * PI * 140.0) / sr).coerceIn(0.005, 0.06).toFloat()
        cachedCrossfeedAlpha = ((2.0 * PI * 720.0) / sr).coerceIn(0.02, 0.18).toFloat()

        // 3. 配置人声通透度、齿音抑制 (De-Esser) 与极高频空气泛音 (Air Exciter)
        val clarityNorm = if (dspActive) (state.vocalClarityStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val deEsserNorm = if (dspActive) (state.deEsserStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val airNorm = if (dspActive) (state.trebleAirStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val warmthNorm = if (dspActive) (state.analogWarmthStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val loudnessHighDb = if (state.equalLoudnessCompensation) 2.8 else 0.0

        vocalMudCutFilter.configurePeaking(265.0, sr, -clarityNorm * 2.2, q = 1.20)
        vocalPresenceFilter.configurePeaking(2750.0, sr, clarityNorm * 4.6, q = 1.10)
        // 6.2kHz 齿音柔化抑制：消除高解析人声与音效提亮后的 s/sh/ch 刺耳毛刺
        val netDeEssDb = -(deEsserNorm * 3.8 + clarityNorm * 0.8).coerceIn(0.0, 4.6)
        deEsserFilter.configurePeaking(6200.0, sr, netDeEssDb, q = 2.40)

        val netAirShelfDb = (clarityNorm * 3.2) + (airNorm * 5.2) + loudnessHighDb - (warmthNorm * 2.4)
        vocalAirShelfFilter.configureHighShelf(11500.0, sr, netAirShelfDb, slope = 0.82)

        // 4. 配置带通隔离的黑胶与黄金胆机温暖度（彻底消除高频互调失真与过零点电流滋滋声）
        warmthBodyFilter.configurePeaking(175.0, sr, warmthNorm * 3.8, q = 0.90)
        cachedWarmthDrive = (warmthNorm * 0.22).toFloat()
        if (cachedWarmthDrive > 0.003f) {
            warmthBandpassFilter.configureBandPass(240.0, sr, q = 0.75)
            warmthPostLowpassFilter.configureLowPass(850.0, sr, q = 0.7071)
        } else {
            warmthBandpassFilter.setBypass()
            warmthPostLowpassFilter.setBypass()
        }

        // 5. 配置瞬态微动态、3D 环绕声场宽度、Bauer 交叉馈送与舞台纵深
        val transientNorm = if (dspActive) (state.transientAttackStrength / 1000f).coerceIn(0f, 1f) else 0f
        cachedTransientDrive = transientNorm * 0.45f

        val virtNorm = if (dspActive) (state.virtualizerStrength / 1000f).coerceIn(0f, 1f) else 0f
        val depthNorm = if (dspActive) (state.soundstageDepthStrength / 1000f).coerceIn(0f, 1f) else 0f
        val crossfeedNorm = if (dspActive) (state.crossfeedStrength / 1000f).coerceIn(0f, 1f) else 0f

        cachedWidthFactor = virtNorm * 1.15f
        cachedHaasCrossMix = virtNorm * 0.24f
        cachedCrossfeedMix = crossfeedNorm * 0.30f
        cachedDepthMix = depthNorm * 0.22f

        activeHaasDelay = ((0.00058f * sr).toInt()).coerceIn(12, maxHaasSamples - 4)
        activeCrossfeedDelay = ((0.00028f * sr).toInt()).coerceIn(6, maxHaasSamples - 4)
        activeDepthDelayL = ((0.0036f * sr).toInt()).coerceIn(48, maxHaasSamples - 4)
        activeDepthDelayR = ((0.0047f * sr).toInt()).coerceIn(64, maxHaasSamples - 4)

        // 6. 配置空间混响房间尺寸、预延迟、干湿比与高频阻尼
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
            reverbInputHpf.configureHighPass(185.0, sr, q = 0.7071)
            reverbInputLpf.configureLowPass(6200.0, sr, q = 0.7071)
            val userWet = (state.reverbWetMix / 1000f).coerceIn(0f, 1f)
            cachedReverbWet = (newReverb.wetMix * 0.55f + userWet * 0.45f).coerceIn(0.04f, 0.52f)
            val userDamp = (state.reverbDamping / 1000f).coerceIn(0.05f, 0.90f)
            cachedReverbDamping = (newReverb.damping * 0.4f + userDamp * 0.6f).coerceIn(0.10f, 0.85f)
            val preDelaySamples = ((state.reverbPreDelayMs.coerceIn(0, 80) / 1000f) * sr).toInt()
            activePreDelaySamples = preDelaySamples.coerceIn(1, maxPreDelaySamples - 4)
        } else {
            reverbInputHpf.setBypass()
            reverbInputLpf.setBypass()
            cachedReverbWet = 0f
        }

        // 7. 基于真实频响曲线峰值增益计算防削波动态预衰减 (True Peak Frequency-Domain Auto-Headroom)
        // 彻底解决低频调节超过 10% 后峰值顶满限幅器导致人声凹陷与失真的问题
        val probeFreqs = doubleArrayOf(31.5, 45.0, 63.0, 90.0, 125.0, 175.0, 250.0, 500.0, 1000.0, 2750.0, 4000.0, 8000.0, 12500.0, 16000.0)
        var maxpeqBoostDb = 0.0
        if (dspActive) {
            for (pf in probeFreqs) {
                val peqDb = AudioEffectManager.evaluatePeqMagnitudeDbAtFrequency(pf, state, sr.toDouble()).toDouble()
                val extraBassDb = if (pf <= 110.0) (shelfGainDb * 0.85 + punchGainDb * 0.65) else 0.0
                val extraVocalDb = if (pf in 2000.0..4500.0) (clarityNorm * 4.2) else if (pf >= 9000.0) maxOf(0.0, netAirShelfDb * 0.8) else 0.0
                val extraWarmthDb = if (pf in 120.0..260.0) (warmthNorm * 3.5) else 0.0
                val combinedDb = peqDb + extraBassDb + extraVocalDb + extraWarmthDb
                if (combinedDb > maxpeqBoostDb) {
                    maxpeqBoostDb = combinedDb
                }
            }
        } else if (state.equalLoudnessCompensation) {
            maxpeqBoostDb = 3.6
        }

        val preampDb = if (dspActive) state.preampGainDb.toDouble() else 0.0
        val loudnessDb = if (dspActive) (state.loudnessGainMb / 100.0).coerceIn(0.0, 15.0) * 0.55 else 0.0
        val totalBoostPeakDb = maxpeqBoostDb + maxOf(0.0, preampDb) + loudnessDb
        val headroomPadDb = if (state.antiClippingEnabled && totalBoostPeakDb > 1.2) {
            // 为低频与 EQ 增益留出充足线性净空，确保低频大动态鼓点绝不挤压人声
            -((totalBoostPeakDb - 1.2) * 0.78).coerceAtMost(11.5)
        } else 0.0
        AudioRhythmTelemetry.headroomGainDb = headroomPadDb.toFloat()

        val netGainDb = (preampDb + loudnessDb + headroomPadDb).coerceIn(-14.0, 10.0)
        cachedLinearPreampAndLoudnessGain = (10.0.pow(netGainDb / 20.0)).toFloat()

        hasActiveEqBands = eqFilters.any { !it.isBypass }
        hasActiveHpfLpf = !hpfFilter.isBypass || !lpfFilter.isBypass
        hasActiveBassFilter = !bassShelfFilter.isBypass || !bassPunchFilter.isBypass || !bassCleanDipFilter.isBypass || cachedSubHarmonicDrive > 0.003f
        hasActiveVocalFilter = !vocalMudCutFilter.isBypass || !vocalPresenceFilter.isBypass || !deEsserFilter.isBypass || !vocalAirShelfFilter.isBypass
        hasActiveWarmthFilter = !warmthBodyFilter.isBypass || cachedWarmthDrive > 0.003f
    }

    private fun processStereoReverb(inL: Float, inR: Float, mode: ReverbMode, outPair: FloatArray) {
        // 先经过 185Hz~6.2kHz 带通净化，消除低频浑浊驻波与高频齿音金属反射
        val cleanL = reverbInputLpf.processLeft(reverbInputHpf.processLeft(inL))
        val cleanR = reverbInputLpf.processRight(reverbInputHpf.processRight(inR))
        val rawMono = (cleanL + cleanR) * 0.24f

        // 经过独立预延迟环状缓冲 (Pre-Delay)，使干声人声清晰居前，混响空间自然铺展在后方
        val preDelayReadIdx = (preDelayWriteIdx - activePreDelaySamples + maxPreDelaySamples) % maxPreDelaySamples
        val inputMono = preDelayBuffer[preDelayReadIdx]
        preDelayBuffer[preDelayWriteIdx] = rawMono
        preDelayWriteIdx = (preDelayWriteIdx + 1) % maxPreDelaySamples

        val feedback = mode.decayFeedback.coerceIn(0.25f, 0.84f)
        val damp1 = cachedReverbDamping
        val damp2 = 1f - damp1

        var sumL = 0f
        var sumR = 0f

        for (i in 0 until 4) {
            val bufL = combBuffersL[i]
            val idxL = combWriteIdxL[i]
            val delayedL = bufL[idxL]
            val storeL = delayedL * damp2 + combFilterStoreL[i] * damp1
            combFilterStoreL[i] = storeL
            bufL[idxL] = (inputMono + storeL * feedback).coerceIn(-2.0f, 2.0f)
            val nextIdxL = idxL + 1
            combWriteIdxL[i] = if (nextIdxL >= combEffectiveLenL[i]) 0 else nextIdxL
            sumL += delayedL

            val bufR = combBuffersR[i]
            val idxR = combWriteIdxR[i]
            val delayedR = bufR[idxR]
            val storeR = delayedR * damp2 + combFilterStoreR[i] * damp1
            combFilterStoreR[i] = storeR
            bufR[idxR] = (inputMono + storeR * feedback).coerceIn(-2.0f, 2.0f)
            val nextIdxR = idxR + 1
            combWriteIdxR[i] = if (nextIdxR >= combEffectiveLenR[i]) 0 else nextIdxR
            sumR += delayedR
        }

        var revL = sumL * 0.26f
        var revR = sumR * 0.26f

        for (i in 0 until 2) {
            val apBufL = allpassBuffersL[i]
            val apIdxL = allpassWriteIdxL[i]
            val bufOutL = apBufL[apIdxL]
            val newInL = (revL + bufOutL * 0.5f).coerceIn(-2.0f, 2.0f)
            apBufL[apIdxL] = newInL
            revL = bufOutL - revL * 0.5f
            val nextApL = apIdxL + 1
            allpassWriteIdxL[i] = if (nextApL >= allpassEffectiveLenL[i]) 0 else nextApL

            val apBufR = allpassBuffersR[i]
            val apIdxR = allpassWriteIdxR[i]
            val bufOutR = apBufR[apIdxR]
            val newInR = (revR + bufOutR * 0.5f).coerceIn(-2.0f, 2.0f)
            apBufR[apIdxR] = newInR
            revR = bufOutR - revR * 0.5f
            val nextApR = apIdxR + 1
            allpassWriteIdxR[i] = if (nextApR >= allpassEffectiveLenR[i]) 0 else nextApR
        }

        val wet = cachedReverbWet
        val dry = 1f - wet * 0.24f
        outPair[0] = inL * dry + revL * wet
        outPair[1] = inR * dry + revR * wet
    }

    private val reverbScratchPair = FloatArray(2)

    /**
     * 只读采样当前音频帧的三频能量与节拍脉冲（即使音效处于 OFF 直通模式也照常运行，驱动「律动光晕」背景与 PEQ 实时频谱）
     */
    private fun sampleRhythmTelemetryFromBuffer(buf: ByteBuffer, encoding: Int, channels: Int, sampleRate: Int) {
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        val frameSize = bytesPerSample * channels
        val totalFrames = buf.remaining() / frameSize
        if (totalFrames <= 0) return

        val dup = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val stride = (totalFrames / 64).coerceAtLeast(1)
        val alphaBass = ((2.0 * PI * 130.0 * stride) / sampleRate).coerceIn(0.02, 0.45).toFloat()
        val alphaMid = ((2.0 * PI * 1800.0 * stride) / sampleRate).coerceIn(0.08, 0.75).toFloat()

        var sumBassSq = 0f
        var sumMidSq = 0f
        var sumHighSq = 0f
        var count = 0

        var frameIdx = 0
        while (frameIdx < totalFrames && dup.remaining() >= frameSize) {
            val pos = buf.position() + frameIdx * frameSize
            dup.position(pos)
            val sL = readNormalizedSample(dup, encoding)
            val sR = if (channels == 2) readNormalizedSample(dup, encoding) else sL
            val mono = (sL + sR) * 0.5f

            telLpBass += alphaBass * (mono - telLpBass)
            telLpMid += alphaMid * (mono - telLpMid)
            val bassComp = telLpBass
            val midComp = telLpMid - telLpBass
            val highComp = mono - telLpMid

            sumBassSq += bassComp * bassComp
            sumMidSq += midComp * midComp
            sumHighSq += highComp * highComp
            count++
            frameIdx += stride
        }

        if (count > 0) {
            val rawBass = (sqrt(sumBassSq / count) * 4.2f).coerceIn(0f, 1f)
            val rawMid = (sqrt(sumMidSq / count) * 4.8f).coerceIn(0f, 1f)
            val rawHigh = (sqrt(sumHighSq / count) * 6.5f).coerceIn(0f, 1f)

            val bassAttack = if (rawBass > telSmoothBass) 0.42f else 0.14f
            val midAttack = if (rawMid > telSmoothMid) 0.38f else 0.16f
            val highAttack = if (rawHigh > telSmoothHigh) 0.45f else 0.18f

            telSmoothBass += bassAttack * (rawBass - telSmoothBass)
            telSmoothMid += midAttack * (rawMid - telSmoothMid)
            telSmoothHigh += highAttack * (rawHigh - telSmoothHigh)

            val bassDelta = (rawBass - telPrevBass).coerceAtLeast(0f)
            telPrevBass = rawBass * 0.7f + telPrevBass * 0.3f
            telBeatImpulse = if (bassDelta > 0.06f) {
                (telBeatImpulse * 0.5f + bassDelta * 2.6f).coerceIn(0f, 1f)
            } else {
                (telBeatImpulse * 0.84f).coerceAtLeast(0f)
            }

            AudioRhythmTelemetry.bassEnergy = telSmoothBass
            AudioRhythmTelemetry.midEnergy = telSmoothMid
            AudioRhythmTelemetry.highEnergy = telSmoothHigh
            AudioRhythmTelemetry.overallLevel = (telSmoothBass * 0.45f + telSmoothMid * 0.35f + telSmoothHigh * 0.20f).coerceIn(0f, 1f)
            AudioRhythmTelemetry.beatImpulse = telBeatImpulse
            AudioRhythmTelemetry.lastUpdateMs = System.currentTimeMillis()

            // 生成 10 段频谱包络供参量均衡器实时频谱显示
            telBinsScratch[0] = (telSmoothBass * 0.95f + telBeatImpulse * 0.15f).coerceIn(0f, 1f)
            telBinsScratch[1] = (telSmoothBass * 1.02f + telBeatImpulse * 0.12f).coerceIn(0f, 1f)
            telBinsScratch[2] = (telSmoothBass * 0.72f + telSmoothMid * 0.32f).coerceIn(0f, 1f)
            telBinsScratch[3] = (telSmoothBass * 0.38f + telSmoothMid * 0.65f).coerceIn(0f, 1f)
            telBinsScratch[4] = (telSmoothMid * 0.92f).coerceIn(0f, 1f)
            telBinsScratch[5] = (telSmoothMid * 1.00f).coerceIn(0f, 1f)
            telBinsScratch[6] = (telSmoothMid * 0.75f + telSmoothHigh * 0.30f).coerceIn(0f, 1f)
            telBinsScratch[7] = (telSmoothMid * 0.42f + telSmoothHigh * 0.64f).coerceIn(0f, 1f)
            telBinsScratch[8] = (telSmoothHigh * 0.92f).coerceIn(0f, 1f)
            telBinsScratch[9] = (telSmoothHigh * 0.78f).coerceIn(0f, 1f)
            AudioRhythmTelemetry.updateBins(telBinsScratch)
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(44100)
        val encoding = inputAudioFormat.encoding
        val channels = inputAudioFormat.channelCount

        // 非破坏性只读采样当前音频帧频谱能量，使「律动光晕」背景与 PEQ 频谱在任意音效状态（含 OFF 直通）下都能同频律动
        sampleRhythmTelemetryFromBuffer(inputBuffer, encoding, channels, sampleRate)

        val state = AudioEffectManager.state.value
        val balance = state.channelBalance.coerceIn(-1f, 1f)
        val dspActive = state.isEnabled && state.currentPreset != EffectPreset.OFF && !state.isAbBypassActive
        val eqLoudnessComp = state.isEnabled && state.equalLoudnessCompensation && !state.isAbBypassActive
        val volNorm = state.isEnabled && state.volumeNormalizationEnabled && !state.isAbBypassActive
        val antiClip = state.antiClippingEnabled && (dspActive || eqLoudnessComp || volNorm)
        val focusDuckGain = PlaybackService.audioFocusDuckGain.coerceIn(0.15f, 1.0f)

        // 若处于 0 音染 / Bit-Perfect 原生直通态（或按住 A/B 原音对比）且声道平衡居中，直接零拷贝透传
        if (abs(balance) < 0.01f && !dspActive && !eqLoudnessComp && !volNorm && focusDuckGain >= 0.99f) {
            AudioRhythmTelemetry.limiterGainReductionDb = 0f
            val out = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
            out.put(inputBuffer)
            out.flip()
            return
        }

        updateDspCoefficientsIfNeeded(state, sampleRate)

        val out = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
        val preGain = cachedLinearPreampAndLoudnessGain
        val targetLeftGain = (if (balance > 0f) (1f - balance).coerceIn(0f, 1f) else 1f) * preGain * focusDuckGain
        val targetRightGain = (if (balance < 0f) (1f + balance).coerceIn(0f, 1f) else 1f) * preGain * focusDuckGain

        val rotSpeed = if (dspActive) state.surroundRotationSpeed else 0
        val phaseStep = if (rotSpeed > 0) (2.0 * PI * (0.20 + rotSpeed * 0.016)) / sampleRate else 0.0

        val subDrive = cachedSubHarmonicDrive
        val sideLpAlpha = cachedSideLowpassAlpha
        val crossfeedAlpha = cachedCrossfeedAlpha
        val crossfeedMix = cachedCrossfeedMix
        val warmthDrive = cachedWarmthDrive
        val transientDrive = cachedTransientDrive
        val widthFactor = cachedWidthFactor
        val haasMix = cachedHaasCrossMix
        val depthMix = cachedDepthMix
        val haasDelay = activeHaasDelay
        val crossDelay = activeCrossfeedDelay
        val depthDelayL = activeDepthDelayL
        val depthDelayR = activeDepthDelayR
        val reverbMode = cachedReverbMode

        // 双声道耦合真包络限制器时间常数（Attack 0.4ms 快速捕峰，Release 85ms 丝滑恢复，绝不产生波形削顶电流声）
        val limAttackCoeff = (1.0 - kotlin.math.exp(-1.0 / (0.0004 * sampleRate))).toFloat().coerceIn(0.01f, 0.5f)
        val limReleaseCoeff = (1.0 - kotlin.math.exp(-1.0 / (0.085 * sampleRate))).toFloat().coerceIn(0.0001f, 0.01f)
        val limThreshold = 0.93f

        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        val frameSize = bytesPerSample * channels
        var maxGainReductionLinear = 1.0f

        while (inputBuffer.remaining() >= frameSize) {
            val rawL = readNormalizedSample(inputBuffer, encoding)
            val rawR = if (channels == 2) readNormalizedSample(inputBuffer, encoding) else rawL

            // 平滑增益过渡（时间常数 ~4ms），消除切换预设或拖动滑杆时的阶跃爆音
            smoothLeftGain += (targetLeftGain - smoothLeftGain) * 0.006f
            smoothRightGain += (targetRightGain - smoothRightGain) * 0.006f

            val normGain = if (volNorm) {
                val frameEnergy = (abs(rawL) + abs(rawR)) * 0.5f
                if (frameEnergy > 0.015f) {
                    val alpha = if (frameEnergy > smoothedEnvelope) 0.0004f else 0.00012f
                    smoothedEnvelope += alpha * (frameEnergy - smoothedEnvelope)
                    val targetEnv = 0.24f
                    val minClamp = if (dspActive) 0.94f else 0.85f
                    val desiredGain = (targetEnv / smoothedEnvelope.coerceAtLeast(0.06f)).coerceIn(minClamp, 1.16f)
                    currentNormGain += 0.0003f * (desiredGain - currentNormGain)
                }
                currentNormGain
            } else 1.0f

            var sL = rawL * normGain * smoothLeftGain
            var sR = rawR * normGain * smoothRightGain

            if (dspActive || eqLoudnessComp) {
                // 0. 高通 (HPF) 与低通 (LPF) 边界滤波
                if (hasActiveHpfLpf) {
                    if (!hpfFilter.isBypass) {
                        sL = hpfFilter.processLeft(sL)
                        sR = hpfFilter.processRight(sR)
                    }
                    if (!lpfFilter.isBypass) {
                        sL = lpfFilter.processLeft(sL)
                        sR = lpfFilter.processRight(sR)
                    }
                }

                // 1. 10段 Biquad 参量均衡滤波级联（当 10 段均为 0dB 平直时直接整组短路跳过）
                if (hasActiveEqBands) {
                    for (b in 0 until 10) {
                        val f = eqFilters[b]
                        if (!f.isBypass) {
                            sL = f.processLeft(sL)
                            sR = f.processRight(sR)
                        }
                    }
                }

                // 2. 纯净分频重低音引擎：低架下潜 + 瞬态冲击 + 235Hz 自动去浑浊 + 带通隔离偶/奇次亚低频泛音
                if (hasActiveBassFilter) {
                    sL = bassCleanDipFilter.processLeft(bassPunchFilter.processLeft(bassShelfFilter.processLeft(sL)))
                    sR = bassCleanDipFilter.processRight(bassPunchFilter.processRight(bassShelfFilter.processRight(sR)))
                    if (subDrive > 0.003f) {
                        // 严格二阶低通提取基频，经 C∞ 平滑软饱和生成谐波后再次经过二阶低通与 DC 阻断，100% 杜绝高频串扰杂音
                        val isoSubL = subIsoLowpassFilter.processLeft(sL)
                        val isoSubR = subIsoLowpassFilter.processRight(sR)
                        val shapedSubL = kotlin.math.tanh((isoSubL * 1.85f).toDouble()).toFloat() - isoSubL * 0.85f
                        val shapedSubR = kotlin.math.tanh((isoSubR * 1.85f).toDouble()).toFloat() - isoSubR * 0.85f
                        val cleanSubL = subPostLowpassFilter.processLeft(shapedSubL)
                        val cleanSubR = subPostLowpassFilter.processRight(shapedSubR)
                        // 20Hz 一阶高通直流阻断 (DC Blocker)
                        val dcBlockedL = cleanSubL - subDcPrevInL + 0.9965f * subDcPrevOutL
                        subDcPrevInL = cleanSubL
                        subDcPrevOutL = dcBlockedL
                        val dcBlockedR = cleanSubR - subDcPrevInR + 0.9965f * subDcPrevOutR
                        subDcPrevInR = cleanSubR
                        subDcPrevOutR = dcBlockedR

                        sL += dcBlockedL * subDrive
                        sR += dcBlockedR * subDrive
                    }
                }

                // 3. 人声通透去浊 (265Hz)、贴耳存在感 (2.75kHz)、齿音柔化 (6.2kHz De-Esser) 与极高频空气泛音 (11.5kHz)
                if (hasActiveVocalFilter) {
                    sL = vocalAirShelfFilter.processLeft(
                        deEsserFilter.processLeft(
                            vocalPresenceFilter.processLeft(
                                vocalMudCutFilter.processLeft(sL)
                            )
                        )
                    )
                    sR = vocalAirShelfFilter.processRight(
                        deEsserFilter.processRight(
                            vocalPresenceFilter.processRight(
                                vocalMudCutFilter.processRight(sR)
                            )
                        )
                    )
                }

                // 4. 带通隔离黑胶与黄金胆机温暖度（仅在 110Hz~680Hz 中低频段生成平滑偶次谐波并经 850Hz 低通回混，消除高频互调电流滋滋声）
                if (hasActiveWarmthFilter) {
                    sL = warmthBodyFilter.processLeft(sL)
                    sR = warmthBodyFilter.processRight(sR)
                    if (warmthDrive > 0.003f) {
                        val midBandL = warmthBandpassFilter.processLeft(sL)
                        val midBandR = warmthBandpassFilter.processRight(sR)
                        // C∞ 平滑非对称三极管传递函数，无任何分段拐点
                        val tubeL = (midBandL + 0.24f * midBandL * midBandL) / (1f + 0.45f * abs(midBandL))
                        val tubeR = (midBandR + 0.24f * midBandR * midBandR) / (1f + 0.45f * abs(midBandR))
                        val dcL = tubeL - warmthDcPrevInL + 0.995f * warmthDcPrevOutL
                        warmthDcPrevInL = tubeL
                        warmthDcPrevOutL = dcL
                        val dcR = tubeR - warmthDcPrevInR + 0.995f * warmthDcPrevOutR
                        warmthDcPrevInR = tubeR
                        warmthDcPrevOutR = dcR

                        val warmFilteredL = warmthPostLowpassFilter.processLeft(dcL)
                        val warmFilteredR = warmthPostLowpassFilter.processRight(dcR)
                        sL += warmFilteredL * warmthDrive
                        sR += warmFilteredR * warmthDrive
                    }
                }

                // 5. 瞬态微动态包络增强器 (Transient Attack Enhancer)
                if (transientDrive > 0.005f) {
                    val absPeak = maxOf(abs(sL), abs(sR))
                    val fastAlpha = if (absPeak > transientFastEnv) 0.12f else 0.004f
                    val slowAlpha = if (absPeak > transientSlowEnv) 0.008f else 0.0015f
                    transientFastEnv += fastAlpha * (absPeak - transientFastEnv)
                    transientSlowEnv += slowAlpha * (absPeak - transientSlowEnv)
                    val attackDiff = (transientFastEnv - transientSlowEnv).coerceIn(0f, 0.35f)
                    val attackBoost = 1f + attackDiff * transientDrive * 1.4f
                    sL *= attackBoost
                    sR *= attackBoost
                }

                // 6. 全维空间声场处理 (M/S 低频锚定拓宽 + Haas 微延迟 + Bauer 耳机交叉馈送 + 舞台纵深早期反射)
                if (channels == 2 && (widthFactor > 0.01f || crossfeedMix > 0.01f || depthMix > 0.01f)) {
                    haasBufferL[haasWriteIdx] = sL
                    haasBufferR[haasWriteIdx] = sR

                    // 6a. Bauer 耳机交叉馈送 (Crossfeed)：将对侧声道 720Hz 以下中低频延迟 280μs 馈入本侧，消除耳机头中效应
                    if (crossfeedMix > 0.01f) {
                        val cfIdx = (haasWriteIdx - crossDelay + maxHaasSamples) % maxHaasSamples
                        crossfeedLpL += crossfeedAlpha * (haasBufferL[cfIdx] - crossfeedLpL)
                        crossfeedLpR += crossfeedAlpha * (haasBufferR[cfIdx] - crossfeedLpR)
                        val nextL = sL * (1f - crossfeedMix * 0.28f) + crossfeedLpR * crossfeedMix
                        val nextR = sR * (1f - crossfeedMix * 0.28f) + crossfeedLpL * crossfeedMix
                        sL = nextL
                        sR = nextR
                    }

                    // 6b. 3D M/S 声场拓宽（140Hz 以下低频严格锁定居中，仅拓宽中高频立体声边信号）
                    if (widthFactor > 0.01f) {
                        val readIdx = (haasWriteIdx - haasDelay + maxHaasSamples) % maxHaasSamples
                        val delayedL = haasBufferL[readIdx]
                        val delayedR = haasBufferR[readIdx]

                        val mid = (sL + sR) * 0.5f
                        val rawSide = (sL - sR) * 0.5f
                        sideLowpass += sideLpAlpha * (rawSide - sideLowpass)
                        val highSide = rawSide - sideLowpass
                        val haasDiff = (delayedL - delayedR) * 0.5f * haasMix
                        val expandedSide = sideLowpass + highSide * (1f + widthFactor) + haasDiff
                        val energyComp = 1f / sqrt(1f + widthFactor * 0.28f)
                        sL = (mid + expandedSide) * energyComp
                        sR = (mid - expandedSide) * energyComp
                    }

                    // 6c. 舞台纵深感早期反射 (Stage Depth Early Reflections)
                    if (depthMix > 0.01f) {
                        val dIdxL = (haasWriteIdx - depthDelayL + maxHaasSamples) % maxHaasSamples
                        val dIdxR = (haasWriteIdx - depthDelayR + maxHaasSamples) % maxHaasSamples
                        val earlyL = haasBufferL[dIdxL] * 0.65f - haasBufferR[dIdxR] * 0.35f
                        val earlyR = haasBufferR[dIdxR] * 0.65f - haasBufferL[dIdxL] * 0.35f
                        sL = sL * (1f - depthMix * 0.18f) + earlyL * depthMix
                        sR = sR * (1f - depthMix * 0.18f) + earlyR * depthMix
                    }

                    haasWriteIdx = (haasWriteIdx + 1) % maxHaasSamples
                }

                // 7. 360° 动态轨迹声场旋转 (恒功率正弦/余弦声像摆位)
                if (phaseStep > 0.0 && channels == 2) {
                    rotationPhase += phaseStep
                    if (rotationPhase > 2.0 * PI) rotationPhase -= 2.0 * PI
                    val pan = sin(rotationPhase).toFloat() * 0.38f
                    val depthMod = (0.94f + 0.06f * cos(rotationPhase).toFloat())
                    sL *= (1f - pan.coerceAtLeast(0f)) * depthMod
                    sR *= (1f + pan.coerceAtMost(0f)) * depthMod
                }

                // 8. 立体声空间混响（含带通净化与独立 Pre-Delay）
                if (reverbMode != ReverbMode.NONE && cachedReverbWet > 0.01f) {
                    processStereoReverb(sL, sR, reverbMode, reverbScratchPair)
                    sL = reverbScratchPair[0]
                    sR = reverbScratchPair[1]
                }
            }

            // 9. 双声道耦合真包络峰值限制器 (True Stereo-Linked Envelope Peak Limiter)
            // 取代旧版逐采样硬折返 waveshaper，在保护不削波的同时 100% 保持正弦波光滑度与人声细节
            var outL = sL
            var outR = sR
            if (antiClip || dspActive || volNorm) {
                val peak = maxOf(abs(outL), abs(outR))
                val coeff = if (peak > limiterEnvelope) limAttackCoeff else limReleaseCoeff
                limiterEnvelope += coeff * (peak - limiterEnvelope)
                if (limiterEnvelope > limThreshold) {
                    val envGain = (limThreshold / limiterEnvelope).coerceIn(0.25f, 1.0f)
                    if (envGain < maxGainReductionLinear) {
                        maxGainReductionLinear = envGain
                    }
                    outL *= envGain
                    outR *= envGain
                }
                // 终极安全软天花板 (仅在极端瞬态 > 0.95f 时柔和收敛至 0.994f)
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

        AudioRhythmTelemetry.limiterGainReductionDb = if (maxGainReductionLinear < 0.999f) {
            (20.0 * kotlin.math.log10(maxGainReductionLinear.toDouble())).toFloat()
        } else 0f

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
                // 加入 TPDF 三角概率密度抖动与四舍五入，彻底消除 16-bit 截断造成的微弱数字颗粒底噪
                val dithered = sample * 32767f + nextTpdfDither()
                out.putShort(dithered.roundToInt().coerceIn(-32768, 32767).toShort())
            }
            C.ENCODING_PCM_24BIT -> {
                val dithered = sample * 8388607f + nextTpdfDither() * 0.5f
                val v = dithered.roundToInt().coerceIn(-8388608, 8388607)
                out.put((v and 0xFF).toByte())
                out.put(((v shr 8) and 0xFF).toByte())
                out.put(((v shr 16) and 0xFF).toByte())
            }
            C.ENCODING_PCM_32BIT -> {
                val v = (sample.toDouble() * 2147483647.0).roundToInt().coerceIn(Int.MIN_VALUE, Int.MAX_VALUE)
                out.putInt(v)
            }
            C.ENCODING_PCM_FLOAT -> {
                out.putFloat(sample.coerceIn(-1f, 1f))
            }
            else -> {
                val dithered = sample * 32767f + nextTpdfDither()
                out.putShort(dithered.roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
    }

    internal fun softLimit(x: Float): Float {
        val threshold = 0.94f
        val absX = abs(x)
        if (absX <= threshold) return x
        val sign = if (x >= 0f) 1f else -1f
        val excess = absX - threshold
        val headroom = 0.996f - threshold
        val compressed = threshold + headroom * (excess / (excess + headroom))
        return (sign * compressed).coerceIn(-0.996f, 0.996f)
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

    private var screenStateReceiverRegistered = false
    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // 手机熄屏锁屏后立即停止后台桌面歌词 160ms 主线程 Ticker，消除熄屏待机耗电
                    stopBackgroundDesktopLyricProgressTicker()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (player?.isPlaying == true && SystemLyricBridge.state.value.floatingCapsuleEnabled) {
                        startBackgroundDesktopLyricProgressTicker()
                    }
                }
            }
        }
    }

    private fun ensureScreenStateReceiverRegistered() {
        if (screenStateReceiverRegistered) return
        runCatching {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenStateReceiver, filter)
            screenStateReceiverRegistered = true
        }
    }

    private val bgLyricProgressRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (p.isPlaying &&
                SystemLyricBridge.state.value.floatingCapsuleEnabled &&
                SystemLyricBridge.isScreenInteractive(applicationContext)
            ) {
                SystemLyricBridge.syncProgressFromService(p.currentPosition, true)
                mainHandler?.postDelayed(this, 160L)
            }
        }
    }

    private fun startBackgroundDesktopLyricProgressTicker() {
        ensureScreenStateReceiverRegistered()
        val h = mainHandler ?: return
        h.removeCallbacks(bgLyricProgressRunnable)
        if (player?.isPlaying == true &&
            SystemLyricBridge.state.value.floatingCapsuleEnabled &&
            SystemLyricBridge.isScreenInteractive(applicationContext)
        ) {
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
        if (screenStateReceiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
            screenStateReceiverRegistered = false
        }
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
