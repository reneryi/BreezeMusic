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

private object SoftCoverDiffusionBlurTransformation : coil.transform.Transformation {
    override val cacheKey: String = "soft_cover_diffusion_blur_v3"

    override suspend fun transform(
        input: android.graphics.Bitmap,
        size: coil.size.Size
    ): android.graphics.Bitmap {
        val targetW = 96
        val targetH = 96
        val scaled = android.graphics.Bitmap.createScaledBitmap(input, targetW, targetH, true)
        val working = scaled.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        if (scaled !== input && scaled !== working) {
            scaled.recycle()
        }

        val w = working.width
        val h = working.height
        val pixels = IntArray(w * h)
        val temp = IntArray(w * h)
        working.getPixels(pixels, 0, w, 0, 0, w, h)

        // 先对像素做色彩柔化与高光压制，彻底消除封面上的文字、锐利线条与刺眼白斑，转化为温润的弥散色块
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            if (hsv[1] > 0.08f) {
                hsv[1] = (hsv[1] * 1.18f).coerceIn(0.28f, 0.82f)
            }
            hsv[2] = (hsv[2] * 0.72f + 0.08f).coerceIn(0.14f, 0.66f)
            pixels[i] = android.graphics.Color.HSVToColor(0xFF, hsv)
        }

        // 3 遍大半径双向均值卷积（等效高斯模糊 sigma ≈ 18），保证任何歌曲切歌或首次进入都具备 100% 均匀丝滑的弥散模糊
        val radius = 16
        val div = 2 * radius + 1
        repeat(3) {
            // Horizontal pass
            for (y in 0 until h) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                val rowOffset = y * w
                for (k in -radius..radius) {
                    val x = k.coerceIn(0, w - 1)
                    val p = pixels[rowOffset + x]
                    rSum += (p shr 16) and 0xFF
                    gSum += (p shr 8) and 0xFF
                    bSum += p and 0xFF
                }
                for (x in 0 until w) {
                    temp[rowOffset + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                    val leftX = (x - radius).coerceIn(0, w - 1)
                    val rightX = (x + radius + 1).coerceIn(0, w - 1)
                    val pOut = pixels[rowOffset + leftX]
                    val pIn = pixels[rowOffset + rightX]
                    rSum += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                    gSum += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                    bSum += (pIn and 0xFF) - (pOut and 0xFF)
                }
            }
            // Vertical pass
            for (x in 0 until w) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (k in -radius..radius) {
                    val y = k.coerceIn(0, h - 1)
                    val p = temp[y * w + x]
                    rSum += (p shr 16) and 0xFF
                    gSum += (p shr 8) and 0xFF
                    bSum += p and 0xFF
                }
                for (y in 0 until h) {
                    pixels[y * w + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                    val topY = (y - radius).coerceIn(0, h - 1)
                    val botY = (y + radius + 1).coerceIn(0, h - 1)
                    val pOut = temp[topY * w + x]
                    val pIn = temp[botY * w + x]
                    rSum += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                    gSum += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                    bSum += (pIn and 0xFF) - (pOut and 0xFF)
                }
            }
        }

        working.setPixels(pixels, 0, w, 0, 0, w, h)
        return working
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
    val infiniteTransition = rememberInfiniteTransition(label = "bgModeAnim")
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bgPhase"
    )
    val animatedSecondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bgSecPhase"
    )
    val animatedPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bgPulse"
    )

    val phase = if (isPlaying) animatedPhase else 0.85f
    val secPhase = if (isPlaying) animatedSecondaryPhase else 1.45f
    val pulse = if (isPlaying) animatedPulse else 0.96f

    // 律动光晕模式专用的 60fps 实时音频能量追踪状态（直接读取 ConceptMasteringAudioProcessor 实时计算的低/中/高频与鼓点脉冲）
    var liveBassEnergy by remember { mutableFloatStateOf(0.18f) }
    var liveMidEnergy by remember { mutableFloatStateOf(0.16f) }
    var liveHighEnergy by remember { mutableFloatStateOf(0.14f) }
    var liveBeatImpulse by remember { mutableFloatStateOf(0f) }
    var liveWaveTime by remember { mutableFloatStateOf(0f) }
    val liveSpectrumBins = remember { FloatArray(10) { 0.15f } }

    LaunchedEffect(backgroundStyle, isPlaying) {
        if (backgroundStyle == PlayerBackgroundStyle.RHYTHM_HALO && isPlaying) {
            var lastNanos = 0L
            while (isActive) {
                withFrameNanos { nanos ->
                    val dt = if (lastNanos == 0L) 0.016f else ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
                    lastNanos = nanos
                    val telemetryActive = com.concept.music.service.AudioRhythmTelemetry.isActive()
                    val targetBass = if (telemetryActive) {
                        com.concept.music.service.AudioRhythmTelemetry.bassEnergy
                    } else {
                        0.28f + 0.16f * abs(sin(liveWaveTime * 2.4f))
                    }
                    val targetMid = if (telemetryActive) {
                        com.concept.music.service.AudioRhythmTelemetry.midEnergy
                    } else {
                        0.24f + 0.14f * abs(cos(liveWaveTime * 1.9f + 0.8f))
                    }
                    val targetHigh = if (telemetryActive) {
                        com.concept.music.service.AudioRhythmTelemetry.highEnergy
                    } else {
                        0.20f + 0.12f * abs(sin(liveWaveTime * 3.1f + 1.7f))
                    }
                    val targetBeat = if (telemetryActive) {
                        com.concept.music.service.AudioRhythmTelemetry.beatImpulse
                    } else {
                        0.15f * abs(sin(liveWaveTime * 2.8f)).pow(3)
                    }

                    liveBassEnergy += (targetBass - liveBassEnergy) * (if (targetBass > liveBassEnergy) 0.36f else 0.14f)
                    liveMidEnergy += (targetMid - liveMidEnergy) * (if (targetMid > liveMidEnergy) 0.32f else 0.15f)
                    liveHighEnergy += (targetHigh - liveHighEnergy) * (if (targetHigh > liveHighEnergy) 0.38f else 0.18f)
                    liveBeatImpulse += (targetBeat - liveBeatImpulse) * (if (targetBeat > liveBeatImpulse) 0.48f else 0.16f)

                    for (b in 0 until 10) {
                        val rawBin = if (telemetryActive) {
                            com.concept.music.service.AudioRhythmTelemetry.readBin(b)
                        } else {
                            0.18f + 0.12f * abs(sin(liveWaveTime * (1.5f + b * 0.22f) + b))
                        }
                        liveSpectrumBins[b] += (rawBin - liveSpectrumBins[b]) * 0.28f
                    }

                    val speedFactor = 1.0f + liveBassEnergy * 1.8f + liveBeatImpulse * 2.2f
                    liveWaveTime = (liveWaveTime + dt * speedFactor) % 628.318f
                }
            }
        } else {
            liveBassEnergy = 0.12f
            liveMidEnergy = 0.10f
            liveHighEnergy = 0.08f
            liveBeatImpulse = 0f
        }
    }

    // 采用 AnimatedContent 包裹五种背景预设，彻底消除切歌或切换预设时的图层残留与光晕叠加 BUG
    AnimatedContent(
        targetState = backgroundStyle,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing))
                .togetherWith(fadeOut(animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing)))
        },
        modifier = Modifier.fillMaxSize(),
        label = "NowPlayingBackgroundStyleTransition"
    ) { activeStyle ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeStyle) {
                PlayerBackgroundStyle.COVER_BLUR -> {
                    // 预设 1：封面虚化（独立缓存 Key + 三遍堆栈高斯弥散 + 柔和三色呼吸色彩晕染，彻底解决切歌光晕突变与原图未模糊 BUG）
                    androidx.compose.animation.Crossfade(
                        targetState = coverUrl,
                        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
                        modifier = Modifier.fillMaxSize(),
                        label = "CoverBlurCrossfade"
                    ) { currentUrl ->
                        if (currentUrl.isNotEmpty()) {
                            val bgImageModel = remember(currentUrl) {
                                ImageRequest.Builder(context)
                                    .data(currentUrl)
                                    .size(96, 96)
                                    .precision(coil.size.Precision.EXACT)
                                    .memoryCacheKey("${currentUrl}#cover_blur_diffusion_v3")
                                    .transformations(SoftCoverDiffusionBlurTransformation)
                                    .allowHardware(false)
                                    .crossfade(false)
                                    .build()
                            }
                            AsyncImage(
                                model = bgImageModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.Medium,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        // 缓慢呼吸微缩放以避免边缘裁切并赋予柔和生命力
                                        val s = 1.08f + 0.03f * sin(phase)
                                        scaleX = s
                                        scaleY = s
                                    }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0A101D))
                            )
                        }
                    }

                    // 柔和三色流光弥散叠加层（按实际屏幕宽高比例自适应分布，消除硬编码像素坐标偏移）
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val minDim = minOf(w, h).coerceAtLeast(1f)
                        val maxDim = maxOf(w, h).coerceAtLeast(1f)

                        // 基础丝绒暗色底衬（平衡封面明暗对比，确保前景白色歌词与控件极佳可读性）
                        val baseOverlayAlpha = if (showLyricView) {
                            (dimAlpha * 0.78f).coerceIn(0.46f, 0.84f)
                        } else {
                            0.52f
                        }
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF070B14).copy(alpha = (baseOverlayAlpha - 0.06f).coerceAtLeast(0.36f)),
                                    Color(0xFF0A111E).copy(alpha = baseOverlayAlpha),
                                    Color(0xFF050810).copy(alpha = (baseOverlayAlpha + 0.14f).coerceAtMost(0.92f))
                                )
                            )
                        )

                        // 三色柔和色彩弥散云团 (Soft Color Diffusion Blooms)
                        val bloomAlphaScale = if (showLyricView) 0.72f else 1.0f
                        val c1Center = Offset(
                            x = w * (0.24f + 0.08f * cos(phase)),
                            y = h * (0.26f + 0.06f * sin(phase))
                        )
                        val c2Center = Offset(
                            x = w * (0.76f + 0.08f * sin(phase * 0.85f + 1.2f)),
                            y = h * (0.68f + 0.07f * cos(phase * 0.85f + 0.9f))
                        )
                        val c3Center = Offset(
                            x = w * (0.48f + 0.10f * sin(secPhase + 2.1f)),
                            y = h * (0.44f + 0.06f * cos(secPhase + 1.5f))
                        )

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentPrimary.copy(alpha = 0.26f * bloomAlphaScale),
                                    meshColorPrimary.copy(alpha = 0.34f * bloomAlphaScale),
                                    Color.Transparent
                                ),
                                center = c1Center,
                                radius = maxDim * 0.68f
                            ),
                            center = c1Center,
                            radius = maxDim * 0.68f
                        )

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentSecondary.copy(alpha = 0.22f * bloomAlphaScale),
                                    meshColorSecondary.copy(alpha = 0.30f * bloomAlphaScale),
                                    Color.Transparent
                                ),
                                center = c2Center,
                                radius = maxDim * 0.72f
                            ),
                            center = c2Center,
                            radius = maxDim * 0.72f
                        )

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentTertiary.copy(alpha = 0.18f * bloomAlphaScale),
                                    meshColorTertiary.copy(alpha = 0.24f * bloomAlphaScale),
                                    Color.Transparent
                                ),
                                center = c3Center,
                                radius = minDim * 0.82f
                            ),
                            center = c3Center,
                            radius = minDim * 0.82f
                        )
                    }
                }

                PlayerBackgroundStyle.FLUID_AURORA -> {
                    // 预设 2：流体极光（完全移除封面残影，提取封面主色/副色/第三色共 3 种色彩，采用多频李萨如轨迹与贝塞尔极光绸带丝滑流动）
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val maxDim = maxOf(w, h).coerceAtLeast(1f)

                        // 纯净深邃极夜天幕底色（100% 不渲染任何专辑封面纹理）
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF050812),
                                    meshColorPrimary.copy(alpha = 0.88f),
                                    meshColorSecondary.copy(alpha = 0.82f),
                                    Color(0xFF04070E)
                                )
                            )
                        )

                        // 第一层：三色李萨如流体光晕团 (Lissajous Fluid Color Orbs)
                        val orb1 = Offset(
                            w * (0.24f + 0.22f * cos(phase) + 0.06f * sin(secPhase)),
                            h * (0.25f + 0.15f * sin(phase * 1.2f))
                        )
                        val orb2 = Offset(
                            w * (0.76f - 0.20f * sin(phase * 0.9f + 1.1f)),
                            h * (0.48f + 0.18f * cos(secPhase * 0.8f + 0.6f))
                        )
                        val orb3 = Offset(
                            w * (0.45f + 0.24f * sin(secPhase + 2.2f)),
                            h * (0.76f - 0.14f * cos(phase + 1.7f))
                        )
                        val orb4 = Offset(
                            w * (0.62f + 0.18f * cos(phase + 3.1f)),
                            h * (0.22f + 0.14f * sin(secPhase + 2.8f))
                        )

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentPrimary.copy(alpha = 0.44f),
                                    accentPrimary.copy(alpha = 0.16f),
                                    Color.Transparent
                                ),
                                center = orb1,
                                radius = maxDim * 0.62f * pulse
                            ),
                            center = orb1,
                            radius = maxDim * 0.62f * pulse
                        )

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentSecondary.copy(alpha = 0.40f),
                                    accentSecondary.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                center = orb2,
                                radius = maxDim * 0.60f * (2.02f - pulse)
                            ),
                            center = orb2,
                            radius = maxDim * 0.60f * (2.02f - pulse)
                        )

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentTertiary.copy(alpha = 0.38f),
                                    meshColorTertiary.copy(alpha = 0.22f),
                                    Color.Transparent
                                ),
                                center = orb3,
                                radius = maxDim * 0.66f * pulse
                            ),
                            center = orb3,
                            radius = maxDim * 0.66f * pulse
                        )

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentSecondary.copy(alpha = 0.24f),
                                    accentTertiary.copy(alpha = 0.12f),
                                    Color.Transparent
                                ),
                                center = orb4,
                                radius = maxDim * 0.50f
                            ),
                            center = orb4,
                            radius = maxDim * 0.50f
                        )

                        // 第二层：三色流体极光丝带 (Bezier Aurora Curtains)
                        for (band in 0 until 3) {
                            val bandOffset = band * 1.9f
                            val yBase = h * (0.28f + band * 0.21f)
                            val waveAmp = h * (0.085f + band * 0.018f)
                            val y0 = yBase + waveAmp * sin(phase + bandOffset)
                            val y1 = yBase + waveAmp * cos(secPhase + bandOffset + 0.9f)
                            val y2 = yBase - waveAmp * sin(phase * 1.2f + bandOffset + 1.8f)
                            val y3 = yBase + waveAmp * cos(phase + secPhase * 0.5f + bandOffset)
                            val thickness = h * (0.16f + 0.03f * sin(secPhase + band))

                            val ribbonPath = Path().apply {
                                moveTo(-w * 0.05f, y0)
                                cubicTo(
                                    w * 0.30f, y1,
                                    w * 0.68f, y2,
                                    w * 1.05f, y3
                                )
                                lineTo(w * 1.05f, y3 + thickness)
                                cubicTo(
                                    w * 0.68f, y2 + thickness * 1.1f,
                                    w * 0.30f, y1 + thickness * 0.9f,
                                    -w * 0.05f, y0 + thickness
                                )
                                close()
                            }

                            val ribbonColors = when (band) {
                                0 -> listOf(
                                    Color.Transparent,
                                    accentPrimary.copy(alpha = 0.24f),
                                    accentSecondary.copy(alpha = 0.22f),
                                    Color.Transparent
                                )
                                1 -> listOf(
                                    Color.Transparent,
                                    accentSecondary.copy(alpha = 0.22f),
                                    accentTertiary.copy(alpha = 0.26f),
                                    Color.Transparent
                                )
                                else -> listOf(
                                    Color.Transparent,
                                    accentTertiary.copy(alpha = 0.20f),
                                    accentPrimary.copy(alpha = 0.22f),
                                    Color.Transparent
                                )
                            }

                            drawPath(
                                path = ribbonPath,
                                brush = Brush.horizontalGradient(colors = ribbonColors)
                            )
                        }

                        if (showLyricView) {
                            drawRect(color = Color(0xFF050811).copy(alpha = (dimAlpha * 0.52f).coerceIn(0.20f, 0.76f)))
                        }
                    }
                }

                PlayerBackgroundStyle.RHYTHM_HALO -> {
                    // 预设 3：律动光晕（实时读取当前播放歌曲的低频/中频/高频与节拍脉冲能量，驱动光晕波纹与极坐标声波环同频律动）
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val minDim = minOf(w, h).coerceAtLeast(1f)

                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    meshColorPrimary.copy(alpha = 0.88f),
                                    Color(0xFF070C17),
                                    meshColorSecondary.copy(alpha = 0.65f),
                                    Color(0xFF04070D)
                                )
                            )
                        )

                        val center = Offset(
                            x = w * 0.5f,
                            y = if (w > h * 1.15f) h * 0.48f else h * 0.39f
                        )
                        val baseR = minDim * 0.32f
                        val bassBoost = liveBassEnergy
                        val midBoost = liveMidEnergy
                        val highBoost = liveHighEnergy
                        val beatKick = liveBeatImpulse

                        // 底层环境低频呼吸大光晕（随歌曲低频能量与鼓点冲击实时扩张）
                        val ambientR = baseR * (1.85f + bassBoost * 0.65f + beatKick * 0.38f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentPrimary.copy(alpha = (0.24f + bassBoost * 0.22f).coerceAtMost(0.55f)),
                                    accentSecondary.copy(alpha = (0.14f + midBoost * 0.16f).coerceAtMost(0.38f)),
                                    accentTertiary.copy(alpha = (0.08f + highBoost * 0.12f).coerceAtMost(0.28f)),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = ambientR
                            ),
                            center = center,
                            radius = ambientR
                        )

                        // 多层同频律动光晕环（外圈响应重低音与鼓点脉冲，中圈响应人声中频，内圈响应高频泛音）
                        for (ring in 4 downTo 1) {
                            val ringFreqEnergy = when (ring) {
                                4 -> bassBoost * 0.85f + beatKick * 0.55f
                                3 -> bassBoost * 0.50f + midBoost * 0.65f + beatKick * 0.35f
                                2 -> midBoost * 0.80f + highBoost * 0.40f
                                else -> highBoost * 0.85f + beatKick * 0.30f
                            }
                            val r = baseR * (0.86f + ring * 0.24f + ringFreqEnergy * (0.18f + ring * 0.05f))
                            val ringColor = when (ring) {
                                4 -> accentPrimary
                                3 -> accentSecondary
                                2 -> accentTertiary
                                else -> accentPrimary
                            }
                            val fillAlpha = ((0.14f + ringFreqEnergy * 0.18f) / ring).coerceIn(0.03f, 0.32f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        ringColor.copy(alpha = fillAlpha * 0.6f),
                                        ringColor.copy(alpha = fillAlpha),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = r
                                ),
                                center = center,
                                radius = r
                            )
                            drawCircle(
                                color = ringColor.copy(alpha = ((0.18f + ringFreqEnergy * 0.35f) / ring).coerceIn(0.05f, 0.45f)),
                                center = center,
                                radius = r * 0.84f,
                                style = Stroke(width = (1.2f + ringFreqEnergy * 2.2f).dp.toPx())
                            )
                        }

                        // 极坐标音频频谱律动光波环 (Audio-Reactive Harmonic Polar Wave Ring)
                        val wavePoints = 48
                        val wavePath = Path()
                        val innerWavePath = Path()
                        for (i in 0..wavePoints) {
                            val idx = i % wavePoints
                            val angle = (idx.toFloat() / wavePoints) * (2f * PI.toFloat())
                            val binIdx = (idx % 10)
                            val binEnergy = liveSpectrumBins[binIdx]
                            val harmonic1 = sin(angle * 3f + liveWaveTime * 1.4f) * (0.04f + bassBoost * 0.08f)
                            val harmonic2 = cos(angle * 5f - liveWaveTime * 2.1f) * (0.03f + midBoost * 0.07f)
                            val harmonic3 = sin(angle * 8f + liveWaveTime * 3.0f) * (0.02f + highBoost * 0.06f)
                            val rMod = baseR * (1.16f + binEnergy * 0.22f + beatKick * 0.12f + harmonic1 + harmonic2 + harmonic3)
                            val rInnerMod = baseR * (0.96f + binEnergy * 0.14f - harmonic1 * 0.7f + harmonic2 * 0.8f)

                            val px = center.x + rMod * cos(angle)
                            val py = center.y + rMod * sin(angle)
                            val ix = center.x + rInnerMod * cos(angle)
                            val iy = center.y + rInnerMod * sin(angle)

                            if (i == 0) {
                                wavePath.moveTo(px, py)
                                innerWavePath.moveTo(ix, iy)
                            } else {
                                wavePath.lineTo(px, py)
                                innerWavePath.lineTo(ix, iy)
                            }
                        }
                        wavePath.close()
                        innerWavePath.close()

                        drawPath(
                            path = wavePath,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    accentPrimary.copy(alpha = (0.34f + beatKick * 0.30f).coerceAtMost(0.70f)),
                                    accentSecondary.copy(alpha = (0.30f + midBoost * 0.28f).coerceAtMost(0.65f)),
                                    accentTertiary.copy(alpha = (0.32f + highBoost * 0.28f).coerceAtMost(0.65f)),
                                    accentPrimary.copy(alpha = (0.34f + beatKick * 0.30f).coerceAtMost(0.70f))
                                ),
                                center = center
                            ),
                            style = Stroke(width = (1.8f + beatKick * 2.4f).dp.toPx())
                        )

                        drawPath(
                            path = innerWavePath,
                            color = accentSecondary.copy(alpha = (0.20f + highBoost * 0.25f).coerceAtMost(0.50f)),
                            style = Stroke(width = 1.2.dp.toPx())
                        )

                        if (showLyricView) {
                            drawRect(color = Color(0xFF050811).copy(alpha = (dimAlpha * 0.55f).coerceIn(0.20f, 0.78f)))
                        }
                    }
                }

                PlayerBackgroundStyle.STARLIGHT_NEBULA -> {
                    // 预设 4：暗夜星尘（升级深空三色旋转星云、三层景深视差星尘、十字衍射星芒与柔光流星轨迹）
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val maxDim = maxOf(w, h).coerceAtLeast(1f)

                        // 深空天鹅绒渐变底幕
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF040711),
                                    meshColorPrimary.copy(alpha = 0.82f),
                                    meshColorTertiary.copy(alpha = 0.72f),
                                    Color(0xFF03050B)
                                )
                            )
                        )

                        // 三团缓慢呼吸旋转的深空星云 (Deep Space Nebula Clouds)
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
                                    accentPrimary.copy(alpha = 0.26f * pulse),
                                    meshColorPrimary.copy(alpha = 0.18f),
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
                                    accentSecondary.copy(alpha = 0.22f),
                                    meshColorSecondary.copy(alpha = 0.15f),
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
                                    accentTertiary.copy(alpha = 0.20f),
                                    Color.Transparent
                                ),
                                center = neb3,
                                radius = maxDim * 0.46f * pulse
                            ),
                            center = neb3,
                            radius = maxDim * 0.46f * pulse
                        )

                        // 三层视差星尘与十字星芒 (56 颗远/中/近景星辰)
                        val normPhase = phase / (2f * PI.toFloat())
                        for (i in 0 until 56) {
                            val layer = i % 3 // 0=远景微尘, 1=中景星辰, 2=近景亮星
                            val seedX = ((i * 73 + 19) % 97) / 97f
                            val seedY = ((i * 41 + 11) % 89) / 89f
                            val speed = when (layer) {
                                0 -> 0.08f
                                1 -> 0.16f
                                else -> 0.24f
                            }
                            val driftY = (seedY - normPhase * speed + 1f) % 1f
                            val driftX = (seedX + 0.025f * sin(phase + i * 0.7f)).coerceIn(0.03f, 0.97f)
                            val twinkle = (0.30f + 0.70f * abs(sin(secPhase * (0.9f + (i % 5) * 0.28f) + i * 1.3f))).coerceIn(0.18f, 1f)

                            val sx = w * driftX
                            val sy = h * driftY
                            val sr = when (layer) {
                                0 -> 0.9f.dp.toPx()
                                1 -> 1.45f.dp.toPx()
                                else -> 2.15f.dp.toPx()
                            }
                            val starColor = when (i % 5) {
                                0 -> accentPrimary
                                1 -> accentSecondary
                                2 -> accentTertiary
                                3 -> Color(0xFFFFF4D6)
                                else -> Color.White
                            }

                            // 星晕
                            if (layer >= 1) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            starColor.copy(alpha = 0.32f * twinkle),
                                            Color.Transparent
                                        ),
                                        center = Offset(sx, sy),
                                        radius = sr * 3.6f
                                    ),
                                    center = Offset(sx, sy),
                                    radius = sr * 3.6f
                                )
                            }

                            // 星核
                            drawCircle(
                                color = starColor.copy(alpha = (0.45f + layer * 0.22f).coerceAtMost(0.95f) * twinkle),
                                radius = sr,
                                center = Offset(sx, sy)
                            )

                            // 近景亮星绘制十字衍射星芒 (4-Point Star Gleam)
                            if (layer == 2 && i % 6 == 2 && twinkle > 0.62f) {
                                val gleamLen = sr * (4.2f + twinkle * 2.5f)
                                val gleamAlpha = (twinkle - 0.45f).coerceIn(0.1f, 0.65f)
                                drawLine(
                                    color = Color.White.copy(alpha = gleamAlpha),
                                    start = Offset(sx - gleamLen, sy),
                                    end = Offset(sx + gleamLen, sy),
                                    strokeWidth = 0.9.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = gleamAlpha),
                                    start = Offset(sx, sy - gleamLen),
                                    end = Offset(sx, sy + gleamLen),
                                    strokeWidth = 0.9.dp.toPx()
                                )
                            }
                        }

                        // 周期性柔光流星划过轨迹 (Gentle Shooting Star Trail)
                        val meteorCycle = (secPhase / (2f * PI.toFloat()))
                        if (meteorCycle in 0.15f..0.45f) {
                            val mp = ((meteorCycle - 0.15f) / 0.30f).coerceIn(0f, 1f)
                            val headX = w * (0.82f - mp * 0.52f)
                            val headY = h * (0.14f + mp * 0.28f)
                            val tailX = headX + w * 0.11f
                            val tailY = headY - h * 0.06f
                            val meteorAlpha = sin(mp * PI.toFloat()).coerceIn(0f, 1f) * 0.65f
                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = meteorAlpha),
                                        accentSecondary.copy(alpha = meteorAlpha * 0.5f),
                                        Color.Transparent
                                    ),
                                    start = Offset(headX, headY),
                                    end = Offset(tailX, tailY)
                                ),
                                start = Offset(headX, headY),
                                end = Offset(tailX, tailY),
                                strokeWidth = 1.6.dp.toPx()
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

