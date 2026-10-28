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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
        rawVersionName = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V").ifBlank { "1.0.0" },
        versionCode = BuildConfig.VERSION_CODE.toLong().coerceAtLeast(1L)
    )
}

/**
 * 微风音乐 (BreezeMusic) 统一品牌应用图标组件：
 * 直接渲染 `@drawable/ic_launcher_concept` 高精度矢量图标，并附带微风流光玻璃边框与柔光阴影，
 * 在「开屏页面」与「设置 -> 关于微风音乐」中共享使用，确保全应用品牌视觉高度一致。
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
                            ConceptCyanAccent.copy(alpha = 0.30f),
                            ConceptPrimary.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        radius = this.size.minDimension * 0.72f
                    ),
                    radius = this.size.minDimension * 0.72f
                )
            }
        }
        Surface(
            shape = shape,
            color = Color(0xFF081224),
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = if (showAmbientHalo) 14.dp else 4.dp,
                    shape = shape,
                    ambientColor = ConceptPrimary,
                    spotColor = ConceptCyanAccent
                )
                .clip(shape)
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            ConceptCyanAccent.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.25f),
                            ConceptMintAccent.copy(alpha = 0.55f)
                        )
                    ),
                    shape = shape
                )
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
 * 微风音乐沉浸式品牌开屏页面（Splash Screen）：
 * 融合「深海微风 · 黑胶流光」动态波浪声场、旋转黑胶光环、统一品牌图标与实时版本号展示，
 * 支持右上角一键「跳过」或 1.35 秒后自动平滑渐隐进入主界面。
 */
@Composable
fun BreezeSplashOverlay(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val versionInfo = remember(context) { resolveAppVersionInfo(context) }

    var startEntranceAnim by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            startEntranceAnim = true
            delay(1380L)
            onDismiss()
        }
    }

    val logoScale by animateFloatAsState(
        targetValue = if (startEntranceAnim) 1f else 0.84f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "SplashLogoScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (startEntranceAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "SplashContentAlpha"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(380)) + scaleOut(
            targetScale = 1.05f,
            animationSpec = tween(380, easing = FastOutSlowInEasing)
        )
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "SplashWaveTransition")
        val wavePhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SplashWavePhase"
        )
        val haloPulse by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
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
            // 背景动态微风声波流光曲线与深海星芒光晕
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ConceptPrimary.copy(alpha = 0.22f),
                            ConceptCyanAccent.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h * 0.40f),
                        radius = w * 0.68f * haloPulse
                    ),
                    center = Offset(w * 0.5f, h * 0.40f),
                    radius = w * 0.68f * haloPulse
                )

                // 绘制三道灵动微风声波丝带
                val ribbonConfigs = listOf(
                    Triple(0.42f, 26f, ConceptCyanAccent.copy(alpha = 0.22f)),
                    Triple(0.46f, 20f, ConceptPrimary.copy(alpha = 0.18f)),
                    Triple(0.50f, 16f, ConceptMintAccent.copy(alpha = 0.15f))
                )
                ribbonConfigs.forEachIndexed { idx, (yFrac, ampDp, color) ->
                    val path = Path()
                    val baseY = h * yFrac
                    val ampPx = ampDp * density
                    val steps = 48
                    for (i in 0..steps) {
                        val t = i / steps.toFloat()
                        val x = w * t
                        val y = baseY + sin(t * 4.2f + wavePhase + idx * 0.9f) * ampPx
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = (2.2f - idx * 0.4f) * density, cap = StrokeCap.Round)
                    )
                }
            }

            // 右上角「跳过」胶囊按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 14.dp, end = 18.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                        .clickable { onDismiss() }
                ) {
                    Text(
                        text = "跳过",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp)
                    )
                }
            }

            // 中心主体：微风音乐品牌图标 + 标题 + Slogan + 特性徽章
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
                    modifier = Modifier.size(136.dp)
                ) {
                    // 外层呼吸黑胶声波环
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = (size.minDimension / 2f) * haloPulse
                        drawCircle(
                            color = ConceptCyanAccent.copy(alpha = 0.22f),
                            radius = r,
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                        drawCircle(
                            color = ConceptPrimary.copy(alpha = 0.14f),
                            radius = r * 0.88f,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    BreezeAppLogo(
                        size = 98.dp,
                        cornerRadius = 28.dp,
                        showAmbientHalo = true
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "微风音乐",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        letterSpacing = 1.5.sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "B R E E Z E   M U S I C",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 3.2.sp
                    ),
                    color = ConceptCyanAccent.copy(alpha = 0.88f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "耳畔微风起 · 帧享真无损",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        letterSpacing = 0.8.sp
                    ),
                    color = Color.White.copy(alpha = 0.78f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "Hi-Res 24bit" to VipGold,
                        "64-bit 母带 DSP" to ConceptCyanAccent,
                        "KRC 逐字流光" to ConceptMintAccent
                    ).forEach { (badgeText, accent) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = 0.12f),
                            modifier = Modifier.border(
                                width = 0.7.dp,
                                color = accent.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        ) {
                            Text(
                                text = badgeText,
                                color = accent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                            )
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
