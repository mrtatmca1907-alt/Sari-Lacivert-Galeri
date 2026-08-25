package com.sarilacivert.galeri.data

import android.content.Context
import android.net.Uri
import com.sarilacivert.galeri.worker.DuplicateScanWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object DuplicateResultStore {
    suspend fun load(context: Context): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val file = DuplicateScanWorker.resultFile(context)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            val root = JSONObject(file.readText())
            val groups = root.getJSONArray("groups")
            buildList {
                for (i in 0 until groups.length()) {
                    val group = groups.getJSONObject(i)
                    val kind = DuplicateKind.valueOf(group.getString("kind"))
                    val itemsJson = group.getJSONArray("items")
                    val items = buildList {
                        for (j in 0 until itemsJson.length()) {
                            val o = itemsJson.getJSONObject(j)
                            add(
                                MediaItem(
                                    id = o.getLong("id"),
                                    uri = Uri.parse(o.getString("uri")),
                                    name = o.getString("name"),
                                    mimeType = o.optString("mime"),
                                    size = o.optLong("size"),
                                    dateAdded = o.optLong("dateAdded"),
                                    dateTaken = o.optLong("dateTaken"),
                                    duration = o.optLong("duration"),
                                    width = o.optInt("width"),
                                    height = o.optInt("height"),
                                    albumPath = o.optString("albumPath"),
                                    albumName = o.optString("albumName"),
                                    isVideo = o.optBoolean("video")
                                )
                            )
                        }
                    }
                    if (items.size > 1) add(DuplicateGroup(kind, items))
                }
            }
        }.getOrDefault(emptyList())
    }
}
