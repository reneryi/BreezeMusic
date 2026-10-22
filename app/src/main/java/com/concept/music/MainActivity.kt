package com.concept.music

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.concept.music.data.kugou.AuthState
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import com.concept.music.service.SystemLyricBridge
import com.concept.music.ui.account.AccountsScreen
import com.concept.music.ui.account.LoginScreen
import com.concept.music.ui.account.VerificationDialog
import com.concept.music.ui.comment.CommentsScreen
import com.concept.music.ui.components.AddToPlaylistDialog
import com.concept.music.ui.components.CreatePlaylistDialog
import com.concept.music.ui.components.FloatingLyricOverlay
import com.concept.music.ui.components.MiniPlayerBar
import com.concept.music.ui.components.NowPlayingSheet
import com.concept.music.ui.components.QueueBottomSheet
import com.concept.music.ui.components.SongActionBottomSheet
import com.concept.music.ui.components.SongDetailSheet
import com.concept.music.ui.components.VipCenterScreen
import com.concept.music.ui.components.ViperSoundEffectScreen
import com.concept.music.ui.home.DailyRecommendScreen
import com.concept.music.ui.home.HomeScreen
import com.concept.music.ui.home.NewSongExpressScreen
import com.concept.music.ui.home.RadioScreen
import com.concept.music.ui.home.RankDetailScreen
import com.concept.music.ui.library.AlbumDetailScreen
import com.concept.music.ui.library.ArtistDetailScreen
import com.concept.music.ui.library.LibraryScreen
import com.concept.music.ui.library.PlaylistDetailScreen
import com.concept.music.ui.navigation.BottomNavBar
import com.concept.music.ui.navigation.Screen
import com.concept.music.ui.search.SearchScreen
import com.concept.music.ui.settings.SettingsScreen
import com.concept.music.ui.theme.ConceptMusicTheme
import com.concept.music.ui.viewmodel.HomeViewModel
import com.concept.music.ui.viewmodel.LibraryViewModel
import com.concept.music.ui.viewmodel.MusicPlayerViewModel
import com.concept.music.ui.viewmodel.SearchViewModel

// 高帧率 120Hz 丝滑五次方减速曲线（消除长尾弹簧导致的双页面同时驻留重绘掉帧，起速灵敏、落点轻盈）
private val FluidPageEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighRefreshRateDisplayMode()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        setContent {
            ConceptMusicTheme {
                MainContent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighRefreshRateDisplayMode()
        SystemLyricBridge.onHostResumed(this)
    }

    /**
     * 主动申请屏幕支持的最高物理刷新率档位（120Hz / 144Hz）
     */
    private fun requestHighRefreshRateDisplayMode() {
        runCatching {
            val currentDisplay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return
            val modes = currentDisplay.supportedModes
            val currentMode = currentDisplay.mode
            val bestMode = modes
                .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?: modes.maxByOrNull { it.refreshRate }
            if (bestMode != null && bestMode.refreshRate >= 90f) {
                val params = window.attributes
                if (params.preferredDisplayModeId != bestMode.modeId || params.preferredRefreshRate != bestMode.refreshRate) {
                    params.preferredDisplayModeId = bestMode.modeId
                    params.preferredRefreshRate = bestMode.refreshRate
                    window.attributes = params
                }
            }
        }
    }
}

