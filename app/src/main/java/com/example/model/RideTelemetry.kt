package com.example.model

enum class ProfitRating {
    HIGH_PROFIT,
    ACCEPTABLE,
    LOW_PROFIT
}

enum class ExtractionEngine {
    ACCESSIBILITY_TREE,
    OCR_VISION,
    SIMULATION
}

data class PickupMetric(
    val distanceKm: Double,
    val durationMinutes: Int,
    val rawText: String = ""
)

data class TripMetric(
    val distanceKm: Double,
    val durationMinutes: Int,
    val rawText: String = ""
)

data class FareBreakdown(
    val calculatedTrueFare: Double,
    val appOfferFare: Double,
    val difference: Double,
    val netDriverFare: Double,
    val profitRating: ProfitRating,
    val ratePerKm: Double,
    val currencySymbol: String,
    val totalOperationalDistanceKm: Double = 0.0,
    val totalOperationalMinutes: Int = 0,
    val effectiveTotalRatePerKm: Double = 0.0,
    val effectiveHourlyYield: Double = 0.0,
    val pickupToTripRatio: Double = 0.0
)

data class RideTelemetryState(
    val appName: String,
    val packageName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val pickup: PickupMetric,
    val trip: TripMetric,
    val fare: FareBreakdown,
    val surgeMultiplier: Double = 1.0,
    val engineSource: ExtractionEngine = ExtractionEngine.ACCESSIBILITY_TREE
)
