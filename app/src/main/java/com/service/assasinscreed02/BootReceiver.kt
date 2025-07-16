package com.service.assasinscreed02

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                val prefs = context.getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
                val enabled = prefs.getBoolean("backup_enabled", false)
                if (enabled) {
                    val serviceIntent = Intent(context, BackupForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d("BootReceiver", "BackupForegroundService iniciado tras BOOT_COMPLETED")
                } else {
                    Log.d("BootReceiver", "BackupForegroundService NO iniciado tras BOOT_COMPLETED porque backup_enabled=false")
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error iniciando servicio tras BOOT_COMPLETED: ${e.message}")
            }
        }
    }
} 