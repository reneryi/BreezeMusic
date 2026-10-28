package com.concept.music.service

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.concept.music.data.kugou.KugouSigner
import com.concept.music.data.kugou.suspendRunCatching
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.viewmodel.PlayMode
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Application/Service 级常驻后台播放与持久化协调器：
 * 1. 解耦 `MainActivity` / `MusicPlayerViewModel` 生命周期与后台播放控制；
 * 2. 当 `MainActivity` 在后台被回收或桌面微件冷启动唤醒服务时，接管播放/暂停与上下首切歌；
 * 3. 提供独立于 `viewModelScope` 的单线程串行 IO 协程池，保障大队列序列化不阻塞主线程且退出时落盘不中断。
 */
object PlaybackCoordinator {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val persistDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val ioScope = CoroutineScope(SupervisorJob() + persistDispatcher)

    @Volatile
    var onTogglePlayDelegate: (() -> Boolean)? = null

    @Volatile
    var onResumePlayDelegate: (() -> Boolean)? = null

    @Volatile
    var onSkipToPreviousDelegate: (() -> Boolean)? = null

    @Volatile
    var onSkipToNextDelegate: (() -> Boolean)? = null

    @Volatile
    private var cachedQueue: List<Song> = emptyList()

    @Volatile
    private var cachedIndex: Int = 0

    @Volatile
    private var cachedPlayModeName: String = PlayMode.REPEAT_ALL.name

    @Volatile
    private var bgResolveJob: Job? = null

    fun syncQueueSnapshot(queue: List<Song>, currentIndex: Int, playModeName: String) {
        cachedQueue = queue
        cachedIndex = currentIndex
        cachedPlayModeName = playModeName
    }

    fun persistAsync(prefs: SharedPreferences, block: SharedPreferences.Editor.() -> Unit): Job {
        return ioScope.launch {
            runCatching {
                val editor = prefs.edit()
                editor.block()
                editor.apply()
            }
        }
    }

    fun handleTogglePlay(context: Context, player: Player?): Boolean {
        onTogglePlayDelegate?.let { delegate ->
            if (delegate()) return true
        }
        val activePlayer = player ?: return false
        if (activePlayer.mediaItemCount > 0) {
            val targetPlay = !activePlayer.isPlaying
            if (targetPlay) {
                if (activePlayer.playbackState == Player.STATE_ENDED) {
                    activePlayer.seekTo(0L)
                } else if (activePlayer.playbackState == Player.STATE_IDLE) {
                    activePlayer.prepare()
                }
                activePlayer.play()
            } else {
                activePlayer.pause()
            }
            AmbientSoundManager.onMusicPlayStateChanged(targetPlay)
            ConceptMusicWidgetUpdater.updateState(context.applicationContext, isPlaying = targetPlay)
            return true
        }
        return playFromBackgroundQueue(context, activePlayer, offsetDelta = 0)
    }

    /**
     * 当插入有线耳机或连接蓝牙耳机时触发自动开始播放（仅在当前未播放时启动，绝不误暂停正在播放的音乐）
     */
    fun handleResumeOnHeadsetConnected(context: Context, player: Player?): Boolean {
        if (player?.isPlaying == true) return false
        onResumePlayDelegate?.let { delegate ->
            if (delegate()) return true
        }
        val activePlayer = player ?: return false
        if (activePlayer.isPlaying) return false
        if (activePlayer.mediaItemCount > 0) {
            if (activePlayer.playbackState == Player.STATE_ENDED) {
                activePlayer.seekTo(0L)
            } else if (activePlayer.playbackState == Player.STATE_IDLE) {
                activePlayer.prepare()
            }
            activePlayer.play()
            AmbientSoundManager.onMusicPlayStateChanged(true)
            ConceptMusicWidgetUpdater.updateState(context.applicationContext, isPlaying = true)
            return true
        }
        return playFromBackgroundQueue(context, activePlayer, offsetDelta = 0)
    }

    fun handleSkipToPrevious(context: Context, player: Player?): Boolean {
        onSkipToPreviousDelegate?.let { delegate ->
            if (delegate()) return true
        }
        val activePlayer = player ?: return false
        return playFromBackgroundQueue(context, activePlayer, offsetDelta = -1)
    }

    fun handleSkipToNext(context: Context, player: Player?): Boolean {
        onSkipToNextDelegate?.let { delegate ->
            if (delegate()) return true
        }
        val activePlayer = player ?: return false
        return playFromBackgroundQueue(context, activePlayer, offsetDelta = 1)
    }

