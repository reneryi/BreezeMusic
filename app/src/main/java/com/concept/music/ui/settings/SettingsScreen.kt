package com.concept.music.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
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
import com.concept.music.service.AppPermissionManager
import com.concept.music.service.AppPermissionState
import com.concept.music.service.AppPermissionType
import com.concept.music.service.AudioCacheLimit
import com.concept.music.service.AudioCacheManager
import com.concept.music.service.AudioEffectManager
import com.concept.music.service.AudioFocusMode
import com.concept.music.service.DetailedCacheStats
import com.concept.music.service.HeadsetClickSensitivity
import com.concept.music.service.MusicDownloadManager
import com.concept.music.service.DownloadTaskStatus
import com.concept.music.ui.theme.*
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.MusicPlayerViewModel
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
    const val GITHUB_LATEST_RELEASE_WEB_URL = "https://github.com/reneryi/breezemusic/releases/latest"
    const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/reneryi/breezemusic/releases/latest"
    const val GITHUB_RELEASES_LIST_API = "https://api.github.com/repos/reneryi/breezemusic/releases?per_page=1"
    const val GHPROXY_LATEST_RELEASE_API = "https://ghfast.top/https://api.github.com/repos/reneryi/breezemusic/releases/latest"
    const val GHPROXY_RELEASES_LIST_API = "https://ghfast.top/https://api.github.com/repos/reneryi/breezemusic/releases?per_page=1"
    const val GHPROXY_MIRROR_LATEST_RELEASE_API = "https://gh-proxy.com/https://api.github.com/repos/reneryi/breezemusic/releases/latest"
    const val GHPROXY_RAW_GRADLE_MAIN_URL = "https://ghfast.top/https://raw.githubusercontent.com/reneryi/breezemusic/main/app/build.gradle.kts"
    const val GHPROXY_RAW_GRADLE_MASTER_URL = "https://ghfast.top/https://raw.githubusercontent.com/reneryi/breezemusic/master/app/build.gradle.kts"
    const val JSDELIVR_PACKAGE_API = "https://data.jsdelivr.com/v1/package/gh/reneryi/breezemusic"
    const val GCORE_JSDELIVR_GRADLE_URL = "https://gcore.jsdelivr.net/gh/reneryi/breezemusic@main/app/build.gradle.kts"
    const val FASTLY_JSDELIVR_GRADLE_URL = "https://fastly.jsdelivr.net/gh/reneryi/breezemusic@main/app/build.gradle.kts"
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
 * 从任意格式的版本字符串或 Tag（如 "v1.2.0"、"BreezeMusic-v1.2.0"、"1.2.0-tablet"、"release_1.2.0"）中提取核心语义化版本号
 */
internal fun extractCleanSemverString(raw: String): String {
    val trimmed = raw.trim()
    val regexMatch = Regex("(\\d+(?:\\.\\d+)+)").find(trimmed)
    if (regexMatch != null) {
        return regexMatch.groupValues[1]
    }
    return trimmed.removePrefix("v").removePrefix("V").substringBefore("-").trim()
}

/**
 * 版本号语义化比对（如 remoteTag = "v1.2.0" vs localVersion = "1.1.0"）
 */
internal fun isRemoteVersionNewer(param1: String, param2: String): Boolean {
    val p1 = param1.trim()
    val p2 = param2.trim()
    val (remoteTag, localVersion) = if (p2.startsWith("v", ignoreCase = true) && !p1.startsWith("v", ignoreCase = true)) {
        p2 to p1
    } else if (!p1.startsWith("v", ignoreCase = true) && !p2.startsWith("v", ignoreCase = true)) {
        val currentClean = extractCleanSemverString(BuildConfig.VERSION_NAME)
        if (extractCleanSemverString(p1) == currentClean && extractCleanSemverString(p2) != currentClean) {
            p2 to p1
        } else {
            p1 to p2
        }
    } else {
        p1 to p2
    }

    fun parseNumericParts(raw: String): List<Int> {
        val core = extractCleanSemverString(raw)
        return core.split(".")
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
    fun isPreReleaseSuffix(ver: String): Boolean {
        val lower = ver.lowercase()
        return lower.contains("-rc") || lower.contains("-beta") || lower.contains("-alpha")
    }
    return isPreReleaseSuffix(localVersion) && !isPreReleaseSuffix(remoteTag)
}

/**
 * 生成预填充当前设备与系统环境信息的标准 GitHub Issue Markdown 模板
 */
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
        subtitle = "深浅主题 · 唱片样式 · 动态背景"
    ),
    AUDIO_QUALITY(
        title = "音质与音效",
        subtitle = "蝰蛇调音 · 默认音质 · 切歌过渡"
    ),
    PLAYBACK_CONTROL(
        title = "外设与播放控制",
        subtitle = "多应用混音 · 耳机线控 · 后台保活"
    ),
    LYRICS(
        title = "歌词显示设置",
        subtitle = "字号排版 · 景深虚化 · 蓝牙校准"
    ),
    SPEED_SLEEP(
        title = "倍速与睡眠定时",
        subtitle = "倍速升降调 · 睡眠定时自动停止"
    ),
    STORAGE(
        title = "存储与缓存",
        subtitle = "空间占比 · 容量上限 · 分项清理"
    ),
    DOWNLOAD(
        title = "下载",
        subtitle = "下载音质 · 保存目录 · 标签内嵌"
    ),
    PERMISSIONS(
        title = "权限管理",
        subtitle = "透明用途说明 · 独立自主开关"
    ),
    FEEDBACK(
        title = "问题反馈与建议",
        subtitle = "Issue 规范示例 · 一键复制环境"
    ),
    ABOUT(
        title = "关于微风音乐",
        subtitle = "版本检查更新 · 更新日志 · 开源声明"
    )
}

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    return runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@runCatching false
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
}

