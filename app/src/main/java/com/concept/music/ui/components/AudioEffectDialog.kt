package com.concept.music.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.concept.music.service.AudioEffectManager
import com.concept.music.service.AudioEffectState
import com.concept.music.service.EffectPreset
import com.concept.music.service.ReverbMode
import com.concept.music.service.ViperEffectCategory
import com.concept.music.ui.theme.*
import java.util.Locale
import kotlin.math.abs

@Composable
fun AudioEffectDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ViperSoundEffectScreen(onBack = onDismiss)
    }
}

private enum class SoundEffectMainTab(
    val index: Int,
    val title: String,
    val icon: ImageVector
) {
    PRESET_LIBRARY(0, "蝰蛇声效", Icons.Rounded.GraphicEq),
    ACOUSTIC_TUNING(1, "声场调音", Icons.Rounded.SurroundSound),
    PARAMETRIC_EQ(2, "参量均衡", Icons.Rounded.Equalizer)
}

/**
 * 蝰蛇音效与调音台 (ViperSoundEffectScreen v1.1.0)：
 * - 对接 64-bit ConceptMasteringAudioProcessor 实时 PCM 母带引擎；
 * - 预设切换、声场调音与 10 段参量均衡推杆全链路实时联动生效；
 * - 调节任意声场或均衡推杆时自动激活音效引擎，无需预先手动开启总开关。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViperSoundEffectScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val state by AudioEffectManager.state.collectAsState()
    var selectedMainTab by remember { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf(ViperEffectCategory.ALL) }

    LaunchedEffect(Unit) {
        AudioEffectManager.refreshOnlineViperEffects()
    }

    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalContentColor provides primaryText) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    val viperAdaptive = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                    Column(
                        modifier = Modifier
                            .then(
                                if (viperAdaptive.isLargeScreenDevice) Modifier.widthIn(max = 920.dp) else Modifier
                            )
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        // 1. 顶部标题栏 + DSP 总开关
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "返回",
                                        tint = primaryText
                                    )
                                }
                                Column {
                                    Text(
                                        text = "蝰蛇音效与调音台",
                                        color = primaryText,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = if (state.isEnabled) "当前音效：${state.currentPreset.label}" else "直通模式 · 未开启音效",
                                        color = if (state.isEnabled) ConceptPrimary else subText,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Switch(
                                checked = state.isEnabled,
                                onCheckedChange = { AudioEffectManager.setEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ConceptPrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. 轻量级四维声学指标条
                        CompactAcousticSummaryStrip(state = state)

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. 三大声学分区导航栏
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = conceptElevatedSurfaceColor(),
                            contentColor = primaryText,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SoundEffectMainTab.entries.forEach { tab ->
                                    val selected = selectedMainTab == tab.index
                                    val tabBg by animateColorAsState(
                                        targetValue = if (selected) ConceptPrimary else Color.Transparent,
                                        animationSpec = tween(180),
                                        label = "soundMainTabBg_${tab.index}"
                                    )
                                    val tabTextColor by animateColorAsState(
                                        targetValue = if (selected) Color.White else subText,
                                        animationSpec = tween(180),
                                        label = "soundMainTabTxt_${tab.index}"
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(tabBg)
                                            .softClickable { selectedMainTab = tab.index }
                                            .padding(vertical = 9.dp, horizontal = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = null,
                                            tint = tabTextColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = tab.title,
                                            color = tabTextColor,
                                            fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            ) { innerPadding ->
                val viperBodyAdaptive = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .horizontalCategorySwipe(
                            currentIndex = selectedMainTab,
                            itemCount = SoundEffectMainTab.entries.size,
                            onSelectIndex = { selectedMainTab = it },
                            onSwipeRightAtStart = onBack
                        ),
                    contentAlignment = Alignment.TopCenter
                ) {
                    AnimatedContent(
                        targetState = selectedMainTab,
                        modifier = Modifier
                            .then(
                                if (viperBodyAdaptive.isLargeScreenDevice) Modifier.widthIn(max = 920.dp) else Modifier
                            )
                            .fillMaxSize(),
                        transitionSpec = {
                            conceptCategoryContentTransition(if (targetState > initialState) 1 else -1)
                        },
                        label = "viperThreeTabSwitch"
                    ) { page ->
                        when (page) {
                            0 -> ViperPresetLibraryTab(
                                state = state,
                                selectedCategory = selectedCategory,
                                onSelectCategory = { selectedCategory = it },
                                onJumpToTuning = { selectedMainTab = 1 }
                            )
                            1 -> AcousticFieldTuningTab(
                                state = state
                            )
                            else -> ProfessionalEqualizerPage(
                                state = state
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactAcousticSummaryStrip(
    state: AudioEffectState
) {
    val isEnabled = state.isEnabled && state.currentPreset != EffectPreset.OFF
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = conceptFloatingCardColor(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AcousticMetricMiniBar(
                label = "低频",
                percent = if (isEnabled) state.bassBoostStrength / 10 else 0,
                color = ConceptCoralAccent,
                modifier = Modifier.weight(1f)
            )
            AcousticMetricMiniBar(
                label = "声场",
                percent = if (isEnabled) state.virtualizerStrength / 10 else 0,
                color = ConceptCyanAccent,
                modifier = Modifier.weight(1f)
            )
            AcousticMetricMiniBar(
                label = "人声",
                percent = if (isEnabled) state.vocalClarityStrength / 10 else 0,
                color = ConceptPrimary,
                modifier = Modifier.weight(1f)
            )
            AcousticMetricMiniBar(
                label = "谐波",
                percent = if (isEnabled) state.analogWarmthStrength / 10 else 0,
                color = VipGold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 专区一：蝰蛇声效库
 */
