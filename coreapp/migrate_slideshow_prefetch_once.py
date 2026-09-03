from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
text = path.read_text(encoding="utf-8")

old = '''    LaunchedEffect(slideshowRunning, pager.currentPage, items.size, slideshowSeconds, slideshowLoop, slideshowRandom) {
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
'''

new = '''    LaunchedEffect(slideshowRunning, pager.currentPage, items.size, slideshowSeconds, slideshowLoop, slideshowRandom) {
        if (!slideshowRunning || items.size <= 1) return@LaunchedEffect
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

        val viewerWidth = (activity?.window?.decorView?.width ?: 1080).coerceAtLeast(1)
        val viewerHeight = (activity?.window?.decorView?.height ?: 1920).coerceAtLeast(1)
        val pagesToPrepare = if (slideshowRandom) listOf(next)
            else slideshowPrefetchIndices(current, items.size, slideshowLoop, ahead = 2)
        pagesToPrepare.forEach { page ->
            items.getOrNull(page)?.let { media ->
                if (!media.isVideo) prefetchViewerBitmap(context, media, viewerWidth, viewerHeight)
            }
        }

        delay(slideshowSeconds * 1000L)
        if (!slideshowRunning) return@LaunchedEffect
        pager.animateScrollToPage(next)
    }
'''

if "pagesToPrepare = if (slideshowRandom)" in text:
    print("Slideshow prefetch already wired")
    raise SystemExit(0)

if old not in text:
    raise SystemExit("Expected slideshow block not found; refusing to guess")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Slideshow prefetch wired directly into GalleryApp.kt")
