package com.concept.music

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.account.AccountsScreen
import com.concept.music.ui.account.LoginScreen
import com.concept.music.ui.account.VerificationDialog
import com.concept.music.ui.comment.CommentsScreen
import com.concept.music.ui.components.AddToPlaylistDialog
import com.concept.music.ui.components.CreatePlaylistDialog
import com.concept.music.ui.components.FloatingLyricOverlay
import com.concept.music.ui.components.MiniPlayerBar
import com.concept.music.ui.components.NowPlayingSheet
import com.concept.music.ui.components.SongActionBottomSheet
import com.concept.music.ui.components.SongDetailSheet
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    val playerUiState by musicPlayerViewModel.uiState.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()
    val playlists by libraryViewModel.playlists.collectAsState()
    val followedArtists by libraryViewModel.followedArtists.collectAsState()

    var showNowPlaying by remember { mutableStateOf(false) }
    var showCommentsScreen by remember { mutableStateOf(false) }

    var actionSheetSong by remember { mutableStateOf<Song?>(null) }
    var actionSheetRemoveCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistFromGlobal by remember { mutableStateOf(false) }
    var detailSheetSong by remember { mutableStateOf<Song?>(null) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                val isSubDetail = currentRoute.startsWith("playlist_detail") ||
                        currentRoute.startsWith("album_detail") ||
                        currentRoute.startsWith("artist_detail") ||
                        currentRoute == "daily_recommend" ||
                        currentRoute == "new_song_express" ||
                        currentRoute.startsWith("rank_detail") ||
                        currentRoute.startsWith("radio_station") ||
                        currentRoute == "search" ||
                        currentRoute == "settings"

                if (currentRoute != "login" && currentRoute != "accounts" && currentRoute != "settings") {
                    Column {
                        playerUiState.error?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = playerUiState.currentSong != null && !showNowPlaying,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(220)) + scaleIn(initialScale = 0.94f),
                            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(180)) + scaleOut(targetScale = 1.04f)
                        ) {
                            MiniPlayerBar(
                                currentSong = playerUiState.currentSong,
                                isPlaying = playerUiState.isPlaying,
                                currentPositionMs = playerUiState.currentPositionMs,
                                durationMs = playerUiState.durationMs,
                                onTogglePlay = { musicPlayerViewModel.togglePlayPause() },
                                onNextClick = { musicPlayerViewModel.skipToNext() },
                                onPreviousClick = { musicPlayerViewModel.skipToPrevious() },
                                onBarClick = { showNowPlaying = true },
                                onQueueClick = { showNowPlaying = true },
                                modifier = if (isSubDetail) Modifier.navigationBarsPadding() else Modifier
                            )
                        }

                        if (!isSubDetail) {
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
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Discover.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    val target = targetState.destination.route.orEmpty()
                    val isMainTab = target == Screen.Discover.route || target == Screen.Library.route
                    if (isMainTab) {
                        fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(240, easing = FastOutSlowInEasing))
                    } else {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> (fullWidth * 0.28f).toInt() },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(300, easing = FastOutSlowInEasing))
                    }
                },
                exitTransition = {
                    val target = targetState.destination.route.orEmpty()
                    val isMainTab = target == Screen.Discover.route || target == Screen.Library.route
                    if (isMainTab) {
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                    } else {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -(fullWidth * 0.12f).toInt() },
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.96f, animationSpec = tween(260, easing = FastOutSlowInEasing))
                    }
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -(fullWidth * 0.15f).toInt() },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(280, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> (fullWidth * 0.30f).toInt() },
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        scaleOut(targetScale = 0.95f, animationSpec = tween(260, easing = FastOutSlowInEasing))
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
                        currentSong = playerUiState.currentSong,
                        isPlaying = playerUiState.isPlaying,
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
                        currentSong = playerUiState.currentSong,
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
                        currentSong = playerUiState.currentSong,
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
                        currentSong = playerUiState.currentSong,
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
                        currentSong = playerUiState.currentSong,
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
                            navController.navigate("search") { launchSingleTop = true }
                        },
                        onOpenSettings = {
                            navController.navigate("settings") { launchSingleTop = true }
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

                // 设置与播放偏好中心
                composable("settings") {
                    SettingsScreen(
                        viewModel = musicPlayerViewModel,
                        onBackClick = { navController.popBackStack() }
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
                        currentSong = playerUiState.currentSong,
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
                        currentSong = playerUiState.currentSong,
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
                        currentSong = playerUiState.currentSong,
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
                        currentSong = playerUiState.currentSong,
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
                    val userAvatar = VipManager.accounts.state.value.profile?.avatarUrl ?: ""
                    CommentsScreen(
                        song = playerUiState.currentSong,
                        comments = playerUiState.comments,
                        loading = playerUiState.commentsLoading,
                        currentType = playerUiState.commentType,
                        hasMore = playerUiState.commentsHasMore,
                        currentUserAvatar = userAvatar,
                        onBack = { navController.popBackStack() },
                        onTabSelected = { musicPlayerViewModel.setCommentType(it) },
                        onLoadMore = { musicPlayerViewModel.loadComments(isRefresh = false) },
                        onRefresh = { musicPlayerViewModel.loadComments(isRefresh = true) },
                        onPostComment = { content, replyId, replyUser ->
                            musicPlayerViewModel.postComment(content, replyId, replyUser)
                        },
                        onLikeComment = { musicPlayerViewModel.likeComment(it) }
                    )
                }
            }
        }

        // 沉浸式全屏播放器
        if (showNowPlaying && playerUiState.currentSong != null) {
            val currentSong = playerUiState.currentSong!!
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
                coverStyle = playerUiState.coverStyle,
                onSelectCoverStyle = { musicPlayerViewModel.setCoverStyle(it) },
                showTranslation = playerUiState.showTranslation,
                showPhonetic = playerUiState.showPhonetic,
                onToggleTranslation = { musicPlayerViewModel.toggleTranslation() },
                onTogglePhonetic = { musicPlayerViewModel.togglePhonetic() },
                lyricFontSize = playerUiState.lyricFontSize,
                lyricAlignment = playerUiState.lyricAlignment,
                lyricWordByWord = playerUiState.lyricWordByWord,
                commentCount = playerUiState.commentCount,
                playbackSpeed = playerUiState.playbackSpeed,
                playbackPitch = playerUiState.playbackPitch,
                linkPitchToSpeed = playerUiState.linkPitchToSpeed,
                sleepTimerRemainingSec = playerUiState.sleepTimerRemainingSec,
                sleepStopAfterCurrentSong = playerUiState.sleepStopAfterCurrentSong,
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
                    songToAddToPlaylist = currentSong
                },
                onSetLyricFontSize = { musicPlayerViewModel.setLyricFontSize(it) },
                onSetLyricAlignment = { musicPlayerViewModel.setLyricAlignment(it) },
                onSetLyricWordByWord = { musicPlayerViewModel.setLyricWordByWord(it) },
                onOpenComments = {
                    musicPlayerViewModel.loadComments(isRefresh = true)
                    showCommentsScreen = true
                },
                onSetPlaybackSpeed = { musicPlayerViewModel.setPlaybackSpeed(it) },
                onSetPlaybackPitch = { musicPlayerViewModel.setPlaybackPitch(it) },
                onSetLinkPitchToSpeed = { musicPlayerViewModel.setLinkPitchToSpeed(it) },
                onResetSpeedAndPitch = { musicPlayerViewModel.resetSpeedAndPitch() },
                onSetSleepTimer = { mins, stopAfter -> musicPlayerViewModel.setSleepTimer(mins, stopAfter) },
                onCancelSleepTimer = { musicPlayerViewModel.cancelSleepTimer() },
                onToggleClimaxMode = { musicPlayerViewModel.toggleClimaxMode() },
                onUnlockFullTrack = { musicPlayerViewModel.unlockFullTrackForCurrentSong() },
                onToggleAccompanyMode = { musicPlayerViewModel.toggleAccompanyMode() },
                onReorderQueueByMood = { mode -> musicPlayerViewModel.reorderQueueByMood(mode) },
                onMoveQueueItem = { fromIdx, toIdx -> musicPlayerViewModel.moveQueueItem(fromIdx, toIdx) },
                songDetail = playerUiState.songDetail,
                onDismiss = { showNowPlaying = false },
                onTogglePlay = { musicPlayerViewModel.togglePlayPause() },
                onPrevious = { musicPlayerViewModel.skipToPrevious() },
                onNext = { musicPlayerViewModel.skipToNext() },
                onSeekTo = { musicPlayerViewModel.seekTo(it) },
                onSeekToLyricLine = { musicPlayerViewModel.seekToLyricLine(it) },
                onCyclePlayMode = { musicPlayerViewModel.cyclePlayMode() },
                onSelectQuality = { musicPlayerViewModel.setPreferredQuality(it) },
                onPlayQueueSong = { musicPlayerViewModel.playSong(it, playerUiState.playlistQueue) },
                onRemoveFromQueue = { musicPlayerViewModel.removeFromQueue(it) },
                onClearQueue = { musicPlayerViewModel.clearQueue() }
            )
        }

        if (showCommentsScreen && playerUiState.currentSong != null) {
            Dialog(
                onDismissRequest = { showCommentsScreen = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                val userAvatar = VipManager.accounts.state.value.profile?.avatarUrl ?: ""
                CommentsScreen(
                    song = playerUiState.currentSong,
                    comments = playerUiState.comments,
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
                    onPostComment = { content, replyId, replyUser ->
                        musicPlayerViewModel.postComment(content, replyId, replyUser)
                    },
                    onLikeComment = { musicPlayerViewModel.likeComment(it) }
                )
            }
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
                    if (playerUiState.currentSong?.id != targetSong.id) {
                        musicPlayerViewModel.playSong(targetSong, listOf(targetSong))
                    } else {
                        musicPlayerViewModel.loadComments(isRefresh = true)
                    }
                    showCommentsScreen = true
                },
                onOpenArtist = if (targetSong.artistId.isNotBlank() && targetSong.artistId != "0") {
                    {
                        showNowPlaying = false
                        navController.navigate("artist_detail/${targetSong.artistId}") { launchSingleTop = true }
                    }
                } else null,
                onOpenAlbum = if (targetSong.albumId.isNotBlank() && targetSong.albumId != "0") {
                    {
                        showNowPlaying = false
                        navController.navigate("album_detail/${targetSong.albumId}") { launchSingleTop = true }
                    }
                } else null,
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

        FloatingLyricOverlay(
            song = if (!showNowPlaying) playerUiState.currentSong else null,
            lyricData = playerUiState.currentLyric,
            currentLyricIndex = playerUiState.currentLyricIndex,
            currentPositionMs = playerUiState.currentPositionMs,
            onClickCapsule = { showNowPlaying = true }
        )
    }
}
