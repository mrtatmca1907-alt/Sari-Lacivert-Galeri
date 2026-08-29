package com.atmaca.filemanager;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public final class VideoPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "path";
    private VideoView videoView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null || path.isEmpty()) { finish(); return; }
        File file = new File(path);
        if (!file.isFile()) { finish(); return; }
        buildUi(file.getName());
        MediaController controls = new MediaController(this);
        controls.setAnchorView(videoView);
        videoView.setMediaController(controls);
        videoView.setVideoPath(path);
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            videoView.start();
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Video açılamadı.", Toast.LENGTH_SHORT).show();
            return true;
        });
        videoView.requestFocus();
    }

    private void buildUi(String name) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.rgb(31,31,31));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(40);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(56)));

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1f));

        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        videoView = new VideoView(this);
        root.addView(videoView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) videoView.pause();
    }

    @Override protected void onDestroy() {
        if (videoView != null) videoView.stopPlayback();
        super.onDestroy();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
