package com.service.assasinscreed02.github

import android.content.Context
import android.util.Log
import com.service.assasinscreed02.database.BackupFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.*
import java.text.SimpleDateFormat

class GitHubHistorialSync(private val context: Context) {
    companion object {
        private const val TAG = "GitHubHistorialSync"
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val REPO_OWNER = "tu-usuario" // Cambiar por tu usuario de GitHub
        private const val REPO_NAME = "radio2-backup-historial" // Cambiar por tu repositorio
        private const val HISTORIAL_FILE_PATH = "historial_backup.json"
        private const val BRANCH = "main"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    data class GitHubConfig(
        val token: String,
        val owner: String = REPO_OWNER,
        val repo: String = REPO_NAME,
        val branch: String = BRANCH
    )

    data class GitHubFile(
        val sha: String,
        val content: String,
        val encoding: String = "base64"
    )

    data class GitHubCommit(
        val message: String,
        val content: String,
        val sha: String? = null
    )

    suspend fun syncHistorialToGitHub(
        historial: List<BackupFile>,
        config: GitHubConfig
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando sincronización con GitHub...")
            
            // Obtener el archivo actual en GitHub
            val currentFile = getFileFromGitHub(config)
            
            // Combinar historial local con el de GitHub
            val combinedHistorial = if (currentFile != null) {
                val githubHistorial = parseHistorialFromContent(currentFile.content)
                mergeHistorials(historial, githubHistorial)
            } else {
                historial
            }
            
            // Convertir a JSON
            val jsonContent = convertHistorialToJson(combinedHistorial)
            
            // Subir a GitHub
            val success = uploadFileToGitHub(jsonContent, currentFile?.sha, config)
            
            if (success) {
                Log.d(TAG, "Historial sincronizado exitosamente con GitHub")
                saveLastSyncTimestamp()
            } else {
                Log.e(TAG, "Error sincronizando con GitHub")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error en syncHistorialToGitHub: ${e.message}", e)
            false
        }
    }

    suspend fun getHistorialFromGitHub(config: GitHubConfig): List<BackupFile> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Obteniendo historial desde GitHub...")
            
            val file = getFileFromGitHub(config)
            if (file != null) {
                val historial = parseHistorialFromContent(file.content)
                Log.d(TAG, "Historial obtenido desde GitHub: ${historial.size} archivos")
                historial
            } else {
                Log.w(TAG, "No se encontró archivo de historial en GitHub")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo historial desde GitHub: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun checkForDuplicates(hash: String, config: GitHubConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val historial = getHistorialFromGitHub(config)
            val exists = historial.any { it.fileHash == hash }
            Log.d(TAG, "Verificación de duplicado para hash $hash: $exists")
            exists
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando duplicados: ${e.message}", e)
            false
        }
    }

    suspend fun getGlobalStatistics(config: GitHubConfig): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val historial = getHistorialFromGitHub(config)
            
            val totalFiles = historial.size
            val totalSize = historial.sumOf { it.fileSize }
            val successfulBackups = historial.count { it.uploadStatus == "success" }
            val failedBackups = historial.count { it.uploadStatus == "failed" }
            
            val fileTypes = historial.groupBy { it.fileType }.mapValues { it.value.size }
            
            mapOf(
                "totalFiles" to totalFiles,
                "totalSize" to totalSize,
                "successfulBackups" to successfulBackups,
                "failedBackups" to failedBackups,
                "fileTypes" to fileTypes,
                "lastSync" to getLastSyncTimestamp()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo estadísticas globales: ${e.message}", e)
            emptyMap()
        }
    }

