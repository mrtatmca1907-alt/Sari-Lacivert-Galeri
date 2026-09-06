from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
repo = ROOT / "app/src/main/kotlin/com/sarilacivert/galeri/data/MediaRepository.kt"
ui = ROOT / "app/src/main/kotlin/com/sarilacivert/galeri/ui/GalleryApp.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Patch bulunamadi: {label}")
    return text.replace(old, new, 1)


text = repo.read_text(encoding="utf-8")
text = replace_once(
    text,
    "import android.database.Cursor\nimport android.net.Uri\nimport android.os.Build\nimport android.os.Bundle\nimport android.os.SystemClock\n",
    "import android.database.ContentObserver\nimport android.database.Cursor\nimport android.net.Uri\nimport android.os.Build\nimport android.os.Bundle\nimport android.os.Handler\nimport android.os.Looper\n",
    "MediaRepository imports",
)
text = replace_once(
    text,
    """    @Volatile
    private var cacheBuiltAtMs: Long = 0L

    private val cacheTtlMs = 2_000L

    fun invalidateCache() {
        mediaCache = null
        cacheBuiltAtMs = 0L
    }
""",
    """    @Volatile
    private var cacheDirty: Boolean = true

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            cacheDirty = true
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            cacheDirty = true
        }
    }

    init {
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
    }

    fun invalidateCache() {
        mediaCache = null
        cacheDirty = true
    }
""",
    "MediaRepository cache fields",
)
text = replace_once(
    text,
    """            val now = SystemClock.elapsedRealtime()
            val cached = mediaCache
            if (cached != null && now - cacheBuiltAtMs <= cacheTtlMs) {
                cached
            } else {
                cacheMutex.withLock {
                    val again = mediaCache
                    val nowInside = SystemClock.elapsedRealtime()
                    if (again != null && nowInside - cacheBuiltAtMs <= cacheTtlMs) {
                        again
                    } else {
                        buildMediaList(false, false).also {
                            mediaCache = it
                            cacheBuiltAtMs = SystemClock.elapsedRealtime()
                        }
                    }
                }
            }
""",
    """            val cached = mediaCache
            if (MediaCachePolicy.shouldReuse(cached != null, cacheDirty)) {
                requireNotNull(cached)
            } else {
                cacheMutex.withLock {
                    val again = mediaCache
                    if (MediaCachePolicy.shouldReuse(again != null, cacheDirty)) {
                        requireNotNull(again)
                    } else {
                        buildMediaList(false, false).also {
                            mediaCache = it
                            cacheDirty = false
                        }
                    }
                }
            }
""",
    "MediaRepository cache load",
)
repo.write_text(text, encoding="utf-8")

text = ui.read_text(encoding="utf-8")
text = text.replace("import androidx.compose.material.icons.filled.Refresh\n", "")
text = replace_once(
    text,
    "            onRefresh = { refreshKey++ },",
    "            onRefresh = { repo.invalidateCache(); refreshKey++ },",
    "root refresh invalidation",
)
text = replace_once(
    text,
    "                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, \"Yenile\") }\n",
    "",
    "remove refresh button",
)
text = replace_once(
    text,
    """    ) { padding ->
        when {
            loading -> Box(Modifier.padding(padding)) { LoadingState(\"Albümler taranıyor…\") }
            albums.isEmpty() -> Box(Modifier.padding(padding)) { EmptyState(\"Medya bulunamadı\", \"Ayarlar > izinleri kontrol et veya yenile.\") }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 4.dp)
            ) {
                items(albums, key = { it.path }) { album ->
                    AlbumCard(album, loader) { onOpenAlbum(album) }
                }
            }
        }
    }
""",
    """    ) { padding ->
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                loading && albums.isEmpty() -> Box(Modifier.fillMaxSize()) { LoadingState(\"Galeri hazırlanıyor…\") }
                albums.isEmpty() -> Box(Modifier.fillMaxSize()) { EmptyState(\"Medya bulunamadı\", \"Ayarlar > izinleri kontrol et veya aşağı çekip yenile.\") }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                ) {
                    items(albums, key = { it.path }) { album ->
                        AlbumCard(album, loader) { onOpenAlbum(album) }
                    }
                }
            }
        }
    }
""",
    "albums pull to refresh",
)
text = replace_once(
    text,
    "            loading -> Box(Modifier.padding(padding)) { LoadingState() }\n            items.isEmpty() -> Box(Modifier.padding(padding)) { EmptyState(\"Bu albüm boş\") }",
    "            loading && items.isEmpty() -> Box(Modifier.padding(padding)) { LoadingState() }\n            items.isEmpty() -> Box(Modifier.padding(padding)) { EmptyState(\"Bu albüm boş\") }",
    "album preserve content while refreshing",
)
text = replace_once(
    text,
    "    var albumSortMenu by remember { mutableStateOf(false) }\n    Scaffold(",
    "    var albumSortMenu by remember { mutableStateOf(false) }\n    var toolsOpen by remember { mutableStateOf(false) }\n    Scaffold(",
    "tools dialog state",
)
text = replace_once(
    text,
    "            Text(\"Android 13 uyumlu • minSdk 26 • targetSdk 36 • compileSdk 37\", color = TextSecondary, style = MaterialTheme.typography.bodySmall)\n        }\n    }\n}\n\n@Composable\nprivate fun SettingRow",
    "            Button(onClick = { toolsOpen = true }) { Text(\"ATMACA Araçları\") }\n            Text(\"Android 13 uyumlu • minSdk 26 • targetSdk 36 • compileSdk 37\", color = TextSecondary, style = MaterialTheme.typography.bodySmall)\n        }\n    }\n    if (toolsOpen) {\n        com.sarilacivert.galeri.tools.AtmacaToolsDialog(onDismiss = { toolsOpen = false })\n    }\n}\n\n@Composable\nprivate fun SettingRow",
    "tools settings button",
)
ui.write_text(text, encoding="utf-8")

print("Galeri hiz/yukleme/cek-yenile ve ATMACA arac yamalari uygulandi.")
