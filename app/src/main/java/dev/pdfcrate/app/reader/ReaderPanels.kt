package dev.pdfcrate.app.reader

import android.graphics.Point
import android.graphics.PointF
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.pdfcrate.app.BuildConfig
import dev.pdfcrate.app.R
import dev.pdfcrate.app.pdf.*
import dev.pdfcrate.app.ui.components.Glyph
import dev.pdfcrate.app.ui.components.copyText
import kotlinx.coroutines.launch

/** Dialogs that can appear over the library or the reader. */
@Composable
fun GlobalDialogs(model: ReaderModel, onLocate: () -> Unit, onSaveCopy: () -> Unit) {
    model.passwordDocument?.let { document -> PasswordDialog(model, document.name, document.id) }
    if (model.discardPrompt) AlertDialog(onDismissRequest = model::cancelDiscard,
        icon = { Glyph(R.drawable.ic_save, null) },
        title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_body)) },
        confirmButton = { TextButton(onClick = { model.cancelDiscard(); onSaveCopy() }) { Text(stringResource(R.string.discard_save_copy)) } },
        dismissButton = {
            Row {
                TextButton(onClick = model::discardAndContinue,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.discard_confirm))
                }
                TextButton(onClick = model::cancelDiscard) { Text(stringResource(R.string.discard_keep)) }
            }
        })
    model.error?.let { message ->
        AlertDialog(onDismissRequest = model::dismissError,
            icon = { Glyph(R.drawable.ic_warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.error_title)) }, text = { Text(message) },
            confirmButton = {
                when {
                    model.locate != null -> TextButton(onClick = onLocate) { Text(stringResource(R.string.error_locate)) }
                    model.recovery != null && model.current != null ->
                        TextButton(onClick = { model.dismissError(); onSaveCopy() }) { Text(stringResource(R.string.error_save_copy)) }
                    else -> TextButton(onClick = model::dismissError) { Text(stringResource(R.string.action_ok)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { model.dismissError(); model.cancelLocate() }) { Text(stringResource(R.string.action_close)) }
            })
    }
}

@Composable
private fun PasswordDialog(model: ReaderModel, name: String, id: String) {
    var password by remember(id) { mutableStateOf("") }
    var visible by remember(id) { mutableStateOf(false) }
    val submit = { if (password.isNotEmpty() && !model.busy) { model.submitPassword(password); password = "" } }
    AlertDialog(onDismissRequest = model::cancelPassword,
        icon = { Glyph(R.drawable.ic_lock, null) },
        title = { Text(stringResource(R.string.password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.password_label)) }, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = model.passwordIncorrect,
                    supportingText = if (model.passwordIncorrect) { { Text(stringResource(R.string.password_incorrect)) } } else null,
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Glyph(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                stringResource(if (visible) R.string.password_hide else R.string.password_show))
                        }
                    })
                Text(stringResource(R.string.password_not_stored), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = password.isNotEmpty() && !model.busy, onClick = submit) { Text(stringResource(R.string.action_open)) } },
        dismissButton = { TextButton(onClick = model::cancelPassword) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
fun AboutDialog(model: ReaderModel, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose,
        icon = { Image(painterResource(R.drawable.ic_pdfcrate), null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) },
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.about_body))
                Text(stringResource(R.string.about_privacy))
                Text(stringResource(R.string.about_capabilities), style = MaterialTheme.typography.titleSmall)
                Capability(stringResource(R.string.about_cap_reading), true)
                Capability(stringResource(R.string.about_cap_text), NativePdfSupport.advanced)
                Capability(stringResource(R.string.about_cap_annotations), NativePdfSupport.annotations)
                Text(stringResource(R.string.about_system, Build.VERSION.RELEASE, NativePdfSupport.extension) + "\n" +
                    stringResource(R.string.about_limits), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
        dismissButton = {
            TextButton(onClick = { onClose(); model.sample() }, enabled = !model.busy) { Text(stringResource(R.string.action_try_sample)) }
        })
}

