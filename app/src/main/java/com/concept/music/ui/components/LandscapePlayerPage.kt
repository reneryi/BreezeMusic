package com.concept.music.ui.components

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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import android.os.Build
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.Song
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.LyricHighlightTheme
import com.concept.music.ui.viewmodel.PlayMode
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * 现代沉浸式横屏播放页面：
 * - 左侧（约 40% 宽度）：圆角方形封面 + 边缘呼吸弥散光晕 (Glowing Halo) + 精简歌曲信息与播放控制；
 * - 右侧（约 60% 宽度，比封面区域更大）：大画幅逐字流光歌词 + 译/音快捷开关与点击跳转。
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
    qualityLabel: String,
    lyricData: LyricData,
    currentLyricIndex: Int,
    isFavorite: Boolean,
    commentCount: Int,
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
    onOpenComments: () -> Unit,
    onToggleTranslation: () -> Unit,
    onTogglePhonetic: () -> Unit,
    onOpenLyricSettings: () -> Unit,
    onQualityClick: () -> Unit
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

    val infiniteTransition = rememberInfiniteTransition(label = "landscapeHalo")
    val haloBreath by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = if (isPlaying) 1.15f else 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloBreath"
    )
    val haloPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloPhase"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧区域 (weight 0.40f)：圆角方形边缘发光封面 + 歌曲信息 + 现代紧凑控播台
        Column(
            modifier = Modifier
                .weight(0.40f)
                .fillMaxHeight()
                .padding(end = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：退出横屏返回竖屏按钮 + 音质徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier.softClickable(onClick = onExitLandscape)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScreenLockPortrait,
                            contentDescription = "返回竖屏",
                            tint = Color.White.copy(alpha = 0.90f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "竖屏",
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val isGoldQuality = qualityLabel.startsWith("Hi-Res", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isGoldQuality) VipGold.copy(alpha = 0.18f) else accentPrimary.copy(alpha = 0.18f),
                    modifier = Modifier.softCardClickable(
                        shape = RoundedCornerShape(10.dp),
                        onClick = onQualityClick
                    )
                ) {
                    Text(
                        text = qualityLabel,
                        color = if (isGoldQuality) VipGold else accentPrimary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 中部：圆角方形封面 + 边缘散发光晕
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
                    val coverSide = minOf(maxWidth * 0.76f, maxHeight * 0.86f).coerceIn(120.dp, 220.dp)

                    // 边缘呼吸弥散光晕 (Glowing Edge Halo)
                    Canvas(
                        modifier = Modifier.size(coverSide * 1.42f)
                    ) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val baseR = (size.minDimension / 2f) * haloBreath
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.00f to accentPrimary.copy(alpha = 0.55f),
                                    0.45f to accentSecondary.copy(alpha = 0.34f),
                                    0.78f to accentPrimary.copy(alpha = 0.14f),
                                    1.00f to Color.Transparent
                                ),
                                center = Offset(
                                    center.x + 8f * kotlin.math.cos(haloPhase),
                                    center.y + 8f * sin(haloPhase)
                                ),
                                radius = baseR
                            ),
                            radius = baseR,
                            center = center
                        )
                    }

                    // 圆角方形封面本体
                    Box(
                        modifier = Modifier
                            .size(coverSide)
                            .shadow(
                                elevation = 28.dp,
                                shape = RoundedCornerShape(26.dp),
                                spotColor = accentPrimary,
                                ambientColor = accentSecondary
                            )
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF151A26)),
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

            // 底部：歌名歌手 + 进度条 + 播放控制栏
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = song.artist.ifBlank { "未知歌手" },
                            color = Color.White.copy(alpha = 0.66f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .softClickable(onClick = onToggleFavorite),
                            contentAlignment = Alignment.Center
                        ) {
                            PlumpHeartCanvasIcon(
                                isFavorite = isFavorite,
                                color = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        val formattedComment = formatPlayerCommentCount(commentCount)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.10f),
                            modifier = Modifier.softClickable(onClick = onOpenComments)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChatBubbleOutline,
                                    contentDescription = "评论",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(14.dp)
                                )
                                if (formattedComment.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = formattedComment,
                                        color = Color.White.copy(alpha = 0.88f),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(36.dp)) {
                        val modeIcon = when (playMode) {
                            PlayMode.REPEAT_ALL -> Icons.Rounded.Repeat
                            PlayMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
                            PlayMode.SHUFFLE -> Icons.Rounded.Shuffle
                        }
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = "播放模式",
                            tint = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "上一首",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = accentPrimary,
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .softClickable(onClick = onTogglePlay)
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
                                    drawRoundRect(Color.White, Offset(w * 0.18f, topY), Size(barW, barH), r)
                                    drawRoundRect(Color.White, Offset(w * 0.57f, topY), Size(barW, barH), r)
                                } else {
                                    val tri = createPlumpPlayTrianglePath(w * 0.54f, h * 0.50f, min(w, h) * 0.84f)
                                    drawPath(tri, Color.White)
                                }
                            }
                        }
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "下一首",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(onClick = onOpenQueue, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = "播放队列",
                            tint = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 右侧区域 (weight 0.60f，比左侧封面区域显著更大)：全画幅沉浸式歌词显示区
        Box(
            modifier = Modifier
                .weight(0.60f)
                .fillMaxHeight()
                .padding(start = 8.dp)
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
                            scrollOffset = -120
                        )
                    }
                }

                val supportsHardwareBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 44.dp),
                    contentPadding = PaddingValues(vertical = 110.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    itemsIndexed(lyricData.lines, key = { idx, item -> "land_${idx}_${item.startTimeMs}" }) { index, line ->
                        val isCurrent = index == currentLyricIndex
                        val distance = abs(index - currentLyricIndex)

                        val lineScale by animateFloatAsState(
                            targetValue = if (isCurrent) 1.04f else (1.0f - distance * 0.025f).coerceAtLeast(0.88f),
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
                                .softClickable(
                                    pressScale = 0.98f,
                                    onClick = {
                                        isUserScrubbing = false
                                        onSeekToLyricLine(index)
                                    }
                                )
                                .padding(vertical = (lyricLineSpacing * 0.85f).dp, horizontal = 6.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val phoneticText = line.phonetic
                                if (showPhonetic && showPhoneticButton && !phoneticText.isNullOrBlank()) {
                                    Text(
                                        text = phoneticText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isCurrent) activePrimaryColor.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.36f),
                                            fontSize = (lyricFontSize * lyricTranslationScale * 0.85f).coerceAtLeast(11f).sp,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
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
                                        distance == 1 -> 0.56f
                                        distance == 2 -> 0.34f
                                        else -> 0.16f
                                    }
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isCurrent) activePrimaryColor else Color.White.copy(alpha = distanceAlpha),
                                            fontSize = if (isCurrent) (lyricFontSize + 2f).sp else lyricFontSize.sp,
                                            lineHeight = (lyricFontSize + 8f).sp
                                        )
                                    )
                                }

                                val trans = line.translation
                                if (showTranslation && showTranslationButton && !trans.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = trans,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = if (isCurrent) activePrimaryColor.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.34f),
                                            fontSize = (lyricFontSize * lyricTranslationScale).coerceAtLeast(12f).sp,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 右侧边栏快捷胶囊：译 / 音 / 歌词设置
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp),
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

private fun formatPlayerDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
