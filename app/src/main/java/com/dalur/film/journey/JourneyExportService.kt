package com.dalur.film.journey

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Foreground service wrapper for long Journey renders (cancellation + progress). */
class JourneyExportService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel("journey", "Journey export", NotificationManager.IMPORTANCE_LOW)
            )
            val n = NotificationCompat.Builder(this, "journey")
                .setContentTitle("DALUR film")
                .setContentText("Exporting journey…")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
            startForeground(41, n)
        }
    }
}
