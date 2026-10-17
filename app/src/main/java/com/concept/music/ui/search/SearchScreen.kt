package com.concept.music.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

            // 开放式极简分类导航栏（去除 4 个胶囊按钮外壳，采用底部多彩指示条）
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
                                    .clickable { viewModel.selectTab(tab) }
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
                                        .clickable {
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
                                                            .clickable {
                                                                keyboardController?.hide()
                                                                viewModel.search(hist)
                                                            }
                                                            .padding(vertical = 5.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .clickable { viewModel.removeHistoryItem(hist) },
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
                                        text = "支持单曲、歌手、专辑与歌单",
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
 * 综合搜索结果页（开放式排版：删除歌手横栏多余的「歌手主页」按钮；歌单与专辑去除独立卡片盒）
 */
@Composable
private fun ComplexSearchResultView(
    result: ComplexSearchResult,
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
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // 1. 歌手直达横栏（删除右侧多余的「歌手主页」蓝色按钮，点击整行即进主页）
        if (result.artists.isNotEmpty()) {
            val artist = result.artists.first()
            item {
                val artistBrush = if (isDark) {
                    Brush.linearGradient(listOf(Color(0xFF15253C), Color(0xFF131B2B)))
                } else {
                    Brush.linearGradient(listOf(Color(0xFFE8F3FF), Color(0xFFF5F8FF)))
                }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onArtistClick(artist.id) }
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
                                Text(
                                    text = "歌手",
                                    color = ConceptPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
        }

        // 2. 精选单曲
        if (result.songs.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "精选单曲",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onSelectTab(SearchTab.SONG) }
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

                    result.songs.take(6).forEachIndexed { index, song ->
                        SongItemRow(
                            song = song,
                            orderIndex = index + 1,
                            isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                            onClick = { onSongClick(song, result.songs) },
                            onMoreClick = { onSongMoreClick(song) }
                        )
                    }
                }
            }
        }

        // 3. 精选歌单（画廊式无外框封面排布，告别外层圆角卡片盒）
        if (result.playlists.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "精选歌单",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onSelectTab(SearchTab.PLAYLIST) }
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
                        items(result.playlists.take(6)) { pl ->
                            Column(
                                modifier = Modifier
                                    .width(134.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onPlaylistClick(pl.id) }
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
        }

        // 4. 热门专辑（无外框开放式行排版）
        if (result.albums.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "热门专辑",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onSelectTab(SearchTab.ALBUM) }
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
                        val primaryArtistName = result.artists.firstOrNull()?.name.orEmpty()
                        result.albums.take(4).forEach { alb ->
                            val fixedAlb = if ((alb.artist.isBlank() || alb.artist == "未知歌手" || alb.artist == "群星") && primaryArtistName.isNotBlank()) {
                                alb.copy(artist = primaryArtistName)
                            } else alb
                            AlbumCardItem(album = fixedAlb, onClick = { onAlbumClick(alb.id) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * 专辑搜索项（无外框通透行排版 + 极细内缩分割线）
 */
@Composable
private fun AlbumCardItem(album: AlbumItem, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
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
 * 歌单搜索项（无外框通透行排版 + 极细内缩分割线）
 */
@Composable
private fun PlaylistSearchCard(playlist: PlaylistItem, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
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
