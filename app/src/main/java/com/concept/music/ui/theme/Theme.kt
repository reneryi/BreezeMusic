package com.concept.music.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class AppThemeMode(val label: String, val desc: String) {
    DARK_ABYSS_BLUE("幽邃微蓝", "深海暗夜基底，透出静谧微蓝光感"),
    LIGHT_WATERCOLOR("水彩浸润", "清透水彩晕染，纯白圆润悬浮卡片"),
    SYSTEM("跟随系统", "随系统日夜模式自动切换深浅主题");

    val subtitle: String get() = desc
}

object ThemeManager {
    private var prefs: SharedPreferences? = null
    private val _themeMode = MutableStateFlow(AppThemeMode.DARK_ABYSS_BLUE)
    val themeMode = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColor = _dynamicColor.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("concept_theme_prefs", Context.MODE_PRIVATE)
        prefs = p
        val savedMode = p.getString("theme_mode", AppThemeMode.DARK_ABYSS_BLUE.name) ?: AppThemeMode.DARK_ABYSS_BLUE.name
        _themeMode.value = runCatching { AppThemeMode.valueOf(savedMode) }.getOrDefault(AppThemeMode.DARK_ABYSS_BLUE)
        _dynamicColor.value = p.getBoolean("dynamic_color", false)
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString("theme_mode", mode.name)?.apply()
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        init(context)
        setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs?.edit()?.putBoolean("dynamic_color", enabled)?.apply()
    }
}

val LocalIsDarkTheme = staticCompositionLocalOf { true }

/**
 * 便捷获取当前概念版主题是否处于深色状态
 */
@Composable
fun isConceptDarkTheme(): Boolean = LocalIsDarkTheme.current

// 全局圆润饱满几何形状系统
val ConceptShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = ConceptPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF16325B),
    onPrimaryContainer = ConceptPrimaryContainer,
    secondary = ConceptCyanAccent,
    secondaryContainer = Color(0xFF172D3E),
    onSecondaryContainer = ConceptSecondaryContainer,
    background = ConceptBackgroundDark,
    surface = ConceptSurfaceDark,
    surfaceVariant = ConceptSurfaceVariantDark,
    surfaceContainerLowest = Color(0xFF080C14),
    surfaceContainerLow = Color(0xFF111826),
    surfaceContainer = ConceptCardDark,
    surfaceContainerHigh = ConceptElevatedCardDark,
    surfaceContainerHighest = Color(0xFF24314A),
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF2D3B55),
    outlineVariant = Color(0xFF1F2B40)
)

private val LightColorScheme = lightColorScheme(
    primary = ConceptPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = ConceptPrimaryContainer,
    onPrimaryContainer = ConceptOnPrimaryContainer,
    secondary = ConceptSecondary,
    secondaryContainer = ConceptSecondaryContainer,
    onSecondaryContainer = Color(0xFF0A2E4D),
    background = ConceptBackgroundLight,
    surface = ConceptSurfaceLight,
    surfaceVariant = ConceptSurfaceVariantLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FBFF),
    surfaceContainer = Color(0xF7FFFFFF),
    surfaceContainerHigh = ConceptElevatedCardLight,
    surfaceContainerHighest = Color(0xFFDCE8F8),
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFCBD8EA),
    outlineVariant = Color(0xFFDCE6F4)
)

/**
 * 沉浸式无边框悬浮卡片背景色：
 * - 深色模式：深海微蓝悬浮块 (#141C2B)
 * - 浅色模式：高透纯白圆润悬浮块 (#F7FFFFFF)
 */
@Composable
fun conceptFloatingCardColor(): Color {
    return if (LocalIsDarkTheme.current) ConceptCardDark else Color(0xF7FFFFFF)
}

/**
 * 次级胶囊/内嵌容器背景色：
 * - 深色模式：#1C263B
 * - 浅色模式：#E8F1FC
 */
@Composable
fun conceptElevatedSurfaceColor(): Color {
    return if (LocalIsDarkTheme.current) ConceptElevatedCardDark else ConceptElevatedCardLight
}

