package dev.pdfcrate.app.reader

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.util.SizeF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.graphics.withTranslation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Zoom controls shared by the single-page and continuous views. */
interface PageViewport {
    val zoomFactor: Float
    fun zoomBy(factor: Float)
    fun fit()
}

/** A navigation request from the model: show [page], centred on [focus] (PDF points) when given. */
data class ScrollTarget(val page: Int, val focus: RectF?, val id: Long)

/**
 * All pages stacked vertically and fitted to the width. Zoom (1x to 6x) applies to the whole
 * document. Bitmaps arrive asynchronously through [setImages]; the view asks for the pages it
 * shows, plus one on each side at normal zoom, with [onRequestPages]. Drawing and animation
 * follow Choreographer/VSync, like [ZoomPageView].
 */
class ContinuousPageView(context: Context) : View(context), PageViewport {
    private val density = resources.displayMetrics.density
    private val margin = 12 * density
    private val gap = 8 * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paper = Paint().apply { color = Color.WHITE }
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inkPreview = Path()
    private val pageRect = RectF()

    private var lastSizes: List<SizeF>? = null
    private var widths = FloatArray(0)
    private var heights = FloatArray(0)
    private var layout: ContinuousLayout? = null
    private var images: Map<Int, Bitmap> = emptyMap()
    override var zoomFactor = 1f
        private set
    /** Scroll position in zoomed pixels; negative Y centres a document shorter than the view. */
    private var scrollPosX = 0f
    private var scrollPosY = 0f
    private val fling = OverScroller(context)
    private var animator: ValueAnimator? = null
    private var scaledThisGesture = false

    var onTap: (() -> Unit)? = null
    var onPageChanged: ((Int) -> Unit)? = null
    var onRequestPages: ((IntRange, Int) -> Unit)? = null
    var onSelection: ((Int, Point, Point) -> Unit)? = null
    var onArea: ((Int, RectF) -> Unit)? = null
    var onInk: ((Int, List<List<PointF>>, RectF) -> Unit)? = null

    private var reportedPage = 0
    private var pendingTarget: ScrollTarget? = null
    private var lastTargetId = Long.MIN_VALUE
    private var requestedRange = IntRange.EMPTY
    private var requestedWidth = 0

    private var hitPage = -1
    private var hitBounds: List<RectF> = emptyList()
    private var selectionPage = -1
    private var selectionBounds: List<RectF> = emptyList()
    private var toolMode = PdfTool.NONE
    private var lockedToolPage = -1
    private var toolPage = -1
    private var anchor: PointF? = null
    private var cursor: PointF? = null
    private val stroke = mutableListOf<PointF>()

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Opens at [page]; navigation requests up to [knownTargetId] were for an earlier view. */
    fun startAt(page: Int, knownTargetId: Long) {
        lastTargetId = knownTargetId
        reportedPage = page
        pendingTarget = ScrollTarget(page, null, knownTargetId)
        applyPendingTarget()
    }

    fun setPages(sizes: List<SizeF>) {
        if (sizes === lastSizes) return
        lastSizes = sizes
        if (sizes.size == widths.size && sizes.indices.all { sizes[it].width == widths[it] && sizes[it].height == heights[it] }) return
        val keep = anchor()
        widths = FloatArray(sizes.size) { sizes[it].width }
        heights = FloatArray(sizes.size) { sizes[it].height }
        rebuild(keep)
    }

    fun setImages(pages: Map<Int, Bitmap>) {
        if (images !== pages) { images = pages; postInvalidateOnAnimation() }
    }

    fun scrollTo(target: ScrollTarget?) {
        if (target == null || target.id == lastTargetId) return
        lastTargetId = target.id
        pendingTarget = target
        applyPendingTarget()
    }

    fun showOverlays(hitPage: Int, hits: List<RectF>, selectionPage: Int, selection: List<RectF>, mode: PdfTool, lockedPage: Int) {
        if (this.hitPage != hitPage || hitBounds != hits || this.selectionPage != selectionPage ||
            selectionBounds != selection || toolMode != mode || lockedToolPage != lockedPage) {
            this.hitPage = hitPage; hitBounds = hits; this.selectionPage = selectionPage; selectionBounds = selection
            toolMode = mode; lockedToolPage = lockedPage
            if (mode == PdfTool.NONE) clearToolInput()
            postInvalidateOnAnimation()
        }
    }

