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
internal fun MasterVinylTurntableView(
    coverUrl: String,
    isPlaying: Boolean,
    accentColor: Color,
    rotationProvider: () -> Float,
    scale: Float = 1f
) {
    val s = scale.coerceIn(0.50f, 1.60f)
    // 液压阻尼唱臂动画（Hydraulic Damped Tonearm）：
    // - 0f 为暂停态：唱臂垂直优雅停靠在右侧唱臂托架上，完全脱离黑胶盘面；
    // - 24f 为播放态：唱臂顺时针平稳旋入黑胶黄金声轨区；
    val HydraulicTonearmPlayEasing = remember { CubicBezierEasing(0.22f, 0.88f, 0.24f, 1.0f) }
    val HydraulicTonearmPauseEasing = remember { CubicBezierEasing(0.42f, 0.04f, 0.22f, 1.0f) }
    val armAngle by animateFloatAsState(
        targetValue = if (isPlaying) 24f else 0f,
        animationSpec = if (isPlaying) {
            tween(durationMillis = 640, easing = HydraulicTonearmPlayEasing)
        } else {
            tween(durationMillis = 1260, easing = HydraulicTonearmPauseEasing)
        },
        label = "masterTonearmAngle"
    )
    // 抬臂高度状态：暂停移开时先柔顺抬起唱臂（投影拉远柔化），落针时贴合盘面
    val stylusLift by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = if (isPlaying) {
            tween(durationMillis = 480, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 620, easing = FastOutSlowInEasing)
        },
        label = "masterStylusLift"
    )

    Box(
        modifier = Modifier.size(width = (312f * s).dp, height = (296f * s).dp),
        contentAlignment = Alignment.Center
    ) {
        // 黑胶主盘体略微左偏 (-8.dp * s)，为右侧精密液压唱臂与驻停托架留出黄金机械空间
        Box(
            modifier = Modifier
                .offset(x = (-8f * s).dp)
                .size((278f * s).dp)
                .shadow(
                    elevation = (28f * s).dp,
                    shape = CircleShape,
                    spotColor = accentColor.copy(alpha = 0.55f)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF222836).copy(alpha = 0.45f),
                            Color(0xFF0E121B).copy(alpha = 0.75f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .offset(x = (-8f * s).dp)
                .size((266f * s).dp)
                .clip(CircleShape)
                .graphicsLayer { rotationZ = rotationProvider() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.minDimension <= 2f) return@Canvas
                val radius = (size.minDimension / 2f).coerceAtLeast(1f)
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E222B),
                            Color(0xFF101217),
                            Color(0xFF08090C)
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )

                drawCircle(
                    brush = Brush.sweepGradient(
                        0.00f to Color.White.copy(alpha = 0.01f),
                        0.08f to Color.White.copy(alpha = 0.11f),
                        0.16f to Color.White.copy(alpha = 0.01f),
                        0.50f to Color.White.copy(alpha = 0.01f),
                        0.58f to Color.White.copy(alpha = 0.11f),
                        0.66f to Color.White.copy(alpha = 0.01f),
                        1.00f to Color.White.copy(alpha = 0.01f),
                        center = center
                    ),
                    radius = radius * 0.98f,
                    center = center
                )

                val innerR = radius * 0.64f
                val outerR = radius * 0.95f
                val step = (outerR - innerR) / 14f
                for (i in 0..13) {
                    val r = innerR + i * step
                    val alpha = when {
                        i == 0 || i == 13 -> 0.12f
                        i % 4 == 0 -> 0.09f
                        else -> 0.035f
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = (if (i % 4 == 0) 1.1f * s else 0.6f * s).dp.toPx())
                    )
                }

                drawCircle(
                    brush = Brush.linearGradient(
                        listOf(
                            VipGold.copy(alpha = 0.65f),
                            ConceptPrimary.copy(alpha = 0.45f),
                            VipGold.copy(alpha = 0.65f)
                        )
                    ),
                    radius = radius * 0.625f,
                    center = center,
                    style = Stroke(width = (2f * s).dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .size((164f * s).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF181C26)),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = coverUrl,
                    animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                    label = "vinylCoverArtworkCrossfade"
                ) { activeCoverUrl ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeCoverUrl.isNotBlank()) {
                            AsyncImage(
                                model = activeCoverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size((54f * s).dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size((28f * s).dp)
                        .clip(CircleShape)
                        .background(Color(0xAA0A0D14)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size((10f * s).dp)
                            .clip(CircleShape)
                            .background(Color(0xFF07090E))
                    )
                }
            }
        }

        // 右上精密液压平衡唱臂组件（按比例 s 同步缩放全套机械几何坐标）
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            if (size.minDimension <= 2f) return@Canvas
            fun Float.sdp(): Float = (this * s).dp.toPx()

            val pivot = Offset(size.width - 24f.sdp(), 34f.sdp())

            // 1. 右侧唱臂驻停托架 (Tonearm Rest Cradle)：暂停时唱臂正好稳稳落在此托架中
            val cradleCenter = Offset(pivot.x - 2f.sdp(), pivot.y + 72f.sdp())
            drawCircle(
                color = Color(0xFF121722).copy(alpha = 0.85f),
                radius = 7.5f.sdp(),
                center = cradleCenter
            )
            drawArc(
                color = Color(0xFF94A3B8).copy(alpha = 0.65f),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(cradleCenter.x - 5.5f.sdp(), cradleCenter.y - 5.5f.sdp()),
                size = Size(11f.sdp(), 11f.sdp()),
                style = Stroke(width = 1.8f.sdp(), cap = StrokeCap.Round)
            )

            // 2. 唱臂金属轴承底座 (Gimbal Pivot Base)
            drawCircle(
                color = Color(0xFF080B12).copy(alpha = 0.72f),
                radius = 21f.sdp(),
                center = pivot
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3E4759), Color(0xFF1A202C)),
                    center = pivot,
                    radius = 18f.sdp()
                ),
                radius = 17f.sdp(),
                center = pivot
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.24f),
                radius = 17f.sdp(),
                center = pivot,
                style = Stroke(width = 1f.sdp())
            )

            // 3. 液压旋转唱臂主体（0° 为垂直停靠在右侧托架，+24° 为旋入唱片声轨）
            rotate(degrees = armAngle, pivot = pivot) {
                val weightEnd = Offset(pivot.x + 2f.sdp(), pivot.y - 23f.sdp())
                drawLine(
                    color = Color(0xFF78849B),
                    start = pivot,
                    end = weightEnd,
                    strokeWidth = 5.5f.sdp(),
                    cap = StrokeCap.Round
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF222836), Color(0xFF475569), Color(0xFF1E2430))
                    ),
                    topLeft = Offset(pivot.x - 6f.sdp(), weightEnd.y),
                    size = Size(14f.sdp(), 11f.sdp()),
                    cornerRadius = CornerRadius(3f.sdp(), 3f.sdp())
                )

                val armEnd = Offset(pivot.x - 4f.sdp(), pivot.y + 104f.sdp())
                val sCurvePath = Path().apply {
                    moveTo(pivot.x, pivot.y)
                    cubicTo(
                        pivot.x + 5f.sdp(), pivot.y + 34f.sdp(),
                        pivot.x - 8f.sdp(), pivot.y + 68f.sdp(),
                        armEnd.x, armEnd.y
                    )
                }

                val shadowDx = (-2f - 2.5f * stylusLift).sdp()
                val shadowDy = (4f + 3.5f * stylusLift).sdp()
                val shadowPath = Path().apply {
                    moveTo(pivot.x + shadowDx * 0.3f, pivot.y + shadowDy * 0.3f)
                    cubicTo(
                        pivot.x + 5f.sdp() + shadowDx, pivot.y + 34f.sdp() + shadowDy,
                        pivot.x - 8f.sdp() + shadowDx, pivot.y + 68f.sdp() + shadowDy,
                        armEnd.x + shadowDx, armEnd.y + shadowDy
                    )
                }
                drawPath(
                    path = shadowPath,
                    color = Color.Black.copy(alpha = 0.42f - 0.14f * stylusLift),
                    style = Stroke(width = (5.8f + 1.5f * stylusLift).sdp(), cap = StrokeCap.Round)
                )

                drawPath(
                    path = sCurvePath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFF1F5F9),
                            Color(0xFF94A3B8),
                            Color(0xFFE2E8F0)
                        ),
                        start = pivot,
                        end = armEnd
                    ),
                    style = Stroke(width = 3.8f.sdp(), cap = StrokeCap.Round)
                )

                val headTip = Offset(armEnd.x - 8f.sdp(), armEnd.y + 16f.sdp())
                drawLine(
                    color = Color(0xFF1E293B),
                    start = armEnd,
                    end = headTip,
                    strokeWidth = 7.8f.sdp(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = VipGold,
                    start = Offset(armEnd.x - 1.5f.sdp(), armEnd.y + 3f.sdp()),
                    end = Offset(headTip.x + 1.5f.sdp(), headTip.y - 2.5f.sdp()),
                    strokeWidth = 2.2f.sdp(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = lerp(ConceptCyanAccent, Color.White.copy(alpha = 0.45f), stylusLift),
                    radius = (2.6f - 0.4f * stylusLift).sdp(),
                    center = headTip
                )
            }

            // 4. 轴承中心高光顶盖
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFF1F5F9), Color(0xFF64748B))
                ),
                radius = 7.2f.sdp(),
                center = pivot
            )
        }
    }
}

