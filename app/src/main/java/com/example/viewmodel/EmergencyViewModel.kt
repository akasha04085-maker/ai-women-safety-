package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.RealtimeAudioStreamer
import com.example.data.AppDatabase
import com.example.data.EmergencyRepository
import com.example.location.GpsCoordinate
import com.example.location.RealtimeLocationManager
import com.example.model.EmergencyContact
import com.example.model.EmergencyStatus
import com.example.model.IncidentCard
import com.example.model.IncidentLog
import com.example.model.OfficerInfo
import com.example.model.SafeRoute
import com.example.network.ConnectionRole
import com.example.network.P2PSyncManager
import com.example.network.P2PState
import com.example.network.PeerDeviceInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmergencyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: EmergencyRepository

    val contacts: StateFlow<List<EmergencyContact>>
    val incidentLogs: StateFlow<List<IncidentLog>>
    val safeRoutes: StateFlow<List<SafeRoute>>

    // Two-Device Sync Components
    val syncManager = P2PSyncManager(application)
    val audioStreamer = RealtimeAudioStreamer(application)
    val locationManager = RealtimeLocationManager(application)

    val p2pState: StateFlow<P2PState> = syncManager.connectionState
    val p2pRole: StateFlow<ConnectionRole> = syncManager.myRole
    val peerDevice: StateFlow<PeerDeviceInfo?> = syncManager.peerDevice
    val currentHostIp: StateFlow<String> = syncManager.currentHostIp
    val pingLatencyMs: StateFlow<Long> = syncManager.pingLatencyMs
    val myLocation: StateFlow<GpsCoordinate> = locationManager.currentLocation
    val peerLocation: StateFlow<GpsCoordinate?> = locationManager.peerLocation
    val audioAmplitude: StateFlow<Float> = audioStreamer.audioAmplitude

    // Active Emergency Session State
    private val _emergencyStatus = MutableStateFlow(EmergencyStatus.IDLE)
    val emergencyStatus: StateFlow<EmergencyStatus> = _emergencyStatus.asStateFlow()

    private val _activeIncidentCode = MutableStateFlow("SOS-9941A")
    val activeIncidentCode: StateFlow<String> = _activeIncidentCode.asStateFlow()

    private val _etaSeconds = MutableStateFlow(210) // 3.5 minutes = 210s
    val etaSeconds: StateFlow<Int> = _etaSeconds.asStateFlow()

    private val _etaString = MutableStateFlow("3.5 mins (1.1 km)")
    val etaString: StateFlow<String> = _etaString.asStateFlow()

    private val _distanceMeters = MutableStateFlow(1100.0)
    val distanceMeters: StateFlow<Double> = _distanceMeters.asStateFlow()

    private val _assignedOfficer = MutableStateFlow(OfficerInfo())
    val assignedOfficer: StateFlow<OfficerInfo> = _assignedOfficer.asStateFlow()

    // WebRTC / Voice Audio state
    private val _isAudioBroadcasting = MutableStateFlow(false)
    val isAudioBroadcasting: StateFlow<Boolean> = _isAudioBroadcasting.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    // Patrol View State
    private val _isPatrolAvailable = MutableStateFlow(true)
    val isPatrolAvailable: StateFlow<Boolean> = _isPatrolAvailable.asStateFlow()

    private val _patrolEscalationSeconds = MutableStateFlow(7)
    val patrolEscalationSeconds: StateFlow<Int> = _patrolEscalationSeconds.asStateFlow()

    private val _isAlertAccepted = MutableStateFlow(false)
    val isAlertAccepted: StateFlow<Boolean> = _isAlertAccepted.asStateFlow()

    private val _isOnScene = MutableStateFlow(false)
    val isOnScene: StateFlow<Boolean> = _isOnScene.asStateFlow()

    private val _isResolved = MutableStateFlow(false)
    val isResolved: StateFlow<Boolean> = _isResolved.asStateFlow()

    // Fake call scheduler state
    private val _fakeCallScheduledSeconds = MutableStateFlow<Int?>(null)
    val fakeCallScheduledSeconds: StateFlow<Int?> = _fakeCallScheduledSeconds.asStateFlow()

    private val _isFakeCallRinging = MutableStateFlow(false)
    val isFakeCallRinging: StateFlow<Boolean> = _isFakeCallRinging.asStateFlow()

    // User Message / Snackbar
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Pin dialog & Safe Cancellation
    val safePin = "1234"
    val duressPin = "9999"

    // Incident queue for command view
    private val _incidentList = MutableStateFlow(
        listOf(
            IncidentCard(
                code = "#INC-8942",
                victimName = "Ananya S.",
                location = "Sector 28 Metro Pillar 42",
                status = "ACTIVE - DISPATCHED",
                etaPacing = "03:18",
                responderName = "SI Vikram Singh",
                responderUnit = "Patrol Alpha-4",
                autoEscalateSeconds = 0,
                isCritical = true
            ),
            IncidentCard(
                code = "#INC-8940",
                victimName = "Reetika D.",
                location = "DLF CyberHub Walkway South",
                status = "RESPONDER SEARCHING",
                etaPacing = "--:--",
                responderName = "Auto-assigning...",
                responderUnit = "Sector 29 Unit Pool",
                autoEscalateSeconds = 4,
                isCritical = true
            )
        )
    )
    val incidentList: StateFlow<List<IncidentCard>> = _incidentList.asStateFlow()

    private var etaTimerJob: Job? = null
    private var fakeCallJob: Job? = null
    private var escalationTimerJob: Job? = null
    private var locationBroadcastJob: Job? = null

    init {
        val database = AppDatabase.getInstance(application)
        repository = EmergencyRepository(database.emergencyDao())

        contacts = repository.contacts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        incidentLogs = repository.incidentLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        safeRoutes = repository.safeRoutes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        setupTwoDeviceSyncWiring()
        startPatrolEscalationCountdown()
        locationManager.startLocationUpdates()
    }

    private fun setupTwoDeviceSyncWiring() {
        // Audio capture -> Send to peer
        audioStreamer.onAudioPacketCaptured = { chunk ->
            if (!_isMicMuted.value && _isAudioBroadcasting.value) {
                syncManager.broadcastVoiceChunk(chunk)
            }
        }

        // Incoming audio from peer -> Play through speaker
        syncManager.onPeerAudioChunkReceived = { chunk ->
            if (_isSpeakerOn.value) {
                audioStreamer.playReceivedAudioChunk(chunk)
            }
        }

        // Incoming location from peer -> Update location manager & recalculate ETA/Distance
        syncManager.onPeerLocationReceived = { peerCoord ->
            locationManager.updatePeerLocation(peerCoord)
            val myLoc = locationManager.currentLocation.value
            val distKm = locationManager.calculateDistanceKm(myLoc, peerCoord)
            val etaMins = locationManager.calculateEtaMinutes(distKm)
            _distanceMeters.value = distKm * 1000.0
            _etaString.value = String.format(Locale.US, "%.1f mins (%.1f km)", etaMins, distKm)
        }

        // Incoming Actions from peer
        syncManager.onPeerActionReceived = { actionName, json ->
            when (actionName) {
                "SOS_TRIGGER" -> {
                    _emergencyStatus.value = EmergencyStatus.RESPONDER_ASSIGNED
                    _toastMessage.value = "⚠️ INCOMING EMERGENCY SOS TRIGGER FROM PEER DEVICE!"
                    viewModelScope.launch {
                        repository.addLog(
                            IncidentLog(
                                incidentCode = _activeIncidentCode.value,
                                timeFormatted = getCurrentFormattedTime(),
                                description = "Emergency Beacon received from connected victim device.",
                                type = "TRIGGER",
                                isCritical = true
                            )
                        )
                    }
                    startEtaCountdown()
                }
                "ACCEPT_ALERT" -> {
                    _isAlertAccepted.value = true
                    _emergencyStatus.value = EmergencyStatus.RESPONDER_ASSIGNED
                    _toastMessage.value = "Responder Vikram Singh accepted and is en-route!"
                    viewModelScope.launch {
                        repository.addLog(
                            IncidentLog(
                                incidentCode = _activeIncidentCode.value,
                                timeFormatted = getCurrentFormattedTime(),
                                description = "Officer Vikram Singh accepted SOS beacon. En route.",
                                type = "PATROL_STATUS"
                            )
                        )
                    }
                }
                "ON_SCENE" -> {
                    _isOnScene.value = true
                    _emergencyStatus.value = EmergencyStatus.ON_SCENE
                    _toastMessage.value = "Patrol Unit arrived on scene!"
                }
                "RESOLVED" -> {
                    _isResolved.value = true
                    _emergencyStatus.value = EmergencyStatus.RESOLVED
                    _toastMessage.value = "Incident marked resolved by responder."
                }
                "CANCEL_PIN" -> {
                    _emergencyStatus.value = EmergencyStatus.IDLE
                    _toastMessage.value = "Emergency SOS stood down by user."
                }
                "DURESS_ALERT" -> {
                    _toastMessage.value = "🚨 COVERT DURESS ALERT (PIN 9999) RECEIVED FROM VICTIM!"
                }
            }
        }

        // Incoming Notes from peer
        syncManager.onPeerNoteReceived = { noteText ->
            viewModelScope.launch {
                repository.addLog(
                    IncidentLog(
                        incidentCode = _activeIncidentCode.value,
                        timeFormatted = getCurrentFormattedTime(),
                        description = "Peer: $noteText",
                        type = "NOTE",
                        author = "Peer"
                    )
                )
                _toastMessage.value = "New incident note received from peer device."
            }
        }

        // Continuous Location sync broadcast job
        locationBroadcastJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (syncManager.connectionState.value == P2PState.CONNECTED) {
                    syncManager.broadcastLocation(locationManager.currentLocation.value)
                }
            }
        }
    }

    private fun getCurrentFormattedTime(): String {
        return SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
    }

    // P2P Two-Device Pairing Methods
    fun startHostingAsVictim() {
        syncManager.startHosting(ConnectionRole.VICTIM_BEACON)
        _toastMessage.value = "Host beacon open. Listening on IP: ${syncManager.currentHostIp.value}:8942"
    }

    fun startHostingAsResponder() {
        syncManager.startHosting(ConnectionRole.RESPONDER_PATROL)
        _toastMessage.value = "Responder listening on IP: ${syncManager.currentHostIp.value}:8942"
    }

    fun connectToPeer(hostIp: String, role: ConnectionRole) {
        syncManager.connectToHost(hostIp, role)
        _toastMessage.value = "Connecting to $hostIp..."
    }

    fun disconnectPeer() {
        syncManager.disconnect()
        _toastMessage.value = "Disconnected from peer device."
    }

    fun triggerSosEmergency(isSilent: Boolean = false) {
        _emergencyStatus.value = EmergencyStatus.RESPONDER_ASSIGNED
        val time = getCurrentFormattedTime()
        val note = if (isSilent) {
            "Silent Sentinel SOS Beacon activated via Long-Press."
        } else {
            "High Urgency SOS Beacon initiated via Master Button."
        }

        // Broadcast to connected peer device
        syncManager.broadcastAction("SOS_TRIGGER", JSONObject().apply {
            put("isSilent", isSilent)
            put("location", locationManager.currentLocation.value.locationName)
        })

        viewModelScope.launch {
            repository.addLog(
                IncidentLog(
                    incidentCode = _activeIncidentCode.value,
                    timeFormatted = time,
                    description = note,
                    type = "TRIGGER",
                    isCritical = true
                )
            )
            repository.addLog(
                IncidentLog(
                    incidentCode = _activeIncidentCode.value,
                    timeFormatted = time,
                    description = "Central 112 Dispatch auto-routed to Patrol #KA-04-P-8821.",
                    type = "DISPATCH",
                    isCritical = false
                )
            )
        }
        startEtaCountdown()
    }

    private fun startEtaCountdown() {
        etaTimerJob?.cancel()
        _etaSeconds.value = 210
        etaTimerJob = viewModelScope.launch {
            while (_etaSeconds.value > 0 && _emergencyStatus.value != EmergencyStatus.RESOLVED && _emergencyStatus.value != EmergencyStatus.IDLE) {
                delay(1000)
                _etaSeconds.value -= 1
                val mins = _etaSeconds.value / 60
                val secs = _etaSeconds.value % 60
                val distKm = String.format(Locale.US, "%.1f", (_etaSeconds.value.toDouble() / 210.0) * 1.1)
                _etaString.value = "$mins.${secs / 10} mins ($distKm km)"

                if (_etaSeconds.value <= 15) {
                    _emergencyStatus.value = EmergencyStatus.RESPONDER_ARRIVING
                }
            }
            if (_etaSeconds.value <= 0) {
                _emergencyStatus.value = EmergencyStatus.ON_SCENE
            }
        }
    }

    private fun startPatrolEscalationCountdown() {
        escalationTimerJob?.cancel()
        escalationTimerJob = viewModelScope.launch {
            while (_patrolEscalationSeconds.value > 0) {
                delay(1000)
                _patrolEscalationSeconds.value -= 1
            }
        }
    }

    fun toggleAudioBroadcasting(start: Boolean? = null) {
        val newState = start ?: !_isAudioBroadcasting.value
        _isAudioBroadcasting.value = newState
        if (newState) {
            audioStreamer.startStreamingVoice()
            _toastMessage.value = "🎙️ Real-time Audio Mic Active (Broadcasting to peer)..."
        } else {
            audioStreamer.stopStreamingVoice()
            _toastMessage.value = "Mic Stopped."
        }
    }

    fun toggleMicMute() {
        _isMicMuted.value = !_isMicMuted.value
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
    }

    fun toggleDutyStatus() {
        _isPatrolAvailable.value = !_isPatrolAvailable.value
        val statusText = if (_isPatrolAvailable.value) "Available for Instant Dispatch" else "Busy / On-Patrol"
        _toastMessage.value = "Patrol status updated: $statusText"
    }

    fun acceptPatrolAlert() {
        _isAlertAccepted.value = true
        _toastMessage.value = "Alert accepted! Intercept route locked."
        syncManager.broadcastAction("ACCEPT_ALERT")
        viewModelScope.launch {
            repository.addLog(
                IncidentLog(
                    incidentCode = "#INC-8942",
                    timeFormatted = getCurrentFormattedTime(),
                    description = "Officer Vikram Singh accepted dispatch. En-route with siren active.",
                    type = "PATROL_STATUS"
                )
            )
        }
    }

    fun passPatrolAlert() {
        _toastMessage.value = "Alert passed. Rerouted to next available patrol in Sector 29."
        syncManager.broadcastAction("PASS_ALERT")
    }

    fun markOnScene() {
        _isOnScene.value = true
        _emergencyStatus.value = EmergencyStatus.ON_SCENE
        _toastMessage.value = "Arrived on scene. Live telemetry verified."
        syncManager.broadcastAction("ON_SCENE")
        viewModelScope.launch {
            repository.addLog(
                IncidentLog(
                    incidentCode = _activeIncidentCode.value,
                    timeFormatted = getCurrentFormattedTime(),
                    description = "Officer Vikram Singh arrived on scene at victim location.",
                    type = "PATROL_STATUS"
                )
            )
        }
    }

    fun resolveIncident() {
        _isResolved.value = true
        _emergencyStatus.value = EmergencyStatus.RESOLVED
        _toastMessage.value = "Incident resolved and archived safely."
        syncManager.broadcastAction("RESOLVED")
        viewModelScope.launch {
            repository.addLog(
                IncidentLog(
                    incidentCode = _activeIncidentCode.value,
                    timeFormatted = getCurrentFormattedTime(),
                    description = "Incident marked resolved. Safe haven secured.",
                    type = "RESOLVED"
                )
            )
        }
    }

    fun addIncidentNote(noteText: String) {
        if (noteText.isBlank()) return
        val time = getCurrentFormattedTime()
        syncManager.broadcastIncidentNote(noteText)
        viewModelScope.launch {
            repository.addLog(
                IncidentLog(
                    incidentCode = _activeIncidentCode.value,
                    timeFormatted = time,
                    description = "Note: $noteText",
                    type = "NOTE",
                    author = "Victim Note"
                )
            )
            _toastMessage.value = "Incident note transmitted directly to Patrol Vehicle Unit."
        }
    }

    fun verifyPinAndCancelSos(enteredPin: String): Boolean {
        return if (enteredPin == safePin) {
            _emergencyStatus.value = EmergencyStatus.IDLE
            etaTimerJob?.cancel()
            _toastMessage.value = "PIN Verified. Emergency session stood down."
            syncManager.broadcastAction("CANCEL_PIN")
            viewModelScope.launch {
                repository.addLog(
                    IncidentLog(
                        incidentCode = _activeIncidentCode.value,
                        timeFormatted = getCurrentFormattedTime(),
                        description = "SOS Cancelled via authenticated Safe PIN.",
                        type = "RESOLVED"
                    )
                )
            }
            true
        } else if (enteredPin == duressPin) {
            _emergencyStatus.value = EmergencyStatus.RESPONDER_ASSIGNED
            _toastMessage.value = "Cancelling..."
            syncManager.broadcastAction("DURESS_ALERT")
            viewModelScope.launch {
                repository.addLog(
                    IncidentLog(
                        incidentCode = _activeIncidentCode.value,
                        timeFormatted = getCurrentFormattedTime(),
                        description = "⚠️ DURESS PIN (9999) TRIGGERED. Covert police escalation active!",
                        type = "TRIGGER",
                        isCritical = true
                    )
                )
            }
            true
        } else {
            _toastMessage.value = "Invalid PIN. Please re-enter."
            false
        }
    }

    fun scheduleFakeCall(delaySeconds: Int = 10) {
        _fakeCallScheduledSeconds.value = delaySeconds
        _toastMessage.value = "Fake safety incoming call scheduled in $delaySeconds seconds."
        fakeCallJob?.cancel()
        fakeCallJob = viewModelScope.launch {
            for (i in delaySeconds downTo 1) {
                _fakeCallScheduledSeconds.value = i
                delay(1000)
            }
            _fakeCallScheduledSeconds.value = null
            _isFakeCallRinging.value = true
        }
    }

    fun dismissFakeCall() {
        _isFakeCallRinging.value = false
        _fakeCallScheduledSeconds.value = null
    }

    fun addContact(name: String, phone: String, relation: String) {
        viewModelScope.launch {
            repository.addContact(
                EmergencyContact(
                    name = name,
                    phone = phone,
                    relationship = relation,
                    notifyLiveGps = true
                )
            )
            _toastMessage.value = "Added $name to Trusted Emergency Allies."
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioStreamer.release()
        syncManager.disconnect()
        locationManager.stopLocationUpdates()
    }
}
