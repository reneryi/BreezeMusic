package com.concept.music.ui.library

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.concept.music.ui.components.BadgeTag
import com.concept.music.ui.components.CreatePlaylistDialog
import com.concept.music.ui.components.PlaylistGridItem
import com.concept.music.ui.components.PlaylistListItem
import com.concept.music.ui.components.VipBannerCard
import com.concept.music.ui.components.VipCenterBottomSheet
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.viewmodel.LibraryViewModel

/**
 * 酷狗概念版第三方 - 我的页
 * - 完整适配深色/浅色双主题，消除浅色模式下的深黑卡片与白字不可读问题
 * - 所有悬浮卡片采用无边框圆润饱满设计
 * - 资产分组 Tab (自建/收藏/关注/已购) 状态持久化在 LibraryViewModel 中，进入专辑或歌单返回后保持原 Tab
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

    // 从 ViewModel 收集持久化的分组 Tab 与视图模式，杜绝从详情页返回后自动跳回「自建」
    val selectedTab by viewModel.selectedAssetTab.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()

    val myPlaylists = remember(playlists) { playlists.filter { it.isCreatedByMe && !it.isLiked } }
    val favoritePlaylists = remember(playlists) { playlists.filter { it.isFavorite } }
    val likedPlaylist = remember(playlists) { playlists.firstOrNull { it.isLiked } }
    val historyCount = remember(account) { com.concept.music.data.vip.VipManager.history.get(account.currentId).size }

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
    val cardColor = conceptFloatingCardColor()
    val elevatedColor = conceptElevatedSurfaceColor()

    ConceptStageBackground(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 60.dp,
                bottom = 108.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 用户头像与账号主卡片 (无边框饱满悬浮块，深浅色自适应)
            item {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = cardColor,
                    shadowElevation = if (isDark) 0.dp else 5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (profile?.avatarUrl.isNullOrEmpty()) {
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                color = elevatedColor
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = "账号头像",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(34.dp)
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
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 18.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (isLoggedIn) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = VipGold.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "VIP",
                                            color = if (isDark) VipGold else Color(0xFFB87900),
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = { if (isLoggedIn) onAccounts() else onLogin() },
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = elevatedColor,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isLoggedIn) "账号管理" else "立即登录",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. VIP 概念版领取入口 (无边框饱满圆润悬浮卡)
            item {
                VipBannerCard(
                    state = vip,
                    loggedIn = isLoggedIn,
                    onClick = { showVipCenterSheet = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. 听歌等级与累计时长仪表盘 (无边框饱满悬浮块，深浅色自适应)
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cardColor,
                    shadowElevation = if (isDark) 0.dp else 5.dp,
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
                                color = ConceptPrimary.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Lv.${userGrade.level}",
                                    color = ConceptPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "听歌等级与时长",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp
                                    )
                                )
                                Text(
                                    text = "累计时长 ${userGrade.displayListenTimeText}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

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
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            // 4. 核心快捷卡片 (我喜欢的音乐 + 最近播放，深浅色自适应柔和渐变)
            item {
                val likeGradient = if (isDark) {
                    listOf(Color(0xFF2E1922), Color(0xFF1B1724))
                } else {
                    listOf(Color(0xFFFFE5EC), Color(0xFFFFF0F4))
                }
                val historyGradient = if (isDark) {
                    listOf(Color(0xFF152538), Color(0xFF171926))
                } else {
                    listOf(Color(0xFFDFF0FF), Color(0xFFEEF6FF))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MediaShortcutCard(
                        title = "我喜欢的音乐",
                        subtitle = "${likedPlaylist?.trackCount ?: 0} 首歌曲",
                        icon = Icons.Rounded.Favorite,
                        iconTint = Color(0xFFFF5252),
                        gradientColors = likeGradient,
                        onClick = { onOpenPlaylist("like") },
                        modifier = Modifier.weight(1f)
                    )

                    MediaShortcutCard(
                        title = "最近播放",
                        subtitle = "${historyCount} 首记录",
                        icon = Icons.Rounded.History,
                        iconTint = ConceptPrimary,
                        gradientColors = historyGradient,
                        onClick = { onOpenPlaylist("history") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 5. 歌单与音乐资产分组栏
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
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) ConceptPrimary.copy(alpha = 0.18f) else cardColor,
                                    shadowElevation = if (!isDark && !isSelected) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            viewModel.setSelectedAssetTab(idx)
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
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
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
                                    onClick = { viewModel.toggleGridView() },
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
                                    viewModel.refreshFollowedArtists()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "刷新",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 6. 音乐库内容列表 (自建歌单 / 收藏歌单 / 关注歌手 / 已购资产)
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
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                    // 关注歌手列表（对接官方 /v4/follow_list?plat=1）
                    if (followedArtists.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isLoggedIn) "暂未获取到已关注的歌手" else "请先登录同步关注的歌手",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                if (isLoggedIn) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    FilledTonalButton(
                                        onClick = { viewModel.refreshFollowedArtists() },
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("重新同步关注列表", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        items(followedArtists, key = { "artist_${it.id}_${it.name}" }) { artist ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = cardColor,
                                shadowElevation = if (isDark) 0.dp else 3.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        if (artist.id.isNotBlank()) onOpenArtist(artist.id)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (artist.avatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = artist.avatarUrl,
                                            contentDescription = artist.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(elevatedColor)
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(52.dp),
                                            shape = CircleShape,
                                            color = elevatedColor
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
                                        }.joinToString(" · ").ifBlank { "已关注歌手 · 点击进入歌手主页" }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = subText,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // 已购专辑与单曲列表
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
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    } else {
                        if (purchasedAlbums.isNotEmpty()) {
                            item {
                                Text(
                                    text = "已购专辑 (${purchasedAlbums.size})",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                            }
                            items(purchasedAlbums, key = { "album_${it.albumId}" }) { album ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = cardColor,
                                    shadowElevation = if (isDark) 0.dp else 3.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            if (album.albumId.isNotBlank()) onOpenAlbum(album.albumId)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = album.coverUrl,
                                            contentDescription = album.albumName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(54.dp)
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
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val countSuffix = if (album.songCount > 0) " (${album.songCount}首)" else ""
                                            Text(
                                                text = "${album.singerName} · 数字专辑$countSuffix",
                                                color = if (isDark) VipGold else Color(0xFFB87900),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (purchasedSongs.isNotEmpty()) {
                            item {
                                Text(
                                    text = "已购单曲 (${purchasedSongs.size})",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                )
                            }
                            val allPurchasedSongs = purchasedSongs.mapNotNull { it.song }
                            items(purchasedSongs, key = { "song_${it.songId}_${it.songName}" }) { item ->
                                val s = item.song
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = cardColor,
                                    shadowElevation = if (isDark) 0.dp else 3.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            s?.let { songObj ->
                                                onPlaySong(songObj, allPurchasedSongs.ifEmpty { listOf(songObj) })
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!s?.coverUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = s?.coverUrl,
                                                contentDescription = item.songName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(elevatedColor)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
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
                                        Spacer(modifier = Modifier.width(12.dp))
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

        // 7. 固定沉浸式顶栏 (深色/浅色主题自适应背景与文字图标颜色)
        val topBarSurfaceColor = if (isDark) {
            Color(0xFF0A0F1A).copy(alpha = (scrollFraction * 0.96f).coerceIn(0f, 0.96f))
        } else {
            Color(0xFFF3F8FF).copy(alpha = (scrollFraction * 0.96f).coerceIn(0f, 0.96f))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = topBarSurfaceColor,
            tonalElevation = (scrollFraction * 4).dp
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
                            modifier = Modifier
                                .alpha((1f - scrollFraction * 1.5f).coerceIn(0f, 1f))
                                .offset(y = (-10 * scrollFraction).dp)
                        )

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

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        showCreateDialog = false
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
                    onClick = {
                        viewModel.importExternalPlaylist(importPlaylistName, importRawText) { ok, msg ->
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
    val isDark = LocalIsDarkTheme.current
    val titleColor = if (isDark) Color.White else Color(0xFF101C2E)
    val subColor = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF4F6482)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = if (isDark) 0.dp else 4.dp,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
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
