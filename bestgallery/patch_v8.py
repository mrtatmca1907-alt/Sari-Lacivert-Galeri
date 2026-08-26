from pathlib import Path
import runpy

# V7 tabanini kur.
runpy.run_path('../bestgallery/patch_v7.py', run_name='__main__')

# Surum
gradle = Path('app/build.gradle')
gs = gradle.read_text()
gs = gs.replace('versionCode 20010012', 'versionCode 20010013')
gs = gs.replace('versionName "7.0-atmaca-fast-media"', 'versionName "8.0-atmaca-no-file-probe"')
gradle.write_text(gs)

# -----------------------------------------------------------------------------
# 1) MainActivity: albüm kapağı seçerken her medya için File.exists() YAPMA.
# Android 10+ scoped storage / OEM davranisinda MediaStore DATA kaydi gecerli olsa
# bile java.io.File.exists false/çok yavas olabilir. Binlerce dosyada sonsuz gibi
# gorunen spinnerin ana sebebi bu olabilir.
# -----------------------------------------------------------------------------
main = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MainActivity.kt')
ms = main.read_text()
old_thumb = '''        var thumbnail = curMedia.firstOrNull { File(it.path).exists() }?.path ?: ""
        albumCovers.forEach {
            if (it.path == path && File(it.tmb).exists()) {
                thumbnail = it.tmb
            }
        }'''
new_thumb = '''        var thumbnail = curMedia.firstOrNull()?.path ?: ""
        albumCovers.forEach {
            if (it.path == path && it.tmb.isNotEmpty()) {
                thumbnail = it.tmb
            }
        }'''
if old_thumb not in ms:
    raise RuntimeError('V8 createDirectoryFromMedia thumbnail hedefi bulunamadi')
ms = ms.replace(old_thumb, new_thumb, 1)

# İlk yükleme 12 saniyeyi aşarsa UI sonsuza kadar spinner göstermesin.
# Tarama bitince normal setupAdapter yine sonucu basar.
needle = '''        mIsGettingDirs = true
        mPendingDirectoryRefresh = false
        val getImagesOnly = mIsPickImageIntent || mIsGetImageContentIntent'''
replacement = '''        mIsGettingDirs = true
        mPendingDirectoryRefresh = false
        mModernMediaHandler.postDelayed({
            if (!isFinishing && !isDestroyed && mIsGettingDirs) {
                loading.hide()
                directories_refresh_layout.isRefreshing = false
            }
        }, 12000L)
        val getImagesOnly = mIsPickImageIntent || mIsGetImageContentIntent'''
if needle not in ms:
    raise RuntimeError('V8 loader watchdog hedefi bulunamadi')
ms = ms.replace(needle, replacement, 1)
main.write_text(ms)

# -----------------------------------------------------------------------------
# 2) MediaFetcher: MediaStore zaten indeksli; sonucu DATE_ADDED DESC iste ve
# tekrar bütün listeyi SHOW_ALL kuralıyla sort etme. Ana ekran daha sonra klasör
# bazlı küçük listeleri gerektiği kadar sıralıyor.
# -----------------------------------------------------------------------------
fetcher = Path('app/src/main/kotlin/com/eagle/gallery/pro/helpers/MediaFetcher.kt')
fs = fetcher.read_text()
old_query = '''            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->'''
new_query = '''            context.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { cursor ->'''
# İlk occurrence getAllMediaFast. Folder query kendi sortunu koruyabilir.
if old_query not in fs:
    raise RuntimeError('V8 getAllMediaFast query hedefi bulunamadi')
fs = fs.replace(old_query, new_query, 1)

old_sort = '''        sortMedia(result, config.getFileSorting(SHOW_ALL))
        return result'''
new_sort = '''        // MediaStore DATE_ADDED DESC ile zaten sirali; onbinlerce medya icin
        // ikinci bir global Kotlin sort yapma.
        return result'''
if old_sort not in fs:
    raise RuntimeError('V8 getAllMediaFast global sort hedefi bulunamadi')
fs = fs.replace(old_sort, new_sort, 1)
fetcher.write_text(fs)

print('V8: File.exists album probe kaldirildi + MediaStore sirasi + loader watchdog')
