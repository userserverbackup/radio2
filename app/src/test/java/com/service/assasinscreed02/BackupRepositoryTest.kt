package com.service.assasinscreed02

import com.service.assasinscreed02.database.*
import com.service.assasinscreed02.repository.BackupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import android.content.Context
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*

@RunWith(MockitoJUnitRunner::class)
class BackupRepositoryTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockBackupFileDao: BackupFileDao

    @Mock
    private lateinit var mockBackupSessionDao: BackupSessionDao

    @Mock
    private lateinit var mockDeviceStatsDao: DeviceStatsDao

    @Mock
    private lateinit var mockDatabase: BackupDatabase

    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        `when`(mockDatabase.backupFileDao()).thenReturn(mockBackupFileDao)
        `when`(mockDatabase.backupSessionDao()).thenReturn(mockBackupSessionDao)
        `when`(mockDatabase.deviceStatsDao()).thenReturn(mockDeviceStatsDao)
        
        // Mock the database builder
        // Note: In a real test, you'd use a test database or dependency injection
        repository = BackupRepository(mockContext)
    }

    @Test
    fun `test insertFile returns correct ID`() = runTest {
        // Given
        val backupFile = BackupFile(
            fileName = "test.jpg",
            filePath = "/test/path/test.jpg",
            fileHash = "abc123",
            fileSize = 1024L,
            fileType = "image"
        )
        val expectedId = 1L
        `when`(mockBackupFileDao.insertFile(backupFile)).thenReturn(expectedId)

        // When
        val result = repository.insertFile(backupFile)

        // Then
        assertEquals(expectedId, result)
        verify(mockBackupFileDao).insertFile(backupFile)
    }

    @Test
    fun `test getFileByHash returns correct file`() = runTest {
        // Given
        val hash = "abc123"
        val expectedFile = BackupFile(
            id = 1L,
            fileName = "test.jpg",
            filePath = "/test/path/test.jpg",
            fileHash = hash,
            fileSize = 1024L,
            fileType = "image"
        )
        `when`(mockBackupFileDao.getFileByHash(hash)).thenReturn(expectedFile)

        // When
        val result = repository.getFileByHash(hash)

        // Then
        assertEquals(expectedFile, result)
        verify(mockBackupFileDao).getFileByHash(hash)
    }

    @Test
    fun `test getSuccessfulBackupsCount returns correct count`() = runTest {
        // Given
        val expectedCount = 5
        `when`(mockBackupFileDao.getSuccessfulBackupsCount()).thenReturn(expectedCount)

        // When
        val result = repository.getSuccessfulBackupsCount()

        // Then
        assertEquals(expectedCount, result)
        verify(mockBackupFileDao).getSuccessfulBackupsCount()
    }

    @Test
    fun `test getFailedBackupsCount returns correct count`() = runTest {
        // Given
        val expectedCount = 2
        `when`(mockBackupFileDao.getFailedBackupsCount()).thenReturn(expectedCount)

        // When
        val result = repository.getFailedBackupsCount()

        // Then
        assertEquals(expectedCount, result)
        verify(mockBackupFileDao).getFailedBackupsCount()
    }

    @Test
    fun `test getTotalBackupSize returns correct size`() = runTest {
        // Given
        val expectedSize = 1024L * 1024L // 1MB
        `when`(mockBackupFileDao.getTotalBackupSize()).thenReturn(expectedSize)

        // When
        val result = repository.getTotalBackupSize()

        // Then
        assertEquals(expectedSize, result)
        verify(mockBackupFileDao).getTotalBackupSize()
    }

    @Test
    fun `test getAllFiles returns flow of files`() = runTest {
        // Given
        val files = listOf(
            BackupFile(
                id = 1L,
                fileName = "test1.jpg",
                filePath = "/test/path/test1.jpg",
                fileHash = "abc123",
                fileSize = 1024L,
                fileType = "image"
            ),
            BackupFile(
                id = 2L,
                fileName = "test2.mp4",
                filePath = "/test/path/test2.mp4",
                fileHash = "def456",
                fileSize = 2048L,
                fileType = "video"
            )
        )
        `when`(mockBackupFileDao.getAllFiles()).thenReturn(flowOf(files))

        // When
        val result = repository.getAllFiles()

        // Then
        result.collect { resultFiles ->
            assertEquals(files, resultFiles)
        }
        verify(mockBackupFileDao).getAllFiles()
    }

    @Test
    fun `test insertSession returns correct ID`() = runTest {
        // Given
        val session = BackupSession(
            sessionType = "automatic",
            status = "running"
        )
        val expectedId = 1L
        `when`(mockBackupSessionDao.insertSession(session)).thenReturn(expectedId)

        // When
        val result = repository.insertSession(session)

        // Then
        assertEquals(expectedId, result)
        verify(mockBackupSessionDao).insertSession(session)
    }

    @Test
    fun `test getSessionById returns correct session`() = runTest {
        // Given
        val sessionId = 1L
        val expectedSession = BackupSession(
            id = sessionId,
            sessionType = "automatic",
            status = "completed"
        )
        `when`(mockBackupSessionDao.getSessionById(sessionId)).thenReturn(expectedSession)

        // When
        val result = repository.getSessionById(sessionId)

        // Then
        assertEquals(expectedSession, result)
        verify(mockBackupSessionDao).getSessionById(sessionId)
    }

    @Test
    fun `test getBackupStatistics returns correct statistics`() = runTest {
        // Given
        val successfulCount = 5
        val failedCount = 2
        val totalSize = 1024L * 1024L // 1MB
        val completedSessions = 3

        `when`(mockBackupFileDao.getSuccessfulBackupsCount()).thenReturn(successfulCount)
        `when`(mockBackupFileDao.getFailedBackupsCount()).thenReturn(failedCount)
        `when`(mockBackupFileDao.getTotalBackupSize()).thenReturn(totalSize)
        `when`(mockBackupSessionDao.getCompletedSessionsCount()).thenReturn(completedSessions)

        // When
        val result = repository.getBackupStatistics()

        // Then
        assertEquals(successfulCount, result["successfulBackups"])
        assertEquals(failedCount, result["failedBackups"])
        assertEquals(totalSize, result["totalSize"])
        assertEquals(completedSessions, result["completedSessions"])
        
        // Calculate expected success rate: 5/(5+2) * 100 = 71%
        val expectedSuccessRate = (successfulCount.toFloat() / (successfulCount + failedCount) * 100).toInt()
        assertEquals(expectedSuccessRate, result["successRate"])
    }

    @Test
    fun `test getBackupStatistics handles zero backups gracefully`() = runTest {
        // Given
        `when`(mockBackupFileDao.getSuccessfulBackupsCount()).thenReturn(0)
        `when`(mockBackupFileDao.getFailedBackupsCount()).thenReturn(0)
        `when`(mockBackupFileDao.getTotalBackupSize()).thenReturn(0L)
        `when`(mockBackupSessionDao.getCompletedSessionsCount()).thenReturn(0)

        // When
        val result = repository.getBackupStatistics()

        // Then
        assertEquals(0, result["successfulBackups"])
        assertEquals(0, result["failedBackups"])
        assertEquals(0L, result["totalSize"])
        assertEquals(0, result["completedSessions"])
        assertEquals(0, result["successRate"])
    }
} 