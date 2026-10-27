package com.concept.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.core.app.NotificationCompat
import com.concept.music.MainActivity
import com.concept.music.R
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.LyricWord
import com.concept.music.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

/**
 * 状态栏歌词与灵动岛核心工作模式（借鉴椒盐音乐 SaltPlayerSource 多系统适配方案）
 */
enum class StatusBarLyricMode(
    val label: String,
    val subtitle: String
) {
    AUTO_SMART(
        "智能全协议融合 (推荐)",
        "同时激活顶部悬浮状态栏/灵动岛 + Flyme 通知 Ticker + 澎湃焦点通知/流体云 + 模块广播"
    ),
    SALT_STATUS_OVERLAY(
        "椒盐风·状态栏悬浮歌词",
        "参考椒盐音乐悬浮窗状态栏歌词，免 Root 直接在系统状态栏/挖孔旁渲染实时逐字歌词"
    ),
    DYNAMIC_ISLAND_CAPSULE(
        "灵动岛音乐胶囊",
        "iPhone 灵动岛风格顶部药丸胶囊，含微缩旋转黑胶、逐字流光歌词与音频频谱，点击可展开控播舱"
    ),
    SYSTEM_TICKER_ISLAND(
        "系统原生 Ticker / 流体云 / 焦点岛",
        "通过 Notification 0x01000000 协议、HyperOS 焦点通知、ColorOS 流体云与 OriginOS 原子岛推送"
    ),
    HOOK_BROADCAST(
        "Xposed / Root 模块广播",
        "向 LyricGetter / SuperLyric / StatusBarLyric / EventLyric 实时发送标准状态栏歌词广播"
    )
}

/**
 * 顶部状态栏/灵动岛视觉形态
 */
enum class StatusBarOverlayStyle(
    val label: String,
    val subtitle: String
) {
    DYNAMIC_ISLAND(
        "灵动岛流光胶囊",
        "药丸胶囊造型，集成旋转黑胶封面、KRC 逐字流光歌词与律动音频波纹，支持点击展开控播面板"
    ),
    SALT_PURE_STATUS_BAR(
        "椒盐极简状态栏字条",
        "参考 SaltPlayer 悬浮窗状态栏歌词，贴合系统状态栏高度，支持完全透明背景与物理点击穿透"
    )
}

/**
 * 状态栏歌词水平锚点（方便避让前摄挖孔、左侧时间或右侧电池图标）
 */
enum class StatusBarHorizontalAnchor(val label: String) {
    CENTER("顶部居中 (灵动岛/居中挖孔)"),
    LEFT_PUNCH_HOLE("居左对齐 (避让居中/右侧图标)"),
    RIGHT_STATUS("居右对齐 (避让左侧挖孔/时间)")
}

/**
 * 国内主流 Android ROM 生态识别结果
 */
enum class RomEcosystemType(
    val displayName: String,
    val islandFeatureName: String,
    val recommendedGuide: String
) {
    HYPER_OS(
        "小米 HyperOS / MIUI",
        "澎湃焦点通知 / 灵动岛",
        "未 Root 机型推荐使用「椒盐风·状态栏悬浮歌词」或「灵动岛音乐胶囊」（开启悬浮窗权限即可在任意界面顶部常驻）；已安装 LyricGetter / SuperLyric 模块可额外直连系统原生状态栏。"
    ),
    COLOR_OS(
        "OPPO / 一加 / 真我 ColorOS",
        "流体云 (Fluid Cloud)",
        "已自动注入 ColorOS 流体云实时媒体通知扩展与锁屏歌词同步；开启悬浮窗权限后可使用顶部「灵动岛音乐胶囊」或「椒盐极简状态栏字条」。"
    ),
    ORIGIN_OS(
        "vivo / iQOO OriginOS",
        "原子岛 (Atomic Island)",
        "已适配 OriginOS 原子岛媒体通知与蓝牙/锁屏元数据歌词同步；搭配顶部「灵动岛音乐胶囊」可在任何应用顶部获得完整逐字流光歌词体验。"
    ),
    FLYME(
        "魅族 Flyme",
        "Flyme 原生状态栏歌词 & 实况通知",
        "已原生内置魅族 Flyme 官方 Notification Ticker 协议 (FLAG_ALWAYS_SHOW_TICKER = 0x01000000 | 0x02000000)，开启通知权限即可直推系统状态栏，同时支持顶部灵动岛胶囊。"
    ),
    HARMONY_EMUI(
        "华为 HarmonyOS / EMUI",
        "实况窗 (Live View) / 蓝牙歌词",
        "已启用华为媒体通知与 MediaSession 实时歌词同步（支持车载/锁屏/实况通知），并可通过「椒盐风·状态栏悬浮歌词」在状态栏区域直接渲染逐字歌词。"
    ),
    MAGIC_OS(
        "荣耀 MagicOS",
        "荣耀灵动胶囊 (Magic Capsule)",
        "已适配荣耀媒体实时通知与 MediaSession 歌词同步，开启悬浮窗权限即可启用顶部「灵动岛音乐胶囊」与状态栏悬浮歌词。"
    ),
    ONE_UI(
        "三星 One UI",
        "实时媒体通知 / 状态栏悬浮窗",
        "三星 One UI 推荐使用「灵动岛音乐胶囊」或「椒盐极简状态栏字条」，支持通过 X/Y 偏移滑块精准贴合居中挖孔两侧。"
    ),
    CUSTOM_AOSP(
        "原生 Android / 类原生定制 ROM",
        "原生 Flyme Ticker 协议 & 灵动岛胶囊",
        "已同时开启 Flyme Ticker 通知协议 (0x01000000)、Android 16 Promoted Ongoing 实时通知与顶部灵动岛/状态栏悬浮窗。"
    )
}

