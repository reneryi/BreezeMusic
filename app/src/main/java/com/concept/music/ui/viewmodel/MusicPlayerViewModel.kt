package com.concept.music.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.concept.music.data.kugou.KugouCsccReporter
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.Song
import com.concept.music.data.model.SongComment
import com.concept.music.data.model.SongDetailInfo
import com.concept.music.data.vip.ListeningGradeManager
import com.concept.music.data.vip.VipManager
import com.concept.music.service.AmbientSoundManager
import com.concept.music.service.AudioCacheManager
import com.concept.music.service.ConceptMusicWidgetUpdater
import com.concept.music.service.PlaybackCoordinator
import com.concept.music.service.PlaybackService
import com.concept.music.service.SystemLyricBridge
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class PlayMode {
    REPEAT_ALL,  // 列表循环
    REPEAT_ONE,  // 单曲循环
    SHUFFLE      // 随机播放
}

/**
 * 封面播放页精简高定双样式：
 * 1. VINYL：高质感仿真黑胶唱片 + 精密S型金属唱臂（旋转）
 * 2. SQUARE：高定圆角方形专辑封面（静态不旋转）
 */
enum class CoverStyle(val label: String) {
    VINYL("黑胶封面"),
    SQUARE("圆角方形")
}

/**
 * 封面播放页五款高定背景视觉模式
 */
enum class PlayerBackgroundStyle(val label: String, val subtitle: String) {
    COVER_BLUR("封面虚化", "柔和色彩弥散"),
    FLUID_AURORA("流体极光", "动态多核流光"),
    RHYTHM_HALO("律动光晕", "呼吸感同心光环"),
    STARLIGHT_NEBULA("暗夜星尘", "深邃微光星云"),
    DEEP_OLED("极简纯黑", "纯粹暗黑画布")
}

enum class LyricAlignment(val label: String) {
    LEFT("居左"),
    CENTER("居中"),
    RIGHT("居右")
}

enum class LyricHighlightTheme(val label: String, val primaryHex: Long, val secondaryHex: Long) {
    DYNAMIC_COVER("封面随动", 0xFF3898FF, 0xFF00E5FF),
    ICE_BLUE("概念冰蓝", 0xFF3898FF, 0xFF00E5FF),
    PURE_WHITE("流光皓白", 0xFFFFFFFF, 0xFFB8DCFF),
    VIP_GOLD("鎏金岁华", 0xFFFFC857, 0xFFFF9F1C),
    SAKURA_PINK("霓虹樱粉", 0xFFFF6B9D, 0xFFFF8E53),
    AURORA_MINT("极光薄荷", 0xFF2CE5B5, 0xFF3898FF)
}

