package com.concept.music.ui.components

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 紧凑型重构版「歌词显示设置 (LyricSettingsDialog)」：
 * 1. 告别冗长占满全屏的卡片堆叠，采用单行内联滑块 (Inline Label + Slider + Value) 与 2x2 矩阵式开关卡片；
 * 2. 默认主歌词字号提升至 24sp（范围 18sp..34sp）；
 * 3. 完美适配竖屏与横屏播放模式（横屏下自动切换为宽屏双栏紧凑布局）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricSettingsDialog(
    fontSize: Float,
    translationScale: Float = 0.84f,
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val systemLyricState by SystemLyricBridge.state.collectAsState()
    val statusBarLyricEnabled = systemLyricState.statusBarBroadcastEnabled
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBg = conceptSheetContainerColor()
    val cardBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val themeAccent = dynamicAccentColor

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = textPrimary,
        windowInsets = WindowInsets(0, 0, 0, 0),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(38.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = textSecondary.copy(alpha = 0.28f)
            ) {}
        }
    ) {
        CompositionLocalProvider(LocalContentColor provides textPrimary) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isLandscape) 22.dp else 18.dp, vertical = 4.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 顶部紧凑标题栏 + 恢复默认
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = themeAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "歌词显示设置",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary,
                                fontSize = 16.5.sp
                            )
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = themeAccent.copy(alpha = 0.14f),
                        contentColor = themeAccent,
                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(10.dp)) {
                            onSetFontSize(24f)
                            onSetTranslationScale(0.84f)
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = themeAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("恢复默认", color = themeAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isLandscape) {
                    // 横屏模式：左右双栏并排紧凑布局，一屏尽览无需滚动
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1.05f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LyricThemeAndAlignCompactCard(
                                highlightTheme = highlightTheme,
                                alignment = alignment,
                                offsetMs = offsetMs,
                                dynamicAccentColor = themeAccent,
                                cardBg = cardBg,
                                sheetBg = sheetBg,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onSetHighlightTheme = onSetHighlightTheme,
                                onSetAlignment = onSetAlignment,
                                onSetOffsetMs = onSetOffsetMs
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LyricSlidersCompactCard(
                                fontSize = fontSize,
                                translationScale = translationScale,
                                lineSpacing = lineSpacing,
                                backgroundDim = backgroundDim,
                                blurEnabled = blurEnabled,
                                blurIntensity = blurIntensity,
                                themeAccent = themeAccent,
                                cardBg = cardBg,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onSetFontSize = onSetFontSize,
                                onSetTranslationScale = onSetTranslationScale,
                                onSetLineSpacing = onSetLineSpacing,
                                onSetBackgroundDim = onSetBackgroundDim,
                                onSetBlurIntensity = onSetBlurIntensity
                            )
                            LyricTogglesGridCard(
                                wordByWord = wordByWord,
                                blurEnabled = blurEnabled,
                                keepScreenOn = keepScreenOn,
                                statusBarLyricEnabled = statusBarLyricEnabled,
                                themeAccent = themeAccent,
                                cardBg = cardBg,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onSetWordByWord = onSetWordByWord,
                                onSetBlurEnabled = onSetBlurEnabled,
                                onSetKeepScreenOn = onSetKeepScreenOn,
                                onSetStatusBarLyric = { SystemLyricBridge.setStatusBarBroadcastEnabled(context, it) }
                            )
                        }
                    }
                } else {
                    // 竖屏模式：三组高密度紧凑面板，告别占满全屏
                    LyricThemeAndAlignCompactCard(
                        highlightTheme = highlightTheme,
                        alignment = alignment,
                        offsetMs = offsetMs,
                        dynamicAccentColor = themeAccent,
                        cardBg = cardBg,
                        sheetBg = sheetBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onSetHighlightTheme = onSetHighlightTheme,
                        onSetAlignment = onSetAlignment,
                        onSetOffsetMs = onSetOffsetMs
                    )

                    LyricSlidersCompactCard(
                        fontSize = fontSize,
                        translationScale = translationScale,
                        lineSpacing = lineSpacing,
                        backgroundDim = backgroundDim,
                        blurEnabled = blurEnabled,
                        blurIntensity = blurIntensity,
                        themeAccent = themeAccent,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onSetFontSize = onSetFontSize,
                        onSetTranslationScale = onSetTranslationScale,
                        onSetLineSpacing = onSetLineSpacing,
                        onSetBackgroundDim = onSetBackgroundDim,
                        onSetBlurIntensity = onSetBlurIntensity
                    )

                    LyricTogglesGridCard(
                        wordByWord = wordByWord,
                        blurEnabled = blurEnabled,
                        keepScreenOn = keepScreenOn,
                        statusBarLyricEnabled = statusBarLyricEnabled,
                        themeAccent = themeAccent,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onSetWordByWord = onSetWordByWord,
                        onSetBlurEnabled = onSetBlurEnabled,
                        onSetKeepScreenOn = onSetKeepScreenOn,
                        onSetStatusBarLyric = { SystemLyricBridge.setStatusBarBroadcastEnabled(context, it) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun LyricThemeAndAlignCompactCard(
    highlightTheme: LyricHighlightTheme,
    alignment: LyricAlignment,
    offsetMs: Long,
    dynamicAccentColor: Color,
    cardBg: Color,
    sheetBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onSetHighlightTheme: (LyricHighlightTheme) -> Unit,
    onSetAlignment: (LyricAlignment) -> Unit,
    onSetOffsetMs: (Long) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        contentColor = textPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 配色横向胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LyricHighlightTheme.entries.forEach { theme ->
                    val selected = highlightTheme == theme
                    val swatchColor = if (theme == LyricHighlightTheme.DYNAMIC_COVER) {
                        dynamicAccentColor
                    } else {
                        Color(theme.primaryHex)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) swatchColor.copy(alpha = 0.22f) else sheetBg,
                        contentColor = textPrimary,
                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(10.dp)) {
                            onSetHighlightTheme(theme)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = theme.label,
                                color = if (selected) swatchColor else textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 对齐方式 + 时间轴微调合并紧凑栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LyricAlignment.entries.forEach { item ->
                        val selected = alignment == item
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) dynamicAccentColor.copy(alpha = 0.20f) else sheetBg,
                            modifier = Modifier.softCardClickable(shape = RoundedCornerShape(8.dp)) {
                                onSetAlignment(item)
                            }
                        ) {
                            Text(
                                text = item.label,
                                color = if (selected) dynamicAccentColor else textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // 紧凑型时间轴微调器
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = sheetBg,
                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(8.dp)) {
                            onSetOffsetMs(offsetMs - 200L)
                        }
                    ) {
                        Text(
                            text = "-0.2s",
                            color = textPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (offsetMs != 0L) VipGold.copy(alpha = 0.18f) else dynamicAccentColor.copy(alpha = 0.14f),
                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(8.dp)) {
                            onSetOffsetMs(0L)
                        }
                    ) {
                        Text(
                            text = if (offsetMs == 0L) "校准 0.0s" else String.format(Locale.getDefault(), "%+.1fs", offsetMs / 1000f),
                            color = if (offsetMs != 0L) VipGold else dynamicAccentColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = sheetBg,
                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(8.dp)) {
                            onSetOffsetMs(offsetMs + 200L)
                        }
                    ) {
                        Text(
                            text = "+0.2s",
                            color = textPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricSlidersCompactCard(
    fontSize: Float,
    translationScale: Float,
    lineSpacing: Float,
    backgroundDim: Float,
    blurEnabled: Boolean,
    blurIntensity: Float,
    themeAccent: Color,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onSetFontSize: (Float) -> Unit,
    onSetTranslationScale: (Float) -> Unit,
    onSetLineSpacing: (Float) -> Unit,
    onSetBackgroundDim: (Float) -> Unit,
    onSetBlurIntensity: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        contentColor = textPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            CompactInlineSliderRow(
                label = "主词字号",
                valueText = "${fontSize.toInt()}sp",
                value = fontSize,
                valueRange = 18f..34f,
                steps = 7,
                accentColor = themeAccent,
                textPrimary = textPrimary,
                onValueChange = onSetFontSize
            )
            CompactInlineSliderRow(
                label = "译文比例",
                valueText = "${(translationScale * 100).roundToInt()}%",
                value = translationScale,
                valueRange = 0.65f..0.95f,
                steps = 0,
                accentColor = themeAccent,
                textPrimary = textPrimary,
                onValueChange = onSetTranslationScale
            )
            CompactInlineSliderRow(
                label = "行间距离",
                valueText = "${lineSpacing.toInt()}dp",
                value = lineSpacing,
                valueRange = 8f..24f,
                steps = 7,
                accentColor = themeAccent,
                textPrimary = textPrimary,
                onValueChange = onSetLineSpacing
            )
            CompactInlineSliderRow(
                label = "背景压暗",
                valueText = "${(backgroundDim * 100).toInt()}%",
                value = backgroundDim,
                valueRange = 0.40f..0.92f,
                steps = 0,
                accentColor = themeAccent,
                textPrimary = textPrimary,
                onValueChange = onSetBackgroundDim
            )
            if (blurEnabled) {
                CompactInlineSliderRow(
                    label = "景深强度",
                    valueText = String.format(Locale.getDefault(), "%.1fx", blurIntensity),
                    value = blurIntensity,
                    valueRange = 0.4f..1.8f,
                    steps = 0,
                    accentColor = themeAccent,
                    textPrimary = textPrimary,
                    onValueChange = onSetBlurIntensity
                )
            }
        }
    }
}

@Composable
private fun CompactInlineSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    accentColor: Color,
    textPrimary: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(58.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Text(
            text = valueText,
            color = accentColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(42.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun LyricTogglesGridCard(
    wordByWord: Boolean,
    blurEnabled: Boolean,
    keepScreenOn: Boolean,
    statusBarLyricEnabled: Boolean,
    themeAccent: Color,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onSetWordByWord: (Boolean) -> Unit,
    onSetBlurEnabled: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetStatusBarLyric: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactTogglePillTile(
                icon = Icons.Rounded.AutoAwesome,
                title = "KRC 逐字流光",
                subtitle = if (wordByWord) "已开启扫光" else "整行高亮",
                checked = wordByWord,
                accentColor = themeAccent,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onToggle = { onSetWordByWord(!wordByWord) },
                modifier = Modifier.weight(1f)
            )
            CompactTogglePillTile(
                icon = Icons.Rounded.BlurOn,
                title = "阶梯景深虚化",
                subtitle = if (blurEnabled) "远端自然虚化" else "已关闭虚化",
                checked = blurEnabled,
                accentColor = themeAccent,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onToggle = { onSetBlurEnabled(!blurEnabled) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactTogglePillTile(
                icon = Icons.Rounded.LightMode,
                title = "歌词屏幕常亮",
                subtitle = if (keepScreenOn) "播放不息屏" else "跟随系统",
                checked = keepScreenOn,
                accentColor = themeAccent,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onToggle = { onSetKeepScreenOn(!keepScreenOn) },
                modifier = Modifier.weight(1f)
            )
            CompactTogglePillTile(
                icon = Icons.Rounded.NotificationsActive,
                title = "状态栏歌词",
                subtitle = if (statusBarLyricEnabled) "广播已开启" else "点击开启",
                checked = statusBarLyricEnabled,
                accentColor = themeAccent,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onToggle = { onSetStatusBarLyric(!statusBarLyricEnabled) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompactTogglePillTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (checked) accentColor.copy(alpha = 0.18f) else cardBg,
        modifier = modifier.softCardClickable(shape = RoundedCornerShape(14.dp), onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (checked) accentColor.copy(alpha = 0.24f) else textPrimary.copy(alpha = 0.07f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (checked) accentColor else textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (checked) accentColor else textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = if (checked) accentColor.copy(alpha = 0.8f) else textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (checked) accentColor else textSecondary.copy(alpha = 0.25f))
            )
        }
    }
}

/**
 * 深度重构版播放队列 (QueueBottomSheet)：
 * 1. 应用播放页的智能封面取色效果（accentColor & secondaryAccentColor 弥散渲染）；
 * 2. 播放顺序模式按钮改为精致小按钮，与封面播放页 Canvas 矢量风格完全统一，置于右上角、清空按钮左侧；
 * 3. 右上角新增自研立体叠卡图标「最近」按钮，点击进入「3D 错位重叠卡片页面」，支持手势左右滑动挑选最多 5 个最近队列；
 * 4. 针对横屏与竖屏同时做自适应高度与宽度优化。
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeMoodMode by remember { mutableStateOf("") }
    var showRecentDeckPage by remember { mutableStateOf(false) }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedPx by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { 56.dp.toPx() }

    val baseSheetBg = conceptSheetContainerColor()
    val cardBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(currentIndex, showRecentDeckPage) {
        if (!showRecentDeckPage && currentIndex in queue.indices && draggingIndex == null) {
            listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = baseSheetBg,
        windowInsets = WindowInsets(0, 0, 0, 0),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.45f)
            ) {}
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.16f),
                            secondaryAccentColor.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isLandscape) 22.dp else 18.dp, vertical = 4.dp)
                    .navigationBarsPadding()
            ) {
                // 1. 顶部栏：标题 + 右上角 [最近队列叠卡按钮] + [统一播放模式小按钮] + [清空]
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
                                color = accentColor.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .size(30.dp)
                                    .softClickable { showRecentDeckPage = false }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "返回当前队列",
                                        tint = accentColor,
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
                                fontSize = 18.5.sp
                            )
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = if (showRecentDeckPage) {
                                    "${recentQueues.size.coerceAtMost(5)}/5"
                                } else if (queue.isNotEmpty()) {
                                    "${(currentIndex + 1).coerceAtMost(queue.size)}/${queue.size}"
                                } else "0/0",
                                color = accentColor,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }

                    // 右上角按钮簇：最近按钮 -> 播放顺序模式小按钮（清空左边）-> 清空按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 自研图标：最近队列 3D 叠卡入口按钮
                        QueueRecentStackIconButton(
                            active = showRecentDeckPage,
                            count = recentQueues.size.coerceAtMost(5),
                            accentColor = accentColor,
                            textPrimary = textPrimary,
                            cardBg = cardBg,
                            onClick = { showRecentDeckPage = !showRecentDeckPage }
                        )

                        // 与封面播放页完全统一的矢量播放顺序模式小按钮（位于清空按钮左侧）
                        QueueUnifiedPlayModeSmallButton(
                            playMode = playMode,
                            accentColor = accentColor,
                            cardBg = cardBg,
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
                                color = Color(0xFFFF6B6B).copy(alpha = 0.13f),
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
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = showRecentDeckPage,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.95f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.95f, animationSpec = tween(180)))
                    },
                    label = "QueueDeckViewSwitch"
                ) { inRecentDeck ->
                    if (inRecentDeck) {
                        // 3D 错位重叠卡片挑选页面（上限 5 个最近队列）
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
                            // 副歌速刷 + AI 漫游智能工具条（融合播放页取色）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (climaxModeEnabled) VipGold.copy(alpha = 0.22f) else cardBg,
                                    modifier = Modifier.softCardClickable(shape = RoundedCornerShape(12.dp)) {
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
                                            tint = if (climaxModeEnabled) VipGold else accentColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (climaxModeEnabled) "副歌速刷·开" else "副歌速刷",
                                            color = if (climaxModeEnabled) VipGold else textPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                if (queue.size > 2) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cardBg.copy(alpha = 0.8f),
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
                                                tint = accentColor,
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
                                                    color = if (isSelected) accentColor else accentColor.copy(alpha = 0.12f),
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
                                                            color = if (isSelected) Color.White else accentColor,
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
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.heightIn(max = if (isLandscape) 250.dp else 420.dp)
                                ) {
                                    itemsIndexed(
                                        items = queue,
                                        key = { _, item -> item.id },
                                        contentType = { _, _ -> "queue_song_row" }
                                    ) { index, item ->
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
                                                    else if (isPlayingThis) accentColor.copy(alpha = 0.14f)
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
                                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier.width(28.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (isPlayingThis) {
                                                        PlayingAudioWaveIndicator(color = accentColor)
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
                                                        .background(accentColor.copy(alpha = 0.12f)),
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
                                                            tint = accentColor.copy(alpha = 0.6f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.title,
                                                        color = if (isPlayingThis) accentColor else textPrimary,
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
                                                            BadgeTag(text = "SQ", color = accentColor)
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                        }
                                                        Text(
                                                            text = item.artist,
                                                            color = if (isPlayingThis) accentColor.copy(alpha = 0.85f) else textSecondary,
                                                            fontSize = 11.5.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

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
                                                        tint = if (isDraggingThis) accentColor else textSecondary.copy(alpha = 0.45f),
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
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
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
    textPrimary: Color,
    cardBg: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) accentColor else accentColor.copy(alpha = 0.14f),
        modifier = Modifier
            .height(32.dp)
            .softCardClickable(shape = RoundedCornerShape(10.dp), onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp)
        ) {
            val iconColor = if (active) Color.White else accentColor
            Canvas(modifier = Modifier.size(16.dp)) {
                val strokeW = 1.4.dp.toPx()
                // 后层右偏卡片
                drawRoundRect(
                    color = iconColor.copy(alpha = 0.45f),
                    topLeft = Offset(size.width * 0.28f, size.height * 0.08f),
                    size = Size(size.width * 0.64f, size.height * 0.68f),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                    style = Stroke(width = strokeW)
                )
                // 中层卡片
                drawRoundRect(
                    color = iconColor.copy(alpha = 0.72f),
                    topLeft = Offset(size.width * 0.16f, size.height * 0.18f),
                    size = Size(size.width * 0.64f, size.height * 0.68f),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                    style = Stroke(width = strokeW)
                )
                // 前层主卡片
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
                color = if (active) Color.White else accentColor,
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
    cardBg: Color,
    onClick: () -> Unit
) {
    val label = when (playMode) {
        PlayMode.REPEAT_ALL -> "列表"
        PlayMode.REPEAT_ONE -> "单曲"
        PlayMode.SHUFFLE -> "随机"
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accentColor.copy(alpha = 0.14f),
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
                            color = accentColor,
                            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        if (playMode == PlayMode.REPEAT_ONE) {
                            drawLine(
                                color = accentColor,
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
                            color = accentColor,
                            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = accentColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * 3D 错位重叠卡片队列挑选页面 (RecentQueuesStaggeredDeckPage)：
 * 支持最多 5 个最近播放队列，采用透视景深、Y/Z 轴倾角、错位叠放与左右手势滑动切换，立体感十足。
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

    if (recentQueues.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
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

    val activeFocusInt = animatedFocusIndex.value.roundToInt().coerceIn(0, recentQueues.lastIndex)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "左右滑动错位卡片挑选历史队列（上限 5 个）",
            color = textSecondary,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 3D 错位重叠卡片舞台
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 235.dp else 320.dp)
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
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            val target = animatedFocusIndex.value.roundToInt()
                                .coerceIn(0, recentQueues.lastIndex)
                            scope.launch { animatedFocusIndex.animateTo(target.toFloat()) }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            recentQueues.forEachIndexed { idx, savedQueue ->
                val relativeOffset = idx - animatedFocusIndex.value
                val absOffset = abs(relativeOffset)
                val isFocused = idx == activeFocusInt
                val isCurrentPlayingQueue = savedQueue.id == activeQueueId || (activeQueueId.isBlank() && idx == 0)
                val coverSong = savedQueue.songs.getOrNull(savedQueue.currentIndex) ?: savedQueue.songs.firstOrNull()

                // 错位立体几何变换参数
                val cardScale = (1f - absOffset * 0.11f).coerceIn(0.72f, 1f)
                val cardAlpha = (1f - absOffset * 0.24f).coerceIn(0.35f, 1f)
                val transX = relativeOffset * 58f * density
                val transY = absOffset * 13f * density
                val rotY = (-relativeOffset * 12f).coerceIn(-28f, 28f)
                val rotZ = (relativeOffset * 2.4f).coerceIn(-7f, 7f)

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cardBg,
                    modifier = Modifier
                        .zIndex(10f - absOffset)
                        .width(if (isLandscape) 320.dp else 286.dp)
                        .fillMaxHeight(0.94f)
                        .graphicsLayer {
                            cameraDistance = 16f * density
                            translationX = transX
                            translationY = transY
                            scaleX = cardScale
                            scaleY = cardScale
                            rotationY = rotY
                            rotationZ = rotZ
                            alpha = cardAlpha
                        }
                        .shadow(
                            elevation = (20f - absOffset * 5f).coerceAtLeast(4f).dp,
                            shape = RoundedCornerShape(22.dp)
                        )
                        .border(
                            width = if (isFocused) 1.5.dp else 0.8.dp,
                            brush = Brush.linearGradient(
                                colors = if (isFocused) {
                                    listOf(accentColor, secondaryAccentColor.copy(alpha = 0.6f))
                                } else {
                                    listOf(textPrimary.copy(alpha = 0.14f), Color.Transparent)
                                }
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .softCardClickable(shape = RoundedCornerShape(22.dp)) {
                            if (!isFocused) {
                                scope.launch {
                                    animatedFocusIndex.animateTo(
                                        idx.toFloat(),
                                        spring(stiffness = Spring.StiffnessMediumLow)
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
                                        accentColor.copy(alpha = if (isFocused) 0.22f else 0.08f),
                                        cardBg
                                    )
                                )
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
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
                                    .background(accentColor.copy(alpha = 0.2f)),
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
                                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = accentColor)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = if (isCurrentPlayingQueue) accentColor else textPrimary.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = if (isCurrentPlayingQueue) "当前队列" else "队列 #${idx + 1}",
                                            color = if (isCurrentPlayingQueue) Color.White else textSecondary,
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

                        // 卡片中部：预览前 4 首曲目
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val maxPreview = if (isLandscape) 3 else 4
                            savedQueue.songs.take(maxPreview).forEachIndexed { sIdx, s ->
                                val isCurSong = sIdx == savedQueue.currentIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurSong) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = (sIdx + 1).toString().padStart(2, '0'),
                                        color = if (isCurSong) accentColor else textSecondary.copy(alpha = 0.6f),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(20.dp)
                                    )
                                    Text(
                                        text = s.title,
                                        color = if (isCurSong) accentColor else textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurSong) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = s.artist,
                                        color = textSecondary,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 72.dp)
                                    )
                                }
                            }
                        }

                        // 卡片底部：一键载入此队列按钮
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrentPlayingQueue) accentColor.copy(alpha = 0.18f) else accentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .softCardClickable(shape = RoundedCornerShape(12.dp)) {
                                    onSelectQueue(savedQueue.id)
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isCurrentPlayingQueue) "正在播放该队列 · 点击返回" else "切换并播放此队列",
                                    color = if (isCurrentPlayingQueue) accentColor else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
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
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(if (selected) 20.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) accentColor else textSecondary.copy(alpha = 0.3f))
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

/**
 * 封面播放页「更多功能控制台 (PlayerMoreControlSheet)」深度重构：
 * 1. 新增「歌手主页」与「专辑详情」双快捷直达入口胶囊卡片；
 * 2. 整合曲目名片与「音频参数详情」快捷按钮；
 * 3. 将封面样式、智能动态取色与 5 款播放页背景效果整合为统一高定视觉面板，布局紧凑优雅。
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
    onOpenArtist: () -> Unit = {},
    onOpenAlbum: () -> Unit = {},
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(38.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = textSecondary.copy(alpha = 0.28f)
            ) {}
        }
    ) {
        CompositionLocalProvider(LocalContentColor provides textPrimary) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .conceptSheetSpringEntrance()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 顶部一体化曲目名片 + 「歌手主页」「专辑详情」双快捷入口
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = tileBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(accentColor.copy(alpha = 0.14f)),
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
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.artist} · ${song.album.ifBlank { "单曲发行" }}",
                                    color = textSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // 右侧快捷查看歌曲技术参数胶囊
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = accentColor.copy(alpha = 0.14f),
                                modifier = Modifier.softCardClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = onOpenSongDetail
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = "音频参数",
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "音频详情",
                                        color = accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 歌手主页 & 专辑详情 双快捷入口卡片
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = sheetBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable(
                                        shape = RoundedCornerShape(14.dp),
                                        onClick = onOpenArtist
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = accentColor.copy(alpha = 0.18f),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.PersonSearch,
                                                contentDescription = "歌手主页",
                                                tint = accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "歌手主页",
                                            color = accentColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = song.artist.ifBlank { "未知歌手" },
                                            color = textPrimary,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = sheetBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable(
                                        shape = RoundedCornerShape(14.dp),
                                        onClick = onOpenAlbum
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = accentColor.copy(alpha = 0.18f),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Album,
                                                contentDescription = "专辑详情",
                                                tint = accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "所属专辑",
                                            color = accentColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = song.album.ifBlank { song.title },
                                            color = textPrimary,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. 播放控制与声学工具矩阵（置顶高频操作）
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "声学与播放工具",
                        color = textSecondary,
                        fontSize = 11.5.sp,
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
                    }
                }

                // 3. 整合式视觉舞台控制台（封面样式 + 智能取色 + 5款背景效果）
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = tileBg,
                    contentColor = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "封面样式与智能取色",
                                    color = textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            // 紧凑型智能取色开关胶囊
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (dynamicCoverAccentEnabled) accentColor.copy(alpha = 0.20f) else sheetBg,
                                modifier = Modifier.softCardClickable(shape = RoundedCornerShape(10.dp)) {
                                    onSetDynamicCoverAccentEnabled(!dynamicCoverAccentEnabled)
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (dynamicCoverAccentEnabled) accentColor else textSecondary)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (dynamicCoverAccentEnabled) "智能取色 · 已开启" else "智能取色 · 已关闭",
                                        color = if (dynamicCoverAccentEnabled) accentColor else textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 双封面样式切换
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) accentColor.copy(alpha = 0.20f) else sheetBg,
                                    modifier = Modifier
                                        .weight(1f)
                                        .softCardClickable(shape = RoundedCornerShape(14.dp)) {
                                            onSelectCoverStyle(style)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = style.label,
                                            tint = if (selected) accentColor else textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = style.label,
                                                color = if (selected) accentColor else textPrimary,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = desc,
                                                color = if (selected) accentColor.copy(alpha = 0.8f) else textSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = textPrimary.copy(alpha = 0.06f))

                        Text(
                            text = "播放页背景动效",
                            color = textSecondary,
                            fontSize = 11.5.sp,
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
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) accentColor.copy(alpha = 0.20f) else sheetBg,
                                    modifier = Modifier
                                        .width(106.dp)
                                        .softCardClickable(shape = RoundedCornerShape(14.dp)) {
                                            onSelectPlayerBackgroundStyle(bgStyle)
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = bgStyle.label,
                                                color = if (selected) accentColor else textPrimary,
                                                fontSize = 12.sp,
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
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = bgStyle.subtitle,
                                            color = if (selected) accentColor.copy(alpha = 0.82f) else textSecondary,
                                            fontSize = 9.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
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
        shape = RoundedCornerShape(16.dp),
        color = if (active) activeColor.copy(alpha = 0.18f) else tileBg,
        contentColor = textPrimary,
        modifier = modifier.softCardClickable(shape = RoundedCornerShape(16.dp), onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (active) activeColor else textPrimary.copy(alpha = 0.82f),
                modifier = Modifier.size(20.dp)
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
                fontSize = 10.sp,
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
