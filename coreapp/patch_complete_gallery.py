from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "enum class HomeSection { PHOTOS, VIDEOS, ALBUMS, DUPLICATES, TRASH }",
    "enum class HomeSection { MEDIA, ALBUMS, SETTINGS, PHOTOS, VIDEOS, DUPLICATES, TRASH }",
    "HomeSection enum",
)
replace_once(
    "var section by rememberSaveable { mutableStateOf(HomeSection.PHOTOS) }",
    "var section by rememberSaveable { mutableStateOf(HomeSection.MEDIA) }",
    "default home section",
)
replace_once(
    '''    LaunchedEffect(section) {
        selectedIds = emptySet()
        viewerIndex = null
        when (section) {
            HomeSection.PHOTOS -> vm.switchTab(GalleryTab.PHOTOS)
            HomeSection.VIDEOS -> vm.switchTab(GalleryTab.VIDEOS)
            HomeSection.TRASH -> vm.openTrash()
            HomeSection.ALBUMS, HomeSection.DUPLICATES -> Unit
        }
    }

    LaunchedEffect(refreshToken) {
        if (refreshToken > 0 && section in listOf(HomeSection.PHOTOS, HomeSection.VIDEOS, HomeSection.TRASH)) {
            vm.reload()
        }
    }''',
    '''    LaunchedEffect(section) {
        selectedIds = emptySet()
        viewerIndex = null
        when (section) {
            HomeSection.MEDIA -> vm.openMedia()
            HomeSection.SETTINGS -> showSettings = true
            HomeSection.PHOTOS -> vm.switchTab(GalleryTab.PHOTOS)
            HomeSection.VIDEOS -> vm.switchTab(GalleryTab.VIDEOS)
            HomeSection.TRASH -> vm.openTrash()
            HomeSection.ALBUMS, HomeSection.DUPLICATES -> Unit
        }
    }

    LaunchedEffect(refreshToken) {
        if (refreshToken > 0 && section in listOf(HomeSection.MEDIA, HomeSection.PHOTOS, HomeSection.VIDEOS, HomeSection.TRASH)) {
            vm.reload()
        }
    }''',
    "section effects",
)
replace_once(
    '''            NavigationBar {
                NavigationBarItem(
                    selected = section == HomeSection.PHOTOS,
                    onClick = { section = HomeSection.PHOTOS },
                    icon = { Text("▣") },
                    label = { Text("Foto") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.VIDEOS,
                    onClick = { section = HomeSection.VIDEOS },
                    icon = { Icon(Icons.Default.PlayArrow, null) },
                    label = { Text("Video") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.ALBUMS,
                    onClick = { section = HomeSection.ALBUMS },
                    icon = { Icon(Icons.Default.Collections, null) },
                    label = { Text("Albüm") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.DUPLICATES,
                    onClick = { section = HomeSection.DUPLICATES },
                    icon = { Icon(Icons.Default.FindInPage, null) },
                    label = { Text("Kopya") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.TRASH,
                    onClick = { section = HomeSection.TRASH },
                    icon = { Icon(Icons.Default.Delete, null) },
                    label = { Text("Çöp") }
                )
            }''',
    '''            NavigationBar {
                NavigationBarItem(
                    selected = section == HomeSection.MEDIA,
                    onClick = { section = HomeSection.MEDIA },
                    icon = { Text("▣") },
                    label = { Text("Medya") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.ALBUMS,
                    onClick = { section = HomeSection.ALBUMS },
                    icon = { Icon(Icons.Default.Collections, null) },
                    label = { Text("Albümler") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.SETTINGS,
                    onClick = { section = HomeSection.SETTINGS; showSettings = true },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Ayarlar") }
                )
            }''',
    "bottom navigation",
)
replace_once(
    '''                    section == HomeSection.PHOTOS -> "Fotoğraflar"
                    section == HomeSection.VIDEOS -> "Videolar"
                    section == HomeSection.ALBUMS -> "Albümler"
                    section == HomeSection.DUPLICATES -> "Yinelenenler"
                    else -> "Çöp Kutusu"''',
    '''                    section == HomeSection.MEDIA -> "Medya"
                    section == HomeSection.SETTINGS -> "Ayarlar"
                    section == HomeSection.PHOTOS -> "Fotoğraflar"
                    section == HomeSection.VIDEOS -> "Videolar"
                    section == HomeSection.ALBUMS -> "Albümler"
                    section == HomeSection.DUPLICATES -> "Yinelenenler"
                    else -> "Çöp Kutusu"''',
    "top title",
)
replace_once(
    "HomeSection.PHOTOS, HomeSection.VIDEOS, HomeSection.TRASH -> {",
    "HomeSection.MEDIA, HomeSection.SETTINGS, HomeSection.PHOTOS, HomeSection.VIDEOS, HomeSection.TRASH -> {",
    "media collection branch",
)
replace_once(
    '''        if (selected) {
            Box(''',
    '''        Text(
            mediaNameOverlay(item.name),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(horizontal = 5.dp, vertical = 3.dp)
        )
        if (selected) {
            Box(''',
    "thumbnail name overlay",
)

path.write_text(text, encoding="utf-8")
print("Complete gallery home patch applied")
