package com.atmaca.hizlisil

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val PICK_FILES = 1906
        private const val PICK_TREE = 1907
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val selectedFiles = mutableListOf<Uri>()
    private var selectedTree: Uri? = null

    private lateinit var fileButton: Button
    private lateinit var folderButton: Button
    private lateinit var deleteButton: Button
    private lateinit var selectedText: TextView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshSelection()
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(34), dp(22), dp(22))
            setBackgroundColor(Color.rgb(248, 249, 252))
        }

        root.addView(TextView(this).apply {
            text = "HIZLI SİLİCİ"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(11, 31, 77))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(24)
        })

        selectedText = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }
        root.addView(selectedText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        fileButton = Button(this).apply {
            text = "DOSYA SEÇ / ÇOKLU SEÇ"
            setOnClickListener { pickFiles() }
        }
        root.addView(fileButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
            bottomMargin = dp(12)
        })

        folderButton = Button(this).apply {
            text = "KLASÖR SEÇ"
            setOnClickListener { pickFolder() }
        }
        root.addView(folderButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
            bottomMargin = dp(12)
        })

        deleteButton = Button(this).apply {
            text = "SEÇİLENLERİ HIZLI SİL"
            setOnClickListener { deleteSelected() }
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

        root.addView(TextView(this).apply {
            text = "Ön tarama ve dosya sayımı yapmaz. Seçilenleri doğrudan siler. Silme kalıcıdır."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(26)
        })

        setContentView(root)
    }

    private fun pickFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, PICK_FILES)
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

    @Deprecated("Deprecated in Android API; used intentionally to keep app dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            PICK_FILES -> {
                selectedTree = null
                selectedFiles.clear()

                val clip = data.clipData
                if (clip != null) {
                    for (i in 0 until clip.itemCount) {
                        addFileUri(clip.getItemAt(i).uri, data.flags)
                    }
                } else {
                    data.data?.let { addFileUri(it, data.flags) }
                }
                statusText.text = "Hazır"
                refreshSelection()
            }

            PICK_TREE -> {
                selectedFiles.clear()
                selectedTree = data.data
                selectedTree?.let { uri ->
                    val flags = data.flags and
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    try {
                        contentResolver.takePersistableUriPermission(uri, flags)
                    } catch (_: Exception) {
                    }
                }
                statusText.text = "Hazır"
                refreshSelection()
            }
        }
    }

    private fun addFileUri(uri: Uri, dataFlags: Int) {
        if (uri !in selectedFiles) selectedFiles += uri
        val flags = dataFlags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
        }
    }

    private fun refreshSelection() {
        selectedText.text = when {
            selectedFiles.isNotEmpty() -> "Seçili dosya: ${selectedFiles.size}"
            selectedTree != null -> "Seçili klasör:\n$selectedTree"
            else -> "Dosya veya klasör seç"
        }
        deleteButton.isEnabled = selectedFiles.isNotEmpty() || selectedTree != null
    }

    private fun deleteSelected() {
        if (selectedFiles.isNotEmpty()) {
            deleteFiles()
        } else if (selectedTree != null) {
            deleteFolder()
        }
    }

    private fun deleteFiles() {
        val files = selectedFiles.toList()
        setBusy(true)
        statusText.text = "Siliniyor..."

        executor.execute {
            var deleted = 0
            var errors = 0
            files.forEachIndexed { index, uri ->
                try {
                    if (DocumentsContract.deleteDocument(contentResolver, uri)) deleted++ else errors++
                } catch (_: Exception) {
                    errors++
                }
                if ((index + 1) % 25 == 0 || index == files.lastIndex) {
                    val d = deleted
                    val e = errors
                    runOnUiThread {
                        statusText.text = "Siliniyor...  Silinen: $d  Hata: $e"
                    }
                }
            }

            runOnUiThread {
                selectedFiles.clear()
                statusText.text = "Bitti — Silinen: $deleted  Hata: $errors"
                refreshSelection()
                setBusy(false)
            }
        }
    }

    private fun deleteFolder() {
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
                        statusText.text = "Siliniyor... Dosya: ${stats.deletedFiles} Klasör: ${stats.deletedDirs} Hata: ${stats.errors}"
                    }
                }
            }
            runOnUiThread {
                selectedTree = null
                statusText.text = "Bitti — Dosya: ${result.deletedFiles} Klasör: ${result.deletedDirs} Hata: ${result.errors}"
                refreshSelection()
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        fileButton.isEnabled = !busy
        folderButton.isEnabled = !busy
        deleteButton.isEnabled = !busy && (selectedFiles.isNotEmpty() || selectedTree != null)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) executor.shutdown()
    }
}
