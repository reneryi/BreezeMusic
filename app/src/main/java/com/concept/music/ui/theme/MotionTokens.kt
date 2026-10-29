package com.concept.music.ui.theme

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 全局统一分层弹性物理动效规范中心 (Material 3 Expressive Motion & Apple Music 物理曲线体系)：
 * - 空间转场弹簧 (Spatial Springs)：用于页面推入/弹出、同平面主页面左右滑动、底部抽屉滑入滑出，兼具丝滑柔和与适度弹性灵动感；
 * - 分类内容与悬浮窗弹簧 (Category & Floating Springs)：用于分类标题下方内容同平面弹性切换、播放页及全局悬浮窗口灵动绽放弹出；
 * - 灵动微交互弹簧 (Expressive Micro Springs)：用于按钮按压回弹、底部导航胶囊滑动、红心点亮、封面缩放、3D 叠卡归位，赋予指尖生命力；
 * - 方向性运动虚化引擎 (Directional Motion Blur)：在 120Hz RenderNode 绘制阶段按水平/垂直运动速度动态施加高斯方向虚化，静止帧自动归零释放开销。
 */
object ConceptMotion {
    // 独立页面与二级菜单卡片转场弹簧：平滑柔顺且带温润微弹性回落 (~0.84 阻尼比)
    const val SPATIAL_PAGE_DAMPING = 0.84f
    const val SPATIAL_PAGE_STIFFNESS = 310f

    // 底部抽屉弹窗灵动弹簧：沿用播放队列抽屉丝滑初速，加入恰到好处的弹性灵动感 (~0.81 阻尼比)
    const val SPATIAL_SHEET_DAMPING = 0.81f
    const val SPATIAL_SHEET_STIFFNESS = 335f

    // 底部导航栏「发现 ↔ 我的」同平面左右滑动弹簧：与底栏流体胶囊同频共振
    const val COPLANAR_TAB_DAMPING = 0.82f
    const val COPLANAR_TAB_STIFFNESS = 325f

    // 分类标题下方内容同平面切换弹簧：富有弹性的横向切入与归位
    const val CATEGORY_CONTENT_DAMPING = 0.77f
    const val CATEGORY_CONTENT_STIFFNESS = 340f

    // 播放页及全局悬浮窗口弹出弹簧：灵动 Q 弹的缩放绽放感
    const val FLOATING_POPUP_DAMPING = 0.74f
    const val FLOATING_POPUP_STIFFNESS = 370f

    const val EXPRESSIVE_MICRO_DAMPING = 0.75f
    const val EXPRESSIVE_MICRO_STIFFNESS = 290f

    const val GENTLE_BOUNCE_DAMPING = 0.82f
    const val GENTLE_BOUNCE_STIFFNESS = 240f

