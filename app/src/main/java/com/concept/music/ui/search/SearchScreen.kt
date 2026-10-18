package com.concept.music.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.*
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptAmberAccent
import com.concept.music.ui.theme.ConceptMintAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.ConceptVioletAccent
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.SearchViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit = {},
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val cardColor = conceptFloatingCardColor()

    val query by viewModel.query.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val lastSearchedKeyword by viewModel.lastSearchedKeyword.collectAsState()

    val complexResult by viewModel.complexResult.collectAsState()
    val songResults by viewModel.songResults.collectAsState()
    val albumResults by viewModel.albumResults.collectAsState()
    val playlistResults by viewModel.playlistResults.collectAsState()

    val loading by viewModel.loading.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    BackHandler(enabled = true) {
        if (suggestions.isNotEmpty()) {
            viewModel.dismissSuggestions()
        } else if (hasSearched || query.isNotEmpty()) {
            viewModel.clearQuery()
        } else {
            onBack()
        }
    }

    var edgeDragStartX by remember { mutableFloatStateOf(0f) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !loadingMore && !loading) {
            viewModel.loadMore()
        }
    }

    ConceptStageBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            edgeDragStartX = offset.x
                        },
                        onDragEnd = { edgeDragStartX = 0f },
                        onDragCancel = { edgeDragStartX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            if (edgeDragStartX in 0f..120f && dragAmount > 0) {
                                if (change.position.x - edgeDragStartX > 150f) {
                                    edgeDragStartX = 0f
                                    if (suggestions.isNotEmpty()) {
                                        viewModel.dismissSuggestions()
                                    } else if (hasSearched || query.isNotEmpty()) {
                                        viewModel.clearQuery()
                                    } else {
                                        onBack()
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            // 顶部无边框搜索输入胶囊
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = {
                        Text(
                            text = "搜索歌曲、歌手、专辑、歌单...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    singleLine = true,
                    shape = CircleShape,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "搜索",
                            tint = ConceptPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearQuery) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "清空",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ConceptPrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = cardColor,
                        unfocusedContainerColor = cardColor,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            viewModel.search()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )
            }

            // 开放式极简分类导航栏（采用柔和弹性点击反馈，彻底消除直角矩形闪烁框）
            if (hasSearched && query.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        SearchTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val tabAccent = when (tab) {
                                SearchTab.COMPLEX -> ConceptPrimary
                                SearchTab.SONG -> ConceptMintAccent
                                SearchTab.ALBUM -> ConceptAmberAccent
                                SearchTab.PLAYLIST -> ConceptVioletAccent
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .softClickable { viewModel.selectTab(tab) }
                                    .padding(top = 4.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = if (isSelected) 15.5.sp else 14.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) tabAccent else Color.Transparent)
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // 1. 输入联想实时浮层
                    suggestions.isNotEmpty() && !hasSearched -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            items(suggestions) { text ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .softClickable {
                                            keyboardController?.hide()
                                            viewModel.search(text)
                                        }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = ConceptPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                )
                            }
                        }
                    }

                    // 2. 搜索中状态
                    loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = ConceptPrimary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "正在检索曲库...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // 3. 搜索结果展示
                    hasSearched && query.isNotEmpty() -> {
                        val displayKw = lastSearchedKeyword.ifBlank { query }
                        when (selectedTab) {
                            SearchTab.COMPLEX -> {
                                val isComplexEmpty = complexResult.songs.isEmpty() &&
                                    complexResult.artists.isEmpty() &&
                                    complexResult.albums.isEmpty() &&
                                    complexResult.playlists.isEmpty()
                                if (isComplexEmpty) {
                                    EmptySearchState(message = "未找到“$displayKw”的相关结果")
                                } else {
                                    ComplexSearchResultView(
                                        result = complexResult,
                                        keyword = displayKw,
                                        currentSong = currentSong,
                                        onSongClick = onSongClick,
                                        onSongMoreClick = onSongMoreClick,
                                        onAlbumClick = onAlbumClick,
                                        onPlaylistClick = onPlaylistClick,
                                        onArtistClick = onArtistClick,
                                        onSelectTab = { viewModel.selectTab(it) }
                                    )
                                }
                            }
                            SearchTab.SONG -> {
                                if (songResults.isEmpty()) {
                                    EmptySearchState(message = "未找到“$displayKw”的相关单曲")
                                } else {
                                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                        itemsIndexed(songResults) { index, song ->
                                            SongItemRow(
                                                song = song,
                                                orderIndex = index + 1,
                                                isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                                                onClick = { onSongClick(song, songResults) },
                                                onMoreClick = { onSongMoreClick(song) }
                                            )
                                        }
                                        if (loadingMore) {
                                            item {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(color = ConceptPrimary, modifier = Modifier.size(24.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            SearchTab.ALBUM -> {
                                if (albumResults.isEmpty()) {
                                    EmptySearchState(message = "未找到“$displayKw”的相关专辑")
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        items(albumResults) { album ->
                                            AlbumCardItem(album = album, onClick = { onAlbumClick(album.id) })
                                        }
                                        if (loadingMore) {
                                            item {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(color = ConceptAmberAccent, modifier = Modifier.size(24.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            SearchTab.PLAYLIST -> {
                                if (playlistResults.isEmpty()) {
                                    EmptySearchState(message = "未找到“$displayKw”的相关歌单")
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        items(playlistResults) { playlist ->
                                            PlaylistSearchCard(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
                                        }
                                        if (loadingMore) {
                                            item {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(color = ConceptVioletAccent, modifier = Modifier.size(24.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. 搜索历史与引导页
                    else -> {
                        if (searchHistory.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "搜索历史",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        IconButton(onClick = viewModel::clearHistory) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "清空历史",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                    ) {
                                        searchHistory.forEach { hist ->
                                            Surface(
                                                color = cardColor,
                                                shape = RoundedCornerShape(16.dp),
                                                shadowElevation = if (isDark) 0.dp else 1.dp,
                                                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                                ) {
                                                    Text(
                                                        text = hist,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        ),
                                                        modifier = Modifier
                                                            .softClickable {
                                                                keyboardController?.hide()
                                                                viewModel.search(hist)
                                                            }
                                                            .padding(vertical = 5.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .softClickable { viewModel.removeHistoryItem(hist) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = "删除",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = ConceptPrimary.copy(alpha = 0.65f),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "搜索你喜爱的音乐",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "支持单曲、歌手、专辑与歌单智能识别",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * 智能意图驱动的综合搜索结果页：
 * - 搜索单曲（如“勋章”）：置顶展示原版单曲及 Live/翻唱版本，底部可选展示原唱歌手（如鹿晗），绝不出现同名冷门用户歌手，并隐藏无关歌单/专辑
 * - 搜索歌手（如“鹿晗”）：置顶展示歌手名片，依次展示精选单曲、热门专辑与精选歌单
 * - 搜索专辑 / 歌单：优先置顶对应专辑或歌单列表，弱化无关模块
 */
@Composable
private fun ComplexSearchResultView(
    result: ComplexSearchResult,
    keyword: String,
    currentSong: Song?,
    onSongClick: (Song, List<Song>) -> Unit,
    onSongMoreClick: (Song) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onSelectTab: (SearchTab) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val elevatedColor = conceptElevatedSurfaceColor()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        when (result.intent) {
            SearchIntent.SONG -> {
                // 1. 单曲意图：首屏直接置顶该歌曲的原版及翻唱/现场版本（最多展示 12 首）
                if (result.songs.isNotEmpty()) {
                    item {
                        SongSectionBlock(
                            title = "单曲与演绎版本",
                            subtitle = "优先展示原唱母带及热门 Live / 翻唱",
                            songs = result.songs.take(12),
                            allSongs = result.songs,
                            currentSong = currentSong,
                            onSongClick = onSongClick,
                            onSongMoreClick = onSongMoreClick,
                            onViewAll = { onSelectTab(SearchTab.SONG) }
                        )
                    }
                }

                // 2. 原唱歌手关联小卡（仅在识别出真实原唱歌手时展示在歌曲列表下方，方便进入原唱主页）
                val origArtist = result.primaryArtist
                if (origArtist != null && origArtist.id.isNotBlank() && origArtist.id != "0") {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "原唱歌手",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                            )
                            ArtistHeroCard(
                                artist = origArtist,
                                roleBadge = "原唱",
                                isDark = isDark,
                                elevatedColor = elevatedColor,
                                onClick = { onArtistClick(origArtist.id) }
                            )
                        }
                    }
                }
            }

            SearchIntent.ALBUM -> {
                // 1. 专辑意图：优先置顶展示匹配的专辑
                if (result.albums.isNotEmpty()) {
                    item {
                        AlbumSectionBlock(
                            title = "匹配的专辑",
                            albums = result.albums.take(6),
                            primaryArtistName = result.primaryArtist?.name.orEmpty(),
                            onAlbumClick = onAlbumClick,
                            onViewAll = { onSelectTab(SearchTab.ALBUM) }
                        )
                    }
                }
                // 2. 专辑关联单曲
                if (result.songs.isNotEmpty()) {
                    item {
                        SongSectionBlock(
                            title = "专辑收录与相关单曲",
                            subtitle = null,
                            songs = result.songs.take(8),
                            allSongs = result.songs,
                            currentSong = currentSong,
                            onSongClick = onSongClick,
                            onSongMoreClick = onSongMoreClick,
                            onViewAll = { onSelectTab(SearchTab.SONG) }
                        )
                    }
                }
            }

            SearchIntent.PLAYLIST -> {
                // 1. 歌单意图：优先置顶展示匹配的精选歌单
                if (result.playlists.isNotEmpty()) {
                    item {
                        PlaylistGalleryBlock(
                            title = "匹配的精选歌单",
                            playlists = result.playlists.take(8),
                            elevatedColor = elevatedColor,
                            onPlaylistClick = onPlaylistClick,
                            onViewAll = { onSelectTab(SearchTab.PLAYLIST) }
                        )
                    }
                }
                // 2. 主题相关单曲
                if (result.songs.isNotEmpty()) {
                    item {
                        SongSectionBlock(
                            title = "相关精选单曲",
                            subtitle = null,
                            songs = result.songs.take(8),
                            allSongs = result.songs,
                            currentSong = currentSong,
                            onSongClick = onSongClick,
                            onSongMoreClick = onSongMoreClick,
                            onViewAll = { onSelectTab(SearchTab.SONG) }
                        )
                    }
                }
            }

            SearchIntent.ARTIST -> {
                // 1. 歌手意图（如“鹿晗”）：置顶歌手数字名片
                if (result.artists.isNotEmpty()) {
                    val artist = result.artists.first()
                    item {
                        ArtistHeroCard(
                            artist = artist,
                            roleBadge = "歌手",
                            isDark = isDark,
                            elevatedColor = elevatedColor,
                            onClick = { onArtistClick(artist.id) }
                        )
                    }
                }

                // 2. 歌手精选单曲
                if (result.songs.isNotEmpty()) {
                    item {
                        SongSectionBlock(
                            title = "精选单曲",
                            subtitle = null,
                            songs = result.songs.take(6),
                            allSongs = result.songs,
                            currentSong = currentSong,
                            onSongClick = onSongClick,
                            onSongMoreClick = onSongMoreClick,
                            onViewAll = { onSelectTab(SearchTab.SONG) }
                        )
                    }
                }

                // 3. 精选歌单
                if (result.playlists.isNotEmpty()) {
                    item {
                        PlaylistGalleryBlock(
                            title = "精选歌单",
                            playlists = result.playlists.take(6),
                            elevatedColor = elevatedColor,
                            onPlaylistClick = onPlaylistClick,
                            onViewAll = { onSelectTab(SearchTab.PLAYLIST) }
                        )
                    }
                }

                // 4. 热门专辑
                if (result.albums.isNotEmpty()) {
                    item {
                        AlbumSectionBlock(
                            title = "热门专辑",
                            albums = result.albums.take(4),
                            primaryArtistName = result.artists.firstOrNull()?.name.orEmpty(),
                            onAlbumClick = onAlbumClick,
                            onViewAll = { onSelectTab(SearchTab.ALBUM) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeroCard(
    artist: ArtistItem,
    roleBadge: String,
    isDark: Boolean,
    elevatedColor: Color,
    onClick: () -> Unit
) {
    val artistBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF15253C), Color(0xFF131B2B)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFE8F3FF), Color(0xFFF5F8FF)))
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(
            0.7.dp,
            if (isDark) Color.White.copy(alpha = 0.08f) else ConceptPrimary.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .softCardClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(artistBrush)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(elevatedColor),
                contentAlignment = Alignment.Center
            ) {
                if (artist.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = artist.avatarUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = artist.name,
                        tint = ConceptPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ConceptPrimary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = roleBadge,
                            color = ConceptPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = buildString {
                        if (artist.songCount > 0) append("单曲 ${artist.songCount}")
                        if (artist.albumCount > 0) {
                            if (isNotEmpty()) append("  ·  ")
                            append("专辑 ${artist.albumCount}")
                        }
                        if (isEmpty()) append("查看代表作与专辑")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "进入歌手主页",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SongSectionBlock(
    title: String,
    subtitle: String?,
    songs: List<Song>,
    allSongs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song, List<Song>) -> Unit,
    onSongMoreClick: (Song) -> Unit,
    onViewAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .softClickable(onClick = onViewAll)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "查看全部",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        songs.forEachIndexed { index, song ->
            SongItemRow(
                song = song,
                orderIndex = index + 1,
                isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                onClick = { onSongClick(song, allSongs) },
                onMoreClick = { onSongMoreClick(song) }
            )
        }
    }
}

@Composable
private fun PlaylistGalleryBlock(
    title: String,
    playlists: List<PlaylistItem>,
    elevatedColor: Color,
    onPlaylistClick: (String) -> Unit,
    onViewAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .softClickable(onClick = onViewAll)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "更多歌单",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(playlists) { pl ->
                Column(
                    modifier = Modifier
                        .width(134.dp)
                        .softCardClickable { onPlaylistClick(pl.id) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(134.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(elevatedColor)
                    ) {
                        AsyncImage(
                            model = pl.coverUrl,
                            contentDescription = pl.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (pl.trackCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.Black.copy(alpha = 0.58f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${pl.trackCount}首",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        text = pl.name,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp
                        ),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumSectionBlock(
    title: String,
    albums: List<AlbumItem>,
    primaryArtistName: String,
    onAlbumClick: (String) -> Unit,
    onViewAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .softClickable(onClick = onViewAll)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "更多专辑",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            albums.forEach { alb ->
                val fixedAlb = if ((alb.artist.isBlank() || alb.artist == "未知歌手" || alb.artist == "群星") && primaryArtistName.isNotBlank()) {
                    alb.copy(artist = primaryArtistName)
                } else alb
                AlbumCardItem(album = fixedAlb, onClick = { onAlbumClick(alb.id) })
            }
        }
    }
}

/**
 * 专辑搜索项（无外框通透行排版 + 柔和弹性反馈）
 */
@Composable
private fun AlbumCardItem(album: AlbumItem, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softClickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(conceptElevatedSurfaceColor()),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                val meta = buildList {
                    if (album.artist.isNotBlank() && album.artist != "未知歌手") add(album.artist)
                    if (album.songCount > 1) add("${album.songCount} 首") else add("专辑")
                    if (album.publishDate.isNotBlank()) add(album.publishDate)
                }.joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(ConceptAmberAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 74.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    }
}

/**
 * 歌单搜索项（无外框通透行排版 + 柔和弹性反馈）
 */
@Composable
private fun PlaylistSearchCard(playlist: PlaylistItem, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softClickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(conceptElevatedSurfaceColor())
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(ConceptVioletAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${playlist.creatorName} · ${playlist.trackCount} 首",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 74.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    }
}

@Composable
private fun EmptySearchState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
