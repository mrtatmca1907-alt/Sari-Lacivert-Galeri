package com.atmaca.filemanager;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class StableMainActivity extends AppCompatActivity implements FileAdapter.Listener {
    private enum PendingMode { NONE, COPY, MOVE }
    private static final int BAR = Color.rgb(31,31,31);
    private static final int TEAL = Color.rgb(32,151,145);
    private static final int TEXT = Color.rgb(35,35,35);
    private static final int SUB = Color.rgb(120,120,120);

    private BrowserViewModel viewModel;
    private FileOperations operations;
    private FileAdapter adapter;
    private RecyclerView recycler;
    private TextView titleView, pathView, statusView;
    private EditText searchView;
    private boolean browserVisible;
    private final Set<String> selected = new LinkedHashSet<>();
    private List<FileEntry> latestItems = Collections.emptyList();
    private PendingMode pendingMode = PendingMode.NONE;
    private List<File> pendingSources = Collections.emptyList();

    private final ActivityResultLauncher<Intent> storageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (hasStorageAccess()) showHome();
                else toast("Tüm dosyalara erişim izni gerekli.");
            });

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        operations = new FileOperations();
        viewModel = new ViewModelProvider(this).get(BrowserViewModel.class);
        observeBrowser();
        showHome();
    }

    @Override protected void onDestroy() {
        if (adapter != null) adapter.shutdown();
        if (isFinishing() && operations != null) operations.shutdown();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (!selected.isEmpty()) { clearSelection(); return; }
        if (browserVisible) {
            BrowserViewModel.BrowserState s = viewModel.state().getValue();
            File root = Environment.getExternalStorageDirectory();
            if (s != null && s.currentDir != null && !sameFile(s.currentDir, root)) { viewModel.goUp(); return; }
            showHome();
            return;
        }
        super.onBackPressed();
    }

    private void showHome() {
        browserVisible = false;
        clearSelection();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.addView(appBar("File Manager +", false), new LinearLayout.LayoutParams(-1, dp(56)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(12), dp(8), dp(16));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        File main = Environment.getExternalStorageDirectory();
        addTile(grid, "▰", "Main storage", Color.rgb(120,130,135), () -> openDir(main));
        addTile(grid, "⬇", "Downloads", Color.rgb(219,166,53), () -> openPublic(Environment.DIRECTORY_DOWNLOADS));
        addTile(grid, "▣", "Images", Color.rgb(36,150,145), () -> openPublic(Environment.DIRECTORY_PICTURES));
        addTile(grid, "♫", "Audio", Color.rgb(77,137,156), () -> openPublic(Environment.DIRECTORY_MUSIC));
        addTile(grid, "▤", "Videos", Color.rgb(53,117,181), () -> openPublic(Environment.DIRECTORY_MOVIES));
        addTile(grid, "≡", "Documents", Color.rgb(93,120,145), () -> openPublic(Environment.DIRECTORY_DOCUMENTS));
        addTile(grid, "A", "Apps", Color.rgb(89,150,72), () -> openDir(main));
        addTile(grid, "◷", "New files", Color.rgb(105,113,120), () -> openDir(main));
        addTile(grid, "★", "Favorites", Color.rgb(194,155,45), () -> toast("Favoriler sonraki sürümde."));
        content.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void buildBrowser() {
        browserVisible = true;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.addView(appBar("Main storage", true), new LinearLayout.LayoutParams(-1, dp(56)));

        pathView = new TextView(this);
        pathView.setTextColor(SUB);
        pathView.setTextSize(13);
        pathView.setSingleLine(true);
        pathView.setPadding(dp(12), dp(8), dp(12), dp(8));
        pathView.setBackgroundColor(Color.rgb(247,247,247));
        root.addView(pathView, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setPadding(dp(10), dp(6), dp(8), dp(4));
        searchView = new EditText(this);
        searchView.setSingleLine(true);
        searchView.setTextColor(TEXT);
        searchView.setHintTextColor(Color.rgb(150,150,150));
        searchView.setHint("Search in this folder");
        searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchView.setBackgroundColor(Color.rgb(246,246,246));
        searchView.setPadding(dp(12), 0, dp(12), 0);
        searchView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { viewModel.search(searchView.getText().toString()); return true; }
            return false;
        });
        searchRow.addView(searchView, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView searchButton = topIcon("⌕");
        searchButton.setTextColor(TEAL);
        searchButton.setOnClickListener(v -> viewModel.search(searchView.getText().toString()));
        searchRow.addView(searchButton, new LinearLayout.LayoutParams(dp(48), dp(42)));
        root.addView(searchRow, new LinearLayout.LayoutParams(-1, dp(52)));

        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);
        recycler.setItemViewCacheSize(10);
        adapter = new FileAdapter(this);
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1f));

        statusView = new TextView(this);
        statusView.setTextColor(SUB);
        statusView.setTextSize(12);
        statusView.setPadding(dp(12), dp(4), dp(12), dp(4));
        root.addView(statusView, new LinearLayout.LayoutParams(-1, dp(28)));
        setContentView(root);
    }

    private View appBar(String title, boolean browser) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BAR);
        TextView left = topIcon(browser ? "‹" : "☰");
        left.setOnClickListener(v -> { if (browser) onBackPressed(); });
        bar.addView(left, new LinearLayout.LayoutParams(dp(52), -1));
        titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(19);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView search = topIcon("⌕");
        search.setOnClickListener(v -> { if (browser && searchView != null) searchView.requestFocus(); else openDir(Environment.getExternalStorageDirectory()); });
        bar.addView(search, new LinearLayout.LayoutParams(dp(48), -1));
        TextView more = topIcon("⋮");
        more.setOnClickListener(v -> showMenu(more, browser));
        bar.addView(more, new LinearLayout.LayoutParams(dp(44), -1));
        return bar;
    }

    private void showMenu(View anchor, boolean browser) {
        PopupMenu p = new PopupMenu(this, anchor);
        if (browser) {
            p.getMenu().add("Refresh");
            p.getMenu().add("New folder");
            p.getMenu().add("Select all");
            if (pendingMode != PendingMode.NONE) p.getMenu().add("Paste here");
        } else p.getMenu().add("Permission");
        p.setOnMenuItemClickListener(item -> {
            String s = item.getTitle().toString();
            if ("Refresh".equals(s)) viewModel.refresh();
            else if ("New folder".equals(s)) createFolder();
            else if ("Select all".equals(s)) selectAll();
            else if ("Paste here".equals(s)) pasteHere();
            else requestStorageAccess();
            return true;
        });
        p.show();
    }

    private TextView topIcon(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(25);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private void addTile(GridLayout grid, String symbol, String title, int color, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(4), dp(8), dp(4), dp(10));
        cell.setOnClickListener(v -> action.run());
        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(25);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        icon.setBackground(bg);
        cell.addView(icon, new LinearLayout.LayoutParams(dp(70), dp(68)));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        cell.addView(t, new LinearLayout.LayoutParams(-1, dp(30)));
        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 0;
        gp.height = dp(112);
        gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gp.setMargins(dp(2), dp(2), dp(2), dp(2));
        grid.addView(cell, gp);
    }

    private void observeBrowser() {
        viewModel.state().observe(this, s -> {
            if (!browserVisible) return;
            if (s.currentDir != null) {
                titleView.setText(displayName(s.currentDir));
                pathView.setText(breadcrumb(s.currentDir));
            }
            if (s.loading) { setStatus("Loading…"); return; }
            latestItems = s.items == null ? Collections.emptyList() : s.items;
            adapter.submitList(latestItems);
            selected.retainAll(pathsOf(latestItems));
            if (s.error != null) setStatus(s.error); else setStatus(latestItems.size() + " items");
        });
    }

    private void openDir(File dir) {
        if (!hasStorageAccess()) { requestStorageAccess(); return; }
        if (adapter != null) adapter.shutdown();
        buildBrowser();
        viewModel.open(dir);
    }

    private void openPublic(String type) { openDir(Environment.getExternalStoragePublicDirectory(type)); }

    @Override public void onClick(FileEntry entry) {
        if (!selected.isEmpty()) { toggleSelection(entry); return; }
        if (entry.directory) openDir(entry.toFile()); else openFile(entry.toFile());
    }

    @Override public void onLongClick(FileEntry entry) {
        if (!selected.contains(entry.path)) selected.add(entry.path);
        if (adapter != null) adapter.notifyDataSetChanged();
        final String[] items = {"Open", "Copy", "Move", "Rename", "Share", "Delete"};
        new AlertDialog.Builder(this).setTitle(entry.name).setItems(items, (d, which) -> {
            switch (which) {
                case 0: clearSelection(); openFileOrDir(entry); break;
                case 1: stageSelection(PendingMode.COPY); break;
                case 2: stageSelection(PendingMode.MOVE); break;
                case 3: renameSelected(); break;
                case 4: shareSelected(); break;
                case 5: deleteSelected(); break;
            }
        }).show();
    }

    @Override public boolean isSelected(FileEntry entry) { return selected.contains(entry.path); }

    private void openFileOrDir(FileEntry e) { if (e.directory) openDir(e.toFile()); else openFile(e.toFile()); }

    private void openFile(File file) {
        if (FileTypes.categoryOf(file.getName()) == FileTypes.Category.IMAGE) {
            startActivity(new Intent(this, ImageViewerActivity.class).putExtra(ImageViewerActivity.EXTRA_PATH, file.getAbsolutePath()));
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName()+".files", file);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileTypes.extension(file.getName()));
            if (mime == null) mime = "*/*";
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Throwable t) { toast("Bu dosya açılamadı."); }
    }

    private void toggleSelection(FileEntry e) {
        if (selected.contains(e.path)) selected.remove(e.path); else selected.add(e.path);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void clearSelection() { selected.clear(); if (adapter != null) adapter.notifyDataSetChanged(); }

    private void selectAll() {
        selected.clear();
        for (FileEntry e : latestItems) selected.add(e.path);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void stageSelection(PendingMode mode) {
        List<File> files = selectedFiles();
        if (files.isEmpty()) { toast("Önce dosya seç."); return; }
        pendingMode = mode;
        pendingSources = new ArrayList<>(files);
        clearSelection();
        toast("Hedef klasöre git, sağ üst menüden Paste here seç.");
    }

    private void pasteHere() {
        BrowserViewModel.BrowserState s = viewModel.state().getValue();
        if (pendingMode == PendingMode.NONE || pendingSources.isEmpty() || s == null || s.currentDir == null) return;
        PendingMode mode = pendingMode;
        List<File> src = new ArrayList<>(pendingSources);
        FileOperations.Callback cb = r -> {
            toast(r.successCount + " başarılı, " + r.failureCount + " hata");
            if (r.failureCount == 0) { pendingMode = PendingMode.NONE; pendingSources = Collections.emptyList(); }
            viewModel.refresh();
        };
        if (mode == PendingMode.COPY) operations.copy(src, s.currentDir, cb); else operations.move(src, s.currentDir, cb);
    }

    private void deleteSelected() {
        List<File> files = selectedFiles();
        if (files.isEmpty()) return;
        new AlertDialog.Builder(this).setTitle("Silinsin mi?").setMessage(files.size()+" öğe silinecek.")
                .setNegativeButton("Vazgeç", null).setPositiveButton("Sil", (d,w) -> operations.delete(files, r -> {
                    clearSelection(); viewModel.refresh(); toast(r.successCount+" silindi");
                })).show();
    }

    private void renameSelected() {
        List<File> files = selectedFiles();
        if (files.size() != 1) { toast("Tek öğe seç."); return; }
        EditText input = new EditText(this);
        input.setText(files.get(0).getName()); input.selectAll();
        new AlertDialog.Builder(this).setTitle("Yeniden adlandır").setView(input).setNegativeButton("Vazgeç", null)
                .setPositiveButton("Kaydet", (d,w) -> operations.rename(files.get(0), input.getText().toString(), r -> { clearSelection(); viewModel.refresh(); })).show();
    }

    private void createFolder() {
        BrowserViewModel.BrowserState s = viewModel.state().getValue();
        if (s == null || s.currentDir == null) return;
        EditText input = new EditText(this); input.setHint("Klasör adı");
        new AlertDialog.Builder(this).setTitle("Yeni klasör").setView(input).setNegativeButton("Vazgeç", null)
                .setPositiveButton("Oluştur", (d,w) -> operations.mkdir(s.currentDir, input.getText().toString(), r -> viewModel.refresh())).show();
    }

    private void shareSelected() {
        List<File> files = selectedFiles();
        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : files) if (f.isFile()) try { uris.add(FileProvider.getUriForFile(this, getPackageName()+".files", f)); } catch (Throwable ignored) {}
        if (uris.isEmpty()) return;
        Intent i = uris.size() == 1 ? new Intent(Intent.ACTION_SEND) : new Intent(Intent.ACTION_SEND_MULTIPLE);
        i.setType("*/*"); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (uris.size() == 1) i.putExtra(Intent.EXTRA_STREAM, uris.get(0)); else i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        startActivity(Intent.createChooser(i, "Paylaş"));
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { storageLauncher.launch(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:"+getPackageName()))); }
            catch (Throwable t) { storageLauncher.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
        }
    }

    private boolean hasStorageAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    private List<File> selectedFiles() {
        List<File> out = new ArrayList<>();
        for (String p : selected) out.add(new File(p));
        return out;
    }

    private static Set<String> pathsOf(List<FileEntry> items) {
        Set<String> out = new LinkedHashSet<>();
        for (FileEntry e : items) out.add(e.path);
        return out;
    }

    private void setStatus(String t) { if (statusView != null) statusView.setText(t == null ? "" : t); }
    private void toast(String t) { Toast.makeText(this, t, Toast.LENGTH_SHORT).show(); }

    private String displayName(File dir) {
        if (sameFile(dir, Environment.getExternalStorageDirectory())) return "Main storage";
        String n = dir.getName(); return n == null || n.isEmpty() ? "Main storage" : n;
    }

    private String breadcrumb(File dir) {
        File root = Environment.getExternalStorageDirectory();
        if (sameFile(dir, root)) return "⌂  >  Main storage";
        String p = dir.getAbsolutePath().replace(root.getAbsolutePath(), "Main storage");
        return "⌂  >  " + p.replace(File.separator, "  >  ");
    }

    private static boolean sameFile(File a, File b) {
        try { return a.getCanonicalPath().equals(b.getCanonicalPath()); }
        catch (Throwable t) { return a.getAbsolutePath().equals(b.getAbsolutePath()); }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