@Composable
internal fun RedesignedCommentCountButton(
    commentCount: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    val countText = formatPlayerCommentCount(commentCount)
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Box(
        modifier = Modifier
            .height(if (isLargeScreen) 54.dp else 44.dp)
            .widthIn(min = if (isLargeScreen) 64.dp else 52.dp)
            .softClickable(
                pressScale = 0.86f,
                pressAlpha = 0.78f,
                onClick = onClick
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // 气泡主图标偏左下放置，为右上角数字角标留出独立无遮挡空间
        Canvas(
            modifier = Modifier
                .size(if (isLargeScreen) 28.dp else 23.dp)
                .align(if (countText.isNotEmpty()) Alignment.CenterStart else Alignment.Center)
                .offset(y = if (countText.isNotEmpty()) 2.dp else 0.dp)
        ) {
            val w = size.width
            val h = size.height
            val stroke = (if (isLargeScreen) 2.4.dp else 2.1.dp).toPx()
            val c = Color.White.copy(alpha = 0.90f)
            val bubblePath = Path().apply {
                moveTo(w * 0.20f, h * 0.22f)
                lineTo(w * 0.80f, h * 0.22f)
                quadraticBezierTo(w * 0.90f, h * 0.22f, w * 0.90f, h * 0.36f)
                lineTo(w * 0.90f, h * 0.65f)
                quadraticBezierTo(w * 0.90f, h * 0.77f, w * 0.78f, h * 0.77f)
                lineTo(w * 0.44f, h * 0.77f)
                lineTo(w * 0.24f, h * 0.91f)
                lineTo(w * 0.26f, h * 0.77f)
                lineTo(w * 0.20f, h * 0.77f)
                quadraticBezierTo(w * 0.10f, h * 0.77f, w * 0.10f, h * 0.65f)
                lineTo(w * 0.10f, h * 0.36f)
                quadraticBezierTo(w * 0.10f, h * 0.22f, w * 0.20f, h * 0.22f)
                close()
            }
            drawPath(
                path = bubblePath,
                color = c,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            val dotR = (if (isLargeScreen) 1.8.dp else 1.5.dp).toPx()
            drawCircle(accentColor, radius = dotR, center = Offset(w * 0.36f, h * 0.49f))
            drawCircle(c, radius = dotR, center = Offset(w * 0.50f, h * 0.49f))
            drawCircle(c, radius = dotR, center = Offset(w * 0.64f, h * 0.49f))
        }

        // 右上角悬浮无遮挡角标（不遮盖评论气泡主体，也不被父容器裁切）
        if (countText.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(if (isLargeScreen) 8.dp else 7.dp),
                color = accentColor.copy(alpha = 0.24f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = if (isLargeScreen) 21.dp else 17.dp, top = 2.dp)
            ) {
                Text(
                    text = countText,
                    color = Color.White,
                    fontSize = if (isLargeScreen) 10.5.sp else 9.sp,
                    lineHeight = if (isLargeScreen) 12.sp else 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                )
            }
        }
    }
}

@Composable
internal fun PlumpLandscapeModeCanvasIcon(
    color: Color,
    accentColor: Color,
    iconSize: androidx.compose.ui.unit.Dp = if (com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice) 29.dp else 24.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx() * (w / 24.dp.toPx()).coerceIn(0.85f, 1.35f)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.10f, h * 0.24f),
            size = Size(w * 0.80f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(accentColor, Offset(w * 0.26f, h * 0.58f), Offset(w * 0.42f, h * 0.40f), stroke * 0.9f, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.26f, h * 0.58f), Offset(w * 0.40f, h * 0.58f), stroke * 0.9f, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.26f, h * 0.58f), Offset(w * 0.26f, h * 0.44f), stroke * 0.9f, StrokeCap.Round)

        drawLine(color, Offset(w * 0.74f, h * 0.42f), Offset(w * 0.58f, h * 0.60f), stroke * 0.9f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.74f, h * 0.42f), Offset(w * 0.60f, h * 0.42f), stroke * 0.9f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.74f, h * 0.42f), Offset(w * 0.74f, h * 0.56f), stroke * 0.9f, StrokeCap.Round)
    }
}