data class SystemLyricState(
    val floatingCapsuleEnabled: Boolean = false, // 桌面歌词总开关
    val floatingCapsuleLocked: Boolean = false,  // 桌面歌词防误触锁定（支持穿透）
    val systemOverlayActive: Boolean = false,    // 是否已挂载系统级跨应用桌面歌词悬浮窗
    val statusBarBroadcastEnabled: Boolean = true, // 状态栏歌词总开关
    val statusBarMode: StatusBarLyricMode = StatusBarLyricMode.AUTO_SMART,
    val statusBarOverlayStyle: StatusBarOverlayStyle = StatusBarOverlayStyle.DYNAMIC_ISLAND,
    val statusBarAnchor: StatusBarHorizontalAnchor = StatusBarHorizontalAnchor.CENTER,
    val statusBarOverlayActive: Boolean = false, // 顶部状态栏/灵动岛系统悬浮窗是否已挂载
    val statusBarExpandedIsland: Boolean = false, // 灵动岛是否处于展开控制舱状态
    val statusBarTouchThrough: Boolean = false,  // 状态栏歌词/灵动岛物理点击穿透（不挡下拉通知栏）
    val statusBarShowIslandMedia: Boolean = true, // 灵动岛显示旋转黑胶封面与动态音频波纹
    val statusBarWordByWord: Boolean = true,      // 状态栏/灵动岛逐字流光染色
    val statusBarShowTranslation: Boolean = true, // 状态栏/灵动岛显示双语译文或下一句
    val statusBarOffsetX: Float = 0f,             // 状态栏水平偏移 dp (-160f .. +160f)
    val statusBarOffsetY: Float = 4f,             // 状态栏垂直偏移 dp (0f .. 64f)
    val statusBarWidthDp: Float = 246f,           // 状态栏歌词/灵动岛宽度 dp (140f .. 380f)
    val statusBarFontSize: Float = 13.5f,         // 状态栏歌词字号 sp (11f .. 18f)
    val statusBarBgAlpha: Float = 0.88f,          // 状态栏胶囊背景不透明度 (0f .. 0.96f)
    val statusBarColorScheme: DesktopLyricColorScheme = DesktopLyricColorScheme.ICE_BLUE,
    val flymeTickerProtocolEnabled: Boolean = true,        // Flyme 0x01000000 原生通知 Ticker 协议
    val systemLiveIslandNotificationEnabled: Boolean = true, // HyperOS 焦点通知 / ColorOS 流体云 / OriginOS 原子岛增强
    val mediaSessionLyricSyncEnabled: Boolean = true,      // MediaSession 蓝牙车载/锁屏/原生媒体岛歌词同步
    val xposedBroadcastEnabled: Boolean = true,            // LyricGetter / SuperLyric / StatusBarLyric 广播
    val detectedRom: RomEcosystemType = RomEcosystemType.CUSTOM_AOSP,
    val lastBroadcastedLine: String = "",
    val lastBroadcastedSubLine: String = "",
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
 * 桌面歌词、系统状态栏歌词与多系统灵动岛桥接器（SaltPlayer 架构融合重构版）
 *
 * 核心能力：
 * 1. **椒盐音乐 (`Moriafly/SaltPlayerSource`) 同款「悬浮窗状态栏歌词」与「灵动岛音乐胶囊」**：
 *    - 针对国内未 Root 的 HyperOS / ColorOS / OriginOS / HarmonyOS / OneUI 限制第三方 App 直接写入系统状态栏的痛点，
 *      采用 `TYPE_APPLICATION_OVERLAY` + `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` + `FLAG_LAYOUT_IN_SCREEN` + `FLAG_LAYOUT_NO_LIMITS`
 *      直接在屏幕最顶部的系统状态栏 / 前摄挖孔区域渲染独立的高帧率状态栏歌词或 iPhone 风格灵动岛胶囊；
 *    - 支持 X/Y 像素级偏移微调（精准避让左/中/右挖孔与时间电量）、宽度调节、字号调节、完全透明背景及物理点击穿透 (`FLAG_NOT_TOUCHABLE`)；
 *    - 若尚未授予系统悬浮窗权限，无缝在应用内顶部渲染同款灵动岛/状态栏歌词，并提供一键授权跨应用常驻。
 * 2. **魅族 Flyme 官方状态栏歌词协议 (`Notification.FLAG_ALWAYS_SHOW_TICKER = 0x01000000`, `FLAG_ONLY_UPDATE_TICKER = 0x02000000`)**：
 *    - 严格遵循 Flyme 开放平台规范设置 `tickerText`、`flags`、`ticker_icon` 与 `ticker_icon_switch` 并实时更新系统通知。
 * 3. **小米 HyperOS 澎湃焦点通知 (`miui.focus.param`) / OPPO ColorOS 流体云 / vivo OriginOS 原子岛 / Android 16 Promoted Ongoing**：
 *    - 注入各家系统实况胶囊扩展字段，并支持将实时歌词同步至 `MediaSession` 元数据，赋能系统媒体岛、锁屏与车载蓝牙屏幕。
 * 4. **Xposed / Root 状态栏歌词生态广播 (`LyricGetter` / `SuperLyric` / `StatusBarLyric`)**：
 *    - 完整兼容 `cn.lyric.getter.api.LYRIC`、`com.meizu.flyme.service.StatusBarLyric`、`statusbar.lyric.update` 等模块协议。
 */
object SystemLyricBridge {
    internal const val FLAG_ALWAYS_SHOW_TICKER = 0x01000000
    internal const val FLAG_ONLY_UPDATE_TICKER = 0x02000000
    private const val STATUS_BAR_LYRIC_CHANNEL_ID = "concept_status_bar_lyric_channel"
    private const val STATUS_BAR_LYRIC_NOTIFICATION_ID = 202608

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastLineIndex: Int = -1
    private var lastSongId: String = ""

    // 实时播放上下文（供系统级悬浮窗高频零分配读取）
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

    // 顶部状态栏歌词 / 灵动岛胶囊独立悬浮窗
    private var statusBarOverlayView: NativeStatusBarIslandView? = null
    private var statusBarOverlayParams: WindowManager.LayoutParams? = null

    private val _state = MutableStateFlow(SystemLyricState())
    val state = _state.asStateFlow()

    /**
     * 识别当前设备的 Android ROM 生态类型
     */
    internal fun detectRomEcosystem(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty(),
        display: String = Build.DISPLAY.orEmpty()
    ): RomEcosystemType {
        val combined = "$manufacturer $brand $display".lowercase()
        val miOsProp = getSystemProperty("ro.mi.os.version.name")
        val miuiProp = getSystemProperty("ro.miui.ui.version.name")
        val oppoProp = getSystemProperty("ro.build.version.opporom")
        val vivoProp = getSystemProperty("ro.vivo.os.version")
        val magicProp = getSystemProperty("ro.magic.version")
        val harmonyProp = getSystemProperty("hw_sc.build.platform.version")

        return when {
            combined.contains("meizu") || combined.contains("flyme") -> RomEcosystemType.FLYME
            combined.contains("xiaomi") || combined.contains("redmi") || combined.contains("poco") ||
                miOsProp.isNotBlank() || miuiProp.isNotBlank() -> RomEcosystemType.HYPER_OS
            combined.contains("oppo") || combined.contains("oneplus") || combined.contains("realme") ||
                oppoProp.isNotBlank() -> RomEcosystemType.COLOR_OS
            combined.contains("vivo") || combined.contains("iqoo") ||
                vivoProp.isNotBlank() -> RomEcosystemType.ORIGIN_OS
            combined.contains("honor") || magicProp.isNotBlank() -> RomEcosystemType.MAGIC_OS
            combined.contains("huawei") || harmonyProp.isNotBlank() -> RomEcosystemType.HARMONY_EMUI
            combined.contains("samsung") -> RomEcosystemType.ONE_UI
            else -> RomEcosystemType.CUSTOM_AOSP
        }
    }

    private fun getSystemProperty(key: String): String {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            (getMethod.invoke(null, key, "") as? String).orEmpty()
        }.getOrDefault("")
    }

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
        val sbModeName = p.getString("status_bar_mode", StatusBarLyricMode.AUTO_SMART.name)
            ?: StatusBarLyricMode.AUTO_SMART.name
        val sbStyleName = p.getString("status_bar_overlay_style", StatusBarOverlayStyle.DYNAMIC_ISLAND.name)
            ?: StatusBarOverlayStyle.DYNAMIC_ISLAND.name
        val sbAnchorName = p.getString("status_bar_anchor", StatusBarHorizontalAnchor.CENTER.name)
            ?: StatusBarHorizontalAnchor.CENTER.name
        val sbSchemeName = p.getString("status_bar_color_scheme", DesktopLyricColorScheme.ICE_BLUE.name)
            ?: DesktopLyricColorScheme.ICE_BLUE.name

        val rom = detectRomEcosystem()
        _state.value = SystemLyricState(
            floatingCapsuleEnabled = p.getBoolean("floating_capsule_enabled", false),
            floatingCapsuleLocked = p.getBoolean("floating_capsule_locked", false),
            systemOverlayActive = false,
            statusBarBroadcastEnabled = p.getBoolean("status_bar_broadcast_enabled", true),
            statusBarMode = runCatching { StatusBarLyricMode.valueOf(sbModeName) }
                .getOrDefault(StatusBarLyricMode.AUTO_SMART),
            statusBarOverlayStyle = runCatching { StatusBarOverlayStyle.valueOf(sbStyleName) }
                .getOrDefault(StatusBarOverlayStyle.DYNAMIC_ISLAND),
            statusBarAnchor = runCatching { StatusBarHorizontalAnchor.valueOf(sbAnchorName) }
                .getOrDefault(StatusBarHorizontalAnchor.CENTER),
            statusBarOverlayActive = false,
            statusBarExpandedIsland = false,
            statusBarTouchThrough = p.getBoolean("status_bar_touch_through", false),
            statusBarShowIslandMedia = p.getBoolean("status_bar_show_island_media", true),
            statusBarWordByWord = p.getBoolean("status_bar_word_by_word", true),
            statusBarShowTranslation = p.getBoolean("status_bar_show_translation", true),
            statusBarOffsetX = p.getFloat("status_bar_offset_x", 0f).coerceIn(-160f, 160f),
            statusBarOffsetY = p.getFloat("status_bar_offset_y", 4f).coerceIn(0f, 64f),
            statusBarWidthDp = p.getFloat("status_bar_width_dp", 246f).coerceIn(140f, 380f),
            statusBarFontSize = p.getFloat("status_bar_font_size", 13.5f).coerceIn(11f, 18f),
            statusBarBgAlpha = p.getFloat("status_bar_bg_alpha", 0.88f).coerceIn(0f, 0.96f),
            statusBarColorScheme = runCatching { DesktopLyricColorScheme.valueOf(sbSchemeName) }
                .getOrDefault(DesktopLyricColorScheme.ICE_BLUE),
            flymeTickerProtocolEnabled = p.getBoolean("flyme_ticker_protocol_enabled", true),
            systemLiveIslandNotificationEnabled = p.getBoolean("system_live_island_notification_enabled", true),
            mediaSessionLyricSyncEnabled = p.getBoolean("media_session_lyric_sync_enabled", true),
            xposedBroadcastEnabled = p.getBoolean("xposed_broadcast_enabled", true),
            detectedRom = rom,
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
        ensureStatusBarNotificationChannel(ctx)
        syncSystemOverlayWindow()
        syncStatusBarOverlayWindow()
    }

    internal fun shouldShowTopStatusBarOverlay(st: SystemLyricState): Boolean {
        if (!st.statusBarBroadcastEnabled) return false
        return when (st.statusBarMode) {
            StatusBarLyricMode.AUTO_SMART,
            StatusBarLyricMode.SALT_STATUS_OVERLAY,
            StatusBarLyricMode.DYNAMIC_ISLAND_CAPSULE -> true
            StatusBarLyricMode.SYSTEM_TICKER_ISLAND,
            StatusBarLyricMode.HOOK_BROADCAST -> false
        }
    }

    internal fun effectiveOverlayStyle(st: SystemLyricState): StatusBarOverlayStyle {
        return when (st.statusBarMode) {
            StatusBarLyricMode.SALT_STATUS_OVERLAY -> StatusBarOverlayStyle.SALT_PURE_STATUS_BAR
            StatusBarLyricMode.DYNAMIC_ISLAND_CAPSULE -> StatusBarOverlayStyle.DYNAMIC_ISLAND
            else -> st.statusBarOverlayStyle
        }
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
                    "请开启「显示在其他应用上层」权限，即可在状态栏与任意应用上方常驻显示歌词/灵动岛",
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
        syncStatusBarOverlayWindow()
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
        val prevSongId = currentSongSnapshot?.id
        currentSongSnapshot = song
        currentLyricSnapshot = lyricData
        currentLineIndexSnapshot = currentLyricIndex
        currentPositionMsSnapshot = currentPositionMs
        lastPositionSyncRealtimeMs = android.os.SystemClock.elapsedRealtime()
        isPlayingSnapshot = isPlaying
        if (onTogglePlay != null) onTogglePlayCallback = onTogglePlay
        if (onPrevious != null) onPreviousCallback = onPrevious
        if (onNext != null) onNextCallback = onNext

        // 若切歌后还没有推送首句状态栏歌词，立即推送曲名或首句
        if (song != null && song.id != prevSongId && _state.value.statusBarBroadcastEnabled) {
            val firstLine = lyricData.lines.getOrNull(currentLyricIndex.coerceAtLeast(0))
            val ctx = appContext
            if (ctx != null) {
                if (firstLine != null && firstLine.text.isNotBlank()) {
                    onLyricLineUpdated(ctx, song, currentLyricIndex.coerceAtLeast(0), firstLine, isPlaying)
                } else {
                    val synthetic = LyricLine(
                        startTimeMs = 0L,
                        durationMs = 4000L,
                        text = "${song.title} - ${song.artist}"
                    )
                    onLyricLineUpdated(ctx, song, -100, synthetic, isPlaying)
                }
            }
        }

        val st = _state.value
        if (st.floatingCapsuleEnabled || shouldShowTopStatusBarOverlay(st)) {
            mainHandler.post {
                if (st.floatingCapsuleEnabled) {
                    syncSystemOverlayWindow()
                    overlayView?.onPlaybackUpdated()
                }
                if (shouldShowTopStatusBarOverlay(st)) {
                    syncStatusBarOverlayWindow()
                    statusBarOverlayView?.onPlaybackUpdated()
                }
            }
        }
    }

    fun setFloatingCapsuleEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(floatingCapsuleEnabled = enabled)
        prefs?.edit()?.putBoolean("floating_capsule_enabled", enabled)?.apply()
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

    // =========================================================================
    // 状态栏歌词与灵动岛二级菜单设置项 Mutators
    // =========================================================================

    fun setStatusBarBroadcastEnabled(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        _state.value = _state.value.copy(statusBarBroadcastEnabled = enabled)
        prefs?.edit()?.putBoolean("status_bar_broadcast_enabled", enabled)?.apply()
        syncStatusBarOverlayWindow()
        if (!enabled) {
            sendStopBroadcast(context)
        } else {
            val song = currentSongSnapshot
            val line = currentLyricSnapshot.lines.getOrNull(currentLineIndexSnapshot.coerceAtLeast(0))
            if (song != null && line != null) {
                lastLineIndex = -2
                onLyricLineUpdated(context, song, currentLineIndexSnapshot.coerceAtLeast(0), line, isPlayingSnapshot)
            } else {
                triggerTestStatusBarLyric(context, showToast = false)
            }
        }
    }

    fun setStatusBarMode(context: Context, mode: StatusBarLyricMode) {
        appContext = context.applicationContext
        val updatedStyle = when (mode) {
            StatusBarLyricMode.SALT_STATUS_OVERLAY -> StatusBarOverlayStyle.SALT_PURE_STATUS_BAR
            StatusBarLyricMode.DYNAMIC_ISLAND_CAPSULE -> StatusBarOverlayStyle.DYNAMIC_ISLAND
            else -> _state.value.statusBarOverlayStyle
        }
        _state.value = _state.value.copy(
            statusBarMode = mode,
            statusBarOverlayStyle = updatedStyle
        )
        prefs?.edit()
            ?.putString("status_bar_mode", mode.name)
            ?.putString("status_bar_overlay_style", updatedStyle.name)
            ?.apply()
        syncStatusBarOverlayWindow()
        refreshStatusBarLyricDisplay()
    }

    fun setStatusBarOverlayStyle(style: StatusBarOverlayStyle) {
        val touchThroughDefault = if (style == StatusBarOverlayStyle.SALT_PURE_STATUS_BAR) true else _state.value.statusBarTouchThrough
        val bgAlphaDefault = if (style == StatusBarOverlayStyle.SALT_PURE_STATUS_BAR && _state.value.statusBarBgAlpha > 0.7f) {
            0.28f
        } else if (style == StatusBarOverlayStyle.DYNAMIC_ISLAND && _state.value.statusBarBgAlpha < 0.4f) {
            0.88f
        } else {
            _state.value.statusBarBgAlpha
        }
        _state.value = _state.value.copy(
            statusBarOverlayStyle = style,
            statusBarTouchThrough = touchThroughDefault,
            statusBarBgAlpha = bgAlphaDefault,
            statusBarExpandedIsland = false
        )
        prefs?.edit()
            ?.putString("status_bar_overlay_style", style.name)
            ?.putBoolean("status_bar_touch_through", touchThroughDefault)
            ?.putFloat("status_bar_bg_alpha", bgAlphaDefault)
            ?.apply()
        refreshStatusBarLyricDisplay(relayout = true)
    }

    fun setStatusBarAnchor(anchor: StatusBarHorizontalAnchor) {
        _state.value = _state.value.copy(statusBarAnchor = anchor)
        prefs?.edit()?.putString("status_bar_anchor", anchor.name)?.apply()
        refreshStatusBarLyricDisplay(relayout = true)
    }

    fun setStatusBarTouchThrough(touchThrough: Boolean) {
        _state.value = _state.value.copy(
            statusBarTouchThrough = touchThrough,
            statusBarExpandedIsland = if (touchThrough) false else _state.value.statusBarExpandedIsland
        )
        prefs?.edit()?.putBoolean("status_bar_touch_through", touchThrough)?.apply()
        refreshStatusBarLyricDisplay(relayout = true)
    }

    fun setStatusBarShowIslandMedia(show: Boolean) {
        _state.value = _state.value.copy(statusBarShowIslandMedia = show)
        prefs?.edit()?.putBoolean("status_bar_show_island_media", show)?.apply()
        refreshStatusBarLyricDisplay(relayout = true)
    }

    fun setStatusBarWordByWord(enabled: Boolean) {
        _state.value = _state.value.copy(statusBarWordByWord = enabled)
        prefs?.edit()?.putBoolean("status_bar_word_by_word", enabled)?.apply()
        refreshStatusBarLyricDisplay()
    }

    fun setStatusBarShowTranslation(show: Boolean) {
        _state.value = _state.value.copy(statusBarShowTranslation = show)
        prefs?.edit()?.putBoolean("status_bar_show_translation", show)?.apply()
        refreshStatusBarLyricDisplay(relayout = true)
    }

    fun setStatusBarOffsetX(offsetDp: Float) {
        val clamped = offsetDp.coerceIn(-160f, 160f)
        _state.value = _state.value.copy(statusBarOffsetX = clamped)
        prefs?.edit()?.putFloat("status_bar_offset_x", clamped)?.apply()
        refreshStatusBarLyricDisplay(relayout = true)
    }

    fun setStatusBarOffsetY(offsetDp: Float) {
        val clamped = offsetDp.coerceIn(0f, 64f)
        _state.value = _state.value.copy(statusBarOffsetY = clamped)
        prefs?.edit()?.putFloat("status_bar_offset_y", clamped)?.apply()
        refreshStatusBarLyricDisplay(relayout = true)
    }

    fun setStatusBarWidthDp(widthDp: Float) {
        val clamped = widthDp.coerceIn(140f, 380f)
        _state.value = _state.value.copy(statusBarWidthDp = clamped)
        prefs?.edit()?.putFloat("status_bar_width_dp", clamped)?.apply()
        refreshStatusBarLyricDisplay(relayout = true)
    }

    fun setStatusBarFontSize(fontSizeSp: Float) {
        val clamped = fontSizeSp.coerceIn(11f, 18f)
        _state.value = _state.value.copy(statusBarFontSize = clamped)
        prefs?.edit()?.putFloat("status_bar_font_size", clamped)?.apply()
        refreshStatusBarLyricDisplay(relayout = true)
    }

    fun setStatusBarBgAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0f, 0.96f)
        _state.value = _state.value.copy(statusBarBgAlpha = clamped)
        prefs?.edit()?.putFloat("status_bar_bg_alpha", clamped)?.apply()
        refreshStatusBarLyricDisplay()
    }

    fun setStatusBarColorScheme(scheme: DesktopLyricColorScheme) {
        _state.value = _state.value.copy(statusBarColorScheme = scheme)
        prefs?.edit()?.putString("status_bar_color_scheme", scheme.name)?.apply()
        refreshStatusBarLyricDisplay()
    }

    fun setFlymeTickerProtocolEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(flymeTickerProtocolEnabled = enabled)
        prefs?.edit()?.putBoolean("flyme_ticker_protocol_enabled", enabled)?.apply()
    }

    fun setSystemLiveIslandNotificationEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(systemLiveIslandNotificationEnabled = enabled)
        prefs?.edit()?.putBoolean("system_live_island_notification_enabled", enabled)?.apply()
    }

    fun setMediaSessionLyricSyncEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(mediaSessionLyricSyncEnabled = enabled)
        prefs?.edit()?.putBoolean("media_session_lyric_sync_enabled", enabled)?.apply()
        if (!enabled) {
            PlaybackService.restoreMediaMetadataTitleIfNeeded()
        }
    }

    fun setXposedBroadcastEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(xposedBroadcastEnabled = enabled)
        prefs?.edit()?.putBoolean("xposed_broadcast_enabled", enabled)?.apply()
    }

    fun setStatusBarExpandedIsland(expanded: Boolean) {
        _state.value = _state.value.copy(statusBarExpandedIsland = expanded)
        refreshStatusBarLyricDisplay(relayout = true)
    }

    fun resetStatusBarSettingsToDefault(context: Context) {
        appContext = context.applicationContext
        _state.value = _state.value.copy(
            statusBarBroadcastEnabled = true,
            statusBarMode = StatusBarLyricMode.AUTO_SMART,
            statusBarOverlayStyle = StatusBarOverlayStyle.DYNAMIC_ISLAND,
            statusBarAnchor = StatusBarHorizontalAnchor.CENTER,
            statusBarTouchThrough = false,
            statusBarShowIslandMedia = true,
            statusBarWordByWord = true,
            statusBarShowTranslation = true,
            statusBarOffsetX = 0f,
            statusBarOffsetY = 4f,
            statusBarWidthDp = 246f,
            statusBarFontSize = 13.5f,
            statusBarBgAlpha = 0.88f,
            statusBarColorScheme = DesktopLyricColorScheme.ICE_BLUE,
            flymeTickerProtocolEnabled = true,
            systemLiveIslandNotificationEnabled = true,
            mediaSessionLyricSyncEnabled = true,
            xposedBroadcastEnabled = true,
            statusBarExpandedIsland = false
        )
        prefs?.edit()
            ?.putBoolean("status_bar_broadcast_enabled", true)
            ?.putString("status_bar_mode", StatusBarLyricMode.AUTO_SMART.name)
            ?.putString("status_bar_overlay_style", StatusBarOverlayStyle.DYNAMIC_ISLAND.name)
            ?.putString("status_bar_anchor", StatusBarHorizontalAnchor.CENTER.name)
            ?.putBoolean("status_bar_touch_through", false)
            ?.putBoolean("status_bar_show_island_media", true)
            ?.putBoolean("status_bar_word_by_word", true)
            ?.putBoolean("status_bar_show_translation", true)
            ?.putFloat("status_bar_offset_x", 0f)
            ?.putFloat("status_bar_offset_y", 4f)
            ?.putFloat("status_bar_width_dp", 246f)
            ?.putFloat("status_bar_font_size", 13.5f)
            ?.putFloat("status_bar_bg_alpha", 0.88f)
            ?.putString("status_bar_color_scheme", DesktopLyricColorScheme.ICE_BLUE.name)
            ?.putBoolean("flyme_ticker_protocol_enabled", true)
            ?.putBoolean("system_live_island_notification_enabled", true)
            ?.putBoolean("media_session_lyric_sync_enabled", true)
            ?.putBoolean("xposed_broadcast_enabled", true)
            ?.apply()
        syncStatusBarOverlayWindow()
        refreshStatusBarLyricDisplay(relayout = true)
    }

    /**
     * 一键发送实时测试状态栏歌词（方便用户在未播放歌曲或调试位置时立即验证状态栏、灵动岛与通知效果）
     */
    fun triggerTestStatusBarLyric(context: Context, showToast: Boolean = true) {
        val appCtx = context.applicationContext
        appContext = appCtx
        if (!_state.value.statusBarBroadcastEnabled) {
            _state.value = _state.value.copy(statusBarBroadcastEnabled = true)
            prefs?.edit()?.putBoolean("status_bar_broadcast_enabled", true)?.apply()
        }
        val demoSong = currentSongSnapshot ?: Song(
            id = "demo_breeze_island",
            title = "微风掠过星河 (Concept Edition)",
            artist = "BreezeMusic",
            album = "概念典藏试音碟",
            durationMs = 210_000L
        )
        val nowPos = currentPositionMsSnapshot.coerceAtLeast(0L)
        val demoLine = currentLyricSnapshot.lines.getOrNull(currentLineIndexSnapshot.coerceAtLeast(0))
            ?: LyricLine(
                startTimeMs = nowPos,
                durationMs = 4200L,
                text = "微风拂过耳畔 · 状态栏歌词与灵动岛已就绪",
                translation = "Breeze Music · Dynamic Island & Status Bar Lyrics Active",
                words = listOf(
                    LyricWord(nowPos, 600L, "微风"),
                    LyricWord(nowPos + 600L, 600L, "拂过"),
                    LyricWord(nowPos + 1200L, 700L, "耳畔 · "),
                    LyricWord(nowPos + 1900L, 1100L, "状态栏歌词"),
                    LyricWord(nowPos + 3000L, 1200L, "与灵动岛已就绪")
                )
            )
        if (currentSongSnapshot == null) {
            currentSongSnapshot = demoSong
            currentLyricSnapshot = LyricData(lines = listOf(demoLine))
            currentLineIndexSnapshot = 0
            currentPositionMsSnapshot = nowPos
            lastPositionSyncRealtimeMs = android.os.SystemClock.elapsedRealtime()
            isPlayingSnapshot = true
        }
        lastLineIndex = -999
        onLyricLineUpdated(
            context = appCtx,
            song = demoSong,
            lineIndex = 0,
            line = demoLine,
            isPlaying = true
        )
        syncStatusBarOverlayWindow()
        refreshStatusBarLyricDisplay(relayout = true)
        if (showToast) {
            Toast.makeText(
                context,
                "已推送测试歌词至顶部状态栏 / 灵动岛与系统通知协议",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun refreshStatusBarLyricDisplay(relayout: Boolean = false) {
        mainHandler.post {
            syncStatusBarOverlayWindow()
            if (relayout) {
                updateStatusBarWindowLayoutParams()
                statusBarOverlayView?.requestLayout()
            }
            statusBarOverlayView?.invalidate()
        }
    }

    // =========================================================================
    // 顶部「椒盐状态栏悬浮歌词 / 灵动岛音乐胶囊」系统悬浮窗生命周期管理
    // =========================================================================

    private fun syncStatusBarOverlayWindow() {
        val ctx = appContext ?: return
        mainHandler.post {
            val st = _state.value
            val canOverlay = canDrawSystemOverlays(ctx)
            val hasActiveContent = currentSongSnapshot != null || st.lastBroadcastedLine.isNotBlank()
            val shouldShow = shouldShowTopStatusBarOverlay(st) && canOverlay && hasActiveContent

            if (!shouldShow) {
                removeStatusBarOverlayView()
                if (_state.value.statusBarOverlayActive) {
                    _state.value = _state.value.copy(statusBarOverlayActive = false)
                }
                return@post
            }

            if (statusBarOverlayView != null) {
                if (!_state.value.statusBarOverlayActive) {
                    _state.value = _state.value.copy(statusBarOverlayActive = true)
                }
                updateStatusBarWindowLayoutParams()
                statusBarOverlayView?.invalidate()
                return@post
            }

            runCatching {
                val wm = (ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return@runCatching
                windowManager = wm
                val lp = buildStatusBarWindowLayoutParams(ctx, st)
                val view = NativeStatusBarIslandView(
                    context = ctx,
                    onDragDeltaDp = { dxDp, dyDp ->
                        setStatusBarOffsetX(_state.value.statusBarOffsetX + dxDp)
                        setStatusBarOffsetY(_state.value.statusBarOffsetY + dyDp)
                    }
                )
                wm.addView(view, lp)
                statusBarOverlayView = view
                statusBarOverlayParams = lp
                _state.value = _state.value.copy(statusBarOverlayActive = true)
            }.onFailure {
                removeStatusBarOverlayView()
                _state.value = _state.value.copy(statusBarOverlayActive = false)
            }
        }
    }

    private fun buildStatusBarWindowLayoutParams(
        ctx: Context,
        st: SystemLyricState
    ): WindowManager.LayoutParams {
        val dm = ctx.resources.displayMetrics
        val density = dm.density
        val screenW = dm.widthPixels.coerceAtLeast(720)
        val style = effectiveOverlayStyle(st)
        val isExpanded = st.statusBarExpandedIsland && style == StatusBarOverlayStyle.DYNAMIC_ISLAND && !st.statusBarTouchThrough

        val targetW = if (isExpanded) {
            (screenW * 0.92f).roundToInt().coerceIn((280 * density).roundToInt(), screenW - (16 * density).roundToInt())
        } else {
            (st.statusBarWidthDp * density).roundToInt().coerceIn((130 * density).roundToInt(), screenW - (12 * density).roundToInt())
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val effectiveFlags = if (st.statusBarTouchThrough) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags
        }

        val horizontalGravity = when (st.statusBarAnchor) {
            StatusBarHorizontalAnchor.LEFT_PUNCH_HOLE -> Gravity.START
            StatusBarHorizontalAnchor.RIGHT_STATUS -> Gravity.END
            StatusBarHorizontalAnchor.CENTER -> Gravity.CENTER_HORIZONTAL
        }

        return WindowManager.LayoutParams(
            targetW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            effectiveFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or horizontalGravity
            x = if (isExpanded) 0 else (st.statusBarOffsetX * density).roundToInt()
            y = (st.statusBarOffsetY * density).roundToInt().coerceAtLeast(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
    }

    private fun updateStatusBarWindowLayoutParams() {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val view = statusBarOverlayView ?: return
        val newLp = buildStatusBarWindowLayoutParams(ctx, _state.value)
        statusBarOverlayParams = newLp
        runCatching { wm.updateViewLayout(view, newLp) }
    }

    private fun removeStatusBarOverlayView() {
        val wm = windowManager
        statusBarOverlayView?.let { v ->
            runCatching { wm?.removeViewImmediate(v) }
        }
        statusBarOverlayView = null
        statusBarOverlayParams = null
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

    // =========================================================================
    // 多协议状态栏歌词推送核心引擎 (Flyme Ticker / 澎湃焦点通知 / 流体云 / 原子岛 / MediaSession / Xposed)
    // =========================================================================

    private fun ensureStatusBarNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (nm.getNotificationChannel(STATUS_BAR_LYRIC_CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        STATUS_BAR_LYRIC_CHANNEL_ID,
                        "状态栏歌词与实况灵动胶囊",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "用于魅族 Flyme 状态栏 Ticker 协议、小米澎湃焦点通知、OPPO 流体云及 vivo 原子岛实时歌词同步"
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                        setSound(null, null)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }
    }

    /**
     * 构建小米 HyperOS 澎湃焦点通知 / 澎湃岛 (`miui.focus.param`) JSON 负载
     */
    internal fun buildHyperOsFocusParamJson(
        songTitle: String,
        artist: String,
        lyricLine: String,
        isPlaying: Boolean
    ): String {
        return runCatching {
            val root = JSONObject()
            val paramV2 = JSONObject().apply {
                put("protocol", 1)
                put("scene", "music")
                put("ticker", lyricLine)
                put("title", lyricLine)
                put("content", "$songTitle - $artist")
                put("enableFloat", false)
                put("updatable", true)
                put("aodTitle", lyricLine)
                put("islandProps", JSONObject().apply {
                    put("islandPriority", 1)
                    put("dismissIsland", !isPlaying)
                    put("title", lyricLine)
                    put("subTitle", songTitle)
                })
            }
            root.put("param_v2", paramV2)
            root.toString()
        }.getOrDefault("{}")
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
        val cleanSub = line.translation?.trim().orEmpty()
        _state.value = _state.value.copy(
            lastBroadcastedLine = cleanText,
            lastBroadcastedSubLine = cleanSub
        )

        ConceptMusicWidgetUpdater.updateState(
            context = context,
            songTitle = song.title,
            artist = song.artist,
            lyricLine = cleanText,
            isPlaying = isPlaying
        )

        val st = _state.value
        if (!st.statusBarBroadcastEnabled) return

        // 1. 刷新顶部状态栏/灵动岛悬浮窗
        if (shouldShowTopStatusBarOverlay(st)) {
            mainHandler.post {
                syncStatusBarOverlayWindow()
                statusBarOverlayView?.onPlaybackUpdated()
            }
        }

        val appCtx = context.applicationContext
        val durationMs = line.durationMs.coerceAtLeast(1500L)

        // 2. 同步至 PlaybackService 的 MediaSession 元数据（支持车载蓝牙、系统锁屏、OriginOS 原子岛与 ColorOS 流体云）
        if (st.mediaSessionLyricSyncEnabled) {
            PlaybackService.syncLiveLyricToMediaSession(
                song = song,
                lyricLine = cleanText,
                translationLine = cleanSub
            )
        }

        // 3. 发送 Flyme 原生 Notification Ticker (0x01000000 | 0x02000000) 及 HyperOS / ColorOS / OriginOS 实况通知
        val shouldPostNotificationTicker = st.flymeTickerProtocolEnabled ||
            st.systemLiveIslandNotificationEnabled ||
            st.statusBarMode == StatusBarLyricMode.SYSTEM_TICKER_ISLAND ||
            st.statusBarMode == StatusBarLyricMode.AUTO_SMART
        if (shouldPostNotificationTicker) {
            postSystemStatusBarLyricNotification(
                context = appCtx,
                song = song,
                lyricLine = cleanText,
                subLine = cleanSub,
                durationMs = durationMs,
                isPlaying = isPlaying,
                st = st
            )
        }

        // 4. 发送 Xposed / Root 状态栏歌词生态广播 (LyricGetter / SuperLyric / StatusBarLyric / EventLyric)
        val shouldSendHookBroadcast = st.xposedBroadcastEnabled ||
            st.statusBarMode == StatusBarLyricMode.HOOK_BROADCAST ||
            st.statusBarMode == StatusBarLyricMode.AUTO_SMART
        if (shouldSendHookBroadcast) {
            sendMultiEcosystemLyricBroadcasts(
                appCtx = appCtx,
                song = song,
                cleanText = cleanText,
                cleanSub = cleanSub,
                durationMs = durationMs
            )
        }
    }

    private fun postSystemStatusBarLyricNotification(
        context: Context,
        song: Song,
        lyricLine: String,
        subLine: String,
        durationMs: Long,
        isPlaying: Boolean,
        st: SystemLyricState
    ) {
        runCatching {
            ensureStatusBarNotificationChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                2026,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val extras = android.os.Bundle().apply {
                // 魅族 Flyme 官方状态栏歌词开放平台字段
                putInt("ticker_icon", R.drawable.ic_launcher_concept)
                putBoolean("ticker_icon_switch", false)
                putInt("flyme.notification.lyric_duration", durationMs.toInt())
                putString("lyric", lyricLine)

                // Android 16 Promoted Ongoing / ColorOS 流体云 / OriginOS 原子岛 / 荣耀灵动胶囊增强字段
                if (st.systemLiveIslandNotificationEnabled) {
                    putBoolean("android.requestPromotedOngoing", true)
                    putString("oplus.ongoing.lyric", lyricLine)
                    putString("vivo.originos.atomic.lyric", lyricLine)
                    putString(
                        "miui.focus.param",
                        buildHyperOsFocusParamJson(song.title, song.artist, lyricLine, isPlaying)
                    )
                    putString("miui.focus.ticker", lyricLine)
                }
            }

            val subtitleText = if (subLine.isNotBlank() && st.statusBarShowTranslation) {
                "$subLine · ${song.title} - ${song.artist}"
            } else {
                "${song.title} - ${song.artist}"
            }

            val notification = NotificationCompat.Builder(context, STATUS_BAR_LYRIC_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_concept)
                .setTicker(lyricLine)
                .setContentTitle(lyricLine)
                .setContentText(subtitleText)
                .setSubText(song.title)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentPendingIntent)
                .addExtras(extras)
                .build()

            notification.tickerText = lyricLine
            if (st.flymeTickerProtocolEnabled) {
                notification.flags = notification.flags or FLAG_ALWAYS_SHOW_TICKER or FLAG_ONLY_UPDATE_TICKER
            }
            nm.notify(STATUS_BAR_LYRIC_NOTIFICATION_ID, notification)
        }
    }

    private fun sendMultiEcosystemLyricBroadcasts(
        appCtx: Context,
        song: Song,
        cleanText: String,
        cleanSub: String,
        durationMs: Long
    ) {
        runCatching {
            val pkg = appCtx.packageName
            val delaySec = (durationMs / 1000L).toInt().coerceAtLeast(1)

            // 1. 本应用标准状态栏歌词广播
            val lyricIntent = Intent("com.concept.music.STATUS_BAR_LYRIC").apply {
                putExtra("lyric", cleanText)
                putExtra("translation", cleanSub)
                putExtra("song_title", song.title)
                putExtra("artist", song.artist)
                putExtra("album", song.album)
                putExtra("duration", durationMs)
                putExtra("pkg", pkg)
            }
            appCtx.sendBroadcast(lyricIntent)

            // 2. LyricGetter / EventLyric / SuperLyric 通用模块标准广播协议
            val lyricGetterIntent = Intent("cn.lyric.getter.api.LYRIC").apply {
                putExtra("type", "update")
                putExtra("lyric", cleanText)
                putExtra("subLyric", cleanSub)
                putExtra("packageName", pkg)
                putExtra("delay", delaySec)
                putExtra("extra", song.title)
            }
            appCtx.sendBroadcast(lyricGetterIntent)

            // 3. StatusBarLyric / Flyme 兼容广播
            val flymeIntent = Intent("com.meizu.flyme.service.StatusBarLyric").apply {
                putExtra("ticker", cleanText)
                putExtra("lyric", cleanText)
                putExtra("duration", durationMs.toInt())
                putExtra("sender_package", pkg)
            }
            appCtx.sendBroadcast(flymeIntent)

            val aospTickerIntent = Intent("statusbar.lyric.update").apply {
                putExtra("lyric", cleanText)
                putExtra("pkg", pkg)
                putExtra("duration", durationMs)
            }
            appCtx.sendBroadcast(aospTickerIntent)
        }
    }

    fun sendStopBroadcast(context: Context) {
        lastLineIndex = -1
        _state.value = _state.value.copy(
            lastBroadcastedLine = "",
            lastBroadcastedSubLine = "",
            statusBarExpandedIsland = false
        )
        PlaybackService.restoreMediaMetadataTitleIfNeeded()
        runCatching {
            val appCtx = context.applicationContext
            val pkg = appCtx.packageName

            // 取消状态栏 Ticker 通知
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(STATUS_BAR_LYRIC_NOTIFICATION_ID)

            val stopIntent = Intent("com.concept.music.STATUS_BAR_LYRIC").apply {
                putExtra("lyric", "")
                putExtra("stop", true)
                putExtra("pkg", pkg)
            }
            appCtx.sendBroadcast(stopIntent)

            val getterStopIntent = Intent("cn.lyric.getter.api.LYRIC").apply {
                putExtra("type", "stop")
                putExtra("lyric", "")
                putExtra("packageName", pkg)
            }
            appCtx.sendBroadcast(getterStopIntent)
        }
        syncStatusBarOverlayWindow()
    }
}

/**
 * 顶部「灵动岛音乐胶囊 / 椒盐风状态栏悬浮歌词」原生硬件加速视图：
 * - 支持两种形态切换：
 *   1. `DYNAMIC_ISLAND`：iPhone 灵动岛药丸胶囊（左侧微缩旋转黑胶 + 中部 KRC 逐字流光歌词 + 右侧 4 柱动态律动音频波纹），
 *      点击可平滑展开为大尺寸灵动岛控播面板（含封面、歌名、专辑、双语歌词、上一首/播放暂停/下一首/进入主界面）；
 *   2. `SALT_PURE_STATUS_BAR`：椒盐音乐 (`SaltPlayerSource`) 同款极简状态栏歌词字条，贴合状态栏高度，支持完全透明背景与物理点击穿透。
 */
private class NativeStatusBarIslandView(
    context: Context,
    private val onDragDeltaDp: (dxDp: Float, dyDp: Float) -> Unit
) : View(context) {
    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var isDragging = false
    private var spinAngle = 0f
    private var wavePhase = 0f

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.1f * d
    }
    private val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val hitRegions = mutableListOf<Pair<RectF, () -> Unit>>()

    private var lastExpandedState: Boolean? = null
    private var lastStyleState: StatusBarOverlayStyle? = null
    private var lastFontSizeState: Float = -1f

    fun onPlaybackUpdated() {
        val st = SystemLyricBridge.state.value
        val curStyle = SystemLyricBridge.effectiveOverlayStyle(st)
        if (st.statusBarExpandedIsland != lastExpandedState ||
            curStyle != lastStyleState ||
            st.statusBarFontSize != lastFontSizeState
        ) {
            lastExpandedState = st.statusBarExpandedIsland
            lastStyleState = curStyle
            lastFontSizeState = st.statusBarFontSize
            requestLayout()
        }
        postInvalidateOnAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val st = SystemLyricBridge.state.value
        val style = SystemLyricBridge.effectiveOverlayStyle(st)
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast((140 * d).roundToInt())
        val isExpanded = st.statusBarExpandedIsland && style == StatusBarOverlayStyle.DYNAMIC_ISLAND && !st.statusBarTouchThrough
        val h = when {
            isExpanded -> (108f * d).roundToInt()
            style == StatusBarOverlayStyle.DYNAMIC_ISLAND -> {
                val baseH = if (st.statusBarShowTranslation && st.lastBroadcastedSubLine.isNotBlank()) 40f else 33f
                (baseH * d).roundToInt()
            }
            else -> {
                val fontPx = st.statusBarFontSize * sp
                val extra = if (st.statusBarShowTranslation && st.lastBroadcastedSubLine.isNotBlank()) fontPx * 0.9f else 0f
                (fontPx * 1.45f + extra + 8f * d).roundToInt().coerceAtLeast((26f * d).roundToInt())
            }
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        hitRegions.clear()

        val st = SystemLyricBridge.state.value
        val style = SystemLyricBridge.effectiveOverlayStyle(st)
        val scheme = st.statusBarColorScheme
        val startColor = scheme.playedStartHex.toInt()
        val endColor = scheme.playedEndHex.toInt()
        val unplayedColor = (scheme.unplayedHex.toInt() and 0x00FFFFFF) or (0xE6 shl 24)
        val shadowColor = scheme.strokeHex.toInt()

        val w = width.toFloat()
        val h = height.toFloat()
        val isExpanded = st.statusBarExpandedIsland && style == StatusBarOverlayStyle.DYNAMIC_ISLAND && !st.statusBarTouchThrough

        val song = SystemLyricBridge.currentSongSnapshot
        val lines = SystemLyricBridge.currentLyricSnapshot.lines
        val safeIdx = SystemLyricBridge.currentLineIndexSnapshot.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        val currentLine = lines.getOrNull(safeIdx)
        val mainText = currentLine?.text?.takeIf { it.isNotBlank() }
            ?: st.lastBroadcastedLine.ifBlank {
                if (song != null) "${song.title} - ${song.artist}" else "微风音乐 · 状态栏歌词就绪"
            }
        val subText = if (st.statusBarShowTranslation) {
            currentLine?.translation?.takeIf { it.isNotBlank() } ?: st.lastBroadcastedSubLine
        } else ""

        val basePos = SystemLyricBridge.currentPositionMsSnapshot
        val elapsed = if (SystemLyricBridge.isPlayingSnapshot && SystemLyricBridge.lastPositionSyncRealtimeMs > 0L) {
            (android.os.SystemClock.elapsedRealtime() - SystemLyricBridge.lastPositionSyncRealtimeMs).coerceIn(0L, 250L)
        } else 0L
        val livePosMs = basePos + (elapsed * SystemLyricBridge.playbackSpeedSnapshot).toLong()

        if (isExpanded) {
            drawExpandedDynamicIsland(
                canvas = canvas,
                w = w,
                h = h,
                song = song,
                currentLine = currentLine,
                mainText = mainText,
                subText = subText,
                livePosMs = livePosMs,
                startColor = startColor,
                endColor = endColor,
                unplayedColor = unplayedColor,
                shadowColor = shadowColor,
                st = st
            )
        } else if (style == StatusBarOverlayStyle.DYNAMIC_ISLAND) {
            drawCollapsedDynamicIsland(
                canvas = canvas,
                w = w,
                h = h,
                currentLine = currentLine,
                mainText = mainText,
                subText = subText,
                livePosMs = livePosMs,
                startColor = startColor,
                endColor = endColor,
                unplayedColor = unplayedColor,
                shadowColor = shadowColor,
                st = st
            )
        } else {
            drawSaltPureStatusBarText(
                canvas = canvas,
                w = w,
                h = h,
                currentLine = currentLine,
                mainText = mainText,
                subText = subText,
                livePosMs = livePosMs,
                startColor = startColor,
                endColor = endColor,
                unplayedColor = unplayedColor,
                shadowColor = shadowColor,
                st = st
            )
        }

        if (SystemLyricBridge.isPlayingSnapshot) {
            spinAngle = (spinAngle + 2.4f) % 360f
            wavePhase = (wavePhase + 0.22f) % 6.283f
            postInvalidateDelayed(36L)
        }
    }

    private fun drawCollapsedDynamicIsland(
        canvas: Canvas,
        w: Float,
        h: Float,
        currentLine: LyricLine?,
        mainText: String,
        subText: String,
        livePosMs: Long,
        startColor: Int,
        endColor: Int,
        unplayedColor: Int,
        shadowColor: Int,
        st: SystemLyricState
    ) {
        val pad = 1.5f * d
        rect.set(pad, pad, w - pad, h - pad)
        val radius = rect.height() / 2f
        val alphaInt = (st.statusBarBgAlpha.coerceIn(0.18f, 0.96f) * 255).roundToInt()
        pillPaint.color = (alphaInt shl 24) or 0x060911
        canvas.drawRoundRect(rect, radius, radius, pillPaint)

        borderPaint.color = (0x66 shl 24) or (startColor and 0x00FFFFFF)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        var textLeft = rect.left + 11f * d
        var textRight = rect.right - 11f * d

        if (st.statusBarShowIslandMedia) {
            // 左侧旋转黑胶微缩盘
            val discR = (h * 0.34f).coerceIn(8.5f * d, 12.5f * d)
            val discCx = rect.left + discR + 6.5f * d
            val discCy = rect.centerY()
            iconPaint.color = 0xFF141C2B.toInt()
            canvas.drawCircle(discCx, discCy, discR, iconPaint)
            borderPaint.color = (0x90 shl 24) or (startColor and 0x00FFFFFF)
            canvas.drawCircle(discCx, discCy, discR * 0.76f, borderPaint)
            iconPaint.color = startColor
            canvas.drawCircle(discCx, discCy, discR * 0.36f, iconPaint)
            val rad = Math.toRadians(spinAngle.toDouble())
            val dotX = discCx + (discR * 0.62f * cos(rad)).toFloat()
            val dotY = discCy + (discR * 0.62f * sin(rad)).toFloat()
            iconPaint.color = endColor
            canvas.drawCircle(dotX, dotY, 1.5f * d, iconPaint)
            textLeft = discCx + discR + 7f * d

            // 右侧 4 柱律动声波
            val waveRight = rect.right - 9f * d
            val barW = 2.1f * d
            val barGap = 2.3f * d
            iconPaint.strokeWidth = barW
            for (i in 0 until 4) {
                val bx = waveRight - (3 - i) * (barW + barGap)
                val amp = if (SystemLyricBridge.isPlayingSnapshot) {
                    (0.35f + 0.65f * abs(sin(wavePhase + i * 0.9f)))
                } else 0.25f
                val halfBarH = (h * 0.26f * amp).coerceAtLeast(2.2f * d)
                iconPaint.color = if (i % 2 == 0) startColor else endColor
                canvas.drawLine(bx, discCy - halfBarH, bx, discCy + halfBarH, iconPaint)
            }
            textRight = waveRight - 4 * (barW + barGap) - 5f * d
        }

        val availW = (textRight - textLeft).coerceAtLeast(60f * d)
        val mainSizePx = st.statusBarFontSize * sp
        val hasSub = subText.isNotBlank()
        val mainBaselineY = if (hasSub) {
            rect.centerY() - 1.5f * d
        } else {
            rect.centerY() + mainSizePx * 0.35f
        }

        drawFittedStatusBarKaraokeText(
            canvas = canvas,
            line = currentLine,
            rawText = mainText,
            leftX = textLeft,
            availW = availW,
            baselineY = mainBaselineY,
            fontSizePx = mainSizePx,
            posMs = if (st.statusBarWordByWord) livePosMs else -1L,
            startColor = startColor,
            endColor = endColor,
            unplayedColor = unplayedColor,
            shadowColor = shadowColor,
            centerInAvail = true
        )

        if (hasSub) {
            subPaint.textSize = (st.statusBarFontSize - 3f).coerceAtLeast(9.5f) * sp
            subPaint.color = (0xCC shl 24) or (unplayedColor and 0x00FFFFFF)
            val fittedSub = ellipsizeStatusBarText(subText, subPaint, availW)
            val subW = subPaint.measureText(fittedSub)
            val subX = textLeft + ((availW - subW) / 2f).coerceAtLeast(0f)
            canvas.drawText(fittedSub, subX, rect.bottom - 4.5f * d, subPaint)
        }
    }

    private fun drawSaltPureStatusBarText(
        canvas: Canvas,
        w: Float,
        h: Float,
        currentLine: LyricLine?,
        mainText: String,
        subText: String,
        livePosMs: Long,
        startColor: Int,
        endColor: Int,
        unplayedColor: Int,
        shadowColor: Int,
        st: SystemLyricState
    ) {
        val pad = 1f * d
        rect.set(pad, pad, w - pad, h - pad)
        if (st.statusBarBgAlpha > 0.04f) {
            val alphaInt = (st.statusBarBgAlpha * 255).roundToInt().coerceIn(0, 240)
            pillPaint.color = (alphaInt shl 24) or 0x070B13
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, pillPaint)
        }

        val leftX = 8f * d
        val availW = (w - 16f * d).coerceAtLeast(60f * d)
        val mainSizePx = st.statusBarFontSize * sp
        val hasSub = subText.isNotBlank()
        val mainY = if (hasSub) {
            rect.centerY() - 1f * d
        } else {
            rect.centerY() + mainSizePx * 0.36f
        }
        val centerText = st.statusBarAnchor == StatusBarHorizontalAnchor.CENTER

        drawFittedStatusBarKaraokeText(
            canvas = canvas,
            line = currentLine,
            rawText = mainText,
            leftX = leftX,
            availW = availW,
            baselineY = mainY,
            fontSizePx = mainSizePx,
            posMs = if (st.statusBarWordByWord) livePosMs else -1L,
            startColor = startColor,
            endColor = endColor,
            unplayedColor = unplayedColor,
            shadowColor = shadowColor,
            centerInAvail = centerText
        )

        if (hasSub) {
            subPaint.textSize = (st.statusBarFontSize - 3f).coerceAtLeast(9.5f) * sp
            subPaint.color = (0xD8 shl 24) or (unplayedColor and 0x00FFFFFF)
            subPaint.setShadowLayer(3f * d, 0f, 1f * d, shadowColor)
            val fittedSub = ellipsizeStatusBarText(subText, subPaint, availW)
            val subW = subPaint.measureText(fittedSub)
            val subX = if (centerText) leftX + ((availW - subW) / 2f).coerceAtLeast(0f) else leftX
            canvas.drawText(fittedSub, subX, rect.bottom - 3.5f * d, subPaint)
        }
    }

    private fun drawExpandedDynamicIsland(
        canvas: Canvas,
        w: Float,
        h: Float,
        song: Song?,
        currentLine: LyricLine?,
        mainText: String,
        subText: String,
        livePosMs: Long,
        startColor: Int,
        endColor: Int,
        unplayedColor: Int,
        shadowColor: Int,
        st: SystemLyricState
    ) {
        val pad = 2f * d
        rect.set(pad, pad, w - pad, h - pad)
        val cornerR = 26f * d
        pillPaint.color = 0xF4070B14.toInt()
        canvas.drawRoundRect(rect, cornerR, cornerR, pillPaint)
        borderPaint.color = (0x88 shl 24) or (startColor and 0x00FFFFFF)
        canvas.drawRoundRect(rect, cornerR, cornerR, borderPaint)

        // 顶部歌曲信息与右侧控播按钮
        val titleText = song?.title ?: "微风音乐"
        val artistAlbum = if (song != null) {
            if (song.album.isNotBlank()) "${song.artist} · 《${song.album}》" else song.artist
        } else "酷狗概念版 · 灵动岛控制舱"

        lyricPaint.shader = null
        lyricPaint.color = 0xFFFFFFFF.toInt()
        lyricPaint.textSize = 13f * sp
        val maxTitleW = (w - 170f * d).coerceAtLeast(90f * d)
        canvas.drawText(ellipsizeStatusBarText(titleText, lyricPaint, maxTitleW), 16f * d, 24f * d, lyricPaint)

        subPaint.color = (0xB8 shl 24) or (startColor and 0x00FFFFFF)
        subPaint.textSize = 10.5f * sp
        canvas.drawText(ellipsizeStatusBarText(artistAlbum, subPaint, maxTitleW), 16f * d, 40f * d, subPaint)

        // 右上角：上一首 / 播放暂停 / 下一首 / 收起
        val btnY = 12f * d
        val btnH = 26f * d
        val collapseRect = RectF(w - 38f * d, btnY, w - 12f * d, btnY + btnH)
        val nextRect = RectF(w - 70f * d, btnY, w - 42f * d, btnY + btnH)
        val playRect = RectF(w - 104f * d, btnY, w - 74f * d, btnY + btnH)
        val prevRect = RectF(w - 136f * d, btnY, w - 108f * d, btnY + btnH)

        pillPaint.color = 0x24FFFFFF
        canvas.drawRoundRect(prevRect, btnH / 2f, btnH / 2f, pillPaint)
        canvas.drawRoundRect(nextRect, btnH / 2f, btnH / 2f, pillPaint)
        canvas.drawRoundRect(collapseRect, btnH / 2f, btnH / 2f, pillPaint)
        pillPaint.color = startColor
        canvas.drawRoundRect(playRect, btnH / 2f, btnH / 2f, pillPaint)

        lyricPaint.textSize = 11.5f * sp
        lyricPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText("⏮", prevRect.left + 6f * d, btnY + 17.5f * d, lyricPaint)
        canvas.drawText("⏭", nextRect.left + 6f * d, btnY + 17.5f * d, lyricPaint)
        canvas.drawText("▲", collapseRect.left + 7.5f * d, btnY + 17.5f * d, lyricPaint)

        lyricPaint.color = 0xFF06101C.toInt()
        val playSym = if (SystemLyricBridge.isPlayingSnapshot) "❚❚" else "▶"
        canvas.drawText(playSym, playRect.left + 7.5f * d, btnY + 17.5f * d, lyricPaint)

        hitRegions.add(prevRect to { SystemLyricBridge.onPreviousCallback?.invoke() })
        hitRegions.add(playRect to { SystemLyricBridge.onTogglePlayCallback?.invoke() })
        hitRegions.add(nextRect to { SystemLyricBridge.onNextCallback?.invoke() })
        hitRegions.add(collapseRect to { SystemLyricBridge.setStatusBarExpandedIsland(false) })

        // 下半部：实时 KRC 逐字流光歌词 + 译文
        val lyricAvailW = (w - 32f * d).coerceAtLeast(120f * d)
        drawFittedStatusBarKaraokeText(
            canvas = canvas,
            line = currentLine,
            rawText = mainText,
            leftX = 16f * d,
            availW = lyricAvailW,
            baselineY = 69f * d,
            fontSizePx = (st.statusBarFontSize + 1.5f).coerceAtMost(17f) * sp,
            posMs = if (st.statusBarWordByWord) livePosMs else -1L,
            startColor = startColor,
            endColor = endColor,
            unplayedColor = unplayedColor,
            shadowColor = shadowColor,
            centerInAvail = false
        )

        val bottomHint = subText.ifBlank { "点击左侧歌名可快速返回微风音乐主界面" }
        subPaint.color = (0xCC shl 24) or (unplayedColor and 0x00FFFFFF)
        subPaint.textSize = 11f * sp
        canvas.drawText(ellipsizeStatusBarText(bottomHint, subPaint, lyricAvailW), 16f * d, 91f * d, subPaint)

        val openAppRegion = RectF(12f * d, 8f * d, w - 142f * d, h - 8f * d)
        hitRegions.add(openAppRegion to {
            SystemLyricBridge.setStatusBarExpandedIsland(false)
            runCatching {
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
            }
        })
    }

    private fun drawFittedStatusBarKaraokeText(
        canvas: Canvas,
        line: LyricLine?,
        rawText: String,
        leftX: Float,
        availW: Float,
        baselineY: Float,
        fontSizePx: Float,
        posMs: Long,
        startColor: Int,
        endColor: Int,
        unplayedColor: Int,
        shadowColor: Int,
        centerInAvail: Boolean
    ) {
        lyricPaint.textSize = fontSizePx
        lyricPaint.shader = null
        lyricPaint.setShadowLayer(3.5f * d, 0f, 1.2f * d, shadowColor)

        val fullW = lyricPaint.measureText(rawText).coerceAtLeast(1f)
        val progress = if (line != null && posMs >= 0L) computeLineProgress(line, posMs) else 1f

        canvas.save()
        canvas.clipRect(leftX, 0f, leftX + availW, height.toFloat())

        // 若单行歌词超出胶囊宽度，随演唱进度平滑水平滚动显示完整歌词，绝不死板截断！
        val drawX = if (fullW <= availW) {
            if (centerInAvail) leftX + (availW - fullW) / 2f else leftX
        } else {
            val maxScroll = fullW - availW
            val scrollOffset = ((progress - 0.25f).coerceIn(0f, 0.65f) / 0.65f) * maxScroll
            leftX - scrollOffset
        }

        if (line != null && posMs >= 0L) {
            val clampP = progress.coerceIn(0f, 1f)
            if (clampP <= 0.005f) {
                lyricPaint.color = unplayedColor
            } else if (clampP >= 0.995f) {
                lyricPaint.color = startColor
            } else {
                val edgeLeft = (clampP - 0.05f).coerceAtLeast(0f)
                val edgeRight = (clampP + 0.05f).coerceAtMost(1f)
                lyricPaint.shader = LinearGradient(
                    drawX, baselineY, drawX + fullW, baselineY,
                    intArrayOf(startColor, endColor, unplayedColor, unplayedColor),
                    floatArrayOf(0f, edgeLeft, edgeRight, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        } else {
            lyricPaint.color = startColor
        }

        canvas.drawText(rawText, drawX, baselineY, lyricPaint)
        lyricPaint.shader = null
        canvas.restore()
    }

    private fun computeLineProgress(line: LyricLine, posMs: Long): Float {
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

    private fun ellipsizeStatusBarText(text: String, paint: Paint, maxW: Float): String {
        if (paint.measureText(text) <= maxW) return text
        var end = text.length
        while (end > 3 && paint.measureText(text, 0, end) + 14f > maxW) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val st = SystemLyricBridge.state.value
        if (st.statusBarTouchThrough) return false
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
                    onDragDeltaDp(dx / d, dy / d)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                } else {
                    val x = event.x
                    val y = event.y
                    val matched = hitRegions.firstOrNull { (r, _) -> r.contains(x, y) }
                    if (matched != null) {
                        matched.second.invoke()
                    } else if (SystemLyricBridge.effectiveOverlayStyle(st) == StatusBarOverlayStyle.DYNAMIC_ISLAND) {
                        SystemLyricBridge.setStatusBarExpandedIsland(!st.statusBarExpandedIsland)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
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
