from pathlib import Path
import re


def replace_once(text, old, new, label):
    if old not in text:
        raise RuntimeError(f"Patch hedefi bulunamadi: {label}")
    return text.replace(old, new, 1)


# --- Marka / reklamsiz taban ---
p = Path('app/build.gradle')
s = p.read_text()
s = s.replace('applicationId "com.eagle.gallery.photos.videos.album.hd.gallery.editor"', 'applicationId "com.sarilacivert.bestgallery"')
for dep in [
    "implementation 'com.tencent.bugly:crashreport:2.3.1'",
    "implementation 'com.google.firebase:firebase-core:16.0.0'",
    "implementation 'com.google.firebase:firebase-config:16.0.0'",
    'implementation "com.google.android.gms:play-services-ads:15.0.1"',
]:
    s = s.replace(dep, '')
s = s.replace("implementation 'com.google.vr:sdk-panowidget:1.180.0'", '')
s = s.replace("implementation 'com.google.vr:sdk-videowidget:1.180.0'", '')
if "implementation fileTree(dir: 'libs', include: ['*.aar'])" not in s:
    s = s.replace('dependencies {', "dependencies {\n    implementation fileTree(dir: 'libs', include: ['*.aar'])\n    implementation 'com.google.protobuf.nano:protobuf-javanano:3.1.0'")
p.write_text(s)

strings = Path('app/src/main/res/values/strings.xml')
t = strings.read_text()
t = re.sub(r'<string name="app_name">.*?</string>', '<string name="app_name">Sarı Lacivert Galeri</string>', t, count=1)
t = re.sub(r'<string name="app_launcher_name">.*?</string>', '<string name="app_launcher_name">Sarı Lacivert Galeri</string>', t, count=1)
strings.write_text(t)

colors = Path('commons/src/main/res/values/colors.xml')
c = colors.read_text()
for old, new in {
    '<color name="color_primary">#ff5c1166</color>': '<color name="color_primary">#0B1D3A</color>',
    '<color name="color_primary_dark">#ff5c1166</color>': '<color name="color_primary_dark">#061326</color>',
    '<color name="color_accent">@color/color_primary</color>': '<color name="color_accent">#F4C430</color>',
    '<color name="default_text_color">@color/theme_dark_text_color</color>': '<color name="default_text_color">#FFF7D6</color>',
    '<color name="default_background_color">@color/theme_dark_background_color</color>': '<color name="default_background_color">#081426</color>',
    '<color name="default_background_color2">#202340</color>': '<color name="default_background_color2">#0D1B2E</color>',
}.items():
    c = c.replace(old, new)
colors.write_text(c)

app_colors = Path('app/src/main/res/values/colors.xml')
ac = app_colors.read_text().replace('<color name="actionbar_menu_icon">#454545</color>', '<color name="actionbar_menu_icon">#F4C430</color>')
app_colors.write_text(ac)

app = Path('app/src/main/kotlin/com/eagle/gallery/pro/App.kt')
a = app.read_text()
a = a.replace('import com.google.firebase.FirebaseApp\n', '')
a = re.sub(r'\n\s*Thread \{\s*FirebaseApp\.initializeApp\(this\)\s*\}\.start\(\)\s*\n', '\n', a, flags=re.S)
if 'import com.eagle.commons.helpers.BaseConfig' not in a:
    a = a.replace('import com.eagle.commons.extensions.checkUseEnglish\n', 'import com.eagle.commons.extensions.checkUseEnglish\nimport com.eagle.commons.helpers.BaseConfig\n')
