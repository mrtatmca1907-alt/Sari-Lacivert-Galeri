package com.atmaca.videokareleri;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 1907;
    private static final int REQ_NOTIFY = 1908;
    private static final int REQ_MEDIA = 1909;

    private Uri selectedTree;
    private TextView folderText;
    private TextView statusText;
    private ProgressBar progress;
    private Button startButton;
    private Button allVideosButton;
    private boolean pendingAllMode;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!FrameExtractService.ACTION_PROGRESS.equals(intent.getAction())) return;
            String message = intent.getStringExtra(FrameExtractService.EXTRA_MESSAGE);
            int done = intent.getIntExtra(FrameExtractService.EXTRA_DONE, 0);
            int total = intent.getIntExtra(FrameExtractService.EXTRA_TOTAL, 0);
            statusText.setText(message == null ? "Çalışıyor" : message);
            progress.setIndeterminate(total <= 0);
            if (total > 0) {
                progress.setMax(total);
                progress.setProgress(Math.min(done, total));
            }
            boolean finished = intent.getBooleanExtra(FrameExtractService.EXTRA_FINISHED, false);
            if (finished) {
                allVideosButton.setEnabled(true);
                startButton.setEnabled(selectedTree != null);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        String saved = getPreferences(MODE_PRIVATE).getString("tree", null);
        if (saved != null) {
            selectedTree = Uri.parse(saved);
            folderText.setText(selectedTree.toString());
            startButton.setEnabled(true);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }
    }

    private void buildUi() {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Video Kareleri");
        title.setTextSize(28f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("İki kullanım: telefondaki tüm videoları bul veya sadece seçtiğin klasörü işle. Her saniyeden 1 JPEG çıkarılır.");
        desc.setTextSize(16f);
        desc.setPadding(0, pad, 0, pad);
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        allVideosButton = new Button(this);
        allVideosButton.setText("Telefondaki Tüm Videoları Bul ve İşle");
        allVideosButton.setOnClickListener(v -> beginAllMode());
        root.addView(allVideosButton, new LinearLayout.LayoutParams(-1, -2));

        TextView orText = new TextView(this);
        orText.setText("veya");
        orText.setGravity(Gravity.CENTER);
        orText.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(orText, new LinearLayout.LayoutParams(-1, -2));

        Button select = new Button(this);
        select.setText("Klasör Seç");
        select.setOnClickListener(v -> chooseTree());
        root.addView(select, new LinearLayout.LayoutParams(-1, -2));

        folderText = new TextView(this);
        folderText.setText("Klasör seçilmedi");
        folderText.setPadding(0, pad / 2, 0, pad);
        root.addView(folderText, new LinearLayout.LayoutParams(-1, -2));

        startButton = new Button(this);
        startButton.setText("Seçili Klasörü Başlat");
        startButton.setEnabled(false);
        startButton.setOnClickListener(v -> startFolderWork());
        root.addView(startButton, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.setMargins(0, pad, 0, pad / 2);
        root.addView(progress, pp);

        statusText = new TextView(this);
        statusText.setText("Hazır");
        statusText.setTextSize(16f);
        root.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private void chooseTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        selectedTree = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { getContentResolver().takePersistableUriPermission(selectedTree, flags); } catch (Exception ignored) {}
        getPreferences(MODE_PRIVATE).edit().putString("tree", selectedTree.toString()).apply();
        folderText.setText(selectedTree.toString());
        startButton.setEnabled(true);
        statusText.setText("Klasör hazır");
    }

    private void beginAllMode() {
        pendingAllMode = true;
        if (!hasMediaPermission()) {
            requestMediaPermission();
            return;
        }
        continueAllMode();
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQ_MEDIA);
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_MEDIA);
        }
    }

    private void continueAllMode() {
        if (SourceModeRules.needsAllFilesAccess(true, Build.VERSION.SDK_INT) && !Environment.isExternalStorageManager()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
            statusText.setText("Tüm videolar için dosya erişimini aç");
            return;
        }
        pendingAllMode = false;
        Intent i = new Intent(this, FrameExtractService.class);
        i.setAction(FrameExtractService.ACTION_START_ALL);
        startServiceForegroundAware(i);
        markRunning("Telefondaki videolar hazırlanıyor…");
    }

    private void startFolderWork() {
        if (selectedTree == null) return;
        Intent i = new Intent(this, FrameExtractService.class);
        i.setAction(FrameExtractService.ACTION_START);
        i.putExtra(FrameExtractService.EXTRA_TREE_URI, selectedTree.toString());
        startServiceForegroundAware(i);
        markRunning("Seçili klasör hazırlanıyor…");
    }

    private void startServiceForegroundAware(Intent i) {
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void markRunning(String text) {
        allVideosButton.setEnabled(false);
        startButton.setEnabled(false);
        statusText.setText(text);
        progress.setIndeterminate(true);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MEDIA) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            continueAllMode();
        } else {
            pendingAllMode = false;
            Toast.makeText(this, "Tüm videolar için video okuma izni gerekli", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (pendingAllMode && hasMediaPermission()) {
            if (!SourceModeRules.needsAllFilesAccess(true, Build.VERSION.SDK_INT) || Environment.isExternalStorageManager()) {
                continueAllMode();
            }
        }
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(FrameExtractService.ACTION_PROGRESS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    @Override protected void onStop() {
        super.onStop();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
    }
}
