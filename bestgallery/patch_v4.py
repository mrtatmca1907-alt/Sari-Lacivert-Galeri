from pathlib import Path
import runpy

# Önce V3 marka/tema/reklamsız ve temel kararlılık yamalarını uygula.
runpy.run_path('../bestgallery/patch_v3.py', run_name='__main__')


def replace_once(text, old, new, label):
    if old not in text:
        raise RuntimeError(f"Patch hedefi bulunamadi: {label}")
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# 1) Normal klasörler de File.listFiles ile tek tek taranmasın.
#    MediaStore tek sorguda klasörün gerçek medya dosyalarını döndürsün.
# -----------------------------------------------------------------------------
fetcher = Path('app/src/main/kotlin/com/eagle/gallery/pro/helpers/MediaFetcher.kt')
fs = fetcher.read_text()
folder_fast = r'''
    fun getFolderMediaFast(folder: String, isPickImage: Boolean, isPickVideo: Boolean, getProperDateTaken: Boolean,
                           getProperFileSize: Boolean, favoritePaths: ArrayList<String>, getVideoDurations: Boolean): ArrayList<Medium> {
        val filterMedia = context.config.filterMedia
        if (filterMedia == 0) return ArrayList()

        val config = context.config
        val result = ArrayList<Medium>()
        val seenPaths = HashSet<String>()
        val favoriteSet = HashSet<String>(favoritePaths)
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_TAKEN)
        val selection = getSelectionQuery(filterMedia) + "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
        val selectionArgs = getSelectionArgsQuery(filterMedia).apply {
            add("$folder/%")
            add("$folder/%/%")
        }.toTypedArray()

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val takenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                while (!shouldStop && cursor.moveToNext()) {
                    if (pathIndex < 0) continue
                    val path = cursor.getString(pathIndex) ?: continue
                    if (!seenPaths.add(path)) continue

                    val file = File(path)
                    if (!file.isFile) continue
                    val size = file.length()
                    if (size <= 0L) continue

                    val filename = file.name
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
                    val modified = file.lastModified()
                    val mediaDate = if (getProperDateTaken && takenIndex >= 0) cursor.getLong(takenIndex) else 0L
                    val taken = if (mediaDate > 0L) mediaDate else modified
                    val videoDuration = if (getVideoDurations && isVideo) path.getVideoDuration() else 0
                    result.add(Medium(null, filename, path, folder, modified, taken, size, type, videoDuration, favoriteSet.contains(path), 0L))
                }
            }
        } catch (e: Exception) {
            // OEM MediaStore sorgusu hata verirse eski güvenli klasör taramasına dön.
            return getFilesFrom(folder, isPickImage, isPickVideo, getProperDateTaken, getProperFileSize, favoritePaths, getVideoDurations)
        }

        sortMedia(result, config.getFileSorting(folder))
        return result
    }

'''
needle = '    private fun getSelectionQuery(filterMedia: Int): String {'
fs = replace_once(fs, needle, folder_fast + needle, 'MediaFetcher fast folder')
fetcher.write_text(fs)

# -----------------------------------------------------------------------------
# 2) Async tarama: normal klasörlerde de hızlı MediaStore yolunu kullan.
#    FAVORITES / RECYCLE_BIN / OTG özel davranışları eski güvenli yolda kalsın.
# -----------------------------------------------------------------------------
task = Path('app/src/main/kotlin/com/eagle/gallery/pro/asynctasks/GetMediaAsynctask.kt')
ts = task.read_text()
old_branch = '''        val media = if (showAll) {
            mediaFetcher.getAllMediaFast(isPickImage, isPickVideo, getProperDateTaken, getProperFileSize, favoritePaths, getVideoDurations)
        } else {
            mediaFetcher.getFilesFrom(mPath, isPickImage, isPickVideo, getProperDateTaken, getProperFileSize, favoritePaths, getVideoDurations)
        }'''
new_branch = '''        val media = if (showAll) {
            mediaFetcher.getAllMediaFast(isPickImage, isPickVideo, getProperDateTaken, getProperFileSize, favoritePaths, getVideoDurations)
        } else if (mPath == FAVORITES || mPath == RECYCLE_BIN || mPath == context.config.OTGPath) {
            mediaFetcher.getFilesFrom(mPath, isPickImage, isPickVideo, getProperDateTaken, getProperFileSize, favoritePaths, getVideoDurations)
        } else {
            mediaFetcher.getFolderMediaFast(mPath, isPickImage, isPickVideo, getProperDateTaken, getProperFileSize, favoritePaths, getVideoDurations)
        }'''
