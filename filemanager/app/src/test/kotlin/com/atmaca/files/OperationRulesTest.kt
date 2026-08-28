package com.atmaca.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationRulesTest {
    @Test fun moveDeletesSourceOnlyAfterSuccessfulCopy() {
        assertFalse(OperationRules.canDeleteSourceAfterCopy(false))
        assertTrue(OperationRules.canDeleteSourceAfterCopy(true))
    }

    @Test fun nameValidationRejectsEmptyAndSlash() {
        assertFalse(OperationRules.validName(""))
        assertFalse(OperationRules.validName("a/b"))
        assertTrue(OperationRules.validName("Fotoğraflar"))
    }
}
