package com.concept.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
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
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import kotlinx.coroutines.launch

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
    var album by remember { mutableStateOf<AlbumItem?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isIntroExpanded by remember { mutableStateOf(false) }

    fun loadData() {
        coroutineScope.launch {
            loading = true
            error = null
            try {
                val detail = VipManager.music.albumDetail(albumId)
                val songList = VipManager.music.albumSongs(albumId)
                album = detail
                songs = songList
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
                    text = album?.name ?: "专辑详情",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { loadData() }) {
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
            // 1. 专辑头部卡片
            item {
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
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF1E293B)))),
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
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album?.name ?: "精选专辑",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "歌手：${album?.artist ?: "群星"}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = ConceptPrimary,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!album?.publishDate.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "发行时间：${album?.publishDate}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                if (!album?.company.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "唱片公司：${album?.company}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // 专辑简介
                        if (!album?.intro.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = album!!.intro,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    lineHeight = 18.sp
                                ),
                                maxLines = if (isIntroExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { isIntroExpanded = !isIntroExpanded }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 播放全部按钮
                        Button(
                            onClick = {
                                if (songs.isNotEmpty()) {
                                    onSongClick(songs.first(), songs)
                                }
                            },
                            enabled = songs.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ConceptPrimary,
                                contentColor = Color(0xFF0F1115)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "播放全部 (${songs.size}首)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
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
                            text = "专辑暂无曲目信息",
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
                    onMoreClick = { onSongMoreClick(song) }
                )
            }
        }
    }
}
