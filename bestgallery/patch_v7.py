from pathlib import Path
import runpy

# V6 tabanini kur, sonra pahali MIME/uzanti OR sorgularini basit MEDIA_TYPE filtresine indir.
runpy.run_path('../bestgallery/patch_v6.py', run_name='__main__')

# Surum
gradle = Path('app/build.gradle')
gs = gradle.read_text()
gs = gs.replace('versionCode 20010011', 'versionCode 20010012')
gs = gs.replace('versionName "6.0-atmaca-mediastore"', 'versionName "7.0-atmaca-fast-media"')
gradle.write_text(gs)

fetcher = Path('app/src/main/kotlin/com/eagle/gallery/pro/helpers/MediaFetcher.kt')
fs = fetcher.read_text()

# Ana galeri: karmaşık getSelectionQuery yerine MediaStore indeksindeki medya tipini kullan.
old_all = '''        val selection = getSelectionQuery(filterMedia).removeSuffix(" AND ")
        val selectionArgs = getSelectionArgsQuery(filterMedia).toTypedArray()'''
new_all = '''        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )'''
if old_all not in fs:
    raise RuntimeError('V7 ana MediaStore selection bulunamadi')
fs = fs.replace(old_all, new_all, 1)

# Klasor ici: yine sadece foto/video + sadece secili klasor.
old_folder = '''        val selection = getSelectionQuery(filterMedia) + "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
        val selectionArgs = getSelectionArgsQuery(filterMedia).apply {
            add("$folder/%")
            add("$folder/%/%")
        }.toTypedArray()'''
new_folder = '''        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?) AND ${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
        val selectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                "$folder/%",
                "$folder/%/%"
        )'''
if old_folder not in fs:
    raise RuntimeError('V7 klasor MediaStore selection bulunamadi')
fs = fs.replace(old_folder, new_folder, 1)
fetcher.write_text(fs)

# Bazi OEM'lerde ContentObserver ilk indeksleme sirasinda art arda yeniden tarama baslatabiliyor.
# İlk kararlı sürümde observer kayıtlarını kapat; mevcut yenileme akışı kalsın.
main = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MainActivity.kt')
ms = main.read_text().replace('        registerModernMediaObserver()\n', '')
main.write_text(ms)

media = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MediaActivity.kt')
ma = media.read_text().replace('        registerModernMediaObserver()\n', '')
media.write_text(ma)

print('V7 hizli medya sorgusu uygulandi: MEDIA_TYPE image/video + observer kapali')
