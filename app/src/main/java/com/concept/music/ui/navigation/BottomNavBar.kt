package com.concept.music.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.concept.music.ui.theme.ConceptPrimary

/**
 * 紧凑轻量底栏：
 * - 大幅降低高度（由 80.dp 缩减至精致的 50.dp）
 * - 去除冗余文字标识，纯粹现代流体图标与激活态微胶囊
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF2101319))
            .navigationBarsPadding()
    ) {
        HorizontalDivider(
            thickness = 0.6.dp,
            color = Color.White.copy(alpha = 0.06f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavScreens.forEach { screen ->
                val isSelected = currentRoute == screen.route
                val animatedBgColor by animateColorAsState(
                    targetValue = if (isSelected) ConceptPrimary.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "nav_item_bg"
                )
                val animatedIconColor by animateColorAsState(
                    targetValue = if (isSelected) ConceptPrimary else Color.White.copy(alpha = 0.45f),
                    animationSpec = tween(durationMillis = 200),
                    label = "nav_item_icon"
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clickable { onNavigate(screen.route) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(animatedBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            tint = animatedIconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
