package com.concept.music.ui.library

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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.concept.music.ui.components.FloatingScrollToTopButton
import com.concept.music.ui.components.smoothFastScrollToItem
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptAmberAccent
import com.concept.music.ui.theme.ConceptCoralAccent
import com.concept.music.ui.theme.ConceptMintAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.ConceptVioletAccent
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.ConceptCategoryTabItem
import com.concept.music.ui.theme.ConceptFluidRefreshIcon
import com.concept.music.ui.theme.ConceptSlidingCategoryTabs
import com.concept.music.ui.theme.horizontalCategorySwipe
import com.concept.music.ui.theme.rememberCategorySwitchGraphicModifier
import com.concept.music.ui.theme.softClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌手主页会话内存缓存：
 * 确保用户在歌手主页切换至「专辑」Tab 点击进入某张专辑再返回时：
 * 1. 保持停留在「专辑」Tab，绝不重置回「单曲」页；
 * 2. 直接恢复已加载好的歌手详情、单曲与专辑列表，绝不重复触发网络请求。
 */
private data class ArtistPageSessionSnapshot(
    val artist: ArtistItem?,
    val songs: List<Song>,
    val albums: List<AlbumItem>,
    val selectedTab: Int,
    val sortMethod: String,
    val songPage: Int,
    val hasMoreSongs: Boolean,
    val albumPage: Int,
    val hasMoreAlbums: Boolean
)

