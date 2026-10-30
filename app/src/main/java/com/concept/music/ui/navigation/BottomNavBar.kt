package com.concept.music.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import com.concept.music.ui.theme.LocalConceptAdaptiveInfo
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.softClickable
import kotlin.math.PI
import kotlin.math.sin

/**
 * 现代流体双态底部导航栏（自适应「幽邃微蓝」深色与「水彩浸润」浅色主题）：
 * 1. 平板竖屏或分屏窗口（`isLargeScreenDevice == true`）：
 *    去掉左侧边栏，采用与悬浮播放控制条（`MiniPlayerBar`）完全同源的「底部居中悬浮胶囊（Floating Capsule）」设计，
 *    限制合适宽度并带柔和立体阴影悬浮于屏幕底部中央；
 * 2. 手机端（`isLargeScreenDevice == false`）：
 *    保持贴底全宽流体导航栏；
 * 3. 左右切换流体滑块动画：点击切换页面时，胶囊指示器呈现带水滴拉伸弹性的左右丝滑位移；
 * 4. 圆润饱满双态自定义图标（「发现」星轨罗盘 + 「我的」耳机人像）。
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val adaptiveInfo = LocalConceptAdaptiveInfo.current
    val isFloatingCapsule = adaptiveInfo.isLargeScreenDevice

    val selectedIndex = if (currentRoute == Screen.Library.route) 1 else 0
    val slideProgress by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = ConceptMotion.expressiveSpring(),
        label = "bottomNavSlideProgress"
    )

    if (isFloatingCapsule) {
        // 平板竖屏或分屏窗口：将「发现 / 我的」悬浮胶囊导航栏放置于屏幕左侧垂直居中（仅保留发现与我的，无多余按钮）
        val capsuleShape = RoundedCornerShape(32.dp)
        val capsuleBgColor = if (isDark) Color(0xFF141E30) else Color(0xFFF7FAFF)
        val capsuleBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color(0xF2162236),
                    Color(0xF2131C2D),
                    Color(0xF2172338)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(0xFAFFFFFF),
                    Color(0xF8F4F9FF),
                    Color(0xFAFFFFFF)
                )
            )
        }
        val capsuleBorderColor = if (isDark) {
            Color.White.copy(alpha = 0.09f)
        } else {
            ConceptPrimary.copy(alpha = 0.14f)
        }

        Box(
            modifier = modifier
                .wrapContentSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 12.dp, top = 16.dp, bottom = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                shape = capsuleShape,
                color = capsuleBgColor,
                shadowElevation = if (isDark) 14.dp else 7.dp,
                modifier = Modifier
                    .width(64.dp)
                    .height(152.dp)
                    .border(width = 0.8.dp, color = capsuleBorderColor, shape = capsuleShape)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(capsuleBrush)
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    VerticalFloatingNavInteractiveContent(
                        maxHeight = maxHeight,
                        selectedIndex = selectedIndex,
                        slideProgress = slideProgress,
                        isDark = isDark,
                        pillBaseHeight = 62.dp,
                        onNavigate = onNavigate
                    )
                }
            }
        }
    } else {
        val barBg = if (isDark) Color(0xF50B111E) else Color(0xF7F2F7FF)
        val topBorderColor = if (isDark) Color.White.copy(alpha = 0.07f) else ConceptPrimary.copy(alpha = 0.10f)

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
                    BottomNavInteractiveContent(
                        maxWidth = maxWidth,
                        selectedIndex = selectedIndex,
                        slideProgress = slideProgress,
                        isDark = isDark,
                        pillBaseWidth = 108.dp,
                        onNavigate = onNavigate
                    )
                }
            }
        }
    }
}


@Composable
private fun VerticalFloatingNavInteractiveContent(
    maxHeight: androidx.compose.ui.unit.Dp,
    selectedIndex: Int,
    slideProgress: Float,
    isDark: Boolean,
    pillBaseHeight: androidx.compose.ui.unit.Dp,
    onNavigate: (String) -> Unit
) {
    val tabCount = bottomNavScreens.size.coerceAtLeast(1)
    val tabSlotHeight = maxHeight / tabCount
    val midStretch = sin(slideProgress.coerceIn(0f, 1f) * PI.toFloat())
    val dynamicPillHeight = pillBaseHeight + (10.dp * midStretch)
    val currentSlotCenterY = tabSlotHeight * (slideProgress + 0.5f)
    val pillOffsetY = (currentSlotCenterY - dynamicPillHeight / 2).coerceAtLeast(0.dp)

    val pillBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                ConceptPrimary.copy(alpha = 0.24f),
                ConceptCyanAccent.copy(alpha = 0.18f)
            )
        )
    } else {
        Brush.verticalGradient(
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

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val pillW = size.width
        val pillH = dynamicPillHeight.toPx().coerceAtMost(size.height)
        val leftX = 0f
        val topY = pillOffsetY.toPx().coerceIn(0f, (size.height - pillH).coerceAtLeast(0f))
        val radius = CornerRadius(pillW / 2f, pillW / 2f)
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

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        bottomNavScreens.forEachIndexed { index, screen ->
            val isSelected = selectedIndex == index
            val morphProgress by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = ConceptMotion.expressiveSpring(),
                label = "verticalIconMorph_$index"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) {
                    ConceptPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                },
                animationSpec = tween(220),
                label = "verticalNavTextColor_$index"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .softClickable(
                        pressScale = 0.92f,
                        pressAlpha = 0.80f,
                        onClick = { onNavigate(screen.route) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.graphicsLayer {
                        val s = 1f + 0.08f * morphProgress
                        scaleX = s
                        scaleY = s
                    }
                ) {
                    if (screen.route == Screen.Discover.route) {
                        DiscoverDualStateIcon(
                            progress = morphProgress,
                            isDark = isDark,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        LibraryDualStateIcon(
                            progress = morphProgress,
                            isDark = isDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = screen.title,
                        color = textColor,
                        fontSize = if (isSelected) 12.sp else 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavInteractiveContent(
    maxWidth: androidx.compose.ui.unit.Dp,
    selectedIndex: Int,
    slideProgress: Float,
    isDark: Boolean,
    pillBaseWidth: androidx.compose.ui.unit.Dp,
    onNavigate: (String) -> Unit
) {
    val tabCount = bottomNavScreens.size.coerceAtLeast(1)
    val tabSlotWidth = maxWidth / tabCount
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
        val pillW = dynamicPillWidth.toPx().coerceAtMost(size.width)
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
