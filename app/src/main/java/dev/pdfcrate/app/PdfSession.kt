package dev.pdfcrate.app

import android.graphics.*
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.LruCache
import android.util.SparseArray
import android.util.SizeF
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

class PdfSession(private val descriptor: ParcelFileDescriptor, password: String? = null) : Closeable {
    constructor(file: File, password: String? = null) : this(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), password)
    data class SourceStamp(val size: Long, val modified: Long, val inode: Long)
    val sourceStamp: SourceStamp?
    private val backend: PdfBackend
    private var closed = false
    private val sizes = SparseArray<SizeF>()
    private val undo = mutableListOf<AnnotationChange>()
    private val redo = mutableListOf<AnnotationChange>()
    private var formsChanged = false
    private val cache = object : LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(8L * 1024 * 1024, 24L * 1024 * 1024).toInt()
    ) { override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount }

    init {
        try {
            sourceStamp = runCatching { Os.fstat(descriptor.fileDescriptor).let { SourceStamp(it.st_size, it.st_mtime, it.st_ino) } }.getOrNull()
            val header = ByteArray(1024)
            val read = Os.pread(descriptor.fileDescriptor, header, 0, header.size, 0)
            if (read <= 0 || !String(header, 0, read, Charsets.ISO_8859_1).contains("%PDF-"))
                throw IOException("O arquivo não tem um cabeçalho PDF válido.")
            backend = openPdfBackend(descriptor, password)
        } catch (failure: Throwable) { descriptor.close(); throw failure }
    }
    val pageCount get() = backend.count
    val advanced get() = backend.advanced
    val canAnnotate get() = backend.annotations
    @Synchronized fun hasChanges() = formsChanged || undo.isNotEmpty()
    @Synchronized fun canUndo() = undo.isNotEmpty()
    @Synchronized fun canRedo() = redo.isNotEmpty()

    @Synchronized fun render(index: Int): Bitmap {
        check(!closed)
        require(index in 0 until pageCount)
        cache.get(index)?.let { return it }
        backend.page(index).use { page ->
            require(page.width > 0 && page.height > 0)
            sizes.put(index, SizeF(page.width.toFloat(), page.height.toFloat()))
            val scale = 2200f / max(page.width, page.height)
            val bitmap = Bitmap.createBitmap(max(1, (page.width * scale).roundToInt()),
                max(1, (page.height * scale).roundToInt()), Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.draw(bitmap)
                bitmap.prepareToDraw()
                cache.put(index, bitmap)
                return bitmap
            } catch (failure: Throwable) { bitmap.recycle(); throw failure }
        }
    }
    @Synchronized fun dimensions(index: Int): SizeF {
        sizes[index]?.let { return it }
        return backend.page(index).use { SizeF(it.width.toFloat(), it.height.toFloat()).also { size -> sizes.put(index, size) } }
    }
    @Synchronized fun text(index: Int): List<PdfText> = backend.page(index).use { it.text() }
    @Synchronized fun search(index: Int, query: String): List<PdfHit> =
        backend.page(index).use { it.search(query).map { bounds -> PdfHit(index, bounds) } }
    @Synchronized fun select(index: Int, from: Point, to: Point): PdfSelection = backend.page(index).use { it.select(from, to) }
    @Synchronized fun fields(index: Int): List<PdfField> = backend.page(index).use { it.fields() }
    @Synchronized fun setField(index: Int, change: FieldChange) {
        backend.page(index).use { page ->
            require(page.fields().first { it.index == change.index }.readOnly.not()) { "Campo somente leitura." }
            page.edit(change)
        }
        formsChanged = true
        cache.evictAll()
    }
    @Synchronized fun annotations(index: Int): List<PdfAnnotationEntry> = backend.page(index).use { it.annotations() }
    @Synchronized fun addAnnotation(index: Int, raw: Any) {
        check(canAnnotate)
        val id = backend.page(index).use { it.add(raw) }
        check(id >= 0) { "Não foi possível adicionar a anotação." }
        undo.add(AnnotationChange(index, id, null, raw)); redo.clear(); cache.evictAll()
    }
    @Synchronized fun removeAnnotation(index: Int, id: Int) {
        backend.page(index).use { page ->
            val before = page.annotations().first { it.id == id }.raw
            page.remove(id)
            undo.add(AnnotationChange(index, id, before, null))
        }
        redo.clear(); cache.evictAll()
    }
    @Synchronized fun editNote(index: Int, id: Int, text: String) {
        backend.page(index).use { page ->
            val before = page.annotations().first { it.id == id }
            require(before.kind == "Texto")
            val after = NativeAnnotations.changedText(before.raw, text)
            check(page.update(id, after)) { "Não foi possível alterar a anotação." }
            undo.add(AnnotationChange(index, id, before.raw, after))
        }
        redo.clear(); cache.evictAll()
    }
    @Synchronized fun moveAnnotation(index: Int, id: Int, bounds: RectF) = changeAnnotation(index, id) { NativeAnnotations.resized(it, bounds) }
    @Synchronized fun recolorAnnotation(index: Int, id: Int, color: Int) = changeAnnotation(index, id) { NativeAnnotations.recolor(it, color) }
    private fun changeAnnotation(index: Int, id: Int, transform: (Any) -> Any) {
        backend.page(index).use { page ->
            val before = page.annotations().first { it.id == id }.raw
            val after = transform(before)
            check(page.update(id, after))
            undo.add(AnnotationChange(index, id, before, after))
        }
        redo.clear(); cache.evictAll()
    }
    @Synchronized fun undoAnnotation() {
        if (undo.isEmpty()) return
        val change = undo.last()
        apply(change, false); undo.removeAt(undo.lastIndex); redo.add(change); cache.evictAll()
    }
    @Synchronized fun redoAnnotation() {
        if (redo.isEmpty()) return
        val change = redo.last()
        apply(change, true); redo.removeAt(redo.lastIndex); undo.add(change); cache.evictAll()
    }
    private fun apply(change: AnnotationChange, forward: Boolean) {
        val from = if (forward) change.before else change.after
        val to = if (forward) change.after else change.before
        backend.page(change.page).use { page ->
            when {
                to == null -> page.remove(change.id)
                from == null -> {
                    val previousId = change.id
                    val restoredId = page.add(to)
                    check(restoredId >= 0) { "Não foi possível restaurar a anotação." }
                    // Native IDs change after a deleted annotation is recreated. Every command
                    // for this annotation must follow the restored identity, including older edits.
                    (undo + redo).filter { it.page == change.page && it.id == previousId }
                        .forEach { it.id = restoredId }
                    change.id = restoredId
                }
                else -> check(page.update(change.id, to))
            }
        }
    }
    @Synchronized fun write(output: ParcelFileDescriptor) {
        try { check(!closed); backend.write(output) } finally { output.close() }
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        cache.evictAll(); undo.clear(); redo.clear(); sizes.clear()
        backend.close()
    }
    fun closeAsync() { cleanup.execute { close() } }
    private data class AnnotationChange(val page: Int, var id: Int, val before: Any?, val after: Any?)
    private companion object {
        val cleanup = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "pdf-cleanup").apply { isDaemon = true } }
    }
}
