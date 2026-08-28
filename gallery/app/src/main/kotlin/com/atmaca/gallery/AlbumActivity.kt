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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(5,12,24)) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(dp(10),dp(12),dp(10),dp(8)) }
        bar.addView(TextView(this).apply { text="‹"; textSize=38f; setTextColor(Color.WHITE); setPadding(dp(4),0,dp(12),0); setOnClickListener{finish()} })
        bar.addView(TextView(this).apply { text = name; textSize = 24f; setTypeface(null,1); setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0,-2,1f))
        root.addView(bar)
        val rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@AlbumActivity, 4)
            adapter = MediaGridAdapter(emptyList()) { pos ->
                startActivity(Intent(this@AlbumActivity, ViewerActivity::class.java).putExtra(EXTRA_PATH, path).putExtra(ViewerActivity.EXTRA_POSITION, pos))
            }.also { this@AlbumActivity.adapter = it }
            setHasFixedSize(true)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(rv, LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
        scope.launch { adapter.submit(withContext(Dispatchers.IO){ MediaRepository(contentResolver).items(path) }) }
    }
    override fun onDestroy(){ scope.cancel(); super.onDestroy() }
    private fun dp(v:Int)=Math.round(v*resources.displayMetrics.density)
    companion object { const val EXTRA_PATH="path"; const val EXTRA_NAME="name" }
}
