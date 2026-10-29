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
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
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

    fun applyNightMode(mode: AppThemeMode) {
        val nightMode = when (mode) {
            AppThemeMode.DARK_ABYSS_BLUE -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            AppThemeMode.LIGHT_WATERCOLOR -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            AppThemeMode.SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun isDarkTheme(context: Context): Boolean {
        init(context)
        return when (_themeMode.value) {
            AppThemeMode.DARK_ABYSS_BLUE -> true
            AppThemeMode.LIGHT_WATERCOLOR -> false
            AppThemeMode.SYSTEM -> {
                val nightFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("concept_theme_prefs", Context.MODE_PRIVATE)
        prefs = p
        val savedMode = p.getString("theme_mode", AppThemeMode.DARK_ABYSS_BLUE.name) ?: AppThemeMode.DARK_ABYSS_BLUE.name
        val resolvedMode = runCatching { AppThemeMode.valueOf(savedMode) }.getOrDefault(AppThemeMode.DARK_ABYSS_BLUE)
        _themeMode.value = resolvedMode
        _dynamicColor.value = p.getBoolean("dynamic_color", false)
        applyNightMode(resolvedMode)
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString("theme_mode", mode.name)?.apply()
        applyNightMode(mode)
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
 * 零分配、零缓冲、零订阅的共享空 InteractionSource 单例：
 * 传入 `Modifier.clickable(interactionSource = NoOpInteractionSource, indication = null, ...)` 时，
 * 完全规避 `Modifier.composed` 开销与每行 `MutableInteractionSource()` 的 `SharedFlow` 分配。
 */
private object NoOpInteractionSource : MutableInteractionSource {
    override val interactions: Flow<Interaction> = emptyFlow()
    override suspend fun emit(interaction: Interaction) {}
    override fun tryEmit(interaction: Interaction): Boolean = true
}

/**
 * 基于 `ModifierNodeElement` + `PointerInputModifierNode` + `DrawModifierNode` 的零重组、惰性按压微回弹节点：
 * - 彻底移除 `Modifier.composed` 与逐行预分配的 `MutableInteractionSource`；
 * - 列表滑动期间：0 协程启动、0 Flow 收集、0 `Animatable` 预分配、0 离屏图层（RenderNode）分配；
 * - 仅当手指真实按在条目上时才惰性创建 `Animatable` 并通过 `DrawModifierNode` 执行弹簧缩放，滚屏或松手立即回弹。
 */
private data class SoftBouncePressElement(
    val enabled: Boolean = true,
    val pressScale: Float = 0.93f,
    val pressAlpha: Float = 0.76f
) : ModifierNodeElement<SoftBouncePressNode>() {
    override fun create(): SoftBouncePressNode {
        return SoftBouncePressNode(
            enabled = enabled,
            pressScale = pressScale,
            pressAlpha = pressAlpha
        )
    }

    override fun update(node: SoftBouncePressNode) {
        node.updateParams(
            enabled = enabled,
            pressScale = pressScale,
            pressAlpha = pressAlpha
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "softBouncePress"
        properties["enabled"] = enabled
        properties["pressScale"] = pressScale
        properties["pressAlpha"] = pressAlpha
    }
}

private class SoftBouncePressNode(
    private var enabled: Boolean,
    private var pressScale: Float,
    private var pressAlpha: Float
) : Modifier.Node(), PointerInputModifierNode, DrawModifierNode {
    private var isPressed: Boolean = false
    private var currentScale: Float = 1f
    private var scaleAnim: Animatable<Float, *>? = null
    private var animJob: Job? = null

    override val shouldAutoInvalidate: Boolean get() = false

    fun updateParams(
        enabled: Boolean,
        pressScale: Float,
        pressAlpha: Float
    ) {
        val disabledNow = this.enabled && !enabled
        this.enabled = enabled
        this.pressScale = pressScale
        this.pressAlpha = pressAlpha
        if (disabledNow && isPressed) {
            animateToPressed(false)
        }
    }

    override fun onDetach() {
        animJob?.cancel()
        animJob = null
        isPressed = false
        currentScale = 1f
        super.onDetach()
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {
        if (!enabled) return
        if (pass != PointerEventPass.Main) return
        when (pointerEvent.type) {
            PointerEventType.Press -> {
                val change = pointerEvent.changes.firstOrNull() ?: return
                if (!change.isConsumed &&
                    change.position.x in 0f..bounds.width.toFloat() &&
                    change.position.y in 0f..bounds.height.toFloat()
                ) {
                    animateToPressed(true)
                }
            }
            PointerEventType.Move -> {
                if (isPressed) {
                    val change = pointerEvent.changes.firstOrNull()
                    val outOfBounds = change == null ||
                        change.isConsumed ||
                        !change.pressed ||
                        change.position.x < -12f ||
                        change.position.x > bounds.width + 12f ||
                        change.position.y < -12f ||
                        change.position.y > bounds.height + 12f
                    if (outOfBounds) {
                        animateToPressed(false)
                    }
                }
            }
            PointerEventType.Release, PointerEventType.Exit -> {
                if (isPressed && pointerEvent.changes.none { it.pressed }) {
                    animateToPressed(false)
                }
            }
        }
    }

    override fun onCancelPointerInput() {
        if (isPressed) {
            animateToPressed(false)
        }
    }

    private fun animateToPressed(pressed: Boolean) {
        if (isPressed == pressed) return
        isPressed = pressed
        if (!isAttached) {
            currentScale = 1f
            return
        }
        animJob?.cancel()
        val anim = scaleAnim ?: Animatable(currentScale).also { scaleAnim = it }
        val target = if (pressed) pressScale else 1f
        val spec = if (pressed) ConceptMotion.softPressSpring<Float>() else ConceptMotion.softReleaseSpring<Float>()
        animJob = coroutineScope.launch {
            anim.animateTo(
                targetValue = target,
                animationSpec = spec
            ) {
                currentScale = value
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val s = currentScale
        if (s >= 0.999f) {
            drawContent()
        } else {
            scale(scaleX = s, scaleY = s, pivot = center) {
                this@draw.drawContent()
            }
        }
    }
}

/**
 * 柔和唯美无框点击反馈修饰符（零 `Modifier.composed` 开销 + 惰性按压激活 + 100% Draw 阶段渲染）
 */
fun Modifier.softClickable(
    enabled: Boolean = true,
    pressScale: Float = 0.93f,
    pressAlpha: Float = 0.76f,
    onClick: () -> Unit
): Modifier {
    return this
        .then(SoftBouncePressElement(enabled = enabled, pressScale = pressScale, pressAlpha = pressAlpha))
        .clickable(
            interactionSource = NoOpInteractionSource,
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
): Modifier {
    val clipped = if (clip) this.clip(shape) else this
    return clipped
        .then(SoftBouncePressElement(enabled = enabled, pressScale = pressScale, pressAlpha = pressAlpha))
        .clickable(
            interactionSource = NoOpInteractionSource,
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
            val dx = dragFeedbackX.value
            if (abs(dx) > 0.5f) {
                translationX = dx
            }
        }
        .pointerInput(Unit) {
            var totalDx = 0f
            var triggered = false
            val settleBack: () -> Unit = {
                if (abs(dragFeedbackX.value) > 0.01f) {
                    scope.launch {
                        dragFeedbackX.animateTo(
                            targetValue = 0f,
                            animationSpec = ConceptMotion.gentleBounceSpring()
                        )
                    }
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
                    if (abs(totalDx) < 14f) return@detectHorizontalDragGestures
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

/**
 * 计算两个颜色之间的 WCAG 2.1 对比度 (1.0 ~ 21.0)。
 */
fun Color.wcagContrastRatio(other: Color): Float {
    val l1 = this.perceptualLuminance()
    val l2 = other.perceptualLuminance()
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * 将 CIE L* 明度色阶 (Tone 0 ~ 100) 映射为 WCAG 相对感知亮度 Y (0.0 ~ 1.0)。
 */
internal fun toneToRelativeLuminance(tone: Int): Float {
    val t = tone.coerceIn(0, 100).toFloat()
    if (t <= 0f) return 0f
    if (t >= 100f) return 1f
    return if (t > 8f) {
        ((t + 16f) / 116f).pow(3f).coerceIn(0f, 1f)
    } else {
        (t / 903.296f).coerceIn(0f, 1f)
    }
}

/**
 * 将 WCAG 相对感知亮度 Y (0.0 ~ 1.0) 反解为 CIE L* 明度色阶 (Tone 0.0 ~ 100.0)。
 */
internal fun relativeLuminanceToTone(y: Float): Float {
    val clamped = y.coerceIn(0f, 1f)
    return if (clamped > 0.008856f) {
        (116f * clamped.pow(1f / 3f) - 16f).coerceIn(0f, 100f)
    } else {
        (903.296f * clamped).coerceIn(0f, 100f)
    }
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
 * 封面种子色（Seed Color）的 13 级明度色阶调色板（Tonal Palette，Tone 0 ~ Tone 100）：
 * - 将封面取色结果视为“基酒（Seed Color）”，完全锁定其色相（Hue, ΔHue = 0°）；
 * - 按 CIE L* 感知明度扩展出从纯黑（Tone 0）到纯白（Tone 100）的 13 级色阶：
 *   [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, 100]；
 * - 低色阶（Tone 10~40）保留醇厚色感用于深色容器底或日间高对比前景；
 * - 高色阶（Tone 80~99）如同按比例兑入苏打水稀释，在保留色相韵味的同时获得高明度通透可读性。
 */
@Immutable
data class CoverTonalPalette(
    val seed: Color,
    val hue: Float,
    val seedSaturation: Float
) {
    val tone0: Color = Color.Black
    val tone10: Color by lazy(LazyThreadSafetyMode.NONE) { tone(10) }
    val tone20: Color by lazy(LazyThreadSafetyMode.NONE) { tone(20) }
    val tone30: Color by lazy(LazyThreadSafetyMode.NONE) { tone(30) }
    val tone40: Color by lazy(LazyThreadSafetyMode.NONE) { tone(40) }
    val tone50: Color by lazy(LazyThreadSafetyMode.NONE) { tone(50) }
    val tone60: Color by lazy(LazyThreadSafetyMode.NONE) { tone(60) }
    val tone70: Color by lazy(LazyThreadSafetyMode.NONE) { tone(70) }
    val tone80: Color by lazy(LazyThreadSafetyMode.NONE) { tone(80) }
    val tone90: Color by lazy(LazyThreadSafetyMode.NONE) { tone(90) }
    val tone95: Color by lazy(LazyThreadSafetyMode.NONE) { tone(95) }
    val tone99: Color by lazy(LazyThreadSafetyMode.NONE) { tone(99) }
    val tone100: Color = Color.White

    /**
     * 获取指定明度色阶 (0..100) 对应的颜色，严格保持色相 [hue] 不变并对齐目标感知相对亮度。
     */
    fun tone(targetTone: Int): Color {
        val clampedTone = targetTone.coerceIn(0, 100)
        if (clampedTone <= 0) return Color.Black
        if (clampedTone >= 100) return Color.White

        val targetY = toneToRelativeLuminance(clampedTone)
        // 无彩色/极低饱和度封面直接按目标相对亮度生成中性色阶
        if (seedSaturation <= 0.04f) {
            var lowV = 0f
            var highV = 1f
            repeat(12) {
                val midV = (lowV + highV) * 0.5f
                if (hsvToComposeColor(hue, seedSaturation, midV).perceptualLuminance() < targetY) {
                    lowV = midV
                } else {
                    highV = midV
                }
            }
            return hsvToComposeColor(hue, seedSaturation, (lowV + highV) * 0.5f)
        }

        // 基酒色度骨干：高明度色阶（Tone 80+）按比例兑入苏打水适度收敛最高饱和度上限，保证清透不刺眼
        val baseSat = seedSaturation.coerceIn(0.48f, 0.88f)
        val maxSatForTone = when {
            clampedTone >= 99 -> baseSat.coerceAtMost(0.08f)
            clampedTone >= 95 -> baseSat.coerceAtMost(0.22f)
            clampedTone >= 90 -> baseSat.coerceAtMost(0.42f)
            clampedTone >= 80 -> baseSat.coerceAtMost(0.66f)
            else -> baseSat
        }

        val fullValCandidate = hsvToComposeColor(hue, maxSatForTone, 1f)
        val fullValY = fullValCandidate.perceptualLuminance()

        return if (targetY <= fullValY) {
            // 浓缩基酒阶段：保持同色相与醇厚饱和度 maxSatForTone，二分求解明度 V 精确命中 targetY
            var lowV = 0f
            var highV = 1f
            repeat(12) {
                val midV = (lowV + highV) * 0.5f
                val y = hsvToComposeColor(hue, maxSatForTone, midV).perceptualLuminance()
                if (y < targetY) {
                    lowV = midV
                } else {
                    highV = midV
                }
            }
            hsvToComposeColor(hue, maxSatForTone, (lowV + highV) * 0.5f)
        } else {
            // 苏打水稀释阶段：固定明度 V = 1.0，在同色相下二分稀释饱和度 S 直至精确命中 targetY
            var lowS = 0f
            var highS = maxSatForTone
            repeat(12) {
                val midS = (lowS + highS) * 0.5f
                val y = hsvToComposeColor(hue, midS, 1f).perceptualLuminance()
                if (y < targetY) {
                    // 饱和度越低，混入白光越多，相对亮度越高
                    highS = midS
                } else {
                    lowS = midS
                }
            }
            hsvToComposeColor(hue, (lowS + highS) * 0.5f, 1f)
        }
    }

    companion object {
        val STANDARD_TONES = intArrayOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, 100)
    }
}

/**
 * 将任意封面取色种子色扩展为 13 级明度色阶调色板 [CoverTonalPalette]。
 */
fun Color.toTonalPalette(): CoverTonalPalette {
    val hsv = composeColorToHsv(this)
    return CoverTonalPalette(
        seed = this,
        hue = hsv[0],
        seedSaturation = hsv[1]
    )
}

/**
 * 封面取色语义化色彩角色（Color Roles）：
 * - 在“取色结果（Seed Color）”与“UI 渲染”之间解耦，根据深色模式/日间浅色模式分别绑定最适配的色阶；
 * - 彻底解决深色模式下深蓝/深紫种子色叠在深色卡片上看不清、以及日间浅色模式下浅字叠浅底看不清的问题。
 */
@Immutable
data class CoverColorRoles(
    val seed: Color,
    val palette: CoverTonalPalette,
    val isDark: Boolean,
    /** 主强调色（Dark: Tone 80 清透亮色；Light: Tone 40 醇厚深色），用于滑块激活轨、高亮文字、选中标签 */
    val primary: Color,
    /** 实心 Primary 按钮/胶囊上的文字与图标色（Dark: Tone 20；Light: Tone 100） */
    val onPrimary: Color,
    /** 激活态卡片/瓷砖/胶囊容器填充色（Dark: Tone 30 融合暗底；Light: Tone 90 融合浅底） */
    val primaryContainer: Color,
    /** 激活态容器内的主标题与主图标色（Dark: Tone 90 极清亮色；Light: Tone 20 浓郁深色） */
    val onPrimaryContainer: Color,
    /** 激活态容器内的副标题与次级说明色（Dark: Tone 80；Light: Tone 30） */
    val onPrimaryContainerSubtle: Color,
    /** 次级激活胶囊/徽标底色 */
    val secondaryContainer: Color,
    /** 次级激活胶囊/徽标文字与图标色 */
    val onSecondaryContainer: Color,
    /** 播放页深色舞台（流光封面/黑胶舞台）上的高亮歌词与前景强调色（恒取 Tone 80） */
    val stageLyricPrimary: Color
)

/**
 * 根据当前深浅主题模式将封面种子色映射为语义化 [CoverColorRoles]。
 */
fun Color.toCoverColorRoles(
    isDark: Boolean,
    baseSurface: Color = if (isDark) Color(0xFF111826) else Color(0xFFF8FBFF)
): CoverColorRoles {
    val palette = this.toTonalPalette()
    return if (isDark) {
        val primaryContainer = palette.tone30.copy(alpha = 0.68f).compositeOver(baseSurface)
        val secondaryContainer = palette.tone20.copy(alpha = 0.72f).compositeOver(baseSurface)
        CoverColorRoles(
            seed = this,
            palette = palette,
            isDark = true,
            primary = palette.tone80,
            onPrimary = palette.tone20,
            primaryContainer = primaryContainer,
            onPrimaryContainer = palette.tone90,
            onPrimaryContainerSubtle = palette.tone80,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = palette.tone80,
            stageLyricPrimary = palette.tone80
        )
    } else {
        val primaryContainer = palette.tone90.copy(alpha = 0.88f).compositeOver(baseSurface)
        val secondaryContainer = palette.tone90.copy(alpha = 0.76f).compositeOver(baseSurface)
        CoverColorRoles(
            seed = this,
            palette = palette,
            isDark = false,
            primary = palette.tone40,
            onPrimary = palette.tone100,
            primaryContainer = primaryContainer,
            onPrimaryContainer = palette.tone20,
            onPrimaryContainerSubtle = palette.tone30,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = palette.tone30,
            stageLyricPrimary = palette.tone80
        )
    }
}

/**
 * Composable 便捷记忆函数：自动感知当前概念版深浅主题 (`isConceptDarkTheme()`) 与弹窗基底色，
 * 将封面种子色 [seedColor] 映射为语义化 [CoverColorRoles]。
 */
@Composable
fun rememberCoverColorRoles(seedColor: Color): CoverColorRoles {
    val isDark = isConceptDarkTheme()
    val baseSurface = conceptSheetContainerColor()
    return remember(seedColor, isDark, baseSurface) {
        seedColor.toCoverColorRoles(isDark = isDark, baseSurface = baseSurface)
    }
}

/**
 * 动态取色深色背景前景内容可读性增强算法（基于 13 级明度色阶 Tonal Palette）：
 * - 在完全锁定原取色色相 (Hue) 不变的前提下，将过暗的种子色映射至对应明度色阶（默认约 Tone 80），
 *   既保留鲜明的封面取色色相特征，又消除深色舞台/背景上的阅读吃力问题。
 */
fun Color.readableOnDarkSurface(minLuminance: Float = 0.42f): Color {
    if (this.perceptualLuminance() >= minLuminance) {
        return this
    }
    val targetTone = kotlin.math.ceil(relativeLuminanceToTone(minLuminance)).toInt().coerceIn(71, 95)
    return this.toTonalPalette().tone(targetTone).copy(alpha = this.alpha)
}

/**
 * 动态取色深色卡片内次级副标题/说明文案可读性增强：
 * - 基于 13 级色阶映射至 Tone 80~92 明度区间，确保小字号副标题在深色背景上清晰通透。
 */
fun Color.readableSubtleOnDarkSurface(minLuminance: Float = 0.54f): Color {
    val targetTone = kotlin.math.ceil(relativeLuminanceToTone(minLuminance)).toInt().coerceIn(80, 92)
    return this.toTonalPalette().tone(targetTone).copy(alpha = 0.94f)
}
