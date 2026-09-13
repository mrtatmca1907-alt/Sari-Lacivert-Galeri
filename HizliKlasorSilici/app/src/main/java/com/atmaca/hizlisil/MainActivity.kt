package com.atmaca.hizlisil

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val PICK_TREE = 1907
        private const val PREFS = "fast_delete_prefs"
        private const val KEY_TREE = "tree_uri"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var selectedTree: Uri? = null
    private lateinit var selectButton: Button
    private lateinit var deleteButton: Button
    private lateinit var selectedText: TextView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedTree = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_TREE, null)
            ?.let(Uri::parse)
        buildUi()
        refreshSelection()
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(34), dp(22), dp(22))
            setBackgroundColor(Color.rgb(248, 249, 252))
        }

        val title = TextView(this).apply {
            text = "HIZLI KLASÖR SİLİCİ"
            textSize = 25f
            setTextColor(Color.rgb(11, 31, 77))
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(28)
        })

        selectedText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(selectedText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        selectButton = Button(this).apply {
            text = "KLASÖR SEÇ"
            setOnClickListener { pickFolder() }
        }
        root.addView(selectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
            bottomMargin = dp(14)
        })

        deleteButton = Button(this).apply {
            text = "HIZLI SİL"
            setOnClickListener { deleteSelectedFolderContents() }
        }
        root.addView(deleteButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
            bottomMargin = dp(22)
        })

        statusText = TextView(this).apply {
            text = "Hazır"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(11, 31, 77))
        }
        root.addView(statusText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val warning = TextView(this).apply {
            text = "Silme kalıcıdır. Çöp kutusuna taşımaz. Ön tarama veya dosya sayımı yapılmaz."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
        }
        root.addView(warning, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(26)
        })

        setContentView(root)
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        startActivityForResult(intent, PICK_TREE)
    }

    @Deprecated("Deprecated in Android API, retained to avoid AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_TREE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val takeFlags = data.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: SecurityException) {
        }
        selectedTree = uri
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TREE, uri.toString()).apply()
        refreshSelection()
        statusText.text = "Hazır"
    }

    private fun refreshSelection() {
        selectedText.text = selectedTree?.let { "Seçili klasör:\n$it" } ?: "Henüz klasör seçilmedi"
        deleteButton.isEnabled = selectedTree != null
    }

    private fun deleteSelectedFolderContents() {
        val tree = selectedTree ?: return
        setBusy(true)
        statusText.text = "Siliniyor..."

        executor.execute {
            var lastUiAt = 0L
            val result = TreeDeleteEngine(contentResolver).deleteTreeContents(tree) { stats ->
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastUiAt >= 300) {
                    lastUiAt = now
                    runOnUiThread {
                        statusText.text = "Siliniyor...  Dosya: ${stats.deletedFiles}  Klasör: ${stats.deletedDirs}  Hata: ${stats.errors}"
                    }
                }
            }
            runOnUiThread {
                statusText.text = "Bitti — Dosya: ${result.deletedFiles}  Klasör: ${result.deletedDirs}  Hata: ${result.errors}"
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        selectButton.isEnabled = !busy
        deleteButton.isEnabled = !busy && selectedTree != null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) executor.shutdown()
    }
}
