package com.concept.music.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.RankItem
import com.concept.music.data.model.Song
import com.concept.music.ui.components.BadgeTag
import com.concept.music.ui.components.FloatingScrollToTopButton
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptAmberAccent
import com.concept.music.ui.theme.ConceptCoralAccent
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptMintAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.ConceptVioletAccent
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.ConceptCategoryTabItem
import com.concept.music.ui.theme.ConceptFluidRefreshIcon
import com.concept.music.ui.theme.conceptCategoryContentTransition
import com.concept.music.ui.theme.conceptSheetSpringEntrance
import com.concept.music.ui.theme.horizontalCategorySwipe
import com.concept.music.ui.theme.ConceptSlidingCategoryTabs
import com.concept.music.ui.theme.softClickable
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

// 对齐 MakcRe/KuGouMusicApi module/top_song.js: 21608华语 / 21609欧美 / 21610日韩 / 21611粤语 + 综合首发
private val NEW_SONG_PERIOD_TABS = listOf(
    NewSongPeriodTab("CHINESE", "华语", "华语"),
    NewSongPeriodTab("WESTERN", "欧美", "欧美"),
    NewSongPeriodTab("JPKR", "日韩", "日韩"),
    NewSongPeriodTab("CANTON", "粤语", "粤语"),
    NewSongPeriodTab("TODAY", "全部", "全部")
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
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.refresh(
                isBackgroundRevalidate = false,
                onFinished = { pullRefreshState.endRefresh() }
            )
        }
    }
    LaunchedEffect(loading) {
        if (!loading && pullRefreshState.isRefreshing) {
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

    // 严格隔离随心听与每日推荐数据源，绝不在随心听未加载完成时盗用每日推荐第一首歌曲的封面
    val fmPool = personalFmSongs
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
        val cached = viewModel.getCachedFmChannelSongs(channel.id)
        if (cached.isNotEmpty()) {
            quickFmSongs = cached
            quickFmLoading = false
        } else {
            quickFmLoading = true
        }
        coroutineScope.launch {
            val loaded = viewModel.getFmChannelSongs(channel.id, forceNextBatch = false)
            if (loaded.isNotEmpty()) {
                quickFmSongs = loaded
            }
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 148.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
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

            // 2. 每日推荐大横幅
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

            // 3. 私人随心听 FM（猜你喜欢定位 · 类播放器交互卡片）
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
                                Toast.makeText(context, "已根据你的听歌口味焕新 ${loaded.size} 首推荐", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // 4. 新歌速递（对接 module/top_song.js: /musicadservice/container/v1/newsong_publish，采用横向三行分栏流卡片设计）
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
                        Toast.makeText(context, "正在实时同步官方最新发行单曲...", Toast.LENGTH_SHORT).show()
                    },
                    onOpenFullScreen = onOpenNewSongExpress,
                    onPlayAll = {
                        if (currentNewSongs.isNotEmpty()) {
                            onSongClick(currentNewSongs.first(), currentNewSongs)
                        }
                    },
                    onPlaySong = { song ->
                        onSongClick(song, currentNewSongs)
                    },
                    onSongMoreClick = onSongMoreClick
                )
            }

            // 5. 巅峰排行榜（开放式排版：无胶囊按钮堆叠，采用底部多彩指示条与单层微渐变榜单卡）
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
                            Text(
                                text = "巅峰排行榜",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .softClickable { showRankGridSheet = true }
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "全部榜单",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "榜单宫格",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (rankCategories.size > 2) {
                            val rankTabItems = remember(rankCategories) {
                                rankCategories.map { cat ->
                                    val catAccent = when (cat) {
                                        "官方热榜" -> ConceptCoralAccent
                                        "特色曲风" -> ConceptVioletAccent
                                        "全球权威" -> ConceptCyanAccent
                                        else -> ConceptPrimary
                                    }
                                    ConceptCategoryTabItem(
                                        key = cat,
                                        label = cat,
                                        accentColor = catAccent
                                    )
                                }
                            }
                            ConceptSlidingCategoryTabs(
                                tabs = rankTabItems,
                                selectedKey = selectedRankCategory,
                                onSelectTab = { selectedRankCategory = it },
                                itemSpacing = 22.dp,
                                fontSize = 13.5.sp,
                                indicatorWidth = 18.dp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                modifier = Modifier.padding(bottom = 15.dp)
                            )
                        }

                        AnimatedContent(
                            targetState = selectedRankCategory,
                            transitionSpec = {
                                val initialIdx = rankCategories.indexOf(initialState).coerceAtLeast(0)
                                val targetIdx = rankCategories.indexOf(targetState).coerceAtLeast(0)
                                val direction = if (targetIdx >= initialIdx) 1 else -1
                                (slideInHorizontally(animationSpec = tween(260)) { fullWidth -> direction * (fullWidth / 7) } +
                                    fadeIn(animationSpec = tween(240))) togetherWith
                                    (slideOutHorizontally(animationSpec = tween(200)) { fullWidth -> -direction * (fullWidth / 9) } +
                                        fadeOut(animationSpec = tween(160)))
                            },
                            label = "rankCategoryTransition"
                        ) { catKey ->
                            val currentRanksForCat = remember(ranks, catKey) {
                                if (catKey == "全部") ranks else ranks.filter { it.category == catKey }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(currentRanksForCat, key = { it.rankId }) { rank ->
                                    RankCardView(
                                        rank = rank,
                                        onCardClick = { onOpenRank(rank.rankId) },
                                        onPlayRank = {
                                            val cached = viewModel.getCachedRankSongs(rank.rankId)
                                            if (cached.isNotEmpty()) {
                                                onSongClick(cached.first(), cached)
                                            } else {
                                                coroutineScope.launch {
                                                    val firstPage = viewModel.getRankSongs(rank.rankId)
                                                    if (firstPage.isNotEmpty()) {
                                                        onSongClick(firstPage.first(), firstPage)
                                                    } else {
                                                        Toast.makeText(context, "榜单歌曲加载失败", Toast.LENGTH_SHORT).show()
                                                    }
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

            // 6. 精选场景电台（画廊式无外框封面卡片 + 极简文字链入口）
            if (fmChannels.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "精选场景电台",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .softClickable { onOpenRadio("all") }
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "电台广场",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "电台专区",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
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

    if (showPersonalFmSheet) {
        val pfmSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val mintAccent = if (isDark) Color(0xFF26DEBF) else ConceptMintAccent
        val sheetBgColor = if (isDark) Color(0xFF0E1D22) else Color(0xFFF2FCF9)
        val headerBannerBrush = if (isDark) {
            Brush.linearGradient(listOf(Color(0xFF123133), Color(0xFF112534), Color(0xFF0E1D22)))
        } else {
            Brush.linearGradient(listOf(Color(0xFFDDFBF3), Color(0xFFEAF6FF), Color(0xFFF2FCF9)))
        }
        var pfmSheetSpin by remember { mutableIntStateOf(0) }

        ModalBottomSheet(
            onDismissRequest = { showPersonalFmSheet = false },
            sheetState = pfmSheetState,
            containerColor = sheetBgColor,
            windowInsets = WindowInsets(0, 0, 0, 0),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .navigationBarsPadding()
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerBannerBrush)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val coverUrl = fmPool.getOrNull(personalFmIndex)?.coverUrl ?: fmPool.firstOrNull()?.coverUrl.orEmpty()
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(listOf(ConceptMintAccent, ConceptPrimaryDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            if (coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = "猜你喜欢·随心听",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "猜你喜欢",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.5.sp
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = " · 随心听",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = mintAccent,
                                        fontSize = 11.5.sp
                                    ),
                                    modifier = Modifier.padding(bottom = 1.5.dp),
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "融合红心与日常听歌偏好",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = {
                                pfmSheetSpin += 1
                                viewModel.refreshPersonalFm { loaded ->
                                    Toast.makeText(context, "已焕新 ${loaded.size} 首随心听曲目", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !personalFmRefreshing,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = mintAccent.copy(alpha = 0.16f),
                                contentColor = mintAccent
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            ConceptFluidRefreshIcon(
                                tint = mintAccent,
                                isRefreshing = personalFmRefreshing,
                                spinTrigger = pfmSheetSpin,
                                modifier = Modifier.size(17.dp)
                            )
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
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = mintAccent,
                                contentColor = if (isDark) Color(0xFF06201B) else Color.White
                            )
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("全部播放", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (personalFmRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = mintAccent,
                        trackColor = mintAccent.copy(alpha = 0.16f)
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

    if (quickFmStation != null) {
        val station = quickFmStation!!
        val qfmSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { quickFmStation = null },
            sheetState = qfmSheetState,
            containerColor = conceptSheetContainerColor(),
            windowInsets = WindowInsets(0, 0, 0, 0),
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
                        Text("播放全部", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    var qfmRefreshSpin by remember { mutableIntStateOf(0) }
                    FilledTonalButton(
                        onClick = {
                            qfmRefreshSpin += 1
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
                        ConceptFluidRefreshIcon(
                            tint = ConceptPrimary,
                            isRefreshing = quickFmLoading,
                            spinTrigger = qfmRefreshSpin,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("换一批", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
 * 发现页顶部标题栏（去除时段胶囊背景，采用多彩呼吸圆点 + 开放式副标题）
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
            in (5 * 60) until (8 * 60) -> Triple("早晨", "晨光微熹 · 用第一缕旋律唤醒耳朵", ConceptMintAccent)
            in (8 * 60) until (11 * 60 + 30) -> Triple("上午", "清风伴读 · 轻快节拍开启元气上午", ConceptCyanAccent)
            in (11 * 60 + 30) until (14 * 60) -> Triple("午间", "午间小憩 · 卸下忙碌享片刻松弛", ConceptAmberAccent)
            in (14 * 60) until (17 * 60) -> Triple("下午", "午后漫游 · 一杯咖啡与恰到好处的律动", ConceptCoralAccent)
            in (17 * 60) until (19 * 60) -> Triple("傍晚", "暮色微醺 · 晚风与落日余晖皆入歌", ConceptCoralAccent)
            in (19 * 60) until (22 * 60 + 30) -> Triple("晚上", "夜幕微蓝 · 卸下喧嚣沉浸私人旋律", ConceptPrimary)
            else -> Triple("深夜", "深夜静谧 · 耳机里的温柔陪你入眠", ConceptVioletAccent)
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
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(timeGreeting.third)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = timeGreeting.second,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.2.sp,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Surface(
            shape = CircleShape,
            color = conceptFloatingCardColor(),
            shadowElevation = 0.dp,
            modifier = Modifier
                .height(36.dp)
                .softCardClickable(shape = CircleShape, onClick = onSearchClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "搜索",
                    tint = ConceptPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "搜索",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 私人随心听 FM（极简概念版设计 + 极光薄荷绿点缀，优化内部呼吸留白）
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
    var fmCardSpin by remember { mutableIntStateOf(0) }

    val playerCardBrush = if (isDark) {
        Brush.linearGradient(
            listOf(Color(0xFF102A2C), Color(0xFF122234), Color(0xFF141B2B))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFE0FBF4), Color(0xFFECF6FF), Color(0xFFF5F4FF))
        )
    }

    val mintAccent = if (isDark) Color(0xFF26DEBF) else ConceptMintAccent

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .softCardClickable(shape = RoundedCornerShape(26.dp), onClick = onOpenList)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(playerCardBrush)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // 上层：左上角「猜你喜欢·随心听」（随心听为精致小字）+ 右上角圆润双弧流光刷新图标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "猜你喜欢",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.5.sp,
                            letterSpacing = 0.3.sp
                        )
                    )
                    Text(
                        text = "·随心听",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = mintAccent.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            letterSpacing = 0.2.sp
                        ),
                        modifier = Modifier.padding(start = 3.dp, bottom = 1.5.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .softClickable(
                            enabled = !isRefreshing,
                            pressScale = 0.88f,
                            onClick = {
                                fmCardSpin += 1
                                onRefreshNewBatch()
                            }
                        )
                ) {
                    ConceptFluidRefreshIcon(
                        tint = mintAccent,
                        isRefreshing = isRefreshing,
                        spinTrigger = fmCardSpin,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 下层：当前随心听曲目封面、歌手信息、下一首预告与播放控制
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(ConceptMintAccent, ConceptPrimaryDark))),
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
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(13.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeSong?.title ?: if (isRefreshing) "正在为你调频私人曲库..." else "轻触开启私人随心漫游",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeSong?.isHiRes == true) {
                            BadgeTag(text = "Hi-Res", color = VipGold)
                            Spacer(modifier = Modifier.width(4.dp))
                        } else if (activeSong?.isLossless == true) {
                            BadgeTag(text = "SQ", color = ConceptPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (activeSong?.isVip == true) {
                            BadgeTag(text = "VIP", color = VipGold)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = activeSong?.artist ?: "根据你的红心与听歌习惯实时推荐",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (nextSong != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "下一首：${nextSong.title} - ${nextSong.artist}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = mintAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(5.dp, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "暂停随心听" else "播放随心听",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "下一首",
                            tint = mintAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 新歌速递发现页模块（彻底去盒子化 · 开放式杂志排版）：
 * - 消除此前「日期胶囊 + 刷新圆按钮 + 86首胶囊 + 分类分段框 + 全播胶囊 + 三行歌曲大黑框」共 6 处圆角盒子的密集感
 * - 标题栏采用「新歌速递  / 10.12 - 10.18」纯排版层次 + 右侧无框文字链「全部 86 首 >」
 * - 语种栏采用开放式文字 Tab + 底部 3dp 鸢尾紫指示条
 * - 三行横向新歌流直接悬浮于画布，不包裹外层深色卡片框，视野开阔通透
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
    onPlaySong: (Song) -> Unit,
    onSongMoreClick: (Song) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val violetAccent = if (isDark) Color(0xFF9688FF) else ConceptVioletAccent
    val windowRangeStr = remember {
        val end = runCatching { LocalDate.now(DISCOVER_CHINA_ZONE) }.getOrElse { LocalDate.now() }
        val start = end.minusDays(6)
        String.format("%02d.%02d - %02d.%02d", start.monthValue, start.dayOfMonth, end.monthValue, end.dayOfMonth)
    }

    val refreshRotation by animateFloatAsState(
        targetValue = if (loading) 360f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "newSongRefreshRot"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. 开放式标题栏（无任何胶囊框包裹）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "新歌速递",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "/ $windowRangeStr",
                    color = violetAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 1.5.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var nsSectionSpin by remember { mutableIntStateOf(0) }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .softClickable(
                            enabled = !loading,
                            onClick = {
                                nsSectionSpin += 1
                                onRefreshPeriod()
                            }
                        )
                ) {
                    ConceptFluidRefreshIcon(
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        isRefreshing = loading,
                        spinTrigger = nsSectionSpin,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .softClickable(onClick = onOpenFullScreen)
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "查看全部",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 2. 开放式极简语种栏（单色左右滑动横条动画）+ 右侧无框全播文字动作
        val periodTabItems = remember(violetAccent) {
            NEW_SONG_PERIOD_TABS.map { tab ->
                ConceptCategoryTabItem(
                    key = tab.key,
                    label = tab.label,
                    accentColor = violetAccent
                )
            }
        }
        ConceptSlidingCategoryTabs(
            tabs = periodTabItems,
            selectedKey = selectedPeriod,
            onSelectTab = onSelectPeriod,
            itemSpacing = 22.dp,
            fontSize = 13.5.sp,
            indicatorWidth = 18.dp,
            trailingContent = {
                if (songs.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .softClickable(onClick = onPlayAll)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = violetAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "全播",
                            color = violetAccent,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            modifier = Modifier.padding(bottom = 14.dp)
        )

        // 3. 无外框通透横向三行新歌流（加入平滑分类切换过渡动画）
        AnimatedContent(
            targetState = selectedPeriod,
            transitionSpec = {
                val initialIdx = NEW_SONG_PERIOD_TABS.indexOfFirst { it.key == initialState }.coerceAtLeast(0)
                val targetIdx = NEW_SONG_PERIOD_TABS.indexOfFirst { it.key == targetState }.coerceAtLeast(0)
                val direction = if (targetIdx >= initialIdx) 1 else -1
                conceptCategoryContentTransition(direction)
            },
            label = "newSongPeriodTransition"
        ) { _ ->
            if (loading && songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = violetAccent, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                }
            } else if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("正在同步近期发行新歌...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                val columns = remember(songs) { songs.take(18).chunked(3) }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                itemsIndexed(columns, key = { colIdx, group -> "ns_col_${colIdx}_${group.firstOrNull()?.id}" }) { _, group ->
                    Column(
                        modifier = Modifier.width(292.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        group.forEachIndexed { rowIdx, song ->
                            val isThisPlaying = currentSong != null && (
                                currentSong.id == song.id ||
                                    (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))
                                )
                            val shortDate = song.publishDate.takeIf { it.length >= 10 }?.substring(5) ?: song.publishDate

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isThisPlaying) violetAccent.copy(alpha = 0.10f)
                                        else Color.Transparent
                                    )
                                    .softClickable { onPlaySong(song) }
                                    .padding(vertical = 7.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(Brush.linearGradient(listOf(ConceptVioletAccent, ConceptPrimaryDark))),
                                    contentAlignment = Alignment.Center
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
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    if (shortDate.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .clip(RoundedCornerShape(topEnd = 7.dp))
                                                .background(Color.Black.copy(alpha = 0.62f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = shortDate,
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isThisPlaying) violetAccent else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (song.isHiRes) {
                                            BadgeTag(text = "Hi-Res", color = VipGold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        } else if (song.isLossless) {
                                            BadgeTag(text = "SQ", color = ConceptPrimary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        if (song.isVip) {
                                            BadgeTag(text = "VIP", color = VipGold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = song.artist,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Icon(
                                    imageVector = if (isThisPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                                    contentDescription = "播放",
                                    tint = if (isThisPlaying) violetAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (rowIdx < group.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 66.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

private data class DateSongGroup(
    val date: String,
    val indexedSongs: List<Pair<Int, Song>>
)

/**
 * 新歌速递独立全屏页（开放式时间轴排版）：
 * - 语种切换采用开放式底部指示条，去除外框胶囊
 * - 日期分组采用开放式彩色竖线时间轴头（点击平滑折叠/展开），彻底告别层层灰盒堆叠
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
    val violetAccent = if (isDark) Color(0xFF9688FF) else ConceptVioletAccent

    val endDay = remember { runCatching { LocalDate.now(DISCOVER_CHINA_ZONE) }.getOrElse { LocalDate.now() } }
    val startDay = remember(endDay) { endDay.minusDays(6) }
    val periodRangeText = remember(startDay, endDay) {
        String.format("%02d.%02d - %02d.%02d", startDay.monthValue, startDay.dayOfMonth, endDay.monthValue, endDay.dayOfMonth)
    }

    val dateGroups = remember(songs, endDay) {
        var orderCounter = 1
        songs.groupBy { it.publishDate.ifBlank { endDay.toString() } }
            .map { (date, list) ->
                val indexed = list.map { song -> (orderCounter++) to song }
                DateSongGroup(date = date, indexedSongs = indexed)
            }
    }

    val collapsedDates = remember(selectedPeriod) { mutableStateMapOf<String, Boolean>() }

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "新歌速递",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "/ $periodRangeText",
                                color = violetAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        var nsScreenSpin by remember { mutableIntStateOf(0) }
                        IconButton(
                            onClick = {
                                nsScreenSpin += 1
                                viewModel.selectNewSongPeriod(selectedPeriod, forceRefresh = true)
                            },
                            enabled = !loading
                        ) {
                            ConceptFluidRefreshIcon(
                                tint = violetAccent,
                                isRefreshing = loading,
                                spinTrigger = nsScreenSpin,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            val periodKeys = remember { NEW_SONG_PERIOD_TABS.map { it.key } }
            val curPeriodIdx = periodKeys.indexOf(selectedPeriod).coerceAtLeast(0)
            val newSongListState = rememberLazyListState()
            val newSongScope = rememberCoroutineScope()
            val showNewSongScrollTop by remember {
                derivedStateOf {
                    songs.size > 30 && newSongListState.firstVisibleItemIndex > 2
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            LazyColumn(
                state = newSongListState,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalCategorySwipe(
                        currentIndex = curPeriodIdx,
                        itemCount = periodKeys.size,
                        onSelectIndex = { nextIdx ->
                            periodKeys.getOrNull(nextIdx)?.let { key ->
                                viewModel.selectNewSongPeriod(key, forceRefresh = false)
                            }
                        }
                    ),
                contentPadding = PaddingValues(bottom = 112.dp)
            ) {
                // 1. 开放式极简五维语种导航栏
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        val fullPeriodTabs = remember(violetAccent) {
                            NEW_SONG_PERIOD_TABS.map { tab ->
                                ConceptCategoryTabItem(
                                    key = tab.key,
                                    label = tab.label,
                                    accentColor = violetAccent
                                )
                            }
                        }
                        ConceptSlidingCategoryTabs(
                            tabs = fullPeriodTabs,
                            selectedKey = selectedPeriod,
                            onSelectTab = { key -> viewModel.selectNewSongPeriod(key, forceRefresh = false) },
                            itemSpacing = 24.dp,
                            fontSize = 14.5.sp,
                            indicatorWidth = 20.dp
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }

                // 2. 开放式操作栏：全部播放 + 折叠/展开全部日期
                item {
                    val allCollapsed = dateGroups.isNotEmpty() && dateGroups.all { collapsedDates[it.date] == true }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .softClickable(enabled = songs.isNotEmpty()) {
                                    if (songs.isNotEmpty()) onSongClick(songs.first(), songs)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(violetAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "播放全部",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (loading) {
                                CircularProgressIndicator(
                                    color = violetAccent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (dateGroups.size > 1) {
                                Text(
                                    text = if (allCollapsed) "展开全部日期" else "折叠全部日期",
                                    color = violetAccent,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .softClickable {
                                            val targetState = !allCollapsed
                                            dateGroups.forEach { collapsedDates[it.date] = targetState }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // 3. 开放式时间轴日期分组（无卡片盒包裹，点击平滑折叠/展开）
                dateGroups.forEach { group ->
                    val pubDate = group.date
                    val isCollapsed = collapsedDates[pubDate] == true

                    item(key = "header_$pubDate") {
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (isCollapsed) -90f else 0f,
                            animationSpec = tween(durationMillis = 240),
                            label = "dateCollapseArrow_$pubDate"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .softClickable { collapsedDates[pubDate] = !isCollapsed }
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.5.dp)
                                            .height(15.dp)
                                            .clip(CircleShape)
                                            .background(violetAccent)
                                    )
                                    Spacer(modifier = Modifier.width(9.dp))
                                    Text(
                                        text = pubDate,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )

                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isCollapsed) "展开" else "收起",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 11.5.sp
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.ExpandMore,
                                        contentDescription = if (isCollapsed) "展开该日新歌" else "折叠该日新歌",
                                        tint = violetAccent,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(arrowRotation)
                                    )
                                }
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            )
                        }
                    }

                    item(key = "songs_body_$pubDate") {
                        AnimatedVisibility(
                            visible = !isCollapsed,
                            enter = expandVertically(
                                expandFrom = Alignment.Top,
                                animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(animationSpec = tween(durationMillis = 210)),
                            exit = shrinkVertically(
                                shrinkTowards = Alignment.Top,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
                            ) + fadeOut(animationSpec = tween(durationMillis = 170))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                group.indexedSongs.forEach { (displayOrder, song) ->
                                    Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                        SongItemRow(
                                            song = song.copy(
                                                album = if (song.publishDate.isNotBlank()) {
                                                    if (song.album.isNotBlank()) "${song.album} · ${song.publishDate}" else song.publishDate
                                                } else song.album
                                            ),
                                            orderIndex = displayOrder,
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
            }

            FloatingScrollToTopButton(
                visible = showNewSongScrollTop,
                accentColor = violetAccent,
                onClick = {
                    newSongScope.launch {
                        newSongListState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 104.dp)
            )
            }
        }
    }
}

/**
 * 精选场景电台画廊式无外框卡片（去除外层圆角卡片盒，封面直接悬浮于通透画布，文字自然排布于封面下方）
 */
@Composable
private fun FmChannelCardView(
    channel: FmChannel,
    fallbackCoverUrl: String,
    index: Int,
    onCardClick: () -> Unit,
    onPlayFmChannel: () -> Unit
) {
    val effectiveCover = channel.coverUrl.takeIf { it.isNotBlank() && !it.contains("20210519") } ?: fallbackCoverUrl
    val freqText = remember(channel.id, index) {
        val base = 88.6 + ((channel.id.hashCode().and(0x7fffffff) % 180) * 0.1)
        String.format("FM %.1f", base)
    }
    val categoryAccent = remember(channel.category, index) {
        when {
            channel.category.contains("年代") || channel.category.contains("语种") -> ConceptCoralAccent
            channel.category.contains("曲风") || channel.category.contains("流派") -> ConceptVioletAccent
            channel.category.contains("心情") || channel.category.contains("场景") -> ConceptMintAccent
            else -> ConceptCyanAccent
        }
    }
    val fallbackBrush = remember(index) {
        when (index % 4) {
            0 -> Brush.linearGradient(listOf(Color(0xFF163A5F), Color(0xFF0E2138)))
            1 -> Brush.linearGradient(listOf(Color(0xFF1A3E4C), Color(0xFF0E242E)))
            2 -> Brush.linearGradient(listOf(Color(0xFF282B54), Color(0xFF141730)))
            else -> Brush.linearGradient(listOf(Color(0xFF33264D), Color(0xFF181228)))
        }
    }

    Column(
        modifier = Modifier
            .width(148.dp)
            .softCardClickable(clip = false, onClick = onCardClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(22.dp))
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
                        .size(46.dp)
                        .align(Alignment.Center)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.28f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.52f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(categoryAccent)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = freqText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            FilledIconButton(
                onClick = onPlayFmChannel,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = categoryAccent),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(34.dp)
                    .shadow(4.dp, CircleShape)
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
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = channel.description.ifBlank { "随心漫游精选旋律" },
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

private fun rankAccentColor(rank: RankItem): Color {
    return when {
        rank.rankName.contains("飙升") || rank.rankId == 6666 -> ConceptCoralAccent
        rank.rankName.contains("TOP") || rank.rankId == 8888 -> ConceptAmberAccent
        rank.rankName.contains("新歌") || rank.category == "特色曲风" -> ConceptVioletAccent
        rank.category == "全球权威" -> ConceptCyanAccent
        else -> ConceptPrimary
    }
}

/**
 * 巅峰排行榜独立全屏页面（开放式大视野头部 + 底部指示条快选栏，消除卡片堆叠）
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
    val coroutineScope = rememberCoroutineScope()
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

    val accentColor = remember(currentRank) { rankAccentColor(currentRank) }

    LaunchedEffect(selectedRankId) {
        val cached = viewModel.getCachedRankSongs(selectedRankId)
        if (cached.isNotEmpty()) {
            rankSongs = cached
            loading = false
            // 避开页面进入转场动画前 280ms 窗口，保证路由切换满帧丝滑
            kotlinx.coroutines.delay(280L)
        } else {
            loading = true
        }
        val firstBatch = viewModel.getRankSongs(selectedRankId)
        if (firstBatch.isNotEmpty()) {
            rankSongs = firstBatch
            loading = false
        }
        kotlinx.coroutines.delay(350L)
        val fullBatch = viewModel.getRankSongsAll(selectedRankId, maxSongs = 300)
        if (fullBatch.isNotEmpty() && fullBatch.size > rankSongs.size) {
            rankSongs = fullBatch
        }
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

    val quickSwitchRanks = remember(ranks, currentRank) {
        val base = ranks.take(10).toMutableList()
        if (base.none { it.rankId == currentRank.rankId }) {
            base.add(0, currentRank)
        }
        base
    }

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "巅峰榜单",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        var rankRefreshSpin by remember { mutableIntStateOf(0) }
                        IconButton(
                            onClick = {
                                rankRefreshSpin += 1
                                coroutineScope.launch {
                                    loading = true
                                    rankSongs = viewModel.getRankSongsAll(selectedRankId, maxSongs = 500)
                                    loading = false
                                }
                            },
                            enabled = !loading
                        ) {
                            ConceptFluidRefreshIcon(
                                tint = accentColor,
                                isRefreshing = loading,
                                spinTrigger = rankRefreshSpin,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        TextButton(
                            onClick = { showRankGridSheet = true },
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GridView,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "全部榜单",
                                color = accentColor,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            val currentQuickIndex = remember(quickSwitchRanks, currentRank) {
                quickSwitchRanks.indexOfFirst { it.rankId == currentRank.rankId }.coerceAtLeast(0)
            }
            val rankListState = rememberLazyListState()
            val showRankScrollTop by remember {
                derivedStateOf {
                    filteredSongs.size > 30 && rankListState.firstVisibleItemIndex > 4
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            LazyColumn(
                state = rankListState,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalCategorySwipe(
                        currentIndex = currentQuickIndex,
                        itemCount = quickSwitchRanks.size,
                        onSelectIndex = { nextIdx ->
                            quickSwitchRanks.getOrNull(nextIdx)?.let { nextRank ->
                                selectedRankId = nextRank.rankId
                            }
                        }
                    ),
                contentPadding = PaddingValues(bottom = 112.dp)
            ) {
                // 1. 开放式横向榜单快选条（无胶囊外壳，底部彩色滑动指示横条 + 触屏左右滑动联动）
                if (quickSwitchRanks.size > 1) {
                    item {
                        val quickRankTabs = remember(quickSwitchRanks) {
                            quickSwitchRanks.map { item ->
                                ConceptCategoryTabItem(
                                    key = item.rankId.toString(),
                                    label = item.rankName,
                                    accentColor = rankAccentColor(item)
                                )
                            }
                        }
                        val quickScrollState = rememberScrollState()
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(quickScrollState)
                                    .padding(horizontal = 16.dp)
                            ) {
                                ConceptSlidingCategoryTabs(
                                    tabs = quickRankTabs,
                                    selectedKey = currentRank.rankId.toString(),
                                    onSelectTab = { key ->
                                        key.toIntOrNull()?.let { selectedRankId = it }
                                    },
                                    itemSpacing = 20.dp,
                                    fontSize = 13.5.sp,
                                    indicatorWidth = 20.dp,
                                    fillWidth = false
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }
                }

                // 2. 开放式无框榜单头部（去除卡片包裹，采用大视野氛围排版 + 灵动切换动画）
                item {
                    AnimatedContent(
                        targetState = currentRank,
                        transitionSpec = {
                            val initIdx = quickSwitchRanks.indexOfFirst { it.rankId == initialState.rankId }.coerceAtLeast(0)
                            val tgtIdx = quickSwitchRanks.indexOfFirst { it.rankId == targetState.rankId }.coerceAtLeast(0)
                            conceptCategoryContentTransition(if (tgtIdx >= initIdx) 1 else -1)
                        },
                        label = "rankHeaderTransition"
                    ) { activeRank ->
                        val activeAccent = rankAccentColor(activeRank)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .shadow(8.dp, RoundedCornerShape(22.dp))
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Brush.linearGradient(listOf(activeAccent, ConceptPrimaryDark))),
                                contentAlignment = Alignment.Center
                            ) {
                                val cover = activeRank.coverUrl.ifBlank { rankSongs.firstOrNull()?.coverUrl.orEmpty() }
                                if (cover.isNotBlank()) {
                                    AsyncImage(
                                        model = cover,
                                        contentDescription = activeRank.rankName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Leaderboard,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(activeAccent)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${activeRank.category.ifBlank { "官方热榜" }} · ${activeRank.updateFrequency.ifBlank { "每日更新" }}",
                                        color = activeAccent,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = activeRank.rankName,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 22.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .softClickable(enabled = filteredSongs.isNotEmpty()) {
                                            if (filteredSongs.isNotEmpty()) {
                                                onSongClick(filteredSongs.first(), filteredSongs)
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(activeAccent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "播放全部",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 极简榜内搜索栏
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        placeholder = { Text("搜索榜单歌曲 / 歌手...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清除", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = conceptFloatingCardColor(),
                            unfocusedContainerColor = conceptFloatingCardColor()
                        ),
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
                            CircularProgressIndicator(color = accentColor)
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

            FloatingScrollToTopButton(
                visible = showRankScrollTop,
                accentColor = accentColor,
                onClick = {
                    coroutineScope.launch {
                        rankListState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 104.dp)
            )
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
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("全部") }
    val categories = remember(ranks) {
        listOf("全部") + ranks.map { it.category.ifBlank { "热门" } }.distinct()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val smoothDismiss: (() -> Unit) -> Unit = { actionAfterHide ->
        scope.launch {
            runCatching { sheetState.hide() }
        }.invokeOnCompletion {
            actionAfterHide()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = conceptSheetContainerColor(),
        windowInsets = WindowInsets(0, 0, 0, 0),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .conceptSheetSpringEntrance()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全部榜单",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                IconButton(onClick = { smoothDismiss(onDismiss) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val sheetTabItems = remember(categories) {
                categories.map { cat ->
                    val accent = when (cat) {
                        "官方热榜" -> ConceptCoralAccent
                        "特色曲风" -> ConceptVioletAccent
                        "全球权威" -> ConceptCyanAccent
                        else -> ConceptPrimary
                    }
                    ConceptCategoryTabItem(key = cat, label = cat, accentColor = accent)
                }
            }
            ConceptSlidingCategoryTabs(
                tabs = sheetTabItems,
                selectedKey = selectedCategory,
                onSelectTab = { selectedCategory = it },
                itemSpacing = 22.dp,
                fontSize = 13.5.sp,
                indicatorWidth = 18.dp
            )

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 固定 460.dp 高度 + skipPartiallyExpanded = true + 左右滑动切换分类 + 灵动 3D 分类过渡
            val catIndex = categories.indexOf(selectedCategory).coerceAtLeast(0)
            AnimatedContent(
                targetState = selectedCategory,
                transitionSpec = {
                    val initIdx = categories.indexOf(initialState).coerceAtLeast(0)
                    val tgtIdx = categories.indexOf(targetState).coerceAtLeast(0)
                    conceptCategoryContentTransition(if (tgtIdx >= initIdx) 1 else -1)
                },
                label = "rankSheetCategoryTransition"
            ) { activeCat ->
                val catRanks = remember(ranks, activeCat) {
                    if (activeCat == "全部") ranks
                    else ranks.filter { (it.category.ifBlank { "热门" }) == activeCat }
                }
                val rows = remember(catRanks) { catRanks.chunked(2) }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(460.dp)
                        .horizontalCategorySwipe(
                            currentIndex = catIndex,
                            itemCount = categories.size,
                            onSelectIndex = { nextIdx ->
                                categories.getOrNull(nextIdx)?.let { selectedCategory = it }
                            }
                        ),
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
                                val itemAccent = rankAccentColor(item)
                                val selectedScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.02f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.72f,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "rankItemSelectScale_${item.rankId}"
                                )
                                val baseCardBg = conceptElevatedSurfaceColor()
                                val selectedBrush = Brush.linearGradient(
                                    colors = listOf(
                                        itemAccent.copy(alpha = if (isDark) 0.28f else 0.18f),
                                        itemAccent.copy(alpha = if (isDark) 0.12f else 0.06f)
                                    )
                                )
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = baseCardBg,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .graphicsLayer {
                                            scaleX = selectedScale
                                            scaleY = selectedScale
                                        }
                                        .softCardClickable(shape = RoundedCornerShape(18.dp)) {
                                            smoothDismiss { onSelectRank(item.rankId) }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (isSelected) Modifier.background(selectedBrush)
                                                else Modifier
                                            )
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Brush.linearGradient(listOf(itemAccent, ConceptPrimaryDark))),
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
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.rankName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                    color = if (isSelected) itemAccent else MaterialTheme.colorScheme.onSurface
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (isSelected) "当前播放榜单" else item.updateFrequency.ifBlank { item.category },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (isSelected) itemAccent.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = CircleShape,
                                                color = itemAccent.copy(alpha = if (isDark) 0.24f else 0.16f),
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.GraphicEq,
                                                        contentDescription = "当前选中",
                                                        tint = itemAccent,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
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
}

/**
 * 每日推荐独立详情页（开放式无框大视野头部，严格刷新边界）
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
    val dailyRefreshing by viewModel.dailyRefreshing.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val isBusy = dailyRefreshing || loading
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
                        var dailyRefreshSpin by remember { mutableIntStateOf(0) }
                        IconButton(
                            onClick = {
                                dailyRefreshSpin += 1
                                viewModel.refreshDailyRecommend()
                            },
                            enabled = !isBusy
                        ) {
                            ConceptFluidRefreshIcon(
                                tint = ConceptCoralAccent,
                                isRefreshing = isBusy,
                                spinTrigger = dailyRefreshSpin,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            val dailyListState = rememberLazyListState()
            val dailyScope = rememberCoroutineScope()
            val showDailyScrollTop by remember {
                derivedStateOf {
                    dailySongs.size > 30 && dailyListState.firstVisibleItemIndex > 4
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            LazyColumn(
                state = dailyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // 开放式日推头部（去除外层圆角卡片盒）
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
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
                                    color = ConceptCoralAccent,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = " / $monthStr",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 5.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "循着你的听歌轨迹 · 每日 6:00 更新",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .softClickable(enabled = dailySongs.isNotEmpty()) {
                                        if (dailySongs.isNotEmpty()) onSongClick(dailySongs.first(), dailySongs)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(ConceptCoralAccent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "播放全部",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isBusy) {
                                    Spacer(modifier = Modifier.width(10.dp))
                                    CircularProgressIndicator(
                                        color = ConceptCoralAccent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                    )
                }

                if (dailySongs.isEmpty() && !isBusy) {
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
                        Box(modifier = Modifier.padding(horizontal = 12.dp)) {
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

            FloatingScrollToTopButton(
                visible = showDailyScrollTop,
                accentColor = ConceptCoralAccent,
                onClick = {
                    dailyScope.launch {
                        dailyListState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 104.dp)
            )
            }
        }
    }
}

/**
 * 每日推荐主页横幅卡片（极简概念版设计 + 暖珊瑚日落渐变点缀）
 */
@Composable
private fun DailyRecommendBanner(
    songs: List<Song>,
    onCardClick: () -> Unit,
    onPlayAll: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val today = remember { runCatching { LocalDate.now(DISCOVER_CHINA_ZONE) }.getOrElse { LocalDate.now() } }
    val dateText = remember(today) { String.format("%02d/%02d", today.monthValue, today.dayOfMonth) }

    val bgBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF281A28),
                Color(0xFF1B1C30),
                Color(0xFF141C2E)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFEAE4),
                Color(0xFFFFF4F0),
                Color(0xFFF3F8FF)
            )
        )
    }

    val titleColor = if (isDark) Color.White else Color(0xFF141C2B)
    val subColor = if (isDark) Color.White.copy(alpha = 0.72f) else Color(0xFF52637A)

    // 与「我的页」头像名片卡一致的 3D 边缘光晕层次效果（无边框线，通过 clip = false 完整保留 Surface 柔和外阴影）
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp)
            .softCardClickable(
                shape = RoundedCornerShape(28.dp),
                clip = false,
                onClick = onCardClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StaggeredThreeCovers(songs = songs.take(3))

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = ConceptCoralAccent,
                        fontSize = 13.sp,
                        letterSpacing = 1.2.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "每日推荐",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor,
                        fontSize = 20.5.sp,
                        letterSpacing = 0.8.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = "懂你的今日专属旋律",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = subColor,
                        fontSize = 11.5.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            FilledIconButton(
                onClick = onPlayAll,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = ConceptCoralAccent
                ),
                modifier = Modifier
                    .size(48.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = ConceptCoralAccent,
                        spotColor = ConceptCoralAccent
                    )
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
            .width(primarySize + 40.dp)
            .height(primarySize + 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(tertiarySize)
                .offset(x = 42.dp, y = 5.dp)
                .graphicsLayer {
                    rotationZ = 14f
                    alpha = 0.72f
                }
                .shadow(5.dp, RoundedCornerShape(16.dp))
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
                .offset(x = 22.dp, y = 2.dp)
                .graphicsLayer {
                    rotationZ = 7f
                    alpha = 0.88f
                }
                .shadow(6.dp, RoundedCornerShape(18.dp))
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
                .shadow(10.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(ConceptCoralAccent, ConceptPrimaryDark)
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
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

/**
 * 巅峰排行榜卡片（去内嵌盒子化：消除内部嵌套的灰底预览盒，采用单层多彩微渐变 + 极细分割线）
 */
@Composable
private fun RankCardView(
    rank: RankItem,
    onCardClick: () -> Unit,
    onPlayRank: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val accent = remember(rank) { rankAccentColor(rank) }
    val cardBrush = if (isDark) {
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.16f),
                Color(0xFF141C2B),
                Color(0xFF141C2B)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.10f),
                Color(0xFFFFFFFF),
                Color(0xFFFFFFFF)
            )
        )
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = 0.dp,
        modifier = Modifier
            .width(268.dp)
            .softCardClickable(shape = RoundedCornerShape(24.dp), onClick = onCardClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBrush)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(accent, ConceptPrimaryDark)))
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
                                .size(24.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
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
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${rank.category.ifBlank { "热榜" }} · ${rank.updateFrequency.ifBlank { "每日更新" }}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
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
                        tint = accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (rank.topSongsPreview.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rank.topSongsPreview.take(3).forEachIndexed { idx, line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${idx + 1}",
                                color = if (idx == 0) VipGold else accent,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                modifier = Modifier.width(18.dp)
                            )
                            Text(
                                text = line.replace(Regex("^\\d+\\.\\s*"), ""),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                fontSize = 12.sp,
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
