package com.concept.music.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
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
import com.concept.music.service.FocusPodManager
import com.concept.music.ui.components.FocusPodBottomSheet
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSongClick: (Song, List<Song>) -> Unit,
    onLogin: () -> Unit,
    onSearchClick: () -> Unit = {},
    onOpenDailyRecommend: () -> Unit = {},
    onOpenRank: (Int) -> Unit = {},
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

    // 二级曲目抽屉状态（用于概念版频道 / 场景电台 / 私人随心听 FM 列表浏览）
    var detailSheetTitle by remember { mutableStateOf("") }
    var detailSheetSubtitle by remember { mutableStateOf("") }
    var detailSheetCover by remember { mutableStateOf("") }
    var detailSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var detailSheetLoading by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var isPersonalFmSheet by remember { mutableStateOf(false) }

    var showFocusPodSheet by remember { mutableStateOf(false) }
    val focusPodState by FocusPodManager.state.collectAsState()

    fun openPersonalFmListSheet() {
        val pool = personalFmSongs.ifEmpty { dailySongs }
        detailSheetTitle = "私人随心听 FM"
        detailSheetSubtitle = "根据你的听歌偏好实时生成的漫游候选队列（可随时换一批）"
        detailSheetCover = pool.firstOrNull()?.coverUrl.orEmpty()
        detailSheetSongs = pool
        detailSheetLoading = pool.isEmpty()
        isPersonalFmSheet = true
        showDetailSheet = true
        if (pool.isEmpty()) {
            viewModel.refreshPersonalFm { loaded ->
                detailSheetSongs = loaded
                detailSheetCover = loaded.firstOrNull()?.coverUrl.orEmpty()
                detailSheetLoading = false
            }
        }
    }

    fun openYouthChannelDetail(channel: YouthChannel, fallbackCover: String) {
        detailSheetTitle = channel.name
        detailSheetSubtitle = channel.description.ifBlank { "概念版专属频道" }
        detailSheetCover = channel.coverUrl.ifBlank { fallbackCover }
        detailSheetSongs = emptyList()
        detailSheetLoading = true
        isPersonalFmSheet = false
        showDetailSheet = true
        coroutineScope.launch {
            val loaded = viewModel.getYouthChannelSongs(channel.id)
            detailSheetSongs = loaded
            if (detailSheetCover.isBlank()) {
                detailSheetCover = loaded.firstOrNull()?.coverUrl.orEmpty()
            }
            detailSheetLoading = false
        }
    }

    fun openFmChannelDetail(channel: FmChannel, fallbackCover: String) {
        detailSheetTitle = channel.name
        detailSheetSubtitle = channel.description.ifBlank { "全天候场景电台" }
        detailSheetCover = channel.coverUrl.ifBlank { fallbackCover }
        detailSheetSongs = emptyList()
        detailSheetLoading = true
        isPersonalFmSheet = false
        showDetailSheet = true
        coroutineScope.launch {
            val loaded = viewModel.getFmChannelSongs(channel.id)
            detailSheetSongs = loaded
            if (detailSheetCover.isBlank()) {
                detailSheetCover = loaded.firstOrNull()?.coverUrl.orEmpty()
            }
            detailSheetLoading = false
        }
    }

    // 为可能缺少封面的频道分配不重复的真实歌曲封面作为视觉兜底
    val coverPool = remember(dailySongs, personalFmSongs, ranks) {
        (dailySongs.mapNotNull { it.coverUrl.takeIf { u -> u.isNotBlank() } } +
            personalFmSongs.mapNotNull { it.coverUrl.takeIf { u -> u.isNotBlank() } } +
            ranks.mapNotNull { it.coverUrl.takeIf { u -> u.isNotBlank() } }).distinct()
    }

    ConceptStageBackground(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (loading && dailySongs.isEmpty()) {
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

            // 1. 顶部现代标题栏与人性化全宽搜索胶囊（消除突兀圆圈）
            item {
                DiscoverTopHeader(
                    onSearchClick = onSearchClick
                )
            }

            // 2. 每日推荐大卡片（错位三封面 + 曲目预览 + 一键全播 + 独立详情页入口）
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

            // 3. 私人随心听 (Personal FM) 漫游卡片（点击主体查看列表，点击右侧按钮直接播放/换一批）
            item {
                val fmPool = personalFmSongs.ifEmpty { dailySongs }
                PersonalFmCard(
                    songs = fmPool,
                    onOpenList = { openPersonalFmListSheet() },
                    onPlayFm = {
                        if (fmPool.isNotEmpty()) {
                            onSongClick(fmPool.first(), fmPool)
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

            // 4. [创新点1] 概念胶囊工作台 (Concept Focus Pod：番茄钟 × 概念频道 × 环境白噪音)
            item {
                val isDark = LocalIsDarkTheme.current
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF131C2E) else Color(0xFFEAF3FF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { showFocusPodSheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                0.8.dp,
                                ConceptPrimary.copy(alpha = if (isDark) 0.28f else 0.35f),
                                RoundedCornerShape(22.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ConceptPrimary.copy(alpha = 0.16f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.SelfImprovement,
                                    contentDescription = "概念胶囊工作台",
                                    tint = ConceptPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "概念胶囊工作台 · Focus Pod",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (focusPodState.isRunning) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ConceptPrimary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "专注 ${focusPodState.formattedRemaining}",
                                            color = ConceptPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "心流番茄钟 × 概念版频道 × 4路白噪音混音 · 自动累计听歌等级",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = { showFocusPodSheet = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ConceptPrimary.copy(alpha = 0.18f),
                                contentColor = ConceptPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (focusPodState.isRunning) "控制台" else "开启心流",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 5. 概念版专属年轻频道 (Youth Channels)
            if (youthChannels.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionTitleBar(
                            title = "概念专属频道",
                            subtitle = "点击卡片查看歌单 · 点击右下角一键畅听"
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(youthChannels, key = { _, it -> it.id }) { idx, channel ->
                                val fallbackCover = coverPool.getOrNull(idx % coverPool.size.coerceAtLeast(1)).orEmpty()
                                YouthChannelCardView(
                                    channel = channel,
                                    fallbackCoverUrl = fallbackCover,
                                    index = idx,
                                    onCardClick = { openYouthChannelDetail(channel, fallbackCover) },
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

            // 6. 全天候场景电台 (FM Channels)
            if (fmChannels.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionTitleBar(
                            title = "精选场景电台",
                            subtitle = "全天候多场景智能伴听"
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(fmChannels, key = { _, it -> it.id }) { idx, channel ->
                                val fallbackCover = coverPool.getOrNull((idx + 3) % coverPool.size.coerceAtLeast(1)).orEmpty()
                                FmChannelCardView(
                                    channel = channel,
                                    fallbackCoverUrl = fallbackCover,
                                    index = idx,
                                    onCardClick = { openFmChannelDetail(channel, fallbackCover) },
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

            // 7. 巅峰排行榜（点击进入独立全屏排行榜页面 RankDetailScreen）
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
                            Column {
                                Text(
                                    text = "巅峰排行榜",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "官方权威实时热榜 · 点击进入榜单专区",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            TextButton(
                                onClick = {
                                    val firstId = ranks.firstOrNull()?.rankId ?: 6666
                                    onOpenRank(firstId)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "全部榜单 >",
                                    color = ConceptPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ranks, key = { it.rankId }) { rank ->
                                RankCardView(
                                    rank = rank,
                                    onCardClick = { onOpenRank(rank.rankId) },
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

        // 仅在用户实际下拉或刷新时才显示下拉刷新指示器，彻底消除静止状态下顶部中央的突兀圆圈！
        if (pullRefreshState.progress > 0.05f || pullRefreshState.isRefreshing) {
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = ConceptPrimary
            )
        }
    }

    // 频道 / 电台 / 私人随心听 FM 曲目列表浏览抽屉
    if (showDetailSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDetailSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF162844)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (detailSheetCover.isNotBlank()) {
                            AsyncImage(
                                model = detailSheetCover,
                                contentDescription = detailSheetTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Radio,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = detailSheetTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = detailSheetSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isPersonalFmSheet) {
                        FilledTonalIconButton(
                            onClick = {
                                detailSheetLoading = true
                                viewModel.refreshPersonalFm { loaded ->
                                    detailSheetSongs = loaded
                                    detailSheetCover = loaded.firstOrNull()?.coverUrl.orEmpty()
                                    detailSheetLoading = false
                                    Toast.makeText(context, "已刷新随心听列表 (${loaded.size}首)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "换一批", tint = ConceptPrimary)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Button(
                        onClick = {
                            if (detailSheetSongs.isNotEmpty()) {
                                onSongClick(detailSheetSongs.first(), detailSheetSongs)
                                showDetailSheet = false
                            }
                        },
                        enabled = detailSheetSongs.isNotEmpty(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ConceptPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("全部播放", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        Text("暂无曲目数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 460.dp)
                    ) {
                        itemsIndexed(detailSheetSongs, key = { idx, s -> "${s.id}_$idx" }) { index, song ->
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

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showFocusPodSheet) {
        FocusPodBottomSheet(
            onDismiss = { showFocusPodSheet = false },
            onPlayYouthChannel = { channelId ->
                coroutineScope.launch {
                    val songs = viewModel.getYouthChannelSongs(channelId)
                    if (songs.isNotEmpty()) {
                        onSongClick(songs.first(), songs)
                    }
                }
            }
        )
    }
}

@Composable
private fun SectionTitleBar(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
        )
    }
}

@Composable
private fun PersonalFmCard(
    songs: List<Song>,
    onOpenList: () -> Unit,
    onPlayFm: () -> Unit,
    onRefreshFm: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val leadSong = songs.firstOrNull()
    val secondSong = songs.getOrNull(1)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF131B2B) else Color(0xFFEBF4FF)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onOpenList)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    0.8.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else ConceptPrimary.copy(alpha = 0.25f),
                    RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF1A2B4C)))),
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
                        tint = Color.White,
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
                        text = "轻触查看漫游列表 (${songs.size}首) >",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = if (leadSong != null) "${leadSong.title} - ${leadSong.artist}" else "点击开启你的专属私人电台",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (secondSong != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "下一首漫游：${secondSong.title} - ${secondSong.artist}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onRefreshFm,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "换一批",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            FilledIconButton(
                onClick = onPlayFm,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "播放随心听",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun YouthChannelCardView(
    channel: YouthChannel,
    fallbackCoverUrl: String,
    index: Int,
    onCardClick: () -> Unit,
    onPlayChannel: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val effectiveCover = channel.coverUrl.takeIf { it.isNotBlank() && !it.contains("20210519") } ?: fallbackCoverUrl
    val fallbackBrush = remember(index) {
        when (index % 4) {
            0 -> Brush.linearGradient(listOf(Color(0xFF14355B), Color(0xFF0E1D33)))
            1 -> Brush.linearGradient(listOf(Color(0xFF1F2B56), Color(0xFF121830)))
            2 -> Brush.linearGradient(listOf(Color(0xFF123B4A), Color(0xFF0C202B)))
            else -> Brush.linearGradient(listOf(Color(0xFF2E234E), Color(0xFF151228)))
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF141B29) else Color(0xFFEBF3FC)
        ),
        modifier = Modifier
            .width(164.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onCardClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(fallbackBrush)
            ) {
                if (effectiveCover.isNotBlank()) {
                    AsyncImage(
                        model = effectiveCover,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier
                            .size(54.dp)
                            .align(Alignment.Center)
                    )
                }
                // 底部微暗渐变增强图标与标签对比度
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        )
                )
                if (channel.tag.isNotBlank()) {
                    Surface(
                        color = Color(0xB30A101D),
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
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                FilledIconButton(
                    onClick = onPlayChannel,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ConceptPrimary
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放频道",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = channel.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    fallbackCoverUrl: String,
    index: Int,
    onCardClick: () -> Unit,
    onPlayFmChannel: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val effectiveCover = channel.coverUrl.takeIf { it.isNotBlank() && !it.contains("20210519") } ?: fallbackCoverUrl
    val fallbackBrush = remember(index) {
        when (index % 3) {
            0 -> Brush.linearGradient(listOf(Color(0xFF1A3A5F), Color(0xFF112238)))
            1 -> Brush.linearGradient(listOf(Color(0xFF183D4E), Color(0xFF0E2430)))
            else -> Brush.linearGradient(listOf(Color(0xFF282B52), Color(0xFF141830)))
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF141B29) else Color(0xFFEBF3FC)
        ),
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onCardClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fallbackBrush),
                contentAlignment = Alignment.Center
            ) {
                if (effectiveCover.isNotBlank()) {
                    AsyncImage(
                        model = effectiveCover,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
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
            IconButton(
                onClick = onPlayFmChannel,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = "播放电台",
                    tint = ConceptPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * 巅峰排行榜独立全屏页面（支持顶部切换任意官方榜单 + 榜单内实时搜索 + 冠亚季军高亮徽章）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankDetailScreen(
    viewModel: HomeViewModel,
    initialRankId: Int,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {}
) {
    val ranks by viewModel.ranks.collectAsState()
    var selectedRankId by remember(initialRankId) { mutableIntStateOf(initialRankId) }
    var rankSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val currentRank = remember(ranks, selectedRankId) {
        ranks.firstOrNull { it.rankId == selectedRankId }
            ?: ranks.firstOrNull()
            ?: RankItem(selectedRankId, "官方热门榜", "", "每日更新")
    }

    LaunchedEffect(selectedRankId) {
        loading = true
        rankSongs = viewModel.getRankSongs(selectedRankId)
        loading = false
    }

    val filteredSongs = remember(rankSongs, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) rankSongs
        else rankSongs.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }
    }

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentRank.rankName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
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
                contentPadding = PaddingValues(bottom = 112.dp)
            ) {
                // 1. 顶部多榜单横向快速切换胶囊栏
                if (ranks.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ranks, key = { it.rankId }) { item ->
                                val selected = item.rankId == currentRank.rankId
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedRankId = item.rankId },
                                    label = {
                                        Text(
                                            text = item.rankName,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = ConceptPrimary
                                    )
                                )
                            }
                        }
                    }
                }

                // 2. 榜单头部视觉卡片
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF1A2942)))),
                                contentAlignment = Alignment.Center
                            ) {
                                val cover = currentRank.coverUrl.ifBlank { rankSongs.firstOrNull()?.coverUrl.orEmpty() }
                                if (cover.isNotBlank()) {
                                    AsyncImage(
                                        model = cover,
                                        contentDescription = currentRank.rankName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Leaderboard,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentRank.rankName,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${currentRank.updateFrequency.ifBlank { "官方实时更新" }} · 共 ${rankSongs.size} 首热歌",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        if (filteredSongs.isNotEmpty()) {
                                            onSongClick(filteredSongs.first(), filteredSongs)
                                        }
                                    },
                                    enabled = filteredSongs.isNotEmpty(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ConceptPrimary,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("播放榜单全部", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 3. 榜单内实时搜索过滤框
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        placeholder = { Text("搜索本榜歌曲 / 歌手...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = ConceptPrimary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清除", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ConceptPrimary)
                        }
                    }
                } else if (filteredSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("未找到匹配的榜单歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    itemsIndexed(filteredSongs, key = { idx, s -> "rank_${s.id}_$idx" }) { index, song ->
                        SongItemRow(
                            song = song,
                            orderIndex = index + 1,
                            isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                            onClick = { onSongClick(song, filteredSongs) },
                            onMoreClick = { onSongMoreClick(song) }
                        )
                    }
                }
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

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "每日推荐",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新推荐", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
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
                                        Color(0xFF122C4F),
                                        Color(0xFF0E213C),
                                        Color(0xFF131826)
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
                                contentColor = Color.White
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
                            Text("暂未获取到推荐歌曲，请点击右上角刷新", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}

@Composable
private fun DiscoverTopHeader(
    onSearchClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val greeting = remember {
        val hour = runCatching { LocalTime.now().hour }.getOrDefault(12)
        when (hour) {
            in 5..10 -> "清晨微风 · 唤醒耳朵"
            in 11..14 -> "午后漫游 · 忙里偷闲"
            in 15..18 -> "落日时分 · 治愈旋律"
            else -> "夜幕微蓝 · 静享独处"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "发现",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.3.sp
                    )
                )
            }
        }

        // 人性化全宽大触控区搜索入口胶囊（取代原先右上角孤立小圆圈）
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isDark) Color(0xFF141C2B) else Color(0xFFEAF2FC),
            border = BorderStroke(
                0.8.dp,
                if (isDark) Color.White.copy(alpha = 0.08f) else ConceptPrimary.copy(alpha = 0.22f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onSearchClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
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
                    text = "搜索歌曲、歌手、专辑、歌单...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 每日推荐横幅卡片
 */
@Composable
private fun DailyRecommendBanner(
    songs: List<Song>,
    onCardClick: () -> Unit,
    onPlayAll: () -> Unit
) {
    val today = remember { LocalDate.now() }
    val monthStr = remember(today) { String.format("%02d", today.monthValue) }
    val dayStr = remember(today) { String.format("%02d", today.dayOfMonth) }

    val previewSummary = remember(songs) {
        if (songs.isEmpty()) "正在为你汇聚今日专属好歌..."
        else songs.take(2).joinToString(" · ") { "${it.title}-${it.artist}" }
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .shadow(16.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onCardClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF132D4E),
                            Color(0xFF0E2038),
                            Color(0xFF121826)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            ConceptPrimary.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StaggeredThreeCovers(
                    songs = songs.take(3),
                    primarySize = 104.dp,
                    secondarySize = 90.dp,
                    tertiarySize = 76.dp
                )

                Spacer(modifier = Modifier.width(18.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = ConceptPrimary.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = ConceptPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$monthStr / $dayStr · 个性化定制",
                                color = ConceptPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "每日推荐",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 22.sp,
                            letterSpacing = 0.5.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "含 $previewSummary",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 11.5.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "查看全部 ${songs.size.takeIf { it > 0 } ?: 30} 首推荐 >",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ConceptPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = onPlayAll,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ConceptPrimary
                    ),
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(10.dp, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放每日推荐",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/**
 * 前三首歌曲封面错位层叠组件
 */
@Composable
private fun StaggeredThreeCovers(
    songs: List<Song>,
    primarySize: Dp = 104.dp,
    secondarySize: Dp = 90.dp,
    tertiarySize: Dp = 76.dp
) {
    val cover1 = songs.getOrNull(0)?.coverUrl.orEmpty()
    val cover2 = songs.getOrNull(1)?.coverUrl.orEmpty()
    val cover3 = songs.getOrNull(2)?.coverUrl.orEmpty()

    Box(
        modifier = Modifier
            .width(primarySize + 28.dp)
            .height(primarySize + 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (cover3.isNotBlank()) {
            AsyncImage(
                model = cover3,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(tertiarySize)
                    .offset(x = 26.dp, y = 6.dp)
                    .graphicsLayer {
                        rotationZ = 10f
                        alpha = 0.55f
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF252A34))
            )
        }

        if (cover2.isNotBlank()) {
            AsyncImage(
                model = cover2,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(secondarySize)
                    .offset(x = 14.dp, y = 3.dp)
                    .graphicsLayer {
                        rotationZ = 5f
                        alpha = 0.78f
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF20252E))
            )
        }

        Box(
            modifier = Modifier
                .size(primarySize)
                .shadow(12.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(ConceptPrimaryDark, Color(0xFF1E293B))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (cover1.isNotBlank()) {
                AsyncImage(
                    model = cover1,
                    contentDescription = "今日推荐封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}

/**
 * 巅峰排行榜精美预览卡片（展示榜单封面 + 前三名预览曲目 + 点击进入独立榜单页）
 */
@Composable
private fun RankCardView(
    rank: RankItem,
    onCardClick: () -> Unit,
    onPlayRank: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF141B29) else Color(0xFFEBF3FC)
        ),
        modifier = Modifier
            .width(276.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onCardClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF1B2845))))
                ) {
                    if (rank.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = rank.coverUrl,
                            contentDescription = rank.rankName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Leaderboard,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rank.rankName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = rank.updateFrequency.ifBlank { "每日实时更新" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onPlayRank,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircleFilled,
                        contentDescription = "播放榜单",
                        tint = ConceptPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            if (rank.topSongsPreview.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rank.topSongsPreview.take(3).forEachIndexed { idx, line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${idx + 1}",
                                color = if (idx == 0) VipGold else ConceptPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                modifier = Modifier.width(16.dp)
                            )
                            Text(
                                text = line.replace(Regex("^\\d+\\.\\s*"), ""),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                fontSize = 11.5.sp,
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
