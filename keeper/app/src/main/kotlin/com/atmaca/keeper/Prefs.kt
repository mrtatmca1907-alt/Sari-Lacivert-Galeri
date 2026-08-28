package com.atmaca.keeper

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("atmaca_keeper", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) { sp.edit().putBoolean(KEY_ENABLED, value).apply() }

    var networkOnline: Boolean
        get() = sp.getBoolean(KEY_NETWORK, false)
        set(value) { sp.edit().putBoolean(KEY_NETWORK, value).apply() }

    fun targets(): List<String> = TargetRules.normalize(
        sp.getStringSet(KEY_TARGETS, emptySet()).orEmpty(),
        "com.atmaca.keeper"
    )

    fun setTargets(values: Collection<String>) {
        val clean = TargetRules.normalize(values, "com.atmaca.keeper")
        sp.edit().putStringSet(KEY_TARGETS, LinkedHashSet(clean)).apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TARGETS = "targets"
        private const val KEY_NETWORK = "network"
    }
}
