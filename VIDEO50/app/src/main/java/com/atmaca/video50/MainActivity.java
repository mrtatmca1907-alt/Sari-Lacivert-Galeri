package com.atmaca.video50;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int REQ_SOURCE_TREE = 5101;
    private static final int REQ_PERMS = 5102;
    private static final int REQ_ALL_FILES = 5103;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<VideoItem> videos = new ArrayList<>();
    private TextView status;
    private ProgressBar progress;
    private Button moveButton;
    private Runnable afterPermissions;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        status.setText("Hazır. Videolar Movies/Video 1, Video 2... içine 50'şerli taşınacak.");
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("ATMACA VIDEO 50");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setPadding(20, 30, 20, 30);
        title.setBackgroundColor(Color.rgb(0, 30, 98));
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(this);
        info.setText("Hedef sabit: Movies → Video 1 / Video 2 / ...\nHer klasör en fazla 50 video. Kopyalama yok, taşıma var.");
        info.setTextSize(16);
        info.setPadding(18, 22, 18, 22);
        info.setBackgroundColor(Color.rgb(254, 219, 0));
        root.addView(info);

        Button folder = new Button(this);
        folder.setText("KAYNAK KLASÖR SEÇ");
        folder.setOnClickListener(v -> chooseSourceTree());
        root.addView(folder, fullButton());

        Button phone = new Button(this);
        phone.setText("TELEFONU TARA");
        phone.setOnClickListener(v -> ensurePermissions(this::scanPhone));
        root.addView(phone, fullButton());

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(12, 24, 12, 18);
        root.addView(status);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        moveButton = new Button(this);
        moveButton.setText("50'ŞERLİ AYIR VE MOVIES'E TAŞI");
        moveButton.setEnabled(false);
        moveButton.setOnClickListener(v -> confirmMove());
        root.addView(moveButton, fullButton());

        return root;
    }

    private LinearLayout.LayoutParams fullButton() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 14, 0, 0);
        return p;
    }

    private void chooseSourceTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_SOURCE_TREE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ALL_FILES) {
            if (hasAllFilesAccess()) runAfterPermissions();
            else toast("Movies'e taşıma için tüm dosyalara erişim gerekli");
            return;
        }
        if (requestCode != REQ_SOURCE_TREE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } catch (Exception ignored) {}
        status.setText("Seçilen klasör taranıyor...");
        worker.execute(() -> {
            try {
                List<VideoItem> found = VideoScanner.scanTree(this, tree);
                runOnUiThread(() -> setFound(found, "Seçilen klasör"));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Tarama hatası: " + safe(e.getMessage())));
            }
        });
    }

    private void ensurePermissions(Runnable next) {
        afterPermissions = next;
        String[] wanted = PermissionPolicy.forSdk(Build.VERSION.SDK_INT);
        ArrayList<String> missing = new ArrayList<>();
        for (String p : wanted) if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQ_PERMS);
            return;
        }
        ensureAllFilesAccess();
    }

    private void ensureAllFilesAccess() {
        if (Build.VERSION.SDK_INT < 30 || hasAllFilesAccess()) {
            runAfterPermissions();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_ALL_FILES);
        } catch (Exception e) {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_ALL_FILES);
        }
    }

    private boolean hasAllFilesAccess() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private void runAfterPermissions() {
        Runnable r = afterPermissions;
        afterPermissions = null;
        if (r != null) r.run();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMS) return;
        for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) {
            toast("Video tarama izni verilmedi");
            afterPermissions = null;
            return;
        }
        ensureAllFilesAccess();
    }

    private void scanPhone() {
        status.setText("Telefon videolar için taranıyor...");
        moveButton.setEnabled(false);
        worker.execute(() -> {
            try {
                List<VideoItem> found = VideoScanner.scanMediaStore(this);
                runOnUiThread(() -> setFound(found, "Telefon"));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Tarama hatası: " + safe(e.getMessage())));
            }
        });
    }

    private void setFound(List<VideoItem> found, String source) {
        videos.clear();
        videos.addAll(found);
        progress.setProgress(0);
        moveButton.setEnabled(!videos.isEmpty());
        status.setText(source + ": " + videos.size() + " video bulundu. Hedef: Movies, 50'şerli klasörler.");
    }

    private void confirmMove() {
        if (videos.isEmpty()) return;
        ensurePermissions(() -> new AlertDialog.Builder(this)
                .setTitle("Videolar taşınsın mı?")
                .setMessage(videos.size() + " video eski yerlerinden kaldırılıp Movies/Video 1, Video 2... klasörlerine taşınacak.")
                .setPositiveButton("TAŞI", (d, w) -> startMove())
                .setNegativeButton("Vazgeç", null)
                .show());
    }

    private void startMove() {
        ArrayList<VideoItem> items = new ArrayList<>(videos);
        moveButton.setEnabled(false);
        progress.setMax(Math.max(1, items.size()));
        progress.setProgress(0);
        status.setText("Taşıma başladı...");
        worker.execute(() -> {
            try {
                MoveEngine.move(this, items, (moved, total, folderName, fileName) -> runOnUiThread(() -> {
                    progress.setMax(total);
                    progress.setProgress(moved);
                    status.setText(moved + "/" + total + " taşındı • " + folderName + " • " + fileName);
                }));
                runOnUiThread(() -> {
                    videos.clear();
                    status.setText("Tamamlandı: " + items.size() + " video Movies içine 50'şerli taşındı.");
                    toast("Taşıma tamamlandı");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Taşıma durdu: " + safe(e.getMessage()));
                    moveButton.setEnabled(true);
                    toast("Kalan videolar yerinde bırakıldı");
                });
            }
        });
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private static String safe(String s) { return s == null ? "bilinmeyen hata" : s; }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }
}
