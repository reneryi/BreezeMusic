package com.concept.music.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.RankItem
import com.concept.music.data.model.SceneCategory
import com.concept.music.data.model.Song
import com.concept.music.data.model.YouthChannel
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSongClick: (Song, List<Song>) -> Unit,
    onLogin: () -> Unit,
    onSearchClick: () -> Unit = {},
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dailySongs by viewModel.dailyRecommendSongs.collectAsState()
    val guessSongs by viewModel.guessSongs.collectAsState()
    val ranks by viewModel.ranks.collectAsState()
    val youthChannels by viewModel.youthChannels.collectAsState()
    val sceneCategories by viewModel.sceneCategories.collectAsState()
    val selectedScene by viewModel.selectedScene.collectAsState()
    val sceneSongs by viewModel.sceneSongs.collectAsState()
    val sceneLoading by viewModel.sceneLoading.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val vipStatus by viewModel.vipStatus.collectAsState(initial = com.concept.music.data.kugou.VipSnapshot())

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentSceneCategory = sceneCategories.find { it.id == selectedScene } ?: sceneCategories.first()

    // [INT-03] 下拉刷新状态
    val pullRefreshState = rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }
    LaunchedEffect(loading, sceneLoading) {
        if (!loading && !sceneLoading) {
            pullRefreshState.endRefresh()
        }
    }

    // [FUNC-05] 榜单 / 电台详情抽屉状态
    var detailSheetTitle by remember { mutableStateOf("") }
    var detailSheetSubtitle by remember { mutableStateOf("") }
    var detailSheetCover by remember { mutableStateOf("") }
    var detailSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var detailSheetLoading by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }

    fun openRankDetail(rank: RankItem) {
        detailSheetTitle = rank.rankName
        detailSheetSubtitle = rank.updateFrequency.ifBlank { "官方权威榜单" }
        detailSheetCover = rank.coverUrl
        detailSheetSongs = emptyList()
        detailSheetLoading = true
        showDetailSheet = true
        coroutineScope.launch {
            detailSheetSongs = viewModel.getRankSongs(rank.rankId)
            detailSheetLoading = false
        }
    }

    fun openChannelDetail(channel: YouthChannel) {
        detailSheetTitle = channel.name
        detailSheetSubtitle = channel.desc.ifBlank { channel.tag }
        detailSheetCover = channel.coverUrl
        detailSheetSongs = emptyList()
        detailSheetLoading = true
        showDetailSheet = true
        coroutineScope.launch {
            detailSheetSongs = viewModel.getYouthChannelSongs(channel.id)
            detailSheetLoading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color(0xFF0F1115)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (loading || sceneLoading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
                    color = ConceptPrimary,
                    trackColor = Color.Transparent
                )
            }
        }

        // 1. 顶部搜索胶囊
        item {
            DiscoverHeaderSearchBar(
                vipActive = vipStatus.isVip,
                onSearchClick = onSearchClick,
                onLogin = onLogin
            )
        }

        // 2. 场景化选择胶囊流 (全部推荐、沉浸学习、深夜助眠、燃动运动、自驾旅行、治愈微风)
        item {
            SceneFilterRow(
                categories = sceneCategories,
                selectedId = selectedScene,
                onSelectCategory = { catId ->
                    viewModel.selectScene(catId)
                }
            )
        }

        if (selectedScene != "ALL") {
            // ================= 场景化专属沉浸流 =================
            // 场景特色大 Banner
            item {
                SceneHeroBanner(
                    category = currentSceneCategory,
                    songCount = sceneSongs.size,
                    isLoading = sceneLoading,
                    onPlayAll = {
                        if (sceneSongs.isNotEmpty()) {
                            onSongClick(sceneSongs.first(), sceneSongs)
                            Toast.makeText(context, "已开启【${currentSceneCategory.label}】场景沉浸播放", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "场景音乐正在加载中...", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 该场景关联的概念版电台推荐
            val matchedChannels = youthChannels.filter { it.tag.contains(currentSceneCategory.label) || it.desc.contains(currentSceneCategory.label) }
                .ifEmpty { youthChannels.take(3) }

            if (matchedChannels.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${currentSceneCategory.label} · 推荐电台",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(matchedChannels) { channel ->
                                YouthChannelCard(
                                    channel = channel,
                                    onCardClick = { openChannelDetail(channel) },
                                    onPlayClick = {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "正在装载【${channel.name}】电台歌曲...", Toast.LENGTH_SHORT).show()
                                            val songs = viewModel.getYouthChannelSongs(channel.id)
                                            if (songs.isNotEmpty()) {
                                                onSongClick(songs.first(), songs)
                                            } else {
                                                Toast.makeText(context, "电台加载失败，请重试", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 场景推荐歌曲列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentSceneCategory.label}曲库 (${sceneSongs.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    if (sceneSongs.isNotEmpty()) {
                        Text(
                            text = "播放全部",
                            color = ConceptPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                onSongClick(sceneSongs.first(), sceneSongs)
                            }
                        )
                    }
                }
            }

            itemsIndexed(sceneSongs, key = { index, song -> "scene_${song.id}_$index" }) { index, song ->
                SongItemRow(
                    song = song,
                    orderIndex = index + 1,
                    isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                    onClick = { onSongClick(song, sceneSongs) },
                    onMoreClick = { onSongMoreClick(song) }
                )
            }
        } else {
            // ================= 默认全局推荐发现流 =================
            // 3. 每日推荐精美 Banner
            item {
                DailyRecommendBanner(
                    songCount = dailySongs.size,
                    onPlayAll = {
                        if (dailySongs.isNotEmpty()) {
                            onSongClick(dailySongs.first(), dailySongs)
                        } else {
                            Toast.makeText(context, "推荐歌曲正在加载中", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 4. 三大核心磁贴导航入口 (猜你喜欢、私人随心听、刷新发现)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickEntryTile(
                        title = "猜你喜欢",
                        subtitle = "AI 智能推荐",
                        icon = Icons.Rounded.AutoAwesome,
                        color = ConceptPrimary,
                        onClick = {
                            if (guessSongs.isNotEmpty()) {
                                onSongClick(guessSongs.first(), guessSongs)
                                Toast.makeText(context, "已开启【猜你喜欢】智能连播", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    QuickEntryTile(
                        title = "随心听 FM",
                        subtitle = "私人专属流",
                        icon = Icons.Rounded.Radio,
                        color = Color(0xFF64B5F6),
                        onClick = {
                            coroutineScope.launch {
                                Toast.makeText(context, "正在接入【酷狗私人随心听】...", Toast.LENGTH_SHORT).show()
                                val fmSongs = viewModel.getPersonalFmSongs()
                                if (fmSongs.isNotEmpty()) {
                                    onSongClick(fmSongs.first(), fmSongs)
                                    Toast.makeText(context, "已开启【私人随心听】专属调频", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "随心听连接失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    QuickEntryTile(
                        title = "刷新发现",
                        subtitle = "探索最新好歌",
                        icon = Icons.Rounded.Refresh,
                        color = VipGold,
                        onClick = viewModel::refresh,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 5. 【概念版专属电台 (Youth Channels)】卡片流
            if (youthChannels.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "概念版专属电台 · 青春频道",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "懂年轻人的心流空间与场景陪伴",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(youthChannels) { channel ->
                                YouthChannelCard(
                                    channel = channel,
                                    onCardClick = { openChannelDetail(channel) },
                                    onPlayClick = {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "正在连接【${channel.name}】电台...", Toast.LENGTH_SHORT).show()
                                            val songs = viewModel.getYouthChannelSongs(channel.id)
                                            if (songs.isNotEmpty()) {
                                                onSongClick(songs.first(), songs)
                                            } else {
                                                Toast.makeText(context, "电台加载失败，请重试", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 6. 【猜你喜欢】横向流
            if (guessSongs.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "猜你喜欢 · 智能推荐",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "播放全部",
                                color = ConceptPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    onSongClick(guessSongs.first(), guessSongs)
                                }
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(guessSongs.take(8)) { song ->
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181B22)),
                                    modifier = Modifier
                                        .width(135.dp)
                                        .clickable { onSongClick(song, guessSongs) }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        AsyncImage(
                                            model = song.coverUrl,
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(115.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0xFF222733))
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = song.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                        Text(
                                            text = song.artist,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.White.copy(alpha = 0.5f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. 【巅峰排行榜】官方榜单横向大卡片
            if (ranks.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "巅峰排行榜",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ranks) { rank ->
                                RankCardView(
                                    rank = rank,
                                    onCardClick = { openRankDetail(rank) },
                                    onPlayRank = {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "正在装载【${rank.rankName}】...", Toast.LENGTH_SHORT).show()
                                            val songs = viewModel.getRankSongs(rank.rankId)
                                            if (songs.isNotEmpty()) {
                                                onSongClick(songs.first(), songs)
                                            } else {
                                                Toast.makeText(context, "榜单歌曲加载失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 8. 【今日专属推歌】精选曲目列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日专属推歌 (${dailySongs.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "播放全部",
                        color = ConceptPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            if (dailySongs.isNotEmpty()) onSongClick(dailySongs.first(), dailySongs)
                        }
                    )
                }
            }

            itemsIndexed(dailySongs, key = { index, song -> "daily_${song.id}_$index" }) { index, song ->
                SongItemRow(
                    song = song,
                    orderIndex = index + 1,
                    isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                    onClick = { onSongClick(song, dailySongs) },
                    onMoreClick = { onSongMoreClick(song) }
                )
            }
        }
    }

        // [INT-03] 下拉刷新指示器
        PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
            containerColor = Color(0xFF1E222A),
            contentColor = ConceptPrimary
        )
    }

    // [FUNC-05] 榜单 / 概念版电台二级曲目浏览抽屉
    if (showDetailSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDetailSheet = false },
            containerColor = Color(0xFF161922),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = detailSheetCover,
                        contentDescription = detailSheetTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF222733))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = detailSheetTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = detailSheetSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.6f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = {
                            if (detailSheetSongs.isNotEmpty()) {
                                onSongClick(detailSheetSongs.first(), detailSheetSongs)
                                showDetailSheet = false
                            }
                        },
                        enabled = detailSheetSongs.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ConceptPrimary,
                            contentColor = Color(0xFF0A1014)
                        )
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("播放全部", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (detailSheetLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        color = ConceptPrimary
                    )
                } else if (detailSheetSongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无曲目数据", color = Color.White.copy(alpha = 0.45f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 460.dp)
                    ) {
                        itemsIndexed(detailSheetSongs) { index, song ->
                            SongItemRow(
                                song = song,
                                orderIndex = index + 1,
                                isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                                onClick = {
                                    onSongClick(song, detailSheetSongs)
                                    showDetailSheet = false
                                },
                                onMoreClick = { onSongMoreClick(song) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// ================= 子组件部分 =================

@Composable
private fun SceneFilterRow(
    categories: List<SceneCategory>,
    selectedId: String,
    onSelectCategory: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categories) { cat ->
            val isSelected = cat.id == selectedId
            val icon = when (cat.id) {
                "ALL" -> Icons.Rounded.AutoAwesome
                "STUDY" -> Icons.Rounded.School
                "SLEEP" -> Icons.Rounded.NightsStay
                "WORKOUT" -> Icons.Rounded.FitnessCenter
                "DRIVE" -> Icons.Rounded.DirectionsCar
                "HEALING" -> Icons.Rounded.Spa
                else -> Icons.Rounded.Radio
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) ConceptPrimary.copy(alpha = 0.2f) else Color(0xFF181B22),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) ConceptPrimary.copy(alpha = 0.8f) else Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelectCategory(cat.id) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = cat.label,
                        tint = if (isSelected) ConceptPrimary else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) ConceptPrimary else Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneHeroBanner(
    category: SceneCategory,
    songCount: Int,
    isLoading: Boolean,
    onPlayAll: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF163E32),
                            Color(0xFF0D2520),
                            Color(0xFF141A22)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ConceptPrimary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "场景专属 · ${category.label}",
                            color = ConceptPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = category.subtitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isLoading) "智能流挑选歌曲中..." else "已为您精选 $songCount 首沉浸曲目",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                FilledIconButton(
                    onClick = onPlayAll,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "沉浸播放",
                        tint = Color(0xFF0F1115),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun YouthChannelCard(
    channel: YouthChannel,
    onCardClick: () -> Unit = {},
    onPlayClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181B22)),
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onCardClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222733))
            ) {
                AsyncImage(
                    model = channel.coverUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // 标签徽章
                if (channel.tag.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = channel.tag,
                            color = ConceptPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // 悬浮微播放按钮（点击直接播放）
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPlayClick)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放",
                        tint = ConceptPrimary,
                        modifier = Modifier
                            .padding(6.dp)
                            .fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = channel.desc,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun DiscoverHeaderSearchBar(
    vipActive: Boolean,
    onSearchClick: () -> Unit,
    onLogin: () -> Unit = {}
) {
    Surface(
        shape = CircleShape,
        color = Color(0xFF1B1E26),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable(onClick = onSearchClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "搜索",
                tint = ConceptPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "搜索周杰伦、热门歌曲、专辑、电台...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp
                ),
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = if (vipActive) VipGold.copy(alpha = 0.2f) else ConceptPrimary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { if (!vipActive) onLogin() }
            ) {
                Text(
                    text = if (vipActive) "VIP" else "登录",
                    color = if (vipActive) VipGold else ConceptPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DailyRecommendBanner(
    songCount: Int,
    onPlayAll: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ConceptPrimaryDark,
                            Color(0xFF0F3B32),
                            Color(0xFF132029)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            tint = ConceptPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "今日每日推荐",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "根据你的品味生成 · 共 ${songCount} 首专属好歌",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }

                // 播放全部悬浮按钮
                FilledIconButton(
                    onClick = onPlayAll,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放全部",
                        tint = Color(0xFF0F1115),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickEntryTile(
    title: String,
    subtitle: String = "",
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF181B22),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                )
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RankCardView(
    rank: RankItem,
    onCardClick: () -> Unit = {},
    onPlayRank: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181B22)),
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onCardClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = rank.coverUrl,
                    contentDescription = rank.rankName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rank.rankName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = rank.updateFrequency,
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.45f))
                    )
                }
                IconButton(
                    onClick = onPlayRank,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = "播放",
                        tint = ConceptPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 预览曲目前3首
            rank.topSongsPreview.forEachIndexed { idx, songText ->
                Text(
                    text = songText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = if (idx == 0) 0.9f else 0.6f),
                        fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

