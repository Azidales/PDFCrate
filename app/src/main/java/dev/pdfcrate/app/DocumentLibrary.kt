package dev.pdfcrate.app

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

data class LibraryDocument(
    val id: String,
    val name: String,
    val page: Int = 0,
    val favorite: Boolean = false,
    val opened: Long = System.currentTimeMillis()
)

class DocumentLibrary(private val context: Context) {
    private val preferences = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "documents").apply { mkdirs() }

    @Synchronized fun documents(): List<LibraryDocument> {
        val array = try { JSONArray(preferences.getString("documents", "[]")) }
        catch (_: Exception) { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (!id.matches(Regex("[a-f0-9-]{36}")) || !file(id).isFile) return@mapNotNull null
            LibraryDocument(id, item.optString("name", "Documento.pdf"),
                item.optInt("page", 0).coerceAtLeast(0), item.optBoolean("favorite"),
                item.optLong("opened", 0))
        }.sortedByDescending { it.opened }
    }

    fun file(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(directory, "$id.pdf")
    }

    @Synchronized fun update(document: LibraryDocument) {
        write(listOf(document) + documents().filter { it.id != document.id })
    }

    @Synchronized fun remove(document: LibraryDocument) {
        val target = file(document.id)
        if (target.exists() && !target.delete()) throw IOException("Não foi possível remover a cópia local.")
        write(documents().filter { it.id != document.id })
    }

    private fun write(documents: List<LibraryDocument>) {
        val array = JSONArray()
        documents.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("name", item.name)
                .put("page", item.page).put("favorite", item.favorite).put("opened", item.opened))
        }
        preferences.edit().putString("documents", array.toString()).apply()
    }

    fun import(uri: Uri): LibraryDocument {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.take(180)?.takeIf { it.isNotBlank() } ?: "Documento.pdf"
        val document = LibraryDocument(UUID.randomUUID().toString(), name)
        val target = file(document.id)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("O provedor não disponibilizou o arquivo.")
            input.use { source ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count == -1) break
                        total += count
                        if (total > 512L * 1024 * 1024) {
                            throw IOException("Esta versão aceita arquivos de até 512 MB.")
                        }
                        if (directory.usableSpace < count + 8L * 1024 * 1024) {
                            throw IOException("Espaço insuficiente para a cópia local.")
                        }
                        output.write(buffer, 0, count)
                    }
                    if (total == 0L) throw IOException("O arquivo está vazio.")
                }
            }
            return document
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
    }

    fun sample(): LibraryDocument {
        val entry = LibraryDocument(UUID.randomUUID().toString(), "Bem-vindo ao PDFCrate.pdf")
        try {
            PdfDocument().use { pdf ->
                val titles = listOf("Seu PDF. Seu espaço.", "Leitura sem distrações.", "Seus documentos, aqui.")
                val lines = listOf(
                    listOf("PDFCrate", "Um leitor local, sem anúncios e sem IA.", "Deslize para mover a página ampliada.", "Use dois dedos para ampliar.", "Toque duas vezes para alternar o zoom."),
                    listOf("Explore o documento", "Use Anterior e Próxima para navegar.", "Toque no número da página para ir direto.", "Ajustar restaura a página inteira.", "A última página é lembrada automaticamente."),
                    listOf("Biblioteca offline", "Abra um PDF pelo seletor do Android.", "Uma cópia fica guardada neste aplicativo.", "O arquivo original permanece intacto.", "Remover a cópia libera espaço no aparelho.")
                )
                titles.forEachIndexed { index, title ->
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    page.canvas.drawColor(Color.WHITE)
                    paint.color = Color.rgb(23, 63, 56)
                    page.canvas.drawRect(0f, 0f, 595f, 16f, paint)
                    paint.textSize = 28f
                    paint.isFakeBoldText = true
                    page.canvas.drawText(title, 44f, 120f, paint)
                    paint.textSize = 16f
                    paint.isFakeBoldText = false
                    lines[index].forEachIndexed { row, line ->
                        page.canvas.drawText(line, 44f, 190f + row * 40, paint)
                    }
                    paint.textSize = 12f
                    page.canvas.drawText("PDFCrate · ${index + 1} / 3", 44f, 790f, paint)
                    pdf.finishPage(page)
                }
                file(entry.id).outputStream().use { pdf.writeTo(it) }
            }
            return entry
        } catch (failure: Throwable) {
            file(entry.id).delete()
            throw failure
        }
    }
}
