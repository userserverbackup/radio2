package com.service.assasinscreed02

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.service.assasinscreed02.updater.data.repositories.GitHubUpdateRepository
import com.service.assasinscreed02.updater.domain.usecases.UpdateUseCase
import com.service.assasinscreed02.updater.domain.entities.UpdateResult
import com.service.assasinscreed02.updater.domain.entities.DownloadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Actividad para manejar actualizaciones automáticas
 */
class UpdateActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "UpdateActivity"
        
        /**
         * Inicia la verificación de actualizaciones
         */
        fun startUpdateCheck(context: Context) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.putExtra("check_updates", true)
            context.startActivity(intent)
        }
    }
    
    private lateinit var updateUseCase: UpdateUseCase
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar sistema de actualización
        val updateRepository = GitHubUpdateRepository()
        updateUseCase = UpdateUseCase(updateRepository)
        
        // Verificar si se debe hacer check de actualizaciones
        if (intent.getBooleanExtra("check_updates", false)) {
            checkForUpdates()
        } else {
            finish()
        }
    }
    
    /**
     * Verifica si hay actualizaciones disponibles
     */
    private fun checkForUpdates() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "Iniciando verificación de actualizaciones...")
                
                val result = withContext(Dispatchers.IO) {
                    updateUseCase.checkForUpdates(this@UpdateActivity)
                }
                
                when (result) {
                    is UpdateResult.UpdateAvailable -> {
                        showUpdateDialog(result.updateInfo)
                    }
                    is UpdateResult.NoUpdateAvailable -> {
                        Log.d(TAG, "No hay actualizaciones disponibles")
                        Toast.makeText(this@UpdateActivity, "No hay actualizaciones disponibles", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is UpdateResult.Error -> {
                        Log.e(TAG, "Error verificando actualizaciones: ${result.message}")
                        Toast.makeText(this@UpdateActivity, "Error verificando actualizaciones: ${result.message}", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error en verificación de actualizaciones: ${e.message}", e)
                Toast.makeText(this@UpdateActivity, "Error verificando actualizaciones: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    /**
     * Muestra el diálogo de actualización
     */
    private fun showUpdateDialog(updateInfo: com.service.assasinscreed02.updater.domain.entities.UpdateInfo) {
        val message = buildString {
            append("Nueva versión disponible: ${updateInfo.version}\n\n")
            append("Tamaño: ${formatFileSize(updateInfo.fileSize)}\n")
            append("Notas de la versión:\n")
            append(updateInfo.releaseNotes)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Actualización Disponible")
            .setMessage(message)
            .setPositiveButton("Actualizar") { _, _ ->
                downloadAndInstallUpdate(updateInfo)
            }
            .setNegativeButton("Más tarde") { _, _ ->
                finish()
            }
            .setCancelable(!updateInfo.isMandatory)
            .create()
        
        dialog.show()
    }
    
    /**
     * Descarga e instala la actualización
     */
    private fun downloadAndInstallUpdate(updateInfo: com.service.assasinscreed02.updater.domain.entities.UpdateInfo) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "Iniciando descarga de actualización: ${updateInfo.version}")
                
                val success = withContext(Dispatchers.IO) {
                    updateUseCase.downloadAndInstallUpdate(
                        this@UpdateActivity,
                        updateInfo
                    ) { downloadResult ->
                        when (downloadResult) {
                            is DownloadResult.Progress -> {
                                Log.d(TAG, "Progreso de descarga: ${downloadResult.percentage}%")
                                // Aquí se podría mostrar un progreso en la UI
                            }
                            is DownloadResult.Success -> {
                                Log.d(TAG, "Descarga completada: ${downloadResult.filePath}")
                            }
                            is DownloadResult.Error -> {
                                Log.e(TAG, "Error en descarga: ${downloadResult.message}")
                            }
                        }
                    }
                }
                
                if (success) {
                    Toast.makeText(this@UpdateActivity, "Instalación iniciada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@UpdateActivity, "Error iniciando instalación", Toast.LENGTH_LONG).show()
                }
                
                finish()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error descargando actualización: ${e.message}", e)
                Toast.makeText(this@UpdateActivity, "Error descargando actualización: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    /**
     * Formatea el tamaño de archivo
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
} 