private val artistPageSessionCache = ConcurrentHashMap<String, ArtistPageSessionSnapshot>()

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

    val cachedSnapshot = remember(artistId) { artistPageSessionCache[artistId] }

    var artist by remember(artistId) { mutableStateOf(cachedSnapshot?.artist) }
    var songs by remember(artistId) { mutableStateOf(cachedSnapshot?.songs ?: emptyList()) }
    var albums by remember(artistId) { mutableStateOf(cachedSnapshot?.albums ?: emptyList()) }
    var selectedTab by rememberSaveable(artistId) { mutableIntStateOf(cachedSnapshot?.selectedTab ?: 0) } // 0: 单曲, 1: 专辑, 2: 歌手百科
    var sortMethod by rememberSaveable(artistId) { mutableStateOf(cachedSnapshot?.sortMethod ?: "hot") } // hot | new
    var songSearchQuery by rememberSaveable(artistId) { mutableStateOf("") }

    var loading by remember(artistId) { mutableStateOf(cachedSnapshot == null) }
    var songsLoading by remember(artistId) { mutableStateOf(false) }
    var error by remember(artistId) { mutableStateOf<String?>(null) }

    var songPage by remember(artistId) { mutableIntStateOf(cachedSnapshot?.songPage ?: 1) }
    var hasMoreSongs by remember(artistId) { mutableStateOf(cachedSnapshot?.hasMoreSongs ?: true) }
    var loadingMoreSongs by remember(artistId) { mutableStateOf(false) }

    var albumPage by remember(artistId) { mutableIntStateOf(cachedSnapshot?.albumPage ?: 1) }
    var hasMoreAlbums by remember(artistId) { mutableStateOf(cachedSnapshot?.hasMoreAlbums ?: true) }
    var loadingMoreAlbums by remember(artistId) { mutableStateOf(false) }

    // 实时同步当前歌手主页状态至会话缓存，确保进入专辑详情再返回时零闪烁恢复
    LaunchedEffect(artistId, artist, songs, albums, selectedTab, sortMethod, songPage, hasMoreSongs, albumPage, hasMoreAlbums) {
        if (artist != null || songs.isNotEmpty() || albums.isNotEmpty()) {
            artistPageSessionCache[artistId] = ArtistPageSessionSnapshot(
                artist = artist,
                songs = songs,
                albums = albums,
                selectedTab = selectedTab,
                sortMethod = sortMethod,
                songPage = songPage,
                hasMoreSongs = hasMoreSongs,
                albumPage = albumPage,
                hasMoreAlbums = hasMoreAlbums
            )
        }
    }

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

    // 仅在首次进入（无会话缓存）时请求网络，从专辑页返回时直接复用缓存，绝不重新加载
    LaunchedEffect(artistId) {
        val existing = artistPageSessionCache[artistId]
        if (existing == null || (existing.songs.isEmpty() && existing.albums.isEmpty())) {
            loadAllData()
        } else {
            loading = false
        }
    }

    // 按需触底分页加载：彻底取代刚进入页面时的主线程死循环拉取风暴，保证进入退出歌手页丝滑满帧
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 4 && lastVisible >= total - 6
        }
    }

    LaunchedEffect(shouldLoadMore, selectedTab, loading, songsLoading) {
        if (!shouldLoadMore || loading || songsLoading) return@LaunchedEffect
        if (selectedTab == 0 && hasMoreSongs && !loadingMoreSongs && songPage < 25) {
            loadingMoreSongs = true
            withContext(Dispatchers.IO) {
                try {
                    val nextPage = songPage + 1
                    val nextBatch = runCatching {
                        VipManager.music.artistSongs(artistId, sort = sortMethod, page = nextPage, pageSize = 100)
                    }.getOrDefault(emptyList())
                    val existingKeys = songs.map { it.hash.ifBlank { it.id }.lowercase() }.toHashSet()
                    val uniqueNew = nextBatch.filter { existingKeys.add(it.hash.ifBlank { it.id }.lowercase()) }
                    withContext(Dispatchers.Main) {
                        if (uniqueNew.isEmpty()) {
                            hasMoreSongs = false
                        } else {
                            songs = songs + uniqueNew
                            songPage = nextPage
                            val targetTotal = artist?.songCount ?: 0
                            hasMoreSongs = nextBatch.size >= 30 && (targetTotal == 0 || songs.size < targetTotal)
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) { loadingMoreSongs = false }
                }
            }
        } else if (selectedTab == 1 && hasMoreAlbums && !loadingMoreAlbums && albumPage < 15) {
            loadingMoreAlbums = true
            withContext(Dispatchers.IO) {
                try {
                    val nextPage = albumPage + 1
                    val nextBatch = runCatching {
                        VipManager.music.artistAlbums(artistId, page = nextPage, pageSize = 50)
                    }.getOrDefault(emptyList())
                    val existingIds = albums.map { it.id }.toHashSet()
                    val artistName = artist?.name.orEmpty()
                    val uniqueNew = nextBatch.filter { existingIds.add(it.id) }.map { alb ->
                        if (alb.artist.isBlank() || alb.artist == "未知歌手" || alb.artist == "群星") {
                            alb.copy(artist = artistName.ifBlank { alb.artist })
                        } else alb
                    }
                    withContext(Dispatchers.Main) {
                        if (uniqueNew.isEmpty()) {
                            hasMoreAlbums = false
                        } else {
                            albums = albums + uniqueNew
                            albumPage = nextPage
                            val targetTotal = artist?.albumCount ?: 0
                            hasMoreAlbums = nextBatch.size >= 20 && (targetTotal == 0 || albums.size < targetTotal)
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) { loadingMoreAlbums = false }
                }
            }
        }
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
                    var artistRefreshSpin by remember { mutableIntStateOf(0) }
                    IconButton(onClick = {
                        artistRefreshSpin += 1
                        loadAllData()
                    }) {
                        ConceptFluidRefreshIcon(
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            isRefreshing = loading || songsLoading,
                            spinTrigger = artistRefreshSpin,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        ) { innerPadding ->
            val showScrollToTop by remember(selectedTab, filteredSongs.size) {
                derivedStateOf {
                    selectedTab == 0 && (
                        listState.firstVisibleItemIndex > 2 ||
                            (listState.firstVisibleItemIndex == 2 && listState.firstVisibleItemScrollOffset > 100)
                        )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalCategorySwipe(
                        currentIndex = selectedTab,
                        itemCount = 3,
                        onSelectIndex = { selectedTab = it }
                    ),
                contentPadding = PaddingValues(bottom = 112.dp)
            ) {
                // 1. 开放式无框歌手头部（消除厚重灰盒与胶囊分段栏，视觉开阔通透）
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .shadow(8.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(ConceptPrimary, ConceptPrimaryDark))),
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
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(18.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = artist?.name ?: "歌手",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 22.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (onToggleFollow != null && artist != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isFollowed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else ConceptCoralAccent,
                                            modifier = Modifier
                                                .softCardClickable { artist?.let { onToggleFollow(it) } }
                                        ) {
                                            Text(
                                                text = if (isFollowed) "已关注" else "+ 关注",
                                                color = if (isFollowed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "单曲 $totalSongCountDisplay",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = ConceptPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                    Text(
                                        text = "  ·  ",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "专辑 $totalAlbumCountDisplay",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = ConceptAmberAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                }
                                if (!artist?.intro.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = artist!!.intro.replace("\n", " "),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                                            lineHeight = 17.sp
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.softClickable { selectedTab = 2 }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 开放式极简栏目导航栏（单曲 / 专辑 / 歌手百科 + 左右滑动渐变彩色底部指示横条）
                        val artistTabItems = listOf(
                            ConceptCategoryTabItem("0", "单曲", totalSongCountDisplay.toString(), ConceptPrimary),
                            ConceptCategoryTabItem("1", "专辑", totalAlbumCountDisplay.toString(), ConceptAmberAccent),
                            ConceptCategoryTabItem("2", "歌手百科", "", ConceptVioletAccent)
                        )
                        ConceptSlidingCategoryTabs(
                            tabs = artistTabItems,
                            selectedKey = selectedTab.toString(),
                            onSelectTab = { keyStr -> selectedTab = keyStr.toIntOrNull() ?: 0 },
                            itemSpacing = 28.dp,
                            fontSize = 15.sp,
                            indicatorWidth = 22.dp
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
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
                        // 单曲页：极简搜索栏（不显示“在已加载的xx首单曲中搜索”）+ 开放式全播与最热/最新文字切换
                        item {
                            val tabAnimMod = rememberCategorySwitchGraphicModifier(selectedTab)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(tabAnimMod)
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = songSearchQuery,
                                    onValueChange = { songSearchQuery = it },
                                    singleLine = true,
                                    placeholder = { Text("搜索单曲或专辑名称...", fontSize = 13.sp) },
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
                                    shape = CircleShape,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ConceptPrimary,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = conceptFloatingCardColor(),
                                        unfocusedContainerColor = conceptFloatingCardColor()
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .softClickable(enabled = filteredSongs.isNotEmpty()) {
                                                if (filteredSongs.isNotEmpty()) {
                                                    onSongClick(filteredSongs.first(), filteredSongs)
                                                }
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(ConceptPrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "播放全部",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // 开放式最热 / 最新文字切换（带滑动横条）
                                    val artistSortTabs = remember {
                                        listOf(
                                            ConceptCategoryTabItem("hot", "最热", accentColor = ConceptPrimary),
                                            ConceptCategoryTabItem("new", "最新", accentColor = ConceptPrimary)
                                        )
                                    }
                                    ConceptSlidingCategoryTabs(
                                        tabs = artistSortTabs,
                                        selectedKey = sortMethod,
                                        onSelectTab = { reloadSongsBySort(it) },
                                        itemSpacing = 16.dp,
                                        fontSize = 13.sp,
                                        indicatorWidth = 16.dp,
                                        fillWidth = false
                                    )
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
                                val tabAnimMod = rememberCategorySwitchGraphicModifier(selectedTab)
                                Box(modifier = tabAnimMod) {
                                    SongItemRow(
                                        song = song,
                                        orderIndex = index + 1,
                                        isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                                        onClick = { onSongClick(song, filteredSongs) },
                                        onMoreClick = { onSongMoreClick(song) }
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // 专辑页：展示歌手所有发行专辑，后台静默全量加载，无外框通透行排版
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
                            itemsIndexed(albums, key = { idx, alb -> "art_alb_${alb.id}_$idx" }) { _, albumItem ->
                                val tabAnimMod = rememberCategorySwitchGraphicModifier(selectedTab)
                                Box(modifier = tabAnimMod) {
                                    ArtistAlbumRow(
                                        album = albumItem,
                                        onClick = { onAlbumClick(albumItem.id) }
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        // 歌手百科：开放式杂志排版，去除卡片套卡片
                        item {
                            val tabAnimMod = rememberCategorySwitchGraphicModifier(selectedTab)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(tabAnimMod)
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.5.dp)
                                            .height(16.dp)
                                            .clip(CircleShape)
                                            .background(ConceptVioletAccent)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "艺人档案",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "歌手姓名：${artist?.name ?: "未知"}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )

                                    if (songs.isNotEmpty()) {
                                        val topWorks = songs.take(3).joinToString(" / ") { "《${it.title}》" }
                                        Text(
                                            text = "代表作品：$topWorks",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = ConceptPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )

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
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
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

            FloatingScrollToTopButton(
                visible = showScrollToTop,
                onClick = {
                    coroutineScope.launch {
                        listState.smoothFastScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 104.dp)
            )
            }
        }
    }
}

/**
 * 歌手专辑列表行（开放式无外框排版 + 琥珀金圆点 + 极细内缩分割线）
 */
@Composable
private fun ArtistAlbumRow(
    album: AlbumItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .softClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(14.dp))
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
                        tint = ConceptAmberAccent,
                        modifier = Modifier.size(28.dp)
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
                    if (album.publishDate.isNotBlank()) add(album.publishDate)
                    if (album.songCount > 1) add("${album.songCount} 首")
                    if (album.company.isNotBlank()) add(album.company)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(ConceptAmberAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (metaParts.isNotEmpty()) metaParts.joinToString(" · ") else album.artist,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "查看专辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 76.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    }
}
