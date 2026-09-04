from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: beklenen 1 eslesme, bulunan {count}")
    text = text.replace(old, new, 1)


imports = [
    ("import androidx.compose.ui.geometry.Offset\n", "import androidx.compose.ui.graphics.Color\n", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.geometry.Offset\n"),
    ("import androidx.compose.ui.input.nestedscroll.NestedScrollConnection\n", "import androidx.compose.ui.input.pointer.pointerInput\n", "import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.input.nestedscroll.NestedScrollConnection\nimport androidx.compose.ui.input.nestedscroll.NestedScrollSource\nimport androidx.compose.ui.input.nestedscroll.nestedScroll\n"),
    ("import androidx.compose.ui.unit.Velocity\n", "import androidx.compose.ui.unit.IntOffset\n", "import androidx.compose.ui.unit.IntOffset\nimport androidx.compose.ui.unit.Velocity\n"),
]
for sentinel, anchor, replacement in imports:
    if sentinel not in text:
        replace_once(anchor, replacement, f"pull refresh import {sentinel.strip()}")

if "val pullRefreshConnection = remember(section, state.mode" not in text:
    marker = '''    BackHandler(enabled = selectedIds.isNotEmpty() && state.mode != CollectionMode.ALBUM) {
        selectedIds = emptySet()
    }

    Scaffold { padding ->'''
    block = '''    BackHandler(enabled = selectedIds.isNotEmpty() && state.mode != CollectionMode.ALBUM) {
        selectedIds = emptySet()
    }

    var pullDistancePx by remember(section, state.mode) { mutableFloatStateOf(0f) }
    var pullRefreshTriggered by remember(section, state.mode) { mutableStateOf(false) }
    val pullRefreshThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }

    fun refreshCurrentSection() {
        selectedIds = emptySet()
        when (section) {
            HomeSection.ALBUMS -> {
                albumsRefresh++
                if (state.mode == CollectionMode.ALBUM) vm.reload()
            }
            HomeSection.DUPLICATES -> duplicatesRefresh++
            else -> refreshToken++
        }
    }

    val pullRefreshConnection = remember(section, state.mode, pullRefreshThresholdPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0f && !pullRefreshTriggered) {
                    pullDistancePx += available.y
                    if (pullDistancePx >= pullRefreshThresholdPx) {
                        pullRefreshTriggered = true
                        refreshCurrentSection()
                    }
                } else if (available.y < 0f) {
                    pullDistancePx = 0f
                    pullRefreshTriggered = false
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pullDistancePx = 0f
                pullRefreshTriggered = false
                return Velocity.Zero
            }
        }
    }

    Scaffold { padding ->'''
    replace_once(marker, block, "pull refresh connection")

if ".nestedScroll(pullRefreshConnection)" not in text:
    replace_once(
        '''        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {''',
        '''        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullRefreshConnection)
        ) {''',
        "pull refresh modifier"
    )

path.write_text(text, encoding="utf-8")
print("Pull-down refresh wired")
