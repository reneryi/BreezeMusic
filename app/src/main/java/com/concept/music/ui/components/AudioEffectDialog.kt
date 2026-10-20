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
 * 兼容旧入口：以全屏独立页面形式承载蝰蛇声学中心，彻底告别局促的底部抽屉弹窗
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

/**
 * 酷狗概念版 · 蝰蛇音效与专业均衡器独立全屏页面
 * 分为两大独立专区页面：
 * 1. 「蝰蛇音效」页：聚合酷狗官方旗舰、3D空间、复古介质、曲风人声、耳机与车载分类音效 + 当前音效详细声学参数精调 + 心流环境音混音
 * 2. 「专业均衡器」页：实时贝塞尔频响曲线可视化画布 + 10段专业精密推杆 + 10款曲风预设 + 前置增益/左右声道平衡/防破音保护
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ViperSoundEffectScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val state by AudioEffectManager.state.collectAsState()
    var selectedTopPage by remember { mutableIntStateOf(0) } // 0: 蝰蛇音效, 1: 专业均衡器
    var selectedCategory by remember { mutableStateOf(ViperEffectCategory.ALL) }

    LaunchedEffect(Unit) {
        AudioEffectManager.refreshOnlineViperEffects()
    }

    val isDark = LocalIsDarkTheme.current
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // 顶部返回、双页面分段导航与总开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                                tint = primaryText
                            )
                        }

                        // 顶部「蝰蛇音效 | 专业均衡器」双页面切换胶囊
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = conceptElevatedSurfaceColor(),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    0 to "蝰蛇音效",
                                    1 to "专业均衡器"
                                ).forEach { (pageIdx, title) ->
                                    val selected = selectedTopPage == pageIdx
                                    val bg by animateColorAsState(
                                        targetValue = if (selected) ConceptPrimary else Color.Transparent,
                                        animationSpec = tween(200),
                                        label = "topPageTabBg_$pageIdx"
                                    )
                                    val txtColor by animateColorAsState(
                                        targetValue = if (selected) Color.White else subText,
                                        animationSpec = tween(200),
                                        label = "topPageTabTxt_$pageIdx"
                                    )
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(17.dp))
                                            .background(bg)
                                            .softClickable { selectedTopPage = pageIdx }
                                            .padding(horizontal = 18.dp)
                                    ) {
                                        Text(
                                            text = title,
                                            color = txtColor,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // DSP 总开关
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
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
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .horizontalCategorySwipe(
                        currentIndex = selectedTopPage,
                        itemCount = 2,
                        onSelectIndex = { selectedTopPage = it },
                        onSwipeRightAtStart = onBack
                    )
            ) {
                AnimatedContent(
                    targetState = selectedTopPage,
                    transitionSpec = {
                        conceptCategoryContentTransition(if (targetState > initialState) 1 else -1)
                    },
                    label = "viperPageSwitch"
                ) { page ->
                    if (page == 0) {
                        ViperEffectsMainPage(
                            state = state,
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it },
                            onSwitchToEq = { selectedTopPage = 1 }
                        )
                    } else {
                        ProfessionalEqualizerPage(
                            state = state
                        )
                    }
                }
            }
        }
    }
}

/**
 * 第一页：蝰蛇音效主页（含当前激活音效看板、详细声学参数精调、6大分类25款酷狗音效库、环境白噪音混音）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViperEffectsMainPage(
    state: AudioEffectState,
    selectedCategory: ViperEffectCategory,
    onSelectCategory: (ViperEffectCategory) -> Unit,
    onSwitchToEq: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val cardBg = conceptFloatingCardColor()
    val elevatedBg = conceptElevatedSurfaceColor()
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant

    var showDetailedKnobs by remember { mutableStateOf(true) }

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶部当前激活音效全息声学头卡
        item {
            ActiveViperHeroCard(
                state = state,
                showDetailedKnobs = showDetailedKnobs,
                onToggleDetailedKnobs = { showDetailedKnobs = !showDetailedKnobs },
                onSwitchToEq = onSwitchToEq
            )
        }

        // 2. 当前音效详细设置精调面板（支持低频下潜、频点、3D空间、人声激励、温暖谐波、动态响度、声场混响）
        item {
            AnimatedVisibility(
                visible = showDetailedKnobs,
                enter = expandVertically(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ViperDetailedParametersCard(state = state)
            }
        }

        // 3. 酷狗蝰蛇音效六大分类导航栏
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "蝰蛇官方声学矩阵 (${allViperPresets.size}款)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText,
                            fontSize = 15.5.sp
                        )
                    )
                    Text(
                        text = selectedCategory.subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ConceptPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
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
                        itemSpacing = 20.dp,
                        fontSize = 13.5.sp,
                        indicatorWidth = 18.dp,
                        fillWidth = false
                    )
                }
            }
        }

        // 4. 分类音效卡片双列宫格
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
                                showDetailedKnobs = true
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

        // 5. 环境白噪音伴音叠加层
        item {
            AmbientFlowOverlaySection(
                cardBg = cardBg,
                elevatedBg = elevatedBg,
                primaryText = primaryText,
                subText = subText
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveViperHeroCard(
    state: AudioEffectState,
    showDetailedKnobs: Boolean,
    onToggleDetailedKnobs: () -> Unit,
    onSwitchToEq: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val activePreset = state.currentPreset
    val isEnabled = state.isEnabled && activePreset != EffectPreset.OFF

    val heroBrush = if (isDark) {
        Brush.linearGradient(
            colors = if (isEnabled) {
                listOf(Color(0xFF13284C), Color(0xFF0F1D36), Color(0xFF16182E))
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
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(heroBrush)
            .border(
                width = 1.dp,
                color = if (isEnabled) ConceptPrimary.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(26.dp)
            )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isEnabled) ConceptPrimary else Color.Gray.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = if (isEnabled) "VIPER DSP ON" else "DIRECT BYPASS",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activePreset.usageBadge,
                            color = VipGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isEnabled) activePreset.label else "原生直通 · 未启用音效",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 21.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEnabled) activePreset.desc else "音频流正以零染色 FastMixer 硬件通道直通输出，点选下方任意音效即可激活",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    )
                }

                IconButton(
                    onClick = onToggleDetailedKnobs,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ConceptPrimary.copy(alpha = 0.14f))
                ) {
                    Icon(
                        imageVector = if (showDetailedKnobs) Icons.Rounded.ExpandLess else Icons.Rounded.Tune,
                        contentDescription = "展开详细设置",
                        tint = ConceptPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 四维声学能量实时仪表条
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    activePreset.acousticTags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ConceptPrimary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "# $tag",
                                color = ConceptPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onSwitchToEq,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "进入专业均衡器 >",
                        color = ConceptPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                    .fillMaxWidth(animatedFrac)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * 当前音效详细设置精调面板（获取并调节音效底层声学参数）
 */
