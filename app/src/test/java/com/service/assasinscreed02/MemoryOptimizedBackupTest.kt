package com.service.assasinscreed02

import com.service.assasinscreed02.utils.MemoryOptimizedBackup
import com.service.assasinscreed02.repository.BackupRepository
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

    @Mock
    private lateinit var mockRepository: BackupRepository

    private lateinit var memoryOptimizedBackup: MemoryOptimizedBackup

    @Before
    fun setUp() {
        memoryOptimizedBackup = MemoryOptimizedBackup(mockContext)
    }

    @Test
    fun `test performBackup with empty file list returns success`() = runTest {
        // Given
        val files = emptyList<File>()
        val token = "test_token"
        val chatId = "test_chat_id"
        val sessionType = "automatic"

        `when`(mockRepository.insertSession(any())).thenReturn(1L)

        // When
        val result = memoryOptimizedBackup.performBackup(files, token, chatId, sessionType)

        // Then
        assertTrue(result.success)
        assertEquals(0, result.filesProcessed)
        assertEquals(0, result.filesSuccess)
        assertEquals(0, result.filesFailed)
        assertEquals(0L, result.totalSize)
        assertNull(result.errorMessage)
    }

    @Test
    fun `test performBackup with non-existent file marks as failed`() = runTest {
        // Given
        val nonExistentFile = File("/non/existent/file.jpg")
        val files = listOf(nonExistentFile)
        val token = "test_token"
        val chatId = "test_chat_id"

        `when`(mockRepository.insertSession(any())).thenReturn(1L)
        `when`(mockRepository.getFileByHash(any())).thenReturn(null)
        `when`(mockRepository.insertFile(any())).thenReturn(1L)

        // When
        val result = memoryOptimizedBackup.performBackup(files, token, chatId)

        // Then
        assertFalse(result.success)
        assertEquals(1, result.filesProcessed)
        assertEquals(0, result.filesSuccess)
        assertEquals(1, result.filesFailed)
        assertEquals(0L, result.totalSize)
    }

    @Test
    fun `test performBackup with already uploaded file skips it`() = runTest {
        // Given
        val file = File("/test/file.jpg")
        val files = listOf(file)
        val token = "test_token"
        val chatId = "test_chat_id"
        val existingFile = BackupFile(
            id = 1L,
            fileName = "file.jpg",
            filePath = "/test/file.jpg",
            fileHash = "abc123",
            fileSize = 1024L,
            fileType = "image"
        )

        `when`(mockRepository.insertSession(any())).thenReturn(1L)
        `when`(mockRepository.getFileByHash(any())).thenReturn(existingFile)

        // When
        val result = memoryOptimizedBackup.performBackup(files, token, chatId)

        // Then
        assertTrue(result.success)
        assertEquals(1, result.filesProcessed)
        assertEquals(0, result.filesSuccess)
        assertEquals(0, result.filesFailed)
        assertEquals(0L, result.totalSize)
    }

    @Test
    fun `test performBackup with valid file processes successfully`() = runTest {
        // Given
        val file = File("/test/file.jpg")
        val files = listOf(file)
        val token = "test_token"
        val chatId = "test_chat_id"

        `when`(mockRepository.insertSession(any())).thenReturn(1L)
        `when`(mockRepository.getFileByHash(any())).thenReturn(null)
        `when`(mockRepository.insertFile(any())).thenReturn(1L)
        `when`(mockRepository.getSessionById(1L)).thenReturn(
            BackupSession(id = 1L, sessionType = "automatic", status = "running")
        )

        // Mock file properties
        // Note: In a real test, you'd need to mock the file system operations
        // This is a simplified test

        // When
        val result = memoryOptimizedBackup.performBackup(files, token, chatId)

        // Then
        // The result will depend on the actual file upload implementation
        // For now, we just verify the method doesn't throw exceptions
        assertNotNull(result)
    }

    @Test
    fun `test performBackup handles repository errors gracefully`() = runTest {
        // Given
        val files = listOf(File("/test/file.jpg"))
        val token = "test_token"
        val chatId = "test_chat_id"

        `when`(mockRepository.insertSession(any())).thenThrow(RuntimeException("Database error"))

        // When
        val result = memoryOptimizedBackup.performBackup(files, token, chatId)

        // Then
        assertFalse(result.success)
        assertEquals(0, result.filesProcessed)
        assertEquals(0, result.filesSuccess)
        assertEquals(0, result.filesFailed)
        assertEquals(0L, result.totalSize)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Database error"))
    }

    @Test
    fun `test performBackup creates session with correct type`() = runTest {
        // Given
        val files = emptyList<File>()
        val token = "test_token"
        val chatId = "test_chat_id"
        val sessionType = "manual"

        `when`(mockRepository.insertSession(any())).thenReturn(1L)

        // When
        memoryOptimizedBackup.performBackup(files, token, chatId, sessionType)

        // Then
        verify(mockRepository).insertSession(
            argThat { session ->
                session.sessionType == sessionType && session.status == "running"
            }
        )
    }

    @Test
    fun `test performBackup updates session on completion`() = runTest {
        // Given
        val files = emptyList<File>()
        val token = "test_token"
        val chatId = "test_chat_id"

        `when`(mockRepository.insertSession(any())).thenReturn(1L)
        `when`(mockRepository.getSessionById(1L)).thenReturn(
            BackupSession(id = 1L, sessionType = "automatic", status = "running")
        )

        // When
        memoryOptimizedBackup.performBackup(files, token, chatId)

        // Then
        verify(mockRepository).updateSession(
            argThat { session ->
                session.id == 1L && 
                session.status == "completed" && 
                session.endTime != null
            }
        )
    }

    @Test
    fun `test performBackup saves device stats`() = runTest {
        // Given
        val files = emptyList<File>()
        val token = "test_token"
        val chatId = "test_chat_id"

        `when`(mockRepository.insertSession(any())).thenReturn(1L)
        `when`(mockRepository.getSessionById(1L)).thenReturn(
            BackupSession(id = 1L, sessionType = "automatic", status = "running")
        )

        // When
        memoryOptimizedBackup.performBackup(files, token, chatId)

        // Then
        verify(mockRepository).insertStats(any())
    }

    @Test
    fun `test performBackup handles large files correctly`() = runTest {
        // Given
        val largeFile = File("/test/large_file.mp4")
        val files = listOf(largeFile)
        val token = "test_token"
        val chatId = "test_chat_id"

        `when`(mockRepository.insertSession(any())).thenReturn(1L)
        `when`(mockRepository.getFileByHash(any())).thenReturn(null)
        `when`(mockRepository.insertFile(any())).thenReturn(1L)
        `when`(mockRepository.getSessionById(1L)).thenReturn(
            BackupSession(id = 1L, sessionType = "automatic", status = "running")
        )

        // Mock large file size (over 100MB)
        // Note: In a real test, you'd need to mock the file system operations

        // When
        val result = memoryOptimizedBackup.performBackup(files, token, chatId)

        // Then
        // The result will depend on the actual file upload implementation
        // For now, we just verify the method doesn't throw exceptions
        assertNotNull(result)
    }

    @Test
    fun `test performBackup handles multiple files correctly`() = runTest {
        // Given
        val file1 = File("/test/file1.jpg")
        val file2 = File("/test/file2.mp4")
        val files = listOf(file1, file2)
        val token = "test_token"
        val chatId = "test_chat_id"

        `when`(mockRepository.insertSession(any())).thenReturn(1L)
        `when`(mockRepository.getFileByHash(any())).thenReturn(null)
        `when`(mockRepository.insertFile(any())).thenReturn(1L)
        `when`(mockRepository.getSessionById(1L)).thenReturn(
            BackupSession(id = 1L, sessionType = "automatic", status = "running")
        )

        // When
        val result = memoryOptimizedBackup.performBackup(files, token, chatId)

        // Then
        // The result will depend on the actual file upload implementation
        // For now, we just verify the method doesn't throw exceptions
        assertNotNull(result)
        assertEquals(2, result.filesProcessed)
    }
} 