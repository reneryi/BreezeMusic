package com.concept.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.concept.music.ui.theme.ConceptMotion
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalIsDarkTheme
import kotlin.math.abs

/**
 * 120fps 丝滑长列表快速定位与返回顶部滚动引擎：
 * 当目标位置距离当前可视区域超过 12 个条目时，先瞬移锚定至目标前/后 10 个条目处，
 * 再执行顺滑惯性滚动动画，彻底消除长歌单跨百首歌曲逐项 Compose 测量引发的掉帧卡顿。
 */
suspend fun LazyListState.smoothFastScrollToItem(
    targetIndex: Int,
    scrollOffset: Int = 0
) {
    val safeTarget = targetIndex.coerceAtLeast(0)
    val current = firstVisibleItemIndex
    val distance = abs(current - safeTarget)
    if (distance > 12) {
        val preJumpIndex = if (current > safeTarget) {
            safeTarget + 10
        } else {
            (safeTarget - 10).coerceAtLeast(0)
        }
        runCatching { scrollToItem(preJumpIndex, 0) }
    }
    runCatching { animateScrollToItem(safeTarget, scrollOffset) }
}

/**
 * 悬浮于页面上层的极简返回顶部按钮（仅显示图标，无多余文字）
 */
@Composable
fun FloatingScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    accentColor: Color = ConceptPrimary,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val containerBg = if (isDark) Color(0xEE182438) else Color(0xF5FFFFFF)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180, easing = ConceptMotion.EmphasizedDecelerate)) +
            scaleIn(initialScale = 0.75f, animationSpec = ConceptMotion.expressiveSpring()) +
            slideInVertically(animationSpec = ConceptMotion.expressiveSpring()) { it / 2 },
        exit = fadeOut(tween(150, easing = ConceptMotion.EmphasizedAccelerate)) +
            scaleOut(targetScale = 0.75f, animationSpec = ConceptMotion.spatialPageSpring()) +
            slideOutVertically(animationSpec = ConceptMotion.spatialPageSpring()) { it / 2 },
        modifier = modifier
    ) {
        FilledIconButton(
            onClick = onClick,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = containerBg,
                contentColor = accentColor
            ),
            modifier = Modifier
                .size(42.dp)
                .shadow(8.dp, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.VerticalAlignTop,
                contentDescription = "返回顶部",
                tint = accentColor,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}
