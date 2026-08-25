package com.sarilacivert.galeri;

import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.util.Collections;

public class ViewerActivity extends AppCompatActivity {

    public static final String EXTRA_URI = "media_uri";
    public static final String EXTRA_NAME = "media_name";
    public static final String EXTRA_MIME = "media_mime";
    public static final String EXTRA_SIZE = "media_size";
    public static final String EXTRA_DATE = "media_date";

    private android.widget.ImageView imgViewer;

    private LinearLayout topBar;
    private LinearLayout bottomBar;

    private TextView txtViewerTitle;
    private TextView btnFavorite;

    private Uri mediaUri;

    private String mediaName = "";
    private String mimeType = "";
    private long mediaSize = 0;
    private long mediaDate = 0;

    private float rotation = 0f;

    private boolean barsVisible = true;
    private boolean favorite = false;

    private SharedPreferences favorites;

    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_viewer);

        bindViews();
        readIntent();
        setupDeleteLauncher();
        setupViewer();
        setupButtons();
        loadFavoriteState();
    }

    private void bindViews() {

        imgViewer = findViewById(R.id.imgViewer);

        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);

        txtViewerTitle = findViewById(R.id.txtViewerTitle);
        btnFavorite = findViewById(R.id.btnFavorite);
    }

    private void readIntent() {

        String uriText =
                getIntent().getStringExtra(EXTRA_URI);

        mediaName =
                getIntent().getStringExtra(EXTRA_NAME);

        mimeType =
                getIntent().getStringExtra(EXTRA_MIME);

        mediaSize =
                getIntent().getLongExtra(
                        EXTRA_SIZE,
                        0
                );

        mediaDate =
                getIntent().getLongExtra(
                        EXTRA_DATE,
                        0
                );

        if (mediaName == null) {
            mediaName = "Fotoğraf";
        }

        if (mimeType == null) {
            mimeType = "";
        }

        txtViewerTitle.setText(mediaName);

        if (uriText == null || uriText.isEmpty()) {

            Toast.makeText(
                    this,
                    "Fotoğraf açılamadı.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        mediaUri = Uri.parse(uriText);
    }

    private void setupViewer() {

        if (mediaUri == null) {
            return;
        }

        Glide.with(this)
                .load(mediaUri)
                .fitCenter()
                .into(imgViewer);

        imgViewer.setOnClickListener(
                v -> toggleBars()
        );
    }

    private void setupButtons() {

        findViewById(R.id.btnViewerBack)
                .setOnClickListener(
                        v -> finish()
                );

        findViewById(R.id.btnShare)
                .setOnClickListener(
                        v -> shareMedia()
                );

        findViewById(R.id.btnRotate)
                .setOnClickListener(
                        v -> rotateImage()
                );

        findViewById(R.id.btnInfo)
                .setOnClickListener(
                        v -> showInfo()
                );

        findViewById(R.id.btnDelete)
                .setOnClickListener(
                        v -> confirmDelete()
                );

        btnFavorite.setOnClickListener(
                v -> toggleFavorite()
        );
    }

    private void toggleBars() {

        barsVisible = !barsVisible;

        if (barsVisible) {

            topBar.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);

        } else {

            topBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
        }
    }

    private void rotateImage() {

        rotation += 90f;

        if (rotation >= 360f) {
            rotation = 0f;
        }

        imgViewer.animate()
                .rotation(rotation)
                .setDuration(180)
                .start();
    }

    private void shareMedia() {

        if (mediaUri == null) {
            return;
        }

        Intent shareIntent =
                new Intent(Intent.ACTION_SEND);

        shareIntent.setType(
                mimeType.isEmpty()
                        ? "image/*"
                        : mimeType
        );

        shareIntent.putExtra(
                Intent.EXTRA_STREAM,
                mediaUri
        );

        shareIntent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );

        startActivity(
                Intent.createChooser(
                        shareIntent,
                        "Paylaş"
                )
        );
    }

    private void loadFavoriteState() {

        favorites =
                getSharedPreferences(
                        "favorites",
                        MODE_PRIVATE
                );

        if (mediaUri == null) {
            return;
        }

        favorite =
                favorites.getBoolean(
                        mediaUri.toString(),
                        false
                );

        updateFavoriteButton();
    }

    private void toggleFavorite() {

        if (mediaUri == null) {
            return;
        }

        favorite = !favorite;

        favorites.edit()
                .putBoolean(
                        mediaUri.toString(),
                        favorite
                )
                .apply();

        updateFavoriteButton();

        Toast.makeText(
                this,
                favorite
                        ? "Favorilere eklendi"
                        : "Favorilerden çıkarıldı",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void updateFavoriteButton() {

        btnFavorite.setText(
                favorite
                        ? "★"
                        : "☆"
        );
    }

    private void showInfo() {

        String sizeText =
                formatFileSize(mediaSize);

        String info =
                "Dosya: "
                        + mediaName
                        + "\n\nTür: "
                        + (
                        mimeType.isEmpty()
                                ? "Bilinmiyor"
                                : mimeType
                )
                        + "\n\nBoyut: "
                        + sizeText
                        + "\n\nURI:\n"
                        + (
                        mediaUri == null
                                ? "-"
                                : mediaUri.toString()
                );

        new AlertDialog.Builder(this)
                .setTitle("Medya Bilgisi")
                .setMessage(info)
                .setPositiveButton(
                        "Tamam",
                        null
                )
                .show();
    }

    private String formatFileSize(long bytes) {

        if (bytes <= 0) {
            return "Bilinmiyor";
        }

        double kb =
                bytes / 1024.0;

        if (kb < 1024) {

            return String.format(
                    "%.1f KB",
                    kb
            );
        }

        double mb =
                kb / 1024.0;

        if (mb < 1024) {

            return String.format(
                    "%.1f MB",
                    mb
            );
        }

        double gb =
                mb / 1024.0;

        return String.format(
                "%.2f GB",
                gb
        );
    }

    private void confirmDelete() {

        if (mediaUri == null) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Sil")
                .setMessage(
                        "Bu fotoğrafı silmek istiyor musun?"
                )
                .setPositiveButton(
                        "Sil",
                        (dialog, which) ->
                                requestDelete()
                )
                .setNegativeButton(
                        "İptal",
                        null
                )
                .show();
    }

    private void setupDeleteLauncher() {

        deleteLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartIntentSenderForResult(),
                        result -> {

                            if (
                                    result.getResultCode()
                                            == RESULT_OK
                            ) {

                                Toast.makeText(
                                        this,
                                        "Silindi",
                                        Toast.LENGTH_SHORT
                                ).show();

                                finish();
                            }
                        }
                );
    }

    private void requestDelete() {

        if (mediaUri == null) {
            return;
        }

        try {

            if (
                    Build.VERSION.SDK_INT
                            >= Build.VERSION_CODES.R
            ) {

                PendingIntent pendingIntent =
                        MediaStore.createDeleteRequest(
                                getContentResolver(),
                                Collections.singletonList(
                                        mediaUri
                                )
                        );

                IntentSenderRequest request =
                        new IntentSenderRequest.Builder(
                                pendingIntent.getIntentSender()
                        ).build();

                deleteLauncher.launch(request);

            } else {

                int deleted =
                        getContentResolver()
                                .delete(
                                        mediaUri,
                                        null,
                                        null
                                );

                if (deleted > 0) {

                    Toast.makeText(
                            this,
                            "Silindi",
                            Toast.LENGTH_SHORT
                    ).show();

                    finish();

                } else {

                    Toast.makeText(
                            this,
                            "Dosya silinemedi.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

        } catch (RecoverableSecurityException e) {

            if (
                    Build.VERSION.SDK_INT
                            >= Build.VERSION_CODES.Q
            ) {

                IntentSenderRequest request =
                        new IntentSenderRequest.Builder(
                                e.getUserAction()
                                        .getActionIntent()
                                        .getIntentSender()
                        ).build();

                deleteLauncher.launch(request);
            }

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Silme hatası: "
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}
