package com.concept.music.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val label: String, val desc: String) {
    DARK_ABYSS_BLUE("幽邃微蓝", "深邃暗夜基底，透出克制内敛的微泛蓝光感"),
    LIGHT_WATERCOLOR("水彩浸润", "酷狗蓝主调水彩晕染，融合薄荷青与微醺紫粉点缀"),
    SYSTEM("跟随系统", "随系统日夜设置自动切换幽邃微蓝与水彩浸润");

    val subtitle: String get() = desc
}

object ThemeManager {
    private var prefs: SharedPreferences? = null
    private val _themeMode = MutableStateFlow(AppThemeMode.DARK_ABYSS_BLUE)
    val themeMode = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColor = _dynamicColor.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("concept_theme_prefs", Context.MODE_PRIVATE)
        prefs = p
        val savedMode = p.getString("theme_mode", AppThemeMode.DARK_ABYSS_BLUE.name) ?: AppThemeMode.DARK_ABYSS_BLUE.name
        _themeMode.value = runCatching { AppThemeMode.valueOf(savedMode) }.getOrDefault(AppThemeMode.DARK_ABYSS_BLUE)
        _dynamicColor.value = p.getBoolean("dynamic_color", false)
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString("theme_mode", mode.name)?.apply()
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        init(context)
        setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs?.edit()?.putBoolean("dynamic_color", enabled)?.apply()
    }
}

val LocalIsDarkTheme = staticCompositionLocalOf { true }

private val DarkColorScheme = darkColorScheme(
    primary = ConceptPrimary,
    onPrimary = Color(0xFF041830),
    primaryContainer = Color(0xFF132E52),
    onPrimaryContainer = ConceptPrimaryContainer,
    secondary = ConceptCyanAccent,
    secondaryContainer = Color(0xFF172D3E),
    onSecondaryContainer = ConceptSecondaryContainer,
    background = ConceptBackgroundDark,
    surface = ConceptSurfaceDark,
    surfaceVariant = ConceptSurfaceVariantDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = ConceptPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = ConceptPrimaryContainer,
    onPrimaryContainer = ConceptOnPrimaryContainer,
    secondary = ConceptSecondary,
    secondaryContainer = ConceptSecondaryContainer,
    onSecondaryContainer = Color(0xFF0A2E4D),
    background = ConceptBackgroundLight,
    surface = ConceptSurfaceLight,
    surfaceVariant = ConceptSurfaceVariantLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight
)

@Composable
fun ConceptMusicTheme(
    darkThemeOverride: Boolean? = null,
    dynamicColorOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    ThemeManager.init(context)
    val mode by ThemeManager.themeMode.collectAsState()
    val dynColor by ThemeManager.dynamicColor.collectAsState()

    val sysDark = isSystemInDarkTheme()
    val isDark = darkThemeOverride ?: when (mode) {
        AppThemeMode.DARK_ABYSS_BLUE -> true
        AppThemeMode.LIGHT_WATERCOLOR -> false
        AppThemeMode.SYSTEM -> sysDark
    }
    val useDynamic = dynamicColorOverride ?: dynColor

    val colorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * 全局氛围画布容器（采用 drawWithCache 硬件着色器缓存，彻底消除滚动时重复创建 Brush 导致的帧率卡顿）：
 * - 深色模式（幽邃微蓝）：在深海暗夜基底 (#0A0E17) 上叠加极克制、隐约泛蓝的深邃幽微光晕，蓝色绝不刺眼；
 * - 浅色模式（水彩浸润）：在宣纸蓝白基底 (#F3F8FF) 上以酷狗蓝为主调晕染水粉青、微醺淡紫粉与暖杏点缀，呈现水彩浸润质感。
 */
@Composable
fun ConceptStageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val baseBg = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                if (isDark) {
                    val topGlow = Brush.radialGradient(
                        colors = listOf(
                            AbyssalBlueGlowPrimary.copy(alpha = 0.50f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.22f, h * 0.10f),
                        radius = w * 0.90f
                    )
                    val bottomGlow = Brush.radialGradient(
                        colors = listOf(
                            AbyssalBlueGlowSecondary.copy(alpha = 0.42f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.82f, h * 0.78f),
                        radius = w * 1.05f
                    )
                    onDrawBehind {
                        drawRect(color = baseBg)
                        drawRect(brush = topGlow)
                        drawRect(brush = bottomGlow)
                    }
                } else {
                    val blueWash = Brush.radialGradient(
                        colors = listOf(
                            WatercolorBlueWash.copy(alpha = 0.24f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.18f, h * 0.12f),
                        radius = w * 0.88f
                    )
                    val lavenderWash = Brush.radialGradient(
                        colors = listOf(
                            WatercolorLavenderAccent.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.88f, h * 0.36f),
                        radius = w * 0.76f
                    )
                    val cyanPeachWash = Brush.radialGradient(
                        colors = listOf(
                            WatercolorCyanWash.copy(alpha = 0.16f),
                            WatercolorPeachAccent.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.32f, h * 0.76f),
                        radius = w * 0.96f
                    )
                    onDrawBehind {
                        drawRect(color = baseBg)
                        drawRect(brush = blueWash)
                        drawRect(brush = lavenderWash)
                        drawRect(brush = cyanPeachWash)
                    }
                }
            },
        content = content
    )
}
