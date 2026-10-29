package com.concept.music.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.BuildConfig
import com.concept.music.R
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptMintAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.VipGold
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * 动态读取的实际应用版本信息（兼容 PackageManager 真实安装版本与 BuildConfig 编译版本）
 */
@Immutable
data class AppVersionInfo(
    val rawVersionName: String,
    val versionCode: Long
) {
    val displayVersion: String
        get() = if (rawVersionName.startsWith("v", ignoreCase = true)) {
            "v${rawVersionName.drop(1)}"
        } else {
            "v$rawVersionName"
        }

    val fullBadgeText: String
        get() = "$displayVersion 正式版 · Build $versionCode"
}

/**
 * 从当前运行环境的 PackageManager 动态获取真实版本号与构建号，确保修改 gradle versionName 或打包后关于页实时准确反映
 */
fun resolveAppVersionInfo(context: Context?): AppVersionInfo {
    if (context != null) {
        runCatching {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageInfo(context.packageName, 0)
            val vName = pkgInfo.versionName?.trim()?.removePrefix("v")?.removePrefix("V")
                ?.takeIf { it.isNotBlank() }
                ?: BuildConfig.VERSION_NAME
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            return AppVersionInfo(
                rawVersionName = vName,
                versionCode = vCode.coerceAtLeast(1L)
            )
        }
    }
    return AppVersionInfo(
        rawVersionName = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V").ifBlank { "1.0.1" },
        versionCode = BuildConfig.VERSION_CODE.toLong().coerceAtLeast(1L)
    )
}

/**
 * 微风音乐 (BreezeMusic) 统一品牌应用图标组件：
 * 采用无边框纯净深海柔光设计，渲染 `@drawable/ic_launcher_concept` 高精度矢量图标与弥散光晕，
 * 在「开屏页面」与「设置 -> 关于微风音乐」中共享使用，保持全应用品牌视觉高度一致。
 */
@Composable
fun BreezeAppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    cornerRadius: Dp = 22.dp,
    showAmbientHalo: Boolean = true
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (showAmbientHalo) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ConceptCyanAccent.copy(alpha = 0.32f),
                            ConceptPrimary.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        radius = this.size.minDimension * 0.78f
                    ),
                    radius = this.size.minDimension * 0.78f
                )
            }
        }
        Surface(
            shape = shape,
            color = Color(0xFF081224),
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = if (showAmbientHalo) 16.dp else 4.dp,
                    shape = shape,
                    ambientColor = ConceptPrimary,
                    spotColor = ConceptCyanAccent
                )
                .clip(shape)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_concept),
                contentDescription = "BreezeMusic 微风音乐图标",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 微风声学频谱律动动画组件（无边框圆角渐变音律波柱）：
 * 通过 9 根对称排布的流光声学能量柱与相位差正弦包络，呈现灵动柔和的音频律动效果。
 */
@Composable
fun BreezeAcousticRhythmBars(
    phase: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 9,
    maxBarHeight: Dp = 26.dp,
    barWidth: Dp = 4.dp,
    barSpacing: Dp = 5.5.dp
) {
    val totalWidth = (barWidth * barCount) + (barSpacing * (barCount - 1).coerceAtLeast(0))
    Canvas(
        modifier = modifier
            .width(totalWidth)
            .height(maxBarHeight)
    ) {
        val wPx = barWidth.toPx()
        val gapPx = barSpacing.toPx()
        val maxH = size.height
        val centerIdx = (barCount - 1) / 2f

        for (i in 0 until barCount) {
            val distFromCenter = kotlin.math.abs(i - centerIdx) / centerIdx.coerceAtLeast(1f)
            val envelopeWeight = 1f - (distFromCenter * 0.48f)
            val wave1 = (sin(phase * 2.2f + i * 0.78f) + 1f) * 0.5f
            val wave2 = (cos(phase * 1.4f - i * 1.15f) + 1f) * 0.5f
            val normalizedAmp = (0.24f + (wave1 * 0.52f + wave2 * 0.24f) * envelopeWeight).coerceIn(0.20f, 1.0f)

            val barH = (maxH * normalizedAmp).coerceAtLeast(wPx)
            val x = i * (wPx + gapPx)
            val y = (maxH - barH) / 2f

            val topColor = when (i % 3) {
                0 -> ConceptCyanAccent
                1 -> ConceptMintAccent
                else -> ConceptPrimary
            }
            val bottomColor = ConceptPrimary.copy(alpha = 0.65f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        topColor.copy(alpha = 0.92f),
                        bottomColor
                    ),
                    startY = y,
                    endY = y + barH
                ),
                topLeft = Offset(x, y),
                size = Size(wPx, barH),
                cornerRadius = CornerRadius(wPx / 2f, wPx / 2f)
            )
        }
    }
}

/**
 * 微风音乐沉浸式品牌开屏页面（Splash Screen v1.0.1）：
 * 1. 移除右上角「跳过」按钮，点击桌面图标后零中间页直达本开屏画面；
 * 2. 全面采用无边框线（Borderless）流体光晕与磨砂胶囊设计，告别生硬描边；
 * 3. 融合双层呼吸极光光晕、微风柔和流体波浪与 9 频段声学律动波柱动画；
 * 4. 保持黄金展示时长（1.38 秒后自动平滑渐隐进入主界面）。
 */
