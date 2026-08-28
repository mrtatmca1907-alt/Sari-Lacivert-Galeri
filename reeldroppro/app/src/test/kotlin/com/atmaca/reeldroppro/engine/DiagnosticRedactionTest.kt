package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactionTest {
    @Test
    fun `cookies and authorization headers are redacted from diagnostics`() {
        val text = DiagnosticRedaction.redact("Cookie: sessionid=secret123\nAuthorization: Bearer abcdef\nHTTP 429")
        assertFalse(text.contains("secret123"))
        assertFalse(text.contains("abcdef"))
        assertTrue(text.contains("HTTP 429"))
    }
}
