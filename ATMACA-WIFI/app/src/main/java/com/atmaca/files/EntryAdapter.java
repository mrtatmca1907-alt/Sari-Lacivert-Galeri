package com.atmaca.files;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public final class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.Holder> {
    public interface Listener { void onClick(CatalogEntry e); void onLongClick(CatalogEntry e); }
    private final ArrayList<CatalogEntry> items = new ArrayList<>();
    private final Listener listener;
    public EntryAdapter(Listener listener) { this.listener = listener; setHasStableIds(true); }
    public void setItems(List<CatalogEntry> rows) { items.clear(); items.addAll(rows); notifyDataSetChanged(); }
    @Override public long getItemId(int position) { return items.get(position).path.hashCode(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(28, 18, 28, 18);
        TextView title = new TextView(parent.getContext());
        title.setTextSize(17); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView sub = new TextView(parent.getContext()); sub.setTextSize(12); sub.setGravity(Gravity.START);
        row.addView(title); row.addView(sub);
        row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(row, title, sub);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        CatalogEntry e = items.get(position);
        h.title.setText((e.isFolder() ? "📁 " : "📄 ") + e.name);
        h.sub.setText(e.path + (e.isFolder() ? "" : "   •   " + humanSize(e.size)));
        h.itemView.setOnClickListener(v -> listener.onClick(e));
        h.itemView.setOnLongClickListener(v -> { listener.onLongClick(e); return true; });
    }

    @Override public int getItemCount() { return items.size(); }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0; if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0; if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title, sub;
        Holder(View item, TextView title, TextView sub) { super(item); this.title = title; this.sub = sub; }
    }
}
