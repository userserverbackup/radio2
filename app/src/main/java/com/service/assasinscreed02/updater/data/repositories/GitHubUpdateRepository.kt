package com.service.assasinscreed02.updater.data.repositories

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.service.assasinscreed02.updater.domain.entities.UpdateInfo
import com.service.assasinscreed02.updater.domain.entities.UpdateResult
import com.service.assasinscreed02.updater.domain.entities.DownloadResult
import com.service.assasinscreed02.updater.domain.repositories.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Implementación del repositorio de actualización usando GitHub Releases
 */
class GitHubUpdateRepository : UpdateRepository {
    
    companion object {
        private const val TAG = "GitHubUpdateRepository"
        private const val PREFS_NAME = "UpdatePrefs"
        private const val AUTO_UPDATE_KEY = "auto_update_enabled"
        
        // Configuración de GitHub - CONFIGURADO
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val REPO_OWNER = "userserverbackup" // Usuario de GitHub configurado
        private const val REPO_NAME = "radio2" // Repositorio configurado
        private const val RELEASES_URL = "$GITHUB_API_BASE/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    }
    
    override suspend fun checkForUpdates(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Verificando actualizaciones desde GitHub...")
            
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url(RELEASES_URL)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateResult.Error("Error HTTP ${response.code}: ${response.message}")
            }
            
            val responseBody = response.body?.string() ?: ""
            if (responseBody.isEmpty()) {
                return@withContext UpdateResult.Error("Respuesta vacía del servidor")
            }
            
            val releaseJson = JSONObject(responseBody)
            val tagName = releaseJson.getString("tag_name")
            val releaseNotes = releaseJson.getString("body")
            
            // Buscar el APK en los assets
            val assets = releaseJson.getJSONArray("assets")
            var apkAsset: JSONObject? = null
            
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    apkAsset = asset
                    break
                }
            }
            
            if (apkAsset == null) {
                return@withContext UpdateResult.Error("No se encontró APK en el release")
            }
            
            val downloadUrl = apkAsset.getString("browser_download_url")
            val fileSize = apkAsset.getLong("size")
            
            // Extraer versión del tag (ej: v1.5.0 -> 1.5.0)
            val version = tagName.removePrefix("v")
            val versionCode = extractVersionCode(version)
            
            val updateInfo = UpdateInfo(
                version = version,
                versionCode = versionCode,
                downloadUrl = downloadUrl,
                releaseNotes = releaseNotes,
                fileSize = fileSize,
                isMandatory = releaseNotes.contains("[MANDATORY]"),
                minSdkVersion = 21
            )
            
            val (currentVersion, currentVersionCode) = getCurrentVersionInfo(context)
            
            if (updateInfo.isNewerThan(currentVersion, currentVersionCode)) {
                Log.d(TAG, "Actualización disponible: ${updateInfo.version} (${updateInfo.versionCode})")
                UpdateResult.UpdateAvailable(updateInfo)
            } else {
                Log.d(TAG, "No hay actualizaciones disponibles. Actual: $currentVersion ($currentVersionCode)")
                UpdateResult.NoUpdateAvailable
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando actualizaciones: ${e.message}", e)
            UpdateResult.Error("Error verificando actualizaciones: ${e.message}", e)
        }
    }
    
    override suspend fun downloadUpdate(
        context: Context,
        updateInfo: UpdateInfo,
        progressCallback: (DownloadResult) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando descarga de actualización: ${updateInfo.version}")
            
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext DownloadResult.Error("Error HTTP ${response.code}: ${response.message}")
            }
            
            val body = response.body ?: return@withContext DownloadResult.Error("Cuerpo de respuesta vacío")
            val contentLength = body.contentLength()
            
            if (contentLength <= 0) {
                return@withContext DownloadResult.Error("Tamaño de archivo inválido")
            }
            
            // Crear directorio de descargas si no existe
            val downloadDir = File(context.getExternalFilesDir(null), "updates")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val apkFile = File(downloadDir, "radio2-update-${updateInfo.version}.apk")
            
            // Descargar archivo con progreso
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)
            val buffer = ByteArray(8192)
            var downloaded = 0L
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                
                val percentage = ((downloaded * 100) / contentLength).toInt()
                progressCallback(DownloadResult.Progress(downloaded, contentLength, percentage))
            }
            
            outputStream.close()
            inputStream.close()
            
            Log.d(TAG, "Descarga completada: ${apkFile.absolutePath}")
            return@withContext DownloadResult.Success(apkFile.absolutePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando actualización: ${e.message}", e)
            return@withContext DownloadResult.Error("Error descargando actualización: ${e.message}", e)
        }
    }
    
    override suspend fun installUpdate(context: Context, apkPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Instalando actualización: $apkPath")
            
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                Log.e(TAG, "Archivo APK no encontrado: $apkPath")
                return@withContext false
            }
            
            // Crear intent para instalar APK
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Intent de instalación enviado")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error instalando actualización: ${e.message}", e)
            return@withContext false
        }
    }
    
    override fun getCurrentVersionInfo(context: Context): Pair<String, Int> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: "1.0.0"
            val versionCode = packageInfo.longVersionCode.toInt()
            Pair(versionName, versionCode)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo información de versión: ${e.message}")
            Pair("1.0.0", 1)
        }
    }
    
    override fun isAutoUpdateEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(AUTO_UPDATE_KEY, true) // Por defecto habilitado
    }
    
    override fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AUTO_UPDATE_KEY, enabled).apply()
        Log.d(TAG, "Actualización automática ${if (enabled) "habilitada" else "deshabilitada"}")
    }
    
    /**
     * Extrae el código de versión de una cadena de versión
     * Ejemplo: "1.5.0" -> 150
     */
    private fun extractVersionCode(version: String): Int {
        return try {
            val parts = version.split(".")
            if (parts.size >= 2) {
                val major = parts[0].toInt()
                val minor = parts[1].toInt()
                val patch = if (parts.size >= 3) parts[2].toInt() else 0
                major * 100 + minor * 10 + patch
            } else {
                1
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extrayendo código de versión de '$version': ${e.message}")
            1
        }
    }
} 