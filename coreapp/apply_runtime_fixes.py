from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: beklenen 1 eslesme, bulunan {count}")
    text = text.replace(old, new, 1)


if "import androidx.compose.foundation.gestures.scrollBy\n" not in text:
    replace_once(
        "import androidx.compose.foundation.gestures.detectTransformGestures\n",
        "import androidx.compose.foundation.gestures.detectTransformGestures\nimport androidx.compose.foundation.gestures.scrollBy\n",
        "scrollBy import"
    )

if "shouldLoadMoreForEmptyFilteredPage(\n            totalLoaded = state.items.size" not in text:
    replace_once(
        "        if (mediaSort == MediaSort.RANDOM) ordered else applySortDirection(ordered, sortDirection)\n    }\n\n    when {",
        "        if (mediaSort == MediaSort.RANDOM) ordered else applySortDirection(ordered, sortDirection)\n    }\n\n    LaunchedEffect(\n        state.items.size,\n        visibleItems.size,\n        state.hasMore,\n        state.loading,\n        mediaFilter\n    ) {\n        if (\n            shouldLoadMoreForEmptyFilteredPage(\n                totalLoaded = state.items.size,\n                filteredVisible = visibleItems.size,\n                hasMore = state.hasMore,\n                loading = state.loading\n            )\n        ) {\n            onLoadMore()\n        }\n    }\n\n    when {",
        "filtered paging wiring"
    )

if "var gridHeightPx by remember { mutableIntStateOf(0) }" not in text:
    replace_once(
        "    var dragY by remember { mutableFloatStateOf(0f) }\n    var lastDragIndex by remember { mutableIntStateOf(-1) }\n",
        "    var dragY by remember { mutableFloatStateOf(0f) }\n    var lastDragIndex by remember { mutableIntStateOf(-1) }\n    var gridHeightPx by remember { mutableIntStateOf(0) }\n    var dragActive by remember { mutableStateOf(false) }\n    val dragEdgePx = with(density) { 96.dp.toPx() }\n    val dragStepPx = with(density) { 36.dp.toPx() }\n",
        "drag state"
    )

if "dragAutoScrollDelta(" not in text:
    replace_once(
        "    LaunchedEffect(gridState, items.size, hasMore) {\n",
        "    LaunchedEffect(dragActive, gridHeightPx, items.size) {\n        while (dragActive) {\n            val edgeDelta = dragAutoScrollDelta(\n                pointerY = dragY,\n                viewportHeight = gridHeightPx.toFloat(),\n                edgePx = dragEdgePx\n            )\n            if (edgeDelta != 0f) {\n                gridState.scrollBy(edgeDelta * dragStepPx)\n                val safeY = dragY.coerceIn(\n                    0f,\n                    (gridHeightPx - 1).coerceAtLeast(0).toFloat()\n                )\n                selectAt(dragX, safeY)\n            }\n            delay(16L)\n        }\n    }\n\n    LaunchedEffect(gridState, items.size, hasMore) {\n",
        "drag auto scroll loop"
    )

if ".onSizeChanged { gridHeightPx = it.height }\n            .pointerInput(items.size, columns)" not in text:
    replace_once(
        "        modifier = Modifier\n            .fillMaxSize()\n            .pointerInput(items.size, columns) {",
        "        modifier = Modifier\n            .fillMaxSize()\n            .onSizeChanged { gridHeightPx = it.height }\n            .pointerInput(items.size, columns) {",
        "grid size tracking"
    )

if "dragActive = true\n                        lastDragIndex = -1" not in text:
    replace_once(
        "                    onDragStart = { offset ->\n                        lastDragIndex = -1",
        "                    onDragStart = { offset ->\n                        dragActive = true\n                        lastDragIndex = -1",
        "drag start"
    )

if "onDragEnd = { dragActive = false; lastDragIndex = -1 }" not in text:
    replace_once(
        "                    onDragEnd = { lastDragIndex = -1 },\n                    onDragCancel = { lastDragIndex = -1 }",
        "                    onDragEnd = { dragActive = false; lastDragIndex = -1 },\n                    onDragCancel = { dragActive = false; lastDragIndex = -1 }",
        "drag end cancel"
    )

path.write_text(text, encoding="utf-8")
print("Runtime gallery fixes applied")
