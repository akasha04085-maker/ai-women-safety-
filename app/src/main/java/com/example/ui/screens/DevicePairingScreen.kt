package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.ConnectionRole
import com.example.network.P2PState
import com.example.ui.theme.Error
import com.example.ui.theme.InverseSurface
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SecondaryFixed
import com.example.viewmodel.EmergencyViewModel
import java.util.Locale

@Composable
fun DevicePairingScreen(
    viewModel: EmergencyViewModel,
    onNavigateBack: () -> Unit
) {
    val p2pState by viewModel.p2pState.collectAsStateWithLifecycle()
    val peerDevice by viewModel.peerDevice.collectAsStateWithLifecycle()
    val hostIp by viewModel.currentHostIp.collectAsStateWithLifecycle()
    val availableIps by viewModel.availableHostIps.collectAsStateWithLifecycle()
    val pingLatency by viewModel.pingLatencyMs.collectAsStateWithLifecycle()
    val connectionError by viewModel.lastConnectionError.collectAsStateWithLifecycle()
    val isScanning by viewModel.isSubnetScanning.collectAsStateWithLifecycle()
    val myLoc by viewModel.myLocation.collectAsStateWithLifecycle()
    val peerLoc by viewModel.peerLocation.collectAsStateWithLifecycle()
    val audioAmp by viewModel.audioAmplitude.collectAsStateWithLifecycle()
    val isBroadcasting by viewModel.isAudioBroadcasting.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var targetIpInput by remember { mutableStateOf(hostIp) }
    var sessionCodeInput by remember { mutableStateOf("SOS-8942") }
    var isPttHolding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("device_pairing_screen_content"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Info
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SecondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Two-Device Live Sync",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Real-Time Location & Voice Walkie-Talkie Bridge",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = when (p2pState) {
                        P2PState.CONNECTED -> SecondaryContainer
                        P2PState.HOSTING_WAITING, P2PState.CONNECTING -> MaterialTheme.colorScheme.surfaceContainerHigh
                        P2PState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceContainer
                    }
                ) {
                    Text(
                        text = when (p2pState) {
                            P2PState.CONNECTED -> "🟢 CONNECTED"
                            P2PState.HOSTING_WAITING -> "🟡 HOSTING"
                            P2PState.CONNECTING -> "🟡 SYNCING"
                            P2PState.DISCONNECTED -> "⚪ OFFLINE"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        color = when (p2pState) {
                            P2PState.CONNECTED -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }

        // Diagnostic Connection Error Alert (When 2 devices fail to connect)
        AnimatedVisibility(visible = connectionError != null && p2pState != P2PState.CONNECTED) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connection Diagnostic",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = connectionError ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Active Connection State Card (If Connected)
        AnimatedVisibility(visible = p2pState == P2PState.CONNECTED) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("p2p_connected_dashboard"),
                shape = RoundedCornerShape(16.dp),
                color = InverseSurface,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryFixed)
                            )
                            Text(
                                text = "ENCRYPTED P2P TUNNEL ACTIVE",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                        }
                        Text(
                            text = "${pingLatency}ms latency",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SecondaryFixed
                            )
                        )
                    }

                    // Connected Device specs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Connected Peer",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color.LightGray
                                )
                                Text(
                                    text = peerDevice?.deviceName ?: "Patrol Unit #KA-04",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "IP: ${peerDevice?.ipAddress ?: hostIp}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = Color.LightGray
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Inter-Device Distance",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color.LightGray
                                )
                                val distKm = peerLoc?.let {
                                    viewModel.locationManager.calculateDistanceKm(myLoc, it)
                                } ?: 1.4
                                Text(
                                    text = String.format(Locale.US, "%.2f km", distKm),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SecondaryFixed
                                    )
                                )
                                Text(
                                    text = "Live GPS 1Hz Synced",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = SecondaryFixed
                                )
                            }
                        }
                    }

                    // Real-Time Voice Walkie-Talkie Test Pad
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        tint = if (isBroadcasting || isPttHolding) SecondaryFixed else Color.LightGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Walkie-Talkie Audio Relay",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = if (isBroadcasting || isPttHolding) "LIVE MIC AUDIO STREAMING" else "Standby (Press & Hold)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBroadcasting || isPttHolding) SecondaryFixed else Color.LightGray
                                    )
                                )
                            }

                            // Interactive PTT Voice Button
                            Button(
                                onClick = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                isPttHolding = true
                                                viewModel.toggleAudioBroadcasting(true)
                                                tryAwaitRelease()
                                                isPttHolding = false
                                                viewModel.toggleAudioBroadcasting(false)
                                            }
                                        )
                                    }
                                    .testTag("btn_p2p_ptt_talk"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBroadcasting || isPttHolding) Secondary else Primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBroadcasting || isPttHolding) "TRANSMITTING VOICE TO PEER..." else "HOLD TO TALK TO OTHER DEVICE",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    // Disconnect Button
                    Button(
                        onClick = { viewModel.disconnectPeer() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Disconnect P2P Tunnel", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Connection Setup Tabs (Host vs Join)
        if (p2pState != P2PState.CONNECTED) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = Primary,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Device 1: Host Beacon",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Device 2: Join Peer",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            // Tab 0: Host Distress Beacon (Device 1)
            if (selectedTab == 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("host_beacon_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Host Distress Beacon (Device 1 - Victim / Anchor)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Starts a local P2P socket server on port 8942 (listening on 0.0.0.0 across all network interfaces). Device 2 can connect to your IP or via the Session Code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Detected IP Interfaces List
                        Text(
                            text = "DETECTED IP ADDRESSES ON THIS DEVICE:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.tertiary
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableIps.forEach { iface ->
                                val isSelected = hostIp == iface.ipAddress
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectHostIp(iface.ipAddress) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) Primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = iface.displayName,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Port: 8942",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Primary
                                            ) {
                                                Text(
                                                    text = "BROADCASTING",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Session Code Box (Cloud Relay Fallback)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Session Room Code (Cloud Bridge):", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
                                    Text(
                                        text = sessionCodeInput,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Secondary
                                    )
                                }
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = Secondary)
                            }
                        }

                        Button(
                            onClick = { viewModel.startHostingAsVictim() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_start_host_beacon"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (p2pState == P2PState.HOSTING_WAITING) Secondary else Primary,
                                contentColor = Color.White
                            )
                        ) {
                            if (p2pState == P2PState.HOSTING_WAITING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Hosting Active on $hostIp:8942", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CellTower,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Hosting Distress Beacon", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Tab 1: Join as Responder / Ally (Device 2)
            if (selectedTab == 1) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_peer_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Connect to Device 1 (Patrol / Ally Mode)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Enter Device 1's IP address (shown on Device 1's screen) or tap 'Auto-Scan' to find it on your Wi-Fi/Hotspot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Quick-Fill IP Chips
                        Text(
                            text = "QUICK-FILL IP TARGETS:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.tertiary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { targetIpInput = hostIp },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "Host IP: $hostIp",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { targetIpInput = "127.0.0.1" },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "127.0.0.1 (Local)",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { targetIpInput = "10.0.2.2" },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "10.0.2.2 (Emulator)",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        OutlinedTextField(
                            value = targetIpInput,
                            onValueChange = { targetIpInput = it },
                            label = { Text("Device 1 IP Address") },
                            placeholder = { Text("e.g. 192.168.1.100 or 127.0.0.1") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_target_host_ip"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Connect Button (Direct Socket)
                        Button(
                            onClick = { viewModel.connectToPeer(targetIpInput, ConnectionRole.RESPONDER_PATROL) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_connect_to_peer"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Secondary,
                                contentColor = Color.White
                            )
                        ) {
                            if (p2pState == P2PState.CONNECTING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting to Host...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect to Device 1 (Direct TCP)", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Auto-Scan Local Subnet Button
                        Button(
                            onClick = { viewModel.scanSubnetAndConnect(ConnectionRole.RESPONDER_PATROL) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_auto_scan_subnet"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning Local Network...", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            } else {
                                Icon(Icons.Default.WifiFind, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto-Scan Local Wi-Fi / Hotspot", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                        }

                        // Session Code Fallback (Bypasses LAN isolation)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                                    Text("Bypass Wi-Fi Isolation via Session Bridge", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Text(
                                    text = "If phones are on separate cellular data or Wi-Fi blocks peer sockets, connect instantly using Room Code:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { viewModel.connectViaSessionCode("SOS-8942", ConnectionRole.RESPONDER_PATROL) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("btn_connect_session_bridge"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0284C7),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Connect via Room Code: SOS-8942", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 1-Tap Simulated Peer Demo for Single-Device Users
                        Button(
                            onClick = { viewModel.simulateConnectedPeer(ConnectionRole.VICTIM_BEACON) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("btn_simulate_peer_link"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = Primary
                            )
                        ) {
                            Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate Patrol Unit Link (Single-Device Demo)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Troubleshooting Guide Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "2-Device Connection Troubleshooting",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "1. Mobile Hotspot: Turn on Personal Hotspot on Phone 1 and connect Phone 2 to it. This guarantees direct TCP socket routing without public Wi-Fi firewall blocks.\n2. Same Wi-Fi: Ensure both devices are on the same 2.4GHz / 5GHz Wi-Fi band.\n3. Exact IP: Verify the IP entered on Device 2 matches one of the IP addresses shown on Device 1's Host screen.\n4. Wi-Fi AP Isolation: If on public/campus Wi-Fi where devices are isolated from each other, tap 'Connect via Room Code: SOS-8942' to link over the cloud relay.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
