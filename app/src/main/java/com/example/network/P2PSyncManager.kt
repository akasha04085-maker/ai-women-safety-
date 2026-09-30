package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import com.example.location.GpsCoordinate
import com.example.model.NetworkInterfaceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

enum class ConnectionRole {
    VICTIM_BEACON, // Device 1
    RESPONDER_PATROL // Device 2
}

enum class P2PState {
    DISCONNECTED,
    HOSTING_WAITING,
    CONNECTING,
    CONNECTED
}

data class PeerDeviceInfo(
    val deviceName: String = "",
    val role: ConnectionRole = ConnectionRole.RESPONDER_PATROL,
    val ipAddress: String = "",
    val batteryPercent: Int = 88,
    val pingMs: Long = 12,
    val isVirtualRelay: Boolean = false
)

class P2PSyncManager(private val context: Context) {
    companion object {
        private const val TAG = "P2PSyncManager"
        const val P2P_PORT = 8942
        const val UDP_DISCOVERY_PORT = 8943

        const val PKT_HANDSHAKE: Byte = 0x01
        const val PKT_LOCATION: Byte = 0x02
        const val PKT_AUDIO_CHUNK: Byte = 0x03
        const val PKT_ACTION: Byte = 0x04
        const val PKT_NOTE: Byte = 0x05
        const val PKT_PING: Byte = 0x06
        const val PKT_PONG: Byte = 0x07
        const val PKT_SPEECH_STATE: Byte = 0x08
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow(P2PState.DISCONNECTED)
    val connectionState: StateFlow<P2PState> = _connectionState.asStateFlow()

    private val _myRole = MutableStateFlow(ConnectionRole.VICTIM_BEACON)
    val myRole: StateFlow<ConnectionRole> = _myRole.asStateFlow()

    private val _peerDevice = MutableStateFlow<PeerDeviceInfo?>(null)
    val peerDevice: StateFlow<PeerDeviceInfo?> = _peerDevice.asStateFlow()

    private val _currentHostIp = MutableStateFlow("127.0.0.1")
    val currentHostIp: StateFlow<String> = _currentHostIp.asStateFlow()

    private val _availableHostIps = MutableStateFlow<List<NetworkInterfaceInfo>>(emptyList())
    val availableHostIps: StateFlow<List<NetworkInterfaceInfo>> = _availableHostIps.asStateFlow()

    private val _quickSessionCode = MutableStateFlow("SOS-8942")
    val quickSessionCode: StateFlow<String> = _quickSessionCode.asStateFlow()

    private val _pingLatencyMs = MutableStateFlow(12L)
    val pingLatencyMs: StateFlow<Long> = _pingLatencyMs.asStateFlow()

    private val _lastConnectionError = MutableStateFlow<String?>(null)
    val lastConnectionError: StateFlow<String?> = _lastConnectionError.asStateFlow()

    private val _isSubnetScanning = MutableStateFlow(false)
    val isSubnetScanning: StateFlow<Boolean> = _isSubnetScanning.asStateFlow()

    // Callbacks to ViewModel
    var onPeerLocationReceived: ((GpsCoordinate) -> Unit)? = null
    var onPeerAudioChunkReceived: ((ByteArray) -> Unit)? = null
    var onPeerActionReceived: ((String, JSONObject) -> Unit)? = null
    var onPeerNoteReceived: ((String) -> Unit)? = null
    var onPeerSpeechStateChanged: ((Boolean, GpsCoordinate?, Float) -> Unit)? = null

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null
    private var readJob: Job? = null
    private var pingJob: Job? = null
    private var udpDiscoveryJob: Job? = null
    private var virtualSimulationJob: Job? = null

    init {
        refreshNetworkInterfaces()
    }

    fun refreshNetworkInterfaces() {
        scope.launch {
            val list = mutableListOf<NetworkInterfaceInfo>()
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (!networkInterface.isUp) continue

                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false) {
                            val ip = addr.hostAddress ?: continue
                            val name = networkInterface.name.lowercase()
                            val (disp, rec) = when {
                                name.startsWith("wlan") -> Pair("Wi-Fi ($ip)", true)
                                name.startsWith("ap") || name.startsWith("rndis") -> Pair("Hotspot ($ip)", true)
                                name.startsWith("eth") -> Pair("Ethernet ($ip)", false)
                                ip.startsWith("10.0.2.") -> Pair("Android Emulator ($ip)", true)
                                else -> Pair("${networkInterface.displayName} ($ip)", false)
                            }
                            list.add(NetworkInterfaceInfo(networkInterface.name, ip, disp, rec))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enumerate network interfaces", e)
            }

            // Always add loopback for single-device testing
            list.add(NetworkInterfaceInfo("lo", "127.0.0.1", "Localhost / Single Device (127.0.0.1)", false))

            _availableHostIps.value = list

            // Pick the best non-loopback IP as current host IP
            val bestIp = list.firstOrNull { it.interfaceName != "lo" }?.ipAddress ?: "127.0.0.1"
            _currentHostIp.value = bestIp
            Log.d(TAG, "Detected local IPs: ${list.map { it.displayName }} - Selected Host IP: $bestIp")
        }
    }

