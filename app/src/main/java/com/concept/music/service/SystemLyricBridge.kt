package com.concept.music.service

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.LyricWord
import com.concept.music.data.model.Song
import com.concept.music.ui.viewmodel.LyricHighlightTheme
import com.concept.music.ui.viewmodel.PlayMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class DesktopLyricColorScheme(
    val label: String,
    val playedStartHex: Long,
    val playedEndHex: Long,
    val unplayedHex: Long,
    val strokeHex: Long = 0xFF060A12
) {
    ICE_BLUE("概念冰蓝", 0xFF3898FF, 0xFF00E5FF, 0xFFF1F5F9),
    VIP_GOLD("鎏金岁华", 0xFFFFD166, 0xFFFF9F1C, 0xFFFFF8E7),
    SAKURA_NEON("霓虹樱粉", 0xFFFF6B9D, 0xFFFF8E53, 0xFFFFF0F5),
    AURORA_MINT("极光薄荷", 0xFF2CE5B5, 0xFF00B4D8, 0xFFECFEFF),
    CYBER_VIOLET("赛博星紫", 0xFFA855F7, 0xFF6366F1, 0xFFF3E8FF),
    PURE_SNOW("流光皓白", 0xFFFFFFFF, 0xFF93C5FD, 0xFFCBD5E1);

    fun toHighlightTheme(): LyricHighlightTheme = when (this) {
        ICE_BLUE -> LyricHighlightTheme.ICE_BLUE
        VIP_GOLD -> LyricHighlightTheme.VIP_GOLD
        SAKURA_NEON -> LyricHighlightTheme.SAKURA_PINK
        AURORA_MINT -> LyricHighlightTheme.AURORA_MINT
        PURE_SNOW -> LyricHighlightTheme.PURE_WHITE
        CYBER_VIOLET -> LyricHighlightTheme.DYNAMIC_COVER
    }

    companion object {
        @JvmField val SAKURA_PINK = SAKURA_NEON
        @JvmField val EMERALD_GREEN = AURORA_MINT

        fun fromHighlightTheme(theme: LyricHighlightTheme): DesktopLyricColorScheme = when (theme) {
            LyricHighlightTheme.ICE_BLUE -> ICE_BLUE
            LyricHighlightTheme.VIP_GOLD -> VIP_GOLD
            LyricHighlightTheme.SAKURA_PINK -> SAKURA_NEON
            LyricHighlightTheme.AURORA_MINT -> AURORA_MINT
            LyricHighlightTheme.PURE_WHITE -> PURE_SNOW
            LyricHighlightTheme.DYNAMIC_COVER -> CYBER_VIOLET
        }
    }
}

enum class DesktopLyricLineMode(val label: String) {
    DUAL_BILINGUAL("双行双语"),
    DUAL_ALTERNATING("KTV双行交替"),
    SINGLE_COMPACT("单行极简")
}

data class SystemLyricState(
    val floatingCapsuleEnabled: Boolean = false, // 桌面歌词总开关
    val floatingCapsuleLocked: Boolean = false,  // 桌面歌词防误触锁定（支持穿透）
    val systemOverlayActive: Boolean = false,    // 是否已挂载系统级跨应用桌面歌词悬浮窗
    val desktopColorScheme: DesktopLyricColorScheme = DesktopLyricColorScheme.ICE_BLUE,
    val desktopLineMode: DesktopLyricLineMode = DesktopLyricLineMode.DUAL_BILINGUAL,
    val desktopFontSize: Float = 20f,
    val desktopWidthFraction: Float = 1.0f,     // 保留兼容字段，实际窗口自适应铺满全屏宽度
    val desktopBgAlpha: Float = 0.68f,
    val desktopAlignment: Int = 0,               // 0: 居中, 1: 居左
    val desktopShowTranslation: Boolean = true,
    val savedOffsetX: Float = 0f,
    val savedOffsetY: Float = 140f
)

/**
 * 桌面歌词单行渲染描述：
 * - `verticalCenterFraction`: 该行中心在固定三行高度容器 `[0f, 1f]` 中的垂直归一化坐标：
 *   - 当两行足够显示时（短歌词）：采用「插空居中」排布（`0.28f` 与 `0.72f`），两行之间留出呼吸空间且整体严格居中；
 *   - 当遇到长歌词折为三行时：自然分布于 `0.18f`、`0.50f`、`0.82f`，容器总高度保持三行空间恒定不变。
 */
internal data class DesktopLyricRenderRow(
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val lineStartTimeMs: Long = 0L,
    val lineDurationMs: Long = 0L,
    val isActiveKaraoke: Boolean = false,
    val isSecondaryRow: Boolean = false,
    val verticalCenterFraction: Float = 0.50f
) {
    val isPrimary: Boolean get() = !isSecondaryRow
}

internal data class DesktopLyricLayoutPlan(
    val rows: List<DesktopLyricRenderRow>,
    val isThreeLineLayout: Boolean,
    val fixedThreeLineHeightPx: Float
)

/**
 * 估算文本视觉字符宽度（中文字符计 1.0，英文/数字/空格计 0.56），用于判定单行是否装得下
 */
internal fun estimateVisualCharUnits(text: String): Float {
    if (text.isEmpty()) return 0f
    var units = 0f
    for (ch in text) {
        units += if (ch.code <= 0x7F) 0.56f else 1.0f
    }
    return units
}

/**
 * 将超长主歌词（连同逐字时间戳 `words`）在自然语义边界处拆分为上下两行，
 * 保证长歌词折为两行后仍然具备完整的逐字卡拉 OK 流光与弹性跃动动效。
 */
internal fun splitLongLyricLineIntoTwoRows(
    line: LyricLine,
    maxVisualUnitsPerLine: Float
): Pair<LyricLine, LyricLine> {
    val fullText = line.text.trim()
    if (line.words.size >= 2) {
        val totalUnits = line.words.sumOf { estimateVisualCharUnits(it.text).toDouble() }.toFloat().coerceAtLeast(1f)
        val targetFirstRowUnits = (totalUnits * 0.52f).coerceAtMost(maxVisualUnitsPerLine)
        var acc = 0f
        var splitIdx = 1
        for (i in line.words.indices) {
            acc += estimateVisualCharUnits(line.words[i].text)
            if (acc >= targetFirstRowUnits && i < line.words.lastIndex) {
                splitIdx = i + 1
                break
            }
        }
        splitIdx = splitIdx.coerceIn(1, line.words.lastIndex)
        val firstWords = line.words.subList(0, splitIdx)
        val secondWords = line.words.subList(splitIdx, line.words.size)
        val firstText = firstWords.joinToString("") { it.text }.trim()
        val secondText = secondWords.joinToString("") { it.text }.trim()
        val firstStart = firstWords.firstOrNull()?.startTimeMs ?: line.startTimeMs
        val firstEnd = firstWords.lastOrNull()?.let { it.startTimeMs + it.durationMs } ?: (line.startTimeMs + line.durationMs / 2)
        val secondStart = secondWords.firstOrNull()?.startTimeMs ?: firstEnd
        val secondEnd = secondWords.lastOrNull()?.let { it.startTimeMs + it.durationMs } ?: (line.startTimeMs + line.durationMs)
        return LyricLine(
            startTimeMs = firstStart,
            durationMs = (firstEnd - firstStart).coerceAtLeast(1L),
            text = firstText,
            words = firstWords
        ) to LyricLine(
            startTimeMs = secondStart,
            durationMs = (secondEnd - secondStart).coerceAtLeast(1L),
            text = secondText,
            words = secondWords
        )
    }

    // 无逐字时间戳时按空格或标点或中点拆分
    val mid = (fullText.length / 2).coerceAtLeast(1)
    var breakPos = -1
    val punctuation = charArrayOf(' ', ',', '，', '。', '！', '？', '；', '、', '~', '-')
    for (offset in 0..8) {
        val right = (mid + offset).coerceAtMost(fullText.lastIndex - 1)
        val left = (mid - offset).coerceAtLeast(1)
        if (fullText[right] in punctuation) {
            breakPos = right + 1
            break
        }
        if (fullText[left] in punctuation) {
            breakPos = left + 1
            break
        }
    }
    if (breakPos <= 0 || breakPos >= fullText.length) {
        breakPos = mid
    }
    val part1 = fullText.substring(0, breakPos).trim()
    val part2 = fullText.substring(breakPos).trim()
    val halfDur = (line.durationMs / 2L).coerceAtLeast(1L)
    return LyricLine(
        startTimeMs = line.startTimeMs,
        durationMs = halfDur,
        text = part1
    ) to LyricLine(
        startTimeMs = line.startTimeMs + halfDur,
        durationMs = (line.durationMs - halfDur).coerceAtLeast(1L),
        text = part2
    )
}

