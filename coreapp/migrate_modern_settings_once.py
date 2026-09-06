from pathlib import Path

path = Path('coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt')
text = path.read_text(encoding='utf-8')
old = '''    if (showSettings) {
        SettingsDialog(
'''
new = '''    if (showSettings) {
        ModernSettingsDialog(
'''
if old not in text:
    raise SystemExit('modern settings migration target not found')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
print('ModernSettingsDialog wired directly into GalleryApp.kt')
