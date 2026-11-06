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
    private val rankSongsCache = ConcurrentHashMap<Int, List<Song>>()
    private val fmChannelSongsCache = ConcurrentHashMap<String, List<Song>>()
    private val refreshedRanksInSession = ConcurrentHashMap.newKeySet<Int>()
    private val refreshedFmChannelsInSession = ConcurrentHashMap.newKeySet<String>()

    companion object {
        private val ALL_NEW_SONG_PERIOD_KEYS = listOf("CHINESE", "WESTERN", "JPKR", "CANTON")
    }

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
    private var silentPreloadJob: Job? = null
    private var loadedUser: String? = "__UNINITIALIZED__"
    private var hasInitialLoaded: Boolean = false

    init {
        // 开屏期间在后台 IO 线程立即水合本地快照并同步启动静默网络预加载，使开屏结束进入首页时数据已就绪
        viewModelScope.launch(Dispatchers.IO) {
            val initialUserId = accountState.value.currentId
            hydrateFromLocalCache(initialUserId)
            loadedUser = initialUserId
            hasInitialLoaded = true
            refresh(isBackgroundRevalidate = hasAnyContentLoaded(), allowWhileRestoring = true)

            accountState.collect { state ->
                if (state.auth != AuthState.RESTORING) {
                    if (state.currentId != loadedUser || !hasInitialLoaded) {
                        loadedUser = state.currentId
                        hasInitialLoaded = true
                        hydrateFromLocalCache(state.currentId)
                        refresh(isBackgroundRevalidate = hasAnyContentLoaded(), allowWhileRestoring = false)
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

        for (periodKey in ALL_NEW_SONG_PERIOD_KEYS) {
            val cachedPeriod = cache.getNewSongs(periodKey)
            if (cachedPeriod.isNotEmpty() && newSongCache[periodKey].isNullOrEmpty()) {
                newSongCache[periodKey] = cachedPeriod
            }
        }
        val activePeriod = _selectedNewSongPeriod.value
        if (_currentNewSongs.value.isEmpty() && !newSongCache[activePeriod].isNullOrEmpty()) {
            _currentNewSongs.value = newSongCache[activePeriod].orEmpty()
        }

        val cachedRanks = cache.getRanks()
        if (_ranks.value.isEmpty() && cachedRanks.isNotEmpty()) {
            _ranks.value = cachedRanks
        }
        for (rank in _ranks.value) {
            val cachedSongs = cache.getRankSongs(rank.rankId)
            if (cachedSongs.isNotEmpty() && rankSongsCache[rank.rankId].isNullOrEmpty()) {
                rankSongsCache[rank.rankId] = cachedSongs
            }
        }

        val cachedChannels = cache.getFmChannels()
        if (_fmChannels.value.isEmpty() && cachedChannels.isNotEmpty()) {
            _fmChannels.value = cachedChannels
        }
        for (ch in _fmChannels.value) {
            val cachedChSongs = cache.getFmChannelSongs(ch.id)
            if (cachedChSongs.isNotEmpty() && fmChannelSongsCache[ch.id].isNullOrEmpty()) {
                fmChannelSongsCache[ch.id] = cachedChSongs
            }
        }
    }

    /**
     * 发现页整页刷新：
     * - 主动下拉刷新 (isBackgroundRevalidate = false)：始终置 _loading = true 并在完成后回调 onFinished
     * - 静默后台更新 (isBackgroundRevalidate = true)：全程在 Dispatchers.IO 深度错峰执行，完全避开刚打开 App 前 1.2s 的动画与交互窗口
     */
    fun refresh(
        isBackgroundRevalidate: Boolean = false,
        allowWhileRestoring: Boolean = false,
        onFinished: (() -> Unit)? = null
    ) {
        request?.cancel()
        val authState = accountState.value.auth
        if (authState == AuthState.RESTORING && !allowWhileRestoring) {
            onFinished?.invoke()
            return
        }

        val currentUserId = accountState.value.currentId
        request = viewModelScope.launch(Dispatchers.IO) {
            if (!isBackgroundRevalidate) {
                _loading.value = true
            } else {
                // 开屏展示期间（约 60ms 后）立即在 IO 线程池并发启动静默预加载，无需等待 1200ms
                delay(60L)
            }
            _error.value = null
            try {
                val dailyJob = launch {
                    val daily = runCatching { VipManager.music.dailyRecommend() }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId && daily.isNotEmpty()) {
                        if (_songs.value != daily) {
                            _songs.value = daily
                        }
                        VipManager.discoverCache.saveDailyRecommend(currentUserId, daily)
                    }
                }
                val fmJob = launch {
                    if (isBackgroundRevalidate) delay(30L)
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
                    if (isBackgroundRevalidate) delay(60L)
                    val period = _selectedNewSongPeriod.value
                    if (_currentNewSongs.value.isEmpty()) {
                        _newSongsLoading.value = true
                    }
                    val list = runCatching { VipManager.music.newSongExpress(periodKey = period) }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId && list.isNotEmpty()) {
                        newSongCache[period] = list
                        if (_currentNewSongs.value != list) {
                            _currentNewSongs.value = list
                        }
                        VipManager.discoverCache.saveNewSongs(period, list)
                    }
                    _newSongsLoading.value = false
                }
                val radioJob = launch {
                    if (isBackgroundRevalidate) delay(90L)
                    val radios = runCatching { VipManager.music.fmChannels() }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId && radios.isNotEmpty()) {
                        if (_fmChannels.value.map { it.id } != radios.map { it.id }) {
                            _fmChannels.value = radios
                        }
                        VipManager.discoverCache.saveFmChannels(radios)
                    }
                }
                val rankJob = launch {
                    if (isBackgroundRevalidate) delay(120L)
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
                scheduleStaggeredSilentPreload(currentUserId)
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

    /**
     * 启动进入首页后错峰逐项静默预加载全量板块（单并发队列 + 帧间间隔延迟）：
     * 1. 依次静默刷新新歌速递其余语种 Tag（欧美、日韩、粤语等）
     * 2. 依次静默刷新所有巅峰排行榜的实时曲目与卡片前三名预览，不再仅刷新前两个榜单
     * 3. 依次静默刷新所有精选场景电台的实时曲目流与真实封面，不再仅刷新前两个电台
     * 全程在 Dispatchers.IO 单纤程串行加 180ms 呼吸间隔执行，不抢占首屏带宽与主线程渲染帧率。
     */
    private fun scheduleStaggeredSilentPreload(expectedUserId: String?) {
        silentPreloadJob?.cancel()
        silentPreloadJob = viewModelScope.launch(Dispatchers.IO) {
            delay(480L)
            if (accountState.value.currentId != expectedUserId) return@launch

            // 1. 逐项静默刷新「新歌速递」其余语种分类
            val initialPeriod = _selectedNewSongPeriod.value
            for (periodKey in ALL_NEW_SONG_PERIOD_KEYS) {
                if (accountState.value.currentId != expectedUserId) return@launch
                if (periodKey == initialPeriod && !newSongCache[periodKey].isNullOrEmpty()) continue
                val songs = runCatching { VipManager.music.newSongExpress(periodKey = periodKey) }.getOrDefault(emptyList())
                if (songs.isNotEmpty() && accountState.value.currentId == expectedUserId) {
                    newSongCache[periodKey] = songs
                    VipManager.discoverCache.saveNewSongs(periodKey, songs)
                    if (_selectedNewSongPeriod.value == periodKey && _currentNewSongs.value != songs) {
                        _currentNewSongs.value = songs
                    }
                }
                delay(180L)
            }

            // 2. 逐项静默刷新全部「巅峰排行榜」曲目与卡片预览
            val currentRanks = _ranks.value
            for (rank in currentRanks) {
                if (accountState.value.currentId != expectedUserId) return@launch
                val rankSongs = runCatching {
                    VipManager.music.rankSongs(rank.rankId, page = 1, pageSize = 100)
                }.getOrDefault(emptyList())
                if (rankSongs.isNotEmpty() && accountState.value.currentId == expectedUserId) {
                    rankSongsCache[rank.rankId] = rankSongs
                    refreshedRanksInSession.add(rank.rankId)
                    VipManager.discoverCache.saveRankSongs(rank.rankId, rankSongs)
                    val previewLines = rankSongs.take(3).mapIndexed { i, s -> "${i + 1}. ${s.title} - ${s.artist}" }
                    val liveCover = rankSongs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty()
                    val updatedList = _ranks.value.map { item ->
                        if (item.rankId == rank.rankId) {
                            item.copy(
                                topSongsPreview = if (previewLines.isNotEmpty()) previewLines else item.topSongsPreview,
                                coverUrl = if (liveCover.isNotBlank() && (item.coverUrl.isBlank() || item.coverUrl.contains("20210729"))) liveCover else item.coverUrl
                            )
                        } else item
                    }
                    if (_ranks.value != updatedList) {
                        _ranks.value = updatedList
                        VipManager.discoverCache.saveRanks(updatedList)
                    }
                }
                delay(180L)
            }

            // 3. 逐项静默刷新全部「精选场景电台」曲目与封面
            val currentChannels = _fmChannels.value
            for (channel in currentChannels) {
                if (accountState.value.currentId != expectedUserId) return@launch
                val chSongs = runCatching {
                    VipManager.music.fmSongs(channel.id, size = 20, forceNextBatch = true)
                }.getOrDefault(emptyList())
                if (chSongs.isNotEmpty() && accountState.value.currentId == expectedUserId) {
                    fmChannelSongsCache[channel.id] = chSongs
                    refreshedFmChannelsInSession.add(channel.id)
                    VipManager.discoverCache.saveFmChannelSongs(channel.id, chSongs)
                    val liveCover = chSongs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty()
                    if (liveCover.isNotBlank() && (channel.coverUrl.isBlank() || channel.coverUrl.contains("20210519"))) {
                        val updatedChannels = _fmChannels.value.map { item ->
                            if (item.id == channel.id) item.copy(coverUrl = liveCover) else item
                        }
                        if (_fmChannels.value != updatedChannels) {
                            _fmChannels.value = updatedChannels
                            VipManager.discoverCache.saveFmChannels(updatedChannels)
                        }
                    }
                }
                delay(180L)
            }
        }
    }

    fun getCachedFmChannelSongs(channelId: String): List<Song> {
        return fmChannelSongsCache[channelId]?.takeIf { it.isNotEmpty() }
            ?: VipManager.discoverCache.getFmChannelSongs(channelId)
    }

    suspend fun getFmChannelSongs(channelId: String, size: Int = 20, forceNextBatch: Boolean = true): List<Song> = withContext(Dispatchers.IO) {
        if (!forceNextBatch && refreshedFmChannelsInSession.contains(channelId)) {
            val cached = getCachedFmChannelSongs(channelId)
            if (cached.isNotEmpty()) return@withContext cached
        }
        val fresh = VipManager.music.fmSongs(channelId, size = size, forceNextBatch = true)
        if (fresh.isNotEmpty()) {
            fmChannelSongsCache[channelId] = fresh
            refreshedFmChannelsInSession.add(channelId)
            VipManager.discoverCache.saveFmChannelSongs(channelId, fresh)
        }
        fresh
    }

    suspend fun getSceneRecommendSongs(sceneId: String, size: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        VipManager.music.sceneRecommendSongs(sceneId, size = size)
    }

    fun getCachedRankSongs(rankId: Int): List<Song> {
        return rankSongsCache[rankId]?.takeIf { it.isNotEmpty() }
            ?: VipManager.discoverCache.getRankSongs(rankId)
    }

    suspend fun getRankSongs(rankId: Int): List<Song> = withContext(Dispatchers.IO) {
        val list = VipManager.music.rankSongs(rankId, page = 1, pageSize = 100)
        if (list.isNotEmpty()) {
            rankSongsCache[rankId] = list
            refreshedRanksInSession.add(rankId)
            VipManager.discoverCache.saveRankSongs(rankId, list)
        }
        list
    }

    suspend fun getRankSongsAll(
        rankId: Int,
        maxSongs: Int = 500,
        onProgress: ((List<Song>) -> Unit)? = null
    ): List<Song> = withContext(Dispatchers.IO) {
        val list = VipManager.music.rankSongsAll(
            rankId = rankId,
            maxSongs = maxSongs,
            pageSize = 100,
            onProgress = onProgress
        )
        if (list.isNotEmpty()) {
            rankSongsCache[rankId] = list
            refreshedRanksInSession.add(rankId)
            VipManager.discoverCache.saveRankSongs(rankId, list)
        }
        list
    }
}
