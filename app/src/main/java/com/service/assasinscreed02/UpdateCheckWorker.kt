package com.service.assasinscreed02

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.service.assasinscreed02.updater.data.repositories.GitHubUpdateRepository
import com.service.assasinscreed02.updater.domain.usecases.UpdateUseCase
import com.service.assasinscreed02.updater.domain.entities.UpdateResult

/**
 * Worker para verificar actualizaciones automáticamente
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val WORK_NAME = "update_check_worker"
    }
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Iniciando verificación automática de actualizaciones...")
            
            val updateRepository = GitHubUpdateRepository()
            val updateUseCase = UpdateUseCase(updateRepository)
            
            // Verificar si la actualización automática está habilitada
            if (!updateUseCase.isAutoUpdateEnabled(applicationContext)) {
                Log.d(TAG, "Actualización automática deshabilitada")
                return Result.success()
            }
            
            // Verificar actualizaciones
            val result = updateUseCase.performAutoUpdateCheck(applicationContext)
            
            when (result) {
                is UpdateResult.UpdateAvailable -> {
                    Log.d(TAG, "Actualización disponible: ${result.updateInfo.version}")
                    
                    // Iniciar actividad de actualización
                    UpdateActivity.startUpdateCheck(applicationContext)
                    
                    Result.success()
                }
                is UpdateResult.NoUpdateAvailable -> {
                    Log.d(TAG, "No hay actualizaciones disponibles")
                    Result.success()
                }
                is UpdateResult.Error -> {
                    Log.e(TAG, "Error verificando actualizaciones: ${result.message}")
                    Result.retry()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en worker de actualización: ${e.message}", e)
            Result.retry()
        }
    }
} 