/**
 * 核心桌面歌词三行恒定空间与「两行插空居中 / 长句自然三行」布局规划器：
 * 1. 容器高度 `fixedThreeLineHeightPx` 始终保持 3 行高度不变；
 * 2. 当主歌词较短（单行可容纳）时，仅渲染 2 行（主歌词 + 译文/下一句），垂直中心坐标设为 `0.28f` 与 `0.72f`，
 *    实现「插空显示、整体居中」（均值 `(0.28 + 0.72)/2 = 0.50`）；
 * 3. 当主歌词较长超出单行容量时，主歌词自然折为第 1、2 行，译文/下一句置于第 3 行，垂直中心坐标设为 `0.18f`、`0.50f`、`0.82f`。
 */
internal fun resolveDesktopLyricLayoutPlan(
    currentLine: LyricLine?,
    nextLine: LyricLine?,
    fallbackTitle: String = "微风音乐",
    showTranslation: Boolean = true,
    maxVisualUnitsPerLine: Float = 16f,
    singleRowSlotHeightPx: Float = 36f,
    song: Any? = null,
    fontSizeSp: Float = 19f,
    lineMode: DesktopLyricLineMode = DesktopLyricLineMode.DUAL_BILINGUAL,
    maxCharsPerSingleRow: Float = maxVisualUnitsPerLine
): DesktopLyricLayoutPlan {
    val fixedThreeLineHeight = (singleRowSlotHeightPx * 3f).coerceAtLeast(1f)
    val primaryText = currentLine?.text?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle
    val secondaryText = when {
        showTranslation && !currentLine?.translation.isNullOrBlank() -> currentLine?.translation?.trim().orEmpty()
        !nextLine?.text.isNullOrBlank() -> nextLine?.text?.trim().orEmpty()
        else -> "♪  •  •  •"
    }

    val safeMaxUnits = (maxCharsPerSingleRow.takeIf { it > 0f } ?: maxVisualUnitsPerLine).coerceAtLeast(10f)
    val primaryUnits = estimateVisualCharUnits(primaryText)
    val isPrimaryLong = currentLine != null && primaryUnits > safeMaxUnits

    return if (isPrimaryLong && currentLine != null) {
        val (row1Line, row2Line) = splitLongLyricLineIntoTwoRows(currentLine, safeMaxUnits)
        DesktopLyricLayoutPlan(
            rows = listOf(
                DesktopLyricRenderRow(
                    text = row1Line.text,
                    words = row1Line.words,
                    lineStartTimeMs = row1Line.startTimeMs,
                    lineDurationMs = row1Line.durationMs,
                    isActiveKaraoke = true,
                    isSecondaryRow = false,
                    verticalCenterFraction = 0.18f
                ),
                DesktopLyricRenderRow(
                    text = row2Line.text,
                    words = row2Line.words,
                    lineStartTimeMs = row2Line.startTimeMs,
                    lineDurationMs = row2Line.durationMs,
                    isActiveKaraoke = true,
                    isSecondaryRow = false,
                    verticalCenterFraction = 0.50f
                ),
                DesktopLyricRenderRow(
                    text = secondaryText,
                    words = emptyList(),
                    isActiveKaraoke = false,
                    isSecondaryRow = true,
                    verticalCenterFraction = 0.82f
                )
            ),
            isThreeLineLayout = true,
            fixedThreeLineHeightPx = fixedThreeLineHeight
        )
    } else {
        // 短歌词两行插空显示，整体垂直居中 (0.28f & 0.72f，中心点 0.50f)
        DesktopLyricLayoutPlan(
            rows = listOf(
                DesktopLyricRenderRow(
                    text = primaryText,
                    words = currentLine?.words.orEmpty(),
                    lineStartTimeMs = currentLine?.startTimeMs ?: 0L,
                    lineDurationMs = currentLine?.durationMs ?: 0L,
                    isActiveKaraoke = currentLine != null,
                    isSecondaryRow = false,
                    verticalCenterFraction = 0.28f
                ),
                DesktopLyricRenderRow(
                    text = secondaryText,
                    words = emptyList(),
                    isActiveKaraoke = false,
                    isSecondaryRow = true,
                    verticalCenterFraction = 0.72f
                )
            ),
            isThreeLineLayout = false,
            fixedThreeLineHeightPx = fixedThreeLineHeight
        )
    }
}

/**
 * 桌面悬浮歌词与桌面小组件歌词同步桥接器（全新无框常驻 + 毛玻璃固定锚点 + App 内双向同步架构）：
 * 1. 启动后常驻态仅显示带柔光立体阴影的歌词，无任何背景框；
 * 2. 横向自适应铺满屏幕宽度，锁定水平偏移 `X = 0`，仅允许上下拖动调节高度；
 * 3. 歌词区恒定保留三行空间，短句两行插空垂直居中，长句自然折为三行；
 * 4. 点击歌词唤醒毛玻璃背景框与上方 7 个圆润无框控制按钮，歌词本身垂直坐标零位移；
 * 5. 点击设置按钮平滑向下延展，提供「歌词颜色」与「歌词字号」两项设置，并与 App 内设置双向实时同步。
 */
object SystemLyricBridge {
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastLineIndex: Int = -1
    private var lastSongId: String = ""

    @Volatile
    internal var currentSongSnapshot: Song? = null
    @Volatile
    internal var currentLyricSnapshot: LyricData = LyricData()
    @Volatile
    internal var currentLineIndexSnapshot: Int = -1
    @Volatile
    internal var currentPositionMsSnapshot: Long = 0L
    @Volatile
    internal var lastPositionSyncRealtimeMs: Long = 0L
    @Volatile
    internal var isPlayingSnapshot: Boolean = false
    @Volatile
    internal var playbackSpeedSnapshot: Float = 1.0f
    @Volatile
    internal var playModeSnapshot: PlayMode = PlayMode.REPEAT_ALL

    // 播放与双向设置同步回调
    var onTogglePlayCallback: (() -> Unit)? = null
    var onPreviousCallback: (() -> Unit)? = null
    var onNextCallback: (() -> Unit)? = null
    var onCyclePlayModeCallback: (() -> Unit)? = null
    var onDesktopColorChangedCallback: ((LyricHighlightTheme) -> Unit)? = null
    var onDesktopFontSizeChangedCallback: ((Float) -> Unit)? = null

    private var windowManager: WindowManager? = null
    private var overlayView: NativeDesktopLyricView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var unlockPillView: NativeUnlockPillView? = null
    private var unlockPillParams: WindowManager.LayoutParams? = null

    private val _state = MutableStateFlow(SystemLyricState())
    val state = _state.asStateFlow()

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (prefs != null) return
        val p = ctx.getSharedPreferences("concept_system_lyric", Context.MODE_PRIVATE)
        prefs = p
        val schemeName = p.getString("desktop_color_scheme", DesktopLyricColorScheme.ICE_BLUE.name)
            ?: DesktopLyricColorScheme.ICE_BLUE.name
        val modeName = p.getString("desktop_line_mode", DesktopLyricLineMode.DUAL_BILINGUAL.name)
            ?: DesktopLyricLineMode.DUAL_BILINGUAL.name