@Composable
internal fun PlayerActionIconButton(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Box(
        modifier = Modifier
            .size(if (isLargeScreen) 54.dp else 44.dp)
            .clip(CircleShape)
            .softClickable(
                pressScale = 0.85f,
                pressAlpha = 0.78f,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
internal fun MasterVinylTurntableView(
    coverUrl: String,
    isPlaying: Boolean,
    accentColor: Color,
    rotationProvider: () -> Float,
    scale: Float = 1f
) {
    val s = scale.coerceIn(0.50f, 1.60f)
    // 液压阻尼唱臂动画（Hydraulic Damped Tonearm）：
    // - 0f 为暂停态：唱臂垂直优雅停靠在右侧唱臂托架上，完全脱离黑胶盘面；
    // - 24f 为播放态：唱臂顺时针平稳旋入黑胶黄金声轨区；
    val HydraulicTonearmPlayEasing = remember { CubicBezierEasing(0.22f, 0.88f, 0.24f, 1.0f) }
    val HydraulicTonearmPauseEasing = remember { CubicBezierEasing(0.42f, 0.04f, 0.22f, 1.0f) }
    val armAngle by animateFloatAsState(
        targetValue = if (isPlaying) 24f else 0f,
        animationSpec = if (isPlaying) {
            tween(durationMillis = 640, easing = HydraulicTonearmPlayEasing)
        } else {
            tween(durationMillis = 1260, easing = HydraulicTonearmPauseEasing)
        },
        label = "masterTonearmAngle"
    )
    // 抬臂高度状态：暂停移开时先柔顺抬起唱臂（投影拉远柔化），落针时贴合盘面
    val stylusLift by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = if (isPlaying) {
            tween(durationMillis = 480, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 620, easing = FastOutSlowInEasing)
        },
        label = "masterStylusLift"
    )

    Box(
        modifier = Modifier.size(width = (312f * s).dp, height = (296f * s).dp),
        contentAlignment = Alignment.Center
    ) {
        // 黑胶主盘体略微左偏 (-8.dp * s)，为右侧精密液压唱臂与驻停托架留出黄金机械空间
        Box(
            modifier = Modifier
                .offset(x = (-8f * s).dp)
                .size((278f * s).dp)
                .shadow(
                    elevation = (28f * s).dp,
                    shape = CircleShape,
                    spotColor = accentColor.copy(alpha = 0.55f)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF222836).copy(alpha = 0.45f),
                            Color(0xFF0E121B).copy(alpha = 0.75f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .offset(x = (-8f * s).dp)
                .size((266f * s).dp)
                .clip(CircleShape)
                .graphicsLayer { rotationZ = rotationProvider() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.minDimension <= 2f) return@Canvas
                val radius = (size.minDimension / 2f).coerceAtLeast(1f)
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E222B),
                            Color(0xFF101217),
                            Color(0xFF08090C)
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )

                drawCircle(
                    brush = Brush.sweepGradient(
                        0.00f to Color.White.copy(alpha = 0.01f),
                        0.08f to Color.White.copy(alpha = 0.11f),
                        0.16f to Color.White.copy(alpha = 0.01f),
                        0.50f to Color.White.copy(alpha = 0.01f),
                        0.58f to Color.White.copy(alpha = 0.11f),
                        0.66f to Color.White.copy(alpha = 0.01f),
                        1.00f to Color.White.copy(alpha = 0.01f),
                        center = center
                    ),
                    radius = radius * 0.98f,
                    center = center
                )

                val innerR = radius * 0.64f
                val outerR = radius * 0.95f
                val step = (outerR - innerR) / 14f
                for (i in 0..13) {
                    val r = innerR + i * step
                    val alpha = when {
                        i == 0 || i == 13 -> 0.12f
                        i % 4 == 0 -> 0.09f
                        else -> 0.035f
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = (if (i % 4 == 0) 1.1f * s else 0.6f * s).dp.toPx())
                    )
                }

                drawCircle(
                    brush = Brush.linearGradient(
                        listOf(
                            VipGold.copy(alpha = 0.65f),
                            ConceptPrimary.copy(alpha = 0.45f),
                            VipGold.copy(alpha = 0.65f)
                        )
                    ),
                    radius = radius * 0.625f,
                    center = center,
                    style = Stroke(width = (2f * s).dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .size((164f * s).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF181C26)),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = coverUrl,
                    animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                    label = "vinylCoverArtworkCrossfade"
                ) { activeCoverUrl ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeCoverUrl.isNotBlank()) {
                            AsyncImage(
                                model = activeCoverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size((54f * s).dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size((28f * s).dp)
                        .clip(CircleShape)
                        .background(Color(0xAA0A0D14)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size((10f * s).dp)
                            .clip(CircleShape)
                            .background(Color(0xFF07090E))
                    )
                }
            }
        }

        // 右上精密液压平衡唱臂组件（按比例 s 同步缩放全套机械几何坐标）
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            if (size.minDimension <= 2f) return@Canvas
            fun Float.sdp(): Float = (this * s).dp.toPx()

            val pivot = Offset(size.width - 24f.sdp(), 34f.sdp())

            // 1. 右侧唱臂驻停托架 (Tonearm Rest Cradle)：暂停时唱臂正好稳稳落在此托架中
            val cradleCenter = Offset(pivot.x - 2f.sdp(), pivot.y + 72f.sdp())
            drawCircle(
                color = Color(0xFF121722).copy(alpha = 0.85f),
                radius = 7.5f.sdp(),
                center = cradleCenter
            )
            drawArc(
                color = Color(0xFF94A3B8).copy(alpha = 0.65f),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(cradleCenter.x - 5.5f.sdp(), cradleCenter.y - 5.5f.sdp()),
                size = Size(11f.sdp(), 11f.sdp()),
                style = Stroke(width = 1.8f.sdp(), cap = StrokeCap.Round)
            )

            // 2. 唱臂金属轴承底座 (Gimbal Pivot Base)
            drawCircle(
                color = Color(0xFF080B12).copy(alpha = 0.72f),
                radius = 21f.sdp(),
                center = pivot
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3E4759), Color(0xFF1A202C)),
                    center = pivot,
                    radius = 18f.sdp()
                ),
                radius = 17f.sdp(),
                center = pivot
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.24f),
                radius = 17f.sdp(),
                center = pivot,
                style = Stroke(width = 1f.sdp())
            )

            // 3. 液压旋转唱臂主体（0° 为垂直停靠在右侧托架，+24° 为旋入唱片声轨）
            rotate(degrees = armAngle, pivot = pivot) {
                val weightEnd = Offset(pivot.x + 2f.sdp(), pivot.y - 23f.sdp())
                drawLine(
                    color = Color(0xFF78849B),
                    start = pivot,
                    end = weightEnd,
                    strokeWidth = 5.5f.sdp(),
                    cap = StrokeCap.Round
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF222836), Color(0xFF475569), Color(0xFF1E2430))
                    ),
                    topLeft = Offset(pivot.x - 6f.sdp(), weightEnd.y),
                    size = Size(14f.sdp(), 11f.sdp()),
                    cornerRadius = CornerRadius(3f.sdp(), 3f.sdp())
                )

                val armEnd = Offset(pivot.x - 4f.sdp(), pivot.y + 104f.sdp())
                val sCurvePath = Path().apply {
                    moveTo(pivot.x, pivot.y)
                    cubicTo(
                        pivot.x + 5f.sdp(), pivot.y + 34f.sdp(),
                        pivot.x - 8f.sdp(), pivot.y + 68f.sdp(),
                        armEnd.x, armEnd.y
                    )
                }

                val shadowDx = (-2f - 2.5f * stylusLift).sdp()
                val shadowDy = (4f + 3.5f * stylusLift).sdp()
                val shadowPath = Path().apply {
                    moveTo(pivot.x + shadowDx * 0.3f, pivot.y + shadowDy * 0.3f)
                    cubicTo(
                        pivot.x + 5f.sdp() + shadowDx, pivot.y + 34f.sdp() + shadowDy,
                        pivot.x - 8f.sdp() + shadowDx, pivot.y + 68f.sdp() + shadowDy,
                        armEnd.x + shadowDx, armEnd.y + shadowDy
                    )
                }
                drawPath(
                    path = shadowPath,
                    color = Color.Black.copy(alpha = 0.42f - 0.14f * stylusLift),
                    style = Stroke(width = (5.8f + 1.5f * stylusLift).sdp(), cap = StrokeCap.Round)
                )

                drawPath(
                    path = sCurvePath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFF1F5F9),
                            Color(0xFF94A3B8),
                            Color(0xFFE2E8F0)
                        ),
                        start = pivot,
                        end = armEnd
                    ),
                    style = Stroke(width = 3.8f.sdp(), cap = StrokeCap.Round)
                )

                val headTip = Offset(armEnd.x - 8f.sdp(), armEnd.y + 16f.sdp())
                drawLine(
                    color = Color(0xFF1E293B),
                    start = armEnd,
                    end = headTip,
                    strokeWidth = 7.8f.sdp(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = VipGold,
                    start = Offset(armEnd.x - 1.5f.sdp(), armEnd.y + 3f.sdp()),
                    end = Offset(headTip.x + 1.5f.sdp(), headTip.y - 2.5f.sdp()),
                    strokeWidth = 2.2f.sdp(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = lerp(ConceptCyanAccent, Color.White.copy(alpha = 0.45f), stylusLift),
                    radius = (2.6f - 0.4f * stylusLift).sdp(),
                    center = headTip
                )
            }

            // 4. 轴承中心高光顶盖
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFF1F5F9), Color(0xFF64748B))
                ),
                radius = 7.2f.sdp(),
                center = pivot
            )
        }
    }
}

internal const val ROUNDED_COVER_INITIAL_REST_PHASE_RAD = (PI / 4.0).toFloat()
internal const val ROUNDED_COVER_SYMMETRY_PERIOD_RAD = (PI / 2.0).toFloat()
internal const val ROUNDED_COVER_TWO_PI_RAD = (PI * 2.0).toFloat()
internal const val ROUNDED_COVER_MIN_STOP_GLIDE_RAD = (PI / 12.0).toFloat() // 最小前向滑行角位移 15°，避免过近急刹
internal const val ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC = (2.0 * PI / 8.0).toFloat() // 巡航角速度 45°/s (8s/圈，每 2s 转过一个 90° 对称周期)
internal const val ROUNDED_COVER_SPIN_UP_DURATION_SEC = 0.68f
private const val ROUNDED_COVER_DECEL_EXPONENT = 2.2f

/**
 * 圆角方形封面流光动效连续状态容器：
 * - [dispersionPhase]：连续相位角（弧度），暂停/播放切换时严格保持 C1 连续，绝不重置回 0°；
 * - [haloPulse]：光晕呼吸缩放因子，随播放/暂停平滑收放；
 * - [playTransition]：0f（静止暂停态）到 1f（饱满播放态）的连续插值因子。
 */
@Immutable
internal data class RoundedCoverMotionState(
    val dispersionPhase: Float,
    val haloPulse: Float,
    val playTransition: Float,
    val coverScale: Float = 1.0f
)

/**
 * 方案 A：计算「播放 -> 暂停」时顺势向前滑行归位的最近 45° (mod 90°) 对角交错对称目标角（弧度）。
 * - 正方形流光框具有 90° (π/2) 四重旋转对称性，因此 45°、135°、225°、315° 几何形态完全一致；
 * - 暂停瞬间绝不瞬移或倒转，而是顺着当前旋转方向锁定前方最近的 k*(π/2) + π/4 对称角平顺滑行停靠。
 */
internal fun computeRoundedCoverSymmetryStopTargetRad(
    currentPhaseRad: Float,
    currentVelocityRadPerSec: Float = ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC
): Float {
    val period = ROUNDED_COVER_SYMMETRY_PERIOD_RAD
    val offset = ROUNDED_COVER_INITIAL_REST_PHASE_RAD
    val normalizedIndex = (currentPhaseRad - offset) / period
    val ceilIndex = kotlin.math.ceil(normalizedIndex.toDouble() - 1e-5).toInt()
    val candidate = ceilIndex * period + offset
    val delta = (candidate - currentPhaseRad).coerceAtLeast(0f)

    if (currentVelocityRadPerSec <= 0.02f && delta < 0.02f) {
        return candidate
    }
    val velocityRatio = (currentVelocityRadPerSec / ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC).coerceIn(0.25f, 1.0f)
    val minGlide = ROUNDED_COVER_MIN_STOP_GLIDE_RAD * velocityRatio
    return if (delta < minGlide) {
        candidate + period
    } else {
        candidate
    }
}

/**
 * 根据前向滑行角位移 Δθ 与当前角速度计算自然的阻尼减速时长（秒）。
 */
internal fun computeRoundedCoverDecelDurationSec(
    deltaRad: Float,
    currentVelocityRadPerSec: Float = ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC
): Float {
    if (deltaRad <= 1e-4f) return 0f
    val velocityFactor = (currentVelocityRadPerSec / ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC).coerceIn(0.45f, 1.15f)
    val rawDuration = (0.58f + 0.56f * deltaRad) / velocityFactor
    return rawDuration.coerceIn(0.68f, 1.52f)
}

/**
 * 计算「暂停 -> 播放」渐进加速阶段在 [elapsedSec] 时刻的瞬时角速度（smoothstep S 型平滑加速，0 阶跃）。
 */
internal fun computeRoundedCoverSpinUpVelocity(
    startOmegaRadPerSec: Float,
    elapsedSec: Float,
    targetOmegaRadPerSec: Float = ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC,
    durationSec: Float = ROUNDED_COVER_SPIN_UP_DURATION_SEC
): Float {
    if (durationSec <= 0f) return targetOmegaRadPerSec
    val u = (elapsedSec / durationSec).coerceIn(0f, 1f)
    val smooth = u * u * (3f - 2f * u)
    return startOmegaRadPerSec + (targetOmegaRadPerSec - startOmegaRadPerSec) * smooth
}

/**
 * 计算「播放 -> 暂停」阻尼减速阶段在 [elapsedSec] 时刻的瞬时相位角与瞬时角速度：
 * - 采用 p(s) = θ0 + Δθ * (1 - (1 - s)^n)，满足 p(0) = θ0, p(1) = θ_target, ω(1) = 0；
 * - 速度导数 ω(s) 全程严格单调递减至 0，无过冲、无回弹、无瞬移。
 */
internal fun evaluateRoundedCoverDecelerationStep(
    startPhaseRad: Float,
    targetPhaseRad: Float,
    durationSec: Float,
    elapsedSec: Float
): Pair<Float, Float> {
    val deltaRad = (targetPhaseRad - startPhaseRad).coerceAtLeast(0f)
    if (deltaRad <= 1e-5f || durationSec <= 1e-4f) {
        return targetPhaseRad to 0f
    }
    val s = (elapsedSec / durationSec).coerceIn(0f, 1f)
    val rem = 1f - s
    val remPow = Math.pow(rem.toDouble(), ROUNDED_COVER_DECEL_EXPONENT.toDouble()).toFloat()
    val remDerivPow = Math.pow(rem.toDouble(), (ROUNDED_COVER_DECEL_EXPONENT - 1f).toDouble()).toFloat()
    val phase = startPhaseRad + deltaRad * (1f - remPow)
    val velocity = if (s >= 1f) 0f else (ROUNDED_COVER_DECEL_EXPONENT * deltaRad / durationSec) * remDerivPow
    return phase to velocity
}

