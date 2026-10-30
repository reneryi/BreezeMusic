package com.concept.music.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptMotion
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.softClickable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 现代流体双态底部导航栏（自适应「幽邃微蓝」深色与「水彩浸润」浅色主题）：
 * 1. 左右切换流体滑块动画：点击切换页面时，底部胶囊指示器呈现带水滴拉伸弹性的左右丝滑位移；
 * 2. 圆润饱满双态自定义图标（两套 UI）：
 *    - 「发现」：未选中态为轻盈圆润星球罗盘线框，选中激活态变形为饱满流光黑胶音律星球（带律动星芒与旋转指针）；
 *    - 「我的」：未选中态为柔和圆润简约人形轮廓，选中激活态变形为头戴饱满圆润大耳机的心流音乐人像（带跃动音符心徽）；
 * 3. 柔和弹性点击反馈：使用 softClickable 彻底消除生硬方形闪烁框。
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val barBg = if (isDark) Color(0xF50B111E) else Color(0xF7F2F7FF)
    val topBorderColor = if (isDark) Color.White.copy(alpha = 0.07f) else ConceptPrimary.copy(alpha = 0.10f)

    val selectedIndex = if (currentRoute == Screen.Library.route) 1 else 0
    val slideProgress by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = ConceptMotion.expressiveSpring(),
        label = "bottomNavSlideProgress"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = barBg,
        tonalElevation = if (isDark) 2.dp else 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(topBorderColor)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val tabCount = bottomNavScreens.size.coerceAtLeast(1)
                val tabSlotWidth = maxWidth / tabCount
                val pillBaseWidth = 108.dp
                // 左右滑动途中产生轻微流体水滴横向拉伸感
                val midStretch = sin(slideProgress.coerceIn(0f, 1f) * PI.toFloat())
                val dynamicPillWidth = pillBaseWidth + (18.dp * midStretch)
                val currentSlotCenter = tabSlotWidth * (slideProgress + 0.5f)
                val pillOffsetX = (currentSlotCenter - dynamicPillWidth / 2).coerceAtLeast(0.dp)

                val pillBrush = if (isDark) {
                    Brush.horizontalGradient(
                        listOf(
                            ConceptPrimary.copy(alpha = 0.24f),
                            ConceptCyanAccent.copy(alpha = 0.18f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            ConceptPrimary.copy(alpha = 0.16f),
                            ConceptCyanAccent.copy(alpha = 0.12f)
                        )
                    )
                }
                val pillBorderColor = if (isDark) {
                    ConceptPrimary.copy(alpha = 0.35f)
                } else {
                    ConceptPrimary.copy(alpha = 0.24f)
                }

                // 底层左右滑动流体胶囊指示器（采用 Canvas 渲染阶段直绘，彻底消除 120Hz 滑动时的 Layout 重排抖动）
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val pillW = dynamicPillWidth.toPx()
                    val pillH = 40.dp.toPx()
                    val leftX = pillOffsetX.toPx().coerceIn(0f, (size.width - pillW).coerceAtLeast(0f))
                    val topY = (size.height - pillH) / 2f
                    val radius = CornerRadius(pillH / 2f, pillH / 2f)
                    drawRoundRect(
                        brush = pillBrush,
                        topLeft = Offset(leftX, topY),
                        size = Size(pillW, pillH),
                        cornerRadius = radius
                    )
                    drawRoundRect(
                        color = pillBorderColor,
                        topLeft = Offset(leftX, topY),
                        size = Size(pillW, pillH),
                        cornerRadius = radius,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // 顶层双态图标与标签交互行
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomNavScreens.forEachIndexed { index, screen ->
                        val isSelected = selectedIndex == index
                        val morphProgress by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = ConceptMotion.expressiveSpring(),
                            label = "iconMorph_$index"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                ConceptPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                            },
                            animationSpec = tween(220),
                            label = "navTextColor_$index"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .softClickable(
                                    pressScale = 0.92f,
                                    pressAlpha = 0.80f,
                                    onClick = { onNavigate(screen.route) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .graphicsLayer {
                                        val s = 1f + 0.10f * morphProgress
                                        scaleX = s
                                        scaleY = s
                                        translationY = -2.dp.toPx() * morphProgress
                                    }
                            ) {
                                if (screen.route == Screen.Discover.route) {
                                    DiscoverDualStateIcon(
                                        progress = morphProgress,
                                        isDark = isDark,
                                        modifier = Modifier.size(26.dp)
                                    )
                                } else {
                                    LibraryDualStateIcon(
                                        progress = morphProgress,
                                        isDark = isDark,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(7.dp))

                                Text(
                                    text = screen.title,
                                    color = textColor,
                                    fontSize = if (isSelected) 13.5.sp else 13.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「发现」双态圆润图标：
 * - 未选中态 (progress = 0f)：圆润饱满的轻盈星球环与罗盘指针线框
 * - 选中态 (progress = 1f)：变形为饱满实心流光音律星球 + 环绕声波轨道 + 旋转星芒核心
 */
@Composable
private fun DiscoverDualStateIcon(
    progress: Float,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val inactiveColor = if (isDark) Color(0xFF8A9BB5) else Color(0xFF647896)
    val activePrimary = ConceptPrimary
    val activeSecondary = ConceptCyanAccent

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = w * 0.36f
        val p = progress.coerceIn(0f, 1f)

        // 1. 选中态外围环绕圆润椭圆星环
        if (p > 0.02f) {
            rotate(degrees = -26f + 12f * p, pivot = center) {
                drawOval(
                    brush = Brush.horizontalGradient(
                        listOf(
                            activeSecondary.copy(alpha = 0.85f * p),
                            activePrimary.copy(alpha = 0.35f * p),
                            activeSecondary.copy(alpha = 0.85f * p)
                        )
                    ),
                    topLeft = Offset(w * 0.05f, h * 0.34f),
                    size = Size(w * 0.90f, h * 0.32f),
                    style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // 2. 主星球球体：从圆润线框平滑过渡为饱满渐变实心球
        if (p > 0.05f) {
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        activePrimary.copy(alpha = p),
                        activeSecondary.copy(alpha = 0.92f * p)
                    ),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                ),
                radius = radius * (0.88f + 0.12f * p),
                center = center
            )
        }

        if (p < 0.98f) {
            drawCircle(
                color = inactiveColor.copy(alpha = 1f - p * 0.85f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 3. 中心探针/星芒：随选中态旋转 45 度并切换为饱满纯白圆润星核
        rotate(degrees = 45f * p, pivot = center) {
            val needleColor = if (p > 0.5f) Color.White else inactiveColor
            val needlePath = Path().apply {
                val len = radius * (0.58f + 0.06f * p)
                val waist = radius * (0.24f + 0.06f * p)
                moveTo(center.x, center.y - len)
                quadraticBezierTo(center.x + waist, center.y - waist, center.x + len, center.y)
                quadraticBezierTo(center.x + waist, center.y + waist, center.x, center.y + len)
                quadraticBezierTo(center.x - waist, center.y + waist, center.x - len, center.y)
                quadraticBezierTo(center.x - waist, center.y - waist, center.x, center.y - len)
                close()
            }
            drawPath(path = needlePath, color = needleColor)
        }

        // 4. 选中态右上角跃动小音律圆点
        if (p > 0.1f) {
            drawCircle(
                color = activeSecondary.copy(alpha = p),
                radius = 2.4.dp.toPx() * p,
                center = Offset(w * 0.83f, h * 0.20f)
            )
        }
    }
}

/**
 * 「我的」双态圆润图标：
 * - 未选中态 (progress = 0f)：圆润饱满的轻盈人像轮廓
 * - 选中态 (progress = 1f)：变形为头戴圆润饱满大耳机的实心人像 + 胸前发光心形徽章
 */
@Composable
private fun LibraryDualStateIcon(
    progress: Float,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val inactiveColor = if (isDark) Color(0xFF8A9BB5) else Color(0xFF647896)
    val activePrimary = ConceptPrimary
    val activeSecondary = ConceptCyanAccent

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val p = progress.coerceIn(0f, 1f)

        val headCenter = Offset(w * 0.5f, h * (0.36f + 0.02f * p))
        val headRadius = w * 0.19f

        // 1. 选中态专属：头顶圆润饱满的头戴式耳机弓与左右耳罩
        if (p > 0.05f) {
            val arcRadius = w * 0.30f
            drawArc(
                brush = Brush.horizontalGradient(
                    listOf(activeSecondary.copy(alpha = p), activePrimary.copy(alpha = p))
                ),
                startAngle = 185f,
                sweepAngle = 170f,
                useCenter = false,
                topLeft = Offset(headCenter.x - arcRadius, headCenter.y - arcRadius * 1.02f),
                size = Size(arcRadius * 2f, arcRadius * 1.95f),
                style = Stroke(width = 2.3.dp.toPx() * p, cap = StrokeCap.Round)
            )
            // 左右饱满胶囊耳罩
            val cupW = 4.2.dp.toPx() * p
            val cupH = 7.6.dp.toPx() * p
            drawRoundRect(
                color = activeSecondary.copy(alpha = p),
                topLeft = Offset(headCenter.x - arcRadius - cupW * 0.45f, headCenter.y - cupH * 0.45f),
                size = Size(cupW, cupH),
                cornerRadius = CornerRadius(cupW / 2f, cupW / 2f)
            )
            drawRoundRect(
                color = activePrimary.copy(alpha = p),
                topLeft = Offset(headCenter.x + arcRadius - cupW * 0.55f, headCenter.y - cupH * 0.45f),
                size = Size(cupW, cupH),
                cornerRadius = CornerRadius(cupW / 2f, cupW / 2f)
            )
        }

        // 2. 头部圆球：线框 -> 饱满渐变实心
        if (p > 0.05f) {
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(activePrimary.copy(alpha = p), activeSecondary.copy(alpha = p))
                ),
                radius = headRadius,
                center = headCenter
            )
        }
        if (p < 0.98f) {
            drawCircle(
                color = inactiveColor.copy(alpha = 1f - p * 0.85f),
                radius = headRadius,
                center = headCenter,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 3. 圆润饱满肩部胶囊底座：线框弧 -> 饱满圆角胶囊实心块
        val bodyTop = h * 0.62f
        val bodyWidth = w * 0.66f
        val bodyHeight = h * 0.27f
        val bodyLeft = (w - bodyWidth) / 2f

        if (p > 0.05f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        activePrimary.copy(alpha = p),
                        activeSecondary.copy(alpha = 0.92f * p)
                    )
                ),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(bodyHeight / 2f, bodyHeight / 2f)
            )
            // 胸前中心纯白圆润微光音律点缀
            drawCircle(
                color = Color.White.copy(alpha = 0.92f * p),
                radius = 2.2.dp.toPx() * p,
                center = Offset(w * 0.5f, bodyTop + bodyHeight * 0.52f)
            )
        }
        if (p < 0.98f) {
            drawRoundRect(
                color = inactiveColor.copy(alpha = 1f - p * 0.85f),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(bodyHeight / 2f, bodyHeight / 2f),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * 平板竖屏与分屏窗口（Medium 宽度）专属「侧边导航栏 (ConceptNavigationRail)」：
 * - 基于 Google 官方 WindowSizeClass 自适应规范，当窗口处于平板竖屏或左右分屏时替代底部导航栏；
 * - 复用圆润饱满双态「发现」与「我的」矢量动效图标，顶部与底部内置快捷搜索与设置入口。
 */
@Composable
fun ConceptNavigationRail(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val railBg = if (isDark) Color(0xF50B111E) else Color(0xF7F2F7FF)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.07f) else ConceptPrimary.copy(alpha = 0.10f)
    val selectedIndex = if (currentRoute == Screen.Library.route) 1 else 0

    Surface(
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight(),
        color = railBg,
        tonalElevation = if (isDark) 2.dp else 4.dp
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 顶部品牌徽章与快捷搜索
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ConceptPrimary.copy(alpha = if (isDark) 0.18f else 0.12f),
                        modifier = Modifier
                            .size(44.dp)
                            .softClickable(onClick = onOpenSearch)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(20.dp)) {
                                val r = size.minDimension * 0.34f
                                val c = Offset(size.width * 0.44f, size.height * 0.44f)
                                drawCircle(
                                    color = ConceptPrimary,
                                    radius = r,
                                    center = c,
                                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawLine(
                                    color = ConceptCyanAccent,
                                    start = Offset(c.x + r * 0.72f, c.y + r * 0.72f),
                                    end = Offset(size.width * 0.84f, size.height * 0.84f),
                                    strokeWidth = 2.4.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }

                // 中央核心双态导航项（优先「发现」，其次「我的」）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    bottomNavScreens.forEachIndexed { index, screen ->
                        val isSelected = selectedIndex == index
                        val morphProgress by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = ConceptMotion.expressiveSpring(),
                            label = "railIconMorph_$index"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                ConceptPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                            },
                            animationSpec = tween(220),
                            label = "railTextColor_$index"
                        )
                        val pillBrush = Brush.verticalGradient(
                            listOf(
                                ConceptPrimary.copy(alpha = if (isDark) 0.24f * morphProgress else 0.16f * morphProgress),
                                ConceptCyanAccent.copy(alpha = if (isDark) 0.18f * morphProgress else 0.12f * morphProgress)
                            )
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(68.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(pillBrush)
                                .softClickable(
                                    pressScale = 0.92f,
                                    pressAlpha = 0.80f,
                                    onClick = { onNavigate(screen.route) }
                                )
                                .padding(vertical = 12.dp, horizontal = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    val s = 1f + 0.10f * morphProgress
                                    scaleX = s
                                    scaleY = s
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                if (screen.route == Screen.Discover.route) {
                                    DiscoverDualStateIcon(
                                        progress = morphProgress,
                                        isDark = isDark,
                                        modifier = Modifier.size(26.dp)
                                    )
                                } else {
                                    LibraryDualStateIcon(
                                        progress = morphProgress,
                                        isDark = isDark,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = screen.title,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 底部快捷设置入口
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    modifier = Modifier
                        .size(40.dp)
                        .softClickable(onClick = onOpenSettings)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val gearColor = MaterialTheme.colorScheme.onSurfaceVariant
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val rOuter = size.minDimension * 0.36f
                            val rInner = size.minDimension * 0.16f
                            drawCircle(gearColor, radius = rOuter, center = c, style = Stroke(width = 1.9.dp.toPx()))
                            drawCircle(ConceptPrimary, radius = rInner, center = c)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .fillMaxHeight()
                    .background(borderColor)
            )
        }
    }
}
