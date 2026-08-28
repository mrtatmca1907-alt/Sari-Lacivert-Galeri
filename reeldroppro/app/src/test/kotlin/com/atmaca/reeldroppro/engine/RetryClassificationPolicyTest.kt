package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryClassificationPolicyTest {
    @Test
    fun `network and rate limit errors retry but private removed and unsupported do not`() {
        assertTrue(RetryClassificationPolicy.retryable(DownloadError.NETWORK))
        assertTrue(RetryClassificationPolicy.retryable(DownloadError.RATE_LIMIT))
        assertFalse(RetryClassificationPolicy.retryable(DownloadError.PRIVATE))
        assertFalse(RetryClassificationPolicy.retryable(DownloadError.REMOVED))
        assertFalse(RetryClassificationPolicy.retryable(DownloadError.UNSUPPORTED))
    }
}
