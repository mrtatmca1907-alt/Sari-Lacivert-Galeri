package com.atmaca.zippaketleyici;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipService extends Service {
    public static final String ACTION_START = "com.atmaca.zippaketleyici.START";
    public static final String ACTION_CANCEL = "com.atmaca.zippaketleyici.CANCEL";
    public static final String ACTION_PROGRESS = "com.atmaca.zippaketleyici.PROGRESS";
    public static final String EXTRA_SOURCE_PATH = "source_path";

    private static final String CHANNEL = "zip_work";
    private static final int NOTIFICATION_ID = 1907;
    private static final String PREFS = "zip_state";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private volatile boolean running;
    private long files;
    private long dirs;
    private long bytes;
    private long entries;
    private long lastUi;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "ZIP paketleme", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Uzun süren ZIP paketleme işlemi");
            nm.createNotificationChannel(ch);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancel.set(true);
            setState(true, "Durduruluyor…", "", files, dirs, bytes);
            return START_NOT_STICKY;
        }

        if (intent == null || !ACTION_START.equals(intent.getAction()) || running) return START_NOT_STICKY;

        String sourcePath = intent.getStringExtra(EXTRA_SOURCE_PATH);
        if (sourcePath == null || sourcePath.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        running = true;
        cancel.set(false);
        files = dirs = bytes = entries = 0;
        startForeground(NOTIFICATION_ID, notification("Hazırlanıyor", "0 dosya"));
        setState(true, "Paketleme başlıyor…", "", 0, 0, 0);

        executor.execute(() -> runJob(new File(sourcePath)));
        return START_NOT_STICKY;
    }

    private void runJob(File source) {
        PowerManager.WakeLock wake = null;
        File output = null;
        try {
            source = source.getCanonicalFile();
            if (!source.isDirectory() || !source.canRead()) throw new IOException("Kaynak klasör okunamıyor");

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ATMACA:Zip");
                wake.acquire();
            }

            File outDir = new File(Environment.getExternalStorageDirectory(), "ATMACA_ZIP");
            if (!outDir.exists() && !outDir.mkdirs()) throw new IOException("ATMACA_ZIP klasörü oluşturulamadı");

            output = chooseOutput(outDir, source);
            writeZip(source, output);
            if (cancel.get()) throw new Cancelled();

            setState(true, "ZIP doğrulanıyor…", output.getName(), files, dirs, bytes);
            updateNotification("ZIP doğrulanıyor", files + " dosya");

            verifyZip(output, entries, files, bytes);

            setState(false, "TAMAMLANDI ✓ ZIP sağlam", output.getAbsolutePath(), files, dirs, bytes);
            updateNotification("Tamamlandı", files + " dosya • ZIP doğrulandı");
        } catch (Cancelled e) {
            if (output != null) safeDelete(output);
            setState(false, "DURDURULDU • yarım ZIP silindi", "", files, dirs, bytes);
            updateNotification("Durduruldu", "Yarım ZIP silindi");
        } catch (Throwable e) {
            if (output != null) safeDelete(output);
            String msg = e.getMessage();
            if (msg == null || msg.trim().isEmpty()) msg = e.getClass().getSimpleName();
            setState(false, "HATA • ZIP tamamlanmadı", msg, files, dirs, bytes);
            updateNotification("Hata", shorten(msg, 70));
        } finally {
            if (wake != null && wake.isHeld()) wake.release();
            running = false;
            stopForeground(false);
            stopSelf();
        }
    }

    private File chooseOutput(File outDir, File source) throws IOException {
        String rootPath = Environment.getExternalStorageDirectory().getCanonicalPath();
        String base;
        if (source.getCanonicalPath().equals(rootPath)) base = "DAHILI_DEPOLAMA";
        else {
            base = ZipNames.safeSegment(source.getName());
            if (base.isEmpty() || "_".equals(base)) base = "ATMACA";
        }

        File candidate = new File(outDir, base + ".zip");
        int n = 2;
        while (candidate.exists()) {
            candidate = new File(outDir, base + " (" + n + ").zip");
            n++;
        }
        return candidate.getCanonicalFile();
    }

    private void writeZip(File source, File output) throws Exception {
        String sourceCanonical = source.getCanonicalPath();
        String outputCanonical = output.getCanonicalPath();

        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(new Node(source, ""));

        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(output), 1024 * 1024))) {
            zip.setLevel(Deflater.NO_COMPRESSION);

            while (!stack.isEmpty()) {
                if (cancel.get()) throw new Cancelled();

                Node dir = stack.pop();
                File[] children = dir.file.listFiles();
                if (children == null) throw new IOException("Klasör okunamadı: " + dir.file.getAbsolutePath());
                Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

                Map<String, Integer> names = new HashMap<>();
                for (File child : children) {
                    if (cancel.get()) throw new Cancelled();

                    File canonical;
                    try { canonical = child.getCanonicalFile(); }
                    catch (IOException e) { throw new IOException("Dosya yolu okunamadı: " + child.getAbsolutePath(), e); }

                    String childCanonical = canonical.getCanonicalPath();
                    if (childCanonical.equals(outputCanonical)) continue;
                    if (!insideSource(sourceCanonical, childCanonical)) continue;

                    String base = ZipNames.safeSegment(child.getName());
                    int seen = names.containsKey(base) ? names.get(base) + 1 : 1;
                    names.put(base, seen);
                    String unique = ZipNames.duplicateName(base, seen);
                    String relative = dir.relative.isEmpty() ? unique : dir.relative + "/" + unique;

                    if (canonical.isDirectory()) {
                        ZipEntry ze = new ZipEntry(relative + "/");
                        long lm = canonical.lastModified();
                        if (lm > 0) ze.setTime(lm);
                        zip.putNextEntry(ze);
                        zip.closeEntry();
                        entries++;
                        dirs++;
                        stack.push(new Node(canonical, relative));
                        publish(relative);
                    } else if (canonical.isFile()) {
                        writeFile(zip, canonical, relative);
                    }
                }
            }
            zip.finish();
        }
    }

    private boolean insideSource(String sourceCanonical, String childCanonical) {
        if (childCanonical.equals(sourceCanonical)) return true;
        String prefix = sourceCanonical.endsWith(File.separator)
                ? sourceCanonical : sourceCanonical + File.separator;
        return childCanonical.startsWith(prefix);
    }

    private void writeFile(ZipOutputStream zip, File file, String relative) throws Exception {
        if (!file.canRead()) throw new IOException("Dosya okunamıyor: " + file.getAbsolutePath());

        ZipEntry ze = new ZipEntry(relative);
        long lm = file.lastModified();
        if (lm > 0) ze.setTime(lm);
        zip.putNextEntry(ze);

        try (InputStream in = new BufferedInputStream(new FileInputStream(file), 1024 * 1024)) {
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (cancel.get()) throw new Cancelled();
                zip.write(buffer, 0, n);
                bytes += n;
                if (System.currentTimeMillis() - lastUi > 350) publish(relative);
            }
        } catch (Throwable t) {
            try { zip.closeEntry(); } catch (Throwable ignored) {}
            throw t;
        }

        zip.closeEntry();
        files++;
        entries++;
        publish(relative);
    }

    private void verifyZip(File output, long expectedEntries, long expectedFiles, long expectedBytes) throws Exception {
        long gotEntries = 0;
        long gotFiles = 0;
        long gotBytes = 0;

        try (ZipFile zf = new ZipFile(output)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            byte[] buffer = new byte[1024 * 1024];
            long last = 0;

            while (en.hasMoreElements()) {
                if (cancel.get()) throw new Cancelled();
                ZipEntry e = en.nextElement();
                gotEntries++;

                if (!e.isDirectory()) {
                    gotFiles++;
                    try (InputStream in = new BufferedInputStream(zf.getInputStream(e), 1024 * 1024)) {
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            gotBytes += n;
                            if (System.currentTimeMillis() - last > 700) {
                                setState(true, "ZIP doğrulanıyor…", e.getName(), files, dirs, bytes);
                                last = System.currentTimeMillis();
                            }
                        }
                    }
                }
            }
        }

        if (gotEntries != expectedEntries || gotFiles != expectedFiles || gotBytes != expectedBytes) {
            throw new IOException("Doğrulama sayıları eşleşmedi");
        }
    }

    private void publish(String current) {
        lastUi = System.currentTimeMillis();
        setState(true, "Paketleniyor…", current, files, dirs, bytes);
        updateNotification("Paketleniyor", files + " dosya • " + prettyBytes(bytes));
    }

    private void setState(boolean isRunning, String status, String current, long f, long d, long b) {
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("running", isRunning)
                .putString("status", status)
                .putString("current", current == null ? "" : current)
                .putLong("files", f)
                .putLong("dirs", d)
                .putLong("bytes", b);
        e.apply();

        Intent i = new Intent(ACTION_PROGRESS);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private Notification notification(String title, String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, ZipService.class);
        stop.setAction(ACTION_CANCEL);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(com.atmaca.zippaketleyici.R.drawable.ic_zip)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(running)
                .addAction(new Notification.Action.Builder(null, "Durdur", stopPi).build())
                .build();
    }

    private void updateNotification(String title, String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, notification(title, text));
    }

    private void safeDelete(File file) {
        try { if (file.exists()) file.delete(); } catch (Throwable ignored) {}
    }

    private String prettyBytes(long b) {
        if (b >= 1024L * 1024 * 1024) return String.format(Locale.US, "%.2f GB", b / (1024d * 1024 * 1024));
        if (b >= 1024L * 1024) return String.format(Locale.US, "%.1f MB", b / (1024d * 1024));
        if (b >= 1024L) return String.format(Locale.US, "%.1f KB", b / 1024d);
        return b + " B";
    }

    private String shorten(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    @Override public void onDestroy() {
        cancel.set(true);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private static final class Node {
        final File file;
        final String relative;
        Node(File file, String relative) {
            this.file = file;
            this.relative = relative;
        }
    }

    private static final class Cancelled extends IOException {}
}