@Composable
fun MainContent(
    musicPlayerViewModel: MusicPlayerViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    searchViewModel: SearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Discover.route

    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            "login" -> libraryViewModel.onLoginRoute(true)
            "accounts", Screen.Library.route -> libraryViewModel.onLoginRoute(false)
        }
    }

    // 关键性能优化：通过 derivedStateOf 在根组件 MainContent 层仅订阅低频状态（当前曲目、播放状态、错误提示），
    // 既保证点击播放瞬间状态 0ms 同步，又彻底隔离每 120ms 变化的 currentPositionMs，杜绝引发 NavHost 重绘卡顿
    val playerStateHolder = musicPlayerViewModel.uiState.collectAsState()
    val currentSong by remember(playerStateHolder) {
        derivedStateOf { playerStateHolder.value.currentSong }
    }
    val isPlaying by remember(playerStateHolder) {
        derivedStateOf { playerStateHolder.value.isPlaying }
    }
    val playerError by remember(playerStateHolder) {
        derivedStateOf { playerStateHolder.value.error }
    }

    val likedSongs by libraryViewModel.likedSongs.collectAsState()
    val playlists by libraryViewModel.playlists.collectAsState()
    val followedArtists by libraryViewModel.followedArtists.collectAsState()

    var showNowPlaying by remember { mutableStateOf(false) }
    var showGlobalQueueSheet by remember { mutableStateOf(false) }
    var showCommentsScreen by remember { mutableStateOf(false) }
    var showViperEffectOverlay by remember { mutableStateOf(false) }

    var actionSheetSong by remember { mutableStateOf<Song?>(null) }
    var actionSheetRemoveCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistFromGlobal by remember { mutableStateOf(false) }
    var detailSheetSong by remember { mutableStateOf<Song?>(null) }
    val mainScope = rememberCoroutineScope()

    val navigateToSongArtist: (Song) -> Unit = { targetSong ->
        showNowPlaying = false
        val directId = targetSong.artistId.trim()
        if (directId.isNotBlank() && directId != "0") {
            navController.navigate("artist_detail/$directId") { launchSingleTop = true }
        } else {
            val cleanArtist = targetSong.artist
                .split("、", "/", "&", "feat.", "Feat.", ",")
                .firstOrNull()
                ?.trim()
                .orEmpty()
            if (cleanArtist.isBlank()) {
                Toast.makeText(context, "未获取到歌手信息", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "正在进入「$cleanArtist」主页...", Toast.LENGTH_SHORT).show()
                mainScope.launch {
                    val resolvedId = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            VipManager.music.searchArtists(cleanArtist, page = 1, pageSize = 5)
                                .firstOrNull { it.id.isNotBlank() && it.id != "0" }
                                ?.id
                        }.getOrNull()
                    }
                    if (!resolvedId.isNullOrBlank()) {
                        navController.navigate("artist_detail/$resolvedId") { launchSingleTop = true }
                    } else {
                        Toast.makeText(context, "未找到「$cleanArtist」的歌手主页", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val navigateToSongAlbum: (Song) -> Unit = { targetSong ->
        showNowPlaying = false
        val directAlbumId = targetSong.albumId.trim()
        if (directAlbumId.isNotBlank() && directAlbumId != "0") {
            navController.navigate("album_detail/$directAlbumId") { launchSingleTop = true }
        } else {
            val kw = "${targetSong.album.ifBlank { targetSong.title }} ${targetSong.artist}".trim()
            Toast.makeText(context, "正在定位所属专辑...", Toast.LENGTH_SHORT).show()
            mainScope.launch {
                val resolvedAlbumId = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val fromSearch = VipManager.music.searchSongs(
                            "${targetSong.title} ${targetSong.artist}".trim(),
                            page = 1,
                            pageSize = 6
                        ).firstOrNull { it.albumId.isNotBlank() && it.albumId != "0" }?.albumId
                        if (!fromSearch.isNullOrBlank()) {
                            fromSearch
                        } else {
                            VipManager.music.searchAlbums(kw, page = 1, pageSize = 5)
                                .firstOrNull { it.id.isNotBlank() && it.id != "0" }
                                ?.id
                        }
                    }.getOrNull()
                }
                if (!resolvedAlbumId.isNullOrBlank()) {
                    navController.navigate("album_detail/$resolvedAlbumId") { launchSingleTop = true }
                } else {
                    Toast.makeText(context, "该曲目暂无关联专辑详情", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) libraryViewModel.onForeground(true)
            if (event == Lifecycle.Event.ON_PAUSE) libraryViewModel.onForeground(false)
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) libraryViewModel.onForeground(true)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val verification by libraryViewModel.pendingVerification.collectAsState()
    verification?.let { request ->
        VerificationDialog(request) { libraryViewModel.verificationAnswer(request.id, it) }
    }

    val openLogin: () -> Unit = {
        libraryViewModel.openLogin()
        navController.navigate("login") { launchSingleTop = true }
    }

    val isSubDetail = currentRoute.startsWith("playlist_detail") ||
        currentRoute.startsWith("album_detail") ||
        currentRoute.startsWith("artist_detail") ||
        currentRoute == "daily_recommend" ||
        currentRoute == "new_song_express" ||
        currentRoute.startsWith("rank_detail") ||
        currentRoute.startsWith("radio_station") ||
        currentRoute == "search" ||
        currentRoute == "settings" ||
        currentRoute == "vip_center" ||
        currentRoute == "viper_effect"

    val showFloatingMiniBar = currentRoute != "login" &&
        currentRoute != "accounts" &&
        currentRoute != "settings" &&
        currentRoute != "vip_center" &&
        currentRoute != "viper_effect"

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Discover.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                enterTransition = {
                    val initial = initialState.destination.route.orEmpty()
                    val target = targetState.destination.route.orEmpty()
                    val bothMainTabs = (initial == Screen.Discover.route || initial == Screen.Library.route) &&
                        (target == Screen.Discover.route || target == Screen.Library.route)
                    if (bothMainTabs) {
                        val dir = if (target == Screen.Library.route) 1 else -1
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> dir * (fullWidth / 8) },
                            animationSpec = tween(220, easing = FluidPageEasing)
                        ) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing))
                    } else {
                        // 120Hz 高帧率丝滑入场：精简为 GPU 友好的水平流体推进 + 微缩放渐显，消除重度复合弹簧引起的过渡掉帧
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> (fullWidth * 0.20f).toInt() },
                            animationSpec = tween(250, easing = FluidPageEasing)
                        ) + scaleIn(
                            initialScale = 0.96f,
                            transformOrigin = TransformOrigin(0.5f, 0.5f),
                            animationSpec = tween(250, easing = FluidPageEasing)
                        ) + fadeIn(animationSpec = tween(190, easing = FastOutSlowInEasing))
                    }
                },
                exitTransition = {
                    val initial = initialState.destination.route.orEmpty()
                    val target = targetState.destination.route.orEmpty()
                    val bothMainTabs = (initial == Screen.Discover.route || initial == Screen.Library.route) &&
                        (target == Screen.Discover.route || target == Screen.Library.route)
                    if (bothMainTabs) {
                        val dir = if (target == Screen.Library.route) 1 else -1
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -dir * (fullWidth / 10) },
                            animationSpec = tween(180, easing = FluidPageEasing)
                        ) + fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
                    } else {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -(fullWidth * 0.07f).toInt() },
                            animationSpec = tween(240, easing = FluidPageEasing)
                        ) + scaleOut(
                            targetScale = 0.96f,
                            transformOrigin = TransformOrigin(0.5f, 0.5f),
                            animationSpec = tween(240, easing = FluidPageEasing)
                        ) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                    }
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -(fullWidth * 0.07f).toInt() },
                        animationSpec = tween(240, easing = FluidPageEasing)
                    ) + scaleIn(
                        initialScale = 0.96f,
                        transformOrigin = TransformOrigin(0.5f, 0.5f),
                        animationSpec = tween(240, easing = FluidPageEasing)
                    ) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> (fullWidth * 0.22f).toInt() },
                        animationSpec = tween(230, easing = FluidPageEasing)
                    ) + scaleOut(
                        targetScale = 0.95f,
                        transformOrigin = TransformOrigin(0.5f, 0.5f),
                        animationSpec = tween(230, easing = FluidPageEasing)
                    ) + fadeOut(animationSpec = tween(170, easing = FastOutSlowInEasing))
                }
            ) {
                // 发现页
                composable(Screen.Discover.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onLogin = openLogin,
                        onSongClick = { song, list ->
                            musicPlayerViewModel.playSong(song, list)
                        },
                        onSearchClick = {
                            searchViewModel.resetTabToDefault()
                            navController.navigate("search") { launchSingleTop = true }
                        },
                        onOpenDailyRecommend = {
                            navController.navigate("daily_recommend") { launchSingleTop = true }
                        },
                        onOpenNewSongExpress = {
                            navController.navigate("new_song_express") { launchSingleTop = true }
                        },
                        onOpenRank = { rankId ->
                            navController.navigate("rank_detail/$rankId") { launchSingleTop = true }
                        },
                        onOpenRadio = { channelId ->
                            val target = channelId.ifBlank { "all" }
                            navController.navigate("radio_station/$target") { launchSingleTop = true }
                        },
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onTogglePlayPause = { musicPlayerViewModel.togglePlayPause() },
                        onSongMoreClick = { song ->
                            actionSheetSong = song
                            actionSheetRemoveCallback = null
                        }
                    )
                }

                // 每日推荐独立页
                composable("daily_recommend") {
                    DailyRecommendScreen(
                        viewModel = homeViewModel,
                        onBack = { navController.popBackStack() },
                        onSongClick = { song, list ->
                            musicPlayerViewModel.playSong(song, list)
                        },
                        currentSong = currentSong,
                        onSongMoreClick = { song ->
                            actionSheetSong = song
                            actionSheetRemoveCallback = null
                        }
                    )
                }

                // 概念新歌速递独立页
                composable("new_song_express") {
                    NewSongExpressScreen(
                        viewModel = homeViewModel,
                        onBack = { navController.popBackStack() },
                        onSongClick = { song, list ->
                            musicPlayerViewModel.playSong(song, list)
                        },
                        currentSong = currentSong,
                        onSongMoreClick = { song ->
                            actionSheetSong = song
                            actionSheetRemoveCallback = null
                        }
                    )
                }

                // 巅峰排行榜独立页
                composable("rank_detail/{rankId}") { backStackEntry ->
                    val rankId = backStackEntry.arguments?.getString("rankId")?.toIntOrNull() ?: 6666
                    RankDetailScreen(
                        viewModel = homeViewModel,
                        initialRankId = rankId,
                        onBack = { navController.popBackStack() },
                        onSongClick = { song, list ->
                            musicPlayerViewModel.playSong(song, list)
                        },
                        currentSong = currentSong,
                        onSongMoreClick = { song ->
                            actionSheetSong = song
                            actionSheetRemoveCallback = null
                        }
                    )
                }

                // 精选场景电台专区独立页
                composable("radio_station/{channelId}") { backStackEntry ->
                    val channelId = backStackEntry.arguments?.getString("channelId") ?: "all"
                    RadioScreen(
                        viewModel = homeViewModel,
                        initialChannelId = channelId,
                        onBack = { navController.popBackStack() },
                        onSongClick = { song, list ->
                            musicPlayerViewModel.playSong(song, list)
                        },
                        currentSong = currentSong,
                        onSongMoreClick = { song ->
                            actionSheetSong = song
                            actionSheetRemoveCallback = null
                        }
                    )
                }

                // 我的页
                composable(Screen.Library.route) {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onLogin = openLogin,
                        onAccounts = { navController.navigate("accounts") { launchSingleTop = true } },
                        onOpenPlaylist = { playlistId ->
                            navController.navigate("playlist_detail/$playlistId") { launchSingleTop = true }
                        },
                        onOpenSearch = {
                            searchViewModel.resetTabToDefault()
                            navController.navigate("search") { launchSingleTop = true }
                        },
                        onOpenSettings = {
                            navController.navigate("settings") { launchSingleTop = true }
                        },
                        onOpenVipCenter = {
                            navController.navigate("vip_center") { launchSingleTop = true }
                        },
                        onOpenArtist = { artistId ->
                            navController.navigate("artist_detail/$artistId") { launchSingleTop = true }
                        },
                        onOpenAlbum = { albumId ->
                            navController.navigate("album_detail/$albumId") { launchSingleTop = true }
                        },
                        onPlaySong = { song, list ->
                            musicPlayerViewModel.playSong(song, list)
                        }
                    )
                }

                // VIP 权益中心独立页面
                composable("vip_center") {
                    val account by libraryViewModel.accountState.collectAsState()
                    val vip by libraryViewModel.vipState.collectAsState()
                    val isLoggedIn = account.auth == AuthState.READY
                    VipCenterScreen(
                        state = vip,
                        loggedIn = isLoggedIn,
                        autoClaim = account.current?.autoClaim ?: false,
                        onBack = { navController.popBackStack() },
                        onClaim = { libraryViewModel.claimVip() },
                        onAutoChange = { libraryViewModel.setAuto(it) },
                        onRefresh = { libraryViewModel.refreshAccount() },
                        onLogin = openLogin
                    )
                }

                // 设置与播放偏好中心
                composable("settings") {
                    SettingsScreen(
                        viewModel = musicPlayerViewModel,
                        onBackClick = { navController.popBackStack() },
                        onOpenViperEffect = {
                            navController.navigate("viper_effect") { launchSingleTop = true }
                        }
                    )
                }

                // 蝰蛇音效与专业均衡器独立页面路由
                composable("viper_effect") {
                    ViperSoundEffectScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 歌单详情页
                composable("playlist_detail/{playlistId}") { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: "like"
                    PlaylistDetailScreen(
                        viewModel = libraryViewModel,
                        playlistId = playlistId,
                        onBack = { navController.popBackStack() },
                        onSongClick = { song, list ->
                            musicPlayerViewModel.playSong(song, list)
                        },
                        currentSong = currentSong,
                        onSongMoreClick = { song, removeCallback ->
                            actionSheetSong = song
                            actionSheetRemoveCallback = removeCallback
                        }
                    )
                }

                // 专辑详情页
                composable("album_detail/{albumId}") { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
                    AlbumDetailScreen(
                        albumId = albumId,
                        onBack = { navController.popBackStack() },
                        onSongClick = { song, list ->
                            musicPlayerViewModel.playSong(song, list)
                        },
                        currentSong = currentSong,
                        onSongMoreClick = { song ->
                            actionSheetSong = song
                            actionSheetRemoveCallback = null
                        }
                    )
                }

                // 歌手详情页
                composable("artist_detail/{artistId}") { backStackEntry ->
                    val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
                    val isArtistFollowed = libraryViewModel.isArtistFollowed(artistId, followedArtists)
                    ArtistDetailScreen(
                        artistId = artistId,
                        onBack = { navController.popBackStack() },
                        onSongClick = { song, list ->
                            musicPlayerViewModel.playSong(song, list)
                        },
                        onAlbumClick = { albumId ->
                            navController.navigate("album_detail/$albumId") { launchSingleTop = true }
                        },
                        currentSong = currentSong,
                        onSongMoreClick = { song ->
                            actionSheetSong = song
                            actionSheetRemoveCallback = null
                        },
                        isFollowed = isArtistFollowed,
                        onToggleFollow = { artistItem ->
                            libraryViewModel.toggleFollowArtist(artistItem) { _, _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // 搜索页
                composable("search") {
                    SearchScreen(
                        viewModel = searchViewModel,
                        onBack = { navController.popBackStack() },
                        onSongClick = { song, list ->
                            musicPlayerViewModel.playSong(song, list)
                            showNowPlaying = true
                        },
                        onAlbumClick = { albumId ->
                            navController.navigate("album_detail/$albumId") { launchSingleTop = true }
                        },
                        onPlaylistClick = { playlistId ->
                            navController.navigate("playlist_detail/$playlistId") { launchSingleTop = true }
                        },
                        onArtistClick = { artistId ->
                            navController.navigate("artist_detail/$artistId") { launchSingleTop = true }
                        },
                        currentSong = currentSong,
                        onSongMoreClick = { song ->
                            actionSheetSong = song
                            actionSheetRemoveCallback = null
                        }
                    )
                }

                // 登录与账号管理
                composable("login") {
                    LoginScreen(libraryViewModel, onBack = { navController.popBackStack() })
                }
                composable("accounts") {
                    AccountsScreen(libraryViewModel, onBack = { navController.popBackStack() }, onLogin = openLogin)
                }

                // 独立全屏评论页面路由
                composable("comments") {
                    val fullState by musicPlayerViewModel.uiState.collectAsState()
                    val userAvatar = VipManager.accounts.state.value.profile?.avatarUrl ?: ""
                    CommentsScreen(
                        song = fullState.currentSong,
                        comments = fullState.comments,
                        totalCount = fullState.commentCount,
                        loading = fullState.commentsLoading,
                        currentType = fullState.commentType,
                        hasMore = fullState.commentsHasMore,
                        currentUserAvatar = userAvatar,
                        onBack = { navController.popBackStack() },
                        onTabSelected = { musicPlayerViewModel.setCommentType(it) },
                        onLoadMore = { musicPlayerViewModel.loadComments(isRefresh = false) },
                        onRefresh = { musicPlayerViewModel.loadComments(isRefresh = true) },
                        onLoadReplies = { musicPlayerViewModel.loadCommentReplies(it) },
                        onPostComment = { content, replyId, replyUser ->
                            musicPlayerViewModel.postComment(content, replyId, replyUser)
                        },
                        onLikeComment = { musicPlayerViewModel.likeComment(it) }
                    )
                }
            }
        }

        // 底部「发现 / 我的」主导航栏
        AnimatedVisibility(
            visible = showFloatingMiniBar && !isSubDetail && !showNowPlaying && !showViperEffectOverlay,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(180, easing = FluidPageEasing)
            ) + fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
        ) {
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }

        // 独立圆角长条形悬浮控制条（隔离高频进度状态订阅，仅重绘胶囊自身）
        val miniBarBottomPadding by animateDpAsState(
            targetValue = if (isSubDetail) 10.dp else 66.dp,
            animationSpec = spring(
                dampingRatio = 0.80f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "miniBarBottomPadding"
        )

        if (showFloatingMiniBar) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = miniBarBottomPadding)
            ) {
                playerError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                AnimatedVisibility(
                    visible = currentSong != null && !showNowPlaying && !showViperEffectOverlay,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(tween(200)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it / 2 },
                        animationSpec = tween(170)
                    ) + fadeOut(tween(150)) + scaleOut(targetScale = 0.95f, animationSpec = tween(170))
                ) {
                    IsolatedMiniPlayerBarHost(
                        viewModel = musicPlayerViewModel,
                        onBarClick = { showNowPlaying = true },
                        onQueueClick = { showGlobalQueueSheet = true }
                    )
                }
            }
        }

        // 从底部悬浮控制条点击队列按钮直接弹出的播放队列抽屉
        if (showGlobalQueueSheet && !showNowPlaying) {
            val playerUiState by musicPlayerViewModel.uiState.collectAsState()
            QueueBottomSheet(
                queue = playerUiState.playlistQueue,
                currentIndex = playerUiState.currentIndex,
                recentQueues = playerUiState.recentQueues,
                activeQueueId = playerUiState.activeQueueId,
                playMode = playerUiState.playMode,
                climaxModeEnabled = playerUiState.climaxModeEnabled,
                onToggleClimaxMode = { musicPlayerViewModel.toggleClimaxMode() },
                onCyclePlayMode = { musicPlayerViewModel.cyclePlayMode() },
                onSwitchRecentQueue = { musicPlayerViewModel.switchToRecentQueue(it) },
                onDeleteRecentQueue = { musicPlayerViewModel.deleteRecentQueue(it) },
                onDismiss = { showGlobalQueueSheet = false },
                onSongClick = { song ->
                    musicPlayerViewModel.playSong(song, playerUiState.playlistQueue)
                    showGlobalQueueSheet = false
                },
                onRemoveSong = { musicPlayerViewModel.removeFromQueue(it) },
                onClear = { musicPlayerViewModel.clearQueue() },
                onReorderQueueByMood = { mode -> musicPlayerViewModel.reorderQueueByMood(mode) },
                onMoveQueueItem = { fromIdx, toIdx -> musicPlayerViewModel.moveQueueItem(fromIdx, toIdx) }
            )
        }

        // 沉浸式全屏 Edge-to-Edge 播放器
        AnimatedVisibility(
            visible = showNowPlaying && currentSong != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(270, easing = FluidPageEasing)
            ) + scaleIn(
                initialScale = 0.94f,
                transformOrigin = TransformOrigin(0.5f, 0.92f),
                animationSpec = tween(270, easing = FluidPageEasing)
            ) + fadeIn(tween(200, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(240, easing = FluidPageEasing)
            ) + scaleOut(
                targetScale = 0.94f,
                transformOrigin = TransformOrigin(0.5f, 0.92f),
                animationSpec = tween(240, easing = FluidPageEasing)
            ) + fadeOut(tween(180, easing = FastOutSlowInEasing))
        ) {
            IsolatedNowPlayingHost(
                musicPlayerViewModel = musicPlayerViewModel,
                libraryViewModel = libraryViewModel,
                likedSongs = likedSongs,
                onDismiss = { showNowPlaying = false },
                onAddToPlaylist = { songToAddToPlaylist = it },
                onOpenComments = {
                    musicPlayerViewModel.loadComments(isRefresh = true)
                    showCommentsScreen = true
                },
                onOpenViperEffectPage = {
                    showViperEffectOverlay = true
                },
                onOpenArtist = navigateToSongArtist,
                onOpenAlbum = navigateToSongAlbum
            )
        }

        // 从播放页进入的独立全屏「蝰蛇音效与专业均衡器」页面（丝滑侧滑叠加于播放页之上，返回直达播放页）
        AnimatedVisibility(
            visible = showViperEffectOverlay,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(250, easing = FluidPageEasing)
            ) + fadeIn(tween(190)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(230, easing = FluidPageEasing)
            ) + fadeOut(tween(170))
        ) {
            ViperSoundEffectScreen(
                onBack = { showViperEffectOverlay = false }
            )
        }

        AnimatedVisibility(
            visible = showCommentsScreen && currentSong != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(250, easing = FluidPageEasing)
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(220, easing = FluidPageEasing)
            ) + fadeOut(tween(160))
        ) {
            val playerUiState by musicPlayerViewModel.uiState.collectAsState()
            val userAvatar = VipManager.accounts.state.value.profile?.avatarUrl ?: ""
            CommentsScreen(
                song = playerUiState.currentSong,
                comments = playerUiState.comments,
                totalCount = playerUiState.commentCount,
                loading = playerUiState.commentsLoading,
                currentType = playerUiState.commentType,
                hasMore = playerUiState.commentsHasMore,
                currentUserAvatar = userAvatar,
                onBack = {
                    showCommentsScreen = false
                },
                onTabSelected = { musicPlayerViewModel.setCommentType(it) },
                onLoadMore = { musicPlayerViewModel.loadComments(isRefresh = false) },
                onRefresh = { musicPlayerViewModel.loadComments(isRefresh = true) },
                onLoadReplies = { musicPlayerViewModel.loadCommentReplies(it) },
                onPostComment = { content, replyId, replyUser ->
                    musicPlayerViewModel.postComment(content, replyId, replyUser)
                },
                onLikeComment = { musicPlayerViewModel.likeComment(it) }
            )
        }

        if (actionSheetSong != null) {
            val targetSong = actionSheetSong!!
            val isTargetLiked = libraryViewModel.isSongLiked(targetSong, likedSongs)
            SongActionBottomSheet(
                song = targetSong,
                isFavorite = isTargetLiked,
                onDismiss = {
                    actionSheetSong = null
                    actionSheetRemoveCallback = null
                },
                onPlayNow = {
                    musicPlayerViewModel.playSong(targetSong, listOf(targetSong))
                },
                onPlayNext = {
                    musicPlayerViewModel.playNext(targetSong)
                    Toast.makeText(context, "已加入下一首播放：${targetSong.title}", Toast.LENGTH_SHORT).show()
                },
                onToggleFavorite = {
                    libraryViewModel.toggleFavorite(targetSong) { _, _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onAddToPlaylist = {
                    songToAddToPlaylist = targetSong
                },
                onOpenComments = {
                    if (currentSong?.id != targetSong.id) {
                        musicPlayerViewModel.playSong(targetSong, listOf(targetSong))
                    } else {
                        musicPlayerViewModel.loadComments(isRefresh = true)
                    }
                    showCommentsScreen = true
                },
                onOpenArtist = if (targetSong.artist.isNotBlank()) {
                    { navigateToSongArtist(targetSong) }
                } else null,
                onOpenAlbum = { navigateToSongAlbum(targetSong) },
                onOpenSongDetail = {
                    detailSheetSong = targetSong
                },
                onRemoveFromCurrentPlaylist = actionSheetRemoveCallback
            )
        }

        if (songToAddToPlaylist != null) {
            val targetSong = songToAddToPlaylist!!
            AddToPlaylistDialog(
                song = targetSong,
                playlists = playlists,
                onSelectPlaylist = { targetPl ->
                    songToAddToPlaylist = null
                    libraryViewModel.addSongToPlaylist(targetPl.listId, targetSong, targetPl.title) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onCreateNewPlaylist = {
                    showCreatePlaylistFromGlobal = true
                },
                onDismiss = { songToAddToPlaylist = null }
            )
        }

        if (showCreatePlaylistFromGlobal) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistFromGlobal = false },
                onConfirm = { name ->
                    libraryViewModel.createPlaylist(name) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            showCreatePlaylistFromGlobal = false
                        }
                    }
                }
            )
        }

        if (detailSheetSong != null) {
            val detail = remember(detailSheetSong) {
                VipManager.music.songDetail(detailSheetSong!!)
            }
            SongDetailSheet(
                detail = detail,
                onDismiss = { detailSheetSong = null }
            )
        }

        // 独立隔离状态订阅的「桌面歌词」宿主
        IsolatedDesktopLyricHost(
            viewModel = musicPlayerViewModel,
            visible = !showViperEffectOverlay,
            onOpenPlayer = { showNowPlaying = true }
        )
    }
}

