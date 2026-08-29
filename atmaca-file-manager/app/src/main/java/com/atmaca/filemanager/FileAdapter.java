package com.atmaca.filemanager;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class FileAdapter extends RecyclerView.Adapter<FileAdapter.Holder> {
    public interface Listener {
        void onClick(FileEntry entry);
        void onLongClick(FileEntry entry);
        boolean isSelected(FileEntry entry);
    }

    private List<FileEntry> items = Collections.emptyList();
    private final Listener listener;
    private final ThumbnailLoader thumbnails = new ThumbnailLoader();

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
        return (p.hashCode() & 0xffffffffL) ^ ((long) p.length() << 32);
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(row, 12), dp(row, 5), dp(row, 8), dp(row, 5));
        row.setMinimumHeight(dp(row, 58));

        FrameLayout iconBox = new FrameLayout(parent.getContext());
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(row, 48), dp(row, 48));
        ip.setMarginEnd(dp(row, 12));
        row.addView(iconBox, ip);

        ImageView preview = new ImageView(parent.getContext());
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iconBox.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        TextView icon = new TextView(parent.getContext());
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(11);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        iconBox.addView(icon, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout texts = new LinearLayout(parent.getContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.rgb(35,35,35));
        title.setTextSize(16);
        title.setSingleLine(true);
        TextView sub = new TextView(parent.getContext());
        sub.setTextColor(Color.rgb(120,120,120));
        sub.setTextSize(12);
        sub.setSingleLine(true);
        texts.addView(title, new LinearLayout.LayoutParams(-1, -2));
        texts.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView more = new TextView(parent.getContext());
        more.setText("⋮");
        more.setTextColor(Color.rgb(90,90,90));
        more.setTextSize(24);
        more.setGravity(Gravity.CENTER);
        row.addView(more, new LinearLayout.LayoutParams(dp(row, 34), dp(row, 44)));
        return new Holder(row, preview, icon, title, sub, more);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        FileEntry e = items.get(position);
        h.title.setText(e.name);
        h.sub.setText(e.directory ? "Klasör" : typeLabel(e) + "  •  " + humanSize(e.size));

        boolean isImage = !e.directory && FileTypes.categoryOf(e.name) == FileTypes.Category.IMAGE;
        h.preview.setImageDrawable(null);
        h.preview.setVisibility(isImage ? View.VISIBLE : View.GONE);
        h.icon.setVisibility(isImage ? View.GONE : View.VISIBLE);
        if (isImage) {
            h.preview.setBackgroundColor(Color.rgb(238,238,238));
            thumbnails.load(e.toFile(), h.preview, dp(h.itemView, 96));
        } else {
            h.icon.setText(symbol(e));
            h.icon.setTextColor(Color.WHITE);
            h.icon.setBackground(iconBackground(e));
        }

        boolean selected = listener != null && listener.isSelected(e);
        h.itemView.setBackgroundColor(selected ? Color.rgb(225,240,238) : Color.WHITE);
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onClick(e); });
        h.itemView.setOnLongClickListener(v -> { if (listener != null) listener.onLongClick(e); return true; });
        h.more.setOnClickListener(v -> { if (listener != null) listener.onLongClick(e); });
    }

    @Override public void onViewRecycled(@NonNull Holder holder) {
        holder.preview.setTag(null);
        holder.preview.setImageDrawable(null);
        super.onViewRecycled(holder);
    }

    @Override public int getItemCount() { return items.size(); }

    public void shutdown() { thumbnails.shutdown(); }

    private static GradientDrawable iconBackground(FileEntry e) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(8f);
        if (e.directory) g.setColor(Color.rgb(215,165,48));
        else {
            switch (FileTypes.categoryOf(e.name)) {
                case VIDEO: g.setColor(Color.rgb(53,117,181)); break;
                case DOCUMENT: g.setColor(Color.rgb(93,120,145)); break;
                case APK: g.setColor(Color.rgb(89,150,72)); break;
                case ARCHIVE: g.setColor(Color.rgb(105,113,120)); break;
                default: g.setColor(Color.rgb(36,150,145)); break;
            }
        }
        return g;
    }

    private static String symbol(FileEntry e) {
        if (e.directory) return "DIR";
        switch (FileTypes.categoryOf(e.name)) {
            case VIDEO: return "VID";
            case DOCUMENT: return "DOC";
            case APK: return "APK";
            case ARCHIVE: return "ZIP";
            default: return "FILE";
        }
    }

    private static String typeLabel(FileEntry e) {
        switch (FileTypes.categoryOf(e.name)) {
            case IMAGE: return "Görsel";
            case VIDEO: return "Video";
            case DOCUMENT: return "Belge";
            case APK: return "Uygulama";
            case ARCHIVE: return "Arşiv";
            default: return "Dosya";
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private static int dp(View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView preview;
        final TextView icon, title, sub, more;
        Holder(@NonNull View itemView, ImageView preview, TextView icon, TextView title, TextView sub, TextView more) {
            super(itemView);
            this.preview = preview;
            this.icon = icon;
            this.title = title;
            this.sub = sub;
            this.more = more;
        }
    }
}
