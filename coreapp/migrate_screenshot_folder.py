from pathlib import Path

p = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
s = p.read_text(encoding="utf-8")

def rep(old, new, label):
    global s
    if new in s:
        print(label, "already")
        return
    if s.count(old) != 1:
        raise SystemExit(f"{label}: expected one block, got {s.count(old)}")
    s = s.replace(old, new, 1)
    print(label, "ok")

rep(
'''    var screenshotMode by remember { mutableStateOf(false) }
    var captureInProgress by remember { mutableStateOf(false) }
''',
'''    var screenshotMode by remember { mutableStateOf(false) }
    var screenshotFolderUri by remember { mutableStateOf(prefs.getString("screenshot_tree_uri", null)) }
    var captureInProgress by remember { mutableStateOf(false) }
''',
"screenshot folder state")

rep(
'''    val slideshowSeconds = clampSlideshowSeconds(prefs.getInt("slideshow_seconds", 4))
    val slideshowLoop = prefs.getBoolean("slideshow_loop", true)
    val slideshowRandom = prefs.getBoolean("slideshow_random", false)

    DisposableEffect(activity) {
''',
'''    val slideshowSeconds = clampSlideshowSeconds(prefs.getInt("slideshow_seconds", 4))
    val slideshowLoop = prefs.getBoolean("slideshow_loop", true)
    val slideshowRandom = prefs.getBoolean("slideshow_random", false)

    val screenshotFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            screenshotFolderUri = uri.toString()
            prefs.edit().putString("screenshot_tree_uri", uri.toString()).apply()
            Toast.makeText(context, "Screenshot klasörü seçildi", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(activity) {
''',
"screenshot folder launcher")

rep(
'''                    root.draw(android.graphics.Canvas(shot))
                    screenshotActions.saveScreenshot(shot)
''',
'''                    root.draw(android.graphics.Canvas(shot))
                    val selectedTree = screenshotFolderUri
                        ?.takeIf(::hasCustomScreenshotFolder)
                        ?.let(Uri::parse)
                    if (selectedTree != null) saveScreenshotToTree(context, shot, selectedTree)
                    else screenshotActions.saveScreenshot(shot)
''',
"screenshot custom save")

needle = '''                                DropdownMenuItem(
                                    text = { Text("Screenshot modu") },
                                    leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                                    onClick = {
                                        screenshotMode = !screenshotMode
                                        optionsExpanded = false
                                    }
                                )
'''
rep(
needle,
needle + '''                                DropdownMenuItem(
                                    text = { Text("Screenshot klasörü seç") },
                                    leadingIcon = { Icon(Icons.Default.Folder, null) },
                                    onClick = {
                                        optionsExpanded = false
                                        screenshotFolderPicker.launch(null)
                                    }
                                )
''',
"screenshot folder menu")

p.write_text(s, encoding="utf-8")