@Composable
internal fun rememberRoundedCoverMotionState(
    isPlaying: Boolean,
    songKey: Any? = null,
    activeAndVisible: Boolean = true
): RoundedCoverMotionState {
    var phaseRad by remember(songKey) { mutableFloatStateOf(ROUNDED_COVER_INITIAL_REST_PHASE_RAD) }
    var velocityRadPerSec by remember(songKey) { mutableFloatStateOf(0f) }
    var breathPhaseRad by remember(songKey) { mutableFloatStateOf(0f) }
    var hasEverPlayed by remember(songKey) { mutableStateOf(isPlaying) }

    val playTransition by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        label = "roundedCoverPlayTransition"
    )
    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.92f,
        animationSpec = ConceptMotion.gentleBounceSpring(),
        label = "squareCoverScale"
    )

    LaunchedEffect(isPlaying, songKey, activeAndVisible) {
        if (!activeAndVisible) return@LaunchedEffect
        val twoPi = ROUNDED_COVER_TWO_PI_RAD
        if (isPlaying) {
            hasEverPlayed = true
            val startOmega = velocityRadPerSec.coerceAtLeast(0f)
            var elapsedSec = 0f
            var lastFrameNanos = -1L
            while (isActive) {
                withFrameNanos { frameNanos ->
                    val dt = if (lastFrameNanos < 0L) {
                        0f
                    } else {
                        ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    }
                    lastFrameNanos = frameNanos
                    elapsedSec += dt
                    val currentOmega = computeRoundedCoverSpinUpVelocity(
                        startOmegaRadPerSec = startOmega,
                        elapsedSec = elapsedSec
                    )
                    velocityRadPerSec = currentOmega
                    phaseRad = (phaseRad + currentOmega * dt) % twoPi
                    breathPhaseRad = (breathPhaseRad + dt * (twoPi / 6.0f)) % twoPi
                }
            }
        } else if (hasEverPlayed || velocityRadPerSec > 0.005f) {
            val startPhase = phaseRad
            val startVelocity = velocityRadPerSec.coerceAtLeast(0.15f)
            val targetPhase = computeRoundedCoverSymmetryStopTargetRad(
                currentPhaseRad = startPhase,
                currentVelocityRadPerSec = startVelocity
            )
            val deltaRad = (targetPhase - startPhase).coerceAtLeast(0f)
            val durationSec = computeRoundedCoverDecelDurationSec(
                deltaRad = deltaRad,
                currentVelocityRadPerSec = startVelocity
            )
            if (durationSec > 0f && deltaRad > 1e-4f) {
                var elapsedSec = 0f
                var lastFrameNanos = -1L
                while (isActive && elapsedSec < durationSec) {
                    withFrameNanos { frameNanos ->
                        val dt = if (lastFrameNanos < 0L) {
                            0f
                        } else {
                            ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        }
                        lastFrameNanos = frameNanos
                        elapsedSec = (elapsedSec + dt).coerceAtMost(durationSec)
                        val (nextPhase, nextVel) = evaluateRoundedCoverDecelerationStep(
                            startPhaseRad = startPhase,
                            targetPhaseRad = targetPhase,
                            durationSec = durationSec,
                            elapsedSec = elapsedSec
                        )
                        phaseRad = nextPhase
                        velocityRadPerSec = nextVel
                        breathPhaseRad = (breathPhaseRad + dt * (twoPi / 6.0f) * (1f - elapsedSec / durationSec)) % twoPi
                    }
                }
            }
            phaseRad = ((targetPhase % twoPi) + twoPi) % twoPi
            velocityRadPerSec = 0f
        }
    }

    val haloPulse = 0.94f + playTransition * (0.065f + 0.055f * sin(breathPhaseRad))
    return RoundedCoverMotionState(
        dispersionPhase = phaseRad,
        haloPulse = haloPulse,
        playTransition = playTransition,
        coverScale = coverScale
    )
}

/**
 * 圆角封面多光谱棱镜边缘色散光晕绘制引擎 (Chromatic Dispersion Edge Halo):
 * - 同时赋能竖屏与横屏圆角封面；
 * - 将歌曲主题色分解为「青蓝短波 (Cyan-Blue)」「玫瑰品红长波 (Rose-Magenta)」「电光紫中波 (Electric-Violet)」三大色散通道；
 * - 采用满足离屏图层 Alpha 结合律的高明度预乘羽化混合 (BlendMode.SrcOver)，
 *   确保在 AnimatedContent 进出歌词页离屏透明层切换 (alpha < 1 <-> alpha == 1) 时光晕亮度 100% 连续零闪烁。
 */
internal fun DrawScope.drawRoundedCoverEdgeDispersionHalo(
    cornerPx: Float,
    accentPrimary: Color,
    accentSecondary: Color,
    dispersionPhase: Float,
    haloPulse: Float,
    isPlaying: Boolean,
    playTransition: Float = if (isPlaying) 1f else 0f
) {
    val w = size.width
    val h = size.height
    if (w <= 4f || h <= 4f) return
    val p = playTransition.coerceIn(0f, 1f)

    // 三原色高明度棱镜色散光谱通道（向高明度霓虹色偏置，保障在 SrcOver 预乘混合下通透鲜亮且不受离屏透明层突变影响）
    val prismCyan = lerp(lerp(accentSecondary, Color(0xFF00F0FF), 0.62f), Color.White, 0.22f)
    val prismMagenta = lerp(lerp(accentPrimary, Color(0xFFFF3E8E), 0.56f), Color.White, 0.20f)
    val prismViolet = lerp(lerp(lerp(accentPrimary, accentSecondary, 0.5f), Color(0xFFA29BFE), 0.58f), Color.White, 0.22f)
    val prismGold = lerp(lerp(accentPrimary, Color(0xFFFFD166), 0.48f), Color.White, 0.20f)
    val deepShadowColor = lerp(accentPrimary, Color.Black, 0.68f)

    // 1. 底部深邃悬浮立体暗影（随 playTransition 平滑过渡）
    val shadowCenter = Offset(w * 0.5f, h * 0.58f)
    val shadowRadius = (w * 0.68f).coerceAtLeast(1f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to deepShadowColor.copy(alpha = 0.42f + 0.20f * p),
                0.64f to deepShadowColor.copy(alpha = 0.16f + 0.12f * p),
                1.00f to Color.Transparent
            ),
            center = shadowCenter,
            radius = shadowRadius
        ),
        radius = shadowRadius,
        center = shadowCenter
    )

    // 2. 沿圆角矩形边缘展开的三通道色散错位光场 (Chromatic Aberration Rounded-Rect Shells)
    val dispAmp = (3.8f + 2.7f * p).dp.toPx() * haloPulse
    val cyanShift = Offset(cos(dispersionPhase) * dispAmp, sin(dispersionPhase) * dispAmp)
    val magentaShift = Offset(cos(dispersionPhase + 2.094f) * dispAmp, sin(dispersionPhase + 2.094f) * dispAmp)
    val violetShift = Offset(cos(dispersionPhase + 4.188f) * dispAmp, sin(dispersionPhase + 4.188f) * dispAmp)

    val maxSpreadPx = 28.dp.toPx() * haloPulse
    val shellCount = 7
    val activityBoost = 0.66f + 0.34f * p

    for (i in shellCount downTo 1) {
        val t = i.toFloat() / shellCount.toFloat()
        val outset = maxSpreadPx * t
        // 越靠近封面边缘色散能量越凝聚，向外呈二次方柔和衰减；采用 BlendMode.SrcOver 消除离屏层切换闪烁
        val shellAlpha = ((1f - t * 0.76f) * (1f - t * 0.76f) * 0.155f * activityBoost).coerceIn(0.010f, 0.20f)
        val shellSize = Size(w + outset * 2f, h + outset * 2f)
        val shellCorner = CornerRadius(cornerPx + outset, cornerPx + outset)

        // 短波青蓝色散壳
        drawRoundRect(
            color = prismCyan.copy(alpha = shellAlpha),
            topLeft = Offset(-outset + cyanShift.x * t, -outset + cyanShift.y * t),
            size = shellSize,
            cornerRadius = shellCorner,
            blendMode = BlendMode.SrcOver
        )
        // 长波品红色散壳
        drawRoundRect(
            color = prismMagenta.copy(alpha = shellAlpha * 0.95f),
            topLeft = Offset(-outset + magentaShift.x * t, -outset + magentaShift.y * t),
            size = shellSize,
            cornerRadius = shellCorner,
            blendMode = BlendMode.SrcOver
        )
        // 中波电光紫色散壳
        drawRoundRect(
            color = prismViolet.copy(alpha = shellAlpha * 0.92f),
            topLeft = Offset(-outset + violetShift.x * t, -outset + violetShift.y * t),
            size = shellSize,
            cornerRadius = shellCorner,
            blendMode = BlendMode.SrcOver
        )
    }

    // 3. 贴合圆角边缘的旋转棱镜光谱色散流光环 (Prismatic Spectral Dispersion Edge Rings)
    val center = Offset(w * 0.5f, h * 0.5f)
    val deg = ((dispersionPhase * 180f / PI.toFloat()) % 360f + 360f) % 360f
    rotate(degrees = deg, pivot = center) {
        val sweepBrush = Brush.sweepGradient(
            0.00f to prismCyan,
            0.16f to lerp(accentPrimary, Color.White, 0.18f),
            0.33f to prismMagenta,
            0.50f to prismGold,
            0.66f to prismViolet,
            0.83f to lerp(accentSecondary, Color.White, 0.18f),
            0.92f to Color.White.copy(alpha = 0.94f),
            1.00f to prismCyan,
            center = center
        )
        for (ring in 1..4) {
            val ringOutset = (1.2f + ring * 2.1f).dp.toPx() * haloPulse
            val ringAlpha = ((0.44f / ring) * activityBoost).coerceIn(0.05f, 0.48f)
            val strokeW = ((5.2f - ring * 0.9f).coerceAtLeast(1.2f)).dp.toPx()
            drawRoundRect(
                brush = sweepBrush,
                topLeft = Offset(-ringOutset, -ringOutset),
                size = Size(w + ringOutset * 2f, h + ringOutset * 2f),
                cornerRadius = CornerRadius(cornerPx + ringOutset, cornerPx + ringOutset),
                alpha = ringAlpha,
                style = Stroke(width = strokeW),
                blendMode = BlendMode.SrcOver
            )
        }
    }
}

/**
 * 圆角封面表层 2.5D 棱镜色散折射倒角高光边 (Prismatic Chamfered Bevel Rim)
 */
internal fun DrawScope.drawRoundedCoverPrismaticRim(
    cornerPx: Float,
    accentPrimary: Color,
    accentSecondary: Color,
    dispersionPhase: Float,
    isPlaying: Boolean,
    playTransition: Float = if (isPlaying) 1f else 0f
) {
    val w = size.width
    val h = size.height
    if (w <= 4f || h <= 4f) return
    val p = playTransition.coerceIn(0f, 1f)

    val prismCyan = lerp(accentSecondary, Color(0xFF00F0FF), 0.58f)
    val prismMagenta = lerp(accentPrimary, Color(0xFFFF3E8E), 0.52f)
    val prismViolet = lerp(accentPrimary, Color(0xFFA29BFE), 0.50f)

    // 1. 表层微受光柔面与暗角
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.13f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.22f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
    )

    // 2. 外沿多光谱棱镜色散折射高光边框
    val strokePx = 1.8.dp.toPx()
    val halfStroke = strokePx / 2f
    val phaseCos = cos(dispersionPhase)
    val phaseSin = sin(dispersionPhase)
    val gradStart = Offset(
        w * (0.5f - 0.5f * phaseCos),
        h * (0.5f - 0.5f * phaseSin)
    )
    val gradEnd = Offset(
        w * (0.5f + 0.5f * phaseCos),
        h * (0.5f + 0.5f * phaseSin)
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.48f + 0.24f * p),
                prismCyan.copy(alpha = 0.42f + 0.26f * p),
                prismMagenta.copy(alpha = 0.36f + 0.26f * p),
                prismViolet.copy(alpha = 0.40f + 0.26f * p),
                Color.White.copy(alpha = 0.32f + 0.22f * p)
            ),
            start = gradStart,
            end = gradEnd
        ),
        topLeft = Offset(halfStroke, halfStroke),
        size = Size(w - strokePx, h - strokePx),
        cornerRadius = CornerRadius(cornerPx - halfStroke, cornerPx - halfStroke),
        style = Stroke(width = strokePx)
    )

    // 3. 内侧晶莹次级折射细线
    val innerInset = 2.4.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                prismCyan.copy(alpha = 0.26f),
                Color.White.copy(alpha = 0.16f),
                prismMagenta.copy(alpha = 0.22f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        ),
        topLeft = Offset(innerInset, innerInset),
        size = Size(w - innerInset * 2f, h - innerInset * 2f),
        cornerRadius = CornerRadius(cornerPx - innerInset, cornerPx - innerInset),
        style = Stroke(width = 0.85.dp.toPx())
    )
}

@Composable
private fun StaticRoundedSquareCoverView(
    coverUrl: String,
    isPlaying: Boolean,
    accentColor: Color,
    secondaryAccentColor: Color = ConceptCyanAccent,
    motionState: RoundedCoverMotionState? = null,
    coverSize: androidx.compose.ui.unit.Dp = 276.dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 30.dp
) {
    val resolvedMotionState = motionState ?: rememberRoundedCoverMotionState(
        isPlaying = isPlaying,
        songKey = coverUrl
    )
    val coverScale = resolvedMotionState.coverScale
    val haloPulse = resolvedMotionState.haloPulse
    val dispersionPhase = resolvedMotionState.dispersionPhase
    val playTransition = resolvedMotionState.playTransition

    Box(
        modifier = Modifier
            .size(coverSize)
            .graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
                clip = false
            }
            .drawBehind {
                drawRoundedCoverEdgeDispersionHalo(
                    cornerPx = cornerRadius.toPx(),
                    accentPrimary = accentColor,
                    accentSecondary = secondaryAccentColor,
                    dispersionPhase = dispersionPhase,
                    haloPulse = haloPulse,
                    isPlaying = isPlaying,
                    playTransition = playTransition
                )
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF151A26))
            .drawWithContent {
                drawContent()
                drawRoundedCoverPrismaticRim(
                    cornerPx = cornerRadius.toPx(),
                    accentPrimary = accentColor,
                    accentSecondary = secondaryAccentColor,
                    dispersionPhase = dispersionPhase,
                    isPlaying = isPlaying,
                    playTransition = playTransition
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = coverUrl,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "squareCoverArtworkCrossfade"
        ) { activeCoverUrl ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (activeCoverUrl.isNotBlank()) {
                    AsyncImage(
                        model = activeCoverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(68.dp)
                    )
                }
            }
        }
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

/**
 * 自然光学彗星拖尾进度条 (CometTailProgressBar):
 * - 彗星外层羽化光流与内层晶核光束均采用「前端半圆包络 (Rounded Teardrop Envelope)」平滑包裹圆点中心，
 *   使拖尾从圆核内部自然向后渐隐收拢，彻底消除原直径处的垂直切线。
 */
