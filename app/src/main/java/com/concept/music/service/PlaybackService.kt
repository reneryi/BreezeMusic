package com.concept.music.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
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
import com.concept.music.data.model.Song
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
        tenBands: IntArray,
        bassStrength: Int,
        bassCutoffHz: Int,
        vocalClarity: Int,
        analogWarmth: Int,
        loudnessComp: Boolean
    ) {
        val bandsHash = tenBands.contentHashCode()
        if (sampleRate == cachedSampleRate &&
            dspActive == cachedDspActive &&
            bandsHash == cachedTenBandsHash &&
            bassStrength == cachedBassStrength &&
            bassCutoffHz == cachedBassCutoff &&
            vocalClarity == cachedVocalClarity &&
            analogWarmth == cachedAnalogWarmth &&
            loudnessComp == cachedEqLoudness
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
        cachedEqLoudness = loudnessComp

        if (!dspActive || sampleRate <= 0) {
            tenBandFilters.forEach { it.setIdentity() }
            bassSubFilter.setIdentity()
            bassPunchFilter.setIdentity()
            vocalPresenceFilter.setIdentity()
            vocalAirFilter.setIdentity()
            warmthBodyFilter.setIdentity()
            warmthRollOffFilter.setIdentity()
            return
        }

        for (i in 0 until 10) {
            val rawLevel = tenBands.getOrElse(i) { 0 }.toDouble()
            val loudnessAdd = if (loudnessComp) {
                when (i) {
                    0 -> 2.4
                    1 -> 1.6
                    8 -> 1.2
                    9 -> 2.0
                    else -> 0.0
                }
            } else 0.0
            val gainDb = (rawLevel * 0.65 + loudnessAdd).coerceIn(-12.0, 12.0)
            when (i) {
                0 -> tenBandFilters[i].configureLowShelf(TEN_BAND_CENTERS_HZ[i], sampleRate, 0.75, gainDb)
                9 -> tenBandFilters[i].configureHighShelf(TEN_BAND_CENTERS_HZ[i], sampleRate, 0.75, gainDb)
                else -> tenBandFilters[i].configurePeakingEq(TEN_BAND_CENTERS_HZ[i], sampleRate, 1.41, gainDb)
            }
        }

        val bassFrac = (bassStrength / 1000.0).coerceIn(0.0, 1.0)
        val cutoff = bassCutoffHz.coerceIn(35, 180).toDouble()
        if (bassFrac > 0.01) {
            bassSubFilter.configureLowShelf(cutoff, sampleRate, 0.72, bassFrac * 6.8)
            bassPunchFilter.configurePeakingEq(cutoff * 1.35, sampleRate, 1.25, bassFrac * 3.6)
        } else {
            bassSubFilter.setIdentity()
            bassPunchFilter.setIdentity()
        }

        val clarityFrac = (vocalClarity / 100.0).coerceIn(0.0, 1.0)
        if (clarityFrac > 0.01) {
            vocalPresenceFilter.configurePeakingEq(2850.0, sampleRate, 1.15, clarityFrac * 4.5)
            vocalAirFilter.configureHighShelf(9500.0, sampleRate, 0.75, clarityFrac * 4.2)
        } else {
            vocalPresenceFilter.setIdentity()
            vocalAirFilter.setIdentity()
        }

        val warmthFrac = (analogWarmth / 100.0).coerceIn(0.0, 1.0)
        if (warmthFrac > 0.01) {
            warmthBodyFilter.configurePeakingEq(210.0, sampleRate, 0.90, warmthFrac * 3.8)
            warmthRollOffFilter.configureHighShelf(12500.0, sampleRate, 0.70, -warmthFrac * 2.2)
        } else {
            warmthBodyFilter.setIdentity()
            warmthRollOffFilter.setIdentity()
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val encoding = inputAudioFormat.encoding
        val channels = inputAudioFormat.channelCount
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)

        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        val frameSize = bytesPerSample * channels
        val frameCount = remaining / frameSize
        if (frameCount <= 0) {
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val dspActive = AudioEffectManager.dspMasterActive
        val balance = if (dspActive) AudioEffectManager.dspChannelBalance.coerceIn(-1f, 1f) else 0f
        val stereoWidth = if (dspActive) AudioEffectManager.dspStereoWidth.coerceIn(0.5f, 2.0f) else 1.0f
        val rotationSpeed = if (dspActive) AudioEffectManager.dspSurroundRotationSpeed.coerceIn(0f, 1f) else 0f
        val preAmpDb = if (dspActive) AudioEffectManager.dspPreAmpDb.coerceIn(-6f, 6f) else 0f
        val volNorm = AudioEffectManager.dspVolumeNormalization
        val antiClip = if (dspActive) AudioEffectManager.dspAntiClipping else true

        val tenBands = if (dspActive) AudioEffectManager.dspTenBandGains else IntArray(10)
        val bassStrength = if (dspActive) AudioEffectManager.dspBassStrength else 0
        val bassCutoff = if (dspActive) AudioEffectManager.dspBassCutoffHz else 65
        val vocalClarity = if (dspActive) AudioEffectManager.dspVocalClarity else 0
        val analogWarmth = if (dspActive) AudioEffectManager.dspAnalogWarmth else 0
        val reverbMix = if (dspActive) AudioEffectManager.dspReverbWetMix else 0f
        val reverbDecay = if (dspActive) AudioEffectManager.dspReverbDecay else 0f
        val loudnessComp = if (dspActive) AudioEffectManager.dspLoudnessCompensation else false

        updateFilterCoefficientsIfNeeded(
            sampleRate = sampleRate,
            dspActive = dspActive,
            tenBands = tenBands,
            bassStrength = bassStrength,
            bassCutoffHz = bassCutoff,
            vocalClarity = vocalClarity,
            analogWarmth = analogWarmth,
            loudnessComp = loudnessComp
        )

        val isBypass = !dspActive && !volNorm
        if (isBypass) {
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val out = replaceOutputBuffer(remaining)
        out.order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        // 先对当前 PCM 块做一次轻量峰值扫描，用于平滑跨曲目响度均衡
        if (volNorm) {
            val markPos = inputBuffer.position()
            var sumAbs = 0f
            val stepFrames = (frameCount / 64).coerceAtLeast(1)
            var sampled = 0
            var fIdx = 0
            while (fIdx < frameCount) {
                val bytePos = markPos + fIdx * frameSize
                if (bytePos + bytesPerSample <= inputBuffer.limit()) {
                    inputBuffer.position(bytePos)
                    val s = abs(readNormalizedSample(inputBuffer, encoding))
                    sumAbs += s
                    sampled++
                }
                fIdx += stepFrames
            }
            inputBuffer.position(markPos)
            if (sampled > 0) {
                val avgAbs = (sumAbs / sampled).coerceIn(0.01f, 0.95f)
                smoothedEnvelope = smoothedEnvelope * 0.94f + avgAbs * 0.06f
                val targetGain = (0.24f / smoothedEnvelope.coerceAtLeast(0.05f)).coerceIn(0.78f, 1.38f)
                currentNormGain = currentNormGain * 0.90f + targetGain * 0.10f
            }
        } else {
            currentNormGain = 1.0f
        }

        val preAmpLinear = if (abs(preAmpDb) > 0.05f) {
            10.0.pow(preAmpDb / 20.0).toFloat()
        } else 1.0f

        val leftPanGain = if (balance > 0f) (1f - balance).coerceIn(0f, 1f) else 1f
        val rightPanGain = if (balance < 0f) (1f + balance).coerceIn(0f, 1f) else 1f
        val targetLeftGain = preAmpLinear * currentNormGain * leftPanGain
        val targetRightGain = preAmpLinear * currentNormGain * rightPanGain

        val rampFrames = frameCount.coerceAtMost(64).coerceAtLeast(1)
        val leftGainStep = (targetLeftGain - smoothLeftGain) / rampFrames
        val rightGainStep = (targetRightGain - smoothRightGain) / rampFrames

        val phaseStep = if (rotationSpeed > 0.01f) {
            (2.0 * PI * (0.12 + rotationSpeed * 0.55)) / sampleRate.toDouble()
        } else 0.0

        val tubeDrive = (analogWarmth / 100f).coerceIn(0f, 1f) * 0.55f
        val revWet = reverbMix.coerceIn(0f, 0.45f)
        val revFeedback = reverbDecay.coerceIn(0f, 0.78f)
        val delaySamples1 = ((sampleRate * 0.0297).toInt()).coerceIn(256, reverbMaxSamples - 1)
        val delaySamples2 = ((sampleRate * 0.0371).toInt()).coerceIn(256, reverbMaxSamples - 1)
        val allpassSamples = ((sampleRate * 0.0083).toInt()).coerceIn(64, allpassBufL.size - 1)

        for (i in 0 until frameCount) {
            if (i < rampFrames) {
                smoothLeftGain += leftGainStep
                smoothRightGain += rightGainStep
            } else {
                smoothLeftGain = targetLeftGain
                smoothRightGain = targetRightGain
            }

            var inL = readNormalizedSample(inputBuffer, encoding)
            var inR = if (channels == 2) readNormalizedSample(inputBuffer, encoding) else inL

            // 1. 级联 Biquad IIR 滤波器网络（10 段 EQ + 动态超重低音 + 人声清澈度 + 模拟温暖度）
            if (dspActive) {
                for (band in tenBandFilters) {
                    inL = band.processLeft(inL)
                    inR = band.processRight(inR)
                }
                inL = bassPunchFilter.processLeft(bassSubFilter.processLeft(inL))
                inR = bassPunchFilter.processRight(bassSubFilter.processRight(inR))
                inL = vocalAirFilter.processLeft(vocalPresenceFilter.processLeft(inL))
                inR = vocalAirFilter.processRight(vocalPresenceFilter.processRight(inR))
                inL = warmthRollOffFilter.processLeft(warmthBodyFilter.processLeft(inL))
                inR = warmthRollOffFilter.processRight(warmthBodyFilter.processRight(inR))

                // 2. 电子管偶次谐波柔化激励 (Analog Tube Saturation)
                if (tubeDrive > 0.01f) {
                    inL += tubeDrive * 0.18f * (inL * inL * (if (inL >= 0f) 1f else -0.6f))
                    inR += tubeDrive * 0.18f * (inR * inR * (if (inR >= 0f) 1f else -0.6f))
                }
            }

            var outL = inL
            var outR = inR

            // 3. M/S 立体声场宽度扩展 (Mid/Side Matrix)
            if (channels == 2 && abs(stereoWidth - 1.0f) > 0.02f) {
                val mid = (outL + outR) * 0.5f
                val side = (outL - outR) * 0.5f * stereoWidth
                outL = mid + side
                outR = mid - side
            }

            // 4. 立体声空间混响网络 (Stereo Comb + Allpass Reverb)
            if (dspActive && revWet > 0.005f) {
                val c1L = combBufL1[combIdx1]
                val c1R = combBufR1[combIdx1]
                val c2L = combBufL2[combIdx2]
                val c2R = combBufR2[combIdx2]

                reverbLpL = reverbLpL * 0.42f + (c1L + c2L) * 0.29f
                reverbLpR = reverbLpR * 0.42f + (c1R + c2R) * 0.29f

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
                outL = outL * (1f - revWet * 0.35f) + wetL * revWet
                outR = outR * (1f - revWet * 0.35f) + wetR * revWet
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
 * 提供锁屏控制、通知栏控制面板、蓝牙耳机按键拦截、AudioFocus 音频焦点管理及 MediaSession 实时歌词元数据同步
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())

        @Volatile
        private var activeServiceInstance: PlaybackService? = null

        @Volatile
        internal var liveLyricOverlaySongId: String = ""

        @Volatile
        internal var liveLyricLine: String = ""

        @Volatile
        internal var liveLyricTranslation: String = ""

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

        /**
         * 将实时歌词同步至 MediaSession 的 SessionExtras 与动态元数据副标题，
         * 使车载蓝牙屏幕、系统锁屏、OriginOS 原子岛与 ColorOS 流体云能够直接读取实时歌词。
         */
        fun syncLiveLyricToMediaSession(
            song: Song,
            lyricLine: String,
            translationLine: String = ""
        ) {
            liveLyricOverlaySongId = song.id
            liveLyricLine = lyricLine.trim()
            liveLyricTranslation = translationLine.trim()
            val svc = activeServiceInstance ?: return
            mainHandler.post {
                runCatching {
                    val extras = Bundle().apply {
                        putString("android.media.metadata.LYRIC", liveLyricLine)
                        putString("lyric", liveLyricLine)
                        putString("translation", liveLyricTranslation)
                        putString("song_title", song.title)
                        putString("artist", song.artist)
                    }
                    svc.mediaSession?.setSessionExtras(extras)
                }
            }
        }

        fun restoreMediaMetadataTitleIfNeeded() {
            liveLyricOverlaySongId = ""
            liveLyricLine = ""
            liveLyricTranslation = ""
            val svc = activeServiceInstance ?: return
            mainHandler.post {
                runCatching {
                    svc.mediaSession?.setSessionExtras(Bundle.EMPTY)
                }
            }
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
            override fun getMediaMetadata(): MediaMetadata {
                val base = super.getMediaMetadata()
                val curItem = currentMediaItem
                val activeLyric = liveLyricLine
                if (activeLyric.isNotBlank() &&
                    SystemLyricBridge.state.value.statusBarBroadcastEnabled &&
                    SystemLyricBridge.state.value.mediaSessionLyricSyncEnabled &&
                    (curItem == null || curItem.mediaId == liveLyricOverlaySongId)
                ) {
                    val extras = Bundle(base.extras ?: Bundle.EMPTY).apply {
                        putString("android.media.metadata.LYRIC", activeLyric)
                        putString("lyric", activeLyric)
                    }
                    return base.buildUpon()
                        .setSubtitle(activeLyric)
                        .setDescription(liveLyricTranslation.ifBlank { base.artist })
                        .setExtras(extras)
                        .build()
                }
                return base
            }

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
