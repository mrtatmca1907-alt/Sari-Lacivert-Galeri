from pathlib import Path

root = Path('appsrc')
main = root / 'app/src/main/java/com/sarilacivert/galeri/MainActivity.java'
album = root / 'app/src/main/java/com/sarilacivert/galeri/AlbumActivity.java'
viewer = root / 'app/src/main/java/com/sarilacivert/galeri/ViewerActivity.java'
layout = root / 'app/src/main/res/layout/activity_main.xml'

# --- MainActivity: tarama hissini kaldır, hafif klasör sorgusu kullan ---
s = main.read_text()
s = s.replace('scanMedia();', 'loadFoldersFast();')
start = s.index('    private void scanMedia() {')
end = s.index('    private void applyFilterAndSort() {')
new_method = r'''    private void loadFoldersFast() {

        if (scanning) return;
        scanning = true;
        txtMediaInfo.setText("Klasörler hazırlanıyor...");
        txtEmpty.setVisibility(View.GONE);

        executor.execute(() -> {
            Map<String, Album> albumMap = new LinkedHashMap<>();

            // Fotoğraf ve videoları ayrı koleksiyonlardan sadece hafif klasör metadatasıyla oku.
            collectFolders(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, albumMap);
            collectFolders(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, albumMap);

            List<Album> result = new ArrayList<>(albumMap.values());
            runOnUiThread(() -> {
                allAlbums.clear();
                allAlbums.addAll(result);
                scanning = false;
                applyFilterAndSort();
            });
        });
    }

    private void collectFolders(Uri collection, boolean video, Map<String, Album> albumMap) {
        String[] projection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[]{
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DATE_MODIFIED
            };
        } else {
            projection = new String[]{
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DATE_MODIFIED
            };
        }

        try (Cursor cursor = getContentResolver().query(
                collection,
                projection,
                null,
                null,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null) return;

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int pathCol = cursor.getColumnIndexOrThrow(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                            ? MediaStore.MediaColumns.RELATIVE_PATH
                            : MediaStore.MediaColumns.DATA
            );
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                long modified = cursor.getLong(dateCol);
                String rawPath = cursor.getString(pathCol);
                String folderPath;
                String folderName;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (rawPath == null || rawPath.trim().isEmpty()) {
                        folderPath = "Diğer";
                        folderName = "Diğer";
                    } else {
                        String p = rawPath.replace("\\", "/");
                        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
                        folderPath = p;
                        int slash = p.lastIndexOf('/');
                        folderName = slash >= 0 ? p.substring(slash + 1) : p;
                    }
                } else {
                    File f = new File(rawPath == null ? "" : rawPath);
                    File parent = f.getParentFile();
                    if (parent == null) continue;
                    folderPath = parent.getAbsolutePath();
                    folderName = parent.getName();
                }

                Uri cover = ContentUris.withAppendedId(collection, id);
                Album a = albumMap.get(folderPath);
                if (a == null) {
                    a = new Album(folderName, folderPath, cover, 1,
                            video ? 0 : 1,
                            video ? 1 : 0,
                            video,
                            modified);
                    albumMap.put(folderPath, a);
                } else {
                    a.setItemCount(a.getItemCount() + 1);
                    if (video) a.setVideoCount(a.getVideoCount() + 1);
                    else a.setPhotoCount(a.getPhotoCount() + 1);
                    if (modified > a.getLastModified()) {
                        a.setCoverUri(cover);
                        a.setCoverVideo(video);
                        a.setLastModified(modified);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

'''
s = s[:start] + new_method + s[end:]
main.write_text(s)

# --- AlbumActivity: bütün telefonu okuyup filtrelemek yerine yalnız açılan klasörü sorgula ---
a = album.read_text()
old_query = '''resolver.query(\n                                collection,\n                                projection,\n                                null,\n                                null,\n                                null\n                        )'''
new_query = '''resolver.query(\n                                collection,\n                                projection,\n                                getAlbumSelection(),\n                                getAlbumSelectionArgs(),\n                                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"\n                        )'''
if old_query not in a:
    raise RuntimeError('AlbumActivity query hedefi bulunamadı')
