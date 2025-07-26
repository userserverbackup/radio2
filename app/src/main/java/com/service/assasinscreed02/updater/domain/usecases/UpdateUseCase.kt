package com.service.assasinscreed02.updater.domain.usecases

import android.content.Context
import android.util.Log
import com.service.assasinscreed02.updater.domain.entities.UpdateInfo
import com.service.assasinscreed02.updater.domain.entities.UpdateResult
import com.service.assasinscreed02.updater.domain.entities.DownloadResult
import com.service.assasinscreed02.updater.domain.repositories.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caso de uso para el sistema de actualización
 */
class UpdateUseCase(
    private val updateRepository: UpdateRepository
) {
    
    companion object {
        private const val TAG = "UpdateUseCase"
    }
    
    /**
     * Verifica si hay actualizaciones disponibles
     */
    suspend fun checkForUpdates(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Verificando actualizaciones...")
            updateRepository.checkForUpdates(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error en caso de uso de verificación: ${e.message}", e)
            UpdateResult.Error("Error verificando actualizaciones: ${e.message}", e)
        }
    }
    
    /**
     * Descarga e instala una actualización
     */
    suspend fun downloadAndInstallUpdate(
        context: Context,
        updateInfo: UpdateInfo,
        progressCallback: (DownloadResult) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando descarga e instalación de actualización: ${updateInfo.version}")
            
            // Descargar actualización
            val downloadResult = updateRepository.downloadUpdate(context, updateInfo, progressCallback)
            
            when (downloadResult) {
                is DownloadResult.Success -> {
                    Log.d(TAG, "Descarga completada, instalando...")
                    
                    // Instalar actualización
                    val installSuccess = updateRepository.installUpdate(context, downloadResult.filePath)
                    
                    if (installSuccess) {
                        Log.d(TAG, "Instalación iniciada exitosamente")
                        true
                    } else {
                        Log.e(TAG, "Error iniciando instalación")
                        false
                    }
                }
                is DownloadResult.Error -> {
                    Log.e(TAG, "Error en descarga: ${downloadResult.message}")
                    false
                }
                is DownloadResult.Progress -> {
                    // No debería llegar aquí
                    false
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en caso de uso de descarga: ${e.message}", e)
            false
        }
    }
    
    /**
     * Obtiene información de la versión actual
     */
    fun getCurrentVersionInfo(context: Context): Pair<String, Int> {
        return updateRepository.getCurrentVersionInfo(context)
    }
    
    /**
     * Verifica si la actualización automática está habilitada
     */
    fun isAutoUpdateEnabled(context: Context): Boolean {
        return updateRepository.isAutoUpdateEnabled(context)
    }
    
    /**
     * Habilita/deshabilita la actualización automática
     */
    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        updateRepository.setAutoUpdateEnabled(context, enabled)
    }
    
    /**
     * Ejecuta verificación automática de actualizaciones
     */
    suspend fun performAutoUpdateCheck(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        try {
            if (!isAutoUpdateEnabled(context)) {
                Log.d(TAG, "Actualización automática deshabilitada")
                return@withContext UpdateResult.NoUpdateAvailable
            }
            
            Log.d(TAG, "Ejecutando verificación automática de actualizaciones")
            checkForUpdates(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en verificación automática: ${e.message}", e)
            UpdateResult.Error("Error en verificación automática: ${e.message}", e)
        }
    }
} 