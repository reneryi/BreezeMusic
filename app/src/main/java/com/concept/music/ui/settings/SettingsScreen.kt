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
    val crossfadeSeconds = uiState.crossfadeSeconds
    val volumeNormalization = uiState.volumeNormalization
    val wifiOnlyLossless = uiState.wifiOnlyLossless
    val pauseOnHeadsetDisconnect = uiState.pauseOnHeadsetDisconnect
    val autoPlayOnStartup = uiState.autoPlayOnStartup

    val cacheSizeBytes by AudioCacheManager.cacheSizeBytes.collectAsState()
    val pinnedSongs by AudioCacheManager.pinnedSongIds.collectAsState()
    val themeMode by ThemeManager.themeMode.collectAsState()

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
                // 1. 视觉主题与色彩风格
                item {
                    SettingsSectionCard(
                        title = "外观主题与视觉风格",
                        icon = Icons.Rounded.Palette
                    ) {
                        Text(
                            text = "主题色彩方案",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "深色模式采用静谧克制的深海微泛蓝基调；浅色模式以酷狗经典蓝为主色，融合水彩晕染质感",
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

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        Text(
                            text = "播放页机芯皮肤",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "自定义全屏播放页的主视觉形态，支持流光黑胶、透明机械磁带、杂志海报与极简圆盘",
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
                    }
                }

                // 2. 音质策略与无缝播放体验（现代音乐 App 专业设置）
                item {
                    SettingsSectionCard(
                        title = "音质标准与无缝听感",
                        icon = Icons.Rounded.HighQuality
                    ) {
                        Text(
                            text = "默认在线播放音质",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "若歌曲未收录所选最高档位，将自动平滑匹配该曲目可用的最高音质",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                        SettingToggleRow(
                            title = "移动网络智能省流保护",
                            subtitle = "使用蜂窝移动数据时自动切换为高品 320K，连接 Wi-Fi 时恢复无损 / Hi-Res",
                            checked = wifiOnlyLossless,
                            onCheckedChange = { viewModel.setWifiOnlyLossless(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "跨曲目智能响度均衡 (ReplayGain)",
                            subtitle = "自动平衡不同年代、不同专辑录音的音量落差，避免切歌时音量忽大忽小",
                            checked = volumeNormalization,
                            onCheckedChange = { viewModel.setVolumeNormalization(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "跨曲目淡入淡出过渡 (Crossfade)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = if (crossfadeSeconds == 0) "已关闭（曲目间保持自然原版间隙）" else "切歌前后 ${crossfadeSeconds} 秒平滑交叉混音过渡",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (crossfadeSeconds == 0) "关闭" else "${crossfadeSeconds} 秒",
                                color = ConceptPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Slider(
                            value = crossfadeSeconds.toFloat(),
                            onValueChange = { viewModel.setCrossfadeSeconds(it.toInt()) },
                            valueRange = 0f..6f,
                            steps = 5,
                            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        SettingToggleRow(
                            title = "副歌刷刷 · 黄金高潮速听",
                            subtitle = "直接切入每首歌 30~45 秒高潮段落并渐入渐出连播，遇到心仪歌曲可一键解锁整首",
                            checked = climaxModeEnabled,
                            onCheckedChange = { viewModel.setClimaxMode(it) },
                            thumbColor = VipGold
                        )
                    }
                }

                // 3. 硬件外设与智能播放行为
                item {
                    SettingsSectionCard(
                        title = "耳机外设与智能播放交互",
                        icon = Icons.Rounded.Headphones
                    ) {
                        SettingToggleRow(
                            title = "拔出耳机或断开蓝牙时自动暂停",
                            subtitle = "当有线耳机拔出或蓝牙音频设备断开时立即暂停音乐，防止公共场合外放尴尬",
                            checked = pauseOnHeadsetDisconnect,
                            onCheckedChange = { viewModel.setPauseOnHeadsetDisconnect(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "启动应用时自动恢复播放",
                            subtitle = "打开微风音乐时自动从上次退出的播放队列与毫秒级进度处继续播放",
                            checked = autoPlayOnStartup,
                            onCheckedChange = { viewModel.setAutoPlayOnStartup(it) }
                        )
                    }
                }

                // 4. 歌词排版、音译注音与系统级歌词投射
                item {
                    SettingsSectionCard(
                        title = "歌词排版、音译注音与系统投射",
                        icon = Icons.Rounded.Lyrics
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("全屏歌词字号", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
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
                            Text("歌词对齐方式", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
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

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "KRC 逐字流光染色与长音辉光",
                            subtitle = "开启后歌词随人声逐字平滑染色，超过 800ms 的长音拖拍字自动叠加呼吸光晕",
                            checked = lyricWordByWord,
                            onCheckedChange = { viewModel.setLyricWordByWord(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "外语歌曲音译注音（罗马音 / 粤拼 / 韩音）",
                            subtitle = "解析 KRC 内置音译轨道，在日韩粤语歌词上方对齐展示辅助跟唱注音",
                            checked = showPhonetic,
                            onCheckedChange = {
                                if (showPhonetic != it) viewModel.togglePhonetic()
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "外语歌曲双行中文翻译",
                            subtitle = "在原文歌词下方同步展示时间轴对齐的中文译文",
                            checked = showTranslation,
                            onCheckedChange = {
                                if (showTranslation != it) viewModel.toggleTranslation()
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        SettingToggleRow(
                            title = "应用内全局悬浮逐字歌词胶囊",
                            subtitle = "在浏览歌单、搜索或设置页时，于顶部悬浮展示可自由拖拽的毛玻璃逐字歌词",
                            checked = floatingLyricEnabled,
                            onCheckedChange = { SystemLyricBridge.setFloatingCapsuleEnabled(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "系统状态栏歌词广播",
                            subtitle = "兼容 Flyme / HyperOS / OriginOS / LyricGetter 状态栏歌词协议",
                            checked = statusBarLyricEnabled,
                            onCheckedChange = { SystemLyricBridge.setStatusBarBroadcastEnabled(context, it) }
                        )
                    }
                }

                // 5. 全自动概念版 VIP 权益守护与桌面组件
                item {
                    SettingsSectionCard(
                        title = "概念版 VIP 自动续期与桌面组件",
                        icon = Icons.Rounded.VerifiedUser
                    ) {
                        SettingToggleRow(
                            title = "每日后台自动续期概念版 VIP",
                            subtitle = "定时执行概念版每日签到、畅听 VIP 领取、SVIP 升级与 8 首听歌打卡上报",
                            checked = autoGuardianEnabled,
                            onCheckedChange = { VipGuardianManager.setEnabled(context, it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

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
                                        text = if (guardianTodayDone) "今日 VIP 守护已完成" else "今日 VIP 守护待执行",
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
                                    Text("立即执行", color = ConceptPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "提示：可在手机桌面长按空白处添加「微风音乐 · 流光黑胶与 VIP 守护小组件」，随时查看权益状态与控制播放。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 6. 倍速变调、睡眠定时与白噪音混音
                item {
                    SettingsSectionCard(
                        title = "倍速变调、睡眠定时与白噪音",
                        icon = Icons.Rounded.Speed
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "当前倍速：${String.format(java.util.Locale.getDefault(), "%.2f", playbackSpeed)}x · 音调：${String.format(java.util.Locale.getDefault(), "%.2f", playbackPitch)}x",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            if (playbackSpeed != 1.0f || playbackPitch != 1.0f) {
                                TextButton(onClick = { viewModel.resetSpeedAndPitch() }) {
                                    Text("恢复原速", color = ConceptPrimary)
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

                        Spacer(modifier = Modifier.height(8.dp))

                        SettingToggleRow(
                            title = "黑胶变速联动变调模式",
                            subtitle = "开启后音调随播放倍速同步升降，模拟物理黑胶唱机慢放/快放的复古听感",
                            checked = linkPitchToSpeed,
                            onCheckedChange = { viewModel.setLinkPitchToSpeed(it) }
                        )

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
                                    text = "环境白噪音混音舱正在运行",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = ConceptPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                TextButton(onClick = { AmbientSoundManager.setMasterEnabled(false) }) {
                                    Text("关闭环境音", color = VipGold)
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
                                Text("睡眠定时关闭", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                val status = when {
                                    sleepTimerRemainingSec > 0L -> "剩余 ${sleepTimerRemainingSec / 60}分${sleepTimerRemainingSec % 60}秒 后自动停止"
                                    sleepStopAfterCurrentSong -> "将在当前曲目播放完毕后停止"
                                    else -> "未开启（倒计时结束前 5 秒音量平滑渐弱）"
                                }
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (sleepTimerRemainingSec > 0L || sleepStopAfterCurrentSong) ConceptPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (sleepTimerRemainingSec > 0L || sleepStopAfterCurrentSong) {
                                TextButton(onClick = { viewModel.cancelSleepTimer() }) {
                                    Text("取消定时", color = MaterialTheme.colorScheme.error)
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

                // 7. 边听边存缓存与固定离线空间
                item {
                    SettingsSectionCard(
                        title = "边听边存与离线缓存空间",
                        icon = Icons.Rounded.Storage
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "本地音频缓存占用（已固定保护 ${pinnedSongs.size} 首）",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "播放过的歌曲自动落盘缓存，支持断网秒播；标记为「固定离线收藏」的歌曲在清理缓存时自动保留",
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
                                    Toast.makeText(context, "缓存统计已更新", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("刷新统计", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    AudioCacheManager.clearCache(context, keepPinned = true) {
                                        Toast.makeText(
                                            context,
                                            if (pinnedSongs.isNotEmpty()) "已清理普通缓存（已保留 ${pinnedSongs.size} 首固定离线曲目）" else "已清空本地音频缓存",
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
                                Text("清理普通缓存", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 8. 关于应用
                item {
                    SettingsSectionCard(
                        title = "关于微风音乐 (BreezeMusic)",
                        icon = Icons.Rounded.Info
                    ) {
                        Text(
                            text = "BreezeMusic · 酷狗概念版高定客户端 (v5.0)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• 音频架构：Media3 ExoPlayer + 32MB 大分片边听边存 + 硬件级 5 段均衡器 / 空间混响\n" +
                                "• 视觉设计：幽邃微蓝（深色）与水彩浸润（浅色）双境主题系统，支持 120Hz 硬件着色器缓存\n" +
                                "• 特色工坊：心流番茄工作台 · AI 情绪漫游重排 · KRC 音译/伴奏跟唱 · VIP 守护日历与风控自愈沙盒 · 黑胶金句海报",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    thumbColor: Color = ConceptPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = thumbColor)
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val sectionAccent = when {
        title.contains("外观") || title.contains("主题") -> ConceptVioletAccent
        title.contains("音质") -> ConceptMintAccent
        title.contains("耳机") -> ConceptCoralAccent
        title.contains("歌词") -> ConceptCyanAccent
        title.contains("VIP") -> VipGold
        title.contains("缓存") -> ConceptAmberAccent
        else -> ConceptPrimary
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = conceptFloatingCardColor(),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(16.dp)
                        .clip(CircleShape)
                        .background(sectionAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = sectionAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
