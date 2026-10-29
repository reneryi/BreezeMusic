package com.concept.music.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.concept.music.ui.theme.ConceptPrimaryLight
import com.concept.music.ui.theme.TextPrimaryLight
import com.concept.music.ui.theme.TextSecondaryLight
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.WatercolorBlueWash
import com.concept.music.ui.theme.isConceptDarkTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * 冷启动图标页额外驻留时长（0ms）：
 * 零额外人为阻塞，首帧渲染完成即刻移除系统 SplashScreen 图标层进入主界面。
 */
const val SPLASH_ICON_HOLD_DURATION_MS = 0L

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
 * 支持明暗两套配色体系：
 * - 深色模式：深海暗夜基底，透出静谧微蓝光感；
 * - 浅色模式：清透水彩浸润底色，纯白圆润悬浮卡片配合柔光投影。
 */
@Composable
fun BreezeAppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    cornerRadius: Dp = 22.dp,
    isDark: Boolean = isConceptDarkTheme(),
    showAmbientHalo: Boolean = true
) {
    val shape = RoundedCornerShape(cornerRadius)
    val haloColors = if (isDark) {
        listOf(
            ConceptCyanAccent.copy(alpha = 0.32f),
            ConceptPrimary.copy(alpha = 0.14f),
            Color.Transparent
        )
    } else {
        listOf(
            WatercolorBlueWash.copy(alpha = 0.28f),
            ConceptPrimaryLight.copy(alpha = 0.12f),
            Color.Transparent
        )
    }
    val surfaceColor = if (isDark) Color(0xFF081224) else Color(0xFFFFFFFF)
    val ambientColor = if (isDark) ConceptPrimary else ConceptPrimaryLight
    val spotColor = if (isDark) ConceptCyanAccent else WatercolorBlueWash

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (showAmbientHalo) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = haloColors,
                        radius = this.size.minDimension * 0.78f
                    ),
                    radius = this.size.minDimension * 0.78f
                )
            }
        }
        Surface(
            shape = shape,
            color = surfaceColor,
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = if (showAmbientHalo) (if (isDark) 16.dp else 12.dp) else 4.dp,
                    shape = shape,
                    ambientColor = ambientColor,
                    spotColor = spotColor
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
 * 声学频谱律动波柱动画组件（明暗模式自动调谐）：
 * 9 阶双对称律动柱，实时呈现呼吸频率波形。
 */
@Composable
fun BreezeAcousticRhythmBars(
    modifier: Modifier = Modifier,
    phase: Float = 0f,
    barCount: Int = 9,
    maxBarHeight: Dp = 24.dp,
    barWidth: Dp = 3.8.dp,
    barSpacing: Dp = 5.5.dp,
    isDark: Boolean = isConceptDarkTheme()
) {
    val primaryColor = if (isDark) ConceptPrimary else ConceptPrimaryLight
    val accentColor = ConceptCyanAccent

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val distFromCenter = kotlin.math.abs(i - (barCount - 1) / 2.0f)
            val centerWeight = 1.0f - (distFromCenter / (barCount / 2.0f)) * 0.45f
            val wave = 0.5f + 0.5f * sin((phase + i * 0.72f).toDouble()).toFloat()
            val heightRatio = (0.28f + 0.72f * wave * centerWeight).coerceIn(0.18f, 1.0f)
            val heightDp = maxBarHeight * heightRatio

            Canvas(
                modifier = Modifier
                    .width(barWidth)
                    .height(heightDp)
            ) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.95f),
                            accentColor.copy(alpha = 0.85f)
                        )
                    ),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(size.width / 2f, size.width / 2f)
                )
            }
        }
    }
}

/**
 * 微风音乐 (BreezeMusic) 原生全屏开屏页面（包含深色「幽邃微蓝」与浅色「水彩浸润」明暗两套页面）：
 * - 在冷启动与首帧完成时流畅展现品牌视觉；
 * - 底部静默挂载 MainContent，约 1.35 秒后平滑淡出，支持轻触屏幕即刻跳过进入主界面。
 */
