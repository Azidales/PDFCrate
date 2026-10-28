package dev.pdfcrate.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.system.Os
import java.io.IOException
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/** Platform renderer only. No third-party PDF engine is shipped in this alpha. */
class PdfSession(private val descriptor: ParcelFileDescriptor) : Closeable {
    constructor(file: File) : this(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
    private val renderer: PdfRenderer
    private var closed = false
    // Byte budget, rather than page count: differently shaped pages use different amounts of RAM.
    private val cache = object : LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(8L * 1024 * 1024, 24L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount
    }

    init {
        try {
            // Reject obvious non-PDF input before constructing the native renderer. Some old
            // Android renderers leave a partially initialized native object after invalid input.
            val header = ByteArray(1024)
            val read = Os.pread(descriptor.fileDescriptor, header, 0, header.size, 0)
            if (read <= 0 || !String(header, 0, read, Charsets.ISO_8859_1).contains("%PDF-"))
                throw IOException("O arquivo não tem um cabeçalho PDF válido.")
            renderer = PdfRenderer(descriptor)
        } catch (failure: Throwable) {
            descriptor.close()
            throw failure
        }
    }

    val pageCount: Int get() = renderer.pageCount

    @Synchronized fun render(index: Int): Bitmap {
        check(!closed) { "Documento fechado." }
        require(index in 0 until pageCount) { "Página fora do documento." }
        cache.get(index)?.let { return it }
        renderer.openPage(index).use { page ->
            require(page.width > 0 && page.height > 0) { "Dimensões de página inválidas." }
            val scale = 2200f / max(page.width, page.height)
            val bitmap = Bitmap.createBitmap(
                max(1, (page.width * scale).roundToInt()),
                max(1, (page.height * scale).roundToInt()),
                Bitmap.Config.ARGB_8888
            )
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.prepareToDraw()
                cache.put(index, bitmap)
                return bitmap
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
        }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        // A bitmap can still belong to a View. Eviction drops our reference, never recycles it.
        cache.evictAll()
        renderer.close()
    }

    fun closeAsync() { cleanup.execute { close() } }

    private companion object {
        val cleanup = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "pdf-cleanup").apply { isDaemon = true }
        }
    }
}