branding = '''
        val brandingPrefs = getSharedPreferences("sari_lacivert_branding", Context.MODE_PRIVATE)
        if (!brandingPrefs.getBoolean("theme_v3", false)) {
            val config = BaseConfig.newInstance(this)
            val primary = resources.getColor(R.color.color_primary)
            val background = resources.getColor(R.color.default_background_color2)
            val text = resources.getColor(R.color.default_text_color)
            config.primaryColor = primary
            config.backgroundColor = background
            config.textColor = text
            config.customPrimaryColor = primary
            config.customBackgroundColor = background
            config.customTextColor = text
            config.appIconColor = primary
            // Kararlılık v3: dosya varlığını hızdan önce tut.
            getSharedPreferences("Prefs", Context.MODE_PRIVATE).edit()
                    .putInt("file_loading_priority", 2)
                    .apply()
            brandingPrefs.edit().putBoolean("theme_v3", true).apply()
        }
'''
a = replace_once(a, '        mContext = applicationContext\n', '        mContext = applicationContext\n' + branding, 'App branding')
app.write_text(a)

icon = Path('app/src/main/res/drawable/ic_sari_lacivert_gallery.xml')
icon.write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#081426" android:pathData="M0,0H108V108H0Z"/>
    <path android:fillColor="#F4C430" android:pathData="M14,18H94V90H14Z"/>
    <path android:fillColor="#081426" android:pathData="M21,25H87V83H21Z"/>
    <path android:fillColor="#F4C430" android:pathData="M68,37a8,8 0,1 0,16,0a8,8 0,1 0,-16,0"/>
    <path android:fillColor="#F4C430" android:pathData="M24,77L43,55L56,68L66,58L84,77Z"/>
</vector>
''')

manifest = Path('app/src/main/AndroidManifest.xml')
m = manifest.read_text().replace('@mipmap/ic_launcher', '@drawable/ic_sari_lacivert_gallery')
m = re.sub(r'\s*<meta-data\s+android:name="com\.google\.android\.gms\.version"\s+android:value="@integer/google_play_services_version"\s*/>', '', m, flags=re.S)
m = re.sub(r'\s*<activity\s+android:name="com\.google\.android\.gms\.ads\.AdActivity".*?/>', '', m, flags=re.S)
manifest.write_text(m)

root = Path('build.gradle')
r = root.read_text()
marker = 'maven { url "https://jitpack.io" }'
if 'artifactory.appodeal.com/appodeal-public' not in r:
    r = r.replace(marker, marker + '\n        maven { url "https://artifactory.appodeal.com/appodeal-public" }\n        maven { url "https://maven.aliyun.com/repository/jcenter" }')
root.write_text(r)

# --- 1) Dosya doğrulama varsayılanı ve hayalet dosya engeli ---
config = Path('app/src/main/kotlin/com/eagle/gallery/pro/helpers/Config.kt')
cs = config.read_text()
cs = cs.replace('prefs.getInt(FILE_LOADING_PRIORITY, PRIORITY_COMPROMISE)', 'prefs.getInt(FILE_LOADING_PRIORITY, PRIORITY_VALIDITY)')
config.write_text(cs)

fetcher = Path('app/src/main/kotlin/com/eagle/gallery/pro/helpers/MediaFetcher.kt')
fs = fetcher.read_text()
fs = fs.replace('val checkFileExistence = config.fileLoadingPriority == PRIORITY_VALIDITY', 'val checkFileExistence = true')

# --- 2) Tum dosyalar: klasor klasor tarama yerine tek MediaStore sorgusu ---
fast_method = r'''
    fun getAllMediaFast(
            isPickImage: Boolean,
            isPickVideo: Boolean,
            getProperDateTaken: Boolean,
            getProperFileSize: Boolean,
            getVideoDurations: Boolean): ArrayList<Medium> {
        val result = ArrayList<Medium>()
        val seenPaths = HashSet<String>()
        val favoriteSet = HashSet<String>(favoritePaths)
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_TAKEN)
        val selection = getSelectionQuery(filterMedia).removeSuffix(" AND ")
        val args = getSelectionArgsQuery(filterMedia)

        try {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
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
                    val parentPath = file.parent ?: continue
                    if (!parentPath.shouldFolderBeVisible(config.excludedFolders, config.includedFolders, config.shouldShowHidden)) continue
                    if (!config.shouldShowHidden && filename.startsWith('.')) continue

                    val isVideo = filename.isVideoFast()
                    val isImage = filename.isImageFast()
                    val isGif = filename.isGif()
                    val isRaw = filename.isRawFast()
                    val isSvg = filename.isSvg()
                    if (isPickImage && isVideo) continue
                    if (isPickVideo && isImage) continue

                    var type = 0
                    var videoDuration = 0L
                    when {
                        isVideo -> {
                            if (filterMedia and TYPE_VIDEOS == 0) continue
                            type = TYPE_VIDEOS
                            if (getVideoDurations) videoDuration = path.getVideoDuration()
                        }
                        isGif -> {
                            if (filterMedia and TYPE_GIFS == 0) continue
                            type = TYPE_GIFS
                        }
                        isRaw -> {
                            if (filterMedia and TYPE_RAWS == 0) continue
                            type = TYPE_RAWS
                        }
                        isSvg -> {
                            if (filterMedia and TYPE_SVGS == 0) continue
                            type = TYPE_SVGS
                        }
                        isImage -> {
                            if (filterMedia and TYPE_IMAGES == 0) continue
                            type = TYPE_IMAGES
                        }
                        else -> continue
                    }

                    val modified = file.lastModified()
                    val mediaDate = if (getProperDateTaken && takenIndex >= 0) cursor.getLong(takenIndex) else 0L
                    val taken = if (mediaDate > 0L) mediaDate else modified
                    result.add(Medium(null, filename, path, parentPath, modified, taken, size, type, videoDuration, favoriteSet.contains(path), 0L))
                }
            }
        } catch (e: Exception) {
            result.clear()
        }

        return sortMedia(result, config.getFileSorting(SHOW_ALL))
    }

