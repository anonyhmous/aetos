package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.FareSettingsEntity
import com.example.data.repository.DriverRepository
import com.example.engine.FareCalculatorEngine
import com.example.model.PickupMetric
import com.example.model.TripMetric
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: DriverRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DriverRepository(database.appDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun read_string_from_context() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Aetos Driver", appName)
    }

    @Test
    fun testRepositoryAndFareCalculationIntegration() = runTest {
        val settings = FareSettingsEntity(
            baseFare = 6.00,
            pricePerKm = 3.00,
            pricePerMinute = 0.40,
            minFare = 12.00,
            commissionPercent = 15.0,
            currencySymbol = "$"
        )
        repository.saveSettings(settings)

        val retrievedSettings = repository.getSettingsSync()
        assertNotNull(retrievedSettings)
        assertEquals(3.00, retrievedSettings.pricePerKm, 0.01)

        val engine = FareCalculatorEngine(retrievedSettings)
        val pickup = PickupMetric(1.5, 4, "Pickup 1.5 km")
        val trip = TripMetric(10.0, 20, "Trip 10 km") // 6 + 30 + 8 = 44.00
        val fare = engine.calculate(pickup, trip, 50.00)

        assertEquals(44.00, fare.calculatedTrueFare, 0.01)
        assertEquals(50.00, fare.appOfferFare, 0.01)
        assertEquals(6.00, fare.difference, 0.01)
        assertEquals(42.50, fare.netDriverFare, 0.01) // 50 * 0.85
    }
}
