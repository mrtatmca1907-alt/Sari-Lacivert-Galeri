from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
text = path.read_text(encoding="utf-8")
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
            val current = currentItem
            if (current != null) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 18.dp, start = 10.dp, end = 10.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
                        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        current.name,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { optionsExpanded = true }) {
                            Text("☰", color = Color.White, style = MaterialTheme.typography.headlineSmall)
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
                }

                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 14.dp)
                        .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onShare(current) }) {
                        Icon(Icons.Default.Share, "Paylaş", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White)
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
print("Eagle viewer patch applied")
