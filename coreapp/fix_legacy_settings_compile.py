from pathlib import Path
p=Path('coreapp/src/main/kotlin/com/atmaca/gallery/CompleteSettingsExtras.kt')
s=p.read_text(encoding='utf-8')
old='    onOpenTool: (AtmacaToolPage) -> Unit\n) {'
new='    onOpenTool: (AtmacaToolPage) -> Unit = {}\n) {'
if old not in s:
    raise SystemExit('CompleteSettingsExtras signature pattern not found')
p.write_text(s.replace(old,new,1),encoding='utf-8')
print('legacy settings compile compatibility applied')
