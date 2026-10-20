package com.concept.music.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
    showTranslation: Boolean = true,
    showPhonetic: Boolean = true,
    lyricFontSize: Float = 22f,
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
    onToggleTranslation: () -> Unit = {},
    onTogglePhonetic: () -> Unit = {},
    onSetLyricFontSize: (Float) -> Unit = {},
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

    // 封面动态流光取色
    var extractedColors by remember { mutableStateOf(Color(0xFF0E1A2F) to Color(0xFF13233F)) }
    LaunchedEffect(song.coverUrl) {
        if (song.coverUrl.isNotBlank()) {
            val pair = withContext(Dispatchers.IO) {
                extractCoverMeshColors(context, song.coverUrl)
            }
            if (pair != null) {
                extractedColors = pair
            }
        } else {
            extractedColors = Color(0xFF0E1A2F) to Color(0xFF13233F)
        }
    }
    val meshColorPrimary by animateColorAsState(
        targetValue = extractedColors.first,
        animationSpec = tween(durationMillis = 900),
        label = "meshPrimary"
    )
    val meshColorSecondary by animateColorAsState(
        targetValue = extractedColors.second,
        animationSpec = tween(durationMillis = 900),
        label = "meshSecondary"
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
        // 1. 沉浸式高斯柔光氛围背景（24x24 超轻量降采样纹理 + 动态双核网格流光，兼顾深邃模糊质感与 120fps 零 GPU 负担）
        if (song.coverUrl.isNotEmpty()) {
            val bgImageModel = remember(song.coverUrl) {
                ImageRequest.Builder(context)
                    .data(song.coverUrl)
                    .size(28, 28)
                    .allowHardware(true)
                    .crossfade(false)
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

        val dimAlpha = if (showLyricView) lyricBackgroundDim.coerceIn(0.40f, 0.92f) else 0.80f
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

        // 2. 主体内容区：封面播放页与重构后的极简歌词页
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部标题栏：退出按钮与歌名高度严格对齐、歌名加大字号居中、歌手换行居中、移除右上角音质按钮、自行优雅排版输出设备与音质标识
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
                    // ==================== 图一：重构后的封面播放页 ====================
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
                        climaxStartMs = song.effectiveClimaxStartMs,
                        climaxEndMs = song.effectiveClimaxEndMs,
                        highlightClimaxRange = climaxModeEnabled && !climaxUnlockedForCurrentSong,
                        accentPrimary = meshColorPrimary,
                        showDoubleTapHeart = showDoubleTapHeart,
                        onTriggerDoubleTapHeart = {
                            showDoubleTapHeart = true
                            if (!isFavorite) onToggleFavorite()
                        },
                        onClearDoubleTapHeart = { showDoubleTapHeart = false },
                        onOpenLyricView = { showLyricView = true },
                        onToggleFavorite = onToggleFavorite,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenAudioEffect = openViperAction,
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
                    // ==================== 图二：推翻重构后的极简高定歌词页 ====================
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
                        lyricAlignment = lyricAlignment,
                        lyricWordByWord = lyricWordByWord,
                        lyricBlurEnabled = lyricBlurEnabled,
                        lyricBlurIntensity = lyricBlurIntensity,
                        lyricLineSpacing = lyricLineSpacing,
                        lyricHighlightTheme = lyricHighlightTheme,
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

    // 播放队列弹窗（新增「副歌速刷」快捷开关）
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

    // 更多设置抽屉（移除右上角多余的收藏歌单按钮、移除心流舱、改名副歌速刷、悬浮词改为桌面歌词、精简双封面样式）
    if (showMoreControlSheet) {
        PlayerMoreControlSheet(
            song = song,
            coverStyle = coverStyle,
            currentQualityLabel = currentQualityLabel,
            climaxModeEnabled = climaxModeEnabled,
            accompanyModeEnabled = accompanyModeEnabled,
            playbackSpeed = playbackSpeed,
            sleepTimerRemainingSec = sleepTimerRemainingSec,
            sleepStopAfterCurrentSong = sleepStopAfterCurrentSong,
            sleepPendingStopAtTrackEnd = sleepPendingStopAtTrackEnd,
            onSelectCoverStyle = onSelectCoverStyle,
            onToggleClimaxMode = onToggleClimaxMode,
            onToggleAccompanyMode = onToggleAccompanyMode,
            onOpenAudioEffect = {
                showMoreControlSheet = false
                openViperAction()
            },
            onOpenLyricPoster = {
                showMoreControlSheet = false
                showLyricPosterDialog = true
            },
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

    // 完善版歌词设置面板
    if (showLyricSettingsDialog) {
        LyricSettingsDialog(
            fontSize = lyricFontSize,
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
            onSetFontSize = onSetLyricFontSize,
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
            onOpenPoster = {
                showLyricSettingsDialog = false
                showLyricPosterDialog = true
            },
            onDismiss = { showLyricSettingsDialog = false }
        )
    }

    // 睡眠定时器弹窗（带“播完当前歌曲再停”记忆功能）
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
 * 顶部区域重构：
 * 1. 退出按钮与歌名处于同一水平基准线（高度严格对齐）；
 * 2. 歌名加大字号（20.sp ExtraBold）并严格屏幕居中；
 * 3. 歌手换行居中显示；
 * 4. 音质选择按钮不再放在右上角；
 * 5. 在封面页歌手下方精致居中排布「音频输出设备标识 · 音质切换徽章」（歌词页自动隐藏该徽章行以保持极致纯粹）；
 * 6. 原歌手下方的三个按钮（副歌速刷、伴奏、心流舱）已彻底移除。
 */
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
 */
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
    climaxStartMs: Long,
    climaxEndMs: Long,
    highlightClimaxRange: Boolean,
    accentPrimary: Color,
    showDoubleTapHeart: Boolean,
    onTriggerDoubleTapHeart: () -> Unit,
    onClearDoubleTapHeart: () -> Unit,
    onOpenLyricView: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenAudioEffect: () -> Unit,
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
        // 1. 封面舞台（支持单击进歌词、双击点赞、左右滑动切歌；仅保留「重构黑胶封面」与「静态圆角方形封面」两种样式）
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

        // 2. 歌词预览与右侧收藏按钮区（去除圆角框，开放式双行流光预览 + 收藏按钮置于屏幕右侧）
        FramelessLyricPreviewWithFavoriteRow(
            lyricData = lyricData,
            currentLyricIndex = currentLyricIndex,
            currentPositionMs = currentPositionMs,
            showTranslation = showTranslation,
            isFavorite = isFavorite,
            onLyricClick = onOpenLyricView,
            onToggleFavorite = onToggleFavorite
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. 进度条上方三键功能栏：严格按「添加到歌单、音效、更多」顺序排布，歌词海报按钮已移除
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // (1) 添加到歌单
            PlayerActionIconButton(
                onClick = onAddToPlaylist,
                contentDescription = "添加到歌单"
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = "添加到歌单",
                    tint = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.size(25.dp)
                )
            }

            // (2) 音效（带激活光点提示）
            PlayerActionIconButton(
                onClick = onOpenAudioEffect,
                contentDescription = "蝰蛇音效"
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "蝰蛇音效",
                        tint = if (viperEnabled) ConceptPrimary else Color.White.copy(alpha = 0.86f),
                        modifier = Modifier.size(24.dp)
                    )
                    if (viperEnabled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-2).dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ConceptCyanAccent)
                        )
                    }
                }
            }

            // (3) 更多
            PlayerActionIconButton(
                onClick = onOpenMoreSheet,
                contentDescription = "更多设置"
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = "更多设置",
                    tint = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. 彗星拖尾进度条 + 五大重绘灵动控件 + 底部评论按钮
        PlayerControlsSection(
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            climaxStartMs = climaxStartMs,
            climaxEndMs = climaxEndMs,
            highlightClimaxRange = highlightClimaxRange,
            isPlaying = isPlaying,
            playMode = playMode,
            commentCount = commentCount,
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
                pressScale = 0.86f,
                pressAlpha = 0.75f,
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
                val radius = size.minDimension / 2f
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
 * 封面下方无框歌词预览与右侧收藏按钮行：
 * - 去除原来的圆角框背景，采用通透大气的无框双行排版；
 * - 支持实时 KRC 逐字流光高亮；
 * - 收藏（红心）按钮放置在屏幕右侧，带弹簧绽放动效。
 */
@Composable
private fun FramelessLyricPreviewWithFavoriteRow(
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    showTranslation: Boolean,
    isFavorite: Boolean,
    onLyricClick: () -> Unit,
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
        targetValue = if (isFavorite) 1.12f else 1.0f,
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧：无圆角框开放式双行歌词阅览
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
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
                                val prog = ((currentPositionMs - word.startTimeMs).toFloat() /
                                    word.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                val brush = Brush.horizontalGradient(
                                    0.0f to Color.White,
                                    (prog - 0.08f).coerceAtLeast(0f) to ConceptCyanAccent,
                                    (prog + 0.08f).coerceAtMost(1f) to Color.White.copy(alpha = 0.52f),
                                    1.0f to Color.White.copy(alpha = 0.52f)
                                )
                                withStyle(SpanStyle(brush = brush, fontWeight = FontWeight.ExtraBold)) {
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

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subText.ifBlank { "♪ • • •" },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 右侧：收藏按钮（移至屏幕右侧）
        Box(
            modifier = Modifier
                .size(42.dp)
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
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "收藏",
                tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * 封面播放页底部控制台：
 * 1. 彗星拖尾流光进度条 (CometTailProgressBar)：
 *    - 宽度加粗，播放时前端无传统圆球，呈现渐进流光彗星核与星云外溢光晕（超越图三设计感）；
 *    - 触摸按住拖动时触发弹簧变宽反馈动画（6.5dp -> 12dp），前端化为超新星能量光梭并浮现时间胶囊；
 *    - 删除原副歌时间的文字标识。
 * 2. 五大控件按钮重绘与灵动动画：
 *    - 播放模式、上一首、播放/暂停、下一首、播放列表五个按钮全部采用 Canvas 矢量重绘，
 *      其中播放模式与播放/暂停具备平滑形态与旋转弹性动画；
 * 3. 底部评论按钮保持不变。
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
        // 1. 彗星流光拖尾进度条（触摸按住自动变宽 + 前端能量光梭形变 + 浮动时间预览）
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

        // 左右时间戳行（已删除副歌时间文字标识）
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
                    color = if (isInteractingWithBar) Color.White else Color.White.copy(alpha = 0.52f),
                    fontSize = 11.5.sp,
                    fontWeight = if (isInteractingWithBar) FontWeight.Bold else FontWeight.Medium
                )
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. 重绘五大播放控件行（播放模式、上一首、播放/暂停、下一首、播放列表）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 控件 1：重绘灵动播放模式切换按钮
            AnimatedPlayModeButton(
                playMode = playMode,
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

            // 控件 2：重绘上一首按钮（带回弹位移微动效）
            RedrawnSkipButton(
                isNext = false,
                onClick = onPrevious
            )

            // 控件 3：重绘核心播放/暂停流体形变按钮
            AnimatedPlayPauseCenterButton(
                isPlaying = isPlaying,
                onClick = onTogglePlay
            )

            // 控件 4：重绘下一首按钮（带推进位移微动效）
            RedrawnSkipButton(
                isNext = true,
                onClick = onNext
            )

            // 控件 5：重绘播放队列按钮
            RedrawnQueueButton(
                onClick = onQueueClick
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 底部居中评论胶囊按钮（保持不变）
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
 * 彗星拖尾流光进度条 (CometTailProgressBar - 图三灵感高定版)
 * - 常态轨道高度 6.5.dp，按住拖动时通过弹簧物理动画再次变宽至 12.dp；
 * - 播放时进度条前端无生硬圆球，而是「流光渐变尾迹 + 炽白彗星核 + 柔和星云光晕」；
 * - 触摸按住拖动时，彗星核觉醒为「超新星能量光梭（垂直流光胶囊 + 双环脉冲光晕）」，并在上方浮现毛玻璃时间预览胶囊。
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
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val trackHeightDp by animateDpAsState(
        targetValue = if (isInteracting) 12.dp else 6.5.dp,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cometTrackHeight"
    )
    val flareScale by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cometFlareScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "cometPulse")
    val cometPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cometBreath"
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
                        val released = tryAwaitRelease()
                        if (released) {
                            onDragEnd()
                        } else {
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
                modifier = Modifier.border(
                    width = 0.8.dp,
                    color = Color.White.copy(alpha = 0.22f),
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
                .height(32.dp)
        ) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f
            val barH = trackHeightDp.toPx()
            val barRadius = CornerRadius(barH / 2f, barH / 2f)
            val topY = centerY - barH / 2f

            // 1. 未播放背景底轨
            drawRoundRect(
                color = Color.White.copy(alpha = if (isInteracting) 0.22f else 0.15f),
                topLeft = Offset(0f, topY),
                size = Size(w, barH),
                cornerRadius = barRadius
            )

            // 2. 副歌高潮区间微光标记（仅在底轨内嵌柔和流金层，无多余文字）
            if (climaxEndFrac > climaxStartFrac) {
                val cStart = w * climaxStartFrac
                val cWidth = (w * (climaxEndFrac - climaxStartFrac)).coerceAtLeast(6.dp.toPx())
                drawRoundRect(
                    color = VipGold.copy(alpha = if (highlightClimaxRange) 0.45f else 0.18f),
                    topLeft = Offset(cStart, topY),
                    size = Size(cWidth, barH),
                    cornerRadius = barRadius
                )
            }

            val headX = (w * fraction.coerceIn(0f, 1f)).coerceAtLeast(barH)
            val tailLength = (w * 0.28f).coerceAtLeast(48.dp.toPx())
            val tailStartX = (headX - tailLength).coerceAtLeast(0f)

            // 3. 已播放主轨道基底（柔和渐变）
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF476582).copy(alpha = 0.55f),
                        ConceptPrimary.copy(alpha = 0.82f),
                        Color(0xFFFFB088).copy(alpha = 0.92f),
                        Color.White
                    ),
                    startX = 0f,
                    endX = headX
                ),
                topLeft = Offset(0f, topY),
                size = Size(headX, barH),
                cornerRadius = barRadius
            )

            // 4. 彗星外溢星云辉光尾迹 (使用 NativeCanvas BlurMaskFilter 营造超越图三的真光晕过渡)
            val pulseFactor = if (isPlaying) cometPulse else 1f
            val glowBlurPx = (if (isInteracting) 16.dp.toPx() else 10.dp.toPx()) * pulseFactor
            drawIntoCanvas { canvas ->
                val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    maskFilter = BlurMaskFilter(glowBlurPx, BlurMaskFilter.Blur.NORMAL)
                    shader = android.graphics.LinearGradient(
                        tailStartX,
                        centerY,
                        headX + 6.dp.toPx(),
                        centerY,
                        intArrayOf(
                            android.graphics.Color.TRANSPARENT,
                            ConceptPrimary.copy(alpha = 0.35f).toArgb(),
                            Color(0xFFFFB88C).copy(alpha = 0.72f).toArgb(),
                            Color.White.copy(alpha = 0.95f).toArgb()
                        ),
                        floatArrayOf(0f, 0.45f, 0.82f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
                val glowSpreadY = barH * (if (isInteracting) 1.35f else 1.15f)
                canvas.nativeCanvas.drawRoundRect(
                    tailStartX,
                    centerY - glowSpreadY,
                    headX + 4.dp.toPx(),
                    centerY + glowSpreadY,
                    glowSpreadY,
                    glowSpreadY,
                    glowPaint
                )
            }

            // 5. 彗星高光核心内轨（由透明平滑汇聚至炽白彗星尖端，无生硬圆形滑块）
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFFFB88C).copy(alpha = 0.75f),
                        Color(0xFFFFF3E6),
                        Color.White
                    ),
                    startX = tailStartX,
                    endX = headX
                ),
                topLeft = Offset(tailStartX, topY),
                size = Size((headX - tailStartX).coerceAtLeast(barH), barH),
                cornerRadius = barRadius
            )

            // 6. 触摸按住拖动时的前端能量态变化：超新星能量光梭 (Supernova Energy Flare Capsule)
            if (flareScale > 0.01f) {
                val capsuleW = 7.dp.toPx() * (0.6f + 0.4f * flareScale)
                val capsuleH = (barH + 10.dp.toPx() * flareScale)
                val capLeft = (headX - capsuleW / 2f).coerceIn(0f, w - capsuleW)
                val capTop = centerY - capsuleH / 2f

                // 外围脉冲能量光环
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f * flareScale),
                            ConceptCyanAccent.copy(alpha = 0.25f * flareScale),
                            Color.Transparent
                        ),
                        center = Offset(headX, centerY),
                        radius = 18.dp.toPx() * flareScale
                    ),
                    radius = 18.dp.toPx() * flareScale,
                    center = Offset(headX, centerY)
                )

                // 垂直流光能量梭核心
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(capLeft, capTop),
                    size = Size(capsuleW, capsuleH),
                    cornerRadius = CornerRadius(capsuleW / 2f, capsuleW / 2f)
                )
            }
        }
    }
}

