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
 * - COMPACT: width < 600dp（主流手机竖屏、平板极窄分屏窗口）
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
 * - BOTTOM_BAR: 手机版（或平板极窄小窗）底部胶囊悬浮导航栏（发现 / 我的）
 * - NAVIGATION_RAIL: 平板版竖屏或分屏窗口（Medium 宽度）左侧边导航栏（Navigation Rail），优先展示发现页
 * - DUAL_PANE_HOME: 平板版横向大屏（Expanded 宽度且横向窗口）将「发现」与「我的」一左一右同屏平铺展示，隐藏底部与侧边主导航
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
 *    - 绝不会因为手机在播放页旋转至横屏（此时手机 widthDp 约 850dp >= 600dp）而误触发平板布局或打断横屏方向锁！
 * 2. 当 `isTabletFlavor == true`（平板版安装包）：
 *    - 全面启用基于 Google WindowSizeClass 的平板与多窗口分屏自适应布局（双栏平铺主页、侧边导航栏、设置页 Master-Detail、详情页左卡右表、双列网格、平板合并横屏播放页）。
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

    // 手机版安装包：严格固定为手机端策略，不启用任何平板多栏/侧边栏/大屏自适应变体
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

    // 平板版安装包：按当前应用窗口实际分到的 dp 宽度（WindowSizeClass）动态切换 UI 结构（完美兼容 16:10 / 3:2 / 7:5 全屏、竖屏与分屏）
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
        // 横向平板大窗口（Expanded 且非极矮分屏）：左「发现」+ 右「我的」一左一右平铺显示，底栏不显示
        widthClass == ConceptWidthSizeClass.EXPANDED && isLandscapeWindow && safeHeight >= 460 ->
            ConceptNavigationMode.DUAL_PANE_HOME
        // 平板竖屏或分屏窗口（Medium 宽度，或高窄比例的 Expanded 窗口）：切换为左侧 Navigation Rail
        widthClass != ConceptWidthSizeClass.COMPACT ->
            ConceptNavigationMode.NAVIGATION_RAIL
        // 平板开启极窄 1/3 分屏小窗（width < 600dp）：回退到底部导航栏保证可用性
        else ->
            ConceptNavigationMode.BOTTOM_BAR
    }

    // 列表是否采用双列网格（平板竖屏/分屏 Medium 窗口，或独立详情页的宽列表区）
    val useTwoColumnSongOrCardGrid = safeWidth >= 600
    // 独立详情页（歌单、专辑、歌手、日推、新歌、榜单、电台、VIP中心、搜索结果）是否采用左信息头 + 右列表的双栏架构
    val useTwoPaneDetailLayout = safeWidth >= 760 || (safeWidth >= 640 && isLandscapeWindow && safeHeight >= 420)
    // 设置页是否使用 Master-Detail 左右并排分栏
    val useMasterDetailSettings = safeWidth >= 600
    // 平板横向播放页：合并封面页与歌词页，左右优化布局并放大封面与控件
    val isTabletLandscapePlayer = isLandscapeWindow && safeWidth >= 680 && safeHeight >= 440

    // 悬浮播放条不要横向拉长，横向或竖屏/分屏使用时适当长度底部居中
    val miniPlayerMaxWidth = when {
        widthClass == ConceptWidthSizeClass.EXPANDED -> 500.dp
        widthClass == ConceptWidthSizeClass.MEDIUM -> 460.dp
        else -> 420.dp
    }

    // 受控宽度的单列容器最大宽度，防止平板竖屏或分屏下文本单行过长、卡片高度超出一屏
    val singleColumnMaxWidth = when {
        widthClass == ConceptWidthSizeClass.EXPANDED -> 820.dp
        widthClass == ConceptWidthSizeClass.MEDIUM -> 680.dp
        else -> Dp.Unspecified
    }

    val horizontalContentPadding = when (widthClass) {
        ConceptWidthSizeClass.EXPANDED -> 24.dp
        ConceptWidthSizeClass.MEDIUM -> 20.dp
        ConceptWidthSizeClass.COMPACT -> 16.dp
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
