package com.service.assasinscreed02

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson

class BackupForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "backup_service_channel"
        const val NOTIFICATION_ID = 101
    }

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Backup en segundo plano")
            .setContentText("El backup se está ejecutando en segundo plano.")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        startPeriodicBackup()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        runnable?.let { handler.removeCallbacks(it) }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startPeriodicBackup() {
        val prefs = getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
        val intervaloHoras = prefs.getInt("intervalo_backup", 24) // valor por defecto: 24h
        val intervaloMs = intervaloHoras * 60 * 60 * 1000L
        runnable = object : Runnable {
            override fun run() {
                try {
                    Log.d("BackupForegroundService", "Ejecutando backup periódico...")
                    BackupUtils.runBackup(applicationContext)
                } catch (e: Exception) {
                    Log.e("BackupForegroundService", "Error en backup periódico: ${e.message}")
                }
                handler.postDelayed(this, intervaloMs)
            }
        }
        handler.post(runnable!!)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Backup Service Channel",
                NotificationManager.IMPORTANCE_MIN
            )
            channel.setSound(null, null)
            channel.enableVibration(false)
            channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
} 