from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

# Extend MediaStore metadata so 'modified' and 'taken' are real columns, not aliases.
repo_path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt")
repo = repo_path.read_text(encoding="utf-8")
repo = replace_once(repo,
    "    val dateAdded: Long,\n    val width: Int,",
    "    val dateAdded: Long,\n    val dateModified: Long,\n    val dateTaken: Long,\n    val width: Int,",
    "GalleryMedia dates")
repo = replace_once(repo,
    "        private val date = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)\n        private val width",
    "        private val date = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)\n        private val dateModified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)\n        private val dateTaken = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)\n        private val width",
    "cursor date columns")
repo = replace_once(repo,
    "                dateAdded = cursor.getLong(date),\n                width = cursor.getInt(width),",
    "                dateAdded = cursor.getLong(date),\n                dateModified = cursor.getLong(dateModified),\n                dateTaken = if (dateTaken >= 0) cursor.getLong(dateTaken) else 0L,\n                width = cursor.getInt(width),",
    "read dates")
repo = replace_once(repo,
    "            add(MediaStore.MediaColumns.DATE_ADDED)\n            add(MediaStore.MediaColumns.WIDTH)",
    "            add(MediaStore.MediaColumns.DATE_ADDED)\n            add(MediaStore.MediaColumns.DATE_MODIFIED)\n            add(MediaStore.MediaColumns.DATE_TAKEN)\n            add(MediaStore.MediaColumns.WIDTH)",
    "projection dates")
repo_path.write_text(repo, encoding="utf-8")

# Base home patch creates the settings UI; upgrade its filter/sort implementation here.
app_path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
app = app_path.read_text(encoding="utf-8")
app = app.replace("MediaSort.NEWEST.name", "MediaSort.TAKEN.name")
app = app.replace("}.getOrDefault(MediaSort.NEWEST)", "}.getOrDefault(MediaSort.TAKEN)")
app = replace_once(app,
    "        val filtered = state.items.filter { mediaFilterAccepts(it.isVideo, mediaFilter) }\n        when (mediaSort) {\n            MediaSort.NEWEST -> filtered.sortedByDescending { it.dateAdded }\n            MediaSort.OLDEST -> filtered.sortedBy { it.dateAdded }\n            MediaSort.NAME -> filtered.sortedBy { it.name.lowercase() }\n        }",
    "        val filtered = state.items.filter { mediaFilterAccepts(it.isVideo, it.mimeType, mediaFilter) }\n        when (mediaSort) {\n            MediaSort.NAME -> filtered.sortedBy { it.name.lowercase() }\n            MediaSort.PATH -> filtered.sortedWith(compareBy({ it.relativePath.lowercase() }, { it.name.lowercase() }))\n            MediaSort.SIZE -> filtered.sortedByDescending { it.size }\n            MediaSort.MODIFIED -> filtered.sortedByDescending { it.dateModified }\n            MediaSort.TAKEN -> filtered.sortedByDescending { if (it.dateTaken > 0L) it.dateTaken else it.dateAdded * 1000L }\n            MediaSort.RANDOM -> filtered.shuffled()\n        }",
    "advanced visible sorting")
app_path.write_text(app, encoding="utf-8")
print("Advanced media settings patch applied")
