package com.concept.music.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 项目中声明、获取或调用的系统权限与硬件能力枚举：
 * 公开透明展示每项权限的获取用途、系统权限标识、隐私保护承诺，以及关闭开关后对具体功能的影响。
 */
enum class AppPermissionType(
    val key: String,
    val title: String,
    val badge: String,
    val manifestPermissionName: String,
    val purposeDescription: String,
    val privacyCommitment: String,
    val disabledImpactDescription: String
) {
    NETWORK_ACCESS(
        key = "perm_network_access",
        title = "网络访问与网络状态检测",
        badge = "核心通信",
        manifestPermissionName = "INTERNET · ACCESS_NETWORK_STATE",
        purposeDescription = "用于端侧直连酷狗概念版接口加载在线音频流、KRC 逐字歌词、高清专辑封面、歌单评论与账号登录鉴权；同时识别 Wi-Fi 与移动数据网络状态，以执行移动网络省流策略及检查 GitHub 版本更新。",
        privacyCommitment = "采用纯端侧直连架构，无任何第三方中转服务器，绝不上传您的个人隐私数据。",
        disabledImpactDescription = "关闭后将进入应用内离线隔离模式：无法播放未缓存的在线音乐、无法搜索或刷新推荐、无法加载评论与检查更新。"
    ),
    NOTIFICATION_MEDIA(
        key = "perm_notification_media",
        title = "通知栏媒体控制与前台播放服务",
        badge = "系统通知",
        manifestPermissionName = "POST_NOTIFICATIONS · FOREGROUND_SERVICE_MEDIA_PLAYBACK",
        purposeDescription = "用于在系统通知栏与锁屏界面展示当前播放曲目、封面、进度及上下首切歌按钮，并维持 AndroidX Media3 后台音频播放服务稳定运行。",
        privacyCommitment = "仅用于展示实时音乐播放控制器，绝不推送任何商业广告、营销弹窗或无关消息。",
        disabledImpactDescription = "关闭后将停用通知栏媒体通知扩展，且在清理最近任务卡片后不再保持后台驻留播放。"
    ),
    SYSTEM_OVERLAY(
        key = "perm_system_overlay",
        title = "跨应用全局悬浮窗（桌面歌词）",
        badge = "桌面歌词",
        manifestPermissionName = "SYSTEM_ALERT_WINDOW",
        purposeDescription = "用于在退回手机桌面或切换至其他应用时，在屏幕上方常驻渲染可拖拽、可调色及支持穿透锁定的「KRC 逐字桌面歌词」。",
        privacyCommitment = "仅在您主动开启桌面歌词时绘制歌词视图，绝不读取屏幕内容或记录任何输入信息。",
        disabledImpactDescription = "关闭后将立即隐藏并禁止开启跨应用全局桌面悬浮歌词与内嵌桌面歌词胶囊。"
    ),
    STORAGE_EXPORT(
        key = "perm_storage_export",
        title = "音乐下载、歌词保存与相册导出",
        badge = "存储导出",
        manifestPermissionName = "WRITE_EXTERNAL_STORAGE · READ_MEDIA_AUDIO · MANAGE_EXTERNAL_STORAGE · MediaStore",
        purposeDescription = "用于将下载的音乐音频文件（含内嵌歌曲信息、高清封面与歌词）及独立 .lrc 歌词文件保存到自定义手机存储目录，并在点击「保存海报」或「保存二维码」时将 PNG 图片导出至系统相册。",
        privacyCommitment = "仅在您主动发起音乐下载、歌词保存或海报导出时向目标路径写入文件，绝不扫描或上传您手机中的私人照片与无关文件。",
        disabledImpactDescription = "关闭后将无法下载保存音乐与独立 .lrc 歌词文件，也无法保存歌词海报或登录二维码至手机存储。"
    ),
    BACKGROUND_GUARDIAN(
        key = "perm_background_guardian",
        title = "后台保活、唤醒锁与 VIP 自动守护",
        badge = "后台守护",
        manifestPermissionName = "WAKE_LOCK · REQUEST_IGNORE_BATTERY_OPTIMIZATIONS · RECEIVE_BOOT_COMPLETED",
        purposeDescription = "用于在手机息屏状态下保持音频解码不断流，并通过系统 JobScheduler 定时在后台静默完成概念版每日 VIP 签到与权益续期。",
        privacyCommitment = "后台任务仅在满足网络条件时轻量触发概念版 VIP 签到接口，不进行任何无关后台唤醒。",
        disabledImpactDescription = "关闭后将停用每日自动领 VIP 后台守护任务、关闭划卡后保持后台播放功能，且不发起电池优化白名单请求。"
    ),
    MOTION_ORIENTATION(
        key = "perm_motion_orientation",
        title = "设备动作与方向传感器",
        badge = "传感交互",
        manifestPermissionName = "OrientationEventListener (Sensor)",
        purposeDescription = "在打开全屏封面播放页时检测手机横竖握持倾角，当横放手机且系统开启自动旋转时，自动平滑进入全屏横屏播放模式。",
        privacyCommitment = "仅在全屏封面播放页前台展示期间临时监听倾角，离开播放页立即注销监听，不保存任何传感器数据。",
        disabledImpactDescription = "关闭后将停用方向传感器监听，横放手机时不再自动切换至横屏播放页。"
    ),
    AUDIO_HARDWARE(
        key = "perm_audio_hardware",
        title = "外设音频检测与耳机线控",
        badge = "外设控制",
        manifestPermissionName = "AudioManager · MEDIA_BUTTON",
        purposeDescription = "用于识别有线耳机、蓝牙耳机与 USB DAC 连接状态，支持拔出耳机自动暂停、连接耳机自动续播、蓝牙歌词延迟自动补偿及有线耳机中键双击/三击切歌。",
        privacyCommitment = "仅监听音频输出路由变化与耳机按键事件，绝不调用麦克风录音或获取蓝牙位置信息。",
        disabledImpactDescription = "关闭后将停用有线耳机双击/三击切歌、拔插耳机自动暂停/播放及蓝牙音频歌词延迟自动补偿。"
    )
}

