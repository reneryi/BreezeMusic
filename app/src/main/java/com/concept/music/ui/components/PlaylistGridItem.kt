package com.concept.music.ui.components

import com.concept.music.ui.theme.softCardClickable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.Playlist
import com.concept.music.ui.theme.ConceptCoralAccent
import com.concept.music.ui.theme.ConceptMintAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptVioletAccent
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import kotlin.math.abs

/**
 * 歌单网格项 (画廊式无外框设计：仅歌单封面为圆角，下方歌单名最多两行超出省略号，支持已收藏快捷取消徽标)
 */
@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onUncollectClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accentColor = when (abs(playlist.title.hashCode()) % 4) {
        0 -> ConceptPrimary
        1 -> ConceptVioletAccent
        2 -> ConceptMintAccent
        else -> ConceptCoralAccent
    }

    Column(
        modifier = modifier
            .softCardClickable(clip = false, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accentColor.copy(alpha = 0.22f),
                            conceptElevatedSurfaceColor()
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            val coverModel = rememberOptimizedAsyncImageModel(rawUrl = playlist.coverUrl, sizePx = 240, targetCdnSize = "240")
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(38.dp)
                )
            }

            if (onUncollectClick != null && playlist.isFavorite && !playlist.isCreatedByMe) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.48f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onUncollectClick)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdded,
                            contentDescription = "取消收藏",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            val badgeText = playlist.playCount.ifBlank {
                if (playlist.trackCount > 0) "${playlist.trackCount}首" else ""
            }
            if (badgeText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (playlist.playCount.isNotEmpty()) Icons.Rounded.Headphones else Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = badgeText,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                lineHeight = 18.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
