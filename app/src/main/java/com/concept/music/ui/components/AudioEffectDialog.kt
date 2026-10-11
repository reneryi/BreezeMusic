package com.concept.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.service.AudioEffectManager
import com.concept.music.service.EffectPreset
import com.concept.music.ui.theme.ConceptPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectDialog(
    onDismiss: () -> Unit
) {
    val state by AudioEffectManager.state.collectAsState()
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16191F),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
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
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
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
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            val presets = listOf(
                EffectPreset.VIPER_3D,
                EffectPreset.VIPER_BASS,
                EffectPreset.VIPER_VOCAL,
                EffectPreset.VIPER_HIFI,
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
                        color = if (isSelected) ConceptPrimary else Color(0xFF222631),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = state.isEnabled) {
                                AudioEffectManager.setPreset(preset)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF0F1115) else Color.White
                                )
                            )
                            Text(
                                text = preset.desc,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color(0xFF0F1115).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.45f)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 动态低音与虚拟环绕滑块
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)),
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
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
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
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
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
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
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
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5频段均衡微调推杆
            Text(
                text = "5段专业均衡器",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White.copy(alpha = 0.6f),
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
                        Text(
                            text = if (level > 0) "+${level / 100}dB" else "${level / 100}dB",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (level != 0) ConceptPrimary else Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // 竖向简易控制滑块或刻度
                        Box(
                            modifier = Modifier
                                .height(110.dp)
                                .width(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF222631)),
                            contentAlignment = Alignment.Center
                        ) {
                            Slider(
                                value = level.toFloat(),
                                onValueChange = { AudioEffectManager.setCustomBandLevel(index, it.toInt()) },
                                valueRange = -1000f..1000f,
                                enabled = state.isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = ConceptPrimary,
                                    activeTrackColor = ConceptPrimary,
                                    inactiveTrackColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .requiredWidth(110.dp)
                                    .requiredHeight(28.dp)
                                    .offset(y = 0.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = freqLabels.getOrElse(index) { "" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
