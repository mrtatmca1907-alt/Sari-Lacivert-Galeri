package com.atmaca.hiosfilemanager;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.VideoView;

public class VideoPlayerActivity extends Activity {
    private VideoView video;
    private boolean landscape = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        String path = getIntent().getStringExtra("file");
        if (path == null) { finish(); return; }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        video = new VideoView(this);
        MediaController controls = new MediaController(this);
        controls.setAnchorView(video);
        video.setMediaController(controls);
        video.setVideoPath(path);
        video.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            video.start();
        });
        root.addView(video, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        Button rotate = new Button(this);
        rotate.setText("Yatay");
        rotate.setTextColor(Color.WHITE);
        rotate.setBackgroundColor(0x66000000);
        rotate.setOnClickListener(v -> {
            landscape = !landscape;
            setRequestedOrientation(landscape
                    ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            rotate.setText(landscape ? "Dikey" : "Yatay");
        });
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(90), dp(44), Gravity.TOP | Gravity.RIGHT);
        rp.topMargin = dp(14);
        rp.rightMargin = dp(14);
        root.addView(rotate, rp);

        setContentView(root);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (video != null && video.isPlaying()) video.pause();
    }
}
