package com.service.assasinscreed02.backup.domain.entities

/**
 * Configuración del sistema de backup
 */
data class BackupConfig(
    val token: String,
    val chatId: String,
    val forzarConDatos: Boolean = false,
    val batchSizeImages: Int = 10,
    val batchSizeVideos: Int = 3,
    val batchDelayMs: Long = 2000L
) {
    fun isValid(): Boolean {
        return token.isNotBlank() && chatId.isNotBlank()
    }
}

/**
 * Resultado de una operación de backup
 */
sealed class BackupResult {
    data class Success(
        val filesSent: Int,
        val filesError: Int,
        val totalFiles: Int
    ) : BackupResult()
    
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : BackupResult()
    
    data class Progress(
        val currentFile: String,
        val phase: String,
        val progress: Int,
        val total: Int,
        val sent: Int,
        val errors: Int,
        val fileProgress: Int,
        val fileSize: Long
    ) : BackupResult()
}

/**
 * Entrada del historial de backup
 */
data class BackupHistoryEntry(
    val fileName: String,
    val hash: String,
    val timestamp: Long
) 