package com.concept.music.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
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
import kotlin.math.sin

/**
 * 概念版发烧级实时 PCM 母带音频处理器
 * 在 Media3 解码管线中直接对 16-bit 立体声 PCM 采样帧执行：
 * 1. 左右声道声像平衡 (Stereo Channel Balance)
 * 2. M/S 立体声场宽度扩展与 3D 环绕声像微旋补偿
 * 3. 智能跨曲目响度均衡 (Volume Normalization) 与硬件级软削波限幅保护 (Soft-Knee Anti-Clipping Limiter)
 */
@OptIn(UnstableApi::class)
class ConceptMasteringAudioProcessor : BaseAudioProcessor() {
    private var rotationPhase = 0.0
    private var smoothedEnvelope = 0.22f
    private var currentNormGain = 1.0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount == 2) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun onFlush() {
        smoothedEnvelope = 0.22f
        currentNormGain = 1.0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val state = AudioEffectManager.state.value
        val balance = state.channelBalance.coerceIn(-1f, 1f)
        val dspActive = state.isEnabled && state.currentPreset != EffectPreset.OFF
        val widthFactor = if (dspActive) (state.virtualizerStrength / 1000f) * 0.32f else 0f
        val rotSpeed = if (dspActive) state.surroundRotationSpeed else 0
        val warmthFactor = if (dspActive) (state.analogWarmthStrength / 1000f) * 0.14f else 0f
        val eqLoudnessComp = state.isEnabled && state.equalLoudnessCompensation
        val volNorm = state.isEnabled && state.volumeNormalizationEnabled
        val antiClip = state.isEnabled && state.antiClippingEnabled && (dspActive || eqLoudnessComp || volNorm)

        // 若处于 0音染 / Bit-Perfect 原生直通态且声道平衡居中，直接零拷贝透传，不做软削波压缩
        if (abs(balance) < 0.01f && !dspActive && !eqLoudnessComp && !volNorm) {
            val out = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val out = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
        val leftBalGain = if (balance > 0f) (1f - balance).coerceIn(0f, 1f) else 1f
        val rightBalGain = if (balance < 0f) (1f + balance).coerceIn(0f, 1f) else 1f
        val loudnessCompGain = if (eqLoudnessComp) 1.06f else 1.0f
        val phaseStep = if (rotSpeed > 0) (2.0 * PI * (0.15 + rotSpeed * 0.012)) / inputAudioFormat.sampleRate.coerceAtLeast(44100) else 0.0

        while (inputBuffer.remaining() >= 4) {
            val rawL = inputBuffer.short.toFloat() / 32768f
            val rawR = inputBuffer.short.toFloat() / 32768f

            // 0. 跨曲目智能响度均衡包络跟踪（慢速 AGC，仅在有效信号区间平滑补偿响度差异）
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

            // 1. Mid-Side 立体声宽度扩展
            val mid = (rawL + rawR) * 0.5f * normGain
            val side = (rawL - rawR) * 0.5f * (1f + widthFactor) * normGain
            var outL = mid + side
            var outR = mid - side

            // 2. 模拟电子管/黑胶偶次谐波微饱和润色
            if (warmthFactor > 0.01f) {
                outL += warmthFactor * (outL * outL * if (outL >= 0f) 0.35f else -0.22f)
                outR += warmthFactor * (outR * outR * if (outR >= 0f) 0.35f else -0.22f)
            }

            // 3. 3D 环绕声场慢速轨迹微旋
            if (phaseStep > 0.0) {
                rotationPhase += phaseStep
                if (rotationPhase > 2.0 * PI) rotationPhase -= 2.0 * PI
                val pan = sin(rotationPhase).toFloat() * 0.16f
                outL *= (1f - pan.coerceAtLeast(0f))
                outR *= (1f + pan.coerceAtMost(0f))
            }

            // 4. 左右声道平衡与等响度增益
            outL *= leftBalGain * loudnessCompGain
            outR *= rightBalGain * loudnessCompGain

            // 5. 软膝防破音数字限幅器 (Soft-Knee Limiter)
            if (antiClip || volNorm) {
                outL = softLimit(outL)
                outR = softLimit(outR)
            } else {
                outL = outL.coerceIn(-1f, 1f)
                outR = outR.coerceIn(-1f, 1f)
            }

            out.putShort((outL * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            out.putShort((outR * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        while (inputBuffer.hasRemaining()) {
            out.put(inputBuffer.get())
        }
        out.flip()
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

        @Volatile
        var onSkipToNextIntercepted: (() -> Boolean)? = null

        fun updateHandleAudioBecomingNoisy(enabled: Boolean) {
            activeServiceInstance?.player?.setHandleAudioBecomingNoisy(enabled)
        }

        fun updateAudioFocusDucking(pauseOnDisconnect: Boolean) {
            activeServiceInstance?.player?.setHandleAudioBecomingNoisy(pauseOnDisconnect)
        }

        internal fun toggleActivePlayerPlayPause(): Boolean {
            val p = activeServiceInstance?.player ?: return false
            if (p.isPlaying) p.pause() else p.play()
            return true
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this

        val prefs = applicationContext.getSharedPreferences("concept_player_prefs", Context.MODE_PRIVATE)
        val pauseOnHeadsetDisconnect = prefs.getBoolean("pause_on_headset_disconnect", true)

        // 1. 配置音频属性（支持系统级 AudioFocus 自动管理与通知音闪避 Ducking）
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 2. 自定义 AudioTrack 环形缓冲区（扩容至 1.5s ~ 4.0s），防止高码率 FLAC 或模拟器 VirtIO 音频驱动欠载造成的哒哒声与电流声
        val bufferSizeProvider = DefaultAudioTrackBufferSizeProvider.Builder()
            .setMinPcmBufferDurationUs(1_500_000)
            .setMaxPcmBufferDurationUs(4_000_000)
            .setPcmBufferMultiplicationFactor(12)
            .build()

        val masteringProcessor = ConceptMasteringAudioProcessor()

        // 3. 自定义 RenderersFactory，输出 16-bit PCM 并挂载声道平衡/母带限幅处理器与软件倍速处理器
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParameters: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioTrackBufferSizeProvider(bufferSizeProvider)
                    .setAudioProcessors(arrayOf(masteringProcessor))
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(false)
                    .build()
            }
        }.setEnableDecoderFallback(true)

        // 4. 配置平滑网络流的 LoadControl（充足预缓冲再起播，避免边下边播初期欠载）
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 35_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 2_800,
                /* bufferForPlaybackAfterRebufferMs = */ 5_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 5. 初始化 ExoPlayer（挂载边听边存 CacheDataSource 流媒体缓存引擎）
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(AudioCacheManager.getDataSourceFactory(this))

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(pauseOnHeadsetDisconnect)
            .setLoadControl(loadControl)
            .build()

        // 6. 初始化并绑定音效管理器
        AudioEffectManager.init(applicationContext)
        AudioEffectManager.attachSession(player!!.audioSessionId)
        player!!.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                AudioEffectManager.attachSession(audioSessionId)
            }
        })

        // 7. 配置点击通知栏回跳 Activity 的 PendingIntent
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // 8. 使用 ForwardingPlayer 包装 ExoPlayer，确保系统锁屏、通知栏与蓝牙耳机的上一曲/下一曲始终可用并精准转发至队列控制器
        val forwardingPlayer = object : ForwardingPlayer(player!!) {
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
                if (onSkipToPreviousIntercepted?.invoke() != true) {
                    super.seekToPrevious()
                }
            }

            override fun seekToPreviousMediaItem() {
                if (onSkipToPreviousIntercepted?.invoke() != true) {
                    super.seekToPreviousMediaItem()
                }
            }

            override fun seekToNext() {
                if (onSkipToNextIntercepted?.invoke() != true) {
                    super.seekToNext()
                }
            }

            override fun seekToNextMediaItem() {
                if (onSkipToNextIntercepted?.invoke() != true) {
                    super.seekToNextMediaItem()
                }
            }
        }

        // 9. 构建 MediaSession
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        if (activeServiceInstance === this) {
            activeServiceInstance = null
        }
        AudioEffectManager.releaseEffects()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
