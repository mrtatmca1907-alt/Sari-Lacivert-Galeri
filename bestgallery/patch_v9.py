from pathlib import Path
import runpy
import re

# V8 tabanini kur.
runpy.run_path('../bestgallery/patch_v8.py', run_name='__main__')

# Surum
gradle = Path('app/build.gradle')
gs = gradle.read_text()
gs = gs.replace('versionCode 20010013', 'versionCode 20010014')
gs = gs.replace('versionName "8.0-atmaca-no-file-probe"', 'versionName "9.0-atmaca-android13-direct"')
gradle.write_text(gs)

# -----------------------------------------------------------------------------
# Android 13: MediaStore.Files + DATA kolonunu tamamen birak.
# Images ve Video koleksiyonlarini ayri sorgula; RELATIVE_PATH + DISPLAY_NAME ile
# gercek dosya yolunu olustur. Boylece OEM'in DATA kolonunu engellemesi sorguyu
# sessizce bos listeye dusurmez.
# -----------------------------------------------------------------------------
fetcher = Path('app/src/main/kotlin/com/eagle/gallery/pro/helpers/MediaFetcher.kt')
fs = fetcher.read_text()

new_method = r'''    fun getAllMediaFast(isPickImage: Boolean, isPickVideo: Boolean, getProperDateTaken: Boolean,
                        getProperFileSize: Boolean, favoritePaths: ArrayList<String>, getVideoDurations: Boolean): ArrayList<Medium> {
        val filterMedia = context.config.filterMedia
        if (filterMedia == 0) return ArrayList()

        val config = context.config
        val result = ArrayList<Medium>()
        val seenPaths = HashSet<String>()
        val favoriteSet = HashSet<String>(favoritePaths)
        val baseStorage = android.os.Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

        fun addFromCollection(uri: android.net.Uri, videoCollection: Boolean) {
            val projection = if (videoCollection) {
                arrayOf("_display_name", "relative_path", "datetaken", "date_modified", "_size", "duration")
            } else {
                arrayOf("_display_name", "relative_path", "datetaken", "date_modified", "_size")
            }

            context.contentResolver.query(uri, projection, null, null, "date_added DESC")?.use { cursor ->
                val nameIndex = cursor.getColumnIndex("_display_name")
                val relativeIndex = cursor.getColumnIndex("relative_path")
                val takenIndex = cursor.getColumnIndex("datetaken")
                val modifiedIndex = cursor.getColumnIndex("date_modified")
                val sizeIndex = cursor.getColumnIndex("_size")
                val durationIndex = if (videoCollection) cursor.getColumnIndex("duration") else -1

                while (!shouldStop && cursor.moveToNext()) {
                    if (nameIndex < 0) continue
                    val filename = cursor.getString(nameIndex) ?: continue
                    if (filename.isEmpty()) continue
                    if (!config.shouldShowHidden && filename.startsWith('.')) continue

                    val relative = if (relativeIndex >= 0) cursor.getString(relativeIndex) ?: "" else ""
                    val cleanRelative = relative.trim('/')
                    val parentPath = if (cleanRelative.isEmpty()) baseStorage else "$baseStorage/$cleanRelative"
                    val path = "$parentPath/$filename"
                    if (!seenPaths.add(path)) continue
                    if (!parentPath.shouldFolderBeVisible(config.excludedFolders, config.includedFolders, config.shouldShowHidden)) continue

                    val type = if (videoCollection) {
                        TYPE_VIDEOS
                    } else {
                        when {
                            path.isGif() -> TYPE_GIFS
                            path.isRawFast() -> TYPE_RAWS
                            path.isSvg() -> TYPE_SVGS
                            else -> TYPE_IMAGES
                        }
                    }

                    if (type == TYPE_VIDEOS && (isPickImage || filterMedia and TYPE_VIDEOS == 0)) continue
                    if (type == TYPE_IMAGES && (isPickVideo || filterMedia and TYPE_IMAGES == 0)) continue
                    if (type == TYPE_GIFS && (isPickVideo || filterMedia and TYPE_GIFS == 0)) continue
                    if (type == TYPE_RAWS && (isPickVideo || filterMedia and TYPE_RAWS == 0)) continue
                    if (type == TYPE_SVGS && (isPickVideo || filterMedia and TYPE_SVGS == 0)) continue

                    val rawModified = if (modifiedIndex >= 0) cursor.getLong(modifiedIndex) else 0L
                    val modified = if (rawModified > 0L) rawModified * 1000L else 0L
                    val mediaDate = if (takenIndex >= 0) cursor.getLong(takenIndex) else 0L
                    val taken = if (getProperDateTaken && mediaDate > 0L) mediaDate else if (mediaDate > 0L) mediaDate else modified
                    val rawSize = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    val size = if (rawSize > 0L) rawSize else 1L
                    val durationLong = if (getVideoDurations && durationIndex >= 0) cursor.getLong(durationIndex) else 0L
                    val duration = if (durationLong > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else durationLong.toInt()

                    result.add(Medium(null, filename, path, parentPath, modified, taken, size, type, duration, favoriteSet.contains(path), 0L))
                }
            }
        }

        try {
            val wantsImages = !isPickVideo && (filterMedia and (TYPE_IMAGES or TYPE_GIFS or TYPE_RAWS or TYPE_SVGS) != 0)
            val wantsVideos = !isPickImage && filterMedia and TYPE_VIDEOS != 0
            if (wantsImages) addFromCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
            if (wantsVideos) addFromCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
        } catch (e: Exception) {
            result.clear()
        }

        return result
    }

'''

pattern = r'    fun getAllMediaFast\(.*?\n    fun getFolderMediaFast\('
match = re.search(pattern, fs, flags=re.S)
if not match:
    raise RuntimeError('V9 getAllMediaFast blogu bulunamadi')
fs = fs[:match.start()] + new_method + '    fun getFolderMediaFast(' + fs[match.end():]
fetcher.write_text(fs)

print('V9: Android 13 direct Images/Video MediaStore + RELATIVE_PATH uygulandi')
