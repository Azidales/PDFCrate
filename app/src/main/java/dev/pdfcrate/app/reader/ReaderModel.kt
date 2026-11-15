package dev.pdfcrate.app.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.SizeF
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pdfcrate.app.R
import dev.pdfcrate.app.library.DocumentLibrary
import dev.pdfcrate.app.library.LibraryDocument
import dev.pdfcrate.app.pdf.*
import kotlinx.coroutines.*
import java.io.IOException
import kotlin.math.abs

enum class PdfTool { NONE, SELECT, DRAW, NOTE, SIGNATURE, MOVE }
enum class MarkupStyle { HIGHLIGHT, UNDERLINE, STRIKEOUT }

class ReaderModel(application: Application) : AndroidViewModel(application) {
    val library = DocumentLibrary(application)
    private val saves = PdfSaveTransaction(application)
    private val preferences = ReaderPreferences(application)
    var documents by mutableStateOf(library.documents()); private set
    var current by mutableStateOf<LibraryDocument?>(null); private set
    var bitmap by mutableStateOf<Bitmap?>(null); private set
    var count by mutableIntStateOf(0); private set
    var size by mutableStateOf(SizeF(1f, 1f)); private set
    var busy by mutableStateOf(false); private set
    /** Blocking problem shown in a dialog, optionally with a recovery action. */
    var error by mutableStateOf<String?>(null); private set
    /** Short, non-blocking feedback shown in a snackbar. */
    var message by mutableStateOf<String?>(null); private set
    var locate by mutableStateOf<LibraryDocument?>(null); private set
    var passwordDocument by mutableStateOf<LibraryDocument?>(null); private set
    var passwordIncorrect by mutableStateOf(false); private set
    var advanced by mutableStateOf(false); private set
    var annotationsAvailable by mutableStateOf(false); private set
    var dirty by mutableStateOf(false); private set
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    var discardPrompt by mutableStateOf(false); private set
    var selection by mutableStateOf(PdfSelection()); private set
    var tool by mutableStateOf(PdfTool.NONE); private set
    var noteBounds by mutableStateOf<RectF?>(null); private set
    var fields by mutableStateOf<List<PdfField>?>(null); private set
    var annotations by mutableStateOf<List<PdfAnnotationEntry>?>(null); private set
    var pageText by mutableStateOf<String?>(null); private set
    var hits by mutableStateOf<List<PdfHit>>(emptyList()); private set
    var hitIndex by mutableIntStateOf(-1); private set
    var searching by mutableStateOf(false); private set
    var recovery by mutableStateOf(saves.pending()); private set
    var signature by mutableStateOf<List<List<PointF>>?>(null); private set
    /** Continuous vertical scrolling; otherwise one page at a time. */
    var continuous by mutableStateOf(preferences.continuous); private set
    /** Every page size in PDF points, for the continuous layout (estimated until measured). */
    var pageSizes by mutableStateOf<List<SizeF>>(emptyList()); private set
    /** Bitmaps for the pages the continuous view is showing. */
    var pageImages by mutableStateOf<Map<Int, Bitmap>>(emptyMap()); private set
    /** Latest navigation request for the continuous view. */
    var scrollTarget by mutableStateOf<ScrollTarget?>(null); private set
    private var session: PdfSession? = null
    private var prefetch: Job? = null
    private var searchJob: Job? = null
    private var searchGeneration = 0
    private var unlockedPassword: String? = null
    private var pendingNavigation: (() -> Unit)? = null
    private var recoveryBacking = false
    private var movingAnnotation: Int? = null
    private var sizesJob: Job? = null
    private var imagesJob: Job? = null
    private var positionJob: Job? = null
    private var visiblePages = IntRange.EMPTY
    private var imageWidth = 0
    /** Width each page image was rendered for; [Int.MAX_VALUE] marks a full-resolution bitmap. */
    private val imageWidths = mutableMapOf<Int, Int>()
    private var scrollSerial = 0L
    val searchBounds: List<RectF> get() = hits.getOrNull(hitIndex)?.takeIf { it.page == current?.page }?.bounds.orEmpty()
    val activeHit: PdfHit? get() = hits.getOrNull(hitIndex)

