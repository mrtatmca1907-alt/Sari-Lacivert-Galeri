package com.atmaca.gallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.os.*
import android.provider.MediaStore
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var adapter: AlbumAdapter
    private lateinit var status: TextView
    private var observerRegistered = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { loadAlbums() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        ensurePermission()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(5, 12, 24)) }
        root.addView(TextView(this).apply {
            text = "Galeri"; textSize = 30f; setTypeface(null, 1); setTextColor(Color.WHITE); setPadding(dp(18), dp(22), dp(18), dp(2))
        })
        status = TextView(this).apply {
            text = "Hazırlanıyor..."; textSize = 13f; setTextColor(Color.rgb(170,180,195)); setPadding(dp(18), 0, dp(18), dp(8))
        }
        root.addView(status)
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = AlbumAdapter(emptyList()) { album ->
                startActivity(Intent(this@MainActivity, AlbumActivity::class.java)
                    .putExtra(AlbumActivity.EXTRA_PATH, album.path)
                    .putExtra(AlbumActivity.EXTRA_NAME, album.name))
            }.also { this@MainActivity.adapter = it }
            setHasFixedSize(true)
            setPadding(dp(7), dp(4), dp(7), dp(16))
            clipToPadding = false
        }
        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasPermission() = requiredPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun ensurePermission() {
        if (hasPermission()) loadAlbums() else requestPermissions(requiredPermissions(), REQ_MEDIA)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MEDIA && hasPermission()) loadAlbums() else status.text = "Fotoğraf/video izni gerekli."
    }

    private fun loadAlbums() {
        if (!hasPermission()) return
        status.text = "Albümler yükleniyor..."
        scope.launch {
            val result = withContext(Dispatchers.IO) { MediaRepository(contentResolver).albums() }
            adapter.submit(result)
            status.text = "${result.size} albüm"
        }
    }

    override fun onStart() {
        super.onStart()
        if (!observerRegistered) {
            contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
            observerRegistered = true
        }
        if (hasPermission()) loadAlbums()
    }

    override fun onStop() {
        if (observerRegistered) { runCatching { contentResolver.unregisterContentObserver(observer) }; observerRegistered = false }
        super.onStop()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private fun dp(v: Int) = Math.round(v * resources.displayMetrics.density)
    companion object { private const val REQ_MEDIA = 71 }
}
