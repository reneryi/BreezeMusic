package com.concept.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.concept.music.data.kugou.AuthState
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
    // 每日推荐
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val dailyRecommendSongs: StateFlow<List<Song>> = _songs.asStateFlow()

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
                    val daily = VipManager.music.dailyRecommend()
                    if (accountState.value.currentId == currentUserId) _songs.value = daily
                }
                val rankJob = launch {
                    val rankList = VipManager.music.rankList()
                    if (accountState.value.currentId == currentUserId) _ranks.value = rankList
                }
                dailyJob.join()
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

    suspend fun getRankSongs(rankId: Int): List<Song> {
        return VipManager.music.rankSongs(rankId)
    }
}
