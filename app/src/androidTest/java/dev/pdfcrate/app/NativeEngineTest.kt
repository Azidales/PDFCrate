package dev.pdfcrate.app

import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeEngineTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun requireAdvanced() {
        android.util.Log.i("PDFCrateNative", NativePdfSupport.description() + " · extensionS=" + NativePdfSupport.extension)
        assumeTrue("Native PDF module is not available on this OS", NativePdfSupport.advanced)
    }
    private fun output(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)

    @Test fun nativeTextSearchSelectionPasswordAndFormRoundTrip() {
        requireAdvanced()
        val source = nativeFixture(context, encrypted = true)
        val saved = File.createTempFile("native-saved-", ".pdf", context.cacheDir)
        val original = source.readBytes()
        try {
            try { PdfSession(source, "wrong").use { fail("Wrong password was accepted") } }
            catch (_: PdfPasswordRequired) { }
            PdfSession(source, "reader-secret").use { pdf ->
                assertTrue(pdf.advanced)
                assertTrue(pdf.text(0).any { it.text.contains("PDFCrate native text") })
                val hit = pdf.search(0, "native").first().bounds.first()
                val at = Point(hit.centerX().toInt(), hit.centerY().toInt())
                assertTrue(pdf.select(0, at, at).text.contains("native", ignoreCase = true))
                val fields = pdf.fields(0)
                val text = fields.single { it.kind == FieldKind.TEXT && !it.readOnly }
                pdf.setField(0, FieldChange(text.index, text = "Edited by native"))
                val combo = fields.single { it.kind == FieldKind.COMBO }
                pdf.setField(0, FieldChange(combo.index, indices = intArrayOf(1)))
                val checkbox = fields.single { it.kind == FieldKind.CHECKBOX }
                pdf.setField(0, FieldChange(checkbox.index, click = Point(checkbox.bounds.centerX(), checkbox.bounds.centerY())))
                val locked = fields.single { it.readOnly }
                try { pdf.setField(0, FieldChange(locked.index, text = "Not allowed")); fail("Read-only field changed") }
                catch (_: IllegalArgumentException) { }
                pdf.write(output(saved))
            }
            assertArrayEquals(original, source.readBytes())
            PDDocument.load(saved, "reader-secret").use { exported ->
                assertTrue(exported.isEncrypted)
                val form = exported.documentCatalog.acroForm
                assertEquals("Edited by native", form.getField("fullName").valueAsString)
                assertEquals("Portugal", (form.getField("country") as PDComboBox).value.first())
                assertTrue((form.getField("agree") as PDCheckBox).isChecked)
                assertEquals("Do not change", form.getField("locked").valueAsString)
            }
        } finally { source.delete(); saved.delete() }
    }

    @Test fun nativeAnnotationsUndoRedoMoveAndInteroperableExport() {
        requireAdvanced()
        assumeTrue("Annotation extension is not present", NativePdfSupport.annotations)
        val source = nativeFixture(context)
        val saved = File.createTempFile("native-annots-", ".pdf", context.cacheDir)
        val original = source.readBytes()
        try {
            PdfSession(source).use { pdf ->
                val hit = pdf.search(0, "native").first()
                val initial = pdf.annotations(0).size
                pdf.addAnnotation(0, NativeAnnotations.highlight(hit.bounds, 0xFFFFD54F.toInt()))
                assertEquals(initial + 1, pdf.annotations(0).size)
                pdf.undoAnnotation(); assertEquals(initial, pdf.annotations(0).size)
                pdf.redoAnnotation(); assertEquals(initial + 1, pdf.annotations(0).size)
                pdf.addAnnotation(0, NativeAnnotations.note(RectF(300f, 200f, 500f, 240f), "Initial note"))
                val note = pdf.annotations(0).single { it.kind == "Texto" }
                pdf.editNote(0, note.id, "Updated note")
                pdf.moveAnnotation(0, note.id, RectF(280f, 250f, 500f, 300f))
                pdf.addAnnotation(0, NativeAnnotations.ink(listOf(listOf(PointF(60f, 300f), PointF(90f, 320f), PointF(140f, 290f))), RectF(55f, 285f, 145f, 325f)))
                val stamp = pdf.annotations(0).single { it.kind == "Desenho ou assinatura" }
                pdf.moveAnnotation(0, stamp.id, RectF(100f, 350f, 280f, 430f))
                pdf.removeAnnotation(0, note.id); pdf.undoAnnotation()
                assertTrue(pdf.annotations(0).any { it.text == "Updated note" })
                pdf.write(output(saved))
            }
            assertArrayEquals(original, source.readBytes())
            PdfSession(saved).use { assertTrue(it.text(0).any { text -> text.text.contains("PDFCrate") }) }
            PDDocument.load(saved).use { document ->
                val kinds = document.getPage(0).annotations.map { it.subtype }
                assertTrue(kinds.containsAll(listOf("Highlight", "FreeText", "Stamp")))
                assertTrue(document.getPage(0).annotations.any { it.contents == "Updated note" })
            }
        } finally { source.delete(); saved.delete() }
    }

    @Test fun saveOriginalAndReadOnlyRecoveryKeepCompleteDocuments() {
        requireAdvanced()
        val generated = nativeFixture(context)
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        val original = File(directory, "save-transaction.pdf").apply { writeBytes(generated.readBytes()) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", original)
        val transaction = PdfSaveTransaction(context)
        transaction.discard()
        try {
            PdfSession(original).use { pdf ->
                val field = pdf.fields(0).single { it.kind == FieldKind.TEXT && !it.readOnly }
                pdf.setField(0, FieldChange(field.index, text = "Saved original"))
                val result = transaction.save(uri, "Original.pdf", pdf)
                assertNull(result.recovery)
            }
            PDDocument.load(original).use { assertEquals("Saved original", it.documentCatalog.acroForm.getField("fullName").valueAsString) }
            val readOnly = Uri.parse("content://dev.pdfcrate.app.test.source/fixture.pdf")
            val bytes = generated.readBytes()
            context.contentResolver.call(readOnly, "seed", null, Bundle().apply { putByteArray("pdf", bytes) })
            PdfSession(context.contentResolver.openFileDescriptor(readOnly, "r")!!).use { pdf ->
                val field = pdf.fields(0).single { it.kind == FieldKind.TEXT && !it.readOnly }
                pdf.setField(0, FieldChange(field.index, text = "Recovered edits"))
                assertNotNull(transaction.save(readOnly, "Read only.pdf", pdf).recovery)
            }
            assertArrayEquals(bytes, context.contentResolver.call(readOnly, "bytes", null, null)!!.getByteArray("pdf"))
            val pending = transaction.pending()!!
            PdfSession(context.contentResolver.openFileDescriptor(pending.edited, "r")!!).use {
                assertTrue(it.fields(0).any { field -> field.text == "Recovered edits" })
            }
        } finally { transaction.discard(); original.delete(); generated.delete() }
    }
}
