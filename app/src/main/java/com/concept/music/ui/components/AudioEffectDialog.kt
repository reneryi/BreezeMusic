package com.concept.music.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.concept.music.service.AmbientSoundChannel
import com.concept.music.service.AmbientSoundManager
import com.concept.music.service.AudioEffectManager
import com.concept.music.service.AudioEffectState
import com.concept.music.service.EffectPreset
import com.concept.music.service.ReverbMode
import com.concept.music.service.ViperEffectCategory
import com.concept.music.ui.theme.*
import java.util.Locale
import kotlin.math.abs

/**
 * 兼容旧入口：以全屏独立页面形式承载蝰蛇声学中心
 */
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
    val subtitle: String,
    val icon: ImageVector
) {
    PRESET_LIBRARY(0, "蝰蛇声效库", "6大分类24款官方音效", Icons.Rounded.GraphicEq),
    ACOUSTIC_TUNING(1, "声场与调音", "低音/3D环绕/人声/混响", Icons.Rounded.SurroundSound),
    PARAMETRIC_EQ(2, "参量均衡器", "10段频响/声像/母带限幅", Icons.Rounded.Equalizer),
    AMBIENT_MIXER(3, "环境伴音舱", "4路自然白噪音混音", Icons.Rounded.Spa)
}

/**
 * 酷狗概念版 · 蝰蛇声学与母带调音中心（全新四维分类架构）
 * 将原先冗长堆叠的内容科学归类为四大独立专区：
 * 1. 「蝰蛇声效库」：酷狗官方旗舰、3D空间、复古介质、曲风人声、耳机与车载 6 大分类音效矩阵
 * 2. 「声场与调音」：四维声学仪表 + 超重低音下潜与频点 + 3D全息声场 + 空间混响模型 + 人声泛音激励 + 电子管温暖度 + 夜间等响度听感补偿
 * 3. 「参量均衡器」：实时贝塞尔频响曲线画布 + 11款曲风预设 + 10段精密推杆 + 母带前置放大 + 左右声道平衡 + 硬件防破音限幅
 * 4. 「环境伴音舱」：程序化 DSP 合成 4 路自然声景（雨夜/黑胶/篝火/潮汐）+ 随音乐同步启停控制
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ViperSoundEffectScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val state by AudioEffectManager.state.collectAsState()
    val ambientState by AmbientSoundManager.state.collectAsState()
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
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
                                        text = "蝰蛇声学与母带调音台",
                                        color = primaryText,
                                        fontSize = 17.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = if (state.isEnabled) "当前激活：${state.currentPreset.label}" else "直通模式 · 未启用 DSP 染色",
                                        color = if (state.isEnabled) ConceptPrimary else subText,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (state.isEnabled) "DSP 已开启" else "已旁路",
                                    color = if (state.isEnabled) ConceptPrimary else subText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Switch(
                                    checked = state.isEnabled,
                                    onCheckedChange = { AudioEffectManager.setEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = ConceptPrimary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. 紧凑型全息声学状态摘要条
                        CompactAcousticSummaryBanner(
                            state = state,
                            ambientEnabled = ambientState.masterEnabled && ambientState.hasActiveChannel,
                            selectedTab = selectedMainTab,
                            onSelectTab = { selectedMainTab = it }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. 四大声学分区导航胶囊栏
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = conceptElevatedSurfaceColor(),
                            contentColor = primaryText,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SoundEffectMainTab.entries.forEach { tab ->
                                    val selected = selectedMainTab == tab.index
                                    val tabBg by animateColorAsState(
                                        targetValue = if (selected) ConceptPrimary else Color.Transparent,
                                        animationSpec = tween(200),
                                        label = "soundMainTabBg_${tab.index}"
                                    )
                                    val tabTextColor by animateColorAsState(
                                        targetValue = if (selected) Color.White else subText,
                                        animationSpec = tween(200),
                                        label = "soundMainTabTxt_${tab.index}"
                                    )
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(tabBg)
                                            .softClickable { selectedMainTab = tab.index }
                                            .padding(vertical = 9.dp, horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = tab.title,
                                            color = tabTextColor,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .horizontalCategorySwipe(
                            currentIndex = selectedMainTab,
                            itemCount = SoundEffectMainTab.entries.size,
                            onSelectIndex = { selectedMainTab = it },
                            onSwipeRightAtStart = onBack
                        )
                ) {
                    AnimatedContent(
                        targetState = selectedMainTab,
                        transitionSpec = {
                            conceptCategoryContentTransition(if (targetState > initialState) 1 else -1)
                        },
                        label = "viperFourTabSwitch"
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
                            2 -> ProfessionalEqualizerPage(
                                state = state
                            )
                            else -> AmbientMixerDedicatedTab()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶部紧凑型全息声学状态看板：一眼总览当前音效、声场指标、均衡器增益与伴音状态
 */
