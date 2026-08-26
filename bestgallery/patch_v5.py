from pathlib import Path
import runpy
import re

# V4 tabanini kur, sonra eski klasor/cache/polling motorunu kaldir.
runpy.run_path('../bestgallery/patch_v4.py', run_name='__main__')


def replace_once(text, old, new, label):
    if old not in text:
        raise RuntimeError(f"Patch hedefi bulunamadi: {label}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# 0) Surum ve yeni Atmaca ikonu
# -----------------------------------------------------------------------------
gradle = Path('app/build.gradle')
gs = gradle.read_text()
gs = gs.replace('versionCode 20010009', 'versionCode 20010010')
gs = gs.replace('versionName "2.0.9"', 'versionName "5.0-atmaca-modern"')
gradle.write_text(gs)

icon = Path('app/src/main/res/drawable/ic_sari_lacivert_gallery.xml')
icon.write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#061326" android:pathData="M0,0H108V108H0Z"/>
    <path android:fillColor="#0B1D3A" android:pathData="M7,7H101V101H7Z"/>
    <path android:fillColor="#F4C430" android:pathData="M14,16H94V88H14Z"/>
    <path android:fillColor="#081426" android:pathData="M19,21H89V83H19Z"/>

    <!-- Atmaca: sivri kanat + ince kafa + kancali gaga -->
    <path android:fillColor="#F4C430" android:pathData="M16,64 C28,42 43,30 62,27 C51,34 45,40 41,47 C54,39 68,37 81,42 C72,44 65,48 60,54 C69,51 77,52 84,57 C76,58 70,61 66,65 C72,67 78,71 82,77 C70,72 58,70 48,72 C37,74 28,79 20,86 C25,76 31,69 39,64 C31,65 23,66 16,64Z"/>
    <path android:fillColor="#FFF7D6" android:pathData="M58,45 C66,40 76,40 84,44 C89,47 91,51 92,55 C87,53 83,53 79,55 C83,57 87,61 89,66 C82,62 75,59 68,59 C63,58 60,54 58,45Z"/>
    <path android:fillColor="#F4C430" android:pathData="M82,48 L98,54 L84,59 C86,55 85,52 82,48Z"/>
    <path android:fillColor="#061326" android:pathData="M75,47a3,3 0,1 0,6,0a3,3 0,1 0,-6,0"/>
    <path android:fillColor="#F4C430" android:pathData="M28,82 L42,67 L51,75 L58,68 L72,82Z"/>
</vector>
''')


# -----------------------------------------------------------------------------
# 1) Ana albüm ekrani: Room cache + klasor klasor tarama TAMAMEN devre disi.
#    Tek MediaStore sorgusu -> tek seferde tum klasorler -> tek UI guncellemesi.
# -----------------------------------------------------------------------------
main = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MainActivity.kt')
ms = main.read_text()
if 'import android.database.ContentObserver' not in ms:
    ms = ms.replace('import android.content.*\n', 'import android.content.*\nimport android.database.ContentObserver\n')

ms = replace_once(
    ms,
    '    private var mLastMediaHandler = Handler()\n',
    '    private var mLastMediaHandler = Handler()\n'
    '    private var mModernMediaObserver: ContentObserver? = null\n'
    '    private var mModernMediaHandler = Handler()\n'
    '    private var mPendingDirectoryRefresh = false\n',
    'Main observer fields'
)

ms = replace_once(
    ms,
    '        registerFileUpdateListener()\n        registerReceiver()\n',
    '        registerFileUpdateListener()\n        registerModernMediaObserver()\n        registerReceiver()\n',
    'Main observer register'
)

ms = replace_once(
    ms,
    '            unregisterFileUpdateListener()\n\n            if (!config.showAll) {',
    '            unregisterFileUpdateListener()\n            unregisterModernMediaObserver()\n\n            if (!config.showAll) {',
    'Main observer unregister'
)

observer_methods = r'''
    private fun registerModernMediaObserver() {
        if (mModernMediaObserver != null) return
        mModernMediaObserver = object : ContentObserver(mModernMediaHandler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                mModernMediaHandler.removeCallbacksAndMessages(null)
                mModernMediaHandler.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        if (mIsGettingDirs) {
                            mPendingDirectoryRefresh = true
                        } else {
                            getDirectories()
                        }
                    }
                }, 350L)
            }
        }
        contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, mModernMediaObserver!!)
    }

    private fun unregisterModernMediaObserver() {
        mModernMediaHandler.removeCallbacksAndMessages(null)
        mModernMediaObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (ignored: Exception) {
            }
        }
        mModernMediaObserver = null
    }

