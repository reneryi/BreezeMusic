package com.concept.music.ui.components

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.Song
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptMotion
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalConceptAdaptiveInfo
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.readableOnDarkSurface
import com.concept.music.ui.theme.rememberIsLifecycleStarted
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricHighlightTheme
import com.concept.music.ui.viewmodel.PlayMode
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * 现代沉浸式全屏横屏播放页面 (LandscapePlayerPage)：
 * 1. 界面视觉重心聚焦于「大画幅 3D 专辑封面 / 拟真黑胶」与「全高 KRC 逐字流光歌词」左右合体舞台；
 * 2. 彻底重构左下方歌名与歌手布局：采用「上歌名 + 下歌手」双层独立无限跑马灯结构（`iterations = Int.MAX_VALUE`），
 *    彻底根治歌手名过长挤占导致短歌名（如 Vibe）宽度归零消失、以及跑马灯滚动 3 次后半截停滞（如 Need Som）的问题；
 * 3. 支持长按封面快捷呼出高清原图预览与纯图标下载按钮；
 * 4. 针对平板横屏（`isTabletLandscapePlayer`）深度自适应：放大封面与控播坞尺寸、收敛纵向间距消除断层，
 *    并无感融合封面页专属辅助工具栏（音质切换、加入歌单、歌词海报、桌面歌词、蝰蛇音效、评论计数、更多设置），清除重复按钮。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun LandscapePlayerPage(
    song: Song,
    isPlaying: Boolean,
    currentPositionMs: Long,
    rawPositionMs: Long,
    durationMs: Long,
    lyricPositionProvider: () -> Long = { currentPositionMs },
    rawPositionProvider: () -> Long = { rawPositionMs },
    playMode: PlayMode,
    coverStyle: CoverStyle = CoverStyle.SQUARE,
    rotationProvider: () -> Float = { 0f },
    playerBackgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.COVER_BLUR,
    lyricData: LyricData,
    currentLyricIndex: Int,
    isFavorite: Boolean,
    showTranslation: Boolean,
    showTranslationButton: Boolean,
    showPhonetic: Boolean,
    showPhoneticButton: Boolean,
    lyricFontSize: Float,
    lyricTranslationScale: Float,
    lyricWordByWord: Boolean,
    lyricBlurEnabled: Boolean,
    lyricBlurIntensity: Float,
    lyricLineSpacing: Float,
    lyricHighlightTheme: LyricHighlightTheme,
    climaxStartMs: Long,
    climaxEndMs: Long,
    highlightClimaxRange: Boolean,
    accentPrimary: Color = ConceptPrimary,
    accentSecondary: Color = ConceptCyanAccent,
    currentQualityLabel: String = "",
    commentCount: Int = 0,
    viperEnabled: Boolean = false,
    desktopLyricEnabled: Boolean = false,
    onExitLandscape: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekToLyricLine: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onToggleTranslation: () -> Unit,
    onTogglePhonetic: () -> Unit,
    onOpenLyricSettings: () -> Unit,
    onSelectPlayerBackgroundStyle: (PlayerBackgroundStyle) -> Unit = {},
    onLongPressCover: () -> Unit = {},
    onQualityClick: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onOpenLyricPoster: () -> Unit = {},
    onToggleDesktopLyric: () -> Unit = {},
    onOpenAudioEffect: () -> Unit = {},
    onOpenComments: () -> Unit = {},
    onOpenMoreSheet: () -> Unit = {}
) {
    val context = LocalContext.current
    val adaptiveInfo = LocalConceptAdaptiveInfo.current
    val isTabletLandscape = adaptiveInfo.isTabletLandscapePlayer
    var showBgPickerMenu by remember { mutableStateOf(false) }

    val readableAccentPrimary = remember(accentPrimary) { accentPrimary.readableOnDarkSurface(0.46f) }
    val readableAccentSecondary = remember(accentSecondary) { accentSecondary.readableOnDarkSurface(0.50f) }

    val activePrimaryColor = if (lyricHighlightTheme == LyricHighlightTheme.DYNAMIC_COVER) {
        readableAccentPrimary
    } else {
        Color(lyricHighlightTheme.primaryHex)
    }
    val activeSecondaryColor = if (lyricHighlightTheme == LyricHighlightTheme.DYNAMIC_COVER) {
        readableAccentSecondary
    } else {
        Color(lyricHighlightTheme.secondaryHex)
    }

    // 横屏圆角封面边缘色散光晕与流光框连续相位动画
    val coverMotionState = rememberRoundedCoverMotionState(
        isPlaying = isPlaying,
        songKey = song.id.ifBlank { song.coverUrl }
    )
    val haloBreath = coverMotionState.haloPulse
    val dispersionPhase = coverMotionState.dispersionPhase
    val playTransition = coverMotionState.playTransition

    val coverPlayScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.92f,
        animationSpec = ConceptMotion.gentleBounceSpring(),
        label = "landscapeCoverPlayScale"
    )

    val effectiveLyricFontSize = if (isTabletLandscape) (lyricFontSize + 2f).coerceAtMost(36f) else lyricFontSize

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isTabletLandscape) 36.dp else 24.dp,
                vertical = if (isTabletLandscape) 18.dp else 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // =========================================================================
            // 左区 (weight 0.46f)：大尺寸主角封面 + 一体化控播坞（平板下自适应放大并消除中部断层）
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(if (isTabletLandscape) 0.46f else 0.45f)
                    .fillMaxHeight()
                    .padding(end = if (isTabletLandscape) 24.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (isTabletLandscape) Arrangement.Center else Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isTabletLandscape) {
                                Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxWidth()
                                    .padding(bottom = 18.dp)
                            } else {
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp)
                            }
                        )
                        .pointerInput(song.id) {
                            detectTapGestures(
                                onDoubleTap = { onToggleFavorite() },
                                onLongPress = { onLongPressCover() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BoxWithConstraints(
                        contentAlignment = Alignment.Center,
                        modifier = if (isTabletLandscape) Modifier.wrapContentSize() else Modifier.fillMaxSize()
                    ) {
                        val coverSide = if (isTabletLandscape) {
                            minOf(
                                (adaptiveInfo.widthDp * 0.30f).dp,
                                (adaptiveInfo.heightDp * 0.48f).dp
                            ).coerceIn(216.dp, 328.dp)
                        } else {
                            minOf(maxWidth * 0.74f, maxHeight * 0.88f).coerceIn(152.dp, 238.dp)
                        }
                        val cornerRadiusDp = if (isTabletLandscape) 32.dp else 26.dp

                        if (isTabletLandscape && coverStyle == CoverStyle.VINYL) {
                            // 平板横屏合并播放页下支持展示旋转黑胶唱盘样式
                            Box(
                                modifier = Modifier
                                    .size(coverSide)
                                    .shadow(
                                        elevation = 28.dp,
                                        shape = CircleShape,
                                        spotColor = accentPrimary.copy(alpha = 0.55f)
                                    )
                                    .clip(CircleShape)
                                    .background(Color(0xFF10131C))
                                    .graphicsLayer { rotationZ = rotationProvider() },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val r = size.minDimension / 2f
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(Color(0xFF222733), Color(0xFF101218), Color(0xFF08090D)),
                                            center = c,
                                            radius = r
                                        ),
                                        radius = r,
                                        center = c
                                    )
                                    val innerR = r * 0.64f
                                    val outerR = r * 0.95f
                                    val step = (outerR - innerR) / 12f
                                    for (i in 0..11) {
                                        drawCircle(
                                            color = Color.White.copy(alpha = if (i % 3 == 0) 0.09f else 0.035f),
                                            radius = innerR + i * step,
                                            center = c,
                                            style = Stroke(width = 0.8.dp.toPx())
                                        )
                                    }
                                    drawCircle(
                                        color = VipGold.copy(alpha = 0.55f),
                                        radius = r * 0.625f,
                                        center = c,
                                        style = Stroke(width = 1.8.dp.toPx())
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(coverSide * 0.61f)
                                        .clip(CircleShape)
                                        .background(Color(0xFF181C26)),
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
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xAA0A0D14)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF07090E))
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(coverSide)
                                    .graphicsLayer {
                                        val tilt = sin(dispersionPhase) * (0.55f + 1.05f * playTransition)
                                        rotationY = tilt
                                        rotationX = -tilt * 0.50f
                                        scaleX = coverPlayScale
                                        scaleY = coverPlayScale
                                        cameraDistance = 14f * density
                                        clip = false
                                    }
                                    .drawBehind {
                                        drawRoundedCoverEdgeDispersionHalo(
                                            cornerPx = cornerRadiusDp.toPx(),
                                            accentPrimary = accentPrimary,
                                            accentSecondary = accentSecondary,
                                            dispersionPhase = dispersionPhase,
                                            haloPulse = haloBreath,
                                            isPlaying = isPlaying,
                                            playTransition = playTransition
                                        )
                                    }
                                    .clip(RoundedCornerShape(cornerRadiusDp))
                                    .background(Color(0xFF151A26))
                                    .drawWithContent {
                                        drawContent()
                                        drawRoundedCoverPrismaticRim(
                                            cornerPx = cornerRadiusDp.toPx(),
                                            accentPrimary = accentPrimary,
                                            accentSecondary = accentSecondary,
                                            dispersionPhase = dispersionPhase,
                                            isPlaying = isPlaying,
                                            playTransition = playTransition
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Crossfade(
                                    targetState = song.coverUrl,
                                    animationSpec = tween(durationMillis = 320, easing = ConceptMotion.EmphasizedDecelerate),
                                    label = "landscapeCoverCrossfade"
                                ) { activeCoverUrl ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (activeCoverUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = activeCoverUrl,
                                                contentDescription = song.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier.size(52.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 底部一体化控播坞（独立隔离高频进度刷新 + 歌名/歌手双层独立无限跑马灯）
                LandscapeBottomControlDock(
                    song = song,
                    isPlaying = isPlaying,
                    rawPositionMs = rawPositionMs,
                    rawPositionProvider = rawPositionProvider,
                    durationMs = durationMs,
                    playMode = playMode,
                    isFavorite = isFavorite,
                    climaxStartMs = climaxStartMs,
                    climaxEndMs = climaxEndMs,
                    highlightClimaxRange = highlightClimaxRange,
                    accentPrimary = accentPrimary,
                    accentSecondary = accentSecondary,
                    isTabletLandscape = isTabletLandscape,
                    currentQualityLabel = currentQualityLabel,
                    commentCount = commentCount,
                    viperEnabled = viperEnabled,
                    desktopLyricEnabled = desktopLyricEnabled,
                    onToggleFavorite = onToggleFavorite,
                    onSeekTo = onSeekTo,
                    onCyclePlayMode = {
                        onCyclePlayMode()
                        val nextDesc = when (playMode) {
                            PlayMode.REPEAT_ALL -> "已切换为单曲循环"
                            PlayMode.REPEAT_ONE -> "已切换为随机播放"
                            PlayMode.SHUFFLE -> "已切换为列表循环"
                        }
                        Toast.makeText(context, nextDesc, Toast.LENGTH_SHORT).show()
                    },
                    onPrevious = onPrevious,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onOpenQueue = onOpenQueue,
                    onQualityClick = onQualityClick,
                    onAddToPlaylist = onAddToPlaylist,
                    onOpenLyricPoster = onOpenLyricPoster,
                    onToggleDesktopLyric = onToggleDesktopLyric,
                    onOpenAudioEffect = onOpenAudioEffect,
                    onOpenComments = onOpenComments,
                    onOpenMoreSheet = onOpenMoreSheet
                )
            }

            // =========================================================================
            // 右区 (weight 0.54f~0.55f)：全高沉浸式 KRC 逐字流光歌词
            // =========================================================================
            BoxWithConstraints(
                modifier = Modifier
                    .weight(if (isTabletLandscape) 0.54f else 0.55f)
                    .fillMaxHeight()
                    .padding(start = if (isTabletLandscape) 18.dp else 10.dp)
            ) {
                val containerHeightDp = maxHeight
                val density = androidx.compose.ui.platform.LocalDensity.current
                val estimatedHalfItemDp = ((effectiveLyricFontSize + 18f) * 0.5f).dp
                val dynamicVerticalPadDp = ((containerHeightDp * 0.5f) - estimatedHalfItemDp).coerceAtLeast(88.dp)

                if (lyricData.lines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "纯音乐或暂无歌词",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = if (isTabletLandscape) 17.sp else 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    val isDragged by listState.interactionSource.collectIsDraggedAsState()
                    var isUserScrubbing by remember { mutableStateOf(false) }

                    LaunchedEffect(isDragged) {
                        if (isDragged) isUserScrubbing = true
                    }
                    LaunchedEffect(isUserScrubbing, isDragged, listState.isScrollInProgress) {
                        if (isUserScrubbing && !isDragged && !listState.isScrollInProgress) {
                            delay(3000L)
                            isUserScrubbing = false
                        }
                    }

                    LaunchedEffect(song.id, lyricData.lines.size) {
                        if (lyricData.lines.isNotEmpty()) {
                            val initIdx = currentLyricIndex.coerceIn(0, lyricData.lines.lastIndex)
                            listState.scrollToItem(index = initIdx, scrollOffset = 0)
                        }
                    }

                    LaunchedEffect(currentLyricIndex, isUserScrubbing, lyricData.lines.size) {
                        if (!isUserScrubbing && lyricData.lines.isNotEmpty()) {
                            val targetIdx = currentLyricIndex.coerceIn(0, lyricData.lines.lastIndex)
                            val layoutInfo = listState.layoutInfo
                            val viewportStart = layoutInfo.viewportStartOffset
                            val viewportEnd = layoutInfo.viewportEndOffset
                            val viewportH = (viewportEnd - viewportStart).takeIf { it > 0 }
                                ?: with(density) { containerHeightDp.toPx().toInt() }.coerceAtLeast(320)
                            val viewportCenterY = (viewportStart + viewportEnd) / 2
                            val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIdx }

                            if (targetItem != null) {
                                val itemCenterY = targetItem.offset + (targetItem.size / 2)
                                val deltaPx = (itemCenterY - viewportCenterY).toFloat()
                                if (abs(deltaPx) > 2f) {
                                    listState.animateScrollBy(
                                        value = deltaPx,
                                        animationSpec = tween(durationMillis = 380, easing = ConceptMotion.LyricSmoothScrollEasing)
                                    )
                                }
                            } else {
                                val avgItemHeightPx = layoutInfo.visibleItemsInfo
                                    .takeIf { it.isNotEmpty() }
                                    ?.map { it.size }
                                    ?.average()
                                    ?.toInt()
                                    ?: with(density) { (effectiveLyricFontSize + 28f).dp.toPx().toInt() }
                                val topPadPx = layoutInfo.beforeContentPadding
                                val desiredTopInViewport = (viewportH - avgItemHeightPx) / 2
                                val centerScrollOffset = topPadPx - desiredTopInViewport
                                listState.animateScrollToItem(
                                    index = targetIdx,
                                    scrollOffset = centerScrollOffset
                                )
                                val settledLayout = listState.layoutInfo
                                val settledCenter = (settledLayout.viewportStartOffset + settledLayout.viewportEndOffset) / 2
                                val settledItem = settledLayout.visibleItemsInfo.firstOrNull { it.index == targetIdx }
                                if (settledItem != null) {
                                    val fineDelta = ((settledItem.offset + settledItem.size / 2) - settledCenter).toFloat()
                                    if (abs(fineDelta) > 2f) {
                                        listState.animateScrollBy(
                                            value = fineDelta,
                                            animationSpec = tween(durationMillis = 220, easing = ConceptMotion.LyricSmoothScrollEasing)
                                        )
                                    }
                                }
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

                    val supportsHardwareBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = if (isTabletLandscape) 44.dp else 36.dp)
                            .graphicsLayer { alpha = 0.99f }
                            .drawWithContent {
                                drawContent()
                                val fadeH = (size.height * 0.14f).coerceIn(24.dp.toPx(), 64.dp.toPx())
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        1.0f to Color.Black,
                                        startY = 0f,
                                        endY = fadeH
                                    ),
                                    size = Size(size.width, fadeH),
                                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                )
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.0f to Color.Black,
                                        1.0f to Color.Transparent,
                                        startY = (size.height - fadeH).coerceAtLeast(0f),
                                        endY = size.height
                                    ),
                                    topLeft = Offset(0f, (size.height - fadeH).coerceAtLeast(0f)),
                                    size = Size(size.width, fadeH),
                                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                )
                            },
                        contentPadding = PaddingValues(vertical = dynamicVerticalPadDp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        itemsIndexed(
                            items = lyricData.lines,
                            key = { idx, item -> "land_${idx}_${item.startTimeMs}" },
                            contentType = { _, _ -> "landscape_lyric_line" }
                        ) { index, line ->
                            val effectiveActiveIdx = currentLyricIndex.coerceAtLeast(0)
                            val isCurrent = index == effectiveActiveIdx
                            val isScrubTarget = isUserScrubbing && index == scrubLineIndex
                            val distance = abs(index - effectiveActiveIdx)

                            val lineScaleState = animateFloatAsState(
                                targetValue = when {
                                    isCurrent -> 1.05f
                                    isScrubTarget -> 1.0f
                                    else -> (0.93f - distance * 0.022f).coerceAtLeast(0.86f)
                                },
                                animationSpec = ConceptMotion.spatialPageSpring(),
                                label = "landLyricScale"
                            )
                            val targetDepthBlurVal = if (lyricBlurEnabled && !isUserScrubbing && !isCurrent) {
                                when (distance) {
                                    1 -> 0.72f * lyricBlurIntensity
                                    2 -> 1.40f * lyricBlurIntensity
                                    else -> 2.40f * lyricBlurIntensity
                                }.coerceIn(0f, 5f)
                            } else 0f
                            val depthBlurVal by animateFloatAsState(
                                targetValue = targetDepthBlurVal,
                                animationSpec = tween(durationMillis = 360, easing = ConceptMotion.LyricSmoothScrollEasing),
                                label = "landLyricDepthBlur"
                            )
                            val targetDistanceAlpha = when {
                                isCurrent -> 1.0f
                                isScrubTarget -> 0.92f
                                distance == 1 -> 0.56f
                                distance == 2 -> 0.34f
                                else -> 0.16f
                            }
                            val distanceAlpha by animateFloatAsState(
                                targetValue = targetDistanceAlpha,
                                animationSpec = tween(durationMillis = 360, easing = ConceptMotion.LyricSmoothScrollEasing),
                                label = "landLyricDistanceAlpha"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        val s = lineScaleState.value
                                        scaleX = s
                                        scaleY = s
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                    }
                                    .then(
                                        if (supportsHardwareBlur && depthBlurVal >= 0.05f) {
                                            Modifier.blur(depthBlurVal.dp)
                                        } else Modifier
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isScrubTarget) activePrimaryColor.copy(alpha = 0.10f) else Color.Transparent
                                    )
                                    .softClickable(
                                        pressScale = 0.98f,
                                        onClick = {
                                            isUserScrubbing = false
                                            onSeekToLyricLine(index)
                                        }
                                    )
                                    .padding(vertical = (lyricLineSpacing * 0.85f).dp, horizontal = 8.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (index == 0 && currentLyricIndex <= 0) {
                                        LyricInterludeBreathingIndicator(
                                            lyricData = lyricData,
                                            lineIndex = -1,
                                            initialPositionMs = currentPositionMs,
                                            isPlaying = isPlaying,
                                            lyricPositionProvider = lyricPositionProvider,
                                            lyricFontSize = effectiveLyricFontSize,
                                            accentPrimary = activePrimaryColor,
                                            accentSecondary = activeSecondaryColor,
                                            horizontalAlignment = Alignment.Start,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }

                                    val phoneticText = line.phonetic
                                    if (showPhonetic && showPhoneticButton && !phoneticText.isNullOrBlank()) {
                                        val phoneticFontSize = (effectiveLyricFontSize * lyricTranslationScale * 0.85f).coerceAtLeast(11.5f)
                                        Text(
                                            text = phoneticText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isCurrent) activePrimaryColor.copy(alpha = 0.90f) else Color.White.copy(alpha = (distanceAlpha * 0.68f).coerceIn(0.12f, 0.55f)),
                                                fontSize = phoneticFontSize.sp,
                                                lineHeight = (phoneticFontSize * 1.38f).sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                    }

                                    if (isCurrent && line.hasWordTiming && lyricWordByWord) {
                                        LandscapeActiveKaraokeWords(
                                            line = line,
                                            isPlaying = isPlaying,
                                            lyricPositionProvider = lyricPositionProvider,
                                            lyricFontSize = effectiveLyricFontSize,
                                            activePrimaryColor = activePrimaryColor,
                                            activeSecondaryColor = activeSecondaryColor
                                        )
                                    } else {
                                        Text(
                                            text = line.text,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isCurrent) activePrimaryColor else Color.White.copy(alpha = distanceAlpha),
                                                fontSize = effectiveLyricFontSize.sp,
                                                lineHeight = (effectiveLyricFontSize + 8f).sp
                                            )
                                        )
                                    }

                                    val trans = line.translation
                                    if (showTranslation && showTranslationButton && !trans.isNullOrBlank()) {
                                        val transFontSize = (effectiveLyricFontSize * lyricTranslationScale).coerceAtLeast(13f)
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Text(
                                            text = trans,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (isCurrent) activePrimaryColor.copy(alpha = 0.88f) else Color.White.copy(alpha = (distanceAlpha * 0.78f).coerceIn(0.14f, 0.58f)),
                                                fontSize = transFontSize.sp,
                                                lineHeight = (transFontSize * 1.42f).sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }

                                    if (isCurrent) {
                                        LyricInterludeBreathingIndicator(
                                            lyricData = lyricData,
                                            lineIndex = index,
                                            initialPositionMs = currentPositionMs,
                                            isPlaying = isPlaying,
                                            lyricPositionProvider = lyricPositionProvider,
                                            lyricFontSize = effectiveLyricFontSize,
                                            accentPrimary = activePrimaryColor,
                                            accentSecondary = activeSecondaryColor,
                                            horizontalAlignment = Alignment.Start,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isUserScrubbing && scrubLineIndex in lyricData.lines.indices,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(end = if (isTabletLandscape) 44.dp else 36.dp)
                    ) {
                        val targetTimeMs = lyricData.lines.getOrNull(scrubLineIndex)?.startTimeMs ?: 0L
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xD90E1624)
                                ) {
                                    Text(
                                        text = formatPlayerDuration(targetTimeMs),
                                        color = activePrimaryColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(1.5.dp)
                                        .background(Brush.horizontalGradient(listOf(activePrimaryColor.copy(alpha = 0.65f), Color.Transparent)))
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(1.5.dp)
                                        .background(Brush.horizontalGradient(listOf(Color.Transparent, activePrimaryColor.copy(alpha = 0.65f))))
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = activePrimaryColor.copy(alpha = 0.28f),
                                    modifier = Modifier
                                        .softCardClickable(shape = RoundedCornerShape(10.dp)) {
                                            isUserScrubbing = false
                                            onSeekToLyricLine(scrubLineIndex)
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Text("播放", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 悬浮无框沉浸式四角按钮层
        // =========================================================================
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(36.dp)
                .softClickable(pressScale = 0.82f, onClick = onExitLandscape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = Color.White.copy(alpha = 0.86f),
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .softClickable(
                        pressScale = 0.82f,
                        onClick = { showBgPickerMenu = !showBgPickerMenu }
                    ),
                contentAlignment = Alignment.Center
            ) {
                LandscapeBackgroundPrismCanvasIcon(
                    accentColor = if (showBgPickerMenu) readableAccentPrimary else Color.White.copy(alpha = 0.86f),
                    secondaryColor = readableAccentSecondary
                )
            }

            AnimatedVisibility(
                visible = showBgPickerMenu,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.90f, animationSpec = ConceptMotion.expressiveSpring()),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.90f, animationSpec = ConceptMotion.spatialSheetSpring())
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xEB0E1524),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerBackgroundStyle.entries.forEach { bgStyle ->
                            val selected = playerBackgroundStyle == bgStyle
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) accentPrimary.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.05f),
                                modifier = Modifier.softCardClickable(shape = RoundedCornerShape(10.dp)) {
                                    onSelectPlayerBackgroundStyle(bgStyle)
                                }
                            ) {
                                Text(
                                    text = bgStyle.label,
                                    color = if (selected) readableAccentPrimary else Color.White.copy(alpha = 0.82f),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = showTranslationButton,
                enter = scaleIn(animationSpec = ConceptMotion.expressiveSpring()) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .softClickable(pressScale = 0.82f, onClick = onToggleTranslation),
                    contentAlignment = Alignment.Center
                ) {
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
                                .size(width = 10.dp, height = 2.dp)
                                .clip(CircleShape)
                                .background(activePrimaryColor)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showPhoneticButton,
                enter = scaleIn(animationSpec = ConceptMotion.expressiveSpring()) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .softClickable(pressScale = 0.82f, onClick = onTogglePhonetic),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "音",
                        color = if (showPhonetic) activePrimaryColor else Color.White.copy(alpha = 0.72f),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (showPhonetic) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .size(width = 10.dp, height = 2.dp)
                                .clip(CircleShape)
                                .background(activePrimaryColor)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .softClickable(pressScale = 0.82f, onClick = onOpenLyricSettings),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(19.dp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = 2.0.dp.toPx()
                    val c = Color.White.copy(alpha = 0.86f)
                    drawLine(c, Offset(w * 0.12f, h * 0.32f), Offset(w * 0.88f, h * 0.32f), stroke, StrokeCap.Round)
                    drawCircle(activePrimaryColor, radius = 2.8.dp.toPx(), center = Offset(w * 0.65f, h * 0.32f))

                    drawLine(c, Offset(w * 0.12f, h * 0.68f), Offset(w * 0.88f, h * 0.68f), stroke, StrokeCap.Round)
                    drawCircle(c, radius = 2.8.dp.toPx(), center = Offset(w * 0.35f, h * 0.68f))
                }
            }
        }
    }
}

/**
 * 横屏激活行局部 120fps 逐字流光渲染器
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LandscapeActiveKaraokeWords(
    line: LyricLine,
    isPlaying: Boolean,
    lyricPositionProvider: () -> Long,
    lyricFontSize: Float,
    activePrimaryColor: Color,
    activeSecondaryColor: Color
) {
    val isLifecycleStarted = rememberIsLifecycleStarted()
    var sampledPosMs by remember(line) { mutableLongStateOf(lyricPositionProvider()) }
    LaunchedEffect(line, isPlaying, isLifecycleStarted) {
        sampledPosMs = lyricPositionProvider()
        if (isPlaying && isLifecycleStarted) {
            while (isActive) {
                withFrameMillis {
                    sampledPosMs = lyricPositionProvider()
                }
            }
        }
    }

    val dimColor = Color.White.copy(alpha = 0.40f)
    FlowRow(
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        line.words.forEach { word ->
            val wordProgress = when {
                word.durationMs > 0L -> ((sampledPosMs - word.startTimeMs).toFloat() / word.durationMs.toFloat()).coerceIn(0f, 1f)
                sampledPosMs >= word.startTimeMs -> 1f
                else -> 0f
            }
            val isSinging = wordProgress in 0.01f..0.99f
            val annotatedWord = buildAnnotatedString {
                if (wordProgress <= 0f) {
                    withStyle(SpanStyle(color = dimColor)) { append(word.text) }
                } else if (wordProgress >= 1f) {
                    withStyle(SpanStyle(color = activePrimaryColor)) { append(word.text) }
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
                    ) { append(word.text) }
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
                    lineHeight = (lyricFontSize + 8f).sp,
                    shadow = if (isLongNote) {
                        Shadow(
                            color = activePrimaryColor.copy(alpha = (0.82f * wave * shimmer).coerceIn(0f, 1f)),
                            offset = Offset.Zero,
                            blurRadius = (20f * wave * shimmer + 6f).coerceAtLeast(1f)
                        )
                    } else null
                ),
                modifier = Modifier.graphicsLayer {
                    translationY = -3.0f * wave * shimmer
                    val s = 1f + 0.035f * wave * shimmer
                    scaleX = s
                    scaleY = s
                }
            )
        }
    }
}

/**
 * 横屏左下方一体化控播坞：
 * 1. 采用「上歌名 + 下歌手」双层错落结构，各自享有完整的横向空间并配备无限循环跑马灯（`iterations = Int.MAX_VALUE`）；
 *    - 彻底解决图三歌手名过长（如 `Famba、Emily Bonabon、Frank Pierce`）时抢占无权重空间导致短歌名（`Vibe`）宽度为 0 完全消失的问题；
 *    - 彻底解决图二歌名滚动几次后停止在截断状态（`Need Som`）的问题；
 * 2. 在平板横屏下（`isTabletLandscape`），额外整合封面页的非重复快捷动作栏（音质档位、加入歌单、歌词海报、桌面歌词、蝰蛇音效、评论与更多菜单）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LandscapeBottomControlDock(
    song: Song,
    isPlaying: Boolean,
    rawPositionMs: Long,
    rawPositionProvider: () -> Long,
    durationMs: Long,
    playMode: PlayMode,
    isFavorite: Boolean,
    climaxStartMs: Long,
    climaxEndMs: Long,
    highlightClimaxRange: Boolean,
    accentPrimary: Color,
    accentSecondary: Color,
    isTabletLandscape: Boolean = false,
    currentQualityLabel: String = "",
    commentCount: Int = 0,
    viperEnabled: Boolean = false,
    desktopLyricEnabled: Boolean = false,
    onToggleFavorite: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onQualityClick: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onOpenLyricPoster: () -> Unit = {},
    onToggleDesktopLyric: () -> Unit = {},
    onOpenAudioEffect: () -> Unit = {},
    onOpenComments: () -> Unit = {},
    onOpenMoreSheet: () -> Unit = {}
) {
    var isInteractingWithBar by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestOnSeekTo by rememberUpdatedState(onSeekTo)
    var settledSeekTargetMs by remember(song.id) { mutableLongStateOf(-1L) }
    var settledSeekTimestampMs by remember(song.id) { mutableLongStateOf(0L) }

    fun sampleLandscapePos(): Long {
        val rawPos = rawPositionProvider().takeIf { it > 0L } ?: rawPositionMs
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

    val isLifecycleStarted = rememberIsLifecycleStarted()
    var livePosMs by remember(song.id) { mutableLongStateOf(sampleLandscapePos()) }

    LaunchedEffect(song.id, isPlaying, isLifecycleStarted, isInteractingWithBar, rawPositionMs) {
        if (!isInteractingWithBar) {
            livePosMs = sampleLandscapePos()
        }
        if (isPlaying && isLifecycleStarted && !isInteractingWithBar) {
            while (isActive) {
                livePosMs = sampleLandscapePos()
                kotlinx.coroutines.delay(66L)
            }
        }
    }

    val currentFraction = if (durationMs > 0) {
        (livePosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val targetFraction = if (isInteractingWithBar) dragFraction else currentFraction
    val displayCurrentMs = if (isInteractingWithBar) (dragFraction * durationMs).toLong() else livePosMs

    val favScaleState = animateFloatAsState(
        targetValue = if (isFavorite) 1.16f else 1.0f,
        animationSpec = ConceptMotion.expressiveSpring(),
        label = "landFavScale"
    )
    val readableAccentPrimary = remember(accentPrimary) { accentPrimary.readableOnDarkSurface(0.45f) }

    Column(
        modifier = Modifier
            .widthIn(max = if (isTabletLandscape) 420.dp else 332.dp)
            .fillMaxWidth(0.95f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 歌曲名与歌手双层错落名片头（左：上歌名 + 下歌手独立无限跑马灯；右：时间指示 + 红心收藏）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 第一层：歌名独占整行，任何长度歌手名均不会挤压歌名宽度；超长歌名无限循环平滑滚动
                Text(
                    text = song.title.ifBlank { "未知歌曲" },
                    color = Color.White,
                    fontSize = if (isTabletLandscape) 17.sp else 14.5.sp,
                    lineHeight = if (isTabletLandscape) 21.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            delayMillis = 1500,
                            initialDelayMillis = 1200,
                            velocity = 26.dp
                        )
                )

                // 第二层：歌手名（平板下附带音质切换胶囊），超长多位合作歌手名同样平滑滚动不截断
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isTabletLandscape && currentQualityLabel.isNotBlank()) {
                        val isGoldQuality = currentQualityLabel.contains("Hi-Res", ignoreCase = true) ||
                            currentQualityLabel.contains("母带", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isGoldQuality) VipGold.copy(alpha = 0.20f) else accentPrimary.copy(alpha = 0.20f),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .softCardClickable(shape = RoundedCornerShape(6.dp), onClick = onQualityClick)
                        ) {
                            Text(
                                text = currentQualityLabel,
                                color = if (isGoldQuality) VipGold else readableAccentPrimary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                    Text(
                        text = song.artist.ifBlank { "未知歌手" },
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = if (isTabletLandscape) 13.sp else 11.5.sp,
                        lineHeight = if (isTabletLandscape) 16.sp else 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                delayMillis = 1800,
                                initialDelayMillis = 1400,
                                velocity = 22.dp
                            )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${formatPlayerDuration(displayCurrentMs)} / ${formatPlayerDuration(durationMs)}",
                    color = if (isInteractingWithBar) readableAccentPrimary else Color.White.copy(alpha = 0.56f),
                    fontSize = if (isTabletLandscape) 11.5.sp else 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
                )
                Box(
                    modifier = Modifier
                        .size(if (isTabletLandscape) 32.dp else 28.dp)
                        .graphicsLayer {
                            val s = favScaleState.value
                            scaleX = s
                            scaleY = s
                        }
                        .softClickable(pressScale = 0.78f, onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center
                ) {
                    PlumpHeartCanvasIcon(
                        isFavorite = isFavorite,
                        color = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(if (isTabletLandscape) 20.dp else 17.5.dp)
                    )
                }
            }
        }

        // 平板横屏合并播放页专属：无重复辅助功能栏（加入歌单、歌词海报、桌面歌词、蝰蛇音效、歌曲评论、更多设置）
        if (isTabletLandscape) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistAdd,
                        contentDescription = "添加到歌单",
                        tint = Color.White.copy(alpha = 0.84f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOpenLyricPoster,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "歌词海报",
                        tint = Color.White.copy(alpha = 0.84f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onToggleDesktopLyric,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = "桌面歌词",
                        tint = if (desktopLyricEnabled) readableAccentPrimary else Color.White.copy(alpha = 0.84f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onOpenAudioEffect,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "蝰蛇音效",
                        tint = if (viperEnabled) readableAccentPrimary else Color.White.copy(alpha = 0.84f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .widthIn(min = 42.dp)
                        .softClickable(onClick = onOpenComments),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubbleOutline,
                            contentDescription = "评论",
                            tint = Color.White.copy(alpha = 0.84f),
                            modifier = Modifier.size(18.dp)
                        )
                        val countStr = formatPlayerCommentCount(commentCount)
                        if (countStr.isNotEmpty()) {
                            Text(
                                text = countStr,
                                color = readableAccentPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onOpenMoreSheet,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "更多功能",
                        tint = Color.White.copy(alpha = 0.84f),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        CometTailProgressBar(
            fraction = targetFraction,
            isPlaying = isPlaying,
            isInteracting = isInteractingWithBar,
            currentTimeText = formatPlayerDuration(displayCurrentMs),
            totalTimeText = formatPlayerDuration(durationMs),
            climaxStartFrac = if (durationMs > 0L && climaxEndMs > climaxStartMs) {
                (climaxStartMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f,
            climaxEndFrac = if (durationMs > 0L && climaxEndMs > climaxStartMs) {
                (climaxEndMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f,
            highlightClimaxRange = highlightClimaxRange,
            accentPrimary = accentPrimary,
            accentSecondary = accentSecondary,
            compact = !isTabletLandscape,
            onDragStart = {
                isInteractingWithBar = true
                dragFraction = it
            },
            onDrag = { dragFraction = it },
            onDragEnd = {
                val targetMs = (dragFraction * latestDurationMs).toLong().coerceIn(0L, latestDurationMs.coerceAtLeast(0L))
                settledSeekTargetMs = targetMs
                settledSeekTimestampMs = android.os.SystemClock.elapsedRealtime()
                livePosMs = targetMs
                latestOnSeekTo(targetMs)
                isInteractingWithBar = false
            },
            onDragCancel = {
                isInteractingWithBar = false
            }
        )

        LandscapeCompactControlRow(
            playMode = playMode,
            isPlaying = isPlaying,
            isTabletLandscape = isTabletLandscape,
            accentPrimary = accentPrimary,
            accentSecondary = accentSecondary,
            onCyclePlayMode = onCyclePlayMode,
            onPrevious = onPrevious,
            onTogglePlay = onTogglePlay,
            onNext = onNext,
            onOpenQueue = onOpenQueue
        )
    }
}

/**
 * 横屏专属紧凑低重心播放控制栏（沿用封面页五大核心控制控件与连续几何插值播放/暂停形变）
 */
@Composable
private fun LandscapeCompactControlRow(
    playMode: PlayMode,
    isPlaying: Boolean,
    isTabletLandscape: Boolean = false,
    accentPrimary: Color,
    accentSecondary: Color,
    onCyclePlayMode: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val readableAccentPrimary = remember(accentPrimary) { accentPrimary.readableOnDarkSurface(0.45f) }
    val prevNudge = remember { Animatable(0f) }
    val nextNudge = remember { Animatable(0f) }
    val morphProgressState = animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = ConceptMotion.expressiveSpring(),
        label = "landPlayMorph"
    )
    val rowHeight = if (isTabletLandscape) 48.dp else 38.dp
    val centerBtnSize = if (isTabletLandscape) 46.dp else 38.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 播放模式
        Box(
            modifier = Modifier
                .size(if (isTabletLandscape) 38.dp else 34.dp)
                .softClickable(pressScale = 0.84f, onClick = onCyclePlayMode),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = playMode,
                transitionSpec = {
                    (scaleIn(initialScale = 0.7f, animationSpec = ConceptMotion.expressiveSpring()) + fadeIn(tween(140))) togetherWith
                        (scaleOut(targetScale = 0.7f) + fadeOut(tween(110)))
                },
                label = "landPlayModeMorph"
            ) { mode ->
                Canvas(modifier = Modifier.size(if (isTabletLandscape) 21.dp else 19.dp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = 2.0.dp.toPx()
                    val tint = Color.White.copy(alpha = 0.84f)
                    when (mode) {
                        PlayMode.REPEAT_ALL -> {
                            val pad = w * 0.14f
                            val rectSize = Size(w - pad * 2, h - pad * 2)
                            drawArc(tint, -155f, 130f, false, Offset(pad, pad), rectSize, style = Stroke(stroke, cap = StrokeCap.Round))
                            drawArc(tint, 25f, 130f, false, Offset(pad, pad), rectSize, style = Stroke(stroke, cap = StrokeCap.Round))
                            val topTip = Offset(w * 0.78f, h * 0.26f)
                            drawLine(tint, topTip, Offset(topTip.x - 3.4.dp.toPx(), topTip.y - 0.8.dp.toPx()), stroke, StrokeCap.Round)
                            drawLine(tint, topTip, Offset(topTip.x - 0.8.dp.toPx(), topTip.y + 3.4.dp.toPx()), stroke, StrokeCap.Round)
                            val botTip = Offset(w * 0.22f, h * 0.74f)
                            drawLine(tint, botTip, Offset(botTip.x + 3.4.dp.toPx(), botTip.y + 0.8.dp.toPx()), stroke, StrokeCap.Round)
                            drawLine(tint, botTip, Offset(botTip.x + 0.8.dp.toPx(), botTip.y - 3.4.dp.toPx()), stroke, StrokeCap.Round)
                        }
                        PlayMode.REPEAT_ONE -> {
                            val pad = w * 0.14f
                            val rectSize = Size(w - pad * 2, h - pad * 2)
                            drawArc(readableAccentPrimary, -155f, 130f, false, Offset(pad, pad), rectSize, style = Stroke(stroke, cap = StrokeCap.Round))
                            drawArc(readableAccentPrimary, 25f, 130f, false, Offset(pad, pad), rectSize, style = Stroke(stroke, cap = StrokeCap.Round))
                            drawLine(Color.White, Offset(w * 0.50f, h * 0.34f), Offset(w * 0.50f, h * 0.66f), stroke, StrokeCap.Round)
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
                            drawPath(path1, tint, style = Stroke(stroke, cap = StrokeCap.Round))
                            drawPath(path2, readableAccentPrimary, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }

        // 2. 上一首
        Box(
            modifier = Modifier
                .size(if (isTabletLandscape) 40.dp else 36.dp)
                .graphicsLayer { translationX = prevNudge.value }
                .softClickable(
                    pressScale = 0.84f,
                    onClick = {
                        scope.launch {
                            prevNudge.animateTo(-8f, tween(80))
                            prevNudge.animateTo(0f, ConceptMotion.expressiveSpring())
                        }
                        onPrevious()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(if (isTabletLandscape) 23.dp else 21.dp)) {
                val w = size.width
                val h = size.height
                val barW = w * 0.145f
                val barH = h * 0.60f
                val barTop = (h - barH) / 2f
                rotate(degrees = 180f, pivot = Offset(w / 2f, h / 2f)) {
                    val triPath = createPlumpPlayTrianglePath(w * 0.40f, h * 0.50f, min(w, h) * 0.66f)
                    drawPath(triPath, Color.White.copy(alpha = 0.92f))
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.92f),
                        topLeft = Offset(w * 0.72f, barTop),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
        }

        // 3. 中心播放/暂停按钮（连续几何插值形变）
        Box(
            modifier = Modifier
                .size(centerBtnSize)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            lerp(accentPrimary, Color.White, 0.12f),
                            accentPrimary,
                            lerp(accentPrimary, accentSecondary, 0.68f)
                        )
                    )
                )
                .softClickable(pressScale = 0.86f, onClick = onTogglePlay),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(if (isTabletLandscape) 20.dp else 17.dp)) {
                drawContinuousPlayPauseMorph(
                    morphProgress = morphProgressState.value,
                    color = Color.White
                )
            }
        }

        // 4. 下一首
        Box(
            modifier = Modifier
                .size(if (isTabletLandscape) 40.dp else 36.dp)
                .graphicsLayer { translationX = nextNudge.value }
                .softClickable(
                    pressScale = 0.84f,
                    onClick = {
                        scope.launch {
                            nextNudge.animateTo(8f, tween(80))
                            nextNudge.animateTo(0f, ConceptMotion.expressiveSpring())
                        }
                        onNext()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(if (isTabletLandscape) 23.dp else 21.dp)) {
                val w = size.width
                val h = size.height
                val barW = w * 0.145f
                val barH = h * 0.60f
                val barTop = (h - barH) / 2f
                val triPath = createPlumpPlayTrianglePath(w * 0.40f, h * 0.50f, min(w, h) * 0.66f)
                drawPath(triPath, Color.White.copy(alpha = 0.92f))
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.92f),
                    topLeft = Offset(w * 0.72f, barTop),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                )
            }
        }

        // 5. 播放队列
        Box(
            modifier = Modifier
                .size(if (isTabletLandscape) 38.dp else 34.dp)
                .softClickable(pressScale = 0.84f, onClick = onOpenQueue),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(if (isTabletLandscape) 21.dp else 19.dp)) {
                val w = size.width
                val h = size.height
                val stroke = 2.0.dp.toPx()
                val tint = Color.White.copy(alpha = 0.84f)
                drawLine(tint, Offset(w * 0.14f, h * 0.26f), Offset(w * 0.78f, h * 0.26f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.62f, h * 0.50f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.14f, h * 0.74f), Offset(w * 0.48f, h * 0.74f), stroke, StrokeCap.Round)
                val noteCenter = Offset(w * 0.72f, h * 0.74f)
                drawCircle(readableAccentPrimary, radius = 2.8.dp.toPx(), center = noteCenter)
                drawLine(readableAccentPrimary, Offset(noteCenter.x + 2.0.dp.toPx(), noteCenter.y), Offset(noteCenter.x + 2.0.dp.toPx(), h * 0.40f), stroke * 0.9f, StrokeCap.Round)
                drawLine(readableAccentPrimary, Offset(noteCenter.x + 2.0.dp.toPx(), h * 0.40f), Offset(w * 0.88f, h * 0.47f), stroke * 0.9f, StrokeCap.Round)
            }
        }
    }
}

/**
 * 自研横屏右上角「流光棱镜背景切换」无框矢量图标
 */
@Composable
private fun LandscapeBackgroundPrismCanvasIcon(
    accentColor: Color,
    secondaryColor: Color
) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val white = Color.White.copy(alpha = 0.88f)

        drawRoundRect(
            color = white,
            topLeft = Offset(w * 0.12f, h * 0.16f),
            size = Size(w * 0.76f, h * 0.68f),
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f),
            style = Stroke(width = stroke)
        )

        val wavePath = Path().apply {
            moveTo(w * 0.16f, h * 0.66f)
            cubicTo(
                w * 0.36f, h * 0.42f,
                w * 0.56f, h * 0.74f,
                w * 0.84f, h * 0.46f
            )
        }
        drawPath(
            path = wavePath,
            brush = Brush.horizontalGradient(listOf(accentColor, secondaryColor)),
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        drawCircle(
            color = accentColor,
            radius = w * 0.09f,
            center = Offset(w * 0.36f, h * 0.36f)
        )
    }
}

private fun formatPlayerDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
