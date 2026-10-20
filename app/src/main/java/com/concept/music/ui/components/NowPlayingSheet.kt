package com.concept.music.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
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
internal fun shouldShowTranslationButton(song: Song, lyricData: LyricData): Boolean {
    val lines = lyricData.lines
    if (lines.isEmpty()) return false
    val hasAnyTranslation = lyricData.hasTranslation || lines.any { !it.translation.isNullOrBlank() }
    if (!hasAnyTranslation) return false

    var cjkCount = 0
    var kanaHangulCount = 0
    var latinCount = 0
    lines.take(30).forEach { line ->
        val txt = line.text
        for (ch in txt) {
            when (ch.code) {
                in 0x3040..0x30FF, in 0x31F0..0x31FF, in 0xAC00..0xD7AF, in 0x1100..0x11FF -> kanaHangulCount++
                in 0x4E00..0x9FFF -> cjkCount++
                in 'a'.code..'z'.code, in 'A'.code..'Z'.code -> latinCount++
            }
        }
    }
    // 含有日韩字符或英文/外文字符占比较高时，属于外语/双语歌曲，展示翻译按钮；纯中文歌隐藏翻译按钮
    if (kanaHangulCount > 2) return true
    if (latinCount > cjkCount * 2 && latinCount > 15) return true
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable

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
    isFavorite: Boolean = false,
    coverStyle: CoverStyle = CoverStyle.VINYL,
    playerBackgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.COVER_BLUR,
    dynamicCoverAccentEnabled: Boolean = true,
    showTranslation: Boolean = true,
    showPhonetic: Boolean = true,
    lyricFontSize: Float = 22f,
    lyricTranslationScale: Float = 0.80f,
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
    onPlayQueueSong: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    onClearQueue: () -> Unit
) {
    if (song == null) return

    val context = LocalContext.current
    val view = LocalView.current
    var showLyricView by remember { mutableStateOf(false) }
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

    // 歌词页屏幕常亮控制
    DisposableEffect(showLyricView, lyricKeepScreenOn, isPlaying) {
        val keepOn = showLyricView && lyricKeepScreenOn && isPlaying
        view.keepScreenOn = keepOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    // 系统返回键：若正处于全屏歌词则先返回封面，否则收起播放页
    BackHandler {
        if (showLyricView) {
            showLyricView = false
        } else {
            onDismiss()
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

    // 封面智能动态流光取色（同时提取背景深色氛围色 + 播放按钮与彗星进度条高饱和主色调）
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
        val dimAlpha = if (showLyricView) lyricBackgroundDim.coerceIn(0.40f, 0.92f) else 0.78f

        // 1. 多模式可切换沉浸式背景层（高斯封面虚化 / 流体极光 / 律动光晕 / 暗夜星尘 / 极简纯黑）
        NowPlayingSelectableBackground(
            coverUrl = song.coverUrl,
            backgroundStyle = playerBackgroundStyle,
            isPlaying = isPlaying,
            showLyricView = showLyricView,
            dimAlpha = dimAlpha,
            meshColorPrimary = meshColorPrimary,
            meshColorSecondary = meshColorSecondary,
            accentPrimary = activeAccentColor,
            accentSecondary = activeSecondaryAccent
        )

        // 2. 主体内容区：封面播放页与重构后的极简歌词页
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
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
                            val msg = if (nextEnabled) {
                                if (SystemLyricBridge.canDrawSystemOverlays(context)) {
                                    "已开启全局桌面悬浮歌词"
                                } else {
                                    "已开启桌面悬浮歌词（可在歌词设置中授予系统悬浮窗权限）"
                                }
                            } else {
                                "已关闭桌面歌词"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
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
                        onOpenLyricSettings = { showLyricSettingsDialog = true }
                    )
                }
            }
        }
    }

    // 播放队列弹窗
    if (showQueueSheet) {
        QueueBottomSheet(
            queue = queue,
            currentIndex = currentIndex,
            playMode = playMode,
            climaxModeEnabled = climaxModeEnabled,
            onToggleClimaxMode = onToggleClimaxMode,
            onCyclePlayMode = onCyclePlayMode,
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

    // 更多设置抽屉（已移除音效、桌面歌词与歌词海报按钮，新增背景意境与动态取色开关）
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

    // 重构版歌词设置面板（自适应App主题色彩 + 移除顶部歌词海报按钮 + 完善功能联动）
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
            showTranslation = showTranslation,
            showPhonetic = showPhonetic,
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
            onToggleTranslation = onToggleTranslation,
            onTogglePhonetic = onTogglePhonetic,
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

/**
 * 封面播放页可切换背景渲染层：
 * 1. COVER_BLUR (封面虚化): 24x24 高斯纹理 + 双核弥散色彩
 * 2. FLUID_AURORA (流体极光): 动态三核极光网格流体漂移 + 柔光封面底纹
 * 3. RHYTHM_HALO (律动光晕): 随音乐呼吸的同心声波光环脉冲
 * 4. STARLIGHT_NEBULA (暗夜星尘): 深空星云微光 + 36颗漫游闪烁星尘微粒
 * 5. DEEP_OLED (极简纯黑): 纯粹深邃 OLED 黑底 + 柔和主题微光
 */
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
    val outputRouteLabel = remember { detectAudioOutputRouteLabel(context) }
    val isGoldQuality = qualityLabel.startsWith("Hi-Res", ignoreCase = true)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 第一行：左侧退出按钮与居中加大歌名严格同高对齐，右上角留空保持极简对称
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBackOrDismiss,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "返回",
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 20.5.sp,
                    letterSpacing = 0.2.sp
                ),
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 46.dp)
                    .basicMarquee()
            )
        }

        // 第二行：歌手名换行居中
        Text(
            text = artist.ifBlank { "未知歌手" },
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 40.dp)
        )

        // 第三行（仅在封面播放页展示）：音频输出设备标识与音质切换徽章一体化微排版
        AnimatedVisibility(
            visible = !isLyricPage,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // 音频输出设备胶囊
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.07f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
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

                // 音质选择微徽章（由原右上角移至此处，与输出设备和谐并列）
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
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
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
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
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
                            text = if (sleepRemainingSec > 0L) "定时 ${formatDuration(sleepRemainingSec * 1000L)}" else "播完当前停",
                            color = ConceptPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 图一：重构后的封面播放页主体

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
    onOpenMoreSheet: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenComments: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 封面舞台（支持单击进歌词、双击点赞、左右滑动切歌）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            var dragDeltaX by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(showDoubleTapHeart) {
                if (showDoubleTapHeart) {
                    delay(750L)
                    onClearDoubleTapHeart()
                }
            }

            Box(
                modifier = Modifier
                    .pointerInput(song.id, isFavorite) {
                        detectTapGestures(
                            onTap = { onOpenLyricView() },
                            onDoubleTap = { onTriggerDoubleTapHeart() }
                        )
                    }
                    .pointerInput(song.id) {
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
        }

        // 2. 歌词预览与右侧「歌词海报 + 收藏」垂直双键区（歌词海报按钮移至收藏按钮正上方）
        FramelessLyricPreviewWithFavoriteRow(
            lyricData = lyricData,
            currentLyricIndex = currentLyricIndex,
            currentPositionMs = currentPositionMs,
            showTranslation = showTranslation,
            isFavorite = isFavorite,
            accentColor = accentPrimary,
            onLyricClick = onOpenLyricView,
            onOpenLyricPoster = onOpenLyricPoster,
            onToggleFavorite = onToggleFavorite
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 进度条上方四键功能栏：「添加到歌单、蝰蛇音效、桌面歌词、更多」全部采用圆润饱满重绘图标
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // (1) 添加到歌单（圆润饱满矢量重绘）
            PlayerActionIconButton(
                onClick = onAddToPlaylist,
                contentDescription = "添加到歌单"
            ) {
                PlumpPlaylistAddCanvasIcon(
                    color = Color.White.copy(alpha = 0.88f),
                    accentColor = accentPrimary
                )
            }

            // (2) 蝰蛇音效（圆润饱满声波胶囊重绘 + 激活提示）
            PlayerActionIconButton(
                onClick = onOpenAudioEffect,
                contentDescription = "蝰蛇音效"
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

            // (3) 桌面歌词（从更多面板移至此处，圆润饱满聊天气泡音符重绘 + 激活状态）
            PlayerActionIconButton(
                onClick = onToggleDesktopLyric,
                contentDescription = "桌面歌词"
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

            // (4) 更多设置（圆润饱满三圆点）
            PlayerActionIconButton(
                onClick = onOpenMoreSheet,
                contentDescription = "更多设置"
            ) {
                PlumpMoreDotsCanvasIcon(
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. 现代光学彗星拖尾进度条 + 圆润饱满五大播放控件 + 底部评论按钮
        PlayerControlsSection(
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            climaxStartMs = climaxStartMs,
            climaxEndMs = climaxEndMs,
            highlightClimaxRange = highlightClimaxRange,
            isPlaying = isPlaying,
            playMode = playMode,
            commentCount = commentCount,
            accentPrimary = accentPrimary,
            accentSecondary = accentSecondary,
            onSeekTo = onSeekTo,
            onTogglePlay = onTogglePlay,
            onPrevious = onPrevious,
            onNext = onNext,
            onCyclePlayMode = onCyclePlayMode,
            onOpenComments = onOpenComments,
            onQueueClick = onOpenQueue
        )

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun PlayerActionIconButton(
    onClick: () -> Unit,
    contentDescription: String,
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

/**
 * 黑胶封面重构：高质感发烧级黑胶唱盘机芯 (MasterVinylTurntableView)
 * - 摒弃原单调黑圈与简陋折线唱臂；
 * - 采用半透明亚克力底座外环 + 各向异性金属丝绸锥形光泽反射 (Sweep Gradient) + 14道精密声轨同心微纹 + 鎏金内环；
 * - 右上角绘制精密平衡 S 型金属唱臂（含万向节轴承座、配重锤、S型流线臂管、倾角唱头壳与发光唱针）。
 */
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
        modifier = Modifier.size(width = 312.dp, height = 304.dp),
        contentAlignment = Alignment.Center
    ) {
        // 底层环境柔光氛围环
        Box(
            modifier = Modifier
                .size(288.dp)
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
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.04f),
                            Color.White.copy(alpha = 0.16f)
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 旋转黑胶主体盘面
        Box(
            modifier = Modifier
                .size(276.dp)
                .clip(CircleShape)
                .graphicsLayer { rotationZ = rotationProvider() },
            contentAlignment = Alignment.Center
        ) {
            // 使用 Canvas 绘制黑胶底色、双束锥形丝绸光泽反射与精密声轨纹路
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.minDimension <= 2f) return@Canvas
                val radius = (size.minDimension / 2f).coerceAtLeast(1f)
                val center = Offset(size.width / 2f, size.height / 2f)

                // 1. 深邃碳黑黑胶基底
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

                // 2. 各向异性丝绸光泽反射扇区 (模拟真实黑胶在灯光下的对角反光带)
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

                // 3. 14 道疏密有致的黑胶音轨微纹
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

                // 4. 唱片中心盘标金属压环
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

            // 唱片中心高清圆形专辑封面
            Box(
                modifier = Modifier
                    .size(170.dp)
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

                // 精密多环金属轴心孔
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xAA0A0D14))
                        .border(1.5.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF07090E))
                            .border(1.dp, VipGold.copy(alpha = 0.7f), CircleShape)
                    )
                }
            }
        }

        // 右上角精密 S 型金属唱臂 (Precision S-Shaped Tonearm)
        Canvas(
            modifier = Modifier
                .size(144.dp)
                .align(Alignment.TopEnd)
        ) {
            if (size.minDimension <= 2f) return@Canvas
            val pivot = Offset(size.width * 0.76f, size.height * 0.17f)

            // 唱臂底座外环阴影与金属轴承盘
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
                // 后部配重锤 (Counterweight)
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

                // S 型流线金属唱臂主杆
                val armEnd = Offset(pivot.x - 36.dp.toPx(), pivot.y + 88.dp.toPx())
                val sCurvePath = Path().apply {
                    moveTo(pivot.x, pivot.y)
                    cubicTo(
                        pivot.x - 4.dp.toPx(), pivot.y + 30.dp.toPx(),
                        pivot.x - 24.dp.toPx(), pivot.y + 56.dp.toPx(),
                        armEnd.x, armEnd.y
                    )
                }
                // 唱臂投影
                drawPath(
                    path = sCurvePath,
                    color = Color.Black.copy(alpha = 0.45f),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
                // 唱臂金属高光管体
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

                // 唱头壳 (Headshell) 与唱针微光
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
                // 针尖呼吸光点
                drawCircle(
                    color = if (isPlaying) ConceptCyanAccent else Color.White.copy(alpha = 0.6f),
                    radius = 2.5.dp.toPx(),
                    center = headTip
                )
            }

            // 轴承中心金属顶盖
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

/**
 * 静态圆角方形封面视图（不旋转，带播放/暂停呼吸缩放与高定玻璃光泽边框）
 */
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
            .size(286.dp)
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
            .background(Color(0xFF151A26))
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.05f),
                        Color.White.copy(alpha = 0.14f)
                    )
                ),
                shape = RoundedCornerShape(30.dp)
            ),
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



