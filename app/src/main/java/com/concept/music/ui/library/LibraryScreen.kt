package com.concept.music.ui.library

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.kugou.AccountProfile
import com.concept.music.data.kugou.AuthState
import com.concept.music.data.model.*
import com.concept.music.data.vip.VipManager
import com.concept.music.service.FocusPodManager
import com.concept.music.ui.components.BadgeTag
import com.concept.music.ui.components.rememberOptimizedAsyncImageModel
import com.concept.music.ui.components.CreatePlaylistDialog
import com.concept.music.ui.components.FocusPodBottomSheet
import com.concept.music.ui.components.PlaylistGridItem
import com.concept.music.ui.components.PlaylistListItem
import com.concept.music.ui.components.VipBannerCard
import com.concept.music.ui.components.VipCenterBottomSheet
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
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.ConceptCategoryTabItem
import com.concept.music.ui.theme.ConceptFluidRefreshIcon
import com.concept.music.ui.theme.ConceptSlidingCategoryTabs
import com.concept.music.ui.theme.horizontalCategorySwipe
import com.concept.music.ui.theme.rememberCategorySwitchGraphicModifier
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

/**
 * 酷狗概念版第三方 - 我的页
 * - 将用户头像账号信息与「听歌等级与累计时长」深度融合为一张立体饱满的一体式数字身份名片卡
 * - 集成「概念胶囊工作台 · Focus Pod」（心流番茄钟 × 4路白噪音混音 × 心流场景伴音）
 * - 修复「已购」胶囊标签在小屏下被截断显示不全的问题
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onLogin: () -> Unit,
    onAccounts: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenVipCenter: (() -> Unit)? = null,
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    onPlaySong: (Song, List<Song>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = LocalIsDarkTheme.current
    val account by viewModel.accountState.collectAsState()
    val vip by viewModel.vipState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val userGrade by viewModel.userGrade.collectAsState()
    val isGradeSyncing by viewModel.isGradeSyncing.collectAsState()
    val libraryLoading by viewModel.libraryLoading.collectAsState()
    val followedArtists by viewModel.followedArtists.collectAsState()
    val purchasedSongs by viewModel.purchasedSongs.collectAsState()
    val purchasedAlbums by viewModel.purchasedAlbums.collectAsState()

    val selectedTab by viewModel.selectedAssetTab.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val adaptiveInfo = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current

    val myPlaylists = remember(playlists) { playlists.filter { it.isCreatedByMe && !it.isLiked } }
    val favoritePlaylists = remember(playlists) { playlists.filter { it.isFavorite } }
    val likedPlaylist = remember(playlists) { playlists.firstOrNull { it.isLiked } }
    val historyVersion by VipManager.history.historyVersion.collectAsState()
    val historyCount = remember(account, historyVersion) { VipManager.history.get(account.currentId).size }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importPlaylistName by remember { mutableStateOf("") }
    var importRawText by remember { mutableStateOf("") }
    var showVipCenterSheet by remember { mutableStateOf(false) }
    var showFocusPodSheet by remember { mutableStateOf(false) }
    val focusPodState by FocusPodManager.state.collectAsState()

    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.refreshMusicLibrary()
            viewModel.refreshAccount()
            kotlinx.coroutines.delay(600L)
            if (!libraryLoading) {
                pullRefreshState.endRefresh()
            }
        }
    }
    LaunchedEffect(libraryLoading) {
        if (!libraryLoading && pullRefreshState.isRefreshing) {
            pullRefreshState.endRefresh()
        }
    }

    // 关键 120fps 性能优化：不在 LibraryScreen 根组合作用域直接解包读取 scrollFraction，
    // 而是将 scrollFractionState 传入顶栏的 drawBehind 与 graphicsLayer 闭包中仅在绘制期读取，
    // 使「我的」页上下滑动全程触发 0 次整屏重组与 0 次 Layout 重排！
    val scrollFractionState = remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val offset = listState.firstVisibleItemScrollOffset.toFloat()
                (offset / 140f).coerceIn(0f, 1f)
            }
        }
    }

    val profile = account.profile
    val isLoggedIn = account.auth == AuthState.READY
    val elevatedColor = conceptElevatedSurfaceColor()

    ConceptStageBackground(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 860.dp)
                .align(Alignment.TopCenter)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 60.dp,
                bottom = 112.dp
            )
        ) {
            // 1. 头像账号与听歌等级时长一体式数字名片（去嵌套盒子化，视觉通透开阔）
            item(key = "lib_profile_card", contentType = "lib_profile_card") {
                UnifiedProfileAndGradeCard(
                    profile = profile,
                    isLoggedIn = isLoggedIn,
                    userGrade = userGrade,
                    isGradeSyncing = isGradeSyncing,
                    onAccountAction = { if (isLoggedIn) onAccounts() else onLogin() },
                    onSyncGrade = {
                        viewModel.refreshGradeInfo { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 2. VIP 概念版领取入口
            item(key = "lib_vip_banner", contentType = "lib_vip_banner") {
                VipBannerCard(
                    state = vip,
                    loggedIn = isLoggedIn,
                    onClick = { if (onOpenVipCenter != null) onOpenVipCenter() else showVipCenterSheet = true },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 3. 核心快捷入口（我喜欢的音乐 + 最近播放，双色渐变点缀）
            item(key = "lib_shortcuts", contentType = "lib_shortcuts") {
                val likeGradient = if (isDark) {
                    listOf(Color(0xFF2B1822), Color(0xFF191724))
                } else {
                    listOf(Color(0xFFFFE8EE), Color(0xFFFFF3F6))
                }
                val historyGradient = if (isDark) {
                    listOf(Color(0xFF132638), Color(0xFF151926))
                } else {
                    listOf(Color(0xFFE3F2FF), Color(0xFFF0F7FF))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MediaShortcutCard(
                        title = "我喜欢的音乐",
                        subtitle = "${likedPlaylist?.trackCount ?: 0} 首歌曲",
                        icon = Icons.Rounded.Favorite,
                        iconTint = ConceptCoralAccent,
                        gradientColors = likeGradient,
                        onClick = { onOpenPlaylist("like") },
                        modifier = Modifier.weight(1f)
                    )

                    MediaShortcutCard(
                        title = "最近播放",
                        subtitle = "$historyCount 首记录",
                        icon = Icons.Rounded.History,
                        iconTint = ConceptPrimary,
                        gradientColors = historyGradient,
                        onClick = { onOpenPlaylist("history") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 4. 概念胶囊工作台 · Focus Pod（去除右侧胶囊按钮，改为极简文字指示，减少页面圆角按钮密度）
            item(key = "lib_focus_pod", contentType = "lib_focus_pod") {
                val podActive = focusPodState.isRunning || focusPodState.activeTracks.isNotEmpty()
                val podAccent = if (isDark) Color(0xFF9688FF) else ConceptVioletAccent
                val podBrush = if (isDark) {
                    Brush.linearGradient(listOf(Color(0xFF1A1E36), Color(0xFF121829)))
                } else {
                    Brush.linearGradient(listOf(Color(0xFFEEEBFF), Color(0xFFEAF4FF)))
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .softCardClickable { showFocusPodSheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(podBrush)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(podAccent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SelfImprovement,
                                contentDescription = "概念胶囊工作台",
                                tint = podAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(13.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "概念胶囊 · Focus Pod",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (focusPodState.isRunning) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "专注 ${focusPodState.formattedRemaining}",
                                        color = podAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (focusPodState.activeTracks.isNotEmpty()) {
                                    "正在混音 ${focusPodState.activeTracks.size} 路环境音"
                                } else {
                                    "专注定时与环境伴音舱"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (podActive) "控制台" else "开启心流",
                                color = podAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = podAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(22.dp))
            }

            // 5. 开放式极简音乐资产导航栏（彻底告别 4 个胶囊按钮 + 4 个图标按钮的密集感）
            item(key = "lib_asset_tabs", contentType = "lib_asset_tabs") {
                val assetTabs = listOf(
                    ConceptCategoryTabItem("0", "自建", myPlaylists.size.toString(), ConceptPrimary),
                    ConceptCategoryTabItem("1", "收藏", favoritePlaylists.size.toString(), ConceptCoralAccent),
                    ConceptCategoryTabItem("2", "关注", followedArtists.size.toString(), ConceptMintAccent),
                    ConceptCategoryTabItem("3", "已购", (purchasedSongs.size + purchasedAlbums.size).toString(), VipGold)
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    ConceptSlidingCategoryTabs(
                        tabs = assetTabs,
                        selectedKey = selectedTab.toString(),
                        onSelectTab = { keyStr ->
                            val idx = keyStr.toIntOrNull() ?: 0
                            viewModel.setSelectedAssetTab(idx)
                            if (idx == 2 && isLoggedIn && followedArtists.isEmpty()) {
                                viewModel.refreshFollowedArtists()
                            } else if (idx == 3 && isLoggedIn && purchasedSongs.isEmpty() && purchasedAlbums.isEmpty()) {
                                viewModel.refreshPurchasedAssets()
                            }
                        },
                        itemSpacing = 20.dp,
                        fontSize = 15.5.sp,
                        indicatorWidth = 20.dp,
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(bottom = 2.dp)
                            ) {
                                if (selectedTab == 0) {
                                    IconButton(
                                        onClick = {
                                            if (account.currentId == null) {
                                                Toast.makeText(context, "请先登录酷狗账号", Toast.LENGTH_SHORT).show()
                                                onLogin()
                                            } else {
                                                showCreateDialog = true
                                            }
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "新建歌单",
                                            tint = ConceptPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { showImportDialog = true },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FileUpload,
                                            contentDescription = "导入歌单",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                                if (selectedTab == 0 || selectedTab == 1) {
                                    IconButton(
                                        onClick = { viewModel.toggleGridView() },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        @Suppress("DEPRECATION")
                                        Icon(
                                            imageVector = if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                            contentDescription = if (isGridView) "切换为列表视图" else "切换为网格视图",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                } else {
                                    var assetRefreshSpin by remember { mutableIntStateOf(0) }
                                    IconButton(
                                        onClick = {
                                            assetRefreshSpin += 1
                                            if (selectedTab == 2) viewModel.refreshFollowedArtists()
                                            else if (selectedTab == 3) viewModel.refreshPurchasedAssets()
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        ConceptFluidRefreshIcon(
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            isRefreshing = libraryLoading,
                                            spinTrigger = assetRefreshSpin,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // 6. 开放式无框音乐资产内容流 (自建歌单 / 收藏歌单 / 关注歌手 / 已购资产)
            when (selectedTab) {
                0, 1 -> {
                    val currentDisplayPlaylists = if (selectedTab == 0) myPlaylists else favoritePlaylists
                    if (currentDisplayPlaylists.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (selectedTab == 0) "暂未创建自定义歌单" else "暂无收藏的歌单",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                if (selectedTab == 0) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    TextButton(onClick = {
                                        if (account.currentId == null) {
                                            Toast.makeText(context, "请先登录酷狗账号", Toast.LENGTH_SHORT).show()
                                            onLogin()
                                        } else {
                                            showCreateDialog = true
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            tint = ConceptPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("新建歌单", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ConceptPrimary)
                                    }
                                }
                            }
                        }
                    } else if (!isGridView && adaptiveInfo.navigationMode != com.concept.music.ui.theme.ConceptNavigationMode.NAVIGATION_RAIL) {
                        itemsIndexed(
                            items = currentDisplayPlaylists,
                            key = { idx, pl -> "pl_list_${pl.id}_${pl.listId}_$idx" },
                            contentType = { _, _ -> "playlist_list_item" }
                        ) { _, pl ->
                            PlaylistListItem(
                                playlist = pl,
                                onClick = { onOpenPlaylist(pl.id) }
                            )
                        }
                    } else {
                        val chunked = currentDisplayPlaylists.chunked(2)
                        itemsIndexed(
                            items = chunked,
                            key = { idx, row -> "pl_grid_${row.first().id}_$idx" },
                            contentType = { _, _ -> "playlist_grid_row" }
                        ) { _, rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                rowItems.forEach { pl ->
                                    PlaylistGridItem(
                                        playlist = pl,
                                        onClick = { onOpenPlaylist(pl.id) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // 关注歌手列表（无外框开放式行排版 + 极光薄荷绿点缀）
                    if (followedArtists.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isLoggedIn) "暂未获取到已关注的歌手" else "请先登录同步关注的歌手",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                if (isLoggedIn) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    var followSyncSpin by remember { mutableIntStateOf(0) }
                                    TextButton(onClick = {
                                        followSyncSpin += 1
                                        viewModel.refreshFollowedArtists()
                                    }) {
                                        ConceptFluidRefreshIcon(
                                            tint = ConceptMintAccent,
                                            isRefreshing = libraryLoading,
                                            spinTrigger = followSyncSpin,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("重新同步", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ConceptMintAccent)
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = followedArtists,
                            key = { idx, a -> "artist_${a.id}_${a.name}_$idx" },
                            contentType = { _, _ -> "followed_artist_item" }
                        ) { _, artist ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .softClickable {
                                        if (artist.id.isNotBlank()) onOpenArtist(artist.id)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val artistAvatarModel = rememberOptimizedAsyncImageModel(rawUrl = artist.avatarUrl, sizePx = 156, targetCdnSize = "150")
                                    if (artistAvatarModel != null) {
                                        AsyncImage(
                                            model = artistAvatarModel,
                                            contentDescription = artist.name,
                                            contentScale = ContentScale.Crop,
                                            filterQuality = FilterQuality.Low,
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(elevatedColor)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(ConceptMintAccent.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = ConceptMintAccent,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = artist.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val subText = buildList {
                                            if (artist.songCount > 0) add("单曲 ${artist.songCount} 首")
                                            if (artist.fansCount > 0) add("粉丝 ${artist.fansCount}")
                                        }.joinToString(" · ").ifBlank { "已关注歌手" }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(ConceptMintAccent)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = subText,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 72.dp, end = 4.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                )
                            }
                        }
                    }
                }
                3 -> {
                    // 已购专辑与单曲列表（无外框开放式行排版 + 琥珀金点缀）
                    if (purchasedAlbums.isEmpty() && purchasedSongs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isLoggedIn) "暂无已购数字专辑或单曲" else "请先登录同步已购音乐资产",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    } else {
                        if (purchasedAlbums.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(13.dp)
                                            .clip(CircleShape)
                                            .background(VipGold)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "数字专辑 · ${purchasedAlbums.size}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            itemsIndexed(
                                items = purchasedAlbums,
                                key = { idx, a -> "album_${a.albumId}_$idx" },
                                contentType = { _, _ -> "purchased_album_item" }
                            ) { _, album ->
                                val albumThumbModel = rememberOptimizedAsyncImageModel(rawUrl = album.coverUrl, sizePx = 156, targetCdnSize = "150")
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .softClickable {
                                            if (album.albumId.isNotBlank()) onOpenAlbum(album.albumId)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = albumThumbModel ?: album.coverUrl,
                                            contentDescription = album.albumName,
                                            contentScale = ContentScale.Crop,
                                            filterQuality = FilterQuality.Low,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(elevatedColor)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = album.albumName,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            val countSuffix = if (album.songCount > 0) " · ${album.songCount} 首" else ""
                                            Text(
                                                text = "${album.singerName}$countSuffix",
                                                color = if (isDark) VipGold else Color(0xFFB87900),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 74.dp, end = 4.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    )
                                }
                            }
                        }

                        if (purchasedSongs.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(13.dp)
                                            .clip(CircleShape)
                                            .background(ConceptPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "已购单曲 · ${purchasedSongs.size}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            val allPurchasedSongs = purchasedSongs.mapNotNull { it.song }
                            itemsIndexed(
                                items = purchasedSongs,
                                key = { idx, s -> "song_${s.songId}_${s.songName}_$idx" },
                                contentType = { _, _ -> "purchased_song_item" }
                            ) { _, item ->
                                val s = item.song
                                val songThumbModel = rememberOptimizedAsyncImageModel(rawUrl = s?.coverUrl, sizePx = 144, targetCdnSize = "150")
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .softClickable {
                                            s?.let { songObj ->
                                                onPlaySong(songObj, allPurchasedSongs.ifEmpty { listOf(songObj) })
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (songThumbModel != null) {
                                            AsyncImage(
                                                model = songThumbModel,
                                                contentDescription = item.songName,
                                                contentScale = ContentScale.Crop,
                                                filterQuality = FilterQuality.Low,
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(elevatedColor)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(ConceptPrimary.copy(alpha = 0.14f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MusicNote,
                                                    contentDescription = null,
                                                    tint = ConceptPrimary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.songName,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (s?.isHiRes == true) {
                                                    BadgeTag(text = "Hi-Res", color = VipGold)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                } else if (s?.isLossless == true) {
                                                    BadgeTag(text = "SQ", color = ConceptPrimary)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                } else if (s?.quality == "320") {
                                                    BadgeTag(text = "HQ", color = ConceptPrimary.copy(alpha = 0.85f))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = item.singerName,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 68.dp, end = 4.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    )
                                }
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

        // 7. 固定沉浸式顶栏（100% Draw/Layer 阶段读取 scrollFractionState，上下滑动 0 重组、0 重排）
        val topBarBaseColor = if (isDark) Color(0xFF0A0F1A) else Color(0xFFF3F8FF)
        val topAvatarModel = rememberOptimizedAsyncImageModel(rawUrl = profile?.avatarUrl, sizePx = 120, targetCdnSize = "150")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .drawBehind {
                    val sf = scrollFractionState.value
                    if (sf > 0.005f) {
                        drawRect(color = topBarBaseColor.copy(alpha = (sf * 0.96f).coerceIn(0f, 0.96f)))
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "我的",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 24.sp
                            ),
                            modifier = Modifier.graphicsLayer {
                                val sf = scrollFractionState.value
                                alpha = (1f - sf * 1.5f).coerceIn(0f, 1f)
                                translationY = -10.dp.toPx() * sf
                            }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.graphicsLayer {
                                val sf = scrollFractionState.value
                                alpha = ((sf - 0.4f) * 1.67f).coerceIn(0f, 1f)
                                translationY = 10.dp.toPx() * (1f - sf)
                            }
                        ) {
                            if (topAvatarModel == null) {
                                Surface(
                                    modifier = Modifier.size(34.dp),
                                    shape = CircleShape,
                                    color = elevatedColor
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                AsyncImage(
                                    model = topAvatarModel,
                                    contentDescription = null,
                                    filterQuality = FilterQuality.Low,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Text(
                                text = profile?.nickname ?: "我的",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 17.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "设置",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showVipCenterSheet) {
        VipCenterBottomSheet(
            state = vip,
            loggedIn = isLoggedIn,
            autoClaim = account.current?.autoClaim ?: false,
            onDismiss = { showVipCenterSheet = false },
            onClaim = { viewModel.claimVip() },
            onAutoChange = { viewModel.setAuto(it) },
            onRefresh = { viewModel.refreshAccount() },
            onLogin = {
                showVipCenterSheet = false
                onLogin()
            }
        )
    }

    if (showFocusPodSheet) {
        FocusPodBottomSheet(
            onDismiss = { showFocusPodSheet = false },
            onPlaySceneMusic = { sceneId ->
                coroutineScope.launch {
                    val songs = runCatching { VipManager.music.sceneRecommendSongs(sceneId, size = 20) }.getOrDefault(emptyList())
                    if (songs.isNotEmpty()) {
                        onPlaySong(songs.first(), songs)
                    }
                }
            }
        )
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirmWithCallback = { name, onComplete ->
                viewModel.createPlaylist(name) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onComplete(success)
                    if (success) {
                        viewModel.setSelectedAssetTab(0)
                    }
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入外部歌单", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = importPlaylistName,
                        onValueChange = { importPlaylistName = it },
                        singleLine = true,
                        label = { Text("新歌单名称") },
                        placeholder = { Text("例如：我的精选导入") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = importRawText,
                        onValueChange = { importRawText = it },
                        minLines = 4,
                        maxLines = 8,
                        label = { Text("曲目列表（每行一首：歌名 - 歌手）") },
                        placeholder = { Text("晴天 - 周杰伦\n江南 - 林俊杰") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = {
                        isImporting = true
                        viewModel.importExternalPlaylist(importPlaylistName, importRawText) { ok, msg ->
                            isImporting = false
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (ok) {
                                showImportDialog = false
                                importPlaylistName = ""
                                importRawText = ""
                                viewModel.setSelectedAssetTab(0)
                            }
                        }
                    }
                ) {
                    Text(if (isImporting) "正在导入..." else "开始匹配导入", color = ConceptPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !isImporting, onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 头像账号与听歌等级时长一体式融合名片卡：
 * - 上层：用户头像（右下角内嵌 Lv.xx 勋章）+ 昵称与 VIP 金标 + 账号管理胶囊
 * - 下层：内嵌式听歌等级进度与累计时长仪表舱 + 云端同步按钮
 */
