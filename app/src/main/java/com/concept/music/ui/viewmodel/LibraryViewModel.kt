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
    private val _librarySongs = MutableStateFlow<List<Song>>(emptyList())
    val librarySongs = _librarySongs.asStateFlow()
    private val _libraryLoading = MutableStateFlow(false)
    val libraryLoading = _libraryLoading.asStateFlow()
    private val _libraryError = MutableStateFlow<String?>(null)
    val libraryError = _libraryError.asStateFlow()
    private val _librarySection = MutableStateFlow("我喜欢")
    val librarySection = _librarySection.asStateFlow()

    // 新增：听歌统计、已购、关注歌手与收藏专辑
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

    init { viewModelScope.launch { accountState.collect { loadMusicLibrary() } } }

    fun openLibrarySection(section: String) {
        _librarySection.value = section
        loadMusicLibrary()
    }

    fun refreshMusicLibrary() = loadMusicLibrary()

    fun clearHistory() {
        val userId = accountState.value.currentId
        VipManager.history.clear(userId)
        if (_librarySection.value == "最近") {
            _librarySongs.value = emptyList()
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
                // 优先从酷狗官方云端拉取当前账号在各端（PC/手机/车载）的最新播放历史并与本地融合
                val cloudLatest = if (userId != null && accountState.value.auth == AuthState.READY) {
                    runCatching { VipManager.music.latestListenSongs(pageSize = 50) }.getOrDefault(emptyList())
                } else emptyList()

                _librarySongs.value = if (cloudLatest.isNotEmpty()) {
                    (cloudLatest + localHistory).distinctBy { it.hash.ifBlank { it.id } }
                } else {
                    localHistory
                }
                _libraryLoading.value = false
            }
            return
        }

        _librarySongs.value = emptyList()
        if (userId == null || accountState.value.auth != AuthState.READY) {
            _playlists.value = emptyList()
            _librarySongs.value = emptyList()
            return
        }
        musicLibraryJob?.cancel()
        musicLibraryJob = viewModelScope.launch {
            _libraryLoading.value = true; _libraryError.value = null
            try {
                val lists = VipManager.music.userPlaylists()
                if (accounts.state.value.currentId != userId) return@launch
                _playlists.value = lists
                val selected = when (section) {
                    "我喜欢" -> lists.firstOrNull { it.isLiked }
                    "我创建的歌单" -> null
                    "收藏的歌单" -> null
                    else -> section.removePrefix("歌单:").takeIf { section.startsWith("歌单:") }?.let { id -> lists.firstOrNull { it.id == id } }
                }
                when (section) {
                    "我创建的歌单", "收藏的歌单" -> _librarySongs.value = emptyList()
                    else -> {
                        if (selected != null) {
                            _librarySongs.value = VipManager.music.playlistSongs(selected)
                        } else if (section.startsWith("歌单:")) {
                            val targetPlaylistId = section.removePrefix("歌单:")
                            val extPlaylist = runCatching { VipManager.music.playlistInfo(targetPlaylistId) }.getOrNull()
                            if (extPlaylist != null) {
                                _playlists.value = (_playlists.value + extPlaylist).distinctBy { it.id }
                            }
                            _librarySongs.value = runCatching { VipManager.music.fetchPlaylistSongsAll(targetPlaylistId) }.getOrDefault(emptyList())
                        }
                    }
                }
                if (accounts.state.value.currentId != userId) return@launch

                // 异步拉取真实等级统计
                launch {
                    val grade = runCatching { VipManager.music.userGradeInfo() }.getOrNull()
                    if (grade != null) _userGrade.value = grade
                }
            } catch (e: Exception) {
                if (accounts.state.value.currentId == userId) _libraryError.value = e.message ?: "音乐库加载失败"
            } finally { if (accounts.state.value.currentId == userId) _libraryLoading.value = false }
        }
    }

    fun refreshGradeInfo() {
        if (accountState.value.auth != AuthState.READY) return
        viewModelScope.launch {
            val grade = runCatching { VipManager.music.userGradeInfo() }.getOrNull()
            if (grade != null) _userGrade.value = grade
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

    fun addSongToPlaylist(playlistId: Long, song: Song, playlistName: String = "", onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val success = runCatching { VipManager.music.addSongToPlaylist(playlistId, song) }.getOrDefault(false)
            if (success) {
                val tip = if (playlistName.isNotBlank()) "已成功添加到「$playlistName」" else "已成功添加到歌单"
                onResult(true, tip)
            } else {
                onResult(false, "添加到歌单失败，请重试")
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, song: Song, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val success = runCatching { VipManager.music.removeSongFromPlaylist(playlistId, song) }.getOrDefault(false)
            if (success) {
                _librarySongs.value = _librarySongs.value.filterNot {
                    (song.fileId > 0 && it.fileId == song.fileId) || (song.hash.isNotBlank() && it.hash.equals(song.hash, ignoreCase = true)) || it.id == song.id
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
    fun setAuto(enabled: Boolean) { accounts.setAuto(enabled); if (enabled) refreshAccount() }
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
