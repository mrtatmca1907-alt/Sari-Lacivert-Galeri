package com.facebookdrop

import android.content.Context

class QueueStore(context: Context) {
    private val prefs = context.getSharedPreferences("facebook_drop", Context.MODE_PRIVATE)
    fun save(urls: List<String>) = prefs.edit().putString("queue", urls.joinToString("\n")).apply()
    fun load(): List<String> = prefs.getString("queue", "").orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    fun clear() = prefs.edit().remove("queue").apply()
}
