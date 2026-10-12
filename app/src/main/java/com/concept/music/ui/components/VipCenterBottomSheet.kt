package com.concept.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import com.concept.music.ui.theme.ConceptPrimary

/**
 * 概念版 VIP 专属二级权益中心抽屉界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipCenterBottomSheet(
    state: VipUiState,
    loggedIn: Boolean,
    autoClaim: Boolean,
    onDismiss: () -> Unit,
    onClaim: () -> Unit,
    onAutoChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14171E),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.2f)
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 顶栏标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = Color(0xFFFFB300).copy(alpha = 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.WorkspacePremium,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "概念版权益中心",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "畅享 SQ 无损听歌与专属特权",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // 1. 黑金高质感 VIP 卡片
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF2C2419),
                                    Color(0xFF1E1A18),
                                    Color(0xFF171415)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "概念版 VIP 会员",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFFD54F),
                                    fontSize = 17.sp
                                )
                            )

                            if (loggedIn && state.snapshot.checked) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFFD54F).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (state.fresh) "云端已同步" else "待刷新",
                                        color = Color(0xFFFFD54F),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (!loggedIn) {
                            Text(
                                text = "登录酷狗账号后，可每天一键免费领取 1 天专属 VIP 权益，畅听全网曲库！",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    lineHeight = 20.sp
                                )
                            )
                        } else {
                            val todayRecord = state.records.find { it.day == today() }
                            val recordDesc = when {
                                todayRecord?.received != true -> if (state.fresh) "今日尚未领取权益" else "待查询今日记录"
                                todayRecord.type == "svip" -> "今日已领：概念版高级权益"
                                else -> "今日已领：畅听权益，可继续升级"
                            }

                            Text(
                                text = recordDesc,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            )

                            if (state.snapshot.benefits.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    state.snapshot.benefits.forEach { benefit ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFFFFCA28),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = benefit.display(),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.White.copy(alpha = 0.7f)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.busy) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = Color(0xFFFFD54F),
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }

                        state.result?.let {
                            Text(
                                text = it.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        state.error?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.error
                                )
                            )
                        }

                        // 一键领取 / 登录按钮
                        Button(
                            onClick = { if (loggedIn) onClaim() else onLogin() },
                            enabled = !state.busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD54F),
                                contentColor = Color(0xFF2C2419),
                                disabledContainerColor = Color(0xFFFFD54F).copy(alpha = 0.4f),
                                disabledContentColor = Color(0xFF2C2419).copy(alpha = 0.4f)
                            )
                        ) {
                            @Suppress("DEPRECATION")
                            Icon(
                                imageVector = if (loggedIn) Icons.Rounded.CardGiftcard else Icons.Rounded.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (loggedIn) "一键领取今日 1 天 VIP" else "立即登录领取",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. 自动领取开关卡片
            if (loggedIn) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "打开应用时自动领取",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "每日启动时自动复核并领取 1 天 VIP · 仅当前账号生效",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            )
                        }

                        Switch(
                            checked = autoClaim,
                            onCheckedChange = onAutoChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ConceptPrimary,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }

                // 3. 刷新与说明按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "酷狗概念版官方每日赠送权益",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    )

                    TextButton(
                        onClick = onRefresh,
                        enabled = !state.busy,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            tint = ConceptPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "刷新会员状态",
                            color = ConceptPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
