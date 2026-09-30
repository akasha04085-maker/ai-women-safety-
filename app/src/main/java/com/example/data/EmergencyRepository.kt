package com.example.data

import com.example.model.EmergencyContact
import com.example.model.IncidentLog
import com.example.model.SafeRoute
import kotlinx.coroutines.flow.Flow

class EmergencyRepository(private val dao: EmergencyDao) {
    val contacts: Flow<List<EmergencyContact>> = dao.getAllContacts()
    val incidentLogs: Flow<List<IncidentLog>> = dao.getAllLogs()
    val safeRoutes: Flow<List<SafeRoute>> = dao.getAllRoutes()

    fun getLogsForIncident(code: String): Flow<List<IncidentLog>> = dao.getLogsForIncident(code)

    suspend fun addContact(contact: EmergencyContact): Long = dao.insertContact(contact)

    suspend fun updateContact(contact: EmergencyContact) = dao.updateContact(contact)

    suspend fun deleteContact(id: Long) = dao.deleteContact(id)

    suspend fun addLog(log: IncidentLog): Long = dao.insertLog(log)

    suspend fun clearLogs() = dao.clearLogs()
}
