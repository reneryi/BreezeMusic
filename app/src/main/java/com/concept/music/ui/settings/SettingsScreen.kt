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
    const val GITHUB_LATEST_RELEASE_WEB_URL = "https://github.com/reneryi/breezemusic/releases/latest"
    const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/reneryi/breezemusic/releases/latest"
    const val GITHUB_RELEASES_LIST_API = "https://api.github.com/repos/reneryi/breezemusic/releases?per_page=5"
    const val GHPROXY_LATEST_RELEASE_API = "https://ghfast.top/https://api.github.com/repos/reneryi/breezemusic/releases/latest"
    const val GHPROXY_RELEASES_LIST_API = "https://ghfast.top/https://api.github.com/repos/reneryi/breezemusic/releases?per_page=5"
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
internal fun buildStandardIssueMarkdownTemplate(
    appVersionName: String = BuildConfig.VERSION_NAME,
    appVersionCode: Int = BuildConfig.VERSION_CODE,
    deviceBrand: String = Build.MANUFACTURER ?: "Android",
    deviceModel: String = Build.MODEL ?: "Device",
    androidRelease: String = Build.VERSION.RELEASE ?: "Unknown",
    sdkInt: Int = Build.VERSION.SDK_INT
): String {
    return """
        ### 问题类型 (Issue Type)
        - [x] BUG 缺陷 / 异常崩溃
        - [ ] 功能建议 / 体验优化
        - [ ] 音效 / 歌词 / 界面显示异常

        ### 运行环境 (Environment)
        - **应用版本**：BreezeMusic v$appVersionName (Build $appVersionCode)
        - **设备型号**：$deviceBrand $deviceModel
        - **系统版本**：Android $androidRelease (API $sdkInt)
        - **音频设备**：例如 手机扬声器 / Type-C 有线耳机 / 蓝牙耳机
        - **网络与状态**：例如 Wi-Fi / 已登录酷狗 VIP

        ### 复现步骤 (Steps to Reproduce)
        1. 打开微风音乐，进入「...」页面
        2. 点击「...」按钮或播放歌曲「...」
        3. 观察到「...」异常

        ### 期望行为 (Expected Behavior)
        请描述正常情况下预期的正确表现。

        ### 实际表现与日志截图 (Actual Behavior & Logs)
        请描述实际发生的问题、Toast 提示，并在此处粘贴截图或日志。
    """.trimIndent()
}

@Immutable
data class ChangelogModuleGroup(
    val moduleTitle: String,
    val points: List<String>
)

@Immutable
data class ChangelogVersionItem(
    val version: String,
    val date: String,
    val tag: String,
    val summary: String,
    val highlights: List<String>,
    val moduleGroups: List<ChangelogModuleGroup> = emptyList()
)

