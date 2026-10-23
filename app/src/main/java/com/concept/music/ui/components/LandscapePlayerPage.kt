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
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 现代沉浸式全屏横屏播放页面 (LandscapePlayerPage)：
 * 1. 界面绝对视觉重心聚焦于「大画幅 3D 专辑封面」与「全高 KRC 逐字流光歌词」；
 * 2. 左上角返回、右上角背景切换、右下角译/音/歌词设置全部采用「无框沉浸式图标」，彻底去除外层按钮框；
 * 3. 大幅压缩底部进度条与播放控件占用高度，整合为紧凑精巧的低重心控播坞，释放封面纵向空间。
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
        initialValue = 0.94f,
        targetValue = if (isPlaying) 1.12f else 0.98f,
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
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        // 主体黄金比例双栏：左区大画幅封面与紧凑控播坞 (0.45f) : 右区全高沉浸式流光歌词 (0.55f)
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // =========================================================================
            // 左区 (weight 0.45f)：大尺寸主角封面 + 极简紧凑一体化控播坞
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .padding(end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 上部大舞台：放大封面占比，使其成为左侧核心视觉焦点
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BoxWithConstraints(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val coverSide = minOf(maxWidth * 0.78f, maxHeight * 0.90f).coerceIn(168.dp, 264.dp)

                        // 多层 3D 立体光晕与交错倾斜星环绘制
                        Canvas(
                            modifier = Modifier.size(coverSide * 1.42f)
                        ) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val baseR = (size.minDimension / 2f) * 0.88f * haloBreath
                            val radPhase = (haloPhase * PI / 180.0).toFloat()

                            // 1. 底层远景深邃弥散光球
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.00f to accentPrimary.copy(alpha = 0.56f),
                                        0.42f to accentSecondary.copy(alpha = 0.32f),
                                        0.76f to accentPrimary.copy(alpha = 0.12f),
                                        1.00f to Color.Transparent
                                    ),
                                    center = Offset(
                                        center.x + 10f * cos(radPhase),
                                        center.y + 8f * sin(radPhase)
                                    ),
                                    radius = baseR * 1.10f
                                ),
                                radius = baseR * 1.10f,
                                center = center
                            )

                            // 2. 第一层 3D 倾斜透视星环光带 (-22度倾角)
                            rotate(degrees = -22f + haloPhase * 0.15f, pivot = center) {
                                val ovalW = baseR * 1.86f
                                val ovalH = baseR * 0.84f
                                drawOval(
                                    brush = Brush.sweepGradient(
                                        0.00f to accentPrimary.copy(alpha = 0.04f),
                                        0.25f to accentSecondary.copy(alpha = 0.46f),
                                        0.50f to Color.White.copy(alpha = 0.34f),
                                        0.75f to accentPrimary.copy(alpha = 0.40f),
                                        1.00f to accentPrimary.copy(alpha = 0.04f),
                                        center = center
                                    ),
                                    topLeft = Offset(center.x - ovalW / 2f, center.y - ovalH / 2f),
                                    size = Size(ovalW, ovalH),
                                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            // 3. 第二层反向交错 3D 立体光环 (+28度倾角)
                            rotate(degrees = 28f - haloPhase * 0.22f, pivot = center) {
                                val ovalW = baseR * 1.72f
                                val ovalH = baseR * 0.90f
                                drawOval(
                                    brush = Brush.sweepGradient(
                                        0.00f to accentSecondary.copy(alpha = 0.04f),
                                        0.35f to accentPrimary.copy(alpha = 0.40f),
                                        0.70f to accentSecondary.copy(alpha = 0.28f),
                                        1.00f to accentSecondary.copy(alpha = 0.04f),
                                        center = center
                                    ),
                                    topLeft = Offset(center.x - ovalW / 2f, center.y - ovalH / 2f),
                                    size = Size(ovalW, ovalH),
                                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            // 4. 环绕漂浮高光星点
                            for (i in 0 until 4) {
                                val angle = radPhase + i * (PI.toFloat() / 2f)
                                val orbitRx = baseR * 0.90f
                                val orbitRy = baseR * 0.56f
                                val px = center.x + orbitRx * cos(angle)
                                val py = center.y + orbitRy * sin(angle)
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.68f),
                                    radius = 2.2.dp.toPx(),
                                    center = Offset(px, py)
                                )
                                drawCircle(
                                    color = accentSecondary.copy(alpha = 0.24f),
                                    radius = 5.5.dp.toPx(),
                                    center = Offset(px, py)
                                )
                            }
                        }

                        // 封面本体（大尺寸立体浮雕阴影 + 细腻高光描边）
                        Box(
                            modifier = Modifier
                                .size(coverSide)
                                .graphicsLayer {
                                    val tilt = sin((haloPhase * PI / 180.0).toFloat()) * 2.0f
                                    rotationY = tilt
                                    rotationX = -tilt * 0.55f
                                    cameraDistance = 14f * density
                                }
                                .shadow(
                                    elevation = 30.dp,
                                    shape = RoundedCornerShape(28.dp),
                                    spotColor = accentPrimary,
                                    ambientColor = accentSecondary
                                )
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color(0xFF151A26))
                                .border(
                                    width = 1.1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.40f),
                                            accentPrimary.copy(alpha = 0.32f),
                                            accentSecondary.copy(alpha = 0.22f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Crossfade(
                                targetState = song.coverUrl,
                                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
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

                // 底部紧凑一体化控播坞：大幅压缩进度条与按钮纵向高度，突出上方大封面
                var isInteractingWithBar by remember { mutableStateOf(false) }
                var dragFraction by remember { mutableFloatStateOf(0f) }
                val currentFraction = if (durationMs > 0) {
                    (rawPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val targetFraction = if (isInteractingWithBar) dragFraction else currentFraction
                val effectiveFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = if (isInteractingWithBar) {
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                    } else {
                        tween(durationMillis = 140, easing = LinearEasing)
                    },
                    label = "landscapeProgressSmoothFraction"
                )
                val displayCurrentMs = if (isInteractingWithBar) (dragFraction * durationMs).toLong() else rawPositionMs

                Column(
                    modifier = Modifier
                        .widthIn(max = 316.dp)
                        .fillMaxWidth(0.94f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 第 1 行：歌名 + 歌手 + 内联时间显示 + 无框沉浸式红心
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .basicMarquee()
                            )
                            Text(
                                text = "  ·  ${song.artist.ifBlank { "未知歌手" }}",
                                color = Color.White.copy(alpha = 0.64f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${formatPlayerDuration(displayCurrentMs)} / ${formatPlayerDuration(durationMs)}",
                                color = if (isInteractingWithBar) accentPrimary else Color.White.copy(alpha = 0.52f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .softClickable(pressScale = 0.82f, onClick = onToggleFavorite),
                                contentAlignment = Alignment.Center
                            ) {
                                PlumpHeartCanvasIcon(
                                    isFavorite = isFavorite,
                                    color = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.82f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    // 第 2 行：紧凑纤细光学彗星进度条 (compact = true，高度仅 20.dp)
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
                        compact = true,
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

                    // 第 3 行：精致紧凑型五大播放控制按钮（总高仅 38.dp，告别巨型播放键抢占封面空间）
                    LandscapeCompactControlRow(
                        playMode = playMode,
                        isPlaying = isPlaying,
                        accentPrimary = accentPrimary,
                        accentSecondary = accentSecondary,
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
                        onOpenQueue = onOpenQueue
                    )
                }
            }

            // =========================================================================
            // 右区 (weight 0.55f)：全高沉浸式 KRC 逐字流光歌词
            // =========================================================================
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .padding(start = 10.dp)
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
                                scrollOffset = -115
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
                            .padding(end = 36.dp),
                        contentPadding = PaddingValues(vertical = 120.dp),
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
                                                val isLongNote = word.durationMs >= 750L && isSinging
                                                Text(
                                                    text = annotatedWord,
                                                    style = MaterialTheme.typography.headlineSmall.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = (lyricFontSize + 2.5f).sp,
                                                        lineHeight = (lyricFontSize + 9.5f).sp,
                                                        shadow = if (isLongNote) {
                                                            Shadow(
                                                                color = activePrimaryColor.copy(alpha = (0.80f * wave).coerceIn(0f, 1f)),
                                                                offset = Offset.Zero,
                                                                blurRadius = (20f * wave + 6f).coerceAtLeast(1f)
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
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isUserScrubbing && scrubLineIndex in lyricData.lines.indices,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(end = 36.dp)
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
            }
        }

        // =========================================================================
        // 悬浮无框沉浸式四角按钮层（彻底去除按钮框，不占用主布局高度）
        // =========================================================================

        // 1. 左上角：无框沉浸式返回按钮
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(34.dp)
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

        // 2. 右上角：无框沉浸式背景修改按钮 + 弹出式背景切换菜单
        Column(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .softClickable(
                        pressScale = 0.82f,
                        onClick = { showBgPickerMenu = !showBgPickerMenu }
                    ),
                contentAlignment = Alignment.Center
            ) {
                LandscapeBackgroundPrismCanvasIcon(
                    accentColor = if (showBgPickerMenu) accentPrimary else Color.White.copy(alpha = 0.86f),
                    secondaryColor = accentSecondary
                )
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
                        .padding(top = 6.dp)
                        .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
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

        // 3. 右下角：无框沉浸式「译 / 音 / 歌词设置」按钮组
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = showTranslationButton,
                enter = scaleIn() + fadeIn(),
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
                enter = scaleIn() + fadeIn(),
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

            // 无框沉浸式歌词设置图标按钮
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
 * 横屏专属紧凑低重心播放控制栏（总高 38.dp，释放左侧封面空间）
 */
@Composable
private fun LandscapeCompactControlRow(
    playMode: PlayMode,
    isPlaying: Boolean,
    accentPrimary: Color,
    accentSecondary: Color,
    onCyclePlayMode: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val prevNudge = remember { Animatable(0f) }
    val nextNudge = remember { Animatable(0f) }
    val morphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "landPlayMorph"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 播放模式
        Box(
            modifier = Modifier
                .size(34.dp)
                .softClickable(pressScale = 0.84f, onClick = onCyclePlayMode),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = playMode,
                transitionSpec = {
                    (scaleIn(initialScale = 0.7f) + fadeIn(tween(140))) togetherWith
                        (scaleOut(targetScale = 0.7f) + fadeOut(tween(110)))
                },
                label = "landPlayModeMorph"
            ) { mode ->
                Canvas(modifier = Modifier.size(19.dp)) {
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
                            drawArc(accentPrimary, -155f, 130f, false, Offset(pad, pad), rectSize, style = Stroke(stroke, cap = StrokeCap.Round))
                            drawArc(accentPrimary, 25f, 130f, false, Offset(pad, pad), rectSize, style = Stroke(stroke, cap = StrokeCap.Round))
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
                            drawPath(path2, accentPrimary, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }

        // 2. 上一首
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer { translationX = prevNudge.value }
                .softClickable(
                    pressScale = 0.84f,
                    onClick = {
                        scope.launch {
                            prevNudge.animateTo(-8f, tween(80))
                            prevNudge.animateTo(0f, spring(dampingRatio = 0.55f))
                        }
                        onPrevious()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(21.dp)) {
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

        // 3. 紧凑中心播放/暂停按钮 (38.dp 精致胶囊圆钮)
        Box(
            modifier = Modifier
                .size(38.dp)
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
            val safeMorph = morphProgress.coerceIn(0f, 1f)
            Canvas(modifier = Modifier.size(17.dp)) {
                val w = size.width
                val h = size.height
                if (safeMorph > 0.5f) {
                    val barW = w * 0.24f
                    val barH = h * 0.72f
                    val topY = (h - barH) / 2f
                    val leftX = w * 0.18f
                    val rightX = w - leftX - barW
                    val r = CornerRadius(barW / 2f, barW / 2f)
                    drawRoundRect(Color.White, Offset(leftX, topY), Size(barW, barH), r)
                    drawRoundRect(Color.White, Offset(rightX, topY), Size(barW, barH), r)
                } else {
                    val triPath = createPlumpPlayTrianglePath(w * 0.54f, h * 0.50f, min(w, h) * 0.84f)
                    drawPath(triPath, Color.White)
                }
            }
        }

        // 4. 下一首
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer { translationX = nextNudge.value }
                .softClickable(
                    pressScale = 0.84f,
                    onClick = {
                        scope.launch {
                            nextNudge.animateTo(8f, tween(80))
                            nextNudge.animateTo(0f, spring(dampingRatio = 0.55f))
                        }
                        onNext()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(21.dp)) {
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
                .size(34.dp)
                .softClickable(pressScale = 0.84f, onClick = onOpenQueue),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(19.dp)) {
                val w = size.width
                val h = size.height
                val stroke = 2.0.dp.toPx()
                val tint = Color.White.copy(alpha = 0.84f)
                drawLine(tint, Offset(w * 0.14f, h * 0.26f), Offset(w * 0.78f, h * 0.26f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.62f, h * 0.50f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.14f, h * 0.74f), Offset(w * 0.48f, h * 0.74f), stroke, StrokeCap.Round)
                val noteCenter = Offset(w * 0.72f, h * 0.74f)
                drawCircle(accentPrimary, radius = 2.8.dp.toPx(), center = noteCenter)
                drawLine(accentPrimary, Offset(noteCenter.x + 2.0.dp.toPx(), noteCenter.y), Offset(noteCenter.x + 2.0.dp.toPx(), h * 0.40f), stroke * 0.9f, StrokeCap.Round)
                drawLine(accentPrimary, Offset(noteCenter.x + 2.0.dp.toPx(), h * 0.40f), Offset(w * 0.88f, h * 0.47f), stroke * 0.9f, StrokeCap.Round)
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
