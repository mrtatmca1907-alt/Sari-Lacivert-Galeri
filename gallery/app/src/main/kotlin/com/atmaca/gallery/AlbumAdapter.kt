package com.atmaca.gallery

import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlbumAdapter(private var items: List<AlbumInfo>, private val onClick: (AlbumInfo) -> Unit) : RecyclerView.Adapter<AlbumAdapter.Holder>() {
    fun submit(value: List<AlbumInfo>) { items = value; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val d = parent.resources.displayMetrics.density
        fun dp(v: Int) = Math.round(v * d)
        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(Color.rgb(8, 42, 101))
        }
        val title = TextView(parent.context).apply { textSize = 19f; setTextColor(Color.WHITE); setTypeface(null, 1) }
        val count = TextView(parent.context).apply { textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, dp(4), 0, 0) }
        row.addView(title)
        row.addView(count)
        return Holder(row, title, count)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.name
        holder.count.text = "${item.count} öğe"
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
    class Holder(view: LinearLayout, val title: TextView, val count: TextView) : RecyclerView.ViewHolder(view)
}
