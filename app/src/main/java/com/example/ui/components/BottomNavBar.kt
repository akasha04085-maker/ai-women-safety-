package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LocalPolice
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserRole
import com.example.ui.theme.Primary
import com.example.ui.theme.Secondary

enum class NavDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String,
    val hasBadge: Boolean = false,
    val badgeColor: Color = Primary
) {
    HOME("SOS Home", Icons.Filled.Emergency, Icons.Outlined.Emergency, "nav_tab_home", true, Primary),
    HUBS("Hubs Map", Icons.Filled.Explore, Icons.Outlined.Explore, "nav_tab_hubs", true, Color(0xFF10B981)),
    ROUTES("Safe Routes", Icons.Filled.AltRoute, Icons.Outlined.AltRoute, "nav_tab_routes"),
    CONTACTS("Allies", Icons.Filled.People, Icons.Outlined.People, "nav_tab_contacts"),
    PATROL("Patrol Radar", Icons.Filled.LocalPolice, Icons.Outlined.LocalPolice, "nav_tab_patrol", true, Color(0xFF0284C7)),
    COMMAND("Command", Icons.Filled.CellTower, Icons.Outlined.CellTower, "nav_tab_command"),
    SYNC("Link", Icons.Filled.Sensors, Icons.Outlined.Sensors, "nav_tab_sync")
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 10.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            roleDestinations.forEach { destination ->
                val isSelected = selectedDestination == destination

                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "iconColor"
                )

                val pillBackground by animateColorAsState(
                    targetValue = if (isSelected) Primary.copy(alpha = 0.12f) else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "pillBg"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 32.dp),
                            onClick = { onDestinationSelected(destination) }
                        )
                        .padding(vertical = 4.dp)
                        .testTag(destination.tag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(pillBackground)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )

                            if (destination.hasBadge && !isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(destination.badgeColor)
                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                )
                            }
                        }
                    }

                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            letterSpacing = 0.1.sp
                        ),
                        color = iconColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
