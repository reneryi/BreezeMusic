package com.concept.music.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.data.model.AudioQuality
import com.concept.music.service.AudioCacheManager
import com.concept.music.service.AudioEffectManager
import com.concept.music.ui.theme.*
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.MusicPlayerViewModel
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 设置页二级菜单分类定义：
 * 告别原先单页长瀑布流堆叠，一级页面仅展示分类入口与实时配置摘要，点击进入对应的二级设置详情页。
 */
enum class SettingsCategory(
    val title: String,
    val subtitle: String
) {
    APPEARANCE(
        title = "外观与播放样式",
        subtitle = "主题模式、唱片样式、播放页背景与封面动态取色"
    ),
    AUDIO_QUALITY(
        title = "音质与音效",
        subtitle = "蝰蛇音效调音台、默认在线音质、移动省流与切歌过渡"
    ),
    PLAYBACK_CONTROL(
        title = "外设与播放控制",
        subtitle = "允许与其他应用同时播放、有线耳机线控切歌与播放行为"
    ),
    LYRICS(
        title = "歌词显示设置",
        subtitle = "全屏歌词字号、译文比例、对齐方式、景深模糊与屏幕常亮"
    ),
    SPEED_SLEEP(
        title = "倍速与睡眠定时",
        subtitle = "倍速升降调调节与睡眠倒计时自动停止播放"
    ),
    STORAGE(
        title = "存储与缓存",
        subtitle = "边听边存本地音频缓存统计与一键清理"
    ),
    ABOUT(
        title = "关于微风音乐",
        subtitle = "版本信息、酷狗概念版特性与说明"
    )
}

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
    val playerBackgroundStyle = uiState.playerBackgroundStyle
    val dynamicCoverAccentEnabled = uiState.dynamicCoverAccentEnabled
    val lyricFontSize = uiState.lyricFontSize
    val lyricTranslationScale = uiState.lyricTranslationScale
    val lyricAlignment = uiState.lyricAlignment
    val lyricBlurEnabled = uiState.lyricBlurEnabled
    val lyricKeepScreenOn = uiState.lyricKeepScreenOn
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
    val allowPlayWithOtherApps = uiState.allowPlayWithOtherApps
    val wiredHeadsetControlEnabled = uiState.wiredHeadsetControlEnabled
    val autoPlayOnStartup = uiState.autoPlayOnStartup
    val smoothPlayPauseFade = uiState.smoothPlayPauseFade
    val motionOrientationPermissionEnabled = uiState.motionOrientationPermissionEnabled

    val cacheSizeBytes by AudioCacheManager.cacheSizeBytes.collectAsState()
    val themeMode by ThemeManager.themeMode.collectAsState()
    val audioEffectState by AudioEffectManager.state.collectAsState()

    var activeCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val activeCategory = remember(activeCategoryName) {
        activeCategoryName?.let { name -> runCatching { SettingsCategory.valueOf(name) }.getOrNull() }
    }

    // 处于二级菜单时，系统返回手势优先退回设置一级主菜单
    BackHandler(enabled = activeCategory != null) {
        activeCategoryName = null
    }

    LaunchedEffect(Unit) {
        AudioCacheManager.refreshCacheSize(context)
    }

    val isSleepActive = sleepTimerRemainingSec > 0L || sleepPendingStopAtTrackEnd
    val sleepStatusText = when {
        sleepTimerRemainingSec > 0L -> "剩余 ${sleepTimerRemainingSec / 60}分${sleepTimerRemainingSec % 60}秒"
        sleepPendingStopAtTrackEnd -> "播完当前歌曲后停止"
        else -> "定时未开启"
    }

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = activeCategory?.title ?: "设置",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (activeCategory != null) {
                                Text(
                                    text = "设置 · 二级选项",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (activeCategory != null) {
                                    activeCategoryName = null
                                } else {
                                    onBackClick()
                                }
                            }
                        ) {
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
            AnimatedContent(
                targetState = activeCategory,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> (fullWidth * 0.24f).toInt() },
                            animationSpec = tween(durationMillis = 340, easing = ConceptMotion.GentleSpatialEnterEasing)
                        ) + fadeIn(tween(durationMillis = 280, easing = ConceptMotion.GentleSpatialEnterEasing))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() },
                                animationSpec = tween(durationMillis = 300, easing = ConceptMotion.GentleSpatialExitEasing)
                            ) + fadeOut(tween(durationMillis = 220, easing = ConceptMotion.GentleSpatialExitEasing)))
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() },
                            animationSpec = tween(durationMillis = 320, easing = ConceptMotion.GentleSpatialEnterEasing)
                        ) + fadeIn(tween(durationMillis = 260, easing = ConceptMotion.GentleSpatialEnterEasing))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (fullWidth * 0.24f).toInt() },
                                animationSpec = tween(durationMillis = 280, easing = ConceptMotion.GentleSpatialExitEasing)
                            ) + fadeOut(tween(durationMillis = 220, easing = ConceptMotion.GentleSpatialExitEasing)))
                    }
                },
                label = "SettingsSubPageTransition"
            ) { category ->
                if (category == null) {
                    // 一级菜单主页：分类卡片导航列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 140.dp, top = 8.dp)
                    ) {
                        item {
                            SettingsGroupHeader(title = "视听与显示")
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.APPEARANCE,
                                icon = Icons.Rounded.Palette,
                                accentColor = ConceptVioletAccent,
                                statusText = "${themeMode.label} · ${coverStyle.label} · ${playerBackgroundStyle.label}",
                                onClick = { activeCategoryName = SettingsCategory.APPEARANCE.name }
                            )
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.LYRICS,
                                icon = Icons.Rounded.Lyrics,
                                accentColor = ConceptCyanAccent,
                                statusText = "${lyricFontSize.toInt()}sp · ${lyricAlignment.label} · ${if (lyricBlurEnabled) "景深模糊" else "清晰文本"}",
                                onClick = { activeCategoryName = SettingsCategory.LYRICS.name }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(2.dp))
                            SettingsGroupHeader(title = "播放与控制")
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.AUDIO_QUALITY,
                                icon = Icons.Rounded.HighQuality,
                                accentColor = ConceptMintAccent,
                                statusText = "默认 ${preferredQuality.label} · ${if (audioEffectState.isEnabled) "蝰蛇：${audioEffectState.currentPreset.label}" else "音效关闭"}",
                                onClick = { activeCategoryName = SettingsCategory.AUDIO_QUALITY.name }
                            )
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.PLAYBACK_CONTROL,
                                icon = Icons.Rounded.Headphones,
                                accentColor = ConceptCoralAccent,
                                statusText = "${if (allowPlayWithOtherApps) "允许与其他应用同播" else "独占音频焦点"} · ${if (wiredHeadsetControlEnabled) "有线线控开启" else "有线线控关闭"}",
                                onClick = { activeCategoryName = SettingsCategory.PLAYBACK_CONTROL.name }
                            )
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.SPEED_SLEEP,
                                icon = Icons.Rounded.Speed,
                                accentColor = ConceptPrimary,
                                statusText = "倍速 ${String.format(Locale.getDefault(), "%.2f", playbackSpeed)}x · $sleepStatusText",
                                onClick = { activeCategoryName = SettingsCategory.SPEED_SLEEP.name }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(2.dp))
                            SettingsGroupHeader(title = "系统与信息")
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.STORAGE,
                                icon = Icons.Rounded.Storage,
                                accentColor = ConceptAmberAccent,
                                statusText = "已缓存 ${AudioCacheManager.formatSize(cacheSizeBytes)}",
                                onClick = { activeCategoryName = SettingsCategory.STORAGE.name }
                            )
                        }
                        item {
                            SettingsMenuEntryCard(
                                category = SettingsCategory.ABOUT,
                                icon = Icons.Rounded.Info,
                                accentColor = ConceptPrimary,
                                statusText = "BreezeMusic v1.0.0 · 酷狗概念版第三方",
                                onClick = { activeCategoryName = SettingsCategory.ABOUT.name }
                            )
                        }
                    }
                } else {
                    // 二级菜单详情页：仅展示当前所选分类的具体设置项
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 140.dp, top = 8.dp)
                    ) {
                        when (category) {
                            SettingsCategory.APPEARANCE -> {
                                item {
                                    SettingsSectionCard(
                                        title = "主题与色彩",
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
                                    }
                                }

                                item {
                                    SettingsSectionCard(
                                        title = "播放页视觉样式",
                                        icon = Icons.Rounded.Album
                                    ) {
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

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

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

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        SettingToggleRow(
                                            title = "封面智能动态取色",
                                            subtitle = "提取封面主色渲染播放键与进度条",
                                            checked = dynamicCoverAccentEnabled,
                                            onCheckedChange = { viewModel.setDynamicCoverAccentEnabled(it) }
                                        )
                                    }
                                }
                            }

                            SettingsCategory.AUDIO_QUALITY -> {
                                item {
                                    SettingsSectionCard(
                                        title = "音效与母带调音",
                                        icon = Icons.Rounded.GraphicEq
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

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

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
                                    }
                                }

                                item {
                                    SettingsSectionCard(
                                        title = "在线播放音质与过渡",
                                        icon = Icons.Rounded.HighQuality
                                    ) {
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

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

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
                            }

                            SettingsCategory.PLAYBACK_CONTROL -> {
                                item {
                                    SettingsSectionCard(
                                        title = "音频焦点与多应用并发",
                                        icon = Icons.Rounded.Layers
                                    ) {
                                        SettingToggleRow(
                                            title = "允许与其他应用同时播放",
                                            subtitle = "开启后不独占系统音频焦点，刷视频、玩游戏或发语音时不中断音乐播放",
                                            checked = allowPlayWithOtherApps,
                                            onCheckedChange = { enabled ->
                                                viewModel.setAllowPlayWithOtherApps(enabled)
                                                val msg = if (enabled) "已开启与其他应用同时播放" else "已恢复独占音频焦点模式"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }

                                item {
                                    SettingsSectionCard(
                                        title = "外设与有线耳机线控",
                                        icon = Icons.Rounded.Headphones
                                    ) {
                                        SettingToggleRow(
                                            title = "有线耳机线控切歌",
                                            subtitle = "通过有线耳机暂停键（中键）双击切换下一首、三击切换上一首",
                                            checked = wiredHeadsetControlEnabled,
                                            onCheckedChange = { enabled ->
                                                viewModel.setWiredHeadsetControlEnabled(enabled)
                                                val msg = if (enabled) "已开启有线耳机双击/三击切歌" else "已关闭有线耳机多击切歌"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        if (wiredHeadsetControlEnabled) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "有线耳机按键操作说明",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = ConceptCoralAccent
                                                    )
                                                    Text(
                                                        text = "• 单击暂停键（中键）：播放 / 暂停\n• 双击暂停键（中键）：切换下一首\n• 三击暂停键（中键）：切换上一首",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        lineHeight = 18.sp
                                                    )
                                                }
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        SettingToggleRow(
                                            title = "断开耳机自动暂停",
                                            subtitle = "拔出有线耳机或蓝牙断开时暂停播放",
                                            checked = pauseOnHeadsetDisconnect,
                                            onCheckedChange = { viewModel.setPauseOnHeadsetDisconnect(it) }
                                        )
                                    }
                                }

                                item {
                                    SettingsSectionCard(
                                        title = "播放行为与横屏感应",
                                        icon = Icons.Rounded.Tune
                                    ) {
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
                            }

                            SettingsCategory.LYRICS -> {
                                item {
                                    SettingsSectionCard(
                                        title = "歌词排版与视觉",
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

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        SettingToggleRow(
                                            title = "阶梯景深模糊",
                                            subtitle = "非当前唱诵行随距离自然虚化",
                                            checked = lyricBlurEnabled,
                                            onCheckedChange = { viewModel.setLyricBlurEnabled(it) }
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        SettingToggleRow(
                                            title = "歌词屏幕常亮",
                                            subtitle = "处于全屏歌词页或横屏播放页时保持屏幕常亮不息屏",
                                            checked = lyricKeepScreenOn,
                                            onCheckedChange = { viewModel.setLyricKeepScreenOn(it) }
                                        )
                                    }
                                }
                            }

                            SettingsCategory.SPEED_SLEEP -> {
                                item {
                                    SettingsSectionCard(
                                        title = "倍速与音调调节",
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
                                    }
                                }

                                item {
                                    SettingsSectionCard(
                                        title = "睡眠定时关闭",
                                        icon = Icons.Rounded.Bedtime
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("睡眠定时", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                                Text(
                                                    text = sleepStatusText,
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
                            }

                            SettingsCategory.STORAGE -> {
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
                            }

                            SettingsCategory.ABOUT -> {
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
                                            text = "专注纯粹听歌体验，支持 Hi-Res / SQ 无损播放、KRC 逐字流光与灵动音律跃动歌词、横屏播放模式、蝰蛇音效调音、2K 高定歌词海报与桌面悬浮歌词。",
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
            }
        }
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsMenuEntryCard(
    category: SettingsCategory,
    icon: ImageVector,
    accentColor: Color,
    statusText: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = conceptFloatingCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .softCardClickable(shape = RoundedCornerShape(22.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category.title,
                    tint = accentColor,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "进入${category.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
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
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val sectionAccent = when {
        title.contains("外观") || title.contains("主题") || title.contains("视觉") -> ConceptVioletAccent
        title.contains("音质") || title.contains("音效") -> ConceptMintAccent
        title.contains("外设") || title.contains("焦点") || title.contains("播放行为") -> ConceptCoralAccent
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
