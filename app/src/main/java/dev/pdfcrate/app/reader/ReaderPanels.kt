package dev.pdfcrate.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun ReaderMenu(model: ReaderModel, onSearch: () -> Unit, onSaveCopy: () -> Unit, onSignature: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = !model.busy) {
            Icon(painterResource(R.drawable.ic_more), "Ferramentas do PDF", Modifier.size(22.dp))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            fun run(action: () -> Unit) { expanded = false; action() }
            DropdownMenuItem(text = { Text("Buscar texto") }, enabled = model.advanced, onClick = { run(onSearch) })
            DropdownMenuItem(text = { Text("Selecionar texto") }, enabled = model.advanced, onClick = { run { model.chooseTool(PdfTool.SELECT) } })
            DropdownMenuItem(text = { Text("Texto desta página") }, enabled = model.advanced, onClick = { run(model::showText) })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Nota de texto") }, enabled = model.annotationsAvailable, onClick = { run { model.chooseTool(PdfTool.NOTE) } })
            DropdownMenuItem(text = { Text("Desenhar") }, enabled = model.annotationsAvailable, onClick = { run { model.chooseTool(PdfTool.DRAW) } })
            DropdownMenuItem(text = { Text("Assinatura desenhada") }, enabled = model.annotationsAvailable, onClick = { run(onSignature) })
            DropdownMenuItem(text = { Text("Anotações desta página") }, enabled = model.annotationsAvailable, onClick = { run(model::showAnnotations) })
            DropdownMenuItem(text = { Text("Formulários desta página") }, enabled = model.advanced, onClick = { run(model::showFields) })
            DropdownMenuItem(text = { Text("Desfazer anotação") }, enabled = model.canUndo, onClick = { run(model::undo) })
            DropdownMenuItem(text = { Text("Refazer anotação") }, enabled = model.canRedo, onClick = { run(model::redo) })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Salvar alterações") }, enabled = model.dirty, onClick = { run(model::saveOriginal) })
            DropdownMenuItem(text = { Text("Salvar cópia…") }, onClick = { run(onSaveCopy) })
            if (!model.advanced) DropdownMenuItem(text = { Text("Recursos de texto e edição indisponíveis neste sistema") }, enabled = false, onClick = {})
            else if (!model.annotationsAvailable) DropdownMenuItem(text = { Text("Este sistema ainda não oferece edição de anotações") }, enabled = false, onClick = {})
        }
    }
}

