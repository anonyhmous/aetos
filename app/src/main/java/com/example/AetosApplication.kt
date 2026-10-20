package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.repository.DriverRepository

class AetosApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: DriverRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        repository = DriverRepository(database.appDao())
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_OVERLAY,
                "Aetos Overlay & Calculation Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Aetos driver HUD active for real-time ride calculations."
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_OVERLAY = "aetos_overlay_channel"
        lateinit var instance: AetosApplication
            private set
    }
}
