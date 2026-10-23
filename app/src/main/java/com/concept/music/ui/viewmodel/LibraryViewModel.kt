package com.concept.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.concept.music.data.kugou.*
import com.concept.music.data.model.AlbumItem
import com.concept.music.data.model.FollowedArtist
import com.concept.music.data.model.Playlist
import com.concept.music.data.model.PurchasedAlbumItem
import com.concept.music.data.model.PurchasedSongItem
import com.concept.music.data.model.Song
import com.concept.music.data.model.UserGradeInfo
import com.concept.music.data.repository.deduplicateAndMergeSongs
import com.concept.music.data.repository.isSameMusicTrack
import com.concept.music.data.vip.ListeningGradeManager
import com.concept.music.data.vip.VipManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class LoginUiState(val tab: Int = 0, val mobile: String = "", val code: String = "", val busy: Boolean = false,
    val resendSeconds: Int = 0, val qr: KugouQrCode? = null, val qrStatus: String = "生成二维码后使用酷狗音乐 App 扫码",
    val candidates: List<LoginCandidate> = emptyList(), val message: String? = null, val completed: Boolean = false)

class LibraryViewModel(
    private val accounts: AccountRepository = VipManager.accounts,
    private val auth: AuthRepository = VipManager.auth,
    private val vip: VipRepository = VipManager.vip,
    private val verification: VerificationCoordinator = VipManager.verification
) : ViewModel() {
    val accountState = accounts.state
    val vipState = vip.state
    val pendingVerification = verification.pending
    private val loginMutable = MutableStateFlow(LoginUiState())
    val login = loginMutable.asStateFlow()
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists = _playlists.asStateFlow()
    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
    val likedSongs = _likedSongs.asStateFlow()
    private val _librarySongs = MutableStateFlow<List<Song>>(emptyList())
    val librarySongs = _librarySongs.asStateFlow()
    private val _libraryLoading = MutableStateFlow(false)
    val libraryLoading = _libraryLoading.asStateFlow()
    private val _libraryError = MutableStateFlow<String?>(null)
    val libraryError = _libraryError.asStateFlow()
    private val _librarySection = MutableStateFlow("我喜欢")
    val librarySection = _librarySection.asStateFlow()

    // 持久化「我的页」资产分组 Tab 状态与列表/网格视图状态（修复浏览已购专辑或收藏歌单返回后被重置回「自建」的问题）
    private val _selectedAssetTab = MutableStateFlow(0) // 0: 自建, 1: 收藏, 2: 关注, 3: 已购
    val selectedAssetTab = _selectedAssetTab.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView = _isGridView.asStateFlow()

    fun setSelectedAssetTab(tab: Int) {
        _selectedAssetTab.value = tab.coerceIn(0, 3)
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    // 听歌统计、已购、关注歌手与收藏专辑
    private val _userGrade = MutableStateFlow(UserGradeInfo())
    val userGrade = _userGrade.asStateFlow()

    private val _purchasedSongs = MutableStateFlow<List<PurchasedSongItem>>(emptyList())
    val purchasedSongs = _purchasedSongs.asStateFlow()

    private val _purchasedAlbums = MutableStateFlow<List<PurchasedAlbumItem>>(emptyList())
    val purchasedAlbums = _purchasedAlbums.asStateFlow()

    private val _followedArtists = MutableStateFlow<List<FollowedArtist>>(emptyList())
    val followedArtists = _followedArtists.asStateFlow()

    private val _favoriteAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val favoriteAlbums = _favoriteAlbums.asStateFlow()
    private var accountJob: Job? = null
    private var musicLibraryJob: Job? = null
    private var claimJob: Job? = null
    private var loginJob: Job? = null
    private var countdownJob: Job? = null
    private var qrJob: Job? = null
    private var loginVisible = false
    private var foreground = false
    private var loginVersion = 0L
    private val autoAttempts = mutableMapOf<String, Long>()

    val isGradeSyncing = ListeningGradeManager.isSyncing
    private var loadedLibraryUserId: String? = "__UNINITIALIZED__"
    private var loadedLibraryAuth: AuthState? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            hydrateLibraryFromLocalCache(accountState.value.currentId)
            accountState.collect { state ->
                if (state.auth != AuthState.RESTORING) {
                    if (state.currentId != loadedLibraryUserId || state.auth != loadedLibraryAuth) {
                        loadedLibraryUserId = state.currentId
                        loadedLibraryAuth = state.auth
                        hydrateLibraryFromLocalCache(state.currentId)
                        val hasCached = _playlists.value.isNotEmpty() || _likedSongs.value.isNotEmpty()
                        if (hasCached) {
                            // 错峰延后静默同步，避开冷启动首屏渲染与发现页加载高峰，彻底消除打开 App 时的掉帧卡顿
                            delay(1500L)
                        }
                        loadMusicLibrary()
                    }
                }
            }
        }
        viewModelScope.launch {
            ListeningGradeManager.gradeInfo.collect { info ->
                _userGrade.value = info
            }
        }
    }

    private fun hydrateLibraryFromLocalCache(userId: String?) {
        val cache = VipManager.discoverCache
        val cachedPlaylists = cache.getUserPlaylists(userId)
        if (_playlists.value.isEmpty() && cachedPlaylists.isNotEmpty()) {
            _playlists.value = cachedPlaylists
        }
        val cachedLiked = cache.getLikedSongs(userId)
        if (_likedSongs.value.isEmpty() && cachedLiked.isNotEmpty()) {
            _likedSongs.value = cachedLiked
        }
    }

    fun isSameSong(a: Song, b: Song): Boolean = isSameMusicTrack(a, b)

    fun isSongLiked(song: Song?, likedList: List<Song> = _likedSongs.value): Boolean {
        if (song == null) return false
        return likedList.any { isSameSong(it, song) }
    }

    /**
     * 严格边界：打开歌单详情页时仅加载该歌单的曲目列表，不重复全量拉取听歌等级、已购资产、关注歌手或全部歌单
     */
    fun openLibrarySection(section: String) {
        _librarySection.value = section
        loadSectionSongsOnly(section = section, forceRefresh = false)
    }

    /**
     * 严格边界：仅刷新当前歌单详情页内的歌曲列表
     */
    fun refreshCurrentPlaylistOnly(playlistId: String) {
        val section = when (playlistId) {
            "like" -> "我喜欢"
            "history" -> "最近"
            else -> "歌单:$playlistId"
        }
        _librarySection.value = section
        loadSectionSongsOnly(section = section, forceRefresh = true)
    }

    /**
     * 严格边界：仅刷新「自建/收藏歌单」列表，不触发关注歌手、已购资产或听歌等级同步
     */
    fun refreshPlaylistsOnly() {
        val userId = accountState.value.currentId ?: return
        if (accountState.value.auth != AuthState.READY) return
        viewModelScope.launch {
            _libraryLoading.value = true
            _libraryError.value = null
            try {
                val lists = VipManager.music.userPlaylists()
                if (accounts.state.value.currentId == userId) {
                    _playlists.value = lists
                    val likedPl = lists.firstOrNull { it.isLiked }
                    if (likedPl != null) {
                        val likedTracks = runCatching { VipManager.music.playlistSongs(likedPl) }.getOrDefault(_likedSongs.value)
                        _likedSongs.value = likedTracks
                    }
                }
            } catch (e: Exception) {
                if (accounts.state.value.currentId == userId) {
                    _libraryError.value = e.message ?: "歌单列表刷新失败"
                }
            } finally {
                if (accounts.state.value.currentId == userId) {
                    _libraryLoading.value = false
                }
            }
        }
    }

    private fun loadSectionSongsOnly(section: String, forceRefresh: Boolean) {
        val userId = accountState.value.currentId
        if (section == "最近") {
            _libraryLoading.value = true
            _libraryError.value = null
            musicLibraryJob?.cancel()
            musicLibraryJob = viewModelScope.launch {
                val localHistory = VipManager.history.get(userId)
                if (localHistory.isNotEmpty()) {
                    _librarySongs.value = localHistory
                }
                val cloudLatest = if (userId != null && accountState.value.auth == AuthState.READY) {
                    runCatching { VipManager.music.latestListenSongs(pageSize = 50) }.getOrDefault(emptyList())
                } else emptyList()
                _librarySongs.value = if (cloudLatest.isNotEmpty()) {
                    deduplicateAndMergeSongs(localHistory + cloudLatest)
                } else {
                    deduplicateAndMergeSongs(localHistory)
                }
                _libraryLoading.value = false
            }
            return
        }

        if (section == "我喜欢") {
            val cachedLiked = _likedSongs.value.ifEmpty { VipManager.discoverCache.getLikedSongs(userId) }
            if (cachedLiked.isNotEmpty()) {
                _likedSongs.value = cachedLiked
                _librarySongs.value = cachedLiked
                if (!forceRefresh) return
            }
            musicLibraryJob?.cancel()
            musicLibraryJob = viewModelScope.launch {
                if (_librarySongs.value.isEmpty() || forceRefresh) {
                    _libraryLoading.value = true
                }
                _libraryError.value = null
                try {
                    val lists = _playlists.value.ifEmpty {
                        if (userId != null && accountState.value.auth == AuthState.READY) {
                            runCatching { VipManager.music.userPlaylists() }.getOrDefault(emptyList()).also {
                                _playlists.value = it
                                VipManager.discoverCache.saveUserPlaylists(userId, it)
                            }
                        } else emptyList()
                    }
                    val likedPl = lists.firstOrNull { it.isLiked }
                    val likedTracks = if (likedPl != null) {
                        runCatching { VipManager.music.playlistSongs(likedPl) }.getOrDefault(_likedSongs.value)
                    } else emptyList()
                    if (likedTracks.isNotEmpty()) {
                        _likedSongs.value = likedTracks
                        _librarySongs.value = likedTracks
                        VipManager.discoverCache.saveLikedSongs(userId, likedTracks)
                    }
                } finally {
                    _libraryLoading.value = false
                }
            }
            return
        }

        if (section.startsWith("歌单:")) {
            val targetPlaylistId = section.removePrefix("歌单:")
            val cachedTracks = VipManager.discoverCache.getPlaylistSongs(targetPlaylistId)
            if (!forceRefresh && cachedTracks.isNotEmpty()) {
                _librarySongs.value = cachedTracks
            }
            musicLibraryJob?.cancel()
            musicLibraryJob = viewModelScope.launch {
                if (cachedTracks.isEmpty() || forceRefresh) {
                    _libraryLoading.value = true
                }
                _libraryError.value = null
                try {
                    val lists = _playlists.value.ifEmpty {
                        if (userId != null && accountState.value.auth == AuthState.READY) {
                            runCatching { VipManager.music.userPlaylists() }.getOrDefault(emptyList()).also {
                                _playlists.value = it
                                VipManager.discoverCache.saveUserPlaylists(userId, it)
                            }
                        } else emptyList()
                    }
                    val selected = lists.firstOrNull {
                        it.id == targetPlaylistId || it.globalCollectionId == targetPlaylistId || it.listCreateListid == targetPlaylistId
                    }
                    val extInfoDef = async { runCatching { VipManager.music.playlistInfo(targetPlaylistId) }.getOrNull() }
                    val extSongsDef = async { runCatching { VipManager.music.fetchPlaylistSongsAll(targetPlaylistId) }.getOrDefault(emptyList()) }

                    if (selected != null) {
                        val tracks = runCatching { VipManager.music.playlistSongs(selected) }.getOrDefault(emptyList())
                        val finalTracks = tracks.ifEmpty { extSongsDef.await() }
                        if (finalTracks.isNotEmpty()) {
                            _librarySongs.value = finalTracks
                            VipManager.discoverCache.savePlaylistSongs(targetPlaylistId, finalTracks)
                        }
                        val extInfo = extInfoDef.await()
                        if (extInfo != null && (selected.description.isBlank() || selected.coverUrl.isBlank())) {
                            val enriched = selected.copy(
                                description = selected.description.ifBlank { extInfo.description },
                                coverUrl = selected.coverUrl.ifBlank { extInfo.coverUrl.ifBlank { finalTracks.firstOrNull()?.coverUrl.orEmpty() } },
                                trackCount = if (finalTracks.isNotEmpty()) finalTracks.size else selected.trackCount
                            )
                            _playlists.value = _playlists.value.map { if (it.id == selected.id) enriched else it }
                        }
                    } else {
                        val extPlaylist = extInfoDef.await()
                        val trackList = extSongsDef.await()
                        val resolvedPl = (extPlaylist ?: Playlist(
                            id = targetPlaylistId,
                            title = "精选歌单",
                            isCreatedByMe = false,
                            globalCollectionId = targetPlaylistId
                        )).let { pl ->
                            pl.copy(
                                coverUrl = pl.coverUrl.ifBlank { trackList.firstOrNull()?.coverUrl.orEmpty() },
                                trackCount = if (trackList.isNotEmpty()) trackList.size else pl.trackCount,
                                listCreateListid = pl.listCreateListid.ifBlank { targetPlaylistId }
                            )
                        }
                        _playlists.value = (_playlists.value.filterNot {
                            it.id == targetPlaylistId || it.id == resolvedPl.id || it.globalCollectionId == targetPlaylistId
                        } + resolvedPl)
                        if (trackList.isNotEmpty()) {
                            _librarySongs.value = trackList
                            VipManager.discoverCache.savePlaylistSongs(targetPlaylistId, trackList)
                        }
                    }
                } catch (e: Exception) {
                    if (_librarySongs.value.isEmpty()) {
                        _libraryError.value = e.message ?: "歌单加载失败"
                    }
                } finally {
                    _libraryLoading.value = false
                }
            }
            return
        }
    }

    fun refreshMusicLibrary() = loadMusicLibrary()

    fun clearHistory() {
        val userId = accountState.value.currentId
        VipManager.history.clear(userId)
        if (_librarySection.value == "最近") {
            _librarySongs.value = emptyList()
        }
    }

    fun refreshPurchasedAssets() {
        val userId = accountState.value.currentId ?: return
        if (accountState.value.auth != AuthState.READY) return
        viewModelScope.launch {
            val pSongs = runCatching { VipManager.music.userPurchasedSongs() }.getOrDefault(emptyList())
            val pAlbums = runCatching { VipManager.music.userPurchasedAlbums() }.getOrDefault(emptyList())
            if (accounts.state.value.currentId == userId) {
                _purchasedSongs.value = pSongs
                _purchasedAlbums.value = pAlbums
            }
        }
    }

    // 刷新关注歌手列表
    fun refreshFollowedArtists() {
        val userId = accountState.value.currentId ?: return
        if (accountState.value.auth != AuthState.READY) return
        viewModelScope.launch {
            val artists = runCatching { VipManager.music.followedArtists() }.getOrDefault(emptyList())
            if (accounts.state.value.currentId == userId) {
                _followedArtists.value = artists
            }
        }
    }

    fun isArtistFollowed(artistId: String, list: List<FollowedArtist> = _followedArtists.value): Boolean {
        if (artistId.isBlank() || artistId == "0") return false
        return list.any { it.id == artistId }
    }

    fun toggleFollowArtist(
        artist: com.concept.music.data.model.ArtistItem,
        onResult: (Boolean, Boolean, String) -> Unit = { _, _, _ -> }
    ) {
        if (accountState.value.auth != AuthState.READY) {
            onResult(false, false, "请先登录酷狗账号")
            return
        }
        val artistId = artist.id.trim()
        if (artistId.isBlank() || artistId == "0") {
            onResult(false, false, "无效的歌手 ID")
            return
        }
        viewModelScope.launch {
            val currentlyFollowed = isArtistFollowed(artistId)
            val prevList = _followedArtists.value
            if (currentlyFollowed) {
                _followedArtists.value = prevList.filterNot { it.id == artistId }
                val ok = runCatching { VipManager.music.unfollowArtist(artistId) }.getOrDefault(false)
                if (ok) {
                    onResult(true, false, "已取消关注 ${artist.name}")
                } else {
                    _followedArtists.value = prevList
                    onResult(false, true, "取消关注失败，请稍后重试")
                }
            } else {
                val added = FollowedArtist(
                    id = artistId,
                    name = artist.name,
                    avatarUrl = artist.avatarUrl,
                    songCount = artist.songCount
                )
                _followedArtists.value = listOf(added) + prevList
                val ok = runCatching { VipManager.music.followArtist(artistId) }.getOrDefault(false)
                if (ok) {
                    onResult(true, true, "已关注歌手 ${artist.name}")
                } else {
                    _followedArtists.value = prevList
                    onResult(false, false, "关注失败，请稍后重试")
                }
            }
        }
    }

    // 外部歌单文本批量导入
    fun importExternalPlaylist(name: String, rawText: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (accountState.value.auth != AuthState.READY) {
            onResult(false, "请先登录酷狗账号")
            return
        }
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            onResult(false, "请输入至少一首歌曲名称（格式：歌名 - 歌手）")
            return
        }
        viewModelScope.launch {
            val count = runCatching { VipManager.music.importExternalPlaylist(name, lines) }.getOrDefault(0)
            if (count > 0) {
                loadMusicLibrary()
                onResult(true, "成功导入 $count 首歌曲至歌单「${name.ifBlank { "导入歌单" }}」")
            } else {
                onResult(false, "未能匹配或导入歌曲，请检查格式后重试")
            }
        }
    }

    private fun loadMusicLibrary() {
        val userId = accountState.value.currentId
        val section = librarySection.value

        if (section == "最近") {
            _libraryLoading.value = true
            _libraryError.value = null
            musicLibraryJob?.cancel()
            musicLibraryJob = viewModelScope.launch {
                val localHistory = VipManager.history.get(userId)
                val cloudLatest = if (userId != null && accountState.value.auth == AuthState.READY) {
                    runCatching { VipManager.music.latestListenSongs(pageSize = 50) }.getOrDefault(emptyList())
                } else emptyList()

                _librarySongs.value = if (cloudLatest.isNotEmpty()) {
                    deduplicateAndMergeSongs(localHistory + cloudLatest)
                } else {
                    deduplicateAndMergeSongs(localHistory)
                }
                if (userId != null && accountState.value.auth == AuthState.READY) {
                    launch { ListeningGradeManager.syncGrade(forceRefresh = false) }
                    launch {
                        val pSongs = runCatching { VipManager.music.userPurchasedSongs() }.getOrDefault(emptyList())
                        val pAlbums = runCatching { VipManager.music.userPurchasedAlbums() }.getOrDefault(emptyList())
                        if (accounts.state.value.currentId == userId) {
                            _purchasedSongs.value = pSongs
                            _purchasedAlbums.value = pAlbums
                        }
                    }
                    launch {
                        val artists = runCatching { VipManager.music.followedArtists() }.getOrDefault(emptyList())
                        if (accounts.state.value.currentId == userId) {
                            _followedArtists.value = artists
                        }
                    }
                }
                _libraryLoading.value = false
            }
            return
        }

        _librarySongs.value = emptyList()
        if (userId == null || accountState.value.auth != AuthState.READY) {
            if (section.startsWith("歌单:")) {
                val targetPlaylistId = section.removePrefix("歌单:")
                musicLibraryJob?.cancel()
                musicLibraryJob = viewModelScope.launch {
                    _libraryLoading.value = true
                    _libraryError.value = null
                    try {
                        val infoDef = async { runCatching { VipManager.music.playlistInfo(targetPlaylistId) }.getOrNull() }
                        val songsDef = async { runCatching { VipManager.music.fetchPlaylistSongsAll(targetPlaylistId) }.getOrDefault(emptyList()) }
                        val extPlaylist = infoDef.await()
                        val trackList = songsDef.await()
                        val resolvedPl = (extPlaylist ?: Playlist(
                            id = targetPlaylistId,
                            title = "精选歌单",
                            isCreatedByMe = false,
                            globalCollectionId = targetPlaylistId
                        )).let { pl ->
                            pl.copy(
                                coverUrl = pl.coverUrl.ifBlank { trackList.firstOrNull()?.coverUrl.orEmpty() },
                                trackCount = if (trackList.isNotEmpty()) trackList.size else pl.trackCount,
                                listCreateListid = pl.listCreateListid.ifBlank { targetPlaylistId }
                            )
                        }
                        _playlists.value = (_playlists.value.filterNot {
                            it.id == targetPlaylistId || it.id == resolvedPl.id || it.globalCollectionId == targetPlaylistId
                        } + resolvedPl)
                        _librarySongs.value = trackList
                    } catch (e: Exception) {
                        _libraryError.value = e.message ?: "歌单加载失败"
                    } finally {
                        _libraryLoading.value = false
                    }
                }
                return
            }
            _playlists.value = emptyList()
            _likedSongs.value = emptyList()
            _librarySongs.value = emptyList()
            _followedArtists.value = emptyList()
            _purchasedSongs.value = emptyList()
            _purchasedAlbums.value = emptyList()
            _favoriteAlbums.value = emptyList()
            return
        }
        musicLibraryJob?.cancel()
        musicLibraryJob = viewModelScope.launch {
            _libraryLoading.value = true; _libraryError.value = null
            try {
                // 并行启动听歌等级、已购资产与关注歌手查询
                launch {
                    ListeningGradeManager.syncGrade(forceRefresh = false)
                }
                launch {
                    val pSongs = runCatching { VipManager.music.userPurchasedSongs() }.getOrDefault(emptyList())
                    val pAlbums = runCatching { VipManager.music.userPurchasedAlbums() }.getOrDefault(emptyList())
                    if (accounts.state.value.currentId == userId) {
                        _purchasedSongs.value = pSongs
                        _purchasedAlbums.value = pAlbums
                    }
                }
                launch {
                    val artists = runCatching { VipManager.music.followedArtists() }.getOrDefault(emptyList())
                    if (accounts.state.value.currentId == userId) {
                        _followedArtists.value = artists
                    }
                }

                val targetPlaylistId: String? = if (section.startsWith("歌单:")) section.removePrefix("歌单:") else null
                val extInfoDef: Deferred<Playlist?>? = if (targetPlaylistId != null) {
                    async<Playlist?> { runCatching { VipManager.music.playlistInfo(targetPlaylistId) }.getOrNull() }
                } else null
                val extSongsDef: Deferred<List<Song>>? = if (targetPlaylistId != null) {
                    async<List<Song>> { runCatching { VipManager.music.fetchPlaylistSongsAll(targetPlaylistId) }.getOrDefault(emptyList()) }
                } else null

                val lists = VipManager.music.userPlaylists()
                if (accounts.state.value.currentId != userId) return@launch
                _playlists.value = lists
                VipManager.discoverCache.saveUserPlaylists(userId, lists)
                val likedPl = lists.firstOrNull { it.isLiked }
                val likedTracks = if (likedPl != null) {
                    runCatching { VipManager.music.playlistSongs(likedPl) }.getOrDefault(_likedSongs.value)
                } else emptyList()
                _likedSongs.value = likedTracks
                if (likedTracks.isNotEmpty()) {
                    VipManager.discoverCache.saveLikedSongs(userId, likedTracks)
                }

                val selected = when (section) {
                    "我喜欢" -> likedPl
                    "我创建的歌单" -> null
                    "收藏的歌单" -> null
                    else -> targetPlaylistId?.let { id ->
                        lists.firstOrNull { it.id == id || it.globalCollectionId == id || it.listCreateListid == id }
                    }
                }
                when (section) {
                    "我创建的歌单", "收藏的歌单" -> _librarySongs.value = emptyList()
                    "我喜欢" -> _librarySongs.value = likedTracks
                    else -> {
                        if (selected != null) {
                            val tracks = runCatching { VipManager.music.playlistSongs(selected) }.getOrDefault(emptyList())
                            val finalTracks = tracks.ifEmpty { extSongsDef?.await().orEmpty() }
                            _librarySongs.value = finalTracks
                            val extInfo = extInfoDef?.await()
                            if (extInfo != null && (selected.description.isBlank() || selected.coverUrl.isBlank())) {
                                val enriched = selected.copy(
                                    description = selected.description.ifBlank { extInfo.description },
                                    coverUrl = selected.coverUrl.ifBlank { extInfo.coverUrl.ifBlank { finalTracks.firstOrNull()?.coverUrl.orEmpty() } },
                                    trackCount = if (finalTracks.isNotEmpty()) finalTracks.size else selected.trackCount
                                )
                                _playlists.value = _playlists.value.map { if (it.id == selected.id) enriched else it }
                            }
                        } else if (targetPlaylistId != null) {
                            val extPlaylist = extInfoDef?.await()
                            val trackList = extSongsDef?.await().orEmpty()
                            val resolvedPl = (extPlaylist ?: Playlist(
                                id = targetPlaylistId,
                                title = "精选歌单",
                                isCreatedByMe = false,
                                globalCollectionId = targetPlaylistId
                            )).let { pl ->
                                pl.copy(
                                    coverUrl = pl.coverUrl.ifBlank { trackList.firstOrNull()?.coverUrl.orEmpty() },
                                    trackCount = if (trackList.isNotEmpty()) trackList.size else pl.trackCount,
                                    listCreateListid = pl.listCreateListid.ifBlank { targetPlaylistId }
                                )
                            }
                            _playlists.value = (_playlists.value.filterNot {
                                it.id == targetPlaylistId || it.id == resolvedPl.id || it.globalCollectionId == targetPlaylistId
                            } + resolvedPl)
                            _librarySongs.value = trackList
                        }
                    }
                }
            } catch (e: Exception) {
                if (accounts.state.value.currentId == userId) _libraryError.value = e.message ?: "音乐库加载失败"
            } finally { if (accounts.state.value.currentId == userId) _libraryLoading.value = false }
        }
    }

    fun refreshGradeInfo(onResult: ((Boolean, String) -> Unit)? = null) {
        if (accountState.value.auth != AuthState.READY) {
            onResult?.invoke(false, "请先登录酷狗账号")
            return
        }
        viewModelScope.launch {
            val result = ListeningGradeManager.syncGrade(forceRefresh = true)
            if (result.isSuccess) {
                val grade = result.getOrNull()
                if (grade != null) _userGrade.value = grade
                onResult?.invoke(true, "听歌等级与时长已同步至官方云端")
            } else {
                onResult?.invoke(false, result.exceptionOrNull()?.message ?: "同步失败，请检查网络")
            }
        }
    }

    fun createPlaylist(name: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (name.isBlank()) {
            onResult(false, "歌单名称不能为空")
            return
        }
        viewModelScope.launch {
            val success = runCatching { VipManager.music.createPlaylist(name.trim()) }.getOrDefault(false)
            if (success) {
                loadMusicLibrary()
                onResult(true, "新建歌单成功")
            } else {
                onResult(false, "新建歌单失败，请稍后重试")
            }
        }
    }

    fun collectPlaylist(playlist: Playlist, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val success = runCatching { VipManager.music.collectPlaylist(playlist) }.getOrDefault(false)
            if (success) {
                loadMusicLibrary()
                onResult(true, "已成功收藏歌单")
            } else {
                onResult(false, "收藏歌单失败，请稍后重试")
            }
        }
    }

    fun deletePlaylist(playlist: Playlist, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val success = runCatching { VipManager.music.deleteOrUncollectPlaylist(playlist) }.getOrDefault(false)
            if (success) {
                _playlists.value = _playlists.value.filterNot { it.id == playlist.id || it.listId == playlist.listId }
                loadMusicLibrary()
                onResult(true, if (playlist.isCreatedByMe) "歌单已删除" else "已取消收藏")
            } else {
                onResult(false, "操作失败，请稍后重试")
            }
        }
    }

    fun toggleFavorite(song: Song, onResult: (Boolean, Boolean, String) -> Unit = { _, _, _ -> }) {
        if (accountState.value.auth != AuthState.READY) {
            onResult(false, false, "请先登录酷狗账号")
            return
        }
        viewModelScope.launch {
            val likedPl = _playlists.value.firstOrNull { it.isLiked }
                ?: runCatching {
                    VipManager.music.userPlaylists().also { _playlists.value = it }.firstOrNull { it.isLiked }
                }.getOrNull()

            if (likedPl == null || likedPl.listId <= 0L) {
                onResult(false, false, "未找到「我喜欢」歌单，请稍后重试")
                return@launch
            }

            val currentlyLiked = isSongLiked(song)
            val prevLikedList = _likedSongs.value
            if (currentlyLiked) {
                val matchedInLiked = prevLikedList.firstOrNull { isSameSong(it, song) } ?: song
                _likedSongs.value = prevLikedList.filterNot { isSameSong(it, song) }
                if (_librarySection.value == "我喜欢") {
                    _librarySongs.value = _librarySongs.value.filterNot { isSameSong(it, song) }
                }
                val success = runCatching { VipManager.music.removeSongFromPlaylist(likedPl.listId, matchedInLiked) }.getOrDefault(false)
                if (success) {
                    _playlists.value = _playlists.value.map {
                        if (it.isLiked) it.copy(trackCount = (it.trackCount - 1).coerceAtLeast(0)) else it
                    }
                    VipManager.discoverCache.saveLikedSongs(accountState.value.currentId, _likedSongs.value)
                    VipManager.discoverCache.saveUserPlaylists(accountState.value.currentId, _playlists.value)
                    onResult(true, false, "已从「我喜欢的音乐」中移除")
                } else {
                    _likedSongs.value = prevLikedList
                    if (_librarySection.value == "我喜欢") {
                        _librarySongs.value = prevLikedList
                    }
                    onResult(false, true, "取消喜欢失败，请稍后重试")
                }
            } else {
                val addedSong = song.copy(isFavorite = true)
                _likedSongs.value = listOf(addedSong) + prevLikedList
                if (_librarySection.value == "我喜欢") {
                    _librarySongs.value = listOf(addedSong) + _librarySongs.value
                }
                val success = runCatching { VipManager.music.addSongToPlaylist(likedPl.listId, song) }.getOrDefault(false)
                if (success) {
                    _playlists.value = _playlists.value.map {
                        if (it.isLiked) it.copy(trackCount = it.trackCount + 1) else it
                    }
                    VipManager.discoverCache.saveLikedSongs(accountState.value.currentId, _likedSongs.value)
                    VipManager.discoverCache.saveUserPlaylists(accountState.value.currentId, _playlists.value)
                    launch {
                        runCatching { VipManager.music.playlistSongs(likedPl) }.getOrNull()?.let { fresh ->
                            _likedSongs.value = fresh
                            VipManager.discoverCache.saveLikedSongs(accountState.value.currentId, fresh)
                            if (_librarySection.value == "我喜欢") {
                                _librarySongs.value = fresh
                            }
                        }
                    }
                    onResult(true, true, "已收藏到「我喜欢的音乐」")
                } else {
                    _likedSongs.value = prevLikedList
                    if (_librarySection.value == "我喜欢") {
                        _librarySongs.value = prevLikedList
                    }
                    onResult(false, false, "收藏失败，请稍后重试")
                }
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song, playlistName: String = "", onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (accountState.value.auth != AuthState.READY) {
            onResult(false, "请先登录酷狗账号")
            return
        }
        viewModelScope.launch {
            val success = runCatching { VipManager.music.addSongToPlaylist(playlistId, song) }.getOrDefault(false)
            if (success) {
                val targetPl = _playlists.value.firstOrNull { it.listId == playlistId }
                _playlists.value = _playlists.value.map {
                    if (it.listId == playlistId) it.copy(trackCount = it.trackCount + 1) else it
                }
                VipManager.discoverCache.saveUserPlaylists(accountState.value.currentId, _playlists.value)
                if (targetPl != null) {
                    val cached = VipManager.discoverCache.getPlaylistSongs(targetPl.id)
                    if (cached.isNotEmpty() && cached.none { isSameSong(it, song) }) {
                        VipManager.discoverCache.savePlaylistSongs(targetPl.id, listOf(song) + cached)
                    }
                    if (_librarySection.value == "歌单:${targetPl.id}" && _librarySongs.value.none { isSameSong(it, song) }) {
                        _librarySongs.value = listOf(song) + _librarySongs.value
                    }
                }
                val tip = if (playlistName.isNotBlank()) "已成功添加到「$playlistName」" else "已成功添加到歌单"
                onResult(true, tip)
            } else {
                onResult(false, "添加到歌单失败，请重试")
            }
        }
    }

    fun addSongsToPlaylistBatch(playlistId: Long, songs: List<Song>, playlistName: String = "", onResult: (Int, String) -> Unit = { _, _ -> }) {
        if (accountState.value.auth != AuthState.READY) {
            onResult(0, "请先登录酷狗账号")
            return
        }
        if (songs.isEmpty()) {
            onResult(0, "未选择需要添加的歌曲")
            return
        }
        viewModelScope.launch {
            val addedCount = runCatching { VipManager.music.addSongsToPlaylistBatch(playlistId, songs) }.getOrDefault(0)
            if (addedCount > 0) {
                val targetPl = _playlists.value.firstOrNull { it.listId == playlistId }
                _playlists.value = _playlists.value.map {
                    if (it.listId == playlistId) it.copy(trackCount = it.trackCount + addedCount) else it
                }
                VipManager.discoverCache.saveUserPlaylists(accountState.value.currentId, _playlists.value)
                if (targetPl != null) {
                    val cached = VipManager.discoverCache.getPlaylistSongs(targetPl.id)
                    if (cached.isNotEmpty()) {
                        val newOnes = songs.filter { s -> cached.none { isSameSong(it, s) } }
                        if (newOnes.isNotEmpty()) {
                            VipManager.discoverCache.savePlaylistSongs(targetPl.id, newOnes + cached)
                        }
                    }
                    if (_librarySection.value == "歌单:${targetPl.id}") {
                        val curList = _librarySongs.value
                        val newOnes = songs.filter { s -> curList.none { isSameSong(it, s) } }
                        if (newOnes.isNotEmpty()) {
                            _librarySongs.value = newOnes + curList
                        }
                    }
                }
                val tip = if (playlistName.isNotBlank()) {
                    "已将 $addedCount 首歌曲加入「$playlistName」"
                } else {
                    "已成功添加 $addedCount 首歌曲到歌单"
                }
                onResult(addedCount, tip)
            } else {
                onResult(0, "批量添加到歌单失败，请重试")
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, song: Song, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val success = runCatching { VipManager.music.removeSongFromPlaylist(playlistId, song) }.getOrDefault(false)
            if (success) {
                val targetPl = _playlists.value.firstOrNull { it.listId == playlistId }
                _librarySongs.value = _librarySongs.value.filterNot { isSameSong(it, song) }
                if (targetPl?.isLiked == true) {
                    _likedSongs.value = _likedSongs.value.filterNot { isSameSong(it, song) }
                    VipManager.discoverCache.saveLikedSongs(accountState.value.currentId, _likedSongs.value)
                }
                _playlists.value = _playlists.value.map {
                    if (it.listId == playlistId) it.copy(trackCount = (it.trackCount - 1).coerceAtLeast(0)) else it
                }
                VipManager.discoverCache.saveUserPlaylists(accountState.value.currentId, _playlists.value)
                if (targetPl != null) {
                    val cached = VipManager.discoverCache.getPlaylistSongs(targetPl.id)
                    if (cached.isNotEmpty()) {
                        VipManager.discoverCache.savePlaylistSongs(targetPl.id, cached.filterNot { isSameSong(it, song) })
                    }
                }
                onResult(true, "已从歌单中移除")
            } else {
                onResult(false, "移除失败，请重试")
            }
        }
    }
    fun onForeground(active: Boolean) {
        foreground = active
        if (active && !loginVisible && accountJob?.isActive != true && claimJob?.isActive != true) refreshAccount()
    }
    fun refreshAccount() {
        if (accountJob?.isActive == true || claimJob?.isActive == true || loginVisible) return
        accountJob = viewModelScope.launch {
            if (accountState.value.auth == AuthState.RESTORING) accounts.restore()
            val context = accounts.context() ?: return@launch
            if (accountState.value.auth == AuthState.NEEDS_LOGIN) return@launch
            supervisorScope {
                launch { accounts.refreshProfile(context) }
                launch { vip.refresh(context) }
            }
            accounts.ensureCurrent(context)
            val current = accountState.value.current ?: return@launch
            val key = "${current.userId}@${today()}"
            val now = System.currentTimeMillis()
            if (foreground && current.autoClaim && current.completedDay != today() &&
                now - (autoAttempts[key] ?: 0) > 60_000 && accountState.value.auth == AuthState.READY) {
                autoAttempts[key] = now
                vip.claim(context, automatic = true)
            }
        }
    }
    private fun cancelAccountWork() {
        accountJob?.cancel(); claimJob?.cancel(); verification.cancel()
        accountJob = null; claimJob = null
    }
    fun selectAccount(id: String) { cancelAccountWork(); accounts.select(id); vip.clear(); refreshAccount() }
    fun removeAccount(id: String) {
        if (id == accountState.value.currentId) { cancelAccountWork(); vip.clear() }
        accounts.remove(id)
    }
    fun setAuto(enabled: Boolean) {
        accounts.setAuto(enabled)
        com.concept.music.service.VipGuardianManager.syncAutoClaimPreference(enabled)
        if (enabled) refreshAccount()
    }
    fun claimVip() {
        val context = accounts.context() ?: return
        if (accountState.value.auth == AuthState.NEEDS_LOGIN || claimJob?.isActive == true || vipState.value.busy) return
        accountJob?.cancel()
        claimJob = viewModelScope.launch { vip.claim(context) }
    }
    fun openLogin() {
        if (loginVisible) return
        cancelAccountWork()
        loginVersion++
        loginVisible = true
        loginMutable.value = LoginUiState()
    }
    fun closeLogin() {
        if (!loginVisible) return
        loginVersion++
        loginJob?.cancel(); qrJob?.cancel(); countdownJob?.cancel(); verification.cancel()
        loginMutable.value = LoginUiState()
        loginVisible = false
        if (foreground) refreshAccount()
    }
    fun onLoginRoute(active: Boolean) { if (active) openLogin() else closeLogin() }
    fun chooseTab(tab: Int) {
        if (login.value.busy) return
        qrJob?.cancel()
        loginMutable.value = login.value.copy(tab = tab, qr = null, candidates = emptyList(), message = null)
        if (tab == 1) createQr()
    }
    fun setMobile(value: String) { if (!login.value.busy) loginMutable.value = login.value.copy(mobile = value.filter(Char::isDigit).take(11), candidates = emptyList()) }
    fun setCode(value: String) { if (!login.value.busy) loginMutable.value = login.value.copy(code = value.filter(Char::isDigit).take(8), candidates = emptyList()) }
    fun sendCode() {
        if (login.value.resendSeconds > 0) return
        loginAction {
            verification.execute(null, guard = ::ensureLoginVisible) { auth.sendCode(login.value.mobile).requireSuccess() }
            loginMutable.value = login.value.copy(message = "验证码已发送", resendSeconds = 60)
            countdownJob?.cancel()
            countdownJob = viewModelScope.launch {
                val until = android.os.SystemClock.elapsedRealtime() + 60_000
                while (isActive) {
                    val left = ((until - android.os.SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
                    loginMutable.value = login.value.copy(resendSeconds = left)
                    if (left == 0) break
                    delay(250)
                }
            }
        }
    }
    fun phoneLogin(id: String? = null) = loginAction {
        val mobile = login.value.mobile
        val code = login.value.code
        when (val result = verification.execute(null, guard = ::ensureLoginVisible) { auth.phone(mobile, code, id) }) {
            is LoginResult.ChooseAccount -> loginMutable.value = login.value.copy(candidates = result.accounts, message = "此手机号关联多个账号，请选择")
            is LoginResult.Success -> finishLogin(result)
        }
    }
    private fun ensureLoginVisible() { if (!loginVisible) throw CancellationException("登录已关闭") }
    private fun finishLogin(result: LoginResult.Success) {
        ensureLoginVisible()
        accounts.accept(result); vip.clear()
        loginMutable.value = login.value.copy(code = "", candidates = emptyList(), completed = true, message = "登录成功")
    }
    private fun loginAction(block: suspend () -> Unit) {
        if (login.value.busy) return
        val version = loginVersion
        loginMutable.value = login.value.copy(busy = true, message = null)
        loginJob = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (version == loginVersion) loginMutable.value = login.value.copy(message = e.message ?: "登录失败") }
            finally { if (version == loginVersion) loginMutable.value = login.value.copy(busy = false) }
        }
    }
    fun createQr() {
        if (login.value.busy) return
        qrJob?.cancel()
        loginMutable.value = login.value.copy(qr = null, qrStatus = "正在生成二维码")
        loginAction {
            val qr = verification.execute(null, guard = ::ensureLoginVisible) { auth.createQr() }
            loginMutable.value = login.value.copy(qr = qr, qrStatus = "请使用酷狗音乐 App 扫码")
            qrJob = viewModelScope.launch {
                while (isActive && loginVisible && login.value.qr?.key == qr.key) {
                    if (System.currentTimeMillis() >= qr.expiresAt) {
                        loginMutable.value = login.value.copy(qr = null, qrStatus = "二维码已过期，请重新生成")
                        break
                    }
                    if (!foreground) { delay(300); continue }
                    try {
                        val result = auth.checkQr(qr.key)
                        when (result.data.number("status")) {
                            0 -> { loginMutable.value = login.value.copy(qr = null, qrStatus = "二维码已过期，请重新生成"); break }
                            1 -> loginMutable.value = login.value.copy(qrStatus = "等待扫码")
                            2 -> loginMutable.value = login.value.copy(qrStatus = "已扫码，请在酷狗音乐 App 确认")
                            4 -> {
                                loginMutable.value = login.value.copy(busy = true, qrStatus = "授权成功，正在补全登录凭证")
                                val initial = auth.qrSession(result.data)
                                val refreshed = verification.execute(initial.session, guard = ::ensureLoginVisible) { auth.refresh(initial.session) }
                                finishLogin(refreshed.copy(profile = if (refreshed.profile.nickname == "酷狗用户") initial.profile else refreshed.profile))
                                loginMutable.value = login.value.copy(busy = false)
                                break
                            }
                            else -> { loginMutable.value = login.value.copy(qrStatus = "二维码状态未知，请刷新"); break }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        loginMutable.value = login.value.copy(busy = false, qrStatus = "检查失败：${e.message}")
                        break
                    }
                    delay(2_000)
                }
            }
        }
    }
    fun verificationAnswer(id: String, answer: VerificationAnswer?) = verification.complete(id, answer)
    override fun onCleared() { verification.cancel(); super.onCleared() }
}
