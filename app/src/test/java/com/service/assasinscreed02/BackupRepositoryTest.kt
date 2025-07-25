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

    @Test
    fun `test BackupFile data class properties`() {
        // Given
        val backupFile = BackupFile(
            id = 1L,
            fileName = "test.jpg",
            filePath = "/test/path/test.jpg",
            fileHash = "abc123",
            fileSize = 1024L,
            fileType = "image",
            uploadDate = System.currentTimeMillis(),
            uploadStatus = "success",
            telegramMessageId = "12345",
            errorMessage = null
        )

        // Then
        assertEquals(1L, backupFile.id)
        assertEquals("test.jpg", backupFile.fileName)
        assertEquals("/test/path/test.jpg", backupFile.filePath)
        assertEquals("abc123", backupFile.fileHash)
        assertEquals(1024L, backupFile.fileSize)
        assertEquals("image", backupFile.fileType)
        assertEquals("success", backupFile.uploadStatus)
        assertEquals("12345", backupFile.telegramMessageId)
        assertNull(backupFile.errorMessage)
        assertTrue(backupFile.uploadDate > 0)
    }

    @Test
    fun `test BackupSession data class properties`() {
        // Given
        val session = BackupSession(
            id = 1L,
            startTime = System.currentTimeMillis(),
            endTime = null,
            sessionType = "automatic",
            filesProcessed = 10,
            filesSuccess = 8,
            filesFailed = 2,
            totalSize = 1024L * 1024L,
            status = "running"
        )

        // Then
        assertEquals(1L, session.id)
        assertEquals("automatic", session.sessionType)
        assertEquals("running", session.status)
        assertEquals(10, session.filesProcessed)
        assertEquals(8, session.filesSuccess)
        assertEquals(2, session.filesFailed)
        assertEquals(1024L * 1024L, session.totalSize)
        assertTrue(session.startTime > 0)
        assertNull(session.endTime)
    }

    @Test
    fun `test DeviceStats data class properties`() {
        // Given
        val stats = DeviceStats(
            id = 1L,
            timestamp = System.currentTimeMillis(),
            batteryLevel = 85,
            isCharging = false,
            networkType = "wifi",
            freeStorage = 1024L * 1024L * 1024L, // 1GB
            totalStorage = 64L * 1024L * 1024L * 1024L, // 64GB
            wifiConnected = true
        )

        // Then
        assertEquals(1L, stats.id)
        assertEquals(85, stats.batteryLevel)
        assertFalse(stats.isCharging)
        assertEquals("wifi", stats.networkType)
        assertEquals(1024L * 1024L * 1024L, stats.freeStorage)
        assertEquals(64L * 1024L * 1024L * 1024L, stats.totalStorage)
        assertTrue(stats.wifiConnected)
        assertTrue(stats.timestamp > 0)
    }

    @Test
    fun `test BackupFile equals and hashCode`() {
        // Given
        val file1 = BackupFile(
            fileName = "test.jpg",
            filePath = "/test/path/test.jpg",
            fileHash = "abc123",
            fileSize = 1024L,
            fileType = "image"
        )
        
        val file2 = BackupFile(
            fileName = "test.jpg",
            filePath = "/test/path/test.jpg",
            fileHash = "abc123",
            fileSize = 1024L,
            fileType = "image"
        )

        // Then
        assertEquals(file1, file2)
        assertEquals(file1.hashCode(), file2.hashCode())
    }

    @Test
    fun `test BackupSession equals and hashCode`() {
        // Given
        val session1 = BackupSession(
            sessionType = "automatic",
            status = "running"
        )
        
        val session2 = BackupSession(
            sessionType = "automatic",
            status = "running"
        )

        // Then
        assertEquals(session1, session2)
        assertEquals(session1.hashCode(), session2.hashCode())
    }

    @Test
    fun `test DeviceStats equals and hashCode`() {
        // Given
        val stats1 = DeviceStats(
            batteryLevel = 85,
            isCharging = false,
            networkType = "wifi",
            freeStorage = 1024L * 1024L * 1024L,
            totalStorage = 64L * 1024L * 1024L * 1024L,
            wifiConnected = true
        )
        
        val stats2 = DeviceStats(
            batteryLevel = 85,
            isCharging = false,
            networkType = "wifi",
            freeStorage = 1024L * 1024L * 1024L,
            totalStorage = 64L * 1024L * 1024L * 1024L,
            wifiConnected = true
        )

        // Then
        assertEquals(stats1, stats2)
        assertEquals(stats1.hashCode(), stats2.hashCode())
    }

    @Test
    fun `test BackupFile toString contains all properties`() {
        // Given
        val backupFile = BackupFile(
            fileName = "test.jpg",
            filePath = "/test/path/test.jpg",
            fileHash = "abc123",
            fileSize = 1024L,
            fileType = "image"
        )

        // When
        val toString = backupFile.toString()

        // Then
        assertTrue(toString.contains("test.jpg"))
        assertTrue(toString.contains("/test/path/test.jpg"))
        assertTrue(toString.contains("abc123"))
        assertTrue(toString.contains("1024"))
        assertTrue(toString.contains("image"))
    }

    @Test
    fun `test BackupSession toString contains all properties`() {
        // Given
        val session = BackupSession(
            sessionType = "automatic",
            status = "running"
        )

        // When
        val toString = session.toString()

        // Then
        assertTrue(toString.contains("automatic"))
        assertTrue(toString.contains("running"))
    }

    @Test
    fun `test DeviceStats toString contains all properties`() {
        // Given
        val stats = DeviceStats(
            batteryLevel = 85,
            isCharging = false,
            networkType = "wifi",
            freeStorage = 1024L * 1024L * 1024L,
            totalStorage = 64L * 1024L * 1024L * 1024L,
            wifiConnected = true
        )

        // When
        val toString = stats.toString()

        // Then
        assertTrue(toString.contains("85"))
        assertTrue(toString.contains("false"))
        assertTrue(toString.contains("wifi"))
        assertTrue(toString.contains("true"))
    }
} 