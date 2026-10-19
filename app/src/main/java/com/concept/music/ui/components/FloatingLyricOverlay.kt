package com.concept.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.Song
import com.concept.music.service.SystemLyricBridge
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import kotlin.math.roundToInt

/**
 * 全局悬浮 KRC 逐字流光歌词胶囊 ([INNOV-05])
 * 可在发现页、歌单页、搜索页、我的页等全场景顶层自由拖拽悬浮，
 * 实时渲染毫秒级 KRC 卡拉 OK 字内流光染色与双语翻译，支持防误触锁定并自适应明暗主题。
 */
@Composable
fun FloatingLyricOverlay(
    song: Song?,
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    onClickCapsule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SystemLyricBridge.state.collectAsState()
    val isDark = LocalIsDarkTheme.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(160f) }

    val capsuleBg = if (isDark) {
        Color(0xFF0E1524).copy(alpha = if (state.floatingCapsuleLocked) 0.80f else 0.94f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = if (state.floatingCapsuleLocked) 0.90f else 0.97f)
    }
    val unplayedWordColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF475569)
    val subTextColor = if (isDark) Color.White.copy(alpha = 0.68f) else Color(0xFF64748B)
    val iconMutedColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF64748B)

    AnimatedVisibility(
        visible = state.floatingCapsuleEnabled && song != null,
        enter = scaleIn(
            initialScale = 0.88f,
            animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
        ) + slideInVertically(
            initialOffsetY = { -it / 2 },
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(tween(200)),
        exit = scaleOut(
            targetScale = 0.88f,
            animationSpec = tween(180)
        ) + slideOutVertically(
            targetOffsetY = { -it / 3 },
            animationSpec = tween(180)
        ) + fadeOut(tween(160)),
        modifier = modifier
    ) {
        val currentLine = lyricData.lines.getOrNull(currentLyricIndex)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .padding(horizontal = 20.dp)
                .pointerInput(state.floatingCapsuleLocked) {
                    if (!state.floatingCapsuleLocked) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(-80f, 80f)
                            offsetY = (offsetY + dragAmount.y).coerceIn(60f, 1400f)
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                onClick = {
                    if (!state.floatingCapsuleLocked) {
                        onClickCapsule()
                    }
                },
                shape = RoundedCornerShape(22.dp),
                color = capsuleBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = if (isDark) 16.dp else 10.dp,
                        shape = RoundedCornerShape(22.dp),
                        spotColor = ConceptPrimary.copy(alpha = if (isDark) 0.45f else 0.22f)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ConceptPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = ConceptPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (currentLine != null && currentLine.words.isNotEmpty()) {
                            val annotated = buildAnnotatedString {
                                currentLine.words.forEach { word ->
                                    val wordEnd = word.startTimeMs + word.durationMs
                                    when {
                                        currentPositionMs >= wordEnd -> {
                                            withStyle(
                                                SpanStyle(
                                                    color = ConceptPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            ) {
                                                append(word.text)
                                            }
                                        }
                                        currentPositionMs > word.startTimeMs -> {
                                            val prog = ((currentPositionMs - word.startTimeMs).toFloat() /
                                                word.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                            val brush = Brush.horizontalGradient(
                                                0.0f to ConceptPrimary,
                                                (prog - 0.08f).coerceAtLeast(0f) to ConceptPrimary,
                                                (prog + 0.08f).coerceAtMost(1f) to unplayedWordColor,
                                                1.0f to unplayedWordColor
                                            )
                                            withStyle(
                                                SpanStyle(
                                                    brush = brush,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            ) {
                                                append(word.text)
                                            }
                                        }
                                        else -> {
                                            withStyle(
                                                SpanStyle(
                                                    color = unplayedWordColor,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            ) {
                                                append(word.text)
                                            }
                                        }
                                    }
                                }
                            }
                            Text(
                                text = annotated,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = currentLine?.text?.ifBlank { "${song?.title ?: ""} - ${song?.artist ?: ""}" }
                                    ?: "${song?.title ?: ""} - ${song?.artist ?: ""}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = ConceptPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (!currentLine?.translation.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentLine?.translation.orEmpty(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = subTextColor,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { SystemLyricBridge.setFloatingCapsuleLocked(!state.floatingCapsuleLocked) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (state.floatingCapsuleLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = "锁定悬浮词",
                            tint = if (state.floatingCapsuleLocked) VipGold else iconMutedColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    if (!state.floatingCapsuleLocked) {
                        IconButton(
                            onClick = { SystemLyricBridge.setFloatingCapsuleEnabled(false) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭悬浮词",
                                tint = iconMutedColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
