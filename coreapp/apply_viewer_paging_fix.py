from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
text = path.read_text(encoding="utf-8")

old = '''    LaunchedEffect(pager, items.size) {
        snapshotFlow { pager.currentPage }
            .distinctUntilChanged()
            .collect { page -> if (items.size - page <= 4) onNeedMore() }
    }'''

new = '''    LaunchedEffect(pager, items.size) {
        snapshotFlow { pager.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (viewerPagingShouldLoadMore(items.size, page)) onNeedMore()
            }
    }'''

if new not in text:
    if text.count(old) != 1:
        raise SystemExit("viewer paging marker bulunamadi")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("Viewer paging fix applied")
