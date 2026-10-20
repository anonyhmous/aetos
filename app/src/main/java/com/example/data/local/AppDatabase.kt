package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        FareSettingsEntity::class,
        RideLogEntity::class,
        CustomAreaEntity::class,
        UiMutationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aetos_driver.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate default starter data asynchronously
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = getInstance(context).appDao()
                                dao.insertOrUpdateSettings(FareSettingsEntity())
                                dao.insertArea(
                                    CustomAreaEntity(
                                        name = "International Airport",
                                        surgeMultiplier = 1.35,
                                        notes = "High wait times and toll fees"
                                    )
                                )
                                dao.insertArea(
                                    CustomAreaEntity(
                                        name = "Downtown Business Center",
                                        surgeMultiplier = 1.20,
                                        notes = "Rush hour peak congestion"
                                    )
                                )
                                dao.insertRideLog(
                                    RideLogEntity(
                                        appName = "Uber X",
                                        timestamp = System.currentTimeMillis() - 15 * 60 * 1000,
                                        pickupDistanceKm = 1.4,
                                        pickupDurationMin = 4,
                                        tripDistanceKm = 9.2,
                                        tripDurationMin = 24,
                                        appOfferFare = 34.00,
                                        calculatedFare = 30.76,
                                        difference = 3.24,
                                        rating = "HIGH_PROFIT",
                                        currency = "$",
                                        netDriverFare = 27.20,
                                        engineSource = "ACCESSIBILITY_TREE"
                                    )
                                )
                                dao.insertMutation(
                                    UiMutationEntity(
                                        packageName = "com.ubercab.driver",
                                        appName = "Uber Driver",
                                        timestamp = System.currentTimeMillis() - 60 * 60 * 1000,
                                        mutationType = "DEPTH_ALTERED",
                                        severity = "INFO_PATCH",
                                        description = "Uber Driver v4.492 updated bottom sheet layout depth (+4 levels).",
                                        confidenceScore = 0.96,
                                        autoPatchApplied = true,
                                        patchActionMessage = "Adaptive node crawler & OCR zone expanded.",
                                        isDismissed = false
                                    )
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
