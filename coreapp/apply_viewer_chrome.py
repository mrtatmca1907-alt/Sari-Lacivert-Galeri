from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
text = path.read_text(encoding="utf-8")

old_bottom = '''                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.66f))
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White)
                    }
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
'''

new_bottom = '''                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.66f))
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { showInfo = !showInfo }) {
                        Text("ⓘ", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = { if (!current.isVideo) onCrop(current) }) {
                        Icon(Icons.Default.Crop, "Kırp", tint = if (current.isVideo) Color.Gray else Color.White)
                    }
                    IconButton(onClick = { onTrash(current) }) {
                        Icon(Icons.Default.Delete, "Çöp", tint = Color.White)
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White)
                    }
                }
'''

if new_bottom not in text:
    count = text.count(old_bottom)
    if count != 1:
        raise SystemExit(f"viewer bottom bar: beklenen 1 eslesme, bulunan {count}")
    text = text.replace(old_bottom, new_bottom, 1)

share_menu = '''                            DropdownMenuItem(
                                text = { Text("Paylaş") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    optionsExpanded = false
                                    onShare(current)
                                }
                            )
'''
if 'text = { Text("Paylaş") }' not in text:
    marker = '''                            DropdownMenuItem(
                                text = { Text("Ad değiştir") },'''
    if text.count(marker) != 1:
        raise SystemExit("viewer share menu marker bulunamadi")
    text = text.replace(marker, share_menu + marker, 1)

path.write_text(text, encoding="utf-8")
print("Viewer chrome simplified")
