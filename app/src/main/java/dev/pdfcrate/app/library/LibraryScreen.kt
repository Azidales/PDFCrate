package dev.pdfcrate.app.library

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pdfcrate.app.R
import dev.pdfcrate.app.reader.ReaderModel
import dev.pdfcrate.app.shareDocument
import dev.pdfcrate.app.ui.components.Glyph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(model: ReaderModel, snackbar: SnackbarHostState, onOpenFile: () -> Unit,
                  onExportBackup: () -> Unit, onAbout: () -> Unit) {
    var discardRecovery by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(R.drawable.ic_pdfcrate), null, Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold, fontSize = 23.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onAbout) { Glyph(R.drawable.ic_info, stringResource(R.string.library_about)) }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!model.busy) onOpenFile() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Glyph(R.drawable.ic_add, null) }, text = { Text(stringResource(R.string.library_open_file)) },
                shape = MaterialTheme.shapes.large
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LibraryContent(model, onExportBackup = onExportBackup, onDiscardRecovery = { discardRecovery = true })
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if (discardRecovery) AlertDialog(onDismissRequest = { discardRecovery = false },
        icon = { Glyph(R.drawable.ic_trash, null) },
        title = { Text(stringResource(R.string.recovery_discard_title)) },
        text = { Text(stringResource(R.string.recovery_discard_body)) },
        confirmButton = {
            TextButton(onClick = { model.discardRecovery(); discardRecovery = false },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.recovery_discard))
            }
        },
        dismissButton = { TextButton(onClick = { discardRecovery = false }) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun LibraryContent(model: ReaderModel, onExportBackup: () -> Unit, onDiscardRecovery: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = model.documents.filter { it.name.contains(query, ignoreCase = true) }
    val recent = if (query.isBlank()) filtered.firstOrNull() else null
    val remaining = if (recent == null) filtered else filtered.drop(1)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.library_subtitle), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.library_search_hint)) }, singleLine = true,
                shape = MaterialTheme.shapes.medium,
                leadingIcon = { Glyph(R.drawable.ic_search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Glyph(R.drawable.ic_close, stringResource(R.string.library_search_clear))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow))
            Spacer(Modifier.height(4.dp))
        }
        model.recovery?.let { item(key = "recovery") { RecoveryCard(model, onExportBackup, onDiscardRecovery) } }
        if (recent != null) item(key = "continue") { ContinueCard(recent, model) }
        if (remaining.isNotEmpty()) item(key = "previous-header") {
            Text(stringResource(R.string.library_previous), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        items(remaining, key = { it.id }) { document -> DocumentRow(document, model) }
        if (filtered.isEmpty()) item(key = "empty") { EmptyState(model, searching = model.documents.isNotEmpty()) }
    }
}

@Composable
private fun RecoveryCard(model: ReaderModel, onExportBackup: () -> Unit, onDiscardRecovery: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Glyph(R.drawable.ic_warning, null)
                Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.recovery_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = model::openRecovery, enabled = !model.busy,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError)) {
                Glyph(R.drawable.ic_restore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.recovery_open))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (model.recovery?.backup != null) TextButton(onClick = onExportBackup,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                    Glyph(R.drawable.ic_export, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recovery_export))
                }
                TextButton(onClick = onDiscardRecovery,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                    Text(stringResource(R.string.recovery_discard))
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(document: LibraryDocument, model: ReaderModel) {
    Surface(onClick = { if (!model.busy) model.open(document) }, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 18.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Thumbnail(Modifier.size(70.dp, 98.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.library_continue), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Text(document.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (document.pages > 0) stringResource(R.string.library_page_progress, document.page + 1, document.pages)
                    else stringResource(R.string.library_page_only, document.page + 1), style = MaterialTheme.typography.bodySmall)
                if (!document.persistent && !model.library.isSample(document)) TemporaryAccessLabel()
                LinearProgressIndicator(progress = { (document.page + 1).toFloat() / document.pages.coerceAtLeast(document.page + 1) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .10f), drawStopIndicator = {})
            }
            DocumentMenu(document, model)
        }
    }
}

@Composable
private fun DocumentRow(document: LibraryDocument, model: ReaderModel) {
    Surface(onClick = { if (!model.busy) model.open(document) }, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Thumbnail(Modifier.size(48.dp, 66.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(document.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(metadata(document), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!document.persistent && !model.library.isSample(document)) TemporaryAccessLabel()
            }
            DocumentMenu(document, model)
        }
    }
}

@Composable
private fun TemporaryAccessLabel() {
    Text(stringResource(R.string.library_temporary_access), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary)
}

@Composable
private fun EmptyState(model: ReaderModel, searching: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Glyph(if (searching) R.drawable.ic_search else R.drawable.ic_document, null,
                Modifier.padding(22.dp).size(42.dp), MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(stringResource(if (searching) R.string.library_no_match_title else R.string.library_empty_title),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (searching) R.string.library_no_match_body else R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!searching) TextButton(onClick = model::sample, enabled = !model.busy) {
            Text(stringResource(R.string.action_try_sample))
        }
    }
}

@Composable
private fun DocumentMenu(document: LibraryDocument, model: ReaderModel) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = !model.busy) {
            Glyph(R.drawable.ic_more, stringResource(R.string.library_options, document.name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.library_share)) }, leadingIcon = { Glyph(R.drawable.ic_share, null) },
                onClick = { expanded = false; shareDocument(context, model, document) })
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.library_remove)) }, leadingIcon = { Glyph(R.drawable.ic_close, null) },
                onClick = { expanded = false; model.remove(document) })
        }
    }
}

/** A neutral page placeholder. The library never renders files in the background. */
@Composable
private fun Thumbnail(modifier: Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.extraSmall).background(Color.White)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(.9f, .75f, .85f, .6f).forEach { width ->
                Box(Modifier.fillMaxWidth(width).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD5DCD4)))
            }
        }
        Surface(color = Color(0xFF285C43), contentColor = Color.White, shape = RoundedCornerShape(topStart = 6.dp),
            modifier = Modifier.align(Alignment.BottomEnd)) {
            Text(stringResource(R.string.pdf_label), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
        }
    }
}

@Composable
private fun metadata(document: LibraryDocument): String {
    val context = LocalContext.current
    val pages = document.pages.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.pages, it, it) }
    return listOfNotNull(stringResource(R.string.pdf_label), pages,
        document.bytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }).joinToString(" · ")
}
