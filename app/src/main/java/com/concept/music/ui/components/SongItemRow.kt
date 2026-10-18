package com.concept.music.ui.components

import com.concept.music.ui.theme.softClickable

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.concept.music.data.model.Song
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold

@Composable
fun SongItemRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    orderIndex: Int? = null,
    onMoreClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val goldTagColor = if (isDark) VipGold else Color(0xFFC47F00)

    val imageModel = remember(song.coverUrl) {
        if (song.coverUrl.isNotBlank()) {
            ImageRequest.Builder(context)
                .data(song.coverUrl)
                .size(144, 144)
                .crossfade(false)
                .build()
        } else null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isPlaying) ConceptPrimary.copy(alpha = 0.12f) else Color.Transparent
            )
            .softClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (orderIndex != null) {
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isPlaying) {
                    PlayingAudioWaveIndicator()
                } else {
                    val isTopThree = orderIndex in 1..3
                    Text(
                        text = orderIndex.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isTopThree) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isTopThree) ConceptPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (orderIndex == null && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayingAudioWaveIndicator()
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isPlaying) ConceptPrimary else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 3.dp)
            ) {
                when {
                    song.isHiRes -> {
                        BadgeTag(text = "Hi-Res", color = goldTagColor)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    song.isLossless -> {
                        BadgeTag(text = "SQ", color = ConceptPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    song.quality == "320" -> {
                        BadgeTag(text = "HQ", color = ConceptPrimary.copy(alpha = 0.85f))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
                if (song.isVip) {
                    BadgeTag(text = "VIP", color = goldTagColor)
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = if (song.album.isNotBlank() && song.album != "单曲") "${song.artist} · ${song.album}" else song.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isPlaying) ConceptPrimary.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onMoreClick != null) {
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PlayingAudioWaveIndicator(
    color: Color = ConceptPrimary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioWave")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    // 采用 Canvas 硬件加速绘制跳动音律柱，仅在 Draw 阶段更新，完全消除每帧 Layout 重排开销（保障 120fps 满帧滚动）
    Canvas(
        modifier = modifier
            .width(18.dp)
            .height(16.dp)
    ) {
        val barW = 3.dp.toPx()
        val gap = 2.5.dp.toPx()
        val maxH = size.height
        val radius = CornerRadius(barW / 2f, barW / 2f)
        val fractions = floatArrayOf(bar1, bar2, bar3)
        for (i in 0..2) {
            val h = (maxH * fractions[i]).coerceAtLeast(barW)
            val left = i * (barW + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(left, maxH - h),
                size = Size(barW, h),
                cornerRadius = radius
            )
        }
    }
}

@Composable
fun BadgeTag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(5.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}
