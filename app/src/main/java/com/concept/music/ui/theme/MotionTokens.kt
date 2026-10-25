package com.concept.music.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 全局统一分层弹性物理动效规范中心 (Material 3 Expressive Motion & Apple Music 物理曲线体系)：
 * - 空间转场弹簧 (Spatial Springs)：用于页面推入/弹出、全屏播放器升降、底部抽屉滑入滑出，柔顺舒展、跟手可打断且不晃眼眩晕；
 * - 灵动微交互弹簧 (Expressive Micro Springs)：用于按钮按压回弹、底部导航胶囊滑动、红心点亮、封面缩放、3D 叠卡归位，赋予指尖生命力；
 * - 强调型缓动曲线 (Emphasized Easing)：用于非空间视觉属性（透明度、动态取色）平滑配速。
 */
object ConceptMotion {
    // 调低刚度 (Stiffness) 并提升阻尼比至临界柔顺区间：消除急促感与边缘回弹抖动，使页面与浮层过渡舒展丝滑 (~420-480ms)
    const val SPATIAL_PAGE_DAMPING = 0.96f
    const val SPATIAL_PAGE_STIFFNESS = 175f

    const val SPATIAL_SHEET_DAMPING = 0.98f
    const val SPATIAL_SHEET_STIFFNESS = 185f

    const val EXPRESSIVE_MICRO_DAMPING = 0.78f
    const val EXPRESSIVE_MICRO_STIFFNESS = 250f

    const val GENTLE_BOUNCE_DAMPING = 0.86f
    const val GENTLE_BOUNCE_STIFFNESS = 195f

    /** 页面路由推入/返回与内部视图层级切换柔和弹簧（临界阻尼，无抖动过冲） */
    fun <T> spatialPageSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = SPATIAL_PAGE_DAMPING,
        stiffness = SPATIAL_PAGE_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    /** 全屏播放器与底部抽屉面板升降柔顺弹簧（零过冲，彻底杜绝底边/侧边越界露白） */
    fun <T> spatialSheetSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = SPATIAL_SHEET_DAMPING,
        stiffness = SPATIAL_SHEET_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    /** 图标形变、胶囊滑块、红心收藏、封面呼吸缩放等温润微交互弹簧 */
    fun <T> expressiveSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = EXPRESSIVE_MICRO_DAMPING,
        stiffness = EXPRESSIVE_MICRO_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    /** 松手柔和回落弹簧（用于卡片/按钮释放、手势阻尼恢复） */
    fun <T> gentleBounceSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = GENTLE_BOUNCE_DAMPING,
        stiffness = GENTLE_BOUNCE_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    /** 按下瞬间柔和收敛弹簧 */
    fun <T> softPressSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 280f,
        visibilityThreshold = visibilityThreshold
    )

    /** 松手温润恢复弹簧 */
    fun <T> softReleaseSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = 240f,
        visibilityThreshold = visibilityThreshold
    )

    /** 丝滑入场减速曲线（舒展柔和、前快后慢无抖动） */
    val GentleSpatialEnterEasing = CubicBezierEasing(0.18f, 0.92f, 0.26f, 1.0f)

    /** 丝滑退场平滑曲线（自然收拢、不仓促） */
    val GentleSpatialExitEasing = CubicBezierEasing(0.28f, 0.08f, 0.26f, 1.0f)

    /** M3 Emphasized Decelerate 曲线（入场透明度与色彩展开） */
    val EmphasizedDecelerate = CubicBezierEasing(0.08f, 0.78f, 0.16f, 1.0f)

    /** M3 Emphasized Accelerate 曲线（退场收敛） */
    val EmphasizedAccelerate = CubicBezierEasing(0.30f, 0.0f, 0.72f, 0.22f)

    /** 120Hz 柔和滚动推进曲线（用于逐字歌词黄金焦点平滑滚动） */
    val LyricSmoothScrollEasing = CubicBezierEasing(0.22f, 0.90f, 0.28f, 1.0f)
}

/**
 * 自研玻璃拟态抽屉/弹窗动画作用域：
 * 提供带完整退场动画闭环的 `requestDismiss()` 与顶部手势下滑修饰符 `sheetVerticalDragGesture()`。
 */
@Stable
class ConceptOverlaySheetScope internal constructor(
    val requestDismiss: () -> Unit,
    private val onDragDelta: (Float) -> Unit,
    private val onDragFinish: () -> Unit,
    private val onDragCancel: () -> Unit,
    private val canDrag: () -> Boolean
) {
    /**
     * 挂载于竖屏底部抽屉顶部手势横条或标题栏区域，支持跟手阻尼下滑与超阈值顺滑关闭
     */
    fun Modifier.sheetVerticalDragGesture(): Modifier = this.pointerInput(this@ConceptOverlaySheetScope) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                if (!canDrag()) return@detectVerticalDragGestures
                if (dragAmount > 0f) {
                    change.consume()
                }
                onDragDelta(dragAmount)
            },
            onDragCancel = { onDragCancel() },
            onDragEnd = { onDragFinish() }
        )
    }
}

