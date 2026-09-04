from pathlib import Path

ROOT = Path(__file__).parent / "src/main/kotlin/com/atmaca/gallery"

# 1) Do not block the internal album picker on a full-library OEM scan.
picker_path = ROOT / "InternalToolAlbumPicker.kt"
picker = picker_path.read_text(encoding="utf-8")
old_picker = '''    LaunchedEffect(refreshKey) {
        loading = true
        albums = runCatching { repository.loadAlbumsOemSafe() }.getOrDefault(emptyList())
        loading = false
    }
'''
new_picker = '''    LaunchedEffect(refreshKey) {
        loading = true
        albums = emptyList()
        val grouped = linkedMapOf<String, GalleryAlbum>()
        var offset = 0
        val pageSize = 1200
        while (true) {
            val page = runCatching {
                repository.loadMixedPage(offset = offset, limit = pageSize)
            }.getOrDefault(emptyList())
            if (page.isEmpty()) break
            page.forEach { item ->
                val rawPath = item.relativePath.trim()
                val key = albumIdentityKey(rawPath, item.bucketId, item.bucketName)
                val displayPath = if (rawPath.isNotBlank()) normalizeRelativePath(rawPath) else ""
                val displayName = item.bucketName?.trim().orEmpty().ifBlank {
                    if (displayPath.isNotBlank()) albumDisplayName(displayPath) else "Depolama"
                }
                val current = grouped[key]
                grouped[key] = if (current == null) {
                    GalleryAlbum(
                        relativePath = displayPath,
                        name = displayName,
                        count = 1,
                        cover = item,
                        bucketId = item.bucketId,
                        bucketName = item.bucketName
                    )
                } else {
                    current.copy(count = current.count + 1, cover = current.cover ?: item)
                }
            }
            albums = grouped.values.sortedBy { it.name.lowercase() }
            loading = false
            if (page.size < pageSize) break
            offset += page.size
        }
        loading = false
    }
'''
if old_picker not in picker:
    raise SystemExit("InternalToolAlbumPicker target block not found")
picker_path.write_text(picker.replace(old_picker, new_picker, 1), encoding="utf-8")

# 2) Expand face-based framing enough to keep most of the person's body.
rules_path = ROOT / "FeatureRules.kt"
rules = rules_path.read_text(encoding="utf-8")
start = rules.index("fun personCropBounds(")
end = rules.index("\nfun isViewerDoubleTap", start)
new_bounds = '''fun personCropBounds(sourceWidth:Int,sourceHeight:Int,faceLeft:Int,faceTop:Int,faceRight:Int,faceBottom:Int):IntCropRect {
    if(sourceWidth<=0 || sourceHeight<=0) return IntCropRect(0,0,0,0)
    val l0=minOf(faceLeft,faceRight).coerceIn(0,sourceWidth-1)
    val r0=maxOf(faceLeft,faceRight).coerceIn(l0+1,sourceWidth)
    val t0=minOf(faceTop,faceBottom).coerceIn(0,sourceHeight-1)
    val b0=maxOf(faceTop,faceBottom).coerceIn(t0+1,sourceHeight)
    val faceW=(r0-l0).coerceAtLeast(1)
    val faceH=(b0-t0).coerceAtLeast(1)
    val targetW=maxOf(faceW*4f, sourceWidth*0.60f).roundToInt().coerceIn(faceW, sourceWidth)
    val targetH=maxOf(faceH*7.2f, sourceHeight*0.75f).roundToInt().coerceIn(faceH, sourceHeight)
    val centerX=(l0+r0)/2f
    var left=(centerX-targetW/2f).roundToInt()
    left=left.coerceIn(0, (sourceWidth-targetW).coerceAtLeast(0))
    var top=(t0-faceH*1.2f).roundToInt()
    top=top.coerceIn(0, (sourceHeight-targetH).coerceAtLeast(0))
    return IntCropRect(left, top, left+targetW, top+targetH)
}
'''
rules_path.write_text(rules[:start] + new_bounds + rules[end:], encoding="utf-8")

# 3) Direct file picker returns file:// URIs. Decode them as real files instead of
# asking ContentResolver/ImageDecoder to treat them like content:// URIs.
engine_path = ROOT / "CompleteToolEngine.kt"
engine = engine_path.read_text(encoding="utf-8")
old_decode = '''    private fun decodeBitmap(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val pixels = info.size.width.toLong() * info.size.height.toLong()
                if (pixels > 24_000_000L) {
                    val edge = max(info.size.width, info.size.height)
                    val sample = (edge / 5000).coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
            }
        } else {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()
'''
new_decode = '''    private fun decodeBitmap(uri: Uri): Bitmap? = runCatching {
        if (uri.scheme.equals("file", true)) {
            val path = uri.path ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val pixels = bounds.outWidth.toLong().coerceAtLeast(0L) * bounds.outHeight.toLong().coerceAtLeast(0L)
            val edge = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            val sample = if (pixels > 24_000_000L) (edge / 5000).coerceAtLeast(1) else 1
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } else if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val pixels = info.size.width.toLong() * info.size.height.toLong()
                if (pixels > 24_000_000L) {
                    val edge = max(info.size.width, info.size.height)
                    val sample = (edge / 5000).coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
            }
        } else {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()
'''
if old_decode not in engine:
    raise SystemExit("CompleteToolEngine decode block not found")
engine_path.write_text(engine.replace(old_decode, new_decode, 1), encoding="utf-8")

print("Physical tool fixes applied")
