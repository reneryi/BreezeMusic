package com.concept.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.Playlist
import com.concept.music.data.model.Song
import com.concept.music.data.share.KugouShareManager
import com.concept.music.data.share.ResolvedSharedMusicTarget
import com.concept.music.data.share.ShareMusicPayload
import com.concept.music.data.share.ShareTargetEdition
import com.concept.music.data.vip.VipManager
import com.concept.music.service.AppPermissionManager
import com.concept.music.ui.theme.ConceptAmberAccent
import com.concept.music.ui.theme.ConceptAnimatedOverlaySheet
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptMintAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptVioletAccent
import com.concept.music.ui.theme.LocalConceptAdaptiveInfo
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.softCardClickable

@Composable
fun ShareMusicBottomSheet(
    song: Song? = null,
    playlist: Playlist? = null,
    onDismiss: () -> Unit,
    onOpenLyricPoster: (() -> Unit)? = null
) {
    val enrichedSong by produceState(initialValue = song, key1 = song) {
        if (song != null) {
            value = runCatching { VipManager.music.enrichSongForShare(song) }.getOrDefault(song)
        }
    }
    val enrichedPlaylist by produceState(initialValue = playlist, key1 = playlist) {
        if (playlist != null) {
            value = runCatching { VipManager.music.enrichPlaylistForShare(playlist) }.getOrDefault(playlist)
        }
    }

    val payload = remember(enrichedSong, enrichedPlaylist) {
        when {
            enrichedSong != null -> KugouShareManager.buildSongSharePayload(enrichedSong!!)
            enrichedPlaylist != null -> KugouShareManager.buildPlaylistSharePayload(enrichedPlaylist!!)
            else -> null
        }
    } ?: return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val adaptiveInfo = LocalConceptAdaptiveInfo.current
    val useCenteredCompactDialog = isLandscape || adaptiveInfo.isLargeScreenDevice
    val permissionState by AppPermissionManager.state.collectAsState()

    var selectedEdition by remember { mutableStateOf(ShareTargetEdition.STANDARD_EDITION) }
    var pendingAfterDismiss by remember { mutableStateOf<(() -> Unit)?>(null) }

    val panelShape = if (useCenteredCompactDialog) {
        RoundedCornerShape(24.dp)
    } else {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    }

    ConceptAnimatedOverlaySheet(
        onDismiss = {
            onDismiss()
            pendingAfterDismiss?.invoke()
            pendingAfterDismiss = null
        },
        isLandscape = useCenteredCompactDialog,
        zIndex = 94f
    ) {
        val dismissWithAction: (() -> Unit) -> Unit = { action ->
            pendingAfterDismiss = action
            requestDismiss()
        }

        Surface(
            shape = panelShape,
            color = conceptSheetContainerColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 24.dp,
            modifier = (if (useCenteredCompactDialog) {
                Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .widthIn(max = 430.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .heightIn(max = (configuration.screenHeightDp * 0.86f).dp)
            } else {
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .heightIn(max = (configuration.screenHeightDp * 0.86f).dp)
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
                // 1. 顶部拖拽手柄 + 分享对象预览名片
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sheetVerticalDragGesture()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!useCenteredCompactDialog) {
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (payload.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = payload.coverUrl,
                                        contentDescription = payload.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (payload.isPlaylist) Icons.AutoMirrored.Rounded.QueueMusic else Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = ConceptPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ConceptPrimary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = if (payload.isPlaylist) "分享歌单" else "分享单曲",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ConceptPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = payload.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.8.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = payload.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(onClick = requestDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }

                // 2. 唤醒协议模式切换与分享动作区
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "目标唤醒版本选择",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShareTargetEdition.entries.forEach { edition ->
                            val selected = selectedEdition == edition
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) {
                                    ConceptPrimary.copy(alpha = 0.22f)
                                } else {
                                    conceptElevatedSurfaceColor()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable(
                                        shape = RoundedCornerShape(12.dp),
                                        onClick = { selectedEdition = edition }
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = edition.shortBadge,
                                        fontSize = 11.8.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = if (selected) ConceptPrimary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = selectedEdition.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )

                    // 主分享操作卡片
                    ShareActionRowCard(
                        icon = Icons.Rounded.Share,
                        title = "发送给微信 / QQ / 系统分享",
                        subtitle = "携带${selectedEdition.shortBadge}官方唤醒链接，对方点击即可唤起对应酷狗 App",
                        accentColor = ConceptPrimary,
                        onClick = {
                            dismissWithAction {
                                KugouShareManager.shareViaSystemSheet(context, payload, selectedEdition)
                            }
                        }
                    )

                    ShareActionRowCard(
                        icon = Icons.Rounded.ContentCopy,
                        title = if (permissionState.clipboardAccessEnabled) {
                            "复制分享口令与唤醒链接"
                        } else {
                            "复制分享口令（剪贴板权限已关闭）"
                        },
                        subtitle = if (permissionState.clipboardAccessEnabled) {
                            "复制完整分享文案至剪贴板，支持粘贴到微信/QQ或在本应用内解析"
                        } else {
                            "已在「设置 - 权限管理」中关闭剪贴板读写权限，点击将提示开启"
                        },
                        accentColor = if (permissionState.clipboardAccessEnabled) ConceptMintAccent else ConceptAmberAccent,
                        onClick = {
                            val copied = KugouShareManager.copyShareText(context, payload, selectedEdition)
                            if (copied) {
                                requestDismiss()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ShareActionRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = conceptElevatedSurfaceColor(),
        modifier = Modifier
            .fillMaxWidth()
            .softCardClickable(shape = RoundedCornerShape(16.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 剪贴板或系统分享检测到官方酷狗 / 酷狗概念版分享链接时的识别确认弹窗
 */
@Composable
fun DetectedShareLinkDialog(
    target: ResolvedSharedMusicTarget,
    onDismiss: () -> Unit,
    onPlaySongNow: (Song) -> Unit,
    onPlaySongNext: (Song) -> Unit,
    onOpenPlaylist: (String, Playlist?, List<Song>) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = conceptSheetContainerColor(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(ConceptPrimary, ConceptMintAccent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (target) {
                        is ResolvedSharedMusicTarget.SharedSong -> "识别到酷狗分享单曲"
                        is ResolvedSharedMusicTarget.SharedPlaylist -> "识别到酷狗分享歌单"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        },
        text = {
            when (target) {
                is ResolvedSharedMusicTarget.SharedSong -> {
                    val song = target.song
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = conceptElevatedSurfaceColor(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (song.coverUrl.isNotBlank()) {
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
                                        tint = ConceptPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (song.album.isNotBlank()) "${song.artist} · ${song.album}" else song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                is ResolvedSharedMusicTarget.SharedPlaylist -> {
                    val pl = target.playlist
                    val title = pl?.title ?: "酷狗分享歌单"
                    val sub = buildString {
                        if (!pl?.creatorName.isNullOrBlank()) {
                            append(pl?.creatorName)
                        } else {
                            append("来自酷狗分享链接")
                        }
                        val cnt = pl?.trackCount?.takeIf { it > 0 } ?: target.fallbackSongs.size
                        if (cnt > 0) {
                            append(" · 共 ").append(cnt).append(" 首")
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = conceptElevatedSurfaceColor(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                val cover = pl?.coverUrl.orEmpty()
                                if (cover.isNotBlank()) {
                                    AsyncImage(
                                        model = cover,
                                        contentDescription = title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = ConceptPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (target) {
                is ResolvedSharedMusicTarget.SharedSong -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                onPlaySongNext(target.song)
                                onDismiss()
                            }
                        ) {
                            Text("下一首播放", color = ConceptMintAccent, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                onPlaySongNow(target.song)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ConceptPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("立即播放", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is ResolvedSharedMusicTarget.SharedPlaylist -> {
                    Button(
                        onClick = {
                            onOpenPlaylist(target.playlistId, target.playlist, target.fallbackSongs)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ConceptPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("查看歌单", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("忽略", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
