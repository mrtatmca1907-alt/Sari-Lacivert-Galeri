package com.atmaca.files

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("atmaca_files", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMediaPermissions()
        buildUi()
    }

    private fun buildUi() {
        val navy = Color.rgb(7, 25, 58)
        val yellow = Color.rgb(255, 220, 0)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(18))
            setBackgroundColor(navy)
        }
        root.addView(TextView(this).apply {
            text = "ATMACA DOSYALAR"
            textSize = 27f
            setTextColor(yellow)
            setTypeface(null, 1)
        })
        root.addView(TextView(this).apply {
            text = "Hafif dosya yöneticisi"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(18))
        })

        val grid = GridLayout(this).apply { columnCount = 2; rowCount = 3 }
        val cats = listOf(
            "🖼 Görseller" to "images", "🎬 Videolar" to "videos",
            "🎵 Ses" to "audio", "📄 Belgeler" to "documents",
            "📦 APK" to "apks", "⬇ İndirilenler" to "downloads"
        )
        cats.forEach { (label, key) ->
            val b = Button(this).apply {
                text = label; textSize = 15f; setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(18, 48, 92))
                setOnClickListener { startActivity(Intent(this@MainActivity, CategoryActivity::class.java).putExtra("category", key)) }
            }
            grid.addView(b, GridLayout.LayoutParams().apply {
                width = 0; height = dp(72); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            })
        }
        root.addView(grid)

        root.addView(TextView(this).apply {
            text = "Depolama"
            textSize = 19f; setTextColor(Color.WHITE); setTypeface(null, 1)
            setPadding(0, dp(18), 0, dp(8))
        })
        root.addView(actionButton("DAHİLİ / SD KART EKLE", yellow, navy) { pickStorage() })
        val saved = prefs.getString("root_uri", null)
        if (saved != null) {
            root.addView(actionButton("SEÇİLİ DEPOLAMAYI AÇ", Color.WHITE, navy) {
                startActivity(Intent(this, BrowserActivity::class.java).putExtra("uri", saved).putExtra("isRoot", true))
            })
        }
        root.addView(TextView(this).apply {
            text = "Tam disk taraması yapmaz; yalnız açtığın klasörü listeler."
            textSize = 12f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(18), 0, 0)
        })
        setContentView(root)
    }

    private fun pickStorage() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        ), 41)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
            prefs.edit().putString("root_uri", uri.toString()).apply()
            startActivity(Intent(this, BrowserActivity::class.java).putExtra("uri", uri.toString()).putExtra("isRoot", true))
            buildUi()
        }
    }

    private fun requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            val perms = arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) requestPermissions(perms, 90)
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 90)
        }
    }

    private fun actionButton(label: String, bg: Int, fg: Int, click: () -> Unit) = Button(this).apply {
        text = label; textSize = 15f; setTextColor(fg); setTypeface(null, 1); setBackgroundColor(bg)
        setOnClickListener { click() }
    }
    private fun dp(v: Int) = Math.round(v * resources.displayMetrics.density)
}
