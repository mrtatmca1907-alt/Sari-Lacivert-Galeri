package com.sarilacivert.galeri;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

public class ZoomImageView extends AppCompatImageView {

    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 8.0f;

    private final Matrix matrix = new Matrix();
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float zoom = 1.0f;
    private float baseScale = 1.0f;
    private float lastX;
    private float lastY;
    private boolean dragging;
    private boolean initialized;

    public ZoomImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        super.setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        if (getDrawable() == null) {
                            return false;
                        }

                        float factor = detector.getScaleFactor();
                        float target = zoom * factor;

                        if (target < MIN_ZOOM) {
                            factor = MIN_ZOOM / zoom;
                            target = MIN_ZOOM;
                        } else if (target > MAX_ZOOM) {
                            factor = MAX_ZOOM / zoom;
                            target = MAX_ZOOM;
                        }

                        matrix.postScale(
                                factor,
                                factor,
                                detector.getFocusX(),
                                detector.getFocusY()
                        );

                        zoom = target;
                        fixTranslation();
                        setImageMatrix(matrix);
                        return true;
                    }
                }
        );

        gestureDetector = new GestureDetector(
                context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        performClick();
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (zoom > 1.05f) {
                            resetZoom();
                        } else {
                            zoomTo(2.5f, e.getX(), e.getY());
                        }
                        return true;
                    }
                }
        );
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        initialized = false;
        post(this::resetZoom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        post(this::resetZoom);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getDrawable() == null) {
            return super.onTouchEvent(event);
        }

        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && event.getPointerCount() == 1 && zoom > 1.0f) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;

                    if (Math.abs(dx) > 1f || Math.abs(dy) > 1f) {
                        dragging = true;
                    }

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

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public boolean isZoomed() {
        return zoom > 1.05f;
    }

    public void resetZoom() {
        Drawable drawable = getDrawable();
        int viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int viewHeight = getHeight() - getPaddingTop() - getPaddingBottom();

        if (drawable == null || viewWidth <= 0 || viewHeight <= 0) {
            return;
        }

        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();

        if (drawableWidth <= 0 || drawableHeight <= 0) {
            return;
        }

        matrix.reset();

        float scaleX = viewWidth / (float) drawableWidth;
        float scaleY = viewHeight / (float) drawableHeight;
        baseScale = Math.min(scaleX, scaleY);

        float dx = getPaddingLeft() + (viewWidth - drawableWidth * baseScale) / 2f;
        float dy = getPaddingTop() + (viewHeight - drawableHeight * baseScale) / 2f;

        matrix.postScale(baseScale, baseScale);
        matrix.postTranslate(dx, dy);

        zoom = MIN_ZOOM;
        initialized = true;
        setImageMatrix(matrix);
    }

    private void zoomTo(float targetZoom, float focusX, float focusY) {
        if (!initialized) {
            resetZoom();
        }

        targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, targetZoom));
        float factor = targetZoom / zoom;

        matrix.postScale(factor, factor, focusX, focusY);
        zoom = targetZoom;
        fixTranslation();
        setImageMatrix(matrix);
    }

    private RectF imageRect() {
        Drawable drawable = getDrawable();
        if (drawable == null) {
            return new RectF();
        }

        RectF rect = new RectF(
                0,
                0,
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight()
        );

        matrix.mapRect(rect);
        return rect;
    }

    private void fixTranslation() {
        RectF rect = imageRect();

        float leftBound = getPaddingLeft();
        float topBound = getPaddingTop();
        float rightBound = getWidth() - getPaddingRight();
        float bottomBound = getHeight() - getPaddingBottom();

        float viewWidth = rightBound - leftBound;
        float viewHeight = bottomBound - topBound;

        float dx = 0f;
        float dy = 0f;

        if (rect.width() <= viewWidth) {
            dx = leftBound + (viewWidth - rect.width()) / 2f - rect.left;
        } else {
            if (rect.left > leftBound) {
                dx = leftBound - rect.left;
            } else if (rect.right < rightBound) {
                dx = rightBound - rect.right;
            }
        }

        if (rect.height() <= viewHeight) {
            dy = topBound + (viewHeight - rect.height()) / 2f - rect.top;
        } else {
            if (rect.top > topBound) {
                dy = topBound - rect.top;
            } else if (rect.bottom < bottomBound) {
                dy = bottomBound - rect.bottom;
            }
        }

        matrix.postTranslate(dx, dy);
    }
}
