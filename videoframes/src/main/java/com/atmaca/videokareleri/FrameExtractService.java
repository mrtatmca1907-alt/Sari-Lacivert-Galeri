package com.atmaca.videokareleri;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FrameExtractService extends Service {
    public static final String ACTION_START = "com.atmaca.videokareleri.START";
    public static final String ACTION_PROGRESS = "com.atmaca.videokareleri.PROGRESS";
    public static final String EXTRA_TREE_URI = "tree_uri";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_DONE = "done";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_FINISHED = "finished";

    private static final int NOTIFICATION_ID = 1907;
    private static final String CHANNEL_ID = "video_frames";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        String raw = intent.getStringExtra(EXTRA_TREE_URI);
        if (raw == null) return START_NOT_STICKY;
        startForeground(NOTIFICATION_ID, notification("Hazırlanıyor…"));
        executor.execute(() -> runJob(Uri.parse(raw)));
        return START_NOT_STICKY;
    }

    private void runJob(Uri treeUri) {
        acquireWakeLock();
        try {
            DocumentFile root = DocumentFile.fromTreeUri(this, treeUri);
            if (root == null || !root.isDirectory()) throw new IllegalStateException("Klasör açılamadı");

            List<DocumentFile> videos = new ArrayList<>();
            for (DocumentFile f : root.listFiles()) {
                if (f.isFile() && FrameRules.isVideo(f.getType(), f.getName())) videos.add(f);
            }

            if (videos.isEmpty()) {
                send("Bu klasörde video bulunamadı", 0, 0, true);
                return;
            }

            int videoDone = 0;
            for (DocumentFile video : videos) {
                String videoName = video.getName() == null ? "video" : video.getName();
                updateNotification((videoDone + 1) + "/" + videos.size() + "  " + videoName);
                try {
                    processVideo(root, video, videoDone, videos.size());
                    videoDone++;
                    send("Tamamlandı: " + videoName, videoDone, videos.size(), false);
                } catch (Throwable error) {
                    try {
                        moveToError(root, video);
                        send("HATA klasörüne taşındı: " + videoName, videoDone, videos.size(), false);
                    } catch (Throwable moveError) {
                        send("Hata: " + videoName + " — " + safeMessage(error), videoDone, videos.size(), false);
                    }
                }
            }
            send("İşlem tamamlandı", videos.size(), videos.size(), true);
        } catch (Throwable e) {
            send("İşlem durdu: " + safeMessage(e), 0, 0, true);
        } finally {
            releaseWakeLock();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void processVideo(DocumentFile root, DocumentFile video, int videoIndex, int totalVideos) throws Exception {
        String videoName = video.getName() == null ? "video.mp4" : video.getName();
        String base = FrameRules.baseName(videoName);
        DocumentFile outDir = root.findFile(base);
        if (outDir == null) outDir = root.createDirectory(base);
        if (outDir == null || !outDir.isDirectory()) throw new IllegalStateException("Kare klasörü oluşturulamadı");

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, video.getUri());
            String durationRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = durationRaw == null ? 0L : Long.parseLong(durationRaw);
            int frameCount = FrameRules.frameCount(durationMs);
            if (frameCount <= 0) throw new IllegalStateException("Video süresi okunamadı");

            for (int i = 0; i < frameCount; i++) {
                long timeUs = i * 1_000_000L;
                Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame == null) throw new IllegalStateException((i + 1) + ". kare alınamadı");
                try {
                    String frameName = FrameRules.frameName(base, i + 1);
                    DocumentFile old = outDir.findFile(frameName);
                    if (old != null) old.delete();
                    DocumentFile target = outDir.createFile("image/jpeg", frameName);
                    if (target == null) throw new IllegalStateException("JPEG oluşturulamadı");
                    try (OutputStream os = getContentResolver().openOutputStream(target.getUri(), "w")) {
                        if (os == null || !frame.compress(Bitmap.CompressFormat.JPEG, 90, os)) {
                            throw new IllegalStateException("JPEG yazılamadı");
                        }
                    }
                } finally {
                    frame.recycle();
                }

                if (i == 0 || (i + 1) % 10 == 0 || i + 1 == frameCount) {
                    String msg = String.format(Locale.ROOT, "%d/%d video • %s • %d/%d kare", videoIndex + 1, totalVideos, videoName, i + 1, frameCount);
                    send(msg, videoIndex, totalVideos, false);
                    updateNotification(msg);
                }
            }
        } finally {
            retriever.release();
        }

        if (!video.delete()) {
            send("Kareler tamam; video silinemedi: " + videoName, videoIndex, totalVideos, false);
        }
    }

    private void moveToError(DocumentFile root, DocumentFile video) throws Exception {
        DocumentFile errorDir = root.findFile("HATA");
        if (errorDir == null) errorDir = root.createDirectory("HATA");
        if (errorDir == null) throw new IllegalStateException("HATA klasörü oluşturulamadı");

        String original = video.getName() == null ? "hata_video" : video.getName();
        String targetName = uniqueName(errorDir, original);
        DocumentFile target = errorDir.createFile(video.getType() == null ? "video/mp4" : video.getType(), targetName);
        if (target == null) throw new IllegalStateException("Hata videosu oluşturulamadı");

        try (InputStream in = getContentResolver().openInputStream(video.getUri());
             OutputStream out = getContentResolver().openOutputStream(target.getUri(), "w")) {
            if (in == null || out == null) throw new IllegalStateException("Hata videosu taşınamadı");
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        if (!video.delete()) throw new IllegalStateException("Kaynak hata videosu silinemedi");
    }

    private String uniqueName(DocumentFile dir, String original) {
        if (dir.findFile(original) == null) return original;
        String base = FrameRules.baseName(original);
        int dot = original.lastIndexOf('.');
        String ext = dot > 0 ? original.substring(dot) : "";
        int n = 1;
        while (dir.findFile(base + " (" + n + ")" + ext) != null) n++;
        return base + " (" + n + ")" + ext;
    }

    private void send(String message, int done, int total, boolean finished) {
        Intent i = new Intent(ACTION_PROGRESS);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_MESSAGE, message);
        i.putExtra(EXTRA_DONE, done);
        i.putExtra(EXTRA_TOTAL, total);
        i.putExtra(EXTRA_FINISHED, finished);
        sendBroadcast(i);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Video kare çıkarma", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle("Video Kareleri")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(String text) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(text));
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoKareleri:Extract");
            wakeLock.acquire();
        } catch (Throwable ignored) {}
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
    }

    private String safeMessage(Throwable e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    @Override public void onDestroy() {
        executor.shutdownNow();
        releaseWakeLock();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
