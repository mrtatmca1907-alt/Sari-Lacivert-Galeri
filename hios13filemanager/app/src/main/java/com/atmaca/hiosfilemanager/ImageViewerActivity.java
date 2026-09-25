package com.atmaca.hiosfilemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.core.content.FileProvider;

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

    private ZoomImageView imageView;
    private TextView counter;
    private LinearLayout topBar;
    private Bitmap currentBitmap;
    private int index = 0;
    private boolean slideshow = false;
    private boolean chromeVisible = false;

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
        enterImmersive();

        String directory = getIntent().getStringExtra("directory");
        String selected = getIntent().getStringExtra("file");

        File dir = directory == null ? null : new File(directory);
        File[] files = dir == null ? null : dir.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
            for (File f : files) if (f.isFile() && isImage(f)) images.add(f);
        }
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getAbsolutePath().equals(selected)) {
                index = i;
                break;
            }
        }

        buildUi();
        if (images.isEmpty()) {
            finish();
            return;
        }

        showIndex(index);
        if (getIntent().getBooleanExtra("slideshow", false)) {
            slideshow = true;
            handler.postDelayed(slideTask, 3000);
        }
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

        imageView = new ZoomImageView(this);
        imageView.setBackgroundColor(Color.BLACK);
        imageView.setListener(new ZoomImageView.Listener() {
            @Override public void onSingleTap() { setChromeVisible(!chromeVisible); }
            @Override public void onSwipeLeft() { next(); }
            @Override public void onSwipeRight() { previous(); }
        });

        root.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
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

        counter = chromeText("", 14);
        counter.setGravity(Gravity.CENTER);
        topBar.addView(counter, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView more = chromeText("⋮", 28);
        more.setOnClickListener(this::showMenu);
        topBar.addView(more, new LinearLayout.LayoutParams(dp(54), dp(48)));

        root.addView(topBar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP
        ));

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

    private void setChromeVisible(boolean visible) {
        chromeVisible = visible;
        topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) enterImmersive();
    }

    private void showMenu(View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        p.getMenu().add(slideshow ? "Slaytı durdur" : "Slayt başlat");
        p.getMenu().add("Paylaş");
        p.getMenu().add("Ayrıntılar");
        p.getMenu().add("Sola dön");
        p.getMenu().add("Sağa dön");
        p.getMenu().add("Zoom sıfırla");
        p.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if (t.startsWith("Slayt")) toggleSlideshow();
            else if (t.equals("Paylaş")) shareCurrent();
            else if (t.equals("Ayrıntılar")) showDetails();
            else if (t.equals("Sola dön")) imageView.setRotation(imageView.getRotation() - 90f);
            else if (t.equals("Sağa dön")) imageView.setRotation(imageView.getRotation() + 90f);
            else if (t.equals("Zoom sıfırla")) imageView.resetZoom();
            return true;
        });
        p.show();
    }

    private void next() {
        if (images.isEmpty()) return;
        imageView.setRotation(0f);
        imageView.resetZoom();
        showIndex((index + 1) % images.size());
    }

    private void previous() {
        if (images.isEmpty()) return;
        imageView.setRotation(0f);
        imageView.resetZoom();
        showIndex((index - 1 + images.size()) % images.size());
    }

    private void toggleSlideshow() {
        slideshow = !slideshow;
        handler.removeCallbacks(slideTask);
        if (slideshow) {
            imageView.resetZoom();
            setChromeVisible(false);
            handler.postDelayed(slideTask, 3000);
        }
    }

    private void shareCurrent() {
        if (images.isEmpty()) return;
        File file = images.get(index);
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/*");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Paylaş"));
        } catch (Exception ignored) {}
    }

    private void showDetails() {
        if (images.isEmpty()) return;
        File f = images.get(index);
        new AlertDialog.Builder(this)
                .setTitle(f.getName())
                .setMessage("Konum: " + f.getParent() +
                        "\nBoyut: " + formatBytes(f.length()) +
                        "\nGörsel: " + (index + 1) + " / " + images.size())
                .setPositiveButton("Tamam", null)
                .show();
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
                imageView.resetZoom();
                if (old != null && old != decoded && !old.isRecycled()) old.recycle();
            });
        });
    }

    private Bitmap decodeForScreen(File file) {
        int targetW = Math.max(getResources().getDisplayMetrics().widthPixels * 2, 1080);
        int targetH = Math.max(getResources().getDisplayMetrics().heightPixels * 2, 2460);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

        int sample = 1;
        while ((bounds.outWidth / (sample * 2)) >= targetW &&
               (bounds.outHeight / (sample * 2)) >= targetH) {
            sample *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, sample);
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inDither = false;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    private boolean isImage(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|heic|heif|avif)$");
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

    @Override protected void onResume() {
        super.onResume();
        enterImmersive();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        decoder.shutdownNow();
        if (currentBitmap != null && !currentBitmap.isRecycled()) currentBitmap.recycle();
        super.onDestroy();
    }
}
