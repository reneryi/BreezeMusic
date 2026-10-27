package com.concept.music.ui.components

import com.concept.music.ui.theme.softClickable

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.concept.music.ui.theme.ConceptMotion
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
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
import coil.size.Precision
import com.concept.music.data.model.Song
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold

private val SongCoverCornerShape = RoundedCornerShape(13.dp)
private val ActiveRowBgColor = ConceptPrimary.copy(alpha = 0.12f)
private val HqBadgeColor = ConceptPrimary.copy(alpha = 0.85f)

private fun normalizeSongThumbUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return ""
    val withSize = if (trimmed.contains("{size}")) {
        trimmed.replace("{size}", "150")
    } else {
        trimmed
    }
    return if (withSize.startsWith("http://", ignoreCase = true)) {
        "https://" + withSize.substring(7)
    } else {
        withSize
    }
}

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

    val thumbUrl = remember(song.coverUrl) { normalizeSongThumbUrl(song.coverUrl) }
    val imageModel = remember(thumbUrl, context) {
        if (thumbUrl.isNotEmpty()) {
            ImageRequest.Builder(context)
                .data(thumbUrl)
                .size(144, 144)
                .precision(Precision.INEXACT)
                .memoryCacheKey(thumbUrl)
                .diskCacheKey(thumbUrl)
                .allowHardware(true)
                .crossfade(false)
                .build()
        } else null
    }

    val orderLabel = remember(orderIndex) {
        orderIndex?.toString()?.padStart(2, '0').orEmpty()
    }
    val subtitleText = remember(song.artist, song.album, song.albumId, song.albumAudioId) {
        val displayAlbum = song.album.ifBlank {
            runCatching { com.concept.music.data.vip.VipManager.music.peekCachedAlbumName(song) }.getOrDefault("")
        }
        if (displayAlbum.isNotBlank() && displayAlbum != "单曲" && displayAlbum != "未知专辑") "${song.artist} · $displayAlbum" else song.artist
    }
    val qualityLabel = remember(song.isHiRes, song.isLossless, song.quality) {
        when {
            song.isHiRes -> "Hi-Res"
            song.isLossless -> "SQ"
            song.quality == "320" -> "HQ"
            else -> ""
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .drawBehind {
                if (isPlaying) {
                    val r = 16.dp.toPx()
                    drawRoundRect(
                        color = ActiveRowBgColor,
                        cornerRadius = CornerRadius(r, r)
                    )
                }
            }
            .softClickable(pressScale = 0.985f, pressAlpha = 0.88f, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
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
                        text = orderLabel,
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
                .size(48.dp)
                .clip(SongCoverCornerShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
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
                if (qualityLabel.isNotEmpty()) {
                    val qColor = when (qualityLabel) {
                        "Hi-Res" -> goldTagColor
                        "SQ" -> ConceptPrimary
                        else -> HqBadgeColor
                    }
                    BadgeTag(text = qualityLabel, color = qColor)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (song.isVip) {
                    BadgeTag(text = "VIP", color = goldTagColor)
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isPlaying) ConceptPrimary.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onMoreClick != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .softClickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center
            ) {
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
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true
) {
    val bar1: Float
    val bar2: Float
    val bar3: Float
    if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "audioWave")
        val animBar1 by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
        val animBar2 by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(360, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
        val animBar3 by infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )
        bar1 = animBar1
        bar2 = animBar2
        bar3 = animBar3
    } else {
        val settled1 by animateFloatAsState(0.28f, ConceptMotion.gentleBounceSpring(), label = "settledBar1")
        val settled2 by animateFloatAsState(0.44f, ConceptMotion.gentleBounceSpring(), label = "settledBar2")
        val settled3 by animateFloatAsState(0.24f, ConceptMotion.gentleBounceSpring(), label = "settledBar3")
        bar1 = settled1
        bar2 = settled2
        bar3 = settled3
    }

    // 采用 Canvas 硬件加速绘制跳动音律柱，仅在 Draw 阶段更新，完全消除每帧 Layout 重排开销
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
    val bgColor = color.copy(alpha = 0.16f)
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .drawBehind {
                val r = 5.dp.toPx()
                drawRoundRect(
                    color = bgColor,
                    cornerRadius = CornerRadius(r, r)
                )
            }
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}