@Composable
private fun Capability(label: String, available: Boolean) {
    val status = stringResource(if (available) R.string.about_cap_available else R.string.about_cap_unavailable)
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Glyph(if (available) R.drawable.ic_check else R.drawable.ic_close, null,
            tint = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun GoToPageDialog(count: Int, onGo: (Int) -> Unit, onClose: () -> Unit) {
    var target by remember { mutableStateOf("") }
    val number = target.toIntOrNull()?.takeIf { it in 1..count }
    val go = { if (number != null) { onGo(number - 1); onClose() } }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.go_to_page_title)) },
        text = {
            OutlinedTextField(value = target, onValueChange = { target = it.filter(Char::isDigit).take(8) },
                label = { Text(stringResource(R.string.go_to_page_label, count)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }))
        },
        confirmButton = { TextButton(enabled = number != null, onClick = go) { Text(stringResource(R.string.action_go)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } })
}

/** Text for a new note box; the box itself was dragged on the page. */
@Composable
internal fun ReaderEditDialogs(model: ReaderModel) {
    model.noteBounds?.let { bounds ->
        var text by remember(bounds) { mutableStateOf("") }
        AlertDialog(onDismissRequest = model::cancelNote,
            icon = { Glyph(R.drawable.ic_note, null) },
            title = { Text(stringResource(R.string.note_title)) },
            text = {
                OutlinedTextField(text, { text = it.take(MAX_NOTE_LENGTH) }, label = { Text(stringResource(R.string.note_label)) },
                    minLines = 3, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = { model.addNote(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_add)) } },
            dismissButton = { TextButton(onClick = model::cancelNote) { Text(stringResource(R.string.action_cancel)) } })
    }
}