internal fun requestIgnoreBatteryOptimizations(context: Context) {
    if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.BACKGROUND_GUARDIAN)) {
        Toast.makeText(context, "已在权限管理中关闭「后台保活、唤醒锁与 VIP 自动守护」权限，请先在权限管理中开启", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "无法打开系统电池优化设置页，请在系统设置中手动允许后台运行", Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        AppPermissionManager.copyTextToClipboard(
            context = context,
            label = "链接",
            text = url,
            toastMessage = "未找到浏览器，已将链接复制到剪贴板：$url"
        )
    }
}

internal fun copyTextToClipboard(context: Context, label: String, text: String) {
    AppPermissionManager.copyTextToClipboard(context, label, text, toastMessage = null)
}

internal fun resolveUpdateCheckFromApiOrRedirect(
    localVersionName: String,
    apiHttpCode: Int,
    apiBodyJson: String?,
    redirectFinalUrl: String? = null
): UpdateCheckResult {
    val cleanLocal = extractCleanSemverString(localVersionName).ifBlank {
        extractCleanSemverString(BuildConfig.VERSION_NAME)
    }
    val localDisplay = "v$cleanLocal"

    if (apiHttpCode in 200..299 && !apiBodyJson.isNullOrBlank()) {
        runCatching {
            val parsedElement = JsonParser.parseString(apiBodyJson)
            val candidateObjects = when {
                parsedElement.isJsonObject -> listOf(parsedElement.asJsonObject)
                parsedElement.isJsonArray -> parsedElement.asJsonArray.mapNotNull { el ->
                    runCatching { el.asJsonObject }.getOrNull()?.takeIf { obj ->
                        val isDraft = obj.get("draft")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                        !isDraft
                    }
                }
                else -> emptyList()
            }

            var bestJson: com.google.gson.JsonObject? = null
            var bestVersionStr = ""
            for (candidate in candidateObjects) {
                val rawTag = candidate.get("tag_name")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
                val rawTitle = candidate.get("name")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
                val candidateVer = extractCleanSemverString(rawTag).takeIf { it.any { c -> c.isDigit() } }
                    ?: extractCleanSemverString(rawTitle)
                if (candidateVer.isNotBlank()) {
                    if (bestJson == null || isRemoteVersionNewer("v$candidateVer", bestVersionStr)) {
                        bestJson = candidate
                        bestVersionStr = candidateVer
                    }
                }
            }

            val json = bestJson ?: candidateObjects.firstOrNull()
            if (json != null) {
                val rawTagName = json.get("tag_name")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
                val releaseTitle = json.get("name")?.takeIf { !it.isJsonNull }?.asString.orEmpty().ifBlank { rawTagName }
                val effectiveVer = extractCleanSemverString(rawTagName).takeIf { it.any { c -> c.isDigit() } }
                    ?: extractCleanSemverString(releaseTitle)
                val releaseNotes = json.get("body")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                val htmlUrl = json.get("html_url")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    .ifBlank { ProjectLinks.GITHUB_RELEASES_URL }
                val publishedAt = json.get("published_at")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    .take(10)

                val isTabletFlavor = runCatching { BuildConfig.IS_TABLET_FLAVOR }.getOrDefault(false)
                val assets = json.getAsJsonArray("assets")
                val apkAssets = assets?.asSequence()
                    ?.mapNotNull { el -> runCatching { el.asJsonObject }.getOrNull() }
                    ?.mapNotNull { obj ->
                        val assetName = obj.get("name")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                        val dlUrl = obj.get("browser_download_url")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                        if (assetName.endsWith(".apk", ignoreCase = true) && dlUrl.isNotBlank()) {
                            assetName to dlUrl
                        } else null
                    }
                    ?.toList()
                    .orEmpty()

                val preferredApkUrl = if (isTabletFlavor) {
                    apkAssets.firstOrNull { (name, _) ->
                        name.contains("tablet", ignoreCase = true) || name.contains("pad", ignoreCase = true)
                    }?.second ?: apkAssets.firstOrNull()?.second
                } else {
                    apkAssets.firstOrNull { (name, _) ->
                        name.contains("phone", ignoreCase = true)
                    }?.second ?: apkAssets.firstOrNull { (name, _) ->
                        !name.contains("tablet", ignoreCase = true) && !name.contains("pad", ignoreCase = true)
                    }?.second ?: apkAssets.firstOrNull()?.second
                }
                val apkDownloadUrl = preferredApkUrl ?: htmlUrl

                val compareTag = if (effectiveVer.isNotBlank()) "v$effectiveVer" else rawTagName
                return if (compareTag.isNotBlank() && isRemoteVersionNewer("v$compareTag".replace("vv", "v"), cleanLocal)) {
                    val normalizedTag = if (effectiveVer.isNotBlank()) {
                        "v$effectiveVer"
                    } else if (rawTagName.startsWith("v", ignoreCase = true)) {
                        rawTagName
                    } else {
                        "v$rawTagName"
                    }
                    UpdateCheckResult.HasUpdate(
                        latestVersion = normalizedTag,
                        title = releaseTitle.ifBlank { normalizedTag },
                        releaseNotes = "",
                        downloadUrl = apkDownloadUrl,
                        publishedAt = publishedAt
                    )
                } else {
                    UpdateCheckResult.UpToDate(
                        versionText = localDisplay,
                        message = "您正在使用最新正式版，暂无可用更新"
                    )
                }
            }
        }
    }

    if (!redirectFinalUrl.isNullOrBlank() && redirectFinalUrl.contains("/releases/tag/")) {
        val rawTag = redirectFinalUrl.substringAfter("/releases/tag/")
            .substringBefore("?")
            .substringBefore("#")
            .trim()
        val effectiveVer = extractCleanSemverString(rawTag)
        val compareTag = if (effectiveVer.isNotBlank()) "v$effectiveVer" else rawTag
        if (compareTag.isNotBlank() && isRemoteVersionNewer("v$compareTag".replace("vv", "v"), cleanLocal)) {
            val normalizedTag = if (effectiveVer.isNotBlank()) {
                "v$effectiveVer"
            } else if (rawTag.startsWith("v", ignoreCase = true)) {
                rawTag
            } else {
                "v$rawTag"
            }
            return UpdateCheckResult.HasUpdate(
                latestVersion = normalizedTag,
                title = "BreezeMusic $normalizedTag",
                releaseNotes = "",
                downloadUrl = redirectFinalUrl,
                publishedAt = ""
            )
        }
        return UpdateCheckResult.UpToDate(
            versionText = localDisplay,
            message = "您正在使用最新正式版，暂无可用更新"
        )
    }

    if (apiHttpCode == 404 || apiHttpCode == 403 || apiHttpCode == 429 || !redirectFinalUrl.isNullOrBlank()) {
        return UpdateCheckResult.UpToDate(
            versionText = localDisplay,
            message = "您正在使用最新正式版，暂无可用更新"
        )
    }

    return UpdateCheckResult.Error(
        "检查更新服务暂时繁忙 (HTTP $apiHttpCode)，可点击右侧前往 Releases 查看"
    )
}

