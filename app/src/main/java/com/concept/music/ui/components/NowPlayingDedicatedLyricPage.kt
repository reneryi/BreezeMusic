package com.concept.music.ui.components

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.OrientationEventListener
import android.graphics.drawable.BitmapDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.Song
import com.concept.music.data.model.SongDetailInfo
import com.concept.music.service.AudioEffectManager
import com.concept.music.service.SystemLyricBridge
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptVioletAccent
import com.concept.music.ui.theme.ConceptMotion
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.SystemBarAppearanceEffect
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.conceptSheetSpringEntrance
import com.concept.music.ui.theme.isConceptDarkTheme
import com.concept.music.ui.theme.rememberIsLifecycleStarted
import com.concept.music.ui.theme.readableOnDarkSurface
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.LyricHighlightTheme
import com.concept.music.ui.viewmodel.PlayMode
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import com.concept.music.ui.viewmodel.SavedPlayQueue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.lerp
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlin.math.min
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.pow

/**
 * 智能识别当前播放歌曲是否为纯中文歌曲（或无需展示翻译按钮）
 * 规则：
 * 1. 若歌词本身没有任何翻译内容 (!lyricData.hasTranslation 且所有行 translation 为空)，则不显示翻译按钮；
 * 2. 若歌曲主歌词不包含日文假名、韩文谚文，且拉丁字母占比极低（主要为中文汉字），判定为中文歌，自动隐藏翻译按钮。
 */
/**
 * 格式化播放页进度条上方评论计数：
 * - 小于 1000 显示准确数值（如 856）
 * - 大于等于 1000 用 k 为单位保留一位小数（如 1.1k）
 * - 大于等于 10000 用 w 为单位保留一位小数（如 1.2w）
 */
