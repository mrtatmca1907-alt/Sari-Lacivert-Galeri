package com.atmaca.hiosfilemanager;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class ZoomImageView extends ImageView {
    public interface Listener {
        void onSingleTap();
        void onSwipeLeft();
        void onSwipeRight();
    }

    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private Listener listener;
    private float scale = 1f;
    private float minScale = 1f;
    private float maxScale = 5f;
    private float lastX, lastY;
    private boolean dragging = false;

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float next = clamp(scale * factor, minScale, maxScale);
                factor = next / scale;
                scale = next;
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                fixTranslation();
                setImageMatrix(matrix);
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onSingleTap();
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                if (scale > 1.05f) resetZoom();
                else zoomTo(2.5f, e.getX(), e.getY());
                return true;
            }

            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (scale > 1.05f || e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) < 80 || Math.abs(vx) < 300) return false;
                if (listener != null) {
                    if (dx < 0) listener.onSwipeLeft();
                    else listener.onSwipeRight();
                }
                return true;
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isZoomed() {
        return scale > 1.05f;
    }

    public void resetZoom() {
        scale = 1f;
        configureBaseMatrix();
    }

    public void zoomTo(float targetScale, float focusX, float focusY) {
        float next = clamp(targetScale, minScale, maxScale);
        float factor = next / scale;
        scale = next;
        matrix.postScale(factor, factor, focusX, focusY);
        fixTranslation();
        setImageMatrix(matrix);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        post(this::configureBaseMatrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        post(this::configureBaseMatrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && scale > 1.05f) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    if (Math.abs(dx) > 1 || Math.abs(dy) > 1) dragging = true;
                    matrix.postTranslate(dx, dy);
                    fixTranslation();
                    setImageMatrix(matrix);
                    lastX = event.getX();
                    lastY = event.getY();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                break;
        }
        return true;
    }

    private void configureBaseMatrix() {
        Drawable d = getDrawable();
        if (d == null || getWidth() <= 0 || getHeight() <= 0) return;

        matrix.reset();
        float dw = d.getIntrinsicWidth();
        float dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;

        float fit = Math.min(getWidth() / dw, getHeight() / dh);
        float w = dw * fit;
        float h = dh * fit;
        float tx = (getWidth() - w) / 2f;
        float ty = (getHeight() - h) / 2f;

        matrix.postScale(fit, fit);
        matrix.postTranslate(tx, ty);
        scale = 1f;
        setImageMatrix(matrix);
    }

    private void fixTranslation() {
        Drawable d = getDrawable();
        if (d == null) return;

        RectF rect = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        matrix.mapRect(rect);

        float dx = 0f, dy = 0f;
        if (rect.width() <= getWidth()) dx = getWidth() / 2f - rect.centerX();
        else {
            if (rect.left > 0) dx = -rect.left;
            if (rect.right < getWidth()) dx = getWidth() - rect.right;
        }

        if (rect.height() <= getHeight()) dy = getHeight() / 2f - rect.centerY();
        else {
            if (rect.top > 0) dy = -rect.top;
            if (rect.bottom < getHeight()) dy = getHeight() - rect.bottom;
        }

        matrix.postTranslate(dx, dy);
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
