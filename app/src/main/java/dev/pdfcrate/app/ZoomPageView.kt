package dev.pdfcrate.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class ZoomPageView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var page: Bitmap? = null
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val scales = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        })
    private val taps = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                performClick()
                return true
            }
            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (zoom > 1.2f) fit() else zoomBy(2.5f, event.x, event.y)
                return true
            }
        })

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun show(bitmap: Bitmap?) {
        if (page !== bitmap) {
            page = bitmap
            fit()
        }
    }

    fun fit() {
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    fun zoomBy(factor: Float, focusX: Float = width / 2f, focusY: Float = height / 2f) {
        val newZoom = (zoom * factor).coerceIn(1f, 6f)
        val ratio = newZoom / zoom
        offsetX = (offsetX - (focusX - width / 2f)) * ratio + focusX - width / 2f
        offsetY = (offsetY - (focusY - height / 2f)) * ratio + focusY - height / 2f
        zoom = newZoom
        clamp()
        invalidate()
    }

    private fun fitScale(bitmap: Bitmap) = min(width.toFloat() / bitmap.width,
        height.toFloat() / bitmap.height)

    private fun clamp() {
        val bitmap = page ?: return
        val scale = fitScale(bitmap) * zoom
        val maxX = max(0f, (bitmap.width * scale - width) / 2f)
        val maxY = max(0f, (bitmap.height * scale - height) / 2f)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { clamp() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = page ?: return
        val scale = fitScale(bitmap) * zoom
        canvas.save()
        canvas.translate(width / 2f + offsetX, height / 2f + offsetY)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scales.onTouchEvent(event)
        taps.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scales.isInProgress && event.pointerCount == 1) {
                    offsetX += event.x - lastX
                    offsetY += event.y - lastY
                    clamp()
                    invalidate()
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

    override fun performClick(): Boolean { super.performClick(); return true }
}
