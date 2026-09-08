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
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 1907;
    private static final int REQ_NOTIFY = 1908;
    private Uri selectedTree;
    private TextView folderText;
    private TextView statusText;
    private ProgressBar progress;
    private Button startButton;

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
            startButton.setEnabled(finished || selectedTree != null);
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
        desc.setText("Seçilen klasördeki videolardan her saniye 1 JPEG çıkarır. Başarılı video silinir; hata veren video HATA klasörüne taşınır.");
        desc.setTextSize(16f);
        desc.setPadding(0, pad, 0, pad);
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        Button select = new Button(this);
        select.setText("Klasör Seç");
        select.setOnClickListener(v -> chooseTree());
        root.addView(select, new LinearLayout.LayoutParams(-1, -2));

        folderText = new TextView(this);
        folderText.setText("Klasör seçilmedi");
        folderText.setPadding(0, pad / 2, 0, pad);
        root.addView(folderText, new LinearLayout.LayoutParams(-1, -2));

        startButton = new Button(this);
        startButton.setText("Başlat");
        startButton.setEnabled(false);
        startButton.setOnClickListener(v -> startWork());
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
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
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

    private void startWork() {
        if (selectedTree == null) return;
        Intent i = new Intent(this, FrameExtractService.class);
        i.setAction(FrameExtractService.ACTION_START);
        i.putExtra(FrameExtractService.EXTRA_TREE_URI, selectedTree.toString());
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        startButton.setEnabled(false);
        statusText.setText("Videolar hazırlanıyor…");
        progress.setIndeterminate(true);
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
