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
    private static final long NOTIFICATION_STEP = 500L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ATMACA-1907-Mover");
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;

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

        if (running.compareAndSet(false, true)) {
            executor.execute(() -> runMove(startId));
        }
        return START_NOT_STICKY;
    }

    private void runMove(int startId) {
        AtomicLong found = new AtomicLong();
        AtomicLong moved = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        List<String> mediaScanBatch = new ArrayList<>(MEDIA_SCAN_BATCH);

        acquireWakeLock();
        try {
            Path root = Environment.getExternalStorageDirectory().toPath();
            Path target = new File(
                    Environment.getExternalStorageDirectory(),
                    "Pictures/1907"
            ).toPath();
            Files.createDirectories(target);

            FileMoveEngine moveEngine = new FileMoveEngine();
            ImageWalker walker = new ImageWalker();

            updateNotification("Taşıma başladı • Pictures/1907", true);

            walker.walk(root, target, source -> {
                if (Thread.currentThread().isInterrupted()) {
                    throw new StopRequestedException();
                }

                long processed = found.incrementAndGet();
                Path destination = target.resolve(source.getFileName().toString());
                if (moveEngine.move(source, target)) {
                    moved.incrementAndGet();
                    mediaScanBatch.add(destination.toString());
                    if (mediaScanBatch.size() >= MEDIA_SCAN_BATCH) {
                        scanMediaBatch(mediaScanBatch);
                    }
                } else {
                    failed.incrementAndGet();
                }

                if (processed % NOTIFICATION_STEP == 0L) {
                    updateNotification(
                            processed + " bulundu • " + moved.get() + " taşındı • " + failed.get() + " hata",
                            true
                    );
                }
            });

            scanMediaBatch(mediaScanBatch);
            updateNotification(
                    "Tamamlandı • " + moved.get() + " taşındı • " + failed.get() + " hata",
                    false
            );
        } catch (StopRequestedException stopped) {
            scanMediaBatch(mediaScanBatch);
            updateNotification("İşlem durduruldu. Uygulamayı açınca kaldığı yerden devam eder.", false);
        } catch (Throwable error) {
            scanMediaBatch(mediaScanBatch);
            updateNotification(
                    "İşlem durdu • " + moved.get() + " taşındı • " + failed.get() + " hata",
                    false
            );
        } finally {
            releaseWakeLock();
            running.set(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH);
            } else {
                stopForeground(false);
            }
            stopSelf(startId);
        }
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
            // Dosya taşıma zaten tamamlandı; medya indeksleme hatası dosyayı geri almamalı.
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
            // Bildirim güncellenemese de taşıma motoru çalışmaya devam eder.
        }
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        executor.shutdownNow();
        updateNotification(
                "Android süre sınırı nedeniyle durdu. Uygulamayı yeniden açınca devam eder.",
                false
        );
        stopSelf(startId);
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
