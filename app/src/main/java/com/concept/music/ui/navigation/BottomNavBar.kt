package com.concept.music.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.concept.music.ui.theme.ConceptPrimary

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 0.6.dp,
            color = Color.White.copy(alpha = 0.08f)
        )
        NavigationBar(
            containerColor = Color(0xEB12151C),
            tonalElevation = 0.dp
        ) {
            bottomNavScreens.forEach { screen ->
                val isSelected = currentRoute == screen.route
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onNavigate(screen.route) },
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title
                        )
                    },
                    label = { Text(screen.title) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = ConceptPrimary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