        _state.value = SystemLyricState(
            floatingCapsuleEnabled = p.getBoolean("floating_capsule_enabled", false),
            floatingCapsuleLocked = p.getBoolean("floating_capsule_locked", false),
            systemOverlayActive = false,
            desktopColorScheme = runCatching { DesktopLyricColorScheme.valueOf(schemeName) }
                .getOrDefault(DesktopLyricColorScheme.ICE_BLUE),
            desktopLineMode = runCatching { DesktopLyricLineMode.valueOf(modeName) }
                .getOrDefault(DesktopLyricLineMode.DUAL_BILINGUAL),
            desktopFontSize = p.getFloat("desktop_font_size", 20f).coerceIn(15f, 26f),
            desktopWidthFraction = p.getFloat("desktop_width_fraction", 0.88f).coerceIn(0.68f, 0.96f),
            desktopBgAlpha = p.getFloat("desktop_bg_alpha", 0.68f).coerceIn(0f, 0.96f),
            desktopAlignment = p.getInt("desktop_alignment", 0).coerceIn(0, 1),
            desktopShowTranslation = p.getBoolean("desktop_show_translation", true),
            savedOffsetX = 0f,
            savedOffsetY = p.getFloat("desktop_offset_y", 140f)
        )
        syncSystemOverlayWindow()
    }

    fun canDrawSystemOverlays(context: Context? = appContext): Boolean {
        val ctx = context ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { Settings.canDrawOverlays(ctx) }.getOrDefault(false)
        } else {
            true
        }
    }

    fun requestOverlayPermissionIfNeeded(context: Context): Boolean {
        if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.SYSTEM_OVERLAY)) {
            Toast.makeText(
                context,
                "已在权限管理中关闭「跨应用全局悬浮窗（桌面歌词）」权限，请先在设置-权限管理中开启",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        if (canDrawSystemOverlays(context)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(
                    context,
                    "请开启「显示在其他应用上层」权限，即可在桌面与任意应用上方常驻显示桌面歌词",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        return false
    }

    fun toggleDesktopLyricWithPermissionCheck(context: Context) {
        if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.SYSTEM_OVERLAY)) {
            setFloatingCapsuleEnabled(false)
            Toast.makeText(
                context,
                "已在权限管理中关闭「跨应用全局悬浮窗（桌面歌词）」权限，请先前往设置-权限管理开启",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val nextEnabled = !_state.value.floatingCapsuleEnabled
        if (nextEnabled && !canDrawSystemOverlays(context)) {
            requestOverlayPermissionIfNeeded(context)
        }
        setFloatingCapsuleEnabled(nextEnabled)
        syncSystemOverlayWindow()
    }

    fun onHostResumed(context: Context) {
        appContext = context.applicationContext
        syncSystemOverlayWindow()
    }

    fun updatePlaybackSnapshot(
        song: Song?,
        lyricData: LyricData,
        currentLyricIndex: Int,
        currentPositionMs: Long,
        isPlaying: Boolean,
        playMode: PlayMode = playModeSnapshot,
        onTogglePlay: (() -> Unit)? = null,
        onPrevious: (() -> Unit)? = null,
        onNext: (() -> Unit)? = null,
        onCyclePlayMode: (() -> Unit)? = null
    ) {
        currentSongSnapshot = song
        currentLyricSnapshot = lyricData
        currentLineIndexSnapshot = currentLyricIndex
        currentPositionMsSnapshot = currentPositionMs
        lastPositionSyncRealtimeMs = android.os.SystemClock.elapsedRealtime()
        isPlayingSnapshot = isPlaying
        playModeSnapshot = playMode
        if (onTogglePlay != null) onTogglePlayCallback = onTogglePlay
        if (onPrevious != null) onPreviousCallback = onPrevious
        if (onNext != null) onNextCallback = onNext
        if (onCyclePlayMode != null) onCyclePlayModeCallback = onCyclePlayMode

        val st = _state.value
        if (st.floatingCapsuleEnabled) {
            mainHandler.post {
                syncSystemOverlayWindow()
                overlayView?.onPlaybackUpdated()
            }
        }
    }

    /**
     * 当 App 内修改了歌词高亮配色、字号、译文显示或播放模式时，单向同步至桌面歌词（不触发反向回环）
     */
    fun syncFromAppSettings(
        highlightTheme: LyricHighlightTheme? = null,
        inAppLyricFontSize: Float? = null,
        appLyricFontSize: Float? = inAppLyricFontSize,
        showTranslation: Boolean? = null,
        playMode: PlayMode? = null
    ) {
        if (playMode != null) {
            playModeSnapshot = playMode
        }
        val targetFontSize = inAppLyricFontSize ?: appLyricFontSize
        var current = _state.value
        var changed = false
        if (highlightTheme != null) {
            val mappedScheme = DesktopLyricColorScheme.fromHighlightTheme(highlightTheme)
            if (current.desktopColorScheme != mappedScheme) {
                current = current.copy(desktopColorScheme = mappedScheme)
                prefs?.edit()?.putString("desktop_color_scheme", mappedScheme.name)?.apply()
                changed = true
            }
        }
        if (targetFontSize != null) {
            // 将 App 内歌词字号 (18..34sp) 映射并同步至桌面歌词字号 (15..26sp)
            val mappedSize = (targetFontSize - 4f).coerceIn(15f, 26f)
            if (abs(current.desktopFontSize - mappedSize) >= 0.4f) {
                current = current.copy(desktopFontSize = mappedSize)
                prefs?.edit()?.putFloat("desktop_font_size", mappedSize)?.apply()
                changed = true
            }
        }
        if (showTranslation != null && current.desktopShowTranslation != showTranslation) {
            current = current.copy(desktopShowTranslation = showTranslation)
            prefs?.edit()?.putBoolean("desktop_show_translation", showTranslation)?.apply()
            changed = true
        }
        if (changed) {
            _state.value = current
        }
        mainHandler.post {
            overlayView?.requestLayout()
            overlayView?.invalidate()
        }
    }

    fun setFloatingCapsuleEnabled(enabled: Boolean) {
        val effectiveEnabled = enabled && AppPermissionManager.isPermissionEnabled(AppPermissionType.SYSTEM_OVERLAY)
        _state.value = _state.value.copy(floatingCapsuleEnabled = effectiveEnabled)
        prefs?.edit()?.putBoolean("floating_capsule_enabled", effectiveEnabled)?.apply()
        syncSystemOverlayWindow()
    }

    fun setDesktopLyricEnabled(enabled: Boolean) = setFloatingCapsuleEnabled(enabled)

    fun setFloatingCapsuleLocked(locked: Boolean) {
        _state.value = _state.value.copy(floatingCapsuleLocked = locked)
        prefs?.edit()?.putBoolean("floating_capsule_locked", locked)?.apply()
        mainHandler.post {
            applyOverlayLockFlags()
            overlayView?.invalidate()
        }
    }

    fun setDesktopColorScheme(scheme: DesktopLyricColorScheme, syncToApp: Boolean = true) {
        _state.value = _state.value.copy(desktopColorScheme = scheme)
        prefs?.edit()?.putString("desktop_color_scheme", scheme.name)?.apply()
        if (syncToApp) {
            onDesktopColorChangedCallback?.invoke(scheme.toHighlightTheme())
        }
        mainHandler.post { overlayView?.invalidate() }
    }

    fun setDesktopLineMode(mode: DesktopLyricLineMode) {
        _state.value = _state.value.copy(desktopLineMode = mode)
        prefs?.edit()?.putString("desktop_line_mode", mode.name)?.apply()
        mainHandler.post {
            overlayView?.requestLayout()
            overlayView?.invalidate()
        }
    }

    fun setDesktopFontSize(sizeSp: Float, syncToApp: Boolean = true) {
        val clamped = sizeSp.coerceIn(15f, 26f)
        _state.value = _state.value.copy(desktopFontSize = clamped)
        prefs?.edit()?.putFloat("desktop_font_size", clamped)?.apply()
        if (syncToApp) {
            onDesktopFontSizeChangedCallback?.invoke((clamped + 4f).coerceIn(18f, 34f))
        }
        mainHandler.post {
            overlayView?.requestLayout()
            overlayView?.invalidate()
        }
    }

    fun setDesktopWidthFraction(context: Context, fraction: Float) = setDesktopWidthFraction(fraction)

    fun setDesktopWidthFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0.68f, 0.96f)
        _state.value = _state.value.copy(desktopWidthFraction = clamped)
        prefs?.edit()?.putFloat("desktop_width_fraction", clamped)?.apply()
        mainHandler.post {
            updateSystemWindowWidth()
            overlayView?.requestLayout()
            overlayView?.invalidate()
        }
    }

    fun setDesktopBgAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0f, 0.96f)
        _state.value = _state.value.copy(desktopBgAlpha = clamped)
        prefs?.edit()?.putFloat("desktop_bg_alpha", clamped)?.apply()
        mainHandler.post { overlayView?.invalidate() }
    }

    fun setDesktopAlignment(alignment: Int) {
        val clamped = alignment.coerceIn(0, 1)
        _state.value = _state.value.copy(desktopAlignment = clamped)
        prefs?.edit()?.putInt("desktop_alignment", clamped)?.apply()
        mainHandler.post { overlayView?.invalidate() }
    }

    fun setDesktopShowTranslation(show: Boolean) {
        _state.value = _state.value.copy(desktopShowTranslation = show)
        prefs?.edit()?.putBoolean("desktop_show_translation", show)?.apply()
        mainHandler.post {
            overlayView?.requestLayout()
            overlayView?.invalidate()
        }
    }

    fun saveDesktopPosition(offsetX: Float, offsetY: Float) {
        // 水平方向始终锁定为 0f（全宽居中），仅持久化垂直高度 Y
        _state.value = _state.value.copy(savedOffsetX = 0f, savedOffsetY = offsetY)
        prefs?.edit()
            ?.putFloat("desktop_offset_x", 0f)
            ?.putFloat("desktop_offset_y", offsetY)
            ?.apply()
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
        ConceptMusicWidgetUpdater.updateState(
            context = context,
            songTitle = song.title,
            artist = song.artist,
            lyricLine = cleanText,
            isPlaying = isPlaying
        )
    }

    // =========================================================================
    // 桌面歌词系统级悬浮窗生命周期管理（全屏满宽 + 锁定 X=0 + 毛玻璃模糊）
    // =========================================================================

    private fun syncSystemOverlayWindow() {
        val ctx = appContext ?: return
        mainHandler.post {
            val st = _state.value
            val canOverlay = canDrawSystemOverlays(ctx)
            val shouldShow = st.floatingCapsuleEnabled && canOverlay && currentSongSnapshot != null
            if (!shouldShow) {
                removeSystemOverlayViews()
                if (_state.value.systemOverlayActive) {
                    _state.value = _state.value.copy(systemOverlayActive = false)
                }
                return@post
            }

            if (overlayView != null) {
                if (!_state.value.systemOverlayActive) {
                    _state.value = _state.value.copy(systemOverlayActive = true)
                }
                applyOverlayLockFlags()
                overlayView?.invalidate()
                return@post
            }

            runCatching {
                val wm = (ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return@runCatching
                windowManager = wm
                val dm = ctx.resources.displayMetrics
                val screenW = dm.widthPixels.coerceAtLeast(720)
                val screenH = dm.heightPixels.coerceAtLeast(1280)

                val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

                val effectiveFlags = if (st.floatingCapsuleLocked) {
                    baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    baseFlags
                }

                val initialY = st.savedOffsetY.roundToInt().coerceIn(48, (screenH - 260).coerceAtLeast(100))

                // 横向自适应占满屏幕宽度，X 恒定为 0，禁止左右偏移
                val lp = WindowManager.LayoutParams(
                    screenW,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    effectiveFlags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    x = 0
                    y = initialY
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    }
                }

                val view = NativeDesktopLyricView(
                    context = ctx,
                    onVerticalDragDelta = { dy, isFinished ->
                        val curLp = overlayParams ?: return@NativeDesktopLyricView
                        val liveDm = ctx.resources.displayMetrics
                        val liveW = liveDm.widthPixels.coerceAtLeast(720)
                        val liveH = liveDm.heightPixels.coerceAtLeast(720)
                        curLp.width = liveW
                        curLp.x = 0
                        curLp.y = (curLp.y + dy).roundToInt().coerceIn(32, (liveH - viewHeightOrDefault()).coerceAtLeast(80))
                        runCatching { wm.updateViewLayout(overlayView, curLp) }
                        if (isFinished) {
                            saveDesktopPosition(0f, curLp.y.toFloat())
                        }
                        syncUnlockPillPosition()
                    },
                    onRequestWindowLayoutUpdate = {
                        val curLp = overlayParams
                        val curView = overlayView
                        if (curLp != null && curView != null) {
                            runCatching { wm.updateViewLayout(curView, curLp) }
                        }
                    }
                )

                wm.addView(view, lp)
                overlayView = view
                overlayParams = lp
                _state.value = _state.value.copy(systemOverlayActive = true)
                applyOverlayLockFlags()
            }.onFailure {
                removeSystemOverlayViews()
                _state.value = _state.value.copy(systemOverlayActive = false)
            }
        }
    }

    private fun viewHeightOrDefault(): Int = overlayView?.height?.takeIf { it > 0 } ?: 220

    private fun updateSystemWindowWidth() {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val lp = overlayParams ?: return
        val screenW = ctx.resources.displayMetrics.widthPixels.coerceAtLeast(720)
        lp.width = screenW
        lp.x = 0
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun applyOverlayLockFlags() {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val lp = overlayParams ?: return
        val locked = _state.value.floatingCapsuleLocked

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        lp.flags = if (locked) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags
        }
        runCatching { wm.updateViewLayout(view, lp) }

        if (locked) {
            ensureUnlockPillMounted(ctx, wm)
        } else {
            removeUnlockPillView()
        }
    }

    private fun ensureUnlockPillMounted(ctx: Context, wm: WindowManager) {
        if (unlockPillView != null) {
            syncUnlockPillPosition()
            return
        }
        runCatching {
            val dm = ctx.resources.displayMetrics
            val density = dm.density
            val pillSize = (36f * density).roundToInt()
            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val mainLp = overlayParams
            val pillLp = WindowManager.LayoutParams(
                pillSize,
                pillSize,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (16f * density).roundToInt()
                y = (mainLp?.y ?: 140) + (8f * density).roundToInt()
            }
            val pill = NativeUnlockPillView(ctx) {
                setFloatingCapsuleLocked(false)
                overlayView?.expandHudOnUnlock()
                Toast.makeText(ctx, "桌面歌词已解锁", Toast.LENGTH_SHORT).show()
            }
            wm.addView(pill, pillLp)
            unlockPillView = pill
            unlockPillParams = pillLp
        }
    }

    private fun syncUnlockPillPosition() {
        val wm = windowManager ?: return
        val pill = unlockPillView ?: return
        val pillLp = unlockPillParams ?: return
        val mainLp = overlayParams ?: return
        pillLp.y = mainLp.y + 12
        runCatching { wm.updateViewLayout(pill, pillLp) }
    }

    private fun removeUnlockPillView() {
        val wm = windowManager
        unlockPillView?.let { v ->
            runCatching { wm?.removeViewImmediate(v) }
        }
        unlockPillView = null
        unlockPillParams = null
    }

    private fun removeSystemOverlayViews() {
        removeUnlockPillView()
        val wm = windowManager
        overlayView?.let { v ->
            runCatching { wm?.removeViewImmediate(v) }
        }
        overlayView = null
        overlayParams = null
    }
}

