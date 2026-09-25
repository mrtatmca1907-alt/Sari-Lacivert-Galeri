package com.atmaca.hiosfilemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

public class HomeActivity extends Activity {
    private static final int NAVY = Color.rgb(7, 27, 58);
    private static final int BLUE = Color.rgb(14, 58, 120);
    private static final int YELLOW = Color.rgb(245, 196, 0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(249, 250, 252));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(10), dp(14), dp(10));
        top.setBackgroundColor(NAVY);

        TextView menu = topIcon("☰");
        menu.setOnClickListener(v -> showSideInfo());
        top.addView(menu, new LinearLayout.LayoutParams(dp(58), dp(50)));

        TextView title = new TextView(this);
        title.setText("ATMACA HiOS Dosyalar");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(null, 1);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1f));

        TextView more = topIcon("⋮");
        more.setOnClickListener(v -> showTopMenu(more));
        top.addView(more, new LinearLayout.LayoutParams(dp(50), dp(50)));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(dp(10), dp(20), dp(10), dp(30));
        scroll.addView(grid);

        addTile(grid, "▣", "Ana bellek", storageSubtitle(), () -> openPath("/storage/emulated/0"));
        addTile(grid, "⇩", "İndirilenler", "", () -> openPath("/storage/emulated/0/Download"));
        addTile(grid, "◔", "Bellek Analizi", usedPercent(), this::showStorageInfo);

        addTile(grid, "▧", "Görüntüler", "", () -> openPath("/storage/emulated/0/Pictures"));
        addTile(grid, "♪", "Ses", "", () -> openPath("/storage/emulated/0/Music"));
        addTile(grid, "▶", "Videolar", "", () -> openPath("/storage/emulated/0/Movies"));

        addTile(grid, "▤", "Belgeler", "", () -> openPath("/storage/emulated/0/Documents"));
        addTile(grid, "◆", "Uygulamalar", "", this::openAppsSettings);
        addTile(grid, "◷", "Yeni Dosyalar", "", () -> openPath("/storage/emulated/0/Download"));

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        setContentView(root);
    }

    private void addTile(GridLayout grid, String symbol, String label, String subtitle, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(6), dp(8), dp(6), dp(14));
        tile.setOnClickListener(v -> action.run());

        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(40);
        icon.setTextColor(BLUE);
        icon.setBackgroundColor(Color.WHITE);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(Color.rgb(20,20,20));
        name.setTextSize(17);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        tile.addView(name);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextColor(Color.GRAY);
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        tile.addView(sub);

        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = 0;
        p.height = dp(190);
        p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        grid.addView(tile, p);
    }

    private TextView topIcon(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(28);
        v.setTextColor(YELLOW);
        return v;
    }

    private void openPath(String path) {
        File f = new File(path);
        if (!f.exists() || !f.isDirectory()) f = new File("/storage/emulated/0");
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("startPath", f.getAbsolutePath());
        startActivity(i);
    }

    private String storageSubtitle() {
        File root = new File("/storage/emulated/0");
        return formatBytes(root.getUsableSpace()) + " boş";
    }

    private String usedPercent() {
        File root = new File("/storage/emulated/0");
        long total = root.getTotalSpace();
        long used = Math.max(0, total - root.getUsableSpace());
        int pct = total <= 0 ? 0 : (int)Math.round(used * 100.0 / total);
        return "Kullanılan %" + pct;
    }

    private void showStorageInfo() {
        File root = new File("/storage/emulated/0");
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        long used = Math.max(0, total - free);
        new AlertDialog.Builder(this)
                .setTitle("Bellek Analizi")
                .setMessage("Toplam: " + formatBytes(total) + "\nKullanılan: " + formatBytes(used) + "\nBoş: " + formatBytes(free))
                .setPositiveButton("Tamam", null)
                .show();
    }

    private void showSideInfo() {
        new AlertDialog.Builder(this)
                .setTitle("ATMACA HiOS Dosyalar")
                .setItems(new String[]{"Ana bellek", "İndirilenler", "Görüntüler", "Videolar"}, (d, which) -> {
                    if (which == 0) openPath("/storage/emulated/0");
                    else if (which == 1) openPath("/storage/emulated/0/Download");
                    else if (which == 2) openPath("/storage/emulated/0/Pictures");
                    else openPath("/storage/emulated/0/Movies");
                }).show();
    }

    private void showTopMenu(View anchor) {
        android.widget.PopupMenu p = new android.widget.PopupMenu(this, anchor);
        p.getMenu().add("Bellek bilgisi");
        p.getMenu().add("Ayarlar");
        p.setOnMenuItemClickListener(item -> {
            if ("Bellek bilgisi".contentEquals(item.getTitle())) showStorageInfo();
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
            return true;
        });
        p.show();
    }

    private void openAppsSettings() {
        startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / (1024L * 1024)) + " MB";
        return String.format(Locale.US, "%.1f GB", bytes / (1024d * 1024d * 1024d));
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