/**
 * 底部抽屉与弹窗容器背景色
 */
@Composable
fun conceptSheetContainerColor(): Color {
    return if (LocalIsDarkTheme.current) Color(0xFF111826) else Color(0xFFF8FBFF)
}

/**
 * 柔和唯美无框点击反馈修饰符
 */
fun Modifier.softClickable(
    enabled: Boolean = true,
    pressScale: Float = 0.94f,
    pressAlpha: Float = 0.72f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "softClickScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressAlpha else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "softClickAlpha"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * 柔和圆润卡片/列表项点击反馈修饰符：
 * - 当用于需要展示外侧 3D 边缘光晕阴影的日推卡片时，传入 clip = false 以保留外围柔和立体光晕；
 * - 普通无阴影水彩卡片默认 clip = true。
 */
fun Modifier.softCardClickable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(24.dp),
    clip: Boolean = true,
    pressScale: Float = 0.98f,
    pressAlpha: Float = 0.88f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "softCardScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressAlpha else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "softCardAlpha"
    )
    val base = this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
    val clipped = if (clip) base.clip(shape) else base
    clipped.clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * 触屏左右滑动切换分类标题修饰符：
 * - 左滑切换至下一分类，右滑切换至上一分类；
 * - 若当前处于首个分类且传入了 onSwipeRightAtStart / onSwipeBackAtFirst，则右滑触发返回/重置。
 */

/**
 * 灵动多维分类内容切换过渡动画（3D 弹性缩放 + 水平弹簧位移 + 微幅垂直悬浮上扬 + 渐隐渐显）：
 * 告别呆板的纯左右线性平移，让分类标题切换时下方内容具有呼吸感与空间层次感。
 */
fun conceptCategoryContentTransition(direction: Int): ContentTransform {
    val dir = if (direction >= 0) 1 else -1
    val enterOrigin = TransformOrigin(if (dir >= 0) 0.68f else 0.32f, 0.35f)
    val exitOrigin = TransformOrigin(if (dir >= 0) 0.32f else 0.68f, 0.35f)
    return (
        slideInHorizontally(
            animationSpec = spring(
                dampingRatio = 0.80f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) { fullWidth -> dir * (fullWidth / 6) } +
            slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { fullHeight -> (fullHeight / 32).coerceAtLeast(12) } +
            scaleIn(
                initialScale = 0.94f,
                transformOrigin = enterOrigin,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) +
            fadeIn(animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing))
        ) togetherWith (
        slideOutHorizontally(
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        ) { fullWidth -> -dir * (fullWidth / 10) } +
            scaleOut(
                targetScale = 0.95f,
                transformOrigin = exitOrigin,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            ) +
            fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing))
        )
}

/**
 * 抽屉 / BottomSheet 内容灵动弹出入场修饰符：
 * 在底部抽屉弹出时赋予内容柔和的弹簧上浮与微缩放绽放感。
 */

/**
 * 针对 LazyColumn 内部分类内容流的 120fps GPU 灵动切换修饰符：
 * 在保持 LazyColumn 原生滚动与虚拟化的同时，在分类 Tab 切换瞬间赋予下方列表项
 * 方向感弹簧平移 + 3D 缩放 + 微幅上浮 + 渐显过渡。
 */
@Composable
fun rememberCategorySwitchGraphicModifier(selectedIndex: Int): Modifier {
    var prevIndex by remember { mutableIntStateOf(selectedIndex) }
    var direction by remember { mutableIntStateOf(1) }
    val anim = remember { Animatable(1f) }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex != prevIndex) {
            direction = if (selectedIndex >= prevIndex) 1 else -1
            prevIndex = selectedIndex
            anim.snapTo(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val p = anim.value
    val dir = direction
    return Modifier.graphicsLayer {
        if (p < 0.999f) {
            val inv = 1f - p
            translationX = dir * inv * 64f
            translationY = inv * 22f
            val s = 0.95f + 0.05f * p
            scaleX = s
            scaleY = s
            alpha = (0.25f + 0.75f * p).coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(if (dir >= 0) 0.65f else 0.35f, 0.25f)
        }
    }
}

fun Modifier.conceptSheetSpringEntrance(): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.76f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    val p = progress.value
    this.graphicsLayer {
        val s = 0.95f + 0.05f * p
        scaleX = s
        scaleY = s
        translationY = (1f - p) * 38f
        alpha = p.coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(0.5f, 0.92f)
    }
}

