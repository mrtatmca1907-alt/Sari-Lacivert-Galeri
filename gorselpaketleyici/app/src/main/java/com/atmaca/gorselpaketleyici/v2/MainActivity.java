package com.atmaca.gorselpaketleyici.v2;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_MEDIA = 41;
    private TextView status;
    private TextView count;
    private ProgressBar progress;
    private boolean receiverRegistered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ScanService.ACTION_PROGRESS.equals(intent.getAction())) return;
            status.setText(intent.getStringExtra("status"));
            count.setText(intent.getStringExtra("count"));
            progress.setIndeterminate(intent.getBooleanExtra("busy", false));
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        showPermissionState();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(ScanService.ACTION_PROGRESS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
        receiverRegistered = true;
    }

    @Override protected void onStop() {
        if (receiverRegistered) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        showPermissionState();
    }

    private void buildUi() {
        int navy = Color.rgb(7,26,61);
        int yellow = Color.rgb(247,198,0);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(navy);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(root);

        TextView title = text("ATMACA GÖRSEL PAKETLEYİCİ", 24, yellow, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(-1, -2, 0, 0, 0, 18));

        TextView info = text("Telefondaki erişilebilir görselleri bulur, ada göre sıralar ve 50'şerli klasörlere taşır. Tarama ekran döndürülse de servis içinde devam eder.", 15, Color.WHITE, false);
        root.addView(info, lp(-1, -2, 0, 0, 0, 18));

        status = text("Hazır", 16, Color.WHITE, true);
        root.addView(status, lp(-1,-2,0,0,0,8));
        count = text("Bulunan: 0", 15, yellow, true);
        root.addView(count, lp(-1,-2,0,0,0,14));

        progress = new ProgressBar(this);
        root.addView(progress, lp(-1, dp(8),0,0,0,18));
        progress.setIndeterminate(false);

        Button permission = button("DEPOLAMA İZNİ", yellow, navy);
        permission.setOnClickListener(v -> requestStorageAccess());
        root.addView(permission, lp(-1,dp(56),0,0,0,12));

        Button scan = button("TARA", yellow, navy);
        scan.setOnClickListener(v -> startAction(ScanService.ACTION_SCAN));
        root.addView(scan, lp(-1,dp(56),0,0,0,12));

        Button pack = button("50'Lİ KLASÖRLERE TAŞI", Color.WHITE, navy);
        pack.setOnClickListener(v -> startAction(ScanService.ACTION_PACK));
        root.addView(pack, lp(-1,dp(56),0,0,0,12));

        Button stop = button("DURDUR", Color.rgb(210,60,60), Color.WHITE);
        stop.setOnClickListener(v -> startAction(ScanService.ACTION_STOP));
        root.addView(stop, lp(-1,dp(56),0,0,0,18));

        TextView note = text("Güvenlik: Android/data, Android/obb, .thumbnails, .cache ve oluşturulan çıktı klasörü taranmaz. Taşıma öncesi TARA ile sayı kontrol edilmelidir.", 13, Color.LTGRAY, false);
        root.addView(note);
        setContentView(scroll);
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                return;
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQ_MEDIA);
        } else if (Build.VERSION.SDK_INT < 33 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_MEDIA);
        } else {
            status.setText("Depolama erişimi hazır.");
        }
    }

    private void showPermissionState() {
        boolean allFiles = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
        boolean media = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        if (allFiles) status.setText("Tam dosya erişimi hazır.");
        else if (media) status.setText("Medya erişimi hazır; tam tarama için 'Depolama İzni'nden tüm dosya erişimini aç.");
        else status.setText("Depolama erişimi gerekli.");
    }

    private void startAction(String action) {
        Intent i = new Intent(this, ScanService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }
    private Button button(String s, int bg, int fg) {
        Button b = new Button(this); b.setText(s); b.setTextSize(15); b.setTextColor(fg); b.setBackgroundColor(bg); b.setAllCaps(false); return b;
    }
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
