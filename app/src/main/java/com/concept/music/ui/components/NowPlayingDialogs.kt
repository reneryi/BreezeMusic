package com.concept.music.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.Song
import com.concept.music.service.SystemLyricBridge
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.conceptSheetSpringEntrance
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.LyricHighlightTheme
import com.concept.music.ui.viewmodel.PlayMode
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import com.concept.music.ui.viewmodel.SavedPlayQueue
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 歌词显示设置抽屉 (LyricSettingsDialog)：
 * - 已按用户要求移除「音译注音」、「外语翻译」（两者直接在歌词页按歌曲语言自动展示开关，默认关闭）；
 * - 已移除「全局桌面悬浮歌词」（封面播放页进度条上方已有专属按钮入口）；
 * - 所有选中项取消边框线设计，采用现代无边框色块填充。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricSettingsDialog(
    fontSize: Float,
    translationScale: Float = 0.82f,
    alignment: LyricAlignment,
    wordByWord: Boolean,
    blurEnabled: Boolean,
    blurIntensity: Float,
    lineSpacing: Float,
    offsetMs: Long,
    highlightTheme: LyricHighlightTheme,
    backgroundDim: Float,
    keepScreenOn: Boolean,
    dynamicAccentColor: Color = ConceptPrimary,
    onSetFontSize: (Float) -> Unit,
    onSetTranslationScale: (Float) -> Unit = {},
    onSetAlignment: (LyricAlignment) -> Unit,
    onSetWordByWord: (Boolean) -> Unit,
    onSetBlurEnabled: (Boolean) -> Unit,
    onSetBlurIntensity: (Float) -> Unit,
    onSetLineSpacing: (Float) -> Unit,
    onSetOffsetMs: (Long) -> Unit,
    onSetHighlightTheme: (LyricHighlightTheme) -> Unit,
    onSetBackgroundDim: (Float) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val systemLyricState by SystemLyricBridge.state.collectAsState()
    val statusBarLyricEnabled = systemLyricState.statusBarBroadcastEnabled
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBg = conceptSheetContainerColor()
    val cardBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val themeAccent = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = textPrimary,
        windowInsets = WindowInsets(0, 0, 0, 0),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides textPrimary) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "歌词显示设置",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "调节歌词配色、字号排版与景深效果",
                            color = textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = themeAccent.copy(alpha = 0.14f),
                        contentColor = themeAccent,
                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(12.dp)) {
                            onSetFontSize(22f)
                            onSetTranslationScale(0.82f)
                            onSetLineSpacing(14f)
                            onSetAlignment(LyricAlignment.LEFT)
                            onSetBlurEnabled(true)
                            onSetBlurIntensity(1.0f)
                            onSetBackgroundDim(0.72f)
                            onSetOffsetMs(0L)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = themeAccent, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("恢复默认", color = themeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 1. 歌词焦点配色与对齐排版（无边框现代胶囊）
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "歌词焦点配色",
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LyricHighlightTheme.entries.forEach { theme ->
                                val selected = highlightTheme == theme
                                val swatchColor = if (theme == LyricHighlightTheme.DYNAMIC_COVER) {
                                    dynamicAccentColor
                                } else {
                                    Color(theme.primaryHex)
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) swatchColor.copy(alpha = 0.22f) else sheetBg,
                                    contentColor = textPrimary,
                                    modifier = Modifier.softCardClickable(shape = RoundedCornerShape(12.dp)) {
                                        onSetHighlightTheme(theme)
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = theme.label,
                                            color = if (selected) swatchColor else textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = textPrimary.copy(alpha = 0.07f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "对齐方式",
                                color = textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LyricAlignment.entries.forEach { item ->
                                    val selected = alignment == item
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) themeAccent.copy(alpha = 0.20f) else sheetBg,
                                        contentColor = if (selected) themeAccent else textPrimary,
                                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(10.dp)) {
                                            onSetAlignment(item)
                                        }
                                    ) {
                                        Text(
                                            text = item.label,
                                            color = if (selected) themeAccent else textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 字号、副歌词比例与行距调节
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("主歌词字号", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${fontSize.toInt()} sp", color = themeAccent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = fontSize,
                            onValueChange = onSetFontSize,
                            valueRange = 16f..30f,
                            steps = 6,
                            colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                            modifier = Modifier.height(30.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("译文/注音字号比例", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${(translationScale * 100).roundToInt()}%", color = themeAccent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = translationScale,
                            onValueChange = onSetTranslationScale,
                            valueRange = 0.65f..0.95f,
                            colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                            modifier = Modifier.height(30.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("行间距", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${lineSpacing.toInt()} dp", color = themeAccent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = lineSpacing,
                            onValueChange = onSetLineSpacing,
                            valueRange = 8f..24f,
                            steps = 7,
                            colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                // 3. 景深模糊与背景暗度
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("阶梯景深模糊", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("非当前行随距离自然虚化", color = textSecondary, fontSize = 11.5.sp)
                            }
                            Switch(
                                checked = blurEnabled,
                                onCheckedChange = onSetBlurEnabled,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = themeAccent)
                            )
                        }

                        if (blurEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("模糊强度", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(String.format(Locale.getDefault(), "%.1fx", blurIntensity), color = themeAccent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                            Slider(
                                value = blurIntensity,
                                onValueChange = onSetBlurIntensity,
                                valueRange = 0.4f..1.8f,
                                colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                                modifier = Modifier.height(28.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("背景压暗度", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${(backgroundDim * 100).toInt()}%", color = themeAccent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = backgroundDim,
                            onValueChange = onSetBackgroundDim,
                            valueRange = 0.40f..0.92f,
                            colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // 4. 歌词时间轴微调校准
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("时间轴校准", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = String.format(Locale.getDefault(), "%+.1f 秒", offsetMs / 1000f),
                                color = if (offsetMs != 0L) VipGold else themeAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                -500L to "-0.5s",
                                -100L to "-0.1s",
                                0L to "重置 0s",
                                100L to "+0.1s",
                                500L to "+0.5s"
                            ).forEach { (delta, label) ->
                                val isHighlight = delta == 0L && offsetMs == 0L
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isHighlight) themeAccent.copy(alpha = 0.20f) else sheetBg,
                                    contentColor = if (isHighlight) themeAccent else textPrimary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .softCardClickable {
                                            if (delta == 0L) onSetOffsetMs(0L) else onSetOffsetMs(offsetMs + delta)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = label,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHighlight) themeAccent else textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. 逐字动效、屏幕常亮与状态栏歌词广播
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LyricSettingSwitchRow(
                            title = "KRC 逐字流光染色",
                            subtitle = "歌词随人声毫秒级逐字扫光",
                            checked = wordByWord,
                            accentColor = themeAccent,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onCheckedChange = onSetWordByWord
                        )
                        LyricSettingSwitchRow(
                            title = "歌词页屏幕常亮",
                            subtitle = "播放时保持歌词界面常亮不息屏",
                            checked = keepScreenOn,
                            accentColor = themeAccent,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onCheckedChange = onSetKeepScreenOn
                        )
                        LyricSettingSwitchRow(
                            title = "状态栏歌词广播",
                            subtitle = "适配 Flyme / HyperOS / OriginOS 状态栏歌词",
                            checked = statusBarLyricEnabled,
                            accentColor = themeAccent,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onCheckedChange = { SystemLyricBridge.setStatusBarBroadcastEnabled(context, it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun LyricSettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color = ConceptPrimary,
    textPrimary: Color = MaterialTheme.colorScheme.onSurface,
    textSecondary: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = textSecondary, fontSize = 11.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
        )
    }
}

/**
 * 现代重构版播放队列 (QueueBottomSheet)：
 * 1. 增加最近 3 个播放队列切换栏（支持一键切换与单独删除按钮）；
 * 2. 强化「播放模式」与「副歌速刷」双功能大卡片视觉标识；
 * 3. 优化「AI 漫游」分段胶囊显示；
 * 4. 歌曲列表去除圆角长方框背景，改为现代通透无框行；
 * 5. 移除「置顶」按钮，支持按住歌曲或右侧手柄自由上下拖动排序。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentIndex: Int,
    recentQueues: List<SavedPlayQueue> = emptyList(),
    activeQueueId: String = "",
    playMode: PlayMode = PlayMode.REPEAT_ALL,
    climaxModeEnabled: Boolean = false,
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
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeMoodMode by remember { mutableStateOf("") }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedPx by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { 58.dp.toPx() }

    val sheetBg = conceptSheetContainerColor()
    val cardBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(currentIndex) {
        if (currentIndex in queue.indices && draggingIndex == null) {
            listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        windowInsets = WindowInsets(0, 0, 0, 0),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            // 1. 顶部标题栏 + 清空队列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "播放队列",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = textPrimary,
                            fontSize = 19.sp
                        )
                    )
                    if (queue.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ConceptPrimary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${(currentIndex + 1).coerceAtMost(queue.size)}/${queue.size}",
                                color = ConceptPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }

                if (queue.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFF6B6B).copy(alpha = 0.12f),
                        modifier = Modifier.softClickable(onClick = onClear)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "清空",
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("清空", color = Color(0xFFFF6B6B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. 最近三个播放队列切换栏（带删除按钮）
            if (recentQueues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentQueues.take(3).forEachIndexed { idx, savedQueue ->
                        val isCurrentQueue = savedQueue.id == activeQueueId || (activeQueueId.isBlank() && idx == 0)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrentQueue) ConceptPrimary.copy(alpha = 0.18f) else cardBg,
                            modifier = Modifier.softCardClickable(shape = RoundedCornerShape(12.dp)) {
                                if (!isCurrentQueue) {
                                    onSwitchRecentQueue(savedQueue.id)
                                }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isCurrentQueue) ConceptPrimary else textSecondary.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = savedQueue.title,
                                    color = if (isCurrentQueue) ConceptPrimary else textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isCurrentQueue) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .softClickable { onDeleteRecentQueue(savedQueue.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "删除此队列",
                                        tint = if (isCurrentQueue) ConceptPrimary else textSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. 强化标识的双功能主卡片：「播放模式」与「副歌速刷」
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val (modeIcon, modeName, modeSub) = when (playMode) {
                    PlayMode.REPEAT_ALL -> Triple(Icons.Rounded.Repeat, "列表循环", "按序循环整列")
                    PlayMode.REPEAT_ONE -> Triple(Icons.Rounded.RepeatOne, "单曲循环", "专注循环当前")
                    PlayMode.SHUFFLE -> Triple(Icons.Rounded.Shuffle, "随机播放", "打破顺序漫游")
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ConceptPrimary.copy(alpha = 0.14f),
                    modifier = Modifier
                        .weight(1f)
                        .softCardClickable(shape = RoundedCornerShape(16.dp), onClick = onCyclePlayMode)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ConceptPrimary.copy(alpha = 0.22f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = modeName,
                                    tint = ConceptPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = modeName,
                                color = ConceptPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = modeSub,
                                color = textSecondary,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (climaxModeEnabled) VipGold.copy(alpha = 0.20f) else cardBg,
                    modifier = Modifier
                        .weight(1f)
                        .softCardClickable(shape = RoundedCornerShape(16.dp)) {
                            onToggleClimaxMode()
                            Toast.makeText(
                                context,
                                if (!climaxModeEnabled) "已开启副歌速刷" else "已关闭副歌速刷",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (climaxModeEnabled) VipGold.copy(alpha = 0.26f) else textPrimary.copy(alpha = 0.08f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Whatshot,
                                    contentDescription = "副歌速刷",
                                    tint = if (climaxModeEnabled) VipGold else textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (climaxModeEnabled) "副歌速刷 · 已开" else "副歌速刷",
                                color = if (climaxModeEnabled) VipGold else textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "30秒高潮精华连播",
                                color = textSecondary,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
            }

            // 4. 现代简洁版 AI 漫游条
            if (queue.size > 2) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardBg.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = ConceptPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "AI 漫游",
                            color = textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        listOf(
                            "sleep" to "睡前渐缓",
                            "commute" to "通勤渐燃",
                            "harmonic" to "声学漫游"
                        ).forEach { (modeKey, label) ->
                            val isSelected = activeMoodMode == modeKey
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (isSelected) ConceptPrimary else ConceptPrimary.copy(alpha = 0.10f),
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable(shape = RoundedCornerShape(9.dp)) {
                                        activeMoodMode = modeKey
                                        val msg = onReorderQueueByMood(modeKey)
                                        if (msg.isNotBlank()) {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else ConceptPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 5. 无边框现代队列列表（支持长按或拖动手柄自由拖动排序，移除置顶按钮）
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
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
                        Text(
                            text = "播放队列为空",
                            color = textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 430.dp)
                ) {
                    itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                        val isPlayingThis = index == currentIndex
                        val isDraggingThis = draggingIndex == index
                        val currentIdxState by rememberUpdatedState(index)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationY = if (isDraggingThis) dragAccumulatedPx else 0f
                                    scaleX = if (isDraggingThis) 1.02f else 1f
                                    scaleY = if (isDraggingThis) 1.02f else 1f
                                    alpha = if (isDraggingThis) 0.94f else 1f
                                }
                                .background(
                                    if (isDraggingThis) cardBg
                                    else if (isPlayingThis) ConceptPrimary.copy(alpha = 0.08f)
                                    else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .pointerInput(queue.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = currentIdxState
                                            dragAccumulatedPx = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val activeIdx = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                            dragAccumulatedPx += dragAmount.y
                                            val indexShift = (dragAccumulatedPx / rowHeightPx).toInt()
                                            if (indexShift != 0) {
                                                val targetIdx = (activeIdx + indexShift).coerceIn(0, queue.lastIndex)
                                                if (targetIdx != activeIdx) {
                                                    onMoveQueueItem(activeIdx, targetIdx)
                                                    draggingIndex = targetIdx
                                                    dragAccumulatedPx -= indexShift * rowHeightPx
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggingIndex = null
                                            dragAccumulatedPx = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            dragAccumulatedPx = 0f
                                        }
                                    )
                                }
                                .clickable { onSongClick(item) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (isPlayingThis) {
                                        PlayingAudioWaveIndicator()
                                    } else {
                                        Text(
                                            text = (index + 1).toString().padStart(2, '0'),
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
                                        .background(ConceptPrimary.copy(alpha = 0.10f)),
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
                                            tint = ConceptPrimary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = if (isPlayingThis) ConceptPrimary else textPrimary,
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
                                            BadgeTag(text = "SQ", color = ConceptPrimary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = item.artist,
                                            color = if (isPlayingThis) ConceptPrimary.copy(alpha = 0.82f) else textSecondary,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // 拖拽排序手柄（支持直接按住上下拖动调整顺序）
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .pointerInput(queue.size) {
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    draggingIndex = currentIdxState
                                                    dragAccumulatedPx = 0f
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val activeIdx = draggingIndex ?: return@detectVerticalDragGestures
                                                    dragAccumulatedPx += dragAmount
                                                    val indexShift = (dragAccumulatedPx / rowHeightPx).toInt()
                                                    if (indexShift != 0) {
                                                        val targetIdx = (activeIdx + indexShift).coerceIn(0, queue.lastIndex)
                                                        if (targetIdx != activeIdx) {
                                                            onMoveQueueItem(activeIdx, targetIdx)
                                                            draggingIndex = targetIdx
                                                            dragAccumulatedPx -= indexShift * rowHeightPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingIndex = null
                                                    dragAccumulatedPx = 0f
                                                },
                                                onDragCancel = {
                                                    draggingIndex = null
                                                    dragAccumulatedPx = 0f
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = "按住拖动排序",
                                        tint = if (isDraggingThis) ConceptPrimary else textSecondary.copy(alpha = 0.45f),
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

                            HorizontalDivider(
                                color = textPrimary.copy(alpha = 0.05f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 80.dp, end = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * 封面播放页「更多功能控制台」重构：
 * - 彻底取消所有选中边框线 (`.border(...)`)，消除边框与圆角不匹配的问题；
 * - 改用柔和饱满的色块填充 + 发光圆点指示选中状态；
 * - 精简所有副标题文案，彻底杜绝小字截断与生硬表达。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerMoreControlSheet(
    song: Song,
    coverStyle: CoverStyle,
    playerBackgroundStyle: PlayerBackgroundStyle,
    dynamicCoverAccentEnabled: Boolean,
    currentQualityLabel: String,
    climaxModeEnabled: Boolean,
    accompanyModeEnabled: Boolean,
    playbackSpeed: Float,
    sleepTimerRemainingSec: Long,
    sleepStopAfterCurrentSong: Boolean,
    sleepPendingStopAtTrackEnd: Boolean,
    accentColor: Color = ConceptPrimary,
    onSelectCoverStyle: (CoverStyle) -> Unit,
    onSelectPlayerBackgroundStyle: (PlayerBackgroundStyle) -> Unit,
    onSetDynamicCoverAccentEnabled: (Boolean) -> Unit,
    onToggleClimaxMode: () -> Unit,
    onToggleAccompanyMode: () -> Unit,
    onOpenSpeedPitch: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQualitySelect: () -> Unit,
    onOpenSongDetail: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBg = conceptSheetContainerColor()
    val tileBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = textPrimary,
        windowInsets = WindowInsets(0, 0, 0, 0),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides textPrimary) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .conceptSheetSpringEntrance()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 顶部当前曲目名片
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = song.coverUrl,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = accentColor)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${song.artist} · ${song.album.ifBlank { "单曲" }}",
                            color = textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 2. 双封面样式选择 + 封面动态取色开关（无边框色块选中设计）
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "封面样式与智能取色",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CoverStyle.entries.forEach { style ->
                            val selected = coverStyle == style
                            val icon = when (style) {
                                CoverStyle.VINYL -> Icons.Rounded.Album
                                CoverStyle.SQUARE -> Icons.Rounded.CropSquare
                            }
                            val desc = when (style) {
                                CoverStyle.VINYL -> "拟真唱盘旋转"
                                CoverStyle.SQUARE -> "静态圆角方图"
                            }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) accentColor.copy(alpha = 0.20f) else tileBg,
                                contentColor = textPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable(shape = RoundedCornerShape(18.dp)) {
                                        onSelectCoverStyle(style)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (selected) accentColor.copy(alpha = 0.24f) else textPrimary.copy(alpha = 0.06f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = style.label,
                                                tint = if (selected) accentColor else textSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = style.label,
                                            color = if (selected) accentColor else textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = desc,
                                            color = if (selected) accentColor.copy(alpha = 0.8f) else textSecondary,
                                            fontSize = 10.5.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 封面智能动态取色开关
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = tileBg,
                        contentColor = textPrimary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "封面智能动态取色",
                                    color = textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "提取封面主色渲染播放键与进度条",
                                    color = textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = dynamicCoverAccentEnabled,
                                onCheckedChange = onSetDynamicCoverAccentEnabled,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                            )
                        }
                    }
                }

                // 3. 播放页背景显示效果（无边框现代卡片，文案精简不截断）
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "播放页背景效果",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerBackgroundStyle.entries.forEach { bgStyle ->
                            val selected = playerBackgroundStyle == bgStyle
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) accentColor.copy(alpha = 0.20f) else tileBg,
                                contentColor = textPrimary,
                                modifier = Modifier
                                    .width(114.dp)
                                    .softCardClickable(shape = RoundedCornerShape(16.dp)) {
                                        onSelectPlayerBackgroundStyle(bgStyle)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = bgStyle.label,
                                            color = if (selected) accentColor else textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1
                                        )
                                        if (selected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(accentColor)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = bgStyle.subtitle,
                                        color = if (selected) accentColor.copy(alpha = 0.82f) else textSecondary,
                                        fontSize = 10.5.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. 播放工具
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "播放工具",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BentoControlTile(
                            icon = Icons.Rounded.Whatshot,
                            title = "副歌速刷",
                            subtitle = if (climaxModeEnabled) "高潮连播中" else "30秒精华",
                            active = climaxModeEnabled,
                            activeColor = VipGold,
                            onClick = onToggleClimaxMode,
                            modifier = Modifier.weight(1f)
                        )
                        BentoControlTile(
                            icon = Icons.Rounded.Mic,
                            title = "KTV 伴奏",
                            subtitle = if (accompanyModeEnabled) "伴奏模式中" else "切换纯伴奏",
                            active = accompanyModeEnabled,
                            activeColor = accentColor,
                            onClick = onToggleAccompanyMode,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BentoControlTile(
                            icon = Icons.Rounded.Speed,
                            title = "倍速与变调",
                            subtitle = "${String.format(Locale.getDefault(), "%.2f", playbackSpeed)}x 播放",
                            active = playbackSpeed != 1.0f,
                            activeColor = accentColor,
                            onClick = onOpenSpeedPitch,
                            modifier = Modifier.weight(1f)
                        )
                        val sleepDesc = when {
                            sleepTimerRemainingSec > 0L -> formatDialogDuration(sleepTimerRemainingSec * 1000L)
                            sleepPendingStopAtTrackEnd -> "播完本首停"
                            sleepStopAfterCurrentSong -> "播完当前停"
                            else -> "定时关闭"
                        }
                        BentoControlTile(
                            icon = Icons.Rounded.Bedtime,
                            title = "睡眠定时",
                            subtitle = sleepDesc,
                            active = sleepTimerRemainingSec > 0L || sleepPendingStopAtTrackEnd,
                            activeColor = accentColor,
                            onClick = onOpenSleepTimer,
                            modifier = Modifier.weight(1f)
                        )
                        BentoControlTile(
                            icon = Icons.Rounded.HighQuality,
                            title = "音质规格",
                            subtitle = currentQualityLabel,
                            active = true,
                            activeColor = accentColor,
                            onClick = onOpenQualitySelect,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 5. 底部音频参数入口
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = tileBg,
                    contentColor = textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .softCardClickable(shape = RoundedCornerShape(16.dp), onClick = onOpenSongDetail)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("歌曲详情与音频参数", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun BentoControlTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    activeColor: Color = ConceptPrimary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) activeColor.copy(alpha = 0.18f) else tileBg,
        contentColor = textPrimary,
        modifier = modifier.softCardClickable(shape = RoundedCornerShape(18.dp), onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (active) activeColor else textPrimary.copy(alpha = 0.82f),
                modifier = Modifier.size(21.dp)
            )
            Text(
                text = title,
                color = if (active) activeColor else textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = if (active) activeColor.copy(alpha = 0.85f) else textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun QualitySelectDialog(
    availableQualities: List<AudioQuality>,
    currentQualityLabel: String,
    onDismiss: () -> Unit,
    onSelectQuality: (AudioQuality) -> Unit
) {
    val context = LocalContext.current
    val validQualities = availableQualities.ifEmpty { listOf(AudioQuality.STANDARD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择音质档位", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                validQualities.forEach { quality ->
                    val desc = when (quality) {
                        AudioQuality.HI_RES -> "Hi-Res (24bit 高解析度无损)"
                        AudioQuality.LOSSLESS -> "SQ 无损 (16bit FLAC)"
                        AudioQuality.HIGH -> "HQ 高品 (320kbps)"
                        AudioQuality.STANDARD -> "标准 (128kbps)"
                    }
                    val isSelected = currentQualityLabel.contains(quality.label.take(2))
                    val onSelect = {
                        onSelectQuality(quality)
                        Toast.makeText(context, "已切换音质：${quality.label}", Toast.LENGTH_SHORT).show()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .softClickable { onSelect() }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = quality.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) ConceptPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SleepTimerDialog(
    remainingSec: Long,
    stopAfterCurrentSong: Boolean,
    pendingStopAtTrackEnd: Boolean,
    onToggleStopAfterSongPref: (Boolean) -> Unit,
    onSetTimer: (minutes: Int, stopAfterCurrentSong: Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var stopAfterSong by remember(stopAfterCurrentSong) { mutableStateOf(stopAfterCurrentSong) }
    val presets = listOf(15, 30, 45, 60, 90)
    val isTimerRunning = remainingSec > 0L || pendingStopAtTrackEnd

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = ConceptPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("睡眠定时", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isTimerRunning) {
                    Surface(
                        color = ConceptPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val statusText = if (remainingSec > 0L) {
                            "剩余 ${formatDialogDuration(remainingSec * 1000L)}" +
                                if (stopAfterSong) "（播完当前曲目后停止）" else ""
                        } else {
                            "将在当前曲目播完后停止"
                        }
                        Text(
                            text = statusText,
                            color = ConceptPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presets.forEach { mins ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                onSetTimer(mins, stopAfterSong)
                                Toast.makeText(
                                    context,
                                    "已设置 $mins 分钟后停止",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = { Text("${mins}分钟") }
                        )
                    }
                    FilterChip(
                        selected = pendingStopAtTrackEnd && remainingSec == 0L,
                        onClick = {
                            onSetTimer(0, true)
                            Toast.makeText(context, "将在当前歌曲播完后停止", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text("播完本首") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("播完当前歌曲再停", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            text = "到点后等待当前歌曲自然结束",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = stopAfterSong,
                        onCheckedChange = {
                            stopAfterSong = it
                            onToggleStopAfterSongPref(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                    )
                }
            }
        },
        dismissButton = {
            if (isTimerRunning) {
                TextButton(
                    onClick = {
                        onCancelTimer()
                        Toast.makeText(context, "已关闭睡眠定时", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("关闭定时", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpeedPitchDialog(
    speed: Float,
    pitch: Float,
    linkPitchToSpeed: Boolean,
    onSetSpeed: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onSetLinkPitch: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val speedPresets = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, contentDescription = null, tint = ConceptPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("倍速与变调", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("播放倍速", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${String.format(Locale.getDefault(), "%.2f", speed)}x",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ConceptPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = speed,
                        onValueChange = onSetSpeed,
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        speedPresets.forEach { preset ->
                            FilterChip(
                                selected = abs(speed - preset) < 0.03f,
                                onClick = { onSetSpeed(preset) },
                                label = { Text("${preset}x", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("音频音调", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${String.format(Locale.getDefault(), "%.2f", pitch)}x",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ConceptPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = pitch,
                        onValueChange = onSetPitch,
                        enabled = !linkPitchToSpeed,
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        colors = SliderDefaults.colors(thumbColor = ConceptPrimary, activeTrackColor = ConceptPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("变速联动变调", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "音调随倍速同步升降，还原黑胶变速听感",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = linkPitchToSpeed,
                        onCheckedChange = onSetLinkPitch,
                        colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("恢复默认")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

private fun formatDialogDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
