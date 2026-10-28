package dev.pdfcrate.app

import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun openNavigateJumpZoomAndReturnToLibrary() {
        compose.onNodeWithText("Experimentar exemplo").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("1 / 3").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Anterior").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Próxima").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Aumentar zoom").performClick()
        compose.onNodeWithContentDescription("Ajustar página").performClick()
        compose.onNodeWithText("2 / 3").performClick()
        compose.onNodeWithText("Página de 1 a 3").performTextInput("3")
        compose.onNodeWithText("Ir", useUnmergedTree = true).performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("3 / 3").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Próxima").assertIsNotEnabled()
        saveScreenshot("reader-page-3.png")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("3 / 3").assertExists()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Página 3 de 3").assertExists()
        saveScreenshot("library.png")
        compose.onNodeWithContentDescription("Opções de Bem-vindo ao PDFCrate.pdf").performClick()
        compose.onNodeWithText("Favoritar").performClick()
        compose.onNodeWithText("Favoritos").performClick()
        compose.onNodeWithText("Bem-vindo ao PDFCrate.pdf").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("3 / 3").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithContentDescription("Opções de Bem-vindo ao PDFCrate.pdf").performClick()
        compose.onNodeWithText("Remover cópia").performClick()
        compose.onNodeWithText("Remover", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Bem-vindo ao PDFCrate.pdf").assertDoesNotExist()
    }

    @Test fun rendererProducesNonblankPagesAndRejectsInvalidInput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = DocumentLibrary(context)
        val sample = library.sample()
        try {
            PdfSession(library.file(sample.id)).use { session ->
                assertEquals(3, session.pageCount)
                val bitmap = session.render(2)
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    assertTrue(pixels.count { Color.red(it) < 128 } > 100)
                    assertTrue(bitmap.width <= 2200 && bitmap.height <= 2200)
                } finally { /* Shared bitmap lifetime belongs to the renderer/cache. */ }
                assertSame("Repeated rendering should reuse the cached page", bitmap, session.render(2))
                try { session.render(3); fail("Out-of-range page was accepted") }
                catch (_: IllegalArgumentException) { }
            }
        } finally { library.file(sample.id).delete() }
        val invalid = File(context.cacheDir, "invalid-reader-test.pdf")
        invalid.writeText("not a PDF")
        try {
            try { PdfSession(invalid).use { fail("Invalid PDF was accepted") } }
            catch (_: java.io.IOException) { }
        } finally { invalid.delete() }
    }

    @Test fun opensPdfFromContentUriAndKeepsAnOfflineCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = DocumentLibrary(context)
        val source = library.sample()
        val sourceFile = library.file(source.id)
        val uri = androidx.core.content.FileProvider.getUriForFile(context,
            "${context.packageName}.files", sourceFile, "Arquivo externo.pdf")
        val activity = compose.activity
        val initialIntent = activity.intent
        try {
            activity.runOnUiThread {
                activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setClass(context, MainActivity::class.java)
                    .setDataAndType(uri, "application/pdf")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("1 / 3").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(sourceFile.delete())
            compose.onNodeWithContentDescription("Próxima").performClick()
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Voltar").performClick()
            compose.onNodeWithText("Arquivo externo.pdf").performClick()
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Voltar").performClick()
            compose.onNodeWithContentDescription("Opções de Arquivo externo.pdf").performClick()
            compose.onNodeWithText("Remover cópia").performClick()
            compose.onNodeWithText("Remover", useUnmergedTree = true).performClick()
        } finally {
            // ActivityScenario identifies lifecycle callbacks by the original launch intent.
            // Restore its identity after testing the real onNewIntent flow, before rule cleanup.
            InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.intent = initialIntent }
            sourceFile.delete()
        }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350) // Let the platform dialog dismissal animation finish.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        try {
            instrumentation.targetContext.externalMediaDirs // Ensure the app media directory exists on older Android.
            val directory = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").absolutePath)
            directory.mkdirs()
            File(directory, name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }
}
