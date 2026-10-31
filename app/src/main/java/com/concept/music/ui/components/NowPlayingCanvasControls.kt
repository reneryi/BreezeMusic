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
@Composable
internal fun RedesignedCommentCountButton(
    commentCount: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    val countText = formatPlayerCommentCount(commentCount)
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Box(
        modifier = Modifier
            .height(if (isLargeScreen) 54.dp else 44.dp)
            .widthIn(min = if (isLargeScreen) 64.dp else 52.dp)
            .softClickable(
                pressScale = 0.86f,
                pressAlpha = 0.78f,
                onClick = onClick
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // 气泡主图标偏左下放置，为右上角数字角标留出独立无遮挡空间
        Canvas(
            modifier = Modifier
                .size(if (isLargeScreen) 28.dp else 23.dp)
                .align(if (countText.isNotEmpty()) Alignment.CenterStart else Alignment.Center)
                .offset(y = if (countText.isNotEmpty()) 2.dp else 0.dp)
        ) {
            val w = size.width
            val h = size.height
            val stroke = (if (isLargeScreen) 2.4.dp else 2.1.dp).toPx()
            val c = Color.White.copy(alpha = 0.90f)
            val bubblePath = Path().apply {
                moveTo(w * 0.20f, h * 0.22f)
                lineTo(w * 0.80f, h * 0.22f)
                quadraticBezierTo(w * 0.90f, h * 0.22f, w * 0.90f, h * 0.36f)
                lineTo(w * 0.90f, h * 0.65f)
                quadraticBezierTo(w * 0.90f, h * 0.77f, w * 0.78f, h * 0.77f)
                lineTo(w * 0.44f, h * 0.77f)
                lineTo(w * 0.24f, h * 0.91f)
                lineTo(w * 0.26f, h * 0.77f)
                lineTo(w * 0.20f, h * 0.77f)
                quadraticBezierTo(w * 0.10f, h * 0.77f, w * 0.10f, h * 0.65f)
                lineTo(w * 0.10f, h * 0.36f)
                quadraticBezierTo(w * 0.10f, h * 0.22f, w * 0.20f, h * 0.22f)
                close()
            }
            drawPath(
                path = bubblePath,
                color = c,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            val dotR = (if (isLargeScreen) 1.8.dp else 1.5.dp).toPx()
            drawCircle(accentColor, radius = dotR, center = Offset(w * 0.36f, h * 0.49f))
            drawCircle(c, radius = dotR, center = Offset(w * 0.50f, h * 0.49f))
            drawCircle(c, radius = dotR, center = Offset(w * 0.64f, h * 0.49f))
        }

        // 右上角悬浮无遮挡角标（不遮盖评论气泡主体，也不被父容器裁切）
        if (countText.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(if (isLargeScreen) 8.dp else 7.dp),
                color = accentColor.copy(alpha = 0.24f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = if (isLargeScreen) 21.dp else 17.dp, top = 2.dp)
            ) {
                Text(
                    text = countText,
                    color = Color.White,
                    fontSize = if (isLargeScreen) 10.5.sp else 9.sp,
                    lineHeight = if (isLargeScreen) 12.sp else 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                )
            }
        }
    }
}

@Composable
internal fun PlumpLandscapeModeCanvasIcon(
    color: Color,
    accentColor: Color,
    iconSize: androidx.compose.ui.unit.Dp = if (com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice) 29.dp else 24.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx() * (w / 24.dp.toPx()).coerceIn(0.85f, 1.35f)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.10f, h * 0.24f),
            size = Size(w * 0.80f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(accentColor, Offset(w * 0.26f, h * 0.58f), Offset(w * 0.42f, h * 0.40f), stroke * 0.9f, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.26f, h * 0.58f), Offset(w * 0.40f, h * 0.58f), stroke * 0.9f, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.26f, h * 0.58f), Offset(w * 0.26f, h * 0.44f), stroke * 0.9f, StrokeCap.Round)

        drawLine(color, Offset(w * 0.74f, h * 0.42f), Offset(w * 0.58f, h * 0.60f), stroke * 0.9f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.74f, h * 0.42f), Offset(w * 0.60f, h * 0.42f), stroke * 0.9f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.74f, h * 0.42f), Offset(w * 0.74f, h * 0.56f), stroke * 0.9f, StrokeCap.Round)
    }
}

