package dev.pdfcrate.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Platform renderer only. No third-party PDF engine is shipped in this alpha. */
class PdfSession(file: File) : Closeable {
    private val renderer: PdfRenderer

    init {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            renderer = PdfRenderer(descriptor)
        } catch (failure: Throwable) {
            descriptor.close()
            throw failure
        }
    }

    val pageCount: Int get() = renderer.pageCount

    @Synchronized fun render(index: Int): Bitmap {
        require(index in 0 until pageCount) { "Página fora do documento." }
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
                return bitmap
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
        }
    }

    @Synchronized override fun close() = renderer.close()
}
