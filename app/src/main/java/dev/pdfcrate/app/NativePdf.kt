package dev.pdfcrate.app

import android.annotation.SuppressLint
import android.graphics.*
import android.graphics.pdf.*
import android.graphics.pdf.component.*
import android.graphics.pdf.content.PdfPageTextContent
import android.graphics.pdf.models.*
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import java.io.Closeable
import java.io.IOException

/** Public PDF APIs only. Older releases load none of the extension-only implementation classes. */
object NativePdfSupport {
    val extension: Int get() = if (Build.VERSION.SDK_INT >= 31) SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) else 0
    val advanced: Boolean get() = Build.VERSION.SDK_INT >= 35 || extension >= 13
    val annotations: Boolean get() = extension >= 18
    fun description() = "Android ${Build.VERSION.RELEASE} · texto/senha/formulários: ${if (advanced) "disponíveis" else "indisponíveis"} · anotações: ${if (annotations) "disponíveis" else "indisponíveis"}"
}

class PdfPasswordRequired : IOException("Senha necessária ou incorreta")
class PdfFeatureUnavailable : IOException("Este recurso nativo não está disponível neste sistema Android.")
data class PdfText(val text: String, val bounds: List<RectF>)
data class PdfHit(val page: Int, val bounds: List<RectF>)
data class PdfSelection(val text: String = "", val bounds: List<RectF> = emptyList())
enum class FieldKind { TEXT, CHECKBOX, RADIO, COMBO, LIST, OTHER }
data class PdfChoice(val label: String, val selected: Boolean)
data class PdfField(val index: Int, val label: String, val kind: FieldKind, val text: String,
    val readOnly: Boolean, val multiline: Boolean, val maxLength: Int, val bounds: Rect,
    val choices: List<PdfChoice>, val multiSelect: Boolean)
data class FieldChange(val index: Int, val text: String? = null, val indices: IntArray? = null, val click: Point? = null)
data class PdfAnnotationEntry(val id: Int, val kind: String, val text: String, val bounds: RectF, internal val raw: Any)

internal class PdfPageAccess(
    val width: Int, val height: Int,
    val draw: (Bitmap) -> Unit,
    val text: () -> List<PdfText> = { throw PdfFeatureUnavailable() },
    val search: (String) -> List<List<RectF>> = { throw PdfFeatureUnavailable() },
    val select: (Point, Point) -> PdfSelection = { _, _ -> throw PdfFeatureUnavailable() },
    val fields: () -> List<PdfField> = { throw PdfFeatureUnavailable() },
    val edit: (FieldChange) -> Unit = { throw PdfFeatureUnavailable() },
    val annotations: () -> List<PdfAnnotationEntry> = { throw PdfFeatureUnavailable() },
    val add: (Any) -> Int = { throw PdfFeatureUnavailable() },
    val remove: (Int) -> Unit = { throw PdfFeatureUnavailable() },
    val update: (Int, Any) -> Boolean = { _, _ -> throw PdfFeatureUnavailable() },
    private val dispose: () -> Unit
) : Closeable { override fun close() = dispose() }

internal interface PdfBackend : Closeable {
    val count: Int
    val advanced: Boolean
    val annotations: Boolean
    fun page(index: Int): PdfPageAccess
    fun write(output: ParcelFileDescriptor) { output.close(); throw PdfFeatureUnavailable() }
}

internal fun openPdfBackend(input: ParcelFileDescriptor, password: String?): PdfBackend {
    try {
        return when {
            Build.VERSION.SDK_INT >= 31 && NativePdfSupport.extension >= 13 -> ExtensionPdfBackend(input, password)
            Build.VERSION.SDK_INT >= 35 -> ModernPdfBackend(input, password)
            else -> BasicPdfBackend(input)
        }
    } catch (failure: SecurityException) {
        if (NativePdfSupport.advanced) throw PdfPasswordRequired()
        throw PdfFeatureUnavailable()
    }
}

private class BasicPdfBackend(input: ParcelFileDescriptor) : PdfBackend {
    private val pdf = PdfRenderer(input)
    override val count get() = pdf.pageCount
    override val advanced = false
    override val annotations = false
    override fun page(index: Int): PdfPageAccess {
        val page = pdf.openPage(index)
        return PdfPageAccess(page.width, page.height,
            draw = { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }, dispose = page::close)
    }
    override fun close() = pdf.close()
}

