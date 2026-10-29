package com.concept.music.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
 * 采用无边框纯净深海柔光设计，渲染 `@drawable/ic_launcher_concept` 高精度矢量图标与弥散光晕，
 * 在「设置 -> 关于微风音乐」中展示，保持全应用品牌视觉高度一致。
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
