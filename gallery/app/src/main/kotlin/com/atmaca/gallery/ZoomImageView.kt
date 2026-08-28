package com.atmaca.gallery

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.min

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {
    private val matrix = Matrix()
    private val baseMatrix = Matrix()
    private val zoom = ZoomState()
    private var lastX = 0f
    private var lastY = 0f

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val old = zoom.scale
            val now = zoom.onScale(detector.scaleFactor)
            matrix.postScale(now / old, now / old, detector.focusX, detector.focusY)
            imageMatrix = matrix
            return true
        }
    })

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val old = zoom.scale
            val now = zoom.onDoubleTap()
            if (now == 1f) resetToFit() else {
                matrix.set(baseMatrix)
                matrix.postScale(now, now, e.x, e.y)
                imageMatrix = matrix
            }
            return old != now
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        setBackgroundColor(android.graphics.Color.BLACK)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { resetToFit() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { resetToFit() }
    }

    private fun resetToFit() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        zoom.reset()
        val s = min(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        val dx = (width - d.intrinsicWidth * s) / 2f
        val dy = (height - d.intrinsicHeight * s) / 2f
        baseMatrix.reset()
        baseMatrix.postScale(s, s)
        baseMatrix.postTranslate(dx, dy)
        matrix.set(baseMatrix)
        imageMatrix = matrix
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        taps.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y }
            MotionEvent.ACTION_MOVE -> if (!scaler.isInProgress && zoom.scale > 1f && e.pointerCount == 1) {
                matrix.postTranslate(e.x - lastX, e.y - lastY)
                imageMatrix = matrix
                lastX = e.x
                lastY = e.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
