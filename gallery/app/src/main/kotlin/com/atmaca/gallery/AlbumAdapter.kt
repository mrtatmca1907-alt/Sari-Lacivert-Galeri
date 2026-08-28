package com.atmaca.gallery

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class AlbumAdapter(private var items: List<AlbumInfo>, private val onClick: (AlbumInfo) -> Unit) : RecyclerView.Adapter<AlbumAdapter.Holder>() {
    fun submit(value: List<AlbumInfo>) { items = value; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val d = parent.resources.displayMetrics.density
        fun dp(v: Int) = Math.round(v * d)
        val card = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(10))
            background = GradientDrawable().apply { setColor(Color.rgb(10, 31, 66)); cornerRadius = dp(14).toFloat() }
            val margin = dp(7)
            layoutParams = ViewGroup.MarginLayoutParams(-1, -2).apply { setMargins(margin, margin, margin, margin) }
        }
        val cover = ImageView(parent.context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(25, 45, 75))
        }
        card.addView(cover, LinearLayout.LayoutParams(-1, dp(150)))
        val title = TextView(parent.context).apply {
            textSize = 16f; setTextColor(Color.WHITE); setTypeface(null, 1); maxLines = 1; setPadding(dp(7), dp(9), dp(7), 0)
        }
        val count = TextView(parent.context).apply {
            textSize = 12f; setTextColor(Color.rgb(185, 195, 210)); setPadding(dp(7), dp(2), dp(7), 0)
        }
        card.addView(title); card.addView(count)
        return Holder(card, cover, title, count)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.name
        holder.count.text = "${item.count} öğe"
        holder.cover.load(item.coverUri) { crossfade(false); size(420); allowHardware(true) }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
    class Holder(view: LinearLayout, val cover: ImageView, val title: TextView, val count: TextView) : RecyclerView.ViewHolder(view)
}
