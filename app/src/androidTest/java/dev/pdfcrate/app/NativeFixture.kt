package dev.pdfcrate.app

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.*
import java.io.File

internal fun nativeFixture(context: Context, encrypted: Boolean = false): File {
    PDFBoxResourceLoader.init(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context)
    val file = File.createTempFile("native-fixture-", ".pdf", context.cacheDir)
    PDDocument().use { document ->
        val page = PDPage(PDRectangle.A4); document.addPage(page)
        PDPageContentStream(document, page).use {
            it.beginText(); it.setFont(PDType1Font.HELVETICA, 18f); it.newLineAtOffset(50f, 700f)
            it.showText("PDFCrate native text"); it.endText()
        }
        val form = PDAcroForm(document); document.documentCatalog.acroForm = form
        form.defaultResources = PDResources().apply { put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA) }
        form.defaultAppearance = "/Helv 12 Tf 0 g"
        fun place(field: PDField, rect: PDRectangle) {
            form.fields.add(field)
            field.widgets.first().apply { rectangle = rect; setPage(page) }
            page.annotations.add(field.widgets.first())
        }
        val text = PDTextField(form).apply { partialName = "fullName"; alternateFieldName = "Full name" }
        place(text, PDRectangle(50f, 600f, 220f, 30f)); text.value = "Initial value"
        val locked = PDTextField(form).apply { partialName = "locked"; alternateFieldName = "Locked value" }
        place(locked, PDRectangle(50f, 550f, 220f, 30f)); locked.value = "Do not change"; locked.isReadOnly = true
        val combo = PDComboBox(form).apply { partialName = "country"; alternateFieldName = "Country"; setOptions(listOf("Brasil", "Portugal")) }
        place(combo, PDRectangle(50f, 500f, 220f, 30f)); combo.setValue("Brasil")
        val checkbox = PDCheckBox(form).apply { partialName = "agree"; alternateFieldName = "Agree" }
        place(checkbox, PDRectangle(50f, 450f, 18f, 18f))
        val states = COSDictionary()
        for (checked in listOf(false, true)) {
            val stream = PDAppearanceStream(document).apply { resources = PDResources(); bBox = PDRectangle(18f, 18f) }
            PDPageContentStream(document, stream).use {
                it.setStrokingColor(0f); it.addRect(1f, 1f, 16f, 16f); it.stroke()
                if (checked) { it.moveTo(4f, 8f); it.lineTo(7f, 4f); it.lineTo(14f, 14f); it.stroke() }
            }
            states.setItem(COSName.getPDFName(if (checked) "Yes" else "Off"), stream)
        }
        checkbox.widgets.first().appearance = PDAppearanceDictionary().apply { cosObject.setItem(COSName.N, states) }
        checkbox.unCheck()
        if (encrypted) document.protect(StandardProtectionPolicy("owner-secret", "reader-secret", AccessPermission()).apply {
            setEncryptionKeyLength(128); setPreferAES(true)
        })
        document.save(file)
    }
    return file
}
