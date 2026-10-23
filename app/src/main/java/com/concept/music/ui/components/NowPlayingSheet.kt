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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.SystemBarAppearanceEffect
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.conceptSheetSpringEntrance
import com.concept.music.ui.theme.isConceptDarkTheme
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
internal fun hasDeviceMotionOrientationPermission(context: Context, appToggleEnabled: Boolean): Boolean {
    return appToggleEnabled
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
    onClearQueue: () -> Unit
) {
    if (song == null) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context as? Activity
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

    val audioEffectState by AudioEffectManager.state.collectAsState()
    val systemLyricState by SystemLyricBridge.state.collectAsState()

    val enterLandscape: (Boolean) -> Unit = { fromSensor ->
        if (!showLyricView) {
            isLandscapeMode = true
            enteredLandscapeViaSensor = fromSensor
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    val exitLandscape: () -> Unit = {
        isLandscapeMode = false
        enteredLandscapeViaSensor = false
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // 严格限制横屏自动触发范围：
    // 1. 仅在封面播放页 (!showLyricView) 且未打开其它弹窗时才允许通过横放手机进入横屏；
    // 2. 必须同时满足：已开启「设备动作与方向」权限 + 手机系统「自动旋转」开关处于打开状态。
    val canSensorTriggerLandscape = !showLyricView &&
        !showQueueSheet &&
        !showMoreControlSheet &&
        !showLyricSettingsDialog &&
        !showLyricPosterDialog &&
        autoRotateSensorEnabled &&
        motionOrientationPermissionEnabled

    DisposableEffect(context, canSensorTriggerLandscape, isLandscapeMode, enteredLandscapeViaSensor) {
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

    // 退出播放页时强制恢复竖屏与系统状态栏显示
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let { win ->
                WindowCompat.getInsetsController(win, view).show(
                    WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
                )
            }
        }
    }

    val effectiveLandscape = !showLyricView && (isLandscapeMode || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)

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
            showQueueSheet -> showQueueSheet = false
            showLyricSettingsDialog -> showLyricSettingsDialog = false
            effectiveLandscape -> exitLandscape()
            showLyricView -> showLyricView = false
            else -> onDismiss()
        }
    }

    SystemBarAppearanceEffect(forceDarkBars = true)

    var verticalDragOffset by remember { mutableFloatStateOf(0f) }
    val animatedVerticalOffset by animateFloatAsState(
        targetValue = verticalDragOffset.coerceAtLeast(0f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playerVerticalOffset"
    )

    // 仅在黑胶封面模式下驱动连续旋转相位；圆角方形封面保持静态不旋转
    val rotationAnim = remember(song.id) { Animatable(0f) }
    LaunchedEffect(isPlaying, song.id, coverStyle) {
        if (isPlaying && coverStyle == CoverStyle.VINYL) {
            while (isActive) {
                val target = rotationAnim.value + 360f
                rotationAnim.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 20000, easing = LinearEasing)
                )
            }
        }
    }

    // 封面智能动态流光取色
    var extractedPalette by remember {
        mutableStateOf(
            CoverExtractedPalette(
                meshPrimary = Color(0xFF0E1A2F),
                meshSecondary = Color(0xFF13233F),
                dominantAccent = ConceptPrimary,
                secondaryAccent = ConceptCyanAccent
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
                dominantAccent = ConceptPrimary,
                secondaryAccent = ConceptCyanAccent
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

    val effectiveLyricPositionMs = (currentPositionMs + lyricOffsetMs).coerceAtLeast(0L)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, animatedVerticalOffset.roundToInt()) }
            .background(Color(0xFF080C14))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        val dimAlpha = if (showLyricView || effectiveLandscape) lyricBackgroundDim.coerceIn(0.40f, 0.92f) else 0.78f

        // 1. 多模式可切换沉浸式背景层
        NowPlayingSelectableBackground(
            coverUrl = song.coverUrl,
            backgroundStyle = playerBackgroundStyle,
            isPlaying = isPlaying,
            showLyricView = showLyricView || effectiveLandscape,
            dimAlpha = dimAlpha,
            meshColorPrimary = meshColorPrimary,
            meshColorSecondary = meshColorSecondary,
            accentPrimary = activeAccentColor,
            accentSecondary = activeSecondaryAccent
        )

        AnimatedContent(
            targetState = effectiveLandscape,
            transitionSpec = {
                (fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.94f, animationSpec = tween(320, easing = FastOutSlowInEasing)))
                    .togetherWith(
                        fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.94f, animationSpec = tween(220, easing = FastOutSlowInEasing))
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
                    onSelectPlayerBackgroundStyle = onSelectPlayerBackgroundStyle
                )
            } else {
                // 2. 竖屏主体内容区：顶部返回按钮与歌名区域适度上移，使视觉重心更紧凑自然
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 22.dp, end = 22.dp, top = 0.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(showLyricView) {
                            detectVerticalDragGestures(
                                onDragStart = { verticalDragOffset = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    if (dragAmount > 0f || verticalDragOffset > 0f) {
                                        change.consume()
                                        verticalDragOffset = (verticalDragOffset + dragAmount).coerceAtLeast(0f)
                                    }
                                },
                                onDragCancel = { verticalDragOffset = 0f },
                                onDragEnd = {
                                    if (verticalDragOffset > 150f) {
                                        verticalDragOffset = 0f
                                        onDismiss()
                                    } else {
                                        verticalDragOffset = 0f
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

                // 页面主体：通过丝滑转场切换「封面播放页」与「重构专属歌词页」
                AnimatedContent(
                    targetState = showLyricView,
                    transitionSpec = {
                        (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.95f, animationSpec = tween(260, easing = FastOutSlowInEasing))) togetherWith
                            (fadeOut(tween(190, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 0.96f, animationSpec = tween(190, easing = FastOutSlowInEasing)))
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
                            lyricData = lyricData,
                            currentLyricIndex = currentLyricIndex,
                            currentPositionMs = effectiveLyricPositionMs,
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
                                val nextEnabled = !systemLyricState.floatingCapsuleEnabled
                                SystemLyricBridge.setFloatingCapsuleEnabled(nextEnabled)
                                val msg = if (nextEnabled) "已开启桌面歌词" else "已关闭桌面歌词"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            onEnterLandscape = { enterLandscape(false) },
                            onOpenMoreSheet = { showMoreControlSheet = true },
                            onSeekTo = onSeekTo,
                            onTogglePlay = onTogglePlay,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onCyclePlayMode = onCyclePlayMode,
                            onOpenQueue = { showQueueSheet = true },
                            onOpenComments = onOpenComments
                        )
                    } else {
                        RedesignedDedicatedLyricPage(
                            lyricData = lyricData,
                            currentLyricIndex = currentLyricIndex,
                            currentPositionMs = effectiveLyricPositionMs,
                            rawPositionMs = currentPositionMs,
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
                            dynamicCoverAccent = activeAccentColor,
                            dynamicCoverSecondary = activeSecondaryAccent,
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
                showQueueSheet = false
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
                onOpenArtist(song)
            },
            onOpenAlbum = {
                showMoreControlSheet = false
                onOpenAlbum(song)
            },
            onDismiss = { showMoreControlSheet = false }
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
                showQualityDialog = false
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
            dynamicAccentColor = activeAccentColor,
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
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                onCancelSleepTimer()
                showSleepTimerDialog = false
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

        // 歌曲技术参数详情弹窗
        if (showDetailSheet && songDetail != null) {
            SongDetailSheet(
                detail = songDetail,
                onDismiss = { showDetailSheet = false }
            )
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
    accentPrimary: Color,
    accentSecondary: Color
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "bgModeAnim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 14000 else 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bgPhase"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = if (isPlaying) 1.16f else 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bgPulse"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        when (backgroundStyle) {
            PlayerBackgroundStyle.COVER_BLUR -> {
                if (coverUrl.isNotEmpty()) {
                    val bgImageModel = remember(coverUrl) {
                        ImageRequest.Builder(context)
                            .data(coverUrl)
                            .size(24, 24)
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
                if (coverUrl.isNotEmpty()) {
                    val bgImageModel = remember(coverUrl) {
                        ImageRequest.Builder(context)
                            .data(coverUrl)
                            .size(24, 24)
                            .allowHardware(false)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = bgImageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.42f }
                    )
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = Color(0xFF070B14).copy(alpha = 0.88f))
                    val w = size.width
                    val h = size.height
                    val orb1 = Offset(w * (0.28f + 0.22f * cos(phase)), h * (0.24f + 0.14f * sin(phase)))
                    val orb2 = Offset(w * (0.74f + 0.20f * sin(phase * 0.8f + 1.2f)), h * (0.50f + 0.16f * cos(phase * 0.9f)))
                    val orb3 = Offset(w * (0.42f + 0.24f * sin(phase * 1.1f + 2.4f)), h * (0.78f + 0.12f * cos(phase * 0.7f)))

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentPrimary.copy(alpha = 0.38f), Color.Transparent),
                            center = orb1,
                            radius = w * 0.75f * pulse
                        ),
                        center = orb1,
                        radius = w * 0.75f * pulse
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentSecondary.copy(alpha = 0.32f), Color.Transparent),
                            center = orb2,
                            radius = w * 0.70f
                        ),
                        center = orb2,
                        radius = w * 0.70f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(meshColorPrimary.copy(alpha = 0.55f), Color.Transparent),
                            center = orb3,
                            radius = w * 0.82f
                        ),
                        center = orb3,
                        radius = w * 0.82f
                    )
                    if (showLyricView) {
                        drawRect(color = Color(0xFF060911).copy(alpha = (dimAlpha * 0.55f).coerceIn(0.20f, 0.78f)))
                    }
                }
            }

            PlayerBackgroundStyle.RHYTHM_HALO -> {
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
                        val r = baseR * (1f + ring * 0.24f) * pulse
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentPrimary.copy(alpha = 0.22f / ring),
                                    accentSecondary.copy(alpha = 0.12f / ring),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = r
                            ),
                            center = center,
                            radius = r
                        )
                        drawCircle(
                            color = accentPrimary.copy(alpha = 0.15f / ring),
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
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF070B16),
                                meshColorPrimary.copy(alpha = 0.78f),
                                Color(0xFF04060C)
                            )
                        )
                    )
                    val w = size.width
                    val h = size.height
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentPrimary.copy(alpha = 0.24f), Color.Transparent),
                            center = Offset(w * 0.32f, h * 0.34f),
                            radius = w * 0.68f
                        ),
                        center = Offset(w * 0.32f, h * 0.34f),
                        radius = w * 0.68f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentSecondary.copy(alpha = 0.18f), Color.Transparent),
                            center = Offset(w * 0.72f, h * 0.64f),
                            radius = w * 0.64f
                        ),
                        center = Offset(w * 0.72f, h * 0.64f),
                        radius = w * 0.64f
                    )
                    for (i in 0 until 36) {
                        val seedX = (i * 73 + 19) % 97 / 97f
                        val seedY = (i * 41 + 11) % 89 / 89f
                        val driftY = (seedY - phase / (2f * PI.toFloat()) * (0.14f + (i % 3) * 0.07f) + 1f) % 1f
                        val twinkle = (0.35f + 0.65f * abs(sin(phase * (1.2f + (i % 4) * 0.35f) + i))).coerceIn(0.15f, 1f)
                        val sx = w * (seedX + 0.03f * sin(phase + i)).coerceIn(0.04f, 0.96f)
                        val sy = h * driftY
                        val sr = (1.1f + (i % 3) * 0.85f).dp.toPx()
                        val col = when (i % 3) {
                            0 -> accentSecondary
                            1 -> accentPrimary
                            else -> Color.White
                        }
                        drawCircle(
                            color = col.copy(alpha = 0.55f * twinkle),
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-6).dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBackOrDismiss,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "返回",
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 20.sp,
                    letterSpacing = 0.2.sp
                ),
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 44.dp)
                    .basicMarquee()
            )
        }

        Text(
            text = artist.ifBlank { "未知歌手" },
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )

        AnimatedVisibility(
            visible = !isLyricPage,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.07f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (outputRouteLabel.contains("蓝牙") || outputRouteLabel.contains("耳机")) {
                                Icons.Rounded.Headphones
                            } else {
                                Icons.Rounded.SpeakerGroup
                            },
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.62f),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = outputRouteLabel,
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGoldQuality) VipGold.copy(alpha = 0.18f) else ConceptPrimary.copy(alpha = 0.16f),
                    modifier = Modifier.softCardClickable(
                        shape = RoundedCornerShape(8.dp),
                        onClick = onQualityClick
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = qualityLabel,
                            color = if (isGoldQuality) VipGold else ConceptPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "切换音质",
                            tint = if (isGoldQuality) VipGold else ConceptPrimary,
                            modifier = Modifier.size(11.dp)
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
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
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
    onOpenComments: () -> Unit
) {
    val favScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.14f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "coverFavScale"
    )

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
                        onDoubleTap = { onTriggerDoubleTapHeart() }
                    )
                }
                .pointerInput(song.id) {
                    var dragDeltaX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragDeltaX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragDeltaX += dragAmount
                        },
                        onDragEnd = {
                            if (dragDeltaX < -105f) {
                                onNext()
                            } else if (dragDeltaX > 105f) {
                                onPrevious()
                            }
                            dragDeltaX = 0f
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

            Box(contentAlignment = Alignment.Center) {
                when (coverStyle) {
                    CoverStyle.VINYL -> {
                        MasterVinylTurntableView(
                            coverUrl = song.coverUrl,
                            isPlaying = isPlaying,
                            accentColor = accentPrimary,
                            rotationProvider = rotationProvider
                        )
                    }
                    CoverStyle.SQUARE -> {
                        StaticRoundedSquareCoverView(
                            coverUrl = song.coverUrl,
                            isPlaying = isPlaying,
                            accentColor = accentPrimary
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
                        modifier = Modifier.size(88.dp)
                    )
                }
            }

            // 右侧三按钮整体再往上移动，与下方增高后的预览歌词区拉开舒适呼吸空间（第1：收藏，第2：添加到歌单，第3：歌词海报）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
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
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .softClickable(
                            pressScale = 0.84f,
                            onClick = onAddToPlaylist
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PlumpPlaylistAddCanvasIcon(
                        color = Color.White.copy(alpha = 0.88f),
                        accentColor = accentPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .softClickable(
                            pressScale = 0.84f,
                            onClick = onOpenLyricPoster
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PlumpPosterSparkleCanvasIcon(
                        color = Color.White.copy(alpha = 0.86f),
                        accentColor = accentPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. 全宽沉浸式歌词预览区（增大字号、加高显示区域，且逐字流光效果与歌词页完全统一）
        FullWidthLyricPreviewSection(
            lyricData = lyricData,
            currentLyricIndex = currentLyricIndex,
            currentPositionMs = currentPositionMs,
            showTranslation = showTranslation && showTranslationButton,
            showPhonetic = showPhonetic && showPhoneticButton,
            lyricWordByWord = lyricWordByWord,
            lyricHighlightTheme = lyricHighlightTheme,
            accentPrimary = accentPrimary,
            accentSecondary = accentSecondary,
            onLyricClick = onOpenLyricView
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. 进度条上方功能栏：「横屏播放、桌面歌词、蝰蛇音效、带计数评论按钮、更多」
        // （已按需求将【音效与桌面歌词互换位置】、【评论与横屏播放互换位置】）
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
                    accentColor = accentPrimary
                )
            }

            PlayerActionIconButton(
                onClick = onToggleDesktopLyric
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PlumpDesktopLyricCanvasIcon(
                        enabled = desktopLyricEnabled,
                        color = if (desktopLyricEnabled) accentPrimary else Color.White.copy(alpha = 0.88f),
                        accentColor = accentSecondary
                    )
                    if (desktopLyricEnabled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-2).dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentSecondary)
                        )
                    }
                }
            }

            PlayerActionIconButton(
                onClick = onOpenAudioEffect
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PlumpAudioWaveCanvasIcon(
                        color = if (viperEnabled) accentPrimary else Color.White.copy(alpha = 0.88f),
                        accentColor = if (viperEnabled) accentSecondary else accentPrimary
                    )
                    if (viperEnabled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-2).dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentSecondary)
                        )
                    }
                }
            }

            RedesignedCommentCountButton(
                commentCount = commentCount,
                accentColor = accentPrimary,
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
            durationMs = durationMs,
            climaxStartMs = climaxStartMs,
            climaxEndMs = climaxEndMs,
            highlightClimaxRange = highlightClimaxRange,
            isPlaying = isPlaying,
            playMode = playMode,
            accentPrimary = accentPrimary,
            accentSecondary = accentSecondary,
            onSeekTo = onSeekTo,
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
private fun RedesignedCommentCountButton(
    commentCount: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    val countText = formatPlayerCommentCount(commentCount)
    Box(
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 52.dp)
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
                .size(23.dp)
                .align(if (countText.isNotEmpty()) Alignment.CenterStart else Alignment.Center)
                .offset(y = if (countText.isNotEmpty()) 2.dp else 0.dp)
        ) {
            val w = size.width
            val h = size.height
            val stroke = 2.1.dp.toPx()
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
            drawCircle(accentColor, radius = 1.5.dp.toPx(), center = Offset(w * 0.36f, h * 0.49f))
            drawCircle(c, radius = 1.5.dp.toPx(), center = Offset(w * 0.50f, h * 0.49f))
            drawCircle(c, radius = 1.5.dp.toPx(), center = Offset(w * 0.64f, h * 0.49f))
        }

        // 右上角悬浮无遮挡角标（不遮盖评论气泡主体，也不被父容器裁切）
        if (countText.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = accentColor.copy(alpha = 0.24f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 17.dp, top = 2.dp)
            ) {
                Text(
                    text = countText,
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.5.dp)
                )
            }
        }
    }
}