internal const val ROUNDED_COVER_INITIAL_REST_PHASE_RAD = (PI / 4.0).toFloat()
internal const val ROUNDED_COVER_SYMMETRY_PERIOD_RAD = (PI / 2.0).toFloat()
internal const val ROUNDED_COVER_TWO_PI_RAD = (PI * 2.0).toFloat()
internal const val ROUNDED_COVER_MIN_STOP_GLIDE_RAD = (PI / 12.0).toFloat() // 最小前向滑行角位移 15°，避免过近急刹
internal const val ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC = (2.0 * PI / 8.0).toFloat() // 巡航角速度 45°/s (8s/圈，每 2s 转过一个 90° 对称周期)
internal const val ROUNDED_COVER_SPIN_UP_DURATION_SEC = 0.68f
private const val ROUNDED_COVER_DECEL_EXPONENT = 2.2f

/**
 * 圆角方形封面流光动效连续状态容器：
 * - [dispersionPhase]：连续相位角（弧度），暂停/播放切换时严格保持 C1 连续，绝不重置回 0°；
 * - [haloPulse]：光晕呼吸缩放因子，随播放/暂停平滑收放；
 * - [playTransition]：0f（静止暂停态）到 1f（饱满播放态）的连续插值因子。
 */
@Immutable
internal data class RoundedCoverMotionState(
    val dispersionPhase: Float,
    val haloPulse: Float,
    val playTransition: Float,
    val coverScale: Float = 1.0f
)