/**
 * 独立高定歌词页面：
 * - 右侧按钮与底部播放键全部去除边框线 (`.border(...)`)，采用圆润饱满的无边框磨砂胶囊与圆钮；
 * - 修复居中/居右对齐时左窄右宽不对称问题；
 * - 支持封面动态取色高亮主题 (`DYNAMIC_COVER`) 与副歌词字号缩放比 (`lyricTranslationScale`)。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RedesignedDedicatedLyricPage(
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    rawPositionMs: Long,
    lyricPositionProvider: () -> Long = { currentPositionMs },
    rawPositionProvider: () -> Long = { rawPositionMs },
    durationMs: Long,
    isPlaying: Boolean,
    isFavorite: Boolean,
    showTranslation: Boolean,
    showTranslationButton: Boolean,
    showPhonetic: Boolean,
    showPhoneticButton: Boolean = false,
    lyricFontSize: Float,
    lyricTranslationScale: Float = 0.80f,
    lyricAlignment: LyricAlignment,
    lyricWordByWord: Boolean,
    lyricBlurEnabled: Boolean,
    lyricBlurIntensity: Float,
    lyricLineSpacing: Float,
    lyricHighlightTheme: LyricHighlightTheme,
    dynamicCoverAccent: Color = ConceptPrimary,
    dynamicCoverSecondary: Color = ConceptCyanAccent,
    onLineClick: (Int) -> Unit,
    onReturnToCover: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleTranslation: () -> Unit,
    onTogglePhonetic: () -> Unit = {},
    onOpenLyricSettings: () -> Unit
) {
    val activePrimaryColor = if (lyricHighlightTheme == LyricHighlightTheme.DYNAMIC_COVER) {
        dynamicCoverAccent.readableOnDarkSurface(minLuminance = 0.46f)
    } else {
        Color(lyricHighlightTheme.primaryHex)
    }
    val activeSecondaryColor = if (lyricHighlightTheme == LyricHighlightTheme.DYNAMIC_COVER) {
        dynamicCoverSecondary.readableOnDarkSurface(minLuminance = 0.50f)
    } else {
        Color(lyricHighlightTheme.secondaryHex)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onReturnToCover
                )
        ) {
            if (lyricData.lines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "纯音乐，暂无歌词或正在检索官方歌词...",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            } else {
                val listState = rememberLazyListState()
                val isDragged by listState.interactionSource.collectIsDraggedAsState()
                var isUserScrubbing by remember { mutableStateOf(false) }

                LaunchedEffect(isDragged) {
                    if (isDragged) {
                        isUserScrubbing = true
                    }
                }

                LaunchedEffect(isUserScrubbing, isDragged, listState.isScrollInProgress) {
                    if (isUserScrubbing && !isDragged && !listState.isScrollInProgress) {
                        delay(3200L)
                        isUserScrubbing = false
                    }
                }

                LaunchedEffect(currentLyricIndex, isUserScrubbing) {
                    if (!isUserScrubbing && currentLyricIndex in lyricData.lines.indices) {
                        val layoutInfo = listState.layoutInfo
                        val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(360)
                        val goldenFocusOffset = -(viewportHeight * 0.28f).roundToInt()
                        val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentLyricIndex }
                        if (targetItem != null) {
                            val currentTargetCenter = targetItem.offset + (targetItem.size / 2)
                            val desiredCenter = -goldenFocusOffset
                            val scrollDelta = (currentTargetCenter - desiredCenter).toFloat()
                            if (abs(scrollDelta) > 2f) {
                                listState.animateScrollBy(
                                    value = scrollDelta,
                                    animationSpec = tween(
                                        durationMillis = 420,
                                        easing = ConceptMotion.LyricSmoothScrollEasing
                                    )
                                )
                            }
                        } else {
                            listState.animateScrollToItem(
                                index = currentLyricIndex,
                                scrollOffset = goldenFocusOffset
                            )
                        }
                    }
                }

                val scrubLineIndex by remember(lyricData.lines) {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val visibleItems = layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) {
                            currentLyricIndex.coerceIn(0, (lyricData.lines.size - 1).coerceAtLeast(0))
                        } else {
                            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                            visibleItems.minByOrNull { item ->
                                abs((item.offset + item.size / 2) - viewportCenter)
                            }?.index?.coerceIn(0, (lyricData.lines.size - 1).coerceAtLeast(0)) ?: 0
                        }
                    }
                }

                val textAlign = when (lyricAlignment) {
                    LyricAlignment.LEFT -> TextAlign.Start
                    LyricAlignment.CENTER -> TextAlign.Center
                    LyricAlignment.RIGHT -> TextAlign.End
                }
                val horizontalAlignment = when (lyricAlignment) {
                    LyricAlignment.LEFT -> Alignment.Start
                    LyricAlignment.CENTER -> Alignment.CenterHorizontally
                    LyricAlignment.RIGHT -> Alignment.End
                }
                val flowArrangement = when (lyricAlignment) {
                    LyricAlignment.LEFT -> Arrangement.Start
                    LyricAlignment.CENTER -> Arrangement.Center
                    LyricAlignment.RIGHT -> Arrangement.End
                }
                val supportsHardwareBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

                val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
                val effectiveLyricPageFontSize = if (isLargeScreen) (lyricFontSize + 2f).coerceAtMost(36f) else lyricFontSize

                // 平衡左右内边距，使歌词显示位置保持不变，同时在大屏下让左右两侧空白点击可返回封面页
                val startPad = when (lyricAlignment) {
                    LyricAlignment.CENTER -> 28.dp
                    LyricAlignment.RIGHT -> 16.dp
                    LyricAlignment.LEFT -> 8.dp
                }
                val endPad = when (lyricAlignment) {
                    LyricAlignment.CENTER -> 28.dp
                    LyricAlignment.RIGHT -> if (isLargeScreen) 28.dp else 54.dp
                    LyricAlignment.LEFT -> if (isLargeScreen) 28.dp else 50.dp
                }

                // 在平板端将歌词列表约束在中央原位置（widthIn(max = 560.dp)），使左右两侧空白区域完全暴露给外层 Box，点击左右空白即可返回封面页；
                // 同时在列表内部点击非歌词文字空白处也支持返回封面页
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(if (isLargeScreen) Modifier.widthIn(max = 560.dp) else Modifier)
                        .fillMaxSize()
                        .pointerInput(onReturnToCover) {
                            detectTapGestures(onTap = { onReturnToCover() })
                        }
                        .padding(start = startPad, end = endPad),
                    contentPadding = PaddingValues(vertical = 170.dp),
                    horizontalAlignment = horizontalAlignment
                ) {
                    itemsIndexed(
                        items = lyricData.lines,
                        key = { idx, item -> "${idx}_${item.startTimeMs}" },
                        contentType = { _, _ -> "lyric_line" }
                    ) { index, line ->
                        val isCurrent = index == currentLyricIndex
                        val isScrubTarget = isUserScrubbing && index == scrubLineIndex
                        val distance = abs(index - currentLyricIndex)

                        // 零 Text Relayout 弹性缩放：统一基础排版字号，通过 GPU graphicsLayer 连续驱动缩放 (0.86f <-> 1.05f)
                        val lineScale by animateFloatAsState(
                            targetValue = when {
                                isCurrent -> 1.05f
                                isScrubTarget -> 1.0f
                                else -> (0.93f - (distance - 1).coerceAtLeast(0) * 0.02f).coerceAtLeast(0.86f)
                            },
                            animationSpec = ConceptMotion.spatialPageSpring(),
                            label = "lyricLineScale"
                        )

                        val targetDepthBlurVal = if (lyricBlurEnabled && !isUserScrubbing && !isCurrent) {
                            when (distance) {
                                1 -> 0.72f * lyricBlurIntensity
                                2 -> 1.35f * lyricBlurIntensity
                                3 -> 2.10f * lyricBlurIntensity
                                else -> 2.85f * lyricBlurIntensity
                            }.coerceIn(0f, 5.0f)
                        } else 0f
                        val depthBlurVal by animateFloatAsState(
                            targetValue = targetDepthBlurVal,
                            animationSpec = tween(
                                durationMillis = 420,
                                easing = ConceptMotion.LyricSmoothScrollEasing
                            ),
                            label = "lyricLineDepthBlur"
                        )

                        val targetDistanceAlpha = when {
                            isCurrent -> 1.0f
                            isScrubTarget -> 0.92f
                            distance == 1 -> 0.58f
                            distance == 2 -> 0.38f
                            distance == 3 -> 0.24f
                            else -> 0.14f
                        }
                        val distanceAlpha by animateFloatAsState(
                            targetValue = targetDistanceAlpha,
                            animationSpec = tween(
                                durationMillis = 420,
                                easing = ConceptMotion.LyricSmoothScrollEasing
                            ),
                            label = "lyricLineDistanceAlpha"
                        )

                        val transformOriginX = when (lyricAlignment) {
                            LyricAlignment.LEFT -> 0f
                            LyricAlignment.CENTER -> 0.5f
                            LyricAlignment.RIGHT -> 1f
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = lineScale
                                    scaleY = lineScale
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(transformOriginX, 0.5f)
                                }
                                .then(
                                    if (supportsHardwareBlur && depthBlurVal >= 0.05f) {
                                        Modifier.blur(depthBlurVal.dp)
                                    } else Modifier
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isScrubTarget) activePrimaryColor.copy(alpha = 0.10f) else Color.Transparent
                                )
                                .softClickable(
                                    pressScale = 0.98f,
                                    onClick = {
                                        isUserScrubbing = false
                                        onLineClick(index)
                                    }
                                )
                                .padding(vertical = lyricLineSpacing.dp, horizontal = 8.dp)
                        ) {
                            Column(
                                horizontalAlignment = horizontalAlignment,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (index == 0 && currentLyricIndex <= 0) {
                                    LyricInterludeBreathingIndicator(
                                        lyricData = lyricData,
                                        lineIndex = -1,
                                        initialPositionMs = currentPositionMs,
                                        isPlaying = isPlaying,
                                        lyricPositionProvider = lyricPositionProvider,
                                        lyricFontSize = effectiveLyricPageFontSize,
                                        accentPrimary = activePrimaryColor,
                                        accentSecondary = activeSecondaryColor,
                                        horizontalAlignment = horizontalAlignment,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )
                                }

                                val phoneticText = line.phonetic
                                if (showPhonetic && showPhoneticButton && !phoneticText.isNullOrBlank()) {
                                    val phoneticFontSize = (effectiveLyricPageFontSize * lyricTranslationScale * 0.86f).coerceAtLeast(11.5f)
                                    Text(
                                        text = phoneticText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isCurrent) activePrimaryColor.copy(alpha = 0.90f) else Color.White.copy(alpha = (distanceAlpha * 0.68f).coerceIn(0.12f, 0.55f)),
                                            fontSize = phoneticFontSize.sp,
                                            lineHeight = (phoneticFontSize * 1.38f).sp,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = textAlign,
                                            letterSpacing = 0.4.sp
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (isCurrent && line.hasWordTiming && lyricWordByWord) {
                                    DedicatedActiveKaraokeWords(
                                        words = line.words,
                                        initialPositionMs = currentPositionMs,
                                        isPlaying = isPlaying,
                                        lyricPositionProvider = lyricPositionProvider,
                                        lyricFontSize = effectiveLyricPageFontSize,
                                        flowArrangement = flowArrangement,
                                        activePrimaryColor = activePrimaryColor,
                                        activeSecondaryColor = activeSecondaryColor
                                    )
                                } else {
                                    val textColor = if (isCurrent) activePrimaryColor else Color.White.copy(alpha = distanceAlpha)
                                    val textWeight = if (isCurrent || isScrubTarget) FontWeight.ExtraBold else FontWeight.SemiBold
                                    val lineSize = effectiveLyricPageFontSize.sp

                                    val fallbackBlurShadow = if (!supportsHardwareBlur && depthBlurVal >= 0.15f) {
                                        Shadow(
                                            color = Color.White.copy(alpha = (distanceAlpha * 0.85f).coerceIn(0f, 1f)),
                                            offset = Offset.Zero,
                                            blurRadius = (depthBlurVal * 3.0f).coerceAtLeast(1f)
                                        )
                                    } else null

                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = textWeight,
                                            color = if (fallbackBlurShadow != null) Color.Transparent else textColor,
                                            fontSize = lineSize,
                                            lineHeight = (effectiveLyricPageFontSize + 9f).sp,
                                            textAlign = textAlign,
                                            shadow = fallbackBlurShadow
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                val trans = line.translation
                                if (showTranslation && showTranslationButton && !trans.isNullOrBlank()) {
                                    val transFontSize = (effectiveLyricPageFontSize * lyricTranslationScale).coerceAtLeast(13f)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = trans,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = when {
                                                isCurrent -> activePrimaryColor.copy(alpha = 0.90f)
                                                isScrubTarget -> Color.White.copy(alpha = 0.78f)
                                                else -> Color.White.copy(alpha = (distanceAlpha * 0.82f).coerceIn(0.14f, 0.62f))
                                            },
                                            fontSize = transFontSize.sp,
                                            lineHeight = (transFontSize * 1.42f).sp,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = textAlign
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (isCurrent) {
                                    LyricInterludeBreathingIndicator(
                                        lyricData = lyricData,
                                        lineIndex = index,
                                        initialPositionMs = currentPositionMs,
                                        isPlaying = isPlaying,
                                        lyricPositionProvider = lyricPositionProvider,
                                        lyricFontSize = effectiveLyricPageFontSize,
                                        accentPrimary = activePrimaryColor,
                                        accentSecondary = activeSecondaryColor,
                                        horizontalAlignment = horizontalAlignment,
                                        modifier = Modifier.padding(top = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 手动滑动歌词时的「双翼渐隐中空导引线 (Split-Wing Hollow Timeline)」
                androidx.compose.animation.AnimatedVisibility(
                    visible = isUserScrubbing && scrubLineIndex in lyricData.lines.indices,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(160)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(if (isLargeScreen) Modifier.widthIn(max = 580.dp) else Modifier)
                        .fillMaxWidth()
                ) {
                    val targetTimeMs = lyricData.lines.getOrNull(scrubLineIndex)?.startTimeMs ?: 0L
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = Color(0xD90E1624),
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = formatDuration(targetTimeMs),
                                    color = activePrimaryColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(1.5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                activePrimaryColor.copy(alpha = 0.68f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(1.5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                activePrimaryColor.copy(alpha = 0.68f)
                                            )
                                        )
                                    )
                            )
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = activePrimaryColor.copy(alpha = 0.28f),
                                modifier = Modifier
                                    .softCardClickable(shape = RoundedCornerShape(11.dp)) {
                                        isUserScrubbing = false
                                        onLineClick(scrubLineIndex)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "跳转播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "播放",
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 手机端：保持屏幕右侧偏下黄金拇指区竖向按钮组；平板端：统一整合进下方居中紧凑控播栏，避免按钮散落与遮挡右侧空白点击返回区
            val isLargeScreenDock = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
            if (!isLargeScreenDock) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LyricRightDockGlassButton(
                        active = isFavorite,
                        activeColor = Color(0xFFFF4D6D),
                        onClick = onToggleFavorite
                    ) {
                        PlumpHeartCanvasIcon(
                            isFavorite = isFavorite,
                            color = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTranslationButton,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        LyricRightDockGlassButton(
                            active = showTranslation,
                            activeColor = activePrimaryColor,
                            onClick = onToggleTranslation
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "译",
                                    color = if (showTranslation) Color.White else Color.White.copy(alpha = 0.76f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (showTranslation) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .offset(y = 8.dp)
                                            .size(width = 10.dp, height = 2.5.dp)
                                            .clip(CircleShape)
                                            .background(activePrimaryColor)
                                    )
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showPhoneticButton,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        LyricRightDockGlassButton(
                            active = showPhonetic,
                            activeColor = activePrimaryColor,
                            onClick = onTogglePhonetic
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "音",
                                    color = if (showPhonetic) Color.White else Color.White.copy(alpha = 0.76f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (showPhonetic) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .offset(y = 8.dp)
                                            .size(width = 10.dp, height = 2.5.dp)
                                            .clip(CircleShape)
                                            .background(activePrimaryColor)
                                    )
                                }
                            }
                        }
                    }

                    LyricRightDockGlassButton(
                        active = false,
                        activeColor = activePrimaryColor,
                        onClick = onOpenLyricSettings
                    ) {
                        Canvas(modifier = Modifier.size(19.dp)) {
                            val w = size.width
                            val h = size.height
                            val stroke = 2.2.dp.toPx()
                            val c = Color.White.copy(alpha = 0.90f)
                            drawLine(c, Offset(w * 0.14f, h * 0.32f), Offset(w * 0.86f, h * 0.32f), stroke, StrokeCap.Round)
                            drawCircle(activePrimaryColor, radius = 3.0.dp.toPx(), center = Offset(w * 0.65f, h * 0.32f))

                            drawLine(c, Offset(w * 0.14f, h * 0.68f), Offset(w * 0.86f, h * 0.68f), stroke, StrokeCap.Round)
                            drawCircle(c, radius = 3.0.dp.toPx(), center = Offset(w * 0.35f, h * 0.68f))
                        }
                    }
                }
            }
        }

        // 歌词页专属底部极简控播栏：平板端将播放键、进度条与右侧工具按钮紧凑居中编组，彻底告别分散感
        val isLargeScreenBottom = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .then(if (isLargeScreenBottom) Modifier.widthIn(max = 580.dp) else Modifier)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    LyricPageMinimalBottomBar(
                        currentPositionMs = rawPositionMs,
                        positionProvider = rawPositionProvider,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        accentColor = activePrimaryColor,
                        onTogglePlay = onTogglePlay,
                        onSeekTo = onSeekTo
                    )
                }

                if (isLargeScreenBottom) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (showTranslationButton) {
                            LyricRightDockGlassButton(
                                active = showTranslation,
                                activeColor = activePrimaryColor,
                                buttonSize = 46.dp,
                                onClick = onToggleTranslation
                            ) {
                                Text(
                                    text = "译",
                                    color = if (showTranslation) Color.White else Color.White.copy(alpha = 0.78f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        if (showPhoneticButton) {
                            LyricRightDockGlassButton(
                                active = showPhonetic,
                                activeColor = activePrimaryColor,
                                buttonSize = 46.dp,
                                onClick = onTogglePhonetic
                            ) {
                                Text(
                                    text = "音",
                                    color = if (showPhonetic) Color.White else Color.White.copy(alpha = 0.78f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        LyricRightDockGlassButton(
                            active = isFavorite,
                            activeColor = Color(0xFFFF4D6D),
                            buttonSize = 46.dp,
                            onClick = onToggleFavorite
                        ) {
                            PlumpHeartCanvasIcon(
                                isFavorite = isFavorite,
                                color = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(23.dp)
                            )
                        }

                        LyricRightDockGlassButton(
                            active = false,
                            activeColor = activePrimaryColor,
                            buttonSize = 46.dp,
                            onClick = onOpenLyricSettings
                        ) {
                            Canvas(modifier = Modifier.size(22.dp)) {
                                val w = size.width
                                val h = size.height
                                val stroke = 2.4.dp.toPx()
                                val c = Color.White.copy(alpha = 0.90f)
                                drawLine(c, Offset(w * 0.14f, h * 0.32f), Offset(w * 0.86f, h * 0.32f), stroke, StrokeCap.Round)
                                drawCircle(activePrimaryColor, radius = 3.3.dp.toPx(), center = Offset(w * 0.65f, h * 0.32f))

                                drawLine(c, Offset(w * 0.14f, h * 0.68f), Offset(w * 0.86f, h * 0.68f), stroke, StrokeCap.Round)
                                drawCircle(c, radius = 3.3.dp.toPx(), center = Offset(w * 0.35f, h * 0.68f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DedicatedActiveKaraokeWords(
    words: List<com.concept.music.data.model.LyricWord>,
    initialPositionMs: Long,
    isPlaying: Boolean,
    lyricPositionProvider: () -> Long,
    lyricFontSize: Float,
    flowArrangement: Arrangement.Horizontal,
    activePrimaryColor: Color,
    activeSecondaryColor: Color
) {
    val isLifecycleStarted = rememberIsLifecycleStarted()
    var sampledPosMs by remember { mutableLongStateOf(lyricPositionProvider()) }
    LaunchedEffect(initialPositionMs) {
        sampledPosMs = lyricPositionProvider()
    }
    LaunchedEffect(isPlaying, isLifecycleStarted, lyricPositionProvider) {
        if (isPlaying && isLifecycleStarted) {
            while (isActive) {
                withFrameMillis {
                    sampledPosMs = lyricPositionProvider()
                }
            }
        } else {
            sampledPosMs = lyricPositionProvider()
        }
    }
    val livePosMs = sampledPosMs
    val dimColor = Color.White.copy(alpha = 0.40f)

    FlowRow(
        horizontalArrangement = flowArrangement,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        words.forEach { word ->
            val wordProgress = when {
                word.durationMs > 0L -> ((livePosMs - word.startTimeMs).toFloat() / word.durationMs.toFloat()).coerceIn(0f, 1f)
                livePosMs >= word.startTimeMs -> 1f
                else -> 0f
            }
            val isSinging = wordProgress in 0.01f..0.99f

            val annotatedWord = buildAnnotatedString {
                if (wordProgress <= 0f) {
                    withStyle(SpanStyle(color = dimColor)) {
                        append(word.text)
                    }
                } else if (wordProgress >= 1f) {
                    withStyle(SpanStyle(color = activePrimaryColor)) {
                        append(word.text)
                    }
                } else {
                    val leftStop = (wordProgress - 0.14f).coerceAtLeast(0f)
                    val rightStop = (wordProgress + 0.14f).coerceAtMost(1f)
                    withStyle(
                        SpanStyle(
                            brush = Brush.horizontalGradient(
                                0.0f to activePrimaryColor,
                                leftStop to activeSecondaryColor,
                                rightStop to dimColor,
                                1.0f to dimColor
                            )
                        )
                    ) {
                        append(word.text)
                    }
                }
            }

            val wave = if (isSinging) sin(wordProgress * PI.toFloat()) else 0f
            val isLongNote = word.durationMs >= 700L && isSinging
            val shimmer = if (isLongNote) 0.85f + 0.15f * sin(wordProgress * PI.toFloat() * 3f) else 1f

            Text(
                text = annotatedWord,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = lyricFontSize.sp,
                    lineHeight = (lyricFontSize + 9f).sp,
                    shadow = if (isLongNote) {
                        Shadow(
                            color = activePrimaryColor.copy(alpha = (0.82f * wave * shimmer).coerceIn(0f, 1f)),
                            offset = Offset.Zero,
                            blurRadius = (20f * wave * shimmer + 6f).coerceAtLeast(1f)
                        )
                    } else null
                ),
                modifier = Modifier.graphicsLayer {
                    translationY = -3.4f * wave * shimmer
                    val s = 1f + 0.038f * wave * shimmer
                    scaleX = s
                    scaleY = s
                }
            )
        }
    }
}

/**
 * 基于酷狗 KRC 精确时间戳的「无框三点流光呼吸指示器 (LyricInterludeBreathingIndicator)」：
 * - 利用 `LyricData.getInterludeProgressAt` 精准探测前奏（≥3s）及行间器乐间奏（≥4s）；
 * - 不使用任何胶囊背景框、不显示剩余秒数，直接悬浮于背景之上，采用与 KRC 逐字歌词完全一致的渲染管线：
 *   按间奏进度 [0 -> 1] 从左至右依次流光染色点亮、叠加柔光霓虹辉光，并伴随正弦波弹性呼吸上浮跳动。
 */
