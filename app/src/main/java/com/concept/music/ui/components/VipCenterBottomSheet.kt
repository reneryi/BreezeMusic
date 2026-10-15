package com.concept.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.data.kugou.CHINA_ZONE
import com.concept.music.data.kugou.VipUiState
import com.concept.music.data.kugou.today
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.VipGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * 概念版 VIP 专属二级权益中心抽屉界面
 * [创新4] 升级集成「当月 VIP 守护日历热力图」与「风控自愈 & 音源链路诊断沙盒」
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentDfid by remember { mutableStateOf(runCatching { VipManager.device().dfid }.getOrDefault("-")) }
    var refreshingDfid by remember { mutableStateOf(false) }
    var diagnosing by remember { mutableStateOf(false) }
    var diagnosticSummary by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF101624),
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VipCrownBadge(size = 38.dp)
                    Column {
                        Text(
                            text = "VIP 权益中心 & 守护沙盒",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "畅享 SQ / Hi-Res 无损听歌 · 月度热力日历与链路自愈",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.55f),
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
                                    Color(0xFF1A1E2D),
                                    Color(0xFF121622)
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
                                todayRecord.type == "svip" -> "今日已领：概念版高级权益 (SVIP)"
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

            // 2. [创新4] 月度 VIP 守护日历热力图 (Monthly VIP Heatmap Calendar)
            val currentDate = remember { LocalDate.now(CHINA_ZONE) }
            val currentYearMonth = remember(currentDate) { YearMonth.from(currentDate) }
            val monthPrefix = remember(currentYearMonth) { currentYearMonth.toString() } // e.g. "2026-10"
            val claimedDaysMap = remember(state.records, monthPrefix) {
                state.records
                    .filter { it.day.startsWith(monthPrefix) && it.received }
                    .associateBy({ it.day.takeLast(2).toIntOrNull() ?: 0 }, { it.type })
            }
            val claimedCountThisMonth = claimedDaysMap.size

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171E2E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${currentYearMonth.year}年${currentYearMonth.monthValue}月 · VIP 守护日历热力图",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "本月已打卡领取 $claimedCountThisMonth 天 · 累计相当节省 ¥${String.format(java.util.Locale.getDefault(), "%.1f", claimedCountThisMonth * 0.6)} 会员费",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = VipGold
                                )
                            )
                        }
                        Surface(
                            color = ConceptPrimary.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "$claimedCountThisMonth / ${currentYearMonth.lengthOfMonth()} 天",
                                color = ConceptPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // 7 列日历热力网格
                    val totalDays = currentYearMonth.lengthOfMonth()
                    val rows = (1..totalDays).chunked(7)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rows.forEach { weekDays ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (i in 0 until 7) {
                                    val dayNum = weekDays.getOrNull(i)
                                    if (dayNum != null) {
                                        val claimType = claimedDaysMap[dayNum]
                                        val isToday = dayNum == currentDate.dayOfMonth
                                        val cellBg = when (claimType) {
                                            "svip" -> VipGold.copy(alpha = 0.32f)
                                            "tvip" -> ConceptPrimary.copy(alpha = 0.28f)
                                            else -> if (claimType != null) ConceptPrimary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.05f)
                                        }
                                        val borderColor = when {
                                            isToday -> ConceptCyanAccent
                                            claimType == "svip" -> VipGold.copy(alpha = 0.7f)
                                            claimType != null -> ConceptPrimary.copy(alpha = 0.6f)
                                            else -> Color.Transparent
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(cellBg)
                                                .border(
                                                    width = if (borderColor != Color.Transparent) 1.dp else 0.dp,
                                                    color = borderColor,
                                                    shape = RoundedCornerShape(8.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayNum.toString(),
                                                fontSize = 11.sp,
                                                fontWeight = if (claimType != null || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    claimType == "svip" -> VipGold
                                                    claimType != null -> ConceptPrimary
                                                    isToday -> ConceptCyanAccent
                                                    else -> Color.White.copy(alpha = 0.45f)
                                                },
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f).height(32.dp))
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendDot(color = VipGold, label = "SVIP 概念升级")
                        LegendDot(color = ConceptPrimary, label = "畅听 VIP 已领")
                        LegendDot(color = ConceptCyanAccent, label = "今日焦点")
                    }
                }
            }

            // 3. [创新4] 风控自愈与音源链路诊断沙盒 (Anti-Risk & Gateway Latency Sandbox)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171E2E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "风控自愈与音源链路诊断沙盒",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            val dfidShort = if (currentDfid.length > 12) "${currentDfid.take(6)}...${currentDfid.takeLast(4)}" else currentDfid
                            Text(
                                text = "当前设备 dfid：$dfidShort · 遇 20028 风控或无损降级可一键自愈",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                            )
                        }
                    }

                    diagnosticSummary?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ConceptPrimary.copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                color = ConceptCyanAccent,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                refreshingDfid = true
                                scope.launch {
                                    val newDfid = withContext(Dispatchers.IO) {
                                        runCatching {
                                            VipManager.music.clearPlaybackCache()
                                            VipManager.auth.registerDevice()
                                        }.getOrNull()
                                    }
                                    refreshingDfid = false
                                    currentDfid = newDfid ?: runCatching { VipManager.device().dfid }.getOrDefault("-")
                                    diagnosticSummary = "已刷新设备 dfid 凭证并清空旧音源缓存，可重新尝试播放或领 VIP"
                                    Toast.makeText(context, "设备指纹与音源缓存已重置", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !refreshingDfid,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.8.dp, ConceptPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.SecurityUpdateGood, contentDescription = null, tint = ConceptPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (refreshingDfid) "刷新中..." else "刷新设备指纹", fontSize = 12.sp, color = ConceptPrimary)
                        }

                        Button(
                            onClick = {
                                diagnosing = true
                                scope.launch {
                                    val t0 = System.currentTimeMillis()
                                    val hotOk = withContext(Dispatchers.IO) {
                                        runCatching { VipManager.music.searchHot().isNotEmpty() }.getOrDefault(false)
                                    }
                                    val latencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                                    diagnosing = false
                                    diagnosticSummary = if (hotOk) {
                                        "网关连通正常 · 响应延迟 ${latencyMs}ms · 概念版签名通道 (appid=3116) 就绪"
                                    } else {
                                        "网关响应超时 (${latencyMs}ms)，建议检查网络或点击左侧刷新设备指纹"
                                    }
                                }
                            },
                            enabled = !diagnosing,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ConceptPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = Color(0xFF061320), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (diagnosing) "测速中..." else "链路测速诊断", fontSize = 12.sp, color = Color(0xFF061320), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 4. 自动领取开关卡片
            if (loggedIn) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171E2E)),
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

                // 5. 刷新与说明按钮栏
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

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}