@Immutable
data class SystemPermissionGrantStatus(
    val statusLabel: String,
    val isSystemGranted: Boolean,
    val actionButtonLabel: String? = null
)

@Immutable
data class AppPermissionState(
    val networkAccessEnabled: Boolean = true,
    val notificationMediaEnabled: Boolean = true,
    val systemOverlayEnabled: Boolean = true,
    val storageExportEnabled: Boolean = true,
    val backgroundGuardianEnabled: Boolean = true,
    val motionOrientationEnabled: Boolean = true,
    val audioHardwareEnabled: Boolean = true
) {
    fun isEnabled(type: AppPermissionType): Boolean = when (type) {
        AppPermissionType.NETWORK_ACCESS -> networkAccessEnabled
        AppPermissionType.NOTIFICATION_MEDIA -> notificationMediaEnabled
        AppPermissionType.SYSTEM_OVERLAY -> systemOverlayEnabled
        AppPermissionType.STORAGE_EXPORT -> storageExportEnabled
        AppPermissionType.BACKGROUND_GUARDIAN -> backgroundGuardianEnabled
        AppPermissionType.MOTION_ORIENTATION -> motionOrientationEnabled
        AppPermissionType.AUDIO_HARDWARE -> audioHardwareEnabled
    }

    val enabledCount: Int
        get() = AppPermissionType.entries.count { isEnabled(it) }

    val totalCount: Int
        get() = AppPermissionType.entries.size

    fun withPermission(type: AppPermissionType, enabled: Boolean): AppPermissionState = when (type) {
        AppPermissionType.NETWORK_ACCESS -> copy(networkAccessEnabled = enabled)
        AppPermissionType.NOTIFICATION_MEDIA -> copy(notificationMediaEnabled = enabled)
        AppPermissionType.SYSTEM_OVERLAY -> copy(systemOverlayEnabled = enabled)
        AppPermissionType.STORAGE_EXPORT -> copy(storageExportEnabled = enabled)
        AppPermissionType.BACKGROUND_GUARDIAN -> copy(backgroundGuardianEnabled = enabled)
        AppPermissionType.MOTION_ORIENTATION -> copy(motionOrientationEnabled = enabled)
        AppPermissionType.AUDIO_HARDWARE -> copy(audioHardwareEnabled = enabled)
    }
}

/**
 * 全局权限公开说明与自主开关管控中心：
 * 1. 统一维护应用内 7 大类权限的自主开关状态并持久化至 SharedPreferences；
 * 2. 当用户关闭某项权限开关时，实时联动停用或拦截对应业务功能；
 * 3. 提供系统级授权状态查询与一键跳转系统授权设置页能力。
 */
object AppPermissionManager {
    private const val PREFS_NAME = "concept_permission_prefs"
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(AppPermissionState())
    val state = _state.asStateFlow()

