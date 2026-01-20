package com.nidoham.premium.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nidoham.premium.ui.component.root.TopBar

/**
 * Represents a navigation destination within the application.
 *
 * This data class encapsulates all information necessary to configure a bottom
 * navigation item, including its visual representation and accessible label.
 *
 * @property route The unique identifier for this navigation destination.
 * @property selectedIcon The icon displayed when this item is selected.
 * @property unselectedIcon The icon displayed when this item is not selected.
 * @property label The text label displayed below the icon.
 * @property contentDescription Accessibility description for screen readers.
 */
private data class NavigationItem(
    val route: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String,
    val contentDescription: String
)

/**
 * Main application screen implementing bottom navigation.
 *
 * This composable provides the primary navigation structure for the application,
 * featuring a bottom navigation bar with four destinations organized in a clean,
 * modern interface. The implementation follows modern Android design patterns with
 * emphasis on content discovery and user engagement features.
 *
 * The screen maintains navigation state across configuration changes and provides
 * smooth transitions between different sections of the application. The navigation
 * bar uses Material Design 3 components with customized styling to achieve a
 * polished, professional appearance consistent with modern Android design guidelines.
 */
@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val navigationItems = listOf(
        NavigationItem(
            route = 0,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            label = "Home",
            contentDescription = "Navigate to Home feed"
        ),
        NavigationItem(
            route = 1,
            selectedIcon = Icons.Filled.Subscriptions,
            unselectedIcon = Icons.Outlined.Subscriptions,
            label = "Subscribe",
            contentDescription = "Navigate to Subscriptions"
        ),
        NavigationItem(
            route = 2,
            selectedIcon = Icons.Filled.Download,
            unselectedIcon = Icons.Outlined.Download,
            label = "Download",
            contentDescription = "Navigate to Downloads"
        ),
        NavigationItem(
            route = 3,
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
            label = "Me",
            contentDescription = "Navigate to Profile"
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(modifier = Modifier.padding(top = 25.dp)) {
                TopBar()
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                navigationItems.forEach { item ->
                    NavigationBarItem(
                        selected = selectedTab == item.route,
                        onClick = { selectedTab = item.route },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == item.route) {
                                    item.selectedIcon
                                } else {
                                    item.unselectedIcon
                                },
                                contentDescription = item.contentDescription
                            )
                        },
                        label = { Text(text = item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSurface,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> SubscribeScreen()
                2 -> DownloadScreen()
                3 -> MeScreen()
            }
        }
    }
}