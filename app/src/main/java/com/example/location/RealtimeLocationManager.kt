package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GpsCoordinate(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 3.0f,
    val speedKmh: Float = 0.0f,
    val altitudeMeters: Double = 215.0,
    val bearingDegrees: Float = 0.0f,
    val locationName: String = "Cyber City Sector 28",
    val timestamp: Long = System.currentTimeMillis()
)

class RealtimeLocationManager(private val context: Context) {
    companion object {
        private const val TAG = "RealtimeLocationManager"

        // Default starting anchor: Cyber City Sector 28
        const val DEFAULT_VICTIM_LAT = 12.9716
        const val DEFAULT_VICTIM_LNG = 77.5946

        const val DEFAULT_PATROL_LAT = 12.9812
        const val DEFAULT_PATROL_LNG = 77.6025
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _currentLocation = MutableStateFlow(
        GpsCoordinate(
            latitude = DEFAULT_VICTIM_LAT,
            longitude = DEFAULT_VICTIM_LNG,
            accuracyMeters = 2.8f,
            speedKmh = 0.0f,
            locationName = "MG Road Metro Station, Sector 28, Gurugram"
        )
    )
    val currentLocation: StateFlow<GpsCoordinate> = _currentLocation.asStateFlow()

    private val _peerLocation = MutableStateFlow<GpsCoordinate?>(
        GpsCoordinate(
            latitude = DEFAULT_PATROL_LAT,
            longitude = DEFAULT_PATROL_LNG,
            accuracyMeters = 2.4f,
            speedKmh = 38.5f,
            locationName = "Sector 28 Station • Patrol Scorpio-4"
        )
    )
    val peerLocation: StateFlow<GpsCoordinate?> = _peerLocation.asStateFlow()

    private val _isGpsFixActive = MutableStateFlow(false)
    val isGpsFixActive: StateFlow<Boolean> = _isGpsFixActive.asStateFlow()

    private val _gpsProviderType = MutableStateFlow("Fused Realtime GPS")
    val gpsProviderType: StateFlow<String> = _gpsProviderType.asStateFlow()

    private val _isSimulatingWalk = MutableStateFlow(false)
    val isSimulatingWalk: StateFlow<Boolean> = _isSimulatingWalk.asStateFlow()

    private var walkSimulationJob: Job? = null
    private var isFusedListening = false

    private val fusedLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleNewLocation(location, "Google Play Services Fused Location")
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location, "Hardware GPS Provider")
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            _isGpsFixActive.value = true
        }
        override fun onProviderDisabled(provider: String) {
            _isGpsFixActive.value = false
        }
    }

    private fun handleNewLocation(location: Location, providerName: String) {
        _isGpsFixActive.value = true
        _gpsProviderType.value = providerName
        _currentLocation.value = GpsCoordinate(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.accuracy > 0) location.accuracy else 2.5f,
            speedKmh = location.speed * 3.6f,
            altitudeMeters = location.altitude,
            bearingDegrees = location.bearing,
            locationName = "Lat: ${String.format(Locale.US, "%.4f", location.latitude)}, Lng: ${String.format(Locale.US, "%.4f", location.longitude)}",
            timestamp = location.time
        )
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not yet granted; retaining simulated high-precision anchor")
            return
        }

        try {
            // 1. First get immediate last known location
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    handleNewLocation(loc, "Fused Location (Cached Fix)")
                }
            }

            // 2. Request high-accuracy continuous updates
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0.5f)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                fusedLocationCallback,
                Looper.getMainLooper()
            )
            isFusedListening = true
            _isGpsFixActive.value = true
            _gpsProviderType.value = "Fused High-Accuracy GPS"
        } catch (e: Exception) {
            Log.w(TAG, "Fused location unavailable; falling back to LocationManager", e)
            fallbackToStandardLocationManager()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackToStandardLocationManager() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.5f,
                    locationListener
                )
                _isGpsFixActive.value = true
                _gpsProviderType.value = "Hardware GPS"
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0.5f,
                    locationListener
                )
                _isGpsFixActive.value = true
                _gpsProviderType.value = "Network Cell/WiFi"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Standard location manager error", e)
        }
    }

    fun stopLocationUpdates() {
        try {
            if (isFusedListening) {
                fusedLocationClient.removeLocationUpdates(fusedLocationCallback)
                isFusedListening = false
            }
            locationManager.removeUpdates(locationListener)
            _isGpsFixActive.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }

    fun updateCurrentLocation(coord: GpsCoordinate) {
        _currentLocation.value = coord
    }

    fun updatePeerLocation(coord: GpsCoordinate) {
        _peerLocation.value = coord
    }

    /**
     * Toggles live simulation walk mode for browser emulator testing.
     * Gradually moves user position towards emergency hubs with realistic speed and bearing.
     */
    fun toggleSimulatedWalk() {
        val willSimulate = !_isSimulatingWalk.value
        _isSimulatingWalk.value = willSimulate

        walkSimulationJob?.cancel()
        if (willSimulate) {
            _gpsProviderType.value = "Live Telemetry Simulator (4.8 km/h)"
            _isGpsFixActive.value = true
            walkSimulationJob = scope.launch {
                var step = 0
                while (isActive && _isSimulatingWalk.value) {
                    val curr = _currentLocation.value
                    // Walk in a circular/patrol path
                    val angle = (step * 8.0) * (Math.PI / 180.0)
                    val deltaLat = sin(angle) * 0.00012
                    val deltaLng = cos(angle) * 0.00012

                    _currentLocation.value = curr.copy(
                        latitude = DEFAULT_VICTIM_LAT + deltaLat,
                        longitude = DEFAULT_VICTIM_LNG + deltaLng,
                        speedKmh = 4.8f,
                        accuracyMeters = 1.8f,
                        bearingDegrees = ((step * 8) % 360).toFloat(),
                        locationName = "Walking • Cyber City Sector 28 Corridor",
                        timestamp = System.currentTimeMillis()
                    )
                    step++
                    delay(1200)
                }
            }
        } else {
            _gpsProviderType.value = "Stationary Fix (±2.5m)"
            _currentLocation.value = _currentLocation.value.copy(speedKmh = 0f)
        }
    }

    /**
     * Simulates real-time patrol vehicle intercept movement towards victim.
     */
    fun updatePatrolProgress(progressRatio: Float) {
        val clampedRatio = progressRatio.coerceIn(0f, 1f)
        val victim = _currentLocation.value
        val lat = DEFAULT_PATROL_LAT + (victim.latitude - DEFAULT_PATROL_LAT) * clampedRatio
        val lng = DEFAULT_PATROL_LNG + (victim.longitude - DEFAULT_PATROL_LNG) * clampedRatio
        val speed = if (clampedRatio >= 0.98f) 0.0f else (35.0f + (clampedRatio * 15.0f))

        _peerLocation.value = GpsCoordinate(
            latitude = lat,
            longitude = lng,
            accuracyMeters = 2.1f,
            speedKmh = speed,
            locationName = if (clampedRatio >= 0.98f) "On Scene • Intercept Secured" else "En Route • Speed ${String.format(Locale.US, "%.0f", speed)} km/h"
        )
    }

    /**
     * Simulates minor live GPS telemetry drift during speech
     */
    fun nudgeLocationDuringSpeech(isCitizen: Boolean, stepCount: Int) {
        val drift = (stepCount % 5 - 2) * 0.00008
        if (isCitizen) {
            val curr = _currentLocation.value
            _currentLocation.value = curr.copy(
                latitude = curr.latitude + drift * 0.5,
                longitude = curr.longitude + drift * 0.4,
                speedKmh = 3.6f,
                timestamp = System.currentTimeMillis()
            )
        } else {
            val curr = _peerLocation.value ?: return
            _peerLocation.value = curr.copy(
                latitude = curr.latitude + drift * 0.8,
                longitude = curr.longitude + drift * 0.7,
                speedKmh = 42.0f,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    fun calculateDistanceKm(loc1: GpsCoordinate, loc2: GpsCoordinate): Double {
        val r = 6371.0 // Earth radius in km
        val latDistance = Math.toRadians(loc2.latitude - loc1.latitude)
        val lonDistance = Math.toRadians(loc2.longitude - loc1.longitude)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(loc1.latitude)) * cos(Math.toRadians(loc2.latitude)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun calculateEtaMinutes(distanceKm: Double, averageSpeedKmh: Double = 35.0): Double {
        val hours = distanceKm / averageSpeedKmh
        return maxOf(0.5, hours * 60.0)
    }
}
