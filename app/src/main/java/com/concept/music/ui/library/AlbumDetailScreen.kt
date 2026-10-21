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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.components.FloatingScrollToTopButton
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptAmberAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.ConceptFluidRefreshIcon
import com.concept.music.ui.theme.softClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val albumSessionCache = ConcurrentHashMap<String, Pair<AlbumItem?, List<Song>>>()

@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val cachedPair = remember(albumId) { albumSessionCache[albumId] }
    var album by remember(albumId) { mutableStateOf<AlbumItem?>(cachedPair?.first) }
    var songs by remember(albumId) { mutableStateOf<List<Song>>(cachedPair?.second ?: emptyList()) }
    var loading by remember(albumId) { mutableStateOf(cachedPair == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isIntroExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    fun loadData() {
        coroutineScope.launch {
            loading = true
            error = null
            try {
                val (resolvedAlbum, enrichedSongs) = withContext(Dispatchers.IO) {
                    val (detail, rawSongs) = coroutineScope {
                        val detailDef = async { runCatching { VipManager.music.albumDetail(albumId) }.getOrNull() }
                        val songsDef = async { runCatching { VipManager.music.albumSongs(albumId) }.getOrDefault(emptyList()) }
                        detailDef.await() to songsDef.await()
                    }
                    val firstSong = rawSongs.firstOrNull()
                    val resolved = when {
                        detail != null -> detail.copy(
                            name = detail.name.takeIf { it.isNotBlank() && it != "精选专辑" }
                                ?: firstSong?.album?.takeIf { it.isNotBlank() && it != "单曲" }
                                ?: detail.name.ifBlank { "精选专辑" },
                            artist = detail.artist.takeIf { it.isNotBlank() && it != "群星" && it != "未知歌手" }
                                ?: firstSong?.artist?.takeIf { it.isNotBlank() && it != "未知歌手" }
                                ?: detail.artist.ifBlank { "未知歌手" },
                            coverUrl = detail.coverUrl.ifBlank { firstSong?.coverUrl.orEmpty() },
                            songCount = if (rawSongs.isNotEmpty()) rawSongs.size else detail.songCount
                        )
                        firstSong != null -> AlbumItem(
                            id = albumId,
                            name = firstSong.album.takeIf { it.isNotBlank() && it != "单曲" } ?: "精选专辑",
                            artist = firstSong.artist.ifBlank { "未知歌手" },
                            coverUrl = firstSong.coverUrl,
                            songCount = rawSongs.size
                        )
                        else -> null
                    }
                    val enriched = rawSongs.map { s ->
                        s.copy(
                            album = s.album.takeIf { it.isNotBlank() && it != "单曲" } ?: resolved?.name.orEmpty(),
                            albumId = s.albumId.ifBlank { albumId },
                            coverUrl = s.coverUrl.ifBlank { resolved?.coverUrl.orEmpty() },
                            artist = s.artist.takeIf { it.isNotBlank() && it != "未知歌手" && it != "群星" } ?: resolved?.artist.orEmpty()
                        )
                    }
                    if (resolved != null || enriched.isNotEmpty()) {
                        albumSessionCache[albumId] = resolved to enriched
                    }
                    resolved to enriched
                }
                album = resolvedAlbum
                songs = enrichedSongs
                loading = false
            } catch (e: Exception) {
                error = e.message ?: "加载专辑信息失败"
                loading = false
            }
        }
    }

    LaunchedEffect(albumId) {
        loadData()
    }

    val filteredSongs = remember(songs, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) songs
        else songs.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true)
        }
    }

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
                        text = album?.name ?: "专辑详情",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    var albumRefreshSpin by remember { mutableIntStateOf(0) }
                    IconButton(onClick = {
                        albumRefreshSpin += 1
                        loadData()
                    }) {
                        ConceptFluidRefreshIcon(
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            isRefreshing = loading,
                            spinTrigger = albumRefreshSpin,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        ) { innerPadding ->
            val listState = rememberLazyListState()
            val showScrollToTop by remember {
                derivedStateOf {
                    filteredSongs.size > 30 && listState.firstVisibleItemIndex > 4
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 112.dp)
            ) {
                // 1. 开放式无框专辑头部（去除外层灰盒与简介内嵌框，营造大视野呼吸感）
                item {
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
                                    .size(106.dp)
                                    .shadow(8.dp, RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Brush.linearGradient(listOf(ConceptAmberAccent, ConceptPrimaryDark))),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!album?.coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = album!!.coverUrl,
                                        contentDescription = album?.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Album,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(46.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(18.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album?.name ?: "精选专辑",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = album?.artist ?: "未知歌手",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = ConceptAmberAccent,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val metaLine = buildList {
                                    if (!album?.publishDate.isNullOrBlank()) add(album!!.publishDate)
                                    if (!album?.company.isNullOrBlank()) add(album!!.company)
                                }.joinToString(" · ")
                                if (metaLine.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = metaLine,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // 开放式专辑简介（无内嵌盒子）
                        if (!album?.intro.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .softClickable { isIntroExpanded = !isIntroExpanded }
                            ) {
                                Text(
                                    text = album!!.intro.trim(),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                                        lineHeight = 19.sp
                                    ),
                                    maxLines = if (isIntroExpanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (isIntroExpanded) "收起简介 ▲" else "展开完整专辑介绍 ▼",
                                    color = ConceptAmberAccent,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

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
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(ConceptAmberAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
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

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }

                // 2. 专辑内歌曲搜索过滤框
                if (songs.size > 5) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            placeholder = { Text("搜索专辑曲目...", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = ConceptAmberAccent, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "清除", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ConceptAmberAccent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = conceptFloatingCardColor(),
                                unfocusedContainerColor = conceptFloatingCardColor()
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                // 3. 状态提示
                if (loading && songs.isEmpty()) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = ConceptAmberAccent
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

                if (!loading && error == null && filteredSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "未找到匹配「$searchQuery」的曲目" else "专辑暂无曲目信息",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 4. 歌曲列表
                itemsIndexed(filteredSongs, key = { idx, s -> "${s.id}_${s.hash}_$idx" }) { index, song ->
                    SongItemRow(
                        song = song,
                        orderIndex = index + 1,
                        isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                        onClick = { onSongClick(song, filteredSongs) },
                        onMoreClick = { onSongMoreClick(song) }
                    )
                }
            }

            FloatingScrollToTopButton(
                visible = showScrollToTop,
                accentColor = ConceptAmberAccent,
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
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