/**
 * 方案 A：计算「播放 -> 暂停」时顺势向前滑行归位的最近 45° (mod 90°) 对角交错对称目标角（弧度）。
 * - 正方形流光框具有 90° (π/2) 四重旋转对称性，因此 45°、135°、225°、315° 几何形态完全一致；
 * - 暂停瞬间绝不瞬移或倒转，而是顺着当前旋转方向锁定前方最近的 k*(π/2) + π/4 对称角平顺滑行停靠。
 */
internal fun computeRoundedCoverSymmetryStopTargetRad(
    currentPhaseRad: Float,
    currentVelocityRadPerSec: Float = ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC
): Float {
    val period = ROUNDED_COVER_SYMMETRY_PERIOD_RAD
    val offset = ROUNDED_COVER_INITIAL_REST_PHASE_RAD
    val normalizedIndex = (currentPhaseRad - offset) / period
    val ceilIndex = kotlin.math.ceil(normalizedIndex.toDouble() - 1e-5).toInt()
    val candidate = ceilIndex * period + offset
    val delta = (candidate - currentPhaseRad).coerceAtLeast(0f)

    if (currentVelocityRadPerSec <= 0.02f && delta < 0.02f) {
        return candidate
    }
    val velocityRatio = (currentVelocityRadPerSec / ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC).coerceIn(0.25f, 1.0f)
    val minGlide = ROUNDED_COVER_MIN_STOP_GLIDE_RAD * velocityRatio
    return if (delta < minGlide) {
        candidate + period
    } else {
        candidate
    }
}

/**
 * 根据前向滑行角位移 Δθ 与当前角速度计算自然的阻尼减速时长（秒）。
 */
internal fun computeRoundedCoverDecelDurationSec(
    deltaRad: Float,
    currentVelocityRadPerSec: Float = ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC
): Float {
    if (deltaRad <= 1e-4f) return 0f
    val velocityFactor = (currentVelocityRadPerSec / ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC).coerceIn(0.45f, 1.15f)
    val rawDuration = (0.58f + 0.56f * deltaRad) / velocityFactor
    return rawDuration.coerceIn(0.68f, 1.52f)
}

