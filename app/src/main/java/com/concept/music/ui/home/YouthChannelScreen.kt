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
import com.concept.music.data.model.MusicStoryItem
import com.concept.music.data.model.Song
import com.concept.music.data.model.YouthChannel
import com.concept.music.data.model.YouthChannelAmway
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * 酷狗概念版专属频道广场独立页面
 * 对齐 MakcRe/KuGouMusicApi 7 大官方概念版频道能力：
 * 1. 获取用户所有频道 (/youth/v2/channel/channel_all_list)
 * 2. 频道详情 (/youth/api/channel/v1/channel_list_by_id)
 * 3. 频道安利 (/youth/api/amway/v2/index)
 * 4. 相似频道 (/youth/v1/channel/get_friendly_channel)
 * 5. 频道订阅/取消订阅 (/youth/v1/channel_subscribe & /youth/v1/channel_un_subscribe)
 * 6. 频道音乐故事列表 (/youth/api/channel/v1/channel_get_song_audit_passed)
 * 7. 频道音乐故事详情 (/youth/v2/post/get_song_detail)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouthChannelPlazaScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onOpenChannelDetail: (String) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalIsDarkTheme.current
    val channels by viewModel.youthChannels.collectAsState()
    val amways by viewModel.youthChannelAmways.collectAsState()
    val dailySongs by viewModel.dailyRecommendSongs.collectAsState()

    val subscribedChannels = remember(channels) { channels.filter { it.isSubscribed } }
    var selectedCategory by remember { mutableStateOf("全部") }
    val categories = remember(channels) {
        listOf("全部", "已订阅") + channels.map { it.tag.ifBlank { "精选频道" } }.distinct()
    }

    val filteredChannels = remember(channels, selectedCategory) {
        when (selectedCategory) {
            "全部" -> channels
            "已订阅" -> channels.filter { it.isSubscribed }
            else -> channels.filter { (it.tag.ifBlank { "精选频道" }) == selectedCategory }
        }
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
                                text = "概念专属频道广场",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "频道安利 · 音乐故事 · 订阅漫游",
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
                        IconButton(
                            onClick = {
                                viewModel.refreshYouthChannels()
                                viewModel.refreshYouthChannelAmways()
                                Toast.makeText(context, "已刷新概念频道与安利流", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. 频道安利墙 (Channel Amway)
                if (amways.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = VipGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "今日频道安利",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.refreshYouthChannelAmways() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = ConceptPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("换一波安利", fontSize = 12.sp, color = ConceptPrimary, fontWeight = FontWeight.Bold)
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(amways, key = { it.id }) { amway ->
                                    AmwaySpotlightCard(
                                        amway = amway,
                                        onClick = {
                                            if (amway.channelId.isNotBlank()) {
                                                onOpenChannelDetail(amway.channelId)
                                            } else if (amway.song != null) {
                                                onPlaySong(amway.song, listOf(amway.song))
                                            }
                                        },
                                        onPlaySong = {
                                            amway.song?.let { s -> onPlaySong(s, listOf(s)) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. 我订阅的频道横栏
                if (subscribedChannels.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "我订阅的频道 (${subscribedChannels.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(subscribedChannels, key = { _, c -> "sub_${c.id}" }) { idx, ch ->
                                    val fb = fallbackCovers.getOrNull(idx % fallbackCovers.size.coerceAtLeast(1)).orEmpty()
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = conceptFloatingCardColor(),
                                        shadowElevation = if (isDark) 0.dp else 4.dp,
                                        modifier = Modifier
                                            .width(196.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { onOpenChannelDetail(ch.id) }
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
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(ConceptPrimary.copy(alpha = 0.15f))
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = ch.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = ch.tag.ifBlank { "已订阅" },
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = ConceptPrimary
                                                    ),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. 频道分类筛选胶囊
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { cat ->
                            val selected = cat == selectedCategory
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) ConceptPrimary else conceptFloatingCardColor(),
                                shadowElevation = if (!isDark && !selected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { selectedCategory = cat }
                            ) {
                                Text(
                                    text = cat,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // 4. 全部频道双列饱满卡片网格
                val rows = filteredChannels.chunked(2)
                itemsIndexed(rows) { rowIdx, pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        pair.forEachIndexed { colIdx, channel ->
                            val globalIdx = rowIdx * 2 + colIdx
                            val fb = fallbackCovers.getOrNull(globalIdx % fallbackCovers.size.coerceAtLeast(1)).orEmpty()
                            YouthPlazaGridCard(
                                channel = channel,
                                fallbackCover = fb,
                                onClick = { onOpenChannelDetail(channel.id) },
                                onToggleSubscribe = {
                                    viewModel.toggleSubscribeYouthChannel(channel) { _, _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onPlayClick = {
                                    scope.launch {
                                        Toast.makeText(context, "正在装载频道【${channel.name}】...", Toast.LENGTH_SHORT).show()
                                        val songs = viewModel.getYouthChannelSongs(channel.id, forceNextPage = true)
                                        if (songs.isNotEmpty()) {
                                            onPlaySong(songs.first(), songs)
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

@Composable
private fun AmwaySpotlightCard(
    amway: YouthChannelAmway,
    onClick: () -> Unit,
    onPlaySong: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 5.dp,
        modifier = Modifier
            .width(286.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ConceptPrimary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = amway.channelName.ifBlank { "概念安利" },
                        color = ConceptPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "@${amway.authorName}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "“${amway.content}”",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (amway.song != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = conceptElevatedSurfaceColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onPlaySong)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = amway.song.coverUrl.ifBlank { amway.coverUrl },
                            contentDescription = amway.song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ConceptPrimary.copy(alpha = 0.2f))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = amway.song.title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = amway.song.artist,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.PlayCircleFilled,
                            contentDescription = "播放安利歌曲",
                            tint = ConceptPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YouthPlazaGridCard(
    channel: YouthChannel,
    fallbackCover: String,
    onClick: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val cover = channel.coverUrl.takeIf { it.isNotBlank() && !it.contains("20210519") } ?: fallbackCover

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 5.dp,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF142848)))
                    )
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
                                listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.48f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = channel.tag.ifBlank { "概念频道" },
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (channel.isSubscribed) ConceptPrimary else Color.Black.copy(alpha = 0.48f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleSubscribe)
                ) {
                    Text(
                        text = if (channel.isSubscribed) "已订" else "+ 订阅",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                FilledIconButton(
                    onClick = onPlayClick,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
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
                text = channel.description.ifBlank { "点击浏览音乐故事与相似频道" },
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
 * 酷狗概念版专属频道详情独立页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouthChannelDetailScreen(
    viewModel: HomeViewModel,
    channelId: String,
    onBack: () -> Unit,
    onSwitchChannel: (String) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    currentSong: Song? = null,
    onSongMoreClick: (Song) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalIsDarkTheme.current

    var currentChannelId by remember(channelId) { mutableStateOf(channelId) }
    var channelDetail by remember { mutableStateOf<YouthChannel?>(null) }
    var stories by remember { mutableStateOf<List<MusicStoryItem>>(emptyList()) }
    var channelSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var similarChannels by remember { mutableStateOf<List<YouthChannel>>(emptyList()) }
    var channelAmways by remember { mutableStateOf<List<YouthChannelAmway>>(emptyList()) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 音乐故事, 1: 频道曲库
    var storyPage by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var refreshingBatch by remember { mutableStateOf(false) }

    var selectedStory by remember { mutableStateOf<MusicStoryItem?>(null) }
    var storyDetailLoading by remember { mutableStateOf(false) }

    fun loadChannelAll(targetId: String, forceNewPage: Boolean = false) {
        scope.launch {
            if (!forceNewPage) loading = true else refreshingBatch = true
            val nextStoryPage = if (forceNewPage) (storyPage % 5) + 1 else 1
            storyPage = nextStoryPage

            val detailDef = async { viewModel.getYouthChannelDetail(targetId) }
            val storiesDef = async { viewModel.getYouthChannelStories(targetId, page = nextStoryPage, pageSize = 14) }
            val songsDef = async { viewModel.getYouthChannelSongs(targetId, forceNextPage = forceNewPage) }
            val similarDef = async { viewModel.getYouthChannelSimilar(targetId) }
            val amwayDef = async { viewModel.getYouthChannelAmwayForChannel(targetId) }

            channelDetail = detailDef.await()
            val loadedStories = storiesDef.await()
            val loadedSongs = songsDef.await()
            similarChannels = similarDef.await()
            channelAmways = amwayDef.await()

            if (loadedStories.isNotEmpty() || !forceNewPage) {
                stories = loadedStories
            }
            if (loadedSongs.isNotEmpty()) {
                channelSongs = loadedSongs
            } else if (loadedStories.isNotEmpty()) {
                channelSongs = loadedStories.mapNotNull { it.song }.distinctBy { it.hash.ifBlank { it.id } }
            }

            loading = false
            refreshingBatch = false
        }
    }

    LaunchedEffect(currentChannelId) {
        loadChannelAll(currentChannelId, forceNewPage = false)
    }

    val displayChannel = channelDetail ?: YouthChannel(
        id = currentChannelId,
        name = "概念专属频道",
        description = "正在探索概念版音乐故事与曲库",
        tag = "概念专属"
    )

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = displayChannel.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                loadChannelAll(currentChannelId, forceNewPage = true)
                                Toast.makeText(context, "正在换一批频道音乐故事与曲目...", Toast.LENGTH_SHORT).show()
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
                                        .size(88.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                            Brush.linearGradient(listOf(ConceptPrimaryDark, Color(0xFF162B4D)))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val cover = displayChannel.coverUrl.ifBlank { channelSongs.firstOrNull()?.coverUrl.orEmpty() }
                                    if (cover.isNotBlank()) {
                                        AsyncImage(
                                            model = cover,
                                            contentDescription = displayChannel.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Album,
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
                                                text = displayChannel.tag.ifBlank { "概念专属" },
                                                color = ConceptPrimary,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        if (displayChannel.subscribeCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${displayChannel.subscribeCount} 人订阅",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = displayChannel.name,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = displayChannel.description.ifBlank { "酷狗概念版官方音乐故事与场景频道" },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (channelSongs.isNotEmpty()) {
                                            onSongClick(channelSongs.first(), channelSongs)
                                        }
                                    },
                                    enabled = channelSongs.isNotEmpty(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ConceptPrimary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(19.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("畅听本频道 (${channelSongs.size}首)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        viewModel.toggleSubscribeYouthChannel(displayChannel) { ok, subscribed, msg ->
                                            if (ok) {
                                                channelDetail = displayChannel.copy(
                                                    isSubscribed = subscribed,
                                                    subscribeCount = (displayChannel.subscribeCount + if (subscribed) 1 else -1).coerceAtLeast(0)
                                                )
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (displayChannel.isSubscribed) conceptElevatedSurfaceColor() else ConceptPrimary.copy(alpha = 0.16f),
                                        contentColor = if (displayChannel.isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else ConceptPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (displayChannel.isSubscribed) Icons.Rounded.Check else Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (displayChannel.isSubscribed) "已订阅" else "订阅频道",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                val topAmway = channelAmways.firstOrNull()
                if (topAmway != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = conceptFloatingCardColor(),
                            shadowElevation = if (isDark) 0.dp else 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FormatQuote,
                                    contentDescription = null,
                                    tint = VipGold,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = topAmway.content,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "来自频道安利官 @${topAmway.authorName}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (similarChannels.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "相似概念频道推荐",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(similarChannels, key = { "sim_${it.id}" }) { sim ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = conceptFloatingCardColor(),
                                        shadowElevation = if (isDark) 0.dp else 3.dp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable {
                                                currentChannelId = sim.id
                                                onSwitchChannel(sim.id)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Explore,
                                                contentDescription = null,
                                                tint = ConceptPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = sim.name,
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(conceptFloatingCardColor())
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val tabs = listOf(
                            "音乐故事 (${stories.size})",
                            "频道曲目 (${channelSongs.size})"
                        )
                        tabs.forEachIndexed { idx, label ->
                            val selected = selectedTab == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) ConceptPrimary else Color.Transparent)
                                    .clickable { selectedTab = idx }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                if (loading || refreshingBatch) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ConceptPrimary)
                        }
                    }
                }

                if (selectedTab == 0) {
                    if (!loading && stories.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无音乐故事，点击右上角「换一批」试试", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        itemsIndexed(stories, key = { idx, s -> "story_${s.storyId}_$idx" }) { _, story ->
                            MusicStoryCard(
                                story = story,
                                onOpenDetail = {
                                    selectedStory = story
                                    storyDetailLoading = true
                                    scope.launch {
                                        val fullDetail = viewModel.getYouthChannelStoryDetail(story.storyId, currentChannelId)
                                        if (fullDetail != null) {
                                            selectedStory = fullDetail.copy(
                                                song = fullDetail.song ?: story.song
                                            )
                                        }
                                        storyDetailLoading = false
                                    }
                                },
                                onPlaySong = {
                                    story.song?.let { s ->
                                        val queue = stories.mapNotNull { it.song }.ifEmpty { listOf(s) }
                                        onSongClick(s, queue)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    if (!loading && channelSongs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无频道曲目，点击右上角「换一批」刷新", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        itemsIndexed(channelSongs, key = { idx, s -> "ch_song_${s.id}_$idx" }) { index, song ->
                            SongItemRow(
                                song = song,
                                orderIndex = index + 1,
                                isPlaying = currentSong != null && (currentSong.id == song.id || (currentSong.hash.isNotBlank() && currentSong.hash.equals(song.hash, ignoreCase = true))),
                                onClick = { onSongClick(song, channelSongs) },
                                onMoreClick = { onSongMoreClick(song) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedStory != null) {
        val story = selectedStory!!
        ModalBottomSheet(
            onDismissRequest = { selectedStory = null },
            containerColor = conceptSheetContainerColor(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ConceptPrimary.copy(alpha = 0.16f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        if (story.authorAvatar.isNotBlank()) {
                            AsyncImage(
                                model = story.authorAvatar,
                                contentDescription = story.authorName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = ConceptPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = story.authorName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "概念版音乐故事详情 · ${story.likeCount} 赞 · ${story.commentCount} 评",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    if (storyDetailLoading) {
                        CircularProgressIndicator(
                            color = ConceptPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (story.title.isNotBlank()) {
                    Text(
                        text = story.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = conceptElevatedSurfaceColor(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = story.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }

                if (story.song != null) {
                    val s = story.song
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ConceptPrimary.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                val queue = stories.mapNotNull { it.song }.ifEmpty { listOf(s) }
                                onSongClick(s, queue)
                                selectedStory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = s.coverUrl.ifBlank { story.coverUrl },
                                contentDescription = s.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ConceptPrimary.copy(alpha = 0.2f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = s.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${s.artist} · 点击播放故事配乐",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = ConceptPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FilledIconButton(
                                onClick = {
                                    val queue = stories.mapNotNull { it.song }.ifEmpty { listOf(s) }
                                    onSongClick(s, queue)
                                    selectedStory = null
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConceptPrimary),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "播放", tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun MusicStoryCard(
    story: MusicStoryItem,
    onOpenDetail: () -> Unit,
    onPlaySong: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = if (isDark) 0.dp else 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onOpenDetail)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = ConceptPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = story.authorName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${story.likeCount}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "故事详情 >",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ConceptPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = story.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 21.sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (story.song != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = conceptElevatedSurfaceColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onPlaySong)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = story.song.coverUrl.ifBlank { story.coverUrl },
                            contentDescription = story.song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ConceptPrimary.copy(alpha = 0.16f))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = story.song.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = story.song.artist,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.PlayCircleFilled,
                            contentDescription = "播放配乐",
                            tint = ConceptPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
