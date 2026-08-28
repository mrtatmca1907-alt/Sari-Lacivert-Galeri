package com.atmaca.reeldroppro.core

object RetryPolicy {
    fun nextDelayMs(attempt: Int, retryable: Boolean): Long? {
        if (!retryable) return null
        val safeAttempt = attempt.coerceIn(0, 16)
        val delay = 5_000L * (1L shl safeAttempt)
        return delay.coerceAtMost(300_000L)
    }
}
