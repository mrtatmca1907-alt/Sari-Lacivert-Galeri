package com.atmaca.hiosfilemanager;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final int NAVY = Color.rgb(7, 27, 58);
    private static final int BLUE = Color.rgb(14, 58, 120);
    private static final int YELLOW = Color.rgb(245, 196, 0);
    private static final int BG = Color.rgb(247, 249, 252);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
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
    private LinearLayout selectionBar;
    private Button pasteButton;
    private EditText searchBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        ensureStorageAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasStorageAccess()) loadDirectory(currentDir);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

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
        title.setText("ATMACA HiOS Dosyalar");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        pathView = new TextView(this);
        pathView.setTextColor(Color.rgb(205, 217, 235));
        pathView.setTextSize(11);
        pathView.setSingleLine(true);
        titleCol.addView(title);
        titleCol.addView(pathView);
        top.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button refresh = smallButton("↻");
        refresh.setOnClickListener(v -> loadDirectory(currentDir));
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(44)));
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

        Button search = actionButton("Ara");
        search.setOnClickListener(v -> applyFilter(searchBox.getText().toString()));
        tools.addView(search, marginParams(dp(64), dp(44)));

        Button folder = actionButton("+ Klasör");
        folder.setOnClickListener(v -> createFolderDialog());
        tools.addView(folder, marginParams(dp(92), dp(44)));
        root.addView(tools);

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
        statusView.setText("Yükleniyor…");
        selected.clear();
        updateSelectionBar();

        io.execute(() -> {
            File[] raw = dir.listFiles();
            List<File> next = new ArrayList<>();
            if (raw != null) next.addAll(Arrays.asList(raw));
            next.sort(Comparator
                    .comparing((File f) -> !f.isDirectory())
                    .thenComparing(f -> f.getName().toLowerCase(Locale.ROOT)));
            runOnUiThread(() -> {
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
                row.setPadding(dp(12), dp(8), dp(12), dp(8));

                TextView icon = new TextView(MainActivity.this);
                icon.setTextSize(28);
                icon.setGravity(Gravity.CENTER);
                row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(52)));

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
            h.icon.setText(f.isDirectory() ? "📁" : "📄");
            h.name.setText(f.getName().isEmpty() ? f.getAbsolutePath() : f.getName());
            h.detail.setText(detail(f));
            convertView.setBackgroundColor(selected.contains(f.getAbsolutePath()) ? Color.rgb(255, 244, 181) : Color.WHITE);
            return convertView;
        }
    }

    private static class Holder {
        final TextView icon, name, detail;
        Holder(TextView i, TextView n, TextView d) { icon = i; name = n; detail = d; }
    }
}
