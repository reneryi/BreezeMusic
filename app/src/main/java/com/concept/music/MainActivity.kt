package com.concept.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.concept.music.ui.account.AccountsScreen
import com.concept.music.ui.account.LoginScreen
import com.concept.music.ui.account.VerificationDialog
import com.concept.music.ui.components.MiniPlayerBar
import com.concept.music.ui.components.NowPlayingSheet
import com.concept.music.ui.home.HomeScreen
import com.concept.music.ui.library.AlbumDetailScreen
import com.concept.music.ui.library.ArtistDetailScreen
import com.concept.music.ui.library.LibraryScreen
import com.concept.music.ui.library.PlaylistDetailScreen
import com.concept.music.ui.navigation.BottomNavBar
import com.concept.music.ui.navigation.Screen
import com.concept.music.ui.search.SearchScreen
import com.concept.music.ui.theme.ConceptMusicTheme
import com.concept.music.ui.viewmodel.HomeViewModel
import com.concept.music.ui.viewmodel.LibraryViewModel
import com.concept.music.ui.viewmodel.MusicPlayerViewModel
import com.concept.music.ui.viewmodel.SearchViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
    var showNowPlaying by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val isSubDetail = currentRoute.startsWith("playlist_detail") ||
                    currentRoute.startsWith("album_detail") ||
                    currentRoute.startsWith("artist_detail")

            if (currentRoute != "login" && currentRoute != "accounts" && currentRoute != "search") {
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

                    // 常驻底部 Mini 悬浮播放条 (播放时显现)
                    AnimatedVisibility(
                        visible = playerUiState.currentSong != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        MiniPlayerBar(
                            currentSong = playerUiState.currentSong,
                            isPlaying = playerUiState.isPlaying,
                            currentPositionMs = playerUiState.currentPositionMs,
                            durationMs = playerUiState.durationMs,
                            onTogglePlay = { musicPlayerViewModel.togglePlayPause() },
                            onNextClick = { musicPlayerViewModel.skipToNext() },
                            onBarClick = { showNowPlaying = true },
                            onQueueClick = { showNowPlaying = true }
                        )
                    }

                    // 底部双 Tab 导航条 (仅在主页级路由展示)
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
            modifier = Modifier.padding(innerPadding)
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
                    }
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
                    }
                )
            }

            // 歌手详情页
            composable("artist_detail/{artistId}") { backStackEntry ->
                val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
                ArtistDetailScreen(
                    artistId = artistId,
                    onBack = { navController.popBackStack() },
                    onSongClick = { song, list ->
                        musicPlayerViewModel.playSong(song, list)
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
        }
    }

    // 沉浸式全屏播放器
    if (showNowPlaying && playerUiState.currentSong != null) {
        NowPlayingSheet(
            song = playerUiState.currentSong,
            isPlaying = playerUiState.isPlaying,
            currentPositionMs = playerUiState.currentPositionMs,
            durationMs = playerUiState.durationMs,
            playMode = playerUiState.playMode,
            currentQualityLabel = playerUiState.currentQualityLabel,
            lyricData = playerUiState.currentLyric,
            currentLyricIndex = playerUiState.currentLyricIndex,
            queue = playerUiState.playlistQueue,
            currentIndex = playerUiState.currentIndex,
            comments = playerUiState.comments,
            commentsLoading = playerUiState.commentsLoading,
            commentsHasMore = playerUiState.commentsHasMore,
            commentType = playerUiState.commentType,
            onSelectCommentType = { musicPlayerViewModel.setCommentType(it) },
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
            onClearQueue = { musicPlayerViewModel.clearQueue() },
            onRequestComments = { musicPlayerViewModel.loadComments(isRefresh = true) },
            onLoadMoreComments = { musicPlayerViewModel.loadComments(isRefresh = false) },
            onRefreshComments = { musicPlayerViewModel.loadComments(isRefresh = true) }
        )
    }
}
