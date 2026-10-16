package com.concept.music.ui.components

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
import com.concept.music.ui.theme.LocalIsDarkTheme

/**
 * 饱满圆润无边框的 VIP 权益中心入口横条（深色模式黑金流光 / 浅色模式香槟暖金水彩）
 */
@Composable
fun VipBannerCard(
    state: VipUiState,
    loggedIn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val todayRecord = state.records.find { it.day == today() }
    val isClaimedToday = todayRecord?.received == true

    val statusText = when {
        !loggedIn -> "登录立领 1 日 VIP"
        isClaimedToday -> "今日 VIP 已激活"
        state.busy -> "权益同步中..."
        else -> "每日免费领 1 日 VIP"
    }

    val bgBrush = if (isDark) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF282016),
                Color(0xFF1D1922),
                Color(0xFF151924)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFF3D6),
                Color(0xFFFFF8E8),
                Color(0xFFF3F6FF)
            )
        )
    }

    val titleColor = if (isDark) Color.White else Color(0xFF2B1F0A)
    val subColor = if (isDark) Color(0xFFFFD54F).copy(alpha = 0.8f) else Color(0xFF946200)
    val statusColor = when {
        isClaimedToday -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        else -> if (isDark) Color(0xFFFFD54F) else Color(0xFFB87900)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = if (isDark) 0.dp else 5.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgBrush)
                .padding(horizontal = 18.dp, vertical = 15.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VipCrownBadge(size = 40.dp)

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "VIP 权益中心",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = titleColor,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = "无损音质与每日免费 VIP 领取",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = subColor,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "进入 VIP 权益中心",
                        tint = titleColor.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VipCrownBadge(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val goldColor = if (isDark) Color(0xFFFFD54F) else Color(0xFFD48800)
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(13.dp),
        color = goldColor.copy(alpha = 0.16f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val strokeWidth = 2.dp.toPx()

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