@Composable
private fun UnifiedProfileAndGradeCard(
    profile: AccountProfile?,
    isLoggedIn: Boolean,
    userGrade: UserGradeInfo,
    isGradeSyncing: Boolean,
    onAccountAction: () -> Unit,
    onSyncGrade: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val cardColor = conceptFloatingCardColor()
    val elevatedColor = conceptElevatedSurfaceColor()
    val mintAccent = if (isDark) Color(0xFF26DEBF) else ConceptMintAccent

    var syncClickSpin by remember { mutableIntStateOf(0) }

    val cardBrush = if (isDark) {
        Brush.linearGradient(
            listOf(Color(0xFF16243A), Color(0xFF131B2B))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFFF3F8FF))
        )
    }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = cardColor,
        shadowElevation = if (isDark) 0.dp else 5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBrush)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 上层：头像 + 昵称与 VIP 标识 + 极简文字链入口（去除胶囊按钮堆叠）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(62.dp)) {
                    if (profile?.avatarUrl.isNullOrEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = elevatedColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = "账号头像",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = profile?.avatarUrl,
                            contentDescription = "账号头像",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = profile?.nickname ?: "登录酷狗账号",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isLoggedIn) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VipGold.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "VIP",
                                    color = if (isDark) VipGold else Color(0xFFB87900),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isLoggedIn) "云端曲库与听歌等级已同步" else "登录同步云端歌单与听歌时长",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .softClickable(onClick = onAccountAction)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isLoggedIn) "账号管理" else "立即登录",
                        color = ConceptPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = ConceptPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
            )

            // 下层：开放式听歌等级与累计时长展示（仅展示账号等级与累计听歌时长，移除等级进度条）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = mintAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Lv.${userGrade.level}",
                        color = mintAccent,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "·  累计听歌 ${userGrade.displayListenTimeText}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .softClickable(enabled = !isGradeSyncing) {
                            syncClickSpin += 1
                            onSyncGrade()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    ConceptFluidRefreshIcon(
                        tint = ConceptPrimary,
                        isRefreshing = isGradeSyncing,
                        spinTrigger = syncClickSpin,
                        modifier = Modifier.size(13.5.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isGradeSyncing) "同步中" else "同步云端",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ConceptPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaShortcutCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val titleColor = if (isDark) Color.White else Color(0xFF101C2E)
    val subColor = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF4F6482)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
        modifier = modifier
            .softCardClickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = iconTint.copy(alpha = 0.16f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = subColor.copy(alpha = 0.55f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = subColor,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