@Composable
internal fun SignatureDialog(onClose: () -> Unit, onReady: (List<List<PointF>>) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val padDescription = stringResource(R.string.signature_pad)
    val guide = MaterialTheme.colorScheme.outline
    AlertDialog(onDismissRequest = onClose,
        icon = { Glyph(R.drawable.ic_signature, null) },
        title = { Text(stringResource(R.string.signature_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.signature_body), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Paper stays white so the ink preview matches the black strokes written to the PDF.
                Surface(color = Color.White, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)) {
                    Canvas(Modifier.fillMaxSize().semantics { contentDescription = padDescription }
                        .onSizeChanged { size = it }.pointerInput(Unit) {
                            detectDragGestures(onDragStart = { current = listOf(it) },
                                onDragEnd = { strokes.add(current); current = emptyList() },
                                onDragCancel = { current = emptyList() }) { change, _ ->
                                change.consume()
                                if (current.size < MAX_STROKE_POINTS) current = current + change.position
                            }
                        }) {
                        val baseline = this.size.height * .78f
                        drawLine(guide, Offset(24f, baseline), Offset(this.size.width - 24f, baseline), strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                        (strokes + listOf(current)).forEach { stroke ->
                            stroke.zipWithNext().forEach { (a, b) -> drawLine(Color.Black, a, b, strokeWidth = 4f, cap = StrokeCap.Round) }
                        }
                    }
                }
                TextButton(onClick = { strokes.clear(); current = emptyList() }, enabled = strokes.isNotEmpty()) {
                    Glyph(R.drawable.ic_trash, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.signature_clear))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = strokes.any { it.size > 1 }, onClick = {
                onReady(strokes.map { stroke ->
                    stroke.map { PointF((it.x / size.width).coerceIn(0f, 1f), (it.y / size.height).coerceIn(0f, 1f)) }
                })
            }) { Text(stringResource(R.string.signature_place)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } })
}

/** Page panels: extracted text, form fields and annotations. */
@Composable
internal fun ReaderSheets(model: ReaderModel) {
    model.pageText?.let { text -> PageTextSheet(model, text) }
    model.fields?.let { fields -> FormSheet(model, fields) }
    model.annotations?.let { entries -> AnnotationSheet(model, entries) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelSheet(title: String, onDismiss: () -> Unit, actions: (@Composable RowScope.() -> Unit)? = null,
                       content: LazyListScope.() -> Unit) {
    val state = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }) {
                Glyph(R.drawable.ic_close, stringResource(R.string.panel_close))
            }
        }
        if (actions != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), content = actions)
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 24.dp),
            content = content)
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PageTextSheet(model: ReaderModel, text: String) {
    val context = LocalContext.current
    val copied = stringResource(R.string.message_copied)
    PanelSheet(stringResource(R.string.reader_page_text), onDismiss = model::closePanels, actions = {
        if (text.isNotBlank()) TextButton(onClick = {
            if (copyText(context, context.getString(R.string.app_name), text)) model.notify(copied)
        }) {
            Glyph(R.drawable.ic_copy, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.page_text_copy))
        }
    }) {
        item {
            if (text.isBlank()) EmptyPanel(stringResource(R.string.page_text_empty))
            else SelectionContainer {
                Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FormSheet(model: ReaderModel, fields: List<PdfField>) {
    var editing by remember { mutableStateOf<PdfField?>(null) }
    PanelSheet(stringResource(R.string.reader_forms), onDismiss = model::closePanels) {
        if (fields.isEmpty()) item { EmptyPanel(stringResource(R.string.forms_empty)) }
        items(fields, key = { it.index }) { field -> FieldRow(model, field, onEdit = { editing = field }) }
    }
    editing?.let { field -> FieldEditor(model, field, onClose = { editing = null }) }
}

@Composable
private fun fieldLabel(field: PdfField) = field.label.ifBlank { stringResource(R.string.field_fallback, field.index + 1) }

@Composable
private fun FieldRow(model: ReaderModel, field: PdfField, onEdit: () -> Unit) {
    val actionable = !field.readOnly && field.kind != FieldKind.OTHER
    val toggles = field.kind == FieldKind.CHECKBOX || field.kind == FieldKind.RADIO
    val act = {
        if (toggles) model.setField(FieldChange(field.index, click = Point(field.bounds.centerX(), field.bounds.centerY())))
        else onEdit()
    }
    val kind = stringResource(when (field.kind) {
        FieldKind.TEXT -> R.string.field_kind_text
        FieldKind.CHECKBOX -> R.string.field_kind_checkbox
        FieldKind.RADIO -> R.string.field_kind_radio
        FieldKind.COMBO -> R.string.field_kind_combo
        FieldKind.LIST -> R.string.field_kind_list
        FieldKind.OTHER -> R.string.field_kind_other
    })
    val value = when (field.kind) {
        FieldKind.TEXT -> field.text.ifEmpty { stringResource(R.string.field_empty) }
        FieldKind.COMBO, FieldKind.LIST -> field.choices.filter { it.selected }.joinToString { it.label }
            .ifEmpty { stringResource(R.string.field_empty) }
        else -> null
    }
    ListItem(
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = actionable && !model.busy, onClick = act),
        leadingContent = {
            Glyph(when (field.kind) {
                FieldKind.CHECKBOX, FieldKind.RADIO -> R.drawable.ic_check
                FieldKind.COMBO, FieldKind.LIST -> R.drawable.ic_form
                else -> R.drawable.ic_edit
            }, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(fieldLabel(field)) },
        supportingContent = {
            Text(listOfNotNull(kind, value, if (field.readOnly) stringResource(R.string.field_read_only) else null).joinToString(" · "),
                maxLines = 2)
        },
        trailingContent = {
            if (field.readOnly) Glyph(R.drawable.ic_lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (actionable) TextButton(onClick = act, enabled = !model.busy) {
                Text(stringResource(when (field.kind) {
                    FieldKind.CHECKBOX -> R.string.field_toggle
                    FieldKind.RADIO -> R.string.field_select_option
                    FieldKind.TEXT -> R.string.field_fill
                    else -> R.string.field_choose
                }))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun FieldEditor(model: ReaderModel, field: PdfField, onClose: () -> Unit) {
    var value by remember(field.index) { mutableStateOf(field.text) }
    val selected = remember(field.index) {
        mutableStateListOf<Int>().apply { addAll(field.choices.indices.filter { field.choices[it].selected }) }
    }
    val apply = {
        model.setField(if (field.kind == FieldKind.TEXT) FieldChange(field.index, text = value)
            else FieldChange(field.index, indices = selected.sorted().toIntArray()))
        onClose()
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(fieldLabel(field)) },
        text = {
            if (field.kind == FieldKind.TEXT) OutlinedTextField(value,
                { value = if (field.maxLength > 0) it.take(field.maxLength) else it.take(MAX_FIELD_LENGTH) },
                singleLine = !field.multiline, minLines = if (field.multiline) 3 else 1, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_value_label)) },
                supportingText = if (field.maxLength > 0) { { Text("${value.length} / ${field.maxLength}") } } else null)
            else Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                field.choices.forEachIndexed { index, choice ->
                    val checked = index in selected
                    val change = { on: Boolean ->
                        if (!field.multiSelect) selected.clear()
                        if (on) selected.add(index) else selected.remove(index)
                        Unit
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
                        .then(if (field.multiSelect) Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = change)
                            // Tapping the chosen option again clears it, as in 0.4: some fields accept no value.
                            else Modifier.selectable(checked, role = Role.RadioButton, onClick = { change(!checked) }))
                        .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (field.multiSelect) Checkbox(checked, onCheckedChange = null) else RadioButton(checked, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = apply, enabled = !model.busy) { Text(stringResource(R.string.action_apply)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun AnnotationSheet(model: ReaderModel, entries: List<PdfAnnotationEntry>) {
    var editing by remember { mutableStateOf<PdfAnnotationEntry?>(null) }
    PanelSheet(stringResource(R.string.reader_annotations), onDismiss = model::closePanels) {
        if (entries.isEmpty()) item { EmptyPanel(stringResource(R.string.annotations_empty)) }
        items(entries, key = { it.id }) { entry -> AnnotationRow(model, entry, onEdit = { editing = entry }) }
    }
    editing?.let { entry ->
        var value by remember(entry.id) { mutableStateOf(entry.text) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(stringResource(R.string.annotation_edit_title)) },
            text = { OutlinedTextField(value, { value = it.take(MAX_NOTE_LENGTH) }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { model.changeNote(entry.id, value); editing = null }) { Text(stringResource(R.string.action_apply)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

@Composable
private fun AnnotationRow(model: ReaderModel, entry: PdfAnnotationEntry, onEdit: () -> Unit) {
    val enabled = !model.busy
    Column(Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Glyph(when (entry.kind) {
                    AnnotationKind.HIGHLIGHT -> R.drawable.ic_highlight
                    AnnotationKind.FREE_TEXT -> R.drawable.ic_note
                    AnnotationKind.STAMP -> R.drawable.ic_draw
                    AnnotationKind.OTHER -> R.drawable.ic_annotations
                }, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            headlineContent = {
                Text(stringResource(when (entry.kind) {
                    AnnotationKind.HIGHLIGHT -> R.string.annotation_kind_highlight
                    AnnotationKind.FREE_TEXT -> R.string.annotation_kind_text
                    AnnotationKind.STAMP -> R.string.annotation_kind_stamp
                    AnnotationKind.OTHER -> R.string.annotation_kind_other
                }))
            },
            supportingContent = if (entry.text.isNotBlank()) { { Text(entry.text, maxLines = 3) } } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        FlowRow(Modifier.padding(start = 52.dp, end = 8.dp), verticalArrangement = Arrangement.Center) {
            if (entry.kind == AnnotationKind.FREE_TEXT) IconTextButton(R.drawable.ic_edit, stringResource(R.string.annotation_edit),
                enabled, onEdit)
            IconTextButton(R.drawable.ic_move, stringResource(R.string.annotation_move), enabled) { model.prepareMove(entry.id) }
            IconTextButton(R.drawable.ic_trash, stringResource(R.string.annotation_delete), enabled,
                color = MaterialTheme.colorScheme.error) { model.removeAnnotation(entry.id) }
        }
        if (entry.kind == AnnotationKind.HIGHLIGHT) Row(Modifier.padding(start = 64.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.annotation_color), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            listOf(R.string.color_yellow, R.string.color_green, R.string.color_pink).zip(ReaderModel.HIGHLIGHT_COLORS.toList())
                .forEach { (label, color) -> ColorSwatch(stringResource(label), Color(color), enabled) { model.recolor(entry.id, color) } }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun IconTextButton(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) =
    IconTextButton(icon, label, enabled, Color.Unspecified, onClick)

@Composable
private fun IconTextButton(icon: Int, label: String, enabled: Boolean, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled,
        colors = if (color == Color.Unspecified) ButtonDefaults.textButtonColors() else ButtonDefaults.textButtonColors(contentColor = color)) {
        Glyph(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ColorSwatch(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f), CircleShape))
    }
}

private const val MAX_NOTE_LENGTH = 5000
private const val MAX_FIELD_LENGTH = 100_000
private const val MAX_STROKE_POINTS = 12_000
