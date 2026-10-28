package com.concept.music.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.concept.music.data.model.Song
import com.concept.music.service.DesktopLyricColorScheme
import com.concept.music.service.DesktopLyricRenderRow
import com.concept.music.service.SystemLyricBridge
import com.concept.music.service.resolveDesktopLyricLayoutPlan
import com.concept.music.ui.theme.ConceptMotion
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.PlayMode
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 推翻重构后的桌面悬浮歌词组件 (`FloatingLyricOverlay`)：
 * 1. 启动后常驻态仅显示纯净歌词（带立体柔光暗影），彻底移除不透明胶囊背景框；
 * 2. 横向自适应铺满屏幕宽度，禁止左右拖动，仅支持垂直方向上下拖动定位；
 * 3. 歌词显示区域保持恒定三行高度空间：短歌词（两行）采用「插空垂直居中」（0.28 / 0.72 黄金分布），遇到长歌词自动折为三行（0.18 / 0.50 / 0.82）；
 * 4. 顶部恒定预留 46.dp 控制栏槽位：点击歌词呼出毛玻璃背景框与上方 7 个圆润无框按钮时，歌词垂直位置纹丝不动（绝不下移）；
 * 5. 顶栏从左到右依次为：锁定、播放顺序（左上角），上一首、暂停/播放、下一首（正中），设置、X（右上角），全部采用圆润饱满无边框矢量图标；
 * 6. 点击设置按钮后，毛玻璃框底部平滑向下延展，仅提供「歌词颜色」与「歌词字号」两项设置，且与 App 内歌词动效及设置双向实时同步。
 */
