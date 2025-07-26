package com.service.assasinscreed02.updater.domain.entities

/**
 * Información de una actualización disponible
 */
data class UpdateInfo(
    val version: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String,
    val fileSize: Long,
    val isMandatory: Boolean = false,
    val minSdkVersion: Int = 21
) {
    fun isNewerThan(currentVersion: String, currentVersionCode: Int): Boolean {
        return versionCode > currentVersionCode
    }
}

/**
 * Resultado de la verificación de actualizaciones
 */
sealed class UpdateResult {
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateResult()
    data object NoUpdateAvailable : UpdateResult()
    data class Error(val message: String, val exception: Exception? = null) : UpdateResult()
}

/**
 * Estado de la descarga de actualización
 */
sealed class DownloadResult {
    data class Progress(val downloaded: Long, val total: Long, val percentage: Int) : DownloadResult()
    data class Success(val filePath: String) : DownloadResult()
    data class Error(val message: String, val exception: Exception? = null) : DownloadResult()
} 