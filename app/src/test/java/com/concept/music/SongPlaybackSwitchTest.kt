package com.concept.music

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.concept.music.data.kugou.*
import com.concept.music.data.model.*
import com.concept.music.data.repository.PlaybackHistoryRepository
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.viewmodel.MusicPlayerViewModel
import com.concept.music.ui.viewmodel.PlayMode
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class SongPlaybackSwitchTest {

    private val testDispatcher = StandardTestDispatcher()

    private val song1 = Song(
        id = "song_1",
        hash = "hash_1",
        title = "第一首歌",
        artist = "歌手A",
        durationMs = 200_000L,
        availableQualities = listOf(AudioQuality.STANDARD, AudioQuality.LOSSLESS)
    )

    private val song2 = Song(
        id = "song_2",
        hash = "hash_2",
        title = "第二首歌",
        artist = "歌手B",
        durationMs = 180_000L,
        availableQualities = listOf(AudioQuality.STANDARD, AudioQuality.HIGH, AudioQuality.HI_RES)
    )

    private val song3 = Song(
        id = "song_3",
        hash = "hash_3",
        title = "第三首歌",
        artist = "歌手C",
        durationMs = 240_000L,
        availableQualities = listOf(AudioQuality.STANDARD)
    )

    private val lyricData1 = LyricData(
        lines = listOf(
            LyricLine(startTimeMs = 0L, durationMs = 5000L, text = "第一首歌歌词 第一行"),
            LyricLine(startTimeMs = 5000L, durationMs = 5000L, text = "第一首歌歌词 第二行")
        )
    )

    private val lyricData2 = LyricData(
        lines = listOf(
            LyricLine(startTimeMs = 0L, durationMs = 4000L, text = "第二首歌歌词 第一行"),
            LyricLine(startTimeMs = 4000L, durationMs = 6000L, text = "第二首歌歌词 第二行")
        )
    )

    private val lyricData3 = LyricData(
        lines = listOf(
            LyricLine(startTimeMs = 0L, durationMs = 3000L, text = "第三首歌歌词 第一行")
        )
    )

    private lateinit var fakePlayerHolder: FakePlayerHolder
    private lateinit var viewModel: MusicPlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // 1. 设置模拟 SharedPreferences
        val editorProxy = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> true
                Void.TYPE -> null
                else -> proxy
            }
        } as SharedPreferences.Editor

        val prefsProxy = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> args?.getOrNull(1) ?: "VINYL"
                "getBoolean" -> (args?.getOrNull(1) as? Boolean) ?: false
                "getFloat" -> (args?.getOrNull(1) as? Float) ?: 1.0f
                "getInt" -> (args?.getOrNull(1) as? Int) ?: 0
                "getLong" -> (args?.getOrNull(1) as? Long) ?: 0L
                "edit" -> editorProxy
                else -> null
            }
        } as SharedPreferences

        val app = TestApplication(prefsProxy)

        // 2. 初始化 VipManager 用于测试
        val dummyTransport = object : KugouTransport {
            override suspend fun call(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())
        }

        val dummyStore = TestSessionStore()
        val authRepo = AuthRepository(dummyTransport, dummyStore)
        val accountRepo = AccountRepository(dummyStore, authRepo, dummyTransport)
        accountRepo.select("user_1")

        val verification = VerificationCoordinator(dummyTransport)
        val vipRepo = VipRepository(dummyTransport, accountRepo, verification)

        // 模拟 LyricRepository，使用 internal 测试辅助方法预填充歌词缓存（零反射）
        val lyricRepo = LyricRepository(dummyTransport)
        lyricRepo.putCacheForTest(song1.id, lyricData1)
        lyricRepo.putCacheForTest(song2.id, lyricData2)
        lyricRepo.putCacheForTest(song3.id, lyricData3)

        val musicRepo = MusicApiRepository(dummyTransport, accountRepo) { dummyStore.device() }

        // 预设音频解析结果缓存（零反射）
        musicRepo.putPlaybackCacheForTest("hash_1_${AudioQuality.LOSSLESS.apiValue}", PlaybackResult("http://audio.kugou.com/1.mp3", AudioQuality.LOSSLESS, 850, 20_000_000L))
        musicRepo.putPlaybackCacheForTest("hash_1_${AudioQuality.HIGH.apiValue}", PlaybackResult("http://audio.kugou.com/1.mp3", AudioQuality.HIGH, 320, 10_000_000L))
        musicRepo.putPlaybackCacheForTest("hash_1_${AudioQuality.STANDARD.apiValue}", PlaybackResult("http://audio.kugou.com/1.mp3", AudioQuality.STANDARD, 128, 5_000_000L))
        musicRepo.putPlaybackCacheForTest("hash_2_${AudioQuality.HI_RES.apiValue}", PlaybackResult("http://audio.kugou.com/2.mp3", AudioQuality.HI_RES, 1500, 40_000_000L))
        musicRepo.putPlaybackCacheForTest("hash_2_${AudioQuality.HIGH.apiValue}", PlaybackResult("http://audio.kugou.com/2.mp3", AudioQuality.HIGH, 320, 10_000_000L))
        musicRepo.putPlaybackCacheForTest("hash_2_${AudioQuality.STANDARD.apiValue}", PlaybackResult("http://audio.kugou.com/2.mp3", AudioQuality.STANDARD, 128, 4_000_000L))
        musicRepo.putPlaybackCacheForTest("hash_3_${AudioQuality.HIGH.apiValue}", PlaybackResult("http://audio.kugou.com/3.mp3", AudioQuality.STANDARD, 128, 6_000_000L))
        musicRepo.putPlaybackCacheForTest("hash_3_${AudioQuality.STANDARD.apiValue}", PlaybackResult("http://audio.kugou.com/3.mp3", AudioQuality.STANDARD, 128, 6_000_000L))

        val historyRepo = PlaybackHistoryRepository(null)

        VipManager.initializeForTest(
            accounts = accountRepo,
            auth = authRepo,
            vip = vipRepo,
            verification = verification,
            music = musicRepo,
            lyric = lyricRepo,
            history = historyRepo
        )

        fakePlayerHolder = FakePlayerHolder()
        viewModel = MusicPlayerViewModel(app)
        viewModel.playerOverride = fakePlayerHolder.player
    }

    @After
    fun tearDown() {
        KugouCsccReporter.eventObserverForTest = null
        runCatching { androidx.lifecycle.ViewModel::class.java.declaredMethods.firstOrNull { it.name.startsWith("clear") }?.apply { isAccessible = true }?.invoke(viewModel) }
        Dispatchers.resetMain()
        runCatching { VipManager.accounts.select("user_1") }
    }

    @Test
    fun testInitialPlayback_SetsCorrectAudioAndLyric() = runTest(testDispatcher) {
        val queue = listOf(song1, song2, song3)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("第一首歌", state.currentSong?.title)
        assertEquals(0, state.currentIndex)
        assertEquals("第一首歌歌词 第一行", state.currentLyric.lines.firstOrNull()?.text)
        assertEquals("song_1", fakePlayerHolder.currentMediaItem?.mediaId)
    }

    @Test
    fun testSkipToNext_SwitchesAudioAndLyricSynchronously() = runTest(testDispatcher) {
        val queue = listOf(song1, song2, song3)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()

        // 第一次点击下一首：应该切到 song2
        viewModel.skipToNext()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("第二首歌", state.currentSong?.title)
        assertEquals(1, state.currentIndex)
        assertEquals("song_2", state.currentSong?.id)
        assertEquals("第二首歌歌词 第一行", state.currentLyric.lines.firstOrNull()?.text)
        assertEquals("song_2", fakePlayerHolder.currentMediaItem?.mediaId)
    }

    @Test
    fun testConsecutiveSkipToNext_AdvancesToThirdSongWithoutRepeatingSecond() = runTest(testDispatcher) {
        val queue = listOf(song1, song2, song3)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()

        // 第一次点下一首：切到 song2
        viewModel.skipToNext()
        testScheduler.advanceUntilIdle()
        assertEquals("第二首歌", viewModel.uiState.value.currentSong?.title)
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals("第二首歌歌词 第一行", viewModel.uiState.value.currentLyric.lines.firstOrNull()?.text)

        // 第二次点下一首：必须切到 song3，音频与歌词必须同步切换到第三首，绝不会停留在第二首歌词或重复播放第二首
        viewModel.skipToNext()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("第三首歌", state.currentSong?.title)
        assertEquals(2, state.currentIndex)
        assertEquals("song_3", state.currentSong?.id)
        assertEquals("第三首歌歌词 第一行", state.currentLyric.lines.firstOrNull()?.text)
        assertEquals("song_3", fakePlayerHolder.currentMediaItem?.mediaId)
    }

    @Test
    fun testSkipToPrevious_SwitchesToPreviousSong() = runTest(testDispatcher) {
        val queue = listOf(song1, song2, song3)
        viewModel.playSong(song2, queue)
        testScheduler.advanceUntilIdle()

        assertEquals("第二首歌", viewModel.uiState.value.currentSong?.title)

        viewModel.skipToPrevious()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("第一首歌", state.currentSong?.title)
        assertEquals(0, state.currentIndex)
        assertEquals("第一首歌歌词 第一行", state.currentLyric.lines.firstOrNull()?.text)
    }

    @Test
    fun testSyncPlayer_DoesNotCancelResolutionJobOrRollbackSong() = runTest(testDispatcher) {
        val queue = listOf(song1, song2, song3)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()

        // 开始播放 song2，此时正在解析中
        viewModel.playSong(song2, queue)

        // 模拟 progress updates 周期触发 syncPlayer
        viewModel.syncPlayer()

        // 验证：当前歌曲仍然是 song2，解析任务未被撤销或回滚回 song1
        assertEquals("第二首歌", viewModel.uiState.value.currentSong?.title)
        assertEquals(1, viewModel.uiState.value.currentIndex)

        testScheduler.advanceUntilIdle()
        assertEquals("第二首歌", viewModel.uiState.value.currentSong?.title)
        assertEquals("第二首歌歌词 第一行", viewModel.uiState.value.currentLyric.lines.firstOrNull()?.text)
    }

    @Test
    fun testPlayNext_InsertionMaintainsCurrentIndexCorrectly() = runTest(testDispatcher) {
        val queue = listOf(song1, song2)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()

        viewModel.playNext(song3)

        val updatedQueue = viewModel.uiState.value.playlistQueue
        assertEquals(3, updatedQueue.size)
        assertEquals("song_1", updatedQueue[0].id)
        assertEquals("song_3", updatedQueue[1].id)
        assertEquals("song_2", updatedQueue[2].id)

        // 下一次切歌直接切到插入的 song3
        viewModel.skipToNext()
        testScheduler.advanceUntilIdle()

        assertEquals("第三首歌", viewModel.uiState.value.currentSong?.title)
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals("第三首歌歌词 第一行", viewModel.uiState.value.currentLyric.lines.firstOrNull()?.text)
    }

    @Test
    fun testAutoTransition_UpdatesStateAndLoadsLyric() = runTest(testDispatcher) {
        val queue = listOf(song1, song2, song3)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()

        // 缓存 song2 到 songsByMediaId
        viewModel.songsByMediaId[song2.id] = song2

        // 模拟 ExoPlayer 自动流转 (reason = MEDIA_ITEM_TRANSITION_REASON_AUTO)
        viewModel.handleSongTransition(song2.id, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        testScheduler.advanceUntilIdle()

        var retries = 0
        while (viewModel.uiState.value.currentLyric.lines.isEmpty() && retries < 25) {
            Thread.sleep(20)
            testScheduler.advanceUntilIdle()
            retries++
        }

        val state = viewModel.uiState.value
        assertEquals("第二首歌", state.currentSong?.title)
        assertEquals(1, state.currentIndex)
        assertEquals("第二首歌歌词 第一行", state.currentLyric.lines.firstOrNull()?.text)
    }

    @Test
    fun testGuestModePlayback_AllowedWithoutLoginBlock() = runTest(testDispatcher) {
        VipManager.accounts.select(null)
        testScheduler.advanceUntilIdle()
        assertNull(VipManager.accounts.state.value.currentId)
        VipManager.music.putPlaybackCacheForTest("hash_1_${AudioQuality.LOSSLESS.apiValue}", PlaybackResult("http://audio.kugou.com/1.mp3", AudioQuality.LOSSLESS, 850, 20_000_000L))
        VipManager.music.putPlaybackCacheForTest("hash_1_${AudioQuality.HIGH.apiValue}", PlaybackResult("http://audio.kugou.com/1.mp3", AudioQuality.HIGH, 320, 10_000_000L))
        VipManager.music.putPlaybackCacheForTest("hash_1_${AudioQuality.STANDARD.apiValue}", PlaybackResult("http://audio.kugou.com/1.mp3", AudioQuality.STANDARD, 128, 5_000_000L))

        val queue = listOf(song1, song2)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals("第一首歌", state.currentSong?.title)
        assertEquals("song_1", fakePlayerHolder.currentMediaItem?.mediaId)
    }

    @Test
    fun testPlaySongInCurrentQueue_PreservesExistingQueueAndInsertsSong() = runTest(testDispatcher) {
        val queue = listOf(song1, song2)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()

        // 在保持原队列 [song1, song2] 的情况下立即播放队列外的 song3
        viewModel.playSongInCurrentQueue(song3)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("第三首歌", state.currentSong?.title)
        assertEquals(3, state.playlistQueue.size)
        assertEquals(listOf("song_1", "song_3", "song_2"), state.playlistQueue.map { it.id })
        assertEquals(1, state.currentIndex)
    }

    @Test
    fun testOpenCommentsForSong_DoesNotInterruptCurrentPlaybackOrWipeQueue() = runTest(testDispatcher) {
        val queue = listOf(song1, song2)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()

        // 查看 song3 的评论，不应打断 song1 的播放或清空队列
        viewModel.openCommentsForSong(song3)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("第一首歌", state.currentSong?.title)
        assertEquals(2, state.playlistQueue.size)
        assertEquals("song_3", state.commentTargetSong?.id)
    }

    @Test
    fun testReorderQueueByMoodAndMoveQueueItem_ClearsStalePrefetchedMediaItems() = runTest(testDispatcher) {
        val queue = listOf(song1, song2, song3)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()

        // 模拟 ExoPlayer 队列中存在旧的预加载项 (mediaItems.size == 2)
        val staleNextItem = MediaItem.Builder().setMediaId("stale_song_id").build()
        fakePlayerHolder.mediaItems.add(staleNextItem)
        assertTrue(fakePlayerHolder.mediaItems.any { it.mediaId == "stale_song_id" })

        // 执行拖拽移位，应立即清理旧预加载项并重新预加载
        viewModel.clearPrefetchedPlayerItems()
        assertEquals(1, fakePlayerHolder.mediaItems.size)
        assertEquals("song_1", fakePlayerHolder.mediaItems.first().mediaId)

        fakePlayerHolder.mediaItems.add(staleNextItem)
        viewModel.moveQueueItem(2, 1)
        assertFalse(fakePlayerHolder.mediaItems.any { it.mediaId == "stale_song_id" })
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun testPlaySong_SetsOptimisticPlayingStateImmediately() = runTest(testDispatcher) {
        val queue = listOf(song1, song2)
        viewModel.playSong(song1, queue)

        // 点击歌曲的同一帧（在异步协程完成前），UI 与进度快照应立即呈现播放态
        assertTrue(viewModel.uiState.value.isPlaying)
        assertTrue(viewModel.playbackPositionFlow.value.isPlaying)

        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun testSeekTo_WhileBufferingWithPlayWhenReady_KeepsPlayingStateTrueAndLocksTargetPos() = runTest(testDispatcher) {
        val queue = listOf(song1, song2)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPlaying)

        // 模拟 ExoPlayer 在 seekTo 时进入 STATE_BUFFERING（此时 player.isPlaying 为 false，但 playWhenReady 仍为 true，且底层 position 短暂滞后）
        fakePlayerHolder.simulateBufferingOnSeek = true
        fakePlayerHolder.lagPositionOnSeek = 12_000L

        viewModel.seekTo(85_000L)

        // 验证：播放状态绝不闪变为 false，且当前位置锁定在目标 85_000L 不回弹到 12_000L
        assertTrue("Seek 缓冲期间播放态必须保持为 true，不能跳回暂停", viewModel.uiState.value.isPlaying)
        assertTrue(viewModel.playbackPositionFlow.value.isPlaying)
        assertEquals(85_000L, viewModel.uiState.value.currentPositionMs)
        assertEquals(85_000L, viewModel.playbackPositionFlow.value.positionMs)

        // 缓冲完成后恢复 STATE_READY 并到达目标位置
        fakePlayerHolder.playbackState = Player.STATE_READY
        fakePlayerHolder.isPlaying = true
        fakePlayerHolder.currentPosition = 85_120L
        viewModel.syncPlayer()
        assertTrue(viewModel.uiState.value.isPlaying)
        assertEquals(85_120L, viewModel.playbackPositionFlow.value.positionMs)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun testBackgroundThrottledTick_DoesNotClipDurationTo2Seconds() = runTest(testDispatcher) {
        var fakeNowMs = 1_000_000L
        viewModel.elapsedRealtimeProviderForTest = { fakeNowMs }
        val recordedEvents = mutableListOf<Triple<String, String, Long>>()
        KugouCsccReporter.eventObserverForTest = { type, song, durMs, _ ->
            recordedEvents += Triple(type, song.id, durMs)
        }

        val queue = listOf(song1, song2)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()
        viewModel.syncPlayer()

        // 模拟后台息屏协程被系统冻结 45 秒后才唤醒一次（旧逻辑 coerceAtMost(2_000L) 会截断为 2000ms）
        fakeNowMs += 45_000L
        fakePlayerHolder.currentPosition = 45_000L
        viewModel.syncPlayer()

        assertEquals(45_000L, viewModel.currentEffectivePlayedMs())

        // 切歌结算，验证完整上报 45_000ms 而非被截断的 2_000ms
        viewModel.skipToNext()
        testScheduler.advanceUntilIdle()

        val endEvents = recordedEvents.filter { it.first == "end" && it.second == "song_1" }
        assertEquals(1, endEvents.size)
        assertEquals(45_000L, endEvents.single().third)
    }

    @Test
    fun testPauseAfter10Seconds_ImmediatelySettlesCsccAndResumeStartsNewSegment() = runTest(testDispatcher) {
        var fakeNowMs = 2_000_000L
        viewModel.elapsedRealtimeProviderForTest = { fakeNowMs }
        val recordedEvents = mutableListOf<Triple<String, String, Long>>()
        KugouCsccReporter.eventObserverForTest = { type, song, durMs, _ ->
            recordedEvents += Triple(type, song.id, durMs)
        }

        val queue = listOf(song1, song2)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()
        viewModel.syncPlayer()

        // 播放 25 秒后点击暂停（如用户暂停去官方酷狗查看时长）
        fakeNowMs += 25_000L
        fakePlayerHolder.currentPosition = 25_000L
        viewModel.togglePlayPause()
        viewModel.syncPlayer()

        val pauseEnds = recordedEvents.filter { it.first == "end" && it.second == "song_1" }
        assertEquals("暂停满 10 秒必须立即结算 CSCC end", 1, pauseEnds.size)
        assertEquals(25_000L, pauseEnds.single().third)

        // 暂停期间经过 30 秒，不应计入播放时长
        fakeNowMs += 30_000L
        viewModel.syncPlayer()
        assertEquals(0L, viewModel.currentEffectivePlayedMs())

        // 恢复播放并再听 18 秒后切歌
        viewModel.togglePlayPause()
        viewModel.syncPlayer()
        val startsForSong1 = recordedEvents.filter { it.first == "start" && it.second == "song_1" }
        assertEquals("从已结算暂停恢复播放时必须重新发送 CSCC start 开启新闭环", 2, startsForSong1.size)

        fakeNowMs += 18_000L
        fakePlayerHolder.currentPosition = 43_000L
        viewModel.skipToNext()
        testScheduler.advanceUntilIdle()

        val allEndsForSong1 = recordedEvents.filter { it.first == "end" && it.second == "song_1" }
        assertEquals(2, allEndsForSong1.size)
        assertEquals(listOf(25_000L, 18_000L), allEndsForSong1.map { it.third })
    }

    @Test
    fun testAutoTransition_ReportsCsccEndExactlyOnceWithoutDuplicate() = runTest(testDispatcher) {
        var fakeNowMs = 3_000_000L
        viewModel.elapsedRealtimeProviderForTest = { fakeNowMs }
        val recordedEvents = mutableListOf<Triple<String, String, Long>>()
        KugouCsccReporter.eventObserverForTest = { type, song, durMs, _ ->
            recordedEvents += Triple(type, song.id, durMs)
        }

        val queue = listOf(song1, song2)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()
        viewModel.syncPlayer()
        viewModel.songsByMediaId[song2.id] = song2

        // 模拟第一首播放 50 秒后由 ExoPlayer 自动切到第二首 (onMediaItemTransition 依次调用 handleSongTransition + syncPlayer)
        fakeNowMs += 50_000L
        val song2Item = MediaItem.Builder().setMediaId(song2.id).build()
        fakePlayerHolder.mediaItems.clear()
        fakePlayerHolder.mediaItems.add(song2Item)
        fakePlayerHolder.currentMediaItemIndex = 0
        viewModel.listener.onMediaItemTransition(song2Item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        testScheduler.advanceUntilIdle()

        val endEventsForSong1 = recordedEvents.filter { it.first == "end" && it.second == "song_1" }
        assertEquals("自动切歌时绝不能重复发送两次 CSCC end", 1, endEventsForSong1.size)
        assertEquals(50_000L, endEventsForSong1.single().third)
    }

    @Test
    fun testRepeatOne_StartsNewCsccSegmentOnEachRepeat() = runTest(testDispatcher) {
        var fakeNowMs = 4_000_000L
        viewModel.elapsedRealtimeProviderForTest = { fakeNowMs }
        val recordedEvents = mutableListOf<Triple<String, String, Long>>()
        KugouCsccReporter.eventObserverForTest = { type, song, durMs, _ ->
            recordedEvents += Triple(type, song.id, durMs)
        }

        viewModel.playSong(song1, listOf(song1))
        testScheduler.advanceUntilIdle()
        viewModel.syncPlayer()

        // 第一遍播放 55 秒后触发单曲循环重播
        fakeNowMs += 55_000L
        viewModel.handleSongTransition(song1.id, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        viewModel.syncPlayer()

        // 第二遍播放 40 秒后暂停
        fakeNowMs += 40_000L
        viewModel.togglePlayPause()
        viewModel.syncPlayer()

        val startCount = recordedEvents.count { it.first == "start" && it.second == "song_1" }
        val endDurations = recordedEvents.filter { it.first == "end" && it.second == "song_1" }.map { it.third }
        assertEquals("单曲循环每一遍都必须发送 start 事件", 2, startCount)
        assertEquals(listOf(55_000L, 40_000L), endDurations)
    }

    @Test
    fun testSeekToWhilePausedDoesNotAccumulateDurationOrReopenSegment() = runTest(testDispatcher) {
        var fakeNowMs = 5_000_000L
        viewModel.elapsedRealtimeProviderForTest = { fakeNowMs }
        val recordedEvents = mutableListOf<Triple<String, String, Long>>()
        KugouCsccReporter.eventObserverForTest = { event, song, dur, _ ->
            recordedEvents.add(Triple(event, song.id, dur))
        }

        viewModel.playSong(song1, listOf(song1))
        testScheduler.advanceUntilIdle()
        viewModel.syncPlayer()

        // 正常播放 25 秒
        fakeNowMs += 25_000L
        viewModel.syncPlayer()

        // 点击暂停
        viewModel.togglePlayPause()
        testScheduler.advanceUntilIdle()
        viewModel.syncPlayer()

        val eventsAfterPause = recordedEvents.toList()
        assertEquals("应上报 start 与 25s 的 end 事件", 2, eventsAfterPause.size)
        assertEquals(Triple("start", "song_1", 0L), eventsAfterPause[0])
        assertEquals(Triple("end", "song_1", 25_000L), eventsAfterPause[1])

        // 暂停状态下多次拖动进度条
        fakeNowMs += 5_000L
        viewModel.seekTo(10_000L)
        fakeNowMs += 5_000L
        viewModel.seekTo(50_000L)
        fakeNowMs += 10_000L
        viewModel.seekTo(80_000L)
        testScheduler.advanceUntilIdle()
        viewModel.syncPlayer()

        // 验证暂停拖动期间绝不上报新的 start/end 事件，也不虚增时长
        assertEquals("暂停拖拽期间绝不能重新开启 segment 或触发 CSCC 事件", 2, recordedEvents.size)
    }

    @Test
    fun testAllowPlayWithOtherAppsAndWiredHeadsetControlSettingsToggle() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.allowPlayWithOtherApps)
        viewModel.setAllowPlayWithOtherApps(true)
        assertTrue(viewModel.uiState.value.allowPlayWithOtherApps)
        viewModel.setAllowPlayWithOtherApps(false)
        assertFalse(viewModel.uiState.value.allowPlayWithOtherApps)

        viewModel.setWiredHeadsetControlEnabled(false)
        assertFalse(viewModel.uiState.value.wiredHeadsetControlEnabled)
        assertFalse(com.concept.music.service.PlaybackService.headsetButtonController.multiClickSkipEnabled)

        viewModel.setWiredHeadsetControlEnabled(true)
        assertTrue(viewModel.uiState.value.wiredHeadsetControlEnabled)
        assertTrue(com.concept.music.service.PlaybackService.headsetButtonController.multiClickSkipEnabled)
    }

    private class TestSessionStore : SessionStore {
        var rows = listOf(SavedAccount("user_1"))
        var activeKey: String? = "user_1"
        val sessions = mutableMapOf("user_1" to KugouSession("user_1", "token_1"))
        override fun accounts(): List<SavedAccount> = rows
        override fun saveAccounts(accounts: List<SavedAccount>) { rows = accounts }
        override fun activeId(): String? = activeKey
        override fun setActive(id: String?) { activeKey = id }
        override fun session(id: String): KugouSession? = sessions[id]
        override fun saveSession(session: KugouSession) { sessions[session.userId] = session }
        override fun removeSession(id: String) { sessions.remove(id) }
        override fun device(): DeviceIdentity = DeviceIdentity("fixture", "1", "device")
        override fun saveDfid(dfid: String) = Unit
    }

    private class TestApplication(private val prefs: SharedPreferences) : Application() {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getPackageName(): String = "com.concept.music"
    }

    private class FakePlayerHolder {
        var isPlaying = false
        var playWhenReady = false
        var simulateBufferingOnSeek = false
        var lagPositionOnSeek: Long? = null
        var playbackState = Player.STATE_READY
        var currentMediaItemIndex = 0
        val mediaItems = mutableListOf<MediaItem>()
        var currentPosition = 0L
        var duration = 200_000L
        var bufferedPosition = 0L
        var repeatMode = Player.REPEAT_MODE_OFF
        val listeners = mutableListOf<Player.Listener>()

        val currentMediaItem: MediaItem?
            get() = mediaItems.getOrNull(currentMediaItemIndex)

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "isPlaying" -> isPlaying
                "getPlayWhenReady" -> playWhenReady
                "getPlaybackState" -> playbackState
                "getCurrentMediaItemIndex" -> currentMediaItemIndex
                "getCurrentMediaItem" -> currentMediaItem
                "getMediaItemCount" -> mediaItems.size
                "getMediaItemAt" -> {
                    val idx = args[0] as Int
                    mediaItems.getOrNull(idx)
                }
                "hasNextMediaItem" -> currentMediaItemIndex < mediaItems.size - 1
                "getCurrentPosition" -> currentPosition
                "getDuration" -> duration
                "getBufferedPosition" -> bufferedPosition
                "getRepeatMode" -> repeatMode
                "setRepeatMode" -> {
                    repeatMode = args[0] as Int
                    null
                }
                "play" -> {
                    isPlaying = true
                    playWhenReady = true
                    listeners.toList().forEach { it.onIsPlayingChanged(true) }
                    null
                }
                "pause" -> {
                    isPlaying = false
                    playWhenReady = false
                    listeners.toList().forEach { it.onIsPlayingChanged(false) }
                    null
                }
                "prepare" -> null
                "seekTo" -> {
                    if (args != null && args.isNotEmpty()) {
                        currentPosition = lagPositionOnSeek ?: (args[0] as Long)
                        if (simulateBufferingOnSeek) {
                            playbackState = Player.STATE_BUFFERING
                            isPlaying = false
                        }
                    }
                    null
                }
                "setMediaItem" -> {
                    val item = args[0] as MediaItem
                    mediaItems.clear()
                    mediaItems.add(item)
                    currentMediaItemIndex = 0
                    listeners.toList().forEach { it.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) }
                    null
                }
                "addMediaItem" -> {
                    val item = args[0] as MediaItem
                    mediaItems.add(item)
                    null
                }
                "removeMediaItem" -> {
                    val idx = args[0] as Int
                    if (idx in mediaItems.indices) {
                        mediaItems.removeAt(idx)
                    }
                    null
                }
                "clearMediaItems" -> {
                    mediaItems.clear()
                    currentMediaItemIndex = 0
                    null
                }
                "stop" -> {
                    isPlaying = false
                    playWhenReady = false
                    null
                }
                "addListener" -> {
                    listeners.add(args[0] as Player.Listener)
                    null
                }
                "removeListener" -> {
                    listeners.remove(args[0] as Player.Listener)
                    null
                }
                "toString" -> "FakePlayerProxy"
                "hashCode" -> 42
                "equals" -> args != null && args.isNotEmpty() && args[0] === proxy
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Float::class.javaPrimitiveType -> 0f
                    Double::class.javaPrimitiveType -> 0.0
                    else -> null
                }
            }
        } as Player
    }
}