@Composable
internal fun LyricInterludeBreathingIndicator(
    lyricData: LyricData,
    lineIndex: Int,
    initialPositionMs: Long,
    isPlaying: Boolean,
    lyricPositionProvider: () -> Long,
    lyricFontSize: Float = 24f,
    accentPrimary: Color,
    accentSecondary: Color,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    modifier: Modifier = Modifier
) {
    val isLifecycleStarted = rememberIsLifecycleStarted()
    val hasCandidate = remember(lyricData, lineIndex) {
        lyricData.hasInterludeCandidateAt(lineIndex)
    }
    var sampledPosMs by remember(lyricData, lineIndex) { mutableLongStateOf(lyricPositionProvider()) }
    LaunchedEffect(initialPositionMs) {
        sampledPosMs = lyricPositionProvider()
    }
    LaunchedEffect(isPlaying, isLifecycleStarted, hasCandidate, lyricData, lineIndex, lyricPositionProvider) {
        if (isPlaying && isLifecycleStarted && hasCandidate) {
            while (isActive) {
                withFrameMillis {
                    sampledPosMs = lyricPositionProvider()
                }
            }
        } else {
            sampledPosMs = lyricPositionProvider()
        }
    }

    val progress = if (hasCandidate) {
        lyricData.getInterludeProgressAt(sampledPosMs, lineIndex)
    } else -1f
    val visible = progress >= 0f

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.86f, animationSpec = ConceptMotion.expressiveSpring()),
        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.86f, animationSpec = tween(180)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = horizontalAlignment
        ) {
            LyricFloatingBreathingDots(
                progress = progress.coerceIn(0f, 1f),
                isPlaying = isPlaying,
                dotFontSizeSp = (lyricFontSize * 0.68f).coerceIn(14f, 22f),
                activePrimaryColor = accentPrimary,
                activeSecondaryColor = accentSecondary
            )
        }
    }
}

