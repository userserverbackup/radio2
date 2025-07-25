package com.service.assasinscreed02

import com.service.assasinscreed02.utils.MemoryOptimizedBackup
import com.service.assasinscreed02.database.BackupFile
import com.service.assasinscreed02.database.BackupSession
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import android.content.Context
import java.io.File
import org.junit.Assert.*

@RunWith(MockitoJUnitRunner::class)
class MemoryOptimizedBackupTest {

    @Mock
    private lateinit var mockContext: Context

    @Test
    fun `test BackupResult data class properties`() {
        // Given
        val result = MemoryOptimizedBackup.BackupResult(
            success = true,
            filesProcessed = 10,
            filesSuccess = 8,
            filesFailed = 2,
            totalSize = 1024L * 1024L, // 1MB
            errorMessage = null
        )

        // Then
        assertTrue(result.success)
        assertEquals(10, result.filesProcessed)
        assertEquals(8, result.filesSuccess)
        assertEquals(2, result.filesFailed)
        assertEquals(1024L * 1024L, result.totalSize)
        assertNull(result.errorMessage)
    }

    @Test
    fun `test BackupResult with error message`() {
        // Given
        val errorMessage = "Test error message"
        val result = MemoryOptimizedBackup.BackupResult(
            success = false,
            filesProcessed = 5,
            filesSuccess = 0,
            filesFailed = 5,
            totalSize = 0L,
            errorMessage = errorMessage
        )

        // Then
        assertFalse(result.success)
        assertEquals(5, result.filesProcessed)
        assertEquals(0, result.filesSuccess)
        assertEquals(5, result.filesFailed)
        assertEquals(0L, result.totalSize)
        assertEquals(errorMessage, result.errorMessage)
    }

    @Test
    fun `test BackupResult equals and hashCode`() {
        // Given
        val result1 = MemoryOptimizedBackup.BackupResult(
            success = true,
            filesProcessed = 10,
            filesSuccess = 8,
            filesFailed = 2,
            totalSize = 1024L * 1024L
        )
        
        val result2 = MemoryOptimizedBackup.BackupResult(
            success = true,
            filesProcessed = 10,
            filesSuccess = 8,
            filesFailed = 2,
            totalSize = 1024L * 1024L
        )

        // Then
        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun `test BackupResult toString contains all properties`() {
        // Given
        val result = MemoryOptimizedBackup.BackupResult(
            success = true,
            filesProcessed = 10,
            filesSuccess = 8,
            filesFailed = 2,
            totalSize = 1024L * 1024L,
            errorMessage = "Test error"
        )

        // When
        val toString = result.toString()

        // Then
        assertTrue(toString.contains("true"))
        assertTrue(toString.contains("10"))
        assertTrue(toString.contains("8"))
        assertTrue(toString.contains("2"))
        assertTrue(toString.contains("1048576")) // 1024 * 1024
        assertTrue(toString.contains("Test error"))
    }

    @Test
    fun `test BackupResult copy method`() {
        // Given
        val original = MemoryOptimizedBackup.BackupResult(
            success = true,
            filesProcessed = 10,
            filesSuccess = 8,
            filesFailed = 2,
            totalSize = 1024L * 1024L
        )

        // When
        val copied = original.copy(
            filesProcessed = 15,
            filesSuccess = 12,
            filesFailed = 3
        )

        // Then
        assertEquals(original.success, copied.success)
        assertEquals(original.totalSize, copied.totalSize)
        assertEquals(original.errorMessage, copied.errorMessage)
        assertEquals(15, copied.filesProcessed)
        assertEquals(12, copied.filesSuccess)
        assertEquals(3, copied.filesFailed)
    }

    @Test
    fun `test BackupResult with zero values`() {
        // Given
        val result = MemoryOptimizedBackup.BackupResult(
            success = true,
            filesProcessed = 0,
            filesSuccess = 0,
            filesFailed = 0,
            totalSize = 0L
        )

        // Then
        assertTrue(result.success)
        assertEquals(0, result.filesProcessed)
        assertEquals(0, result.filesSuccess)
        assertEquals(0, result.filesFailed)
        assertEquals(0L, result.totalSize)
        assertNull(result.errorMessage)
    }

    @Test
    fun `test BackupResult with large values`() {
        // Given
        val largeSize = 1024L * 1024L * 1024L // 1GB
        val result = MemoryOptimizedBackup.BackupResult(
            success = true,
            filesProcessed = 1000,
            filesSuccess = 950,
            filesFailed = 50,
            totalSize = largeSize
        )

        // Then
        assertTrue(result.success)
        assertEquals(1000, result.filesProcessed)
        assertEquals(950, result.filesSuccess)
        assertEquals(50, result.filesFailed)
        assertEquals(largeSize, result.totalSize)
    }

    @Test
    fun `test BackupResult success rate calculation`() {
        // Given
        val result = MemoryOptimizedBackup.BackupResult(
            success = true,
            filesProcessed = 100,
            filesSuccess = 80,
            filesFailed = 20,
            totalSize = 1024L * 1024L
        )

        // Then
        val successRate = (result.filesSuccess.toFloat() / result.filesProcessed * 100).toInt()
        assertEquals(80, successRate)
    }

    @Test
    fun `test BackupResult failure rate calculation`() {
        // Given
        val result = MemoryOptimizedBackup.BackupResult(
            success = false,
            filesProcessed = 50,
            filesSuccess = 30,
            filesFailed = 20,
            totalSize = 512L * 1024L
        )

        // Then
        val failureRate = (result.filesFailed.toFloat() / result.filesProcessed * 100).toInt()
        assertEquals(40, failureRate)
    }
} 