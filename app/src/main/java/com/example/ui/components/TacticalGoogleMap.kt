package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PersonPinCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.location.GpsCoordinate
import com.example.model.UserRole
import com.example.ui.theme.InverseSurface
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SecondaryFixed
import com.example.ui.theme.WarningAmber
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

@Composable
fun TacticalGoogleMap(
    modifier: Modifier = Modifier,
    victimLocation: GpsCoordinate,
    patrolLocation: GpsCoordinate?,
    showSafeCorridors: Boolean = true,
    etaText: String = "3.5 mins (1.1 km)",
    isUserSpeaking: Boolean = false,
    isPeerSpeaking: Boolean = false,
    userAudioLevel: Float = 0f,
    peerAudioLevel: Float = 0f,
    activeSpeakerName: String? = null,
    currentUserRole: UserRole = UserRole.CITIZEN,
    onRecenterClick: () -> Unit = {}
) {
    val victimLatLng = remember(victimLocation.latitude, victimLocation.longitude) {
        LatLng(victimLocation.latitude, victimLocation.longitude)
    }

    val patrolLatLng = remember(patrolLocation?.latitude, patrolLocation?.longitude) {
        if (patrolLocation != null) {
            LatLng(patrolLocation.latitude, patrolLocation.longitude)
        } else {
            LatLng(victimLocation.latitude + 0.0085, victimLocation.longitude + 0.0072)
        }
    }

    // Secondary patrol unit in sector
    val nearbyPatrolLatLng = remember(victimLocation) {
        LatLng(victimLocation.latitude - 0.0062, victimLocation.longitude + 0.0048)
    }

    // Safe Haven Police Station
    val policeStationLatLng = remember(victimLocation) {
        LatLng(victimLocation.latitude + 0.0042, victimLocation.longitude - 0.0055)
    }

    // Civil Hospital Trauma Desk
    val hospitalLatLng = remember(victimLocation) {
        LatLng(victimLocation.latitude - 0.0075, victimLocation.longitude - 0.0035)
    }

    // Safest well-lit navigation route waypoints
    val safeRouteWaypoints = remember(victimLatLng, patrolLatLng) {
        listOf(
            patrolLatLng,
            LatLng(patrolLatLng.latitude - 0.0025, patrolLatLng.longitude - 0.0018),
            LatLng(victimLatLng.latitude + 0.0030, victimLatLng.longitude + 0.0022),
            LatLng(victimLatLng.latitude + 0.0012, victimLatLng.longitude + 0.0008),
            victimLatLng
        )
    }

    // Alternative safe corridor
    val altSafeRouteWaypoints = remember(victimLatLng, policeStationLatLng) {
        listOf(
            victimLatLng,
            LatLng(victimLatLng.latitude + 0.0020, victimLatLng.longitude - 0.0022),
            policeStationLatLng
        )
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(victimLatLng, 15f)
    }

    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var followSpeakerMode by remember { mutableStateOf(true) }

    // Pulsing transition for victim beacon
    val infiniteTransition = rememberInfiniteTransition(label = "markerPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "markerPulseScale"
    )

    // Voice acoustic wave pulse animation for the active speaker
    val voiceRippleScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voiceRipple"
    )
    val voiceRippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voiceRippleAlpha"
    )

    // Auto-center camera on speaker if someone is speaking and followSpeakerMode is on
    LaunchedEffect(isUserSpeaking, isPeerSpeaking, victimLatLng, patrolLatLng, followSpeakerMode) {
        if (followSpeakerMode) {
            if (isUserSpeaking) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(victimLatLng, 16.5f),
                    durationMs = 500
                )
            } else if (isPeerSpeaking) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(patrolLatLng, 16.5f),
                    durationMs = 500
                )
            } else {
                try {
                    val bounds = LatLngBounds.builder()
                        .include(victimLatLng)
                        .include(patrolLatLng)
                        .include(policeStationLatLng)
                        .build()
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(bounds, 120),
                        durationMs = 800
                    )
                } catch (e: Exception) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(victimLatLng, 14.8f)
                }
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tactical_google_map_container"),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = mapType,
                    isMyLocationEnabled = false,
                    isTrafficEnabled = true
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = true,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false
                )
            ) {
                // 1. Victim Marker (Ananya S. - High Distress SOS Beacon)
                MarkerComposable(
                    state = MarkerState(position = victimLatLng),
                    title = "Your Location (Victim Beacon)",
                    snippet = "Broadcasting GPS • High Urgency SOS"
                ) {
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Expanding sonic ripples if Citizen is speaking
                        if (isUserSpeaking && currentUserRole == UserRole.CITIZEN || isPeerSpeaking && currentUserRole != UserRole.CITIZEN) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .scale(voiceRippleScale)
                                    .clip(CircleShape)
                                    .background(Primary.copy(alpha = voiceRippleAlpha * 0.7f))
                            )
                        }

                        // Normal GPS pulse
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Primary.copy(alpha = 0.45f))
                        )
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Primary)
                                .border(2.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isUserSpeaking) Icons.Default.GraphicEq else Icons.Default.PersonPinCircle,
                                contentDescription = "Victim Location",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 2. Connected Patrol Unit Marker (Officer Vikram Singh - Scorpio-4)
                MarkerComposable(
                    state = MarkerState(position = patrolLatLng),
                    title = "Officer Vikram Singh",
                    snippet = "Patrol Unit Scorpio-4 • Speed: ${String.format(Locale.US, "%.0f", patrolLocation?.speedKmh ?: 38f)} km/h"
                ) {
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Expanding sonic waves if Patrol is speaking
                        if (isUserSpeaking && currentUserRole == UserRole.RESPONDER_PATROL || isPeerSpeaking && currentUserRole != UserRole.RESPONDER_PATROL) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .scale(voiceRippleScale)
                                    .clip(CircleShape)
                                    .background(Secondary.copy(alpha = voiceRippleAlpha * 0.8f))
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Secondary.copy(alpha = 0.35f))
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Secondary)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPeerSpeaking || (isUserSpeaking && currentUserRole == UserRole.RESPONDER_PATROL)) Icons.Default.GraphicEq else Icons.Default.DirectionsCar,
                                contentDescription = "Assigned Patrol Unit",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 3. Secondary Patrol Unit (Patrol Scorpio-7 in standby)
                MarkerComposable(
                    state = MarkerState(position = nearbyPatrolLatLng),
                    title = "Patrol Scorpio-7 (Backup)",
                    snippet = "Sector 28 Grid Standby"
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalPolice,
                            contentDescription = "Backup Patrol",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // 4. Safe Haven Police Station
                Marker(
                    state = MarkerState(position = policeStationLatLng),
                    title = "Sector 28 Police Station (Safe Haven)",
                    snippet = "24/7 Armed Guard Desk • 350m Away",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )

                // 5. Civil Hospital Trauma Desk
                Marker(
                    state = MarkerState(position = hospitalLatLng),
                    title = "Civil Hospital 24/7 Trauma Desk",
                    snippet = "Level-1 Trauma Care • Emergency ER",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )

                // 6. Pink Booth Women Safe Haven
                val pinkBoothLatLng = LatLng(victimLocation.latitude + 0.0019, victimLocation.longitude + 0.0022)
                Marker(
                    state = MarkerState(position = pinkBoothLatLng),
                    title = "Pink Booth Women Safe Haven",
                    snippet = "24/7 Guarded Haven • Direct SOS Intercom",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
                )

                // 5. Active Safest Intercept Polyline
                Polyline(
                    points = safeRouteWaypoints,
                    color = Primary,
                    width = 12f,
                    geodesic = true
                )

                // 6. Alternative Safe Corridor (Cyan well-lit road)
                if (showSafeCorridors) {
                    Polyline(
                        points = altSafeRouteWaypoints,
                        color = Secondary,
                        width = 8f,
                        geodesic = true
                    )
                }
            }

            // Fallback Vector Canvas Map when tiles are loading/rendering in container
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("tactical_map_canvas_overlay")
            ) {
                // Subtle tactical radar range circles centered on victim
                val center = Offset(size.width * 0.45f, size.height * 0.65f)
                drawCircle(
                    color = Primary.copy(alpha = 0.05f),
                    radius = size.width * 0.35f,
                    center = center
                )
            }

            // Top Floating Live Telemetry & Audio Channel HUD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ETA Chip
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = InverseSurface.copy(alpha = 0.90f),
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                tint = SecondaryFixed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "ETA: $etaText",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                ),
                                color = Color.White
                            )
                        }
                    }

                    // Map controls (Follow Speaker & Layer Toggle)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = if (followSpeakerMode) SecondaryContainer else InverseSurface.copy(alpha = 0.85f),
                            modifier = Modifier.clickable { followSpeakerMode = !followSpeakerMode }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Follow Speaker",
                                    tint = if (followSpeakerMode) MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (followSpeakerMode) "Tracking Speaker" else "Free Map",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (followSpeakerMode) MaterialTheme.colorScheme.onSecondaryContainer else Color.White
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = InverseSurface.copy(alpha = 0.85f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            IconButton(onClick = {
                                mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = "Change Map Layer",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Active Live Voice Channel Banner
                AnimatedVisibility(
                    visible = isUserSpeaking || isPeerSpeaking,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isUserSpeaking) PrimaryContainer.copy(alpha = 0.95f) else SecondaryContainer.copy(alpha = 0.95f),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isUserSpeaking) "🎙️ You are speaking live..." else "🎙️ $activeSpeakerName speaking...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "GPS 1Hz SYNCED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // Bottom Right Floating Recenter Button
            IconButton(
                onClick = {
                    onRecenterClick()
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(victimLatLng, 15.5f)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(InverseSurface.copy(alpha = 0.90f))
                    .testTag("btn_recenter_google_map")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recenter Map",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Bottom Left Telemetry Status Pill
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Secondary)
                    )
                    Text(
                        text = "Patrol: ${String.format(Locale.US, "%.0f", patrolLocation?.speedKmh ?: 38f)} km/h • ±2.1m",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}
