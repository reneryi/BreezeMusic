package com.concept.music

import com.concept.music.data.kugou.*
import com.concept.music.data.model.*
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.PlayerUiState
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UiAlignmentRegressionTest {

    @Test
    fun testPlayerUiStateDefaults() {
        val state = PlayerUiState()
        assertEquals(0, state.commentCount)
        assertEquals(CoverStyle.VINYL, state.coverStyle)
        assertTrue(state.showTranslation)
        assertEquals(LyricAlignment.CENTER, state.lyricAlignment)
        assertTrue(state.lyricWordByWord)
        assertEquals("hot", state.commentType)
    }

    @Test
    fun testCommentCountResolutionLogic() {
        // 场景 1: 服务器返回有效评论总数，优先使用服务器总数
        var serverCount = 1420
        var mergedList = listOf(
            SongComment(id = "1", userId = "u1", userName = "User 1", userAvatar = "", content = "评论1", timeFormatted = "10分钟前")
        )
        var count = if (serverCount > 0) serverCount else if (mergedList.isNotEmpty()) maxOf(0, mergedList.size) else 0
        assertEquals(1420, count)

        // 场景 2: 服务器未返回 count (<= 0)，降级使用已加载评论列表大小
        serverCount = 0
        count = if (serverCount > 0) serverCount else if (mergedList.isNotEmpty()) maxOf(0, mergedList.size) else 0
        assertEquals(1, count)

        // 场景 3: 刷新时若未获取到新评论，保留之前的计数
        val previousCount = 25
        val emptyList = emptyList<SongComment>()
        count = if (serverCount > 0) serverCount else if (emptyList.isNotEmpty()) maxOf(previousCount, emptyList.size) else previousCount
        assertEquals(25, count)
    }

    @Test
    fun testTreeBranchRepliesExpandThreshold() {
        // 评论回复小于等于 3 条时，默认全部展示且不展示“展开剩余”折叠按钮
        val commentWith2Replies = SongComment(
            id = "c1",
            userId = "u1",
            userName = "Root",
            userAvatar = "",
            content = "Root content",
            timeFormatted = "1小时前",
            replies = listOf(
                SongComment(id = "r1", userId = "u2", userName = "Reply 1", userAvatar = "", content = "Rep 1", timeFormatted = "50分钟前"),
                SongComment(id = "r2", userId = "u3", userName = "Reply 2", userAvatar = "", content = "Rep 2", timeFormatted = "40分钟前")
            )
        )
        val visibleRepliesInitial = commentWith2Replies.replies.take(3)
        assertEquals(2, visibleRepliesInitial.size)
        assertFalse(commentWith2Replies.replies.size > 3)

        // 评论回复多于 3 条时（例如 5 条），默认截断展示前 3 条，计算剩余 2 条
        val commentWith5Replies = SongComment(
            id = "c2",
            userId = "u1",
            userName = "Root 2",
            userAvatar = "",
            content = "Root content 2",
            timeFormatted = "2小时前",
            replies = (1..5).map {
                SongComment(id = "r$it", userId = "u$it", userName = "Reply $it", userAvatar = "", content = "Rep $it", timeFormatted = "$it 分钟前")
            }
        )
        val visibleRepliesCollapsed = commentWith5Replies.replies.take(3)
        assertEquals(3, visibleRepliesCollapsed.size)
        assertTrue(commentWith5Replies.replies.size > 3)
        val remainingCount = commentWith5Replies.replies.size - 3
        assertEquals(2, remainingCount)

        // 展开状态下展示全部 5 条
        val visibleRepliesExpanded = commentWith5Replies.replies
        assertEquals(5, visibleRepliesExpanded.size)
    }

    @Test
    fun testLyricDualLinePreviewResolution() {
        // 构造含双语翻译的歌词行
        val line1 = LyricLine(
            startTimeMs = 1000,
            durationMs = 2000,
            text = "Every night in my dreams",
            translation = "每一个夜晚在我的梦里"
        )
        val line2 = LyricLine(
            startTimeMs = 3000,
            durationMs = 2500,
            text = "I see you, I feel you",
            translation = "我看见你，我感觉你"
        )
        val lyricData = LyricData(lines = listOf(line1, line2))

        // 当开启翻译且当前行有翻译时，第二行展示译文
        val currentLyricIndex = 0
        val currentLineObj = lyricData.lines.getOrNull(currentLyricIndex)
        val showTranslation = true
        val subTextWithTrans = if (showTranslation && !currentLineObj?.translation.isNullOrBlank()) {
            currentLineObj?.translation ?: ""
        } else {
            lyricData.lines.getOrNull(currentLyricIndex + 1)?.text ?: ""
        }
        assertEquals("每一个夜晚在我的梦里", subTextWithTrans)

        // 当关闭翻译时，第二行展示下一行歌词主文本
        val subTextWithoutTrans = if (false && !currentLineObj?.translation.isNullOrBlank()) {
            currentLineObj?.translation ?: ""
        } else {
            lyricData.lines.getOrNull(currentLyricIndex + 1)?.text ?: ""
        }
        assertEquals("I see you, I feel you", subTextWithoutTrans)
    }

    @Test
    fun testMusicRepositoryCommentCountTracking() {
        val store = MemoryStore()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ): ApiResponse {
                return ApiResponse(JsonObject())
            }
        }
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)
        assertEquals(0, repo.lastSongCommentCount)
    }

    @Test
    fun testSongEffectiveQualitiesAndHighestQualityAutoDetection() {
        // 当未显式传递 availableQualities 时，应根据音频标志（isHiRes, isLossless 等）动态计算
        val songHiResAuto = Song(
            id = "auto_hires",
            title = "HiRes Auto",
            artist = "歌手",
            isHiRes = true,
            isLossless = true
        )
        assertEquals(AudioQuality.HI_RES, songHiResAuto.highestQuality)
        assertTrue(songHiResAuto.effectiveQualities.contains(AudioQuality.HI_RES))
        assertTrue(songHiResAuto.effectiveQualities.contains(AudioQuality.LOSSLESS))
        assertTrue(songHiResAuto.effectiveQualities.contains(AudioQuality.HIGH))
        assertTrue(songHiResAuto.effectiveQualities.contains(AudioQuality.STANDARD))

        val songLosslessAuto = Song(
            id = "auto_lossless",
            title = "Lossless Auto",
            artist = "歌手",
            isLossless = true
        )
        assertEquals(AudioQuality.LOSSLESS, songLosslessAuto.highestQuality)
        assertFalse(songLosslessAuto.effectiveQualities.contains(AudioQuality.HI_RES))
        assertTrue(songLosslessAuto.effectiveQualities.contains(AudioQuality.LOSSLESS))

        val songStandardAuto = Song(
            id = "auto_std",
            title = "Standard Auto",
            artist = "歌手",
            quality = "128"
        )
        assertEquals(AudioQuality.STANDARD, songStandardAuto.highestQuality)
        assertEquals(listOf(AudioQuality.STANDARD), songStandardAuto.effectiveQualities)
    }

    @Test
    fun testPostSongCommentProfilePreservation() = kotlinx.coroutines.runBlocking {
        val store = MemoryStore()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())
        }
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val testSong = Song(id = "100", title = "测试曲目", artist = "测试歌手")
        val comment = repo.postSongComment(
            song = testSong,
            content = "很棒的音乐！",
            replyToCommentId = "root_1",
            replyToUserName = "原评主"
        )

        assertEquals("很棒的音乐！", comment.content)
        assertEquals("root_1", comment.parentId)
        assertEquals("原评主", comment.replyUserName)
        assertEquals("刚刚", comment.timeFormatted)
    }

    @Test
    fun testSearchViewModelHistoryInitiallyEmptyAndCleanable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = com.concept.music.ui.viewmodel.SearchViewModel()
            // 验证已彻底清除写死的模拟搜索记录
            assertTrue(vm.searchHistory.value.isEmpty())

            // 验证搜索后历史记录正常追加与去重
            vm.search("夜曲")
            assertEquals(listOf("夜曲"), vm.searchHistory.value)

            vm.search("晴天")
            assertEquals(listOf("晴天", "夜曲"), vm.searchHistory.value)

            // 验证单项移除与清空
            vm.removeHistoryItem("夜曲")
            assertEquals(listOf("晴天"), vm.searchHistory.value)

            vm.clearHistory()
            assertTrue(vm.searchHistory.value.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testSearchExecutionStateAndEmptyResultBehavior() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = com.concept.music.ui.viewmodel.SearchViewModel()
            assertFalse(vm.hasSearched.value)
            assertEquals("", vm.lastSearchedKeyword.value)

            // 执行搜索
            vm.search("冷门未收录歌曲")
            assertTrue(vm.hasSearched.value)
            assertEquals("冷门未收录歌曲", vm.lastSearchedKeyword.value)

            // 清空搜索重置
            vm.clearQuery()
            assertFalse(vm.hasSearched.value)
            assertEquals("", vm.lastSearchedKeyword.value)
            assertEquals("", vm.query.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testSearchSuggestionCancellationAndDismissal() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = com.concept.music.ui.viewmodel.SearchViewModel()
            vm.dismissSuggestions()
            assertTrue(vm.suggestions.value.isEmpty())

            vm.search("七里香")
            assertTrue(vm.suggestions.value.isEmpty())
            assertTrue(vm.hasSearched.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testSearchHistoryRepository() {
        val repo = com.concept.music.data.repository.SearchHistoryRepository(null)
        assertTrue(repo.get().isEmpty())
        repo.save(listOf("稻香", "花海"))
        assertEquals(listOf("稻香", "花海"), repo.get())
        repo.clear()
        assertTrue(repo.get().isEmpty())
    }
}