@Composable
internal fun PlayerActionIconButton(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Box(
        modifier = Modifier
            .size(if (isLargeScreen) 54.dp else 44.dp)
            .clip(CircleShape)
            .softClickable(
                pressScale = 0.85f,
                pressAlpha = 0.78f,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}


/**
 * 自然光学彗星拖尾进度条 (CometTailProgressBar):
 * - 彗星外层羽化光流与内层晶核光束均采用「前端半圆包络 (Rounded Teardrop Envelope)」平滑包裹圆点中心，
 *   使拖尾从圆核内部自然向后渐隐收拢，彻底消除原直径处的垂直切线。
 */
@Composable
internal fun CometTailProgressBar(
    fraction: Float,
    isPlaying: Boolean,
    isInteracting: Boolean,
    currentTimeText: String,
    totalTimeText: String,
    climaxStartFrac: Float,
    climaxEndFrac: Float,
    highlightClimaxRange: Boolean,
    accentPrimary: Color = ConceptPrimary,
    accentSecondary: Color = ConceptCyanAccent,
    compact: Boolean = false,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit = onDragEnd
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val trackHeightDp by animateDpAsState(
        targetValue = if (compact) {
            if (isInteracting) 5.8.dp else 3.4.dp
        } else {
            if (isInteracting) 8.0.dp else 4.8.dp
        },
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cometTrackHeight"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isInteracting) 1.32f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cometThumbScale"
    )

    val cometPulse: Float
    val stardustPhase: Float
    if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "cometOpticalFlow")
        val animatedPulse by infiniteTransition.animateFloat(
            initialValue = 0.90f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cometBreath"
        )
        val animatedStardust by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "stardustPhase"
        )
        cometPulse = animatedPulse
        stardustPhase = animatedStardust
    } else {
        cometPulse = 1.0f
        stardustPhase = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 22.dp else 40.dp)
            .steplessHorizontalScrubGesture(
                onScrubStart = { startF -> currentOnDragStart(startF) },
                onScrubMove = { moveF -> currentOnDrag(moveF) },
                onScrubEnd = { currentOnDragEnd() },
                onScrubCancel = { currentOnDragCancel() }
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isInteracting,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xE6141E30),
                contentColor = Color.White
            ) {
                Text(
                    text = "$currentTimeText / $totalTimeText",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(if (compact) 18.dp else 32.dp)
        ) {
            val w = size.width
            val h = size.height
            if (w <= 2f || h <= 2f) return@Canvas
            val centerY = h / 2f
            val barH = trackHeightDp.toPx().coerceIn(2f, h)
            val barRadius = CornerRadius(barH / 2f, barH / 2f)
            val topY = centerY - barH / 2f

            drawRoundRect(
                color = Color.White.copy(alpha = if (isInteracting) 0.22f else 0.15f),
                topLeft = Offset(0f, topY),
                size = Size(w, barH),
                cornerRadius = barRadius
            )

            if (climaxEndFrac > climaxStartFrac) {
                val cStart = (w * climaxStartFrac).coerceIn(0f, w)
                val cWidth = (w * (climaxEndFrac - climaxStartFrac)).coerceIn(4f, (w - cStart).coerceAtLeast(4f))
                if (highlightClimaxRange) {
                    drawRoundRect(
                        color = VipGold.copy(alpha = 0.24f),
                        topLeft = Offset(cStart, centerY - barH * 1.10f),
                        size = Size(cWidth, barH * 2.20f),
                        cornerRadius = CornerRadius(barH, barH)
                    )
                }
                drawRoundRect(
                    color = VipGold.copy(alpha = if (highlightClimaxRange) 0.86f else 0.48f),
                    topLeft = Offset(cStart, topY),
                    size = Size(cWidth, barH),
                    cornerRadius = barRadius
                )
            }

            val headX = (w * fraction.coerceIn(0f, 1f)).coerceIn(0f, w)
            val headCenter = Offset(headX, centerY)
            val thumbRadius = (if (compact) 3.8.dp else 5.0.dp).toPx() * thumbScale
            val pulseFactor = (if (isPlaying) cometPulse else 1f).coerceIn(0.88f, 1.16f)

            if (headX > 1f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accentPrimary.copy(alpha = 0.38f),
                            accentPrimary.copy(alpha = 0.85f),
                            lerp(accentPrimary, accentSecondary, 0.75f)
                        ),
                        startX = 0f,
                        endX = headX
                    ),
                    topLeft = Offset(0f, topY),
                    size = Size(headX, barH),
                    cornerRadius = barRadius
                )
            }

            // 自然水滴包络式彗星拖尾 (Seamless Teardrop Comet Wake)
            val maxTailLen = min(headX, 118.dp.toPx())
            if (maxTailLen > 4f) {
                val tailStartX = (headX - maxTailLen).coerceAtLeast(0f)
                val outerCapR = (thumbRadius * 1.38f * pulseFactor).coerceAtLeast(barH * 0.9f)
                val innerCapR = (thumbRadius * 0.82f).coerceAtLeast(barH * 0.55f)

                val outerTeardropPath = Path().apply {
                    moveTo(tailStartX, centerY)
                    cubicTo(
                        tailStartX + maxTailLen * 0.45f, centerY - outerCapR * 0.15f,
                        tailStartX + maxTailLen * 0.82f, centerY - outerCapR,
                        headX, centerY - outerCapR
                    )
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = headX - outerCapR,
                            top = centerY - outerCapR,
                            right = headX + outerCapR,
                            bottom = centerY + outerCapR
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    cubicTo(
                        tailStartX + maxTailLen * 0.82f, centerY + outerCapR,
                        tailStartX + maxTailLen * 0.45f, centerY + outerCapR * 0.15f,
                        tailStartX, centerY
                    )
                    close()
                }
                drawPath(
                    path = outerTeardropPath,
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.38f to accentPrimary.copy(alpha = 0.12f),
                            0.76f to accentSecondary.copy(alpha = 0.34f),
                            0.94f to Color.White.copy(alpha = 0.46f),
                            1.00f to Color.White.copy(alpha = 0.25f)
                        ),
                        startX = tailStartX,
                        endX = (headX + outerCapR).coerceAtLeast(tailStartX + 2f)
                    )
                )

                val coreStart = tailStartX + maxTailLen * 0.20f
                val innerTeardropPath = Path().apply {
                    moveTo(coreStart, centerY)
                    cubicTo(
                        tailStartX + maxTailLen * 0.58f, centerY - innerCapR * 0.22f,
                        tailStartX + maxTailLen * 0.86f, centerY - innerCapR,
                        headX, centerY - innerCapR
                    )
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = headX - innerCapR,
                            top = centerY - innerCapR,
                            right = headX + innerCapR,
                            bottom = centerY + innerCapR
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    cubicTo(
                        tailStartX + maxTailLen * 0.86f, centerY + innerCapR,
                        tailStartX + maxTailLen * 0.58f, centerY + innerCapR * 0.22f,
                        coreStart, centerY
                    )
                    close()
                }
                drawPath(
                    path = innerTeardropPath,
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.42f to accentPrimary.copy(alpha = 0.45f),
                            0.82f to lerp(accentSecondary, Color.White, 0.55f).copy(alpha = 0.88f),
                            1.00f to Color.White
                        ),
                        startX = coreStart,
                        endX = (headX + innerCapR).coerceAtLeast(coreStart + 2f)
                    )
                )

                for (i in 0 until 5) {
                    val pNorm = (stardustPhase + i * 0.20f) % 1f
                    val distRatio = 1f - pNorm
                    val px = tailStartX + maxTailLen * (0.24f + 0.70f * distRatio)
                    val spread = distRatio * outerCapR * 0.55f
                    val dirSign = if (i % 2 == 0) 1f else -1f
                    val py = centerY + dirSign * sin(pNorm * PI.toFloat() * 2.0f + i) * spread
                    val pAlpha = (sin(distRatio * PI.toFloat()) * 0.78f).coerceIn(0f, 0.82f)
                    val pRadius = (0.8f + 1.2f * distRatio).dp.toPx()
                    drawCircle(
                        color = if (i % 2 == 0) Color.White.copy(alpha = pAlpha) else accentSecondary.copy(alpha = pAlpha),
                        radius = pRadius,
                        center = Offset(px, py)
                    )
                }
            }

            // 彗星晶核头部：多层同心柔光晕 + 融合炽白核心
            val haloRadius = (if (compact) 9.5.dp else 13.5.dp).toPx() * thumbScale * pulseFactor
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White,
                        0.26f to Color.White.copy(alpha = 0.92f),
                        0.52f to lerp(accentSecondary, Color.White, 0.45f).copy(alpha = 0.58f),
                        0.78f to accentPrimary.copy(alpha = 0.24f),
                        1.00f to Color.Transparent
                    ),
                    center = headCenter,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = headCenter
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        Color.White,
                        lerp(accentSecondary, Color.White, 0.65f)
                    ),
                    center = headCenter,
                    radius = thumbRadius
                ),
                radius = thumbRadius,
                center = headCenter
            )
        }
    }
}

