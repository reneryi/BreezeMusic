package com.concept.music.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.concept.music.BuildConfig
import com.concept.music.data.kugou.NetworkClientProvider
import com.concept.music.data.model.AudioQuality
import com.concept.music.service.AudioCacheLimit
import com.concept.music.service.AudioCacheManager
import com.concept.music.service.AudioEffectManager
import com.concept.music.service.AudioFocusMode
import com.concept.music.service.DetailedCacheStats
import com.concept.music.service.HeadsetClickSensitivity
import com.concept.music.ui.theme.*
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.MusicPlayerViewModel
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 项目官方 GitHub 仓库与反馈/发布地址常量
 */
object ProjectLinks {
    const val GITHUB_REPO_OWNER = "reneryi"
    const val GITHUB_REPO_NAME = "breezemusic"
    const val GITHUB_REPO_URL = "https://github.com/reneryi/breezemusic"
    const val GITHUB_ISSUES_URL = "https://github.com/reneryi/breezemusic/issues"
    const val GITHUB_NEW_ISSUE_URL = "https://github.com/reneryi/breezemusic/issues/new"
    const val GITHUB_RELEASES_URL = "https://github.com/reneryi/breezemusic/releases"
    const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/reneryi/breezemusic/releases/latest"
}

/**
 * 在线版本更新检查结果状态
 */
