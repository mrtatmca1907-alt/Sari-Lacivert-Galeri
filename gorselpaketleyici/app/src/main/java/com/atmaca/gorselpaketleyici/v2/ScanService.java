package com.atmaca.gorselpaketleyici.v2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScanService extends Service {
    public static final String ACTION_SCAN = "com.atmaca.gorselpaketleyici.v2.SCAN";
    public static final String ACTION_PACK = "com.atmaca.gorselpaketleyici.v2.PACK";
    public static final String ACTION_STOP = "com.atmaca.gorselpaketleyici.v2.STOP";
    public static final String ACTION_PROGRESS = "com.atmaca.gorselpaketleyici.v2.PROGRESS";
    private static final int NOTIF_ID = 1907;
    private static final String CHANNEL = "scan";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private volatile boolean busy;
    private ScanDb db;
    private File outputRoot;

    @Override public void onCreate() {
        super.onCreate();
        db = new ScanDb(this);
        outputRoot = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GorselPaketleri");
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stop.set(true);
            publish("Durdurma istendi. Güvenli noktada duracak...", "Bulunan: " + db.count(), true);
            return START_NOT_STICKY;
        }
        startForeground(NOTIF_ID, notification("Hazır"));
        if (!busy && ACTION_SCAN.equals(action)) runScan();
        else if (!busy && ACTION_PACK.equals(action)) runPack();
        return START_NOT_STICKY;
    }

    private void runScan() {
        busy = true; stop.set(false);
        executor.execute(() -> {
            try {
                db.reset();
                publish("Tarama başladı...", "Bulunan: 0", true);
                if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) scanFileSystem();
                else scanMediaStore();
                long n = db.count();
                publish(stop.get() ? "Tarama durduruldu." : "Tarama tamamlandı.", "Bulunan: " + n, false);
            } catch (Throwable t) {
                publish("Tarama hatası: " + safe(t), "Bulunan: " + db.count(), false);
            } finally {
                busy = false;
                stopForeground(false);
            }
        });
    }

    private void scanFileSystem() {
        Set<String> seen = new HashSet<>();
        File[] roots = new File[]{Environment.getExternalStorageDirectory()};
        for (File root : roots) {
            if (root == null || !root.exists()) continue;
            ArrayDeque<File> q = new ArrayDeque<>(); q.add(root);
            while (!q.isEmpty() && !stop.get()) {
                File f = q.pollFirst();
                String path;
                try { path = f.getCanonicalPath(); } catch (Exception e) { path = f.getAbsolutePath(); }
                if (!seen.add(path)) continue;
                if (PathPolicy.shouldSkipPath(path, outputRoot.getAbsolutePath())) continue;
                if (f.isDirectory()) {
                    File[] kids;
                    try { kids = f.listFiles(); } catch (Throwable t) { kids = null; }
                    if (kids == null) continue;
                    for (File k : kids) if (k != null) q.addLast(k);
                } else if (f.isFile() && f.length() > 0 && PathPolicy.isImageName(f.getName())) {
                    if (db.add(f.getAbsolutePath(), f.getName())) maybeProgress();
                }
            }
        }
    }

    private void scanMediaStore() {
        ContentResolver cr = getContentResolver();
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATA};
        Cursor c = null;
        try {
            c = cr.query(uri, projection, null, null, MediaStore.Images.Media.DISPLAY_NAME + " COLLATE NOCASE ASC");
            if (c == null) return;
            int iName = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
            int iData = c.getColumnIndex(MediaStore.Images.Media.DATA);
            int iId = c.getColumnIndex(MediaStore.Images.Media._ID);
            while (c.moveToNext() && !stop.get()) {
                String name = iName >= 0 ? c.getString(iName) : null;
                String path = iData >= 0 ? c.getString(iData) : null;
                if (path == null && iId >= 0) path = Uri.withAppendedPath(uri, String.valueOf(c.getLong(iId))).toString();
                if (name != null && path != null && !PathPolicy.shouldSkipPath(path, outputRoot.getAbsolutePath()) && db.add(path, name)) maybeProgress();
            }
        } finally { if (c != null) c.close(); }
    }

    private void maybeProgress() {
        long n = db.count();
        if (n < 50 || n % 250 == 0) publish("Taranıyor...", "Bulunan: " + n, true);
    }

    private void runPack() {
        busy = true; stop.set(false);
        executor.execute(() -> {
            long total = db.count();
            if (total == 0) {
                publish("Önce TARA ile görselleri bul.", "Bulunan: 0", false);
                busy = false; stopForeground(false); return;
            }
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
            File session = new File(outputRoot, "Oturum_" + stamp);
            if (!session.mkdirs() && !session.isDirectory()) {
                publish("Çıktı klasörü oluşturulamadı.", "Bulunan: " + total, false);
                busy = false; stopForeground(false); return;
            }
            long done = 0, failed = 0;
            Cursor c = db.ordered();
            try {
                while (c.moveToNext() && !stop.get()) {
                    String src = c.getString(0); String name = c.getString(1);
                    int packet = (int)(done / 50L) + 1;
                    File dir = new File(session, String.format(Locale.ROOT, "Paket_%04d", packet));
                    if (!dir.exists()) dir.mkdirs();
                    boolean ok = moveOne(src, name, dir);
                    if (ok) done++; else failed++;
                    if ((done + failed) < 30 || (done + failed) % 25 == 0) {
                        publish("Paketleniyor...", "Taşınan: " + done + " / " + total + (failed > 0 ? "  Hata: " + failed : ""), true);
                    }
                }
            } finally { c.close(); }
            String msg = stop.get() ? "Durduruldu." : "Tamamlandı.";
            publish(msg, "Taşınan: " + done + " / " + total + (failed > 0 ? "  Hata: " + failed : ""), false);
            busy = false; stopForeground(false);
        });
    }

    private boolean moveOne(String source, String name, File dir) {
        if (source.startsWith("content://")) return false;
        File src = new File(source);
        if (!src.isFile()) return false;
        File dst = uniqueDestination(dir, name);
        try {
            if (src.renameTo(dst)) { rescan(src, dst); return true; }
            copyFile(src, dst);
            if (!src.delete()) { try { dst.delete(); } catch (Exception ignored) {} return false; }
            rescan(src, dst); return true;
        } catch (Throwable t) {
            try { if (dst.exists()) dst.delete(); } catch (Throwable ignored) {}
            return false;
        }
    }

    private File uniqueDestination(File dir, String name) {
        File d = new File(dir, name); if (!d.exists()) return d;
        int dot = name.lastIndexOf('.'); String base = dot > 0 ? name.substring(0,dot) : name; String ext = dot > 0 ? name.substring(dot) : "";
        int n = 2; do { d = new File(dir, base + " (" + n++ + ")" + ext); } while (d.exists()); return d;
    }

    private void copyFile(File src, File dst) throws Exception {
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst);
             FileChannel ic = in.getChannel(); FileChannel oc = out.getChannel()) {
            long size = ic.size(), pos = 0;
            while (pos < size) { long moved = ic.transferTo(pos, Math.min(8L * 1024 * 1024, size - pos), oc); if (moved <= 0) throw new java.io.IOException("Kopyalama durdu"); pos += moved; }
            out.getFD().sync();
        }
    }

    private void rescan(File oldFile, File newFile) {
        try { MediaScannerConnection.scanFile(this, new String[]{oldFile.getAbsolutePath(), newFile.getAbsolutePath()}, null, null); } catch (Throwable ignored) {}
    }

    private void publish(String s, String count, boolean active) {
        Intent i = new Intent(ACTION_PROGRESS).setPackage(getPackageName());
        i.putExtra("status", s); i.putExtra("count", count); i.putExtra("busy", active);
        sendBroadcast(i);
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, notification(s + "  " + count));
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_gallery).setContentTitle("ATMACA Görsel Paketleyici").setContentText(text).setContentIntent(pi).setOngoing(busy).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Görsel tarama", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE); if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private String safe(Throwable t) { String m = t.getMessage(); return m == null || m.trim().isEmpty() ? t.getClass().getSimpleName() : m; }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { stop.set(true); executor.shutdownNow(); if (db != null) db.close(); super.onDestroy(); }
}