internal fun extractVersionFromGradleText(gradleText: String?): String? {
    if (gradleText.isNullOrBlank()) return null
    val match = Regex("""versionName\s*=\s*"([^"]+)"""").find(gradleText)
    return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
}

internal fun extractLatestVersionFromJsDelivrPackageJson(jsonText: String?): String? {
    if (jsonText.isNullOrBlank()) return null
    return runCatching {
        val obj = JsonParser.parseString(jsonText).asJsonObject
        val versionsArr = obj.getAsJsonArray("versions")
        val extractedVersions = mutableListOf<String>()
        if (versionsArr != null) {
            for (el in versionsArr) {
                val verStr = when {
                    el.isJsonPrimitive -> el.asString.trim()
                    el.isJsonObject -> el.asJsonObject.get("version")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                    else -> ""
                }
                if (verStr.isNotBlank()) extractedVersions.add(verStr)
            }
        }
        val tagsObj = obj.getAsJsonObject("tags")
        val latestTag = tagsObj?.get("latest")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
        if (!latestTag.isNullOrBlank()) extractedVersions.add(latestTag)

        var highest: String? = null
        for (candidate in extractedVersions) {
            if (highest == null || isRemoteVersionNewer("v${extractCleanSemverString(candidate)}", highest)) {
                highest = candidate
            }
        }
        highest
    }.getOrNull()
}

private data class UpdateProbeOutcome(
    val isAuthoritativeReleaseSource: Boolean,
    val result: UpdateCheckResult?
)

/**
 * 多通道并发竞速检查更新（Concurrent Racing Update Check）：
 * - 告别此前 12 个端点逐一串行阻塞等待（耗时 8s~30s）的慢速轮询，采用 `supervisorScope` + `Channel` 并行发起探测；
 * - 单通道连接/读取超时收敛至 2.2s/2.5s，优先通过零响应体的 Web 302 `Location` 重定向与轻量 Releases API 竞速探测；
 * - 任一通道发现新版本（`HasUpdate`）立即取消其余全部慢速请求并毫秒级返回；
 * - 任一权威 Release 通道确认已是最新（`UpToDate`）且完成双通道交叉核验后立即取消慢速尾请求并返回，大幅缩短检查更新等待时间。
 */
private suspend fun checkGitHubLatestRelease(
    localVersionName: String = BuildConfig.VERSION_NAME
): UpdateCheckResult = withContext(Dispatchers.IO) {
    if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.NETWORK_ACCESS)) {
        return@withContext UpdateCheckResult.Error("已在权限管理中关闭「网络访问」权限，请先开启后再检查更新")
    }
    val cleanLocal = extractCleanSemverString(localVersionName).ifBlank {
        extractCleanSemverString(BuildConfig.VERSION_NAME)
    }
    val fastClient = NetworkClientProvider.baseClient.newBuilder()
        .connectTimeout(2200, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
        .callTimeout(2800, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
    val noRedirectClient = fastClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    val cacheBuster = System.currentTimeMillis() / 15000L

    fun probeReleaseApi(apiEndpoint: String): UpdateCheckResult? = runCatching {
        val req = Request.Builder()
            .url(apiEndpoint)
            .header("Accept", "application/vnd.github+json, application/json")
            .header("Cache-Control", "no-cache, no-store, max-age=0")
            .header("Pragma", "no-cache")
            .header("User-Agent", "BreezeMusic-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        fastClient.newCall(req).execute().use { resp ->
            val code = resp.code
            val body = if (resp.isSuccessful) resp.body?.string() else null
            if (code in 200..299 && !body.isNullOrBlank()) {
                resolveUpdateCheckFromApiOrRedirect(
                    localVersionName = cleanLocal,
                    apiHttpCode = code,
                    apiBodyJson = body,
                    redirectFinalUrl = null
                )
            } else null
        }
    }.getOrNull()

    fun probeWebRedirect(webUrl: String): UpdateCheckResult? = runCatching {
        val webReq = Request.Builder()
            .url("$webUrl?_t=$cacheBuster")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) BreezeMusic/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        noRedirectClient.newCall(webReq).execute().use { webResp ->
            val loc = webResp.header("Location").orEmpty()
            val targetUrl = if (loc.contains("/releases/tag/")) loc else webResp.request.url.toString()
            if (targetUrl.contains("/releases/tag/")) {
                resolveUpdateCheckFromApiOrRedirect(
                    localVersionName = cleanLocal,
                    apiHttpCode = 200,
                    apiBodyJson = null,
                    redirectFinalUrl = targetUrl
                )
            } else null
        }
    }.getOrNull()

    fun probeRemoteGradle(cdnGradleUrl: String): UpdateCheckResult? = runCatching {
        val req = Request.Builder()
            .url(cdnGradleUrl)
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "BreezeMusic-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        val cdnVer = fastClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) extractVersionFromGradleText(resp.body?.string()) else null
        }
        if (!cdnVer.isNullOrBlank()) {
            resolveUpdateCheckFromApiOrRedirect(
                localVersionName = cleanLocal,
                apiHttpCode = 200,
                apiBodyJson = null,
                redirectFinalUrl = "${ProjectLinks.GITHUB_REPO_URL}/releases/tag/v${extractCleanSemverString(cdnVer)}"
            )
        } else null
    }.getOrNull()

    var confirmedUpToDate: UpdateCheckResult.UpToDate? = null

    val racedResult: UpdateCheckResult? = supervisorScope {
        val channel = Channel<UpdateProbeOutcome>(Channel.UNLIMITED)
        val probeSpecs = listOf<Pair<Boolean, () -> UpdateCheckResult?>>(
            true to { probeWebRedirect("https://ghfast.top/${ProjectLinks.GITHUB_LATEST_RELEASE_WEB_URL}") },
            true to { probeWebRedirect(ProjectLinks.GITHUB_LATEST_RELEASE_WEB_URL) },
            true to { probeReleaseApi("${ProjectLinks.GHPROXY_LATEST_RELEASE_API}?_t=$cacheBuster") },
            true to { probeReleaseApi("${ProjectLinks.GHPROXY_MIRROR_LATEST_RELEASE_API}?_t=$cacheBuster") },
            true to { probeReleaseApi("${ProjectLinks.GITHUB_LATEST_RELEASE_API}?_t=$cacheBuster") },
            false to { probeRemoteGradle("${ProjectLinks.GHPROXY_RAW_GRADLE_MAIN_URL}?_t=$cacheBuster") },
            false to { probeRemoteGradle(ProjectLinks.GCORE_JSDELIVR_GRADLE_URL) }
        )

        val jobs = probeSpecs.map { (isAuthoritative, block) ->
            async(Dispatchers.IO) {
                val res = runCatching { block() }.getOrNull()
                channel.trySend(UpdateProbeOutcome(isAuthoritativeReleaseSource = isAuthoritative, result = res))
            }
        }

        var authoritativeUpToDateCount = 0
        var validSuccessCount = 0
        var immediateHasUpdate: UpdateCheckResult.HasUpdate? = null

        for (i in 0 until probeSpecs.size) {
            val outcome = channel.receive()
            when (val r = outcome.result) {
                is UpdateCheckResult.HasUpdate -> {
                    immediateHasUpdate = r
                    break
                }
                is UpdateCheckResult.UpToDate -> {
                    if (confirmedUpToDate == null) confirmedUpToDate = r
                    validSuccessCount++
                    if (outcome.isAuthoritativeReleaseSource) {
                        authoritativeUpToDateCount++
                    }
                    // 当至少有 1 个权威 Release 通道确认 UpToDate 且已有 2 个通道返回一致结果时，立即提前结束无需等待慢速节点超时
                    if (authoritativeUpToDateCount >= 1 && validSuccessCount >= 2) {
                        break
                    }
                }
                else -> {}
            }
        }

        jobs.forEach { it.cancel() }
        channel.close()
        immediateHasUpdate
    }

    if (racedResult is UpdateCheckResult.HasUpdate) return@withContext racedResult
    if (confirmedUpToDate != null) return@withContext confirmedUpToDate!!

    // 兜底查询 Releases 列表与 jsDelivr Package 元数据接口（仅当首批并发通道全部因极端网络受阻时触发）
    for (fallbackApi in listOf(
        "${ProjectLinks.GHPROXY_RELEASES_LIST_API}&_t=$cacheBuster",
        "${ProjectLinks.GITHUB_RELEASES_LIST_API}&_t=$cacheBuster"
    )) {
        val listRes = probeReleaseApi(fallbackApi)
        if (listRes is UpdateCheckResult.HasUpdate) return@withContext listRes
        if (listRes is UpdateCheckResult.UpToDate) return@withContext listRes
    }

    val jsdResult = runCatching {
        val req = Request.Builder()
            .url("${ProjectLinks.JSDELIVR_PACKAGE_API}?_t=$cacheBuster")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "BreezeMusic-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        fastClient.newCall(req).execute().use { resp ->
            val body = if (resp.isSuccessful) resp.body?.string() else null
            if (!body.isNullOrBlank()) {
                val jsdTag = extractLatestVersionFromJsDelivrPackageJson(body)
                if (!jsdTag.isNullOrBlank()) {
                    resolveUpdateCheckFromApiOrRedirect(
                        localVersionName = cleanLocal,
                        apiHttpCode = 200,
                        apiBodyJson = null,
                        redirectFinalUrl = "${ProjectLinks.GITHUB_REPO_URL}/releases/tag/$jsdTag"
                    )
                } else null
            } else null
        }
    }.getOrNull()
    if (jsdResult is UpdateCheckResult.HasUpdate) return@withContext jsdResult
    if (jsdResult is UpdateCheckResult.UpToDate) return@withContext jsdResult

    UpdateCheckResult.UpToDate(
        versionText = "v$cleanLocal",
        message = "您正在使用最新正式版，暂无可用更新"
    )
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
    val downloadConfig by MusicDownloadManager.config.collectAsState()
    val appVersionInfo = remember(context) { com.concept.music.ui.components.resolveAppVersionInfo(context) }
    val themeMode by ThemeManager.themeMode.collectAsState()
    val audioEffectState by AudioEffectManager.state.collectAsState()
    val permissionState by AppPermissionManager.state.collectAsState()

    var batteryWhitelisted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var systemPermissionRefreshTick by remember { mutableIntStateOf(0) }
    var updateCheckState by remember { mutableStateOf<UpdateCheckResult>(UpdateCheckResult.Idle) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryWhitelisted = isIgnoringBatteryOptimizations(context)
                systemPermissionRefreshTick += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val adaptiveInfo = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current
    val isMasterDetail = adaptiveInfo.useMasterDetailSettings

    var activeCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var showChangelogSubPage by rememberSaveable { mutableStateOf(false) }
    var showDownloadHistorySubPage by rememberSaveable { mutableStateOf(false) }
    val activeCategory = remember(activeCategoryName) {
        activeCategoryName?.let { name -> runCatching { SettingsCategory.valueOf(name) }.getOrNull() }
    }
    val effectiveMasterDetailCategory = activeCategory ?: SettingsCategory.APPEARANCE

    // 若权限管理中关闭了「音乐下载与歌词保存」，自动退出并隐藏「下载」设置项
    LaunchedEffect(permissionState.musicDownloadEnabled, activeCategory) {
        if (activeCategory == SettingsCategory.DOWNLOAD && !permissionState.musicDownloadEnabled) {
            showDownloadHistorySubPage = false
            activeCategoryName = null
        }
    }

    // 处于更新日志、最近下载记录子页面或二级菜单时，系统返回手势逐级退回上一层（平板左右分栏模式下仅对三级子页拦截返回）
    BackHandler(
        enabled = if (isMasterDetail) {
            showChangelogSubPage || showDownloadHistorySubPage
        } else {
            activeCategory != null || showChangelogSubPage || showDownloadHistorySubPage
        }
    ) {
        when {
            showChangelogSubPage -> showChangelogSubPage = false
            showDownloadHistorySubPage -> showDownloadHistorySubPage = false
            else -> activeCategoryName = null
        }
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
                        Text(
                            text = when {
                                showChangelogSubPage -> "更新日志"
                                showDownloadHistorySubPage -> "最近下载记录"
                                isMasterDetail -> "设置 · ${effectiveMasterDetailCategory.title}"
                                activeCategory != null -> activeCategory.title
                                else -> "设置"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                when {
                                    showChangelogSubPage -> showChangelogSubPage = false
                                    showDownloadHistorySubPage -> showDownloadHistorySubPage = false
                                    !isMasterDetail && activeCategory != null -> activeCategoryName = null
                                    else -> onBackClick()
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
            val selectCategoryAction: (SettingsCategory) -> Unit = { cat ->
                showChangelogSubPage = false
                showDownloadHistorySubPage = false
                activeCategoryName = cat.name
            }

            val renderMasterMenuList: @Composable (Modifier, SettingsCategory?) -> Unit = { listMod, highlightedCat ->
                LazyColumn(
                    modifier = listMod,
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
                            statusText = "${themeMode.label} · ${coverStyle.label}",
                            selected = highlightedCat == SettingsCategory.APPEARANCE,
                            onClick = { selectCategoryAction(SettingsCategory.APPEARANCE) }
                        )
                    }
                    item {
                        SettingsMenuEntryCard(
                            category = SettingsCategory.LYRICS,
                            icon = Icons.Rounded.Lyrics,
                            accentColor = ConceptCyanAccent,
                            statusText = "${lyricFontSize.toInt()}sp · ${lyricAlignment.label}",
                            selected = highlightedCat == SettingsCategory.LYRICS,
                            onClick = { selectCategoryAction(SettingsCategory.LYRICS) }
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
                            statusText = if (audioEffectState.isEnabled) "${preferredQuality.shortBadge} · 蝰蛇开" else preferredQuality.label,
                            selected = highlightedCat == SettingsCategory.AUDIO_QUALITY,
                            onClick = { selectCategoryAction(SettingsCategory.AUDIO_QUALITY) }
                        )
                    }
                    item {
                        SettingsMenuEntryCard(
                            category = SettingsCategory.PLAYBACK_CONTROL,
                            icon = Icons.Rounded.Headphones,
                            accentColor = ConceptCoralAccent,
                            statusText = if (allowPlayWithOtherApps) "混音同播" else audioFocusMode.label,
                            selected = highlightedCat == SettingsCategory.PLAYBACK_CONTROL,
                            onClick = { selectCategoryAction(SettingsCategory.PLAYBACK_CONTROL) }
                        )
                    }
                    item {
                        SettingsMenuEntryCard(
                            category = SettingsCategory.SPEED_SLEEP,
                            icon = Icons.Rounded.Speed,
                            accentColor = ConceptPrimary,
                            statusText = if (isSleepActive) sleepStatusText else "${String.format(Locale.getDefault(), "%.2f", playbackSpeed)}x",
                            selected = highlightedCat == SettingsCategory.SPEED_SLEEP,
                            onClick = { selectCategoryAction(SettingsCategory.SPEED_SLEEP) }
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
                            statusText = AudioCacheManager.formatSize(cacheSizeBytes),
                            selected = highlightedCat == SettingsCategory.STORAGE,
                            onClick = { selectCategoryAction(SettingsCategory.STORAGE) }
                        )
                    }
                    if (permissionState.musicDownloadEnabled) {
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.DOWNLOAD,
                                icon = Icons.Rounded.Download,
                                accentColor = ConceptCyanAccent,
                                statusText = downloadConfig.defaultDownloadQuality.shortBadge,
                                selected = highlightedCat == SettingsCategory.DOWNLOAD,
                                onClick = { selectCategoryAction(SettingsCategory.DOWNLOAD) }
                            )
                        }
                    }
                    item {
                        val permEnabledCnt = permissionState.enabledCount
                        val permTotalCnt = permissionState.totalCount
                        SettingsMenuEntryCard(
                            category = SettingsCategory.PERMISSIONS,
                            icon = Icons.Rounded.VerifiedUser,
                            accentColor = ConceptMintAccent,
                            statusText = "已开 $permEnabledCnt/$permTotalCnt",
                            selected = highlightedCat == SettingsCategory.PERMISSIONS,
                            onClick = { selectCategoryAction(SettingsCategory.PERMISSIONS) }
                        )
                    }
                    item {
                        SettingsMenuEntryCard(
                            category = SettingsCategory.FEEDBACK,
                            icon = Icons.Rounded.BugReport,
                            accentColor = ConceptRoseAccent,
                            statusText = "GitHub Issue",
                            selected = highlightedCat == SettingsCategory.FEEDBACK,
                            onClick = { selectCategoryAction(SettingsCategory.FEEDBACK) }
                        )
                    }
                    item {
                        SettingsMenuEntryCard(
                            category = SettingsCategory.ABOUT,
                            icon = Icons.Rounded.Info,
                            accentColor = ConceptPrimary,
                            statusText = appVersionInfo.displayVersion,
                            selected = highlightedCat == SettingsCategory.ABOUT,
                            onClick = { selectCategoryAction(SettingsCategory.ABOUT) }
                        )
                    }
                }
            }

            val renderDetailCategoryPane: @Composable (Modifier, SettingsCategory, Boolean, Boolean) -> Unit = { detailMod, category, isChangelogPage, isDownloadHistoryPage ->
                LazyColumn(
                    modifier = detailMod,
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
                                                ConceptBorderlessChoicePill(
                                                    selected = coverStyle == style,
                                                    label = style.label,
                                                    accentColor = ConceptVioletAccent,
                                                    onClick = { viewModel.setCoverStyle(style) }
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
                                                ConceptBorderlessChoicePill(
                                                    selected = playerBackgroundStyle == bgStyle,
                                                    label = bgStyle.label,
                                                    accentColor = ConceptVioletAccent,
                                                    onClick = { viewModel.setPlayerBackgroundStyle(bgStyle) }
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
                                                ConceptBorderlessChoicePill(
                                                    selected = preferredQuality == quality,
                                                    label = quality.label,
                                                    accentColor = ConceptMintAccent,
                                                    onClick = {
                                                        viewModel.setPreferredQuality(quality)
                                                        Toast.makeText(context, "默认音质：${quality.label}", Toast.LENGTH_SHORT).show()
                                                    }
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
                                                ConceptBorderlessChoicePill(
                                                    selected = audioFocusMode == mode,
                                                    label = mode.label,
                                                    accentColor = ConceptCoralAccent,
                                                    onClick = {
                                                        viewModel.setAudioFocusMode(mode)
                                                        Toast.makeText(context, "音频焦点模式：${mode.label}", Toast.LENGTH_SHORT).show()
                                                    }
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
                                                    ConceptBorderlessChoicePill(
                                                        selected = headsetClickSensitivity == sens,
                                                        label = sens.label,
                                                        accentColor = ConceptCoralAccent,
                                                        onClick = { viewModel.setHeadsetClickSensitivity(sens) }
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
                                                ConceptBorderlessChoicePill(
                                                    selected = autoPlayOnWiredConnect,
                                                    label = "有线耳机插入自动播",
                                                    accentColor = ConceptCoralAccent,
                                                    onClick = { viewModel.setAutoPlayOnWiredConnect(!autoPlayOnWiredConnect) }
                                                )
                                                ConceptBorderlessChoicePill(
                                                    selected = autoPlayOnBluetoothConnect,
                                                    label = "蓝牙耳机连接自动播",
                                                    accentColor = ConceptCoralAccent,
                                                    onClick = { viewModel.setAutoPlayOnBluetoothConnect(!autoPlayOnBluetoothConnect) }
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
                                                    ConceptBorderlessChoicePill(
                                                        selected = lyricAlignment == align,
                                                        label = align.label,
                                                        accentColor = ConceptCyanAccent,
                                                        onClick = { viewModel.setLyricAlignment(align) }
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
                                                ConceptBorderlessChoicePill(
                                                    selected = abs(playbackSpeed - preset) < 0.03f,
                                                    label = "${preset}x",
                                                    accentColor = ConceptPrimary,
                                                    onClick = { viewModel.setPlaybackSpeed(preset) }
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
                                                ConceptBorderlessChoicePill(
                                                    selected = isPresetActive,
                                                    label = "${mins}分钟",
                                                    accentColor = ConceptPrimary,
                                                    onClick = {
                                                        viewModel.setSleepTimer(mins, sleepStopAfterCurrentSong)
                                                        Toast.makeText(context, "已设置 $mins 分钟后停止", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                            ConceptBorderlessChoicePill(
                                                selected = sleepPendingStopAtTrackEnd && sleepTimerRemainingSec == 0L,
                                                label = "播完本首",
                                                accentColor = ConceptPrimary,
                                                onClick = {
                                                    viewModel.setSleepTimer(0, true)
                                                    Toast.makeText(context, "将在当前歌曲播完后暂停", Toast.LENGTH_SHORT).show()
                                                }
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

                            SettingsCategory.DOWNLOAD -> {
                                item {
                                    if (isDownloadHistoryPage) {
                                        DownloadHistorySubPageContent()
                                    } else {
                                        DownloadSettingsSectionContent(
                                            context = context,
                                            refreshTick = systemPermissionRefreshTick,
                                            onOpenDownloadHistory = { showDownloadHistorySubPage = true }
                                        )
                                    }
                                }
                            }

                            SettingsCategory.PERMISSIONS -> {
                                item {
                                    PermissionsSectionContent(
                                        context = context,
                                        permissionState = permissionState,
                                        refreshTick = systemPermissionRefreshTick,
                                        onTogglePermission = { type, enabled ->
                                            viewModel.setAppPermissionEnabled(type, enabled)
                                            val stateLabel = if (enabled) "已开启" else "已关闭"
                                            Toast.makeText(
                                                context,
                                                "$stateLabel「${type.title}」权限",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onEnableAll = {
                                            AppPermissionType.entries.forEach { t ->
                                                viewModel.setAppPermissionEnabled(t, true)
                                            }
                                            Toast.makeText(context, "已恢复开启全部应用权限", Toast.LENGTH_SHORT).show()
                                        }
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
                                    if (isChangelogPage) {
                                        ChangelogSubPageContent(appVersionInfo = appVersionInfo)
                                    } else {
                                        AboutAppSectionContent(
                                            context = context,
                                            appVersionInfo = appVersionInfo,
                                            updateState = updateCheckState,
                                            onOpenChangelog = { showChangelogSubPage = true },
                                            onTriggerCheckUpdate = {
                                                if (updateCheckState !is UpdateCheckResult.Checking) {
                                                    updateCheckState = UpdateCheckResult.Checking
                                                    coroutineScope.launch {
                                                        val res = checkGitHubLatestRelease(appVersionInfo.rawVersionName)
                                                        updateCheckState = res
                                                        when (res) {
                                                            is UpdateCheckResult.UpToDate -> {
                                                                Toast.makeText(context, "当前已是最新版本 (${res.versionText})", Toast.LENGTH_SHORT).show()
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

            if (isMasterDetail) {
                // 平板横屏左右并排分栏（Master-Detail）：加宽左栏一级菜单宽度，确保卡片标题单行完整显示不折行
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    renderMasterMenuList(
                        Modifier
                            .width(if (adaptiveInfo.widthDp >= 960) 372.dp else 336.dp)
                            .fillMaxHeight(),
                        effectiveMasterDetailCategory
                    )

                    val detailTransitionState = Triple(effectiveMasterDetailCategory, showChangelogSubPage, showDownloadHistorySubPage)
                    AnimatedContent(
                        targetState = detailTransitionState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        transitionSpec = {
                            (fadeIn(tween(220, easing = ConceptMotion.EmphasizedDecelerate)) +
                                androidx.compose.animation.scaleIn(initialScale = 0.975f, animationSpec = ConceptMotion.spatialPageSpring())) togetherWith
                                (fadeOut(tween(180, easing = ConceptMotion.EmphasizedAccelerate)) +
                                    androidx.compose.animation.scaleOut(targetScale = 0.975f, animationSpec = ConceptMotion.spatialPageSpring()))
                        },
                        label = "MasterDetailRightPaneTransition"
                    ) { (cat, isChangelogPage, isDownloadHistoryPage) ->
                        renderDetailCategoryPane(
                            Modifier.fillMaxSize(),
                            cat,
                            isChangelogPage,
                            isDownloadHistoryPage
                        )
                    }
                }
            } else {
                val subPageTransitionState = Triple(activeCategory, showChangelogSubPage, showDownloadHistorySubPage)
                var lastSubPageDepth by remember { mutableIntStateOf(0) }
                val subPageBlurPulse = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(subPageTransitionState) {
                    val curDepth = when {
                        subPageTransitionState.second || subPageTransitionState.third -> 2
                        subPageTransitionState.first != null -> 1
                        else -> 0
                    }
                    if (curDepth != lastSubPageDepth) {
                        lastSubPageDepth = curDepth
                        subPageBlurPulse.snapTo(1f)
                        subPageBlurPulse.animateTo(0f, ConceptMotion.spatialPageSpring())
                    }
                }

                AnimatedContent(
                    targetState = subPageTransitionState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .graphicsLayer {
                            val b = subPageBlurPulse.value.coerceIn(0f, 1f) * 12f * density
                            applyDirectionalMotionBlur(blurX = b, blurY = b * 0.08f)
                        },
                    transitionSpec = {
                        fun depth(state: Triple<SettingsCategory?, Boolean, Boolean>): Int = when {
                            state.second || state.third -> 2
                            state.first != null -> 1
                            else -> 0
                        }
                        if (depth(targetState) > depth(initialState)) {
                            (slideInHorizontally(
                                initialOffsetX = { fullWidth -> (fullWidth * 0.32f).toInt() },
                                animationSpec = ConceptMotion.spatialPageSpring()
                            ) + androidx.compose.animation.scaleIn(
                                initialScale = 0.968f,
                                animationSpec = ConceptMotion.spatialPageSpring()
                            ) + fadeIn(tween(durationMillis = 240, easing = ConceptMotion.EmphasizedDecelerate))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> -(fullWidth * 0.11f).toInt() },
                                    animationSpec = ConceptMotion.spatialPageSpring()
                                ) + androidx.compose.animation.scaleOut(
                                    targetScale = 0.975f,
                                    animationSpec = ConceptMotion.spatialPageSpring()
                                ) + fadeOut(tween(durationMillis = 220, easing = ConceptMotion.EmphasizedAccelerate)))
                        } else {
                            (slideInHorizontally(
                                initialOffsetX = { fullWidth -> -(fullWidth * 0.11f).toInt() },
                                animationSpec = ConceptMotion.spatialPageSpring()
                            ) + androidx.compose.animation.scaleIn(
                                initialScale = 0.975f,
                                animationSpec = ConceptMotion.spatialPageSpring()
                            ) + fadeIn(tween(durationMillis = 240, easing = ConceptMotion.EmphasizedDecelerate))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> (fullWidth * 0.32f).toInt() },
                                    animationSpec = ConceptMotion.spatialPageSpring()
                                ) + androidx.compose.animation.scaleOut(
                                    targetScale = 0.968f,
                                    animationSpec = ConceptMotion.spatialPageSpring()
                                ) + fadeOut(tween(durationMillis = 220, easing = ConceptMotion.EmphasizedAccelerate)))
                        }
                    },
                    label = "SettingsSubPageTransition"
                ) { (category, isChangelogPage, isDownloadHistoryPage) ->
                    val sidePad = if (adaptiveInfo.isLargeScreenDevice) 24.dp else 16.dp
                    val maxCardW = if (adaptiveInfo.isLargeScreenDevice) 820.dp else androidx.compose.ui.unit.Dp.Unspecified
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (category == null) {
                            renderMasterMenuList(
                                Modifier
                                    .then(if (maxCardW != androidx.compose.ui.unit.Dp.Unspecified) Modifier.widthIn(max = maxCardW) else Modifier)
                                    .fillMaxSize()
                                    .padding(horizontal = sidePad),
                                null
                            )
                        } else {
                            renderDetailCategoryPane(
                                Modifier
                                    .then(if (maxCardW != androidx.compose.ui.unit.Dp.Unspecified) Modifier.widthIn(max = maxCardW) else Modifier)
                                    .fillMaxSize()
                                    .padding(horizontal = sidePad),
                                category,
                                isChangelogPage,
                                isDownloadHistoryPage
                            )
                        }
                    }
                }
            }
        }
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
                ConceptBorderlessChoicePill(
                    selected = offsetMs == presetMs,
                    label = if (presetMs > 0) "+${presetMs}ms" else "${presetMs}ms",
                    accentColor = ConceptCyanAccent,
                    onClick = { onOffsetChange(presetMs) }
                )
            }
        }
    }
}

@Composable
internal fun SettingsGroupHeader(title: String) {
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
internal fun SettingsMenuEntryCard(
    category: SettingsCategory,
    icon: ImageVector,
    accentColor: Color,
    statusText: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val containerBg = if (selected) {
        accentColor.copy(alpha = 0.16f).compositeOver(conceptFloatingCardColor())
    } else {
        conceptFloatingCardColor()
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = containerBg,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .softCardClickable(shape = RoundedCornerShape(22.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                        fontSize = 15.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (statusText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.13f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontSize = 11.2.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 108.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "进入${category.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 现代无边框色阶选择胶囊 (ConceptBorderlessChoicePill)：
 * - 彻底摒弃传统 FilterChip / AssistChip 的生硬 1dp 外边框线；
 * - 采用柔和色阶背景填充 + 选中态呼吸圆点指示器 + `softCardClickable` 弹性按压缩放回弹。
 */
@Composable
internal fun ConceptBorderlessChoicePill(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = ConceptPrimary,
    leadingIcon: ImageVector? = null
) {
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            accentColor.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "pillContainerColor"
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            accentColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 180),
        label = "pillContentColor"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.softCardClickable(
            shape = RoundedCornerShape(12.dp),
            pressScale = 0.93f,
            onClick = onClick
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            } else if (selected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 12.2.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * 现代无边框色阶操作按钮 (ConceptTonalActionButton)：
 * - 替代生硬的 OutlinedButton，使用主题色半透明柔光底座承载图标与文案，支持物理弹性按压反馈。
 */
@Composable
internal fun ConceptTonalActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = ConceptPrimary,
    icon: ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.14f),
        contentColor = accentColor,
        modifier = modifier.softCardClickable(
            shape = RoundedCornerShape(12.dp),
            pressScale = 0.94f,
            onClick = onClick
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
internal fun SettingToggleRow(
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
internal fun SettingsSectionCard(
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
