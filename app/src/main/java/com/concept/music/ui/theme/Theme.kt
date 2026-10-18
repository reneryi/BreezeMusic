package com.concept.music.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val label: String, val desc: String) {
    DARK_ABYSS_BLUE("幽邃微蓝", "深邃暗夜基底，透出克制内敛的微泛蓝光感"),
    LIGHT_WATERCOLOR("水彩浸润", "酷狗蓝主调水彩晕染，融合纯白圆润悬浮卡片质感"),
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

/**
 * 便捷获取当前概念版主题是否处于深色状态
 */
@Composable
fun isConceptDarkTheme(): Boolean = LocalIsDarkTheme.current

// 全局圆润饱满几何形状系统
val ConceptShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = ConceptPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF16325B),
    onPrimaryContainer = ConceptPrimaryContainer,
    secondary = ConceptCyanAccent,
    secondaryContainer = Color(0xFF172D3E),
    onSecondaryContainer = ConceptSecondaryContainer,
    background = ConceptBackgroundDark,
    surface = ConceptSurfaceDark,
    surfaceVariant = ConceptSurfaceVariantDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF2D3B55),
    outlineVariant = Color(0xFF1F2B40)
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
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFCBD8EA),
    outlineVariant = Color(0xFFDCE6F4)
)

/**
 * 沉浸式无边框悬浮卡片背景色：
 * - 深色模式：深海微蓝悬浮块 (#141C2B)
 * - 浅色模式：高透纯白圆润悬浮块 (#F7FFFFFF)，依靠柔和阴影与浅蓝水彩底色自然分层，无需生硬边框线
 */
@Composable
fun conceptFloatingCardColor(): Color {
    return if (LocalIsDarkTheme.current) ConceptCardDark else Color(0xF7FFFFFF)
}

/**
 * 次级胶囊/内嵌容器背景色：
 * - 深色模式：#1C263B
 * - 浅色模式：#E8F1FC
 */
@Composable
fun conceptElevatedSurfaceColor(): Color {
    return if (LocalIsDarkTheme.current) ConceptElevatedCardDark else ConceptElevatedCardLight
}

/**
 * 底部抽屉与弹窗容器背景色
 */
@Composable
fun conceptSheetContainerColor(): Color {
    return if (LocalIsDarkTheme.current) Color(0xFF111826) else Color(0xFFF8FBFF)
}

/**
 * 柔和唯美无框点击反馈修饰符：
 * - 彻底消除生硬突兀的默认方形水波纹闪烁框（indication = null）
 * - 代之以轻盈的弹性按压微缩放与柔和透明度呼吸过渡，贴合全局柔和唯美风格
 */
fun Modifier.softClickable(
    enabled: Boolean = true,
    pressScale: Float = 0.94f,
    pressAlpha: Float = 0.72f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "softClickScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressAlpha else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "softClickAlpha"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * 柔和圆润卡片/列表项点击反馈修饰符：
 * - 裁剪为圆润几何边界，按压时呈现轻微弹性内缩与柔和光泽，无生硬方框闪烁
 */
fun Modifier.softCardClickable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp),
    pressScale: Float = 0.98f,
    pressAlpha: Float = 0.86f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "softCardScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressAlpha else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "softCardAlpha"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 当全屏深色沉浸式层（如全屏播放页）覆盖在浅色主题之上时，动态同步系统状态栏与导航栏图标为浅色亮字，
 * 关闭后自动恢复当前主题对应的状态栏图标明暗度。
 */
@Composable
fun SystemBarAppearanceEffect(forceDarkBars: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDarkTheme = LocalIsDarkTheme.current
    if (!view.isInEditMode) {
        DisposableEffect(forceDarkBars, isDarkTheme) {
            val window = context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val effectiveDark = forceDarkBars || isDarkTheme
            controller?.isAppearanceLightStatusBars = !effectiveDark
            controller?.isAppearanceLightNavigationBars = !effectiveDark
            onDispose {
                controller?.isAppearanceLightStatusBars = !isDarkTheme
                controller?.isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }
}

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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ConceptShapes,
            content = content
        )
    }
}

/**
 * 全局氛围画布容器（采用 drawWithCache 硬件着色器缓存）：
 * - 深色模式（幽邃微蓝）：在深海暗夜基底 (#0A0E17) 上叠加极克制、隐约泛蓝的深邃幽微光晕；
 * - 浅色模式（水彩浸润）：在云端蓝白基底 (#F2F6FC) 上以酷狗蓝为主调晕染水粉青、微醺淡紫粉点缀，衬托纯白无边框悬浮卡片。
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
                            WatercolorBlueWash.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.18f, h * 0.10f),
                        radius = w * 0.88f
                    )
                    val lavenderWash = Brush.radialGradient(
                        colors = listOf(
                            WatercolorLavenderAccent.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.88f, h * 0.36f),
                        radius = w * 0.76f
                    )
                    val cyanPeachWash = Brush.radialGradient(
                        colors = listOf(
                            WatercolorCyanWash.copy(alpha = 0.12f),
                            WatercolorPeachAccent.copy(alpha = 0.08f),
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
