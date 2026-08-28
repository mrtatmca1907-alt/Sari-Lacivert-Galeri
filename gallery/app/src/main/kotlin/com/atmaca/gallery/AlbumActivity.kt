package com.atmaca.gallery

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class AlbumActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var adapter: MediaGridAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH) ?: MediaRepository.ROOT
        val name = intent.getStringExtra(EXTRA_NAME) ?: path
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(0,35,102)) }
        root.addView(TextView(this).apply { text = name; textSize = 23f; setTextColor(Color.rgb(255,220,0)); setPadding(18,18,18,12) })
        val rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@AlbumActivity, 4)
            adapter = MediaGridAdapter(emptyList()) { pos ->
                startActivity(Intent(this@AlbumActivity, ViewerActivity::class.java).putExtra(EXTRA_PATH, path).putExtra(ViewerActivity.EXTRA_POSITION, pos))
            }.also { this@AlbumActivity.adapter = it }
            setHasFixedSize(true)
        }
        root.addView(rv, LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
        scope.launch { adapter.submit(withContext(Dispatchers.IO){ MediaRepository(contentResolver).items(path) }) }
    }
    override fun onDestroy(){ scope.cancel(); super.onDestroy() }
    companion object { const val EXTRA_PATH="path"; const val EXTRA_NAME="name" }
}
