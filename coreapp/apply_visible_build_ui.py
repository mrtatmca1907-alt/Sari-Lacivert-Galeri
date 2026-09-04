from pathlib import Path

p = Path('coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt')
text = p.read_text(encoding='utf-8')

old_title = '''        Text(\n            title,\n            style = MaterialTheme.typography.headlineSmall,\n            maxLines = 1,\n            overflow = TextOverflow.Ellipsis,\n            modifier = Modifier.weight(1f)\n        )'''
new_title = '''        Column(modifier = Modifier.weight(1f)) {\n            Text(\n                title,\n                style = MaterialTheme.typography.headlineSmall,\n                maxLines = 1,\n                overflow = TextOverflow.Ellipsis\n            )\n            Text(\n                visibleBuildBadge(),\n                style = MaterialTheme.typography.labelSmall,\n                color = MaterialTheme.colorScheme.primary,\n                maxLines = 1\n            )\n        }'''
if old_title not in text:
    raise SystemExit('top title pattern not found')
text = text.replace(old_title, new_title, 1)

old_sel = '''        Modifier\n            .fillMaxWidth()\n            .background(MaterialTheme.colorScheme.surfaceVariant)\n            .padding(horizontal = 6.dp, vertical = 4.dp),\n        verticalAlignment = Alignment.CenterVertically\n    ) {\n        Text("$selectedCount seçili", maxLines = 1, modifier = Modifier.width(78.dp))'''
new_sel = '''        Modifier\n            .fillMaxWidth()\n            .background(MaterialTheme.colorScheme.surfaceVariant)\n            .horizontalScroll(rememberScrollState())\n            .padding(horizontal = 6.dp, vertical = 4.dp),\n        verticalAlignment = Alignment.CenterVertically\n    ) {\n        Text("$selectedCount seçili", maxLines = 1, softWrap = false, modifier = Modifier.width(94.dp))'''
if old_sel not in text:
    raise SystemExit('selection bar pattern not found')
text = text.replace(old_sel, new_sel, 1)

p.write_text(text, encoding='utf-8')
print('visible build badge and non-wrapping selection bar applied')
