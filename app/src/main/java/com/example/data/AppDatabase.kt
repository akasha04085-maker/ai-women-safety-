package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.EmergencyContact
import com.example.model.IncidentLog
import com.example.model.SafeRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [EmergencyContact::class, IncidentLog::class, SafeRoute::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun emergencyDao(): EmergencyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "resolute_emergency_db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getInstance(context).emergencyDao()
                            // Prepopulate default contacts
                            dao.insertContacts(
                                listOf(
                                    EmergencyContact(
                                        name = "Priya Sharma (Sister)",
                                        phone = "+91 98765 43210",
                                        relationship = "Immediate Family",
                                        isPrimary = true,
                                        notifyLiveGps = true
                                    ),
                                    EmergencyContact(
                                        name = "Aman Verma (Partner)",
                                        phone = "+91 98112 34567",
                                        relationship = "Emergency Ally",
                                        isPrimary = false,
                                        notifyLiveGps = true
                                    ),
                                    EmergencyContact(
                                        name = "Dr. Neha Kapoor",
                                        phone = "+91 97234 56789",
                                        relationship = "Family Doctor",
                                        isPrimary = false,
                                        notifyLiveGps = true
                                    )
                                )
                            )

                            // Prepopulate initial incident logs
                            dao.insertLogs(
                                listOf(
                                    IncidentLog(
                                        incidentCode = "SOS-9941A",
                                        timeFormatted = "10:42:01 AM",
                                        description = "SOS Beacon initiated via Hardware Triple-Press.",
                                        type = "TRIGGER",
                                        isCritical = true
                                    ),
                                    IncidentLog(
                                        incidentCode = "SOS-9941A",
                                        timeFormatted = "10:42:03 AM",
                                        description = "Central 112 Dispatch auto-routed to Patrol #KA-04-P-8821.",
                                        type = "DISPATCH",
                                        isCritical = false
                                    ),
                                    IncidentLog(
                                        incidentCode = "SOS-9941A",
                                        timeFormatted = "10:42:15 AM",
                                        description = "Officer Vikram Singh acknowledged beacon. En route via Golf Course Rd.",
                                        type = "PATROL_STATUS",
                                        isCritical = false
                                    )
                                )
                            )

                            // Prepopulate safe routes
                            dao.insertRoutes(
                                listOf(
                                    SafeRoute(
                                        title = "CyberHub -> MG Road Safe Corridor",
                                        destination = "MG Road Metro Station (Gate 2)",
                                        distanceKm = 1.8,
                                        etaMinutes = 7,
                                        safetyScore = 98,
                                        lightingLevel = "98% Illuminated (Smart LED)",
                                        cctvDensity = "32 CCTV AI Coverage",
                                        patrolZone = "Sector 28 Active Police Tier",
                                        isRecommended = true
                                    ),
                                    SafeRoute(
                                        title = "Galleria Market -> Sector 29 Transit",
                                        destination = "Sector 29 Safe Haven Hub",
                                        distanceKm = 2.4,
                                        etaMinutes = 11,
                                        safetyScore = 94,
                                        lightingLevel = "92% Illuminated",
                                        cctvDensity = "24 CCTV Network",
                                        patrolZone = "Sector 29 Rapid Unit",
                                        isRecommended = true
                                    ),
                                    SafeRoute(
                                        title = "Golf Course Extn -> Safe Shelter Point",
                                        destination = "Civil Hospital Safe Desk",
                                        distanceKm = 3.1,
                                        etaMinutes = 14,
                                        safetyScore = 89,
                                        lightingLevel = "85% Illuminated",
                                        cctvDensity = "18 CCTV Points",
                                        patrolZone = "Station 28 Alpha Unit",
                                        isRecommended = false
                                    )
                                )
                            )
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