/**
 * 计算「暂停 -> 播放」渐进加速阶段在 [elapsedSec] 时刻的瞬时角速度（smoothstep S 型平滑加速，0 阶跃）。
 */
internal fun computeRoundedCoverSpinUpVelocity(
    startOmegaRadPerSec: Float,
    elapsedSec: Float,
    targetOmegaRadPerSec: Float = ROUNDED_COVER_CRUISE_OMEGA_RAD_PER_SEC,
    durationSec: Float = ROUNDED_COVER_SPIN_UP_DURATION_SEC
): Float {
    if (durationSec <= 0f) return targetOmegaRadPerSec
    val u = (elapsedSec / durationSec).coerceIn(0f, 1f)
    val smooth = u * u * (3f - 2f * u)
    return startOmegaRadPerSec + (targetOmegaRadPerSec - startOmegaRadPerSec) * smooth
}

/**
 * 计算「播放 -> 暂停」阻尼减速阶段在 [elapsedSec] 时刻的瞬时相位角与瞬时角速度：
 * - 采用 p(s) = θ0 + Δθ * (1 - (1 - s)^n)，满足 p(0) = θ0, p(1) = θ_target, ω(1) = 0；
 * - 速度导数 ω(s) 全程严格单调递减至 0，无过冲、无回弹、无瞬移。
 */
internal fun evaluateRoundedCoverDecelerationStep(
    startPhaseRad: Float,
    targetPhaseRad: Float,
    durationSec: Float,
    elapsedSec: Float
): Pair<Float, Float> {
    val deltaRad = (targetPhaseRad - startPhaseRad).coerceAtLeast(0f)
    if (deltaRad <= 1e-5f || durationSec <= 1e-4f) {
        return targetPhaseRad to 0f
    }
    val s = (elapsedSec / durationSec).coerceIn(0f, 1f)
    val rem = 1f - s
    val remPow = Math.pow(rem.toDouble(), ROUNDED_COVER_DECEL_EXPONENT.toDouble()).toFloat()
    val remDerivPow = Math.pow(rem.toDouble(), (ROUNDED_COVER_DECEL_EXPONENT - 1f).toDouble()).toFloat()
    val phase = startPhaseRad + deltaRad * (1f - remPow)
    val velocity = if (s >= 1f) 0f else (ROUNDED_COVER_DECEL_EXPONENT * deltaRad / durationSec) * remDerivPow
    return phase to velocity
}

@Composable
internal fun rememberRoundedCoverMotionState(
    isPlaying: Boolean,
    songKey: Any? = null,
    activeAndVisible: Boolean = true
): RoundedCoverMotionState {
    var phaseRad by remember(songKey) { mutableFloatStateOf(ROUNDED_COVER_INITIAL_REST_PHASE_RAD) }
    var velocityRadPerSec by remember(songKey) { mutableFloatStateOf(0f) }
    var breathPhaseRad by remember(songKey) { mutableFloatStateOf(0f) }
    var hasEverPlayed by remember(songKey) { mutableStateOf(isPlaying) }

    val playTransition by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        label = "roundedCoverPlayTransition"
    )
    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.92f,
        animationSpec = ConceptMotion.gentleBounceSpring(),
        label = "squareCoverScale"
    )

    LaunchedEffect(isPlaying, songKey, activeAndVisible) {
        if (!activeAndVisible) return@LaunchedEffect
        val twoPi = ROUNDED_COVER_TWO_PI_RAD
        if (isPlaying) {
            hasEverPlayed = true
            val startOmega = velocityRadPerSec.coerceAtLeast(0f)
            var elapsedSec = 0f
            var lastFrameNanos = -1L
            while (isActive) {
                withFrameNanos { frameNanos ->
                    val dt = if (lastFrameNanos < 0L) {
                        0f
                    } else {
                        ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    }
                    lastFrameNanos = frameNanos
                    elapsedSec += dt
                    val currentOmega = computeRoundedCoverSpinUpVelocity(
                        startOmegaRadPerSec = startOmega,
                        elapsedSec = elapsedSec
                    )
                    velocityRadPerSec = currentOmega
                    phaseRad = (phaseRad + currentOmega * dt) % twoPi
                    breathPhaseRad = (breathPhaseRad + dt * (twoPi / 6.0f)) % twoPi
                }
            }
        } else if (hasEverPlayed || velocityRadPerSec > 0.005f) {
            val startPhase = phaseRad
            val startVelocity = velocityRadPerSec.coerceAtLeast(0.15f)
            val targetPhase = computeRoundedCoverSymmetryStopTargetRad(
                currentPhaseRad = startPhase,
                currentVelocityRadPerSec = startVelocity
            )
            val deltaRad = (targetPhase - startPhase).coerceAtLeast(0f)
            val durationSec = computeRoundedCoverDecelDurationSec(
                deltaRad = deltaRad,
                currentVelocityRadPerSec = startVelocity
            )
            if (durationSec > 0f && deltaRad > 1e-4f) {
                var elapsedSec = 0f
                var lastFrameNanos = -1L
                while (isActive && elapsedSec < durationSec) {
                    withFrameNanos { frameNanos ->
                        val dt = if (lastFrameNanos < 0L) {
                            0f
                        } else {
                            ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        }
                        lastFrameNanos = frameNanos
                        elapsedSec = (elapsedSec + dt).coerceAtMost(durationSec)
                        val (nextPhase, nextVel) = evaluateRoundedCoverDecelerationStep(
                            startPhaseRad = startPhase,
                            targetPhaseRad = targetPhase,
                            durationSec = durationSec,
                            elapsedSec = elapsedSec
                        )
                        phaseRad = nextPhase
                        velocityRadPerSec = nextVel
                        breathPhaseRad = (breathPhaseRad + dt * (twoPi / 6.0f) * (1f - elapsedSec / durationSec)) % twoPi
                    }
                }
            }
            phaseRad = ((targetPhase % twoPi) + twoPi) % twoPi
            velocityRadPerSec = 0f
        }
    }

    val haloPulse = 0.94f + playTransition * (0.065f + 0.055f * sin(breathPhaseRad))
    return RoundedCoverMotionState(
        dispersionPhase = phaseRad,
        haloPulse = haloPulse,
        playTransition = playTransition,
        coverScale = coverScale
    )
}

