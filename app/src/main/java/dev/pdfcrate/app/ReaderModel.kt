package dev.pdfcrate.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class ReaderModel(application: Application) : AndroidViewModel(application) {
    val library = DocumentLibrary(application)
    var documents by mutableStateOf(library.documents())
        private set
    var current by mutableStateOf<LibraryDocument?>(null)
        private set
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var count by mutableStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var session: PdfSession? = null
    private var prefetch: Job? = null

    fun dismissError() { error = null }
    fun open(uri: Uri) = load { library.import(uri) }
    fun sample() = load { library.sample() }
    fun open(document: LibraryDocument) = load(existing = true) { document }

    private fun load(existing: Boolean = false, obtain: () -> LibraryDocument) {
        if (busy) { error = "Aguarde a abertura do documento atual e tente novamente."; return }
        busy = true
        prefetch?.cancel()
        viewModelScope.launch {
            var imported: LibraryDocument? = null
            var candidate: PdfSession? = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val document = obtain()
                    if (!existing) imported = document
                    val opened = PdfSession(library.file(document.id))
                    candidate = opened
                    require(opened.pageCount > 0) { "O PDF não contém páginas." }
                    val page = document.page.coerceIn(0, opened.pageCount - 1)
                    val updated = document.copy(page = page, opened = System.currentTimeMillis(),
                        pages = opened.pageCount, bytes = library.file(document.id).length())
                    val rendered = opened.render(page)
                    // A cache failure must never prevent reading the document.
                    runCatching { library.saveThumbnail(updated, rendered) }
                    Triple(updated, opened.pageCount, rendered)
                }
                session?.closeAsync()
                session = candidate
                candidate = null
                current = result.first
                count = result.second
                bitmap = result.third
                library.update(result.first)
                documents = library.documents()
                prepareNext(result.first.page + 1)
            } catch (failure: Exception) {
                candidate?.closeAsync()
                imported?.let { library.file(it.id).delete() }
                if (failure is CancellationException) throw failure
                error = when (failure) {
                    is SecurityException -> "Este PDF está protegido ou o acesso foi negado. Nesta versão, abra uma cópia sem senha."
                    is IOException -> "Não foi possível abrir o PDF. ${failure.message.orEmpty()}"
                    else -> "Não foi possível abrir este documento. Ele pode estar inválido ou usar um formato não compatível."
                }
            } finally { busy = false }
        }
    }

    fun page(index: Int) {
        val document = current ?: return
        val engine = session ?: return
        if (busy || index !in 0 until count || index == document.page) return
        busy = true
        prefetch?.cancel()
        viewModelScope.launch {
            try {
                val rendered = withContext(Dispatchers.IO) { engine.render(index) }
                bitmap = rendered
                val updated = document.copy(page = index)
                current = updated
                library.update(updated)
                documents = library.documents()
                prepareNext(index + if (index > document.page) 1 else -1)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = "Não foi possível renderizar esta página. Tente outra página."
            } finally { busy = false }
        }
    }

    private fun prepareNext(index: Int) {
        val engine = session ?: return
        if (index !in 0 until count) return
        prefetch = viewModelScope.launch(Dispatchers.IO) {
            delay(120)
            // Native rasterization is serialized by PdfSession; no work is done on the UI thread.
            runCatching { engine.render(index) }
        }
    }

    fun home() {
        if (busy) return
        prefetch?.cancel()
        session?.closeAsync()
        session = null
        bitmap = null
        current = null
        count = 0
        documents = library.documents()
    }

    fun favorite(document: LibraryDocument) {
        library.update(document.copy(favorite = !document.favorite))
        documents = library.documents()
    }

    fun remove(document: LibraryDocument) {
        if (busy) return
        try {
            library.remove(document)
            documents = library.documents()
        } catch (_: IOException) { error = "Não foi possível remover a cópia local." }
    }

    override fun onCleared() { prefetch?.cancel(); session?.closeAsync() }
}
