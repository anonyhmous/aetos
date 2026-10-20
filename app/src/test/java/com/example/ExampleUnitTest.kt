package com.example

import com.example.data.local.FareSettingsEntity
import com.example.engine.AdaptiveUiEngine
import com.example.engine.FareCalculatorEngine
import com.example.engine.TripMetricParser
import com.example.engine.UiMutationDetector
import com.example.model.AdaptiveScreenProfile
import com.example.model.MutationType
import com.example.model.PickupMetric
import com.example.model.ProfitRating
import com.example.model.TripMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testTripMetricParser_arabicTextExtractionWithArabicDecimals() {
        val arabicLines = listOf(
            "الوصول للراكب خلال ٣ دقائق / ١٫٢ كم",
            "مسافة الرحلة ٨٫٥ كم / ٢٢ دقيقة",
            "السعر المقترح: 45.00 ج.م"
        )

        val (pickup, trip) = TripMetricParser.parseRideDetails(arabicLines)
        val fare = TripMetricParser.extractFareAmount(arabicLines.joinToString(" "))

        assertEquals(1.2, pickup.distanceKm, 0.01)
        assertEquals(3, pickup.durationMinutes)
        assertEquals(8.5, trip.distanceKm, 0.01)
        assertEquals(22, trip.durationMinutes)
        assertEquals(45.00, fare, 0.01)
    }

    @Test
    fun testTripMetricParser_persianUrduDigitsNormalization() {
        val text = "فاصله تا مبدا: ۲.۴ کیلومتر (۵ دقیقه) | مقصد: ۱۲.۵ کیلومتر | کرایه: ۱۲۰٫۰۰۰ ریال"
        val normalized = TripMetricParser.normalizeText(text)
        assertTrue(normalized.contains("2.4"))
        assertTrue(normalized.contains("12.5"))

        val dist = TripMetricParser.extractDistanceKm("۱۲.۵ کیلومتر")
        assertEquals(12.5, dist, 0.01)
    }

    @Test
    fun testTripMetricParser_europeanCommaDecimal() {
        val lines = listOf(
            "Pickup away: 2,3 km in 5 mins",
            "Trip dropoff: 14,8 km, 32 mins",
            "Offer: €28,50"
        )
        val (pickup, trip) = TripMetricParser.parseRideDetails(lines)
        val fare = TripMetricParser.extractFareAmount(lines.joinToString(" "))

        assertEquals(2.3, pickup.distanceKm, 0.01)
        assertEquals(5, pickup.durationMinutes)
        assertEquals(14.8, trip.distanceKm, 0.01)
        assertEquals(32, trip.durationMinutes)
        assertEquals(28.50, fare, 0.01)
    }

    @Test
    fun testTripMetricParser_englishTextExtraction() {
        val englishLines = listOf(
            "Pickup: 4 mins away (1.5 km)",
            "Dropoff trip distance: 12.0 km, 28 mins",
            "Offer: $35.50"
        )

        val (pickup, trip) = TripMetricParser.parseRideDetails(englishLines)
        val fare = TripMetricParser.extractFareAmount(englishLines.joinToString(" "))

        assertEquals(1.5, pickup.distanceKm, 0.01)
        assertEquals(4, pickup.durationMinutes)
        assertEquals(12.0, trip.distanceKm, 0.01)
        assertEquals(28, trip.durationMinutes)
        assertEquals(35.50, fare, 0.01)
    }

    @Test
    fun testTripMetricParser_milesAndMetersConversion() {
        val milesText = "Pickup is 1.5 mi away"
        val milesKm = TripMetricParser.extractDistanceKm(milesText)
        assertEquals(1.5 * 1.60934, milesKm, 0.01)

        val meterText = "Arrival in 800 m"
        val meterKm = TripMetricParser.extractDistanceKm(meterText)
        assertEquals(0.8, meterKm, 0.01)
    }

    @Test
    fun testFareCalculatorEngine_strictTripDistanceMultiplication() {
        val settings = FareSettingsEntity(
            baseFare = 5.00,
            pricePerKm = 2.00,
            pricePerMinute = 0.50,
            minFare = 8.00,
            commissionPercent = 20.0,
            currencySymbol = "$"
        )
        val engine = FareCalculatorEngine(settings)

        val pickup = PickupMetric(distanceKm = 4.0, durationMinutes = 10, rawText = "Pickup 4km")
        val trip = TripMetric(distanceKm = 10.0, durationMinutes = 20, rawText = "Trip 10km")

        val offerFare = 40.00
        val breakdown = engine.calculate(pickup, trip, offerFare, surgeMultiplier = 1.0)

        assertEquals(35.00, breakdown.calculatedTrueFare, 0.01)
        assertEquals(40.00, breakdown.appOfferFare, 0.01)
        assertEquals(5.00, breakdown.difference, 0.01)
        assertEquals(32.00, breakdown.netDriverFare, 0.01) // 40 * 0.8
        assertEquals(ProfitRating.HIGH_PROFIT, breakdown.profitRating)
        assertEquals(4.00, breakdown.ratePerKm, 0.01) // 40 / 10km

        assertEquals(14.0, breakdown.totalOperationalDistanceKm, 0.01)
        assertEquals(30, breakdown.totalOperationalMinutes)
        assertEquals(2.29, breakdown.effectiveTotalRatePerKm, 0.01) // 32 / 14km
    }

    @Test
    fun testFareCalculatorEngine_surgeMultiplier() {
        val settings = FareSettingsEntity(
            baseFare = 5.00,
            pricePerKm = 2.00,
            pricePerMinute = 0.50,
            minFare = 8.00
        )
        val engine = FareCalculatorEngine(settings)
        val pickup = PickupMetric(1.0, 3, "pickup")
        val trip = TripMetric(10.0, 20, "trip")

        val breakdown = engine.calculate(pickup, trip, 50.00, surgeMultiplier = 1.30)
        assertEquals(45.50, breakdown.calculatedTrueFare, 0.01)
    }

    @Test
    fun testFareCalculatorEngine_lowProfitRating() {
        val settings = FareSettingsEntity(
            baseFare = 5.00,
            pricePerKm = 3.00,
            pricePerMinute = 0.20,
            minFare = 10.00
        )
        val engine = FareCalculatorEngine(settings)

        val pickup = PickupMetric(1.0, 2, "pickup")
        val trip = TripMetric(10.0, 25, "trip")

        val lowOfferFare = 25.00
        val breakdown = engine.calculate(pickup, trip, lowOfferFare)

        assertEquals(40.00, breakdown.calculatedTrueFare, 0.01)
        assertEquals(ProfitRating.LOW_PROFIT, breakdown.profitRating)
        assertTrue(breakdown.difference < 0.0)
    }

    @Test
    fun testAdaptiveUiEngine_zonesForMultiApps() {
        val standardPhoneProfile = AdaptiveScreenProfile(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            densityDpi = 420,
            aspectRatio = 2.22f,
            isTabletOrFoldable = false
        )

        val uberZone = AdaptiveUiEngine.getNormalizedScanZone("com.ubercab.driver", standardPhoneProfile)
        assertTrue(uberZone.top >= 0.25f && uberZone.bottom <= 1.0f)

        val indriveZone = AdaptiveUiEngine.getNormalizedScanZone("sinet.startup.inDriver", standardPhoneProfile)
        assertTrue(indriveZone.left >= 0.0f && indriveZone.right <= 1.0f)

        val didiZone = AdaptiveUiEngine.getNormalizedScanZone("com.didiglobal.driver", standardPhoneProfile)
        assertTrue(didiZone.bottom <= 1.0f)
    }

    @Test
    fun testUiMutationDetector_detectsNodeRestriction() {
        UiMutationDetector.reset()
        val pkg = "com.ubercab.driver"
        val name = "Uber Driver"

        // First pass with node restriction
        UiMutationDetector.analyzeHierarchy(
            packageName = pkg,
            appName = name,
            nodeCount = 2,
            maxDepth = 3,
            nodeExtractionSuccess = false,
            ocrFallbackSuccess = true,
            rawTexts = emptyList()
        )

        // Second consecutive pass triggers the heuristic mutation alert
        val alert = UiMutationDetector.analyzeHierarchy(
            packageName = pkg,
            appName = name,
            nodeCount = 1,
            maxDepth = 2,
            nodeExtractionSuccess = false,
            ocrFallbackSuccess = true,
            rawTexts = emptyList()
        )

        assertNotNull(alert)
        assertEquals(MutationType.NODE_TREE_RESTRICTED, alert?.mutationType)
        assertTrue(alert?.autoPatchApplied == true)
        assertTrue(alert?.confidenceScore ?: 0.0 >= 0.95)
    }
}
