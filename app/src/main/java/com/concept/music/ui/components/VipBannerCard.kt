package com.concept.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.data.kugou.VipUiState
import com.concept.music.data.kugou.today

/**
 * 饱满圆润的 VIP 权益中心入口横条
 */
@Composable
fun VipBannerCard(
    state: VipUiState,
    loggedIn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todayRecord = state.records.find { it.day == today() }
    val isClaimedToday = todayRecord?.received == true

    val statusText = when {
        !loggedIn -> "登录立领 1 日 VIP"
        isClaimedToday -> "今日 VIP 已激活"
        state.busy -> "权益同步中..."
        else -> "每日免费领 1 日 VIP"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF181B23),
        border = BorderStroke(
            width = 0.8.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFFFD54F).copy(alpha = 0.32f),
                    Color(0xFFFFB300).copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.05f)
                )
            )
        ),
        tonalElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF2B2318),
                            Color(0xFF1F1B22),
                            Color(0xFF171A23)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧醒目标识：黑金皇冠钻章 + VIP 权益中心
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VipCrownBadge(size = 38.dp)

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "VIP 权益中心",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = "无损音质与每日免费 VIP 领取",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFFFD54F).copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // 右侧提示文案 + 箭头
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isClaimedToday) Color(0xFF81C784) else Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "进入 VIP 权益中心",
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 极简现代风格 VIP 线性皇冠徽章组件
 */
@Composable
fun VipCrownBadge(
    size: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(11.dp),
        color = Color(0xFFFFD54F).copy(alpha = 0.14f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val goldColor = Color(0xFFFFD54F)
            val strokeWidth = 2.dp.toPx()

            // 极简一体式几何皇冠轮廓
            val crownPath = Path().apply {
                moveTo(w * 0.26f, h * 0.68f)
                lineTo(w * 0.22f, h * 0.38f)
                lineTo(w * 0.39f, h * 0.51f)
                lineTo(w * 0.50f, h * 0.31f)
                lineTo(w * 0.61f, h * 0.51f)
                lineTo(w * 0.78f, h * 0.38f)
                lineTo(w * 0.74f, h * 0.68f)
                close()
            }
            drawPath(
                path = crownPath,
                color = goldColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