'''
ms = replace_once(ms, '    private fun startNewPhotoFetcher() {', observer_methods + '    private fun startNewPhotoFetcher() {', 'Main observer methods')

new_get_dirs = r'''    private fun getDirectories() {
        if (mIsGettingDirs) {
            mPendingDirectoryRefresh = true
            return
        }

        if (mDirs.isEmpty()) {
            loading.show()
        }

        mIsGettingDirs = true
        mPendingDirectoryRefresh = false
        val getImagesOnly = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideosOnly = mIsPickVideoIntent || mIsGetVideoContentIntent

        Thread {
            try {
                val mediaFetcher = MediaFetcher(applicationContext)
                val favoritePaths = getFavoritePaths()
                val getProperDateTaken = config.directorySorting and SORT_BY_DATE_TAKEN != 0
                val getProperFileSize = config.directorySorting and SORT_BY_SIZE != 0
                val allMedia = mediaFetcher.getAllMediaFast(
                        getImagesOnly,
                        getVideosOnly,
                        getProperDateTaken,
                        getProperFileSize,
                        favoritePaths,
                        false
                )

                val grouped = LinkedHashMap<String, ArrayList<Medium>>()
                allMedia.forEach { medium ->
                    if (File(medium.path).isFile) {
                        grouped.getOrPut(medium.parentPath) { ArrayList() }.add(medium)
                    }
                }

                val albumCovers = config.parseAlbumCovers()
                val hiddenString = getString(R.string.hidden)
                val includedFolders = config.includedFolders
                val isSortingAscending = config.directorySorting and SORT_DESCENDING == 0
                val dirs = ArrayList<Directory>()

                grouped.forEach { (path, media) ->
                    if (media.isNotEmpty()) {
                        mediaFetcher.sortMedia(media, config.getFileSorting(path))
                        dirs.add(createDirectoryFromMedia(path, media, albumCovers, hiddenString, includedFolders, isSortingAscending, getProperFileSize))
                    }
                }

                // Favoriler de ayni canli listeden uretilir; stale Room kaydi ekrana cikmaz.
                val favoriteSet = HashSet<String>(favoritePaths)
                val favoriteMedia = ArrayList<Medium>()
                allMedia.filterTo(favoriteMedia) { favoriteSet.contains(it.path) && File(it.path).isFile }
                if (favoriteMedia.isNotEmpty()) {
                    dirs.add(createDirectoryFromMedia(FAVORITES, favoriteMedia, albumCovers, hiddenString, includedFolders, isSortingAscending, getProperFileSize))
                }

                // Cop kutusu sadece gercekten diskte duran kayitlardan olusur.
                if (config.useRecycleBin && config.showRecycleBinAtFolders) {
                    val deletedMedia = ArrayList<Medium>()
                    mMediumDao.getDeletedMedia().filterTo(deletedMedia) { File(it.path).isFile }
                    if (deletedMedia.isNotEmpty()) {
                        dirs.add(createDirectoryFromMedia(RECYCLE_BIN, deletedMedia, albumCovers, hiddenString, includedFolders, isSortingAscending, getProperFileSize))
                    }
                }

                val sorted = getSortedDirectories(dirs)
                runOnUiThread {
                    mDirs = sorted.clone() as ArrayList<Directory>
                    setupAdapter(mDirs)
                    checkPlaceholderVisibility(mDirs)
                    loading.hide()
                    directories_refresh_layout.isRefreshing = false
                    mIsGettingDirs = false
                    mLoadedInitialPhotos = true

                    if (mPendingDirectoryRefresh) {
                        mPendingDirectoryRefresh = false
                        getDirectories()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    mIsGettingDirs = false
                    directories_refresh_layout.isRefreshing = false
                    loading.hide()
                    if (mPendingDirectoryRefresh) {
                        mPendingDirectoryRefresh = false
                        getDirectories()
                    }
                }
            }
        }.start()
    }

'''
ms, count = re.subn(r'    private fun getDirectories\(\) \{.*?\n    private fun showSortingDialog\(\) \{', new_get_dirs + '    private fun showSortingDialog() {', ms, count=1, flags=re.S)
if count != 1:
    raise RuntimeError('Main getDirectories replacement failed')

# 3 saniyede bir sonsuz polling yok. ContentObserver degisiklik geldigi anda tetikler.
ms, count = re.subn(
    r'    private fun checkLastMediaChanged\(\) \{.*?\n    private fun showSortingDialog\(\) \{',
    '    private fun checkLastMediaChanged() {\n        // V5: MediaStore ContentObserver kullaniyor; periyodik polling kapali.\n    }\n\n    private fun showSortingDialog() {',
    ms,
    count=1,
    flags=re.S
)
# Bu method getDirectories'dan sonra ikinci kez yer aliyorsa replace olur; yoksa sorun degil.
main.write_text(ms)


# -----------------------------------------------------------------------------
# 2) Medya grid ekrani: 3 saniyelik polling yerine debounced ContentObserver.
#    Degisiklikte devam eden eski tarama iptal edilir, sadece son durum kazanir.
# -----------------------------------------------------------------------------
media = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MediaActivity.kt')
ma = media.read_text()
if 'import android.database.ContentObserver' not in ma:
    ma = ma.replace('import android.content.Intent\n', 'import android.content.Intent\nimport android.database.ContentObserver\n')
if 'import android.provider.MediaStore' not in ma:
    ma = ma.replace('import android.os.Handler\n', 'import android.os.Handler\nimport android.provider.MediaStore\n')

ma = replace_once(
    ma,
    '    private var mLastMediaHandler = Handler()\n',
    '    private var mLastMediaHandler = Handler()\n'
    '    private var mModernMediaObserver: ContentObserver? = null\n'
    '    private var mModernMediaHandler = Handler()\n',
    'Media observer fields'
)

ma = replace_once(
    ma,
    '        updateWidgets()\n',
    '        registerModernMediaObserver()\n        updateWidgets()\n',
    'Media observer register'
)

ma = replace_once(
    ma,
    '        mTempShowHiddenHandler.removeCallbacksAndMessages(null)\n        com.eagle.gallery.pro.activities.MediaActivity.Companion.mMedia.clear()\n',
    '        mTempShowHiddenHandler.removeCallbacksAndMessages(null)\n        unregisterModernMediaObserver()\n        com.eagle.gallery.pro.activities.MediaActivity.Companion.mMedia.clear()\n',
    'Media observer unregister'
)

media_observer_methods = r'''
    private fun registerModernMediaObserver() {
        if (mModernMediaObserver != null) return
        mModernMediaObserver = object : ContentObserver(mModernMediaHandler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                mModernMediaHandler.removeCallbacksAndMessages(null)
                mModernMediaHandler.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        mCurrAsyncTask?.stopFetching()
                        mIsGettingMedia = false
                        getMedia()
                    }
                }, 350L)
            }
        }
        contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, mModernMediaObserver!!)
    }

    private fun unregisterModernMediaObserver() {
        mModernMediaHandler.removeCallbacksAndMessages(null)
        mModernMediaObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (ignored: Exception) {
            }
        }
        mModernMediaObserver = null
    }

'''
ma = replace_once(ma, '    private fun storeStateVariables() {', media_observer_methods + '    private fun storeStateVariables() {', 'Media observer methods')

ma, count = re.subn(
    r'    private fun checkLastMediaChanged\(\) \{.*?\n    private fun showSortingDialog\(\) \{',
    '    private fun checkLastMediaChanged() {\n        // V5: sonsuz 3 saniyelik kontrol yerine MediaStore ContentObserver.\n    }\n\n    private fun showSortingDialog() {',
    ma,
    count=1,
    flags=re.S
)
if count != 1:
    raise RuntimeError('Media checkLastMediaChanged replacement failed')
media.write_text(ma)

print('V5 modern MediaStore motoru + ContentObserver + Atmaca ikonu uygulandi')