@Composable
internal fun CometTailProgressBar(
    fraction: Float,
    isPlaying: Boolean,
    isInteracting: Boolean,
    currentTimeText: String,
    totalTimeText: String,
    climaxStartFrac: Float,
    climaxEndFrac: Float,
    highlightClimaxRange: Boolean,
    accentPrimary: Color = ConceptPrimary,
    accentSecondary: Color = ConceptCyanAccent,
    compact: Boolean = false,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit = onDragEnd
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val trackHeightDp by animateDpAsState(
        targetValue = if (compact) {
            if (isInteracting) 5.8.dp else 3.4.dp
        } else {
            if (isInteracting) 8.0.dp else 4.8.dp
        },
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cometTrackHeight"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isInteracting) 1.32f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cometThumbScale"
    )

    val cometPulse: Float
    val stardustPhase: Float
    if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "cometOpticalFlow")
        val animatedPulse by infiniteTransition.animateFloat(
            initialValue = 0.90f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cometBreath"
        )
        val animatedStardust by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "stardustPhase"
        )
        cometPulse = animatedPulse
        stardustPhase = animatedStardust
    } else {
        cometPulse = 1.0f
        stardustPhase = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 22.dp else 40.dp)
            .steplessHorizontalScrubGesture(
                onScrubStart = { startF -> currentOnDragStart(startF) },
                onScrubMove = { moveF -> currentOnDrag(moveF) },
                onScrubEnd = { currentOnDragEnd() },
                onScrubCancel = { currentOnDragCancel() }
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isInteracting,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xE6141E30),
                contentColor = Color.White
            ) {
                Text(
                    text = "$currentTimeText / $totalTimeText",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(if (compact) 18.dp else 32.dp)
        ) {
            val w = size.width
            val h = size.height
            if (w <= 2f || h <= 2f) return@Canvas
            val centerY = h / 2f
            val barH = trackHeightDp.toPx().coerceIn(2f, h)
            val barRadius = CornerRadius(barH / 2f, barH / 2f)
            val topY = centerY - barH / 2f

            drawRoundRect(
                color = Color.White.copy(alpha = if (isInteracting) 0.22f else 0.15f),
                topLeft = Offset(0f, topY),
                size = Size(w, barH),
                cornerRadius = barRadius
            )

            if (climaxEndFrac > climaxStartFrac) {
                val cStart = (w * climaxStartFrac).coerceIn(0f, w)
                val cWidth = (w * (climaxEndFrac - climaxStartFrac)).coerceIn(4f, (w - cStart).coerceAtLeast(4f))
                if (highlightClimaxRange) {
                    drawRoundRect(
                        color = VipGold.copy(alpha = 0.24f),
                        topLeft = Offset(cStart, centerY - barH * 1.10f),
                        size = Size(cWidth, barH * 2.20f),
                        cornerRadius = CornerRadius(barH, barH)
                    )
                }
                drawRoundRect(
                    color = VipGold.copy(alpha = if (highlightClimaxRange) 0.86f else 0.48f),
                    topLeft = Offset(cStart, topY),
                    size = Size(cWidth, barH),
                    cornerRadius = barRadius
                )
            }

            val headX = (w * fraction.coerceIn(0f, 1f)).coerceIn(0f, w)
            val headCenter = Offset(headX, centerY)
            val thumbRadius = (if (compact) 3.8.dp else 5.0.dp).toPx() * thumbScale
            val pulseFactor = (if (isPlaying) cometPulse else 1f).coerceIn(0.88f, 1.16f)

            if (headX > 1f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accentPrimary.copy(alpha = 0.38f),
                            accentPrimary.copy(alpha = 0.85f),
                            lerp(accentPrimary, accentSecondary, 0.75f)
                        ),
                        startX = 0f,
                        endX = headX
                    ),
                    topLeft = Offset(0f, topY),
                    size = Size(headX, barH),
                    cornerRadius = barRadius
                )
            }

            // 自然水滴包络式彗星拖尾 (Seamless Teardrop Comet Wake)
            val maxTailLen = min(headX, 118.dp.toPx())
            if (maxTailLen > 4f) {
                val tailStartX = (headX - maxTailLen).coerceAtLeast(0f)
                val outerCapR = (thumbRadius * 1.38f * pulseFactor).coerceAtLeast(barH * 0.9f)
                val innerCapR = (thumbRadius * 0.82f).coerceAtLeast(barH * 0.55f)

                val outerTeardropPath = Path().apply {
                    moveTo(tailStartX, centerY)
                    cubicTo(
                        tailStartX + maxTailLen * 0.45f, centerY - outerCapR * 0.15f,
                        tailStartX + maxTailLen * 0.82f, centerY - outerCapR,
                        headX, centerY - outerCapR
                    )
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = headX - outerCapR,
                            top = centerY - outerCapR,
                            right = headX + outerCapR,
                            bottom = centerY + outerCapR
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    cubicTo(
                        tailStartX + maxTailLen * 0.82f, centerY + outerCapR,
                        tailStartX + maxTailLen * 0.45f, centerY + outerCapR * 0.15f,
                        tailStartX, centerY
                    )
                    close()
                }
                drawPath(
                    path = outerTeardropPath,
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.38f to accentPrimary.copy(alpha = 0.12f),
                            0.76f to accentSecondary.copy(alpha = 0.34f),
                            0.94f to Color.White.copy(alpha = 0.46f),
                            1.00f to Color.White.copy(alpha = 0.25f)
                        ),
                        startX = tailStartX,
                        endX = (headX + outerCapR).coerceAtLeast(tailStartX + 2f)
                    )
                )

                val coreStart = tailStartX + maxTailLen * 0.20f
                val innerTeardropPath = Path().apply {
                    moveTo(coreStart, centerY)
                    cubicTo(
                        tailStartX + maxTailLen * 0.58f, centerY - innerCapR * 0.22f,
                        tailStartX + maxTailLen * 0.86f, centerY - innerCapR,
                        headX, centerY - innerCapR
                    )
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = headX - innerCapR,
                            top = centerY - innerCapR,
                            right = headX + innerCapR,
                            bottom = centerY + innerCapR
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    cubicTo(
                        tailStartX + maxTailLen * 0.86f, centerY + innerCapR,
                        tailStartX + maxTailLen * 0.58f, centerY + innerCapR * 0.22f,
                        coreStart, centerY
                    )
                    close()
                }
                drawPath(
                    path = innerTeardropPath,
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.42f to accentPrimary.copy(alpha = 0.45f),
                            0.82f to lerp(accentSecondary, Color.White, 0.55f).copy(alpha = 0.88f),
                            1.00f to Color.White
                        ),
                        startX = coreStart,
                        endX = (headX + innerCapR).coerceAtLeast(coreStart + 2f)
                    )
                )

                for (i in 0 until 5) {
                    val pNorm = (stardustPhase + i * 0.20f) % 1f
                    val distRatio = 1f - pNorm
                    val px = tailStartX + maxTailLen * (0.24f + 0.70f * distRatio)
                    val spread = distRatio * outerCapR * 0.55f
                    val dirSign = if (i % 2 == 0) 1f else -1f
                    val py = centerY + dirSign * sin(pNorm * PI.toFloat() * 2.0f + i) * spread
                    val pAlpha = (sin(distRatio * PI.toFloat()) * 0.78f).coerceIn(0f, 0.82f)
                    val pRadius = (0.8f + 1.2f * distRatio).dp.toPx()
                    drawCircle(
                        color = if (i % 2 == 0) Color.White.copy(alpha = pAlpha) else accentSecondary.copy(alpha = pAlpha),
                        radius = pRadius,
                        center = Offset(px, py)
                    )
                }
            }

            // 彗星晶核头部：多层同心柔光晕 + 融合炽白核心
            val haloRadius = (if (compact) 9.5.dp else 13.5.dp).toPx() * thumbScale * pulseFactor
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White,
                        0.26f to Color.White.copy(alpha = 0.92f),
                        0.52f to lerp(accentSecondary, Color.White, 0.45f).copy(alpha = 0.58f),
                        0.78f to accentPrimary.copy(alpha = 0.24f),
                        1.00f to Color.Transparent
                    ),
                    center = headCenter,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = headCenter
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        Color.White,
                        lerp(accentSecondary, Color.White, 0.65f)
                    ),
                    center = headCenter,
                    radius = thumbRadius
                ),
                radius = thumbRadius,
                center = headCenter
            )
        }
    }
}

internal fun createPlumpPlayTrianglePath(
    centerX: Float,
    centerY: Float,
    size: Float
): Path {
    val halfH = size * 0.50f
    val leftX = centerX - size * 0.42f
    val rightX = centerX + size * 0.48f
    val topY = centerY - halfH
    val bottomY = centerY + halfH
    val corner = size * 0.19f

    val p1 = Offset(leftX, topY)
    val p2 = Offset(rightX, centerY)
    val p3 = Offset(leftX, bottomY)

    fun moveAlong(from: Offset, to: Offset, dist: Float): Offset {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val r = (dist / len).coerceIn(0f, 0.45f)
        return Offset(from.x + dx * r, from.y + dy * r)
    }

    val p1a = moveAlong(p1, p3, corner)
    val p1b = moveAlong(p1, p2, corner)
    val p2a = moveAlong(p2, p1, corner)
    val p2b = moveAlong(p2, p3, corner)
    val p3a = moveAlong(p3, p2, corner)
    val p3b = moveAlong(p3, p1, corner)

    return Path().apply {
        moveTo(p1b.x, p1b.y)
        lineTo(p2a.x, p2a.y)
        quadraticBezierTo(p2.x, p2.y, p2b.x, p2b.y)
        lineTo(p3a.x, p3a.y)
        quadraticBezierTo(p3.x, p3.y, p3b.x, p3b.y)
        lineTo(p1a.x, p1a.y)
        quadraticBezierTo(p1.x, p1.y, p1b.x, p1b.y)
        close()
    }
}

/**
 * 连续几何插值播放/暂停形变绘制 (True Geometric Play/Pause Morph):
 * - 彻底淘汰 `morphProgress > 0.5f` 瞬间硬切；
 * - `morphProgress = 0f`（暂停态，显示饱满圆角播放三角） <-> `morphProgress = 1f`（播放态，分裂并延展为双圆角暂停竖柱）；
 * - 通过左右两组圆润四边几何路径连续线性插值，实现 120fps 丝滑无缝形变。
 */
