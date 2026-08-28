package com.atmaca.gallery

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import kotlinx.coroutines.*

class ViewerActivity: Activity(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    override fun onCreate(b:Bundle?){super.onCreate(b); window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION; val pager=ViewPager2(this); pager.setBackgroundColor(Color.BLACK); setContentView(pager); val path=intent.getStringExtra(AlbumActivity.EXTRA_PATH)?:MediaRepository.ROOT; val pos=intent.getIntExtra(EXTRA_POSITION,0); scope.launch{val items=withContext(Dispatchers.IO){MediaRepository(contentResolver).items(path)}; pager.adapter=Pages(items); pager.setCurrentItem(pos.coerceIn(0,(items.size-1).coerceAtLeast(0)),false)}}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    inner class Pages(private val items:List<MediaItem>):RecyclerView.Adapter<Pages.H>(){
        inner class H(val box:FrameLayout):RecyclerView.ViewHolder(box)
        override fun onCreateViewHolder(p:ViewGroup,t:Int)=H(FrameLayout(p.context).apply{layoutParams=ViewGroup.LayoutParams(-1,-1);setBackgroundColor(Color.BLACK)})
        override fun getItemCount()=items.size
        override fun onBindViewHolder(h:H,p:Int){h.box.removeAllViews();val m=items[p];if(m.mime.startsWith("video")){val v=VideoView(this@ViewerActivity);v.setVideoURI(m.uri);v.setMediaController(MediaController(this@ViewerActivity).apply{setAnchorView(v)});h.box.addView(v,FrameLayout.LayoutParams(-1,-1));v.setOnPreparedListener{it.isLooping=false;v.start()}}else{val z=ZoomImageView(this@ViewerActivity);z.setImageURI(m.uri);h.box.addView(z,FrameLayout.LayoutParams(-1,-1))}}
    }
    companion object{const val EXTRA_POSITION="position"}
}
