package com.atmaca.imagemover;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int STORAGE_REQUEST = 1907;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            if (hasStorageAccess()) {
                statusView.setText(MoveService.getStatusText());
                startButton.setText(MoveService.isRunning() ? "ÇALIŞIYOR" : "TEKRAR ÇALIŞTIR");
                startButton.setEnabled(!MoveService.isRunning());
            }
            handler.postDelayed(this, 350L);
        }
    };

    private TextView statusView;
    private TextView targetView;
    private Button permissionButton;
    private Button startButton;
    private boolean settingsOpened;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("ATMACA 1907");
        title.setTextSize(30f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        targetView = new TextView(this);
        targetView.setText("HEDEF: Pictures/1907\nAynı isimli görsel varsa yenisi onun üzerine yazılır.");
        targetView.setTextSize(15f);
        targetView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams targetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        targetParams.topMargin = dp(18);
        root.addView(targetView, targetParams);

        statusView = new TextView(this);
        statusView.setTextSize(20f);
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(26);
        root.addView(statusView, statusParams);

        permissionButton = new Button(this);
        permissionButton.setText("TÜM DOSYALARA ERİŞİMİ AÇ");
        permissionButton.setOnClickListener(v -> openStorageAccessSettings());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.topMargin = dp(24);
        root.addView(permissionButton, buttonParams);

        startButton = new Button(this);
        startButton.setText("TEKRAR ÇALIŞTIR");
        startButton.setOnClickListener(v -> startMover());
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        startParams.topMargin = dp(12);
        root.addView(startButton, startParams);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureAccessAndStart();
        handler.removeCallbacks(statusTicker);
        handler.post(statusTicker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(statusTicker);
        super.onPause();
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureAccessAndStart() {
        if (hasStorageAccess()) {
            permissionButton.setVisibility(View.GONE);
            startButton.setVisibility(View.VISIBLE);
            if (!MoveService.isRunning()) {
                startMover();
            }
            return;
        }

        startButton.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            statusView.setText("İzin bekleniyor\n\nTüm dosyalara erişimi aç.");
            permissionButton.setVisibility(View.VISIBLE);
            if (!settingsOpened) {
                settingsOpened = true;
                openStorageAccessSettings();
            }
        } else {
            statusView.setText("Depolama izni bekleniyor");
            permissionButton.setVisibility(View.GONE);
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, STORAGE_REQUEST);
        }
    }

    private void openStorageAccessSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, STORAGE_REQUEST);
            return;
        }

        Intent appIntent = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        try {
            startActivity(appIntent);
        } catch (ActivityNotFoundException unavailable) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void startMover() {
        if (!hasStorageAccess()) {
            ensureAccessAndStart();
            return;
        }
        statusView.setText("Başlatılıyor…");
        Intent serviceIntent = new Intent(this, MoveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_REQUEST) {
            ensureAccessAndStart();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
