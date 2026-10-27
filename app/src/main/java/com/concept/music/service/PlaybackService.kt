package com.concept.music.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.concept.music.MainActivity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 概念版发烧级实时 PCM 母带音频处理器
 * 支持 16-bit / 24-bit / 32-bit / Float PCM 与单声道/立体声采样帧：
 * 1. 左右声道声像平衡 (Stereo Channel Balance)
 * 2. M/S 立体声场宽度扩展与 3D 环绕声像微旋补偿
 * 3. 智能跨曲目响度均衡 (Volume Normalization) 与软膝防破音数字限幅器 (Soft-Knee Anti-Clipping Limiter)
 * 4. 64 采样线性增益平滑过渡与 Flush/Reset 相位清零，彻底消除 Seek/切换音效时的咔哒爆音
 */
@OptIn(UnstableApi::class)
class ConceptMasteringAudioProcessor : BaseAudioProcessor() {

    private class BiquadBand {
        var b0 = 1.0f
        var b1 = 0.0f
        var b2 = 0.0f
        var a1 = 0.0f
        var a2 = 0.0f

        var z1L = 0.0f
        var z2L = 0.0f
        var z1R = 0.0f
        var z2R = 0.0f

        fun resetState() {
            z1L = 0.0f
            z2L = 0.0f
            z1R = 0.0f
            z2R = 0.0f
        }

        fun setIdentity() {
            b0 = 1.0f
            b1 = 0.0f
            b2 = 0.0f
            a1 = 0.0f
            a2 = 0.0f
        }

        fun configurePeakingEq(freqHz: Double, sampleRate: Int, q: Double, gainDb: Double) {
            if (abs(gainDb) < 0.05 || sampleRate <= 0) {
                setIdentity()
                return
            }
            val clampedFreq = freqHz.coerceIn(20.0, sampleRate * 0.45)
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * clampedFreq / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q.coerceAtLeast(0.2))

            val rawB0 = 1.0 + alpha * a
            val rawB1 = -2.0 * cosW0
            val rawB2 = 1.0 - alpha * a
            val rawA0 = 1.0 + alpha / a
            val rawA1 = -2.0 * cosW0
            val rawA2 = 1.0 - alpha / a

            b0 = (rawB0 / rawA0).toFloat()
            b1 = (rawB1 / rawA0).toFloat()
            b2 = (rawB2 / rawA0).toFloat()
            a1 = (rawA1 / rawA0).toFloat()
            a2 = (rawA2 / rawA0).toFloat()
        }

        fun configureLowShelf(freqHz: Double, sampleRate: Int, q: Double, gainDb: Double) {
            if (abs(gainDb) < 0.05 || sampleRate <= 0) {
                setIdentity()
                return
            }
            val clampedFreq = freqHz.coerceIn(20.0, sampleRate * 0.45)
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * clampedFreq / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q.coerceAtLeast(0.2))
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

            val rawB0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
            val rawB1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
            val rawB2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
            val rawA0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
            val rawA1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
            val rawA2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha

