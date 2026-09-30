package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.EmergencyContact
import com.example.model.IncidentLog
import com.example.model.SafeRoute
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyDao {
    // Contacts
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, id ASC")
    fun getAllContacts(): Flow<List<EmergencyContact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContact): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<EmergencyContact>)

    @Update
    suspend fun updateContact(contact: EmergencyContact)

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteContact(id: Long)

    // Logs
    @Query("SELECT * FROM incident_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<IncidentLog>>

    @Query("SELECT * FROM incident_logs WHERE incidentCode = :code ORDER BY timestamp ASC")
    fun getLogsForIncident(code: String): Flow<List<IncidentLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: IncidentLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<IncidentLog>)

    @Query("DELETE FROM incident_logs")
    suspend fun clearLogs()

    // Safe Routes
    @Query("SELECT * FROM safe_routes ORDER BY safetyScore DESC")
    fun getAllRoutes(): Flow<List<SafeRoute>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<SafeRoute>)
}
