package com.concept.music.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.concept.music.ui.theme.ConceptMotion
import kotlinx.coroutines.isActive
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.TransformOrigin
import com.concept.music.ui.theme.conceptCategoryContentTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.Song
import com.concept.music.ui.components.PlayingAudioWaveIndicator
import com.concept.music.ui.components.FloatingScrollToTopButton
import com.concept.music.ui.components.rememberProgressiveSongList
import com.concept.music.ui.components.smoothFastScrollToItem
import com.concept.music.ui.components.SongItemRow
import com.concept.music.ui.theme.ConceptCoralAccent
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptMintAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryDark
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.ConceptVioletAccent
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.rememberIsLifecycleStarted
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.ConceptCategoryTabItem
import com.concept.music.ui.theme.ConceptFluidRefreshIcon
import com.concept.music.ui.theme.ConceptSlidingCategoryTabs
import com.concept.music.ui.theme.horizontalCategorySwipe
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

/**
 * 精选场景电台独立页面 (重构极简概念版风格)：
 * 1. 做减法：删除顶部栏副标题、删除顶部栏与列表头重复的「换一批」按钮、删除冗长分类说明；
 * 2. 调频舱集中操控：支持「播放全部」「换一批」「+20首」三键一体操控；
 * 3. 修复选中电台卡片时的方形阴影透底与明暗色差 Bug（采用 100% 不透明高亮底色 + 精致描边 + 零透底阴影）；
 * 4. 注入现代多彩点缀美学，不同分类电台呈现差异化点缀色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    viewModel: HomeViewModel,
    initialChannelId: String = "",
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onSongMoreClick: (Song) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalIsDarkTheme.current

    val fmChannels by viewModel.fmChannels.collectAsState()
    val adaptiveInfo = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current
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
        (listOf("全部") + fmChannels.map { it.category.ifBlank { "推荐电台" } }.distinct()).take(5)
    }
    val filteredStations = remember(fmChannels, selectedCategory) {
        if (selectedCategory == "全部") fmChannels
        else fmChannels.filter { (it.category.ifBlank { "推荐电台" }) == selectedCategory }
    }

    var showAllStationsGrid by remember { mutableStateOf(false) }
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
                    "已追加 ${newUnique.size} 首「${currentChannel.name}」新曲",
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
        val cached = viewModel.getCachedFmChannelSongs(selectedChannelId)
        if (cached.isNotEmpty()) {
            stationSongs = cached
            loading = false
        } else {
            loading = true
            val firstBatch = viewModel.getFmChannelSongs(selectedChannelId, size = 20, forceNextBatch = true)
            stationSongs = firstBatch
            loading = false
        }
        // 静默补齐漫游队列：在首屏渲染完成后，后台静默补充 2 批电台曲目并以微批次平滑注入列表
        for (extraRound in 1..2) {
            kotlinx.coroutines.delay(320L)
            if (!isActive || stationSongs.size >= 60) break
            val moreBatch = runCatching {
                viewModel.getFmChannelSongs(selectedChannelId, size = 20, forceNextBatch = true)
            }.getOrDefault(emptyList())
            if (moreBatch.isEmpty()) break
            val existingKeys = stationSongs.map { it.hash.ifBlank { it.id }.lowercase() }.toHashSet()
            val newUnique = moreBatch.filter { existingKeys.add(it.hash.ifBlank { it.id }.lowercase()) }
            if (newUnique.isNotEmpty()) {
                stationSongs = stationSongs + newUnique
            } else {
                break
            }
        }
    }

    val fallbackCovers = remember(dailySongs) {
        dailySongs.mapNotNull { it.coverUrl.takeIf { u -> u.isNotBlank() } }
    }

    fun fmFrequencyLabel(id: String): String {
        val hash = kotlin.math.abs(id.hashCode())
        val major = 88 + (hash % 20)
        val minor = (hash / 20) % 10
        return "FM $major.$minor"
    }

    // 电台黑胶唱盘按需旋转：仅在前台且音乐处于播放状态时驱动旋转，暂停或切后台时零空跑帧循环
    val isLifecycleStarted = rememberIsLifecycleStarted()
    val isTunerPlaying = isPlaying && currentSong != null && isLifecycleStarted
    val vinylRotationAnim = remember { Animatable(0f) }
    var wasTunerPlaying by remember { mutableStateOf(isTunerPlaying) }
    LaunchedEffect(isTunerPlaying) {
        if (isTunerPlaying) {
            wasTunerPlaying = true
            while (isActive) {
                vinylRotationAnim.animateTo(
                    targetValue = vinylRotationAnim.value + 360f,
                    animationSpec = tween(durationMillis = 18000, easing = LinearEasing)
                )
            }
        } else if (wasTunerPlaying && !isPlaying) {
            wasTunerPlaying = false
            vinylRotationAnim.animateTo(
                targetValue = vinylRotationAnim.value + 15f,
                animationSpec = tween(durationMillis = 450, easing = ConceptMotion.EmphasizedDecelerate)
            )
        }
    }

    val expandArrowRotation by animateFloatAsState(
        targetValue = if (showAllStationsGrid) 180f else 0f,
        animationSpec = ConceptMotion.expressiveSpring(),
        label = "stationGridArrow"
    )

    val currentCategoryAccent = remember(currentChannel.category) {
        radioCategoryAccentColor(currentChannel.category)
    }

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "场景电台",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp
                            )
                        )
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
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = currentCategoryAccent.copy(alpha = 0.14f),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .softCardClickable { showAllStationsGrid = !showAllStationsGrid }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Radio,
                                    contentDescription = null,
                                    tint = currentCategoryAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (showAllStationsGrid) "收起电台" else "切换电台",
                                    color = currentCategoryAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = currentCategoryAccent,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(expandArrowRotation)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            val currentCatIdx = categories.indexOf(selectedCategory).coerceAtLeast(0)
            val radioListState = rememberLazyListState()
            val displayedRadioSongs by rememberProgressiveSongList(
                sourceSongs = stationSongs,
                listState = radioListState,
                resetKey = selectedChannelId,
                initialBatchSize = 12,
                microBatchSize = 8
            )
            val latestStationSongs by rememberUpdatedState(stationSongs)
            val curRadioPlayingId = currentSong?.id
            val curRadioPlayingHash = currentSong?.hash.orEmpty()
            val showRadioScrollTop by remember(stationSongs.size) {
                derivedStateOf {
                    radioListState.firstVisibleItemIndex > 2 ||
                        (radioListState.firstVisibleItemIndex == 2 && radioListState.firstVisibleItemScrollOffset > 100)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
            LazyColumn(
                state = radioListState,
                modifier = Modifier
                    .widthIn(max = 920.dp)
                    .fillMaxSize()
                    .horizontalCategorySwipe(
                        currentIndex = currentCatIdx,
                        itemCount = categories.size,
                        onSelectIndex = { nextIdx ->
                            categories.getOrNull(nextIdx)?.let { cat ->
                                selectedCategory = cat
                                showAllStationsGrid = true
                            }
                        }
                    ),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 顶部沉浸式 FM 调频广播舱（唯一操作中心：播放全部 + 换一批 + +20首）
                item {
                    val tunerBrush = if (isDark) {
                        Brush.linearGradient(
                            listOf(
                                currentCategoryAccent.copy(alpha = 0.28f),
                                Color(0xFF132238),
                                Color(0xFF121928)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                currentCategoryAccent.copy(alpha = 0.18f),
                                Color(0xFFEAF4FF),
                                Color(0xFFF6F3FF)
                            )
                        )
                    }
                    val titleColor = if (isDark) Color.White else Color(0xFF0E1B2E)
                    val subColor = if (isDark) Color.White.copy(alpha = 0.72f) else Color(0xFF425875)
                    val cardBaseColor = conceptFloatingCardColor()

                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = cardBaseColor,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(tunerBrush)
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 旋转电台黑胶唱盘
                                Box(
                                    modifier = Modifier.size(82.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                rotationZ = vinylRotationAnim.value % 360f
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
                                                .size(60.dp)
                                                .graphicsLayer {
                                                    rotationZ = vinylRotationAnim.value % 360f
                                                    shape = CircleShape
                                                    clip = true
                                                }
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Radio,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(if (isDark) Color(0xFF102039) else Color(0xFFE8F3FF))
                                    )
                                }

                                Spacer(modifier = Modifier.width(15.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(7.dp),
                                            color = currentCategoryAccent.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = fmFrequencyLabel(currentChannel.id),
                                                color = currentCategoryAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(7.dp),
                                            color = ConceptPrimary.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = currentChannel.category.ifBlank { "推荐电台" },
                                                color = ConceptPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = currentChannel.name,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = titleColor,
                                                fontSize = 20.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        PlayingAudioWaveIndicator(color = currentCategoryAccent, isPlaying = isTunerPlaying)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentChannel.description.ifBlank { "随心漫游同频旋律" },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = subColor,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (stationSongs.isNotEmpty()) {
                                            onSongClick(stationSongs.first(), stationSongs)
                                        }
                                    },
                                    enabled = stationSongs.isNotEmpty(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = currentCategoryAccent,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "播放全部",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }

                                var radioRefreshSpin by remember { mutableIntStateOf(0) }
                                FilledTonalButton(
                                    onClick = {
                                        radioRefreshSpin += 1
                                        loadStationBatch(currentChannel.id, append = false)
                                        Toast.makeText(context, "已焕新「${currentChannel.name}」曲目", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = !loading && !appending,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isDark) Color.White.copy(alpha = 0.12f) else currentCategoryAccent.copy(alpha = 0.14f),
                                        contentColor = if (isDark) Color.White else currentCategoryAccent
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    ConceptFluidRefreshIcon(
                                        tint = if (isDark) Color.White else currentCategoryAccent,
                                        isRefreshing = loading,
                                        spinTrigger = radioRefreshSpin,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "换一批",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { loadStationBatch(currentChannel.id, append = true) },
                                    enabled = !loading && !appending,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isDark) Color.White.copy(alpha = 0.12f) else ConceptPrimary.copy(alpha = 0.14f),
                                        contentColor = if (isDark) Color.White else ConceptPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = if (appending) "漫游中" else "+20首",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. 分段式电台分类栏 + 平滑折叠电台矩阵
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 开放式极简电台分类指示栏（无胶囊外壳）
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val radioTabItems = remember(categories) {
                                categories.map { cat ->
                                    ConceptCategoryTabItem(
                                        key = cat,
                                        label = cat.removeSuffix("电台"),
                                        accentColor = radioCategoryAccentColor(cat)
                                    )
                                }
                            }
                            ConceptSlidingCategoryTabs(
                                tabs = radioTabItems,
                                selectedKey = selectedCategory,
                                onSelectTab = { cat ->
                                    selectedCategory = cat
                                    showAllStationsGrid = true
                                },
                                itemSpacing = 24.dp,
                                fontSize = 14.sp,
                                indicatorWidth = 18.dp
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }

                        AnimatedVisibility(
                            visible = showAllStationsGrid,
                            enter = expandVertically(
                                expandFrom = Alignment.Top,
                                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                            ) + scaleIn(
                                initialScale = 0.94f,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(animationSpec = tween(220)),
                            exit = shrinkVertically(
                                shrinkTowards = Alignment.Top,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
                            ) + scaleOut(
                                targetScale = 0.94f,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
                            ) + fadeOut(animationSpec = tween(180))
                        ) {
                            AnimatedContent(
                                targetState = selectedCategory,
                                transitionSpec = {
                                    val initIdx = categories.indexOf(initialState).coerceAtLeast(0)
                                    val tgtIdx = categories.indexOf(targetState).coerceAtLeast(0)
                                    conceptCategoryContentTransition(if (tgtIdx >= initIdx) 1 else -1)
                                },
                                label = "radioCategoryGridTransition"
                            ) { activeCat ->
                                val catStations = remember(fmChannels, activeCat) {
                                    if (activeCat == "全部") fmChannels
                                    else fmChannels.filter { (it.category.ifBlank { "推荐电台" }) == activeCat }
                                }
                                val stationRows = remember(catStations) { catStations.chunked(2) }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    stationRows.forEachIndexed { rowIdx, pair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                                    onClick = {
                                                        selectedChannelId = ch.id
                                                        showAllStationsGrid = false
                                                    },
                                                    onPlayImmediate = {
                                                        selectedChannelId = ch.id
                                                        showAllStationsGrid = false
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
                }

                // 3. 精简后的电台漫游列表头（无重复按钮、无冗余小字）
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "漫游列表",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                        )
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
                            CircularProgressIndicator(color = currentCategoryAccent)
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
                            Text("暂未获取到电台曲目，请点击上方「换一批」", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (adaptiveInfo.useTwoColumnSongOrCardGrid) {
                    val radioPairs = displayedRadioSongs.mapIndexed { idx, s -> idx to s }.chunked(2)
                    itemsIndexed(
                        items = radioPairs,
                        key = { pIdx, pair -> "fm_song_pair_${pair.first().second.id}_$pIdx" },
                        contentType = { _, _ -> "song_pair_row" }
                    ) { _, pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { (index, song) ->
                                Box(modifier = Modifier.weight(1f)) {
                                    SongItemRow(
                                        song = song,
                                        orderIndex = index + 1,
                                        isPlaying = curRadioPlayingId != null && (curRadioPlayingId == song.id || (curRadioPlayingHash.isNotEmpty() && curRadioPlayingHash.equals(song.hash, ignoreCase = true))),
                                        onClick = { onSongClick(song, latestStationSongs) },
                                        onMoreClick = { onSongMoreClick(song) }
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    itemsIndexed(
                        items = displayedRadioSongs,
                        key = { idx, s -> "fm_song_${s.id}_$idx" },
                        contentType = { _, _ -> "song_row" }
                    ) { index, song ->
                        SongItemRow(
                            song = song,
                            orderIndex = index + 1,
                            isPlaying = curRadioPlayingId != null && (curRadioPlayingId == song.id || (curRadioPlayingHash.isNotEmpty() && curRadioPlayingHash.equals(song.hash, ignoreCase = true))),
                            onClick = { onSongClick(song, latestStationSongs) },
                            onMoreClick = { onSongMoreClick(song) }
                        )
                    }
                }
            }

            FloatingScrollToTopButton(
                visible = showRadioScrollTop,
                accentColor = currentCategoryAccent,
                onClick = {
                    scope.launch {
                        radioListState.smoothFastScrollToItem(0)
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

private fun radioCategoryAccentColor(category: String): Color {
    return when {
        category.contains("年代") || category.contains("语种") -> ConceptCoralAccent
        category.contains("曲风") || category.contains("流派") -> ConceptVioletAccent
        category.contains("心情") || category.contains("场景") -> ConceptMintAccent
        category.contains("推荐") -> ConceptCyanAccent
        else -> ConceptPrimary
    }
}

/**
 * 电台墙双列宫格卡片：
 * 修复选中态半透明底色叠加硬件阴影导致的方形灰块明暗差异问题 ——
 * 采用 100% 不透明高亮容器色 + 主题色精致描边，选中态视觉纯粹统一。
 */
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
    val accent = remember(channel.category) { radioCategoryAccentColor(channel.category) }

    val selectedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "radioCardSelectScale_${channel.id}"
    )

    val baseCardColor = conceptFloatingCardColor()
    val selectedGradient = Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = if (isDark) 0.28f else 0.18f),
            accent.copy(alpha = if (isDark) 0.12f else 0.06f)
        )
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = baseCardColor,
        shadowElevation = 0.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = selectedScale
                scaleY = selectedScale
            }
            .softCardClickable(shape = RoundedCornerShape(22.dp), onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) Modifier.background(selectedGradient)
                    else Modifier
                )
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(102.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), ConceptPrimaryDark)))
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
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) accent else Color.Black.copy(alpha = 0.46f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = if (isSelected) "正在调频" else frequencyText,
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = channel.category.removeSuffix("电台"),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 7.dp)
                )

                FilledIconButton(
                    onClick = onPlayImmediate,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                        contentDescription = "播放电台",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = channel.description,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isSelected) accent.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