@Composable
private fun IsolatedMiniPlayerBarHost(
    viewModel: MusicPlayerViewModel,
    onBarClick: () -> Unit,
    onQueueClick: () -> Unit
) {
    val playerUiState by viewModel.uiState.collectAsState()
    MiniPlayerBar(
        currentSong = playerUiState.currentSong,
        isPlaying = playerUiState.isPlaying,
        currentPositionMs = playerUiState.currentPositionMs,
        durationMs = playerUiState.durationMs,
        onTogglePlay = { viewModel.togglePlayPause() },
        onNextClick = { viewModel.skipToNext() },
        onPreviousClick = { viewModel.skipToPrevious() },
        onBarClick = onBarClick,
        onQueueClick = onQueueClick
    )
}

@Composable
private fun IsolatedNowPlayingHost(
    musicPlayerViewModel: MusicPlayerViewModel,
    libraryViewModel: LibraryViewModel,
    likedSongs: List<Song>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onOpenComments: () -> Unit,
    onOpenViperEffectPage: () -> Unit,
    onOpenArtist: (Song) -> Unit = {},
    onOpenAlbum: (Song) -> Unit = {}
) {
    val context = LocalContext.current
    val playerUiState by musicPlayerViewModel.uiState.collectAsState()
    val currentSong = playerUiState.currentSong ?: return
    val isCurrentSongLiked = libraryViewModel.isSongLiked(currentSong, likedSongs)

    NowPlayingSheet(
        song = currentSong,
        isPlaying = playerUiState.isPlaying,
        currentPositionMs = playerUiState.currentPositionMs,
        durationMs = playerUiState.durationMs,
        playMode = playerUiState.playMode,
        currentQualityLabel = playerUiState.currentQualityLabel,
        lyricData = playerUiState.currentLyric,
        currentLyricIndex = playerUiState.currentLyricIndex,
        queue = playerUiState.playlistQueue,
        currentIndex = playerUiState.currentIndex,
        recentQueues = playerUiState.recentQueues,
        activeQueueId = playerUiState.activeQueueId,
        onSwitchRecentQueue = { musicPlayerViewModel.switchToRecentQueue(it) },
        onDeleteRecentQueue = { musicPlayerViewModel.deleteRecentQueue(it) },
        coverStyle = playerUiState.coverStyle,
        playerBackgroundStyle = playerUiState.playerBackgroundStyle,
        dynamicCoverAccentEnabled = playerUiState.dynamicCoverAccentEnabled,
        onSelectCoverStyle = { musicPlayerViewModel.setCoverStyle(it) },
        onSelectPlayerBackgroundStyle = { musicPlayerViewModel.setPlayerBackgroundStyle(it) },
        onSetDynamicCoverAccentEnabled = { musicPlayerViewModel.setDynamicCoverAccentEnabled(it) },
        showTranslation = playerUiState.showTranslation,
        showPhonetic = playerUiState.showPhonetic,
        onToggleTranslation = { musicPlayerViewModel.toggleTranslation() },
        onTogglePhonetic = { musicPlayerViewModel.togglePhonetic() },
        lyricFontSize = playerUiState.lyricFontSize,
        lyricTranslationScale = playerUiState.lyricTranslationScale,
        lyricAlignment = playerUiState.lyricAlignment,
        lyricWordByWord = playerUiState.lyricWordByWord,
        lyricBlurEnabled = playerUiState.lyricBlurEnabled,
        lyricBlurIntensity = playerUiState.lyricBlurIntensity,
        lyricLineSpacing = playerUiState.lyricLineSpacing,
        lyricOffsetMs = playerUiState.lyricOffsetMs,
        lyricHighlightTheme = playerUiState.lyricHighlightTheme,
        lyricBackgroundDim = playerUiState.lyricBackgroundDim,
        lyricKeepScreenOn = playerUiState.lyricKeepScreenOn,
        commentCount = playerUiState.commentCount,
        playbackSpeed = playerUiState.playbackSpeed,
        playbackPitch = playerUiState.playbackPitch,
        linkPitchToSpeed = playerUiState.linkPitchToSpeed,
        sleepTimerRemainingSec = playerUiState.sleepTimerRemainingSec,
        sleepStopAfterCurrentSong = playerUiState.sleepStopAfterCurrentSong,
        sleepPendingStopAtTrackEnd = playerUiState.sleepPendingStopAtTrackEnd,
        climaxModeEnabled = playerUiState.climaxModeEnabled,
        climaxUnlockedForCurrentSong = playerUiState.climaxUnlockedForCurrentSong,
        accompanyModeEnabled = playerUiState.accompanyModeEnabled,
        isFavorite = isCurrentSongLiked,
        onToggleFavorite = {
            libraryViewModel.toggleFavorite(currentSong) { _, _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        },
        onAddToPlaylist = {
            onAddToPlaylist(currentSong)
        },
        onSetLyricFontSize = { musicPlayerViewModel.setLyricFontSize(it) },
        onSetLyricTranslationScale = { musicPlayerViewModel.setLyricTranslationScale(it) },
        onSetLyricAlignment = { musicPlayerViewModel.setLyricAlignment(it) },
        onSetLyricWordByWord = { musicPlayerViewModel.setLyricWordByWord(it) },
        onSetLyricBlurEnabled = { musicPlayerViewModel.setLyricBlurEnabled(it) },
        onSetLyricBlurIntensity = { musicPlayerViewModel.setLyricBlurIntensity(it) },
        onSetLyricLineSpacing = { musicPlayerViewModel.setLyricLineSpacing(it) },
        onSetLyricOffsetMs = { musicPlayerViewModel.setLyricOffsetMs(it) },
        onSetLyricHighlightTheme = { musicPlayerViewModel.setLyricHighlightTheme(it) },
        onSetLyricBackgroundDim = { musicPlayerViewModel.setLyricBackgroundDim(it) },
        onSetLyricKeepScreenOn = { musicPlayerViewModel.setLyricKeepScreenOn(it) },
        onOpenComments = onOpenComments,
        onOpenViperEffectPage = onOpenViperEffectPage,
        onSetPlaybackSpeed = { musicPlayerViewModel.setPlaybackSpeed(it) },
        onSetPlaybackPitch = { musicPlayerViewModel.setPlaybackPitch(it) },
        onSetLinkPitchToSpeed = { musicPlayerViewModel.setLinkPitchToSpeed(it) },
        onResetSpeedAndPitch = { musicPlayerViewModel.resetSpeedAndPitch() },
        onSetSleepTimer = { mins, stopAfter -> musicPlayerViewModel.setSleepTimer(mins, stopAfter) },
        onSetSleepStopAfterCurrentSongPref = { musicPlayerViewModel.setSleepStopAfterCurrentSongPreference(it) },
        onCancelSleepTimer = { musicPlayerViewModel.cancelSleepTimer() },
        onToggleClimaxMode = { musicPlayerViewModel.toggleClimaxMode() },
        onUnlockFullTrack = { musicPlayerViewModel.unlockFullTrackForCurrentSong() },
        onToggleAccompanyMode = { musicPlayerViewModel.toggleAccompanyMode() },
        onReorderQueueByMood = { mode -> musicPlayerViewModel.reorderQueueByMood(mode) },
        onMoveQueueItem = { fromIdx, toIdx -> musicPlayerViewModel.moveQueueItem(fromIdx, toIdx) },
        songDetail = playerUiState.songDetail,
        onDismiss = onDismiss,
        onTogglePlay = { musicPlayerViewModel.togglePlayPause() },
        onPrevious = { musicPlayerViewModel.skipToPrevious() },
        onNext = { musicPlayerViewModel.skipToNext() },
        onSeekTo = { musicPlayerViewModel.seekTo(it) },
        onSeekToLyricLine = { musicPlayerViewModel.seekToLyricLine(it) },
        onCyclePlayMode = { musicPlayerViewModel.cyclePlayMode() },
        onSelectQuality = { musicPlayerViewModel.setPreferredQuality(it) },
        onPlayQueueSong = { musicPlayerViewModel.playSong(it, playerUiState.playlistQueue) },
        onRemoveFromQueue = { musicPlayerViewModel.removeFromQueue(it) },
        onClearQueue = { musicPlayerViewModel.clearQueue() },
        onOpenArtist = { onOpenArtist(currentSong) },
        onOpenAlbum = { onOpenAlbum(currentSong) },
        motionOrientationPermissionEnabled = playerUiState.motionOrientationPermissionEnabled
    )
}

@Composable
private fun IsolatedDesktopLyricHost(
    viewModel: MusicPlayerViewModel,
    visible: Boolean,
    onOpenPlayer: () -> Unit
) {
    val playerUiState by viewModel.uiState.collectAsState()
    val adjustedPos = (playerUiState.currentPositionMs + playerUiState.lyricOffsetMs).coerceAtLeast(0L)
    FloatingLyricOverlay(
        song = if (visible) playerUiState.currentSong else null,
        lyricData = playerUiState.currentLyric,
        currentLyricIndex = playerUiState.currentLyricIndex,
        currentPositionMs = adjustedPos,
        isPlaying = playerUiState.isPlaying,
        onTogglePlay = { viewModel.togglePlayPause() },
        onPrevious = { viewModel.skipToPrevious() },
        onNext = { viewModel.skipToNext() },
        onClickCapsule = onOpenPlayer
    )
}
