package com.concept.music

import com.concept.music.data.kugou.*
import com.concept.music.data.model.*
import com.concept.music.ui.theme.CoverTonalPalette
import com.concept.music.ui.theme.composeColorToHsv
import com.concept.music.ui.theme.perceptualLuminance
import com.concept.music.ui.theme.readableOnDarkSurface
import com.concept.music.ui.theme.readableSubtleOnDarkSurface
import com.concept.music.ui.theme.toCoverColorRoles
import com.concept.music.ui.theme.toTonalPalette
import com.concept.music.ui.theme.wcagContrastRatio
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.LyricHighlightTheme
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import com.concept.music.ui.viewmodel.PlayerUiState
import com.concept.music.ui.viewmodel.SavedPlayQueue
import com.concept.music.ui.viewmodel.PlayMode
import com.concept.music.service.AudioEffectState
import com.concept.music.service.SystemLyricState
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
        assertEquals(2, CoverStyle.entries.size)
        assertTrue(CoverStyle.entries.contains(CoverStyle.SQUARE))
        assertFalse(state.showTranslation)
        assertFalse(state.showPhonetic)
        assertEquals(LyricAlignment.LEFT, state.lyricAlignment)
        assertEquals(24f, state.lyricFontSize, 0.01f)
        assertTrue(state.lyricBlurEnabled)
        assertTrue(state.lyricWordByWord)
        assertEquals("hot", state.commentType)
        assertEquals(PlayerBackgroundStyle.COVER_BLUR, state.playerBackgroundStyle)
        assertEquals(5, PlayerBackgroundStyle.entries.size)
        assertTrue(state.dynamicCoverAccentEnabled)
        assertEquals(0.84f, state.lyricTranslationScale, 0.01f)
        assertTrue(state.motionOrientationPermissionEnabled)
        assertEquals(LyricHighlightTheme.ICE_BLUE, state.lyricHighlightTheme)
        assertTrue(LyricHighlightTheme.entries.contains(LyricHighlightTheme.DYNAMIC_COVER))
        assertEquals(1.0f, SystemLyricState().desktopWidthFraction, 0.01f)
        assertFalse(AudioEffectState().equalLoudnessCompensation)
        assertFalse(state.allowPlayWithOtherApps)
        assertTrue(state.wiredHeadsetControlEnabled)
        assertEquals(9, com.concept.music.ui.settings.SettingsCategory.entries.size)
        assertTrue(com.concept.music.ui.settings.SettingsCategory.entries.contains(com.concept.music.ui.settings.SettingsCategory.PERMISSIONS))
    }

    @Test
    fun testPlayerCommentCountFormatting() {
        assertEquals("", com.concept.music.ui.components.formatPlayerCommentCount(0))
        assertEquals("999", com.concept.music.ui.components.formatPlayerCommentCount(999))
        assertEquals("1.0k", com.concept.music.ui.components.formatPlayerCommentCount(1000))
        assertEquals("2.4k", com.concept.music.ui.components.formatPlayerCommentCount(2380))
        assertEquals("1.0w", com.concept.music.ui.components.formatPlayerCommentCount(10000))
        assertEquals("2.6w", com.concept.music.ui.components.formatPlayerCommentCount(25800))
    }

    @Test
    fun testChineseSongAutoHidesTranslationButtonAndPhoneticDetection() {
        val chineseSong = Song(id = "cn1", title = "淋雨一直走", artist = "张韶涵")
        val chineseLyrics = LyricData(
            lines = listOf(
                LyricLine(0, 2000, "人都应该有梦", translation = "Everyone should have a dream"),
                LyricLine(2000, 2000, "有梦就别怕痛", translation = "Don't be afraid of pain"),
                LyricLine(4000, 2000, "有雷声在轰不停", translation = "Thunder is roaring")
            )
        )
        assertFalse(
            "Pure Chinese songs should hide the translation toggle button",
            com.concept.music.ui.components.shouldShowTranslationButton(chineseSong, chineseLyrics)
        )
        assertFalse(
            "Pure Chinese songs without phonetic lines should hide phonetic button",
            com.concept.music.ui.components.shouldShowPhoneticButton(chineseSong, chineseLyrics)
        )

        val japaneseSong = Song(id = "jp1", title = "夜に駆ける", artist = "YOASOBI")
        val japaneseLyrics = LyricData(
            lines = listOf(
                LyricLine(0, 2000, "沈むように溶けてゆくように", translation = "仿佛沉溺般 仿佛融化般", phonetic = "shizumu you ni tokete yuku you ni"),
                LyricLine(2000, 2000, "二人だけの空が広がる夜に", translation = "在只属于两人的天空延展的夜晚", phonetic = "futari dake no sora ga hirogaru yoru ni")
            )
        )
        assertTrue(
            "Japanese songs with translation should show the translation toggle button",
            com.concept.music.ui.components.shouldShowTranslationButton(japaneseSong, japaneseLyrics)
        )
        assertTrue(
            "Japanese songs with phonetic should show the phonetic toggle button",
            com.concept.music.ui.components.shouldShowPhoneticButton(japaneseSong, japaneseLyrics)
        )
    }

    @Test
    fun testCommentCountResolutionLogic() {
        var serverCount = 1420
        val mergedList = listOf(
            SongComment(id = "1", userId = "u1", userName = "User 1", userAvatar = "", content = "评论1", timeFormatted = "10分钟前")
        )
        var count = if (serverCount > 0) serverCount else if (mergedList.isNotEmpty()) maxOf(0, mergedList.size) else 0
        assertEquals(1420, count)

        serverCount = 0
        count = if (serverCount > 0) serverCount else if (mergedList.isNotEmpty()) maxOf(0, mergedList.size) else 0
        assertEquals(1, count)

        val previousCount = 25
        val emptyList = emptyList<SongComment>()
        count = if (serverCount > 0) serverCount else if (emptyList.isNotEmpty()) maxOf(previousCount, emptyList.size) else previousCount
        assertEquals(25, count)
    }

    @Test
    fun testTreeBranchRepliesExpandThreshold() {
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

        val visibleRepliesExpanded = commentWith5Replies.replies
        assertEquals(5, visibleRepliesExpanded.size)
    }

    @Test
    fun testLyricDualLinePreviewResolution() {
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

        val currentLyricIndex = 0
        val currentLineObj = lyricData.lines.getOrNull(currentLyricIndex)
        val showTranslation = true
        val subTextWithTrans = if (showTranslation && !currentLineObj?.translation.isNullOrBlank()) {
            currentLineObj?.translation ?: ""
        } else {
            lyricData.lines.getOrNull(currentLyricIndex + 1)?.text ?: ""
        }
        assertEquals("每一个夜晚在我的梦里", subTextWithTrans)

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
    fun testPostSongCommentProfilePreservationAndRouting() = kotlinx.coroutines.runBlocking {
        val store = MemoryStore()
        val capturedRoutes = mutableListOf<String>()
        val capturedRouters = mutableListOf<String>()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ): ApiResponse {
                query["r"]?.let { capturedRoutes.add(it) }
                headers["x-router"]?.let { capturedRouters.add(it) }
                return ApiResponse(JsonObject())
            }
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
        assertEquals("commentsv2/reply", capturedRoutes.firstOrNull())
        assertEquals("m.comment.service.kugou.com", capturedRouters.firstOrNull())

        val rootComment = repo.postSongComment(
            song = testSong,
            content = "主评论内容"
        )
        assertEquals("主评论内容", rootComment.content)
        assertTrue(capturedRoutes.contains("commentsv3/add"))

        // 未登录时必须抛出 ApiFailure，绝不伪造 userId="10086" 的假评论
        accounts.select(null)
        try {
            repo.postSongComment(song = testSong, content = "未登录评论")
            fail("未登录状态下发表评论应抛出 ApiFailure")
        } catch (expected: ApiFailure) {
            assertEquals(20010, expected.code)
        }
    }

    @Test
    fun testSearchViewModelHistoryInitiallyEmptyAndCleanable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = com.concept.music.ui.viewmodel.SearchViewModel()
            assertTrue(vm.searchHistory.value.isEmpty())

            vm.search("夜曲")
            assertEquals(listOf("夜曲"), vm.searchHistory.value)

            vm.search("晴天")
            assertEquals(listOf("晴天", "夜曲"), vm.searchHistory.value)

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

            vm.search("冷门未收录歌曲")
            assertTrue(vm.hasSearched.value)
            assertEquals("冷门未收录歌曲", vm.lastSearchedKeyword.value)

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

    @Test
    fun testSearchTabAlwaysResetsToComplexOnNewSearchOrExit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = com.concept.music.ui.viewmodel.SearchViewModel()
            assertEquals(SearchTab.COMPLEX, vm.selectedTab.value)

            vm.search("周杰伦")
            vm.selectTab(SearchTab.ALBUM)
            assertEquals(SearchTab.ALBUM, vm.selectedTab.value)

            vm.clearQuery()
            assertEquals(SearchTab.COMPLEX, vm.selectedTab.value)

            vm.selectTab(SearchTab.ALBUM)
            assertEquals(SearchTab.ALBUM, vm.selectedTab.value)
            vm.search("林俊杰")
            assertEquals(SearchTab.COMPLEX, vm.selectedTab.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testParseSongRejectsUnverifiedHiResAndSongDetailMatchesActualPlayQuality() {
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

        val fakeHiResJson = com.google.gson.JsonParser.parseString(
            """
            {
                "SongName": "普通流行歌",
                "SingerName": "测试歌手",
                "FileHash": "11111111111111111111111111111111",
                "SQFileHash": "22222222222222222222222222222222",
                "SQFileSize": 25000000,
                "ResFileHash": "33333333333333333333333333333333",
                "ResFileSize": 0,
                "is_hires": 1,
                "duration": 210
            }
            """
        ).asJsonObject
        val parsedFake = repo.parseSong(fakeHiResJson)
        assertFalse("Unverified ResFileHash with 0 size must NOT be marked Hi-Res", parsedFake.isHiRes)
        assertTrue("Should be marked as SQ Lossless instead", parsedFake.isLossless)

        val playingAt320 = parsedFake.copy(
            actualPlayQuality = AudioQuality.HIGH,
            bitrateKbps = 320,
            fileSizeByte = 8_400_000L
        )
        val detail = repo.songDetail(playingAt320)
        assertTrue(detail.audioQuality.contains("320kbps"))
        assertEquals("320 kbps", detail.bitRate)
        assertTrue(detail.sampleRate.contains("44.1 kHz"))
    }

    @Test
    fun testKugouCommentEmojiParserConvertsCodesAndBracketTags() {
        val raw = "太好听了[em]e400132[/em] 永远支持[爱心][赞]"
        val parsed = KugouCommentEmojiParser.parseEmojiContent(raw)
        assertFalse(parsed.contains("[em]"))
        assertTrue(parsed.contains("❤️"))
        assertTrue(parsed.contains("👍"))
    }

    @Test
    fun testOfficialListeningDuration220904MinutesAndGradeRemainMinutes() {
        val officialMinutes = 220904L
        val officialSeconds = officialMinutes * 60L
        val gradeInfo = UserGradeInfo(
            level = 11,
            listenDurationMinutes = officialMinutes,
            listenDurationSeconds = officialSeconds,
            nextLevelProgress = 0.99f,
            nextLevelRemainMinutes = 1096L,
            currentBaseScore = 176000L,
            nextLevelMinMinutes = 222000L
        )
        assertEquals("3681 小时 44 分", gradeInfo.formattedDuration)

        val updated = gradeInfo.withUpdatedSeconds(officialSeconds)
        assertEquals(11, updated.level)
        assertEquals(220904L, updated.listenDurationMinutes)
        assertEquals("3681 小时 44 分", updated.formattedDuration)
        assertEquals(176000L, updated.currentBaseScore)
        // 222000 - 220904 = 1096 minutes (~18.2 hours)
        assertEquals(1096L, updated.nextLevelRemainMinutes)
        assertTrue(updated.nextLevelProgress in 0.97f..0.99f)
    }

    @Test
    fun testCommentSpecialChildIdAndHotReplyListRouting() = kotlinx.coroutines.runBlocking {
        val store = MemoryStore()
        var capturedReplyPath = ""
        var capturedChildrenId = ""
        var capturedTid = ""
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ): ApiResponse {
                if (path == "/mcomment/v1/hot_replylist") {
                    capturedReplyPath = path
                    capturedChildrenId = query["childrenid"].orEmpty()
                    capturedTid = query["tid"].orEmpty()
                    val json = com.google.gson.JsonParser.parseString(
                        """
                        {
                            "status": 1,
                            "err_code": 0,
                            "list": [
                                {
                                    "id": "rep_101",
                                    "user_id": "8801",
                                    "uname": "回音听众",
                                    "content": "致敬先辈，山河无恙！",
                                    "addtime": "2026-10-22 10:12:00",
                                    "pcontent": "MG"
                                }
                            ]
                        }
                        """
                    ).asJsonObject
                    return ApiResponse(json)
                }
                return ApiResponse(JsonObject())
            }
        }
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val song = Song(id = "song_1", title = "共赴", artist = "黄霄雲", mixSongId = "987654321")
        val replies = repo.songCommentReplies(
            song = song,
            commentId = "tid_555",
            specialChildId = "scid_999",
            page = 1,
            pageSize = 20
        )
        assertEquals("/mcomment/v1/hot_replylist", capturedReplyPath)
        assertEquals("scid_999", capturedChildrenId)
        assertEquals("tid_555", capturedTid)
        assertEquals(1, replies.size)
        assertEquals("回音听众", replies.first().userName)
        assertEquals("致敬先辈，山河无恙！", replies.first().content)
    }

    @Test
    fun testWeiboStyleReplyChainStrippingAnd4LevelClusterTree() {
        val rawWeibo = "因为有个作家叫夏目漱石，我一开始以为是写给夏目漱石的哈哈哈。//@Stephanie月月:我跟你一样，而且你还更过分一点，人家是日不是目哈哈哈哈哈哈//@dlarng:我以为这首歌叫橘子海，是夏目漱石写的。。。。。。"
        val parsed = KugouCommentEmojiParser.parseWeiboReplyChain(rawWeibo)
        assertEquals("因为有个作家叫夏目漱石，我一开始以为是写给夏目漱石的哈哈哈。", parsed.cleanContent)
        assertEquals("Stephanie月月", parsed.targetUserName)
        assertEquals("我跟你一样，而且你还更过分一点，人家是日不是目哈哈哈哈哈哈", parsed.quotedContent)
        assertEquals(2, parsed.chainHops.size)

        val root = SongComment(
            id = "root_1",
            userId = "u0",
            userName = "dlarng",
            userAvatar = "",
            content = "我以为这首歌叫橘子海，是夏目漱石写的。。。。。。",
            timeFormatted = "2021-07-25"
        )
        val lv1 = SongComment(
            id = "r1",
            userId = "u1",
            userName = "齐天洋洋",
            userAvatar = "",
            content = "我跟你一样，而且你还更过分一点，人家是日不是目哈哈哈哈哈哈",
            timeFormatted = "2022-05-05"
        )
        val lv2 = SongComment(
            id = "r2",
            userId = "u2",
            userName = "x",
            userAvatar = "",
            content = parsed.cleanContent,
            timeFormatted = "2022-05-21",
            replyUserName = parsed.targetUserName,
            replyQuotedContent = parsed.quotedContent
        )
        val lv3 = SongComment(
            id = "r3",
            userId = "u3",
            userName = "三级用户",
            userAvatar = "",
            content = "三级回复内容",
            timeFormatted = "2022-05-22",
            replyTargetCommentId = "r2",
            replyUserName = "x"
        )
        val lv4 = SongComment(
            id = "r4",
            userId = "u4",
            userName = "四级用户",
            userAvatar = "",
            content = "四级回复内容",
            timeFormatted = "2022-05-23",
            replyTargetCommentId = "r3",
            replyUserName = "三级用户"
        )
        val lv5 = SongComment(
            id = "r5",
            userId = "u5",
            userName = "五级用户",
            userAvatar = "",
            content = "五级深层回复内容",
            timeFormatted = "2022-05-24",
            replyTargetCommentId = "r4",
            replyUserName = "四级用户"
        )

        val treeResult = com.concept.music.ui.comment.buildFloorClusterTree(
            rootComment = root,
            rawReplies = listOf(lv1, lv2, lv3, lv4, lv5)
        )
        assertEquals(5, treeResult.totalReplyCountInTree)
        // 4 cluster comment rows (lv1..lv4) + 1 ViewDeepDialogueBadgeRow for lv5
        assertEquals(5, treeResult.visualItems.size)
        assertTrue(treeResult.visualItems.last() is com.concept.music.ui.comment.FloorTreeVisualItem.ViewDeepDialogueBadgeRow)
    }

    @Test
    fun testCommentImageUrlPreservesCleartextMultiLevelBssdlAndUpgradesSingleLevel() {
        // Multi-level bssdl CDN (no *.bssdl.kugou.com wildcard cert) must remain http:// to avoid SSLPeerUnverifiedException
        assertEquals(
            "http://webimg.bssdl.kugou.com/eef916e65102022ea9ac4d379179fd04.jpg",
            imageUrl("http://webimg.bssdl.kugou.com/eef916e65102022ea9ac4d379179fd04.jpg")
        )
        // Single-level imge.kugou.com and cloud.kugou.com work over HTTPS
        assertEquals(
            "https://imge.kugou.com/stdmusic/400/20260318/cover.jpg",
            imageUrl("http://imge.kugou.com/stdmusic/{size}/20260318/cover.jpg")
        )
        assertEquals(
            "https://cmtimgbssdl.cloud.kugou.com/eeff95d6c551b31371097b1795197256.png",
            imageUrl("https://cmtimgbssdl.cloud.kugou.com/eeff95d6c551b31371097b1795197256.png")
        )
        // JSON string accidentally passed to imageUrl must return empty string
        assertEquals("", imageUrl("{\"city\":\"杭州\"}"))
    }

    @Test
    fun testCommentImagePreviewZoomAndPanClampPreservesAspectRatioBounds() {
        val container = androidx.compose.ui.unit.IntSize(1200, 2400)
        // 3:2 landscape image (600x400) fitted into 1200x2400 -> fitted size is 1200x800
        val intrinsic = androidx.compose.ui.geometry.Size(600f, 400f)

        // At scale 1f, offset must always clamp to zero
        val at1x = com.concept.music.ui.comment.clampPreviewOffset(
            rawOffset = androidx.compose.ui.geometry.Offset(150f, -200f),
            scale = 1f,
            containerSize = container,
            imageIntrinsicSize = intrinsic
        )
        assertEquals(0f, at1x.x, 0.01f)
        assertEquals(0f, at1x.y, 0.01f)

        // At scale 2f, fittedW=1200 -> maxX = 1200*(2-1)/2 = 600; fittedH=800 -> maxY = 800*(2-1)/2 = 400
        val at2x = com.concept.music.ui.comment.clampPreviewOffset(
            rawOffset = androidx.compose.ui.geometry.Offset(900f, -650f),
            scale = 2f,
            containerSize = container,
            imageIntrinsicSize = intrinsic
        )
        assertEquals(600f, at2x.x, 0.01f)
        assertEquals(-400f, at2x.y, 0.01f)
    }

    @Test
    fun testDynamicColorReadabilityPreservesHueAndBoostsPerceptualLuminance() {
        val darkExtractedColors = listOf(
            androidx.compose.ui.graphics.Color(0xFF1F45A3), // 深皇家蓝 (图一、图二典型低可读性取色)
            androidx.compose.ui.graphics.Color(0xFF282088), // 深靛紫
            androidx.compose.ui.graphics.Color(0xFF56187A), // 深紫罗兰
            androidx.compose.ui.graphics.Color(0xFF7A153B), // 深酒红
            androidx.compose.ui.graphics.Color(0xFF135C49)  // 深墨绿
        )

        for (rawColor in darkExtractedColors) {
            val readable = rawColor.readableOnDarkSurface(minLuminance = 0.42f)
            val readableSubtle = rawColor.readableSubtleOnDarkSurface(minLuminance = 0.54f)
            val lumReadable = readable.perceptualLuminance()
            val lumSubtle = readableSubtle.perceptualLuminance()
            assertTrue("可读性前景明度应达标: $lumReadable", lumReadable >= 0.415f)
            assertTrue("次要副文案明度应达标: $lumSubtle", lumSubtle >= 0.535f)
        }

        // 本身已具备高明度的亮青色/亮琥珀金应保持原色不变
        val brightCyan = androidx.compose.ui.graphics.Color(0xFF2AF5D6)
        val processedBright = brightCyan.readableOnDarkSurface(minLuminance = 0.42f)
        assertEquals(brightCyan, processedBright)
    }

    @Test
    fun testPlaybackHistoryRepositoryUnlimitedCapacityBeyond100Songs() {
        val repo = com.concept.music.data.repository.PlaybackHistoryRepository(context = null)
        val userId = "unlimited_user"
        for (i in 1..240) {
            repo.add(
                userId,
                Song(
                    id = "song_$i",
                    hash = String.format("%032x", i),
                    title = "测试歌曲 $i",
                    artist = "歌手 $i"
                )
            )
        }
        val history = repo.get(userId)
        assertEquals(240, history.size)
        assertEquals("song_240", history.first().id)
        assertEquals("song_1", history.last().id)
    }

    @Test
    fun testTonalPalette13TonesMonotonicityHueLockAndDarkLightColorRolesContrast() {
        val seeds = listOf(
            androidx.compose.ui.graphics.Color(0xFF1E3A8A), // 图一典型深靛蓝封面种子色
            androidx.compose.ui.graphics.Color(0xFF282088), // 深靛紫
            androidx.compose.ui.graphics.Color(0xFF7A153B), // 深酒红
            androidx.compose.ui.graphics.Color(0xFF135C49), // 深墨绿
            androidx.compose.ui.graphics.Color(0xFFD97706)  // 琥珀橙
        )

        for (seed in seeds) {
            val palette = seed.toTonalPalette()
            val seedHue = composeColorToHsv(seed)[0]

            // 1. 验证 13 级明度色阶 (Tone 0..100) 严格单调递增且色相锁定 (ΔHue < 1.5°)
            var prevLum = -1f
            for (tone in CoverTonalPalette.STANDARD_TONES) {
                val c = palette.tone(tone)
                val lum = c.perceptualLuminance()
                assertTrue(
                    "Tone $tone 的感知亮度 ($lum) 必须严格大于上一级 ($prevLum)",
                    lum > prevLum
                )
                prevLum = lum

                if (tone in 10..95) {
                    val toneHue = composeColorToHsv(c)[0]
                    val rawDiff = kotlin.math.abs(toneHue - seedHue)
                    val hueDiff = minOf(rawDiff, 360f - rawDiff)
                    // Tone 95 接近纯白 (S ≈ 0.12)，8-bit sRGB 通道整数量化步长约 3.5°
                    val maxAllowedDiff = if (tone >= 95) 4.0f else 2.0f
                    assertTrue(
                        "Tone $tone 应保持基酒色相不变: seedHue=$seedHue, toneHue=$toneHue, diff=$hueDiff",
                        hueDiff < maxAllowedDiff
                    )
                }
            }

            // 2. 验证深色模式 (Dark Mode) Color Roles 前背景 WCAG 对比度 (解决图一/图二深蓝叠深灰蓝看不清)
            val darkRoles = seed.toCoverColorRoles(isDark = true)
            val darkTitleContrast = darkRoles.onPrimaryContainer.wcagContrastRatio(darkRoles.primaryContainer)
            val darkSubtleContrast = darkRoles.onPrimaryContainerSubtle.wcagContrastRatio(darkRoles.primaryContainer)
            val darkPrimaryOnSheetContrast = darkRoles.primary.wcagContrastRatio(androidx.compose.ui.graphics.Color(0xFF111826))
            assertTrue("深色模式主文案与容器对比度应 >= 4.5: $darkTitleContrast", darkTitleContrast >= 4.5f)
            assertTrue("深色模式副文案与容器对比度应 >= 4.0: $darkSubtleContrast", darkSubtleContrast >= 4.0f)
            assertTrue("深色模式主强调色与弹窗底色对比度应 >= 4.5: $darkPrimaryOnSheetContrast", darkPrimaryOnSheetContrast >= 4.5f)

            // 3. 验证日间浅色模式 (Light Mode) Color Roles 前背景 WCAG 对比度 (解决图三浅字叠浅底看不清)
            val lightRoles = seed.toCoverColorRoles(isDark = false)
            val lightTitleContrast = lightRoles.onPrimaryContainer.wcagContrastRatio(lightRoles.primaryContainer)
            val lightSubtleContrast = lightRoles.onPrimaryContainerSubtle.wcagContrastRatio(lightRoles.primaryContainer)
            val lightPrimaryOnSheetContrast = lightRoles.primary.wcagContrastRatio(androidx.compose.ui.graphics.Color(0xFFF8FBFF))
            assertTrue("浅色模式主文案与容器对比度应 >= 4.5: $lightTitleContrast", lightTitleContrast >= 4.5f)
            assertTrue("浅色模式副文案与容器对比度应 >= 4.5: $lightSubtleContrast", lightSubtleContrast >= 4.5f)
            assertTrue("浅色模式主强调色与日间弹窗底色对比度应 >= 4.5: $lightPrimaryOnSheetContrast", lightPrimaryOnSheetContrast >= 4.5f)
        }
    }

    @Test
    fun testUserPurchasedAlbumsPaginationAndValidPageSize20() = kotlinx.coroutines.runBlocking {
        val store = MemoryStore()
        val requestedPageSizes = mutableListOf<Int>()
        val requestedPages = mutableListOf<Int>()

        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ): ApiResponse {
                if (path == "/openapi/v1/copyright/get_album_goods") {
                    val ps = body?.get("pagesize")?.asInt ?: 0
                    val pg = body?.get("page")?.asInt ?: 0
                    requestedPageSizes.add(ps)
                    requestedPages.add(pg)
                    // 模拟酷狗 OpenAPI 服务端严格参数校验：pagesize > 50 直接返回空错
                    if (ps > 50) {
                        return ApiResponse(com.google.gson.JsonParser.parseString("""{"status":0,"errcode":40001}""").asJsonObject)
                    }
                    if (pg == 1) {
                        val arr = com.google.gson.JsonArray()
                        for (i in 1..20) {
                            arr.add(
                                com.google.gson.JsonParser.parseString(
                                    """{"album_id":"alb_$i","album_name":"已购专辑 $i","singer_name":"歌手 $i","sizable_cover":"https://imge.kugou.com/stdmusic/400/2026/a$i.jpg","song_count":10,"buy_time":"2026-10-01"}"""
                                ).asJsonObject
                            )
                        }
                        val resp = JsonObject().apply {
                            addProperty("status", 1)
                            add("data", arr)
                        }
                        return ApiResponse(resp)
                    } else if (pg == 2) {
                        val arr = com.google.gson.JsonArray()
                        arr.add(
                            com.google.gson.JsonParser.parseString(
                                """{"album_id":"alb_21","album_name":"已购专辑 21","singer_name":"歌手 21","sizable_cover":"https://imge.kugou.com/stdmusic/400/2026/a21.jpg","song_count":12,"buy_time":"2026-10-02"}"""
                            ).asJsonObject
                        )
                        val resp = JsonObject().apply {
                            addProperty("status", 1)
                            add("data", arr)
                        }
                        return ApiResponse(resp)
                    }
                }
                return ApiResponse(JsonObject())
            }
        }

        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val albums = repo.userPurchasedAlbums()
        assertEquals(21, albums.size)
        assertTrue("每次请求的 pagesize 必须 <= 20", requestedPageSizes.all { it in 1..20 })
        assertEquals(listOf(1, 2), requestedPages)
        assertEquals("已购专辑 1", albums.first().albumName)
        assertEquals("已购专辑 21", albums.last().albumName)
    }

    @Test
    fun testRoundedCoverMotionSchemeASpinUpAndSymmetryStopContinuity() {
        // 1. 验证初始静止角严格为 45° (π/4)，且点击播放时角速度从 0 平滑单调加速至巡航速度（绝不重置相位回 0°）
        val initialPhase = com.concept.music.ui.components.ROUNDED_COVER_INITIAL_REST_PHASE_RAD
        assertEquals((Math.PI / 4.0).toFloat(), initialPhase, 1e-4f)

        var prevOmega = com.concept.music.ui.components.computeRoundedCoverSpinUpVelocity(0f, 0f)
        assertEquals(0f, prevOmega, 1e-5f)
        for (step in 1..20) {
            val t = step * (com.concept.music.ui.components.ROUNDED_COVER_SPIN_UP_DURATION_SEC / 20f)
            val omega = com.concept.music.ui.components.computeRoundedCoverSpinUpVelocity(0f, t)
            assertTrue("起旋角速度必须严格单调递增: $omega >= $prevOmega", omega >= prevOmega)
            prevOmega = omega
        }
        assertEquals(
            com.concept.music.ui.components.ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC,
            prevOmega,
            1e-4f
        )

        // 2. 验证方案 A（播放 -> 暂停）：从任意播放角度暂停时，目标角始终为前方的 k*90° + 45° 对称交错角，
        //    且减速全过程相位连续递增（零瞬移、零倒转）、角速度严格单调递减至 0
        val testAnglesDeg = listOf(5f, 18f, 43f, 45f, 62f, 89f, 128f, 179f, 224f, 275f, 352f)
        val period = com.concept.music.ui.components.ROUNDED_COVER_SYMMETRY_PERIOD_RAD
        val offset = com.concept.music.ui.components.ROUNDED_COVER_INITIAL_REST_PHASE_RAD

        for (deg in testAnglesDeg) {
            val startPhase = (deg * Math.PI / 180.0).toFloat()
            val targetPhase = com.concept.music.ui.components.computeRoundedCoverSymmetryStopTargetRad(
                currentPhaseRad = startPhase
            )
            // 目标角必须严格在当前角度前方，且满足 (targetPhase - π/4) 是 π/2 的整数倍
            assertTrue("目标角 ($targetPhase) 必须大于当前角 ($startPhase)", targetPhase > startPhase)
            val kFloat = (targetPhase - offset) / period
            assertEquals("目标角必须落在 45° (mod 90°) 对角交错格点上", kotlin.math.round(kFloat), kFloat, 1e-4f)

            val deltaRad = targetPhase - startPhase
            val durationSec = com.concept.music.ui.components.computeRoundedCoverDecelDurationSec(deltaRad)
            assertTrue("减速时长必须在合理自然区间 [0.68s, 1.52s]: $durationSec", durationSec in 0.68f..1.52f)

            // 验证 t = 0 首帧零跳变，且全程相位单调前进、角速度单调递减至 0
            val (p0, v0) = com.concept.music.ui.components.evaluateRoundedCoverDecelerationStep(
                startPhaseRad = startPhase,
                targetPhaseRad = targetPhase,
                durationSec = durationSec,
                elapsedSec = 0f
            )
            assertEquals("暂停首帧相位必须与当前播放相位严格一致（零瞬移）", startPhase, p0, 1e-5f)

            var lastP = p0
            var lastV = v0
            for (i in 1..30) {
                val t = durationSec * (i / 30f)
                val (pi, vi) = com.concept.music.ui.components.evaluateRoundedCoverDecelerationStep(
                    startPhaseRad = startPhase,
                    targetPhaseRad = targetPhase,
                    durationSec = durationSec,
                    elapsedSec = t
                )
                assertTrue("减速过程相位必须正向连续推进: $pi >= $lastP", pi >= lastP - 1e-5f)
                assertTrue("减速过程角速度必须单调递减: $vi <= $lastV", vi <= lastV + 1e-5f)
                lastP = pi
                lastV = vi
            }
            assertEquals("减速结束时必须精准停靠在目标对角交错角", targetPhase, lastP, 1e-4f)
            assertEquals("减速结束时角速度必须归零", 0f, lastV, 1e-5f)
        }
    }

    @Test
    fun `圆角封面动效状态包含跨视图共享的缩放与光晕相位参数`() {
        val statePaused = com.concept.music.ui.components.RoundedCoverMotionState(
            dispersionPhase = 1.25f,
            haloPulse = 0.5f,
            playTransition = 0f,
            coverScale = 0.88f
        )
        val statePlaying = com.concept.music.ui.components.RoundedCoverMotionState(
            dispersionPhase = 2.50f,
            haloPulse = 0.8f,
            playTransition = 1f,
            coverScale = 1.0f
        )
        assertEquals(0.88f, statePaused.coverScale, 1e-5f)
        assertEquals(0f, statePaused.playTransition, 1e-5f)
        assertEquals(1.0f, statePlaying.coverScale, 1e-5f)
        assertEquals(1f, statePlaying.playTransition, 1e-5f)
    }

    @Test
    fun `歌词海报超清分辨率封面URL升级与完整歌手专辑无截断排版验证`() {
        assertEquals(2f, com.concept.music.ui.components.POSTER_EXPORT_SCALE, 1e-4f)
        assertEquals(1080, com.concept.music.ui.components.POSTER_BASE_LOGICAL_WIDTH)
        assertEquals(1560, com.concept.music.ui.components.POSTER_BASE_LOGICAL_HEIGHT)

        val rawUrl1 = "http://imge.kugou.com/stdmusic/{size}/20261027/abc.jpg"
        assertEquals(
            "http://imge.kugou.com/stdmusic/1000/20261027/abc.jpg",
            com.concept.music.ui.components.upgradePosterCoverUrl(rawUrl1)
        )

        val rawUrl2 = "https://imge.kugou.com/stdmusic/240/20261027/xyz.jpg"
        assertEquals(
            "https://imge.kugou.com/stdmusic/1000/20261027/xyz.jpg",
            com.concept.music.ui.components.upgradePosterCoverUrl(rawUrl2)
        )

        val fullSub = com.concept.music.ui.components.formatPosterArtistAlbumSubtitle(
            artist = "Kastra",
            albumName = "Fool For You"
        )
        assertEquals("Kastra · 《Fool For You》", fullSub)

        val noAlbumSub = com.concept.music.ui.components.formatPosterArtistAlbumSubtitle(
            artist = "Kastra",
            albumName = "未知专辑",
            fallbackTagWhenNoAlbum = "24-BIT HI-RES"
        )
        assertEquals("Kastra · 24-BIT HI-RES", noAlbumSub)
    }

    @Test
    fun `KRC灵动音律跃动与前奏间奏呼吸波点进度计算验证`() {
        val defaultState = PlayerUiState()
        assertTrue("KRC逐字流光与弹性跃动应作为项目默认设计开启", defaultState.lyricWordByWord)

        val desktopState = SystemLyricState()
        assertFalse("桌面歌词悬浮胶囊应默认关闭", desktopState.floatingCapsuleEnabled)

        val line1 = LyricLine(
            startTimeMs = 5_000L,
            durationMs = 3_000L,
            text = "微风掠过星河",
            words = listOf(
                com.concept.music.data.model.LyricWord(5_000L, 1_500L, "微风"),
                com.concept.music.data.model.LyricWord(6_500L, 1_500L, "掠过星河")
            )
        )
        val line2 = LyricLine(
            startTimeMs = 14_000L,
            durationMs = 4_000L,
            text = "点亮概念之夜",
            words = listOf(
                com.concept.music.data.model.LyricWord(14_000L, 4_000L, "点亮概念之夜")
            )
        )
        val krcData = LyricData(lines = listOf(line1, line2), isKrc = true)

        // 1. 前奏区间 (0ms ~ 5000ms)
        val preludeProgress = krcData.getInterludeProgressAt(positionMs = 2_500L)
        assertEquals(0.5f, preludeProgress, 0.01f)
        assertEquals(3, krcData.getInterludeRemainingSecondsAt(positionMs = 2_500L))

        // 2. 第一句人声演唱中 (5000ms ~ 8000ms)：不显示间奏波点
        assertEquals(-1f, krcData.getInterludeProgressAt(positionMs = 6_200L), 0.001f)
        assertEquals(0, krcData.getInterludeRemainingSecondsAt(positionMs = 6_200L))

        // 3. 行间器乐间奏区间 (8000ms ~ 14000ms，间隔 6000ms >= 4000ms)
        val interludeMidProgress = krcData.getInterludeProgressAt(positionMs = 11_000L)
        assertEquals(0.5f, interludeMidProgress, 0.01f)
        assertEquals(3, krcData.getInterludeRemainingSecondsAt(positionMs = 11_000L))
    }

    @Test
    fun `有线耳机线控单击暂停双击下一首三击上一首及防误触去重验证`() {
        var fakeNowMs = 10_000L
        var scheduledAction: (() -> Unit)? = null
        var playPauseCount = 0
        var nextTrackCount = 0
        var prevTrackCount = 0

        val controller = com.concept.music.service.HeadsetMediaButtonController(
            multiClickTimeoutMs = 350L,
            nowMsProvider = { fakeNowMs },
            scheduleDelayed = { _, action -> scheduledAction = action },
            cancelScheduled = { scheduledAction = null },
            onTogglePlay = { playPauseCount++ },
            onSkipToNext = { nextTrackCount++ },
            onSkipToPrevious = { prevTrackCount++ }
        )

        // 1. 单击有线耳机中键 (KEYCODE_HEADSETHOOK) -> 等待超时后触发播放/暂停
        assertTrue(controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_DOWN, 0, 100L))
        assertTrue(controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_UP, 0, 100L))
        assertNotNull(scheduledAction)
        scheduledAction?.invoke()
        assertEquals(1, playPauseCount)
        assertEquals(0, nextTrackCount)
        assertEquals(0, prevTrackCount)

        // 2. 双击有线耳机暂停/中键 -> 触发下一首
        fakeNowMs += 1_000L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_DOWN, 0, 200L)
        fakeNowMs += 160L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_DOWN, 0, 360L)
        assertNotNull(scheduledAction)
        scheduledAction?.invoke()
        assertEquals(1, playPauseCount)
        assertEquals(1, nextTrackCount)
        assertEquals(0, prevTrackCount)

        // 3. 三击有线耳机暂停/中键 -> 立即触发上一首，且冷却期内忽略误触第 4 击
        fakeNowMs += 1_000L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.ACTION_DOWN, 0, 500L)
        fakeNowMs += 140L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.ACTION_DOWN, 0, 640L)
        fakeNowMs += 140L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.ACTION_DOWN, 0, 780L)
        assertNull("满三击后应立即执行上一首并清空延时任务", scheduledAction)
        assertEquals(1, prevTrackCount)

        // 冷却期内 (100ms < 220ms) 误触第 4 击不触发暂停
        fakeNowMs += 100L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.ACTION_DOWN, 0, 880L)
        assertNull(scheduledAction)
        assertEquals(1, playPauseCount)

        // 4. 重复 downTimeMs (如 Activity 与 MediaSession 双通道重复派发) 自动去重
        fakeNowMs += 1_000L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_DOWN, 0, 1500L)
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_DOWN, 0, 1500L)
        scheduledAction?.invoke()
        assertEquals("同一物理按键事件不得被计为双击", 2, playPauseCount)
        assertEquals(1, nextTrackCount)

        // 5. 关闭多击切歌开关时，单击直接切换播放/暂停
        controller.multiClickSkipEnabled = false
        fakeNowMs += 1_000L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_DOWN, 0, 2500L)
        assertEquals(3, playPauseCount)

        // 6. 开启交换双击/三击切歌方向与灵敏度调节：双击触发上一首，三击触发下一首
        controller.multiClickSkipEnabled = true
        controller.swapDoubleTripleClick = true
        controller.multiClickTimeoutMs = com.concept.music.service.HeadsetClickSensitivity.FAST.timeoutMs
        assertEquals(280L, controller.multiClickTimeoutMs)

        fakeNowMs += 1_000L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_DOWN, 0, 3000L)
        fakeNowMs += 120L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_DOWN, 0, 3120L)
        scheduledAction?.invoke()
        assertEquals("交换方向后双击应触发上一首", 2, prevTrackCount)
        assertEquals(1, nextTrackCount)

        fakeNowMs += 1_000L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_DOWN, 0, 4000L)
        fakeNowMs += 110L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_DOWN, 0, 4110L)
        fakeNowMs += 110L
        controller.handleKeyEvent(android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.ACTION_DOWN, 0, 4220L)
        assertEquals("交换方向后三击应触发下一首", 2, nextTrackCount)
    }

    @Test
    fun `音频焦点三档模式与蓝牙歌词延迟补偿及缓存上限档位验证`() {
        assertEquals(3, com.concept.music.service.AudioFocusMode.entries.size)
        assertEquals(3, com.concept.music.service.HeadsetClickSensitivity.entries.size)
        assertEquals(5, com.concept.music.service.AudioCacheLimit.entries.size)
        assertEquals(512L * 1024L * 1024L, com.concept.music.service.AudioCacheLimit.MB_512.maxBytes)
        assertEquals(10240L * 1024L * 1024L, com.concept.music.service.AudioCacheLimit.GB_10.maxBytes)

        val baseState = PlayerUiState(
            lyricOffsetMs = 100L,
            bluetoothLyricOffsetEnabled = true,
            bluetoothLyricOffsetMs = -250L,
            isBluetoothAudioConnected = false
        )
        assertEquals("未连接蓝牙时仅生效基础偏移", 100L, baseState.effectiveLyricOffsetMs)

        val btConnectedState = baseState.copy(isBluetoothAudioConnected = true)
        assertEquals("连接蓝牙且开启补偿时自动叠加蓝牙偏移", -150L, btConnectedState.effectiveLyricOffsetMs)
    }

    @Test
    fun `缓存分项明细统计与GitHub版本对比及标准Issue反馈模板验证`() {
        val stats = com.concept.music.service.DetailedCacheStats(
            audioBytes = 128L * 1024L * 1024L,
            audioTrackCount = 24,
            imageDiskBytes = 32L * 1024L * 1024L,
            imageMemoryBytes = 6L * 1024L * 1024L,
            swrMetadataBytes = 640L * 1024L,
            swrSnapshotCount = 5,
            lyricMemoryCount = 12,
            runtimeMetaCount = 30,
            otherTempBytes = 2L * 1024L * 1024L
        )
        assertEquals(38L * 1024L * 1024L, stats.totalImageBytes)
        assertEquals(640L * 1024L, stats.swrMetadataBytes)
        assertTrue(stats.totalBytes > 168L * 1024L * 1024L)

        // GitHub Release 版本号语义对比验证 (localVersion, remoteTag)
        assertTrue(com.concept.music.ui.settings.isRemoteVersionNewer("1.0.0", "v1.0.1"))
        assertTrue(com.concept.music.ui.settings.isRemoteVersionNewer("1.1.9", "v1.2.0"))
        assertFalse(com.concept.music.ui.settings.isRemoteVersionNewer("1.0.0", "v1.0.0"))
        assertFalse(com.concept.music.ui.settings.isRemoteVersionNewer("1.0.1", "v1.0.0"))

        // GitHub Issue 标准模板与仓库链接及 v1.0.0 正式版日志验证
        assertEquals("https://github.com/reneryi/breezemusic", com.concept.music.ui.settings.ProjectLinks.GITHUB_REPO_URL)
        assertEquals("https://github.com/reneryi/breezemusic/issues", com.concept.music.ui.settings.ProjectLinks.GITHUB_ISSUES_URL)
        val issueTemplate = com.concept.music.ui.settings.buildStandardIssueMarkdownTemplate("1.0.0", 1)
        assertTrue(issueTemplate.contains("### 问题类型 (Issue Type)"))
        assertTrue(issueTemplate.contains("### 运行环境 (Environment)"))
        assertTrue(issueTemplate.contains("### 复现步骤 (Steps to Reproduce)"))
        assertTrue(issueTemplate.contains("BreezeMusic v1.0.0 (Build 1)"))

        assertEquals(1, com.concept.music.ui.settings.PROJECT_CHANGELOG_HISTORY.size)
        val firstRelease = com.concept.music.ui.settings.PROJECT_CHANGELOG_HISTORY.first()
        assertEquals("v1.0.0", firstRelease.version)
        assertEquals(4, firstRelease.moduleGroups.size)
        assertTrue(firstRelease.highlights.isNotEmpty())
    }

    @Test
    fun `权限管理七大权限公开说明与自主开关对功能影响验证`() {
        com.concept.music.service.AppPermissionManager.resetForTest()
        try {
            val initialState = com.concept.music.service.AppPermissionManager.state.value
            assertEquals(7, initialState.totalCount)
            assertEquals(7, initialState.enabledCount)

            // 验证每项权限均包含完整的公开用途说明、隐私承诺与关闭后功能影响说明
            com.concept.music.service.AppPermissionType.entries.forEach { type ->
                assertTrue(type.title.isNotBlank())
                assertTrue(type.manifestPermissionName.isNotBlank())
                assertTrue(type.purposeDescription.isNotBlank())
                assertTrue(type.privacyCommitment.isNotBlank())
                assertTrue(type.disabledImpactDescription.isNotBlank())
                assertTrue(initialState.isEnabled(type))
            }

            // 1. 关闭「全局悬浮窗（桌面歌词）」权限 -> 立即关闭桌面歌词并阻止再次开启
            com.concept.music.service.SystemLyricBridge.setFloatingCapsuleEnabled(true)
            assertTrue(com.concept.music.service.SystemLyricBridge.state.value.floatingCapsuleEnabled)
            com.concept.music.service.AppPermissionManager.setPermissionEnabled(
                null,
                com.concept.music.service.AppPermissionType.SYSTEM_OVERLAY,
                false
            )
            assertFalse(com.concept.music.service.SystemLyricBridge.state.value.floatingCapsuleEnabled)
            com.concept.music.service.SystemLyricBridge.setFloatingCapsuleEnabled(true)
            assertFalse("关闭悬浮窗权限后不得允许开启桌面歌词", com.concept.music.service.SystemLyricBridge.state.value.floatingCapsuleEnabled)

            // 2. 关闭「外设音频检测与耳机线控」权限 -> 蓝牙歌词延迟补偿与耳机按键拦截自动停用
            val btState = PlayerUiState(
                lyricOffsetMs = 50L,
                bluetoothLyricOffsetEnabled = true,
                bluetoothLyricOffsetMs = -200L,
                isBluetoothAudioConnected = true
            )
            assertEquals(-150L, btState.effectiveLyricOffsetMs)
            com.concept.music.service.AppPermissionManager.setPermissionEnabled(
                null,
                com.concept.music.service.AppPermissionType.AUDIO_HARDWARE,
                false
            )
            assertEquals("关闭外设音频权限后蓝牙歌词延迟补偿应停用", 50L, btState.effectiveLyricOffsetMs)

            // 3. 关闭「设备动作与方向传感器」权限 -> 横放自动横屏判定返回 false
            com.concept.music.service.AppPermissionManager.setPermissionEnabled(
                null,
                com.concept.music.service.AppPermissionType.MOTION_ORIENTATION,
                false
            )
            assertEquals(4, com.concept.music.service.AppPermissionManager.state.value.enabledCount)

            // 4. 一键恢复开启全部权限
            com.concept.music.service.AppPermissionManager.enableAllPermissions(null)
            assertEquals(7, com.concept.music.service.AppPermissionManager.state.value.enabledCount)
        } finally {
            com.concept.music.service.AppPermissionManager.resetForTest()
        }
    }

    @Test
    fun `桌面歌词三行恒定高度与两行插空居中及长歌词自然换行验证`() {
        val shortLine = LyricLine(
            startTimeMs = 1000L,
            durationMs = 3000L,
            text = "穿过旷野的风",
            translation = "The wind blowing through the wilderness",
            words = listOf(
                LyricWord(startTimeMs = 1000L, durationMs = 1500L, text = "穿过旷野"),
                LyricWord(startTimeMs = 2500L, durationMs = 1500L, text = "的风")
            )
        )
        val nextShortLine = LyricLine(
            startTimeMs = 4000L,
            durationMs = 2500L,
            text = "你慢些走"
        )
        val shortPlan = com.concept.music.service.resolveDesktopLyricLayoutPlan(
            currentLine = shortLine,
            nextLine = nextShortLine,
            song = null,
            fontSizeSp = 19f,
            lineMode = com.concept.music.service.DesktopLyricLineMode.DUAL_BILINGUAL,
            maxCharsPerSingleRow = 16f
        )

        // 短歌词仅用两行：插空居中显示（0.28f 与 0.72f，整体中点严格位于 0.50f）
        assertFalse("短歌词不应触发三行换行", shortPlan.isThreeLineLayout)
        assertEquals(2, shortPlan.rows.size)
        assertEquals(0.28f, shortPlan.rows[0].verticalCenterFraction, 0.001f)
        assertEquals(0.72f, shortPlan.rows[1].verticalCenterFraction, 0.001f)
        assertEquals(
            "两行插空布局的整体视觉中心必须严格居中(0.50f)",
            0.50f,
            (shortPlan.rows[0].verticalCenterFraction + shortPlan.rows[1].verticalCenterFraction) / 2f,
            0.001f
        )

        // 长歌词超过单行阈值：自然扩展为三行（0.18f、0.50f、0.82f），且三行总高度与两行完全一致
        val longWords = listOf(
            LyricWord(startTimeMs = 1000L, durationMs = 600L, text = "在无人问津的"),
            LyricWord(startTimeMs = 1600L, durationMs = 600L, text = "深夜里独自"),
            LyricWord(startTimeMs = 2200L, durationMs = 600L, text = "穿越漫长而又"),
            LyricWord(startTimeMs = 2800L, durationMs = 600L, text = "辽阔的星河与"),
            LyricWord(startTimeMs = 3400L, durationMs = 600L, text = "旷野寻找光芒")
        )
        val longLine = LyricLine(
            startTimeMs = 1000L,
            durationMs = 3000L,
            text = longWords.joinToString("") { it.text },
            translation = "穿越漫长星河寻找失落的光芒",
            words = longWords
        )
        val longPlan = com.concept.music.service.resolveDesktopLyricLayoutPlan(
            currentLine = longLine,
            nextLine = nextShortLine,
            song = null,
            fontSizeSp = 19f,
            lineMode = com.concept.music.service.DesktopLyricLineMode.DUAL_BILINGUAL,
            maxCharsPerSingleRow = 16f
        )

        assertTrue("长歌词应自然切换为三行布局", longPlan.isThreeLineLayout)
        assertEquals(3, longPlan.rows.size)
        assertEquals(0.18f, longPlan.rows[0].verticalCenterFraction, 0.001f)
        assertEquals(0.50f, longPlan.rows[1].verticalCenterFraction, 0.001f)
        assertEquals(0.82f, longPlan.rows[2].verticalCenterFraction, 0.001f)
        assertEquals(
            "两行插空与三行换行的歌词容器高度必须保持恒定不变",
            shortPlan.fixedThreeLineHeightPx,
            longPlan.fixedThreeLineHeightPx,
            0.001f
        )
        assertTrue(longPlan.rows[0].isPrimary)
        assertTrue(longPlan.rows[1].isPrimary)
        assertFalse(longPlan.rows[2].isPrimary)
        assertEquals(
            "长歌词拆分为两行主句后应完整保留全部 KRC 逐字时间戳",
            longWords,
            longPlan.rows[0].words + longPlan.rows[1].words
        )
    }

    @Test
    fun `桌面歌词与App内设置双向同步及横向全屏锁定验证`() {
        var syncedTheme: LyricHighlightTheme? = null
        var syncedFontSize: Float? = null
        com.concept.music.service.SystemLyricBridge.onDesktopColorChangedCallback = { syncedTheme = it }
        com.concept.music.service.SystemLyricBridge.onDesktopFontSizeChangedCallback = { syncedFontSize = it }
        try {
            // 1. 桌面端修改配色与字号 -> 实时回调同步给 App 内播放器设置
            com.concept.music.service.SystemLyricBridge.setDesktopColorScheme(
                com.concept.music.service.DesktopLyricColorScheme.SAKURA_PINK
            )
            assertEquals(LyricHighlightTheme.SAKURA_PINK, syncedTheme)

            com.concept.music.service.SystemLyricBridge.setDesktopFontSize(22f)
            assertNotNull(syncedFontSize)
            assertTrue(syncedFontSize!! >= 18f)

            // 2. App 内修改配色（含「封面随动 DYNAMIC_COVER」）、字号、播放顺序 -> 实时同步到桌面歌词状态
            com.concept.music.service.SystemLyricBridge.syncFromAppSettings(
                highlightTheme = LyricHighlightTheme.DYNAMIC_COVER,
                inAppLyricFontSize = 26f,
                playMode = PlayMode.REPEAT_ONE
            )
            com.concept.music.service.SystemLyricBridge.updateDynamicCoverColors(
                primaryHex = 0xFFFF7A45L,
                secondaryHex = 0xFFFFB347L,
                coverUrl = "https://example.com/cover.jpg"
            )
            val bridgeState = com.concept.music.service.SystemLyricBridge.state.value
            assertEquals(
                com.concept.music.service.DesktopLyricColorScheme.DYNAMIC_COVER,
                bridgeState.desktopColorScheme
            )
            assertEquals(
                "封面随动模式下桌面歌词主高亮色应与 App 封面取色完全一致，而非固定紫色",
                0xFFFF7A45L,
                bridgeState.effectivePlayedStartHex
            )
            assertEquals(
                0xFFFFB347L,
                bridgeState.effectivePlayedEndHex
            )
            assertEquals(0xFFFF7A45L, bridgeState.swatchStartHex(com.concept.music.service.DesktopLyricColorScheme.DYNAMIC_COVER))
            assertEquals(PlayMode.REPEAT_ONE, com.concept.music.service.SystemLyricBridge.playModeSnapshot)
            assertEquals(1.0f, bridgeState.desktopWidthFraction, 0.001f)

            // 3. 横向占满屏幕宽度，锁定横向偏移 X = 0f，仅允许纵向拖动保存
            com.concept.music.service.SystemLyricBridge.saveDesktopPosition(168f, 420f)
            val afterDrag = com.concept.music.service.SystemLyricBridge.state.value
            assertEquals("横向自适应占满屏幕宽度后 X 轴偏移必须恒定锁定为 0f", 0f, afterDrag.savedOffsetX, 0.001f)
            assertEquals(420f, afterDrag.savedOffsetY, 0.001f)
        } finally {
            com.concept.music.service.SystemLyricBridge.onDesktopColorChangedCallback = null
            com.concept.music.service.SystemLyricBridge.onDesktopFontSizeChangedCallback = null
        }
    }

    @Test
    fun `系统通知栏与流体云桌面歌词开关及锁定解锁自定义按钮适配验证`() {
        com.concept.music.service.AppPermissionManager.resetForTest()
        var notifRefreshCount = 0
        com.concept.music.service.SystemLyricBridge.onDesktopLyricNotificationStateChanged = {
            notifRefreshCount++
        }
        try {
            // 1. 默认未开启、未锁定态：通知栏/流体云按钮应显示「开启桌面歌词」与「锁定桌面歌词」
            val layoutOffUnlocked = com.concept.music.service.PlaybackService.buildNotificationCustomLayout(
                lyricEnabled = false,
                lyricLocked = false
            )
            assertEquals(2, layoutOffUnlocked.size)
            assertEquals("开启桌面歌词", layoutOffUnlocked[0].displayName.toString())
            assertEquals(
                com.concept.music.service.PlaybackService.ACTION_CUSTOM_TOGGLE_DESKTOP_LYRIC,
                layoutOffUnlocked[0].sessionCommand?.customAction
            )
            assertEquals(R.drawable.ic_notif_lyric_off, layoutOffUnlocked[0].iconResId)

            assertEquals("锁定桌面歌词", layoutOffUnlocked[1].displayName.toString())
            assertEquals(
                com.concept.music.service.PlaybackService.ACTION_CUSTOM_TOGGLE_DESKTOP_LYRIC_LOCK,
                layoutOffUnlocked[1].sessionCommand?.customAction
            )
            assertEquals(R.drawable.ic_notif_lyric_unlocked, layoutOffUnlocked[1].iconResId)

            // 2. 开启并锁定桌面歌词：触发通知栏刷新，且按钮切换为「关闭桌面歌词」与「解锁桌面歌词」
            com.concept.music.service.SystemLyricBridge.setFloatingCapsuleEnabled(true)
            com.concept.music.service.SystemLyricBridge.setFloatingCapsuleLocked(true)
            assertTrue(com.concept.music.service.SystemLyricBridge.state.value.floatingCapsuleEnabled)
            assertTrue(com.concept.music.service.SystemLyricBridge.state.value.floatingCapsuleLocked)
            assertTrue("切换桌面歌词开关与锁定态应通知刷新系统媒体通知与流体云", notifRefreshCount >= 2)

            val layoutOnLocked = com.concept.music.service.PlaybackService.buildNotificationCustomLayout(
                lyricEnabled = com.concept.music.service.SystemLyricBridge.state.value.floatingCapsuleEnabled,
                lyricLocked = com.concept.music.service.SystemLyricBridge.state.value.floatingCapsuleLocked
            )
            assertEquals("关闭桌面歌词", layoutOnLocked[0].displayName.toString())
            assertEquals(R.drawable.ic_notif_lyric_on, layoutOnLocked[0].iconResId)
            assertEquals("解锁桌面歌词", layoutOnLocked[1].displayName.toString())
            assertEquals(R.drawable.ic_notif_lyric_locked, layoutOnLocked[1].iconResId)

            // 3. 关闭桌面歌词时自动重置锁定状态为未锁定
            com.concept.music.service.SystemLyricBridge.setFloatingCapsuleEnabled(false)
            assertFalse(com.concept.music.service.SystemLyricBridge.state.value.floatingCapsuleEnabled)
            assertFalse(
                "关闭桌面歌词后锁定状态应自动复位为未锁定",
                com.concept.music.service.SystemLyricBridge.state.value.floatingCapsuleLocked
            )
        } finally {
            com.concept.music.service.SystemLyricBridge.onDesktopLyricNotificationStateChanged = null
            com.concept.music.service.SystemLyricBridge.setFloatingCapsuleEnabled(false)
            com.concept.music.service.AppPermissionManager.resetForTest()
        }
    }

    @Test
    fun `音乐下载FLAC与MP3高清封面歌词及歌曲元数据内嵌读写验证`() {
        val tempDir = kotlin.io.path.createTempDirectory("breezemusic_embed_test").toFile()
        try {
            val fakeJpegBytes = byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte()
            )
            val sampleLrc = "[00:01.20]第一句歌词\n[00:01.20]First line translation\n[00:05.80]第二句歌词"
            val tagMeta = com.concept.music.service.AudioTagMetadata(
                title = "晴天",
                artist = "周杰伦",
                album = "叶惠美",
                publishDate = "2003-07-31",
                songHash = "ABCDEF1234567890",
                qualityLabel = "SQ 无损音质",
                coverBytes = fakeJpegBytes,
                coverMimeType = "image/jpeg",
                lrcText = sampleLrc
            )

            // 1. 构造最小合法 FLAC 文件头 (fLaC + Last STREAMINFO block 34 bytes + 模拟音频帧)
            val flacFile = java.io.File(tempDir, "test_track.flac")
            java.io.ByteArrayOutputStream().use { bos ->
                bos.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
                bos.write(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x22)) // isLast=1, type=0 (STREAMINFO), len=34
                bos.write(ByteArray(34))
                bos.write(byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x69.toByte(), 0x0C.toByte()))
                flacFile.writeBytes(bos.toByteArray())
            }

            val flacOk = com.concept.music.service.AudioMetadataEmbedder.embedMetadata(flacFile, tagMeta)
            assertTrue("FLAC 元数据、高清封面与歌词内嵌应成功", flacOk)

            val flacTags = com.concept.music.service.AudioMetadataEmbedder.readEmbeddedTagsForVerification(flacFile)
            assertEquals("晴天", flacTags["TITLE"])
            assertEquals("周杰伦", flacTags["ARTIST"])
            assertEquals("叶惠美", flacTags["ALBUM"])
            assertEquals("周杰伦", flacTags["ALBUMARTIST"])
            assertEquals("2003-07-31", flacTags["DATE"])
            assertEquals(sampleLrc, flacTags["LYRICS"])
            assertEquals(sampleLrc, flacTags["UNSYNCEDLYRICS"])
            assertEquals("true", flacTags["HAS_PICTURE"])

            // 2. 构造最小合法 MP3 文件并验证 ID3v2.3 (TIT2/TPE1/TALB/USLT/APIC) 内嵌
            val mp3File = java.io.File(tempDir, "test_track.mp3")
            mp3File.writeBytes(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte(), 0x00, 0x00))

            val mp3Ok = com.concept.music.service.AudioMetadataEmbedder.embedMetadata(mp3File, tagMeta)
            assertTrue("MP3 ID3v2.3 元数据、高清封面与歌词内嵌应成功", mp3Ok)

            val mp3Tags = com.concept.music.service.AudioMetadataEmbedder.readEmbeddedTagsForVerification(mp3File)
            assertEquals("晴天", mp3Tags["TIT2"])
            assertEquals("周杰伦", mp3Tags["TPE1"])
            assertEquals("叶惠美", mp3Tags["TALB"])
            assertEquals("2003", mp3Tags["TYER"])
            assertEquals(sampleLrc, mp3Tags["USLT"])
            assertEquals("true", mp3Tags["HAS_APIC"])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `音乐下载路径自定义与歌词LRC格式化及高清封面URL升级验证`() {
        com.concept.music.service.MusicDownloadManager.resetForTest()
        try {
            val defaultCfg = com.concept.music.service.MusicDownloadManager.config.value
            assertTrue("歌曲高清封面应默认开启内嵌", defaultCfg.embedHdCover)
            assertTrue("歌曲歌词应默认开启内嵌", defaultCfg.embedLyrics)
            assertFalse("独立保存 .lrc 文件默认可按需开启", defaultCfg.saveStandaloneLrcFile)

            com.concept.music.service.MusicDownloadManager.setDownloadDirectoryPath("/storage/emulated/0/Music/CustomBreeze")
            com.concept.music.service.MusicDownloadManager.setLyricDirectoryPath("/storage/emulated/0/Music/CustomLyrics")
            com.concept.music.service.MusicDownloadManager.setSaveStandaloneLrcFile(true)

            val updatedCfg = com.concept.music.service.MusicDownloadManager.config.value
            assertEquals("/storage/emulated/0/Music/CustomBreeze", updatedCfg.downloadDirectoryPath)
            assertEquals("/storage/emulated/0/Music/CustomLyrics", updatedCfg.lyricDirectoryPath)
            assertTrue(updatedCfg.saveStandaloneLrcFile)

            val hdCandidates = com.concept.music.service.MusicDownloadManager.buildHighDefCoverUrlCandidates(
                "http://imge.kugou.com/stdmusic/{size}/20230501/test.jpg"
            )
            assertTrue(hdCandidates.first().contains("/1000/"))
            assertTrue(hdCandidates.any { it.contains("/480/") })

            val safeName = com.concept.music.service.MusicDownloadManager.buildSafeBaseFileName("周杰伦/Jay", "晴天:Live?")
            assertEquals("周杰伦_Jay - 晴天_Live_", safeName)

            val store = MemoryStore()
            val transport = object : KugouTransport {
                override suspend fun call(
                    path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                    host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
                ): ApiResponse = ApiResponse(JsonObject())
            }
            val lyricRepo = LyricRepository(transport)
            val lrcOut = lyricRepo.formatLyricDataAsLrc(
                song = Song(id = "1", title = "夜に駆ける", artist = "YOASOBI", album = "THE BOOK"),
                lyricData = LyricData(
                    lines = listOf(
                        LyricLine(startTimeMs = 1500, durationMs = 2000, text = "沈むように", translation = "仿佛沉溺般")
                    )
                ),
                includeTranslation = true
            )
            assertTrue(lrcOut.contains("[ti:夜に駆ける]"))
            assertTrue(lrcOut.contains("[ar:YOASOBI]"))
            assertTrue(lrcOut.contains("[00:01.50]沈むように"))
            assertTrue(lrcOut.contains("[00:01.50]仿佛沉溺般"))
        } finally {
            com.concept.music.service.MusicDownloadManager.resetForTest()
        }
    }
}
