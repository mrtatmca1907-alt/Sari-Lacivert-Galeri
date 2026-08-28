package com.videokareleri.v5

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class FrameExtractService : Service() {
    private val cancelled = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Video kare çıkarma", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> { cancelled.set(true); return START_NOT_STICKY }
            ACTION_START -> Unit
            else -> return START_NOT_STICKY
        }
        if (!running.compareAndSet(false, true)) {
            broadcast(0, 0, 0, 0, 0, "Zaten bir işlem çalışıyor.", false)
            return START_NOT_STICKY
        }
        cancelled.set(false)
        startForeground(NOTIFICATION_ID, notification("Hazırlanıyor...", 0, 0))
        val paths = intent.getStringArrayExtra(EXTRA_PATHS)
        scope.launch { runJob(paths) }
        return START_NOT_STICKY
    }

    private suspend fun runJob(paths: Array<String>?) {
        var videos = 0; var total = 0; var saved = 0; var skipped = 0; var errors = 0
        val repo = MediaStoreVideoRepository(contentResolver)
        val writer = FrameWriter(contentResolver)
        try {
            repo.openSelectedVideos(paths)?.use { c ->
                total = c.count
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                while (!cancelled.get() && c.moveToNext()) {
                    videos++
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol).orEmpty()
                    val duration = c.getLong(durCol).coerceAtLeast(0L)
                    val base = NameUtils.sanitizeBaseName(name)
                    val existing = writer.existingNames(base)
                    val mmr = MediaMetadataRetriever()
                    try {
                        mmr.setDataSource(this, MediaStoreVideoRepository.videoUri(id))
                        val frameCount = NameUtils.frameCount(duration)
                        for (second in 0 until frameCount) {
                            if (cancelled.get()) break
                            var bitmap: Bitmap? = null
                            try {
                                bitmap = mmr.getFrameAtTime(second * 1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                if (bitmap == null) { errors++; continue }
                                if (writer.write(bitmap, base, second + 1, existing)) saved++ else skipped++
                            } catch (_: Exception) { errors++ }
                            finally { bitmap?.takeIf { !it.isRecycled }?.recycle() }
                        }
                    } catch (_: Exception) { errors++ }
                    finally { runCatching { mmr.release() } }
                    updateNotification(name, videos, total)
                    broadcast(videos, total, saved, skipped, errors, name, false)
                    yield()
                }
            }
        } catch (_: Exception) { errors++ }
        finally {
            val end = if (cancelled.get()) "Durduruldu" else "Tamamlandı"
            broadcast(videos, total, saved, skipped, errors, end, true)
            running.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notification(text: String, done: Int, total: Int): Notification {
        val cancelIntent = Intent(this, FrameExtractService::class.java).setAction(ACTION_CANCEL)
        val pi = PendingIntent.getService(this, 2, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setContentTitle("Video Kareleri V5").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Durdur", pi)
            .apply { if (total > 0) setProgress(total, done, false) else setProgress(0, 0, true) }
            .build()
    }

    private fun updateNotification(name: String, done: Int, total: Int) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(name, done, total))

    private fun broadcast(videos: Int, total: Int, saved: Int, skipped: Int, errors: Int, current: String, finished: Boolean) {
        sendBroadcast(Intent(ACTION_PROGRESS).setPackage(packageName).apply {
            putExtra("videos", videos); putExtra("total", total); putExtra("saved", saved)
            putExtra("skipped", skipped); putExtra("errors", errors); putExtra("current", current); putExtra("finished", finished)
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { cancelled.set(true); scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_START = "com.videokareleri.v5.START"
        const val ACTION_CANCEL = "com.videokareleri.v5.CANCEL"
        const val ACTION_PROGRESS = "com.videokareleri.v5.PROGRESS"
        const val EXTRA_PATHS = "paths"
        private const val CHANNEL = "video_kareleri"
        private const val NOTIFICATION_ID = 4105
    }
}