/**
 * 圆角封面多光谱棱镜边缘色散光晕绘制引擎 (Chromatic Dispersion Edge Halo):
 * - 同时赋能竖屏与横屏圆角封面；
 * - 将歌曲主题色分解为「青蓝短波 (Cyan-Blue)」「玫瑰品红长波 (Rose-Magenta)」「电光紫中波 (Electric-Violet)」三大色散通道；
 * - 采用满足离屏图层 Alpha 结合律的高明度预乘羽化混合 (BlendMode.SrcOver)，
 *   确保在 AnimatedContent 进出歌词页离屏透明层切换 (alpha < 1 <-> alpha == 1) 时光晕亮度 100% 连续零闪烁。
 */
internal fun DrawScope.drawRoundedCoverEdgeDispersionHalo(
    cornerPx: Float,
    accentPrimary: Color,
    accentSecondary: Color,
    dispersionPhase: Float,
    haloPulse: Float,
    isPlaying: Boolean,
    playTransition: Float = if (isPlaying) 1f else 0f
) {
    val w = size.width
    val h = size.height
    if (w <= 4f || h <= 4f) return
    val p = playTransition.coerceIn(0f, 1f)

    // 三原色高明度棱镜色散光谱通道（向高明度霓虹色偏置，保障在 SrcOver 预乘混合下通透鲜亮且不受离屏透明层突变影响）
    val prismCyan = lerp(lerp(accentSecondary, Color(0xFF00F0FF), 0.62f), Color.White, 0.22f)
    val prismMagenta = lerp(lerp(accentPrimary, Color(0xFFFF3E8E), 0.56f), Color.White, 0.20f)
    val prismViolet = lerp(lerp(lerp(accentPrimary, accentSecondary, 0.5f), Color(0xFFA29BFE), 0.58f), Color.White, 0.22f)
    val prismGold = lerp(lerp(accentPrimary, Color(0xFFFFD166), 0.48f), Color.White, 0.20f)
    val deepShadowColor = lerp(accentPrimary, Color.Black, 0.68f)

    // 1. 底部深邃悬浮立体暗影（随 playTransition 平滑过渡）
    val shadowCenter = Offset(w * 0.5f, h * 0.58f)
    val shadowRadius = (w * 0.68f).coerceAtLeast(1f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to deepShadowColor.copy(alpha = 0.42f + 0.20f * p),
                0.64f to deepShadowColor.copy(alpha = 0.16f + 0.12f * p),
                1.00f to Color.Transparent
            ),
            center = shadowCenter,
            radius = shadowRadius
        ),
        radius = shadowRadius,
        center = shadowCenter
    )

    // 2. 沿圆角矩形边缘展开的三通道色散错位光场 (Chromatic Aberration Rounded-Rect Shells)
    val dispAmp = (3.8f + 2.7f * p).dp.toPx() * haloPulse
    val cyanShift = Offset(cos(dispersionPhase) * dispAmp, sin(dispersionPhase) * dispAmp)
    val magentaShift = Offset(cos(dispersionPhase + 2.094f) * dispAmp, sin(dispersionPhase + 2.094f) * dispAmp)
    val violetShift = Offset(cos(dispersionPhase + 4.188f) * dispAmp, sin(dispersionPhase + 4.188f) * dispAmp)

    val maxSpreadPx = 28.dp.toPx() * haloPulse
    val shellCount = 7
    val activityBoost = 0.66f + 0.34f * p

    for (i in shellCount downTo 1) {
        val t = i.toFloat() / shellCount.toFloat()
        val outset = maxSpreadPx * t
        // 越靠近封面边缘色散能量越凝聚，向外呈二次方柔和衰减；采用 BlendMode.SrcOver 消除离屏层切换闪烁
        val shellAlpha = ((1f - t * 0.76f) * (1f - t * 0.76f) * 0.155f * activityBoost).coerceIn(0.010f, 0.20f)
        val shellSize = Size(w + outset * 2f, h + outset * 2f)
        val shellCorner = CornerRadius(cornerPx + outset, cornerPx + outset)

        // 短波青蓝色散壳
        drawRoundRect(
            color = prismCyan.copy(alpha = shellAlpha),
            topLeft = Offset(-outset + cyanShift.x * t, -outset + cyanShift.y * t),
            size = shellSize,
            cornerRadius = shellCorner,
            blendMode = BlendMode.SrcOver
        )
        // 长波品红色散壳
        drawRoundRect(
            color = prismMagenta.copy(alpha = shellAlpha * 0.95f),
            topLeft = Offset(-outset + magentaShift.x * t, -outset + magentaShift.y * t),
            size = shellSize,
            cornerRadius = shellCorner,
            blendMode = BlendMode.SrcOver
        )
        // 中波电光紫色散壳
        drawRoundRect(
            color = prismViolet.copy(alpha = shellAlpha * 0.92f),
            topLeft = Offset(-outset + violetShift.x * t, -outset + violetShift.y * t),
            size = shellSize,
            cornerRadius = shellCorner,
            blendMode = BlendMode.SrcOver
        )
    }

    // 3. 贴合圆角边缘的旋转棱镜光谱色散流光环 (Prismatic Spectral Dispersion Edge Rings)
    val center = Offset(w * 0.5f, h * 0.5f)
    val deg = ((dispersionPhase * 180f / PI.toFloat()) % 360f + 360f) % 360f
    rotate(degrees = deg, pivot = center) {
        val sweepBrush = Brush.sweepGradient(
            0.00f to prismCyan,
            0.16f to lerp(accentPrimary, Color.White, 0.18f),
            0.33f to prismMagenta,
            0.50f to prismGold,
            0.66f to prismViolet,
            0.83f to lerp(accentSecondary, Color.White, 0.18f),
            0.92f to Color.White.copy(alpha = 0.94f),
            1.00f to prismCyan,
            center = center
        )
        for (ring in 1..4) {
            val ringOutset = (1.2f + ring * 2.1f).dp.toPx() * haloPulse
            val ringAlpha = ((0.44f / ring) * activityBoost).coerceIn(0.05f, 0.48f)
            val strokeW = ((5.2f - ring * 0.9f).coerceAtLeast(1.2f)).dp.toPx()
            drawRoundRect(
                brush = sweepBrush,
                topLeft = Offset(-ringOutset, -ringOutset),
                size = Size(w + ringOutset * 2f, h + ringOutset * 2f),
                cornerRadius = CornerRadius(cornerPx + ringOutset, cornerPx + ringOutset),
                alpha = ringAlpha,
                style = Stroke(width = strokeW),
                blendMode = BlendMode.SrcOver
            )
        }
    }
}

