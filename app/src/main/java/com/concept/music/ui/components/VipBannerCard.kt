package com.concept.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.data.kugou.VipUiState
import com.concept.music.data.kugou.today

/**
 * 饱满圆润的福利活动与VIP领取入口横条 (参考酷狗概念版官方设计)
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
        isClaimedToday -> "今日权益已激活"
        state.busy -> "特权同步中..."
        else -> "每日免费领 1 日 VIP"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF181B23),
        tonalElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF2A231C),
                            Color(0xFF1E1A22),
                            Color(0xFF181B24)
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
                // 左侧醒目标识：尊贵钻石/皇冠 + 微风权益馆
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = Color(0xFFFFC107).copy(alpha = 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CardGiftcard,
                                contentDescription = "微风权益馆",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "微风权益馆",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = "概念版专属会员礼遇",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFFFD54F).copy(alpha = 0.7f),
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
                        contentDescription = "进入详情",
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
