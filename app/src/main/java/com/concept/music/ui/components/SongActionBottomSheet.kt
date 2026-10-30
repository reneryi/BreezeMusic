package com.concept.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import com.concept.music.service.AppPermissionManager
import com.concept.music.service.AppPermissionType
import com.concept.music.ui.theme.ConceptAnimatedOverlaySheet
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptMintAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.softCardClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val songCoverSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

@Composable
fun SongActionBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSaveSongCover: (() -> Unit)? = null,
    onDownloadSong: (() -> Unit)? = null,
    onOpenComments: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    onOpenAlbum: (() -> Unit)? = null,
    onOpenSongDetail: (() -> Unit)? = null,
    onRemoveFromCurrentPlaylist: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    var pendingActionAfterDismiss by remember { mutableStateOf<(() -> Unit)?>(null) }

    val initialAlbumName = remember(song) {
        runCatching { VipManager.music.peekCachedAlbumName(song) }.getOrDefault(song.album.trim())
    }
    var resolvedAlbumName by remember(song.id, song.hash, song.album, song.albumId) {
        mutableStateOf(initialAlbumName)
    }
    var resolvedAlbumId by remember(song.id, song.hash, song.albumId) {
        mutableStateOf(song.albumId.trim().takeIf { it != "0" }.orEmpty())
    }

    LaunchedEffect(song.id, song.hash, song.album, song.albumId, song.albumAudioId, song.audioId) {
        if (resolvedAlbumName.isBlank()) {
            val (fetchedName, fetchedId) = runCatching {
                VipManager.music.resolveSongAlbumMeta(song)
            }.getOrDefault("" to resolvedAlbumId)
            if (fetchedName.isNotBlank()) {
                resolvedAlbumName = fetchedName
            }
            if (resolvedAlbumId.isBlank() && fetchedId.isNotBlank()) {
                resolvedAlbumId = fetchedId
            }
        }
    }

    val defaultSaveSongCoverAction: () -> Unit = {
        val appCtx = context.applicationContext
        if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.STORAGE_EXPORT)) {
            Toast.makeText(
                appCtx,
                "已在权限管理中关闭「存储读写与相册导出」权限，请先在设置-权限管理中开启",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(appCtx, "正在保存《${song.title}》高清封面...", Toast.LENGTH_SHORT).show()
            songCoverSaveScope.launch {
                val res = withContext(Dispatchers.IO) {
                    HdArtworkSaver.saveArtworkToGallery(
                        context = appCtx,
                        rawUrl = song.coverUrl,
                        category = ArtworkDownloadCategory.SONG_COVER,
                        title = song.title,
                        subtitle = song.artist,
                        songForFallback = song
                    )
                }
                when (res) {
                    is ArtworkSaveResult.Success -> Toast.makeText(appCtx, res.message, Toast.LENGTH_SHORT).show()
                    is ArtworkSaveResult.PermissionDisabled -> Toast.makeText(appCtx, res.message, Toast.LENGTH_LONG).show()
                    is ArtworkSaveResult.Failure -> Toast.makeText(appCtx, res.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val panelShape = if (isLandscape) {
        RoundedCornerShape(26.dp)
    } else {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    }

    ConceptAnimatedOverlaySheet(
        onDismiss = {
            onDismiss()
            pendingActionAfterDismiss?.invoke()
            pendingActionAfterDismiss = null
        },
        isLandscape = isLandscape,
        zIndex = 88f
    ) {
        val dismissWithAction: (() -> Unit) -> Unit = { action ->
            pendingActionAfterDismiss = action
            requestDismiss()
        }

        Surface(
            shape = panelShape,
            color = conceptSheetContainerColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 24.dp,
            modifier = (if (isLandscape) {
                Modifier
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
            } else {
                Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxWidth()
                    .heightIn(max = (configuration.screenHeightDp * 0.82f).dp)
            }).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // 顶部跟手拖拽横条与歌曲名片头部（支持直接下拉关闭，复刻播放队列抽屉丝滑手感）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sheetVerticalDragGesture()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isLandscape) {
                        Surface(
                            modifier = Modifier
                                .padding(top = 12.dp, bottom = 10.dp)
                                .width(40.dp)
                                .height(4.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                        ) {}
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }

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
                                .clip(RoundedCornerShape(14.dp))
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
                                    text = if (resolvedAlbumName.isNotBlank()) "${song.artist} · $resolvedAlbumName" else song.artist,
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
                                dismissWithAction { onToggleFavorite() }
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
                }

                // 2. 操作菜单项列表（不含播放页更多按钮，专属歌曲列表更多操作）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    SongActionMenuItem(
                        icon = Icons.Rounded.PlayCircleOutline,
                        title = "立即播放",
                        tint = ConceptPrimary,
                        onClick = { dismissWithAction { onPlayNow() } }
                    )

                    SongActionMenuItem(
                        icon = Icons.Rounded.QueuePlayNext,
                        title = "下一首播放",
                        onClick = { dismissWithAction { onPlayNext() } }
                    )

                    val permissionState by AppPermissionManager.state.collectAsState()
                    if (onDownloadSong != null && permissionState.musicDownloadEnabled) {
                        SongActionMenuItem(
                            icon = Icons.Rounded.Download,
                            title = "下载歌曲",
                            tint = ConceptMintAccent,
                            onClick = { dismissWithAction { onDownloadSong() } }
                        )
                    }

                    SongActionMenuItem(
                        icon = Icons.Rounded.Image,
                        title = "保存歌曲封面",
                        tint = ConceptCyanAccent,
                        onClick = {
                            dismissWithAction {
                                (onSaveSongCover ?: defaultSaveSongCoverAction).invoke()
                            }
                        }
                    )

                    SongActionMenuItem(
                        icon = if (isFavorite) Icons.Rounded.HeartBroken else Icons.Rounded.FavoriteBorder,
                        title = if (isFavorite) "从「我喜欢的音乐」移除" else "收藏到「我喜欢的音乐」",
                        tint = if (isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        onClick = { dismissWithAction { onToggleFavorite() } }
                    )

                    SongActionMenuItem(
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        title = "添加到其他歌单...",
                        onClick = { dismissWithAction { onAddToPlaylist() } }
                    )

                    if (onOpenComments != null) {
                        SongActionMenuItem(
                            icon = Icons.AutoMirrored.Rounded.Comment,
                            title = "查看歌曲评论",
                            onClick = { dismissWithAction { onOpenComments() } }
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
                            onClick = { dismissWithAction { onOpenArtist() } }
                        )
                    }

                    if (onOpenAlbum != null) {
                        SongActionMenuItem(
                            icon = Icons.Rounded.Album,
                            title = "查看专辑：${resolvedAlbumName.ifBlank { "未知专辑" }}",
                            onClick = { dismissWithAction { onOpenAlbum() } }
                        )
                    }

                    if (onOpenSongDetail != null) {
                        SongActionMenuItem(
                            icon = Icons.Rounded.Info,
                            title = "音频技术参数与特征码",
                            onClick = { dismissWithAction { onOpenSongDetail() } }
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
                            onClick = { dismissWithAction { onRemoveFromCurrentPlaylist() } }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
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
