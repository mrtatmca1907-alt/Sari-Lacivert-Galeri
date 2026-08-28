package com.atmaca.reeldroppro.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.atmaca.reeldroppro.MainActivity
import com.atmaca.reeldroppro.core.RetryPolicy
import com.atmaca.reeldroppro.data.AppDatabase
import com.atmaca.reeldroppro.data.JobEntity
import com.atmaca.reeldroppro.engine.EngineSlotPolicy
import com.atmaca.reeldroppro.engine.ErrorClassifier
import com.atmaca.reeldroppro.engine.ExtractorEngine
import com.atmaca.reeldroppro.engine.MediaKind
import com.atmaca.reeldroppro.engine.MediaTypePolicy
import com.atmaca.reeldroppro.engine.RetryClassificationPolicy
import com.atmaca.reeldroppro.engine.SlotCancelledException
import com.atmaca.reeldroppro.model.ParsedInput
import com.atmaca.reeldroppro.model.Platform
import com.atmaca.reeldroppro.storage.MediaPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workers = ConcurrentHashMap<Int, Job>()
    private val cancelRequested = ConcurrentHashMap.newKeySet<Int>()
    private val activeWorkCount = AtomicInteger(0)

    private lateinit var extractor: ExtractorEngine
    private lateinit var publisher: MediaPublisher
    private lateinit var db: AppDatabase
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
        extractor = ExtractorEngine(this)
        publisher = MediaPublisher(this)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReelDropPro:ActiveDownloads")
            .apply { setReferenceCounted(false) }
        createChannel()
        startForeground(NOTIFICATION_ID, notification("5 motor hazır", 0))
        scope.launch {
            db.jobs().recoverInterrupted(System.currentTimeMillis())
            EngineSlotPolicy.slotIds.forEach(::startSlotWorker)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val slotId = intent?.getIntExtra(EXTRA_SLOT_ID, 0) ?: 0
        when (intent?.action) {
            ACTION_STOP_SLOT -> if (EngineSlotPolicy.isValid(slotId)) requestStop(slotId)
            ACTION_START_SLOT -> if (EngineSlotPolicy.isValid(slotId)) startSlotWorker(slotId)
            ACTION_STOP_ALL -> EngineSlotPolicy.slotIds.forEach(::requestStop)
            else -> EngineSlotPolicy.slotIds.forEach(::startSlotWorker)
        }
        return START_STICKY
    }

    private fun startSlotWorker(slotId: Int) {
        if (!EngineSlotPolicy.isValid(slotId)) return
        val current = workers[slotId]
        if (current?.isActive == true) return
        cancelRequested.remove(slotId)
        workers[slotId] = scope.launch {
            try {
                runSlot(slotId)
            } finally {
                workers.remove(slotId)
                updateSummaryNotification()
                maybeStopWhenIdle()
            }
        }
        updateSummaryNotification()
    }

    private suspend fun runSlot(slotId: Int) {
        while (scope.isActive && !cancelRequested.contains(slotId)) {
            val now = System.currentTimeMillis()
            var job = db.jobs().runnableForSlot(slotId, now)
            if (job == null) {
                val latest = db.jobs().latestForSlot(slotId) ?: break
                if (latest.state == "RETRY_WAIT" && latest.nextAttemptAt > now) {
                    delay((latest.nextAttemptAt - now).coerceIn(500L, 2_000L))
                    continue
                }
                break
            }

            if (!isConnected()) {
                db.jobs().setState(job.id, "RETRY_WAIT", now, "NETWORK", "İnternet bağlantısı bekleniyor", now + 5_000L, job.attempt)
                delay(2_000L)
                continue
            }

            val platform = runCatching { Platform.valueOf(job.platform) }.getOrNull()
            if (platform == null) {
                db.jobs().setState(job.id, "FAILED", now, "INPUT", "Geçersiz platform", 0, job.attempt)
                break
            }
            val parsed = ParsedInput(platform, job.inputValue, job.sourceKey)
            db.jobs().setState(job.id, "DOWNLOADING", now, null, null, 0, job.attempt)
            job = job.copy(state = "DOWNLOADING", updatedAt = now)
            updateSummaryNotification()

            acquireActiveWakeLock()
            try {
                processJob(slotId, job, parsed)
            } finally {
                releaseActiveWakeLock()
            }
        }

        if (cancelRequested.remove(slotId)) {
            db.jobs().latestForSlot(slotId)?.let { latest ->
                if (latest.state !in setOf("COMPLETED", "FAILED", "CANCELLED")) {
                    db.jobs().setState(latest.id, "CANCELLED", System.currentTimeMillis(), null, "Kullanıcı tarafından durduruldu", 0, latest.attempt)
                }
            }
        }
    }

    private suspend fun processJob(slotId: Int, job: JobEntity, parsed: ParsedInput) = coroutineScope {
        val workingDir = extractor.workingDir(parsed, job.id)
        val progressRef = AtomicReference(job.progress)
        val monitor = launch(Dispatchers.IO) {
            var lastBytes = 0L
            var lastAt = System.currentTimeMillis()
            while (isActive) {
                val snapshot = scan(workingDir)
                val now = System.currentTimeMillis()
                val elapsedMs = (now - lastAt).coerceAtLeast(1L)
                val speed = ((snapshot.bytes - lastBytes).coerceAtLeast(0L) * 1000L) / elapsedMs
                db.jobs().updateProgress(
                    job.id,
                    progressRef.get(),
                    snapshot.currentFile,
                    snapshot.bytes,
                    speed,
                    snapshot.photos + snapshot.videos,
                    snapshot.photos,
                    snapshot.videos,
                    job.failedCount,
                    now
                )
                lastBytes = snapshot.bytes
                lastAt = now
                delay(1_000L)
            }
        }

        val result = extractor.run(parsed, job.id, slotId) { progress, _ ->
            progressRef.set(progress.coerceIn(0f, 100f))
        }
        monitor.cancelAndJoin()

        if (cancelRequested.contains(slotId)) {
            db.jobs().setState(job.id, "CANCELLED", System.currentTimeMillis(), null, "Kullanıcı tarafından durduruldu", 0, job.attempt)
            return@coroutineScope
        }

        result.fold(
            onSuccess = { data ->
                db.jobs().setState(job.id, "POST_PROCESSING", System.currentTimeMillis(), null, null, 0, job.attempt)
                runCatching { publisher.publishTree(parsed.platform.name, job.sourceKey, data.tempDir) }
                    .onSuccess { published ->
                        val errorCount = (job.failedCount + data.itemErrors).coerceAtLeast(0)
                        db.jobs().updateProgress(
                            job.id,
                            100f,
                            null,
                            published.bytes,
                            0,
                            published.files,
                            published.photos,
                            published.videos,
                            errorCount,
                            System.currentTimeMillis()
                        )
                        db.jobs().setState(
                            job.id,
                            "COMPLETED",
                            System.currentTimeMillis(),
                            if (data.itemErrors > 0) "PARTIAL" else null,
                            data.partialError?.take(1200),
                            0,
                            job.attempt
                        )
                        data.tempDir.deleteRecursively()
                    }
                    .onFailure { throwable -> handleFailure(job, throwable) }
            },
            onFailure = { throwable ->
                if (throwable is SlotCancelledException || cancelRequested.contains(slotId)) {
                    db.jobs().setState(job.id, "CANCELLED", System.currentTimeMillis(), null, "Kullanıcı tarafından durduruldu", 0, job.attempt)
                } else {
                    handleFailure(job, throwable)
                }
            }
        )
        updateSummaryNotification()
    }

    private suspend fun handleFailure(job: JobEntity, throwable: Throwable) {
        val error = ErrorClassifier.classify("", throwable)
        val canRetry = RetryClassificationPolicy.retryable(error.kind) && job.attempt < 8
        if (canRetry) {
            val delayMs = RetryPolicy.nextDelayMs(job.attempt, true) ?: 5_000L
            db.jobs().setState(
                job.id,
                "RETRY_WAIT",
                System.currentTimeMillis(),
                error.kind.name,
                error.message.take(1200),
                System.currentTimeMillis() + delayMs,
                job.attempt + 1
            )
        } else {
            db.jobs().setState(
                job.id,
                "FAILED",
                System.currentTimeMillis(),
                error.kind.name,
                error.message.take(1200),
                0,
                job.attempt
            )
        }
    }

    private fun requestStop(slotId: Int) {
        cancelRequested.add(slotId)
        extractor.cancel(slotId)
        scope.launch {
            db.jobs().latestForSlot(slotId)?.let { job ->
                if (job.state !in setOf("COMPLETED", "FAILED", "CANCELLED")) {
                    db.jobs().setState(job.id, "CANCELLED", System.currentTimeMillis(), null, "Durduruluyor", 0, job.attempt)
                }
            }
        }
    }

    private fun acquireActiveWakeLock() {
        if (activeWorkCount.incrementAndGet() == 1 && !wakeLock.isHeld) wakeLock.acquire()
    }

    private fun releaseActiveWakeLock() {
        if (activeWorkCount.decrementAndGet().coerceAtLeast(0) == 0) {
            activeWorkCount.set(0)
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun isConnected(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private data class FileSnapshot(
        val photos: Int,
        val videos: Int,
        val bytes: Long,
        val currentFile: String?
    )

    private fun scan(root: File): FileSnapshot {
        if (!root.exists()) return FileSnapshot(0, 0, 0L, null)
        var photos = 0
        var videos = 0
        var bytes = 0L
        var newest: File? = null
        root.walkTopDown().forEach { file ->
            if (!file.isFile || file.name.endsWith(".part")) return@forEach
            when (MediaTypePolicy.fromExtension(file.extension)) {
                MediaKind.PHOTO -> photos++
                MediaKind.VIDEO -> videos++
                MediaKind.OTHER -> return@forEach
            }
            bytes += file.length()
            if (newest == null || file.lastModified() > newest!!.lastModified()) newest = file
        }
        return FileSnapshot(photos, videos, bytes, newest?.name)
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ReelDrop Pro indirmeleri", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String, progress: Int) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("ReelDrop Pro V2")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun updateSummaryNotification() {
        val active = workers.values.count { it.isActive }
        val text = if (active > 0) "$active motor aktif • indirme korunuyor" else "Motorlar hazır"
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, 0))
    }

    private fun maybeStopWhenIdle() {
        if (workers.values.any { it.isActive }) return
        scope.launch {
            if (db.jobs().activeCount() == 0) stopSelf()
        }
    }

    override fun onDestroy() {
        extractor.cancelAll()
        scope.cancel()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "reeldrop_downloads_v2"
        private const val NOTIFICATION_ID = 7402
        const val EXTRA_SLOT_ID = "slot_id"
        const val ACTION_START_SLOT = "com.atmaca.reeldroppro.START_SLOT"
        const val ACTION_STOP_SLOT = "com.atmaca.reeldroppro.STOP_SLOT"
        const val ACTION_STOP_ALL = "com.atmaca.reeldroppro.STOP_ALL"
    }
}
