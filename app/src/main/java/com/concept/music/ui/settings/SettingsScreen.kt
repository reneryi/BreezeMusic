package com.concept.music.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import com.concept.music.service.AudioEffectManager
import com.concept.music.service.DesktopLyricColorScheme
import com.concept.music.service.DesktopLyricLineMode
import com.concept.music.service.SystemLyricBridge
import com.concept.music.service.VipGuardianManager
import com.concept.music.ui.theme.*
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.MusicPlayerViewModel
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MusicPlayerViewModel,
    onBackClick: () -> Unit,
    onOpenViperEffect: () -> Unit = {}
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
    val lyricBlurEnabled = uiState.lyricBlurEnabled
    val playbackSpeed = uiState.playbackSpeed
    val playbackPitch = uiState.playbackPitch
    val linkPitchToSpeed = uiState.linkPitchToSpeed
    val sleepTimerRemainingSec = uiState.sleepTimerRemainingSec
    val sleepStopAfterCurrentSong = uiState.sleepStopAfterCurrentSong
    val sleepPendingStopAtTrackEnd = uiState.sleepPendingStopAtTrackEnd
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
    val audioEffectState by AudioEffectManager.state.collectAsState()

    val autoGuardianEnabled = guardianState.enabled
    val guardianStatusText = "${guardianState.lastRunTimeFormatted} · ${guardianState.lastResultSummary}"
    val guardianTodayDone = guardianState.lastRunDay.isNotBlank() && guardianState.lastResultSummary.contains("已激活")
    val desktopLyricEnabled = systemLyricState.floatingCapsuleEnabled
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
                            text = "设置",
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
                // 1. 视觉主题与双封面播放样式
                item {
                    SettingsSectionCard(
                        title = "外观与播放样式",
                        icon = Icons.Rounded.Palette
                    ) {
                        Text(
                            text = "主题色彩方案",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "提供幽邃微蓝深色与水彩漫染浅色两套主题",
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
                                    color = if (selected) ConceptPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
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
                            text = "封面播放页样式",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "精简为重构拟真黑胶封面与静态圆角方形封面",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

                // 2. 音质策略、蝰蛇音效中心与副歌速刷
                item {
                    SettingsSectionCard(
                        title = "音质与蝰蛇声效",
                        icon = Icons.Rounded.HighQuality
                    ) {
                        // 独立蝰蛇音效与专业均衡器入口卡
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ConceptPrimary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .softCardClickable(shape = RoundedCornerShape(16.dp), onClick = onOpenViperEffect)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = ConceptPrimary, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "蝰蛇音效与专业均衡器",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (audioEffectState.isEnabled) "当前已启用：${audioEffectState.currentPreset.label}" else "25款酷狗分类音效 · 详细声学参数 · 10段专业均衡器",
                                            fontSize = 11.sp,
                                            color = if (audioEffectState.isEnabled) ConceptPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ConceptPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "默认在线播放音质",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "优先按所选音质播放，无对应档位时自动择优",
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
                            title = "移动网络省流",
                            subtitle = "移动网络下播 320K，Wi-Fi 下恢复无损 / Hi-Res",
                            checked = wifiOnlyLossless,
                            onCheckedChange = { viewModel.setWifiOnlyLossless(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "智能音量均衡",
                            subtitle = "平衡不同曲目音量差异，避免切歌忽大忽小",
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
                                    text = "切歌淡入淡出",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = if (crossfadeSeconds == 0) "已关闭，保持曲目原始衔接" else "切歌前后 ${crossfadeSeconds} 秒平滑过渡",
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
                            title = "副歌速刷",
                            subtitle = "直达 30~45 秒副歌高潮精华段，支持在播放队列随时开关",
                            checked = climaxModeEnabled,
                            onCheckedChange = { viewModel.setClimaxMode(it) },
                            thumbColor = VipGold
                        )
                    }
                }

                // 3. 硬件外设与智能播放行为
                item {
                    SettingsSectionCard(
                        title = "外设与播放控制",
                        icon = Icons.Rounded.Headphones
                    ) {
                        SettingToggleRow(
                            title = "断开耳机自动暂停",
                            subtitle = "拔出有线耳机或蓝牙断开时立即暂停播放",
                            checked = pauseOnHeadsetDisconnect,
                            onCheckedChange = { viewModel.setPauseOnHeadsetDisconnect(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "启动时自动续播",
                            subtitle = "启动后从上次播放位置继续播放",
                            checked = autoPlayOnStartup,
                            onCheckedChange = { viewModel.setAutoPlayOnStartup(it) }
                        )
                    }
                }

                // 4. 歌词页排版、景深模糊与桌面歌词设置
                item {
                    SettingsSectionCard(
                        title = "歌词页与桌面歌词",
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
                            valueRange = 16f..30f,
                            steps = 6,
                            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("歌词对齐方式", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LyricAlignment.entries.forEach { align ->
                                    FilterChip(
                                        selected = lyricAlignment == align,
                                        onClick = { viewModel.setLyricAlignment(align) },
                                        label = { Text(align.label, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "歌词阶梯景深高斯模糊",
                            subtitle = "以当前唱诵行为视觉焦点，远端歌词呈柔和高斯虚化",
                            checked = lyricBlurEnabled,
                            onCheckedChange = { viewModel.setLyricBlurEnabled(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "KRC 逐字流光歌词",
                            subtitle = "歌词随人声逐字染色，长音自动叠加微光",
                            checked = lyricWordByWord,
                            onCheckedChange = { viewModel.setLyricWordByWord(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "外语音译注音",
                            subtitle = "在日韩粤语歌词上方显示罗马音或粤拼",
                            checked = showPhonetic,
                            onCheckedChange = {
                                if (showPhonetic != it) viewModel.togglePhonetic()
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "外语中文翻译",
                            subtitle = "在外语歌曲下方同步显示中文译文（中文歌自动隐藏）",
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
                            title = "桌面歌词",
                            subtitle = "启用多风格悬浮桌面歌词（支持6套配色、单双行、无框透明度与控播栏）",
                            checked = desktopLyricEnabled,
                            onCheckedChange = { SystemLyricBridge.setFloatingCapsuleEnabled(it) }
                        )

                        if (desktopLyricEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DesktopLyricColorScheme.entries.forEach { scheme ->
                                    val selected = systemLyricState.desktopColorScheme == scheme
                                    FilterChip(
                                        selected = selected,
                                        onClick = { SystemLyricBridge.setDesktopColorScheme(scheme) },
                                        label = { Text(scheme.label, fontSize = 11.sp) }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DesktopLyricLineMode.entries.forEach { mode ->
                                    val selected = systemLyricState.desktopLineMode == mode
                                    FilterChip(
                                        selected = selected,
                                        onClick = { SystemLyricBridge.setDesktopLineMode(mode) },
                                        label = { Text(mode.label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "状态栏歌词",
                            subtitle = "向 Flyme / HyperOS / OriginOS 发送状态栏歌词",
                            checked = statusBarLyricEnabled,
                            onCheckedChange = { SystemLyricBridge.setStatusBarBroadcastEnabled(context, it) }
                        )
                    }
                }

                // 5. 全自动概念版 VIP 权益守护与桌面组件
                item {
                    SettingsSectionCard(
                        title = "VIP 自动守护",
                        icon = Icons.Rounded.VerifiedUser
                    ) {
                        SettingToggleRow(
                            title = "每日自动领取 VIP",
                            subtitle = "自动完成每日签到、VIP 领取与听歌打卡",
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
                    }
                }

                // 6. 倍速变调与带记忆功能的睡眠定时
                item {
                    SettingsSectionCard(
                        title = "倍速与睡眠定时",
                        icon = Icons.Rounded.Speed
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "当前倍速：${String.format(Locale.getDefault(), "%.2f", playbackSpeed)}x · 音调：${String.format(Locale.getDefault(), "%.2f", playbackPitch)}x",
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
                            title = "变速联动变调",
                            subtitle = "音调随倍速同步升降，还原黑胶慢放与快放听感",
                            checked = linkPitchToSpeed,
                            onCheckedChange = { viewModel.setLinkPitchToSpeed(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        val isSleepActive = sleepTimerRemainingSec > 0L || sleepPendingStopAtTrackEnd
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("睡眠定时关闭", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                val status = when {
                                    sleepTimerRemainingSec > 0L -> "剩余 ${sleepTimerRemainingSec / 60}分${sleepTimerRemainingSec % 60}秒 后停止" +
                                        if (sleepStopAfterCurrentSong) "（播完当前歌曲再停）" else ""
                                    sleepPendingStopAtTrackEnd -> "将在当前曲目播放完毕后自动停止"
                                    else -> "未开启定时，结束前音量平滑渐弱"
                                }
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSleepActive) ConceptPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSleepActive) {
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
                                        viewModel.setSleepTimer(mins, sleepStopAfterCurrentSong)
                                        Toast.makeText(
                                            context,
                                            "已开启 $mins 分钟睡眠定时" + if (sleepStopAfterCurrentSong) "（播完当前歌曲再停）" else "",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    label = { Text("${mins}分钟", fontSize = 12.sp) }
                                )
                            }
                            FilterChip(
                                selected = sleepPendingStopAtTrackEnd && sleepTimerRemainingSec == 0L,
                                onClick = {
                                    viewModel.setSleepTimer(0, true)
                                    Toast.makeText(context, "将在当前歌曲播完后自动暂停", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("仅播完当前这首", fontSize = 12.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        SettingToggleRow(
                            title = "播完当前歌曲再停（自动记忆）",
                            subtitle = "倒计时归零后等待当前歌曲完整播完再暂停，设置永久记忆",
                            checked = sleepStopAfterCurrentSong,
                            onCheckedChange = { viewModel.setSleepStopAfterCurrentSongPreference(it) }
                        )
                    }
                }

                // 7. 边听边存缓存与固定离线空间
                item {
                    SettingsSectionCard(
                        title = "存储与音频缓存",
                        icon = Icons.Rounded.Storage
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "本地音频缓存（已固定 ${pinnedSongs.size} 首）",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "已播歌曲自动缓存，清理时保留已固定的离线曲目",
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
                            var cacheRefreshSpin by remember { mutableIntStateOf(0) }
                            OutlinedButton(
                                onClick = {
                                    cacheRefreshSpin += 1
                                    AudioCacheManager.refreshCacheSize(context)
                                    Toast.makeText(context, "缓存统计已更新", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                ConceptFluidRefreshIcon(
                                    tint = ConceptPrimary,
                                    spinTrigger = cacheRefreshSpin,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
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
                        title = "关于微风音乐",
                        icon = Icons.Rounded.Info
                    ) {
                        Text(
                            text = "BreezeMusic · 酷狗概念版第三方客户端",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• 音频引擎：Media3 ExoPlayer · 蝰蛇音效矩阵 · 10段专业均衡器\n" +
                                "• 视觉体验：彗星流光进度条 · 景深高斯模糊歌词 · 120Hz 高刷动效\n" +
                                "• 核心特性：副歌速刷 · 多风格桌面歌词 · 5款高定歌词海报 · VIP自动守护",
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
    val sectionAccent = when {
        title.contains("外观") || title.contains("主题") -> ConceptVioletAccent
        title.contains("音质") -> ConceptMintAccent
        title.contains("外设") -> ConceptCoralAccent
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
