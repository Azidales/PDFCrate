package dev.pdfcrate.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** A reference to a user-selected document. No imported PDF is stored in the application. */
data class LibraryDocument(
    val id: String,
    val name: String,
    val page: Int = 0,
    val opened: Long = System.currentTimeMillis(),
    val pages: Int = 0,
    val bytes: Long = 0,
    val uri: String,
    val persistent: Boolean = false
)

class DocumentLibrary(private val context: Context) {
    private val preferences = context.getSharedPreferences("recents_v1", Context.MODE_PRIVATE)

    @Synchronized fun documents(): List<LibraryDocument> {
        val array = try { JSONArray(preferences.getString("documents", "[]")) }
            catch (_: Exception) { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val uri = item.optString("uri")
            if (Uri.parse(uri).scheme != "content") return@mapNotNull null
            LibraryDocument(item.optString("id"), item.optString("name", "Documento.pdf"),
                item.optInt("page", 0).coerceAtLeast(0), item.optLong("opened", 0),
                item.optInt("pages", 0), item.optLong("bytes", 0), uri, item.optBoolean("persistent"))
        }.sortedByDescending { it.opened }
    }

    fun uri(document: LibraryDocument): Uri = Uri.parse(document.uri)
    fun isSample(document: LibraryDocument): Boolean =
        uri(document).authority == "${context.packageName}.files"

    /** Opens the provider's URI and attempts to retain only permissions actually offered by it. */
    fun reference(uri: Uri, previous: LibraryDocument? = null): LibraryDocument {
        require(uri.scheme == "content") { "Escolha o PDF pelo seletor de arquivos do Android." }
        var name = "Documento.pdf"
        var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)?.take(180)?.takeIf { it.isNotBlank() } ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).coerceAtLeast(0)
            }
        }
        if (uri.authority != "${context.packageName}.files") {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* ACTION_VIEW/SEND providers may offer temporary access only. */ }
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            catch (_: SecurityException) { /* Read-only documents remain readable. */ }
        }
        val persistent = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        val existing = previous ?: documents().firstOrNull { it.uri == uri.toString() }
        return LibraryDocument(existing?.id ?: UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString(),
            name, existing?.page ?: 0, pages = existing?.pages ?: 0, bytes = size,
            uri = uri.toString(), persistent = persistent)
    }

    @Synchronized fun update(document: LibraryDocument) {
        val all = documents()
        val retained = (listOf(document) + all.filter { it.id != document.id && it.uri != document.uri }).take(50)
        write(retained)
        all.filter { old -> retained.none { it.uri == old.uri } }.forEach(::release)
    }

    /** Forgetting a recent entry never deletes, truncates or rewrites the provider's file. */
    @Synchronized fun remove(document: LibraryDocument) {
        write(documents().filter { it.id != document.id })
        release(document)
    }

    private fun release(document: LibraryDocument) {
        val target = uri(document)
        context.contentResolver.persistedUriPermissions.filter { it.uri == target }.forEach { permission ->
            val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            try { context.contentResolver.releasePersistableUriPermission(target, flags) }
            catch (_: SecurityException) { }
        }
    }

    private fun write(documents: List<LibraryDocument>) {
        val array = JSONArray()
        documents.forEach { item -> array.put(JSONObject().put("id", item.id).put("name", item.name)
            .put("page", item.page).put("opened", item.opened).put("pages", item.pages)
            .put("bytes", item.bytes).put("uri", item.uri).put("persistent", item.persistent)) }
        preferences.edit().putString("documents", array.toString()).apply()
    }

    /** The demo is generated by the app, not imported from a user's document. */
    fun sample(): LibraryDocument {
        val directory = File(context.cacheDir, "samples").apply { mkdirs() }
        val target = File(directory, "exemplo.pdf")
        if (!target.isFile) {
            val pdf = PdfDocument()
            try {
                val titles = listOf("Bem-vindo ao PDFCrate", "Leia do seu jeito", "Arquivos no lugar certo")
                val rows = listOf(
                    listOf("Um leitor sem anúncios e sem conta.", "Use dois dedos para ampliar.", "Toque duas vezes para alternar o zoom.", "Um toque oculta ou mostra os controles."),
                    listOf("Use as setas para navegar.", "Toque no número para ir a uma página.", "Deslize na horizontal com a página ajustada.", "O aplicativo lembra onde você parou."),
                    listOf("Abra PDFs pelo seletor do Android.", "O arquivo permanece no provedor escolhido.", "O app guarda apenas os arquivos recentes.", "Remover dos recentes não apaga o PDF.")
                )
                titles.forEachIndexed { index, title ->
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(23, 63, 56) }
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawRect(0f, 0f, 595f, 16f, paint)
                    paint.textSize = 27f
                    paint.isFakeBoldText = true
                    page.canvas.drawText(title, 44f, 120f, paint)
                    paint.textSize = 16f
                    paint.isFakeBoldText = false
                    rows[index].forEachIndexed { row, line -> page.canvas.drawText(line, 44f, 190f + row * 40, paint) }
                    paint.textSize = 12f
                    page.canvas.drawText("PDFCrate - ${index + 1} / 3", 44f, 790f, paint)
                    pdf.finishPage(page)
                }
                target.outputStream().use { pdf.writeTo(it) }
            } catch (failure: Throwable) { target.delete(); throw failure }
            finally { pdf.close() }
        }
        return reference(FileProvider.getUriForFile(context, "${context.packageName}.files", target, "Exemplo PDFCrate.pdf"))
    }
}
