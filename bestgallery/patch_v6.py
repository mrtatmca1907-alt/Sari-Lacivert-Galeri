from pathlib import Path
import runpy
import re

# V5 tabanini uygula, sonra Android 13'te sorun cikaran fiziksel File kontrollerini
# MediaStore kolonlariyla degistir. Amaç: bos galeri yok, tek sorgu, daha az disk I/O.
runpy.run_path('../bestgallery/patch_v5.py', run_name='__main__')


def replace_regex(text, pattern, repl, label):
    new_text, count = re.subn(pattern, repl, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f'V6 patch hedefi bulunamadi: {label}')
    return new_text


# -----------------------------------------------------------------------------
# 0) Surum
# -----------------------------------------------------------------------------
gradle = Path('app/build.gradle')
gs = gradle.read_text()
gs = gs.replace('versionCode 20010010', 'versionCode 20010011')
gs = gs.replace('versionName "5.0-atmaca-modern"', 'versionName "6.0-atmaca-mediastore"')
gradle.write_text(gs)


# -----------------------------------------------------------------------------
# 1) MediaFetcher: File.isFile / File.length / File.lastModified YOK.
#    Liste bilgileri direkt MediaStore'dan gelir. Ana sorguda selection da yok;
#    OEM'lerin eski DATA + karma OR sorgularini reddetmesi engellenir.
# -----------------------------------------------------------------------------
fetcher = Path('app/src/main/kotlin/com/eagle/gallery/pro/helpers/MediaFetcher.kt')
fs = fetcher.read_text()

all_method = r'''    fun getAllMediaFast(isPickImage: Boolean, isPickVideo: Boolean, getProperDateTaken: Boolean,
                        getProperFileSize: Boolean, favoritePaths: ArrayList<String>, getVideoDurations: Boolean): ArrayList<Medium> {
        val filterMedia = context.config.filterMedia
        if (filterMedia == 0) return ArrayList()

        val config = context.config
        val result = ArrayList<Medium>()
        val seenPaths = HashSet<String>()
        val favoriteSet = HashSet<String>(favoritePaths)
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE
        )

        try {
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use { cursor ->
                val pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val takenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val modifiedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)

                while (!shouldStop && cursor.moveToNext()) {
                    if (pathIndex < 0) continue
                    val path = cursor.getString(pathIndex) ?: continue
                    if (path.isEmpty() || !seenPaths.add(path)) continue

                    val filename = if (nameIndex >= 0) cursor.getString(nameIndex) ?: path.substringAfterLast('/') else path.substringAfterLast('/')
                    val parentPath = path.substringBeforeLast('/', "")
                    if (filename.isEmpty() || parentPath.isEmpty()) continue
                    if (!parentPath.shouldFolderBeVisible(config.excludedFolders, config.includedFolders, config.shouldShowHidden)) continue
                    if (!config.shouldShowHidden && (filename.startsWith('.') || path.contains("/."))) continue

                    val isImage = path.isImageFast()
                    val isVideo = if (isImage) false else path.isVideoFast()
                    val isGif = if (isImage || isVideo) false else path.isGif()
                    val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
                    val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

                    if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) continue
                    if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0)) continue
                    if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0)) continue
                    if (isGif && filterMedia and TYPE_GIFS == 0) continue
                    if (isRaw && filterMedia and TYPE_RAWS == 0) continue
                    if (isSvg && filterMedia and TYPE_SVGS == 0) continue

                    val type = when {
                        isVideo -> TYPE_VIDEOS
                        isGif -> TYPE_GIFS
                        isRaw -> TYPE_RAWS
                        isSvg -> TYPE_SVGS
                        else -> TYPE_IMAGES
                    }

                    val mediaDate = if (takenIndex >= 0) cursor.getLong(takenIndex) else 0L
                    val modifiedSeconds = if (modifiedIndex >= 0) cursor.getLong(modifiedIndex) else 0L
                    val modified = if (modifiedSeconds > 0L) modifiedSeconds * 1000L else mediaDate
                    val taken = if (getProperDateTaken && mediaDate > 0L) mediaDate else modified
                    val rawSize = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    val size = if (rawSize > 0L) rawSize else 1L
                    val videoDuration = if (getVideoDurations && isVideo) path.getVideoDuration() else 0

                    result.add(Medium(null, filename, path, parentPath, modified, taken, size, type, videoDuration, favoriteSet.contains(path), 0L))
                }
            }
        } catch (e: Exception) {
            result.clear()
        }

        sortMedia(result, config.getFileSorting(SHOW_ALL))
        return result
    }

'''

fs = replace_regex(
    fs,
    r'    fun getAllMediaFast\(.*?\n    \}\n\n(?=    fun getFolderMediaFast\(|    private fun getSelectionQuery\()',
    all_method,
    'getAllMediaFast'
)

