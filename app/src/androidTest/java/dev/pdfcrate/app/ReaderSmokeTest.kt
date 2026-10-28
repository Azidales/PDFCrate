package dev.pdfcrate.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun openNavigateJumpZoomAndReturnToRecents() {
        compose.onNodeWithText("Experimentar exemplo").performClick()
        awaitPage("1 / 3")
        compose.onNodeWithContentDescription("Anterior").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Próxima").performClick()
        awaitPage("2 / 3")
        compose.onNodeWithContentDescription("Aumentar zoom").performClick()
        compose.onNodeWithContentDescription("Ajustar página").performClick()
        compose.onNodeWithText("2 / 3").performClick()
        compose.onNodeWithText("Página de 1 a 3").performTextInput("3")
        compose.onNodeWithText("Ir", useUnmergedTree = true).performClick()
        awaitPage("3 / 3")
        compose.onNodeWithContentDescription("Próxima").assertIsNotEnabled()
        saveScreenshot("reader.png")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("3 / 3").assertExists()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Página 3 de 3").assertExists()
        saveScreenshot("recents.png")
        compose.onNodeWithText("Exemplo PDFCrate.pdf").performClick()
        awaitPage("3 / 3")
        compose.onNodeWithContentDescription("Voltar").performClick()
        forget("Exemplo PDFCrate.pdf")
        compose.onNodeWithText("Exemplo PDFCrate.pdf").assertDoesNotExist()
    }

    @Test fun rendererProducesNonblankPagesAndRejectsInvalidInput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = DocumentLibrary(context)
        val sample = library.sample()
        val descriptor = context.contentResolver.openFileDescriptor(library.uri(sample), "r")!!
        PdfSession(descriptor).use { session ->
            assertEquals(3, session.pageCount)
            val bitmap = session.render(2)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(pixels.count { Color.red(it) < 128 } > 100)
            assertTrue(bitmap.width <= 2200 && bitmap.height <= 2200)
            assertSame(bitmap, session.render(2))
            try { session.render(3); fail("Out-of-range page was accepted") }
            catch (_: IllegalArgumentException) { }
        }
        val invalid = File(context.cacheDir, "invalid-reader-test.pdf").apply { writeText("not a PDF") }
        try {
            try { PdfSession(invalid).use { fail("Invalid PDF was accepted") } }
            catch (_: java.io.IOException) { }
        } finally { invalid.delete() }
    }

    @Test fun opensOriginalWithoutCopyAndObservesChangesAndRevocation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Uri.parse("content://dev.pdfcrate.app.test.source/fixture.pdf")
        val resolver = context.contentResolver
        resolver.call(source, "seed", null, Bundle().apply { putByteArray("pdf", fixturePdf(3)) })
        val activity = compose.activity
        val initialIntent = activity.intent
        try {
            activity.runOnUiThread {
                activity.startActivity(Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java)
                    .setDataAndType(source, "application/pdf")
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
            awaitPage("1 / 3")
            val record = DocumentLibrary(context).documents().single { it.uri == source.toString() }
            assertFalse(record.persistent)
            assertEquals(0, File(context.filesDir, "documents").listFiles()?.count { it.isFile } ?: 0)
            compose.onNodeWithContentDescription("Próxima").performClick()
            awaitPage("2 / 3")
            compose.onNodeWithContentDescription("Voltar").performClick()
            compose.onNodeWithText("Acesso temporário").assertExists()
            // An atomic replacement in the PROVIDER must be observed by the next open.
            val changed = fixturePdf(1)
            resolver.call(source, "seed", null, Bundle().apply { putByteArray("pdf", changed) })
            compose.onNodeWithText("Arquivo externo.pdf").performClick()
            awaitPage("1 / 1")
            compose.onNodeWithContentDescription("Voltar").performClick()
            forget("Arquivo externo.pdf")
            assertArrayEquals(changed, resolver.call(source, "bytes", null, null)!!.getByteArray("pdf"))
            activity.runOnUiThread {
                activity.startActivity(Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java)
                    .setDataAndType(source, "application/pdf").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            awaitPage("1 / 1")
            compose.onNodeWithContentDescription("Voltar").performClick()
            resolver.call(source, "delete", null, null)
            compose.onNodeWithText("Arquivo externo.pdf").performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Não foi possível concluir").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Fechar").performClick()
            forget("Arquivo externo.pdf")
            assertEquals(0, File(context.filesDir, "documents").listFiles()?.count { it.isFile } ?: 0)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.intent = initialIntent }
            resolver.call(source, "delete", null, null)
        }
    }

    private fun awaitPage(text: String) {
        try {
            compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            compose.onRoot(useUnmergedTree = true).printToLog("PDFCrateUITest")
            saveScreenshot("failure.png")
            throw failure
        }
    }
    private fun forget(name: String) {
        compose.onNodeWithContentDescription("Opções de $name").performClick()
        compose.onNodeWithText("Remover dos recentes").performClick()
    }
    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        try {
            instrumentation.targetContext.externalMediaDirs
            val directory = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").absolutePath).apply { mkdirs() }
            File(directory, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}

internal fun fixturePdf(pages: Int): ByteArray {
    val pdf = PdfDocument()
    try {
        repeat(pages) { index ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 600, index + 1).create())
            page.canvas.drawText("Provider page ${index + 1}", 30f, 80f, Paint().apply { textSize = 20f })
            pdf.finishPage(page)
        }
        return ByteArrayOutputStream().also { pdf.writeTo(it) }.toByteArray()
    } finally { pdf.close() }
}
