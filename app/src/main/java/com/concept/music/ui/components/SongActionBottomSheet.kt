package com.concept.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.Song
import com.concept.music.service.AudioCacheManager
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.softCardClickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SongActionBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenComments: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    onOpenAlbum: (() -> Unit)? = null,
    onOpenSongDetail: (() -> Unit)? = null,
    onRemoveFromCurrentPlaylist: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isPinned by remember(song.id, song.hash) {
        mutableStateOf(AudioCacheManager.isSongPinned(context, song))
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = conceptSheetContainerColor(),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            // 1. 歌曲头部名片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.coverUrl.isNotEmpty()) {
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (song.isHiRes) {
                            BadgeTag(text = "Hi-Res", color = VipGold)
                            Spacer(modifier = Modifier.width(4.dp))
                        } else if (song.isLossless) {
                            BadgeTag(text = "SQ", color = ConceptPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (song.isVip) {
                            BadgeTag(text = "VIP", color = VipGold)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (song.album.isNotBlank()) "${song.artist} · ${song.album}" else song.artist,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "取消喜欢" else "我喜欢",
                        tint = if (isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(6.dp))

            // 2. 操作菜单项
            SongActionMenuItem(
                icon = Icons.Rounded.PlayCircleOutline,
                title = "立即播放",
                tint = ConceptPrimary,
                onClick = {
                    onDismiss()
                    onPlayNow()
                }
            )

            SongActionMenuItem(
                icon = Icons.Rounded.QueuePlayNext,
                title = "下一首播放",
                onClick = {
                    onDismiss()
                    onPlayNext()
                }
            )

            SongActionMenuItem(
                icon = if (isFavorite) Icons.Rounded.HeartBroken else Icons.Rounded.FavoriteBorder,
                title = if (isFavorite) "从「我喜欢的音乐」移除" else "收藏到「我喜欢的音乐」",
                tint = if (isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                onClick = {
                    onDismiss()
                    onToggleFavorite()
                }
            )

            SongActionMenuItem(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                title = "添加到其他歌单...",
                onClick = {
                    onDismiss()
                    onAddToPlaylist()
                }
            )

            SongActionMenuItem(
                icon = if (isPinned) Icons.Rounded.PushPin else Icons.Rounded.OfflinePin,
                title = if (isPinned) "已固定离线保护（点击解除）" else "固定离线收藏（清理缓存时不删除）",
                tint = if (isPinned) VipGold else ConceptPrimary,
                onClick = {
                    val nowPinned = AudioCacheManager.togglePinSong(context, song)
                    isPinned = nowPinned
                    Toast.makeText(
                        context,
                        if (nowPinned) "已开启「${song.title}」固定离线保护" else "已解除固定离线保护",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            if (onOpenComments != null) {
                SongActionMenuItem(
                    icon = Icons.AutoMirrored.Rounded.Comment,
                    title = "查看歌曲评论",
                    onClick = {
                        onDismiss()
                        onOpenComments()
                    }
                )
            }

            if (onOpenArtist != null && song.artist.isNotBlank()) {
                val artistNames = song.artist.split("、", "/", "&", "，", ",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                SongActionMenuItem(
                    icon = Icons.Rounded.Person,
                    title = if (artistNames.size > 1) {
                        "查看歌手：${artistNames.joinToString(" / ")}"
                    } else {
                        "查看歌手：${song.artist}"
                    },
                    onClick = {
                        onDismiss()
                        onOpenArtist()
                    }
                )
            }

            if (onOpenAlbum != null && song.albumId.isNotBlank() && song.albumId != "0") {
                SongActionMenuItem(
                    icon = Icons.Rounded.Album,
                    title = "查看专辑：${song.album.ifBlank { "未知专辑" }}",
                    onClick = {
                        onDismiss()
                        onOpenAlbum()
                    }
                )
            }

            if (onOpenSongDetail != null) {
                SongActionMenuItem(
                    icon = Icons.Rounded.Info,
                    title = "音频技术参数与特征码",
                    onClick = {
                        onDismiss()
                        onOpenSongDetail()
                    }
                )
            }

            if (onRemoveFromCurrentPlaylist != null) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                SongActionMenuItem(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "从当前歌单中移除",
                    tint = MaterialTheme.colorScheme.error,
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = {
                        onDismiss()
                        onRemoveFromCurrentPlaylist()
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SongActionMenuItem(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .softCardClickable(
                shape = RoundedCornerShape(12.dp),
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = textColor,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
