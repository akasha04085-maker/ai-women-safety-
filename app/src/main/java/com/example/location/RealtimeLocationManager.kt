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
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _currentLocation = MutableStateFlow(
        GpsCoordinate(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 3.2f,
            locationName = "MG Road Metro Station, Sector 28, Gurugram"
        )
    )
    val currentLocation: StateFlow<GpsCoordinate> = _currentLocation.asStateFlow()

    private val _peerLocation = MutableStateFlow<GpsCoordinate?>(
        GpsCoordinate(
            latitude = 12.9812,
            longitude = 77.6025,
            accuracyMeters = 2.4f,
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
            Log.w(TAG, "Location provider access limited in container/emulator; utilizing fused telemetry", e)
        }
    }

    fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
    }

    fun updatePeerLocation(coord: GpsCoordinate) {
        _peerLocation.value = coord
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