/**
 * 与 KRC 歌词同源渲染的「无框悬浮三点流光呼吸组件 (LyricFloatingBreathingDots)」：
 * - 无任何背景框与文字标签，直接悬浮在播放背景上；
 * - 每个圆点采用与 `DedicatedActiveKaraokeWords` 完全一致的 `SpanStyle(Brush.horizontalGradient)` 逐点扫光染色、
 *   `Shadow` 柔光辉光以及 `graphicsLayer` 正弦呼吸上浮与弹性缩放。
 */
@Composable
internal fun LyricFloatingBreathingDots(
    progress: Float,
    isPlaying: Boolean = true,
    dotFontSizeSp: Float = 16f,
    activePrimaryColor: Color,
    activeSecondaryColor: Color
) {
    val breathPhase: Float
    if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "lyricFloatingDotsBreath")
        val animatedPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1280, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "lyricFloatingDotsWavePhase"
        )
        breathPhase = animatedPhase
    } else {
        breathPhase = 0f
    }

    val dimColor = Color.White.copy(alpha = 0.40f)
    val spacingDp = (dotFontSizeSp * 0.42f).coerceIn(6f, 11f).dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacingDp)
    ) {
        for (i in 0 until 3) {
            val dotProgress = ((progress * 3f) - i).coerceIn(0f, 1f)
            val isLighting = dotProgress in 0.01f..0.99f
            val isLit = dotProgress >= 1f

            val annotatedDot = buildAnnotatedString {
                if (dotProgress <= 0f) {
                    withStyle(SpanStyle(color = dimColor)) {
                        append("●")
                    }
                } else if (isLit) {
                    withStyle(SpanStyle(color = activePrimaryColor)) {
                        append("●")
                    }
                } else {
                    val leftStop = (dotProgress - 0.18f).coerceAtLeast(0f)
                    val rightStop = (dotProgress + 0.18f).coerceAtMost(1f)
                    withStyle(
                        SpanStyle(
                            brush = Brush.horizontalGradient(
                                0.0f to activePrimaryColor,
                                leftStop to activeSecondaryColor,
                                rightStop to dimColor,
                                1.0f to dimColor
                            )
                        )
                    ) {
                        append("●")
                    }
                }
            }

            // 正弦呼吸相位 + 点亮瞬间的弹性波峰，与 KRC 歌词单字上浮缩放完全同构
            val phaseSin = sin(breathPhase - i * 0.92f)
            val breathWave = (phaseSin + 1f) * 0.5f
            val activeArc = if (isLighting) sin(dotProgress * PI.toFloat()) else 0f
            val glowStrength = (activeArc * 0.75f + dotProgress * (0.35f + 0.55f * breathWave)).coerceIn(0f, 1f)

            Text(
                text = annotatedDot,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = dotFontSizeSp.sp,
                    lineHeight = (dotFontSizeSp + 4f).sp,
                    shadow = if (glowStrength > 0.04f) {
                        Shadow(
                            color = activePrimaryColor.copy(alpha = (0.82f * glowStrength).coerceIn(0f, 1f)),
                            offset = Offset.Zero,
                            blurRadius = (18f * glowStrength + 5f).coerceAtLeast(1f)
                        )
                    } else null
                ),
                modifier = Modifier.graphicsLayer {
                    val floatWave = (0.35f + 0.65f * dotProgress) * phaseSin
                    translationY = -3.2f * activeArc - 2.2f * floatWave
                    val s = 0.90f + 0.14f * dotProgress + 0.10f * activeArc + 0.06f * breathWave * dotProgress
                    scaleX = s
                    scaleY = s
                }
            )
        }
    }
}