internal fun DrawScope.drawContinuousPlayPauseMorph(
    morphProgress: Float,
    color: Color
) {
    val w = size.width
    val h = size.height
    if (w <= 2f || h <= 2f) return
    val p = morphProgress.coerceIn(0f, 1f)

    if (p <= 0.02f) {
        val triPath = createPlumpPlayTrianglePath(
            centerX = w * 0.54f,
            centerY = h * 0.50f,
            size = min(w, h) * 0.84f
        )
        drawPath(path = triPath, color = color)
        return
    }
    if (p >= 0.98f) {
        val barW = w * 0.24f
        val barH = h * 0.72f
        val topY = (h - barH) / 2f
        val leftX = w * 0.18f
        val rightX = w - leftX - barW
        val radius = CornerRadius(barW / 2f, barW / 2f)
        drawRoundRect(
            color = color,
            topLeft = Offset(leftX, topY),
            size = Size(barW, barH),
            cornerRadius = radius
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(rightX, topY),
            size = Size(barW, barH),
            cornerRadius = radius
        )
        return
    }

    fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    // 左半路径：从三角左半梯形平滑过渡到左暂停竖条
    val lTopLeftX = lerpF(w * 0.19f, w * 0.18f, p)
    val lTopLeftY = lerpF(h * 0.14f, h * 0.14f, p)
    val lTopRightX = lerpF(w * 0.53f, w * 0.42f, p)
    val lTopRightY = lerpF(h * 0.32f, h * 0.14f, p)
    val lBotRightX = lerpF(w * 0.53f, w * 0.42f, p)
    val lBotRightY = lerpF(h * 0.68f, h * 0.86f, p)
    val lBotLeftX = lerpF(w * 0.19f, w * 0.18f, p)
    val lBotLeftY = lerpF(h * 0.86f, h * 0.86f, p)

    // 右半路径：从三角右尖端平滑分裂延展为右暂停竖条
    val gapProgress = ((p - 0.10f) / 0.90f).coerceIn(0f, 1f)
    val rTopLeftX = lerpF(w * 0.51f, w * 0.58f, gapProgress)
    val rTopLeftY = lerpF(h * 0.31f, h * 0.14f, p)
    val rTopRightX = lerpF(w * 0.87f, w * 0.82f, p)
    val rTopRightY = lerpF(h * 0.50f, h * 0.14f, p)
    val rBotRightX = lerpF(w * 0.87f, w * 0.82f, p)
    val rBotRightY = lerpF(h * 0.50f, h * 0.86f, p)
    val rBotLeftX = lerpF(w * 0.51f, w * 0.58f, gapProgress)
    val rBotLeftY = lerpF(h * 0.69f, h * 0.86f, p)

    val strokeJoinRadius = min(w, h) * 0.11f

    val leftMorphPath = Path().apply {
        moveTo(lTopLeftX, lTopLeftY)
        lineTo(lTopRightX, lTopRightY)
        lineTo(lBotRightX, lBotRightY)
        lineTo(lBotLeftX, lBotLeftY)
        close()
    }
    val rightMorphPath = Path().apply {
        moveTo(rTopLeftX, rTopLeftY)
        lineTo(rTopRightX, rTopRightY)
        lineTo(rBotRightX, rBotRightY)
        lineTo(rBotLeftX, rBotLeftY)
        close()
    }

    drawPath(path = leftMorphPath, color = color)
    drawPath(
        path = leftMorphPath,
        color = color,
        style = Stroke(width = strokeJoinRadius, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(path = rightMorphPath, color = color)
    drawPath(
        path = rightMorphPath,
        color = color,
        style = Stroke(width = strokeJoinRadius, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

// =========================================================================================
// 圆润饱满重绘图标组件库 (Plump & Rounded Custom Canvas Icons)
// =========================================================================================

@Composable
internal fun PlumpHeartCanvasIcon(
    isFavorite: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.85f)
            cubicTo(w * 0.10f, h * 0.60f, w * 0.06f, h * 0.25f, w * 0.28f, h * 0.16f)
            cubicTo(w * 0.42f, h * 0.10f, w * 0.50f, h * 0.22f, w * 0.50f, h * 0.28f)
            cubicTo(w * 0.50f, h * 0.22f, w * 0.58f, h * 0.10f, w * 0.72f, h * 0.16f)
            cubicTo(w * 0.94f, h * 0.25f, w * 0.90f, h * 0.60f, w * 0.50f, h * 0.85f)
            close()
        }
        if (isFavorite) {
            drawPath(path = path, color = color)
        } else {
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
internal fun PlumpPosterSparkleCanvasIcon(
    color: Color,
    accentColor: Color,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx() * (w / 22.dp.toPx()).coerceIn(0.85f, 1.35f)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            cornerRadius = CornerRadius(w * 0.24f, w * 0.24f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(w * 0.28f, h * 0.44f), Offset(w * 0.68f, h * 0.44f), stroke, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.28f, h * 0.62f), Offset(w * 0.54f, h * 0.62f), stroke, StrokeCap.Round)
        drawCircle(accentColor, radius = w * 0.08f, center = Offset(w * 0.68f, h * 0.28f))
    }
}

@Composable
internal fun PlumpPlaylistAddCanvasIcon(
    color: Color,
    accentColor: Color,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx() * (w / 24.dp.toPx()).coerceIn(0.85f, 1.35f)
        drawLine(color, Offset(w * 0.14f, h * 0.26f), Offset(w * 0.78f, h * 0.26f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.56f, h * 0.50f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.74f), Offset(w * 0.46f, h * 0.74f), stroke, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.74f, h * 0.52f), Offset(w * 0.74f, h * 0.84f), stroke, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.58f, h * 0.68f), Offset(w * 0.90f, h * 0.68f), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun PlumpAudioWaveCanvasIcon(
    color: Color,
    accentColor: Color,
    iconSize: androidx.compose.ui.unit.Dp = if (com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice) 29.dp else 24.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val pillW = w * 0.13f
        val xs = listOf(0.15f, 0.34f, 0.53f, 0.72f)
        val hs = listOf(0.36f, 0.76f, 0.56f, 0.36f)
        xs.forEachIndexed { idx, xr ->
            val bh = h * hs[idx]
            val top = (h - bh) / 2f
            drawRoundRect(
                color = if (idx == 1) accentColor else color,
                topLeft = Offset(w * xr, top),
                size = Size(pillW, bh),
                cornerRadius = CornerRadius(pillW / 2f, pillW / 2f)
            )
        }
    }
}

@Composable
internal fun PlumpDesktopLyricCanvasIcon(
    enabled: Boolean,
    color: Color,
    accentColor: Color,
    iconSize: androidx.compose.ui.unit.Dp = if (com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice) 29.dp else 24.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 2.3.dp.toPx() * (w / 24.dp.toPx()).coerceIn(0.85f, 1.35f)
        if (enabled) {
            drawRoundRect(
                color = color.copy(alpha = 0.20f),
                topLeft = Offset(w * 0.12f, h * 0.18f),
                size = Size(w * 0.76f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
            )
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.18f),
            size = Size(w * 0.76f, h * 0.64f),
            cornerRadius = CornerRadius(w * 0.22f, w * 0.22f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(w * 0.28f, h * 0.42f), Offset(w * 0.72f, h * 0.42f), stroke, StrokeCap.Round)
        drawLine(if (enabled) accentColor else color, Offset(w * 0.28f, h * 0.60f), Offset(w * 0.56f, h * 0.60f), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun PlumpMoreDotsCanvasIcon(
    color: Color,
    iconSize: androidx.compose.ui.unit.Dp = if (com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice) 29.dp else 24.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val r = w * 0.088f
        drawCircle(color, radius = r, center = Offset(w * 0.23f, h * 0.5f))
        drawCircle(color, radius = r, center = Offset(w * 0.50f, h * 0.5f))
        drawCircle(color, radius = r, center = Offset(w * 0.77f, h * 0.5f))
    }
}

/**
 * 重绘控件 1：圆润饱满播放模式切换按钮
 */
@Composable
internal fun AnimatedPlayModeButton(
    playMode: PlayMode,
    accentColor: Color = ConceptCyanAccent,
    onClick: () -> Unit
) {
    var clickCount by remember { mutableIntStateOf(0) }
    val spinDeg by animateFloatAsState(
        targetValue = clickCount * 180f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playModeSpin"
    )
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice

    Box(
        modifier = Modifier
            .size(if (isLargeScreen) 56.dp else 46.dp)
            .clip(CircleShape)
            .softClickable(
                pressScale = 0.84f,
                onClick = {
                    clickCount++
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = playMode,
            transitionSpec = {
                (scaleIn(initialScale = 0.65f, animationSpec = spring(dampingRatio = 0.65f)) +
                    fadeIn(tween(160))) togetherWith
                    (scaleOut(targetScale = 0.65f, animationSpec = tween(130)) +
                        fadeOut(tween(120)))
            },
            label = "playModeMorph"
        ) { mode ->
            Canvas(
                modifier = Modifier
                    .size(if (isLargeScreen) 30.dp else 25.dp)
                    .graphicsLayer {
                        rotationZ = if (mode == PlayMode.REPEAT_ALL) spinDeg % 360f else 0f
                    }
            ) {
                val w = size.width
                val h = size.height
                val stroke = (if (isLargeScreen) 2.7.dp else 2.4.dp).toPx()
                val tint = Color.White.copy(alpha = 0.90f)

                when (mode) {
                    PlayMode.REPEAT_ALL -> {
                        val pad = w * 0.14f
                        val rectSize = Size(w - pad * 2, h - pad * 2)
                        drawArc(
                            color = tint,
                            startAngle = -155f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = rectSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = tint,
                            startAngle = 25f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = rectSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        val topTip = Offset(w * 0.78f, h * 0.26f)
                        drawLine(tint, topTip, Offset(topTip.x - 4.2.dp.toPx(), topTip.y - 1.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(tint, topTip, Offset(topTip.x - 1.dp.toPx(), topTip.y + 4.2.dp.toPx()), stroke, StrokeCap.Round)

                        val botTip = Offset(w * 0.22f, h * 0.74f)
                        drawLine(tint, botTip, Offset(botTip.x + 4.2.dp.toPx(), botTip.y + 1.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(tint, botTip, Offset(botTip.x + 1.dp.toPx(), botTip.y - 4.2.dp.toPx()), stroke, StrokeCap.Round)
                    }

                    PlayMode.REPEAT_ONE -> {
                        val pad = w * 0.14f
                        val rectSize = Size(w - pad * 2, h - pad * 2)
                        drawArc(
                            color = accentColor,
                            startAngle = -155f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = rectSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = accentColor,
                            startAngle = 25f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = rectSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(w * 0.50f, h * 0.34f),
                            end = Offset(w * 0.50f, h * 0.66f),
                            strokeWidth = stroke * 1.05f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(w * 0.41f, h * 0.41f),
                            end = Offset(w * 0.50f, h * 0.34f),
                            strokeWidth = stroke * 0.95f,
                            cap = StrokeCap.Round
                        )
                    }

                    PlayMode.SHUFFLE -> {
                        val path1 = Path().apply {
                            moveTo(w * 0.15f, h * 0.32f)
                            cubicTo(w * 0.45f, h * 0.32f, w * 0.55f, h * 0.68f, w * 0.85f, h * 0.68f)
                        }
                        val path2 = Path().apply {
                            moveTo(w * 0.15f, h * 0.68f)
                            cubicTo(w * 0.45f, h * 0.68f, w * 0.55f, h * 0.32f, w * 0.85f, h * 0.32f)
                        }
                        drawPath(path1, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
                        drawPath(path2, accentColor, style = Stroke(width = stroke, cap = StrokeCap.Round))

                        val tipTop = Offset(w * 0.86f, h * 0.32f)
                        drawLine(accentColor, tipTop, Offset(tipTop.x - 3.8.dp.toPx(), tipTop.y - 3.2.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(accentColor, tipTop, Offset(tipTop.x - 3.8.dp.toPx(), tipTop.y + 3.2.dp.toPx()), stroke, StrokeCap.Round)

                        val tipBot = Offset(w * 0.86f, h * 0.68f)
                        drawLine(tint, tipBot, Offset(tipBot.x - 3.8.dp.toPx(), tipBot.y - 3.2.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(tint, tipBot, Offset(tipBot.x - 3.8.dp.toPx(), tipBot.y + 3.2.dp.toPx()), stroke, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

/**
 * 重绘控件 2 & 4：圆润饱满上一首 / 下一首按钮（饱满大圆角三角 + 圆润胶囊竖柱）
 */
@Composable
internal fun RedrawnSkipButton(
    isNext: Boolean,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val nudgeX = remember { Animatable(0f) }
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice

    Box(
        modifier = Modifier
            .size(if (isLargeScreen) 62.dp else 52.dp)
            .clip(CircleShape)
            .graphicsLayer {
                translationX = nudgeX.value
            }
            .softClickable(
                pressScale = 0.86f,
                onClick = {
                    scope.launch {
                        val target = if (isNext) 12f else -12f
                        nudgeX.animateTo(target, tween(85))
                        nudgeX.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
                    }
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(if (isLargeScreen) 36.dp else 30.dp)) {
            val w = size.width
            val h = size.height
            val barW = w * 0.145f
            val barH = h * 0.60f
            val barTop = (h - barH) / 2f

            if (isNext) {
                val triPath = createPlumpPlayTrianglePath(
                    centerX = w * 0.40f,
                    centerY = h * 0.50f,
                    size = min(w, h) * 0.66f
                )
                drawPath(path = triPath, color = Color.White)
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.72f, barTop),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                )
            } else {
                rotate(degrees = 180f, pivot = Offset(w / 2f, h / 2f)) {
                    val triPath = createPlumpPlayTrianglePath(
                        centerX = w * 0.40f,
                        centerY = h * 0.50f,
                        size = min(w, h) * 0.66f
                    )
                    drawPath(path = triPath, color = Color.White)
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(w * 0.72f, barTop),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
        }
    }
}

/**
 * 重绘控件 3：核心播放/暂停流体按钮 (AnimatedPlayPauseCenterButton)
 */
@Composable
internal fun AnimatedPlayPauseCenterButton(
    isPlaying: Boolean,
    accentPrimary: Color = ConceptPrimary,
    accentSecondary: Color = ConceptCyanAccent,
    onClick: () -> Unit
) {
    val morphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = ConceptMotion.expressiveSpring(),
        label = "playPauseMorph"
    )
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice

    val auraScale: Float = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "playButtonAura")
        val animatedAura by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "auraScale"
        )
        animatedAura
    } else {
        1.0f
    }

    Box(
        modifier = Modifier.size(if (isLargeScreen) 88.dp else 76.dp),
        contentAlignment = Alignment.Center
    ) {
        // 动态封面色彩呼吸光环
        Box(
            modifier = Modifier
                .size(if (isLargeScreen) 86.dp else 74.dp)
                .graphicsLayer {
                    scaleX = auraScale
                    scaleY = auraScale
                    alpha = if (isPlaying) 0.38f else 0.20f
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accentPrimary, accentSecondary.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )

        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 14.dp,
            modifier = Modifier
                .size(if (isLargeScreen) 76.dp else 66.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            lerp(accentPrimary, Color.White, 0.14f),
                            accentPrimary,
                            lerp(accentPrimary, accentSecondary, 0.72f)
                        )
                    )
                )
                .softClickable(
                    pressScale = 0.88f,
                    onClick = onClick
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                val safeMorph = morphProgress.coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .size(if (isLargeScreen) 33.dp else 28.dp)
                        .graphicsLayer {
                            val bounce = (1f - 0.08f * sin(safeMorph * PI.toFloat())).coerceIn(0.88f, 1.10f)
                            scaleX = bounce
                            scaleY = bounce
                            rotationZ = (1f - safeMorph) * -6f * sin(safeMorph * PI.toFloat())
                        }
                ) {
                    drawContinuousPlayPauseMorph(
                        morphProgress = safeMorph,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 重绘控件 5：圆润饱满播放列表/队列按钮
 */
@Composable
internal fun RedrawnQueueButton(
    accentColor: Color = ConceptCyanAccent,
    onClick: () -> Unit
) {
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Box(
        modifier = Modifier
            .size(if (isLargeScreen) 56.dp else 46.dp)
            .clip(CircleShape)
            .softClickable(
                pressScale = 0.84f,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(if (isLargeScreen) 30.dp else 25.dp)) {
            val w = size.width
            val h = size.height
            val stroke = (if (isLargeScreen) 2.8.dp else 2.5.dp).toPx()
            val tint = Color.White.copy(alpha = 0.90f)

            drawLine(tint, Offset(w * 0.14f, h * 0.26f), Offset(w * 0.78f, h * 0.26f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.62f, h * 0.50f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.14f, h * 0.74f), Offset(w * 0.48f, h * 0.74f), stroke, StrokeCap.Round)

            val noteCenter = Offset(w * 0.72f, h * 0.74f)
            drawCircle(accentColor, radius = (if (isLargeScreen) 4.0.dp else 3.4.dp).toPx(), center = noteCenter)
            drawLine(
                color = accentColor,
                start = Offset(noteCenter.x + 2.5.dp.toPx(), noteCenter.y),
                end = Offset(noteCenter.x + 2.5.dp.toPx(), h * 0.40f),
                strokeWidth = stroke * 0.95f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = accentColor,
                start = Offset(noteCenter.x + 2.5.dp.toPx(), h * 0.40f),
                end = Offset(w * 0.90f, h * 0.47f),
                strokeWidth = stroke * 0.95f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 独立高定歌词页面：
 * - 右侧按钮与底部播放键全部去除边框线 (`.border(...)`)，采用圆润饱满的无边框磨砂胶囊与圆钮；
 * - 修复居中/居右对齐时左窄右宽不对称问题；
 * - 支持封面动态取色高亮主题 (`DYNAMIC_COVER`) 与副歌词字号缩放比 (`lyricTranslationScale`)。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RedesignedDedicatedLyricPage(
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    rawPositionMs: Long,
    lyricPositionProvider: () -> Long = { currentPositionMs },
    rawPositionProvider: () -> Long = { rawPositionMs },
    durationMs: Long,
    isPlaying: Boolean,
    isFavorite: Boolean,
    showTranslation: Boolean,
    showTranslationButton: Boolean,
    showPhonetic: Boolean,
    showPhoneticButton: Boolean = false,
    lyricFontSize: Float,
    lyricTranslationScale: Float = 0.80f,
    lyricAlignment: LyricAlignment,
    lyricWordByWord: Boolean,
    lyricBlurEnabled: Boolean,
    lyricBlurIntensity: Float,
    lyricLineSpacing: Float,
    lyricHighlightTheme: LyricHighlightTheme,
    dynamicCoverAccent: Color = ConceptPrimary,
    dynamicCoverSecondary: Color = ConceptCyanAccent,
    onLineClick: (Int) -> Unit,
    onReturnToCover: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleTranslation: () -> Unit,
    onTogglePhonetic: () -> Unit = {},
    onOpenLyricSettings: () -> Unit
) {
    val activePrimaryColor = if (lyricHighlightTheme == LyricHighlightTheme.DYNAMIC_COVER) {
        dynamicCoverAccent.readableOnDarkSurface(minLuminance = 0.46f)
    } else {
        Color(lyricHighlightTheme.primaryHex)
    }
    val activeSecondaryColor = if (lyricHighlightTheme == LyricHighlightTheme.DYNAMIC_COVER) {
        dynamicCoverSecondary.readableOnDarkSurface(minLuminance = 0.50f)
    } else {
        Color(lyricHighlightTheme.secondaryHex)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onReturnToCover
                )
        ) {
            if (lyricData.lines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "纯音乐，暂无歌词或正在检索官方歌词...",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            } else {
                val listState = rememberLazyListState()
                val isDragged by listState.interactionSource.collectIsDraggedAsState()
                var isUserScrubbing by remember { mutableStateOf(false) }

                LaunchedEffect(isDragged) {
                    if (isDragged) {
                        isUserScrubbing = true
                    }
                }

                LaunchedEffect(isUserScrubbing, isDragged, listState.isScrollInProgress) {
                    if (isUserScrubbing && !isDragged && !listState.isScrollInProgress) {
                        delay(3200L)
                        isUserScrubbing = false
                    }
                }

                LaunchedEffect(currentLyricIndex, isUserScrubbing) {
                    if (!isUserScrubbing && currentLyricIndex in lyricData.lines.indices) {
                        val layoutInfo = listState.layoutInfo
                        val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(360)
                        val goldenFocusOffset = -(viewportHeight * 0.28f).roundToInt()
                        val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentLyricIndex }
                        if (targetItem != null) {
                            val currentTargetCenter = targetItem.offset + (targetItem.size / 2)
                            val desiredCenter = -goldenFocusOffset
                            val scrollDelta = (currentTargetCenter - desiredCenter).toFloat()
                            if (abs(scrollDelta) > 2f) {
                                listState.animateScrollBy(
                                    value = scrollDelta,
                                    animationSpec = tween(
                                        durationMillis = 420,
                                        easing = ConceptMotion.LyricSmoothScrollEasing
                                    )
                                )
                            }
                        } else {
                            listState.animateScrollToItem(
                                index = currentLyricIndex,
                                scrollOffset = goldenFocusOffset
                            )
                        }
                    }
                }

                val scrubLineIndex by remember(lyricData.lines) {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val visibleItems = layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) {
                            currentLyricIndex.coerceIn(0, (lyricData.lines.size - 1).coerceAtLeast(0))
                        } else {
                            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                            visibleItems.minByOrNull { item ->
                                abs((item.offset + item.size / 2) - viewportCenter)
                            }?.index?.coerceIn(0, (lyricData.lines.size - 1).coerceAtLeast(0)) ?: 0
                        }
                    }
                }

                val textAlign = when (lyricAlignment) {
                    LyricAlignment.LEFT -> TextAlign.Start
                    LyricAlignment.CENTER -> TextAlign.Center
                    LyricAlignment.RIGHT -> TextAlign.End
                }
                val horizontalAlignment = when (lyricAlignment) {
                    LyricAlignment.LEFT -> Alignment.Start
                    LyricAlignment.CENTER -> Alignment.CenterHorizontally
                    LyricAlignment.RIGHT -> Alignment.End
                }
                val flowArrangement = when (lyricAlignment) {
                    LyricAlignment.LEFT -> Arrangement.Start
                    LyricAlignment.CENTER -> Arrangement.Center
                    LyricAlignment.RIGHT -> Arrangement.End
                }
                val supportsHardwareBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

                val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
                val effectiveLyricPageFontSize = if (isLargeScreen) (lyricFontSize + 2f).coerceAtMost(36f) else lyricFontSize

                // 平衡左右内边距，使歌词显示位置保持不变，同时在大屏下让左右两侧空白点击可返回封面页
                val startPad = when (lyricAlignment) {
                    LyricAlignment.CENTER -> 28.dp
                    LyricAlignment.RIGHT -> 16.dp
                    LyricAlignment.LEFT -> 8.dp
                }
                val endPad = when (lyricAlignment) {
                    LyricAlignment.CENTER -> 28.dp
                    LyricAlignment.RIGHT -> if (isLargeScreen) 28.dp else 54.dp
                    LyricAlignment.LEFT -> if (isLargeScreen) 28.dp else 50.dp
                }

                // 在平板端将歌词列表约束在中央原位置（widthIn(max = 560.dp)），使左右两侧空白区域完全暴露给外层 Box，点击左右空白即可返回封面页；
                // 同时在列表内部点击非歌词文字空白处也支持返回封面页
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(if (isLargeScreen) Modifier.widthIn(max = 560.dp) else Modifier)
                        .fillMaxSize()
                        .pointerInput(onReturnToCover) {
                            detectTapGestures(onTap = { onReturnToCover() })
                        }
                        .padding(start = startPad, end = endPad),
                    contentPadding = PaddingValues(vertical = 170.dp),
                    horizontalAlignment = horizontalAlignment
                ) {
                    itemsIndexed(
                        items = lyricData.lines,
                        key = { idx, item -> "${idx}_${item.startTimeMs}" },
                        contentType = { _, _ -> "lyric_line" }
                    ) { index, line ->
                        val isCurrent = index == currentLyricIndex
                        val isScrubTarget = isUserScrubbing && index == scrubLineIndex
                        val distance = abs(index - currentLyricIndex)

                        // 零 Text Relayout 弹性缩放：统一基础排版字号，通过 GPU graphicsLayer 连续驱动缩放 (0.86f <-> 1.05f)
                        val lineScale by animateFloatAsState(
                            targetValue = when {
                                isCurrent -> 1.05f
                                isScrubTarget -> 1.0f
                                else -> (0.93f - (distance - 1).coerceAtLeast(0) * 0.02f).coerceAtLeast(0.86f)
                            },
                            animationSpec = ConceptMotion.spatialPageSpring(),
                            label = "lyricLineScale"
                        )

                        val targetDepthBlurVal = if (lyricBlurEnabled && !isUserScrubbing && !isCurrent) {
                            when (distance) {
                                1 -> 0.72f * lyricBlurIntensity
                                2 -> 1.35f * lyricBlurIntensity
                                3 -> 2.10f * lyricBlurIntensity
                                else -> 2.85f * lyricBlurIntensity
                            }.coerceIn(0f, 5.0f)
                        } else 0f
                        val depthBlurVal by animateFloatAsState(
                            targetValue = targetDepthBlurVal,
                            animationSpec = tween(
                                durationMillis = 420,
                                easing = ConceptMotion.LyricSmoothScrollEasing
                            ),
                            label = "lyricLineDepthBlur"
                        )

                        val targetDistanceAlpha = when {
                            isCurrent -> 1.0f
                            isScrubTarget -> 0.92f
                            distance == 1 -> 0.58f
                            distance == 2 -> 0.38f
                            distance == 3 -> 0.24f
                            else -> 0.14f
                        }
                        val distanceAlpha by animateFloatAsState(
                            targetValue = targetDistanceAlpha,
                            animationSpec = tween(
                                durationMillis = 420,
                                easing = ConceptMotion.LyricSmoothScrollEasing
                            ),
                            label = "lyricLineDistanceAlpha"
                        )

                        val transformOriginX = when (lyricAlignment) {
                            LyricAlignment.LEFT -> 0f
                            LyricAlignment.CENTER -> 0.5f
                            LyricAlignment.RIGHT -> 1f
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = lineScale
                                    scaleY = lineScale
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(transformOriginX, 0.5f)
                                }
                                .then(
                                    if (supportsHardwareBlur && depthBlurVal >= 0.05f) {
                                        Modifier.blur(depthBlurVal.dp)
                                    } else Modifier
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isScrubTarget) activePrimaryColor.copy(alpha = 0.10f) else Color.Transparent
                                )
                                .softClickable(
                                    pressScale = 0.98f,
                                    onClick = {
                                        isUserScrubbing = false
                                        onLineClick(index)
                                    }
                                )
                                .padding(vertical = lyricLineSpacing.dp, horizontal = 8.dp)
                        ) {
                            Column(
                                horizontalAlignment = horizontalAlignment,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (index == 0 && currentLyricIndex <= 0) {
                                    LyricInterludeBreathingIndicator(
                                        lyricData = lyricData,
                                        lineIndex = -1,
                                        initialPositionMs = currentPositionMs,
                                        isPlaying = isPlaying,
                                        lyricPositionProvider = lyricPositionProvider,
                                        lyricFontSize = effectiveLyricPageFontSize,
                                        accentPrimary = activePrimaryColor,
                                        accentSecondary = activeSecondaryColor,
                                        horizontalAlignment = horizontalAlignment,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )
                                }

                                val phoneticText = line.phonetic
                                if (showPhonetic && showPhoneticButton && !phoneticText.isNullOrBlank()) {
                                    val phoneticFontSize = (effectiveLyricPageFontSize * lyricTranslationScale * 0.86f).coerceAtLeast(11.5f)
                                    Text(
                                        text = phoneticText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isCurrent) activePrimaryColor.copy(alpha = 0.90f) else Color.White.copy(alpha = (distanceAlpha * 0.68f).coerceIn(0.12f, 0.55f)),
                                            fontSize = phoneticFontSize.sp,
                                            lineHeight = (phoneticFontSize * 1.38f).sp,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = textAlign,
                                            letterSpacing = 0.4.sp
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (isCurrent && line.hasWordTiming && lyricWordByWord) {
                                    DedicatedActiveKaraokeWords(
                                        words = line.words,
                                        initialPositionMs = currentPositionMs,
                                        isPlaying = isPlaying,
                                        lyricPositionProvider = lyricPositionProvider,
                                        lyricFontSize = effectiveLyricPageFontSize,
                                        flowArrangement = flowArrangement,
                                        activePrimaryColor = activePrimaryColor,
                                        activeSecondaryColor = activeSecondaryColor
                                    )
                                } else {
                                    val textColor = if (isCurrent) activePrimaryColor else Color.White.copy(alpha = distanceAlpha)
                                    val textWeight = if (isCurrent || isScrubTarget) FontWeight.ExtraBold else FontWeight.SemiBold
                                    val lineSize = effectiveLyricPageFontSize.sp

                                    val fallbackBlurShadow = if (!supportsHardwareBlur && depthBlurVal >= 0.15f) {
                                        Shadow(
                                            color = Color.White.copy(alpha = (distanceAlpha * 0.85f).coerceIn(0f, 1f)),
                                            offset = Offset.Zero,
                                            blurRadius = (depthBlurVal * 3.0f).coerceAtLeast(1f)
                                        )
                                    } else null

                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = textWeight,
                                            color = if (fallbackBlurShadow != null) Color.Transparent else textColor,
                                            fontSize = lineSize,
                                            lineHeight = (effectiveLyricPageFontSize + 9f).sp,
                                            textAlign = textAlign,
                                            shadow = fallbackBlurShadow
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                val trans = line.translation
                                if (showTranslation && showTranslationButton && !trans.isNullOrBlank()) {
                                    val transFontSize = (effectiveLyricPageFontSize * lyricTranslationScale).coerceAtLeast(13f)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = trans,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = when {
                                                isCurrent -> activePrimaryColor.copy(alpha = 0.90f)
                                                isScrubTarget -> Color.White.copy(alpha = 0.78f)
                                                else -> Color.White.copy(alpha = (distanceAlpha * 0.82f).coerceIn(0.14f, 0.62f))
                                            },
                                            fontSize = transFontSize.sp,
                                            lineHeight = (transFontSize * 1.42f).sp,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = textAlign
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (isCurrent) {
                                    LyricInterludeBreathingIndicator(
                                        lyricData = lyricData,
                                        lineIndex = index,
                                        initialPositionMs = currentPositionMs,
                                        isPlaying = isPlaying,
                                        lyricPositionProvider = lyricPositionProvider,
                                        lyricFontSize = effectiveLyricPageFontSize,
                                        accentPrimary = activePrimaryColor,
                                        accentSecondary = activeSecondaryColor,
                                        horizontalAlignment = horizontalAlignment,
                                        modifier = Modifier.padding(top = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 手动滑动歌词时的「双翼渐隐中空导引线 (Split-Wing Hollow Timeline)」
                androidx.compose.animation.AnimatedVisibility(
                    visible = isUserScrubbing && scrubLineIndex in lyricData.lines.indices,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(160)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(if (isLargeScreen) Modifier.widthIn(max = 580.dp) else Modifier)
                        .fillMaxWidth()
                ) {
                    val targetTimeMs = lyricData.lines.getOrNull(scrubLineIndex)?.startTimeMs ?: 0L
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = Color(0xD90E1624),
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = formatDuration(targetTimeMs),
                                    color = activePrimaryColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(1.5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                activePrimaryColor.copy(alpha = 0.68f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(1.5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                activePrimaryColor.copy(alpha = 0.68f)
                                            )
                                        )
                                    )
                            )
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = activePrimaryColor.copy(alpha = 0.28f),
                                modifier = Modifier
                                    .softCardClickable(shape = RoundedCornerShape(11.dp)) {
                                        isUserScrubbing = false
                                        onLineClick(scrubLineIndex)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "跳转播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "播放",
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 手机端：保持屏幕右侧偏下黄金拇指区竖向按钮组；平板端：统一整合进下方居中紧凑控播栏，避免按钮散落与遮挡右侧空白点击返回区
            val isLargeScreenDock = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
            if (!isLargeScreenDock) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LyricRightDockGlassButton(
                        active = isFavorite,
                        activeColor = Color(0xFFFF4D6D),
                        onClick = onToggleFavorite
                    ) {
                        PlumpHeartCanvasIcon(
                            isFavorite = isFavorite,
                            color = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTranslationButton,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        LyricRightDockGlassButton(
                            active = showTranslation,
                            activeColor = activePrimaryColor,
                            onClick = onToggleTranslation
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "译",
                                    color = if (showTranslation) Color.White else Color.White.copy(alpha = 0.76f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (showTranslation) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .offset(y = 8.dp)
                                            .size(width = 10.dp, height = 2.5.dp)
                                            .clip(CircleShape)
                                            .background(activePrimaryColor)
                                    )
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showPhoneticButton,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        LyricRightDockGlassButton(
                            active = showPhonetic,
                            activeColor = activePrimaryColor,
                            onClick = onTogglePhonetic
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "音",
                                    color = if (showPhonetic) Color.White else Color.White.copy(alpha = 0.76f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (showPhonetic) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .offset(y = 8.dp)
                                            .size(width = 10.dp, height = 2.5.dp)
                                            .clip(CircleShape)
                                            .background(activePrimaryColor)
                                    )
                                }
                            }
                        }
                    }

                    LyricRightDockGlassButton(
                        active = false,
                        activeColor = activePrimaryColor,
                        onClick = onOpenLyricSettings
                    ) {
                        Canvas(modifier = Modifier.size(19.dp)) {
                            val w = size.width
                            val h = size.height
                            val stroke = 2.2.dp.toPx()
                            val c = Color.White.copy(alpha = 0.90f)
                            drawLine(c, Offset(w * 0.14f, h * 0.32f), Offset(w * 0.86f, h * 0.32f), stroke, StrokeCap.Round)
                            drawCircle(activePrimaryColor, radius = 3.0.dp.toPx(), center = Offset(w * 0.65f, h * 0.32f))

                            drawLine(c, Offset(w * 0.14f, h * 0.68f), Offset(w * 0.86f, h * 0.68f), stroke, StrokeCap.Round)
                            drawCircle(c, radius = 3.0.dp.toPx(), center = Offset(w * 0.35f, h * 0.68f))
                        }
                    }
                }
            }
        }

        // 歌词页专属底部极简控播栏：平板端将播放键、进度条与右侧工具按钮紧凑居中编组，彻底告别分散感
        val isLargeScreenBottom = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .then(if (isLargeScreenBottom) Modifier.widthIn(max = 580.dp) else Modifier)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    LyricPageMinimalBottomBar(
                        currentPositionMs = rawPositionMs,
                        positionProvider = rawPositionProvider,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        accentColor = activePrimaryColor,
                        onTogglePlay = onTogglePlay,
                        onSeekTo = onSeekTo
                    )
                }

                if (isLargeScreenBottom) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (showTranslationButton) {
                            LyricRightDockGlassButton(
                                active = showTranslation,
                                activeColor = activePrimaryColor,
                                buttonSize = 46.dp,
                                onClick = onToggleTranslation
                            ) {
                                Text(
                                    text = "译",
                                    color = if (showTranslation) Color.White else Color.White.copy(alpha = 0.78f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        if (showPhoneticButton) {
                            LyricRightDockGlassButton(
                                active = showPhonetic,
                                activeColor = activePrimaryColor,
                                buttonSize = 46.dp,
                                onClick = onTogglePhonetic
                            ) {
                                Text(
                                    text = "音",
                                    color = if (showPhonetic) Color.White else Color.White.copy(alpha = 0.78f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        LyricRightDockGlassButton(
                            active = isFavorite,
                            activeColor = Color(0xFFFF4D6D),
                            buttonSize = 46.dp,
                            onClick = onToggleFavorite
                        ) {
                            PlumpHeartCanvasIcon(
                                isFavorite = isFavorite,
                                color = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(23.dp)
                            )
                        }

                        LyricRightDockGlassButton(
                            active = false,
                            activeColor = activePrimaryColor,
                            buttonSize = 46.dp,
                            onClick = onOpenLyricSettings
                        ) {
                            Canvas(modifier = Modifier.size(22.dp)) {
                                val w = size.width
                                val h = size.height
                                val stroke = 2.4.dp.toPx()
                                val c = Color.White.copy(alpha = 0.90f)
                                drawLine(c, Offset(w * 0.14f, h * 0.32f), Offset(w * 0.86f, h * 0.32f), stroke, StrokeCap.Round)
                                drawCircle(activePrimaryColor, radius = 3.3.dp.toPx(), center = Offset(w * 0.65f, h * 0.32f))

                                drawLine(c, Offset(w * 0.14f, h * 0.68f), Offset(w * 0.86f, h * 0.68f), stroke, StrokeCap.Round)
                                drawCircle(c, radius = 3.3.dp.toPx(), center = Offset(w * 0.35f, h * 0.68f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DedicatedActiveKaraokeWords(
    words: List<com.concept.music.data.model.LyricWord>,
    initialPositionMs: Long,
    isPlaying: Boolean,
    lyricPositionProvider: () -> Long,
    lyricFontSize: Float,
    flowArrangement: Arrangement.Horizontal,
    activePrimaryColor: Color,
    activeSecondaryColor: Color
) {
    val isLifecycleStarted = rememberIsLifecycleStarted()
    var sampledPosMs by remember { mutableLongStateOf(lyricPositionProvider()) }
    LaunchedEffect(initialPositionMs) {
        sampledPosMs = lyricPositionProvider()
    }
    LaunchedEffect(isPlaying, isLifecycleStarted, lyricPositionProvider) {
        if (isPlaying && isLifecycleStarted) {
            while (isActive) {
                withFrameMillis {
                    sampledPosMs = lyricPositionProvider()
                }
            }
        } else {
            sampledPosMs = lyricPositionProvider()
        }
    }
    val livePosMs = sampledPosMs
    val dimColor = Color.White.copy(alpha = 0.40f)

    FlowRow(
        horizontalArrangement = flowArrangement,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        words.forEach { word ->
            val wordProgress = when {
                word.durationMs > 0L -> ((livePosMs - word.startTimeMs).toFloat() / word.durationMs.toFloat()).coerceIn(0f, 1f)
                livePosMs >= word.startTimeMs -> 1f
                else -> 0f
            }
            val isSinging = wordProgress in 0.01f..0.99f

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
            val shimmer = if (isLongNote) 0.85f + 0.15f * sin(wordProgress * PI.toFloat() * 3f) else 1f

            Text(
                text = annotatedWord,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = lyricFontSize.sp,
                    lineHeight = (lyricFontSize + 9f).sp,
                    shadow = if (isLongNote) {
                        Shadow(
                            color = activePrimaryColor.copy(alpha = (0.82f * wave * shimmer).coerceIn(0f, 1f)),
                            offset = Offset.Zero,
                            blurRadius = (20f * wave * shimmer + 6f).coerceAtLeast(1f)
                        )
                    } else null
                ),
                modifier = Modifier.graphicsLayer {
                    translationY = -3.4f * wave * shimmer
                    val s = 1f + 0.038f * wave * shimmer
                    scaleX = s
                    scaleY = s
                }
            )
        }
    }
}

/**
 * 基于酷狗 KRC 精确时间戳的「无框三点流光呼吸指示器 (LyricInterludeBreathingIndicator)」：
 * - 利用 `LyricData.getInterludeProgressAt` 精准探测前奏（≥3s）及行间器乐间奏（≥4s）；
 * - 不使用任何胶囊背景框、不显示剩余秒数，直接悬浮于背景之上，采用与 KRC 逐字歌词完全一致的渲染管线：
 *   按间奏进度 [0 -> 1] 从左至右依次流光染色点亮、叠加柔光霓虹辉光，并伴随正弦波弹性呼吸上浮跳动。
 */
@Composable
internal fun LyricInterludeBreathingIndicator(
    lyricData: LyricData,
    lineIndex: Int,
    initialPositionMs: Long,
    isPlaying: Boolean,
    lyricPositionProvider: () -> Long,
    lyricFontSize: Float = 24f,
    accentPrimary: Color,
    accentSecondary: Color,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    modifier: Modifier = Modifier
) {
    val isLifecycleStarted = rememberIsLifecycleStarted()
    val hasCandidate = remember(lyricData, lineIndex) {
        lyricData.hasInterludeCandidateAt(lineIndex)
    }
    var sampledPosMs by remember(lyricData, lineIndex) { mutableLongStateOf(lyricPositionProvider()) }
    LaunchedEffect(initialPositionMs) {
        sampledPosMs = lyricPositionProvider()
    }
    LaunchedEffect(isPlaying, isLifecycleStarted, hasCandidate, lyricData, lineIndex, lyricPositionProvider) {
        if (isPlaying && isLifecycleStarted && hasCandidate) {
            while (isActive) {
                withFrameMillis {
                    sampledPosMs = lyricPositionProvider()
                }
            }
        } else {
            sampledPosMs = lyricPositionProvider()
        }
    }

    val progress = if (hasCandidate) {
        lyricData.getInterludeProgressAt(sampledPosMs, lineIndex)
    } else -1f
    val visible = progress >= 0f

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.86f, animationSpec = ConceptMotion.expressiveSpring()),
        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.86f, animationSpec = tween(180)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = horizontalAlignment
        ) {
            LyricFloatingBreathingDots(
                progress = progress.coerceIn(0f, 1f),
                isPlaying = isPlaying,
                dotFontSizeSp = (lyricFontSize * 0.68f).coerceIn(14f, 22f),
                activePrimaryColor = accentPrimary,
                activeSecondaryColor = accentSecondary
            )
        }
    }
}

/**
 * 与 KRC 歌词同源渲染的「无框悬浮三点流光呼吸组件 (LyricFloatingBreathingDots)」：
 * - 无任何背景框与文字标签，直接悬浮在播放背景上；
 * - 每个圆点采用与 `DedicatedActiveKaraokeWords` 完全一致的 `SpanStyle(Brush.horizontalGradient)` 逐点扫光染色、
 *   `Shadow` 柔光辉光以及 `graphicsLayer` 正弦呼吸上浮与弹性缩放。
 */
@Composable
internal fun LyricFloatingBreathingDots(
    progress: Float,
    isPlaying: Boolean = true,
    dotFontSizeSp: Float = 16f,
    activePrimaryColor: Color,
    activeSecondaryColor: Color
) {
    val breathPhase: Float
    if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "lyricFloatingDotsBreath")
        val animatedPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1280, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "lyricFloatingDotsWavePhase"
        )
        breathPhase = animatedPhase
    } else {
        breathPhase = 0f
    }

    val dimColor = Color.White.copy(alpha = 0.40f)
    val spacingDp = (dotFontSizeSp * 0.42f).coerceIn(6f, 11f).dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacingDp)
    ) {
        for (i in 0 until 3) {
            val dotProgress = ((progress * 3f) - i).coerceIn(0f, 1f)
            val isLighting = dotProgress in 0.01f..0.99f
            val isLit = dotProgress >= 1f

            val annotatedDot = buildAnnotatedString {
                if (dotProgress <= 0f) {
                    withStyle(SpanStyle(color = dimColor)) {
                        append("●")
                    }
                } else if (isLit) {
                    withStyle(SpanStyle(color = activePrimaryColor)) {
                        append("●")
                    }
                } else {
                    val leftStop = (dotProgress - 0.18f).coerceAtLeast(0f)
                    val rightStop = (dotProgress + 0.18f).coerceAtMost(1f)
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
                        append("●")
                    }
                }
            }

            // 正弦呼吸相位 + 点亮瞬间的弹性波峰，与 KRC 歌词单字上浮缩放完全同构
            val phaseSin = sin(breathPhase - i * 0.92f)
            val breathWave = (phaseSin + 1f) * 0.5f
            val activeArc = if (isLighting) sin(dotProgress * PI.toFloat()) else 0f
            val glowStrength = (activeArc * 0.75f + dotProgress * (0.35f + 0.55f * breathWave)).coerceIn(0f, 1f)

            Text(
                text = annotatedDot,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = dotFontSizeSp.sp,
                    lineHeight = (dotFontSizeSp + 4f).sp,
                    shadow = if (glowStrength > 0.04f) {
                        Shadow(
                            color = activePrimaryColor.copy(alpha = (0.82f * glowStrength).coerceIn(0f, 1f)),
                            offset = Offset.Zero,
                            blurRadius = (18f * glowStrength + 5f).coerceAtLeast(1f)
                        )
                    } else null
                ),
                modifier = Modifier.graphicsLayer {
                    val floatWave = (0.35f + 0.65f * dotProgress) * phaseSin
                    translationY = -3.2f * activeArc - 2.2f * floatWave
                    val s = 0.90f + 0.14f * dotProgress + 0.10f * activeArc + 0.06f * breathWave * dotProgress
                    scaleX = s
                    scaleY = s
                }
            )
        }
    }
}

/**
 * 歌词页右侧悬浮无边框磨砂胶囊按钮（已彻底移除 `.border(...)` 边框线，采用柔和磨砂填充）
 */
@Composable
private fun LyricRightDockGlassButton(
    active: Boolean,
    activeColor: Color,
    buttonSize: androidx.compose.ui.unit.Dp = 42.dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) activeColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.11f),
        animationSpec = tween(200),
        label = "lyricDockGlassBg"
    )
    Surface(
        shape = RoundedCornerShape(if (buttonSize >= 46.dp) 18.dp else 16.dp),
        color = bgColor,
        contentColor = Color.White,
        modifier = Modifier
            .size(buttonSize)
            .softClickable(
                pressScale = 0.86f,
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * 零死区无极丝滑水平进度手势引擎 (Zero-Slop Stepless Scrubbing Gesture):
 * - 使用单一 pointerInput(awaitEachGesture) 统一处理点按直达与水平无极微调滑动，消除双 pointerInput 竞争；
 * - 按下瞬间 (awaitFirstDown) 立即进入交互态，绕过系统 18px touchSlop 死区，实现 120Hz 亚像素跟手滑动。
 */
internal fun Modifier.steplessHorizontalScrubGesture(
    onScrubStart: (Float) -> Unit,
    onScrubMove: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    onScrubCancel: () -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val widthPx = size.width.toFloat().coerceAtLeast(1f)
        val initialFraction = (down.position.x / widthPx).coerceIn(0f, 1f)
        onScrubStart(initialFraction)

        var pointerId = down.id
        var finishedNormally = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
                ?: event.changes.firstOrNull()
                ?: break
            pointerId = change.id
            if (!change.pressed) {
                change.consume()
                finishedNormally = true
                break
            }
            val nextFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
            change.consume()
            onScrubMove(nextFraction)
        }
        if (finishedNormally) {
            onScrubEnd()
        } else {
            onScrubCancel()
        }
    }
}

/**
 * 歌词页专属底部极简控播栏（已去除播放按钮的 `.border(...)` 边框线，并采用圆润饱满自定义播放/暂停矢量图形）
 */
@Composable
private fun LyricPageMinimalBottomBar(
    currentPositionMs: Long,
    positionProvider: () -> Long = { currentPositionMs },
    durationMs: Long,
    isPlaying: Boolean,
    accentColor: Color,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestOnSeekTo by rememberUpdatedState(onSeekTo)
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
        if (!isDragging) {
            sampledPositionMs = sampleWithSeekHold()
        }
    }
    val isLifecycleStarted = rememberIsLifecycleStarted()
    LaunchedEffect(isPlaying, isLifecycleStarted, isDragging, positionProvider) {
        if (isPlaying && isLifecycleStarted && !isDragging) {
            while (isActive) {
                sampledPositionMs = sampleWithSeekHold()
                kotlinx.coroutines.delay(66L)
            }
        } else if (!isDragging) {
            sampledPositionMs = sampleWithSeekHold()
        }
    }

    val livePositionMs = sampledPositionMs
    val currentFraction = if (durationMs > 0L) {
        (livePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val effectiveFraction = if (isDragging) dragFraction else currentFraction
    val displayTimeMs = if (isDragging) (dragFraction * durationMs).toLong() else livePositionMs

    val railHeightDp by animateDpAsState(
        targetValue = if (isDragging) 7.dp else 3.5.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "lyricSilkRailHeight"
    )
    val cursorWidthDp by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 9.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "lyricSilkCursorW"
    )
    val cursorHeightDp by animateDpAsState(
        targetValue = if (isDragging) 9.dp else 5.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "lyricSilkCursorH"
    )
    val morphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "lyricBarPlayPauseMorph"
    )

    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：无边框柔光圆润播放/暂停按钮（彻底移除 border 线条）
        Surface(
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.26f),
            contentColor = Color.White,
            modifier = Modifier
                .size(if (isLargeScreen) 52.dp else 46.dp)
                .softClickable(
                    pressScale = 0.86f,
                    onClick = onTogglePlay
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                val safeMorph = morphProgress.coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .size(if (isLargeScreen) 23.dp else 20.dp)
                        .graphicsLayer {
                            val bounce = (1f - 0.08f * sin(safeMorph * PI.toFloat())).coerceIn(0.88f, 1.10f)
                            scaleX = bounce
                            scaleY = bounce
                        }
                ) {
                    drawContinuousPlayPauseMorph(
                        morphProgress = safeMorph,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(displayTimeMs),
                    color = if (isDragging) accentColor.readableOnDarkSurface(0.46f) else Color.White.copy(alpha = 0.56f),
                    fontSize = 10.5.sp,
                    fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = formatDuration(durationMs),
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .steplessHorizontalScrubGesture(
                        onScrubStart = { startF ->
                            isDragging = true
                            dragFraction = startF
                        },
                        onScrubMove = { moveF ->
                            dragFraction = moveF
                        },
                        onScrubEnd = {
                            val targetMs = (dragFraction * latestDurationMs).toLong().coerceIn(0L, latestDurationMs.coerceAtLeast(0L))
                            settledSeekTargetMs = targetMs
                            settledSeekTimestampMs = android.os.SystemClock.elapsedRealtime()
                            sampledPositionMs = targetMs
                            latestOnSeekTo(targetMs)
                            isDragging = false
                        },
                        onScrubCancel = {
                            isDragging = false
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    if (w <= 2f || h <= 2f) return@Canvas
                    val cy = h / 2f
                    val railH = railHeightDp.toPx().coerceIn(1f, h)
                    val r = CornerRadius(railH / 2f, railH / 2f)

                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.14f),
                        topLeft = Offset(0f, cy - railH / 2f),
                        size = Size(w, railH),
                        cornerRadius = r
                    )

                    val activeW = (w * effectiveFraction.coerceIn(0f, 1f)).coerceIn(0f, w)
                    if (activeW > 1f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.45f),
                                    accentColor,
                                    Color.White
                                ),
                                startX = 0f,
                                endX = activeW.coerceAtLeast(1f)
                            ),
                            topLeft = Offset(0f, cy - railH / 2f),
                            size = Size(activeW, railH),
                            cornerRadius = r
                        )
                    }

                    val cursorW = cursorWidthDp.toPx()
                    val cursorH = cursorHeightDp.toPx()
                    val cursorLeft = (activeW - cursorW / 2f).coerceIn(0f, (w - cursorW).coerceAtLeast(0f))
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(cursorLeft, cy - cursorH / 2f),
                        size = Size(cursorW, cursorH),
                        cornerRadius = CornerRadius(cursorH / 2f, cursorH / 2f)
                    )
                }
            }
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

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun detectAudioOutputRouteLabel(context: Context): String {
    return runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "内置扬声器"
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        val usb = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        val wired = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        when {
            bt != null -> {
                val name = bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "蓝牙音频"
                "蓝牙 · ${name.take(12)}"
            }
            usb != null -> "USB DAC · 高解析独占"
            wired != null -> "Hi-Fi 有线耳机"
            else -> "立体声扬声器"
        }
    }.getOrDefault("立体声扬声器")
}
