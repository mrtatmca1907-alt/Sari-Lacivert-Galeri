package com.atmaca.gallery

import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class MediaGridAdapter(private var items: List<MediaItem>, private val click:(Int)->Unit): RecyclerView.Adapter<MediaGridAdapter.H>() {
    class H(val box:FrameLayout,val image:ImageView,val badge:TextView):RecyclerView.ViewHolder(box)
    override fun onCreateViewHolder(p:ViewGroup,t:Int):H{
        val gap = Math.round(1.5f * p.resources.displayMetrics.density)
        val size=(p.resources.displayMetrics.widthPixels/4)
        val box=FrameLayout(p.context).apply { layoutParams=ViewGroup.MarginLayoutParams(size,size).apply { setMargins(gap,gap,gap,gap) } }
        val img=ImageView(p.context).apply{scaleType=ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.rgb(24,30,38))}
        box.addView(img,FrameLayout.LayoutParams(-1,-1))
        val badge=TextView(p.context).apply{setTextColor(Color.WHITE); setBackgroundColor(0x88000000.toInt()); textSize=15f; setPadding(7,3,7,3)}
        box.addView(badge,FrameLayout.LayoutParams(-2,-2,android.view.Gravity.BOTTOM or android.view.Gravity.END))
        return H(box,img,badge)
    }
    override fun onBindViewHolder(h:H,p:Int){
        val m=items[p]
        h.image.load(m.uri) { crossfade(false); size(360); allowHardware(true) }
        h.badge.text=if(m.mime.startsWith("video")) "▶" else ""
        h.box.setOnClickListener{ val pos=h.bindingAdapterPosition; if(pos!=RecyclerView.NO_POSITION) click(pos) }
    }
    override fun getItemCount()=items.size
    fun submit(v:List<MediaItem>){items=v;notifyDataSetChanged()}
}
