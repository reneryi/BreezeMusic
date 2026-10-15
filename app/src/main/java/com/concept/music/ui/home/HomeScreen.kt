package com.concept.music.ui.home

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.RankItem
import com.concept.music.data.model.Song
import com.concept.music.data.model.YouthChannel
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSongClick: (Song, List<Song>) -> Unit,
    onLogin: () -> Unit,
    onSearchClick: () -> Unit = {},
    onOpenDailyRecommend: () -> Unit = {},
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dailySongs by viewModel.dailyRecommendSongs.collectAsState()
    val personalFmSongs by viewModel.personalFmSongs.collectAsState()
    val youthChannels by viewModel.youthChannels.collectAsState()
    val fmChannels by viewModel.fmChannels.collectAsState()
    val ranks by viewModel.ranks.collectAsState()
    val loading by viewModel.loading.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pullRefreshState = rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }
    LaunchedEffect(loading) {
        if (!loading) {
            pullRefreshState.endRefresh()
        }
    }

    // 二级曲目抽屉状态（共用：榜单 / 概念版频道 / 场景电台）
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

    fun openYouthChannelDetail(channel: YouthChannel) {
        detailSheetTitle = channel.name
        detailSheetSubtitle = channel.description.ifBlank { "概念版专属频道" }
        detailSheetCover = channel.coverUrl
        detailSheetSongs = emptyList()
        detailSheetLoading = true
        showDetailSheet = true
        coroutineScope.launch {
            detailSheetSongs = viewModel.getYouthChannelSongs(channel.id)
            detailSheetLoading = false
        }
    }

    fun openFmChannelDetail(channel: FmChannel) {
        detailSheetTitle = channel.name
        detailSheetSubtitle = channel.description.ifBlank { "全天候场景电台" }
        detailSheetCover = channel.coverUrl
        detailSheetSongs = emptyList()
        detailSheetLoading = true
        showDetailSheet = true
        coroutineScope.launch {
            detailSheetSongs = viewModel.getFmChannelSongs(channel.id)
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (loading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(CircleShape),
                        color = ConceptPrimary,
                        trackColor = Color.Transparent
                    )
                }
            }

            // 1. 顶部标题与右上角搜索按钮
            item {
                DiscoverTopHeader(
                    onSearchClick = onSearchClick
                )
            }

            // 2. 每日推荐大卡片（加高设计 + 前三首封面错位重叠 + 点击进入独立界面）
            item {
                DailyRecommendBanner(
                    songs = dailySongs,
                    onCardClick = onOpenDailyRecommend,
                    onPlayAll = {
                        if (dailySongs.isNotEmpty()) {
                            onSongClick(dailySongs.first(), dailySongs)
                        } else {
                            Toast.makeText(context, "每日推荐正在加载中", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 3. [P1-1] 私人随心听 (Personal FM) 漫游卡片
            item {
                PersonalFmCard(
                    songs = personalFmSongs.ifEmpty { dailySongs },
                    onPlayFm = {
                        val pool = personalFmSongs.ifEmpty { dailySongs }
                        if (pool.isNotEmpty()) {
                            onSongClick(pool.first(), pool)
                        } else {
                            viewModel.refreshPersonalFm { loaded ->
                                if (loaded.isNotEmpty()) onSongClick(loaded.first(), loaded)
                            }
                        }
                    },
                    onRefreshFm = {
                        viewModel.refreshPersonalFm { loaded ->
                            if (loaded.isNotEmpty()) {
                                Toast.makeText(context, "已换一批随心听推荐 (${loaded.size}首)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // 4. [P1-1] 概念版专属年轻频道 (Youth Channels)
            if (youthChannels.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "概念专属频道",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "心流 · 自愈 · 场景漫游",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = ConceptPrimary.copy(alpha = 0.85f)
                                )
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(youthChannels, key = { it.id }) { channel ->
                                YouthChannelCardView(
                                    channel = channel,
                                    onCardClick = { openYouthChannelDetail(channel) },
                                    onPlayChannel = {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "正在装载【${channel.name}】...", Toast.LENGTH_SHORT).show()
                                            val songs = viewModel.getYouthChannelSongs(channel.id)
                                            if (songs.isNotEmpty()) {
                                                onSongClick(songs.first(), songs)
                                            } else {
                                                Toast.makeText(context, "频道歌曲加载失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 5. [P1-1] 全天候场景电台 (FM Channels)
            if (fmChannels.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "精选场景电台",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "全天候智能伴听",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(fmChannels, key = { it.id }) { channel ->
                                FmChannelCardView(
                                    channel = channel,
                                    onCardClick = { openFmChannelDetail(channel) },
                                    onPlayFmChannel = {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "正在开启【${channel.name}】...", Toast.LENGTH_SHORT).show()
                                            val songs = viewModel.getFmChannelSongs(channel.id)
                                            if (songs.isNotEmpty()) {
                                                onSongClick(songs.first(), songs)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 6. 巅峰排行榜
            if (ranks.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "巅峰排行榜",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "官方实时热榜",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            )
                        }

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
        }

        // 下拉刷新指示器
        PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
            containerColor = Color(0xFF1E222A),
            contentColor = ConceptPrimary
        )
    }

    // 榜单/频道/电台二级曲目浏览抽屉
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

@Composable
private fun PersonalFmCard(
    songs: List<Song>,
    onPlayFm: () -> Unit,
    onRefreshFm: () -> Unit
) {
    val leadSong = songs.firstOrNull()
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171B24)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onPlayFm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1A2535),
                            Color(0xFF171B24)
                        )
                    )
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF232936)),
                contentAlignment = Alignment.Center
            ) {
                if (!leadSong?.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = leadSong!!.coverUrl,
                        contentDescription = leadSong.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Radio,
                        contentDescription = null,
                        tint = ConceptPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = ConceptPrimary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "私人随心听 FM",
                            color = ConceptPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "不间断契合口味漫游",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = if (leadSong != null) "${leadSong.title} - ${leadSong.artist}" else "点击开启你的专属私人电台",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRefreshFm,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "换一批",
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            FilledIconButton(
                onClick = onPlayFm,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "播放随心听",
                    tint = Color(0xFF0A1014),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun YouthChannelCardView(
    channel: YouthChannel,
    onCardClick: () -> Unit,
    onPlayChannel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181B22)),
        modifier = Modifier
            .width(164.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onCardClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF222733))
            ) {
                AsyncImage(
                    model = channel.coverUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (channel.tag.isNotBlank()) {
                    Surface(
                        color = Color(0xAA0F1115),
                        shape = RoundedCornerShape(8.dp),
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
                FilledIconButton(
                    onClick = onPlayChannel,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ConceptPrimary.copy(alpha = 0.92f)
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放频道",
                        tint = Color(0xFF0A1014),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = channel.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FmChannelCardView(
    channel: FmChannel,
    onCardClick: () -> Unit,
    onPlayFmChannel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181B22)),
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onCardClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = channel.coverUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222733))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = channel.description,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.5f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onPlayFmChannel,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = "播放电台",
                    tint = ConceptPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * 每日推荐独立详情页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRecommendScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {}
) {
    val dailySongs by viewModel.dailyRecommendSongs.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val today = remember { LocalDate.now() }
    val monthStr = remember(today) { String.format("%02d", today.monthValue) }
    val dayStr = remember(today) { String.format("%02d", today.dayOfMonth) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "每日推荐",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新推荐", tint = Color.White.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1115))
            )
        },
        containerColor = Color(0xFF0F1115)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // 顶部视觉头图区
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF144235),
                                    Color(0xFF0F2D27),
                                    Color(0xFF151922)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StaggeredThreeCovers(
                            songs = dailySongs.take(3),
                            primarySize = 86.dp,
                            secondarySize = 74.dp,
                            tertiarySize = 64.dp
                        )

                        Spacer(modifier = Modifier.width(18.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = dayStr,
                                    color = ConceptPrimary,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = " / $monthStr",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "私人定制 · 每日 6:00 更新",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "根据你的听歌口味精选 ${dailySongs.size} 首好歌",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            )
                        }
                    }
                }
            }

            // 播放全部操作栏
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (dailySongs.isNotEmpty()) {
                                onSongClick(dailySongs.first(), dailySongs)
                            }
                        },
                        enabled = dailySongs.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ConceptPrimary,
                            contentColor = Color(0xFF0A1014)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "播放全部 (${dailySongs.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    if (loading) {
                        CircularProgressIndicator(
                            color = ConceptPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (dailySongs.isEmpty() && !loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂未获取到推荐歌曲，请下拉或点击右上角刷新", color = Color.White.copy(alpha = 0.5f))
                    }
                }
            } else {
                itemsIndexed(dailySongs, key = { index, song -> "daily_page_${song.id}_$index" }) { index, song ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
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
        }
    }
}

@Composable
private fun DiscoverTopHeader(
    onSearchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "发现",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "探索你的音乐世界",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 0.3.sp
                )
            )
        }

        // 右上角搜索触发按钮（精致圆润胶囊，去除原有全宽搜索框与右侧VIP标识）
        Surface(
            shape = CircleShape,
            color = Color(0xFF1B1E26),
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(onClick = onSearchClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "搜索",
                    tint = ConceptPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * 重新设计的每日推荐大卡片：
 * - 高度提升，视觉层次饱满
 * - 左侧前三首歌曲封面错位重叠展示
 * - 修正文案语病，改为“每日推荐”并展示日期与推荐曲目预览
 * - 点击卡片进入独立每日推荐页，点击右侧播放按钮直接播放全部
 */
@Composable
private fun DailyRecommendBanner(
    songs: List<Song>,
    onCardClick: () -> Unit,
    onPlayAll: () -> Unit
) {
    val dateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("MM / dd"))
    }
    val previewSummary = remember(songs) {
        if (songs.isNotEmpty()) {
            val previewNames = songs.take(2).joinToString(" · ") { "${it.title}-${it.artist}" }
            "含 $previewNames 等 ${songs.size} 首好歌"
        } else {
            "契合你的听歌品味 · 每日精选 30 首好歌"
        }
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .height(172.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onCardClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ConceptPrimaryDark,
                            Color(0xFF103F35),
                            Color(0xFF13222C),
                            Color(0xFF161A24)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：前三首歌曲封面错位重叠显示
                StaggeredThreeCovers(
                    songs = songs.take(3),
                    primarySize = 96.dp,
                    secondarySize = 82.dp,
                    tertiarySize = 70.dp
                )

                Spacer(modifier = Modifier.width(18.dp))

                // 中间：标题、日期徽章与推荐语
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ConceptPrimary.copy(alpha = 0.18f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = ConceptPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$dateLabel · 个性化定制",
                                color = ConceptPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "每日推荐",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = previewSummary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.72f),
                            lineHeight = 17.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "进入歌单详情",
                            color = ConceptPrimary.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = ConceptPrimary.copy(alpha = 0.9f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 右侧：独立快速播放按钮
                FilledIconButton(
                    onClick = onPlayAll,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(8.dp, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放全部",
                        tint = Color(0xFF0A1014),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * 前三首歌曲封面错位重叠组件
 */
@Composable
private fun StaggeredThreeCovers(
    songs: List<Song>,
    primarySize: Dp = 94.dp,
    secondarySize: Dp = 80.dp,
    tertiarySize: Dp = 68.dp
) {
    val cover1 = songs.getOrNull(0)?.coverUrl.orEmpty()
    val cover2 = songs.getOrNull(1)?.coverUrl.orEmpty()
    val cover3 = songs.getOrNull(2)?.coverUrl.orEmpty()

    Box(
        modifier = Modifier
            .width(primarySize + 36.dp)
            .height(primarySize + 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // 第 3 首封面（最底层，偏右下，微向右倾）
        CoverStackLayer(
            coverUrl = cover3,
            size = tertiarySize,
            modifier = Modifier
                .offset(x = 36.dp, y = 10.dp)
                .graphicsLayer {
                    rotationZ = 9f
                    alpha = 0.72f
                }
        )

        // 第 2 首封面（中间层，居中偏右，微倾）
        CoverStackLayer(
            coverUrl = cover2,
            size = secondarySize,
            modifier = Modifier
                .offset(x = 18.dp, y = 4.dp)
                .graphicsLayer {
                    rotationZ = 3.5f
                    alpha = 0.88f
                }
        )

        // 第 1 首封面（最顶层，偏左上，主视觉）
        CoverStackLayer(
            coverUrl = cover1,
            size = primarySize,
            modifier = Modifier
                .offset(x = 0.dp, y = (-3).dp)
                .graphicsLayer {
                    rotationZ = -4f
                }
        )
    }
}

@Composable
private fun CoverStackLayer(
    coverUrl: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .size(size)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(Color(0xFF1E2631))
            .border(1.2.dp, Color.White.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = ConceptPrimary.copy(alpha = 0.6f),
                modifier = Modifier.size(size * 0.42f)
            )
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
