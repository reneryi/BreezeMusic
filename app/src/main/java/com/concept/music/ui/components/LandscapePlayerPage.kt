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
import androidx.compose.foundation.gestures.animateScrollBy
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
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.Song
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptMotion
import com.concept.music.ui.theme.ConceptPrimary
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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 现代沉浸式全屏横屏播放页面 (LandscapePlayerPage)：
 * 1. 界面绝对视觉重心聚焦于「大画幅 3D 专辑封面」与「全高 KRC 逐字流光歌词」；
 * 2. 引入 `lyricPositionProvider` 与 `rawPositionProvider` 隔离 120fps 高频采样，外层双栏 0 次重组；
 * 3. KRC 歌词列表采用统一字号 + GPU `graphicsLayer` 弹性缩放（0 文字重排）与黄金焦点平滑滚动；
 * 4. 暂停态自动休眠 3D 星环无限动画，播放/暂停按钮采用连续几何形变。
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

    // 暂停态自动休眠 3D 星环无限动画，杜绝静态空转耗电
    val haloBreath: Float
    val haloPhase: Float
    if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "landscape3dHalo")
        val animBreath by infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "haloBreath"
        )
        val animPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 11000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "haloPhase"
        )
        haloBreath = animBreath
        haloPhase = animPhase
    } else {
        haloBreath = 0.96f
        haloPhase = 24f
    }

    val coverPlayScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.91f,
        animationSpec = ConceptMotion.expressiveSpring(),
        label = "landscapeCoverPlayScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
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

                        Canvas(
                            modifier = Modifier.size(coverSide * 1.42f)
                        ) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val baseR = (size.minDimension / 2f) * 0.88f * haloBreath
                            val radPhase = (haloPhase * PI / 180.0).toFloat()

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

                        Box(
                            modifier = Modifier
                                .size(coverSide)
                                .graphicsLayer {
                                    val tilt = sin((haloPhase * PI / 180.0).toFloat()) * 2.0f
                                    rotationY = tilt
                                    rotationX = -tilt * 0.55f
                                    scaleX = coverPlayScale
                                    scaleY = coverPlayScale
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

                // 底部紧凑一体化控播坞（独立隔离高频进度刷新）
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
                    onOpenQueue = onOpenQueue
                )
            }

            // =========================================================================
            // 右区 (weight 0.55f)：全高沉浸式 KRC 逐字流光歌词（零重排 GPU 缩放 + 黄金焦点平滑滚动）
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
                            val layoutInfo = listState.layoutInfo
                            val viewportH = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).takeIf { it > 0 } ?: 420
                            val goldenFocusOffset = -(viewportH * 0.28f).toInt()
                            val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentLyricIndex }
                            if (targetItem != null) {
                                val deltaPx = (targetItem.offset - goldenFocusOffset).toFloat()
                                if (abs(deltaPx) > 2f) {
                                    listState.animateScrollBy(
                                        value = deltaPx,
                                        animationSpec = tween(durationMillis = 360, easing = ConceptMotion.LyricSmoothScrollEasing)
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

                    val supportsHardwareBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    val isAutoScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

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

                            // 统一使用 GPU 弹性缩放表达主次焦点，彻底消除字号跳变引发的 Text Relayout
                            val lineScaleState = animateFloatAsState(
                                targetValue = when {
                                    isCurrent -> 1.05f
                                    isScrubTarget -> 1.0f
                                    else -> (0.93f - distance * 0.022f).coerceAtLeast(0.86f)
                                },
                                animationSpec = ConceptMotion.spatialPageSpring(),
                                label = "landLyricScale"
                            )
                            val depthBlurVal = if (lyricBlurEnabled && !isUserScrubbing && !isAutoScrolling && !isCurrent) {
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
                                        val s = lineScaleState.value
                                        scaleX = s
                                        scaleY = s
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
                                            lyricFontSize = lyricFontSize,
                                            activePrimaryColor = activePrimaryColor,
                                            activeSecondaryColor = activeSecondaryColor
                                        )
                                    } else {
                                        val distanceAlpha = when {
                                            isCurrent -> 1.0f
                                            isScrubTarget -> 0.92f
                                            distance == 1 -> 0.56f
                                            distance == 2 -> 0.34f
                                            else -> 0.16f
                                        }
                                        // 固定基础字号与字重，配合外层 graphicsLayer 缩放实现 0 Relayout 丝滑过渡
                                        Text(
                                            text = line.text,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isCurrent) activePrimaryColor else Color.White.copy(alpha = distanceAlpha),
                                                fontSize = lyricFontSize.sp,
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
                                                fontWeight = FontWeight.SemiBold
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
        // 悬浮无框沉浸式四角按钮层
        // =========================================================================
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
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.90f, animationSpec = ConceptMotion.expressiveSpring()),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.90f, animationSpec = ConceptMotion.spatialSheetSpring())
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
 * 横屏激活行局部 120fps 逐字流光渲染器（仅重绘当前激活行，不触发 LazyColumn 兄弟行重组）
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
    var sampledPosMs by remember(line) { mutableLongStateOf(lyricPositionProvider()) }
    LaunchedEffect(line, isPlaying) {
        sampledPosMs = lyricPositionProvider()
        if (isPlaying) {
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
            val isLongNote = word.durationMs >= 750L && isSinging
            Text(
                text = annotatedWord,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = lyricFontSize.sp,
                    lineHeight = (lyricFontSize + 8f).sp,
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
}

/**
 * 横屏左下角紧凑控播坞（内部局部帧采样进度，隔离对封面区的重组影响）
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
    onToggleFavorite: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit
) {
    var isInteractingWithBar by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var livePosMs by remember(song.id) { mutableLongStateOf(rawPositionProvider().takeIf { it > 0L } ?: rawPositionMs) }

    LaunchedEffect(song.id, isPlaying, isInteractingWithBar, rawPositionMs) {
        livePosMs = rawPositionProvider().takeIf { it > 0L } ?: rawPositionMs
        if (isPlaying && !isInteractingWithBar) {
            while (isActive) {
                withFrameMillis {
                    livePosMs = rawPositionProvider()
                }
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

    Column(
        modifier = Modifier
            .widthIn(max = 316.dp)
            .fillMaxWidth(0.94f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                        color = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(17.dp)
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

        LandscapeCompactControlRow(
            playMode = playMode,
            isPlaying = isPlaying,
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
 * 横屏专属紧凑低重心播放控制栏（总高 38.dp，采用连续几何插值播放/暂停形变）
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
    val morphProgressState = animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = ConceptMotion.expressiveSpring(),
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
                    (scaleIn(initialScale = 0.7f, animationSpec = ConceptMotion.expressiveSpring()) + fadeIn(tween(140))) togetherWith
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
                            prevNudge.animateTo(0f, ConceptMotion.expressiveSpring())
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

        // 3. 紧凑中心播放/暂停按钮（连续几何插值形变）
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
            Canvas(modifier = Modifier.size(17.dp)) {
                drawContinuousPlayPauseMorph(
                    morphProgress = morphProgressState.value,
                    color = Color.White
                )
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
                            nextNudge.animateTo(0f, ConceptMotion.expressiveSpring())
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
