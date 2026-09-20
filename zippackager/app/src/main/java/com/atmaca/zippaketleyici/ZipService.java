package com.atmaca.zippaketleyici;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.provider.DocumentsContract;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipService extends Service {
    public static final String ACTION_START = "com.atmaca.zippaketleyici.START";
    public static final String ACTION_CANCEL = "com.atmaca.zippaketleyici.CANCEL";
    public static final String ACTION_PROGRESS = "com.atmaca.zippaketleyici.PROGRESS";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_OUTPUT = "output";
    public static final String EXTRA_NAME = "name";

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

        String s = intent.getStringExtra(EXTRA_SOURCE);
        String o = intent.getStringExtra(EXTRA_OUTPUT);
        String n = intent.getStringExtra(EXTRA_NAME);
        if (s == null || o == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        running = true;
        cancel.set(false);
        files = dirs = bytes = entries = 0;
        startForeground(NOTIFICATION_ID, notification("Hazırlanıyor", "0 dosya"));
        setState(true, "Paketleme başlıyor…", "", 0, 0, 0);

        Uri source = Uri.parse(s);
        Uri output = Uri.parse(o);
        String displayName = (n == null || n.isEmpty()) ? "ATMACA" : n;
        executor.execute(() -> runJob(source, output, displayName));
        return START_NOT_STICKY;
    }

    private void runJob(Uri sourceTree, Uri outputUri, String displayName) {
        PowerManager.WakeLock wake = null;
        boolean success = false;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ATMACA:Zip");
                wake.acquire();
            }

            writeZip(sourceTree, outputUri);
            if (cancel.get()) throw new Cancelled();

            setState(true, "ZIP doğrulanıyor…", "", files, dirs, bytes);
            updateNotification("ZIP doğrulanıyor", files + " dosya");

            verifyZip(outputUri, entries, files, bytes);
            if (!hasValidEndRecord(outputUri)) throw new IOException("ZIP merkez dizin sonu bulunamadı");

            success = true;
            setState(false, "TAMAMLANDI ✓ ZIP sağlam", "", files, dirs, bytes);
            updateNotification("Tamamlandı", files + " dosya • ZIP doğrulandı");
        } catch (Cancelled e) {
            deleteOutput(outputUri);
            setState(false, "DURDURULDU • yarım ZIP silindi", "", files, dirs, bytes);
            updateNotification("Durduruldu", "Yarım ZIP silindi");
        } catch (Throwable e) {
            deleteOutput(outputUri);
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

    private void writeZip(Uri sourceTree, Uri outputUri) throws Exception {
        ContentResolver r = getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(sourceTree);
        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(new Node(rootId, ""));

        try (OutputStream raw = r.openOutputStream(outputUri, "w")) {
            if (raw == null) throw new IOException("ZIP hedefi açılamadı");
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw, 1024 * 1024))) {
                // Sıkıştırma yapma: tek geçiş, yüksek hız, zaten sıkışmış foto/video için gereksiz CPU yok.
                zip.setLevel(Deflater.NO_COMPRESSION);

                while (!stack.isEmpty()) {
                    if (cancel.get()) throw new Cancelled();
                    Node dir = stack.pop();
                    Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(sourceTree, dir.docId);
                    String[] projection = {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    };

                    Map<String, Integer> names = new HashMap<>();
                    try (Cursor c = r.query(children, projection, null, null, null)) {
                        if (c == null) throw new IOException("Klasör okunamadı: " + dir.relative);
                        while (c.moveToNext()) {
                            if (cancel.get()) throw new Cancelled();

                            String childId = c.getString(0);
                            String rawName = c.getString(1);
                            String mime = c.getString(2);
                            long modified = c.isNull(3) ? 0L : c.getLong(3);
                            String base = ZipNames.safeSegment(rawName);

                            int seen = names.containsKey(base) ? names.get(base) + 1 : 1;
                            names.put(base, seen);
                            String unique = ZipNames.duplicateName(base, seen);
                            String relative = dir.relative.isEmpty() ? unique : dir.relative + "/" + unique;
                            Uri childUri = DocumentsContract.buildDocumentUriUsingTree(sourceTree, childId);

                            if (sameDocument(childUri, outputUri)) continue;

                            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                                String entryName = relative + "/";
                                ZipEntry ze = new ZipEntry(entryName);
                                if (modified > 0) ze.setTime(modified);
                                zip.putNextEntry(ze);
                                zip.closeEntry();
                                entries++;
                                dirs++;
                                stack.push(new Node(childId, relative));
                                publish(relative);
                            } else {
                                writeFile(r, zip, childUri, relative, modified);
                            }
                        }
                    } catch (SecurityException se) {
                        throw new IOException("Erişim reddedildi: " + dir.relative, se);
                    }
                }
                zip.finish();
            }
        }
    }

    private void writeFile(ContentResolver r, ZipOutputStream zip, Uri fileUri, String relative, long modified) throws Exception {
        ZipEntry ze = new ZipEntry(relative);
        if (modified > 0) ze.setTime(modified);
        zip.putNextEntry(ze);

        try (InputStream in = r.openInputStream(fileUri)) {
            if (in == null) throw new IOException("Dosya açılamadı: " + relative);
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

    private void verifyZip(Uri outputUri, long expectedEntries, long expectedFiles, long expectedBytes) throws Exception {
        long gotEntries = 0;
        long gotFiles = 0;
        long gotBytes = 0;

        try (InputStream raw = getContentResolver().openInputStream(outputUri)) {
            if (raw == null) throw new IOException("ZIP doğrulama için açılamadı");
            try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw, 1024 * 1024))) {
                ZipEntry e;
                byte[] buffer = new byte[1024 * 1024];
                long last = 0;
                while ((e = zin.getNextEntry()) != null) {
                    if (cancel.get()) throw new Cancelled();
                    gotEntries++;
                    if (!e.isDirectory()) {
                        gotFiles++;
                        int n;
                        while ((n = zin.read(buffer)) != -1) {
                            gotBytes += n;
                            if (System.currentTimeMillis() - last > 700) {
                                setState(true, "ZIP doğrulanıyor…", e.getName(), files, dirs, bytes);
                                last = System.currentTimeMillis();
                            }
                        }
                    }
                    zin.closeEntry();
                }
            }
        }

        if (gotEntries != expectedEntries || gotFiles != expectedFiles || gotBytes != expectedBytes) {
            throw new IOException("Doğrulama sayıları eşleşmedi");
        }
    }

    private boolean hasValidEndRecord(Uri outputUri) throws IOException {
        final int maxTail = 65557;
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(outputUri, "r")) {
            if (pfd == null) return false;
            try (FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
                FileChannel ch = in.getChannel();
                long size = ch.size();
                int count = (int) Math.min((long) maxTail, size);
                if (count < 22) return false;
                ByteBuffer bb = ByteBuffer.allocate(count);
                ch.position(size - count);
                while (bb.hasRemaining() && ch.read(bb) != -1) {}
                return validEocd(bb.array(), bb.position());
            }
        } catch (Exception seekFailed) {
            return hasValidEndRecordSequential(outputUri);
        }
    }

    private boolean hasValidEndRecordSequential(Uri outputUri) throws IOException {
        final int cap = 65557;
        byte[] ring = new byte[cap];
        long total = 0;
        try (InputStream in = new BufferedInputStream(getContentResolver().openInputStream(outputUri), 1024 * 1024)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                for (int i = 0; i < n; i++) ring[(int) ((total + i) % cap)] = buf[i];
                total += n;
            }
        }
        int len = (int) Math.min((long) cap, total);
        byte[] tail = new byte[len];
        long start = Math.max(0, total - len);
        for (int i = 0; i < len; i++) tail[i] = ring[(int) ((start + i) % cap)];
        return validEocd(tail, len);
    }

    private boolean validEocd(byte[] b, int len) {
        for (int i = len - 22; i >= 0; i--) {
            if ((b[i] & 0xff) == 0x50 && (b[i + 1] & 0xff) == 0x4b &&
                    (b[i + 2] & 0xff) == 0x05 && (b[i + 3] & 0xff) == 0x06) {
                int comment = (b[i + 20] & 0xff) | ((b[i + 21] & 0xff) << 8);
                if (i + 22 + comment == len) return true;
            }
        }
        return false;
    }

    private boolean sameDocument(Uri a, Uri b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        if (!safeEquals(a.getAuthority(), b.getAuthority())) return false;
        try {
            return DocumentsContract.getDocumentId(a).equals(DocumentsContract.getDocumentId(b));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
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

    private void deleteOutput(Uri uri) {
        try { DocumentsContract.deleteDocument(getContentResolver(), uri); } catch (Throwable ignored) {}
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
        final String docId;
        final String relative;
        Node(String docId, String relative) {
            this.docId = docId;
            this.relative = relative;
        }
    }

    private static final class Cancelled extends IOException {}
}