/** Called only after the joint platform/extension checks in openPdfBackend. */
@SuppressLint("NewApi")
private class ModernPdfBackend(input: ParcelFileDescriptor, password: String?) : PdfBackend {
    private val pdf = PdfRenderer(input, LoadParams.Builder().apply { if (password != null) setPassword(password) }.build())
    override val count get() = pdf.pageCount
    override val advanced = true
    override val annotations = false // Editing uses the explicitly versioned module below.
    override fun page(index: Int): PdfPageAccess {
        val page = pdf.openPage(index)
        return PdfPageAccess(page.width, page.height,
            draw = { page.render(it, null, null, NativePdfMapping.renderParams()) },
            text = { NativePdfMapping.text(page.textContents) },
            search = { query -> page.searchText(query).map { it.bounds.map(::RectF) } },
            select = { from, to -> NativePdfMapping.selection(page.selectContent(SelectionBoundary(from), SelectionBoundary(to))?.selectedTextContents) },
            fields = { page.formWidgetInfos.map(NativePdfMapping::field) },
            edit = { page.applyEdit(NativePdfMapping.edit(index, it)) }, dispose = page::close)
    }
    override fun write(output: ParcelFileDescriptor) = pdf.write(output, false)
    override fun close() = pdf.close()
}

/** framework-pdf extension 13+ also works on Android 12/13/14 when the module is present. */
@SuppressLint("NewApi")
private class ExtensionPdfBackend(input: ParcelFileDescriptor, password: String?) : PdfBackend {
    private val pdf = PdfRendererPreV(input, LoadParams.Builder().apply { if (password != null) setPassword(password) }.build())
    override val count get() = pdf.pageCount
    override val advanced = true
    override val annotations = NativePdfSupport.extension >= 18
    override fun page(index: Int): PdfPageAccess {
        val page = pdf.openPage(index)
        return PdfPageAccess(page.width, page.height,
            draw = { page.render(it, null, null, NativePdfMapping.renderParams()) },
            text = { NativePdfMapping.text(page.textContents) },
            search = { query -> page.searchText(query).map { it.bounds.map(::RectF) } },
            select = { from, to -> NativePdfMapping.selection(page.selectContent(SelectionBoundary(from), SelectionBoundary(to))?.selectedTextContents) },
            fields = { page.formWidgetInfos.map(NativePdfMapping::field) },
            edit = { page.applyEdit(NativePdfMapping.edit(index, it)) },
            annotations = { checkAnnotations(); page.pageAnnotations.map { NativeAnnotations.entry(it.first, it.second) } },
            add = { checkAnnotations(); page.addPageAnnotation(it as PdfAnnotation) },
            remove = { checkAnnotations(); page.removePageAnnotation(it) },
            update = { id, value -> checkAnnotations(); page.updatePageAnnotation(id, value as PdfAnnotation) },
            dispose = page::close)
    }
    private fun checkAnnotations() { if (!annotations) throw PdfFeatureUnavailable() }
    override fun write(output: ParcelFileDescriptor) = pdf.write(output, false)
    override fun close() = pdf.close()
}

@SuppressLint("NewApi")
private object NativePdfMapping {
    fun renderParams() = RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY)
        .setRenderFlags(RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS or RenderParams.FLAG_RENDER_HIGHLIGHT_ANNOTATIONS).build()
    fun text(contents: List<PdfPageTextContent>) = contents.map { PdfText(it.text, it.bounds.map(::RectF)) }
    fun selection(contents: List<PdfPageTextContent>?) = PdfSelection(
        contents?.joinToString("\n") { it.text }.orEmpty(), contents?.flatMap { it.bounds.map(::RectF) }.orEmpty())
    fun field(widget: FormWidgetInfo) = PdfField(widget.widgetIndex,
        widget.accessibilityLabel?.takeIf { it.isNotBlank() } ?: "Campo ${widget.widgetIndex + 1}",
        when (widget.widgetType) {
            FormWidgetInfo.WIDGET_TYPE_TEXTFIELD -> FieldKind.TEXT
            FormWidgetInfo.WIDGET_TYPE_CHECKBOX -> FieldKind.CHECKBOX
            FormWidgetInfo.WIDGET_TYPE_RADIOBUTTON -> FieldKind.RADIO
            FormWidgetInfo.WIDGET_TYPE_COMBOBOX -> FieldKind.COMBO
            FormWidgetInfo.WIDGET_TYPE_LISTBOX -> FieldKind.LIST
            else -> FieldKind.OTHER
        }, widget.textValue.orEmpty(), widget.isReadOnly, widget.isMultiLineText, widget.maxLength,
        Rect(widget.widgetRect), widget.listItems.map { PdfChoice(it.label, it.isSelected) }, widget.isMultiSelect)
    fun edit(page: Int, change: FieldChange): FormEditRecord = FormEditRecord.Builder(page,
        when { change.click != null -> FormEditRecord.EDIT_TYPE_CLICK
            change.indices != null -> FormEditRecord.EDIT_TYPE_SET_INDICES
            else -> FormEditRecord.EDIT_TYPE_SET_TEXT }, change.index).apply {
            change.click?.let(::setClickPoint)
            change.indices?.let(::setSelectedIndices)
            change.text?.let(::setText)
        }.build()
}

