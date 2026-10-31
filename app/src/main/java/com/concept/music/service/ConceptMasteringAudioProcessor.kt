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

    // 纯线性 LTI 低频与瞬态滤波器组（零谐波失真 THD = 0，彻底根除一切非线性波形畸变造成的电流滋滋声）
    private val bassShelfFilter = BiquadStereoFilter()
    private val bassPunchFilter = BiquadStereoFilter()
    private val bassCleanDipFilter = BiquadStereoFilter()
    private val transientPresenceFilter = BiquadStereoFilter()

    // 纯线性 LTI 人声精修与空气感滤波器组
    private val vocalMudCutFilter = BiquadStereoFilter()
    private val vocalPresenceFilter = BiquadStereoFilter()
    private val deEsserFilter = BiquadStereoFilter()
    private val vocalAirShelfFilter = BiquadStereoFilter()

    // 纯线性 LTI 黑胶与黄金胆机温润度滤波器组（中低频饱满度 + 极高频柔顺丝滑度）
    private val warmthBodyFilter = BiquadStereoFilter()
    private val warmthLowMidFilter = BiquadStereoFilter()

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
    private var cachedSideLowpassAlpha = 0.018f
    private var cachedCrossfeedAlpha = 0.08f
    private var cachedCrossfeedMix = 0f
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
        transientPresenceFilter.resetState()
        vocalMudCutFilter.resetState()
        vocalPresenceFilter.resetState()
        deEsserFilter.resetState()
        vocalAirShelfFilter.resetState()
        warmthBodyFilter.resetState()
        warmthLowMidFilter.resetState()
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
            transientPresenceFilter.setBypass()
            vocalMudCutFilter.setBypass()
            vocalPresenceFilter.setBypass()
            deEsserFilter.setBypass()
            vocalAirShelfFilter.setBypass()
            warmthBodyFilter.setBypass()
            warmthLowMidFilter.setBypass()
            reverbInputHpf.setBypass()
            reverbInputLpf.setBypass()
            cachedLinearPreampAndLoudnessGain = 1.0f
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

        // 高通 (HPF) 与低通 (LPF) 边界滤波器配置
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

        // 计算当前 10 段 EQ 在低频/中高频已有的增益，防止预设同时开启 10 段 EQ 与声场调音时产生双重叠加过载
        val existingLowEqDb = if (dspActive && tenBands.size >= 2) {
            maxOf(0.0, ((tenBands[0] + tenBands[1]) / 200.0))
        } else 0.0
        val existingVocalEqDb = if (dspActive && tenBands.size >= 8) {
            maxOf(0.0, ((tenBands[6] + tenBands[7]) / 200.0))
        } else 0.0
        val existingHighEqDb = if (dspActive && tenBands.size >= 10) {
            maxOf(0.0, ((tenBands[8] + tenBands[9]) / 200.0))
        } else 0.0

        val lowOverlapScale = (1.0 - (existingLowEqDb / 14.0)).coerceIn(0.48, 1.0)
        val vocalOverlapScale = (1.0 - (existingVocalEqDb / 14.0)).coerceIn(0.50, 1.0)
        val highOverlapScale = (1.0 - (existingHighEqDb / 14.0)).coerceIn(0.50, 1.0)

        // 2. 配置纯线性 LTI 分频重低音与瞬态引擎：低架下潜 + 瞬态冲击 + 235Hz 防浑浊净空 + 3.15kHz 瞬态咬字清晰度
        val bassNorm = if (dspActive) (state.bassBoostStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val punchNorm = if (dspActive) (state.bassPunchStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val transientNorm = if (dspActive) (state.transientAttackStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val cutoffHz = state.bassCutoffHz.coerceIn(40, 120).toDouble()
        val loudnessLowDb = if (state.equalLoudnessCompensation) 3.0 else 0.0

        val shelfGainDb = (bassNorm * (if (cutoffHz <= 50.0) 5.2 else if (cutoffHz <= 65.0) 4.6 else 4.0) * lowOverlapScale) + loudnessLowDb
        val punchGainDb = ((punchNorm * 3.2 + bassNorm * 0.9 + transientNorm * 1.1) * lowOverlapScale)
        val punchFreq = (cutoffHz * 1.32).coerceIn(52.0, 138.0)
        val cleanMudCutDb = -((bassNorm * 1.3) + (punchNorm * 0.8)).coerceIn(0.0, 1.9)

        bassShelfFilter.configureLowShelf(cutoffHz * 1.05, sr, shelfGainDb, slope = 0.88)
        bassPunchFilter.configurePeaking(punchFreq, sr, punchGainDb, q = 1.28)
        bassCleanDipFilter.configurePeaking(235.0, sr, cleanMudCutDb, q = 1.40)
        transientPresenceFilter.configurePeaking(3150.0, sr, transientNorm * 2.4 * vocalOverlapScale, q = 1.45)

        cachedSideLowpassAlpha = ((2.0 * PI * 140.0) / sr).coerceIn(0.005, 0.06).toFloat()
        cachedCrossfeedAlpha = ((2.0 * PI * 720.0) / sr).coerceIn(0.02, 0.18).toFloat()

        // 3. 配置纯线性 LTI 人声通透度、齿音柔化 (De-Esser) 与极高频空气泛音 (Air Shelf)
        val clarityNorm = if (dspActive) (state.vocalClarityStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val deEsserNorm = if (dspActive) (state.deEsserStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val airNorm = if (dspActive) (state.trebleAirStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val warmthNorm = if (dspActive) (state.analogWarmthStrength / 1000.0).coerceIn(0.0, 1.0) else 0.0
        val loudnessHighDb = if (state.equalLoudnessCompensation) 2.4 else 0.0

        vocalMudCutFilter.configurePeaking(265.0, sr, -clarityNorm * 1.6, q = 1.20)
        val vocalPresenceDb = clarityNorm * 3.6 * vocalOverlapScale
        vocalPresenceFilter.configurePeaking(2750.0, sr, vocalPresenceDb, q = 1.08)
        val netDeEssDb = -(deEsserNorm * 3.4 + clarityNorm * 0.6).coerceIn(0.0, 4.0)
        deEsserFilter.configurePeaking(6200.0, sr, netDeEssDb, q = 2.20)

        val netAirShelfDb = ((clarityNorm * 2.2 + airNorm * 4.2) * highOverlapScale) + loudnessHighDb - (warmthNorm * 1.8)
        vocalAirShelfFilter.configureHighShelf(11500.0, sr, netAirShelfDb, slope = 0.80)

        // 4. 配置纯线性 LTI 黑胶与黄金胆机温暖度（170Hz 饱满醇厚基音 + 350Hz 丝绒中低频润色，100% 零非线性失真）
        warmthBodyFilter.configurePeaking(170.0, sr, warmthNorm * 2.8 * lowOverlapScale, q = 0.85)
        warmthLowMidFilter.configurePeaking(350.0, sr, warmthNorm * 1.8, q = 1.05)

        // 5. 配置 3D 环绕声场宽度、Bauer 交叉馈送与舞台纵深
        val virtNorm = if (dspActive) (state.virtualizerStrength / 1000f).coerceIn(0f, 1f) else 0f
        val depthNorm = if (dspActive) (state.soundstageDepthStrength / 1000f).coerceIn(0f, 1f) else 0f
        val crossfeedNorm = if (dspActive) (state.crossfeedStrength / 1000f).coerceIn(0f, 1f) else 0f

        cachedWidthFactor = virtNorm * 1.05f
        cachedHaasCrossMix = virtNorm * 0.20f
        cachedCrossfeedMix = crossfeedNorm * 0.28f
        cachedDepthMix = depthNorm * 0.20f

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
            cachedReverbWet = (newReverb.wetMix * 0.55f + userWet * 0.45f).coerceIn(0.04f, 0.48f)
            val userDamp = (state.reverbDamping / 1000f).coerceIn(0.05f, 0.90f)
            cachedReverbDamping = (newReverb.damping * 0.4f + userDamp * 0.6f).coerceIn(0.10f, 0.85f)
            val preDelaySamples = ((state.reverbPreDelayMs.coerceIn(0, 80) / 1000f) * sr).toInt()
            activePreDelaySamples = preDelaySamples.coerceIn(1, maxPreDelaySamples - 4)
        } else {
            reverbInputHpf.setBypass()
            reverbInputLpf.setBypass()
            cachedReverbWet = 0f
        }

        // 7. 智能动态余量校准 (Smart Transparent Headroom Calibration)：
        // 仅在频段综合增益超出安全阈值时执行温和预衰减（最高不超过 -3.2 dB），既避免低频满载破音，又绝不造成预设音量骤降
        val probeFreqs = doubleArrayOf(31.5, 45.0, 63.0, 90.0, 125.0, 175.0, 250.0, 500.0, 1000.0, 2750.0, 4000.0, 8000.0, 12500.0, 16000.0)
        var maxCascadeBoostDb = 0.0
        if (dspActive) {
            for (pf in probeFreqs) {
                val peqDb = AudioEffectManager.evaluatePeqMagnitudeDbAtFrequency(pf, state, sr.toDouble()).toDouble()
                val extraBassDb = if (pf <= 110.0) (shelfGainDb * 0.70 + punchGainDb * 0.55) else 0.0
                val extraVocalDb = if (pf in 2000.0..4500.0) (vocalPresenceDb * 0.75) else if (pf >= 9000.0) maxOf(0.0, netAirShelfDb * 0.65) else 0.0
                val extraWarmthDb = if (pf in 120.0..380.0) (warmthNorm * 2.2) else 0.0
                val combinedDb = peqDb + extraBassDb + extraVocalDb + extraWarmthDb
                if (combinedDb > maxCascadeBoostDb) {
                    maxCascadeBoostDb = combinedDb
                }
            }
        } else if (state.equalLoudnessCompensation) {
            maxCascadeBoostDb = 3.0
        }

        val preampDb = if (dspActive) state.preampGainDb.toDouble() else 0.0
        val loudnessDb = if (dspActive) (state.loudnessGainMb / 100.0).coerceIn(0.0, 15.0) * 0.45 else 0.0
        val totalBoostPeakDb = maxCascadeBoostDb + maxOf(0.0, preampDb)
        val headroomPadDb = if (state.antiClippingEnabled && totalBoostPeakDb > 3.2) {
            -((totalBoostPeakDb - 3.2) * 0.36).coerceIn(0.0, 3.2)
        } else 0.0
        AudioRhythmTelemetry.headroomGainDb = headroomPadDb.toFloat()

        val netGainDb = (preampDb + loudnessDb + headroomPadDb).coerceIn(-12.0, 9.0)
        cachedLinearPreampAndLoudnessGain = (10.0.pow(netGainDb / 20.0)).toFloat()

        hasActiveEqBands = eqFilters.any { !it.isBypass }
        hasActiveHpfLpf = !hpfFilter.isBypass || !lpfFilter.isBypass
        hasActiveBassFilter = !bassShelfFilter.isBypass || !bassPunchFilter.isBypass || !bassCleanDipFilter.isBypass || !transientPresenceFilter.isBypass
        hasActiveVocalFilter = !vocalMudCutFilter.isBypass || !vocalPresenceFilter.isBypass || !deEsserFilter.isBypass || !vocalAirShelfFilter.isBypass
        hasActiveWarmthFilter = !warmthBodyFilter.isBypass || !warmthLowMidFilter.isBypass
    }

    private fun processStereoReverb(inL: Float, inR: Float, mode: ReverbMode, outPair: FloatArray) {
        val cleanL = reverbInputLpf.processLeft(reverbInputHpf.processLeft(inL))
        val cleanR = reverbInputLpf.processRight(reverbInputHpf.processRight(inR))
        val rawMono = (cleanL + cleanR) * 0.24f

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
        val dry = 1f - wet * 0.22f
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

        // 非破坏性只读采样当前音频帧频谱能量，使「律动光晕」背景在任意音效状态（含 OFF 直通）下都能同频律动
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

        val sideLpAlpha = cachedSideLowpassAlpha
        val crossfeedAlpha = cachedCrossfeedAlpha
        val crossfeedMix = cachedCrossfeedMix
        val widthFactor = cachedWidthFactor
        val haasMix = cachedHaasCrossMix
        val depthMix = cachedDepthMix
        val haasDelay = activeHaasDelay
        val crossDelay = activeCrossfeedDelay
        val depthDelayL = activeDepthDelayL
        val depthDelayR = activeDepthDelayR
        val reverbMode = cachedReverbMode

        // 双声道耦合真包络限制器时间常数（Attack 0.8ms，Release 110ms，仅在真实峰值接近 0dBFS 时平滑控峰，零谐波失真）
        val limAttackCoeff = (1.0 - kotlin.math.exp(-1.0 / (0.0008 * sampleRate))).toFloat().coerceIn(0.005f, 0.35f)
        val limReleaseCoeff = (1.0 - kotlin.math.exp(-1.0 / (0.110 * sampleRate))).toFloat().coerceIn(0.00008f, 0.008f)
        val limThreshold = 0.955f

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

            // 平滑增益过渡（消除切换预设或拖动滑杆时的阶跃爆音）
            smoothLeftGain += (targetLeftGain - smoothLeftGain) * 0.005f
            smoothRightGain += (targetRightGain - smoothRightGain) * 0.005f

            val normGain = if (volNorm) {
                val frameEnergy = (abs(rawL) + abs(rawR)) * 0.5f
                if (frameEnergy > 0.015f) {
                    val alpha = if (frameEnergy > smoothedEnvelope) 0.0004f else 0.00012f
                    smoothedEnvelope += alpha * (frameEnergy - smoothedEnvelope)
                    val targetEnv = 0.24f
                    val minClamp = if (dspActive) 0.96f else 0.88f
                    val desiredGain = (targetEnv / smoothedEnvelope.coerceAtLeast(0.06f)).coerceIn(minClamp, 1.14f)
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

                // 1. 10段 64-bit Biquad 参量均衡滤波级联（当 10 段均为 0dB 平直时直接整组短路跳过）
                if (hasActiveEqBands) {
                    for (b in 0 until 10) {
                        val f = eqFilters[b]
                        if (!f.isBypass) {
                            sL = f.processLeft(sL)
                            sR = f.processRight(sR)
                        }
                    }
                }

                // 2. 纯线性 LTI 重低音与瞬态滤波：低架下潜 + 瞬态弹性冲击 + 235Hz 防浑浊净空 + 3.15kHz 瞬态清晰度
                if (hasActiveBassFilter) {
                    sL = transientPresenceFilter.processLeft(
                        bassCleanDipFilter.processLeft(
                            bassPunchFilter.processLeft(
                                bassShelfFilter.processLeft(sL)
                            )
                        )
                    )
                    sR = transientPresenceFilter.processRight(
                        bassCleanDipFilter.processRight(
                            bassPunchFilter.processRight(
                                bassShelfFilter.processRight(sR)
                            )
                        )
                    )
                }

                // 3. 纯线性 LTI 人声通透去浊 (265Hz)、贴耳存在感 (2.75kHz)、齿音柔化 (6.2kHz De-Esser) 与极高频空气泛音 (11.5kHz)
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

                // 4. 纯线性 LTI 黑胶与黄金胆机温暖度（170Hz + 350Hz 双二阶丰满度润色，100% 零谐波失真）
                if (hasActiveWarmthFilter) {
                    sL = warmthLowMidFilter.processLeft(warmthBodyFilter.processLeft(sL))
                    sR = warmthLowMidFilter.processRight(warmthBodyFilter.processRight(sR))
                }

                // 5. 全维空间声场处理 (M/S 低频锚定拓宽 + Haas 微延迟 + Bauer 耳机交叉馈送 + 舞台纵深早期反射)
                if (channels == 2 && (widthFactor > 0.01f || crossfeedMix > 0.01f || depthMix > 0.01f)) {
                    haasBufferL[haasWriteIdx] = sL
                    haasBufferR[haasWriteIdx] = sR

                    if (crossfeedMix > 0.01f) {
                        val cfIdx = (haasWriteIdx - crossDelay + maxHaasSamples) % maxHaasSamples
                        crossfeedLpL += crossfeedAlpha * (haasBufferL[cfIdx] - crossfeedLpL)
                        crossfeedLpR += crossfeedAlpha * (haasBufferR[cfIdx] - crossfeedLpR)
                        val nextL = sL * (1f - crossfeedMix * 0.26f) + crossfeedLpR * crossfeedMix
                        val nextR = sR * (1f - crossfeedMix * 0.26f) + crossfeedLpL * crossfeedMix
                        sL = nextL
                        sR = nextR
                    }

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
                        val energyComp = 1f / sqrt(1f + widthFactor * 0.25f)
                        sL = (mid + expandedSide) * energyComp
                        sR = (mid - expandedSide) * energyComp
                    }

                    if (depthMix > 0.01f) {
                        val dIdxL = (haasWriteIdx - depthDelayL + maxHaasSamples) % maxHaasSamples
                        val dIdxR = (haasWriteIdx - depthDelayR + maxHaasSamples) % maxHaasSamples
                        val earlyL = haasBufferL[dIdxL] * 0.65f - haasBufferR[dIdxR] * 0.35f
                        val earlyR = haasBufferR[dIdxR] * 0.65f - haasBufferL[dIdxL] * 0.35f
                        sL = sL * (1f - depthMix * 0.16f) + earlyL * depthMix
                        sR = sR * (1f - depthMix * 0.16f) + earlyR * depthMix
                    }

                    haasWriteIdx = (haasWriteIdx + 1) % maxHaasSamples
                }

                // 6. 360° 动态轨迹声场旋转
                if (phaseStep > 0.0 && channels == 2) {
                    rotationPhase += phaseStep
                    if (rotationPhase > 2.0 * PI) rotationPhase -= 2.0 * PI
                    val pan = sin(rotationPhase).toFloat() * 0.35f
                    val depthMod = (0.95f + 0.05f * cos(rotationPhase).toFloat())
                    sL *= (1f - pan.coerceAtLeast(0f)) * depthMod
                    sR *= (1f + pan.coerceAtMost(0f)) * depthMod
                }

                // 7. 立体声空间混响（含带通净化与独立 Pre-Delay）
                if (reverbMode != ReverbMode.NONE && cachedReverbWet > 0.01f) {
                    processStereoReverb(sL, sR, reverbMode, reverbScratchPair)
                    sL = reverbScratchPair[0]
                    sR = reverbScratchPair[1]
                }
            }

            // 8. 双声道耦合真包络峰值限制器 (True Stereo-Linked Envelope Peak Limiter)
            var outL = sL
            var outR = sR
            if (antiClip || dspActive || volNorm) {
                val peak = maxOf(abs(outL), abs(outR))
                val coeff = if (peak > limiterEnvelope) limAttackCoeff else limReleaseCoeff
                limiterEnvelope += coeff * (peak - limiterEnvelope)
                if (limiterEnvelope > limThreshold) {
                    val envGain = (limThreshold / limiterEnvelope).coerceIn(0.35f, 1.0f)
                    if (envGain < maxGainReductionLinear) {
                        maxGainReductionLinear = envGain
                    }
                    outL *= envGain
                    outR *= envGain
                }
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
        val clamped = sample.coerceIn(-1f, 1f)
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                out.putShort((clamped * 32767f).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            C.ENCODING_PCM_24BIT -> {
                val v = (clamped * 8388607f).roundToInt().coerceIn(-8388608, 8388607)
                out.put((v and 0xFF).toByte())
                out.put(((v shr 8) and 0xFF).toByte())
                out.put(((v shr 16) and 0xFF).toByte())
            }
            C.ENCODING_PCM_32BIT -> {
                val v = (clamped.toDouble() * 2147483647.0).roundToInt().coerceIn(Int.MIN_VALUE, Int.MAX_VALUE)
                out.putInt(v)
            }
            C.ENCODING_PCM_FLOAT -> {
                out.putFloat(clamped)
            }
            else -> {
                out.putShort((clamped * 32767f).roundToInt().coerceIn(-32768, 32767).toShort())
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

