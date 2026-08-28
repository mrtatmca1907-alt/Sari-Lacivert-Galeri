package com.atmaca.reeldroppro.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.atmaca.reeldroppro.MainActivity
import com.atmaca.reeldroppro.R
import com.atmaca.reeldroppro.data.AppDatabase
import com.atmaca.reeldroppro.engine.ErrorClassifier
import com.atmaca.reeldroppro.engine.ExtractorEngine
import com.atmaca.reeldroppro.engine.RetryClassificationPolicy
import com.atmaca.reeldroppro.engine.RetryPolicy
import com.atmaca.reeldroppro.model.ParsedInput
import com.atmaca.reeldroppro.model.Platform
import com.atmaca.reeldroppro.storage.MediaPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var extractor: ExtractorEngine
    private lateinit var publisher: MediaPublisher
    private lateinit var db: AppDatabase
    private var loop: Job? = null

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
        extractor = ExtractorEngine(this)
        publisher = MediaPublisher(this)
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Kuyruk hazırlanıyor", 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            extractor.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
        return START_STICKY
    }

    private suspend fun runLoop() {
        db.jobs().recoverInterrupted(System.currentTimeMillis())
        while (scope.isActive) {
            val job = db.jobs().nextRunnable(System.currentTimeMillis())
            if (job == null) {
                updateNotification("Kuyruk boş", 0)
                delay(1500)
                continue
            }
            val platform = runCatching { Platform.valueOf(job.platform) }.getOrNull()
            if (platform == null) {
                db.jobs().setState(job.id, "FAILED", System.currentTimeMillis(), "INPUT", "Geçersiz platform", 0, job.attempt)
                continue
            }
            val parsed = ParsedInput(platform, job.inputValue, job.sourceKey)
            db.jobs().setState(job.id, "DOWNLOADING", System.currentTimeMillis(), null, null, 0, job.attempt)
            updateNotification("${job.sourceKey} indiriliyor", 0)

            val started = System.currentTimeMillis()
            val result = extractor.run(parsed, job.id) { progress, eta ->
                scope.launch {
                    db.jobs().updateProgress(
                        job.id, progress, "ETA ${eta}s", job.bytesTransferred, 0,
                        job.downloadedCount, job.photoCount, job.videoCount, job.failedCount,
                        System.currentTimeMillis()
                    )
                    updateNotification("${job.sourceKey} • %${progress.toInt()}", progress.toInt())
                }
            }

            result.fold(
                onSuccess = { data ->
                    runCatching { publisher.publishTree(platform.name.substringBefore('_'), job.sourceKey, data.tempDir) }
                        .onSuccess { pub ->
                            db.jobs().updateProgress(job.id, 100f, null, pub.bytes, 0, pub.files, pub.photos, pub.videos, job.failedCount, System.currentTimeMillis())
                            db.jobs().setState(job.id, "COMPLETED", System.currentTimeMillis(), null, null, 0, job.attempt)
                            data.tempDir.deleteRecursively()
                            updateNotification("${job.sourceKey} tamamlandı • ${pub.files} dosya", 100)
                        }
                        .onFailure { t -> handleFailure(job.id, job.attempt, t) }
                },
                onFailure = { t -> handleFailure(job.id, job.attempt, t) }
            )
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < 250) delay(250 - elapsed)
        }
    }

    private suspend fun handleFailure(id: Long, attempt: Int, throwable: Throwable) {
        val error = ErrorClassifier.classify("", throwable)
        val canRetry = RetryClassificationPolicy.retryable(error.kind) && attempt < 8
        if (canRetry) {
            val base = RetryPolicy.nextDelayMs(attempt, true) ?: 5_000L
            db.jobs().setState(id, "RETRY_WAIT", System.currentTimeMillis(), error.kind.name, error.message, System.currentTimeMillis() + base, attempt + 1)
            updateNotification("Geçici hata • tekrar denenecek", 0)
        } else {
            db.jobs().setState(id, "FAILED", System.currentTimeMillis(), error.kind.name, error.message, 0, attempt)
            updateNotification("İndirme hatası: ${error.message.take(60)}", 0)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "ReelDrop Pro indirmeleri", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(text: String, progress: Int) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("ReelDrop Pro")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .build()

    private fun updateNotification(text: String, progress: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, progress))
    }

    override fun onDestroy() {
        extractor.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "reeldrop_downloads"
        private const val NOTIFICATION_ID = 7401
        const val ACTION_STOP = "com.atmaca.reeldroppro.STOP"
    }
}