internal fun createPlumpPlayTrianglePath(
    centerX: Float,
    centerY: Float,
    size: Float
): Path {
    val halfH = size * 0.50f
    val leftX = centerX - size * 0.42f
    val rightX = centerX + size * 0.48f
    val topY = centerY - halfH
    val bottomY = centerY + halfH
    val corner = size * 0.19f

    val p1 = Offset(leftX, topY)
    val p2 = Offset(rightX, centerY)
    val p3 = Offset(leftX, bottomY)

    fun moveAlong(from: Offset, to: Offset, dist: Float): Offset {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val r = (dist / len).coerceIn(0f, 0.45f)
        return Offset(from.x + dx * r, from.y + dy * r)
    }

    val p1a = moveAlong(p1, p3, corner)
    val p1b = moveAlong(p1, p2, corner)
    val p2a = moveAlong(p2, p1, corner)
    val p2b = moveAlong(p2, p3, corner)
    val p3a = moveAlong(p3, p2, corner)
    val p3b = moveAlong(p3, p1, corner)

    return Path().apply {
        moveTo(p1b.x, p1b.y)
        lineTo(p2a.x, p2a.y)
        quadraticBezierTo(p2.x, p2.y, p2b.x, p2b.y)
        lineTo(p3a.x, p3a.y)
        quadraticBezierTo(p3.x, p3.y, p3b.x, p3b.y)
        lineTo(p1a.x, p1a.y)
        quadraticBezierTo(p1.x, p1.y, p1b.x, p1b.y)
        close()
    }
}

