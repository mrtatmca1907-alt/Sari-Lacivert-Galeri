package com.atmaca.hiosfilemanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import android.util.LruCache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(7, 27, 58);
    private static final int BLUE = Color.rgb(14, 58, 120);
    private static final int YELLOW = Color.rgb(245, 196, 0);
    private static final int BG = Color.rgb(247, 249, 252);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor thumbs = new ThreadPoolExecutor(
            2, 2, 15, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(32),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );
    private final LruCache<String, Bitmap> thumbCache = new LruCache<String, Bitmap>(24 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };
    private final List<File> allItems = new ArrayList<>();
    private final List<File> shownItems = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private final List<File> clipboard = new ArrayList<>();

    private File currentDir = new File("/storage/emulated/0");
    private boolean clipboardMove = false;

    private ListView listView;
    private FileAdapter adapter;
    private TextView pathView;
    private TextView statusView;
    private TextView storageView;
    private LinearLayout selectionBar;
    private Button pasteButton;
    private EditText searchBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String startPath = getIntent().getStringExtra("startPath");
        if (startPath != null && !startPath.isEmpty()) currentDir = new File(startPath);
        buildUi();
        ensureStorageAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasStorageAccess()) loadDirectory(currentDir);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        thumbs.shutdownNow();
        io.shutdownNow();
        thumbCache.evictAll();
    }

    @Override
    public void onBackPressed() {
        if (!selected.isEmpty()) {
            selected.clear();
            adapter.notifyDataSetChanged();
            updateSelectionBar();
            return;
        }
        if (!clipboard.isEmpty()) {
            clipboard.clear();
            pasteButton.setVisibility(View.GONE);
            toast("Kopyalama/taşıma iptal edildi");
            return;
        }
        File root = new File("/storage/emulated/0");
        if (!currentDir.getAbsolutePath().equals(root.getAbsolutePath())) {
            File parent = currentDir.getParentFile();
            if (parent != null && parent.canRead() && parent.getAbsolutePath().startsWith(root.getAbsolutePath())) {
                currentDir = parent;
                searchBox.setText("");
                loadDirectory(currentDir);
                return;
            }
            currentDir = root;
            searchBox.setText("");
            loadDirectory(currentDir);
            return;
        }
        super.onBackPressed();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFocusableInTouchMode(true);
        root.requestFocus();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(8), dp(8), dp(8));
        top.setBackgroundColor(NAVY);

        Button up = smallButton("↑");
        up.setOnClickListener(v -> goUp());
        top.addView(up, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Dosyalar");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        pathView = new TextView(this);
        pathView.setTextColor(Color.rgb(205, 217, 235));
        pathView.setTextSize(12);
        pathView.setSingleLine(true);
        titleCol.addView(title);
        titleCol.addView(pathView);
        top.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button more = smallButton("⋮");
        more.setOnClickListener(v -> showOptionsMenu(more));
        top.addView(more, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(top);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(dp(8), dp(6), dp(8), dp(6));
        tools.setGravity(Gravity.CENTER_VERTICAL);

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("Dosya veya klasör ara");
        searchBox.setTextSize(14);
        searchBox.setBackgroundColor(Color.WHITE);
        tools.addView(searchBox, new LinearLayout.LayoutParams(0, dp(46), 1f));

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(tools);

        storageView = new TextView(this);
        storageView.setPadding(dp(14), dp(8), dp(14), dp(8));
        storageView.setTextColor(NAVY);
        storageView.setTextSize(12);
        storageView.setTypeface(Typeface.DEFAULT_BOLD);
        storageView.setBackgroundColor(Color.rgb(233, 239, 248));
        root.addView(storageView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        pasteButton = actionButton("Buraya Yapıştır");
        pasteButton.setVisibility(View.GONE);
        pasteButton.setOnClickListener(v -> pasteClipboard());
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        pasteParams.setMargins(dp(8), 0, dp(8), dp(6));
        root.addView(pasteButton, pasteParams);

        listView = new ListView(this);
        listView.setDividerHeight(1);
        listView.setFastScrollEnabled(true);
        adapter = new FileAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            File f = shownItems.get(position);
            if (!selected.isEmpty()) {
                toggleSelection(f);
            } else if (f.isDirectory()) {
                currentDir = f;
                loadDirectory(f);
            } else if (isImageFile(f)) {
                openImageViewer(f);
            } else if (isVideoFile(f)) {
                openVideoViewer(f);
            } else {
                openFile(f);
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleSelection(shownItems.get(position));
            return true;
        });
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        statusView = new TextView(this);
        statusView.setPadding(dp(12), dp(5), dp(12), dp(5));
        statusView.setTextColor(Color.DKGRAY);
        statusView.setTextSize(12);
        root.addView(statusView);

        selectionBar = new LinearLayout(this);
        selectionBar.setOrientation(LinearLayout.HORIZONTAL);
        selectionBar.setGravity(Gravity.CENTER);
        selectionBar.setPadding(dp(6), dp(6), dp(6), dp(6));
        selectionBar.setBackgroundColor(BLUE);
        selectionBar.setVisibility(View.GONE);

        Button copy = bottomButton("Kopyala");
        copy.setOnClickListener(v -> setClipboard(false));
        Button move = bottomButton("Taşı");
        move.setOnClickListener(v -> setClipboard(true));
        Button rename = bottomButton("Yeniden Adlandır");
        rename.setOnClickListener(v -> renameSelected());
        Button delete = bottomButton("Sil");
        delete.setOnClickListener(v -> confirmDelete());
        selectionBar.addView(copy, equalParams());
        selectionBar.addView(move, equalParams());
        selectionBar.addView(rename, equalParams());
        selectionBar.addView(delete, equalParams());
        root.addView(selectionBar);

        setContentView(root);
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(20);
        b.setTextColor(YELLOW);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(BLUE);
        return b;
    }

    private Button bottomButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setTextColor(NAVY);
        b.setBackgroundColor(YELLOW);
        return b;
    }

    private LinearLayout.LayoutParams equalParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private LinearLayout.LayoutParams marginParams(int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(6), 0, 0, 0);
        return p;
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureStorageAccess() {
        if (hasStorageAccess()) {
            loadDirectory(currentDir);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 77);
        }
    }

    private void loadDirectory(File dir) {
        if (dir == null) return;
        pathView.setText(dir.getAbsolutePath());
        String visibleName = dir.getAbsolutePath().equals("/storage/emulated/0") ? "Ana bellek" : dir.getName();
        if (visibleName == null || visibleName.isEmpty()) visibleName = "Dosyalar";
        File storageRoot = new File("/storage/emulated/0");
        long total = storageRoot.getTotalSpace();
        long free = storageRoot.getUsableSpace();
        long used = Math.max(0, total - free);
        storageView.setText("Depolama  •  " + formatBytes(used) + " kullanılıyor  •  " + formatBytes(free) + " boş");
        statusView.setText("Yükleniyor…");
        selected.clear();
        updateSelectionBar();

        io.execute(() -> {
            File[] raw = dir.listFiles();
            List<File> next = new ArrayList<>(raw == null ? 0 : raw.length);
            if (raw != null) next.addAll(Arrays.asList(raw));
            next.sort(Comparator
                    .comparing((File f) -> !f.isDirectory())
                    .thenComparing(f -> f.getName().toLowerCase(Locale.ROOT)));
            runOnUiThread(() -> {
                if (!currentDir.getAbsolutePath().equals(dir.getAbsolutePath())) return;
                allItems.clear();
                allItems.addAll(next);
                applyFilter(searchBox.getText().toString());
                statusView.setText(next.size() + " öğe");
            });
        });
    }

    private void applyFilter(String q) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        shownItems.clear();
        if (query.isEmpty()) shownItems.addAll(allItems);
        else {
            for (File f : allItems) {
                if (f.getName().toLowerCase(Locale.ROOT).contains(query)) shownItems.add(f);
            }
        }
        adapter.notifyDataSetChanged();
        statusView.setText(shownItems.size() + " / " + allItems.size() + " öğe");
    }

    private void goUp() {
        File p = currentDir.getParentFile();
        if (p != null && p.canRead()) {
            currentDir = p;
            loadDirectory(currentDir);
        }
    }

    private void toggleSelection(File f) {
        String p = f.getAbsolutePath();
        if (!selected.add(p)) selected.remove(p);
        adapter.notifyDataSetChanged();
        updateSelectionBar();
    }

    private void updateSelectionBar() {
        selectionBar.setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
        statusView.setText(selected.isEmpty() ? shownItems.size() + " öğe" : selected.size() + " seçili");
    }

    private List<File> selectedFiles() {
        List<File> out = new ArrayList<>();
        for (String p : selected) out.add(new File(p));
        return out;
    }

    private void setClipboard(boolean move) {
        clipboard.clear();
        clipboard.addAll(selectedFiles());
        clipboardMove = move;
        selected.clear();
        updateSelectionBar();
        adapter.notifyDataSetChanged();
        pasteButton.setText(move ? "Buraya Taşı" : "Buraya Kopyala");
        pasteButton.setVisibility(View.VISIBLE);
        toast(clipboard.size() + " öğe hazır");
    }

    private void pasteClipboard() {
        if (clipboard.isEmpty()) return;
        File targetDir = currentDir;
        List<File> jobs = new ArrayList<>(clipboard);
        boolean move = clipboardMove;
        runBusy(move ? "Taşınıyor…" : "Kopyalanıyor…", () -> {
            int ok = 0;
            for (File src : jobs) {
                File dst = uniqueTarget(targetDir, src.getName());
                try {
                    if (move && src.renameTo(dst)) {
                        ok++;
                    } else {
                        copyRecursive(src, dst);
                        if (move && !deleteRecursive(src)) throw new IOException("Kaynak silinemedi");
                        ok++;
                    }
                } catch (Exception ignored) {
                }
            }
            int done = ok;
            runOnUiThread(() -> {
                clipboard.clear();
                pasteButton.setVisibility(View.GONE);
                loadDirectory(currentDir);
                toast(done + " öğe tamamlandı");
            });
        });
    }

    private void confirmDelete() {
        if (selected.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Kalıcı olarak silinsin mi?")
                .setMessage(selected.size() + " öğe silinecek.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil", (d, w) -> deleteSelected())
                .show();
    }

    private void deleteSelected() {
        List<File> jobs = selectedFiles();
        selected.clear();
        updateSelectionBar();
        runBusy("Siliniyor…", () -> {
            int ok = 0;
            for (File f : jobs) if (deleteRecursive(f)) ok++;
            int done = ok;
            runOnUiThread(() -> {
                loadDirectory(currentDir);
                toast(done + " öğe silindi");
            });
        });
    }

    private void renameSelected() {
        if (selected.size() != 1) {
            toast("Yeniden adlandırmak için tek öğe seç");
            return;
        }
        File src = selectedFiles().get(0);
        EditText input = new EditText(this);
        input.setText(src.getName());
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Yeniden adlandır")
                .setView(input)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Kaydet", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.contains("/")) return;
                    File dst = new File(src.getParentFile(), name);
                    if (src.renameTo(dst)) {
                        selected.clear();
                        loadDirectory(currentDir);
                    } else toast("Yeniden adlandırılamadı");
                }).show();
    }

    private void createFolderDialog() {
        EditText input = new EditText(this);
        input.setHint("Yeni klasör adı");
        new AlertDialog.Builder(this)
                .setTitle("Yeni klasör")
                .setView(input)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Oluştur", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.contains("/")) return;
                    File f = new File(currentDir, name);
                    if (f.mkdir()) loadDirectory(currentDir);
                    else toast("Klasör oluşturulamadı");
                }).show();
    }

    private boolean isImageFile(File file) {
        String n = file.getName().toLowerCase(Locale.ROOT);
        return n.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|heic|heif|avif)$");
    }

    private boolean isVideoFile(File file) {
        String n = file.getName().toLowerCase(Locale.ROOT);
        return n.matches(".*\\.(mp4|mkv|avi|mov|webm|3gp|m4v|ts)$");
    }

    private void openVideoViewer(File file) {
        Intent i = new Intent(this, VideoPlayerActivity.class);
        i.putExtra("file", file.getAbsolutePath());
        startActivity(i);
    }

    private void openImageViewer(File file) {
        Intent i = new Intent(this, ImageViewerActivity.class);
        i.putExtra("directory", currentDir.getAbsolutePath());
        i.putExtra("file", file.getAbsolutePath());
        startActivity(i);
    }

    private void showOptionsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Yeni klasör");
        menu.getMenu().add("Tümünü seç");
        menu.getMenu().add("Paylaş");
        menu.getMenu().add("Slayt başlat");
        menu.getMenu().add("Yenile");
        menu.getMenu().add("Ada göre sırala");
        menu.getMenu().add("Tarihe göre sırala");
        menu.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if (t.equals("Yeni klasör")) createFolderDialog();
            else if (t.equals("Tümünü seç")) selectAll();
            else if (t.equals("Paylaş")) shareSelected();
            else if (t.equals("Slayt başlat")) startSlideshow();
            else if (t.equals("Yenile")) loadDirectory(currentDir);
            else if (t.equals("Ada göre sırala")) sortByName();
            else if (t.equals("Tarihe göre sırala")) sortByDate();
            return true;
        });
        menu.show();
    }

    private void selectAll() {
        selected.clear();
        for (File f : shownItems) selected.add(f.getAbsolutePath());
        adapter.notifyDataSetChanged();
        updateSelectionBar();
    }

    private void shareSelected() {
        List<File> files = selectedFiles();
        if (files.isEmpty()) { toast("Önce dosya veya klasör seç"); return; }

        ArrayList<File> shareFiles = new ArrayList<>();
        for (File f : files) collectShareableMedia(f, shareFiles);

        if (shareFiles.isEmpty()) {
            toast("Paylaşılabilir görüntü veya video yok");
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>(shareFiles.size());
        boolean hasImage = false;
        boolean hasVideo = false;

        for (File f : shareFiles) {
            try {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
                uris.add(uri);
                if (isImageFile(f)) hasImage = true;
                if (isVideoFile(f)) hasVideo = true;
            } catch (Exception ignored) {}
        }

        if (uris.isEmpty()) {
            toast("Paylaşım URI'si oluşturulamadı");
            return;
        }

        String mime = hasImage && !hasVideo ? "image/*" :
                hasVideo && !hasImage ? "video/*" : "*/*";

        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
        send.setType(mime);
        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        ClipData clip = ClipData.newUri(getContentResolver(), "ATMACA medya", uris.get(0));
        for (int n = 1; n < uris.size(); n++) {
            clip.addItem(new ClipData.Item(uris.get(n)));
        }
        send.setClipData(clip);

        Intent chooser = Intent.createChooser(send, uris.size() + " dosyayı paylaş");
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(chooser);
        } catch (Exception e) {
            toast("Hedef uygulama bu kadar dosyayı tek seferde kabul etmedi");
        }
    }

    private void collectShareableMedia(File source, List<File> out) {
        if (source == null || !source.exists()) return;

        if (source.isFile()) {
            if (isImageFile(source) || isVideoFile(source)) out.add(source);
            return;
        }

        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) collectShareableMedia(child, out);
    }

    private void startSlideshow() {
        File first = null;
        for (File f : shownItems) if (isImageFile(f)) { first = f; break; }
        if (first == null) { toast("Bu klasörde resim yok"); return; }
        Intent i = new Intent(this, ImageViewerActivity.class);
        i.putExtra("directory", currentDir.getAbsolutePath());
        i.putExtra("file", first.getAbsolutePath());
        i.putExtra("slideshow", true);
        startActivity(i);
    }

    private void sortByName() {
        allItems.sort(Comparator.comparing((File f) -> !f.isDirectory())
                .thenComparing(f -> f.getName().toLowerCase(Locale.ROOT)));
        applyFilter(searchBox.getText().toString());
    }

    private void sortByDate() {
        allItems.sort(Comparator.comparing((File f) -> !f.isDirectory())
                .thenComparingLong(File::lastModified).reversed());
        applyFilter(searchBox.getText().toString());
    }

    private void openFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            String ext = MimeTypeMap.getFileExtensionFromUrl(file.getName());
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
            if (mime == null) mime = "*/*";
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("Bu dosyayı açacak uygulama yok");
        } catch (Exception e) {
            toast("Dosya açılamadı");
        }
    }

    private void runBusy(String message, Runnable task) {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(message);
        dialog.setCancelable(false);
        dialog.show();
        io.execute(() -> {
            try { task.run(); }
            finally { runOnUiThread(dialog::dismiss); }
        });
    }

    private File uniqueTarget(File dir, String name) {
        File target = new File(dir, name);
        if (!target.exists()) return target;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int n = 1;
        while (target.exists()) {
            target = new File(dir, base + " (" + n++ + ")" + ext);
        }
        return target;
    }

    private void copyRecursive(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new IOException("Klasör oluşturulamadı");
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) copyRecursive(child, new File(dst, child.getName()));
            }
            return;
        }
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            out.getFD().sync();
        }
        if (dst.length() != src.length()) throw new IOException("Boyut doğrulaması başarısız");
        dst.setLastModified(src.lastModified());
    }

    private boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File child : children) if (!deleteRecursive(child)) return false;
        }
        return !f.exists() || f.delete();
    }

    private String detail(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            return "Klasör" + (children == null ? "" : " • " + children.length + " öğe");
        }
        return formatBytes(f.length()) + " • " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(f.lastModified()));
    }

    private String fileIcon(File f) {
        if (f.isDirectory()) return "▣";
        String ext = f.getName().toLowerCase(Locale.ROOT);
        if (ext.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|heic|heif)$")) return "▧";
        if (ext.matches(".*\\.(mp4|mkv|avi|mov|webm|3gp|m4v|ts)$")) return "▶";
        if (ext.matches(".*\\.(mp3|wav|flac|aac|m4a|ogg)$")) return "♪";
        if (ext.matches(".*\\.(zip|rar|7z|tar|gz|apk)$")) return "◆";
        if (ext.matches(".*\\.(pdf|doc|docx|txt|rtf)$")) return "▤";
        return "•";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / (1024L * 1024)) + " MB";
        return String.format(Locale.US, "%.1f GB", bytes / (1024d * 1024d * 1024d));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private class FileAdapter extends BaseAdapter {
        @Override public int getCount() { return shownItems.size(); }
        @Override public Object getItem(int position) { return shownItems.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setMinimumHeight(dp(68));

                ImageView icon = new ImageView(MainActivity.this);
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                icon.setPadding(dp(3), dp(3), dp(3), dp(3));
                row.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));

                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView name = new TextView(MainActivity.this);
                name.setTextSize(15);
                name.setTextColor(NAVY);
                name.setTypeface(Typeface.DEFAULT_BOLD);
                name.setSingleLine(true);
                TextView detail = new TextView(MainActivity.this);
                detail.setTextSize(11);
                detail.setTextColor(Color.GRAY);
                detail.setSingleLine(true);
                col.addView(name);
                col.addView(detail);
                row.addView(col, new LinearLayout.LayoutParams(0, dp(58), 1f));

                h = new Holder(icon, name, detail);
                row.setTag(h);
                convertView = row;
            } else h = (Holder) convertView.getTag();

            File f = shownItems.get(position);
            h.icon.setTag(null);
            h.icon.setImageDrawable(null);
            h.icon.setBackgroundColor(f.isDirectory() ? Color.rgb(255, 244, 181) : Color.rgb(233, 239, 248));
            if (isImageFile(f)) {
                loadThumb(f, h.icon);
            } else {
                h.icon.setScaleType(ImageView.ScaleType.CENTER);
                h.icon.setImageResource(f.isDirectory() ? android.R.drawable.ic_menu_gallery : android.R.drawable.ic_menu_save);
            }
            h.name.setText(f.getName().isEmpty() ? f.getAbsolutePath() : f.getName());
            h.detail.setText(detail(f));
            if (selected.contains(f.getAbsolutePath())) {
                convertView.setBackgroundColor(Color.rgb(255, 244, 181));
            } else {
                convertView.setBackgroundColor(position % 2 == 0 ? Color.WHITE : Color.rgb(244, 247, 252));
            }
            return convertView;
        }
    }

    private void loadThumb(File file, ImageView view) {
        String key = file.getAbsolutePath() + ":" + file.lastModified();
        view.setTag(key);
        Bitmap cached = thumbCache.get(key);
        if (cached != null) {
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(cached);
            return;
        }
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageResource(android.R.drawable.ic_menu_gallery);
        thumbs.execute(() -> {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while ((bounds.outWidth / sample) > 180 || (bounds.outHeight / sample) > 180) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bm != null) {
                thumbCache.put(key, bm);
                runOnUiThread(() -> {
                    if (key.equals(view.getTag())) {
                        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        view.setImageBitmap(bm);
                    }
                });
            }
        });
    }

    private static class Holder {
        final ImageView icon;
        final TextView name, detail;
        Holder(ImageView i, TextView n, TextView d) { icon = i; name = n; detail = d; }
    }
}
