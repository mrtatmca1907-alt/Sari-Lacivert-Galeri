from pathlib import Path
import re

path = Path('coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt')
text = path.read_text()

text = re.sub(
    r'(?:import androidx\.compose\.material\.icons\.filled\.Crop\n)+',
    'import androidx.compose.material.icons.filled.Crop\n',
    text,
)

additions = {
    'import android.view.View\n': 'import android.view.View\nimport android.widget.Toast\n',
    'import androidx.compose.foundation.gestures.detectTapGestures\n': 'import androidx.compose.foundation.gestures.detectDragGestures\nimport androidx.compose.foundation.gestures.detectTapGestures\n',
    'import androidx.compose.foundation.layout.padding\n': 'import androidx.compose.foundation.layout.offset\nimport androidx.compose.foundation.layout.padding\n',
    'import androidx.compose.ui.unit.dp\n': 'import androidx.compose.ui.unit.IntOffset\nimport androidx.compose.ui.unit.dp\n',
    'import kotlinx.coroutines.Dispatchers\n': 'import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.delay\n',
    'import kotlinx.coroutines.withContext\n': 'import kotlinx.coroutines.withContext\nimport kotlin.math.roundToInt\n',
}
for old, new in additions.items():
    if new not in text:
        if old not in text:
            raise SystemExit(f'missing import anchor: {old!r}')
        text = text.replace(old, new, 1)

crop_pattern = re.compile(
    r'    cropItem\?\.let \{ item ->\n.*?        return\n    \}\n\n',
    re.S,
)
matches = list(crop_pattern.finditer(text))
if not matches:
    raise SystemExit('crop block not found')
for match in reversed(matches[1:]):
    text = text[:match.start()] + text[match.end():]

old_crop = '                onCrop = { cropItem = it }\n'
new_crop = '                onCrop = { item -> runAfterWriteAccess(listOf(item)) { cropItem = item } }\n'
if old_crop in text:
    text = text.replace(old_crop, new_crop, 1)
elif new_crop not in text:
    raise SystemExit('viewer crop wiring anchor not found')

start_marker = '@OptIn(ExperimentalFoundationApi::class)\n@Composable\nprivate fun MediaViewer('
end_marker = '\n@Composable\nprivate fun PhotoPage'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('MediaViewer block markers not found')

