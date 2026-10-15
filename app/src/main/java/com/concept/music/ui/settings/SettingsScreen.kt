package com.concept.music.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.data.model.AudioQuality
import com.concept.music.service.AmbientSoundManager
import com.concept.music.service.AudioCacheManager
import com.concept.music.service.SystemLyricBridge
import com.concept.music.service.VipGuardianManager
import com.concept.music.ui.theme.*
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.MusicPlayerViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MusicPlayerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val preferredQuality = uiState.preferredQuality
    val coverStyle = uiState.coverStyle
    val showTranslation = uiState.showTranslation
    val showPhonetic = uiState.showPhonetic
    val lyricFontSize = uiState.lyricFontSize
    val lyricAlignment = uiState.lyricAlignment
    val lyricWordByWord = uiState.lyricWordByWord
    val playbackSpeed = uiState.playbackSpeed
    val playbackPitch = uiState.playbackPitch
    val linkPitchToSpeed = uiState.linkPitchToSpeed
    val sleepTimerRemainingSec = uiState.sleepTimerRemainingSec
    val sleepStopAfterCurrentSong = uiState.sleepStopAfterCurrentSong
    val climaxModeEnabled = uiState.climaxModeEnabled
    val cacheSizeBytes by AudioCacheManager.cacheSizeBytes.collectAsState()
    val pinnedSongs by AudioCacheManager.pinnedSongIds.collectAsState()
    val themeMode by ThemeManager.themeMode.collectAsState()

    // Phase 3 创新模块状态订阅
    val guardianState by VipGuardianManager.state.collectAsState()
    val systemLyricState by SystemLyricBridge.state.collectAsState()
    val ambientState by AmbientSoundManager.state.collectAsState()

    val autoGuardianEnabled = guardianState.enabled
    val guardianStatusText = "${guardianState.lastRunTimeFormatted} · ${guardianState.lastResultSummary}"
    val guardianTodayDone = guardianState.lastRunDay.isNotBlank() && guardianState.lastResultSummary.contains("已激活")
    val floatingLyricEnabled = systemLyricState.floatingCapsuleEnabled
    val statusBarLyricEnabled = systemLyricState.statusBarBroadcastEnabled

    LaunchedEffect(Unit) {
        AudioCacheManager.refreshCacheSize(context)
    }

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "设置与播放偏好",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 140.dp, top = 8.dp)
            ) {
                // 0. [维度4定制要求] 酷狗蓝视觉与双境水彩/幽邃主题系统
                item {
                    SettingsSectionCard(
                        title = "视觉主题与色彩风格",
                        icon = Icons.Rounded.Palette
                    ) {
                        Text(
                            text = "酷狗经典蓝 · 幽邃微蓝（深色） / 水彩浸润（浅色）",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "深色呈现克制幽邃的深海微泛蓝质感；浅色以酷狗蓝为主调，交融水粉青与淡紫粉的水彩晕染效果",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppThemeMode.entries.forEach { mode ->
                                val selected = themeMode == mode
                                val previewBrush = when (mode) {
                                    AppThemeMode.DARK_ABYSS_BLUE -> Brush.horizontalGradient(
                                        listOf(Color(0xFF0A0E17), Color(0xFF142238), Color(0xFF1E3A5F))
                                    )
                                    AppThemeMode.LIGHT_WATERCOLOR -> Brush.horizontalGradient(
                                        listOf(Color(0xFFE3F2FD), Color(0xFFB3E5FC), Color(0xFFEDE7F6))
                                    )
                                    AppThemeMode.SYSTEM -> Brush.horizontalGradient(
                                        listOf(Color(0xFF101826), Color(0xFF2B8CFF), Color(0xFFEAF4FF))
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) ConceptPrimary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    border = BorderStroke(
                                        if (selected) 1.2.dp else 0.6.dp,
                                        if (selected) ConceptPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            ThemeManager.setThemeMode(context, mode)
                                            Toast.makeText(context, "已切换主题：${mode.label}", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(previewBrush)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.label,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (selected) ConceptPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = mode.subtitle,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        RadioButton(
                                            selected = selected,
                                            onClick = { ThemeManager.setThemeMode(context, mode) },
                                            colors = RadioButtonDefaults.colors(selectedColor = ConceptPrimary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. 音质与多态机芯封面样式 ([INNOV-02])
                item {
                    SettingsSectionCard(
                        title = "音质与概念多态机芯",
                        icon = Icons.Rounded.HighQuality
                    ) {
                        Text(
                            text = "默认首选音质档位",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AudioQuality.entries.forEach { quality ->
                                val selected = preferredQuality == quality
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        viewModel.setPreferredQuality(quality)
                                        Toast.makeText(context, "已设置默认音质：${quality.label}", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text(quality.label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = ConceptPrimary
                                    )
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        Text(
                            text = "概念多态播放机芯皮肤 ([INNOV-02])",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "支持流光黑胶、双轮消长透明磁带、内嵌逐字歌词杂志海报、极简圆盘",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CoverStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = coverStyle == style,
                                    onClick = { viewModel.setCoverStyle(style) },
                                    label = { Text(style.label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = ConceptPrimary
                                    )
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // [INNOV-01] 副歌刷刷（高潮速听模式）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🔥 副歌刷刷 · 高潮速听模式 ([INNOV-01])",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "直接切入每首歌 30~45 秒黄金副歌并渐入渐出自动连播，喜欢可一键解锁整首",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = climaxModeEnabled,
                                onCheckedChange = { viewModel.setClimaxMode(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = VipGold)
                            )
                        }
                    }
                }

                // 2. 全自动静默养号守护与概念版小组件 ([INNOV-03])
                item {
                    SettingsSectionCard(
                        title = "全自动静默养号守护与桌面组件",
                        icon = Icons.Rounded.VerifiedUser
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "后台定时全自动续期概念 VIP",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "JobScheduler 每日自动执行青年VIP签到 + VIP畅听补签 + 8首刷歌上报",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoGuardianEnabled,
                                onCheckedChange = { VipGuardianManager.setEnabled(context, it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (guardianTodayDone) ConceptPrimary.copy(alpha = 0.12f) else VipGold.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (guardianTodayDone) "今日守护已达成" else "今日守护待执行",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (guardianTodayDone) ConceptPrimary else VipGold
                                    )
                                    Text(
                                        text = guardianStatusText,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        VipGuardianManager.triggerGuardianNow(context, isManual = true) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("立即守护", color = ConceptPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 提示：可在系统桌面长按添加「酷狗概念版 · 流光唱片与养号小组件」，在桌面直观查看今日 VIP 守护状态与实时逐字歌词。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3. 沉浸式歌词、音译注音与系统级状态栏/悬浮歌词 ([创新点3 + INNOV-05])
                item {
                    SettingsSectionCard(
                        title = "KRC 逐字歌词、音译注音与投射",
                        icon = Icons.Rounded.Lyrics
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("歌词字号大小", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("${lyricFontSize.toInt()} sp", color = ConceptPrimary, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = lyricFontSize,
                            onValueChange = { viewModel.setLyricFontSize(it) },
                            valueRange = 14f..26f,
                            steps = 5,
                            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("歌词对齐排版", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = lyricAlignment == LyricAlignment.CENTER,
                                    onClick = { viewModel.setLyricAlignment(LyricAlignment.CENTER) },
                                    label = { Text("居中对齐", fontSize = 12.sp) }
                                )
                                FilterChip(
                                    selected = lyricAlignment == LyricAlignment.LEFT,
                                    onClick = { viewModel.setLyricAlignment(LyricAlignment.LEFT) },
                                    label = { Text("左对齐", fontSize = 12.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("KRC 字内流光横扫与长音辉光", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "开启后逐字平滑染色，>800ms 长音拖拍字自动叠加呼吸光晕",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = lyricWordByWord,
                                onCheckedChange = { viewModel.setLyricWordByWord(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // [创新点3] 音译注音开关
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("真·音译注音跟唱层（罗马音/粤拼/韩音）", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "解析 KRC 内置 language(type=1) 音译流，在原文上方对齐展示发音注音",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showPhonetic,
                                onCheckedChange = {
                                    if (showPhonetic != it) viewModel.togglePhonetic()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("默认显示双行中文翻译", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "外语歌曲自动展示时间轴对齐的中文译文",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showTranslation,
                                onCheckedChange = {
                                    if (showTranslation != it) viewModel.toggleTranslation()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("全局可拖拽 KRC 逐字悬浮歌词胶囊 ([INNOV-05])", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "在任意页面顶部悬浮展示毛玻璃逐字染色歌词，支持自由拖拽",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = floatingLyricEnabled,
                                onCheckedChange = { SystemLyricBridge.setFloatingCapsuleEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("系统状态栏歌词广播 ([INNOV-05])", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "主动发送 Flyme / HyperOS / EvoX / LyricGetter 状态栏歌词广播",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = statusBarLyricEnabled,
                                onCheckedChange = { SystemLyricBridge.setStatusBarBroadcastEnabled(context, it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                            )
                        }
                    }
                }

                // 4. 倍速播放、睡眠定时器与心流白噪音状态
                item {
                    SettingsSectionCard(
                        title = "倍速变调、睡眠定时与心流白噪音",
                        icon = Icons.Rounded.Speed
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "当前倍速：${String.format(java.util.Locale.getDefault(), "%.2f", playbackSpeed)}x / 音调：${String.format(java.util.Locale.getDefault(), "%.2f", playbackPitch)}x",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            if (playbackSpeed != 1.0f || playbackPitch != 1.0f) {
                                TextButton(onClick = { viewModel.resetSpeedAndPitch() }) {
                                    Text("重置 1.0x", color = ConceptPrimary)
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { preset ->
                                FilterChip(
                                    selected = abs(playbackSpeed - preset) < 0.03f,
                                    onClick = { viewModel.setPlaybackSpeed(preset) },
                                    label = { Text("${preset}x", fontSize = 12.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("黑胶变速联动变调", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = linkPitchToSpeed,
                                onCheckedChange = { viewModel.setLinkPitchToSpeed(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                            )
                        }

                        if (ambientState.masterEnabled && ambientState.hasActiveChannel) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "心流环境音混音舱运行中 ([INNOV-04])",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = ConceptPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                TextButton(onClick = { AmbientSoundManager.setMasterEnabled(false) }) {
                                    Text("一键静音环境音", color = VipGold)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("睡眠定时器", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                val status = when {
                                    sleepTimerRemainingSec > 0L -> "剩余 ${sleepTimerRemainingSec / 60}分${sleepTimerRemainingSec % 60}秒 后自动停止"
                                    sleepStopAfterCurrentSong -> "将在当前歌曲播放完毕后停止"
                                    else -> "未开启（到点前5秒音量平滑渐弱）"
                                }
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (sleepTimerRemainingSec > 0L || sleepStopAfterCurrentSong) ConceptPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (sleepTimerRemainingSec > 0L || sleepStopAfterCurrentSong) {
                                TextButton(onClick = { viewModel.cancelSleepTimer() }) {
                                    Text("关闭", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        viewModel.setSleepTimer(mins, false)
                                        Toast.makeText(context, "已开启 $mins 分钟睡眠定时", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("${mins}分钟", fontSize = 12.sp) }
                                )
                            }
                            FilterChip(
                                selected = sleepStopAfterCurrentSong && sleepTimerRemainingSec == 0L,
                                onClick = {
                                    viewModel.setSleepTimer(0, true)
                                    Toast.makeText(context, "将在当前歌曲播完后自动暂停", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("播完当前曲目", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // 5. 边听边存音频流缓存与固定离线保护 ([维度6])
                item {
                    SettingsSectionCard(
                        title = "边听边存与固定离线空间",
                        icon = Icons.Rounded.Storage
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "已缓存音频流数据（已固定保护 ${pinnedSongs.size} 首）",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Media3 SimpleCache 引擎（上限 1.0 GB，固定离线曲目在清理缓存时自动保留）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = AudioCacheManager.formatSize(cacheSizeBytes),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = VipGold,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    AudioCacheManager.refreshCacheSize(context)
                                    Toast.makeText(context, "缓存统计已刷新", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("刷新占用", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    AudioCacheManager.clearCache(context, keepPinned = true) {
                                        Toast.makeText(
                                            context,
                                            if (pinnedSongs.isNotEmpty()) "已清理普通缓存（已保留 ${pinnedSongs.size} 首固定离线曲目）" else "已清空本地音频流缓存",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ConceptPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("清理非固定缓存", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 6. 关于酷狗概念版第三方
                item {
                    SettingsSectionCard(
                        title = "关于与音频架构",
                        icon = Icons.Rounded.Info
                    ) {
                        Text(
                            text = "酷狗概念版 · 幽邃微蓝 & 水彩浸润定制版 (v4.0 Full Blueprint)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• 视觉系统：幽邃微蓝（深色微泛蓝） + 水彩浸润（酷狗经典蓝 × 水彩晕染浅色）\n" +
                                "• 五大创新：概念胶囊工作台(番茄钟) + AI 情绪漫游队列重排 + 真·音译注音/KTV伴奏实验室 + VIP守护日历热力图&风控沙盒 + 歌词金句黑胶海报生成器",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ConceptPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ConceptPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
