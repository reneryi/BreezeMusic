package com.concept.music.ui.home

import android.widget.Toast
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
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.conceptSheetContainerColor
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
    onOpenYouthPlaza: () -> Unit = {},
    onOpenYouthChannel: (String) -> Unit = {},
    onOpenRadio: (String) -> Unit = {},
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dailySongs by viewModel.dailyRecommendSongs.collectAsState()
    val personalFmSongs by viewModel.personalFmSongs.collectAsState()
    val youthChannels by viewModel.youthChannels.collectAsState()
    val youthAmways by viewModel.youthChannelAmways.collectAsState()
    val fmChannels by viewModel.fmChannels.collectAsState()
    val ranks by viewModel.ranks.collectAsState()
    val loading by viewModel.loading.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = LocalIsDarkTheme.current

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

    // 私人随心听 FM 漫游候选列表抽屉
    var detailSheetTitle by remember { mutableStateOf("") }
    var detailSheetSubtitle by remember { mutableStateOf("") }
    var detailSheetCover by remember { mutableStateOf("") }
    var detailSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var detailSheetLoading by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }

    // 发现页内直接打开的全量榜单分类宫格选择器
    var showRankGridSheet by remember { mutableStateOf(false) }
    var selectedRankCategory by remember { mutableStateOf("全部") }

    var showFocusPodSheet by remember { mutableStateOf(false) }
    val focusPodState by FocusPodManager.state.collectAsState()

    fun openPersonalFmListSheet() {
        val pool = personalFmSongs.ifEmpty { dailySongs }
        detailSheetTitle = "私人随心听 FM"
        detailSheetSubtitle = "根据你的听歌偏好实时生成的漫游候选队列（可随时换一批）"
        detailSheetCover = pool.firstOrNull()?.coverUrl.orEmpty()
        detailSheetSongs = pool
        detailSheetLoading = pool.isEmpty()
        showDetailSheet = true
        if (pool.isEmpty()) {
            viewModel.refreshPersonalFm { loaded ->
                detailSheetSongs = loaded
                detailSheetCover = loaded.firstOrNull()?.coverUrl.orEmpty()
                detailSheetLoading = false
            }
        }
    }

    val coverPool = remember(dailySongs, personalFmSongs, ranks) {
        (dailySongs.mapNotNull { it.coverUrl.takeIf { u -> u.isNotBlank() } } +
            personalFmSongs.mapNotNull { it.coverUrl.takeIf { u -> u.isNotBlank() } } +
            ranks.mapNotNull { it.coverUrl.takeIf { u -> u.isNotBlank() } }).distinct()
    }

    val rankCategories = remember(ranks) {
        listOf("全部") + ranks.map { it.category.ifBlank { "热门" } }.distinct()
    }
    val displayedRanks = remember(ranks, selectedRankCategory) {
        if (selectedRankCategory == "全部") ranks
        else ranks.filter { (it.category.ifBlank { "热门" }) == selectedRankCategory }
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (loading && dailySongs.isEmpty()) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape),
                        color = ConceptPrimary,
                        trackColor = Color.Transparent
                    )
                }
            }

            // 1. 顶部标题与无边框悬浮搜索胶囊
            item {
                DiscoverTopHeader(onSearchClick = onSearchClick)
            }

            // 2. 每日推荐大横幅（真实三封面错位层叠 + 深浅色自适应水彩/深海卡片）
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

            // 3. 私人随心听 FM（无边框饱满悬浮块）
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

            // 4. 巅峰排行榜（移至前面！支持分类胶囊筛选 + 一键展开全榜单宫格面板 + 饱满圆润新卡片）
            if (ranks.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "巅峰排行榜",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "每日实时更新 · 支持分类快选与全量曲目加载",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = ConceptPrimary.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { showRankGridSheet = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.GridView,
                                            contentDescription = "榜单宫格",
                                            tint = ConceptPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "榜单宫格",
                                            color = ConceptPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        val firstId = displayedRanks.firstOrNull()?.rankId ?: ranks.firstOrNull()?.rankId ?: 6666
                                        onOpenRank(firstId)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "进入专区 >",
                                        color = ConceptPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 发现页内直接分类筛选榜单，无需盲目横滑查找
                        if (rankCategories.size > 2) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                items(rankCategories) { cat ->
                                    val selected = cat == selectedRankCategory
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selected) ConceptPrimary else conceptFloatingCardColor(),
                                        shadowElevation = if (!isDark && !selected) 2.dp else 0.dp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { selectedRankCategory = cat }
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(displayedRanks, key = { it.rankId }) { rank ->
                                RankCardView(
                                    rank = rank,
                                    onCardClick = { onOpenRank(rank.rankId) },
                                    onPlayRank = {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "正在装载【${rank.rankName}】全部热歌...", Toast.LENGTH_SHORT).show()
                                            val songs = viewModel.getRankSongsAll(rank.rankId, maxSongs = 300)
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

            // 5. 概念专属频道（对齐官方 7 大频道接口：频道广场、频道安利、音乐故事、订阅、相似频道）
            if (youthChannels.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "概念专属频道",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "音乐故事 · 频道安利 · 订阅与相似频道漫游",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            TextButton(
                                onClick = onOpenYouthPlaza,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "频道广场 >",
                                    color = ConceptPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 顶部精选频道安利条（点击直接进入对应频道详情页）
                        val featuredAmway = youthAmways.firstOrNull()
                        if (featuredAmway != null) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = conceptFloatingCardColor(),
                                shadowElevation = if (isDark) 0.dp else 4.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        if (featuredAmway.channelId.isNotBlank()) {
                                            onOpenYouthChannel(featuredAmway.channelId)
                                        } else {
                                            onOpenYouthPlaza()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = VipGold.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "频道安利",
                                            color = VipGold,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "“${featuredAmway.content}”",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.refreshYouthChannelAmways() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = "换一条安利",
                                            tint = ConceptPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(youthChannels, key = { _, it -> it.id }) { idx, channel ->
                                val fallbackCover = coverPool.getOrNull(idx % coverPool.size.coerceAtLeast(1)).orEmpty()
                                YouthChannelCardView(
                                    channel = channel,
                                    fallbackCoverUrl = fallbackCover,
                                    index = idx,
                                    onCardClick = { onOpenYouthChannel(channel.id) },
                                    onToggleSubscribe = {
                                        viewModel.toggleSubscribeYouthChannel(channel) { _, _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onPlayChannel = {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "正在为您开启【${channel.name}】最新音乐故事曲目...", Toast.LENGTH_SHORT).show()
                                            val songs = viewModel.getYouthChannelSongs(channel.id, forceNextPage = true)
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

            // 6. 精选场景电台（点击进入独立电台页 RadioScreen，每次刷新拉取全新偏移曲目）
            if (fmChannels.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "精选场景电台",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "动态频率漫游 · 每次刷新拉取全新曲库",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            TextButton(
                                onClick = {
                                    val firstId = fmChannels.firstOrNull()?.id.orEmpty()
                                    onOpenRadio(firstId)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "电台专区 >",
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
                            itemsIndexed(fmChannels, key = { _, it -> it.id }) { idx, channel ->
                                val fallbackCover = coverPool.getOrNull((idx + 3) % coverPool.size.coerceAtLeast(1)).orEmpty()
                                FmChannelCardView(
                                    channel = channel,
                                    fallbackCoverUrl = fallbackCover,
                                    index = idx,
                                    onCardClick = { onOpenRadio(channel.id) },
                                    onPlayFmChannel = {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "正在调频至【${channel.name}】最新曲目流...", Toast.LENGTH_SHORT).show()
                                            val songs = viewModel.getFmChannelSongs(channel.id, forceNextBatch = true)
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

            // 7. 概念胶囊工作台 (Focus Pod) —— 调整至发现页底部，采用无边框饱满悬浮块设计
            item {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = conceptFloatingCardColor(),
                    shadowElevation = if (isDark) 0.dp else 5.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .clickable { showFocusPodSheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ConceptPrimary.copy(alpha = 0.16f),
                            modifier = Modifier.size(50.dp)
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
                                        shape = RoundedCornerShape(8.dp),
                                        color = ConceptPrimary.copy(alpha = 0.18f)
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
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = { showFocusPodSheet = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ConceptPrimary.copy(alpha = 0.16f),
                                contentColor = ConceptPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = if (focusPodState.isRunning) "控制台" else "开启心流",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

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

    // 发现页直接呼出的全量榜单宫格选择器
    if (showRankGridSheet) {
        RankGridPickerSheet(
            ranks = ranks,
            selectedRankId = null,
            onSelectRank = { chosenId ->
                showRankGridSheet = false
                onOpenRank(chosenId)
            },
            onDismiss = { showRankGridSheet = false }
        )
    }

    // 私人随心听 FM 曲目列表浏览抽屉
    if (showDetailSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDetailSheet = false },
            containerColor = conceptSheetContainerColor(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
                            .clip(RoundedCornerShape(18.dp))
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
                    val songs = viewModel.getYouthChannelSongs(channelId, forceNextPage = true)
                    if (songs.isNotEmpty()) {
                        onSongClick(songs.first(), songs)
                    }
                }
            }
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
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onOpenList)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(18.dp))
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
                        color = ConceptPrimary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "私人随心听 FM",
                            color = ConceptPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "漫游列表 (${songs.size}首) >",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onRefreshFm,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "换一批",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            FilledIconButton(
                onClick = onPlayFm,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "播放随心听",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 概念版专属频道圆润饱满悬浮卡片（无边框线，带订阅状态胶囊与故事感视觉）
 */
@Composable
private fun YouthChannelCardView(
    channel: YouthChannel,
    fallbackCoverUrl: String,
    index: Int,
    onCardClick: () -> Unit,
    onToggleSubscribe: () -> Unit,
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

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 5.dp,
        modifier = Modifier
            .width(178.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onCardClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(20.dp))
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )
                if (channel.tag.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = channel.tag,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // 快捷订阅胶囊
                Surface(
                    color = if (channel.isSubscribed) ConceptPrimary else Color.Black.copy(alpha = 0.46f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleSubscribe)
                ) {
                    Text(
                        text = if (channel.isSubscribed) "已订" else "+ 订阅",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                FilledIconButton(
                    onClick = onPlayChannel,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ConceptPrimary
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放频道",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
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
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = channel.description.ifBlank { "点击浏览频道故事与动态曲目" },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 精选场景电台圆润饱满悬浮卡片（无边框线，调频收音机质感）
 */
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

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 5.dp,
        modifier = Modifier
            .width(248.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onCardClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(18.dp))
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
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ConceptPrimary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "FM · ${channel.category.ifBlank { "伴听" }}",
                        color = ConceptPrimary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
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
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            FilledIconButton(
                onClick = onPlayFmChannel,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "播放电台",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 巅峰排行榜独立全屏页面
 * - 自动并发分页拉取榜单全量歌曲（例如 TOP500 全部曲目，彻底解除 50/100 首截断）
 * - 支持顶部「全部榜单宫格」一键展开多列分类榜单选择面板，告别繁琐的横向盲滑
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
    val isDark = LocalIsDarkTheme.current
    var selectedRankId by remember(initialRankId) { mutableIntStateOf(initialRankId) }
    var rankSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showRankGridSheet by remember { mutableStateOf(false) }

    val currentRank = remember(ranks, selectedRankId) {
        ranks.firstOrNull { it.rankId == selectedRankId }
            ?: ranks.firstOrNull()
            ?: RankItem(selectedRankId, "官方热门榜", "", "每日更新")
    }

    LaunchedEffect(selectedRankId) {
        loading = true
        rankSongs = viewModel.getRankSongsAll(selectedRankId, maxSongs = 500)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showRankGridSheet = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = currentRank.rankName,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = "切换榜单",
                                tint = ConceptPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = { showRankGridSheet = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ConceptPrimary.copy(alpha = 0.15f),
                                contentColor = ConceptPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .height(34.dp)
                        ) {
                            Icon(Icons.Rounded.GridView, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("全部榜单 (${ranks.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                // 1. 榜单头部无边框饱满视觉悬浮卡
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(26.dp),
                        color = conceptFloatingCardColor(),
                        shadowElevation = if (isDark) 0.dp else 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(22.dp))
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ConceptPrimary.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = currentRank.category.ifBlank { "官方热榜" },
                                            color = ConceptPrimary,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentRank.updateFrequency.ifBlank { "每日更新" },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = currentRank.rankName,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "已加载完整榜单共 ${rankSongs.size} 首热歌",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        Text("播放全部 (${filteredSongs.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    FilledTonalButton(
                                        onClick = { showRankGridSheet = true },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = conceptElevatedSurfaceColor(),
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("换榜单", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 榜单内实时搜索过滤框
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        placeholder = { Text("在 ${rankSongs.size} 首榜单曲目中搜索...", fontSize = 13.sp) },
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
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ConceptPrimary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = conceptFloatingCardColor(),
                            unfocusedContainerColor = conceptFloatingCardColor()
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
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

    if (showRankGridSheet) {
        RankGridPickerSheet(
            ranks = ranks,
            selectedRankId = currentRank.rankId,
            onSelectRank = { chosenId ->
                selectedRankId = chosenId
                showRankGridSheet = false
            },
            onDismiss = { showRankGridSheet = false }
        )
    }
}

/**
 * 全量榜单分类宫格快选抽屉（彻底解决横向滑动查找榜单繁琐的问题）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankGridPickerSheet(
    ranks: List<RankItem>,
    selectedRankId: Int?,
    onSelectRank: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    var selectedCategory by remember { mutableStateOf("全部") }
    val categories = remember(ranks) {
        listOf("全部") + ranks.map { it.category.ifBlank { "热门" } }.distinct()
    }
    val filteredRanks = remember(ranks, selectedCategory) {
        if (selectedCategory == "全部") ranks
        else ranks.filter { (it.category.ifBlank { "热门" }) == selectedCategory }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = conceptSheetContainerColor(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "选择巅峰榜单",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "按分类宫格快速直达目标榜单（共 ${ranks.size} 个官方榜）",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    val selected = cat == selectedCategory
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) ConceptPrimary else conceptElevatedSurfaceColor(),
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

            Spacer(modifier = Modifier.height(12.dp))

            val rows = filteredRanks.chunked(2)
            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(rows) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { item ->
                            val isSelected = item.rankId == selectedRankId
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) ConceptPrimary.copy(alpha = 0.16f) else conceptElevatedSurfaceColor(),
                                shadowElevation = if (!isDark && !isSelected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onSelectRank(item.rankId) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF1A2942)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.coverUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = item.coverUrl,
                                                contentDescription = item.rankName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Leaderboard,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.rankName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) ConceptPrimary else MaterialTheme.colorScheme.onSurface
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.updateFrequency.ifBlank { item.category },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
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
    val isDark = LocalIsDarkTheme.current
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
                item {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = conceptFloatingCardColor(),
                        shadowElevation = if (isDark) 0.dp else 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StaggeredThreeCovers(
                                songs = dailySongs.take(3),
                                primarySize = 84.dp,
                                secondarySize = 72.dp,
                                tertiarySize = 62.dp
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "私人定制 · 每日 6:00 更新",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "根据你的听歌口味精选 ${dailySongs.size} 首好歌",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }

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

        // 无边框沉浸式圆润搜索胶囊
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = conceptFloatingCardColor(),
            shadowElevation = if (isDark) 0.dp else 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onSearchClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 每日推荐横幅卡片（自适应深色/浅色模式色彩，消除浅色模式下的沉重黑块，并展现鲜明的三封面错位层叠）
 */
@Composable
private fun DailyRecommendBanner(
    songs: List<Song>,
    onCardClick: () -> Unit,
    onPlayAll: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val today = remember { LocalDate.now() }
    val monthStr = remember(today) { String.format("%02d", today.monthValue) }
    val dayStr = remember(today) { String.format("%02d", today.dayOfMonth) }

    val previewSummary = remember(songs) {
        if (songs.isEmpty()) "正在为你汇聚今日专属好歌..."
        else songs.take(2).joinToString(" · ") { "${it.title}-${it.artist}" }
    }

    val bgBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF153156),
                Color(0xFF10233E),
                Color(0xFF131B2E)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFD6EAFF),
                Color(0xFFE6F2FF),
                Color(0xFFF3ECFF)
            )
        )
    }

    val titleColor = if (isDark) Color.White else Color(0xFF0E1B2E)
    val subColor = if (isDark) Color.White.copy(alpha = 0.72f) else Color(0xFF3E5473)

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
        shadowElevation = if (isDark) 10.dp else 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp)
            .clip(RoundedCornerShape(30.dp))
            .clickable(onClick = onCardClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StaggeredThreeCovers(
                    songs = songs.take(3),
                    primarySize = 96.dp,
                    secondarySize = 84.dp,
                    tertiarySize = 72.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = ConceptPrimary.copy(alpha = if (isDark) 0.22f else 0.14f),
                        shape = RoundedCornerShape(10.dp)
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
                            color = titleColor,
                            fontSize = 22.sp,
                            letterSpacing = 0.5.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "含 $previewSummary",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = subColor,
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
                            fontWeight = FontWeight.Bold
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
                        .size(48.dp)
                        .shadow(8.dp, CircleShape)
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
 * 前三首歌曲封面错位层叠组件（加大横向错位偏移与旋转倾角，让第 2、3 张封面清晰探出）
 */
@Composable
private fun StaggeredThreeCovers(
    songs: List<Song>,
    primarySize: Dp = 96.dp,
    secondarySize: Dp = 84.dp,
    tertiarySize: Dp = 72.dp
) {
    val cover1 = songs.getOrNull(0)?.coverUrl.orEmpty()
    val cover2 = songs.getOrNull(1)?.coverUrl?.takeIf { it.isNotBlank() } ?: cover1
    val cover3 = songs.getOrNull(2)?.coverUrl?.takeIf { it.isNotBlank() } ?: cover2

    Box(
        modifier = Modifier
            .width(primarySize + 44.dp)
            .height(primarySize + 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // 第三张封面：最右侧探出 + 明显倾角
        Box(
            modifier = Modifier
                .size(tertiarySize)
                .offset(x = 46.dp, y = 6.dp)
                .graphicsLayer {
                    rotationZ = 14f
                    alpha = 0.72f
                }
                .shadow(6.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf( Color(0xFF3A5A8C), Color(0xFF1F3252))))
        ) {
            if (cover3.isNotBlank()) {
                AsyncImage(
                    model = cover3,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 第二张封面：中层错位探出
        Box(
            modifier = Modifier
                .size(secondarySize)
                .offset(x = 24.dp, y = 2.dp)
                .graphicsLayer {
                    rotationZ = 7f
                    alpha = 0.88f
                }
                .shadow(8.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF284B7E), Color(0xFF182A49))))
        ) {
            if (cover2.isNotBlank()) {
                AsyncImage(
                    model = cover2,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 第一张主封面：前景微逆倾，立体悬浮
        Box(
            modifier = Modifier
                .size(primarySize)
                .graphicsLayer {
                    rotationZ = -3f
                }
                .shadow(12.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(ConceptPrimaryDark, Color(0xFF1E293B))
                    )
                ),
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
 * 巅峰排行榜圆润饱满悬浮卡片（无边框线，展示榜单分类角标、封面与前三名热歌）
 */
@Composable
private fun RankCardView(
    rank: RankItem,
    onCardClick: () -> Unit,
    onPlayRank: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 5.dp,
        modifier = Modifier
            .width(282.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onCardClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(18.dp))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ConceptPrimary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = rank.category.ifBlank { "热榜" },
                                color = ConceptPrimary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = rank.updateFrequency.ifBlank { "每日更新" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rank.rankName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledIconButton(
                    onClick = onPlayRank,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放榜单",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (rank.topSongsPreview.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = conceptElevatedSurfaceColor(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        rank.topSongsPreview.take(3).forEachIndexed { idx, line ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${idx + 1}",
                                    color = if (idx == 0) VipGold else ConceptPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.width(18.dp)
                                )
                                Text(
                                    text = line.replace(Regex("^\\d+\\.\\s*"), ""),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
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
}