sealed class UpdateCheckResult {
    data object Idle : UpdateCheckResult()
    data object Checking : UpdateCheckResult()
    data class HasUpdate(
        val latestVersion: String,
        val title: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val publishedAt: String
    ) : UpdateCheckResult()
    data class UpToDate(val versionText: String, val message: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * 版本号语义化比对（如 remoteTag = "v1.1.0" vs localVersion = "1.0.0"）
 */
internal fun isRemoteVersionNewer(param1: String, param2: String): Boolean {
    // 智能识别 remoteTag 与 localVersion 传入顺序（兼容 (remoteTag, localVersion) 与 (localVersion, remoteTag) 调用）
    val (remoteTag, localVersion) = if (param2.trim().startsWith("v", ignoreCase = true) && !param1.trim().startsWith("v", ignoreCase = true)) {
        param2 to param1
    } else {
        param1 to param2
    }

    fun parseNumericParts(raw: String): List<Int> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        return cleaned.split(".")
            .mapNotNull { it.trim().toIntOrNull() }
    }
    val localParts = parseNumericParts(localVersion)
    val remoteParts = parseNumericParts(remoteTag)
    if (remoteParts.isEmpty()) return false
    val maxLen = maxOf(localParts.size, remoteParts.size)
    for (i in 0 until maxLen) {
        val l = localParts.getOrElse(i) { 0 }
        val r = remoteParts.getOrElse(i) { 0 }
        if (r > l) return true
        if (r < l) return false
    }
    // 版本相同时，非 rc/beta 的正式发布版高于预发布版（带 -rc / -beta，判定远端更新）
    val localIsPreRelease = localVersion.contains("-")
    val remoteIsPreRelease = remoteTag.contains("-")
    return localIsPreRelease && !remoteIsPreRelease
}

/**
 * 生成预填充当前设备与系统环境信息的标准 GitHub Issue Markdown 模板
 */
internal fun buildStandardIssueMarkdownTemplate(
    appVersionName: String = BuildConfig.VERSION_NAME,
    appVersionCode: Int = BuildConfig.VERSION_CODE,
    deviceBrand: String = Build.MANUFACTURER ?: "Android",
    deviceModel: String = Build.MODEL ?: "Device",
    androidRelease: String = Build.VERSION.RELEASE ?: "Unknown",
    sdkInt: Int = Build.VERSION.SDK_INT
): String {
    return """
        ### 📌 问题描述 (Bug Summary)
        - [x] BUG 缺陷 / 异常崩溃
        - [ ] 功能建议 / 体验优化
        - [ ] 音效 / 歌词 / 界面显示异常

        ### 📱 运行环境 (Environment)
        - **应用版本**：BreezeMusic v$appVersionName (Build $appVersionCode)
        - **设备型号**：$deviceBrand $deviceModel
        - **系统版本**：Android $androidRelease (API $sdkInt)
        - **音频设备**：例如 手机扬声器 / Type-C 有线耳机 / 蓝牙耳机
        - **网络与状态**：例如 Wi-Fi / 已登录酷狗 VIP

        ### 🔄 复现步骤 (Steps to Reproduce)
        1. 打开微风音乐，进入「...」页面
        2. 点击「...」按钮或播放歌曲「...」
        3. 观察到「...」异常

        ### 🎯 期望行为 (Expected Behavior)
        请描述正常情况下预期的正确表现。

        ### 💥 实际表现与日志截图 (Actual Behavior & Logs)
        请描述实际发生的问题、Toast 提示，并在此处粘贴截图或日志。
    """.trimIndent()
}

@Immutable
data class ChangelogVersionItem(
    val version: String,
    val date: String,
    val tag: String,
    val highlights: List<String>
)

internal val PROJECT_CHANGELOG_HISTORY = listOf(
    ChangelogVersionItem(
        version = "v1.0.0 (RC3 · 正式版候选)",
        date = "2026-10-28",
        tag = "当前版本",
        highlights = listOf(
            "重构设置页为二级分类导航架构，彻底告别单页长瀑布流，支持手势侧滑逐级返回",
            "新增「问题反馈与建议」二级页：内置可视化 GitHub Issue 示例图、填写规范、环境模板一键复制与仓库 Issues 跳转",
            "升级「存储与缓存」中心：支持流媒体音频、封面/评论图片、歌单 SWR 快照、KRC 歌词与临时缓存四维分项统计与独立清理，支持 512MB~10GB 音频缓存上限调节",
            "新增「允许与其他应用同时播放」及三档音频焦点策略（独占焦点 / 压低音量闪避同播 Ducking / 完全混音同播）",
            "新增有线耳机暂停键双击/三击切歌状态机，支持双击/三击方向互换、280ms/350ms/450ms 灵敏度调节与连接耳机（有线/蓝牙）自动播放",
            "新增蓝牙音频输出延迟自动歌词补偿、划掉后台任务卡片保持播放与系统电池优化白名单一键引导",
            "完善「关于微风音乐」页面：新增项目定位、开源学习与版权免责声明、GitHub 仓库跳转、在线检查更新与更新日志"
        )
    ),
    ChangelogVersionItem(
        version = "v1.0.0-rc2 (视听与动效跃迁)",
        date = "2026-10-20",
        tag = "核心演进",
        highlights = listOf(
            "自研 64-bit/Float PCM 母带音频处理器（10 段参量均衡器、超重低音、人声清澈度、电子管温暖度、M/S 声场扩展与混响）",
            "重构 KRC 逐字流光歌词渲染引擎：支持字内平滑卡拉 OK 扫光、长音谐波辉光、前奏/间奏三点呼吸波点与阶梯景深模糊",
            "实现封面色相锁定 13 级 Tonal Palette 动态取色系统（WCAG 4.5:1 对比度保障）与五款高定流光播放页背景",
            "新增 120Hz 高刷横屏播放模式、2K 超清多模板歌词海报生成与支持物理穿透锁定的系统级全局桌面悬浮歌词"
        )
    ),
    ChangelogVersionItem(
        version = "v1.0.0-rc1 (酷狗概念版协议底座)",
        date = "2026-10-10",
        tag = "基础架构",
        highlights = listOf(
            "纯 Kotlin 端侧完整实现 MakcRe/KuGouMusicApi 概念版（appid=3116）签名、RSA/AES 加密与 dfid 风控设备注册，零后端直连",
            "实现手机验证码登录（支持同手机号多账号切换）、扫码登录与 Android Keystore 硬件级凭证加密存储",
            "打通概念版每日畅听领 VIP、升级 VIP 权益、CSCC 三段式真实听歌时长上报与 JobScheduler 后台静默守护",
            "支持 Hi-Res / SQ 无损 / 320K 智能解析与真无损码率防伪校验，补齐每日推荐、私人 FM、排行榜、歌手/专辑详情与四级楼中楼评论"
        )
    )
)

/**
 * 设置页二级菜单分类定义：
 * 告别原先单页长瀑布流堆叠，一级页面仅展示分类入口与实时配置摘要，点击进入对应的二级设置详情页。
 */
enum class SettingsCategory(
    val title: String,
    val subtitle: String
) {
    APPEARANCE(
        title = "外观与播放样式",
        subtitle = "主题模式、唱片样式、播放页背景与封面动态取色"
    ),
    AUDIO_QUALITY(
        title = "音质与音效",
        subtitle = "蝰蛇音效调音台、默认在线音质、移动省流与切歌过渡"
    ),
    PLAYBACK_CONTROL(
        title = "外设与播放控制",
        subtitle = "音频焦点混音/闪避、有线线控自定义、耳机连播与后台保活"
    ),
    LYRICS(
        title = "歌词显示设置",
        subtitle = "全屏歌词字号、译文比例、对齐方式、景深模糊与蓝牙延迟补偿"
    ),
    SPEED_SLEEP(
        title = "倍速与睡眠定时",
        subtitle = "倍速升降调调节与睡眠倒计时自动停止播放"
    ),
    STORAGE(
        title = "存储与缓存",
        subtitle = "音频/图片/歌单快照/歌词分项缓存明细、上限设置与独立清理"
    ),
    FEEDBACK(
        title = "问题反馈与建议",
        subtitle = "标准 GitHub Issue 提交示例图、编写指南与一键跳转仓库 Issues"
    ),
    ABOUT(
        title = "关于微风音乐",
        subtitle = "项目定义、免责与版权声明、项目仓库、检查更新与更新日志"
    )
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    return runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@runCatching false
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
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
            Toast.makeText(context, "无法打开系统电池优化设置页，请在系统设置中手动允许后台运行", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        copyTextToClipboard(context, "链接", url)
        Toast.makeText(context, "未找到浏览器，已将链接复制到剪贴板：$url", Toast.LENGTH_SHORT).show()
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

private suspend fun checkGitHubLatestRelease(): UpdateCheckResult = withContext(Dispatchers.IO) {
    runCatching {
        val req = Request.Builder()
            .url(ProjectLinks.GITHUB_LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BreezeMusic-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        NetworkClientProvider.baseClient.newCall(req).execute().use { resp ->
            if (resp.code == 404) {
                return@withContext UpdateCheckResult.UpToDate(
                    versionText = "v${BuildConfig.VERSION_NAME} (RC)",
                    message = "当前已是最新开发版，首个正式版 Release 即将发布，敬请关注 GitHub 仓库！"
                )
            }
            if (!resp.isSuccessful) {
                return@withContext UpdateCheckResult.Error(
                    "GitHub 接口响应异常 (HTTP ${resp.code})，可点击下方按钮前往 Releases 页面查看"
                )
            }
            val bodyStr = resp.body?.string().orEmpty()
            val json = JsonParser.parseString(bodyStr).asJsonObject
            val tagName = json.get("tag_name")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val releaseTitle = json.get("name")?.takeIf { !it.isJsonNull }?.asString.orEmpty().ifBlank { tagName }
            val releaseNotes = json.get("body")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val htmlUrl = json.get("html_url")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                .ifBlank { ProjectLinks.GITHUB_RELEASES_URL }
            val publishedAt = json.get("published_at")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                .take(10)

            var apkDownloadUrl = htmlUrl
            val assets = json.getAsJsonArray("assets")
            if (assets != null) {
                for (el in assets) {
                    val obj = el.asJsonObject
                    val assetName = obj.get("name")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    val dlUrl = obj.get("browser_download_url")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    if (assetName.endsWith(".apk", ignoreCase = true) && dlUrl.isNotBlank()) {
                        apkDownloadUrl = dlUrl
                        break
                    }
                }
            }

            if (tagName.isNotBlank() && isRemoteVersionNewer(BuildConfig.VERSION_NAME, tagName)) {
                UpdateCheckResult.HasUpdate(
                    latestVersion = tagName,
                    title = releaseTitle,
                    releaseNotes = releaseNotes.ifBlank { "包含性能优化与问题修复，建议更新体验。" },
                    downloadUrl = apkDownloadUrl,
                    publishedAt = publishedAt
                )
            } else {
                val verDisplay = tagName.ifBlank { "v${BuildConfig.VERSION_NAME}" }
                UpdateCheckResult.UpToDate(
                    versionText = verDisplay,
                    message = "当前已是最新版本 ($verDisplay)"
                )
            }
        }
    }.getOrElse {
        UpdateCheckResult.Error("无法连接 GitHub 更新服务，请检查网络或直接访问仓库 Releases 页面")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MusicPlayerViewModel,
    onBackClick: () -> Unit,
    onOpenViperEffect: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val preferredQuality = uiState.preferredQuality
    val coverStyle = uiState.coverStyle
    val playerBackgroundStyle = uiState.playerBackgroundStyle
    val dynamicCoverAccentEnabled = uiState.dynamicCoverAccentEnabled
    val lyricFontSize = uiState.lyricFontSize
    val lyricTranslationScale = uiState.lyricTranslationScale
    val lyricAlignment = uiState.lyricAlignment
    val lyricBlurEnabled = uiState.lyricBlurEnabled
    val lyricKeepScreenOn = uiState.lyricKeepScreenOn
    val playbackSpeed = uiState.playbackSpeed
    val playbackPitch = uiState.playbackPitch
    val linkPitchToSpeed = uiState.linkPitchToSpeed
    val sleepTimerRemainingSec = uiState.sleepTimerRemainingSec
    val sleepStopAfterCurrentSong = uiState.sleepStopAfterCurrentSong
    val sleepPendingStopAtTrackEnd = uiState.sleepPendingStopAtTrackEnd
    val climaxModeEnabled = uiState.climaxModeEnabled
    val crossfadeSeconds = uiState.crossfadeSeconds
    val volumeNormalization = uiState.volumeNormalization
    val wifiOnlyLossless = uiState.wifiOnlyLossless
    val pauseOnHeadsetDisconnect = uiState.pauseOnHeadsetDisconnect
    val allowPlayWithOtherApps = uiState.allowPlayWithOtherApps
    val audioFocusMode = uiState.audioFocusMode
    val wiredHeadsetControlEnabled = uiState.wiredHeadsetControlEnabled
    val swapHeadsetDoubleTripleClick = uiState.swapHeadsetDoubleTripleClick
    val headsetClickSensitivity = uiState.headsetClickSensitivity
    val autoPlayOnHeadsetConnect = uiState.autoPlayOnHeadsetConnect
    val autoPlayOnWiredConnect = uiState.autoPlayOnWiredConnect
    val autoPlayOnBluetoothConnect = uiState.autoPlayOnBluetoothConnect
    val bluetoothLyricOffsetEnabled = uiState.bluetoothLyricOffsetEnabled
    val bluetoothLyricOffsetMs = uiState.bluetoothLyricOffsetMs
    val isBluetoothAudioConnected = uiState.isBluetoothAudioConnected
    val keepPlayingOnTaskRemoved = uiState.keepPlayingOnTaskRemoved
    val autoPlayOnStartup = uiState.autoPlayOnStartup
    val smoothPlayPauseFade = uiState.smoothPlayPauseFade
    val motionOrientationPermissionEnabled = uiState.motionOrientationPermissionEnabled

    val cacheSizeBytes by AudioCacheManager.cacheSizeBytes.collectAsState()
    val detailedCacheStats by AudioCacheManager.detailedStats.collectAsState()
    val cacheLimit by AudioCacheManager.cacheLimit.collectAsState()
    val themeMode by ThemeManager.themeMode.collectAsState()
    val audioEffectState by AudioEffectManager.state.collectAsState()

    var batteryWhitelisted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var updateCheckState by remember { mutableStateOf<UpdateCheckResult>(UpdateCheckResult.Idle) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryWhitelisted = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var activeCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val activeCategory = remember(activeCategoryName) {
        activeCategoryName?.let { name -> runCatching { SettingsCategory.valueOf(name) }.getOrNull() }
    }

    // 处于二级菜单时，系统返回手势优先退回设置一级主菜单
    BackHandler(enabled = activeCategory != null) {
        activeCategoryName = null
    }

    LaunchedEffect(activeCategory) {
        if (activeCategory == null || activeCategory == SettingsCategory.STORAGE) {
            AudioCacheManager.refreshCacheSize(context)
        }
    }

    val isSleepActive = sleepTimerRemainingSec > 0L || sleepPendingStopAtTrackEnd
    val sleepStatusText = when {
        sleepTimerRemainingSec > 0L -> "剩余 ${sleepTimerRemainingSec / 60}分${sleepTimerRemainingSec % 60}秒"
        sleepPendingStopAtTrackEnd -> "播完当前歌曲后停止"
        else -> "定时未开启"
    }

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = activeCategory?.title ?: "设置",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (activeCategory != null) {
                                Text(
                                    text = "设置 · 二级选项",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (activeCategory != null) {
                                    activeCategoryName = null
                                } else {
                                    onBackClick()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            AnimatedContent(
                targetState = activeCategory,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> (fullWidth * 0.24f).toInt() },
                            animationSpec = tween(durationMillis = 340, easing = ConceptMotion.GentleSpatialEnterEasing)
                        ) + fadeIn(tween(durationMillis = 280, easing = ConceptMotion.GentleSpatialEnterEasing))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() },
                                animationSpec = tween(durationMillis = 300, easing = ConceptMotion.GentleSpatialExitEasing)
                            ) + fadeOut(tween(durationMillis = 220, easing = ConceptMotion.GentleSpatialExitEasing)))
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() },
                            animationSpec = tween(durationMillis = 320, easing = ConceptMotion.GentleSpatialEnterEasing)
                        ) + fadeIn(tween(durationMillis = 260, easing = ConceptMotion.GentleSpatialEnterEasing))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (fullWidth * 0.24f).toInt() },
                                animationSpec = tween(durationMillis = 280, easing = ConceptMotion.GentleSpatialExitEasing)
                            ) + fadeOut(tween(durationMillis = 220, easing = ConceptMotion.GentleSpatialExitEasing)))
                    }
                },
                label = "SettingsSubPageTransition"
            ) { category ->
                if (category == null) {
                    // 一级菜单主页：分类卡片导航列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 140.dp, top = 8.dp)
                    ) {
                        item {
                            SettingsGroupHeader(title = "视听与显示")
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.APPEARANCE,
                                icon = Icons.Rounded.Palette,
                                accentColor = ConceptVioletAccent,
                                statusText = "${themeMode.label} · ${coverStyle.label} · ${playerBackgroundStyle.label}",
                                onClick = { activeCategoryName = SettingsCategory.APPEARANCE.name }
                            )
                        }
                        item {
                            val btCompLabel = if (bluetoothLyricOffsetEnabled) " · 蓝牙补偿 ${bluetoothLyricOffsetMs}ms" else ""
                            SettingsMenuEntryCard(
                                category = SettingsCategory.LYRICS,
                                icon = Icons.Rounded.Lyrics,
                                accentColor = ConceptCyanAccent,
                                statusText = "${lyricFontSize.toInt()}sp · ${lyricAlignment.label} · ${if (lyricBlurEnabled) "景深模糊" else "清晰文本"}$btCompLabel",
                                onClick = { activeCategoryName = SettingsCategory.LYRICS.name }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(2.dp))
                            SettingsGroupHeader(title = "播放与控制")
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.AUDIO_QUALITY,
                                icon = Icons.Rounded.HighQuality,
                                accentColor = ConceptMintAccent,
                                statusText = "默认 ${preferredQuality.label} · ${if (audioEffectState.isEnabled) "蝰蛇：${audioEffectState.currentPreset.label}" else "音效关闭"}",
                                onClick = { activeCategoryName = SettingsCategory.AUDIO_QUALITY.name }
                            )
                        }
                        item {
                            val headsetStatus = if (wiredHeadsetControlEnabled) "线控开启(${headsetClickSensitivity.label})" else "线控关闭"
                            SettingsMenuEntryCard(
                                category = SettingsCategory.PLAYBACK_CONTROL,
                                icon = Icons.Rounded.Headphones,
                                accentColor = ConceptCoralAccent,
                                statusText = "${audioFocusMode.label} · $headsetStatus",
                                onClick = { activeCategoryName = SettingsCategory.PLAYBACK_CONTROL.name }
                            )
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.SPEED_SLEEP,
                                icon = Icons.Rounded.Speed,
                                accentColor = ConceptPrimary,
                                statusText = "倍速 ${String.format(Locale.getDefault(), "%.2f", playbackSpeed)}x · $sleepStatusText",
                                onClick = { activeCategoryName = SettingsCategory.SPEED_SLEEP.name }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(2.dp))
                            SettingsGroupHeader(title = "系统与信息")
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.STORAGE,
                                icon = Icons.Rounded.Storage,
                                accentColor = ConceptAmberAccent,
                                statusText = "总缓存 ${AudioCacheManager.formatSize(cacheSizeBytes)} · 音频 ${AudioCacheManager.formatSize(detailedCacheStats.audioBytes)} / 图片 ${AudioCacheManager.formatSize(detailedCacheStats.totalImageBytes)}",
                                onClick = { activeCategoryName = SettingsCategory.STORAGE.name }
                            )
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.FEEDBACK,
                                icon = Icons.Rounded.BugReport,
                                accentColor = ConceptRoseAccent,
                                statusText = "GitHub Issue 规范反馈示例 · 一键复制环境与跳转仓库",
                                onClick = { activeCategoryName = SettingsCategory.FEEDBACK.name }
                            )
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.ABOUT,
                                icon = Icons.Rounded.Info,
                                accentColor = ConceptPrimary,
                                statusText = "BreezeMusic v${BuildConfig.VERSION_NAME} · 项目地址 · 检查更新 · 版权声明",
                                onClick = { activeCategoryName = SettingsCategory.ABOUT.name }
                            )
                        }
                    }
                } else {
                    // 二级菜单详情页：仅展示当前所选分类的具体设置项
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 140.dp, top = 8.dp)
                    ) {
                        when (category) {
                            SettingsCategory.APPEARANCE -> {
                                item {
                                    SettingsSectionCard(
                                        title = "主题与色彩",
                                        icon = Icons.Rounded.Palette
                                    ) {
                                        Text(
                                            text = "主题色彩",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AppThemeMode.entries.forEach { mode ->
                                                val selected = themeMode == mode
                                                val previewBrush = when (mode) {
                                                    AppThemeMode.DARK_ABYSS_BLUE -> Brush.horizontalGradient(
                                                        listOf(Color(0xFF0A0E17), Color(0xFF142238), Color(0xFF1E3A5F))
                                                    )
                                                    AppThemeMode.LIGHT_WATERCOLOR -> Brush.horizontalGradient(
                                                        listOf(Color(0xFFE3F2FD), Color(0xFFB3E5FC), Color(0xFFEDE7F6))
                                                    )
                                                    AppThemeMode.SYSTEM -> Brush.horizontalGradient(
                                                        listOf(Color(0xFF101826), Color(0xFF2B8CFF), Color(0xFFEAF4FF))
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = if (selected) ConceptPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .clickable {
                                                            ThemeManager.setThemeMode(context, mode)
                                                            Toast.makeText(context, "已切换主题：${mode.label}", Toast.LENGTH_SHORT).show()
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(previewBrush)
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = mode.label,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp,
                                                                color = if (selected) ConceptPrimary else MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = mode.subtitle,
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        RadioButton(
                                                            selected = selected,
                                                            onClick = { ThemeManager.setThemeMode(context, mode) },
                                                            colors = RadioButtonDefaults.colors(selectedColor = ConceptPrimary)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    SettingsSectionCard(
                                        title = "播放页视觉样式",
                                        icon = Icons.Rounded.Album
                                    ) {
                                        Text(
                                            text = "封面样式",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            CoverStyle.entries.forEach { style ->
                                                FilterChip(
                                                    selected = coverStyle == style,
                                                    onClick = { viewModel.setCoverStyle(style) },
                                                    label = { Text(style.label, fontSize = 12.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                                        selectedLabelColor = ConceptPrimary
                                                    )
                                                )
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        Text(
                                            text = "播放页背景效果",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            PlayerBackgroundStyle.entries.forEach { bgStyle ->
                                                FilterChip(
                                                    selected = playerBackgroundStyle == bgStyle,
                                                    onClick = { viewModel.setPlayerBackgroundStyle(bgStyle) },
                                                    label = { Text(bgStyle.label, fontSize = 12.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                                        selectedLabelColor = ConceptPrimary
                                                    )
                                                )
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        SettingToggleRow(
                                            title = "封面智能动态取色",
                                            subtitle = "提取封面主色渲染播放键与进度条",
                                            checked = dynamicCoverAccentEnabled,
                                            onCheckedChange = { viewModel.setDynamicCoverAccentEnabled(it) }
                                        )
                                    }
                                }
                            }

                            SettingsCategory.AUDIO_QUALITY -> {
                                item {
                                    SettingsSectionCard(
                                        title = "音效与母带调音",
                                        icon = Icons.Rounded.GraphicEq
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = ConceptPrimary.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .softCardClickable(shape = RoundedCornerShape(16.dp), onClick = onOpenViperEffect)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = ConceptPrimary, modifier = Modifier.size(22.dp))
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(
                                                            text = "蝰蛇音效与调音台",
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 14.sp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = if (audioEffectState.isEnabled) "已启用：${audioEffectState.currentPreset.label}" else "声效库 · 声场调音 · 10段均衡器",
                                                            fontSize = 11.sp,
                                                            color = if (audioEffectState.isEnabled) ConceptPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ConceptPrimary)
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        SettingToggleRow(
                                            title = "音量均衡",
                                            subtitle = "平衡不同歌曲间的音量差异",
                                            checked = volumeNormalization,
                                            onCheckedChange = { viewModel.setVolumeNormalization(it) }
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        SettingToggleRow(
                                            title = "夜间小音量等响度补偿",
                                            subtitle = "低音量聆听时自动补偿低频与高频细节",
                                            checked = audioEffectState.equalLoudnessCompensation,
                                            onCheckedChange = { AudioEffectManager.setEqualLoudnessCompensation(it) }
                                        )
                                    }
                                }

                                item {
                                    SettingsSectionCard(
                                        title = "在线播放音质与过渡",
                                        icon = Icons.Rounded.HighQuality
                                    ) {
                                        Text(
                                            text = "默认在线播放音质",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            AudioQuality.entries.forEach { quality ->
                                                val selected = preferredQuality == quality
                                                FilterChip(
                                                    selected = selected,
                                                    onClick = {
                                                        viewModel.setPreferredQuality(quality)
                                                        Toast.makeText(context, "默认音质：${quality.label}", Toast.LENGTH_SHORT).show()
                                                    },
                                                    label = { Text(quality.label, fontSize = 12.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                                        selectedLabelColor = ConceptPrimary
                                                    )
                                                )
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        SettingToggleRow(
                                            title = "移动网络省流",
                                            subtitle = "移动网络下播放 320K 高品，Wi-Fi 下恢复无损",
                                            checked = wifiOnlyLossless,
                                            onCheckedChange = { viewModel.setWifiOnlyLossless(it) }
                                        )

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "切歌淡入淡出",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                                )
                                                Text(
                                                    text = if (crossfadeSeconds == 0) "已关闭" else "切歌时 ${crossfadeSeconds} 秒平滑过渡",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = if (crossfadeSeconds == 0) "关闭" else "${crossfadeSeconds} 秒",
                                                color = ConceptPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Slider(
                                            value = crossfadeSeconds.toFloat(),
                                            onValueChange = { viewModel.setCrossfadeSeconds(it.toInt()) },
                                            valueRange = 0f..6f,
                                            steps = 5,
                                            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                                        )

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        SettingToggleRow(
                                            title = "副歌速刷",
                                            subtitle = "直达歌曲副歌精华段落",
                                            checked = climaxModeEnabled,
                                            onCheckedChange = { viewModel.setClimaxMode(it) },
                                            thumbColor = VipGold
                                        )
                                    }
                                }
                            }

                            SettingsCategory.PLAYBACK_CONTROL -> {
                                item {
                                    SettingsSectionCard(
                                        title = "音频焦点与多应用并发",
                                        icon = Icons.Rounded.Layers
                                    ) {
                                        SettingToggleRow(
                                            title = "允许与其他应用同时播放",
                                            subtitle = "开启后刷视频、玩游戏或发语音时不中断音乐播放",
                                            checked = allowPlayWithOtherApps,
                                            onCheckedChange = { enabled ->
                                                viewModel.setAllowPlayWithOtherApps(enabled)
                                                val msg = if (enabled) "已开启与其他应用同时播放" else "已恢复独占音频焦点模式"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        Text(
                                            text = "音频焦点与导航/通知音闪避策略",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = audioFocusMode.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            AudioFocusMode.entries.forEach { mode ->
                                                FilterChip(
                                                    selected = audioFocusMode == mode,
                                                    onClick = {
                                                        viewModel.setAudioFocusMode(mode)
                                                        Toast.makeText(context, "音频焦点模式：${mode.label}", Toast.LENGTH_SHORT).show()
                                                    },
                                                    label = { Text(mode.label, fontSize = 12.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = ConceptCoralAccent.copy(alpha = 0.2f),
                                                        selectedLabelColor = ConceptCoralAccent
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    SettingsSectionCard(
                                        title = "外设与有线耳机线控",
                                        icon = Icons.Rounded.Headphones
                                    ) {
                                        SettingToggleRow(
                                            title = "有线耳机线控切歌",
                                            subtitle = "通过有线耳机暂停键（中键）双击/三击切换上下首",
                                            checked = wiredHeadsetControlEnabled,
                                            onCheckedChange = { enabled ->
                                                viewModel.setWiredHeadsetControlEnabled(enabled)
                                                val msg = if (enabled) "已开启有线耳机双击/三击切歌" else "已关闭有线耳机多击切歌"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        if (wiredHeadsetControlEnabled) {
                                            Spacer(modifier = Modifier.height(10.dp))

                                            SettingToggleRow(
                                                title = "交换双击/三击切歌方向",
                                                subtitle = if (swapHeadsetDoubleTripleClick) "当前：双击上一首 · 三击下一首" else "当前：双击下一首 · 三击上一首（默认）",
                                                checked = swapHeadsetDoubleTripleClick,
                                                onCheckedChange = { swapped ->
                                                    viewModel.setSwapHeadsetDoubleTripleClick(swapped)
                                                }
                                            )

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Text(
                                                text = "线控连击灵敏度（多击判定间隔）",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                HeadsetClickSensitivity.entries.forEach { sens ->
                                                    FilterChip(
                                                        selected = headsetClickSensitivity == sens,
                                                        onClick = { viewModel.setHeadsetClickSensitivity(sens) },
                                                        label = { Text(sens.label, fontSize = 12.sp) },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = ConceptCoralAccent.copy(alpha = 0.2f),
                                                            selectedLabelColor = ConceptCoralAccent
                                                        )
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))
                                            val doubleActionText = if (swapHeadsetDoubleTripleClick) "切换上一首" else "切换下一首"
                                            val tripleActionText = if (swapHeadsetDoubleTripleClick) "切换下一首" else "切换上一首"
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "有线耳机按键操作说明（${headsetClickSensitivity.label}）",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = ConceptCoralAccent
                                                    )
                                                    Text(
                                                        text = "• 单击暂停键（中键）：播放 / 暂停\n• 双击暂停键（中键）：$doubleActionText\n• 三击暂停键（中键）：$tripleActionText",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        lineHeight = 18.sp
                                                    )
                                                }
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        SettingToggleRow(
                                            title = "断开耳机自动暂停",
                                            subtitle = "拔出有线耳机或蓝牙断开时暂停播放",
                                            checked = pauseOnHeadsetDisconnect,
                                            onCheckedChange = { viewModel.setPauseOnHeadsetDisconnect(it) }
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        SettingToggleRow(
                                            title = "连接耳机（有线/蓝牙）自动开始播放",
                                            subtitle = "插入有线耳机或蓝牙音频设备连接成功时自动恢复播放",
                                            checked = autoPlayOnHeadsetConnect,
                                            onCheckedChange = { enabled ->
                                                viewModel.setAutoPlayOnHeadsetConnect(enabled)
                                                val msg = if (enabled) "已开启连接耳机自动播放" else "已关闭连接耳机自动播放"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        if (autoPlayOnHeadsetConnect) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                FilterChip(
                                                    selected = autoPlayOnWiredConnect,
                                                    onClick = { viewModel.setAutoPlayOnWiredConnect(!autoPlayOnWiredConnect) },
                                                    label = { Text("有线耳机插入自动播", fontSize = 12.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                                        selectedLabelColor = ConceptPrimary
                                                    )
                                                )
                                                FilterChip(
                                                    selected = autoPlayOnBluetoothConnect,
                                                    onClick = { viewModel.setAutoPlayOnBluetoothConnect(!autoPlayOnBluetoothConnect) },
                                                    label = { Text("蓝牙耳机连接自动播", fontSize = 12.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                                        selectedLabelColor = ConceptPrimary
                                                    )
                                                )
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        BluetoothLyricCompensationSection(
                                            enabled = bluetoothLyricOffsetEnabled,
                                            offsetMs = bluetoothLyricOffsetMs,
                                            isBluetoothConnected = isBluetoothAudioConnected,
                                            onEnabledChange = { viewModel.setBluetoothLyricOffsetEnabled(it) },
                                            onOffsetChange = { viewModel.setBluetoothLyricOffsetMs(it) }
                                        )
                                    }
                                }

                                item {
                                    SettingsSectionCard(
                                        title = "播放行为与后台保活",
                                        icon = Icons.Rounded.Tune
                                    ) {
                                        SettingToggleRow(
                                            title = "启动时自动续播",
                                            subtitle = "打开应用后自动恢复上次播放",
                                            checked = autoPlayOnStartup,
                                            onCheckedChange = { viewModel.setAutoPlayOnStartup(it) }
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        SettingToggleRow(
                                            title = "播放/暂停音量渐入渐出",
                                            subtitle = "点击播放或暂停时平滑过渡音量",
                                            checked = smoothPlayPauseFade,
                                            onCheckedChange = { viewModel.setSmoothPlayPauseFade(it) }
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        SettingToggleRow(
                                            title = "横放手机自动进入横屏播放",
                                            subtitle = "需开启系统自动旋转，仅在封面播放页生效",
                                            checked = motionOrientationPermissionEnabled,
                                            onCheckedChange = { enabled ->
                                                viewModel.setMotionOrientationPermissionEnabled(enabled)
                                            }
                                        )

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        SettingToggleRow(
                                            title = "底部任务栏清理后保持后台播放",
                                            subtitle = "在系统最近任务列表划掉卡片时，保持后台音乐继续播放不中断",
                                            checked = keepPlayingOnTaskRemoved,
                                            onCheckedChange = { viewModel.setKeepPlayingOnTaskRemoved(it) }
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (batteryWhitelisted) ConceptMintAccent.copy(alpha = 0.12f) else ConceptAmberAccent.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .softCardClickable(
                                                    shape = RoundedCornerShape(16.dp),
                                                    onClick = { requestIgnoreBatteryOptimizations(context) }
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(
                                                        imageVector = if (batteryWhitelisted) Icons.Rounded.VerifiedUser else Icons.Rounded.BatteryChargingFull,
                                                        contentDescription = null,
                                                        tint = if (batteryWhitelisted) ConceptMintAccent else ConceptAmberAccent,
                                                        modifier = Modifier.size(21.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(
                                                            text = "电池优化白名单与后台常驻",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = if (batteryWhitelisted) {
                                                                "已忽略电池优化 · 息屏长时间听歌不杀后台"
                                                            } else {
                                                                "未加入白名单 · 点击一键允许后台无限制运行"
                                                            },
                                                            fontSize = 11.sp,
                                                            color = if (batteryWhitelisted) ConceptMintAccent else ConceptAmberAccent
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    imageVector = Icons.Rounded.ChevronRight,
                                                    contentDescription = null,
                                                    tint = if (batteryWhitelisted) ConceptMintAccent else ConceptAmberAccent
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            SettingsCategory.LYRICS -> {
                                item {
                                    SettingsSectionCard(
                                        title = "歌词排版与视觉",
                                        icon = Icons.Rounded.Lyrics
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("全屏歌词字号", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                            Text("${lyricFontSize.toInt()} sp", color = ConceptPrimary, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = lyricFontSize,
                                            onValueChange = { viewModel.setLyricFontSize(it) },
                                            valueRange = 18f..34f,
                                            steps = 7,
                                            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("译文/注音字号比例", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                            Text("${(lyricTranslationScale * 100).roundToInt()}%", color = ConceptPrimary, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = lyricTranslationScale,
                                            onValueChange = { viewModel.setLyricTranslationScale(it) },
                                            valueRange = 0.65f..0.95f,
                                            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("歌词对齐方式", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                LyricAlignment.entries.forEach { align ->
                                                    FilterChip(
                                                        selected = lyricAlignment == align,
                                                        onClick = { viewModel.setLyricAlignment(align) },
                                                        label = { Text(align.label, fontSize = 12.sp) }
                                                    )
                                                }
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        SettingToggleRow(
                                            title = "阶梯景深模糊",
                                            subtitle = "非当前唱诵行随距离自然虚化",
                                            checked = lyricBlurEnabled,
                                            onCheckedChange = { viewModel.setLyricBlurEnabled(it) }
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        SettingToggleRow(
                                            title = "歌词屏幕常亮",
                                            subtitle = "处于全屏歌词页或横屏播放页时保持屏幕常亮不息屏",
                                            checked = lyricKeepScreenOn,
                                            onCheckedChange = { viewModel.setLyricKeepScreenOn(it) }
                                        )
                                    }
                                }

                                item {
                                    SettingsSectionCard(
                                        title = "蓝牙歌词音画同步补偿",
                                        icon = Icons.Rounded.BluetoothAudio
                                    ) {
                                        BluetoothLyricCompensationSection(
                                            enabled = bluetoothLyricOffsetEnabled,
                                            offsetMs = bluetoothLyricOffsetMs,
                                            isBluetoothConnected = isBluetoothAudioConnected,
                                            onEnabledChange = { viewModel.setBluetoothLyricOffsetEnabled(it) },
                                            onOffsetChange = { viewModel.setBluetoothLyricOffsetMs(it) }
                                        )
                                    }
                                }
                            }

                            SettingsCategory.SPEED_SLEEP -> {
                                item {
                                    SettingsSectionCard(
                                        title = "倍速与音调调节",
                                        icon = Icons.Rounded.Speed
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "倍速 ${String.format(Locale.getDefault(), "%.2f", playbackSpeed)}x · 音调 ${String.format(Locale.getDefault(), "%.2f", playbackPitch)}x",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            if (playbackSpeed != 1.0f || playbackPitch != 1.0f) {
                                                TextButton(onClick = { viewModel.resetSpeedAndPitch() }) {
                                                    Text("恢复原速", color = ConceptPrimary)
                                                }
                                            }
                                        }

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { preset ->
                                                FilterChip(
                                                    selected = abs(playbackSpeed - preset) < 0.03f,
                                                    onClick = { viewModel.setPlaybackSpeed(preset) },
                                                    label = { Text("${preset}x", fontSize = 12.sp) }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        SettingToggleRow(
                                            title = "变速联动变调",
                                            subtitle = "音调随倍速同步升降",
                                            checked = linkPitchToSpeed,
                                            onCheckedChange = { viewModel.setLinkPitchToSpeed(it) }
                                        )
                                    }
                                }

                                item {
                                    SettingsSectionCard(
                                        title = "睡眠定时关闭",
                                        icon = Icons.Rounded.Bedtime
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("睡眠定时", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                                Text(
                                                    text = sleepStatusText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isSleepActive) ConceptPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSleepActive) {
                                                TextButton(onClick = { viewModel.cancelSleepTimer() }) {
                                                    Text("取消定时", color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(15, 30, 45, 60).forEach { mins ->
                                                val targetSec = mins * 60L
                                                val isPresetActive = sleepTimerRemainingSec in (targetSec - 14 * 60L).coerceAtLeast(1L)..targetSec
                                                FilterChip(
                                                    selected = isPresetActive,
                                                    onClick = {
                                                        viewModel.setSleepTimer(mins, sleepStopAfterCurrentSong)
                                                        Toast.makeText(context, "已设置 $mins 分钟后停止", Toast.LENGTH_SHORT).show()
                                                    },
                                                    label = { Text("${mins}分钟", fontSize = 12.sp) }
                                                )
                                            }
                                            FilterChip(
                                                selected = sleepPendingStopAtTrackEnd && sleepTimerRemainingSec == 0L,
                                                onClick = {
                                                    viewModel.setSleepTimer(0, true)
                                                    Toast.makeText(context, "将在当前歌曲播完后暂停", Toast.LENGTH_SHORT).show()
                                                },
                                                label = { Text("播完本首", fontSize = 12.sp) }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        SettingToggleRow(
                                            title = "播完当前歌曲再停",
                                            subtitle = "倒计时结束后等待当前歌曲自然播完",
                                            checked = sleepStopAfterCurrentSong,
                                            onCheckedChange = { viewModel.setSleepStopAfterCurrentSongPreference(it) }
                                        )
                                    }
                                }
                            }

                            SettingsCategory.STORAGE -> {
                                item {
                                    StorageAndCacheSectionContent(
                                        context = context,
                                        totalBytes = cacheSizeBytes,
                                        stats = detailedCacheStats,
                                        cacheLimit = cacheLimit
                                    )
                                }
                            }

                            SettingsCategory.FEEDBACK -> {
                                item {
                                    FeedbackGuideSectionContent(context = context)
                                }
                            }

                            SettingsCategory.ABOUT -> {
                                item {
                                    AboutAppSectionContent(
                                        context = context,
                                        updateState = updateCheckState,
                                        onTriggerCheckUpdate = {
                                            if (updateCheckState !is UpdateCheckResult.Checking) {
                                                updateCheckState = UpdateCheckResult.Checking
                                                coroutineScope.launch {
                                                    val res = checkGitHubLatestRelease()
                                                    updateCheckState = res
                                                    when (res) {
                                                        is UpdateCheckResult.UpToDate -> {
                                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                                        }
                                                        is UpdateCheckResult.HasUpdate -> {
                                                            Toast.makeText(context, "发现新版本：${res.latestVersion}", Toast.LENGTH_SHORT).show()
                                                        }
                                                        is UpdateCheckResult.Error -> {
                                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                                        }
                                                        else -> {}
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 存储与缓存二级页面完整内容：
 * 1. 总空间占用与多维构成比例条
 * 2. 流媒体音频缓存上限档位调节（512MB ~ 10GB）
 * 3. 四大实际缓存分类明细（音频流、封面/评论图片、歌单SWR快照、KRC歌词与WebView临时缓存）及各自独立清理按钮
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageAndCacheSectionContent(
    context: Context,
    totalBytes: Long,
    stats: DetailedCacheStats,
    cacheLimit: AudioCacheLimit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsSectionCard(
            title = "存储与缓存总览",
            icon = Icons.Rounded.Storage
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前总缓存占用",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "包含在线音频流、封面图片、歌单秒开快照与歌词缓存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = AudioCacheManager.formatSize(totalBytes),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = VipGold,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 四色分项缓存构成比例条
            val denom = totalBytes.coerceAtLeast(1L).toFloat()
            val audioFrac = (stats.audioBytes / denom).coerceIn(0f, 1f)
            val imgFrac = (stats.totalImageBytes / denom).coerceIn(0f, 1f)
            val swrFrac = (stats.swrMetadataBytes / denom).coerceIn(0f, 1f)
            val otherFrac = (stats.otherTempBytes / denom).coerceIn(0f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (totalBytes > 0L) {
                    if (audioFrac > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(audioFrac.coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .background(ConceptAmberAccent)
                        )
                    }
                    if (imgFrac > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(imgFrac.coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .background(ConceptPrimary)
                        )
                    }
                    if (swrFrac > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(swrFrac.coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .background(ConceptMintAccent)
                        )
                    }
                    if (otherFrac > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(otherFrac.coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .background(ConceptVioletAccent)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CacheLegendDot("音频 ${AudioCacheManager.formatSize(stats.audioBytes)}", ConceptAmberAccent)
                CacheLegendDot("图片 ${AudioCacheManager.formatSize(stats.totalImageBytes)}", ConceptPrimary)
                CacheLegendDot("歌单快照 ${AudioCacheManager.formatSize(stats.swrMetadataBytes)}", ConceptMintAccent)
                CacheLegendDot("歌词/临时 ${AudioCacheManager.formatSize(stats.otherTempBytes)}", ConceptVioletAccent)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "流媒体音频缓存容量上限",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "${AudioCacheManager.formatSize(stats.audioBytes)} / ${cacheLimit.label}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ConceptAmberAccent,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "超出设定上限时，播放器将按 LRU（最久未播放）策略自动淘汰旧音频缓存",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AudioCacheLimit.entries.forEach { limitOption ->
                    FilterChip(
                        selected = cacheLimit == limitOption,
                        onClick = {
                            AudioCacheManager.setCacheLimit(context, limitOption)
                            Toast.makeText(context, "音频缓存上限已设为 ${limitOption.label}", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text(limitOption.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ConceptAmberAccent.copy(alpha = 0.2f),
                            selectedLabelColor = ConceptAmberAccent
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                var cacheRefreshSpin by remember { mutableIntStateOf(0) }
                OutlinedButton(
                    onClick = {
                        cacheRefreshSpin += 1
                        AudioCacheManager.refreshCacheSize(context)
                        Toast.makeText(context, "已刷新各项缓存统计", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    ConceptFluidRefreshIcon(
                        tint = ConceptPrimary,
                        spinTrigger = cacheRefreshSpin,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("刷新统计", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        AudioCacheManager.clearCache(context) {
                            Toast.makeText(context, "已清理全部缓存数据", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConceptPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("一键清理全部", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        SettingsSectionCard(
            title = "分项缓存明细与独立管理",
            icon = Icons.Rounded.PieChart
        ) {
            CacheCategoryDetailRow(
                title = "流媒体音频缓存",
                badgeText = "共 ${stats.audioTrackCount} 首音频片段",
                sizeText = AudioCacheManager.formatSize(stats.audioBytes),
                description = "目录：concept_audio_stream_cache · 在线边听边存的 Hi-Res / SQ 无损 FLAC / 320K 音频流数据块，二次重播免缓冲零流量。",
                accentColor = ConceptAmberAccent,
                icon = Icons.Rounded.LibraryMusic,
                actionLabel = "清理音频",
                onClearClick = {
                    AudioCacheManager.clearAudioCacheOnly(context) {
                        Toast.makeText(context, "已清理流媒体音频缓存", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            CacheCategoryDetailRow(
                title = "封面与评论图片缓存",
                badgeText = "磁盘 ${AudioCacheManager.formatSize(stats.imageDiskBytes)} · 内存 ${AudioCacheManager.formatSize(stats.imageMemoryBytes)}",
                sizeText = AudioCacheManager.formatSize(stats.totalImageBytes),
                description = "目录：image_cache (Coil) · 专辑高清封面、2K 歌词海报底图、歌手写真、歌单封面及评论区动态 GIF/WebP 表情包与配图。",
                accentColor = ConceptPrimary,
                icon = Icons.Rounded.Image,
                actionLabel = "清理图片",
                onClearClick = {
                    AudioCacheManager.clearImageCacheOnly(context) {
                        Toast.makeText(context, "已清理封面与评论图片缓存", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            CacheCategoryDetailRow(
                title = "歌单与发现页秒开快照 (SWR)",
                badgeText = "${stats.swrSnapshotCount} 份歌单离线快照",
                sizeText = AudioCacheManager.formatSize(stats.swrMetadataBytes),
                description = "目录：swr_playlists · 每日推荐、私人 FM、新歌速递、排行榜及个人收藏歌单的冷启动秒开缓存（清理后联网自动重新同步，不影响云端歌单）。",
                accentColor = ConceptMintAccent,
                icon = Icons.Rounded.FlashOn,
                actionLabel = "清理快照",
                onClearClick = {
                    AudioCacheManager.clearSwrMetadataCacheOnly(context) {
                        Toast.makeText(context, "已清理歌单与发现页秒开快照", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            CacheCategoryDetailRow(
                title = "KRC 歌词、音源直链与验证临时缓存",
                badgeText = "歌词 ${stats.lyricMemoryCount} 首 · 音源元数据 ${stats.runtimeMetaCount} 条",
                sizeText = AudioCacheManager.formatSize(stats.otherTempBytes),
                description = "包含内存中已解密的 KRC 逐字双语歌词、20 分钟音源直链缓存、专辑发行信息缓存及安全验证 WebView 临时文件。",
                accentColor = ConceptVioletAccent,
                icon = Icons.Rounded.Lyrics,
                actionLabel = "清理临时",
                onClearClick = {
                    AudioCacheManager.clearLyricAndTempCacheOnly(context) {
                        Toast.makeText(context, "已清理歌词、音源直链与临时缓存", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun CacheLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun CacheCategoryDetailRow(
    title: String,
    badgeText: String,
    sizeText: String,
    description: String,
    accentColor: Color,
    icon: ImageVector,
    actionLabel: String,
    onClearClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onClearClick,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CleaningServices,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = actionLabel,
                    fontSize = 11.5.sp,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 问题反馈与建议二级页面：
 * 1. 标准 GitHub Issue 提交可视化介绍示例图（仿真窗口 + ①~⑤ 编号标注）
 * 2. 「写什么、怎么写」详细规范说明（涵盖 BUG、接口报错、体验不足三类场景）
 * 3. 一键复制预填本机环境的 Markdown 模板 + 一键跳转 GitHub Issues 仓库页面
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackGuideSectionContent(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. 介绍示例图卡片
        SettingsSectionCard(
            title = "标准 GitHub Issue 反馈示例图",
            icon = Icons.Rounded.BugReport
        ) {
            Text(
                text = "提交反馈时，请参照下方标准 GitHub Issue 示例图结构填写，帮助开发者快速复现并修复问题：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 仿真 GitHub New Issue 界面可视化示例图
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0D1117),
                contentColor = Color(0xFFE6EDF3),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFF30363D),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 浏览器顶部栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161B22))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "reneryi / breezemusic · New Issue",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B949E),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF238636).copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = "示例图",
                                fontSize = 10.sp,
                                color = Color(0xFF3FB950),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ① 标题区
                        IssueDiagramCalloutLabel(
                            stepNumber = "①",
                            title = "标题 (Title)：[问题分类] + 触发场景 + 核心现象",
                            accentColor = ConceptPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF010409),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ConceptPrimary.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "[BUG] 开启蝰蛇音效切换 Hi-Res 音质时偶发短暂杂音",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE6EDF3),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }

                        // ② 标签区
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("标签分类：", fontSize = 11.sp, color = Color(0xFF8B949E))
                            IssueTagChip("bug · 缺陷/闪退", Color(0xFFDA3633))
                            IssueTagChip("error · 报错码", Color(0xFFD29922))
                            IssueTagChip("enhancement · 建议", Color(0xFF1F6FEB))
                        }

                        // ③ 正文模板区
                        IssueDiagramCalloutLabel(
                            stepNumber = "②",
                            title = "正文 (Write)：环境信息 + 复现步骤 + 预期与实际对比 + 截图/日志",
                            accentColor = ConceptCyanAccent
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF161B22),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(10.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "### 1. 运行环境 (Environment)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF58A6FF),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "• 版本: BreezeMusic v${BuildConfig.VERSION_NAME} | 系统: Android 14 (HyperOS)\n• 机型: Redmi K70 | 输出: Type-C 有线耳机 | VIP: 已激活",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFC9D1D9),
                                    lineHeight = 15.sp
                                )

                                Text(
                                    text = "### 2. 复现步骤 (Steps to Reproduce)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF58A6FF),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "1. 播放歌曲《晴天 - 周杰伦》并打开「蝰蛇 3D 音效」\n2. 点击音质按钮由「高品 320K」切换为「Hi-Res」\n3. 拖动进度条至 01:25 副歌位置",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFC9D1D9),
                                    lineHeight = 15.sp
                                )

                                Text(
                                    text = "### 3. 预期行为 vs 实际表现 (Expected vs Actual)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF58A6FF),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "• 预期：无缝平滑切换到 Hi-Res 音质继续播放\n• 实际：切换瞬间出现约 0.5 秒咔哒声，或弹出错误码提示",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFC9D1D9),
                                    lineHeight = 15.sp
                                )

                                Text(
                                    text = "### 4. 报错截图或日志附件 (Screenshots / Logs)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF58A6FF),
                                    fontFamily = FontFamily.Monospace
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0D1117),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "[在此拖拽或粘贴报错 Toast 截图、异常界面录屏或 Logcat 堆栈]",
                                        fontSize = 10.sp,
                                        color = Color(0xFF8B949E),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // 底部仿真提交栏
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "支持 Markdown 格式与直接粘贴图片",
                                fontSize = 10.sp,
                                color = Color(0xFF8B949E)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF238636)
                            ) {
                                Text(
                                    text = "Submit new issue",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. 写什么、怎么写详细说明
        SettingsSectionCard(
            title = "写什么、怎么写 · 反馈规范指南",
            icon = Icons.Rounded.EditNote
        ) {
            FeedbackRuleItem(
                badge = "1. 标题怎么写",
                title = "一句话概括「分类 + 场景 + 现象」",
                desc = "请勿只写“播放不了”或“有bug”。推荐格式：\n• 缺陷闪退：[BUG] 横屏播放页旋转回竖屏时歌词字号未恢复\n• 接口报错：[报错] 扫码登录后点击领取每日 VIP 提示 20028 验证失败\n• 功能建议：[建议] 希望在播放队列抽屉增加按歌手筛选功能"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            FeedbackRuleItem(
                badge = "2. 正文写什么",
                title = "四要素：环境 + 复现路径 + 现象对比 + 歌曲信息",
                desc = "• 若遇到特定歌曲无音源、音质不对或 KRC 歌词错位，请务必写明「歌名 + 歌手 + 专辑名」（或在单曲详情中复制歌曲 Hash）。\n• 若遇到登录、VIP 领取或评论发送报错，请附上界面底部的 Toast 报错原文或错误码。"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            FeedbackRuleItem(
                badge = "3. 提交前检查",
                title = "先搜已有 Issue，一事一议效率最高",
                desc = "• 提交前可先在仓库 Issues 列表查看是否已有相同问题；\n• 每个 Issue 建议只反馈一个具体问题，方便独立追踪修复进度与关闭归档。"
            )
        }

        // 3. 快捷复制模板与跳转仓库 Issues 按钮
        SettingsSectionCard(
            title = "提交问题到 GitHub 仓库",
            icon = Icons.AutoMirrored.Rounded.OpenInNew
        ) {
            Text(
                text = "点击下方按钮可先自动生成包含您当前机型（${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}）的 Markdown 模板，再跳转到 GitHub Issues 页面直接粘贴提交：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val template = buildStandardIssueMarkdownTemplate()
                    copyTextToClipboard(context, "GitHub Issue 模板", template)
                    Toast.makeText(context, "已复制标准 Issue 模板（含本机环境信息），可直接前往粘贴", Toast.LENGTH_LONG).show()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = ConceptPrimary, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "复制标准 Issue 反馈模板（附带本机环境）",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ConceptPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { openExternalUrl(context, ProjectLinks.GITHUB_ISSUES_URL) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConceptRoseAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("跳转仓库 Issues 界面", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }

                FilledTonalButton(
                    onClick = {
                        val template = buildStandardIssueMarkdownTemplate()
                        copyTextToClipboard(context, "GitHub Issue 模板", template)
                        Toast.makeText(context, "已自动复制模板，正在打开新建 Issue 页面...", Toast.LENGTH_SHORT).show()
                        openExternalUrl(context, ProjectLinks.GITHUB_NEW_ISSUE_URL)
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("直接新建 Issue", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun IssueDiagramCalloutLabel(
    stepNumber: String,
    title: String,
    accentColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.25f)
        ) {
            Text(
                text = stepNumber,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}

@Composable
private fun IssueTagChip(text: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.22f),
        modifier = Modifier.border(0.8.dp, color.copy(alpha = 0.65f), CircleShape)
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun FeedbackRuleItem(
    badge: String,
    title: String,
    desc: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ConceptRoseAccent.copy(alpha = 0.14f)
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ConceptRoseAccent,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
            fontSize = 12.sp
        )
    }
}

/**
 * 关于微风音乐二级页面：
 * 1. 品牌与版本卡片 + GitHub 项目仓库地址跳转 + 在线检查更新
 * 2. 项目定义与五大核心技术特点介绍
 * 3. 版本演进更新日志（Changelog）
 * 4. 严谨正式的开源学习与知识产权免责声明（叠甲声明）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutAppSectionContent(
    context: Context,
    updateState: UpdateCheckResult,
    onTriggerCheckUpdate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. 品牌头部与项目仓库、检查更新
        SettingsSectionCard(
            title = "版本信息与项目仓库",
            icon = Icons.Rounded.Info
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(ConceptPrimary, ConceptCyanAccent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BreezeMusic · 微风音乐",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "版本 v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE} · 正式版候选)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = ConceptPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Kotlin · Jetpack Compose · Media3 · KuGou Concept (3116)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 仓库地址卡片
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { openExternalUrl(context, ProjectLinks.GITHUB_REPO_URL) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = ConceptPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GitHub 开源项目地址",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = ProjectLinks.GITHUB_REPO_URL,
                                fontSize = 11.sp,
                                color = ConceptPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                copyTextToClipboard(context, "项目仓库地址", ProjectLinks.GITHUB_REPO_URL)
                                Toast.makeText(context, "已复制仓库链接", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "复制仓库地址",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "打开仓库",
                            tint = ConceptPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮栏：打开项目主页 + 检查更新
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { openExternalUrl(context, ProjectLinks.GITHUB_REPO_URL) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("访问项目主页", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onTriggerCheckUpdate,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConceptPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (updateState is UpdateCheckResult.Checking) "正在检查..." else "检查更新",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 检查更新结果状态面板
            when (updateState) {
                is UpdateCheckResult.Idle -> {}
                is UpdateCheckResult.Checking -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = ConceptPrimary
                    )
                }
                is UpdateCheckResult.HasUpdate -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ConceptMintAccent.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "发现新版本：${updateState.latestVersion}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp,
                                    color = ConceptMintAccent
                                )
                                if (updateState.publishedAt.isNotBlank()) {
                                    Text(
                                        text = updateState.publishedAt,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = updateState.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp
                            )
                            Button(
                                onClick = { openExternalUrl(context, updateState.downloadUrl) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ConceptMintAccent,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("前往下载更新", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ConceptPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = updateState.message,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { openExternalUrl(context, ProjectLinks.GITHUB_RELEASES_URL) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("查看 Releases", fontSize = 11.5.sp, color = ConceptPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is UpdateCheckResult.Error -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ConceptAmberAccent.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = updateState.message,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { openExternalUrl(context, ProjectLinks.GITHUB_RELEASES_URL) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("手动前往", fontSize = 11.5.sp, color = ConceptAmberAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. 项目定义与核心特点介绍
        SettingsSectionCard(
            title = "项目定义与核心特性",
            icon = Icons.Rounded.AutoAwesome
        ) {
            Text(
                text = "BreezeMusic（微风音乐）是一款面向个人审美与高频听歌习惯深度定制的「酷狗概念版（Lite）」第三方原生 Android 客户端。项目参考并移植了 MakcRe/KuGouMusicApi 概念版协议体系，将所有签名、加解密与数据解析全量运行在手机端侧，不依赖任何中间服务器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProjectFeatureBullet(
                title = "1. 零后端原生直连与概念版 VIP 权益闭环",
                desc = "基于概念版（appid=3116）签名与设备风控协议直连官方网关，支持验证码/扫码登录、每日畅听领 VIP、CSCC 听歌时长上报及 JobScheduler 后台静默续期守护。"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProjectFeatureBullet(
                title = "2. 真无损音源防伪与自研 64-bit 母带 DSP 管线",
                desc = "支持 Hi-Res / SQ 无损 / 320K 智能解析与真实格式/码率校验；内置 10 段参量均衡器、蝰蛇声效预设、动态超重低音、人声清澈度、电子管激励与立体声混响。"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProjectFeatureBullet(
                title = "3. KRC 逐字流光歌词与全局桌面悬浮歌词",
                desc = "端侧解密 KRC 逐字时间戳、双语译文与罗马音注音，支持阶梯景深模糊、前奏/间奏呼吸波点、蓝牙延迟自动补偿、2K 歌词海报与可穿透锁定的系统级桌面歌词。"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProjectFeatureBullet(
                title = "4. 现代 Material 3 流体动效与封面色板",
                desc = "提取专辑封面生成色相锁定的 13 级明度色阶（满足 WCAG 对比度标准），提供仿真黑胶唱臂、圆角方形封面、五款动态背景与 120Hz 高刷横竖屏播放页。"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProjectFeatureBullet(
                title = "5. 全场景外设与多应用协同控制",
                desc = "提供独占焦点、压低音量闪避同播、完全混音同播三档音频焦点策略，以及有线耳机双击/三击切歌定制、连接耳机自动播放与最高 10GB 边听边存缓存。"
            )
        }

        // 3. 更新日志 (Changelog)
        SettingsSectionCard(
            title = "更新日志 · Changelog",
            icon = Icons.Rounded.History
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PROJECT_CHANGELOG_HISTORY.forEachIndexed { idx, item ->
                    if (idx > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.version,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (idx == 0) ConceptPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (idx == 0) ConceptPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = item.tag,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (idx == 0) ConceptPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = item.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        item.highlights.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "• ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ConceptPrimary
                                )
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 17.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. 严谨正式的免责与版权声明（叠甲声明）
        SettingsSectionCard(
            title = "免责与版权声明（法律条款）",
            icon = Icons.Rounded.Gavel
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DisclaimerClauseBlock(
                        title = "一、项目性质与技术研究用途声明",
                        content = "本软件（BreezeMusic / 微风音乐）系完全开源、非营利性质的个人计算机软件工程与移动开发技术学习项目，旨在探索 Kotlin 协程并发、Jetpack Compose 声明式 UI、AndroidX Media3 实时音频数字信号处理（DSP）及网络协议架构设计。本项目仅供个人学习、技术交流与编程研究使用，严禁任何组织或个人将本项目及其编译产物用于商业牟利、付费售卖、捆绑分发或任何违反中华人民共和国及相关国家/地区法律法规的用途。"
                    )
                    DisclaimerClauseBlock(
                        title = "二、媒体数据来源与知识产权归属",
                        content = "本项目客户端自身不存储、不托管、不制作、不修改、亦不提供任何音频文件、歌词文本、专辑封面或其它受著作权保护的音像制品。应用运行期间所索引及流式加载的全部数据均由客户端实时请求自第三方公开网络接口，其涉及的全部音乐作品著作权、录音制作者权、表演者权、词曲版权及商标权均归原版权方、唱片公司、词曲作者及对应内容平台（如酷狗音乐等）所有。"
                    )
                    DisclaimerClauseBlock(
                        title = "三、正版支持倡议与 24 小时试用清理义务",
                        content = "尊重知识产权是音乐产业持续繁荣的基石。我们强烈倡议并呼吁广大用户支持正版音乐，前往官方客户端正规渠道购买数字音乐专辑或开通会员服务。根据相关法律法规惯例，您下载、编译或安装本软件仅限于技术功能测试，测试过程中产生的本地缓存数据不得用于任何非法传播，且请务必在下载或编译试用后的 24 小时内主动清理全部缓存并彻底卸载、删除本软件。"
                    )
                    DisclaimerClauseBlock(
                        title = "四、免责条款与权利人联络机制",
                        content = "本开源项目按「原样（AS-IS）」提供，不附带任何明示或暗示的适销性、特定用途适用性或稳定性担保。使用者一旦运行或使用本软件，即视为已充分阅读、理解并同意本声明全部条款；因使用者违反本声明、滥用第三方接口或不当使用本软件所引发的一切账号异常、数据损失、版权纠纷或法律责任，均由使用者本人独立承担，与本项目开发者及开源贡献者无关。若任何权利人认为本项目的相关技术实现存在侵害其合法权益之虞，请通过 GitHub Issues 联系开发者，我们将在核实后第一时间予以修正或移除。"
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectFeatureBullet(title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = ConceptPrimary,
            fontSize = 12.5.sp
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun DisclaimerClauseBlock(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.2.sp,
            lineHeight = 16.8.sp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BluetoothLyricCompensationSection(
    enabled: Boolean,
    offsetMs: Long,
    isBluetoothConnected: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOffsetChange: (Long) -> Unit
) {
    SettingToggleRow(
        title = "蓝牙音频输出延迟补偿",
        subtitle = "连接蓝牙耳机或音箱时，自动为 KRC 逐字歌词套用延迟偏移量，断开蓝牙自动恢复 0ms",
        checked = enabled,
        onCheckedChange = onEnabledChange
    )

    if (enabled) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isBluetoothConnected) "蓝牙音频已连接 · 补偿生效中" else "当前未连接蓝牙 · 连上后自动生效",
                style = MaterialTheme.typography.bodySmall,
                color = if (isBluetoothConnected) ConceptCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${if (offsetMs > 0) "+" else ""}${offsetMs} ms",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = ConceptPrimary
                )
            )
        }

        Slider(
            value = offsetMs.toFloat().coerceIn(-600f, 600f),
            onValueChange = { raw ->
                val stepped = ((raw / 25f).roundToInt() * 25L).coerceIn(-600L, 600L)
                onOffsetChange(stepped)
            },
            valueRange = -600f..600f,
            steps = 47,
            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(-300L, -200L, -150L, -100L, 0L, 100L).forEach { presetMs ->
                FilterChip(
                    selected = offsetMs == presetMs,
                    onClick = { onOffsetChange(presetMs) },
                    label = {
                        Text(
                            text = if (presetMs > 0) "+${presetMs}ms" else "${presetMs}ms",
                            fontSize = 11.sp
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsMenuEntryCard(
    category: SettingsCategory,
    icon: ImageVector,
    accentColor: Color,
    statusText: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = conceptFloatingCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .softCardClickable(shape = RoundedCornerShape(22.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category.title,
                    tint = accentColor,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "进入${category.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    thumbColor: Color = ConceptPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = thumbColor)
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val sectionAccent = when {
        title.contains("外观") || title.contains("主题") || title.contains("视觉") -> ConceptVioletAccent
        title.contains("音质") || title.contains("音效") -> ConceptMintAccent
        title.contains("外设") || title.contains("焦点") || title.contains("播放行为") || title.contains("后台") -> ConceptCoralAccent
        title.contains("歌词") || title.contains("蓝牙") -> ConceptCyanAccent
        title.contains("缓存") || title.contains("存储") -> ConceptAmberAccent
        title.contains("反馈") || title.contains("Issue") || title.contains("规范") -> ConceptRoseAccent
        else -> ConceptPrimary
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = conceptFloatingCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(16.dp)
                        .clip(CircleShape)
                        .background(sectionAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = sectionAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