internal val PROJECT_CHANGELOG_HISTORY = listOf(
    ChangelogVersionItem(
        version = "v${BuildConfig.VERSION_NAME}",
        date = "2026-10-31",
        tag = "正式版 · 平板竖屏/横屏深度打磨与双端体验升级",
        summary = "平板端悬浮导航栏移至左侧垂直居中、电台预览双行大图排版、修复我的页歌单列表/网格切换与平板竖屏点击横屏播放 90° 全屏翻转；双端新增多歌手选择悬浮窗（播放页动态取色毛玻璃适配）、音效页复刻心流舱灵动滑动标题切换动画、新歌速递日期分组物理弹簧丝滑收折、修复随心听抽屉内更多弹窗层级遮挡及关注歌手取关 PKCS#1 加密报错",
        highlights = listOf(
            "平板端导航与排版专项精修（严格隔离平板版与手机版）：将平板竖屏/分屏下的「发现 / 我的」悬浮胶囊导航栏移至屏幕左侧垂直居中（Alignment.CenterStart），底部悬浮播放条直接贴底居中展示，彻底消除底部双胶囊堆叠；平板发现页「精选场景电台」改为两行横向网格预览并将封面尺寸增大至 168dp；修复平板「我的」页自建/收藏歌单列表视图切换无效的问题",
            "平板竖屏点击「横屏播放」全屏翻转修复：针对 Android 16 (API 36, sw>=600dp) 忽略方向锁并产生上下黑边兼容小窗（Letterboxing）的机制，重构平板竖屏点击「横屏播放」按钮为 90° 全屏画布翻转（宽高约束互换 + 陀螺仪正反横向握持自适应），点击后立即如手机般横向铺满整块屏幕（与平板横屏图四状态 100% 一致）",
            "多歌手（>=2位）智能选择悬浮窗：当单曲包含 2 位及以上合作歌手时，在歌曲列表「更多 -> 查看歌手」与播放页「更多 -> 歌手主页」点击后弹出悬浮选择窗口，异步加载各位歌手高清头像与单曲数，支持任选一位直达其歌手主页；播放页内弹窗深度适配封面动态取色（rememberCoverColorRoles）流光毛玻璃风格",
            "音效页灵动标题栏动画与新歌速递丝滑收折：在「蝰蛇音效与调音台」顶部三栏切换（蝰蛇声效 / 声场调音 / 参量均衡）复刻心流舱（FocusPodBottomSheet）同源的 ExpressiveMicroSpring 滑动胶囊指示器、水滴微拉伸与文字微缩放动效；将「新歌速递」页面日期分组的展开/收起升级为 spatialPageSpring 连续高度折叠动画与箭头弹性旋转",
            "随心听抽屉层级修复与关注歌手取关协议修复：将首页「猜你喜欢 · 随心听」与「场景电台快捷抽屉」由独立系统 DialogWindow 的 ModalBottomSheet 迁移至主窗口 ConceptAnimatedOverlaySheet (zIndex = 80f)，确保点击歌曲「更多」按钮时操作菜单 (zIndex = 88f) 始终浮于随心听抽屉之上；严格对齐 MakcRe/KuGouMusicApi (module/artist_unfollow.js & util/crypto.js)，将取关/关注接口的 RSA 加密由无填充模幂修正为 RSAES-PKCS1-V1_5 (KugouSigner.rsaPkcs1)，并打通 singerid 与 target_userid 双向映射，彻底解决关注歌手无法取关报错问题"
        ),
        moduleGroups = listOf(
            ChangelogModuleGroup(
                moduleTitle = "一、平板版专项交互与布局打磨（BuildConfig.IS_TABLET_FLAVOR 严格隔离）",
                points = listOf(
                    "左侧垂直居中悬浮胶囊导航栏：平板竖屏与分屏模式下，「发现 / 我的」采用垂直悬浮胶囊置于屏幕左侧居中，内置垂直流体拉伸滑块；底部 MiniPlayerBar 无需再向上偏移避让底栏，视觉更加通透舒展",
                    "精选场景电台两行大图预览：平板发现页电台预览模块升级为 2 行横向网格滚动（chunked(2)），并将电台封面由 148dp 增大至 168dp",
                    "歌单视图切换与横屏播放全屏翻转：移除 LibraryScreen 对平板竖屏列表视图的硬编码拦截，恢复列表/网格自由切换；平板竖屏点击「横屏播放」通过 90° 全屏画布翻转消除 Android 16 上下黑边小窗，将平板横过来即可获得与原生横屏完全一致的左封面+右歌词合并舞台"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "二、多歌手选择悬浮窗与随心听抽屉 Z 轴层级修复",
                points = listOf(
                    "新增 parseSongArtistNames 与 ArtistSelectionFloatingDialog：自动识别 、 / & ， , feat. ft. 等多歌手分隔符，当歌手数 >= 2 时弹出悬浮选择窗并异步补全每位歌手头像；列表端采用主题色阶卡片，播放页端采用随封面动态着色的流光玻璃拟态面板",
                    "修复随心听抽屉内点击「更多」被遮挡问题：将「猜你喜欢 · 随心听」与「电台快捷漫游」抽屉统一迁移至应用主窗口 ConceptAnimatedOverlaySheet (zIndex = 80f)，使 SongActionBottomSheet (zIndex = 88f) 与 AddToPlaylistDialog (zIndex = 92f) 自然呈现在随心听抽屉上方"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "三、音效页灵动标题切换动画与新歌速递丝滑收折",
                points = listOf(
                    "音效页顶栏复刻心流舱切换动画：ViperSoundEffectScreen 三大分区导航栏接入 ConceptMotion.expressiveSpring() 滑动胶囊底座 + 跨栏水滴拉伸形变 + 图标文字弹性微缩放",
                    "新歌速递日期分组丝滑展开/收起：NewSongExpressScreen 日期分组接入 AnimatedVisibility (expandVertically / shrinkVertically + spatialPageSpring + fadeIn/fadeOut) 与箭头 expressiveSpring 旋转，消除生硬跳变"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "四、关注歌手取关失败修复（对齐 MakcRe/KuGouMusicApi）",
                points = listOf(
                    "修复 /followservice/v3/unfollow_singer 与 /followservice/v3/follow_singer 的密钥加密方式：对齐 MakcRe/KuGouMusicApi 的 rsaEncrypt2 (RSA/ECB/PKCS1Padding，即 KugouSigner.rsaPkcs1)，修复此前使用裸 RSA 导致服务端解密 AES key 失败的问题",
                    "完善 /v4/follow_list 中 singerid 与 target_userid 的双向映射及本地取关缓存同步，确保在歌手详情页与我的关注列表中均可一键稳定取关"
                )
            )
        )
    ),
    ChangelogVersionItem(
        version = "v1.2.0",
        date = "2026-10-30",
        tag = "正式版 · 平板与大屏自适应、高清封面保存与横屏体验升级",
        summary = "基于 WindowSizeClass 与 Android 16 规范构建全场景平板/分屏自适应架构（主界面双栏平铺与侧边导航栏、设置页左右分栏、独立详情页与搜索页多栏自适应、MiniPlayer 居中限宽、平板横屏封面与歌词合并舞台）、新增歌曲/专辑/歌单封面与歌手头像高清原图预览保存（同步存储读写权限）、重构横屏播放页双层独立跑马灯与歌词设置弹窗防截断 UI、支持手机与平板双安装包独立签名区分",
        highlights = listOf(
            "全场景平板与大屏自适应架构（适配 Android 16 API 36 与多窗口分屏）：基于 WindowSizeClass 实时监听应用窗口 dp 断点；平板横屏（Expanded）将「发现」与「我的」左右双栏同屏平铺并隐藏底栏；平板竖屏或分屏窗口（Medium）自动切换为左侧 NavigationRail 侧边导航栏，优先展示发现页并采用双列网格与受控阅读宽度；悬浮播放条（MiniPlayerBar）自动限宽底部居中",
            "设置页、搜索页与独立详情页大屏重构：设置页在宽屏下支持 Master-Detail 左右并排分栏；搜索发现页与搜索结果页支持按分类左右分栏看板及双列网格；歌单详情（含我喜欢/最近）、专辑详情、歌手主页、每日推荐、排行榜与 VIP 中心全面支持「左固定信息头 + 右独立滚动列表」双栏排版；平板横屏播放页将封面与歌词左右合并，放大封面与控播坞并整合非重复工具栏",
            "高清封面与歌手头像原图预览下载（同步「存储读写与相册导出」权限，与歌词海报同级）：支持歌曲封面、专辑封面、歌单封面、歌手头像 1000P 原图导出至系统相册（不含榜单和电台封面）；歌曲列表更多按钮新增「保存歌曲封面」；播放页长按黑胶/方形封面快捷预览并提供纯图标无文字下载按钮；专辑/歌单/歌手主页点击封面或头像即可预览并保存",
            "横屏播放页歌名/歌手跑马灯与歌词设置窗口 UI 深度修复：将横屏控播坞重构为「上歌名 + 下歌手」双层独立无限跑马灯（iterations = Int.MAX_VALUE），彻底解决长歌手名抢占导致短歌名（如 Vibe）消失及跑马灯停滞截断问题；歌词解析自动反转义 &apos; 等 HTML 实体；将歌词设置弹窗中的「歌词对齐」与「时长校准」拆分为独立行，彻底解决 +0.2s 截断并对齐左右双栏高度",
            "全量版本更新日志归档与手机/平板双签名构建体系：在「设置 -> 关于 -> 更新日志」完整收录 v1.0.0、v1.0.1、v1.0.2、v1.1.0、v1.2.0 每次发布版本详情；Gradle 构建配置新增 phone 与 tablet 两个 Product Flavors 并绑定独立签名配置（phoneSign / tabletSign），支持编译发布双平台独立安装包"
        ),
        moduleGroups = listOf(
            ChangelogModuleGroup(
                moduleTitle = "一、平板与大屏自适应架构（Android 16 API 36 & WindowSizeClass）",
                points = listOf(
                    "构建零外部依赖的 ConceptAdaptiveLayoutInfo 响应式断点引擎，实时感知窗口宽度（Compact <600dp / Medium 600..839dp / Expanded >=840dp）与多窗口分屏状态，移除大屏设备锁死竖屏限制",
                    "主界面三级自适应：平板横屏下「发现」与「我的」一左一右双栏平铺并隐藏底栏；平板竖屏与分屏窗口切换为 ConceptNavigationRail 左侧边导航栏（优先显示发现页，列表升级为双列网格或受控宽度单列）；手机端保持底部胶囊导航与同平面满屏弹性横滑",
                    "悬浮播放控制条（MiniPlayerBar）在大屏横竖屏及分屏窗口下自动限制最大宽度（420dp~500dp）并底部居中悬浮，告别横向过度拉长",
                    "设置中心支持 Master-Detail 左右并排分栏常驻展示；搜索页支持左右双栏热搜与分类看板；歌单、专辑、歌手、日推、榜单等独立单页支持左信息看板 + 右列表双栏布局；平板横屏播放页合并封面页与歌词页，清除重复按钮并放大封面与控件比例"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "二、高清封面与歌手头像原图预览及保存",
                points = listOf(
                    "新增 HdArtworkSaver 与 HdArtworkPreviewDialog，将高清封面/头像保存同步纳入「设置 -> 权限管理 -> 存储读写与相册导出 (STORAGE_EXPORT)」管控，与 2K 歌词海报处于同一权限级别",
                    "覆盖歌曲封面、专辑封面、歌单封面、歌手头像四大范围（自动升级酷狗 1000×1000 原图源并支持多级回退，严格排除榜单与电台封面）",
                    "打通三类保存入口：① 歌曲列表更多菜单（非播放页更多菜单）新增「保存歌曲封面」；② 播放页长按黑胶或方形封面弹出高清预览与无文字纯图标下载按钮；③ 专辑详情、歌单详情、歌手主页点击封面或头像弹出高清缩放预览与保存按钮"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "三、横屏播放页歌名/歌手显示与歌词设置弹窗 UI 修复",
                points = listOf(
                    "重构 LandscapeBottomControlDock 曲目信息区为「上歌名 + 下歌手」双层错落布局，赋予各自独立行宽与无限循环跑马灯（iterations = Int.MAX_VALUE），彻底修复歌手名过长导致歌名（如 Vibe）宽度归零不可见及跑马灯滚动三次后半截停滞（如 Need Som）的缺陷",
                    "KrcDecoder 新增 HTML 实体反转义（unescapeLyricEntities），自动还原歌词中的 &apos;、&quot;、&amp; 等转义字符",
                    "重构 LyricSettingsDialog 左栏卡片排版，将「歌词对齐（居左/居中/居右）」与「时长校准（-0.2s / 校准 0.0s / +0.2s）」拆分为上下独立两行，彻底消除横屏双栏下 +0.2s 被截断问题，并使左栏与右栏五滑块卡片高度均衡对齐"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "四、全版本更新日志记录与手机/平板独立签名打包",
                points = listOf(
                    "补全 v1.0.0、v1.0.1、v1.0.2、v1.1.0、v1.2.0 全部历史发布版本的更新日志记录，每个版本均可独立展开查阅核心亮点与模块明细",
                    "在 app/build.gradle.kts 中配置 phone 与 tablet 两个 Product Flavors 及独立签名证书（phoneSign 与 tabletSign），支持针对手机端与平板端分别编译带独立签名的 Release / Debug 安装包"
                )
            )
        )
    ),
    ChangelogVersionItem(
        version = "v1.1.0",
        date = "2026-10-29",
        tag = "正式版 · 视觉动效与性能大版本",
        summary = "单层 96dp 极速启动页与双套深浅主题独立持久化、设置中心全面无边框美化与分级收折、全局 120fps 欠阻尼弹性动效与方向性运动虚化、全链路低功耗/熄屏零唤醒与 120fps 列表上下滑动满帧率优化、64-bit 母带 DSP 与免 VPN 登录解析稳固",
        highlights = listOf(
            "单层 96dp 黄金比例启动页与双套深浅主题独立持久化：统一系统级 Starting Window 为 96dp 圆角图标并移除二级开屏层，通过 SplashScreen.setSplashScreenTheme 与 UiModeManager.setApplicationNightMode 双重持久化「幽邃微蓝（深色）」与「水彩浸润（浅色）」两套启动页，彻底不受手机系统暗色模式干扰，首帧即刻直达主页",
            "全局 120fps 欠阻尼弹性物理动效与方向性运动虚化：打造 ConceptMotion 五维分层弹簧体系，底部「发现 ↔ 我的」升级为同平面 100% 满屏左右滑动并叠加 GPU RenderEffect 水平运动虚化；全项目 8 大底部抽屉与弹窗统一接入零重组 ConceptAnimatedOverlaySheet 弹性架构；分类标题下方内容流支持同平面弹性切换与左右虚化",
            "设置中心全面去边框化美化与信息层级减负：彻底移除设置页全部生硬 1dp 边框线组件（FilterChip / OutlinedButton / AssistChip），升级为无边框色阶胶囊 ConceptBorderlessChoicePill 与色阶按钮 ConceptTonalActionButton；精简一级菜单副标题、支持权限与缓存卡片按需展开收折，并精简「我的」页 VIP 权益中心卡片文案",
            "全链路低功耗、后台熄屏零唤醒与 120fps 上下滑动满帧率：暂停态彻底退出进度轮询（0 唤醒/秒），后台播放自适应节流至 200ms/400ms/1000ms；锁屏熄屏瞬间停止后台桌面歌词 Ticker 与重绘，消除 NativeDesktopLyricView.onDraw 每帧对象分配与冗余 WindowManager 跨进程 IPC；「我的」页顶栏 100% 移入绘制期闭包（滑动 0 重组/0 重排），构建全局缩略图降维管线并补齐 LazyColumn contentType",
            "64-bit 母带 DSP 短路加速与国内免 VPN 全链路稳固：ConceptMasteringAudioProcessor 对 0dB 平直频段执行整组短路跳过以降低 Hi-Res 后台解码功耗；支持国内网络免 VPN 验证码/扫码多通道容灾登录、未登录访客态音源解析与 jsDelivr 多源免 VPN 在线更新检查"
        ),
        moduleGroups = listOf(
            ChangelogModuleGroup(
                moduleTitle = "一、单层 96dp 极速启动页与双套深浅主题独立持久化",
                points = listOf(
                    "将系统级 Starting Window 图标统一重构为 208dp Canvas 内嵌 96dp 黄金比例圆角图标（ic_splash_window_icon_light / dark），彻底移除冗余的第二层 Compose 开屏页，消除大图标跳小图标的割裂感与 850ms+240ms 阻塞等待",
                    "针对 ColorOS / HyperOS / OriginOS / OneUI 及原生 Android 12+，结合 Activity.splashScreen.setSplashScreenTheme 与 UiModeManager.setApplicationNightMode 双重持久化启动页主题，确保幽邃微蓝（深色）与水彩浸润（浅色）启动页与 App 内部主题 100% 同步，不受系统暗色模式影响",
                    "下沉非核心组件初始化与 PlaybackService 绑定至首帧渲染之后，配合主界面首屏封面静默预热，实现点击桌面图标即刻丝滑进入主页面"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "二、全局 120fps 欠阻尼弹性物理动效与方向性运动虚化",
                points = listOf(
                    "基于播放队列抽屉丝滑范式提炼 ConceptMotion 五维物理弹簧体系（coplanarTabSpring / spatialPageSpring / spatialSheetSpring / categoryContentSpring / floatingPopupSpring，阻尼比 ζ = 0.75~0.82）",
                    "底部「发现 ↔ 我的」主页面切换重构为与底栏滑块同向的 100% 同平面左右弹性滑动，并在 Android 12+ 绘制期实时施加 RenderEffect 水平方向性运动虚化（applyDirectionalMotionBlur）",
                    "全项目 8 大底部抽屉与弹窗（歌曲菜单、技术参数、VIP 中心、专注舱、加入歌单、新建歌单、单曲下载、2K 歌词海报）全面接入零重组 ConceptAnimatedOverlaySheet 架构，通过 graphicsLayer 闭包实现满帧率弹性入场、跟手下拉阻尼与平滑退出",
                    "二级独立页面卡片进出升级为带微妙景深缩放的弹性推入与底层 -12% 视差联动；同平面分类标题下方内容流（新歌速递、排行榜、我的资产、歌手详情、搜索页）支持顺向弹性滑移与左右运动虚化；播放页倍速变调与睡眠定时悬浮面板接入三轴弹性展开"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "三、设置中心全面无边框美化、布局减负与细节打磨",
                points = listOf(
                    "彻底清除设置中心全部生硬的 1dp 边框线组件（FilterChip、OutlinedButton、AssistChip 与 Modifier.border），全新设计无边框色阶选择胶囊 ConceptBorderlessChoicePill（柔和色阶填充 + 选中呼吸圆点 + 弹性按压回弹）与无边框色阶操作按钮 ConceptTonalActionButton",
                    "重构设置一级菜单与二级子页信息层级：一级菜单副标题精简为一行微摘要 + 右侧亮色胶囊；权限管理卡片默认仅展示核心标题与开关，点击「查看权限详情」按需展开技术说明；存储与缓存页将四维明细与清理操作整合进单张卡片",
                    "精简「我的」页 VIP 权益中心横幅卡片文案（「每日签到 · 畅享无损」配短状态词「免费领取 / 今日已领 / 去登录」），消除左右文案重复，视觉层次更清爽直观"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "四、全链路低功耗、后台熄屏零唤醒与 120fps 列表上下滑动满帧率",
                points = listOf(
                    "MusicPlayerViewModel 引入前后台生命周期感知（onAppForegroundChanged）：暂停且无解析任务时彻底退出主线程轮询（0 次/秒唤醒）；退至后台播放时将进度轮询从 120ms 自适应放宽至 200ms/400ms/1000ms，主线程后台唤醒降低 88%~100%",
                    "PlaybackService 监听 ACTION_SCREEN_OFF / ACTION_SCREEN_ON 并结合 PowerManager.isInteractive，手机锁屏熄屏瞬间立即暂停后台桌面歌词 160ms Ticker；SystemLyricBridge 预缓存 joinedWordsText 与行布局规划，消除 NativeDesktopLyricView.onDraw 每帧内存分配及冗余 WindowManager.updateViewLayout 跨进程 IPC",
                    "NowPlayingSheet 硬件方向传感器（OrientationEventListener）与黑胶旋转、圆角流光着色器、内嵌/全屏逐字歌词等 120Hz 帧循环均按前台状态（rememberIsLifecycleStarted）与视图可见性精准休眠；LyricData 新增 hasInterludeCandidateAt O(1) 间奏预判，消除 95% 以上非间奏时段的 120Hz 空转",
                    "LibraryScreen 沉浸式顶栏滚动因子 scrollFractionState 100% 移入 drawBehind 与 graphicsLayer 绘制期闭包读取，上下滑动全程 0 次整屏重组与 0 次 Layout 重排；构建 normalizeKugouThumbUrl 与 rememberOptimizedAsyncImageModel 全局缩略图降维管线（自动将 /400/、/1000/ 等大图降维至 150/240 并禁用列表 crossfade），升级长歌单渐进加载批次（首屏 60 首 + 步长 100 首）与评论页快速滑屏 180ms 防抖，全量补齐 LazyColumn contentType"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "五、64-bit 母带 DSP 短路加速与国内免 VPN 全链路稳固",
                points = listOf(
                    "ConceptMasteringAudioProcessor 预统计非 Bypass 的有效均衡器频段与子模块状态（hasActiveEqBands 等），在 44.1kHz~96kHz PCM 采样热循环中对 0dB 平直频段直接整组短路跳过，显著降低后台 Hi-Res 播放 CPU 占用与发热",
                    "完整保留 64-bit 双精度 Biquad 10 段参量均衡、声场调音、Schroeder 立体声混响与 120ms~360ms 低延迟硬件缓冲，以及国内免 VPN 验证码/扫码多通道容灾登录、未登录访客态音源解析与 jsDelivr 多源免 VPN 更新检查"
                )
            )
        )
    ),
    ChangelogVersionItem(
        version = "v1.0.2",
        date = "2026-10-29",
        tag = "正式版 · 多音质下载内嵌与 STREAMINFO 硬件级探测",
        summary = "新增多音质本地下载与零依赖 FLAC/MP3 1000P 高清封面及双语歌词二进制内嵌、自定义音乐与 .lrc 歌词保存路径、FLAC STREAMINFO 硬件级真实采样率与位深探测、权限管理第 8 项独立下载开关、全新深海微风黑胶流光图标",
        highlights = listOf(
            "多音质本地下载与零依赖二进制元数据内嵌：自研 AudioMetadataEmbedder，支持将标题、歌手、专辑、发行日期、1000×1000 高清封面与双语 LRC 歌词直接写入 FLAC (Vorbis Comment + Picture Block) 与 MP3 (ID3v2.3 TIT2/TPE1/TALB/USLT/APIC)，并识别 OGG 容器防损坏",
            "自定义保存目录与独立「下载」设置中心：支持通过系统 SAF 文件夹选择器（OpenDocumentTree）或手动输入自定义音乐下载路径与 .lrc 歌词路径，提供「最近下载记录」二级管理页与同名文件智能防重跳过",
            "FLAC STREAMINFO 硬件级真实采样率与位深探测：遵循 RFC 9639 标准通过 HTTP Range 读取音频流前 42~512 字节直接解析 FLAC STREAMINFO、OGG Vorbis 识别头与 MP3 帧头，精准还原 44.1kHz / 48.0kHz / 96.0kHz / 192.0kHz 真实采样率与 16bit / 24bit 位深",
            "权限管理新增「音乐下载与歌词保存」独立开关（默认关闭隐藏，开启后解锁设置页下载项与歌曲下载菜单），并换装全新「深海微风 · 黑胶流光」自适应应用图标"
        ),
        moduleGroups = listOf(
            ChangelogModuleGroup(
                moduleTitle = "一、多音质音频下载与零依赖高清封面/歌词内嵌",
                points = listOf(
                    "构建 MusicDownloadManager 与 AudioMetadataEmbedder，支持 Hi-Res / SQ 无损 / 320K / 128K 自动解析降级下载",
                    "对 FLAC 格式重写 Metadata Block 链表注入 Vorbis Comment (Block 4) 与 1000P 高清封面 Picture (Block 6)；对 MP3 格式写入 ID3v2.3 UTF-16LE 标签帧、USLT 同步歌词与 APIC 封面帧",
                    "支持系统 SAF 目录选择器自定义音频与独立 .lrc 歌词保存目录，并在「设置 -> 权限管理」提供默认关闭的「音乐下载与歌词保存」独立总开关"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "二、FLAC STREAMINFO 硬件级真实音频参数探测",
                points = listOf(
                    "通过轻量 HTTP Range (bytes=0-511) 请求提取音频流头部字节，精确解析真实采样率（如 44.1kHz / 24bit Hi-Res）与声道数，消除按码率估算的偏差",
                    "重绘「深海微风 · 黑胶流光」自适应应用图标并同步至关于页品牌横幅与启动窗口"
                )
            )
        )
    ),
    ChangelogVersionItem(
        version = "v1.0.1",
        date = "2026-10-28",
        tag = "正式版 · 跨应用桌面歌词、耳机线控与透明权限中心",
        summary = "上线系统级跨应用全局悬浮桌面歌词与通知栏/流体云快捷控制、有线耳机双击/三击切歌与三档音频焦点混音策略、蓝牙歌词延迟自动补偿、存储缓存四维分项统计清理、GitHub 多源免 VPN 更新检查与透明权限管控中心",
        highlights = listOf(
            "系统级跨应用全局悬浮桌面歌词：支持横向全屏自适应宽度、三行恒定高度容器（短句双行插空居中、长句自然三行换行不抖动）、封面动态取色实时同步、物理点击穿透锁定，以及系统通知栏与 OPPO 流体云「桌面歌词开关 / 锁定解锁」快捷按钮",
            "外设耳机线控与三档音频焦点混音：新增 HeadsetMediaButtonController 支持有线耳机中键单击暂停、双击下一首、三击上一首（支持方向互换与三档灵敏度防误触）；支持独占焦点、压低音量闪避（Ducking）与多应用完全混音同播，以及蓝牙音频歌词延迟自动补偿",
            "存储缓存四维分项统计与透明权限管理中心：AudioCacheManager 支持音频流、图片缓存、歌单 SWR 快照与运行期歌词四维明细统计及 512MB~10GB 上限调节；上线公开透明的「权限管理」二级页与 GitHub 多通道免 VPN 在线更新检查"
        ),
        moduleGroups = listOf(
            ChangelogModuleGroup(
                moduleTitle = "一、跨应用全局桌面悬浮歌词与通知栏/流体云联动",
                points = listOf(
                    "实现 NativeDesktopLyricView 与 SystemLyricBridge 跨应用悬浮窗渲染，支持 KRC 逐字卡拉 OK 扫光、App 内外字号/配色（「封面随动」等）双向实时同步与纵向位置记忆",
                    "在 Media3 PlaybackService 媒体会话中注入通知栏与流体云自定义按钮，支持一键开启/关闭桌面歌词及解锁穿透状态"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "二、耳机线控定制、多应用混音与透明权限管控",
                points = listOf(
                    "支持有线耳机物理中键多击切歌、拔出耳机自动暂停、插入耳机自动续播及蓝牙连接状态下的歌词时间轴自动补偿",
                    "构建 AppPermissionManager 公开披露各项权限用途、隐私承诺与关闭影响，并提供预填充设备环境的标准 GitHub Issue 反馈模板"
                )
            )
        )
    ),
    ChangelogVersionItem(
        version = "v1.0.0",
        date = "2026-10-28",
        tag = "正式版 · 首发",
        summary = "微风音乐正式版发布：完整实现酷狗概念版端侧直连、FLAC STREAMINFO 硬件级真实采样率探测、64-bit 母带 DSP、KRC 逐字流光与穿透桌面歌词、多音质下载与高清封面/歌词二进制内嵌、全新微风流光图标与开屏动效",
        highlights = listOf(
            "酷狗概念版纯端侧直连与 VIP 闭环：纯 Kotlin 移植 MakcRe/KuGouMusicApi 概念版（appid=3116, clientver=11440）签名加密与 dfid 风控设备注册，支持验证码/扫码登录、每日畅听领 VIP、升级 VIP、CSCC 听歌时长上报与 JobScheduler 后台静默守护",
            "高解析音源、FLAC STREAMINFO 真实参数探测与 64-bit 母带 DSP：支持 Hi-Res / SQ 无损 / 320K / 128K 智能解析回退，直接读取音频流前 512 字节 STREAMINFO/帧头提取与椒盐音乐一致的硬件级真实采样率（44.1kHz/48kHz/96kHz/192kHz）与位深（16bit/24bit）；内置 64-bit PCM 母带管线、10 段参量均衡器与蝰蛇音效",
            "多音质本地下载、1000P 高清封面/歌词二进制内嵌与独立 .lrc 导出：自研零依赖 AudioMetadataEmbedder 写入 FLAC Vorbis Comment (Block 4) + Picture (Block 6) 与 MP3 ID3v2.3 (TIT2/TPE1/TALB/TYER/USLT/APIC)，支持自定义音乐与歌词保存路径、独立「下载」设置页与二级下载记录",
            "KRC 逐字流光歌词与系统级穿透桌面歌词：端侧解密 KRC 逐字时间戳、双语译文与罗马音注音，支持阶梯景深模糊、前奏/间奏呼吸波点、2K 超清歌词海报生成，以及支持物理穿透锁定、封面取色随动与通知栏/流体云快捷控制的全局桌面歌词",
            "全新品牌视觉、开屏动效与外设/权限透明管控：设计「深海微风 · 黑胶流光」自适应图标与沉浸式开屏页面；提取封面生成色相锁定的 13 级明度色阶，提供仿真黑胶唱臂、120Hz 高刷横竖屏播放页、三档音频焦点混音/闪避、有线耳机双击/三击切歌与 8 大权限独立透明开关"
        ),
        moduleGroups = listOf(
            ChangelogModuleGroup(
                moduleTitle = "一、酷狗概念版端侧直连与 VIP 权益闭环",
                points = listOf(
                    "纯 Kotlin 端侧完整实现 MakcRe/KuGouMusicApi 概念版（appid=3116, clientver=11440）签名、RSA/AES 加解密与 dfid 设备注册，零中间服务器直连",
                    "支持手机验证码登录（含同手机号多酷狗账号切换）、扫码登录及 Android Keystore 硬件级凭证加密存储",
                    "打通概念版每日签到畅听领 VIP、升级 VIP 权益、CSCC 三段式真实听歌时长上报与 JobScheduler 后台静默续期守护",
                    "完整覆盖每日推荐、私人 FM、新歌速递、排行榜、歌单广场、歌手/专辑详情与四级楼中楼评论区交互（含动态表情与评论图片查看）"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "二、高解析音源、STREAMINFO 真实参数探测与 64-bit 母带 DSP",
                points = listOf(
                    "支持 Hi-Res (res_hash) / SQ 无损 (sq_hash) / 高品 320K / 标准 128K 多通道智能解析与平滑降级，支持移动网络省流保护",
                    "遵循 RFC 9639 标准通过轻量 Range 请求解析 FLAC STREAMINFO 前 42 字节（及 OGG Vorbis 识别头、MP3 MPEG 帧头），精准呈现 44.1kHz / 48.0kHz / 96.0kHz / 192.0kHz 真实硬件采样率与 16bit / 24bit 位深",
                    "自研 64-bit/Float PCM 母带音频管线：内置 10 段参量均衡器、蝰蛇声效预设、动态超重低音、人声清澈度、电子管温暖度激励与 M/S 声场混响",
                    "支持高潮智能试听（Climax 副歌直达）、跨曲淡入淡出无缝衔接（Crossfade）、ReplayGain 音量平衡与倍速/升降调独立调节"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "三、多音质音乐下载、高清封面与歌词二进制内嵌",
                points = listOf(
                    "参考 lisalee23042605/kgmusicbot 架构并纯 Kotlin 原生重写：支持多音质音频文件流式下载与同名文件智能防重跳过",
                    "自研零外部依赖 AudioMetadataEmbedder：对 FLAC 写入 Vorbis Comment (TITLE/ARTIST/ALBUM/DATE/LYRICS/UNSYNCEDLYRICS) 与 Picture Block (type=3)，对 MP3 写入 ID3v2.3 (TIT2/TPE1/TALB/TYER/COMM/USLT/APIC)，并识别 OGG 容器防损坏",
                    "默认自动拉取 1000×1000 高清专辑封面与双语 LRC 歌词内嵌至音频文件，支持按需开启同步生成独立 .lrc 歌词文件",
                    "支持通过系统 SAF 目录选择器或手动输入自定义音乐保存路径与歌词保存路径；设置页提供独立「下载」模块与「最近下载记录」二级子页面"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "四、KRC 逐字流光歌词、全局桌面歌词与 2K 海报",
                points = listOf(
                    "端侧解密 KRC 逐字时间戳、双语译文与罗马音注音，支持字内卡拉 OK 平滑扫光、前奏/间奏呼吸波点与阶梯景深模糊",
                    "系统级跨应用全局桌面悬浮歌词：支持横向全屏自适应、双行插空居中与长句自然三行换行、封面取色实时随动、物理穿透锁定及通知栏/流体云快捷开关",
                    "内置 2K 超清多模板歌词海报生成器，支持挑选任意歌词段落、切换视觉样式并一键导出至系统相册"
                )
            ),
            ChangelogModuleGroup(
                moduleTitle = "五、流体视觉、品牌图标/开屏动效与外设/权限透明管控",
                points = listOf(
                    "全新设计「深海微风 · 黑胶流光」自适应应用图标（Adaptive Icon）与沉浸式动态波浪开屏页面，全链路统一关于页与桌面视觉",
                    "提取专辑封面生成色相锁定的 13 级 Tonal Palette 动态取色系统（满足 WCAG 对比度标准），提供仿真黑胶与圆角方图封面及五款动态背景，支持 120Hz 高刷横竖屏自适应舞台",
                    "支持「允许与其他应用同时播放」及三档音频焦点策略（独占 / 压低音量闪避 Ducking / 完全混音），提供有线耳机暂停键双击/三击切歌、连接耳机自动续播与蓝牙歌词延迟自动补偿",
                    "公开透明的权限管理中心：详细披露 8 项系统与功能权限用途，支持独立开关管控（「音乐下载与歌词保存」默认关闭并隐藏，需手动开启后解锁）"
                )
            )
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

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    return runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@runCatching false
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
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
                        releaseNotes = releaseNotes.ifBlank { "包含体验优化与问题修复，建议更新至最新版本。" },
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
                releaseNotes = "发现新版本 $normalizedTag，点击下方按钮可前往 GitHub Releases 查看更新详情并下载安装包。",
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
        .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    var confirmedUpToDate: UpdateCheckResult.UpToDate? = null
    val cacheBuster = System.currentTimeMillis() / 15000L

    // 1. 优先请求实时性最高的 GitHub Releases API（含国内免 VPN 加速镜像 + 官方直连 + Releases 列表接口）
    // 关键修复：若某个镜像因缓存滞后返回 UpToDate 或 404，绝不提前短路退出，而是继续核实后续实时通道，只有发现 HasUpdate 时才立即返回！
    val releaseApiEndpoints = listOf(
        "${ProjectLinks.GHPROXY_LATEST_RELEASE_API}?_t=$cacheBuster",
        "${ProjectLinks.GHPROXY_MIRROR_LATEST_RELEASE_API}?_t=$cacheBuster",
        "${ProjectLinks.GITHUB_LATEST_RELEASE_API}?_t=$cacheBuster",
        "${ProjectLinks.GHPROXY_RELEASES_LIST_API}&_t=$cacheBuster",
        "${ProjectLinks.GITHUB_RELEASES_LIST_API}&_t=$cacheBuster"
    )
    for (apiEndpoint in releaseApiEndpoints) {
        val result = runCatching {
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
                } else {
                    null
                }
            }
        }.getOrNull()
        if (result is UpdateCheckResult.HasUpdate) return@withContext result
        if (result is UpdateCheckResult.UpToDate && confirmedUpToDate == null) {
            confirmedUpToDate = result
        }
    }

    // 2. 尝试 GitHub Web /releases/latest 实时 302 Location 重定向探测（零 API 限流、零 CDN 缓存延迟）
    val noRedirectClient = fastClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
    for (webUrl in listOf(
        "https://ghfast.top/${ProjectLinks.GITHUB_LATEST_RELEASE_WEB_URL}",
        ProjectLinks.GITHUB_LATEST_RELEASE_WEB_URL
    )) {
        val webResult = runCatching {
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
        if (webResult is UpdateCheckResult.HasUpdate) return@withContext webResult
        if (webResult is UpdateCheckResult.UpToDate && confirmedUpToDate == null) {
            confirmedUpToDate = webResult
        }
    }

    // 3. 尝试国内免 VPN 直连的 GitHub Raw 镜像与 GCore / Fastly jsDelivr 源码分支 app/build.gradle.kts 版本号比对
    for (cdnGradleUrl in listOf(
        "${ProjectLinks.GHPROXY_RAW_GRADLE_MAIN_URL}?_t=$cacheBuster",
        "${ProjectLinks.GHPROXY_RAW_GRADLE_MASTER_URL}?_t=$cacheBuster",
        ProjectLinks.GCORE_JSDELIVR_GRADLE_URL,
        ProjectLinks.FASTLY_JSDELIVR_GRADLE_URL
    )) {
        val cdnVer = runCatching {
            val req = Request.Builder()
                .url(cdnGradleUrl)
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "BreezeMusic-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            fastClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) extractVersionFromGradleText(resp.body?.string()) else null
            }
        }.getOrNull()
        if (!cdnVer.isNullOrBlank()) {
            val gradleResult = resolveUpdateCheckFromApiOrRedirect(
                localVersionName = cleanLocal,
                apiHttpCode = 200,
                apiBodyJson = null,
                redirectFinalUrl = "${ProjectLinks.GITHUB_REPO_URL}/releases/tag/v${extractCleanSemverString(cdnVer)}"
            )
            if (gradleResult is UpdateCheckResult.HasUpdate) return@withContext gradleResult
            if (gradleResult is UpdateCheckResult.UpToDate && confirmedUpToDate == null) {
                confirmedUpToDate = gradleResult
            }
        }
    }

    // 4. 兜底查询 jsDelivr Package 元数据接口（置于末尾，防止其 CDN 缓存延迟屏蔽刚发布的 GitHub Release）
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
    if (jsdResult is UpdateCheckResult.UpToDate && confirmedUpToDate == null) {
        confirmedUpToDate = jsdResult
    }

    confirmedUpToDate ?: UpdateCheckResult.UpToDate(
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

/**
 * 独立「下载」二级页面（仅在权限管理中手动开启「音乐下载与歌词保存」后可见）：
 * 采用清晰、整洁的模块化四卡片分组设计，并将「最近下载记录」独立为二级子页面进入。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadSettingsSectionContent(
    context: Context,
    refreshTick: Int,
    onOpenDownloadHistory: () -> Unit
) {
    val config by MusicDownloadManager.config.collectAsState()
    val tasks by MusicDownloadManager.tasks.collectAsState()

    val storageGrantStatus = remember(refreshTick) {
        AppPermissionManager.querySystemGrantStatus(context, AppPermissionType.STORAGE_EXPORT)
    }
    val allFilesGranted = remember(refreshTick) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    }

    var showCustomDownloadPathDialog by remember { mutableStateOf(false) }
    var customDownloadPathInput by remember(config.downloadDirectoryPath) {
        mutableStateOf(config.downloadDirectoryPath)
    }

    var showCustomLyricPathDialog by remember { mutableStateOf(false) }
    var customLyricPathInput by remember(config.lyricDirectoryPath) {
        mutableStateOf(config.lyricDirectoryPath)
    }

    val downloadDirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val resolvedPath = MusicDownloadManager.resolveTreeUriToFilePath(context, treeUri)
            if (!resolvedPath.isNullOrBlank()) {
                MusicDownloadManager.setDownloadDirectoryPath(resolvedPath)
                Toast.makeText(context, "音乐保存路径已更新：$resolvedPath", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "所选目录无法映射为本地路径，请使用手动输入", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val lyricDirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val resolvedPath = MusicDownloadManager.resolveTreeUriToFilePath(context, treeUri)
            if (!resolvedPath.isNullOrBlank()) {
                MusicDownloadManager.setLyricDirectoryPath(resolvedPath)
                Toast.makeText(context, "歌词保存路径已更新：$resolvedPath", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "所选目录无法映射为本地路径，请使用手动输入", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val completedCount = remember(tasks) {
        tasks.count { it.status == DownloadTaskStatus.COMPLETED || it.status == DownloadTaskStatus.SKIPPED_EXISTING }
    }
    val activeCount = remember(tasks) {
        tasks.count {
            it.status == DownloadTaskStatus.QUEUED ||
                it.status == DownloadTaskStatus.RESOLVING ||
                it.status == DownloadTaskStatus.DOWNLOADING ||
                it.status == DownloadTaskStatus.EMBEDDING
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 卡片 1：存储权限授权（直跳系统权限页）与「最近下载记录」二级菜单入口
        SettingsSectionCard(
            title = "存储授权与下载记录",
            icon = Icons.Rounded.Download
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (allFilesGranted || storageGrantStatus.isSystemGranted) {
                                    ConceptMintAccent.copy(alpha = 0.14f)
                                } else {
                                    ConceptAmberAccent.copy(alpha = 0.14f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (allFilesGranted || storageGrantStatus.isSystemGranted) {
                                Icons.Rounded.VerifiedUser
                            } else {
                                Icons.Rounded.FolderOff
                            },
                            contentDescription = null,
                            tint = if (allFilesGranted || storageGrantStatus.isSystemGranted) ConceptMintAccent else ConceptAmberAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "手机存储读写权限",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = storageGrantStatus.statusLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allFilesGranted) ConceptMintAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.8.sp
                        )
                    }
                }

                ConceptTonalActionButton(
                    text = "存储权限设置",
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    accentColor = ConceptPrimary,
                    onClick = {
                        AppPermissionManager.openSystemPermissionSetting(context, AppPermissionType.STORAGE_EXPORT)
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // 二级菜单入口：最近下载记录
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                modifier = Modifier
                    .fillMaxWidth()
                    .softCardClickable(shape = RoundedCornerShape(16.dp), onClick = onOpenDownloadHistory)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(ConceptPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = ConceptPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "最近下载记录",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    activeCount > 0 -> "正在下载 $activeCount 首 · 已完成 $completedCount 首"
                                    tasks.isNotEmpty() -> "共 ${tasks.size} 条记录 · 已完成 $completedCount 首"
                                    else -> "查看下载队列、内嵌状态与本地文件位置"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.8.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "进入最近下载记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 卡片 2：默认下载音质档位（整洁的 2x2 对称网格卡片）
        SettingsSectionCard(
            title = "默认下载音质",
            icon = Icons.Rounded.HighQuality
        ) {
            Text(
                text = "优先按所选音质下载，若目标歌曲无该档位将按 Hi-Res → SQ → 320K → 128K 自动平滑降级",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.8.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            val qualityRows = listOf(
                listOf(
                    Triple(AudioQuality.HI_RES, "Hi-Res 高解析", "24bit 无损 FLAC"),
                    Triple(AudioQuality.LOSSLESS, "SQ 无损音质", "16bit CD级 FLAC")
                ),
                listOf(
                    Triple(AudioQuality.HIGH, "HQ 高品 320K", "320kbps MP3"),
                    Triple(AudioQuality.STANDARD, "标准 128K", "128kbps MP3/OGG")
                )
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualityRows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (qOption, qTitle, qSpec) ->
                            val isSelected = config.defaultDownloadQuality == qOption
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    ConceptMintAccent.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable(shape = RoundedCornerShape(14.dp)) {
                                        MusicDownloadManager.setDefaultDownloadQuality(qOption)
                                        Toast.makeText(context, "默认下载音质已设为 ${qOption.label}", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = qTitle,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) ConceptMintAccent else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.5.sp
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = ConceptMintAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = qSpec,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 卡片 3：自定义保存路径设置（音乐下载路径 + 独立 .lrc 歌词保存路径）
        SettingsSectionCard(
            title = "保存路径设置",
            icon = Icons.Rounded.Folder
        ) {
            // 1. 音乐下载保存路径
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "音乐保存路径",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { runCatching { downloadDirPickerLauncher.launch(null) } },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选择文件夹", fontSize = 11.5.sp)
                    }
                    TextButton(
                        onClick = {
                            customDownloadPathInput = config.downloadDirectoryPath
                            showCustomDownloadPathDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("手动输入", fontSize = 11.5.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = ConceptAmberAccent,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = config.downloadDirectoryPath,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.8.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // 2. 独立 .lrc 歌词保存路径
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "歌词保存路径 (.lrc)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { runCatching { lyricDirPickerLauncher.launch(null) } },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选择文件夹", fontSize = 11.5.sp)
                    }
                    TextButton(
                        onClick = {
                            customLyricPathInput = config.lyricDirectoryPath
                            showCustomLyricPathDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("手动输入", fontSize = 11.5.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = ConceptCyanAccent,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = config.lyricDirectoryPath,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.8.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        MusicDownloadManager.setLyricDirectoryPath(config.downloadDirectoryPath)
                        Toast.makeText(context, "已将歌词路径设为与音乐目录相同", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("歌词与音乐同目录", fontSize = 11.5.sp, color = ConceptCyanAccent)
                }
                TextButton(
                    onClick = {
                        MusicDownloadManager.resetDirectoriesToDefault()
                        Toast.makeText(context, "已恢复默认下载与歌词目录", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("恢复默认路径", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 卡片 4：音频标签内嵌与歌词导出选项
        SettingsSectionCard(
            title = "音频内嵌与歌词选项",
            icon = Icons.Rounded.Lyrics
        ) {
            SettingToggleRow(
                title = "内嵌高清封面与歌曲信息",
                subtitle = "自动写入 1000P 高清专辑封面、歌名、歌手、专辑与发行年份标签",
                checked = config.embedHdCover,
                onCheckedChange = { MusicDownloadManager.setEmbedHdCover(it) },
                thumbColor = ConceptPrimary
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            SettingToggleRow(
                title = "内嵌歌词到音频文件",
                subtitle = "自动将时间轴歌词写入 FLAC (LYRICS) 与 MP3 (USLT) 内嵌标签",
                checked = config.embedLyrics,
                onCheckedChange = { MusicDownloadManager.setEmbedLyrics(it) },
                thumbColor = ConceptCyanAccent
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            SettingToggleRow(
                title = "独立保存 .lrc 歌词文件",
                subtitle = "下载歌曲时同步在「歌词保存路径」下生成同名 .lrc 文件",
                checked = config.saveStandaloneLrcFile,
                onCheckedChange = { MusicDownloadManager.setSaveStandaloneLrcFile(it) },
                thumbColor = ConceptMintAccent
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            SettingToggleRow(
                title = "歌词包含双语翻译",
                subtitle = "外语歌曲存在中文译文时，在内嵌与导出歌词中附加双语行",
                checked = config.includeTranslationInLyric,
                onCheckedChange = { MusicDownloadManager.setIncludeTranslationInLyric(it) },
                thumbColor = ConceptVioletAccent
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            SettingToggleRow(
                title = "跳过已存在的同名歌曲",
                subtitle = "目标路径下已存在同名音频文件时自动跳过，避免重复下载",
                checked = config.skipExistingFiles,
                onCheckedChange = { MusicDownloadManager.setSkipExistingFiles(it) },
                thumbColor = ConceptAmberAccent
            )
        }
    }

    if (showCustomDownloadPathDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDownloadPathDialog = false },
            title = { Text("自定义音乐保存路径", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "请输入手机存储内的目录路径（不存在将自动创建）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customDownloadPathInput,
                        onValueChange = { customDownloadPathInput = it },
                        singleLine = true,
                        label = { Text("音乐保存目录路径") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "/storage/emulated/0/Music/BreezeMusic",
                            "/storage/emulated/0/Download/BreezeMusic",
                            "/storage/emulated/0/Music"
                        ).forEach { preset ->
                            ConceptBorderlessChoicePill(
                                selected = customDownloadPathInput == preset,
                                label = preset.removePrefix("/storage/emulated/0/"),
                                accentColor = ConceptCyanAccent,
                                onClick = { customDownloadPathInput = preset }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customDownloadPathInput.trim()
                        if (trimmed.isNotEmpty()) {
                            MusicDownloadManager.setDownloadDirectoryPath(trimmed)
                            Toast.makeText(context, "音乐保存路径已更新", Toast.LENGTH_SHORT).show()
                        }
                        showCustomDownloadPathDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDownloadPathDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCustomLyricPathDialog) {
        AlertDialog(
            onDismissRequest = { showCustomLyricPathDialog = false },
            title = { Text("自定义歌词保存路径 (.lrc)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "请输入独立 .lrc 歌词文件的保存目录路径：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customLyricPathInput,
                        onValueChange = { customLyricPathInput = it },
                        singleLine = true,
                        label = { Text("歌词保存目录路径") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "/storage/emulated/0/Music/BreezeMusic/Lyrics",
                            "/storage/emulated/0/Download/BreezeMusic/Lyrics",
                            config.downloadDirectoryPath
                        ).distinct().forEach { preset ->
                            ConceptBorderlessChoicePill(
                                selected = customLyricPathInput == preset,
                                label = preset.removePrefix("/storage/emulated/0/"),
                                accentColor = ConceptCyanAccent,
                                onClick = { customLyricPathInput = preset }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customLyricPathInput.trim()
                        if (trimmed.isNotEmpty()) {
                            MusicDownloadManager.setLyricDirectoryPath(trimmed)
                            Toast.makeText(context, "歌词保存路径已更新", Toast.LENGTH_SHORT).show()
                        }
                        showCustomLyricPathDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomLyricPathDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 「最近下载记录」独立二级子页面：
 * 展示全部下载任务状态、进度、已内嵌标签详情与保存文件路径，支持一键清理已完成记录。
 */
@Composable
private fun DownloadHistorySubPageContent() {
    val tasks by MusicDownloadManager.tasks.collectAsState()
    val completedCount = remember(tasks) {
        tasks.count { it.status == DownloadTaskStatus.COMPLETED || it.status == DownloadTaskStatus.SKIPPED_EXISTING }
    }
    val activeCount = remember(tasks) {
        tasks.count {
            it.status == DownloadTaskStatus.QUEUED ||
                it.status == DownloadTaskStatus.RESOLVING ||
                it.status == DownloadTaskStatus.DOWNLOADING ||
                it.status == DownloadTaskStatus.EMBEDDING
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsSectionCard(
            title = "下载队列概览",
            icon = Icons.Rounded.History
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "共 ${tasks.size} 条下载记录",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "已完成 $completedCount 首 · 进行中 $activeCount 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (tasks.isNotEmpty()) {
                    ConceptTonalActionButton(
                        text = "清理已完成",
                        icon = Icons.Rounded.CleaningServices,
                        accentColor = ConceptPrimary,
                        onClick = { MusicDownloadManager.clearCompletedTasks() }
                    )
                }
            }
        }

        if (tasks.isEmpty()) {
            SettingsSectionCard(
                title = "暂无记录",
                icon = Icons.Rounded.DownloadDone
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = "暂无最近下载记录",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "在歌曲更多菜单或播放页点击「下载歌曲」即可将音频与歌词保存至本地",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 11.5.sp
                    )
                }
            }
        } else {
            tasks.forEach { task ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = conceptFloatingCardColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = task.song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = task.song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.8.sp
                                )
                            }
                            val statusColor = when (task.status) {
                                DownloadTaskStatus.COMPLETED, DownloadTaskStatus.SKIPPED_EXISTING -> ConceptMintAccent
                                DownloadTaskStatus.FAILED -> ConceptRoseAccent
                                else -> ConceptPrimary
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = statusColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${task.actualQuality.shortBadge} · ${task.status.label}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (task.status == DownloadTaskStatus.DOWNLOADING ||
                            task.status == DownloadTaskStatus.EMBEDDING ||
                            task.status == DownloadTaskStatus.RESOLVING
                        ) {
                            LinearProgressIndicator(
                                progress = { task.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(CircleShape),
                                color = ConceptPrimary
                            )
                        }

                        if (task.status == DownloadTaskStatus.COMPLETED || task.status == DownloadTaskStatus.SKIPPED_EXISTING) {
                            val tagsSummary = buildList {
                                if (task.embeddedMetadata) add("歌曲信息已内嵌")
                                if (task.embeddedCover) add("高清封面已内嵌")
                                if (task.embeddedLyric) add("歌词已内嵌")
                                if (task.savedLrcPath.isNotBlank()) add("独立 .lrc 已保存")
                            }.joinToString(" · ").ifBlank { "文件已保存" }
                            Text(
                                text = tagsSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = ConceptMintAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (task.savedAudioPath.isNotBlank()) {
                                Text(
                                    text = task.savedAudioPath,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        } else if (task.status == DownloadTaskStatus.FAILED && !task.errorMessage.isNullOrBlank()) {
                            Text(
                                text = task.errorMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = ConceptRoseAccent,
                                fontSize = 11.sp
                            )
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
                    ConceptBorderlessChoicePill(
                        selected = cacheLimit == limitOption,
                        label = limitOption.label,
                        accentColor = ConceptAmberAccent,
                        onClick = {
                            AudioCacheManager.setCacheLimit(context, limitOption)
                            Toast.makeText(context, "音频缓存上限已设为 ${limitOption.label}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                var cacheRefreshSpin by remember { mutableIntStateOf(0) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ConceptPrimary.copy(alpha = 0.14f),
                    modifier = Modifier
                        .height(38.dp)
                        .softCardClickable(shape = RoundedCornerShape(12.dp)) {
                            cacheRefreshSpin += 1
                            AudioCacheManager.refreshCacheSize(context)
                            Toast.makeText(context, "已刷新各项缓存统计", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConceptFluidRefreshIcon(
                            tint = ConceptPrimary,
                            spinTrigger = cacheRefreshSpin,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("刷新统计", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ConceptPrimary)
                    }
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
                description = "在线播放时自动保存的 Hi-Res / SQ 无损与高品音频数据块，再次播放同一歌曲时免缓冲且不消耗流量。",
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
                description = "包含专辑高清封面、歌词海报底图、歌手写真、歌单封面及评论区配图缓存。",
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
                title = "歌单与发现页本地快照",
                badgeText = "${stats.swrSnapshotCount} 份歌单离线快照",
                sizeText = AudioCacheManager.formatSize(stats.swrMetadataBytes),
                description = "每日推荐、私人 FM、新歌速递、排行榜及个人收藏歌单的启动加速缓存，清理后联网会自动重新同步，不影响云端歌单。",
                accentColor = ConceptMintAccent,
                icon = Icons.Rounded.FlashOn,
                actionLabel = "清理快照",
                onClearClick = {
                    AudioCacheManager.clearSwrMetadataCacheOnly(context) {
                        Toast.makeText(context, "已清理歌单与发现页本地快照", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            CacheCategoryDetailRow(
                title = "逐字歌词与音源直链缓存",
                badgeText = "歌词 ${stats.lyricMemoryCount} 首 · 音源元数据 ${stats.runtimeMetaCount} 条",
                sizeText = AudioCacheManager.formatSize(stats.otherTempBytes),
                description = "包含已解析的 KRC 逐字双语歌词、临时音源链接、专辑发行信息及安全验证临时文件。",
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
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = sizeText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            ConceptTonalActionButton(
                text = actionLabel,
                icon = Icons.Rounded.CleaningServices,
                accentColor = accentColor,
                onClick = onClearClick
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            lineHeight = 16.5.sp
        )
    }
}


/**
 * 权限管理二级页面（大气、高可读性的权限公开透明说明与自主开关中心）：
 * 1. 顶部全景概览横幅：直观呈现已开启权限数量、最小化隐私承诺及「系统权限设置 / 一键全部开启」快捷入口；
 * 2. 7 大核心权限独立卡片：清晰公开每项权限的 Android 系统标识、获取用途、隐私保护承诺，以及关闭后的具体功能影响；
 * 3. 独立自主开关：用户可随时开启或关闭任意一项权限，关闭后立即拦截或停用对应功能。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionsSectionContent(
    context: Context,
    permissionState: AppPermissionState,
    refreshTick: Int,
    onTogglePermission: (AppPermissionType, Boolean) -> Unit,
    onEnableAll: () -> Unit
) {
    val enabledCount = permissionState.enabledCount
    val totalCount = permissionState.totalCount
    val restrictedCount = (totalCount - enabledCount).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. 顶部大气概览 Hero 卡片
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = conceptFloatingCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ConceptMintAccent.copy(alpha = 0.16f),
                                    ConceptPrimary.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(ConceptMintAccent, ConceptPrimary)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "应用权限公开与自主管控",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 17.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = if (restrictedCount == 0) {
                                            "当前已启用全部 $totalCount 项权限 · 所有功能正常可用"
                                        } else {
                                            "已启用 $enabledCount / $totalCount 项 · 已自主关闭 $restrictedCount 项权限"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (restrictedCount == 0) ConceptMintAccent else ConceptAmberAccent
                                    )
                                }
                            }
                        }

                        Text(
                            text = "微风音乐遵循「最小化按需获取」与「纯端侧透明直连」原则。下方完整公开项目中声明与使用的每项权限用途，您可随时自主开关；关闭某项权限后，对应功能将立即停用，不影响其他无关模块正常运行。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "零私服纯端侧直连",
                                "用途透明公开",
                                "支持独立自主关停",
                                "绝不读取私人相册与通讯录"
                            ).forEach { badge ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ConceptMintAccent.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = badge,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ConceptMintAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ConceptTonalActionButton(
                                text = "系统应用权限页",
                                icon = Icons.Rounded.SettingsApplications,
                                accentColor = ConceptPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                onClick = { AppPermissionManager.openAppDetailsSettings(context) }
                            )

                            if (restrictedCount > 0) {
                                Button(
                                    onClick = onEnableAll,
                                    shape = RoundedCornerShape(13.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ConceptMintAccent,
                                        contentColor = Color(0xFF061310)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DoneAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "恢复开启全部",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. 逐项权限公开说明与独立开关卡片
        AppPermissionType.entries.forEach { permType ->
            val isEnabled = permissionState.isEnabled(permType)
            val systemGrant = remember(permType, refreshTick, isEnabled) {
                AppPermissionManager.querySystemGrantStatus(context, permType)
            }
            PermissionDisclosureCard(
                type = permType,
                enabled = isEnabled,
                systemGrant = systemGrant,
                onCheckedChange = { onTogglePermission(permType, it) },
                onOpenSystemSetting = {
                    AppPermissionManager.openSystemPermissionSetting(context, permType)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionDisclosureCard(
    type: AppPermissionType,
    enabled: Boolean,
    systemGrant: com.concept.music.service.SystemPermissionGrantStatus,
    onCheckedChange: (Boolean) -> Unit,
    onOpenSystemSetting: () -> Unit
) {
    val (icon, accentColor) = when (type) {
        AppPermissionType.NETWORK_ACCESS -> Icons.Rounded.Public to ConceptPrimary
        AppPermissionType.NOTIFICATION_MEDIA -> Icons.Rounded.NotificationsActive to ConceptCyanAccent
        AppPermissionType.SYSTEM_OVERLAY -> Icons.Rounded.PictureInPictureAlt to ConceptVioletAccent
        AppPermissionType.STORAGE_EXPORT -> Icons.Rounded.PhotoLibrary to ConceptAmberAccent
        AppPermissionType.MUSIC_DOWNLOAD -> Icons.Rounded.Download to ConceptCyanAccent
        AppPermissionType.BACKGROUND_GUARDIAN -> Icons.Rounded.BatteryChargingFull to ConceptMintAccent
        AppPermissionType.MOTION_ORIENTATION -> Icons.Rounded.ScreenRotation to ConceptCoralAccent
        AppPermissionType.AUDIO_HARDWARE -> Icons.Rounded.Headphones to ConceptRoseAccent
    }
    var detailsExpanded by rememberSaveable(type.name) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = conceptFloatingCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .softCardClickable(
                shape = RoundedCornerShape(22.dp),
                onClick = { detailsExpanded = !detailsExpanded }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. 卡片头部：色阶图标 + 权限标题 + 状态胶囊 + 独立开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (enabled) accentColor.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = type.title,
                            tint = if (enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = type.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accentColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = type.badge,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (enabled && systemGrant.isSystemGranted) {
                                    ConceptMintAccent.copy(alpha = 0.14f)
                                } else {
                                    ConceptAmberAccent.copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = if (!enabled) "应用内已关闭" else systemGrant.statusLabel,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (enabled && systemGrant.isSystemGranted) {
                                        ConceptMintAccent
                                    } else {
                                        ConceptAmberAccent
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor
                    )
                )
            }

            // 2. 一句话精简摘要（或关闭时的停用提示）+ 右侧展开详情/系统设置无框胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (enabled) type.purposeDescription else "已停用：${type.disabledImpactDescription}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else ConceptAmberAccent,
                    fontSize = 12.sp,
                    maxLines = if (detailsExpanded) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (systemGrant.actionButtonLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.13f),
                            modifier = Modifier.softCardClickable(
                                shape = RoundedCornerShape(8.dp),
                                onClick = onOpenSystemSetting
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = systemGrant.actionButtonLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        modifier = Modifier.softCardClickable(
                            shape = RoundedCornerShape(8.dp),
                            onClick = { detailsExpanded = !detailsExpanded }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (detailsExpanded) "收起" else "详情",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (detailsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // 3. 按需展开的技术标识、完整隐私承诺与关闭影响详情（避免默认满屏堆砌长文）
            AnimatedVisibility(
                visible = detailsExpanded,
                enter = expandVertically(animationSpec = ConceptMotion.spatialPageSpring()) + fadeIn(tween(180)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Code,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = type.manifestPermissionName,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "隐私与数据边界：${type.privacyCommitment}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.5.sp
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                            )
                            Text(
                                text = "关闭影响：${type.disabledImpactDescription}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else ConceptAmberAccent,
                                fontSize = 12.sp,
                                lineHeight = 17.5.sp
                            )
                        }
                    }
                }
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
                modifier = Modifier.fillMaxWidth()
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
                            shape = RoundedCornerShape(10.dp),
                            color = ConceptPrimary.copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "[BUG] 开启蝰蛇音效切换 Hi-Res 音质时偶发短暂杂音",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE6EDF3),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }

                        // ② 标签区（使用自适应流式单行胶囊，彻底杜绝窄屏下「enhancement · 建议」挤压折行变形）
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "标签：",
                                fontSize = 11.sp,
                                color = Color(0xFF8B949E),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
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
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF161B22),
                            modifier = Modifier.fillMaxWidth()
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

            ConceptTonalActionButton(
                text = "复制标准 Issue 反馈模板（附带本机环境）",
                icon = Icons.Rounded.ContentCopy,
                accentColor = ConceptPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                onClick = {
                    val template = buildStandardIssueMarkdownTemplate()
                    copyTextToClipboard(context, "GitHub Issue 模板", template)
                    Toast.makeText(context, "已复制标准 Issue 模板（含本机环境信息），可直接前往粘贴", Toast.LENGTH_LONG).show()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { openExternalUrl(context, ProjectLinks.GITHUB_ISSUES_URL) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConceptRoseAccent,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "浏览仓库 Issues",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = {
                        val template = buildStandardIssueMarkdownTemplate()
                        copyTextToClipboard(context, "GitHub Issue 模板", template)
                        Toast.makeText(context, "已自动复制模板，正在打开新建 Issue 页面...", Toast.LENGTH_SHORT).show()
                        openExternalUrl(context, ProjectLinks.GITHUB_NEW_ISSUE_URL)
                    },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "直接新建 Issue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
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
        modifier = Modifier.height(22.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 7.dp)
        ) {
            Text(
                text = text,
                fontSize = 9.5.sp,
                color = color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
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
 * 1. 大气居中的品牌版本 Hero 卡片 + 简洁无冗余的仓库入口、更新日志二级入口与检查更新按钮
 * 2. 凝练简洁的项目介绍（几句话概括核心定位与特性标签）
 * 3. 精简正式的免责与版权声明（保留第一点研究用途声明 + 知识产权归属与正版倡导 + 精简免责条款）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutAppSectionContent(
    context: Context,
    appVersionInfo: com.concept.music.ui.components.AppVersionInfo = com.concept.music.ui.components.resolveAppVersionInfo(context),
    updateState: UpdateCheckResult,
    onOpenChangelog: () -> Unit,
    onTriggerCheckUpdate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. 大气居中的品牌版本与核心入口卡片（同步使用全新设计的微风音乐应用图标与动态真实版本号）
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = conceptFloatingCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 顶部居中大气品牌横幅区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ConceptPrimary.copy(alpha = 0.16f),
                                    ConceptCyanAccent.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(top = 26.dp, bottom = 18.dp, start = 20.dp, end = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.concept.music.ui.components.BreezeAppLogo(
                            size = 74.dp,
                            cornerRadius = 22.dp,
                            showAmbientHalo = true
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "BreezeMusic · 微风音乐",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ConceptPrimary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = appVersionInfo.fullBadgeText,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ConceptPrimary,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "轻量 · 纯粹 · 高解析的第三方概念版客户端",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                // 底部简洁统一的三项导航与操作区（每个入口职责唯一，绝无一大一小重复按钮）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 入口 1：GitHub 开源仓库（点击整行直接跳转仓库，右侧附轻量复制胶囊）
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { openExternalUrl(context, ProjectLinks.GITHUB_REPO_URL) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(ConceptPrimary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Code,
                                        contentDescription = null,
                                        tint = ConceptPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "GitHub 开源仓库",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "github.com/${ProjectLinks.GITHUB_REPO_OWNER}/${ProjectLinks.GITHUB_REPO_NAME}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.5.sp,
                                        color = ConceptPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            copyTextToClipboard(context, "项目仓库地址", ProjectLinks.GITHUB_REPO_URL)
                                            Toast.makeText(context, "已复制仓库链接", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Text(
                                        text = "复制",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "前往项目仓库",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // 入口 2：版本更新日志（二级菜单入口，点击进入按版本号分类的可折叠日志页面）
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenChangelog)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(ConceptCyanAccent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = ConceptCyanAccent,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "更新日志",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "查看 ${appVersionInfo.displayVersion} 版本更新内容与发版记录",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.8.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = ConceptCyanAccent.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = appVersionInfo.displayVersion,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ConceptCyanAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "查看更新日志",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // 入口 3：检查更新按钮（大气通栏主按钮）
                    Button(
                        onClick = onTriggerCheckUpdate,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ConceptPrimary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (updateState is UpdateCheckResult.Checking) "正在连接 GitHub 检查更新..." else "检查更新",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 检查更新结果内联反馈
                    when (updateState) {
                        is UpdateCheckResult.Idle -> {}
                        is UpdateCheckResult.Checking -> {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = ConceptPrimary
                            )
                        }
                        is UpdateCheckResult.HasUpdate -> {
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
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ConceptMintAccent.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = ConceptMintAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "当前已是最新版本 (${updateState.versionText})",
                                                fontSize = 12.8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = updateState.message,
                                                fontSize = 11.2.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = { openExternalUrl(context, ProjectLinks.GITHUB_RELEASES_URL) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("查看 Releases", fontSize = 11.5.sp, color = ConceptMintAccent, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        is UpdateCheckResult.Error -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ConceptAmberAccent.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                        Text("前往查看", fontSize = 11.5.sp, color = ConceptAmberAccent, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. 精简版项目介绍（几句话说清核心定位与特点）
        SettingsSectionCard(
            title = "项目简介",
            icon = Icons.Rounded.AutoAwesome
        ) {
            Text(
                text = "BreezeMusic（微风音乐）是基于 Kotlin、Jetpack Compose 与 Media3 打造的第三方「酷狗概念版」原生客户端。项目参考开源方案 MakcRe/KuGouMusicApi 实现端侧直连与每日 VIP 畅听闭环，无任何中间服务器；集成了 Hi-Res 无损音源解析、64-bit 母带 DSP 音效、KRC 逐字流光与穿透桌面歌词、120fps 欠阻尼弹性物理动效与方向性运动虚化、全链路低功耗后台守护及多应用混音/硬件线控能力，致力于提供纯粹、丝滑的个人听歌体验。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.5.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "端侧零后端直连",
                    "Hi-Res 无损与母带 DSP",
                    "KRC 逐字与桌面歌词",
                    "120fps 弹性物理动效",
                    "全链路低功耗架构",
                    "M3 封面动态取色",
                    "混音同播与耳机线控"
                ).forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ConceptPrimary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = tag,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ConceptPrimary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 3. 精简正式的免责与版权声明（保留第一点，合并简化正版倡导与知识产权说明，精简免责条款）
        SettingsSectionCard(
            title = "免责与版权声明",
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
                        title = "二、知识产权归属与正版倡导",
                        content = "本客户端自身不存储、不托管任何音频文件，运行期间加载的音乐作品、歌词及封面等全部知识产权均归原版权方及对应内容平台所有。请广大用户尊重知识产权、支持正版音乐，前往官方正规渠道购买数字专辑或开通会员服务。"
                    )
                    DisclaimerClauseBlock(
                        title = "三、免责条款",
                        content = "本项目按「原样（AS-IS）」提供，仅限技术测试，请在下载或编译试用后 24 小时内卸载删除。因不当使用本软件引发的任何账号异常、版权纠纷或法律责任均由使用者自行承担，与开发者无关；如有权益疑问请通过 GitHub Issues 联系处理。"
                    )
                }
            }
        }
    }
}

/**
 * 更新日志独立二级页面：
 * 按版本号分类归档展示，每个版本卡片均支持点击展开和收起详情。
 * 首个正式版 v1.0.0 汇总项目的全部核心架构与功能模块，后续随版本迭代按版本号追加。
 */
@Composable
private fun ChangelogSubPageContent(
    appVersionInfo: com.concept.music.ui.components.AppVersionInfo? = null
) {
    val changelogItems = remember(appVersionInfo) {
        if (appVersionInfo != null && PROJECT_CHANGELOG_HISTORY.isNotEmpty()) {
            PROJECT_CHANGELOG_HISTORY.mapIndexed { index, item ->
                if (index == 0) item.copy(version = appVersionInfo.displayVersion) else item
            }
        } else {
            PROJECT_CHANGELOG_HISTORY
        }
    }
    var expandedVersions by rememberSaveable(appVersionInfo?.displayVersion) {
        mutableStateOf(listOf(changelogItems.firstOrNull()?.version ?: "v${BuildConfig.VERSION_NAME}"))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 按版本号分类的可折叠版本卡片列表（移除顶部冗余横条介绍，直接呈现版本卡片）
        changelogItems.forEachIndexed { idx, item ->
            val isExpanded = expandedVersions.contains(item.version)
            ChangelogVersionAccordionCard(
                item = item,
                isLatest = idx == 0,
                isExpanded = isExpanded,
                onToggleExpand = {
                    expandedVersions = if (isExpanded) {
                        expandedVersions - item.version
                    } else {
                        expandedVersions + item.version
                    }
                }
            )
        }
    }
}

@Composable
private fun ChangelogVersionAccordionCard(
    item: ChangelogVersionItem,
    isLatest: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = conceptFloatingCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onToggleExpand)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.version,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        ),
                        color = if (isLatest) ConceptPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLatest) ConceptPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = item.tag,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLatest) ConceptPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }
                    Text(
                        text = item.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "收起" else "展开",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ConceptPrimary
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (isExpanded) "收起版本日志" else "展开版本日志",
                            tint = ConceptPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.5.sp
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(160))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    if (item.moduleGroups.isNotEmpty()) {
                        item.moduleGroups.forEach { group ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = group.moduleTitle,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = ConceptPrimary,
                                        fontSize = 12.5.sp
                                    )
                                    group.points.forEach { pt ->
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
                                                text = pt,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 11.8.sp,
                                                lineHeight = 17.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    lineHeight = 17.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
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
private fun ConceptBorderlessChoicePill(
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
private fun ConceptTonalActionButton(
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
