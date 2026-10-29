package com.concept.music.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.concept.music.BuildConfig
import com.concept.music.R
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptPrimaryLight
import com.concept.music.ui.theme.WatercolorBlueWash
import com.concept.music.ui.theme.isConceptDarkTheme

/**
 * 冷启动图标页额外驻留时长（0ms）：
 * 零额外人为阻塞，首帧渲染完成即刻平滑过渡进入主界面。
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
        rawVersionName = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V").ifBlank { "1.0.2" },
        versionCode = BuildConfig.VERSION_CODE.toLong().coerceAtLeast(1L)
    )
}

/**
 * 微风音乐 (BreezeMusic) 统一品牌应用图标组件：
 * 支持明暗两套配色体系：
 * - 深色模式：幽邃微蓝暗夜基底，深海蓝光感图标；
 * - 浅色模式：水彩浸润清透底色，水彩浅色品牌图标配合柔光投影。
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
    val iconRes = if (isDark) R.drawable.ic_launcher_concept else R.drawable.ic_splash_icon_light

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
                painter = painterResource(id = iconRes),
                contentDescription = "BreezeMusic 微风音乐图标",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}


/**
 * 微风音乐 (BreezeMusic) 独立纯净启动图标开屏页面：
 * - 纯净呈现中间品牌图标，绝无任何音律跳动效果；
 * - 页面底色与图标完全独立绑定于 App 内的主题偏好（深色「幽邃微蓝」vs 浅色「水彩浸润」），不受手机系统日夜模式影响；
 * - 极速轻量（默认停留约 850ms，支持轻触即刻跳过），240ms 自然平滑淡出，全链路色彩零色差统一。
 */
@Composable
fun PureSplashIconView(
    visible: Boolean,
    isDark: Boolean = isConceptDarkTheme(),
    onDismiss: () -> Unit = {}
) {
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(850L)
            onDismiss()
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.EnterTransition.None,
        exit = androidx.compose.animation.fadeOut(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 240,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        )
    ) {
        val bgColor = if (isDark) Color(0xFF0A0E17) else Color(0xFFFFF2F6FC)
        val iconRes = if (isDark) R.drawable.ic_launcher_concept else R.drawable.ic_splash_icon_light

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = "BreezeMusic 图标",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
        }
    }
}
