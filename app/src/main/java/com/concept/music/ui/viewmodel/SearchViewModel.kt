package com.concept.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.concept.music.data.model.AlbumItem
import com.concept.music.data.model.ArtistItem
import com.concept.music.data.model.ComplexSearchResult
import com.concept.music.data.model.PlaylistItem
import com.concept.music.data.model.SearchTab
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _selectedTab = MutableStateFlow(SearchTab.COMPLEX)
    val selectedTab = _selectedTab.asStateFlow()

    private val _hotKeywords = MutableStateFlow<List<String>>(emptyList())
    val hotKeywords = _hotKeywords.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(listOf("周杰伦", "林俊杰", "告五人"))
    val searchHistory = _searchHistory.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    // 搜索结果
    private val _complexResult = MutableStateFlow(ComplexSearchResult())
    val complexResult = _complexResult.asStateFlow()

    private val _songResults = MutableStateFlow<List<Song>>(emptyList())
    val songResults = _songResults.asStateFlow()

    private val _albumResults = MutableStateFlow<List<AlbumItem>>(emptyList())
    val albumResults = _albumResults.asStateFlow()

    private val _playlistResults = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlistResults = _playlistResults.asStateFlow()

    private val _artistResults = MutableStateFlow<List<ArtistItem>>(emptyList())
    val artistResults = _artistResults.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    private var currentPage = 1
    private val pageSize = 30
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        loadHotKeywords()
    }

    fun loadHotKeywords() {
        viewModelScope.launch {
            try {
                _hotKeywords.value = VipManager.music.searchHot()
            } catch (_: Exception) {
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        suggestJob?.cancel()
        if (newQuery.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            try {
                _suggestions.value = VipManager.music.searchSuggest(newQuery)
            } catch (_: Exception) {
                _suggestions.value = emptyList()
            }
        }
    }

    fun selectTab(tab: SearchTab) {
        if (_selectedTab.value == tab) return
        _selectedTab.value = tab
        if (_query.value.isNotBlank()) {
            performSearchForCurrentTab(_query.value)
        }
    }

    fun search(targetQuery: String = _query.value) {
        val trimmed = targetQuery.trim()
        if (trimmed.isBlank()) return
        _query.value = trimmed
        _suggestions.value = emptyList()

        // 加入历史记录
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(trimmed)
        currentHistory.add(0, trimmed)
        _searchHistory.value = currentHistory.take(15)

        performSearchForCurrentTab(trimmed)
    }

    private fun performSearchForCurrentTab(keyword: String) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        currentPage = 1

        searchJob = viewModelScope.launch {
            _loading.value = true
            try {
                when (_selectedTab.value) {
                    SearchTab.COMPLEX -> {
                        val res = VipManager.music.searchComplex(keyword)
                        _complexResult.value = res
                        _songResults.value = res.songs
                        _hasMore.value = false
                    }
                    SearchTab.SONG -> {
                        val res = VipManager.music.searchSongs(keyword, page = 1, pageSize = pageSize)
                        _songResults.value = res.songs
                        _hasMore.value = res.hasMore
                    }
                    SearchTab.ALBUM -> {
                        val res = VipManager.music.searchAlbums(keyword, page = 1, pageSize = pageSize)
                        _albumResults.value = res
                        _hasMore.value = res.size >= pageSize
                    }
                    SearchTab.PLAYLIST -> {
                        val res = VipManager.music.searchPlaylists(keyword, page = 1, pageSize = pageSize)
                        _playlistResults.value = res
                        _hasMore.value = res.size >= pageSize
                    }
                    SearchTab.ARTIST -> {
                        val res = VipManager.music.searchArtists(keyword, page = 1, pageSize = pageSize)
                        _artistResults.value = res
                        _hasMore.value = res.size >= pageSize
                    }
                }
            } catch (_: Exception) {
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadMore() {
        val currentQuery = _query.value.trim()
        if (currentQuery.isBlank() || _loading.value || _loadingMore.value || !_hasMore.value) return
        if (_selectedTab.value == SearchTab.COMPLEX) return

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _loadingMore.value = true
            val nextPage = currentPage + 1
            try {
                when (_selectedTab.value) {
                    SearchTab.SONG -> {
                        val res = VipManager.music.searchSongs(currentQuery, page = nextPage, pageSize = pageSize)
                        if (res.songs.isNotEmpty()) {
                            currentPage = nextPage
                            val existingHashes = _songResults.value.map { it.hash.lowercase() }.toSet()
                            _songResults.value = _songResults.value + res.songs.filter { it.hash.lowercase() !in existingHashes }
                            _hasMore.value = res.hasMore
                        } else {
                            _hasMore.value = false
                        }
                    }
                    SearchTab.ALBUM -> {
                        val res = VipManager.music.searchAlbums(currentQuery, page = nextPage, pageSize = pageSize)
                        if (res.isNotEmpty()) {
                            currentPage = nextPage
                            _albumResults.value = _albumResults.value + res
                            _hasMore.value = res.size >= pageSize
                        } else _hasMore.value = false
                    }
                    SearchTab.PLAYLIST -> {
                        val res = VipManager.music.searchPlaylists(currentQuery, page = nextPage, pageSize = pageSize)
                        if (res.isNotEmpty()) {
                            currentPage = nextPage
                            _playlistResults.value = _playlistResults.value + res
                            _hasMore.value = res.size >= pageSize
                        } else _hasMore.value = false
                    }
                    SearchTab.ARTIST -> {
                        val res = VipManager.music.searchArtists(currentQuery, page = nextPage, pageSize = pageSize)
                        if (res.isNotEmpty()) {
                            currentPage = nextPage
                            _artistResults.value = _artistResults.value + res
                            _hasMore.value = res.size >= pageSize
                        } else _hasMore.value = false
                    }
                    else -> {}
                }
            } catch (_: Exception) {
            } finally {
                _loadingMore.value = false
            }
        }
    }

    fun clearHistory() {
        _searchHistory.value = emptyList()
    }

    fun removeHistoryItem(item: String) {
        _searchHistory.value = _searchHistory.value.filter { it != item }
    }

    fun clearQuery() {
        _query.value = ""
        _suggestions.value = emptyList()
        _complexResult.value = ComplexSearchResult()
        _songResults.value = emptyList()
        _albumResults.value = emptyList()
        _playlistResults.value = emptyList()
        _artistResults.value = emptyList()
        _hasMore.value = false
    }
}
