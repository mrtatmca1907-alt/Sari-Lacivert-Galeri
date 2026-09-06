from pathlib import Path

p = Path('coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt')
s = p.read_text()
old = '''        val albums = snapshot()\n        return albumQueryOutcome(albums, imageFailed = failed && albums.isEmpty(), videoFailed = failed && albums.isEmpty())\n    }\n'''
new = '''        val albums = snapshot()\n        if (shouldUseSeparateAlbumCollections(albums.size, failed)) {\n            val fallback = loadAlbumsFromSeparateCollections()\n            if (fallback.albums.isNotEmpty()) return fallback\n        }\n        return albumQueryOutcome(albums, imageFailed = failed && albums.isEmpty(), videoFailed = failed && albums.isEmpty())\n    }\n'''
if old not in s:
    raise SystemExit('target block not found')
p.write_text(s.replace(old, new, 1))
print('empty album OEM fallback applied')
