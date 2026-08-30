from pathlib import Path
import re

p = Path("ATMACA-WIFI/app/src/main/java/com/atmaca/files/MainActivity.java")
s = p.read_text(encoding="utf-8")

s = s.replace("import android.content.ClipData;\n", "import android.Manifest;\nimport android.content.ClipData;\n", 1)
s = s.replace("import android.content.Intent;\n", "import android.content.Intent;\nimport android.content.pm.PackageManager;\n", 1)
s = s.replace(
    "import androidx.appcompat.app.AppCompatActivity;\nimport androidx.core.content.FileProvider;\n",
    "import androidx.appcompat.app.AppCompatActivity;\nimport androidx.core.app.ActivityCompat;\nimport androidx.core.content.ContextCompat;\nimport androidx.core.content.FileProvider;\n",
    1,
)

s = s.replace(
    "    private static final int REQ_PHONE_DEST = 4107;\n",
    "    private static final int REQ_PHONE_DEST = 4107;\n"
    "    private static final int REQ_VIDEO_PERMISSION = 4108;\n"
    "    private static final String PREF_CLOUD_TREE = \"cloud_tree_uri\";\n",
    1,
)

s = s.replace('folderBackup.setText("Klasör Yedekle → Bulut");', 'folderBackup.setText("Movies → Bulut Yedekle");', 1)
s = s.replace('cloudDownload.setText("Buluttan Telefona");', 'cloudDownload.setText("Bulutu Aç / Telefona İndir");', 1)

old_pick = """    private void pickBackupSource() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_BACKUP_SOURCE);
    }
"""
new_pick = """    private void pickBackupSource() {
        startMoviesBackup();
    }

    private void startMoviesBackup() {
        if (!hasVideoReadPermission()) {
            String permission = Build.VERSION.SDK_INT >= 33
                    ? Manifest.permission.READ_MEDIA_VIDEO
                    : Manifest.permission.READ_EXTERNAL_STORAGE;
            ActivityCompat.requestPermissions(this, new String[]{permission}, REQ_VIDEO_PERMISSION);
            return;
        }
        String saved = prefs.getString(PREF_CLOUD_TREE, "");
        if (saved == null || saved.trim().isEmpty()) {
            status.setText("İlk kullanım: Bulut hedefini bir kez seç");
            toast("Bulut hedefini bir kez seç; sonra Movies direkt yedeklenecek");
            pickDestinationTree(REQ_BACKUP_CLOUD_DEST);
            return;
        }
        backupMoviesToCloud(Uri.parse(saved));
    }

    private boolean hasVideoReadPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }
"""
if old_pick not in s:
    raise SystemExit("pickBackupSource block not found")
s = s.replace(old_pick, new_pick, 1)

old_cloud_picker = """    private void pickCloudFiles() {
        Intent i = new Intent(FilePickerPolicy.action());
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(FilePickerPolicy.mimeType());
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i, "Buluttan dosya seç"), REQ_CLOUD_FILES);
    }
"""
new_cloud_picker = """    private void pickCloudFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        String saved = prefs.getString(PREF_CLOUD_TREE, "");
        if (Build.VERSION.SDK_INT >= 26 && saved != null && !saved.trim().isEmpty()) {
            try { i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(saved)); } catch (Exception ignored) {}
        }
        startActivityForResult(Intent.createChooser(i, "Bulutu aç / telefona indir"), REQ_CLOUD_FILES);
    }
"""
if old_cloud_picker not in s:
    raise SystemExit("pickCloudFiles block not found")
s = s.replace(old_cloud_picker, new_cloud_picker, 1)

old_targets = """        if (requestCode == REQ_CARD_FOLDER || requestCode == REQ_CLOUD_FOLDER) {
            Uri tree = data.getData();
            if (tree == null) return;
            persistTreePermission(data, tree);
            copySelectedFilesToTree(tree, requestCode == REQ_CARD_FOLDER ? "Kart" : "Bulut");
            return;
        }
"""
new_targets = """        if (requestCode == REQ_CARD_FOLDER || requestCode == REQ_CLOUD_FOLDER) {
            Uri tree = data.getData();
            if (tree == null) return;
            persistTreePermission(data, tree);
            if (requestCode == REQ_CLOUD_FOLDER) prefs.edit().putString(PREF_CLOUD_TREE, tree.toString()).apply();
            copySelectedFilesToTree(tree, requestCode == REQ_CARD_FOLDER ? "Kart" : "Bulut");
            return;
        }
"""
if old_targets not in s:
    raise SystemExit("card/cloud target block not found")
s = s.replace(old_targets, new_targets, 1)

old_dest = """        if (requestCode == REQ_BACKUP_CLOUD_DEST) {
            Uri dest = data.getData();
            if (dest == null || backupSourceTree == null) return;
            persistTreePermission(data, dest);
            backupFolderToCloud(backupSourceTree, dest);
            return;
        }
"""
new_dest = """        if (requestCode == REQ_BACKUP_CLOUD_DEST) {
            Uri dest = data.getData();
            if (dest == null) return;
            persistTreePermission(data, dest);
            prefs.edit().putString(PREF_CLOUD_TREE, dest.toString()).apply();
            backupMoviesToCloud(dest);
            return;
        }
"""
if old_dest not in s:
    raise SystemExit("backup destination block not found")
s = s.replace(old_dest, new_dest, 1)

marker = """    private void collectUris(Intent data, ArrayList<Uri> target) {
"""
permission_result = """    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_VIDEO_PERMISSION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startMoviesBackup();
        } else {
            status.setText("Movies videolarını okuyabilmek için video izni gerekli");
            toast("Video izni verilmedi");
        }
    }

"""
if marker not in s:
    raise SystemExit("collectUris marker not found")
s = s.replace(marker, permission_result + marker, 1)

pattern = r"    private void backupFolderToCloud\(Uri source, Uri dest\) \{\n.*?\n    \}\n\n(?=    private void copyCloudFilesToPhone)"
replacement = """    private void backupMoviesToCloud(Uri dest) {
        status.setText("Movies taranıyor...");
        io.execute(() -> {
            try {
                MoviesCloudBackup.Result result = MoviesCloudBackup.backup(this, dest, (done, total, currentName) ->
                        runOnUiThread(() -> status.setText(BackupProgress.text(done, total, currentName))));
                runOnUiThread(() -> {
                    status.setText("Movies → Bulut tamamlandı: " + result.processed + " / " + result.total
                            + " • yeni " + result.copied + " • zaten vardı " + result.skipped);
                    toast(result.copied + " yeni video Bulut'a yedeklendi");
                });
            } catch (SecurityException e) {
                prefs.edit().remove(PREF_CLOUD_TREE).apply();
                runOnUiThread(() -> {
                    status.setText("Bulut hedef izni yenilenmeli");
                    toast("Bulut hedefini tekrar seç");
                    pickDestinationTree(REQ_BACKUP_CLOUD_DEST);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Movies yedekleme hatası: " + safe(e.getMessage()));
                    toast("Movies yedekleme tamamlanamadı");
                });
            }
        });
    }

"""
s2, n = re.subn(pattern, replacement, s, count=1, flags=re.S)
if n != 1:
    raise SystemExit(f"backup method replacement failed: {n}")

p.write_text(s2, encoding="utf-8")
