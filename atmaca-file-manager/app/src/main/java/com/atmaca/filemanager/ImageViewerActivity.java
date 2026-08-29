package com.atmaca.filemanager;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageViewerActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "path";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ZoomImageView image;
    private TextView title;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        buildUi();
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null || path.isEmpty()) { finish(); return; }
        File file = new File(path);
        title.setText(file.getName());
        load(path);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.rgb(31,31,31));
        bar.setPadding(dp(4), 0, dp(8), 0);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(40);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(56)));

        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1f));

        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        image = new ZoomImageView(this);
        root.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void load(String path) {
        worker.execute(() -> {
            int w = Math.max(1080, getResources().getDisplayMetrics().widthPixels * 2);
            int h = Math.max(1920, getResources().getDisplayMetrics().heightPixels * 2);
            Bitmap b = ThumbnailLoader.decodeSampled(path, w, h);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (b == null) Toast.makeText(this, "Görsel açılamadı.", Toast.LENGTH_SHORT).show();
                else image.setImageBitmapAndReset(b);
            });
        });
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    static final class ZoomImageView extends AppCompatImageView {
        private final Matrix drawMatrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private float scale = 1f;
        private float lastX, lastY;
        private boolean dragging;

        ZoomImageView(android.content.Context context) {
            super(context);
            setBackgroundColor(Color.BLACK);
            setScaleType(ImageView.ScaleType.MATRIX);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    float requested = scale * detector.getScaleFactor();
                    float next = Math.max(1f, Math.min(6f, requested));
                    float factor = next / scale;
                    scale = next;
                    drawMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    setImageMatrix(drawMatrix);
                    return true;
                }
            });
            setOnClickListener(v -> {
                if (scale > 1.01f) resetMatrix();
            });
        }

        void setImageBitmapAndReset(Bitmap bitmap) {
            setImageBitmap(bitmap);
            post(this::resetMatrix);
        }

        private void resetMatrix() {
            if (getDrawable() == null || getWidth() <= 0 || getHeight() <= 0) return;
            int dw = getDrawable().getIntrinsicWidth();
            int dh = getDrawable().getIntrinsicHeight();
            if (dw <= 0 || dh <= 0) return;
            float fit = Math.min((float) getWidth() / dw, (float) getHeight() / dh);
            float dx = (getWidth() - dw * fit) / 2f;
            float dy = (getHeight() - dh * fit) / 2f;
            drawMatrix.reset();
            drawMatrix.postScale(fit, fit);
            drawMatrix.postTranslate(dx, dy);
            scale = 1f;
            setImageMatrix(drawMatrix);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 1 && scale > 1f && !scaleDetector.isInProgress()) {
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;
                        if (Math.abs(dx) + Math.abs(dy) > 2f) dragging = true;
                        drawMatrix.postTranslate(dx, dy);
                        setImageMatrix(drawMatrix);
                        lastX = event.getX();
                        lastY = event.getY();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging && !scaleDetector.isInProgress()) performClick();
                    return true;
                default:
                    return true;
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
