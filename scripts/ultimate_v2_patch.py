from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_text(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"pattern not found in {path}: {old[:160]!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


def replace_section(path: str, start_marker: str, end_marker: str, replacement: str):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"start marker not found in {path}: {start_marker!r}")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"end marker not found in {path}: {end_marker!r}")
    text = text[:start] + replacement.rstrip() + "\n\n" + text[end:]
    p.write_text(text, encoding="utf-8")


# 1) App name: only "Galeri".
strings = ROOT / "app/src/main/res/values/strings.xml"
s = strings.read_text(encoding="utf-8")
s = s.replace("Sarı Lacivert Galeri", "Galeri").replace("ATMACA Galeri", "Galeri").replace("ATMACA-Galeri", "Galeri")
strings.write_text(s, encoding="utf-8")

# Gallery UI changes: trash consistency, remove duplicate UI, move trash under Settings.
gallery_path = "app/src/main/kotlin/com/sarilacivert/galeri/ui/GalleryApp.kt"
gallery = (ROOT / gallery_path).read_text(encoding="utf-8")
gallery = gallery.replace('Text("Sarı Lacivert Galeri", fontWeight = FontWeight.Bold)', 'Text("Galeri", fontWeight = FontWeight.Bold)')

# 3) Android 11+ bulk trash callback must invalidate the permanent fast cache.
gallery = gallery.replace(
    '''        if (result.resultCode == Activity.RESULT_OK) {\n            selectedUris = emptySet()\n            reloadToken++\n            Toast.makeText(context, "Seçilenler çöp kutusuna taşındı", Toast.LENGTH_SHORT).show()\n        }''',
    '''        if (result.resultCode == Activity.RESULT_OK) {\n            repo.invalidateCache()\n            selectedUris = emptySet()\n            reloadToken++\n            Toast.makeText(context, "Seçilenler çöp kutusuna taşındı", Toast.LENGTH_SHORT).show()\n        }'''
)

# Settings call gets a Trash entry. Duplicate sensitivity remains stored for backward compatibility
# but is no longer visible/reachable in the Gallery UI.
gallery = gallery.replace(
    '''            onAlbumSort = { scope.launch { prefs.setDefaultAlbumSort(it) } },\n            bottomScreen = Screen.Settings,''',
    '''            onAlbumSort = { scope.launch { prefs.setDefaultAlbumSort(it) } },\n            onOpenTrash = { screen = Screen.Trash },\n            bottomScreen = Screen.Settings,'''
)

