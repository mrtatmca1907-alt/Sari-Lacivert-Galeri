package com.atmaca.gallery

import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MediaGridAdapter(private var items: List<MediaItem>, private val click:(Int)->Unit): RecyclerView.Adapter<MediaGridAdapter.H>() {
    class H(val box:FrameLayout,val image:ImageView,val badge:TextView):RecyclerView.ViewHolder(box)
    override fun onCreateViewHolder(p:ViewGroup,t:Int):H{
        val box=FrameLayout(p.context); val size=(p.resources.displayMetrics.widthPixels/4)
        box.layoutParams=ViewGroup.LayoutParams(size,size)
        val img=ImageView(p.context).apply{scaleType=ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.DKGRAY)}
        box.addView(img,FrameLayout.LayoutParams(-1,-1))
        val badge=TextView(p.context).apply{setTextColor(Color.WHITE); setBackgroundColor(0x88000000.toInt()); textSize=11f; setPadding(5,2,5,2)}
        box.addView(badge,FrameLayout.LayoutParams(-2,-2,android.view.Gravity.BOTTOM or android.view.Gravity.END))
        return H(box,img,badge)
    }
    override fun onBindViewHolder(h:H,p:Int){ val m=items[p]; h.image.setImageURI(m.uri); h.badge.text=if(m.mime.startsWith("video")) "▶" else ""; h.box.setOnClickListener{click(h.bindingAdapterPosition)} }
    override fun getItemCount()=items.size
    fun submit(v:List<MediaItem>){items=v;notifyDataSetChanged()}
}
