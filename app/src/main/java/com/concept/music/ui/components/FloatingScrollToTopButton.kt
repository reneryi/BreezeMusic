package com.concept.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalIsDarkTheme

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
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.82f, animationSpec = tween(220)),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.82f, animationSpec = tween(180)),
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
