package com.atmaca.filemanager;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FileAdapter extends RecyclerView.Adapter<FileAdapter.Holder> {
    public interface Listener {
        void onClick(FileEntry entry);
        void onLongClick(FileEntry entry);
        boolean isSelected(FileEntry entry);
    }

    private List<FileEntry> items = Collections.emptyList();
    private final Listener listener;

    public FileAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<FileEntry> list) {
        items = list == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(list));
        notifyDataSetChanged();
    }

    public List<FileEntry> currentItems() { return items; }

    @Override public long getItemId(int position) {
        String p = items.get(position).path;
        long h1 = p.hashCode() & 0xffffffffL;
        long h2 = (long) p.length() << 32;
        return h1 ^ h2;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(row, 12), dp(row, 9), dp(row, 12), dp(row, 9));
        row.setMinimumHeight(dp(row, 64));

        TextView icon = new TextView(parent.getContext());
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(18);
        icon.setTextColor(Color.rgb(7, 26, 58));
        icon.setBackgroundColor(Color.rgb(245, 196, 0));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(row, 44), dp(row, 44));
        ip.setMarginEnd(dp(row, 12));
        row.addView(icon, ip);

        LinearLayout texts = new LinearLayout(parent.getContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setSingleLine(true);
        TextView sub = new TextView(parent.getContext());
        sub.setTextColor(Color.rgb(184, 196, 217));
        sub.setTextSize(12);
        sub.setSingleLine(true);
        texts.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        texts.addView(sub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return new Holder(row, icon, title, sub);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        FileEntry e = items.get(position);
        h.title.setText(e.name);
        h.icon.setText(symbol(e));
        h.sub.setText(e.directory ? "Klasör" : FileTypes.categoryOf(e.name).name() + "  •  " + humanSize(e.size));
        boolean selected = listener != null && listener.isSelected(e);
        h.itemView.setBackgroundColor(selected ? Color.rgb(42, 71, 119) : Color.rgb(7, 26, 58));
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onClick(e); });
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onLongClick(e);
            return true;
        });
    }

    @Override public int getItemCount() { return items.size(); }

    private static String symbol(FileEntry e) {
        if (e.directory) return "D";
        switch (FileTypes.categoryOf(e.name)) {
            case IMAGE: return "IMG";
            case VIDEO: return "VID";
            case DOCUMENT: return "DOC";
            case APK: return "APK";
            case ARCHIVE: return "ZIP";
            default: return "F";
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.ROOT, "%.1f MB", mb);
        return String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private static int dp(View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView icon, title, sub;
        Holder(@NonNull View itemView, TextView icon, TextView title, TextView sub) {
            super(itemView);
            this.icon = icon;
            this.title = title;
            this.sub = sub;
        }
    }
}
