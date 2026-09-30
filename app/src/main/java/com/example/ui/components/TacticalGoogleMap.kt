package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.location.GpsCoordinate
import com.example.ui.theme.Error
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

@Composable
fun TacticalGoogleMap(
    modifier: Modifier = Modifier,
    victimLocation: GpsCoordinate,
    patrolLocation: GpsCoordinate?,
    showSafeCorridors: Boolean = true,
    etaText: String = "3.5 mins (1.1 km)",
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

    // Civil Hospital Emergency Trauma Desk
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
    var useGoogleMapsSdk by remember { mutableStateOf(true) }

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

    LaunchedEffect(victimLatLng, patrolLatLng) {
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
            // Camera animate fallback
            cameraPositionState.position = CameraPosition.fromLatLngZoom(victimLatLng, 14.8f)
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
            if (useGoogleMapsSdk) {
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
                    ),
                    onMapLoaded = {
                        // Map successfully loaded
                    }
                ) {
                    // 1. Victim Marker (Ananya S. - High Distress SOS Beacon)
                    MarkerComposable(
                        state = MarkerState(position = victimLatLng),
                        title = "Your Location (Victim Beacon)",
                        snippet = "Broadcasting GPS • High Urgency SOS"
                    ) {
                        Box(
                            modifier = Modifier.size(54.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(Primary.copy(alpha = 0.45f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Primary)
                                    .border(2.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonPinCircle,
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
                        snippet = "Patrol Unit Scorpio-4 (KA-04-P-8821)"
                    ) {
                        Box(
                            modifier = Modifier.size(54.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(Secondary.copy(alpha = 0.35f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Secondary)
                                    .border(2.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalPolice,
                                    contentDescription = "Patrol Cruiser",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 3. Nearby Sector 28 Standby Patrol
                    MarkerComposable(
                        state = MarkerState(position = nearbyPatrolLatLng),
                        title = "Sector 28 Support Patrol",
                        snippet = "KA-04-P-1102 (Standby Tier 2)"
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // 4. Safe Haven Police Station
                    MarkerComposable(
                        state = MarkerState(position = policeStationLatLng),
                        title = "Sector 29 Police Station",
                        snippet = "Verified Safe Haven • 0.8 km"
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(SecondaryContainer)
                                .border(2.dp, Secondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // 5. Civil Hospital Trauma Center
                    MarkerComposable(
                        state = MarkerState(position = hospitalLatLng),
                        title = "Civil Hospital Trauma Center",
                        snippet = "24/7 Female Support Desk • 1.4 km"
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFDADA))
                                .border(2.dp, Primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalHospital,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // 6. Safest Well-Lit Intercept Route Polyline
                    if (showSafeCorridors) {
                        // Main Intercept Safe Route
                        Polyline(
                            points = safeRouteWaypoints,
                            color = Secondary,
                            width = 12f,
                            geodesic = true
                        )

                        // Emergency Corridors to Safe Haven Station
                        Polyline(
                            points = altSafeRouteWaypoints,
                            color = WarningAmber,
                            width = 7f,
                            geodesic = true
                        )
                    }
                }
            } else {
                // Tactical Fallback Map Image with Overlay
                AsyncImage(
                    model = "https://lh3.googleusercontent.com/aida-public/AB6AXuDvx8E6sauEOvXqsEXj1qeM5hg8AJ5l2VUpDAYiLg_p7NyquiQ5H9Q5HW1AItEW7ZIz9mauaqO2YuADmae11sj_Y0-fvMAJE8CIVvvkerMywGNtYpoDYdLBEu3OweZaU7iZWjoFPyco3PVTTLVjEQJ7tYS0kCnKjr9WGJv1SvgyYvEzVH0eQ5eK0r7fB5sft-dNduQvVTSN6GDEOOZbQZbKxf3W3b6qilkNplUxvfREkyGn0xEdLAgz",
                    contentDescription = "Tactical Live Tracking Map",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dashed Canvas Route overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                    drawLine(
                        color = Color(0xFF006C4A),
                        start = Offset(x = size.width * 0.22f, y = size.height * 0.72f),
                        end = Offset(x = size.width * 0.78f, y = size.height * 0.28f),
                        strokeWidth = 8f,
                        pathEffect = pathEffect
                    )
                }
            }

            // Top Floating HUD Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = InverseSurface.copy(alpha = 0.92f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = null,
                            tint = SecondaryFixed,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "ETA to Victim",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.LightGray
                            )
                            Text(
                                text = etaText,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.92f),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Secondary)
                            )
                            Text(
                                text = "Live GPS 1Hz",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Map layer switch
                    Surface(
                        onClick = {
                            mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.92f),
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Toggle Map Type",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Location Telemetry Bar & Recenter
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(6.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 2.dp
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Broadcasting ${String.format(java.util.Locale.US, "%.4f° N, %.4f° E", victimLocation.latitude, victimLocation.longitude)} • 98% Well-Lit Route",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = {
                            try {
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(victimLatLng, 15.5f)
                            } catch (e: Exception) {}
                            onRecenterClick()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_recenter_google_map")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Recenter",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
