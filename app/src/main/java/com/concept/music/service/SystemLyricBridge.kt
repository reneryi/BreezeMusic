package com.concept.music.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DesktopLyricColorScheme(
    val label: String,
    val playedStartHex: Long,
    val playedEndHex: Long,
    val unplayedHex: Long,
    val strokeHex: Long = 0xFF060A12
) {
    ICE_BLUE("概念冰蓝", 0xFF3898FF, 0xFF00E5FF, 0xFFE2E8F0),
    VIP_GOLD("璀璨鎏金", 0xFFFFD166, 0xFFFF9F1C, 0xFFFFF3D6),
    SAKURA_NEON("霓虹樱粉", 0xFFFF6B9D, 0xFFFF8E53, 0xFFFFE4EE),
    AURORA_MINT("极光薄荷", 0xFF2CE5B5, 0xFF00B4D8, 0xFFE0FBF4),
    CYBER_VIOLET("赛博星紫", 0xFFA855F7, 0xFF6366F1, 0xFFEDE9FE),
    PURE_SNOW("纯净皓白", 0xFFFFFFFF, 0xFF93C5FD, 0xFFCBD5E1)
}

enum class DesktopLyricLineMode(val label: String) {
    DUAL_BILINGUAL("双行双语"),
    DUAL_ALTERNATING("KTV双行交替"),
    SINGLE_COMPACT("单行极简")
}

data class SystemLyricState(
    val floatingCapsuleEnabled: Boolean = false, // 桌面歌词总开关（保留字段名以兼容旧调用）
    val floatingCapsuleLocked: Boolean = false,  // 桌面歌词防误触锁定
    val statusBarBroadcastEnabled: Boolean = true,
    val lastBroadcastedLine: String = "",
    val desktopColorScheme: DesktopLyricColorScheme = DesktopLyricColorScheme.ICE_BLUE,
    val desktopLineMode: DesktopLyricLineMode = DesktopLyricLineMode.DUAL_BILINGUAL,
    val desktopFontSize: Float = 17f,
    val desktopBgAlpha: Float = 0.78f,
    val desktopAlignment: Int = 0, // 0: 居中, 1: 居左
    val desktopShowTranslation: Boolean = true,
    val savedOffsetX: Float = 0f,
    val savedOffsetY: Float = 140f
)

/**
 * 桌面歌词与系统状态栏歌词桥接器
 * 1. 管理全功能「桌面歌词」（支持 6 套流光配色、单行/双行双语/KTV交替模式、字号无级缩放、无框全透明/磨砂底框透明度调节、播放控制栏、防误触锁定、位置记忆）；
 * 2. 支持向主流定制 Android 状态栏歌词协议（Lyricon / Flyme / HyperOS / EvoX / LyricGetter）实时推送当前歌词行与耗时。
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
        val schemeName = p.getString("desktop_color_scheme", DesktopLyricColorScheme.ICE_BLUE.name)
            ?: DesktopLyricColorScheme.ICE_BLUE.name
        val modeName = p.getString("desktop_line_mode", DesktopLyricLineMode.DUAL_BILINGUAL.name)
            ?: DesktopLyricLineMode.DUAL_BILINGUAL.name
        _state.value = SystemLyricState(
            floatingCapsuleEnabled = p.getBoolean("floating_capsule_enabled", false),
            floatingCapsuleLocked = p.getBoolean("floating_capsule_locked", false),
            statusBarBroadcastEnabled = p.getBoolean("status_bar_broadcast_enabled", true),
            desktopColorScheme = runCatching { DesktopLyricColorScheme.valueOf(schemeName) }
                .getOrDefault(DesktopLyricColorScheme.ICE_BLUE),
            desktopLineMode = runCatching { DesktopLyricLineMode.valueOf(modeName) }
                .getOrDefault(DesktopLyricLineMode.DUAL_BILINGUAL),
            desktopFontSize = p.getFloat("desktop_font_size", 17f).coerceIn(13f, 26f),
            desktopBgAlpha = p.getFloat("desktop_bg_alpha", 0.78f).coerceIn(0f, 0.96f),
            desktopAlignment = p.getInt("desktop_alignment", 0).coerceIn(0, 1),
            desktopShowTranslation = p.getBoolean("desktop_show_translation", true),
            savedOffsetX = p.getFloat("desktop_offset_x", 0f),
            savedOffsetY = p.getFloat("desktop_offset_y", 140f)
        )
    }

    fun setFloatingCapsuleEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(floatingCapsuleEnabled = enabled)
        prefs?.edit()?.putBoolean("floating_capsule_enabled", enabled)?.apply()
    }

    fun setDesktopLyricEnabled(enabled: Boolean) = setFloatingCapsuleEnabled(enabled)

    fun setFloatingCapsuleLocked(locked: Boolean) {
        _state.value = _state.value.copy(floatingCapsuleLocked = locked)
        prefs?.edit()?.putBoolean("floating_capsule_locked", locked)?.apply()
    }

    fun setDesktopColorScheme(scheme: DesktopLyricColorScheme) {
        _state.value = _state.value.copy(desktopColorScheme = scheme)
        prefs?.edit()?.putString("desktop_color_scheme", scheme.name)?.apply()
    }

    fun setDesktopLineMode(mode: DesktopLyricLineMode) {
        _state.value = _state.value.copy(desktopLineMode = mode)
        prefs?.edit()?.putString("desktop_line_mode", mode.name)?.apply()
    }

    fun setDesktopFontSize(sizeSp: Float) {
        val clamped = sizeSp.coerceIn(13f, 26f)
        _state.value = _state.value.copy(desktopFontSize = clamped)
        prefs?.edit()?.putFloat("desktop_font_size", clamped)?.apply()
    }

    fun setDesktopBgAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0f, 0.96f)
        _state.value = _state.value.copy(desktopBgAlpha = clamped)
        prefs?.edit()?.putFloat("desktop_bg_alpha", clamped)?.apply()
    }

    fun setDesktopAlignment(alignment: Int) {
        val clamped = alignment.coerceIn(0, 1)
        _state.value = _state.value.copy(desktopAlignment = clamped)
        prefs?.edit()?.putInt("desktop_alignment", clamped)?.apply()
    }

    fun setDesktopShowTranslation(show: Boolean) {
        _state.value = _state.value.copy(desktopShowTranslation = show)
        prefs?.edit()?.putBoolean("desktop_show_translation", show)?.apply()
    }

    fun saveDesktopPosition(offsetX: Float, offsetY: Float) {
        _state.value = _state.value.copy(savedOffsetX = offsetX, savedOffsetY = offsetY)
        prefs?.edit()
            ?.putFloat("desktop_offset_x", offsetX)
            ?.putFloat("desktop_offset_y", offsetY)
            ?.apply()
    }

    fun setStatusBarBroadcastEnabled(context: Context, enabled: Boolean) {
        _state.value = _state.value.copy(statusBarBroadcastEnabled = enabled)
        prefs?.edit()?.putBoolean("status_bar_broadcast_enabled", enabled)?.apply()
        if (!enabled) {
            sendStopBroadcast(context)
        }
    }

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

            val lyricIntent = Intent("com.concept.music.STATUS_BAR_LYRIC").apply {
                putExtra("lyric", cleanText)
                putExtra("translation", line.translation)
                putExtra("song_title", song.title)
                putExtra("artist", song.artist)
                putExtra("duration", durationMs)
                putExtra("pkg", appCtx.packageName)
            }
            appCtx.sendBroadcast(lyricIntent)

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
