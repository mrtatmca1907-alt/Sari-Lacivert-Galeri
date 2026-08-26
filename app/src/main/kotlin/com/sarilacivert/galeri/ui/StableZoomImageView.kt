package com.sarilacivert.galeri.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Compose içinde üç ayrı pointerInput birbirinin hareketlerini tükettiği için eski
 * görüntüleyicide pinch, pan ve sağ/sol geçiş zaman zaman kavga ediyordu.
 * Bu view bütün hareketleri tek MotionEvent akışında yönetir.
 */
class StableZoomImageView(context: Context) : ImageView(context) {
    var onSingleTapAction: (() -> Unit)? = null
    var onPreviousAction: (() -> Unit)? = null
    var onNextAction: (() -> Unit)? = null

    private val drawMatrix = Matrix()
    private var shownBitmap: Bitmap? = null

    private var userScale = 1f
    private var translateX = 0f
    private var translateY = 0f
    private var externalRotation = 0f

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var hadMultiTouch = false
    private var moved = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val swipeThreshold = 110f * resources.displayMetrics.density

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                hadMultiTouch = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = userScale
                val newScale = (oldScale * detector.scaleFactor).coerceIn(1f, 8f)
                if (!newScale.isFinite() || abs(newScale - oldScale) < 0.0001f) return true

                if (newScale <= 1.001f) {
                    userScale = 1f
                    translateX = 0f
                    translateY = 0f
                } else {
                    val ratio = newScale / oldScale.coerceAtLeast(0.0001f)
                    val cx = width / 2f
                    val cy = height / 2f
                    translateX = ratio * translateX + (1f - ratio) * (detector.focusX - cx)
                    translateY = ratio * translateY + (1f - ratio) * (detector.focusY - cy)
                    userScale = newScale
                }
                updateImageMatrix()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (userScale < 1.015f) resetTransform()
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!hadMultiTouch) {
                    performClick()
                    onSingleTapAction?.invoke()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (hadMultiTouch) return true
                if (userScale > 1.05f) {
                    resetTransform()
                } else {
                    val old = userScale
                    val target = 2.5f
                    val ratio = target / old
                    val cx = width / 2f
                    val cy = height / 2f
                    translateX = ratio * translateX + (1f - ratio) * (e.x - cx)
                    translateY = ratio * translateY + (1f - ratio) * (e.y - cy)
                    userScale = target
                    updateImageMatrix()
                }
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
        isFocusable = true
    }

    fun setBitmap(bitmap: Bitmap?) {
        if (shownBitmap === bitmap) return
        shownBitmap = bitmap
        setImageBitmap(bitmap)
        resetTransform()
    }

    fun setExternalRotation(degrees: Float) {
        val normalized = ((degrees % 360f) + 360f) % 360f
        if (abs(normalized - externalRotation) < 0.01f) return
        externalRotation = normalized
        rotation = externalRotation
        pivotX = width / 2f
        pivotY = height / 2f
        resetTransform()
    }

    fun resetTransform() {
        userScale = 1f
        translateX = 0f
        translateY = 0f
        updateImageMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w / 2f
        pivotY = h / 2f
        resetTransform()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            hadMultiTouch = true
        }

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                hadMultiTouch = false
                moved = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                hadMultiTouch = true
                moved = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val x = event.x
                    val y = event.y
                    val dx = x - lastX
                    val dy = y - lastY

                    if (userScale > 1.015f) {
                        translateX += dx
                        translateY += dy
                        updateImageMatrix()
                    }

                    if (abs(x - downX) > touchSlop || abs(y - downY) > touchSlop) moved = true
                    lastX = x
                    lastY = y
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!hadMultiTouch && userScale <= 1.015f) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) >= swipeThreshold && abs(dx) > abs(dy) * 1.25f) {
                        if (dx > 0f) onPreviousAction?.invoke() else onNextAction?.invoke()
                    }
                }
                if (userScale <= 1.015f) {
                    userScale = 1f
                    translateX = 0f
                    translateY = 0f
                    updateImageMatrix()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (userScale <= 1.015f) resetTransform()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateImageMatrix() {
        val drawable = drawable ?: return
        if (width <= 0 || height <= 0) return

        val dw = drawable.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val dh = drawable.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val baseScale = min(width / dw, height / dh)
        val finalScale = baseScale * userScale

        val displayW = dw * finalScale
        val displayH = dh * finalScale
        val maxX = max(0f, (displayW - width) / 2f)
        val maxY = max(0f, (displayH - height) / 2f)

        translateX = translateX.coerceIn(-maxX, maxX)
        translateY = translateY.coerceIn(-maxY, maxY)

        val left = (width - displayW) / 2f + translateX
        val top = (height - displayH) / 2f + translateY

        drawMatrix.reset()
        drawMatrix.setScale(finalScale, finalScale)
        drawMatrix.postTranslate(left, top)
        imageMatrix = drawMatrix
        invalidate()
    }
}