fun Modifier.horizontalCategorySwipe(
    currentIndex: Int,
    itemCount: Int = 0,
    totalCount: Int = itemCount,
    swipeThresholdPx: Float = 125f,
    onSelectIndex: (Int) -> Unit,
    onSwipeRightAtStart: (() -> Unit)? = null,
    onSwipeBackAtFirst: (() -> Unit)? = onSwipeRightAtStart
): Modifier = composed {
    val curIdx by rememberUpdatedState(currentIndex)
    val count by rememberUpdatedState(if (itemCount > 0) itemCount else totalCount)
    val onSelect by rememberUpdatedState(onSelectIndex)
    val onBack by rememberUpdatedState(onSwipeRightAtStart ?: onSwipeBackAtFirst)
    val scope = rememberCoroutineScope()
    val dragFeedbackX = remember { Animatable(0f) }

    this
        .graphicsLayer {
            translationX = dragFeedbackX.value
        }
        .pointerInput(Unit) {
            var totalDx = 0f
            var triggered = false
            val settleBack: () -> Unit = {
                scope.launch {
                    dragFeedbackX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
            detectHorizontalDragGestures(
                onDragStart = {
                    totalDx = 0f
                    triggered = false
                },
                onDragEnd = {
                    totalDx = 0f
                    triggered = false
                    settleBack()
                },
                onDragCancel = {
                    totalDx = 0f
                    triggered = false
                    settleBack()
                },
                onHorizontalDrag = { change, dragAmount ->
                    if (triggered || count <= 1) return@detectHorizontalDragGestures
                    totalDx += dragAmount
                    val canGoNext = curIdx < count - 1
                    val canGoPrev = curIdx > 0 || onBack != null
                    val resistance = when {
                        totalDx < 0f && !canGoNext -> 0.08f
                        totalDx > 0f && !canGoPrev -> 0.08f
                        else -> 0.24f
                    }
                    scope.launch {
                        dragFeedbackX.snapTo(
                            (dragFeedbackX.value + dragAmount * resistance).coerceIn(-34f, 34f)
                        )
                    }
                    if (totalDx <= -swipeThresholdPx) {
                        if (canGoNext) {
                            triggered = true
                            change.consume()
                            settleBack()
                            onSelect(curIdx + 1)
                        }
                    } else if (totalDx >= swipeThresholdPx) {
                        if (curIdx > 0) {
                            triggered = true
                            change.consume()
                            settleBack()
                            onSelect(curIdx - 1)
                        } else if (onBack != null) {
                            triggered = true
                            change.consume()
                            settleBack()
                            onBack?.invoke()
                        }
                    }
                }
            )
        }
}

/**
 * 开放式分类标题导航项数据模型
 */
@Immutable
data class ConceptCategoryTabItem(
    val key: String,
    val label: String,
    val countText: String = "",
    val accentColor: Color = ConceptPrimary
)

/**
 * 丝滑流体分类标题栏（支持左右滑动动画 + 多彩横条渐变过渡 + 120fps 零重排 GPU 缩放）：
 * - 单色模式（如「新歌速递」「歌单内排序」所有分类同色）：底部彩色横条在左右滑动时保持纯净单色并带水滴弹性拉伸；
 * - 多色模式（如「巅峰排行榜」「我的页」「搜索页」「歌手页」「电台页」各分类不同颜色）：
 *   底部横条在左右移动时自动呈现平滑双色水平渐变过渡（起始分类色 -> 目标分类色流光渐变）。
 */
@Composable
fun ConceptSlidingCategoryTabs(
    tabs: List<ConceptCategoryTabItem>,
    selectedKey: String,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 22.dp,
    fontSize: TextUnit = 14.sp,
    indicatorWidth: Dp = 20.dp,
    indicatorHeight: Dp = 3.dp,
    fillWidth: Boolean = true,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    if (tabs.isEmpty()) return
    val density = LocalDensity.current
    val selectedIndex = remember(tabs, selectedKey) {
        tabs.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    }

    // 记录每个标题在父 Row 中的水平中心坐标 (px) 与行总宽度
    val tabCentersPx = remember(tabs.size) { FloatArray(tabs.size) }
    var rowWidthPx by remember(tabs.size) { mutableIntStateOf(0) }
    var layoutVersion by remember(tabs.size) { mutableIntStateOf(0) }

    // 判断是否为多色分类栏（如新歌速递为单色，排行榜/我的页/搜索页为多色）
    val isMultiColor = remember(tabs) {
        tabs.map { it.accentColor.value }.distinct().size > 1
    }

    // 丝滑弹簧驱动的浮点索引，实现横条左右连续滑动与水滴拉伸
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "categoryTabSlideIndex"
    )

    val targetAccent = tabs[selectedIndex].accentColor
    val neighborIndex = if (selectedIndex < tabs.lastIndex) selectedIndex + 1 else (selectedIndex - 1).coerceAtLeast(0)
    val neighborAccent = tabs[neighborIndex].accentColor

    // 领先端与拖尾端采用不同刚度的弹簧色彩动画，移动过程中自然拉出柔和渐变流光
    val leadColor by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tabLeadColor"
    )
    val trailColor by animateColorAsState(
        targetValue = if (isMultiColor) lerp(targetAccent, neighborAccent, 0.36f) else targetAccent,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "tabTrailColor"
    )

    Row(
        modifier = if (fillWidth || trailingContent != null) modifier.fillMaxWidth() else modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = if (trailingContent != null) Modifier.weight(1f, fill = false) else Modifier) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.onGloballyPositioned { coords ->
                    if (coords.size.width != rowWidthPx) {
                        rowWidthPx = coords.size.width
                        layoutVersion++
                    }
                }
            ) {
                tabs.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        },
                        animationSpec = tween(durationMillis = 220),
                        label = "tabTextColor_$index"
                    )
                    val countColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            item.accentColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                        },
                        animationSpec = tween(durationMillis = 220),
                        label = "tabCountColor_$index"
                    )
                    val textScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.06f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "tabTextScale_$index"
                    )

                    Box(
                        contentAlignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                val center = coords.positionInParent().x + coords.size.width / 2f
                                if (abs(tabCentersPx[index] - center) > 0.5f) {
                                    tabCentersPx[index] = center
                                    layoutVersion++
                                }
                            }
                            .softClickable(
                                pressScale = 0.92f,
                                pressAlpha = 0.76f,
                                onClick = { onSelectTab(item.key) }
                            )
                            .padding(top = 4.dp, bottom = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.graphicsLayer {
                                scaleX = textScale
                                scaleY = textScale
                                transformOrigin = TransformOrigin(0.5f, 0.85f)
                            }
                        ) {
                            Text(
                                text = item.label,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = fontSize,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (item.countText.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = item.countText,
                                    color = countColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(bottom = 1.5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 底部左右丝滑移动指示横条（Canvas 硬件加速绘制，0 布局抖动）
            val baseIndicatorWidthPx = with(density) { indicatorWidth.toPx() }
            val extraStretchPx = with(density) { 14.dp.toPx() }
            val canvasWidthDp = with(density) { rowWidthPx.coerceAtLeast(1).toDp() }
            Canvas(
                modifier = Modifier
                    .width(canvasWidthDp)
                    .height(indicatorHeight)
            ) {
                val _ver = layoutVersion // 订阅坐标测量更新
                if (tabs.isEmpty()) return@Canvas
                val clamped = animatedIndex.coerceIn(0f, tabs.lastIndex.toFloat())
                val lowerIdx = clamped.toInt().coerceIn(0, tabs.lastIndex)
                val upperIdx = (lowerIdx + 1).coerceIn(0, tabs.lastIndex)
                val fraction = (clamped - lowerIdx).coerceIn(0f, 1f)

                val c1 = tabCentersPx[lowerIdx]
                val c2 = tabCentersPx[upperIdx]
                val currentCenterPx = if (c1 > 0f && c2 > 0f) {
                    c1 + (c2 - c1) * fraction
                } else {
                    c1.coerceAtLeast(baseIndicatorWidthPx / 2f)
                }

                // 滑动中途产生柔和流体拉伸
                val stretch = sin(fraction * PI.toFloat()) * extraStretchPx
                val barW = baseIndicatorWidthPx + stretch
                val barH = size.height
                val leftX = (currentCenterPx - barW / 2f).coerceAtLeast(0f)

                if (isMultiColor) {
                    val cLower = tabs[lowerIdx].accentColor
                    val cUpper = tabs[upperIdx].accentColor
                    val dynamicLeftColor = if (fraction > 0.01f) {
                        lerp(cLower, cUpper, (fraction * 0.65f).coerceIn(0f, 1f))
                    } else {
                        leadColor
                    }
                    val dynamicRightColor = if (fraction > 0.01f) {
                        lerp(cLower, cUpper, (0.35f + fraction * 0.65f).coerceIn(0f, 1f))
                    } else {
                        trailColor
                    }
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(dynamicLeftColor, dynamicRightColor),
                            startX = leftX,
                            endX = leftX + barW
                        ),
                        topLeft = Offset(leftX, 0f),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barH / 2f, barH / 2f)
                    )
                } else {
                    drawRoundRect(
                        color = leadColor,
                        topLeft = Offset(leftX, 0f),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barH / 2f, barH / 2f)
                    )
                }
            }
        }

        if (trailingContent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = trailingContent
            )
        }
    }
}

