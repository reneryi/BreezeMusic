package com.concept.music.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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

            // 5. 巅峰排行榜
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
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

            // 6. 精选场景电台
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
                            Text(
                                text = "精选场景电台",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
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
                            text = "基于你的我喜欢、最近播放与酷狗 AI 协同推荐实时生成",
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
 * 发现页顶部标题栏
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
 * 私人随心听 FM（极简概念版设计 + 极光薄荷绿多彩点缀）：
 * - 做减法：删除顶部冗长的「私人随心听 · 猜你喜欢」「口味漫游 1/20」「查看列表 (20首) >」状态栏
 * - 点击卡片任意区域直达 20 首列表抽屉，右侧保留刷新、播放/暂停、下一首三枚核心操控键
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
            listOf(Color(0xFF112A2A), Color(0xFF122334), Color(0xFF141C2C))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFE3FAF4), Color(0xFFEDF7FF), Color(0xFFF4F2FF))
        )
    }

    val mintAccent = if (isDark) Color(0xFF26DEBF) else ConceptMintAccent

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
                .background(playerCardBrush)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
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
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = mintAccent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "随心听",
                            color = mintAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeSong?.title ?: "点击开启随心漫游",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

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
                        text = activeSong?.artist ?: "猜你喜欢",
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
                        text = "下一首：${nextSong.title}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onRefreshNewBatch,
                    enabled = !isRefreshing,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = mintAccent.copy(alpha = 0.14f),
                        contentColor = mintAccent
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "换一批",
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotationAngle)
                    )
                }

                FilledIconButton(
                    onClick = onTogglePlayPause,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = mintAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(4.dp, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "暂停随心听" else "播放随心听",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onSkipNext,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = mintAccent.copy(alpha = 0.14f),
                        contentColor = mintAccent
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * 新歌速递发现页卡片（极简减法 + 鸢尾紫点缀色 + 免滑动分段式分类栏）：
 * - 删除标题下冗余小字介绍，日期角标去除「近7日」仅保留周期如 10.11 - 10.17
 * - 重新排版右侧刷新按钮与独立页入口胶囊，单行紧凑呈现，绝不换行
 * - 语种分类采用免滑动一体式分段胶囊栏（华语 / 欧美 / 日韩 / 粤语 / 全部）
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
        // 顶部标题栏：左对齐标题+精简日期，右对齐紧凑刷新键+单行独立页入口
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "新歌速递",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = violetAccent.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = windowRangeStr,
                        color = violetAccent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = violetAccent.copy(alpha = 0.12f),
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !loading, onClick = onRefreshPeriod)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新当前分类新歌",
                            tint = violetAccent,
                            modifier = Modifier
                                .size(15.dp)
                                .rotate(refreshRotation)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = violetAccent.copy(alpha = 0.14f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenFullScreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (songs.isNotEmpty()) "${songs.size} 首" else "全部",
                            color = violetAccent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = violetAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // 免滑动一体式分段分类选择栏 + 右侧全播按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = conceptElevatedSurfaceColor(),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    NEW_SONG_PERIOD_TABS.forEach { tab ->
                        val selected = tab.key == selectedPeriod
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = if (selected) violetAccent else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(13.dp))
                                .clickable { onSelectPeriod(tab.key) }
                        ) {
                            Text(
                                text = tab.label,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onPlayAll,
                enabled = songs.isNotEmpty(),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = violetAccent.copy(alpha = 0.15f),
                    contentColor = violetAccent
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("全播", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }

        // 横向滑动三行新歌分栏矩阵
        if (loading && songs.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = conceptFloatingCardColor(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = violetAccent, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                }
            }
        } else if (songs.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = conceptFloatingCardColor(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("正在同步近期发行新歌...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else {
            val columns = remember(songs) { songs.take(18).chunked(3) }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(columns, key = { colIdx, group -> "ns_col_${colIdx}_${group.firstOrNull()?.id}" }) { _, group ->
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = conceptFloatingCardColor(),
                        shadowElevation = if (isDark) 0.dp else 5.dp,
                        modifier = Modifier.width(304.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            group.forEach { song ->
                                val isThisPlaying = currentSong != null && (
                                    currentSong.id == song.id ||
                                        (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))
                                    )
                                val shortDate = song.publishDate.takeIf { it.length >= 10 }?.substring(5) ?: song.publishDate

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isThisPlaying) violetAccent.copy(alpha = 0.12f)
                                            else Color.Transparent
                                        )
                                        .clickable { onPlaySong(song) }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
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
                                            Surface(
                                                shape = RoundedCornerShape(topEnd = 8.dp),
                                                color = Color.Black.copy(alpha = 0.65f),
                                                modifier = Modifier.align(Alignment.BottomStart)
                                            ) {
                                                Text(
                                                    text = shortDate,
                                                    color = Color.White,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isThisPlaying) violetAccent else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 14.sp
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
                                                    fontSize = 11.5.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Surface(
                                        shape = CircleShape,
                                        color = if (isThisPlaying) violetAccent else violetAccent.copy(alpha = 0.13f),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isThisPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                                                contentDescription = "播放",
                                                tint = if (isThisPlaying) Color.White else violetAccent,
                                                modifier = Modifier.size(16.dp)
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
    }
}

private data class DateSongGroup(
    val date: String,
    val indexedSongs: List<Pair<Int, Song>>
)

/**
 * 新歌速递独立全屏页（极简优化版）：
 * 1. 每个分类下的歌曲序号严格从 1 (01) 开始连续递增排序（预计算 1..N 索引，杜绝 LazyColumn 闭包累加导致的 96 起步 Bug）；
 * 2. 免滑动一体式分段分类栏（华语 / 欧美 / 日韩 / 粤语 / 全部）；
 * 3. 点击任意发行日期栏可平滑折叠/展开当日曲目（AnimatedVisibility 丝滑垂直展开/收起 + 箭头旋转动效）；
 * 4. 删除顶部小字介绍与分组内冗余排序说明。
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

    // 预计算每个分类下从 1 开始的严格连续序号 (1..songs.size)，彻底修复 LazyColumn 延迟组合导致的序号从 96 开始的问题
    val dateGroups = remember(songs, endDay) {
        var orderCounter = 1
        songs.groupBy { it.publishDate.ifBlank { endDay.toString() } }
            .map { (date, list) ->
                val indexed = list.map { song -> (orderCounter++) to song }
                DateSongGroup(date = date, indexedSongs = indexed)
            }
    }

    // 记录每个日期分组的折叠状态（切换分类时自动展开）
    val collapsedDates = remember(selectedPeriod) { mutableStateMapOf<String, Boolean>() }

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "新歌速递",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = violetAccent.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = periodRangeText,
                                    color = violetAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.selectNewSongPeriod(selectedPeriod, forceRefresh = true) },
                            enabled = !loading
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新当前分类", tint = violetAccent)
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
                // 1. 免滑动一体式五维分段选择栏
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = conceptElevatedSurfaceColor(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            NEW_SONG_PERIOD_TABS.forEach { tab ->
                                val selected = tab.key == selectedPeriod
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) violetAccent else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { viewModel.selectNewSongPeriod(tab.key, forceRefresh = false) }
                                ) {
                                    Text(
                                        text = tab.label,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. 播放全部 + 一键折叠/展开全部日期
                item {
                    val allCollapsed = dateGroups.isNotEmpty() && dateGroups.all { collapsedDates[it.date] == true }
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
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = violetAccent, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("全部播放 (${songs.size})", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (loading) {
                                CircularProgressIndicator(
                                    color = violetAccent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (dateGroups.size > 1) {
                                TextButton(
                                    onClick = {
                                        val targetState = !allCollapsed
                                        dateGroups.forEach { collapsedDates[it.date] = targetState }
                                    }
                                ) {
                                    Text(
                                        text = if (allCollapsed) "展开全部日期" else "折叠全部日期",
                                        color = violetAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 按日期分组列表（点击日期栏平滑折叠/展开）
                dateGroups.forEach { group ->
                    val pubDate = group.date
                    val isCollapsed = collapsedDates[pubDate] == true

                    item(key = "header_$pubDate") {
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (isCollapsed) -90f else 0f,
                            animationSpec = tween(durationMillis = 240),
                            label = "dateCollapseArrow_$pubDate"
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = conceptFloatingCardColor(),
                            shadowElevation = if (isDark) 0.dp else 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    collapsedDates[pubDate] = !isCollapsed
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = violetAccent.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = pubDate,
                                            color = violetAccent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "${group.indexedSongs.size} 首",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isCollapsed) "展开" else "收起",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        fontSize = 11.sp
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
                        }
                    }

                    item(key = "songs_body_$pubDate") {
                        AnimatedVisibility(
                            visible = !isCollapsed,
                            enter = expandVertically(animationSpec = tween(durationMillis = 260)) +
                                fadeIn(animationSpec = tween(durationMillis = 220)),
                            exit = shrinkVertically(animationSpec = tween(durationMillis = 240)) +
                                fadeOut(animationSpec = tween(durationMillis = 180))
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
        }
    }
}

/**
 * 精选场景电台大气饱满悬浮卡片（移除封面上冗余的「每次 20 首严选」字样，按分类注入多彩点缀）
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

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 5.dp,
        modifier = Modifier
            .width(174.dp)
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
                        imageVector = Icons.Rounded.Radio,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.32f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.56f)
                                )
                            )
                        )
                )

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = Color.Black.copy(alpha = 0.48f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = categoryAccent,
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

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = categoryAccent.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = channel.category.removeSuffix("电台").ifBlank { "伴听" },
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                FilledIconButton(
                    onClick = onPlayFmChannel,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = categoryAccent),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .shadow(4.dp, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放电台",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(9.dp))

            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = channel.description.ifBlank { "随心漫游精选旋律" },
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
 * 巅峰排行榜独立全屏页面（全新重构设计）：
 * - 彻底解决此前「左上角标题下拉 + 右上角全部榜单 + 卡片内换榜单」三次重复弹窗的问题：
 *   1. 左上角仅作纯粹页面标题展示；
 *   2. 右上角保留刷新键与唯一的「榜单宫格」入口；
 *   3. 顶部内嵌一条高频榜单横向快选胶囊栏，无需弹窗即可一键切换常用热榜；
 *   4. 头部卡片按榜单属性呈现多彩微渐变点缀，去除冗余小字与重复的「换榜单」按钮。
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
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    loading = true
                                    rankSongs = viewModel.getRankSongsAll(selectedRankId, maxSongs = 500)
                                    loading = false
                                }
                            },
                            enabled = !loading
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新当前榜单", tint = accentColor)
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = accentColor.copy(alpha = 0.14f),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showRankGridSheet = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GridView,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "榜单宫格 (${ranks.size})",
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                // 1. 顶部内嵌同级榜单快选条（无需弹窗一键切榜）
                if (quickSwitchRanks.size > 1) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(quickSwitchRanks, key = { it.rankId }) { item ->
                                val selected = item.rankId == currentRank.rankId
                                val itemAccent = rankAccentColor(item)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) itemAccent else conceptFloatingCardColor(),
                                    shadowElevation = if (!isDark && !selected) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { selectedRankId = item.rankId }
                                ) {
                                    Text(
                                        text = item.rankName,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. 重构后的精简多彩榜单头部卡（无重复「换榜单」按钮，无冗余加载提示词）
                item {
                    val headerBrush = if (isDark) {
                        Brush.linearGradient(
                            listOf(
                                accentColor.copy(alpha = 0.25f),
                                Color(0xFF151F32),
                                Color(0xFF121826)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                accentColor.copy(alpha = 0.16f),
                                Color(0xFFEEF5FF),
                                Color(0xFFFFFFFF)
                            )
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(26.dp),
                        color = conceptFloatingCardColor(),
                        shadowElevation = if (isDark) 0.dp else 5.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(headerBrush)
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(22.dp))
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
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = accentColor.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = currentRank.category.ifBlank { "官方热榜" },
                                            color = accentColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 20.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (filteredSongs.isNotEmpty()) {
                                            onSongClick(filteredSongs.first(), filteredSongs)
                                        }
                                    },
                                    enabled = filteredSongs.isNotEmpty(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 7.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("播放全部 (${filteredSongs.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 3. 榜内搜索栏
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
                        shape = RoundedCornerShape(18.dp),
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
                Text(
                    text = "全部榜单 (${ranks.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                            val itemAccent = rankAccentColor(item)
                            val cardBg = when {
                                isSelected && isDark -> Color(0xFF1A2D46)
                                isSelected && !isDark -> Color(0xFFE6F3FF)
                                else -> conceptElevatedSurfaceColor()
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = cardBg,
                                border = if (isSelected) BorderStroke(1.5.dp, itemAccent) else null,
                                shadowElevation = 0.dp,
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
                                                color = if (isSelected) itemAccent else MaterialTheme.colorScheme.onSurface
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
 * 每日推荐独立详情页（严格刷新边界：右上角刷新仅刷新每日推荐本身）
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
                        IconButton(
                            onClick = { viewModel.refreshDailyRecommend() },
                            enabled = !isBusy
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新每日推荐", tint = ConceptCoralAccent)
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
                                        color = ConceptCoralAccent,
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
                                    text = "个性化定制 · 每日 6:00 更新",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "今日精选 ${dailySongs.size} 首心动曲目",
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
                                containerColor = ConceptCoralAccent,
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

                        if (isBusy) {
                            CircularProgressIndicator(
                                color = ConceptCoralAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
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
 * 每日推荐主页横幅卡片（极简概念版设计 + 暖珊瑚日落渐变点缀）：
 * - 做减法：删除顶部「10/17 · 个性化定制」胶囊与底部「查看全部 30 首推荐 >」字样（点击卡片即进独立详情页展示）
 * - 将生硬的歌名罗列替换为富有呼吸感的概念版音乐导语
 */
@Composable
private fun DailyRecommendBanner(
    songs: List<Song>,
    onCardClick: () -> Unit,
    onPlayAll: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current

    val bgBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF2B1926),
                Color(0xFF1E1A2D),
                Color(0xFF141B2D)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFEAE6),
                Color(0xFFFFF3EB),
                Color(0xFFEEF5FF)
            )
        )
    }

    val titleColor = if (isDark) Color.White else Color(0xFF141C2B)
    val subColor = if (isDark) Color.White.copy(alpha = 0.72f) else Color(0xFF52637A)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(142.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onCardClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StaggeredThreeCovers(
                    songs = songs.take(3),
                    primarySize = 88.dp,
                    secondarySize = 76.dp,
                    tertiarySize = 66.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "每日推荐",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = titleColor,
                            fontSize = 22.sp,
                            letterSpacing = 0.5.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "循着你的听歌轨迹 · 邂逅今日心动旋律",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = subColor,
                            fontSize = 12.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                FilledIconButton(
                    onClick = onPlayAll,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ConceptCoralAccent
                    ),
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(6.dp, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放每日推荐",
                        tint = Color.White,
                        modifier = Modifier.size(25.dp)
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
 * 巅峰排行榜圆润饱满悬浮卡片（按榜单分类赋予珊瑚红/琥珀金/鸢尾紫/概念蓝差异化点缀）
 */
@Composable
private fun RankCardView(
    rank: RankItem,
    onCardClick: () -> Unit,
    onPlayRank: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val accent = remember(rank) { rankAccentColor(rank) }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 5.dp,
        modifier = Modifier
            .width(278.dp)
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
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
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
                                .size(26.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accent.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = rank.category.ifBlank { "热榜" },
                                color = accent,
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
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放榜单",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
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
                                    color = if (idx == 0) VipGold else accent,
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