@Composable
private fun PlumpLandscapeModeCanvasIcon(
    color: Color,
    accentColor: Color
) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
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
private fun PlayerActionIconButton(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
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
private fun MasterVinylTurntableView(
    coverUrl: String,
    isPlaying: Boolean,
    accentColor: Color,
    rotationProvider: () -> Float
) {
    val armAngle by animateFloatAsState(
        targetValue = if (isPlaying) 25f else 2f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessLow
        ),
        label = "masterTonearmAngle"
    )

    Box(
        modifier = Modifier.size(width = 304.dp, height = 296.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(282.dp)
                .shadow(
                    elevation = 28.dp,
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
                .size(270.dp)
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
                        style = Stroke(width = if (i % 4 == 0) 1.1.dp.toPx() else 0.6.dp.toPx())
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
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .size(166.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF181C26)),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(54.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xAA0A0D14)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF07090E))
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .size(144.dp)
                .align(Alignment.TopEnd)
        ) {
            if (size.minDimension <= 2f) return@Canvas
            val pivot = Offset(size.width * 0.76f, size.height * 0.17f)

            drawCircle(
                color = Color(0xFF0B0E16).copy(alpha = 0.7f),
                radius = 20.dp.toPx(),
                center = pivot
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3A4252), Color(0xFF1B202B)),
                    center = pivot,
                    radius = 18.dp.toPx()
                ),
                radius = 17.dp.toPx(),
                center = pivot
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = 17.dp.toPx(),
                center = pivot,
                style = Stroke(width = 1.dp.toPx())
            )

            rotate(degrees = armAngle, pivot = pivot) {
                val weightStart = Offset(pivot.x + 4.dp.toPx(), pivot.y - 14.dp.toPx())
                val weightEnd = Offset(pivot.x + 8.dp.toPx(), pivot.y - 24.dp.toPx())
                drawLine(
                    color = Color(0xFF6C7689),
                    start = pivot,
                    end = weightEnd,
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF282F3D),
                    start = weightStart,
                    end = weightEnd,
                    strokeWidth = 10.dp.toPx(),
                    cap = StrokeCap.Butt
                )

                val armEnd = Offset(pivot.x - 36.dp.toPx(), pivot.y + 88.dp.toPx())
                val sCurvePath = Path().apply {
                    moveTo(pivot.x, pivot.y)
                    cubicTo(
                        pivot.x - 4.dp.toPx(), pivot.y + 30.dp.toPx(),
                        pivot.x - 24.dp.toPx(), pivot.y + 56.dp.toPx(),
                        armEnd.x, armEnd.y
                    )
                }
                drawPath(
                    path = sCurvePath,
                    color = Color.Black.copy(alpha = 0.45f),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = sCurvePath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFE2E8F0),
                            Color(0xFF94A3B8),
                            Color(0xFFCBD5E1)
                        ),
                        start = pivot,
                        end = armEnd
                    ),
                    style = Stroke(width = 3.8.dp.toPx(), cap = StrokeCap.Round)
                )

                val headTip = Offset(armEnd.x - 10.dp.toPx(), armEnd.y + 14.dp.toPx())
                drawLine(
                    color = Color(0xFF1E293B),
                    start = armEnd,
                    end = headTip,
                    strokeWidth = 7.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = VipGold,
                    start = Offset(armEnd.x - 2.dp.toPx(), armEnd.y + 3.dp.toPx()),
                    end = Offset(headTip.x + 2.dp.toPx(), headTip.y - 2.dp.toPx()),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = if (isPlaying) ConceptCyanAccent else Color.White.copy(alpha = 0.6f),
                    radius = 2.5.dp.toPx(),
                    center = headTip
                )
            }

            drawCircle(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFE2E8F0), Color(0xFF64748B))
                ),
                radius = 7.dp.toPx(),
                center = pivot
            )
        }
    }
}