@Immutable
data class SavedPlayQueue(
    val id: String,
    val title: String,
    val songs: List<Song>,
    val currentIndex: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * 独立高频播放进度快照：
 * 将每 120ms 变化的播放进度与结构化 `PlayerUiState` 彻底解耦，并结合 `sampleElapsedRealtimeMs`
 * 支持 UI 渲染帧（60/120fps）无锁线性插值采样，斩断播放器全树重组风暴。
 */
@Immutable
data class PlaybackPositionSnapshot(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val sampleElapsedRealtimeMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f
)

@Immutable
data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playlistQueue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val recentQueues: List<SavedPlayQueue> = emptyList(),
    val activeQueueId: String = "",
    val playMode: PlayMode = PlayMode.REPEAT_ALL,
    val preferredQuality: AudioQuality = AudioQuality.HIGH,
    val currentQualityLabel: String = "高品 320K",
    val currentLyric: LyricData = LyricData(),
    val currentLyricIndex: Int = -1,
    val coverStyle: CoverStyle = CoverStyle.VINYL,
    val playerBackgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.COVER_BLUR,
    val dynamicCoverAccentEnabled: Boolean = true,
    val showTranslation: Boolean = false,
    val showPhonetic: Boolean = false,
    val lyricFontSize: Float = 24f,
    val lyricTranslationScale: Float = 0.84f,
    val motionOrientationPermissionEnabled: Boolean = true,
    val lyricAlignment: LyricAlignment = LyricAlignment.LEFT,
    val lyricWordByWord: Boolean = true,
    val lyricBlurEnabled: Boolean = true,
    val lyricBlurIntensity: Float = 1.0f,
    val lyricLineSpacing: Float = 14f,
    val lyricOffsetMs: Long = 0L,
    val lyricHighlightTheme: LyricHighlightTheme = LyricHighlightTheme.ICE_BLUE,
    val lyricBackgroundDim: Float = 0.72f,
    val lyricKeepScreenOn: Boolean = true,
    val smoothPlayPauseFade: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val linkPitchToSpeed: Boolean = false,
    val sleepTimerRemainingSec: Long = 0L,
    val sleepStopAfterCurrentSong: Boolean = false,
    val sleepPendingStopAtTrackEnd: Boolean = false,
    val climaxModeEnabled: Boolean = false,
    val climaxUnlockedForCurrentSong: Boolean = false,
    val accompanyModeEnabled: Boolean = false,
    val crossfadeSeconds: Int = 2,
    val volumeNormalization: Boolean = true,
    val wifiOnlyLossless: Boolean = false,
    val pauseOnHeadsetDisconnect: Boolean = true,
    val autoPlayOnStartup: Boolean = false,
    val commentTargetSong: Song? = null,
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

    private val mutablePositionFlow = MutableStateFlow(PlaybackPositionSnapshot())
    val playbackPositionFlow = mutablePositionFlow.asStateFlow()

    internal var elapsedRealtimeProviderForTest: (() -> Long)? = null

    private fun safeElapsedRealtime(): Long =
        elapsedRealtimeProviderForTest?.invoke()
            ?: runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L).takeIf { it > 0L }
            ?: System.currentTimeMillis()

    private fun updatePositionSnapshot(
        positionMs: Long,
        durationMs: Long = mutableState.value.durationMs,
        bufferedPositionMs: Long = mutablePositionFlow.value.bufferedPositionMs,
        isPlaying: Boolean = mutableState.value.isPlaying,
        playbackSpeed: Float = mutableState.value.playbackSpeed
    ) {
        mutablePositionFlow.value = PlaybackPositionSnapshot(
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            bufferedPositionMs = bufferedPositionMs.coerceAtLeast(0L),
            sampleElapsedRealtimeMs = safeElapsedRealtime(),
            isPlaying = isPlaying,
            playbackSpeed = playbackSpeed
        )
    }

    /**
     * 供进度条 `Canvas` 与激活歌词行在 60/120fps 绘制帧中直接按需无锁采样的平滑进度计算器：
     * 结合最近一次 ExoPlayer 采样位置与实时单调时钟插值，彻底消除 120ms 阶梯跳动感与全树重组。
     */
    fun currentSmoothPositionMs(): Long {
        val snap = mutablePositionFlow.value
        val basePos = snap.positionMs.coerceAtLeast(0L)
        if (!snap.isPlaying || snap.sampleElapsedRealtimeMs <= 0L) {
            return basePos
        }
        val now = safeElapsedRealtime()
        if (now <= snap.sampleElapsedRealtimeMs) return basePos
        val elapsed = (now - snap.sampleElapsedRealtimeMs).coerceIn(0L, 360L)
        val interpolated = basePos + (elapsed * snap.playbackSpeed).toLong()
        return if (snap.durationMs > 0L) {
            interpolated.coerceIn(0L, snap.durationMs)
        } else {
            interpolated.coerceAtLeast(0L)
        }
    }

    private val gson = com.concept.music.data.kugou.KugouSigner.gson
    private val prefs by lazy {
        application.applicationContext.getSharedPreferences("concept_player_prefs", android.content.Context.MODE_PRIVATE)
    }

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    internal var playerOverride: Player? = null
    internal val activePlayer: Player? get() = playerOverride ?: controller

    private var resolveJob: Job? = null
    private var lyricJob: Job? = null
    private var commentsJob: Job? = null
    private var progressJob: Job? = null
    private var prefetchJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var persistStateJob: Job? = null
    private var climaxFadeInJob: Job? = null
    private var playPauseFadeJob: Job? = null
    private var lastClimaxSkipSongId: String = ""
    private var lastErrorRetrySongId: String = ""
    private var hasTriggeredStartupAutoPlay: Boolean = false

    private var currentUser: String? = VipManager.accounts.state.value.currentId
    internal val songsByMediaId = mutableMapOf<String, Song>()
    private var recordedMediaId: String? = null
    private var lastRecordTimestamp: Long = 0L
    private var lastPersistPosTimestamp: Long = 0L
    private var activePlayingSong: Song? = null
    private var songPlaybackStartTimestamp: Long = 0L
    private var accumulatedPlayedMs: Long = 0L
    private var currentSegmentRecordedGradeSec: Long = 0L
    private var lastAccumulateTickMs: Long = 0L
    private var csccSegmentOpen: Boolean = false
    private var isPausingWithFade: Boolean = false
    private var nextShuffleIndex: Int = -1
    private var prefetchedNextQueueIndex: Int = -1
    private var prefetchedNextSongKey: String = ""
    private var pendingSeekPositionMs: Long = -1L
    private var pendingSeekElapsedMs: Long = 0L
    @Volatile
    private var lastSerializedQueueRef: List<Song>? = null
    @Volatile
    private var lastSerializedQueueJson: String = "[]"

    private fun resolveEffectiveIsPlaying(player: Player, isResolving: Boolean): Boolean {
        if (isPausingWithFade) return false
        if (isResolving) return mutableState.value.isPlaying
        if (player.mediaItemCount == 0) return false
        if (player.isPlaying) return true
        return player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
    }

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
            if (playbackState == Player.STATE_READY) {
                val st = mutableState.value
                val inSleepFade = !st.sleepStopAfterCurrentSong && st.sleepTimerRemainingSec in 1L..5L
                if (climaxFadeInJob?.isActive != true && playPauseFadeJob?.isActive != true && !inSleepFade) {
                    activePlayer?.volume = 1f
                }
            }
            syncPlayer()
            if (playbackState == Player.STATE_ENDED) {
                onTrackEnded()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val failedSong = mutableState.value.currentSong
            if (failedSong != null && lastErrorRetrySongId != failedSong.id) {
                lastErrorRetrySongId = failedSong.id
                val retryPos = currentSmoothPositionMs().takeIf { it > 0L } ?: mutableState.value.currentPositionMs
                VipManager.music.clearPlaybackCache(failedSong.hash)
                playSong(
                    song = failedSong.copy(playUrl = ""),
                    queue = mutableState.value.playlistQueue,
                    seekToPositionMs = retryPos
                )
                return
            }
            mutableState.value = mutableState.value.copy(
                loading = false,
                isPlaying = false,
                error = error.message ?: "播放器解码失败，请稍后重试"
            )
            updatePositionSnapshot(
                positionMs = mutablePositionFlow.value.positionMs,
                isPlaying = false
            )
        }
    }

    private fun accumulateActiveSlice(nowElapsed: Long = safeElapsedRealtime()) {
        if (lastAccumulateTickMs > 0L && nowElapsed > lastAccumulateTickMs) {
            accumulatedPlayedMs += (nowElapsed - lastAccumulateTickMs)
            lastAccumulateTickMs = nowElapsed
        }
    }

    private fun flushGradePlaybackSeconds(nowElapsed: Long = safeElapsedRealtime(), songOverride: Song? = null) {
        val targetSong = songOverride ?: activePlayingSong ?: mutableState.value.currentSong ?: return
        val effectiveMs = currentEffectivePlayedMs(nowElapsed)
        if (effectiveMs < 10_000L) return
        val totalEligibleSec = effectiveMs / 1000L
        val deltaSec = totalEligibleSec - currentSegmentRecordedGradeSec
        if (deltaSec >= 1L) {
            currentSegmentRecordedGradeSec = totalEligibleSec
            lastRecordTimestamp = nowElapsed
            ListeningGradeManager.recordPlaybackSeconds(deltaSec, targetSong)
        }
    }

    internal fun currentEffectivePlayedMs(nowElapsed: Long = safeElapsedRealtime()): Long {
        val activeDelta = if (lastAccumulateTickMs > 0L && nowElapsed > lastAccumulateTickMs) {
            nowElapsed - lastAccumulateTickMs
        } else 0L
        return (accumulatedPlayedMs + activeDelta).coerceAtLeast(0L)
    }

    private fun startNewPlaybackSegment(song: Song, mediaIdValue: String?, recordHistory: Boolean) {
        val nowElapsed = safeElapsedRealtime()
        activePlayingSong = song
        recordedMediaId = mediaIdValue
        songPlaybackStartTimestamp = nowElapsed
        accumulatedPlayedMs = 0L
        currentSegmentRecordedGradeSec = 0L
        lastAccumulateTickMs = nowElapsed
        lastRecordTimestamp = nowElapsed
        csccSegmentOpen = true

        if (recordHistory) {
            VipManager.history.add(currentUser, song)
        }
        KugouCsccReporter.onSongStart(song)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { VipManager.music.uploadPlayHistory(song) }
        }
        if (recordHistory) {
            AudioCacheManager.refreshCacheSize(getApplication())
        }
    }

    internal fun settleCurrentPlaybackSegment(
        state: String = "完整播放",
        clearActiveSong: Boolean = true,
        notifySongCompleted: Boolean = false
    ): Long {
        val nowElapsed = safeElapsedRealtime()
        accumulateActiveSlice(nowElapsed)
        val targetSong = activePlayingSong ?: mutableState.value.currentSong
        if (targetSong != null) {
            flushGradePlaybackSeconds(nowElapsed, targetSong)
        }
        val playedMs = accumulatedPlayedMs.coerceAtLeast(0L)
        if (targetSong != null && csccSegmentOpen && playedMs >= 10_000L) {
            KugouCsccReporter.onSongEnd(targetSong, playedMs, state = state)
            accumulatedPlayedMs = 0L
            currentSegmentRecordedGradeSec = 0L
            songPlaybackStartTimestamp = 0L
            csccSegmentOpen = false
        } else if (clearActiveSong) {
            accumulatedPlayedMs = 0L
            currentSegmentRecordedGradeSec = 0L
            songPlaybackStartTimestamp = 0L
            csccSegmentOpen = false
        }
        lastAccumulateTickMs = 0L
        lastRecordTimestamp = 0L
        if (notifySongCompleted && targetSong != null) {
            ListeningGradeManager.onSongCompleted(targetSong)
        }
        if (clearActiveSong) {
            activePlayingSong = null
            recordedMediaId = null
        }
        return playedMs
    }

    internal fun handleSongTransition(transitionedMediaId: String, reason: Int = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
        val currentSong = mutableState.value.currentSong
        if (currentSong != null && currentSong.id == transitionedMediaId) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                settleCurrentPlaybackSegment(
                    state = "完整播放",
                    clearActiveSong = false,
                    notifySongCompleted = true
                )
                if (activePlayer?.isPlaying == true || mutableState.value.isPlaying) {
                    startNewPlaybackSegment(currentSong, transitionedMediaId, recordHistory = false)
                }
                lastClimaxSkipSongId = ""
                pendingSeekPositionMs = -1L
                mutableState.update { it.copy(climaxUnlockedForCurrentSong = false) }
                if (mutableState.value.climaxModeEnabled) {
                    val startPos = currentSong.effectiveClimaxStartMs
                    activePlayer?.seekTo(startPos)
                    triggerClimaxSmoothFadeIn()
                }
            }
            return
        }

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            settleCurrentPlaybackSegment(
                state = "完整播放",
                clearActiveSong = true,
                notifySongCompleted = true
            )
            if (mutableState.value.sleepPendingStopAtTrackEnd && mutableState.value.sleepTimerRemainingSec <= 0L) {
                activePlayer?.pause()
                activePlayer?.volume = 1f
                mutableState.value = mutableState.value.copy(
                    isPlaying = false,
                    sleepPendingStopAtTrackEnd = false
                )
                updatePositionSnapshot(positionMs = mutablePositionFlow.value.positionMs, isPlaying = false)
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
        pendingSeekPositionMs = -1L

        val playQuality = transitionedSong.actualPlayQuality ?: transitionedSong.highestQuality
        val resolvedSong = transitionedSong.copy(
            actualPlayQuality = playQuality,
            availableQualities = transitionedSong.effectiveQualities
        )
        val updatedQueue = q.map { if (it.id == resolvedSong.id) resolvedSong else it }
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

        val updatedRecent = syncActiveQueueInRecent(updatedQueue, newIdx, mutableState.value.activeQueueId, mutableState.value.recentQueues)
        commentsJob?.cancel()
        updatePositionSnapshot(
            positionMs = startPos,
            durationMs = resolvedSong.durationMs,
            bufferedPositionMs = 0L,
            isPlaying = mutableState.value.isPlaying
        )
        mutableState.value = mutableState.value.copy(
            currentSong = resolvedSong,
            playlistQueue = updatedQueue,
            currentQualityLabel = transQualityLabel,
            currentIndex = newIdx,
            recentQueues = updatedRecent.first,
            activeQueueId = updatedRecent.second,
            currentPositionMs = startPos,
            durationMs = resolvedSong.durationMs,
            climaxUnlockedForCurrentSong = false,
            songDetail = VipManager.music.songDetail(resolvedSong),
            commentTargetSong = null,
            comments = emptyList(),
            commentCount = 0,
            commentsPage = 1,
            commentsHasMore = true
        )

        persistPlaybackStateAsync()
        loadLyric(resolvedSong)
        loadComments(isRefresh = true)
        prefetchNextSong(updatedQueue, newIdx)
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

        SystemLyricBridge.onTogglePlayCallback = {
            viewModelScope.launch(Dispatchers.Main) { togglePlayPause() }
        }
        SystemLyricBridge.onPreviousCallback = {
            viewModelScope.launch(Dispatchers.Main) { skipToPrevious() }
        }
        SystemLyricBridge.onNextCallback = {
            viewModelScope.launch(Dispatchers.Main) { skipToNext() }
        }

        PlaybackService.onSkipToPreviousIntercepted = {
            val hasQueue = mutableState.value.playlistQueue.isNotEmpty()
            if (hasQueue) {
                viewModelScope.launch(Dispatchers.Main) { skipToPrevious() }
            }
            hasQueue
        }
        PlaybackCoordinator.onTogglePlayDelegate = {
            viewModelScope.launch(Dispatchers.Main) { togglePlayPause() }
            true
        }
        PlaybackService.onSkipToNextIntercepted = {
            val hasQueue = mutableState.value.playlistQueue.isNotEmpty()
            if (hasQueue) {
                viewModelScope.launch(Dispatchers.Main) { skipToNext() }
            }
            hasQueue
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
                        PlaybackService.updateHandleAudioBecomingNoisy(st.pauseOnHeadsetDisconnect)
                        syncPlayer()
                        startProgressUpdates()
                        if (!hasTriggeredStartupAutoPlay && st.autoPlayOnStartup && st.currentSong != null && !player.isPlaying) {
                            hasTriggeredStartupAutoPlay = true
                            playSong(st.currentSong, st.playlistQueue, seekToPositionMs = st.currentPositionMs)
                        }
                    }.onFailure { error ->
                        mutableState.value = mutableState.value.copy(error = error.message ?: "播放器连接失败")
                    }
                }, { runnable -> runnable.run() })
            }
        }

        viewModelScope.launch {
            VipManager.accounts.state.collect { state ->
                if (currentUser != state.currentId) {
                    val previousUser = currentUser
                    val hadPreviousUser = previousUser != null
                    currentUser = state.currentId
                    if (hadPreviousUser) {
                        settleCurrentPlaybackSegment(
                            state = "手动停止",
                            clearActiveSong = true,
                            notifySongCompleted = false
                        )
                        resolveJob?.cancel()
                        lyricJob?.cancel()
                        prefetchJob?.cancel()
                        activePlayer?.pause()
                        activePlayer?.clearMediaItems()
                        songsByMediaId.clear()
                        VipManager.music.onAccountChanged()
                        mutableState.value = PlayerUiState()
                        mutablePositionFlow.value = PlaybackPositionSnapshot()
                        restorePersistedPlaybackState()
                    }
                }
            }
        }
    }

    private fun buildQueueTitle(songs: List<Song>): String {
        if (songs.isEmpty()) return "空队列"
        val first = songs.first().title.take(10)
        return if (songs.size == 1) {
            "单曲 · $first"
        } else {
            "$first 等 ${songs.size} 首"
        }
    }

    private fun isSameQueueLineage(a: List<Song>, b: List<Song>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a.size == b.size && a.first().id == b.first().id && a.last().id == b.last().id) return true
        val setA = a.take(30).map { it.id }.toSet()
        val setB = b.take(30).map { it.id }.toSet()
        val overlap = setA.intersect(setB).size
        val minSize = minOf(setA.size, setB.size)
        return minSize >= 3 && overlap * 10 >= minSize * 7
    }

    private fun syncActiveQueueInRecent(
        queue: List<Song>,
        currentIndex: Int,
        currentActiveId: String,
        existingRecent: List<SavedPlayQueue>
    ): Pair<List<SavedPlayQueue>, String> {
        if (queue.isEmpty()) return existingRecent to currentActiveId
        val cleanSongs = queue.map { it.copy(playUrl = "") }
        val now = System.currentTimeMillis()
        val matchedExisting = existingRecent.find { it.id == currentActiveId && isSameQueueLineage(it.songs, cleanSongs) }
            ?: existingRecent.find { isSameQueueLineage(it.songs, cleanSongs) }

        val targetId = matchedExisting?.id ?: "q_$now"
        val updatedEntry = SavedPlayQueue(
            id = targetId,
            title = buildQueueTitle(cleanSongs),
            songs = cleanSongs,
            currentIndex = currentIndex.coerceIn(0, (cleanSongs.size - 1).coerceAtLeast(0)),
            updatedAtMs = now
        )
        val newList = buildList {
            add(updatedEntry)
            existingRecent.forEach { item ->
                if (item.id != targetId && !isSameQueueLineage(item.songs, cleanSongs)) {
                    add(item)
                }
            }
        }.take(5)
        return newList to targetId
    }

    fun switchToRecentQueue(queueId: String) {
        val state = mutableState.value
        val target = state.recentQueues.find { it.id == queueId } ?: return
        if (target.songs.isEmpty()) return
        val safeIdx = target.currentIndex.coerceIn(0, target.songs.lastIndex)
        val song = target.songs[safeIdx]
        mutableState.value = state.copy(activeQueueId = target.id)
        playSong(song, target.songs)
    }

    fun deleteRecentQueue(queueId: String) {
        val state = mutableState.value
        val remaining = state.recentQueues.filterNot { it.id == queueId }
        if (queueId == state.activeQueueId) {
            val nextQueue = remaining.firstOrNull()
            if (nextQueue != null && nextQueue.songs.isNotEmpty()) {
                val idx = nextQueue.currentIndex.coerceIn(0, nextQueue.songs.lastIndex)
                mutableState.value = state.copy(
                    recentQueues = remaining,
                    activeQueueId = nextQueue.id
                )
                playSong(nextQueue.songs[idx], nextQueue.songs)
            } else {
                mutableState.value = state.copy(
                    recentQueues = emptyList(),
                    activeQueueId = ""
                )
                clearQueue()
            }
        } else {
            mutableState.value = state.copy(recentQueues = remaining)
            persistPlaybackStateAsync()
        }
    }

    private fun restorePersistedPlaybackState() {
        val savedCover = runCatching {
            val raw = prefs.getString("cover_style", CoverStyle.VINYL.name) ?: CoverStyle.VINYL.name
            if (raw == "SQUARE") CoverStyle.SQUARE else CoverStyle.VINYL
        }.getOrDefault(CoverStyle.VINYL)
        val savedBgStyle = runCatching {
            val rawBg = prefs.getString("player_bg_style", PlayerBackgroundStyle.COVER_BLUR.name)
                ?: PlayerBackgroundStyle.COVER_BLUR.name
            PlayerBackgroundStyle.valueOf(rawBg)
        }.getOrDefault(PlayerBackgroundStyle.COVER_BLUR)
        val savedDynamicAccent = prefs.getBoolean("dynamic_cover_accent_enabled", true)
        val savedTransScale = prefs.getFloat("lyric_translation_scale", 0.84f).coerceIn(0.65f, 0.95f)
        val savedSpeed = prefs.getFloat("playback_speed", 1.0f).coerceIn(0.5f, 2.0f)
        val savedPitch = prefs.getFloat("playback_pitch", 1.0f).coerceIn(0.5f, 2.0f)
        val savedLinkPitch = prefs.getBoolean("link_pitch_to_speed", false)
        SystemLyricBridge.playbackSpeedSnapshot = savedSpeed
        val savedSmoothFade = prefs.getBoolean("smooth_play_pause_fade", true)
        val savedPlayMode = runCatching {
            PlayMode.valueOf(prefs.getString("play_mode", PlayMode.REPEAT_ALL.name) ?: PlayMode.REPEAT_ALL.name)
        }.getOrDefault(PlayMode.REPEAT_ALL)
        val savedQuality = runCatching {
            AudioQuality.valueOf(prefs.getString("preferred_quality_v2", AudioQuality.HIGH.name) ?: AudioQuality.HIGH.name)
        }.getOrDefault(AudioQuality.HIGH)
        val savedShowTrans = prefs.getBoolean("show_translation_v3", false)
        val savedShowPhonetic = prefs.getBoolean("show_phonetic_v3", false)
        val savedFontSize = prefs.getFloat("lyric_font_size_v3", 24f).coerceIn(18f, 34f)
        val savedMotionPerm = prefs.getBoolean("motion_orientation_sensor_enabled", true)
        val savedAlign = runCatching {
            LyricAlignment.valueOf(prefs.getString("lyric_alignment_v2", LyricAlignment.LEFT.name) ?: LyricAlignment.LEFT.name)
        }.getOrDefault(LyricAlignment.LEFT)
        val savedWordByWord = prefs.getBoolean("lyric_word_by_word", true)
        val savedBlurEnabled = prefs.getBoolean("lyric_blur_enabled", true)
        val savedBlurIntensity = prefs.getFloat("lyric_blur_intensity", 1.0f).coerceIn(0.3f, 2.0f)
        val savedLineSpacing = prefs.getFloat("lyric_line_spacing", 14f).coerceIn(6f, 26f)
        val savedLyricOffset = prefs.getLong("lyric_offset_ms", 0L).coerceIn(-5000L, 5000L)
        val savedHighlightTheme = runCatching {
            LyricHighlightTheme.valueOf(
                prefs.getString("lyric_highlight_theme", LyricHighlightTheme.ICE_BLUE.name)
                    ?: LyricHighlightTheme.ICE_BLUE.name
            )
        }.getOrDefault(LyricHighlightTheme.ICE_BLUE)
        val savedBgDim = prefs.getFloat("lyric_bg_dim", 0.72f).coerceIn(0.35f, 0.92f)
        val savedKeepScreenOn = prefs.getBoolean("lyric_keep_screen_on", true)

        val savedSleepStopAfterSong = prefs.getBoolean("sleep_stop_after_song_pref", false)

        val savedClimaxMode = prefs.getBoolean("climax_mode_enabled", false)
        val savedCrossfade = prefs.getInt("crossfade_seconds", 2).coerceIn(0, 8)
        val savedVolNorm = prefs.getBoolean("volume_normalization", true)
        com.concept.music.service.AudioEffectManager.setVolumeNormalizationEnabled(savedVolNorm)
        val savedWifiOnly = prefs.getBoolean("wifi_only_lossless", false)
        val savedPauseHeadset = prefs.getBoolean("pause_on_headset_disconnect", true)
        val savedAutoPlay = prefs.getBoolean("auto_play_on_startup", false)

        val userScopeKey = currentUser ?: "guest"
        val queueJson = prefs.getString("saved_queue_json_$userScopeKey", null) ?: if (currentUser == null) prefs.getString("saved_queue_json", null) else null
        val restoredQueue: List<Song> = if (!queueJson.isNullOrBlank()) {
            runCatching {
                val type = object : TypeToken<List<Song>>() {}.type
                gson.fromJson<List<Song>>(queueJson, type) ?: emptyList()
            }.getOrDefault(emptyList())
        } else emptyList()

        val recentQueuesJson = prefs.getString("saved_recent_queues_json_$userScopeKey", null) ?: if (currentUser == null) prefs.getString("saved_recent_queues_json", null) else null
        val restoredRecentQueues: List<SavedPlayQueue> = if (!recentQueuesJson.isNullOrBlank()) {
            runCatching {
                val type = object : TypeToken<List<SavedPlayQueue>>() {}.type
                gson.fromJson<List<SavedPlayQueue>>(recentQueuesJson, type) ?: emptyList()
            }.getOrDefault(emptyList())
        } else emptyList()

        fun sanitizePersistedSong(rawSong: Song): Song {
            val s = rawSong.sanitizeFromJson()
            if (!s.isHiRes) return s
            val realHiRes = s.hashHiRes.isNotBlank() &&
                !s.hashHiRes.equals(s.hash320, ignoreCase = true) &&
                !s.hashHiRes.equals(s.hash128, ignoreCase = true)
            if (realHiRes) return s
            val flacFallback = s.hashFlac.ifBlank { s.hashHiRes }
            val cleanedQualities = s.effectiveQualities.filter { it != AudioQuality.HI_RES }.let { q ->
                if (flacFallback.isNotBlank() && AudioQuality.LOSSLESS !in q) q + AudioQuality.LOSSLESS else q
            }.ifEmpty { listOf(AudioQuality.STANDARD) }
            return s.copy(
                hashHiRes = "",
                hashFlac = flacFallback,
                isHiRes = false,
                isLossless = flacFallback.isNotBlank() || (s.isLossless && !s.isHiRes),
                availableQualities = cleanedQualities
            )
        }
        val sanitizedQueue = restoredQueue.map { sanitizePersistedSong(it) }
        val savedIndex = prefs.getInt("saved_current_index_$userScopeKey", prefs.getInt("saved_current_index", 0))
            .coerceIn(0, (sanitizedQueue.size - 1).coerceAtLeast(0))
        val restoredSong = sanitizedQueue.getOrNull(savedIndex)
        val savedPosMs = prefs.getLong("saved_position_ms_$userScopeKey", prefs.getLong("saved_position_ms", 0L)).coerceAtLeast(0L)
        val savedActiveQueueId = prefs.getString("saved_active_queue_id", "").orEmpty()

        val (syncedRecent, activeQueueId) = syncActiveQueueInRecent(
            queue = sanitizedQueue,
            currentIndex = savedIndex,
            currentActiveId = savedActiveQueueId,
            existingRecent = restoredRecentQueues.take(5).map { rq -> rq.copy(id = (rq.id as String?) ?: "", title = (rq.title as String?) ?: "", songs = (rq.songs as List<Song?>?)?.filterNotNull()?.map { sanitizePersistedSong(it) }.orEmpty()) }
        )

        val effectiveRestoredQuality = if (restoredSong != null) {
            restoredSong.actualPlayQuality?.takeIf { it in restoredSong.effectiveQualities }
                ?: if (savedQuality in restoredSong.effectiveQualities) savedQuality else restoredSong.highestQuality
        } else savedQuality

        val initialPos = if (restoredSong != null) savedPosMs else 0L
        val initialDur = restoredSong?.durationMs ?: 0L
        updatePositionSnapshot(
            positionMs = initialPos,
            durationMs = initialDur,
            bufferedPositionMs = 0L,
            isPlaying = false
        )

        mutableState.value = mutableState.value.copy(
            coverStyle = savedCover,
            playerBackgroundStyle = savedBgStyle,
            dynamicCoverAccentEnabled = savedDynamicAccent,
            lyricTranslationScale = savedTransScale,
            smoothPlayPauseFade = savedSmoothFade,
            playbackSpeed = savedSpeed,
            playbackPitch = savedPitch,
            linkPitchToSpeed = savedLinkPitch,
            playMode = savedPlayMode,
            preferredQuality = savedQuality,
            currentQualityLabel = effectiveRestoredQuality.label,
            showTranslation = savedShowTrans,
            showPhonetic = savedShowPhonetic,
            lyricFontSize = savedFontSize,
            motionOrientationPermissionEnabled = savedMotionPerm,
            lyricAlignment = savedAlign,
            lyricWordByWord = savedWordByWord,
            lyricBlurEnabled = savedBlurEnabled,
            lyricBlurIntensity = savedBlurIntensity,
            lyricLineSpacing = savedLineSpacing,
            lyricOffsetMs = savedLyricOffset,
            lyricHighlightTheme = savedHighlightTheme,
            lyricBackgroundDim = savedBgDim,
            lyricKeepScreenOn = savedKeepScreenOn,
            sleepStopAfterCurrentSong = savedSleepStopAfterSong,
            climaxModeEnabled = savedClimaxMode,
            crossfadeSeconds = savedCrossfade,
            volumeNormalization = savedVolNorm,
            wifiOnlyLossless = savedWifiOnly,
            pauseOnHeadsetDisconnect = savedPauseHeadset,
            autoPlayOnStartup = savedAutoPlay,
            playlistQueue = sanitizedQueue,
            currentIndex = savedIndex,
            recentQueues = syncedRecent,
            activeQueueId = activeQueueId,
            currentSong = restoredSong,
            currentPositionMs = initialPos,
            durationMs = initialDur,
            songDetail = restoredSong?.let { VipManager.music.songDetail(it) }
        )

        if (restoredSong != null) {
            loadLyric(restoredSong)
            if (playerOverride == null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val warmList = buildList {
                        add(restoredSong)
                        val nextIdx = (savedIndex + 1) % sanitizedQueue.size.coerceAtLeast(1)
                        sanitizedQueue.getOrNull(nextIdx)?.let { if (it.id != restoredSong.id) add(it) }
                    }
                    runCatching { VipManager.music.prewarmSongsPlayback(warmList, savedQuality, maxCount = 2) }
                }
            }
            loadComments(isRefresh = true)
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
        val userScopeKey = currentUser ?: "guest"
        val latestPos = mutablePositionFlow.value.positionMs.takeIf { it > 0L } ?: snapshot.currentPositionMs
        PlaybackCoordinator.syncQueueSnapshot(snapshot.playlistQueue, snapshot.currentIndex, snapshot.playMode.name)
        PlaybackCoordinator.persistAsync(prefs) {
            val queueJson = if (snapshot.playlistQueue === lastSerializedQueueRef && lastSerializedQueueJson.isNotBlank()) {
                lastSerializedQueueJson
            } else {
                val cleanQueue = snapshot.playlistQueue.map { it.copy(playUrl = "") }
                gson.toJson(cleanQueue).also {
                    lastSerializedQueueRef = snapshot.playlistQueue
                    lastSerializedQueueJson = it
                }
            }
            // queueJson cached above
            val recentJson = gson.toJson(
                snapshot.recentQueues.take(5).map { rq ->
                    rq.copy(songs = rq.songs.map { s -> s.copy(playUrl = "") })
                }
            )
            putString("cover_style", snapshot.coverStyle.name)
            putString("player_bg_style", snapshot.playerBackgroundStyle.name)
            putBoolean("dynamic_cover_accent_enabled", snapshot.dynamicCoverAccentEnabled)
            putFloat("lyric_translation_scale", snapshot.lyricTranslationScale)
            putBoolean("smooth_play_pause_fade", snapshot.smoothPlayPauseFade)
            putFloat("playback_speed", snapshot.playbackSpeed)
            putFloat("playback_pitch", snapshot.playbackPitch)
            putBoolean("link_pitch_to_speed", snapshot.linkPitchToSpeed)
            putString("play_mode", snapshot.playMode.name)
            putString("preferred_quality_v2", snapshot.preferredQuality.name)
            putBoolean("show_translation_v3", snapshot.showTranslation)
            putBoolean("show_phonetic_v3", snapshot.showPhonetic)
            putFloat("lyric_font_size_v3", snapshot.lyricFontSize)
            putBoolean("motion_orientation_sensor_enabled", snapshot.motionOrientationPermissionEnabled)
            putString("lyric_alignment_v2", snapshot.lyricAlignment.name)
            putBoolean("lyric_word_by_word", snapshot.lyricWordByWord)
            putBoolean("lyric_blur_enabled", snapshot.lyricBlurEnabled)
            putFloat("lyric_blur_intensity", snapshot.lyricBlurIntensity)
            putFloat("lyric_line_spacing", snapshot.lyricLineSpacing)
            putLong("lyric_offset_ms", snapshot.lyricOffsetMs)
            putString("lyric_highlight_theme", snapshot.lyricHighlightTheme.name)
            putFloat("lyric_bg_dim", snapshot.lyricBackgroundDim)
            putBoolean("lyric_keep_screen_on", snapshot.lyricKeepScreenOn)
            putBoolean("sleep_stop_after_song_pref", snapshot.sleepStopAfterCurrentSong)
            putBoolean("climax_mode_enabled", snapshot.climaxModeEnabled)
            putInt("crossfade_seconds", snapshot.crossfadeSeconds)
            putBoolean("volume_normalization", snapshot.volumeNormalization)
            putBoolean("wifi_only_lossless", snapshot.wifiOnlyLossless)
            putBoolean("pause_on_headset_disconnect", snapshot.pauseOnHeadsetDisconnect)
            putBoolean("auto_play_on_startup", snapshot.autoPlayOnStartup)
            putString("saved_queue_json_$userScopeKey", queueJson)
            putString("saved_recent_queues_json_$userScopeKey", recentJson)
            putInt("saved_current_index_$userScopeKey", snapshot.currentIndex)
            putLong("saved_position_ms_$userScopeKey", latestPos)
            putString("saved_queue_json", queueJson)
            putString("saved_recent_queues_json", recentJson)
            putString("saved_active_queue_id", snapshot.activeQueueId)
            putInt("saved_current_index", snapshot.currentIndex)
            putLong("saved_position_ms", latestPos)
        }
    }

    private fun persistPositionOnlyAsync() {
        val snapshot = mutableState.value
        val userScopeKey = currentUser ?: "guest"
        val latestPos = mutablePositionFlow.value.positionMs.takeIf { it > 0L } ?: snapshot.currentPositionMs
        prefs.edit()
            .putInt("saved_current_index_$userScopeKey", snapshot.currentIndex)
            .putLong("saved_position_ms_$userScopeKey", latestPos)
            .putInt("saved_current_index", snapshot.currentIndex)
            .putLong("saved_position_ms", latestPos)
            .apply()
    }

    fun playSong(song: Song, queue: List<Song> = emptyList(), seekToPositionMs: Long = 0L) {
        val player = activePlayer ?: run {
            mutableState.value = mutableState.value.copy(error = "播放器正在连接，请稍后重试")
            return
        }

        val list = (queue.ifEmpty { listOf(song) }).distinctBy { it.id }
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val userId = VipManager.accounts.state.value.currentId

        if (activePlayingSong != null) {
            settleCurrentPlaybackSegment(
                state = "切换下一首",
                clearActiveSong = activePlayingSong?.id != song.id,
                notifySongCompleted = false
            )
        }

        resolveJob?.cancel()
        prefetchJob?.cancel()
        playPauseFadeJob?.cancel()
        isPausingWithFade = false
        lastClimaxSkipSongId = ""
        pendingSeekPositionMs = -1L

        val initialPrefQuality = mutableState.value.preferredQuality
        val initialPlayQuality = if (initialPrefQuality in song.effectiveQualities) initialPrefQuality else song.highestQuality
        val initialQualityLabel = when (initialPlayQuality) {
            AudioQuality.HI_RES -> "Hi-Res"
            AudioQuality.LOSSLESS -> "SQ 无损"
            AudioQuality.HIGH -> "高品 320K"
            AudioQuality.STANDARD -> "标准 128K"
        }

        val (updatedRecentQueues, newActiveQueueId) = syncActiveQueueInRecent(
            queue = list,
            currentIndex = index,
            currentActiveId = mutableState.value.activeQueueId,
            existingRecent = mutableState.value.recentQueues
        )

        updatePositionSnapshot(
            positionMs = seekToPositionMs,
            durationMs = song.durationMs,
            bufferedPositionMs = 0L,
            isPlaying = false
        )
        mutableState.value = mutableState.value.copy(
            loading = true,
            error = null,
            playlistQueue = list,
            currentIndex = index,
            recentQueues = updatedRecentQueues,
            activeQueueId = newActiveQueueId,
            currentSong = song,
            currentPositionMs = seekToPositionMs,
            durationMs = song.durationMs,
            currentQualityLabel = initialQualityLabel,
            isPlaying = true,
            climaxUnlockedForCurrentSong = false,
            songDetail = VipManager.music.songDetail(song),
            commentTargetSong = null,
            comments = emptyList(),
            commentCount = 0
        )
        updatePositionSnapshot(
            positionMs = seekToPositionMs,
            durationMs = song.durationMs,
            bufferedPositionMs = 0L,
            isPlaying = true
        )
        loadLyric(song)
        loadComments(isRefresh = true)
        persistPlaybackStateAsync()

        resolveJob = viewModelScope.launch {
            try {
                val prefQuality = resolveNetworkAwarePreferredQuality(mutableState.value.preferredQuality)
                val useAccompany = mutableState.value.accompanyModeEnabled
                val playQuality = if (prefQuality in song.effectiveQualities) prefQuality else song.highestQuality
                val playbackResult = VipManager.music.resolvePlayback(song, playQuality, useAccompaniment = useAccompany)
                val url = playbackResult.url.takeIf { it.startsWith("http") }
                    ?: song.playUrl.takeIf { it.startsWith("http") }
                    ?: playbackResult.url
                if (lastErrorRetrySongId != song.id) {
                    lastErrorRetrySongId = ""
                }

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
                val isTrulyHiRes = resolvedQuality == AudioQuality.HI_RES ||
                    (song.isHiRes && song.hashHiRes.isNotBlank() && !song.hashHiRes.equals(song.hash320, ignoreCase = true))
                val sanitizedQualities = if (!isTrulyHiRes) {
                    fullQualities.filter { it != AudioQuality.HI_RES }.ifEmpty { listOf(AudioQuality.STANDARD) }
                } else fullQualities

                val resolvedSong = song.copy(
                    playUrl = url,
                    actualPlayQuality = resolvedQuality,
                    availableQualities = sanitizedQualities,
                    isLossless = song.isLossless || resolvedQuality >= AudioQuality.LOSSLESS,
                    isHiRes = isTrulyHiRes,
                    bitrateKbps = playbackResult.bitrateKbps,
                    fileSizeByte = playbackResult.fileSizeByte,
                    climaxStartMs = playbackResult.climaxStartMs.takeIf { it > 0L } ?: song.climaxStartMs,
                    climaxEndMs = playbackResult.climaxEndMs.takeIf { it > 0L } ?: song.climaxEndMs
                )

                val mediaId = mediaId(resolvedSong)
                list.forEach { queued -> songsByMediaId[mediaId(queued)] = queued }
                songsByMediaId[mediaId] = resolvedSong

                val streamCacheKey = AudioCacheManager.buildCacheKey(
                    hash = playbackResult.resolvedHash.ifBlank { resolvedSong.hash },
                    qualityApiValue = resolvedQuality.apiValue,
                    isFreePart = playbackResult.isFreePart
                )
                val item = MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setUri(url)
                    .setCustomCacheKey(streamCacheKey)
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
                    player.setMediaItem(item, targetSeekMs)
                } else {
                    player.setMediaItem(item)
                }
                player.repeatMode = if (mutableState.value.playMode == PlayMode.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                player.prepare()
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

                val updatedQueue = mutableState.value.playlistQueue.map {
                    if (it.id == resolvedSong.id) resolvedSong else it
                }
                updatePositionSnapshot(
                    positionMs = targetSeekMs,
                    durationMs = resolvedSong.durationMs,
                    bufferedPositionMs = 0L,
                    isPlaying = resolveEffectiveIsPlaying(player, isResolving = false) || mutableState.value.isPlaying
                )
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    currentSong = resolvedSong,
                    playlistQueue = updatedQueue,
                    currentPositionMs = targetSeekMs,
                    currentQualityLabel = qualityLabel,
                    isPlaying = resolveEffectiveIsPlaying(player, isResolving = false) || mutableState.value.isPlaying,
                    songDetail = VipManager.music.songDetail(resolvedSong)
                )

                prefetchNextSong(updatedQueue, index)
                if (mutableState.value.commentCount <= 0) {
                    loadComments(isRefresh = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    isPlaying = false,
                    error = e.message ?: "无法获取歌曲播放地址"
                )
                updatePositionSnapshot(positionMs = mutablePositionFlow.value.positionMs, isPlaying = false)
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
                if (mutableState.value.currentSong?.id == song.id) {
                    mutableState.value = mutableState.value.copy(
                        lyricLoading = false,
                        currentLyric = data
                    )
                    updateLyricIndex(currentSmoothPositionMs())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                if (mutableState.value.currentSong?.id == song.id) {
                    mutableState.value = mutableState.value.copy(
                        lyricLoading = false,
                        currentLyric = LyricData()
                    )
                }
            }
        }
    }

    fun togglePlayPause() {
        val player = activePlayer ?: return
        val state = mutableState.value
        if (player.mediaItemCount == 0 && state.currentSong != null) {
            val resumePos = currentSmoothPositionMs().takeIf { it > 0L } ?: state.currentPositionMs
            playSong(state.currentSong, state.playlistQueue, seekToPositionMs = resumePos)
            return
        }
        playPauseFadeJob?.cancel()
        val useSmoothFade = state.smoothPlayPauseFade && playerOverride == null
        if (player.isPlaying && !isPausingWithFade) {
            isPausingWithFade = useSmoothFade
            settleCurrentPlaybackSegment(
                state = "手动停止",
                clearActiveSong = false,
                notifySongCompleted = false
            )
            if (useSmoothFade) {
                syncPlayer()
                playPauseFadeJob = viewModelScope.launch {
                    try {
                        val startVol = player.volume.coerceIn(0.15f, 1.0f)
                        val steps = 4
                        for (step in 1..steps) {
                            activePlayer?.volume = (startVol * (1f - step * 0.22f)).coerceIn(0.06f, 1.0f)
                            delay(28L)
                        }
                        activePlayer?.pause()
                        activePlayer?.volume = 1f
                        persistPlaybackStateAsync()
                    } finally {
                        isPausingWithFade = false
                    }
                }
            } else {
                isPausingWithFade = false
                player.pause()
                player.volume = 1f
                persistPlaybackStateAsync()
            }
        } else {
            isPausingWithFade = false
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            if (useSmoothFade && climaxFadeInJob?.isActive != true) {
                player.volume = 0.22f
                player.play()
                playPauseFadeJob = viewModelScope.launch {
                    val steps = 5
                    for (step in 1..steps) {
                        delay(32L)
                        activePlayer?.volume = (0.22f + 0.78f * (step.toFloat() / steps)).coerceIn(0.22f, 1.0f)
                    }
                    activePlayer?.volume = 1.0f
                }
            } else {
                player.volume = 1f
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val maxDur = mutableState.value.durationMs
        val clamped = if (maxDur > 0L) positionMs.coerceIn(0L, maxDur) else positionMs.coerceAtLeast(0L)
        pendingSeekPositionMs = clamped
        pendingSeekElapsedMs = safeElapsedRealtime()
        val player = activePlayer
        val st = mutableState.value
        val inSleepFade = (!st.sleepStopAfterCurrentSong && st.sleepTimerRemainingSec in 1L..5L) ||
            (st.sleepPendingStopAtTrackEnd && st.sleepTimerRemainingSec <= 0L && st.durationMs > 5_000L && (st.durationMs - clamped) in 1L..5_000L)
        if (!inSleepFade && playPauseFadeJob?.isActive != true) {
            player?.volume = 1f
        }
        updatePositionSnapshot(
            positionMs = clamped,
            durationMs = st.durationMs,
            bufferedPositionMs = mutablePositionFlow.value.bufferedPositionMs,
            isPlaying = st.isPlaying
        )
        mutableState.value = st.copy(currentPositionMs = clamped)
        if (player != null && player.mediaItemCount > 0) {
            if (!player.isPlaying || isPausingWithFade) {
                lastAccumulateTickMs = 0L
                lastRecordTimestamp = 0L
            }
            player.seekTo(clamped)
            syncPlayer()
        }
        updateLyricIndex(clamped)
        persistPlaybackStateAsync()
    }

    fun seekToLyricLine(index: Int) {
        val lines = mutableState.value.currentLyric.lines
        if (index in lines.indices) {
            val targetMs = (lines[index].startTimeMs - mutableState.value.lyricOffsetMs).coerceAtLeast(0L)
            seekTo(targetMs)
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

    /**
     * 在保留当前播放队列的前提下立即播放指定歌曲（若不在队列中则插入到当前歌曲之后并立即播放，不清空原有队列）
     */
    fun playSongInCurrentQueue(song: Song) {
        val state = mutableState.value
        val currentQueue = state.playlistQueue
        if (currentQueue.isEmpty()) {
            playSong(song, listOf(song))
            return
        }
        val existingIdx = currentQueue.indexOfFirst { it.id == song.id }
        if (existingIdx != -1) {
            playSong(song, currentQueue)
        } else {
            val mutableQueue = currentQueue.toMutableList()
            val insertIdx = (state.currentIndex + 1).coerceIn(0, mutableQueue.size)
            mutableQueue.add(insertIdx, song)
            playSong(song, mutableQueue)
        }
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
        val (updatedRecent, activeId) = syncActiveQueueInRecent(queue, newCurrentIndex, state.activeQueueId, state.recentQueues)
        mutableState.value = state.copy(
            playlistQueue = queue,
            currentIndex = newCurrentIndex,
            recentQueues = updatedRecent,
            activeQueueId = activeId
        )
        persistPlaybackStateAsync()

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
            val (updatedRecent, activeId) = syncActiveQueueInRecent(queue, newCurrentIndex, state.activeQueueId, state.recentQueues)
            mutableState.value = state.copy(
                playlistQueue = queue,
                currentIndex = newCurrentIndex,
                recentQueues = updatedRecent,
                activeQueueId = activeId
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
        settleCurrentPlaybackSegment(
            state = "手动停止",
            clearActiveSong = true,
            notifySongCompleted = false
        )
        activePlayer?.stop()
        activePlayer?.clearMediaItems()
        songsByMediaId.clear()
        val st = mutableState.value
        val remainingRecent = st.recentQueues.filterNot { it.id == st.activeQueueId }
        mutablePositionFlow.value = PlaybackPositionSnapshot(playbackSpeed = st.playbackSpeed)
        mutableState.value = PlayerUiState(
            coverStyle = st.coverStyle,
            playerBackgroundStyle = st.playerBackgroundStyle,
            dynamicCoverAccentEnabled = st.dynamicCoverAccentEnabled,
            lyricTranslationScale = st.lyricTranslationScale,
            smoothPlayPauseFade = st.smoothPlayPauseFade,
            playMode = st.playMode,
            preferredQuality = st.preferredQuality,
            currentQualityLabel = st.preferredQuality.label,
            showTranslation = st.showTranslation,
            showPhonetic = st.showPhonetic,
            lyricFontSize = st.lyricFontSize,
            motionOrientationPermissionEnabled = st.motionOrientationPermissionEnabled,
            lyricAlignment = st.lyricAlignment,
            lyricWordByWord = st.lyricWordByWord,
            lyricBlurEnabled = st.lyricBlurEnabled,
            lyricBlurIntensity = st.lyricBlurIntensity,
            lyricLineSpacing = st.lyricLineSpacing,
            lyricOffsetMs = st.lyricOffsetMs,
            lyricHighlightTheme = st.lyricHighlightTheme,
            lyricBackgroundDim = st.lyricBackgroundDim,
            lyricKeepScreenOn = st.lyricKeepScreenOn,
            sleepStopAfterCurrentSong = st.sleepStopAfterCurrentSong,
            climaxModeEnabled = st.climaxModeEnabled,
            crossfadeSeconds = st.crossfadeSeconds,
            volumeNormalization = st.volumeNormalization,
            wifiOnlyLossless = st.wifiOnlyLossless,
            pauseOnHeadsetDisconnect = st.pauseOnHeadsetDisconnect,
            autoPlayOnStartup = st.autoPlayOnStartup,
            recentQueues = remainingRecent,
            activeQueueId = ""
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
        val currentPosition = currentSmoothPositionMs().takeIf { it > 0L } ?: mutableState.value.currentPositionMs
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

    fun togglePhonetic() {
        mutableState.value = mutableState.value.copy(
            showPhonetic = !mutableState.value.showPhonetic
        )
        persistPlaybackStateAsync()
    }

    fun reorderQueueByMood(mode: String): String {
        val state = mutableState.value
        val currentQueue = state.playlistQueue
        if (currentQueue.size <= 2) return "队列曲目较少，无需重排"

        val currentSong = state.currentSong
        val remaining = currentQueue.filter { it.id != currentSong?.id }

        fun songEnergyScore(song: Song): Float {
            val durSec = (song.durationMs / 1000f).coerceAtLeast(60f)
            val climaxSpan = (song.effectiveClimaxEndMs - song.effectiveClimaxStartMs).coerceAtLeast(15_000L) / 1000f
            val density = (climaxSpan / durSec).coerceIn(0.08f, 0.45f)
            val fastBonus = if (durSec < 210f) 0.18f else if (durSec > 270f) -0.12f else 0f
            val hashVariance = ((song.hash.hashCode() and 0xFF) / 255f) * 0.15f
            return (density + fastBonus + hashVariance).coerceIn(0f, 1f)
        }

        val reorderedRemaining = when (mode) {
            "sleep" -> remaining.sortedByDescending { songEnergyScore(it) }.reversed()
            "commute" -> remaining.sortedBy { songEnergyScore(it) }.reversed()
            else -> {
                remaining.sortedWith(
                    compareBy<Song> { (songEnergyScore(it) * 10).toInt() }
                        .thenBy { it.artist }
                )
            }
        }

        val newQueue = if (currentSong != null) {
            listOf(currentSong) + reorderedRemaining
        } else {
            reorderedRemaining
        }
        val (updatedRecent, activeId) = syncActiveQueueInRecent(newQueue, 0, state.activeQueueId, state.recentQueues)
        mutableState.value = state.copy(
            playlistQueue = newQueue,
            currentIndex = 0,
            recentQueues = updatedRecent,
            activeQueueId = activeId
        )
        persistPlaybackStateAsync()
        clearPrefetchedPlayerItems()
        prefetchNextSong(newQueue, 0)

        return when (mode) {
            "sleep" -> "已开启「睡前渐缓」重排"
            "commute" -> "已开启「通勤渐燃」重排"
            else -> "已开启「声学漫游」重排"
        }
    }

    private var queueReorderCommitJob: Job? = null

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val state = mutableState.value
        val queue = state.playlistQueue.toMutableList()
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
        val currentSongId = state.currentSong?.id
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
        val newCurrentIndex = if (currentSongId != null) {
            queue.indexOfFirst { it.id == currentSongId }.coerceAtLeast(0)
        } else state.currentIndex
        mutableState.value = state.copy(
            playlistQueue = queue,
            currentIndex = newCurrentIndex
        )
        clearPrefetchedPlayerItems()
        queueReorderCommitJob?.cancel()
        queueReorderCommitJob = viewModelScope.launch {
            delay(220L)
            val latest = mutableState.value
            val (updatedRecent, activeId) = syncActiveQueueInRecent(
                latest.playlistQueue,
                latest.currentIndex,
                latest.activeQueueId,
                latest.recentQueues
            )
            mutableState.value = latest.copy(
                recentQueues = updatedRecent,
                activeQueueId = activeId
            )
            persistPlaybackStateAsync()
            clearPrefetchedPlayerItems()
            prefetchNextSong(latest.playlistQueue, latest.currentIndex)
        }
    }

    internal fun clearPrefetchedPlayerItems() {
        prefetchedNextQueueIndex = -1
        prefetchedNextSongKey = ""
        activePlayer?.let { player ->
            while (player.mediaItemCount > player.currentMediaItemIndex + 1) {
                player.removeMediaItem(player.mediaItemCount - 1)
            }
        }
    }

    private fun resolveNetworkAwarePreferredQuality(preferred: AudioQuality): AudioQuality {
        if (!mutableState.value.wifiOnlyLossless || preferred <= AudioQuality.HIGH) {
            return preferred
        }
        return runCatching {
            val cm = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return@runCatching preferred
            val net = cm.activeNetwork ?: return@runCatching preferred
            val caps = cm.getNetworkCapabilities(net) ?: return@runCatching preferred
            val isMeteredCellular = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            if (isMeteredCellular) AudioQuality.HIGH else preferred
        }.getOrDefault(preferred)
    }

    fun setCoverStyle(style: CoverStyle) {
        mutableState.value = mutableState.value.copy(coverStyle = style)
        persistPlaybackStateAsync()
    }

    fun setPlayerBackgroundStyle(style: PlayerBackgroundStyle) {
        mutableState.value = mutableState.value.copy(playerBackgroundStyle = style)
        persistPlaybackStateAsync()
    }

    fun setDynamicCoverAccentEnabled(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(dynamicCoverAccentEnabled = enabled)
        persistPlaybackStateAsync()
    }

    fun setLyricTranslationScale(scale: Float) {
        mutableState.value = mutableState.value.copy(lyricTranslationScale = scale.coerceIn(0.70f, 1.0f))
        persistPlaybackStateAsync()
    }

    fun setSmoothPlayPauseFade(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(smoothPlayPauseFade = enabled)
        persistPlaybackStateAsync()
    }

    fun setLyricFontSize(size: Float) {
        mutableState.value = mutableState.value.copy(lyricFontSize = size.coerceIn(18f, 34f))
        persistPlaybackStateAsync()
    }

    fun setMotionOrientationPermissionEnabled(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(motionOrientationPermissionEnabled = enabled)
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

    fun setLyricBlurEnabled(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(lyricBlurEnabled = enabled)
        persistPlaybackStateAsync()
    }

    fun setLyricBlurIntensity(intensity: Float) {
        mutableState.value = mutableState.value.copy(lyricBlurIntensity = intensity.coerceIn(0.3f, 2.0f))
        persistPlaybackStateAsync()
    }

    fun setLyricLineSpacing(spacing: Float) {
        mutableState.value = mutableState.value.copy(lyricLineSpacing = spacing.coerceIn(6f, 26f))
        persistPlaybackStateAsync()
    }

    fun setLyricOffsetMs(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(-5000L, 5000L)
        mutableState.value = mutableState.value.copy(lyricOffsetMs = clamped)
        updateLyricIndex(currentSmoothPositionMs())
        persistPlaybackStateAsync()
    }

    fun setLyricHighlightTheme(theme: LyricHighlightTheme) {
        mutableState.value = mutableState.value.copy(lyricHighlightTheme = theme)
        persistPlaybackStateAsync()
    }

    fun setLyricBackgroundDim(dim: Float) {
        mutableState.value = mutableState.value.copy(lyricBackgroundDim = dim.coerceIn(0.35f, 0.92f))
        persistPlaybackStateAsync()
    }

    fun setLyricKeepScreenOn(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(lyricKeepScreenOn = enabled)
        persistPlaybackStateAsync()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        mutableState.value = mutableState.value.copy(crossfadeSeconds = seconds.coerceIn(0, 8))
        persistPlaybackStateAsync()
    }

    fun setVolumeNormalization(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(volumeNormalization = enabled)
        com.concept.music.service.AudioEffectManager.setVolumeNormalizationEnabled(enabled)
        persistPlaybackStateAsync()
    }

    fun setWifiOnlyLossless(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(wifiOnlyLossless = enabled)
        persistPlaybackStateAsync()
    }

    fun setPauseOnHeadsetDisconnect(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(pauseOnHeadsetDisconnect = enabled)
        PlaybackService.updateHandleAudioBecomingNoisy(enabled)
        persistPlaybackStateAsync()
    }

    fun setAutoPlayOnStartup(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(autoPlayOnStartup = enabled)
        persistPlaybackStateAsync()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = ((speed * 20).toInt() / 20f).coerceIn(0.5f, 2.0f)
        val current = mutableState.value
        val newPitch = if (current.linkPitchToSpeed) clampedSpeed else current.playbackPitch
        mutableState.update {
            it.copy(
                playbackSpeed = clampedSpeed,
                playbackPitch = newPitch
            )
        }
        SystemLyricBridge.playbackSpeedSnapshot = clampedSpeed
        updatePositionSnapshot(
            positionMs = currentSmoothPositionMs(),
            playbackSpeed = clampedSpeed
        )
        activePlayer?.playbackParameters = PlaybackParameters(clampedSpeed, newPitch)
        persistPlaybackStateAsync()
    }

    fun setPlaybackPitch(pitch: Float) {
        val clampedPitch = ((pitch * 20).toInt() / 20f).coerceIn(0.5f, 2.0f)
        val current = mutableState.value
        mutableState.update {
            it.copy(
                playbackPitch = clampedPitch,
                linkPitchToSpeed = false
            )
        }
        activePlayer?.playbackParameters = PlaybackParameters(current.playbackSpeed, clampedPitch)
        persistPlaybackStateAsync()
    }

    fun setLinkPitchToSpeed(linked: Boolean) {
        val current = mutableState.value
        val newPitch = if (linked) current.playbackSpeed else 1.0f
        mutableState.update {
            it.copy(
                linkPitchToSpeed = linked,
                playbackPitch = newPitch
            )
        }
        activePlayer?.playbackParameters = PlaybackParameters(current.playbackSpeed, newPitch)
        persistPlaybackStateAsync()
    }

    fun resetSpeedAndPitch() {
        mutableState.update {
            it.copy(
                playbackSpeed = 1.0f,
                playbackPitch = 1.0f,
                linkPitchToSpeed = false
            )
        }
        SystemLyricBridge.playbackSpeedSnapshot = 1.0f
        updatePositionSnapshot(
            positionMs = currentSmoothPositionMs(),
            playbackSpeed = 1.0f
        )
        activePlayer?.playbackParameters = PlaybackParameters(1.0f, 1.0f)
        persistPlaybackStateAsync()
    }

    fun setSleepStopAfterCurrentSongPreference(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(sleepStopAfterCurrentSong = enabled)
        prefs.edit().putBoolean("sleep_stop_after_song_pref", enabled).apply()
    }

    private fun trimPrefetchedMediaItems() {
        prefetchJob?.cancel()
        activePlayer?.let { player ->
            while (player.mediaItemCount > player.currentMediaItemIndex + 1) {
                player.removeMediaItem(player.mediaItemCount - 1)
            }
        }
    }

    fun setSleepTimer(minutes: Int, stopAfterCurrentSong: Boolean = mutableState.value.sleepStopAfterCurrentSong) {
        sleepTimerJob?.cancel()
        activePlayer?.volume = 1f

        prefs.edit().putBoolean("sleep_stop_after_song_pref", stopAfterCurrentSong).apply()

        if (minutes <= 0 && !stopAfterCurrentSong) {
            mutableState.value = mutableState.value.copy(
                sleepTimerRemainingSec = 0L,
                sleepPendingStopAtTrackEnd = false
            )
            prefetchNextSong(mutableState.value.playlistQueue, mutableState.value.currentIndex)
            return
        }

        if (minutes <= 0 && stopAfterCurrentSong) {
            trimPrefetchedMediaItems()
            mutableState.value = mutableState.value.copy(
                sleepTimerRemainingSec = 0L,
                sleepStopAfterCurrentSong = true,
                sleepPendingStopAtTrackEnd = true
            )
            return
        }

        val totalSec = minutes * 60L
        mutableState.value = mutableState.value.copy(
            sleepTimerRemainingSec = totalSec,
            sleepStopAfterCurrentSong = stopAfterCurrentSong,
            sleepPendingStopAtTrackEnd = stopAfterCurrentSong
        )

        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSec
            while (remaining > 0L) {
                delay(1_000L)
                remaining -= 1L
                mutableState.value = mutableState.value.copy(sleepTimerRemainingSec = remaining)

                if (!mutableState.value.sleepStopAfterCurrentSong && remaining in 1L..5L) {
                    activePlayer?.volume = (remaining / 5f).coerceIn(0.1f, 1f)
                }
            }

            if (!mutableState.value.sleepStopAfterCurrentSong) {
                activePlayer?.pause()
                activePlayer?.volume = 1f
                mutableState.value = mutableState.value.copy(
                    sleepTimerRemainingSec = 0L,
                    sleepPendingStopAtTrackEnd = false
                )
            } else {
                trimPrefetchedMediaItems()
                mutableState.value = mutableState.value.copy(
                    sleepTimerRemainingSec = 0L,
                    sleepPendingStopAtTrackEnd = true
                )
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        activePlayer?.volume = 1f
        mutableState.value = mutableState.value.copy(
            sleepTimerRemainingSec = 0L,
            sleepPendingStopAtTrackEnd = false
        )
        prefetchNextSong(mutableState.value.playlistQueue, mutableState.value.currentIndex)
    }

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
            val curPos = currentSmoothPositionMs()
            if (curPos < climaxStart || curPos >= climaxEnd) {
                player.seekTo(climaxStart)
                triggerClimaxSmoothFadeIn()
                updatePositionSnapshot(positionMs = climaxStart)
                mutableState.value = mutableState.value.copy(currentPositionMs = climaxStart)
            }
        } else {
            climaxFadeInJob?.cancel()
            player.volume = 1f
        }
    }

    fun unlockFullTrackForCurrentSong() {
        climaxFadeInJob?.cancel()
        activePlayer?.volume = 1f
        mutableState.value = mutableState.value.copy(
            climaxUnlockedForCurrentSong = true
        )
    }

    fun toggleAccompanyMode() {
        val nextAccompany = !mutableState.value.accompanyModeEnabled
        mutableState.value = mutableState.value.copy(accompanyModeEnabled = nextAccompany)
        val currentSong = mutableState.value.currentSong ?: return
        val currentPosition = currentSmoothPositionMs().takeIf { it > 0L } ?: mutableState.value.currentPositionMs
        val updatedSong = currentSong.copy(playUrl = "")
        playSong(updatedSong, mutableState.value.playlistQueue, seekToPositionMs = currentPosition)
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

    /**
     * 打开任意歌曲的评论页（不打断当前正在播放的歌曲，也不破坏当前播放队列）
     */
    fun openCommentsForSong(targetSong: Song?) {
        val song = targetSong ?: mutableState.value.currentSong ?: return
        val currentTarget = mutableState.value.commentTargetSong ?: mutableState.value.currentSong
        val isSameTarget = currentTarget != null && (currentTarget.id == song.id || (song.hash.isNotBlank() && currentTarget.hash == song.hash))
        mutableState.value = mutableState.value.copy(
            commentTargetSong = if (song.id == mutableState.value.currentSong?.id) null else song,
            comments = if (isSameTarget) mutableState.value.comments else emptyList(),
            commentCount = if (isSameTarget) mutableState.value.commentCount else 0,
            commentsPage = 1,
            commentsHasMore = true
        )
        loadComments(isRefresh = true)
    }

    fun closeCommentsForTargetSong() {
        if (mutableState.value.commentTargetSong != null) {
            commentsJob?.cancel()
            mutableState.value = mutableState.value.copy(
                commentTargetSong = null,
                comments = emptyList(),
                commentCount = 0,
                commentsPage = 1,
                commentsHasMore = true
            )
            if (mutableState.value.currentSong != null) {
                loadComments(isRefresh = true)
            }
        }
    }

    fun setCommentType(type: String) {
        if (mutableState.value.commentType == type) return
        mutableState.value = mutableState.value.copy(
            commentType = type,
            commentsPage = 1,
            comments = emptyList(),
            commentsHasMore = true
        )
        loadComments(isRefresh = true)
    }

    fun loadComments(isRefresh: Boolean = false) {
        val song = mutableState.value.commentTargetSong ?: mutableState.value.currentSong ?: return
        if (!isRefresh && (mutableState.value.commentsLoading || !mutableState.value.commentsHasMore)) return
        if (isRefresh) {
            commentsJob?.cancel()
        }
        val page = if (isRefresh) 1 else mutableState.value.commentsPage + 1
        val type = mutableState.value.commentType
        val targetSongId = song.id
        val targetSongHash = song.hash

        commentsJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                commentsLoading = true,
                commentsPage = if (isRefresh) 1 else mutableState.value.commentsPage
            )
            runCatching {
                val list = VipManager.music.songComments(song, type = type, page = page, pageSize = 30)
                val activeSong = mutableState.value.commentTargetSong ?: mutableState.value.currentSong
                if (activeSong == null || (activeSong.id != targetSongId && activeSong.hash != targetSongHash)) {
                    return@launch
                }
                val current = if (isRefresh) emptyList<SongComment>() else mutableState.value.comments
                val merged: List<SongComment> = (current + list).distinctBy { it.id }
                val serverCount = VipManager.music.lastSongCommentCount
                val prevCount = if (isRefresh) 0 else mutableState.value.commentCount
                val count = when {
                    serverCount > 0 -> maxOf(serverCount, merged.size)
                    merged.isNotEmpty() -> maxOf(prevCount, merged.size)
                    else -> prevCount
                }
                val addedNewItems = isRefresh || merged.size > current.size
                mutableState.value = mutableState.value.copy(
                    comments = merged,
                    commentsLoading = false,
                    commentsPage = page,
                    commentsHasMore = list.size >= 8 && addedNewItems,
                    commentCount = count
                )
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                mutableState.value = mutableState.value.copy(commentsLoading = false)
            }
        }
    }

    fun loadCommentReplies(commentId: String) {
        val song = mutableState.value.commentTargetSong ?: mutableState.value.currentSong ?: return
        if (commentId.isBlank()) return
        val existingSpecialChildId = mutableState.value.comments.find { it.id == commentId }?.specialChildId.orEmpty()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val replies = VipManager.music.songCommentReplies(
                    song = song,
                    commentId = commentId,
                    page = 1,
                    pageSize = 30,
                    specialChildId = existingSpecialChildId
                )
                if (replies.isNotEmpty()) {
                    mutableState.update { cur ->
                        val updatedList = cur.comments.map { cmt ->
                            if (cmt.id == commentId) {
                                val mergedReplies = (cmt.replies + replies).distinctBy { it.id }
                                cmt.copy(
                                    replies = mergedReplies,
                                    replyCount = maxOf(cmt.replyCount, mergedReplies.size)
                                )
                            } else cmt
                        }
                        cur.copy(comments = updatedList)
                    }
                }
            }
        }
    }

    fun syncCommentReplyCount(commentId: String, actualCount: Int, latestReplies: List<SongComment>? = null) {
        if (commentId.isBlank() || actualCount < 0) return
        val updatedList = mutableState.value.comments.map { cmt ->
            if (cmt.id == commentId) {
                val mergedReplies = if (!latestReplies.isNullOrEmpty()) {
                    (cmt.replies + latestReplies).distinctBy { it.id }
                } else {
                    cmt.replies
                }
                cmt.copy(
                    replyCount = actualCount,
                    replies = mergedReplies
                )
            } else cmt
        }
        mutableState.value = mutableState.value.copy(comments = updatedList)
    }

    fun postComment(
        content: String,
        replyToCommentId: String? = null,
        replyToUserName: String? = null,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val song = mutableState.value.commentTargetSong ?: mutableState.value.currentSong ?: run {
            onComplete(false, "未选择当前歌曲")
            return
        }
        if (content.isBlank()) {
            onComplete(false, "评论内容不能为空")
            return
        }

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
                    val directRootIdx = currentList.indexOfFirst { it.id == replyToCommentId }
                    val subRootIdx = if (directRootIdx != -1) {
                        directRootIdx
                    } else {
                        currentList.indexOfFirst { root -> root.replies.any { it.id == replyToCommentId } }
                    }
                    if (subRootIdx != -1) {
                        val parent = currentList[subRootIdx]
                        val targetReplyId = if (replyToCommentId != parent.id) replyToCommentId else null
                        val enrichedNew = newComment.copy(
                            parentId = replyToCommentId,
                            rootId = parent.id,
                            replyTargetCommentId = targetReplyId
                        )
                        val updatedReplies = parent.replies + enrichedNew
                        currentList[subRootIdx] = parent.copy(
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
                onComplete(true, if (replyToCommentId != null) "回复已发布" else "评论已发布")
            }.onFailure { err ->
                onComplete(false, err.message ?: "评论发送失败，请稍后重试")
            }
        }
    }

    fun likeComment(commentId: String) {
        var targetNewLikeState: Boolean? = null
        val currentList = mutableState.value.comments.map { comment ->
            if (comment.id == commentId) {
                val nextLiked = !comment.isLiked
                targetNewLikeState = nextLiked
                val delta = if (nextLiked) 1 else -1
                comment.copy(
                    isLiked = nextLiked,
                    likeCount = (comment.likeCount + delta).coerceAtLeast(0)
                )
            } else {
                val updatedReplies = comment.replies.map { reply ->
                    if (reply.id == commentId) {
                        val nextLiked = !reply.isLiked
                        targetNewLikeState = nextLiked
                        val delta = if (nextLiked) 1 else -1
                        reply.copy(
                            isLiked = nextLiked,
                            likeCount = (reply.likeCount + delta).coerceAtLeast(0)
                        )
                    } else reply
                }
                comment.copy(replies = updatedReplies)
            }
        }
        mutableState.value = mutableState.value.copy(comments = currentList)
        val song = mutableState.value.commentTargetSong ?: mutableState.value.currentSong
        val liked = targetNewLikeState
        if (song != null && liked != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { VipManager.music.likeSongComment(song, commentId, liked) }
            }
        }
    }

    private fun prefetchNextSong(queue: List<Song>, currentIndex: Int) {
        prefetchJob?.cancel()
        if (queue.isEmpty()) return
        if (mutableState.value.sleepPendingStopAtTrackEnd && mutableState.value.sleepTimerRemainingSec <= 0L) return
        val nextIndex = getNextIndex(queue, currentIndex)
        if (nextIndex in queue.indices && nextIndex != currentIndex) {
            val nextSong = queue[nextIndex]
            prefetchJob = viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val prefQuality = resolveNetworkAwarePreferredQuality(mutableState.value.preferredQuality)
                    val useAccompany = mutableState.value.accompanyModeEnabled
                    val nextQuality = if (prefQuality in nextSong.effectiveQualities) {
                        prefQuality
                    } else nextSong.highestQuality
                    val nextResult = VipManager.music.resolvePlayback(
                        nextSong,
                        nextQuality,
                        useAccompaniment = useAccompany
                    )
                    VipManager.lyric.getLyric(nextSong)

                    withContext(Dispatchers.Main) {
                        val player = activePlayer ?: return@withContext
                        val stNow = mutableState.value
                        if (nextResult.url.isNotBlank() &&
                            stNow.playMode != PlayMode.REPEAT_ONE &&
                            !(stNow.sleepPendingStopAtTrackEnd && stNow.sleepTimerRemainingSec <= 0L)
                        ) {
                            val nextMediaId = mediaId(nextSong)
                            val resolvedQuality = nextResult.actualQuality
                            val isTrulyHiRes = resolvedQuality == AudioQuality.HI_RES ||
                                (nextSong.isHiRes && nextSong.hashHiRes.isNotBlank() && !nextSong.hashHiRes.equals(nextSong.hash320, ignoreCase = true))
                            val resolvedNextSong = nextSong.copy(
                                playUrl = nextResult.url,
                                actualPlayQuality = resolvedQuality,
                                isLossless = nextSong.isLossless || resolvedQuality >= AudioQuality.LOSSLESS,
                                isHiRes = isTrulyHiRes,
                                bitrateKbps = nextResult.bitrateKbps,
                                fileSizeByte = nextResult.fileSizeByte,
                                climaxStartMs = nextResult.climaxStartMs.takeIf { it > 0L } ?: nextSong.climaxStartMs,
                                climaxEndMs = nextResult.climaxEndMs.takeIf { it > 0L } ?: nextSong.climaxEndMs
                            )
                            songsByMediaId[nextMediaId] = resolvedNextSong
                            val nextCacheKey = AudioCacheManager.buildCacheKey(
                                hash = nextResult.resolvedHash.ifBlank { resolvedNextSong.hash },
                                qualityApiValue = resolvedQuality.apiValue,
                                isFreePart = nextResult.isFreePart
                            )
                            val nextItem = MediaItem.Builder()
                                .setMediaId(nextMediaId)
                                .setUri(nextResult.url)
                                .setCustomCacheKey(nextCacheKey)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(nextSong.title)
                                        .setArtist(nextSong.artist)
                                        .setAlbumTitle(nextSong.album)
                                        .setArtworkUri(nextSong.coverUrl.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse))
                                        .build()
                                ).build()

                            while (player.currentMediaItemIndex > 6) {
                                player.removeMediaItem(0)
                            }
                            while (player.mediaItemCount > player.currentMediaItemIndex + 1) {
                                player.removeMediaItem(player.mediaItemCount - 1)
                            }
                            prefetchedNextQueueIndex = nextIndex
                            prefetchedNextSongKey = "${nextSong.id}_${nextSong.hash}"
                            player.addMediaItem(nextItem)
                            if (playerOverride == null && !useAccompany) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    val warmCandidates = buildList {
                                        val secondIdx = getNextIndex(queue, nextIndex)
                                        if (secondIdx in queue.indices && secondIdx != currentIndex && secondIdx != nextIndex) {
                                            add(queue[secondIdx])
                                        }
                                        val prevIdx = if (currentIndex - 1 < 0) queue.size - 1 else currentIndex - 1
                                        if (prevIdx in queue.indices && prevIdx != currentIndex && prevIdx != nextIndex) {
                                            add(queue[prevIdx])
                                        }
                                    }
                                    if (warmCandidates.isNotEmpty()) {
                                        runCatching { VipManager.music.prewarmSongsPlayback(warmCandidates, prefQuality, maxCount = 2) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onTrackEnded() {
        val current = mutableState.value.currentSong
        val isRepeatOne = mutableState.value.playMode == PlayMode.REPEAT_ONE
        if (current != null) {
            settleCurrentPlaybackSegment(
                state = "完整播放",
                clearActiveSong = !isRepeatOne,
                notifySongCompleted = true
            )
        }
        if (mutableState.value.sleepPendingStopAtTrackEnd && mutableState.value.sleepTimerRemainingSec <= 0L) {
            activePlayer?.pause()
            activePlayer?.volume = 1f
            mutableState.value = mutableState.value.copy(
                isPlaying = false,
                sleepPendingStopAtTrackEnd = false
            )
            updatePositionSnapshot(positionMs = mutablePositionFlow.value.positionMs, isPlaying = false)
            return
        }
        when (mutableState.value.playMode) {
            PlayMode.REPEAT_ONE -> {
                activePlayer?.seekTo(0)
                activePlayer?.play()
                if (current != null && (activePlayer?.isPlaying == true || mutableState.value.isPlaying)) {
                    startNewPlaybackSegment(current, activePlayer?.currentMediaItem?.mediaId ?: mediaId(current), recordHistory = false)
                }
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
        val isResolving = resolveJob?.isActive == true
        val hasMedia = player.mediaItemCount > 0
        val isBufferingWithPlayIntent = !isPausingWithFade && hasMedia && player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
        val isAudiblyPlaying = !isResolving && !isPausingWithFade && hasMedia && player.isPlaying

        if (song != null && isAudiblyPlaying) {
            if (recordedMediaId != mediaId || activePlayingSong?.id != song.id) {
                if (activePlayingSong != null && activePlayingSong?.id != song.id) {
                    settleCurrentPlaybackSegment(
                        state = "切换下一首",
                        clearActiveSong = true,
                        notifySongCompleted = false
                    )
                }
                startNewPlaybackSegment(song, mediaId, recordHistory = true)
            } else if (!csccSegmentOpen) {
                startNewPlaybackSegment(song, mediaId, recordHistory = false)
            } else {
                val nowElapsed = safeElapsedRealtime()
                if (lastAccumulateTickMs == 0L) {
                    lastAccumulateTickMs = nowElapsed
                } else {
                    accumulateActiveSlice(nowElapsed)
                }
                if (lastRecordTimestamp == 0L) {
                    lastRecordTimestamp = nowElapsed
                } else {
                    flushGradePlaybackSeconds(nowElapsed, song)
                }
            }
        } else if (activePlayingSong != null) {
            val nowElapsed = safeElapsedRealtime()
            accumulateActiveSlice(nowElapsed)
            flushGradePlaybackSeconds(nowElapsed, activePlayingSong)
            lastAccumulateTickMs = 0L
            lastRecordTimestamp = 0L
            if (!isResolving && !isBufferingWithPlayIntent && (!player.isPlaying || isPausingWithFade)) {
                settleCurrentPlaybackSegment(
                    state = "手动停止",
                    clearActiveSong = false,
                    notifySongCompleted = false
                )
            }
        }

        val rawPlayerPos = if (isResolving || !hasMedia) {
            mutablePositionFlow.value.positionMs.takeIf { it > 0L } ?: mutableState.value.currentPositionMs
        } else {
            player.currentPosition.coerceAtLeast(0)
        }
        val nowElapsed = safeElapsedRealtime()
        val pos = if (!isResolving && pendingSeekPositionMs >= 0L && nowElapsed - pendingSeekElapsedMs in 0L..550L) {
            if (kotlin.math.abs(rawPlayerPos - pendingSeekPositionMs) <= 800L && player.playbackState == Player.STATE_READY) {
                val settled = rawPlayerPos
                pendingSeekPositionMs = -1L
                settled
            } else {
                pendingSeekPositionMs
            }
        } else {
            if (nowElapsed - pendingSeekElapsedMs > 550L) pendingSeekPositionMs = -1L
            rawPlayerPos
        }
        val dur = if (isResolving || !hasMedia) (song?.durationMs ?: 0L) else (player.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0L)
        val buf = if (isResolving || !hasMedia) 0L else player.bufferedPosition.coerceAtLeast(0)
        val isPlayingNow = resolveEffectiveIsPlaying(player, isResolving)

        if (song != null && isAudiblyPlaying && csccSegmentOpen) {
            val remainTrackForCheckpoint = if (dur > 0L) (dur - pos) else Long.MAX_VALUE
            if (currentEffectivePlayedMs() >= 60_000L && remainTrackForCheckpoint >= 20_000L) {
                settleCurrentPlaybackSegment(
                    state = "完整播放",
                    clearActiveSong = false,
                    notifySongCompleted = false
                )
                startNewPlaybackSegment(song, mediaId, recordHistory = false)
            }
        }

        val st = mutableState.value
        updatePositionSnapshot(
            positionMs = pos,
            durationMs = dur,
            bufferedPositionMs = buf,
            isPlaying = isPlayingNow,
            playbackSpeed = st.playbackSpeed
        )

        if (st.isPlaying != isPlayingNow) {
            AmbientSoundManager.onMusicPlayStateChanged(isPlayingNow)
            if (song != null) {
                ConceptMusicWidgetUpdater.updateState(
                    context = getApplication(),
                    songTitle = song.title,
                    artist = song.artist,
                    isPlaying = isPlayingNow
                )
            }
        }

        if (st.climaxModeEnabled && !st.climaxUnlockedForCurrentSong && song != null && isPlayingNow) {
            val climaxEnd = song.effectiveClimaxEndMs
            val remainClimaxMs = climaxEnd - pos
            if (remainClimaxMs in 1L..2_500L && climaxFadeInJob?.isActive != true) {
                player.volume = (remainClimaxMs / 2_500f).coerceIn(0.15f, 1.0f)
            } else if (pos >= climaxEnd && lastClimaxSkipSongId != song.id) {
                if (st.playMode == PlayMode.REPEAT_ONE) {
                    lastClimaxSkipSongId = ""
        pendingSeekPositionMs = -1L
                    val climaxStart = song.effectiveClimaxStartMs
                    seekTo(climaxStart)
                    triggerClimaxSmoothFadeIn()
                } else {
                    lastClimaxSkipSongId = song.id
                    player.volume = 1f
                    skipToNext()
                }
                return
            }
        }

        if (st.sleepPendingStopAtTrackEnd && st.sleepTimerRemainingSec <= 0L && dur > 5_000L && isPlayingNow) {
            val remainTrackMs = dur - pos
            if (remainTrackMs in 1L..5_000L) {
                player.volume = (remainTrackMs / 5_000f).coerceIn(0.08f, 1f)
            }
        } else if (st.crossfadeSeconds > 0 && !st.climaxModeEnabled && dur > 12_000L && isPlayingNow && climaxFadeInJob?.isActive != true) {
            val fadeWindowMs = st.crossfadeSeconds * 1000L
            val remainTrackMs = dur - pos
            if (remainTrackMs in 1L..fadeWindowMs) {
                player.volume = (remainTrackMs.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.15f, 1f)
            } else if (pos < 1_500L && st.crossfadeSeconds >= 2) {
                player.volume = (0.35f + 0.65f * (pos.toFloat() / 1_500f)).coerceIn(0.35f, 1f)
            } else if (playPauseFadeJob?.isActive != true && player.volume < 0.99f) {
                player.volume = 1f
            }
        } else if (playPauseFadeJob?.isActive != true && climaxFadeInJob?.isActive != true && !st.climaxModeEnabled && player.volume < 0.99f) {
            player.volume = 1f
        }

        val newLoading = isResolving || (hasMedia && player.playbackState == Player.STATE_BUFFERING)
        val updatedSongWithDur = if (song != null && song.durationMs <= 0L && dur > 0L) {
            song.copy(durationMs = dur)
        } else song
        val updatedDetail = if (updatedSongWithDur !== song && updatedSongWithDur != null) {
            VipManager.music.songDetail(updatedSongWithDur)
        } else st.songDetail

        // 核心性能优化：仅当低频结构性状态（播放启停、总时长、缓冲加载态、当前曲目）变更时才广播 uiState，
        // 毫秒级进度更新由 playbackPositionFlow 与 currentSmoothPositionMs() 独立承载，彻底消除每 120ms 全树重组！
        if (st.isPlaying != isPlayingNow ||
            st.durationMs != dur ||
            st.loading != newLoading ||
            updatedSongWithDur !== song
        ) {
            mutableState.value = st.copy(
                currentSong = updatedSongWithDur,
                songDetail = updatedDetail,
                isPlaying = isPlayingNow,
                currentPositionMs = pos,
                durationMs = dur,
                bufferedPositionMs = buf,
                loading = newLoading
            )
        }
        if (!isResolving) {
            updateLyricIndex(pos)
        }
        val latestState = mutableState.value
        val adjustedPos = (pos + latestState.lyricOffsetMs).coerceAtLeast(0L)
        SystemLyricBridge.updatePlaybackSnapshot(
            song = latestState.currentSong,
            lyricData = latestState.currentLyric,
            currentLyricIndex = latestState.currentLyricIndex,
            currentPositionMs = adjustedPos,
            isPlaying = latestState.isPlaying
        )
    }

    internal fun updateLyricIndex(pos: Long) {
        val st = mutableState.value
        val lyric = st.currentLyric
        val adjustedPos = (pos + st.lyricOffsetMs).coerceAtLeast(0L)
        if (lyric.lines.isNotEmpty()) {
            val index = lyric.getLineIndexAt(adjustedPos)
            if (index != st.currentLyricIndex) {
                mutableState.value = st.copy(
                    currentLyricIndex = index,
                    currentPositionMs = pos
                )
                val line = lyric.lines.getOrNull(index)
                SystemLyricBridge.onLyricLineUpdated(
                    context = getApplication(),
                    song = st.currentSong,
                    lineIndex = index,
                    line = line,
                    isPlaying = st.isPlaying
                )
            }
            SystemLyricBridge.updatePlaybackSnapshot(
                song = st.currentSong,
                lyricData = lyric,
                currentLyricIndex = index,
                currentPositionMs = adjustedPos,
                isPlaying = st.isPlaying
            )
        } else if (st.currentLyricIndex != -1) {
            mutableState.value = st.copy(
                currentLyricIndex = -1,
                currentPositionMs = pos
            )
            SystemLyricBridge.updatePlaybackSnapshot(
                song = st.currentSong,
                lyricData = lyric,
                currentLyricIndex = -1,
                currentPositionMs = adjustedPos,
                isPlaying = st.isPlaying
            )
        }
    }

    private fun mediaId(song: Song): String = song.id

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = viewModelScope.launch {
            while (true) {
                val delayMs = if (mutableState.value.isPlaying) 120L else 500L
                syncPlayer()

                if (mutableState.value.isPlaying) {
                    val now = safeElapsedRealtime()
                    if (now - lastPersistPosTimestamp >= 5_000L) {
                        lastPersistPosTimestamp = now
                        persistPositionOnlyAsync()
                    }
                }

                delay(delayMs)
            }
        }
    }

    override fun onCleared() {
        persistPlaybackStateAsync()
        settleCurrentPlaybackSegment(
            state = "手动停止",
            clearActiveSong = true,
            notifySongCompleted = false
        )
        resolveJob?.cancel()
        lyricJob?.cancel()
        commentsJob?.cancel()
        progressJob?.cancel()
        prefetchJob?.cancel()
        sleepTimerJob?.cancel()
        climaxFadeInJob?.cancel()
        playPauseFadeJob?.cancel()
        ConceptMusicWidgetUpdater.onWidgetCommand = null
        PlaybackCoordinator.onTogglePlayDelegate = null
        PlaybackService.onSkipToNextIntercepted = null
        PlaybackService.onSkipToPreviousIntercepted = null
        SystemLyricBridge.onTogglePlayCallback = null
        SystemLyricBridge.onPreviousCallback = null
        SystemLyricBridge.onNextCallback = null
        activePlayer?.removeListener(listener)
        controllerFuture?.let(MediaController::releaseFuture)
        super.onCleared()
    }
}
