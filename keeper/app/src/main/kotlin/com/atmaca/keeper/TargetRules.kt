package com.atmaca.keeper

object TargetRules {
    fun normalize(input: Collection<String>, selfPackage: String): List<String> {
        val seen = LinkedHashSet<String>()
        input.forEach { raw ->
            val value = raw.trim()
            if (value.isNotEmpty() && value != selfPackage) seen += value
        }
        return seen.toList()
    }
}
