from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str):
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"pattern not found in {path}: {old[:180]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

# 1) Tools are launched by the gallery root, never from inside Settings Dialog.
extras = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/CompleteSettingsExtras.kt"
replace_once(
    extras,
    '''fun CompleteSettingsExtras(\n    onOpenTrash: () -> Unit,\n    onOpenDuplicates: () -> Unit\n) {''',
    '''fun CompleteSettingsExtras(\n    onOpenTrash: () -> Unit,\n    onOpenDuplicates: () -> Unit,\n    onOpenTool: (AtmacaToolPage) -> Unit\n) {'''
)
replace_once(extras, '    var activeTool by remember { mutableStateOf<AtmacaToolPage?>(null) }\n', '')
replace_once(extras,
    '        ToolLaunchButton("Akıllı Kişi Kırpma", "Fotoğraflardaki kişi/yüz bölgelerini ayrı JPEG olarak üretir.") { activeTool = AtmacaToolPage.PERSON_CROP }',
    '        ToolLaunchButton("Akıllı Kişi Kırpma", "Fotoğraflardaki kişi/yüz bölgelerini ayrı JPEG olarak üretir.") { onOpenTool(AtmacaToolPage.PERSON_CROP) }')
replace_once(extras,
    '        ToolLaunchButton("Görsel Paketleyici", "Seçilen medya dosyalarını belirlediğin grup boyutuyla klasörlere ayırır.") { activeTool = AtmacaToolPage.PACKAGER }',
    '        ToolLaunchButton("Görsel Paketleyici", "Seçilen medya dosyalarını belirlediğin grup boyutuyla klasörlere ayırır.") { onOpenTool(AtmacaToolPage.PACKAGER) }')
replace_once(extras,
    '        ToolLaunchButton("Video Kareleri", "Videolardan seçtiğin hızda JPEG kareleri ayrı video klasörlerine çıkarır.") { activeTool = AtmacaToolPage.VIDEO_FRAMES }',
    '        ToolLaunchButton("Video Kareleri", "Videolardan seçtiğin hızda JPEG kareleri ayrı video klasörlerine çıkarır.") { onOpenTool(AtmacaToolPage.VIDEO_FRAMES) }')
replace_once(extras, '\n    activeTool?.let { tool -> AtmacaToolDialog(tool = tool, onDismiss = { activeTool = null }) }\n', '\n')
replace_once(extras, '@Composable\nprivate fun AtmacaToolDialog(', '@Composable\nfun AtmacaToolDialog(')

settings = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/ModernSettingsDialog.kt"
replace_once(
    settings,
    '''    onSortDirection: (SortDirection) -> Unit,\n    onOpenTrash: () -> Unit,\n    onOpenDuplicates: () -> Unit\n) {''',
    '''    onSortDirection: (SortDirection) -> Unit,\n    onOpenTrash: () -> Unit,\n    onOpenDuplicates: () -> Unit,\n    onOpenTool: (AtmacaToolPage) -> Unit\n) {'''
)
replace_once(
    settings,
    '''                        CompleteSettingsExtras(\n                            onOpenTrash = onOpenTrash,\n                            onOpenDuplicates = onOpenDuplicates\n                        )''',
    '''                        CompleteSettingsExtras(\n                            onOpenTrash = onOpenTrash,\n                            onOpenDuplicates = onOpenDuplicates,\n                            onOpenTool = onOpenTool\n                        )'''
)

gallery = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt"
replace_once(
    gallery,
    '''    var showSettings by remember { mutableStateOf(false) }\n    var showMore by remember { mutableStateOf(false) }''',
    '''    var showSettings by remember { mutableStateOf(false) }\n    var activeTool by remember { mutableStateOf<AtmacaToolPage?>(null) }\n    var showMore by remember { mutableStateOf(false) }'''
)
replace_once(
    gallery,
    '''            onOpenDuplicates = {\n                showSettings = false\n                section = HomeSection.DUPLICATES\n            }\n        )\n    }\n\n    BackHandler''',
    '''            onOpenDuplicates = {\n                showSettings = false\n                section = HomeSection.DUPLICATES\n            },\n            onOpenTool = { tool ->\n                showSettings = false\n                if (section == HomeSection.SETTINGS) section = HomeSection.MEDIA\n                activeTool = tool\n            }\n        )\n    }\n\n    activeTool?.let { tool ->\n        AtmacaToolDialog(tool = tool, onDismiss = { activeTool = null })\n    }\n\n    BackHandler'''
)
replace_once(
    gallery,
    '''                    } else {\n                        AlbumGrid(\n                            albums = if (albums.isNotEmpty()) albums else quickAlbums(state.items),\n                            onOpen = { album -> vm.openAlbum(album) }\n                        )\n                    }''',
    '''                    } else {\n                        Column(Modifier.fillMaxSize()) {\n                            Text(\n                                albumDiagnosticText(albums.size),\n                                style = MaterialTheme.typography.labelMedium,\n                                color = MaterialTheme.colorScheme.primary,\n                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)\n                            )\n                            AlbumGrid(\n                                albums = if (albums.isNotEmpty()) albums else quickAlbums(state.items),\n                                onOpen = { album -> vm.openAlbum(album) }\n                            )\n                        }\n                    }'''
)

rules = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/OemFixRules.kt"
text = rules.read_text(encoding="utf-8")
if 'fun toolHostMustBeGalleryRoot()' not in text:
    text += '\nfun toolHostMustBeGalleryRoot(): Boolean = true\n\nfun albumDiagnosticText(count: Int): String = "Bulunan albüm: ${count.coerceAtLeast(0)}"\n'
rules.write_text(text, encoding="utf-8")

identity = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/BuildIdentity.kt"
replace_once(identity, 'const val ATMACA_TEST_VERSION_CODE: Int = 140904', 'const val ATMACA_TEST_VERSION_CODE: Int = 140905')
replace_once(identity, 'private const val ATMACA_BUILD_BADGE: String = "BUILD 140904"', 'private const val ATMACA_BUILD_BADGE: String = "BUILD 140904-R2"')

build = ROOT / "coreapp/build.gradle.kts"
replace_once(build, '        versionCode = 140904', '        versionCode = 140905')
replace_once(build, '        versionName = "0.7.0-hios-build-140904"', '        versionName = "0.7.1-root-tool-host-R2"')

test = ROOT / "coreapp/src/test/kotlin/com/atmaca/gallery/OemRegressionTest.kt"
replace_once(test, '        assertEquals("BUILD 140904", visibleBuildBadge())\n        assertTrue(appVersionCodeForTest() > 13)', '        assertEquals("BUILD 140904-R2", visibleBuildBadge())\n        assertTrue(appVersionCodeForTest() > 140904)')

print("root tool host + album diagnostics + unique R2 build applied")
