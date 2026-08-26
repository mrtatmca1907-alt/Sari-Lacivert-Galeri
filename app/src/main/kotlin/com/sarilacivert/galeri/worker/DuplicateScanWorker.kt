package com.sarilacivert.galeri.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.sarilacivert.galeri.data.DuplicateKind
import com.sarilacivert.galeri.data.GalleryPreferences
import com.sarilacivert.galeri.data.MediaItem
import com.sarilacivert.galeri.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque

class DuplicateScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repo = MediaRepository(applicationContext)
        val prefs = GalleryPreferences(applicationContext)
        val threshold = prefs.duplicateDistance.first()

        // Çift/benzer ekranı yalnızca fotoğrafları tarar. Videolar bu denetleyiciye dahil edilmez.
        val imageItems = repo.loadAll(showImages = true, showVideos = false)

        setProgress(Data.Builder().putInt(KEY_PROGRESS, 1).putString(KEY_STAGE, "Fotoğraflar hazırlanıyor").build())

        val exactGroups = findExactDuplicates(imageItems)
        setProgress(Data.Builder().putInt(KEY_PROGRESS, 50).putString(KEY_STAGE, "Benzer fotoğraflar taranıyor").build())

        val similarGroups = findSimilarImages(imageItems, threshold)

        val json = JSONObject().apply {
            put("generatedAt", System.currentTimeMillis())
            put("distance", threshold)
            put("groups", JSONArray().apply {
                exactGroups.forEach { put(groupToJson(DuplicateKind.EXACT, it)) }
                similarGroups.forEach { put(groupToJson(DuplicateKind.SIMILAR, it)) }
            })
        }
        resultFile(applicationContext).writeText(json.toString())

        setProgress(Data.Builder().putInt(KEY_PROGRESS, 100).putString(KEY_STAGE, "Tamamlandı").build())
        Result.success(
            Data.Builder()
                .putInt(KEY_EXACT_GROUPS, exactGroups.size)
                .putInt(KEY_SIMILAR_GROUPS, similarGroups.size)
                .build()
        )
    }

    private suspend fun findExactDuplicates(items: List<MediaItem>): List<List<MediaItem>> {
        val candidateGroups = items
            .filter { it.size > 0 }
            .groupBy { it.size }
            .values
            .filter { it.size > 1 }

        val out = mutableListOf<List<MediaItem>>()
        var processed = 0
        val total = candidateGroups.sumOf { it.size }.coerceAtLeast(1)

        for (sameSize in candidateGroups) {
            val byHash = linkedMapOf<String, MutableList<MediaItem>>()
            for (item in sameSize) {
                if (isStopped) return out
                sha256(item.uri)?.let { hash -> byHash.getOrPut(hash) { mutableListOf() }.add(item) }
                processed++
                if (processed % 3 == 0) {
                    val p = (processed * 45 / total).coerceIn(2, 48)
                    setProgress(Data.Builder().putInt(KEY_PROGRESS, p).putString(KEY_STAGE, "Aynı fotoğraflar karşılaştırılıyor").build())
                }
            }
            out += byHash.values.filter { it.size > 1 }
        }
        return out
    }

    private suspend fun findSimilarImages(items: List<MediaItem>, threshold: Int): List<List<MediaItem>> {
        if (items.size < 2) return emptyList()
        val union = UnionFind(items.size)
        val tree = BKTree()
        val resolver = applicationContext.contentResolver

        items.forEachIndexed { index, item ->
            if (isStopped) return emptyList()
            val hash = runCatching { differenceHash(resolver, item.uri) }.getOrNull()
            if (hash != null) {
                tree.query(hash, threshold).forEach { matchIndex -> union.union(index, matchIndex) }
                tree.insert(hash, index)
            }
            if (index % 8 == 0) {
                val p = 50 + (index * 48 / items.size.coerceAtLeast(1))
                setProgress(Data.Builder().putInt(KEY_PROGRESS, p.coerceAtMost(98)).putString(KEY_STAGE, "Benzer fotoğraflar karşılaştırılıyor").build())
            }
        }

        return items.indices
            .groupBy { union.find(it) }
            .values
            .filter { it.size > 1 }
            .map { indices -> indices.map { items[it] } }
            .filter { group -> group.map { it.uri }.distinct().size > 1 }
    }

    private fun sha256(uri: Uri): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return null
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun differenceHash(resolver: android.content.ContentResolver, uri: Uri): Long? {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.loadThumbnail(uri, Size(9, 8), null)
        } else {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } ?: return null

        val scaled = if (bitmap.width == 9 && bitmap.height == 8) bitmap else Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(scaled.getPixel(x, y))
                val right = luminance(scaled.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return hash
    }

    private fun luminance(color: Int): Int {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun groupToJson(kind: DuplicateKind, items: List<MediaItem>): JSONObject = JSONObject().apply {
        put("kind", kind.name)
        put("items", JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("uri", item.uri.toString())
                    put("name", item.name)
                    put("mime", item.mimeType)
                    put("size", item.size)
                    put("dateAdded", item.dateAdded)
                    put("dateTaken", item.dateTaken)
                    put("duration", item.duration)
                    put("width", item.width)
                    put("height", item.height)
                    put("albumPath", item.albumPath)
                    put("albumName", item.albumName)
                    put("video", item.isVideo)
                })
            }
        })
    }

    private class UnionFind(size: Int) {
        private val parent = IntArray(size) { it }
        private val rank = IntArray(size)
        fun find(x: Int): Int {
            if (parent[x] != x) parent[x] = find(parent[x])
            return parent[x]
        }
        fun union(a: Int, b: Int) {
            var ra = find(a)
            var rb = find(b)
            if (ra == rb) return
            if (rank[ra] < rank[rb]) { val t = ra; ra = rb; rb = t }
            parent[rb] = ra
            if (rank[ra] == rank[rb]) rank[ra]++
        }
    }

    private class BKTree {
        private data class Node(
            val hash: Long,
            val indexes: MutableList<Int> = mutableListOf(),
            val children: MutableMap<Int, Node> = mutableMapOf()
        )
        private var root: Node? = null

        fun insert(hash: Long, index: Int) {
            val r = root
            if (r == null) {
                root = Node(hash, mutableListOf(index))
                return
            }
            var node: Node = r
            while (true) {
                val distance = java.lang.Long.bitCount(node.hash xor hash)
                if (distance == 0) {
                    node.indexes += index
                    return
                }
                val child = node.children[distance]
                if (child == null) {
                    node.children[distance] = Node(hash, mutableListOf(index))
                    return
                }
                node = child
            }
        }

        fun query(hash: Long, maxDistance: Int): List<Int> {
            val start = root ?: return emptyList()
            val out = mutableListOf<Int>()
            val queue = ArrayDeque<Node>()
            queue.add(start)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val distance = java.lang.Long.bitCount(node.hash xor hash)
                if (distance <= maxDistance) out += node.indexes
                val min = (distance - maxDistance).coerceAtLeast(0)
                val max = distance + maxDistance
                node.children.forEach { (edge, child) -> if (edge in min..max) queue.add(child) }
            }
            return out
        }
    }

    companion object {
        const val UNIQUE_WORK = "duplicate_scan_v2"
        const val KEY_PROGRESS = "progress"
        const val KEY_STAGE = "stage"
        const val KEY_EXACT_GROUPS = "exact_groups"
        const val KEY_SIMILAR_GROUPS = "similar_groups"
        fun resultFile(context: Context): File = File(context.filesDir, "duplicate_results_v2.json")
    }
}