'''
needle = '    private fun getSelectionQuery(filterMedia: Int): String {'
if 'fun getAllMediaFast(' not in fs:
    fs = replace_once(fs, needle, fast_method + needle, 'MediaFetcher fast all')
fetcher.write_text(fs)

# --- 3) Async tarama: eski iptal edilmiş iş sonucu yeni listeyi ezmesin ---
task = Path('app/src/main/kotlin/com/eagle/gallery/pro/asynctasks/GetMediaAsynctask.kt')
ts = task.read_text()
old_show_all = '''        if (showAll) {
            mediaFetcher.getFoldersToScan().forEach {
                if (mediaFetcher.shouldStop) {
                    return@forEach
                }

                val files = mediaFetcher.getFilesFrom(it, isPickImage, isPickVideo, getProperDateTaken, getProperFileSize, getVideoDurations)
                media.addAll(files)
            }
            media = mediaFetcher.sortMedia(media, config.getFileSorting(SHOW_ALL))
        } else {'''
new_show_all = '''        if (showAll) {
            media = mediaFetcher.getAllMediaFast(isPickImage, isPickVideo, getProperDateTaken, getProperFileSize, getVideoDurations)
        } else {'''
ts = replace_once(ts, old_show_all, new_show_all, 'GetMedia show all')
old_post = '''    override fun onPostExecute(media: ArrayList<ThumbnailItem>) {
        super.onPostExecute(media)
        callback(media)
    }'''
new_post = '''    override fun onPostExecute(media: ArrayList<ThumbnailItem>) {
        super.onPostExecute(media)
        if (!isCancelled && !mediaFetcher.shouldStop) {
            callback(media)
        }
    }'''
ts = replace_once(ts, old_post, new_post, 'GetMedia cancelled callback')
task.write_text(ts)

# --- 4) MediaActivity: stale Room cache'i ekrana basma, refresh daima latest-wins ---
activity = Path('app/src/main/kotlin/com/eagle/gallery/pro/activities/MediaActivity.kt')
ma = activity.read_text()
get_media_re = re.compile(r'    private fun getMedia\(\) \{.*?\n    \}\n\n    private fun startAsyncTask\(\)', re.S)
new_get_media = '''    private fun getMedia() {
        mCurrAsyncTask?.stopFetching()
        mIsGettingMedia = true
        if (MediaActivity.mMedia.size > 0) {
            loadingMedia?.hide()
        } else {
            loadingMedia?.show()
        }
        startAsyncTask()
        mLoadedInitialPhotos = true
    }

    private fun startAsyncTask()'''
ma, n = get_media_re.subn(new_get_media, ma, count=1)
if n != 1:
    raise RuntimeError('Patch hedefi bulunamadi: MediaActivity getMedia')

old_refresh = '''    override fun refreshItems() {
        getMedia()
    }'''
new_refresh = '''    override fun refreshItems() {
        mCurrAsyncTask?.stopFetching()
        mIsGettingMedia = false
        getMedia()
        // Android MediaStore tasima/silme sonrasinda kisa sure geriden gelebiliyor.
        // Tek ek kontrol, kaynak ve hedef listesini kararlı hale getirir.
        Handler().postDelayed({
            if (!isFinishing && !isDestroyed) {
                mCurrAsyncTask?.stopFetching()
                mIsGettingMedia = false
                getMedia()
            }
        }, 650L)
    }'''
ma = replace_once(ma, old_refresh, new_refresh, 'MediaActivity refreshItems')

# gotMedia: fiziksel olarak yok olan/0 byte/tekrar yolları ekrana hic verme.
got_marker = '''    private fun gotMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean) {
        mIsGettingMedia = false
        checkLastMediaChanged()
        com.eagle.gallery.pro.activities.MediaActivity.Companion.mMedia = media'''
got_replacement = '''    private fun gotMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean) {
        mIsGettingMedia = false
        checkLastMediaChanged()

        val validMedia = ArrayList<Medium>()
        val seenPaths = HashSet<String>()
        media.mapNotNull { it as? Medium }.forEach {
            val f = File(it.path)
            if (f.isFile && f.length() > 0L && seenPaths.add(it.path)) {
                validMedia.add(it)
            }
        }
        val cleaned = MediaFetcher(applicationContext).groupMedia(validMedia, if (mShowAll) SHOW_ALL else mPath)
        media.clear()
        media.addAll(cleaned)
        com.eagle.gallery.pro.activities.MediaActivity.Companion.mMedia = media'''
ma = replace_once(ma, got_marker, got_replacement, 'MediaActivity gotMedia validate')

# Silme baslarken halen calisan taramayi iptal et; eski callback silineni geri koyamasin.
try_delete_marker = '''    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>) {
        val filtered = fileDirItems.filter {'''
try_delete_replacement = '''    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>) {
        mCurrAsyncTask?.stopFetching()
        mIsGettingMedia = false
        val filtered = fileDirItems.filter {'''
ma = replace_once(ma, try_delete_marker, try_delete_replacement, 'MediaActivity delete cancel')
activity.write_text(ma)

# --- 5) Adapter: 100 ms gecikmeli eski state'in yenisini ezmesini kaldir ---
adapter = Path('app/src/main/kotlin/com/eagle/gallery/pro/adapters/MediaAdapter.kt')
ads = adapter.read_text()
update_re = re.compile(r'    fun updateMedia\(newMedia: ArrayList<ThumbnailItem>\) \{.*?\n    \}\n', re.S)
new_update = '''    fun updateMedia(newMedia: ArrayList<ThumbnailItem>) {
        val thumbnailItems = newMedia.clone() as ArrayList<ThumbnailItem>
        val newHash = thumbnailItems.hashCode()
        if (newHash != currentMediaHash) {
            currentMediaHash = newHash
            media = thumbnailItems
            enableInstantLoad()
            notifyDataSetChanged()
            finishActMode()
        }
    }
'''
ads, n = update_re.subn(new_update, ads, count=1)
if n != 1:
    raise RuntimeError('Patch hedefi bulunamadi: MediaAdapter updateMedia')
adapter.write_text(ads)

print('BestGallery V3 stability patches applied')
