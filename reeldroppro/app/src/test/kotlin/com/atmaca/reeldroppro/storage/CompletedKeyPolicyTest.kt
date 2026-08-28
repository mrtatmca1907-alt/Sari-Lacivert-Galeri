package com.atmaca.reeldroppro.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class CompletedKeyPolicyTest {
    @Test
    fun `same media identity produces same completed key`() {
        val a = CompletedKeyPolicy.key("Instagram", "UserName", "ABC123")
        val b = CompletedKeyPolicy.key("instagram", "username", "ABC123")
        assertEquals(a, b)
    }
}
