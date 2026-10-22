package com.concept.music.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.data.kugou.CHINA_ZONE
import com.concept.music.data.kugou.DeviceIdentity
import com.concept.music.data.kugou.VipUiState
import com.concept.music.data.kugou.today
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptFluidRefreshIcon
import com.concept.music.ui.theme.ConceptMintAccent
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
 * 概念版 VIP 权益中心页面：
 * - 移除底部重复的「刷新状态」按钮，仅保留右上角刷新图标；
 * - 精简页面小字说明文案，使视觉层级更清爽自然。
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
                    modifier = Modifier.statusBarsPadding(),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VipCrownBadge(size = 30.dp)
                            Text(
                                text = "VIP 权益中心",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp
                                )
                            )
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
                onLogin = onLogin,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(top = 6.dp, bottom = 80.dp)
            )
        }
    }
}

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

    var currentDevice by remember {
        mutableStateOf(
            runCatching { VipManager.device() }.getOrElse {
                DeviceIdentity(guid = "-", mid = "-", dev = "-", dfid = "-")
            }
        )
    }
    var showFullDeviceIds by remember { mutableStateOf(false) }
    var refreshingDfid by remember { mutableStateOf(false) }
    var diagnosing by remember { mutableStateOf(false) }
    var diagnosticSummary by remember { mutableStateOf<String?>(null) }

    // 进入页面时若设备标识尚未初始化，自动注册并激活概念版 DFID 凭证
    LaunchedEffect(Unit) {
        if (currentDevice.dfid.isBlank() || currentDevice.dfid == "-") {
            withContext(Dispatchers.IO) {
                runCatching { VipManager.auth.registerDevice(forceRefresh = false) }
            }
            currentDevice = runCatching { VipManager.device() }.getOrDefault(currentDevice)
        }
    }

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
        // 1. 概念版 VIP 会员核心权益卡
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            shadowElevation = 0.dp,
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
                                    text = if (state.fresh) "已同步" else "待刷新",
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
                            text = "登录账号后，每日可免费领取 1 天 VIP 权益。",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = heroSubText,
                                lineHeight = 20.sp
                            )
                        )
                    } else {
                        val todayRecord = state.records.find { it.day == today() }
                        val recordDesc = when {
                            todayRecord?.received != true -> if (state.fresh) "今日尚未领取" else "正在同步状态"
                            todayRecord.type == "svip" -> "今日已领：概念版 VIP"
                            else -> "今日已领：畅听 VIP"
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
                            text = if (loggedIn) "一键领取今日 VIP" else "立即登录领取",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 2. 月度 VIP 打卡日历
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
                    Text(
                        text = "${currentYearMonth.year}年${currentYearMonth.monthValue}月 · 领取日历",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Surface(
                        color = ConceptPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "已领 $claimedCountThisMonth / ${currentYearMonth.lengthOfMonth()} 天",
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
                                    val cellBg = when {
                                        claimType == "svip" -> VipGold.copy(alpha = if (isDark) 0.32f else 0.24f)
                                        claimType != null -> ConceptPrimary.copy(alpha = if (isDark) 0.28f else 0.20f)
                                        isToday -> ConceptCyanAccent.copy(alpha = if (isDark) 0.24f else 0.16f)
                                        else -> elevatedColor
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(cellBg),
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
                    LegendDot(color = goldAccent, label = "概念 VIP")
                    LegendDot(color = ConceptPrimary, label = "畅听 VIP")
                    LegendDot(color = ConceptCyanAccent, label = "今日")
                }
            }
        }

        // 3. 自动领取开关卡片（唯一入口，已移除设置页中的重复项与底部重复刷新按钮）
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
                            text = "启动时自动领取当日 1 天 VIP 权益",
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
        }

        // 4. 设备标识与音源鉴权凭证（深度重构：真实展示/复制 DFID/MID/GUID、支持强制重签与多节点链路诊断）
        val isDfidHealthy = currentDevice.dfid.isNotBlank() && currentDevice.dfid != "-"
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ConceptPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = null,
                                tint = ConceptPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "设备标识与音源鉴权凭证",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "酷狗概念版协议 · AppID 3116 / Ver 11440",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (isDfidHealthy) ConceptMintAccent.copy(alpha = 0.16f) else VipGold.copy(alpha = 0.18f)
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { showFullDeviceIds = !showFullDeviceIds }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isDfidHealthy) ConceptMintAccent else VipGold)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (showFullDeviceIds) "收起明文" else if (isDfidHealthy) "凭证有效" else "待激活",
                                color = if (isDfidHealthy) ConceptMintAccent else goldAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 设备标识核心参数矩阵（点击任意一行即可复制完整凭证）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(elevatedColor.copy(alpha = 0.65f))
                        .border(0.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeviceCredentialItemRow(
                        label = "DFID 指纹",
                        rawValue = currentDevice.dfid,
                        showFull = showFullDeviceIds,
                        accentColor = ConceptPrimary,
                        onCopy = { copyDeviceValue(context, "DFID 设备指纹", currentDevice.dfid) }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    DeviceCredentialItemRow(
                        label = "MID 终端码",
                        rawValue = currentDevice.mid,
                        showFull = showFullDeviceIds,
                        accentColor = ConceptCyanAccent,
                        onCopy = { copyDeviceValue(context, "MID 终端码", currentDevice.mid) }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    DeviceCredentialItemRow(
                        label = "GUID 标识",
                        rawValue = currentDevice.guid,
                        showFull = showFullDeviceIds,
                        accentColor = goldAccent,
                        onCopy = { copyDeviceValue(context, "GUID 会话标识", currentDevice.guid) }
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
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            refreshingDfid = true
                            scope.launch {
                                val updatedDevice = withContext(Dispatchers.IO) {
                                    runCatching {
                                        VipManager.music.clearPlaybackCache()
                                        VipManager.auth.registerDevice(forceRefresh = true)
                                        VipManager.device()
                                    }.getOrNull()
                                }
                                refreshingDfid = false
                                if (updatedDevice != null) {
                                    currentDevice = updatedDevice
                                }
                                val dfidTail = currentDevice.dfid.takeLast(6)
                                diagnosticSummary = "已重签概念版设备标识（尾号 $dfidTail）并清空音源URL缓存"
                                Toast.makeText(context, "设备标识已更新", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !refreshingDfid,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ConceptPrimary.copy(alpha = 0.15f),
                            contentColor = ConceptPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Rounded.SecurityUpdateGood,
                            contentDescription = null,
                            tint = ConceptPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (refreshingDfid) "重签中..." else "刷新设备标识",
                            fontSize = 12.sp,
                            color = ConceptPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            diagnosing = true
                            scope.launch {
                                val t0 = System.currentTimeMillis()
                                val diagResult = withContext(Dispatchers.IO) {
                                    val ensuredDfid: String = runCatching {
                                        VipManager.auth.registerDevice(forceRefresh = false)
                                    }.getOrNull() ?: currentDevice.dfid
                                    val hotOk = runCatching { VipManager.music.searchHot().isNotEmpty() }.getOrDefault(false)
                                    val latestDev = runCatching { VipManager.device() }.getOrDefault(currentDevice)
                                    Triple(ensuredDfid, hotOk, latestDev)
                                }
                                val latencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                                currentDevice = diagResult.third
                                diagnosing = false
                                val dfidValid = !diagResult.first.isNullOrBlank() && diagResult.first != "-"
                                diagnosticSummary = if (diagResult.second && dfidValid) {
                                    "概念版网关连通正常 · 延迟 ${latencyMs}ms · DFID 鉴权已就绪"
                                } else if (dfidValid) {
                                    "本地 DFID 凭证已就绪 · 网络响应耗时 ${latencyMs}ms，请检查网络环境"
                                } else {
                                    "响应超时 (${latencyMs}ms)，请点击左侧「刷新设备标识」重试"
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
                        Text(if (diagnosing) "诊断中..." else "音源链路诊断", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCredentialItemRow(
    label: String,
    rawValue: String,
    showFull: Boolean,
    accentColor: Color,
    onCopy: () -> Unit
) {
    val formattedValue = remember(rawValue, showFull) {
        val clean = rawValue.trim()
        if (clean.isBlank() || clean == "-") {
            "正在激活..."
        } else if (showFull || clean.length <= 16) {
            clean
        } else {
            "${clean.take(8)}••••${clean.takeLast(6)}"
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onCopy)
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = formattedValue,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = "复制",
                tint = accentColor.copy(alpha = 0.8f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

private fun copyDeviceValue(context: Context, label: String, value: String) {
    if (value.isBlank() || value == "-") {
        Toast.makeText(context, "$label 尚未生成，请点击刷新设备标识", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "已复制 $label", Toast.LENGTH_SHORT).show()
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