const val BREEZE_SPLASH_DISPLAY_DURATION_MS = 2150L

@Composable
fun BreezeSplashOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    durationMillis: Long = BREEZE_SPLASH_DISPLAY_DURATION_MS
) {
    val context = LocalContext.current
    val versionInfo = remember(context) { resolveAppVersionInfo(context) }

    var startEntranceAnim by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            startEntranceAnim = true
            delay(durationMillis)
            onDismiss()
        }
    }

    val logoScale by animateFloatAsState(
        targetValue = if (startEntranceAnim) 1f else 0.94f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "SplashLogoScale"
    )
    val contentAlpha = 1f

    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.EnterTransition.None,
        exit = fadeOut(animationSpec = tween(380)) + scaleOut(
            targetScale = 1.03f,
            animationSpec = tween(380, easing = FastOutSlowInEasing)
        )
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "SplashRhythmTransition")
        val rhythmPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SplashRhythmPhase"
        )
        val haloPulse by infiniteTransition.animateFloat(
            initialValue = 0.90f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "SplashHaloPulse"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF050A14),
                            Color(0xFF0A162B),
                            Color(0xFF0E2240),
                            Color(0xFF07101E)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            // 背景无边框流体极光光晕与微风声场柔光色带（全填充渐变，不使用任何线条描边）
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // 主极光呼吸光晕
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ConceptPrimary.copy(alpha = 0.24f),
                            ConceptCyanAccent.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h * 0.39f),
                        radius = w * 0.72f * haloPulse
                    ),
                    center = Offset(w * 0.5f, h * 0.39f),
                    radius = w * 0.72f * haloPulse
                )

                // 次级薄荷青律动弥散光斑
                val driftX = w * 0.5f + sin(rhythmPhase) * (w * 0.08f)
                val driftY = h * 0.45f + cos(rhythmPhase) * (h * 0.03f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ConceptMintAccent.copy(alpha = 0.12f),
                            ConceptCyanAccent.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(driftX, driftY),
                        radius = w * 0.48f
                    ),
                    center = Offset(driftX, driftY),
                    radius = w * 0.48f
                )

                // 底部微风柔光波浪填充层（无边框面状渐变）
                val wavePath = Path().apply {
                    val baseY = h * 0.78f
                    val ampPx = 18.dp.toPx()
                    moveTo(0f, h)
                    lineTo(0f, baseY)
                    val steps = 36
                    for (i in 0..steps) {
                        val t = i / steps.toFloat()
                        val x = w * t
                        val y = baseY + sin(t * 3.6f + rhythmPhase) * ampPx
                        lineTo(x, y)
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(
                    path = wavePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ConceptCyanAccent.copy(alpha = 0.06f),
                            ConceptPrimary.copy(alpha = 0.02f),
                            Color.Transparent
                        ),
                        startY = h * 0.74f,
                        endY = h
                    )
                )
            }

            // 中心主体：无边框呼吸声学光环 + 品牌图标 + 标题 + 声学律动波柱 + 无边框圆点特性标识
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = contentAlpha
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(144.dp)
                ) {
                    // 无边框双层柔光声学呼吸光晕（替代原先的细线圆环）
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val rOuter = (size.minDimension / 2f) * haloPulse
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ConceptCyanAccent.copy(alpha = 0.24f),
                                    ConceptPrimary.copy(alpha = 0.10f),
                                    Color.Transparent
                                ),
                                radius = rOuter
                            ),
                            radius = rOuter
                        )
                        drawCircle(
                            color = ConceptCyanAccent.copy(alpha = 0.08f),
                            radius = rOuter * 0.78f
                        )
                    }

                    BreezeAppLogo(
                        size = 98.dp,
                        cornerRadius = 28.dp,
                        showAmbientHalo = true
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "微风音乐",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        letterSpacing = 1.8.sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "B R E E Z E   M U S I C",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 3.4.sp
                    ),
                    color = ConceptCyanAccent.copy(alpha = 0.88f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "耳畔微风起 · 帧享真无损",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        letterSpacing = 1.0.sp
                    ),
                    color = Color.White.copy(alpha = 0.78f)
                )

                Spacer(modifier = Modifier.height(22.dp))

                // 实时声学频谱律动波柱动画
                BreezeAcousticRhythmBars(
                    phase = rhythmPhase,
                    barCount = 9,
                    maxBarHeight = 24.dp,
                    barWidth = 3.8.dp,
                    barSpacing = 5.5.dp
                )

                Spacer(modifier = Modifier.height(22.dp))

                // 无边框柔光胶囊标识（采用呼吸光点 + 柔和底色，完全去除边框线）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "Hi-Res 24bit" to VipGold,
                        "64-bit 母带 DSP" to ConceptCyanAccent,
                        "KRC 逐字流光" to ConceptMintAccent
                    ).forEach { (badgeText, accent) ->
                        Surface(
                            shape = CircleShape,
                            color = accent.copy(alpha = 0.12f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = badgeText,
                                    color = accent.copy(alpha = 0.95f),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 底部实时版本标识与端侧直连签名说明
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 26.dp)
                    .graphicsLayer { alpha = contentAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "BreezeMusic ${versionInfo.displayVersion} · Build ${versionInfo.versionCode}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 11.5.sp
                )
                Text(
                    text = "Pure Client-Side Direct Engine · Kugou Concept Edition",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