@Composable
private fun ViperPresetLibraryTab(
    state: AudioEffectState,
    selectedCategory: ViperEffectCategory,
    onSelectCategory: (ViperEffectCategory) -> Unit,
    onJumpToTuning: () -> Unit
) {
    val context = LocalContext.current

    val allViperPresets = remember {
        EffectPreset.entries.filter { it != EffectPreset.OFF && !it.isEqPreset }
    }
    val filteredPresets = remember(selectedCategory, allViperPresets) {
        if (selectedCategory == ViperEffectCategory.ALL) {
            allViperPresets
        } else {
            allViperPresets.filter { it.category == selectedCategory }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 分类筛选栏
        item {
            val categoryTabs = remember {
                ViperEffectCategory.entries.map { cat ->
                    val accent = when (cat) {
                        ViperEffectCategory.ALL -> ConceptPrimary
                        ViperEffectCategory.FLAGSHIP -> VipGold
                        ViperEffectCategory.SPATIAL_3D -> ConceptCyanAccent
                        ViperEffectCategory.RETRO_ANALOG -> ConceptAmberAccent
                        ViperEffectCategory.GENRE_VOCAL -> ConceptVioletAccent
                        ViperEffectCategory.DEVICE_CAR -> ConceptMintAccent
                    }
                    ConceptCategoryTabItem(
                        key = cat.key,
                        label = cat.label,
                        accentColor = accent
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                ConceptSlidingCategoryTabs(
                    tabs = categoryTabs,
                    selectedKey = selectedCategory.key,
                    onSelectTab = { key ->
                        ViperEffectCategory.entries.find { it.key == key }?.let(onSelectCategory)
                    },
                    itemSpacing = 18.dp,
                    fontSize = 13.sp,
                    indicatorWidth = 18.dp,
                    fillWidth = false
                )
            }
        }

        // 双列清爽音效宫格（无边框色块选中，高可读性）
        val presetRows = filteredPresets.chunked(2)
        items(presetRows) { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowPresets.forEach { preset ->
                    val isSelected = state.isEnabled && state.currentPreset == preset
                    val onlineMeta = state.onlineEffectMeta[preset.modeId]
                    ViperEffectPresetCard(
                        preset = preset,
                        onlineDesc = onlineMeta?.description,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                onJumpToTuning()
                            } else {
                                AudioEffectManager.setPreset(preset)
                                Toast.makeText(context, "已启用：${preset.label}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowPresets.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 专区二：声场调音（所有滑杆与选项始终支持直接拖动/点击，修改任意参数立即激活 64-bit DSP 并实时生效）
 */
@Composable
private fun AcousticFieldTuningTab(
    state: AudioEffectState
) {
    val context = LocalContext.current
    val cardBg = conceptFloatingCardColor()
    val elevatedBg = conceptElevatedSurfaceColor()
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.SurroundSound,
                                contentDescription = null,
                                tint = ConceptCyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "低频与空间声场",
                                color = primaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        TextButton(
                            onClick = {
                                AudioEffectManager.resetCurrentPresetToDefault()
                                Toast.makeText(context, "已恢复默认声场参数", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("恢复默认", fontSize = 12.sp, color = ConceptPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ParameterSliderItem(
                        title = "重低音下潜",
                        valueText = "${state.bassBoostStrength / 10}% · ${state.bassCutoffHz}Hz",
                        value = state.bassBoostStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptCoralAccent,
                        onValueChange = { AudioEffectManager.setBassBoostStrength(it.toInt()) }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("低频频点:", fontSize = 11.5.sp, color = subText)
                        listOf(
                            45 to "45Hz 深潜",
                            60 to "60Hz 均衡",
                            85 to "85Hz 紧凑"
                        ).forEach { (hz, label) ->
                            val selected = state.bassCutoffHz == hz
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) ConceptCoralAccent.copy(alpha = 0.18f) else elevatedBg,
                                contentColor = if (selected) ConceptCoralAccent else subText,
                                modifier = Modifier.softCardClickable(enabled = true) {
                                    AudioEffectManager.setBassCutoffHz(hz)
                                }
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) ConceptCoralAccent else subText,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    ParameterSliderItem(
                        title = "3D 环绕声场宽度",
                        valueText = "${state.virtualizerStrength / 10}%",
                        value = state.virtualizerStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptCyanAccent,
                        onValueChange = { AudioEffectManager.setVirtualizerStrength(it.toInt()) }
                    )

                    if (state.currentPreset == EffectPreset.VIPER_3D_ROTATE || state.surroundRotationSpeed > 0) {
                        ParameterSliderItem(
                            title = "360° 动态环绕轨迹速率",
                            valueText = if (state.surroundRotationSpeed == 0) "固定声场" else "${state.surroundRotationSpeed}%",
                            value = state.surroundRotationSpeed.toFloat(),
                            valueRange = 0f..100f,
                            enabled = true,
                            accentColor = ConceptVioletAccent,
                            onValueChange = { AudioEffectManager.setSurroundRotationSpeed(it.toInt()) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "空间混响",
                        color = primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ReverbMode.entries) { mode ->
                            val isSelected = state.reverbMode == mode
                            Surface(
                                color = if (isSelected) ConceptPrimary else elevatedBg,
                                contentColor = if (isSelected) Color.White else subText,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.softCardClickable(
                                    enabled = true,
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = { AudioEffectManager.setReverbMode(mode) }
                                )
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (isSelected) Color.White else subText,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = VipGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "人声解析与胆机温暖度",
                            color = primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ParameterSliderItem(
                        title = "人声通透度",
                        valueText = "${state.vocalClarityStrength / 10}%",
                        value = state.vocalClarityStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptPrimary,
                        onValueChange = { AudioEffectManager.setVocalClarityStrength(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "黑胶与胆机温暖度",
                        valueText = "${state.analogWarmthStrength / 10}%",
                        value = state.analogWarmthStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = VipGold,
                        onValueChange = { AudioEffectManager.setAnalogWarmthStrength(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "动态响度增益",
                        valueText = String.format(Locale.getDefault(), "+%.1f dB", state.loudnessGainMb / 100f),
                        value = state.loudnessGainMb.toFloat(),
                        valueRange = 0f..1500f,
                        enabled = true,
                        accentColor = ConceptMintAccent,
                        onValueChange = { AudioEffectManager.setLoudnessGain(it.toInt()) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = primaryText.copy(alpha = 0.08f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "夜间小音量等响度补偿",
                                color = primaryText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "低音量聆听时自动补偿高低频细节",
                                color = subText,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = state.equalLoudnessCompensation,
                            onCheckedChange = { AudioEffectManager.setEqualLoudnessCompensation(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ConceptPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AcousticMetricMiniBar(
    label: String,
    percent: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedFrac by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "metric_$label"
    )
    val trackBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "$percent%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.5.dp)
        ) {
            val w = size.width
            val h = size.height
            val r = CornerRadius(h / 2f, h / 2f)
            drawRoundRect(
                color = trackBgColor,
                size = Size(w, h),
                cornerRadius = r
            )
            val fillW = (w * animatedFrac.coerceIn(0f, 1f)).coerceIn(0f, w)
            if (fillW > 0.5f) {
                drawRoundRect(
                    color = color,
                    size = Size(fillW, h),
                    cornerRadius = r
                )
            }
        }
    }
}

@Composable
private fun ParameterSliderItem(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                text = valueText,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.16f)
            ),
            modifier = Modifier.height(32.dp)
        )
    }
}

@Composable
private fun ViperEffectPresetCard(
    preset: EffectPreset,
    onlineDesc: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val cardBg = conceptFloatingCardColor()
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant

    val catAccent = when (preset.category) {
        ViperEffectCategory.FLAGSHIP -> VipGold
        ViperEffectCategory.SPATIAL_3D -> ConceptCyanAccent
        ViperEffectCategory.RETRO_ANALOG -> ConceptAmberAccent
        ViperEffectCategory.GENRE_VOCAL -> ConceptVioletAccent
        ViperEffectCategory.DEVICE_CAR -> ConceptMintAccent
        else -> ConceptPrimary
    }

    val icon: ImageVector = when (preset) {
        EffectPreset.VIPER_3D, EffectPreset.VIPER_3D_ROTATE -> Icons.Rounded.SurroundSound
        EffectPreset.VIPER_BASS, EffectPreset.VIPER_CAR_SUBWOOFER -> Icons.Rounded.Speaker
        EffectPreset.VIPER_VOCAL, EffectPreset.VIPER_GUFENG, EffectPreset.VIPER_ACG -> Icons.Rounded.RecordVoiceOver
        EffectPreset.VIPER_HIFI, EffectPreset.VIPER_CONCERT, EffectPreset.VIPER_LIVEHOUSE -> Icons.Rounded.Stadium
        EffectPreset.VIPER_VINYL, EffectPreset.VIPER_BLACK_DIAMOND, EffectPreset.VIPER_GRAMOPHONE -> Icons.Rounded.Album
        EffectPreset.VIPER_TAPE -> Icons.Rounded.Radio
        EffectPreset.VIPER_TUBE -> Icons.Rounded.Bolt
        EffectPreset.VIPER_IEM, EffectPreset.VIPER_HEADPHONE, EffectPreset.VIPER_TWS -> Icons.Rounded.Headphones
        EffectPreset.VIPER_CAR_LUXURY -> Icons.Rounded.DirectionsCar
        else -> Icons.Rounded.GraphicEq
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) catAccent.copy(alpha = if (isDark) 0.22f else 0.15f) else cardBg,
        contentColor = primaryText,
        modifier = modifier.softCardClickable(shape = RoundedCornerShape(18.dp), onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(catAccent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = preset.label,
                        tint = catAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = catAccent
                    ) {
                        Text(
                            text = "使用中",
                            color = Color(0xFF0A1018),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        preset.acousticTags.take(1).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = catAccent.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = tag,
                                    color = catAccent,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = preset.label,
                color = if (isSelected) catAccent else primaryText,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = onlineDesc?.takeIf { it.isNotBlank() } ?: preset.desc,
                color = subText,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 专区三：参量均衡器（支持 10 段独立双二阶 Biquad 滤波推杆、前置放大增益与声道平衡实时生效）
 */
@Composable
private fun ProfessionalEqualizerPage(
    state: AudioEffectState
) {
    val context = LocalContext.current
    val cardBg = conceptFloatingCardColor()
    val elevatedBg = conceptElevatedSurfaceColor()
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant

    val eqPresets = remember {
        listOf(
            EffectPreset.FLAT,
            EffectPreset.POP,
            EffectPreset.ROCK,
            EffectPreset.VIPER_DYNAMIC,
            EffectPreset.EQ_CLASSICAL,
            EffectPreset.EQ_JAZZ,
            EffectPreset.EQ_DANCE,
            EffectPreset.EQ_HIPHOP,
            EffectPreset.EQ_ACOUSTIC,
            EffectPreset.EQ_SOFT,
            EffectPreset.CUSTOM
        )
    }

    val effectiveCurveBands = remember(state) {
        AudioEffectManager.computeEffectiveTenBandsMb(state)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "实时频响曲线",
                                color = primaryText,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp
                            )
                            Text(
                                text = "${state.currentPreset.label} · 增益 ${String.format(Locale.getDefault(), "%+.1f dB", state.preampGainDb)}",
                                color = subText,
                                fontSize = 11.5.sp
                            )
                        }

                        TextButton(
                            onClick = {
                                AudioEffectManager.setPreset(EffectPreset.FLAT)
                                AudioEffectManager.setPreampGainDb(0f)
                                Toast.makeText(context, "已重置均衡器", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("归零重置", color = ConceptPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    EqFrequencyResponseCurveCanvas(
                        tenBandLevels = effectiveCurveBands,
                        preampDb = state.preampGainDb,
                        enabled = state.isEnabled
                    )
                }
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(eqPresets) { preset ->
                    val isSelected = state.isEnabled && state.currentPreset == preset
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) ConceptPrimary else cardBg,
                        contentColor = if (isSelected) Color.White else primaryText,
                        modifier = Modifier.softCardClickable(
                            shape = RoundedCornerShape(12.dp),
                            onClick = { AudioEffectManager.setPreset(preset) }
                        )
                    ) {
                        Text(
                            text = preset.label,
                            color = if (isSelected) Color.White else primaryText,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "10段频响推杆 (-10dB ~ +10dB)",
                            color = primaryText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "31Hz — 16kHz",
                            color = ConceptPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val tenBands = state.tenBandLevels.let {
                        if (it.size == 10) it else List(10) { 0 }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        tenBands.forEachIndexed { index, level ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                val dbVal = level / 100f
                                Text(
                                    text = if (level > 0) String.format(Locale.getDefault(), "+%.1f", dbVal)
                                    else if (level < 0) String.format(Locale.getDefault(), "%.1f", dbVal)
                                    else "0",
                                    color = if (level != 0) ConceptPrimary else subText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .height(144.dp)
                                        .width(28.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(elevatedBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(14.dp)
                                            .height(1.dp)
                                            .background(subText.copy(alpha = 0.25f))
                                    )
                                    Slider(
                                        value = level.toFloat(),
                                        onValueChange = { AudioEffectManager.setTenBandLevel(index, it.toInt()) },
                                        valueRange = -1000f..1000f,
                                        enabled = true,
                                        colors = SliderDefaults.colors(
                                            thumbColor = ConceptPrimary,
                                            activeTrackColor = ConceptPrimary,
                                            inactiveTrackColor = ConceptPrimary.copy(alpha = 0.16f)
                                        ),
                                        modifier = Modifier
                                            .requiredWidth(130.dp)
                                            .requiredHeight(30.dp)
                                            .graphicsLayer {
                                                rotationZ = -90f
                                            }
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = AudioEffectManager.TEN_BAND_FREQUENCIES.getOrElse(index) { "" },
                                    color = subText,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "前置增益与声道平衡",
                            color = primaryText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        if (abs(state.channelBalance) > 0.02f || abs(state.preampGainDb) > 0.05f) {
                            TextButton(
                                onClick = {
                                    AudioEffectManager.setPreampGainDb(0f)
                                    AudioEffectManager.setChannelBalance(0f)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("归零", color = ConceptPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    ParameterSliderItem(
                        title = "前置放大增益",
                        valueText = String.format(Locale.getDefault(), "%+.1f dB", state.preampGainDb),
                        value = state.preampGainDb,
                        valueRange = -6f..6f,
                        enabled = true,
                        accentColor = ConceptPrimary,
                        onValueChange = { AudioEffectManager.setPreampGainDb(it) }
                    )

                    val balPercent = (state.channelBalance * 100).toInt()
                    val balLabel = when {
                        abs(balPercent) <= 2 -> "居中 (L = R)"
                        balPercent < 0 -> "偏左 L ${abs(balPercent)}%"
                        else -> "偏右 R $balPercent%"
                    }
                    ParameterSliderItem(
                        title = "左右声道平衡",
                        valueText = balLabel,
                        value = state.channelBalance,
                        valueRange = -1f..1f,
                        enabled = true,
                        accentColor = ConceptCyanAccent,
                        onValueChange = { AudioEffectManager.setChannelBalance(it) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = primaryText.copy(alpha = 0.08f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "防削波破音保护",
                                color = primaryText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "提升多频段增益时自动预留动态余量，防止破音",
                                color = subText,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = state.antiClippingEnabled,
                            onCheckedChange = { AudioEffectManager.setAntiClippingEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ConceptPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqFrequencyResponseCurveCanvas(
    tenBandLevels: List<Int>,
    preampDb: Float,
    enabled: Boolean
) {
    val elevatedBg = conceptElevatedSurfaceColor()
    val lineColor = if (enabled) ConceptPrimary else Color.Gray
    val glowColor = if (enabled) ConceptCyanAccent else Color.Gray

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(elevatedBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            listOf(0.15f, 0.5f, 0.85f).forEach { frac ->
                val y = h * frac
                drawLine(
                    color = Color.White.copy(alpha = if (frac == 0.5f) 0.18f else 0.07f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (frac == 0.5f) 1.2.dp.toPx() else 0.8.dp.toPx()
                )
            }

            val bands = if (tenBandLevels.size == 10) tenBandLevels else List(10) { 0 }
            val points = bands.mapIndexed { idx, levelMb ->
                val x = (w / (bands.size - 1).coerceAtLeast(1)) * idx
                val normalized = ((levelMb / 1000f) + (preampDb / 18f)).coerceIn(-1f, 1f)
                val y = midY - normalized * (h * 0.40f)
                Offset(x, y)
            }

            if (points.size >= 2) {
                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.lastIndex) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val ctrlX = (p0.x + p1.x) / 2f
                        cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(points.last().x, h)
                    lineTo(points.first().x, h)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.30f),
                            glowColor.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )

                drawPath(
                    path = strokePath,
                    brush = Brush.horizontalGradient(listOf(lineColor, glowColor, lineColor)),
                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                )

                points.forEach { pt ->
                    drawCircle(
                        color = Color.White,
                        radius = 3.6.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 2.2.dp.toPx(),
                        center = pt
                    )
                }
            }
        }
    }
}
