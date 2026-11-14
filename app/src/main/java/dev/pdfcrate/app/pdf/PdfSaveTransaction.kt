package dev.pdfcrate.app

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/** Recovery files exist only after an explicit Save action, never as an import on opening. */
class PdfSaveTransaction(private val context: Context) {
    private val directory = File(context.filesDir, "save-recovery").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("save-recovery", Context.MODE_PRIVATE)
    data class Recovery(val edited: Uri, val backup: Uri?, val name: String)
    data class Result(val recovery: Recovery?, val message: String?)

    fun pending(): Recovery? {
        val draft = prefs.getString("edited", null)?.let { File(directory, it) } ?: return null
        if (!draft.isFile) return null
        val backup = prefs.getString("backup", null)?.let { File(directory, it) }?.takeIf { it.isFile }
        val name = prefs.getString("name", "Documento.pdf")!!
        return Recovery(uri(draft, "Recuperação-$name"), backup?.let { uri(it, "Original-$name") }, name)
    }
    private fun uri(file: File, name: String) = FileProvider.getUriForFile(context, "${context.packageName}.files", file, name)
    fun discard() {
        listOfNotNull(prefs.getString("edited", null), prefs.getString("backup", null)).forEach { File(directory, it).delete() }
        prefs.edit().clear().commit()
    }

    /** Complete output and backup are prepared before the original is opened for replacement. */
    fun save(original: Uri, name: String, session: PdfSession): Result {
        check(pending() == null) { "Revise a recuperação anterior antes de salvar no original novamente." }
        val id = UUID.randomUUID().toString()
        val draft = File(directory, "$id-edited.pdf")
        val backup = File(directory, "$id-original.pdf")
        var journaled = false
        try {
            context.contentResolver.openFileDescriptor(original, "r")?.use { checkFd ->
                val before = session.sourceStamp
                val now = runCatching { android.system.Os.fstat(checkFd.fileDescriptor) }.getOrNull()
                if (before != null && now != null && before.size > 0 && now.st_size > 0 &&
                    (before.size != now.st_size || (before.modified > 0 && now.st_mtime > 0 && before.modified != now.st_mtime) ||
                        (before.inode > 0 && now.st_ino > 0 && before.inode != now.st_ino)))
                    throw IOException("O arquivo foi alterado fora do aplicativo. Salve uma cópia ou reabra o original.")
            }
            session.write(ParcelFileDescriptor.open(draft, ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE))
            context.contentResolver.openInputStream(original)?.use { source ->
                backup.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (directory.usableSpace < read + 4L * 1024 * 1024) throw IOException("Espaço insuficiente para salvar com recuperação.")
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            } ?: throw IOException("Não foi possível preservar o original antes da gravação.")
            check(prefs.edit().putString("edited", draft.name).putString("backup", backup.name).putString("name", name).commit())
            journaled = true
            // The live renderer must not continue reading a file that is being replaced.
            session.close()
            try {
                context.contentResolver.openOutputStream(original, "rwt")?.use { output ->
                    draft.inputStream().use { it.copyTo(output) }
                } ?: throw IOException("O provedor não ofereceu escrita.")
                discard()
                return Result(null, null)
            } catch (failure: Exception) {
                val restored = if (failure is SecurityException) true else runCatching {
                    context.contentResolver.openOutputStream(original, "rwt")?.use { output ->
                        backup.inputStream().use { it.copyTo(output) }
                    } ?: throw IOException("Restauração indisponível")
                }.isSuccess
                return Result(pending(), if (restored)
                    "Não foi possível salvar no original. O original foi preservado/restaurado e suas alterações estão na recuperação. Use Salvar cópia."
                    else "A gravação e a restauração falharam. Não use o original até revisar a recuperação; as versões editada e anterior foram preservadas no app.")
            }
        } catch (failure: Throwable) {
            if (!journaled) { draft.delete(); backup.delete() }
            throw failure
        }
    }
}
