package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import com.example.location.GpsCoordinate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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
    val pingMs: Long = 12
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

    private val _quickSessionCode = MutableStateFlow("SOS-8942")
    val quickSessionCode: StateFlow<String> = _quickSessionCode.asStateFlow()

    private val _pingLatencyMs = MutableStateFlow(12L)
    val pingLatencyMs: StateFlow<Long> = _pingLatencyMs.asStateFlow()

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

    init {
        detectLocalIp()
    }

    @Suppress("DEPRECATION")
    private fun detectLocalIp() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = Formatter.formatIpAddress(wifiManager?.connectionInfo?.ipAddress ?: 0)
            if (ip != "0.0.0.0" && ip.isNotBlank()) {
                _currentHostIp.value = ip
            } else {
                _currentHostIp.value = "192.168.1.100"
            }
        } catch (e: Exception) {
            _currentHostIp.value = "192.168.1.100"
        }
    }

    fun startHosting(role: ConnectionRole = ConnectionRole.VICTIM_BEACON) {
        disconnect()
        _myRole.value = role
        _connectionState.value = P2PState.HOSTING_WAITING
        detectLocalIp()

        scope.launch {
            try {
                serverSocket = ServerSocket(P2P_PORT)
                Log.d(TAG, "Host listening on port $P2P_PORT")

                startUdpDiscoveryBroadcast()

                val socket = serverSocket?.accept()
                if (socket != null) {
                    setupSocketConnection(socket, isHost = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error", e)
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

        scope.launch {
            try {
                val ip = if (targetIp.isBlank()) "127.0.0.1" else targetIp.trim()
                Log.d(TAG, "Connecting to host at $ip:$P2P_PORT")
                val socket = Socket(ip, P2P_PORT)
                setupSocketConnection(socket, isHost = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to host", e)
                _connectionState.value = P2PState.DISCONNECTED
            }
        }
    }

    private fun setupSocketConnection(socket: Socket, isHost: Boolean) {
        activeSocket = socket
        outputStream = DataOutputStream(socket.getOutputStream())
        inputStream = DataInputStream(socket.getInputStream())
        _connectionState.value = P2PState.CONNECTED

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
            put("deviceName", if (_myRole.value == ConnectionRole.VICTIM_BEACON) "Ananya S. (Victim Phone)" else "Officer Vikram Singh (Patrol #4)")
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
                val role = ConnectionRole.valueOf(roleStr)
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
                    latitude = json.optDouble("lat", 12.9716),
                    longitude = json.optDouble("lng", 77.5946),
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
                val actionName = json.optString("action", "")
                onPeerActionReceived?.invoke(actionName, json)
            }
            PKT_NOTE -> {
                val note = String(payload, StandardCharsets.UTF_8)
                onPeerNoteReceived?.invoke(note)
            }
            PKT_PING -> {
                val sendTime = String(payload, StandardCharsets.UTF_8).toLongOrNull() ?: 0L
                val pongJson = JSONObject().apply { put("sentTs", sendTime) }
                sendJsonPacket(PKT_PONG, pongJson)
            }
            PKT_PONG -> {
                val json = JSONObject(String(payload, StandardCharsets.UTF_8))
                val sentTs = json.optLong("sentTs", 0L)
                if (sentTs > 0) {
                    val rtt = System.currentTimeMillis() - sentTs
                    _pingLatencyMs.value = maxOf(4, rtt)
                }
            }
            PKT_SPEECH_STATE -> {
                val json = JSONObject(String(payload, StandardCharsets.UTF_8))
                val isSpeaking = json.optBoolean("isSpeaking", false)
                val amp = json.optDouble("amp", 0.0).toFloat()
                val hasLoc = json.has("lat")
                val coord = if (hasLoc) {
                    GpsCoordinate(
                        latitude = json.optDouble("lat", 12.9716),
                        longitude = json.optDouble("lng", 77.5946),
                        accuracyMeters = json.optDouble("acc", 2.0).toFloat(),
                        speedKmh = json.optDouble("spd", 0.0).toFloat(),
                        locationName = json.optString("name", "Speaking Peer"),
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
                val tsString = System.currentTimeMillis().toString()
                sendRawPacket(PKT_PING, tsString.toByteArray(StandardCharsets.UTF_8))
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
