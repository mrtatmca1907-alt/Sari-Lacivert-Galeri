package com.atmaca.files

import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView

class DocAdapter(
    private var items: List<DocumentFile>,
    private val selected: MutableSet<String>,
    private val click: (DocumentFile) -> Unit,
    private val longClick: (DocumentFile) -> Unit
) : RecyclerView.Adapter<DocAdapter.H>() {
    class H(val row: LinearLayout, val icon: TextView, val name: TextView, val meta: TextView) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H {
        val d = parent.resources.displayMetrics.density
        fun dp(v:Int)=Math.round(v*d)
        val row=LinearLayout(parent.context).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(14),dp(12),dp(14),dp(12))}
        val icon=TextView(parent.context).apply{textSize=26f;setPadding(0,0,dp(14),0)}
        val col=LinearLayout(parent.context).apply{orientation=LinearLayout.VERTICAL}
        val name=TextView(parent.context).apply{textSize=16f;setTextColor(Color.WHITE);maxLines=1}
        val meta=TextView(parent.context).apply{textSize=12f;setTextColor(Color.LTGRAY)}
        col.addView(name);col.addView(meta);row.addView(icon);row.addView(col,LinearLayout.LayoutParams(0,-2,1f))
        return H(row,icon,name,meta)
    }

    override fun onBindViewHolder(h:H,p:Int){
        val f=items[p];val key=f.uri.toString();h.icon.text=if(f.isDirectory)"📁" else "📄";h.name.text=f.name?:"Adsız"
        h.meta.text=if(f.isDirectory)"Klasör" else sizeText(f.length())
        h.row.setBackgroundColor(if(selected.contains(key)) Color.rgb(37,74,127) else Color.TRANSPARENT)
        h.row.setOnClickListener{click(f)};h.row.setOnLongClickListener{longClick(f);true}
    }
    override fun getItemCount()=items.size
    fun submit(v:List<DocumentFile>){items=v;notifyDataSetChanged()}
    fun refreshSelection(){notifyDataSetChanged()}
    private fun sizeText(v:Long):String=when{v>=1024L*1024*1024->"%.1f GB".format(v/(1024.0*1024*1024));v>=1024L*1024->"%.1f MB".format(v/(1024.0*1024));v>=1024->"%.1f KB".format(v/1024.0);else->"$v B"}
}
