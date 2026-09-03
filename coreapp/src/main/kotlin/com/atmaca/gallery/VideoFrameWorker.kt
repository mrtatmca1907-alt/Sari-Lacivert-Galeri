package com.atmaca.gallery

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
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
const val VIDEO_FRAME_WORK_TAG = "atmaca_video_frames"

fun enqueueVideoFrameWork(context: Context, uris: List<Uri>, framesPerSecond: Int): UUID? {
    if (uris.isEmpty()) return null
    val id = UUID.randomUUID()
    val dir = File(context.applicationContext.filesDir, "frame_jobs").apply { mkdirs() }
    val queue = File(dir, "$id.queue")
    val written = runCatching {
        queue.bufferedWriter().use { writer ->
            uris.distinct().forEach { uri ->
                writer.appendLine(uri.toString())
            }
        }
        true
    }.getOrDefault(false)
    if (!written) return null

    val request = OneTimeWorkRequestBuilder<VideoFrameWorker>()
        .setId(id)
        .setInputData(
            workDataOf(
                KEY_QUEUE_FILE to queue.absolutePath,
                KEY_FPS to framesPerSecond.coerceIn(1, 4)
            )
        )
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
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
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
}
