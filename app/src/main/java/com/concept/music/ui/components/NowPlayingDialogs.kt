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
    val isTablet = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    val useCenteredFloatingPanel = isLandscape || isTablet

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
    val panelShape = if (useCenteredFloatingPanel) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    }

    ConceptAnimatedOverlaySheet(
        onDismiss = onDismiss,
        isLandscape = useCenteredFloatingPanel,
        dismissDragThresholdPx = 110f
    ) {
        // 现代玻璃拟态主窗口（入退场弹簧与遮罩渐隐由 ConceptAnimatedOverlaySheet 统一在 Draw 阶段驱动，无外框描边）
        Box(
            modifier = Modifier
                .then(
                    if (isLandscape) {
                        Modifier
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .widthIn(max = if (isTablet) 820.dp else 760.dp)
                            .fillMaxWidth(if (isTablet) 0.88f else 0.94f)
                            .wrapContentHeight()
                            .heightIn(max = if (isTablet) 600.dp else 460.dp)
                    } else if (isTablet) {
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 18.dp)
                            .widthIn(max = 600.dp)
                            .fillMaxWidth(0.88f)
                            .wrapContentHeight()
                            .heightIn(max = 720.dp)
                    } else {
                        Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
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
                            start = if (isTablet) 26.dp else if (isLandscape) 22.dp else 18.dp,
                            end = if (isTablet) 26.dp else if (isLandscape) 22.dp else 18.dp,
                            top = if (isTablet) 20.dp else if (isLandscape) 14.dp else 8.dp,
                            bottom = if (isTablet) 22.dp else if (isLandscape) 16.dp else 12.dp
                        )
                        .then(if (!useCenteredFloatingPanel) Modifier.navigationBarsPadding() else Modifier)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (isTablet) 16.dp else 12.dp)
                ) {
                    // 顶部一体化手势指示横条（仅手机底部抽屉形态展示）
                    if (!useCenteredFloatingPanel) {
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
                            horizontalArrangement = Arrangement.spacedBy(if (isTablet) 12.dp else 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isTablet) 38.dp else 32.dp)
                                    .clip(RoundedCornerShape(if (isTablet) 12.dp else 10.dp))
                                    .background(roles.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = roles.onSecondaryContainer,
                                    modifier = Modifier.size(if (isTablet) 20.dp else 17.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "歌词显示设置",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrimary,
                                        fontSize = if (isTablet) 19.5.sp else 17.sp
                                    )
                                )
                                Text(
                                    text = "实时预览流光色彩、字号与排版景深",
                                    color = textSecondary.copy(alpha = 0.72f),
                                    fontSize = if (isTablet) 12.5.sp else 11.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isTablet) 10.dp else 8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(if (isTablet) 14.dp else 12.dp),
                                color = roles.secondaryContainer,
                                contentColor = roles.onSecondaryContainer,
                                modifier = Modifier.softCardClickable(shape = RoundedCornerShape(if (isTablet) 14.dp else 12.dp)) {
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
                                    modifier = Modifier.padding(
                                        horizontal = if (isTablet) 14.dp else 11.dp,
                                        vertical = if (isTablet) 8.dp else 6.dp
                                    )
                                ) {
                                    Icon(
                                        Icons.Rounded.RestartAlt,
                                        contentDescription = null,
                                        tint = roles.onSecondaryContainer,
                                        modifier = Modifier.size(if (isTablet) 16.dp else 14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        "恢复默认",
                                        color = roles.onSecondaryContainer,
                                        fontSize = if (isTablet) 13.sp else 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = glassPillBg,
                                modifier = Modifier
                                    .size(if (isTablet) 36.dp else 30.dp)
                                    .softClickable(onClick = requestDismiss)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "关闭",
                                        tint = textSecondary,
                                        modifier = Modifier.size(if (isTablet) 19.dp else 16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isLandscape) {
                        // 横屏模式：宽幅双栏现代玻璃布局，左栏与右栏等高均衡排布，彻底消除底部留白
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (isTablet) 16.dp else 14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1.03f),
                                verticalArrangement = Arrangement.spacedBy(if (isTablet) 14.dp else 10.dp)
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
                                modifier = Modifier.weight(0.97f),
                                verticalArrangement = Arrangement.spacedBy(if (isTablet) 14.dp else 10.dp)
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
    val isTablet = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Surface(
        shape = RoundedCornerShape(if (isTablet) 20.dp else 18.dp),
        color = cardBg,
        contentColor = textPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (isTablet) 18.dp else 13.dp,
                vertical = if (isTablet) 16.dp else 11.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (isTablet) 15.dp else 10.dp)
        ) {
            // 1. 流光配色胶囊矩阵（2行 × 3列等宽网格，彻底杜绝单行 6 项拥挤导致四字样式名称第 4 字被截断的问题）
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (isTablet) 8.dp else 6.5.dp)
            ) {
                val themeRows = remember { LyricHighlightTheme.entries.chunked(3) }
                themeRows.forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isTablet) 8.dp else 6.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowThemes.forEach { theme ->
                            val selected = highlightTheme == theme
                            val swatchColor = if (theme == LyricHighlightTheme.DYNAMIC_COVER) {
                                dynamicAccentColor
                            } else {
                                Color(theme.primaryHex)
                            }
                            val swatchRoles = rememberCoverColorRoles(swatchColor)
                            val chipShape = RoundedCornerShape(if (isTablet) 13.dp else 11.dp)
                            Surface(
                                shape = chipShape,
                                color = if (selected) swatchRoles.primaryContainer else sheetBg,
                                contentColor = textPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable(shape = chipShape) {
                                        onSetHighlightTheme(theme)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(
                                        horizontal = if (isTablet) 10.dp else 8.dp,
                                        vertical = if (isTablet) 10.dp else 8.dp
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isTablet) 9.5.dp else 8.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) swatchRoles.primary else swatchColor)
                                    )
                                    Spacer(modifier = Modifier.width(if (isTablet) 7.dp else 6.dp))
                                    Text(
                                        text = theme.label,
                                        color = if (selected) swatchRoles.onPrimaryContainer else textPrimary,
                                        fontSize = if (isTablet) 13.sp else 12.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. 歌词对齐方式独立行（左标签 + 右等宽分段胶囊，视野舒展不拥挤）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "歌词对齐",
                    color = textPrimary,
                    fontSize = if (isTablet) 14.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(if (isTablet) 12.dp else 10.dp))
                        .background(sheetBg)
                        .padding(if (isTablet) 3.dp else 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isTablet) 4.dp else 3.dp)
                ) {
                    LyricAlignment.entries.forEach { item ->
                        val selected = alignment == item
                        val segShape = RoundedCornerShape(if (isTablet) 10.dp else 8.dp)
                        Surface(
                            shape = segShape,
                            color = if (selected) roles.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .softCardClickable(shape = segShape) {
                                    onSetAlignment(item)
                                }
                        ) {
                            Text(
                                text = item.label,
                                color = if (selected) roles.onPrimaryContainer else textSecondary,
                                fontSize = if (isTablet) 13.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(
                                    horizontal = if (isTablet) 16.dp else 12.dp,
                                    vertical = if (isTablet) 7.dp else 5.dp
                                )
                            )
                        }
                    }
                }
            }

            // 3. 时长校准微调独立行（彻底杜绝横屏双栏下 +0.2s 被右侧边界截断问题，并与右栏滑块卡片高度对齐）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "时长校准",
                    color = textPrimary,
                    fontSize = if (isTablet) 14.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isTablet) 8.dp else 6.dp)
                ) {
                    val btnShape = RoundedCornerShape(if (isTablet) 11.dp else 9.dp)
                    Surface(
                        shape = btnShape,
                        color = sheetBg,
                        modifier = Modifier.softCardClickable(shape = btnShape) {
                            onSetOffsetMs(offsetMs - 200L)
                        }
                    ) {
                        Text(
                            text = "-0.2s",
                            color = textPrimary,
                            fontSize = if (isTablet) 13.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(
                                horizontal = if (isTablet) 14.dp else 10.dp,
                                vertical = if (isTablet) 7.5.dp else 5.5.dp
                            )
                        )
                    }
                    Surface(
                        shape = btnShape,
                        color = if (offsetMs != 0L) goldRoles.primaryContainer else roles.secondaryContainer,
                        modifier = Modifier.softCardClickable(shape = btnShape) {
                            onSetOffsetMs(0L)
                        }
                    ) {
                        Text(
                            text = if (offsetMs == 0L) "校准 0.0s" else String.format(Locale.getDefault(), "%+.1fs", offsetMs / 1000f),
                            color = if (offsetMs != 0L) goldRoles.onPrimaryContainer else roles.onSecondaryContainer,
                            fontSize = if (isTablet) 13.sp else 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(
                                horizontal = if (isTablet) 14.dp else 10.dp,
                                vertical = if (isTablet) 7.5.dp else 5.5.dp
                            )
                        )
                    }
                    Surface(
                        shape = btnShape,
                        color = sheetBg,
                        modifier = Modifier.softCardClickable(shape = btnShape) {
                            onSetOffsetMs(offsetMs + 200L)
                        }
                    ) {
                        Text(
                            text = "+0.2s",
                            color = textPrimary,
                            fontSize = if (isTablet) 13.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(
                                horizontal = if (isTablet) 14.dp else 10.dp,
                                vertical = if (isTablet) 7.5.dp else 5.5.dp
                            )
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
    val isTablet = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Surface(
        shape = RoundedCornerShape(if (isTablet) 20.dp else 18.dp),
        color = cardBg,
        contentColor = textPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (isTablet) 18.dp else 13.dp,
                vertical = if (isTablet) 15.dp else 10.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (isTablet) 7.dp else 3.dp)
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
    val isTablet = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTablet) 46.dp else 34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = textPrimary,
            fontSize = if (isTablet) 13.8.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(if (isTablet) 68.dp else 56.dp)
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
                .padding(horizontal = if (isTablet) 6.dp else 4.dp)
        )
        Box(
            modifier = Modifier
                .width(if (isTablet) 54.dp else 46.dp)
                .clip(RoundedCornerShape(if (isTablet) 8.dp else 6.dp))
                .background(roles.secondaryContainer)
                .padding(vertical = if (isTablet) 4.dp else 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = valueText,
                color = roles.onSecondaryContainer,
                fontSize = if (isTablet) 12.5.sp else 11.sp,
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
    val isTablet = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (isTablet) 12.dp else 8.dp)
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
    val isTablet = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    val tileShape = RoundedCornerShape(if (isTablet) 18.dp else 15.dp)
    Surface(
        shape = tileShape,
        color = if (checked) roles.primaryContainer else cardBg,
        modifier = modifier
            .softCardClickable(shape = tileShape, onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isTablet) 14.dp else 10.dp,
                    vertical = if (isTablet) 14.dp else 9.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(if (isTablet) 12.dp else 10.dp),
                color = if (checked) roles.primary.copy(alpha = 0.22f) else textPrimary.copy(alpha = 0.07f),
                modifier = Modifier.size(if (isTablet) 38.dp else 30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (checked) roles.onPrimaryContainer else textSecondary,
                        modifier = Modifier.size(if (isTablet) 20.dp else 16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(if (isTablet) 10.dp else 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (checked) roles.onPrimaryContainer else textPrimary,
                    fontSize = if (isTablet) 13.8.sp else 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                if (isTablet) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = subtitle,
                    color = if (checked) roles.onPrimaryContainerSubtle else textSecondary,
                    fontSize = if (isTablet) 11.8.sp else 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(if (isTablet) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (checked) roles.primary else textSecondary.copy(alpha = 0.25f))
            )
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    var pendingAfterDismiss by remember { mutableStateOf<(() -> Unit)?>(null) }

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

    val panelShape = if (isLandscape) {
        RoundedCornerShape(26.dp)
    } else {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    }

    ConceptAnimatedOverlaySheet(
        onDismiss = {
            onDismiss()
            pendingAfterDismiss?.invoke()
            pendingAfterDismiss = null
        },
        isLandscape = isLandscape,
        zIndex = 88f
    ) {
        val dismissThen: (() -> Unit) -> Unit = { actionAfterHide ->
            pendingAfterDismiss = actionAfterHide
            requestDismiss()
        }

        Surface(
            shape = panelShape,
            color = sheetBg,
            contentColor = textPrimary,
            shadowElevation = 24.dp,
            modifier = (if (isLandscape) {
                Modifier
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .widthIn(max = 540.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
            } else {
                Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
            }).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
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
                    if (!isLandscape) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .sheetVerticalDragGesture()
                                .padding(top = 8.dp, bottom = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp),
                                shape = CircleShape,
                                color = textSecondary.copy(alpha = 0.28f)
                            ) {}
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
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
                                    val moreArtistList = remember(song.artist) { parseSongArtistNames(song.artist) }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (moreArtistList.size >= 2) "歌手主页 (${moreArtistList.size}位)" else "歌手主页",
                                            color = roles.primary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (moreArtistList.size >= 2) moreArtistList.joinToString(" / ") else song.artist.ifBlank { "未知歌手" },
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = textPrimary.copy(alpha = 0.06f),
                            modifier = Modifier.softCardClickable(shape = RoundedCornerShape(12.dp)) {
                                onSetTimer(mins, stopAfterSong)
                                Toast.makeText(
                                    context,
                                    "已设置 $mins 分钟后停止",
                                    Toast.LENGTH_SHORT
                                ).show()
                                requestDismiss()
                            }
                        ) {
                            Text(
                                text = "${mins}分钟",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                            )
                        }
                    }
                    val stopEndSelected = pendingStopAtTrackEnd && remainingSec == 0L
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (stopEndSelected) ConceptPrimary.copy(alpha = 0.18f) else textPrimary.copy(alpha = 0.06f),
                        modifier = Modifier.softCardClickable(shape = RoundedCornerShape(12.dp)) {
                            onSetTimer(0, true)
                            Toast.makeText(context, "将在当前歌曲播完后停止", Toast.LENGTH_SHORT).show()
                            requestDismiss()
                        }
                    ) {
                        Text(
                            text = "播完本首",
                            fontSize = 12.sp,
                            fontWeight = if (stopEndSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (stopEndSelected) ConceptPrimary else textPrimary,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                        )
                    }
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        speedPresets.forEach { preset ->
                            val isPresetSelected = abs(speed - preset) < 0.03f
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = if (isPresetSelected) ConceptPrimary.copy(alpha = 0.18f) else textPrimary.copy(alpha = 0.06f),
                                modifier = Modifier.softCardClickable(shape = RoundedCornerShape(11.dp)) {
                                    onSetSpeed(preset)
                                }
                            ) {
                                Text(
                                    text = "${preset}x",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isPresetSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isPresetSelected) ConceptPrimary else textSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
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

/**
 * 拆分单曲中的多位合作歌手名称（支持 、 / & ， , feat. ft. 等常见分隔符）
 */
fun parseSongArtistNames(rawArtist: String): List<String> {
    if (rawArtist.isBlank()) return emptyList()
    return rawArtist
        .split(Regex("\\s*(?:、|/|&|，|,|\\b(?:[fF]eat|[fF]t)\\b\\.?)\\s*"))
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "未知歌手" }
        .distinct()
}

/**
 * 多歌手（>=2位）选择悬浮窗：
 * - 当在歌曲列表更多菜单点击「查看歌手」时：`isPlayerStyle = false`，采用应用主题卡片色彩体系；
 * - 当在播放页更多菜单点击「歌手主页」时：`isPlayerStyle = true`，采用与播放页封面动态取色（`rememberCoverColorRoles(accentColor)`）完全融合的流光毛玻璃悬浮窗风格；
 * - 异步静默补全各位歌手的真实头像与作品数量，支持一键选择前往对应歌手主页。
 */
@Composable
fun ArtistSelectionFloatingDialog(
    song: Song,
    artistNames: List<String>,
    isPlayerStyle: Boolean = false,
    accentColor: Color = ConceptPrimary,
    onSelectArtist: (artistName: String, resolvedArtistId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val baseSheetBg = conceptSheetContainerColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val roles = rememberCoverColorRoles(if (isPlayerStyle) accentColor else ConceptPrimary)

    val panelTopColor = if (isPlayerStyle) {
        roles.primaryContainer.copy(alpha = 0.58f).compositeOver(baseSheetBg.copy(alpha = 0.96f))
    } else {
        baseSheetBg.copy(alpha = 0.98f)
    }
    val panelMidColor = if (isPlayerStyle) {
        roles.primaryContainer.copy(alpha = 0.24f).compositeOver(baseSheetBg.copy(alpha = 0.97f))
    } else {
        baseSheetBg.copy(alpha = 0.98f)
    }
    val panelBottomColor = baseSheetBg.copy(alpha = 0.99f)
    val itemCardBg = if (isPlayerStyle) {
        textPrimary.copy(alpha = 0.065f).compositeOver(baseSheetBg.copy(alpha = 0.90f))
    } else {
        conceptElevatedSurfaceColor()
    }
    val activeHighlight = if (isPlayerStyle) roles.primary else ConceptPrimary

    // 异步静默查询每位歌手的头像与 ID，提升视觉精致度与跳转速度
    val resolvedArtistsMap = remember(song.id, artistNames) {
        mutableStateMapOf<String, com.concept.music.data.model.ArtistItem>()
    }
    LaunchedEffect(song.id, artistNames) {
        artistNames.forEach { name ->
            launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val matched = com.concept.music.data.vip.VipManager.music
                        .searchArtists(name, page = 1, pageSize = 4)
                        .firstOrNull { it.id.isNotBlank() && it.id != "0" }
                    if (matched != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            resolvedArtistsMap[name] = matched
                        }
                    }
                }
            }
        }
    }

    var pendingSelection by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val panelShape = RoundedCornerShape(26.dp)

    ConceptAnimatedOverlaySheet(
        onDismiss = {
            val chosen = pendingSelection
            pendingSelection = null
            if (chosen != null) {
                onSelectArtist(chosen.first, chosen.second)
            } else {
                onDismiss()
            }
        },
        isLandscape = true,
        scrimAlpha = 0.54f,
        zIndex = 95f
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 18.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .heightIn(max = 480.dp)
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
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 顶部标题栏
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
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (isPlayerStyle) roles.secondaryContainer else ConceptPrimary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonSearch,
                                    contentDescription = null,
                                    tint = if (isPlayerStyle) roles.onSecondaryContainer else ConceptPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "选择要查看的歌手",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrimary,
                                        fontSize = 16.5.sp
                                    )
                                )
                                Text(
                                    text = "《${song.title}》共 ${artistNames.size} 位创作者",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textSecondary,
                                        fontSize = 11.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = requestDismiss,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭",
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 歌手卡片列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        itemsIndexed(
                            items = artistNames,
                            key = { idx, name -> "artist_pick_${idx}_$name" }
                        ) { index, artistName ->
                            val resolved = resolvedArtistsMap[artistName]
                            val avatarUrl = resolved?.avatarUrl.orEmpty()
                            val directFirstId = if (index == 0) song.artistId.trim().takeIf { it.isNotBlank() && it != "0" } else null
                            val resolvedId = resolved?.id?.takeIf { it.isNotBlank() && it != "0" } ?: directFirstId

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = itemCardBg,
                                contentColor = textPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .softCardClickable(
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = {
                                            pendingSelection = artistName to resolvedId
                                            requestDismiss()
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPlayerStyle) roles.secondaryContainer else activeHighlight.copy(alpha = 0.14f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (avatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = avatarUrl,
                                                contentDescription = artistName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = artistName.take(1).uppercase(),
                                                color = if (isPlayerStyle) roles.onSecondaryContainer else activeHighlight,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(13.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = artistName,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = textPrimary,
                                                fontSize = 15.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (resolved != null && resolved.songCount > 0) {
                                                "${resolved.songCount} 首单曲 · 点击前往歌手主页"
                                            } else {
                                                "点击前往歌手主页"
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = textSecondary,
                                                fontSize = 11.5.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = activeHighlight.copy(alpha = 0.14f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "主页",
                                                color = activeHighlight,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                tint = activeHighlight,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
