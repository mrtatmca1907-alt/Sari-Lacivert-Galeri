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
    private float rotation = 0f;
    private final float minScale = 1f;
    private final float maxScale = 5f;
    private float lastX, lastY;
    private float lastAngle = Float.NaN;

    public ZoomImageView(Context context) { this(context, null); }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float next = clamp(scale * detector.getScaleFactor(), minScale, maxScale);
                float factor = next / scale;
                scale = next;
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                setImageMatrix(matrix);
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onSingleTap();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (scale > 1.05f) resetTransform();
                else zoomTo(2.5f, e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (isTransformed() || e1 == null || e2 == null) return false;
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

    public void setListener(Listener listener) { this.listener = listener; }

    public boolean isZoomed() { return scale > 1.05f; }

    public boolean isTransformed() {
        return scale > 1.05f || Math.abs(normalizedRotation()) > 0.5f;
    }

    public float getUserRotation() { return normalizedRotation(); }

    public void resetZoom() { resetTransform(); }

    public void resetTransform() {
        scale = 1f;
        rotation = 0f;
        configureBaseMatrix();
    }

    public void rotateBy(float degrees) {
        rotation += degrees;
        matrix.postRotate(degrees, getWidth() / 2f, getHeight() / 2f);
        setImageMatrix(matrix);
    }

    public void zoomTo(float targetScale, float focusX, float focusY) {
        float next = clamp(targetScale, minScale, maxScale);
        float factor = next / scale;
        scale = next;
        matrix.postScale(factor, factor, focusX, focusY);
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

        int action = event.getActionMasked();

        if (event.getPointerCount() >= 2) {
            float angle = angle(event);
            if (!Float.isNaN(lastAngle) && action == MotionEvent.ACTION_MOVE) {
                float delta = angle - lastAngle;
                rotation += delta;
                matrix.postRotate(delta, midpointX(event), midpointY(event));
                setImageMatrix(matrix);
            }
            lastAngle = angle;
        } else if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            lastAngle = Float.NaN;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && event.getPointerCount() == 1 && isTransformed()) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    matrix.postTranslate(dx, dy);
                    setImageMatrix(matrix);
                    lastX = event.getX();
                    lastY = event.getY();
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                lastAngle = angle(event);
                break;

            case MotionEvent.ACTION_POINTER_UP:
                lastAngle = Float.NaN;
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
        rotation = 0f;
        setImageMatrix(matrix);
    }

    private float angle(MotionEvent e) {
        if (e.getPointerCount() < 2) return Float.NaN;
        float dx = e.getX(1) - e.getX(0);
        float dy = e.getY(1) - e.getY(0);
        return (float)Math.toDegrees(Math.atan2(dy, dx));
    }

    private float midpointX(MotionEvent e) {
        return (e.getX(0) + e.getX(1)) / 2f;
    }

    private float midpointY(MotionEvent e) {
        return (e.getY(0) + e.getY(1)) / 2f;
    }

    private float normalizedRotation() {
        float r = rotation % 360f;
        if (r > 180f) r -= 360f;
        if (r < -180f) r += 360f;
        return r;
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
