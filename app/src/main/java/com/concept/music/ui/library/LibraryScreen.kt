package com.concept.music.ui.library

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.kugou.AuthState
import com.concept.music.data.model.*
import com.concept.music.ui.components.CreatePlaylistDialog
import com.concept.music.ui.components.PlaylistGridItem
import com.concept.music.ui.components.PlaylistListItem
import com.concept.music.ui.components.VipBannerCard
import com.concept.music.ui.components.VipCenterBottomSheet
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.viewmodel.LibraryViewModel

/**
 * 酷狗概念版第三方 - 我的页 (全新重构)
 * 1. 沉浸式固定顶栏，自适应系统状态栏高度；
 * 2. 向上滚动时大标题与用户头像+名字平滑过渡动效，右上角常驻扫码、搜索与设置按钮；
 * 3. 饱满圆润的 VIP 领取二级界面入口胶囊；
 * 4. 听歌等级与数据展示，支持双向同步酷狗官方云端；
 * 5. 歌单与资产排版规范化，支持列表/网格切换、下拉刷新及关注歌手/已购专区浏览。
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
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    onPlaySong: (Song, List<Song>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val account by viewModel.accountState.collectAsState()
    val vip by viewModel.vipState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val userGrade by viewModel.userGrade.collectAsState()
    val isGradeSyncing by viewModel.isGradeSyncing.collectAsState()
    val libraryLoading by viewModel.libraryLoading.collectAsState()
    val followedArtists by viewModel.followedArtists.collectAsState()
    val purchasedSongs by viewModel.purchasedSongs.collectAsState()
    val purchasedAlbums by viewModel.purchasedAlbums.collectAsState()

    val myPlaylists = remember(playlists) { playlists.filter { it.isCreatedByMe && !it.isLiked } }
    val favoritePlaylists = remember(playlists) { playlists.filter { it.isFavorite } }
    val likedPlaylist = remember(playlists) { playlists.firstOrNull { it.isLiked } }
    val historyCount = remember(account) { com.concept.music.data.vip.VipManager.history.get(account.currentId).size }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 我创建的, 1: 收藏的, 2: 关注歌手, 3: 已购音乐
    var isGridView by remember { mutableStateOf(false) } // 歌单视图模式：false=列表视图, true=网格视图
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importPlaylistName by remember { mutableStateOf("") }
    var importRawText by remember { mutableStateOf("") }
    var showVipCenterSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshMusicLibrary()
            viewModel.refreshAccount()
        }
    }
    LaunchedEffect(libraryLoading) {
        if (!libraryLoading) {
            pullRefreshState.endRefresh()
        }
    }

    // 计算滑动过渡比例 (0f: 完全在顶部; 1f: 头像卡片滑出视野)
    val scrollFraction by remember {
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

    ConceptStageBackground(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
        // 主滚动内容流
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 60.dp,
                bottom = 36.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 用户头像与账号主卡片 (饱满微光背景)
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 用户大头像 (64.dp)
                        if (profile?.avatarUrl.isNullOrEmpty()) {
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                color = Color(0xFF222734)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = "账号头像",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        } else {
                            AsyncImage(
                                model = profile?.avatarUrl,
                                contentDescription = "账号头像",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
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
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 18.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (isLoggedIn) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = VipGold.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "VIP",
                                            color = VipGold,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (isLoggedIn) "酷狗概念版专属权益已同步" else "登录后同步我喜欢的音乐与专属VIP",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 登录 / 账号管理胶囊按钮
                        FilledTonalButton(
                            onClick = { if (isLoggedIn) onAccounts() else onLogin() },
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF252B3A),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isLoggedIn) "账号管理" else "立即登录",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 2. VIP 概念版领取入口 (饱满圆润胶囊卡片，点击打开二级界面)
            item {
                VipBannerCard(
                    state = vip,
                    loggedIn = isLoggedIn,
                    onClick = { showVipCenterSheet = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. 听歌等级与累计时长仪表盘 (支持听歌时长实时同步至官方云端)
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = ConceptPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Lv.${userGrade.level}",
                                    color = ConceptPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "听歌等级与时长",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                )
                                Text(
                                    text = "累计时长 ${userGrade.displayListenTimeText}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        // 同步云端时长按钮 (带旋转动效与明确反馈)
                        val rotationAngle by animateFloatAsState(
                            targetValue = if (isGradeSyncing) 360f else 0f,
                            animationSpec = tween(durationMillis = 800),
                            label = "syncRotation"
                        )

                        TextButton(
                            onClick = {
                                viewModel.refreshGradeInfo { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isGradeSyncing,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "同步",
                                tint = ConceptPrimary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .rotate(rotationAngle)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isGradeSyncing) "同步中..." else "同步云端",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = ConceptPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            // 4. 核心快捷卡片 (我喜欢的音乐 + 最近播放)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MediaShortcutCard(
                        title = "我喜欢的音乐",
                        subtitle = "${likedPlaylist?.trackCount ?: 0} 首歌曲",
                        icon = Icons.Rounded.Favorite,
                        iconTint = Color(0xFFFF5252),
                        gradientColors = listOf(Color(0xFF2E1922), Color(0xFF1B1724)),
                        onClick = { onOpenPlaylist("like") },
                        modifier = Modifier.weight(1f)
                    )

                    MediaShortcutCard(
                        title = "最近播放",
                        subtitle = "${historyCount} 首记录",
                        icon = Icons.Rounded.History,
                        iconTint = Color(0xFF448AFF),
                        gradientColors = listOf(Color(0xFF152538), Color(0xFF171926)),
                        onClick = { onOpenPlaylist("history") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 5. 歌单与音乐资产分组栏（现代胶囊分段栏 + 右侧紧凑工具组，彻底解决「已购」文字截断问题）
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val tabs = listOf(
                                "自建 ${myPlaylists.size}",
                                "收藏 ${favoritePlaylists.size}",
                                "关注 ${followedArtists.size}",
                                "已购 ${purchasedSongs.size + purchasedAlbums.size}"
                            )
                            tabs.forEachIndexed { idx, label ->
                                val isSelected = selectedTab == idx
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) ConceptPrimary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            selectedTab = idx
                                            if (idx == 2 && isLoggedIn) {
                                                viewModel.refreshFollowedArtists()
                                            } else if (idx == 3 && isLoggedIn) {
                                                viewModel.refreshPurchasedAssets()
                                            }
                                        }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) ConceptPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier.size(32.dp)
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
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileUpload,
                                    contentDescription = "导入歌单",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (selectedTab == 0 || selectedTab == 1) {
                                IconButton(
                                    onClick = { isGridView = !isGridView },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    @Suppress("DEPRECATION")
                                    Icon(
                                        imageVector = if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                        contentDescription = if (isGridView) "切换为列表视图" else "切换为网格视图",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    viewModel.refreshMusicLibrary()
                                    viewModel.refreshPurchasedAssets()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "刷新",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 6. 音乐库内容列表 (自建歌单 / 收藏歌单 / 已购资产)
            when (selectedTab) {
                0, 1 -> {
                    val currentDisplayPlaylists = if (selectedTab == 0) myPlaylists else favoritePlaylists
                    if (currentDisplayPlaylists.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (selectedTab == 0) "暂未创建自定义歌单" else "暂无收藏的歌单",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.35f))
                                )
                                if (selectedTab == 0) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { showCreateDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = ConceptPrimary),
                                        shape = RoundedCornerShape(18.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("新建歌单", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (!isGridView) {
                        items(currentDisplayPlaylists, key = { it.id }) { pl ->
                            PlaylistListItem(
                                playlist = pl,
                                onClick = { onOpenPlaylist(pl.id) }
                            )
                        }
                    } else {
                        val chunked = currentDisplayPlaylists.chunked(2)
                        items(chunked, key = { it.first().id }) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    // [P1-2] 关注歌手列表
                    if (followedArtists.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isLoggedIn) "暂未关注任何歌手" else "请先登录同步关注的歌手",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.35f))
                                )
                            }
                        }
                    } else {
                        items(followedArtists, key = { "artist_${it.id}_${it.name}" }) { artist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF161922))
                                    .clickable {
                                        if (artist.id.isNotBlank()) onOpenArtist(artist.id)
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (artist.avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = artist.avatarUrl,
                                        contentDescription = artist.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF222734))
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(50.dp),
                                        shape = CircleShape,
                                        color = Color(0xFF222734)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = ConceptPrimary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subText = buildList {
                                        if (artist.songCount > 0) add("单曲 ${artist.songCount} 首")
                                        if (artist.fansCount > 0) add("粉丝 ${artist.fansCount}")
                                    }.joinToString(" · ").ifBlank { "已关注歌手" }
                                    Text(
                                        text = subText,
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                3 -> {
                    // 已购专辑与单曲列表 ([FUNC-05])
                    if (purchasedAlbums.isEmpty() && purchasedSongs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isLoggedIn) "暂无已购数字专辑或单曲" else "请先登录同步已购音乐资产",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.35f))
                                )
                            }
                        }
                    } else {
                        if (purchasedAlbums.isNotEmpty()) {
                            item {
                                Text(
                                    text = "已购专辑 (${purchasedAlbums.size})",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                            }
                            items(purchasedAlbums, key = { "album_${it.albumId}" }) { album ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF161922))
                                        .clickable {
                                            if (album.albumId.isNotBlank()) onOpenAlbum(album.albumId)
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = album.coverUrl,
                                        contentDescription = album.albumName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF222734))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = album.albumName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val countSuffix = if (album.songCount > 0) " (${album.songCount}首)" else ""
                                        Text(
                                            text = "${album.singerName} · 数字专辑$countSuffix",
                                            color = VipGold.copy(alpha = 0.85f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.35f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (purchasedSongs.isNotEmpty()) {
                            item {
                                Text(
                                    text = "已购单曲 (${purchasedSongs.size})",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                )
                            }
                            val allPurchasedSongs = purchasedSongs.mapNotNull { it.song }
                            items(purchasedSongs, key = { "song_${it.songId}_${it.songName}" }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF161922))
                                        .clickable {
                                            item.song?.let { s ->
                                                onPlaySong(s, allPurchasedSongs.ifEmpty { listOf(s) })
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!item.song?.coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = item.song?.coverUrl,
                                            contentDescription = item.songName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF222734))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
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
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.songName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.singerName,
                                            color = Color.White.copy(alpha = 0.55f),
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
        }

        // [INT-03] 下拉刷新指示器（仅在下拉或刷新时显示，杜绝静止状态下顶部突兀圆圈）
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

        // 7. 固定沉浸式顶栏 (随页面滚动平滑切换大标题与小头像+昵称)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color(0xFF0E1015).copy(alpha = (scrollFraction * 0.96f).coerceIn(0f, 0.96f)),
            tonalElevation = (scrollFraction * 6).dp
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
                    // 左侧标题与用户小头像平滑联动切换
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // 1. 顶部未滑动时的大标题 "我的"
                        Text(
                            text = "我的",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 24.sp
                            ),
                            modifier = Modifier
                                .alpha((1f - scrollFraction * 1.5f).coerceIn(0f, 1f))
                                .offset(y = (-10 * scrollFraction).dp)
                        )

                        // 2. 向上滑动时平滑淡入并滑出的小头像 + 用户昵称
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .alpha(((scrollFraction - 0.4f) * 1.67f).coerceIn(0f, 1f))
                                .offset(y = (10 * (1f - scrollFraction)).dp)
                        ) {
                            if (profile?.avatarUrl.isNullOrEmpty()) {
                                Surface(
                                    modifier = Modifier.size(34.dp),
                                    shape = CircleShape,
                                    color = Color(0xFF222734)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                AsyncImage(
                                    model = profile?.avatarUrl,
                                    contentDescription = null,
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
                                    color = Color.White,
                                    fontSize = 17.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 右侧功能按钮组：搜索、设置
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 搜索按钮
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "搜索",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        // 设置按钮 (当前重点添加)
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "设置",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 二级界面：VIP 权益中心抽屉
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

    // 新建歌单对话框
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        showCreateDialog = false
                        selectedTab = 0
                    }
                }
            }
        )
    }

    // [P1-3] 导入外部歌单弹窗
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
                    onClick = {
                        viewModel.importExternalPlaylist(importPlaylistName, importRawText) { ok, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (ok) {
                                showImportDialog = false
                                importPlaylistName = ""
                                importRawText = ""
                                selectedTab = 0
                            }
                        }
                    }
                ) {
                    Text("开始匹配导入", color = ConceptPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            }
        )
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
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
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
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = iconTint.copy(alpha = 0.18f)
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
                        tint = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
