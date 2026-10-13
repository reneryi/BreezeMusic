package com.concept.music.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.Playlist
import com.concept.music.data.model.Song
import com.concept.music.ui.components.AddToPlaylistDialog
import com.concept.music.ui.components.CreatePlaylistDialog
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: LibraryViewModel,
    playlistId: String,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    currentSong: Song? = null,
    onSongMoreClick: ((Song, (() -> Unit)?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.librarySongs.collectAsState()
    val loading by viewModel.libraryLoading.collectAsState()
    val error by viewModel.libraryError.collectAsState()

    val currentPlaylist = remember(playlists, playlistId, songs) {
        val matched = when (playlistId) {
            "like" -> playlists.firstOrNull { it.isLiked } ?: Playlist("like", "我喜欢的音乐", isLiked = true)
            "history" -> Playlist("history", "最近播放")
            else -> playlists.firstOrNull {
                it.id == playlistId || it.globalCollectionId == playlistId || it.listCreateListid == playlistId
            }
        }
        if (matched != null && matched.coverUrl.isBlank() && songs.isNotEmpty()) {
            matched.copy(coverUrl = songs.firstOrNull()?.coverUrl.orEmpty())
        } else {
            matched
        }
    }

    val isCustomCreated = remember(currentPlaylist, playlistId) {
        currentPlaylist != null && currentPlaylist.isCreatedByMe && !currentPlaylist.isLiked && playlistId != "like" && playlistId != "history"
    }

    val isExternalPlaylist = remember(currentPlaylist, playlistId) {
        currentPlaylist != null && !currentPlaylist.isCreatedByMe && playlistId != "like" && playlistId != "history"
    }

    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    var showUncollectPlaylistDialog by remember { mutableStateOf(false) }
    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var showCreateDialogFromAdd by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        val section = when (playlistId) {
            "like" -> "我喜欢"
            "history" -> "最近"
            else -> "歌单:$playlistId"
        }
        viewModel.openLibrarySection(section)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text(
                    text = currentPlaylist?.title ?: "歌单详情",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // 历史记录清空按钮
                if (playlistId == "history" && songs.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearHistory) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "清空历史",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        )
                    }
                }

                // 自建歌单删除按钮
                if (isCustomCreated) {
                    IconButton(onClick = { showDeletePlaylistDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "删除歌单",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }

                // 刷新按钮
                IconButton(onClick = viewModel::refreshMusicLibrary) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // 1. 歌单头部 Header（封面、信息与操作栏）
            item {
                PlaylistHeaderCard(
                    playlist = currentPlaylist,
                    songCount = songs.size,
                    isExternal = isExternalPlaylist,
                    onPlayAll = {
                        if (songs.isNotEmpty()) {
                            onSongClick(songs.first(), songs)
                        }
                    },
                    onCollectClick = {
                        if (currentPlaylist != null) {
                            if (currentPlaylist.isFavorite) {
                                showUncollectPlaylistDialog = true
                            } else {
                                viewModel.collectPlaylist(currentPlaylist) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }

            // 2. 状态提示
            if (loading && songs.isEmpty()) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = ConceptPrimary
                    )
                }
            }

            if (error != null) {
                item {
                    Text(
                        text = error ?: "加载失败",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (!loading && error == null && songs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "歌单内暂无歌曲",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. 歌曲列表
            itemsIndexed(songs) { index, song ->
                SongItemRow(
                    song = song,
                    orderIndex = index + 1,
                    isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                    onClick = { onSongClick(song, songs) },
                    onMoreClick = {
                        if (onSongMoreClick != null) {
                            val removeAction: (() -> Unit)? = if (isCustomCreated && currentPlaylist != null) {
                                {
                                    viewModel.removeSongFromPlaylist(currentPlaylist.listId, song) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else null
                            onSongMoreClick(song, removeAction)
                        } else {
                            selectedSongForMenu = song
                        }
                    }
                )
            }
        }
    }

    // 删除自建歌单二次确认
    if (showDeletePlaylistDialog && currentPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeletePlaylistDialog = false },
            title = { Text("删除歌单") },
            text = { Text("确定要删除歌单「${currentPlaylist.title}」吗？删除后云端数据不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        viewModel.deletePlaylist(currentPlaylist) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                onBack()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlaylistDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 取消收藏歌单二次确认
    if (showUncollectPlaylistDialog && currentPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showUncollectPlaylistDialog = false },
            title = { Text("取消收藏") },
            text = { Text("确定要取消收藏歌单「${currentPlaylist.title}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUncollectPlaylistDialog = false
                        viewModel.deletePlaylist(currentPlaylist) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("取消收藏")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUncollectPlaylistDialog = false }) {
                    Text("保留")
                }
            }
        )
    }

    // 单曲更多操作 BottomSheet
    if (selectedSongForMenu != null) {
        val targetSong = selectedSongForMenu!!
        ModalBottomSheet(
            onDismissRequest = { selectedSongForMenu = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                // 单曲头信息
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (targetSong.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = targetSong.coverUrl,
                                contentDescription = targetSong.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = targetSong.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = targetSong.artist,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // 操作列表
                // 1. 添加到歌单
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val s = targetSong
                            selectedSongForMenu = null
                            songToAddToPlaylist = s
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "添加到其他歌单",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // 2. 从当前自建歌单移除
                if (isCustomCreated && currentPlaylist != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val s = targetSong
                                selectedSongForMenu = null
                                viewModel.removeSongFromPlaylist(currentPlaylist.listId, s) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "从歌单中移除",
                            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.error)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 添加到歌单 Dialog
    if (songToAddToPlaylist != null) {
        val s = songToAddToPlaylist!!
        AddToPlaylistDialog(
            song = s,
            playlists = playlists,
            onSelectPlaylist = { targetPl ->
                songToAddToPlaylist = null
                viewModel.addSongToPlaylist(targetPl.listId, s, targetPl.title) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onCreateNewPlaylist = {
                showCreateDialogFromAdd = true
            },
            onDismiss = { songToAddToPlaylist = null }
        )
    }

    // 从添加到歌单处快捷新建歌单
    if (showCreateDialogFromAdd) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialogFromAdd = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        showCreateDialogFromAdd = false
                    }
                }
            }
        )
    }
}

@Composable
private fun PlaylistHeaderCard(
    playlist: Playlist?,
    songCount: Int,
    isExternal: Boolean = false,
    onPlayAll: () -> Unit,
    onCollectClick: () -> Unit = {}
) {
    var isDescExpanded by remember { mutableStateOf(false) }
    val displayCount = if (songCount > 0) songCount else (playlist?.trackCount ?: 0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 封面
                val isLikedPlaylist = playlist?.id == "like" || playlist?.isLiked == true
                val isHistoryPlaylist = playlist?.id == "history"
                val gradientColors = when {
                    isLikedPlaylist -> listOf(Color(0xFFEA580C), Color(0xFFC2410C))
                    isHistoryPlaylist -> listOf(ConceptPrimaryDark, Color(0xFF007565))
                    else -> listOf(ConceptPrimaryDark, Color(0xFF1E293B))
                }

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(colors = gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!playlist?.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = playlist!!.coverUrl,
                            contentDescription = playlist.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val icon = when {
                            isLikedPlaylist -> Icons.Rounded.Favorite
                            isHistoryPlaylist -> Icons.Rounded.History
                            else -> Icons.Rounded.MusicNote
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist?.title ?: "精选歌单",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "共 $displayCount 首歌曲",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 按钮栏（播放全部 + 收藏/已收藏按钮）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPlayAll,
                            enabled = songCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = ConceptPrimary),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("播放全部", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1115))
                        }

                        // 外部/他人歌单提供收藏/取消收藏按钮
                        if (isExternal && playlist != null) {
                            val isFavorite = playlist.isFavorite
                            if (isFavorite) {
                                OutlinedButton(
                                    onClick = onCollectClick,
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BookmarkAdded,
                                        contentDescription = null,
                                        tint = ConceptPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("已收藏", fontSize = 12.sp, color = ConceptPrimary)
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = onCollectClick,
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BookmarkAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("收藏", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (!playlist?.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = playlist!!.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    ),
                    maxLines = if (isDescExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { isDescExpanded = !isDescExpanded }
                )
            }
        }
    }
}
