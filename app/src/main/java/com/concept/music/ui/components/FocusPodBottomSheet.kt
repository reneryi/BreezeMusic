package com.concept.music.ui.components

import com.concept.music.ui.theme.conceptSheetSpringEntrance

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.concept.music.service.FocusPodManager
import com.concept.music.service.FocusPreset
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptVioletAccent
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.softCardClickable

/**
 * 「概念胶囊 · Focus Pod」抽屉面板
 * 色调与「我的」页面概念胶囊入口卡片完全统一（薰衣草紫 / 极光紫渐变体系）
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val podAccent = if (isDark) Color(0xFF9688FF) else ConceptVioletAccent
    val sheetBgColor = if (isDark) Color(0xFF121526) else Color(0xFFF6F4FF)
    val itemSurfaceColor = if (isDark) Color(0xFF1A1E34) else Color(0xFFEDE9FF).copy(alpha = 0.65f)

    val timerCardBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF201F3D),
                Color(0xFF181C34),
                Color(0xFF131729)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFEBE6FF),
                Color(0xFFE8F1FF),
                Color(0xFFF3F0FF)
            )
        )
    }
    val timerTitleColor = if (isDark) Color.White else Color(0xFF181532)
    val timerSubColor = if (isDark) Color.White.copy(alpha = 0.70f) else Color(0xFF564F7A)

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
                .conceptSheetSpringEntrance()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(13.dp),
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
                            text = "概念胶囊 · Focus Pod",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "今日专注 ${state.completedPodsToday} 次 · 累计 ${state.totalFocusMinutesToday} 分钟",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = podAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(timerCardBrush)
                        .padding(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = if (state.isRunning) {
                                ConceptCyanAccent.copy(alpha = 0.2f)
                            } else {
                                podAccent.copy(alpha = 0.14f)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (state.isRunning) "心流专注中 · 自动计入听歌时长" else state.selectedPreset.title,
                                color = if (state.isRunning) ConceptCyanAccent else podAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.formattedRemaining,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Black,
                                color = timerTitleColor
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.selectedPreset.subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = timerSubColor
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))
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
                text = "选择心流场景预设",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusPreset.entries.forEach { preset ->
                    val selected = state.selectedPreset == preset
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) podAccent.copy(alpha = 0.18f) else itemSurfaceColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .softCardClickable(
                                enabled = !state.isRunning,
                                shape = RoundedCornerShape(18.dp),
                                onClick = { FocusPodManager.selectPreset(preset) }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) podAccent else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = podAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = itemSurfaceColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "联动播放心流场景轻音",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "启动时自动装载对应场景伴听曲目",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自动叠加 DSP 环境白噪音",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "联动雨夜窗台 / 黑胶底噪 / 深海潮汐混音舱",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Toast.makeText(context, "已结束本次心流专注舱", Toast.LENGTH_SHORT).show()
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
                shape = RoundedCornerShape(18.dp),
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
                    text = if (state.isRunning) "结束专注并关闭环境音" else "开启心流专注舱",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
