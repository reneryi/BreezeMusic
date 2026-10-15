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

        // 模拟 LyricRepository，预填充歌词缓存
        val lyricRepo = LyricRepository(dummyTransport)
        val lyricCacheField = LyricRepository::class.java.getDeclaredField("cache")
        lyricCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val lyricCache = lyricCacheField.get(lyricRepo) as MutableMap<String, LyricData>
        lyricCache[song1.id] = lyricData1
        lyricCache[song2.id] = lyricData2
        lyricCache[song3.id] = lyricData3

        val musicRepo = MusicApiRepository(dummyTransport, accountRepo) { dummyStore.device() }

        // 预设音频解析结果缓存
        val playbackCacheField = MusicApiRepository::class.java.getDeclaredField("playbackResultCache")
        playbackCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val playbackCache = playbackCacheField.get(musicRepo) as MutableMap<String, PlaybackResult>
        playbackCache["hash_1_${AudioQuality.LOSSLESS.apiValue}"] = PlaybackResult("http://audio.kugou.com/1.mp3", AudioQuality.LOSSLESS, 850, 20_000_000L)
        playbackCache["hash_1_${AudioQuality.HIGH.apiValue}"] = PlaybackResult("http://audio.kugou.com/1.mp3", AudioQuality.HIGH, 320, 10_000_000L)
        playbackCache["hash_1_${AudioQuality.STANDARD.apiValue}"] = PlaybackResult("http://audio.kugou.com/1.mp3", AudioQuality.STANDARD, 128, 5_000_000L)
        playbackCache["hash_2_${AudioQuality.HI_RES.apiValue}"] = PlaybackResult("http://audio.kugou.com/2.mp3", AudioQuality.HI_RES, 1500, 40_000_000L)
        playbackCache["hash_2_${AudioQuality.HIGH.apiValue}"] = PlaybackResult("http://audio.kugou.com/2.mp3", AudioQuality.HIGH, 320, 10_000_000L)
        playbackCache["hash_2_${AudioQuality.STANDARD.apiValue}"] = PlaybackResult("http://audio.kugou.com/2.mp3", AudioQuality.STANDARD, 128, 4_000_000L)
        playbackCache["hash_3_${AudioQuality.HIGH.apiValue}"] = PlaybackResult("http://audio.kugou.com/3.mp3", AudioQuality.STANDARD, 128, 6_000_000L)
        playbackCache["hash_3_${AudioQuality.STANDARD.apiValue}"] = PlaybackResult("http://audio.kugou.com/3.mp3", AudioQuality.STANDARD, 128, 6_000_000L)

        val historyRepo = Proxy.newProxyInstance(
            PlaybackHistoryRepository::class.java.classLoader,
            arrayOf()
        ) { _, _, _ -> null } as? PlaybackHistoryRepository ?: PlaybackHistoryRepository(app)

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

        val queue = listOf(song1, song2)
        viewModel.playSong(song1, queue)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals("第一首歌", state.currentSong?.title)
        assertEquals("song_1", fakePlayerHolder.currentMediaItem?.mediaId)
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
                    listeners.toList().forEach { it.onIsPlayingChanged(true) }
                    null
                }
                "pause" -> {
                    isPlaying = false
                    listeners.toList().forEach { it.onIsPlayingChanged(false) }
                    null
                }
                "prepare" -> null
                "seekTo" -> {
                    if (args != null && args.isNotEmpty()) {
                        currentPosition = args[0] as Long
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
