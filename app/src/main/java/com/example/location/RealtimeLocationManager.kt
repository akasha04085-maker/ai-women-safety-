package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val locationName: String = "Cyber City Sector 28",
    val timestamp: Long = System.currentTimeMillis()
)

class RealtimeLocationManager(private val context: Context) {
    companion object {
        private const val TAG = "RealtimeLocationManager"
        
        // Base starting coordinates (Cyber City Sector 28)
        const val DEFAULT_VICTIM_LAT = 12.9716
        const val DEFAULT_VICTIM_LNG = 77.5946
        
        const val DEFAULT_PATROL_LAT = 12.9812
        const val DEFAULT_PATROL_LNG = 77.6025
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _currentLocation = MutableStateFlow(
        GpsCoordinate(
            latitude = DEFAULT_VICTIM_LAT,
            longitude = DEFAULT_VICTIM_LNG,
            accuracyMeters = 3.2f,
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

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _currentLocation.value = GpsCoordinate(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                speedKmh = location.speed * 3.6f,
                altitudeMeters = location.altitude,
                locationName = "Lat: ${String.format(Locale.US, "%.4f", location.latitude)}, Lng: ${String.format(Locale.US, "%.4f", location.longitude)}"
            )
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    locationListener
                )
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    1f,
                    locationListener
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Location provider access limited; using fused telemetry stream", e)
        }
    }

    fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
    }

    fun updateCurrentLocation(coord: GpsCoordinate) {
        _currentLocation.value = coord
    }

    fun updatePeerLocation(coord: GpsCoordinate) {
        _peerLocation.value = coord
    }

    /**
     * Simulates real-time patrol vehicle intercept movement towards victim.
     * Progress ratio from 0.0 (starting station) to 1.0 (on scene with victim).
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
     * Simulates minor live GPS telemetry drift / pedestrian movement during speech
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