/**
 * 歌词页右侧悬浮无边框磨砂胶囊按钮（已彻底移除 `.border(...)` 边框线，采用柔和磨砂填充）
 */
@Composable
private fun LyricRightDockGlassButton(
    active: Boolean,
    activeColor: Color,
    buttonSize: androidx.compose.ui.unit.Dp = 42.dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) activeColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.11f),
        animationSpec = tween(200),
        label = "lyricDockGlassBg"
    )
    Surface(
        shape = RoundedCornerShape(if (buttonSize >= 46.dp) 18.dp else 16.dp),
        color = bgColor,
        contentColor = Color.White,
        modifier = Modifier
            .size(buttonSize)
            .softClickable(
                pressScale = 0.86f,
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * 零死区无极丝滑水平进度手势引擎 (Zero-Slop Stepless Scrubbing Gesture):
 * - 使用单一 pointerInput(awaitEachGesture) 统一处理点按直达与水平无极微调滑动，消除双 pointerInput 竞争；
 * - 按下瞬间 (awaitFirstDown) 立即进入交互态，绕过系统 18px touchSlop 死区，实现 120Hz 亚像素跟手滑动。
 */
internal fun Modifier.steplessHorizontalScrubGesture(
    onScrubStart: (Float) -> Unit,
    onScrubMove: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    onScrubCancel: () -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val widthPx = size.width.toFloat().coerceAtLeast(1f)
        val initialFraction = (down.position.x / widthPx).coerceIn(0f, 1f)
        onScrubStart(initialFraction)

        var pointerId = down.id
        var finishedNormally = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
                ?: event.changes.firstOrNull()
                ?: break
            pointerId = change.id
            if (!change.pressed) {
                change.consume()
                finishedNormally = true
                break
            }
            val nextFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
            change.consume()
            onScrubMove(nextFraction)
        }
        if (finishedNormally) {
            onScrubEnd()
        } else {
            onScrubCancel()
        }
    }
}

