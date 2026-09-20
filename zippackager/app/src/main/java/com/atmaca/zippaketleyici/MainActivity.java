package com.atmaca.zippaketleyici;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {
    private static final int REQ_DIR = 1907;
    private static final int REQ_STORAGE = 1908;
    private static final String PREFS = "zip_state";

    private String sourcePath;
    private boolean waitingForAllFiles;
    private TextView sourceView;
    private TextView statusView;
    private TextView progressView;
    private Button selectButton;
    private Button startButton;
    private Button cancelButton;

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refreshState();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(0, 30, 72));
        buildUi();

        sourcePath = getSharedPreferences(PREFS, MODE_PRIVATE).getString("source_path", null);
        updateSourceLabel();
        refreshState();

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
        }
    }

    private void buildUi() {
        int navy = Color.rgb(0, 42, 92);
        int yellow = Color.rgb(255, 215, 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(22));
        root.setBackgroundColor(navy);

        TextView title = new TextView(this);
        title.setText("ATMACA ZIP PAKETLEYİCİ");
        title.setTextSize(26);
        title.setTextColor(yellow);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = text("Dahili depolamanın kökü dahil istediğin klasörü seçer. Kaynak dosyalara dokunmaz; silmez ve taşımaz.");
        info.setTextSize(16);
        root.addView(info);

        sourceView = text("Klasör seçilmedi");
        sourceView.setPadding(0, dp(18), 0, dp(10));
        root.addView(sourceView);

        selectButton = button("1) KLASÖR SEÇ");
        selectButton.setOnClickListener(v -> chooseFolder());
        root.addView(selectButton);

        startButton = button("2) ZIP OLUŞTUR");
        startButton.setOnClickListener(v -> startZip());
        root.addView(startButton);

        cancelButton = button("DURDUR");
        cancelButton.setOnClickListener(v -> {
            Intent i = new Intent(this, ZipService.class);
            i.setAction(ZipService.ACTION_CANCEL);
            startService(i);
        });
        root.addView(cancelButton);

        statusView = text("Hazır");
        statusView.setTextSize(18);
        statusView.setTextColor(yellow);
        statusView.setPadding(0, dp(22), 0, dp(8));
        root.addView(statusView);

        progressView = text("");
        progressView.setTextSize(15);
        root.addView(progressView);

        TextView note = text("ZIP kayıt yeri: Dahili depolama / ATMACA_ZIP\n\nİşlem bittikten sonra ZIP baştan sona tekrar okunur ve CRC kontrolü yapılır. Doğrulama geçmeden tamamlanmış sayılmaz.");
        note.setPadding(0, dp(24), 0, 0);
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private TextView text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }

    private void chooseFolder() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            waitingForAllFiles = true;
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_STORAGE);
            } catch (Exception e) {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_STORAGE);
            }
            return;
        }

        if (Build.VERSION.SDK_INT < 30 &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }

        launchPicker();
    }

    private void launchPicker() {
        waitingForAllFiles = false;
        startActivityForResult(new Intent(this, DirectoryPickerActivity.class), REQ_DIR);
    }

    private void startZip() {
        if (sourcePath == null || sourcePath.isEmpty()) {
            Toast.makeText(this, "Önce klasör seç.", Toast.LENGTH_SHORT).show();
            return;
        }

        File f = new File(sourcePath);
        if (!f.isDirectory() || !f.canRead()) {
            Toast.makeText(this, "Seçilen klasör okunamıyor.", Toast.LENGTH_LONG).show();
            return;
        }

        Intent service = new Intent(this, ZipService.class);
        service.setAction(ZipService.ACTION_START);
        service.putExtra(ZipService.EXTRA_SOURCE_PATH, sourcePath);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        refreshState();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_DIR && resultCode == RESULT_OK && data != null) {
            String path = data.getStringExtra(DirectoryPickerActivity.EXTRA_PATH);
            if (path != null && !path.isEmpty()) {
                sourcePath = path;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("source_path", path).apply();
                updateSourceLabel();
                refreshState();
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchPicker();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (waitingForAllFiles && Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            launchPicker();
        }
        refreshState();
    }

    private void updateSourceLabel() {
        if (sourceView == null) return;
        if (sourcePath == null || sourcePath.isEmpty()) {
            sourceView.setText("Klasör seçilmedi");
            return;
        }
        File f = new File(sourcePath);
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        sourceView.setText(sourcePath.equals(root)
                ? "Seçili klasör: DAHİLİ DEPOLAMA KÖKÜ\n" + sourcePath
                : "Seçili klasör: " + f.getName() + "\n" + sourcePath);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(ZipService.ACTION_PROGRESS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(progressReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(progressReceiver, f);
        refreshState();
    }

    @Override protected void onStop() {
        super.onStop();
        try { unregisterReceiver(progressReceiver); } catch (Exception ignored) {}
    }

    private void refreshState() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean running = p.getBoolean("running", false);
        String status = p.getString("status", "Hazır");
        long files = p.getLong("files", 0);
        long dirs = p.getLong("dirs", 0);
        long bytes = p.getLong("bytes", 0);
        String current = p.getString("current", "");

        if (statusView != null) statusView.setText(status);
        if (progressView != null) {
            progressView.setText("Dosya: " + files + "   Klasör: " + dirs +
                    "\nİşlenen: " + prettyBytes(bytes) +
                    (current.isEmpty() ? "" : "\nŞu an: " + current));
        }
        if (selectButton != null) selectButton.setEnabled(!running);
        if (startButton != null) startButton.setEnabled(!running && sourcePath != null);
        if (cancelButton != null) cancelButton.setEnabled(running);
    }

    private String prettyBytes(long b) {
        if (b >= 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.2f GB", b / (1024d * 1024 * 1024));
        if (b >= 1024L * 1024) return String.format(java.util.Locale.US, "%.1f MB", b / (1024d * 1024));
        if (b >= 1024L) return String.format(java.util.Locale.US, "%.1f KB", b / 1024d);
        return b + " B";
    }
}
