package dev.pdfcrate.app

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class ReaderFrameTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun zoomUsesHardwareDrawingAndReportsFrameDurations() {
        // Tests may execute with another suite's history, so the About menu always exposes the sample.
        compose.onNodeWithContentDescription("Sobre o PDFCrate").performClick()
        compose.onNodeWithText("Experimentar exemplo").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("1 / 3").fetchSemanticsNodes().isNotEmpty() }
        val activity = compose.activity
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var reader: ZoomPageView
        instrumentation.runOnMainSync {
            reader = findReader(activity.window.decorView)!!
            assertTrue(reader.isHardwareAccelerated)
        }
        val durations = Collections.synchronizedList(mutableListOf<Long>())
        val thread = HandlerThread("frame-test").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
                durations.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
            }
        }
        instrumentation.runOnMainSync { activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        try {
            repeat(6) { iteration ->
                instrumentation.runOnMainSync { reader.zoomBy(if (iteration % 2 == 0) 1.8f else 1f / 1.8f) }
                SystemClock.sleep(300)
            }
            instrumentation.waitForIdleSync()
            val samples = synchronized(durations) { durations.toList().sorted() }
            assertTrue("Animated zoom did not produce enough frames", samples.size >= 10)
            val refresh = activity.windowManager.defaultDisplay.refreshRate
            val budget = 1_000_000_000.0 / refresh
            val report = JSONObject().put("frames", samples.size).put("displayHz", refresh)
                .put("p50Ms", samples[samples.size / 2] / 1_000_000.0)
                .put("p95Ms", samples[((samples.size - 1) * .95).toInt()] / 1_000_000.0)
                .put("overBudget", samples.count { it > budget })
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "PDFCRATE_FRAME_METRICS $report\n") })
            // Emulators are not a device FPS guarantee. Do not turn their timing into a hard 60/120 FPS assertion.
            instrumentation.runOnMainSync { assertEquals(1f, reader.zoomFactor, .02f) }
        } finally {
            instrumentation.runOnMainSync { activity.window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely()
        }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithContentDescription("Opções de Bem-vindo ao PDFCrate.pdf").performClick()
        compose.onNodeWithText("Remover cópia").performClick()
        compose.onNodeWithText("Remover", useUnmergedTree = true).performClick()
    }

    private fun findReader(view: View): ZoomPageView? {
        if (view is ZoomPageView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findReader(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
