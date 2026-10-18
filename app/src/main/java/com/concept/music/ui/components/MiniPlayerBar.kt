package com.concept.music.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

/**
 * 立体层次感悬浮播放控制条（Layered Floating Island MiniPlayer）：
 * 1. 极简功能聚焦：移除上一首/下一首按钮，仅保留「播放/暂停」与「播放队列」双按钮；
 * 2. 丝滑横滑切歌：整条控制栏支持左右滑动切歌（左滑下一首 / 右滑上一首），滑动时实时浮现方向提示胶囊；
 * 3. 立体悬浮层次感：
 *    - 底层内缩叠影层（Stacked Ambient Base）营造双层卡片悬浮纵深；
 *    - 主胶囊采用多层漫反射彩色阴影 + 顶部高光描边 + 立体黑胶微缩唱片盘；
 *    - 播放按钮外环集成实时环形进度轨，底部内嵌呼吸流光进度条。
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
    val mainCapsuleBrush = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                Color(0xFA162238),
                Color(0xFA131C2E),
                Color(0xFA17243A)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFEFFFFFF),
                Color(0xFCF6FAFF),
                Color(0xFEFFFFFF)
            )
        )
    }
    val underStackColor = if (isDark) {
        ConceptPrimary.copy(alpha = 0.16f)
    } else {
        ConceptPrimary.copy(alpha = 0.12f)
    }
    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.16f),
                ConceptPrimary.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.05f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White,
                ConceptPrimary.copy(alpha = 0.16f),
                ConceptPrimary.copy(alpha = 0.08f)
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
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .graphicsLayer {
                scaleX = barScale
                scaleY = barScale
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // 第一层：底部内缩悬浮叠影衬底（营造类似头像卡片的立体多层悬浮感）
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(60.dp)
                .offset(y = 6.dp)
                .shadow(
                    elevation = if (isDark) 18.dp else 12.dp,
                    shape = RoundedCornerShape(30.dp),
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.65f) else ConceptPrimary.copy(alpha = 0.28f),
                    spotColor = ConceptPrimary.copy(alpha = 0.36f)
                )
                .clip(RoundedCornerShape(30.dp))
                .background(underStackColor)
        )

        // 第二层：主悬浮胶囊岛
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDark) 22.dp else 14.dp,
                    shape = RoundedCornerShape(30.dp),
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.72f) else ConceptPrimary.copy(alpha = 0.24f),
                    spotColor = if (isDark) ConceptPrimary.copy(alpha = 0.38f) else ConceptPrimary.copy(alpha = 0.32f)
                )
                .clip(RoundedCornerShape(30.dp))
                .border(1.dp, borderBrush, RoundedCornerShape(30.dp))
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
                    shape = RoundedCornerShape(30.dp),
                    pressScale = 0.985f,
                    pressAlpha = 0.95f,
                    onClick = onBarClick
                ),
            color = Color.Transparent,
            shape = RoundedCornerShape(30.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(mainCapsuleBrush)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧 + 中间：可随手势横滑位移的立体黑胶唱片与曲目信息区
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    translationX = currentDrag * 0.42f
                                    alpha = (1f - (abs(currentDrag) / 340f)).coerceIn(0.35f, 1f)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 立体双环黑胶微缩唱片盘
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .shadow(6.dp, CircleShape, spotColor = ConceptPrimary.copy(alpha = 0.35f))
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0xFF2B3242), Color(0xFF121620))
                                        )
                                    )
                                    .border(1.5.dp, ConceptPrimary.copy(alpha = 0.28f), CircleShape)
                                    .padding(3.5.dp),
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
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                // 唱片中心微轴孔
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF141C2E) else Color.White)
                                        .border(1.dp, ConceptPrimary.copy(alpha = 0.4f), CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentSong.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = primaryTextColor,
                                            fontSize = 14.5.sp
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

                                Spacer(modifier = Modifier.height(2.5.dp))

                                val subtitleHint = when {
                                    isSwipingNext -> if (willTriggerSwitch) "松手切换下一首 ▶" else "继续左滑切下一首"
                                    isSwipingPrev -> if (willTriggerSwitch) "◀ 松手切换上一首" else "继续右滑切上一首"
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

                        // 右侧极简双按钮区：1. 环形进度立体播放/暂停按钮  2. 播放队列抽屉呼出按钮
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .softClickable(
                                        pressScale = 0.88f,
                                        pressAlpha = 0.85f,
                                        onClick = onTogglePlay
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                // 外圈环形播放进度光轨
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeW = 2.2.dp.toPx()
                                    drawCircle(
                                        color = ConceptPrimary.copy(alpha = if (isDark) 0.20f else 0.15f),
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

                                // 中心饱满悬浮圆按钮
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .shadow(
                                            elevation = 5.dp,
                                            shape = CircleShape,
                                            spotColor = ConceptPrimary.copy(alpha = 0.45f)
                                        )
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(ConceptPrimary, ConceptCyanAccent.copy(alpha = 0.9f))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "暂停" else "播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            // 播放队列按钮（点击直接以抽屉形式弹出播放队列）
                            Surface(
                                shape = CircleShape,
                                color = if (isDark) Color.White.copy(alpha = 0.07f) else ConceptPrimary.copy(alpha = 0.09f),
                                border = BorderStroke(
                                    0.6.dp,
                                    if (isDark) Color.White.copy(alpha = 0.10f) else ConceptPrimary.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier
                                    .size(38.dp)
                                    .softClickable(
                                        pressScale = 0.88f,
                                        pressAlpha = 0.80f,
                                        onClick = onQueueClick
                                    )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = "弹出播放队列",
                                        tint = if (isDark) Color.White.copy(alpha = 0.88f) else ConceptPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 底部内缩柔和流光进度条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp)
                            .padding(bottom = 3.dp)
                            .height(2.2.dp)
                            .clip(CircleShape)
                            .background(ConceptPrimary.copy(alpha = if (isDark) 0.14f else 0.10f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(ConceptPrimary, ConceptCyanAccent)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
