package com.concept.music.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.roundToInt

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

                        // 3. 三大声学分区导航栏（复刻心流舱 FocusPodBottomSheet 灵动弹性滑动胶囊指示器 + 微缩放动效）
                        val pillFraction by animateFloatAsState(
                            targetValue = selectedMainTab.toFloat(),
                            animationSpec = ConceptMotion.expressiveSpring(),
                            label = "viperTabSlidingPill"
                        )
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = conceptElevatedSurfaceColor(),
                            contentColor = primaryText,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                            ) {
                                val tabCount = SoundEffectMainTab.entries.size.coerceAtLeast(1)
                                val tabGap = 6.dp
                                val singleTabWidth = (maxWidth - tabGap * (tabCount - 1)) / tabCount
                                val fractionalPart = abs(pillFraction - pillFraction.roundToInt().toFloat())
                                val stretchScaleX = 1f + 0.08f * kotlin.math.sin(fractionalPart * Math.PI.toFloat())

                                // 灵动滑动胶囊底座（与心流舱同源 ExpressiveMicroSpring）
                                Box(
                                    modifier = Modifier
                                        .width(singleTabWidth)
                                        .height(38.dp)
                                        .graphicsLayer {
                                            translationX = pillFraction * (singleTabWidth.toPx() + tabGap.toPx())
                                            scaleX = stretchScaleX
                                        }
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ConceptPrimary)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(tabGap)
                                ) {
                                    SoundEffectMainTab.entries.forEach { tab ->
                                        val selected = selectedMainTab == tab.index
                                        val tabTextColor by animateColorAsState(
                                            targetValue = if (selected) Color.White else subText,
                                            animationSpec = tween(200),
                                            label = "soundMainTabTxt_${tab.index}"
                                        )
                                        val tabScale by animateFloatAsState(
                                            targetValue = if (selected) 1.04f else 1f,
                                            animationSpec = ConceptMotion.expressiveSpring(),
                                            label = "soundMainTabScale_${tab.index}"
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .softClickable { selectedMainTab = tab.index }
                                                .graphicsLayer {
                                                    scaleX = tabScale
                                                    scaleY = tabScale
                                                }
                                                .padding(horizontal = 6.dp)
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
 * 专区二：声场调音（重新设计清爽专业调音台 UI：自适应双行六列分频频点矩阵、无换行单行参数滑杆、独立分区卡片）
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
        // 卡片 1：低频动态与瞬态微动态引擎
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ConceptCoralAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Speaker,
                                    contentDescription = null,
                                    tint = ConceptCoralAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "低频下潜与瞬态动态",
                                    color = primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "64-Bit 线性低架滤波 · 235Hz 防浑浊净空补偿",
                                    color = subText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ConceptPrimary.copy(alpha = 0.14f),
                            modifier = Modifier.softCardClickable(shape = RoundedCornerShape(10.dp)) {
                                AudioEffectManager.resetCurrentPresetToDefault()
                                Toast.makeText(context, "已恢复当前预设默认声场参数", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = "恢复默认",
                                fontSize = 11.5.sp,
                                color = ConceptPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ParameterSliderItem(
                        title = "超重低音下潜",
                        valueText = "${state.bassBoostStrength / 10}% · ${state.bassCutoffHz}Hz",
                        value = state.bassBoostStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptCoralAccent,
                        onValueChange = { AudioEffectManager.setBassBoostStrength(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "低频瞬态弹性冲击",
                        valueText = "${state.bassPunchStrength / 10}%",
                        value = state.bassPunchStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptAmberAccent,
                        onValueChange = { AudioEffectManager.setBassPunchStrength(it.toInt()) }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "低频分频谐振中心频点",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = subText,
                            maxLines = 1
                        )
                        Text(
                            text = "当前 ${state.bassCutoffHz} Hz",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ConceptCoralAccent,
                            softWrap = false,
                            maxLines = 1
                        )
                    }

                    // 六档等宽分频频点网格（一屏完整展示，彻底消除右侧截断）
                    val cutoffOptions = remember {
                        listOf(
                            Triple(40, "40Hz", "极深潜"),
                            Triple(55, "55Hz", "影院"),
                            Triple(60, "60Hz", "黄金"),
                            Triple(75, "75Hz", "流行"),
                            Triple(95, "95Hz", "紧凑"),
                            Triple(110, "110Hz", "暖厚")
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cutoffOptions.forEach { (hz, hzText, tagText) ->
                            val selected = state.bassCutoffHz == hz
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) ConceptCoralAccent.copy(alpha = 0.20f) else elevatedBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable(enabled = true, shape = RoundedCornerShape(10.dp)) {
                                        AudioEffectManager.setBassCutoffHz(hz)
                                    }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = hzText,
                                        color = if (selected) ConceptCoralAccent else primaryText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = tagText,
                                        color = if (selected) ConceptCoralAccent else subText,
                                        fontSize = 9.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    ParameterSliderItem(
                        title = "瞬态微动态与鼓点清晰度",
                        valueText = "${state.transientAttackStrength / 10}%",
                        value = state.transientAttackStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptMintAccent,
                        onValueChange = { AudioEffectManager.setTransientAttackStrength(it.toInt()) }
                    )
                }
            }
        }

        // 卡片 2：3D 全息声场与双耳交叉馈送
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ConceptCyanAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SurroundSound,
                                contentDescription = null,
                                tint = ConceptCyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "全息声场与双耳渲染",
                                color = primaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "140Hz 低频锚定居中 M/S 拓宽 · Bauer 双耳交叉馈送",
                                color = subText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ParameterSliderItem(
                        title = "3D 环绕声场宽度",
                        valueText = "${state.virtualizerStrength / 10}%",
                        value = state.virtualizerStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptCyanAccent,
                        onValueChange = { AudioEffectManager.setVirtualizerStrength(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "舞台纵深与前后距离感",
                        valueText = "${state.soundstageDepthStrength / 10}%",
                        value = state.soundstageDepthStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptPrimary,
                        onValueChange = { AudioEffectManager.setSoundstageDepthStrength(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "Bauer 耳机交叉馈送 (消除头中效应)",
                        valueText = "${state.crossfeedStrength / 10}%",
                        value = state.crossfeedStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptMintAccent,
                        onValueChange = { AudioEffectManager.setCrossfeedStrength(it.toInt()) }
                    )

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
            }
        }

        // 卡片 3：空间混响声学矩阵 (支持干湿比、预延迟与高频阻尼精调)
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ConceptVioletAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stadium,
                                contentDescription = null,
                                tint = ConceptVioletAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "空间声学混响矩阵",
                                color = primaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "185Hz-6.2kHz 频带净化 · 独立预延迟防人声浑浊",
                                color = subText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = state.reverbMode != ReverbMode.NONE) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            ParameterSliderItem(
                                title = "混响空间干湿比",
                                valueText = "${state.reverbWetMix / 10}%",
                                value = state.reverbWetMix.toFloat(),
                                valueRange = 0f..1000f,
                                enabled = true,
                                accentColor = ConceptPrimary,
                                onValueChange = { AudioEffectManager.setReverbWetMix(it.toInt()) }
                            )

                            ParameterSliderItem(
                                title = "混响预延迟 (人声前置分离)",
                                valueText = "${state.reverbPreDelayMs} ms",
                                value = state.reverbPreDelayMs.toFloat(),
                                valueRange = 0f..80f,
                                enabled = true,
                                accentColor = ConceptCyanAccent,
                                onValueChange = { AudioEffectManager.setReverbPreDelayMs(it.toInt()) }
                            )

                            ParameterSliderItem(
                                title = "空间高频吸收阻尼",
                                valueText = "${state.reverbDamping / 10}%",
                                value = state.reverbDamping.toFloat(),
                                valueRange = 50f..900f,
                                enabled = true,
                                accentColor = ConceptVioletAccent,
                                onValueChange = { AudioEffectManager.setReverbDamping(it.toInt()) }
                            )
                        }
                    }
                }
            }
        }

        // 卡片 4：人声精修与模拟胆机温暖度
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(VipGold.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = VipGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "人声精修与模拟胆机母带",
                                color = primaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "2.75kHz 贴耳人声 · 6.2kHz 齿音柔化 · 胆机线性温润滤波",
                                color = subText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ParameterSliderItem(
                        title = "人声通透与贴耳存在感",
                        valueText = "${state.vocalClarityStrength / 10}%",
                        value = state.vocalClarityStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptPrimary,
                        onValueChange = { AudioEffectManager.setVocalClarityStrength(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "人声齿音柔化抑制 (6.2kHz)",
                        valueText = "${state.deEsserStrength / 10}%",
                        value = state.deEsserStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptCyanAccent,
                        onValueChange = { AudioEffectManager.setDeEsserStrength(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "极高频空气泛音 (12kHz+)",
                        valueText = "${state.trebleAirStrength / 10}%",
                        value = state.trebleAirStrength.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = true,
                        accentColor = ConceptVioletAccent,
                        onValueChange = { AudioEffectManager.setTrebleAirStrength(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "黑胶与黄金胆机温暖度",
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
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "夜间小音量等响度补偿",
                                color = primaryText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "低音量聆听时自动补偿高低频细节 (Fletcher-Munson)",
                                color = subText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
            Text(text = label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(text = "$percent%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = color, softWrap = false, maxLines = 1)
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
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Text(
                text = valueText,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                softWrap = false,
                maxLines = 1
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
                            softWrap = false,
                            maxLines = 1,
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
                                    softWrap = false,
                                    maxLines = 1,
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
 * 专区三：发烧级全能参量均衡器 (Audiophile 10-Band Parametric Equalizer)：
 * 重新设计 UI 布局，彻底解决徽章竖排挤压、频谱绿柱杂乱、副标题双行碰撞与 0.1dB 微调按钮换行问题。
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

    val selectedBandIdx = state.selectedPeqBandIndex.coerceIn(0, 9)
    val selectedBandLevelMb = state.tenBandLevels.getOrElse(selectedBandIdx) { 0 }
    val selectedBandQ = state.tenBandQFactors.getOrElse(selectedBandIdx) { 1.20f }
    val selectedBandHz = state.tenBandCenterHz.getOrElse(selectedBandIdx) { AudioEffectManager.DEFAULT_TEN_BAND_HZ[selectedBandIdx] }
    val selectedBandType = state.tenBandFilterTypes.getOrElse(selectedBandIdx) { com.concept.music.service.PeqFilterType.PEAK }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 实时 Biquad 传递函数频响曲线 + A/B 原音对比
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "参量均衡频响曲线",
                                color = primaryText,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (state.isAbBypassActive) ConceptAmberAccent.copy(alpha = 0.20f) else ConceptCyanAccent.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = if (state.isAbBypassActive) "原音直通" else "实时生效",
                                    color = if (state.isAbBypassActive) ConceptAmberAccent else ConceptCyanAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (state.isAbBypassActive) ConceptAmberAccent else elevatedBg,
                                modifier = Modifier.softCardClickable(shape = RoundedCornerShape(10.dp)) {
                                    AudioEffectManager.setAbBypassActive(!state.isAbBypassActive)
                                }
                            ) {
                                Text(
                                    text = if (state.isAbBypassActive) "A/B: 原音" else "A/B 对比",
                                    color = if (state.isAbBypassActive) Color(0xFF0A1018) else primaryText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.5.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ConceptPrimary.copy(alpha = 0.14f),
                                modifier = Modifier.softCardClickable(shape = RoundedCornerShape(10.dp)) {
                                    AudioEffectManager.resetCurrentPresetToDefault()
                                    Toast.makeText(context, "已重置参量均衡器全部参数", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = "归零",
                                    color = ConceptPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val hpfInfo = if (state.highPassCutoffHz >= 20) " · HPF ${state.highPassCutoffHz}Hz" else ""
                    val lpfInfo = if (state.lowPassCutoffHz < 19500) String.format(Locale.getDefault(), " · LPF %.1fkHz", state.lowPassCutoffHz / 1000f) else ""
                    Text(
                        text = "${state.currentPreset.label} · 前置增益 ${String.format(Locale.getDefault(), "%+.1f dB", state.preampGainDb)}$hpfInfo$lpfInfo",
                        color = subText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    EqFrequencyResponseCurveCanvas(
                        state = state,
                        tenBandLevels = effectiveCurveBands,
                        preampDb = state.preampGainDb,
                        enabled = state.isEnabled && !state.isAbBypassActive,
                        selectedBandIndex = selectedBandIdx,
                        onSelectBand = { AudioEffectManager.setSelectedPeqBandIndex(it) }
                    )
                }
            }
        }

        // 2. 发烧友声学目标参考曲线 & 曲风预设 & 自定义存档槽位 (A / B / C 含载入/存入/删除重置)
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                contentColor = primaryText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "目标参考曲线与自定义存档",
                            color = primaryText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.customPeqSlots.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ConceptCoralAccent.copy(alpha = 0.14f),
                                modifier = Modifier.softCardClickable(shape = RoundedCornerShape(8.dp)) {
                                    AudioEffectManager.clearAllCustomPeqSlots()
                                    Toast.makeText(context, "已清空并重置全部自定义记忆槽", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = "重置全部槽位",
                                    color = ConceptCoralAccent,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "哈曼 / 弥散场 / 记忆槽",
                                color = subText,
                                fontSize = 11.sp,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }

                    // 发烧目标参考曲线横滑栏
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(com.concept.music.service.AudiophileTargetCurve.entries) { target ->
                            val isMatched = state.tenBandLevels == target.tenBandMb &&
                                state.tenBandFilterTypes == target.tenBandTypes
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isMatched) VipGold.copy(alpha = 0.22f) else elevatedBg,
                                modifier = Modifier.softCardClickable(shape = RoundedCornerShape(12.dp)) {
                                    AudioEffectManager.applyAudiophileTargetCurve(target)
                                    Toast.makeText(context, "已载入「${target.label}」: ${target.subtitle}", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoGraph,
                                        contentDescription = null,
                                        tint = if (isMatched) VipGold else ConceptCyanAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = target.label,
                                        color = if (isMatched) VipGold else primaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // 经典曲风预设横滑栏
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(eqPresets) { preset ->
                            val isSelected = state.isEnabled && state.currentPreset == preset
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) ConceptPrimary else elevatedBg,
                                contentColor = if (isSelected) Color.White else primaryText,
                                modifier = Modifier.softCardClickable(
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = { AudioEffectManager.setPreset(preset) }
                                )
                            ) {
                                Text(
                                    text = preset.label,
                                    color = if (isSelected) Color.White else primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // 3 个用户自定义记忆槽位 (槽位 A / B / C：支持点击载入、覆盖存入、独立删除重置)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (slotIdx in 0..2) {
                            val slotChar = ('A' + slotIdx)
                            val savedSlot = state.customPeqSlots.firstOrNull { it.slotIndex == slotIdx }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (savedSlot != null) ConceptPrimary.copy(alpha = 0.14f) else elevatedBg,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .softClickable {
                                                if (savedSlot != null) {
                                                    AudioEffectManager.loadCustomPeqSlot(slotIdx)
                                                    Toast.makeText(context, "已载入调音槽位 $slotChar", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    AudioEffectManager.saveCustomPeqSlot(slotIdx)
                                                    Toast.makeText(context, "已保存当前 PEQ 至槽位 $slotChar", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = "记忆槽 $slotChar",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (savedSlot != null) ConceptPrimary else primaryText,
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (savedSlot != null) "已存 · 载入" else "空 · 存入",
                                            fontSize = 9.5.sp,
                                            color = if (savedSlot != null) ConceptCyanAccent else subText,
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                AudioEffectManager.saveCustomPeqSlot(slotIdx)
                                                Toast.makeText(context, if (savedSlot != null) "已更新记忆槽 $slotChar" else "已存入记忆槽 $slotChar", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Save,
                                                contentDescription = "保存到槽位 $slotChar",
                                                tint = ConceptPrimary,
                                                modifier = Modifier.size(13.5.dp)
                                            )
                                        }
                                        if (savedSlot != null) {
                                            IconButton(
                                                onClick = {
                                                    AudioEffectManager.deleteCustomPeqSlot(slotIdx)
                                                    Toast.makeText(context, "已删除/重置记忆槽 $slotChar", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "删除重置槽位 $slotChar",
                                                    tint = ConceptCoralAccent,
                                                    modifier = Modifier.size(13.5.dp)
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

        // 3. 10段频响推杆矩阵 + 单频段发烧级参量精调工作台 (Frequency / Q-Factor / Filter Topology / 0.1dB Gain)
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
                            text = "10段参量均衡推杆",
                            color = primaryText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "点击频段精调 Q 值与频点",
                            color = ConceptPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            softWrap = false,
                            maxLines = 1
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
                            val isBandSelected = index == selectedBandIdx
                            val bandType = state.tenBandFilterTypes.getOrElse(index) { com.concept.music.service.PeqFilterType.PEAK }
                            val bandAccent = if (isBandSelected) ConceptCyanAccent else ConceptPrimary
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isBandSelected) bandAccent.copy(alpha = 0.10f) else Color.Transparent)
                                    .softClickable { AudioEffectManager.setSelectedPeqBandIndex(index) }
                                    .padding(vertical = 4.dp)
                            ) {
                                val dbVal = level / 100f
                                Text(
                                    text = if (level > 0) String.format(Locale.getDefault(), "+%.1f", dbVal)
                                    else if (level < 0) String.format(Locale.getDefault(), "%.1f", dbVal)
                                    else "0.0",
                                    color = if (isBandSelected) bandAccent else if (level != 0) ConceptPrimary else subText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    softWrap = false,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .height(144.dp)
                                        .width(28.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isBandSelected) bandAccent.copy(alpha = 0.18f) else elevatedBg),
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
                                            thumbColor = bandAccent,
                                            activeTrackColor = bandAccent,
                                            inactiveTrackColor = bandAccent.copy(alpha = 0.16f)
                                        ),
                                        modifier = Modifier
                                            .requiredWidth(130.dp)
                                            .requiredHeight(30.dp)
                                            .graphicsLayer {
                                                rotationZ = -90f
                                            }
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = AudioEffectManager.TEN_BAND_FREQUENCIES.getOrElse(index) { "" },
                                    color = if (isBandSelected) bandAccent else subText,
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isBandSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    softWrap = false,
                                    maxLines = 1
                                )
                                Text(
                                    text = bandType.shortTag,
                                    color = if (isBandSelected) bandAccent else subText.copy(alpha = 0.65f),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 单频段发烧级参量精调工作台 (Selected Band Parametric Inspector)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = elevatedBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ConceptCyanAccent.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "BAND ${selectedBandIdx + 1}",
                                            color = ConceptCyanAccent,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val freqLabel = if (selectedBandHz >= 1000f) {
                                        String.format(Locale.getDefault(), "%.2fkHz", selectedBandHz / 1000f)
                                    } else {
                                        String.format(Locale.getDefault(), "%.0fHz", selectedBandHz)
                                    }
                                    Text(
                                        text = "$freqLabel · Q=${String.format(Locale.getDefault(), "%.2f", selectedBandQ)}",
                                        color = primaryText,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.5.sp,
                                        softWrap = false,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // 0.1dB 精密增益步进器（紧凑不换行）
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = cardBg,
                                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(8.dp)) {
                                            AudioEffectManager.setTenBandLevel(selectedBandIdx, selectedBandLevelMb - 10)
                                        }
                                    ) {
                                        Text(
                                            text = "-0.1",
                                            color = primaryText,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = String.format(Locale.getDefault(), "%+.1fdB", selectedBandLevelMb / 100f),
                                        color = ConceptCyanAccent,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        softWrap = false,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = cardBg,
                                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(8.dp)) {
                                            AudioEffectManager.setTenBandLevel(selectedBandIdx, selectedBandLevelMb + 10)
                                        }
                                    ) {
                                        Text(
                                            text = "+0.1",
                                            color = primaryText,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // 滤波器拓扑类型选择 (Peak / Low-Shelf / High-Shelf / Notch / Band-Pass)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(com.concept.music.service.PeqFilterType.entries) { fType ->
                                    val selected = selectedBandType == fType
                                    Surface(
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (selected) ConceptPrimary else cardBg,
                                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(9.dp)) {
                                            AudioEffectManager.setTenBandFilterType(selectedBandIdx, fType)
                                        }
                                    ) {
                                        Text(
                                            text = fType.label,
                                            color = if (selected) Color.White else subText,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            // 中心频率连续微调滑杆
                            val nominalHz = AudioEffectManager.DEFAULT_TEN_BAND_HZ[selectedBandIdx]
                            val minFreq = (nominalHz * 0.55f).coerceAtLeast(20f)
                            val maxFreq = (nominalHz * 1.80f).coerceAtMost(20000f)
                            ParameterSliderItem(
                                title = "中心频点微调",
                                valueText = if (selectedBandHz >= 1000f) String.format(Locale.getDefault(), "%.2f kHz", selectedBandHz / 1000f)
                                else String.format(Locale.getDefault(), "%.1f Hz", selectedBandHz),
                                value = selectedBandHz.coerceIn(minFreq, maxFreq),
                                valueRange = minFreq..maxFreq,
                                enabled = true,
                                accentColor = ConceptCyanAccent,
                                onValueChange = { AudioEffectManager.setTenBandCenterFrequency(selectedBandIdx, it) }
                            )

                            // 品质因数 Q 值滑杆与快捷档位
                            ParameterSliderItem(
                                title = "品质因数 Q (频带宽度)",
                                valueText = String.format(Locale.getDefault(), "Q = %.2f · %s", selectedBandQ, when {
                                    selectedBandQ < 0.85f -> "宽频柔顺"
                                    selectedBandQ <= 1.8f -> "标准均衡"
                                    selectedBandQ <= 3.5f -> "窄带聚焦"
                                    else -> "手术刀陷波"
                                }),
                                value = selectedBandQ.coerceIn(0.30f, 8.00f),
                                valueRange = 0.30f..8.00f,
                                enabled = true,
                                accentColor = ConceptVioletAccent,
                                onValueChange = { AudioEffectManager.setTenBandQFactor(selectedBandIdx, it) }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    0.71f to "0.71 宽频",
                                    1.20f to "1.20 标准",
                                    2.50f to "2.50 窄带",
                                    5.00f to "5.00 极窄"
                                ).forEach { (qPreset, qLabel) ->
                                    val isQSelected = abs(selectedBandQ - qPreset) < 0.08f
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isQSelected) ConceptVioletAccent.copy(alpha = 0.22f) else cardBg,
                                        modifier = Modifier
                                            .weight(1f)
                                            .softCardClickable(shape = RoundedCornerShape(8.dp)) {
                                                AudioEffectManager.setTenBandQFactor(selectedBandIdx, qPreset)
                                            }
                                    ) {
                                        Text(
                                            text = qLabel,
                                            color = if (isQSelected) ConceptVioletAccent else subText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.padding(vertical = 5.5.dp, horizontal = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. 高低通边界滤波 (HPF / LPF) + 前置增益 + 声道平衡 + 防破音保护
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
                            text = "高低通滤波与母带增益控制",
                            color = primaryText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (abs(state.channelBalance) > 0.02f || abs(state.preampGainDb) > 0.05f || state.highPassCutoffHz > 0 || state.lowPassCutoffHz < 20000) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ConceptPrimary.copy(alpha = 0.14f),
                                modifier = Modifier.softCardClickable(shape = RoundedCornerShape(10.dp)) {
                                    AudioEffectManager.setPreampGainDb(0f)
                                    AudioEffectManager.setChannelBalance(0f)
                                    AudioEffectManager.setHighPassCutoffHz(0)
                                    AudioEffectManager.setLowPassCutoffHz(20000)
                                }
                            ) {
                                Text(
                                    text = "全部归零",
                                    color = ConceptPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    ParameterSliderItem(
                        title = "高通亚音速滤波 (HPF)",
                        valueText = if (state.highPassCutoffHz < 20) "关闭 · 全频直通" else "${state.highPassCutoffHz} Hz (-12dB/oct)",
                        value = state.highPassCutoffHz.toFloat(),
                        valueRange = 0f..100f,
                        enabled = true,
                        accentColor = ConceptCoralAccent,
                        onValueChange = { AudioEffectManager.setHighPassCutoffHz(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "低通极高频滚降 (LPF)",
                        valueText = if (state.lowPassCutoffHz >= 19500) "20.0 kHz · 全频直通" else String.format(Locale.getDefault(), "%.1f kHz (-12dB/oct)", state.lowPassCutoffHz / 1000f),
                        value = state.lowPassCutoffHz.toFloat(),
                        valueRange = 8000f..20000f,
                        enabled = true,
                        accentColor = ConceptVioletAccent,
                        onValueChange = { AudioEffectManager.setLowPassCutoffHz(it.toInt()) }
                    )

                    ParameterSliderItem(
                        title = "前置放大增益 (Preamp)",
                        valueText = String.format(Locale.getDefault(), "%+.1f dB", state.preampGainDb),
                        value = state.preampGainDb,
                        valueRange = -12f..12f,
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
                        title = "左右声道平衡 (Balance)",
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
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = "双声道耦合真包络防削波保护",
                                color = primaryText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "智能峰值余量校准 + 双声道平滑包络控峰，彻底杜绝破音",
                                color = subText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
    state: AudioEffectState,
    tenBandLevels: List<Int>,
    preampDb: Float,
    enabled: Boolean,
    selectedBandIndex: Int = 0,
    onSelectBand: (Int) -> Unit = {}
) {
    val elevatedBg = conceptElevatedSurfaceColor()
    val lineColor = if (enabled) ConceptPrimary else Color.Gray
    val glowColor = if (enabled) ConceptCyanAccent else Color.Gray

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(144.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(elevatedBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            // 1. 绘制 +10dB / 0dB / -10dB 水平参考网格线与十段频点垂直参考虚线
            listOf(0.14f, 0.5f, 0.86f).forEach { frac ->
                val y = h * frac
                drawLine(
                    color = Color.White.copy(alpha = if (frac == 0.5f) 0.22f else 0.07f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (frac == 0.5f) 1.2.dp.toPx() else 0.8.dp.toPx()
                )
            }

            val minLogF = kotlin.math.ln(20.0)
            val maxLogF = kotlin.math.ln(20000.0)
            for (idx in 0 until 10) {
                val fc = AudioEffectManager.DEFAULT_TEN_BAND_HZ[idx].toDouble()
                val xFrac = ((kotlin.math.ln(fc) - minLogF) / (maxLogF - minLogF)).toFloat().coerceIn(0.02f, 0.98f)
                val gx = w * xFrac
                drawLine(
                    color = if (idx == selectedBandIndex) ConceptCyanAccent.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.05f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = if (idx == selectedBandIndex) 1.2.dp.toPx() else 0.7.dp.toPx()
                )
            }

            // 2. 计算 96 个对数频率采样点 (20Hz ~ 20kHz) 上的真实 Biquad 传递函数幅频响应 |H(e^jw)|
            val sampleCount = 96
            val strokePath = Path()
            for (i in 0 until sampleCount) {
                val frac = i.toFloat() / (sampleCount - 1)
                val freqHz = kotlin.math.exp(minLogF + frac * (maxLogF - minLogF))
                val peqDb = if (enabled) {
                    AudioEffectManager.evaluatePeqMagnitudeDbAtFrequency(freqHz, state, 48000.0)
                } else 0f
                val totalDb = (peqDb + (if (enabled) preampDb * 0.45f else 0f)).coerceIn(-14f, 14f)
                val x = w * frac
                val y = midY - (totalDb / 14f) * (h * 0.42f)
                if (i == 0) {
                    strokePath.moveTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                }
            }

            val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.28f),
                        glowColor.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                )
            )

            drawPath(
                path = strokePath,
                brush = Brush.horizontalGradient(listOf(lineColor, glowColor, ConceptVioletAccent)),
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
            )

            // 3. 绘制 10 个频段的控制锚点（高亮当前选中的精调频段）
            val bands = if (tenBandLevels.size == 10) tenBandLevels else List(10) { 0 }
            for (idx in 0 until 10) {
                val fc = state.tenBandCenterHz.getOrElse(idx) { AudioEffectManager.DEFAULT_TEN_BAND_HZ[idx] }.toDouble().coerceIn(20.0, 20000.0)
                val logXFrac = ((kotlin.math.ln(fc) - minLogF) / (maxLogF - minLogF)).toFloat().coerceIn(0.02f, 0.98f)
                val nodeDb = if (enabled) {
                    AudioEffectManager.evaluatePeqMagnitudeDbAtFrequency(fc, state, 48000.0) + preampDb * 0.45f
                } else {
                    bands[idx] / 100f
                }
                val pt = Offset(
                    x = w * logXFrac,
                    y = midY - (nodeDb.coerceIn(-14f, 14f) / 14f) * (h * 0.42f)
                )
                val isSel = idx == selectedBandIndex
                if (isSel) {
                    drawCircle(
                        color = ConceptCyanAccent.copy(alpha = 0.32f),
                        radius = 7.5.dp.toPx(),
                        center = pt
                    )
                }
                drawCircle(
                    color = if (isSel) ConceptCyanAccent else Color.White,
                    radius = if (isSel) 4.4.dp.toPx() else 3.2.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = if (isSel) Color(0xFF0A1018) else lineColor,
                    radius = 1.8.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}
