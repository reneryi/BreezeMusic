package com.concept.music.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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

/**
 * AndroidX Media3 官方后台播放服务
 * 提供锁屏控制、通知栏控制面板、蓝牙耳机按键拦截与 AudioFocus 音频焦点管理
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. 配置音频属性
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

        // 3. 自定义 RenderersFactory，输出 16-bit PCM 并固定软件倍速处理器，杜绝重采样/硬件倍速引发的哒哒爆音
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParameters: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioTrackBufferSizeProvider(bufferSizeProvider)
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
                /* bufferForPlaybackMs = */ 3_500,
                /* bufferForPlaybackAfterRebufferMs = */ 6_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 5. 初始化 ExoPlayer（挂载边听边存 CacheDataSource 离线缓存引擎）
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(AudioCacheManager.getDataSourceFactory(this))

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
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

        // 6. 配置点击通知栏回跳 Activity 的 PendingIntent
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // 7. 构建 MediaSession
        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