@Composable
fun ReaderExtraBar(model: ReaderModel, showSearch: Boolean, onCloseSearch: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        if (showSearch) {
            var query by remember { mutableStateOf("") }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("Buscar no PDF") }, singleLine = true)
                TextButton(onClick = { model.search(query) }, enabled = !model.busy && !model.searching && query.isNotBlank()) { Text("Buscar") }
                IconButton(onClick = { model.clearSearch(); onCloseSearch() }) {
                    Icon(painterResource(R.drawable.ic_close), "Fechar busca")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { model.moveHit(-1) }, enabled = model.hits.isNotEmpty() && !model.busy) { Text("Anterior resultado") }
                Text(if (model.searching) "Buscando…" else if (model.hits.isEmpty()) "Sem resultados" else
                    "${model.hitIndex + 1} / ${model.hits.size}${if (model.hits.size == 5000) "+" else ""}", Modifier.padding(top = 14.dp).testTag("search-count"))
                TextButton(onClick = { model.moveHit(1) }, enabled = model.hits.isNotEmpty() && !model.busy) { Text("Próximo resultado") }
            }
        }
        if (model.tool != PdfTool.NONE) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(when (model.tool) {
                PdfTool.SELECT -> "Arraste sobre o texto para selecionar."
                PdfTool.DRAW -> "Desenhe sobre a página."
                PdfTool.NOTE -> "Arraste para definir a caixa de texto."
                PdfTool.SIGNATURE -> "Arraste para posicionar e dimensionar a assinatura."
                PdfTool.MOVE -> "Arraste para definir a nova posição e tamanho."
                else -> ""
            }, Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { model.chooseTool(PdfTool.NONE) }) { Text("Cancelar") }
        }
        if (model.selection.text.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { copyText(context, model.selection.text); model.chooseTool(PdfTool.NONE) }) { Text("Copiar") }
                TextButton(onClick = { model.addHighlight("Destaque") }, enabled = model.annotationsAvailable) { Text("Destacar") }
                TextButton(onClick = { model.chooseTool(PdfTool.NONE) }) { Text("Limpar") }
            }
            if (model.annotationsAvailable) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { model.addHighlight("Sublinhado") }) { Text("Sublinhar") }
                TextButton(onClick = { model.addHighlight("Tachado") }) { Text("Tachar") }
            }
        }
        if (model.dirty) Text("Alterações não salvas", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun NativeDialogs(model: ReaderModel, showSignature: Boolean, closeSignature: () -> Unit, onSaveCopy: () -> Unit) {
    val context = LocalContext.current
    model.passwordDocument?.let { doc ->
        var password by remember(doc.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = model::cancelPassword, title = { Text("Abrir PDF protegido") },
            text = { Column {
                Text(doc.name)
                OutlinedTextField(password, { password = it }, label = { Text("Senha do PDF") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = model.passwordIncorrect)
                if (model.passwordIncorrect) Text("Senha incorreta ou proteção não compatível.", style = MaterialTheme.typography.bodySmall)
                Text("A senha não será guardada no histórico.", style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(enabled = password.isNotEmpty() && !model.busy,
                onClick = { model.submitPassword(password); password = "" }) { Text("Abrir") } },
            dismissButton = { TextButton(onClick = model::cancelPassword) { Text("Cancelar") } })
    }
    if (model.discardPrompt) AlertDialog(onDismissRequest = model::cancelDiscard,
        title = { Text("Há alterações não salvas") }, text = { Text("Salve antes de sair para manter suas alterações no PDF.") },
        confirmButton = { TextButton(onClick = { model.cancelDiscard(); onSaveCopy() }) { Text("Salvar cópia") } },
        dismissButton = { Row {
            TextButton(onClick = model::cancelDiscard) { Text("Continuar editando") }
            TextButton(onClick = model::discardAndContinue) { Text("Descartar e sair") }
        } })
    model.pageText?.let { text -> AlertDialog(onDismissRequest = model::closePanels, title = { Text("Texto desta página") },
        text = { SelectionContainer { Text(text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = { copyText(context, text) }) { Text("Copiar texto") } },
        dismissButton = { TextButton(onClick = model::closePanels) { Text("Fechar") } }) }
    model.noteBounds?.let {
        var text by remember(it) { mutableStateOf("") }
        AlertDialog(onDismissRequest = model::cancelNote, title = { Text("Nota de texto") },
            text = { OutlinedTextField(text, { text = it.take(5000) }, label = { Text("Texto da anotação") }, minLines = 3) },
            confirmButton = { TextButton(onClick = { model.addNote(text) }, enabled = text.isNotBlank()) { Text("Adicionar") } },
            dismissButton = { TextButton(onClick = model::cancelNote) { Text("Cancelar") } })
    }
    if (showSignature) SignatureDialog(onClose = closeSignature, onReady = { model.prepareSignature(it); closeSignature() })
    model.fields?.let { items -> FormDialog(items, model) }
    model.annotations?.let { items -> AnnotationDialog(items, model) }
}

@Composable
private fun FormDialog(items: List<PdfField>, model: ReaderModel) {
    var editing by remember { mutableStateOf<PdfField?>(null) }
    AlertDialog(onDismissRequest = model::closePanels, title = { Text("Formulários desta página") },
        text = { Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
            if (items.isEmpty()) Text("Não há campos compatíveis de formulário nesta página.")
            items.forEach { field ->
                Text(field.label, style = MaterialTheme.typography.titleSmall)
                if (field.text.isNotEmpty() && field.kind == FieldKind.TEXT) Text(field.text, style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !field.readOnly && field.kind != FieldKind.OTHER && !model.busy, onClick = {
                    if (field.kind == FieldKind.CHECKBOX || field.kind == FieldKind.RADIO)
                        model.setField(FieldChange(field.index, click = Point(field.bounds.centerX(), field.bounds.centerY())))
                    else editing = field
                }) { Text(if (field.readOnly) "Somente leitura" else when (field.kind) {
                    FieldKind.CHECKBOX -> "Alternar marcação"; FieldKind.RADIO -> "Selecionar opção"; FieldKind.OTHER -> "Tipo não editável"; else -> "Preencher"
                }) }
                HorizontalDivider()
            }
        } }, confirmButton = { TextButton(onClick = model::closePanels) { Text("Fechar") } })
    editing?.let { field ->
        var value by remember(field.index) { mutableStateOf(field.text) }
        val selected = remember(field.index) { mutableStateListOf<Int>().apply { addAll(field.choices.indices.filter { field.choices[it].selected }) } }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(field.label) }, text = {
            if (field.kind == FieldKind.TEXT) OutlinedTextField(value,
                { value = if (field.maxLength > 0) it.take(field.maxLength) else it.take(100000) },
                singleLine = !field.multiline, minLines = if (field.multiline) 3 else 1, label = { Text("Valor do campo") })
            else Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                field.choices.forEachIndexed { index, choice ->
                    Row {
                        Checkbox(index in selected, onCheckedChange = { checked ->
                            if (!field.multiSelect) selected.clear()
                            if (checked) selected.add(index) else selected.remove(index)
                        })
                        Text(choice.label, Modifier.padding(top = 14.dp))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = {
            model.setField(if (field.kind == FieldKind.TEXT) FieldChange(field.index, text = value)
                else FieldChange(field.index, indices = selected.sorted().toIntArray()))
            editing = null
        }) { Text("Aplicar") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun AnnotationDialog(items: List<PdfAnnotationEntry>, model: ReaderModel) {
    var editing by remember { mutableStateOf<PdfAnnotationEntry?>(null) }
    AlertDialog(onDismissRequest = model::closePanels, title = { Text("Anotações desta página") },
        text = { Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
            if (items.isEmpty()) Text("Não há anotações compatíveis nesta página.")
            items.forEach { annotation ->
                Text(annotation.kind, style = MaterialTheme.typography.titleSmall)
                if (annotation.text.isNotBlank()) Text(annotation.text, style = MaterialTheme.typography.bodySmall)
                Row {
                    if (annotation.kind == "Texto") TextButton(onClick = { editing = annotation }) { Text("Editar") }
                    TextButton(onClick = { model.prepareMove(annotation.id) }) { Text("Mover/tamanho") }
                    TextButton(onClick = { model.removeAnnotation(annotation.id) }, enabled = !model.busy) { Text("Apagar") }
                }
                if (annotation.kind == "Destaque") Row {
                    listOf("Amarelo" to 0xFFFFD54F.toInt(), "Verde" to 0xFF81C784.toInt(), "Rosa" to 0xFFF48FB1.toInt()).forEach { (label, color) ->
                        TextButton(onClick = { model.recolor(annotation.id, color) }, enabled = !model.busy) { Text(label) }
                    }
                }
                HorizontalDivider()
            }
        } }, confirmButton = { TextButton(onClick = model::closePanels) { Text("Fechar") } })
    editing?.let { annotation ->
        var value by remember(annotation.id) { mutableStateOf(annotation.text) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Editar texto") },
            text = { OutlinedTextField(value, { value = it.take(5000) }, minLines = 3) },
            confirmButton = { TextButton(onClick = { model.changeNote(annotation.id, value); editing = null }) { Text("Aplicar") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun SignatureDialog(onClose: () -> Unit, onReady: (List<List<PointF>>) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Desenhe sua assinatura") },
        text = { Column {
            Text("Depois, arraste na página para escolher posição e tamanho. Não é assinatura digital com certificado.", style = MaterialTheme.typography.bodySmall)
            Surface(color = Color.White, modifier = Modifier.fillMaxWidth().height(180.dp)) {
                Canvas(Modifier.fillMaxSize().onSizeChanged { size = it }.pointerInput(Unit) {
                    detectDragGestures(onDragStart = { current = listOf(it) }, onDragEnd = { strokes.add(current); current = emptyList() },
                        onDragCancel = { current = emptyList() }) { change, _ ->
                        change.consume(); if (current.size < 12000) current = current + change.position
                    }
                }) {
                    (strokes + listOf(current)).forEach { stroke -> stroke.zipWithNext().forEach { (a, b) -> drawLine(Color.Black, a, b, strokeWidth = 3f) } }
                }
            }
            TextButton(onClick = { strokes.clear(); current = emptyList() }) { Text("Limpar assinatura") }
        } }, confirmButton = { TextButton(enabled = strokes.any { it.size > 1 }, onClick = {
            onReady(strokes.map { stroke -> stroke.map { PointF((it.x / size.width).coerceIn(0f, 1f), (it.y / size.height).coerceIn(0f, 1f)) } })
        }) { Text("Posicionar") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}

private fun copyText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PDFCrate", text))
}