    private suspend fun getFileFromGitHub(config: GitHubConfig): GitHubFile? = withContext(Dispatchers.IO) {
        try {
            val url = "$GITHUB_API_BASE/repos/${config.owner}/${config.repo}/contents/$HISTORIAL_FILE_PATH?ref=${config.branch}"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "token ${config.token}")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                GitHubFile(
                    sha = json.getString("sha"),
                    content = json.getString("content"),
                    encoding = json.optString("encoding", "base64")
                )
            } else if (response.code == 404) {
                // El archivo no existe, es normal para la primera vez
                null
            } else {
                Log.e(TAG, "Error obteniendo archivo de GitHub: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo archivo de GitHub: ${e.message}", e)
            null
        }
    }

    private suspend fun uploadFileToGitHub(
        content: String,
        currentSha: String?,
        config: GitHubConfig
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$GITHUB_API_BASE/repos/${config.owner}/${config.repo}/contents/$HISTORIAL_FILE_PATH"
            
            val commitMessage = if (currentSha != null) {
                "Actualizar historial de backup - ${getCurrentTimestamp()}"
            } else {
                "Crear historial de backup inicial - ${getCurrentTimestamp()}"
            }
            
            val jsonBody = JSONObject().apply {
                put("message", commitMessage)
                put("content", android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP))
                put("branch", config.branch)
                if (currentSha != null) {
                    put("sha", currentSha)
                }
            }
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "token ${config.token}")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .put(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.d(TAG, "Archivo subido exitosamente a GitHub")
                true
            } else {
                Log.e(TAG, "Error subiendo archivo a GitHub: ${response.code} - ${response.body?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo archivo a GitHub: ${e.message}", e)
            false
        }
    }

    private fun parseHistorialFromContent(content: String): List<BackupFile> {
        return try {
            val decodedContent = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
            val jsonArray = JSONArray(decodedContent)
            val historial = mutableListOf<BackupFile>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val backupFile = BackupFile(
                    id = jsonObject.optLong("id", 0),
                    fileName = jsonObject.getString("fileName"),
                    filePath = jsonObject.getString("filePath"),
                    fileHash = jsonObject.getString("fileHash"),
                    fileSize = jsonObject.getLong("fileSize"),
                    fileType = jsonObject.getString("fileType"),
                    uploadDate = jsonObject.optLong("uploadDate", System.currentTimeMillis()),
                    uploadStatus = jsonObject.optString("uploadStatus", "success"),
                    telegramMessageId = jsonObject.optString("telegramMessageId").takeIf { it.isNotEmpty() },
                    errorMessage = jsonObject.optString("errorMessage").takeIf { it.isNotEmpty() }
                )
                historial.add(backupFile)
            }
            
            historial
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando historial desde GitHub: ${e.message}", e)
            emptyList()
        }
    }

    private fun mergeHistorials(local: List<BackupFile>, remote: List<BackupFile>): List<BackupFile> {
        val merged = mutableMapOf<String, BackupFile>()
        
        // Agregar archivos remotos
        remote.forEach { file ->
            merged[file.fileHash] = file
        }
        
        // Agregar archivos locales (sobrescriben si tienen el mismo hash)
        local.forEach { file ->
            merged[file.fileHash] = file
        }
        
        return merged.values.toList().sortedByDescending { it.uploadDate }
    }

    private fun convertHistorialToJson(historial: List<BackupFile>): String {
        return try {
            val jsonArray = JSONArray()
            historial.forEach { file ->
                val jsonObject = JSONObject().apply {
                    put("id", file.id)
                    put("fileName", file.fileName)
                    put("filePath", file.filePath)
                    put("fileHash", file.fileHash)
                    put("fileSize", file.fileSize)
                    put("fileType", file.fileType)
                    put("uploadDate", file.uploadDate)
                    put("uploadStatus", file.uploadStatus)
                    put("telegramMessageId", file.telegramMessageId ?: "")
                    put("errorMessage", file.errorMessage ?: "")
                }
                jsonArray.put(jsonObject)
            }
            jsonArray.toString(2) // Pretty print con indentación
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo historial a JSON: ${e.message}", e)
            "[]"
        }
    }

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun saveLastSyncTimestamp() {
        val prefs = context.getSharedPreferences("github_sync", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply()
    }

    private fun getLastSyncTimestamp(): Long {
        val prefs = context.getSharedPreferences("github_sync", Context.MODE_PRIVATE)
        return prefs.getLong("last_sync", 0L)
    }
} 