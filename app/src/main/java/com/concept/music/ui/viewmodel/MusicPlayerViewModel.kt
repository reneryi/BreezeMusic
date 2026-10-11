package com.concept.music.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.Song
import com.concept.music.data.model.SongComment
import com.concept.music.data.model.SongDetailInfo
import com.concept.music.data.vip.VipManager
import com.concept.music.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class PlayMode {
    REPEAT_ALL,  // 列表循环
    REPEAT_ONE,  // 单曲循环
    SHUFFLE      // 随机播放
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
    val preferredQuality: AudioQuality = AudioQuality.LOSSLESS,
    val currentQualityLabel: String = "SQ 无损",
    val currentLyric: LyricData = LyricData(),
    val currentLyricIndex: Int = -1,
    val comments: List<SongComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentsPage: Int = 1,
    val commentsHasMore: Boolean = true,
    val commentType: String = "hot",
    val songDetail: SongDetailInfo? = null,
    val loading: Boolean = false,
    val lyricLoading: Boolean = false,
    val error: String? = null
)

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(PlayerUiState())
    val uiState = mutableState.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var resolveJob: Job? = null
    private var lyricJob: Job? = null
    private var progressJob: Job? = null
    private var currentUser: String? = VipManager.accounts.state.value.currentId
    private val songsByMediaId = mutableMapOf<String, Song>()
    private var recordedMediaId: String? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncPlayer()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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

    init {
        val context = application.applicationContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
            future.addListener({
                runCatching { future.get() }.onSuccess { player ->
                    controller = player
                    player.addListener(listener)
                    syncPlayer()
                    startProgressUpdates()
                }.onFailure { error ->
                    mutableState.value = mutableState.value.copy(error = error.message ?: "播放器连接失败")
                }
            }, { runnable -> runnable.run() })
        }

        viewModelScope.launch {
            VipManager.accounts.state.collect { state ->
                if (currentUser != state.currentId) {
                    currentUser = state.currentId
                    resolveJob?.cancel()
                    lyricJob?.cancel()
                    controller?.pause()
                    recordedMediaId = null
                    songsByMediaId.clear()
                    mutableState.value = PlayerUiState()
                }
            }
        }
    }

    fun playSong(song: Song, queue: List<Song> = emptyList(), seekToPositionMs: Long = 0L) {
        val player = controller ?: run {
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
        loadLyric(song)

        resolveJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                loading = true,
                error = null,
                playlistQueue = list,
                currentIndex = index,
                currentSong = song,
                currentPositionMs = seekToPositionMs,
                durationMs = song.durationMs,
                songDetail = VipManager.music.songDetail(song),
                comments = emptyList()
            )

            try {
                val quality = mutableState.value.preferredQuality
                val url = song.playUrl.takeIf { it.startsWith("http") }
                    ?: VipManager.music.resolveUrl(song, quality)

                if (VipManager.accounts.state.value.currentId != userId) return@launch

                val mediaId = mediaId(song)
                list.forEach { queued -> songsByMediaId[mediaId(queued)] = queued }

                val item = MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.coverUrl.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse))
                            .build()
                    ).build()

                if (seekToPositionMs > 0) {
                    player.pause() // 切换音质或定位时先暂停排空旧流音频缓冲区，杜绝混流爆音
                }
                player.setMediaItem(item)
                player.repeatMode = if (mutableState.value.playMode == PlayMode.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                player.prepare()
                if (seekToPositionMs > 0) {
                    player.seekTo(seekToPositionMs)
                }
                player.play()

                mutableState.value = mutableState.value.copy(
                    loading = false,
                    currentQualityLabel = when (quality) {
                        AudioQuality.LOSSLESS -> if (song.isLossless) "SQ 无损" else "高品 320K"
                        AudioQuality.HIGH -> "高品 320K"
                        AudioQuality.STANDARD -> "标准 128K"
                        AudioQuality.HI_RES -> "Hi-Res"
                    }
                )
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

    fun setCommentType(type: String) {
        if (mutableState.value.commentType == type) return
        mutableState.value = mutableState.value.copy(
            commentType = type,
            comments = emptyList(),
            commentsPage = 1,
            commentsHasMore = true
        )
        loadComments(mutableState.value.currentSong, isRefresh = true, type = type)
    }

    fun loadComments(
        targetSong: Song? = mutableState.value.currentSong,
        isRefresh: Boolean = true,
        type: String = mutableState.value.commentType
    ) {
        val song = targetSong ?: return
        if (!isRefresh && !mutableState.value.commentsHasMore) return
        if (mutableState.value.commentsLoading) return

        val targetPage = if (isRefresh) 1 else mutableState.value.commentsPage + 1
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                commentsLoading = true,
                comments = if (isRefresh) emptyList() else mutableState.value.comments
            )
            try {
                val list = VipManager.music.songComments(song, type = type, page = targetPage, pageSize = 30)
                val newComments = if (isRefresh) list else (mutableState.value.comments + list).distinctBy { it.id }
                mutableState.value = mutableState.value.copy(
                    comments = newComments,
                    commentsPage = targetPage,
                    commentsHasMore = list.size >= 30,
                    commentsLoading = false
                )
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(commentsLoading = false)
            }
        }
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        syncPlayer()
        updateLyricIndex(positionMs)
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
        controller?.repeatMode = if (nextMode == PlayMode.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        return nextMode
    }

    fun skipToNext() {
        val state = mutableState.value
        val queue = state.playlistQueue
        if (queue.isEmpty()) return

        val nextIndex = when (state.playMode) {
            PlayMode.SHUFFLE -> {
                if (queue.size <= 1) 0 else {
                    var rand = Random.nextInt(queue.size)
                    while (rand == state.currentIndex && queue.size > 1) {
                        rand = Random.nextInt(queue.size)
                    }
                    rand
                }
            }
            PlayMode.REPEAT_ONE, PlayMode.REPEAT_ALL -> {
                (state.currentIndex + 1) % queue.size
            }
        }

        playSong(queue[nextIndex], queue)
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

        playSong(queue[prevIndex], queue)
    }

    fun playNext(song: Song) {
        val state = mutableState.value
        val queue = state.playlistQueue.toMutableList()
        val existingIndex = queue.indexOfFirst { it.id == song.id }
        if (existingIndex != -1) {
            queue.removeAt(existingIndex)
        }
        val insertIndex = (state.currentIndex + 1).coerceAtMost(queue.size)
        queue.add(insertIndex, song)
        mutableState.value = state.copy(playlistQueue = queue)
    }

    fun removeFromQueue(song: Song) {
        val state = mutableState.value
        val queue = state.playlistQueue.toMutableList()
        val index = queue.indexOfFirst { it.id == song.id }
        if (index == -1) return
        queue.removeAt(index)

        val newCurrentIndex = if (index < state.currentIndex) {
            (state.currentIndex - 1).coerceAtLeast(0)
        } else if (index == state.currentIndex) {
            if (queue.isEmpty()) {
                controller?.stop()
                mutableState.value = PlayerUiState()
                return
            }
            index.coerceAtMost(queue.size - 1)
        } else {
            state.currentIndex
        }

        mutableState.value = state.copy(
            playlistQueue = queue,
            currentIndex = newCurrentIndex,
            currentSong = queue.getOrNull(newCurrentIndex)
        )
    }

    fun clearQueue() {
        controller?.stop()
        mutableState.value = PlayerUiState()
    }

    fun setPreferredQuality(quality: AudioQuality) {
        if (mutableState.value.preferredQuality == quality) return
        mutableState.value = mutableState.value.copy(
            preferredQuality = quality,
            currentQualityLabel = quality.label
        )
        val currentSong = mutableState.value.currentSong ?: return
        val currentPosition = mutableState.value.currentPositionMs
        // 清空已缓存的 playUrl，使 resolveUrl 重新根据新音质发起请求并从当前位置继续播放
        val updatedSong = currentSong.copy(playUrl = "")
        playSong(updatedSong, mutableState.value.playlistQueue, seekToPositionMs = currentPosition)
    }

    private fun onTrackEnded() {
        when (mutableState.value.playMode) {
            PlayMode.REPEAT_ONE -> {
                controller?.seekTo(0)
                controller?.play()
            }
            PlayMode.REPEAT_ALL, PlayMode.SHUFFLE -> {
                skipToNext()
            }
        }
    }

    private fun syncPlayer() {
        val player = controller ?: return
        val mediaId = player.currentMediaItem?.mediaId
        val song = mediaId?.let(songsByMediaId::get) ?: mutableState.value.currentSong
        if (song != null && player.isPlaying && recordedMediaId != mediaId) {
            VipManager.history.add(currentUser, song)
            recordedMediaId = mediaId
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { VipManager.music.uploadPlayHistory(song) }
            }
        }

        val pos = player.currentPosition.coerceAtLeast(0)
        val dur = player.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0L
        val buf = player.bufferedPosition.coerceAtLeast(0)

        mutableState.value = mutableState.value.copy(
            currentSong = song,
            isPlaying = player.isPlaying,
            currentPositionMs = pos,
            durationMs = dur,
            bufferedPositionMs = buf,
            loading = player.playbackState == Player.STATE_BUFFERING
        )
        updateLyricIndex(pos)
    }

    private fun updateLyricIndex(pos: Long) {
        val lyric = mutableState.value.currentLyric
        if (lyric.lines.isNotEmpty()) {
            val index = lyric.getLineIndexAt(pos)
            if (index != mutableState.value.currentLyricIndex) {
                mutableState.value = mutableState.value.copy(currentLyricIndex = index)
            }
        }
    }

    private fun mediaId(song: Song): String = song.id

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = viewModelScope.launch {
            while (true) {
                val delayMs = if (mutableState.value.isPlaying) 150L else 500L
                syncPlayer()
                delay(delayMs)
            }
        }
    }

    override fun onCleared() {
        resolveJob?.cancel()
        lyricJob?.cancel()
        progressJob?.cancel()
        controller?.removeListener(listener)
        controllerFuture?.let(MediaController::releaseFuture)
        super.onCleared()
    }
}