    var onMotionPermissionSyncedCallback: ((Boolean) -> Unit)? = null

    fun init(context: Context) {
        val appCtx = context.applicationContext
        val p = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p

        val playerPrefs = appCtx.getSharedPreferences("concept_player_prefs", Context.MODE_PRIVATE)
        val legacyMotionEnabled = playerPrefs.getBoolean("motion_orientation_sensor_enabled", true)

        val loaded = AppPermissionState(
            networkAccessEnabled = p.getBoolean(AppPermissionType.NETWORK_ACCESS.key, true),
            notificationMediaEnabled = p.getBoolean(AppPermissionType.NOTIFICATION_MEDIA.key, true),
            systemOverlayEnabled = p.getBoolean(AppPermissionType.SYSTEM_OVERLAY.key, true),
            storageExportEnabled = p.getBoolean(AppPermissionType.STORAGE_EXPORT.key, true),
            backgroundGuardianEnabled = p.getBoolean(AppPermissionType.BACKGROUND_GUARDIAN.key, true),
            motionOrientationEnabled = p.getBoolean(AppPermissionType.MOTION_ORIENTATION.key, legacyMotionEnabled),
            audioHardwareEnabled = p.getBoolean(AppPermissionType.AUDIO_HARDWARE.key, true)
        )
        _state.value = loaded
    }

    fun isPermissionEnabled(type: AppPermissionType): Boolean {
        return _state.value.isEnabled(type)
    }

