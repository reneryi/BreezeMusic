package com.concept.music.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
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
import android.widget.Toast
import com.concept.music.MainActivity
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val floatingCapsuleEnabled: Boolean = false, // 桌面歌词总开关
    val floatingCapsuleLocked: Boolean = false,  // 桌面歌词防误触锁定（支持穿透）
    val systemOverlayActive: Boolean = false,    // 是否已挂载系统级跨应用桌面歌词悬浮窗
    val desktopColorScheme: DesktopLyricColorScheme = DesktopLyricColorScheme.ICE_BLUE,
    val desktopLineMode: DesktopLyricLineMode = DesktopLyricLineMode.DUAL_BILINGUAL,
    val desktopFontSize: Float = 18f,
    val desktopWidthFraction: Float = 0.88f,     // 窗口宽度比例 (0.68 ~ 0.96)
    val desktopBgAlpha: Float = 0.68f,
    val desktopAlignment: Int = 0, // 0: 居中, 1: 居左
    val desktopShowTranslation: Boolean = true,
    val savedOffsetX: Float = 0f,
    val savedOffsetY: Float = 140f
)

/**
 * 桌面悬浮歌词与桌面小组件歌词同步桥接器：
 * 1. 驱动 Android 系统级全局桌面歌词悬浮窗 (`WindowManager`) 与应用内悬浮胶囊；
 * 2. 支持桌面歌词配色、双行/KTV交替/单行模式、字号、透明度、宽度与防误触穿透锁定；
 * 3. 在歌词行切换时同步刷新桌面小组件 (`ConceptMusicWidgetUpdater`)。
 */
object SystemLyricBridge {
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastLineIndex: Int = -1
    private var lastSongId: String = ""

    // 实时播放上下文（供系统级桌面歌词悬浮窗高频零分配读取）
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

    // 播放控制回调
    var onTogglePlayCallback: (() -> Unit)? = null
    var onPreviousCallback: (() -> Unit)? = null
    var onNextCallback: (() -> Unit)? = null

    private var windowManager: WindowManager? = null
    // 桌面歌词悬浮窗
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
            desktopFontSize = p.getFloat("desktop_font_size", 18f).coerceIn(13f, 28f),
            desktopWidthFraction = p.getFloat("desktop_width_fraction", 0.88f).coerceIn(0.68f, 0.96f),
            desktopBgAlpha = p.getFloat("desktop_bg_alpha", 0.68f).coerceIn(0f, 0.96f),
            desktopAlignment = p.getInt("desktop_alignment", 0).coerceIn(0, 1),
            desktopShowTranslation = p.getBoolean("desktop_show_translation", true),
            savedOffsetX = p.getFloat("desktop_offset_x", 0f),
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