@Composable
private fun StaticRoundedSquareCoverView(
    coverUrl: String,
    isPlaying: Boolean,
    accentColor: Color
) {
    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.93f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "squareCoverScale"
    )

    Box(
        modifier = Modifier
            .size(278.dp)
            .graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
            }
            .shadow(
                elevation = if (isPlaying) 32.dp else 16.dp,
                shape = RoundedCornerShape(30.dp),
                spotColor = accentColor.copy(alpha = 0.65f)
            )
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF151A26)),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullWidthLyricPreviewSection(
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    showTranslation: Boolean,
    showPhonetic: Boolean,
    lyricWordByWord: Boolean = true,
    lyricHighlightTheme: LyricHighlightTheme = LyricHighlightTheme.ICE_BLUE,
    accentPrimary: Color,
    accentSecondary: Color = ConceptCyanAccent,
    onLyricClick: () -> Unit
) {
    val activePrimaryColor = if (lyricHighlightTheme == LyricHighlightTheme.DYNAMIC_COVER) {
        lerp(accentPrimary, Color.White, 0.16f)
    } else {
        Color(lyricHighlightTheme.primaryHex)
    }
    val activeSecondaryColor = if (lyricHighlightTheme == LyricHighlightTheme.DYNAMIC_COVER) {
        accentSecondary
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onLyricClick
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        if (!phoneticText.isNullOrBlank()) {
            Text(
                text = phoneticText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = activePrimaryColor.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(3.dp))
        }

        if (currentLineObj != null && currentLineObj.hasWordTiming && currentLineObj.words.isNotEmpty() && lyricWordByWord) {
            FlowRow(
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                currentLineObj.words.forEach { word ->
                    val wordProgress = when {
                        word.durationMs > 0L -> ((currentPositionMs - word.startTimeMs).toFloat() / word.durationMs.toFloat()).coerceIn(0f, 1f)
                        currentPositionMs >= word.startTimeMs -> 1f
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
                    val isLongNote = word.durationMs >= 750L && isSinging

                    Text(
                        text = annotatedWord,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.5.sp,
                            lineHeight = 26.5.sp,
                            shadow = if (isLongNote) {
                                Shadow(
                                    color = activePrimaryColor.copy(alpha = (0.80f * wave).coerceIn(0f, 1f)),
                                    offset = Offset.Zero,
                                    blurRadius = (18f * wave + 5f).coerceAtLeast(1f)
                                )
                            } else null
                        ),
                        modifier = Modifier.graphicsLayer {
                            translationY = -2.8f * wave
                            val s = 1f + 0.032f * wave
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
                    fontSize = 19.5.sp,
                    lineHeight = 26.5.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subText.ifBlank { "♪ • • •" },
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PlayerControlsSection(
    currentPositionMs: Long,
    durationMs: Long,
    climaxStartMs: Long = 0L,
    climaxEndMs: Long = 0L,
    highlightClimaxRange: Boolean = false,
    isPlaying: Boolean,
    playMode: PlayMode,
    accentPrimary: Color = ConceptPrimary,
    accentSecondary: Color = ConceptCyanAccent,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onQueueClick: () -> Unit
) {
    val context = LocalContext.current
    var isInteractingWithBar by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val effectiveFraction = if (isInteractingWithBar) dragFraction else currentFraction
    val displayCurrentMs = if (isInteractingWithBar) (dragFraction * durationMs).toLong() else currentPositionMs

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
                isInteractingWithBar = true
                dragFraction = startFrac
            },
            onDrag = { newFrac ->
                dragFraction = newFrac
            },
            onDragEnd = {
                onSeekTo((dragFraction * durationMs).toLong())
                isInteractingWithBar = false
            }
        )

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
                    color = if (isInteractingWithBar) accentPrimary else Color.White.copy(alpha = 0.56f),
                    fontSize = 11.5.sp,
                    fontWeight = if (isInteractingWithBar) FontWeight.Bold else FontWeight.Medium
                )
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.56f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedPlayModeButton(
                playMode = playMode,
                accentColor = accentPrimary,
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
                accentColor = accentPrimary,
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
    onDragEnd: () -> Unit
) {
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

    val infiniteTransition = rememberInfiniteTransition(label = "cometOpticalFlow")
    val cometPulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cometBreath"
    )
    val stardustPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 1400 else 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stardustPhase"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 22.dp else 40.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val startF = (offset.x / w).coerceIn(0f, 1f)
                        onDragStart(startF)
                        if (tryAwaitRelease()) {
                            onDragEnd()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        onDragStart((offset.x / w).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        onDrag((change.position.x / w).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            },
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
private fun PlumpPosterSparkleCanvasIcon(
    color: Color,
    accentColor: Color
) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
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
private fun PlumpPlaylistAddCanvasIcon(
    color: Color,
    accentColor: Color
) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx()
        drawLine(color, Offset(w * 0.14f, h * 0.26f), Offset(w * 0.78f, h * 0.26f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.56f, h * 0.50f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.74f), Offset(w * 0.46f, h * 0.74f), stroke, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.74f, h * 0.52f), Offset(w * 0.74f, h * 0.84f), stroke, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.58f, h * 0.68f), Offset(w * 0.90f, h * 0.68f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun PlumpAudioWaveCanvasIcon(
    color: Color,
    accentColor: Color
) {
    Canvas(modifier = Modifier.size(24.dp)) {
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
private fun PlumpDesktopLyricCanvasIcon(
    enabled: Boolean,
    color: Color,
    accentColor: Color
) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.3.dp.toPx()
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
private fun PlumpMoreDotsCanvasIcon(
    color: Color
) {
    Canvas(modifier = Modifier.size(24.dp)) {
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

    Box(
        modifier = Modifier
            .size(46.dp)
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
                    .size(25.dp)
                    .graphicsLayer {
                        rotationZ = if (mode == PlayMode.REPEAT_ALL) spinDeg % 360f else 0f
                    }
            ) {
                val w = size.width
                val h = size.height
                val stroke = 2.4.dp.toPx()
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

    Box(
        modifier = Modifier
            .size(52.dp)
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
        Canvas(modifier = Modifier.size(30.dp)) {
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
 * - 采用封面动态取色 (`accentPrimary` -> `accentSecondary`) 渲染按钮背景与呼吸光环；
 * - 彻底修复原 `rotationZ = -28f` 导致播放图标朝下歪斜的 Bug；
 * - 采用圆润饱满的胖三角与胖胶囊暂停柱。
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
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playPauseMorph"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "playButtonAura")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isPlaying) 1.12f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )

    Box(
        modifier = Modifier.size(76.dp),
        contentAlignment = Alignment.Center
    ) {
        // 动态封面色彩呼吸光环
        Box(
            modifier = Modifier
                .size(74.dp)
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
                .size(66.dp)
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
                .border(
                    width = 1.2.dp,
                    color = Color.White.copy(alpha = 0.32f),
                    shape = CircleShape
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
                        .size(28.dp)
                        .graphicsLayer {
                            val bounce = (1f - 0.10f * sin(safeMorph * PI.toFloat())).coerceIn(0.86f, 1.12f)
                            scaleX = bounce
                            scaleY = bounce
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    if (w <= 2f || h <= 2f) return@Canvas

                    if (safeMorph > 0.5f) {
                        val pauseScale = 0.72f + (safeMorph - 0.5f) * 0.56f
                        val barW = w * 0.24f
                        val barH = h * 0.72f * pauseScale
                        val topY = (h - barH) / 2f
                        val leftX = w * 0.18f
                        val rightX = w - leftX - barW
                        val radius = CornerRadius(barW / 2f, barW / 2f)

                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(leftX, topY),
                            size = Size(barW, barH),
                            cornerRadius = radius
                        )
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(rightX, topY),
                            size = Size(barW, barH),
                            cornerRadius = radius
                        )
                    } else {
                        val playScale = 1f - safeMorph * 0.32f
                        val triPath = createPlumpPlayTrianglePath(
                            centerX = w * 0.54f,
                            centerY = h * 0.50f,
                            size = min(w, h) * 0.84f * playScale
                        )
                        drawPath(
                            path = triPath,
                            color = Color.White
                        )
                    }
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
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .softClickable(
                pressScale = 0.84f,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(25.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2.5.dp.toPx()
            val tint = Color.White.copy(alpha = 0.90f)

            drawLine(tint, Offset(w * 0.14f, h * 0.26f), Offset(w * 0.78f, h * 0.26f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.62f, h * 0.50f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.14f, h * 0.74f), Offset(w * 0.48f, h * 0.74f), stroke, StrokeCap.Round)

            val noteCenter = Offset(w * 0.72f, h * 0.74f)
            drawCircle(accentColor, radius = 3.4.dp.toPx(), center = noteCenter)
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
        lerp(dynamicCoverAccent, Color.White, 0.16f)
    } else {
        Color(lyricHighlightTheme.primaryHex)
    }
    val activeSecondaryColor = if (lyricHighlightTheme == LyricHighlightTheme.DYNAMIC_COVER) {
        dynamicCoverSecondary
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
                        listState.animateScrollToItem(
                            index = currentLyricIndex,
                            scrollOffset = -210
                        )
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

                // 平衡左右内边距，使居中对齐严格处于视觉正中心
                val startPad = when (lyricAlignment) {
                    LyricAlignment.CENTER -> 28.dp
                    LyricAlignment.RIGHT -> 16.dp
                    LyricAlignment.LEFT -> 8.dp
                }
                val endPad = when (lyricAlignment) {
                    LyricAlignment.CENTER -> 28.dp
                    LyricAlignment.RIGHT -> 54.dp
                    LyricAlignment.LEFT -> 50.dp
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = startPad, end = endPad),
                    contentPadding = PaddingValues(vertical = 170.dp),
                    horizontalAlignment = horizontalAlignment
                ) {
                    itemsIndexed(lyricData.lines, key = { idx, item -> "${idx}_${item.startTimeMs}" }) { index, line ->
                        val isCurrent = index == currentLyricIndex
                        val isScrubTarget = isUserScrubbing && index == scrubLineIndex
                        val distance = abs(index - currentLyricIndex)

                        val lineScale by animateFloatAsState(
                            targetValue = when {
                                isCurrent -> 1.03f
                                isScrubTarget -> 1.0f
                                else -> (1.0f - distance * 0.024f).coerceAtLeast(0.89f)
                            },
                            animationSpec = tween(durationMillis = 260),
                            label = "lyricLineScale"
                        )

                        val depthBlurVal = if (lyricBlurEnabled && !isUserScrubbing && !isCurrent) {
                            when (distance) {
                                1 -> 0.8f * lyricBlurIntensity
                                2 -> 1.6f * lyricBlurIntensity
                                3 -> 2.4f * lyricBlurIntensity
                                else -> 3.2f * lyricBlurIntensity
                            }.coerceIn(0f, 5.5f)
                        } else 0f

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
                                    if (supportsHardwareBlur && depthBlurVal >= 0.5f) {
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
                                val phoneticText = line.phonetic
                                if (showPhonetic && showPhoneticButton && !phoneticText.isNullOrBlank()) {
                                    val phoneticFontSize = (lyricFontSize * lyricTranslationScale * 0.86f).coerceAtLeast(11.5f)
                                    Text(
                                        text = phoneticText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isCurrent) activePrimaryColor.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.38f),
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
                                    FlowRow(
                                        horizontalArrangement = flowArrangement,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        line.words.forEach { word ->
                                            val wordProgress = when {
                                                word.durationMs > 0L -> ((currentPositionMs - word.startTimeMs).toFloat() / word.durationMs.toFloat()).coerceIn(0f, 1f)
                                                currentPositionMs >= word.startTimeMs -> 1f
                                                else -> 0f
                                            }
                                            val isSinging = wordProgress in 0.01f..0.99f
                                            val dimColor = Color.White.copy(alpha = 0.40f)

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
                                            val isLongNote = word.durationMs >= 750L && isSinging

                                            Text(
                                                text = annotatedWord,
                                                style = MaterialTheme.typography.headlineSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = (lyricFontSize + 3.5f).sp,
                                                    lineHeight = (lyricFontSize + 11f).sp,
                                                    shadow = if (isLongNote) {
                                                        Shadow(
                                                            color = activePrimaryColor.copy(alpha = (0.80f * wave).coerceIn(0f, 1f)),
                                                            offset = Offset.Zero,
                                                            blurRadius = (20f * wave + 6f).coerceAtLeast(1f)
                                                        )
                                                    } else null
                                                ),
                                                modifier = Modifier.graphicsLayer {
                                                    translationY = -3.2f * wave
                                                    val s = 1f + 0.035f * wave
                                                    scaleX = s
                                                    scaleY = s
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    val distanceAlpha = when {
                                        isCurrent -> 1.0f
                                        isScrubTarget -> 0.92f
                                        distance == 1 -> 0.58f
                                        distance == 2 -> 0.38f
                                        distance == 3 -> 0.24f
                                        else -> 0.14f
                                    }
                                    val textColor = if (isCurrent) activePrimaryColor else Color.White.copy(alpha = distanceAlpha)
                                    val textWeight = if (isCurrent || isScrubTarget) FontWeight.ExtraBold else FontWeight.SemiBold
                                    val lineSize = if (isCurrent) (lyricFontSize + 3f).sp else lyricFontSize.sp

                                    val fallbackBlurShadow = if (!supportsHardwareBlur && depthBlurVal >= 0.5f) {
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
                                            lineHeight = (lyricFontSize + 9f).sp,
                                            textAlign = textAlign,
                                            shadow = fallbackBlurShadow
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                val trans = line.translation
                                if (showTranslation && showTranslationButton && !trans.isNullOrBlank()) {
                                    val transFontSize = (lyricFontSize * lyricTranslationScale).coerceAtLeast(13f)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = trans,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = when {
                                                isCurrent -> activePrimaryColor.copy(alpha = 0.90f)
                                                isScrubTarget -> Color.White.copy(alpha = 0.78f)
                                                distance == 1 -> Color.White.copy(alpha = 0.48f)
                                                else -> Color.White.copy(alpha = 0.24f)
                                            },
                                            fontSize = transFontSize.sp,
                                            lineHeight = (transFontSize * 1.42f).sp,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = textAlign
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // 手动滑动歌词时的「双翼渐隐中空导引线 (Split-Wing Hollow Timeline)」：
                // 左右对称全宽对齐，中央歌词区域 100% 留空不遮挡文字，仅在两侧翼展示渐隐光束与时间/播放胶囊
                androidx.compose.animation.AnimatedVisibility(
                    visible = isUserScrubbing && scrubLineIndex in lyricData.lines.indices,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(160)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                ) {
                    val targetTimeMs = lyricData.lines.getOrNull(scrubLineIndex)?.startTimeMs ?: 0L
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 左翼：磨砂时间胶囊 + 向内渐隐短光束
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = Color(0xD90E1624),
                                contentColor = Color.White,
                                modifier = Modifier.border(
                                    width = 0.8.dp,
                                    color = activePrimaryColor.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(9.dp)
                                )
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

                        // 中央完全留空，绝不穿过或遮挡任何歌词文字
                        Spacer(modifier = Modifier.weight(1f))

                        // 右翼：向内渐隐短光束 + 灵动跳转播放胶囊
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
                                color = activePrimaryColor.copy(alpha = 0.24f),
                                modifier = Modifier
                                    .border(
                                        width = 0.9.dp,
                                        color = activePrimaryColor.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(11.dp)
                                    )
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

            // 屏幕右侧偏下黄金拇指区无边框圆润按钮组（去除边框线，柔和磨砂底色）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 收藏按钮（无边框 + 圆润红心）
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

                // 2. 外语翻译显示按钮（仅非中文/非粤语的外语歌曲展示，默认关闭）
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

                // 3. 音译注音按钮（对含有罗马音/粤拼/韩语音译的歌曲精准展示，默认关闭）
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

                // 3. 歌词设置按钮（无边框 + 圆润饱满双轨滑块图标）
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

        // 歌词页专属底部极简控播栏：无边框圆润播放/暂停按钮 + 极光细丝进度条
        LyricPageMinimalBottomBar(
            currentPositionMs = rawPositionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            accentColor = activePrimaryColor,
            onTogglePlay = onTogglePlay,
            onSeekTo = onSeekTo
        )
    }
}

/**
 * 歌词页右侧悬浮无边框磨砂胶囊按钮（已彻底移除 `.border(...)` 边框线，采用柔和磨砂填充）
 */
@Composable
private fun LyricRightDockGlassButton(
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) activeColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.11f),
        animationSpec = tween(200),
        label = "lyricDockGlassBg"
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        contentColor = Color.White,
        modifier = Modifier
            .size(42.dp)
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
 * 歌词页专属底部极简控播栏（已去除播放按钮的 `.border(...)` 边框线，并采用圆润饱满自定义播放/暂停矢量图形）
 */
@Composable
private fun LyricPageMinimalBottomBar(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    accentColor: Color,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = if (durationMs > 0L) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val effectiveFraction = if (isDragging) dragFraction else currentFraction
    val displayTimeMs = if (isDragging) (dragFraction * durationMs).toLong() else currentPositionMs

    val railHeightDp by animateDpAsState(
        targetValue = if (isDragging) 7.dp else 3.5.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "lyricSilkRailHeight"
    )

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
                .size(46.dp)
                .softClickable(
                    pressScale = 0.86f,
                    onClick = onTogglePlay
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val w = size.width
                    val h = size.height
                    if (isPlaying) {
                        val barW = w * 0.25f
                        val barH = h * 0.70f
                        val topY = (h - barH) / 2f
                        val r = CornerRadius(barW / 2f, barW / 2f)
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(w * 0.18f, topY),
                            size = Size(barW, barH),
                            cornerRadius = r
                        )
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(w * 0.57f, topY),
                            size = Size(barW, barH),
                            cornerRadius = r
                        )
                    } else {
                        val tri = createPlumpPlayTrianglePath(
                            centerX = w * 0.54f,
                            centerY = h * 0.50f,
                            size = min(w, h) * 0.84f
                        )
                        drawPath(path = tri, color = Color.White)
                    }
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
                    color = if (isDragging) accentColor else Color.White.copy(alpha = 0.56f),
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
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                isDragging = true
                                dragFraction = (offset.x / w).coerceIn(0f, 1f)
                                if (tryAwaitRelease()) {
                                    onSeekTo((dragFraction * durationMs).toLong())
                                    isDragging = false
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                isDragging = true
                                dragFraction = (offset.x / w).coerceIn(0f, 1f)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                dragFraction = (change.position.x / w).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                onSeekTo((dragFraction * durationMs).toLong())
                                isDragging = false
                            },
                            onDragCancel = {
                                isDragging = false
                            }
                        )
                    },
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

                    val cursorW = if (isDragging) 14.dp.toPx() else 9.dp.toPx()
                    val cursorH = if (isDragging) 9.dp.toPx() else 5.dp.toPx()
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

private data class CoverExtractedPalette(
    val meshPrimary: Color,
    val meshSecondary: Color,
    val dominantAccent: Color,
    val secondaryAccent: Color
)

private suspend fun extractCoverMeshColors(
    context: Context,
    coverUrl: String
): CoverExtractedPalette? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(coverUrl)
            .size(48, 48)
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

        // 24-bin hue histogram for vivid accent color extraction
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
                if (x + y < (width + height) / 2) {
                    r1 += r; g1 += g; b1 += b; count1++
                } else {
                    r2 += r; g2 += g; b2 += b; count2++
                }

                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val v = hsv[2]
                if (sat >= 0.18f && v in 0.20f..0.96f) {
                    val bin = ((hue / 15f).toInt()).coerceIn(0, 23)
                    val centerBias = 1f - 0.30f * (abs(x - width * 0.5f) / (width * 0.5f).coerceAtLeast(1f))
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
            tmpHsv[0] = (tmpHsv[0] + hueShift) % 360f
            tmpHsv[1] = tmpHsv[1].coerceIn(0.38f, 0.72f)
            tmpHsv[2] = tmpHsv[2].coerceIn(0.15f, 0.28f)
            return Color(android.graphics.Color.HSVToColor(tmpHsv))
        }

        val c1 = if (count1 > 0) {
            normalizeForDarkMesh((r1 / count1).toInt(), (g1 / count1).toInt(), (b1 / count1).toInt(), 0f)
        } else Color(0xFF1A2C38)

        val c2 = if (count2 > 0) {
            normalizeForDarkMesh((r2 / count2).toInt(), (g2 / count2).toInt(), (b2 / count2).toInt(), 28f)
        } else Color(0xFF1B1932)

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
            val secHue = if (secondBin != -1 && secondWeight > bestWeight * 0.22f) {
                (secondBin * 15f + 7.5f) % 360f
            } else {
                (domHue + 32f) % 360f
            }
            secondaryAccent = Color(android.graphics.Color.HSVToColor(floatArrayOf(secHue, (domSat * 0.95f).coerceIn(0.55f, 0.90f), 0.95f)))
        } else {
            dominantAccent = ConceptPrimary
            secondaryAccent = ConceptCyanAccent
        }

        CoverExtractedPalette(
            meshPrimary = c1,
            meshSecondary = c2,
            dominantAccent = dominantAccent,
            secondaryAccent = secondaryAccent
        )
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
