package com.concept.music.ui.library

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.Playlist
import com.concept.music.data.model.Song
import com.concept.music.service.AppPermissionManager
import com.concept.music.service.AppPermissionType
import com.concept.music.ui.components.AddToPlaylistDialog
import com.concept.music.ui.components.ArtworkDownloadCategory
import com.concept.music.ui.components.ArtworkSaveResult
import com.concept.music.ui.components.CreatePlaylistDialog
import com.concept.music.ui.components.FloatingScrollToTopButton
import com.concept.music.ui.components.HdArtworkPreviewDialog
import com.concept.music.ui.components.HdArtworkSaver
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.components.rememberProgressiveSongList
import com.concept.music.ui.components.smoothFastScrollToItem
import com.concept.music.ui.theme.ConceptCategoryTabItem
import com.concept.music.ui.theme.ConceptCoralAccent
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptFluidRefreshIcon
import com.concept.music.ui.theme.ConceptMintAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.ConceptSlidingCategoryTabs
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.LocalConceptAdaptiveInfo
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.horizontalCategorySwipe
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 歌单内排序模式（无门槛直接标配，不设 200 首门槛限制）
 */
private fun Song.selectionKey(): String = "${id}_${hash}"

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
    val adaptiveInfo = LocalConceptAdaptiveInfo.current
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
    var showPlaylistCoverPreview by remember { mutableStateOf(false) }

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

    val playingSongIndex = remember(filteredAndSortedSongs, currentSong) {
        if (currentSong == null) -1
        else filteredAndSortedSongs.indexOfFirst { s -> viewModel.isSameSong(s, currentSong) }
    }

    var lastKnownPlaylist by remember(playlistId) { mutableStateOf<Playlist?>(null) }
    val currentPlaylist = remember(playlists, playlistId, songs, lastKnownPlaylist) {
        val matched = when (playlistId) {
            "like" -> playlists.firstOrNull { it.isLiked } ?: Playlist("like", "我喜欢的音乐", isLiked = true)
            "history" -> Playlist("history", "最近播放")
            else -> {
                val directMatch = playlists.firstOrNull { it.matchesIdentifier(playlistId) }
                val fallbackMatch = if (directMatch == null && lastKnownPlaylist != null) {
                    playlists.firstOrNull { it.isSamePlaylistAs(lastKnownPlaylist) }
                } else null
                val chosen = directMatch ?: fallbackMatch
                if (chosen != null && lastKnownPlaylist != null) {
                    chosen.copy(
                        coverUrl = chosen.coverUrl.ifBlank { lastKnownPlaylist!!.coverUrl },
                        description = chosen.description.ifBlank { lastKnownPlaylist!!.description },
                        creatorName = chosen.creatorName.ifBlank { lastKnownPlaylist!!.creatorName },
                        trackCount = if (chosen.trackCount > 0) chosen.trackCount else lastKnownPlaylist!!.trackCount
                    )
                } else {
                    chosen ?: lastKnownPlaylist?.copy(isFavorite = false)
                }
            }
        }
        if (matched != null && matched.coverUrl.isBlank() && songs.isNotEmpty()) {
            matched.copy(coverUrl = songs.firstOrNull()?.coverUrl.orEmpty())
        } else {
            matched
        }
    }
    LaunchedEffect(currentPlaylist) {
        if (currentPlaylist != null) {
            lastKnownPlaylist = currentPlaylist
        }
    }

    val isCustomCreated = remember(currentPlaylist, playlistId) {
        currentPlaylist != null && currentPlaylist.isCreatedByMe && !currentPlaylist.isLiked && playlistId != "like" && playlistId != "history"
    }

    val isExternalPlaylist = remember(currentPlaylist, playlistId) {
        currentPlaylist != null && !currentPlaylist.isCreatedByMe && playlistId != "like" && playlistId != "history"
    }

    val effectivePlaylistCoverUrl = remember(currentPlaylist?.coverUrl, songs) {
        currentPlaylist?.coverUrl?.takeIf { it.isNotBlank() }
            ?: songs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty()
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

                        if (playlistId == "history" && songs.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearHistory) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "清空历史",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                )
                            }
                        }

                        if (isCustomCreated) {
                            IconButton(onClick = { showDeletePlaylistDialog = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "删除歌单",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }

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
                val displayedPlaylistSongs by rememberProgressiveSongList(
                    sourceSongs = filteredAndSortedSongs,
                    listState = listState,
                    resetKey = "${playlistId}_${sortMode.name}_$searchQuery",
                    initialBatchSize = 20,
                    microBatchSize = 15
                )
                val latestFilteredAndSortedSongs by rememberUpdatedState(filteredAndSortedSongs)
                val curPlaylistPlayingId = currentSong?.id
                val curPlaylistPlayingHash = currentSong?.hash.orEmpty()

                val onCollectToggle: () -> Unit = {
                    val targetPl = currentPlaylist
                    if (targetPl != null) {
                        if (targetPl.isFavorite) {
                            viewModel.deletePlaylist(targetPl) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            viewModel.collectPlaylist(targetPl) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                if (adaptiveInfo.useTwoPaneDetailLayout) {
                    // 平板横屏 / 宽屏双栏布局：左栏固定歌单信息卡片 + 右栏独立滚动曲目列表
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        PlaylistSideHeroPane(
                            playlist = currentPlaylist,
                            effectiveCoverUrl = effectivePlaylistCoverUrl,
                            songCount = songs.size,
                            isExternal = isExternalPlaylist,
                            onPlayAll = {
                                if (filteredAndSortedSongs.isNotEmpty()) {
                                    onSongClick(filteredAndSortedSongs.first(), filteredAndSortedSongs)
                                }
                            },
                            onCollectClick = onCollectToggle,
                            onCoverClick = {
                                if (effectivePlaylistCoverUrl.isNotBlank()) {
                                    showPlaylistCoverPreview = true
                                }
                            }
                        )

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(bottom = 128.dp)
                        ) {
                            item(key = "pl_toolbar", contentType = "pl_toolbar") {
                                PlaylistToolbarSection(
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchQuery = it },
                                    sortMode = sortMode,
                                    onSortModeChange = { sortMode = it },
                                    isMultiSelectMode = isMultiSelectMode,
                                    selectedSongIds = selectedSongIds,
                                    filteredAndSortedSongs = filteredAndSortedSongs,
                                    onToggleSelectAll = {
                                        selectedSongIds = if (selectedSongIds.size == filteredAndSortedSongs.size) {
                                            emptySet()
                                        } else {
                                            filteredAndSortedSongs.map { it.selectionKey() }.toSet()
                                        }
                                    },
                                    onPlaySelected = {
                                        val chosen = filteredAndSortedSongs.filter { selectedSongIds.contains(it.selectionKey()) }
                                        if (chosen.isNotEmpty()) {
                                            onSongClick(chosen.first(), chosen)
                                            isMultiSelectMode = false
                                            selectedSongIds = emptySet()
                                        }
                                    },
                                    onBatchAddSelected = { showBatchAddToPlaylistDialog = true }
                                )
                            }

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
                                            .padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (searchQuery.isNotBlank()) "未找到匹配「$searchQuery」的歌曲" else "歌单内暂无歌曲",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            itemsIndexed(
                                items = displayedPlaylistSongs,
                                key = { idx, s -> "${s.id}_${s.hash}_$idx" },
                                contentType = { _, _ -> "song_row" }
                            ) { index, song ->
                                val isCurrentTrack = curPlaylistPlayingId != null && (
                                    curPlaylistPlayingId == song.id ||
                                        (curPlaylistPlayingHash.isNotEmpty() && curPlaylistPlayingHash.equals(song.hash, ignoreCase = true))
                                    )
                                val handleMore: () -> Unit = {
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

                                if (isMultiSelectMode) {
                                    val songKey = song.selectionKey()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selectedSongIds.contains(songKey),
                                            onCheckedChange = { checked ->
                                                selectedSongIds = if (checked) {
                                                    selectedSongIds + songKey
                                                } else {
                                                    selectedSongIds - songKey
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = ConceptPrimary),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                        Box(modifier = Modifier.weight(1f)) {
                                            SongItemRow(
                                                song = song,
                                                orderIndex = index + 1,
                                                isPlaying = isCurrentTrack,
                                                onClick = {
                                                    selectedSongIds = if (selectedSongIds.contains(songKey)) {
                                                        selectedSongIds - songKey
                                                    } else {
                                                        selectedSongIds + songKey
                                                    }
                                                },
                                                onMoreClick = handleMore
                                            )
                                        }
                                    }
                                } else {
                                    SongItemRow(
                                        song = song,
                                        orderIndex = index + 1,
                                        isPlaying = isCurrentTrack,
                                        onClick = {
                                            onSongClick(song, latestFilteredAndSortedSongs)
                                        },
                                        onMoreClick = handleMore
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // 手机竖屏或平板竖屏/分屏单栏模式（平板竖屏下居中受控最大宽度）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .then(
                                    if (adaptiveInfo.singleColumnMaxWidth != Dp.Unspecified) {
                                        Modifier.widthIn(max = adaptiveInfo.singleColumnMaxWidth)
                                    } else Modifier
                                )
                                .fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 128.dp)
                        ) {
                            // 1. 歌单头部 Header（点击封面弹出高清预览与保存）
                            item(key = "pl_header", contentType = "pl_header") {
                                PlaylistHeaderCard(
                                    playlist = currentPlaylist,
                                    effectiveCoverUrl = effectivePlaylistCoverUrl,
                                    songCount = songs.size,
                                    isExternal = isExternalPlaylist,
                                    onPlayAll = {
                                        if (filteredAndSortedSongs.isNotEmpty()) {
                                            onSongClick(filteredAndSortedSongs.first(), filteredAndSortedSongs)
                                        }
                                    },
                                    onCollectClick = onCollectToggle,
                                    onCoverClick = {
                                        if (effectivePlaylistCoverUrl.isNotBlank()) {
                                            showPlaylistCoverPreview = true
                                        }
                                    }
                                )
                            }

                            // 2. 歌单内搜索 + 排序切换 + 批量多选栏
                            item(key = "pl_toolbar", contentType = "pl_toolbar") {
                                PlaylistToolbarSection(
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchQuery = it },
                                    sortMode = sortMode,
                                    onSortModeChange = { sortMode = it },
                                    isMultiSelectMode = isMultiSelectMode,
                                    selectedSongIds = selectedSongIds,
                                    filteredAndSortedSongs = filteredAndSortedSongs,
                                    onToggleSelectAll = {
                                        selectedSongIds = if (selectedSongIds.size == filteredAndSortedSongs.size) {
                                            emptySet()
                                        } else {
                                            filteredAndSortedSongs.map { it.selectionKey() }.toSet()
                                        }
                                    },
                                    onPlaySelected = {
                                        val chosen = filteredAndSortedSongs.filter { selectedSongIds.contains(it.selectionKey()) }
                                        if (chosen.isNotEmpty()) {
                                            onSongClick(chosen.first(), chosen)
                                            isMultiSelectMode = false
                                            selectedSongIds = emptySet()
                                        }
                                    },
                                    onBatchAddSelected = { showBatchAddToPlaylistDialog = true }
                                )
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
                            itemsIndexed(
                                items = displayedPlaylistSongs,
                                key = { idx, s -> "${s.id}_${s.hash}_$idx" },
                                contentType = { _, _ -> "song_row" }
                            ) { index, song ->
                                val isCurrentTrack = curPlaylistPlayingId != null && (
                                    curPlaylistPlayingId == song.id ||
                                        (curPlaylistPlayingHash.isNotEmpty() && curPlaylistPlayingHash.equals(song.hash, ignoreCase = true))
                                    )
                                val handleMore: () -> Unit = {
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

                                if (isMultiSelectMode) {
                                    val songKey = song.selectionKey()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selectedSongIds.contains(songKey),
                                            onCheckedChange = { checked ->
                                                selectedSongIds = if (checked) {
                                                    selectedSongIds + songKey
                                                } else {
                                                    selectedSongIds - songKey
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = ConceptPrimary),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                        Box(modifier = Modifier.weight(1f)) {
                                            SongItemRow(
                                                song = song,
                                                orderIndex = index + 1,
                                                isPlaying = isCurrentTrack,
                                                onClick = {
                                                    selectedSongIds = if (selectedSongIds.contains(songKey)) {
                                                        selectedSongIds - songKey
                                                    } else {
                                                        selectedSongIds + songKey
                                                    }
                                                },
                                                onMoreClick = handleMore
                                            )
                                        }
                                    }
                                } else {
                                    SongItemRow(
                                        song = song,
                                        orderIndex = index + 1,
                                        isPlaying = isCurrentTrack,
                                        onClick = {
                                            onSongClick(song, latestFilteredAndSortedSongs)
                                        },
                                        onMoreClick = handleMore
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 右下角悬浮按钮组
            val showScrollToTop by remember(filteredAndSortedSongs.size) {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 2 ||
                        (listState.firstVisibleItemIndex == 2 && listState.firstVisibleItemScrollOffset > 100)
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
                            listState.smoothFastScrollToItem(0)
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
                                    val headerOffset = if (adaptiveInfo.useTwoPaneDetailLayout) 1 else 2
                                    val targetLazyIndex = (headerOffset + playingSongIndex - 2).coerceAtLeast(0)
                                    listState.smoothFastScrollToItem(targetLazyIndex)
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

            if (showPlaylistCoverPreview && effectivePlaylistCoverUrl.isNotBlank()) {
                HdArtworkPreviewDialog(
                    artworkUrl = effectivePlaylistCoverUrl,
                    title = currentPlaylist?.title ?: "精选歌单",
                    subtitle = currentPlaylist?.creatorName.orEmpty(),
                    category = ArtworkDownloadCategory.PLAYLIST_COVER,
                    iconOnlySaveButton = false,
                    accentColor = ConceptPrimary,
                    onDismiss = { showPlaylistCoverPreview = false }
                )
            }
        }
    }

    // 批量添加到歌单弹窗
    if (showBatchAddToPlaylistDialog) {
        val chosenSongs = filteredAndSortedSongs.filter { selectedSongIds.contains(it.selectionKey()) }
        val lead = chosenSongs.firstOrNull()
        if (lead != null) {
            AddToPlaylistDialog(
                song = lead,
                playlists = playlists,
                onSelectPlaylist = { targetPl ->
                    showBatchAddToPlaylistDialog = false
                    viewModel.addSongsToPlaylistBatch(targetPl.listId, chosenSongs, targetPl.title) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
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

    // 单曲更多操作备用 BottomSheet
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
                            val appCtx = context.applicationContext
                            if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.STORAGE_EXPORT)) {
                                Toast.makeText(
                                    appCtx,
                                    "已在权限管理中关闭「存储读写与相册导出」权限，请先在设置-权限管理中开启",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                coroutineScope.launch {
                                    val res = withContext(Dispatchers.IO) {
                                        HdArtworkSaver.saveArtworkToGallery(
                                            context = appCtx,
                                            rawUrl = s.coverUrl,
                                            category = ArtworkDownloadCategory.SONG_COVER,
                                            title = s.title,
                                            subtitle = s.artist,
                                            songForFallback = s
                                        )
                                    }
                                    val msg = when (res) {
                                        is ArtworkSaveResult.Success -> res.message
                                        is ArtworkSaveResult.PermissionDisabled -> res.message
                                        is ArtworkSaveResult.Failure -> res.message
                                    }
                                    Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = ConceptCyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "保存歌曲封面",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

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
            onConfirmWithCallback = { name, onComplete ->
                val pendingSingle = songToAddToPlaylist
                val pendingBatch = if (isMultiSelectMode) songs.filter { it.id in selectedSongIds } else emptyList()
                viewModel.createPlaylist(
                    name = name,
                    onCreatedPlaylist = { createdPl ->
                        if (createdPl != null) {
                            when {
                                pendingSingle != null -> {
                                    songToAddToPlaylist = null
                                    viewModel.addSongToPlaylist(createdPl.listId, pendingSingle, createdPl.title) { _, addMsg ->
                                        Toast.makeText(context, addMsg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                pendingBatch.isNotEmpty() -> {
                                    isMultiSelectMode = false
                                    selectedSongIds = emptySet()
                                    viewModel.addSongsToPlaylistBatch(createdPl.listId, pendingBatch, createdPl.title) { _, addMsg ->
                                        Toast.makeText(context, addMsg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    onResult = { success, msg ->
                        if (!success || (pendingSingle == null && pendingBatch.isEmpty())) {
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        onComplete(success)
                    }
                )
            }
        )
    }
}

@Composable
private fun PlaylistToolbarSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortMode: PlaylistSortMode,
    onSortModeChange: (PlaylistSortMode) -> Unit,
    isMultiSelectMode: Boolean,
    selectedSongIds: Set<String>,
    filteredAndSortedSongs: List<Song>,
    onToggleSelectAll: () -> Unit,
    onPlaySelected: () -> Unit,
    onBatchAddSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
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
                    IconButton(onClick = { onSearchQueryChange("") }) {
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
                        PlaylistSortMode.entries.getOrNull(nextIdx)?.let { onSortModeChange(it) }
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
                    PlaylistSortMode.entries.find { it.name == key }?.let { onSortModeChange(it) }
                },
                itemSpacing = 16.dp,
                fontSize = 12.5.sp,
                indicatorWidth = 16.dp,
                fillWidth = false
            )
        }

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
                            onClick = onToggleSelectAll,
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
                            onClick = onPlaySelected,
                            enabled = selectedSongIds.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("播放选中", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = onBatchAddSelected,
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

@Composable
private fun PlaylistSideHeroPane(
    playlist: Playlist?,
    effectiveCoverUrl: String,
    songCount: Int,
    isExternal: Boolean,
    onPlayAll: () -> Unit,
    onCollectClick: () -> Unit,
    onCoverClick: () -> Unit
) {
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

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = conceptFloatingCardColor(),
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .padding(bottom = 96.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(172.dp)
                    .shadow(14.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(colors = gradientColors))
                    .softCardClickable(
                        shape = RoundedCornerShape(26.dp),
                        enabled = effectiveCoverUrl.isNotBlank(),
                        onClick = onCoverClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (effectiveCoverUrl.isNotBlank()) {
                    AsyncImage(
                        model = effectiveCoverUrl,
                        contentDescription = playlist?.title,
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
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = playlist?.title ?: "精选歌单",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (!playlist?.creatorName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = playlist!!.creatorName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            if (songCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "共 $songCount 首曲目",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .softCardClickable(
                        shape = RoundedCornerShape(22.dp),
                        enabled = songCount > 0,
                        onClick = onPlayAll
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "播放全部",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            if (isExternal && playlist != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val isFavorite = playlist.isFavorite
                val favScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isFavorite) 1.02f else 1f,
                    animationSpec = com.concept.music.ui.theme.ConceptMotion.expressiveSpring(),
                    label = "sideHeroCollectScale"
                )
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isFavorite) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .graphicsLayer {
                            scaleX = favScale
                            scaleY = favScale
                        }
                        .softCardClickable(
                            shape = RoundedCornerShape(18.dp),
                            onClick = onCollectClick
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                            contentDescription = if (isFavorite) "已收藏（再次点击取消收藏）" else "收藏歌单",
                            tint = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFavorite) "已收藏" else "收藏",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!playlist?.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = playlist!!.description.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeaderCard(
    playlist: Playlist?,
    effectiveCoverUrl: String = playlist?.coverUrl.orEmpty(),
    songCount: Int,
    isExternal: Boolean = false,
    onPlayAll: () -> Unit,
    onCollectClick: () -> Unit = {},
    onCoverClick: () -> Unit = {}
) {
    var isDescExpanded by remember { mutableStateOf(false) }
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
                    .background(Brush.linearGradient(colors = gradientColors))
                    .softCardClickable(
                        shape = RoundedCornerShape(20.dp),
                        enabled = effectiveCoverUrl.isNotBlank(),
                        onClick = onCoverClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (effectiveCoverUrl.isNotBlank()) {
                    AsyncImage(
                        model = effectiveCoverUrl,
                        contentDescription = playlist?.title,
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
                        val favScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isFavorite) 1.04f else 1f,
                            animationSpec = com.concept.music.ui.theme.ConceptMotion.expressiveSpring(),
                            label = "headerCollectScale"
                        )
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isFavorite) {
                                accentColor.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            },
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = favScale
                                    scaleY = favScale
                                }
                                .softCardClickable(
                                    shape = RoundedCornerShape(18.dp),
                                    onClick = onCollectClick
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                    contentDescription = if (isFavorite) "已收藏（再次点击取消收藏）" else "收藏歌单",
                                    tint = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.5.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isFavorite) "已收藏" else "收藏",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
