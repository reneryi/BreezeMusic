package com.concept.music.ui.components

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.OrientationEventListener
import android.graphics.drawable.BitmapDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.Song
import com.concept.music.data.model.SongDetailInfo
import com.concept.music.service.AudioEffectManager
import com.concept.music.service.SystemLyricBridge
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptVioletAccent
import com.concept.music.ui.theme.ConceptMotion
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.SystemBarAppearanceEffect
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.conceptSheetSpringEntrance
import com.concept.music.ui.theme.isConceptDarkTheme
import com.concept.music.ui.theme.rememberIsLifecycleStarted
import com.concept.music.ui.theme.readableOnDarkSurface
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.LyricHighlightTheme
import com.concept.music.ui.viewmodel.PlayMode
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import com.concept.music.ui.viewmodel.SavedPlayQueue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.lerp
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlin.math.min
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.pow

/**
 * 智能识别当前播放歌曲是否为纯中文歌曲（或无需展示翻译按钮）
 * 规则：
 * 1. 若歌词本身没有任何翻译内容 (!lyricData.hasTranslation 且所有行 translation 为空)，则不显示翻译按钮；
 * 2. 若歌曲主歌词不包含日文假名、韩文谚文，且拉丁字母占比极低（主要为中文汉字），判定为中文歌，自动隐藏翻译按钮。
 */
/**
 * 格式化播放页进度条上方评论计数：
 * - 小于 1000 显示准确数值（如 856）
 * - 大于等于 1000 用 k 为单位保留一位小数（如 1.1k）
 * - 大于等于 10000 用 w 为单位保留一位小数（如 1.2w）
 */
internal fun formatPlayerCommentCount(count: Int): String {
    return when {
        count <= 0 -> ""
        count < 1_000 -> count.toString()
        count < 10_000 -> String.format(Locale.US, "%.1fk", count / 1000.0)
        else -> String.format(Locale.US, "%.1fw", count / 10000.0)
    }
}

/**
 * 智能识别当前播放歌曲是否应展示「译」（外语翻译）按钮：
 * 1. 歌词本身必须包含有效翻译内容；
 * 2. 仅对中文歌以外的外语歌曲（日、韩、英、俄、泰、法等）显示，纯中文歌与粤语歌均不显示翻译按钮。
 */
internal fun shouldShowTranslationButton(song: Song, lyricData: LyricData): Boolean {
    val lines = lyricData.lines
    if (lines.isEmpty() || (song.id.isEmpty() && song.title.isEmpty())) return false
    val hasAnyTranslation = lyricData.hasTranslation || lines.any { !it.translation.isNullOrBlank() }
    if (!hasAnyTranslation) return false

    var cjkCount = 0
    var kanaHangulCount = 0
    var latinCount = 0
    var otherForeignCount = 0
    lines.take(35).forEach { line ->
        val txt = line.text
        for (ch in txt) {
            when (ch.code) {
                in 0x3040..0x30FF, in 0x31F0..0x31FF, in 0xAC00..0xD7AF, in 0x1100..0x11FF -> kanaHangulCount++
                in 0x4E00..0x9FFF -> cjkCount++
                in 'a'.code..'z'.code, in 'A'.code..'Z'.code -> latinCount++
                in 0x0400..0x04FF, in 0x0E00..0x0E7F -> otherForeignCount++
            }
        }
    }
    if (kanaHangulCount > 2 || otherForeignCount > 2) return true
    if (latinCount > cjkCount * 2 && latinCount > 12) return true
    return false
}

/**
 * 智能识别当前播放歌曲是否应展示「音」（音译注音）按钮：
 * 只要歌词中包含非空的音译注音层（如日语罗马音、韩语音译、粤语粤拼等），即在歌词页显示「音」按钮。
 */
internal fun shouldShowPhoneticButton(song: Song, lyricData: LyricData): Boolean {
    val lines = lyricData.lines
    if (lines.isEmpty() || (song.id.isEmpty() && song.title.isEmpty())) return false
    return lyricData.hasPhonetic || lines.any { !it.phonetic.isNullOrBlank() }
}

/**
 * 检查手机系统「自动旋转 (Auto-Rotate)」开关是否开启
 */
internal fun isSystemAutoRotateEnabled(context: Context): Boolean {
    return runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    }.getOrDefault(false)
}

/**
 * 检查用户是否开启封面播放页横放自动进入横屏播放开关（OrientationEventListener 无需 ACTIVITY_RECOGNITION 权限）
 */
