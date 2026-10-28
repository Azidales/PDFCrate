package dev.pdfcrate.app

import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Invoked only by test-update.sh, on either side of an actual adb install -r. */
@RunWith(AndroidJUnit4::class)
class UpgradeContinuityTest {
    @Test fun preservesHistoryAcrossPackageUpdate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = DocumentLibrary(context)
        val source = Uri.parse("content://dev.pdfcrate.app.test.source/fixture.pdf")
        when (InstrumentationRegistry.getArguments().getString("stage")) {
            "seed" -> {
                context.contentResolver.call(source, "seed", null, Bundle().apply { putByteArray("pdf", fixturePdf(3)) })
                library.update(library.reference(source).copy(page = 1, pages = 3))
                context.getSharedPreferences("update-probe", 0).edit().putString("token", "preserve-me").commit()
            }
            "verify" -> {
                assertEquals("preserve-me", context.getSharedPreferences("update-probe", 0).getString("token", null))
                val recent = library.documents().single { it.uri == source.toString() }
                assertEquals(1, recent.page)
                PdfSession(context.contentResolver.openFileDescriptor(source, "r")!!).use { assertEquals(3, it.pageCount) }
                library.remove(recent)
            }
            else -> throw IllegalArgumentException("The update test requires a seed or verify stage")
        }
    }
}
