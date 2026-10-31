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
 * 问题反馈与建议二级页面：
 * 1. 标准 GitHub Issue 提交可视化介绍示例图（仿真窗口 + ①~⑤ 编号标注）
 * 2. 「写什么、怎么写」详细规范说明（涵盖 BUG、接口报错、体验不足三类场景）
 * 3. 一键复制预填本机环境的 Markdown 模板 + 一键跳转 GitHub Issues 仓库页面
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackGuideSectionContent(context: Context) {
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
internal fun AboutAppSectionContent(
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
internal fun ChangelogSubPageContent(
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

