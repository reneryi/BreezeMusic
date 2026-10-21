package com.concept.music.ui.library

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.concept.music.ui.components.FloatingScrollToTopButton
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptCoralAccent
import com.concept.music.ui.theme.ConceptMintAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.ConceptCategoryTabItem
import com.concept.music.ui.theme.ConceptFluidRefreshIcon
import com.concept.music.ui.theme.ConceptSlidingCategoryTabs
import com.concept.music.ui.theme.horizontalCategorySwipe
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

/**
 * 歌单内排序模式（无门槛直接标配，不设 200 首门槛限制）
 */
enum class PlaylistSortMode(val label: String) {
    DEFAULT("默认顺序"),
    TITLE_AZ("歌名 A-Z"),
    ARTIST_AZ("歌手名"),
    DURATION_DESC("时长最长")
}

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
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.librarySongs.collectAsState()
    val loading by viewModel.libraryLoading.collectAsState()
    val error by viewModel.libraryError.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(PlaylistSortMode.DEFAULT) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf(setOf<String>()) }
    var showBatchAddToPlaylistDialog by remember { mutableStateOf(false) }

    val filteredAndSortedSongs = remember(songs, searchQuery, sortMode) {
        val q = searchQuery.trim()
        val filtered = if (q.isBlank()) {
            songs
        } else {
            songs.filter { s ->
                s.title.contains(q, ignoreCase = true) ||
                    s.artist.contains(q, ignoreCase = true) ||
                    s.album.contains(q, ignoreCase = true)
            }
        }
        when (sortMode) {
            PlaylistSortMode.DEFAULT -> filtered
            PlaylistSortMode.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            PlaylistSortMode.ARTIST_AZ -> filtered.sortedBy { it.artist.lowercase() }
            PlaylistSortMode.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
        }
    }

    // 智能判断当前正在播放的歌曲是否包含于当前浏览的歌单（含「我喜欢」、自建歌单、收藏歌单等）
    val playingSongIndex = remember(filteredAndSortedSongs, currentSong) {
        if (currentSong == null) -1
        else filteredAndSortedSongs.indexOfFirst { s -> viewModel.isSameSong(s, currentSong) }
    }

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

    ConceptStageBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
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

                        // 批量多选模式切换按钮
                        if (songs.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    isMultiSelectMode = !isMultiSelectMode
                                    if (!isMultiSelectMode) selectedSongIds = emptySet()
                                }
                            ) {
                                Text(
                                    text = if (isMultiSelectMode) "完成 (${selectedSongIds.size})" else "多选",
                                    color = ConceptPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

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

                        // 刷新按钮（严格边界：仅刷新当前歌单，不触发全局资产重拉）
                        var plRefreshSpin by remember { mutableIntStateOf(0) }
                        IconButton(
                            onClick = {
                                plRefreshSpin += 1
                                viewModel.refreshCurrentPlaylistOnly(playlistId)
                            }
                        ) {
                            ConceptFluidRefreshIcon(
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                isRefreshing = loading,
                                spinTrigger = plRefreshSpin,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 128.dp)
                ) {
                    // 1. 歌单头部 Header（封面、信息与操作栏）
                    item {
                        PlaylistHeaderCard(
                            playlist = currentPlaylist,
                            songCount = songs.size,
                            isExternal = isExternalPlaylist,
                            onPlayAll = {
                                if (filteredAndSortedSongs.isNotEmpty()) {
                                    onSongClick(filteredAndSortedSongs.first(), filteredAndSortedSongs)
                                }
                            },
                            onCollectClick = {
                                if (currentPlaylist != null) {
                                    if (currentPlaylist.isFavorite) {
                                        showUncollectPlaylistDialog = true
                                    } else {
                                        viewModel.collectPlaylist(currentPlaylist) { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }

                    // 2. 歌单直接标配「歌单内搜索 + 排序切换 + 批量多选栏」
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 极简无边框圆角搜索栏
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        text = "搜索歌单内歌曲 / 歌手 / 专辑...",
                                        fontSize = 13.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = "搜索",
                                        tint = ConceptPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "清除",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                shape = CircleShape,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ConceptPrimary,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = conceptFloatingCardColor(),
                                    unfocusedContainerColor = conceptFloatingCardColor()
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            )

                            // 开放式极简排序栏（带左右平滑滑动横条动画）
                            val sortTabItems = remember {
                                PlaylistSortMode.entries.map { mode ->
                                    ConceptCategoryTabItem(
                                        key = mode.name,
                                        label = mode.label,
                                        accentColor = ConceptPrimary
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .horizontalCategorySwipe(
                                        currentIndex = sortMode.ordinal,
                                        itemCount = PlaylistSortMode.entries.size,
                                        onSelectIndex = { nextIdx ->
                                            PlaylistSortMode.entries.getOrNull(nextIdx)?.let { sortMode = it }
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = "排序",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "排序",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                ConceptSlidingCategoryTabs(
                                    tabs = sortTabItems,
                                    selectedKey = sortMode.name,
                                    onSelectTab = { key ->
                                        PlaylistSortMode.entries.find { it.name == key }?.let { sortMode = it }
                                    },
                                    itemSpacing = 16.dp,
                                    fontSize = 12.5.sp,
                                    indicatorWidth = 16.dp,
                                    fillWidth = false
                                )
                            }

                            // 批量多选操作栏
                            AnimatedVisibility(visible = isMultiSelectMode) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = ConceptPrimary.copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                onClick = {
                                                    selectedSongIds = if (selectedSongIds.size == filteredAndSortedSongs.size) {
                                                        emptySet()
                                                    } else {
                                                        filteredAndSortedSongs.map { it.id }.toSet()
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (selectedSongIds.size == filteredAndSortedSongs.size) "取消全选" else "全选 (${filteredAndSortedSongs.size})",
                                                    color = ConceptPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilledTonalButton(
                                                onClick = {
                                                    val chosen = filteredAndSortedSongs.filter { selectedSongIds.contains(it.id) }
                                                    if (chosen.isNotEmpty()) {
                                                        onSongClick(chosen.first(), chosen)
                                                        isMultiSelectMode = false
                                                        selectedSongIds = emptySet()
                                                    }
                                                },
                                                enabled = selectedSongIds.isNotEmpty(),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("播放选中", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            FilledTonalButton(
                                                onClick = { showBatchAddToPlaylistDialog = true },
                                                enabled = selectedSongIds.isNotEmpty(),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("批量加歌单", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. 状态提示
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

                    if (!loading && error == null && filteredAndSortedSongs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "未找到匹配「$searchQuery」的歌曲" else "歌单内暂无歌曲",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 4. 歌曲列表
                    itemsIndexed(filteredAndSortedSongs, key = { idx, s -> "${s.id}_${s.hash}_$idx" }) { index, song ->
                        val isCurrentTrack = currentSong != null && viewModel.isSameSong(song, currentSong)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMultiSelectMode) {
                                Checkbox(
                                    checked = selectedSongIds.contains(song.id),
                                    onCheckedChange = { checked ->
                                        selectedSongIds = if (checked) {
                                            selectedSongIds + song.id
                                        } else {
                                            selectedSongIds - song.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = ConceptPrimary),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                SongItemRow(
                                    song = song,
                                    orderIndex = index + 1,
                                    isPlaying = isCurrentTrack,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            selectedSongIds = if (selectedSongIds.contains(song.id)) {
                                                selectedSongIds - song.id
                                            } else {
                                                selectedSongIds + song.id
                                            }
                                        } else {
                                            onSongClick(song, filteredAndSortedSongs)
                                        }
                                    },
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
                }
            }

            // 右下角悬浮按钮组：返回顶部（歌单超30首且下滚时展示）+ 极简纯图标定位当前播放歌曲
            val showScrollToTop by remember {
                derivedStateOf {
                    filteredAndSortedSongs.size > 30 && listState.firstVisibleItemIndex > 4
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 104.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingScrollToTopButton(
                    visible = showScrollToTop,
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                )

                AnimatedVisibility(
                    visible = playingSongIndex >= 0,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut() + scaleOut(targetScale = 0.85f)
                ) {
                    FilledIconButton(
                        onClick = {
                            if (playingSongIndex >= 0) {
                                coroutineScope.launch {
                                    val targetLazyIndex = (2 + playingSongIndex - 2).coerceAtLeast(0)
                                    listState.animateScrollToItem(targetLazyIndex)
                                }
                            }
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = ConceptPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(8.dp, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = "定位当前播放歌曲",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }

    // 批量添加到歌单弹窗
    if (showBatchAddToPlaylistDialog) {
        val chosenSongs = filteredAndSortedSongs.filter { selectedSongIds.contains(it.id) }
        val lead = chosenSongs.firstOrNull()
        if (lead != null) {
            AddToPlaylistDialog(
                song = lead,
                playlists = playlists,
                onSelectPlaylist = { targetPl ->
                    showBatchAddToPlaylistDialog = false
                    chosenSongs.forEach { s ->
                        viewModel.addSongToPlaylist(targetPl.listId, s, targetPl.title) { _, _ -> }
                    }
                    Toast.makeText(context, "已将 ${chosenSongs.size} 首歌曲加入「${targetPl.title}」", Toast.LENGTH_SHORT).show()
                    isMultiSelectMode = false
                    selectedSongIds = emptySet()
                },
                onCreateNewPlaylist = {
                    showBatchAddToPlaylistDialog = false
                    showCreateDialogFromAdd = true
                },
                onDismiss = { showBatchAddToPlaylistDialog = false }
            )
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
                        viewModel.deletePlaylist(currentPlaylist) { _, msg ->
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
            containerColor = conceptSheetContainerColor(),
            windowInsets = WindowInsets(0, 0, 0, 0),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .softClickable {
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

                if (isCustomCreated && currentPlaylist != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .softClickable {
                                val s = targetSong
                                selectedSongForMenu = null
                                viewModel.removeSongFromPlaylist(currentPlaylist.listId, s) { _, msg ->
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
                viewModel.addSongToPlaylist(targetPl.listId, s, targetPl.title) { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onCreateNewPlaylist = {
                showCreateDialogFromAdd = true
            },
            onDismiss = { songToAddToPlaylist = null }
        )
    }

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
    val isLikedPlaylist = playlist?.id == "like" || playlist?.isLiked == true
    val isHistoryPlaylist = playlist?.id == "history"
    val accentColor = when {
        isLikedPlaylist -> ConceptCoralAccent
        isHistoryPlaylist -> ConceptMintAccent
        else -> ConceptPrimary
    }
    val gradientColors = when {
        isLikedPlaylist -> listOf(ConceptCoralAccent, Color(0xFFC2410C))
        isHistoryPlaylist -> listOf(ConceptMintAccent, ConceptPrimaryDark)
        else -> listOf(ConceptPrimary, ConceptPrimaryDark)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
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
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist?.title ?: "精选歌单",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!playlist?.creatorName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playlist!!.creatorName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .softClickable(enabled = songCount > 0, onClick = onPlayAll)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "播放全部",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (isExternal && playlist != null) {
                        val isFavorite = playlist.isFavorite
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .softClickable(onClick = onCollectClick)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                contentDescription = null,
                                tint = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFavorite) "已收藏" else "收藏",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (!playlist?.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .softClickable { isDescExpanded = !isDescExpanded }
            ) {
                Text(
                    text = playlist!!.description.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                        lineHeight = 19.sp
                    ),
                    maxLines = if (isDescExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlist.description.length > 45) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (isDescExpanded) "收起简介 ▲" else "展开完整歌单简介 ▼",
                        color = accentColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
}