    private fun playFromBackgroundQueue(
        context: Context,
        player: Player,
        offsetDelta: Int
    ): Boolean {
        val appCtx = context.applicationContext
        VipManager.initialize(appCtx)
        val prefs = appCtx.getSharedPreferences("concept_player_prefs", Context.MODE_PRIVATE)
        val userScopeKey = VipManager.accounts.state.value.currentId ?: "guest"

        val queue: List<Song> = cachedQueue.ifEmpty {
            val queueJson = prefs.getString("saved_queue_json_$userScopeKey", null)
                ?: prefs.getString("saved_queue_json", null)
            if (queueJson.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching {
                    val type = object : TypeToken<List<Song>>() {}.type
                    KugouSigner.gson.fromJson<List<Song>>(queueJson, type)
                        ?.map { it.sanitizeFromJson() }
                        .orEmpty()
                }.getOrDefault(emptyList())
            }
        }
        if (queue.isEmpty()) return false

        val baseIndex = if (cachedQueue.isNotEmpty()) {
            cachedIndex
        } else {
            prefs.getInt("saved_current_index_$userScopeKey", prefs.getInt("saved_current_index", 0))
        }.coerceIn(0, queue.lastIndex)

        val modeName = if (cachedQueue.isNotEmpty()) {
            cachedPlayModeName
        } else {
            prefs.getString("play_mode", PlayMode.REPEAT_ALL.name) ?: PlayMode.REPEAT_ALL.name
        }
        val playMode = runCatching { PlayMode.valueOf(modeName) }.getOrDefault(PlayMode.REPEAT_ALL)

        val targetIdx = when {
            offsetDelta == 0 -> baseIndex
            playMode == PlayMode.SHUFFLE && queue.size > 1 -> {
                var r = Random.nextInt(queue.size)
                if (r == baseIndex) r = (baseIndex + 1) % queue.size
                r
            }
            offsetDelta > 0 -> (baseIndex + 1) % queue.size
            else -> if (baseIndex - 1 < 0) queue.lastIndex else baseIndex - 1
        }
        val targetSong = queue.getOrNull(targetIdx) ?: return false
        syncQueueSnapshot(queue, targetIdx, playMode.name)

        val savedQuality = runCatching {
            AudioQuality.valueOf(
                prefs.getString("preferred_quality_v2", AudioQuality.HIGH.name) ?: AudioQuality.HIGH.name
            )
        }.getOrDefault(AudioQuality.HIGH)

        persistAsync(prefs) {
            putInt("saved_current_index_$userScopeKey", targetIdx)
            putInt("saved_current_index", targetIdx)
            putLong("saved_position_ms_$userScopeKey", 0L)
            putLong("saved_position_ms", 0L)
        }

        bgResolveJob?.cancel()
        bgResolveJob = mainScope.launch {
            val playbackResult = withContext(Dispatchers.IO) {
                suspendRunCatching {
                    val reqQ = if (savedQuality in targetSong.effectiveQualities) {
                        savedQuality
                    } else {
                        targetSong.highestQuality
                    }
                    VipManager.music.resolvePlayback(targetSong, reqQ)
                }.getOrNull()
            } ?: return@launch

            if (playbackResult.url.isNotBlank()) {
                val resolvedHash = playbackResult.resolvedHash.ifBlank {
                    targetSong.hashForQuality(playbackResult.actualQuality).ifBlank { targetSong.hash }
                }
                val cacheKey = AudioCacheManager.buildCacheKey(
                    hash = resolvedHash,
                    qualityApiValue = playbackResult.actualQuality.apiValue,
                    isFreePart = playbackResult.isFreePart
                )
                val item = MediaItem.Builder()
                    .setMediaId(targetSong.id)
                    .setUri(playbackResult.url)
                    .setCustomCacheKey(cacheKey)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(targetSong.title)
                            .setArtist(targetSong.artist)
                            .setAlbumTitle(targetSong.album)
                            .setArtworkUri(targetSong.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                            .build()
                    )
                    .build()
                player.setMediaItem(item)
                player.repeatMode = if (playMode == PlayMode.REPEAT_ONE) {
                    Player.REPEAT_MODE_ONE
                } else {
                    Player.REPEAT_MODE_OFF
                }
                player.prepare()
                player.play()
                AmbientSoundManager.onMusicPlayStateChanged(true)
                ConceptMusicWidgetUpdater.updateState(
                    context = appCtx,
                    songTitle = targetSong.title,
                    artist = targetSong.artist,
                    isPlaying = true
                )
            }
        }
        return true
    }
}
