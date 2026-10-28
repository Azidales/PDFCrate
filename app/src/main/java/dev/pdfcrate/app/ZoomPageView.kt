package dev.pdfcrate.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Drawing and animation stay on Android's Choreographer/VSync timeline. No fixed-FPS loop. */
class ZoomPageView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paper = Paint().apply { color = android.graphics.Color.WHITE }
    private var page: Bitmap? = null
    var zoomFactor = 1f
        private set
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var animator: ValueAnimator? = null
    private val fling = OverScroller(context)
    private val margin = 12 * resources.displayMetrics.density
    var onTap: (() -> Unit)? = null
    var onPageSwipe: ((Int) -> Unit)? = null

    private val scales = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                stopMotion()
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                changeZoom((zoomFactor * detector.scaleFactor).coerceIn(1f, 6f),
                    detector.focusX, detector.focusY)
                return true
            }
        })
    private val taps = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean = performClick()
            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (zoomFactor > 1.2f) fit() else zoomBy(2.5f, event.x, event.y)
                return true
            }
            override fun onFling(first: MotionEvent?, last: MotionEvent, vx: Float, vy: Float): Boolean {
                if (zoomFactor <= 1.01f && first != null) {
                    val distance = last.x - first.x
                    if (abs(distance) > width * .18f && abs(distance) > abs(last.y - first.y) * 1.3f) {
                        onPageSwipe?.invoke(if (distance < 0) 1 else -1)
                        return true
                    }
                }
                if (zoomFactor <= 1.01f) return false
                val bitmap = page ?: return false
                val scale = fitScale(bitmap) * zoomFactor
                val maxX = max(0f, (bitmap.width * scale - width) / 2f).roundToInt()
                val maxY = max(0f, (bitmap.height * scale - height) / 2f).roundToInt()
                fling.fling(offsetX.roundToInt(), offsetY.roundToInt(), vx.roundToInt(), vy.roundToInt(),
                    -maxX, maxX, -maxY, maxY)
                postInvalidateOnAnimation()
                return true
            }
        })

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun show(bitmap: Bitmap?) {
        if (page !== bitmap) {
            stopMotion()
            page = bitmap
            zoomFactor = 1f
            offsetX = 0f
            offsetY = 0f
            postInvalidateOnAnimation()
        }
    }

    fun fit() = animateZoom(1f, width / 2f, height / 2f)

    fun zoomBy(factor: Float, focusX: Float = width / 2f, focusY: Float = height / 2f) =
        animateZoom((zoomFactor * factor).coerceIn(1f, 6f), focusX, focusY)

    private fun animateZoom(target: Float, focusX: Float, focusY: Float) {
        stopMotion()
        val start = zoomFactor
        if (abs(start - target) < .001f) return
        animator = ValueAnimator.ofFloat(start, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { changeZoom(it.animatedValue as Float, focusX, focusY) }
            start()
        }
    }

    private fun changeZoom(target: Float, focusX: Float, focusY: Float) {
        val ratio = target / zoomFactor
        offsetX = (offsetX - (focusX - width / 2f)) * ratio + focusX - width / 2f
        offsetY = (offsetY - (focusY - height / 2f)) * ratio + focusY - height / 2f
        zoomFactor = target
        clamp()
        postInvalidateOnAnimation()
    }

    private fun stopMotion() { animator?.cancel(); animator = null; fling.forceFinished(true) }

    private fun fitScale(bitmap: Bitmap) = min(
        max(1f, width - margin * 2) / bitmap.width,
        max(1f, height - margin * 2) / bitmap.height
    )

    private fun clamp() {
        val bitmap = page ?: return
        val scale = fitScale(bitmap) * zoomFactor
        val maxX = max(0f, (bitmap.width * scale - width) / 2f)
        val maxY = max(0f, (bitmap.height * scale - height) / 2f)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    override fun computeScroll() {
        if (fling.computeScrollOffset()) {
            offsetX = fling.currX.toFloat()
            offsetY = fling.currY.toFloat()
            clamp()
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { clamp() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = page ?: return
        val scale = fitScale(bitmap) * zoomFactor
        canvas.save()
        canvas.translate(width / 2f + offsetX, height / 2f + offsetY)
        canvas.scale(scale, scale)
        canvas.drawRect(-bitmap.width / 2f - 1, -bitmap.height / 2f - 1,
            bitmap.width / 2f + 1, bitmap.height / 2f + 1, paper)
        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) stopMotion()
        scales.onTouchEvent(event)
        taps.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scales.isInProgress && event.pointerCount == 1 && zoomFactor > 1f) {
                    offsetX += event.x - lastX
                    offsetY += event.y - lastY
                    clamp()
                    postInvalidateOnAnimation()
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(remaining)
                lastY = event.getY(remaining)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun onDetachedFromWindow() { stopMotion(); super.onDetachedFromWindow() }
    override fun performClick(): Boolean { super.performClick(); onTap?.invoke(); return true }
}
