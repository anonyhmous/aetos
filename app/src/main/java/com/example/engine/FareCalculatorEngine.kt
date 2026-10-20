package com.example.engine

import com.example.data.local.FareSettingsEntity
import com.example.model.FareBreakdown
import com.example.model.PickupMetric
import com.example.model.ProfitRating
import com.example.model.TripMetric
import kotlin.math.max
import kotlin.math.roundToInt

class FareCalculatorEngine(
    private val settings: FareSettingsEntity
) {
    /**
     * Strictly computes fare based ONLY on actual passenger trip distance and trip duration.
     * Pickup distance and time are operational overhead and are NEVER factored into passenger billing.
     */
    fun calculate(
        pickup: PickupMetric,
        trip: TripMetric,
        appOfferFare: Double,
        surgeMultiplier: Double = 1.0
    ): FareBreakdown {
        // STRICT ISOLATION: Passenger billing components ONLY use TripMetric
        val distanceCost = trip.distanceKm * settings.pricePerKm
        val durationCost = trip.durationMinutes * settings.pricePerMinute
        val subtotal = settings.baseFare + distanceCost + durationCost
        val surged = subtotal * max(1.0, surgeMultiplier)
        val grossTrueFare = max(settings.minFare, surged)

        // Platform commission cut deduction
        val effectiveCommission = settings.commissionPercent.coerceIn(0.0, 50.0)
        val effectiveOffer = if (appOfferFare > 0.0) appOfferFare else grossTrueFare
        val netDriverReceived = effectiveOffer * (1.0 - (effectiveCommission / 100.0))
        val diff = effectiveOffer - grossTrueFare

        // Effective passenger trip rate per KM (Trip distance only)
        val ratePerKm = if (trip.distanceKm > 0.0) {
            effectiveOffer / trip.distanceKm
        } else {
            settings.pricePerKm
        }

        // Driver Operational Overhead Telemetry (Isolated for driver analysis)
        val totalOperationalDistance = pickup.distanceKm + trip.distanceKm
        val totalOperationalMinutes = pickup.durationMinutes + trip.durationMinutes
        val effectiveTotalRatePerKm = if (totalOperationalDistance > 0.0) {
            netDriverReceived / totalOperationalDistance
        } else {
            0.0
        }
        val effectiveHourlyYield = if (totalOperationalMinutes > 0) {
            (netDriverReceived / totalOperationalMinutes) * 60.0
        } else {
            0.0
        }
        val pickupToTripRatio = if (trip.distanceKm > 0.0) {
            pickup.distanceKm / trip.distanceKm
        } else {
            1.0
        }

        // Profitability evaluation:
        // Evaluates whether the offer covers true fare AND ensures deadhead distance is reasonable
        val rating = when {
            effectiveOffer >= grossTrueFare * 1.10 && pickupToTripRatio <= 0.65 -> ProfitRating.HIGH_PROFIT
            effectiveOffer >= grossTrueFare * 0.90 && pickupToTripRatio <= 1.25 -> ProfitRating.ACCEPTABLE
            else -> ProfitRating.LOW_PROFIT
        }

        return FareBreakdown(
            calculatedTrueFare = roundTwoDecimals(grossTrueFare),
            appOfferFare = roundTwoDecimals(effectiveOffer),
            difference = roundTwoDecimals(diff),
            netDriverFare = roundTwoDecimals(netDriverReceived),
            profitRating = rating,
            ratePerKm = roundTwoDecimals(ratePerKm),
            currencySymbol = settings.currencySymbol,
            totalOperationalDistanceKm = roundTwoDecimals(totalOperationalDistance),
            totalOperationalMinutes = totalOperationalMinutes,
            effectiveTotalRatePerKm = roundTwoDecimals(effectiveTotalRatePerKm),
            effectiveHourlyYield = roundTwoDecimals(effectiveHourlyYield),
            pickupToTripRatio = roundTwoDecimals(pickupToTripRatio)
        )
    }

    private fun roundTwoDecimals(value: Double): Double {
        return (value * 100.0).roundToInt() / 100.0
    }
}
