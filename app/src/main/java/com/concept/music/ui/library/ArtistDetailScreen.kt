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
import com.concept.music.data.model.ArtistItem
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import kotlinx.coroutines.launch

@Composable
fun ArtistDetailScreen(
    artistId: String,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var artist by remember { mutableStateOf<ArtistItem?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var sortMethod by remember { mutableStateOf("hot") } // hot | new
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isIntroExpanded by remember { mutableStateOf(false) }

    fun loadData() {
        coroutineScope.launch {
            loading = true
            error = null
            try {
                val detail = VipManager.music.artistDetail(artistId)
                val songList = VipManager.music.artistSongs(artistId, sort = sortMethod)
                artist = detail
                songs = songList
                loading = false
            } catch (e: Exception) {
                error = e.message ?: "加载歌手信息失败"
                loading = false
            }
        }
    }

    LaunchedEffect(artistId, sortMethod) {
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
                    text = artist?.name ?: "歌手主页",
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
            // 1. 歌手头部信息卡片
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
                                    .size(96.dp)
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
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artist?.name ?: "歌手",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "单曲 ${if (songs.isNotEmpty()) songs.size else (artist?.songCount ?: 0)} 首 · 专辑 ${artist?.albumCount ?: 0} 张",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        // 歌手简介
                        if (!artist?.intro.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = artist!!.intro,
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

                        // 排序筛选与一键播放
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "播放全量单曲 (${songs.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // 最热 / 最新 / 百科 切换 ([FUNC-05])
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = !isIntroExpanded && sortMethod == "hot",
                                    onClick = {
                                        isIntroExpanded = false
                                        sortMethod = "hot"
                                    },
                                    label = { Text("最热", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = ConceptPrimary
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                FilterChip(
                                    selected = !isIntroExpanded && sortMethod == "new",
                                    onClick = {
                                        isIntroExpanded = false
                                        sortMethod = "new"
                                    },
                                    label = { Text("最新", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = ConceptPrimary
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                FilterChip(
                                    selected = isIntroExpanded,
                                    onClick = { isIntroExpanded = !isIntroExpanded },
                                    label = { Text("百科", fontSize = 11.sp) },
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
            }

            if (isIntroExpanded) {
                // 歌手百科详情面板 ([FUNC-05])
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                                text = "歌手百科与艺术档案",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ConceptPrimary
                                )
                            )
                            Text(
                                text = "收录单曲：${artist?.songCount ?: songs.size} 首  ·  发行专辑：${artist?.albumCount ?: 0} 张",
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
            } else {
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
                                text = "该歌手暂无单曲",
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
}