# Trash screen now has a real Empty Trash action and returns to Settings.
trash_start = "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun TrashScreen("
trash_end = "@Composable\nprivate fun MediaGrid("
new_trash = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashScreen(
    repo: MediaRepository,
    loader: BitmapLoader,
    columns: Int,
    sort: MediaSort,
    refreshKey: Int,
    onOpen: (List<MediaItem>, Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var confirmEmpty by remember { mutableStateOf(false) }

    val deleteAllLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            repo.invalidateCache()
            reloadToken++
            Toast.makeText(context, "Çöp kutusu boşaltıldı", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(sort, refreshKey, reloadToken) {
        loading = true
        items = repo.loadTrash(sort)
        loading = false
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Çöp Kutusu") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Ayarlar") } },
                actions = {
                    if (items.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        TextButton(onClick = { confirmEmpty = true }) { Text("Boşalt") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy800)
            )
        },
        containerColor = Navy900
    ) { padding ->
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> Box(Modifier.padding(padding)) { EmptyState("Sistem çöp kutusu Android 11+ gerektirir") }
            loading -> Box(Modifier.padding(padding)) { LoadingState() }
            items.isEmpty() -> Box(Modifier.padding(padding)) { EmptyState("Çöp kutusu boş") }
            else -> MediaGrid(items, loader, emptySet(), columns, Modifier.fillMaxSize().padding(padding)) { onOpen(items, it) }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Çöp kutusunu boşalt?") },
            text = { Text("${items.size} öğe kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        repo.createDeleteRequest(items)?.let { sender ->
                            deleteAllLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                        }
                    }
                }) { Text("Kalıcı Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Vazgeç") } }
        )
    }
}'''
start = gallery.find(trash_start)
end = gallery.find(trash_end, start)
if start < 0 or end < 0:
    raise SystemExit("TrashScreen section not found")
gallery = gallery[:start] + new_trash + "\n\n" + gallery[end:]

# Update Trash route to new signature.
gallery = gallery.replace(
    '''            onOpen = { items, index -> screen = Screen.Viewer(items, index, Screen.Trash, fromTrash = true) },\n            bottomScreen = Screen.Trash,\n            onBottomNavigate = { screen = it }\n        )''',
    '''            onOpen = { items, index -> screen = Screen.Viewer(items, index, Screen.Trash, fromTrash = true) },\n            onBack = { screen = Screen.Settings }\n        )'''
)

# 7 + 8) Remove "Benzer" controls from Settings and put Trash there instead.
settings_start = "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun SettingsScreen("
settings_end = "@Composable\nprivate fun SettingRow("
new_settings = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    columns: Int,
    slideshowSeconds: Int,
    showImages: Boolean,
    showVideos: Boolean,
    duplicateDistance: Int,
    mediaSort: MediaSort,
    albumSort: AlbumSort,
    onColumns: (Int) -> Unit,
    onSlideshowSeconds: (Int) -> Unit,
    onShowImages: (Boolean) -> Unit,
    onShowVideos: (Boolean) -> Unit,
    onDuplicateDistance: (Int) -> Unit,
    onMediaSort: (MediaSort) -> Unit,
    onAlbumSort: (AlbumSort) -> Unit,
    onOpenTrash: () -> Unit,
    bottomScreen: Screen,
    onBottomNavigate: (Screen) -> Unit
) {
    var mediaSortMenu by remember { mutableStateOf(false) }
    var albumSortMenu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Ayarlar") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy800)) },
        bottomBar = { GalleryBottomBar(bottomScreen, onBottomNavigate) },
        containerColor = Navy900
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingRow("Fotoğrafları göster", "Albüm ve aramalarda fotoğrafları dahil et") { Switch(showImages, onCheckedChange = onShowImages) }
            SettingRow("Videoları göster", "Albüm ve aramalarda videoları dahil et") { Switch(showVideos, onCheckedChange = onShowVideos) }

            Button(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DeleteOutline, null)
                Text("  Çöp Kutusu")
            }

            Text("Izgara: $columns sütun", color = TextPrimary, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (3..5).forEach { c -> Button(onClick = { onColumns(c) }, enabled = c != columns) { Text("$c") } }
            }

            Text("Slayt geçişi: ${slideshowSeconds} sn", color = TextPrimary, fontWeight = FontWeight.Bold)
            Slider(value = slideshowSeconds.toFloat(), onValueChange = { onSlideshowSeconds(it.toInt()) }, valueRange = 2f..10f, steps = 7)

            Box {
                Button(onClick = { mediaSortMenu = true }) { Text("Medya sıralama: ${mediaSortLabel(mediaSort)}") }
                DropdownMenu(mediaSortMenu, onDismissRequest = { mediaSortMenu = false }) {
                    MediaSort.entries.forEach { s -> DropdownMenuItem(text = { Text(mediaSortLabel(s)) }, onClick = { mediaSortMenu = false; onMediaSort(s) }) }
                }
            }
            Box {
                Button(onClick = { albumSortMenu = true }) { Text("Albüm sıralama: ${albumSortLabel(albumSort)}") }
                DropdownMenu(albumSortMenu, onDismissRequest = { albumSortMenu = false }) {
                    AlbumSort.entries.forEach { s -> DropdownMenuItem(text = { Text(albumSortLabel(s)) }, onClick = { albumSortMenu = false; onAlbumSort(s) }) }
                }
            }

            Text("Android 13 uyumlu • minSdk 26 • targetSdk 36 • compileSdk 37", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}'''
start = gallery.find(settings_start)
end = gallery.find(settings_end, start)
if start < 0 or end < 0:
    raise SystemExit("SettingsScreen section not found")
gallery = gallery[:start] + new_settings + "\n\n" + gallery[end:]

# Bottom bar: Albums + Favorites + Settings only. Trash is inside Settings; Benzer is gone.
bottom_start = "@Composable\nprivate fun GalleryBottomBar("
bottom_end = "private fun requiredMediaPermissions()"
new_bottom = r'''@Composable
private fun GalleryBottomBar(current: Screen, onNavigate: (Screen) -> Unit) {
    NavigationBar(containerColor = Navy800) {
        NavigationBarItem(
            selected = current == Screen.Albums,
            onClick = { onNavigate(Screen.Albums) },
            icon = { Icon(Icons.Default.Folder, null) },
            label = { Text("Albümler") }
        )
        NavigationBarItem(
            selected = current == Screen.Favorites,
            onClick = { onNavigate(Screen.Favorites) },
            icon = { Icon(Icons.Default.Favorite, null) },
            label = { Text("Favori") }
        )
        NavigationBarItem(
            selected = current == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("Ayarlar") }
        )
    }
}'''
start = gallery.find(bottom_start)
end = gallery.find(bottom_end, start)
if start < 0 or end < 0:
    raise SystemExit("GalleryBottomBar section not found")