/**
 * 歌词页专属底部极简控播栏（已去除播放按钮的 `.border(...)` 边框线，并采用圆润饱满自定义播放/暂停矢量图形）
 */
@Composable
private fun LyricPageMinimalBottomBar(
    currentPositionMs: Long,
    positionProvider: () -> Long = { currentPositionMs },
    durationMs: Long,
    isPlaying: Boolean,
    accentColor: Color,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestOnSeekTo by rememberUpdatedState(onSeekTo)
    var settledSeekTargetMs by remember { mutableLongStateOf(-1L) }
    var settledSeekTimestampMs by remember { mutableLongStateOf(0L) }

    fun sampleWithSeekHold(): Long {
        val rawPos = positionProvider()
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        return if (settledSeekTargetMs >= 0L && nowElapsed - settledSeekTimestampMs in 0L..550L) {
            if (kotlin.math.abs(rawPos - settledSeekTargetMs) <= 800L) {
                settledSeekTargetMs = -1L
                rawPos
            } else {
                settledSeekTargetMs
            }
        } else {
            settledSeekTargetMs = -1L
            rawPos
        }
    }

    var sampledPositionMs by remember { mutableLongStateOf(sampleWithSeekHold()) }

    LaunchedEffect(currentPositionMs) {
        if (!isDragging) {
            sampledPositionMs = sampleWithSeekHold()
        }
    }
    val isLifecycleStarted = rememberIsLifecycleStarted()
    LaunchedEffect(isPlaying, isLifecycleStarted, isDragging, positionProvider) {
        if (isPlaying && isLifecycleStarted && !isDragging) {
            while (isActive) {
                sampledPositionMs = sampleWithSeekHold()
                kotlinx.coroutines.delay(66L)
            }
        } else if (!isDragging) {
            sampledPositionMs = sampleWithSeekHold()
        }
    }

    val livePositionMs = sampledPositionMs
    val currentFraction = if (durationMs > 0L) {
        (livePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val effectiveFraction = if (isDragging) dragFraction else currentFraction
    val displayTimeMs = if (isDragging) (dragFraction * durationMs).toLong() else livePositionMs

    val railHeightDp by animateDpAsState(
        targetValue = if (isDragging) 7.dp else 3.5.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "lyricSilkRailHeight"
    )
    val cursorWidthDp by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 9.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "lyricSilkCursorW"
    )
    val cursorHeightDp by animateDpAsState(
        targetValue = if (isDragging) 9.dp else 5.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "lyricSilkCursorH"
    )
    val morphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "lyricBarPlayPauseMorph"
    )

    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：无边框柔光圆润播放/暂停按钮（彻底移除 border 线条）
        Surface(
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.26f),
            contentColor = Color.White,
            modifier = Modifier
                .size(if (isLargeScreen) 52.dp else 46.dp)
                .softClickable(
                    pressScale = 0.86f,
                    onClick = onTogglePlay
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                val safeMorph = morphProgress.coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .size(if (isLargeScreen) 23.dp else 20.dp)
                        .graphicsLayer {
                            val bounce = (1f - 0.08f * sin(safeMorph * PI.toFloat())).coerceIn(0.88f, 1.10f)
                            scaleX = bounce
                            scaleY = bounce
                        }
                ) {
                    drawContinuousPlayPauseMorph(
                        morphProgress = safeMorph,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(displayTimeMs),
                    color = if (isDragging) accentColor.readableOnDarkSurface(0.46f) else Color.White.copy(alpha = 0.56f),
                    fontSize = 10.5.sp,
                    fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = formatDuration(durationMs),
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .steplessHorizontalScrubGesture(
                        onScrubStart = { startF ->
                            isDragging = true
                            dragFraction = startF
                        },
                        onScrubMove = { moveF ->
                            dragFraction = moveF
                        },
                        onScrubEnd = {
                            val targetMs = (dragFraction * latestDurationMs).toLong().coerceIn(0L, latestDurationMs.coerceAtLeast(0L))
                            settledSeekTargetMs = targetMs
                            settledSeekTimestampMs = android.os.SystemClock.elapsedRealtime()
                            sampledPositionMs = targetMs
                            latestOnSeekTo(targetMs)
                            isDragging = false
                        },
                        onScrubCancel = {
                            isDragging = false
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    if (w <= 2f || h <= 2f) return@Canvas
                    val cy = h / 2f
                    val railH = railHeightDp.toPx().coerceIn(1f, h)
                    val r = CornerRadius(railH / 2f, railH / 2f)

                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.14f),
                        topLeft = Offset(0f, cy - railH / 2f),
                        size = Size(w, railH),
                        cornerRadius = r
                    )

                    val activeW = (w * effectiveFraction.coerceIn(0f, 1f)).coerceIn(0f, w)
                    if (activeW > 1f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.45f),
                                    accentColor,
                                    Color.White
                                ),
                                startX = 0f,
                                endX = activeW.coerceAtLeast(1f)
                            ),
                            topLeft = Offset(0f, cy - railH / 2f),
                            size = Size(activeW, railH),
                            cornerRadius = r
                        )
                    }

                    val cursorW = cursorWidthDp.toPx()
                    val cursorH = cursorHeightDp.toPx()
                    val cursorLeft = (activeW - cursorW / 2f).coerceIn(0f, (w - cursorW).coerceAtLeast(0f))
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(cursorLeft, cy - cursorH / 2f),
                        size = Size(cursorW, cursorH),
                        cornerRadius = CornerRadius(cursorH / 2f, cursorH / 2f)
                    )
                }
            }
        }
    }
}


internal fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

internal fun detectAudioOutputRouteLabel(context: Context): String {
    return runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "内置扬声器"
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        val usb = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        val wired = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        when {
            bt != null -> {
                val name = bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "蓝牙音频"
                "蓝牙 · ${name.take(12)}"
            }
            usb != null -> "USB DAC · 高解析独占"
            wired != null -> "Hi-Fi 有线耳机"
            else -> "立体声扬声器"
        }
    }.getOrDefault("立体声扬声器")
}
