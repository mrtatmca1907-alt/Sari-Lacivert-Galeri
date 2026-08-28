package com.atmaca.reeldroppro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryPolicyTest {
    @Test fun exponentialDelayStartsAtFiveSecondsAndCapsAtFiveMinutes() {
        assertEquals(5_000L, RetryPolicy.nextDelayMs(0, true))
        assertEquals(10_000L, RetryPolicy.nextDelayMs(1, true))
        assertEquals(20_000L, RetryPolicy.nextDelayMs(2, true))
        assertEquals(300_000L, RetryPolicy.nextDelayMs(20, true))
    }

    @Test fun nonRetryableErrorsStopImmediately() {
        assertNull(RetryPolicy.nextDelayMs(0, false))
    }
}
