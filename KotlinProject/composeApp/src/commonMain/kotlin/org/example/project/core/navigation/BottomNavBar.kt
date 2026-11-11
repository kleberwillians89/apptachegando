package org.example.project.core.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.example.project.core.theme.AppColors

private data class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    isVendor: Boolean
) {
    val items = if (isVendor) {
        listOf(
            BottomNavItem("Início", HomeIcon, NavDestination.VendorDashboard.route),
            BottomNavItem("Rota", RouteIcon, NavDestination.VendorRoute.route),
            BottomNavItem("Clientes", HeatmapIcon, NavDestination.VendorHeatmap.route),
            BottomNavItem("Conta", SettingsIcon, NavDestination.VendorSettings.route),
        )
    } else {
        listOf(
            BottomNavItem("Mapa", LocationOnIcon, NavDestination.ConsumerHome.route),
            BottomNavItem("Buscar", SearchIcon, NavDestination.ConsumerCategories.route),
            BottomNavItem("Avisos", NotificationsIcon, NavDestination.Notifications.route),
            BottomNavItem("Perfil", PersonIcon, NavDestination.ConsumerProfile.route),
        )
    }

    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color.White,
        contentColor = AppColors.Secondary
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = { Text(item.title) },
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        onNavigate(item.route)
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppColors.Primary,
                    selectedTextColor = AppColors.Primary,
                    indicatorColor = AppColors.Primary.copy(alpha = 0.12f),
                    unselectedIconColor = AppColors.OnSurfaceVariant,
                    unselectedTextColor = AppColors.OnSurfaceVariant
                )
            )
        }
    }
}
