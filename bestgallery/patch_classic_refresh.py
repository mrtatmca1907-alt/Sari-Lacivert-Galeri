from pathlib import Path
import re

# Bu yama MEDYA TARAMA KODUNA DOKUNMAZ.
# JustinBeeper/BestGallery eski çalışan motoru aynen kalır;
# yalnızca marka/tema/reklam bağımlılıkları/ikon/sürüm bilgisi tazelenir.

# -----------------------------------------------------------------------------
# Uygulama kimliği, sürüm ve reklam/telemetri bağımlılıkları
# -----------------------------------------------------------------------------
p = Path('app/build.gradle')
s = p.read_text()
s = s.replace('applicationId "com.eagle.gallery.photos.videos.album.hd.gallery.editor"', 'applicationId "com.sarilacivert.bestgallery"')
s = re.sub(r'versionCode\s+\d+', 'versionCode 20010150', s, count=1)
s = re.sub(r'versionName\s+"[^"]+"', 'versionName "10.1-klasik-tazelenmis"', s, count=1)

for dep in [
    "implementation 'com.tencent.bugly:crashreport:2.3.1'",
    "implementation 'com.google.firebase:firebase-core:16.0.0'",
    "implementation 'com.google.firebase:firebase-config:16.0.0'",
    'implementation "com.google.android.gms:play-services-ads:15.0.1"',
    "implementation 'com.google.vr:sdk-panowidget:1.180.0'",
    "implementation 'com.google.vr:sdk-videowidget:1.180.0'",
]:
    s = s.replace(dep, '')

if "implementation fileTree(dir: 'libs', include: ['*.aar'])" not in s:
    s = s.replace('dependencies {', "dependencies {\n    implementation fileTree(dir: 'libs', include: ['*.aar'])\n    implementation 'com.google.protobuf.nano:protobuf-javanano:3.1.0'")
p.write_text(s)

# -----------------------------------------------------------------------------
# Uygulama adı
# -----------------------------------------------------------------------------
strings = Path('app/src/main/res/values/strings.xml')
t = strings.read_text()
t = re.sub(r'<string name="app_name">.*?</string>', '<string name="app_name">Sarı Lacivert Galeri</string>', t, count=1)
t = re.sub(r'<string name="app_launcher_name">.*?</string>', '<string name="app_launcher_name">Sarı Lacivert Galeri</string>', t, count=1)
strings.write_text(t)

# -----------------------------------------------------------------------------
# Sarı-lacivert tema. Sadece kaynak renkleri ve ilk kurulum varsayılanları değişir.
# -----------------------------------------------------------------------------
colors = Path('commons/src/main/res/values/colors.xml')
c = colors.read_text()
for old, new in {
    '<color name="color_primary">#ff5c1166</color>': '<color name="color_primary">#0B1D3A</color>',
    '<color name="color_primary_dark">#ff5c1166</color>': '<color name="color_primary_dark">#061326</color>',
    '<color name="color_accent">@color/color_primary</color>': '<color name="color_accent">#F4C430</color>',
    '<color name="default_text_color">@color/theme_dark_text_color</color>': '<color name="default_text_color">#FFF7D6</color>',
    '<color name="default_background_color">@color/theme_dark_background_color</color>': '<color name="default_background_color">#081426</color>',
    '<color name="default_background_color2">#202340</color>': '<color name="default_background_color2">#0D1B2E</color>',
}.items():
    c = c.replace(old, new)
colors.write_text(c)

app_colors = Path('app/src/main/res/values/colors.xml')
ac = app_colors.read_text()
ac = ac.replace('<color name="actionbar_menu_icon">#454545</color>', '<color name="actionbar_menu_icon">#F4C430</color>')
app_colors.write_text(ac)