    private fun text(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    fun notifyError(message: String) { error = message }
    fun notify(message: String) { this.message = message }
    fun consumeMessage() { message = null }
    fun dismissError() { error = null; locate = null }
    fun beginLocate() { error = null }
    fun cancelLocate() { locate = null }
    fun cancelPassword() { passwordDocument = null; passwordIncorrect = false }
    fun submitPassword(password: String) {
        val document = passwordDocument ?: return
        load(document, password) { library.reference(library.uri(document), document) }
    }
    private fun navigate(action: () -> Unit) {
        if (busy) return
        if (dirty) { pendingNavigation = action; discardPrompt = true } else action()
    }
    fun cancelDiscard() { pendingNavigation = null; discardPrompt = false }
    fun discardAndContinue() {
        val action = pendingNavigation
        pendingNavigation = null; discardPrompt = false
        closeDocument(); action?.invoke()
    }
    fun open(uri: Uri) = navigate { locate = null; load { library.reference(uri) } }
    fun openSelected(uri: Uri) {
        val previous = locate; locate = null
        navigate { load(previous) { library.reference(uri, previous) } }
    }
    fun sample() = navigate { load { library.sample() } }
    fun open(document: LibraryDocument) = navigate { load(document) { library.reference(library.uri(document), document) } }

    private fun load(previous: LibraryDocument? = null, password: String? = null, obtain: () -> LibraryDocument) {
        if (busy) return
        busy = true; error = null; searching = false; searchGeneration++; prefetch?.cancel(); searchJob?.cancel()
        viewModelScope.launch {
            var selected = previous
            var candidate: PdfSession? = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val document = obtain(); selected = document
                    val descriptor = getApplication<Application>().contentResolver.openFileDescriptor(library.uri(document), "r")
                        ?: throw IOException("Provider returned no descriptor")
                    val opened = PdfSession(descriptor, password); candidate = opened
                    require(opened.pageCount > 0)
                    val page = document.page.coerceIn(0, opened.pageCount - 1)
                    val updated = document.copy(page = page, opened = System.currentTimeMillis(), pages = opened.pageCount)
                    Loaded(updated, opened.render(page), opened.dimensions(page))
                }
                flushPosition(); visiblePages = IntRange.EMPTY
                session?.closeAsync(); session = candidate; candidate = null
                current = result.document; count = session!!.pageCount; bitmap = result.bitmap; size = result.size
                advanced = session!!.advanced; annotationsAvailable = session!!.canAnnotate
                unlockedPassword = password; passwordDocument = null; passwordIncorrect = false
                recoveryBacking = false; dirty = false; canUndo = false; canRedo = false
                resetTools(); hits = emptyList(); hitIndex = -1
                library.update(result.document); documents = library.documents()
                startContinuous(result.document.page, result.bitmap, result.size, scroll = true)
                if (!continuous) prepareNext(result.document.page + 1)
            } catch (failure: Exception) {
                candidate?.closeAsync()
                if (failure is CancellationException) throw failure
                if (failure is PdfPasswordRequired) {
                    passwordDocument = selected; passwordIncorrect = password != null
                } else {
                    locate = selected
                    error = text(when (failure) {
                        is PdfFeatureUnavailable -> R.string.error_open_feature_unavailable
                        is SecurityException -> R.string.error_open_access_denied
                        is IOException -> R.string.error_open_unavailable
                        else -> R.string.error_open_invalid
                    })
                }
            } finally { busy = false }
        }
    }
    private data class Loaded(val document: LibraryDocument, val bitmap: Bitmap, val size: SizeF)

    fun page(index: Int) {
        val document = current ?: return
        val engine = session ?: return
        if (continuous) {
            if (busy || index !in 0 until count) return
            showContinuous(index, null)
            return
        }
        if (busy || index !in 0 until count || index == document.page) return
        busy = true; prefetch?.cancel()
        viewModelScope.launch {
            try {
                val rendered = withContext(Dispatchers.IO) { engine.render(index) to engine.dimensions(index) }
                bitmap = rendered.first; size = rendered.second
                current = document.copy(page = index)
                resetTools(); library.update(current!!); documents = library.documents()
                prepareNext(index + if (index > document.page) 1 else -1)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = text(R.string.error_render_page)
            } finally { busy = false }
        }
    }
    /** Continuous mode: makes [index] current and asks the view to show it (centred on [focus] when given). */
    private fun showContinuous(index: Int, focus: RectF?) {
        val document = current ?: return
        if (index != document.page) {
            current = document.copy(page = index)
            resetTools(); positionJob?.cancel(); library.update(current!!)
        }
        scrollTarget = ScrollTarget(index, focus, ++scrollSerial)
    }

    /** Called while the reader scrolls: the page in view becomes current without re-rendering. */
    fun focusPage(index: Int) {
        val document = current ?: return
        if (!continuous || index == document.page || index !in 0 until count) return
        current = document.copy(page = index)
        // Selections belong to a page; an active tool (drawing, signature) continues on the new page.
        if (tool == PdfTool.NONE) resetTools() else selection = PdfSelection()
        persistPositionSoon()
    }

    private fun persistPositionSoon() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            delay(POSITION_SAVE_DELAY_MS)
            current?.let(library::update)
        }
    }

    private fun flushPosition() {
        if (positionJob?.isActive == true) { positionJob?.cancel(); current?.let(library::update) }
        positionJob = null
    }

    fun useContinuousScroll(value: Boolean) {
        if (value == continuous || busy) return
        continuous = value
        preferences.continuous = value
        val document = current ?: return
        val engine = session ?: return
        resetTools()
        if (value) {
            prefetch?.cancel()
            startContinuous(document.page, bitmap, size, scroll = true)
        } else {
            stopContinuous()
            busy = true
            viewModelScope.launch {
                try {
                    val rendered = withContext(Dispatchers.IO) { engine.render(document.page) to engine.dimensions(document.page) }
                    bitmap = rendered.first; size = rendered.second
                    prepareNext(document.page + 1)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = text(R.string.error_render_page)
                } finally { busy = false }
            }
        }
    }

    /** Seeds the continuous view for the current session: the page already rendered and estimated sizes. */
    private fun startContinuous(page: Int, rendered: Bitmap?, renderedSize: SizeF, scroll: Boolean, refresh: Boolean = false) {
        val previous = pageImages
        val sizes = pageSizes
        stopContinuous()
        if (!continuous) return
        val engine = session ?: return
        // After saving, the same document reopens: earlier bitmaps stay on screen until re-rendered.
        if (refresh) { pageImages = previous; pageSizes = sizes }
        if (rendered != null) { pageImages = pageImages + (page to rendered); imageWidths[page] = Int.MAX_VALUE }
        if (!refresh || pageSizes.size != engine.pageCount) pageSizes = List(engine.pageCount) { renderedSize }
        sizesJob = viewModelScope.launch {
            val measured = try {
                withContext(Dispatchers.IO) { List(engine.pageCount) { ensureActive(); engine.dimensions(it) } }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                return@launch // The session closed; its replacement measures again.
            }
            if (session === engine) pageSizes = measured
        }
        if (scroll) scrollTarget = ScrollTarget(page, null, ++scrollSerial)
        renderVisible(force = refresh)
    }

    private fun stopContinuous() {
        sizesJob?.cancel(); imagesJob?.cancel()
        pageImages = emptyMap(); imageWidths.clear(); pageSizes = emptyList()
    }

    /** The continuous view reports the pages it needs and the bitmap width for its zoom. */
    fun requestPages(range: IntRange, width: Int) {
        visiblePages = range; imageWidth = width
        renderVisible(force = false)
    }

    /** Renders missing or outdated bitmaps for the visible pages, nearest to the current page first. */
    private fun renderVisible(force: Boolean) {
        val engine = session ?: return
        val range = visiblePages
        val width = imageWidth
        if (!continuous || width <= 0 || range.isEmpty()) return
        imagesJob?.cancel()
        pageImages = pageImages.filterKeys { it in range }
        imageWidths.keys.retainAll(range.toSet())
        val stale = if (force) range.toMutableSet() else mutableSetOf()
        val focus = current?.page ?: range.first
        imagesJob = viewModelScope.launch {
            for (index in range.sortedBy { abs(it - focus) }) {
                if (index !in stale && pageImages[index] != null && (imageWidths[index] ?: 0) >= width) continue
                val rendered = try { withContext(Dispatchers.IO) { engine.render(index, width) } }
                    catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        return@launch
                    }
                if (session !== engine) return@launch
                pageImages = pageImages + (index to rendered)
                imageWidths[index] = width
            }
        }
    }

    private fun prepareNext(index: Int) {
        val engine = session ?: return
        if (index !in 0 until count) return
        prefetch = viewModelScope.launch(Dispatchers.IO) { delay(120); runCatching { engine.render(index) } }
    }
    fun home() = navigate(::closeDocument)
    private fun closeDocument() {
        flushPosition(); stopContinuous(); scrollTarget = null; visiblePages = IntRange.EMPTY
        prefetch?.cancel(); searchJob?.cancel(); session?.closeAsync(); session = null
        current = null; count = 0; bitmap = null; unlockedPassword = null
        advanced = false; annotationsAvailable = false; dirty = false; recoveryBacking = false
        passwordDocument = null; resetTools(); hits = emptyList(); hitIndex = -1
        documents = library.documents()
    }
    fun remove(document: LibraryDocument) { if (!busy) { library.remove(document); documents = library.documents() } }
    fun resetTools() { movingAnnotation = null; selection = PdfSelection(); tool = PdfTool.NONE; noteBounds = null; signature = null; fields = null; annotations = null; pageText = null }
    fun chooseTool(value: PdfTool) { selection = PdfSelection(); tool = value }
    fun cancelNote() { noteBounds = null; tool = PdfTool.NONE }
    fun closePanels() { fields = null; annotations = null; pageText = null }

    fun search(query: String) {
        val engine = session ?: return
        if (busy) return
        searchGeneration++
        val generation = searchGeneration
        searchJob?.cancel(); hits = emptyList(); hitIndex = -1
        if (query.isBlank() || !advanced) { searching = false; return }
        searching = true
        searchJob = viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    buildList {
                        for (index in 0 until engine.pageCount) {
                            ensureActive(); addAll(engine.search(index, query))
                            if (size >= MAX_SEARCH_HITS) break
                        }
                    }.take(MAX_SEARCH_HITS)
                }
                if (session === engine) { hits = results; hitIndex = if (results.isEmpty()) -1 else 0
                    results.firstOrNull()?.let(::reveal) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = text(R.string.error_search)
            } finally { if (searchGeneration == generation) searching = false }
        }
    }
    fun clearSearch() { searchGeneration++; searchJob?.cancel(); hits = emptyList(); hitIndex = -1; searching = false }
    fun moveHit(direction: Int) {
        if (hits.isEmpty() || busy) return
        hitIndex = (hitIndex + direction + hits.size) % hits.size
        reveal(hits[hitIndex])
    }
    private fun reveal(hit: PdfHit) {
        if (continuous) showContinuous(hit.page, RectF().also { box -> hit.bounds.forEach(box::union) }) else page(hit.page)
    }
    fun select(from: Point, to: Point) = inspect { engine, index ->
        val result = engine.select(index, from, to)
        withContext(Dispatchers.Main) { selection = result; tool = PdfTool.NONE
            if (result.text.isBlank()) message = text(R.string.message_no_selectable_text) }
    }
    fun showText() = inspect { engine, index ->
        val content = engine.text(index).joinToString("\n") { it.text }
        withContext(Dispatchers.Main) { pageText = content }
    }
    fun showFields() = inspect { engine, index -> val result = engine.fields(index); withContext(Dispatchers.Main) { fields = result } }
    fun showAnnotations() = inspect { engine, index -> val result = engine.annotations(index); withContext(Dispatchers.Main) { annotations = result } }
    private fun inspect(block: suspend (PdfSession, Int) -> Unit) {
        val engine = session ?: return
        val index = current?.page ?: return
        if (busy) return
        busy = true; prefetch?.cancel()
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block(engine, index) } }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; error = text(R.string.error_feature_page) }
            finally { busy = false }
        }
    }
    fun setField(change: FieldChange) = edit { engine, index -> engine.setField(index, change) }
    fun addMarkup(style: MarkupStyle) {
        val bounds = selection.bounds.map(::RectF)
        if (bounds.isEmpty() || !annotationsAvailable) return
        edit { engine, index -> engine.addAnnotation(index,
            if (style == MarkupStyle.HIGHLIGHT) NativeAnnotations.highlight(bounds, HIGHLIGHT_COLORS.first())
            else NativeAnnotations.lines(bounds, style == MarkupStyle.STRIKEOUT)) }
    }
    fun prepareMove(id: Int) { movingAnnotation = id; annotations = null; tool = PdfTool.MOVE }
    fun recolor(id: Int, color: Int) = edit { engine, index -> engine.recolorAnnotation(index, id, color) }
    fun receiveArea(rect: RectF) {
        val moving = movingAnnotation
        if (tool == PdfTool.MOVE && moving != null) {
            movingAnnotation = null
            edit { engine, index -> engine.moveAnnotation(index, moving, rect) }
        } else if (tool == PdfTool.SIGNATURE && signature != null) {
            val scaled = signature!!.map { stroke -> stroke.map { PointF(rect.left + it.x * rect.width(), rect.top + it.y * rect.height()) } }
            addInk(scaled, rect)
        } else { noteBounds = rect; tool = PdfTool.NONE }
    }
    fun addNote(content: String) {
        val bounds = noteBounds ?: return
        noteBounds = null
        edit { engine, index -> engine.addAnnotation(index, NativeAnnotations.note(bounds, content)) }
    }
    fun prepareSignature(strokes: List<List<PointF>>) { signature = strokes; tool = PdfTool.SIGNATURE }
    fun addInk(strokes: List<List<PointF>>, bounds: RectF) {
        if (strokes.isEmpty()) return
        edit { engine, index -> engine.addAnnotation(index, NativeAnnotations.ink(strokes, bounds)) }
    }
    fun removeAnnotation(id: Int) = edit { engine, index -> engine.removeAnnotation(index, id) }
    fun changeNote(id: Int, content: String) = edit { engine, index -> engine.editNote(index, id, content) }
    fun undo() = edit { engine, _ -> engine.undoAnnotation() }
    fun redo() = edit { engine, _ -> engine.redoAnnotation() }
    private fun edit(block: (PdfSession, Int) -> Unit) {
        val engine = session ?: return
        val index = current?.page ?: return
        if (busy) return
        val hadFields = fields != null; val hadAnnotations = annotations != null
        val single = !continuous
        busy = true; prefetch?.cancel(); searchJob?.cancel(); imagesJob?.cancel()
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    block(engine, index)
                    Triple(if (single) engine.render(index) else null, if (hadFields) engine.fields(index) else null,
                        if (hadAnnotations) engine.annotations(index) else null)
                }
                result.first?.let { bitmap = it }
                fields = result.second; annotations = result.third
                dirty = recoveryBacking || engine.hasChanges(); canUndo = engine.canUndo(); canRedo = engine.canRedo()
                selection = PdfSelection(); tool = PdfTool.NONE; signature = null; hits = emptyList(); hitIndex = -1
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = text(R.string.error_edit)
            } finally {
                busy = false
                // Undo and redo may touch any page, so every visible page is redrawn.
                if (!single) renderVisible(force = true)
            }
        }
    }

    fun saveCopy(destination: Uri) {
        val document = current ?: return
        val engine = session ?: return
        if (busy) return
        if (destination == library.uri(document)) { error = text(R.string.error_copy_same_file); return }
        busy = true; prefetch?.cancel(); searchJob?.cancel()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver.openFileDescriptor(destination, "rwt") ?: throw IOException()
                    if (engine.advanced) engine.write(output)
                    else ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream ->
                        getApplication<Application>().contentResolver.openInputStream(library.uri(document))!!.use { it.copyTo(stream) }
                    }
                }
                dirty = false
                message = text(R.string.message_copy_saved)
                val password = unlockedPassword
                // The explicitly saved file becomes the current document; the original is untouched.
                busy = false
                load(password = password) { library.reference(destination).copy(page = document.page) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = text(R.string.error_copy_failed); busy = false
            }
        }
    }
    fun saveOriginal() {
        val document = current ?: return
        val engine = session ?: return
        if (busy || !dirty) return
        busy = true; prefetch?.cancel(); searchJob?.cancel(); imagesJob?.cancel(); sizesJob?.cancel()
        viewModelScope.launch {
            var originalWasSaved = false
            try {
                val result = withContext(Dispatchers.IO) { saves.save(library.uri(document), document.name, engine) }
                recovery = saves.pending()
                originalWasSaved = result.recovery == null
                val password = unlockedPassword
                val source = result.recovery?.edited ?: library.uri(document)
                val reopened = withContext(Dispatchers.IO) {
                    val next = PdfSession(getApplication<Application>().contentResolver.openFileDescriptor(source, "r")!!, password)
                    val page = document.page.coerceIn(0, next.pageCount - 1)
                    Triple(next, next.render(page), next.dimensions(page))
                }
                session = reopened.first; bitmap = reopened.second; size = reopened.third
                count = reopened.first.pageCount; recoveryBacking = result.recovery != null
                startContinuous(document.page, reopened.second, reopened.third, scroll = false, refresh = true)
                dirty = recoveryBacking; canUndo = false; canRedo = false; resetTools()
                when {
                    result.recovery == null -> message = text(R.string.message_saved)
                    result.restored -> error = text(R.string.error_save_recovered)
                    else -> error = text(R.string.error_save_restore_failed)
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                recovery = saves.pending()
                if (originalWasSaved) {
                    closeDocument()
                    error = text(R.string.error_saved_reopen_failed)
                } else if (recovery != null) {
                    closeDocument()
                    error = text(R.string.error_recovery_reopen_failed)
                } else error = text(R.string.error_save_failed, text(when (failure) {
                    is SourceChangedException -> R.string.error_save_reason_changed
                    is InsufficientSpaceException -> R.string.error_save_reason_space
                    is RecoveryPendingException -> R.string.error_save_reason_pending
                    else -> R.string.error_save_reason_generic
                }))
            } finally { busy = false }
        }
    }
    fun openRecovery() { recovery?.let { open(it.edited) } }
    fun discardRecovery() {
        val info = recovery
        val document = current
        if (info != null) {
            if (document != null && library.uri(document) in listOf(info.edited, info.backup)) closeDocument()
            library.documents().filter { library.uri(it) in listOf(info.edited, info.backup) }.forEach(library::remove)
            documents = library.documents()
        }
        saves.discard(); recovery = null
    }
    fun exportRecoveryBackup(destination: Uri) {
        val backup = recovery?.backup ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(backup)!!.use { input ->
                        getApplication<Application>().contentResolver.openOutputStream(destination, "rwt")!!.use { input.copyTo(it) }
                    }
                }
                message = text(R.string.message_backup_exported)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = text(R.string.error_backup_export)
            }
        }
    }
    override fun onCleared() {
        flushPosition(); prefetch?.cancel(); searchJob?.cancel(); sizesJob?.cancel(); imagesJob?.cancel()
        session?.closeAsync(); unlockedPassword = null
    }

    companion object {
        const val MAX_SEARCH_HITS = 5000
        private const val POSITION_SAVE_DELAY_MS = 400L
        /** Yellow (default), green and pink. */
        val HIGHLIGHT_COLORS = intArrayOf(0xFFFFD54F.toInt(), 0xFF81C784.toInt(), 0xFFF48FB1.toInt())
    }
}
