package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Quickreply
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserRole
import com.example.ui.theme.Primary

enum class NavDestination(val label: String, val icon: ImageVector, val tag: String) {
    HOME("SOS Home", Icons.Default.Emergency, "nav_tab_home"),
    HUBS("Nearby Hubs", Icons.Default.Map, "nav_tab_hubs"),
    ROUTES("Routes", Icons.Default.AltRoute, "nav_tab_routes"),
    CONTACTS("Allies", Icons.Default.Quickreply, "nav_tab_contacts"),
    PATROL("Patrol Duty", Icons.Default.LocalPolice, "nav_tab_patrol"),
    COMMAND("Command", Icons.Default.CellTower, "nav_tab_command"),
    SYNC("2-Device Link", Icons.Default.Sync, "nav_tab_sync")
}

@Composable
fun TacticalBottomNavBar(
    selectedDestination: NavDestination,
    currentUserRole: UserRole = UserRole.CITIZEN,
    onDestinationSelected: (NavDestination) -> Unit
) {
    val roleDestinations = when (currentUserRole) {
        UserRole.CITIZEN -> listOf(
            NavDestination.HOME,
            NavDestination.HUBS,
            NavDestination.ROUTES,
            NavDestination.CONTACTS,
            NavDestination.SYNC
        )
        UserRole.RESPONDER_PATROL -> listOf(
            NavDestination.PATROL,
            NavDestination.HUBS,
            NavDestination.COMMAND,
            NavDestination.SYNC
        )
        UserRole.COMMAND_CENTER -> listOf(
            NavDestination.COMMAND,
            NavDestination.HUBS,
            NavDestination.PATROL,
            NavDestination.SYNC
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tactical_bottom_navigation_bar"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            roleDestinations.forEach { destination ->
                val isSelected = selectedDestination == destination
                val contentColor = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 28.dp),
                            onClick = { onDestinationSelected(destination) }
                        )
                        .padding(vertical = 6.dp)
                        .testTag(destination.tag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = fontWeight
                        ),
                        color = contentColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
