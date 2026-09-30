package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.EmergencyHub
import com.example.model.HubType
import com.example.ui.theme.InverseSurface
import com.example.ui.theme.Primary
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SecondaryFixed
import com.example.viewmodel.EmergencyViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

@Composable
fun EmergencyHubsMapScreen(
    viewModel: EmergencyViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val userLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val hubs by viewModel.emergencyHubs.collectAsStateWithLifecycle()
    val selectedHub by viewModel.selectedHub.collectAsStateWithLifecycle()
    val hubFilter by viewModel.hubFilter.collectAsStateWithLifecycle()
    val isGpsFixActive by viewModel.isGpsFixActive.collectAsStateWithLifecycle()
    val gpsProviderType by viewModel.gpsProviderType.collectAsStateWithLifecycle()
    val isSimulatingWalk by viewModel.isSimulatingWalk.collectAsStateWithLifecycle()

    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var hasPermission by remember { mutableStateOf(viewModel.locationManager.hasLocationPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = fineGranted || coarseGranted
        if (hasPermission) {
            viewModel.startGpsTracking()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.startGpsTracking()
        }
    }

    val userLatLng = remember(userLocation.latitude, userLocation.longitude) {
        LatLng(userLocation.latitude, userLocation.longitude)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLatLng, 15.2f)
    }

    // Auto-focus on selected hub when chosen
    LaunchedEffect(selectedHub) {
        selectedHub?.let { hub ->
            val hubLatLng = LatLng(hub.latitude, hub.longitude)
            try {
                val bounds = LatLngBounds.builder()
                    .include(userLatLng)
                    .include(hubLatLng)
                    .build()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, 140),
                    durationMs = 600
                )
            } catch (e: Exception) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(hubLatLng, 15.5f),
                    durationMs = 500
                )
            }
        }
    }

    // Animated GPS Beacon Pulse
    val infiniteTransition = rememberInfiniteTransition(label = "gpsPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gpsPulseScale"
    )

    // Filtered Hubs
    val filteredHubs = remember(hubs, hubFilter) {
        if (hubFilter == null) hubs else hubs.filter { it.type == hubFilter }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("emergency_hubs_map_screen_root")
    ) {
        // 1. Google Maps SDK Viewport
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
            // User Current GPS Location Marker with Pulse Ring
            MarkerComposable(
                state = MarkerState(position = userLatLng),
                title = "Your Real-Time GPS Position",
                snippet = "Accuracy ±${String.format(Locale.US, "%.1f", userLocation.accuracyMeters)}m • Speed ${String.format(Locale.US, "%.0f", userLocation.speedKmh)} km/h"
            ) {
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.35f))
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Primary)
                            .border(3.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }

            // User Accuracy Radius Circle
            Circle(
                center = userLatLng,
                radius = userLocation.accuracyMeters.toDouble().coerceAtLeast(15.0),
                fillColor = Primary.copy(alpha = 0.12f),
                strokeColor = Primary.copy(alpha = 0.45f),
                strokeWidth = 2f
            )

            // Emergency Hub Markers
            filteredHubs.forEach { hub ->
                val hubLatLng = LatLng(hub.latitude, hub.longitude)
                val isSelected = selectedHub?.id == hub.id
                val markerColor = Color(hub.type.colorHex)

                MarkerComposable(
                    state = MarkerState(position = hubLatLng),
                    title = hub.name,
                    snippet = "${hub.type.title} • ${hub.distanceFormatted} away",
                    onClick = {
                        viewModel.selectHub(hub)
                        true
                    }
                ) {
                    HubMapMarkerIcon(
                        hub = hub,
                        isSelected = isSelected,
                        markerColor = markerColor
                    )
                }
            }

            // Directional Route Polyline to Selected Hub
            selectedHub?.let { hub ->
                val hubLatLng = LatLng(hub.latitude, hub.longitude)
                val routeWaypoints = listOf(
                    userLatLng,
                    LatLng(
                        (userLatLng.latitude + hubLatLng.latitude) / 2 + 0.0006,
                        (userLatLng.longitude + hubLatLng.longitude) / 2 - 0.0004
                    ),
                    hubLatLng
                )
                Polyline(
                    points = routeWaypoints,
                    color = Color(hub.type.colorHex),
                    width = 12f,
                    geodesic = true
                )
            }
        }

        // 2. Top Floating Header (GPS Status, Telemetry & Filter Chips)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live GPS Status Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = InverseSurface.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isGpsFixActive) Color(0xFF10B981) else Color(0xFFF59E0B))
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isSimulatingWalk) "LIVE SIMULATED WALK" else "REAL-TIME GPS TRACKING",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color.White
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isGpsFixActive) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFFF59E0B).copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        text = "±${String.format(Locale.US, "%.1f", userLocation.accuracyMeters)}m",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Text(
                                text = "${userLocation.locationName} • Speed: ${String.format(Locale.US, "%.1f", userLocation.speedKmh)} km/h",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }

                    // Simulation & Refresh Controls
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSimulatingWalk) Secondary else Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.clickable { viewModel.toggleSimulatedWalk() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsWalk,
                                    contentDescription = "Simulate Walk",
                                    tint = if (isSimulatingWalk) Color(0xFF002114) else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isSimulatingWalk) "Walking" else "Walk",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSimulatingWalk) Color(0xFF002114) else Color.White
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            IconButton(onClick = {
                                mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = "Map Layers",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Category Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = hubFilter == null,
                    onClick = { viewModel.setHubFilter(null) },
                    label = { Text("All Hubs (${hubs.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = hubFilter == HubType.POLICE,
                    onClick = { viewModel.setHubFilter(if (hubFilter == HubType.POLICE) null else HubType.POLICE) },
                    label = { Text("Police & QRT", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.LocalPolice, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(HubType.POLICE.colorHex),
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = hubFilter == HubType.HOSPITAL,
                    onClick = { viewModel.setHubFilter(if (hubFilter == HubType.HOSPITAL) null else HubType.HOSPITAL) },
                    label = { Text("Trauma Hospitals", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.LocalHospital, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(HubType.HOSPITAL.colorHex),
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = hubFilter == HubType.SAFE_HAVEN,
                    onClick = { viewModel.setHubFilter(if (hubFilter == HubType.SAFE_HAVEN) null else HubType.SAFE_HAVEN) },
                    label = { Text("Women Safe Havens", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(HubType.SAFE_HAVEN.colorHex),
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = hubFilter == HubType.FIRE_STATION,
                    onClick = { viewModel.setHubFilter(if (hubFilter == HubType.FIRE_STATION) null else HubType.FIRE_STATION) },
                    label = { Text("Fire & Rescue", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(HubType.FIRE_STATION.colorHex),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // 3. Floating Recenter on User GPS Button
        IconButton(
            onClick = {
                viewModel.selectHub(null)
                cameraPositionState.position = CameraPosition.fromLatLngZoom(userLatLng, 16.0f)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 220.dp, end = 16.dp)
                .size(46.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(InverseSurface.copy(alpha = 0.92f))
                .testTag("btn_recenter_user_gps")
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Recenter on User GPS",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // 4. Bottom Sliding Hubs Carousel & Quick Triage Sheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEARBY EMERGENCY HUBS (${filteredHubs.size})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sorted by proximity",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredHubs, key = { it.id }) { hub ->
                    EmergencyHubCard(
                        hub = hub,
                        isSelected = selectedHub?.id == hub.id,
                        onCardClick = { viewModel.selectHub(hub) },
                        onCallClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${hub.phone}"))
                            context.startActivity(intent)
                        },
                        onNavigateClick = {
                            val uri = Uri.parse("google.navigation:q=${hub.latitude},${hub.longitude}")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(mapIntent)
                            } else {
                                val webIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://maps.google.com/?q=${hub.latitude},${hub.longitude}")
                                )
                                context.startActivity(webIntent)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HubMapMarkerIcon(
    hub: EmergencyHub,
    isSelected: Boolean,
    markerColor: Color
) {
    Box(
        modifier = Modifier.size(if (isSelected) 60.dp else 46.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(markerColor.copy(alpha = 0.35f))
            )
        }

        Box(
            modifier = Modifier
                .size(if (isSelected) 38.dp else 30.dp)
                .clip(CircleShape)
                .background(markerColor)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (hub.type) {
                    HubType.POLICE -> Icons.Default.LocalPolice
                    HubType.HOSPITAL -> Icons.Default.LocalHospital
                    HubType.SAFE_HAVEN -> Icons.Default.Shield
                    HubType.FIRE_STATION -> Icons.Default.LocalFireDepartment
                    HubType.PATROL_POST -> Icons.Default.Security
                },
                contentDescription = hub.name,
                tint = Color.White,
                modifier = Modifier.size(if (isSelected) 22.dp else 16.dp)
            )
        }
    }
}

@Composable
fun EmergencyHubCard(
    hub: EmergencyHub,
    isSelected: Boolean,
    onCardClick: () -> Unit,
    onCallClick: () -> Unit,
    onNavigateClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(hub.type.colorHex) else Color.Transparent

    Card(
        modifier = Modifier
            .width(300.dp)
            .clickable { onCardClick() }
            .testTag("hub_card_${hub.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, borderColor) else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(hub.type.colorHex).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(hub.type.colorHex))
                        )
                        Text(
                            text = hub.type.chipLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp
                            ),
                            color = Color(hub.type.colorHex)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "24/7 Active",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF10B981)
                    )
                }
            }

            Text(
                text = hub.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                maxLines = 1
            )

            Text(
                text = hub.address,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            // ETA & Proximity Stats Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = hub.distanceFormatted,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Primary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsWalk,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${hub.etaWalkingMinutes}m",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Secondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${hub.etaDrivingMinutes}m",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Secondary
                            )
                        }
                    }
                }
            }

            // Quick Call & Navigation CTAs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onCallClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call Hub",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call ${hub.phone}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onNavigateClick,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(hub.type.colorHex),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Navigate to Hub",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start Route", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
