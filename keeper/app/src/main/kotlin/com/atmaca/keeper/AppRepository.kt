package com.atmaca.keeper

import android.content.Context
import android.content.Intent

 data class AppItem(val packageName: String, val label: String)

class AppRepository(private val context: Context) {
    fun launcherApps(): List<AppItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                AppItem(pkg, ri.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