/**
 * 圆角封面表层 2.5D 棱镜色散折射倒角高光边 (Prismatic Chamfered Bevel Rim)
 */
internal fun DrawScope.drawRoundedCoverPrismaticRim(
    cornerPx: Float,
    accentPrimary: Color,
    accentSecondary: Color,
    dispersionPhase: Float,
    isPlaying: Boolean,
    playTransition: Float = if (isPlaying) 1f else 0f
) {
    val w = size.width
    val h = size.height
    if (w <= 4f || h <= 4f) return
    val p = playTransition.coerceIn(0f, 1f)

    val prismCyan = lerp(accentSecondary, Color(0xFF00F0FF), 0.58f)
    val prismMagenta = lerp(accentPrimary, Color(0xFFFF3E8E), 0.52f)
    val prismViolet = lerp(accentPrimary, Color(0xFFA29BFE), 0.50f)

    // 1. 表层微受光柔面与暗角
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.13f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.22f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
    )

    // 2. 外沿多光谱棱镜色散折射高光边框
    val strokePx = 1.8.dp.toPx()
    val halfStroke = strokePx / 2f
    val phaseCos = cos(dispersionPhase)
    val phaseSin = sin(dispersionPhase)
    val gradStart = Offset(
        w * (0.5f - 0.5f * phaseCos),
        h * (0.5f - 0.5f * phaseSin)
    )
    val gradEnd = Offset(
        w * (0.5f + 0.5f * phaseCos),
        h * (0.5f + 0.5f * phaseSin)
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.48f + 0.24f * p),
                prismCyan.copy(alpha = 0.42f + 0.26f * p),
                prismMagenta.copy(alpha = 0.36f + 0.26f * p),
                prismViolet.copy(alpha = 0.40f + 0.26f * p),
                Color.White.copy(alpha = 0.32f + 0.22f * p)
            ),
            start = gradStart,
            end = gradEnd
        ),
        topLeft = Offset(halfStroke, halfStroke),
        size = Size(w - strokePx, h - strokePx),
        cornerRadius = CornerRadius(cornerPx - halfStroke, cornerPx - halfStroke),
        style = Stroke(width = strokePx)
    )

    // 3. 内侧晶莹次级折射细线
    val innerInset = 2.4.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                prismCyan.copy(alpha = 0.26f),
                Color.White.copy(alpha = 0.16f),
                prismMagenta.copy(alpha = 0.22f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        ),
        topLeft = Offset(innerInset, innerInset),
        size = Size(w - innerInset * 2f, h - innerInset * 2f),
        cornerRadius = CornerRadius(cornerPx - innerInset, cornerPx - innerInset),
        style = Stroke(width = 0.85.dp.toPx())
    )
}

