package com.service.assasinscreed02.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.*

@Entity(tableName = "backup_files")
data class BackupFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val fileHash: String,
    val fileSize: Long,
    val fileType: String, // "image" o "video"
    val uploadDate: Long = System.currentTimeMillis(),
    val uploadStatus: String = "success", // "success", "failed", "pending"
    val telegramMessageId: String? = null,
    val errorMessage: String? = null
)

@Entity(tableName = "backup_sessions")
data class BackupSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val sessionType: String, // "automatic", "manual", "selective"
    val filesProcessed: Int = 0,
    val filesSuccess: Int = 0,
    val filesFailed: Int = 0,
    val totalSize: Long = 0,
    val status: String = "running" // "running", "completed", "failed"
)

@Entity(tableName = "device_stats")
data class DeviceStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val batteryLevel: Int,
    val isCharging: Boolean,
    val networkType: String,
    val freeStorage: Long,
    val totalStorage: Long,
    val wifiConnected: Boolean
)

@Dao
interface BackupFileDao {
    @Query("SELECT * FROM backup_files ORDER BY uploadDate DESC")
    fun getAllFiles(): Flow<List<BackupFile>>
    
    @Query("SELECT * FROM backup_files WHERE uploadStatus = :status ORDER BY uploadDate DESC")
    fun getFilesByStatus(status: String): Flow<List<BackupFile>>
    
    @Query("SELECT * FROM backup_files WHERE fileType = :type ORDER BY uploadDate DESC")
    fun getFilesByType(type: String): Flow<List<BackupFile>>
    
    @Query("SELECT COUNT(*) FROM backup_files WHERE uploadStatus = 'success'")
    suspend fun getSuccessfulBackupsCount(): Int
    
    @Query("SELECT COUNT(*) FROM backup_files WHERE uploadStatus = 'failed'")
    suspend fun getFailedBackupsCount(): Int
    
    @Query("SELECT SUM(fileSize) FROM backup_files WHERE uploadStatus = 'success'")
    suspend fun getTotalBackupSize(): Long?
    
    @Query("SELECT * FROM backup_files WHERE fileHash = :hash LIMIT 1")
    suspend fun getFileByHash(hash: String): BackupFile?
    
    @Insert
    suspend fun insertFile(file: BackupFile): Long
    
    @Update
    suspend fun updateFile(file: BackupFile)
    
    @Delete
    suspend fun deleteFile(file: BackupFile)
    
    @Query("DELETE FROM backup_files WHERE uploadDate < :timestamp")
    suspend fun deleteOldFiles(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM backup_files")
    suspend fun getTotalFilesCount(): Int
}

@Dao
interface BackupSessionDao {
    @Query("SELECT * FROM backup_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<BackupSession>>
    
    @Query("SELECT * FROM backup_sessions WHERE status = :status ORDER BY startTime DESC")
    fun getSessionsByStatus(status: String): Flow<List<BackupSession>>
    
    @Query("SELECT * FROM backup_sessions WHERE sessionType = :type ORDER BY startTime DESC")
    fun getSessionsByType(type: String): Flow<List<BackupSession>>
    
    @Insert
    suspend fun insertSession(session: BackupSession): Long
    
    @Update
    suspend fun updateSession(session: BackupSession)
    
    @Query("SELECT * FROM backup_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): BackupSession?
    
    @Query("SELECT COUNT(*) FROM backup_sessions WHERE status = 'completed'")
    suspend fun getCompletedSessionsCount(): Int
    
    @Query("SELECT SUM(filesSuccess) FROM backup_sessions WHERE status = 'completed'")
    suspend fun getTotalSuccessfulFiles(): Long?
}

@Dao
interface DeviceStatsDao {
    @Query("SELECT * FROM device_stats ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentStats(limit: Int = 100): Flow<List<DeviceStats>>
    
    @Insert
    suspend fun insertStats(stats: DeviceStats)
    
    @Query("DELETE FROM device_stats WHERE timestamp < :timestamp")
    suspend fun deleteOldStats(timestamp: Long)
    
    @Query("SELECT * FROM device_stats ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestStats(): DeviceStats?
}

@Database(
    entities = [BackupFile::class, BackupSession::class, DeviceStats::class],
    version = 1,
    exportSchema = false
)
abstract class BackupDatabase : RoomDatabase() {
    abstract fun backupFileDao(): BackupFileDao
    abstract fun backupSessionDao(): BackupSessionDao
    abstract fun deviceStatsDao(): DeviceStatsDao
    
    companion object {
        @Volatile
        private var INSTANCE: BackupDatabase? = null
        
        fun getDatabase(context: android.content.Context): BackupDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    BackupDatabase::class.java,
                    "backup_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 