@Composable
fun BreezeSplashOverlay(
    visible: Boolean,
    isDark: Boolean = isConceptDarkTheme(),
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val versionInfo = remember(context) { resolveAppVersionInfo(context) }

    LaunchedEffect(visible) {
        if (visible) {
            delay(1350L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 1.04f, animationSpec = tween(280, easing = FastOutSlowInEasing))
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "SplashAcousticPulse")
        val rhythmPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "RhythmPhase"
        )
        val auraPulse by infiniteTransition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "AuraPulse"
        )

        // 明暗模式双套配色定义
        val bgGradient = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF070E1B),
                    Color(0xFF0D1728),
                    Color(0xFF050B15)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(0xFFF2F6FC),
                    Color(0xFFE5EFFB),
                    Color(0xFFFAFBFD)
                )
            )
        }

        val auraColors = if (isDark) {
            listOf(
                ConceptCyanAccent.copy(alpha = 0.28f),
                ConceptPrimary.copy(alpha = 0.16f),
                Color.Transparent
            )
        } else {
            listOf(
                WatercolorBlueWash.copy(alpha = 0.24f),
                ConceptPrimaryLight.copy(alpha = 0.12f),
                Color.Transparent
            )
        }

        val titleColor = if (isDark) Color.White else TextPrimaryLight
        val brandTagColor = if (isDark) ConceptCyanAccent.copy(alpha = 0.90f) else ConceptPrimaryLight
        val sloganColor = if (isDark) Color.White.copy(alpha = 0.78f) else TextSecondaryLight
        val badgeBgAlpha = if (isDark) 0.14f else 0.10f
        val versionTextColor = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondaryLight.copy(alpha = 0.75f)
        val engineSubtextColor = if (isDark) Color.White.copy(alpha = 0.42f) else TextSecondaryLight.copy(alpha = 0.48f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        ) {
            // 背景弥散微光层
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width * 0.5f, size.height * 0.42f)
                val baseRadius = size.minDimension * 0.72f * auraPulse
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = auraColors,
                        center = center,
                        radius = baseRadius
                    ),
                    center = center,
                    radius = baseRadius
                )
            }

            // 核心品牌徽章与标题
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BreezeAppLogo(
                    size = 96.dp,
                    cornerRadius = 26.dp,
                    isDark = isDark,
                    showAmbientHalo = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "微风音乐",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        letterSpacing = 1.8.sp
                    ),
                    color = titleColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "B R E E Z E   M U S I C",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 3.4.sp
                    ),
                    color = brandTagColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "耳畔微风起 · 帧享真无损",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        letterSpacing = 1.0.sp
                    ),
                    color = sloganColor
                )

                Spacer(modifier = Modifier.height(22.dp))

                // 实时声学频谱律动动画
                BreezeAcousticRhythmBars(
                    phase = rhythmPhase,
                    barCount = 9,
                    maxBarHeight = 22.dp,
                    barWidth = 3.6.dp,
                    barSpacing = 5.2.dp,
                    isDark = isDark
                )

                Spacer(modifier = Modifier.height(22.dp))

                // 无边框柔光胶囊标识
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "Hi-Res 24bit" to VipGold,
                        "64-bit 母带 DSP" to (if (isDark) ConceptCyanAccent else ConceptPrimaryLight),
                        "KRC 逐字流光" to ConceptMintAccent
                    ).forEach { (badgeText, accent) ->
                        Surface(
                            shape = CircleShape,
                            color = accent.copy(alpha = badgeBgAlpha)
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

            // 底部版本与引擎标识
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "BreezeMusic ${versionInfo.displayVersion} · Build ${versionInfo.versionCode}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = versionTextColor,
                    fontSize = 11.5.sp
                )
                Text(
                    text = "Pure Client-Side Direct Engine · Kugou Concept Edition",
                    style = MaterialTheme.typography.labelSmall,
                    color = engineSubtextColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}
