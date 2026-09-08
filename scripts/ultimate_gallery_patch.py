from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

repo = ROOT / 'app/src/main/kotlin/com/sarilacivert/galeri/data/MediaRepository.kt'
text = repo.read_text(encoding='utf-8')
text = text.replace('import android.os.SystemClock\n', '')
text = text.replace('''    @Volatile
    private var cacheBuiltAtMs: Long = 0L

    private val cacheTtlMs = 2_000L

    fun invalidateCache() {
        mediaCache = null
        cacheBuiltAtMs = 0L
    }
''', '''    fun invalidateCache() {
        mediaCache = null
    }
''')
old_cache = '''            val now = SystemClock.elapsedRealtime()
            val cached = mediaCache
            if (cached != null && now - cacheBuiltAtMs <= cacheTtlMs) {
                cached
            } else {
                cacheMutex.withLock {
                    val again = mediaCache
                    val nowInside = SystemClock.elapsedRealtime()
                    if (again != null && nowInside - cacheBuiltAtMs <= cacheTtlMs) {
                        again
                    } else {
                        buildMediaList(false, false).also {
                            mediaCache = it
                            cacheBuiltAtMs = SystemClock.elapsedRealtime()
                        }
                    }
                }
            }
'''
new_cache = '''            val cached = mediaCache
            if (MediaCachePolicy.shouldReuse(cached != null, dirty = false)) {
                cached!!
            } else {
                cacheMutex.withLock {
                    val again = mediaCache
                    if (MediaCachePolicy.shouldReuse(again != null, dirty = false)) {
                        again!!
                    } else {
                        buildMediaList(false, false).also { mediaCache = it }
                    }
                }
            }
'''
if old_cache not in text:
    raise SystemExit('MediaRepository cache block not found')
text = text.replace(old_cache, new_cache)
repo.write_text(text, encoding='utf-8')

view = ROOT / 'app/src/main/kotlin/com/sarilacivert/galeri/ui/StableZoomImageView.kt'
view.write_text(r'''package com.sarilacivert.galeri.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/** Eagle tarzı tek hareket motoru: pinch, pan, swipe ve iki-parmak serbest dönüş. */
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
    private var gestureRotation = 0f
    private var lastRotationAngle = 0f
    private var rotating = false

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var hadMultiTouch = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val swipeThreshold = 92f * resources.displayMetrics.density

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
                val newScale = (oldScale * detector.scaleFactor).coerceIn(1f, 10f)
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
                if (userScale < 1.015f) resetZoomAndPan()
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
                    resetZoomAndPan()
                } else {
                    val target = 2.75f
                    val ratio = target / userScale.coerceAtLeast(0.0001f)
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
        gestureRotation = 0f
        applyRotation()
        resetZoomAndPan()
    }

    fun setExternalRotation(degrees: Float) {
        externalRotation = GestureMath.normalizeRotation(degrees)
        gestureRotation = 0f
        applyRotation()
        resetZoomAndPan()
    }

    fun resetTransform() {
        gestureRotation = 0f
        applyRotation()
        resetZoomAndPan()
    }

    private fun resetZoomAndPan() {
        userScale = 1f
        translateX = 0f
        translateY = 0f
        updateImageMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w / 2f
        pivotY = h / 2f
        applyRotation()
        resetZoomAndPan()
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
                rotating = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                hadMultiTouch = true
                if (event.pointerCount >= 2) {
                    lastRotationAngle = pointerAngle(event)
                    rotating = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val angle = pointerAngle(event)
                    if (rotating) {
                        val delta = GestureMath.shortestAngleDelta(lastRotationAngle, angle)
                        if (delta.isFinite() && abs(delta) <= 45f) {
                            gestureRotation += delta
                            applyRotation()
                        }
                    } else {
                        rotating = true
                    }
                    lastRotationAngle = angle
                } else if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val x = event.x
                    val y = event.y
                    if (userScale > 1.015f) {
                        translateX += x - lastX
                        translateY += y - lastY
                        updateImageMatrix()
                    }
                    lastX = x
                    lastY = y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                rotating = false
            }

            MotionEvent.ACTION_UP -> {
                if (!hadMultiTouch && userScale <= 1.015f) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) >= swipeThreshold && abs(dx) > abs(dy) * 1.2f && abs(dy) > touchSlop / 4f) {
                        if (dx > 0f) onPreviousAction?.invoke() else onNextAction?.invoke()
                    } else if (abs(dx) >= swipeThreshold && abs(dx) > abs(dy) * 1.2f) {
                        if (dx > 0f) onPreviousAction?.invoke() else onNextAction?.invoke()
                    }
                }
                if (userScale <= 1.015f) resetZoomAndPan()
                rotating = false
            }

            MotionEvent.ACTION_CANCEL -> {
                if (userScale <= 1.015f) resetZoomAndPan()
                rotating = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun pointerAngle(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }

    private fun applyRotation() {
        rotation = GestureMath.normalizeRotation(externalRotation + gestureRotation)
        pivotX = width / 2f
        pivotY = height / 2f
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
''', encoding='utf-8')

# Büyük albümlerde küçük resim kuyruğunu kontrollü tut; tam ekran kalite korunur.
bitmap = ROOT / 'app/src/main/kotlin/com/sarilacivert/galeri/data/BitmapLoader.kt'
b = bitmap.read_text(encoding='utf-8')
b = b.replace('private val thumbGate = Semaphore(4)', 'private val thumbGate = Semaphore(3)')
bitmap.write_text(b, encoding='utf-8')

print('Gallery Ultimate patches applied: stable cache + Eagle-style gesture engine + bounded thumbnail decoding')
