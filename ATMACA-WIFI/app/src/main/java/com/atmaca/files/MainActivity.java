package com.atmaca.files;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity implements EntryAdapter.Listener {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
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
        hostInput = new EditText(this); hostInput.setSingleLine(true); hostInput.setHint("PC IP (örn. 192.168.1.104)"); hostInput.setText(prefs.getString("host", "192.168.1.104"));
        Button sync = new Button(this); sync.setText("Wi‑Fi Eşitle"); sync.setOnClickListener(v -> syncNow());
        conn.addView(hostInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); conn.addView(sync);
        root.addView(conn);

        status = new TextView(this); status.setPadding(20,4,20,8); status.setTextColor(Color.DKGRAY); root.addView(status);
        pathView = new TextView(this); pathView.setTextSize(16); pathView.setPadding(20,10,20,10); pathView.setBackgroundColor(Color.rgb(254,219,0)); root.addView(pathView);

        LinearLayout tools = new LinearLayout(this); tools.setOrientation(LinearLayout.HORIZONTAL); tools.setPadding(12,6,12,6);
        Button up = new Button(this); up.setText("↑ Üst"); up.setOnClickListener(v -> { currentPath = PathUtil.parent(currentPath); refreshList(); });
        Button mkdir = new Button(this); mkdir.setText("+ Klasör"); mkdir.setOnClickListener(v -> askMkdir());
        Button send = new Button(this); send.setText("Kuyruğu Gönder"); send.setOnClickListener(v -> sendPending());
        tools.addView(up); tools.addView(mkdir); tools.addView(send); root.addView(tools);

        searchInput = new EditText(this); searchInput.setHint("Ara..."); searchInput.setSingleLine(true); searchInput.setPadding(20,8,20,8);
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ refreshList(); } public void afterTextChanged(Editable e){}
        });
        root.addView(searchInput);

        RecyclerView list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this)); adapter = new EntryAdapter(this); list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        return root;
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

    @Override public void onClick(CatalogEntry e) { if (e.isFolder()) { currentPath = e.path; searchInput.setText(""); refreshList(); } }
    @Override public void onLongClick(CatalogEntry e) {
        String[] items = {"Yeniden adlandır", "Taşı", "Sil"};
        new AlertDialog.Builder(this).setTitle(e.name).setItems(items, (d, which) -> {
            if (which == 0) askRename(e); else if (which == 1) askMove(e); else queueDelete(e);
        }).show();
    }

    private void askMkdir() { askText("Yeni klasör", "Klasör adı", "", text -> queueOp("mkdir", PathUtil.child(currentPath, text), null, null)); }
    private void askRename(CatalogEntry e) { askText("Yeniden adlandır", "Yeni ad", e.name, text -> queueOp("rename", e.path, null, text)); }
    private void askMove(CatalogEntry e) { askText("Taşı", "Hedef klasör yolu", currentPath, text -> queueOp("move", e.path, PathUtil.normalize(text), null)); }
    private void queueDelete(CatalogEntry e) { new AlertDialog.Builder(this).setTitle("Silinsin mi?").setMessage(e.path).setPositiveButton("Sil", (d,w) -> queueOp("delete", e.path, null, null)).setNegativeButton("Vazgeç", null).show(); }

    private interface TextDone { void run(String text); }
    private void askText(String title, String hint, String initial, TextDone done) {
        EditText edit = new EditText(this); edit.setHint(hint); edit.setText(initial); edit.selectAll();
        new AlertDialog.Builder(this).setTitle(title).setView(edit).setPositiveButton("Tamam", (d,w) -> {
            String t = edit.getText().toString().trim(); if (!t.isEmpty()) done.run(t);
        }).setNegativeButton("Vazgeç", null).show();
    }

    private void queueOp(String op, String path, String dest, String newName) {
        try {
            JSONObject o = new JSONObject(); o.put("op", op); if (path != null) o.put("path", path); if (dest != null) o.put("dest", dest); if (newName != null) o.put("newName", newName);
            o.put("createdAt", System.currentTimeMillis()); db.addQueue(o.toString()); toast("İşlem kuyruğa alındı"); refreshList();
        } catch (Exception e) { toast("İşlem kaydedilemedi"); }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private static String safe(String s) { return s == null ? "bağlantı yok" : s; }
    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }
}