    /** 页面路由推入/返回与设置二级菜单卡片切换弹簧（平滑柔和 + 适度弹性） */
    fun <T> spatialPageSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = SPATIAL_PAGE_DAMPING,
        stiffness = SPATIAL_PAGE_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    /** 底部抽屉面板升降灵动弹簧（丝滑弹出 + 柔和弹性定格，底部锚定不露白） */
    fun <T> spatialSheetSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = SPATIAL_SHEET_DAMPING,
        stiffness = SPATIAL_SHEET_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    /** 主页面（发现 ↔ 我的）同平面左右滑动弹簧 */
    fun <T> coplanarTabSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = COPLANAR_TAB_DAMPING,
        stiffness = COPLANAR_TAB_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    /** 分类标题下方内容同平面弹性切换弹簧 */
    fun <T> categoryContentSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = CATEGORY_CONTENT_DAMPING,
        stiffness = CATEGORY_CONTENT_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    /** 悬浮窗口 / 模态卡片弹性绽放弹出弹簧 */
    fun <T> floatingPopupSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = FLOATING_POPUP_DAMPING,
        stiffness = FLOATING_POPUP_STIFFNESS,
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
        stiffness = 340f,
        visibilityThreshold = visibilityThreshold
    )

    /** 松手温润弹性恢复弹簧 */
    fun <T> softReleaseSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = 0.76f,
        stiffness = 280f,
        visibilityThreshold = visibilityThreshold
    )

    /** 丝滑入场减速曲线（舒展柔和、前快后慢） */
    val GentleSpatialEnterEasing = CubicBezierEasing(0.18f, 0.92f, 0.24f, 1.0f)

    /** 丝滑退场平滑曲线（自然收拢、干脆柔和） */
    val GentleSpatialExitEasing = CubicBezierEasing(0.28f, 0.08f, 0.26f, 1.0f)

    /** M3 Emphasized Decelerate 曲线（入场透明度与色彩展开） */
    val EmphasizedDecelerate = CubicBezierEasing(0.08f, 0.78f, 0.16f, 1.0f)

    /** M3 Emphasized Accelerate 曲线（退场收敛） */
    val EmphasizedAccelerate = CubicBezierEasing(0.30f, 0.0f, 0.72f, 0.22f)

    /** 120Hz 柔和滚动推进曲线（用于逐字歌词黄金焦点平滑滚动） */
    val LyricSmoothScrollEasing = CubicBezierEasing(0.22f, 0.90f, 0.28f, 1.0f)
}

/**
 * 120Hz RenderNode 硬件加速方向性动态虚化 (Directional Motion Blur)：
 * - 仅在运动过渡途中 (`blurX > 0.45f || blurY > 0.45f`) 挂载 GPU RenderEffect；
 * - 动画静止后立即恢复 `renderEffect = null`，确保常规浏览时 0 离屏模糊开销。
 */
