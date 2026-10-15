package com.concept.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.concept.music.data.kugou.AuthState
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.RankItem
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

    // [P1-1] 官方私人随心听 (Personal FM)
    private val _personalFmSongs = MutableStateFlow<List<Song>>(emptyList())
    val personalFmSongs: StateFlow<List<Song>> = _personalFmSongs.asStateFlow()

    // [P1-1] 概念版专属频道列表 (Youth Channels)
    private val _youthChannels = MutableStateFlow<List<YouthChannel>>(emptyList())
    val youthChannels: StateFlow<List<YouthChannel>> = _youthChannels.asStateFlow()

    // [P1-1] 实时电台频道列表 (FM Channels)
    private val _fmChannels = MutableStateFlow<List<FmChannel>>(emptyList())
    val fmChannels: StateFlow<List<FmChannel>> = _fmChannels.asStateFlow()

    // 排行榜
    private val _ranks = MutableStateFlow<List<RankItem>>(emptyList())
    val ranks: StateFlow<List<RankItem>> = _ranks.asStateFlow()

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
                    val channels = runCatching { VipManager.music.youthChannels(page = 1, pageSize = 20) }.getOrDefault(emptyList())
                    if (accountState.value.currentId == currentUserId) _youthChannels.value = channels
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

    suspend fun getYouthChannelSongs(channelId: String): List<Song> {
        return VipManager.music.youthChannelSongs(channelId)
    }

    suspend fun getFmChannelSongs(channelId: String): List<Song> {
        return VipManager.music.fmSongs(channelId)
    }

    suspend fun getRankSongs(rankId: Int): List<Song> {
        return VipManager.music.rankSongs(rankId)
    }
}
