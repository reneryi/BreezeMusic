package com.concept.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.concept.music.data.kugou.AuthState
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.RankItem
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    // 每日推荐（独立刷新边界）
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val dailyRecommendSongs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _dailyRefreshing = MutableStateFlow(false)
    val dailyRefreshing: StateFlow<Boolean> = _dailyRefreshing.asStateFlow()

    // 官方私人随心听 FM（定位「猜你喜欢」，独立刷新边界，仅刷新随心听自身）
    private val _personalFmSongs = MutableStateFlow<List<Song>>(emptyList())
    val personalFmSongs: StateFlow<List<Song>> = _personalFmSongs.asStateFlow()

    private val _personalFmIndex = MutableStateFlow(0)
    val personalFmIndex: StateFlow<Int> = _personalFmIndex.asStateFlow()

    private val _personalFmRefreshing = MutableStateFlow(false)
    val personalFmRefreshing: StateFlow<Boolean> = _personalFmRefreshing.asStateFlow()

    // 新歌速递（独立刷新边界：仅刷新当前选中分类的新歌，不影响其他模块）
    private val _selectedNewSongPeriod = MutableStateFlow("CHINESE")
    val selectedNewSongPeriod: StateFlow<String> = _selectedNewSongPeriod.asStateFlow()

    private val _currentNewSongs = MutableStateFlow<List<Song>>(emptyList())
    val currentNewSongs: StateFlow<List<Song>> = _currentNewSongs.asStateFlow()

    private val _newSongsLoading = MutableStateFlow(false)
    val newSongsLoading: StateFlow<Boolean> = _newSongsLoading.asStateFlow()

    private val newSongCache = mutableMapOf<String, List<Song>>()

    // 实时场景电台列表 (FM Channels)
    private val _fmChannels = MutableStateFlow<List<FmChannel>>(emptyList())
    val fmChannels: StateFlow<List<FmChannel>> = _fmChannels.asStateFlow()

    // 巅峰排行榜列表 (每日实时更新)
    private val _ranks = MutableStateFlow<List<RankItem>>(emptyList())
    val ranks: StateFlow<List<RankItem>> = _ranks.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val accountState = VipManager.accounts.state
    val vipStatus = VipManager.vip.state.map { it.snapshot }

    private var request: Job? = null
    private var loadedUser: String? = "__UNINITIALIZED__"
    private var hasInitialLoaded: Boolean = false

    init {
        viewModelScope.launch {
            accountState.collect { state ->
                if (state.auth != AuthState.RESTORING) {
                    if (state.currentId != loadedUser || !hasInitialLoaded) {
                        loadedUser = state.currentId
                        hasInitialLoaded = true
                        refresh()
                    }
                }
            }
        }
    }

    /**
     * 发现页整页下拉刷新：仅在用户于发现页主页主动下拉时触发
     */
    fun refresh() {
        request?.cancel()
        val authState = accountState.value.auth
        if (authState == AuthState.RESTORING) return

        val currentUserId = accountState.value.currentId
        request = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val dailyJob = launch {
                    val daily = runCatching { VipManager.music.dailyRecommend() }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId) _songs.value = daily
                }
                val fmJob = launch {
                    val fm = runCatching { VipManager.music.personalFm(size = 20) }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId && fm.isNotEmpty()) {
                        _personalFmSongs.value = fm
                        _personalFmIndex.value = 0
                    }
                }
                val newSongJob = launch {
                    val period = _selectedNewSongPeriod.value
                    _newSongsLoading.value = true
                    val list = runCatching { VipManager.music.newSongExpress(periodKey = period) }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId) {
                        newSongCache[period] = list
                        _currentNewSongs.value = list
                    }
                    _newSongsLoading.value = false
                }
                val radioJob = launch {
                    val radios = runCatching { VipManager.music.fmChannels() }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId) _fmChannels.value = radios
                }
                val rankJob = launch {
                    val rankList = runCatching { VipManager.music.rankList() }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId) _ranks.value = rankList
                }
                dailyJob.join()
                fmJob.join()
                newSongJob.join()
                radioJob.join()
                rankJob.join()
            } catch (e: Exception) {
                if (accountState.value.currentId == currentUserId) {
                    _error.value = e.message ?: "发现页内容加载失败"
                }
            } finally {
                if (accountState.value.currentId == currentUserId) {
                    _loading.value = false
                }
            }
        }
    }

    /**
     * 严格边界：仅刷新「每日推荐」模块，绝不触发随心听、新歌速递、排行榜或电台重拉
     */
    fun refreshDailyRecommend(onLoaded: (List<Song>) -> Unit = {}) {
        if (_dailyRefreshing.value) return
        viewModelScope.launch {
            _dailyRefreshing.value = true
            val daily = runCatching { VipManager.music.dailyRecommend() }.getOrDefault(emptyList())
            if (daily.isNotEmpty()) {
                _songs.value = daily
            }
            _dailyRefreshing.value = false
            onLoaded(_songs.value)
        }
    }

    /**
     * 严格边界：仅刷新「私人随心听」模块，不影响任何其他模块
     */
    fun refreshPersonalFm(onLoaded: (List<Song>) -> Unit = {}) {
        if (_personalFmRefreshing.value) return
        viewModelScope.launch {
            _personalFmRefreshing.value = true
            val list = runCatching { VipManager.music.personalFm(size = 20) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                _personalFmSongs.value = list
                _personalFmIndex.value = 0
            }
            _personalFmRefreshing.value = false
            onLoaded(_personalFmSongs.value)
        }
    }

    fun setPersonalFmIndex(index: Int) {
        val list = _personalFmSongs.value
        if (list.isNotEmpty()) {
            _personalFmIndex.value = index.coerceIn(0, list.lastIndex)
        }
    }

    fun skipToNextPersonalFm(onPlay: (Song, List<Song>) -> Unit) {
        val list = _personalFmSongs.value
        if (list.isEmpty()) {
            refreshPersonalFm { loaded ->
                if (loaded.isNotEmpty()) {
                    _personalFmIndex.value = 0
                    onPlay(loaded.first(), loaded)
                }
            }
            return
        }
        val nextIdx = _personalFmIndex.value + 1
        if (nextIdx < list.size) {
            _personalFmIndex.value = nextIdx
            onPlay(list[nextIdx], list)
        } else {
            refreshPersonalFm { loaded ->
                if (loaded.isNotEmpty()) {
                    _personalFmIndex.value = 0
                    onPlay(loaded.first(), loaded)
                }
            }
        }
    }

    /**
     * 严格边界：仅切换或刷新「新歌速递」当前选中分类
     */
    fun selectNewSongPeriod(periodKey: String, forceRefresh: Boolean = false) {
        _selectedNewSongPeriod.value = periodKey
        val cached = newSongCache[periodKey]
        if (!forceRefresh && !cached.isNullOrEmpty()) {
            _currentNewSongs.value = cached
            return
        }
        viewModelScope.launch {
            _newSongsLoading.value = true
            val list = runCatching { VipManager.music.newSongExpress(periodKey = periodKey) }.getOrDefault(emptyList())
            newSongCache[periodKey] = list
            if (_selectedNewSongPeriod.value == periodKey) {
                _currentNewSongs.value = list
            }
            _newSongsLoading.value = false
        }
    }

    suspend fun getFmChannelSongs(channelId: String, size: Int = 20, forceNextBatch: Boolean = true): List<Song> {
        return VipManager.music.fmSongs(channelId, size = size, forceNextBatch = forceNextBatch)
    }

    suspend fun getSceneRecommendSongs(sceneId: String, size: Int = 20): List<Song> {
        return VipManager.music.sceneRecommendSongs(sceneId, size = size)
    }

    suspend fun getRankSongs(rankId: Int): List<Song> {
        return VipManager.music.rankSongs(rankId, page = 1, pageSize = 100)
    }

    suspend fun getRankSongsAll(rankId: Int, maxSongs: Int = 500): List<Song> {
        return VipManager.music.rankSongsAll(rankId, maxSongs = maxSongs)
    }
}
