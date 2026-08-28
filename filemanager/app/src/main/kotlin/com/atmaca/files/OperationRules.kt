package com.atmaca.files

object OperationRules {
    fun canDeleteSourceAfterCopy(copySucceeded: Boolean): Boolean = copySucceeded
    fun validName(name: String): Boolean {
        val n = name.trim()
        return n.isNotEmpty() && !n.contains('/') && !n.contains('\\') && n != "." && n != ".."
    }
}
