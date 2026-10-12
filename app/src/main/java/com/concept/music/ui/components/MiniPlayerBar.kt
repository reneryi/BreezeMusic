package com.concept.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
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
import com.concept.music.ui.theme.VipGold
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = ConceptPrimary.copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onBarClick),
        color = Color(0xE8181C24),
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.09f)),
        tonalElevation = 8.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧封面与歌曲信息区域：支持左右滑动手势切歌 ([INT-01])
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            translationX = dragOffsetX.value * 0.45f
                            alpha = (1f - (kotlin.math.abs(dragOffsetX.value) / 360f)).coerceIn(0.4f, 1f)
                        }
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
                    // 封面 (连续相位旋转唱盘样式，下推至 Draw 阶段避免 60fps 主线程重组)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                rotationZ = rotationAnim.value % 360f
                                shape = CircleShape
                                clip = true
                            }
                            .background(Color(0xFF282D37)),
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
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 歌曲与歌手
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentSong.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (currentSong.isHiRes) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = VipGold.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Hi-Res",
                                        color = VipGold,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            } else if (currentSong.isLossless) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = ConceptPrimary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "SQ",
                                        color = ConceptPrimary,
                                        fontSize = 9.sp,
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
                                color = Color.White.copy(alpha = 0.58f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 播放/暂停实心圆按钮
                Surface(
                    color = ConceptPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePlay)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color(0xFF0A1014),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 下一曲
                IconButton(onClick = onNextClick) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 播放列表
                IconButton(onClick = onQueueClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "播放列表",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 细微播放进度条
            val progressFraction = if (durationMs > 0L) {
                (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = ConceptPrimary,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}
