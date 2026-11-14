package dev.pdfcrate.app.pdf

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/** A previous save left recovery files that must be reviewed first. */
class RecoveryPendingException : IOException("A previous save recovery is pending")
/** The original changed on the provider after it was opened. */
class SourceChangedException : IOException("The original changed outside the app")
/** Not enough private storage to keep the edited and previous versions. */
class InsufficientSpaceException : IOException("Not enough space to save with recovery")

/** Recovery files exist only after an explicit Save action, never as an import on opening. */
class PdfSaveTransaction(private val context: Context) {
    private val directory = File(context.filesDir, "save-recovery").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("save-recovery", Context.MODE_PRIVATE)

    data class Recovery(val edited: Uri, val backup: Uri?, val name: String)

    /**
     * [recovery] is null when the original was replaced. Otherwise the original could not be
     * written; [restored] tells whether it is known to hold its previous content.
     */
    data class Result(val recovery: Recovery?, val restored: Boolean = true)

    fun pending(): Recovery? {
        val draft = prefs.getString(KEY_EDITED, null)?.let { File(directory, it) } ?: return null
        if (!draft.isFile) return null
        val backup = prefs.getString(KEY_BACKUP, null)?.let { File(directory, it) }?.takeIf { it.isFile }
        val name = prefs.getString(KEY_NAME, null) ?: "Documento.pdf"
        return Recovery(uri(draft, "Recuperação-$name"), backup?.let { uri(it, "Original-$name") }, name)
    }

    private fun uri(file: File, name: String) =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file, name)

    @SuppressLint("ApplySharedPref", "UseKtx") // Synchronous on purpose: recovery state must be durable.
    fun discard() {
        listOfNotNull(prefs.getString(KEY_EDITED, null), prefs.getString(KEY_BACKUP, null))
            .forEach { File(directory, it).delete() }
        prefs.edit().clear().commit()
    }

    /** Complete output and backup are prepared before the original is opened for replacement. */
    @SuppressLint("ApplySharedPref", "UseKtx", "UsableSpace") // The journal is committed before writing the original.
    fun save(original: Uri, name: String, session: PdfSession): Result {
        if (pending() != null) throw RecoveryPendingException()
        val id = UUID.randomUUID().toString()
        val draft = File(directory, "$id-edited.pdf")
        val backup = File(directory, "$id-original.pdf")
        var journaled = false
        try {
            context.contentResolver.openFileDescriptor(original, "r")?.use { current ->
                val before = session.sourceStamp
                val now = SourceStamp.of(current)
                if (before != null && now != null && before.differsFrom(now)) throw SourceChangedException()
            }
            session.write(ParcelFileDescriptor.open(draft, ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE))
            context.contentResolver.openInputStream(original)?.use { source ->
                backup.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (directory.usableSpace < read + RESERVED_BYTES) throw InsufficientSpaceException()
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            } ?: throw IOException("Original could not be preserved before writing")
            check(prefs.edit().putString(KEY_EDITED, draft.name).putString(KEY_BACKUP, backup.name)
                .putString(KEY_NAME, name).commit())
            journaled = true
            // The live renderer must not continue reading a file that is being replaced.
            session.close()
            try {
                context.contentResolver.openOutputStream(original, "rwt")?.use { output ->
                    draft.inputStream().use { it.copyTo(output) }
                } ?: throw IOException("Provider offered no write access")
                discard()
                return Result(null)
            } catch (failure: Exception) {
                // A SecurityException means the provider refused before anything was written.
                val restored = if (failure is SecurityException) true else runCatching {
                    context.contentResolver.openOutputStream(original, "rwt")?.use { output ->
                        backup.inputStream().use { it.copyTo(output) }
                    } ?: throw IOException("Restore unavailable")
                }.isSuccess
                return Result(pending(), restored)
            }
        } catch (failure: Throwable) {
            if (!journaled) { draft.delete(); backup.delete() }
            throw failure
        }
    }

    private companion object {
        const val KEY_EDITED = "edited"
        const val KEY_BACKUP = "backup"
        const val KEY_NAME = "name"
        const val RESERVED_BYTES = 4L * 1024 * 1024
    }
}