@Composable
private fun ViperDetailedParametersCard(
    state: AudioEffectState
) {
    val context = LocalContext.current
    val cardBg = conceptFloatingCardColor()
    val elevatedBg = conceptElevatedSurfaceColor()
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
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
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = ConceptPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "音效详细参数设置 · ${state.currentPreset.label}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText,
                            fontSize = 14.5.sp
                        )
                    )
                }

                TextButton(
                    onClick = {
                        AudioEffectManager.resetCurrentPresetToDefault()
                        Toast.makeText(context, "已恢复「${state.currentPreset.label}」官方默认调音参数", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("重置参数", fontSize = 11.5.sp, color = ConceptPrimary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. 超重低音下潜强度 + 低频截止频点选择
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
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("低频谐振频点:", fontSize = 11.sp, color = subText)
                listOf(
                    45 to "45Hz 极深下潜",
                    60 to "60Hz 黄金澎湃",
                    85 to "85Hz 紧凑弹性"
                ).forEach { (hz, label) ->
                    val selected = state.bassCutoffHz == hz
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) ConceptCoralAccent.copy(alpha = 0.18f) else elevatedBg,
                        modifier = Modifier.softCardClickable(enabled = state.isEnabled) {
                            AudioEffectManager.setBassCutoffHz(hz)
                        }
                    ) {
                        Text(
                            text = label,
                            color = if (selected) ConceptCoralAccent else subText,
                            fontSize = 10.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 2. 3D 虚拟空间环绕宽度
            ParameterSliderItem(
                title = "3D 全息空间声场宽度 (Spatial Width)",
                valueText = "${state.virtualizerStrength / 10}%",
                value = state.virtualizerStrength.toFloat(),
                valueRange = 0f..1000f,
                enabled = state.isEnabled,
                accentColor = ConceptCyanAccent,
                onValueChange = { AudioEffectManager.setVirtualizerStrength(it.toInt()) }
            )

            // 3. 人声通透度与泛音激励器
            ParameterSliderItem(
                title = "人声通透度与高频空气泛音 (Vocal Exciter)",
                valueText = "${state.vocalClarityStrength / 10}%",
                value = state.vocalClarityStrength.toFloat(),
                valueRange = 0f..1000f,
                enabled = state.isEnabled,
                accentColor = ConceptPrimary,
                onValueChange = { AudioEffectManager.setVocalClarityStrength(it.toInt()) }
            )

            // 4. 黑胶/电子管模拟偶次谐波温暖度
            ParameterSliderItem(
                title = "黑胶与电子管偶次谐波温暖度 (Analog Warmth)",
                valueText = "${state.analogWarmthStrength / 10}%",
                value = state.analogWarmthStrength.toFloat(),
                valueRange = 0f..1000f,
                enabled = state.isEnabled,
                accentColor = VipGold,
                onValueChange = { AudioEffectManager.setAnalogWarmthStrength(it.toInt()) }
            )

            // 5. 动态响度增益
            ParameterSliderItem(
                title = "微动态与响度增益补偿 (Dynamic Gain)",
                valueText = String.format(Locale.getDefault(), "+%.1f dB", state.loudnessGainMb / 100f),
                value = state.loudnessGainMb.toFloat(),
                valueRange = 0f..1500f,
                enabled = state.isEnabled,
                accentColor = ConceptMintAccent,
                onValueChange = { AudioEffectManager.setLoudnessGain(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 6. 空间声场混响房间模型
            Text(
                text = "空间声学混响模型 (Acoustic Reverb)",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = primaryText,
                    fontWeight = FontWeight.Bold
                )
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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.softCardClickable(
                            enabled = state.isEnabled,
                            shape = RoundedCornerShape(12.dp),
                            onClick = { AudioEffectManager.setReverbMode(mode) }
                        )
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isSelected) Color.White else subText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
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
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                )
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
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
                        text = if (isSelected) "使用中" else (onlineUsage ?: preset.usageBadge),
                        color = if (isSelected) Color(0xFF0A1018) else subText,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = preset.label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) catAccent else primaryText,
                    fontSize = 14.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = onlineDesc?.takeIf { it.isNotBlank() } ?: preset.desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = subText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
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
 * 第二页：专业均衡器独立页面（含实时贝塞尔频响曲线画布、10款曲风预设、10段精密推杆、前置增益与声道平衡）
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 实时贝塞尔频响曲线可视化画布 (Real-Time Spline Frequency Response Graph)
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg,
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
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryText
                                )
                            )
                            Text(
                                text = "当前预设：${state.currentPreset.label} · 前置增益 ${String.format(Locale.getDefault(), "%+.1f dB", state.preampGainDb)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = subText)
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
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryText
                    )
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
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText,
                                fontSize = 14.sp
                            )
                        )
                        Text(
                            text = "31Hz — 16kHz",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ConceptPrimary,
                                fontWeight = FontWeight.Bold
                            )
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
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (level != 0) ConceptPrimary else subText,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
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
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = subText,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. 前置放大器增益、左右声道平衡与硬件级防削波保护
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "母带前置放大与声像平衡",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                    )
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "硬件级防削波限幅保护 (Anti-Clipping Headroom)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = primaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "拉高多频段增益时自动预留 -0.6dB 动态余量，彻底杜绝破音与数字毛刺",
                                style = MaterialTheme.typography.labelSmall.copy(color = subText)
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
            .height(136.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(elevatedBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            // 参考网格线 (-10dB, 0dB, +10dB)
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

@Composable
private fun AmbientFlowOverlaySection(
    cardBg: Color,
    elevatedBg: Color,
    primaryText: Color,
    subText: Color
) {
    val ambientState by AmbientSoundManager.state.collectAsState()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "心流环境伴音叠加层 (Ambient DSP Mixer)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = primaryText,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Text(
                        text = "程序化实时合成自然声场，可与任意蝰蛇音效叠加播放",
                        style = MaterialTheme.typography.labelSmall.copy(color = subText)
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

            Spacer(modifier = Modifier.height(10.dp))

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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.softCardClickable(
                            shape = RoundedCornerShape(12.dp),
                            onClick = { AmbientSoundManager.applyQuickScene(key) }
                        )
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = ConceptPrimary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AmbientSoundChannel.entries.forEach { channel ->
                val lvl = ambientState.levelFor(channel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${channel.title} · ${channel.subtitle}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = primaryText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "${(lvl * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (lvl > 0.01f && ambientState.masterEnabled) ConceptPrimary else subText,
                            fontWeight = FontWeight.Bold
                        )
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