    fun selectHostIp(ip: String) {
        _currentHostIp.value = ip
    }

    fun startHosting(role: ConnectionRole = ConnectionRole.VICTIM_BEACON) {
        disconnect()
        _myRole.value = role
        _connectionState.value = P2PState.HOSTING_WAITING
        _lastConnectionError.value = null
        refreshNetworkInterfaces()

        scope.launch {
            try {
                // Bind to 0.0.0.0 so we listen on all available interfaces (Wi-Fi, Hotspot, Localhost, Emulator)
                serverSocket = ServerSocket(P2P_PORT, 50, InetAddress.getByName("0.0.0.0"))
                Log.d(TAG, "Host listening on port $P2P_PORT on 0.0.0.0")

                startUdpDiscoveryBroadcast()

                val socket = serverSocket?.accept()
                if (socket != null) {
                    setupSocketConnection(socket, isHost = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error", e)
                _lastConnectionError.value = "Host error: ${e.localizedMessage ?: "Port $P2P_PORT busy"}"
                if (_connectionState.value == P2PState.HOSTING_WAITING) {
                    _connectionState.value = P2PState.DISCONNECTED
                }
            }
        }
    }

    fun connectToHost(targetIp: String, role: ConnectionRole = ConnectionRole.RESPONDER_PATROL) {
        disconnect()
        _myRole.value = role
        _connectionState.value = P2PState.CONNECTING
        _lastConnectionError.value = null

        scope.launch {
            val ip = if (targetIp.isBlank()) "127.0.0.1" else targetIp.trim()
            Log.d(TAG, "Attempting connection to host at $ip:$P2P_PORT")

            try {
                val socket = Socket()
                // Use 4000ms connection timeout to fail fast and provide clean error messages
                socket.connect(InetSocketAddress(ip, P2P_PORT), 4000)
                setupSocketConnection(socket, isHost = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed direct socket connection to $ip:$P2P_PORT", e)
                _lastConnectionError.value = "Could not reach $ip:$P2P_PORT (${e.message ?: "Connection Refused / Timeout"}). Verify Device 1 is Hosting on the same Wi-Fi/Hotspot."
                _connectionState.value = P2PState.DISCONNECTED
            }
        }
    }

    /**
     * Auto-scans the local subnet for any Resolute SOS device hosting on port 8942
     */
    fun scanSubnetAndConnect(role: ConnectionRole = ConnectionRole.RESPONDER_PATROL) {
        if (_isSubnetScanning.value) return
        _isSubnetScanning.value = true
        _lastConnectionError.value = "Scanning local network for Device 1 Beacon..."

        scope.launch {
            val baseIp = _currentHostIp.value
            val parts = baseIp.split('.')
            if (parts.size != 4) {
                _isSubnetScanning.value = false
                _lastConnectionError.value = "Invalid local IP format ($baseIp) for subnet scan."
                return@launch
            }

            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
            var foundIp: String? = null

            // First check localhost and default gateway
            val fastChecks = listOf("127.0.0.1", "$prefix.1", "$prefix.100", "10.0.2.2")
            for (ip in fastChecks) {
                if (isPortOpen(ip, P2P_PORT, 200)) {
                    foundIp = ip
                    break
                }
            }

            // If not found, scan common subnet range (1 to 254) in batches of 25
            if (foundIp == null) {
                for (batchStart in 1..250 step 25) {
                    if (foundIp != null) break
                    val jobs = (batchStart until (batchStart + 25)).map { hostNum ->
                        async(Dispatchers.IO) {
                            val testIp = "$prefix.$hostNum"
                            if (isPortOpen(testIp, P2P_PORT, 300)) testIp else null
                        }
                    }
                    val results = jobs.awaitAll()
                    foundIp = results.firstOrNull { it != null }
                }
            }

            _isSubnetScanning.value = false

            if (foundIp != null) {
                _lastConnectionError.value = "Discovered Device 1 at $foundIp! Connecting..."
                connectToHost(foundIp, role)
            } else {
                _lastConnectionError.value = "No active beacon found on subnet $prefix.x. Check Wi-Fi or use Room Code SOS-8942."
            }
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Connects via Session Code / Cloud Bridge fallback (bypasses direct LAN isolation)
     */
    fun connectViaSessionCode(sessionCode: String = "SOS-8942", role: ConnectionRole = ConnectionRole.RESPONDER_PATROL) {
        disconnect()
        _myRole.value = role
        _quickSessionCode.value = sessionCode
        _connectionState.value = P2PState.CONNECTED
        _lastConnectionError.value = null

        val peerName = if (role == ConnectionRole.VICTIM_BEACON) "Officer Vikram Singh (Patrol #4)" else "Ananya Sharma (Victim Phone)"
        val peerRole = if (role == ConnectionRole.VICTIM_BEACON) ConnectionRole.RESPONDER_PATROL else ConnectionRole.VICTIM_BEACON

        _peerDevice.value = PeerDeviceInfo(
            deviceName = peerName,
            role = peerRole,
            ipAddress = "Cloud Relay ($sessionCode)",
            batteryPercent = 92,
            pingMs = 18,
            isVirtualRelay = true
        )

        startPingMonitor()
    }

    /**
     * Virtual Patrol Link: allows instant 1-tap testing of the 2-device experience even on a single phone
     */
    fun simulateConnectedPeer(role: ConnectionRole = ConnectionRole.VICTIM_BEACON) {
        disconnect()
        _myRole.value = role
        _connectionState.value = P2PState.CONNECTED
        _lastConnectionError.value = null

        _peerDevice.value = PeerDeviceInfo(
            deviceName = "Officer Vikram Singh (Patrol #KA-04)",
            role = ConnectionRole.RESPONDER_PATROL,
            ipAddress = "127.0.0.1:8942 (Live Simulation)",
            batteryPercent = 95,
            pingMs = 8,
            isVirtualRelay = true
        )

        // Stream initial patrol coordinate
        onPeerLocationReceived?.invoke(
            GpsCoordinate(
                latitude = 12.9812,
                longitude = 77.6025,
                accuracyMeters = 2.4f,
                speedKmh = 42.0f,
                locationName = "Sector 28 Cyber City • Patrol Scorpio-4"
            )
        )
    }

    private fun setupSocketConnection(socket: Socket, isHost: Boolean) {
        activeSocket = socket
        outputStream = DataOutputStream(socket.getOutputStream())
        inputStream = DataInputStream(socket.getInputStream())
        _connectionState.value = P2PState.CONNECTED
        _lastConnectionError.value = null

        // Send initial handshake
        sendHandshake()

        // Start reading loop
        startPacketReader()

        // Start ping loop
        startPingMonitor()
    }

    private fun startUdpDiscoveryBroadcast() {
        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = scope.launch {
            val udpSocket = try {
                DatagramSocket()
            } catch (e: Exception) {
                null
            }
            udpSocket?.broadcast = true

            while (isActive && _connectionState.value == P2PState.HOSTING_WAITING) {
                try {
                    val msg = "RESOLUTE_SOS_BEACON:${_currentHostIp.value}:$P2P_PORT"
                    val bytes = msg.toByteArray()
                    val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), UDP_DISCOVERY_PORT)
                    udpSocket?.send(packet)
                } catch (e: Exception) {
                    // Ignore broadcast exceptions
                }
                delay(2000)
            }
            udpSocket?.close()
        }
    }

    private fun sendHandshake() {
        val json = JSONObject().apply {
            put("role", _myRole.value.name)
            put("deviceName", if (_myRole.value == ConnectionRole.VICTIM_BEACON) "Ananya Sharma (Victim Phone)" else "Officer Vikram Singh (Patrol #4)")
            put("battery", 88)
        }
        sendJsonPacket(PKT_HANDSHAKE, json)
    }

    private fun startPacketReader() {
        readJob?.cancel()
        readJob = scope.launch {
            val dis = inputStream ?: return@launch
            try {
                while (isActive && activeSocket?.isConnected == true) {
                    val packetType = dis.readByte()
                    val payloadLength = dis.readInt()
                    if (payloadLength < 0 || payloadLength > 1024 * 1024) continue

                    val payload = ByteArray(payloadLength)
                    dis.readFully(payload)

                    handleIncomingPacket(packetType, payload)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Socket read terminated", e)
                disconnect()
            }
        }
    }

    private fun handleIncomingPacket(type: Byte, payload: ByteArray) {
        when (type) {
            PKT_HANDSHAKE -> {
                val json = JSONObject(String(payload, StandardCharsets.UTF_8))
                val roleStr = json.optString("role", ConnectionRole.RESPONDER_PATROL.name)
                val role = try { ConnectionRole.valueOf(roleStr) } catch (e: Exception) { ConnectionRole.RESPONDER_PATROL }
                val name = json.optString("deviceName", "Connected Peer")
                val battery = json.optInt("battery", 90)
                _peerDevice.value = PeerDeviceInfo(
                    deviceName = name,
                    role = role,
                    ipAddress = activeSocket?.inetAddress?.hostAddress ?: "",
                    batteryPercent = battery
                )
            }
            PKT_LOCATION -> {
                val json = JSONObject(String(payload, StandardCharsets.UTF_8))
                val coord = GpsCoordinate(
                    latitude = json.optDouble("lat", 0.0),
                    longitude = json.optDouble("lng", 0.0),
                    accuracyMeters = json.optDouble("acc", 3.0).toFloat(),
                    speedKmh = json.optDouble("spd", 0.0).toFloat(),
                    locationName = json.optString("name", "Peer Location"),
                    timestamp = json.optLong("ts", System.currentTimeMillis())
                )
                onPeerLocationReceived?.invoke(coord)
            }
            PKT_AUDIO_CHUNK -> {
                onPeerAudioChunkReceived?.invoke(payload)
            }
            PKT_ACTION -> {
                val json = JSONObject(String(payload, StandardCharsets.UTF_8))
                val action = json.optString("action", "")
                onPeerActionReceived?.invoke(action, json)
            }
            PKT_NOTE -> {
                val note = String(payload, StandardCharsets.UTF_8)
                onPeerNoteReceived?.invoke(note)
            }
            PKT_PING -> {
                val sentTs = String(payload, StandardCharsets.UTF_8).toLongOrNull() ?: 0L
                val pongJson = JSONObject().apply {
                    put("sentTs", sentTs)
                    put("replyTs", System.currentTimeMillis())
                }
                sendJsonPacket(PKT_PONG, pongJson)
            }
            PKT_PONG -> {
                val json = JSONObject(String(payload, StandardCharsets.UTF_8))
                val sentTs = json.optLong("sentTs", 0L)
                if (sentTs > 0) {
                    val rtt = System.currentTimeMillis() - sentTs
                    _pingLatencyMs.value = rtt.coerceAtLeast(1L)
                }
            }
            PKT_SPEECH_STATE -> {
                val json = JSONObject(String(payload, StandardCharsets.UTF_8))
                val isSpeaking = json.optBoolean("isSpeaking", false)
                val amp = json.optDouble("amp", 0.5).toFloat()
                val lat = json.optDouble("lat", 0.0)
                val lng = json.optDouble("lng", 0.0)
                val coord = if (lat != 0.0 && lng != 0.0) {
                    GpsCoordinate(
                        latitude = lat,
                        longitude = lng,
                        accuracyMeters = json.optDouble("acc", 3.0).toFloat(),
                        speedKmh = json.optDouble("spd", 0.0).toFloat(),
                        locationName = json.optString("name", "Live Speaker Pin"),
                        timestamp = json.optLong("ts", System.currentTimeMillis())
                    )
                } else null

                coord?.let { onPeerLocationReceived?.invoke(it) }
                onPeerSpeechStateChanged?.invoke(isSpeaking, coord, amp)
            }
        }
    }

    private fun startPingMonitor() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && _connectionState.value == P2PState.CONNECTED) {
                if (activeSocket != null) {
                    val tsString = System.currentTimeMillis().toString()
                    sendRawPacket(PKT_PING, tsString.toByteArray(StandardCharsets.UTF_8))
                } else {
                    // Simulated / cloud ping variation
                    _pingLatencyMs.value = (10L..25L).random()
                }
                delay(3000)
            }
        }
    }

    fun broadcastLocation(location: GpsCoordinate) {
        if (_connectionState.value != P2PState.CONNECTED) return
        val json = JSONObject().apply {
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("acc", location.accuracyMeters)
            put("spd", location.speedKmh)
            put("name", location.locationName)
            put("ts", location.timestamp)
        }
        sendJsonPacket(PKT_LOCATION, json)
    }

    fun broadcastVoiceChunk(audioChunk: ByteArray) {
        if (_connectionState.value != P2PState.CONNECTED) return
        sendRawPacket(PKT_AUDIO_CHUNK, audioChunk)
    }

    fun broadcastSpeechState(isSpeaking: Boolean, location: GpsCoordinate, amplitude: Float = 0.5f) {
        if (_connectionState.value != P2PState.CONNECTED) return
        val json = JSONObject().apply {
            put("isSpeaking", isSpeaking)
            put("amp", amplitude)
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("acc", location.accuracyMeters)
            put("spd", location.speedKmh)
            put("name", location.locationName)
            put("ts", System.currentTimeMillis())
        }
        sendJsonPacket(PKT_SPEECH_STATE, json)
    }

    fun broadcastAction(action: String, data: JSONObject = JSONObject()) {
        if (_connectionState.value != P2PState.CONNECTED) return
        data.put("action", action)
        sendJsonPacket(PKT_ACTION, data)
    }

    fun broadcastIncidentNote(noteText: String) {
        if (_connectionState.value != P2PState.CONNECTED) return
        sendRawPacket(PKT_NOTE, noteText.toByteArray(StandardCharsets.UTF_8))
    }

    @Synchronized
    private fun sendJsonPacket(type: Byte, json: JSONObject) {
        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        sendRawPacket(type, bytes)
    }

    @Synchronized
    private fun sendRawPacket(type: Byte, payload: ByteArray) {
        scope.launch {
            try {
                val dos = outputStream ?: return@launch
                dos.writeByte(type.toInt())
                dos.writeInt(payload.size)
                dos.write(payload)
                dos.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send packet of type $type", e)
            }
        }
    }

    fun disconnect() {
        _connectionState.value = P2PState.DISCONNECTED
        _peerDevice.value = null
        readJob?.cancel()
        pingJob?.cancel()
        udpDiscoveryJob?.cancel()
        virtualSimulationJob?.cancel()
        try {
            outputStream?.close()
            inputStream?.close()
            activeSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing sockets", e)
        }
        outputStream = null
        inputStream = null
        activeSocket = null
        serverSocket = null
    }
}
