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

    fun dismissError() { error = null; locate = null }
    fun beginLocate() { error = null }
    var locate by mutableStateOf<LibraryDocument?>(null)
        private set
    fun cancelLocate() { locate = null }
    fun open(uri: Uri) { locate = null; load { library.reference(uri) } }
    fun openSelected(uri: Uri) {
        val previous = locate
        locate = null
        load { library.reference(uri, previous) }
    }
    fun sample() = load { library.sample() }
    fun open(document: LibraryDocument) = load { document }

    private fun load(obtain: () -> LibraryDocument) {
        if (busy) { error = "Aguarde a abertura do documento atual e tente novamente."; return }
        busy = true
        prefetch?.cancel()
        viewModelScope.launch {
            var selected: LibraryDocument? = null
            var candidate: PdfSession? = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val document = obtain()
                    selected = document
                    val descriptor = getApplication<Application>().contentResolver.openFileDescriptor(library.uri(document), "r")
                        ?: throw IOException("O provedor nao disponibilizou o PDF.")
                    val opened = PdfSession(descriptor)
                    candidate = opened
                    require(opened.pageCount > 0) { "O PDF não contém páginas." }
                    val page = document.page.coerceIn(0, opened.pageCount - 1)
                    val updated = document.copy(page = page, opened = System.currentTimeMillis(),
                        pages = opened.pageCount)
                    val rendered = opened.render(page)
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

                if (failure is CancellationException) throw failure
                locate = selected
                error = when (failure) {
                    is SecurityException -> "O acesso ao PDF foi negado ou o documento tem senha. Escolha o arquivo novamente pelo seletor do Android."
                    is IOException -> "O PDF pode ter sido movido, removido ou estar indisponível no provedor. Abra o arquivo novamente pelo seletor do Android."
                    else -> "Não foi possível ler o PDF diretamente. Ele pode estar inválido ou o provedor pode não oferecer acesso compatível. Nenhuma cópia foi importada."
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

    fun remove(document: LibraryDocument) {
        if (busy) return
        library.remove(document)
        documents = library.documents()
    }

    override fun onCleared() { prefetch?.cancel(); session?.closeAsync() }
}
