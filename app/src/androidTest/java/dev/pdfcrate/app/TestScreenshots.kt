package dev.pdfcrate.app

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Saves a device screenshot into the instrumentation's additional output (collected by CI). */
internal fun ComposeTestRule.saveScreenshot(name: String) {
    waitForIdle()
    SystemClock.sleep(350)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
    try {
        val directory = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").absolutePath).apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally { bitmap.recycle() }
}