gallery = gallery[:start] + new_bottom + "\n\n" + gallery[end:]

(ROOT / gallery_path).write_text(gallery, encoding="utf-8")

# 6) Make video rotate action always visible in the top bar, and restore orientation when leaving video.
viewer_path = ROOT / "app/src/main/kotlin/com/sarilacivert/galeri/ui/ViewerScreen.kt"
viewer = viewer_path.read_text(encoding="utf-8")
viewer = viewer.replace(
    '''                    IconButton(onClick = { share(context, current) }) {\n                        Icon(Icons.Default.Share, "Paylaş")\n                    }''',
    '''                    IconButton(onClick = { share(context, current) }) {\n                        Icon(Icons.Default.Share, "Paylaş")\n                    }\n                    if (current.isVideo) {\n                        IconButton(onClick = {\n                            activity?.requestedOrientation =\n                                if (activity?.resources?.configuration?.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {\n                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT\n                                } else {\n                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE\n                                }\n                        }) {\n                            Icon(Icons.Default.RotateRight, "Videoyu yan çevir")\n                        }\n                    }'''
)
viewer = viewer.replace(
    '''                ViewerAction(Icons.Default.RotateRight, if (current.isVideo) "Ekran" else "Döndür") {\n                    if (current.isVideo) {\n                        activity?.requestedOrientation =\n                            if (activity?.resources?.configuration?.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {\n                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT\n                            } else {\n                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE\n                            }\n                    } else {\n                        photoRotation = (photoRotation + 90f) % 360f\n                    }\n                }''',
    '''                if (!current.isVideo) {\n                    ViewerAction(Icons.Default.RotateRight, "Döndür") {\n                        photoRotation = (photoRotation + 90f) % 360f\n                    }\n                }'''
)
viewer = viewer.replace(
    '''    DisposableEffect(barsVisible) {\n        val window = activity?.window''',
    '''    DisposableEffect(current.isVideo) {\n        onDispose {\n            if (current.isVideo) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED\n        }\n    }\n\n    DisposableEffect(barsVisible) {\n        val window = activity?.window'''
)
viewer_path.write_text(viewer, encoding="utf-8")