    override fun fit() = animateZoom(1f, width / 2f, height / 2f)
    override fun zoomBy(factor: Float) = animateZoom((zoomFactor * factor).coerceIn(MIN_ZOOM, MAX_ZOOM), width / 2f, height / 2f)

    private fun rebuild(keep: Pair<Int, Float>?) {
        layout = if (width > 0 && widths.isNotEmpty()) ContinuousLayout(widths, heights, width.toFloat(), margin, gap) else null
        if (keep != null) restore(keep) else clamp()
        applyPendingTarget()
        updateVisible(report = false)
        postInvalidateOnAnimation()
    }

    /** Page at the top of the viewport and how far into it, to keep the reading position on relayout. */
    private fun anchor(): Pair<Int, Float>? {
        val current = layout ?: return null
        if (current.count == 0) return null
        val top = max(0f, scrollPosY) / zoomFactor
        val page = current.pageAt(top)
        return page to (top - current.tops[page]) / current.pageHeights[page]
    }

    private fun restore(keep: Pair<Int, Float>) {
        val current = layout ?: return
        val page = keep.first.coerceIn(0, current.count - 1)
        scrollPosY = (current.tops[page] + keep.second * current.pageHeights[page]) * zoomFactor
        clamp()
    }

    private fun applyPendingTarget() {
        val current = layout ?: return
        val target = pendingTarget ?: return
        if (height == 0 || current.count == 0) return
        pendingTarget = null
        stopMotion()
        val page = target.page.coerceIn(0, current.count - 1)
        val focus = target.focus
        if (focus != null && focus.width() >= 0 && widths[page] > 0) {
            val x = current.margin + focus.centerX() / widths[page] * current.contentWidth
            val y = current.tops[page] + focus.centerY() / heights[page] * current.pageHeights[page]
            scrollPosX = x * zoomFactor - width / 2f
            scrollPosY = y * zoomFactor - height / 2f
        } else scrollPosY = (current.tops[page] - margin) * zoomFactor
        clamp()
        reportedPage = page
        updateVisible(report = false)
        postInvalidateOnAnimation()
    }

    private fun clamp() {
        val current = layout ?: return
        scrollPosX = scrollPosX.coerceIn(0f, max(0f, width * zoomFactor - width))
        val contentHeight = current.totalHeight * zoomFactor
        scrollPosY = if (contentHeight <= height) -(height - contentHeight) / 2f
            else scrollPosY.coerceIn(0f, contentHeight - height)
    }

