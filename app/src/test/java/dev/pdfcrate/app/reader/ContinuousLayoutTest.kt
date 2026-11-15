package dev.pdfcrate.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousLayoutTest {
    private val a4 = 595f to 842f
    // View 1000 px wide, 10 px margin, 8 px gap: each A4 page is 980 px wide and ~1386.8 px tall.
    private fun layout(vararg pages: Pair<Float, Float>) =
        ContinuousLayout(FloatArray(pages.size) { pages[it].first }, FloatArray(pages.size) { pages[it].second }, 1000f, 10f, 8f)

    @Test fun pagesAreStackedAtTheViewWidth() {
        val l = layout(a4, a4, a4)
        val height = 980f * 842f / 595f
        assertEquals(980f, l.contentWidth, .01f)
        assertEquals(10f, l.tops[0], .01f)
        assertEquals(10f + height + 8f, l.tops[1], .01f)
        assertEquals(10f + 3 * height + 2 * 8f + 10f, l.totalHeight, .01f)
    }

    @Test fun pointsInGapsBelongToTheNearestPage() {
        val l = layout(a4, a4, a4)
        assertEquals(0, l.pageAt(0f))
        assertEquals(0, l.pageAt(l.bottom(0) + 3f))
        assertEquals(1, l.pageAt(l.bottom(0) + 5f))
        assertEquals(2, l.pageAt(l.totalHeight + 100f))
    }

    @Test fun visibleRangeCoversEveryIntersectingPage() {
        val l = layout(a4, a4, a4)
        assertEquals(0..1, l.visible(l.bottom(0) - 50f, l.tops[1] + 50f))
        assertEquals(1..1, l.visible(l.tops[1] + 10f, l.bottom(1) - 10f))
        assertEquals(0..2, l.visible(0f, l.totalHeight))
    }

    @Test fun currentPageFollowsTheViewportCentreAndDocumentEnds() {
        val l = layout(a4, a4, a4)
        assertEquals(0, l.currentPage(0f, 800f, atStart = true, atEnd = false))
        assertEquals(1, l.currentPage(l.tops[1] - 100f, l.tops[1] + 700f, atStart = false, atEnd = false))
        assertEquals(2, l.currentPage(l.totalHeight - 800f, l.totalHeight, atStart = false, atEnd = true))
    }

    @Test fun mixedPageSizesKeepTheirAspectRatio() {
        val l = layout(a4, 842f to 595f)
        assertEquals(980f * 595f / 842f, l.pageHeights[1], .01f)
        assertTrue(l.pageHeights[0] > l.pageHeights[1])
    }

    @Test fun emptyDocumentHasNoPages() {
        val l = ContinuousLayout(FloatArray(0), FloatArray(0), 1000f, 10f, 8f)
        assertEquals(-1, l.pageAt(10f))
        assertTrue(l.visible(0f, 100f).isEmpty())
    }
}
