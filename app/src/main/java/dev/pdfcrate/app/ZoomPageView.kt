package dev.pdfcrate.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Path
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
    private var shownIndex = Int.MIN_VALUE
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
    var onSelection: ((Point, Point) -> Unit)? = null
    var onArea: ((RectF) -> Unit)? = null
    var onInk: ((List<List<PointF>>, RectF) -> Unit)? = null
    private var pdfWidth = 1f
    private var pdfHeight = 1f
    private var matches: List<RectF> = emptyList()
    private var selected: List<RectF> = emptyList()
    private var toolMode = PdfTool.NONE
    private var anchor: PointF? = null
    private var cursor: PointF? = null
    private val stroke = mutableListOf<PointF>()
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG)

    fun showOverlays(width: Float, height: Float, hits: List<RectF>, selection: List<RectF>, mode: PdfTool) {
        if (pdfWidth != width || pdfHeight != height || matches != hits || selected != selection || toolMode != mode) {
            pdfWidth = width; pdfHeight = height; matches = hits; selected = selection; toolMode = mode
            if (mode == PdfTool.NONE) { anchor = null; cursor = null; stroke.clear() }
            postInvalidateOnAnimation()
        }
    }
    private fun pdfPoint(x: Float, y: Float): PointF {
        val bitmap = page ?: return PointF()
        val scale = fitScale(bitmap) * zoomFactor
        return PointF((((x - width / 2f - offsetX) / scale + bitmap.width / 2f) * pdfWidth / bitmap.width).coerceIn(0f, pdfWidth),
            (((y - height / 2f - offsetY) / scale + bitmap.height / 2f) * pdfHeight / bitmap.height).coerceIn(0f, pdfHeight))
    }
    private fun point(value: PointF) = Point(value.x.roundToInt(), value.y.roundToInt())


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
            override fun onLongPress(event: MotionEvent) {
                onSelection?.let { callback -> val at = point(pdfPoint(event.x, event.y)); callback(at, at) }
            }
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

    fun show(bitmap: Bitmap?, pageIndex: Int = -1) {
        if (page !== bitmap) {
            stopMotion()
            page = bitmap
            if (shownIndex != pageIndex || bitmap == null) {
                zoomFactor = 1f; offsetX = 0f; offsetY = 0f
            }
            shownIndex = pageIndex
            clamp()
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
        canvas.translate(-bitmap.width / 2f, -bitmap.height / 2f)
        canvas.scale(bitmap.width / pdfWidth, bitmap.height / pdfHeight)
        overlay.style = Paint.Style.FILL
        overlay.color = 0x66FFD54F
        matches.forEach { canvas.drawRect(it, overlay) }
        overlay.color = 0x664A90E2
        selected.forEach { canvas.drawRect(it, overlay) }
        val from = anchor; val to = cursor
        if (from != null && to != null && toolMode != PdfTool.DRAW) {
            overlay.color = 0x44578CCB
            canvas.drawRect(min(from.x, to.x), min(from.y, to.y), max(from.x, to.x), max(from.y, to.y), overlay)
        }
        if (stroke.isNotEmpty()) {
            overlay.style = Paint.Style.STROKE; overlay.strokeWidth = 1.5f; overlay.color = android.graphics.Color.BLACK
            val path = Path()
            stroke.forEachIndexed { index, pt -> if (index == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }
            canvas.drawPath(path, overlay)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (toolMode != PdfTool.NONE) {
            val at = pdfPoint(event.x, event.y)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stopMotion(); anchor = at; cursor = at; stroke.clear(); stroke.add(at)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    cursor = at
                    if (toolMode == PdfTool.DRAW && stroke.size < 12000) stroke.add(at)
                }
                MotionEvent.ACTION_UP -> {
                    val from = anchor ?: at
                    val bounds = RectF(min(from.x, at.x), min(from.y, at.y), max(from.x, at.x), max(from.y, at.y))
                    when (toolMode) {
                        PdfTool.SELECT -> onSelection?.invoke(point(from), point(at))
                        PdfTool.DRAW -> {
                            stroke.add(at)
                            val points = stroke.map { PointF(it.x, it.y) }
                            val box = RectF(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y }).apply { inset(-2f, -2f) }
                            onInk?.invoke(listOf(points), box)
                        }
                        else -> {
                            if (bounds.width() < 15 || bounds.height() < 10) {
                                val w = min(180f, pdfWidth); val h = min(64f, pdfHeight)
                                val left = (from.x - w / 2).coerceIn(0f, pdfWidth - w)
                                val top = (from.y - h / 2).coerceIn(0f, pdfHeight - h)
                                bounds.set(left, top, left + w, top + h)
                            }
                            onArea?.invoke(bounds)
                        }
                    }
                    anchor = null; cursor = null; stroke.clear()
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL -> { anchor = null; cursor = null; stroke.clear() }
            }
            postInvalidateOnAnimation()
            return true
        }
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
