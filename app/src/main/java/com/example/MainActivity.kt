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
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.EmergencyStatus
import com.example.model.UserRole
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
import com.example.ui.screens.LoginScreen
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
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val currentUserProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()

    var currentScreen by remember {
        mutableStateOf(
            when (currentRole) {
                UserRole.CITIZEN -> AppScreen.HOME
                UserRole.RESPONDER_PATROL -> AppScreen.PATROL
                UserRole.COMMAND_CENTER -> AppScreen.INCIDENT_COMMAND
            }
        )
    }

    var showProfileModal by remember { mutableStateOf(false) }
    var showRoleSwitchDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val emergencyStatus by viewModel.emergencyStatus.collectAsStateWithLifecycle()
    val isFakeCallRinging by viewModel.isFakeCallRinging.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val p2pState by viewModel.p2pState.collectAsStateWithLifecycle()
    val isP2pConnected = p2pState == P2PState.CONNECTED

    // Role changes update initial environment
    LaunchedEffect(currentRole) {
        currentScreen = when (currentRole) {
            UserRole.CITIZEN -> AppScreen.HOME
            UserRole.RESPONDER_PATROL -> AppScreen.PATROL
            UserRole.COMMAND_CENTER -> AppScreen.INCIDENT_COMMAND
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearToast()
        }
    }

    // Auto-navigate to Active SOS screen if emergency is triggered while in Citizen view
    LaunchedEffect(emergencyStatus) {
        if (emergencyStatus == EmergencyStatus.RESPONDER_ASSIGNED && currentScreen == AppScreen.HOME && currentRole == UserRole.CITIZEN) {
            currentScreen = AppScreen.ACTIVE_SOS
        }
    }

    // If not logged in, show dedicated authentication & role selection view
    if (!isLoggedIn) {
        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = { role ->
                currentScreen = when (role) {
                    UserRole.CITIZEN -> AppScreen.HOME
                    UserRole.RESPONDER_PATROL -> AppScreen.PATROL
                    UserRole.COMMAND_CENTER -> AppScreen.INCIDENT_COMMAND
                }
            }
        )
        return
    }

    // Handle back button behavior
    val defaultHomeForRole = when (currentRole) {
        UserRole.CITIZEN -> AppScreen.HOME
        UserRole.RESPONDER_PATROL -> AppScreen.PATROL
        UserRole.COMMAND_CENTER -> AppScreen.INCIDENT_COMMAND
    }

    BackHandler(enabled = currentScreen != defaultHomeForRole) {
        currentScreen = when (currentScreen) {
            AppScreen.ACTIVE_SOS -> AppScreen.HOME
            AppScreen.INCIDENT_COMMAND -> if (currentRole == UserRole.COMMAND_CENTER) AppScreen.INCIDENT_COMMAND else AppScreen.PATROL
            AppScreen.DEVICE_PAIRING -> defaultHomeForRole
            else -> defaultHomeForRole
        }
    }

    val navDestination = when (currentScreen) {
        AppScreen.HOME -> NavDestination.HOME
        AppScreen.ROUTES -> NavDestination.ROUTES
        AppScreen.CONTACTS -> NavDestination.CONTACTS
        AppScreen.PATROL -> NavDestination.PATROL
        AppScreen.INCIDENT_COMMAND -> NavDestination.COMMAND
        AppScreen.DEVICE_PAIRING -> NavDestination.SYNC
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
                        title = "Home SOS",
                        subtitle = "GPS Precision: 3.2m",
                        showBack = false,
                        currentUserRole = currentRole,
                        onDemoClick = {
                            viewModel.triggerSosEmergency()
                            currentScreen = AppScreen.ACTIVE_SOS
                        },
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        onSwitchRoleClick = { showRoleSwitchDialog = true },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.ACTIVE_SOS -> {
                    TacticalTopAppBar(
                        title = "Active SOS Session",
                        subtitle = "Encrypted Live Channel Active",
                        showBack = true,
                        currentUserRole = currentRole,
                        onBackClick = { currentScreen = defaultHomeForRole },
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        onSwitchRoleClick = { showRoleSwitchDialog = true },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.PATROL -> {
                    TacticalTopAppBar(
                        title = "Responder Patrol",
                        subtitle = "Unit Scorpio-4 • On Duty",
                        showBack = false,
                        currentUserRole = currentRole,
                        onDemoClick = {
                            viewModel.triggerSosEmergency()
                        },
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        onSwitchRoleClick = { showRoleSwitchDialog = true },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.INCIDENT_COMMAND -> {
                    TacticalTopAppBar(
                        title = "Incident Command",
                        subtitle = "Central 112 Cluster Grid",
                        showBack = currentRole != UserRole.COMMAND_CENTER,
                        currentUserRole = currentRole,
                        onBackClick = { currentScreen = defaultHomeForRole },
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        onSwitchRoleClick = { showRoleSwitchDialog = true },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.ROUTES -> {
                    TacticalTopAppBar(
                        title = "Safe Navigation",
                        subtitle = "AI Well-Lit Pathway",
                        showBack = false,
                        currentUserRole = currentRole,
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        onSwitchRoleClick = { showRoleSwitchDialog = true },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.CONTACTS -> {
                    TacticalTopAppBar(
                        title = "Emergency Allies",
                        subtitle = "3 Trusted Allies Active",
                        showBack = false,
                        currentUserRole = currentRole,
                        onPairDevicesClick = { currentScreen = AppScreen.DEVICE_PAIRING },
                        onSwitchRoleClick = { showRoleSwitchDialog = true },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
                AppScreen.DEVICE_PAIRING -> {
                    TacticalTopAppBar(
                        title = "Sync 2 Devices",
                        subtitle = "Live GPS & Audio Relay",
                        showBack = true,
                        currentUserRole = currentRole,
                        onBackClick = { currentScreen = defaultHomeForRole },
                        onSwitchRoleClick = { showRoleSwitchDialog = true },
                        isP2pConnected = isP2pConnected,
                        onProfileClick = { showProfileModal = true }
                    )
                }
            }
        },
        bottomBar = {
            if (currentScreen != AppScreen.ACTIVE_SOS && currentScreen != AppScreen.DEVICE_PAIRING) {
                TacticalBottomNavBar(
                    selectedDestination = navDestination,
                    currentUserRole = currentRole,
                    onDestinationSelected = { dest ->
                        currentScreen = when (dest) {
                            NavDestination.HOME -> AppScreen.HOME
                            NavDestination.ROUTES -> AppScreen.ROUTES
                            NavDestination.CONTACTS -> AppScreen.CONTACTS
                            NavDestination.PATROL -> AppScreen.PATROL
                            NavDestination.COMMAND -> AppScreen.INCIDENT_COMMAND
                            NavDestination.SYNC -> AppScreen.DEVICE_PAIRING
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
                            onNavigateBack = { currentScreen = defaultHomeForRole }
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
                            onNavigateBack = { currentScreen = defaultHomeForRole }
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
                            onNavigateBack = { currentScreen = defaultHomeForRole }
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

    // Role Switcher Dialog
    if (showRoleSwitchDialog) {
        Dialog(onDismissRequest = { showRoleSwitchDialog = false }) {
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
                            text = "Switch Environment Role",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = { showRoleSwitchDialog = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Text(
                        text = "Select an authenticated role to immediately test and view their respective environment:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    UserRole.values().forEach { role ->
                        val isCurrent = currentRole == role
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) Primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.switchRole(role)
                                    showRoleSwitchDialog = false
                                },
                            color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (role) {
                                                UserRole.CITIZEN -> Primary.copy(alpha = 0.15f)
                                                UserRole.RESPONDER_PATROL -> Secondary.copy(alpha = 0.15f)
                                                UserRole.COMMAND_CENTER -> Color(0xFF6750A4).copy(alpha = 0.15f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (role) {
                                            UserRole.CITIZEN -> Icons.Default.Security
                                            UserRole.RESPONDER_PATROL -> Icons.Default.LocalPolice
                                            UserRole.COMMAND_CENTER -> Icons.Default.CellTower
                                        },
                                        contentDescription = null,
                                        tint = when (role) {
                                            UserRole.CITIZEN -> Primary
                                            UserRole.RESPONDER_PATROL -> Secondary
                                            UserRole.COMMAND_CENTER -> Color(0xFF6750A4)
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = role.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    )
                                    Text(
                                        text = role.subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isCurrent) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Primary
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Profile & Settings Modal
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
                            text = "Tactical Profile & Role",
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
                                .background(
                                    when (currentRole) {
                                        UserRole.CITIZEN -> Primary
                                        UserRole.RESPONDER_PATROL -> Secondary
                                        UserRole.COMMAND_CENTER -> Color(0xFF6750A4)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (currentRole) {
                                    UserRole.CITIZEN -> Icons.Default.Person
                                    UserRole.RESPONDER_PATROL -> Icons.Default.LocalPolice
                                    UserRole.COMMAND_CENTER -> Icons.Default.CellTower
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = currentUserProfile.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Primary
                                ) {
                                    Text(
                                        text = currentRole.badge,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                            Text(
                                text = currentUserProfile.designation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = if (isP2pConnected) "🟢 Linked to Responder Peer" else "Mesh Link: Ready",
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
                            Text("Active Role: ${currentRole.title}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Assigned Sector: ${currentUserProfile.sector}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Safe Cancellation PIN: 1234", fontSize = 12.sp, color = Secondary)
                            Text("Duress PIN: 9999 (Triggers covert police escalation)", fontSize = 12.sp, color = Primary)
                            Text("Live GPS & Speech Sync: 2.5Hz P2P Stream Active", fontSize = 12.sp, color = Secondary)
                        }
                    }

                    Button(
                        onClick = {
                            showProfileModal = false
                            showRoleSwitchDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Switch Role / Environment", fontWeight = FontWeight.Bold)
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

                    OutlinedButton(
                        onClick = {
                            showProfileModal = false
                            viewModel.logout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp), tint = Primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Out (Switch Account)", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
