package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fare_settings")
data class FareSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val baseFare: Double = 5.00,
    val pricePerKm: Double = 2.80,
    val pricePerMinute: Double = 0.35,
    val minFare: Double = 10.00,
    val commissionPercent: Double = 20.0,
    val currencySymbol: String = "$",
    val autoFilterEnabled: Boolean = true
)

@Entity(tableName = "ride_logs")
data class RideLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val pickupDistanceKm: Double,
    val pickupDurationMin: Int,
    val tripDistanceKm: Double,
    val tripDurationMin: Int,
    val appOfferFare: Double,
    val calculatedFare: Double,
    val difference: Double,
    val rating: String,
    val currency: String,
    val netDriverFare: Double = 0.0,
    val engineSource: String = "ACCESSIBILITY_TREE"
)

@Entity(tableName = "custom_areas")
data class CustomAreaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val surgeMultiplier: Double = 1.25,
    val notes: String = ""
)

@Entity(tableName = "ui_mutation_logs")
data class UiMutationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mutationType: String,
    val severity: String,
    val description: String,
    val confidenceScore: Double,
    val autoPatchApplied: Boolean = true,
    val patchActionMessage: String = "",
    val isDismissed: Boolean = false
)
