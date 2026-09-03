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
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.util.UUID

private const val KEY_QUEUE_FILE = "queue_file"
private const val KEY_FPS = "fps"
private const val KEY_DONE = "done"
private const val KEY_TOTAL = "total"
private const val KEY_CREATED = "created"
private const val KEY_FAILED = "failed"
private const val FRAME_CHANNEL_ID = "atmaca_video_frames"
private const val FRAME_NOTIFICATION_ID = 1907
const val VIDEO_FRAME_WORK_TAG = "atmaca_video_frames"

fun enqueueVideoFrameWork(context: Context, uris: List<Uri>, framesPerSecond: Int): UUID? {
    if (uris.isEmpty()) return null
    val id = UUID.randomUUID()
    val dir = File(context.applicationContext.filesDir, "frame_jobs").apply { mkdirs() }
    val queue = File(dir, "$id.queue")
    val written = runCatching {
        queue.bufferedWriter().use { writer ->
            uris.distinct().forEach { uri -> writer.appendLine(uri.toString()) }
        }
        true
    }.getOrDefault(false)
    if (!written) return null

    val request = OneTimeWorkRequestBuilder<VideoFrameWorker>()
        .setId(id)
        .setInputData(workDataOf(KEY_QUEUE_FILE to queue.absolutePath, KEY_FPS to framesPerSecond.coerceIn(1, 4)))
        .addTag(VIDEO_FRAME_WORK_TAG)
        .build()
    WorkManager.getInstance(context.applicationContext).enqueue(request)
    return request.id
}

class VideoFrameWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())

        val queuePath = inputData.getString(KEY_QUEUE_FILE) ?: return Result.failure()
        val queue = File(queuePath)
        if (!queue.exists()) return Result.failure()
        val uris = runCatching {
            queue.useLines { lines -> lines.filter { it.isNotBlank() }.map(Uri::parse).toList() }
        }.getOrElse { return Result.failure() }
        if (uris.isEmpty()) {
            runCatching { queue.delete() }
            return Result.failure()
        }

        val fps = inputData.getInt(KEY_FPS, 1).coerceIn(1, 4)
        val engine = CompleteToolEngine(applicationContext)
        return try {
            val result = engine.extractVideoFrames(
                videos = uris,
                framesPerSecond = fps,
                moveSourceAfterSuccess = true
            ) { done, total ->
                setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
            }
            runCatching { queue.delete() }
            Result.success(
                Data.Builder()
                    .putInt(KEY_CREATED, result.created)
                    .putInt(KEY_FAILED, result.failed)
                    .build()
            )
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                FRAME_CHANNEL_ID,
                "ATMACA Video Kareleri",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val openApp = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(applicationContext, FRAME_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("ATMACA Video Kareleri")
            .setContentText("Videolar arka planda işleniyor")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
        return ForegroundInfo(
            FRAME_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}
