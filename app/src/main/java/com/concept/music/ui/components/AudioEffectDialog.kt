package com.concept.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.service.AudioEffectManager
import com.concept.music.service.EffectPreset
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.conceptSheetSpringEntrance
import com.concept.music.ui.theme.softCardClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectDialog(
    onDismiss: () -> Unit
) {
    val state by AudioEffectManager.state.collectAsState()
    val scrollState = rememberScrollState()
    val isDark = LocalIsDarkTheme.current
    val sheetBg = conceptSheetContainerColor()
    val cardBg = conceptFloatingCardColor()
    val elevatedBg = conceptElevatedSurfaceColor()
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = subText.copy(alpha = 0.28f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .verticalScroll(scrollState)
        ) {
            // 顶部标题与总开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = ConceptPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "蝰蛇声效 & 专业均衡器",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText,
                            fontSize = 18.sp
                        )
                    )
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

            Spacer(modifier = Modifier.height(16.dp))

            // 预设模式横向胶囊切换
            Text(
                text = "特色声效预设",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = subText,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            val presets = listOf(
                EffectPreset.VIPER_3D,
                EffectPreset.VIPER_VOCAL,
                EffectPreset.VIPER_BASS,
                EffectPreset.VIPER_HIFI,
                EffectPreset.VIPER_VINYL,
                EffectPreset.VIPER_SURROUND,
                EffectPreset.VIPER_DYNAMIC,
                EffectPreset.ROCK,
                EffectPreset.POP,
                EffectPreset.FLAT
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { preset ->
                    val isSelected = state.currentPreset == preset
                    Surface(
                        color = if (isSelected) ConceptPrimary else elevatedBg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .softCardClickable(
                                enabled = state.isEnabled,
                                shape = RoundedCornerShape(16.dp),
                                onClick = { AudioEffectManager.setPreset(preset) }
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else primaryText
                                )
                            )
                            Text(
                                text = preset.desc,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else subText
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 详细声效调节推杆
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 低音增强 (BassBoost)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "超重低音增强 (BassBoost)",
                            style = MaterialTheme.typography.bodyMedium.copy(color = primaryText, fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${state.bassBoostStrength / 10}%",
                            style = MaterialTheme.typography.labelMedium.copy(color = ConceptPrimary, fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = state.bassBoostStrength.toFloat(),
                        onValueChange = { AudioEffectManager.setBassBoostStrength(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = state.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = ConceptPrimary,
                            activeTrackColor = ConceptPrimary,
                            inactiveTrackColor = ConceptPrimary.copy(alpha = 0.16f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 空间环绕 (Virtualizer)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "3D 虚拟空间环绕 (Surround)",
                            style = MaterialTheme.typography.bodyMedium.copy(color = primaryText, fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${state.virtualizerStrength / 10}%",
                            style = MaterialTheme.typography.labelMedium.copy(color = ConceptPrimary, fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = state.virtualizerStrength.toFloat(),
                        onValueChange = { AudioEffectManager.setVirtualizerStrength(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = state.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = ConceptPrimary,
                            activeTrackColor = ConceptPrimary,
                            inactiveTrackColor = ConceptPrimary.copy(alpha = 0.16f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 响度增益 (LoudnessEnhancer)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "人声动态与响度增益 (Loudness)",
                            style = MaterialTheme.typography.bodyMedium.copy(color = primaryText, fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "+${state.loudnessGainMb / 100} dB",
                            style = MaterialTheme.typography.labelMedium.copy(color = ConceptPrimary, fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = state.loudnessGainMb.toFloat(),
                        onValueChange = { AudioEffectManager.setLoudnessGain(it.toInt()) },
                        valueRange = 0f..1500f,
                        enabled = state.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = ConceptPrimary,
                            activeTrackColor = ConceptPrimary,
                            inactiveTrackColor = ConceptPrimary.copy(alpha = 0.16f)
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 声场混响模式 (ReverbMode)
                    Text(
                        text = "声场混响模式 (Reverb)",
                        style = MaterialTheme.typography.bodyMedium.copy(color = primaryText, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(com.concept.music.service.ReverbMode.values()) { mode ->
                            val isModeSelected = state.reverbMode == mode
                            Surface(
                                color = if (isModeSelected) ConceptPrimary else elevatedBg,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .softCardClickable(
                                        enabled = state.isEnabled,
                                        shape = RoundedCornerShape(12.dp),
                                        onClick = { AudioEffectManager.setReverbMode(mode) }
                                    )
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isModeSelected) Color.White else subText,
                                        fontWeight = if (isModeSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5频段均衡微调推杆
            Text(
                text = "5段专业均衡器",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = subText,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            val freqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                state.bandLevels.forEachIndexed { index, level ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        val dbVal = level / 100f
                        Text(
                            text = if (level > 0) String.format(java.util.Locale.getDefault(), "+%.1fdB", dbVal)
                            else String.format(java.util.Locale.getDefault(), "%.1fdB", dbVal),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (level != 0) ConceptPrimary else subText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // 竖向推杆控制滑块（-90度旋转映射为下负上正）
                        Box(
                            modifier = Modifier
                                .height(132.dp)
                                .width(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(elevatedBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(1.dp)
                                    .background(subText.copy(alpha = 0.25f))
                            )
                            Slider(
                                value = level.toFloat(),
                                onValueChange = { AudioEffectManager.setCustomBandLevel(index, it.toInt()) },
                                valueRange = -1000f..1000f,
                                enabled = state.isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = ConceptPrimary,
                                    activeTrackColor = ConceptPrimary,
                                    inactiveTrackColor = ConceptPrimary.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier
                                    .requiredWidth(116.dp)
                                    .requiredHeight(36.dp)
                                    .graphicsLayer {
                                        rotationZ = -90f
                                    }
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = freqLabels.getOrElse(index) { "" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = subText,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 4. 心流环境音混音舱 ([INNOV-04])
            val ambientState by com.concept.music.service.AmbientSoundManager.state.collectAsState()
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "心流环境音混音舱 (Ambient Mixer)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = primaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "实时程序化 DSP 合成环境声，可与音乐叠加或独立助眠",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = subText
                                )
                            )
                        }
                        Switch(
                            checked = ambientState.masterEnabled,
                            onCheckedChange = { com.concept.music.service.AmbientSoundManager.setMasterEnabled(it) },
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
                                modifier = Modifier
                                    .softCardClickable(
                                        shape = RoundedCornerShape(12.dp),
                                        onClick = { com.concept.music.service.AmbientSoundManager.applyQuickScene(key) }
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

                    com.concept.music.service.AmbientSoundChannel.entries.forEach { channel ->
                        val lvl = ambientState.levelFor(channel)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = channel.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = primaryText,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = channel.subtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = subText,
                                        fontSize = 10.sp
                                    )
                                )
                            }
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
                            onValueChange = { com.concept.music.service.AmbientSoundManager.setChannelLevel(channel, it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = ConceptPrimary,
                                activeTrackColor = ConceptPrimary,
                                inactiveTrackColor = ConceptPrimary.copy(alpha = 0.16f)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "仅在主音乐播放时叠加（关闭则暂停音乐后仍持续发声）",
                            style = MaterialTheme.typography.labelSmall.copy(color = subText),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = ambientState.syncWithMusic,
                            onCheckedChange = { com.concept.music.service.AmbientSoundManager.setSyncWithMusic(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ConceptPrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