            b0 = (rawB0 / rawA0).toFloat()
            b1 = (rawB1 / rawA0).toFloat()
            b2 = (rawB2 / rawA0).toFloat()
            a1 = (rawA1 / rawA0).toFloat()
            a2 = (rawA2 / rawA0).toFloat()
        }

        fun configureHighShelf(freqHz: Double, sampleRate: Int, q: Double, gainDb: Double) {
            if (abs(gainDb) < 0.05 || sampleRate <= 0) {
                setIdentity()
                return
            }
            val clampedFreq = freqHz.coerceIn(20.0, sampleRate * 0.45)
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * clampedFreq / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q.coerceAtLeast(0.2))
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

            val rawB0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
            val rawB1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
            val rawB2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
            val rawA0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
            val rawA1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
            val rawA2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha

            b0 = (rawB0 / rawA0).toFloat()
            b1 = (rawB1 / rawA0).toFloat()
            b2 = (rawB2 / rawA0).toFloat()
            a1 = (rawA1 / rawA0).toFloat()
            a2 = (rawA2 / rawA0).toFloat()
        }

        fun processLeft(x: Float): Float {
            val y = b0 * x + z1L
            z1L = b1 * x - a1 * y + z2L
            z2L = b2 * x - a2 * y
            return y
        }

        fun processRight(x: Float): Float {
            val y = b0 * x + z1R
            z1R = b1 * x - a1 * y + z2R
            z2R = b2 * x - a2 * y
            return y
        }
    }

    companion object {
        private val TEN_BAND_CENTERS_HZ = doubleArrayOf(
            31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
        )
    }

    private val tenBandFilters = Array(10) { BiquadBand() }
    private val bassSubFilter = BiquadBand()
    private val bassPunchFilter = BiquadBand()
    private val vocalPresenceFilter = BiquadBand()
    private val vocalAirFilter = BiquadBand()
    private val warmthBodyFilter = BiquadBand()
    private val warmthRollOffFilter = BiquadBand()

    // 立体声空间混响环形延迟网络 (Stereo Comb + Allpass Reverb Tank)
    private val reverbMaxSamples = 16384 // Enough for ~340ms at 48kHz
    private val combBufL1 = FloatArray(reverbMaxSamples)
    private val combBufR1 = FloatArray(reverbMaxSamples)
    private val combBufL2 = FloatArray(reverbMaxSamples)
    private val combBufR2 = FloatArray(reverbMaxSamples)
    private val allpassBufL = FloatArray(4096)
    private val allpassBufR = FloatArray(4096)
    private var combIdx1 = 0
    private var combIdx2 = 0
    private var allpassIdx = 0
    private var reverbLpL = 0.0f
    private var reverbLpR = 0.0f

    private var cachedSampleRate = 0
    private var cachedTenBandsHash = 0
    private var cachedBassStrength = -1
    private var cachedBassCutoff = -1
    private var cachedVocalClarity = -1
    private var cachedAnalogWarmth = -1
    private var cachedEqLoudness = false
    private var cachedDspActive = false

    private var rotationPhase = 0.0
    private var smoothedEnvelope = 0.22f
    private var currentNormGain = 1.0f
    private var smoothLeftGain = 1.0f
    private var smoothRightGain = 1.0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val validEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_32BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val validChannels = inputAudioFormat.channelCount == 1 || inputAudioFormat.channelCount == 2
        return if (validEncoding && validChannels) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    private fun clearDspStates() {
        tenBandFilters.forEach { it.resetState() }
        bassSubFilter.resetState()
        bassPunchFilter.resetState()
        vocalPresenceFilter.resetState()
        vocalAirFilter.resetState()
        warmthBodyFilter.resetState()
        warmthRollOffFilter.resetState()
        combBufL1.fill(0f)
        combBufR1.fill(0f)
        combBufL2.fill(0f)
        combBufR2.fill(0f)
        allpassBufL.fill(0f)
        allpassBufR.fill(0f)
        combIdx1 = 0
        combIdx2 = 0
        allpassIdx = 0
        reverbLpL = 0f
        reverbLpR = 0f
        cachedSampleRate = 0
    }

    override fun onFlush() {
        rotationPhase = 0.0
        smoothedEnvelope = 0.22f
        currentNormGain = 1.0f
        smoothLeftGain = 1.0f
        smoothRightGain = 1.0f
        clearDspStates()
    }

    override fun onReset() {
        onFlush()
    }

    private fun updateFilterCoefficientsIfNeeded(
        sampleRate: Int,
        dspActive: Boolean,
        tenBands: List<Int>,
        bassStrength: Int,
        bassCutoffHz: Int,
        vocalClarity: Int,
        analogWarmth: Int,
        eqLoudnessComp: Boolean
    ) {
        val bandsHash = tenBands.hashCode()
        if (sampleRate == cachedSampleRate &&
            dspActive == cachedDspActive &&
            bandsHash == cachedTenBandsHash &&
            bassStrength == cachedBassStrength &&
            bassCutoffHz == cachedBassCutoff &&
            vocalClarity == cachedVocalClarity &&
            analogWarmth == cachedAnalogWarmth &&
            eqLoudnessComp == cachedEqLoudness
        ) {
            return
        }

        cachedSampleRate = sampleRate
        cachedDspActive = dspActive
        cachedTenBandsHash = bandsHash
        cachedBassStrength = bassStrength
        cachedBassCutoff = bassCutoffHz
        cachedVocalClarity = vocalClarity
        cachedAnalogWarmth = analogWarmth
        cachedEqLoudness = eqLoudnessComp

        // 1. 10 段参量均衡器 (31Hz ~ 16kHz, -10dB ~ +10dB) + 等响度高低频听感曲线补偿
        for (i in 0 until 10) {
            val baseMb = if (dspActive) (tenBands.getOrNull(i) ?: 0) else 0
            val loudnessCompMb = if (eqLoudnessComp) {
                when (i) {
                    0 -> 380  // +3.8 dB @ 31Hz
                    1 -> 280  // +2.8 dB @ 62Hz
                    2 -> 140  // +1.4 dB @ 125Hz
                    8 -> 180  // +1.8 dB @ 8kHz
                    9 -> 280  // +2.8 dB @ 16kHz
                    else -> 0
                }
            } else 0
            val totalGainDb = ((baseMb + loudnessCompMb).coerceIn(-1200, 1200)) / 100.0
            val freq = TEN_BAND_CENTERS_HZ[i]
            when (i) {
                0 -> tenBandFilters[i].configureLowShelf(freq, sampleRate, 0.707, totalGainDb)
                9 -> tenBandFilters[i].configureHighShelf(freq, sampleRate, 0.707, totalGainDb)
                else -> tenBandFilters[i].configurePeakingEq(freq, sampleRate, 1.414, totalGainDb)
            }
        }

        // 2. 重低音下潜与低频频点 (45Hz 深潜 / 60Hz 均衡 / 85Hz 紧凑)
        if (dspActive && bassStrength > 0) {
            val normBass = (bassStrength / 1000.0).coerceIn(0.0, 1.0)
            val cutoff = bassCutoffHz.coerceIn(40, 120).toDouble()
            val shelfDb = normBass * 6.5
            val punchDb = normBass * 4.5
            val punchQ = when {
                bassCutoffHz <= 50 -> 1.35 // 45Hz 深潜下潜
                bassCutoffHz >= 75 -> 1.65 // 85Hz 紧凑弹性
                else -> 1.15               // 60Hz 均衡饱满
            }
            bassSubFilter.configureLowShelf(cutoff * 1.15, sampleRate, 0.707, shelfDb)
            bassPunchFilter.configurePeakingEq(cutoff, sampleRate, punchQ, punchDb)
        } else {
            bassSubFilter.setIdentity()
            bassPunchFilter.setIdentity()
        }

        // 3. 人声通透度 (2.6kHz 咬字解析 + 9kHz 呼吸空气泛音激励)
        if (dspActive && vocalClarity > 0) {
            val normVocal = (vocalClarity / 1000.0).coerceIn(0.0, 1.0)
            vocalPresenceFilter.configurePeakingEq(2600.0, sampleRate, 1.15, normVocal * 5.2)
            vocalAirFilter.configureHighShelf(9000.0, sampleRate, 0.75, normVocal * 3.6)
        } else {
            vocalPresenceFilter.setIdentity()
            vocalAirFilter.setIdentity()
        }

        // 4. 黑胶与胆机温暖度 (220Hz 电子管醇厚中低频 + 11kHz 毛刺柔化)
        if (dspActive && analogWarmth > 0) {
            val normWarmth = (analogWarmth / 1000.0).coerceIn(0.0, 1.0)
            warmthBodyFilter.configurePeakingEq(220.0, sampleRate, 0.85, normWarmth * 3.8)
            warmthRollOffFilter.configureHighShelf(11000.0, sampleRate, 0.707, -normWarmth * 2.4)
        } else {
            warmthBodyFilter.setIdentity()
            warmthRollOffFilter.setIdentity()
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val state = AudioEffectManager.state.value
        val balance = state.channelBalance.coerceIn(-1f, 1f)
        val dspActive = state.isEnabled && state.currentPreset != EffectPreset.OFF
        val widthFactor = if (dspActive) (state.virtualizerStrength / 1000f) * 0.52f else 0f
        val rotSpeed = if (dspActive) state.surroundRotationSpeed else 0
        val warmthFactor = if (dspActive) (state.analogWarmthStrength / 1000f) * 0.22f else 0f
        val eqLoudnessComp = state.equalLoudnessCompensation
        val volNorm = state.isEnabled && state.volumeNormalizationEnabled
        val reverbMode = if (dspActive) state.reverbMode else ReverbMode.NONE
        val preampDb = if (dspActive) state.preampGainDb.coerceIn(-6f, 6f) else 0f
        val loudnessDb = if (dspActive) (state.loudnessGainMb.coerceIn(0, 1500) / 100f) * 0.65f else 0f
        val tenBands = state.tenBandLevels
        val hasTenBandEq = dspActive && tenBands.any { it != 0 }
        val hasToneShaping = hasTenBandEq ||
            (dspActive && (state.bassBoostStrength > 0 || state.vocalClarityStrength > 0 || state.analogWarmthStrength > 0)) ||
            eqLoudnessComp
        val antiClip = state.antiClippingEnabled && (dspActive || eqLoudnessComp || volNorm)

        // 若处于 0 音染 / Bit-Perfect 原生直通态且声道平衡居中，直接零拷贝透传
        if (abs(balance) < 0.01f && !dspActive && !eqLoudnessComp && !volNorm) {
            val out = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(44100)
        if (hasToneShaping) {
            updateFilterCoefficientsIfNeeded(
                sampleRate = sampleRate,
                dspActive = dspActive,
                tenBands = tenBands,
                bassStrength = state.bassBoostStrength,
                bassCutoffHz = state.bassCutoffHz,
                vocalClarity = state.vocalClarityStrength,
                analogWarmth = state.analogWarmthStrength,
                eqLoudnessComp = eqLoudnessComp
            )
        }

        // 计算前置放大增益 (preampGainDb) + 动态响度增益 (loudnessGainMb) + 等响度补偿
        val totalGainDb = preampDb + loudnessDb + (if (eqLoudnessComp) 1.2f else 0f)
        val masterGainLinear = 10.0.pow(totalGainDb.toDouble() / 20.0).toFloat().coerceIn(0.25f, 4.0f)

        val out = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
        val targetLeftGain = (if (balance > 0f) (1f - balance).coerceIn(0f, 1f) else 1f) * masterGainLinear
        val targetRightGain = (if (balance < 0f) (1f + balance).coerceIn(0f, 1f) else 1f) * masterGainLinear
        val phaseStep = if (rotSpeed > 0) (2.0 * PI * (0.15 + rotSpeed * 0.012)) / sampleRate else 0.0

        // 配置空间混响房间延迟与衰减反馈参数
        val hasReverb = reverbMode != ReverbMode.NONE
        val (revDelayMs1, revFeedback, revWetMix) = when (reverbMode) {
            ReverbMode.NONE -> Triple(0f, 0f, 0f)
            ReverbMode.SMALL_ROOM -> Triple(26f, 0.34f, 0.14f)
            ReverbMode.MEDIUM_ROOM -> Triple(42f, 0.44f, 0.18f)
            ReverbMode.LARGE_ROOM -> Triple(64f, 0.52f, 0.22f)
            ReverbMode.MEDIUM_HALL -> Triple(88f, 0.60f, 0.26f)
            ReverbMode.LARGE_HALL -> Triple(118f, 0.68f, 0.30f)
            ReverbMode.PLATE -> Triple(54f, 0.62f, 0.24f)
        }
        val delaySamples1 = ((revDelayMs1 * sampleRate) / 1000f).toInt().coerceIn(128, reverbMaxSamples - 64)
        val delaySamples2 = ((delaySamples1 * 1.37f).toInt()).coerceIn(192, reverbMaxSamples - 64)
        val allpassSamples = ((11.7f * sampleRate) / 1000f).toInt().coerceIn(64, allpassBufL.size - 16)

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

            // 64 采样线性增益平滑，防止音效开关瞬间产生阶跃爆音
            smoothLeftGain += (targetLeftGain - smoothLeftGain) * 0.03125f
            smoothRightGain += (targetRightGain - smoothRightGain) * 0.03125f

            val normGain = if (volNorm) {
                val frameEnergy = (abs(rawL) + abs(rawR)) * 0.5f
                if (frameEnergy > 0.015f) {
                    val alpha = if (frameEnergy > smoothedEnvelope) 0.0005f else 0.00015f
                    smoothedEnvelope += alpha * (frameEnergy - smoothedEnvelope)
                    val targetEnv = 0.24f
                    val desiredGain = (targetEnv / smoothedEnvelope.coerceAtLeast(0.06f)).coerceIn(0.82f, 1.22f)
                    currentNormGain += 0.0004f * (desiredGain - currentNormGain)
                }
                currentNormGain
            } else 1.0f

            var sigL = rawL * normGain
            var sigR = rawR * normGain

            // 1. 10 段参量均衡器 + 重低音频点下潜 + 人声通透激励 + 胆机温暖度滤波链
            if (hasToneShaping) {
                if (hasTenBandEq || eqLoudnessComp) {
                    for (b in 0 until 10) {
                        val filter = tenBandFilters[b]
                        sigL = filter.processLeft(sigL)
                        sigR = filter.processRight(sigR)
                    }
                }
                if (dspActive && state.bassBoostStrength > 0) {
                    sigL = bassPunchFilter.processLeft(bassSubFilter.processLeft(sigL))
                    sigR = bassPunchFilter.processRight(bassSubFilter.processRight(sigR))
                }
                if (dspActive && state.vocalClarityStrength > 0) {
                    sigL = vocalAirFilter.processLeft(vocalPresenceFilter.processLeft(sigL))
                    sigR = vocalAirFilter.processRight(vocalPresenceFilter.processRight(sigR))
                }
                if (dspActive && state.analogWarmthStrength > 0) {
                    sigL = warmthRollOffFilter.processLeft(warmthBodyFilter.processLeft(sigL))
                    sigR = warmthRollOffFilter.processRight(warmthBodyFilter.processRight(sigR))
                }
            }

            // 2. M/S 立体声场宽度扩展
            val mid = (sigL + sigR) * 0.5f
            val side = if (channels == 2) (sigL - sigR) * 0.5f * (1f + widthFactor) else 0f
            var outL = mid + side
            var outR = mid - side

            // 3. 偶次谐波电子管微饱和润色 (Tube / Vinyl Even-Harmonic Saturation)
            if (warmthFactor > 0.005f) {
                outL += warmthFactor * (outL * outL * if (outL >= 0f) 0.38f else -0.24f)
                outR += warmthFactor * (outR * outR * if (outR >= 0f) 0.38f else -0.24f)
            }

            // 4. 空间混响处理 (Schroeder Stereo Comb + Allpass Reverb)
            if (hasReverb) {
                val c1L = combBufL1[combIdx1]
                val c1R = combBufR1[combIdx1]
                val c2L = combBufL2[combIdx2]
                val c2R = combBufR2[combIdx2]

                reverbLpL = 0.65f * (c1L + c2L) * 0.5f + 0.35f * reverbLpL
                reverbLpR = 0.65f * (c1R + c2R) * 0.5f + 0.35f * reverbLpR

                // 立体声交叉反馈营造宽阔空间包裹感
                combBufL1[combIdx1] = (outL + (reverbLpL * 0.82f + reverbLpR * 0.18f) * revFeedback).coerceIn(-1.5f, 1.5f)
                combBufR1[combIdx1] = (outR + (reverbLpR * 0.82f + reverbLpL * 0.18f) * revFeedback).coerceIn(-1.5f, 1.5f)
                combBufL2[combIdx2] = (outL * 0.9f + (reverbLpR * 0.75f + reverbLpL * 0.25f) * revFeedback * 0.92f).coerceIn(-1.5f, 1.5f)
                combBufR2[combIdx2] = (outR * 0.9f + (reverbLpL * 0.75f + reverbLpR * 0.25f) * revFeedback * 0.92f).coerceIn(-1.5f, 1.5f)

                combIdx1 = (combIdx1 + 1) % delaySamples1
                combIdx2 = (combIdx2 + 1) % delaySamples2

                val apDelayedL = allpassBufL[allpassIdx]
                val apDelayedR = allpassBufR[allpassIdx]
                val apInL = (c1L + c2L) * 0.5f
                val apInR = (c1R + c2R) * 0.5f
                allpassBufL[allpassIdx] = (apInL + 0.5f * apDelayedL).coerceIn(-1.5f, 1.5f)
                allpassBufR[allpassIdx] = (apInR + 0.5f * apDelayedR).coerceIn(-1.5f, 1.5f)
                allpassIdx = (allpassIdx + 1) % allpassSamples

                val wetL = apDelayedL - 0.5f * apInL
                val wetR = apDelayedR - 0.5f * apInR
                outL = outL * (1f - revWetMix * 0.35f) + wetL * revWetMix
                outR = outR * (1f - revWetMix * 0.35f) + wetR * revWetMix
            }

            // 5. 3D 环绕头部动态轨迹旋转
            if (phaseStep > 0.0 && channels == 2) {
                rotationPhase += phaseStep
                if (rotationPhase > 2.0 * PI) rotationPhase -= 2.0 * PI
                val pan = sin(rotationPhase).toFloat() * 0.22f
                outL *= (1f - pan.coerceAtLeast(0f))
                outR *= (1f + pan.coerceAtMost(0f))
            }

            // 6. 前置放大增益、动态响度与左右声道平衡
            outL *= smoothLeftGain
            outR *= smoothRightGain

            // 7. 软膝防破音数字限幅保护 (Anti-Clipping Soft-Knee Limiter)
            if (antiClip || volNorm) {
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
        val threshold = 0.88f
        val absX = abs(x)
        if (absX <= threshold) return x
        val sign = if (x >= 0f) 1f else -1f
        val excess = absX - threshold
        val compressed = threshold + (1f - threshold) * (excess / (excess + (1f - threshold)))
        return (sign * compressed).coerceIn(-0.995f, 0.995f)
    }
}

/**
 * AndroidX Media3 官方后台播放服务
 * 提供锁屏控制、通知栏控制面板、蓝牙耳机按键拦截与 AudioFocus 音频焦点管理
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    companion object {
        @Volatile
        private var activeServiceInstance: PlaybackService? = null

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

        fun updateHandleAudioBecomingNoisy(enabled: Boolean) {
            activeServiceInstance?.player?.setHandleAudioBecomingNoisy(enabled)
        }

        fun updateAudioFocusDucking(enabled: Boolean) {
            val p = activeServiceInstance?.player ?: return
            p.setAudioAttributes(p.audioAttributes, enabled)
        }

        internal fun toggleActivePlayerPlayPause(): Boolean {
            val svc = activeServiceInstance ?: return false
            return PlaybackCoordinator.handleTogglePlay(svc.applicationContext, svc.player)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this

        val prefs = applicationContext.getSharedPreferences("concept_player_prefs", Context.MODE_PRIVATE)
        val pauseOnHeadsetDisconnect = prefs.getBoolean("pause_on_headset_disconnect", true)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val bufferSizeProvider = DefaultAudioTrackBufferSizeProvider.Builder()
            .setMinPcmBufferDurationUs(250_000)
            .setMaxPcmBufferDurationUs(2_500_000)
            .setPcmBufferMultiplicationFactor(8)
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
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(pauseOnHeadsetDisconnect)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
        player = exoPlayer

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

        val sessionCallback = object : MediaSession.Callback {
            @OptIn(UnstableApi::class)
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val isSelfOrSystem = controller.packageName == packageName ||
                    controller.packageName == "android" ||
                    controller.packageName.startsWith("com.android.") ||
                    controller.isTrusted
                return if (isSelfOrSystem) {
                    super.onConnect(session, controller)
                } else {
                    val restrictedPlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                        .buildUpon()
                        .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                        .remove(Player.COMMAND_SET_MEDIA_ITEM)
                        .build()
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(restrictedPlayerCommands)
                        .build()
                }
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(sessionCallback)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        }
        return res
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
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
