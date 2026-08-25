package com.sarilacivert.galeri;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class ViewerActivity extends AppCompatActivity {

    public static final String EXTRA_URI = "media_uri";
    public static final String EXTRA_NAME = "media_name";
    public static final String EXTRA_MIME = "media_mime";
    public static final String EXTRA_SIZE = "media_size";
    public static final String EXTRA_DATE = "media_date";
    public static final String EXTRA_IS_VIDEO = "media_is_video";

    private ZoomImageView imgViewer;
    private VideoView videoViewer;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private TextView txtViewerTitle;
    private TextView btnFavorite;
    private TextView btnRotate;

    private Uri mediaUri;
    private String mediaName = "";
    private String mimeType = "";
    private long mediaSize = 0;
    private long mediaDate = 0;
    private boolean isVideo = false;

    private float rotation = 0f;
    private boolean barsVisible = true;
    private boolean favorite = false;
    private boolean videoPrepared = false;

    private SharedPreferences favorites;
    private SharedPreferences playback;
    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewer);

        bindViews();
        readIntent();
        setupDeleteLauncher();
        setupButtons();
        loadFavoriteState();
        setupViewer();
    }

    private void bindViews() {
        imgViewer = findViewById(R.id.imgViewer);
        videoViewer = findViewById(R.id.videoViewer);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        txtViewerTitle = findViewById(R.id.txtViewerTitle);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnRotate = findViewById(R.id.btnRotate);
    }

    private void readIntent() {
        String uriText = getIntent().getStringExtra(EXTRA_URI);
        mediaName = getIntent().getStringExtra(EXTRA_NAME);
        mimeType = getIntent().getStringExtra(EXTRA_MIME);
        mediaSize = getIntent().getLongExtra(EXTRA_SIZE, 0);
        mediaDate = getIntent().getLongExtra(EXTRA_DATE, 0);
        isVideo = getIntent().getBooleanExtra(EXTRA_IS_VIDEO, false);

        if (mediaName == null || mediaName.trim().isEmpty()) {
            mediaName = isVideo ? "Video" : "Fotoğraf";
        }

        if (mimeType == null) {
            mimeType = "";
        }

        if (!isVideo && mimeType.startsWith("video/")) {
            isVideo = true;
        }

        txtViewerTitle.setText(mediaName);

        if (uriText == null || uriText.isEmpty()) {
            Toast.makeText(this, "Medya açılamadı.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mediaUri = Uri.parse(uriText);
    }

    private void setupViewer() {
        if (mediaUri == null) {
            return;
        }

        if (isVideo) {
            setupVideo();
        } else {
            setupPhoto();
        }
    }

    private void setupPhoto() {
        videoViewer.setVisibility(View.GONE);
        imgViewer.setVisibility(View.VISIBLE);
        btnRotate.setText("Döndür");

        Glide.with(this)
                .load(mediaUri)
                .fitCenter()
                .into(imgViewer);

        imgViewer.setOnClickListener(v -> toggleBars());
    }

    private void setupVideo() {
        imgViewer.setVisibility(View.GONE);
        videoViewer.setVisibility(View.VISIBLE);
        btnRotate.setText("Ekran");

        playback = getSharedPreferences("video_positions", MODE_PRIVATE);

        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoViewer);
        videoViewer.setMediaController(controller);
        videoViewer.setVideoURI(mediaUri);

        videoViewer.setOnPreparedListener(mp -> {
            videoPrepared = true;
            int saved = playback.getInt(mediaUri.toString(), 0);
            if (saved > 1000 && saved < mp.getDuration() - 1000) {
                videoViewer.seekTo(saved);
            }
            videoViewer.start();
        });

        videoViewer.setOnCompletionListener(mp -> {
            playback.edit().remove(mediaUri.toString()).apply();
        });

        videoViewer.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Video açılamadı.", Toast.LENGTH_LONG).show();
            return true;
        });

        videoViewer.setOnClickListener(v -> toggleBars());
    }

    private void setupButtons() {
        findViewById(R.id.btnViewerBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnShare).setOnClickListener(v -> shareMedia());
        btnRotate.setOnClickListener(v -> rotateOrToggleOrientation());
        findViewById(R.id.btnInfo).setOnClickListener(v -> showInfo());
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
        btnFavorite.setOnClickListener(v -> toggleFavorite());
    }

    private void toggleBars() {
        barsVisible = !barsVisible;
        topBar.setVisibility(barsVisible ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(barsVisible ? View.VISIBLE : View.GONE);

        if (barsVisible) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void rotateOrToggleOrientation() {
        if (isVideo) {
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
            return;
        }

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

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(
                mimeType.isEmpty()
                        ? (isVideo ? "video/*" : "image/*")
                        : mimeType
        );
        shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Paylaş"));
    }

    private void loadFavoriteState() {
        favorites = getSharedPreferences("favorites", MODE_PRIVATE);

        if (mediaUri == null) {
            return;
        }

        favorite = favorites.getBoolean(mediaUri.toString(), false);
        updateFavoriteButton();
    }

    private void toggleFavorite() {
        if (mediaUri == null) {
            return;
        }

        favorite = !favorite;
        favorites.edit().putBoolean(mediaUri.toString(), favorite).apply();
        updateFavoriteButton();

        Toast.makeText(
                this,
                favorite ? "Favorilere eklendi" : "Favorilerden çıkarıldı",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void updateFavoriteButton() {
        btnFavorite.setText(favorite ? "★" : "☆");
    }

    private void showInfo() {
        String dateText = "Bilinmiyor";
        if (mediaDate > 0) {
            long millis = mediaDate < 100000000000L ? mediaDate * 1000L : mediaDate;
            dateText = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                    Locale.getDefault()
            ).format(new Date(millis));
        }

        String info =
                "Dosya: " + mediaName
                        + "\n\nTür: " + (mimeType.isEmpty() ? "Bilinmiyor" : mimeType)
                        + "\n\nBoyut: " + formatFileSize(mediaSize)
                        + "\n\nTarih: " + dateText
                        + "\n\nURI:\n" + (mediaUri == null ? "-" : mediaUri.toString());

        new AlertDialog.Builder(this)
                .setTitle("Medya Bilgisi")
                .setMessage(info)
                .setPositiveButton("Tamam", null)
                .show();
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) {
            return "Bilinmiyor";
        }

        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", kb);
        }

        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", mb);
        }

        return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0);
    }

    private void confirmDelete() {
        if (mediaUri == null) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Sil")
                .setMessage((isVideo ? "Bu videoyu" : "Bu fotoğrafı") + " silmek istiyor musun?")
                .setPositiveButton("Sil", (dialog, which) -> requestDelete())
                .setNegativeButton("İptal", null)
                .show();
    }

    private void setupDeleteLauncher() {
        deleteLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PendingIntent pendingIntent = MediaStore.createDeleteRequest(
                        getContentResolver(),
                        Collections.singletonList(mediaUri)
                );

                IntentSenderRequest request = new IntentSenderRequest.Builder(
                        pendingIntent.getIntentSender()
                ).build();

                deleteLauncher.launch(request);
            } else {
                int deleted = getContentResolver().delete(mediaUri, null, null);

                if (deleted > 0) {
                    Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, "Dosya silinemedi.", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (SecurityException e) {
            Toast.makeText(
                    this,
                    "Bu dosyayı silmek için Android izin vermedi.",
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Silme hatası: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onPause() {
        if (isVideo && mediaUri != null && videoPrepared && videoViewer != null) {
            int position = videoViewer.getCurrentPosition();
            if (position > 1000) {
                playback.edit().putInt(mediaUri.toString(), position).apply();
            }
            videoViewer.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isVideo && videoPrepared && videoViewer != null && !videoViewer.isPlaying()) {
            videoViewer.start();
        }
    }

    @Override
    protected void onDestroy() {
        if (videoViewer != null) {
            videoViewer.stopPlayback();
        }
        super.onDestroy();
    }
}
