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
import com.concept.music.ui.theme.rememberIsLifecycleStarted

private val SongCoverCornerShape = RoundedCornerShape(13.dp)
private val ActiveRowBgColor = ConceptPrimary.copy(alpha = 0.12f)
private val HqBadgeColor = ConceptPrimary.copy(alpha = 0.85f)

private val KUGOU_CDN_LARGE_SIZE_REGEX = Regex("/(240|300|400|480|720|1000)/")

/**
 * 酷狗封面与头像缩略图 URL 归一化引擎：
 * 1. 将 `{size}` 模板变量替换为紧凑目标尺寸（列表小图默认 `"150"`，中尺寸卡片默认 `"240"`）；
 * 2. 将已被上游写死为 `/240/`、`/300/`、`/400/`、`/480/`、`/720/`、`/1000/` 的酷狗 CDN 路径降维至目标缩略图尺寸，
 *    避免列表高速上下滑动时拉取与解码百万像素原图；
 * 3. 统一升级 `http://` 为 `https://`，保证与 Coil L1/L2 缓存 Key 100% 一致。
 */
fun normalizeKugouThumbUrl(rawUrl: String?, targetCdnSize: String = "150"): String {
    val trimmed = rawUrl?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    val withSize = when {
        trimmed.contains("{size}") -> trimmed.replace("{size}", targetCdnSize)
        (trimmed.contains("kugou.com", ignoreCase = true) || trimmed.contains("kgimg.com", ignoreCase = true)) &&
            KUGOU_CDN_LARGE_SIZE_REGEX.containsMatchIn(trimmed) -> {
            KUGOU_CDN_LARGE_SIZE_REGEX.replaceFirst(trimmed, "/$targetCdnSize/")
        }
        else -> trimmed
    }
    return if (withSize.startsWith("http://", ignoreCase = true)) {
        "https://" + withSize.substring(7)
    } else {
        withSize
    }
}

private fun normalizeSongThumbUrl(rawUrl: String): String = normalizeKugouThumbUrl(rawUrl, "150")

/**
 * 120fps 列表滑动专用零抖动图片请求构建器：
 * - 显式指定目标解码像素尺寸 (`sizePx`) 与 `Precision.INEXACT`，杜绝按全尺寸解码大图；
 * - 禁用列表项 `crossfade(false)`，消除快速上下滑动时每行 200ms CrossfadeDrawable 透明混合重绘开销；
 * - 统一 `allowHardware(false)` 与全局 `ConceptMusicApp` ImageLoader 保持一致，既根治 Android 14+ 酷狗 Gainmap 封面硬件纹理崩溃，又确保首屏预热缓存 100% 命中。
 */
@Composable
fun rememberOptimizedAsyncImageModel(
    rawUrl: String?,
    sizePx: Int = 144,
    targetCdnSize: String = if (sizePx <= 160) "150" else "240"
): ImageRequest? {
    val context = LocalContext.current
    val thumbUrl = remember(rawUrl, targetCdnSize) {
        normalizeKugouThumbUrl(rawUrl, targetCdnSize)
    }
    return remember(thumbUrl, sizePx, context) {
        if (thumbUrl.isNotEmpty()) {
            val cacheKey = "${thumbUrl}_s$sizePx"
            ImageRequest.Builder(context)
                .data(thumbUrl)
                .size(sizePx, sizePx)
                .precision(Precision.INEXACT)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(thumbUrl)
                .allowHardware(false)
                .crossfade(false)
                .build()
        } else null
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
    val isDark = LocalIsDarkTheme.current
    val goldTagColor = if (isDark) VipGold else Color(0xFFC47F00)
    val imageModel = rememberOptimizedAsyncImageModel(rawUrl = song.coverUrl, sizePx = 144, targetCdnSize = "150")

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
    val isLifecycleStarted = rememberIsLifecycleStarted()
    val effectiveAnimating = isPlaying && isLifecycleStarted
    val bar1: Float
    val bar2: Float
    val bar3: Float
    if (effectiveAnimating) {
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