    /** Reports the current page after user scrolling and asks for the bitmaps now needed. */
    private fun updateVisible(report: Boolean) {
        val current = layout ?: return
        if (current.count == 0 || height == 0) return
        val top = scrollPosY / zoomFactor
        val bottom = (scrollPosY + height) / zoomFactor
        val visible = current.visible(top, bottom)
        if (report) {
            val maxY = current.totalHeight * zoomFactor - height
            val page = current.currentPage(top, bottom, atStart = scrollPosY <= 0.5f, atEnd = maxY > 0 && scrollPosY >= maxY - 0.5f)
            if (page >= 0 && page != reportedPage) { reportedPage = page; onPageChanged?.invoke(page) }
        }
        // Higher zoom renders sharper bitmaps, so only the visible pages are kept then.
        val sharp = zoomFactor >= SHARP_ZOOM
        val extra = if (sharp) 0 else 1
        val range = max(0, visible.first - extra)..min(current.count - 1, visible.last + extra)
        val target = current.contentWidth * if (sharp) 2f else 1f
        val renderWidth = max(WIDTH_STEP, (target / WIDTH_STEP).roundToInt() * WIDTH_STEP)
        if (range != requestedRange || renderWidth != requestedWidth) {
            requestedRange = range; requestedWidth = renderWidth
            onRequestPages?.invoke(range, renderWidth)
        }
    }

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
        val contentX = (focusX + scrollPosX) / zoomFactor
        val contentY = (focusY + scrollPosY) / zoomFactor
        zoomFactor = target
        scrollPosX = contentX * target - focusX
        scrollPosY = contentY * target - focusY
        clamp()
        updateVisible(report = true)
        postInvalidateOnAnimation()
    }

    private fun stopMotion() { animator?.cancel(); animator = null; fling.forceFinished(true) }

    private val scales = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { stopMotion(); scaledThisGesture = true; return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            changeZoom((zoomFactor * detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM), detector.focusX, detector.focusY)
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean = performClick()
        override fun onLongPress(event: MotionEvent) {
            val callback = onSelection ?: return
            val page = pageAtScreen(event.y)
            if (page < 0) return
            val at = point(pdfPoint(page, event.x, event.y))
            callback(page, at, at)
        }
        override fun onDoubleTap(event: MotionEvent): Boolean {
            if (zoomFactor > 1.2f) fit() else animateZoom(DOUBLE_TAP_ZOOM, event.x, event.y)
            return true
        }
        override fun onScroll(first: MotionEvent?, last: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scales.isInProgress || last.pointerCount > 1) return false
            scrollPosX += dx; scrollPosY += dy
            clamp()
            updateVisible(report = true)
            postInvalidateOnAnimation()
            return true
        }
        override fun onFling(first: MotionEvent?, last: MotionEvent, vx: Float, vy: Float): Boolean {
            val current = layout ?: return false
            if (scaledThisGesture) return false
            val maxX = max(0f, width * zoomFactor - width).roundToInt()
            val contentHeight = current.totalHeight * zoomFactor
            val minY = if (contentHeight <= height) scrollPosY.roundToInt() else 0
            val maxY = if (contentHeight <= height) scrollPosY.roundToInt() else (contentHeight - height).roundToInt()
            fling.fling(scrollPosX.roundToInt(), scrollPosY.roundToInt(), -vx.roundToInt(), -vy.roundToInt(), 0, maxX, minY, maxY)
            postInvalidateOnAnimation()
            return true
        }
    })

    override fun computeScroll() {
        if (fling.computeScrollOffset()) {
            scrollPosX = fling.currX.toFloat()
            scrollPosY = fling.currY.toFloat()
            clamp()
            updateVisible(report = true)
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w != oldw) {
            val keep = anchor()
            scrollPosX = if (oldw > 0) scrollPosX * w / oldw else 0f
            rebuild(keep)
        } else {
            clamp()
            applyPendingTarget()
            updateVisible(report = false)
        }
    }

    private fun pageAtScreen(y: Float): Int = layout?.pageAt((y + scrollPosY) / zoomFactor) ?: -1

    /** Converts a view position to PDF points on [page], clamped to the page. */
    private fun pdfPoint(page: Int, x: Float, y: Float): PointF {
        val current = layout ?: return PointF()
        val layoutX = (x + scrollPosX) / zoomFactor
        val layoutY = (y + scrollPosY) / zoomFactor
        return PointF(((layoutX - current.margin) / current.contentWidth * widths[page]).coerceIn(0f, widths[page]),
            ((layoutY - current.tops[page]) / current.pageHeights[page] * heights[page]).coerceIn(0f, heights[page]))
    }

    private fun point(value: PointF) = Point(value.x.roundToInt(), value.y.roundToInt())

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = layout ?: return
        val top = scrollPosY / zoomFactor
        val bottom = (scrollPosY + height) / zoomFactor
        canvas.withTranslation(-scrollPosX, -scrollPosY) {
            scale(zoomFactor, zoomFactor)
            for (page in current.visible(top, bottom)) {
                pageRect.set(current.margin, current.tops[page], current.margin + current.contentWidth, current.bottom(page))
                drawRect(pageRect, paper)
                images[page]?.let { drawBitmap(it, null, pageRect, paint) }
                if (page == hitPage || page == selectionPage || page == toolPage) {
                    withTranslation(pageRect.left, pageRect.top) {
                        scale(current.contentWidth / widths[page], current.pageHeights[page] / heights[page])
                        drawOverlays(this, page)
                    }
                }
            }
        }
    }

    private fun drawOverlays(canvas: Canvas, page: Int) {
        overlay.style = Paint.Style.FILL
        if (page == hitPage) { overlay.color = 0x66FFD54F; hitBounds.forEach { canvas.drawRect(it, overlay) } }
        if (page == selectionPage) { overlay.color = 0x664A90E2; selectionBounds.forEach { canvas.drawRect(it, overlay) } }
        if (page != toolPage) return
        val from = anchor
        val to = cursor
        if (from != null && to != null && toolMode != PdfTool.DRAW) {
            overlay.color = 0x44578CCB
            canvas.drawRect(min(from.x, to.x), min(from.y, to.y), max(from.x, to.x), max(from.y, to.y), overlay)
        }
        if (stroke.isNotEmpty()) {
            overlay.style = Paint.Style.STROKE; overlay.strokeWidth = 1.5f; overlay.color = Color.BLACK
            inkPreview.rewind()
            stroke.forEachIndexed { index, pt -> if (index == 0) inkPreview.moveTo(pt.x, pt.y) else inkPreview.lineTo(pt.x, pt.y) }
            canvas.drawPath(inkPreview, overlay)
        }
    }

    private fun clearToolInput() { anchor = null; cursor = null; stroke.clear(); toolPage = -1 }

    // Single taps reach performClick() through the GestureDetector's onSingleTapConfirmed.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (toolMode != PdfTool.NONE) return handleTool(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            stopMotion(); scaledThisGesture = false
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        scales.onTouchEvent(event)
        gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
            parent?.requestDisallowInterceptTouchEvent(false)
        return true
    }

    /** Tool input goes to the page under the first touch (or the locked page when moving an annotation). */
    private fun handleTool(event: MotionEvent): Boolean {
        val current = layout ?: return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            stopMotion()
            toolPage = if (lockedToolPage in 0 until current.count) lockedToolPage else pageAtScreen(event.y)
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val page = toolPage
        if (page < 0) return true
        val at = pdfPoint(page, event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { anchor = at; cursor = at; stroke.clear(); stroke.add(at) }
            MotionEvent.ACTION_MOVE -> {
                cursor = at
                if (toolMode == PdfTool.DRAW && stroke.size < MAX_STROKE_POINTS) stroke.add(at)
            }
            MotionEvent.ACTION_UP -> {
                val from = anchor ?: at
                val bounds = RectF(min(from.x, at.x), min(from.y, at.y), max(from.x, at.x), max(from.y, at.y))
                when (toolMode) {
                    PdfTool.SELECT -> onSelection?.invoke(page, point(from), point(at))
                    PdfTool.DRAW -> {
                        stroke.add(at)
                        val points = stroke.map { PointF(it.x, it.y) }
                        val box = RectF(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
                            .apply { inset(-2f, -2f) }
                        onInk?.invoke(page, listOf(points), box)
                    }
                    else -> {
                        if (bounds.width() < 15 || bounds.height() < 10) {
                            val w = min(180f, widths[page]); val h = min(64f, heights[page])
                            val left = (from.x - w / 2).coerceIn(0f, widths[page] - w)
                            val topEdge = (from.y - h / 2).coerceIn(0f, heights[page] - h)
                            bounds.set(left, topEdge, left + w, topEdge + h)
                        }
                        onArea?.invoke(page, bounds)
                    }
                }
                clearToolInput()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> clearToolInput()
        }
        postInvalidateOnAnimation()
        return true
    }

    override fun onDetachedFromWindow() { stopMotion(); super.onDetachedFromWindow() }
    override fun performClick(): Boolean { super.performClick(); onTap?.invoke(); return true }

    private companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 6f
        const val DOUBLE_TAP_ZOOM = 2.5f
        const val SHARP_ZOOM = 1.5f
        const val WIDTH_STEP = 64
        const val MAX_STROKE_POINTS = 12_000
    }
}