/** Annotation models are instantiated only when framework-pdf extension 18+ is available. */
@SuppressLint("NewApi")
internal object NativeAnnotations {
    fun entry(id: Int, value: PdfAnnotation): PdfAnnotationEntry = when (value) {
        is HighlightAnnotation -> PdfAnnotationEntry(id, "Destaque", "", union(value.boundsList), value)
        is FreeTextAnnotation -> PdfAnnotationEntry(id, "Texto", value.textContent, RectF(value.bounds), value)
        is StampAnnotation -> PdfAnnotationEntry(id, "Desenho ou assinatura", "", RectF(value.bounds), value)
        else -> PdfAnnotationEntry(id, "Anotação", "", RectF(), value)
    }
    fun highlight(bounds: List<RectF>, color: Int): Any = HighlightAnnotation(bounds.map(::RectF)).apply { setColor(color) }
    fun changedText(raw: Any, text: String): Any {
        val value = raw as FreeTextAnnotation
        return FreeTextAnnotation(RectF(value.bounds), text).apply {
            setTextColor(value.textColor); setBackgroundColor(value.backgroundColor)
        }
    }
    fun note(bounds: RectF, text: String): Any = FreeTextAnnotation(RectF(bounds), text).apply {
        setTextColor(Color.BLACK); setBackgroundColor(0xFFFFF4B8.toInt())
    }
    fun lines(bounds: List<RectF>, strike: Boolean): Any {
        val rect = union(bounds).apply { inset(-2f, -2f) }
        val path = Path()
        bounds.forEach { val y = if (strike) it.centerY() else it.bottom - 1; path.moveTo(it.left, y); path.lineTo(it.right, y) }
        return stamp(rect, path)
    }
    fun ink(strokes: List<List<PointF>>, bounds: RectF): Any {
        val path = Path()
        strokes.forEach { stroke -> stroke.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        } }
        return stamp(RectF(bounds), path)
    }
    private fun stamp(bounds: RectF, path: Path): Any = StampAnnotation(bounds).apply {
        addObject(PdfPagePathObject(path).apply {
            setRenderMode(PdfPagePathObject.RENDER_MODE_STROKE)
            setStrokeColor(Color.BLACK); setStrokeWidth(1.5f)
        })
    }
    fun recolor(raw: Any, color: Int): Any {
        val value = raw as HighlightAnnotation
        return highlight(value.boundsList, color)
    }
    fun resized(raw: Any, bounds: RectF): Any {
        val old = entry(-1, raw as PdfAnnotation).bounds
        val transform = Matrix().apply { setRectToRect(old, bounds, Matrix.ScaleToFit.FILL) }
        return when (raw) {
            is FreeTextAnnotation -> FreeTextAnnotation(RectF(bounds), raw.textContent).apply {
                setTextColor(raw.textColor); setBackgroundColor(raw.backgroundColor)
            }
            is HighlightAnnotation -> HighlightAnnotation(raw.boundsList.map { RectF(it).also(transform::mapRect) }).apply { setColor(raw.color) }
            is StampAnnotation -> StampAnnotation(RectF(bounds)).apply {
                raw.objects.forEach { original ->
                    val copy: PdfPageObject = when (original) {
                        is PdfPagePathObject -> PdfPagePathObject(original.toPath()).apply {
                            setRenderMode(original.renderMode); setStrokeWidth(original.strokeWidth)
                            setStrokeColor(original.strokeColor); setFillColor(original.fillColor)
                        }
                        is PdfPageImageObject -> PdfPageImageObject(original.bitmap)
                        is PdfPageTextObject -> PdfPageTextObject(original.text, original.font, original.fontSize).apply {
                            setRenderMode(original.renderMode); setStrokeColor(original.strokeColor)
                            setStrokeWidth(original.strokeWidth); setFillColor(original.fillColor)
                        }
                        else -> throw PdfFeatureUnavailable()
                    }
                    copy.setMatrix(Matrix().apply { setValues(original.matrix); postConcat(transform) })
                    addObject(copy)
                }
            }
            else -> throw PdfFeatureUnavailable()
        }
    }
    fun union(rects: List<RectF>): RectF = RectF().also { result -> rects.forEach(result::union) }
}