/**
 * 通用自研玻璃拟态浮层动画宿主容器 (ConceptAnimatedOverlaySheet)：
 * - 彻底解决裸 `if (show)` 条件渲染导致的「关闭瞬间 0ms 瞬断闪没」与「入场仅 13dp 原地闪现」问题；
 * - 竖屏模式：遮罩渐显 + 面板从底部 100% 高度以 `SpatialSheetSpring` 丝滑升起，关闭或下拉超阈值时完整滑出屏幕底部后再触发 `onDismiss` 卸载；
 * - 横屏模式：居中悬浮玻璃面板以 `0.90f -> 1.0f` 弹性缩放 + 微上浮绽放弹出，关闭时柔和微缩淡出；
 * - 所有动画属性均严格在 `Modifier.graphicsLayer` 绘制阶段读取，过渡全程 0 次重组。
 */
@Composable
fun ConceptAnimatedOverlaySheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    isLandscape: Boolean = false,
    scrimAlpha: Float = if (isLandscape) 0.56f else 0.50f,
    zIndex: Float = 85f,
    dismissDragThresholdPx: Float = 115f,
    contentAlignment: Alignment = if (isLandscape) Alignment.Center else Alignment.BottomCenter,
    content: @Composable ConceptOverlaySheetScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    val triggerAnimatedDismiss: () -> Unit = remember(scope, onDismiss) {
        {
            if (!isDismissing) {
                isDismissing = true
                scope.launch {
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 360, easing = ConceptMotion.GentleSpatialExitEasing)
                    )
                    onDismiss()
                }
            }
        }
    }

    LaunchedEffect(visible) {
        if (visible && !isDismissing) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = ConceptMotion.GentleSpatialEnterEasing)
            )
        } else if (!visible && !isDismissing) {
            isDismissing = true
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 360, easing = ConceptMotion.GentleSpatialExitEasing)
            )
            onDismiss()
        }
    }

    BackHandler(enabled = visible && !isDismissing) {
        triggerAnimatedDismiss()
    }

    val sheetScope = remember(isLandscape, dismissDragThresholdPx, triggerAnimatedDismiss) {
        ConceptOverlaySheetScope(
            requestDismiss = triggerAnimatedDismiss,
            onDragDelta = { delta ->
                if (!isLandscape && !isDismissing) {
                    val next = (dragOffsetY.value + delta * 0.90f).coerceAtLeast(0f)
                    if (next != dragOffsetY.value) {
                        scope.launch { dragOffsetY.snapTo(next) }
                    }
                }
            },
            onDragFinish = {
                if (!isLandscape && !isDismissing) {
                    if (dragOffsetY.value > dismissDragThresholdPx) {
                        isDismissing = true
                        scope.launch {
                            coroutineScope {
                                launch {
                                    dragOffsetY.animateTo(
                                        targetValue = dragOffsetY.value + 1200f,
                                        animationSpec = ConceptMotion.spatialSheetSpring()
                                    )
                                }
                                launch {
                                    progress.animateTo(
                                        targetValue = 0f,
                                        animationSpec = ConceptMotion.spatialSheetSpring()
                                    )
                                }
                            }
                            onDismiss()
                        }
                    } else {
                        scope.launch {
                            dragOffsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = ConceptMotion.gentleBounceSpring()
                            )
                        }
                    }
                }
            },
            onDragCancel = {
                if (!isLandscape && !isDismissing) {
                    scope.launch {
                        dragOffsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = ConceptMotion.gentleBounceSpring()
                        )
                    }
                }
            },
            canDrag = { !isLandscape && !isDismissing }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(zIndex),
        contentAlignment = contentAlignment
    ) {
        // 全屏沉浸式背景遮罩（绘制阶段动态透明度，0 重组）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val dragDecay = (dragOffsetY.value / 1400f).coerceIn(0f, 0.65f)
                    alpha = (p * (1f - dragDecay)).coerceIn(0f, 1f)
                }
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = triggerAnimatedDismiss
                )
        )

        // 面板主体容器（绘制阶段执行弹簧升降/缩放与跟手拖拽位移，0 重组）
        Box(
            contentAlignment = contentAlignment,
            modifier = Modifier.graphicsLayer {
                val p = progress.value.coerceIn(0f, 1f)
                val dragY = dragOffsetY.value.coerceAtLeast(0f)
                if (isLandscape) {
                    val s = 0.90f + 0.10f * p
                    scaleX = s
                    scaleY = s
                    translationY = (1f - p) * 28f * density
                    alpha = p.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                } else {
                    val fullH = size.height.takeIf { it > 80f } ?: (480f * density)
                    val enterOffsetY = (1f - p) * fullH
                    translationY = enterOffsetY + dragY
                    val dragScale = (1f - (dragY / 3200f)).coerceIn(0.94f, 1f)
                    val baseScale = 0.96f + 0.04f * p.coerceIn(0f, 1f)
                    scaleX = baseScale * dragScale
                    scaleY = baseScale * dragScale
                    alpha = (0.15f + 0.85f * p).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, 1.0f)
                }
            }
        ) {
            sheetScope.content()
        }
    }
}