viewer = r'''@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewer(
    items: List<GalleryMedia>,
    initialIndex: Int,
    onBack: () -> Unit,
    onNeedMore: () -> Unit,
    onShare: (GalleryMedia) -> Unit,
    onTrash: (GalleryMedia) -> Unit,
    onRename: (GalleryMedia) -> Unit,
    onCrop: (GalleryMedia) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val screenshotActions = remember { GalleryActions(context) }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = initialIndex) { items.size }
    val rotations = remember { mutableStateMapOf<Long, Float>() }
    val zooms = remember { mutableStateMapOf<Long, Float>() }
    var gestureActive by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var optionsExpanded by remember { mutableStateOf(false) }
    var screenshotMode by remember { mutableStateOf(false) }
    var captureInProgress by remember { mutableStateOf(false) }
    var screenshotOffsetX by remember { mutableFloatStateOf(0f) }
    var screenshotOffsetY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(activity) {
        val decor = activity?.window?.decorView
        val oldFlags = decor?.systemUiVisibility ?: 0
        if (decor != null) {
            @Suppress("DEPRECATION")
            decor.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        onDispose {
            if (decor != null) {
                @Suppress("DEPRECATION")
                decor.systemUiVisibility = oldFlags
            }
        }
    }

    LaunchedEffect(pager, items.size) {
        snapshotFlow { pager.currentPage }
            .distinctUntilChanged()
            .collect { page -> if (items.size - page <= 4) onNeedMore() }
    }

    BackHandler(onBack = onBack)

    val currentItem = items.getOrNull(pager.currentPage)
    val currentScale = currentItem?.let { zooms[it.id] } ?: 1f
    val currentRotation = currentItem?.let { rotations[it.id] } ?: 0f
    val renderChrome = shouldRenderViewerChrome(
        captureInProgress = captureInProgress,
        controlsVisible = controlsVisible,
        scale = currentScale,
        gestureActive = gestureActive
    )

    fun captureCleanScreenshot() {
        if (captureInProgress || currentItem?.isVideo != false) return
        val host = activity ?: return
        scope.launch {
            captureInProgress = true
            optionsExpanded = false
            delay(140)
            val root = host.window.decorView.rootView
            val bitmap = if (root.width > 0 && root.height > 0) {
                Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            } else null
            val saved = bitmap?.let { shot ->
                runCatching {
                    root.draw(android.graphics.Canvas(shot))
                    screenshotActions.saveScreenshot(shot)
                }.getOrNull().also { shot.recycle() }
            }
            captureInProgress = false
            Toast.makeText(
                context,
                if (saved != null) "Screenshot kaydedildi" else "Screenshot kaydedilemedi",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = shouldEnablePager(currentScale, currentRotation),
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            val rotation = rotations[item.id] ?: 0f
            if (item.isVideo) {
                VideoPage(item, rotation)
            } else {
                StablePhotoPage(
                    item = item,
                    rotation = rotation,
                    onRotationChanged = { rotations[item.id] = it },
                    onScaleChanged = { zooms[item.id] = it },
                    onGestureActive = { gestureActive = it },
                    onFitTap = { controlsVisible = !controlsVisible }
                )
            }
        }

        if (renderChrome) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(top = 22.dp, start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    currentItem?.name.orEmpty(),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            val current = currentItem
            if (current != null) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.60f))
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onShare(current) }) {
                        Icon(Icons.Default.Share, "Paylaş", tint = Color.White)
                    }

                    if (!current.isVideo) {
                        IconButton(onClick = {
                            rotations[current.id] = nextQuarterRotation(rotations[current.id] ?: 0f)
                        }) {
                            Icon(Icons.Default.RotateRight, "90 derece döndür", tint = Color.White)
                        }
                    }

                    Box {
                        IconButton(onClick = { optionsExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "Seçenekler", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = optionsExpanded,
                            onDismissRequest = { optionsExpanded = false }
                        ) {
                            if (!current.isVideo) {
                                DropdownMenuItem(
                                    text = { Text("Kırp") },
                                    leadingIcon = { Icon(Icons.Default.Crop, null) },
                                    onClick = {
                                        optionsExpanded = false
                                        onCrop(current)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (screenshotMode) "Screenshot modunu kapat" else "Screenshot modu") },
                                    leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                                    onClick = {
                                        screenshotMode = !screenshotMode
                                        optionsExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Ad değiştir") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    optionsExpanded = false
                                    onRename(current)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Çöpe taşı / sil") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = {
                                    optionsExpanded = false
                                    onTrash(current)
                                }
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White)
                    }
                }
            }
        }

        if (
            screenshotMode &&
            currentItem?.isVideo == false &&
            !captureInProgress
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset {
                        IntOffset(
                            screenshotOffsetX.roundToInt(),
                            screenshotOffsetY.roundToInt()
                        )
                    }
                    .background(Color.Black.copy(alpha = 0.68f), CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            screenshotOffsetX += dragAmount.x
                            screenshotOffsetY += dragAmount.y
                        }
                    }
            ) {
                IconButton(onClick = { captureCleanScreenshot() }) {
                    Icon(Icons.Default.PhotoCamera, "Screenshot", tint = Color.White)
                }
            }
        }
    }
}
'''

text = text[:start] + viewer.rstrip() + text[end:]
path.write_text(text)

gradle = Path('coreapp/build.gradle.kts')
g = gradle.read_text()
g = g.replace('versionCode = 6', 'versionCode = 7')
g = g.replace('versionName = "0.4.2-stable-photo"', 'versionName = "0.5.0-photo-tools"')
gradle.write_text(g)

Path('.github/workflows/apply-photo-tools-v05.yml').unlink(missing_ok=True)
Path('scripts/apply_photo_tools_v05.py').unlink(missing_ok=True)
