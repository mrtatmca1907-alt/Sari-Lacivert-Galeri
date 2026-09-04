from pathlib import Path
p = Path('coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt')
text = p.read_text(encoding='utf-8')
needle = 'import androidx.compose.foundation.combinedClickable\n'
insert = 'import androidx.compose.foundation.combinedClickable\nimport androidx.compose.foundation.horizontalScroll\n'
if 'import androidx.compose.foundation.horizontalScroll\n' not in text:
    if needle not in text:
        raise SystemExit('combinedClickable import not found')
    text = text.replace(needle, insert, 1)
needle2 = 'import androidx.compose.foundation.layout.Row\n'
insert2 = 'import androidx.compose.foundation.layout.Row\nimport androidx.compose.foundation.rememberScrollState\n'
if 'import androidx.compose.foundation.rememberScrollState\n' not in text:
    if needle2 not in text:
        raise SystemExit('Row import not found')
    text = text.replace(needle2, insert2, 1)
p.write_text(text, encoding='utf-8')
print('scroll imports added')
