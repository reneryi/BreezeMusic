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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.concept.music.ui.theme.ConceptFluidRefreshIcon
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * 概念版 VIP 权益中心 & 守护沙盒独立全屏页面：
 * - 由原本的底部抽屉升级为独立沉浸式路由页面（消除抽屉占满屏幕且顶部状态栏独立渲染的不协调感）
 * - 完整适配「水彩浸润（浅色）」与「幽邃微蓝（深色）」双主题动态色彩系统，彻底告别写死深色背景
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipCenterScreen(
    state: VipUiState,
    loggedIn: Boolean,
    autoClaim: Boolean,
    onBack: () -> Unit,
    onClaim: () -> Unit,
    onAutoChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit
) {
    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VipCrownBadge(size = 32.dp)
                            Column {
                                Text(
                                    text = "VIP 权益中心 & 守护沙盒",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 17.5.sp
                                    )
                                )
                                Text(
                                    text = "每日免费领 VIP · 无损音质畅听",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        if (loggedIn) {
                            var topRefreshSpin by remember { mutableIntStateOf(0) }
                            IconButton(
                                onClick = {
                                    topRefreshSpin += 1
                                    onRefresh()
                                },
                                enabled = !state.busy
                            ) {
                                ConceptFluidRefreshIcon(
                                    tint = ConceptPrimary,
                                    isRefreshing = state.busy,
                                    spinTrigger = topRefreshSpin,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            VipCenterContentBody(
                state = state,
                loggedIn = loggedIn,
                autoClaim = autoClaim,
                onClaim = onClaim,
                onAutoChange = onAutoChange,
                onRefresh = onRefresh,
                onLogin = onLogin,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(top = 6.dp, bottom = 112.dp)
            )
        }
    }
}

/**
 * 兼容保留的抽屉封装（内部同样全面接入亮/暗自适应主题色与全屏沉浸式 WindowInsets）
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
        windowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = conceptSheetContainerColor(),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        VipCenterContentBody(
            state = state,
            loggedIn = loggedIn,
            autoClaim = autoClaim,
            onClaim = onClaim,
            onAutoChange = onAutoChange,
            onRefresh = onRefresh,
            onLogin = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun VipCenterContentBody(
    state: VipUiState,
    loggedIn: Boolean,
    autoClaim: Boolean,
    onClaim: () -> Unit,
    onAutoChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalIsDarkTheme.current
    val cardBgColor = conceptFloatingCardColor()
    val elevatedColor = conceptElevatedSurfaceColor()
    val goldAccent = if (isDark) Color(0xFFFFD54F) else Color(0xFFC68400)
    val greenSuccess = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

    var currentDfid by remember { mutableStateOf(runCatching { VipManager.device().dfid }.getOrDefault("-")) }
    var refreshingDfid by remember { mutableStateOf(false) }
    var diagnosing by remember { mutableStateOf(false) }
    var diagnosticSummary by remember { mutableStateOf<String?>(null) }

    val vipHeroBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF2C2419),
                Color(0xFF1A1E2D),
                Color(0xFF131926)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFF4D9),
                Color(0xFFFFF9EB),
                Color(0xFFF2F7FF)
            )
        )
    }
    val heroPrimaryText = if (isDark) Color.White else Color(0xFF231A0B)
    val heroSubText = if (isDark) Color.White.copy(alpha = 0.76f) else Color(0xFF5C4722)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 概念版 VIP 会员核心权益卡（自适应深色黑金 / 浅色香槟暖金水彩）
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            shadowElevation = 0.dp,
            border = BorderStroke(
                0.8.dp,
                if (isDark) goldAccent.copy(alpha = 0.26f) else goldAccent.copy(alpha = 0.22f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(vipHeroBrush)
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
                                color = goldAccent,
                                fontSize = 18.sp
                            )
                        )

                        if (loggedIn && state.snapshot.checked) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = goldAccent.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = if (state.fresh) "云端已同步" else "待刷新",
                                    color = goldAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                                )
                            }
                        }
                    }

                    if (!loggedIn) {
                        Text(
                            text = "登录酷狗账号后，可每天一键免费领取 1 天专属 VIP 权益，畅听全网无损曲库！",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = heroSubText,
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
                                color = heroPrimaryText,
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        if (state.snapshot.benefits.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                state.snapshot.benefits.forEach { benefit ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = goldAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = benefit.display(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = heroSubText,
                                                fontWeight = FontWeight.Medium
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
                            color = goldAccent,
                            trackColor = goldAccent.copy(alpha = 0.15f)
                        )
                    }

                    state.result?.let {
                        Text(
                            text = it.message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = greenSuccess,
                                fontWeight = FontWeight.Bold
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

                    Button(
                        onClick = { if (loggedIn) onClaim() else onLogin() },
                        enabled = !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFCA28),
                            contentColor = Color(0xFF281E0B),
                            disabledContainerColor = Color(0xFFFFCA28).copy(alpha = 0.4f),
                            disabledContentColor = Color(0xFF281E0B).copy(alpha = 0.4f)
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
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 2. 月度 VIP 守护日历热力图 (自适应明暗主题悬浮卡片)
        val currentDate = remember { LocalDate.now(CHINA_ZONE) }
        val currentYearMonth = remember(currentDate) { YearMonth.from(currentDate) }
        val monthPrefix = remember(currentYearMonth) { currentYearMonth.toString() }
        val claimedDaysMap = remember(state.records, monthPrefix) {
            state.records
                .filter { it.day.startsWith(monthPrefix) && it.received }
                .associateBy({ it.day.takeLast(2).toIntOrNull() ?: 0 }, { it.type })
        }
        val claimedCountThisMonth = claimedDaysMap.size

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardBgColor,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${currentYearMonth.year}年${currentYearMonth.monthValue}月 · VIP 守护日历热力图",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "本月已打卡领取 $claimedCountThisMonth 天 · 累计相当节省 ¥${String.format(java.util.Locale.getDefault(), "%.1f", claimedCountThisMonth * 0.6)} 会员费",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = goldAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Surface(
                        color = ConceptPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "$claimedCountThisMonth / ${currentYearMonth.lengthOfMonth()} 天",
                            color = ConceptPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

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
                                        "svip" -> VipGold.copy(alpha = if (isDark) 0.28f else 0.22f)
                                        "tvip" -> ConceptPrimary.copy(alpha = if (isDark) 0.24f else 0.18f)
                                        else -> if (claimType != null) {
                                            ConceptPrimary.copy(alpha = if (isDark) 0.24f else 0.18f)
                                        } else {
                                            elevatedColor
                                        }
                                    }
                                    val borderColor = when {
                                        isToday -> ConceptCyanAccent
                                        claimType == "svip" -> goldAccent.copy(alpha = 0.7f)
                                        claimType != null -> ConceptPrimary.copy(alpha = 0.6f)
                                        else -> Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(cellBg)
                                            .border(
                                                width = if (borderColor != Color.Transparent) 1.2.dp else 0.dp,
                                                color = borderColor,
                                                shape = RoundedCornerShape(9.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            fontSize = 11.5.sp,
                                            fontWeight = if (claimType != null || isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = when {
                                                claimType == "svip" -> goldAccent
                                                claimType != null -> ConceptPrimary
                                                isToday -> ConceptCyanAccent
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                            },
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).height(34.dp))
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(color = goldAccent, label = "SVIP 概念升级")
                    LegendDot(color = ConceptPrimary, label = "畅听 VIP 已领")
                    LegendDot(color = ConceptCyanAccent, label = "今日焦点")
                }
            }
        }

        // 3. 风控自愈与音源链路诊断沙盒（自适应主题色）
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardBgColor,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "风控自愈与音源链路诊断沙盒",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val dfidShort = if (currentDfid.length > 12) "${currentDfid.take(6)}...${currentDfid.takeLast(4)}" else currentDfid
                    Text(
                        text = "当前设备 dfid：$dfidShort · 遇 20028 风控或无损降级可一键自愈",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                diagnosticSummary?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ConceptPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = if (isDark) ConceptCyanAccent else ConceptPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ConceptPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.SecurityUpdateGood, contentDescription = null, tint = ConceptPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (refreshingDfid) "刷新中..." else "刷新设备指纹", fontSize = 12.sp, color = ConceptPrimary, fontWeight = FontWeight.Bold)
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
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ConceptPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (diagnosing) "测速中..." else "链路测速诊断", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. 自动领取开关卡片
        if (loggedIn) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBgColor,
                shadowElevation = 0.dp,
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
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "每日启动时自动复核并领取 1 天 VIP · 仅当前账号生效",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Switch(
                        checked = autoClaim,
                        onCheckedChange = onAutoChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ConceptPrimary
                        )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "酷狗概念版官方每日赠送权益",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                var bottomRefreshSpin by remember { mutableIntStateOf(0) }
                TextButton(
                    onClick = {
                        bottomRefreshSpin += 1
                        onRefresh()
                    },
                    enabled = !state.busy,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    ConceptFluidRefreshIcon(
                        tint = ConceptPrimary,
                        isRefreshing = state.busy,
                        spinTrigger = bottomRefreshSpin,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "刷新状态",
                        color = ConceptPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
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
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