/**
 * 封面下方无框歌词预览 + 右侧「歌词海报（上）+ 收藏（下）」垂直操作列：
 * - 歌词海报按钮按需求移至封面播放页收藏按钮的正上方；
 * - 两个图标均采用圆润饱满的矢量绘制风格。
 */
@Composable
private fun FramelessLyricPreviewWithFavoriteRow(
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    showTranslation: Boolean,
    isFavorite: Boolean,
    accentColor: Color,
    onLyricClick: () -> Unit,
    onOpenLyricPoster: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val currentLineObj = lyricData.lines.getOrNull(currentLyricIndex)
    val mainText = currentLineObj?.text?.takeIf { it.isNotBlank() } ?: "轻触展开沉浸歌词"
    val subText = if (showTranslation && !currentLineObj?.translation.isNullOrBlank()) {
        currentLineObj?.translation.orEmpty()
    } else {
        lyricData.lines.getOrNull(currentLyricIndex + 1)?.text.orEmpty()
    }

    val favScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.14f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "coverFavScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧：无圆角框开放式双行歌词阅览
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp, bottom = 4.dp)
                .softClickable(
                    pressScale = 0.98f,
                    onClick = onLyricClick
                ),
            horizontalAlignment = Alignment.Start
        ) {
            if (currentLineObj != null && currentLineObj.hasWordTiming && currentLineObj.words.isNotEmpty()) {
                val annotated = buildAnnotatedString {
                    currentLineObj.words.forEach { word ->
                        val wordEnd = word.startTimeMs + word.durationMs
                        when {
                            currentPositionMs >= wordEnd -> {
                                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) {
                                    append(word.text)
                                }
                            }
                            currentPositionMs > word.startTimeMs -> {
                                withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.ExtraBold)) {
                                    append(word.text)
                                }
                            }
                            else -> {
                                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.52f), fontWeight = FontWeight.SemiBold)) {
                                    append(word.text)
                                }
                            }
                        }
                    }
                }
                Text(
                    text = annotated,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subText.ifBlank { "♪ • • •" },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.54f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 右侧：歌词海报按钮（位于收藏按钮正上方）+ 圆润饱满红心收藏按钮
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                    accentColor = accentColor
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
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
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 封面播放页底部控制台：
 * 1. 现代美学光学彗星拖尾进度条 (CometTailProgressBar) — 动态封面取色驱动轨道与彗星光锥，副歌区间保持金色不变；
 * 2. 圆润饱满五大播放控件 — 修复播放三角倒置/歪斜问题，动态封面主色驱动播放/暂停圆钮；
 * 3. 底部评论胶囊。
 */
@Composable
private fun PlayerControlsSection(
    currentPositionMs: Long,
    durationMs: Long,
    climaxStartMs: Long = 0L,
    climaxEndMs: Long = 0L,
    highlightClimaxRange: Boolean = false,
    isPlaying: Boolean,
    playMode: PlayMode,
    commentCount: Int,
    accentPrimary: Color = ConceptPrimary,
    accentSecondary: Color = ConceptCyanAccent,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onOpenComments: () -> Unit,
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

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.softCardClickable(
                    shape = RoundedCornerShape(16.dp),
                    onClick = onOpenComments
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    val commentText = if (commentCount > 0) "$commentCount 评论" else "评论"
                    Text(
                        text = commentText,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "进入评论",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 重新设计的现代光学彗星拖尾进度条 (CometTailProgressBar):
 * 1. 摒弃原先生硬的圆角矩形外框光晕，改用真正的「三次贝塞尔锥形流光束 (Tapered Bezier Comet Plume)」：
 *    由尾端 0px 尖端平滑向彗星核张开，形成深邃的星云外溢羽化层与炽白高光内束；
 * 2. 彗星尾迹中嵌有 6 颗随播放实时向后飘逸微闪的「星尘微粒 (Stardust Particles)」；
 * 3. 彗星头部由三层径向星云光晕 + 十字星芒微光 (4-Point Starburst Lens Flare) + 动态主色炽白晶核构成；
 * 4. 进度条与彗星颜色由封面动态取色 (`accentPrimary` / `accentSecondary`) 驱动，而副歌高潮标记严格保持流金 `VipGold` 不变。
 */
@Composable
private fun CometTailProgressBar(
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
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val trackHeightDp by animateDpAsState(
        targetValue = if (isInteracting) 8.5.dp else 5.0.dp,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cometTrackHeight"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isInteracting) 1.36f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cometThumbScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "cometOpticalFlow")
    val cometPulse by infiniteTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cometBreath"
    )
    val stardustPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 1350 else 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stardustPhase"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
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
        // 按住拖动时浮现于手指上方的毛玻璃时间预览胶囊
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
                contentColor = Color.White,
                modifier = Modifier.border(
                    width = 0.8.dp,
                    color = accentPrimary.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp)
                )
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
                .height(34.dp)
        ) {
            val w = size.width
            val h = size.height
            if (w <= 2f || h <= 2f) return@Canvas
            val centerY = h / 2f
            val barH = trackHeightDp.toPx().coerceIn(2f, h)
            val barRadius = CornerRadius(barH / 2f, barH / 2f)
            val topY = centerY - barH / 2f

            // 1. 未播放背景底轨
            drawRoundRect(
                color = Color.White.copy(alpha = if (isInteracting) 0.22f else 0.15f),
                topLeft = Offset(0f, topY),
                size = Size(w, barH),
                cornerRadius = barRadius
            )

            // 2. 副歌高潮区间流金标记（严格保持 VipGold 金色不变！）
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

            // 3. 已播放动态主色渐变轨道
            if (headX > 1f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accentPrimary.copy(alpha = 0.42f),
                            accentPrimary.copy(alpha = 0.88f),
                            lerp(accentPrimary, accentSecondary, 0.72f)
                        ),
                        startX = 0f,
                        endX = headX
                    ),
                    topLeft = Offset(0f, topY),
                    size = Size(headX, barH),
                    cornerRadius = barRadius
                )
            }

            // 4. 现代光学锥形彗星拖尾 (Tapered Bezier Comet Wake + Stardust Micro-Particles)
            val maxTailLen = min(headX, 116.dp.toPx())
            val pulseFactor = (if (isPlaying) cometPulse else 1f).coerceIn(0.82f, 1.22f)
            if (maxTailLen > 4f) {
                val tailStartX = (headX - maxTailLen).coerceAtLeast(0f)
                val headHalfH = (barH * 0.72f * thumbScale * pulseFactor)

                // 外层星云弥散锥形光羽
                val outerPlumePath = Path().apply {
                    moveTo(tailStartX, centerY)
                    cubicTo(
                        tailStartX + maxTailLen * 0.46f, centerY - headHalfH * 0.20f,
                        tailStartX + maxTailLen * 0.84f, centerY - headHalfH * 2.45f,
                        headX, centerY - headHalfH * 2.15f
                    )
                    lineTo(headX, centerY + headHalfH * 2.15f)
                    cubicTo(
                        tailStartX + maxTailLen * 0.84f, centerY + headHalfH * 2.45f,
                        tailStartX + maxTailLen * 0.46f, centerY + headHalfH * 0.20f,
                        tailStartX, centerY
                    )
                    close()
                }
                drawPath(
                    path = outerPlumePath,
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.42f to accentPrimary.copy(alpha = 0.14f),
                            0.80f to accentSecondary.copy(alpha = 0.38f),
                            1.00f to Color.White.copy(alpha = 0.54f)
                        ),
                        startX = tailStartX,
                        endX = headX.coerceAtLeast(tailStartX + 1f)
                    )
                )

                // 内层高亮度凝聚流光束
                val corePlumePath = Path().apply {
                    val coreStart = tailStartX + maxTailLen * 0.16f
                    moveTo(coreStart, centerY)
                    cubicTo(
                        tailStartX + maxTailLen * 0.58f, centerY - headHalfH * 0.26f,
                        tailStartX + maxTailLen * 0.88f, centerY - headHalfH * 1.05f,
                        headX, centerY - headHalfH * 0.96f
                    )
                    lineTo(headX, centerY + headHalfH * 0.96f)
                    cubicTo(
                        tailStartX + maxTailLen * 0.88f, centerY + headHalfH * 1.05f,
                        tailStartX + maxTailLen * 0.58f, centerY + headHalfH * 0.26f,
                        coreStart, centerY
                    )
                    close()
                }
                drawPath(
                    path = corePlumePath,
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.36f to accentPrimary.copy(alpha = 0.48f),
                            0.78f to lerp(accentSecondary, Color.White, 0.48f).copy(alpha = 0.92f),
                            1.00f to Color.White
                        ),
                        startX = tailStartX + maxTailLen * 0.16f,
                        endX = headX.coerceAtLeast(tailStartX + 2f)
                    )
                )

                // 6 颗随音乐律动飘散的彗尾星尘微粒
                for (i in 0 until 6) {
                    val pNorm = (stardustPhase + i * 0.166f) % 1f
                    val distRatio = 1f - pNorm
                    val px = tailStartX + maxTailLen * (0.20f + 0.78f * distRatio)
                    val spread = (1f - distRatio) * 5.6.dp.toPx()
                    val dirSign = if (i % 2 == 0) 1f else -1f
                    val py = centerY + dirSign * sin(pNorm * PI.toFloat() * 2.2f + i) * spread
                    val pAlpha = (sin(distRatio * PI.toFloat()) * 0.86f).coerceIn(0f, 0.90f)
                    val pRadius = (0.9f + 1.45f * distRatio).dp.toPx()
                    drawCircle(
                        color = if (i % 2 == 0) Color.White.copy(alpha = pAlpha) else accentSecondary.copy(alpha = pAlpha),
                        radius = pRadius,
                        center = Offset(px, py)
                    )
                }
            }

            // 5. 彗星晶核头部：多层星云光晕 + 十字光学星芒 + 炽白核心
            val headCenter = Offset(headX, centerY)
            val haloRadius = 15.dp.toPx() * thumbScale * pulseFactor
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White.copy(alpha = 0.96f),
                        0.28f to lerp(accentSecondary, Color.White, 0.50f).copy(alpha = 0.74f),
                        0.64f to accentPrimary.copy(alpha = 0.34f),
                        1.00f to Color.Transparent
                    ),
                    center = headCenter,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = headCenter
            )

            // 十字光学星芒 (4-Point Optical Starflare)
            val spikeH = 10.5.dp.toPx() * thumbScale * pulseFactor
            val spikeV = 7.8.dp.toPx() * thumbScale * pulseFactor
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.88f), Color.Transparent),
                    startX = headCenter.x - spikeH,
                    endX = headCenter.x + spikeH
                ),
                start = Offset(headCenter.x - spikeH, headCenter.y),
                end = Offset(headCenter.x + spikeH, headCenter.y),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.76f), Color.Transparent),
                    startY = headCenter.y - spikeV,
                    endY = headCenter.y + spikeV
                ),
                start = Offset(headCenter.x, headCenter.y - spikeV),
                end = Offset(headCenter.x, headCenter.y + spikeV),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 彗星实心外环与炽白晶核
            drawCircle(
                color = accentPrimary,
                radius = 5.4.dp.toPx() * thumbScale,
                center = headCenter
            )
            drawCircle(
                color = Color.White,
                radius = 3.5.dp.toPx() * thumbScale,
                center = headCenter
            )
        }
    }
}