@Composable
internal fun StaticRoundedSquareCoverView(
    coverUrl: String,
    isPlaying: Boolean,
    accentColor: Color,
    secondaryAccentColor: Color = ConceptCyanAccent,
    motionState: RoundedCoverMotionState? = null,
    coverSize: androidx.compose.ui.unit.Dp = 276.dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 30.dp
) {
    val resolvedMotionState = motionState ?: rememberRoundedCoverMotionState(
        isPlaying = isPlaying,
        songKey = coverUrl
    )
    val coverScale = resolvedMotionState.coverScale
    val haloPulse = resolvedMotionState.haloPulse
    val dispersionPhase = resolvedMotionState.dispersionPhase
    val playTransition = resolvedMotionState.playTransition

    Box(
        modifier = Modifier
            .size(coverSize)
            .graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
                clip = false
            }
            .drawBehind {
                drawRoundedCoverEdgeDispersionHalo(
                    cornerPx = cornerRadius.toPx(),
                    accentPrimary = accentColor,
                    accentSecondary = secondaryAccentColor,
                    dispersionPhase = dispersionPhase,
                    haloPulse = haloPulse,
                    isPlaying = isPlaying,
                    playTransition = playTransition
                )
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF151A26))
            .drawWithContent {
                drawContent()
                drawRoundedCoverPrismaticRim(
                    cornerPx = cornerRadius.toPx(),
                    accentPrimary = accentColor,
                    accentSecondary = secondaryAccentColor,
                    dispersionPhase = dispersionPhase,
                    isPlaying = isPlaying,
                    playTransition = playTransition
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = coverUrl,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "squareCoverArtworkCrossfade"
        ) { activeCoverUrl ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (activeCoverUrl.isNotBlank()) {
                    AsyncImage(
                        model = activeCoverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(68.dp)
                    )
                }
            }
        }
    }
}

