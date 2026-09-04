package com.atmaca.gallery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.UUID

private const val CHANNEL_ID = "atmaca_media_tools"

fun enqueueMediaToolWork(context: Context, tool: AtmacaToolPage, uris: List<Uri>, option: Int): UUID? {
    if (uris.isEmpty()) return null
    val id = UUID.randomUUID()
    val queue = File(context.applicationContext.filesDir, "media_tool_jobs/$id.queue")
    if (!writeMediaToolQueue(queue, uris.map(Uri::toString))) return null
    val request = OneTimeWorkRequestBuilder<MediaToolWorker>().setId(id).setInputData(
        workDataOf(MEDIA_TOOL_KEY_QUEUE_FILE to queue.absolutePath, MEDIA_TOOL_KEY_TOOL to tool.name, MEDIA_TOOL_KEY_OPTION to option)
    ).addTag(MEDIA_TOOL_WORK_TAG).build()
    WorkManager.getInstance(context.applicationContext).enqueue(request)
    return id
}

class MediaToolWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val notificationId = (id.hashCode() and 0x7fffffff).coerceAtLeast(1907)

    override suspend fun doWork(): Result {
        val queue = inputData.getString(MEDIA_TOOL_KEY_QUEUE_FILE)?.let(::File) ?: return Result.failure()
        val tool = inputData.getString(MEDIA_TOOL_KEY_TOOL)?.let { runCatching { AtmacaToolPage.valueOf(it) }.getOrNull() } ?: return Result.failure()
        val uris = runCatching { readMediaToolQueue(queue).map(Uri::parse) }.getOrElse { queue.delete(); return Result.failure() }
        if (uris.isEmpty()) { queue.delete(); return Result.failure() }
        setForeground(foregroundInfo(tool, 0, 0))
        return try {
            val engine = CompleteToolEngine(applicationContext)
            val option = inputData.getInt(MEDIA_TOOL_KEY_OPTION, defaultOption(tool))
            val progress: (Int, Int) -> Unit = { done, total ->
                setProgressAsync(workDataOf(MEDIA_TOOL_KEY_DONE to done, MEDIA_TOOL_KEY_TOTAL to total))
                setForegroundAsync(foregroundInfo(tool, done, total))
            }
            val result = when (tool) {
                AtmacaToolPage.PERSON_CROP -> engine.smartPersonCrop(uris, option.coerceIn(1, 24), progress)
                AtmacaToolPage.PACKAGER -> engine.packageMedia(uris, option.coerceIn(5, 200), progress)
                AtmacaToolPage.VIDEO_FRAMES -> engine.extractVideoFrames(uris, option.coerceIn(1, 4), true, progress)
            }
            queue.delete()
            completionNotification(tool, result)
            Result.success(workDataOf(MEDIA_TOOL_KEY_CREATED to result.created, MEDIA_TOOL_KEY_SKIPPED to result.skipped, MEDIA_TOOL_KEY_FAILED to result.failed))
        } catch (cancelled: CancellationException) {
            queue.delete(); throw cancelled
        } catch (_: SecurityException) {
            queue.delete(); Result.failure()
        } catch (_: Throwable) {
            if (runAttemptCount < 2) Result.retry() else { queue.delete(); Result.failure() }
        }
    }

    private fun foregroundInfo(tool: AtmacaToolPage, done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "ATMACA Arka Plan İşlemleri", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(applicationContext, notificationId, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(toolTitleForWork(tool))
            .setContentText(if (total > 0) "$done / $total işlendi" else "Hazırlanıyor…")
            .setProgress(total.coerceAtLeast(0), done.coerceIn(0, total.coerceAtLeast(0)), total <= 0)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pending).build()
        return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun completionNotification(tool: AtmacaToolPage, result: ToolRunResult) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("${toolTitleForWork(tool)} tamamlandı")
            .setContentText("${result.created} oluşturuldu • ${result.skipped} atlandı • ${result.failed} hata").setAutoCancel(true).build()
        manager.notify(notificationId + 1, notification)
    }
}

private fun defaultOption(tool: AtmacaToolPage) = when (tool) {
    AtmacaToolPage.PERSON_CROP -> 12
    AtmacaToolPage.PACKAGER -> 50
    AtmacaToolPage.VIDEO_FRAMES -> 1
}
internal fun toolTitleForWork(tool: AtmacaToolPage) = when (tool) {
    AtmacaToolPage.PERSON_CROP -> "Akıllı Kişi Kırpma"
    AtmacaToolPage.PACKAGER -> "Görsel Paketleyici"
    AtmacaToolPage.VIDEO_FRAMES -> "Video Kareleri"
}
