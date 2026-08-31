package com.atmaca.imagemover;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MoveService extends Service {
    private static final String CHANNEL_ID = "atmaca_1907_move";
    private static final int NOTIFICATION_ID = 1907;
    private static final int MEDIA_SCAN_BATCH = 128;
    private static final long NOTIFICATION_STEP = 100L;

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicLong FOUND = new AtomicLong();
    private static final AtomicLong MOVED = new AtomicLong();
    private static final AtomicLong FAILED = new AtomicLong();
    private static volatile String PHASE = "Bekliyor";

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ATMACA-1907-Mover");
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });

    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;

    public static boolean isRunning() {
        return RUNNING.get();
    }

    public static String getStatusText() {
        return PHASE + "\n"
                + "Bulunan: " + FOUND.get() + "\n"
                + "Taşınan: " + MOVED.get() + "\n"
                + "Hata: " + FAILED.get();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "ATMACA 1907 taşıma",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Görseller Pictures/1907 klasörüne taşınırken ilerlemeyi gösterir.");
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification("Hazırlanıyor…", true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (RUNNING.compareAndSet(false, true)) {
            executor.execute(this::runMove);
        }
        return START_NOT_STICKY;
    }

    private void runMove() {
        FOUND.set(0);
        MOVED.set(0);
        FAILED.set(0);
        PHASE = "Hazırlanıyor";
        List<String> mediaScanBatch = new ArrayList<>(MEDIA_SCAN_BATCH * 2);

        acquireWakeLock();
        try {
            Path root = Environment.getExternalStorageDirectory().toPath();
            Path target = new File(
                    Environment.getExternalStorageDirectory(),
                    "Pictures/1907"
            ).toPath();
            Files.createDirectories(target);

            FileMoveEngine moveEngine = new FileMoveEngine();

            PHASE = "Galeri listesinden taşıyor";
            updateNotification(statusLine(), true);
            MediaStoreImageWalker mediaStoreWalker = new MediaStoreImageWalker();
            mediaStoreWalker.walk(this, target, source -> processOne(source, target, moveEngine, mediaScanBatch));

            // MediaStore'a hiç girmemiş/indekslenmemiş görselleri de kaybetmemek için yedek geçiş.
            PHASE = "Kalan görseller kontrol ediliyor";
            updateNotification(statusLine(), true);
            ImageWalker fileWalker = new ImageWalker();
            fileWalker.walk(root, target, source -> processOne(source, target, moveEngine, mediaScanBatch));

            scanMediaBatch(mediaScanBatch);
            PHASE = "Tamamlandı";
            updateNotification(statusLine(), false);
        } catch (StopRequestedException stopped) {
            scanMediaBatch(mediaScanBatch);
            PHASE = "Durduruldu";
            updateNotification(statusLine(), false);
        } catch (Throwable error) {
            scanMediaBatch(mediaScanBatch);
            PHASE = "İşlem durdu";
            FAILED.incrementAndGet();
            updateNotification(statusLine(), false);
        } finally {
            releaseWakeLock();
            RUNNING.set(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH);
            } else {
                stopForeground(false);
            }
            stopSelf();
        }
    }

    private void processOne(
            Path source,
            Path target,
            FileMoveEngine moveEngine,
            List<String> mediaScanBatch
    ) {
        if (Thread.currentThread().isInterrupted()) {
            throw new StopRequestedException();
        }

        long processed = FOUND.incrementAndGet();
        Path destination = target.resolve(source.getFileName().toString());
        String oldPath = source.toString();

        if (moveEngine.move(source, target)) {
            MOVED.incrementAndGet();
            mediaScanBatch.add(oldPath);
            mediaScanBatch.add(destination.toString());
            if (mediaScanBatch.size() >= MEDIA_SCAN_BATCH * 2) {
                scanMediaBatch(mediaScanBatch);
            }
        } else {
            FAILED.incrementAndGet();
        }

        if (processed <= 10L || processed % NOTIFICATION_STEP == 0L) {
            updateNotification(statusLine(), true);
        }
    }

    private String statusLine() {
        return PHASE + " • " + FOUND.get() + " bulundu • "
                + MOVED.get() + " taşındı • " + FAILED.get() + " hata";
    }

    private void scanMediaBatch(List<String> batch) {
        if (batch.isEmpty()) {
            return;
        }
        String[] paths = batch.toArray(new String[0]);
        batch.clear();
        try {
            MediaScannerConnection.scanFile(this, paths, null, null);
        } catch (Throwable ignored) {
            // Dosya taşıma tamamlandıysa indeks hatası kaynağı geri getirmemeli.
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ATMACA1907:ImageMove"
            );
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Throwable ignored) {
            wakeLock = null;
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
            } catch (Throwable ignored) {
                // Servis kapanışını engelleme.
            }
            wakeLock = null;
        }
    }

    private Notification buildNotification(String text, boolean ongoing) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("ATMACA 1907")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .build();
    }

    private void updateNotification(String text, boolean ongoing) {
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(text, ongoing));
        } catch (Throwable ignored) {
            // Bildirim güncellenemese de taşıma motoru devam eder.
        }
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        executor.shutdownNow();
        PHASE = "Android süre sınırı nedeniyle durdu";
        updateNotification(statusLine(), false);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        releaseWakeLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final class StopRequestedException extends RuntimeException {
    }
}
