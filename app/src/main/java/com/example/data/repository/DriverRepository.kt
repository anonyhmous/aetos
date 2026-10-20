package com.example.data.repository

import com.example.data.local.AppDao
import com.example.data.local.CustomAreaEntity
import com.example.data.local.FareSettingsEntity
import com.example.data.local.RideLogEntity
import com.example.data.local.UiMutationEntity
import com.example.model.MutationSeverity
import com.example.model.MutationType
import com.example.model.RideTelemetryState
import com.example.model.UiMutationAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class DriverRepository(private val dao: AppDao) {

    private val _liveTelemetry = MutableStateFlow<RideTelemetryState?>(null)
    val liveTelemetry: StateFlow<RideTelemetryState?> = _liveTelemetry.asStateFlow()

    fun updateLiveTelemetry(state: RideTelemetryState) {
        _liveTelemetry.value = state
    }

    // Active AI Heuristic Alert for HUD banner & notification
    private val _activeMutationAlert = MutableStateFlow<UiMutationAlert?>(null)
    val activeMutationAlert: StateFlow<UiMutationAlert?> = _activeMutationAlert.asStateFlow()

    fun postMutationAlert(alert: UiMutationAlert) {
        _activeMutationAlert.value = alert
    }

    fun dismissActiveMutationAlert() {
        _activeMutationAlert.value = null
    }

    val settingsFlow: Flow<FareSettingsEntity> = dao.getSettingsFlow().map {
        it ?: FareSettingsEntity()
    }

    suspend fun getSettingsSync(): FareSettingsEntity {
        return dao.getSettingsSync() ?: FareSettingsEntity()
    }

    suspend fun saveSettings(settings: FareSettingsEntity) {
        dao.insertOrUpdateSettings(settings)
    }

    val logsFlow: Flow<List<RideLogEntity>> = dao.getAllLogsFlow()

    suspend fun insertRideLog(state: RideTelemetryState) {
        val entity = RideLogEntity(
            appName = state.appName,
            timestamp = state.timestamp,
            pickupDistanceKm = state.pickup.distanceKm,
            pickupDurationMin = state.pickup.durationMinutes,
            tripDistanceKm = state.trip.distanceKm,
            tripDurationMin = state.trip.durationMinutes,
            appOfferFare = state.fare.appOfferFare,
            calculatedFare = state.fare.calculatedTrueFare,
            difference = state.fare.difference,
            rating = state.fare.profitRating.name,
            currency = state.fare.currencySymbol,
            netDriverFare = state.fare.netDriverFare,
            engineSource = state.engineSource.name
        )
        dao.insertRideLog(entity)
    }

    suspend fun clearLogs() {
        dao.clearAllLogs()
    }

    val areasFlow: Flow<List<CustomAreaEntity>> = dao.getAllAreasFlow()

    suspend fun getAreasSync(): List<CustomAreaEntity> {
        return dao.getAllAreasSync()
    }

    suspend fun addArea(name: String, multiplier: Double, notes: String = "") {
        val area = CustomAreaEntity(
            name = name,
            surgeMultiplier = multiplier,
            notes = notes
        )
        dao.insertArea(area)
    }

    suspend fun deleteArea(area: CustomAreaEntity) {
        dao.deleteArea(area)
    }

    val mutationsFlow: Flow<List<UiMutationAlert>> = dao.getAllMutationsFlow().map { entities ->
        entities.map { mapEntityToAlert(it) }
    }

    suspend fun insertMutation(alert: UiMutationAlert) {
        val entity = UiMutationEntity(
            packageName = alert.packageName,
            appName = alert.appName,
            timestamp = alert.timestamp,
            mutationType = alert.mutationType.name,
            severity = alert.severity.name,
            description = alert.description,
            confidenceScore = alert.confidenceScore,
            autoPatchApplied = alert.autoPatchApplied,
            patchActionMessage = alert.patchActionMessage,
            isDismissed = alert.isDismissed
        )
        val id = dao.insertMutation(entity)
        postMutationAlert(alert.copy(id = id))
    }

    suspend fun dismissMutation(id: Long) {
        dao.dismissMutation(id)
        if (_activeMutationAlert.value?.id == id) {
            _activeMutationAlert.value = null
        }
    }

    suspend fun clearAllMutations() {
        dao.clearAllMutations()
        _activeMutationAlert.value = null
    }

    private fun mapEntityToAlert(entity: UiMutationEntity): UiMutationAlert {
        val type = try {
            MutationType.valueOf(entity.mutationType)
        } catch (e: Exception) {
            MutationType.DEPTH_ALTERED
        }
        val severity = try {
            MutationSeverity.valueOf(entity.severity)
        } catch (e: Exception) {
            MutationSeverity.INFO_PATCH
        }
        return UiMutationAlert(
            id = entity.id,
            packageName = entity.packageName,
            appName = entity.appName,
            timestamp = entity.timestamp,
            mutationType = type,
            severity = severity,
            description = entity.description,
            confidenceScore = entity.confidenceScore,
            autoPatchApplied = entity.autoPatchApplied,
            patchActionMessage = entity.patchActionMessage,
            isDismissed = entity.isDismissed
        )
    }
}
