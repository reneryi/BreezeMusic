package com.concept.music.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.Song
import com.concept.music.service.DesktopLyricColorScheme
import com.concept.music.service.DesktopLyricLineMode
import com.concept.music.service.SystemLyricBridge
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.softClickable
import kotlin.math.roundToInt

/**
 * 全新重构「桌面歌词 (Desktop Lyrics)」系统
 * 替代原简陋的“悬浮词”，提供商业级多样化桌面歌词体验：
 * 1. 支持 6 款高颜值 KRC 逐字卡拉OK流光配色方案（概念冰蓝、璀璨鎏金、霓虹樱粉、极光薄荷、赛博星紫、纯净皓白）；
 * 2. 支持 3 种显示模式：双行双语对照、KTV双行奇偶交替、单行极简流光；
 * 3. 支持点击唤出交互式桌面歌词控制台：上一首 / 播放暂停 / 下一首 / 进入播放页 / 字号无级缩放 / 对齐方式 / 背景透明度（支持 0% 全透明无框描边字） / 防误触锁定；
 * 4. 锁定后自动收起控制台并变为半透防误触悬浮态，点击右侧迷你锁头可一键解锁，且拖拽坐标持久记忆。
 */
@Composable
fun FloatingLyricOverlay(
    song: Song?,
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    isPlaying: Boolean = true,
    onTogglePlay: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onClickCapsule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by SystemLyricBridge.state.collectAsState()
    val isDark = LocalIsDarkTheme.current

    var offsetX by remember(state.savedOffsetX) { mutableFloatStateOf(state.savedOffsetX) }
    var offsetY by remember(state.savedOffsetY) { mutableFloatStateOf(state.savedOffsetY) }
    var showHudControls by remember { mutableStateOf(false) }
    var showStyleSubPanel by remember { mutableStateOf(false) }

    LaunchedEffect(state.floatingCapsuleLocked) {
        if (state.floatingCapsuleLocked) {
            showHudControls = false
            showStyleSubPanel = false
        }
    }

    val scheme = state.desktopColorScheme
    val playedStartColor = Color(scheme.playedStartHex)
    val playedEndColor = Color(scheme.playedEndHex)
    val unplayedColor = Color(scheme.unplayedHex).copy(alpha = 0.78f)
    val textShadow = Shadow(
        color = Color(scheme.strokeHex).copy(alpha = 0.85f),
        offset = Offset(0f, 2f),
        blurRadius = 6f
    )

    val effectiveBgAlpha = if (state.floatingCapsuleLocked) {
        (state.desktopBgAlpha * 0.45f).coerceIn(0f, 0.65f)
    } else {
        state.desktopBgAlpha
    }
    val containerColor = Color(0xFF0A101D).copy(alpha = effectiveBgAlpha)
    val borderAlpha = if (effectiveBgAlpha < 0.12f) 0f else if (state.floatingCapsuleLocked) 0.08f else 0.22f

    AnimatedVisibility(
        visible = state.floatingCapsuleEnabled && song != null,
        enter = scaleIn(
            initialScale = 0.90f,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        ) + slideInVertically(
            initialOffsetY = { -it / 2 },
            animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(tween(180)),
        exit = scaleOut(
            targetScale = 0.90f,
            animationSpec = tween(160)
        ) + slideOutVertically(
            targetOffsetY = { -it / 3 },
            animationSpec = tween(160)
        ) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        val lines = lyricData.lines
        val safeIdx = currentLyricIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        val currentLine = lines.getOrNull(safeIdx)
        val nextLine = lines.getOrNull(safeIdx + 1)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .padding(horizontal = 14.dp)
                .pointerInput(state.floatingCapsuleLocked) {
                    if (!state.floatingCapsuleLocked) {
                        detectDragGestures(
                            onDragEnd = {
                                SystemLyricBridge.saveDesktopPosition(offsetX, offsetY)
                            },
                            onDragCancel = {
                                SystemLyricBridge.saveDesktopPosition(offsetX, offsetY)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(-120f, 120f)
                            offsetY = (offsetY + dragAmount.y).coerceIn(48f, 1650f)
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (effectiveBgAlpha >= 0.15f) {
                            Modifier.shadow(
                                elevation = if (showHudControls) 18.dp else 8.dp,
                                shape = RoundedCornerShape(22.dp),
                                spotColor = playedStartColor.copy(alpha = 0.38f)
                            )
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(containerColor)
                    .then(
                        if (borderAlpha > 0.01f) {
                            Modifier.border(
                                width = 1.dp,
                                color = playedStartColor.copy(alpha = borderAlpha),
                                shape = RoundedCornerShape(22.dp)
                            )
                        } else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!state.floatingCapsuleLocked) {
                            showHudControls = !showHudControls
                            if (!showHudControls) showStyleSubPanel = false
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // 1. 顶部交互控制栏（未锁定且点击桌面歌词后展开）
                AnimatedVisibility(
                    visible = showHudControls && !state.floatingCapsuleLocked,
                    enter = expandVertically() + fadeIn(tween(150)),
                    exit = shrinkVertically() + fadeOut(tween(120))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左：歌曲名与点击回主播放页
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .softClickable(onClick = onClickCapsule)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInFull,
                                    contentDescription = "进入播放页",
                                    tint = playedStartColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${song?.title.orEmpty()} - ${song?.artist.orEmpty()}",
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 中：上一首 / 播放暂停 / 下一首
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(onClick = onPrevious, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = "上一首",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = onTogglePlay, modifier = Modifier.size(30.dp)) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                                        contentDescription = "播放/暂停",
                                        tint = playedStartColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = "下一首",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // 右：样式设置、锁定、关闭
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = { showStyleSubPanel = !showStyleSubPanel },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = "桌面歌词样式",
                                        tint = if (showStyleSubPanel) playedStartColor else Color.White.copy(alpha = 0.78f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        SystemLyricBridge.setFloatingCapsuleLocked(true)
                                        Toast.makeText(context, "桌面歌词已锁定，点击右侧小锁图标可解锁", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LockOpen,
                                        contentDescription = "锁定桌面歌词",
                                        tint = Color.White.copy(alpha = 0.78f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { SystemLyricBridge.setFloatingCapsuleEnabled(false) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "关闭桌面歌词",
                                        tint = Color.White.copy(alpha = 0.78f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // 2. 桌面歌词实时逐字流光主显示区
                val textAlign = if (state.desktopAlignment == 1) TextAlign.Start else TextAlign.Center
                val colAlign = if (state.desktopAlignment == 1) Alignment.Start else Alignment.CenterHorizontally

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = colAlign
                    ) {
                        when (state.desktopLineMode) {
                            DesktopLyricLineMode.SINGLE_COMPACT -> {
                                DesktopKrcKaraokeLineText(
                                    line = currentLine,
                                    fallbackText = "${song?.title.orEmpty()} - ${song?.artist.orEmpty()}",
                                    currentPositionMs = currentPositionMs,
                                    fontSizeSp = state.desktopFontSize,
                                    playedStartColor = playedStartColor,
                                    playedEndColor = playedEndColor,
                                    unplayedColor = unplayedColor,
                                    textShadow = textShadow,
                                    textAlign = textAlign
                                )
                            }

                            DesktopLyricLineMode.DUAL_ALTERNATING -> {
                                // KTV 奇偶双行交替展示当前句与下一句预读
                                val isEvenLine = safeIdx % 2 == 0
                                val topLine = if (isEvenLine) currentLine else nextLine
                                val bottomLine = if (isEvenLine) nextLine else currentLine

                                DesktopKrcKaraokeLineText(
                                    line = topLine,
                                    fallbackText = "${song?.title.orEmpty()} - ${song?.artist.orEmpty()}",
                                    currentPositionMs = if (isEvenLine) currentPositionMs else -1L,
                                    fontSizeSp = if (isEvenLine) state.desktopFontSize else (state.desktopFontSize - 2.5f).coerceAtLeast(12f),
                                    playedStartColor = playedStartColor,
                                    playedEndColor = playedEndColor,
                                    unplayedColor = unplayedColor,
                                    textShadow = textShadow,
                                    textAlign = TextAlign.Start
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                DesktopKrcKaraokeLineText(
                                    line = bottomLine,
                                    fallbackText = "♪ • • •",
                                    currentPositionMs = if (!isEvenLine) currentPositionMs else -1L,
                                    fontSizeSp = if (!isEvenLine) state.desktopFontSize else (state.desktopFontSize - 2.5f).coerceAtLeast(12f),
                                    playedStartColor = playedStartColor,
                                    playedEndColor = playedEndColor,
                                    unplayedColor = unplayedColor,
                                    textShadow = textShadow,
                                    textAlign = TextAlign.End
                                )
                            }

                            DesktopLyricLineMode.DUAL_BILINGUAL -> {
                                DesktopKrcKaraokeLineText(
                                    line = currentLine,
                                    fallbackText = "${song?.title.orEmpty()} - ${song?.artist.orEmpty()}",
                                    currentPositionMs = currentPositionMs,
                                    fontSizeSp = state.desktopFontSize,
                                    playedStartColor = playedStartColor,
                                    playedEndColor = playedEndColor,
                                    unplayedColor = unplayedColor,
                                    textShadow = textShadow,
                                    textAlign = textAlign
                                )
                                val subLineText = when {
                                    state.desktopShowTranslation && !currentLine?.translation.isNullOrBlank() ->
                                        currentLine?.translation.orEmpty()
                                    !nextLine?.text.isNullOrBlank() ->
                                        nextLine?.text.orEmpty()
                                    else -> ""
                                }
                                if (subLineText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = subLineText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = unplayedColor.copy(alpha = 0.85f),
                                            fontSize = (state.desktopFontSize - 4f).coerceAtLeast(11.5f).sp,
                                            fontWeight = FontWeight.Medium,
                                            shadow = textShadow
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = textAlign,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // 锁定状态下在右侧保留一个微透明防误触解锁小按钮
                    if (state.floatingCapsuleLocked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.35f),
                            modifier = Modifier
                                .size(26.dp)
                                .softClickable {
                                    SystemLyricBridge.setFloatingCapsuleLocked(false)
                                    showHudControls = true
                                    Toast.makeText(context, "桌面歌词已解锁", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "解锁桌面歌词",
                                    tint = VipGold.copy(alpha = 0.9f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                // 3. 展开式桌面歌词多样化个性定制抽屉（配色、行模式、字号、对齐、底框透明度）
                AnimatedVisibility(
                    visible = showHudControls && showStyleSubPanel && !state.floatingCapsuleLocked,
                    enter = expandVertically() + fadeIn(tween(150)),
                    exit = shrinkVertically() + fadeOut(tween(120))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)

                        // 配色方案快速切换圆点
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("配色:", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            DesktopLyricColorScheme.entries.forEach { item ->
                                val selected = state.desktopColorScheme == item
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) Color(item.playedStartHex).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier
                                        .border(
                                            width = if (selected) 1.dp else 0.dp,
                                            color = if (selected) Color(item.playedStartHex) else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .softClickable { SystemLyricBridge.setDesktopColorScheme(item) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(item.playedStartHex), Color(item.playedEndHex))
                                                    )
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = item.label,
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // 行数模式 + 对齐 + 字号调节
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DesktopLyricLineMode.entries.forEach { mode ->
                                    val selected = state.desktopLineMode == mode
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) playedStartColor else Color.White.copy(alpha = 0.10f),
                                        modifier = Modifier.softClickable { SystemLyricBridge.setDesktopLineMode(mode) }
                                    ) {
                                        Text(
                                            text = mode.label,
                                            color = if (selected) Color(0xFF06101E) else Color.White.copy(alpha = 0.85f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.10f),
                                    modifier = Modifier.softClickable {
                                        SystemLyricBridge.setDesktopAlignment(if (state.desktopAlignment == 0) 1 else 0)
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (state.desktopAlignment == 0) Icons.Rounded.FormatAlignCenter else Icons.AutoMirrored.Rounded.FormatAlignLeft,
                                        contentDescription = "切换对齐",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(5.dp)
                                            .size(14.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.10f),
                                    modifier = Modifier.softClickable {
                                        SystemLyricBridge.setDesktopFontSize(state.desktopFontSize - 1.5f)
                                    }
                                ) {
                                    Text(
                                        text = "A-",
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.10f),
                                    modifier = Modifier.softClickable {
                                        SystemLyricBridge.setDesktopFontSize(state.desktopFontSize + 1.5f)
                                    }
                                ) {
                                    Text(
                                        text = "A+",
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // 背景透明度滑杆（支持 0% 全透明无框字）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.desktopBgAlpha < 0.08f) "背景: 纯净无框" else "底框透度 ${(state.desktopBgAlpha * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 10.5.sp,
                                modifier = Modifier.width(86.dp)
                            )
                            Slider(
                                value = state.desktopBgAlpha,
                                onValueChange = { SystemLyricBridge.setDesktopBgAlpha(it) },
                                valueRange = 0f..0.95f,
                                colors = SliderDefaults.colors(
                                    thumbColor = playedStartColor,
                                    activeTrackColor = playedStartColor,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopKrcKaraokeLineText(
    line: LyricLine?,
    fallbackText: String,
    currentPositionMs: Long,
    fontSizeSp: Float,
    playedStartColor: Color,
    playedEndColor: Color,
    unplayedColor: Color,
    textShadow: Shadow,
    textAlign: TextAlign
) {
    if (line != null && line.words.isNotEmpty() && currentPositionMs >= 0L) {
        val annotated = buildAnnotatedString {
            line.words.forEach { word ->
                val wordEnd = word.startTimeMs + word.durationMs
                when {
                    currentPositionMs >= wordEnd -> {
                        withStyle(
                            SpanStyle(
                                color = playedStartColor,
                                fontWeight = FontWeight.ExtraBold,
                                shadow = textShadow
                            )
                        ) {
                            append(word.text)
                        }
                    }
                    currentPositionMs > word.startTimeMs -> {
                        val prog = ((currentPositionMs - word.startTimeMs).toFloat() /
                            word.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                        val brush = Brush.horizontalGradient(
                            0.0f to playedStartColor,
                            (prog - 0.08f).coerceAtLeast(0f) to playedEndColor,
                            (prog + 0.08f).coerceAtMost(1f) to unplayedColor,
                            1.0f to unplayedColor
                        )
                        withStyle(
                            SpanStyle(
                                brush = brush,
                                fontWeight = FontWeight.ExtraBold,
                                shadow = textShadow
                            )
                        ) {
                            append(word.text)
                        }
                    }
                    else -> {
                        withStyle(
                            SpanStyle(
                                color = unplayedColor,
                                fontWeight = FontWeight.SemiBold,
                                shadow = textShadow
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
            fontSize = fontSizeSp.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        val display = line?.text?.takeIf { it.isNotBlank() } ?: fallbackText
        val isActive = currentPositionMs >= 0L
        Text(
            text = display,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (isActive) playedStartColor else unplayedColor,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize = fontSizeSp.sp,
                shadow = textShadow
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