# -----------------------------------------------------------------------------
# Firebase başlatmasını kaldır; eski galeri davranışı aynen kalsın.
# Tema yalnızca ilk çalıştırmada sarı-lacivert varsayılana çekilir.
# -----------------------------------------------------------------------------
app = Path('app/src/main/kotlin/com/eagle/gallery/pro/App.kt')
a = app.read_text()
a = a.replace('import com.google.firebase.FirebaseApp\n', '')
if 'import com.eagle.commons.helpers.BaseConfig' not in a:
    a = a.replace('import com.eagle.commons.extensions.checkUseEnglish\n', 'import com.eagle.commons.extensions.checkUseEnglish\nimport com.eagle.commons.helpers.BaseConfig\n')
a = re.sub(r'\n\s*Thread \{\s*FirebaseApp\.initializeApp\(this\)\s*\}\.start\(\)\s*\n', '\n', a, flags=re.S)
needle = '        mContext = applicationContext\n'
branding = '''        mContext = applicationContext\n\n        val brandingPrefs = getSharedPreferences("sari_lacivert_klasik", Context.MODE_PRIVATE)\n        if (!brandingPrefs.getBoolean("tema_kuruldu", false)) {\n            val config = BaseConfig.newInstance(this)\n            val primary = resources.getColor(R.color.color_primary)\n            val background = resources.getColor(R.color.default_background_color2)\n            val text = resources.getColor(R.color.default_text_color)\n            config.primaryColor = primary\n            config.backgroundColor = background\n            config.textColor = text\n            config.customPrimaryColor = primary\n            config.customBackgroundColor = background\n            config.customTextColor = text\n            config.appIconColor = primary\n            brandingPrefs.edit().putBoolean("tema_kuruldu", true).apply()\n        }\n'''
if needle not in a:
    raise RuntimeError('App.kt tema hedefi bulunamadı')
a = a.replace(needle, branding, 1)
app.write_text(a)

# -----------------------------------------------------------------------------
# Basit, güvenli sarı-lacivert galeri ikonu
# -----------------------------------------------------------------------------
icon = Path('app/src/main/res/drawable/ic_sari_lacivert_gallery.xml')
icon.write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#081426" android:pathData="M0,0H108V108H0Z"/>
    <path android:fillColor="#F4C430" android:pathData="M14,18H94V90H14Z"/>
    <path android:fillColor="#081426" android:pathData="M21,25H87V83H21Z"/>
    <path android:fillColor="#F4C430" android:pathData="M68,37a8,8 0,1 0,16,0a8,8 0,1 0,-16,0"/>
    <path android:fillColor="#F4C430" android:pathData="M24,77L43,55L56,68L66,58L84,77Z"/>
</vector>\n''')

manifest = Path('app/src/main/AndroidManifest.xml')
m = manifest.read_text().replace('@mipmap/ic_launcher', '@drawable/ic_sari_lacivert_gallery')
m = re.sub(r'\s*<meta-data\s+android:name="com\.google\.android\.gms\.version"\s+android:value="@integer/google_play_services_version"\s*/>', '', m, flags=re.S)
m = re.sub(r'\s*<activity\s+android:name="com\.google\.android\.gms\.ads\.AdActivity".*?/>', '', m, flags=re.S)
manifest.write_text(m)

# -----------------------------------------------------------------------------
# Eski bağımlılıkların günümüzde çözülebilmesi için yalnızca repo kaynakları.
# Galeri çalışma mantığına etkisi yoktur.
# -----------------------------------------------------------------------------
root = Path('build.gradle')
r = root.read_text()
marker = 'maven { url "https://jitpack.io" }'
if 'artifactory.appodeal.com/appodeal-public' not in r:
    r = r.replace(marker, marker + '\n        maven { url "https://artifactory.appodeal.com/appodeal-public" }\n        maven { url "https://maven.aliyun.com/repository/jcenter" }')
root.write_text(r)

print('KLASIK REFRESH: eski BestGallery motoru aynen korundu; sadece görünüm/marka/reklamsızlık tazelendi')
