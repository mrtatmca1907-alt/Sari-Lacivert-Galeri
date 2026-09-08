package com.atmaca.insgetorganizer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int LEGACY_STORAGE_REQUEST = 1907;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView permissionStatus;
    private TextView counters;
    private TextView statusText;
    private Button permissionButton;
    private Button startButton;
    private volatile boolean running;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionStatus = findViewById(R.id.permissionStatus);
        counters = findViewById(R.id.counters);
        statusText = findViewById(R.id.statusText);
        permissionButton = findViewById(R.id.permissionButton);
        startButton = findViewById(R.id.startButton);

        permissionButton.setOnClickListener(v -> requestStorageAccess());
        startButton.setOnClickListener(v -> startOrganizing());

        updatePermissionUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionUi();
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, LEGACY_STORAGE_REQUEST);
        }
    }

    private void updatePermissionUi() {
        boolean granted = hasStorageAccess();
        permissionStatus.setText(granted ? R.string.permission_ready : R.string.permission_needed);
        permissionButton.setVisibility(granted ? View.GONE : View.VISIBLE);
        startButton.setEnabled(granted && !running);
    }

    private void startOrganizing() {
        if (running) return;
        if (!hasStorageAccess()) {
            requestStorageAccess();
            return;
        }

        running = true;
        startButton.setEnabled(false);
        permissionButton.setEnabled(false);
        counters.setText("Taşınan: 0   Atlanan: 0   Hata: 0");
        statusText.setText("Düzenleniyor...");

        File storage = Environment.getExternalStorageDirectory();
        File picturesInsget = new File(storage, "Pictures/Insget");
        File moviesInsget = new File(storage, "Movies/Insget");

        executor.execute(() -> {
            Organizer organizer = new Organizer();
            Organizer.Result finalResult = organizer.organize(
                    picturesInsget,
                    moviesInsget,
                    (current, message) -> runOnUiThread(() -> {
                        showCounters(current);
                        statusText.setText(message);
                    })
            );

            runOnUiThread(() -> {
                showCounters(finalResult);
                statusText.setText("Bitti");
                running = false;
                permissionButton.setEnabled(true);
                updatePermissionUi();
                Toast.makeText(this,
                        "Bitti: " + finalResult.moved + " dosya taşındı",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void showCounters(Organizer.Result result) {
        counters.setText("Taşınan: " + result.moved
                + "   Atlanan: " + result.skipped
                + "   Hata: " + result.errors);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
