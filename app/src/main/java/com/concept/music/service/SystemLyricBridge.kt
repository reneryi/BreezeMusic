package com.concept.music.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SystemLyricState(
    val floatingCapsuleEnabled: Boolean = false,
    val floatingCapsuleLocked: Boolean = false,
    val statusBarBroadcastEnabled: Boolean = true,
    val lastBroadcastedLine: String = ""
)

/**
 * 系统级 KRC 逐字悬浮歌词与状态栏歌词广播桥接器 ([INNOV-05])
 * 1. 支持向主流定制 Android 状态栏歌词协议（Lyricon / 魅族 Flyme 状态栏 Ticker / 通用歌词广播）实时推送当前歌词行与耗时；
 * 2. 管理全局悬浮 KRC 逐字流光胶囊歌词的状态与锁定开关。
 */
object SystemLyricBridge {
    private var prefs: SharedPreferences? = null
    private var lastLineIndex: Int = -1
    private var lastSongId: String = ""

    private val _state = MutableStateFlow(SystemLyricState())
    val state = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("concept_system_lyric", Context.MODE_PRIVATE)
        prefs = p
        _state.value = SystemLyricState(
            floatingCapsuleEnabled = p.getBoolean("floating_capsule_enabled", false),
            floatingCapsuleLocked = p.getBoolean("floating_capsule_locked", false),
            statusBarBroadcastEnabled = p.getBoolean("status_bar_broadcast_enabled", true)
        )
    }

    fun setFloatingCapsuleEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(floatingCapsuleEnabled = enabled)
        prefs?.edit()?.putBoolean("floating_capsule_enabled", enabled)?.apply()
    }

    fun setFloatingCapsuleLocked(locked: Boolean) {
        _state.value = _state.value.copy(floatingCapsuleLocked = locked)
        prefs?.edit()?.putBoolean("floating_capsule_locked", locked)?.apply()
    }

    fun setStatusBarBroadcastEnabled(context: Context, enabled: Boolean) {
        _state.value = _state.value.copy(statusBarBroadcastEnabled = enabled)
        prefs?.edit()?.putBoolean("status_bar_broadcast_enabled", enabled)?.apply()
        if (!enabled) {
            sendStopBroadcast(context)
        }
    }

    /**
     * 当播放进度或当前歌词行变化时调用，向系统状态栏歌词插件广播最新歌词与单句持续时长
     */
    fun onLyricLineUpdated(
        context: Context,
        song: Song?,
        lineIndex: Int,
        line: LyricLine?,
        isPlaying: Boolean
    ) {
        if (song == null || !isPlaying || line == null || line.text.isBlank()) {
            return
        }
        if (song.id == lastSongId && lineIndex == lastLineIndex) {
            return
        }
        lastSongId = song.id
        lastLineIndex = lineIndex

        val cleanText = line.text.trim()
        _state.value = _state.value.copy(lastBroadcastedLine = cleanText)

        // 同步更新桌面小组件上的实时歌词
        ConceptMusicWidgetUpdater.updateState(
            context = context,
            songTitle = song.title,
            artist = song.artist,
            lyricLine = cleanText,
            isPlaying = isPlaying
        )

        if (!_state.value.statusBarBroadcastEnabled) return

        runCatching {
            val appCtx = context.applicationContext
            val durationMs = line.durationMs.coerceAtLeast(1500L)

            // 1. 通用 StatusBarLyric / LyricGetter / Evo 状态栏歌词广播协议
            val lyricIntent = Intent("com.concept.music.STATUS_BAR_LYRIC").apply {
                putExtra("lyric", cleanText)
                putExtra("translation", line.translation)
                putExtra("song_title", song.title)
                putExtra("artist", song.artist)
                putExtra("duration", durationMs)
                putExtra("pkg", appCtx.packageName)
            }
            appCtx.sendBroadcast(lyricIntent)

            // 2. 兼容酷狗/魅族 Flyme/MIUI 第三方状态栏歌词扩展广播
            val flymeIntent = Intent("com.meizu.flyme.service.StatusBarLyric").apply {
                putExtra("ticker", cleanText)
                putExtra("lyric", cleanText)
                putExtra("duration", durationMs.toInt())
                putExtra("sender_package", appCtx.packageName)
            }
            appCtx.sendBroadcast(flymeIntent)
        }
    }

    fun sendStopBroadcast(context: Context) {
        lastLineIndex = -1
        runCatching {
            val appCtx = context.applicationContext
            val stopIntent = Intent("com.concept.music.STATUS_BAR_LYRIC").apply {
                putExtra("lyric", "")
                putExtra("stop", true)
                putExtra("pkg", appCtx.packageName)
            }
            appCtx.sendBroadcast(stopIntent)
        }
    }
}
