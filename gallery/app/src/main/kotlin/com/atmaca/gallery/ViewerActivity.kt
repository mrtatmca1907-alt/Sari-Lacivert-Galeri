package com.atmaca.gallery

import android.app.Activity
import android.app.RecoverableSecurityException
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class ViewerActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var pager: ViewPager2
    private var items: List<MediaItem> = emptyList()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        hideBars()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        pager = ViewPager2(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(pager, FrameLayout.LayoutParams(-1, -1))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(0xCC050B14.toInt())
        }
        fun action(text: String, click: () -> Unit) = TextView(this).apply {
            this.text = text; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10)); setOnClickListener { click() }
        }
        actions.addView(action("‹ Geri") { finish() }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(action("Sil") { deleteCurrent() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)

        val path = intent.getStringExtra(AlbumActivity.EXTRA_PATH) ?: MediaRepository.ROOT
        val pos = intent.getIntExtra(EXTRA_POSITION, 0)
        scope.launch {
            items = withContext(Dispatchers.IO) { MediaRepository(contentResolver).items(path) }
            pager.adapter = Pages(items)
            pager.setCurrentItem(pos.coerceIn(0, (items.size - 1).coerceAtLeast(0)), false)
        }
    }

    private fun deleteCurrent() {
        val item = items.getOrNull(pager.currentItem) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pending = MediaStore.createDeleteRequest(contentResolver, listOf(item.uri))
                startIntentSenderForResult(pending.intentSender, REQ_DELETE, null, 0, 0, 0)
            } else {
                contentResolver.delete(item.uri, null, null)
                finish()
            }
        } catch (e: RecoverableSecurityException) {
            if (Build.VERSION.SDK_INT == 29) {
                startIntentSenderForResult(e.userAction.actionIntent.intentSender, REQ_DELETE, null, 0, 0, 0)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_DELETE && resultCode == RESULT_OK) finish()
    }

    private fun hideBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideBars() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private fun dp(v:Int)=Math.round(v*resources.displayMetrics.density)

    inner class Pages(private val data: List<MediaItem>) : RecyclerView.Adapter<Pages.H>() {
        inner class H(val box: FrameLayout) : RecyclerView.ViewHolder(box)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = H(FrameLayout(p.context).apply { layoutParams = ViewGroup.LayoutParams(-1, -1); setBackgroundColor(Color.BLACK) })
        override fun getItemCount() = data.size
        override fun onBindViewHolder(h: H, p: Int) {
            h.box.removeAllViews()
            val m = data[p]
            if (m.mime.startsWith("video")) {
                val v = VideoView(this@ViewerActivity)
                v.setVideoURI(m.uri)
                v.setMediaController(MediaController(this@ViewerActivity).apply { setAnchorView(v) })
                h.box.addView(v, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(58) })
                v.setOnPreparedListener { it.isLooping = false; v.start() }
            } else {
                val z = ZoomImageView(this@ViewerActivity)
                z.setImageURI(m.uri)
                h.box.addView(z, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(58) })
            }
        }
    }

    companion object { const val EXTRA_POSITION = "position"; private const val REQ_DELETE = 91 }
}