folder_method = r'''    fun getFolderMediaFast(folder: String, isPickImage: Boolean, isPickVideo: Boolean, getProperDateTaken: Boolean,
                           getProperFileSize: Boolean, favoritePaths: ArrayList<String>, getVideoDurations: Boolean): ArrayList<Medium> {
        val filterMedia = context.config.filterMedia
        if (filterMedia == 0) return ArrayList()

        val config = context.config
        val result = ArrayList<Medium>()
        val seenPaths = HashSet<String>()
        val favoriteSet = HashSet<String>(favoritePaths)
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE
        )
        val selection = "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
        val selectionArgs = arrayOf("$folder/%", "$folder/%/%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use { cursor ->
                val pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val takenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val modifiedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)

                while (!shouldStop && cursor.moveToNext()) {
                    if (pathIndex < 0) continue
                    val path = cursor.getString(pathIndex) ?: continue
                    if (path.isEmpty() || !seenPaths.add(path)) continue

                    val filename = if (nameIndex >= 0) cursor.getString(nameIndex) ?: path.substringAfterLast('/') else path.substringAfterLast('/')
                    if (filename.isEmpty()) continue
                    if (!config.shouldShowHidden && filename.startsWith('.')) continue

                    val isImage = path.isImageFast()
                    val isVideo = if (isImage) false else path.isVideoFast()
                    val isGif = if (isImage || isVideo) false else path.isGif()
                    val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
                    val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

                    if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) continue
                    if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0)) continue
                    if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0)) continue
                    if (isGif && filterMedia and TYPE_GIFS == 0) continue
                    if (isRaw && filterMedia and TYPE_RAWS == 0) continue
                    if (isSvg && filterMedia and TYPE_SVGS == 0) continue

                    val type = when {
                        isVideo -> TYPE_VIDEOS
                        isGif -> TYPE_GIFS
                        isRaw -> TYPE_RAWS
                        isSvg -> TYPE_SVGS
                        else -> TYPE_IMAGES
                    }

                    val mediaDate = if (takenIndex >= 0) cursor.getLong(takenIndex) else 0L
                    val modifiedSeconds = if (modifiedIndex >= 0) cursor.getLong(modifiedIndex) else 0L
                    val modified = if (modifiedSeconds > 0L) modifiedSeconds * 1000L else mediaDate
                    val taken = if (getProperDateTaken && mediaDate > 0L) mediaDate else modified
                    val rawSize = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    val size = if (rawSize > 0L) rawSize else 1L
                    val videoDuration = if (getVideoDurations && isVideo) path.getVideoDuration() else 0

                    result.add(Medium(null, filename, path, folder, modified, taken, size, type, videoDuration, favoriteSet.contains(path), 0L))
                }
            }
        } catch (e: Exception) {
            // Basit MediaStore sorgusu hata verirse eski yol son care olarak kalsin.
            return getFilesFrom(folder, isPickImage, isPickVideo, getProperDateTaken, getProperFileSize, favoritePaths, getVideoDurations)
        }

        sortMedia(result, config.getFileSorting(folder))
        return result
    }

'''

fs = replace_regex(
    fs,
    r'    fun getFolderMediaFast\(.*?\n    \}\n\n(?=    private fun getSelectionQuery\()',
    folder_method,
    'getFolderMediaFast'
)
fetcher.write_text(fs)


# -----------------------------------------------------------------------------
# 2) Ana albüm listesi: MediaStore zaten bize gecerli medya verdi; her kayitta
#    tekrar File.isFile yapma. Bu Android 13'te tum listeyi bosaltabiliyordu.
# -----------------------------------------------------------------------------
main = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MainActivity.kt')
ms = main.read_text()
ms = ms.replace(
'''                allMedia.forEach { medium ->
                    if (File(medium.path).isFile) {
                        grouped.getOrPut(medium.parentPath) { ArrayList() }.add(medium)
                    }
                }''',
'''                allMedia.forEach { medium ->
                    grouped.getOrPut(medium.parentPath) { ArrayList() }.add(medium)
                }'''
)
ms = ms.replace(
'''                allMedia.filterTo(favoriteMedia) { favoriteSet.contains(it.path) && File(it.path).isFile }''',
'''                allMedia.filterTo(favoriteMedia) { favoriteSet.contains(it.path) }'''
)
main.write_text(ms)


# -----------------------------------------------------------------------------
# 3) Grid temizleme: tekrar File.isFile ile bütün sonuçları silme.
#    MediaStore sorgusundan gelen path + dedupe yeterli.
# -----------------------------------------------------------------------------
media = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MediaActivity.kt')
ma = media.read_text()
ma = ma.replace(
'''        media.mapNotNull { it as? Medium }.forEach {
            val file = File(it.path)
            if (file.isFile && file.length() > 0L && seenPaths.add(it.path)) {
                validMedia.add(it)
            }
        }''',
'''        media.mapNotNull { it as? Medium }.forEach {
            if (it.path.isNotEmpty() && seenPaths.add(it.path)) {
                validMedia.add(it)
            }
        }'''
)
media.write_text(ma)


# -----------------------------------------------------------------------------
# 4) Foto görüntüleyici: silme/taşıma sonrası sadece işlem yapılan path çıkar.
#    Android 13'te File.isFile false dönerse tüm açık albümü yanlışlıkla temizleme.
# -----------------------------------------------------------------------------
vp = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/ViewPagerActivity.kt')
vs = vp.read_text()
vs = vs.replace(
'''        mMediaFiles.removeAll { it.path == path || !File(it.path).isFile }
        com.eagle.gallery.pro.activities.MediaActivity.Companion.mMedia.removeAll {
            val medium = it as? Medium
            medium != null && (medium.path == path || !File(medium.path).isFile)
        }''',
'''        mMediaFiles.removeAll { it.path == path }
        com.eagle.gallery.pro.activities.MediaActivity.Companion.mMedia.removeAll {
            val medium = it as? Medium
            medium != null && medium.path == path
        }'''
)
vp.write_text(vs)

print('V6 Android 13 MediaStore listeleme duzeltmesi uygulandi')
