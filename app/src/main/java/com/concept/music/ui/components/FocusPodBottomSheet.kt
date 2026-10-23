package com.concept.music.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import com.concept.music.ui.theme.ConceptMotion
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.concept.music.service.AmbientSoundChannel
import com.concept.music.service.AmbientSoundManager
import com.concept.music.service.FocusPodManager
import com.concept.music.service.FocusPreset
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptVioletAccent
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable

/**
 * 「心流舱 · Focus Pod & 环境伴音舱」重构页面：
 * - 将原音效页中的「环境伴音舱」整体迁移至此，与番茄心流专注深度融合；
 * - 采用双模块分区切换（心流专注 / 环境伴音混音）与清晰卡片排版，阅读与操控体验更佳。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusPodBottomSheet(
    onDismiss: () -> Unit,
    onPlaySceneMusic: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    LaunchedEffect(Unit) {
        FocusPodManager.init(context)
    }
    val state by FocusPodManager.state.collectAsState()
    val ambientState by AmbientSoundManager.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val dismissWithAnimation: () -> Unit = remember(coroutineScope, sheetState, onDismiss) {
        {
            coroutineScope.launch {
                runCatching { sheetState.hide() }
                onDismiss()
            }
        }
    }
    var selectedSection by remember { mutableIntStateOf(0) }

    val podAccent = if (isDark) Color(0xFF9688FF) else ConceptVioletAccent
    val sheetBgColor = if (isDark) Color(0xFF111524) else Color(0xFFF6F5FF)
    val cardSurfaceColor = if (isDark) Color(0xFF191F34) else Color.White
    val subSurfaceColor = if (isDark) Color(0xFF222942) else Color(0xFFEEEBFF)
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant

    val timerCardBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF222042),
                Color(0xFF181C34),
                Color(0xFF14182B)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFEDE8FF),
                Color(0xFFE8F1FF),
                Color(0xFFF4F1FF)
            )
        )
    }
    val timerTitleColor = if (isDark) Color.White else Color(0xFF181532)
    val timerSubColor = if (isDark) Color.White.copy(alpha = 0.72f) else Color(0xFF564F7A)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = sheetBgColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // 1. 顶部标题与状态概览
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = podAccent.copy(alpha = 0.16f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SelfImprovement,
                                contentDescription = null,
                                tint = podAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "心流舱",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "今日专注 ${state.completedPodsToday} 次 · 累计 ${state.totalFocusMinutesToday} 分钟",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = podAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
                IconButton(onClick = dismissWithAnimation) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = subText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. 顶部双模块切换栏：「专注定时」与「环境伴音舱」（搭载 ExpressiveMicroSpring 滑动胶囊指示器）
            val pillFraction by animateFloatAsState(
                targetValue = selectedSection.toFloat(),
                animationSpec = ConceptMotion.expressiveSpring(),
                label = "focusTabSlidingPill"
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardSurfaceColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    val tabGap = 6.dp
                    val singleTabWidth = (maxWidth - tabGap) / 2f
                    // 灵动滑动胶囊底座
                    Box(
                        modifier = Modifier
                            .width(singleTabWidth)
                            .height(36.dp)
                            .graphicsLayer {
                                translationX = pillFraction * (singleTabWidth.toPx() + tabGap.toPx())
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(podAccent)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tabGap)
                    ) {
                        val tabs = listOf(
                            0 to "心流专注定时",
                            1 to if (ambientState.masterEnabled) "环境伴音舱 · 已开" else "环境伴音舱"
                        )
                        tabs.forEach { (idx, label) ->
                            val selected = selectedSection == idx
                            val txtColor by animateColorAsState(
                                targetValue = if (selected) Color.White else subText,
                                animationSpec = tween(200),
                                label = "focusTabTxt_$idx"
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .softClickable { selectedSection = idx }
                            ) {
                                Text(
                                    text = label,
                                    color = txtColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = selectedSection,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(animationSpec = ConceptMotion.spatialPageSpring()) { fullW -> dir * (fullW / 7) } +
                        fadeIn(tween(240, easing = ConceptMotion.EmphasizedDecelerate))) togetherWith
                        (slideOutHorizontally(animationSpec = ConceptMotion.spatialPageSpring()) { fullW -> -dir * (fullW / 9) } +
                            fadeOut(tween(170, easing = ConceptMotion.EmphasizedAccelerate)))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "focusPodSectionTransition"
            ) { activeSection ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (activeSection == 0) {
                    // 模块一：心流专注计时与场景预设
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(timerCardBrush)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    color = if (state.isRunning) {
                                        ConceptCyanAccent.copy(alpha = 0.2f)
                                    } else {
                                        podAccent.copy(alpha = 0.14f)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (state.isRunning) "专注进行中" else state.selectedPreset.title,
                                        color = if (state.isRunning) ConceptCyanAccent else podAccent,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = state.formattedRemaining,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Black,
                                        color = timerTitleColor
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.selectedPreset.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = timerSubColor,
                                        fontSize = 12.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { if (state.isRunning) state.progressFraction else 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = podAccent,
                                    trackColor = podAccent.copy(alpha = 0.16f)
                                )
                            }
                        }
                    }

                    Text(
                        text = "专注时长预设",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FocusPreset.entries.forEach { preset ->
                            val selected = state.selectedPreset == preset
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) podAccent.copy(alpha = 0.18f) else cardSurfaceColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable(
                                        enabled = !state.isRunning,
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = { FocusPodManager.selectPreset(preset) }
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${preset.minutes} 分钟",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = if (selected) podAccent else primaryText
                                    )
                                    Text(
                                        text = preset.title.substringBefore(" ("),
                                        fontSize = 11.sp,
                                        color = if (selected) podAccent.copy(alpha = 0.85f) else subText,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cardSurfaceColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "联动播放场景轻音乐",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = primaryText
                                        )
                                    )
                                    Text(
                                        text = "开启专注时自动播放轻音乐歌单",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = subText
                                    )
                                }
                                Switch(
                                    checked = state.autoPlaySceneMusic,
                                    onCheckedChange = { FocusPodManager.setAutoPlaySceneMusic(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = podAccent
                                    )
                                )
                            }

                            HorizontalDivider(color = primaryText.copy(alpha = 0.06f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "联动开启环境伴音",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = primaryText
                                        )
                                    )
                                    Text(
                                        text = "专注期间叠加自然白噪音声景",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = subText
                                    )
                                }
                                Switch(
                                    checked = state.autoMixAmbient,
                                    onCheckedChange = { FocusPodManager.setAutoMixAmbient(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = podAccent
                                    )
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (state.isRunning) {
                                FocusPodManager.stopFocusPod()
                                Toast.makeText(context, "已结束心流专注", Toast.LENGTH_SHORT).show()
                            } else {
                                FocusPodManager.startFocusPod { sceneId ->
                                    onPlaySceneMusic(sceneId)
                                }
                                Toast.makeText(context, "已开启 ${state.selectedPreset.title}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isRunning) VipGold else podAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (state.isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isRunning) "结束专注" else "开启心流专注",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    // 模块二：环境伴音舱（原音效设置页中的环境伴音舱整体迁移至此并重新设计布局）
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cardSurfaceColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "环境伴音总开关",
                                        color = primaryText,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "实时合成自然白噪音，可与音乐叠加或独立播放",
                                        color = subText,
                                        fontSize = 11.5.sp
                                    )
                                }
                                Switch(
                                    checked = ambientState.masterEnabled,
                                    onCheckedChange = { AmbientSoundManager.setMasterEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = podAccent
                                    )
                                )
                            }

                            HorizontalDivider(color = primaryText.copy(alpha = 0.06f))

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
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (ambientState.syncWithMusic) "音乐暂停时同步暂停环境音" else "独立白噪音模式，暂停音乐仍继续播放",
                                        color = subText,
                                        fontSize = 11.5.sp
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
                        }
                    }

                    // 氛围声景预设宫格（2x2 清晰布局，告别横向截断）
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cardSurfaceColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "氛围声景预设",
                                color = primaryText,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp
                            )

                            val quickScenes = listOf(
                                Triple("rain_study", "雨夜自习", "细雨屋檐 + 黑胶暖噪"),
                                Triple("retro_vinyl", "黑胶茶歇", "复古唱针底噪"),
                                Triple("camp_night", "篝火露营", "松木篝火 + 微雨"),
                                Triple("deep_ocean", "深海冥想", "慢呼吸海浪潮汐")
                            )
                            quickScenes.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { (key, title, desc) ->
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = subSurfaceColor,
                                            modifier = Modifier
                                                .weight(1f)
                                                .softCardClickable(
                                                    shape = RoundedCornerShape(14.dp),
                                                    onClick = { AmbientSoundManager.applyQuickScene(key) }
                                                )
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                                Text(
                                                    text = title,
                                                    color = podAccent,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = desc,
                                                    color = subText,
                                                    fontSize = 10.5.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 四路独立环境声道混音推子
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cardSurfaceColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "四路环境声道混音",
                                color = primaryText,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp
                            )

                            AmbientSoundChannel.entries.forEach { channel ->
                                val lvl = ambientState.levelFor(channel)
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = channel.title,
                                                color = primaryText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = channel.subtitle,
                                                color = subText,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Text(
                                            text = "${(lvl * 100).toInt()}%",
                                            color = if (lvl > 0.01f && ambientState.masterEnabled) podAccent else subText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    }
                                    Slider(
                                        value = lvl,
                                        onValueChange = { AmbientSoundManager.setChannelLevel(channel, it) },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = podAccent,
                                            activeTrackColor = podAccent,
                                            inactiveTrackColor = podAccent.copy(alpha = 0.16f)
                                        ),
                                        modifier = Modifier.height(30.dp)
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