@Composable
private fun CompactAcousticSummaryBanner(
    state: AudioEffectState,
    ambientEnabled: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val isEnabled = state.isEnabled && state.currentPreset != EffectPreset.OFF
    val bannerBrush = if (isDark) {
        Brush.linearGradient(
            colors = if (isEnabled) {
                listOf(Color(0xFF14284C), Color(0xFF0F1D38), Color(0xFF161A30))
            } else {
                listOf(Color(0xFF141B29), Color(0xFF101520))
            }
        )
    } else {
        Brush.linearGradient(
            colors = if (isEnabled) {
                listOf(Color(0xFFE3F2FF), Color(0xFFEDE7FF), Color(0xFFE0F7FA))
            } else {
                listOf(Color(0xFFF3F7FC), Color(0xFFEEF3FA))
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bannerBrush)
            .border(
                width = 1.dp,
                color = if (isEnabled) ConceptPrimary.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AcousticMetricMiniBar(
                    label = "低频下潜",
                    percent = if (isEnabled) state.bassBoostStrength / 10 else 0,
                    color = ConceptCoralAccent,
                    modifier = Modifier.weight(1f)
                )
                AcousticMetricMiniBar(
                    label = "3D声场",
                    percent = if (isEnabled) state.virtualizerStrength / 10 else 0,
                    color = ConceptCyanAccent,
                    modifier = Modifier.weight(1f)
                )
                AcousticMetricMiniBar(
                    label = "人声解析",
                    percent = if (isEnabled) state.vocalClarityStrength / 10 else 0,
                    color = ConceptPrimary,
                    modifier = Modifier.weight(1f)
                )
                AcousticMetricMiniBar(
                    label = "温暖谐波",
                    percent = if (isEnabled) state.analogWarmthStrength / 10 else 0,
                    color = VipGold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadgeChip(
                        text = if (state.equalLoudnessCompensation) "等响度补偿已开" else "标准响度曲线",
                        active = state.equalLoudnessCompensation,
                        accent = ConceptMintAccent,
                        onClick = { onSelectTab(1) }
                    )
                    StatusBadgeChip(
                        text = if (abs(state.channelBalance) > 0.02f) {
                            val pct = (state.channelBalance * 100).toInt()
                            if (pct < 0) "声像 L${abs(pct)}%" else "声像 R$pct%"
                        } else "声像居中",
                        active = abs(state.channelBalance) > 0.02f,
                        accent = ConceptCyanAccent,
                        onClick = { onSelectTab(2) }
                    )
                    if (ambientEnabled) {
                        StatusBadgeChip(
                            text = "环境伴音混音中",
                            active = true,
                            accent = VipGold,
                            onClick = { onSelectTab(3) }
                        )
                    }
                }

                Text(
                    text = state.currentPreset.usageBadge,
                    color = VipGold,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatusBadgeChip(
    text: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        modifier = Modifier.softClickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/**
 * 专区一：蝰蛇声效库 (6 大分类 24 款官方音效矩阵)
 */
@Composable
private fun ViperPresetLibraryTab(
    state: AudioEffectState,
    selectedCategory: ViperEffectCategory,
    onSelectCategory: (ViperEffectCategory) -> Unit,
    onJumpToTuning: () -> Unit
) {
    val context = LocalContext.current
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 分类筛选栏
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "酷狗蝰蛇官方声效矩阵 (${filteredPresets.size}款)",
                            color = primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = selectedCategory.subtitle,
                            color = subText,
                            fontSize = 11.5.sp
                        )
                    }

                    TextButton(
                        onClick = onJumpToTuning,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = ConceptPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "精调当前参数 ›",
                            color = ConceptPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

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
        }

        // 双列音效宫格
        val presetRows = filteredPresets.chunked(2)
        items(presetRows) { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowPresets.forEach { preset ->
                    val isSelected = state.isEnabled && state.currentPreset == preset
                    val onlineMeta = state.onlineEffectMeta[preset.modeId]
                    ViperEffectPresetCard(
                        preset = preset,
                        onlineUsage = onlineMeta?.usageText,
                        onlineDesc = onlineMeta?.description,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                onJumpToTuning()
                            } else {
                                AudioEffectManager.setPreset(preset)
                                Toast.makeText(context, "已启用蝰蛇音效：${preset.label}", Toast.LENGTH_SHORT).show()
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
 * 专区二：声场与调音 (低频下潜、3D全息声场、混响房间、人声激励、电子管谐波、等响度夜间听感补偿)
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 卡片 1：超重低音下潜与 3D 空间声场混响
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
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
                            Column {
                                Text(
                                    text = "低频下潜与 3D 空间声场",
                                    color = primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "调节当前「${state.currentPreset.label}」的物理低音谐振与三维环绕声场",
                                    color = subText,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                AudioEffectManager.resetCurrentPresetToDefault()
                                Toast.makeText(context, "已恢复「${state.currentPreset.label}」默认声场参数", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("恢复默认", fontSize = 11.5.sp, color = ConceptPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ParameterSliderItem(
                        title = "超重低音下潜深度 (Sub-Bass)",
                        valueText = "${state.bassBoostStrength / 10}% · ${state.bassCutoffHz}Hz",
                        value = state.bassBoostStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = state.isEnabled,
                        accentColor = ConceptCoralAccent,
                        onValueChange = { AudioEffectManager.setBassBoostStrength(it.toInt()) }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("低频谐振频点:", fontSize = 11.5.sp, color = subText)
                        listOf(
                            45 to "45Hz 极深下潜",
                            60 to "60Hz 黄金澎湃",
                            85 to "85Hz 紧凑弹性"
                        ).forEach { (hz, label) ->
                            val selected = state.bassCutoffHz == hz
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) ConceptCoralAccent.copy(alpha = 0.18f) else elevatedBg,
                                contentColor = if (selected) ConceptCoralAccent else subText,
                                modifier = Modifier.softCardClickable(enabled = state.isEnabled) {
                                    AudioEffectManager.setBassCutoffHz(hz)
                                }
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) ConceptCoralAccent else subText,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    ParameterSliderItem(
                        title = "3D 全息空间声场宽度 (Spatial Width)",
                        valueText = "${state.virtualizerStrength / 10}%",
                        value = state.virtualizerStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = state.isEnabled,
                        accentColor = ConceptCyanAccent,
                        onValueChange = { AudioEffectManager.setVirtualizerStrength(it.toInt()) }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "空间声学混响房间模型 (Acoustic Reverb)",
                        color = primaryText,
                        fontSize = 12.5.sp,
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
                                    enabled = state.isEnabled,
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

        // 卡片 2：人声泛音激励、模拟胆机温暖度与等响度听感补偿
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = VipGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "人声激励、模拟胆机与动态响度",
                                color = primaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "精细雕刻人声齿音空气感、黑胶偶次谐波与小音量等响度补偿",
                                color = subText,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ParameterSliderItem(
                        title = "人声通透度与高频空气泛音 (Vocal Exciter)",
                        valueText = "${state.vocalClarityStrength / 10}%",
                        value = state.vocalClarityStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = state.isEnabled,
                        accentColor = ConceptPrimary,
                        onValueChange = { AudioEffectManager.setVocalClarityStrength(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "黑胶与电子管偶次谐波温暖度 (Analog Warmth)",
                        valueText = "${state.analogWarmthStrength / 10}%",
                        value = state.analogWarmthStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = state.isEnabled,
                        accentColor = VipGold,
                        onValueChange = { AudioEffectManager.setAnalogWarmthStrength(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "微动态与响度增益补偿 (Dynamic Gain)",
                        valueText = String.format(Locale.getDefault(), "+%.1f dB", state.loudnessGainMb / 100f),
                        value = state.loudnessGainMb.toFloat(),
                        valueRange = 0f..1500f,
                        enabled = state.isEnabled,
                        accentColor = ConceptMintAccent,
                        onValueChange = { AudioEffectManager.setLoudnessGain(it.toInt()) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = primaryText.copy(alpha = 0.08f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "夜间小音量等响度听感补偿 (Fletcher-Munson)",
                                color = primaryText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "根据人耳等响度曲线自动补偿极低频与高频泛音，深夜低音量聆听依然饱满通透",
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
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "$percent%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFrac.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
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
                fontSize = 12.5.sp
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
            modifier = Modifier.height(34.dp)
        )
    }
}

@Composable
private fun ViperEffectPresetCard(
    preset: EffectPreset,
    onlineUsage: String?,
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
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) catAccent.copy(alpha = if (isDark) 0.20f else 0.14f) else cardBg,
        contentColor = primaryText,
        modifier = modifier
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) catAccent else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .softCardClickable(shape = RoundedCornerShape(20.dp), onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                        .clip(RoundedCornerShape(11.dp))
                        .background(catAccent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = preset.label,
                        tint = catAccent,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) catAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                ) {
                    Text(
                        text = if (isSelected) "使用中 · 点击精调" else (onlineUsage ?: preset.usageBadge),
                        color = if (isSelected) Color(0xFF0A1018) else subText,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = preset.label,
                color = if (isSelected) catAccent else primaryText,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.5.sp,
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

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                preset.acousticTags.take(2).forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = catAccent.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = tag,
                            color = catAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 专区三：参量均衡器（含实时贝塞尔频响曲线画布、11款曲风预设、10段精密推杆、前置增益与实时左右声道平衡）
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 实时贝塞尔频响曲线可视化画布
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
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
                                text = "实时声学频响曲线 (Frequency Response)",
                                color = primaryText,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp
                            )
                            Text(
                                text = "当前预设：${state.currentPreset.label} · 前置增益 ${String.format(Locale.getDefault(), "%+.1f dB", state.preampGainDb)}",
                                color = subText,
                                fontSize = 11.5.sp
                            )
                        }

                        TextButton(
                            onClick = {
                                AudioEffectManager.setPreset(EffectPreset.FLAT)
                                AudioEffectManager.setPreampGainDb(0f)
                                Toast.makeText(context, "已重置为 0dB 平直参考均衡", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("归零重置", color = ConceptPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    EqFrequencyResponseCurveCanvas(
                        tenBandLevels = state.tenBandLevels,
                        preampDb = state.preampGainDb,
                        enabled = state.isEnabled
                    )
                }
            }
        }

        // 2. 专业均衡器曲风预设横向栏
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "专业均衡器曲风预设",
                    color = primaryText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(eqPresets) { preset ->
                        val isSelected = state.currentPreset == preset
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) ConceptPrimary else cardBg,
                            contentColor = if (isSelected) Color.White else primaryText,
                            modifier = Modifier.softCardClickable(
                                shape = RoundedCornerShape(14.dp),
                                onClick = { AudioEffectManager.setPreset(preset) }
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = preset.label,
                                    color = if (isSelected) Color.White else primaryText,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                                Text(
                                    text = preset.usageBadge,
                                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else subText,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 10频段专业精密垂直推杆矩阵
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
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
                            text = "10段精密参量推杆 (-10dB ~ +10dB)",
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
                                    text = if (level > 0) String.format(Locale.getDefault(), "+%.0f", dbVal)
                                    else String.format(Locale.getDefault(), "%.0f", dbVal),
                                    color = if (level != 0) ConceptPrimary else subText,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .height(148.dp)
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
                                        enabled = state.isEnabled,
                                        colors = SliderDefaults.colors(
                                            thumbColor = ConceptPrimary,
                                            activeTrackColor = ConceptPrimary,
                                            inactiveTrackColor = ConceptPrimary.copy(alpha = 0.16f)
                                        ),
                                        modifier = Modifier
                                            .requiredWidth(134.dp)
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

        // 4. 前置放大器增益、左右声道平衡（实时PCM矩阵处理）与硬件级防削波保护
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "母带前置放大与声像平衡",
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
                                Text("声像/增益归零", color = ConceptPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    ParameterSliderItem(
                        title = "前置放大增益 (Preamp Gain)",
                        valueText = String.format(Locale.getDefault(), "%+.1f dB", state.preampGainDb),
                        value = state.preampGainDb,
                        valueRange = -6f..6f,
                        enabled = state.isEnabled,
                        accentColor = ConceptPrimary,
                        onValueChange = { AudioEffectManager.setPreampGainDb(it) }
                    )

                    val balPercent = (state.channelBalance * 100).toInt()
                    val balLabel = when {
                        abs(balPercent) <= 2 -> "居中平衡 (L = R)"
                        balPercent < 0 -> "偏左 L ${abs(balPercent)}%"
                        else -> "偏右 R $balPercent%"
                    }
                    ParameterSliderItem(
                        title = "左右声道声像平衡 (Stereo Balance)",
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
                                text = "硬件级防削波限幅保护 (Anti-Clipping Headroom)",
                                color = primaryText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "拉高多频段增益时自动预留动态余量并启用软拐点限幅，彻底杜绝破音毛刺",
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

/**
 * 专区四：环境伴音舱（独立分类页面：4 路实时合成白噪音 + 快捷场景 + 随音乐播放同步启停开关）
 */
@Composable
private fun AmbientMixerDedicatedTab() {
    val cardBg = conceptFloatingCardColor()
    val elevatedBg = conceptElevatedSurfaceColor()
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant
    val ambientState by AmbientSoundManager.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "心流环境伴音混音舱 (Ambient DSP)",
                                color = primaryText,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.5.sp
                            )
                            Text(
                                text = "采用实时粉红噪声滤波与包络算法合成自然声景，可与音乐叠加播放",
                                color = subText,
                                fontSize = 11.5.sp
                            )
                        }
                        Switch(
                            checked = ambientState.masterEnabled,
                            onCheckedChange = { AmbientSoundManager.setMasterEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ConceptPrimary
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = primaryText.copy(alpha = 0.08f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "随音乐播放同步启停",
                                color = primaryText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = if (ambientState.syncWithMusic) "音乐暂停时自动暂停环境音；关闭此项可作为独立助眠/专注白噪音使用"
                                else "独立白噪音模式：即使暂停音乐也持续播放自然环境声",
                                color = subText,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = ambientState.syncWithMusic,
                            onCheckedChange = { AmbientSoundManager.setSyncWithMusic(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ConceptCyanAccent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "一键氛围声景预设",
                        color = primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val quickScenes = listOf(
                        "rain_study" to "🌧️ 雨夜自习",
                        "retro_vinyl" to "💿 黑胶茶歇",
                        "camp_night" to "🔥 篝火露营",
                        "deep_ocean" to "🌊 深海冥想"
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickScenes) { (key, label) ->
                            Surface(
                                color = elevatedBg,
                                contentColor = ConceptPrimary,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.softCardClickable(
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = { AmbientSoundManager.applyQuickScene(key) }
                                )
                            ) {
                                Text(
                                    text = label,
                                    color = ConceptPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4 路分轨独立混音台
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "四路独立环境声道推子",
                        color = primaryText,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )

                    AmbientSoundChannel.entries.forEach { channel ->
                        val lvl = ambientState.levelFor(channel)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = channel.title,
                                        color = primaryText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = channel.subtitle,
                                        color = subText,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = "${(lvl * 100).toInt()}%",
                                    color = if (lvl > 0.01f && ambientState.masterEnabled) ConceptPrimary else subText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Slider(
                                value = lvl,
                                onValueChange = { AmbientSoundManager.setChannelLevel(channel, it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ConceptPrimary,
                                    activeTrackColor = ConceptPrimary,
                                    inactiveTrackColor = ConceptPrimary.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.height(32.dp)
                            )
                        }
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
            .height(136.dp)
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
                val y = midY - normalized * (h * 0.38f)
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
                            lineColor.copy(alpha = 0.34f),
                            glowColor.copy(alpha = 0.08f),
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
                        radius = 3.5.dp.toPx(),
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
