package dev.pdfcrate.app.reader

import kotlin.math.max

/**
 * Vertical layout of every page fitted to the view width, in pixels at zoom 1.
 * Pure arithmetic so it can be unit-tested; [widths] and [heights] are PDF page sizes in points.
 */
internal class ContinuousLayout(widths: FloatArray, heights: FloatArray, viewWidth: Float,
                                val margin: Float, val gap: Float) {
    val count = widths.size
    val contentWidth = max(1f, viewWidth - 2 * margin)
    val tops = FloatArray(count)
    val pageHeights = FloatArray(count)
    val totalHeight: Float

    init {
        require(heights.size == count)
        var y = margin
        for (index in 0 until count) {
            tops[index] = y
            pageHeights[index] = contentWidth * max(1f, heights[index]) / max(1f, widths[index])
            y += pageHeights[index] + if (index < count - 1) gap else 0f
        }
        totalHeight = y + margin
    }

    fun bottom(index: Int) = tops[index] + pageHeights[index]

    /** The page at layout height [y]; a point in a gap or margin belongs to the nearest page. */
    fun pageAt(y: Float): Int {
        if (count == 0) return -1
        var low = 0
        var high = count - 1
        while (low < high) { // Last page whose top is at or above y.
            val mid = (low + high + 1) / 2
            if (tops[mid] <= y) low = mid else high = mid - 1
        }
        return if (low < count - 1 && y > bottom(low) + gap / 2) low + 1 else low
    }

    /** Pages intersecting the layout band [top, bottom]. */
    fun visible(top: Float, bottom: Float): IntRange {
        if (count == 0) return IntRange.EMPTY
        var first = pageAt(top)
        if (first > 0 && bottom(first - 1) >= top) first -= 1
        var last = first
        while (last + 1 < count && tops[last + 1] <= bottom) last++
        return first..last
    }

    /**
     * The page a reader is on: the one under the middle of the viewport, except at the very
     * start or end of the document, where the first or last page is reported.
     */
    fun currentPage(top: Float, bottom: Float, atStart: Boolean, atEnd: Boolean): Int = when {
        count == 0 -> -1
        atStart -> visible(top, bottom).first
        atEnd -> count - 1
        else -> pageAt((top + bottom) / 2)
    }
}
