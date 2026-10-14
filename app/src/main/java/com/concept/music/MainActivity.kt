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
import com.concept.music.ui.components.SongActionBottomSheet
import com.concept.music.ui.components.SongDetailSheet
import com.concept.music.ui.home.DailyRecommendScreen
import com.concept.music.ui.home.HomeScreen
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
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
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

    var showNowPlaying by remember { mutableStateOf(false) }
    var showCommentsScreen by remember { mutableStateOf(false) }

    // 全局通用单曲操作菜单与弹窗状态 ([BUG-05] / [INT-04])
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

                        // [MOT-03] 常驻底部 Mini 悬浮播放条，展开大播放页时伴随平滑容器缩放过渡
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
            // [MOT-03] 全局页面空间容器缩放+淡入淡出丝滑转场
            NavHost(
                navController = navController,
                startDestination = Screen.Discover.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    fadeIn(animationSpec = tween(240)) + scaleIn(initialScale = 0.95f, animationSpec = tween(240))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 1.03f, animationSpec = tween(180))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 1.03f, animationSpec = tween(220))
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.95f, animationSpec = tween(180))
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
                    currentSong = playerUiState.currentSong,
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

            // 设置与播放偏好中心 ([FUNC-06])
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
            onToggleTranslation = { musicPlayerViewModel.toggleTranslation() },
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

    // 独立全屏评论浮层 (使用全屏 Dialog 栈顶挂载，不销毁底层 NowPlayingSheet，修复 [BUG-07])
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

    // 全局复用单曲更多操作抽屉 ([BUG-05] / [INT-04])
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

    // 全局添加到歌单弹窗
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

    // 全局快捷新建歌单弹窗
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

    // 全局歌曲技术参数详情弹窗
    if (detailSheetSong != null) {
        val detail = remember(detailSheetSong) {
            VipManager.music.songDetail(detailSheetSong!!)
        }
        SongDetailSheet(
            detail = detail,
            onDismiss = { detailSheetSong = null }
        )
    }

        // [INNOV-05] 全局可拖拽 KRC 逐字悬浮歌词胶囊浮层（当播放页未展开且开启了悬浮歌词时展示）
        FloatingLyricOverlay(
            song = if (!showNowPlaying) playerUiState.currentSong else null,
            lyricData = playerUiState.currentLyric,
            currentLyricIndex = playerUiState.currentLyricIndex,
            currentPositionMs = playerUiState.currentPositionMs,
            onClickCapsule = { showNowPlaying = true }
        )
    }
}
