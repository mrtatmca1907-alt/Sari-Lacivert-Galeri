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
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int STORAGE_REQUEST = 1907;

    private TextView statusView;
    private Button permissionButton;
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

        statusView = new TextView(this);
        statusView.setTextSize(17f);
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(22);
        root.addView(statusView, statusParams);

        permissionButton = new Button(this);
        permissionButton.setText("TÜM DOSYALARA ERİŞİMİ AÇ");
        permissionButton.setOnClickListener(v -> openStorageAccessSettings());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.topMargin = dp(22);
        root.addView(permissionButton, buttonParams);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureAccessAndStart();
    }

    private void ensureAccessAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                showRunningAndStart();
            } else {
                statusView.setText("İlk çalıştırmada Android'in ‘Tüm dosyalara erişim’ iznini bir kez aç.\nİzin verince taşıma otomatik başlayacak.");
                permissionButton.setVisibility(View.VISIBLE);
                if (!settingsOpened) {
                    settingsOpened = true;
                    openStorageAccessSettings();
                }
            }
            return;
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            showRunningAndStart();
        } else {
            statusView.setText("Depolama izni gerekiyor. İzin verince taşıma otomatik başlayacak.");
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

    private void showRunningAndStart() {
        permissionButton.setVisibility(View.GONE);
        statusView.setText("Görseller bulunur bulunmaz Pictures/1907 klasörüne taşınıyor.\nAynı isimli dosyalar tek dosyada birleşir.");
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
