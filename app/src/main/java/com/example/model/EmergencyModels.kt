package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val relationship: String,
    val isPrimary: Boolean = false,
    val notifyLiveGps: Boolean = true,
    val avatarUrl: String = ""
)

@Entity(tableName = "incident_logs")
data class IncidentLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val incidentCode: String,
    val timestamp: Long = System.currentTimeMillis(),
    val timeFormatted: String,
    val description: String,
    val type: String, // "TRIGGER", "DISPATCH", "NOTE", "RESOLVED", "PATROL_STATUS", "VOICE"
    val isCritical: Boolean = false,
    val author: String = "System"
)

@Entity(tableName = "safe_routes")
data class SafeRoute(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val destination: String,
    val distanceKm: Double,
    val etaMinutes: Int,
    val safetyScore: Int, // e.g. 98/100
    val lightingLevel: String, // "High (96% Lit)"
    val cctvDensity: String, // "Dense (24 CCTV Grid)"
    val patrolZone: String, // "Sector 28 Active Grid"
    val isRecommended: Boolean = true
)

enum class EmergencyStatus {
    IDLE,
    ACTIVATING,
    ACTIVE_SEARCHING,
    RESPONDER_ASSIGNED,
    RESPONDER_ARRIVING,
    ON_SCENE,
    RESOLVED
}

enum class UserRole(
    val title: String,
    val subtitle: String,
    val defaultName: String,
    val defaultDesignation: String,
    val badge: String,
    val badgeId: String
) {
    CITIZEN(
        title = "Citizen / Protected User",
        subtitle = "Aura Women Safety SOS Beacon",
        defaultName = "Ananya Sharma",
        defaultDesignation = "Cyber City Safe Zone • ID: #AG-4410",
        badge = "CITIZEN BEACON",
        badgeId = "AG-4410"
    ),
    RESPONDER_PATROL(
        title = "Patrol Officer / QRT",
        subtitle = "Quick Response Unit & Tactical Intercept",
        defaultName = "SI Vikram Singh",
        defaultDesignation = "Patrol Scorpio-4 • Sector 28 Station",
        badge = "ARMED RESPONDER",
        badgeId = "POL-8902"
    ),
    COMMAND_CENTER(
        title = "Command Center / Dispatch",
        subtitle = "Central 112 Incident Command & PostGIS Radar",
        defaultName = "Inspector Radhika Roy",
        defaultDesignation = "Chief Dispatch Officer • Central 112 Grid",
        badge = "COMMAND DISPATCH",
        badgeId = "DISPATCH-112"
    )
}

data class UserProfile(
    val id: String,
    val name: String,
    val role: UserRole,
    val designation: String,
    val phone: String,
    val avatarUrl: String = "",
    val sector: String = "Sector 28 Cyber City",
    val status: String = "Active & On-Duty"
)

data class LiveSpeakingState(
    val isSpeaking: Boolean = false,
    val speakerRole: UserRole? = null,
    val speakerName: String = "",
    val speedKmh: Float = 0f,
    val audioAmplitude: Float = 0f,
    val isUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class OfficerInfo(
    val id: String = "POL-8902",
    val name: String = "Officer Vikram Singh",
    val rank: String = "Sub-Inspector",
    val station: String = "Sector 28 Station • Cyber City",
    val vehicleId: String = "Mahindra Scorpio Patrol 4",
    val vehiclePlate: String = "KA-04-P-8821",
    val phone: String = "112",
    val isVerified: Boolean = true,
    val isAvailable: Boolean = true,
    val photoUrl: String = "https://lh3.googleusercontent.com/aida-public/AB6AXuD8WqSoo-DW1yNGc-7JtJW6V_NErh4f3qErzVQZM14P7QjU5pgMpsB-BM8sgodMk20IOSOdsRLBri5dAxMqlcrx2fvLC7AVExIULqs__Da6w8tQuAJUou93Od4fHK4MW78pZAz8ZWg_9VpqLpsqgjraqLgRSL1jv-5y61zWTJGNNhnJkwnncQVm4Kx7CWkahIiN8kHhGo8K095hD4uZlHG7ctd2E1-70V7HcZVhUE4iUZM2E9JgiRXm"
)

data class IncidentCard(
    val code: String,
    val victimName: String,
    val location: String,
    val status: String,
    val etaPacing: String,
    val responderName: String,
    val responderUnit: String,
    val autoEscalateSeconds: Int,
    val isCritical: Boolean = true
)

enum class HubType(
    val title: String,
    val chipLabel: String,
    val colorHex: Long
) {
    POLICE("Police Station & QRT", "POLICE", 0xFF1E40AF),
    HOSPITAL("Hospital Trauma Desk", "HOSPITAL", 0xFFDC2626),
    SAFE_HAVEN("Women Safe Haven", "SAFE HAVEN", 0xFF7C3AED),
    FIRE_STATION("Fire & Rescue Station", "FIRE", 0xFFEA580C),
    PATROL_POST("Tactical Patrol Post", "PATROL", 0xFF059669)
}

data class EmergencyHub(
    val id: String,
    val name: String,
    val type: HubType,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val phone: String,
    val operates24Hours: Boolean = true,
    val facilities: List<String> = emptyList(),
    val distanceMeters: Double = 0.0,
    val distanceFormatted: String = "350 m",
    val etaWalkingMinutes: Int = 4,
    val etaDrivingMinutes: Int = 2,
    val isVerified: Boolean = true,
    val description: String = ""
)

data class ContactBroadcastLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val contactName: String,
    val contactPhone: String,
    val timestamp: Long = System.currentTimeMillis(),
    val formattedTime: String,
    val latitude: Double,
    val longitude: Double,
    val mapsUrl: String,
    val accuracyMeters: Float = 3.0f,
    val deliveryChannel: String = "SMS / Cellular Dispatch",
    val status: String = "DELIVERED"
)

data class NetworkInterfaceInfo(
    val interfaceName: String,
    val ipAddress: String,
    val displayName: String,
    val isRecommended: Boolean = false
)

