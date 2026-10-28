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
 * 现代玻璃拟态「歌词显示设置 (LyricSettingsDialog)」：
 * 1. 采用应用窗口内全屏级玻璃拟态浮层（告别 ModalBottomSheet 独立 DialogWindow 在横屏下右侧未渲染与黑框割裂问题）；
 * 2. 顶部手势横条与面板主体处于同一连续渐变容器内，无任何颜色分层；
 * 3. 横屏采用居中宽幅悬浮玻璃面板（双栏均衡排版，杜绝按钮换行与标题截断），竖屏采用底部一体式流光抽屉。
 */
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
    onSetWordByWord: (Boolean) -> Unit = {},
    onSetBlurEnabled: (Boolean) -> Unit,
    onSetBlurIntensity: (Float) -> Unit,
    onSetLineSpacing: (Float) -> Unit,
    onSetOffsetMs: (Long) -> Unit,
    onSetHighlightTheme: (LyricHighlightTheme) -> Unit,
    onSetBackgroundDim: (Float) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val baseSheetBg = conceptSheetContainerColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val themeAccent = dynamicAccentColor
    val roles = rememberCoverColorRoles(themeAccent)

    // 一体化连续渐变玻璃底色（从顶部边缘到面板底部平滑过渡，彻底消除顶部横线区域颜色分层）
    val panelTopColor = roles.primaryContainer.copy(alpha = 0.55f).compositeOver(baseSheetBg.copy(alpha = 0.96f))
    val panelMidColor = roles.primaryContainer.copy(alpha = 0.22f).compositeOver(baseSheetBg.copy(alpha = 0.97f))
    val panelBottomColor = baseSheetBg.copy(alpha = 0.98f)
    val glassCardBg = textPrimary.copy(alpha = 0.055f).compositeOver(baseSheetBg.copy(alpha = 0.90f))
    val glassPillBg = textPrimary.copy(alpha = 0.08f)
    val panelShape = if (isLandscape) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    }

    ConceptAnimatedOverlaySheet(
        onDismiss = onDismiss,
        isLandscape = isLandscape,
        dismissDragThresholdPx = 110f
    ) {
        // 现代玻璃拟态主窗口（入退场弹簧与遮罩渐隐由 ConceptAnimatedOverlaySheet 统一在 Draw 阶段驱动，无外框描边）
        Box(
            modifier = Modifier
                .then(
                    if (isLandscape) {
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                            .widthIn(max = 720.dp)
                            .fillMaxWidth(0.92f)
                            .heightIn(max = 420.dp)
                    } else {
                        Modifier.fillMaxWidth()
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
            CompositionLocalProvider(LocalContentColor provides textPrimary) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (isLandscape) 22.dp else 18.dp,
                            end = if (isLandscape) 22.dp else 18.dp,
                            top = if (isLandscape) 14.dp else 8.dp,
                            bottom = if (isLandscape) 16.dp else 12.dp
                        )
                        .then(if (!isLandscape) Modifier.navigationBarsPadding() else Modifier)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 顶部一体化手势指示横条（支持跟手平滑下滑关闭与松手弹性回弹）
                    if (!isLandscape) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 2.dp)
                                .sheetVerticalDragGesture(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(38.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(textPrimary.copy(alpha = 0.24f))
                            )
                        }
                    }

                    // 顶部现代标题栏：图标徽章 + 标题 + 恢复默认 + 关闭按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(roles.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = roles.onSecondaryContainer,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "歌词显示设置",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrimary,
                                        fontSize = 17.sp
                                    )
                                )
                                Text(
                                    text = "实时预览流光色彩、字号与排版景深",
                                    color = textSecondary.copy(alpha = 0.72f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = roles.secondaryContainer,
                                contentColor = roles.onSecondaryContainer,
                                modifier = Modifier.softCardClickable(shape = RoundedCornerShape(12.dp)) {
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
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.RestartAlt,
                                        contentDescription = null,
                                        tint = roles.onSecondaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "恢复默认",
                                        color = roles.onSecondaryContainer,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = glassPillBg,
                                modifier = Modifier
                                    .size(30.dp)
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

                    if (isLandscape) {
                        // 横屏模式：宽幅双栏现代玻璃布局，不拥挤、不折行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1.02f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LyricThemeAndAlignCompactCard(
                                    highlightTheme = highlightTheme,
                                    alignment = alignment,
                                    offsetMs = offsetMs,
                                    dynamicAccentColor = themeAccent,
                                    cardBg = glassCardBg,
                                    sheetBg = glassPillBg,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    onSetHighlightTheme = onSetHighlightTheme,
                                    onSetAlignment = onSetAlignment,
                                    onSetOffsetMs = onSetOffsetMs
                                )
                                LyricTogglesGridCard(
                                    blurEnabled = blurEnabled,
                                    keepScreenOn = keepScreenOn,
                                    themeAccent = themeAccent,
                                    cardBg = glassCardBg,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    onSetBlurEnabled = onSetBlurEnabled,
                                    onSetKeepScreenOn = onSetKeepScreenOn
                                )
                            }
                            Column(
                                modifier = Modifier.weight(0.98f),
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
                                    cardBg = glassCardBg,
                                    textPrimary = textPrimary,
                                    onSetFontSize = onSetFontSize,
                                    onSetTranslationScale = onSetTranslationScale,
                                    onSetLineSpacing = onSetLineSpacing,
                                    onSetBackgroundDim = onSetBackgroundDim,
                                    onSetBlurIntensity = onSetBlurIntensity
                                )
                            }
                        }
                    } else {
                        // 竖屏模式：现代三段式玻璃卡片面板
                        LyricThemeAndAlignCompactCard(
                            highlightTheme = highlightTheme,
                            alignment = alignment,
                            offsetMs = offsetMs,
                            dynamicAccentColor = themeAccent,
                            cardBg = glassCardBg,
                            sheetBg = glassPillBg,
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
                            cardBg = glassCardBg,
                            textPrimary = textPrimary,
                            onSetFontSize = onSetFontSize,
                            onSetTranslationScale = onSetTranslationScale,
                            onSetLineSpacing = onSetLineSpacing,
                            onSetBackgroundDim = onSetBackgroundDim,
                            onSetBlurIntensity = onSetBlurIntensity
                        )

                        LyricTogglesGridCard(
                            blurEnabled = blurEnabled,
                            keepScreenOn = keepScreenOn,
                            themeAccent = themeAccent,
                            cardBg = glassCardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onSetBlurEnabled = onSetBlurEnabled,
                            onSetKeepScreenOn = onSetKeepScreenOn
                        )
                    }
                }
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
    val roles = rememberCoverColorRoles(dynamicAccentColor)
    val goldRoles = rememberCoverColorRoles(VipGold)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        contentColor = textPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. 流光配色胶囊矩阵（无描边，色阶容器分离）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LyricHighlightTheme.entries.forEach { theme ->
                    val selected = highlightTheme == theme
                    val swatchColor = if (theme == LyricHighlightTheme.DYNAMIC_COVER) {
                        dynamicAccentColor
                    } else {
                        Color(theme.primaryHex)
                    }
                    val swatchRoles = rememberCoverColorRoles(swatchColor)
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = if (selected) swatchRoles.primaryContainer else sheetBg,
                        contentColor = textPrimary,
                        modifier = Modifier
                            .softCardClickable(shape = RoundedCornerShape(11.dp)) {
                                onSetHighlightTheme(theme)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) swatchRoles.primary else swatchColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = theme.label,
                                color = if (selected) swatchRoles.onPrimaryContainer else textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 2. 对齐方式分段控件 + 时间轴校准（单行不折行设计）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 对齐方式分段胶囊
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(sheetBg)
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    LyricAlignment.entries.forEach { item ->
                        val selected = alignment == item
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) roles.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .softCardClickable(shape = RoundedCornerShape(8.dp)) {
                                    onSetAlignment(item)
                                }
                        ) {
                            Text(
                                text = item.label,
                                color = if (selected) roles.onPrimaryContainer else textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // 紧凑型时间轴微调器（强制单行不换行，彻底解决 +0.2s 折行问题）
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End)
                ) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = sheetBg,
                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(9.dp)) {
                            onSetOffsetMs(offsetMs - 200L)
                        }
                    ) {
                        Text(
                            text = "-0.2s",
                            color = textPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (offsetMs != 0L) goldRoles.primaryContainer else roles.secondaryContainer,
                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(9.dp)) {
                            onSetOffsetMs(0L)
                        }
                    ) {
                        Text(
                            text = if (offsetMs == 0L) "校准 0.0s" else String.format(Locale.getDefault(), "%+.1fs", offsetMs / 1000f),
                            color = if (offsetMs != 0L) goldRoles.onPrimaryContainer else roles.onSecondaryContainer,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = sheetBg,
                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(9.dp)) {
                            onSetOffsetMs(offsetMs + 200L)
                        }
                    ) {
                        Text(
                            text = "+0.2s",
                            color = textPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.5.dp)
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
    onSetFontSize: (Float) -> Unit,
    onSetTranslationScale: (Float) -> Unit,
    onSetLineSpacing: (Float) -> Unit,
    onSetBackgroundDim: (Float) -> Unit,
    onSetBlurIntensity: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        contentColor = textPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
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
    val roles = rememberCoverColorRoles(accentColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(56.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = roles.primary,
                activeTrackColor = roles.primary.copy(alpha = 0.88f),
                inactiveTrackColor = textPrimary.copy(alpha = 0.16f)
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Box(
            modifier = Modifier
                .width(46.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(roles.secondaryContainer)
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = valueText,
                color = roles.onSecondaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun LyricTogglesGridCard(
    blurEnabled: Boolean,
    keepScreenOn: Boolean,
    themeAccent: Color,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onSetBlurEnabled: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
    val roles = rememberCoverColorRoles(accentColor)
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = if (checked) roles.primaryContainer else cardBg,
        modifier = modifier
            .softCardClickable(shape = RoundedCornerShape(15.dp), onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (checked) roles.primary.copy(alpha = 0.22f) else textPrimary.copy(alpha = 0.07f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (checked) roles.onPrimaryContainer else textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (checked) roles.onPrimaryContainer else textPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = if (checked) roles.onPrimaryContainerSubtle else textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (checked) roles.primary else textSecondary.copy(alpha = 0.25f))
            )
        }
    }
}

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
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                            .widthIn(max = 720.dp)
                            .fillMaxWidth(0.92f)
                            .heightIn(max = 420.dp)
                    } else {
                        Modifier.fillMaxWidth()
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
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.heightIn(max = if (isLandscape) 260.dp else 420.dp)
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
    onDownloadSong: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val dismissThen: (() -> Unit) -> Unit = remember(coroutineScope, sheetState) {
        { actionAfterHide ->
            coroutineScope.launch {
                runCatching { sheetState.hide() }
                actionAfterHide()
            }
        }
    }

    val sheetBg = conceptSheetContainerColor()
    val tileBg = conceptElevatedSurfaceColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val roles = rememberCoverColorRoles(accentColor)
    var resolvedAlbumName by remember(song.id, song.hash, song.album, song.albumId) {
        mutableStateOf(
            runCatching { com.concept.music.data.vip.VipManager.music.peekCachedAlbumName(song) }
                .getOrDefault(song.album.trim())
        )
    }
    LaunchedEffect(song.id, song.hash, song.album, song.albumId, song.albumAudioId, song.audioId) {
        if (resolvedAlbumName.isBlank()) {
            val (fetchedName, _) = runCatching {
                com.concept.music.data.vip.VipManager.music.resolveSongAlbumMeta(song)
            }.getOrDefault("" to "")
            if (fetchedName.isNotBlank()) {
                resolvedAlbumName = fetchedName
            }
        }
    }

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
                                    .background(roles.secondaryContainer),
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
                                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = roles.onSecondaryContainer)
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
                                    text = "${song.artist} · ${resolvedAlbumName.ifBlank { "未知专辑" }}",
                                    color = textSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // 右侧快捷查看歌曲技术参数胶囊（无边框线，采用色阶容器分离）
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = roles.secondaryContainer,
                                modifier = Modifier
                                    .softCardClickable(
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = { dismissThen(onOpenSongDetail) }
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = "音频参数",
                                        tint = roles.onSecondaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "音频详情",
                                        color = roles.onSecondaryContainer,
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
                                        onClick = { dismissThen(onOpenArtist) }
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
                                        color = roles.secondaryContainer,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.PersonSearch,
                                                contentDescription = "歌手主页",
                                                tint = roles.onSecondaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "歌手主页",
                                            color = roles.primary,
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
                                        onClick = { dismissThen(onOpenAlbum) }
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
                                        color = roles.secondaryContainer,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Album,
                                                contentDescription = "专辑详情",
                                                tint = roles.onSecondaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "所属专辑",
                                            color = roles.primary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = resolvedAlbumName.ifBlank { "未知专辑" },
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
                            onClick = { dismissThen(onOpenQualitySelect) },
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
                            onClick = { dismissThen(onOpenSpeedPitch) },
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
                            onClick = { dismissThen(onOpenSleepTimer) },
                            modifier = Modifier.weight(1f)
                        )
                        val permState by com.concept.music.service.AppPermissionManager.state.collectAsState()
                        if (permState.musicDownloadEnabled) {
                            BentoControlTile(
                                icon = Icons.Rounded.Download,
                                title = "下载歌曲",
                                subtitle = "保存至本地",
                                active = false,
                                activeColor = accentColor,
                                onClick = { dismissThen(onDownloadSong) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. 整合式视觉舞台控制台（封面样式 + 智能取色 + 5款背景效果，无边框线，采用 13 级色阶角色分离）
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
                                    tint = roles.primary,
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

                            // 紧凑型智能取色开关胶囊（无描边）
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (dynamicCoverAccentEnabled) roles.primaryContainer else sheetBg,
                                modifier = Modifier
                                    .softCardClickable(shape = RoundedCornerShape(10.dp)) {
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
                                            .background(if (dynamicCoverAccentEnabled) roles.onPrimaryContainer else textSecondary)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (dynamicCoverAccentEnabled) "智能取色 · 已开启" else "智能取色 · 已关闭",
                                        color = if (dynamicCoverAccentEnabled) roles.onPrimaryContainer else textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 双封面样式切换（无描边，色阶容器分离）
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
                                    color = if (selected) roles.primaryContainer else sheetBg,
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
                                            tint = if (selected) roles.onPrimaryContainer else textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = style.label,
                                                color = if (selected) roles.onPrimaryContainer else textPrimary,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = desc,
                                                color = if (selected) roles.onPrimaryContainerSubtle else textSecondary,
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
                                    color = if (selected) roles.primaryContainer else sheetBg,
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
                                                color = if (selected) roles.onPrimaryContainer else textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1
                                            )
                                            if (selected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(roles.onPrimaryContainer)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = bgStyle.subtitle,
                                            color = if (selected) roles.onPrimaryContainerSubtle else textSecondary,
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
    val roles = rememberCoverColorRoles(activeColor)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) roles.primaryContainer else tileBg,
        contentColor = textPrimary,
        modifier = modifier
            .softCardClickable(shape = RoundedCornerShape(16.dp), onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (active) roles.onPrimaryContainer else textPrimary.copy(alpha = 0.82f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                color = if (active) roles.onPrimaryContainer else textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = if (active) roles.onPrimaryContainerSubtle else textSecondary,
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
    val baseSheetBg = conceptSheetContainerColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val panelShape = RoundedCornerShape(26.dp)

    ConceptAnimatedOverlaySheet(
        onDismiss = onDismiss,
        isLandscape = true,
        scrimAlpha = 0.54f
    ) {
        Surface(
            shape = panelShape,
            color = baseSheetBg.copy(alpha = 0.98f),
            contentColor = textPrimary,
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.HighQuality, contentDescription = null, tint = ConceptPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择音质档位", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = textPrimary)
                    }
                    IconButton(onClick = requestDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            requestDismiss()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) ConceptPrimary.copy(alpha = 0.14f) else Color.Transparent)
                                .softClickable { onSelect() }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
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
                                        color = if (isSelected) ConceptPrimary else textPrimary
                                    )
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = requestDismiss) { Text("取消") }
                }
            }
        }
    }
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
    val baseSheetBg = conceptSheetContainerColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val panelShape = RoundedCornerShape(26.dp)

    ConceptAnimatedOverlaySheet(
        onDismiss = onDismiss,
        isLandscape = true,
        scrimAlpha = 0.54f
    ) {
        Surface(
            shape = panelShape,
            color = baseSheetBg.copy(alpha = 0.98f),
            contentColor = textPrimary,
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = ConceptPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("睡眠定时", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = textPrimary)
                    }
                    IconButton(onClick = requestDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

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
                                requestDismiss()
                            },
                            label = { Text("${mins}分钟") }
                        )
                    }
                    FilterChip(
                        selected = pendingStopAtTrackEnd && remainingSec == 0L,
                        onClick = {
                            onSetTimer(0, true)
                            Toast.makeText(context, "将在当前歌曲播完后停止", Toast.LENGTH_SHORT).show()
                            requestDismiss()
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
                            color = textSecondary,
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isTimerRunning) {
                        TextButton(
                            onClick = {
                                onCancelTimer()
                                Toast.makeText(context, "已关闭睡眠定时", Toast.LENGTH_SHORT).show()
                                requestDismiss()
                            }
                        ) {
                            Text("关闭定时", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    TextButton(onClick = requestDismiss) { Text("完成") }
                }
            }
        }
    }
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
    val baseSheetBg = conceptSheetContainerColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val panelShape = RoundedCornerShape(26.dp)

    ConceptAnimatedOverlaySheet(
        onDismiss = onDismiss,
        isLandscape = true,
        scrimAlpha = 0.54f
    ) {
        Surface(
            shape = panelShape,
            color = baseSheetBg.copy(alpha = 0.98f),
            contentColor = textPrimary,
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = ConceptPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("倍速与变调", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = textPrimary)
                    }
                    IconButton(onClick = requestDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

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
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = linkPitchToSpeed,
                        onCheckedChange = onSetLinkPitch,
                        colors = SwitchDefaults.colors(checkedThumbColor = ConceptPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onReset) {
                        Text("恢复默认")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = requestDismiss) { Text("完成") }
                }
            }
        }
    }
}

private fun formatDialogDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
