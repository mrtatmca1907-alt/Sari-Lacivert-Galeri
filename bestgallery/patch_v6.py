from pathlib import Path
import runpy

# V5 tabanini kur. V6'da Android 13'te MediaStore'dan gelen kayitlari
# tekrar java.io.File ile dogrulamayacagiz; bu kontrol bazi cihazlarda tum listeyi bosaltiyor.
runpy.run_path('../bestgallery/patch_v5.py', run_name='__main__')


def replace_once(text, old, new, label):
    if old not in text:
        raise RuntimeError(f'V6 patch hedefi bulunamadi: {label}')
    return text.replace(old, new, 1)


# Surum
gradle = Path('app/build.gradle')
gs = gradle.read_text()
gs = gs.replace('versionCode 20010010', 'versionCode 20010011')
gs = gs.replace('versionName "5.0-atmaca-modern"', 'versionName "6.0-atmaca-mediastore"')
gradle.write_text(gs)


# -----------------------------------------------------------------------------
# MediaFetcher: MediaStore sonucu geldikten sonra File.isFile/length/lastModified
# ile tekrar diske dokunma. Yol, ad ve klasor bilgisi MediaStore DATA'dan uretilir.
# -----------------------------------------------------------------------------
fetcher = Path('app/src/main/kotlin/com/eagle/gallery/pro/helpers/MediaFetcher.kt')
fs = fetcher.read_text()

all_file_block = '''                    val file = File(path)
                    if (!file.isFile) continue
                    val size = file.length()
                    if (size <= 0L) continue

                    val filename = file.name
                    val parentPath = file.parent ?: continue'''
all_file_new = '''                    val filename = path.substringAfterLast('/')
                    val parentPath = path.substringBeforeLast('/', "")
                    if (filename.isEmpty() || parentPath.isEmpty()) continue
                    val size = 1L'''
fs = replace_once(fs, all_file_block, all_file_new, 'getAllMediaFast File kontrolu')

folder_file_block = '''                    val file = File(path)
                    if (!file.isFile) continue
                    val size = file.length()
                    if (size <= 0L) continue

                    val filename = file.name'''
folder_file_new = '''                    val filename = path.substringAfterLast('/')
                    if (filename.isEmpty()) continue
                    val size = 1L'''
fs = replace_once(fs, folder_file_block, folder_file_new, 'getFolderMediaFast File kontrolu')

modified_block = '''                    val modified = file.lastModified()
                    val mediaDate = if (getProperDateTaken && takenIndex >= 0) cursor.getLong(takenIndex) else 0L
                    val taken = if (mediaDate > 0L) mediaDate else modified'''
modified_new = '''                    val mediaDate = if (takenIndex >= 0) cursor.getLong(takenIndex) else 0L
                    val modified = mediaDate
                    val taken = if (getProperDateTaken && mediaDate > 0L) mediaDate else modified'''
if fs.count(modified_block) < 2:
    raise RuntimeError('V6 patch hedefi bulunamadi: MediaFetcher modified bloklari')
fs = fs.replace(modified_block, modified_new, 2)
fetcher.write_text(fs)


# -----------------------------------------------------------------------------
# Ana album listesi: MediaStore'un verdigi kaydi File.isFile ile tekrar eleme.
# -----------------------------------------------------------------------------
main = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MainActivity.kt')
ms = main.read_text()
ms = replace_once(
    ms,
    '''                allMedia.forEach { medium ->
                    if (File(medium.path).isFile) {
                        grouped.getOrPut(medium.parentPath) { ArrayList() }.add(medium)
                    }
                }''',
    '''                allMedia.forEach { medium ->
                    grouped.getOrPut(medium.parentPath) { ArrayList() }.add(medium)
                }''',
    'MainActivity grup File kontrolu'
)
ms = replace_once(
    ms,
    '''                allMedia.filterTo(favoriteMedia) { favoriteSet.contains(it.path) && File(it.path).isFile }''',
    '''                allMedia.filterTo(favoriteMedia) { favoriteSet.contains(it.path) }''',
    'MainActivity favori File kontrolu'
)
main.write_text(ms)


# -----------------------------------------------------------------------------
# Grid ekrani: son savunma adiminda tum sonuclari File.isFile ile silme.
# -----------------------------------------------------------------------------
media = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MediaActivity.kt')
ma = media.read_text()
ma = replace_once(
    ma,
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
        }''',
    'MediaActivity File kontrolu'
)
media.write_text(ma)


# -----------------------------------------------------------------------------
# Tam ekran: silme/tasima sonrasi sadece islem yapilan path listeden ciksin.
# Diger fotograflari File.isFile false diye topluca temizleme.
# -----------------------------------------------------------------------------
vp = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/ViewPagerActivity.kt')
vs = vp.read_text()
vs = replace_once(
    vs,
    '''        mMediaFiles.removeAll { it.path == path || !File(it.path).isFile }
        com.eagle.gallery.pro.activities.MediaActivity.Companion.mMedia.removeAll {
            val medium = it as? Medium
            medium != null && (medium.path == path || !File(medium.path).isFile)
        }''',
    '''        mMediaFiles.removeAll { it.path == path }
        com.eagle.gallery.pro.activities.MediaActivity.Companion.mMedia.removeAll {
            val medium = it as? Medium
            medium != null && medium.path == path
        }''',
    'ViewPager toplu File kontrolu'
)
vp.write_text(vs)

print('V6 Android 13 MediaStore listeleme duzeltmesi uygulandi')
