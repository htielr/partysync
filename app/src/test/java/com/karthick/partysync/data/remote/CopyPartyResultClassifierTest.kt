package com.karthick.partysync.data.remote

import com.karthick.partysync.domain.model.FileSyncStatus
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals

class CopyPartyResultClassifierTest {

    @Test
    fun `success classifies as SUCCESS with no error message`() {
        val (status, message) = classifyCopyPartyResult(CopyPartyResult.Success)
        assertEquals(FileSyncStatus.SUCCESS, status)
        assertEquals(null, message)
    }

    @Test
    fun `401 classifies as FAILED_AUTH`() {
        val (status, _) = classifyCopyPartyResult(CopyPartyResult.HttpError(401, "Unauthorized"))
        assertEquals(FileSyncStatus.FAILED_AUTH, status)
    }

    @Test
    fun `403 classifies as FAILED_AUTH`() {
        val (status, _) = classifyCopyPartyResult(CopyPartyResult.HttpError(403, "Forbidden"))
        assertEquals(FileSyncStatus.FAILED_AUTH, status)
    }

    @Test
    fun `5xx classifies as FAILED_RETRYABLE`() {
        val (status, _) = classifyCopyPartyResult(CopyPartyResult.HttpError(503, "Unavailable"))
        assertEquals(FileSyncStatus.FAILED_RETRYABLE, status)
    }

    @Test
    fun `other 4xx classifies as FAILED_OTHER`() {
        val (status, _) = classifyCopyPartyResult(CopyPartyResult.HttpError(404, "Not found"))
        assertEquals(FileSyncStatus.FAILED_OTHER, status)
    }

    @Test
    fun `network error classifies as FAILED_RETRYABLE with the exception message`() {
        val (status, message) = classifyCopyPartyResult(CopyPartyResult.NetworkError(IOException("timeout")))
        assertEquals(FileSyncStatus.FAILED_RETRYABLE, status)
        assertEquals("timeout", message)
    }
}
