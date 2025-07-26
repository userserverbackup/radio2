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
        private const val REPO_OWNER = "userserverbackup" // Usuario de GitHub configurado
        private const val REPO_NAME = "radio2-backup-historial" // Repositorio configurado
        private const val HISTORIAL_FILE_PATH = "historial_backup.json"
        private const val BRANCH = "main"
        private const val MAX_FILE_SIZE = 100 * 1024 * 1024 // 100MB límite de GitHub
        private const val DEFAULT_TOKEN = "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" // Token por defecto
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
    ) {
        fun isValid(): Boolean {
            return token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank() && branch.isNotBlank()
        }
    }

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

    data class GitHubError(
        val message: String,
        val code: Int = 0
    )

    suspend fun testConnection(config: GitHubConfig): GitHubError? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Probando conexión con GitHub...")
            
            if (!config.isValid()) {
                return@withContext GitHubError("Configuración inválida", 400)
            }
            
            val url = "$GITHUB_API_BASE/repos/${config.owner}/${config.repo}"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "token ${config.token}")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.d(TAG, "Conexión exitosa con GitHub")
                null
            } else {
                val errorBody = response.body?.string() ?: ""
                val errorMessage = try {
                    val jsonError = JSONObject(errorBody)
                    jsonError.optString("message", "Error desconocido")
                } catch (e: Exception) {
                    "Error ${response.code}: ${response.message}"
                }
                Log.e(TAG, "Error de conexión: $errorMessage")
                GitHubError(errorMessage, response.code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error probando conexión: ${e.message}", e)
            GitHubError("Error de red: ${e.message}")
        }
    }

    suspend fun syncHistorialToGitHub(
        historial: List<BackupFile>,
        config: GitHubConfig
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando sincronización con GitHub...")
            
            if (!config.isValid()) {
                Log.e(TAG, "Configuración de GitHub inválida")
                return@withContext false
            }
            
            // Obtener el archivo actual en GitHub
            val currentFile = getFileFromGitHub(config)
            
            // Combinar historial local con el de GitHub
            val combinedHistorial = if (currentFile != null) {
                val githubHistorial = parseHistorialFromContent(currentFile.content)
                mergeHistorials(historial, githubHistorial)
            } else {
                historial
            }
            
            // Verificar tamaño del archivo
            val jsonContent = convertHistorialToJson(combinedHistorial)
            if (jsonContent.toByteArray().size > MAX_FILE_SIZE) {
                Log.e(TAG, "El archivo de historial excede el límite de tamaño de GitHub")
                return@withContext false
            }
            
            // Subir a GitHub
            val success = uploadFileToGitHub(jsonContent, currentFile?.sha, config)
            
            if (success) {
                Log.d(TAG, "Historial sincronizado exitosamente con GitHub")
                saveLastSyncTimestamp()
                saveSyncStatistics(combinedHistorial.size, combinedHistorial.sumOf { it.fileSize })
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
            
            if (!config.isValid()) {
                Log.e(TAG, "Configuración de GitHub inválida")
                return@withContext emptyList()
            }
            
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
            if (!config.isValid()) {
                return@withContext false
            }
            
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
            if (!config.isValid()) {
                return@withContext             mapOf<String, Any>(
                "error" to "Configuración de GitHub inválida",
                "totalFiles" to 0,
                "totalSize" to 0L,
                "successfulBackups" to 0,
                "failedBackups" to 0,
                "lastSync" to 0L
            )
            }
            
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
                "lastSync" to getLastSyncTimestamp(),
                "repoInfo" to mapOf<String, String>(
                    "owner" to config.owner,
                    "repo" to config.repo,
                    "branch" to config.branch
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo estadísticas globales: ${e.message}", e)
            mapOf<String, Any>(
                "error" to (e.message ?: "Error desconocido"),
                "totalFiles" to 0,
                "totalSize" to 0L,
                "successfulBackups" to 0,
                "failedBackups" to 0,
                "lastSync" to 0L
            )
        }
    }

    suspend fun deleteFileFromGitHub(fileHash: String, config: GitHubConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!config.isValid()) {
                return@withContext false
            }
            
            val currentFile = getFileFromGitHub(config)
            if (currentFile == null) {
                Log.w(TAG, "No hay archivo de historial en GitHub para eliminar")
                return@withContext false
            }
            
            val historial = parseHistorialFromContent(currentFile.content)
            val filteredHistorial = historial.filter { it.fileHash != fileHash }
            
            if (filteredHistorial.size == historial.size) {
                Log.w(TAG, "Archivo con hash $fileHash no encontrado en GitHub")
                return@withContext false
            }
            
            val jsonContent = convertHistorialToJson(filteredHistorial)
            val success = uploadFileToGitHub(jsonContent, currentFile.sha, config)
            
            if (success) {
                Log.d(TAG, "Archivo eliminado exitosamente de GitHub")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando archivo de GitHub: ${e.message}", e)
            false
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
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Error obteniendo archivo de GitHub: ${response.code} - $errorBody")
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
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Error subiendo archivo a GitHub: ${response.code} - $errorBody")
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

    private fun saveSyncStatistics(fileCount: Int, totalSize: Long) {
        val prefs = context.getSharedPreferences("github_sync", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("last_sync_file_count", fileCount)
            .putLong("last_sync_total_size", totalSize)
            .apply()
    }

    fun getLastSyncStatistics(): Map<String, Any> {
        val prefs = context.getSharedPreferences("github_sync", Context.MODE_PRIVATE)
        return mapOf(
            "lastSync" to prefs.getLong("last_sync", 0L),
            "fileCount" to prefs.getInt("last_sync_file_count", 0),
            "totalSize" to prefs.getLong("last_sync_total_size", 0L)
        )
    }
} 