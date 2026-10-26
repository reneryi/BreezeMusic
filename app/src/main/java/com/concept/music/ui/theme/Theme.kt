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
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
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
 * 基于轻量级 `Modifier.Node` 的零重组按压微回弹修饰节点：
 * - 按下瞬间采用 `ConceptMotion.softPressSpring()` 敏捷收敛；
 * - 松手恢复采用 `ConceptMotion.softReleaseSpring()` 赋予灵动微回弹生命力；
 * - 缩放与透明度变换通过 `placeWithLayer` 100% 在 GPU 图层阶段完成，彻底避免长列表挂载 `animateFloatAsState` 的开销。
 */
private data class SoftBounceTouchElement(
    val interactionSource: InteractionSource,
    val enabled: Boolean,
    val pressScale: Float,
    val pressAlpha: Float
) : ModifierNodeElement<SoftBounceTouchNode>() {
    override fun create(): SoftBounceTouchNode = SoftBounceTouchNode(
        interactionSource = interactionSource,
        enabled = enabled,
        pressScale = pressScale,
        pressAlpha = pressAlpha
    )

    override fun update(node: SoftBounceTouchNode) {
        node.updateParams(
            newSource = interactionSource,
            newEnabled = enabled,
            newPressScale = pressScale,
            newPressAlpha = pressAlpha
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "softBounceTouch"
        properties["enabled"] = enabled
        properties["pressScale"] = pressScale
        properties["pressAlpha"] = pressAlpha
    }
}

private class SoftBounceTouchNode(
    var interactionSource: InteractionSource,
    var enabled: Boolean,
    var pressScale: Float,
    var pressAlpha: Float
) : Modifier.Node(), LayoutModifierNode {
    private val scaleAnim = Animatable(1f)
    private val alphaAnim = Animatable(1f)
    private var interactionJob: Job? = null

    override val shouldAutoInvalidate: Boolean get() = false

    override fun onAttach() {
        super.onAttach()
        observeInteractions()
    }

    override fun onDetach() {
        interactionJob?.cancel()
        interactionJob = null
        super.onDetach()
    }

    fun updateParams(
        newSource: InteractionSource,
        newEnabled: Boolean,
        newPressScale: Float,
        newPressAlpha: Float
    ) {
        val sourceChanged = interactionSource !== newSource
        val enabledChanged = enabled != newEnabled
        interactionSource = newSource
        enabled = newEnabled
        pressScale = newPressScale
        pressAlpha = newPressAlpha
        if (!newEnabled && isAttached) {
            coroutineScope.launch {
                scaleAnim.snapTo(1f)
                alphaAnim.snapTo(1f)
            }
        }
        if ((sourceChanged || enabledChanged) && isAttached) {
            observeInteractions()
        }
    }

    private fun observeInteractions() {
        interactionJob?.cancel()
        if (!isAttached) return
        val source = interactionSource
        interactionJob = coroutineScope.launch {
            var pressCount = 0
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressCount++
                        if (pressCount == 1 && enabled) {
                            launch {
                                scaleAnim.animateTo(
                                    targetValue = pressScale,
                                    animationSpec = ConceptMotion.softPressSpring()
                                )
                            }
                            launch {
                                alphaAnim.animateTo(
                                    targetValue = pressAlpha,
                                    animationSpec = ConceptMotion.softPressSpring()
                                )
                            }
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        pressCount = (pressCount - 1).coerceAtLeast(0)
                        if (pressCount == 0) {
                            launch {
                                scaleAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = ConceptMotion.softReleaseSpring()
                                )
                            }
                            launch {
                                alphaAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = ConceptMotion.gentleBounceSpring()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val s = scaleAnim.value
                scaleX = s
                scaleY = s
                alpha = alphaAnim.value
            }
        }
    }
}

/**
 * 柔和唯美无框点击反馈修饰符（按压敏捷收敛 + 松手微回弹 + 100% Draw 阶段图层渲染）
 */
fun Modifier.softClickable(
    enabled: Boolean = true,
    pressScale: Float = 0.93f,
    pressAlpha: Float = 0.76f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .then(
            SoftBounceTouchElement(
                interactionSource = interactionSource,
                enabled = enabled,
                pressScale = pressScale,
                pressAlpha = pressAlpha
            )
        )
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
    pressScale: Float = 0.975f,
    pressAlpha: Float = 0.90f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val base = this.then(
        SoftBounceTouchElement(
            interactionSource = interactionSource,
            enabled = enabled,
            pressScale = pressScale,
            pressAlpha = pressAlpha
        )
    )
    val clipped = if (clip) base.clip(shape) else base
    clipped.clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * 灵动多维分类内容切换过渡动画（入场与退场统一采用物理弹簧配速 + 顺向惯性微滑出 + 景深微缩放）：
 * 消除原本入场弹簧、退场 150ms 短促线性硬切造成的物理节奏割裂。
 */
fun conceptCategoryContentTransition(direction: Int): ContentTransform {
    val dir = if (direction >= 0) 1 else -1
    val enterOrigin = TransformOrigin(if (dir >= 0) 0.68f else 0.32f, 0.35f)
    val exitOrigin = TransformOrigin(if (dir >= 0) 0.32f else 0.68f, 0.35f)
    return (
        slideInHorizontally(
            animationSpec = ConceptMotion.spatialPageSpring()
        ) { fullWidth -> dir * (fullWidth / 6) } +
            slideInVertically(
                animationSpec = ConceptMotion.spatialPageSpring()
            ) { fullHeight -> (fullHeight / 34).coerceAtLeast(10) } +
            scaleIn(
                initialScale = 0.945f,
                transformOrigin = enterOrigin,
                animationSpec = ConceptMotion.spatialPageSpring()
            ) +
            fadeIn(animationSpec = tween(durationMillis = 240, easing = ConceptMotion.EmphasizedDecelerate))
        ) togetherWith (
        slideOutHorizontally(
            animationSpec = ConceptMotion.spatialPageSpring()
        ) { fullWidth -> -dir * (fullWidth / 9) } +
            slideOutVertically(
                animationSpec = ConceptMotion.spatialPageSpring()
            ) { fullHeight -> -(fullHeight / 48).coerceAtLeast(6) } +
            scaleOut(
                targetScale = 0.955f,
                transformOrigin = exitOrigin,
                animationSpec = ConceptMotion.spatialPageSpring()
            ) +
            fadeOut(animationSpec = tween(durationMillis = 200, easing = ConceptMotion.EmphasizedAccelerate))
        )
}

/**
 * 针对 LazyColumn 内部分类内容流的 120fps GPU 灵动切换修饰符：
 * 【阶段延迟修复】：将 `anim.value` 的读取严格封装在 `Modifier.graphicsLayer { ... }` 内部，
 * 切换分类时仅更新 RenderNode 属性，实现宿主页（我的页/歌手页）0 次全树重组！
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
                animationSpec = ConceptMotion.spatialPageSpring()
            )
        }
    }

    return Modifier.graphicsLayer {
        val p = anim.value
        val dir = direction
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

/**
 * 抽屉 / BottomSheet 内容灵动弹出入场修饰符：
 * 【阶段延迟修复】：将 `progress.value` 严格移入 `graphicsLayer` 闭包内部读取，彻底消除入场期间的逐帧重组。
 */
fun Modifier.conceptSheetSpringEntrance(): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = ConceptMotion.spatialSheetSpring()
        )
    }
    this.graphicsLayer {
        val p = progress.value
        val s = 0.95f + 0.05f * p
        scaleX = s
        scaleY = s
        translationY = (1f - p) * 42f * density
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
                        animationSpec = ConceptMotion.gentleBounceSpring()
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
                        else -> 0.26f
                    }
                    scope.launch {
                        dragFeedbackX.snapTo(
                            (dragFeedbackX.value + dragAmount * resistance).coerceIn(-40f, 40f)
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
 * 丝滑流体分类标题栏（支持左右滑动动画 + 多彩横条渐变过渡 + 120fps 零重排 GPU 缩放）
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
    val animatedIndexState = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = ConceptMotion.expressiveSpring(),
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
                        animationSpec = tween(durationMillis = 220, easing = ConceptMotion.EmphasizedDecelerate),
                        label = "tabTextColor_$index"
                    )
                    val countColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            item.accentColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                        },
                        animationSpec = tween(durationMillis = 220, easing = ConceptMotion.EmphasizedDecelerate),
                        label = "tabCountColor_$index"
                    )
                    val textScaleState = animateFloatAsState(
                        targetValue = if (isSelected) 1.06f else 1.0f,
                        animationSpec = ConceptMotion.expressiveSpring(),
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
                                val s = textScaleState.value
                                scaleX = s
                                scaleY = s
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
                val animatedIndex = animatedIndexState.value
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
    val animatedSpinState = animateFloatAsState(
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

        rotate(degrees = animatedSpinState.value, pivot = center) {
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
private var forceDarkBarsOverlayCount = 0

@Composable
fun SystemBarAppearanceEffect(forceDarkBars: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDarkTheme = LocalIsDarkTheme.current
    if (!view.isInEditMode) {
        DisposableEffect(forceDarkBars, isDarkTheme) {
            if (forceDarkBars) forceDarkBarsOverlayCount++
            val window = context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val effectiveDark = forceDarkBarsOverlayCount > 0 || forceDarkBars || isDarkTheme
            controller?.isAppearanceLightStatusBars = !effectiveDark
            controller?.isAppearanceLightNavigationBars = !effectiveDark
            onDispose {
                if (forceDarkBars) forceDarkBarsOverlayCount = (forceDarkBarsOverlayCount - 1).coerceAtLeast(0)
                val stillDark = forceDarkBarsOverlayCount > 0 || isDarkTheme
                controller?.isAppearanceLightStatusBars = !stillDark
                controller?.isAppearanceLightNavigationBars = !stillDark
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
            val effectiveDark = isDark || forceDarkBarsOverlayCount > 0
            insetsController.isAppearanceLightStatusBars = !effectiveDark
            insetsController.isAppearanceLightNavigationBars = !effectiveDark
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

/**
 * 计算颜色的 WCAG 2.1 相对感知亮度 (Relative Luminance, 0.0 ~ 1.0)。
 */
fun Color.perceptualLuminance(): Float {
    fun channelLinear(c: Float): Float {
        val v = c.coerceIn(0f, 1f)
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * channelLinear(red) +
        0.7152f * channelLinear(green) +
        0.0722f * channelLinear(blue)
}

internal fun composeColorToHsv(color: Color): FloatArray {
    val r = color.red.coerceIn(0f, 1f)
    val g = color.green.coerceIn(0f, 1f)
    val b = color.blue.coerceIn(0f, 1f)
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val v = max
    val s = if (max <= 1e-5f) 0f else delta / max
    val hRaw = when {
        delta <= 1e-5f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val h = if (hRaw < 0f) hRaw + 360f else hRaw
    return floatArrayOf(h, s, v)
}

internal fun hsvToComposeColor(h: Float, s: Float, v: Float, alpha: Float = 1f): Color {
    val cleanH = ((h % 360f) + 360f) % 360f
    val cleanS = s.coerceIn(0f, 1f)
    val cleanV = v.coerceIn(0f, 1f)
    val c = cleanV * cleanS
    val x = c * (1f - abs(((cleanH / 60f) % 2f) - 1f))
    val m = cleanV - c
    val sector = (cleanH / 60f).toInt().coerceIn(0, 5)
    val (r1, g1, b1) = when (sector) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f)
    )
}

/**
 * 动态取色深色背景前景内容可读性增强算法：
 * - 在完全锁定原取色色相 (Hue) 不变的前提下，基于 WCAG 感知相对亮度连续自适应提亮前景色（文字、图标、徽标、动态高亮歌词）；
 * - 针对深蓝、靛紫、暗红、深紫等高饱和低感知亮度的取色，通过二分收敛在同色相下寻找满足最小感知亮度阈值 [minLuminance] 的最高饱和度点，
 *   既保留鲜明的封面取色色相特征，又消除深色卡片/背景上的阅读吃力问题。
 */
fun Color.readableOnDarkSurface(minLuminance: Float = 0.42f): Color {
    if (this.perceptualLuminance() >= minLuminance) {
        return this
    }
    val hsv = composeColorToHsv(this)
    val hue = hsv[0]
    val sat0 = hsv[1]
    val val0 = hsv[2]

    if (sat0 <= 0.04f) {
        val liftedVal = val0.coerceAtLeast(0.90f)
        return hsvToComposeColor(hue, sat0, liftedVal, alpha)
    }

    val targetVal = val0.coerceAtLeast(0.96f)
    val baseSat = sat0.coerceAtMost(0.82f)
    val initialCandidate = hsvToComposeColor(hue, baseSat, targetVal, alpha)
    if (initialCandidate.perceptualLuminance() >= minLuminance) {
        return initialCandidate
    }

    // 在完全保持色相 hue 不变、明度提升至 0.99f 的条件下，二分搜索满足 minLuminance 的最大饱和度
    val highV = 0.99f
    var lowS = 0.22f
    var highS = baseSat
    repeat(10) {
        val midS = (lowS + highS) * 0.5f
        val testColor = hsvToComposeColor(hue, midS, highV, alpha)
        if (testColor.perceptualLuminance() >= minLuminance) {
            lowS = midS
        } else {
            highS = midS
        }
    }
    return hsvToComposeColor(hue, lowS, highV, alpha)
}

/**
 * 动态取色深色卡片内次级副标题/说明文案可读性增强：
 * - 在保持同色相感知提亮的基础上融入柔和冰白高光，确保 10sp~11sp 小字号副标题在动态染色卡片上清晰通透。
 */
fun Color.readableSubtleOnDarkSurface(minLuminance: Float = 0.54f): Color {
    val readableBase = this.readableOnDarkSurface(minLuminance = minLuminance)
    return lerp(readableBase, Color(0xFFF2F6FF), 0.26f).copy(alpha = 0.94f)
}
