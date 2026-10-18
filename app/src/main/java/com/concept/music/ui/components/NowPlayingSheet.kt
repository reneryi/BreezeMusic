package com.concept.music.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.concept.music.service.SystemLyricBridge
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.SystemBarAppearanceEffect
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.isConceptDarkTheme
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.PlayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

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
    lyricFontSize: Float = 18f,
    lyricAlignment: LyricAlignment = LyricAlignment.CENTER,
    lyricWordByWord: Boolean = true,
    commentCount: Int = 0,
    songDetail: SongDetailInfo? = null,
    playbackSpeed: Float = 1.0f,
    playbackPitch: Float = 1.0f,
    linkPitchToSpeed: Boolean = false,
    sleepTimerRemainingSec: Long = 0L,
    sleepStopAfterCurrentSong: Boolean = false,
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
    onOpenComments: () -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onSetPlaybackPitch: (Float) -> Unit = {},
    onSetLinkPitchToSpeed: (Boolean) -> Unit = {},
    onResetSpeedAndPitch: () -> Unit = {},
    onSetSleepTimer: (Int, Boolean) -> Unit = { _, _ -> },
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
    var showLyricView by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioEffectDialog by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showLyricSettingsDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedPitchDialog by remember { mutableStateOf(false) }
    var showLyricPosterDialog by remember { mutableStateOf(false) }
    var showFocusPodSheet by remember { mutableStateOf(false) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    var showMoreControlSheet by remember { mutableStateOf(false) }

    // 拦截系统返回键：若正处于全屏歌词则先返回封面，否则收起播放页
    BackHandler {
        if (showLyricView) {
            showLyricView = false
        } else {
            onDismiss()
        }
    }

    // 播放页采用沉浸式流体暗黑背景，同步将系统状态栏与导航栏图标设为浅色，彻底消除顶部状态栏割裂白条
    SystemBarAppearanceEffect(forceDarkBars = true)

    // 支持顶部区域下滑手势收起播放页
    var verticalDragOffset by remember { mutableFloatStateOf(0f) }
    val animatedVerticalOffset by animateFloatAsState(
        targetValue = verticalDragOffset.coerceAtLeast(0f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playerVerticalOffset"
    )

    // [MOT-02] 状态提升：将唱片/封面旋转角度状态提升至 NowPlayingSheet 顶层，切换歌词/封面不重置相位
    val rotationAnim = remember(song.id) { Animatable(0f) }
    LaunchedEffect(isPlaying, song.id, coverStyle) {
        if (isPlaying) {
            val durationPerRev = if (coverStyle == CoverStyle.VINYL) 20000 else 24000
            while (isActive) {
                val target = rotationAnim.value + 360f
                rotationAnim.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = durationPerRev, easing = LinearEasing)
                )
            }
        }
    }

    // [UI-01] 封面动态流光取色 (提取主色与次生氛围色并做暗色护眼映射)
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

    // 全屏 Edge-to-Edge 沉浸式播放页容器（彻底消除 ModalBottomSheet 预留系统状态栏导致的顶部独立白条）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, animatedVerticalOffset.roundToInt()) }
            .background(Color(0xFF0A0E17))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        // 1. 沉浸式氛围背景（统一采用 24x24 超轻量降采样柔光背景，彻底杜绝全屏 RenderEffect 高斯模糊引发的 Skia AGTM 解析风暴与 GPU 抢占）
        if (song.coverUrl.isNotEmpty()) {
            val bgImageModel = remember(song.coverUrl) {
                ImageRequest.Builder(context)
                    .data(song.coverUrl)
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
        // 渐变暗黑柔光遮罩 + [UI-01] 动态流光双核径向网格渐变
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xD80A0E17),
                            Color(0xE8101726),
                            Color(0xF60A0E17)
                        )
                    )
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            meshColorPrimary.copy(alpha = 0.52f),
                            Color.Transparent
                        ),
                        center = Offset(220f, 380f),
                        radius = 950f
                    )
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            meshColorSecondary.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = Offset(820f, 1380f),
                        radius = 1050f
                    )
                )
        )

        // 2. 主体内容布局 (Edge-to-Edge 状态栏与导航栏内边距)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部控制栏 (支持下滑收起、歌曲名、歌手、真实音质胶囊)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
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
                TopHeaderBar(
                    title = song.title,
                    artist = song.artist,
                    qualityLabel = currentQualityLabel,
                    isHiRes = song.isHiRes,
                    onDismiss = onDismiss,
                    onQualityClick = { showQualityDialog = true }
                )
            }

            // 顶部状态胶囊栏（[INNOV-01] 副歌刷刷模式 + 听整首解锁按钮 + 睡眠定时 + 倍速变调）
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // [INNOV-01] 副歌刷刷（高潮速听）胶囊开关
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (climaxModeEnabled) VipGold.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(
                        0.6.dp,
                        if (climaxModeEnabled) VipGold.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier.softCardClickable(onClick = onToggleClimaxMode)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Whatshot,
                            contentDescription = "副歌刷刷",
                            tint = if (climaxModeEnabled) VipGold else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val climaxLabel = when {
                            !climaxModeEnabled -> "副歌刷刷"
                            climaxUnlockedForCurrentSong -> "副歌刷刷 · 已解锁整首"
                            else -> "副歌速听 ${formatDuration(song.effectiveClimaxStartMs)}-${formatDuration(song.effectiveClimaxEndMs)}"
                        }
                        Text(
                            text = climaxLabel,
                            color = if (climaxModeEnabled) VipGold else Color.White.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 当处于副歌刷刷且尚未解锁本曲整首时，展示一键「听整首」高亮胶囊
                if (climaxModeEnabled && !climaxUnlockedForCurrentSong) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ConceptPrimary.copy(alpha = 0.22f),
                        border = BorderStroke(0.6.dp, ConceptPrimary.copy(alpha = 0.65f)),
                        modifier = Modifier.softCardClickable {
                            onUnlockFullTrack()
                            Toast.makeText(context, "已解锁本曲完整播放", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LockOpen,
                                contentDescription = "听整首",
                                tint = ConceptPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "听整首",
                                color = ConceptPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // [P1-3] 原唱 / 伴奏 (KTV 模式) 胶囊切换
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (accompanyModeEnabled) ConceptPrimary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(
                        0.6.dp,
                        if (accompanyModeEnabled) ConceptPrimary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier.softCardClickable {
                        onToggleAccompanyMode()
                        Toast.makeText(
                            context,
                            if (!accompanyModeEnabled) "已切换至伴奏模式 (KTV)" else "已恢复原唱模式",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "伴奏切换",
                            tint = if (accompanyModeEnabled) ConceptPrimary else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (accompanyModeEnabled) "伴奏中" else "伴奏",
                            color = if (accompanyModeEnabled) ConceptPrimary else Color.White.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // [创新点1] 概念胶囊工作台 (Focus Pod) 快捷入口
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier.softCardClickable { showFocusPodSheet = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SelfImprovement,
                            contentDescription = "概念胶囊",
                            tint = ConceptPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "心流舱",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (sleepTimerRemainingSec > 0L || sleepStopAfterCurrentSong) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ConceptPrimary.copy(alpha = 0.16f),
                        border = BorderStroke(0.5.dp, ConceptPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.softCardClickable { showSleepTimerDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = null,
                                tint = ConceptPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val timerText = if (sleepTimerRemainingSec > 0L) {
                                formatDuration(sleepTimerRemainingSec * 1000L)
                            } else {
                                "播完当前停"
                            }
                            Text(
                                text = timerText,
                                color = ConceptPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                if (playbackSpeed != 1.0f || playbackPitch != 1.0f) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VipGold.copy(alpha = 0.16f),
                        border = BorderStroke(0.5.dp, VipGold.copy(alpha = 0.4f)),
                        modifier = Modifier.softCardClickable { showSpeedPitchDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = VipGold,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${String.format(java.util.Locale.getDefault(), "%.2f", playbackSpeed)}x",
                                color = VipGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 主舞台区（丝滑 AnimatedContent 3D 缩放转场：四大多态机芯封面 VS 全屏沉浸歌词）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = showLyricView,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(initialScale = 0.94f, animationSpec = tween(260))) togetherWith
                            (fadeOut(tween(180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(180)))
                    },
                    label = "stageSwitch"
                ) { isLyricStage ->
                    if (!isLyricStage) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            var dragDeltaX by remember { mutableFloatStateOf(0f) }
                            LaunchedEffect(showDoubleTapHeart) {
                                if (showDoubleTapHeart) {
                                    delay(750L)
                                    showDoubleTapHeart = false
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .pointerInput(song.id, isFavorite) {
                                        detectTapGestures(
                                            onTap = { showLyricView = true },
                                            onDoubleTap = {
                                                showDoubleTapHeart = true
                                                if (!isFavorite) {
                                                    onToggleFavorite()
                                                }
                                            }
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
                                                if (dragDeltaX < -110f) {
                                                    onNext()
                                                } else if (dragDeltaX > 110f) {
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
                                        VinylRecordView(
                                            coverUrl = song.coverUrl,
                                            isPlaying = isPlaying,
                                            rotationProvider = { rotationAnim.value % 360f }
                                        )
                                    }
                                    CoverStyle.CASSETTE -> {
                                        TransparentCassetteDeckView(
                                            song = song,
                                            isPlaying = isPlaying,
                                            rotationProvider = { rotationAnim.value % 360f },
                                            currentPositionMs = currentPositionMs,
                                            durationMs = durationMs
                                        )
                                    }
                                    CoverStyle.POSTER -> {
                                        MagazinePosterCoverView(
                                            song = song,
                                            lyricData = lyricData,
                                            currentLyricIndex = currentLyricIndex,
                                            currentPositionMs = currentPositionMs,
                                            showTranslation = showTranslation
                                        )
                                    }
                                    CoverStyle.CIRCLE -> {
                                        CircleCoverView(
                                            coverUrl = song.coverUrl,
                                            rotationProvider = { rotationAnim.value % 360f }
                                        )
                                    }
                                }
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showDoubleTapHeart,
                                    enter = fadeIn(tween(120)),
                                    exit = fadeOut(tween(320))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252).copy(alpha = 0.92f),
                                        modifier = Modifier.size(92.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (coverStyle != CoverStyle.POSTER) {
                                LyricPreviewBar(
                                    lyricData = lyricData,
                                    currentLyricIndex = currentLyricIndex,
                                    showTranslation = showTranslation,
                                    onClick = { showLyricView = true }
                                )
                            } else {
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.softCardClickable { showLyricView = true }
                                ) {
                                    Text(
                                        text = "杂志海报机芯 · 轻触展开全屏歌词",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        ImmersiveLyricView(
                            lyricData = lyricData,
                            currentLyricIndex = currentLyricIndex,
                            currentPositionMs = currentPositionMs,
                            showTranslation = showTranslation,
                            showPhonetic = showPhonetic,
                            lyricFontSize = lyricFontSize,
                            lyricAlignment = lyricAlignment,
                            lyricWordByWord = lyricWordByWord,
                            onLineClick = onSeekToLyricLine,
                            onToggleTranslation = onToggleTranslation,
                            onTogglePhonetic = onTogglePhonetic,
                            onOpenPoster = { showLyricPosterDialog = true },
                            onOpenSettings = { showLyricSettingsDialog = true },
                            onCloseLyric = { showLyricView = false }
                        )
                    }
                }
            }

            // 现代对称式五键快捷功能船坞
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 喜欢收藏
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "我喜欢",
                        tint = if (isFavorite) Color(0xFFFF5252) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 2. 音效与白噪音混音舱
                IconButton(onClick = { showAudioEffectDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "蝰蛇音效与白噪音",
                        tint = ConceptPrimary,
                        modifier = Modifier.size(23.dp)
                    )
                }

                // 3. 加入其他歌单
                IconButton(onClick = onAddToPlaylist) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = "加入歌单",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 4. 歌词金句黑胶海报
                IconButton(onClick = { showLyricPosterDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoFilter,
                        contentDescription = "歌词黑胶海报",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 5. 更多功能 Bento 控制台
                IconButton(onClick = { showMoreControlSheet = true }) {
                    Icon(
                        imageVector = Icons.Rounded.GridView,
                        contentDescription = "更多功能控制台",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 底部播放控制台 (进度条Slider + 高潮区间金标、中层三键控制、最底栏：循环模式、XX评论⌃、队列)
            PlayerControlsSection(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                climaxStartMs = song.effectiveClimaxStartMs,
                climaxEndMs = song.effectiveClimaxEndMs,
                highlightClimaxRange = climaxModeEnabled && !climaxUnlockedForCurrentSong,
                isPlaying = isPlaying,
                playMode = playMode,
                commentCount = commentCount,
                onSeekTo = onSeekTo,
                onTogglePlay = onTogglePlay,
                onPrevious = onPrevious,
                onNext = onNext,
                onCyclePlayMode = onCyclePlayMode,
                onOpenComments = onOpenComments,
                onQueueClick = { showQueueSheet = true }
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    // 播放队列弹窗 ([维度2 + 创新点2] 自动居中定位 + AI 情绪漫游重排 + 封面与音质高定美化 + 主题自适应)
    if (showQueueSheet) {
        QueueBottomSheet(
            queue = queue,
            currentIndex = currentIndex,
            playMode = playMode,
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

    // 全新设计的 Bento 网格化「更多功能控制台」抽屉（主题自适应）
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
            onSelectCoverStyle = onSelectCoverStyle,
            onToggleClimaxMode = onToggleClimaxMode,
            onToggleAccompanyMode = onToggleAccompanyMode,
            onOpenFocusPod = {
                showMoreControlSheet = false
                showFocusPodSheet = true
            },
            onOpenAudioEffect = {
                showMoreControlSheet = false
                showAudioEffectDialog = true
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
            onAddToPlaylist = {
                showMoreControlSheet = false
                onAddToPlaylist()
            },
            onDismiss = { showMoreControlSheet = false }
        )
    }

    // [创新点5] 歌词金句黑胶海报生成器弹窗
    if (showLyricPosterDialog) {
        LyricPosterDialog(
            song = song,
            lyricData = lyricData,
            currentLyricIndex = currentLyricIndex,
            onDismiss = { showLyricPosterDialog = false }
        )
    }

    // [创新点1] 概念胶囊工作台（番茄钟 × 概念频道 × 白噪音）
    if (showFocusPodSheet) {
        FocusPodBottomSheet(
            onDismiss = { showFocusPodSheet = false }
        )
    }

    // 音质选择弹窗 (动态过滤，仅显示该歌曲真实拥有的音质档位)
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

    // Viper 音效设置弹窗
    if (showAudioEffectDialog) {
        AudioEffectDialog(onDismiss = { showAudioEffectDialog = false })
    }

    // 歌词个性化设置弹窗 (字号、对齐、逐字卡拉OK)
    if (showLyricSettingsDialog) {
        LyricSettingsDialog(
            fontSize = lyricFontSize,
            alignment = lyricAlignment,
            wordByWord = lyricWordByWord,
            onSetFontSize = onSetLyricFontSize,
            onSetAlignment = onSetLyricAlignment,
            onSetWordByWord = onSetLyricWordByWord,
            onDismiss = { showLyricSettingsDialog = false }
        )
    }

    // 睡眠定时器弹窗 ([FUNC-03])
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingSec = sleepTimerRemainingSec,
            stopAfterCurrentSong = sleepStopAfterCurrentSong,
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

    // 倍速与音调弹窗 ([FUNC-04])
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopHeaderBar(
    title: String,
    artist: String,
    qualityLabel: String,
    isHiRes: Boolean,
    onDismiss: () -> Unit,
    onQualityClick: () -> Unit
) {
    val context = LocalContext.current
    val outputRouteLabel = remember { detectAudioOutputRouteLabel(context) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 下拉收起
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "收起",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(32.dp)
            )
        }

        // 歌曲信息（[维度5] 长标题平滑跑马灯 basicMarquee + [维度1] 音频输出设备路由指示）
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.68f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = outputRouteLabel,
                        color = ConceptPrimary.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // 真实音质胶囊 (严格与当前实际解析/可用音质同步，杜绝假 Hi-Res 显示)
        val isGoldQuality = qualityLabel.startsWith("Hi-Res", ignoreCase = true)
        Surface(
            color = if (isGoldQuality) VipGold.copy(alpha = 0.22f) else ConceptPrimary.copy(alpha = 0.18f),
            border = BorderStroke(
                0.6.dp,
                if (isGoldQuality) VipGold.copy(alpha = 0.5f) else ConceptPrimary.copy(alpha = 0.45f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.softCardClickable(onClick = onQualityClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = qualityLabel,
                    color = if (isGoldQuality) VipGold else ConceptPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = if (isGoldQuality) VipGold else ConceptPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * 黑胶唱片旋转视图 ([MOT-02] 连续旋转相位 + 仿真黑胶唱臂搭落与抬起动效)
 */
@Composable
private fun VinylRecordView(
    coverUrl: String,
    isPlaying: Boolean,
    rotationProvider: () -> Float
) {
    val armAngle by animateFloatAsState(
        targetValue = if (isPlaying) 24f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tonearmAngle"
    )

    Box(
        modifier = Modifier.size(width = 300.dp, height = 296.dp),
        contentAlignment = Alignment.Center
    ) {
        // 黑胶盘体
        Box(
            modifier = Modifier
                .size(276.dp)
                .shadow(24.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2E2E33),
                            Color(0xFF1C1C20),
                            Color(0xFF0C0C0E)
                        )
                    )
                )
                .border(5.dp, Color(0xFF1B1B1E), CircleShape)
                .graphicsLayer { rotationZ = rotationProvider() },
            contentAlignment = Alignment.Center
        ) {
            // 同心微纹
            Box(
                modifier = Modifier
                    .size(236.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(198.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
            )

            // 唱片中心圆形封面
            Box(
                modifier = Modifier
                    .size(172.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
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
                        modifier = Modifier.size(56.dp)
                    )
                }

                // 轴心孔
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF14181B))
                        .border(2.5.dp, Color(0xFF444444), CircleShape)
                )
            }
        }

        // 右上角仿真金属唱臂 ([MOT-02])
        Canvas(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
        ) {
            val pivot = Offset(size.width * 0.72f, size.height * 0.18f)
            rotate(degrees = armAngle, pivot = pivot) {
                // 唱臂主杆
                val elbow = Offset(pivot.x - 14.dp.toPx(), pivot.y + 62.dp.toPx())
                val head = Offset(elbow.x - 24.dp.toPx(), elbow.y + 26.dp.toPx())
                drawLine(
                    color = Color(0xFFD6DCE5),
                    start = pivot,
                    end = elbow,
                    strokeWidth = 4.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFB8C2CC),
                    start = elbow,
                    end = head,
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // 唱头外壳
                drawCircle(
                    color = ConceptPrimary,
                    radius = 5.5.dp.toPx(),
                    center = head
                )
            }
            // 唱臂轴承底座
            drawCircle(
                color = Color(0xFF2B303A),
                radius = 15.dp.toPx(),
                center = pivot
            )
            drawCircle(
                color = Color(0xFF9EA8B6),
                radius = 7.dp.toPx(),
                center = pivot
            )
        }
    }
}

/**
 * 极简圆形封面旋转视图 (官方概念版风格，[MOT-02] 连续旋转相位)
 */
@Composable
private fun CircleCoverView(
    coverUrl: String,
    rotationProvider: () -> Float
) {
    Box(
        modifier = Modifier
            .size(260.dp)
            .shadow(24.dp, CircleShape)
            .clip(CircleShape)
            .border(3.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .graphicsLayer { rotationZ = rotationProvider() },
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
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

/**
 * [INNOV-02] 概念多态机芯 B：透明机械磁带机芯 (Transparent Cassette Deck)
 */
@Composable
private fun TransparentCassetteDeckView(
    song: Song,
    isPlaying: Boolean,
    rotationProvider: () -> Float,
    currentPositionMs: Long,
    durationMs: Long
) {
    val progress = if (durationMs > 0L) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0.35f
    }

    Box(
        modifier = Modifier
            .size(width = 308.dp, height = 216.dp)
            .shadow(24.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xEE1C212B),
                        Color(0xE612151C),
                        Color(0xF0181C26)
                    )
                )
            )
            .border(1.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (song.coverUrl.isNotBlank()) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.22f }
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val screwR = 4.5.dp.toPx()
            val screwOffsets = listOf(
                Offset(16.dp.toPx(), 16.dp.toPx()),
                Offset(w - 16.dp.toPx(), 16.dp.toPx()),
                Offset(20.dp.toPx(), h - 16.dp.toPx()),
                Offset(w - 20.dp.toPx(), h - 16.dp.toPx())
            )
            screwOffsets.forEach { pt ->
                drawCircle(color = Color(0xFF4E5668), radius = screwR, center = pt)
                drawLine(
                    color = Color(0xFF1F242D),
                    start = Offset(pt.x - screwR * 0.6f, pt.y - screwR * 0.6f),
                    end = Offset(pt.x + screwR * 0.6f, pt.y + screwR * 0.6f),
                    strokeWidth = 1.3.dp.toPx()
                )
            }

            val labelLeft = w * 0.08f
            val labelTop = h * 0.11f
            val labelWidth = w * 0.84f
            val labelHeight = h * 0.66f
            drawRoundRect(
                color = Color(0x99161A24),
                topLeft = Offset(labelLeft, labelTop),
                size = Size(labelWidth, labelHeight),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
            )
            drawRoundRect(
                color = ConceptPrimary.copy(alpha = 0.55f),
                topLeft = Offset(labelLeft, labelTop + 26.dp.toPx()),
                size = Size(labelWidth, 3.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
            drawRoundRect(
                color = VipGold.copy(alpha = 0.45f),
                topLeft = Offset(labelLeft, labelTop + 31.dp.toPx()),
                size = Size(labelWidth, 1.5.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )

            val winLeft = w * 0.15f
            val winTop = h * 0.34f
            val winWidth = w * 0.70f
            val winHeight = h * 0.38f
            drawRoundRect(
                color = Color(0xD90B0E13),
                topLeft = Offset(winLeft, winTop),
                size = Size(winWidth, winHeight),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.14f),
                topLeft = Offset(winLeft, winTop),
                size = Size(winWidth, winHeight),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            val leftCenter = Offset(w * 0.32f, h * 0.53f)
            val rightCenter = Offset(w * 0.68f, h * 0.53f)
            val maxSpoolR = 44.dp.toPx()
            val minSpoolR = 23.dp.toPx()
            val leftTapeR = maxSpoolR - (maxSpoolR - minSpoolR) * progress
            val rightTapeR = minSpoolR + (maxSpoolR - minSpoolR) * progress

            val headTrapPath = Path().apply {
                moveTo(w * 0.22f, h)
                lineTo(w * 0.27f, h * 0.81f)
                lineTo(w * 0.73f, h * 0.81f)
                lineTo(w * 0.78f, h)
                close()
            }
            drawPath(path = headTrapPath, color = Color(0xEB0E1117))
            drawPath(
                path = headTrapPath,
                color = Color.White.copy(alpha = 0.12f),
                style = Stroke(width = 1.dp.toPx())
            )

            val leftRoller = Offset(w * 0.24f, h * 0.87f)
            val rightRoller = Offset(w * 0.76f, h * 0.87f)
            val tapeLineColor = Color(0xFF5D4234)
            drawLine(
                color = tapeLineColor,
                start = Offset(leftCenter.x - leftTapeR * 0.65f, leftCenter.y + leftTapeR * 0.65f),
                end = leftRoller,
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = tapeLineColor,
                start = leftRoller,
                end = rightRoller,
                strokeWidth = 1.8.dp.toPx()
            )
            drawLine(
                color = tapeLineColor,
                start = rightRoller,
                end = Offset(rightCenter.x + rightTapeR * 0.65f, rightCenter.y + rightTapeR * 0.65f),
                strokeWidth = 1.5.dp.toPx()
            )

            drawRoundRect(
                color = if (isPlaying) ConceptPrimary.copy(alpha = 0.85f) else Color(0xFF7D8594),
                topLeft = Offset(w * 0.46f, h * 0.84f),
                size = Size(w * 0.08f, 8.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            fun drawSpool(center: Offset, tapeRadius: Float, angle: Float) {
                drawCircle(color = Color(0xFF281B15), radius = tapeRadius, center = center)
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    radius = tapeRadius * 0.82f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
                val coreR = 19.dp.toPx()
                drawCircle(color = Color(0xFFDDE3EC), radius = coreR, center = center)
                drawCircle(color = Color(0xFF141820), radius = coreR * 0.76f, center = center)

                rotate(degrees = angle, pivot = center) {
                    for (i in 0 until 6) {
                        rotate(degrees = i * 60f, pivot = center) {
                            drawRoundRect(
                                color = Color(0xFFDDE3EC),
                                topLeft = Offset(center.x - 2.2.dp.toPx(), center.y - coreR * 0.78f),
                                size = Size(4.4.dp.toPx(), 5.5.dp.toPx()),
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                            )
                        }
                    }
                }
                drawCircle(color = Color(0xFF090B0E), radius = 6.dp.toPx(), center = center)
            }

            val deg = rotationProvider()
            drawSpool(leftCenter, leftTapeR, deg)
            drawSpool(rightCenter, rightTapeR, deg)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.80f)
                .padding(top = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = ConceptPrimary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "SIDE A",
                        color = Color(0xFF0A1014),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp)
                )
            }
            Text(
                text = "CrO₂ • ${ (progress * 100).toInt() }%",
                color = VipGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * [INNOV-02] 概念多态机芯 C：杂志海报机芯 (Magazine Poster Mode)
 */
@Composable
private fun MagazinePosterCoverView(
    song: Song,
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    showTranslation: Boolean
) {
    val currentLine = lyricData.lines.getOrNull(currentLyricIndex)

    Box(
        modifier = Modifier
            .size(width = 292.dp, height = 324.dp)
            .shadow(26.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF171A22))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
    ) {
        if (song.coverUrl.isNotBlank()) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.48f),
                        0.32f to Color.Transparent,
                        0.58f to Color.Black.copy(alpha = 0.35f),
                        1.0f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONCEPT ARCHIVE // NO.04",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Text(
                    text = if (song.isHiRes) "HI-RES MASTER" else "STUDIO EDITION",
                    color = if (song.isHiRes) VipGold else ConceptPrimary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            if (currentLine != null) {
                val annotatedPosterLyric = buildAnnotatedString {
                    if (currentLine.hasWordTiming && currentLine.words.isNotEmpty()) {
                        currentLine.words.forEach { word ->
                            val wordProgress = when {
                                word.durationMs > 0L ->
                                    ((currentPositionMs - word.startTimeMs).toFloat() / word.durationMs.toFloat())
                                        .coerceIn(0f, 1f)
                                currentPositionMs >= word.startTimeMs -> 1f
                                else -> 0f
                            }
                            val color = when {
                                wordProgress >= 1f -> ConceptPrimary
                                wordProgress > 0f -> Color.White
                                else -> Color.White.copy(alpha = 0.55f)
                            }
                            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Black)) {
                                append(word.text)
                            }
                        }
                    } else {
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) {
                            append(currentLine.text)
                        }
                    }
                }
                Text(
                    text = annotatedPosterLyric,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (showTranslation && !currentLine.translation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = currentLine.translation,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.18f), thickness = 0.6.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${song.title} — ${song.artist}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "POSTER SKIN",
                    color = ConceptPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 封面下方实时单/双行歌词预览条
 */
@Composable
private fun LyricPreviewBar(
    lyricData: LyricData,
    currentLyricIndex: Int,
    showTranslation: Boolean,
    onClick: () -> Unit
) {
    val currentLineObj = lyricData.lines.getOrNull(currentLyricIndex)
    val mainText = currentLineObj?.text ?: "轻触展开沉浸歌词"
    val subText = if (showTranslation && !currentLineObj?.translation.isNullOrBlank()) {
        currentLineObj?.translation ?: ""
    } else {
        lyricData.lines.getOrNull(currentLyricIndex + 1)?.text ?: ""
    }

    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .softCardClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    contentDescription = null,
                    tint = ConceptPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (subText.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 全屏沉浸歌词视图 (居中双行滚动，右侧悬浮纵向药丸【译】与【歌词设置】按钮)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImmersiveLyricView(
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    showTranslation: Boolean,
    showPhonetic: Boolean = true,
    lyricFontSize: Float,
    lyricAlignment: LyricAlignment,
    lyricWordByWord: Boolean,
    onLineClick: (Int) -> Unit,
    onToggleTranslation: () -> Unit,
    onTogglePhonetic: () -> Unit = {},
    onOpenPoster: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onCloseLyric: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCloseLyric
            )
    ) {
        if (lyricData.lines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "纯音乐，暂无歌词或正在检索官方歌词...",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
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
                    delay(3500L)
                    isUserScrubbing = false
                }
            }

            LaunchedEffect(currentLyricIndex, isUserScrubbing) {
                if (!isUserScrubbing && currentLyricIndex in lyricData.lines.indices) {
                    listState.animateScrollToItem(
                        index = currentLyricIndex,
                        scrollOffset = -180
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

            val textAlign = if (lyricAlignment == LyricAlignment.CENTER) TextAlign.Center else TextAlign.Start
            val horizontalAlignment = if (lyricAlignment == LyricAlignment.CENTER) Alignment.CenterHorizontally else Alignment.Start
            val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(vertical = 150.dp),
                horizontalAlignment = horizontalAlignment
            ) {
                itemsIndexed(lyricData.lines) { index, line ->
                    val isCurrent = index == currentLyricIndex
                    val isScrubTarget = isUserScrubbing && index == scrubLineIndex
                    val distance = abs(index - currentLyricIndex)

                    val lineScale by animateFloatAsState(
                        targetValue = when {
                            isCurrent -> 1.0f
                            isScrubTarget -> 0.99f
                            else -> (1.0f - distance * 0.024f).coerceAtLeast(0.91f)
                        },
                        animationSpec = tween(durationMillis = 280),
                        label = "lineScale"
                    )
                    val depthBlurDp = if (supportsBlur && !isUserScrubbing && distance >= 3) {
                        ((distance - 2) * 0.7f).coerceAtMost(2.2f).dp
                    } else {
                        0.dp
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = lineScale
                                scaleY = lineScale
                            }
                            .then(if (depthBlurDp > 0.dp) Modifier.blur(depthBlurDp) else Modifier)
                            .softClickable {
                                isUserScrubbing = false
                                onLineClick(index)
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = if (lyricAlignment == LyricAlignment.CENTER) Alignment.Center else Alignment.CenterStart
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
                                        color = if (isCurrent) ConceptPrimary.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.36f),
                                        fontSize = (lyricFontSize - 6).coerceAtLeast(10f).sp,
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
                                    horizontalArrangement = if (lyricAlignment == LyricAlignment.CENTER) Arrangement.Center else Arrangement.Start,
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
                                        val dimColor = Color.White.copy(alpha = 0.42f)

                                        val annotatedWord = buildAnnotatedString {
                                            if (wordProgress <= 0f) {
                                                withStyle(SpanStyle(color = dimColor)) {
                                                    append(word.text)
                                                }
                                            } else if (wordProgress >= 1f) {
                                                withStyle(SpanStyle(color = ConceptPrimary)) {
                                                    append(word.text)
                                                }
                                            } else {
                                                val leftStop = (wordProgress - 0.12f).coerceAtLeast(0f)
                                                val rightStop = (wordProgress + 0.12f).coerceAtMost(1f)
                                                withStyle(
                                                    SpanStyle(
                                                        brush = Brush.horizontalGradient(
                                                            0.0f to ConceptPrimary,
                                                            leftStop to ConceptPrimary,
                                                            rightStop to dimColor,
                                                            1.0f to dimColor
                                                        )
                                                    )
                                                ) {
                                                    append(word.text)
                                                }
                                            }
                                        }

                                        val wave = if (isSinging) sin(wordProgress * Math.PI.toFloat()) else 0f
                                        val isLongNote = word.durationMs >= 800L && isSinging

                                        Text(
                                            text = annotatedWord,
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (lyricFontSize + 4).sp,
                                                shadow = if (isLongNote) {
                                                    Shadow(
                                                        color = ConceptPrimary.copy(alpha = 0.75f * wave),
                                                        offset = Offset.Zero,
                                                        blurRadius = 18f * wave + 4f
                                                    )
                                                } else null
                                            ),
                                            modifier = Modifier.graphicsLayer {
                                                translationY = -3.5f * wave
                                                val s = 1f + 0.04f * wave
                                                scaleX = s
                                                scaleY = s
                                            }
                                        )
                                    }
                                }
                            } else {
                                val distanceAlpha = when {
                                    isScrubTarget -> 0.92f
                                    distance == 1 -> 0.68f
                                    distance == 2 -> 0.48f
                                    else -> 0.30f
                                }
                                val textColor = if (isCurrent) ConceptPrimary else Color.White.copy(alpha = distanceAlpha)
                                val textWeight = if (isCurrent || isScrubTarget) FontWeight.Bold else FontWeight.Normal
                                val lineSize = if (isCurrent) (lyricFontSize + 3).sp else lyricFontSize.sp

                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = textWeight,
                                        color = textColor,
                                        fontSize = lineSize,
                                        textAlign = textAlign
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            val trans = line.translation
                            if (showTranslation && !trans.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = trans,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = when {
                                            isCurrent -> ConceptPrimary.copy(alpha = 0.85f)
                                            isScrubTarget -> Color.White.copy(alpha = 0.75f)
                                            else -> Color.White.copy(alpha = 0.38f)
                                        },
                                        fontSize = (lyricFontSize - 4).coerceAtLeast(11f).sp,
                                        textAlign = textAlign
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isUserScrubbing && scrubLineIndex in lyricData.lines.indices,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                val targetTimeMs = lyricData.lines.getOrNull(scrubLineIndex)?.startTimeMs ?: 0L
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 44.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xCC161922),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f))
                    ) {
                        Text(
                            text = formatDuration(targetTimeMs),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        thickness = 0.8.dp,
                        color = ConceptPrimary.copy(alpha = 0.45f)
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ConceptPrimary,
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
                                tint = Color(0xFF0A1014),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "跳转",
                                color = Color(0xFF0A1014),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 顶部居中轻触返回封面提示
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.softCardClickable(onClick = onCloseLyric)
            ) {
                Text(
                    text = "轻触返回封面",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // 右侧悬浮纵向控制栏
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (showPhonetic) ConceptPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, if (showPhonetic) ConceptPrimary else Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.softCardClickable { onTogglePhonetic() }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "音",
                        color = if (showPhonetic) ConceptPrimary else Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (showTranslation) ConceptPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, if (showTranslation) ConceptPrimary else Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.softCardClickable { onToggleTranslation() }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "译",
                        color = if (showTranslation) ConceptPrimary else Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onOpenPoster,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoFilter,
                    contentDescription = "生成歌词黑胶海报",
                    tint = ConceptPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "歌词设置",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/**
 * 歌词设置弹窗 (字号、对齐、逐字卡拉OK)
 */
@Composable
private fun LyricSettingsDialog(
    fontSize: Float,
    alignment: LyricAlignment,
    wordByWord: Boolean,
    onSetFontSize: (Float) -> Unit,
    onSetAlignment: (LyricAlignment) -> Unit,
    onSetWordByWord: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val systemLyricState by SystemLyricBridge.state.collectAsState()
    val floatingLyricEnabled = systemLyricState.floatingCapsuleEnabled
    val statusBarLyricEnabled = systemLyricState.statusBarBroadcastEnabled

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("歌词显示设置", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("字号大小", style = MaterialTheme.typography.bodyMedium)
                        Text("${fontSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium, color = ConceptPrimary)
                    }
                    Slider(
                        value = fontSize,
                        onValueChange = onSetFontSize,
                        valueRange = 14f..26f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("文本对齐", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = alignment == LyricAlignment.CENTER,
                            onClick = { onSetAlignment(LyricAlignment.CENTER) },
                            label = { Text("居中") }
                        )
                        FilterChip(
                            selected = alignment == LyricAlignment.LEFT,
                            onClick = { onSetAlignment(LyricAlignment.LEFT) },
                            label = { Text("居左") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("KRC逐字动效", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = wordByWord,
                        onCheckedChange = onSetWordByWord,
                        colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("全局悬浮逐字歌词", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "切回其它页面时悬浮展示 KRC 逐字胶囊",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = floatingLyricEnabled,
                        onCheckedChange = { SystemLyricBridge.setFloatingCapsuleEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("系统状态栏歌词广播", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "适配 Flyme / HyperOS / EvoX / LyricGetter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = statusBarLyricEnabled,
                        onCheckedChange = { SystemLyricBridge.setStatusBarBroadcastEnabled(context, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

/**
 * 底部播放控制台
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
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val effectiveFraction = if (isDraggingSlider) dragFraction else currentFraction

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (durationMs > 0L && climaxEndMs > climaxStartMs) {
                val startFrac = (climaxStartMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val endFrac = (climaxEndMs.toFloat() / durationMs.toFloat()).coerceIn(startFrac, 1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(10.dp)
                ) {
                    val barStart = size.width * startFrac
                    val barWidth = (size.width * (endFrac - startFrac)).coerceAtLeast(8.dp.toPx())
                    val alpha = if (highlightClimaxRange) 0.85f else 0.32f
                    drawRoundRect(
                        color = VipGold.copy(alpha = alpha),
                        topLeft = Offset(barStart, size.height * 0.25f),
                        size = Size(barWidth, 5.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }

            Slider(
                value = effectiveFraction,
                onValueChange = {
                    isDraggingSlider = true
                    dragFraction = it
                },
                onValueChangeFinished = {
                    onSeekTo((dragFraction * durationMs).toLong())
                    isDraggingSlider = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = if (highlightClimaxRange) VipGold else Color.White,
                    activeTrackColor = if (highlightClimaxRange) VipGold else ConceptPrimary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayCurrent = if (isDraggingSlider) (dragFraction * durationMs).toLong() else currentPositionMs
            Text(
                text = formatDuration(displayCurrent),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            )
            if (durationMs > 0L && climaxEndMs > climaxStartMs) {
                Text(
                    text = "副歌 ${formatDuration(climaxStartMs)}~${formatDuration(climaxEndMs)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (highlightClimaxRange) VipGold else Color.White.copy(alpha = 0.35f),
                        fontSize = 10.sp,
                        fontWeight = if (highlightClimaxRange) FontWeight.Bold else FontWeight.Normal
                    ),
                    modifier = Modifier.softClickable { onSeekTo(climaxStartMs) }
                )
            }
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.width(36.dp))

            Surface(
                color = ConceptPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .size(68.dp)
                    .softCardClickable(onClick = onTogglePlay),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(36.dp))

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    onCyclePlayMode()
                    val nextDesc = when (playMode) {
                        PlayMode.REPEAT_ALL -> "已切换为单曲循环"
                        PlayMode.REPEAT_ONE -> "已切换为随机播放"
                        PlayMode.SHUFFLE -> "已切换为列表循环"
                    }
                    Toast.makeText(context, nextDesc, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(40.dp)
            ) {
                val icon = when (playMode) {
                    PlayMode.REPEAT_ALL -> Icons.Rounded.Repeat
                    PlayMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
                    PlayMode.SHUFFLE -> Icons.Rounded.Shuffle
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "播放模式",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier.softCardClickable(onClick = onOpenComments)
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

            IconButton(
                onClick = onQueueClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "播放列表",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * 播放队列 BottomSheet（公开可复用组件，支持从底部悬浮控制条或播放页打开，全面适配浅色/深色主题）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentIndex: Int,
    playMode: PlayMode = PlayMode.REPEAT_ALL,
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
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            // 顶部标题栏 + 循环模式切换胶囊 + 清空按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                            text = "${(currentIndex + 1).coerceAtMost(queue.size.coerceAtLeast(1))} / ${queue.size}",
                            color = ConceptPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                        border = BorderStroke(0.6.dp, textPrimary.copy(alpha = 0.08f)),
                        modifier = Modifier.softCardClickable(onClick = onCyclePlayMode)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(modeIcon, contentDescription = null, tint = textSecondary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(modeName, color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("清空", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // [创新点2] AI 情绪与声学漫游过渡 (Harmonic & Mood DJ) 卡片重排栏
            if (queue.size > 2) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(0.6.dp, textPrimary.copy(alpha = 0.07f)),
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
                                border = BorderStroke(
                                    0.6.dp,
                                    if (isSelected) ConceptPrimary else ConceptPrimary.copy(alpha = 0.32f)
                                ),
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
                            border = if (isPlayingThis) {
                                BorderStroke(0.8.dp, ConceptPrimary.copy(alpha = 0.45f))
                            } else {
                                BorderStroke(0.5.dp, textPrimary.copy(alpha = 0.04f))
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

                                // 歌曲圆角小封面
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
 * 全新 Bento 网格化播放页「更多功能控制台」抽屉（自适应浅色/深色主题）
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
    onSelectCoverStyle: (CoverStyle) -> Unit,
    onToggleClimaxMode: () -> Unit,
    onToggleAccompanyMode: () -> Unit,
    onOpenFocusPod: () -> Unit,
    onOpenAudioEffect: () -> Unit,
    onOpenSpeedPitch: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQualitySelect: () -> Unit,
    onOpenSongDetail: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    val systemLyricState by SystemLyricBridge.state.collectAsState()
    val floatingLyricEnabled = systemLyricState.floatingCapsuleEnabled
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBg = conceptSheetContainerColor()
    val tileBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 顶部当前曲目名片与快捷操作
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
                        fontSize = 15.sp,
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
                FilledTonalButton(
                    onClick = onAddToPlaylist,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ConceptPrimary.copy(alpha = 0.16f),
                        contentColor = ConceptPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("收藏歌单", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 2. 四大概念多态机芯皮肤视觉切换卡
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "概念多态机芯皮肤",
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoverStyle.entries.forEach { style ->
                        val selected = coverStyle == style
                        val icon = when (style) {
                            CoverStyle.VINYL -> Icons.Rounded.Album
                            CoverStyle.CASSETTE -> Icons.Rounded.Radio
                            CoverStyle.POSTER -> Icons.Rounded.Style
                            CoverStyle.CIRCLE -> Icons.Rounded.Circle
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) ConceptPrimary.copy(alpha = 0.16f) else tileBg,
                            border = BorderStroke(
                                if (selected) 1.2.dp else 0.6.dp,
                                if (selected) ConceptPrimary else textPrimary.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .softCardClickable { onSelectCoverStyle(style) }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = style.label,
                                    tint = if (selected) ConceptPrimary else textSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = style.label,
                                    color = if (selected) ConceptPrimary else textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 3. Bento 双行多功能矩阵卡片
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
                        title = "副歌刷刷",
                        subtitle = if (climaxModeEnabled) "高潮连播已开" else "30秒黄金高潮",
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
                        icon = Icons.Rounded.SelfImprovement,
                        title = "心流番茄舱",
                        subtitle = "番茄钟×白噪音",
                        active = false,
                        onClick = onOpenFocusPod,
                        modifier = Modifier.weight(1f)
                    )
                    BentoControlTile(
                        icon = Icons.Rounded.Lyrics,
                        title = "全局悬浮词",
                        subtitle = if (floatingLyricEnabled) "已开启胶囊" else "跨页逐字词",
                        active = floatingLyricEnabled,
                        onClick = { SystemLyricBridge.setFloatingCapsuleEnabled(!floatingLyricEnabled) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BentoControlTile(
                        icon = Icons.Rounded.GraphicEq,
                        title = "蝰蛇音效舱",
                        subtitle = "5段EQ/混响",
                        active = false,
                        onClick = onOpenAudioEffect,
                        modifier = Modifier.weight(1f)
                    )
                    BentoControlTile(
                        icon = Icons.Rounded.Speed,
                        title = "倍速与变调",
                        subtitle = "${String.format(java.util.Locale.getDefault(), "%.2f", playbackSpeed)}x 播放",
                        active = playbackSpeed != 1.0f,
                        onClick = onOpenSpeedPitch,
                        modifier = Modifier.weight(1f)
                    )
                    val sleepDesc = when {
                        sleepTimerRemainingSec > 0L -> formatDuration(sleepTimerRemainingSec * 1000L)
                        sleepStopAfterCurrentSong -> "播完本首停"
                        else -> "定时渐弱"
                    }
                    BentoControlTile(
                        icon = Icons.Rounded.Bedtime,
                        title = "睡眠定时",
                        subtitle = sleepDesc,
                        active = sleepTimerRemainingSec > 0L || sleepStopAfterCurrentSong,
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
                border = BorderStroke(0.6.dp, textPrimary.copy(alpha = 0.08f)),
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
        border = BorderStroke(
            if (active) 1.dp else 0.6.dp,
            if (active) activeColor.copy(alpha = 0.55f) else textPrimary.copy(alpha = 0.07f)
        ),
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

/**
 * 音质选择弹窗 (动态过滤，仅显示该歌曲真实拥有的音质档位)
 */
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
                        AudioQuality.HI_RES -> "Hi-Res 母带 (24bit/96kHz 超高解析)"
                        AudioQuality.LOSSLESS -> "无损 SQ (FLAC / 真实母带压制)"
                        AudioQuality.HIGH -> "高品 320K (320kbps MP3)"
                        AudioQuality.STANDARD -> "标准 128K (标准音质)"
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
 * 睡眠定时器弹窗 ([FUNC-03])
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerDialog(
    remainingSec: Long,
    stopAfterCurrentSong: Boolean,
    onSetTimer: (minutes: Int, stopAfterCurrentSong: Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var stopAfterSong by remember { mutableStateOf(stopAfterCurrentSong) }
    val presets = listOf(15, 30, 45, 60, 90)

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
                if (remainingSec > 0L || stopAfterCurrentSong) {
                    Surface(
                        color = ConceptPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val statusText = if (remainingSec > 0L) {
                            "倒计时进行中：剩余 ${formatDuration(remainingSec * 1000L)}" +
                                if (stopAfterCurrentSong) "（到点后播完当前曲目停止）" else "（含5秒音量渐弱）"
                        } else {
                            "已开启：播完当前曲目后自动停止"
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
                                Toast.makeText(context, "已设置 $mins 分钟后睡眠停止", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("${mins}分钟") }
                        )
                    }
                    FilterChip(
                        selected = stopAfterCurrentSong && remainingSec == 0L,
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
                    Text("倒计时结束后播完当前歌曲再停", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = stopAfterSong,
                        onCheckedChange = { stopAfterSong = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                    )
                }
            }
        },
        dismissButton = {
            if (remainingSec > 0L || stopAfterCurrentSong) {
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

/**
 * 倍速与变调调节面板 ([FUNC-04])
 */
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
                            text = "${String.format(java.util.Locale.getDefault(), "%.2f", speed)}x",
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
                            text = "${String.format(java.util.Locale.getDefault(), "%.2f", pitch)}x",
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

/**
 * [UI-01] 从封面 URL 采样并提取暗色护眼双核网格渐变色（自动钳制饱和度与明度，防止亮色封面刺眼）
 */
private suspend fun extractCoverMeshColors(
    context: android.content.Context,
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
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

/**
 * [维度1] 实时检测当前音频输出路由（蓝牙耳机 / 有线耳机 / USB DAC / 扬声器）
 */
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