ts = replace_once(ts, old_branch, new_branch, 'GetMedia fast folder branch')
task.write_text(ts)

# -----------------------------------------------------------------------------
# 3) Tam ekran görüntüleyicide silinen/taşınan fotoğrafı anında listeden çıkar.
#    Eski kod dosya işlemi bitse bile yeniden taramayı beklediği için silinen foto
#    ekranda duruyordu. Önce UI state temizleniyor, sonra arka planda doğrulama var.
# -----------------------------------------------------------------------------
vp = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/ViewPagerActivity.kt')
vs = vp.read_text()

helper_needle = '    private fun deleteDirectoryIfEmpty() {'
helper = r'''
    private fun removePathImmediately(path: String) {
        val oldPos = mPos.coerceAtLeast(0)
        mMediaFiles.removeAll { it.path == path || !File(it.path).isFile }
        com.eagle.gallery.pro.activities.MediaActivity.Companion.mMedia.removeAll {
            val medium = it as? Medium
            medium != null && (medium.path == path || !File(medium.path).isFile)
        }

        if (mMediaFiles.isEmpty()) {
            finish()
            return
        }

        mPos = oldPos.coerceAtMost(mMediaFiles.lastIndex)
        updatePagerItems(mMediaFiles)
        view_pager.setCurrentItem(mPos, false)
        supportActionBar?.title = mMediaFiles[mPos].name
        invalidateOptionsMenu()
    }

'''
vs = replace_once(vs, helper_needle, helper + helper_needle, 'ViewPager immediate remove helper')

old_delete = '''        val fileDirItem = FileDirItem(path, path.getFilenameFromPath())
        if (config.useRecycleBin && !getCurrentMedium()!!.getIsInRecycleBin()) {
            movePathsInRecycleBin(arrayListOf(path)) {
                if (it) {
                    tryDeleteFileDirItem(fileDirItem, false, false) {
                        refreshViewPager()
                    }
                } else {
                    toast(R.string.unknown_error_occurred)
                }
            }
        } else {
            tryDeleteFileDirItem(fileDirItem, false, true) {
                refreshViewPager()
            }
        }'''
new_delete = '''        val fileDirItem = FileDirItem(path, path.getFilenameFromPath())
        if (config.useRecycleBin && !getCurrentMedium()!!.getIsInRecycleBin()) {
            movePathsInRecycleBin(arrayListOf(path)) {
                if (it) {
                    tryDeleteFileDirItem(fileDirItem, false, false) {
                        removePathImmediately(path)
                        Handler().postDelayed({ if (!isFinishing && !isDestroyed) refreshViewPager() }, 250L)
                    }
                } else {
                    toast(R.string.unknown_error_occurred)
                }
            }
        } else {
            tryDeleteFileDirItem(fileDirItem, false, true) {
                removePathImmediately(path)
                Handler().postDelayed({ if (!isFinishing && !isDestroyed) refreshViewPager() }, 250L)
            }
        }'''
vs = replace_once(vs, old_delete, new_delete, 'ViewPager delete immediate')

old_move = '''        val fileDirItems = arrayListOf(FileDirItem(currPath, currPath.getFilenameFromPath()))
        tryCopyMoveFilesTo(fileDirItems, isCopyOperation) {
            config.tempFolderPath = ""
            if (!isCopyOperation) {
                refreshViewPager()
                updateFavoritePaths(fileDirItems, it)
            }'''
new_move = '''        val fileDirItems = arrayListOf(FileDirItem(currPath, currPath.getFilenameFromPath()))
        tryCopyMoveFilesTo(fileDirItems, isCopyOperation) {
            config.tempFolderPath = ""
            if (!isCopyOperation) {
                removePathImmediately(currPath)
                Handler().postDelayed({ if (!isFinishing && !isDestroyed) refreshViewPager() }, 250L)
                updateFavoritePaths(fileDirItems, it)
            }'''
vs = replace_once(vs, old_move, new_move, 'ViewPager move immediate')
vp.write_text(vs)

# -----------------------------------------------------------------------------
# 4) Grid küçük resimlerinde yapay 100ms gecikmeyi kaldır.
# -----------------------------------------------------------------------------
adapter = Path('app/src/main/kotlin/com/eagle/gallery/pro/adapters/MediaAdapter.kt')
ads = adapter.read_text().replace('private val IMAGE_LOAD_DELAY = 100L', 'private val IMAGE_LOAD_DELAY = 0L')
adapter.write_text(ads)

print('V4 kararlilik ve hiz yamalari uygulandi')