/**
 * 锁定状态下的圆润无框悬浮解锁小锁图标（位于左上角原锁定键位置，点击即可解锁）
 */
private class NativeUnlockPillView(
    context: Context,
    private val onUnlockClick: () -> Unit
) : View(context) {
    private val d = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x780D1524
        style = Paint.Style.FILL
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD166.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.1f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD166.toInt()
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = (width.coerceAtMost(height) / 2f) - 1f * d
        canvas.drawCircle(cx, cy, r, bgPaint)

        val bw = 11f * d
        val bh = 8.5f * d
        rect.set(cx - bw / 2f, cy - 0.5f * d, cx + bw / 2f, cy - 0.5f * d + bh)
        canvas.drawRoundRect(rect, 3f * d, 3f * d, bodyPaint)
        rect.set(cx - 3.6f * d, cy - 6.8f * d, cx + 3.6f * d, cy + 1f * d)
        canvas.drawArc(rect, 180f, 180f, false, iconPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            onUnlockClick()
            return true
        }
        return true
    }
}

/**
 * 全新重构的原生硬件加速系统级桌面歌词视图 (`NativeDesktopLyricView`)：
 * 1. 常驻态 0 背景框，仅渲染带柔光立体阴影的 KRC 逐字流光 + 弹性上浮歌词；
 * 2. 顶部预留恒定高度 `topHudHeightPx`，唤醒毛玻璃框时上方显现 7 个圆润饱满无框按钮，歌词垂直位置绝不下移；
 * 3. 歌词区域恒定保持 3 行高度空间，短句两行「插空居中」显示，长句自然折为三行；
 * 4. 点击设置按钮后，毛玻璃框底部通过属性动画平滑向下延伸，展示「歌词颜色」与「歌词字号」两项设置。
 */
