package com.atmaca.hiosfilemanager;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageViewerActivity extends Activity {
    private final List<File> images = new ArrayList<>();
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ImageView imageView;
    private TextView counter;
    private Button slideshowButton;
    private GestureDetector gestures;
    private Bitmap currentBitmap;
    private int index = 0;
    private boolean slideshow = false;

    private final Runnable slideTask = new Runnable() {
        @Override public void run() {
            if (!slideshow || images.isEmpty()) return;
            showIndex((index + 1) % images.size());
            handler.postDelayed(this, 3000);
        }
    };

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

        String directory = getIntent().getStringExtra("directory");
        String selected = getIntent().getStringExtra("file");

        File dir = directory == null ? null : new File(directory);
        File[] files = dir == null ? null : dir.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
            for (File f : files) if (f.isFile() && isImage(f)) images.add(f);
        }
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getAbsolutePath().equals(selected)) { index = i; break; }
        }

        buildUi();
        if (images.isEmpty()) finish();
        else showIndex(index);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        imageView = new ImageView(this);
        imageView.setBackgroundColor(Color.BLACK);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        counter = new TextView(this);
        counter.setTextColor(Color.WHITE);
        counter.setTextSize(14);
        counter.setPadding(dp(12), dp(8), dp(12), dp(8));
        counter.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        cp.topMargin = dp(12);
        root.addView(counter, cp);

        Button close = button("Kapat");
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams closeP = new FrameLayout.LayoutParams(dp(90), dp(44), Gravity.BOTTOM | Gravity.LEFT);
        closeP.leftMargin = dp(14);
        closeP.bottomMargin = dp(16);
        root.addView(close, closeP);

        slideshowButton = button("Slayt");
        slideshowButton.setOnClickListener(v -> toggleSlideshow());
        FrameLayout.LayoutParams slideP = new FrameLayout.LayoutParams(dp(90), dp(44), Gravity.BOTTOM | Gravity.RIGHT);
        slideP.rightMargin = dp(14);
        slideP.bottomMargin = dp(16);
        root.addView(slideshowButton, slideP);

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) < dp(55) || Math.abs(velocityX) < 250) return false;
                if (dx < 0) next(); else previous();
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                int vis = slideshowButton.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
                slideshowButton.setVisibility(vis);
                close.setVisibility(vis);
                counter.setVisibility(vis);
                return true;
            }
        });
        imageView.setOnTouchListener((v, event) -> gestures.onTouchEvent(event));

        setContentView(root);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0x66000000);
        return b;
    }

    private void next() {
        if (images.isEmpty()) return;
        showIndex((index + 1) % images.size());
    }

    private void previous() {
        if (images.isEmpty()) return;
        showIndex((index - 1 + images.size()) % images.size());
    }

    private void toggleSlideshow() {
        slideshow = !slideshow;
        slideshowButton.setText(slideshow ? "Durdur" : "Slayt");
        handler.removeCallbacks(slideTask);
        if (slideshow) handler.postDelayed(slideTask, 3000);
    }

    private void showIndex(int target) {
        if (target < 0 || target >= images.size()) return;
        index = target;
        File file = images.get(index);
        counter.setText((index + 1) + " / " + images.size() + "   " + file.getName());
        final String expected = file.getAbsolutePath();
        imageView.setTag(expected);

        decoder.execute(() -> {
            Bitmap decoded = decodeForScreen(file);
            runOnUiThread(() -> {
                if (!expected.equals(imageView.getTag())) {
                    if (decoded != null) decoded.recycle();
                    return;
                }
                Bitmap old = currentBitmap;
                currentBitmap = decoded;
                imageView.setImageBitmap(decoded);
                if (old != null && old != decoded && !old.isRecycled()) old.recycle();
            });
        });
    }

    private Bitmap decodeForScreen(File file) {
        int targetW = Math.max(getResources().getDisplayMetrics().widthPixels * 2, 1080);
        int targetH = Math.max(getResources().getDisplayMetrics().heightPixels * 2, 1920);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while ((bounds.outWidth / (sample * 2)) >= targetW && (bounds.outHeight / (sample * 2)) >= targetH) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, sample);
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    private boolean isImage(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|heic|heif|avif)$");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        decoder.shutdownNow();
        if (currentBitmap != null && !currentBitmap.isRecycled()) currentBitmap.recycle();
        super.onDestroy();
    }
}
