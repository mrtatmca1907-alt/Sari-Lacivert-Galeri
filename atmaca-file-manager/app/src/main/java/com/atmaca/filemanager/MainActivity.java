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
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
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

public final class MainActivity extends AppCompatActivity implements FileAdapter.Listener {
    private enum PendingMode { NONE, COPY, MOVE }

    private static final int BAR = Color.rgb(35, 35, 35);
    private static final int TEAL = Color.rgb(31, 162, 151);
    private static final int TEXT = Color.rgb(34, 34, 34);
    private static final int SUB = Color.rgb(120, 120, 120);

    private BrowserViewModel viewModel;
    private FileOperations operations;
    private FileAdapter adapter;
    private RecyclerView recycler;
    private TextView titleView;
    private TextView pathView;
    private TextView statusView;
    private LinearLayout browserRoot;
    private EditText searchView;

    private final Set<String> selected = new LinkedHashSet<>();
    private List<FileEntry> latestItems = Collections.emptyList();
    private PendingMode pendingMode = PendingMode.NONE;
    private List<File> pendingSources = Collections.emptyList();
    private boolean browserVisible;

    private final ActivityResultLauncher<Intent> manageStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (hasStorageAccess()) showHome();
                else toast("Tüm dosyalara erişim izni gerekli.");
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        operations = new FileOperations();
        viewModel = new ViewModelProvider(this).get(BrowserViewModel.class);
        observeBrowser();
        showHome();
    }

    @Override protected void onDestroy() {
        if (isFinishing() && operations != null) operations.shutdown();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (!selected.isEmpty()) { clearSelection(); return; }
        if (browserVisible) {
            BrowserViewModel.BrowserState s = viewModel.state().getValue();
            File root = Environment.getExternalStorageDirectory();
            if (s != null && s.currentDir != null && !sameFile(s.currentDir, root)) {
                viewModel.goUp();
                return;
            }
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
        content.setPadding(dp(8), dp(14), dp(8), dp(20));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setUseDefaultMargins(false);

        File main = Environment.getExternalStorageDirectory();
        String storageSub = storageText(main);
        addTile(grid, "▰", "Main storage", storageSub, Color.rgb(150,155,158), () -> openDir(main));
        addTile(grid, "⬇", "Downloads", dirSummary(Environment.DIRECTORY_DOWNLOADS), Color.rgb(229,165,65), () -> openPublic(Environment.DIRECTORY_DOWNLOADS));
        addTile(grid, "▣", "Images", dirSummary(Environment.DIRECTORY_PICTURES), Color.rgb(107,79,124), () -> openPublic(Environment.DIRECTORY_PICTURES));
        addTile(grid, "♫", "Audio", dirSummary(Environment.DIRECTORY_MUSIC), Color.rgb(48,135,135), () -> openPublic(Environment.DIRECTORY_MUSIC));
        addTile(grid, "▤", "Videos", dirSummary(Environment.DIRECTORY_MOVIES), Color.rgb(160,44,48), () -> openPublic(Environment.DIRECTORY_MOVIES));
        addTile(grid, "≡", "Documents", dirSummary(Environment.DIRECTORY_DOCUMENTS), Color.rgb(55,103,160), () -> openPublic(Environment.DIRECTORY_DOCUMENTS));
        addTile(grid, "A", "Apps", "APK files", Color.rgb(139,188,58), () -> openFiltered(main, FileTypes.Category.APK));
        addTile(grid, "◷", "New files", "Recently changed", Color.rgb(100,118,118), () -> openDir(main));
        addTile(grid, "★", "Favorites", "Quick access", Color.rgb(198,157,49), () -> toast("Favoriler sonraki sürümde."));
        content.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        sectionTitle(content, "Network & cloud");
        GridLayout network = new GridLayout(this);
        network.setColumnCount(3);
        addTile(network, "☁", "Cloud", "Drive / Dropbox", Color.rgb(64,115,164), () -> toast("Bulut modülü kararlılık için ayrı tutuldu."));
        addTile(network, "⌁", "Remote", "FTP / SFTP / SMB", Color.rgb(64,145,120), () -> toast("Ağ modülü kararlılık için ayrı tutuldu."));
        addTile(network, "PC", "Access from PC", "Local network", Color.rgb(118,118,118), () -> toast("PC erişimi sonraki sürümde."));
        content.addView(network, new LinearLayout.LayoutParams(-1, -2));

        sectionTitle(content, "Tools");
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.addView(toolRow("Storage analysis", "Depolama kullanımını görüntüle", () -> showStorageDialog()));
        tools.addView(toolRow("Recycle bin", "Silinen dosyalar için güvenli alan", () -> toast("Çöp kutusu sonraki sürümde.")));
        tools.addView(toolRow("Storage permission", hasStorageAccess() ? "Tüm dosyalara erişim açık" : "İzin gerekli", this::requestStorageAccess));
        content.addView(tools, new LinearLayout.LayoutParams(-1, -2));

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void openDir(File dir) {
        if (!hasStorageAccess()) { requestStorageAccess(); return; }
        buildBrowser();
        viewModel.open(dir);
    }

    private void openFiltered(File dir, FileTypes.Category category) {
        openDir(dir);
        recycler.postDelayed(() -> showCategory(category), 250);
    }

    private void openPublic(String type) {
        openDir(Environment.getExternalStoragePublicDirectory(type));
    }

    private void buildBrowser() {
        browserVisible = true;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        browserRoot = root;
        root.addView(appBar("Main storage", true), new LinearLayout.LayoutParams(-1, dp(56)));

        pathView = new TextView(this);
        pathView.setTextColor(SUB);
        pathView.setTextSize(13);
        pathView.setSingleLine(true);
        pathView.setPadding(dp(12), dp(9), dp(12), dp(9));
        pathView.setBackgroundColor(Color.rgb(245,245,245));
        root.addView(pathView, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setPadding(dp(8), dp(5), dp(8), dp(5));
        searchView = new EditText(this);
        searchView.setSingleLine(true);
        searchView.setTextColor(TEXT);
        searchView.setHintTextColor(Color.rgb(150,150,150));
        searchView.setHint("Search in this folder");
        searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchView.setBackgroundColor(Color.rgb(244,244,244));
        searchView.setPadding(dp(12), 0, dp(12), 0);
        searchView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { viewModel.search(searchView.getText().toString()); return true; }
            return false;
        });
        searchRow.addView(searchView, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView searchBtn = topIcon("⌕");
        searchBtn.setTextColor(TEAL);
        searchBtn.setOnClickListener(v -> viewModel.search(searchView.getText().toString()));
        searchRow.addView(searchBtn, new LinearLayout.LayoutParams(dp(48), dp(42)));
        root.addView(searchRow, new LinearLayout.LayoutParams(-1, dp(52)));

        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);
        recycler.setItemViewCacheSize(12);
        adapter = new FileAdapter(this);
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1f));

        statusView = new TextView(this);
        statusView.setTextColor(SUB);
        statusView.setTextSize(12);
        statusView.setPadding(dp(12), dp(5), dp(12), dp(5));
        root.addView(statusView, new LinearLayout.LayoutParams(-1, dp(30)));
        setContentView(root);
    }

    private View appBar(String title, boolean browser) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BAR);
        bar.setPadding(dp(4), 0, dp(4), 0);

        TextView left = topIcon(browser ? "‹" : "☰");
        left.setOnClickListener(v -> { if (browser) onBackPressed(); else toast("File Manager + style home"); });
        bar.addView(left, new LinearLayout.LayoutParams(dp(52), -1));

        titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(19);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView search = topIcon("⌕");
        search.setOnClickListener(v -> {
            if (browser && searchView != null) searchView.requestFocus();
            else openDir(Environment.getExternalStorageDirectory());
        });
        bar.addView(search, new LinearLayout.LayoutParams(dp(48), -1));

        TextView more = topIcon("⋮");
        more.setOnClickListener(v -> showTopMenu(more, browser));
        bar.addView(more, new LinearLayout.LayoutParams(dp(44), -1));
        return bar;
    }

    private TextView topIcon(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(25);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private void showTopMenu(View anchor, boolean browser) {
        PopupMenu p = new PopupMenu(this, anchor);
        if (browser) {
            p.getMenu().add("Refresh");
            p.getMenu().add("New folder");
            p.getMenu().add("Select all");
            p.getMenu().add("Images");
            p.getMenu().add("Videos");
            p.getMenu().add("Documents");
        } else {
            p.getMenu().add("Storage analysis");
            p.getMenu().add("Settings");
            p.getMenu().add("Permission");
        }
        p.setOnMenuItemClickListener(item -> {
            String s = item.getTitle().toString();
            if ("Refresh".equals(s)) viewModel.refresh();
            else if ("New folder".equals(s)) createFolder();
            else if ("Select all".equals(s)) selectAll();
            else if ("Images".equals(s)) showCategory(FileTypes.Category.IMAGE);
            else if ("Videos".equals(s)) showCategory(FileTypes.Category.VIDEO);
            else if ("Documents".equals(s)) showCategory(FileTypes.Category.DOCUMENT);
            else if ("Storage analysis".equals(s)) showStorageDialog();
            else if ("Permission".equals(s)) requestStorageAccess();
            else toast("Ayarlar sonraki sürümde genişletilecek.");
            return true;
        });
        p.show();
    }

    private void addTile(GridLayout grid, String iconText, String title, String sub, int color, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(4), dp(8), dp(4), dp(10));
        cell.setOnClickListener(v -> action.run());

        TextView icon = new TextView(this);
        icon.setText(iconText);
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(iconText.length() > 2 ? 14 : 26);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        icon.setBackground(bg);
        cell.addView(icon, new LinearLayout.LayoutParams(dp(70), dp(68)));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        cell.addView(t, new LinearLayout.LayoutParams(-1, dp(28)));

        TextView st = new TextView(this);
        st.setText(sub);
        st.setTextColor(SUB);
        st.setTextSize(12);
        st.setGravity(Gravity.CENTER);
        st.setSingleLine(true);
        cell.addView(st, new LinearLayout.LayoutParams(-1, dp(20)));

        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 0;
        gp.height = dp(132);
        gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gp.setMargins(dp(2), dp(2), dp(2), dp(2));
        grid.addView(cell, gp);
    }

    private void sectionTitle(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(TEAL);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(10), dp(16), dp(10), dp(8));
        parent.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private View toolRow(String title, String sub, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackgroundColor(Color.rgb(248,248,248));
        TextView a = new TextView(this); a.setText(title); a.setTextColor(TEXT); a.setTextSize(16);
        TextView b = new TextView(this); b.setText(sub); b.setTextColor(SUB); b.setTextSize(12);
        row.addView(a); row.addView(b);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void observeBrowser() {
        viewModel.state().observe(this, s -> {
            if (!browserVisible) return;
            File dir = s.currentDir;
            if (dir != null) {
                titleView.setText(displayName(dir));
                pathView.setText(breadcrumb(dir));
            }
            if (s.loading) { setStatus("Loading…"); return; }
            latestItems = s.items == null ? Collections.emptyList() : s.items;
            adapter.submitList(latestItems);
            selected.retainAll(pathsOf(latestItems));
            if (s.error != null) setStatus(s.error);
            else setStatus(latestItems.size() + " items" + (selected.isEmpty() ? "" : " • selected " + selected.size()));
        });
    }

    @Override public void onClick(FileEntry entry) {
        if (!selected.isEmpty()) { toggleSelection(entry); return; }
        if (entry.directory) openDir(entry.toFile()); else openFile(entry.toFile());
    }

    @Override public void onLongClick(FileEntry entry) {
        toggleSelection(entry);
        if (selected.size() == 1) showSelectionMenu(entry);
    }

    @Override public boolean isSelected(FileEntry entry) { return selected.contains(entry.path); }

    private void showSelectionMenu(FileEntry entry) {
        final String[] items = {"Open", "Copy", "Move", "Rename", "Share", "Delete"};
        new AlertDialog.Builder(this).setTitle(entry.name).setItems(items, (d, which) -> {
            switch (which) {
                case 0: clearSelection(); onClick(entry); break;
                case 1: stageSelection(PendingMode.COPY); break;
                case 2: stageSelection(PendingMode.MOVE); break;
                case 3: renameSelected(); break;
                case 4: shareSelected(); break;
                case 5: deleteSelected(); break;
            }
        }).show();
    }

    private void toggleSelection(FileEntry entry) {
        if (selected.contains(entry.path)) selected.remove(entry.path); else selected.add(entry.path);
        if (adapter != null) adapter.notifyDataSetChanged();
        setStatus(selected.size() + " selected");
    }

    private void selectAll() {
        selected.clear();
        for (FileEntry e : latestItems) selected.add(e.path);
        if (adapter != null) adapter.notifyDataSetChanged();
        setStatus(selected.size() + " selected");
    }

    private void clearSelection() {
        selected.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showCategory(FileTypes.Category category) {
        if (adapter == null) return;
        List<FileEntry> filtered = new ArrayList<>();
        for (FileEntry e : latestItems) if (e.directory || FileTypes.categoryOf(e.name) == category) filtered.add(e);
        adapter.submitList(filtered);
        setStatus(filtered.size() + " items");
    }

    private void stageSelection(PendingMode mode) {
        List<File> files = selectedFiles();
        if (files.isEmpty()) { toast("Select a file first."); return; }
        pendingMode = mode;
        pendingSources = new ArrayList<>(files);
        clearSelection();
        toast((mode == PendingMode.COPY ? "Copy" : "Move") + ": go to destination, then use menu > Paste here");
        if (titleView != null) titleView.setText((mode == PendingMode.COPY ? "Copy" : "Move") + " • " + files.size());
    }

    private void pasteHere() {
        BrowserViewModel.BrowserState s = viewModel.state().getValue();
        if (pendingMode == PendingMode.NONE || pendingSources.isEmpty() || s == null || s.currentDir == null) return;
        PendingMode mode = pendingMode;
        List<File> src = new ArrayList<>(pendingSources);
        FileOperations.Callback cb = r -> {
            toast(r.successCount + " successful, " + r.failureCount + " failed");
            if (r.failureCount == 0) { pendingMode = PendingMode.NONE; pendingSources = Collections.emptyList(); }
            viewModel.refresh();
        };
        if (mode == PendingMode.COPY) operations.copy(src, s.currentDir, cb); else operations.move(src, s.currentDir, cb);
    }

    private void deleteSelected() {
        List<File> files = selectedFiles();
        if (files.isEmpty()) return;
        new AlertDialog.Builder(this).setTitle("Delete?").setMessage(files.size() + " item(s) will be deleted.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d,w) -> operations.delete(files, r -> {
                    clearSelection(); viewModel.refresh(); toast(r.successCount + " deleted");
                })).show();
    }

    private void renameSelected() {
        List<File> files = selectedFiles();
        if (files.size() != 1) { toast("Select one item."); return; }
        EditText input = new EditText(this); input.setText(files.get(0).getName()); input.selectAll();
        new AlertDialog.Builder(this).setTitle("Rename").setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d,w) -> operations.rename(files.get(0), input.getText().toString(), r -> { clearSelection(); viewModel.refresh(); })).show();
    }

    private void createFolder() {
        BrowserViewModel.BrowserState s = viewModel.state().getValue();
        if (s == null || s.currentDir == null) return;
        EditText input = new EditText(this); input.setHint("Folder name");
        new AlertDialog.Builder(this).setTitle("New folder").setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (d,w) -> operations.mkdir(s.currentDir, input.getText().toString(), r -> viewModel.refresh())).show();
    }

    private void shareSelected() {
        List<File> files = selectedFiles();
        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : files) if (f.isFile()) try { uris.add(FileProvider.getUriForFile(this, getPackageName()+".files", f)); } catch (Throwable ignored) {}
        if (uris.isEmpty()) return;
        Intent i = uris.size() == 1 ? new Intent(Intent.ACTION_SEND) : new Intent(Intent.ACTION_SEND_MULTIPLE);
        i.setType("*/*"); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (uris.size() == 1) i.putExtra(Intent.EXTRA_STREAM, uris.get(0)); else i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        startActivity(Intent.createChooser(i, "Share"));
    }

    private void openFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName()+".files", file);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileTypes.extension(file.getName()));
            if (mime == null) mime = "*/*";
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Throwable t) { toast("No app can open this file."); }
    }

    private void showStorageDialog() {
        File f = Environment.getExternalStorageDirectory();
        StatFs s = new StatFs(f.getAbsolutePath());
        long total = s.getTotalBytes(), free = s.getAvailableBytes(), used = total - free;
        new AlertDialog.Builder(this).setTitle("Storage analysis")
                .setMessage("Main storage\nUsed: " + human(used) + "\nFree: " + human(free) + "\nTotal: " + human(total))
                .setPositiveButton("OK", null).show();
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { manageStorageLauncher.launch(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:"+getPackageName()))); }
            catch (Throwable t) { manageStorageLauncher.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
        }
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return true;
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

    private void setStatus(String text) { if (statusView != null) statusView.setText(text == null ? "" : text); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

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

    private String dirSummary(String type) {
        File d = Environment.getExternalStoragePublicDirectory(type);
        File[] a = null; try { a = d.listFiles(); } catch (Throwable ignored) {}
        return a == null ? "" : a.length + " items";
    }

    private String storageText(File f) {
        try {
            StatFs s = new StatFs(f.getAbsolutePath());
            long total=s.getTotalBytes(), free=s.getAvailableBytes();
            return human(total-free) + " / " + human(total);
        } catch (Throwable t) { return "Storage"; }
    }

    private static boolean sameFile(File a, File b) {
        try { return a.getCanonicalPath().equals(b.getCanonicalPath()); }
        catch (Throwable t) { return a.getAbsolutePath().equals(b.getAbsolutePath()); }
    }

    private static String human(long bytes) {
        double gb = bytes / 1073741824.0;
        if (gb >= 1) return String.format(Locale.ROOT, "%.1f GB", gb);
        return String.format(Locale.ROOT, "%.0f MB", bytes / 1048576.0);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