private class NativeDesktopLyricView(
    context: Context,
    private val onVerticalDragDelta: (dy: Float, isFinished: Boolean) -> Unit,
    private val onRequestWindowLayoutUpdate: () -> Unit
) : View(context) {
    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var showHud = false
    private var showSettingsPanel = false

    // 平滑动画进度 (0f..1f)
    private var hudRevealProgress = 0f
    private var settingsExpandProgress = 0f

    private val hudAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 220L
        interpolator = DecelerateInterpolator()
        addUpdateListener { anim ->
            hudRevealProgress = anim.animatedValue as Float
            invalidate()
        }
    }

    private val settingsAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 260L
        interpolator = DecelerateInterpolator()
        addUpdateListener { anim ->
            settingsExpandProgress = anim.animatedValue as Float
            requestLayout()
            onRequestWindowLayoutUpdate()
            invalidate()
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawY = 0f
    private var isDragging = false
    private var isDraggingFontSlider = false

    private val glassBasePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.7f * d
    }
    private val mainTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        pathEffect = CornerPathEffect(2.6f * d)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt()
        textSize = 12f * sp
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glassRect = RectF()
    private val tempRect = RectF()
    private val tempPath = Path()
    private val hitRegions = mutableListOf<Pair<RectF, () -> Unit>>()
    private var fontSliderTrackRect = RectF()

    // 固定几何尺寸参数：顶部控制栏预留高度恒定不变，确保点击唤醒控件时歌词绝不下移
    private val topHudHeightPx: Float
        get() = 46f * d

    private val settingsPanelFullHeightPx: Float
        get() = 86f * d

    private fun singleRowSlotHeightPx(fontSizeSp: Float): Float {
        return (fontSizeSp * sp * 1.44f).coerceAtLeast(24f * d)
    }

    private fun fixedThreeLineZoneHeightPx(fontSizeSp: Float): Float {
        return singleRowSlotHeightPx(fontSizeSp) * 3f + 8f * d
    }

    fun expandHudOnUnlock() {
        setHudVisible(true)
    }

    private fun setHudVisible(visible: Boolean) {
        if (showHud == visible) return
        showHud = visible
        if (!visible && showSettingsPanel) {
            setSettingsPanelVisible(false)
        }
        hudAnimator.cancel()
        hudAnimator.setFloatValues(hudRevealProgress, if (visible) 1f else 0f)
        hudAnimator.start()
    }

    private fun setSettingsPanelVisible(visible: Boolean) {
        if (showSettingsPanel == visible) return
        showSettingsPanel = visible
        settingsAnimator.cancel()
        settingsAnimator.setFloatValues(settingsExpandProgress, if (visible) 1f else 0f)
        settingsAnimator.start()
    }

    private var lastLayoutFontSize = -1f
    private var lastLayoutLocked: Boolean? = null

    fun onPlaybackUpdated() {
        val st = SystemLyricBridge.state.value
        if (st.desktopFontSize != lastLayoutFontSize || st.floatingCapsuleLocked != lastLayoutLocked) {
            lastLayoutFontSize = st.desktopFontSize
            lastLayoutLocked = st.floatingCapsuleLocked
            requestLayout()
            onRequestWindowLayoutUpdate()
        }
        postInvalidateOnAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val st = SystemLyricBridge.state.value
        val screenW = resources.displayMetrics.widthPixels.coerceAtLeast((320 * d).roundToInt())
        val w = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: screenW
        val lyricZoneH = fixedThreeLineZoneHeightPx(st.desktopFontSize)
        val extraSettingsH = if (st.floatingCapsuleLocked) 0f else (settingsPanelFullHeightPx * settingsExpandProgress)
        val totalH = (topHudHeightPx + lyricZoneH + extraSettingsH + 10f * d).roundToInt()
        setMeasuredDimension(w, totalH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        hitRegions.clear()
        fontSliderTrackRect.setEmpty()

        val st = SystemLyricBridge.state.value
        val scheme = st.desktopColorScheme
        val startColor = scheme.playedStartHex.toInt()
        val endColor = scheme.playedEndHex.toInt()
        val unplayedColor = (scheme.unplayedHex.toInt() and 0x00FFFFFF) or (0xE8 shl 24)
        val strokeColor = scheme.strokeHex.toInt()

        val w = width.toFloat()
        val sideMargin = 10f * d
        val lyricZoneTop = topHudHeightPx
        val singleSlotH = singleRowSlotHeightPx(st.desktopFontSize)
        val lyricZoneH = fixedThreeLineZoneHeightPx(st.desktopFontSize)
        val lyricZoneBottom = lyricZoneTop + lyricZoneH

        val effectiveHudAlpha = if (st.floatingCapsuleLocked) 0f else hudRevealProgress.coerceIn(0f, 1f)
        val currentSettingsH = if (st.floatingCapsuleLocked) 0f else (settingsPanelFullHeightPx * settingsExpandProgress)

        // 1. 仅在点击唤醒控制态时绘制通透毛玻璃背景框（常驻态 effectiveHudAlpha == 0f，完全无框纯歌词）
        if (effectiveHudAlpha > 0.01f) {
            val panelTop = 2f * d
            val panelBottom = lyricZoneBottom + currentSettingsH + 4f * d
            glassRect.set(sideMargin, panelTop, w - sideMargin, panelBottom)
            val cornerR = 24f * d

            val baseAlphaTop = (effectiveHudAlpha * 214).roundToInt().coerceIn(0, 255)
            val baseAlphaBot = (effectiveHudAlpha * 228).roundToInt().coerceIn(0, 255)
            glassBasePaint.shader = LinearGradient(
                0f, panelTop, 0f, panelBottom,
                (baseAlphaTop shl 24) or 0x121B2E,
                (baseAlphaBot shl 24) or 0x090E1A,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(glassRect, cornerR, cornerR, glassBasePaint)

            // 顶部主题色柔光漫反射层，营造通透毛玻璃景深感
            val glowAlpha = (effectiveHudAlpha * 44).roundToInt().coerceIn(0, 255)
            glassAccentPaint.shader = LinearGradient(
                glassRect.left, panelTop, glassRect.right, panelTop + topHudHeightPx * 1.6f,
                intArrayOf(
                    (glowAlpha shl 24) or (startColor and 0x00FFFFFF),
                    (glowAlpha / 2 shl 24) or (endColor and 0x00FFFFFF),
                    0x00000000
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(glassRect, cornerR, cornerR, glassAccentPaint)

            // 1px 细腻高光玻璃边缘
            val rimAlphaWhite = (effectiveHudAlpha * 56).roundToInt().coerceIn(0, 255)
            val rimAlphaAccent = (effectiveHudAlpha * 68).roundToInt().coerceIn(0, 255)
            glassRimPaint.shader = LinearGradient(
                glassRect.left, panelTop, glassRect.right, panelBottom,
                (rimAlphaWhite shl 24) or 0xFFFFFF,
                (rimAlphaAccent shl 24) or (startColor and 0x00FFFFFF),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(glassRect, cornerR, cornerR, glassRimPaint)

            // 顶部 7 个圆润饱满无框控件按钮（位于歌词正上方 0..lyricZoneTop，歌词本身绝不下移）
            drawTopPlumpControlBar(
                canvas = canvas,
                leftBound = glassRect.left,
                rightBound = glassRect.right,
                topY = panelTop,
                barHeight = topHudHeightPx - panelTop,
                alpha = effectiveHudAlpha,
                accentColor = startColor
            )

            // 顶栏与歌词区之间的极细柔光分割线
            dividerPaint.color = ((effectiveHudAlpha * 28).roundToInt() shl 24) or 0xFFFFFF
            canvas.drawLine(
                glassRect.left + 18f * d,
                lyricZoneTop,
                glassRect.right - 18f * d,
                lyricZoneTop,
                dividerPaint
            )
        }

        // 2. 实时插值播放进度（60/120fps 逐字流光 + 单字弹性上浮 + 前奏呼吸波点）
        val basePos = SystemLyricBridge.currentPositionMsSnapshot
        val elapsed = if (SystemLyricBridge.isPlayingSnapshot && SystemLyricBridge.lastPositionSyncRealtimeMs > 0L) {
            (android.os.SystemClock.elapsedRealtime() - SystemLyricBridge.lastPositionSyncRealtimeMs).coerceIn(0L, 260L)
        } else 0L
        val livePosMs = basePos + (elapsed * SystemLyricBridge.playbackSpeedSnapshot).toLong()

        val lyricData = SystemLyricBridge.currentLyricSnapshot
        val lines = lyricData.lines
        val safeIdx = SystemLyricBridge.currentLineIndexSnapshot.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        val currentLine = lines.getOrNull(safeIdx)
        val nextLine = lines.getOrNull(safeIdx + 1)
        val song = SystemLyricBridge.currentSongSnapshot
        val fallbackTitle = if (song != null) "${song.title} - ${song.artist}" else "微风音乐 · 酷狗概念版"

        val mainFontSizePx = st.desktopFontSize * sp
        val subFontSizePx = (st.desktopFontSize * 0.82f).coerceAtLeast(13f) * sp
        val availableTextWidth = (w - sideMargin * 2f - 28f * d).coerceAtLeast(160f * d)
        val maxVisualUnits = (availableTextWidth / mainFontSizePx).coerceAtLeast(10f)

        val layoutPlan = resolveDesktopLyricLayoutPlan(
            currentLine = currentLine,
            nextLine = nextLine,
            fallbackTitle = fallbackTitle,
            showTranslation = st.desktopShowTranslation,
            maxVisualUnitsPerLine = maxVisualUnits,
            singleRowSlotHeightPx = singleSlotH
        )

        // 绘制三行恒定高度容器内的歌词行（短句两行插空垂直居中，长句自然三行）
        for (row in layoutPlan.rows) {
            val rowCenterY = lyricZoneTop + lyricZoneH * row.verticalCenterFraction
            val fontPx = if (row.isSecondaryRow) subFontSizePx else mainFontSizePx
            val baselineY = rowCenterY + fontPx * 0.34f
            drawUnifiedAnimatedLyricRow(
                canvas = canvas,
                row = row,
                posMs = if (row.isActiveKaraoke) livePosMs else -1L,
                baselineY = baselineY,
                fontSizePx = fontPx,
                maxWidthPx = availableTextWidth,
                startColor = startColor,
                endColor = endColor,
                unplayedColor = unplayedColor,
                shadowColor = strokeColor
            )
        }

        // 前奏 / 长间奏三点流光呼吸波点指示（与 App 内歌词动效统一）
        val interludeProgress = lyricData.getInterludeProgressAt(livePosMs)
        if (interludeProgress >= 0f) {
            drawInterludeBreathingDots(
                canvas = canvas,
                centerX = w / 2f,
                centerY = lyricZoneTop + 8f * d,
                progress = interludeProgress,
                accentColor = startColor
            )
        }

        // 3. 底部平滑向下延伸的设置面板（仅含「歌词颜色」与「歌词字号」两项）
        if (effectiveHudAlpha > 0.01f && settingsExpandProgress > 0.02f) {
            val settingsTop = lyricZoneBottom
            val settingsAlpha = (effectiveHudAlpha * settingsExpandProgress).coerceIn(0f, 1f)
            canvas.save()
            canvas.clipRect(
                glassRect.left,
                settingsTop,
                glassRect.right,
                settingsTop + currentSettingsH + 4f * d
            )
            drawExpandableSettingsPanel(
                canvas = canvas,
                leftBound = glassRect.left,
                rightBound = glassRect.right,
                topY = settingsTop,
                alpha = settingsAlpha,
                accentColor = startColor,
                state = st
            )
            canvas.restore()
        }

        // 播放中按 60fps 持续刷新逐字扫光与弹性跃动动画
        if (SystemLyricBridge.isPlayingSnapshot && currentLine != null) {
            postInvalidateDelayed(16L)
        }
    }

    /**
     * 绘制与 App 内动效统一的 KRC 逐字流光 + 唱诵单字正弦波弹性上浮行
     */
    private fun drawUnifiedAnimatedLyricRow(
        canvas: Canvas,
        row: DesktopLyricRenderRow,
        posMs: Long,
        baselineY: Float,
        fontSizePx: Float,
        maxWidthPx: Float,
        startColor: Int,
        endColor: Int,
        unplayedColor: Int,
        shadowColor: Int
    ) {
        mainTextPaint.textSize = fontSizePx
        mainTextPaint.shader = null
        mainTextPaint.setShadowLayer(5.2f * d, 0f, 1.8f * d, shadowColor)

        // 若该行包含逐字时间戳且处于激活唱诵状态，逐词绘制流光渐变与弹性上浮跃动
        if (row.isActiveKaraoke && row.words.isNotEmpty() && posMs >= 0L) {
            val fullLineText = row.words.joinToString("") { it.text }
            val totalW = mainTextPaint.measureText(fullLineText).coerceAtLeast(1f)
            if (totalW <= maxWidthPx * 1.04f) {
                var cursorX = ((width - totalW) / 2f).coerceAtLeast(16f * d)
                for (word in row.words) {
                    val wordW = mainTextPaint.measureText(word.text)
                    val wordProgress = when {
                        word.durationMs > 0L -> ((posMs - word.startTimeMs).toFloat() / word.durationMs.toFloat()).coerceIn(0f, 1f)
                        posMs >= word.startTimeMs -> 1f
                        else -> 0f
                    }
                    val isSinging = wordProgress in 0.01f..0.99f
                    val wave = if (isSinging) sin(wordProgress * PI.toFloat()) else 0f
                    val floatOffsetY = -2.4f * d * wave

                    when {
                        wordProgress <= 0f -> {
                            mainTextPaint.shader = null
                            mainTextPaint.color = unplayedColor
                            mainTextPaint.setShadowLayer(5.0f * d, 0f, 1.6f * d, shadowColor)
                        }
                        wordProgress >= 1f -> {
                            mainTextPaint.shader = null
                            mainTextPaint.color = startColor
                            mainTextPaint.setShadowLayer(5.5f * d, 0f, 1.6f * d, shadowColor)
                        }
                        else -> {
                            val lStop = (wordProgress - 0.16f).coerceAtLeast(0f)
                            val rStop = (wordProgress + 0.16f).coerceAtMost(1f)
                            mainTextPaint.shader = LinearGradient(
                                cursorX, baselineY, cursorX + wordW.coerceAtLeast(1f), baselineY,
                                intArrayOf(startColor, endColor, unplayedColor, unplayedColor),
                                floatArrayOf(0f, lStop, rStop, 1f),
                                Shader.TileMode.CLAMP
                            )
                            if (word.durationMs >= 700L) {
                                mainTextPaint.setShadowLayer(8f * d * wave + 4f * d, 0f, 1f * d, startColor)
                            } else {
                                mainTextPaint.setShadowLayer(5.2f * d, 0f, 1.6f * d, shadowColor)
                            }
                        }
                    }
                    canvas.drawText(word.text, cursorX, baselineY + floatOffsetY, mainTextPaint)
                    cursorX += wordW
                }
                mainTextPaint.shader = null
                return
            }
        }

        // 整行平滑渐变扫光回退渲染
        val displayText = ellipsizeToWidth(row.text, mainTextPaint, maxWidthPx)
        val textW = mainTextPaint.measureText(displayText).coerceAtLeast(1f)
        val startX = ((width - textW) / 2f).coerceAtLeast(16f * d)

        if (row.isActiveKaraoke && posMs >= 0L && row.lineDurationMs > 0L) {
            val clampP = ((posMs - row.lineStartTimeMs).toFloat() / row.lineDurationMs.toFloat()).coerceIn(0f, 1f)
            if (clampP <= 0.005f) {
                mainTextPaint.color = unplayedColor
            } else if (clampP >= 0.995f) {
                mainTextPaint.color = startColor
            } else {
                val edgeLeft = (clampP - 0.06f).coerceAtLeast(0f)
                val edgeRight = (clampP + 0.06f).coerceAtMost(1f)
                mainTextPaint.shader = LinearGradient(
                    startX, baselineY, startX + textW, baselineY,
                    intArrayOf(startColor, endColor, unplayedColor, unplayedColor),
                    floatArrayOf(0f, edgeLeft, edgeRight, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        } else {
            val secondaryAlpha = if (row.isSecondaryRow) 0xCC else 0xF2
            mainTextPaint.color = if (row.isActiveKaraoke) {
                startColor
            } else {
                (unplayedColor and 0x00FFFFFF) or (secondaryAlpha shl 24)
            }
        }

        canvas.drawText(displayText, startX, baselineY, mainTextPaint)
        mainTextPaint.shader = null
    }

    private fun drawInterludeBreathingDots(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        progress: Float,
        accentColor: Int
    ) {
        val spacing = 10f * d
        for (i in 0..2) {
            val dotPhase = ((progress * 3f) - i * 0.35f).coerceIn(0f, 1f)
            val pulse = sin(dotPhase * PI.toFloat()).coerceIn(0.25f, 1f)
            val alpha = (pulse * 230).roundToInt().coerceIn(50, 240)
            swatchPaint.color = (alpha shl 24) or (accentColor and 0x00FFFFFF)
            val radius = (2.4f + 1.2f * pulse) * d
            canvas.drawCircle(centerX + (i - 1) * spacing, centerY, radius, swatchPaint)
        }
    }

    private fun ellipsizeToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 4 && paint.measureText(text, 0, end) + 18f > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    /**
     * 顶部 7 键无边框、圆润饱满矢量控制栏：
     * 从左到右依次为：
     * - 左上角：锁定 (Lock)、播放顺序 (PlayMode)
     * - 正中央：上一首 (Prev)、暂停/播放 (Play/Pause)、下一首 (Next)
     * - 右上角：设置 (Settings)、关闭 (X)
     */
    private fun drawTopPlumpControlBar(
        canvas: Canvas,
        leftBound: Float,
        rightBound: Float,
        topY: Float,
        barHeight: Float,
        alpha: Float,
        accentColor: Int
    ) {
        val cy = topY + barHeight / 2f
        val centerX = (leftBound + rightBound) / 2f
        val whiteColor = ((alpha * 242).roundToInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
        val activeColor = ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24) or (accentColor and 0x00FFFFFF)
        val goldColor = ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24) or 0xFFD166

        val lockCx = leftBound + 26f * d
        val modeCx = leftBound + 62f * d
        val prevCx = centerX - 44f * d
        val playCx = centerX
        val nextCx = centerX + 44f * d
        val settingsCx = rightBound - 62f * d
        val closeCx = rightBound - 26f * d
        val hitR = 18f * d

        // 1. 锁定按钮（左上角第 1 个：圆润饱满挂锁，无边框）
        drawPlumpLockIcon(canvas, lockCx, cy, 18f * d, goldColor)
        if (alpha > 0.5f) {
            hitRegions.add(RectF(lockCx - hitR, cy - hitR, lockCx + hitR, cy + hitR) to {
                setSettingsPanelVisible(false)
                setHudVisible(false)
                SystemLyricBridge.setFloatingCapsuleLocked(true)
                Toast.makeText(context, "桌面歌词已锁定，点击左上角小锁图标即可解锁", Toast.LENGTH_SHORT).show()
            })
        }

        // 2. 播放顺序按钮（左上角第 2 个：列表循环 / 单曲循环 / 随机播放，圆润双弧箭头，无边框）
        val modeColor = if (SystemLyricBridge.playModeSnapshot == PlayMode.REPEAT_ALL) whiteColor else activeColor
        drawPlumpPlayModeIcon(canvas, modeCx, cy, 19f * d, SystemLyricBridge.playModeSnapshot, modeColor)
        if (alpha > 0.5f) {
            hitRegions.add(RectF(modeCx - hitR, cy - hitR, modeCx + hitR, cy + hitR) to {
                val nextMode = when (SystemLyricBridge.playModeSnapshot) {
                    PlayMode.REPEAT_ALL -> PlayMode.REPEAT_ONE
                    PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
                    PlayMode.SHUFFLE -> PlayMode.REPEAT_ALL
                }
                SystemLyricBridge.playModeSnapshot = nextMode
                SystemLyricBridge.onCyclePlayModeCallback?.invoke()
                val modeLabel = when (nextMode) {
                    PlayMode.REPEAT_ALL -> "列表循环"
                    PlayMode.REPEAT_ONE -> "单曲循环"
                    PlayMode.SHUFFLE -> "随机播放"
                }
                Toast.makeText(context, "播放顺序：$modeLabel", Toast.LENGTH_SHORT).show()
                invalidate()
            })
        }

        // 3. 上一首（居中左：圆润大圆角三角 + 圆角竖柱，无边框）
        drawPlumpSkipIcon(canvas, prevCx, cy, 19f * d, isNext = false, color = whiteColor)
        if (alpha > 0.5f) {
            hitRegions.add(RectF(prevCx - hitR, cy - hitR, prevCx + hitR, cy + hitR) to {
                SystemLyricBridge.onPreviousCallback?.invoke()
            })
        }

        // 4. 暂停/播放（正中：饱满圆润大号播放三角 / 双圆角胶囊暂停柱，无边框）
        drawPlumpPlayPauseIcon(
            canvas = canvas,
            cx = playCx,
            cy = cy,
            sizePx = 23f * d,
            isPlaying = SystemLyricBridge.isPlayingSnapshot,
            color = activeColor
        )
        if (alpha > 0.5f) {
            hitRegions.add(RectF(playCx - hitR * 1.15f, cy - hitR, playCx + hitR * 1.15f, cy + hitR) to {
                SystemLyricBridge.onTogglePlayCallback?.invoke()
                invalidate()
            })
        }

        // 5. 下一首（居中右：圆润大圆角三角 + 圆角竖柱，无边框）
        drawPlumpSkipIcon(canvas, nextCx, cy, 19f * d, isNext = true, color = whiteColor)
        if (alpha > 0.5f) {
            hitRegions.add(RectF(nextCx - hitR, cy - hitR, nextCx + hitR, cy + hitR) to {
                SystemLyricBridge.onNextCallback?.invoke()
            })
        }

        // 6. 设置（右上角左：圆润饱满双轨圆头滑块图标，展开设置时高亮主题色，无边框）
        val settingsColor = if (showSettingsPanel) activeColor else whiteColor
        drawPlumpSettingsIcon(canvas, settingsCx, cy, 19f * d, settingsColor, activeColor)
        if (alpha > 0.5f) {
            hitRegions.add(RectF(settingsCx - hitR, cy - hitR, settingsCx + hitR, cy + hitR) to {
                setSettingsPanelVisible(!showSettingsPanel)
            })
        }

        // 7. 关闭 X（右上角右：圆润饱满粗圆头交叉图标，无边框）
        drawPlumpCloseIcon(canvas, closeCx, cy, 16f * d, whiteColor)
        if (alpha > 0.5f) {
            hitRegions.add(RectF(closeCx - hitR, cy - hitR, closeCx + hitR, cy + hitR) to {
                setSettingsPanelVisible(false)
                setHudVisible(false)
                SystemLyricBridge.setFloatingCapsuleEnabled(false)
            })
        }
    }

    private fun drawPlumpLockIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val bw = size * 0.66f
        val bh = size * 0.50f
        iconFillPaint.color = color
        tempRect.set(cx - bw / 2f, cy - size * 0.04f, cx + bw / 2f, cy - size * 0.04f + bh)
        canvas.drawRoundRect(tempRect, 3.2f * d, 3.2f * d, iconFillPaint)

        iconStrokePaint.color = color
        iconStrokePaint.strokeWidth = 2.2f * d
        val shackleW = size * 0.42f
        tempRect.set(cx - shackleW / 2f, cy - size * 0.44f, cx + shackleW / 2f, cy + size * 0.06f)
        canvas.drawArc(tempRect, 180f, 180f, false, iconStrokePaint)
    }

    private fun drawPlumpPlayModeIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        mode: PlayMode,
        color: Int
    ) {
        iconStrokePaint.color = color
        iconStrokePaint.strokeWidth = 2.2f * d
        val r = size * 0.38f
        when (mode) {
            PlayMode.REPEAT_ALL, PlayMode.REPEAT_ONE -> {
                tempRect.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawArc(tempRect, -155f, 130f, false, iconStrokePaint)
                canvas.drawArc(tempRect, 25f, 130f, false, iconStrokePaint)

                val tipTopX = cx + r * 0.78f
                val tipTopY = cy - r * 0.52f
                canvas.drawLine(tipTopX, tipTopY, tipTopX - 3.6f * d, tipTopY - 0.8f * d, iconStrokePaint)
                canvas.drawLine(tipTopX, tipTopY, tipTopX - 0.8f * d, tipTopY + 3.6f * d, iconStrokePaint)

                val tipBotX = cx - r * 0.78f
                val tipBotY = cy + r * 0.52f
                canvas.drawLine(tipBotX, tipBotY, tipBotX + 3.6f * d, tipBotY + 0.8f * d, iconStrokePaint)
                canvas.drawLine(tipBotX, tipBotY, tipBotX + 0.8f * d, tipBotY - 3.6f * d, iconStrokePaint)

                if (mode == PlayMode.REPEAT_ONE) {
                    canvas.drawLine(cx, cy - r * 0.42f, cx, cy + r * 0.42f, iconStrokePaint)
                }
            }
            PlayMode.SHUFFLE -> {
                val leftX = cx - r
                val rightX = cx + r
                val topY = cy - r * 0.55f
                val botY = cy + r * 0.55f
                tempPath.reset()
                tempPath.moveTo(leftX, topY)
                tempPath.cubicTo(cx - r * 0.1f, topY, cx + r * 0.1f, botY, rightX, botY)
                canvas.drawPath(tempPath, iconStrokePaint)

                tempPath.reset()
                tempPath.moveTo(leftX, botY)
                tempPath.cubicTo(cx - r * 0.1f, botY, cx + r * 0.1f, topY, rightX, topY)
                canvas.drawPath(tempPath, iconStrokePaint)

                canvas.drawLine(rightX, topY, rightX - 3.2f * d, topY - 2.6f * d, iconStrokePaint)
                canvas.drawLine(rightX, topY, rightX - 3.2f * d, topY + 2.6f * d, iconStrokePaint)
                canvas.drawLine(rightX, botY, rightX - 3.2f * d, botY - 2.6f * d, iconStrokePaint)
                canvas.drawLine(rightX, botY, rightX - 3.2f * d, botY + 2.6f * d, iconStrokePaint)
            }
        }
    }

    private fun drawPlumpSkipIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        isNext: Boolean,
        color: Int
    ) {
        iconFillPaint.color = color
        val barW = size * 0.16f
        val barH = size * 0.66f
        val triH = size * 0.68f
        val triW = size * 0.54f

        if (isNext) {
            tempPath.reset()
            tempPath.moveTo(cx - size * 0.34f, cy - triH / 2f)
            tempPath.lineTo(cx - size * 0.34f + triW, cy)
            tempPath.lineTo(cx - size * 0.34f, cy + triH / 2f)
            tempPath.close()
            canvas.drawPath(tempPath, iconFillPaint)

            tempRect.set(cx + size * 0.22f, cy - barH / 2f, cx + size * 0.22f + barW, cy + barH / 2f)
            canvas.drawRoundRect(tempRect, barW / 2f, barW / 2f, iconFillPaint)
        } else {
            tempPath.reset()
            tempPath.moveTo(cx + size * 0.34f, cy - triH / 2f)
            tempPath.lineTo(cx + size * 0.34f - triW, cy)
            tempPath.lineTo(cx + size * 0.34f, cy + triH / 2f)
            tempPath.close()
            canvas.drawPath(tempPath, iconFillPaint)

            tempRect.set(cx - size * 0.22f - barW, cy - barH / 2f, cx - size * 0.22f, cy + barH / 2f)
            canvas.drawRoundRect(tempRect, barW / 2f, barW / 2f, iconFillPaint)
        }
    }

    private fun drawPlumpPlayPauseIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sizePx: Float,
        isPlaying: Boolean,
        color: Int
    ) {
        iconFillPaint.color = color
        if (isPlaying) {
            val barW = sizePx * 0.24f
            val barH = sizePx * 0.72f
            val gap = sizePx * 0.18f
            tempRect.set(cx - gap / 2f - barW, cy - barH / 2f, cx - gap / 2f, cy + barH / 2f)
            canvas.drawRoundRect(tempRect, barW / 2f, barW / 2f, iconFillPaint)
            tempRect.set(cx + gap / 2f, cy - barH / 2f, cx + gap / 2f + barW, cy + barH / 2f)
            canvas.drawRoundRect(tempRect, barW / 2f, barW / 2f, iconFillPaint)
        } else {
            val triW = sizePx * 0.68f
            val triH = sizePx * 0.76f
            val left = cx - triW * 0.42f
            tempPath.reset()
            tempPath.moveTo(left, cy - triH / 2f)
            tempPath.lineTo(left + triW, cy)
            tempPath.lineTo(left, cy + triH / 2f)
            tempPath.close()
            canvas.drawPath(tempPath, iconFillPaint)
        }
    }

    private fun drawPlumpSettingsIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        color: Int,
        knobAccentColor: Int
    ) {
        iconStrokePaint.color = color
        iconStrokePaint.strokeWidth = 2.2f * d
        val halfW = size * 0.40f
        val topY = cy - size * 0.20f
        val botY = cy + size * 0.20f
        canvas.drawLine(cx - halfW, topY, cx + halfW, topY, iconStrokePaint)
        canvas.drawLine(cx - halfW, botY, cx + halfW, botY, iconStrokePaint)

        swatchPaint.color = knobAccentColor
        canvas.drawCircle(cx + halfW * 0.35f, topY, 3.1f * d, swatchPaint)
        swatchPaint.color = color
        canvas.drawCircle(cx - halfW * 0.35f, botY, 3.1f * d, swatchPaint)
    }

    private fun drawPlumpCloseIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        iconStrokePaint.color = color
        iconStrokePaint.strokeWidth = 2.3f * d
        val r = size * 0.34f
        canvas.drawLine(cx - r, cy - r, cx + r, cy + r, iconStrokePaint)
        canvas.drawLine(cx + r, cy - r, cx - r, cy + r, iconStrokePaint)
    }

    /**
     * 底部平滑展开设置面板（仅保留「歌词颜色」与「歌词字号」两个设置项）
     */
    private fun drawExpandableSettingsPanel(
        canvas: Canvas,
        leftBound: Float,
        rightBound: Float,
        topY: Float,
        alpha: Float,
        accentColor: Int,
        state: SystemLyricState
    ) {
        val alphaInt = (alpha * 255).roundToInt().coerceIn(0, 255)
        dividerPaint.color = ((alpha * 32).roundToInt() shl 24) or 0xFFFFFF
        canvas.drawLine(leftBound + 18f * d, topY, rightBound - 18f * d, topY, dividerPaint)

        val padX = leftBound + 18f * d
        val contentRight = rightBound - 18f * d
        labelPaint.color = ((alpha * 215).roundToInt() shl 24) or 0xFFFFFF
        labelPaint.textSize = 11.5f * sp

        // 第一行：歌词颜色（圆润饱满色球）
        val row1Cy = topY + 23f * d
        canvas.drawText("颜色", padX, row1Cy + 4f * d, labelPaint)

        val schemes = DesktopLyricColorScheme.entries
        val swatchStartLeft = padX + 42f * d
        val swatchAvailableW = (contentRight - swatchStartLeft).coerceAtLeast(140f * d)
        val swatchStep = swatchAvailableW / schemes.size.toFloat()
        val swatchR = 9.5f * d

        schemes.forEachIndexed { idx, item ->
            val scx = swatchStartLeft + swatchStep * (idx + 0.5f)
            val selected = state.desktopColorScheme == item
            val cStart = ((alphaInt) shl 24) or (item.playedStartHex.toInt() and 0x00FFFFFF)
            val cEnd = ((alphaInt) shl 24) or (item.playedEndHex.toInt() and 0x00FFFFFF)

            if (selected) {
                iconStrokePaint.color = ((alpha * 235).roundToInt() shl 24) or 0xFFFFFF
                iconStrokePaint.strokeWidth = 1.8f * d
                canvas.drawCircle(scx, row1Cy, swatchR + 3.2f * d, iconStrokePaint)
            }

            swatchPaint.shader = LinearGradient(
                scx - swatchR, row1Cy - swatchR, scx + swatchR, row1Cy + swatchR,
                cStart, cEnd, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(scx, row1Cy, swatchR, swatchPaint)
            swatchPaint.shader = null

            if (selected) {
                swatchPaint.color = (alphaInt shl 24) or 0xFFFFFF
                canvas.drawCircle(scx, row1Cy, 3.0f * d, swatchPaint)
            }

            if (alpha > 0.5f) {
                val hit = RectF(scx - 16f * d, row1Cy - 15f * d, scx + 16f * d, row1Cy + 15f * d)
                hitRegions.add(hit to {
                    SystemLyricBridge.setDesktopColorScheme(item)
                })
            }
        }

        // 第二行：歌词字号（圆润无框 A- / 平滑进度条 / 字号读数 / A+）
        val row2Cy = topY + 62f * d
        canvas.drawText("字号", padX, row2Cy + 4f * d, labelPaint)

        val minusCx = padX + 54f * d
        val plusCx = contentRight - 14f * d
        val sizeValueText = "${state.desktopFontSize.roundToInt()}sp"
        labelPaint.color = (alphaInt shl 24) or (accentColor and 0x00FFFFFF)
        val valTextW = labelPaint.measureText(sizeValueText)
        val valTextX = plusCx - 24f * d - valTextW

        labelPaint.color = ((alpha * 240).roundToInt() shl 24) or 0xFFFFFF
        labelPaint.textSize = 13f * sp
        canvas.drawText("A-", minusCx - 8f * d, row2Cy + 4.5f * d, labelPaint)
        canvas.drawText("A+", plusCx - 8f * d, row2Cy + 4.5f * d, labelPaint)

        labelPaint.color = (alphaInt shl 24) or (accentColor and 0x00FFFFFF)
        labelPaint.textSize = 11.5f * sp
        canvas.drawText(sizeValueText, valTextX, row2Cy + 4f * d, labelPaint)

        val trackLeft = minusCx + 16f * d
        val trackRight = (valTextX - 12f * d).coerceAtLeast(trackLeft + 48f * d)
        val trackH = 5f * d
        fontSliderTrackRect.set(trackLeft, row2Cy - 14f * d, trackRight, row2Cy + 14f * d)

        // 轨道底色
        swatchPaint.color = ((alpha * 54).roundToInt() shl 24) or 0xFFFFFF
        tempRect.set(trackLeft, row2Cy - trackH / 2f, trackRight, row2Cy + trackH / 2f)
        canvas.drawRoundRect(tempRect, trackH / 2f, trackH / 2f, swatchPaint)

        // 激活进度与圆润滑块
        val frac = ((state.desktopFontSize - 15f) / (26f - 15f)).coerceIn(0f, 1f)
        val activeRight = trackLeft + (trackRight - trackLeft) * frac
        swatchPaint.color = (alphaInt shl 24) or (accentColor and 0x00FFFFFF)
        tempRect.set(trackLeft, row2Cy - trackH / 2f, activeRight, row2Cy + trackH / 2f)
        canvas.drawRoundRect(tempRect, trackH / 2f, trackH / 2f, swatchPaint)

        swatchPaint.color = (alphaInt shl 24) or 0xFFFFFF
        canvas.drawCircle(activeRight, row2Cy, 6.5f * d, swatchPaint)
        swatchPaint.color = (alphaInt shl 24) or (accentColor and 0x00FFFFFF)
        canvas.drawCircle(activeRight, row2Cy, 3.5f * d, swatchPaint)

        if (alpha > 0.5f) {
            hitRegions.add(RectF(minusCx - 18f * d, row2Cy - 16f * d, minusCx + 14f * d, row2Cy + 16f * d) to {
                SystemLyricBridge.setDesktopFontSize(state.desktopFontSize - 1f)
            })
            hitRegions.add(RectF(plusCx - 14f * d, row2Cy - 16f * d, plusCx + 18f * d, row2Cy + 16f * d) to {
                SystemLyricBridge.setDesktopFontSize(state.desktopFontSize + 1f)
            })
        }
    }

    private fun updateFontSizeFromTouchX(touchX: Float) {
        if (fontSliderTrackRect.isEmpty) return
        val w = (fontSliderTrackRect.width()).coerceAtLeast(1f)
        val frac = ((touchX - fontSliderTrackRect.left) / w).coerceIn(0f, 1f)
        val rawSize = 15f + frac * (26f - 15f)
        val stepped = (rawSize * 2f).roundToInt() / 2f
        SystemLyricBridge.setDesktopFontSize(stepped)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (SystemLyricBridge.state.value.floatingCapsuleLocked) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawY = event.rawY
                isDragging = false
                if (showHud && showSettingsPanel && !fontSliderTrackRect.isEmpty &&
                    fontSliderTrackRect.contains(event.x, event.y)
                ) {
                    isDraggingFontSlider = true
                    updateFontSizeFromTouchX(event.x)
                    return true
                }
                isDraggingFontSlider = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingFontSlider) {
                    updateFontSizeFromTouchX(event.x)
                    return true
                }
                val dy = event.rawY - lastRawY
                if (!isDragging && abs(event.rawY - downRawY) > touchSlop) {
                    isDragging = true
                }
                if (isDragging) {
                    lastRawY = event.rawY
                    onVerticalDragDelta(dy, false)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingFontSlider) {
                    isDraggingFontSlider = false
                    return true
                }
                if (isDragging) {
                    onVerticalDragDelta(0f, true)
                    isDragging = false
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val x = event.x
                    val y = event.y
                    if (showHud) {
                        val matched = hitRegions.firstOrNull { (rect, _) -> rect.contains(x, y) }
                        if (matched != null) {
                            matched.second.invoke()
                            return true
                        }
                    }
                    setHudVisible(!showHud)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
