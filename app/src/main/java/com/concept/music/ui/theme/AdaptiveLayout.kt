package com.concept.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.concept.music.BuildConfig

/**
 * Google 官方 WindowSizeClass 宽度断点标准（适配 Android 16 API 36 大屏与多窗口分屏规范）：
 * - COMPACT: width < 600dp（主流手机竖屏、平板窄分屏窗口）
 * - MEDIUM: 600dp <= width < 840dp（平板竖屏、折叠屏展开态、平板横屏左右分屏窗口）
 * - EXPANDED: width >= 840dp（平板横屏全屏/宽窗口、桌面自由窗口模式）
 */
enum class ConceptWidthSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class ConceptHeightSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

/**
 * 主界面导航与布局架构模式：
 * - BOTTOM_BAR: 手机版及平板版竖屏/分屏窗口底部胶囊悬浮导航栏（发现 / 我的）
 * - NAVIGATION_RAIL: 保留枚举兼容
 * - DUAL_PANE_HOME: 平板版横向大屏（Expanded 宽度且横向窗口）将「发现」与「我的」一左一右同屏平铺展示，隐藏底部导航栏
 */
enum class ConceptNavigationMode {
    BOTTOM_BAR,
    NAVIGATION_RAIL,
    DUAL_PANE_HOME
}

@Immutable
data class ConceptAdaptiveLayoutInfo(
    val isTabletFlavor: Boolean,
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int,
    val widthSizeClass: ConceptWidthSizeClass,
    val heightSizeClass: ConceptHeightSizeClass,
    val isLandscapeWindow: Boolean,
    val isLargeScreenDevice: Boolean,
    val navigationMode: ConceptNavigationMode,
    val useTwoColumnSongOrCardGrid: Boolean,
    val useTwoPaneDetailLayout: Boolean,
    val useMasterDetailSettings: Boolean,
    val isTabletLandscapePlayer: Boolean,
    val miniPlayerMaxWidth: Dp,
    val singleColumnMaxWidth: Dp,
    val horizontalContentPadding: Dp
) {
    val isCompactWindow: Boolean get() = widthSizeClass == ConceptWidthSizeClass.COMPACT
    val isMediumWindow: Boolean get() = widthSizeClass == ConceptWidthSizeClass.MEDIUM
    val isExpandedWindow: Boolean get() = widthSizeClass == ConceptWidthSizeClass.EXPANDED
    val isWideWindow: Boolean get() = isLargeScreenDevice && widthSizeClass != ConceptWidthSizeClass.COMPACT
}

/**
 * 计算当前窗口的自适应布局状态：
 *
 * 严格区分「手机版安装包 (phone flavor, isTabletFlavor = false)」与「平板版安装包 (tablet flavor, isTabletFlavor = true)」：
 * 1. 当 `isTabletFlavor == false`（手机版安装包）：
 *    - 强制保持纯粹的手机端交互与布局（`isLargeScreenDevice = false`、`BOTTOM_BAR`、单列流、手机版横竖屏播放页切换）；
 *    - 绝不会因为手机在播放页旋转至横屏而误触发平板布局或打断横屏方向锁。
 * 2. 当 `isTabletFlavor == true`（平板版安装包）：
 *    - 针对平板横屏、平板竖屏与多窗口分屏（Split-screen）精细化区分断点：
 *      - 平板横屏全屏（`width >= 840dp` 且 `isLandscapeWindow`）：主界面启用 `DUAL_PANE_HOME`（发现+我的左右平铺，隐藏底栏），独立详情页启用 `useTwoPaneDetailLayout`（左信息卡+右列表），播放页启用 `isTabletLandscapePlayer`（左封面控播+右歌词合并）。
 *      - 平板竖屏或分屏窗口：去掉左侧边栏，主页面自动补充占满左侧空间，「发现」与「我的」采用与悬浮播放条同源的底部居中悬浮胶囊导航栏（`BOTTOM_BAR`），列表采用双列网格或受控宽度单列。
 */
