package com.concept.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.concept.music.data.model.AlbumItem
import com.concept.music.data.model.ArtistItem
import com.concept.music.data.model.ComplexSearchResult
import com.concept.music.data.model.PlaylistItem
import com.concept.music.data.model.SearchTab
import com.concept.music.data.model.Song
import com.concept.music.data.repository.SearchHistoryRepository
import com.concept.music.data.vip.VipManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val historyRepo: SearchHistoryRepository = VipManager.searchHistoryRepo
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _selectedTab = MutableStateFlow(SearchTab.COMPLEX)
    val selectedTab = _selectedTab.asStateFlow()

    private val _hotKeywords = MutableStateFlow<List<String>>(emptyList())
    val hotKeywords = _hotKeywords.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(historyRepo.get())
    val searchHistory = _searchHistory.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    // 搜索执行状态追踪（用于区分未搜索状态与0条匹配的空结果状态）
    private val _hasSearched = MutableStateFlow(false)
    val hasSearched = _hasSearched.asStateFlow()

    private val _lastSearchedKeyword = MutableStateFlow("")
    val lastSearchedKeyword = _lastSearchedKeyword.asStateFlow()

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
    private var lastComplexKeyword = ""
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        // 不在初始化时无谓触发已被页面移除的热搜请求
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
            delay(180)
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
        val currentKw = _query.value.trim()
        if (currentKw.isNotBlank()) {
            // 若刚刚在综合页搜索过同一关键词，直接复用已缓存结果实现 0ms 秒切，并在后台静默补齐更多分页数据
            val hasWarmCache = lastComplexKeyword == currentKw && when (tab) {
                SearchTab.COMPLEX -> _complexResult.value.songs.isNotEmpty() || _complexResult.value.artists.isNotEmpty()
                SearchTab.SONG -> _songResults.value.isNotEmpty()
                SearchTab.ALBUM -> _albumResults.value.isNotEmpty()
                SearchTab.PLAYLIST -> _playlistResults.value.isNotEmpty()
            }
            if (hasWarmCache) {
                currentPage = 1
                _hasMore.value = tab != SearchTab.COMPLEX
                if (tab == SearchTab.SONG && _songResults.value.size < pageSize) {
                    performSearchForCurrentTab(currentKw, silent = true)
                } else if (tab == SearchTab.ALBUM && _albumResults.value.size < pageSize) {
                    performSearchForCurrentTab(currentKw, silent = true)
                } else if (tab == SearchTab.PLAYLIST && _playlistResults.value.size < pageSize) {
                    performSearchForCurrentTab(currentKw, silent = true)
                }
            } else {
                performSearchForCurrentTab(currentKw, silent = false)
            }
        }
    }

    fun resetTabToDefault() {
        _selectedTab.value = SearchTab.COMPLEX
    }

    fun search(targetQuery: String = _query.value) {
        val trimmed = targetQuery.trim()
        if (trimmed.isBlank()) return
        suggestJob?.cancel()
        _suggestions.value = emptyList()
        _query.value = trimmed
        _lastSearchedKeyword.value = trimmed
        // 每次发起新搜索时始终默认回归「综合」结果页，避免残留上一次的专辑/歌单分类记忆
        _selectedTab.value = SearchTab.COMPLEX
        _hasSearched.value = true

        // 加入历史记录并持久化
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(trimmed)
        currentHistory.add(0, trimmed)
        val updated = currentHistory.take(15)
        _searchHistory.value = updated
        historyRepo.save(updated)

        performSearchForCurrentTab(trimmed, silent = false)
    }

    private fun performSearchForCurrentTab(keyword: String, silent: Boolean = false) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        currentPage = 1

        searchJob = viewModelScope.launch {
            if (!silent) _loading.value = true
            try {
                when (_selectedTab.value) {
                    SearchTab.COMPLEX -> {
                        val res = VipManager.music.searchComplex(keyword)
                        lastComplexKeyword = keyword
                        _complexResult.value = res
                        _songResults.value = res.songs
                        _albumResults.value = res.albums
                        _playlistResults.value = res.playlists
                        _artistResults.value = res.artists
                        _hasMore.value = false
                    }
                    SearchTab.SONG -> {
                        val res = VipManager.music.searchSongs(keyword, page = 1, pageSize = pageSize)
                        _songResults.value = res.songs
                        _hasMore.value = res.hasMore || res.songs.size >= pageSize
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
                            _hasMore.value = res.hasMore || res.songs.size >= pageSize
                        } else {
                            _hasMore.value = false
                        }
                    }
                    SearchTab.ALBUM -> {
                        val res = VipManager.music.searchAlbums(currentQuery, page = nextPage, pageSize = pageSize)
                        if (res.isNotEmpty()) {
                            currentPage = nextPage
                            val existingIds = _albumResults.value.map { it.id }.toSet()
                            _albumResults.value = _albumResults.value + res.filter { it.id !in existingIds }
                            _hasMore.value = res.size >= pageSize
                        } else _hasMore.value = false
                    }
                    SearchTab.PLAYLIST -> {
                        val res = VipManager.music.searchPlaylists(currentQuery, page = nextPage, pageSize = pageSize)
                        if (res.isNotEmpty()) {
                            currentPage = nextPage
                            val existingIds = _playlistResults.value.map { it.id }.toSet()
                            _playlistResults.value = _playlistResults.value + res.filter { it.id !in existingIds }
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

    fun dismissSuggestions() {
        suggestJob?.cancel()
        _suggestions.value = emptyList()
    }

    fun clearHistory() {
        _searchHistory.value = emptyList()
        historyRepo.clear()
    }

    fun removeHistoryItem(item: String) {
        val updated = _searchHistory.value.filter { it != item }
        _searchHistory.value = updated
        historyRepo.save(updated)
    }

    fun clearQuery() {
        suggestJob?.cancel()
        _query.value = ""
        lastComplexKeyword = ""
        _lastSearchedKeyword.value = ""
        _selectedTab.value = SearchTab.COMPLEX
        _hasSearched.value = false
        _suggestions.value = emptyList()
        _complexResult.value = ComplexSearchResult()
        _songResults.value = emptyList()
        _albumResults.value = emptyList()
        _playlistResults.value = emptyList()
        _artistResults.value = emptyList()
        _hasMore.value = false
    }
}
