package com.concept.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Discover : Screen("discover", "发现", Icons.Rounded.Explore)
    // 兼容别名
    val Recommend get() = Discover
    object Library : Screen("library", "我的", Icons.Rounded.AccountCircle)
}

val bottomNavScreens = listOf(
    Screen.Discover,
    Screen.Library
)
