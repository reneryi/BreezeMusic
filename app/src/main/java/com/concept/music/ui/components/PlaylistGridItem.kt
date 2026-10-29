package com.concept.music.ui.components

import com.concept.music.ui.theme.softCardClickable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
 * 歌单网格项 (画廊式无外框设计：仅歌单封面为圆角，下方歌单名最多两行超出省略号，去除重复的曲目数副标题)
 */
@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    onClick: () -> Unit,
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

            if (playlist.playCount.isNotEmpty()) {
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
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = playlist.playCount,
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
