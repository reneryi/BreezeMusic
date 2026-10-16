package com.concept.music.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.Song
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

/**
 * 精选场景电台独立页面 (RadioScreen)
 * - 支持全部分类电台选择
 * - 支持「换一批全新曲目」（推进电台游标，获取全新歌曲而非单纯打乱顺序）
 * - 支持「继续漫游 +20 首」（在现有电台队列末尾追加新一批曲目，曲目数量随漫游不断增长）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    viewModel: HomeViewModel,
    initialChannelId: String = "",
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalIsDarkTheme.current

    val fmChannels by viewModel.fmChannels.collectAsState()
    val dailySongs by viewModel.dailyRecommendSongs.collectAsState()

    var selectedChannelId by remember(initialChannelId, fmChannels) {
        mutableStateOf(
            initialChannelId.takeIf { it.isNotBlank() }
                ?: fmChannels.firstOrNull()?.id
                ?: "1"
        )
    }

    val currentChannel = remember(fmChannels, selectedChannelId) {
        fmChannels.firstOrNull { it.id == selectedChannelId }
            ?: fmChannels.firstOrNull()
            ?: FmChannel(id = selectedChannelId, name = "随心漫游电台", description = "全天候智能音乐伴听", category = "精选")
    }

    var selectedCategory by remember { mutableStateOf("全部") }
    val categories = remember(fmChannels) {
        listOf("全部") + fmChannels.map { it.category.ifBlank { "精选" } }.distinct()
    }
    val filteredStations = remember(fmChannels, selectedCategory) {
        if (selectedCategory == "全部") fmChannels
        else fmChannels.filter { (it.category.ifBlank { "精选" }) == selectedCategory }
    }

    var stationSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var appending by remember { mutableStateOf(false) }

    fun loadStationBatch(channelId: String, append: Boolean = false) {
        scope.launch {
            if (append) appending = true else loading = true
            val batch = viewModel.getFmChannelSongs(channelId, forceNextBatch = true)
            if (append) {
                val existingKeys = stationSongs.map { it.hash.ifBlank { it.id }.lowercase() }.toHashSet()
                val newUnique = batch.filter { existingKeys.add(it.hash.ifBlank { it.id }.lowercase()) }
                stationSongs = stationSongs + newUnique
                Toast.makeText(
                    context,
                    "已追加 ${newUnique.size} 首新漫游曲目（当前共 ${stationSongs.size} 首）",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                stationSongs = batch
            }
            loading = false
            appending = false
        }
    }

    LaunchedEffect(selectedChannelId) {
        loadStationBatch(selectedChannelId, append = false)
    }

    val fallbackCovers = remember(dailySongs) {
        dailySongs.mapNotNull { it.coverUrl.takeIf { u -> u.isNotBlank() } }
    }

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "精选场景电台",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "实时电台频率 · 动态曲库漫游",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                loadStationBatch(currentChannel.id, append = false)
                                Toast.makeText(context, "正在为您切换下一批全新电台曲目...", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = ConceptPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("换一批", color = ConceptPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = conceptFloatingCardColor(),
                        shadowElevation = if (isDark) 0.dp else 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(86.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                            Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF153252)))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val cover = currentChannel.coverUrl.ifBlank { stationSongs.firstOrNull()?.coverUrl.orEmpty() }
                                    if (cover.isNotBlank()) {
                                        AsyncImage(
                                            model = cover,
                                            contentDescription = currentChannel.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Radio,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ConceptPrimary.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "FM 实时频率 · ${currentChannel.category}",
                                                color = ConceptPrimary,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        if (currentChannel.listenerCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${currentChannel.listenerCount} 人正在听",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = currentChannel.name,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${currentChannel.description} · 当前队列 ${stationSongs.size} 首",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (stationSongs.isNotEmpty()) {
                                            onSongClick(stationSongs.first(), stationSongs)
                                        }
                                    },
                                    enabled = stationSongs.isNotEmpty(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ConceptPrimary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("立即收听 (${stationSongs.size}首)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                FilledTonalButton(
                                    onClick = { loadStationBatch(currentChannel.id, append = true) },
                                    enabled = !loading && !appending,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = ConceptPrimary.copy(alpha = 0.15f),
                                        contentColor = ConceptPrimary
                                    )
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (appending) "漫游中..." else "继续漫游 +20首",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories) { cat ->
                                val selected = cat == selectedCategory
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) ConceptPrimary else conceptFloatingCardColor(),
                                    shadowElevation = if (!isDark && !selected) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { selectedCategory = cat }
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(filteredStations, key = { _, ch -> "radio_${ch.id}" }) { idx, ch ->
                                val isCurrent = ch.id == currentChannel.id
                                val fb = fallbackCovers.getOrNull(idx % fallbackCovers.size.coerceAtLeast(1)).orEmpty()
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isCurrent) ConceptPrimary else conceptFloatingCardColor(),
                                    shadowElevation = if (!isDark && !isCurrent) 3.dp else 0.dp,
                                    modifier = Modifier
                                        .width(168.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { selectedChannelId = ch.id }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = ch.coverUrl.ifBlank { fb },
                                            contentDescription = ch.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isCurrent) Color.White.copy(alpha = 0.2f)
                                                    else ConceptPrimary.copy(alpha = 0.14f)
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ch.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = ch.description,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (isCurrent) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
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
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "当前频率曲目流 (${stationSongs.size}首)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "每次刷新均拉取全新批次曲目",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                if (loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ConceptPrimary)
                        }
                    }
                } else if (stationSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂未获取到电台曲目，请点击右上角「换一批」", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    itemsIndexed(stationSongs, key = { idx, s -> "fm_song_${s.id}_$idx" }) { index, song ->
                        SongItemRow(
                            song = song,
                            orderIndex = index + 1,
                            isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                            onClick = { onSongClick(song, stationSongs) },
                            onMoreClick = { onSongMoreClick(song) }
                        )
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FilledTonalButton(
                                onClick = { loadStationBatch(currentChannel.id, append = true) },
                                enabled = !appending,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = conceptFloatingCardColor(),
                                    contentColor = ConceptPrimary
                                )
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (appending) "正在漫游新曲目..." else "继续加载下一批电台歌曲 (+20首)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
