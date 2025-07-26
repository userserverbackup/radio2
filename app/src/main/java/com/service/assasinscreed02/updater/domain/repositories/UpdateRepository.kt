package com.service.assasinscreed02.updater.domain.repositories

import android.content.Context
import com.service.assasinscreed02.updater.domain.entities.UpdateInfo
import com.service.assasinscreed02.updater.domain.entities.UpdateResult
import com.service.assasinscreed02.updater.domain.entities.DownloadResult

/**
 * Repositorio para operaciones de actualización
 */
interface UpdateRepository {
    /**
     * Verifica si hay actualizaciones disponibles
     */
    suspend fun checkForUpdates(context: Context): UpdateResult
    
    /**
     * Descarga la actualización
     */
    suspend fun downloadUpdate(
        context: Context, 
        updateInfo: UpdateInfo,
        progressCallback: (DownloadResult) -> Unit
    ): DownloadResult
    
    /**
     * Instala la actualización descargada
     */
    suspend fun installUpdate(context: Context, apkPath: String): Boolean
    
    /**
     * Obtiene la información de la versión actual
     */
    fun getCurrentVersionInfo(context: Context): Pair<String, Int>
    
    /**
     * Verifica si la actualización automática está habilitada
     */
    fun isAutoUpdateEnabled(context: Context): Boolean
    
    /**
     * Habilita/deshabilita la actualización automática
     */
    fun setAutoUpdateEnabled(context: Context, enabled: Boolean)
} 