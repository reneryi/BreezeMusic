package com.concept.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.concept.music.data.kugou.AuthState
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.RankItem
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class HomeViewModel : ViewModel() {
    // 每日推荐（独立刷新边界）
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val dailyRecommendSongs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _dailyRefreshing = MutableStateFlow(false)
    val dailyRefreshing: StateFlow<Boolean> = _dailyRefreshing.asStateFlow()

    // 官方私人随心听 FM（定位「猜你喜欢」，独立刷新边界，仅刷新随心听自身，绝不盗用日推封面）
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

    private val newSongCache = ConcurrentHashMap<String, List<Song>>()

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
        // 在后台 IO 线程水合本地缓存快照，彻底消除冷启动时主线程 Gson 反序列化引起的首帧卡顿
        viewModelScope.launch(Dispatchers.IO) {
            hydrateFromLocalCache(accountState.value.currentId)
            accountState.collect { state ->
                if (state.auth != AuthState.RESTORING) {
                    if (state.currentId != loadedUser || !hasInitialLoaded) {
                        loadedUser = state.currentId
                        hasInitialLoaded = true
                        hydrateFromLocalCache(state.currentId)
                        refresh(isBackgroundRevalidate = hasAnyContentLoaded())
                    }
                }
            }
        }
    }

    private fun hasAnyContentLoaded(): Boolean {
        return _songs.value.isNotEmpty() ||
            _personalFmSongs.value.isNotEmpty() ||
            _currentNewSongs.value.isNotEmpty() ||
            _ranks.value.isNotEmpty()
    }

    private fun hydrateFromLocalCache(userId: String?) {
        val cache = VipManager.discoverCache
        val cachedDaily = cache.getDailyRecommend(userId).ifEmpty { cache.getDailyRecommend(null) }
        if (cachedDaily.isNotEmpty()) {
            _songs.value = cachedDaily
        }

        val cachedFm = cache.getPersonalFm(userId).ifEmpty { cache.getPersonalFm(null) }
        if (cachedFm.isNotEmpty()) {
            _personalFmSongs.value = cachedFm
        }

        val activePeriod = _selectedNewSongPeriod.value
        val cachedActivePeriod = cache.getNewSongs(activePeriod)
        if (cachedActivePeriod.isNotEmpty() && newSongCache[activePeriod].isNullOrEmpty()) {
            newSongCache[activePeriod] = cachedActivePeriod
        }
        if (_currentNewSongs.value.isEmpty() && !newSongCache[activePeriod].isNullOrEmpty()) {
            _currentNewSongs.value = newSongCache[activePeriod].orEmpty()
        }

        val cachedRanks = cache.getRanks()
        if (_ranks.value.isEmpty() && cachedRanks.isNotEmpty()) {
            _ranks.value = cachedRanks
        }

        val cachedChannels = cache.getFmChannels()
        if (_fmChannels.value.isEmpty() && cachedChannels.isNotEmpty()) {
            _fmChannels.value = cachedChannels
        }
    }

    /**
     * 发现页整页刷新：
     * - 主动下拉刷新 (isBackgroundRevalidate = false)：始终置 _loading = true 并在完成后回调 onFinished
     * - 静默后台更新 (isBackgroundRevalidate = true)：全程在 Dispatchers.IO 深度错峰执行，完全避开刚打开 App 前 1.2s 的动画与交互窗口
     */
    fun refresh(isBackgroundRevalidate: Boolean = false, onFinished: (() -> Unit)? = null) {
        request?.cancel()
        val authState = accountState.value.auth
        if (authState == AuthState.RESTORING) {
            onFinished?.invoke()
            return
        }

        val currentUserId = accountState.value.currentId
        request = viewModelScope.launch(Dispatchers.IO) {
            if (!isBackgroundRevalidate) {
                _loading.value = true
            } else {
                // 若已有本地秒开缓存，彻底避开刚打开 App 前 1200ms 的首屏渲染、JIT 预热与用户首次交互期
                delay(1200L)
            }
            _error.value = null
            try {
                val dailyJob = launch {
                    val daily = runCatching { VipManager.music.dailyRecommend() }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId && daily.isNotEmpty()) {
                        if (_songs.value.map { it.id } != daily.map { it.id }) {
                            _songs.value = daily
                        }
                        VipManager.discoverCache.saveDailyRecommend(currentUserId, daily)
                    }
                }
                val fmJob = launch {
                    if (isBackgroundRevalidate) delay(280L)
                    val fm = runCatching { VipManager.music.personalFm(size = 20) }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId && fm.isNotEmpty()) {
                        val wasEmpty = _personalFmSongs.value.isEmpty()
                        if (wasEmpty || !isBackgroundRevalidate) {
                            _personalFmSongs.value = fm
                            _personalFmIndex.value = 0
                        }
                        VipManager.discoverCache.savePersonalFm(currentUserId, fm)
                    }
                }
                val newSongJob = launch {
                    if (isBackgroundRevalidate) delay(560L)
                    val period = _selectedNewSongPeriod.value
                    if (_currentNewSongs.value.isEmpty()) {
                        _newSongsLoading.value = true
                    }
                    val list = runCatching { VipManager.music.newSongExpress(periodKey = period) }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId && list.isNotEmpty()) {
                        newSongCache[period] = list
                        if (_currentNewSongs.value.map { it.id } != list.map { it.id }) {
                            _currentNewSongs.value = list
                        }
                        VipManager.discoverCache.saveNewSongs(period, list)
                    }
                    _newSongsLoading.value = false
                }
                val radioJob = launch {
                    if (isBackgroundRevalidate) delay(840L)
                    val radios = runCatching { VipManager.music.fmChannels() }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId && radios.isNotEmpty()) {
                        if (_fmChannels.value.map { it.id } != radios.map { it.id }) {
                            _fmChannels.value = radios
                        }
                        VipManager.discoverCache.saveFmChannels(radios)
                    }
                }
                val rankJob = launch {
                    if (isBackgroundRevalidate) delay(1100L)
                    val rankList = runCatching { VipManager.music.rankList() }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId && rankList.isNotEmpty()) {
                        if (_ranks.value.map { it.rankId } != rankList.map { it.rankId }) {
                            _ranks.value = rankList
                        }
                        VipManager.discoverCache.saveRanks(rankList)
                    }
                }
                dailyJob.join()
                fmJob.join()
                newSongJob.join()
                radioJob.join()
                rankJob.join()
            } catch (e: Exception) {
                if (accountState.value.currentId == currentUserId && !hasAnyContentLoaded()) {
                    _error.value = e.message ?: "发现页内容加载失败"
                }
            } finally {
                _loading.value = false
                if (onFinished != null) {
                    withContext(Dispatchers.Main) {
                        onFinished.invoke()
                    }
                }
            }
        }
    }

    /**
     * 严格边界：仅刷新「每日推荐」模块，绝不触发随心听、新歌速递、排行榜或电台重拉
     */
    fun refreshDailyRecommend(onLoaded: (List<Song>) -> Unit = {}) {
        if (_dailyRefreshing.value) return
        val currentUserId = accountState.value.currentId
        viewModelScope.launch(Dispatchers.IO) {
            _dailyRefreshing.value = true
            val daily = runCatching { VipManager.music.dailyRecommend() }.getOrDefault(emptyList())
            if (daily.isNotEmpty()) {
                _songs.value = daily
                VipManager.discoverCache.saveDailyRecommend(currentUserId, daily)
            }
            _dailyRefreshing.value = false
            withContext(Dispatchers.Main) {
                onLoaded(_songs.value)
            }
        }
    }

    /**
     * 严格边界：仅刷新「私人随心听」模块，不影响任何其他模块
     */
    fun refreshPersonalFm(onLoaded: (List<Song>) -> Unit = {}) {
        if (_personalFmRefreshing.value) return
        val currentUserId = accountState.value.currentId
        viewModelScope.launch(Dispatchers.IO) {
            _personalFmRefreshing.value = true
            val list = runCatching { VipManager.music.personalFm(size = 20) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                _personalFmSongs.value = list
                _personalFmIndex.value = 0
                VipManager.discoverCache.savePersonalFm(currentUserId, list)
            }
            _personalFmRefreshing.value = false
            withContext(Dispatchers.Main) {
                onLoaded(_personalFmSongs.value)
            }
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
     * 严格边界：仅切换或刷新「新歌速递」当前选中分类（优先 0ms 展示内存或持久化缓存，后台静默更新）
     */
    fun selectNewSongPeriod(periodKey: String, forceRefresh: Boolean = false) {
        _selectedNewSongPeriod.value = periodKey
        val cached = newSongCache[periodKey] ?: VipManager.discoverCache.getNewSongs(periodKey).also {
            if (it.isNotEmpty()) newSongCache[periodKey] = it
        }
        if (!cached.isNullOrEmpty()) {
            _currentNewSongs.value = cached
            if (!forceRefresh) return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (_currentNewSongs.value.isEmpty() || forceRefresh) {
                _newSongsLoading.value = true
            }
            val list = runCatching { VipManager.music.newSongExpress(periodKey = periodKey) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                newSongCache[periodKey] = list
                VipManager.discoverCache.saveNewSongs(periodKey, list)
                if (_selectedNewSongPeriod.value == periodKey) {
                    _currentNewSongs.value = list
                }
            }
            _newSongsLoading.value = false
        }
    }

    fun getCachedFmChannelSongs(channelId: String): List<Song> {
        return VipManager.discoverCache.getFmChannelSongs(channelId)
    }

    suspend fun getFmChannelSongs(channelId: String, size: Int = 20, forceNextBatch: Boolean = true): List<Song> = withContext(Dispatchers.IO) {
        if (!forceNextBatch) {
            val cached = VipManager.discoverCache.getFmChannelSongs(channelId)
            if (cached.isNotEmpty()) return@withContext cached
        }
        val fresh = VipManager.music.fmSongs(channelId, size = size, forceNextBatch = forceNextBatch)
        if (fresh.isNotEmpty()) {
            VipManager.discoverCache.saveFmChannelSongs(channelId, fresh)
        }
        fresh
    }

    suspend fun getSceneRecommendSongs(sceneId: String, size: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        VipManager.music.sceneRecommendSongs(sceneId, size = size)
    }

    fun getCachedRankSongs(rankId: Int): List<Song> {
        return VipManager.discoverCache.getRankSongs(rankId)
    }

    suspend fun getRankSongs(rankId: Int): List<Song> = withContext(Dispatchers.IO) {
        val list = VipManager.music.rankSongs(rankId, page = 1, pageSize = 100)
        if (list.isNotEmpty()) {
            VipManager.discoverCache.saveRankSongs(rankId, list)
        }
        list
    }

    suspend fun getRankSongsAll(rankId: Int, maxSongs: Int = 500): List<Song> = withContext(Dispatchers.IO) {
        val list = VipManager.music.rankSongsAll(rankId, maxSongs = maxSongs)
        if (list.isNotEmpty()) {
            VipManager.discoverCache.saveRankSongs(rankId, list)
        }
        list
    }
}