# 4 + 5) Stable image transform: rotation is part of the image matrix, not the Android View.
# This keeps the transformed bounds inside the viewport and removes competing View-rotation jitter.
zoom_path = ROOT / "app/src/main/kotlin/com/sarilacivert/galeri/ui/StableZoomImageView.kt"
zoom_path.write_text(r'''package com.sarilacivert.galeri.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Stable Eagle-style image engine: pinch, pan, swipe and free two-finger rotation. */
class StableZoomImageView(context: Context) : ImageView(context) {
    var onSingleTapAction: (() -> Unit)? = null
    var onPreviousAction: (() -> Unit)? = null
    var onNextAction: (() -> Unit)? = null

    private val drawMatrix = Matrix()
    private var shownBitmap: Bitmap? = null

    private var userScale = 1f
    private var translateX = 0f
    private var translateY = 0f
    private var externalRotation = 0f
    private var gestureRotation = 0f
    private var lastRotationAngle = 0f
    private var rotating = false

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var hadMultiTouch = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val swipeThreshold = 92f * resources.displayMetrics.density

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                hadMultiTouch = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                if (!factor.isFinite() || factor <= 0f) return true
                val oldScale = userScale
                val newScale = (oldScale * factor).coerceIn(1f, 8f)
                if (abs(newScale - oldScale) < 0.0008f) return true

                if (newScale <= 1.002f) {
                    userScale = 1f
                    translateX = 0f
                    translateY = 0f
                } else {
                    val ratio = newScale / oldScale.coerceAtLeast(0.0001f)
                    val cx = width / 2f
                    val cy = height / 2f
                    translateX = ratio * translateX + (1f - ratio) * (detector.focusX - cx)
                    translateY = ratio * translateY + (1f - ratio) * (detector.focusY - cy)
                    userScale = newScale
                }
                updateImageMatrix()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (userScale < 1.015f) resetZoomAndPan()
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!hadMultiTouch) {
                    performClick()
                    onSingleTapAction?.invoke()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (hadMultiTouch) return true
                if (userScale > 1.05f) {
                    resetZoomAndPan()
                } else {
                    val target = 2.5f
                    val ratio = target / userScale.coerceAtLeast(0.0001f)
                    val cx = width / 2f
                    val cy = height / 2f
                    translateX = ratio * translateX + (1f - ratio) * (e.x - cx)
                    translateY = ratio * translateY + (1f - ratio) * (e.y - cy)
                    userScale = target
                    updateImageMatrix()
                }
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
        isFocusable = true
    }

    fun setBitmap(bitmap: Bitmap?) {
        if (shownBitmap === bitmap) return
        shownBitmap = bitmap
        setImageBitmap(bitmap)
        gestureRotation = 0f
        resetZoomAndPan()
    }

    fun setExternalRotation(degrees: Float) {
        val normalized = GestureMath.normalizeRotation(degrees)
        if (abs(GestureMath.shortestAngleDelta(externalRotation, normalized)) < 0.01f) return
        externalRotation = normalized
        gestureRotation = 0f
        resetZoomAndPan()
    }

    fun resetTransform() {
        gestureRotation = 0f
        resetZoomAndPan()
    }

    private fun resetZoomAndPan() {
        userScale = 1f
        translateX = 0f
        translateY = 0f
        updateImageMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoomAndPan()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            hadMultiTouch = true
        }

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                hadMultiTouch = false
                rotating = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                hadMultiTouch = true
                if (event.pointerCount >= 2) {
                    lastRotationAngle = pointerAngle(event)
                    rotating = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val angle = pointerAngle(event)
                    if (rotating) {
                        val delta = GestureMath.shortestAngleDelta(lastRotationAngle, angle)
                        // Ignore sensor/finger micro-noise, but keep free rotation responsive.
                        if (delta.isFinite() && abs(delta) in 0.18f..30f) {
                            gestureRotation += delta
                            updateImageMatrix()
                        }
                    } else {
                        rotating = true
                    }
                    lastRotationAngle = angle
                } else if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val x = event.x
                    val y = event.y
                    if (userScale > 1.015f) {
                        translateX += x - lastX
                        translateY += y - lastY
                        updateImageMatrix()
                    }
                    lastX = x
                    lastY = y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                rotating = false
                // Re-seed one-finger pan coordinates from the pointer that remains,
                // preventing the classic post-pinch jump/tremor.
                val upIndex = event.actionIndex
                val remainIndex = if (upIndex == 0 && event.pointerCount > 1) 1 else 0
                if (remainIndex < event.pointerCount) {
                    lastX = event.getX(remainIndex)
                    lastY = event.getY(remainIndex)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!hadMultiTouch && userScale <= 1.015f) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) >= swipeThreshold && abs(dx) > abs(dy) * 1.2f && abs(dy) > touchSlop / 4f) {
                        if (dx > 0f) onPreviousAction?.invoke() else onNextAction?.invoke()
                    } else if (abs(dx) >= swipeThreshold && abs(dx) > abs(dy) * 1.2f) {
                        if (dx > 0f) onPreviousAction?.invoke() else onNextAction?.invoke()
                    }
                }
                if (userScale <= 1.015f) resetZoomAndPan()
                rotating = false
            }

            MotionEvent.ACTION_CANCEL -> {
                if (userScale <= 1.015f) resetZoomAndPan()
                rotating = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun pointerAngle(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }

    private fun updateImageMatrix() {
        val drawable = drawable ?: return
        if (width <= 0 || height <= 0) return

        val dw = drawable.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val dh = drawable.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val angle = GestureMath.normalizeRotation(externalRotation + gestureRotation)
        val radians = Math.toRadians(angle.toDouble())
        val c = abs(cos(radians)).toFloat()
        val s = abs(sin(radians)).toFloat()
        val rotatedW = (dw * c + dh * s).coerceAtLeast(1f)
        val rotatedH = (dw * s + dh * c).coerceAtLeast(1f)

        val baseScale = min(width / rotatedW, height / rotatedH)
        val finalScale = baseScale * userScale
        val displayW = rotatedW * finalScale
        val displayH = rotatedH * finalScale

        val maxX = max(0f, (displayW - width) / 2f)
        val maxY = max(0f, (displayH - height) / 2f)
        translateX = translateX.coerceIn(-maxX, maxX)
        translateY = translateY.coerceIn(-maxY, maxY)

        drawMatrix.reset()
        drawMatrix.postTranslate(-dw / 2f, -dh / 2f)
        drawMatrix.postRotate(angle)
        drawMatrix.postScale(finalScale, finalScale)
        drawMatrix.postTranslate(width / 2f + translateX, height / 2f + translateY)
        imageMatrix = drawMatrix
        invalidate()
    }
}
''', encoding="utf-8")

print("Gallery Ultimate v2 patches applied: name + trash + cache sync + bounded stable zoom + video rotate + simplified navigation")
