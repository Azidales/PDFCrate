package dev.pdfcrate.app.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.pdfcrate.app.R
import dev.pdfcrate.app.library.LibraryDocument
import dev.pdfcrate.app.shareDocument
import dev.pdfcrate.app.ui.components.Glyph
import dev.pdfcrate.app.ui.components.ToolButton
import dev.pdfcrate.app.ui.components.copyText
import dev.pdfcrate.app.ui.theme.LocalReaderColors

@Composable
fun ReaderScreen(model: ReaderModel, document: LibraryDocument, snackbar: SnackbarHostState, onSaveCopy: () -> Unit) {
    val context = LocalContext.current
    var chrome by rememberSaveable(document.id) { mutableStateOf(true) }
    var searchOpen by rememberSaveable(document.id) { mutableStateOf(false) }
    var editing by rememberSaveable(document.id) { mutableStateOf(false) }
    var goToPage by remember { mutableStateOf(false) }
    var showSignature by remember { mutableStateOf(false) }
    val selecting = model.selection.text.isNotBlank()
    val showChrome = chrome || searchOpen || editing || model.tool != PdfTool.NONE || selecting
    val closeSearch = { model.clearSearch(); searchOpen = false }
    val stopEditing = { editing = false; model.chooseTool(PdfTool.NONE) }

    val pageView = remember(context, document.id) { ZoomPageView(context) }
    DisposableEffect(pageView) { onDispose { pageView.show(null); pageView.onTap = null; pageView.onPageSwipe = null } }
    // Back dismisses the innermost transient state before leaving the document.
    BackHandler {
        when {
            model.tool != PdfTool.NONE || selecting -> model.chooseTool(PdfTool.NONE)
            searchOpen -> closeSearch()
            editing -> stopEditing()
            else -> model.home()
        }
    }

    Scaffold(
        containerColor = LocalReaderColors.current.backdrop,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut()) {
                if (searchOpen) SearchBar(model, onClose = closeSearch)
                else ReaderTopBar(model, document, editing,
                    onSearch = { stopEditing(); searchOpen = true },
                    onToggleEdit = { if (editing) stopEditing() else editing = true },
                    onSaveCopy = onSaveCopy, onShare = { shareDocument(context, model, document) })
            }
        },
        bottomBar = {
            AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut()) {
                ReaderBottomBar(model, document, pageView, editing, onGoToPage = { goToPage = true },
                    onSignature = { showSignature = true }, onDone = stopEditing)
            }
        }
    ) { padding ->
        val pageDescription = stringResource(R.string.reader_page_description, document.page + 1, model.count)
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { pageView }, modifier = Modifier.fillMaxSize(), update = {
                it.show(model.bitmap, document.page)
                it.isEnabled = !model.busy
                it.showOverlays(model.size.width, model.size.height, model.searchBounds, model.selection.bounds, model.tool)
                it.onTap = { chrome = !chrome }
                it.onPageSwipe = { direction -> model.page(document.page + direction) }
                it.onSelection = if (model.advanced) model::select else null
                it.onArea = if (model.annotationsAvailable) model::receiveArea else null
                it.onInk = if (model.annotationsAvailable) model::addInk else null
                it.contentDescription = pageDescription
            })
            if (model.tool != PdfTool.NONE) ToolHint(model, Modifier.align(Alignment.TopCenter))
            if (selecting) SelectionBar(model, Modifier.align(Alignment.BottomCenter).padding(12.dp))
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    if (goToPage) GoToPageDialog(model.count, onGo = { model.page(it) }, onClose = { goToPage = false })
    if (showSignature) SignatureDialog(onClose = { showSignature = false },
        onReady = { model.prepareSignature(it); showSignature = false })
    ReaderSheets(model)
    ReaderEditDialogs(model)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(model: ReaderModel, document: LibraryDocument, editing: Boolean, onSearch: () -> Unit,
                         onToggleEdit: () -> Unit, onSaveCopy: () -> Unit, onShare: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(document.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (model.dirty) Text(stringResource(R.string.reader_unsaved), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                else Text(stringResource(R.string.reader_subtitle, pluralStringResource(R.plurals.pages, model.count, model.count)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = {
            IconButton(onClick = model::home, enabled = !model.busy) { Glyph(R.drawable.ic_back, stringResource(R.string.action_back)) }
        },
        actions = {
            if (model.advanced) {
                IconButton(onClick = onSearch, enabled = !model.busy) {
                    Glyph(R.drawable.ic_search, stringResource(R.string.reader_search))
                }
                FilledTonalIconToggleButton(checked = editing, onCheckedChange = { onToggleEdit() }, enabled = !model.busy,
                    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(containerColor = Color.Transparent)) {
                    Glyph(R.drawable.ic_edit, stringResource(R.string.reader_edit))
                }
            }
            if (model.dirty) IconButton(onClick = model::saveOriginal, enabled = !model.busy) {
                Glyph(R.drawable.ic_save, stringResource(R.string.reader_save), tint = MaterialTheme.colorScheme.primary)
            }
            ReaderMenu(model, onSaveCopy, onShare)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun ReaderMenu(model: ReaderModel, onSaveCopy: () -> Unit, onShare: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = !model.busy) { Glyph(R.drawable.ic_more, stringResource(R.string.action_more)) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            fun run(action: () -> Unit) { expanded = false; action() }
            DropdownMenuItem(text = { Text(stringResource(R.string.reader_page_text)) }, enabled = model.advanced,
                leadingIcon = { Glyph(R.drawable.ic_article, null) }, onClick = { run(model::showText) })
            DropdownMenuItem(text = { Text(stringResource(R.string.reader_share)) },
                leadingIcon = { Glyph(R.drawable.ic_share, null) }, onClick = { run(onShare) })
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.reader_save)) }, enabled = model.dirty,
                leadingIcon = { Glyph(R.drawable.ic_save, null) }, onClick = { run(model::saveOriginal) })
            DropdownMenuItem(text = { Text(stringResource(R.string.reader_save_copy)) },
                leadingIcon = { Glyph(R.drawable.ic_save_copy, null) }, onClick = { run(onSaveCopy) })
            val limitation = when {
                !model.advanced -> R.string.reader_no_text_features
                !model.annotationsAvailable -> R.string.reader_no_annotation_features
                else -> null
            }
            if (limitation != null) {
                HorizontalDivider()
                Text(stringResource(limitation), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 260.dp).padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun SearchBar(model: ReaderModel, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = { if (query.isNotBlank() && !model.busy) { submitted = true; model.search(query) } }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Glyph(R.drawable.ic_back, stringResource(R.string.search_close)) }
                TextField(query, { query = it }, Modifier.weight(1f).focusRequester(focus).testTag("search-field"),
                    placeholder = { Text(stringResource(R.string.search_hint)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; submitted = false; model.clearSearch() }) {
                            Glyph(R.drawable.ic_close, stringResource(R.string.search_clear))
                        }
                    },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                val counter = when {
                    model.searching -> stringResource(R.string.search_running)
                    model.hits.isNotEmpty() -> stringResource(
                        if (model.hits.size >= ReaderModel.MAX_SEARCH_HITS) R.string.search_count_limited else R.string.search_count,
                        model.hitIndex + 1, model.hits.size)
                    submitted -> stringResource(R.string.search_none)
                    else -> ""
                }
                Text(counter, Modifier.testTag("search-count").padding(horizontal = 4.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { model.moveHit(-1) }, enabled = model.hits.isNotEmpty() && !model.busy) {
                    Glyph(R.drawable.ic_arrow_up, stringResource(R.string.search_previous))
                }
                IconButton(onClick = { model.moveHit(1) }, enabled = model.hits.isNotEmpty() && !model.busy) {
                    Glyph(R.drawable.ic_arrow_down, stringResource(R.string.search_next))
                }
            }
            if (model.searching) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            else HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ReaderBottomBar(model: ReaderModel, document: LibraryDocument, pageView: ZoomPageView, editing: Boolean,
                            onGoToPage: () -> Unit, onSignature: () -> Unit, onDone: () -> Unit) {
    Surface(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large, tonalElevation = 2.dp, shadowElevation = 2.dp) {
        Column {
            AnimatedVisibility(editing && model.advanced, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    EditPalette(model, onSignature, onDone)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { model.page(document.page - 1) }, enabled = !model.busy && document.page > 0) {
                    Glyph(R.drawable.ic_previous, stringResource(R.string.reader_previous))
                }
                TextButton(onClick = onGoToPage, enabled = !model.busy, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(stringResource(R.string.reader_page_indicator, document.page + 1, model.count), fontSize = 13.sp)
                }
                IconButton(onClick = { model.page(document.page + 1) }, enabled = !model.busy && document.page + 1 < model.count) {
                    Glyph(R.drawable.ic_next, stringResource(R.string.reader_next))
                }
                IconButton(onClick = { pageView.zoomBy(1f / 1.5f) }) { Glyph(R.drawable.ic_zoom_out, stringResource(R.string.reader_zoom_out)) }
                IconButton(onClick = pageView::fit) { Glyph(R.drawable.ic_fit, stringResource(R.string.reader_fit)) }
                IconButton(onClick = { pageView.zoomBy(1.5f) }) { Glyph(R.drawable.ic_zoom_in, stringResource(R.string.reader_zoom_in)) }
            }
            LinearProgressIndicator(progress = { (document.page + 1).toFloat() / model.count.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
        }
    }
}

/** Editing tools offered by the device's PDF module. Text selection and forms need the text APIs only. */
@Composable
private fun EditPalette(model: ReaderModel, onSignature: () -> Unit, onDone: () -> Unit) {
    val enabled = !model.busy
    fun toggle(tool: PdfTool) = model.chooseTool(if (model.tool == tool) PdfTool.NONE else tool)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val scroll = rememberScrollState()
        Row(Modifier.weight(1f).horizontalFadingEdge(scroll, 28.dp).horizontalScroll(scroll).padding(horizontal = 4.dp)) {
            ToolButton(R.drawable.ic_select, stringResource(R.string.tool_select), { toggle(PdfTool.SELECT) },
                enabled = enabled, selected = model.tool == PdfTool.SELECT)
            if (model.annotationsAvailable) {
                ToolButton(R.drawable.ic_note, stringResource(R.string.tool_note), { toggle(PdfTool.NOTE) },
                    enabled = enabled, selected = model.tool == PdfTool.NOTE)
                ToolButton(R.drawable.ic_draw, stringResource(R.string.tool_draw), { toggle(PdfTool.DRAW) },
                    enabled = enabled, selected = model.tool == PdfTool.DRAW)
                ToolButton(R.drawable.ic_signature, stringResource(R.string.tool_signature),
                    { if (model.tool == PdfTool.SIGNATURE) model.chooseTool(PdfTool.NONE) else onSignature() },
                    enabled = enabled, selected = model.tool == PdfTool.SIGNATURE)
            }
            ToolButton(R.drawable.ic_form, stringResource(R.string.tool_forms), model::showFields, enabled = enabled)
            if (model.annotationsAvailable) {
                ToolButton(R.drawable.ic_annotations, stringResource(R.string.tool_annotations), model::showAnnotations, enabled = enabled)
                ToolButton(R.drawable.ic_undo, stringResource(R.string.tool_undo), model::undo, enabled = enabled && model.canUndo)
                ToolButton(R.drawable.ic_redo, stringResource(R.string.tool_redo), model::redo, enabled = enabled && model.canRedo)
            }
        }
        VerticalDivider(Modifier.height(40.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ToolButton(R.drawable.ic_check, stringResource(R.string.tool_done), onDone, Modifier.padding(horizontal = 4.dp))
    }
}

/** Fades the clipped side(s) of a horizontally scrolling row so hidden tools are discoverable. */
private fun Modifier.horizontalFadingEdge(state: ScrollState, length: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = length.toPx().coerceAtMost(size.width / 2)
        if (state.value > 0) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, edge),
            size = Size(edge, size.height), blendMode = BlendMode.DstIn)
        if (state.value < state.maxValue) drawRect(
            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - edge, size.width),
            topLeft = Offset(size.width - edge, 0f), size = Size(edge, size.height), blendMode = BlendMode.DstIn)
    }

@Composable
private fun ToolHint(model: ReaderModel, modifier: Modifier) {
    val hint = when (model.tool) {
        PdfTool.SELECT -> R.string.hint_select
        PdfTool.DRAW -> R.string.hint_draw
        PdfTool.NOTE -> R.string.hint_note
        PdfTool.SIGNATURE -> R.string.hint_signature
        PdfTool.MOVE -> R.string.hint_move
        PdfTool.NONE -> return
    }
    Surface(modifier.padding(12.dp).widthIn(max = 560.dp), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 3.dp) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(hint), Modifier.weight(1f, fill = false).padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { model.chooseTool(PdfTool.NONE) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun SelectionBar(model: ReaderModel, modifier: Modifier) {
    val context = LocalContext.current
    val copied = stringResource(R.string.message_copied)
    val enabled = !model.busy
    Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(Modifier.padding(4.dp)) {
            ToolButton(R.drawable.ic_copy, stringResource(R.string.selection_copy), {
                if (copyText(context, context.getString(R.string.app_name), model.selection.text)) model.notify(copied)
                model.chooseTool(PdfTool.NONE)
            })
            if (model.annotationsAvailable) {
                ToolButton(R.drawable.ic_highlight, stringResource(R.string.selection_highlight), { model.addMarkup(MarkupStyle.HIGHLIGHT) }, enabled = enabled)
                ToolButton(R.drawable.ic_underline, stringResource(R.string.selection_underline), { model.addMarkup(MarkupStyle.UNDERLINE) }, enabled = enabled)
                ToolButton(R.drawable.ic_strikethrough, stringResource(R.string.selection_strike), { model.addMarkup(MarkupStyle.STRIKEOUT) }, enabled = enabled)
            }
            ToolButton(R.drawable.ic_close, stringResource(R.string.selection_clear), { model.chooseTool(PdfTool.NONE) })
        }
    }
}
