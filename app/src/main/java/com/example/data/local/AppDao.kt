package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Query("SELECT * FROM fare_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<FareSettingsEntity?>

    @Query("SELECT * FROM fare_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsSync(): FareSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: FareSettingsEntity)

    @Query("SELECT * FROM ride_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<RideLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRideLog(log: RideLogEntity)

    @Query("DELETE FROM ride_logs")
    suspend fun clearAllLogs()

    @Query("SELECT * FROM custom_areas ORDER BY id DESC")
    fun getAllAreasFlow(): Flow<List<CustomAreaEntity>>

    @Query("SELECT * FROM custom_areas")
    suspend fun getAllAreasSync(): List<CustomAreaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArea(area: CustomAreaEntity)

    @Delete
    suspend fun deleteArea(area: CustomAreaEntity)

    @Query("SELECT * FROM ui_mutation_logs ORDER BY timestamp DESC")
    fun getAllMutationsFlow(): Flow<List<UiMutationEntity>>

    @Query("SELECT * FROM ui_mutation_logs WHERE isDismissed = 0 ORDER BY timestamp DESC LIMIT 5")
    fun getActiveMutationsFlow(): Flow<List<UiMutationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMutation(mutation: UiMutationEntity): Long

    @Query("UPDATE ui_mutation_logs SET isDismissed = 1 WHERE id = :id")
    suspend fun dismissMutation(id: Long)

    @Query("DELETE FROM ui_mutation_logs")
    suspend fun clearAllMutations()
}
