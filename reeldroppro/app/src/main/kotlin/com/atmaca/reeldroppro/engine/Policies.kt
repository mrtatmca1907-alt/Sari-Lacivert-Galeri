package com.atmaca.reeldroppro.engine

import java.util.Locale
import kotlin.random.Random

typealias JobState = com.atmaca.reeldroppro.model.JobState

object BackoffJitter {
    fun cap(delayMs: Long): Long = delayMs.coerceAtMost(300_000L)

    fun apply(baseMs: Long, seed: Long): Long {
        val random = Random(seed)
        val factor = 0.8 + (random.nextDouble() * 0.4)
        return cap((baseMs * factor).toLong())
    }
}

object ConcurrencyPolicy {
    fun limit(lowMemory: Boolean, thermalSevere: Boolean): Int =
        if (lowMemory || thermalSevere) 1 else 3
}

object DiagnosticRedaction {
    fun redact(text: String): String = text
        .replace(Regex("(?im)^Cookie:\\s*.*$"), "Cookie: [REDACTED]")
        .replace(Regex("(?im)^Authorization:\\s*.*$"), "Authorization: [REDACTED]")
        .replace(Regex("(?i)(sessionid|csrftoken|cookie)=([^\\s;]+)"), "$1=[REDACTED]")
}

object DownloadName {
    fun normalize(raw: String): String = raw
        .trim()
        .replace(Regex("[\\s/\\\\:*?\"<>|]+"), "_")
        .trim('_', '.')
        .ifBlank { "media" }
}

object FailureMessagePolicy {
    fun message(exitCode: Int, stderr: String?): String {
        val detail = stderr?.trim().orEmpty()
        return if (detail.isNotBlank()) detail else "Extractor çıkış kodu: $exitCode"
    }
}

object FilenamePolicy {
    fun fileName(title: String, stableId: String, extension: String): String {
        val normalizedTitle = DownloadName.normalize(title)
            .lowercase(Locale.ROOT)
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
        val id = DownloadName.normalize(stableId)
        val ext = extension.trim().trimStart('.').lowercase(Locale.ROOT).ifBlank { "bin" }
        return "${normalizedTitle}_[${id}].$ext"
    }
}

object ForegroundReason {
    fun requiresForeground(state: JobState): Boolean = state in setOf(
        JobState.RESOLVING,
        JobState.DOWNLOADING,
        JobState.POST_PROCESSING
    )
}

object MediaStoreNamePolicy {
    fun displayName(title: String, stableId: String, extension: String): String =
        FilenamePolicy.fileName(title, stableId, extension)
}

enum class MediaKind { PHOTO, VIDEO, OTHER }

object MediaTypePolicy {
    private val photo = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif")
    private val video = setOf("mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp")

    fun fromExtension(extension: String): MediaKind = when (extension.trim().trimStart('.').lowercase(Locale.ROOT)) {
        in photo -> MediaKind.PHOTO
        in video -> MediaKind.VIDEO
        else -> MediaKind.OTHER
    }
}

object QueueStatePolicy {
    fun onNetworkLost(state: JobState): JobState = when (state) {
        JobState.RESOLVING, JobState.DOWNLOADING, JobState.POST_PROCESSING -> JobState.RETRY_WAIT
        else -> state
    }
}

object ResumePolicy {
    fun onConnectivityRestored(state: JobState): JobState =
        if (state == JobState.RETRY_WAIT) JobState.QUEUED else state
}

data class DownloadProgress(
    val photos: Int = 0,
    val videos: Int = 0,
    val failed: Int = 0,
    val bytesDownloaded: Long = 0L
)

sealed interface ProgressEvent {
    data class PhotoCompleted(val sizeBytes: Long) : ProgressEvent
    data class VideoCompleted(val sizeBytes: Long) : ProgressEvent
    data object Failed : ProgressEvent
    data class BytesDownloaded(val bytes: Long) : ProgressEvent
}

object ProgressReducer {
    fun apply(state: DownloadProgress, event: ProgressEvent): DownloadProgress = when (event) {
        is ProgressEvent.PhotoCompleted -> state.copy(photos = state.photos + 1)
        is ProgressEvent.VideoCompleted -> state.copy(videos = state.videos + 1)
        ProgressEvent.Failed -> state.copy(failed = state.failed + 1)
        is ProgressEvent.BytesDownloaded -> state.copy(bytesDownloaded = state.bytesDownloaded + event.bytes)
    }

    fun applyAll(initial: DownloadProgress, events: List<ProgressEvent>): DownloadProgress =
        events.fold(initial, ::apply)
}

data class QueueMetrics(
    val active: Int,
    val completed: Int,
    val failed: Int,
    val waiting: Int
) {
    companion object {
        fun from(states: List<JobState>): QueueMetrics = QueueMetrics(
            active = states.count { it == JobState.RESOLVING || it == JobState.DOWNLOADING || it == JobState.POST_PROCESSING },
            completed = states.count { it == JobState.COMPLETED },
            failed = states.count { it == JobState.FAILED },
            waiting = states.count { it == JobState.QUEUED || it == JobState.RETRY_WAIT }
        )
    }
}

data class QueueCandidate(
    val id: String,
    val state: JobState,
    val nextAttemptAt: Long?
)

object QueueSelectionPolicy {
    fun next(jobs: List<QueueCandidate>, nowMs: Long): QueueCandidate? {
        val dueRetry = jobs.firstOrNull { it.state == JobState.RETRY_WAIT && (it.nextAttemptAt ?: Long.MAX_VALUE) <= nowMs }
        return dueRetry ?: jobs.firstOrNull { it.state == JobState.QUEUED }
    }
}

object RetryClassificationPolicy {
    fun retryable(kind: DownloadError.Kind): Boolean = when (kind) {
        DownloadError.Kind.NETWORK,
        DownloadError.Kind.RATE_LIMITED,
        DownloadError.Kind.STORAGE_FULL,
        DownloadError.Kind.EXTRACTOR,
        DownloadError.Kind.UNKNOWN -> true
        DownloadError.Kind.AUTH_REQUIRED,
        DownloadError.Kind.REMOVED,
        DownloadError.Kind.UNSUPPORTED -> false
    }
}

enum class StorageDecision { RUN, WAIT_FOR_SPACE }

object StoragePressurePolicy {
    private const val SAFETY_MARGIN_BYTES = 128L * 1024L * 1024L

    fun decide(freeBytes: Long, expectedBytes: Long): StorageDecision =
        if (freeBytes >= expectedBytes + SAFETY_MARGIN_BYTES) StorageDecision.RUN else StorageDecision.WAIT_FOR_SPACE
}

object TempFilePolicy {
    fun tempName(finalName: String): String = if (finalName.endsWith(".part")) finalName else "$finalName.part"
    fun finalName(tempName: String): String = tempName.removeSuffix(".part")
}

object ValidationPolicy {
    fun valid(sizeBytes: Long, fileName: String, expectedExtension: String): Boolean {
        if (sizeBytes <= 0L) return false
        val expected = expectedExtension.trim().trimStart('.').lowercase(Locale.ROOT)
        val actual = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return expected.isNotBlank() && actual == expected
    }
}
