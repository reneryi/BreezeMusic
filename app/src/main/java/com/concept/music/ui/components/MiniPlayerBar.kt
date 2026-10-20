package com.concept.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.Song
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * 独立圆角长条形上层悬浮播放控制条（Floating Capsule MiniPlayer）：
 * 采用圆润饱满重绘播放/暂停与播放队列矢量图标，保持全应用视觉风格高度统一。
 */
@Composable
fun MiniPlayerBar(
    currentSong: Song?,
    isPlaying: Boolean,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    onTogglePlay: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit,
    onBarClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentSong == null) return

    val isDark = LocalIsDarkTheme.current
    val capsuleShape = RoundedCornerShape(29.dp)
    val mainCapsuleBrush = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                Color(0xF2162236),
                Color(0xF2131C2D),
                Color(0xF2172338)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFAFFFFFF),
                Color(0xF8F4F9FF),
                Color(0xFAFFFFFF)
            )
        )
    }

    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val goldColor = if (isDark) VipGold else Color(0xFFC47F00)

    val scope = rememberCoroutineScope()
    val rotationAnim = remember { Animatable(0f) }
    val dragOffsetX = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                rotationAnim.animateTo(
                    targetValue = rotationAnim.value + 360f,
                    animationSpec = tween(durationMillis = 16000, easing = LinearEasing)
                )
            }
        }
    }

    val progressFraction = if (durationMs > 0L) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val swipeThreshold = 82f
    val currentDrag = dragOffsetX.value
    val isSwipingNext = currentDrag < -28f
    val isSwipingPrev = currentDrag > 28f
    val willTriggerSwitch = abs(currentDrag) >= swipeThreshold

    val barScale by animateFloatAsState(
        targetValue = if (willTriggerSwitch) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "miniBarSwipeScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .graphicsLayer {
                scaleX = barScale
                scaleY = barScale
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = capsuleShape,
            color = Color.Transparent,
            shadowElevation = if (isDark) 14.dp else 7.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .pointerInput(currentSong.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val finalOffset = dragOffsetX.value
                            when {
                                finalOffset <= -swipeThreshold -> onNextClick()
                                finalOffset >= swipeThreshold -> onPreviousClick()
                            }
                            scope.launch {
                                dragOffsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                dragOffsetX.animateTo(0f, spring())
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                dragOffsetX.snapTo((dragOffsetX.value + dragAmount).coerceIn(-210f, 210f))
                            }
                        }
                    )
                }
                .softCardClickable(
                    shape = capsuleShape,
                    clip = false,
                    pressScale = 0.985f,
                    pressAlpha = 0.95f,
                    onClick = onBarClick
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(mainCapsuleBrush)
                    .padding(start = 9.dp, end = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            translationX = currentDrag * 0.42f
                            alpha = (1f - (abs(currentDrag) / 340f)).coerceIn(0.35f, 1f)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(4.dp, CircleShape, spotColor = ConceptPrimary.copy(alpha = 0.30f))
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF2B3242), Color(0xFF121620))
                                )
                            )
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = rotationAnim.value % 360f
                                    shape = CircleShape
                                    clip = true
                                }
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentSong.coverUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = currentSong.coverUrl,
                                    contentDescription = currentSong.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = ConceptPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF141C2E) else Color.White)
                        )
                    }

                    Spacer(modifier = Modifier.width(11.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentSong.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryTextColor,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            when {
                                currentSong.isHiRes -> {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    BadgeTag(text = "Hi-Res", color = goldColor)
                                }
                                currentSong.isLossless -> {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    BadgeTag(text = "SQ", color = ConceptPrimary)
                                }
                                currentSong.quality == "320" -> {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    BadgeTag(text = "HQ", color = ConceptPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val subtitleHint = when {
                            isSwipingNext -> if (willTriggerSwitch) "松手切下一首 ▶" else "左滑切下一首"
                            isSwipingPrev -> if (willTriggerSwitch) "◀ 松手切上一首" else "右滑切上一首"
                            else -> currentSong.artist.ifBlank { "未知歌手" }
                        }
                        val subtitleColor = if (willTriggerSwitch) ConceptPrimary else subTextColor

                        Text(
                            text = subtitleHint,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = subtitleColor,
                                fontWeight = if (willTriggerSwitch) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 右侧双按钮区：圆润饱满播放/暂停与圆润播放队列按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .softClickable(
                                pressScale = 0.88f,
                                pressAlpha = 0.85f,
                                onClick = onTogglePlay
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeW = 2.2.dp.toPx()
                            drawCircle(
                                color = ConceptPrimary.copy(alpha = if (isDark) 0.22f else 0.16f),
                                style = Stroke(width = strokeW)
                            )
                            if (progressFraction > 0.005f) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(ConceptPrimary, ConceptCyanAccent, ConceptPrimary)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = 360f * progressFraction,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(ConceptPrimary, ConceptCyanAccent.copy(alpha = 0.9f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(15.dp)) {
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
                                        centerX = w * 0.55f,
                                        centerY = h * 0.50f,
                                        size = min(w, h) * 0.86f
                                    )
                                    drawPath(path = tri, color = Color.White)
                                }
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (isDark) Color.White.copy(alpha = 0.10f) else ConceptPrimary.copy(alpha = 0.11f),
                        modifier = Modifier
                            .size(36.dp)
                            .softClickable(
                                pressScale = 0.88f,
                                pressAlpha = 0.80f,
                                onClick = onQueueClick
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val iconTint = if (isDark) Color.White.copy(alpha = 0.90f) else ConceptPrimary
                            Canvas(modifier = Modifier.size(18.dp)) {
                                val w = size.width
                                val h = size.height
                                val stroke = 2.1.dp.toPx()
                                drawLine(iconTint, Offset(w * 0.16f, h * 0.26f), Offset(w * 0.80f, h * 0.26f), stroke, StrokeCap.Round)
                                drawLine(iconTint, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.62f, h * 0.50f), stroke, StrokeCap.Round)
                                drawLine(iconTint, Offset(w * 0.16f, h * 0.74f), Offset(w * 0.48f, h * 0.74f), stroke, StrokeCap.Round)
                                drawCircle(ConceptCyanAccent, radius = 2.6.dp.toPx(), center = Offset(w * 0.72f, h * 0.74f))
                                drawLine(ConceptCyanAccent, Offset(w * 0.80f, h * 0.74f), Offset(w * 0.80f, h * 0.40f), stroke * 0.9f, StrokeCap.Round)
                            }
                        }
                    }
                }
            }
        }
    }
}
