package com.concept.music.ui.settings

import android.widget.Toast
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
import com.concept.music.service.AudioCacheManager
import com.concept.music.service.AudioEffectManager
import com.concept.music.service.SystemLyricBridge
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import com.concept.music.service.DesktopLyricColorScheme
import com.concept.music.service.StatusBarHorizontalAnchor
import com.concept.music.service.StatusBarLyricMode
import com.concept.music.service.StatusBarOverlayStyle
import com.concept.music.ui.theme.*
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.MusicPlayerViewModel
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MusicPlayerViewModel,
    onBackClick: () -> Unit,
    onOpenViperEffect: () -> Unit = {},
    onOpenStatusBarLyricSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val preferredQuality = uiState.preferredQuality
    val coverStyle = uiState.coverStyle
    val playerBackgroundStyle = uiState.playerBackgroundStyle
    val dynamicCoverAccentEnabled = uiState.dynamicCoverAccentEnabled
    val lyricFontSize = uiState.lyricFontSize
    val lyricTranslationScale = uiState.lyricTranslationScale
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
    val smoothPlayPauseFade = uiState.smoothPlayPauseFade
    val motionOrientationPermissionEnabled = uiState.motionOrientationPermissionEnabled

    val cacheSizeBytes by AudioCacheManager.cacheSizeBytes.collectAsState()
    val themeMode by ThemeManager.themeMode.collectAsState()

    val systemLyricState by SystemLyricBridge.state.collectAsState()
    val audioEffectState by AudioEffectManager.state.collectAsState()
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
                // 1. 外观与播放样式
                item {
                    SettingsSectionCard(
                        title = "外观与播放样式",
                        icon = Icons.Rounded.Palette
                    ) {
                        Text(
                            text = "主题色彩",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

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
                                    contentColor = MaterialTheme.colorScheme.onSurface,
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
                                                .size(36.dp)
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
                            text = "封面样式",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
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

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "播放页背景效果",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PlayerBackgroundStyle.entries.forEach { bgStyle ->
                                FilterChip(
                                    selected = playerBackgroundStyle == bgStyle,
                                    onClick = { viewModel.setPlayerBackgroundStyle(bgStyle) },
                                    label = { Text(bgStyle.label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = ConceptPrimary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "封面智能动态取色",
                            subtitle = "提取封面主色渲染播放键与进度条",
                            checked = dynamicCoverAccentEnabled,
                            onCheckedChange = { viewModel.setDynamicCoverAccentEnabled(it) }
                        )
                    }
                }

                // 2. 音质与蝰蛇音效
                item {
                    SettingsSectionCard(
                        title = "音质与音效",
                        icon = Icons.Rounded.HighQuality
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ConceptPrimary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
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
                                            text = "蝰蛇音效与调音台",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (audioEffectState.isEnabled) "已启用：${audioEffectState.currentPreset.label}" else "声效库 · 声场调音 · 10段均衡器",
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
                                        Toast.makeText(context, "默认音质：${quality.label}", Toast.LENGTH_SHORT).show()
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
                            subtitle = "移动网络下播放 320K 高品，Wi-Fi 下恢复无损",
                            checked = wifiOnlyLossless,
                            onCheckedChange = { viewModel.setWifiOnlyLossless(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "音量均衡",
                            subtitle = "平衡不同歌曲间的音量差异",
                            checked = volumeNormalization,
                            onCheckedChange = { viewModel.setVolumeNormalization(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "夜间小音量等响度补偿",
                            subtitle = "低音量聆听时自动补偿低频与高频细节",
                            checked = audioEffectState.equalLoudnessCompensation,
                            onCheckedChange = { AudioEffectManager.setEqualLoudnessCompensation(it) }
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
                                    text = if (crossfadeSeconds == 0) "已关闭" else "切歌时 ${crossfadeSeconds} 秒平滑过渡",
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
                            subtitle = "直达歌曲副歌精华段落",
                            checked = climaxModeEnabled,
                            onCheckedChange = { viewModel.setClimaxMode(it) },
                            thumbColor = VipGold
                        )
                    }
                }

                // 3. 外设与播放控制
                item {
                    SettingsSectionCard(
                        title = "外设与播放控制",
                        icon = Icons.Rounded.Headphones
                    ) {
                        SettingToggleRow(
                            title = "断开耳机自动暂停",
                            subtitle = "拔出有线耳机或蓝牙断开时暂停播放",
                            checked = pauseOnHeadsetDisconnect,
                            onCheckedChange = { viewModel.setPauseOnHeadsetDisconnect(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "启动时自动续播",
                            subtitle = "打开应用后自动恢复上次播放",
                            checked = autoPlayOnStartup,
                            onCheckedChange = { viewModel.setAutoPlayOnStartup(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "播放/暂停音量渐入渐出",
                            subtitle = "点击播放或暂停时平滑过渡音量",
                            checked = smoothPlayPauseFade,
                            onCheckedChange = { viewModel.setSmoothPlayPauseFade(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "横放手机自动进入横屏播放",
                            subtitle = "需开启系统自动旋转，仅在封面播放页生效",
                            checked = motionOrientationPermissionEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setMotionOrientationPermissionEnabled(enabled)
                            }
                        )
                    }
                }

                // 4. 歌词显示设置
                item {
                    SettingsSectionCard(
                        title = "歌词显示设置",
                        icon = Icons.Rounded.Lyrics
                    ) {
                        // 状态栏歌词与灵动岛二级页面入口
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ConceptCyanAccent.copy(alpha = 0.13f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .softCardClickable(shape = RoundedCornerShape(16.dp), onClick = onOpenStatusBarLyricSettings)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        tint = ConceptCyanAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "状态栏歌词与灵动岛",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (statusBarLyricEnabled) {
                                                "已开启 · ${systemLyricState.statusBarMode.label} (${systemLyricState.detectedRom.displayName})"
                                            } else {
                                                "点击进入二级设置 · 适配椒盐状态栏悬浮歌词 / 灵动岛 / Flyme"
                                            },
                                            fontSize = 11.sp,
                                            color = if (statusBarLyricEnabled) ConceptCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ConceptCyanAccent)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

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
                            valueRange = 18f..34f,
                            steps = 7,
                            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("译文/注音字号比例", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("${(lyricTranslationScale * 100).roundToInt()}%", color = ConceptPrimary, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = lyricTranslationScale,
                            onValueChange = { viewModel.setLyricTranslationScale(it) },
                            valueRange = 0.65f..0.95f,
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
                            title = "阶梯景深模糊",
                            subtitle = "非当前唱诵行随距离自然虚化",
                            checked = lyricBlurEnabled,
                            onCheckedChange = { viewModel.setLyricBlurEnabled(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingToggleRow(
                            title = "KRC 逐字流光染色",
                            subtitle = "歌词随人声逐字高亮扫光",
                            checked = lyricWordByWord,
                            onCheckedChange = { viewModel.setLyricWordByWord(it) }
                        )
                    }
                }

                // 5. 倍速与睡眠定时
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
                                text = "倍速 ${String.format(Locale.getDefault(), "%.2f", playbackSpeed)}x · 音调 ${String.format(Locale.getDefault(), "%.2f", playbackPitch)}x",
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
                            subtitle = "音调随倍速同步升降",
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
                                Text("睡眠定时", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                val status = when {
                                    sleepTimerRemainingSec > 0L -> "剩余 ${sleepTimerRemainingSec / 60}分${sleepTimerRemainingSec % 60}秒"
                                    sleepPendingStopAtTrackEnd -> "播完当前歌曲后停止"
                                    else -> "未开启"
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
                                val targetSec = mins * 60L
                                val isPresetActive = sleepTimerRemainingSec in (targetSec - 14 * 60L).coerceAtLeast(1L)..targetSec
                                FilterChip(
                                    selected = isPresetActive,
                                    onClick = {
                                        viewModel.setSleepTimer(mins, sleepStopAfterCurrentSong)
                                        Toast.makeText(context, "已设置 $mins 分钟后停止", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("${mins}分钟", fontSize = 12.sp) }
                                )
                            }
                            FilterChip(
                                selected = sleepPendingStopAtTrackEnd && sleepTimerRemainingSec == 0L,
                                onClick = {
                                    viewModel.setSleepTimer(0, true)
                                    Toast.makeText(context, "将在当前歌曲播完后暂停", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("播完本首", fontSize = 12.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        SettingToggleRow(
                            title = "播完当前歌曲再停",
                            subtitle = "倒计时结束后等待当前歌曲自然播完",
                            checked = sleepStopAfterCurrentSong,
                            onCheckedChange = { viewModel.setSleepStopAfterCurrentSongPreference(it) }
                        )
                    }
                }

                // 6. 存储与音频缓存
                item {
                    SettingsSectionCard(
                        title = "存储与缓存",
                        icon = Icons.Rounded.Storage
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "本地音频缓存",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "已播歌曲自动缓存，提升二次加载速度",
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
                                    Toast.makeText(context, "已更新缓存统计", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                ConceptFluidRefreshIcon(
                                    tint = ConceptPrimary,
                                    spinTrigger = cacheRefreshSpin,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("刷新", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    AudioCacheManager.clearCache(context) {
                                        Toast.makeText(context, "已清理音频缓存", Toast.LENGTH_SHORT).show()
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
                                Text("清理缓存", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 7. 关于应用
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
                            text = "专注纯粹听歌体验，支持 Hi-Res / SQ 无损播放、逐字流光歌词、横屏播放模式、蝰蛇音效调音、2K 高定歌词海报与状态栏灵动岛。",
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
                color = MaterialTheme.colorScheme.onSurface,
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
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = thumbColor)
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
        title.contains("缓存") -> ConceptAmberAccent
        else -> ConceptPrimary
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = conceptFloatingCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.onSurface,
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

/**
 * 状态栏歌词与多系统灵动岛二级设置页面（深度参考椒盐音乐 `Moriafly/SaltPlayerSource` 架构）：
 * 1. 自动识别当前国内安卓 ROM（小米 HyperOS、OPPO ColorOS、vivo OriginOS、魅族 Flyme、华为 HarmonyOS、荣耀 MagicOS、三星 OneUI、类原生 AOSP）；
 * 2. 提供「椒盐风·状态栏悬浮歌词」与「iPhone 风格灵动岛音乐胶囊」双形态切换，免 Root 即可在任意安卓系统状态栏/前摄挖孔区域常驻渲染；
 * 3. 支持 X/Y 像素级位置微调（精准避让左/中/右前摄挖孔与系统时间电量）、宽度、字号、配色、透明背景与物理点击穿透（不挡下拉通知栏）；
 * 4. 完整集成魅族 Flyme 官方 `0x01000000 | 0x02000000` 通知 Ticker 协议、小米澎湃焦点通知 (`miui.focus.param`)、ColorOS 流体云、OriginOS 原子岛、车载/锁屏 `MediaSession` 歌词同步以及 `LyricGetter` / `SuperLyric` 广播协议。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatusBarLyricSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by SystemLyricBridge.state.collectAsState()
    val hasOverlayPermission = remember(state.statusBarOverlayActive, state.systemOverlayActive) {
        SystemLyricBridge.canDrawSystemOverlays(context)
    }

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "状态栏歌词与灵动岛",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                            )
                            Text(
                                text = "椒盐同款状态栏悬浮歌词 · 灵动岛胶囊 · 全系统原生协议",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                SystemLyricBridge.resetStatusBarSettingsToDefault(context)
                                Toast.makeText(context, "已恢复状态栏歌词与灵动岛默认参数", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("恢复默认", color = ConceptPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                contentPadding = PaddingValues(bottom = 140.dp, top = 6.dp)
            ) {
                // 1. 顶部实时预览与当前系统诊断卡片
                item {
                    RomDiagnosticAndPreviewCard(
                        rom = state.detectedRom,
                        enabled = state.statusBarBroadcastEnabled,
                        hasOverlayPermission = hasOverlayPermission,
                        style = SystemLyricBridge.effectiveOverlayStyle(state),
                        scheme = state.statusBarColorScheme,
                        sampleLyric = state.lastBroadcastedLine.ifBlank { "微风拂过耳畔 · 状态栏歌词与灵动岛已就绪" },
                        sampleSub = state.lastBroadcastedSubLine.ifBlank { "Breeze Music · SaltPlayer Multi-ROM Engine" },
                        showTranslation = state.statusBarShowTranslation,
                        showIslandMedia = state.statusBarShowIslandMedia,
                        bgAlpha = state.statusBarBgAlpha,
                        onToggleMaster = { SystemLyricBridge.setStatusBarBroadcastEnabled(context, it) },
                        onRequestOverlayPermission = { SystemLyricBridge.requestOverlayPermissionIfNeeded(context) },
                        onTestLyric = { SystemLyricBridge.triggerTestStatusBarLyric(context) }
                    )
                }

                // 2. 核心工作引擎选择（5 种适配模式）
                item {
                    StatusBarSectionCard(
                        title = "适配方案与工作模式",
                        icon = Icons.Rounded.Tune,
                        accent = ConceptCyanAccent
                    ) {
                        Text(
                            text = "国内未 Root 的 HyperOS / ColorOS / OriginOS / HarmonyOS 会拦截普通第三方应用直接改写系统状态栏，借鉴椒盐音乐 (SaltPlayer) 的「悬浮窗状态栏歌词」与「灵动岛胶囊」可实现 100% 免 Root 跨应用顶部常驻。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBarLyricMode.entries.forEach { mode ->
                                val selected = state.statusBarMode == mode
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) ConceptPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            SystemLyricBridge.setStatusBarMode(context, mode)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { SystemLyricBridge.setStatusBarMode(context, mode) },
                                            colors = RadioButtonDefaults.colors(selectedColor = ConceptPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.label,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.5.sp,
                                                color = if (selected) ConceptPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = mode.subtitle,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. 顶部视觉形态与灵动岛交互设置
                item {
                    StatusBarSectionCard(
                        title = "灵动岛胶囊与状态栏视觉形态",
                        icon = Icons.Rounded.AutoAwesomeMotion,
                        accent = ConceptVioletAccent
                    ) {
                        Text(
                            text = "顶部形态切换",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatusBarOverlayStyle.entries.forEach { style ->
                                val currentEffective = SystemLyricBridge.effectiveOverlayStyle(state)
                                val selected = currentEffective == style
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) ConceptVioletAccent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (selected) 1.2.dp else 0.5.dp,
                                            color = if (selected) ConceptVioletAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            SystemLyricBridge.setStatusBarOverlayStyle(style)
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (style == StatusBarOverlayStyle.DYNAMIC_ISLAND) Icons.Rounded.LensBlur else Icons.Rounded.Subtitles,
                                                contentDescription = null,
                                                tint = if (selected) ConceptVioletAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = style.label,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.5.sp,
                                                color = if (selected) ConceptVioletAccent else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = style.subtitle,
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "水平对齐与挖孔避让锚点",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusBarHorizontalAnchor.entries.forEach { anchor ->
                                FilterChip(
                                    selected = state.statusBarAnchor == anchor,
                                    onClick = { SystemLyricBridge.setStatusBarAnchor(anchor) },
                                    label = { Text(anchor.label, fontSize = 11.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ConceptVioletAccent.copy(alpha = 0.2f),
                                        selectedLabelColor = ConceptVioletAccent
                                    )
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        StatusBarToggleItem(
                            title = "手势点击穿透 (锁定不遮挡下拉通知栏)",
                            subtitle = if (state.statusBarTouchThrough) {
                                "已开启物理穿透：手指在顶部下滑可直接呼出系统通知栏；如需点击展开灵动岛请关闭此项"
                            } else {
                                "未开启穿透：支持手指拖拽微调状态栏歌词位置，点击灵动岛可展开音乐控播舱"
                            },
                            checked = state.statusBarTouchThrough,
                            onCheckedChange = { SystemLyricBridge.setStatusBarTouchThrough(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        StatusBarToggleItem(
                            title = "灵动岛微缩旋转黑胶与动态音频波纹",
                            subtitle = "在灵动岛胶囊左右两侧显示旋转黑胶唱片与实时律动频谱柱",
                            checked = state.statusBarShowIslandMedia,
                            onCheckedChange = { SystemLyricBridge.setStatusBarShowIslandMedia(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        StatusBarToggleItem(
                            title = "KRC 逐字流光扫光",
                            subtitle = "状态栏与灵动岛歌词随人声进度逐字渐变染色，超长句自动平滑滚动",
                            checked = state.statusBarWordByWord,
                            onCheckedChange = { SystemLyricBridge.setStatusBarWordByWord(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        StatusBarToggleItem(
                            title = "附带双语译文显示",
                            subtitle = "外语歌曲在状态栏或灵动岛下方同步显示精简中文译文",
                            checked = state.statusBarShowTranslation,
                            onCheckedChange = { SystemLyricBridge.setStatusBarShowTranslation(it) }
                        )
                    }
                }

                // 4. 椒盐同款：位置偏移、宽度、字号与配色精细调节
                item {
                    StatusBarSectionCard(
                        title = "椒盐同款 · 状态栏位置与字号微调",
                        icon = Icons.Rounded.OpenWith,
                        accent = ConceptMintAccent
                    ) {
                        Text(
                            text = "歌词流光配色",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DesktopLyricColorScheme.entries.forEach { scheme ->
                                val selected = state.statusBarColorScheme == scheme
                                val startC = Color(scheme.playedStartHex)
                                val endC = Color(scheme.playedEndHex)
                                Surface(
                                    shape = RoundedCornerShape(11.dp),
                                    color = if (selected) startC.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(11.dp))
                                        .border(
                                            width = if (selected) 1.2.dp else 0.5.dp,
                                            color = if (selected) startC else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(11.dp)
                                        )
                                        .clickable { SystemLyricBridge.setStatusBarColorScheme(scheme) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Brush.linearGradient(listOf(startC, endC)))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = scheme.label,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (selected) startC else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        FineTuneSliderWithStepButtons(
                            title = "水平位置偏移 (X 轴避让挖孔)",
                            valueText = "${state.statusBarOffsetX.roundToInt()} dp",
                            value = state.statusBarOffsetX,
                            valueRange = -160f..160f,
                            onValueChange = { SystemLyricBridge.setStatusBarOffsetX(it) },
                            onStepMinus = { SystemLyricBridge.setStatusBarOffsetX(state.statusBarOffsetX - 2f) },
                            onResetZero = { SystemLyricBridge.setStatusBarOffsetX(0f) },
                            onStepPlus = { SystemLyricBridge.setStatusBarOffsetX(state.statusBarOffsetX + 2f) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        FineTuneSliderWithStepButtons(
                            title = "垂直位置偏移 (Y 轴状态栏高度)",
                            valueText = "${state.statusBarOffsetY.roundToInt()} dp",
                            value = state.statusBarOffsetY,
                            valueRange = 0f..64f,
                            onValueChange = { SystemLyricBridge.setStatusBarOffsetY(it) },
                            onStepMinus = { SystemLyricBridge.setStatusBarOffsetY(state.statusBarOffsetY - 1f) },
                            onResetZero = { SystemLyricBridge.setStatusBarOffsetY(4f) },
                            onStepPlus = { SystemLyricBridge.setStatusBarOffsetY(state.statusBarOffsetY + 1f) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("状态栏歌词 / 灵动岛宽度", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("${state.statusBarWidthDp.roundToInt()} dp", color = ConceptPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                        Slider(
                            value = state.statusBarWidthDp,
                            onValueChange = { SystemLyricBridge.setStatusBarWidthDp(it) },
                            valueRange = 140f..380f,
                            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("状态栏歌词字号", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(String.format(Locale.getDefault(), "%.1f sp", state.statusBarFontSize), color = ConceptPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                        Slider(
                            value = state.statusBarFontSize,
                            onValueChange = { SystemLyricBridge.setStatusBarFontSize(it) },
                            valueRange = 11f..18f,
                            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("胶囊底色不透明度 (0% 为纯文字透明状态栏)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("${(state.statusBarBgAlpha * 100).roundToInt()}%", color = ConceptPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                        Slider(
                            value = state.statusBarBgAlpha,
                            onValueChange = { SystemLyricBridge.setStatusBarBgAlpha(it) },
                            valueRange = 0f..0.96f,
                            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                        )
                    }
                }

                // 5. 多系统原生通知协议、车载蓝牙与 Root/Xposed 模块桥接
                item {
                    StatusBarSectionCard(
                        title = "系统原生协议与车载/模块增强",
                        icon = Icons.Rounded.SendToMobile,
                        accent = ConceptAmberAccent
                    ) {
                        StatusBarToggleItem(
                            title = "魅族 Flyme 官方 Notification Ticker 协议",
                            subtitle = "发送 FLAG_ALWAYS_SHOW_TICKER (0x01000000) 与 FLAG_ONLY_UPDATE_TICKER (0x02000000)，适配 Flyme 与类原生演进版 ROM",
                            checked = state.flymeTickerProtocolEnabled,
                            onCheckedChange = { SystemLyricBridge.setFlymeTickerProtocolEnabled(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        StatusBarToggleItem(
                            title = "澎湃焦点通知 / ColorOS 流体云 / OriginOS 原子岛增强",
                            subtitle = "向系统通知注入 miui.focus.param、oplus.ongoing.lyric、vivo.originos.atomic.lyric 与 Android 16 Promoted Ongoing 扩展",
                            checked = state.systemLiveIslandNotificationEnabled,
                            onCheckedChange = { SystemLyricBridge.setSystemLiveIslandNotificationEnabled(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        StatusBarToggleItem(
                            title = "车载蓝牙 / 锁屏 MediaSession 元数据歌词同步",
                            subtitle = "实时将当前唱诵歌词同步至 MediaSession 副标题与扩展字段，车载屏幕、系统锁屏及系统原生媒体岛可直接显示歌词",
                            checked = state.mediaSessionLyricSyncEnabled,
                            onCheckedChange = { SystemLyricBridge.setMediaSessionLyricSyncEnabled(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        StatusBarToggleItem(
                            title = "LyricGetter / SuperLyric / StatusBarLyric 模块广播",
                            subtitle = "向 cn.lyric.getter.api.LYRIC、statusbar.lyric.update 等发送毫秒级歌词与时长广播，支持 LSPosed 状态栏歌词模块",
                            checked = state.xposedBroadcastEnabled,
                            onCheckedChange = { SystemLyricBridge.setXposedBroadcastEnabled(it) }
                        )
                    }
                }

                // 6. 国内各主流 Android 系统适配说明
                item {
                    StatusBarSectionCard(
                        title = "国内各安卓系统状态栏与灵动岛适配指南",
                        icon = Icons.Rounded.DevicesOther,
                        accent = ConceptCoralAccent
                    ) {
                        RomGuideItem(
                            brand = "小米 HyperOS / MIUI",
                            detail = "未解锁 Root 机型建议开启「悬浮窗权限」并使用「灵动岛音乐胶囊」或「椒盐极简状态栏字条」；已 Root 用户配合 LyricGetter / SuperLyric 模块可直接接管系统原生状态栏。"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RomGuideItem(
                            brand = "OPPO / 一加 / 真我 ColorOS",
                            detail = "已支持流体云实时通知与 MediaSession 锁屏歌词；开启悬浮窗权限后，顶部灵动岛胶囊可完美与前摄挖孔融为一体。"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RomGuideItem(
                            brand = "vivo / iQOO OriginOS",
                            detail = "已适配原子岛媒体通知与蓝牙车载歌词同步；若前摄为居中挖孔，可将锚点设为「顶部居中」并微调 Y 轴偏移贴合挖孔。"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RomGuideItem(
                            brand = "魅族 Flyme & 类原生定制 ROM",
                            detail = "原生内置 Flyme 开放平台 0x01000000 状态栏歌词协议，无需悬浮窗权限即可在系统状态栏直接滚动显示歌词。"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RomGuideItem(
                            brand = "华为 HarmonyOS / 荣耀 MagicOS / 三星 One UI",
                            detail = "通过「椒盐风·状态栏悬浮歌词」+「手势点击穿透」可在不遮挡下拉通知栏的前提下，在屏幕顶部状态栏实时渲染逐字流光歌词。"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RomDiagnosticAndPreviewCard(
    rom: com.concept.music.service.RomEcosystemType,
    enabled: Boolean,
    hasOverlayPermission: Boolean,
    style: StatusBarOverlayStyle,
    scheme: DesktopLyricColorScheme,
    sampleLyric: String,
    sampleSub: String,
    showTranslation: Boolean,
    showIslandMedia: Boolean,
    bgAlpha: Float,
    onToggleMaster: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onTestLyric: () -> Unit
) {
    val startColor = Color(scheme.playedStartHex)
    val endColor = Color(scheme.playedEndHex)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = conceptFloatingCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ConceptPrimary.copy(alpha = 0.16f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = ConceptPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "开启状态栏歌词与灵动岛",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.5.sp
                            )
                        )
                        Text(
                            text = "当前系统：${rom.displayName} (${rom.islandFeatureName})",
                            style = MaterialTheme.typography.bodySmall,
                            color = ConceptPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggleMaster,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ConceptPrimary)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A1220), Color(0xFF132038))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("09:41", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("5G · 100%", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val pillAlpha = if (style == StatusBarOverlayStyle.DYNAMIC_ISLAND) {
                        bgAlpha.coerceIn(0.28f, 0.96f)
                    } else {
                        bgAlpha
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF05080F).copy(alpha = pillAlpha))
                            .then(
                                if (pillAlpha > 0.12f) {
                                    Modifier.border(1.dp, startColor.copy(alpha = 0.55f), RoundedCornerShape(50))
                                } else Modifier
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        if (style == StatusBarOverlayStyle.DYNAMIC_ISLAND && showIslandMedia) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF162032))
                                    .border(1.dp, startColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(startColor)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = sampleLyric,
                                color = startColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (showTranslation && sampleSub.isNotBlank()) {
                                Text(
                                    text = sampleSub,
                                    color = endColor.copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (style == StatusBarOverlayStyle.DYNAMIC_ISLAND && showIslandMedia) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                listOf(6, 12, 9, 14).forEachIndexed { idx, h ->
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(h.dp)
                                            .clip(CircleShape)
                                            .background(if (idx % 2 == 0) startColor else endColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = rom.recommendedGuide,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRequestOverlayPermission,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (hasOverlayPermission) Icons.Rounded.Verified else Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = if (hasOverlayPermission) ConceptMintAccent else ConceptPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasOverlayPermission) "跨应用悬浮权限已就绪" else "授权跨应用顶部悬浮",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onTestLyric,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConceptPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("发送测试歌词", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun FineTuneSliderWithStepButtons(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onStepMinus: () -> Unit,
    onResetZero: () -> Unit,
    onStepPlus: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.softClickable(onClick = onStepMinus)
                ) {
                    Text(
                        text = "-1",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ConceptPrimary.copy(alpha = 0.16f),
                    modifier = Modifier.softClickable(onClick = onResetZero)
                ) {
                    Text(
                        text = valueText,
                        color = ConceptPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.softClickable(onClick = onStepPlus)
                ) {
                    Text(
                        text = "+1",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
        )
    }
}

@Composable
private fun StatusBarToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
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
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ConceptPrimary)
        )
    }
}

@Composable
private fun RomGuideItem(
    brand: String,
    detail: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                text = brand,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = ConceptPrimary,
                fontSize = 12.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun StatusBarSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = conceptFloatingCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
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
