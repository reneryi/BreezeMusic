package com.concept.music.ui.home

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.Song
import com.concept.music.ui.components.PlayingAudioWaveIndicator
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

/**
 * 精选场景电台专区独立总览页 (RadioScreen)
 * 与发现页单电台快捷抽屉完全区分，定位为「电台调频广播中心 + 全部分类电台双列矩阵 + 20首分类曲目漫游流」：
 * 1. 顶部：复古未来感 FM 实时调频舱（展示当前锁定电台的频率、旋转黑胶唱片、电台简介、一键收听、换一批 20 首、继续漫游 +20 首）；
 * 2. 中部：电台分类筛选胶囊（全部 / 推荐电台 / 心情场景 / 年代语种 / 曲风流派）+ 双列大气圆润电台矩阵（可一键展开/折叠全部电台）；
 * 3. 底部：当前调频电台专属曲库流（每次默认加载完整 20 首严格符合当前电台分类的歌曲，点击「继续漫游 +20首」每次真实追加 20 首新曲目）。
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
            initialChannelId.takeIf { it.isNotBlank() && it != "all" }
                ?: fmChannels.firstOrNull()?.id
                ?: "hot"
        )
    }

    val currentChannel = remember(fmChannels, selectedChannelId) {
        fmChannels.firstOrNull { it.id == selectedChannelId }
            ?: fmChannels.firstOrNull()
            ?: FmChannel(id = selectedChannelId, name = "流行风向标", coverUrl = "", desc = "全天候智能音乐伴听", category = "推荐电台")
    }

    var selectedCategory by remember { mutableStateOf("全部") }
    val categories = remember(fmChannels) {
        listOf("全部") + fmChannels.map { it.category.ifBlank { "推荐电台" } }.distinct()
    }
    val filteredStations = remember(fmChannels, selectedCategory) {
        if (selectedCategory == "全部") fmChannels
        else fmChannels.filter { (it.category.ifBlank { "推荐电台" }) == selectedCategory }
    }

    var showAllStationsGrid by remember { mutableStateOf(true) }
    var stationSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var appending by remember { mutableStateOf(false) }

    fun loadStationBatch(channelId: String, append: Boolean = false) {
        scope.launch {
            if (append) appending = true else loading = true
            val batch = viewModel.getFmChannelSongs(channelId, size = 20, forceNextBatch = true)
            if (append) {
                val existingKeys = stationSongs.map { it.hash.ifBlank { it.id }.lowercase() }.toHashSet()
                val newUnique = batch.filter { existingKeys.add(it.hash.ifBlank { it.id }.lowercase()) }
                stationSongs = stationSongs + newUnique
                Toast.makeText(
                    context,
                    "已追加 ${newUnique.size} 首「${currentChannel.name}」分类新曲（当前共 ${stationSongs.size} 首）",
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

    // 计算每个电台对应的仿真 FM 赫兹频率号 (如 FM 98.6 MHz)
    fun fmFrequencyLabel(id: String): String {
        val hash = kotlin.math.abs(id.hashCode())
        val major = 88 + (hash % 20)
        val minor = (hash / 20) % 10
        return "FM $major.$minor MHz"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "tunerVinyl")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylRot"
    )

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "场景电台专区",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "全部分类电台矩阵 · 每次固定漫游 20 首同分类新歌",
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
                                Toast.makeText(context, "已为您刷新 20 首「${currentChannel.name}」全新曲目", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = ConceptPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("换一批 (20首)", color = ConceptPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
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
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. 顶部沉浸式 FM 调频广播舱
                item {
                    val tunerBrush = if (isDark) {
                        Brush.linearGradient(
                            listOf(Color(0xFF152C4E), Color(0xFF102039), Color(0xFF131A2B))
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(Color(0xFFD8ECFF), Color(0xFFE8F3FF), Color(0xFFF3EEFF))
                        )
                    }
                    val titleColor = if (isDark) Color.White else Color(0xFF0E1B2E)
                    val subColor = if (isDark) Color.White.copy(alpha = 0.72f) else Color(0xFF425875)

                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.Transparent,
                        shadowElevation = if (isDark) 8.dp else 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(tunerBrush)
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 旋转电台黑胶唱盘
                                Box(
                                    modifier = Modifier.size(92.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                rotationZ = vinylRotation
                                                shape = CircleShape
                                                clip = true
                                            }
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(Color(0xFF2B3548), Color(0xFF111622), Color(0xFF242D3F))
                                                )
                                            )
                                    )
                                    val cover = currentChannel.coverUrl.ifBlank { stationSongs.firstOrNull()?.coverUrl.orEmpty() }
                                    if (cover.isNotBlank()) {
                                        AsyncImage(
                                            model = cover,
                                            contentDescription = currentChannel.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(68.dp)
                                                .graphicsLayer {
                                                    rotationZ = vinylRotation
                                                    shape = CircleShape
                                                    clip = true
                                                }
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Radio,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(if (isDark) Color(0xFF102039) else Color(0xFFE8F3FF))
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ConceptPrimary.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = fmFrequencyLabel(currentChannel.id),
                                                color = ConceptPrimary,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = VipGold.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = currentChannel.category.ifBlank { "推荐电台" },
                                                color = if (isDark) VipGold else Color(0xFFB87900),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = currentChannel.name,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = titleColor,
                                                fontSize = 21.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        PlayingAudioWaveIndicator()
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${currentChannel.description} · ${currentChannel.listenerCountText}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = subColor
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

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
                                    Text("畅听本电台 (${stationSongs.size}首)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                FilledTonalButton(
                                    onClick = { loadStationBatch(currentChannel.id, append = true) },
                                    enabled = !loading && !appending,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isDark) Color.White.copy(alpha = 0.12f) else ConceptPrimary.copy(alpha = 0.14f),
                                        contentColor = if (isDark) Color.White else ConceptPrimary
                                    )
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (appending) "漫游中..." else "继续漫游 +20首",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. 全部分类电台宫格矩阵（与单电台列表彻底区分，直观选择任意分类电台）
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "全部分类电台 (${filteredStations.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            TextButton(
                                onClick = { showAllStationsGrid = !showAllStationsGrid },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (showAllStationsGrid) "收起电台墙 ▲" else "展开电台墙 ▼",
                                    color = ConceptPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories) { cat ->
                                val selected = cat == selectedCategory
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (selected) ConceptPrimary else conceptFloatingCardColor(),
                                    shadowElevation = if (!isDark && !selected) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable {
                                            selectedCategory = cat
                                            showAllStationsGrid = true
                                        }
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        if (showAllStationsGrid) {
                            val stationRows = filteredStations.chunked(2)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                stationRows.forEachIndexed { rowIdx, pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        pair.forEachIndexed { colIdx, ch ->
                                            val globalIdx = rowIdx * 2 + colIdx
                                            val isCurrent = ch.id == currentChannel.id
                                            val fb = fallbackCovers.getOrNull(globalIdx % fallbackCovers.size.coerceAtLeast(1)).orEmpty()
                                            RadioPlazaGridCard(
                                                channel = ch,
                                                frequencyText = fmFrequencyLabel(ch.id),
                                                fallbackCover = fb,
                                                isSelected = isCurrent,
                                                onClick = { selectedChannelId = ch.id },
                                                onPlayImmediate = {
                                                    selectedChannelId = ch.id
                                                    scope.launch {
                                                        val songs = viewModel.getFmChannelSongs(ch.id, size = 20, forceNextBatch = true)
                                                        stationSongs = songs
                                                        if (songs.isNotEmpty()) {
                                                            onSongClick(songs.first(), songs)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (pair.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. 当前调频电台 20 首专属分类曲目流
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "「${currentChannel.name}」当前漫游队列 (${stationSongs.size}首)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "曲目严格匹配【${currentChannel.category} · ${currentChannel.name}】分类",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        FilledTonalButton(
                            onClick = { loadStationBatch(currentChannel.id, append = false) },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ConceptPrimary.copy(alpha = 0.14f),
                                contentColor = ConceptPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("换20首新歌", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
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
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = conceptFloatingCardColor(),
                                    contentColor = ConceptPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (appending) "正在漫游 20 首同分类新歌..." else "继续漫游 +20 首「${currentChannel.name}」好歌",
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

@Composable
private fun RadioPlazaGridCard(
    channel: FmChannel,
    frequencyText: String,
    fallbackCover: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPlayImmediate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val cover = channel.coverUrl.takeIf { it.isNotBlank() && !it.contains("20210519") } ?: fallbackCover

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) ConceptPrimary.copy(alpha = if (isDark) 0.24f else 0.16f) else conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 4.dp,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF142848))))
            ) {
                if (cover.isNotBlank()) {
                    AsyncImage(
                        model = cover,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.58f))
                            )
                        )
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) ConceptPrimary else Color.Black.copy(alpha = 0.48f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (isSelected) "正在调频" else frequencyText,
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = channel.category,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp)
                )

                FilledIconButton(
                    onClick = onPlayImmediate,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放电台",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) ConceptPrimary else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = channel.description,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
