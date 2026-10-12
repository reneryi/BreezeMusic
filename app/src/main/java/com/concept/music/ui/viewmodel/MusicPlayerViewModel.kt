package com.concept.music.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.Song
import com.concept.music.data.kugou.KugouCsccReporter
import com.concept.music.data.model.SongComment
import com.concept.music.data.model.SongDetailInfo
import com.concept.music.data.vip.ListeningGradeManager
import com.concept.music.data.vip.VipManager
import com.concept.music.service.AmbientSoundManager
import com.concept.music.service.AudioCacheManager
import com.concept.music.service.ConceptMusicWidgetUpdater
import com.concept.music.service.PlaybackService
import com.concept.music.service.SystemLyricBridge
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class PlayMode {
    REPEAT_ALL,  // 列表循环
    REPEAT_ONE,  // 单曲循环
    SHUFFLE      // 随机播放
}

enum class CoverStyle(val label: String) {
    VINYL("流光黑胶"),     // 流光黑胶唱片 + 物理阻尼唱臂
    CASSETTE("透明磁带"),  // 透明机械磁带双轴机芯 ([INNOV-02])
    POSTER("杂志海报"),    // 杂志大字海报嵌合逐字歌词 ([INNOV-02])
    CIRCLE("极简圆盘")     // 极简圆形封面旋转
}

