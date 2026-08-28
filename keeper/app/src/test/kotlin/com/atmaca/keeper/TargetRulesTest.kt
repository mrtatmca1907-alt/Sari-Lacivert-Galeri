package com.atmaca.keeper

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetRulesTest {
    @Test fun normalize_removes_blank_duplicates_and_self() {
        val result = TargetRules.normalize(
            listOf(" com.example.a ", "", "com.example.a", "com.atmaca.keeper", "com.example.b"),
            "com.atmaca.keeper"
        )
        assertEquals(listOf("com.example.a", "com.example.b"), result)
    }

    @Test fun normalize_is_stable_and_ignores_whitespace_only_values() {
        val result = TargetRules.normalize(listOf("  ", "b.pkg", "a.pkg", "b.pkg"), "self.pkg")
        assertEquals(listOf("b.pkg", "a.pkg"), result)
    }
}
