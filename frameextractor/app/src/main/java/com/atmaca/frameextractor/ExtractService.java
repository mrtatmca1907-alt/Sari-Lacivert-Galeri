package com.atmaca.frameextractor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.os.Build;
import java.io.OutputStream;
import java.util.Locale;

public class ExtractService extends Service {
    private static final String CHANNEL = "extract";
    private NotificationManager notifications;
    private volatile boolean busy;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (busy) return START_NOT_STICKY;
        busy = true;
        notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Kare çıkarma", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, notice("Video hazırlanıyor", true));
        Uri video = Uri.parse(intent.getStringExtra("video"));
        Uri target = Uri.parse(intent.getStringExtra("folder"));
        new Thread(() -> {
            try { extract(video, target); }
            catch (Exception e) { notifications.notify(2, notice("Hata: " + e.getMessage(), false)); }
            finally { busy = false; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId); }
        }, "extract-frames").start();
        return START_NOT_STICKY;
    }

    private Notification notice(String text, boolean ongoing) {
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("ATMACA Kare Çıkarıcı").setContentText(text)
                .setOngoing(ongoing).setOnlyAlertOnce(true).build();
    }

    private void extract(Uri video, Uri destination) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, video);
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (value == null) throw new IllegalStateException("Video süresi okunamadı");
            long durationMs = Long.parseLong(value);
            if (durationMs <= 0) throw new IllegalStateException("Video boş");
            long total = (durationMs + 999) / 1000;
            Uri root = DocumentsContract.buildDocumentUriUsingTree(destination, DocumentsContract.getTreeDocumentId(destination));
            Uri session = DocumentsContract.createDocument(getContentResolver(), root,
                    DocumentsContract.Document.MIME_TYPE_DIR, "ATMACA_Kareler_" + System.currentTimeMillis());
            if (session == null) throw new IllegalStateException("Çıktı klasörü oluşturulamadı");
            Uri batch = null;
            int saved = 0;
            for (long second = 0; second < total; second++) {
                long micros = second * 1_000_000L;
                Bitmap bitmap = Build.VERSION.SDK_INT >= 28
                        ? retriever.getFrameAtTime(micros, MediaMetadataRetriever.OPTION_CLOSEST)
                        : retriever.getFrameAtTime(micros, MediaMetadataRetriever.OPTION_CLOSEST);
                if (bitmap == null) throw new IllegalStateException(second + ". saniyede kare çözülemedi");
                try {
                    if (saved % 50 == 0) {
                        batch = DocumentsContract.createDocument(getContentResolver(), session,
                                DocumentsContract.Document.MIME_TYPE_DIR,
                                String.format(Locale.ROOT, "Klasor_%04d", saved / 50 + 1));
                        if (batch == null) throw new IllegalStateException("50'lik klasör oluşturulamadı");
                    }
                    Uri output = DocumentsContract.createDocument(getContentResolver(), batch, "image/png",
                            String.format(Locale.ROOT, "Kare_%06d_%ds.png", saved + 1, second));
                    if (output == null) throw new IllegalStateException("Görsel dosyası oluşturulamadı");
                    try (OutputStream stream = getContentResolver().openOutputStream(output, "w")) {
                        if (stream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                            throw new IllegalStateException("PNG kaydı başarısız");
                    } catch (Exception e) {
                        DocumentsContract.deleteDocument(getContentResolver(), output);
                        throw e;
                    }
                    saved++;
                } finally { bitmap.recycle(); }
                if (saved % 5 == 0 || second + 1 == total)
                    notifications.notify(1, notice(saved + " / " + total + " kare kaydedildi", true));
            }
            notifications.notify(2, notice(saved + " kare tamamlandı. Son klasör " + ((saved - 1) % 50 + 1) + " kare.", false));
        } finally { retriever.release(); }
    }
}