@Composable
fun FloatingLyricOverlay(
    song: Song?,
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    positionProvider: () -> Long = { currentPositionMs },
    isPlaying: Boolean = true,
    playMode: PlayMode = PlayMode.REPEAT_ALL,
    onTogglePlay: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onCyclePlayMode: () -> Unit = {},
    onClickCapsule: () -> Unit = {},
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
            playMode = playMode,
            onTogglePlay = onTogglePlay,
            onPrevious = onPrevious,
            onNext = onNext,
            onCyclePlayMode = onCyclePlayMode
        )
    }

    // 若系统级跨应用全局悬浮窗已激活，则由系统窗口接管渲染，避免双层重叠
    if (state.systemOverlayActive) {
        return
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx().coerceAtLeast(1280f) }
    }
    val minVerticalOffsetPx = remember(density) { with(density) { 36.dp.toPx() } }
    val maxVerticalOffsetPx = remember(screenHeightPx, density) {
        (screenHeightPx - with(density) { 220.dp.toPx() }).coerceAtLeast(minVerticalOffsetPx + 100f)
    }

    var dragY by remember {
        mutableFloatStateOf(state.savedOffsetY.coerceIn(minVerticalOffsetPx, maxVerticalOffsetPx))
    }
    var showHudControls by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }

    LaunchedEffect(state.floatingCapsuleLocked) {
        if (state.floatingCapsuleLocked) {
            showHudControls = false
            showSettingsPanel = false
        }
    }

    val hudAlpha by animateFloatAsState(
        targetValue = if (showHudControls && !state.floatingCapsuleLocked) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "desktopHudAlpha"
    )

    val settingsHeightDp by animateDpAsState(
        targetValue = if (showHudControls && showSettingsPanel && !state.floatingCapsuleLocked) 86.dp else 0.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "desktopSettingsExpandHeight"
    )

    val settingsAlpha by animateFloatAsState(
        targetValue = if (showHudControls && showSettingsPanel && !state.floatingCapsuleLocked) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "desktopSettingsAlpha"
    )

    val scheme = state.desktopColorScheme
    val playedStartColor by animateColorAsState(
        targetValue = Color(state.effectivePlayedStartHex),
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "desktopPlayedStartColor"
    )
    val playedEndColor by animateColorAsState(
        targetValue = Color(state.effectivePlayedEndHex),
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "desktopPlayedEndColor"
    )
    val unplayedColor = Color(scheme.unplayedHex).copy(alpha = 0.92f)
    val textShadow = remember(scheme) {
        Shadow(
            color = Color(scheme.strokeHex).copy(alpha = 0.92f),
            offset = Offset(0f, 3f),
            blurRadius = 8f
        )
    }

    val topHudReserveDp = 46.dp
    val singleRowSlotDp = remember(state.desktopFontSize) {
        (state.desktopFontSize * 1.44f).coerceAtLeast(24f).dp
    }
    val fixedThreeLineZoneDp = remember(singleRowSlotDp) {
        singleRowSlotDp * 3 + 8.dp
    }

    // 当系统级悬浮窗 (`systemOverlayActive`) 已挂载时，不再在 Activity 内重复叠加渲染，彻底消除双层重影
    AnimatedVisibility(
        visible = state.floatingCapsuleEnabled && !state.systemOverlayActive && song != null,
        enter = scaleIn(
            initialScale = 0.94f,
            animationSpec = ConceptMotion.expressiveSpring()
        ) + fadeIn(tween(180, easing = ConceptMotion.EmphasizedDecelerate)),
        exit = scaleOut(
            targetScale = 0.94f,
            animationSpec = ConceptMotion.spatialSheetSpring()
        ) + fadeOut(tween(150, easing = ConceptMotion.EmphasizedAccelerate)),
        modifier = modifier
    ) {
        val lines = lyricData.lines
        val safeIdx = currentLyricIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        val currentLine = lines.getOrNull(safeIdx)
        val nextLine = lines.getOrNull(safeIdx + 1)
        val fallbackTitle = "${song?.title.orEmpty()} - ${song?.artist.orEmpty()}".trim(' ', '-')
            .ifBlank { "微风音乐 · 酷狗概念版" }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // 横向自适应占满屏幕宽度（仅留 10.dp 安全边距），锁定 X=0，仅支持垂直上下拖动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .graphicsLayer {
                        translationY = dragY
                    }
                    .then(
                        if (!state.floatingCapsuleLocked) {
                            Modifier.pointerInput(minVerticalOffsetPx, maxVerticalOffsetPx) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        SystemLyricBridge.saveDesktopPosition(0f, dragY)
                                    },
                                    onDragCancel = {
                                        SystemLyricBridge.saveDesktopPosition(0f, dragY)
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    dragY = (dragY + dragAmount).coerceIn(minVerticalOffsetPx, maxVerticalOffsetPx)
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                val panelShape = RoundedCornerShape(24.dp)

                // 主容器：顶部 46.dp 控制槽位 + 中间三行恒定高度歌词区 + 底部平滑延伸设置区
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(panelShape)
                        .then(
                            if (hudAlpha > 0.01f) {
                                Modifier
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF131D31).copy(alpha = 0.85f * hudAlpha),
                                                Color(0xFF0A101D).copy(alpha = 0.90f * hudAlpha)
                                            )
                                        ),
                                        shape = panelShape
                                    )
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                playedStartColor.copy(alpha = 0.16f * hudAlpha),
                                                playedEndColor.copy(alpha = 0.08f * hudAlpha),
                                                Color.Transparent
                                            )
                                        ),
                                        shape = panelShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.24f * hudAlpha),
                                                playedStartColor.copy(alpha = 0.28f * hudAlpha)
                                            )
                                        ),
                                        shape = panelShape
                                    )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // 1. 顶部固定 46.dp 控制栏槽位（始终占据固定高度，保证点击唤醒时下方歌词位置 0px 偏移！）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topHudReserveDp)
                            .graphicsLayer { alpha = hudAlpha }
                    ) {
                        if (hudAlpha > 0.05f && !state.floatingCapsuleLocked) {
                            DesktopPlumpTopControlBar(
                                isPlaying = isPlaying,
                                playMode = playMode,
                                isSettingsOpen = showSettingsPanel,
                                accentColor = playedStartColor,
                                onLockClick = {
                                    showSettingsPanel = false
                                    showHudControls = false
                                    SystemLyricBridge.setFloatingCapsuleLocked(true)
                                    Toast.makeText(context, "桌面歌词已锁定，可在通知栏或流体云中解锁", Toast.LENGTH_SHORT).show()
                                },
                                onCyclePlayModeClick = {
                                    onCyclePlayMode()
                                },
                                onPreviousClick = onPrevious,
                                onTogglePlayClick = onTogglePlay,
                                onNextClick = onNext,
                                onToggleSettingsClick = {
                                    showSettingsPanel = !showSettingsPanel
                                },
                                onCloseClick = {
                                    showSettingsPanel = false
                                    showHudControls = false
                                    SystemLyricBridge.setFloatingCapsuleEnabled(false)
                                }
                            )

                            // 顶栏底部极细柔光分割线
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .height(0.6.dp)
                                    .background(Color.White.copy(alpha = 0.12f * hudAlpha))
                            )
                        }
                    }

                    // 2. 恒定三行高度歌词主显示区（短歌词两行插空居中 0.28/0.72，长歌词自然三行 0.18/0.50/0.82）
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(fixedThreeLineZoneDp)
                            .then(
                                if (!state.floatingCapsuleLocked) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        val nextHud = !showHudControls
                                        showHudControls = nextHud
                                        if (!nextHud) {
                                            showSettingsPanel = false
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 14.dp)
                    ) {
                        val availWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(200f) }
                        val mainFontPx = with(density) { state.desktopFontSize.sp.toPx().coerceAtLeast(15f) }
                        val slotHeightPx = with(density) { singleRowSlotDp.toPx() }
                        val maxVisualUnits = (availWidthPx / mainFontPx).coerceAtLeast(10f)

                        val layoutPlan = remember(
                            currentLine,
                            nextLine,
                            fallbackTitle,
                            state.desktopShowTranslation,
                            maxVisualUnits,
                            slotHeightPx
                        ) {
                            resolveDesktopLyricLayoutPlan(
                                currentLine = currentLine,
                                nextLine = nextLine,
                                fallbackTitle = fallbackTitle,
                                showTranslation = state.desktopShowTranslation,
                                maxVisualUnitsPerLine = maxVisualUnits,
                                singleRowSlotHeightPx = slotHeightPx
                            )
                        }

                        val containerHeightDp = maxHeight
                        layoutPlan.rows.forEach { row ->
                            val rowFontSizeSp = if (row.isSecondaryRow) {
                                (state.desktopFontSize * 0.82f).coerceAtLeast(13f)
                            } else {
                                state.desktopFontSize
                            }
                            val rowHalfHeightDp = (rowFontSizeSp * 0.68f).dp
                            val targetTopOffsetDp = (containerHeightDp * row.verticalCenterFraction - rowHalfHeightDp)
                                .coerceAtLeast(0.dp)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = targetTopOffsetDp),
                                contentAlignment = Alignment.Center
                            ) {
                                DesktopUnifiedAnimatedLyricRow(
                                    row = row,
                                    isPlaying = isPlaying,
                                    positionProvider = positionProvider,
                                    fontSizeSp = rowFontSizeSp,
                                    playedStartColor = playedStartColor,
                                    playedEndColor = playedEndColor,
                                    unplayedColor = unplayedColor,
                                    textShadow = textShadow
                                )
                            }
                        }

                        // 前奏 / 间奏三点呼吸波点（与 App 内动效统一）
                        DesktopInterludeBreathingIndicator(
                            lyricData = lyricData,
                            isPlaying = isPlaying,
                            positionProvider = positionProvider,
                            accentColor = playedStartColor,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 2.dp)
                        )
                    }

                    // 3. 底部平滑向下延伸的设置面板（仅含「歌词颜色」与「歌词字号」两项）
                    if (settingsHeightDp > 0.5.dp && !state.floatingCapsuleLocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(settingsHeightDp)
                                .graphicsLayer { alpha = settingsAlpha }
                        ) {
                            DesktopExpandableSettingsSection(
                                selectedScheme = state.desktopColorScheme,
                                dynamicCoverPrimaryHex = state.dynamicCoverPrimaryHex,
                                dynamicCoverSecondaryHex = state.dynamicCoverSecondaryHex,
                                fontSizeSp = state.desktopFontSize,
                                accentColor = playedStartColor,
                                onSelectScheme = { SystemLyricBridge.setDesktopColorScheme(it) },
                                onChangeFontSize = { SystemLyricBridge.setDesktopFontSize(it) }
                            )
                        }
                    }
                }

                // 锁定状态下屏幕上不再保留任何悬浮小锁图标，统一通过系统通知栏 / OPPO 流体云或 App 内按钮解锁
            }
        }
    }
}

