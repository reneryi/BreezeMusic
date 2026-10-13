package com.concept.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var artist by remember { mutableStateOf<ArtistItem?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AlbumItem>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 单曲, 1: 专辑, 2: 详情百科
    var sortMethod by remember { mutableStateOf("hot") } // hot | new
    var loading by remember { mutableStateOf(true) }
    var songsLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isHeaderIntroExpanded by remember { mutableStateOf(false) }

    fun loadAllData() {
        coroutineScope.launch {
            loading = true
            error = null
            try {
                val (detail, songList, albumList) = coroutineScope {
                    val detailDef = async { runCatching { VipManager.music.artistDetail(artistId) }.getOrNull() }
                    val songsDef = async { runCatching { VipManager.music.artistSongs(artistId, sort = sortMethod, page = 1, pageSize = 100) }.getOrDefault(emptyList()) }
                    val albumsDef = async { runCatching { VipManager.music.artistAlbums(artistId, page = 1, pageSize = 50) }.getOrDefault(emptyList()) }
                    Triple(detailDef.await(), songsDef.await(), albumsDef.await())
                }
                val fallbackName = songList.firstOrNull()?.artist?.split("、", "/", "&", ",")?.firstOrNull()?.trim()
                    ?: albumList.firstOrNull()?.artist
                    ?: "歌手"
                val fallbackAvatar = songList.firstOrNull()?.coverUrl
                    ?.ifBlank { albumList.firstOrNull()?.coverUrl.orEmpty() }.orEmpty()

                artist = if (detail != null) {
                    detail.copy(
                        name = detail.name.takeIf { it.isNotBlank() && it != "歌手" } ?: fallbackName,
                        avatarUrl = detail.avatarUrl.ifBlank { fallbackAvatar },
                        songCount = if (detail.songCount > 0) detail.songCount else songList.size,
                        albumCount = if (detail.albumCount > 0) detail.albumCount else albumList.size
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
                songs = songList
                albums = albumList
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
        coroutineScope.launch {
            songsLoading = true
            val list = runCatching {
                VipManager.music.artistSongs(artistId, sort = newSort, page = 1, pageSize = 100)
            }.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                songs = list
            }
            songsLoading = false
        }
    }

    LaunchedEffect(artistId) {
        loadAllData()
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
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // 1. 歌手头部信息卡片
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
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
                                Text(
                                    text = artist?.name ?: "歌手",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val displaySongCount = artist?.songCount?.takeIf { it > 0 } ?: songs.size
                                val displayAlbumCount = artist?.albumCount?.takeIf { it > 0 } ?: albums.size
                                Text(
                                    text = "单曲 $displaySongCount 首  ·  专辑 $displayAlbumCount 张",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = ConceptPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                if (!artist?.intro.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = artist!!.intro,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                            lineHeight = 17.sp
                                        ),
                                        maxLines = if (isHeaderIntroExpanded) 6 else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { isHeaderIntroExpanded = !isHeaderIntroExpanded }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 栏目切换：单曲 / 专辑 / 百科
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val tabs = listOf(
                                "单曲 (${ if (songs.isNotEmpty()) songs.size else (artist?.songCount ?: 0) })",
                                "专辑 (${ if (albums.isNotEmpty()) albums.size else (artist?.albumCount ?: 0) })",
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
                                            color = if (selected) Color(0xFF0F1115) else MaterialTheme.colorScheme.onSurfaceVariant
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
                    // 单曲页：一键播放 + 最热/最新切换
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = {
                                    if (songs.isNotEmpty()) {
                                        onSongClick(songs.first(), songs)
                                    }
                                },
                                enabled = songs.isNotEmpty(),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ConceptPrimary,
                                    contentColor = Color(0xFF0F1115)
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
                                    text = "播放全部 (${songs.size})",
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

                    if (!loading && !songsLoading && songs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无该歌手的单曲列表",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        itemsIndexed(songs) { index, song ->
                            SongItemRow(
                                song = song,
                                orderIndex = index + 1,
                                isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                                onClick = { onSongClick(song, songs) },
                                onMoreClick = { onSongMoreClick(song) }
                            )
                        }
                    }
                }

                1 -> {
                    // 专辑页：展示歌手所有发行专辑，点击进入 AlbumDetailScreen
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
                    }
                }

                2 -> {
                    // 歌手详情与百科面板
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "艺人百科与档案",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = ConceptPrimary
                                    )
                                )
                                Text(
                                    text = "歌手名：${artist?.name ?: "未知"}  ·  单曲：${artist?.songCount?.takeIf { it > 0 } ?: songs.size} 首  ·  专辑：${artist?.albumCount?.takeIf { it > 0 } ?: albums.size} 张",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Text(
                                    text = artist?.intro?.ifBlank { "暂无该歌手的详细百科介绍。" } ?: "暂无该歌手的详细百科介绍。",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                        lineHeight = 22.sp
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

@Composable
private fun ArtistAlbumRow(
    album: AlbumItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val metaParts = buildList {
                if (album.publishDate.isNotBlank()) add(album.publishDate)
                if (album.songCount > 0) add("${album.songCount} 首")
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
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "查看专辑",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