/**
 * 连续几何插值播放/暂停形变绘制 (True Geometric Play/Pause Morph):
 * - 彻底淘汰 `morphProgress > 0.5f` 瞬间硬切；
 * - `morphProgress = 0f`（暂停态，显示饱满圆角播放三角） <-> `morphProgress = 1f`（播放态，分裂并延展为双圆角暂停竖柱）；
 * - 通过左右两组圆润四边几何路径连续线性插值，实现 120fps 丝滑无缝形变。
 */
internal fun DrawScope.drawContinuousPlayPauseMorph(
    morphProgress: Float,
    color: Color
) {
    val w = size.width
    val h = size.height
    if (w <= 2f || h <= 2f) return
    val p = morphProgress.coerceIn(0f, 1f)

    if (p <= 0.02f) {
        val triPath = createPlumpPlayTrianglePath(
            centerX = w * 0.54f,
            centerY = h * 0.50f,
            size = min(w, h) * 0.84f
        )
        drawPath(path = triPath, color = color)
        return
    }
    if (p >= 0.98f) {
        val barW = w * 0.24f
        val barH = h * 0.72f
        val topY = (h - barH) / 2f
        val leftX = w * 0.18f
        val rightX = w - leftX - barW
        val radius = CornerRadius(barW / 2f, barW / 2f)
        drawRoundRect(
            color = color,
            topLeft = Offset(leftX, topY),
            size = Size(barW, barH),
            cornerRadius = radius
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(rightX, topY),
            size = Size(barW, barH),
            cornerRadius = radius
        )
        return
    }

    fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    // 左半路径：从三角左半梯形平滑过渡到左暂停竖条
    val lTopLeftX = lerpF(w * 0.19f, w * 0.18f, p)
    val lTopLeftY = lerpF(h * 0.14f, h * 0.14f, p)
    val lTopRightX = lerpF(w * 0.53f, w * 0.42f, p)
    val lTopRightY = lerpF(h * 0.32f, h * 0.14f, p)
    val lBotRightX = lerpF(w * 0.53f, w * 0.42f, p)
    val lBotRightY = lerpF(h * 0.68f, h * 0.86f, p)
    val lBotLeftX = lerpF(w * 0.19f, w * 0.18f, p)
    val lBotLeftY = lerpF(h * 0.86f, h * 0.86f, p)

    // 右半路径：从三角右尖端平滑分裂延展为右暂停竖条
    val gapProgress = ((p - 0.10f) / 0.90f).coerceIn(0f, 1f)
    val rTopLeftX = lerpF(w * 0.51f, w * 0.58f, gapProgress)
    val rTopLeftY = lerpF(h * 0.31f, h * 0.14f, p)
    val rTopRightX = lerpF(w * 0.87f, w * 0.82f, p)
    val rTopRightY = lerpF(h * 0.50f, h * 0.14f, p)
    val rBotRightX = lerpF(w * 0.87f, w * 0.82f, p)
    val rBotRightY = lerpF(h * 0.50f, h * 0.86f, p)
    val rBotLeftX = lerpF(w * 0.51f, w * 0.58f, gapProgress)
    val rBotLeftY = lerpF(h * 0.69f, h * 0.86f, p)

    val strokeJoinRadius = min(w, h) * 0.11f

    val leftMorphPath = Path().apply {
        moveTo(lTopLeftX, lTopLeftY)
        lineTo(lTopRightX, lTopRightY)
        lineTo(lBotRightX, lBotRightY)
        lineTo(lBotLeftX, lBotLeftY)
        close()
    }
    val rightMorphPath = Path().apply {
        moveTo(rTopLeftX, rTopLeftY)
        lineTo(rTopRightX, rTopRightY)
        lineTo(rBotRightX, rBotRightY)
        lineTo(rBotLeftX, rBotLeftY)
        close()
    }

    drawPath(path = leftMorphPath, color = color)
    drawPath(
        path = leftMorphPath,
        color = color,
        style = Stroke(width = strokeJoinRadius, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(path = rightMorphPath, color = color)
    drawPath(
        path = rightMorphPath,
        color = color,
        style = Stroke(width = strokeJoinRadius, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

// =========================================================================================
// 圆润饱满重绘图标组件库 (Plump & Rounded Custom Canvas Icons)
// =========================================================================================

@Composable
internal fun PlumpHeartCanvasIcon(
    isFavorite: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.85f)
            cubicTo(w * 0.10f, h * 0.60f, w * 0.06f, h * 0.25f, w * 0.28f, h * 0.16f)
            cubicTo(w * 0.42f, h * 0.10f, w * 0.50f, h * 0.22f, w * 0.50f, h * 0.28f)
            cubicTo(w * 0.50f, h * 0.22f, w * 0.58f, h * 0.10f, w * 0.72f, h * 0.16f)
            cubicTo(w * 0.94f, h * 0.25f, w * 0.90f, h * 0.60f, w * 0.50f, h * 0.85f)
            close()
        }
        if (isFavorite) {
            drawPath(path = path, color = color)
        } else {
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
internal fun PlumpPosterSparkleCanvasIcon(
    color: Color,
    accentColor: Color,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx() * (w / 22.dp.toPx()).coerceIn(0.85f, 1.35f)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            cornerRadius = CornerRadius(w * 0.24f, w * 0.24f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(w * 0.28f, h * 0.44f), Offset(w * 0.68f, h * 0.44f), stroke, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.28f, h * 0.62f), Offset(w * 0.54f, h * 0.62f), stroke, StrokeCap.Round)
        drawCircle(accentColor, radius = w * 0.08f, center = Offset(w * 0.68f, h * 0.28f))
    }
}

@Composable
internal fun PlumpPlaylistAddCanvasIcon(
    color: Color,
    accentColor: Color,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx() * (w / 24.dp.toPx()).coerceIn(0.85f, 1.35f)
        drawLine(color, Offset(w * 0.14f, h * 0.26f), Offset(w * 0.78f, h * 0.26f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.56f, h * 0.50f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.74f), Offset(w * 0.46f, h * 0.74f), stroke, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.74f, h * 0.52f), Offset(w * 0.74f, h * 0.84f), stroke, StrokeCap.Round)
        drawLine(accentColor, Offset(w * 0.58f, h * 0.68f), Offset(w * 0.90f, h * 0.68f), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun PlumpAudioWaveCanvasIcon(
    color: Color,
    accentColor: Color,
    iconSize: androidx.compose.ui.unit.Dp = if (com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice) 29.dp else 24.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val pillW = w * 0.13f
        val xs = listOf(0.15f, 0.34f, 0.53f, 0.72f)
        val hs = listOf(0.36f, 0.76f, 0.56f, 0.36f)
        xs.forEachIndexed { idx, xr ->
            val bh = h * hs[idx]
            val top = (h - bh) / 2f
            drawRoundRect(
                color = if (idx == 1) accentColor else color,
                topLeft = Offset(w * xr, top),
                size = Size(pillW, bh),
                cornerRadius = CornerRadius(pillW / 2f, pillW / 2f)
            )
        }
    }
}

@Composable
internal fun PlumpDesktopLyricCanvasIcon(
    enabled: Boolean,
    color: Color,
    accentColor: Color,
    iconSize: androidx.compose.ui.unit.Dp = if (com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice) 29.dp else 24.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 2.3.dp.toPx() * (w / 24.dp.toPx()).coerceIn(0.85f, 1.35f)
        if (enabled) {
            drawRoundRect(
                color = color.copy(alpha = 0.20f),
                topLeft = Offset(w * 0.12f, h * 0.18f),
                size = Size(w * 0.76f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
            )
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.18f),
            size = Size(w * 0.76f, h * 0.64f),
            cornerRadius = CornerRadius(w * 0.22f, w * 0.22f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(w * 0.28f, h * 0.42f), Offset(w * 0.72f, h * 0.42f), stroke, StrokeCap.Round)
        drawLine(if (enabled) accentColor else color, Offset(w * 0.28f, h * 0.60f), Offset(w * 0.56f, h * 0.60f), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun PlumpMoreDotsCanvasIcon(
    color: Color,
    iconSize: androidx.compose.ui.unit.Dp = if (com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice) 29.dp else 24.dp
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val r = w * 0.088f
        drawCircle(color, radius = r, center = Offset(w * 0.23f, h * 0.5f))
        drawCircle(color, radius = r, center = Offset(w * 0.50f, h * 0.5f))
        drawCircle(color, radius = r, center = Offset(w * 0.77f, h * 0.5f))
    }
}

/**
 * 重绘控件 1：圆润饱满播放模式切换按钮
 */
@Composable
internal fun AnimatedPlayModeButton(
    playMode: PlayMode,
    accentColor: Color = ConceptCyanAccent,
    onClick: () -> Unit
) {
    var clickCount by remember { mutableIntStateOf(0) }
    val spinDeg by animateFloatAsState(
        targetValue = clickCount * 180f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playModeSpin"
    )
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice

    Box(
        modifier = Modifier
            .size(if (isLargeScreen) 56.dp else 46.dp)
            .clip(CircleShape)
            .softClickable(
                pressScale = 0.84f,
                onClick = {
                    clickCount++
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = playMode,
            transitionSpec = {
                (scaleIn(initialScale = 0.65f, animationSpec = spring(dampingRatio = 0.65f)) +
                    fadeIn(tween(160))) togetherWith
                    (scaleOut(targetScale = 0.65f, animationSpec = tween(130)) +
                        fadeOut(tween(120)))
            },
            label = "playModeMorph"
        ) { mode ->
            Canvas(
                modifier = Modifier
                    .size(if (isLargeScreen) 30.dp else 25.dp)
                    .graphicsLayer {
                        rotationZ = if (mode == PlayMode.REPEAT_ALL) spinDeg % 360f else 0f
                    }
            ) {
                val w = size.width
                val h = size.height
                val stroke = (if (isLargeScreen) 2.7.dp else 2.4.dp).toPx()
                val tint = Color.White.copy(alpha = 0.90f)

                when (mode) {
                    PlayMode.REPEAT_ALL -> {
                        val pad = w * 0.14f
                        val rectSize = Size(w - pad * 2, h - pad * 2)
                        drawArc(
                            color = tint,
                            startAngle = -155f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = rectSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = tint,
                            startAngle = 25f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = rectSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        val topTip = Offset(w * 0.78f, h * 0.26f)
                        drawLine(tint, topTip, Offset(topTip.x - 4.2.dp.toPx(), topTip.y - 1.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(tint, topTip, Offset(topTip.x - 1.dp.toPx(), topTip.y + 4.2.dp.toPx()), stroke, StrokeCap.Round)

                        val botTip = Offset(w * 0.22f, h * 0.74f)
                        drawLine(tint, botTip, Offset(botTip.x + 4.2.dp.toPx(), botTip.y + 1.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(tint, botTip, Offset(botTip.x + 1.dp.toPx(), botTip.y - 4.2.dp.toPx()), stroke, StrokeCap.Round)
                    }

                    PlayMode.REPEAT_ONE -> {
                        val pad = w * 0.14f
                        val rectSize = Size(w - pad * 2, h - pad * 2)
                        drawArc(
                            color = accentColor,
                            startAngle = -155f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = rectSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = accentColor,
                            startAngle = 25f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = rectSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(w * 0.50f, h * 0.34f),
                            end = Offset(w * 0.50f, h * 0.66f),
                            strokeWidth = stroke * 1.05f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(w * 0.41f, h * 0.41f),
                            end = Offset(w * 0.50f, h * 0.34f),
                            strokeWidth = stroke * 0.95f,
                            cap = StrokeCap.Round
                        )
                    }

                    PlayMode.SHUFFLE -> {
                        val path1 = Path().apply {
                            moveTo(w * 0.15f, h * 0.32f)
                            cubicTo(w * 0.45f, h * 0.32f, w * 0.55f, h * 0.68f, w * 0.85f, h * 0.68f)
                        }
                        val path2 = Path().apply {
                            moveTo(w * 0.15f, h * 0.68f)
                            cubicTo(w * 0.45f, h * 0.68f, w * 0.55f, h * 0.32f, w * 0.85f, h * 0.32f)
                        }
                        drawPath(path1, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
                        drawPath(path2, accentColor, style = Stroke(width = stroke, cap = StrokeCap.Round))

                        val tipTop = Offset(w * 0.86f, h * 0.32f)
                        drawLine(accentColor, tipTop, Offset(tipTop.x - 3.8.dp.toPx(), tipTop.y - 3.2.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(accentColor, tipTop, Offset(tipTop.x - 3.8.dp.toPx(), tipTop.y + 3.2.dp.toPx()), stroke, StrokeCap.Round)

                        val tipBot = Offset(w * 0.86f, h * 0.68f)
                        drawLine(tint, tipBot, Offset(tipBot.x - 3.8.dp.toPx(), tipBot.y - 3.2.dp.toPx()), stroke, StrokeCap.Round)
                        drawLine(tint, tipBot, Offset(tipBot.x - 3.8.dp.toPx(), tipBot.y + 3.2.dp.toPx()), stroke, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

/**
 * 重绘控件 2 & 4：圆润饱满上一首 / 下一首按钮（饱满大圆角三角 + 圆润胶囊竖柱）
 */
@Composable
internal fun RedrawnSkipButton(
    isNext: Boolean,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val nudgeX = remember { Animatable(0f) }
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice

    Box(
        modifier = Modifier
            .size(if (isLargeScreen) 62.dp else 52.dp)
            .clip(CircleShape)
            .graphicsLayer {
                translationX = nudgeX.value
            }
            .softClickable(
                pressScale = 0.86f,
                onClick = {
                    scope.launch {
                        val target = if (isNext) 12f else -12f
                        nudgeX.animateTo(target, tween(85))
                        nudgeX.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
                    }
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(if (isLargeScreen) 36.dp else 30.dp)) {
            val w = size.width
            val h = size.height
            val barW = w * 0.145f
            val barH = h * 0.60f
            val barTop = (h - barH) / 2f

            if (isNext) {
                val triPath = createPlumpPlayTrianglePath(
                    centerX = w * 0.40f,
                    centerY = h * 0.50f,
                    size = min(w, h) * 0.66f
                )
                drawPath(path = triPath, color = Color.White)
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.72f, barTop),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                )
            } else {
                rotate(degrees = 180f, pivot = Offset(w / 2f, h / 2f)) {
                    val triPath = createPlumpPlayTrianglePath(
                        centerX = w * 0.40f,
                        centerY = h * 0.50f,
                        size = min(w, h) * 0.66f
                    )
                    drawPath(path = triPath, color = Color.White)
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(w * 0.72f, barTop),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
        }
    }
}

/**
 * 重绘控件 3：核心播放/暂停流体按钮 (AnimatedPlayPauseCenterButton)
 */
@Composable
internal fun AnimatedPlayPauseCenterButton(
    isPlaying: Boolean,
    accentPrimary: Color = ConceptPrimary,
    accentSecondary: Color = ConceptCyanAccent,
    onClick: () -> Unit
) {
    val morphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = ConceptMotion.expressiveSpring(),
        label = "playPauseMorph"
    )
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice

    val auraScale: Float = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "playButtonAura")
        val animatedAura by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "auraScale"
        )
        animatedAura
    } else {
        1.0f
    }

    Box(
        modifier = Modifier.size(if (isLargeScreen) 88.dp else 76.dp),
        contentAlignment = Alignment.Center
    ) {
        // 动态封面色彩呼吸光环
        Box(
            modifier = Modifier
                .size(if (isLargeScreen) 86.dp else 74.dp)
                .graphicsLayer {
                    scaleX = auraScale
                    scaleY = auraScale
                    alpha = if (isPlaying) 0.38f else 0.20f
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accentPrimary, accentSecondary.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )

        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 14.dp,
            modifier = Modifier
                .size(if (isLargeScreen) 76.dp else 66.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            lerp(accentPrimary, Color.White, 0.14f),
                            accentPrimary,
                            lerp(accentPrimary, accentSecondary, 0.72f)
                        )
                    )
                )
                .softClickable(
                    pressScale = 0.88f,
                    onClick = onClick
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                val safeMorph = morphProgress.coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .size(if (isLargeScreen) 33.dp else 28.dp)
                        .graphicsLayer {
                            val bounce = (1f - 0.08f * sin(safeMorph * PI.toFloat())).coerceIn(0.88f, 1.10f)
                            scaleX = bounce
                            scaleY = bounce
                            rotationZ = (1f - safeMorph) * -6f * sin(safeMorph * PI.toFloat())
                        }
                ) {
                    drawContinuousPlayPauseMorph(
                        morphProgress = safeMorph,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 重绘控件 5：圆润饱满播放列表/队列按钮
 */
@Composable
internal fun RedrawnQueueButton(
    accentColor: Color = ConceptCyanAccent,
    onClick: () -> Unit
) {
    val isLargeScreen = com.concept.music.ui.theme.LocalConceptAdaptiveInfo.current.isLargeScreenDevice
    Box(
        modifier = Modifier
            .size(if (isLargeScreen) 56.dp else 46.dp)
            .clip(CircleShape)
            .softClickable(
                pressScale = 0.84f,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(if (isLargeScreen) 30.dp else 25.dp)) {
            val w = size.width
            val h = size.height
            val stroke = (if (isLargeScreen) 2.8.dp else 2.5.dp).toPx()
            val tint = Color.White.copy(alpha = 0.90f)

            drawLine(tint, Offset(w * 0.14f, h * 0.26f), Offset(w * 0.78f, h * 0.26f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.62f, h * 0.50f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.14f, h * 0.74f), Offset(w * 0.48f, h * 0.74f), stroke, StrokeCap.Round)

            val noteCenter = Offset(w * 0.72f, h * 0.74f)
            drawCircle(accentColor, radius = (if (isLargeScreen) 4.0.dp else 3.4.dp).toPx(), center = noteCenter)
            drawLine(
                color = accentColor,
                start = Offset(noteCenter.x + 2.5.dp.toPx(), noteCenter.y),
                end = Offset(noteCenter.x + 2.5.dp.toPx(), h * 0.40f),
                strokeWidth = stroke * 0.95f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = accentColor,
                start = Offset(noteCenter.x + 2.5.dp.toPx(), h * 0.40f),
                end = Offset(w * 0.90f, h * 0.47f),
                strokeWidth = stroke * 0.95f,
                cap = StrokeCap.Round
            )
        }
    }
}