/**
 * 重新设计的圆润对称双弧流光刷新图标（替代生硬的系统默认单箭头 Refresh 图标，支持丝滑旋转动画）
 */
@Composable
fun ConceptFluidRefreshIcon(
    tint: Color,
    rotationDegrees: Float = 0f,
    isRefreshing: Boolean = false,
    spinTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    var spinTurns by remember { mutableIntStateOf(0) }
    LaunchedEffect(spinTrigger) {
        if (spinTrigger > 0) {
            spinTurns += 1
        }
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (true) {
                spinTurns += 1
                kotlinx.coroutines.delay(650L)
            }
        }
    }
    val animatedSpin by animateFloatAsState(
        targetValue = spinTurns * 360f + rotationDegrees,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "conceptFluidRefreshSpin"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = (w * 0.115f).coerceAtLeast(2.2f)
        val pad = strokeW * 1.35f
        val arcSize = Size(w - pad * 2f, h - pad * 2f)
        val topLeft = Offset(pad, pad)
        val radius = arcSize.width / 2f
        val center = Offset(w / 2f, h / 2f)

        rotate(degrees = animatedSpin, pivot = center) {
            // 上半圆润流线弧 (22° -> 158°)
            drawArc(
                color = tint,
                startAngle = 22f,
                sweepAngle = 136f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            // 下半圆润流线弧 (202° -> 338°)
            drawArc(
                color = tint,
                startAngle = 202f,
                sweepAngle = 136f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // 绘制两个对称的圆润箭头翼
            val arrowLen = strokeW * 1.55f
            listOf(158f, 338f).forEach { endAngleDeg ->
                val rad = endAngleDeg * (PI.toFloat() / 180f)
                val tipX = center.x + radius * cos(rad)
                val tipY = center.y + radius * sin(rad)
                val wing1Rad = (endAngleDeg - 142f) * (PI.toFloat() / 180f)
                val wing2Rad = (endAngleDeg - 42f) * (PI.toFloat() / 180f)
                val path = Path().apply {
                    moveTo(tipX + arrowLen * cos(wing1Rad), tipY + arrowLen * sin(wing1Rad))
                    lineTo(tipX, tipY)
                    lineTo(tipX + arrowLen * cos(wing2Rad), tipY + arrowLen * sin(wing2Rad))
                }
                drawPath(
                    path = path,
                    color = tint,
                    style = Stroke(width = strokeW * 0.95f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 当全屏深色沉浸式层（如全屏播放页）覆盖在浅色主题之上时，动态同步系统状态栏与导航栏图标为浅色亮字，
 * 关闭后自动恢复当前主题对应的状态栏图标明暗度。
 */
@Composable
fun SystemBarAppearanceEffect(forceDarkBars: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDarkTheme = LocalIsDarkTheme.current
    if (!view.isInEditMode) {
        DisposableEffect(forceDarkBars, isDarkTheme) {
            val window = context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val effectiveDark = forceDarkBars || isDarkTheme
            controller?.isAppearanceLightStatusBars = !effectiveDark
            controller?.isAppearanceLightNavigationBars = !effectiveDark
            onDispose {
                controller?.isAppearanceLightStatusBars = !isDarkTheme
                controller?.isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }
}

@Composable
fun ConceptMusicTheme(
    darkThemeOverride: Boolean? = null,
    dynamicColorOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    ThemeManager.init(context)
    val mode by ThemeManager.themeMode.collectAsState()
    val dynColor by ThemeManager.dynamicColor.collectAsState()

    val sysDark = isSystemInDarkTheme()
    val isDark = darkThemeOverride ?: when (mode) {
        AppThemeMode.DARK_ABYSS_BLUE -> true
        AppThemeMode.LIGHT_WATERCOLOR -> false
        AppThemeMode.SYSTEM -> sysDark
    }
    val useDynamic = dynamicColorOverride ?: dynColor

    val colorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides isDark,
        androidx.compose.material3.LocalContentColor provides colorScheme.onSurface
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ConceptShapes
        ) {
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides colorScheme.onSurface,
                content = content
            )
        }
    }
}

/**
 * 全局氛围画布容器（采用 drawWithCache 硬件着色器缓存）
 */
@Composable
fun ConceptStageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val baseBg = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                if (isDark) {
                    val topGlow = Brush.radialGradient(
                        colors = listOf(
                            AbyssalBlueGlowPrimary.copy(alpha = 0.50f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.22f, h * 0.10f),
                        radius = w * 0.90f
                    )
                    val bottomGlow = Brush.radialGradient(
                        colors = listOf(
                            AbyssalBlueGlowSecondary.copy(alpha = 0.42f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.82f, h * 0.78f),
                        radius = w * 1.05f
                    )
                    onDrawBehind {
                        drawRect(color = baseBg)
                        drawRect(brush = topGlow)
                        drawRect(brush = bottomGlow)
                    }
                } else {
                    val blueWash = Brush.radialGradient(
                        colors = listOf(
                            WatercolorBlueWash.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.18f, h * 0.10f),
                        radius = w * 0.88f
                    )
                    val lavenderWash = Brush.radialGradient(
                        colors = listOf(
                            WatercolorLavenderAccent.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.88f, h * 0.36f),
                        radius = w * 0.76f
                    )
                    val cyanPeachWash = Brush.radialGradient(
                        colors = listOf(
                            WatercolorCyanWash.copy(alpha = 0.12f),
                            WatercolorPeachAccent.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.32f, h * 0.76f),
                        radius = w * 0.96f
                    )
                    onDrawBehind {
                        drawRect(color = baseBg)
                        drawRect(brush = blueWash)
                        drawRect(brush = lavenderWash)
                        drawRect(brush = cyanPeachWash)
                    }
                }
            },
        content = content
    )
}
