package com.concept.music.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
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
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.VipGold
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

    // 核心返回逻辑拦截：联想 -> 搜索结果 -> 退出页面
    BackHandler(enabled = true) {
        if (suggestions.isNotEmpty()) {
            viewModel.dismissSuggestions()
        } else if (hasSearched || query.isNotEmpty()) {
            viewModel.clearQuery()
        } else {
            onBack()
        }
    }

    // 边缘手势起点记录，实现自然顺畅的屏幕左边缘右划返回
    var edgeDragStartX by remember { mutableFloatStateOf(0f) }

    // 触底自动加载
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Color(0xFF0F1115))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        edgeDragStartX = offset.x
                    },
                    onDragEnd = { edgeDragStartX = 0f },
                    onDragCancel = { edgeDragStartX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        // 仅当触控起点位于屏幕最左边缘区域 (<= 40dp 约 120px) 且向右滑动时生效
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
        // 顶部搜索输入栏：去除返回按钮（滑动返回），去除右上角“搜索”按钮（键盘一键搜索），全宽呼吸胶囊形态
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
                        color = Color.White.copy(alpha = 0.42f)
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
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ConceptPrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                    focusedContainerColor = Color(0xFF191D26),
                    unfocusedContainerColor = Color(0xFF191D26),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
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
                    .height(52.dp)
            )
        }

        // 若已执行搜索且输入非空，展示现代流体分类 Tab（支持小屏横向平滑滚动防截断）
        if (hasSearched && query.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SearchTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val animatedBgColor by animateColorAsState(
                        targetValue = if (isSelected) ConceptPrimary else Color(0xFF191D26),
                        animationSpec = tween(durationMillis = 180),
                        label = "search_tab_bg"
                    )
                    val animatedTextColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF091215) else Color.White.copy(alpha = 0.72f),
                        animationSpec = tween(durationMillis = 180),
                        label = "search_tab_text"
                    )

                    Surface(
                        color = animatedBgColor,
                        shape = RoundedCornerShape(16.dp),
                        border = if (!isSelected) BorderStroke(0.6.dp, Color.White.copy(alpha = 0.08f)) else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.selectTab(tab) }
                    ) {
                        Text(
                            text = tab.label,
                            color = animatedTextColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // 页面主体
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                // 1. 输入联想实时浮层（仅在未搜索完成且有联想内容时显示）
                suggestions.isNotEmpty() && !hasSearched -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F1115))
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
                                    tint = ConceptPrimary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
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
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "正在检索酷狗概念版曲库...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }

                // 3. 搜索结果展示（现代设计感，全量空状态防护）
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
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                                CircularProgressIndicator(color = ConceptPrimary, modifier = Modifier.size(24.dp))
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
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                                CircularProgressIndicator(color = ConceptPrimary, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. 空状态：清除了假数据，删除了热门搜索，仅在有真实历史时展示历史
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
                                            color = Color.White
                                        )
                                    )
                                    IconButton(onClick = viewModel::clearHistory) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "清空历史",
                                            tint = Color.White.copy(alpha = 0.5f),
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
                                            color = Color(0xFF191D26),
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.08f)),
                                            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                            ) {
                                                Text(
                                                    text = hist,
                                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f)),
                                                    modifier = Modifier
                                                        .clickable {
                                                            keyboardController?.hide()
                                                            viewModel.search(hist)
                                                        }
                                                        .padding(vertical = 6.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .clickable { viewModel.removeHistoryItem(hist) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "删除",
                                                        tint = Color.White.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 历史记录为空且删除了热门搜索后的现代极简音乐探索引导页
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
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF181C25),
                                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.size(76.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = ConceptPrimary.copy(alpha = 0.85f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = "搜索你喜爱的音乐",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "支持单曲、歌手、专辑与歌单",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.42f)
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

/**
 * 现代设计感综合搜索结果页
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. 歌手专属现代流光卡片
        if (result.artists.isNotEmpty()) {
            val artist = result.artists.first()
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF161A24),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.10f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onArtistClick(artist.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1E2332), Color(0xFF141722))
                                )
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF232936))
                                .border(1.5.dp, ConceptPrimary.copy(alpha = 0.45f), CircleShape),
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
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = ConceptPrimary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "歌手",
                                        color = ConceptPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            Text(
                                text = buildString {
                                    if (artist.songCount > 0) append("单曲 ${artist.songCount}")
                                    if (artist.albumCount > 0) {
                                        if (isNotEmpty()) append(" · ")
                                        append("专辑 ${artist.albumCount}")
                                    }
                                    if (isEmpty()) append("点击进入歌手主页")
                                },
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.55f))
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = ConceptPrimary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onArtistClick(artist.id) }
                        ) {
                            Text(
                                text = "歌手主页",
                                color = Color(0xFF091215),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
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
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "查看全部 >",
                            color = ConceptPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onSelectTab(SearchTab.SONG) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

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

        // 3. 精选歌单
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
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "更多歌单 >",
                            color = ConceptPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onSelectTab(SearchTab.PLAYLIST) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(result.playlists.take(6)) { pl ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF181C26),
                                border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .width(136.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onPlaylistClick(pl.id) }
                            ) {
                                Column(modifier = Modifier.padding(9.dp)) {
                                    Box {
                                        AsyncImage(
                                            model = pl.coverUrl,
                                            contentDescription = pl.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(118.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                        if (pl.trackCount > 0) {
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.55f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(6.dp)
                                            ) {
                                                Text(
                                                    text = "${pl.trackCount}首",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = pl.name,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. 热门专辑
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
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "更多专辑 >",
                            color = ConceptPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onSelectTab(SearchTab.ALBUM) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        result.albums.take(3).forEach { alb ->
                            AlbumCardItem(album = alb, onClick = { onAlbumClick(alb.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCardItem(album: AlbumItem, onClick: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF181C26),
        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222631)),
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
                        color = Color.White,
                        fontSize = 14.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${album.artist} · ${if (album.songCount > 0) "${album.songCount}首曲目" else "专辑"}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.58f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (album.publishDate.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "发行时间：${album.publishDate}",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistSearchCard(playlist: PlaylistItem, onClick: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF181C26),
        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "创建者：${playlist.creatorName} · ${playlist.trackCount}首",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.58f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
            )
        }
    }
}
