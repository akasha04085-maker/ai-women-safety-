package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.EmergencyStatus
import com.example.network.P2PState
import com.example.ui.components.FakeIncomingCallSimulationDialog
import com.example.ui.components.NavDestination
import com.example.ui.components.TacticalBottomNavBar
import com.example.ui.components.TacticalTopAppBar
import com.example.ui.screens.ActiveSosScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.DevicePairingScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IncidentDetailsScreen
import com.example.ui.screens.ResponderPatrolScreen
import com.example.ui.screens.SafeRoutesScreen
import com.example.ui.theme.Primary
import com.example.ui.theme.ResoluteSosTheme
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.viewmodel.EmergencyViewModel

enum class AppScreen {
    HOME,
    ROUTES,
    CONTACTS,
    PATROL,
    ACTIVE_SOS,
    INCIDENT_COMMAND,
    DEVICE_PAIRING
}

class MainActivity : ComponentActivity() {
    private val emergencyViewModel: EmergencyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResoluteSosTheme {
                MainAppRoot(viewModel = emergencyViewModel)
            }
        }
    }
}

@Composable
fun MainAppRoot(viewModel: EmergencyViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var showProfileModal by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val emergencyStatus by viewModel.emergencyStatus.collectAsStateWithLifecycle()
    val isFakeCallRinging by viewModel.isFakeCallRinging.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val p2pState by viewModel.p2pState.collectAsStateWithLifecycle()
    val isP2pConnected = p2pState == P2PState.CONNECTED

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearToast()
        }
    }

    // Auto-navigate to Active SOS screen if emergency is triggered
    LaunchedEffect(emergencyStatus) {
        if (emergencyStatus == EmergencyStatus.RESPONDER_ASSIGNED && currentScreen == AppScreen.HOME) {
            currentScreen = AppScreen.ACTIVE_SOS
        }
    }

    // Handle back button behavior
    BackHandler(enabled = currentScreen != AppScreen.HOME) {
        currentScreen = when (currentScreen) {
            AppScreen.ACTIVE_SOS -> AppScreen.HOME
            AppScreen.INCIDENT_COMMAND -> AppScreen.PATROL
            AppScreen.DEVICE_PAIRING -> AppScreen.HOME
            else -> AppScreen.HOME
        }
    }

    val navDestination = when (currentScreen) {
        AppScreen.HOME -> NavDestination.HOME
        AppScreen.ROUTES -> NavDestination.ROUTES
        AppScreen.CONTACTS -> NavDestination.CONTACTS
        AppScreen.PATROL -> NavDestination.PATROL
        else -> NavDestination.HOME
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_scaffold_root"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            when (currentScreen) {
                AppScreen.HOME -> {
                    TacticalTopAppBar(
                        title = "Home Sos",
                        subtitle = "GPS Precision: 3m",
                        showBack = false,
                        onDemoClick = {
                            viewModel.triggerSosEmergency()
                            currentScreen = AppScreen.ACTIVE_SOS
                        },
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.ACTIVE_SOS -> {
                    TacticalTopAppBar(
                        title = "Active Sos Session",
                        subtitle = "Encrypted Session Active",
                        showBack = true,
                        onBackClick = { currentScreen = AppScreen.HOME },
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.PATROL -> {
                    TacticalTopAppBar(
                        title = "Responder Patrol",
                        subtitle = "GPS Precision: 3m",
                        showBack = false,
                        onDemoClick = {
                            viewModel.triggerSosEmergency()
                            currentScreen = AppScreen.ACTIVE_SOS
                        },
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.INCIDENT_COMMAND -> {
                    TacticalTopAppBar(
                        title = "Incident Details",
                        subtitle = "Encrypted Session Active",
                        showBack = true,
                        onBackClick = { currentScreen = AppScreen.PATROL },
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.ROUTES -> {
                    TacticalTopAppBar(
                        title = "Safe Navigation",
                        subtitle = "AI Well-Lit Pathway",
                        showBack = false,
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.CONTACTS -> {
                    TacticalTopAppBar(
                        title = "Emergency Allies",
                        subtitle = "3 Trusted Allies Active",
                        showBack = false,
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.DEVICE_PAIRING -> {
                    TacticalTopAppBar(
                        title = "Sync 2 Devices",
                        subtitle = "Live GPS & Audio Relay",
                        showBack = true,
                        onBackClick = { currentScreen = AppScreen.HOME },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
            }
        },
        bottomBar = {
            if (currentScreen != AppScreen.ACTIVE_SOS && currentScreen != AppScreen.INCIDENT_COMMAND && currentScreen != AppScreen.DEVICE_PAIRING) {
                TacticalBottomNavBar(
                    selectedDestination = navDestination,
                    onDestinationSelected = { dest ->
                        currentScreen = when (dest) {
                            NavDestination.HOME -> AppScreen.HOME
                            NavDestination.ROUTES -> AppScreen.ROUTES
                            NavDestination.CONTACTS -> AppScreen.CONTACTS
                            NavDestination.PATROL -> AppScreen.PATROL
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screenTransition"
            ) { screen ->
                when (screen) {
                    AppScreen.HOME -> {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToActiveSos = { currentScreen = AppScreen.ACTIVE_SOS },
                            onNavigateToRoutes = { currentScreen = AppScreen.ROUTES },
                            onNavigateToContacts = { currentScreen = AppScreen.CONTACTS }
                        )
                    }
                    AppScreen.ACTIVE_SOS -> {
                        ActiveSosScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentScreen = AppScreen.HOME }
                        )
                    }
                    AppScreen.PATROL -> {
                        ResponderPatrolScreen(
                            viewModel = viewModel,
                            onNavigateToIncidentCommand = { currentScreen = AppScreen.INCIDENT_COMMAND }
                        )
                    }
                    AppScreen.INCIDENT_COMMAND -> {
                        IncidentDetailsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentScreen = AppScreen.PATROL }
                        )
                    }
                    AppScreen.ROUTES -> {
                        SafeRoutesScreen(viewModel = viewModel)
                    }
                    AppScreen.CONTACTS -> {
                        ContactsScreen(viewModel = viewModel)
                    }
                    AppScreen.DEVICE_PAIRING -> {
                        DevicePairingScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentScreen = AppScreen.HOME }
                        )
                    }
                }
            }
        }
    }

    if (isFakeCallRinging) {
        FakeIncomingCallSimulationDialog(
            onDismiss = { viewModel.dismissFakeCall() }
        )
    }

    if (showProfileModal) {
        Dialog(onDismissRequest = { showProfileModal = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Aura Guardian Profile",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = { showProfileModal = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Ananya Sharma",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Cyber City Safe Zone • ID: #AG-4410",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = if (isP2pConnected) "🟢 Linked to Responder Peer" else "Mesh Mode: Standby",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isP2pConnected) Secondary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Triple-Press Power: Instant SOS (Enabled)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Safe Cancellation PIN: 1234", fontSize = 12.sp, color = Secondary)
                            Text("Duress Fake Cancel PIN: 9999", fontSize = 12.sp, color = Primary)
                            Text("Audio Engine: 16kHz Full-Duplex PCM Stream", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                            Text("Two-Device Sync: P2P Socket Port 8942", fontSize = 12.sp, color = Secondary)
                        }
                    }

                    Button(
                        onClick = {
                            showProfileModal = false
                            currentScreen = AppScreen.DEVICE_PAIRING
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Secondary)
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open 2-Device Sync Manager", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showProfileModal = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