enum class LyricAlignment {
    CENTER,
    LEFT
}

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playlistQueue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val playMode: PlayMode = PlayMode.REPEAT_ALL,
    val preferredQuality: AudioQuality = AudioQuality.HI_RES,
    val currentQualityLabel: String = "SQ 无损",
    val currentLyric: LyricData = LyricData(),
    val currentLyricIndex: Int = -1,
    val coverStyle: CoverStyle = CoverStyle.VINYL,
    val showTranslation: Boolean = true,
    val lyricFontSize: Float = 18f,
    val lyricAlignment: LyricAlignment = LyricAlignment.CENTER,
    val lyricWordByWord: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val linkPitchToSpeed: Boolean = false,
    val sleepTimerRemainingSec: Long = 0L,
    val sleepStopAfterCurrentSong: Boolean = false,
    val climaxModeEnabled: Boolean = false,
    val climaxUnlockedForCurrentSong: Boolean = false,
    val comments: List<SongComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentsPage: Int = 1,
    val commentsHasMore: Boolean = true,
    val commentType: String = "hot",
    val commentCount: Int = 0,
    val songDetail: SongDetailInfo? = null,
    val loading: Boolean = false,
    val lyricLoading: Boolean = false,
    val error: String? = null
)

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(PlayerUiState())
    val uiState = mutableState.asStateFlow()

    private val gson = Gson()
    private val prefs by lazy {
        application.applicationContext.getSharedPreferences("concept_player_prefs", android.content.Context.MODE_PRIVATE)
    }

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    internal var playerOverride: Player? = null
    internal val activePlayer: Player? get() = playerOverride ?: controller

    private var resolveJob: Job? = null
    private var lyricJob: Job? = null
    private var progressJob: Job? = null
    private var prefetchJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var persistStateJob: Job? = null
    private var climaxFadeInJob: Job? = null
    private var lastClimaxSkipSongId: String = ""

    private var currentUser: String? = VipManager.accounts.state.value.currentId
    internal val songsByMediaId = mutableMapOf<String, Song>()
    private var recordedMediaId: String? = null
    private var lastRecordTimestamp: Long = 0L
    private var lastPersistPosTimestamp: Long = 0L
    private var activePlayingSong: Song? = null
    private var songPlaybackStartTimestamp: Long = 0L
    private var nextShuffleIndex: Int = -1

    internal val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncPlayer()
            if (!isPlaying) {
                persistPlaybackStateAsync()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) {
                handleSongTransition(mediaItem.mediaId, reason)
            }
            syncPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncPlayer()
            if (playbackState == Player.STATE_ENDED) {
                onTrackEnded()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            mutableState.value = mutableState.value.copy(
                loading = false,
                isPlaying = false,
                error = error.message ?: "播放器解码失败，请稍后重试"
            )
        }
    }

    internal fun handleSongTransition(transitionedMediaId: String, reason: Int = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
        val currentSong = mutableState.value.currentSong
        if (currentSong != null && currentSong.id == transitionedMediaId) {
            return
        }

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            currentSong?.let { ListeningGradeManager.onSongCompleted(it) }
            // 若开启了“播完当前歌曲后停止”且倒计时已归零，则在切歌瞬间拦截并暂停
            if (mutableState.value.sleepStopAfterCurrentSong && mutableState.value.sleepTimerRemainingSec <= 0L) {
                activePlayer?.pause()
                activePlayer?.volume = 1f
                mutableState.value = mutableState.value.copy(
                    isPlaying = false,
                    sleepStopAfterCurrentSong = false
                )
                return
            }
        }

        val transitionedSong = songsByMediaId[transitionedMediaId]
            ?: mutableState.value.playlistQueue.find { it.id == transitionedMediaId }
            ?: return

        resolveJob?.cancel()
        prefetchJob?.cancel()

        val q = mutableState.value.playlistQueue
        val newIdx = q.indexOfFirst { it.id == transitionedSong.id }.takeIf { it >= 0 } ?: mutableState.value.currentIndex
        nextShuffleIndex = -1
        lastClimaxSkipSongId = ""

        val playQuality = transitionedSong.actualPlayQuality ?: transitionedSong.highestQuality
        val resolvedSong = transitionedSong.copy(
            actualPlayQuality = playQuality,
            availableQualities = transitionedSong.effectiveQualities
        )
        val transQualityLabel = when (playQuality) {
            AudioQuality.HI_RES -> "Hi-Res" + (if (transitionedSong.bitrateKbps > 0) " ${transitionedSong.bitrateKbps}k" else "")
            AudioQuality.LOSSLESS -> "SQ 无损" + (if (transitionedSong.bitrateKbps > 0) " ${transitionedSong.bitrateKbps}k" else "")
            AudioQuality.HIGH -> "高品 320K"
            AudioQuality.STANDARD -> "标准 128K"
        }

        val startPos = if (mutableState.value.climaxModeEnabled) resolvedSong.effectiveClimaxStartMs else 0L
        if (mutableState.value.climaxModeEnabled) {
            activePlayer?.seekTo(startPos)
            triggerClimaxSmoothFadeIn()
        } else {
            activePlayer?.volume = 1f
        }

        mutableState.value = mutableState.value.copy(
            currentSong = resolvedSong,
            currentQualityLabel = transQualityLabel,
            currentIndex = newIdx,
            currentPositionMs = startPos,
            durationMs = resolvedSong.durationMs,
            climaxUnlockedForCurrentSong = false,
            songDetail = VipManager.music.songDetail(resolvedSong)
        )

        persistPlaybackStateAsync()
        loadLyric(resolvedSong)
        loadComments(isRefresh = true)
        prefetchNextSong(q, newIdx)
    }

    init {
        val context = application.applicationContext
        restorePersistedPlaybackState()

        ConceptMusicWidgetUpdater.onWidgetCommand = { action ->
            viewModelScope.launch(Dispatchers.Main) {
                when (action) {
                    ConceptMusicWidgetUpdater.ACTION_WIDGET_TOGGLE_PLAY -> togglePlayPause()
                    ConceptMusicWidgetUpdater.ACTION_WIDGET_NEXT -> skipToNext()
                    ConceptMusicWidgetUpdater.ACTION_WIDGET_PREV -> skipToPrevious()
                }
            }
        }

        runCatching {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
                future.addListener({
                    runCatching { future.get() }.onSuccess { player ->
                        controller = player
                        player.addListener(listener)
                        val st = mutableState.value
                        player.playbackParameters = PlaybackParameters(st.playbackSpeed, st.playbackPitch)
                        syncPlayer()
                        startProgressUpdates()
                    }.onFailure { error ->
                        mutableState.value = mutableState.value.copy(error = error.message ?: "播放器连接失败")
                    }
                }, { runnable -> runnable.run() })
            }
        }

        viewModelScope.launch {
            VipManager.accounts.state.collect { state ->
                if (currentUser != state.currentId) {
                    val hadPreviousUser = currentUser != null
                    currentUser = state.currentId
                    if (hadPreviousUser && state.currentId == null) {
                        resolveJob?.cancel()
                        lyricJob?.cancel()
                        prefetchJob?.cancel()
                        activePlayer?.pause()
                        recordedMediaId = null
                        songsByMediaId.clear()
                        mutableState.value = PlayerUiState()
                    }
                }
            }
        }
    }

    private fun restorePersistedPlaybackState() {
        val savedCover = runCatching {
            CoverStyle.valueOf(prefs.getString("cover_style", CoverStyle.VINYL.name) ?: CoverStyle.VINYL.name)
        }.getOrDefault(CoverStyle.VINYL)
        val savedPlayMode = runCatching {
            PlayMode.valueOf(prefs.getString("play_mode", PlayMode.REPEAT_ALL.name) ?: PlayMode.REPEAT_ALL.name)
        }.getOrDefault(PlayMode.REPEAT_ALL)
        val savedQuality = runCatching {
            AudioQuality.valueOf(prefs.getString("preferred_quality", AudioQuality.HI_RES.name) ?: AudioQuality.HI_RES.name)
        }.getOrDefault(AudioQuality.HI_RES)
        val savedShowTrans = prefs.getBoolean("show_translation", true)
        val savedFontSize = prefs.getFloat("lyric_font_size", 18f).coerceIn(14f, 26f)
        val savedAlign = runCatching {
            LyricAlignment.valueOf(prefs.getString("lyric_alignment", LyricAlignment.CENTER.name) ?: LyricAlignment.CENTER.name)
        }.getOrDefault(LyricAlignment.CENTER)
        val savedWordByWord = prefs.getBoolean("lyric_word_by_word", true)
        val savedClimaxMode = prefs.getBoolean("climax_mode_enabled", false)

        val queueJson = prefs.getString("saved_queue_json", null)
        val restoredQueue: List<Song> = if (!queueJson.isNullOrBlank()) {
            runCatching {
                val type = object : TypeToken<List<Song>>() {}.type
                gson.fromJson<List<Song>>(queueJson, type) ?: emptyList()
            }.getOrDefault(emptyList())
        } else emptyList()

        val savedIndex = prefs.getInt("saved_current_index", 0)
            .coerceIn(0, (restoredQueue.size - 1).coerceAtLeast(0))
        val restoredSong = restoredQueue.getOrNull(savedIndex)
        val savedPosMs = prefs.getLong("saved_position_ms", 0L).coerceAtLeast(0L)

        mutableState.value = mutableState.value.copy(
            coverStyle = savedCover,
            playMode = savedPlayMode,
            preferredQuality = savedQuality,
            currentQualityLabel = savedQuality.label,
            showTranslation = savedShowTrans,
            lyricFontSize = savedFontSize,
            lyricAlignment = savedAlign,
            lyricWordByWord = savedWordByWord,
            climaxModeEnabled = savedClimaxMode,
            playlistQueue = restoredQueue,
            currentIndex = savedIndex,
            currentSong = restoredSong,
            currentPositionMs = if (restoredSong != null) savedPosMs else 0L,
            durationMs = restoredSong?.durationMs ?: 0L,
            songDetail = restoredSong?.let { VipManager.music.songDetail(it) }
        )

        if (restoredSong != null) {
            loadLyric(restoredSong)
            ConceptMusicWidgetUpdater.updateState(
                context = getApplication(),
                songTitle = restoredSong.title,
                artist = restoredSong.artist,
                isPlaying = false
            )
        }
    }

    private fun persistPlaybackStateAsync() {
        val snapshot = mutableState.value
        persistStateJob?.cancel()
        persistStateJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val cleanQueue = snapshot.playlistQueue.take(150).map { it.copy(playUrl = "") }
                val queueJson = gson.toJson(cleanQueue)
                prefs.edit()
                    .putString("cover_style", snapshot.coverStyle.name)
                    .putString("play_mode", snapshot.playMode.name)
                    .putString("preferred_quality", snapshot.preferredQuality.name)
                    .putBoolean("show_translation", snapshot.showTranslation)
                    .putFloat("lyric_font_size", snapshot.lyricFontSize)
                    .putString("lyric_alignment", snapshot.lyricAlignment.name)
                    .putBoolean("lyric_word_by_word", snapshot.lyricWordByWord)
                    .putBoolean("climax_mode_enabled", snapshot.climaxModeEnabled)
                    .putString("saved_queue_json", queueJson)
                    .putInt("saved_current_index", snapshot.currentIndex)
                    .putLong("saved_position_ms", snapshot.currentPositionMs)
                    .apply()
            }
        }
    }

    fun playSong(song: Song, queue: List<Song> = emptyList(), seekToPositionMs: Long = 0L) {
        val player = activePlayer ?: run {
            mutableState.value = mutableState.value.copy(error = "播放器正在连接，请稍后重试")
            return
        }

        val list = (queue.ifEmpty { listOf(song) }).distinctBy { it.id }
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val userId = VipManager.accounts.state.value.currentId
        if (userId == null) {
            mutableState.value = mutableState.value.copy(error = "请先登录后播放")
            return
        }

        resolveJob?.cancel()
        prefetchJob?.cancel()
        lastClimaxSkipSongId = ""
        loadLyric(song)

        mutableState.value = mutableState.value.copy(
            loading = true,
            error = null,
            playlistQueue = list,
            currentIndex = index,
            currentSong = song,
            currentPositionMs = seekToPositionMs,
            durationMs = song.durationMs,
            climaxUnlockedForCurrentSong = false,
            songDetail = VipManager.music.songDetail(song),
            comments = emptyList()
        )
        persistPlaybackStateAsync()

        resolveJob = viewModelScope.launch {
            try {
                val prefQuality = mutableState.value.preferredQuality
                // 默认采用音源实际拥有的最高音质或首选音质
                val playQuality = if (prefQuality in song.effectiveQualities) prefQuality else song.highestQuality
                val playbackResult = VipManager.music.resolvePlayback(song, playQuality)
                val url = song.playUrl.takeIf { it.startsWith("http") } ?: playbackResult.url

                if (VipManager.accounts.state.value.currentId != userId) return@launch

                val resolvedQuality = playbackResult.actualQuality
                val fullQualities = (song.effectiveQualities + resolvedQuality).let { qList ->
                    val full = qList.toMutableList()
                    if (resolvedQuality >= AudioQuality.HIGH && AudioQuality.HIGH !in full) full.add(AudioQuality.HIGH)
                    if (resolvedQuality >= AudioQuality.LOSSLESS && AudioQuality.LOSSLESS !in full) full.add(AudioQuality.LOSSLESS)
                    if (resolvedQuality == AudioQuality.HI_RES && AudioQuality.HI_RES !in full) full.add(AudioQuality.HI_RES)
                    if (AudioQuality.STANDARD !in full) full.add(AudioQuality.STANDARD)
                    full.distinct().sortedBy { it.ordinal }
                }
                val resolvedSong = song.copy(
                    playUrl = url,
                    actualPlayQuality = resolvedQuality,
                    availableQualities = fullQualities,
                    isLossless = song.isLossless || resolvedQuality >= AudioQuality.LOSSLESS,
                    isHiRes = song.isHiRes || resolvedQuality == AudioQuality.HI_RES,
                    bitrateKbps = playbackResult.bitrateKbps,
                    fileSizeByte = playbackResult.fileSizeByte,
                    climaxStartMs = playbackResult.climaxStartMs.takeIf { it > 0L } ?: song.climaxStartMs,
                    climaxEndMs = playbackResult.climaxEndMs.takeIf { it > 0L } ?: song.climaxEndMs
                )

                val mediaId = mediaId(resolvedSong)
                list.forEach { queued -> songsByMediaId[mediaId(queued)] = queued }
                songsByMediaId[mediaId] = resolvedSong

                val item = MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(resolvedSong.title)
                            .setArtist(resolvedSong.artist)
                            .setAlbumTitle(resolvedSong.album)
                            .setArtworkUri(resolvedSong.coverUrl.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse))
                            .build()
                    ).build()

                val targetSeekMs = if (seekToPositionMs > 0L) {
                    seekToPositionMs
                } else if (mutableState.value.climaxModeEnabled) {
                    resolvedSong.effectiveClimaxStartMs
                } else 0L

                if (targetSeekMs > 0L) {
                    player.pause() // 切换音质或定位时先暂停排空旧流音频缓冲区，杜绝混流爆音
                }
                player.setMediaItem(item)
                player.repeatMode = if (mutableState.value.playMode == PlayMode.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                player.prepare()
                if (targetSeekMs > 0L) {
                    player.seekTo(targetSeekMs)
                }
                if (mutableState.value.climaxModeEnabled && seekToPositionMs == 0L) {
                    triggerClimaxSmoothFadeIn()
                } else {
                    player.volume = 1f
                }
                player.play()

                val qualityLabel = when (resolvedQuality) {
                    AudioQuality.HI_RES -> "Hi-Res" + (if (playbackResult.bitrateKbps > 0) " ${playbackResult.bitrateKbps}k" else "")
                    AudioQuality.LOSSLESS -> "SQ 无损" + (if (playbackResult.bitrateKbps > 0) " ${playbackResult.bitrateKbps}k" else "")
                    AudioQuality.HIGH -> "高品 320K"
                    AudioQuality.STANDARD -> "标准 128K"
                }

                mutableState.value = mutableState.value.copy(
                    loading = false,
                    currentSong = resolvedSong,
                    currentPositionMs = targetSeekMs,
                    currentQualityLabel = qualityLabel
                )

                // 启动下一首零卡顿后台预加载 (预解析流地址与预取歌词)
                prefetchNextSong(list, index)
                loadComments(isRefresh = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    isPlaying = false,
                    error = e.message ?: "无法获取歌曲播放地址"
                )
            }
        }
    }

    private fun loadLyric(song: Song) {
        lyricJob?.cancel()
        lyricJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                lyricLoading = true,
                currentLyric = LyricData(),
                currentLyricIndex = -1
            )
            try {
                val data = VipManager.lyric.getLyric(song)
                mutableState.value = mutableState.value.copy(
                    lyricLoading = false,
                    currentLyric = data
                )
                updateLyricIndex(mutableState.value.currentPositionMs)
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    lyricLoading = false,
                    currentLyric = LyricData()
                )
            }
        }
    }


    fun togglePlayPause() {
        val player = activePlayer ?: return
        val state = mutableState.value
        if (player.mediaItemCount == 0 && state.currentSong != null) {
            playSong(state.currentSong, state.playlistQueue, seekToPositionMs = state.currentPositionMs)
            return
        }
        if (player.isPlaying) {
            player.pause()
            persistPlaybackStateAsync()
        } else {
            player.volume = 1f
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        val player = activePlayer
        if (player != null && player.mediaItemCount > 0) {
            player.seekTo(clamped)
            syncPlayer()
        } else {
            mutableState.value = mutableState.value.copy(currentPositionMs = clamped)
        }
        updateLyricIndex(clamped)
        persistPlaybackStateAsync()
    }

    fun seekToLyricLine(index: Int) {
        val lines = mutableState.value.currentLyric.lines
        if (index in lines.indices) {
            seekTo(lines[index].startTimeMs)
        }
    }

    fun cyclePlayMode(): PlayMode {
        val nextMode = when (mutableState.value.playMode) {
            PlayMode.REPEAT_ALL -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.REPEAT_ALL
        }
        mutableState.value = mutableState.value.copy(playMode = nextMode)
        persistPlaybackStateAsync()
        activePlayer?.repeatMode = if (nextMode == PlayMode.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (nextMode == PlayMode.REPEAT_ONE) {
            activePlayer?.let { player ->
                while (player.mediaItemCount > player.currentMediaItemIndex + 1) {
                    player.removeMediaItem(player.mediaItemCount - 1)
                }
            }
        } else {
            prefetchNextSong(mutableState.value.playlistQueue, mutableState.value.currentIndex)
        }
        return nextMode
    }

    private fun getNextIndex(queue: List<Song>, currentIndex: Int): Int {
        if (queue.isEmpty()) return 0
        return when (mutableState.value.playMode) {
            PlayMode.SHUFFLE -> {
                if (queue.size <= 1) 0 else {
                    if (nextShuffleIndex in queue.indices && nextShuffleIndex != currentIndex) {
                        nextShuffleIndex
                    } else {
                        var rand = Random.nextInt(queue.size)
                        while (rand == currentIndex && queue.size > 1) {
                            rand = Random.nextInt(queue.size)
                        }
                        nextShuffleIndex = rand
                        rand
                    }
                }
            }
            PlayMode.REPEAT_ONE, PlayMode.REPEAT_ALL -> (currentIndex + 1) % queue.size
        }
    }

    fun skipToNext() {
        val state = mutableState.value
        val queue = state.playlistQueue
        if (queue.isEmpty()) return

        val nextIndex = getNextIndex(queue, state.currentIndex)
        nextShuffleIndex = -1
        val expectedNextSong = queue.getOrNull(nextIndex) ?: return

        playSong(expectedNextSong, queue)
    }

    fun skipToPrevious() {
        val state = mutableState.value
        val queue = state.playlistQueue
        if (queue.isEmpty()) return

        val prevIndex = when (state.playMode) {
            PlayMode.SHUFFLE -> {
                if (queue.size <= 1) 0 else Random.nextInt(queue.size)
            }
            PlayMode.REPEAT_ONE, PlayMode.REPEAT_ALL -> {
                if (state.currentIndex - 1 < 0) queue.size - 1 else state.currentIndex - 1
            }
        }
        val expectedPrevSong = queue.getOrNull(prevIndex) ?: return

        playSong(expectedPrevSong, queue)
    }

    fun playNext(song: Song) {
        val state = mutableState.value
        if (state.currentSong == null || state.playlistQueue.isEmpty()) {
            playSong(song, listOf(song))
            return
        }
        val queue = state.playlistQueue.toMutableList()
        val existingIndex = queue.indexOfFirst { it.id == song.id }
        if (existingIndex != -1) {
            queue.removeAt(existingIndex)
        }
        val newCurrentIndex = if (existingIndex != -1 && existingIndex < state.currentIndex) {
            (state.currentIndex - 1).coerceAtLeast(0)
        } else {
            state.currentIndex
        }
        val insertIndex = (newCurrentIndex + 1).coerceAtMost(queue.size)
        queue.add(insertIndex, song)
        mutableState.value = state.copy(playlistQueue = queue, currentIndex = newCurrentIndex)
        persistPlaybackStateAsync()

        // 重新同步预缓存下一首
        activePlayer?.let { player ->
            while (player.mediaItemCount > player.currentMediaItemIndex + 1) {
                player.removeMediaItem(player.mediaItemCount - 1)
            }
        }
        prefetchNextSong(queue, newCurrentIndex)
    }

    fun removeFromQueue(song: Song) {
        val state = mutableState.value
        val queue = state.playlistQueue.toMutableList()
        val index = queue.indexOfFirst { it.id == song.id }
        if (index == -1) return
        queue.removeAt(index)

        if (queue.isEmpty()) {
            clearQueue()
            return
        }

        if (index == state.currentIndex) {
            val newIndex = index.coerceAtMost(queue.size - 1)
            playSong(queue[newIndex], queue)
        } else {
            val newCurrentIndex = if (index < state.currentIndex) {
                (state.currentIndex - 1).coerceAtLeast(0)
            } else {
                state.currentIndex
            }
            mutableState.value = state.copy(
                playlistQueue = queue,
                currentIndex = newCurrentIndex
            )
            persistPlaybackStateAsync()
            activePlayer?.let { player ->
                while (player.mediaItemCount > player.currentMediaItemIndex + 1) {
                    player.removeMediaItem(player.mediaItemCount - 1)
                }
            }
            prefetchNextSong(queue, newCurrentIndex)
        }
    }

    fun clearQueue() {
        activePlayingSong?.let {
            val playedMs = (System.currentTimeMillis() - songPlaybackStartTimestamp).coerceAtLeast(0L)
            KugouCsccReporter.onSongEnd(it, playedMs)
        }
        activePlayingSong = null
        songPlaybackStartTimestamp = 0L
        activePlayer?.stop()
        activePlayer?.clearMediaItems()
        songsByMediaId.clear()
        val st = mutableState.value
        mutableState.value = PlayerUiState(
            coverStyle = st.coverStyle,
            playMode = st.playMode,
            preferredQuality = st.preferredQuality,
            currentQualityLabel = st.preferredQuality.label,
            showTranslation = st.showTranslation,
            lyricFontSize = st.lyricFontSize,
            lyricAlignment = st.lyricAlignment,
            lyricWordByWord = st.lyricWordByWord
        )
        persistPlaybackStateAsync()
    }

    fun setPreferredQuality(quality: AudioQuality) {
        if (mutableState.value.preferredQuality == quality) return
        mutableState.value = mutableState.value.copy(
            preferredQuality = quality,
            currentQualityLabel = quality.label
        )
        persistPlaybackStateAsync()
        val currentSong = mutableState.value.currentSong ?: return
        val currentPosition = mutableState.value.currentPositionMs
        // 清理缓存使播放器以新音质重载并从当前进度继续播放
        VipManager.music.clearPlaybackCache(currentSong.hash)
        val updatedSong = currentSong.copy(playUrl = "")
        playSong(updatedSong, mutableState.value.playlistQueue, seekToPositionMs = currentPosition)
    }

    fun toggleTranslation() {
        mutableState.value = mutableState.value.copy(
            showTranslation = !mutableState.value.showTranslation
        )
        persistPlaybackStateAsync()
    }

    fun setCoverStyle(style: CoverStyle) {
        mutableState.value = mutableState.value.copy(coverStyle = style)
        persistPlaybackStateAsync()
    }

    fun setLyricFontSize(size: Float) {
        mutableState.value = mutableState.value.copy(lyricFontSize = size.coerceIn(14f, 26f))
        persistPlaybackStateAsync()
    }

    fun setLyricAlignment(alignment: LyricAlignment) {
        mutableState.value = mutableState.value.copy(lyricAlignment = alignment)
        persistPlaybackStateAsync()
    }

    fun setLyricWordByWord(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(lyricWordByWord = enabled)
        persistPlaybackStateAsync()
    }

    /**
     * 倍速与音高调节 ([FUNC-04])
     */
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = ((speed * 20).toInt() / 20f).coerceIn(0.5f, 2.0f)
        val current = mutableState.value
        val newPitch = if (current.linkPitchToSpeed) clampedSpeed else current.playbackPitch
        mutableState.value = current.copy(
            playbackSpeed = clampedSpeed,
            playbackPitch = newPitch
        )
        activePlayer?.playbackParameters = PlaybackParameters(clampedSpeed, newPitch)
    }

    fun setPlaybackPitch(pitch: Float) {
        val clampedPitch = ((pitch * 20).toInt() / 20f).coerceIn(0.5f, 2.0f)
        val current = mutableState.value
        mutableState.value = current.copy(
            playbackPitch = clampedPitch,
            linkPitchToSpeed = false
        )
        activePlayer?.playbackParameters = PlaybackParameters(current.playbackSpeed, clampedPitch)
    }

    fun setLinkPitchToSpeed(linked: Boolean) {
        val current = mutableState.value
        val newPitch = if (linked) current.playbackSpeed else 1.0f
        mutableState.value = current.copy(
            linkPitchToSpeed = linked,
            playbackPitch = newPitch
        )
        activePlayer?.playbackParameters = PlaybackParameters(current.playbackSpeed, newPitch)
    }

    fun resetSpeedAndPitch() {
        mutableState.value = mutableState.value.copy(
            playbackSpeed = 1.0f,
            playbackPitch = 1.0f,
            linkPitchToSpeed = false
        )
        activePlayer?.playbackParameters = PlaybackParameters(1.0f, 1.0f)
    }

    /**
     * 睡眠定时器 ([FUNC-03])
     * 支持倒计时分钟数 + “播完当前这首歌后停止”，倒计时结束前 5 秒音量平滑淡出
     */
    fun setSleepTimer(minutes: Int, stopAfterCurrentSong: Boolean = false) {
        sleepTimerJob?.cancel()
        activePlayer?.volume = 1f

        if (minutes <= 0 && !stopAfterCurrentSong) {
            mutableState.value = mutableState.value.copy(
                sleepTimerRemainingSec = 0L,
                sleepStopAfterCurrentSong = false
            )
            return
        }

        if (minutes <= 0 && stopAfterCurrentSong) {
            mutableState.value = mutableState.value.copy(
                sleepTimerRemainingSec = 0L,
                sleepStopAfterCurrentSong = true
            )
            return
        }

        val totalSec = minutes * 60L
        mutableState.value = mutableState.value.copy(
            sleepTimerRemainingSec = totalSec,
            sleepStopAfterCurrentSong = stopAfterCurrentSong
        )

        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSec
            while (remaining > 0L) {
                delay(1_000L)
                remaining -= 1L
                mutableState.value = mutableState.value.copy(sleepTimerRemainingSec = remaining)

                // 若未勾选“播完当前歌曲再停”，则在最后 5 秒平滑淡出音量
                if (!mutableState.value.sleepStopAfterCurrentSong && remaining in 1L..5L) {
                    activePlayer?.volume = (remaining / 5f).coerceIn(0.1f, 1f)
                }
            }

            // 倒计时归零
            if (!mutableState.value.sleepStopAfterCurrentSong) {
                activePlayer?.pause()
                activePlayer?.volume = 1f
                mutableState.value = mutableState.value.copy(
                    sleepTimerRemainingSec = 0L,
                    sleepStopAfterCurrentSong = false
                )
            }
        }
    }

    fun cancelSleepTimer() {
        setSleepTimer(0, stopAfterCurrentSong = false)
    }

    /**
     * 「副歌刷刷」高潮速听探索模式控制 ([INNOV-01])
     */
    fun toggleClimaxMode() {
        setClimaxMode(!mutableState.value.climaxModeEnabled)
    }

    fun setClimaxMode(enabled: Boolean) {
        val current = mutableState.value
        mutableState.value = current.copy(
            climaxModeEnabled = enabled,
            climaxUnlockedForCurrentSong = false
        )
        persistPlaybackStateAsync()

        val song = current.currentSong ?: return
        val player = activePlayer ?: return
        if (enabled) {
            val climaxStart = song.effectiveClimaxStartMs
            val climaxEnd = song.effectiveClimaxEndMs
            if (current.currentPositionMs < climaxStart || current.currentPositionMs >= climaxEnd) {
                player.seekTo(climaxStart)
                triggerClimaxSmoothFadeIn()
                mutableState.value = mutableState.value.copy(currentPositionMs = climaxStart)
            }
        } else {
            climaxFadeInJob?.cancel()
            player.volume = 1f
        }
    }

    /**
     * 遇到心仪歌曲点击「听整首」解除当前曲目的高潮截断 ([INNOV-01])
     */
    fun unlockFullTrackForCurrentSong() {
        climaxFadeInJob?.cancel()
        activePlayer?.volume = 1f
        mutableState.value = mutableState.value.copy(
            climaxUnlockedForCurrentSong = true
        )
    }

    private fun triggerClimaxSmoothFadeIn() {
        climaxFadeInJob?.cancel()
        val player = activePlayer ?: return
        player.volume = 0.15f
        climaxFadeInJob = viewModelScope.launch {
            val steps = 8
            for (step in 1..steps) {
                delay(140L)
                activePlayer?.volume = (0.15f + (0.85f * step / steps)).coerceIn(0.15f, 1.0f)
            }
            activePlayer?.volume = 1.0f
        }
    }

    fun setCommentType(type: String) {
        if (mutableState.value.commentType == type) return
        mutableState.value = mutableState.value.copy(
            commentType = type,
            commentsPage = 1,
            comments = emptyList()
        )
        loadComments(isRefresh = true)
    }

    fun loadComments(isRefresh: Boolean = false) {
        val song = mutableState.value.currentSong ?: return
        val page = if (isRefresh) 1 else mutableState.value.commentsPage + 1
        val type = mutableState.value.commentType

        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(commentsLoading = true)
            runCatching {
                val list = VipManager.music.songComments(song, type = type, page = page, pageSize = 30)
                val current = if (isRefresh) emptyList<SongComment>() else mutableState.value.comments
                val merged: List<SongComment> = (current + list).distinctBy { it.id }
                val serverCount = VipManager.music.lastSongCommentCount
                val count = if (serverCount > 0) serverCount else if (merged.isNotEmpty()) maxOf(mutableState.value.commentCount, merged.size) else mutableState.value.commentCount
                mutableState.value = mutableState.value.copy(
                    comments = merged,
                    commentsLoading = false,
                    commentsPage = page,
                    commentsHasMore = list.isNotEmpty(),
                    commentCount = count
                )
            }.onFailure {
                mutableState.value = mutableState.value.copy(commentsLoading = false)
            }
        }
    }

    fun postComment(
        content: String,
        replyToCommentId: String? = null,
        replyToUserName: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val song = mutableState.value.currentSong ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            runCatching {
                val newComment = VipManager.music.postSongComment(
                    song = song,
                    content = content.trim(),
                    replyToCommentId = replyToCommentId,
                    replyToUserName = replyToUserName
                )
                val currentList = mutableState.value.comments.toMutableList()
                if (replyToCommentId != null) {
                    val parentIndex = currentList.indexOfFirst { it.id == replyToCommentId }
                    if (parentIndex != -1) {
                        val parent = currentList[parentIndex]
                        val updatedReplies = parent.replies + newComment
                        currentList[parentIndex] = parent.copy(
                            replyCount = parent.replyCount + 1,
                            replies = updatedReplies
                        )
                    } else {
                        currentList.add(0, newComment)
                    }
                } else {
                    currentList.add(0, newComment)
                }
                mutableState.value = mutableState.value.copy(
                    comments = currentList,
                    commentCount = mutableState.value.commentCount + 1
                )
                onComplete(true)
            }.onFailure {
                onComplete(false)
            }
        }
    }

    fun likeComment(commentId: String) {
        val currentList = mutableState.value.comments.map { comment ->
            if (comment.id == commentId) {
                comment.copy(likeCount = comment.likeCount + 1)
            } else {
                val updatedReplies = comment.replies.map { reply ->
                    if (reply.id == commentId) {
                        reply.copy(likeCount = reply.likeCount + 1)
                    } else reply
                }
                comment.copy(replies = updatedReplies)
            }
        }
        mutableState.value = mutableState.value.copy(comments = currentList)
    }

    private fun prefetchNextSong(queue: List<Song>, currentIndex: Int) {
        prefetchJob?.cancel()
        if (queue.isEmpty()) return
        val nextIndex = getNextIndex(queue, currentIndex)
        if (nextIndex in queue.indices && nextIndex != currentIndex) {
            val nextSong = queue[nextIndex]
            prefetchJob = viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val nextQuality = if (mutableState.value.preferredQuality in nextSong.effectiveQualities) {
                        mutableState.value.preferredQuality
                    } else nextSong.highestQuality
                    val nextResult = VipManager.music.resolvePlayback(nextSong, nextQuality)
                    VipManager.lyric.getLyric(nextSong)

                    withContext(Dispatchers.Main) {
                        val player = activePlayer ?: return@withContext
                        if (nextResult.url.isNotBlank() && mutableState.value.playMode != PlayMode.REPEAT_ONE) {
                            val nextMediaId = mediaId(nextSong)
                            val resolvedNextSong = nextSong.copy(
                                playUrl = nextResult.url,
                                actualPlayQuality = nextResult.actualQuality,
                                bitrateKbps = nextResult.bitrateKbps,
                                fileSizeByte = nextResult.fileSizeByte,
                                climaxStartMs = nextResult.climaxStartMs.takeIf { it > 0L } ?: nextSong.climaxStartMs,
                                climaxEndMs = nextResult.climaxEndMs.takeIf { it > 0L } ?: nextSong.climaxEndMs
                            )
                            songsByMediaId[nextMediaId] = resolvedNextSong
                            val nextItem = MediaItem.Builder()
                                .setMediaId(nextMediaId)
                                .setUri(nextResult.url)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(nextSong.title)
                                        .setArtist(nextSong.artist)
                                        .setAlbumTitle(nextSong.album)
                                        .setArtworkUri(nextSong.coverUrl.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse))
                                        .build()
                                ).build()

                            // 保持 ExoPlayer 队列紧凑：清理当前项之前的历史项与当前项之后的多余项
                            while (player.currentMediaItemIndex > 0) {
                                player.removeMediaItem(0)
                            }
                            while (player.mediaItemCount > player.currentMediaItemIndex + 1) {
                                player.removeMediaItem(player.mediaItemCount - 1)
                            }
                            player.addMediaItem(nextItem)
                        }
                    }
                }
            }
        }
    }

    private fun onTrackEnded() {
        val current = mutableState.value.currentSong
        if (current != null) {
            val playedMs = (System.currentTimeMillis() - songPlaybackStartTimestamp).coerceAtLeast(0L)
            KugouCsccReporter.onSongEnd(current, playedMs)
            ListeningGradeManager.onSongCompleted(current)
            activePlayingSong = null
            songPlaybackStartTimestamp = 0L
        }
        // 若开启了“播完当前歌曲后停止”且倒计时已归零，则播完本首即停止
        if (mutableState.value.sleepStopAfterCurrentSong && mutableState.value.sleepTimerRemainingSec <= 0L) {
            activePlayer?.pause()
            activePlayer?.volume = 1f
            mutableState.value = mutableState.value.copy(
                isPlaying = false,
                sleepStopAfterCurrentSong = false
            )
            return
        }
        when (mutableState.value.playMode) {
            PlayMode.REPEAT_ONE -> {
                activePlayer?.seekTo(0)
                activePlayer?.play()
            }
            PlayMode.REPEAT_ALL, PlayMode.SHUFFLE -> {
                skipToNext()
            }
        }
    }

    internal fun syncPlayer() {
        val player = activePlayer ?: return
        val mediaId = player.currentMediaItem?.mediaId

        val song = mutableState.value.currentSong
        if (song != null && player.isPlaying && recordedMediaId != mediaId) {
            // 切歌或起播：若上一首有效则上报 end 事件
            val prevSong = activePlayingSong
            if (prevSong != null && prevSong.id != song.id) {
                val playedMs = (System.currentTimeMillis() - songPlaybackStartTimestamp).coerceAtLeast(0L)
                KugouCsccReporter.onSongEnd(prevSong, playedMs)
            }

            activePlayingSong = song
            songPlaybackStartTimestamp = System.currentTimeMillis()
            recordedMediaId = mediaId

            // 1. 本地播放历史记录
            VipManager.history.add(currentUser, song)

            // 2. 官方 CSCC 播放 start 事件上报
            KugouCsccReporter.onSongStart(song)

            // 3. 官方云端播放历史同步上报 (/playhistory/v1/upload_songs)
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { VipManager.music.uploadPlayHistory(song) }
            }

            // 4. 刷新缓存统计
            AudioCacheManager.refreshCacheSize(getApplication())
        }

        val isResolving = resolveJob?.isActive == true
        val hasMedia = player.mediaItemCount > 0
        val pos = if (isResolving || !hasMedia) mutableState.value.currentPositionMs else player.currentPosition.coerceAtLeast(0)
        val dur = if (isResolving || !hasMedia) (song?.durationMs ?: 0L) else (player.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0L)
        val buf = if (isResolving || !hasMedia) 0L else player.bufferedPosition.coerceAtLeast(0)
        val isPlayingNow = if (isResolving || !hasMedia) false else player.isPlaying

        // 同步环境音混音舱与桌面小组件播放状态 ([INNOV-03], [INNOV-04])
        AmbientSoundManager.onMusicPlayStateChanged(isPlayingNow)
        if (song != null && mutableState.value.isPlaying != isPlayingNow) {
            ConceptMusicWidgetUpdater.updateState(
                context = getApplication(),
                songTitle = song.title,
                artist = song.artist,
                isPlaying = isPlayingNow
            )
        }

        val st = mutableState.value

        // 「副歌刷刷」高潮速听自动跨轨淡出与切歌逻辑 ([INNOV-01])
        if (st.climaxModeEnabled && !st.climaxUnlockedForCurrentSong && song != null && isPlayingNow) {
            val climaxEnd = song.effectiveClimaxEndMs
            val remainClimaxMs = climaxEnd - pos
            if (remainClimaxMs in 1L..2_500L && climaxFadeInJob?.isActive != true) {
                player.volume = (remainClimaxMs / 2_500f).coerceIn(0.15f, 1.0f)
            } else if (pos >= climaxEnd && lastClimaxSkipSongId != song.id) {
                lastClimaxSkipSongId = song.id
                player.volume = 1f
                skipToNext()
                return
            }
        }

        // 若开启了“播完本首后停止”且倒计时已归零，在曲末最后 5 秒平滑淡出音量
        if (st.sleepStopAfterCurrentSong && st.sleepTimerRemainingSec <= 0L && dur > 5_000L && isPlayingNow) {
            val remainTrackMs = dur - pos
            if (remainTrackMs in 1L..5_000L) {
                player.volume = (remainTrackMs / 5_000f).coerceIn(0.08f, 1f)
            }
        }

        mutableState.value = st.copy(
            isPlaying = isPlayingNow,
            currentPositionMs = pos,
            durationMs = dur,
            bufferedPositionMs = buf,
            loading = isResolving || (hasMedia && player.playbackState == Player.STATE_BUFFERING)
        )
        if (!isResolving) {
            updateLyricIndex(pos)
        }
    }

    internal fun updateLyricIndex(pos: Long) {
        val st = mutableState.value
        val lyric = st.currentLyric
        if (lyric.lines.isNotEmpty()) {
            val index = lyric.getLineIndexAt(pos)
            if (index != st.currentLyricIndex) {
                mutableState.value = st.copy(currentLyricIndex = index)
                val line = lyric.lines.getOrNull(index)
                SystemLyricBridge.onLyricLineUpdated(
                    context = getApplication(),
                    song = st.currentSong,
                    lineIndex = index,
                    line = line,
                    isPlaying = st.isPlaying
                )
            }
        } else if (st.currentLyricIndex != -1) {
            mutableState.value = st.copy(currentLyricIndex = -1)
        }
    }

    private fun mediaId(song: Song): String = song.id

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = viewModelScope.launch {
            while (true) {
                val delayMs = if (mutableState.value.isPlaying) 150L else 500L
                syncPlayer()

                // 统计在线听歌时长并自动向酷狗云端增量同步 + 每 5 秒保存一次当前播放进度
                if (mutableState.value.isPlaying) {
                    val now = System.currentTimeMillis()
                    if (lastRecordTimestamp > 0L) {
                        val elapsedSec = (now - lastRecordTimestamp) / 1000
                        if (elapsedSec >= 1) {
                            lastRecordTimestamp = now
                            mutableState.value.currentSong?.let { song ->
                                ListeningGradeManager.recordPlaybackSeconds(elapsedSec, song)
                            }
                        }
                    } else {
                        lastRecordTimestamp = now
                    }

                    if (now - lastPersistPosTimestamp >= 5_000L) {
                        lastPersistPosTimestamp = now
                        persistPlaybackStateAsync()
                    }
                } else {
                    lastRecordTimestamp = 0L
                }

                delay(delayMs)
            }
        }
    }

    override fun onCleared() {
        persistPlaybackStateAsync()
        activePlayingSong?.let {
            val playedMs = (System.currentTimeMillis() - songPlaybackStartTimestamp).coerceAtLeast(0L)
            KugouCsccReporter.onSongEnd(it, playedMs)
        }
        activePlayingSong = null
        resolveJob?.cancel()
        lyricJob?.cancel()
        progressJob?.cancel()
        prefetchJob?.cancel()
        sleepTimerJob?.cancel()
        activePlayer?.removeListener(listener)
        controllerFuture?.let(MediaController::releaseFuture)
        super.onCleared()
    }
}