fun GraphicsLayerScope.applyDirectionalMotionBlur(
    blurX: Float,
    blurY: Float = 0.8f
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val bx = blurX.coerceIn(0f, 28f)
        val by = blurY.coerceIn(0f, 20f)
        renderEffect = if (bx > 0.45f || by > 0.45f) {
            AndroidRenderEffect.createBlurEffect(
                bx.coerceAtLeast(0.5f),
                by.coerceAtLeast(0.5f),
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        } else {
            null
        }
    }
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
                if (dragAmount > 0f || canDrag()) {
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
 * 通用自研玻璃拟态浮层与弹性弹窗动画宿主容器 (ConceptAnimatedOverlaySheet)：
 * - 完整复刻播放页「播放队列抽屉 (QueueBottomSheet)」丝滑柔和的底层架构：
 *   1. 零独立 DialogWindow 创建开销，消除打开/关闭首帧卡顿；
 *   2. 所有动画进度与手势位移 100% 延迟至 `Modifier.graphicsLayer` GPU 绘制阶段读取，过渡全程 0 次内容重组；
 *   3. 闭环退场动画 (`triggerAnimatedDismiss`)，点击遮罩、返回键或关闭按钮时先完整播放丝滑退出动画再卸载；
 * - 升级弹性灵动物理表现：
 *   - 竖屏抽屉模式 (`!isLandscape`)：采用 `ConceptMotion.spatialSheetSpring()` 弹性升起；当弹簧产生轻微过冲 (`rawP > 1f`) 时，
 *     以底部中心 `TransformOrigin(0.5f, 1.0f)` 转化为轻微纵向弹性舒展，底部边缘 100% 贴合屏幕底边绝不露白；并支持向上轻拉弹性阻尼与向下跟手关闭；
 *   - 悬浮弹窗模式 (`isLandscape = true`)：采用 `ConceptMotion.floatingPopupSpring()` 从 `0.86f -> 1.0f` 弹性缩放绽放 + 微上浮 + 运动虚化渐显。
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

    val triggerAnimatedDismiss: () -> Unit = remember(scope, onDismiss, isLandscape) {
        {
            if (!isDismissing) {
                isDismissing = true
                scope.launch {
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = if (isLandscape) 250 else 310,
                            easing = ConceptMotion.GentleSpatialExitEasing
                        )
                    )
                    onDismiss()
                }
            }
        }
    }

    LaunchedEffect(visible) {
        if (visible && !isDismissing) {
            if (isLandscape) {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = ConceptMotion.floatingPopupSpring()
                )
            } else {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = ConceptMotion.spatialSheetSpring()
                )
            }
        } else if (!visible && !isDismissing) {
            isDismissing = true
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = if (isLandscape) 250 else 310,
                    easing = ConceptMotion.GentleSpatialExitEasing
                )
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
                    val current = dragOffsetY.value
                    // 向下拉动 0.92f 跟手，向上轻拉施加 0.18f 柔性橡皮筋阻尼以体现弹性灵动感
                    val next = if (current + delta >= 0f) {
                        (current + delta * 0.92f).coerceAtMost(2400f)
                    } else {
                        (current + delta * 0.18f).coerceAtLeast(-36f)
                    }
                    if (next != current) {
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
                                        targetValue = dragOffsetY.value + 1100f,
                                        animationSpec = tween(
                                            durationMillis = 280,
                                            easing = ConceptMotion.GentleSpatialExitEasing
                                        )
                                    )
                                }
                                launch {
                                    progress.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = 280,
                                            easing = ConceptMotion.GentleSpatialExitEasing
                                        )
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
                    val dragDecay = (dragOffsetY.value.coerceAtLeast(0f) / 1300f).coerceIn(0f, 0.70f)
                    alpha = (p * (1f - dragDecay)).coerceIn(0f, 1f)
                }
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = triggerAnimatedDismiss
                )
        )

        // 面板主体容器（绘制阶段执行弹性弹簧升降/缩放与跟手拖拽位移，0 重组）
        Box(
            contentAlignment = contentAlignment,
            modifier = Modifier.graphicsLayer {
                val rawP = progress.value
                val clampedP = rawP.coerceIn(0f, 1f)
                val rawDragY = dragOffsetY.value
                if (isLandscape) {
                    // 悬浮窗口弹性绽放弹出：允许 rawP 产生 ~1.03f 的柔和弹簧过冲回弹
                    val s = (0.86f + 0.14f * rawP).coerceIn(0.75f, 1.05f)
                    scaleX = s
                    scaleY = s
                    translationY = (1f - rawP) * 26f * density
                    alpha = (clampedP * 1.15f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, 0.54f)
                    val blurStrength = ((1f - clampedP) * 8f * density).coerceIn(0f, 10f)
                    applyDirectionalMotionBlur(blurX = blurStrength * 0.6f, blurY = blurStrength)
                } else {
                    val fullH = size.height.takeIf { it > 80f } ?: (500f * density)
                    val overshoot = (rawP - 1f).coerceAtLeast(0f)
                    val enterOffsetY = (1f - clampedP) * fullH
                    val positiveDragY = rawDragY.coerceAtLeast(0f)
                    val negativePullStretch = (-rawDragY).coerceAtLeast(0f) / fullH.coerceAtLeast(320f)
                    translationY = enterOffsetY + positiveDragY
                    val dragScale = (1f - (positiveDragY / 3200f)).coerceIn(0.94f, 1f)
                    val baseScaleX = (0.955f + 0.045f * clampedP) * (1f - overshoot * 0.08f)
                    val baseScaleY = (0.955f + 0.045f * clampedP) * (1f + overshoot * 0.36f + negativePullStretch * 0.35f)
                    scaleX = (baseScaleX * dragScale).coerceIn(0.90f, 1.04f)
                    scaleY = (baseScaleY * dragScale).coerceIn(0.90f, 1.06f)
                    alpha = (0.18f + 0.82f * clampedP).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, 1.0f)
                }
            }
        ) {
            sheetScope.content()
        }
    }
}
