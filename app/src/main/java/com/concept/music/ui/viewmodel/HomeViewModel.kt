package com.concept.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.concept.music.data.kugou.AuthState
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.MusicStoryItem
import com.concept.music.data.model.RankItem
import com.concept.music.data.model.Song
import com.concept.music.data.model.YouthChannel
import com.concept.music.data.model.YouthChannelAmway
import com.concept.music.data.vip.VipManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    // 每日推荐
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val dailyRecommendSongs: StateFlow<List<Song>> = _songs.asStateFlow()

    // 官方私人随心听 (Personal FM)
    private val _personalFmSongs = MutableStateFlow<List<Song>>(emptyList())
    val personalFmSongs: StateFlow<List<Song>> = _personalFmSongs.asStateFlow()

    // 概念版专属频道列表 (Youth Channels - 获取用户所有频道)
    private val _youthChannels = MutableStateFlow<List<YouthChannel>>(emptyList())
    val youthChannels: StateFlow<List<YouthChannel>> = _youthChannels.asStateFlow()

    // 概念版频道安利流 (Youth Channel Amway)
    private val _youthChannelAmways = MutableStateFlow<List<YouthChannelAmway>>(emptyList())
    val youthChannelAmways: StateFlow<List<YouthChannelAmway>> = _youthChannelAmways.asStateFlow()

    // 实时电台频道列表 (FM Channels)
    private val _fmChannels = MutableStateFlow<List<FmChannel>>(emptyList())
    val fmChannels: StateFlow<List<FmChannel>> = _fmChannels.asStateFlow()

    // 排行榜
    private val _ranks = MutableStateFlow<List<RankItem>>(emptyList())
    val ranks: StateFlow<List<RankItem>> = _ranks.asStateFlow()

    // 频道分页游标缓存（每次刷新频道歌曲时向前推进）
    private val youthChannelPageCursor = mutableMapOf<String, Int>()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val accountState = VipManager.accounts.state
    val vipStatus = VipManager.vip.state.map { it.snapshot }

    private var request: Job? = null
    private var loadedUser: String? = null

    init {
        viewModelScope.launch {
            accountState.collect { state ->
                if (state.auth != AuthState.RESTORING) {
                    if (state.currentId != loadedUser || _songs.value.isEmpty()) {
                        loadedUser = state.currentId
                        refresh()
                    }
                }
            }
        }
    }

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
                    if (accountState.value.currentId == currentUserId) _personalFmSongs.value = fm
                }
                val youthJob = launch {
                    val channels = runCatching { VipManager.music.youthChannels(page = 1, pageSize = 24) }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId) _youthChannels.value = channels
                }
                val amwayJob = launch {
                    val amways = runCatching { VipManager.music.youthChannelAmway() }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId) _youthChannelAmways.value = amways
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
                youthJob.join()
                amwayJob.join()
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

    fun refreshPersonalFm(onLoaded: (List<Song>) -> Unit = {}) {
        viewModelScope.launch {
            val list = runCatching { VipManager.music.personalFm(size = 20) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                _personalFmSongs.value = list
            }
            onLoaded(_personalFmSongs.value)
        }
    }

    fun refreshYouthChannelAmways(channelId: String = "0") {
        viewModelScope.launch {
            val amways = runCatching { VipManager.music.youthChannelAmway(channelId) }.getOrDefault(emptyList())
            if (amways.isNotEmpty()) {
                _youthChannelAmways.value = amways
            }
        }
    }

    // 1. 频道 - 获取用户所有频道
    fun refreshYouthChannels() {
        viewModelScope.launch {
            val channels = runCatching { VipManager.music.youthChannels(page = 1, pageSize = 24) }.getOrDefault(emptyList())
            if (channels.isNotEmpty()) {
                _youthChannels.value = channels
            }
        }
    }

    // 2. 频道 - 详情
    suspend fun getYouthChannelDetail(channelId: String): YouthChannel? {
        val fetched = runCatching { VipManager.music.youthChannelDetail(channelId) }.getOrNull()
        val cached = _youthChannels.value.firstOrNull { it.id == channelId }
        return if (fetched != null) {
            val merged = fetched.copy(
                name = fetched.name.ifBlank { cached?.name.orEmpty() },
                description = fetched.description.ifBlank { cached?.description.orEmpty() },
                coverUrl = fetched.coverUrl.ifBlank { cached?.coverUrl.orEmpty() },
                bannerUrl = fetched.bannerUrl.ifBlank { cached?.bannerUrl.orEmpty() },
                tag = fetched.tag.ifBlank { cached?.tag ?: "概念频道" },
                isSubscribed = fetched.isSubscribed || (cached?.isSubscribed == true)
            )
            _youthChannels.value = _youthChannels.value.map { if (it.id == channelId) merged else it }
                .let { current -> if (current.any { it.id == channelId }) current else current + listOf(merged) }
            merged
        } else {
            cached
        }
    }

    // 3. 频道 - 频道安利
    suspend fun getYouthChannelAmwayForChannel(channelId: String): List<YouthChannelAmway> {
        return runCatching { VipManager.music.youthChannelAmway(channelId) }.getOrDefault(emptyList())
    }

    // 4. 频道 - 相似频道
    suspend fun getYouthChannelSimilar(channelId: String): List<YouthChannel> {
        return runCatching { VipManager.music.youthChannelSimilar(channelId) }.getOrDefault(emptyList())
    }

    // 5. 频道 - 订阅 / 取消订阅
    fun toggleSubscribeYouthChannel(
        channel: YouthChannel,
        onResult: (Boolean, Boolean, String) -> Unit = { _, _, _ -> }
    ) {
        viewModelScope.launch {
            val targetSubscribe = !channel.isSubscribed
            val prevChannels = _youthChannels.value
            _youthChannels.value = prevChannels.map {
                if (it.id == channel.id) {
                    it.copy(
                        isSubscribed = targetSubscribe,
                        subscribeCount = (it.subscribeCount + if (targetSubscribe) 1 else -1).coerceAtLeast(0)
                    )
                } else it
            }
            val ok = runCatching { VipManager.music.youthChannelSubscribe(channel.id, subscribe = targetSubscribe) }.getOrDefault(false)
            if (ok) {
                val msg = if (targetSubscribe) "已订阅频道「${channel.name}」" else "已取消订阅「${channel.name}」"
                onResult(true, targetSubscribe, msg)
            } else {
                _youthChannels.value = prevChannels
                onResult(false, channel.isSubscribed, "频道订阅状态更新失败，请稍后重试")
            }
        }
    }

    // 6. 频道 - 音乐故事
    suspend fun getYouthChannelStories(channelId: String, page: Int = 1, pageSize: Int = 12): List<MusicStoryItem> {
        return runCatching { VipManager.music.youthChannelStories(channelId, page = page, pageSize = pageSize) }.getOrDefault(emptyList())
    }

    // 7. 频道 - 音乐故事详情
    suspend fun getYouthChannelStoryDetail(storyId: String, channelId: String = ""): MusicStoryItem? {
        return runCatching { VipManager.music.youthChannelStoryDetail(storyId, channelId) }.getOrNull()
    }

    suspend fun getYouthChannelSongs(channelId: String, forceNextPage: Boolean = false): List<Song> {
        val currentPage = synchronized(youthChannelPageCursor) {
            val cur = youthChannelPageCursor[channelId] ?: 1
            val next = if (forceNextPage) (cur % 6) + 1 else cur
            youthChannelPageCursor[channelId] = next
            next
        }
        return VipManager.music.youthChannelSongs(channelId, page = currentPage)
    }

    suspend fun getFmChannelSongs(channelId: String, forceNextBatch: Boolean = true): List<Song> {
        return VipManager.music.fmSongs(channelId, forceNextBatch = forceNextBatch)
    }

    suspend fun getRankSongs(rankId: Int): List<Song> {
        return VipManager.music.rankSongs(rankId, page = 1, pageSize = 100)
    }

    suspend fun getRankSongsAll(rankId: Int, maxSongs: Int = 500): List<Song> {
        val maxPages = (maxSongs + 99) / 100
        return VipManager.music.rankSongsAll(rankId, maxPages = maxPages)
    }
}