/**
 * 顶部 7 键无边框、圆润饱满矢量控制栏：
 * 左上角：[锁定, 播放顺序] | 正中：[上一首, 暂停/播放, 下一首] | 右上角：[设置, X]
 */
@Composable
private fun DesktopPlumpTopControlBar(
    isPlaying: Boolean,
    playMode: PlayMode,
    isSettingsOpen: Boolean,
    accentColor: Color,
    onLockClick: () -> Unit,
    onCyclePlayModeClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onTogglePlayClick: () -> Unit,
    onNextClick: () -> Unit,
    onToggleSettingsClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
    ) {
        // 左上角组：锁定、播放顺序
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .softClickable(pressScale = 0.84f, onClick = onLockClick),
                contentAlignment = Alignment.Center
            ) {
                PlumpDesktopLockCanvasIcon(
                    color = VipGold,
                    modifier = Modifier.size(19.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .softClickable(pressScale = 0.84f, onClick = onCyclePlayModeClick),
                contentAlignment = Alignment.Center
            ) {
                PlumpDesktopPlayModeCanvasIcon(
                    playMode = playMode,
                    color = if (playMode == PlayMode.REPEAT_ALL) Color.White.copy(alpha = 0.94f) else accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 正中央组：上一首、暂停/播放、下一首
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .softClickable(pressScale = 0.84f, onClick = onPreviousClick),
                contentAlignment = Alignment.Center
            ) {
                PlumpDesktopSkipCanvasIcon(
                    isNext = false,
                    color = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .softClickable(pressScale = 0.84f, onClick = onTogglePlayClick),
                contentAlignment = Alignment.Center
            ) {
                PlumpDesktopPlayPauseCanvasIcon(
                    isPlaying = isPlaying,
                    color = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .softClickable(pressScale = 0.84f, onClick = onNextClick),
                contentAlignment = Alignment.Center
            ) {
                PlumpDesktopSkipCanvasIcon(
                    isNext = true,
                    color = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 右上角组：设置、关闭 (X)
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .softClickable(pressScale = 0.84f, onClick = onToggleSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                PlumpDesktopSettingsCanvasIcon(
                    color = if (isSettingsOpen) accentColor else Color.White.copy(alpha = 0.94f),
                    knobColor = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .softClickable(pressScale = 0.84f, onClick = onCloseClick),
                contentAlignment = Alignment.Center
            ) {
                PlumpDesktopCloseCanvasIcon(
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun PlumpDesktopLockCanvasIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.28f, h * 0.10f),
            size = Size(w * 0.44f, h * 0.50f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.16f, h * 0.44f),
            size = Size(w * 0.68f, h * 0.48f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
        )
    }
}

@Composable
private fun PlumpDesktopPlayModeCanvasIcon(
    playMode: PlayMode,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        when (playMode) {
            PlayMode.REPEAT_ALL, PlayMode.REPEAT_ONE -> {
                val pad = w * 0.14f
                val arcSize = Size(w - pad * 2f, h - pad * 2f)
                drawArc(
                    color = color,
                    startAngle = -155f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = color,
                    startAngle = 25f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                val topTip = Offset(w * 0.78f, h * 0.26f)
                drawLine(color, topTip, Offset(topTip.x - 3.6.dp.toPx(), topTip.y - 0.8.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(color, topTip, Offset(topTip.x - 0.8.dp.toPx(), topTip.y + 3.6.dp.toPx()), stroke, StrokeCap.Round)

                val botTip = Offset(w * 0.22f, h * 0.74f)
                drawLine(color, botTip, Offset(botTip.x + 3.6.dp.toPx(), botTip.y + 0.8.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(color, botTip, Offset(botTip.x + 0.8.dp.toPx(), botTip.y - 3.6.dp.toPx()), stroke, StrokeCap.Round)

                if (playMode == PlayMode.REPEAT_ONE) {
                    drawLine(color, Offset(w * 0.50f, h * 0.34f), Offset(w * 0.50f, h * 0.66f), stroke, StrokeCap.Round)
                }
            }
            PlayMode.SHUFFLE -> {
                val p1 = Path().apply {
                    moveTo(w * 0.15f, h * 0.30f)
                    cubicTo(w * 0.45f, h * 0.30f, w * 0.55f, h * 0.70f, w * 0.85f, h * 0.70f)
                }
                val p2 = Path().apply {
                    moveTo(w * 0.15f, h * 0.70f)
                    cubicTo(w * 0.45f, h * 0.70f, w * 0.55f, h * 0.30f, w * 0.85f, h * 0.30f)
                }
                drawPath(p1, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawPath(p2, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
                val tipTop = Offset(w * 0.86f, h * 0.30f)
                drawLine(color, tipTop, Offset(tipTop.x - 3.2.dp.toPx(), tipTop.y - 2.6.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(color, tipTop, Offset(tipTop.x - 3.2.dp.toPx(), tipTop.y + 2.6.dp.toPx()), stroke, StrokeCap.Round)
                val tipBot = Offset(w * 0.86f, h * 0.70f)
                drawLine(color, tipBot, Offset(tipBot.x - 3.2.dp.toPx(), tipBot.y - 2.6.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(color, tipBot, Offset(tipBot.x - 3.2.dp.toPx(), tipBot.y + 2.6.dp.toPx()), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun PlumpDesktopSkipCanvasIcon(
    isNext: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.16f
        val barH = h * 0.66f
        val barTop = (h - barH) / 2f
        val strokeJoin = 2.6.dp.toPx()

        if (isNext) {
            val tri = Path().apply {
                moveTo(w * 0.18f, h * 0.20f)
                lineTo(w * 0.68f, h * 0.50f)
                lineTo(w * 0.18f, h * 0.80f)
                close()
            }
            drawPath(tri, color)
            drawPath(tri, color, style = Stroke(width = strokeJoin, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.72f, barTop),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        } else {
            val tri = Path().apply {
                moveTo(w * 0.82f, h * 0.20f)
                lineTo(w * 0.32f, h * 0.50f)
                lineTo(w * 0.82f, h * 0.80f)
                close()
            }
            drawPath(tri, color)
            drawPath(tri, color, style = Stroke(width = strokeJoin, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.12f, barTop),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}

@Composable
private fun PlumpDesktopPlayPauseCanvasIcon(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (isPlaying) {
            val barW = w * 0.24f
            val barH = h * 0.72f
            val top = (h - barH) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.18f, top),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.58f, top),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        } else {
            val strokeJoin = 3.2.dp.toPx()
            val tri = Path().apply {
                moveTo(w * 0.24f, h * 0.16f)
                lineTo(w * 0.84f, h * 0.50f)
                lineTo(w * 0.24f, h * 0.84f)
                close()
            }
            drawPath(tri, color)
            drawPath(tri, color, style = Stroke(width = strokeJoin, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun PlumpDesktopSettingsCanvasIcon(
    color: Color,
    knobColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        drawLine(color, Offset(w * 0.14f, h * 0.32f), Offset(w * 0.86f, h * 0.32f), stroke, StrokeCap.Round)
        drawCircle(knobColor, radius = 3.2.dp.toPx(), center = Offset(w * 0.64f, h * 0.32f))

        drawLine(color, Offset(w * 0.14f, h * 0.68f), Offset(w * 0.86f, h * 0.68f), stroke, StrokeCap.Round)
        drawCircle(color, radius = 3.2.dp.toPx(), center = Offset(w * 0.36f, h * 0.68f))
    }
}

@Composable
private fun PlumpDesktopCloseCanvasIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.4.dp.toPx()
        drawLine(color, Offset(w * 0.18f, h * 0.18f), Offset(w * 0.82f, h * 0.82f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.82f, h * 0.18f), Offset(w * 0.18f, h * 0.82f), stroke, StrokeCap.Round)
    }
}

/**
 * 底部平滑延伸的设置面板（仅包含「歌词颜色」与「歌词字号」两项）
 */
@Composable
private fun DesktopExpandableSettingsSection(
    selectedScheme: DesktopLyricColorScheme,
    dynamicCoverPrimaryHex: Long,
    dynamicCoverSecondaryHex: Long,
    fontSizeSp: Float,
    accentColor: Color,
    onSelectScheme: (DesktopLyricColorScheme) -> Unit,
    onChangeFontSize: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // 1. 歌词颜色
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "颜色",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DesktopLyricColorScheme.entries.forEach { scheme ->
                    val selected = scheme == selectedScheme
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .softClickable(pressScale = 0.85f) { onSelectScheme(scheme) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(25.dp)
                                    .clip(CircleShape)
                                    .border(1.8.dp, Color.White, CircleShape)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(19.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        if (scheme == DesktopLyricColorScheme.DYNAMIC_COVER) {
                                            listOf(Color(dynamicCoverPrimaryHex), Color(dynamicCoverSecondaryHex))
                                        } else {
                                            listOf(Color(scheme.playedStartHex), Color(scheme.playedEndHex))
                                        }
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. 歌词字号
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "字号",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "A-",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .softClickable(pressScale = 0.85f) {
                        onChangeFontSize((fontSizeSp - 1f).coerceIn(15f, 26f))
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Slider(
                value = fontSizeSp.coerceIn(15f, 26f),
                onValueChange = { raw ->
                    val stepped = (raw * 2f).roundToInt() / 2f
                    onChangeFontSize(stepped)
                },
                valueRange = 15f..26f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
            )
            Text(
                text = "${fontSizeSp.roundToInt()}sp",
                color = accentColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Text(
                text = "A+",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .softClickable(pressScale = 0.85f) {
                        onChangeFontSize((fontSizeSp + 1f).coerceIn(15f, 26f))
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * 与 App 内歌词动效完全统一的桌面歌词单行渲染器（支持 KRC 逐字流光渐变 + 唱诵单字弹性上浮 + 长音辉光）
 */
@Composable
private fun DesktopUnifiedAnimatedLyricRow(
    row: DesktopLyricRenderRow,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    fontSizeSp: Float,
    playedStartColor: Color,
    playedEndColor: Color,
    unplayedColor: Color,
    textShadow: Shadow
) {
    var sampledPosMs by remember(row, isPlaying) {
        mutableLongStateOf(if (row.isActiveKaraoke) positionProvider() else -1L)
    }
    LaunchedEffect(row, isPlaying) {
        if (row.isActiveKaraoke) {
            sampledPosMs = positionProvider()
            if (isPlaying) {
                while (isActive) {
                    withFrameMillis {
                        sampledPosMs = positionProvider()
                    }
                }
            }
        } else {
            sampledPosMs = -1L
        }
    }

    val currentPosMs = if (row.isActiveKaraoke) sampledPosMs else -1L

    if (row.isActiveKaraoke && row.words.isNotEmpty() && currentPosMs >= 0L) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            row.words.forEach { word ->
                val wordProgress = when {
                    word.durationMs > 0L -> ((currentPosMs - word.startTimeMs).toFloat() / word.durationMs.toFloat()).coerceIn(0f, 1f)
                    currentPosMs >= word.startTimeMs -> 1f
                    else -> 0f
                }
                val isSinging = wordProgress in 0.01f..0.99f
                val wave = if (isSinging) sin(wordProgress * PI.toFloat()) else 0f
                val isLongNote = word.durationMs >= 700L && isSinging

                val wordAnnotated = buildAnnotatedString {
                    when {
                        wordProgress <= 0f -> {
                            withStyle(SpanStyle(color = unplayedColor, fontWeight = FontWeight.Bold, shadow = textShadow)) {
                                append(word.text)
                            }
                        }
                        wordProgress >= 1f -> {
                            withStyle(SpanStyle(color = playedStartColor, fontWeight = FontWeight.ExtraBold, shadow = textShadow)) {
                                append(word.text)
                            }
                        }
                        else -> {
                            val lStop = (wordProgress - 0.15f).coerceAtLeast(0f)
                            val rStop = (wordProgress + 0.15f).coerceAtMost(1f)
                            val glowShadow = if (isLongNote) {
                                Shadow(
                                    color = playedStartColor.copy(alpha = (0.85f * wave).coerceIn(0.25f, 1f)),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 14f * wave + 6f
                                )
                            } else textShadow
                            withStyle(
                                SpanStyle(
                                    brush = Brush.horizontalGradient(
                                        0.0f to playedStartColor,
                                        lStop to playedEndColor,
                                        rStop to unplayedColor,
                                        1.0f to unplayedColor
                                    ),
                                    fontWeight = FontWeight.ExtraBold,
                                    shadow = glowShadow
                                )
                            ) {
                                append(word.text)
                            }
                        }
                    }
                }

                Text(
                    text = wordAnnotated,
                    fontSize = fontSizeSp.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        translationY = -2.5f.dp.toPx() * wave
                        val s = 1f + 0.036f * wave
                        scaleX = s
                        scaleY = s
                    }
                )
            }
        }
    } else {
        Text(
            text = row.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (row.isActiveKaraoke) playedStartColor else unplayedColor.copy(alpha = if (row.isSecondaryRow) 0.82f else 0.94f),
                fontWeight = if (row.isActiveKaraoke) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize = fontSizeSp.sp,
                shadow = textShadow
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DesktopInterludeBreathingIndicator(
    lyricData: LyricData,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var sampledPosMs by remember(lyricData, isPlaying) {
        mutableLongStateOf(positionProvider())
    }
    LaunchedEffect(lyricData, isPlaying) {
        sampledPosMs = positionProvider()
        if (isPlaying && lyricData.lines.isNotEmpty()) {
            while (isActive) {
                withFrameMillis {
                    sampledPosMs = positionProvider()
                }
            }
        }
    }
    val interludeProgress = lyricData.getInterludeProgressAt(sampledPosMs)
    if (interludeProgress >= 0f) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0..2) {
                val dotPhase = ((interludeProgress * 3f) - i * 0.35f).coerceIn(0f, 1f)
                val pulse = sin(dotPhase * PI.toFloat()).coerceIn(0.25f, 1f)
                Box(
                    modifier = Modifier
                        .size((4.5f + 2.5f * pulse).dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = (0.35f + 0.60f * pulse).coerceIn(0f, 1f)))
                )
            }
        }
    }
}
