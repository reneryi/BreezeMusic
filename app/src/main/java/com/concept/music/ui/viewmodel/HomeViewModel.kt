package com.concept.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.concept.music.data.kugou.AuthState
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.RankItem
import com.concept.music.data.model.SceneCategory
import com.concept.music.data.model.Song
import com.concept.music.data.model.YouthChannel
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

    // 猜你喜欢 (AI 推荐)
    private val _guessSongs = MutableStateFlow<List<Song>>(emptyList())
    val guessSongs: StateFlow<List<Song>> = _guessSongs.asStateFlow()

    // 排行榜
    private val _ranks = MutableStateFlow<List<RankItem>>(emptyList())
    val ranks: StateFlow<List<RankItem>> = _ranks.asStateFlow()

    // 经典电台频道
    private val _fmChannels = MutableStateFlow<List<FmChannel>>(emptyList())
    val fmChannels: StateFlow<List<FmChannel>> = _fmChannels.asStateFlow()

    // 概念版专属频道 (Youth Channels)
    private val _youthChannels = MutableStateFlow<List<YouthChannel>>(emptyList())
    val youthChannels: StateFlow<List<YouthChannel>> = _youthChannels.asStateFlow()

    // 场景化分类选择
    private val _sceneCategories = MutableStateFlow(
        listOf(
            SceneCategory("ALL", "推荐", "懂你的专属流", "Sparkles"),
            SceneCategory("STUDY", "沉浸学习", "白噪音与心流舱", "Book"),
            SceneCategory("SLEEP", "深夜助眠", "晚安梦游与雨声", "Moon"),
            SceneCategory("WORKOUT", "燃动运动", "高能多巴胺电音", "Bolt"),
            SceneCategory("DRIVE", "自驾旅行", "落日公路慢摇", "Car"),
            SceneCategory("HEALING", "治愈微风", "温柔慢调放空所", "Spa")
        )
    )
    val sceneCategories: StateFlow<List<SceneCategory>> = _sceneCategories.asStateFlow()

    private val _selectedScene = MutableStateFlow("ALL")
    val selectedScene: StateFlow<String> = _selectedScene.asStateFlow()

    // 场景专属推荐歌曲列表
    private val _sceneSongs = MutableStateFlow<List<Song>>(emptyList())
    val sceneSongs: StateFlow<List<Song>> = _sceneSongs.asStateFlow()

    private val _sceneLoading = MutableStateFlow(false)
    val sceneLoading: StateFlow<Boolean> = _sceneLoading.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val accountState = VipManager.accounts.state
    val vipStatus = VipManager.vip.state.map { it.snapshot }

    private var request: Job? = null
    private var sceneJob: Job? = null
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
                // 并发加载每日推荐、猜你喜欢、排行榜、电台与概念版专属频道
                launch {
                    val daily = VipManager.music.dailyRecommend()
                    if (accountState.value.currentId == currentUserId) _songs.value = daily
                }
                launch {
                    val guess = VipManager.music.guessYouLike()
                    if (accountState.value.currentId == currentUserId) _guessSongs.value = guess
                }
                launch {
                    val rankList = VipManager.music.rankList()
                    if (accountState.value.currentId == currentUserId) _ranks.value = rankList
                }
                launch {
                    val channelList = VipManager.music.fmChannels()
                    if (accountState.value.currentId == currentUserId) _fmChannels.value = channelList
                }
                launch {
                    val youthList = VipManager.music.youthChannels()
                    if (accountState.value.currentId == currentUserId) _youthChannels.value = youthList
                }
                // 若当前选中了特定场景，刷新该场景数据
                if (_selectedScene.value != "ALL") {
                    loadSceneSongsInternal(_selectedScene.value)
                }
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

    fun selectScene(sceneId: String) {
        if (_selectedScene.value == sceneId && _sceneSongs.value.isNotEmpty()) return
        _selectedScene.value = sceneId
        if (sceneId == "ALL") {
            _sceneSongs.value = emptyList()
            return
        }
        loadSceneSongsInternal(sceneId)
    }

    private fun loadSceneSongsInternal(sceneId: String) {
        sceneJob?.cancel()
        sceneJob = viewModelScope.launch {
            _sceneLoading.value = true
            try {
                val songs = VipManager.music.sceneRecommendSongs(sceneId, size = 30)
                _sceneSongs.value = songs
            } catch (e: Exception) {
                _sceneSongs.value = emptyList()
            } finally {
                _sceneLoading.value = false
            }
        }
    }

    suspend fun getRankSongs(rankId: Int): List<Song> {
        return VipManager.music.rankSongs(rankId)
    }

    suspend fun getFmSongs(channelId: String): List<Song> {
        return VipManager.music.fmSongs(channelId)
    }

    suspend fun getYouthChannelSongs(channelId: String): List<Song> {
        return VipManager.music.youthChannelSongs(channelId)
    }

    suspend fun getPersonalFmSongs(): List<Song> {
        return VipManager.music.personalFm(size = 30)
    }
}
