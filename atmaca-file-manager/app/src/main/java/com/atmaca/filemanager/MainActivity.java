package com.atmaca.filemanager;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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

    private BrowserViewModel viewModel;
    private FileOperations operations;
    private FileAdapter adapter;
    private RecyclerView recycler;
    private TextView pathView;
    private TextView storageView;
    private TextView selectionView;
    private TextView statusView;
    private EditText searchView;

    private final Set<String> selected = new LinkedHashSet<>();
    private List<FileEntry> latestItems = Collections.emptyList();
    private PendingMode pendingMode = PendingMode.NONE;
    private List<File> pendingSources = Collections.emptyList();

    private final ActivityResultLauncher<Intent> manageStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (hasStorageAccess()) openInitialIfNeeded();
                else setStatus("Tüm dosyalara erişim izni gerekli.");
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        operations = new FileOperations();
        viewModel = new ViewModelProvider(this).get(BrowserViewModel.class);
        buildUi();
        observeBrowser();
        if (hasStorageAccess()) openInitialIfNeeded();
        else setStatus("Önce İZİN düğmesine bas ve tüm dosyalara erişimi aç.");
    }

    @Override protected void onResume() {
        super.onResume();
        if (hasStorageAccess()) openInitialIfNeeded();
        updateStorageSummary();
    }

    @Override protected void onDestroy() {
        if (isFinishing() && operations != null) operations.shutdown();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7, 26, 58));
        root.setPadding(dp(8), dp(8), dp(8), dp(6));

        TextView title = new TextView(this);
        title.setText("ATMACA DOSYA YÖNETİCİSİ");
        title.setTextColor(Color.rgb(245, 196, 0));
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(6), dp(5), dp(6), dp(3));
        root.addView(title, lpMatchWrap());

        storageView = smallText();
        root.addView(storageView, lpMatchWrap());

        pathView = new TextView(this);
        pathView.setTextColor(Color.WHITE);
        pathView.setTextSize(13);
        pathView.setSingleLine(true);
        pathView.setPadding(dp(6), dp(5), dp(6), dp(5));
        root.addView(pathView, lpMatchWrap());

        root.addView(horizontalBar(
                button("İZİN", v -> requestStorageAccess()),
                button("YUKARI", v -> viewModel.goUp()),
                button("DAHİLİ", v -> openDir(Environment.getExternalStorageDirectory())),
                button("İNDİR", v -> openPublic(Environment.DIRECTORY_DOWNLOADS)),
                button("RESİMLER", v -> openPublic(Environment.DIRECTORY_PICTURES)),
                button("VİDEOLAR", v -> openPublic(Environment.DIRECTORY_MOVIES)),
                button("YENİLE", v -> viewModel.refresh())
        ), lpMatchWrap());

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchView = new EditText(this);
        searchView.setSingleLine(true);
        searchView.setTextColor(Color.WHITE);
        searchView.setHintTextColor(Color.rgb(184, 196, 217));
        searchView.setHint("Bu klasörde ara");
        searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(searchView.getText().toString());
                return true;
            }
            return false;
        });
        searchRow.addView(searchView, new LinearLayout.LayoutParams(0, dp(48), 1f));
        searchRow.addView(button("ARA", v -> viewModel.search(searchView.getText().toString())), new LinearLayout.LayoutParams(dp(70), dp(44)));
        searchRow.addView(button("TEMİZ", v -> { searchView.setText(""); viewModel.search(""); }), new LinearLayout.LayoutParams(dp(76), dp(44)));
        root.addView(searchRow, lpMatchWrap());

        root.addView(horizontalBar(
                button("GÖRSEL", v -> showCategory(FileTypes.Category.IMAGE)),
                button("VİDEO", v -> showCategory(FileTypes.Category.VIDEO)),
                button("BELGE", v -> showCategory(FileTypes.Category.DOCUMENT)),
                button("APK", v -> showCategory(FileTypes.Category.APK)),
                button("ARŞİV", v -> showCategory(FileTypes.Category.ARCHIVE)),
                button("HEPSİ", v -> adapter.submitList(latestItems))
        ), lpMatchWrap());

        selectionView = smallText();
        selectionView.setText("Seçim: 0");
        root.addView(selectionView, lpMatchWrap());

        root.addView(horizontalBar(
                button("KOPYALA", v -> stageSelection(PendingMode.COPY)),
                button("TAŞI", v -> stageSelection(PendingMode.MOVE)),
                button("BURAYA", v -> pasteHere()),
                button("AD", v -> renameSelected()),
                button("YENİ KLASÖR", v -> createFolder()),
                button("PAYLAŞ", v -> shareSelected()),
                button("SİL", v -> deleteSelected())
        ), lpMatchWrap());

        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);
        recycler.setItemViewCacheSize(16);
        adapter = new FileAdapter(this);
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        statusView = smallText();
        statusView.setTextColor(Color.rgb(245, 196, 0));
        root.addView(statusView, lpMatchWrap());
        setContentView(root);
        updateStorageSummary();
    }

    private void observeBrowser() {
        viewModel.state().observe(this, s -> {
            File dir = s.currentDir;
            pathView.setText(dir == null ? "" : dir.getAbsolutePath());
            if (s.loading) {
                setStatus("Klasör okunuyor…");
                return;
            }
            latestItems = s.items == null ? Collections.emptyList() : s.items;
            adapter.submitList(latestItems);
            selected.retainAll(pathsOf(latestItems));
            updateSelectionUi();
            if (s.error != null) setStatus(s.error);
            else setStatus(latestItems.size() + " öğe" + (s.filter == null || s.filter.isEmpty() ? "" : " • filtre: " + s.filter));
        });
    }

    private void openInitialIfNeeded() {
        BrowserViewModel.BrowserState s = viewModel.state().getValue();
        if (s == null || s.currentDir == null) openDir(Environment.getExternalStorageDirectory());
    }

    private void openDir(File dir) {
        if (!hasStorageAccess()) {
            requestStorageAccess();
            return;
        }
        clearSelection();
        viewModel.open(dir);
    }

    private void openPublic(String type) {
        openDir(Environment.getExternalStoragePublicDirectory(type));
    }

    private void showCategory(FileTypes.Category category) {
        List<FileEntry> filtered = new ArrayList<>();
        for (FileEntry e : latestItems) {
            if (e.directory || FileTypes.categoryOf(e.name) == category) filtered.add(e);
        }
        adapter.submitList(filtered);
        setStatus("Bu klasörde " + categoryLabel(category) + ": " + filtered.size() + " öğe (klasörler dahil)");
    }

    private static String categoryLabel(FileTypes.Category c) {
        switch (c) {
            case IMAGE: return "görseller";
            case VIDEO: return "videolar";
            case DOCUMENT: return "belgeler";
            case APK: return "APK dosyaları";
            case ARCHIVE: return "arşivler";
            default: return "dosyalar";
        }
    }

    @Override public void onClick(FileEntry entry) {
        if (!selected.isEmpty()) {
            toggleSelection(entry);
            return;
        }
        if (entry.directory) openDir(entry.toFile());
        else openFile(entry.toFile());
    }

    @Override public void onLongClick(FileEntry entry) { toggleSelection(entry); }

    @Override public boolean isSelected(FileEntry entry) { return selected.contains(entry.path); }

    private void toggleSelection(FileEntry entry) {
        if (selected.contains(entry.path)) selected.remove(entry.path);
        else selected.add(entry.path);
        adapter.notifyDataSetChanged();
        updateSelectionUi();
    }

    private void clearSelection() {
        selected.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        if (selectionView != null) {
            String pending = pendingMode == PendingMode.NONE ? "" : " • Bekleyen: " + (pendingMode == PendingMode.COPY ? "kopyala" : "taşı") + " " + pendingSources.size();
            selectionView.setText("Seçim: " + selected.size() + pending);
        }
    }

    private void stageSelection(PendingMode mode) {
        List<File> files = selectedFiles();
        if (files.isEmpty()) {
            toast("Önce dosya veya klasör seç.");
            return;
        }
        pendingMode = mode;
        pendingSources = Collections.unmodifiableList(new ArrayList<>(files));
        clearSelection();
        updateSelectionUi();
        setStatus((mode == PendingMode.COPY ? "Kopyalanacak" : "Taşınacak") + " " + pendingSources.size() + " öğe hazır. Hedef klasöre git ve BURAYA bas.");
    }

    private void pasteHere() {
        BrowserViewModel.BrowserState s = viewModel.state().getValue();
        File destination = s == null ? null : s.currentDir;
        if (pendingMode == PendingMode.NONE || pendingSources.isEmpty()) {
            toast("Bekleyen kopyalama/taşıma yok.");
            return;
        }
        if (destination == null) return;
        PendingMode mode = pendingMode;
        List<File> sources = new ArrayList<>(pendingSources);
        setStatus("İşlem başladı: " + sources.size() + " öğe…");
        FileOperations.Callback cb = result -> {
            setStatus(result.successCount + " başarılı, " + result.failureCount + " hata" + firstFailure(result));
            if (mode == PendingMode.MOVE && result.failureCount == 0) {
                pendingMode = PendingMode.NONE;
                pendingSources = Collections.emptyList();
            } else if (mode == PendingMode.COPY && result.failureCount == 0) {
                pendingMode = PendingMode.NONE;
                pendingSources = Collections.emptyList();
            }
            updateSelectionUi();
            viewModel.refresh();
        };
        if (mode == PendingMode.COPY) operations.copy(sources, destination, cb);
        else operations.move(sources, destination, cb);
    }

    private void deleteSelected() {
        List<File> files = selectedFiles();
        if (files.isEmpty()) { toast("Silmek için öğe seç."); return; }
        new AlertDialog.Builder(this)
                .setTitle("Silinsin mi?")
                .setMessage(files.size() + " öğe kalıcı olarak silinecek.")
                .setNegativeButton("VAZGEÇ", null)
                .setPositiveButton("SİL", (d, w) -> {
                    setStatus("Siliniyor…");
                    operations.delete(files, result -> {
                        setStatus(result.successCount + " silindi, " + result.failureCount + " hata" + firstFailure(result));
                        clearSelection();
                        viewModel.refresh();
                    });
                }).show();
    }

    private void renameSelected() {
        List<File> files = selectedFiles();
        if (files.size() != 1) { toast("Yeniden adlandırmak için tek öğe seç."); return; }
        File file = files.get(0);
        EditText input = dialogInput(file.getName());
        new AlertDialog.Builder(this)
                .setTitle("Yeni ad")
                .setView(input)
                .setNegativeButton("VAZGEÇ", null)
                .setPositiveButton("KAYDET", (d, w) -> operations.rename(file, input.getText().toString(), result -> {
                    setStatus(result.allSucceeded() ? "Ad değiştirildi." : "Ad değiştirilemedi" + firstFailure(result));
                    clearSelection();
                    viewModel.refresh();
                })).show();
    }

    private void createFolder() {
        BrowserViewModel.BrowserState s = viewModel.state().getValue();
        if (s == null || s.currentDir == null) return;
        EditText input = dialogInput("");
        input.setHint("Klasör adı");
        new AlertDialog.Builder(this)
                .setTitle("Yeni klasör")
                .setView(input)
                .setNegativeButton("VAZGEÇ", null)
                .setPositiveButton("OLUŞTUR", (d, w) -> operations.mkdir(s.currentDir, input.getText().toString(), result -> {
                    setStatus(result.allSucceeded() ? "Klasör oluşturuldu." : "Klasör oluşturulamadı" + firstFailure(result));
                    viewModel.refresh();
                })).show();
    }

    private void shareSelected() {
        List<File> files = selectedFiles();
        if (files.isEmpty()) { toast("Paylaşmak için dosya seç."); return; }
        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : files) {
            if (f.isFile()) {
                try { uris.add(FileProvider.getUriForFile(this, getPackageName() + ".files", f)); }
                catch (Throwable ignored) { }
            }
        }
        if (uris.isEmpty()) { toast("Paylaşılabilir dosya yok."); return; }
        Intent share;
        if (uris.size() == 1) {
            share = new Intent(Intent.ACTION_SEND);
            share.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            share = new Intent(Intent.ACTION_SEND_MULTIPLE);
            share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        share.setType("*/*");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(share, "Paylaş")); }
        catch (Throwable t) { toast("Paylaşım uygulaması bulunamadı."); }
    }

    private void openFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            String ext = FileTypes.extension(file.getName());
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
            if (mime == null) mime = "*/*";
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast("Bu dosya türünü açacak uygulama yok.");
        } catch (Throwable t) {
            setStatus("Dosya açılamadı: " + safeMessage(t));
        }
    }

    private List<File> selectedFiles() {
        List<File> out = new ArrayList<>();
        for (String path : selected) out.add(new File(path));
        return out;
    }

    private static Set<String> pathsOf(List<FileEntry> entries) {
        Set<String> out = new LinkedHashSet<>();
        for (FileEntry e : entries) out.add(e.path);
        return out;
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                manageStorageLauncher.launch(intent);
            } catch (Throwable first) {
                manageStorageLauncher.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && hasStorageAccess()) openInitialIfNeeded();
    }

    private void updateStorageSummary() {
        try {
            File root = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(root.getAbsolutePath());
            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();
            storageView.setText("Depolama: " + humanSize(total - free) + " kullanılıyor / " + humanSize(total) + " • boş " + humanSize(free));
        } catch (Throwable t) {
            storageView.setText("Depolama bilgisi okunamadı");
        }
    }

    private HorizontalScrollView horizontalBar(Button... buttons) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (Button b : buttons) row.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        scroll.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(7, 26, 58));
        b.setBackgroundColor(Color.rgb(245, 196, 0));
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        p.setMarginEnd(dp(5));
        b.setLayoutParams(p);
        return b;
    }

    private TextView smallText() {
        TextView t = new TextView(this);
        t.setTextColor(Color.rgb(184, 196, 217));
        t.setTextSize(12);
        t.setPadding(dp(6), dp(3), dp(6), dp(3));
        return t;
    }

    private EditText dialogInput(String initial) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(initial);
        input.setSelection(input.length());
        int pad = dp(18);
        input.setPadding(pad, dp(8), pad, dp(8));
        return input;
    }

    private void setStatus(String text) {
        if (statusView != null) statusView.setText(text == null ? "" : text);
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private static String firstFailure(FileOperations.Result r) {
        return r.failures.isEmpty() ? "" : " • " + r.failures.get(0);
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isEmpty()) ? t.getClass().getSimpleName() : m;
    }
}
