package com.atmaca.hiosfilemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.Locale;

public class VideoPlayerActivity extends Activity {
    private PlayerView playerView;
    private ExoPlayer player;
    private LinearLayout topBar;
    private boolean chromeVisible = false;
    private String path;
    private long savedPosition = 0L;
    private boolean playWhenReady = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();

        path = getIntent().getStringExtra("file");
        if (path == null) {
            finish();
            return;
        }

        buildUi();
        initializePlayer();
    }

    private void enterImmersive() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(false);
        playerView.setControllerHideOnTouch(false);
        playerView.setControllerShowTimeoutMs(3500);
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        topBar.setBackgroundColor(0xAA000000);
        topBar.setVisibility(View.GONE);

        TextView menu = chromeText("☰", 28);
        menu.setOnClickListener(this::showMenu);
        topBar.addView(menu, new LinearLayout.LayoutParams(dp(54), dp(48)));

        TextView title = chromeText(new File(path).getName(), 14);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        topBar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView more = chromeText("⋮", 28);
        more.setOnClickListener(this::showMenu);
        topBar.addView(more, new LinearLayout.LayoutParams(dp(54), dp(48)));

        root.addView(topBar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP
        ));

        playerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                setChromeVisible(!chromeVisible);
                return true;
            }
            return true;
        });

        setContentView(root);
    }

    private TextView chromeText(String text, int size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(size);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private void initializePlayer() {
        releasePlayer();

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);

        MediaItem item = MediaItem.fromUri(Uri.fromFile(new File(path)));
        player.setMediaItem(item);
        player.setPlayWhenReady(playWhenReady);
        if (savedPosition > 0) player.seekTo(savedPosition);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                showPlaybackError(error);
            }
        });
        player.prepare();
    }

    private void setChromeVisible(boolean visible) {
        chromeVisible = visible;
        topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            playerView.showController();
        } else {
            playerView.hideController();
            enterImmersive();
        }
    }

    private void showMenu(View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        p.getMenu().add("Otomatik döndür");
        p.getMenu().add("Yatay");
        p.getMenu().add("Dikey");
        p.getMenu().add("Paylaş");
        p.getMenu().add("Ayrıntılar");
        p.getMenu().add("Harici oynatıcıda aç");
        p.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if (t.equals("Otomatik döndür")) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            else if (t.equals("Yatay")) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            else if (t.equals("Dikey")) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            else if (t.equals("Paylaş")) shareVideo();
            else if (t.equals("Ayrıntılar")) showDetails();
            else if (t.equals("Harici oynatıcıda aç")) openExternal();
            return true;
        });
        p.show();
    }

    private void showPlaybackError(PlaybackException error) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Video açılamadı")
                .setMessage("Bu dosya cihaz codec'i ile açılamadı. İstersen harici oynatıcıda açabilirsin.")
                .setNegativeButton("Kapat", null)
                .setPositiveButton("Harici aç", (d, w) -> openExternal())
                .show();
    }

    private void openExternal() {
        try {
            File f = new File(path);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "video/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private void shareVideo() {
        try {
            File f = new File(path);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("video/*");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Paylaş"));
        } catch (Exception ignored) {}
    }

    private void showDetails() {
        File f = new File(path);
        new AlertDialog.Builder(this)
                .setTitle(f.getName())
                .setMessage("Konum: " + f.getParent() + "\nBoyut: " + formatBytes(f.length()))
                .setPositiveButton("Tamam", null)
                .show();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / (1024L * 1024)) + " MB";
        return String.format(Locale.US, "%.1f GB", bytes / (1024d * 1024d * 1024d));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void releasePlayer() {
        if (player != null) {
            savedPosition = player.getCurrentPosition();
            playWhenReady = player.getPlayWhenReady();
            player.release();
            player = null;
        }
    }

    @Override
    protected void onPause() {
        if (player != null) {
            savedPosition = player.getCurrentPosition();
            playWhenReady = player.getPlayWhenReady();
            player.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
        if (player == null) initializePlayer();
        else if (playWhenReady) player.play();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
