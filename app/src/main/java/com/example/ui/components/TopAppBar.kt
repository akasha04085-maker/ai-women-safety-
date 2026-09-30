package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserRole
import com.example.ui.theme.Primary
import com.example.ui.theme.Secondary

@Composable
fun TacticalTopAppBar(
    title: String,
    subtitle: String = "GPS Accuracy: ±3m • Realtime",
    showBack: Boolean = false,
    onBackClick: () -> Unit = {},
    currentUserRole: UserRole = UserRole.CITIZEN,
    onDemoClick: (() -> Unit)? = null,
    onPairDevicesClick: (() -> Unit)? = null,
    onSwitchRoleClick: (() -> Unit)? = null,
    isP2pConnected: Boolean = false,
    onProfileClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gpsPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tactical_top_app_bar"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (showBack) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .testTag("top_bar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                } else {
                    // Tactically styled Role Emblem
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when (currentUserRole) {
                                    UserRole.CITIZEN -> Primary.copy(alpha = 0.12f)
                                    UserRole.RESPONDER_PATROL -> Color(0xFF0284C7).copy(alpha = 0.12f)
                                    UserRole.COMMAND_CENTER -> Color(0xFF7C3AED).copy(alpha = 0.12f)
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = when (currentUserRole) {
                                    UserRole.CITIZEN -> Primary.copy(alpha = 0.3f)
                                    UserRole.RESPONDER_PATROL -> Color(0xFF0284C7).copy(alpha = 0.3f)
                                    UserRole.COMMAND_CENTER -> Color(0xFF7C3AED).copy(alpha = 0.3f)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSwitchRoleClick?.invoke() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (currentUserRole) {
                                UserRole.CITIZEN -> Icons.Default.Security
                                UserRole.RESPONDER_PATROL -> Icons.Default.LocalPolice
                                UserRole.COMMAND_CENTER -> Icons.Default.CrisisAlert
                            },
                            contentDescription = "Role Emblem",
                            tint = when (currentUserRole) {
                                UserRole.CITIZEN -> Primary
                                UserRole.RESPONDER_PATROL -> Color(0xFF0284C7)
                                UserRole.COMMAND_CENTER -> Color(0xFF7C3AED)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Role Tag Chip
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (currentUserRole) {
                                UserRole.CITIZEN -> Primary
                                UserRole.RESPONDER_PATROL -> Color(0xFF0284C7)
                                UserRole.COMMAND_CENTER -> Color(0xFF7C3AED)
                            },
                            modifier = Modifier
                                .clickable { onSwitchRoleClick?.invoke() }
                                .testTag("top_bar_role_badge")
                        ) {
                            Text(
                                text = when (currentUserRole) {
                                    UserRole.CITIZEN -> "CITIZEN"
                                    UserRole.RESPONDER_PATROL -> "PATROL"
                                    UserRole.COMMAND_CENTER -> "DISPATCH"
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = Color.White
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Pulsing GPS Satellite Dot
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    (if (isP2pConnected) Secondary else Color(0xFF10B981)).copy(
                                        alpha = pulseAlpha
                                    )
                                )
                        )
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = if (isP2pConnected) Secondary else Color(0xFF10B981),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action Tray
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Demo SOS Trigger Quick Pill
                if (onDemoClick != null) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDemoClick() }
                            .testTag("btn_top_bar_demo_sos"),
                        color = Primary.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CrisisAlert,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Test SOS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                ),
                                color = Primary
                            )
                        }
                    }
                }

                // 2-Device Sync Indicator / Launcher
                if (onPairDevicesClick != null) {
                    IconButton(
                        onClick = onPairDevicesClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isP2pConnected) Secondary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .testTag("btn_top_bar_sync")
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = if (isP2pConnected) Icons.Default.WifiTethering else Icons.Default.Sync,
                                contentDescription = "2-Device Sync",
                                tint = if (isP2pConnected) Secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            if (isP2pConnected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Secondary)
                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                )
                            }
                        }
                    }
                }

                // Profile Avatar / Role Switcher
                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .testTag("btn_top_bar_profile")
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Profile & Role Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
