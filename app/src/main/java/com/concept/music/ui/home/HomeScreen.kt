package com.concept.music.ui.home

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.concept.music.ui.components.BadgeTag
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
import java.time.ZoneId

private val DISCOVER_CHINA_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

private data class NewSongPeriodTab(
    val key: String,
    val label: String,
    val badge: String
)

private val NEW_SONG_PERIOD_TABS = listOf(
    NewSongPeriodTab("TODAY", "今日最新", "今日首发"),
    NewSongPeriodTab("DAY3", "近3日", "3日热推"),
    NewSongPeriodTab("DAY5", "近5日", "5日精选"),
    NewSongPeriodTab("CHINESE", "华语", "华语新声"),
    NewSongPeriodTab("WESTERN", "欧美", "欧美前沿"),
    NewSongPeriodTab("JPKR", "日韩", "日韩潮流")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSongClick: (Song, List<Song>) -> Unit,
    onLogin: () -> Unit,
    onSearchClick: () -> Unit = {},
    onOpenDailyRecommend: () -> Unit = {},
    onOpenNewSongExpress: () -> Unit = {},
    onOpenRank: (Int) -> Unit = {},
    onOpenRadio: (String) -> Unit = {},
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    onSongMoreClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dailySongs by viewModel.dailyRecommendSongs.collectAsState()
    val personalFmSongs by viewModel.personalFmSongs.collectAsState()
    val personalFmIndex by viewModel.personalFmIndex.collectAsState()
    val personalFmRefreshing by viewModel.personalFmRefreshing.collectAsState()
    val selectedNewSongPeriod by viewModel.selectedNewSongPeriod.collectAsState()
    val currentNewSongs by viewModel.currentNewSongs.collectAsState()
    val newSongsLoading by viewModel.newSongsLoading.collectAsState()
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
    var showPersonalFmSheet by remember { mutableStateOf(false) }

    // 单个场景电台快捷漫游抽屉（与「电台专区」独立区分）
    var quickFmStation by remember { mutableStateOf<FmChannel?>(null) }
    var quickFmSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var quickFmLoading by remember { mutableStateOf(false) }
    var quickFmLoadingMore by remember { mutableStateOf(false) }

    // 发现页内直接打开的全量榜单分类宫格选择器
    var showRankGridSheet by remember { mutableStateOf(false) }
    var selectedRankCategory by remember { mutableStateOf("全部") }

    // 若当前播放歌曲在随心听列表中，自动同步随心听指针位置
    val fmPool = remember(personalFmSongs, dailySongs) {
        personalFmSongs.ifEmpty { dailySongs.take(20) }
    }
    LaunchedEffect(currentSong?.id, currentSong?.hash, fmPool) {
        if (currentSong != null && fmPool.isNotEmpty()) {
            val matchedIdx = fmPool.indexOfFirst { s ->
                (s.id.isNotBlank() && s.id == currentSong.id) ||
                    (s.hash.isNotBlank() && s.hash.equals(currentSong.hash, ignoreCase = true))
            }
            if (matchedIdx >= 0 && matchedIdx != personalFmIndex) {
                viewModel.setPersonalFmIndex(matchedIdx)
            }
        }
    }

    fun openFmStationQuickSheet(channel: FmChannel) {
        quickFmStation = channel
        quickFmLoading = true
        coroutineScope.launch {
            val loaded = viewModel.getFmChannelSongs(channel.id, forceNextBatch = false)
            quickFmSongs = loaded
            quickFmLoading = false
        }
    }

    val coverPool = remember(dailySongs, personalFmSongs, currentNewSongs, ranks) {
        (dailySongs.mapNotNull { it.coverUrl.takeIf { u -> u.isNotBlank() } } +
            currentNewSongs.mapNotNull { it.coverUrl.takeIf { u -> u.isNotBlank() } } +
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

            // 1. 顶部标题与右上角长圆形搜索按钮 + 全时段精细化问候文案
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

            // 3. 私人随心听 FM（猜你喜欢定位 · 类播放器交互卡片：仅保留暂停/播放、下一首、刷新全新20首，点击卡片查看列表）
            item {
                val safeIdx = if (fmPool.isEmpty()) 0 else personalFmIndex.coerceIn(0, fmPool.lastIndex)
                val activeFmSong = fmPool.getOrNull(safeIdx)
                val nextFmSong = if (fmPool.size > 1) fmPool.getOrNull((safeIdx + 1) % fmPool.size) else null
                val isCurrentInFm = currentSong != null && activeFmSong != null && (
                    (currentSong.id.isNotBlank() && currentSong.id == activeFmSong.id) ||
                        (currentSong.hash.isNotBlank() && currentSong.hash.equals(activeFmSong.hash, ignoreCase = true))
                    )
                val isFmPlayingNow = isCurrentInFm && isPlaying

                PersonalFmPlayerCard(
                    songs = fmPool,
                    activeSong = activeFmSong,
                    nextSong = nextFmSong,
                    currentIndex = safeIdx,
                    isPlaying = isFmPlayingNow,
                    isRefreshing = personalFmRefreshing,
                    onOpenList = { showPersonalFmSheet = true },
                    onTogglePlayPause = {
                        if (isCurrentInFm) {
                            onTogglePlayPause()
                        } else if (activeFmSong != null) {
                            onSongClick(activeFmSong, fmPool)
                        } else {
                            viewModel.refreshPersonalFm { loaded ->
                                if (loaded.isNotEmpty()) {
                                    onSongClick(loaded.first(), loaded)
                                }
                            }
                        }
                    },
                    onSkipNext = {
                        viewModel.skipToNextPersonalFm { nextSong, list ->
                            onSongClick(nextSong, list)
                        }
                    },
                    onRefreshNewBatch = {
                        viewModel.refreshPersonalFm { loaded ->
                            if (loaded.isNotEmpty()) {
                                onSongClick(loaded.first(), loaded)
                                Toast.makeText(context, "已为你焕新 ${loaded.size} 首猜你喜欢新曲", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // 4. 概念新歌速递（支持今日最新 / 近3日 / 近5日 / 华语 / 欧美 / 日韩实时切换，每日自动刷新）
            item {
                NewSongExpressSectionCard(
                    selectedPeriod = selectedNewSongPeriod,
                    songs = currentNewSongs,
                    loading = newSongsLoading,
                    currentSong = currentSong,
                    onSelectPeriod = { periodKey ->
                        viewModel.selectNewSongPeriod(periodKey, forceRefresh = false)
                    },
                    onRefreshPeriod = {
                        viewModel.selectNewSongPeriod(selectedNewSongPeriod, forceRefresh = true)
                        Toast.makeText(context, "正在实时同步最新发行单曲...", Toast.LENGTH_SHORT).show()
                    },
                    onOpenFullScreen = onOpenNewSongExpress,
                    onPlayAll = {
                        if (currentNewSongs.isNotEmpty()) {
                            onSongClick(currentNewSongs.first(), currentNewSongs)
                        }
                    },
                    onPlaySong = { song ->
                        onSongClick(song, currentNewSongs)
                    }
                )
            }

            // 5. 巅峰排行榜（删除冗余「进入专区 >」，保留「榜单宫格」快选与分类胶囊，每日实时更新）
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
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ConceptPrimary.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showRankGridSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GridView,
                                        contentDescription = "榜单宫格",
                                        tint = ConceptPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "榜单宫格 (${ranks.size})",
                                        color = ConceptPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

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

            // 6. 精选场景电台（大气饱满新卡片设计：点击单卡片弹出该电台 20 首即刻漫游抽屉；点击「电台专区 >」进入全新电台调频总控广场页）
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
                                    text = "分类严选 20 首漫游 · 点击卡片速听 · 专区支持全频段调频",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ConceptPrimary.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenRadio("all") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Radio,
                                        contentDescription = "电台专区",
                                        tint = ConceptPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "电台专区 >",
                                        color = ConceptPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(fmChannels, key = { _, it -> it.id }) { idx, channel ->
                                val fallbackCover = coverPool.getOrNull((idx + 2) % coverPool.size.coerceAtLeast(1)).orEmpty()
                                FmChannelCardView(
                                    channel = channel,
                                    fallbackCoverUrl = fallbackCover,
                                    index = idx,
                                    onCardClick = { openFmStationQuickSheet(channel) },
                                    onPlayFmChannel = {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "正在调频至【${channel.name}】精选 20 首曲目...", Toast.LENGTH_SHORT).show()
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

    // 私人随心听 FM（猜你喜欢 20 首）候选列表抽屉
    if (showPersonalFmSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPersonalFmSheet = false },
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
                    val coverUrl = fmPool.getOrNull(personalFmIndex)?.coverUrl ?: fmPool.firstOrNull()?.coverUrl.orEmpty()
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF162844)))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = "私人随心听",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "私人随心听 · 猜你喜欢 (${fmPool.size}首)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "每次刷新均从多源推荐池拉取全新 20 首曲目，绝不重复洗牌",
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
                            viewModel.refreshPersonalFm { loaded ->
                                Toast.makeText(context, "已焕新 ${loaded.size} 首猜你喜欢新歌", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !personalFmRefreshing
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "换一批新歌", tint = ConceptPrimary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (fmPool.isNotEmpty()) {
                                onSongClick(fmPool.first(), fmPool)
                                showPersonalFmSheet = false
                            }
                        },
                        enabled = fmPool.isNotEmpty(),
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

                if (personalFmRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        color = ConceptPrimary
                    )
                }

                if (fmPool.isEmpty() && !personalFmRefreshing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无曲目数据，请点击右上角刷新", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 460.dp)
                    ) {
                        itemsIndexed(fmPool, key = { idx, s -> "pfm_${s.id}_${s.hash}_$idx" }) { index, song ->
                            SongItemRow(
                                song = song,
                                orderIndex = index + 1,
                                isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                                onClick = {
                                    viewModel.setPersonalFmIndex(index)
                                    onSongClick(song, fmPool)
                                    showPersonalFmSheet = false
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

    // 单个场景电台快捷预览与漫游抽屉（与「电台专区」独立区分）
    if (quickFmStation != null) {
        val station = quickFmStation!!
        ModalBottomSheet(
            onDismissRequest = { quickFmStation = null },
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
                    val coverUrl = station.coverUrl.ifBlank { quickFmSongs.firstOrNull()?.coverUrl.orEmpty() }
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF162844)))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = station.name,
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ConceptPrimary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "FM · ${station.category.ifBlank { "精选电台" }}",
                                    color = ConceptPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "已加载 ${quickFmSongs.size} 首专属曲目",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = station.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = {
                            val targetId = station.id
                            quickFmStation = null
                            onOpenRadio(targetId)
                        }
                    ) {
                        Text("进入调频台 >", color = ConceptPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (quickFmSongs.isNotEmpty()) {
                                onSongClick(quickFmSongs.first(), quickFmSongs)
                                quickFmStation = null
                            }
                        },
                        enabled = quickFmSongs.isNotEmpty() && !quickFmLoading,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ConceptPrimary, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("播放电台 (${quickFmSongs.size}首)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            quickFmLoading = true
                            coroutineScope.launch {
                                val nextBatch = viewModel.getFmChannelSongs(station.id, forceNextBatch = true)
                                if (nextBatch.isNotEmpty()) {
                                    quickFmSongs = nextBatch
                                    Toast.makeText(context, "已为【${station.name}】换一批 ${nextBatch.size} 首新歌", Toast.LENGTH_SHORT).show()
                                }
                                quickFmLoading = false
                            }
                        },
                        enabled = !quickFmLoading,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("换一批(20首)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            if (!quickFmLoadingMore) {
                                quickFmLoadingMore = true
                                coroutineScope.launch {
                                    val more = viewModel.getFmChannelSongs(station.id, forceNextBatch = true)
                                    val merged = (quickFmSongs + more).distinctBy { it.hash.ifBlank { it.id } }
                                    val addedCount = (merged.size - quickFmSongs.size).coerceAtLeast(0)
                                    quickFmSongs = merged
                                    quickFmLoadingMore = false
                                    Toast.makeText(context, "已继续加载 $addedCount 首符合「${station.name}」的歌曲", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !quickFmLoadingMore && !quickFmLoading,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(if (quickFmLoadingMore) "加载中" else "+20首", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (quickFmLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        color = ConceptPrimary
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        itemsIndexed(quickFmSongs, key = { idx, s -> "qfm_${s.id}_${s.hash}_$idx" }) { index, song ->
                            SongItemRow(
                                song = song,
                                orderIndex = index + 1,
                                isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                                onClick = {
                                    onSongClick(song, quickFmSongs)
                                    quickFmStation = null
                                },
                                onMoreClick = { onSongMoreClick(song) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

/**
 * 发现页顶部标题栏：
 * 1. 去除大体积通栏搜索框，改为右上角精巧长圆形胶囊搜索按钮
 * 2. 发现标题下方时间问候文案覆盖「早晨(清晨)、上午、午间、下午、傍晚、晚上、深夜」7 个精细时段
 */
@Composable
private fun DiscoverTopHeader(
    onSearchClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val timeGreeting = remember {
        val now = runCatching { LocalTime.now(DISCOVER_CHINA_ZONE) }.getOrElse { LocalTime.now() }
        val minutes = now.hour * 60 + now.minute
        when (minutes) {
            in (5 * 60) until (8 * 60) -> "早晨" to "晨光微熹 · 用第一缕旋律唤醒耳朵"
            in (8 * 60) until (11 * 60 + 30) -> "上午" to "清风伴读 · 轻快节拍开启元气上午"
            in (11 * 60 + 30) until (14 * 60) -> "午间" to "午间小憩 · 卸下忙碌享片刻松弛"
            in (14 * 60) until (17 * 60) -> "下午" to "午后漫游 · 一杯咖啡与恰到好处的律动"
            in (17 * 60) until (19 * 60) -> "傍晚" to "暮色微醺 · 晚风与落日余晖皆入歌"
            in (19 * 60) until (22 * 60 + 30) -> "晚上" to "夜幕微蓝 · 卸下喧嚣沉浸私人旋律"
            else -> "深夜" to "深夜静谧 · 耳机里的温柔陪你入眠"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "发现",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ConceptPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = timeGreeting.first,
                        color = ConceptPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeGreeting.second,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.2.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 右上角精巧长圆形搜索按钮（不过长，视觉灵动）
        Surface(
            shape = CircleShape,
            color = conceptFloatingCardColor(),
            shadowElevation = if (isDark) 0.dp else 4.dp,
            modifier = Modifier
                .height(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onSearchClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "搜索",
                    tint = ConceptPrimary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "搜索",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 私人随心听 FM（猜你喜欢定位 · 类播放器效果卡片）
 * - 仅保留三个核心按钮：刷新（拉取全新 20 首不重复推荐）、播放/暂停、下一首
 * - 点击卡片主体同样弹出 20 首随心听候选列表
 */
@Composable
private fun PersonalFmPlayerCard(
    songs: List<Song>,
    activeSong: Song?,
    nextSong: Song?,
    currentIndex: Int,
    isPlaying: Boolean,
    isRefreshing: Boolean,
    onOpenList: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onRefreshNewBatch: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = tween(durationMillis = 750),
        label = "fmRefreshRotation"
    )

    val playerCardBrush = if (isDark) {
        Brush.linearGradient(
            listOf(Color(0xFF142642), Color(0xFF101C30), Color(0xFF141B2D))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFEAF4FF), Color(0xFFF3F8FF), Color(0xFFF6F1FF))
        )
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        shadowElevation = if (isDark) 0.dp else 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onOpenList)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(playerCardBrush)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // 顶部状态栏：私人随心听 · 猜你喜欢 + 漫游列表入口
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = ConceptPrimary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = ConceptPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "私人随心听 · 猜你喜欢",
                                color = ConceptPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (songs.isNotEmpty()) "正在漫游 ${currentIndex + 1}/${songs.size}" else "实时个性推荐",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "查看列表 (${songs.size}首) >",
                        color = ConceptPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 中部类播放器主体：专辑黑胶感封面 + 当前/下一首曲目信息 + 仅有的 3 个控制按钮（刷新、播放/暂停、下一首）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(6.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF1A2B4C)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (!activeSong?.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = activeSong!!.coverUrl,
                            contentDescription = activeSong.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeSong?.title ?: "点击开启猜你喜欢漫游",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeSong?.isVip == true) {
                            BadgeTag(text = "VIP", color = VipGold)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (activeSong?.isHiRes == true) {
                            BadgeTag(text = "Hi-Res", color = VipGold)
                            Spacer(modifier = Modifier.width(4.dp))
                        } else if (activeSong?.isLossless == true) {
                            BadgeTag(text = "SQ", color = ConceptPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = activeSong?.artist ?: "智能口味探测引擎",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (nextSong != null) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "下一首：${nextSong.title} - ${nextSong.artist}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 播放器三键控制区：1. 刷新（全新20首） 2. 播放/暂停 3. 下一首
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onRefreshNewBatch,
                        enabled = !isRefreshing,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = ConceptPrimary.copy(alpha = 0.14f),
                            contentColor = ConceptPrimary
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "换一批新歌",
                            modifier = Modifier
                                .size(19.dp)
                                .rotate(rotationAngle)
                        )
                    }

                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = ConceptPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(46.dp)
                            .shadow(6.dp, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "暂停随心听" else "播放随心听",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onSkipNext,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = ConceptPrimary.copy(alpha = 0.14f),
                            contentColor = ConceptPrimary
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "下一首",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 概念新歌速递卡片（自主设计：支持「今日最新 / 近3日 / 近5日 / 华语 / 欧美 / 日韩」切换，顶部 3 张焦点首发卡 + 底部 3 首速听条）
 */
@Composable
private fun NewSongExpressSectionCard(
    selectedPeriod: String,
    songs: List<Song>,
    loading: Boolean,
    currentSong: Song?,
    onSelectPeriod: (String) -> Unit,
    onRefreshPeriod: () -> Unit,
    onOpenFullScreen: () -> Unit,
    onPlayAll: () -> Unit,
    onPlaySong: (Song) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val todayStr = remember {
        val date = runCatching { LocalDate.now(DISCOVER_CHINA_ZONE) }.getOrElse { LocalDate.now() }
        String.format("%02d.%02d 实时更新", date.monthValue, date.dayOfMonth)
    }
    val currentTabMeta = remember(selectedPeriod) {
        NEW_SONG_PERIOD_TABS.firstOrNull { it.key == selectedPeriod } ?: NEW_SONG_PERIOD_TABS.first()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "概念新歌速递",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ConceptPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = todayStr,
                            color = ConceptPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "实时获取最近新歌 · 支持今日与近3日/5日多维速递",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRefreshPeriod,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "刷新新歌",
                        tint = ConceptPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                TextButton(
                    onClick = onOpenFullScreen,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "全部 (${songs.size.coerceAtLeast(30)}首) >",
                        color = ConceptPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 多时段 / 多语种切换胶囊栏
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            items(NEW_SONG_PERIOD_TABS, key = { it.key }) { tab ->
                val selected = tab.key == selectedPeriod
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) ConceptPrimary else conceptFloatingCardColor(),
                    shadowElevation = if (!isDark && !selected) 2.dp else 0.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectPeriod(tab.key) }
                ) {
                    Text(
                        text = tab.label,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 主体饱满悬浮卡片
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = conceptFloatingCardColor(),
            shadowElevation = if (isDark) 0.dp else 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (loading && songs.isEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        color = ConceptPrimary
                    )
                } else if (songs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("正在同步今日新歌速递...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    // 第一层：前 3 首焦点首发视觉大卡
                    val spotlightSongs = songs.take(3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        spotlightSongs.forEachIndexed { idx, song ->
                            val isThisPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true)))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onPlaySong(song) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF1B2B48))))
                                ) {
                                    if (song.coverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = song.coverUrl,
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Bolt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .align(Alignment.Center)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.55f))
                                                )
                                            )
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = if (idx == 0) VipGold.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.52f),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = if (idx == 0) "TOP 1" else currentTabMeta.badge,
                                            color = if (idx == 0) Color(0xFF1A1200) else Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = if (isThisPlaying) VipGold else ConceptPrimary,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isThisPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isThisPlaying) Color(0xFF1A1200) else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isThisPlaying) ConceptPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 第二层：第 4~6 首紧凑新歌速听栏 + 一键播放全部新歌按钮
                    val secondarySongs = songs.drop(3).take(3)
                    if (secondarySongs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = conceptElevatedSurfaceColor(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                secondarySongs.forEachIndexed { idx, song ->
                                    val isThisPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true)))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onPlaySong(song) }
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "0${idx + 4}",
                                            color = ConceptPrimary,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier.width(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                color = if (isThisPlaying) ConceptPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = song.artist + if (song.album.isNotBlank()) " · 《${song.album}》" else "",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (song.isHiRes) {
                                            BadgeTag(text = "Hi-Res", color = VipGold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                        } else if (song.isLossless) {
                                            BadgeTag(text = "SQ", color = ConceptPrimary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.PlayCircleFilled,
                                            contentDescription = "播放",
                                            tint = ConceptPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "当前分类共汇聚 ${songs.size} 首近期首发单曲",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                        FilledTonalButton(
                            onClick = onPlayAll,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ConceptPrimary.copy(alpha = 0.16f),
                                contentColor = ConceptPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("播放全部 ${songs.size} 首", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 概念新歌速递独立全屏页（支持今日最新 / 近3日 / 近5日 / 华语 / 欧美 / 日韩全量畅听）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSongExpressScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {}
) {
    val selectedPeriod by viewModel.selectedNewSongPeriod.collectAsState()
    val songs by viewModel.currentNewSongs.collectAsState()
    val loading by viewModel.newSongsLoading.collectAsState()
    val isDark = LocalIsDarkTheme.current
    val today = remember { runCatching { LocalDate.now(DISCOVER_CHINA_ZONE) }.getOrElse { LocalDate.now() } }

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "概念新歌速递",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${today.monthValue}月${today.dayOfMonth}日 实时曲库已同步",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.selectNewSongPeriod(selectedPeriod, forceRefresh = true) }
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = ConceptPrimary)
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
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(NEW_SONG_PERIOD_TABS, key = { it.key }) { tab ->
                            val selected = tab.key == selectedPeriod
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) ConceptPrimary else conceptFloatingCardColor(),
                                shadowElevation = if (!isDark && !selected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { viewModel.selectNewSongPeriod(tab.key, forceRefresh = false) }
                            ) {
                                Text(
                                    text = "${tab.label} · ${tab.badge}",
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.5.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (songs.isNotEmpty()) {
                                    onSongClick(songs.first(), songs)
                                }
                            },
                            enabled = songs.isNotEmpty(),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ConceptPrimary, contentColor = Color.White)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("播放全部新歌 (${songs.size})", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
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

                itemsIndexed(songs, key = { idx, s -> "nse_${s.id}_${s.hash}_$idx" }) { index, song ->
                    Box(modifier = Modifier.padding(horizontal = 12.dp)) {
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
}

/**
 * 精选场景电台大气饱满悬浮卡片（重新设计：告别原先小气的窄条，升级为 178dp 宽立体封面调频卡）
 * - 点击卡片本体：弹出当前电台的 20 首分类漫游抽屉
 * - 点击播放按钮：立即拉取 20 首该电台分类歌曲并播放
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
    val freqText = remember(channel.id, index) {
        val base = 88.6 + ((channel.id.hashCode().and(0x7fffffff) % 180) * 0.1)
        String.format("FM %.1f", base)
    }
    val fallbackBrush = remember(index) {
        when (index % 4) {
            0 -> Brush.linearGradient(listOf(Color(0xFF163A5F), Color(0xFF0E2138)))
            1 -> Brush.linearGradient(listOf(Color(0xFF1A3E4C), Color(0xFF0E242E)))
            2 -> Brush.linearGradient(listOf(Color(0xFF282B54), Color(0xFF141730)))
            else -> Brush.linearGradient(listOf(Color(0xFF33264D), Color(0xFF181228)))
        }
    }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 6.dp,
        modifier = Modifier
            .width(178.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onCardClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
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
                        imageVector = Icons.Rounded.Radio,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier
                            .size(52.dp)
                            .align(Alignment.Center)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.62f)
                                )
                            )
                        )
                )

                // 左上角频率波段徽章
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.48f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = ConceptPrimary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = freqText,
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 右上角分类胶囊
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ConceptPrimary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = channel.category.ifBlank { "伴听" },
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }

                // 左下角收听人数或20首标识
                Text(
                    text = "每次 20 首严选",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 10.dp)
                )

                // 右下角一键播放按钮
                FilledIconButton(
                    onClick = onPlayFmChannel,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(38.dp)
                        .shadow(6.dp, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放电台",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = channel.description.ifBlank { "分类精选曲库 · 点击查看20首漫游列表" },
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
                                        text = currentRank.updateFrequency.ifBlank { "每日实时更新" },
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
 * 全量榜单分类宫格快选抽屉
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
    val today = remember { runCatching { LocalDate.now(DISCOVER_CHINA_ZONE) }.getOrElse { LocalDate.now() } }
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
    val today = remember { runCatching { LocalDate.now(DISCOVER_CHINA_ZONE) }.getOrElse { LocalDate.now() } }
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
 * 前三首歌曲封面错位层叠组件
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
                .background(Brush.linearGradient(listOf(Color(0xFF3A5A8C), Color(0xFF1F3252))))
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
 * 巅峰排行榜圆润饱满悬浮卡片
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
