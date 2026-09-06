from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
text = path.read_text(encoding="utf-8")

# Background blur is intentionally viewer-only; do not touch the gesture engine.
if "import androidx.compose.ui.draw.blur\n" not in text:
    anchor = "import androidx.compose.ui.draw.clip\n"
    if anchor not in text:
        raise RuntimeError("viewer blur import anchor missing")
    text = text.replace(anchor, anchor + "import androidx.compose.ui.draw.blur\n", 1)

start_marker = "@OptIn(ExperimentalFoundationApi::class)\n@Composable\nprivate fun MediaViewer("
end_marker = "@Composable\nprivate fun PhotoPage("
start = text.index(start_marker)
end = text.index(end_marker, start)

replacement = r'''@OptIn(ExperimentalFoundationApi::class)
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
    val prefs = remember { context.getSharedPreferences("gallery", Context.MODE_PRIVATE) }
    val screenshotActions = remember { GalleryActions(context) }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val rotations = remember { mutableStateMapOf<Long, Float>() }
    val zooms = remember { mutableStateMapOf<Long, Float>() }
    var gestureActive by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var optionsExpanded by remember { mutableStateOf(false) }
    var screenshotMode by remember { mutableStateOf(false) }
    var captureInProgress by remember { mutableStateOf(false) }
    var screenshotOffsetX by remember { mutableFloatStateOf(0f) }
    var screenshotOffsetY by remember { mutableFloatStateOf(0f) }
    var showInfo by remember { mutableStateOf(false) }
    var slideshowRunning by remember { mutableStateOf(false) }
    var favoriteIds by remember {
        mutableStateOf(prefs.getStringSet("favorite_ids", emptySet())?.toSet() ?: emptySet())
    }

    val slideshowSeconds = clampSlideshowSeconds(prefs.getInt("slideshow_seconds", 4))
    val slideshowLoop = prefs.getBoolean("slideshow_loop", true)
    val slideshowRandom = prefs.getBoolean("slideshow_random", false)

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

    DisposableEffect(slideshowRunning, activity) {
        val window = activity?.window
        if (slideshowRunning) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (slideshowRunning) {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(pager, items.size) {
        snapshotFlow { pager.currentPage }
            .distinctUntilChanged()
            .collect { page -> if (items.size - page <= 4) onNeedMore() }
    }

    LaunchedEffect(slideshowRunning, pager.currentPage, items.size, slideshowSeconds, slideshowLoop, slideshowRandom) {
        if (!slideshowRunning || items.size <= 1) return@LaunchedEffect
        delay(slideshowSeconds * 1000L)
        if (!slideshowRunning) return@LaunchedEffect
        val current = pager.currentPage
        val controller = SlideshowController(items.size, slideshowLoop)
        if (!controller.canAdvance(current)) {
            slideshowRunning = false
            return@LaunchedEffect
        }
        val next = if (slideshowRandom && items.size > 1) {
            var candidate = current
            while (candidate == current) candidate = kotlin.random.Random.nextInt(items.size)
            candidate
        } else controller.nextIndex(current)
        pager.animateScrollToPage(next)
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

    val backgroundBitmap by produceState<Bitmap?>(initialValue = null, currentItem?.uri) {
        val current = currentItem
        value = if (current != null && !current.isVideo && Build.VERSION.SDK_INT >= 29) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.contentResolver.loadThumbnail(
                        current.uri,
                        android.util.Size(420, 720),
                        null
                    )
                }.getOrNull()
            }
        } else null
    }

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

    fun toggleFavorite(item: GalleryMedia) {
        val id = item.id.toString()
        favoriteIds = if (id in favoriteIds) favoriteIds - id else favoriteIds + id
        prefs.edit().putStringSet("favorite_ids", favoriteIds.toSet()).apply()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        backgroundBitmap?.let { bg ->
            Image(
                bitmap = bg.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp)
                    .graphicsLayer(alpha = 0.42f, scaleX = 1.12f, scaleY = 1.12f)
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)))
        }

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
            val current = currentItem
            if (current != null) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(top = 14.dp, start = 2.dp, end = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White)
                    }
                    Text(
                        current.name,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!current.isVideo) {
                        IconButton(onClick = {
                            rotations[current.id] = nextQuarterRotation(rotations[current.id] ?: 0f)
                        }) {
                            Icon(Icons.Default.RotateRight, "Döndür", tint = Color.White)
                        }
                        IconButton(onClick = { onCrop(current) }) {
                            Icon(Icons.Default.Crop, "Düzenle", tint = Color.White)
                        }
                    }
                    Box {
                        IconButton(onClick = { optionsExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "Diğer", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = optionsExpanded,
                            onDismissRequest = { optionsExpanded = false }
                        ) {
                            if (!current.isVideo) {
                                DropdownMenuItem(
                                    text = { Text("Screenshot modu") },
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
                                text = { Text(if (slideshowRunning) "Slaytı durdur" else "Slayt gösterisi") },
                                onClick = {
                                    optionsExpanded = false
                                    slideshowRunning = !slideshowRunning
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
                }

                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.66f))
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { toggleFavorite(current) }) {
                        Text(if (current.id.toString() in favoriteIds) "♥" else "♡", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = { if (!current.isVideo) onCrop(current) }) {
                        Icon(Icons.Default.Edit, "Düzenle", tint = if (current.isVideo) Color.Gray else Color.White)
                    }
                    IconButton(onClick = { onShare(current) }) {
                        Icon(Icons.Default.Share, "Paylaş", tint = Color.White)
                    }
                    IconButton(onClick = { onTrash(current) }) {
                        Icon(Icons.Default.Delete, "Çöp", tint = Color.White)
                    }
                    IconButton(onClick = { showInfo = !showInfo }) {
                        Text("ⓘ", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = { slideshowRunning = !slideshowRunning }) {
                        Text(if (slideshowRunning) "Ⅱ" else "▶", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                }

                if (showInfo) {
                    Column(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(current.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${current.width} × ${current.height}", color = Color.White)
                        Text(formatBytes(current.size), color = Color.White)
                        Text(current.relativePath, color = Color.LightGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (current.isVideo) Text("Süre: ${formatDuration(current.durationMs)}", color = Color.White)
                    }
                }
            }
        }

        if (screenshotMode && currentItem?.isVideo == false && !captureInProgress) {
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

path.write_text(text[:start] + replacement + text[end:], encoding="utf-8")
print("Complete Eagle viewer patch applied")