/**
 * 圆润饱满三角形路径辅助函数（严格水平向右 +X，零旋转歪斜，三顶点全圆角过渡）
 */
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
        quadraticTo(p2.x, p2.y, p2b.x, p2b.y)
        lineTo(p3a.x, p3a.y)
        quadraticTo(p3.x, p3.y, p3b.x, p3b.y)
        lineTo(p1a.x, p1a.y)
        quadraticTo(p1.x, p1.y, p1b.x, p1b.y)
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
private fun AnimatedPlayModeButton(
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
private fun RedrawnSkipButton(
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
private fun AnimatedPlayPauseCenterButton(
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
private fun RedrawnQueueButton(
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
                                .softClickable(
                                    pressScale = 0.98f,
                                    onClick = {
                                        isUserScrubbing = false
                                        onLineClick(index)
                                    }
                                )
                                .padding(vertical = lyricLineSpacing.dp, horizontal = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = horizontalAlignment,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val phoneticText = line.phonetic
                                if (showPhonetic && !phoneticText.isNullOrBlank()) {
                                    Text(
                                        text = phoneticText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isCurrent) activePrimaryColor.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.36f),
                                            fontSize = (lyricFontSize * lyricTranslationScale * 0.86f).coerceAtLeast(11f).sp,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = textAlign,
                                            letterSpacing = 0.4.sp
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
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
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        text = trans,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = when {
                                                isCurrent -> activePrimaryColor.copy(alpha = 0.88f)
                                                isScrubTarget -> Color.White.copy(alpha = 0.72f)
                                                distance == 1 -> Color.White.copy(alpha = 0.44f)
                                                else -> Color.White.copy(alpha = 0.22f)
                                            },
                                            fontSize = (lyricFontSize * lyricTranslationScale).coerceAtLeast(12f).sp,
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

                // 手动滑动歌词时的交互跳转时间尺
                androidx.compose.animation.AnimatedVisibility(
                    visible = isUserScrubbing && scrubLineIndex in lyricData.lines.indices,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(160)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(end = 52.dp)
                ) {
                    val targetTimeMs = lyricData.lines.getOrNull(scrubLineIndex)?.startTimeMs ?: 0L
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xD9101726),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = formatDuration(targetTimeMs),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            thickness = 0.8.dp,
                            color = activePrimaryColor.copy(alpha = 0.45f)
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = activePrimaryColor,
                            modifier = Modifier.softCardClickable {
                                isUserScrubbing = false
                                onLineClick(scrubLineIndex)
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "跳转播放",
                                    tint = Color(0xFF081018),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "播放",
                                    color = Color(0xFF081018),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
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

                // 2. 翻译显示按钮（无边框 + 圆润字标）
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

/**
 * 重新设计的歌词显示与视觉设置抽屉 (LyricSettingsDialog)
 * - 修复原 `Surface` 未指定 `contentColor` 导致标题文字变成纯黑 (`#000000`) 的严重显示 Bug；
 * - 跟随 App 主题色 (`MaterialTheme.colorScheme` + `dynamicAccentColor`) 动态渲染；
 * - 移除顶部的「生成歌词海报」按钮（已移至封面播放页收藏按钮上方），改为一键恢复默认；
 * - 新增「译文/音译字号比例」调节、封面动态律动配色 (`DYNAMIC_COVER`) 与桌面歌词窗口宽度/悬浮窗权限快捷配置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricSettingsDialog(
    fontSize: Float,
    translationScale: Float = 0.80f,
    alignment: LyricAlignment,
    wordByWord: Boolean,
    blurEnabled: Boolean,
    blurIntensity: Float,
    lineSpacing: Float,
    offsetMs: Long,
    highlightTheme: LyricHighlightTheme,
    backgroundDim: Float,
    keepScreenOn: Boolean,
    showTranslation: Boolean,
    showPhonetic: Boolean,
    dynamicAccentColor: Color = ConceptPrimary,
    onSetFontSize: (Float) -> Unit,
    onSetTranslationScale: (Float) -> Unit = {},
    onSetAlignment: (LyricAlignment) -> Unit,
    onSetWordByWord: (Boolean) -> Unit,
    onSetBlurEnabled: (Boolean) -> Unit,
    onSetBlurIntensity: (Float) -> Unit,
    onSetLineSpacing: (Float) -> Unit,
    onSetOffsetMs: (Long) -> Unit,
    onSetHighlightTheme: (LyricHighlightTheme) -> Unit,
    onSetBackgroundDim: (Float) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onToggleTranslation: () -> Unit,
    onTogglePhonetic: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val systemLyricState by SystemLyricBridge.state.collectAsState()
    val desktopLyricEnabled = systemLyricState.floatingCapsuleEnabled
    val statusBarLyricEnabled = systemLyricState.statusBarBroadcastEnabled
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBg = conceptSheetContainerColor()
    val cardBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val themeAccent = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = textPrimary,
        windowInsets = WindowInsets(0, 0, 0, 0),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides textPrimary) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 顶部标题与一键重置
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "歌词显示与视觉设置",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "实时定制逐字流光配色、排版字号与景深美学",
                            color = textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = themeAccent.copy(alpha = 0.14f),
                        contentColor = themeAccent,
                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(12.dp)) {
                            onSetFontSize(22f)
                            onSetTranslationScale(0.80f)
                            onSetLineSpacing(14f)
                            onSetAlignment(LyricAlignment.LEFT)
                            onSetBlurEnabled(true)
                            onSetBlurIntensity(1.0f)
                            onSetBackgroundDim(0.72f)
                            onSetOffsetMs(0L)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = themeAccent, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("恢复默认", color = themeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 1. 歌词焦点配色与对齐排版
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "歌词焦点配色",
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LyricHighlightTheme.entries.forEach { theme ->
                                val selected = highlightTheme == theme
                                val swatchColor = if (theme == LyricHighlightTheme.DYNAMIC_COVER) {
                                    dynamicAccentColor
                                } else {
                                    Color(theme.primaryHex)
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) swatchColor.copy(alpha = 0.22f) else sheetBg,
                                    contentColor = textPrimary,
                                    modifier = Modifier
                                        .border(
                                            width = if (selected) 1.4.dp else 0.dp,
                                            color = if (selected) swatchColor else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .softCardClickable(shape = RoundedCornerShape(12.dp)) {
                                            onSetHighlightTheme(theme)
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(11.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = theme.label,
                                            color = if (selected) swatchColor else textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = textPrimary.copy(alpha = 0.07f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "歌词对齐排版",
                                color = textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LyricAlignment.entries.forEach { item ->
                                    val selected = alignment == item
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) themeAccent.copy(alpha = 0.20f) else sheetBg,
                                        contentColor = if (selected) themeAccent else textPrimary,
                                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(10.dp)) {
                                            onSetAlignment(item)
                                        }
                                    ) {
                                        Text(
                                            text = item.label,
                                            color = if (selected) themeAccent else textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 字号、副歌译文比例与行距调节
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("主歌词字号大小", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${fontSize.toInt()} sp", color = themeAccent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = fontSize,
                            onValueChange = onSetFontSize,
                            valueRange = 16f..30f,
                            steps = 6,
                            colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                            modifier = Modifier.height(30.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("副歌词(翻译/音译)字号比例", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${(translationScale * 100).roundToInt()}%", color = themeAccent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = translationScale,
                            onValueChange = onSetTranslationScale,
                            valueRange = 0.65f..0.95f,
                            colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                            modifier = Modifier.height(30.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("行间呼吸间距", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${lineSpacing.toInt()} dp", color = themeAccent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = lineSpacing,
                            onValueChange = onSetLineSpacing,
                            valueRange = 8f..24f,
                            steps = 7,
                            colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                // 3. 景深高斯模糊与背景暗度
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("阶梯景深高斯模糊", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("非当前行随距离自然虚化，聚焦正在唱诵的歌词", color = textSecondary, fontSize = 11.5.sp)
                            }
                            Switch(
                                checked = blurEnabled,
                                onCheckedChange = onSetBlurEnabled,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = themeAccent)
                            )
                        }

                        if (blurEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("景深模糊浓度", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(String.format(Locale.getDefault(), "%.1fx", blurIntensity), color = themeAccent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                            Slider(
                                value = blurIntensity,
                                onValueChange = onSetBlurIntensity,
                                valueRange = 0.4f..1.8f,
                                colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                                modifier = Modifier.height(28.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("背景柔光压暗度", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${(backgroundDim * 100).toInt()}%", color = themeAccent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = backgroundDim,
                            onValueChange = onSetBackgroundDim,
                            valueRange = 0.40f..0.92f,
                            colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // 4. 歌词时间轴微调校准
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("歌词时间轴校准", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = String.format(Locale.getDefault(), "%+.1f 秒", offsetMs / 1000f),
                                color = if (offsetMs != 0L) VipGold else themeAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                -500L to "-0.5s",
                                -100L to "-0.1s",
                                0L to "重置 0s",
                                100L to "+0.1s",
                                500L to "+0.5s"
                            ).forEach { (delta, label) ->
                                val isHighlight = delta == 0L && offsetMs == 0L
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isHighlight) themeAccent.copy(alpha = 0.20f) else sheetBg,
                                    contentColor = if (isHighlight) themeAccent else textPrimary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .softCardClickable {
                                            if (delta == 0L) onSetOffsetMs(0L) else onSetOffsetMs(offsetMs + delta)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = label,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHighlight) themeAccent else textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. 逐字动效、音译注音、屏幕常亮与全局桌面歌词配置
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LyricSettingSwitchRow(
                            title = "KRC 逐字流光染色",
                            subtitle = "歌词随人声毫秒级逐字扫光，长音叠加微光",
                            checked = wordByWord,
                            accentColor = themeAccent,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onCheckedChange = onSetWordByWord
                        )
                        LyricSettingSwitchRow(
                            title = "外语音译注音 (罗马音/粤拼)",
                            subtitle = "在日韩粤语歌词上方显示发音注音",
                            checked = showPhonetic,
                            accentColor = themeAccent,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onCheckedChange = { if (showPhonetic != it) onTogglePhonetic() }
                        )
                        LyricSettingSwitchRow(
                            title = "外语中文翻译",
                            subtitle = "在外语歌曲下方显示双语对照译文",
                            checked = showTranslation,
                            accentColor = themeAccent,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onCheckedChange = { if (showTranslation != it) onToggleTranslation() }
                        )
                        LyricSettingSwitchRow(
                            title = "歌词页屏幕常亮",
                            subtitle = "处于歌词界面且音乐播放时防止手机息屏",
                            checked = keepScreenOn,
                            accentColor = themeAccent,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onCheckedChange = onSetKeepScreenOn
                        )
                        HorizontalDivider(color = textPrimary.copy(alpha = 0.08f))
                        LyricSettingSwitchRow(
                            title = "全局桌面悬浮歌词",
                            subtitle = "支持返回手机桌面全局悬浮显示 · 60fps 零延迟拖动",
                            checked = desktopLyricEnabled,
                            accentColor = themeAccent,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onCheckedChange = { SystemLyricBridge.setFloatingCapsuleEnabled(it) }
                        )
                        if (desktopLyricEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("桌面歌词窗口宽度", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${(systemLyricState.desktopWidthFraction * 100).roundToInt()}%", color = themeAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = systemLyricState.desktopWidthFraction,
                                onValueChange = { SystemLyricBridge.setDesktopWidthFraction(context, it) },
                                valueRange = 0.65f..0.98f,
                                colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                                modifier = Modifier.height(28.dp)
                            )
                            if (!SystemLyricBridge.canDrawSystemOverlays(context)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = themeAccent.copy(alpha = 0.14f),
                                    contentColor = themeAccent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .softCardClickable {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                runCatching {
                                                    val intent = Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:${context.packageName}")
                                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(intent)
                                                }
                                            }
                                        }
                                ) {
                                    Text(
                                        text = "点击授予「显示在其他应用上层」权限以在系统桌面悬浮显示 ›",
                                        color = themeAccent,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        LyricSettingSwitchRow(
                            title = "系统状态栏歌词广播",
                            subtitle = "适配 Flyme / HyperOS / OriginOS / LyricGetter",
                            checked = statusBarLyricEnabled,
                            accentColor = themeAccent,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onCheckedChange = { SystemLyricBridge.setStatusBarBroadcastEnabled(context, it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun LyricSettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color = ConceptPrimary,
    textPrimary: Color = MaterialTheme.colorScheme.onSurface,
    textSecondary: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = textSecondary, fontSize = 11.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
        )
    }
}

/**
 * 播放队列 BottomSheet
 * - 在播放队列顶部新增「副歌速刷」一键开关胶囊，方便用户在浏览播放队列时随时开启/关闭副歌高潮速刷模式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentIndex: Int,
    playMode: PlayMode = PlayMode.REPEAT_ALL,
    climaxModeEnabled: Boolean = false,
    onToggleClimaxMode: () -> Unit = {},
    onCyclePlayMode: () -> Unit = {},
    onDismiss: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onClear: () -> Unit,
    onReorderQueueByMood: (String) -> String = { "" },
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeMoodMode by remember { mutableStateOf("") }

    val isDark = isConceptDarkTheme()
    val sheetBg = conceptSheetContainerColor()
    val cardBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(currentIndex) {
        if (currentIndex in queue.indices) {
            listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        windowInsets = WindowInsets(0, 0, 0, 0),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            // 顶部标题栏 + 循环模式胶囊 + 新增「副歌速刷」开关胶囊 + 清空按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "播放队列",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = textPrimary,
                            fontSize = 18.sp
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ConceptPrimary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "${(currentIndex + 1).coerceAtMost(queue.size.coerceAtLeast(1))}/${queue.size}",
                            color = ConceptPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    // 循环模式快捷切换胶囊
                    val (modeIcon, modeName) = when (playMode) {
                        PlayMode.REPEAT_ALL -> Icons.Rounded.Repeat to "列表循环"
                        PlayMode.REPEAT_ONE -> Icons.Rounded.RepeatOne to "单曲循环"
                        PlayMode.SHUFFLE -> Icons.Rounded.Shuffle to "随机漫游"
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cardBg,
                        modifier = Modifier.softCardClickable(onClick = onCyclePlayMode)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(modeIcon, contentDescription = null, tint = textSecondary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(modeName, color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // 播放队列内直接集成「副歌速刷」开关胶囊
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (climaxModeEnabled) VipGold.copy(alpha = 0.20f) else cardBg,
                        modifier = Modifier.softCardClickable {
                            onToggleClimaxMode()
                            Toast.makeText(
                                context,
                                if (!climaxModeEnabled) "已开启「副歌速刷」高潮连播模式" else "已关闭「副歌速刷」",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Whatshot,
                                contentDescription = "副歌速刷",
                                tint = if (climaxModeEnabled) VipGold else textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "副歌速刷",
                                color = if (climaxModeEnabled) VipGold else textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (climaxModeEnabled) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("清空", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (queue.size > 2) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = ConceptPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "AI 漫游:",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        listOf(
                            "sleep" to "🌙 睡前渐缓",
                            "commute" to "⚡ 通勤渐燃",
                            "harmonic" to "🌊 声学漫游"
                        ).forEach { (modeKey, label) ->
                            val isSelected = activeMoodMode == modeKey
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) ConceptPrimary else ConceptPrimary.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable {
                                        activeMoodMode = modeKey
                                        val msg = onReorderQueueByMood(modeKey)
                                        if (msg.isNotBlank()) {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else ConceptPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = textSecondary.copy(alpha = 0.45f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "播放队列为空，去挑选喜欢的音乐吧",
                            color = textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    itemsIndexed(queue, key = { idx, item -> "${item.id}_$idx" }) { index, item ->
                        val isPlayingThis = index == currentIndex
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isPlayingThis) {
                                ConceptPrimary.copy(alpha = if (isDark) 0.16f else 0.10f)
                            } else {
                                cardBg.copy(alpha = if (isDark) 0.55f else 0.72f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .softCardClickable { onSongClick(item) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (isPlayingThis) {
                                        PlayingAudioWaveIndicator()
                                    } else {
                                        Text(
                                            text = (index + 1).toString().padStart(2, '0'),
                                            color = textSecondary.copy(alpha = 0.65f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ConceptPrimary.copy(alpha = 0.10f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.coverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = item.coverUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = ConceptPrimary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = if (isPlayingThis) ConceptPrimary else textPrimary,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (item.isHiRes) {
                                            BadgeTag(text = "Hi-Res", color = VipGold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        } else if (item.isLossless) {
                                            BadgeTag(text = "SQ", color = ConceptPrimary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = item.artist,
                                            color = if (isPlayingThis) ConceptPrimary.copy(alpha = 0.82f) else textSecondary,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (!isPlayingThis && index != currentIndex + 1) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = textPrimary.copy(alpha = 0.06f),
                                        modifier = Modifier.softCardClickable {
                                            val targetIdx = (currentIndex + 1).coerceIn(0, queue.lastIndex.coerceAtLeast(0))
                                            onMoveQueueItem(index, targetIdx)
                                            Toast.makeText(context, "已将「${item.title}」移至下一首播放", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.VerticalAlignTop,
                                                contentDescription = "下一首播",
                                                tint = textSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("置顶", color = textSecondary, fontSize = 10.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                IconButton(
                                    onClick = { onRemoveSong(item) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "移除",
                                        tint = textSecondary.copy(alpha = 0.65f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * 播放页「更多功能控制台」抽屉重构：
 * 1. 移除右上角多余的「收藏歌单」按钮；
 * 2. 封面样式精简为「黑胶封面」与「圆角方形（不旋转）」两款；
 * 3. 「副歌速刷」改名为「副歌速刷」；
 * 4. 移除「心流舱」按钮，替换为重构后的「歌词海报」工坊入口；
 * 5. 「悬浮词」重构为「桌面歌词」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerMoreControlSheet(
    song: Song,
    coverStyle: CoverStyle,
    playerBackgroundStyle: PlayerBackgroundStyle,
    dynamicCoverAccentEnabled: Boolean,
    currentQualityLabel: String,
    climaxModeEnabled: Boolean,
    accompanyModeEnabled: Boolean,
    playbackSpeed: Float,
    sleepTimerRemainingSec: Long,
    sleepStopAfterCurrentSong: Boolean,
    sleepPendingStopAtTrackEnd: Boolean,
    accentColor: Color = ConceptPrimary,
    onSelectCoverStyle: (CoverStyle) -> Unit,
    onSelectPlayerBackgroundStyle: (PlayerBackgroundStyle) -> Unit,
    onSetDynamicCoverAccentEnabled: (Boolean) -> Unit,
    onToggleClimaxMode: () -> Unit,
    onToggleAccompanyMode: () -> Unit,
    onOpenSpeedPitch: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQualitySelect: () -> Unit,
    onOpenSongDetail: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBg = conceptSheetContainerColor()
    val tileBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = textPrimary,
        windowInsets = WindowInsets(0, 0, 0, 0),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides textPrimary) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .conceptSheetSpringEntrance()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 顶部当前曲目名片
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = song.coverUrl,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = accentColor)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${song.artist} · ${song.album.ifBlank { "单曲" }}",
                            color = textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 2. 双封面样式选择 + 封面动态取色开关
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "封面样式与智能取色",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CoverStyle.entries.forEach { style ->
                            val selected = coverStyle == style
                            val icon = when (style) {
                                CoverStyle.VINYL -> Icons.Rounded.Album
                                CoverStyle.SQUARE -> Icons.Rounded.CropSquare
                            }
                            val desc = when (style) {
                                CoverStyle.VINYL -> "拟真黑胶唱盘与S型唱臂"
                                CoverStyle.SQUARE -> "高定圆角方形封面 (不旋转)"
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) accentColor.copy(alpha = 0.16f) else tileBg,
                                contentColor = textPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (selected) 1.2.dp else 0.dp,
                                        color = if (selected) accentColor else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .softCardClickable { onSelectCoverStyle(style) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = style.label,
                                        tint = if (selected) accentColor else textSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = style.label,
                                            color = if (selected) accentColor else textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = desc,
                                            color = textSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 封面智能动态取色开关
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = tileBg,
                        contentColor = textPrimary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "封面智能动态取色",
                                    color = textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "提取歌曲封面主色调渲染播放键与彗星进度条，副歌高潮保持金色",
                                    color = textSecondary,
                                    fontSize = 10.5.sp
                                )
                            }
                            Switch(
                                checked = dynamicCoverAccentEnabled,
                                onCheckedChange = onSetDynamicCoverAccentEnabled,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                            )
                        }
                    }
                }

                // 3. 播放页背景意境切换（5款高定背景效果）
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "播放页背景显示效果",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerBackgroundStyle.entries.forEach { bgStyle ->
                            val selected = playerBackgroundStyle == bgStyle
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) accentColor.copy(alpha = 0.18f) else tileBg,
                                contentColor = textPrimary,
                                modifier = Modifier
                                    .width(126.dp)
                                    .border(
                                        width = if (selected) 1.2.dp else 0.dp,
                                        color = if (selected) accentColor else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .softCardClickable { onSelectPlayerBackgroundStyle(bgStyle) }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        text = bgStyle.label,
                                        color = if (selected) accentColor else textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = bgStyle.subtitle,
                                        color = textSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. 音频实验室与播放工具（已删除「音效」、「桌面歌词」、「歌词海报」，精简为5项核心工具）
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "音频实验室与播放工具",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BentoControlTile(
                            icon = Icons.Rounded.Whatshot,
                            title = "副歌速刷",
                            subtitle = if (climaxModeEnabled) "高潮连播已开" else "30秒精华速听",
                            active = climaxModeEnabled,
                            activeColor = VipGold,
                            onClick = onToggleClimaxMode,
                            modifier = Modifier.weight(1f)
                        )
                        BentoControlTile(
                            icon = Icons.Rounded.Mic,
                            title = "KTV 伴奏",
                            subtitle = if (accompanyModeEnabled) "伴奏模式中" else "切换纯伴奏",
                            active = accompanyModeEnabled,
                            activeColor = accentColor,
                            onClick = onToggleAccompanyMode,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BentoControlTile(
                            icon = Icons.Rounded.Speed,
                            title = "倍速与变调",
                            subtitle = "${String.format(Locale.getDefault(), "%.2f", playbackSpeed)}x 播放",
                            active = playbackSpeed != 1.0f,
                            activeColor = accentColor,
                            onClick = onOpenSpeedPitch,
                            modifier = Modifier.weight(1f)
                        )
                        val sleepDesc = when {
                            sleepTimerRemainingSec > 0L -> formatDuration(sleepTimerRemainingSec * 1000L)
                            sleepPendingStopAtTrackEnd -> "播完本首停"
                            sleepStopAfterCurrentSong -> "已记忆播完停"
                            else -> "定时渐弱"
                        }
                        BentoControlTile(
                            icon = Icons.Rounded.Bedtime,
                            title = "睡眠定时",
                            subtitle = sleepDesc,
                            active = sleepTimerRemainingSec > 0L || sleepPendingStopAtTrackEnd,
                            activeColor = accentColor,
                            onClick = onOpenSleepTimer,
                            modifier = Modifier.weight(1f)
                        )
                        BentoControlTile(
                            icon = Icons.Rounded.HighQuality,
                            title = "音质规格",
                            subtitle = currentQualityLabel,
                            active = true,
                            activeColor = accentColor,
                            onClick = onOpenQualitySelect,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 5. 底部音频技术参数按钮
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = tileBg,
                    contentColor = textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .softCardClickable(onClick = onOpenSongDetail)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("查看音频技术参数、采样率与文件特征码 (Hash)", color = textPrimary, fontSize = 12.5.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BentoControlTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    activeColor: Color = ConceptPrimary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) activeColor.copy(alpha = 0.16f) else tileBg,
        contentColor = textPrimary,
        modifier = modifier.softCardClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (active) activeColor else textPrimary.copy(alpha = 0.82f),
                modifier = Modifier.size(21.dp)
            )
            Text(
                text = title,
                color = if (active) activeColor else textPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = if (active) activeColor.copy(alpha = 0.85f) else textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QualitySelectDialog(
    availableQualities: List<AudioQuality>,
    currentQualityLabel: String,
    onDismiss: () -> Unit,
    onSelectQuality: (AudioQuality) -> Unit
) {
    val context = LocalContext.current
    val validQualities = availableQualities.ifEmpty { listOf(AudioQuality.STANDARD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择音质档位", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "当前音源实际可获取档位：",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                validQualities.forEach { quality ->
                    val desc = when (quality) {
                        AudioQuality.HI_RES -> "Hi-Res 音质 (24bit FLAC 高解析度无损)"
                        AudioQuality.LOSSLESS -> "SQ 无损音质 (16bit FLAC 无损)"
                        AudioQuality.HIGH -> "HQ 高品音质 (320kbps MP3)"
                        AudioQuality.STANDARD -> "标准 128K (128kbps OGG/MP3)"
                    }
                    val isSelected = currentQualityLabel.contains(quality.label.take(2))
                    val onSelect = {
                        onSelectQuality(quality)
                        Toast.makeText(context, "已切换音质偏好：${quality.label}", Toast.LENGTH_SHORT).show()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .softClickable { onSelect() }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = quality.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) ConceptPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 睡眠定时器弹窗（增强版：“倒计时结束后播完当前歌曲再停”开关具备持久化记忆能力）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerDialog(
    remainingSec: Long,
    stopAfterCurrentSong: Boolean,
    pendingStopAtTrackEnd: Boolean,
    onToggleStopAfterSongPref: (Boolean) -> Unit,
    onSetTimer: (minutes: Int, stopAfterCurrentSong: Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var stopAfterSong by remember(stopAfterCurrentSong) { mutableStateOf(stopAfterCurrentSong) }
    val presets = listOf(15, 30, 45, 60, 90)
    val isTimerRunning = remainingSec > 0L || pendingStopAtTrackEnd

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = ConceptPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("睡眠定时器", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isTimerRunning) {
                    Surface(
                        color = ConceptPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val statusText = if (remainingSec > 0L) {
                            "倒计时进行中：剩余 ${formatDuration(remainingSec * 1000L)}" +
                                if (stopAfterSong) "（到点后播完当前曲目停止）" else "（含5秒音量渐弱）"
                        } else {
                            "已开启：将在当前曲目播完后自动停止"
                        }
                        Text(
                            text = statusText,
                            color = ConceptPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Text(
                    text = "选择定时关闭时长（结束前5秒音量平滑渐弱）：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presets.forEach { mins ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                onSetTimer(mins, stopAfterSong)
                                Toast.makeText(
                                    context,
                                    "已设置 $mins 分钟后睡眠停止" + if (stopAfterSong) "（播完当前歌曲再停）" else "",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = { Text("${mins}分钟") }
                        )
                    }
                    FilterChip(
                        selected = pendingStopAtTrackEnd && remainingSec == 0L,
                        onClick = {
                            onSetTimer(0, true)
                            Toast.makeText(context, "将在当前歌曲播完后停止播放", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text("仅播完当前这首") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("播完当前歌曲再停", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            text = "已支持自动记忆此偏好，无需每次重新打开",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = stopAfterSong,
                        onCheckedChange = {
                            stopAfterSong = it
                            onToggleStopAfterSongPref(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                    )
                }
            }
        },
        dismissButton = {
            if (isTimerRunning) {
                TextButton(
                    onClick = {
                        onCancelTimer()
                        Toast.makeText(context, "已关闭睡眠定时器", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("关闭定时", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedPitchDialog(
    speed: Float,
    pitch: Float,
    linkPitchToSpeed: Boolean,
    onSetSpeed: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onSetLinkPitch: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val speedPresets = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, contentDescription = null, tint = ConceptPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("倍速与变调控制", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("播放倍速", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${String.format(Locale.getDefault(), "%.2f", speed)}x",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ConceptPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = speed,
                        onValueChange = onSetSpeed,
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        speedPresets.forEach { preset ->
                            FilterChip(
                                selected = abs(speed - preset) < 0.03f,
                                onClick = { onSetSpeed(preset) },
                                label = { Text("${preset}x", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("音频音调 (Pitch)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${String.format(Locale.getDefault(), "%.2f", pitch)}x",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ConceptPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = pitch,
                        onValueChange = onSetPitch,
                        enabled = !linkPitchToSpeed,
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("黑胶变速联动变调", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "开启后音调随倍速同步升降，还原黑胶慢放胶片感",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = linkPitchToSpeed,
                        onCheckedChange = onSetLinkPitch,
                        colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("恢复默认 (1.0x)")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
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
