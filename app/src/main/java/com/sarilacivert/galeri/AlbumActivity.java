package com.sarilacivert.galeri;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlbumActivity extends AppCompatActivity {

    public static final String EXTRA_ALBUM_NAME = "album_name";
    public static final String EXTRA_ALBUM_PATH = "album_path";

    private RecyclerView recyclerMedia;

    private TextView txtAlbumTitle;
    private TextView txtAlbumInfo;
    private TextView txtAlbumPath;
    private TextView txtAlbumEmpty;

    private TextView btnGrid;
    private TextView btnAlbumSort;

    private final List<MediaItem> mediaItems =
            new ArrayList<>();

    private MediaAdapter mediaAdapter;
    private GridLayoutManager gridLayoutManager;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private String albumName = "";
    private String albumPath = "";

    private int columnCount = 3;

    private boolean sortNewestFirst = true;

    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_album);

        albumName =
                getIntent().getStringExtra(EXTRA_ALBUM_NAME);

        albumPath =
                getIntent().getStringExtra(EXTRA_ALBUM_PATH);

        if (albumName == null) {
            albumName = "Albüm";
        }

        if (albumPath == null) {
            albumPath = "";
        }

        preferences =
                getSharedPreferences(
                        "gallery_settings",
                        MODE_PRIVATE
                );

        columnCount =
                preferences.getInt(
                        "grid_columns",
                        3
                );

        if (
                columnCount < 3
                        || columnCount > 5
        ) {
            columnCount = 3;
        }

        bindViews();
        setupRecycler();
        setupButtons();

        txtAlbumTitle.setText(albumName);
        txtAlbumPath.setText(albumPath);

        loadAlbumMedia();
    }

    private void bindViews() {

        recyclerMedia =
                findViewById(
                        R.id.recyclerMedia
                );

        txtAlbumTitle =
                findViewById(
                        R.id.txtAlbumTitle
                );

        txtAlbumInfo =
                findViewById(
                        R.id.txtAlbumInfo
                );

        txtAlbumPath =
                findViewById(
                        R.id.txtAlbumPath
                );

        txtAlbumEmpty =
                findViewById(
                        R.id.txtAlbumEmpty
                );

        btnGrid =
                findViewById(
                        R.id.btnGrid
                );

        btnAlbumSort =
                findViewById(
                        R.id.btnAlbumSort
                );
    }

    private void setupRecycler() {

        gridLayoutManager =
                new GridLayoutManager(
                        this,
                        columnCount
                );

        recyclerMedia.setLayoutManager(
                gridLayoutManager
        );

        mediaAdapter =
                new MediaAdapter(
                        this,
                        mediaItems,
                        (item, position) -> {
                            Intent intent = new Intent(
                                    AlbumActivity.this,
                                    ViewerActivity.class
                            );

                            intent.putExtra(
                                    ViewerActivity.EXTRA_URI,
                                    item.getUri().toString()
                            );
                            intent.putExtra(
                                    ViewerActivity.EXTRA_NAME,
                                    item.getName()
                            );
                            intent.putExtra(
                                    ViewerActivity.EXTRA_MIME,
                                    item.getMimeType()
                            );
                            intent.putExtra(
                                    ViewerActivity.EXTRA_SIZE,
                                    item.getSize()
                            );
                            intent.putExtra(
                                    ViewerActivity.EXTRA_DATE,
                                    item.getDateModified()
                            );
                            intent.putExtra(
                                    ViewerActivity.EXTRA_IS_VIDEO,
                                    item.isVideo()
                            );

                            startActivity(intent);
                        }
                );

        mediaAdapter.setColumnCount(
                columnCount
        );

        recyclerMedia.setAdapter(
                mediaAdapter
        );
    }

    private void setupButtons() {

        findViewById(
                R.id.btnBack
        ).setOnClickListener(
                v -> finish()
        );

        btnGrid.setOnClickListener(
                v -> changeGrid()
        );

        btnAlbumSort.setOnClickListener(
                v -> {

                    sortNewestFirst =
                            !sortNewestFirst;

                    sortMedia();

                    mediaAdapter.notifyMediaChanged();

                    if (sortNewestFirst) {

                        Toast.makeText(
                                this,
                                "En yeni önce",
                                Toast.LENGTH_SHORT
                        ).show();

                    } else {

                        Toast.makeText(
                                this,
                                "Dosya adına göre",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    private void changeGrid() {

        if (columnCount == 3) {

            columnCount = 4;

        } else if (columnCount == 4) {

            columnCount = 5;

        } else {

            columnCount = 3;
        }

        preferences.edit()
                .putInt(
                        "grid_columns",
                        columnCount
                )
                .apply();

        gridLayoutManager.setSpanCount(
                columnCount
        );

        mediaAdapter.setColumnCount(
                columnCount
        );

        Toast.makeText(
                this,
                columnCount + " sütun",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void loadAlbumMedia() {

        txtAlbumInfo.setText(
                "Medya yükleniyor..."
        );

        txtAlbumEmpty.setVisibility(
                View.GONE
        );

        executor.execute(() -> {

            List<MediaItem> result =
                    new ArrayList<>();

            loadImages(result);
            loadVideos(result);

            runOnUiThread(() -> {

                mediaItems.clear();

                mediaItems.addAll(
                        result
                );

                sortMedia();

                mediaAdapter.notifyMediaChanged();

                updateInfo();
            });
        });
    }

    private void loadImages(
            List<MediaItem> result
    ) {

        ContentResolver resolver =
                getContentResolver();

        Uri collection =
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            projection =
                    new String[]{
                            MediaStore.Images.Media._ID,
                            MediaStore.Images.Media.DISPLAY_NAME,
                            MediaStore.Images.Media.MIME_TYPE,
                            MediaStore.Images.Media.RELATIVE_PATH,
                            MediaStore.Images.Media.DATE_MODIFIED,
                            MediaStore.Images.Media.SIZE
                    };

        } else {

            projection =
                    new String[]{
                            MediaStore.Images.Media._ID,
                            MediaStore.Images.Media.DISPLAY_NAME,
                            MediaStore.Images.Media.MIME_TYPE,
                            MediaStore.Images.Media.DATA,
                            MediaStore.Images.Media.DATE_MODIFIED,
                            MediaStore.Images.Media.SIZE
                    };
        }

        try (
                Cursor cursor =
                        resolver.query(
                                collection,
                                projection,
                                null,
                                null,
                                null
                        )
        ) {

            if (cursor == null) {
                return;
            }

            int idColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Images.Media._ID
                    );

            int nameColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Images.Media.DISPLAY_NAME
                    );

            int mimeColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Images.Media.MIME_TYPE
                    );

            int dateColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Images.Media.DATE_MODIFIED
                    );

            int sizeColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Images.Media.SIZE
                    );

            int pathColumn;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                pathColumn =
                        cursor.getColumnIndexOrThrow(
                                MediaStore.Images.Media.RELATIVE_PATH
                        );

            } else {

                pathColumn =
                        cursor.getColumnIndexOrThrow(
                                MediaStore.Images.Media.DATA
                        );
            }

            while (cursor.moveToNext()) {

                long id =
                        cursor.getLong(
                                idColumn
                        );

                String name =
                        cursor.getString(
                                nameColumn
                        );

                String mime =
                        cursor.getString(
                                mimeColumn
                        );

                long modified =
                        cursor.getLong(
                                dateColumn
                        );

                long size =
                        cursor.getLong(
                                sizeColumn
                        );

                String mediaPath =
                        cursor.getString(
                                pathColumn
                        );

                if (!belongsToAlbum(mediaPath)) {
                    continue;
                }

                Uri uri =
                        ContentUris.withAppendedId(
                                collection,
                                id
                        );

                result.add(
                        new MediaItem(
                                id,
                                uri,
                                name,
                                albumPath,
                                mime,
                                modified,
                                size,
                                false,
                                0
                        )
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private void loadVideos(
            List<MediaItem> result
    ) {

        ContentResolver resolver =
                getContentResolver();

        Uri collection =
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        String[] projection;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            projection =
                    new String[]{
                            MediaStore.Video.Media._ID,
                            MediaStore.Video.Media.DISPLAY_NAME,
                            MediaStore.Video.Media.MIME_TYPE,
                            MediaStore.Video.Media.RELATIVE_PATH,
                            MediaStore.Video.Media.DATE_MODIFIED,
                            MediaStore.Video.Media.SIZE,
                            MediaStore.Video.Media.DURATION
                    };

        } else {

            projection =
                    new String[]{
                            MediaStore.Video.Media._ID,
                            MediaStore.Video.Media.DISPLAY_NAME,
                            MediaStore.Video.Media.MIME_TYPE,
                            MediaStore.Video.Media.DATA,
                            MediaStore.Video.Media.DATE_MODIFIED,
                            MediaStore.Video.Media.SIZE,
                            MediaStore.Video.Media.DURATION
                    };
        }

        try (
                Cursor cursor =
                        resolver.query(
                                collection,
                                projection,
                                null,
                                null,
                                null
                        )
        ) {

            if (cursor == null) {
                return;
            }

            int idColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Video.Media._ID
                    );

            int nameColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Video.Media.DISPLAY_NAME
                    );

            int mimeColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Video.Media.MIME_TYPE
                    );

            int dateColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Video.Media.DATE_MODIFIED
                    );

            int sizeColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Video.Media.SIZE
                    );

            int durationColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Video.Media.DURATION
                    );

            int pathColumn;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                pathColumn =
                        cursor.getColumnIndexOrThrow(
                                MediaStore.Video.Media.RELATIVE_PATH
                        );

            } else {

                pathColumn =
                        cursor.getColumnIndexOrThrow(
                                MediaStore.Video.Media.DATA
                        );
            }

            while (cursor.moveToNext()) {

                long id =
                        cursor.getLong(
                                idColumn
                        );

                String name =
                        cursor.getString(
                                nameColumn
                        );

                String mime =
                        cursor.getString(
                                mimeColumn
                        );

                long modified =
                        cursor.getLong(
                                dateColumn
                        );

                long size =
                        cursor.getLong(
                                sizeColumn
                        );

                long duration =
                        cursor.getLong(
                                durationColumn
                        );

                String mediaPath =
                        cursor.getString(
                                pathColumn
                        );

                if (!belongsToAlbum(mediaPath)) {
                    continue;
                }

                Uri uri =
                        ContentUris.withAppendedId(
                                collection,
                                id
                        );

                result.add(
                        new MediaItem(
                                id,
                                uri,
                                name,
                                albumPath,
                                mime,
                                modified,
                                size,
                                true,
                                duration
                        )
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private boolean belongsToAlbum(
            String mediaPath
    ) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            if (
                    mediaPath == null
                            || mediaPath.trim().isEmpty()
            ) {

                return albumPath.equals(
                        "Diğer"
                );
            }

            String normalized =
                    mediaPath.replace(
                            "\\",
                            "/"
                    );

            while (
                    normalized.endsWith("/")
            ) {

                normalized =
                        normalized.substring(
                                0,
                                normalized.length() - 1
                        );
            }

            return normalized.equals(
                    albumPath
            );

        } else {

            if (mediaPath == null) {
                return false;
            }

            File file =
                    new File(mediaPath);

            File parent =
                    file.getParentFile();

            return parent != null
                    && parent.getAbsolutePath()
                    .equals(albumPath);
        }
    }

    private void sortMedia() {

        if (sortNewestFirst) {

            Collections.sort(
                    mediaItems,
                    (a, b) ->
                            Long.compare(
                                    b.getDateModified(),
                                    a.getDateModified()
                            )
            );

        } else {

            Collections.sort(
                    mediaItems,
                    Comparator.comparing(
                            MediaItem::getName,
                            String.CASE_INSENSITIVE_ORDER
                    )
            );
        }
    }

    private void updateInfo() {

        int photos = 0;
        int videos = 0;

        for (MediaItem item : mediaItems) {

            if (item.isVideo()) {

                videos++;

            } else {

                photos++;
            }
        }

        txtAlbumInfo.setText(
                mediaItems.size()
                        + " öğe • "
                        + photos
                        + " foto • "
                        + videos
                        + " video"
        );

        if (mediaItems.isEmpty()) {

            txtAlbumEmpty.setVisibility(
                    View.VISIBLE
            );

            recyclerMedia.setVisibility(
                    View.GONE
            );

        } else {

            txtAlbumEmpty.setVisibility(
                    View.GONE
            );

            recyclerMedia.setVisibility(
                    View.VISIBLE
            );
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        if (mediaAdapter != null) {
            loadAlbumMedia();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        executor.shutdownNow();
    }
          }
