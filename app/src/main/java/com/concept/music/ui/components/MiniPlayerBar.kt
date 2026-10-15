package com.concept.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.Song
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 现代流体悬浮播放条（Floating Island MiniPlayer）：
 * - 自适应「幽邃微蓝（深色）」与「水彩浸润（浅色）」双主题
 * - 连续旋转黑胶微唱盘与手势平滑横滑切歌
 * - 底部无缝内嵌渐变流光播放进度条
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
    val containerColor = if (isDark) Color(0xF2101726) else Color(0xF5EBF4FF)
    val borderColor = if (isDark) Color(0x285C9DFF) else Color(0x382B8CFF)
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(
                elevation = if (isDark) 18.dp else 10.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.65f) else ConceptPrimary.copy(alpha = 0.25f),
                spotColor = ConceptPrimary.copy(alpha = 0.28f)
            )
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onBarClick),
        color = containerColor,
        border = BorderStroke(0.8.dp, borderColor),
        tonalElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧封面与歌曲信息区域：支持左右滑动手势切歌与点击展开播放页
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            translationX = dragOffsetX.value * 0.45f
                            alpha = (1f - (kotlin.math.abs(dragOffsetX.value) / 360f)).coerceIn(0.4f, 1f)
                        }
                        .clickable(onClick = onBarClick)
                        .pointerInput(currentSong.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val finalOffset = dragOffsetX.value
                                    when {
                                        finalOffset < -95f -> onNextClick()
                                        finalOffset > 95f -> onPreviousClick()
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
                                        dragOffsetX.snapTo((dragOffsetX.value + dragAmount).coerceIn(-220f, 220f))
                                    }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 黑胶微唱盘封面 (Draw 阶段旋转)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                rotationZ = rotationAnim.value % 360f
                                shape = CircleShape
                                clip = true
                            }
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, borderColor, CircleShape),
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
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 歌曲与歌手
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentSong.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (currentSong.isHiRes) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Surface(
                                    color = VipGold.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Hi-Res",
                                        color = VipGold,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            } else if (currentSong.isLossless) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Surface(
                                    color = ConceptPrimary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "SQ",
                                        color = ConceptPrimary,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "${currentSong.artist} · 左右轻滑切歌",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = subTextColor,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 控制按键组：播放/暂停
                Surface(
                    color = ConceptPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePlay)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                // 下一曲
                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        tint = primaryTextColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 播放列表
                IconButton(
                    onClick = onQueueClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "播放列表",
                        tint = subTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 底部内嵌极细流光播放进度条
            val progressFraction = if (durationMs > 0L) {
                (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = ConceptPrimary,
                trackColor = ConceptPrimary.copy(alpha = 0.12f)
            )
        }
    }
}
