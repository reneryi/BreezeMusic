package com.concept.music.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.Song
import com.concept.music.service.DesktopLyricColorScheme
import com.concept.music.service.DesktopLyricLineMode
import com.concept.music.service.StatusBarHorizontalAnchor
import com.concept.music.service.StatusBarOverlayStyle
import com.concept.music.service.SystemLyricBridge
import com.concept.music.service.SystemLyricState
import com.concept.music.ui.theme.ConceptMotion
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.softClickable
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sin

/**
 * 全新重构「桌面歌词 + 顶部状态栏歌词 / 灵动岛胶囊」双引擎宿主组件：
 * 1. 自动向 `SystemLyricBridge` 同步实时毫秒级进度、歌词与控播回调，驱动 Android 系统级全局悬浮窗 (`WindowManager`)；
 * 2. 若已授予「显示在其他应用上层」权限，直接由系统级 `NativeStatusBarIslandView` / `NativeDesktopLyricView` 跨应用全局渲染，本组件内对应图层自动隐退防重叠；
 * 3. 若尚未授予系统悬浮窗权限，无缝在应用内顶部渲染同款「椒盐风状态栏悬浮歌词 / iPhone 灵动岛音乐胶囊」，确保开启即刻生效！
 */
@Composable
fun FloatingLyricOverlay(
    song: Song?,
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    positionProvider: () -> Long = { currentPositionMs },
    isPlaying: Boolean = true,
    onTogglePlay: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onClickCapsule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by SystemLyricBridge.state.collectAsState()

    // 实时同步至系统级悬浮窗桥接器
    SideEffect {
        SystemLyricBridge.updatePlaybackSnapshot(
            song = song,
            lyricData = lyricData,
            currentLyricIndex = currentLyricIndex,
            currentPositionMs = positionProvider(),
            isPlaying = isPlaying,
            onTogglePlay = onTogglePlay,
            onPrevious = onPrevious,
            onNext = onNext
        )
    }

    // 1. 顶部状态栏歌词 / 灵动岛胶囊（当未挂载系统级跨应用状态栏悬浮窗时，提供应用内零门槛实时渲染）
    val showInAppTopStatusBarIsland = !state.statusBarOverlayActive &&
        SystemLyricBridge.shouldShowTopStatusBarOverlay(state) &&
        (song != null || state.lastBroadcastedLine.isNotBlank())

    if (showInAppTopStatusBarIsland) {
        InAppTopStatusBarIslandOverlay(
            state = state,
            song = song,
            lyricData = lyricData,
            currentLyricIndex = currentLyricIndex,
            positionProvider = positionProvider,
            isPlaying = isPlaying,
            onTogglePlay = onTogglePlay,
            onPrevious = onPrevious,
            onNext = onNext,
            onClickCapsule = onClickCapsule
        )
    }

    // 2. 若系统级桌面歌词全局悬浮窗已激活，则不重复渲染应用内桌面歌词图层
    if (state.systemOverlayActive) {
        return
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx().coerceAtLeast(720f) }
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx().coerceAtLeast(1280f) }
    }
    val maxHorizontalOffsetPx = remember(screenWidthPx, state.desktopWidthFraction) {
        ((screenWidthPx * (1f - state.desktopWidthFraction)) / 2f).coerceAtLeast(24f)
    }
    val minVerticalOffsetPx = remember(density) { with(density) { 44.dp.toPx() } }
    val maxVerticalOffsetPx = remember(screenHeightPx, density) {
        (screenHeightPx - with(density) { 180.dp.toPx() }).coerceAtLeast(minVerticalOffsetPx + 100f)
    }

    var dragX by remember { mutableFloatStateOf(state.savedOffsetX.coerceIn(-maxHorizontalOffsetPx, maxHorizontalOffsetPx)) }
    var dragY by remember { mutableFloatStateOf(state.savedOffsetY.coerceIn(minVerticalOffsetPx, maxVerticalOffsetPx)) }
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
    val unplayedColor = Color(scheme.unplayedHex).copy(alpha = 0.82f)
    val textShadow = remember(scheme) {
        Shadow(
            color = Color(scheme.strokeHex).copy(alpha = 0.88f),
            offset = Offset(0f, 2f),
            blurRadius = 6f
        )
    }

    val effectiveBgAlpha = when {
        state.floatingCapsuleLocked -> (state.desktopBgAlpha * 0.35f).coerceIn(0f, 0.52f)
        showHudControls -> state.desktopBgAlpha.coerceAtLeast(0.75f)
        else -> state.desktopBgAlpha
    }
    val containerColor = Color(0xFF0A101D).copy(alpha = effectiveBgAlpha)
    val borderAlpha = if (effectiveBgAlpha < 0.10f) 0f else if (state.floatingCapsuleLocked) 0.06f else 0.24f

    AnimatedVisibility(
        visible = state.floatingCapsuleEnabled && song != null,
        enter = scaleIn(
            initialScale = 0.90f,
            animationSpec = ConceptMotion.expressiveSpring()
        ) + fadeIn(tween(180, easing = ConceptMotion.EmphasizedDecelerate)),
        exit = scaleOut(
            targetScale = 0.90f,
            animationSpec = ConceptMotion.spatialSheetSpring()
        ) + fadeOut(tween(150, easing = ConceptMotion.EmphasizedAccelerate)),
        modifier = modifier
    ) {
        val lines = lyricData.lines
        val safeIdx = currentLyricIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        val currentLine = lines.getOrNull(safeIdx)
        val nextLine = lines.getOrNull(safeIdx + 1)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // 使用 graphicsLayer 直接在 GPU 渲染阶段做平移变换，彻底消除拖拽时的 Layout 重排与 Shadow 重算卡顿
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth(state.desktopWidthFraction.coerceIn(0.68f, 0.96f))
                    .graphicsLayer {
                        translationX = dragX
                        translationY = dragY
                    }
                    .then(
                        if (!state.floatingCapsuleLocked) {
                            Modifier.pointerInput(maxHorizontalOffsetPx, maxVerticalOffsetPx) {
                                detectDragGestures(
                                    onDragEnd = {
                                        SystemLyricBridge.saveDesktopPosition(dragX, dragY)
                                    },
                                    onDragCancel = {
                                        SystemLyricBridge.saveDesktopPosition(dragX, dragY)
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    dragX = (dragX + dragAmount.x).coerceIn(-maxHorizontalOffsetPx, maxHorizontalOffsetPx)
                                    dragY = (dragY + dragAmount.y).coerceIn(minVerticalOffsetPx, maxVerticalOffsetPx)
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(containerColor)
                        .then(
                            if (borderAlpha > 0.01f) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = playedStartColor.copy(alpha = borderAlpha),
                                    shape = RoundedCornerShape(20.dp)
                                )
                            } else Modifier
                        )
                        .then(
                            if (!state.floatingCapsuleLocked) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showHudControls = !showHudControls
                                    if (!showHudControls) showStyleSubPanel = false
                                }
                            } else Modifier
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    // 1. 顶部交互控制栏（未锁定且点击桌面歌词后展开）
                    AnimatedVisibility(
                        visible = showHudControls && !state.floatingCapsuleLocked,
                        enter = expandVertically(animationSpec = ConceptMotion.spatialSheetSpring()) +
                            fadeIn(tween(180, easing = ConceptMotion.EmphasizedDecelerate)),
                        exit = shrinkVertically(animationSpec = ConceptMotion.spatialSheetSpring()) +
                            fadeOut(tween(140, easing = ConceptMotion.EmphasizedAccelerate))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (!SystemLyricBridge.canDrawSystemOverlays(context)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = playedStartColor.copy(alpha = 0.18f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .softClickable {
                                            SystemLyricBridge.requestOverlayPermissionIfNeeded(context)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "开启「跨应用全局悬浮窗」权限，退回手机桌面也可常驻显示",
                                            color = Color.White.copy(alpha = 0.92f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "去开启 >",
                                            color = playedStartColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "${song?.title.orEmpty()} - ${song?.artist.orEmpty()}",
                                        color = Color.White.copy(alpha = 0.90f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(onClick = onPrevious, modifier = Modifier.size(26.dp)) {
                                        Icon(
                                            imageVector = Icons.Rounded.SkipPrevious,
                                            contentDescription = "上一首",
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    IconButton(onClick = onTogglePlay, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                                            contentDescription = "播放/暂停",
                                            tint = playedStartColor,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                    IconButton(onClick = onNext, modifier = Modifier.size(26.dp)) {
                                        Icon(
                                            imageVector = Icons.Rounded.SkipNext,
                                            contentDescription = "下一首",
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = { showStyleSubPanel = !showStyleSubPanel },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Palette,
                                            contentDescription = "桌面歌词样式",
                                            tint = if (showStyleSubPanel) playedStartColor else Color.White.copy(alpha = 0.80f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            SystemLyricBridge.setFloatingCapsuleLocked(true)
                                            Toast.makeText(context, "桌面歌词已锁定，点击右侧小锁图标可解锁", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LockOpen,
                                            contentDescription = "锁定桌面歌词",
                                            tint = Color.White.copy(alpha = 0.80f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { SystemLyricBridge.setFloatingCapsuleEnabled(false) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "关闭桌面歌词",
                                            tint = Color.White.copy(alpha = 0.80f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(5.dp))
                        }
                    }

                    // 2. 桌面歌词实时逐字流光主显示区（局部帧采样，不引起外层容器重组）
                    val textAlign = if (state.desktopAlignment == 1) TextAlign.Start else TextAlign.Center
                    val colAlign = if (state.desktopAlignment == 1) Alignment.Start else Alignment.CenterHorizontally

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = colAlign
                    ) {
                        when (state.desktopLineMode) {
                            DesktopLyricLineMode.SINGLE_COMPACT -> {
                                DesktopKrcKaraokeLineText(
                                    line = currentLine,
                                    fallbackText = "${song?.title.orEmpty()} - ${song?.artist.orEmpty()}",
                                    isActiveLine = true,
                                    isPlaying = isPlaying,
                                    positionProvider = positionProvider,
                                    fontSizeSp = state.desktopFontSize,
                                    playedStartColor = playedStartColor,
                                    playedEndColor = playedEndColor,
                                    unplayedColor = unplayedColor,
                                    textShadow = textShadow,
                                    textAlign = textAlign
                                )
                            }

                            DesktopLyricLineMode.DUAL_ALTERNATING -> {
                                val isEvenLine = safeIdx % 2 == 0
                                val topLine = if (isEvenLine) currentLine else nextLine
                                val bottomLine = if (isEvenLine) nextLine else currentLine

                                DesktopKrcKaraokeLineText(
                                    line = topLine,
                                    fallbackText = "${song?.title.orEmpty()} - ${song?.artist.orEmpty()}",
                                    isActiveLine = isEvenLine,
                                    isPlaying = isPlaying,
                                    positionProvider = positionProvider,
                                    fontSizeSp = if (isEvenLine) state.desktopFontSize else (state.desktopFontSize - 2.5f).coerceAtLeast(12f),
                                    playedStartColor = playedStartColor,
                                    playedEndColor = playedEndColor,
                                    unplayedColor = unplayedColor,
                                    textShadow = textShadow,
                                    textAlign = TextAlign.Start
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                DesktopKrcKaraokeLineText(
                                    line = bottomLine,
                                    fallbackText = "♪ • • •",
                                    isActiveLine = !isEvenLine,
                                    isPlaying = isPlaying,
                                    positionProvider = positionProvider,
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
                                    isActiveLine = true,
                                    isPlaying = isPlaying,
                                    positionProvider = positionProvider,
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
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subLineText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = unplayedColor.copy(alpha = 0.85f),
                                            fontSize = (state.desktopFontSize - 3.8f).coerceAtLeast(11.5f).sp,
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

                    // 3. 展开式桌面歌词个性定制面板
                    AnimatedVisibility(
                        visible = showHudControls && showStyleSubPanel && !state.floatingCapsuleLocked,
                        enter = expandVertically(animationSpec = ConceptMotion.spatialSheetSpring()) +
                            fadeIn(tween(180, easing = ConceptMotion.EmphasizedDecelerate)),
                        exit = shrinkVertically(animationSpec = ConceptMotion.spatialSheetSpring()) +
                            fadeOut(tween(140, easing = ConceptMotion.EmphasizedAccelerate))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("配色:", color = Color.White.copy(alpha = 0.7f), fontSize = 10.5.sp)
                                DesktopLyricColorScheme.entries.forEach { item ->
                                    val selected = state.desktopColorScheme == item
                                    Surface(
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (selected) Color(item.playedStartHex).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                        modifier = Modifier.softClickable { SystemLyricBridge.setDesktopColorScheme(item) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
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
                                                fontSize = 10.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
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
                                                .padding(4.5.dp)
                                                .size(13.dp)
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
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
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
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (state.desktopBgAlpha < 0.08f) "无框透明" else "透度 ${(state.desktopBgAlpha * 100).toInt()}%",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.width(64.dp)
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
                                        .height(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "窗宽 ${(state.desktopWidthFraction * 100).toInt()}%",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.width(56.dp)
                                )
                                Slider(
                                    value = state.desktopWidthFraction,
                                    onValueChange = { SystemLyricBridge.setDesktopWidthFraction(it) },
                                    valueRange = 0.68f..0.96f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = playedStartColor,
                                        activeTrackColor = playedStartColor,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .weight(0.85f)
                                        .height(24.dp)
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
                        color = Color(0xCC0A101D),
                        modifier = Modifier
                            .size(30.dp)
                            .border(1.dp, VipGold.copy(alpha = 0.65f), CircleShape)
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
                                tint = VipGold,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 应用内顶部「灵动岛音乐胶囊 / 椒盐风状态栏歌词」实时叠加层（当未授予跨应用悬浮窗权限时无缝生效）
 */
@Composable
private fun InAppTopStatusBarIslandOverlay(
    state: SystemLyricState,
    song: Song?,
    lyricData: LyricData,
    currentLyricIndex: Int,
    positionProvider: () -> Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClickCapsule: () -> Unit
) {
    val density = LocalDensity.current
    val style = SystemLyricBridge.effectiveOverlayStyle(state)
    val scheme = state.statusBarColorScheme
    val startColor = Color(scheme.playedStartHex)
    val endColor = Color(scheme.playedEndHex)
    val unplayedColor = Color(scheme.unplayedHex).copy(alpha = 0.88f)
    val shadow = remember(scheme) {
        Shadow(
            color = Color(scheme.strokeHex).copy(alpha = 0.90f),
            offset = Offset(0f, 1.5f),
            blurRadius = 4f
        )
    }

    val lines = lyricData.lines
    val safeIdx = currentLyricIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    val currentLine = lines.getOrNull(safeIdx)
    val fallbackText = state.lastBroadcastedLine.ifBlank {
        if (song != null) "${song.title} - ${song.artist}" else "微风音乐 · 状态栏歌词就绪"
    }
    val subText = if (state.statusBarShowTranslation) {
        currentLine?.translation?.takeIf { it.isNotBlank() } ?: state.lastBroadcastedSubLine
    } else ""

    val boxAlignment = when (state.statusBarAnchor) {
        StatusBarHorizontalAnchor.LEFT_PUNCH_HOLE -> Alignment.TopStart
        StatusBarHorizontalAnchor.RIGHT_STATUS -> Alignment.TopEnd
        StatusBarHorizontalAnchor.CENTER -> Alignment.TopCenter
    }

    val offsetXPx = with(density) { state.statusBarOffsetX.dp.toPx() }
    val offsetYPx = with(density) { state.statusBarOffsetY.dp.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "islandWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "islandWavePhase"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        contentAlignment = boxAlignment
    ) {
        val isExpanded = state.statusBarExpandedIsland && style == StatusBarOverlayStyle.DYNAMIC_ISLAND && !state.statusBarTouchThrough
        val targetWidthMod = if (isExpanded) {
            Modifier.fillMaxWidth(0.94f)
        } else {
            Modifier.width(state.statusBarWidthDp.dp)
        }

        val bgAlpha = if (style == StatusBarOverlayStyle.DYNAMIC_ISLAND) {
            state.statusBarBgAlpha.coerceIn(0.24f, 0.96f)
        } else {
            state.statusBarBgAlpha
        }

        val capsuleShape = if (isExpanded) RoundedCornerShape(24.dp) else CircleShape

        Column(
            modifier = targetWidthMod
                .graphicsLayer {
                    translationX = if (isExpanded) 0f else offsetXPx
                    translationY = offsetYPx
                }
                .clip(capsuleShape)
                .background(Color(0xFF060A12).copy(alpha = bgAlpha))
                .then(
                    if (bgAlpha > 0.12f) {
                        Modifier.border(
                            width = 1.dp,
                            color = startColor.copy(alpha = 0.42f),
                            shape = capsuleShape
                        )
                    } else Modifier
                )
                .then(
                    if (!state.statusBarTouchThrough) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (style == StatusBarOverlayStyle.DYNAMIC_ISLAND) {
                                SystemLyricBridge.setStatusBarExpandedIsland(!state.statusBarExpandedIsland)
                            } else {
                                onClickCapsule()
                            }
                        }
                    } else Modifier
                )
                .padding(
                    horizontal = if (isExpanded) 14.dp else 10.dp,
                    vertical = if (isExpanded) 10.dp else 4.dp
                )
        ) {
            if (isExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .softClickable {
                                SystemLyricBridge.setStatusBarExpandedIsland(false)
                                onClickCapsule()
                            }
                    ) {
                        Text(
                            text = song?.title ?: "微风音乐",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (song != null) {
                                if (song.album.isNotBlank()) "${song.artist} · 《${song.album}》" else song.artist
                            } else "灵动岛控制舱",
                            color = startColor.copy(alpha = 0.85f),
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onTogglePlay, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                                contentDescription = "播放/暂停",
                                tint = startColor,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { SystemLyricBridge.setStatusBarExpandedIsland(false) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "收起灵动岛", tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (style == StatusBarOverlayStyle.DYNAMIC_ISLAND && state.statusBarShowIslandMedia && !isExpanded) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141C2B))
                            .border(1.dp, startColor.copy(alpha = 0.65f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(startColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = if (state.statusBarAnchor == StatusBarHorizontalAnchor.CENTER && !isExpanded) {
                        Alignment.CenterHorizontally
                    } else {
                        Alignment.Start
                    }
                ) {
                    DesktopKrcKaraokeLineText(
                        line = currentLine,
                        fallbackText = fallbackText,
                        isActiveLine = state.statusBarWordByWord,
                        isPlaying = isPlaying,
                        positionProvider = positionProvider,
                        fontSizeSp = state.statusBarFontSize,
                        playedStartColor = startColor,
                        playedEndColor = endColor,
                        unplayedColor = unplayedColor,
                        textShadow = shadow,
                        textAlign = if (state.statusBarAnchor == StatusBarHorizontalAnchor.CENTER && !isExpanded) TextAlign.Center else TextAlign.Start
                    )
                    if (subText.isNotBlank()) {
                        Text(
                            text = subText,
                            color = unplayedColor.copy(alpha = 0.82f),
                            fontSize = (state.statusBarFontSize - 3f).coerceAtLeast(9.5f).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (style == StatusBarOverlayStyle.DYNAMIC_ISLAND && state.statusBarShowIslandMedia && !isExpanded) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (i in 0 until 4) {
                            val amp = if (isPlaying) (0.35f + 0.65f * abs(sin(wavePhase + i * 0.9f))) else 0.25f
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height((13f * amp).coerceAtLeast(4f).dp)
                                    .clip(CircleShape)
                                    .background(if (i % 2 == 0) startColor else endColor)
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
    isActiveLine: Boolean,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    fontSizeSp: Float,
    playedStartColor: Color,
    playedEndColor: Color,
    unplayedColor: Color,
    textShadow: Shadow,
    textAlign: TextAlign
) {
    var sampledPosMs by remember(line, isActiveLine) {
        mutableLongStateOf(if (isActiveLine) positionProvider() else -1L)
    }
    LaunchedEffect(line, isActiveLine, isPlaying) {
        if (isActiveLine && line != null && line.words.isNotEmpty()) {
            sampledPosMs = positionProvider()
            if (isPlaying) {
                while (isActive) {
                    withFrameMillis {
                        sampledPosMs = positionProvider()
                    }
                }
            }
        } else {
            sampledPosMs = if (isActiveLine) positionProvider() else -1L
        }
    }

    val currentPositionMs = if (isActiveLine) sampledPosMs else -1L
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
                        val subP = if (word.durationMs > 0L) {
                            ((currentPositionMs - word.startTimeMs).toFloat() / word.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 1f
                        val lStop = (subP - 0.15f).coerceAtLeast(0f)
                        val rStop = (subP + 0.15f).coerceAtMost(1f)
                        withStyle(
                            SpanStyle(
                                brush = Brush.horizontalGradient(
                                    0.0f to playedStartColor,
                                    lStop to playedEndColor,
                                    rStop to unplayedColor,
                                    1.0f to unplayedColor
                                ),
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
        Text(
            text = display,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (isActiveLine) playedStartColor else unplayedColor,
                fontWeight = if (isActiveLine) FontWeight.ExtraBold else FontWeight.SemiBold,
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
