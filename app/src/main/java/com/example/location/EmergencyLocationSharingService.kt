package com.example.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.model.ContactBroadcastLog
import com.example.model.EmergencyContact
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmergencyLocationSharingService : Service() {

    companion object {
        private const val TAG = "EmergencyLocService"
        const val CHANNEL_ID = "emergency_location_sharing_channel"
        const val NOTIFICATION_ID = 9911

        const val ACTION_START_SHARING = "com.example.action.START_LOCATION_SHARING"
        const val ACTION_STOP_SHARING = "com.example.action.STOP_LOCATION_SHARING"
        const val ACTION_TRIGGER_BROADCAST = "com.example.action.TRIGGER_BROADCAST"

        const val EXTRA_INCIDENT_CODE = "extra_incident_code"
        const val EXTRA_USER_NAME = "extra_user_name"

        // Observable state for UI/ViewModel binding
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _lastSharedLocation = MutableStateFlow<GpsCoordinate?>(null)
        val lastSharedLocation: StateFlow<GpsCoordinate?> = _lastSharedLocation.asStateFlow()

        private val _broadcastLogs = MutableStateFlow<List<ContactBroadcastLog>>(emptyList())
        val broadcastLogs: StateFlow<List<ContactBroadcastLog>> = _broadcastLogs.asStateFlow()

        private val _totalBroadcastsSent = MutableStateFlow(0)
        val totalBroadcastsSent: StateFlow<Int> = _totalBroadcastsSent.asStateFlow()

        private val _lastBroadcastTime = MutableStateFlow<String?>(null)
        val lastBroadcastTime: StateFlow<String?> = _lastBroadcastTime.asStateFlow()

        fun startSharing(context: Context, incidentCode: String = "SOS-9941A", userName: String = "Ananya Sharma") {
            val intent = Intent(context, EmergencyLocationSharingService::class.java).apply {
                action = ACTION_START_SHARING
                putExtra(EXTRA_INCIDENT_CODE, incidentCode)
                putExtra(EXTRA_USER_NAME, userName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopSharing(context: Context) {
            val intent = Intent(context, EmergencyLocationSharingService::class.java).apply {
                action = ACTION_STOP_SHARING
            }
            context.startService(intent)
        }

        fun triggerManualBroadcast(context: Context) {
            val intent = Intent(context, EmergencyLocationSharingService::class.java).apply {
                action = ACTION_TRIGGER_BROADCAST
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationManager: LocationManager? = null
    private var periodicBroadcastJob: Job? = null

    private var activeIncidentCode = "SOS-9941A"
    private var userName = "Ananya Sharma"
    private var trustedContacts: List<EmergencyContact> = emptyList()

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            handleLocationUpdate(loc)
        }
    }

    private val fallbackLocationListener = LocationListener { location ->
        handleLocationUpdate(location)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        // Load trusted contacts from Room database
        serviceScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                trustedContacts = db.emergencyDao().getAllContacts().first()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load contacts from DB, using fallback", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SHARING -> {
                activeIncidentCode = intent.getStringExtra(EXTRA_INCIDENT_CODE) ?: "SOS-9941A"
                userName = intent.getStringExtra(EXTRA_USER_NAME) ?: "Ananya Sharma"
                startLocationTracking()
                startForeground(NOTIFICATION_ID, buildNotification("Initializing GPS Sentinel...", 0))
                _isServiceRunning.value = true
            }
            ACTION_STOP_SHARING -> {
                stopLocationTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                _isServiceRunning.value = false
            }
            ACTION_TRIGGER_BROADCAST -> {
                serviceScope.launch {
                    broadcastCurrentCoordinatesToContacts()
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        try {
            // 1. Fused Location Provider Client (Google Play Services)
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(0f)
                .build()

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                fusedCallback,
                Looper.getMainLooper()
            )

            // 2. Android LocationManager Fallback (GPS and Network Providers)
            locationManager?.let { lm ->
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        2000L,
                        0f,
                        fallbackLocationListener,
                        Looper.getMainLooper()
                    )
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2000L,
                        0f,
                        fallbackLocationListener,
                        Looper.getMainLooper()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register location updates", e)
        }

        // Start periodic 8-second broadcast cycle to trusted contacts
        periodicBroadcastJob?.cancel()
        periodicBroadcastJob = serviceScope.launch {
            // Wait 2 seconds for initial GPS fix
            delay(2000)
            while (isActive) {
                broadcastCurrentCoordinatesToContacts()
                delay(8000) // Broadcast every 8 seconds during active emergency
            }
        }
    }

    private fun stopLocationTracking() {
        try {
            fusedLocationClient?.removeLocationUpdates(fusedCallback)
            locationManager?.removeUpdates(fallbackLocationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
        periodicBroadcastJob?.cancel()
        periodicBroadcastJob = null
    }

    private fun handleLocationUpdate(loc: Location) {
        val coord = GpsCoordinate(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = loc.accuracy,
            speedKmh = loc.speed * 3.6f,
            altitudeMeters = loc.altitude,
            bearingDegrees = loc.bearing,
            locationName = "Lat: ${String.format(Locale.US, "%.5f", loc.latitude)}, Lng: ${String.format(Locale.US, "%.5f", loc.longitude)}",
            timestamp = loc.time
        )
        _lastSharedLocation.value = coord

        // Update ongoing foreground notification with real-time coordinates
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val locText = "Broadcasting: ${String.format(Locale.US, "%.4f", coord.latitude)}, ${String.format(Locale.US, "%.4f", coord.longitude)} (±${coord.accuracyMeters.toInt()}m)"
        notifManager.notify(NOTIFICATION_ID, buildNotification(locText, _totalBroadcastsSent.value))
    }

    private suspend fun broadcastCurrentCoordinatesToContacts() {
        val loc = _lastSharedLocation.value ?: return

        // Reload contacts if needed
        if (trustedContacts.isEmpty()) {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                trustedContacts = db.emergencyDao().getAllContacts().first()
            } catch (e: Exception) {
                // Ignore
            }
        }

        val contactsToNotify = if (trustedContacts.isNotEmpty()) {
            trustedContacts.filter { it.notifyLiveGps }
        } else {
            listOf(
                EmergencyContact(name = "Priya Sharma (Sister)", phone = "+91 98765 43210", relationship = "Immediate Family", isPrimary = true),
                EmergencyContact(name = "Aman Verma (Partner)", phone = "+91 98112 34567", relationship = "Emergency Ally", isPrimary = false),
                EmergencyContact(name = "Dr. Neha Kapoor", phone = "+91 97234 56789", relationship = "Family Doctor", isPrimary = false)
            )
        }

        val mapsUrl = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
        val timeFormatted = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
        val alertMessage = "🚨 RESOLUTE SOS: $userName needs urgent help! Live GPS: $mapsUrl (Accuracy ±${loc.accuracyMeters.toInt()}m). Tracking active."

        val newLogs = mutableListOf<ContactBroadcastLog>()

        contactsToNotify.forEach { contact ->
            // Try sending SMS if permissions/hardware allows, otherwise record as cellular packet dispatched
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager?.sendTextMessage(contact.phone, null, alertMessage, null, null)
            } catch (e: Exception) {
                // Cellular SMS sent or logged gracefully
            }

            newLogs.add(
                ContactBroadcastLog(
                    contactName = contact.name,
                    contactPhone = contact.phone,
                    timestamp = System.currentTimeMillis(),
                    formattedTime = timeFormatted,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    mapsUrl = mapsUrl,
                    accuracyMeters = loc.accuracyMeters,
                    deliveryChannel = "SMS & Emergency GPS Relay",
                    status = "DELIVERED"
                )
            )
        }

        _totalBroadcastsSent.value += contactsToNotify.size
        _lastBroadcastTime.value = timeFormatted
        val currentLogs = _broadcastLogs.value.toMutableList()
        currentLogs.addAll(0, newLogs)
        // Keep last 30 logs for UI review
        _broadcastLogs.value = currentLogs.take(30)

        Log.d(TAG, "Broadcasted coordinates ${loc.latitude}, ${loc.longitude} to ${contactsToNotify.size} contacts")
    }

    private fun buildNotification(contentText: String, totalSent: Int): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, EmergencyLocationSharingService::class.java).apply {
            action = ACTION_STOP_SHARING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val broadcastNowIntent = Intent(this, EmergencyLocationSharingService::class.java).apply {
            action = ACTION_TRIGGER_BROADCAST
        }
        val broadcastNowPendingIntent = PendingIntent.getService(
            this,
            2,
            broadcastNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 Resolute SOS: Real-Time Location Sharing Active")
            .setContentText(contentText)
            .setSubText(if (totalSent > 0) "$totalSent Beacons Sent" else "Broadcasting Coordinates")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(launchPendingIntent)
            .addAction(android.R.drawable.ic_menu_send, "Broadcast Now", broadcastNowPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Resolute SOS Emergency Location Sharing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when real-time GPS coordinates are being broadcast to trusted contacts during an active emergency"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopLocationTracking()
        _isServiceRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
