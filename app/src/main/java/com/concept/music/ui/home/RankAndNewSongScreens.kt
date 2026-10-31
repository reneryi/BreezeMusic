package com.concept.music.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.FilterQuality
import com.concept.music.ui.components.BadgeTag
import com.concept.music.ui.components.rememberOptimizedAsyncImageModel
import com.concept.music.ui.components.FloatingScrollToTopButton
import com.concept.music.ui.components.rememberProgressiveSongList
import com.concept.music.ui.components.smoothFastScrollToItem
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptAnimatedOverlaySheet
import com.concept.music.ui.theme.ConceptMotion
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
import com.concept.music.ui.theme.horizontalCategorySwipe
import com.concept.music.ui.theme.ConceptSlidingCategoryTabs
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private data class IndexedNewSong(
    val orderIndex: Int,
    val rawSong: Song,
    val displaySong: Song
)

private data class DateSongGroup(
    val date: String,
    val indexedSongs: List<IndexedNewSong>
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
            val progressiveSongs by rememberProgressiveSongList(
                sourceSongs = songs,
                listState = newSongListState,
                resetKey = selectedPeriod,
                initialBatchSize = 15,
                microBatchSize = 10
            )
            val dateGroups = remember(progressiveSongs, endDay) {
                var orderCounter = 1
                progressiveSongs.groupBy { it.publishDate.ifBlank { endDay.toString() } }
                    .map { (date, list) ->
                        val indexed = list.map { song ->
                            val displaySong = if (song.publishDate.isNotBlank()) {
                                song.copy(
                                    album = if (song.album.isNotBlank()) "${song.album} · ${song.publishDate}" else song.publishDate
                                )
                            } else song
                            IndexedNewSong(
                                orderIndex = orderCounter++,
                                rawSong = song,
                                displaySong = displaySong
                            )
                        }
                        DateSongGroup(date = date, indexedSongs = indexed)
                    }
            }
            val latestFullSongs by rememberUpdatedState(songs)
            val curPlayingId = currentSong?.id
            val curPlayingHash = currentSong?.hash.orEmpty()
            val showNewSongScrollTop by remember(songs.size) {
                derivedStateOf {
                    newSongListState.firstVisibleItemIndex > 1 ||
                        (newSongListState.firstVisibleItemIndex == 1 && newSongListState.firstVisibleItemScrollOffset > 140)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            val nsAdaptive = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current
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
                item(key = "ns_top_tabs", contentType = "ns_header") {
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
                item(key = "ns_action_bar", contentType = "ns_action") {
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
                                    if (latestFullSongs.isNotEmpty()) onSongClick(latestFullSongs.first(), latestFullSongs)
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

                // 3. 开放式时间轴日期分组（搭载 spatialPageSpring 连续高度折叠/展开物理弹簧动画 + expressiveSpring 箭头旋转）
                dateGroups.forEach { group ->
                    val pubDate = group.date
                    val isCollapsed = collapsedDates[pubDate] == true

                    item(key = "header_$pubDate", contentType = "date_header") {
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (isCollapsed) -90f else 0f,
                            animationSpec = ConceptMotion.expressiveSpring(),
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
                                    AnimatedContent(
                                        targetState = isCollapsed,
                                        transitionSpec = {
                                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                                        },
                                        label = "collapseLabel_$pubDate"
                                    ) { collapsedState ->
                                        Text(
                                            text = if (collapsedState) "展开 (${group.indexedSongs.size})" else "收起",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 11.5.sp
                                        )
                                    }
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

                    item(key = "ns_group_songs_$pubDate", contentType = "date_song_group") {
                        AnimatedVisibility(
                            visible = !isCollapsed,
                            enter = expandVertically(
                                animationSpec = ConceptMotion.spatialPageSpring(),
                                expandFrom = Alignment.Top
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = 220, easing = ConceptMotion.EmphasizedDecelerate)
                            ),
                            exit = shrinkVertically(
                                animationSpec = ConceptMotion.spatialPageSpring(),
                                shrinkTowards = Alignment.Top
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = 180, easing = ConceptMotion.EmphasizedAccelerate)
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (nsAdaptive.useTwoColumnSongOrCardGrid) {
                                    val pairs = remember(group.indexedSongs) { group.indexedSongs.chunked(2) }
                                    pairs.forEach { pair ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            pair.forEach { item ->
                                                val rawSong = item.rawSong
                                                Box(modifier = Modifier.weight(1f)) {
                                                    SongItemRow(
                                                        song = item.displaySong,
                                                        orderIndex = item.orderIndex,
                                                        isPlaying = curPlayingId != null && (curPlayingId == rawSong.id || (curPlayingHash.isNotEmpty() && curPlayingHash.equals(rawSong.hash, ignoreCase = true))),
                                                        onClick = { onSongClick(rawSong, latestFullSongs) },
                                                        onMoreClick = { onSongMoreClick(rawSong) }
                                                    )
                                                }
                                            }
                                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                } else {
                                    group.indexedSongs.forEach { item ->
                                        val rawSong = item.rawSong
                                        Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                            SongItemRow(
                                                song = item.displaySong,
                                                orderIndex = item.orderIndex,
                                                isPlaying = curPlayingId != null && (curPlayingId == rawSong.id || (curPlayingHash.isNotEmpty() && curPlayingHash.equals(rawSong.hash, ignoreCase = true))),
                                                onClick = { onSongClick(rawSong, latestFullSongs) },
                                                onMoreClick = { onSongMoreClick(rawSong) }
                                            )
                                        }
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
                        newSongListState.smoothFastScrollToItem(0)
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


internal fun rankAccentColor(rank: RankItem): Color {
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
    onSongMoreClick: (Song) -> Unit = {},
    onSubSheetOpenChanged: (Boolean) -> Unit = {}
) {
    val ranks by viewModel.ranks.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var selectedRankId by remember(initialRankId) { mutableIntStateOf(initialRankId) }
    var rankSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showRankGridSheet by remember { mutableStateOf(false) }
    var isRankSubSheetDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(showRankGridSheet) {
        if (showRankGridSheet) isRankSubSheetDismissing = false
    }
    val rankSheetActive = showRankGridSheet && !isRankSubSheetDismissing
    LaunchedEffect(rankSheetActive) {
        onSubSheetOpenChanged(rankSheetActive)
    }
    DisposableEffect(Unit) {
        onDispose { onSubSheetOpenChanged(false) }
    }

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
        val fullBatch = viewModel.getRankSongsAll(
            rankId = selectedRankId,
            maxSongs = 500,
            onProgress = { partial ->
                if (partial.isNotEmpty()) {
                    rankSongs = partial
                    loading = false
                }
            }
        )
        if (fullBatch.isNotEmpty() && fullBatch.size >= rankSongs.size) {
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
            val displayedRankSongs by rememberProgressiveSongList(
                sourceSongs = filteredSongs,
                listState = rankListState,
                resetKey = "${selectedRankId}_$searchQuery",
                initialBatchSize = 15,
                microBatchSize = 10
            )
            val latestFilteredRankSongs by rememberUpdatedState(filteredSongs)
            val curRankPlayingId = currentSong?.id
            val curRankPlayingHash = currentSong?.hash.orEmpty()
            val showRankScrollTop by remember(filteredSongs.size) {
                derivedStateOf {
                    rankListState.firstVisibleItemIndex > 2 ||
                        (rankListState.firstVisibleItemIndex == 2 && rankListState.firstVisibleItemScrollOffset > 100)
                }
            }
            val playingRankSongIndex = remember(filteredSongs, currentSong?.id, currentSong?.hash) {
                if (currentSong == null) -1
                else filteredSongs.indexOfFirst {
                    it.id == currentSong.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(it.hash, ignoreCase = true))
                }
            }
            val rankAdaptive = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
            if (rankAdaptive.useTwoPaneDetailLayout) {
                // 平板横屏双栏布局：左栏固定榜单封面信息与快捷切榜看板 + 右栏独立滚动曲目双列网格
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = conceptFloatingCardColor(),
                        modifier = Modifier
                            .width(310.dp)
                            .fillMaxHeight()
                            .padding(bottom = 96.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(144.dp)
                                    .shadow(10.dp, RoundedCornerShape(26.dp))
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Brush.linearGradient(listOf(accentColor, ConceptPrimaryDark))),
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
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "${currentRank.category.ifBlank { "官方热榜" }} · ${currentRank.updateFrequency.ifBlank { "每日更新" }}",
                                color = accentColor,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentRank.rankName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 20.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    if (filteredSongs.isNotEmpty()) {
                                        onSongClick(filteredSongs.first(), filteredSongs)
                                    }
                                },
                                enabled = filteredSongs.isNotEmpty(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("播放全部 (${filteredSongs.size})", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                            }
                            if (quickSwitchRanks.size > 1) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "快捷切换榜单",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                quickSwitchRanks.forEach { item ->
                                    val selected = item.rankId == currentRank.rankId
                                    val itemAccent = rankAccentColor(item)
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) itemAccent.copy(alpha = 0.16f) else Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .softCardClickable { selectedRankId = item.rankId }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (selected) itemAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = item.rankName,
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = if (selected) itemAccent else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LazyColumn(
                        state = rankListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 112.dp)
                    ) {
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
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        if (loading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = accentColor)
                                }
                            }
                        } else if (filteredSongs.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                    Text("未找到匹配的榜单歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            val rankPairs = displayedRankSongs.mapIndexed { idx, s -> idx to s }.chunked(2)
                            itemsIndexed(
                                items = rankPairs,
                                key = { pIdx, pair -> "rank_tp_pair_${pair.first().second.id}_$pIdx" },
                                contentType = { _, _ -> "song_pair_row" }
                            ) { _, pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    pair.forEach { (index, song) ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            SongItemRow(
                                                song = song,
                                                orderIndex = index + 1,
                                                isPlaying = curRankPlayingId != null && (curRankPlayingId == song.id || (curRankPlayingHash.isNotEmpty() && curRankPlayingHash.equals(song.hash, ignoreCase = true))),
                                                onClick = { onSongClick(song, latestFilteredRankSongs) },
                                                onMoreClick = { onSongMoreClick(song) }
                                            )
                                        }
                                    }
                                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
            LazyColumn(
                state = rankListState,
                modifier = Modifier
                    .widthIn(max = 960.dp)
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
                } else if (rankAdaptive.useTwoColumnSongOrCardGrid) {
                    val rankPairs = displayedRankSongs.mapIndexed { idx, s -> idx to s }.chunked(2)
                    itemsIndexed(
                        items = rankPairs,
                        key = { pIdx, pair -> "rank_pair_${pair.first().second.id}_$pIdx" },
                        contentType = { _, _ -> "song_pair_row" }
                    ) { _, pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { (index, song) ->
                                Box(modifier = Modifier.weight(1f)) {
                                    SongItemRow(
                                        song = song,
                                        orderIndex = index + 1,
                                        isPlaying = curRankPlayingId != null && (curRankPlayingId == song.id || (curRankPlayingHash.isNotEmpty() && curRankPlayingHash.equals(song.hash, ignoreCase = true))),
                                        onClick = { onSongClick(song, latestFilteredRankSongs) },
                                        onMoreClick = { onSongMoreClick(song) }
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    itemsIndexed(
                        items = displayedRankSongs,
                        key = { idx, s -> "rank_${s.id}_$idx" },
                        contentType = { _, _ -> "song_row" }
                    ) { index, song ->
                        SongItemRow(
                            song = song,
                            orderIndex = index + 1,
                            isPlaying = curRankPlayingId != null && (curRankPlayingId == song.id || (curRankPlayingHash.isNotEmpty() && curRankPlayingHash.equals(song.hash, ignoreCase = true))),
                            onClick = { onSongClick(song, latestFilteredRankSongs) },
                            onMoreClick = { onSongMoreClick(song) }
                        )
                    }
                }
            }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 104.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingScrollToTopButton(
                    visible = showRankScrollTop,
                    accentColor = accentColor,
                    onClick = {
                        coroutineScope.launch {
                            rankListState.smoothFastScrollToItem(0)
                        }
                    }
                )

                AnimatedVisibility(
                    visible = playingRankSongIndex >= 0,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut() + scaleOut(targetScale = 0.85f)
                ) {
                    val headerCount = if (quickSwitchRanks.size > 1) 3 else 2
                    FilledIconButton(
                        onClick = {
                            if (playingRankSongIndex >= 0) {
                                coroutineScope.launch {
                                    val targetLazyIndex = (headerCount + playingRankSongIndex - 2).coerceAtLeast(0)
                                    rankListState.smoothFastScrollToItem(targetLazyIndex)
                                }
                            }
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(8.dp, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = "定位当前播放歌曲",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
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
                isRankSubSheetDismissing = true
                selectedRankId = chosenId
                showRankGridSheet = false
            },
            onDismissStart = { isRankSubSheetDismissing = true },
            onDismiss = { showRankGridSheet = false }
        )
    }
}

/**
 * 全量榜单分类宫格快选抽屉
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RankGridPickerSheet(
    ranks: List<RankItem>,
    selectedRankId: Int?,
    onSelectRank: (Int) -> Unit,
    onDismissStart: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    var selectedCategory by remember { mutableStateOf("全部") }
    val categories = remember(ranks) {
        listOf("全部") + ranks.map { it.category.ifBlank { "热门" } }.distinct()
    }
    val rankAdaptive = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current
    val useCenteredRankPanel = rankAdaptive.isLargeScreenDevice && rankAdaptive.isLandscapeWindow
    val rankSheetShape = if (useCenteredRankPanel) {
        RoundedCornerShape(26.dp)
    } else {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    }

    ConceptAnimatedOverlaySheet(
        onDismiss = onDismiss,
        onDismissStart = onDismissStart,
        isLandscape = useCenteredRankPanel,
        zIndex = 80f
    ) {
        val dismissSheet = requestDismiss
        Surface(
            shape = rankSheetShape,
            color = conceptSheetContainerColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 24.dp,
            modifier = (if (useCenteredRankPanel) {
                Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
            } else {
                Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
            }).clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .sheetVerticalDragGesture()
                    .padding(top = 6.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                ) {}
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .sheetVerticalDragGesture(),
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
                IconButton(onClick = dismissSheet) {
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
                                            onDismissStart()
                                            onSelectRank(item.rankId)
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
}

