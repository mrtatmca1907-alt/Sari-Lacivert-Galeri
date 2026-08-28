package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorClassifierTest {
    @Test fun classifiesAuthenticationAndPrivateContent() {
        val error = ErrorClassifier.classify("This content is private. Login required", null)
        assertEquals(DownloadError.Kind.AUTH_REQUIRED, error.kind)
        assertFalse(error.retryable)
    }

    @Test fun classifiesRateLimitAsRetryable() {
        val error = ErrorClassifier.classify("HTTP Error 429: Too Many Requests", null)
        assertEquals(DownloadError.Kind.RATE_LIMITED, error.kind)
        assertTrue(error.retryable)
    }

    @Test fun classifiesNetworkFailureAsRetryable() {
        val error = ErrorClassifier.classify("Unable to download webpage: timed out", null)
        assertEquals(DownloadError.Kind.NETWORK, error.kind)
        assertTrue(error.retryable)
    }

    @Test fun classifiesRemovedAndUnsupportedAsPermanent() {
        assertEquals(DownloadError.Kind.REMOVED, ErrorClassifier.classify("Video unavailable or removed", null).kind)
        assertEquals(DownloadError.Kind.UNSUPPORTED, ErrorClassifier.classify("Unsupported URL", null).kind)
    }

    @Test fun genericExitDoesNotEraseUsefulStderr() {
        val error = ErrorClassifier.classify("Extractor failed while parsing media", IllegalStateException("exit code 1"))
        assertEquals(DownloadError.Kind.EXTRACTOR, error.kind)
        assertTrue(error.message.contains("parsing media"))
    }
}