fun computeAdaptiveLayoutInfo(
    widthDp: Int,
    heightDp: Int,
    smallestWidthDp: Int = minOf(widthDp, heightDp),
    isTabletFlavor: Boolean = true
): ConceptAdaptiveLayoutInfo {
    val safeWidth = widthDp.coerceAtLeast(280)
    val safeHeight = heightDp.coerceAtLeast(280)
    val isLandscapeWindow = safeWidth > safeHeight

    // 手机版安装包：严格固定为手机端策略，不启用任何平板多栏/大屏自适应变体
    if (!isTabletFlavor) {
        return ConceptAdaptiveLayoutInfo(
            isTabletFlavor = false,
            widthDp = safeWidth,
            heightDp = safeHeight,
            smallestWidthDp = smallestWidthDp,
            widthSizeClass = ConceptWidthSizeClass.COMPACT,
            heightSizeClass = if (isLandscapeWindow) ConceptHeightSizeClass.COMPACT else ConceptHeightSizeClass.MEDIUM,
            isLandscapeWindow = isLandscapeWindow,
            isLargeScreenDevice = false,
            navigationMode = ConceptNavigationMode.BOTTOM_BAR,
            useTwoColumnSongOrCardGrid = false,
            useTwoPaneDetailLayout = false,
            useMasterDetailSettings = false,
            isTabletLandscapePlayer = false,
            miniPlayerMaxWidth = Dp.Unspecified,
            singleColumnMaxWidth = Dp.Unspecified,
            horizontalContentPadding = 16.dp
        )
    }

    // 平板版安装包：按当前应用窗口实际分到的 dp 宽度（WindowSizeClass）动态切换 UI 结构
    val widthClass = when {
        safeWidth < 600 -> ConceptWidthSizeClass.COMPACT
        safeWidth < 840 -> ConceptWidthSizeClass.MEDIUM
        else -> ConceptWidthSizeClass.EXPANDED
    }
    val heightClass = when {
        safeHeight < 480 -> ConceptHeightSizeClass.COMPACT
        safeHeight < 900 -> ConceptHeightSizeClass.MEDIUM
        else -> ConceptHeightSizeClass.EXPANDED
    }
    val isLargeScreenDevice = true

    val navigationMode = when {
        // 横向平板大窗口（Expanded 且横向）：左「发现」+ 右「我的」一左一右平铺显示，底栏不显示
        widthClass == ConceptWidthSizeClass.EXPANDED && isLandscapeWindow && safeHeight >= 460 ->
            ConceptNavigationMode.DUAL_PANE_HOME
        // 平板竖屏或分屏窗口：去掉侧边导航栏，页面自动补充左侧，「发现」和「我的」使用底部居中悬浮胶囊导航栏
        else ->
            ConceptNavigationMode.BOTTOM_BAR
    }

    // 列表是否采用双列网格（当窗口宽度 >= 600dp 时启用双列网格；若分屏窗口窄于 600dp 则自动回退为受控单列，防止歌名截断）
    val useTwoColumnSongOrCardGrid = safeWidth >= 600
    // 独立详情页（歌单、专辑、歌手、日推、榜单、VIP、搜索综合）仅在横向宽窗口（>= 820dp 且横屏）下启用「左信息卡 + 右列表」双栏；
    // 平板竖屏或分屏窗口下保持上下布局 + 双列网格，彻底解决竖屏/分屏下左右分栏过窄挤压的问题！
    val useTwoPaneDetailLayout = isLandscapeWindow && safeWidth >= 820 && safeHeight >= 440
    // 设置页仅在横向宽窗口（isLandscapeWindow && safeWidth >= 760）下启用 Master-Detail 左右并排分栏；
    // 平板竖屏或窄分屏下采用舒展的单栏宽卡片布局，彻底避免竖屏下左侧分类卡片标题被挤压折行
    val useMasterDetailSettings = isLandscapeWindow && safeWidth >= 760
    // 平板横向播放页：合并封面页与歌词页（横向且宽度 >= 680dp）
    val isTabletLandscapePlayer = isLandscapeWindow

    // 悬浮播放条不要横向拉长，横向或竖屏/分屏使用时适当长度底部居中
    val miniPlayerMaxWidth = when {
        widthClass == ConceptWidthSizeClass.EXPANDED -> 500.dp
        widthClass == ConceptWidthSizeClass.MEDIUM -> 460.dp
        else -> 400.dp
    }

    // 受控宽度的单列容器最大宽度，防止平板竖屏或分屏下文本单行过长、卡片高度超出一屏
    val singleColumnMaxWidth = when {
        widthClass == ConceptWidthSizeClass.EXPANDED -> 820.dp
        widthClass == ConceptWidthSizeClass.MEDIUM -> 740.dp
        else -> Dp.Unspecified
    }

    val horizontalContentPadding = when (widthClass) {
        ConceptWidthSizeClass.EXPANDED -> 24.dp
        ConceptWidthSizeClass.MEDIUM -> 20.dp
        ConceptWidthSizeClass.COMPACT -> 14.dp
    }

    return ConceptAdaptiveLayoutInfo(
        isTabletFlavor = true,
        widthDp = safeWidth,
        heightDp = safeHeight,
        smallestWidthDp = smallestWidthDp,
        widthSizeClass = widthClass,
        heightSizeClass = heightClass,
        isLandscapeWindow = isLandscapeWindow,
        isLargeScreenDevice = isLargeScreenDevice,
        navigationMode = navigationMode,
        useTwoColumnSongOrCardGrid = useTwoColumnSongOrCardGrid,
        useTwoPaneDetailLayout = useTwoPaneDetailLayout,
        useMasterDetailSettings = useMasterDetailSettings,
        isTabletLandscapePlayer = isTabletLandscapePlayer,
        miniPlayerMaxWidth = miniPlayerMaxWidth,
        singleColumnMaxWidth = singleColumnMaxWidth,
        horizontalContentPadding = horizontalContentPadding
    )
}

val LocalConceptAdaptiveInfo = staticCompositionLocalOf {
    computeAdaptiveLayoutInfo(
        widthDp = 392,
        heightDp = 820,
        smallestWidthDp = 392,
        isTabletFlavor = false
    )
}

@Composable
fun currentConceptAdaptiveInfo(): ConceptAdaptiveLayoutInfo {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val smallestWidthDp = configuration.smallestScreenWidthDp
    val isTabletFlavor = runCatching { BuildConfig.IS_TABLET_FLAVOR }.getOrDefault(false)
    return remember(widthDp, heightDp, smallestWidthDp, isTabletFlavor) {
        computeAdaptiveLayoutInfo(
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = smallestWidthDp,
            isTabletFlavor = isTabletFlavor
        )
    }
}