/**
 * 重绘控件 1：灵动播放模式切换按钮（带 3D 旋转与弹性缩放过渡 + 自定义矢量绘制）
 */
@Composable
private fun AnimatedPlayModeButton(
    playMode: PlayMode,
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
            .size(44.dp)
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
                    .size(24.dp)
                    .graphicsLayer {
                        rotationZ = if (mode == PlayMode.REPEAT_ALL) spinDeg % 360f else 0f
                    }
            ) {
                val w = size.width
                val h = size.height
                val stroke = 2.1.dp.toPx()
                val tint = Color.White.copy(alpha = 0.88f)

                when (mode) {
                    PlayMode.REPEAT_ALL -> {
                        // 双圆弧回环箭头
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
                        // 右上与左下箭头翼
                        val topTip = Offset(w * 0.78f, h * 0.26f)
                        drawLine(tint, topTip, Offset(topTip.x - 4.dp.toPx(), topTip.y - 1.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(tint, topTip, Offset(topTip.x - 1.dp.toPx(), topTip.y + 4.dp.toPx()), stroke, StrokeCap.Round)

                        val botTip = Offset(w * 0.22f, h * 0.74f)
                        drawLine(tint, botTip, Offset(botTip.x + 4.dp.toPx(), botTip.y + 1.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(tint, botTip, Offset(botTip.x + 1.dp.toPx(), botTip.y - 4.dp.toPx()), stroke, StrokeCap.Round)
                    }

                    PlayMode.REPEAT_ONE -> {
                        val pad = w * 0.14f
                        val rectSize = Size(w - pad * 2, h - pad * 2)
                        drawArc(
                            color = ConceptCyanAccent,
                            startAngle = -155f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = rectSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = ConceptCyanAccent,
                            startAngle = 25f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = rectSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        // 中心数字 1 竖柱
                        drawLine(
                            color = Color.White,
                            start = Offset(w * 0.50f, h * 0.34f),
                            end = Offset(w * 0.50f, h * 0.66f),
                            strokeWidth = stroke * 1.05f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(w * 0.42f, h * 0.40f),
                            end = Offset(w * 0.50f, h * 0.34f),
                            strokeWidth = stroke * 0.9f,
                            cap = StrokeCap.Round
                        )
                    }

                    PlayMode.SHUFFLE -> {
                        // 灵动双交织流线随机图标
                        val path1 = Path().apply {
                            moveTo(w * 0.15f, h * 0.32f)
                            cubicTo(w * 0.45f, h * 0.32f, w * 0.55f, h * 0.68f, w * 0.85f, h * 0.68f)
                        }
                        val path2 = Path().apply {
                            moveTo(w * 0.15f, h * 0.68f)
                            cubicTo(w * 0.45f, h * 0.68f, w * 0.55f, h * 0.32f, w * 0.85f, h * 0.32f)
                        }
                        drawPath(path1, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
                        drawPath(path2, ConceptCyanAccent, style = Stroke(width = stroke, cap = StrokeCap.Round))

                        // 右侧双箭头尖
                        val tipTop = Offset(w * 0.86f, h * 0.32f)
                        drawLine(ConceptCyanAccent, tipTop, Offset(tipTop.x - 3.5.dp.toPx(), tipTop.y - 3.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(ConceptCyanAccent, tipTop, Offset(tipTop.x - 3.5.dp.toPx(), tipTop.y + 3.dp.toPx()), stroke, StrokeCap.Round)

                        val tipBot = Offset(w * 0.86f, h * 0.68f)
                        drawLine(tint, tipBot, Offset(tipBot.x - 3.5.dp.toPx(), tipBot.y - 3.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(tint, tipBot, Offset(tipBot.x - 3.5.dp.toPx(), tipBot.y + 3.dp.toPx()), stroke, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

/**
 * 重绘控件 2 & 4：上一首 / 下一首双翼流线按钮（带点击方向感弹簧冲量动画）
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
                        val target = if (isNext) 14f else -14f
                        nudgeX.animateTo(target, tween(90))
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
            val dir = if (isNext) 1f else -1f
            val centerX = w / 2f

            // 绘制圆润主三角翼
            val triPath = Path().apply {
                if (isNext) {
                    moveTo(w * 0.22f, h * 0.22f)
                    lineTo(w * 0.68f, h * 0.50f)
                    lineTo(w * 0.22f, h * 0.78f)
                } else {
                    moveTo(w * 0.78f, h * 0.22f)
                    lineTo(w * 0.32f, h * 0.50f)
                    lineTo(w * 0.78f, h * 0.78f)
                }
                close()
            }
            drawPath(
                path = triPath,
                color = Color.White,
                style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(path = triPath, color = Color.White)

            // 绘制终端圆角竖杠
            val barX = if (isNext) w * 0.78f else w * 0.22f
            drawLine(
                color = Color.White,
                start = Offset(barX, h * 0.24f),
                end = Offset(barX, h * 0.76f),
                strokeWidth = 3.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 重绘控件 3：核心播放/暂停流体形变按钮 (AnimatedPlayPauseCenterButton)
 * - 具备呼吸流光外环 + 播放三角与双竖杠暂停之间的连续弹性缩放旋转过渡动效
 */
@Composable
private fun AnimatedPlayPauseCenterButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val morphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playPauseMorph"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "playButtonAura")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )

    Box(
        modifier = Modifier.size(76.dp),
        contentAlignment = Alignment.Center
    ) {
        // 播放状态下的柔和呼吸光环
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = auraScale
                        scaleY = auraScale
                        alpha = 0.32f
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(ConceptPrimary, ConceptCyanAccent, Color.Transparent)
                        )
                    )
            )
        }

        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 12.dp,
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ConceptPrimary,
                            Color(0xFF2878F0),
                            ConceptCyanAccent.copy(alpha = 0.90f)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    color = Color.White.copy(alpha = 0.28f),
                    shape = CircleShape
                )
                .softClickable(
                    pressScale = 0.88f,
                    onClick = onClick
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            // 切换瞬间产生灵动微旋与弹性缩放
                            rotationZ = (1f - morphProgress) * -90f + 90f * (1f - morphProgress)
                            val bounce = 1f - 0.14f * sin(morphProgress * PI.toFloat())
                            scaleX = bounce
                            scaleY = bounce
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    // 1. 暂停双圆角竖柱（随 morphProgress 0 -> 1 展开）
                    if (morphProgress > 0.05f) {
                        val barW = 4.5.dp.toPx()
                        val barH = h * (0.55f + 0.25f * morphProgress)
                        val topY = (h - barH) / 2f
                        val gapOffset = (w * 0.19f) * morphProgress
                        val centerX = w / 2f

                        drawRoundRect(
                            color = Color.White.copy(alpha = morphProgress),
                            topLeft = Offset(centerX - gapOffset - barW / 2f, topY),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = morphProgress),
                            topLeft = Offset(centerX + gapOffset - barW / 2f, topY),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }

                    // 2. 播放圆润三角（随 morphProgress 1 -> 0 显现）
                    val playAlpha = (1f - morphProgress).coerceIn(0f, 1f)
                    if (playAlpha > 0.05f) {
                        val playPath = Path().apply {
                            moveTo(w * 0.26f, h * 0.18f)
                            lineTo(w * 0.82f, h * 0.50f)
                            lineTo(w * 0.26f, h * 0.82f)
                            close()
                        }
                        drawPath(
                            path = playPath,
                            color = Color.White.copy(alpha = playAlpha),
                            style = Stroke(width = 3.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = playPath,
                            color = Color.White.copy(alpha = playAlpha)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 重绘控件 5：韵律播放列表/队列按钮
 */
@Composable
private fun RedrawnQueueButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .softClickable(
                pressScale = 0.84f,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2.2.dp.toPx()
            val tint = Color.White.copy(alpha = 0.88f)

            // 三条错落圆角韵律横线
            drawLine(tint, Offset(w * 0.14f, h * 0.26f), Offset(w * 0.78f, h * 0.26f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.62f, h * 0.50f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.14f, h * 0.74f), Offset(w * 0.50f, h * 0.74f), stroke, StrokeCap.Round)

            // 右下角灵动音符
            val noteCenter = Offset(w * 0.72f, h * 0.74f)
            drawCircle(ConceptCyanAccent, radius = 3.2.dp.toPx(), center = noteCenter)
            drawLine(
                color = ConceptCyanAccent,
                start = Offset(noteCenter.x + 2.4.dp.toPx(), noteCenter.y),
                end = Offset(noteCenter.x + 2.4.dp.toPx(), h * 0.42f),
                strokeWidth = stroke * 0.95f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = ConceptCyanAccent,
                start = Offset(noteCenter.x + 2.4.dp.toPx(), h * 0.42f),
                end = Offset(w * 0.90f, h * 0.48f),
                strokeWidth = stroke * 0.95f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * ==================== 图二：推翻重构后的独立高定歌词页面 ====================
 * 严格遵循用户重构要求：
 * 1. 左上角返回按钮不变、歌名歌手不变，“返回封面提示”已删除；
 * 2. 除歌词外页面其他 UI 仅有：底部进度条、播放/暂停、收藏、翻译显示和歌词设置按钮；
 * 3. 歌词默认左对齐（支持居中/居右切换）、字号加大（默认 22sp，当前行 25sp），加入阶梯景深高斯模糊处理；
 * 4. 翻译显示、收藏和设置按钮放置在屏幕右侧偏下黄金拇指区（避免遮挡左对齐大字主歌词视区），且播放中文歌时自动隐藏翻译按钮；
 * 5. 底部进度条与封面播放页完全不同：采用「左置玻璃播放/暂停圆钮 + 右侧极光细丝流光进度条 (Aurora Silk Ribbon)」一体式极简排版。
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
    lyricAlignment: LyricAlignment,
    lyricWordByWord: Boolean,
    lyricBlurEnabled: Boolean,
    lyricBlurIntensity: Float,
    lyricLineSpacing: Float,
    lyricHighlightTheme: LyricHighlightTheme,
    onLineClick: (Int) -> Unit,
    onReturnToCover: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleTranslation: () -> Unit,
    onOpenLyricSettings: () -> Unit
) {
    val activePrimaryColor = Color(lyricHighlightTheme.primaryHex)
    val activeSecondaryColor = Color(lyricHighlightTheme.secondaryHex)

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 上部主歌词画布 + 右侧悬浮三键栏（收藏、翻译、歌词设置）
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

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, end = 52.dp), // 为右侧操作栏留出干净安全边距，文字绝不与右侧按钮重叠
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
                                else -> (1.0f - distance * 0.025f).coerceAtLeast(0.88f)
                            },
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                            label = "lyricLineScale"
                        )

                        // 动态阶梯景深高斯模糊（以当前唱诵行为清晰焦点，向外逐级递进高斯模糊；手动滑动时自动解除模糊以便快速查阅）
                        val targetBlurVal = if (lyricBlurEnabled && !isUserScrubbing && !isCurrent) {
                            when (distance) {
                                1 -> 1.1f * lyricBlurIntensity
                                2 -> 2.4f * lyricBlurIntensity
                                3 -> 3.8f * lyricBlurIntensity
                                else -> 5.4f * lyricBlurIntensity
                            }.coerceAtMost(8f)
                        } else 0f

                        val animatedBlurVal by animateFloatAsState(
                            targetValue = targetBlurVal,
                            animationSpec = tween(durationMillis = 280),
                            label = "lyricDepthBlur"
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
                                    if (supportsHardwareBlur && animatedBlurVal > 0.15f) {
                                        Modifier.blur(animatedBlurVal.dp)
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
                                            color = if (isCurrent) activePrimaryColor.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.32f),
                                            fontSize = (lyricFontSize - 7f).coerceAtLeast(11f).sp,
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
                                                            color = activePrimaryColor.copy(alpha = 0.80f * wave),
                                                            offset = Offset.Zero,
                                                            blurRadius = 20f * wave + 6f
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

                                    // 在 Android 11 及以下设备使用软阴影模拟高斯模糊柔化效果
                                    val fallbackBlurShadow = if (!supportsHardwareBlur && animatedBlurVal > 0.2f) {
                                        Shadow(
                                            color = Color.White.copy(alpha = distanceAlpha * 0.85f),
                                            offset = Offset.Zero,
                                            blurRadius = animatedBlurVal * 3.5f
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
                                                isCurrent -> activePrimaryColor.copy(alpha = 0.86f)
                                                isScrubTarget -> Color.White.copy(alpha = 0.72f)
                                                distance == 1 -> Color.White.copy(alpha = 0.42f)
                                                else -> Color.White.copy(alpha = 0.20f)
                                            },
                                            fontSize = (lyricFontSize - 4.5f).coerceAtLeast(12.5f).sp,
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
                            color = Color(0xD9101726)
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

            // 屏幕右侧偏下黄金拇指区悬浮按钮组（收藏、翻译显示[中文歌自动隐藏]、歌词设置）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 收藏按钮
                LyricRightDockGlassButton(
                    active = isFavorite,
                    activeColor = Color(0xFFFF4D6D),
                    onClick = onToggleFavorite
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 2. 翻译显示按钮（重新设计双层字标徽章，且播放中文歌时自动隐藏）
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
                                color = if (showTranslation) activePrimaryColor else Color.White.copy(alpha = 0.72f),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (showTranslation) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(y = 8.dp)
                                        .size(width = 10.dp, height = 2.dp)
                                        .clip(CircleShape)
                                        .background(activePrimaryColor)
                                )
                            }
                        }
                    }
                }

                // 3. 重新设计的歌词设置按钮（自定义精密滑轨与排版图标）
                LyricRightDockGlassButton(
                    active = false,
                    activeColor = activePrimaryColor,
                    onClick = onOpenLyricSettings
                ) {
                    Canvas(modifier = Modifier.size(18.dp)) {
                        val w = size.width
                        val h = size.height
                        val stroke = 1.8.dp.toPx()
                        val c = Color.White.copy(alpha = 0.86f)
                        // 上下两条精密调节轨与圆环滑块
                        drawLine(c, Offset(w * 0.12f, h * 0.30f), Offset(w * 0.88f, h * 0.30f), stroke, StrokeCap.Round)
                        drawCircle(activePrimaryColor, radius = 2.6.dp.toPx(), center = Offset(w * 0.65f, h * 0.30f))

                        drawLine(c, Offset(w * 0.12f, h * 0.70f), Offset(w * 0.88f, h * 0.70f), stroke, StrokeCap.Round)
                        drawCircle(c, radius = 2.6.dp.toPx(), center = Offset(w * 0.35f, h * 0.70f))
                    }
                }
            }
        }

        // 歌词页专属底部极简控播栏：左置玻璃播放/暂停按钮 + 右侧不同于封面页的「极光细丝进度条」
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

@Composable
private fun LyricRightDockGlassButton(
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) activeColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
        modifier = Modifier
            .size(40.dp)
            .border(
                width = 0.8.dp,
                color = if (active) activeColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp)
            )
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
 * 歌词页专属底部进度条与播放/暂停一体式极简控播栏（不同于封面播放页的彗星进度条与五键布局）：
 * - 左侧：高透磨砂玻璃播放/暂停圆钮（44.dp），带流光描边与平滑形变动画；
 * - 右侧：极光细丝双轨进度条 (Aurora Silk Line Progress Bar) + 内嵌等宽时间戳，视觉极致克制纯粹。
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
        targetValue = if (isDragging) 7.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "lyricSilkRailHeight"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：磨砂玻璃播放/暂停圆钮
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.10f),
            modifier = Modifier
                .size(46.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            accentColor.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.18f)
                        )
                    ),
                    shape = CircleShape
                )
                .softClickable(
                    pressScale = 0.86f,
                    onClick = onTogglePlay
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // 右侧：极光细丝流光进度条 + 顶部微字号时间戳
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
                                tryAwaitRelease()
                                onSeekTo((dragFraction * durationMs).toLong())
                                isDragging = false
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
                    val cy = h / 2f
                    val railH = railHeightDp.toPx()
                    val r = CornerRadius(railH / 2f, railH / 2f)

                    // 细丝底轨
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.14f),
                        topLeft = Offset(0f, cy - railH / 2f),
                        size = Size(w, railH),
                        cornerRadius = r
                    )

                    val activeW = (w * effectiveFraction).coerceIn(0f, w)
                    if (activeW > 1f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.45f),
                                    accentColor,
                                    Color.White
                                ),
                                startX = 0f,
                                endX = activeW
                            ),
                            topLeft = Offset(0f, cy - railH / 2f),
                            size = Size(activeW, railH),
                            cornerRadius = r
                        )
                    }

                    // 晶钻胶囊游标（与封面页彗星拖尾截然不同的高雅几何细丝游标）
                    val cursorW = if (isDragging) 14.dp.toPx() else 9.dp.toPx()
                    val cursorH = if (isDragging) 9.dp.toPx() else 5.dp.toPx()
                    val cursorLeft = (activeW - cursorW / 2f).coerceIn(0f, w - cursorW)
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
 * 完善版歌词设置抽屉 (LyricSettingsDialog)
 * 涵盖：字号无级调节、行距调节、左/中/右对齐、景深高斯模糊开关与强度、5款高亮配色、歌词时间轴微调校准、KRC逐字动效、译文/音译开关、背景暗度、屏幕常亮、歌词海报与桌面歌词开关
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricSettingsDialog(
    fontSize: Float,
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
    onSetFontSize: (Float) -> Unit,
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
    onOpenPoster: () -> Unit,
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
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "歌词显示与视觉设置",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimary,
                        fontSize = 18.sp
                    )
                )
                FilledTonalButton(
                    onClick = onOpenPoster,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.PhotoFilter, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("生成歌词海报", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 1. 歌词高亮主题色选择
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("歌词焦点配色", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = textPrimary))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LyricHighlightTheme.entries.forEach { theme ->
                        val selected = highlightTheme == theme
                        val c = Color(theme.primaryHex)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) c.copy(alpha = 0.20f) else cardBg,
                            modifier = Modifier
                                .border(
                                    width = if (selected) 1.2.dp else 0.dp,
                                    color = if (selected) c else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .softCardClickable(shape = RoundedCornerShape(12.dp)) {
                                    onSetHighlightTheme(theme)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = theme.label,
                                    color = if (selected) c else textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 2. 字号与行距调节
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("歌词字号大小", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("${fontSize.toInt()} sp", color = ConceptPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = fontSize,
                        onValueChange = onSetFontSize,
                        valueRange = 16f..30f,
                        steps = 6,
                        colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary),
                        modifier = Modifier.height(30.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("行间呼吸间距", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("${lineSpacing.toInt()} dp", color = ConceptPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = lineSpacing,
                        onValueChange = onSetLineSpacing,
                        valueRange = 8f..24f,
                        steps = 7,
                        colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary),
                        modifier = Modifier.height(30.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("歌词对齐排版", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LyricAlignment.entries.forEach { item ->
                                FilterChip(
                                    selected = alignment == item,
                                    onClick = { onSetAlignment(item) },
                                    label = { Text(item.label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. 景深高斯模糊与背景暗度
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("阶梯景深高斯模糊", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("非当前行随距离自然虚化，聚焦正在唱诵的歌词", style = MaterialTheme.typography.bodySmall, color = textSecondary)
                        }
                        Switch(
                            checked = blurEnabled,
                            onCheckedChange = onSetBlurEnabled,
                            colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                        )
                    }

                    if (blurEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("景深模糊浓度", style = MaterialTheme.typography.bodySmall)
                            Text(String.format(Locale.getDefault(), "%.1fx", blurIntensity), color = ConceptPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Slider(
                            value = blurIntensity,
                            onValueChange = onSetBlurIntensity,
                            valueRange = 0.4f..1.8f,
                            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary),
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("背景柔光压暗度", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("${(backgroundDim * 100).toInt()}%", color = ConceptPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = backgroundDim,
                        onValueChange = onSetBackgroundDim,
                        valueRange = 0.40f..0.92f,
                        colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // 4. 歌词时间轴微调校准 (Lyric Offset Calibration)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("歌词时间轴校准", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            text = String.format(Locale.getDefault(), "%+.1f 秒", offsetMs / 1000f),
                            color = if (offsetMs != 0L) VipGold else ConceptPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
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
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (delta == 0L && offsetMs == 0L) ConceptPrimary.copy(alpha = 0.18f) else sheetBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable {
                                        if (delta == 0L) onSetOffsetMs(0L) else onSetOffsetMs(offsetMs + delta)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            // 5. 逐字动效、音译注音、屏幕常亮与桌面歌词开关
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LyricSettingSwitchRow(
                        title = "KRC 逐字流光染色",
                        subtitle = "歌词随人声毫秒级逐字扫光，长音叠加微光",
                        checked = wordByWord,
                        onCheckedChange = onSetWordByWord
                    )
                    LyricSettingSwitchRow(
                        title = "外语音译注音 (罗马音/粤拼)",
                        subtitle = "在日韩粤语歌词上方显示发音注音",
                        checked = showPhonetic,
                        onCheckedChange = { if (showPhonetic != it) onTogglePhonetic() }
                    )
                    LyricSettingSwitchRow(
                        title = "外语中文翻译",
                        subtitle = "在外语歌曲下方显示双语对照译文",
                        checked = showTranslation,
                        onCheckedChange = { if (showTranslation != it) onToggleTranslation() }
                    )
                    LyricSettingSwitchRow(
                        title = "歌词页屏幕常亮",
                        subtitle = "处于歌词界面且音乐播放时防止手机息屏",
                        checked = keepScreenOn,
                        onCheckedChange = onSetKeepScreenOn
                    )
                    HorizontalDivider(color = textPrimary.copy(alpha = 0.08f))
                    LyricSettingSwitchRow(
                        title = "桌面歌词",
                        subtitle = "启用可自由定制配色、单双行与透明度的悬浮桌面歌词",
                        checked = desktopLyricEnabled,
                        onCheckedChange = { SystemLyricBridge.setFloatingCapsuleEnabled(it) }
                    )
                    LyricSettingSwitchRow(
                        title = "系统状态栏歌词广播",
                        subtitle = "适配 Flyme / HyperOS / OriginOS / LyricGetter",
                        checked = statusBarLyricEnabled,
                        onCheckedChange = { SystemLyricBridge.setStatusBarBroadcastEnabled(context, it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun LyricSettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
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
                                            val targetIdx = (currentIndex + 1).coerceIn(0, queue.lastIndex)
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
private fun PlayerMoreControlSheet(
    song: Song,
    coverStyle: CoverStyle,
    currentQualityLabel: String,
    climaxModeEnabled: Boolean,
    accompanyModeEnabled: Boolean,
    playbackSpeed: Float,
    sleepTimerRemainingSec: Long,
    sleepStopAfterCurrentSong: Boolean,
    sleepPendingStopAtTrackEnd: Boolean,
    onSelectCoverStyle: (CoverStyle) -> Unit,
    onToggleClimaxMode: () -> Unit,
    onToggleAccompanyMode: () -> Unit,
    onOpenAudioEffect: () -> Unit,
    onOpenLyricPoster: () -> Unit,
    onOpenSpeedPitch: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQualitySelect: () -> Unit,
    onOpenSongDetail: () -> Unit,
    onDismiss: () -> Unit
) {
    val systemLyricState by SystemLyricBridge.state.collectAsState()
    val desktopLyricEnabled = systemLyricState.floatingCapsuleEnabled
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBg = conceptSheetContainerColor()
    val tileBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

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
                .conceptSheetSpringEntrance()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 顶部当前曲目名片（右上角多余的“收藏歌单”按钮已移除）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ConceptPrimary.copy(alpha = 0.12f)),
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
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = ConceptPrimary)
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

            // 2. 精简双封面样式视觉切换卡（黑胶封面 vs 静态圆角方形封面）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "封面样式选择",
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
                            color = if (selected) ConceptPrimary.copy(alpha = 0.16f) else tileBg,
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = if (selected) 1.2.dp else 0.dp,
                                    color = if (selected) ConceptPrimary else Color.Transparent,
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
                                    tint = if (selected) ConceptPrimary else textSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = style.label,
                                        color = if (selected) ConceptPrimary else textPrimary,
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
            }

            // 3. Bento 双行多功能矩阵卡片（副歌速刷、KTV伴奏、歌词海报、桌面歌词 / 蝰蛇音效、倍速、睡眠定时、音质规格）
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
                        onClick = onToggleAccompanyMode,
                        modifier = Modifier.weight(1f)
                    )
                    BentoControlTile(
                        icon = Icons.Rounded.PhotoFilter,
                        title = "歌词海报",
                        subtitle = "5款高定模板",
                        active = false,
                        onClick = onOpenLyricPoster,
                        modifier = Modifier.weight(1f)
                    )
                    BentoControlTile(
                        icon = Icons.Rounded.Lyrics,
                        title = "桌面歌词",
                        subtitle = if (desktopLyricEnabled) "已开启桌面词" else "多风格悬浮词",
                        active = desktopLyricEnabled,
                        onClick = { SystemLyricBridge.setFloatingCapsuleEnabled(!desktopLyricEnabled) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BentoControlTile(
                        icon = Icons.Rounded.GraphicEq,
                        title = "蝰蛇音效",
                        subtitle = "音效库/均衡器",
                        active = AudioEffectManager.state.value.isEnabled,
                        onClick = onOpenAudioEffect,
                        modifier = Modifier.weight(1f)
                    )
                    BentoControlTile(
                        icon = Icons.Rounded.Speed,
                        title = "倍速与变调",
                        subtitle = "${String.format(Locale.getDefault(), "%.2f", playbackSpeed)}x 播放",
                        active = playbackSpeed != 1.0f,
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
                        onClick = onOpenSleepTimer,
                        modifier = Modifier.weight(1f)
                    )
                    BentoControlTile(
                        icon = Icons.Rounded.HighQuality,
                        title = "音质规格",
                        subtitle = currentQualityLabel,
                        active = true,
                        onClick = onOpenQualitySelect,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. 底部音频技术参数按钮
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tileBg,
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
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = ConceptPrimary, modifier = Modifier.size(18.dp))
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = if (active) activeColor.copy(alpha = 0.85f) else textSecondary,
                fontSize = 9.5.sp,
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

private suspend fun extractCoverMeshColors(
    context: Context,
    coverUrl: String
): Pair<Color, Color>? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(coverUrl)
            .size(32, 32)
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
            }
        }

        fun normalizeForDarkMesh(rAvg: Int, gAvg: Int, bAvg: Int, hueShift: Float): Color {
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(rAvg, gAvg, bAvg, hsv)
            hsv[0] = (hsv[0] + hueShift) % 360f
            hsv[1] = hsv[1].coerceIn(0.38f, 0.72f)
            hsv[2] = hsv[2].coerceIn(0.15f, 0.28f)
            return Color(android.graphics.Color.HSVToColor(hsv))
        }

        val c1 = if (count1 > 0) {
            normalizeForDarkMesh((r1 / count1).toInt(), (g1 / count1).toInt(), (b1 / count1).toInt(), 0f)
        } else Color(0xFF1A2C38)

        val c2 = if (count2 > 0) {
            normalizeForDarkMesh((r2 / count2).toInt(), (g2 / count2).toInt(), (b2 / count2).toInt(), 28f)
        } else Color(0xFF1B1932)

        c1 to c2
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
