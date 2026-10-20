package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CustomAreaEntity
import com.example.data.local.FareSettingsEntity
import com.example.data.local.RideLogEntity
import com.example.data.repository.DriverRepository
import com.example.engine.FareCalculatorEngine
import com.example.model.ExtractionEngine
import com.example.model.MutationSeverity
import com.example.model.MutationType
import com.example.model.PickupMetric
import com.example.model.RideTelemetryState
import com.example.model.TripMetric
import com.example.model.UiMutationAlert
import com.example.service.AetosAccessibilityService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel(private val repository: DriverRepository) : ViewModel() {

    val settings: StateFlow<FareSettingsEntity> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FareSettingsEntity())

    val logs: StateFlow<List<RideLogEntity>> = repository.logsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val areas: StateFlow<List<CustomAreaEntity>> = repository.areasFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mutations: StateFlow<List<UiMutationAlert>> = repository.mutationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liveTelemetry: StateFlow<RideTelemetryState?> = repository.liveTelemetry
    val activeMutationAlert: StateFlow<UiMutationAlert?> = repository.activeMutationAlert

    fun saveSettings(settingsEntity: FareSettingsEntity) {
        viewModelScope.launch {
            repository.saveSettings(settingsEntity)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun clearMutations() {
        viewModelScope.launch {
            repository.clearAllMutations()
        }
    }

    fun dismissMutation(id: Long) {
        viewModelScope.launch {
            repository.dismissMutation(id)
        }
    }

    fun addArea(name: String, multiplier: Double, notes: String = "") {
        viewModelScope.launch {
            repository.addArea(name, multiplier, notes)
        }
    }

    fun deleteArea(area: CustomAreaEntity) {
        viewModelScope.launch {
            repository.deleteArea(area)
        }
    }

    fun triggerOcrScan(callback: (Boolean) -> Unit) {
        val service = AetosAccessibilityService.instance
        if (service != null) {
            service.triggerManualOcr(callback)
        } else {
            callback(false)
        }
    }

    fun simulateAlert() {
        viewModelScope.launch {
            val currentSettings = repository.getSettingsSync()
            val engine = FareCalculatorEngine(currentSettings)

            val apps = listOf("Uber X", "InDrive", "Careem GO", "Bolt Driver", "DiDi Fast", "Yango Pro")
            val selectedApp = apps.random()

            // Realistic random values for test simulation
            val pickupDistance = (8 + Random.nextInt(25)) / 10.0 // 0.8 - 3.3 km
            val pickupDuration = (pickupDistance * 2.8).toInt().coerceAtLeast(2)

            val tripDistance = (40 + Random.nextInt(180)) / 10.0 // 4.0 - 22.0 km
            val tripDuration = (tripDistance * 2.4).toInt().coerceAtLeast(8)

            val idealFare = currentSettings.baseFare +
                    (tripDistance * currentSettings.pricePerKm) +
                    (tripDuration * currentSettings.pricePerMinute)

            val offerRatio = listOf(0.82, 0.94, 1.12, 1.35).random()
            val offerFare = (idealFare * offerRatio * 100).toInt() / 100.0

            val pickup = PickupMetric(pickupDistance, pickupDuration, "$pickupDistance km / $pickupDuration min away")
            val trip = TripMetric(tripDistance, tripDuration, "$tripDistance km / $tripDuration min trip")
            val fare = engine.calculate(pickup, trip, offerFare, 1.0)

            val state = RideTelemetryState(
                appName = selectedApp,
                timestamp = System.currentTimeMillis(),
                pickup = pickup,
                trip = trip,
                fare = fare,
                engineSource = ExtractionEngine.SIMULATION
            )

            repository.updateLiveTelemetry(state)
            repository.insertRideLog(state)
        }
    }

    fun simulateUiMutation() {
        viewModelScope.launch {
            val apps = listOf(
                Triple("com.ubercab.driver", "Uber Driver", "Uber updated dispatch modal tree depth (+6 levels)"),
                Triple("sinet.startup.inDriver", "InDrive", "InDrive changed offer card view tags to dynamic identifiers"),
                Triple("com.didiglobal.driver", "DiDi Driver", "DiDi altered fare text layout and currency formatting")
            )
            val (pkg, name, desc) = apps.random()

            val alert = UiMutationAlert(
                packageName = pkg,
                appName = name,
                timestamp = System.currentTimeMillis(),
                mutationType = MutationType.DEPTH_ALTERED,
                severity = MutationSeverity.INFO_PATCH,
                description = desc,
                confidenceScore = 0.97,
                autoPatchApplied = true,
                patchActionMessage = "Adaptive regex rule #2 & ML Kit vision fallback engaged"
            )
            repository.insertMutation(alert)
        }
    }

    class Factory(private val repository: DriverRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
