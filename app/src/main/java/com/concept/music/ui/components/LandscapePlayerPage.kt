package com.concept.music.ui.components

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import com.concept.music.data.model.Song
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricHighlightTheme
import com.concept.music.ui.viewmodel.PlayMode
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 深度重构版全屏横屏播放页面 (LandscapePlayerPage)：
 * 1. 隐藏手机状态栏，全屏幕沉浸显示；
 * 2. 左上角改为极简返回图标按钮（移除原“竖屏”胶囊按钮）；
 * 3. 隐藏音质标签与评论按钮，保持界面纯粹聚焦；
 * 4. 封面区域与歌词区域严格五五分 (weight 0.50f : 0.50f)；
 * 5. 封面搭载多层 3D 立体交错光晕环绕效果 (Volumetric 3D Orbital Halo)；
 * 6. 底部五大播放控件与竖屏封面播放页完全统一 (AnimatedPlayModeButton / RedrawnSkipButton / AnimatedPlayPauseCenterButton / RedrawnQueueButton)；
 * 7. 右上角新增自研「流光棱镜背景切换」图标按钮，支持一键唤出背景动效面板或实时切换背景风格。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun LandscapePlayerPage(
    song: Song,
    isPlaying: Boolean,
    currentPositionMs: Long,
    rawPositionMs: Long,
    durationMs: Long,
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
    onSelectPlayerBackgroundStyle: (PlayerBackgroundStyle) -> Unit = {}
) {
    val context = LocalContext.current
    var showBgPickerMenu by remember { mutableStateOf(false) }

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

    val infiniteTransition = rememberInfiniteTransition(label = "landscape3dHalo")
    val haloBreath by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = if (isPlaying) 1.16f else 0.97f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloBreath"
    )
    val haloPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 11000 else 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloPhase"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 14.dp)
    ) {
        // 主体严格五五分：左侧封面与控播台 (0.50f) : 右侧沉浸式歌词 (0.50f)
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // =========================================================================
            // 左半区 (weight 0.50f)：左上角返回按钮 + 3D 立体光晕封面 + 统一控播台
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(0.50f)
                    .fillMaxHeight()
                    .padding(end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 左上角：纯图标返回按钮（不再显示“竖屏”文字胶囊，也不显示音质按钮）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.11f),
                        modifier = Modifier
                            .size(38.dp)
                            .softClickable(pressScale = 0.85f, onClick = onExitLandscape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 中部：封面 + 3D 立体星环光晕 (Volumetric 3D Halo)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    BoxWithConstraints(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val coverSide = minOf(maxWidth * 0.64f, maxHeight * 0.84f).coerceIn(132.dp, 216.dp)

                        // 多层 3D 立体光晕与交错倾斜星环绘制
                        Canvas(
                            modifier = Modifier.size(coverSide * 1.56f)
                        ) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val baseR = (size.minDimension / 2f) * 0.88f * haloBreath
                            val radPhase = (haloPhase * PI / 180.0).toFloat()

                            // 1. 底层远景深邃弥散光球
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.00f to accentPrimary.copy(alpha = 0.62f),
                                        0.42f to accentSecondary.copy(alpha = 0.36f),
                                        0.76f to accentPrimary.copy(alpha = 0.15f),
                                        1.00f to Color.Transparent
                                    ),
                                    center = Offset(
                                        center.x + 12f * cos(radPhase),
                                        center.y + 10f * sin(radPhase)
                                    ),
                                    radius = baseR * 1.12f
                                ),
                                radius = baseR * 1.12f,
                                center = center
                            )

                            // 2. 第一层 3D 倾斜透视星环光带 (-22度倾角)
                            rotate(degrees = -22f + haloPhase * 0.15f, pivot = center) {
                                val ovalW = baseR * 1.92f
                                val ovalH = baseR * 0.86f
                                drawOval(
                                    brush = Brush.sweepGradient(
                                        0.00f to accentPrimary.copy(alpha = 0.05f),
                                        0.25f to accentSecondary.copy(alpha = 0.52f),
                                        0.50f to Color.White.copy(alpha = 0.38f),
                                        0.75f to accentPrimary.copy(alpha = 0.45f),
                                        1.00f to accentPrimary.copy(alpha = 0.05f),
                                        center = center
                                    ),
                                    topLeft = Offset(center.x - ovalW / 2f, center.y - ovalH / 2f),
                                    size = Size(ovalW, ovalH),
                                    style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            // 3. 第二层反向交错 3D 立体光环 (+28度倾角)
                            rotate(degrees = 28f - haloPhase * 0.22f, pivot = center) {
                                val ovalW = baseR * 1.78f
                                val ovalH = baseR * 0.94f
                                drawOval(
                                    brush = Brush.sweepGradient(
                                        0.00f to accentSecondary.copy(alpha = 0.04f),
                                        0.35f to accentPrimary.copy(alpha = 0.46f),
                                        0.70f to accentSecondary.copy(alpha = 0.32f),
                                        1.00f to accentSecondary.copy(alpha = 0.04f),
                                        center = center
                                    ),
                                    topLeft = Offset(center.x - ovalW / 2f, center.y - ovalH / 2f),
                                    size = Size(ovalW, ovalH),
                                    style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            // 4. 环绕漂浮高光星点
                            for (i in 0 until 4) {
                                val angle = radPhase + i * (PI.toFloat() / 2f)
                                val orbitRx = baseR * 0.92f
                                val orbitRy = baseR * 0.58f
                                val px = center.x + orbitRx * cos(angle)
                                val py = center.y + orbitRy * sin(angle)
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.72f),
                                    radius = 2.6.dp.toPx(),
                                    center = Offset(px, py)
                                )
                                drawCircle(
                                    color = accentSecondary.copy(alpha = 0.28f),
                                    radius = 6.5.dp.toPx(),
                                    center = Offset(px, py)
                                )
                            }
                        }

                        // 封面本体（立体浮雕阴影 + 内发光高光描边）
                        Box(
                            modifier = Modifier
                                .size(coverSide)
                                .graphicsLayer {
                                    val tilt = sin((haloPhase * PI / 180.0).toFloat()) * 2.2f
                                    rotationY = tilt
                                    rotationX = -tilt * 0.6f
                                    cameraDistance = 14f * density
                                }
                                .shadow(
                                    elevation = 32.dp,
                                    shape = RoundedCornerShape(28.dp),
                                    spotColor = accentPrimary,
                                    ambientColor = accentSecondary
                                )
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color(0xFF151A26))
                                .border(
                                    width = 1.2.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.42f),
                                            accentPrimary.copy(alpha = 0.35f),
                                            accentSecondary.copy(alpha = 0.25f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                ),
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
                        }
                    }
                }

                // 底部：歌名歌手 + 红心收藏（无评论按钮）+ 彗星进度条 + 统一五大播放控件
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Text(
                                text = song.artist.ifBlank { "未知歌手" },
                                color = Color.White.copy(alpha = 0.68f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 仅保留红心收藏按钮（评论按钮在横屏下不显示）
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .softClickable(pressScale = 0.84f, onClick = onToggleFavorite),
                            contentAlignment = Alignment.Center
                        ) {
                            PlumpHeartCanvasIcon(
                                isFavorite = isFavorite,
                                color = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    var isInteractingWithBar by remember { mutableStateOf(false) }
                    var dragFraction by remember { mutableFloatStateOf(0f) }
                    val currentFraction = if (durationMs > 0) {
                        (rawPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val effectiveFraction = if (isInteractingWithBar) dragFraction else currentFraction
                    val displayCurrentMs = if (isInteractingWithBar) (dragFraction * durationMs).toLong() else rawPositionMs

                    CometTailProgressBar(
                        fraction = effectiveFraction,
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
                        onDragStart = {
                            isInteractingWithBar = true
                            dragFraction = it
                        },
                        onDrag = { dragFraction = it },
                        onDragEnd = {
                            onSeekTo((dragFraction * durationMs).toLong())
                            isInteractingWithBar = false
                        }
                    )

                    // 与封面播放页完全统一的五大圆润饱满矢量控件
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
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
                            onClick = onOpenQueue
                        )
                    }
                }
            }

            // =========================================================================
            // 右半区 (weight 0.50f，与左侧严格五五分)：全画幅逐字流光歌词 + 右上角背景切换按钮
            // =========================================================================
            Box(
                modifier = Modifier
                    .weight(0.50f)
                    .fillMaxHeight()
                    .padding(start = 12.dp)
            ) {
                if (lyricData.lines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "纯音乐或暂无歌词",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 15.sp,
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
                    LaunchedEffect(currentLyricIndex, isUserScrubbing) {
                        if (!isUserScrubbing && currentLyricIndex in lyricData.lines.indices) {
                            listState.animateScrollToItem(
                                index = currentLyricIndex,
                                scrollOffset = -110
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

                    val supportsHardwareBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 46.dp),
                        contentPadding = PaddingValues(vertical = 110.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        itemsIndexed(lyricData.lines, key = { idx, item -> "land_${idx}_${item.startTimeMs}" }) { index, line ->
                            val isCurrent = index == currentLyricIndex
                            val isScrubTarget = isUserScrubbing && index == scrubLineIndex
                            val distance = abs(index - currentLyricIndex)

                            val lineScale by animateFloatAsState(
                                targetValue = when {
                                    isCurrent -> 1.04f
                                    isScrubTarget -> 1.0f
                                    else -> (1.0f - distance * 0.025f).coerceAtLeast(0.88f)
                                },
                                animationSpec = tween(durationMillis = 250),
                                label = "landLyricScale"
                            )
                            val depthBlurVal = if (lyricBlurEnabled && !isUserScrubbing && !isCurrent) {
                                when (distance) {
                                    1 -> 0.7f * lyricBlurIntensity
                                    2 -> 1.4f * lyricBlurIntensity
                                    else -> 2.4f * lyricBlurIntensity
                                }.coerceIn(0f, 5f)
                            } else 0f

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = lineScale
                                        scaleY = lineScale
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                    }
                                    .then(
                                        if (supportsHardwareBlur && depthBlurVal >= 0.5f) {
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
                                    val phoneticText = line.phonetic
                                    if (showPhonetic && showPhoneticButton && !phoneticText.isNullOrBlank()) {
                                        val phoneticFontSize = (lyricFontSize * lyricTranslationScale * 0.85f).coerceAtLeast(11.5f)
                                        Text(
                                            text = phoneticText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isCurrent) activePrimaryColor.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.38f),
                                                fontSize = phoneticFontSize.sp,
                                                lineHeight = (phoneticFontSize * 1.38f).sp,
                                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                    }

                                    if (isCurrent && line.hasWordTiming && lyricWordByWord) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.Start,
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
                                                Text(
                                                    text = annotatedWord,
                                                    style = MaterialTheme.typography.headlineSmall.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = (lyricFontSize + 2.5f).sp,
                                                        lineHeight = (lyricFontSize + 9f).sp
                                                    ),
                                                    modifier = Modifier.graphicsLayer {
                                                        translationY = -2.5f * wave
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        val distanceAlpha = when {
                                            isCurrent -> 1.0f
                                            isScrubTarget -> 0.92f
                                            distance == 1 -> 0.56f
                                            distance == 2 -> 0.34f
                                            else -> 0.16f
                                        }
                                        Text(
                                            text = line.text,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = if (isCurrent || isScrubTarget) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                color = if (isCurrent) activePrimaryColor else Color.White.copy(alpha = distanceAlpha),
                                                fontSize = if (isCurrent) (lyricFontSize + 2f).sp else lyricFontSize.sp,
                                                lineHeight = (lyricFontSize + 8f).sp
                                            )
                                        )
                                    }

                                    val trans = line.translation
                                    if (showTranslation && showTranslationButton && !trans.isNullOrBlank()) {
                                        val transFontSize = (lyricFontSize * lyricTranslationScale).coerceAtLeast(13f)
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Text(
                                            text = trans,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (isCurrent) activePrimaryColor.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.38f),
                                                fontSize = transFontSize.sp,
                                                lineHeight = (transFontSize * 1.42f).sp,
                                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 横屏滑动歌词时的双翼渐隐中空导引线（不遮挡歌词）
                    AnimatedVisibility(
                        visible = isUserScrubbing && scrubLineIndex in lyricData.lines.indices,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(end = 46.dp)
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
                                    color = Color(0xD90E1624),
                                    modifier = Modifier.border(0.8.dp, activePrimaryColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
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
                                    color = activePrimaryColor.copy(alpha = 0.24f),
                                    modifier = Modifier
                                        .border(0.8.dp, activePrimaryColor.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
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

                // 右上角：新增自研「修改背景界面」图标按钮 + 弹出式背景切换胶囊条
                Column(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalAlignment = Alignment.End
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (showBgPickerMenu) accentPrimary.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.11f),
                        modifier = Modifier
                            .size(38.dp)
                            .softClickable(
                                pressScale = 0.85f,
                                onClick = { showBgPickerMenu = !showBgPickerMenu }
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            LandscapeBackgroundPrismCanvasIcon(
                                accentColor = accentPrimary,
                                secondaryColor = accentSecondary
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showBgPickerMenu,
                        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.90f),
                        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.90f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xEB0E1524),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
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
                                        color = if (selected) accentPrimary.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.06f),
                                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(10.dp)) {
                                            onSelectPlayerBackgroundStyle(bgStyle)
                                        }
                                    ) {
                                        Text(
                                            text = bgStyle.label,
                                            color = if (selected) accentPrimary else Color.White.copy(alpha = 0.82f),
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

                // 右下角：译 / 音 / 歌词设置
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = showTranslationButton,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (showTranslation) activePrimaryColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
                            modifier = Modifier
                                .size(38.dp)
                                .softClickable(onClick = onToggleTranslation)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "译",
                                    color = if (showTranslation) Color.White else Color.White.copy(alpha = 0.72f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showPhoneticButton,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (showPhonetic) activePrimaryColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
                            modifier = Modifier
                                .size(38.dp)
                                .softClickable(onClick = onTogglePhonetic)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "音",
                                    color = if (showPhonetic) Color.White else Color.White.copy(alpha = 0.72f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        modifier = Modifier
                            .size(38.dp)
                            .softClickable(onClick = onOpenLyricSettings)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "歌词设置",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 自研横屏右上角「流光棱镜背景切换」矢量图标
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
        val white = Color.White.copy(alpha = 0.90f)

        // 外框画布层
        drawRoundRect(
            color = white,
            topLeft = Offset(w * 0.12f, h * 0.16f),
            size = Size(w * 0.76f, h * 0.68f),
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f),
            style = Stroke(width = stroke)
        )

        // 内部流光山峦/极光波浪曲线
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

        // 右上角星芒圆点
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
