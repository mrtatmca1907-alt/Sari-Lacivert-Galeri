package com.sarilacivert.galeri;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerAlbums;
    private TextView txtMediaInfo;
    private TextView txtEmpty;

    private TextView btnSort;
    private TextView btnSettings;

    private TextView navAlbums;
    private TextView navFavorites;
    private TextView navDuplicates;

    private AlbumAdapter albumAdapter;

    private final List<Album> allAlbums = new ArrayList<>();
    private final List<Album> shownAlbums = new ArrayList<>();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private boolean scanning = false;
    private boolean sortByCount = false;

    private String searchText = "";

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        bindViews();
        setupRecycler();
        setupPermissions();
        setupButtons();

        checkPermissionAndScan();
    }

    private void bindViews() {

        recyclerAlbums = findViewById(R.id.recyclerAlbums);

        txtMediaInfo = findViewById(R.id.txtMediaInfo);
        txtEmpty = findViewById(R.id.txtEmpty);

        btnSort = findViewById(R.id.btnSort);
        btnSettings = findViewById(R.id.btnSettings);

        navAlbums = findViewById(R.id.navAlbums);
        navFavorites = findViewById(R.id.navFavorites);
        navDuplicates = findViewById(R.id.navDuplicates);
    }

    private void setupRecycler() {

        recyclerAlbums.setLayoutManager(
                new GridLayoutManager(this, 2)
        );

        albumAdapter = new AlbumAdapter(
                this,
                shownAlbums,
                album -> {

                    Toast.makeText(
                            this,
                            album.getName()
                                    + "\n"
                                    + album.getPath(),
                            Toast.LENGTH_SHORT
                    ).show();

                    /*
                     * Bir sonraki aşamada burada
                     * AlbumActivity açılacak.
                     */
                }
        );

        recyclerAlbums.setAdapter(albumAdapter);
    }

    private void setupPermissions() {

        permissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestMultiplePermissions(),
                        result -> {

                            boolean granted = false;

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                                Boolean imagePermission =
                                        result.get(Manifest.permission.READ_MEDIA_IMAGES);

                                Boolean videoPermission =
                                        result.get(Manifest.permission.READ_MEDIA_VIDEO);

                                granted =
                                        Boolean.TRUE.equals(imagePermission)
                                                || Boolean.TRUE.equals(videoPermission);

                            } else {

                                Boolean storagePermission =
                                        result.get(Manifest.permission.READ_EXTERNAL_STORAGE);

                                granted =
                                        Boolean.TRUE.equals(storagePermission);
                            }

                            if (granted) {

                                scanMedia();

                            } else {

                                txtMediaInfo.setText(
                                        "Medya izni verilmedi."
                                );

                                txtEmpty.setVisibility(View.VISIBLE);

                                Toast.makeText(
                                        this,
                                        "Fotoğraf ve videoları göstermek için medya izni gerekiyor.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                );
    }

    private void checkPermissionAndScan() {

        if (hasMediaPermission()) {

            scanMedia();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            permissionLauncher.launch(
                    new String[]{
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                    }
            );

        } else {

            permissionLauncher.launch(
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    }
            );
        }
    }

    private boolean hasMediaPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            boolean images =
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_MEDIA_IMAGES
                    ) == PackageManager.PERMISSION_GRANTED;

            boolean videos =
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_MEDIA_VIDEO
                    ) == PackageManager.PERMISSION_GRANTED;

            return images || videos;

        } else {

            return ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void setupButtons() {

        findViewById(R.id.btnSearch).setOnClickListener(
                v -> showSearchDialog()
        );

        btnSort.setOnClickListener(
                v -> {

                    sortByCount = !sortByCount;

                    applyFilterAndSort();

                    if (sortByCount) {

                        Toast.makeText(
                                this,
                                "Albüm sayısına göre sıralandı",
                                Toast.LENGTH_SHORT
                        ).show();

                    } else {

                        Toast.makeText(
                                this,
                                "Albüm adına göre sıralandı",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );

        findViewById(R.id.btnCamera).setOnClickListener(
                v -> Toast.makeText(
                        this,
                        "Kamera bölümü sonraki aşamada bağlanacak.",
                        Toast.LENGTH_SHORT
                ).show()
        );

        btnSettings.setOnClickListener(
                v -> Toast.makeText(
                        this,
                        "Ayarlar bölümü hazırlanıyor.",
                        Toast.LENGTH_SHORT
                ).show()
        );

        navAlbums.setOnClickListener(
                v -> {

                    searchText = "";
                    applyFilterAndSort();

                    navAlbums.setTextColor(
                            ContextCompat.getColor(
                                    this,
                                    R.color.yellow_500
                            )
                    );

                    navFavorites.setTextColor(
                            ContextCompat.getColor(
                                    this,
                                    R.color.text_secondary
                            )
                    );

                    navDuplicates.setTextColor(
                            ContextCompat.getColor(
                                    this,
                                    R.color.text_secondary
                            )
                    );
                }
        );

        navFavorites.setOnClickListener(
                v -> Toast.makeText(
                        this,
                        "Favoriler sonraki aşamada bağlanacak.",
                        Toast.LENGTH_SHORT
                ).show()
        );

        navDuplicates.setOnClickListener(
                v -> Toast.makeText(
                        this,
                        "Çift / benzer medya tarayıcı sonraki aşamada bağlanacak.",
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    private void showSearchDialog() {

        EditText input = new EditText(this);

        input.setHint("Albüm veya klasör ara");

        input.setSingleLine(true);

        input.setText(searchText);

        int padding = (int) (
                20 * getResources()
                        .getDisplayMetrics()
                        .density
        );

        input.setPadding(
                padding,
                padding,
                padding,
                padding
        );

        new AlertDialog.Builder(this)
                .setTitle("Albüm Ara")
                .setView(input)

                .setPositiveButton(
                        "Ara",
                        (dialog, which) -> {

                            searchText =
                                    input.getText()
                                            .toString()
                                            .trim();

                            applyFilterAndSort();
                        }
                )

                .setNeutralButton(
                        "Temizle",
                        (dialog, which) -> {

                            searchText = "";

                            applyFilterAndSort();
                        }
                )

                .setNegativeButton(
                        "İptal",
                        null
                )

                .show();
    }

    private void scanMedia() {

        if (scanning) {
            return;
        }

        scanning = true;

        txtMediaInfo.setText(
                "Telefon taranıyor..."
        );

        txtEmpty.setVisibility(View.GONE);

        executor.execute(() -> {

            Map<String, Album> albumMap =
                    new LinkedHashMap<>();

            ContentResolver resolver =
                    getContentResolver();

            Uri filesUri =
                    MediaStore.Files.getContentUri(
                            "external"
                    );

            String[] projection;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                projection = new String[]{
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.MEDIA_TYPE,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        MediaStore.MediaColumns.DATE_MODIFIED
                };

            } else {

                projection = new String[]{
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.MEDIA_TYPE,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.DATA,
                        MediaStore.MediaColumns.DATE_MODIFIED
                };
            }

            String selection =
                    MediaStore.Files.FileColumns.MEDIA_TYPE
                            + "=? OR "
                            + MediaStore.Files.FileColumns.MEDIA_TYPE
                            + "=?";

            String[] selectionArgs = new String[]{
                    String.valueOf(
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    ),

                    String.valueOf(
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    )
            };

            String sortOrder =
                    MediaStore.MediaColumns.DATE_MODIFIED
                            + " DESC";

            try (
                    Cursor cursor =
                            resolver.query(
                                    filesUri,
                                    projection,
                                    selection,
                                    selectionArgs,
                                    sortOrder
                            )
            ) {

                if (cursor != null) {

                    int idColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Files.FileColumns._ID
                            );

                    int typeColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Files.FileColumns.MEDIA_TYPE
                            );

                    int dateColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.MediaColumns.DATE_MODIFIED
                            );

                    int pathColumn;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                        pathColumn =
                                cursor.getColumnIndexOrThrow(
                                        MediaStore.MediaColumns.RELATIVE_PATH
                                );

                    } else {

                        pathColumn =
                                cursor.getColumnIndexOrThrow(
                                        MediaStore.MediaColumns.DATA
                                );
                    }

                    while (cursor.moveToNext()) {

                        long id =
                                cursor.getLong(idColumn);

                        int mediaType =
                                cursor.getInt(typeColumn);

                        long modified =
                                cursor.getLong(dateColumn);

                        String folderPath;
                        String folderName;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                            String relativePath =
                                    cursor.getString(pathColumn);

                            if (
                                    relativePath == null
                                            || relativePath.trim().isEmpty()
                            ) {

                                folderPath =
                                        "Diğer";

                                folderName =
                                        "Diğer";

                            } else {

                                relativePath =
                                        relativePath.replace(
                                                "\\",
                                                "/"
                                        );

                                while (
                                        relativePath.endsWith("/")
                                ) {

                                    relativePath =
                                            relativePath.substring(
                                                    0,
                                                    relativePath.length() - 1
                                            );
                                }

                                folderPath =
                                        relativePath;

                                int slash =
                                        relativePath.lastIndexOf("/");

                                if (slash >= 0) {

                                    folderName =
                                            relativePath.substring(
                                                    slash + 1
                                            );

                                } else {

                                    folderName =
                                            relativePath;
                                }
                            }

                        } else {

                            String fullPath =
                                    cursor.getString(pathColumn);

                            File file =
                                    new File(fullPath);

                            File parent =
                                    file.getParentFile();

                            if (parent != null) {

                                folderPath =
                                        parent.getAbsolutePath();

                                folderName =
                                        parent.getName();

                            } else {

                                folderPath =
                                        "Diğer";

                                folderName =
                                        "Diğer";
                            }
                        }

                        Uri contentUri =
                                ContentUris.withAppendedId(
                                        filesUri,
                                        id
                                );

                        boolean isVideo =
                                mediaType
                                        == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

                        Album album =
                                albumMap.get(
                                        folderPath
                                );

                        if (album == null) {

                            album =
                                    new Album(
                                            folderName,
                                            folderPath,
                                            contentUri,
                                            1,
                                            isVideo ? 0 : 1,
                                            isVideo ? 1 : 0,
                                            isVideo,
                                            modified
                                    );

                            albumMap.put(
                                    folderPath,
                                    album
                            );

                        } else {

                            album.setItemCount(
                                    album.getItemCount() + 1
                            );

                            if (isVideo) {

                                album.setVideoCount(
                                        album.getVideoCount() + 1
                                );

                            } else {

                                album.setPhotoCount(
                                        album.getPhotoCount() + 1
                                );
                            }

                            if (
                                    modified
                                            > album.getLastModified()
                            ) {

                                album.setCoverUri(
                                        contentUri
                                );

                                album.setCoverVideo(
                                        isVideo
                                );

                                album.setLastModified(
                                        modified
                                );
                            }
                        }
                    }
                }

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() -> {

                    scanning = false;

                    txtMediaInfo.setText(
                            "Tarama sırasında hata oluştu."
                    );

                    Toast.makeText(
                            this,
                            "Galeri tarama hatası: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });

                return;
            }

            List<Album> result =
                    new ArrayList<>(
                            albumMap.values()
                    );

            runOnUiThread(() -> {

                allAlbums.clear();

                allAlbums.addAll(result);

                scanning = false;

                applyFilterAndSort();
            });
        });
    }

    private void applyFilterAndSort() {

        shownAlbums.clear();

        String query =
                searchText
                        .toLowerCase(
                                Locale.getDefault()
                        );

        for (Album album : allAlbums) {

            String name =
                    album.getName()
                            .toLowerCase(
                                    Locale.getDefault()
                            );

            String path =
                    album.getPath()
                            .toLowerCase(
                                    Locale.getDefault()
                            );

            if (
                    query.isEmpty()
                            || name.contains(query)
                            || path.contains(query)
            ) {

                shownAlbums.add(album);
            }
        }

        if (sortByCount) {

            Collections.sort(
                    shownAlbums,
                    (a, b) ->
                            Integer.compare(
                                    b.getItemCount(),
                                    a.getItemCount()
                            )
            );

        } else {

            Collections.sort(
                    shownAlbums,
                    Comparator.comparing(
                            Album::getName,
                            String.CASE_INSENSITIVE_ORDER
                    )
            );
        }

        albumAdapter.notifyAlbumsChanged();

        int photoCount = 0;
        int videoCount = 0;

        for (Album album : shownAlbums) {

            photoCount +=
                    album.getPhotoCount();

            videoCount +=
                    album.getVideoCount();
        }

        txtMediaInfo.setText(
                shownAlbums.size()
                        + " albüm • "
                        + photoCount
                        + " foto • "
                        + videoCount
                        + " video"
        );

        if (shownAlbums.isEmpty()) {

            txtEmpty.setVisibility(
                    View.VISIBLE
            );

            recyclerAlbums.setVisibility(
                    View.GONE
            );

        } else {

            txtEmpty.setVisibility(
                    View.GONE
            );

            recyclerAlbums.setVisibility(
                    View.VISIBLE
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (
                recyclerAlbums != null
                        && hasMediaPermission()
                        && !scanning
                        && !allAlbums.isEmpty()
        ) {

            scanMedia();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        executor.shutdownNow();
    }
}