    /**
     * 切换或开启桌面歌词（自动检测并引导开启系统级全局悬浮窗权限）
     */
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
        onTogglePlay: (() -> Unit)? = null,
        onPrevious: (() -> Unit)? = null,
        onNext: (() -> Unit)? = null
    ) {
        currentSongSnapshot = song
        currentLyricSnapshot = lyricData
        currentLineIndexSnapshot = currentLyricIndex
        currentPositionMsSnapshot = currentPositionMs
        lastPositionSyncRealtimeMs = android.os.SystemClock.elapsedRealtime()
        isPlayingSnapshot = isPlaying
        if (onTogglePlay != null) onTogglePlayCallback = onTogglePlay
        if (onPrevious != null) onPreviousCallback = onPrevious
        if (onNext != null) onNextCallback = onNext

        val st = _state.value
        if (st.floatingCapsuleEnabled) {
            mainHandler.post {
                syncSystemOverlayWindow()
                overlayView?.onPlaybackUpdated()
            }
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

    fun setDesktopColorScheme(scheme: DesktopLyricColorScheme) {
        _state.value = _state.value.copy(desktopColorScheme = scheme)
        prefs?.edit()?.putString("desktop_color_scheme", scheme.name)?.apply()
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

    fun setDesktopFontSize(sizeSp: Float) {
        val clamped = sizeSp.coerceIn(13f, 28f)
        _state.value = _state.value.copy(desktopFontSize = clamped)
        prefs?.edit()?.putFloat("desktop_font_size", clamped)?.apply()
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
        _state.value = _state.value.copy(savedOffsetX = offsetX, savedOffsetY = offsetY)
        prefs?.edit()
            ?.putFloat("desktop_offset_x", offsetX)
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
    // 桌面歌词悬浮窗生命周期管理
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
                val targetW = (screenW * st.desktopWidthFraction).roundToInt().coerceIn((screenW * 0.65f).roundToInt(), screenW - 24)

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

                val initialX = st.savedOffsetX.roundToInt().coerceIn(-(screenW - targetW) / 2, (screenW - targetW) / 2)
                val initialY = st.savedOffsetY.roundToInt().coerceIn(64, (screenH - 220).coerceAtLeast(120))

                val lp = WindowManager.LayoutParams(
                    targetW,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    effectiveFlags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    x = initialX
                    y = initialY
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    }
                }

                val view = NativeDesktopLyricView(ctx) { dx, dy, isFinished ->
                    val curLp = overlayParams ?: return@NativeDesktopLyricView
                    val liveDm = ctx.resources.displayMetrics
                    val liveW = liveDm.widthPixels.coerceAtLeast(720)
                    val liveH = liveDm.heightPixels.coerceAtLeast(720)
                    val maxHalfX = ((liveW - curLp.width).coerceAtLeast(0)) / 2
                    curLp.x = (curLp.x + dx).roundToInt().coerceIn(-maxHalfX, maxHalfX)
                    curLp.y = (curLp.y + dy).roundToInt().coerceIn(36, (liveH - viewHeightOrDefault()).coerceAtLeast(80))
                    runCatching { wm.updateViewLayout(overlayView, curLp) }
                    if (isFinished) {
                        saveDesktopPosition(curLp.x.toFloat(), curLp.y.toFloat())
                    }
                    syncUnlockPillPosition()
                }

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

    private fun viewHeightOrDefault(): Int = overlayView?.height?.takeIf { it > 0 } ?: 180

    private fun updateSystemWindowWidth() {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val lp = overlayParams ?: return
        val screenW = ctx.resources.displayMetrics.widthPixels.coerceAtLeast(720)
        lp.width = (screenW * _state.value.desktopWidthFraction).roundToInt().coerceIn((screenW * 0.65f).roundToInt(), screenW - 24)
        val maxHalfX = ((screenW - lp.width).coerceAtLeast(0)) / 2
        lp.x = lp.x.coerceIn(-maxHalfX, maxHalfX)
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
            val pillSize = (34f * density).roundToInt()
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
                gravity = Gravity.TOP or Gravity.END
                x = (14f * density).roundToInt()
                y = (mainLp?.y ?: 140) + (12f * density).roundToInt()
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
        pillLp.y = mainLp.y + 16
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
 * 锁定状态下的迷你悬浮解锁圆钮（仅占 34dp，让歌词主区域实现 100% 物理点击穿透）
 */
private class NativeUnlockPillView(
    context: Context,
    private val onUnlockClick: () -> Unit
) : View(context) {
    private val d = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC0D1524.toInt()
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE5A93B.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * d
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE5A93B.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE5A93B.toInt()
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = (width.coerceAtMost(height) / 2f) - 1.5f * d
        canvas.drawCircle(cx, cy, r, bgPaint)
        canvas.drawCircle(cx, cy, r, strokePaint)

        // 绘制圆润饱满小锁图标
        val bw = 10f * d
        val bh = 8f * d
        rect.set(cx - bw / 2f, cy - 1f * d, cx + bw / 2f, cy - 1f * d + bh)
        canvas.drawRoundRect(rect, 2.5f * d, 2.5f * d, bodyPaint)
        rect.set(cx - 3.2f * d, cy - 6.5f * d, cx + 3.2f * d, cy + 1f * d)
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
 * 原生硬件加速系统级桌面歌词视图（零 Compose 重组开销、真 60/120fps 逐字平滑线性流光渲染、丝滑零延迟拖拽）
 */
private class NativeDesktopLyricView(
    context: Context,
    private val onDragDelta: (dx: Float, dy: Float, isFinished: Boolean) -> Unit
) : View(context) {
    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var showHud = false
    private var showStyleBar = false

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var isDragging = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
    }
    private val mainTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 11.5f * sp
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val buttonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val containerRect = RectF()
    private val hitRegions = mutableListOf<Pair<RectF, () -> Unit>>()

    fun expandHudOnUnlock() {
        showHud = true
        requestLayout()
        invalidate()
    }

    private var lastLayoutLineIdx = -2
    private var lastLayoutFontSize = -1f
    private var lastLayoutLineMode: DesktopLyricLineMode? = null
    private var lastLayoutLocked: Boolean? = null

    fun onPlaybackUpdated() {
        val st = SystemLyricBridge.state.value
        val curIdx = SystemLyricBridge.currentLineIndexSnapshot
        if (curIdx != lastLayoutLineIdx ||
            st.desktopFontSize != lastLayoutFontSize ||
            st.desktopLineMode != lastLayoutLineMode ||
            st.floatingCapsuleLocked != lastLayoutLocked
        ) {
            lastLayoutLineIdx = curIdx
            lastLayoutFontSize = st.desktopFontSize
            lastLayoutLineMode = st.desktopLineMode
            lastLayoutLocked = st.floatingCapsuleLocked
            requestLayout()
        }
        postInvalidateOnAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val st = SystemLyricBridge.state.value
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast((260 * d).roundToInt())
        val baseFontPx = st.desktopFontSize * sp
        val lineCount = if (st.desktopLineMode == DesktopLyricLineMode.SINGLE_COMPACT) 1 else 2
        val lyricAreaH = if (lineCount == 1) (baseFontPx * 1.65f + 16f * d) else (baseFontPx * 2.85f + 22f * d)
        val hudH = if (showHud && !st.floatingCapsuleLocked) (40f * d) else 0f
        val styleH = if (showHud && showStyleBar && !st.floatingCapsuleLocked) (42f * d) else 0f
        val totalH = (lyricAreaH + hudH + styleH).roundToInt().coerceAtLeast((48 * d).roundToInt())
        setMeasuredDimension(w, totalH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        hitRegions.clear()

        val st = SystemLyricBridge.state.value
        val scheme = st.desktopColorScheme
        val startColor = scheme.playedStartHex.toInt()
        val endColor = scheme.playedEndHex.toInt()
        val unplayedColor = (scheme.unplayedHex.toInt() and 0x00FFFFFF) or (0xD8 shl 24)
        val strokeColor = scheme.strokeHex.toInt()

        val effectiveBgAlpha = when {
            st.floatingCapsuleLocked -> (st.desktopBgAlpha * 0.32f).coerceIn(0f, 0.50f)
            showHud -> st.desktopBgAlpha.coerceAtLeast(0.76f)
            else -> st.desktopBgAlpha
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 4f * d
        containerRect.set(pad, pad, w - pad, h - pad)
        val cornerR = 20f * d

        if (effectiveBgAlpha > 0.04f) {
            val alphaInt = (effectiveBgAlpha * 255).roundToInt().coerceIn(0, 245)
            bgPaint.color = (alphaInt shl 24) or 0x0A101D
            canvas.drawRoundRect(containerRect, cornerR, cornerR, bgPaint)

            if (!st.floatingCapsuleLocked || effectiveBgAlpha > 0.18f) {
                val borderAlpha = if (st.floatingCapsuleLocked) 28 else 72
                borderPaint.color = (borderAlpha shl 24) or (startColor and 0x00FFFFFF)
                canvas.drawRoundRect(containerRect, cornerR, cornerR, borderPaint)
            }
        }

        var cursorY = containerRect.top + 10f * d
        val song = SystemLyricBridge.currentSongSnapshot

        // 1. 交互控制栏 (未锁定且点击唤出时显示)
        if (showHud && !st.floatingCapsuleLocked) {
            drawHudControls(canvas, cursorY, w, startColor, song)
            cursorY += 36f * d
        }

        // 2. 实时计算插值播放进度（实现丝滑 60fps 逐字卡拉OK扫光）
        val basePos = SystemLyricBridge.currentPositionMsSnapshot
        val elapsed = if (SystemLyricBridge.isPlayingSnapshot && SystemLyricBridge.lastPositionSyncRealtimeMs > 0L) {
            (android.os.SystemClock.elapsedRealtime() - SystemLyricBridge.lastPositionSyncRealtimeMs).coerceIn(0L, 250L)
        } else 0L
        val livePosMs = basePos + (elapsed * SystemLyricBridge.playbackSpeedSnapshot).toLong()

        val lines = SystemLyricBridge.currentLyricSnapshot.lines
        val safeIdx = SystemLyricBridge.currentLineIndexSnapshot.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        val currentLine = lines.getOrNull(safeIdx)
        val nextLine = lines.getOrNull(safeIdx + 1)
        val fallbackTitle = if (song != null) "${song.title} - ${song.artist}" else "微风音乐 · 酷狗概念版"

        val mainFontSizePx = st.desktopFontSize * sp
        val subFontSizePx = (st.desktopFontSize - 3.2f).coerceAtLeast(12f) * sp

        when (st.desktopLineMode) {
            DesktopLyricLineMode.SINGLE_COMPACT -> {
                val textY = cursorY + mainFontSizePx * 0.92f
                drawKaraokeLine(
                    canvas = canvas,
                    line = currentLine,
                    fallback = fallbackTitle,
                    posMs = livePosMs,
                    y = textY,
                    fontSizePx = mainFontSizePx,
                    startColor = startColor,
                    endColor = endColor,
                    unplayedColor = unplayedColor,
                    shadowColor = strokeColor,
                    alignMode = st.desktopAlignment
                )
            }

            DesktopLyricLineMode.DUAL_ALTERNATING -> {
                val isEven = safeIdx % 2 == 0
                val topLine = if (isEven) currentLine else nextLine
                val bottomLine = if (isEven) nextLine else currentLine
                val topY = cursorY + mainFontSizePx * 0.88f
                drawKaraokeLine(
                    canvas = canvas,
                    line = topLine,
                    fallback = fallbackTitle,
                    posMs = if (isEven) livePosMs else -1L,
                    y = topY,
                    fontSizePx = if (isEven) mainFontSizePx else subFontSizePx,
                    startColor = startColor,
                    endColor = endColor,
                    unplayedColor = unplayedColor,
                    shadowColor = strokeColor,
                    alignMode = 1 // 左对齐上句
                )
                val botY = topY + mainFontSizePx * 1.25f
                drawKaraokeLine(
                    canvas = canvas,
                    line = bottomLine,
                    fallback = "♪ • • •",
                    posMs = if (!isEven) livePosMs else -1L,
                    y = botY,
                    fontSizePx = if (!isEven) mainFontSizePx else subFontSizePx,
                    startColor = startColor,
                    endColor = endColor,
                    unplayedColor = unplayedColor,
                    shadowColor = strokeColor,
                    alignMode = 2 // 右对齐下句
                )
            }

            DesktopLyricLineMode.DUAL_BILINGUAL -> {
                val topY = cursorY + mainFontSizePx * 0.88f
                drawKaraokeLine(
                    canvas = canvas,
                    line = currentLine,
                    fallback = fallbackTitle,
                    posMs = livePosMs,
                    y = topY,
                    fontSizePx = mainFontSizePx,
                    startColor = startColor,
                    endColor = endColor,
                    unplayedColor = unplayedColor,
                    shadowColor = strokeColor,
                    alignMode = st.desktopAlignment
                )
                val subText = when {
                    st.desktopShowTranslation && !currentLine?.translation.isNullOrBlank() -> currentLine?.translation.orEmpty()
                    !nextLine?.text.isNullOrBlank() -> nextLine?.text.orEmpty()
                    else -> "♪ • • •"
                }
                val botY = topY + mainFontSizePx * 1.22f
                drawKaraokeLine(
                    canvas = canvas,
                    line = null,
                    fallback = subText,
                    posMs = -1L,
                    y = botY,
                    fontSizePx = subFontSizePx,
                    startColor = startColor,
                    endColor = endColor,
                    unplayedColor = unplayedColor,
                    shadowColor = strokeColor,
                    alignMode = st.desktopAlignment
                )
            }
        }

        // 3. 底部快捷样式栏
        if (showHud && showStyleBar && !st.floatingCapsuleLocked) {
            val styleTop = h - 42f * d
            drawStyleQuickBar(canvas, styleTop, w, startColor, st)
        }

        // 若正在播放且含有逐字时间戳，按 60fps 持续平滑刷新字内流光
        if (SystemLyricBridge.isPlayingSnapshot && currentLine != null && currentLine.words.isNotEmpty()) {
            postInvalidateDelayed(32L)
        }
    }

    private fun drawKaraokeLine(
        canvas: Canvas,
        line: LyricLine?,
        fallback: String,
        posMs: Long,
        y: Float,
        fontSizePx: Float,
        startColor: Int,
        endColor: Int,
        unplayedColor: Int,
        shadowColor: Int,
        alignMode: Int
    ) {
        val rawText = line?.text?.takeIf { it.isNotBlank() } ?: fallback
        mainTextPaint.textSize = fontSizePx
        mainTextPaint.shader = null
        mainTextPaint.setShadowLayer(4.5f * d, 0f, 1.5f * d, shadowColor)

        val maxW = (width - 32f * d).coerceAtLeast(100f)
        val displayText = ellipsizeToWidth(rawText, mainTextPaint, maxW)
        val textW = mainTextPaint.measureText(displayText).coerceAtLeast(1f)
        val startX = when (alignMode) {
            1 -> 16f * d
            2 -> (width - 16f * d - textW).coerceAtLeast(16f * d)
            else -> ((width - textW) / 2f).coerceAtLeast(12f * d)
        }

        if (line != null && posMs >= 0L) {
            val progressFrac = computeSmoothLineProgress(line, posMs)
            val clampP = progressFrac.coerceIn(0f, 1f)
            if (clampP <= 0.005f) {
                mainTextPaint.color = unplayedColor
            } else if (clampP >= 0.995f) {
                mainTextPaint.color = startColor
            } else {
                val edgeLeft = (clampP - 0.05f).coerceAtLeast(0f)
                val edgeRight = (clampP + 0.05f).coerceAtMost(1f)
                mainTextPaint.shader = LinearGradient(
                    startX, y, startX + textW, y,
                    intArrayOf(startColor, endColor, unplayedColor, unplayedColor),
                    floatArrayOf(0f, edgeLeft, edgeRight, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        } else {
            mainTextPaint.color = if (posMs >= 0L) startColor else unplayedColor
        }

        canvas.drawText(displayText, startX, y, mainTextPaint)
        mainTextPaint.shader = null
    }

    private fun computeSmoothLineProgress(line: LyricLine, posMs: Long): Float {
        if (line.words.isNotEmpty()) {
            var totalChars = 0
            line.words.forEach { totalChars += it.text.length.coerceAtLeast(1) }
            if (totalChars <= 0) return 1f
            var coveredChars = 0f
            for (word in line.words) {
                val wLen = word.text.length.coerceAtLeast(1).toFloat()
                val wEnd = word.startTimeMs + word.durationMs
                when {
                    posMs >= wEnd -> coveredChars += wLen
                    posMs > word.startTimeMs && word.durationMs > 0L -> {
                        val sub = ((posMs - word.startTimeMs).toFloat() / word.durationMs.toFloat()).coerceIn(0f, 1f)
                        coveredChars += wLen * sub
                        break
                    }
                    else -> break
                }
            }
            return (coveredChars / totalChars.toFloat()).coerceIn(0f, 1f)
        }
        if (line.durationMs <= 0L) return 1f
        return ((posMs - line.startTimeMs).toFloat() / line.durationMs.toFloat()).coerceIn(0f, 1f)
    }

    private fun ellipsizeToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 4 && paint.measureText(text, 0, end) + 18f > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    private fun drawHudControls(canvas: Canvas, topY: Float, w: Float, accentColor: Int, song: Song?) {
        val btnH = 24f * d
        val cy = topY + btnH / 2f

        // 左侧：返回主 App 按钮
        val appRect = RectF(14f * d, topY, 92f * d, topY + btnH)
        buttonBgPaint.color = 0x26FFFFFF
        canvas.drawRoundRect(appRect, btnH / 2f, btnH / 2f, buttonBgPaint)
        hudPaint.color = 0xFFFFFFFF.toInt()
        hudPaint.textSize = 10.5f * sp
        canvas.drawText("↗ 打开音乐", appRect.left + 8f * d, cy + 3.8f * d, hudPaint)
        hitRegions.add(appRect to {
            runCatching {
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
            }
        })

        // 中间：上一首 / 播放暂停 / 下一首
        val centerX = w / 2f
        val prevRect = RectF(centerX - 46f * d, topY, centerX - 18f * d, topY + btnH)
        val playRect = RectF(centerX - 14f * d, topY, centerX + 14f * d, topY + btnH)
        val nextRect = RectF(centerX + 18f * d, topY, centerX + 46f * d, topY + btnH)

        hudPaint.textSize = 12f * sp
        canvas.drawText("⏮", prevRect.left + 6f * d, cy + 4.5f * d, hudPaint)
        buttonBgPaint.color = accentColor
        canvas.drawRoundRect(playRect, btnH / 2f, btnH / 2f, buttonBgPaint)
        hudPaint.color = 0xFF06101C.toInt()
        val playSymbol = if (SystemLyricBridge.isPlayingSnapshot) "❚❚" else "▶"
        canvas.drawText(playSymbol, playRect.left + 7f * d, cy + 4.2f * d, hudPaint)
        hudPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText("⏭", nextRect.left + 6f * d, cy + 4.5f * d, hudPaint)

        hitRegions.add(prevRect to { SystemLyricBridge.onPreviousCallback?.invoke() })
        hitRegions.add(playRect to { SystemLyricBridge.onTogglePlayCallback?.invoke() })
        hitRegions.add(nextRect to { SystemLyricBridge.onNextCallback?.invoke() })

        // 右侧：样式 / 锁定 / 关闭
        val closeRect = RectF(w - 38f * d, topY, w - 12f * d, topY + btnH)
        val lockRect = RectF(w - 68f * d, topY, w - 42f * d, topY + btnH)
        val styleRect = RectF(w - 102f * d, topY, w - 72f * d, topY + btnH)

        buttonBgPaint.color = if (showStyleBar) accentColor else 0x26FFFFFF
        canvas.drawRoundRect(styleRect, btnH / 2f, btnH / 2f, buttonBgPaint)
        hudPaint.color = if (showStyleBar) 0xFF06101C.toInt() else 0xFFFFFFFF.toInt()
        hudPaint.textSize = 10f * sp
        canvas.drawText("词风", styleRect.left + 5f * d, cy + 3.5f * d, hudPaint)

        buttonBgPaint.color = 0x26FFFFFF
        canvas.drawRoundRect(lockRect, btnH / 2f, btnH / 2f, buttonBgPaint)
        hudPaint.color = 0xFFE5A93B.toInt()
        canvas.drawText("🔒", lockRect.left + 6f * d, cy + 4f * d, hudPaint)

        canvas.drawRoundRect(closeRect, btnH / 2f, btnH / 2f, buttonBgPaint)
        hudPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText("✕", closeRect.left + 8f * d, cy + 4f * d, hudPaint)

        hitRegions.add(styleRect to {
            showStyleBar = !showStyleBar
            requestLayout()
            invalidate()
        })
        hitRegions.add(lockRect to {
            showHud = false
            showStyleBar = false
            SystemLyricBridge.setFloatingCapsuleLocked(true)
            Toast.makeText(context, "桌面歌词已锁定（支持点击穿透），点击右侧金锁图标可解锁", Toast.LENGTH_SHORT).show()
        })
        hitRegions.add(closeRect to {
            SystemLyricBridge.setFloatingCapsuleEnabled(false)
        })
    }

    private fun drawStyleQuickBar(canvas: Canvas, topY: Float, w: Float, accentColor: Int, st: SystemLyricState) {
        val btnH = 24f * d
        val cy = topY + btnH / 2f
        val gap = 6f * d
        val itemW = ((w - 28f * d - gap * 4) / 5f).coerceAtLeast(42f * d)

        val actions: List<Pair<String, () -> Unit>> = listOf(
            "色:${st.desktopColorScheme.label.take(2)}" to {
                val entries = DesktopLyricColorScheme.entries
                val next = entries[(entries.indexOf(st.desktopColorScheme) + 1) % entries.size]
                SystemLyricBridge.setDesktopColorScheme(next)
            },
            st.desktopLineMode.label.take(4) to {
                val entries = DesktopLyricLineMode.entries
                val next = entries[(entries.indexOf(st.desktopLineMode) + 1) % entries.size]
                SystemLyricBridge.setDesktopLineMode(next)
            },
            "字号-" to { SystemLyricBridge.setDesktopFontSize(st.desktopFontSize - 1.5f) },
            "字号+" to { SystemLyricBridge.setDesktopFontSize(st.desktopFontSize + 1.5f) },
            (if (st.desktopBgAlpha < 0.08f) "透:无框" else "透:${(st.desktopBgAlpha * 100).toInt()}%") to {
                val steps = listOf(0.0f, 0.35f, 0.68f, 0.88f)
                val next = steps.firstOrNull { it > st.desktopBgAlpha + 0.08f } ?: steps.first()
                SystemLyricBridge.setDesktopBgAlpha(next)
            }
        )

        actions.forEachIndexed { idx, (label, act) ->
            val left = 14f * d + idx * (itemW + gap)
            val rect = RectF(left, topY, left + itemW, topY + btnH)
            buttonBgPaint.color = 0x26FFFFFF
            canvas.drawRoundRect(rect, 8f * d, 8f * d, buttonBgPaint)
            hudPaint.color = 0xFFFFFFFF.toInt()
            hudPaint.textSize = 9.8f * sp
            val tw = hudPaint.measureText(label)
            canvas.drawText(label, rect.centerX() - tw / 2f, cy + 3.5f * d, hudPaint)
            hitRegions.add(rect to act)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (SystemLyricBridge.state.value.floatingCapsuleLocked) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                if (!isDragging && (abs(event.rawX - downRawX) > touchSlop || abs(event.rawY - downRawY) > touchSlop)) {
                    isDragging = true
                }
                if (isDragging) {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    onDragDelta(dx, dy, false)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    onDragDelta(0f, 0f, true)
                    isDragging = false
                } else {
                    val x = event.x
                    val y = event.y
                    val matched = hitRegions.firstOrNull { (rect, _) -> rect.contains(x, y) }
                    if (matched != null) {
                        matched.second.invoke()
                    } else {
                        showHud = !showHud
                        if (!showHud) showStyleBar = false
                        requestLayout()
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    onDragDelta(0f, 0f, true)
                    isDragging = false
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
