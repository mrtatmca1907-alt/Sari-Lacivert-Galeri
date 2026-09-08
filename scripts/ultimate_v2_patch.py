from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# This patch is intentionally conservative: preserve the fast v1 loading path and
# only change UI/gesture/trash/video behavior requested for v2.

def replace(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'pattern not found in {path}: {old[:120]!r}')
    p.write_text(text.replace(old, new), encoding='utf-8')

# App label.
strings = ROOT / 'app/src/main/res/values/strings.xml'
if strings.exists():
    s = strings.read_text(encoding='utf-8')
    s = s.replace('ATMACA Galeri', 'Galeri').replace('ATMACA-Galeri', 'Galeri')
    strings.write_text(s, encoding='utf-8')

print('Ultimate v2 base patch prepared')
