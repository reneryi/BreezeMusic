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

// 64-bit 纯线性双二阶母带级音频处理器已独立拆分至 ConceptMasteringAudioProcessor.kt (支持 10段 PEQ 与 hasActiveEqBands 零开销短路)

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