    fun setPermissionEnabled(
        context: Context?,
        type: AppPermissionType,
        enabled: Boolean
    ) {
        val updated = _state.value.withPermission(type, enabled)
        _state.value = updated
        prefs?.edit()?.putBoolean(type.key, enabled)?.apply()

        when (type) {
            AppPermissionType.SYSTEM_OVERLAY -> {
                if (!enabled) {
                    runCatching { SystemLyricBridge.setFloatingCapsuleEnabled(false) }
                }
            }
            AppPermissionType.BACKGROUND_GUARDIAN -> {
                if (!enabled) {
                    if (context != null) {
                        runCatching { VipGuardianManager.setEnabled(context, false) }
                    } else {
                        runCatching { VipGuardianManager.syncAutoClaimPreference(false) }
                    }
                }
            }
            AppPermissionType.MOTION_ORIENTATION -> {
                context?.applicationContext
                    ?.getSharedPreferences("concept_player_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putBoolean("motion_orientation_sensor_enabled", enabled)
                    ?.apply()
                onMotionPermissionSyncedCallback?.invoke(enabled)
            }
            else -> {}
        }
    }

    fun enableAllPermissions(context: Context? = null) {
        val allOn = AppPermissionState()
        _state.value = allOn
        prefs?.edit()?.apply {
            AppPermissionType.entries.forEach { putBoolean(it.key, true) }
            apply()
        }
        context?.applicationContext
            ?.getSharedPreferences("concept_player_prefs", Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean("motion_orientation_sensor_enabled", true)
            ?.apply()
        onMotionPermissionSyncedCallback?.invoke(true)
    }

    fun resetForTest() {
        _state.value = AppPermissionState()
    }

    /**
     * 检查手机存储与音乐下载权限是否已就绪：
     * - Android 9 及以下 (API <= 28)：需授予 `WRITE_EXTERNAL_STORAGE`
     * - Android 10 (API 29)：若启用 legacyExternalStorage 则检查 `WRITE_EXTERNAL_STORAGE`，或走 MediaStore
     * - Android 11+ (API >= 30)：标准公共目录 `Music/`、`Download/` 通过 `MediaStore` 免运行时权限写入；
     *   若需要访问非标准任意自定义绝对路径，可检查 `Environment.isExternalStorageManager()` 或 `READ_MEDIA_AUDIO`
     */
    fun hasMusicDownloadStoragePermission(context: Context): Boolean {
        return runCatching {
            when {
                Build.VERSION.SDK_INT <= 29 -> {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }
                Build.VERSION.SDK_INT >= 33 -> {
                    Environment.isExternalStorageManager() ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_MEDIA_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                }
                else -> {
                    Environment.isExternalStorageManager() ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }.getOrDefault(true)
    }

    /**
     * 返回当前 Android 系统版本下推荐申请的存储运行时权限数组
     */
    fun requiredStorageRuntimePermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT <= 29 -> arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun openAllFilesAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }.onFailure {
                runCatching {
                    val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                }.onFailure {
                    openAppDetailsSettings(context)
                }
            }
        } else {
            openAppDetailsSettings(context)
        }
    }

    fun querySystemGrantStatus(context: Context, type: AppPermissionType): SystemPermissionGrantStatus {
        return when (type) {
            AppPermissionType.NETWORK_ACCESS -> SystemPermissionGrantStatus(
                statusLabel = "系统默认已授权",
                isSystemGranted = true,
                actionButtonLabel = null
            )
            AppPermissionType.NOTIFICATION_MEDIA -> {
                val granted = runCatching {
                    if (Build.VERSION.SDK_INT >= 33) {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED &&
                            NotificationManagerCompat.from(context).areNotificationsEnabled()
                    } else {
                        NotificationManagerCompat.from(context).areNotificationsEnabled()
                    }
                }.getOrDefault(true)
                SystemPermissionGrantStatus(
                    statusLabel = if (granted) "系统通知已允许" else "系统通知未开启",
                    isSystemGranted = granted,
                    actionButtonLabel = "系统通知设置"
                )
            }
            AppPermissionType.SYSTEM_OVERLAY -> {
                val canDraw = SystemLyricBridge.canDrawSystemOverlays(context)
                SystemPermissionGrantStatus(
                    statusLabel = if (canDraw) "悬浮窗已授权" else "悬浮窗未授权",
                    isSystemGranted = canDraw,
                    actionButtonLabel = "悬浮窗授权设置"
                )
            }
            AppPermissionType.STORAGE_EXPORT -> {
                val granted = hasMusicDownloadStoragePermission(context)
                val allFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
                } else granted
                SystemPermissionGrantStatus(
                    statusLabel = when {
                        allFilesGranted -> "全盘存储读写已授权"
                        granted -> "媒体存储已就绪"
                        Build.VERSION.SDK_INT >= 29 -> "分区存储就绪 · 可授权音频权限"
                        else -> "需授予存储写入权限"
                    },
                    isSystemGranted = granted || Build.VERSION.SDK_INT >= 29,
                    actionButtonLabel = "存储权限设置"
                )
            }
            AppPermissionType.BACKGROUND_GUARDIAN -> {
                val ignoring = runCatching {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                }.getOrDefault(false)
                SystemPermissionGrantStatus(
                    statusLabel = if (ignoring) "已加入电池白名单" else "未加入电池白名单",
                    isSystemGranted = ignoring,
                    actionButtonLabel = "配置电池优化"
                )
            }
            AppPermissionType.MOTION_ORIENTATION -> {
                val autoRotate = runCatching {
                    Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                }.getOrDefault(true)
                SystemPermissionGrantStatus(
                    statusLabel = if (autoRotate) "传感器与自动旋转就绪" else "系统自动旋转未开启",
                    isSystemGranted = autoRotate,
                    actionButtonLabel = null
                )
            }
            AppPermissionType.AUDIO_HARDWARE -> SystemPermissionGrantStatus(
                statusLabel = "系统音频外设就绪",
                isSystemGranted = true,
                actionButtonLabel = null
            )
        }
    }

    fun openSystemPermissionSetting(context: Context, type: AppPermissionType) {
        when (type) {
            AppPermissionType.NOTIFICATION_MEDIA -> {
                runCatching {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }.onFailure {
                    openAppDetailsSettings(context)
                }
            }
            AppPermissionType.SYSTEM_OVERLAY -> {
                runCatching {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }.onFailure {
                    openAppDetailsSettings(context)
                }
            }
            AppPermissionType.STORAGE_EXPORT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    openAllFilesAccessSettings(context)
                } else {
                    openAppDetailsSettings(context)
                }
            }
            AppPermissionType.BACKGROUND_GUARDIAN -> {
                if (!isPermissionEnabled(AppPermissionType.BACKGROUND_GUARDIAN)) {
                    Toast.makeText(context, "请先开启「后台保活、唤醒锁与 VIP 自动守护」权限开关", Toast.LENGTH_SHORT).show()
                    return
                }
                runCatching {
                    val directIntent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(directIntent)
                }.onFailure {
                    runCatching {
                        val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(listIntent)
                    }.onFailure {
                        openAppDetailsSettings(context)
                    }
                }
            }
            else -> openAppDetailsSettings(context)
        }
    }

    fun openAppDetailsSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "无法打开系统应用设置页面", Toast.LENGTH_SHORT).show()
        }
    }
}
