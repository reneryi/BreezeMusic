package com.concept.music.ui.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.Song
import com.concept.music.ui.theme.ConceptAnimatedOverlaySheet
import com.concept.music.ui.theme.ConceptMotion
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.isConceptDarkTheme
import com.concept.music.ui.theme.rememberCoverColorRoles
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.LyricHighlightTheme
import com.concept.music.ui.viewmodel.PlayMode
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import com.concept.music.ui.viewmodel.SavedPlayQueue
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 现代一体化玻璃拟态「播放队列 (QueueBottomSheet)」：
 * 1. 彻底解决顶部有横线的区域与下方渐变区域的颜色分层问题：顶部手势横条与主体内容同处一个连续渐变玻璃容器；
 * 2. 彻底解决横屏下弹窗黑框与右侧未渲染问题：采用应用窗口内全屏遮罩 + 横屏居中宽幅悬浮玻璃面板；
 * 3. 最近播放队列视图移除多余小字提示与卡片底部冗余按钮，滑动定位后点击卡片即可直接切换播放。
 */
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentIndex: Int,
    recentQueues: List<SavedPlayQueue> = emptyList(),
    activeQueueId: String = "",
    playMode: PlayMode = PlayMode.REPEAT_ALL,
    climaxModeEnabled: Boolean = false,
    accentColor: Color = ConceptPrimary,
    secondaryAccentColor: Color = ConceptPrimary,
    onToggleClimaxMode: () -> Unit = {},
    onCyclePlayMode: () -> Unit = {},
    onSwitchRecentQueue: (String) -> Unit = {},
    onDeleteRecentQueue: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onClear: () -> Unit,
    onReorderQueueByMood: (String) -> String = { "" },
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> }
) {


    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var activeMoodMode by remember { mutableStateOf("") }
    var showRecentDeckPage by remember { mutableStateOf(false) }

    var displayQueue by remember { mutableStateOf(queue) }
    var displayCurrentIndex by remember { mutableIntStateOf(currentIndex) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isSettlingDrop by remember { mutableStateOf(false) }
    val settleDropAnim = remember { Animatable(0f) }
    val rowHeightPx = with(density) { 56.5.dp.toPx() }

    val isReorderActive = dragStartIndex != null

    LaunchedEffect(queue, currentIndex, isReorderActive) {
        if (!isReorderActive) {
            displayQueue = queue
            displayCurrentIndex = currentIndex
        }
    }

    fun resolveRowStridePx(): Float {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.size >= 2) {
            val diff = (visible[1].offset - visible[0].offset).toFloat()
            if (diff > 24f) return diff
        }
        return (visible.firstOrNull()?.size?.toFloat() ?: rowHeightPx).coerceAtLeast(36f)
    }

    fun updateDragTargetFromOffset() {
        val startIdx = dragStartIndex ?: return
        val stridePx = resolveRowStridePx()
        var currentShift = (dragTargetIndex ?: startIdx) - startIdx
        val rawShift = dragOffsetY / stridePx
        while (rawShift - currentShift >= 0.52f && startIdx + currentShift < displayQueue.lastIndex) {
            currentShift += 1
        }
        while (rawShift - currentShift <= -0.52f && startIdx + currentShift > 0) {
            currentShift -= 1
        }
        dragTargetIndex = (startIdx + currentShift).coerceIn(0, displayQueue.lastIndex)
    }

    fun startQueueDrag(index: Int) {
        if (isSettlingDrop || index !in displayQueue.indices) return
        dragStartIndex = index
        dragTargetIndex = index
        dragOffsetY = 0f
    }

    fun handleQueueDragDelta(deltaY: Float) {
        val startIdx = dragStartIndex ?: return
        if (isSettlingDrop) return
        dragOffsetY += deltaY
        val stridePx = resolveRowStridePx()
        val minOffsetPx = -startIdx * stridePx
        val maxOffsetPx = (displayQueue.lastIndex - startIdx) * stridePx
        dragOffsetY = dragOffsetY.coerceIn(minOffsetPx - stridePx * 0.25f, maxOffsetPx + stridePx * 0.25f)
        updateDragTargetFromOffset()

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val startItemInfo = visibleItems.firstOrNull { it.index == startIdx }
        if (startItemInfo != null) {
            val viewportStart = listState.layoutInfo.viewportStartOffset
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            val draggedCenterY = startItemInfo.offset + dragOffsetY + startItemInfo.size / 2f
            val edgeZonePx = stridePx * 1.1f
            if (draggedCenterY < viewportStart + edgeZonePx && listState.canScrollBackward) {
                coroutineScope.launch {
                    val consumed = listState.scrollBy(-14f)
                    if (consumed != 0f && dragStartIndex != null && !isSettlingDrop) {
                        dragOffsetY += consumed
                        updateDragTargetFromOffset()
                    }
                }
            } else if (draggedCenterY > viewportEnd - edgeZonePx && listState.canScrollForward) {
                coroutineScope.launch {
                    val consumed = listState.scrollBy(14f)
                    if (consumed != 0f && dragStartIndex != null && !isSettlingDrop) {
                        dragOffsetY += consumed
                        updateDragTargetFromOffset()
                    }
                }
            }
        }
    }

    fun finishQueueDrag() {
        val startIdx = dragStartIndex ?: return
        val targetIdx = (dragTargetIndex ?: startIdx).coerceIn(0, displayQueue.lastIndex)
        if (isSettlingDrop) return
        val stridePx = resolveRowStridePx()
        val targetSettleOffsetPx = (targetIdx - startIdx) * stridePx
        isSettlingDrop = true
        coroutineScope.launch {
            settleDropAnim.snapTo(dragOffsetY)
            settleDropAnim.animateTo(
                targetValue = targetSettleOffsetPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            if (startIdx in displayQueue.indices && targetIdx in displayQueue.indices && startIdx != targetIdx) {
                val playingSongId = displayQueue.getOrNull(displayCurrentIndex)?.id
                val updated = displayQueue.toMutableList()
                val movedItem = updated.removeAt(startIdx)
                updated.add(targetIdx, movedItem)
                displayQueue = updated
                if (playingSongId != null) {
                    val newPlayingIdx = updated.indexOfFirst { it.id == playingSongId }
                    if (newPlayingIdx >= 0) displayCurrentIndex = newPlayingIdx
                }
                onMoveQueueItem(startIdx, targetIdx)
            }
            dragStartIndex = null
            dragTargetIndex = null
            dragOffsetY = 0f
            isSettlingDrop = false
        }
    }

    val isDark = isConceptDarkTheme()
    val baseSheetBg = conceptSheetContainerColor()
    val cardBg = if (isDark) {
        Color.White.copy(alpha = 0.055f).compositeOver(conceptElevatedSurfaceColor())
    } else {
        conceptElevatedSurfaceColor()
    }
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val roles = rememberCoverColorRoles(accentColor)
    val secondaryRoles = rememberCoverColorRoles(secondaryAccentColor)
    val goldRoles = rememberCoverColorRoles(VipGold)

    // 从顶部横线区域到底部列表的一体化平滑连续渐变，彻底消除顶部颜色分层
    val panelTopColor = roles.primaryContainer.copy(alpha = 0.55f).compositeOver(baseSheetBg.copy(alpha = 0.96f))
    val panelMidColor = secondaryRoles.primaryContainer.copy(alpha = 0.22f).compositeOver(baseSheetBg.copy(alpha = 0.97f))
    val panelBottomColor = baseSheetBg.copy(alpha = 0.99f)
    val panelShape = if (isLandscape) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    }

    val activePlayingSongId = displayQueue.getOrNull(displayCurrentIndex)?.id
    LaunchedEffect(activePlayingSongId, showRecentDeckPage) {
        if (!showRecentDeckPage && displayCurrentIndex in displayQueue.indices && !isReorderActive) {
            listState.scrollToItem((displayCurrentIndex - 1).coerceAtLeast(0))
        }
    }

    ConceptAnimatedOverlaySheet(
        onDismiss = onDismiss,
        isLandscape = isLandscape,
        dismissDragThresholdPx = 110f
    ) {
        // 一体化连续渐变玻璃容器（入退场弹簧与遮罩渐隐由 ConceptAnimatedOverlaySheet 统一在 Draw 阶段驱动，无外框描边）
        Box(
            modifier = Modifier
                .then(
                    if (isLandscape) {
                        val isTablet = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                            .widthIn(max = if (isTablet) 760.dp else 720.dp)
                            .fillMaxWidth(if (isTablet) 0.86f else 0.92f)
                            .heightIn(max = if (isTablet) 600.dp else 420.dp)
                    } else {
                        val isTablet = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
                        if (isTablet) {
                            Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    }
                )
                .shadow(elevation = 28.dp, shape = panelShape)
                .clip(panelShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(panelTopColor, panelMidColor, panelBottomColor)
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (isLandscape) 22.dp else 18.dp,
                        end = if (isLandscape) 22.dp else 18.dp,
                        top = if (isLandscape) 14.dp else 8.dp,
                        bottom = if (isLandscape) 12.dp else 8.dp
                    )
                    .then(if (!isLandscape) Modifier.navigationBarsPadding() else Modifier)
            ) {
                // 顶部横线指示条（支持跟手平滑下滑关闭与松手弹性回弹）
                if (!isLandscape) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 8.dp)
                            .sheetVerticalDragGesture(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(roles.primary.copy(alpha = 0.45f))
                        )
                    }
                }

                // 1. 顶部栏：标题 + 右上角 [最近队列叠卡按钮] + [统一播放模式小按钮] + [清空] + (横屏关闭按钮)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showRecentDeckPage) {
                            Surface(
                                shape = CircleShape,
                                color = roles.secondaryContainer,
                                modifier = Modifier
                                    .size(30.dp)
                                    .softClickable { showRecentDeckPage = false }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "返回当前队列",
                                        tint = roles.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (showRecentDeckPage) "最近播放队列" else "播放队列",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary,
                                fontSize = 18.sp
                            )
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = roles.secondaryContainer
                        ) {
                            Text(
                                text = if (showRecentDeckPage) {
                                    "${recentQueues.size.coerceAtMost(5)}/5"
                                } else if (queue.isNotEmpty()) {
                                    "${(currentIndex + 1).coerceAtMost(queue.size)}/${queue.size}"
                                } else "0/0",
                                color = roles.onSecondaryContainer,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }

                    // 右上角按钮簇：最近按钮 -> 播放顺序模式小按钮（清空左边）-> 清空按钮 -> 关闭按钮（横屏）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QueueRecentStackIconButton(
                            active = showRecentDeckPage,
                            count = recentQueues.size.coerceAtMost(5),
                            accentColor = accentColor,
                            onClick = { showRecentDeckPage = !showRecentDeckPage }
                        )

                        QueueUnifiedPlayModeSmallButton(
                            playMode = playMode,
                            accentColor = accentColor,
                            onClick = {
                                onCyclePlayMode()
                                val nextLabel = when (playMode) {
                                    PlayMode.REPEAT_ALL -> "单曲循环"
                                    PlayMode.REPEAT_ONE -> "随机播放"
                                    PlayMode.SHUFFLE -> "列表循环"
                                }
                                Toast.makeText(context, "已切换为：$nextLabel", Toast.LENGTH_SHORT).show()
                            }
                        )

                        if (queue.isNotEmpty() && !showRecentDeckPage) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFF6B6B).copy(alpha = 0.14f),
                                modifier = Modifier
                                    .height(32.dp)
                                    .softClickable(onClick = onClear)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteSweep,
                                        contentDescription = "清空",
                                        tint = Color(0xFFFF6B6B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "清空",
                                        color = Color(0xFFFF6B6B),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (isLandscape) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .size(32.dp)
                                    .softClickable(onClick = requestDismiss)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "关闭",
                                        tint = textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = showRecentDeckPage,
                    transitionSpec = {
                        (fadeIn(tween(240, easing = ConceptMotion.EmphasizedDecelerate)) +
                            scaleIn(initialScale = 0.94f, animationSpec = ConceptMotion.spatialPageSpring()))
                            .togetherWith(
                                fadeOut(tween(180, easing = ConceptMotion.EmphasizedAccelerate)) +
                                    scaleOut(targetScale = 0.94f, animationSpec = ConceptMotion.spatialPageSpring())
                            )
                    },
                    label = "QueueDeckViewSwitch"
                ) { inRecentDeck ->
                    if (inRecentDeck) {
                        // 3D 错位重叠卡片挑选页面（已精简小字说明与底部多余按钮）
                        RecentQueuesStaggeredDeckPage(
                            recentQueues = recentQueues.take(5),
                            activeQueueId = activeQueueId,
                            accentColor = accentColor,
                            secondaryAccentColor = secondaryAccentColor,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            isLandscape = isLandscape,
                            onSelectQueue = { qId ->
                                onSwitchRecentQueue(qId)
                                showRecentDeckPage = false
                            },
                            onDeleteQueue = onDeleteRecentQueue
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 副歌速刷 + AI 漫游智能工具条（无描边，色阶容器分离）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (climaxModeEnabled) goldRoles.primaryContainer else cardBg,
                                    modifier = Modifier
                                        .softCardClickable(shape = RoundedCornerShape(12.dp)) {
                                            onToggleClimaxMode()
                                            Toast.makeText(
                                                context,
                                                if (!climaxModeEnabled) "已开启副歌速刷 (30秒高潮连播)" else "已关闭副歌速刷",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Whatshot,
                                            contentDescription = "副歌速刷",
                                            tint = if (climaxModeEnabled) goldRoles.onPrimaryContainer else roles.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (climaxModeEnabled) "副歌速刷·开" else "副歌速刷",
                                            color = if (climaxModeEnabled) goldRoles.onPrimaryContainer else textPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                if (queue.size > 2) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cardBg,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                tint = roles.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            listOf(
                                                "sleep" to "睡前渐缓",
                                                "commute" to "通勤渐燃",
                                                "harmonic" to "声学漫游"
                                            ).forEach { (modeKey, label) ->
                                                val isSelected = activeMoodMode == modeKey
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) roles.primary else roles.secondaryContainer,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .softCardClickable(shape = RoundedCornerShape(8.dp)) {
                                                            activeMoodMode = modeKey
                                                            val msg = onReorderQueueByMood(modeKey)
                                                            if (msg.isNotBlank()) {
                                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.padding(vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            color = if (isSelected) roles.onPrimary else roles.onSecondaryContainer,
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (queue.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isLandscape) 180.dp else 220.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = null,
                                            tint = textSecondary.copy(alpha = 0.45f),
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("播放队列为空", color = textSecondary, fontSize = 13.sp)
                                    }
                                }
                            } else {
                                val queueItemKeys = remember(displayQueue) {
                                    val seen = HashSet<String>(displayQueue.size)
                                    displayQueue.mapIndexed { idx, s ->
                                        val base = "q_${s.id}_${s.hash}"
                                        if (seen.add(base)) base else "${base}_dup_$idx"
                                    }
                                }
                                val isTabletQueue = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.heightIn(
                                        max = when {
                                            isTabletQueue && isLandscape -> 440.dp
                                            isTabletQueue -> 540.dp
                                            isLandscape -> 260.dp
                                            else -> 420.dp
                                        }
                                    )
                                ) {
                                    itemsIndexed(
                                        items = displayQueue,
                                        key = { idx, item -> queueItemKeys.getOrElse(idx) { "q_${item.id}_$idx" } },
                                        contentType = { _, _ -> "queue_song_row" }
                                    ) { index, item ->
                                        val itemKey = queueItemKeys.getOrElse(index) { "q_${item.id}_$index" }
                                        val activeStartIdx = dragStartIndex
                                        val activeTargetIdx = dragTargetIndex ?: activeStartIdx
                                        val isPlayingThis = index == displayCurrentIndex
                                        val isDraggingThis = isReorderActive && index == activeStartIdx
                                        val currentIdxState by rememberUpdatedState(index)

                                        val stridePx = resolveRowStridePx()
                                        val targetNeighborShiftPx = when {
                                            !isReorderActive || activeStartIdx == null || activeTargetIdx == null -> 0f
                                            index == activeStartIdx -> 0f
                                            activeStartIdx < activeTargetIdx && index in (activeStartIdx + 1)..activeTargetIdx -> -stridePx
                                            activeStartIdx > activeTargetIdx && index in activeTargetIdx until activeStartIdx -> stridePx
                                            else -> 0f
                                        }

                                        val neighborOffsetAnim = remember(itemKey) { Animatable(0f) }
                                        LaunchedEffect(isReorderActive, targetNeighborShiftPx) {
                                            if (!isReorderActive) {
                                                neighborOffsetAnim.snapTo(0f)
                                            } else {
                                                neighborOffsetAnim.animateTo(
                                                    targetValue = targetNeighborShiftPx,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                            }
                                        }

                                        val displayOrderIndex = when {
                                            !isReorderActive || activeStartIdx == null || activeTargetIdx == null -> index
                                            index == activeStartIdx -> activeTargetIdx
                                            activeStartIdx < activeTargetIdx && index in (activeStartIdx + 1)..activeTargetIdx -> index - 1
                                            activeStartIdx > activeTargetIdx && index in activeTargetIdx until activeStartIdx -> index + 1
                                            else -> index
                                        }

                                        val dragScale by animateFloatAsState(
                                            targetValue = if (isDraggingThis && !isSettlingDrop) 1.028f else 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            ),
                                            label = "queueDragScale"
                                        )
                                        val dragElevation by animateDpAsState(
                                            targetValue = if (isDraggingThis && !isSettlingDrop) 14.dp else 0.dp,
                                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                            label = "queueDragElevation"
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .zIndex(if (isDraggingThis) 10f else 0f)
                                                .graphicsLayer {
                                                    translationY = when {
                                                        !isReorderActive -> 0f
                                                        isDraggingThis -> if (isSettlingDrop) settleDropAnim.value else dragOffsetY
                                                        else -> neighborOffsetAnim.value
                                                    }
                                                    scaleX = dragScale
                                                    scaleY = dragScale
                                                    shadowElevation = dragElevation.toPx()
                                                    shape = RoundedCornerShape(14.dp)
                                                    clip = false
                                                }
                                                .background(
                                                    when {
                                                        isDraggingThis -> roles.primaryContainer
                                                        isPlayingThis -> roles.primaryContainer
                                                        else -> Color.Transparent
                                                    },
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .pointerInput(displayQueue.size) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            startQueueDrag(currentIdxState)
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            handleQueueDragDelta(dragAmount.y)
                                                        },
                                                        onDragEnd = {
                                                            finishQueueDrag()
                                                        },
                                                        onDragCancel = {
                                                            finishQueueDrag()
                                                        }
                                                    )
                                                }
                                                .clickable(enabled = !isReorderActive) {
                                                    onSongClick(item)
                                                    requestDismiss()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier.width(28.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (isPlayingThis) {
                                                        PlayingAudioWaveIndicator(color = roles.onPrimaryContainer)
                                                    } else {
                                                        Text(
                                                            text = (displayOrderIndex + 1).toString().padStart(2, '0'),
                                                            color = textSecondary.copy(alpha = 0.6f),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(roles.secondaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (item.coverUrl.isNotBlank()) {
                                                        AsyncImage(
                                                            model = item.coverUrl,
                                                            contentDescription = item.title,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Rounded.MusicNote,
                                                            contentDescription = null,
                                                            tint = roles.onSecondaryContainer,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.title,
                                                        color = if (isPlayingThis) roles.onPrimaryContainer else textPrimary,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                                                            fontSize = 14.sp
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (item.isHiRes) {
                                                            BadgeTag(text = "Hi-Res", color = VipGold)
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                        } else if (item.isLossless) {
                                                            BadgeTag(text = "SQ", color = roles.primary)
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                        }
                                                        Text(
                                                            text = item.artist,
                                                            color = if (isPlayingThis) roles.onPrimaryContainerSubtle else textSecondary,
                                                            fontSize = 11.5.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .pointerInput(displayQueue.size) {
                                                            detectVerticalDragGestures(
                                                                onDragStart = {
                                                                    startQueueDrag(currentIdxState)
                                                                },
                                                                onVerticalDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    handleQueueDragDelta(dragAmount)
                                                                },
                                                                onDragEnd = {
                                                                    finishQueueDrag()
                                                                },
                                                                onDragCancel = {
                                                                    finishQueueDrag()
                                                                }
                                                            )
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DragHandle,
                                                        contentDescription = "按住拖动排序",
                                                        tint = if (isDraggingThis) roles.onPrimaryContainer else textSecondary.copy(alpha = 0.45f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { onRemoveSong(item) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "移除",
                                                        tint = textSecondary.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            if (!isPlayingThis) {
                                                HorizontalDivider(
                                                    color = textPrimary.copy(alpha = 0.05f),
                                                    thickness = 0.5.dp,
                                                    modifier = Modifier.padding(start = 80.dp, end = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

/**
 * 自研图标：最近队列立体错位叠卡按钮
 */
@Composable
private fun QueueRecentStackIconButton(
    active: Boolean,
    count: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    val roles = rememberCoverColorRoles(accentColor)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) roles.primary else roles.secondaryContainer,
        modifier = Modifier
            .height(32.dp)
            .softCardClickable(shape = RoundedCornerShape(10.dp), onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp)
        ) {
            val iconColor = if (active) roles.onPrimary else roles.onSecondaryContainer
            Canvas(modifier = Modifier.size(16.dp)) {
                val strokeW = 1.4.dp.toPx()
                drawRoundRect(
                    color = iconColor.copy(alpha = 0.45f),
                    topLeft = Offset(size.width * 0.28f, size.height * 0.08f),
                    size = Size(size.width * 0.64f, size.height * 0.68f),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                    style = Stroke(width = strokeW)
                )
                drawRoundRect(
                    color = iconColor.copy(alpha = 0.72f),
                    topLeft = Offset(size.width * 0.16f, size.height * 0.18f),
                    size = Size(size.width * 0.64f, size.height * 0.68f),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                    style = Stroke(width = strokeW)
                )
                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(size.width * 0.04f, size.height * 0.28f),
                    size = Size(size.width * 0.64f, size.height * 0.68f),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                    style = Stroke(width = strokeW)
                )
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (count > 0) "最近 $count" else "最近",
                color = iconColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * 与封面播放页完全统一的矢量播放顺序模式小按钮（置于右上角，清空按钮左边）
 */
@Composable
private fun QueueUnifiedPlayModeSmallButton(
    playMode: PlayMode,
    accentColor: Color,
    onClick: () -> Unit
) {
    val roles = rememberCoverColorRoles(accentColor)
    val label = when (playMode) {
        PlayMode.REPEAT_ALL -> "列表"
        PlayMode.REPEAT_ONE -> "单曲"
        PlayMode.SHUFFLE -> "随机"
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = roles.secondaryContainer,
        modifier = Modifier
            .height(32.dp)
            .softCardClickable(shape = RoundedCornerShape(10.dp), onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp)
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val stroke = 1.65.dp.toPx()
                val w = size.width
                val h = size.height
                when (playMode) {
                    PlayMode.REPEAT_ALL, PlayMode.REPEAT_ONE -> {
                        val topY = h * 0.28f
                        val botY = h * 0.72f
                        val leftX = w * 0.14f
                        val rightX = w * 0.86f
                        val path = Path().apply {
                            moveTo(leftX, h * 0.45f)
                            lineTo(leftX, topY)
                            lineTo(rightX - w * 0.12f, topY)
                            moveTo(rightX - w * 0.24f, topY - h * 0.13f)
                            lineTo(rightX - w * 0.08f, topY)
                            lineTo(rightX - w * 0.24f, topY + h * 0.13f)

                            moveTo(rightX, h * 0.55f)
                            lineTo(rightX, botY)
                            lineTo(leftX + w * 0.12f, botY)
                            moveTo(leftX + w * 0.24f, botY - h * 0.13f)
                            lineTo(leftX + w * 0.08f, botY)
                            lineTo(leftX + w * 0.24f, botY + h * 0.13f)
                        }
                        drawPath(
                            path = path,
                            color = roles.onSecondaryContainer,
                            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        if (playMode == PlayMode.REPEAT_ONE) {
                            drawLine(
                                color = roles.onSecondaryContainer,
                                start = Offset(w * 0.5f, h * 0.39f),
                                end = Offset(w * 0.5f, h * 0.61f),
                                strokeWidth = stroke * 0.95f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    PlayMode.SHUFFLE -> {
                        val leftX = w * 0.14f
                        val rightX = w * 0.86f
                        val topY = h * 0.28f
                        val botY = h * 0.72f
                        val path = Path().apply {
                            moveTo(leftX, botY)
                            cubicTo(w * 0.45f, botY, w * 0.55f, topY, rightX - w * 0.08f, topY)
                            moveTo(rightX - w * 0.22f, topY - h * 0.12f)
                            lineTo(rightX - w * 0.06f, topY)
                            lineTo(rightX - w * 0.22f, topY + h * 0.12f)

                            moveTo(leftX, topY)
                            cubicTo(w * 0.45f, topY, w * 0.55f, botY, rightX - w * 0.08f, botY)
                            moveTo(rightX - w * 0.22f, botY - h * 0.12f)
                            lineTo(rightX - w * 0.06f, botY)
                            lineTo(rightX - w * 0.22f, botY + h * 0.12f)
                        }
                        drawPath(
                            path = path,
                            color = roles.onSecondaryContainer,
                            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = roles.onSecondaryContainer,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * 3D 错位重叠卡片队列挑选页面 (RecentQueuesStaggeredDeckPage)：
 * 支持最多 5 个最近播放队列，采用透视景深、Y/Z 轴倾角、错位叠放与左右手势滑动切换；
 * 已移除顶部小字说明与卡片底部多余按钮，滑动定位后点击卡片即可直接切换播放。
 */
@Composable
private fun RecentQueuesStaggeredDeckPage(
    recentQueues: List<SavedPlayQueue>,
    activeQueueId: String,
    accentColor: Color,
    secondaryAccentColor: Color,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    isLandscape: Boolean,
    onSelectQueue: (String) -> Unit,
    onDeleteQueue: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val roles = rememberCoverColorRoles(accentColor)

    if (recentQueues.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无最近播放队列记录", color = textSecondary, fontSize = 13.sp)
        }
        return
    }

    val initialIdx = remember(recentQueues, activeQueueId) {
        recentQueues.indexOfFirst { it.id == activeQueueId }.takeIf { it >= 0 } ?: 0
    }
    val animatedFocusIndex = remember { Animatable(initialIdx.toFloat()) }

    LaunchedEffect(recentQueues.size) {
        val clamped = animatedFocusIndex.value.coerceIn(0f, (recentQueues.size - 1).coerceAtLeast(0).toFloat())
        animatedFocusIndex.snapTo(clamped)
    }

    // 使用 derivedStateOf 隔离焦点索引整数态：横滑连续拖拽期间绝不触发全弹窗重组
    val activeFocusInt by remember(recentQueues.size) {
        derivedStateOf {
            animatedFocusIndex.value.roundToInt().coerceIn(0, (recentQueues.size - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 3D 错位重叠卡片舞台
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 225.dp else 286.dp)
                .pointerInput(recentQueues.size) {
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragTotal += dragAmount
                            val deltaIdx = -dragAmount / 240f
                            val nextVal = (animatedFocusIndex.value + deltaIdx)
                                .coerceIn(0f, (recentQueues.size - 1).coerceAtLeast(0).toFloat())
                            scope.launch { animatedFocusIndex.snapTo(nextVal) }
                        },
                        onDragEnd = {
                            val target = animatedFocusIndex.value.roundToInt()
                                .coerceIn(0, recentQueues.lastIndex)
                            scope.launch {
                                animatedFocusIndex.animateTo(
                                    targetValue = target.toFloat(),
                                    animationSpec = ConceptMotion.expressiveSpring()
                                )
                            }
                        },
                        onDragCancel = {
                            val target = animatedFocusIndex.value.roundToInt()
                                .coerceIn(0, recentQueues.lastIndex)
                            scope.launch {
                                animatedFocusIndex.animateTo(
                                    targetValue = target.toFloat(),
                                    animationSpec = ConceptMotion.gentleBounceSpring()
                                )
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            recentQueues.forEachIndexed { idx, savedQueue ->
                val isFocused = idx == activeFocusInt
                val staticDistance = abs(idx - activeFocusInt)
                val isCurrentPlayingQueue = savedQueue.id == activeQueueId || (activeQueueId.isBlank() && idx == 0)
                val coverSong = savedQueue.songs.getOrNull(savedQueue.currentIndex) ?: savedQueue.songs.firstOrNull()
                val cardShape = RoundedCornerShape(22.dp)

                Surface(
                    shape = cardShape,
                    color = cardBg,
                    modifier = Modifier
                        .zIndex(10f - staticDistance)
                        .width(if (isLandscape) 330.dp else 288.dp)
                        .fillMaxHeight(0.94f)
                        .graphicsLayer {
                            // 严格在 Draw/Layer 阶段读取 animatedFocusIndex.value 并驱动 3D 变换与阴影，拖拽全程 0 次重组
                            val relativeOffset = idx - animatedFocusIndex.value
                            val absOffset = abs(relativeOffset)
                            val cardScale = (1f - absOffset * 0.11f).coerceIn(0.72f, 1f)
                            val cardAlpha = (1f - absOffset * 0.24f).coerceIn(0.35f, 1f)
                            cameraDistance = 16f * density
                            translationX = relativeOffset * 58f * density
                            translationY = absOffset * 13f * density
                            scaleX = cardScale
                            scaleY = cardScale
                            rotationY = (-relativeOffset * 12f).coerceIn(-28f, 28f)
                            rotationZ = (relativeOffset * 2.4f).coerceIn(-7f, 7f)
                            alpha = cardAlpha
                            shadowElevation = (20f - absOffset * 5f).coerceAtLeast(4f) * density
                            shape = cardShape
                            clip = true
                        }
                        .softCardClickable(shape = cardShape) {
                            if (!isFocused) {
                                scope.launch {
                                    animatedFocusIndex.animateTo(
                                        idx.toFloat(),
                                        ConceptMotion.expressiveSpring()
                                    )
                                }
                            } else {
                                onSelectQueue(savedQueue.id)
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        if (isFocused) roles.primaryContainer else roles.primaryContainer.copy(alpha = 0.35f),
                                        cardBg
                                    )
                                )
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 卡片头部：队列封面 + 标题 + 删除按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(roles.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (coverSong != null && coverSong.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = coverSong.coverUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = roles.onSecondaryContainer)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = if (isCurrentPlayingQueue) roles.primary else textPrimary.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = if (isCurrentPlayingQueue) "当前队列" else "队列 #${idx + 1}",
                                            color = if (isCurrentPlayingQueue) roles.onPrimary else textSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "共 ${savedQueue.songs.size} 首",
                                        color = textSecondary,
                                        fontSize = 10.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = savedQueue.title,
                                    color = textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onDeleteQueue(savedQueue.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "删除此队列",
                                    tint = textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // 卡片曲目预览列表（已移除底部冗余按钮，点击卡片即刻切换播放）
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val maxPreview = if (isLandscape) 4 else 5
                            savedQueue.songs.take(maxPreview).forEachIndexed { sIdx, s ->
                                val isCurSong = sIdx == savedQueue.currentIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurSong) roles.primaryContainer else Color.Transparent)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = (sIdx + 1).toString().padStart(2, '0'),
                                        color = if (isCurSong) roles.onPrimaryContainer else textSecondary.copy(alpha = 0.6f),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(20.dp)
                                    )
                                    Text(
                                        text = s.title,
                                        color = if (isCurSong) roles.onPrimaryContainer else textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurSong) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = s.artist,
                                        color = if (isCurSong) roles.onPrimaryContainerSubtle else textSecondary,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 76.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 底部卡片位置指示点（支持点击快速跳切）
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            recentQueues.forEachIndexed { idx, _ ->
                val selected = idx == activeFocusInt
                val dotWidth by animateDpAsState(
                    targetValue = if (selected) 20.dp else 6.dp,
                    animationSpec = spring(
                        dampingRatio = 0.74f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "deckDotWidth_$idx"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (selected) roles.primary else textSecondary.copy(alpha = 0.3f),
                    animationSpec = tween(220),
                    label = "deckDotColor_$idx"
                )
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(dotColor)
                        .softClickable {
                            scope.launch {
                                animatedFocusIndex.animateTo(
                                    idx.toFloat(),
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }
                )
            }
        }
    }
}

