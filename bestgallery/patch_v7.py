from pathlib import Path
import runpy

# V6 tabanini kur, sonra MediaStore.Files icinde tum telefonu tarama problemini kaldir.
runpy.run_path('../bestgallery/patch_v6.py', run_name='__main__')

# Surum
gradle = Path('app/build.gradle')
gs = gradle.read_text()
gs = gs.replace('versionCode 20010011', 'versionCode 20010012')
gs = gs.replace('versionName "6.0-atmaca-mediastore"', 'versionName "7.0-atmaca-fast-media"')
gradle.write_text(gs)

# Sadece MEDIA_TYPE_IMAGE ve MEDIA_TYPE_VIDEO kayitlarini sorgula.
# Boylece MediaStore.Files icindeki APK/ZIP/TXT/uygulama dosyalari tek tek gezilmez.
fetcher = Path('app/src/main/kotlin/com/eagle/gallery/pro/helpers/MediaFetcher.kt')
fs = fetcher.read_text()

old_all_query = '''            context.contentResolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use { cursor ->'''
new_all_query = '''            context.contentResolver.query(
                    uri,
                    projection,
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
                    arrayOf(
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                    ),
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->'''
if old_all_query not in fs:
    raise RuntimeError('V7 ana MediaStore sorgusu bulunamadi')
fs = fs.replace(old_all_query, new_all_query, 1)

old_folder = '''        val selection = "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
        val selectionArgs = arrayOf("$folder/%", "$folder/%/%")'''
new_folder = '''        val selection = "(${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?) AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
                "$folder/%",
                "$folder/%/%",
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )'''
if old_folder not in fs:
    raise RuntimeError('V7 klasor MediaStore sorgusu bulunamadi')
fs = fs.replace(old_folder, new_folder, 1)
fetcher.write_text(fs)

# V5'te eklenen ContentObserver, bazı OEM'lerde ilk medya indekslemesi sürerken
# art arda yeniden tarama baslatabiliyor. Ilk kararlı sürümde observer kapali;
# mevcut file-update listener + elle yenileme/lifecycle akisi kullanilsin.
main = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MainActivity.kt')
ms = main.read_text()
ms = ms.replace('        registerModernMediaObserver()\n', '')
main.write_text(ms)

media = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MediaActivity.kt')
ma = media.read_text()
ma = ma.replace('        registerModernMediaObserver()\n', '')
media.write_text(ma)

print('V7 hizli medya sorgusu uygulandi: sadece foto/video, observer ilk yuklemede kapali')
