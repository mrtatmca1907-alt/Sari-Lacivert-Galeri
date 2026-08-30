package com.atmaca.files;

import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity implements EntryAdapter.Listener {
    private static final int REQ_VIDEOS = 4101;
    private static final int REQ_CARD_FOLDER = 4102;
    private static final int REQ_CLOUD_FOLDER = 4103;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<Uri> selectedVideos = new ArrayList<>();
    private CatalogDb db;
    private EntryAdapter adapter;
    private TextView status, pathView;
    private EditText hostInput, searchInput;
    private String currentPath = "/";
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new CatalogDb(this);
        prefs = getSharedPreferences("atmaca", MODE_PRIVATE);
        setContentView(buildUi());
        refreshList();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        TextView bar = new TextView(this); bar.setText("ATMACA DOSYA YÖNETİCİSİ"); bar.setTextColor(Color.WHITE); bar.setTextSize(20); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(24,24,24,24); bar.setBackgroundColor(Color.rgb(0,30,98));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout conn = new LinearLayout(this); conn.setOrientation(LinearLayout.HORIZONTAL); conn.setPadding(14,10,14,6);
        hostInput = new EditText(this); hostInput.setSingleLine(true); hostInput.setHint("PC IP"); hostInput.setText(prefs.getString("host", "192.168.1.104"));
        Button sync = new Button(this); sync.setText("Wi‑Fi Eşitle"); sync.setOnClickListener(v -> syncNow());
        conn.addView(hostInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); conn.addView(sync);
        root.addView(conn);

        status = new TextView(this); status.setPadding(20,4,20,8); status.setTextColor(Color.DKGRAY); root.addView(status);

        LinearLayout tools = new LinearLayout(this); tools.setOrientation(LinearLayout.HORIZONTAL); tools.setPadding(12,6,12,6);
        Button back = new Button(this); back.setText("← Geri"); back.setOnClickListener(v -> goBack(false));
        Button mkdir = new Button(this); mkdir.setText("+ Yeni Klasör"); mkdir.setOnClickListener(v -> askMkdir());
        Button send = new Button(this); send.setText("Kuyruğu Gönder"); send.setOnClickListener(v -> sendPending());
        tools.addView(back); tools.addView(mkdir); tools.addView(send); root.addView(tools);

        Button videos = new Button(this); videos.setText("Video Seç → Kart / Bulut / HDD"); videos.setOnClickListener(v -> pickVideos());
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); vp.setMargins(12,0,12,6); root.addView(videos, vp);

        pathView = new TextView(this); pathView.setTextSize(16); pathView.setPadding(20,10,20,10); pathView.setBackgroundColor(Color.rgb(254,219,0)); root.addView(pathView);

        searchInput = new EditText(this); searchInput.setHint("Ara..."); searchInput.setSingleLine(true); searchInput.setPadding(20,8,20,8);
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ refreshList(); } public void afterTextChanged(Editable e){}
        });
        root.addView(searchInput);

        RecyclerView list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this)); adapter = new EntryAdapter(this); list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        return root;
    }

    private void pickVideos() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_VIDEOS);
    }

    private void chooseTarget() {
        String[] items = StorageTargetPolicy.targets();
        new AlertDialog.Builder(this).setTitle(selectedVideos.size() + " video nereye kaydedilsin?").setItems(items, (d, which) -> {
            if (which == 0) pickDestinationTree(REQ_CARD_FOLDER);
            else if (which == 1) pickDestinationTree(REQ_CLOUD_FOLDER);
            else uploadVideosToHdd();
        }).show();
    }

    private void pickDestinationTree(int requestCode) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, requestCode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_VIDEOS) {
            selectedVideos.clear();
            ClipData clips = data.getClipData();
            if (clips != null) {
                for (int x = 0; x < clips.getItemCount(); x++) selectedVideos.add(clips.getItemAt(x).getUri());
            } else if (data.getData() != null) selectedVideos.add(data.getData());
            if (selectedVideos.isEmpty()) { toast("Video seçilmedi"); return; }
            chooseTarget();
            return;
        }
        if (requestCode == REQ_CARD_FOLDER || requestCode == REQ_CLOUD_FOLDER) {
            Uri tree = data.getData();
            if (tree == null) return;
            try {
                getContentResolver().takePersistableUriPermission(tree, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            } catch (Exception ignored) {}
            copyVideosToTree(tree, requestCode == REQ_CARD_FOLDER ? "Kart" : "Bulut");
        }
    }

    private void copyVideosToTree(Uri tree, String label) {
        ArrayList<Uri> items = new ArrayList<>(selectedVideos);
        status.setText(label + " klasörüne kopyalanıyor...");
        io.execute(() -> {
            int done = 0;
            try {
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                for (Uri src : items) {
                    String name = displayName(src);
                    String mime = getContentResolver().getType(src);
                    if (mime == null) mime = mimeFor(name);
                    Uri dest = DocumentsContract.createDocument(getContentResolver(), parent, mime, name);
                    if (dest == null) throw new IllegalStateException("Hedef dosya oluşturulamadı");
                    try (InputStream in = getContentResolver().openInputStream(src); OutputStream out = getContentResolver().openOutputStream(dest)) {
                        if (in == null || out == null) throw new IllegalStateException("Dosya açılamadı");
                        copy(in, out);
                    }
                    done++;
                    int n = done;
                    runOnUiThread(() -> status.setText(label + ": " + n + "/" + items.size()));
                }
                selectedVideos.clear();
                runOnUiThread(() -> { status.setText(label + " aktarımı tamamlandı: " + items.size()); toast("Videolar hedef klasöre kaydedildi"); });
            } catch (Exception e) {
                int n = done;
                runOnUiThread(() -> { status.setText(label + " aktarım hatası: " + safe(e.getMessage())); toast(n + " video tamamlandı, aktarım kesildi"); });
            }
        });
    }

    private void uploadVideosToHdd() {
        ArrayList<Uri> items = new ArrayList<>(selectedVideos);
        String host = hostInput.getText().toString().trim();
        String folder = StorageTargetPolicy.hddFolder(currentPath);
        status.setText("HDD " + folder + " klasörüne gönderiliyor...");
        io.execute(() -> {
            int done = 0;
            try {
                AtmacaApi api = new AtmacaApi(host);
                if (!api.health()) throw new IllegalStateException("PC servisi yanıt vermedi");
                for (Uri src : items) {
                    String name = displayName(src);
                    try (InputStream in = getContentResolver().openInputStream(src)) {
                        if (in == null) throw new IllegalStateException("Video açılamadı");
                        api.upload(folder, name, in);
                    }
                    done++;
                    int n = done;
                    runOnUiThread(() -> status.setText("HDD: " + n + "/" + items.size() + " → " + folder));
                }
                selectedVideos.clear();
                runOnUiThread(() -> { toast("Videolar HDD klasörüne gönderildi"); syncNow(); });
            } catch (Exception e) {
                int n = done;
                runOnUiThread(() -> { status.setText("HDD aktarım hatası: " + safe(e.getMessage())); toast(n + " video gönderildi, bağlantı kesildi"); });
            }
        });
    }

    private void refreshList() {
        pathView.setText("HDD: " + currentPath);
        String q = searchInput == null ? "" : searchInput.getText().toString();
        io.execute(() -> {
            List<CatalogEntry> rows = q.trim().isEmpty() ? db.listChildren(currentPath, "", 1000, 0) : db.search(q, 1000);
            int count = db.count(); int pending = db.pendingQueue().size();
            runOnUiThread(() -> { adapter.setItems(rows); status.setText(count + " kayıt • " + pending + " bekleyen işlem • " + rows.size() + " gösteriliyor"); });
        });
    }

    private void goBack(boolean allowExitAtRoot) {
        String target = NavigationPolicy.backTarget(currentPath);
        if (target != null) {
            currentPath = target;
            if (searchInput != null) searchInput.setText("");
            refreshList();
            return;
        }
        if (allowExitAtRoot) super.onBackPressed();
        else toast("Ana klasördesin");
    }

    @Override public void onBackPressed() { goBack(true); }

    private void syncNow() {
        String host = hostInput.getText().toString().trim(); prefs.edit().putString("host", host).apply(); status.setText("PC'ye bağlanıyor...");
        io.execute(() -> {
            try {
                AtmacaApi api = new AtmacaApi(host);
                if (!api.health()) throw new IllegalStateException("PC servisi yanıt vermedi");
                List<String> pending = db.pendingQueue();
                if (!pending.isEmpty()) { api.sendQueue(pending); db.clearQueue(); }
                List<CatalogEntry> rows = api.fetchCatalog(); db.replaceAll(rows);
                runOnUiThread(() -> { toast("Wi‑Fi eşitleme tamam"); refreshList(); });
            } catch (Exception e) { runOnUiThread(() -> { status.setText("Offline mod: " + safe(e.getMessage())); toast("PC yok; offline katalog açık"); refreshList(); }); }
        });
    }

    private void sendPending() {
        String host = hostInput.getText().toString().trim();
        io.execute(() -> {
            try {
                List<String> pending = db.pendingQueue();
                if (pending.isEmpty()) { runOnUiThread(() -> toast("Bekleyen işlem yok")); return; }
                new AtmacaApi(host).sendQueue(pending); db.clearQueue();
                runOnUiThread(() -> { toast("Kuyruk gönderildi"); syncNow(); });
            } catch (Exception e) { runOnUiThread(() -> toast("PC'ye ulaşılamadı; kuyruk korunuyor")); }
        });
    }

    @Override public void onClick(CatalogEntry e) {
        if (FileActionPolicy.onTap(e.isFolder()) == FileActionPolicy.Action.NAVIGATE) {
            currentPath = e.path;
            if (searchInput != null) searchInput.setText("");
            refreshList();
            return;
        }
        showFileMenu(e);
    }

    private void showFileMenu(CatalogEntry e) {
        String[] items = FileActionPolicy.fileMenu();
        new AlertDialog.Builder(this).setTitle(e.name).setItems(items, (d, which) -> {
            if (which == 0) openRemoteFile(e);
            else if (which == 1) downloadRemoteFile(e);
            else if (which == 2) askRename(e);
            else if (which == 3) askMove(e);
            else queueDelete(e);
        }).show();
    }

    @Override public void onLongClick(CatalogEntry e) {
        String[] items = {"Yeniden adlandır", "Taşı", "Sil"};
        new AlertDialog.Builder(this).setTitle(e.name).setItems(items, (d, which) -> {
            if (which == 0) askRename(e); else if (which == 1) askMove(e); else queueDelete(e);
        }).show();
    }

    private void openRemoteFile(CatalogEntry e) {
        status.setText("Dosya açılmak için alınıyor...");
        String host = hostInput.getText().toString().trim();
        io.execute(() -> {
            try {
                File dir = new File(getCacheDir(), "open");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Önbellek klasörü oluşturulamadı");
                File outFile = new File(dir, safeName(e.name));
                try (FileOutputStream out = new FileOutputStream(outFile, false)) { new AtmacaApi(host).download(e.path, out); }
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", outFile);
                Intent intent = new Intent(Intent.ACTION_VIEW); intent.setDataAndType(uri, mimeFor(e.name)); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> { try { startActivity(intent); status.setText("Dosya açıldı: " + e.name); } catch (Exception ex) { toast("Bu dosyayı açacak uygulama bulunamadı"); } });
            } catch (Exception ex) { runOnUiThread(() -> { status.setText("Açma hatası: " + safe(ex.getMessage())); toast("Dosya HDD'den alınamadı"); }); }
        });
    }

    private void downloadRemoteFile(CatalogEntry e) {
        status.setText("İndiriliyor: " + e.name);
        String host = hostInput.getText().toString().trim();
        io.execute(() -> {
            Uri created = null;
            try {
                AtmacaApi api = new AtmacaApi(host);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues(); v.put(MediaStore.Downloads.DISPLAY_NAME, e.name); v.put(MediaStore.Downloads.MIME_TYPE, mimeFor(e.name)); v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ATMACA"); v.put(MediaStore.Downloads.IS_PENDING, 1);
                    created = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (created == null) throw new IllegalStateException("İndirme kaydı oluşturulamadı");
                    try (OutputStream out = getContentResolver().openOutputStream(created)) { if (out == null) throw new IllegalStateException("Dosya açılamadı"); api.download(e.path, out); }
                    ContentValues done = new ContentValues(); done.put(MediaStore.Downloads.IS_PENDING, 0); getContentResolver().update(created, done, null, null);
                } else {
                    File base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS); if (base == null) throw new IllegalStateException("İndirme klasörü yok");
                    File dir = new File(base, "ATMACA"); if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("ATMACA klasörü oluşturulamadı");
                    try (FileOutputStream out = new FileOutputStream(new File(dir, safeName(e.name)), false)) { api.download(e.path, out); }
                }
                runOnUiThread(() -> { status.setText("İndirildi: " + e.name); toast("Download/ATMACA içine indirildi"); });
            } catch (Exception ex) {
                if (created != null && Build.VERSION.SDK_INT >= 29) { try { getContentResolver().delete(created, null, null); } catch (Exception ignored) {} }
                runOnUiThread(() -> { status.setText("İndirme hatası: " + safe(ex.getMessage())); toast("İndirme tamamlanamadı"); });
            }
        });
    }

    private void askMkdir() { askText("Yeni klasör oluştur", "Klasör adı", "", text -> queueOp("mkdir", PathUtil.child(currentPath, text), null, null)); }
    private void askRename(CatalogEntry e) { askText("Yeniden adlandır", "Yeni ad", e.name, text -> queueOp("rename", e.path, null, text)); }
    private void askMove(CatalogEntry e) { askText("Taşı", "Hedef klasör yolu", currentPath, text -> queueOp("move", e.path, PathUtil.normalize(text), null)); }
    private void queueDelete(CatalogEntry e) { new AlertDialog.Builder(this).setTitle("Silinsin mi?").setMessage(e.path).setPositiveButton("Sil", (d,w) -> queueOp("delete", e.path, null, null)).setNegativeButton("Vazgeç", null).show(); }

    private interface TextDone { void run(String text); }
    private void askText(String title, String hint, String initial, TextDone done) {
        EditText edit = new EditText(this); edit.setHint(hint); edit.setText(initial); edit.selectAll();
        new AlertDialog.Builder(this).setTitle(title).setView(edit).setPositiveButton("Tamam", (d,w) -> { String t = edit.getText().toString().trim(); if (!t.isEmpty()) done.run(t); }).setNegativeButton("Vazgeç", null).show();
    }

    private void queueOp(String op, String path, String dest, String newName) {
        try {
            JSONObject o = new JSONObject(); o.put("op", op); if (path != null) o.put("path", path); if (dest != null) o.put("dest", dest); if (newName != null) o.put("newName", newName);
            o.put("createdAt", System.currentTimeMillis()); db.addQueue(o.toString()); toast("İşlem kuyruğa alındı"); refreshList(); sendPending();
        } catch (Exception e) { toast("İşlem kaydedilemedi"); }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) { int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) return safeName(c.getString(i)); }
        } catch (Exception ignored) {}
        String last = uri == null ? null : uri.getLastPathSegment(); return safeName(last);
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[1024 * 1024]; int n; while ((n = in.read(buf)) >= 0) out.write(buf, 0, n); out.flush();
    }
    private static String safeName(String name) { return (name == null || name.trim().isEmpty()) ? "video" : name.replace('/', '_').replace('\\', '_'); }
    private static String mimeFor(String name) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(name == null ? "" : name).toLowerCase(); String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext); return type == null ? "application/octet-stream" : type;
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private static String safe(String s) { return s == null ? "bağlantı yok" : s; }
    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }
}