@Suppress("UNUSED_PARAMETER")
internal fun hasDeviceMotionOrientationPermission(context: Context, appToggleEnabled: Boolean): Boolean {
    return appToggleEnabled &&
        com.concept.music.service.AppPermissionManager.isPermissionEnabled(
            com.concept.music.service.AppPermissionType.MOTION_ORIENTATION
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    song: Song?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playMode: PlayMode,
    currentQualityLabel: String,
    lyricData: LyricData,
    currentLyricIndex: Int,
    queue: List<Song>,
    currentIndex: Int,
    recentQueues: List<SavedPlayQueue> = emptyList(),
    activeQueueId: String = "",
    isFavorite: Boolean = false,
    coverStyle: CoverStyle = CoverStyle.VINYL,
    playerBackgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.COVER_BLUR,
    dynamicCoverAccentEnabled: Boolean = true,
    showTranslation: Boolean = false,
    showPhonetic: Boolean = false,
    lyricFontSize: Float = 24f,
    lyricTranslationScale: Float = 0.84f,
    motionOrientationPermissionEnabled: Boolean = true,
    lyricAlignment: LyricAlignment = LyricAlignment.LEFT,
    lyricWordByWord: Boolean = true,
    lyricBlurEnabled: Boolean = true,
    lyricBlurIntensity: Float = 1.0f,
    lyricLineSpacing: Float = 14f,
    lyricOffsetMs: Long = 0L,
    lyricHighlightTheme: LyricHighlightTheme = LyricHighlightTheme.ICE_BLUE,
    lyricBackgroundDim: Float = 0.72f,
    lyricKeepScreenOn: Boolean = true,
    commentCount: Int = 0,
    songDetail: SongDetailInfo? = null,
    playbackSpeed: Float = 1.0f,
    playbackPitch: Float = 1.0f,
    linkPitchToSpeed: Boolean = false,
    sleepTimerRemainingSec: Long = 0L,
    sleepStopAfterCurrentSong: Boolean = false,
    sleepPendingStopAtTrackEnd: Boolean = false,
    climaxModeEnabled: Boolean = false,
    climaxUnlockedForCurrentSong: Boolean = false,
    accompanyModeEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekToLyricLine: (Int) -> Unit,
    onCyclePlayMode: () -> Unit,
    onSelectQuality: (AudioQuality) -> Unit,
    onToggleFavorite: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onSelectCoverStyle: (CoverStyle) -> Unit = {},
    onSelectPlayerBackgroundStyle: (PlayerBackgroundStyle) -> Unit = {},
    onSetDynamicCoverAccentEnabled: (Boolean) -> Unit = {},
    onToggleTranslation: () -> Unit = {},
    onTogglePhonetic: () -> Unit = {},
    onSetLyricFontSize: (Float) -> Unit = {},
    onSetLyricTranslationScale: (Float) -> Unit = {},
    onSetLyricAlignment: (LyricAlignment) -> Unit = {},
    onSetLyricWordByWord: (Boolean) -> Unit = {},
    onSetLyricBlurEnabled: (Boolean) -> Unit = {},
    onSetLyricBlurIntensity: (Float) -> Unit = {},
    onSetLyricLineSpacing: (Float) -> Unit = {},
    onSetLyricOffsetMs: (Long) -> Unit = {},
    onSetLyricHighlightTheme: (LyricHighlightTheme) -> Unit = {},
    onSetLyricBackgroundDim: (Float) -> Unit = {},
    onSetLyricKeepScreenOn: (Boolean) -> Unit = {},
    onOpenComments: () -> Unit = {},
    onOpenViperEffectPage: (() -> Unit)? = null,
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onSetPlaybackPitch: (Float) -> Unit = {},
    onSetLinkPitchToSpeed: (Boolean) -> Unit = {},
    onResetSpeedAndPitch: () -> Unit = {},
    onSetSleepTimer: (Int, Boolean) -> Unit = { _, _ -> },
    onSetSleepStopAfterCurrentSongPref: (Boolean) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onToggleClimaxMode: () -> Unit = {},
    onUnlockFullTrack: () -> Unit = {},
    onToggleAccompanyMode: () -> Unit = {},
    onReorderQueueByMood: (String) -> String = { "" },
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onSwitchRecentQueue: (String) -> Unit = {},
    onDeleteRecentQueue: (String) -> Unit = {},
    onOpenArtist: (Song) -> Unit = {},
    onOpenAlbum: (Song) -> Unit = {},
    onPlayQueueSong: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    onClearQueue: () -> Unit,
    positionProvider: () -> Long = { currentPositionMs }
) {
    if (song == null) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) {
        var ctx: Context? = context
        var found: Activity? = null
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) {
                found = ctx
                break
            }
            ctx = ctx.baseContext
        }
        found
    }
    val view = LocalView.current
    var showLyricView by remember { mutableStateOf(false) }
    var isLandscapeMode by remember { mutableStateOf(false) }
    var enteredLandscapeViaSensor by remember { mutableStateOf(false) }
    var autoRotateSensorEnabled by remember { mutableStateOf(true) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioEffectScreen by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showLyricSettingsDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedPitchDialog by remember { mutableStateOf(false) }
    var showLyricPosterDialog by remember { mutableStateOf(false) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    var showMoreControlSheet by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showCoverPreviewDialog by remember { mutableStateOf(false) }
    var showArtistPickerDialog by remember { mutableStateOf(false) }
    var tabletForcedRotationDeg by remember { mutableFloatStateOf(90f) }
    val adaptiveInfo = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current
    val isLargeScreen = adaptiveInfo.isLargeScreenDevice
    val latestIsLargeScreen by rememberUpdatedState(isLargeScreen)

    val audioEffectState by AudioEffectManager.state.collectAsState()
    val systemLyricState by SystemLyricBridge.state.collectAsState()

    val enterLandscape: (Boolean) -> Unit = { fromSensor ->
        if (!showLyricView) {
            isLandscapeMode = true
            enteredLandscapeViaSensor = fromSensor
            if (!isLargeScreen) {
                // 手机端：请求传感器横屏方向
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                // 平板端：Android 16 (API 36, sw>=600dp) 对 requestedOrientation=LANDSCAPE 会触发上下黑边兼容小窗 (Letterboxing)，
                // 因此平板端保持 UNSPECIFIED 满屏窗口，当处于竖屏窗口时直接将全屏播放页画布旋转 90° 横向铺满全屏（图四状态），让用户像手机一样将平板横过来使用！
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    val exitLandscape: () -> Unit = {
        if (isLargeScreen && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !isLandscapeMode) {
            // 平板本身处于横屏状态时，点击左上角返回直接收起播放页回到上一界面
            onDismiss()
        } else {
            isLandscapeMode = false
            enteredLandscapeViaSensor = false
            if (isLargeScreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    // 严格限制横屏自动触发范围：
    // 1. 仅在封面播放页 (!showLyricView) 且未打开其它弹窗时才允许通过横放手机进入横屏；
    // 2. 必须同时满足：已开启「设备动作与方向」权限 + 手机系统「自动旋转」开关处于打开状态。
    val isLifecycleStarted = rememberIsLifecycleStarted()
    val canSensorTriggerLandscape = isLifecycleStarted &&
        !showLyricView &&
        !showQueueSheet &&
        !showMoreControlSheet &&
        !showLyricSettingsDialog &&
        !showLyricPosterDialog &&
        autoRotateSensorEnabled &&
        motionOrientationPermissionEnabled
    val shouldListenOrientationSensor = isLifecycleStarted &&
        (canSensorTriggerLandscape || (isLandscapeMode && enteredLandscapeViaSensor))

    DisposableEffect(context, shouldListenOrientationSensor, canSensorTriggerLandscape, isLandscapeMode, enteredLandscapeViaSensor) {
        if (!shouldListenOrientationSensor) {
            return@DisposableEffect onDispose {}
        }
        var consecutiveHorizontalSamples = 0
        var consecutiveVerticalSamples = 0
        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val isHorizontalHold = orientation in 70..110 || orientation in 250..290
                val isVerticalHold = orientation in 0..20 || orientation in 340..360

                if (isHorizontalHold) {
                    consecutiveHorizontalSamples++
                    consecutiveVerticalSamples = 0
                    if (consecutiveHorizontalSamples >= 2 && !isLandscapeMode && canSensorTriggerLandscape) {
                        if (isSystemAutoRotateEnabled(context) &&
                            hasDeviceMotionOrientationPermission(context, motionOrientationPermissionEnabled)
                        ) {
                            enterLandscape(true)
                        }
                    }
                } else if (isVerticalHold) {
                    consecutiveVerticalSamples++
                    consecutiveHorizontalSamples = 0
                    if (consecutiveVerticalSamples >= 2 && isLandscapeMode && enteredLandscapeViaSensor) {
                        exitLandscape()
                    }
                } else {
                    consecutiveHorizontalSamples = 0
                    consecutiveVerticalSamples = 0
                }
            }
        }
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
        onDispose {
            orientationListener.disable()
        }
    }

    // 退出播放页时恢复屏幕方向：注意仅以 activity 为 Key，绝不把随旋转变化的布尔值作为 Key，
    // 彻底避免手机旋转进入横屏瞬间触发旧闭包 onDispose 把屏幕强行掰回竖屏！
    DisposableEffect(activity) {
        onDispose {
            if (!latestIsLargeScreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            activity?.window?.let { win ->
                WindowCompat.getInsetsController(win, view).show(
                    WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
                )
            }
        }
    }

    val isWindowActuallyLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenWidthDp > configuration.screenHeightDp
    // 平板竖屏点击「横屏播放」按钮时：将全屏画布旋转 90° 横向铺满整个屏幕（完全复刻图四状态，无任何上下黑边），把平板横过来即可沉浸使用
    val isForcedRotatedTabletLandscape = isLargeScreen && !showLyricView && isLandscapeMode && !isWindowActuallyLandscape
    val effectiveLandscape = if (adaptiveInfo.isTabletLandscapePlayer) {
        true
    } else if (isLargeScreen) {
        !showLyricView && (isWindowActuallyLandscape || isLandscapeMode)
    } else {
        !showLyricView && isWindowActuallyLandscape && isLandscapeMode
    }

    // 平板强制横屏旋转状态下：跟随重力感应自动适配正反横向握持（90° / -90°）
    DisposableEffect(context, isForcedRotatedTabletLandscape) {
        if (!isForcedRotatedTabletLandscape) {
            return@DisposableEffect onDispose {}
        }
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                when (orientation) {
                    in 65..115 -> tabletForcedRotationDeg = -90f
                    in 245..295 -> tabletForcedRotationDeg = 90f
                }
            }
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
        }
        onDispose {
            listener.disable()
        }
    }

    // 横屏模式下隐藏手机顶部状态栏与底部导航栏，实现纯粹全屏幕沉浸显示
    DisposableEffect(effectiveLandscape, activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (effectiveLandscape) {
                controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }
        onDispose {}
    }

    // 歌词页与横屏页屏幕常亮控制
    DisposableEffect(showLyricView, effectiveLandscape, lyricKeepScreenOn, isPlaying) {
        val keepOn = (showLyricView || effectiveLandscape) && lyricKeepScreenOn && isPlaying
        view.keepScreenOn = keepOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    // 系统返回键：优先关闭已打开的子面板（队列/歌词设置），若正处于横屏播放页则退回竖屏，若处于歌词页则退回封面，否则收起播放页
    BackHandler {
        when {
            showArtistPickerDialog -> showArtistPickerDialog = false
            showCoverPreviewDialog -> showCoverPreviewDialog = false
            showQueueSheet -> showQueueSheet = false
            showLyricSettingsDialog -> showLyricSettingsDialog = false
            effectiveLandscape -> exitLandscape()
            showLyricView -> showLyricView = false
            else -> onDismiss()
        }
    }

    SystemBarAppearanceEffect(forceDarkBars = true)

    val sheetDragScope = rememberCoroutineScope()
    val verticalDragAnim = remember { Animatable(0f) }

    // 仅在黑胶封面模式且封面页处于前台可见时驱动连续旋转相位；切至全屏歌词页或退后台时自动挂起 120Hz 旋转循环
    val rotationAnim = remember(song.id) { Animatable(0f) }
    var wasVinylPlaying by remember(song.id) { mutableStateOf(isPlaying) }
    val shouldSpinVinyl = isPlaying && isLifecycleStarted && !showLyricView && coverStyle == CoverStyle.VINYL
    LaunchedEffect(shouldSpinVinyl, isPlaying, song.id, coverStyle) {
        if (coverStyle == CoverStyle.VINYL) {
            if (shouldSpinVinyl) {
                wasVinylPlaying = true
                while (isActive) {
                    val target = rotationAnim.value + 360f
                    rotationAnim.animateTo(
                        targetValue = target,
                        animationSpec = tween(durationMillis = 20000, easing = LinearEasing)
                    )
                }
            } else if (wasVinylPlaying && !isPlaying && isLifecycleStarted && !showLyricView) {
                wasVinylPlaying = false
                // 物理摩擦惯性减速停转：
                // 播放态恒定角速度 ω0 = 360° / 20000ms = 18°/s；
                // 使用起始斜率 dy/dx = 0.66 / 0.33 = 2.0、末端斜率为 0 的阻尼曲线，
                // 搭配 Δθ = 13.5° 与 T = 1480ms，使暂停瞬间初始角速度严格等于 18°/s（零速度跳变），随后平顺滑行停稳
                rotationAnim.animateTo(
                    targetValue = rotationAnim.value + 13.5f,
                    animationSpec = tween(
                        durationMillis = 1480,
                        easing = CubicBezierEasing(0.33f, 0.66f, 0.45f, 1.0f)
                    )
                )
            }
        }
    }

    // 顶层驻留圆角封面色散相位、呼吸脉冲与缩放弹簧状态：
    // 置于 AnimatedContent(showLyricView) 之上，确保进出歌词页或切换横竖屏时相位与光晕状态永不销毁重置，
    // 同时仅在封面页可见且处于圆角流光模式时才运行 120Hz 相位积分
    val sharedRoundedCoverMotionState = rememberRoundedCoverMotionState(
        isPlaying = isPlaying,
        songKey = song.coverUrl.ifBlank { song.id },
        activeAndVisible = isLifecycleStarted && !showLyricView && coverStyle == CoverStyle.SQUARE
    )

    // 封面智能动态流光取色（支持主色、副色、第三极光点缀色三色提取）
    var extractedPalette by remember {
        mutableStateOf(
            CoverExtractedPalette(
                meshPrimary = Color(0xFF0E1A2F),
                meshSecondary = Color(0xFF13233F),
                meshTertiary = Color(0xFF191B38),
                dominantAccent = ConceptPrimary,
                secondaryAccent = ConceptCyanAccent,
                tertiaryAccent = ConceptVioletAccent
            )
        )
    }
    LaunchedEffect(song.id, song.coverUrl) {
        if (song.coverUrl.isNotBlank()) {
            val palette = withContext(Dispatchers.IO) {
                extractCoverMeshColors(context, song.coverUrl)
            }
            if (palette != null) {
                extractedPalette = palette
            }
        } else {
            extractedPalette = CoverExtractedPalette(
                meshPrimary = Color(0xFF0E1A2F),
                meshSecondary = Color(0xFF13233F),
                meshTertiary = Color(0xFF191B38),
                dominantAccent = ConceptPrimary,
                secondaryAccent = ConceptCyanAccent,
                tertiaryAccent = ConceptVioletAccent
            )
        }
    }
    val meshColorPrimary by animateColorAsState(
        targetValue = extractedPalette.meshPrimary,
        animationSpec = tween(durationMillis = 850),
        label = "meshPrimary"
    )
    val meshColorSecondary by animateColorAsState(
        targetValue = extractedPalette.meshSecondary,
        animationSpec = tween(durationMillis = 850),
        label = "meshSecondary"
    )
    val meshColorTertiary by animateColorAsState(
        targetValue = extractedPalette.meshTertiary,
        animationSpec = tween(durationMillis = 850),
        label = "meshTertiary"
    )
    val activeAccentColor by animateColorAsState(
        targetValue = if (dynamicCoverAccentEnabled) extractedPalette.dominantAccent else ConceptPrimary,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "activeAccentColor"
    )
    val activeSecondaryAccent by animateColorAsState(
        targetValue = if (dynamicCoverAccentEnabled) extractedPalette.secondaryAccent else ConceptCyanAccent,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "activeSecondaryAccent"
    )
    val activeTertiaryAccent by animateColorAsState(
        targetValue = if (dynamicCoverAccentEnabled) extractedPalette.tertiaryAccent else ConceptVioletAccent,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "activeTertiaryAccent"
    )

    val coverDynamicPrimary by animateColorAsState(
        targetValue = extractedPalette.dominantAccent,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "coverDynamicPrimary"
    )
    val coverDynamicSecondary by animateColorAsState(
        targetValue = extractedPalette.secondaryAccent,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "coverDynamicSecondary"
    )

    // 将播放页当前实时计算的封面随动可读高亮色（与全屏歌词完全同源）同步至桌面歌词
    val syncedDesktopPrimary = remember(coverDynamicPrimary) {
        coverDynamicPrimary.readableOnDarkSurface(minLuminance = 0.46f)
    }
    val syncedDesktopSecondary = remember(coverDynamicSecondary) {
        coverDynamicSecondary.readableOnDarkSurface(minLuminance = 0.50f)
    }
    LaunchedEffect(syncedDesktopPrimary, syncedDesktopSecondary, song.coverUrl) {
        SystemLyricBridge.updateDynamicCoverColors(
            primaryHex = syncedDesktopPrimary.toArgb().toLong() and 0xFFFFFFFFL,
            secondaryHex = syncedDesktopSecondary.toArgb().toLong() and 0xFFFFFFFFL,
            coverUrl = song.coverUrl
        )
    }

    val effectiveLyricPositionMs = (currentPositionMs + lyricOffsetMs).coerceAtLeast(0L)
    val lyricPositionProvider: () -> Long = remember(positionProvider, lyricOffsetMs) {
        { (positionProvider() + lyricOffsetMs).coerceAtLeast(0L) }
    }
    val rawPositionProvider: () -> Long = positionProvider
    val showTranslationBtn = remember(song.id, lyricData) {
        shouldShowTranslationButton(song, lyricData)
    }
    val showPhoneticBtn = remember(song.id, lyricData) {
        shouldShowPhoneticButton(song, lyricData)
    }

    val openViperAction: () -> Unit = {
        if (onOpenViperEffectPage != null) {
            onOpenViperEffectPage()
        } else {
            showAudioEffectScreen = true
        }
    }

    val effectiveConfiguration = remember(configuration, isForcedRotatedTabletLandscape) {
        if (isForcedRotatedTabletLandscape) {
            Configuration(configuration).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
                screenHeightDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
            }
        } else {
            configuration
        }
    }
    val effectiveAdaptiveInfo = remember(adaptiveInfo, configuration.screenWidthDp, configuration.screenHeightDp, isForcedRotatedTabletLandscape) {
        if (isForcedRotatedTabletLandscape) {
            com.concept.music.ui.theme.computeAdaptiveLayoutInfo(
                widthDp = maxOf(configuration.screenWidthDp, configuration.screenHeightDp),
                heightDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp),
                isTabletFlavor = true
            )
        } else {
            adaptiveInfo
        }
    }

    CompositionLocalProvider(
        LocalConfiguration provides effectiveConfiguration,
        com.concept.music.ui.theme.LocalConceptAdaptiveInfo provides effectiveAdaptiveInfo
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isForcedRotatedTabletLandscape) {
                    Modifier.layout { measurable, constraints ->
                        val w = constraints.maxWidth
                        val h = constraints.maxHeight
                        val placeable = measurable.measure(
                            Constraints(
                                minWidth = h,
                                maxWidth = h,
                                minHeight = w,
                                maxHeight = w
                            )
                        )
                        layout(w, h) {
                            placeable.placeWithLayer(
                                x = (w - h) / 2,
                                y = (h - w) / 2
                            ) {
                                rotationZ = tabletForcedRotationDeg
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                translationY = if (isForcedRotatedTabletLandscape) 0f else verticalDragAnim.value.coerceAtLeast(0f)
            }
            .background(Color(0xFF080C14))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        val dimAlpha = if (showLyricView || effectiveLandscape) lyricBackgroundDim.coerceIn(0.40f, 0.92f) else 0.78f

        // 1. 多模式可切换沉浸式背景层（支持三色流光弥散、无封面三色极光、实时音频同频律动光晕与深空暗夜星尘）
        NowPlayingSelectableBackground(
            coverUrl = song.coverUrl,
            backgroundStyle = playerBackgroundStyle,
            isPlaying = isPlaying,
            showLyricView = showLyricView || effectiveLandscape,
            dimAlpha = dimAlpha,
            meshColorPrimary = meshColorPrimary,
            meshColorSecondary = meshColorSecondary,
            meshColorTertiary = meshColorTertiary,
            accentPrimary = activeAccentColor,
            accentSecondary = activeSecondaryAccent,
            accentTertiary = activeTertiaryAccent
        )

        AnimatedContent(
            targetState = effectiveLandscape,
            transitionSpec = {
                (fadeIn(tween(420, easing = ConceptMotion.GentleSpatialEnterEasing)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(420, easing = ConceptMotion.GentleSpatialEnterEasing)))
                    .togetherWith(
                        fadeOut(tween(320, easing = ConceptMotion.GentleSpatialExitEasing)) +
                            scaleOut(targetScale = 0.96f, animationSpec = tween(320, easing = ConceptMotion.GentleSpatialExitEasing))
                    )
            },
            modifier = Modifier.fillMaxSize(),
            label = "PortraitLandscapeTransition"
        ) { isLandscapeStage ->
            if (isLandscapeStage) {
                LandscapePlayerPage(
                    song = song,
                    isPlaying = isPlaying,
                    currentPositionMs = effectiveLyricPositionMs,
                    rawPositionMs = currentPositionMs,
                    lyricPositionProvider = lyricPositionProvider,
                    rawPositionProvider = rawPositionProvider,
                    durationMs = durationMs,
                    playMode = playMode,
                    coverStyle = coverStyle,
                    rotationProvider = { rotationAnim.value % 360f },
                    playerBackgroundStyle = playerBackgroundStyle,
                    lyricData = lyricData,
                    currentLyricIndex = currentLyricIndex,
                    isFavorite = isFavorite,
                    showTranslation = showTranslation,
                    showTranslationButton = showTranslationBtn,
                    showPhonetic = showPhonetic,
                    showPhoneticButton = showPhoneticBtn,
                    lyricFontSize = lyricFontSize,
                    lyricTranslationScale = lyricTranslationScale,
                    lyricWordByWord = lyricWordByWord,
                    lyricBlurEnabled = lyricBlurEnabled,
                    lyricBlurIntensity = lyricBlurIntensity,
                    lyricLineSpacing = lyricLineSpacing,
                    lyricHighlightTheme = lyricHighlightTheme,
                    climaxStartMs = song.effectiveClimaxStartMs,
                    climaxEndMs = song.effectiveClimaxEndMs,
                    highlightClimaxRange = climaxModeEnabled && !climaxUnlockedForCurrentSong,
                    accentPrimary = activeAccentColor,
                    accentSecondary = activeSecondaryAccent,
                    currentQualityLabel = currentQualityLabel,
                    commentCount = commentCount,
                    viperEnabled = audioEffectState.isEnabled,
                    desktopLyricEnabled = systemLyricState.floatingCapsuleEnabled,
                    onExitLandscape = exitLandscape,
                    onTogglePlay = onTogglePlay,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeekTo = onSeekTo,
                    onSeekToLyricLine = onSeekToLyricLine,
                    onToggleFavorite = onToggleFavorite,
                    onCyclePlayMode = onCyclePlayMode,
                    onOpenQueue = { showQueueSheet = true },
                    onToggleTranslation = onToggleTranslation,
                    onTogglePhonetic = onTogglePhonetic,
                    onOpenLyricSettings = { showLyricSettingsDialog = true },
                    onSelectPlayerBackgroundStyle = onSelectPlayerBackgroundStyle,
                    onLongPressCover = { showCoverPreviewDialog = true },
                    onQualityClick = { showQualityDialog = true },
                    onAddToPlaylist = onAddToPlaylist,
                    onOpenLyricPoster = { showLyricPosterDialog = true },
                    onToggleDesktopLyric = {
                        SystemLyricBridge.toggleDesktopLyricWithPermissionCheck(context)
                    },
                    onOpenAudioEffect = openViperAction,
                    onOpenComments = onOpenComments,
                    onOpenMoreSheet = { showMoreControlSheet = true }
                )
            } else {
                // 2. 竖屏主体内容区（平板竖屏/分屏下受控最大宽度居中展示，避免进度条与按钮过度拉宽）
                val density = LocalDensity.current
                val dismissSlideTargetPx = remember(configuration.screenHeightDp, density) {
                    with(density) { configuration.screenHeightDp.dp.toPx() }.coerceAtLeast(1200f)
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(
                            start = if (isLargeScreen) 38.dp else 22.dp,
                            end = if (isLargeScreen) 38.dp else 22.dp,
                            top = if (isLargeScreen) 8.dp else 0.dp,
                            bottom = if (isLargeScreen) 18.dp else 10.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(showLyricView, dismissSlideTargetPx) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    if (dragAmount > 0f || verticalDragAnim.value > 0f) {
                                        change.consume()
                                        sheetDragScope.launch {
                                            verticalDragAnim.snapTo(
                                                (verticalDragAnim.value + dragAmount * 0.90f).coerceAtLeast(0f)
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    sheetDragScope.launch {
                                        verticalDragAnim.animateTo(
                                            targetValue = 0f,
                                            animationSpec = ConceptMotion.gentleBounceSpring()
                                        )
                                    }
                                },
                                onDragEnd = {
                                    if (verticalDragAnim.value > 145f) {
                                        sheetDragScope.launch {
                                            verticalDragAnim.animateTo(
                                                targetValue = dismissSlideTargetPx,
                                                animationSpec = tween(durationMillis = 340, easing = ConceptMotion.GentleSpatialExitEasing)
                                            )
                                            onDismiss()
                                            verticalDragAnim.snapTo(0f)
                                        }
                                    } else {
                                        sheetDragScope.launch {
                                            verticalDragAnim.animateTo(
                                                targetValue = 0f,
                                                animationSpec = ConceptMotion.gentleBounceSpring()
                                            )
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    RedesignedTopHeaderBar(
                        title = song.title,
                        artist = song.artist,
                        qualityLabel = currentQualityLabel,
                        isLyricPage = showLyricView,
                        climaxModeEnabled = climaxModeEnabled,
                        climaxUnlocked = climaxUnlockedForCurrentSong,
                        sleepActive = sleepTimerRemainingSec > 0L || sleepPendingStopAtTrackEnd,
                        sleepRemainingSec = sleepTimerRemainingSec,
                        onBackOrDismiss = {
                            if (showLyricView) {
                                showLyricView = false
                            } else {
                                onDismiss()
                            }
                        },
                        onQualityClick = { showQualityDialog = true },
                        onUnlockFullTrack = {
                            onUnlockFullTrack()
                            Toast.makeText(context, "已解锁本曲完整播放", Toast.LENGTH_SHORT).show()
                        },
                        onOpenSleepTimer = { showSleepTimerDialog = true }
                    )
                }

                // 页面主体：通过 420ms/320ms 柔顺空间景深转场切换「封面播放页」与「重构专属歌词页」
                AnimatedContent(
                    targetState = showLyricView,
                    transitionSpec = {
                        if (targetState) {
                            (fadeIn(tween(420, easing = ConceptMotion.GentleSpatialEnterEasing)) +
                                scaleIn(initialScale = 0.95f, animationSpec = tween(420, easing = ConceptMotion.GentleSpatialEnterEasing)) +
                                slideInVertically(animationSpec = tween(420, easing = ConceptMotion.GentleSpatialEnterEasing)) { it / 16 }) togetherWith
                                (fadeOut(tween(300, easing = ConceptMotion.GentleSpatialExitEasing)) +
                                    scaleOut(targetScale = 0.95f, animationSpec = tween(320, easing = ConceptMotion.GentleSpatialExitEasing)) +
                                    slideOutVertically(animationSpec = tween(320, easing = ConceptMotion.GentleSpatialExitEasing)) { -it / 20 })
                        } else {
                            (fadeIn(tween(420, easing = ConceptMotion.GentleSpatialEnterEasing)) +
                                scaleIn(initialScale = 0.95f, animationSpec = tween(420, easing = ConceptMotion.GentleSpatialEnterEasing)) +
                                slideInVertically(animationSpec = tween(420, easing = ConceptMotion.GentleSpatialEnterEasing)) { -it / 20 }) togetherWith
                                (fadeOut(tween(300, easing = ConceptMotion.GentleSpatialExitEasing)) +
                                    scaleOut(targetScale = 0.95f, animationSpec = tween(320, easing = ConceptMotion.GentleSpatialExitEasing)) +
                                    slideOutVertically(animationSpec = tween(320, easing = ConceptMotion.GentleSpatialExitEasing)) { it / 16 })
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = "coverVsLyricStage"
                ) { isLyricPage ->
                    if (!isLyricPage) {
                        CoverPlayerPageContent(
                            song = song,
                            isPlaying = isPlaying,
                            coverStyle = coverStyle,
                            rotationProvider = { rotationAnim.value % 360f },
                            roundedCoverMotionState = sharedRoundedCoverMotionState,
                            lyricData = lyricData,
                            currentLyricIndex = currentLyricIndex,
                            currentPositionMs = effectiveLyricPositionMs,
                            lyricPositionProvider = lyricPositionProvider,
                            rawPositionProvider = rawPositionProvider,
                            durationMs = durationMs,
                            showTranslation = showTranslation,
                            showTranslationButton = showTranslationBtn,
                            showPhonetic = showPhonetic,
                            showPhoneticButton = showPhoneticBtn,
                            lyricWordByWord = lyricWordByWord,
                            lyricHighlightTheme = lyricHighlightTheme,
                            isFavorite = isFavorite,
                            playMode = playMode,
                            commentCount = commentCount,
                            viperEnabled = audioEffectState.isEnabled,
                            desktopLyricEnabled = systemLyricState.floatingCapsuleEnabled,
                            climaxStartMs = song.effectiveClimaxStartMs,
                            climaxEndMs = song.effectiveClimaxEndMs,
                            highlightClimaxRange = climaxModeEnabled && !climaxUnlockedForCurrentSong,
                            accentPrimary = activeAccentColor,
                            accentSecondary = activeSecondaryAccent,
                            showDoubleTapHeart = showDoubleTapHeart,
                            onTriggerDoubleTapHeart = {
                                showDoubleTapHeart = true
                                if (!isFavorite) onToggleFavorite()
                            },
                            onClearDoubleTapHeart = { showDoubleTapHeart = false },
                            onOpenLyricView = { showLyricView = true },
                            onOpenLyricPoster = { showLyricPosterDialog = true },
                            onToggleFavorite = onToggleFavorite,
                            onAddToPlaylist = onAddToPlaylist,
                            onOpenAudioEffect = openViperAction,
                            onToggleDesktopLyric = {
                                SystemLyricBridge.toggleDesktopLyricWithPermissionCheck(context)
                            },
                            onEnterLandscape = { enterLandscape(false) },
                            onOpenMoreSheet = { showMoreControlSheet = true },
                            onSeekTo = onSeekTo,
                            onTogglePlay = onTogglePlay,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onCyclePlayMode = onCyclePlayMode,
                            onOpenQueue = { showQueueSheet = true },
                            onOpenComments = onOpenComments,
                            onLongPressCover = { showCoverPreviewDialog = true }
                        )
                    } else {
                        RedesignedDedicatedLyricPage(
                            lyricData = lyricData,
                            currentLyricIndex = currentLyricIndex,
                            currentPositionMs = effectiveLyricPositionMs,
                            rawPositionMs = currentPositionMs,
                            lyricPositionProvider = lyricPositionProvider,
                            rawPositionProvider = rawPositionProvider,
                            durationMs = durationMs,
                            isPlaying = isPlaying,
                            isFavorite = isFavorite,
                            showTranslation = showTranslation,
                            showTranslationButton = showTranslationBtn,
                            showPhonetic = showPhonetic,
                            showPhoneticButton = showPhoneticBtn,
                            lyricFontSize = lyricFontSize,
                            lyricTranslationScale = lyricTranslationScale,
                            lyricAlignment = lyricAlignment,
                            lyricWordByWord = lyricWordByWord,
                            lyricBlurEnabled = lyricBlurEnabled,
                            lyricBlurIntensity = lyricBlurIntensity,
                            lyricLineSpacing = lyricLineSpacing,
                            lyricHighlightTheme = lyricHighlightTheme,
                            dynamicCoverAccent = coverDynamicPrimary,
                            dynamicCoverSecondary = coverDynamicSecondary,
                            onLineClick = onSeekToLyricLine,
                            onReturnToCover = { showLyricView = false },
                            onTogglePlay = onTogglePlay,
                            onSeekTo = onSeekTo,
                            onToggleFavorite = onToggleFavorite,
                            onToggleTranslation = onToggleTranslation,
                            onTogglePhonetic = onTogglePhonetic,
                            onOpenLyricSettings = { showLyricSettingsDialog = true }
                        )
                    }
                }
                }
            }
            }
        }

        // 播放队列弹窗（融合封面动态取色，置于根全屏 Box 内以完整覆盖横竖屏全窗口）
    if (showQueueSheet) {
        QueueBottomSheet(
            queue = queue,
            currentIndex = currentIndex,
            recentQueues = recentQueues,
            activeQueueId = activeQueueId,
            playMode = playMode,
            climaxModeEnabled = climaxModeEnabled,
            accentColor = activeAccentColor,
            secondaryAccentColor = activeSecondaryAccent,
            onToggleClimaxMode = onToggleClimaxMode,
            onCyclePlayMode = onCyclePlayMode,
            onSwitchRecentQueue = onSwitchRecentQueue,
            onDeleteRecentQueue = onDeleteRecentQueue,
            onDismiss = { showQueueSheet = false },
            onSongClick = {
                onPlayQueueSong(it)
            },
            onRemoveSong = onRemoveFromQueue,
            onClear = onClearQueue,
            onReorderQueueByMood = onReorderQueueByMood,
            onMoveQueueItem = onMoveQueueItem
        )
    }

    // 更多设置抽屉（含歌手主页、所属专辑直达入口）
    if (showMoreControlSheet) {
        PlayerMoreControlSheet(
            song = song,
            coverStyle = coverStyle,
            playerBackgroundStyle = playerBackgroundStyle,
            dynamicCoverAccentEnabled = dynamicCoverAccentEnabled,
            currentQualityLabel = currentQualityLabel,
            climaxModeEnabled = climaxModeEnabled,
            accompanyModeEnabled = accompanyModeEnabled,
            playbackSpeed = playbackSpeed,
            sleepTimerRemainingSec = sleepTimerRemainingSec,
            sleepStopAfterCurrentSong = sleepStopAfterCurrentSong,
            sleepPendingStopAtTrackEnd = sleepPendingStopAtTrackEnd,
            accentColor = activeAccentColor,
            onSelectCoverStyle = onSelectCoverStyle,
            onSelectPlayerBackgroundStyle = onSelectPlayerBackgroundStyle,
            onSetDynamicCoverAccentEnabled = onSetDynamicCoverAccentEnabled,
            onToggleClimaxMode = onToggleClimaxMode,
            onToggleAccompanyMode = onToggleAccompanyMode,
            onOpenSpeedPitch = {
                showMoreControlSheet = false
                showSpeedPitchDialog = true
            },
            onOpenSleepTimer = {
                showMoreControlSheet = false
                showSleepTimerDialog = true
            },
            onOpenQualitySelect = {
                showMoreControlSheet = false
                showQualityDialog = true
            },
            onOpenSongDetail = {
                showMoreControlSheet = false
                showDetailSheet = true
            },
            onOpenArtist = {
                showMoreControlSheet = false
                val artistNames = parseSongArtistNames(song.artist)
                if (artistNames.size >= 2) {
                    showArtistPickerDialog = true
                } else {
                    onOpenArtist(song)
                }
            },
            onOpenAlbum = {
                showMoreControlSheet = false
                onOpenAlbum(song)
            },
            onDownloadSong = {
                showMoreControlSheet = false
                showDownloadDialog = true
            },
            onDismiss = { showMoreControlSheet = false }
        )
    }

    // 多音质下载与封面/歌词内嵌弹窗
    if (showDownloadDialog) {
        SongDownloadDialog(
            song = song,
            onDismiss = { showDownloadDialog = false }
        )
    }

    // 高定歌词海报工坊
    if (showLyricPosterDialog) {
        LyricPosterDialog(
            song = song,
            lyricData = lyricData,
            currentLyricIndex = currentLyricIndex,
            onDismiss = { showLyricPosterDialog = false }
        )
    }

    // 音质选择弹窗
    if (showQualityDialog) {
        QualitySelectDialog(
            availableQualities = song.effectiveQualities,
            currentQualityLabel = currentQualityLabel,
            onDismiss = { showQualityDialog = false },
            onSelectQuality = {
                onSelectQuality(it)
            }
        )
    }

    // 独立蝰蛇音效与专业均衡器页面
    if (showAudioEffectScreen) {
        AudioEffectDialog(onDismiss = { showAudioEffectScreen = false })
    }

    // 重构版歌词设置面板（移除音译/翻译/桌面歌词冗余设置）
    if (showLyricSettingsDialog) {
        LyricSettingsDialog(
            fontSize = lyricFontSize,
            translationScale = lyricTranslationScale,
            alignment = lyricAlignment,
            wordByWord = lyricWordByWord,
            blurEnabled = lyricBlurEnabled,
            blurIntensity = lyricBlurIntensity,
            lineSpacing = lyricLineSpacing,
            offsetMs = lyricOffsetMs,
            highlightTheme = lyricHighlightTheme,
            backgroundDim = lyricBackgroundDim,
            keepScreenOn = lyricKeepScreenOn,
            dynamicAccentColor = coverDynamicPrimary,
            onSetFontSize = onSetLyricFontSize,
            onSetTranslationScale = onSetLyricTranslationScale,
            onSetAlignment = onSetLyricAlignment,
            onSetWordByWord = onSetLyricWordByWord,
            onSetBlurEnabled = onSetLyricBlurEnabled,
            onSetBlurIntensity = onSetLyricBlurIntensity,
            onSetLineSpacing = onSetLyricLineSpacing,
            onSetOffsetMs = onSetLyricOffsetMs,
            onSetHighlightTheme = onSetLyricHighlightTheme,
            onSetBackgroundDim = onSetLyricBackgroundDim,
            onSetKeepScreenOn = onSetLyricKeepScreenOn,
            onDismiss = { showLyricSettingsDialog = false }
        )
    }

    // 睡眠定时器弹窗
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingSec = sleepTimerRemainingSec,
            stopAfterCurrentSong = sleepStopAfterCurrentSong,
            pendingStopAtTrackEnd = sleepPendingStopAtTrackEnd,
            onToggleStopAfterSongPref = onSetSleepStopAfterCurrentSongPref,
            onSetTimer = { mins, stopCurrent ->
                onSetSleepTimer(mins, stopCurrent)
            },
            onCancelTimer = {
                onCancelSleepTimer()
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    // 倍速与变调弹窗
    if (showSpeedPitchDialog) {
        SpeedPitchDialog(
            speed = playbackSpeed,
            pitch = playbackPitch,
            linkPitchToSpeed = linkPitchToSpeed,
            onSetSpeed = onSetPlaybackSpeed,
            onSetPitch = onSetPlaybackPitch,
            onSetLinkPitch = onSetLinkPitchToSpeed,
            onReset = onResetSpeedAndPitch,
            onDismiss = { showSpeedPitchDialog = false }
        )
    }

        // 歌曲技术参数详情弹窗（即使 songDetail 尚未异步填充，也立即基于当前 Song 渲染参数卡片）
        if (showDetailSheet) {
            val effectiveDetail = songDetail ?: remember(song) {
                com.concept.music.data.vip.VipManager.music.songDetail(song)
            }
            SongDetailSheet(
                detail = effectiveDetail,
                onDismiss = { showDetailSheet = false }
            )
        }

        // 播放页长按黑胶/方形封面：高清封面预览与纯图标下载按钮（无文字说明）
        if (showCoverPreviewDialog) {
            HdArtworkPreviewDialog(
                artworkUrl = song.coverUrl,
                title = song.title,
                subtitle = song.artist,
                category = ArtworkDownloadCategory.SONG_COVER,
                songForFallback = song,
                iconOnlySaveButton = true,
                accentColor = activeAccentColor,
                onDismiss = { showCoverPreviewDialog = false }
            )
        }

        // 播放页内多歌手（>=2位）选择悬浮窗：适配播放页封面动态流光配色
        if (showArtistPickerDialog) {
            val artistNames = remember(song.artist) { parseSongArtistNames(song.artist) }
            ArtistSelectionFloatingDialog(
                song = song,
                artistNames = artistNames,
                isPlayerStyle = true,
                accentColor = activeAccentColor,
                onSelectArtist = { chosenName, resolvedId ->
                    showArtistPickerDialog = false
                    onOpenArtist(song.copy(artist = chosenName, artistId = resolvedId.orEmpty()))
                },
                onDismiss = { showArtistPickerDialog = false }
            )
        }
    }
    }
}

@Composable
private fun NowPlayingSelectableBackground(
    coverUrl: String,
    backgroundStyle: PlayerBackgroundStyle,
    isPlaying: Boolean,
    showLyricView: Boolean,
    dimAlpha: Float,
    meshColorPrimary: Color,
    meshColorSecondary: Color,
    meshColorTertiary: Color = Color(0xFF191B38),
    accentPrimary: Color,
    accentSecondary: Color,
    accentTertiary: Color = ConceptVioletAccent
) {
    val context = LocalContext.current

    // 播放 <-> 暂停连续动量因子：暂停时从 1f 丝滑减速至 0f (880ms)，恢复播放时从 0f 原位柔和加速至 1f，彻底消除暂停/播放切换时的位置跳变
    val playMomentum by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 880, easing = FastOutSlowInEasing),
        label = "bgPlayMomentum"
    )

    var phase by remember { mutableFloatStateOf(0.85f) }
    var secPhase by remember { mutableFloatStateOf(1.45f) }
    var pulsePhase by remember { mutableFloatStateOf(0f) }

    // 律动光晕模式专用的 60fps 实时音频能量追踪状态（读取 ConceptMasteringAudioProcessor 实时计算的歌曲低/中/高频与节拍脉冲）
    var liveBassEnergy by remember { mutableFloatStateOf(0.08f) }
    var liveMidEnergy by remember { mutableFloatStateOf(0.06f) }
    var liveHighEnergy by remember { mutableFloatStateOf(0.05f) }
    var liveBeatImpulse by remember { mutableFloatStateOf(0f) }

    val isAnimatedBackground = backgroundStyle == PlayerBackgroundStyle.FLUID_AURORA ||
        backgroundStyle == PlayerBackgroundStyle.RHYTHM_HALO ||
        backgroundStyle == PlayerBackgroundStyle.STARLIGHT_NEBULA

    LaunchedEffect(backgroundStyle, isPlaying, playMomentum > 0.002f) {
        if (isAnimatedBackground && (isPlaying || playMomentum > 0.002f || liveBassEnergy > 0.09f || liveBeatImpulse > 0.005f)) {
            var waveClock = 0f
            var lastNanos = 0L
            val twoPi = (2.0 * PI).toFloat()
            while (isActive) {
                var keepRunning = false
                withFrameNanos { nanos ->
                    val dt = if (lastNanos == 0L) 0.016f else ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
                    lastNanos = nanos
                    val m = playMomentum.coerceIn(0f, 1f)

                    if (m > 0.001f) {
                        phase = (phase + dt * 0.349f * m) % twoPi
                        secPhase = (secPhase + dt * 0.546f * m) % twoPi
                        pulsePhase = (pulsePhase + dt * 2.416f * m) % twoPi
                        keepRunning = true
                    }

                    if (backgroundStyle == PlayerBackgroundStyle.RHYTHM_HALO) {
                        waveClock = (waveClock + dt * m) % 628.318f
                        val telemetryActive = isPlaying && com.concept.music.service.AudioRhythmTelemetry.isActive()
                        val rawBass = if (telemetryActive) {
                            com.concept.music.service.AudioRhythmTelemetry.bassEnergy
                        } else {
                            0.24f + 0.14f * abs(sin(waveClock * 2.2f))
                        }
                        val rawMid = if (telemetryActive) {
                            com.concept.music.service.AudioRhythmTelemetry.midEnergy
                        } else {
                            0.20f + 0.12f * abs(cos(waveClock * 1.8f + 0.7f))
                        }
                        val rawHigh = if (telemetryActive) {
                            com.concept.music.service.AudioRhythmTelemetry.highEnergy
                        } else {
                            0.16f + 0.10f * abs(sin(waveClock * 2.9f + 1.4f))
                        }
                        val rawBeat = if (telemetryActive) {
                            com.concept.music.service.AudioRhythmTelemetry.beatImpulse
                        } else {
                            0.12f * abs(sin(waveClock * 2.6f)).pow(3)
                        }

                        // 按播放动量 m 平滑收放能量，使播放->暂停与暂停->播放拥有连续丝滑的呼吸阻尼过渡
                        val targetBass = 0.08f + (rawBass - 0.08f) * m
                        val targetMid = 0.06f + (rawMid - 0.06f) * m
                        val targetHigh = 0.05f + (rawHigh - 0.05f) * m
                        val targetBeat = rawBeat * m

                        liveBassEnergy += (targetBass - liveBassEnergy) * (if (targetBass > liveBassEnergy) 0.28f else 0.10f)
                        liveMidEnergy += (targetMid - liveMidEnergy) * (if (targetMid > liveMidEnergy) 0.25f else 0.10f)
                        liveHighEnergy += (targetHigh - liveHighEnergy) * (if (targetHigh > liveHighEnergy) 0.30f else 0.12f)
                        liveBeatImpulse += (targetBeat - liveBeatImpulse) * (if (targetBeat > liveBeatImpulse) 0.36f else 0.12f)

                        if (abs(liveBassEnergy - 0.08f) > 0.004f || liveBeatImpulse > 0.004f) {
                            keepRunning = true
                        }
                    }
                }
                if (!keepRunning && !isPlaying && playMomentum <= 0.002f) {
                    break
                }
            }
        }
    }

    val pulse = 0.96f + (0.04f + 0.12f * playMomentum) * (0.5f + 0.5f * sin(pulsePhase))

    // 采用 AnimatedContent 包裹五种背景预设，确保切换背景或切歌时图层独立无残留
    AnimatedContent(
        targetState = backgroundStyle,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
                .togetherWith(fadeOut(animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)))
        },
        modifier = Modifier.fillMaxSize(),
        label = "NowPlayingBackgroundStyleTransition"
    ) { activeStyle ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeStyle) {
                PlayerBackgroundStyle.COVER_BLUR -> {
                    // 还原原版封面虚化视觉，同时将请求尺寸与黑胶唱盘统一为 400x400，彻底修复原版切歌时因 24x24 缓存未命中导致莫名变糊的 BUG
                    if (coverUrl.isNotEmpty()) {
                        val bgImageModel = remember(coverUrl) {
                            ImageRequest.Builder(context)
                                .data(coverUrl)
                                .size(400, 400)
                                .allowHardware(false)
                                .crossfade(true)
                                .build()
                        }
                        AsyncImage(
                            model = bgImageModel,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF070B12).copy(alpha = dimAlpha),
                                        Color(0xFF0D1422).copy(alpha = (dimAlpha + 0.06f).coerceAtMost(0.95f)),
                                        Color(0xFF060910).copy(alpha = (dimAlpha + 0.12f).coerceAtMost(0.97f))
                                    )
                                )
                            )
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        meshColorPrimary.copy(alpha = if (showLyricView) 0.44f else 0.56f),
                                        Color.Transparent
                                    ),
                                    center = Offset(240f, 360f),
                                    radius = 980f
                                )
                            )
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        meshColorSecondary.copy(alpha = if (showLyricView) 0.38f else 0.48f),
                                        Color.Transparent
                                    ),
                                    center = Offset(820f, 1420f),
                                    radius = 1080f
                                )
                            )
                    )
                }

                PlayerBackgroundStyle.FLUID_AURORA -> {
                    // 纯净三色流体极光（不显示专辑封面，基于封面提取的三种色彩 + 深邃色阶平滑交融流动）
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val maxDim = maxOf(w, h).coerceAtLeast(1f)

                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF060A14),
                                    meshColorPrimary.copy(alpha = 0.72f),
                                    meshColorSecondary.copy(alpha = 0.64f),
                                    Color(0xFF050811)
                                )
                            )
                        )

                        val orb1 = Offset(
                            w * (0.26f + 0.22f * cos(phase)),
                            h * (0.24f + 0.14f * sin(phase))
                        )
                        val orb2 = Offset(
                            w * (0.74f + 0.20f * sin(phase * 0.85f + 1.2f)),
                            h * (0.48f + 0.16f * cos(phase * 0.9f))
                        )
                        val orb3 = Offset(
                            w * (0.38f + 0.24f * sin(secPhase * 0.85f + 2.2f)),
                            h * (0.76f + 0.12f * cos(phase * 0.75f))
                        )
                        val orb4 = Offset(
                            w * (0.64f - 0.18f * cos(secPhase + 0.8f)),
                            h * (0.28f + 0.15f * sin(phase * 0.7f + 1.9f))
                        )

                        // 第一流体主色光团 (Primary Fluid Bloom)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentPrimary.copy(alpha = 0.42f),
                                    meshColorPrimary.copy(alpha = 0.24f),
                                    Color.Transparent
                                ),
                                center = orb1,
                                radius = maxDim * 0.62f * pulse
                            ),
                            center = orb1,
                            radius = maxDim * 0.62f * pulse
                        )

                        // 第二流体副色光团 (Secondary Fluid Bloom)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentSecondary.copy(alpha = 0.38f),
                                    meshColorSecondary.copy(alpha = 0.22f),
                                    Color.Transparent
                                ),
                                center = orb2,
                                radius = maxDim * 0.58f * (1.98f - pulse * 0.98f)
                            ),
                            center = orb2,
                            radius = maxDim * 0.58f * (1.98f - pulse * 0.98f)
                        )

                        // 第三流体极光色团 (Tertiary Fluid Bloom)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentTertiary.copy(alpha = 0.36f),
                                    meshColorTertiary.copy(alpha = 0.26f),
                                    Color.Transparent
                                ),
                                center = orb3,
                                radius = maxDim * 0.65f * pulse
                            ),
                            center = orb3,
                            radius = maxDim * 0.65f * pulse
                        )

                        // 伴随融合流体晕染层，使三种颜色过渡更加饱满丝滑
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentTertiary.copy(alpha = 0.22f),
                                    accentPrimary.copy(alpha = 0.12f),
                                    Color.Transparent
                                ),
                                center = orb4,
                                radius = maxDim * 0.52f
                            ),
                            center = orb4,
                            radius = maxDim * 0.52f
                        )

                        if (showLyricView) {
                            drawRect(color = Color(0xFF060911).copy(alpha = (dimAlpha * 0.55f).coerceIn(0.20f, 0.78f)))
                        }
                    }
                }

                PlayerBackgroundStyle.RHYTHM_HALO -> {
                    // 还原原版三层同心圆光晕视觉，同时保留根据当前播放歌曲真实音频能量同频律动与播放/暂停平滑阻尼过渡
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    meshColorPrimary.copy(alpha = 0.85f),
                                    Color(0xFF080D18),
                                    Color(0xFF05080F)
                                )
                            )
                        )
                        val center = Offset(size.width * 0.5f, size.height * 0.40f)
                        val baseR = size.width * 0.36f
                        for (ring in 3 downTo 1) {
                            val ringEnergy = when (ring) {
                                3 -> liveBassEnergy * 0.55f + liveBeatImpulse * 0.35f
                                2 -> liveMidEnergy * 0.50f + liveBassEnergy * 0.25f + liveBeatImpulse * 0.25f
                                else -> liveHighEnergy * 0.45f + liveMidEnergy * 0.30f + liveBeatImpulse * 0.22f
                            }
                            val activeScale = (0.94f + ringEnergy * 0.34f).coerceIn(0.90f, 1.32f)
                            val dynamicPulse = pulse * (1f - playMomentum) + activeScale * playMomentum
                            val r = baseR * (1f + ring * 0.24f) * dynamicPulse
                            val alphaBoost = 1f + ringEnergy * 0.55f * playMomentum
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        accentPrimary.copy(alpha = ((0.22f * alphaBoost) / ring).coerceAtMost(0.42f)),
                                        accentSecondary.copy(alpha = ((0.12f * alphaBoost) / ring).coerceAtMost(0.26f)),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = r
                                ),
                                center = center,
                                radius = r
                            )
                            drawCircle(
                                color = accentPrimary.copy(alpha = ((0.15f * alphaBoost) / ring).coerceAtMost(0.32f)),
                                center = center,
                                radius = r * 0.78f,
                                style = Stroke(width = 1.4.dp.toPx())
                            )
                        }
                        if (showLyricView) {
                            drawRect(color = Color(0xFF050811).copy(alpha = (dimAlpha * 0.55f).coerceIn(0.20f, 0.78f)))
                        }
                    }
                }

                PlayerBackgroundStyle.STARLIGHT_NEBULA -> {
                    // 保留深空星云与柔和圆点星尘美化，彻底去掉十字星芒线条与流星线条，并支持播放/暂停平滑减速过渡
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val maxDim = maxOf(w, h).coerceAtLeast(1f)

                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF040711),
                                    meshColorPrimary.copy(alpha = 0.80f),
                                    meshColorTertiary.copy(alpha = 0.68f),
                                    Color(0xFF03050B)
                                )
                            )
                        )

                        val neb1 = Offset(
                            w * (0.28f + 0.08f * cos(phase)),
                            h * (0.30f + 0.06f * sin(phase))
                        )
                        val neb2 = Offset(
                            w * (0.74f - 0.07f * sin(phase * 0.8f)),
                            h * (0.64f + 0.06f * cos(phase * 0.8f))
                        )
                        val neb3 = Offset(
                            w * (0.48f + 0.09f * sin(secPhase)),
                            h * (0.46f - 0.05f * cos(secPhase))
                        )

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentPrimary.copy(alpha = 0.25f * pulse),
                                    meshColorPrimary.copy(alpha = 0.16f),
                                    Color.Transparent
                                ),
                                center = neb1,
                                radius = maxDim * 0.56f
                            ),
                            center = neb1,
                            radius = maxDim * 0.56f
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentSecondary.copy(alpha = 0.20f),
                                    meshColorSecondary.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                center = neb2,
                                radius = maxDim * 0.52f
                            ),
                            center = neb2,
                            radius = maxDim * 0.52f
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentTertiary.copy(alpha = 0.18f),
                                    Color.Transparent
                                ),
                                center = neb3,
                                radius = maxDim * 0.46f * pulse
                            ),
                            center = neb3,
                            radius = maxDim * 0.46f * pulse
                        )

                        // 柔和圆点星尘（无十字星芒线条）
                        val normPhase = phase / (2f * PI.toFloat())
                        for (i in 0 until 48) {
                            val layer = i % 3
                            val seedX = ((i * 73 + 19) % 97) / 97f
                            val seedY = ((i * 41 + 11) % 89) / 89f
                            val speed = when (layer) {
                                0 -> 0.08f
                                1 -> 0.14f
                                else -> 0.20f
                            }
                            val driftY = (seedY - normPhase * speed + 1f) % 1f
                            val driftX = (seedX + 0.02f * sin(phase + i * 0.7f)).coerceIn(0.03f, 0.97f)
                            val twinkle = (0.32f + 0.68f * abs(sin(secPhase * (0.9f + (i % 4) * 0.26f) + i * 1.3f))).coerceIn(0.18f, 1f)

                            val sx = w * driftX
                            val sy = h * driftY
                            val sr = when (layer) {
                                0 -> 1.0f.dp.toPx()
                                1 -> 1.5f.dp.toPx()
                                else -> 2.1f.dp.toPx()
                            }
                            val starColor = when (i % 5) {
                                0 -> accentPrimary
                                1 -> accentSecondary
                                2 -> accentTertiary
                                3 -> Color(0xFFFFF4D6)
                                else -> Color.White
                            }

                            if (layer >= 1) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            starColor.copy(alpha = 0.28f * twinkle),
                                            Color.Transparent
                                        ),
                                        center = Offset(sx, sy),
                                        radius = sr * 3.2f
                                    ),
                                    center = Offset(sx, sy),
                                    radius = sr * 3.2f
                                )
                            }

                            drawCircle(
                                color = starColor.copy(alpha = (0.42f + layer * 0.20f).coerceAtMost(0.90f) * twinkle),
                                radius = sr,
                                center = Offset(sx, sy)
                            )
                        }

                        if (showLyricView) {
                            drawRect(color = Color(0xFF050811).copy(alpha = (dimAlpha * 0.50f).coerceIn(0.18f, 0.75f)))
                        }
                    }
                }

                PlayerBackgroundStyle.DEEP_OLED -> {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(color = Color(0xFF04060A))
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentPrimary.copy(alpha = if (showLyricView) 0.09f else 0.15f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.38f),
                                radius = size.width * 0.62f
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.38f),
                            radius = size.width * 0.62f
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RedesignedTopHeaderBar(
    title: String,
    artist: String,
    qualityLabel: String,
    isLyricPage: Boolean,
    climaxModeEnabled: Boolean,
    climaxUnlocked: Boolean,
    sleepActive: Boolean,
    sleepRemainingSec: Long,
    onBackOrDismiss: () -> Unit,
    onQualityClick: () -> Unit,
    onUnlockFullTrack: () -> Unit,
    onOpenSleepTimer: () -> Unit
) {
    val context = LocalContext.current
    var outputRouteLabel by remember { mutableStateOf(detectAudioOutputRouteLabel(context)) }
    DisposableEffect(context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val callback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                outputRouteLabel = detectAudioOutputRouteLabel(context)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                outputRouteLabel = detectAudioOutputRouteLabel(context)
            }
        }
        am?.registerAudioDeviceCallback(callback, null)
        onDispose {
            am?.unregisterAudioDeviceCallback(callback)
        }
    }
    val isGoldQuality = qualityLabel.startsWith("Hi-Res", ignoreCase = true)
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = if (isLargeScreen) (-2).dp else (-6).dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLargeScreen) 42.dp else 32.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBackOrDismiss,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(if (isLargeScreen) 44.dp else 36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "返回",
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(if (isLargeScreen) 34.dp else 28.dp)
                )
            }

            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 3 })
                        .togetherWith(
                            fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                                slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it / 3 }
                        )
                },
                label = "headerSongTitleTransition",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isLargeScreen) 56.dp else 44.dp)
            ) { displayTitle ->
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = if (isLargeScreen) 24.sp else 20.sp,
                        letterSpacing = 0.2.sp
                    ),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
            }
        }

        if (isLargeScreen) {
            Spacer(modifier = Modifier.height(2.dp))
        }

        AnimatedContent(
            targetState = artist.ifBlank { "未知歌手" },
            transitionSpec = {
                fadeIn(tween(220, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(tween(150, easing = FastOutSlowInEasing)))
            },
            label = "headerSongArtistTransition"
        ) { displayArtist ->
            Text(
                text = displayArtist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = if (isLargeScreen) 15.5.sp else 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }

        AnimatedVisibility(
            visible = !isLyricPage,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            Row(
                modifier = Modifier.padding(top = if (isLargeScreen) 5.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(if (isLargeScreen) 10.dp else 8.dp),
                    color = Color.White.copy(alpha = 0.07f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = if (isLargeScreen) 10.dp else 7.dp,
                            vertical = if (isLargeScreen) 3.5.dp else 2.dp
                        )
                    ) {
                        Icon(
                            imageVector = if (outputRouteLabel.contains("蓝牙") || outputRouteLabel.contains("耳机")) {
                                Icons.Rounded.Headphones
                            } else {
                                Icons.Rounded.SpeakerGroup
                            },
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.66f),
                            modifier = Modifier.size(if (isLargeScreen) 13.5.dp else 11.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = outputRouteLabel,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = if (isLargeScreen) 12.sp else 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(if (isLargeScreen) 10.dp else 8.dp),
                    color = if (isGoldQuality) VipGold.copy(alpha = 0.18f) else ConceptPrimary.copy(alpha = 0.16f),
                    modifier = Modifier.softCardClickable(
                        shape = RoundedCornerShape(if (isLargeScreen) 10.dp else 8.dp),
                        onClick = onQualityClick
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = if (isLargeScreen) 10.dp else 7.dp,
                            vertical = if (isLargeScreen) 3.5.dp else 2.dp
                        )
                    ) {
                        Text(
                            text = qualityLabel,
                            color = if (isGoldQuality) VipGold else ConceptPrimary,
                            fontSize = if (isLargeScreen) 12.sp else 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "切换音质",
                            tint = if (isGoldQuality) VipGold else ConceptPrimary,
                            modifier = Modifier.size(if (isLargeScreen) 13.5.dp else 11.dp)
                        )
                    }
                }

                if (climaxModeEnabled && !climaxUnlocked) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VipGold.copy(alpha = 0.20f),
                        modifier = Modifier.softCardClickable(
                            shape = RoundedCornerShape(8.dp),
                            onClick = onUnlockFullTrack
                        )
                    ) {
                        Text(
                            text = "副歌速刷 · 听整首",
                            color = VipGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (sleepActive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ConceptPrimary.copy(alpha = 0.16f),
                        modifier = Modifier.softCardClickable(
                            shape = RoundedCornerShape(8.dp),
                            onClick = onOpenSleepTimer
                        )
                    ) {
                        Text(
                            text = if (sleepRemainingSec > 0L) "定时 ${formatDuration(sleepRemainingSec * 1000L)}" else "播完本首停",
                            color = ConceptPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverPlayerPageContent(
    song: Song,
    isPlaying: Boolean,
    coverStyle: CoverStyle,
    rotationProvider: () -> Float,
    roundedCoverMotionState: RoundedCoverMotionState? = null,
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    lyricPositionProvider: () -> Long = { currentPositionMs },
    rawPositionProvider: () -> Long = { currentPositionMs },
    durationMs: Long,
    showTranslation: Boolean,
    showTranslationButton: Boolean,
    showPhonetic: Boolean,
    showPhoneticButton: Boolean = false,
    lyricWordByWord: Boolean = true,
    lyricHighlightTheme: LyricHighlightTheme = LyricHighlightTheme.ICE_BLUE,
    isFavorite: Boolean,
    playMode: PlayMode,
    commentCount: Int,
    viperEnabled: Boolean,
    desktopLyricEnabled: Boolean,
    climaxStartMs: Long,
    climaxEndMs: Long,
    highlightClimaxRange: Boolean,
    accentPrimary: Color,
    accentSecondary: Color,
    showDoubleTapHeart: Boolean,
    onTriggerDoubleTapHeart: () -> Unit,
    onClearDoubleTapHeart: () -> Unit,
    onOpenLyricView: () -> Unit,
    onOpenLyricPoster: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenAudioEffect: () -> Unit,
    onToggleDesktopLyric: () -> Unit,
    onEnterLandscape: () -> Unit,
    onOpenMoreSheet: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenComments: () -> Unit,
    onLongPressCover: () -> Unit = {}
) {
    val favBurstAnim = remember { Animatable(1f) }
    var prevFavState by remember { mutableStateOf(isFavorite) }
    LaunchedEffect(isFavorite) {
        if (isFavorite != prevFavState) {
            prevFavState = isFavorite
            if (isFavorite) {
                favBurstAnim.snapTo(0.75f)
                favBurstAnim.animateTo(1.18f, ConceptMotion.expressiveSpring())
                favBurstAnim.animateTo(1.0f, ConceptMotion.gentleBounceSpring())
            } else {
                favBurstAnim.animateTo(0.90f, tween(90))
                favBurstAnim.animateTo(1.0f, ConceptMotion.gentleBounceSpring())
            }
        }
    }
    val favScale = favBurstAnim.value
    val coverSwipeScope = rememberCoroutineScope()
    val coverDragX = remember { Animatable(0f) }
    var scrubPreviewPositionMs by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 封面大舞台（扩大点击触发进入歌词范围至整个中央舞台区域）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(song.id, isFavorite) {
                    detectTapGestures(
                        onTap = { onOpenLyricView() },
                        onDoubleTap = { onTriggerDoubleTapHeart() },
                        onLongPress = { onLongPressCover() }
                    )
                }
                .pointerInput(song.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coverSwipeScope.launch {
                                coverDragX.snapTo(
                                    (coverDragX.value + dragAmount * 0.68f).coerceIn(-210f, 210f)
                                )
                            }
                        },
                        onDragCancel = {
                            coverSwipeScope.launch {
                                coverDragX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.74f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        onDragEnd = {
                            val finalDx = coverDragX.value
                            if (finalDx < -82f) {
                                onNext()
                            } else if (finalDx > 82f) {
                                onPrevious()
                            }
                            coverSwipeScope.launch {
                                coverDragX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.74f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            LaunchedEffect(showDoubleTapHeart) {
                if (showDoubleTapHeart) {
                    delay(750L)
                    onClearDoubleTapHeart()
                }
            }

            val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val dx = coverDragX.value
                        translationX = dx
                        rotationZ = (dx / 38f).coerceIn(-6.5f, 6.5f)
                        rotationY = (-dx / 28f).coerceIn(-9f, 9f)
                        val s = (1f - (abs(dx) / 2400f)).coerceIn(0.94f, 1f)
                        scaleX = s
                        scaleY = s
                        cameraDistance = 14f * density
                    },
                contentAlignment = Alignment.Center
            ) {
                val vinylScale = if (isLargeScreen) {
                    minOf((maxWidth * 0.78f) / 312.dp, (maxHeight * 0.88f) / 296.dp).coerceIn(1.0f, 1.42f)
                } else {
                    1.0f
                }
                val squareCoverSize = if (isLargeScreen) {
                    minOf(maxWidth * 0.72f, maxHeight * 0.84f).coerceIn(276.dp, 392.dp)
                } else {
                    276.dp
                }

                when (coverStyle) {
                    CoverStyle.VINYL -> {
                        MasterVinylTurntableView(
                            coverUrl = song.coverUrl,
                            isPlaying = isPlaying,
                            accentColor = accentPrimary,
                            rotationProvider = rotationProvider,
                            scale = vinylScale
                        )
                    }
                    CoverStyle.SQUARE -> {
                        StaticRoundedSquareCoverView(
                            coverUrl = song.coverUrl,
                            isPlaying = isPlaying,
                            accentColor = accentPrimary,
                            secondaryAccentColor = accentSecondary,
                            motionState = roundedCoverMotionState,
                            coverSize = squareCoverSize,
                            cornerRadius = if (isLargeScreen) 38.dp else 30.dp
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showDoubleTapHeart,
                    enter = scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = 0.5f)) + fadeIn(tween(100)),
                    exit = scaleOut(targetScale = 1.3f, animationSpec = tween(280)) + fadeOut(tween(280))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFF4D6D).copy(alpha = 0.92f),
                        modifier = Modifier.size(if (isLargeScreen) 108.dp else 88.dp)
                    )
                }
            }

            // 右侧三按钮恒定锚定在封面舞台右下角（第1：收藏，第2：添加到歌单，第3：歌词海报），紧邻下方固定三行预览歌词区
            val sideBtnBoxSize = if (isLargeScreen) 48.dp else 38.dp
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (isLargeScreen) 8.dp else 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLargeScreen) 12.dp else 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(sideBtnBoxSize)
                        .clip(CircleShape)
                        .graphicsLayer {
                            scaleX = favScale
                            scaleY = favScale
                        }
                        .softClickable(
                            pressScale = 0.82f,
                            onClick = onToggleFavorite
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PlumpHeartCanvasIcon(
                        isFavorite = isFavorite,
                        color = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(if (isLargeScreen) 28.dp else 22.dp)
                    )
                }

                val readableAccentPrimary = remember(accentPrimary) { accentPrimary.readableOnDarkSurface(0.45f) }

                Box(
                    modifier = Modifier
                        .size(sideBtnBoxSize)
                        .clip(CircleShape)
                        .softClickable(
                            pressScale = 0.84f,
                            onClick = onAddToPlaylist
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PlumpPlaylistAddCanvasIcon(
                        color = Color.White.copy(alpha = 0.88f),
                        accentColor = readableAccentPrimary,
                        iconSize = if (isLargeScreen) 30.dp else 24.dp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(sideBtnBoxSize)
                        .clip(CircleShape)
                        .softClickable(
                            pressScale = 0.84f,
                            onClick = onOpenLyricPoster
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PlumpPosterSparkleCanvasIcon(
                        color = Color.White.copy(alpha = 0.86f),
                        accentColor = readableAccentPrimary,
                        iconSize = if (isLargeScreen) 27.dp else 22.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 2. 全宽沉浸式歌词预览区（固定三行视口高度，杜绝抬高上方封面与按钮，支持 1行/2行/≥3行超长混动滚动；无极拖动进度条时实时联动预览对应时间歌词）
        val previewScrubPos = scrubPreviewPositionMs
        val effectivePreviewIndex = remember(previewScrubPos, currentLyricIndex, lyricData) {
            if (previewScrubPos != null && lyricData.lines.isNotEmpty()) {
                lyricData.getLineIndexAt(previewScrubPos).coerceAtLeast(0)
            } else {
                currentLyricIndex
            }
        }
        val effectivePreviewPosMs = previewScrubPos ?: currentPositionMs
        val effectivePreviewPosProvider: () -> Long = remember(previewScrubPos, lyricPositionProvider) {
            if (previewScrubPos != null) {
                { previewScrubPos }
            } else {
                lyricPositionProvider
            }
        }
        FullWidthLyricPreviewSection(
            lyricData = lyricData,
            currentLyricIndex = effectivePreviewIndex,
            currentPositionMs = effectivePreviewPosMs,
            isPlaying = isPlaying && previewScrubPos == null,
            positionProvider = effectivePreviewPosProvider,
            showTranslation = showTranslation && showTranslationButton,
            showPhonetic = showPhonetic && showPhoneticButton,
            lyricWordByWord = lyricWordByWord,
            lyricHighlightTheme = lyricHighlightTheme,
            accentPrimary = accentPrimary,
            accentSecondary = accentSecondary,
            onLyricClick = onOpenLyricView
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. 进度条上方功能栏：「横屏播放、桌面歌词、蝰蛇音效、带计数评论按钮、更多」
        // （已按需求将【音效与桌面歌词互换位置】、【评论与横屏播放互换位置】）
        val barAccentPrimary = remember(accentPrimary) { accentPrimary.readableOnDarkSurface(0.45f) }
        val barAccentSecondary = remember(accentSecondary) { accentSecondary.readableOnDarkSurface(0.48f) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerActionIconButton(
                onClick = onEnterLandscape
            ) {
                PlumpLandscapeModeCanvasIcon(
                    color = Color.White.copy(alpha = 0.88f),
                    accentColor = barAccentPrimary
                )
            }

            PlayerActionIconButton(
                onClick = onToggleDesktopLyric
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PlumpDesktopLyricCanvasIcon(
                        enabled = desktopLyricEnabled,
                        color = if (desktopLyricEnabled) barAccentPrimary else Color.White.copy(alpha = 0.88f),
                        accentColor = barAccentSecondary
                    )
                    if (desktopLyricEnabled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-2).dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(barAccentSecondary)
                        )
                    }
                }
            }

            PlayerActionIconButton(
                onClick = onOpenAudioEffect
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PlumpAudioWaveCanvasIcon(
                        color = if (viperEnabled) barAccentPrimary else Color.White.copy(alpha = 0.88f),
                        accentColor = if (viperEnabled) barAccentSecondary else barAccentPrimary
                    )
                    if (viperEnabled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-2).dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(barAccentSecondary)
                        )
                    }
                }
            }

            RedesignedCommentCountButton(
                commentCount = commentCount,
                accentColor = barAccentPrimary,
                onClick = onOpenComments
            )

            PlayerActionIconButton(
                onClick = onOpenMoreSheet
            ) {
                PlumpMoreDotsCanvasIcon(
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. 现代自然光学彗星拖尾进度条 + 圆润饱满五大播放控件
        PlayerControlsSection(
            currentPositionMs = currentPositionMs,
            positionProvider = rawPositionProvider,
            durationMs = durationMs,
            climaxStartMs = climaxStartMs,
            climaxEndMs = climaxEndMs,
            highlightClimaxRange = highlightClimaxRange,
            isPlaying = isPlaying,
            playMode = playMode,
            accentPrimary = accentPrimary,
            accentSecondary = accentSecondary,
            onSeekTo = onSeekTo,
            onScrubPreviewMsChange = { scrubPreviewPositionMs = it },
            onTogglePlay = onTogglePlay,
            onPrevious = onPrevious,
            onNext = onNext,
            onCyclePlayMode = onCyclePlayMode,
            onQueueClick = onOpenQueue
        )

        Spacer(modifier = Modifier.height(2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullWidthLyricPreviewSection(
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    isPlaying: Boolean = true,
    positionProvider: () -> Long = { currentPositionMs },
    showTranslation: Boolean,
    showPhonetic: Boolean,
    lyricWordByWord: Boolean = true,
    lyricHighlightTheme: LyricHighlightTheme = LyricHighlightTheme.ICE_BLUE,
    accentPrimary: Color,
    accentSecondary: Color = ConceptCyanAccent,
    onLyricClick: () -> Unit
) {
    var sampledPositionMs by remember { mutableLongStateOf(positionProvider()) }
    LaunchedEffect(currentPositionMs, currentLyricIndex) {
        sampledPositionMs = positionProvider()
    }
    val isLifecycleStarted = rememberIsLifecycleStarted()
    val currentLineHasWordTiming = lyricData.lines.getOrNull(currentLyricIndex)?.hasWordTiming == true
    val hasInterludeCandidate = remember(lyricData, currentLyricIndex) {
        lyricData.hasInterludeCandidateAt(currentLyricIndex)
    }
    LaunchedEffect(isPlaying, isLifecycleStarted, currentLyricIndex, lyricWordByWord, currentLineHasWordTiming, hasInterludeCandidate, positionProvider) {
        if (isPlaying && isLifecycleStarted && ((lyricWordByWord && currentLineHasWordTiming) || hasInterludeCandidate)) {
            while (isActive) {
                withFrameMillis {
                    sampledPositionMs = positionProvider()
                }
            }
        } else {
            sampledPositionMs = positionProvider()
        }
    }
    val livePositionMs = sampledPositionMs
    val density = LocalDensity.current
    val activePrimaryColor = if (lyricHighlightTheme == LyricHighlightTheme.DYNAMIC_COVER) {
        accentPrimary.readableOnDarkSurface(minLuminance = 0.46f)
    } else {
        Color(lyricHighlightTheme.primaryHex)
    }
    val activeSecondaryColor = if (lyricHighlightTheme == LyricHighlightTheme.DYNAMIC_COVER) {
        accentSecondary.readableOnDarkSurface(minLuminance = 0.50f)
    } else {
        Color(lyricHighlightTheme.secondaryHex)
    }

    val currentLineObj = lyricData.lines.getOrNull(currentLyricIndex)
    val mainText = currentLineObj?.text?.takeIf { it.isNotBlank() } ?: "轻触展开全屏歌词"
    val phoneticText = if (showPhonetic) currentLineObj?.phonetic?.takeIf { it.isNotBlank() } else null
    val subText = if (showTranslation && !currentLineObj?.translation.isNullOrBlank()) {
        currentLineObj?.translation.orEmpty()
    } else {
        lyricData.lines.getOrNull(currentLyricIndex + 1)?.text.orEmpty()
    }

    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    val mainFontSp = if (isLargeScreen) 23.5.sp else 19.5.sp
    val mainLineHeightSp = if (isLargeScreen) 32.sp else 26.5.sp
    val singleLinePx = with(density) { mainLineHeightSp.toPx() }.coerceAtLeast(1f)
    val threeLineViewportPx = singleLinePx * 3.05f
    var measuredMainHeightPx by remember(currentLyricIndex, mainText) { mutableIntStateOf(0) }
    val wordTopYMap = remember(currentLyricIndex, mainText) { mutableStateMapOf<Int, Float>() }

    val actualLineCount = remember(measuredMainHeightPx, singleLinePx) {
        if (measuredMainHeightPx <= 0) 1
        else (measuredMainHeightPx.toFloat() / singleLinePx).roundToInt().coerceAtLeast(1)
    }
    // 当主歌词行数 >= 3 行时（超长歌词），三行视口全部用于渲染当前句并开启混动滚动，不显示下一句或翻译
    val isSuperLongLyric = actualLineCount >= 3

    val maxScrollPx = (measuredMainHeightPx.toFloat() - threeLineViewportPx).coerceAtLeast(0f)
    val targetScrollPx = if (!isSuperLongLyric || maxScrollPx <= 1f || currentLineObj == null) {
        0f
    } else if (currentLineObj.hasWordTiming && currentLineObj.words.isNotEmpty() && lyricWordByWord) {
        val activeWordIdx = currentLineObj.words.indexOfLast { livePositionMs >= it.startTimeMs }
            .coerceAtLeast(0)
        val activeWordY = wordTopYMap[activeWordIdx] ?: 0f
        // 当演唱推进到第 2 行往后时，平滑向上混动滚动，使当前演唱行保持在三行视口中央区域
        if (activeWordY <= singleLinePx * 0.85f) {
            0f
        } else {
            (activeWordY - singleLinePx).coerceIn(0f, maxScrollPx)
        }
    } else {
        val nextStartMs = lyricData.lines.getOrNull(currentLyricIndex + 1)?.startTimeMs
            ?: (currentLineObj.startTimeMs + 5000L)
        val lineDurationMs = (currentLineObj.durationMs.takeIf { it > 0L }
            ?: (nextStartMs - currentLineObj.startTimeMs)).coerceAtLeast(2500L)
        val lineProgress = ((livePositionMs - currentLineObj.startTimeMs).toFloat() / lineDurationMs.toFloat())
            .coerceIn(0f, 1f)
        val scrollFraction = ((lineProgress - 0.20f) / 0.68f).coerceIn(0f, 1f)
        (scrollFraction * maxScrollPx).coerceIn(0f, maxScrollPx)
    }

    val animatedScrollY by animateFloatAsState(
        targetValue = if (isSuperLongLyric) targetScrollPx else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "superLongLyricScrollY"
    )

    val lineEnterAnim = remember { Animatable(1f) }
    LaunchedEffect(currentLyricIndex, mainText) {
        lineEnterAnim.snapTo(0f)
        lineEnterAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    // 固定 92.dp 三行总高度容器（顶部对齐歌词海报按钮正下方区域），绝不随歌词长短被动抬高上方封面与右侧三按钮
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isLargeScreen) 114.dp else 92.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onLyricClick
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .graphicsLayer {
                val p = lineEnterAnim.value
                translationY = (1f - p) * 12f
                alpha = (0.32f + 0.68f * p).coerceIn(0f, 1f)
            },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        if (!phoneticText.isNullOrBlank() && actualLineCount == 1) {
            Text(
                text = phoneticText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = activePrimaryColor.copy(alpha = 0.88f),
                    fontSize = if (isLargeScreen) 15.sp else 12.5.sp,
                    lineHeight = if (isLargeScreen) 19.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    val unboundedConstraints = constraints.copy(
                        minHeight = 0,
                        maxHeight = Constraints.Infinity
                    )
                    val placeable = measurable.measure(unboundedConstraints)
                    if (measuredMainHeightPx != placeable.height) {
                        measuredMainHeightPx = placeable.height
                    }
                    val maxViewportHeightPx = threeLineViewportPx.roundToInt()
                    val viewportHeight = placeable.height.coerceAtMost(maxViewportHeightPx)
                    layout(placeable.width, viewportHeight) {
                        val yOffset = if (placeable.height > maxViewportHeightPx) {
                            -animatedScrollY.roundToInt()
                        } else {
                            0
                        }
                        placeable.placeRelative(0, yOffset)
                    }
                }
        ) {
            if (currentLineObj != null && currentLineObj.hasWordTiming && currentLineObj.words.isNotEmpty() && lyricWordByWord) {
                FlowRow(
                    horizontalArrangement = Arrangement.Start,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    currentLineObj.words.forEachIndexed { wordIndex, word ->
                        val wordProgress = when {
                            word.durationMs > 0L -> ((livePositionMs - word.startTimeMs).toFloat() / word.durationMs.toFloat()).coerceIn(0f, 1f)
                            livePositionMs >= word.startTimeMs -> 1f
                            else -> 0f
                        }
                        val isSinging = wordProgress in 0.01f..0.99f
                        val dimColor = Color.White.copy(alpha = 0.44f)

                        val annotatedWord = buildAnnotatedString {
                            if (wordProgress <= 0f) {
                                withStyle(SpanStyle(color = dimColor)) {
                                    append(word.text)
                                }
                            } else if (wordProgress >= 1f) {
                                withStyle(SpanStyle(color = activePrimaryColor)) {
                                    append(word.text)
                                }
                            } else {
                                val leftStop = (wordProgress - 0.14f).coerceAtLeast(0f)
                                val rightStop = (wordProgress + 0.14f).coerceAtMost(1f)
                                withStyle(
                                    SpanStyle(
                                        brush = Brush.horizontalGradient(
                                            0.0f to activePrimaryColor,
                                            leftStop to activeSecondaryColor,
                                            rightStop to dimColor,
                                            1.0f to dimColor
                                        )
                                    )
                                ) {
                                    append(word.text)
                                }
                            }
                        }

                        val wave = if (isSinging) sin(wordProgress * PI.toFloat()) else 0f
                        val isLongNote = word.durationMs >= 700L && isSinging
                        val shimmer = if (isLongNote) 0.86f + 0.14f * sin(wordProgress * PI.toFloat() * 3f) else 1f

                        Text(
                            text = annotatedWord,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = mainFontSp,
                                lineHeight = mainLineHeightSp,
                                shadow = if (isLongNote) {
                                    Shadow(
                                        color = activePrimaryColor.copy(alpha = (0.82f * wave * shimmer).coerceIn(0f, 1f)),
                                        offset = Offset.Zero,
                                        blurRadius = (18f * wave * shimmer + 5f).coerceAtLeast(1f)
                                    )
                                } else null
                            ),
                            modifier = Modifier
                                .onGloballyPositioned { coords ->
                                    wordTopYMap[wordIndex] = coords.positionInParent().y
                                }
                                .graphicsLayer {
                                    translationY = -2.8f * wave * shimmer
                                    val s = 1f + 0.034f * wave * shimmer
                                    scaleX = s
                                    scaleY = s
                                }
                        )
                    }
                }
            } else {
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (currentLineObj != null) activePrimaryColor else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = mainFontSp,
                        lineHeight = mainLineHeightSp
                    ),
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 仅当主歌词为 1 行或 2 行时，在下方显示小一号的下一句歌词（或外文歌翻译）；若处于前奏/间奏，同步展示无框三点流光呼吸
        if (!isSuperLongLyric) {
            Spacer(modifier = Modifier.height(4.dp))

            val interludeProgress = lyricData.getInterludeProgressAt(livePositionMs, currentLyricIndex)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (interludeProgress >= 0f) {
                    LyricFloatingBreathingDots(
                        progress = interludeProgress,
                        isPlaying = isPlaying,
                        dotFontSizeSp = 15f,
                        activePrimaryColor = activePrimaryColor,
                        activeSecondaryColor = activeSecondaryColor
                    )
                }
                if (subText.isNotBlank() || interludeProgress < 0f) {
                    Text(
                        text = subText.ifBlank { "♪ • • •" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = if (isLargeScreen) 18.sp else 15.sp,
                            lineHeight = if (isLargeScreen) 24.sp else 21.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControlsSection(
    currentPositionMs: Long,
    positionProvider: () -> Long = { currentPositionMs },
    durationMs: Long,
    climaxStartMs: Long = 0L,
    climaxEndMs: Long = 0L,
    highlightClimaxRange: Boolean = false,
    isPlaying: Boolean,
    playMode: PlayMode,
    accentPrimary: Color = ConceptPrimary,
    accentSecondary: Color = ConceptCyanAccent,
    onSeekTo: (Long) -> Unit,
    onScrubPreviewMsChange: ((Long?) -> Unit)? = null,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onQueueClick: () -> Unit
) {
    val context = LocalContext.current
    var isInteractingWithBar by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestOnSeekTo by rememberUpdatedState(onSeekTo)
    val latestOnScrubPreviewMsChange by rememberUpdatedState(onScrubPreviewMsChange)
    var settledSeekTargetMs by remember { mutableLongStateOf(-1L) }
    var settledSeekTimestampMs by remember { mutableLongStateOf(0L) }

    fun sampleWithSeekHold(): Long {
        val rawPos = positionProvider()
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        return if (settledSeekTargetMs >= 0L && nowElapsed - settledSeekTimestampMs in 0L..550L) {
            if (kotlin.math.abs(rawPos - settledSeekTargetMs) <= 800L) {
                settledSeekTargetMs = -1L
                rawPos
            } else {
                settledSeekTargetMs
            }
        } else {
            settledSeekTargetMs = -1L
            rawPos
        }
    }

    var sampledPositionMs by remember { mutableLongStateOf(sampleWithSeekHold()) }

    LaunchedEffect(currentPositionMs) {
        if (!isInteractingWithBar) {
            sampledPositionMs = sampleWithSeekHold()
        }
    }
    val isLifecycleStarted = rememberIsLifecycleStarted()
    // 消除每 8ms 重建 animateFloatAsState 协程的 120Hz 全树重组风暴，仅在前台按 66ms 节流采样驱动平滑进度
    LaunchedEffect(isPlaying, isLifecycleStarted, isInteractingWithBar, positionProvider) {
        if (isPlaying && isLifecycleStarted && !isInteractingWithBar) {
            while (isActive) {
                sampledPositionMs = sampleWithSeekHold()
                kotlinx.coroutines.delay(66L)
            }
        } else if (!isInteractingWithBar) {
            sampledPositionMs = sampleWithSeekHold()
        }
    }

    val livePositionMs = sampledPositionMs
    val currentFraction = if (durationMs > 0) {
        (livePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val effectiveFraction = if (isInteractingWithBar) dragFraction else currentFraction
    val displayCurrentMs = if (isInteractingWithBar) (dragFraction * durationMs).toLong() else livePositionMs

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CometTailProgressBar(
            fraction = effectiveFraction,
            isPlaying = isPlaying,
            isInteracting = isInteractingWithBar,
            currentTimeText = formatDuration(displayCurrentMs),
            totalTimeText = formatDuration(durationMs),
            climaxStartFrac = if (durationMs > 0L && climaxEndMs > climaxStartMs) {
                (climaxStartMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f,
            climaxEndFrac = if (durationMs > 0L && climaxEndMs > climaxStartMs) {
                (climaxEndMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f,
            highlightClimaxRange = highlightClimaxRange,
            accentPrimary = accentPrimary,
            accentSecondary = accentSecondary,
            onDragStart = { startFrac ->
                val clampedFrac = startFrac.coerceIn(0f, 1f)
                isInteractingWithBar = true
                dragFraction = clampedFrac
                latestOnScrubPreviewMsChange?.invoke((clampedFrac * latestDurationMs).toLong())
            },
            onDrag = { newFrac ->
                val clampedFrac = newFrac.coerceIn(0f, 1f)
                dragFraction = clampedFrac
                latestOnScrubPreviewMsChange?.invoke((clampedFrac * latestDurationMs).toLong())
            },
            onDragEnd = {
                val targetMs = (dragFraction * latestDurationMs).toLong().coerceIn(0L, latestDurationMs.coerceAtLeast(0L))
                settledSeekTargetMs = targetMs
                settledSeekTimestampMs = android.os.SystemClock.elapsedRealtime()
                sampledPositionMs = targetMs
                latestOnSeekTo(targetMs)
                isInteractingWithBar = false
                latestOnScrubPreviewMsChange?.invoke(null)
            },
            onDragCancel = {
                isInteractingWithBar = false
                latestOnScrubPreviewMsChange?.invoke(null)
            }
        )

        val readableControlAccent = remember(accentPrimary) { accentPrimary.readableOnDarkSurface(0.45f) }
        val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(displayCurrentMs),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isInteractingWithBar) readableControlAccent else Color.White.copy(alpha = 0.60f),
                    fontSize = if (isLargeScreen) 13.5.sp else 11.5.sp,
                    fontWeight = if (isInteractingWithBar) FontWeight.Bold else FontWeight.Medium
                )
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = if (isLargeScreen) 13.5.sp else 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Spacer(modifier = Modifier.height(if (isLargeScreen) 14.dp else 10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedPlayModeButton(
                playMode = playMode,
                accentColor = readableControlAccent,
                onClick = {
                    onCyclePlayMode()
                    val nextDesc = when (playMode) {
                        PlayMode.REPEAT_ALL -> "已切换为单曲循环"
                        PlayMode.REPEAT_ONE -> "已切换为随机播放"
                        PlayMode.SHUFFLE -> "已切换为列表循环"
                    }
                    Toast.makeText(context, nextDesc, Toast.LENGTH_SHORT).show()
                }
            )

            RedrawnSkipButton(
                isNext = false,
                onClick = onPrevious
            )

            AnimatedPlayPauseCenterButton(
                isPlaying = isPlaying,
                accentPrimary = accentPrimary,
                accentSecondary = accentSecondary,
                onClick = onTogglePlay
            )

            RedrawnSkipButton(
                isNext = true,
                onClick = onNext
            )

            RedrawnQueueButton(
                accentColor = readableControlAccent,
                onClick = onQueueClick
            )
        }
    }
}

internal data class CoverExtractedPalette(
    val meshPrimary: Color,
    val meshSecondary: Color,
    val meshTertiary: Color = Color(0xFF191B38),
    val dominantAccent: Color,
    val secondaryAccent: Color,
    val tertiaryAccent: Color = Color(0xFF8B7CFF)
)

internal val coverPaletteMemoryCache: MutableMap<String, CoverExtractedPalette> =
    java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, CoverExtractedPalette>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoverExtractedPalette>?): Boolean {
                return size > 64
            }
        }
    )

internal suspend fun extractCoverMeshColors(
    context: Context,
    coverUrl: String
): CoverExtractedPalette? {
    if (coverUrl.isBlank()) return null
    coverPaletteMemoryCache[coverUrl]?.let { return it }
    return try {
        val request = ImageRequest.Builder(context)
            .data(coverUrl)
            .size(48, 48)
            .precision(coil.size.Precision.EXACT)
            .memoryCacheKey("${coverUrl}#palette_48")
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            ?: return null

        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        var r1 = 0L; var g1 = 0L; var b1 = 0L; var count1 = 0L
        var r2 = 0L; var g2 = 0L; var b2 = 0L; var count2 = 0L
        var r3 = 0L; var g3 = 0L; var b3 = 0L; var count3 = 0L

        // 24-bin hue histogram for 3-color vivid accent extraction
        val hueWeights = FloatArray(24)
        val hueSatSum = FloatArray(24)
        val hueValSum = FloatArray(24)
        val hsv = FloatArray(3)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                when {
                    x + y < (width + height) / 3 -> {
                        r1 += r; g1 += g; b1 += b; count1++
                    }
                    x + y < ((width + height) * 2) / 3 -> {
                        r2 += r; g2 += g; b2 += b; count2++
                    }
                    else -> {
                        r3 += r; g3 += g; b3 += b; count3++
                    }
                }

                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val v = hsv[2]
                if (sat >= 0.16f && v in 0.18f..0.97f) {
                    val bin = ((hue / 15f).toInt()).coerceIn(0, 23)
                    val centerBias = 1f - 0.28f * (abs(x - width * 0.5f) / (width * 0.5f).coerceAtLeast(1f))
                    val weight = (sat * sat + 0.12f) * (v * 0.85f + 0.15f) * centerBias
                    hueWeights[bin] += weight
                    hueSatSum[bin] += sat * weight
                    hueValSum[bin] += v * weight
                }
            }
        }

        fun normalizeForDarkMesh(rAvg: Int, gAvg: Int, bAvg: Int, hueShift: Float): Color {
            val tmpHsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(rAvg, gAvg, bAvg, tmpHsv)
            tmpHsv[0] = (tmpHsv[0] + hueShift + 360f) % 360f
            tmpHsv[1] = tmpHsv[1].coerceIn(0.38f, 0.74f)
            tmpHsv[2] = tmpHsv[2].coerceIn(0.15f, 0.28f)
            return Color(android.graphics.Color.HSVToColor(tmpHsv))
        }

        val c1 = if (count1 > 0) {
            normalizeForDarkMesh((r1 / count1).toInt(), (g1 / count1).toInt(), (b1 / count1).toInt(), 0f)
        } else Color(0xFF1A2C38)

        val c2 = if (count2 > 0) {
            normalizeForDarkMesh((r2 / count2).toInt(), (g2 / count2).toInt(), (b2 / count2).toInt(), 24f)
        } else Color(0xFF1B1932)

        val c3 = if (count3 > 0) {
            normalizeForDarkMesh((r3 / count3).toInt(), (g3 / count3).toInt(), (b3 / count3).toInt(), -28f)
        } else Color(0xFF162238)

        var bestBin = -1
        var bestWeight = 0f
        for (i in 0 until 24) {
            if (hueWeights[i] > bestWeight) {
                bestWeight = hueWeights[i]
                bestBin = i
            }
        }

        val dominantAccent: Color
        val secondaryAccent: Color
        val tertiaryAccent: Color
        if (bestBin != -1) {
            val domHue = (bestBin * 15f + 7.5f) % 360f
            val domSat = (hueSatSum[bestBin] / hueWeights[bestBin]).coerceIn(0.58f, 0.88f)
            val domVal = (hueValSum[bestBin] / hueWeights[bestBin]).coerceIn(0.82f, 0.96f)
            dominantAccent = Color(android.graphics.Color.HSVToColor(floatArrayOf(domHue, domSat, domVal)))

            var secondBin = -1
            var secondWeight = 0f
            for (i in 0 until 24) {
                val dist = min(abs(i - bestBin), 24 - abs(i - bestBin))
                if (dist >= 2 && hueWeights[i] > secondWeight) {
                    secondWeight = hueWeights[i]
                    secondBin = i
                }
            }
            val secHue = if (secondBin != -1 && secondWeight > bestWeight * 0.16f) {
                (secondBin * 15f + 7.5f) % 360f
            } else {
                (domHue + 36f) % 360f
            }
            val secSat = if (secondBin != -1 && hueWeights[secondBin] > 0f) {
                (hueSatSum[secondBin] / hueWeights[secondBin]).coerceIn(0.55f, 0.90f)
            } else (domSat * 0.95f).coerceIn(0.55f, 0.90f)
            secondaryAccent = Color(android.graphics.Color.HSVToColor(floatArrayOf(secHue, secSat, 0.95f)))

            var thirdBin = -1
            var thirdWeight = 0f
            for (i in 0 until 24) {
                val dist1 = min(abs(i - bestBin), 24 - abs(i - bestBin))
                val dist2 = if (secondBin != -1) min(abs(i - secondBin), 24 - abs(i - secondBin)) else 12
                if (dist1 >= 2 && dist2 >= 2 && hueWeights[i] > thirdWeight) {
                    thirdWeight = hueWeights[i]
                    thirdBin = i
                }
            }
            val terHue = if (thirdBin != -1 && thirdWeight > bestWeight * 0.10f) {
                (thirdBin * 15f + 7.5f) % 360f
            } else {
                (domHue + 288f) % 360f
            }
            val terSat = if (thirdBin != -1 && hueWeights[thirdBin] > 0f) {
                (hueSatSum[thirdBin] / hueWeights[thirdBin]).coerceIn(0.54f, 0.88f)
            } else (domSat * 0.92f).coerceIn(0.54f, 0.88f)
            tertiaryAccent = Color(android.graphics.Color.HSVToColor(floatArrayOf(terHue, terSat, 0.94f)))
        } else {
            dominantAccent = ConceptPrimary
            secondaryAccent = ConceptCyanAccent
            tertiaryAccent = ConceptVioletAccent
        }

        CoverExtractedPalette(
            meshPrimary = c1,
            meshSecondary = c2,
            meshTertiary = c3,
            dominantAccent = dominantAccent,
            secondaryAccent = secondaryAccent,
            tertiaryAccent = tertiaryAccent
        ).also { palette ->
            coverPaletteMemoryCache[coverUrl] = palette
        }
    } catch (_: Exception) {
        null
    }
}