a = a.replace(old_query, new_query)

insert_at = a.index('    private boolean belongsToAlbum(')
helpers = r'''    private String getAlbumSelection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (albumPath.equals("Diğer")) {
                return "(" + MediaStore.MediaColumns.RELATIVE_PATH + " IS NULL OR "
                        + MediaStore.MediaColumns.RELATIVE_PATH + "='')";
            }
            return "(" + MediaStore.MediaColumns.RELATIVE_PATH + "=? OR "
                    + MediaStore.MediaColumns.RELATIVE_PATH + "=?)";
        }
        return MediaStore.MediaColumns.DATA + " LIKE ?";
    }

    private String[] getAlbumSelectionArgs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (albumPath.equals("Diğer")) return null;
            String clean = albumPath.replace("\\", "/");
            while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
            return new String[]{clean, clean + "/"};
        }
        return new String[]{albumPath + File.separator + "%"};
    }

'''
a = a[:insert_at] + helpers + a[insert_at:]
# Dönüşte sadece o klasörü yeniden yüklemek hızlı artık; aynı anda üst üste yükleme olmasın.
a = a.replace('    private boolean sortNewestFirst = true;\n', '    private boolean sortNewestFirst = true;\n    private int loadGeneration = 0;\n')
a = a.replace('    private void loadAlbumMedia() {\n\n        txtAlbumInfo.setText(', '    private void loadAlbumMedia() {\n\n        final int generation = ++loadGeneration;\n        txtAlbumInfo.setText(')
a = a.replace('            runOnUiThread(() -> {\n\n                mediaItems.clear();', '            runOnUiThread(() -> {\n\n                if (generation != loadGeneration || isFinishing() || isDestroyed()) return;\n                mediaItems.clear();')
album.write_text(a)

# --- ViewerActivity: Android sistem silme akışında çift tıklama/yarım kalma olmasın ---
v = viewer.read_text()
v = v.replace('    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher;\n', '    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher;\n    private boolean deletePending = false;\n')
v = v.replace('                    if (result.getResultCode() == RESULT_OK) {\n                        Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show();\n                        setResult(RESULT_OK);\n                        finish();\n                    }', '                    deletePending = false;\n                    if (result.getResultCode() == RESULT_OK) {\n                        if (favorites != null && mediaUri != null) favorites.edit().remove(mediaUri.toString()).apply();\n                        if (playback != null && mediaUri != null) playback.edit().remove(mediaUri.toString()).apply();\n                        Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show();\n                        setResult(RESULT_OK);\n                        finish();\n                    }')
v = v.replace('    private void requestDelete() {\n        if (mediaUri == null) {\n            return;\n        }\n\n        try {', '    private void requestDelete() {\n        if (mediaUri == null || deletePending) return;\n        deletePending = true;\n\n        try {')
v = v.replace('                if (deleted > 0) {', '                if (deleted > 0) {\n                    deletePending = false;')
v = v.replace('                } else {\n                    Toast.makeText(this, "Dosya silinemedi.", Toast.LENGTH_SHORT).show();', '                } else {\n                    deletePending = false;\n                    Toast.makeText(this, "Dosya silinemedi.", Toast.LENGTH_SHORT).show();')
v = v.replace('        } catch (SecurityException e) {\n            Toast.makeText(', '        } catch (SecurityException e) {\n            deletePending = false;\n            Toast.makeText(')
v = v.replace('        } catch (Exception e) {\n            Toast.makeText(', '        } catch (Exception e) {\n            deletePending = false;\n            Toast.makeText(')
viewer.write_text(v)

# Ana ekranda "taranıyor" yazısı olmasın.
x = layout.read_text().replace('android:text="Medya taranıyor..."', 'android:text="Klasörler"')
layout.write_text(x)

print('Hızlı klasör + klasör içi doğrudan sorgu + sağlam silme yaması uygulandı')
