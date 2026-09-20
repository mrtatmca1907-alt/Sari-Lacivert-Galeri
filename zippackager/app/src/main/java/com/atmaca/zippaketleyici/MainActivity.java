package com.atmaca.zippaketleyici;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 1907;
    private static final int REQ_ZIP = 1908;
    private static final String PREFS = "zip_state";

    private Uri sourceTree;
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

        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString("source_uri", null);
        if (saved != null && !saved.isEmpty()) {
            sourceTree = Uri.parse(saved);
            sourceView.setText("Seçili klasör: " + displayName(sourceTree));
        }
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

        TextView info = text("Seçtiğin klasörü ve içindeki tüm alt klasörleri tek ZIP yapar. Kaynak dosyalara dokunmaz; silmez ve taşımaz.");
        info.setTextSize(16);
        root.addView(info);

        sourceView = text("Klasör seçilmedi");
        sourceView.setPadding(0, dp(18), 0, dp(10));
        root.addView(sourceView);

        selectButton = button("1) KLASÖR SEÇ");
        selectButton.setOnClickListener(v -> chooseFolder());
        root.addView(selectButton);

        startButton = button("2) ZIP OLUŞTUR");
        startButton.setOnClickListener(v -> chooseOutput());
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

        TextView note = text("Bütünlük kontrolü: işlem bittikten sonra ZIP baştan sona tekrar okunur ve CRC kontrolü yapılır. Doğrulama geçmeden işlem tamamlanmış sayılmaz.");
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
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    private void chooseOutput() {
        if (sourceTree == null) {
            Toast.makeText(this, "Önce klasör seç.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/zip");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE, ZipNames.safeSegment(displayName(sourceTree)) + ".zip");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_ZIP);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_TREE) {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}
            sourceTree = uri;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("source_uri", uri.toString()).apply();
            sourceView.setText("Seçili klasör: " + displayName(uri));
        } else if (requestCode == REQ_ZIP) {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}

            Intent service = new Intent(this, ZipService.class);
            service.setAction(ZipService.ACTION_START);
            service.putExtra(ZipService.EXTRA_SOURCE, sourceTree.toString());
            service.putExtra(ZipService.EXTRA_OUTPUT, uri.toString());
            service.putExtra(ZipService.EXTRA_NAME, displayName(sourceTree));
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            refreshState();
        }
    }

    private String displayName(Uri treeUri) {
        try {
            Uri docUri = treeUri;
            if (DocumentsContract.isTreeUri(treeUri)) {
                String id = DocumentsContract.getTreeDocumentId(treeUri);
                docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
            }
            try (Cursor c = getContentResolver().query(docUri,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    String n = c.getString(0);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Exception ignored) {}
        return "ATMACA";
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
        if (startButton != null) startButton.setEnabled(!running && sourceTree != null);
        if (cancelButton != null) cancelButton.setEnabled(running);
    }

    private String prettyBytes(long b) {
        if (b >= 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.2f GB", b / (1024d * 1024 * 1024));
        if (b >= 1024L * 1024) return String.format(java.util.Locale.US, "%.1f MB", b / (1024d * 1024));
        if (b >= 1024L) return String.format(java.util.Locale.US, "%.1f KB", b / 1024d);
        return b + " B";
    }
}
