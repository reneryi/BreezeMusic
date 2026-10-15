package com.concept.music.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.concept.music.data.model.AlbumItem
import com.concept.music.data.model.ArtistItem
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.ConceptStageBackground
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ArtistDetailScreen(
    artistId: String,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (String) -> Unit = {},
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {},
    isFollowed: Boolean = false,
    onToggleFollow: ((ArtistItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var artist by remember { mutableStateOf<ArtistItem?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AlbumItem>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 单曲, 1: 专辑, 2: 歌手百科
    var sortMethod by remember { mutableStateOf("hot") } // hot | new
    var songSearchQuery by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(true) }
    var songsLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isHeaderIntroExpanded by remember { mutableStateOf(false) }

    // 无限滚动分页状态（彻底突破原先 100 首歌与 50 张专辑的上限！）
    var songPage by remember { mutableIntStateOf(1) }
    var hasMoreSongs by remember { mutableStateOf(true) }
    var loadingMoreSongs by remember { mutableStateOf(false) }

    var albumPage by remember { mutableIntStateOf(1) }
    var hasMoreAlbums by remember { mutableStateOf(true) }
    var loadingMoreAlbums by remember { mutableStateOf(false) }

    fun loadAllData() {
        coroutineScope.launch {
            loading = true
            error = null
            songPage = 1
            albumPage = 1
            try {
                val (detail, songList, albumList) = coroutineScope {
                    val detailDef = async { runCatching { VipManager.music.artistDetail(artistId) }.getOrNull() }
                    val songsDef = async { runCatching { VipManager.music.artistSongs(artistId, sort = sortMethod, page = 1, pageSize = 100) }.getOrDefault(emptyList()) }
                    val albumsDef = async { runCatching { VipManager.music.artistAlbums(artistId, page = 1, pageSize = 50) }.getOrDefault(emptyList()) }
                    Triple(detailDef.await(), songsDef.await(), albumsDef.await())
                }
                val fallbackName = songList.firstOrNull()?.artist?.split("、", "/", "&", ",")?.firstOrNull()?.trim()
                    ?: albumList.firstOrNull()?.artist?.takeIf { it != "未知歌手" && it != "群星" }
                    ?: "歌手"
                val fallbackAvatar = songList.firstOrNull()?.coverUrl
                    ?.ifBlank { albumList.firstOrNull()?.coverUrl.orEmpty() }.orEmpty()

                val resolvedArtist = if (detail != null) {
                    detail.copy(
                        name = detail.name.takeIf { it.isNotBlank() && it != "歌手" } ?: fallbackName,
                        avatarUrl = detail.avatarUrl.ifBlank { fallbackAvatar },
                        songCount = maxOf(detail.songCount, songList.size),
                        albumCount = maxOf(detail.albumCount, albumList.size)
                    )
                } else {
                    ArtistItem(
                        id = artistId,
                        name = fallbackName,
                        avatarUrl = fallbackAvatar,
                        songCount = songList.size,
                        albumCount = albumList.size
                    )
                }
                artist = resolvedArtist
                songs = songList
                albums = albumList.map { alb ->
                    if (alb.artist.isBlank() || alb.artist == "未知歌手" || alb.artist == "群星") {
                        alb.copy(artist = resolvedArtist.name)
                    } else alb
                }
                hasMoreSongs = songList.size >= 50 && (resolvedArtist.songCount == 0 || songList.size < resolvedArtist.songCount)
                hasMoreAlbums = albumList.size >= 25 && (resolvedArtist.albumCount == 0 || albumList.size < resolvedArtist.albumCount)
                loading = false
            } catch (e: Exception) {
                error = e.message ?: "加载歌手信息失败"
                loading = false
            }
        }
    }

    fun loadMoreSongs() {
        if (loadingMoreSongs || !hasMoreSongs || loading || songsLoading) return
        loadingMoreSongs = true
        val nextPage = songPage + 1
        coroutineScope.launch {
            val nextBatch = runCatching {
                VipManager.music.artistSongs(artistId, sort = sortMethod, page = nextPage, pageSize = 100)
            }.getOrDefault(emptyList())
            if (nextBatch.isEmpty()) {
                hasMoreSongs = false
            } else {
                val existingKeys = songs.map { it.hash.ifBlank { it.id }.lowercase() }.toHashSet()
                val uniqueNew = nextBatch.filter { existingKeys.add(it.hash.ifBlank { it.id }.lowercase()) }
                if (uniqueNew.isEmpty()) {
                    hasMoreSongs = false
                } else {
                    songs = songs + uniqueNew
                    songPage = nextPage
                    val targetTotal = artist?.songCount ?: 0
                    hasMoreSongs = nextBatch.size >= 30 && (targetTotal == 0 || songs.size < targetTotal)
                }
            }
            loadingMoreSongs = false
        }
    }

    fun loadMoreAlbums() {
        if (loadingMoreAlbums || !hasMoreAlbums || loading) return
        loadingMoreAlbums = true
        val nextPage = albumPage + 1
        coroutineScope.launch {
            val nextBatch = runCatching {
                VipManager.music.artistAlbums(artistId, page = nextPage, pageSize = 50)
            }.getOrDefault(emptyList())
            if (nextBatch.isEmpty()) {
                hasMoreAlbums = false
            } else {
                val existingIds = albums.map { it.id }.toHashSet()
                val artistName = artist?.name.orEmpty()
                val uniqueNew = nextBatch.filter { existingIds.add(it.id) }.map { alb ->
                    if (alb.artist.isBlank() || alb.artist == "未知歌手" || alb.artist == "群星") {
                        alb.copy(artist = artistName.ifBlank { alb.artist })
                    } else alb
                }
                if (uniqueNew.isEmpty()) {
                    hasMoreAlbums = false
                } else {
                    albums = albums + uniqueNew
                    albumPage = nextPage
                    val targetTotal = artist?.albumCount ?: 0
                    hasMoreAlbums = nextBatch.size >= 20 && (targetTotal == 0 || albums.size < targetTotal)
                }
            }
            loadingMoreAlbums = false
        }
    }

    fun reloadSongsBySort(newSort: String) {
        if (sortMethod == newSort && songs.isNotEmpty()) return
        sortMethod = newSort
        songPage = 1
        coroutineScope.launch {
            songsLoading = true
            val list = runCatching {
                VipManager.music.artistSongs(artistId, sort = newSort, page = 1, pageSize = 100)
            }.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                songs = list
                val targetTotal = artist?.songCount ?: 0
                hasMoreSongs = list.size >= 50 && (targetTotal == 0 || list.size < targetTotal)
            }
            songsLoading = false
        }
    }

    // 滚动触底自动加载下一页单曲或专辑
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 4 && lastVisible >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMore, selectedTab, songSearchQuery) {
        if (shouldLoadMore) {
            if (selectedTab == 0 && songSearchQuery.isBlank()) {
                loadMoreSongs()
            } else if (selectedTab == 1) {
                loadMoreAlbums()
            }
        }
    }

    LaunchedEffect(artistId) {
        loadAllData()
    }

    val filteredSongs = remember(songs, songSearchQuery) {
        val q = songSearchQuery.trim()
        if (q.isBlank()) songs
        else songs.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }
    }

    val totalSongCountDisplay = maxOf(artist?.songCount ?: 0, songs.size)
    val totalAlbumCountDisplay = maxOf(artist?.albumCount ?: 0, albums.size)

    ConceptStageBackground(modifier = modifier.fillMaxSize()) {
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
                        text = artist?.name ?: "歌手主页",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { loadAllData() }) {
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
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 112.dp)
            ) {
                // 1. 歌手头部信息卡片
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF1E293B)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!artist?.avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = artist!!.avatarUrl,
                                            contentDescription = artist?.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(46.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = artist?.name ?: "歌手",
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (onToggleFollow != null && artist != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            FilledTonalButton(
                                                onClick = { artist?.let { onToggleFollow(it) } },
                                                shape = CircleShape,
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = if (isFollowed) MaterialTheme.colorScheme.surface else ConceptPrimary,
                                                    contentColor = if (isFollowed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                                                ),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text(
                                                    text = if (isFollowed) "已关注" else "+ 关注",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "单曲 $totalSongCountDisplay 首  ·  专辑 $totalAlbumCountDisplay 张",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = ConceptPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    if (!artist?.intro.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = artist!!.intro.replace("\n", " "),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                lineHeight = 17.sp
                                            ),
                                            maxLines = if (isHeaderIntroExpanded) 6 else 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable { selectedTab = 2 }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 栏目切换：单曲 / 专辑 / 歌手百科（显示真实总数，不再被第一页的 100/50 截断）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val tabs = listOf(
                                    "单曲 ($totalSongCountDisplay)",
                                    "专辑 ($totalAlbumCountDisplay)",
                                    "歌手百科"
                                )
                                tabs.forEachIndexed { idx, title ->
                                    val selected = selectedTab == idx
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) ConceptPrimary else Color.Transparent)
                                            .clickable { selectedTab = idx }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (loading && songs.isEmpty() && albums.isEmpty()) {
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

                when (selectedTab) {
                    0 -> {
                        // 单曲页：搜索过滤框 + 一键播放 + 最热/最新切换
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = songSearchQuery,
                                    onValueChange = { songSearchQuery = it },
                                    singleLine = true,
                                    placeholder = { Text("在已加载的 ${songs.size} 首单曲中搜索...", fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Search, contentDescription = null, tint = ConceptPrimary, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        if (songSearchQuery.isNotBlank()) {
                                            IconButton(onClick = { songSearchQuery = "" }) {
                                                Icon(Icons.Rounded.Close, contentDescription = "清除", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Button(
                                        onClick = {
                                            if (filteredSongs.isNotEmpty()) {
                                                onSongClick(filteredSongs.first(), filteredSongs)
                                            }
                                        },
                                        enabled = filteredSongs.isNotEmpty(),
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ConceptPrimary,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "播放全部 (${filteredSongs.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilterChip(
                                            selected = sortMethod == "hot",
                                            onClick = { reloadSongsBySort("hot") },
                                            label = { Text("最热", fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                                selectedLabelColor = ConceptPrimary
                                            ),
                                            border = null,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        FilterChip(
                                            selected = sortMethod == "new",
                                            onClick = { reloadSongsBySort("new") },
                                            label = { Text("最新", fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                                selectedLabelColor = ConceptPrimary
                                            ),
                                            border = null,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (songsLoading) {
                            item {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = ConceptPrimary
                                )
                            }
                        }

                        if (!loading && !songsLoading && filteredSongs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (songSearchQuery.isNotBlank()) "未找到匹配「$songSearchQuery」的单曲" else "暂无该歌手的单曲列表",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(filteredSongs, key = { idx, s -> "${s.id}_${s.hash}_$idx" }) { index, song ->
                                SongItemRow(
                                    song = song,
                                    orderIndex = index + 1,
                                    isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                                    onClick = { onSongClick(song, filteredSongs) },
                                    onMoreClick = { onSongMoreClick(song) }
                                )
                            }

                            // 底部自动/手动加载更多单曲提示
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        loadingMoreSongs -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    color = ConceptPrimary,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "正在加载更多单曲 (${songs.size}/$totalSongCountDisplay)...",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        hasMoreSongs -> {
                                            OutlinedButton(
                                                onClick = { loadMoreSongs() },
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(0.8.dp, ConceptPrimary.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "加载更多单曲（已展示 ${songs.size} / 共 $totalSongCountDisplay 首）",
                                                    fontSize = 12.sp,
                                                    color = ConceptPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        songs.isNotEmpty() -> {
                                            Text(
                                                text = "— 已加载全部 ${songs.size} 首单曲 —",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // 专辑页：展示歌手所有发行专辑，支持无限滚动分页加载全部专辑
                        if (!loading && albums.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无该歌手的专辑信息",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(albums, key = { it.id }) { albumItem ->
                                ArtistAlbumRow(
                                    album = albumItem,
                                    onClick = { onAlbumClick(albumItem.id) }
                                )
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        loadingMoreAlbums -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    color = ConceptPrimary,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "正在加载更多专辑 (${albums.size}/$totalAlbumCountDisplay)...",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        hasMoreAlbums -> {
                                            OutlinedButton(
                                                onClick = { loadMoreAlbums() },
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(0.8.dp, ConceptPrimary.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "加载更多专辑（已展示 ${albums.size} / 共 $totalAlbumCountDisplay 张）",
                                                    fontSize = 12.sp,
                                                    color = ConceptPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        albums.isNotEmpty() -> {
                                            Text(
                                                text = "— 已加载全部 ${albums.size} 张专辑 —",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // 歌手详情与完整多段落百科面板
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = ConceptPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "艺人百科与完整档案",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = ConceptPrimary
                                            )
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "艺人姓名：${artist?.name ?: "未知"}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "曲库收录：单曲 $totalSongCountDisplay 首 · 专辑 $totalAlbumCountDisplay 张",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                            if (songs.isNotEmpty()) {
                                                val topWorks = songs.take(3).joinToString(" / ") { "《${it.title}》" }
                                                Text(
                                                    text = "代表作品：$topWorks",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = ConceptPrimary
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    val fullIntro = artist?.intro?.trim().orEmpty()
                                    if (fullIntro.isBlank()) {
                                        Text(
                                            text = "暂无该歌手的详细百科介绍。",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    } else {
                                        val paragraphs = fullIntro.split("\n", "\r\n")
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                        paragraphs.forEach { para ->
                                            Text(
                                                text = para,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                                                    lineHeight = 24.sp,
                                                    fontSize = 14.5.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistAlbumRow(
    album: AlbumItem,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = album.coverUrl,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = ConceptPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val metaParts = buildList {
                    if (album.publishDate.isNotBlank()) add("发行于 ${album.publishDate}")
                    if (album.songCount > 1) add("${album.songCount} 首曲目")
                    if (album.company.isNotBlank()) add(album.company)
                }
                Text(
                    text = if (metaParts.isNotEmpty()) metaParts.joinToString(" · ") else album.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (album.intro.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = album.intro.replace("\n", " "),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "查看专辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
