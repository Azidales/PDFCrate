package dev.pdfcrate.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Uri?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme(
                primary = Color(0xFFA8D2BC), onPrimary = Color(0xFF123526),
                primaryContainer = Color(0xFF254C3D), onPrimaryContainer = Color(0xFFD9EDDF),
                background = Color(0xFF121714), surface = Color(0xFF121714),
                surfaceContainer = Color(0xFF1C241F), surfaceContainerLow = Color(0xFF19201B)
            ) else lightColorScheme(
                primary = Color(0xFF285C43), onPrimary = Color.White,
                primaryContainer = Color(0xFFE1EBDD), onPrimaryContainer = Color(0xFF1A3928),
                background = Color(0xFFF6F7F2), surface = Color(0xFFF6F7F2),
                surfaceContainer = Color(0xFFEBEEE6), surfaceContainerLow = Color(0xFFF0F2EC),
                outlineVariant = Color(0xFFDCE2D7)
            )
            MaterialTheme(colorScheme = colors) {
                val model: ReaderModel = viewModel()
                LaunchedEffect(incoming) { incoming?.let { model.open(it); incoming = null } }
                PDFCrate(model)
            }
        }
        if (savedInstanceState == null) incoming = documentUri(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = documentUri(intent)
    }
    private fun documentUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
}

@Composable
private fun Glyph(@DrawableRes icon: Int, description: String?, modifier: Modifier = Modifier,
                  tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), description, modifier.size(22.dp), tint = tint)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PDFCrate(model: ReaderModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::open) }
    val document = model.current
    var chrome by rememberSaveable(document?.id) { mutableStateOf(true) }
    var goToPage by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    val pageView = remember(context, document?.id) { ZoomPageView(context) }
    BackHandler(enabled = document != null) { model.home() }
    DisposableEffect(pageView) { onDispose { pageView.show(null); pageView.onTap = null; pageView.onPageSwipe = null } }
    Scaffold(
        containerColor = if (document == null) MaterialTheme.colorScheme.background
            else if (isSystemInDarkTheme()) Color(0xFF242925) else Color(0xFFE2E6DE),
        topBar = {
            AnimatedVisibility(document == null || chrome, enter = fadeIn(), exit = fadeOut()) {
                TopAppBar(
                    title = {
                        if (document == null) Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(painterResource(R.drawable.ic_pdfcrate), null,
                                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
                            Text("PDFCrate", fontWeight = FontWeight.SemiBold, fontSize = 23.sp)
                        } else Column {
                            Text(document.name, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("PDF · ${model.count} páginas", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        if (document != null) IconButton(onClick = model::home, enabled = !model.busy) {
                            Glyph(R.drawable.ic_back, "Voltar")
                        }
                    },
                    actions = {
                        if (document == null) IconButton(onClick = { about = true }) { Glyph(R.drawable.ic_info, "Sobre o PDFCrate") }
                        else IconButton(onClick = { share(context, model, document) }, enabled = !model.busy) {
                            Glyph(R.drawable.ic_share, "Compartilhar PDF")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        floatingActionButton = {
            if (document == null) ExtendedFloatingActionButton(
                onClick = { if (!model.busy) picker.launch(arrayOf("application/pdf")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Glyph(R.drawable.ic_add, null) }, text = { Text("Abrir PDF") },
                shape = RoundedCornerShape(18.dp)
            )
        },
        bottomBar = {
            if (document != null) AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
                Surface(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(22.dp), tonalElevation = 2.dp, shadowElevation = 2.dp) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = { model.page(document.page - 1) }, enabled = !model.busy && document.page > 0) {
                                Glyph(R.drawable.ic_previous, "Anterior")
                            }
                            TextButton(onClick = { goToPage = true }, enabled = !model.busy,
                                contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text("${document.page + 1} / ${model.count}", fontSize = 13.sp)
                            }
                            IconButton(onClick = { model.page(document.page + 1) }, enabled = !model.busy && document.page + 1 < model.count) {
                                Glyph(R.drawable.ic_next, "Próxima")
                            }
                            IconButton(onClick = { pageView.zoomBy(1f / 1.5f) }) { Glyph(R.drawable.ic_zoom_out, "Diminuir zoom") }
                            IconButton(onClick = pageView::fit) { Glyph(R.drawable.ic_fit, "Ajustar página") }
                            IconButton(onClick = { pageView.zoomBy(1.5f) }) { Glyph(R.drawable.ic_zoom_in, "Aumentar zoom") }
                        }
                        LinearProgressIndicator(progress = { (document.page + 1).toFloat() / model.count.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (document == null) LibraryScreen(model)
            else AndroidView(factory = { pageView }, modifier = Modifier.fillMaxSize(), update = {
                it.show(model.bitmap)
                it.onTap = { chrome = !chrome }
                it.onPageSwipe = { direction -> model.page(document.page + direction) }
                it.contentDescription = "Página ${document.page + 1} de ${model.count}"
            })
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if (goToPage && document != null) {
        var target by remember { mutableStateOf("") }
        val number = target.toIntOrNull()
        AlertDialog(onDismissRequest = { goToPage = false }, title = { Text("Ir para página") },
            text = { OutlinedTextField(value = target, onValueChange = { target = it.filter(Char::isDigit).take(8) },
                label = { Text("Página de 1 a ${model.count}") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(enabled = number != null && number in 1..model.count,
                onClick = { model.page(number!! - 1); goToPage = false }) { Text("Ir") } },
            dismissButton = { TextButton(onClick = { goToPage = false }) { Text("Cancelar") } })
    }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("PDFCrate") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Versão 0.2.0 · Leitor offline")
            Text("Os PDFs abertos são copiados para esta biblioteca. O original permanece intacto. Remover uma cópia ou desinstalar o aplicativo apaga apenas os arquivos guardados aqui.")
            Text("Anotações, formulários e PDFs com senha ainda não estão disponíveis.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { about = false; model.sample() }, enabled = !model.busy) { Text("Experimentar exemplo") }
        } }, confirmButton = { TextButton(onClick = { about = false }) { Text("Fechar") } })
    model.error?.let { message -> AlertDialog(onDismissRequest = model::dismissError,
        title = { Text("Não foi possível concluir") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("Entendi") } }) }
}

@Composable
private fun LibraryScreen(model: ReaderModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<LibraryDocument?>(null) }
    val filtered = model.documents.filter { (!favorites || it.favorite) && it.name.contains(query, ignoreCase = true) }
    val recent = if (!favorites && query.isBlank()) filtered.firstOrNull() else null
    val remaining = if (recent == null) filtered else filtered.drop(1)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Biblioteca", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(if (model.documents.isEmpty()) "Seus documentos, neste aparelho" else
                "${model.documents.size} ${if (model.documents.size == 1) "documento salvo" else "documentos salvos"}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar documentos") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                leadingIcon = { Glyph(R.drawable.ic_search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Glyph(R.drawable.ic_close, "Limpar busca") } },
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !favorites, onClick = { favorites = false }, label = { Text("Recentes") })
                FilterChip(selected = favorites, onClick = { favorites = true }, label = { Text("Favoritos") },
                    leadingIcon = { Glyph(R.drawable.ic_heart, null, Modifier.size(16.dp)) })
            }
        }
        if (recent != null) item {
            RecentCard(recent, model, onRemove = { removing = recent })
        }
        if (remaining.isNotEmpty()) item {
            Text(if (recent != null) "Outros documentos" else "Documentos", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(remaining, key = { it.id }) { document ->
            Surface(onClick = { if (!model.busy) model.open(document) }, shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Thumbnail(document, model, Modifier.size(48.dp, 66.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(document.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(metadata(document), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DocumentMenu(document, model, { removing = document })
                }
            }
        }
        if (filtered.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 38.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Glyph(R.drawable.ic_document, null, Modifier.padding(22.dp).size(42.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(if (model.documents.isEmpty()) "Sua biblioteca está vazia" else "Nenhum documento encontrado",
                    style = MaterialTheme.typography.titleMedium)
                Text(if (model.documents.isEmpty()) "Abra um PDF para começar a ler." else "Tente outro nome ou filtro.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (model.documents.isEmpty()) TextButton(onClick = model::sample, enabled = !model.busy) { Text("Experimentar exemplo") }
            }
        }
    }
    removing?.let { document -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remover cópia local?") },
        text = { Text("${document.name} será removido desta biblioteca. O arquivo original não será apagado.") },
        confirmButton = { TextButton(onClick = { model.remove(document); removing = null }) { Text("Remover") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } }) }
}

@Composable
private fun RecentCard(document: LibraryDocument, model: ReaderModel, onRemove: () -> Unit) {
    Surface(onClick = { if (!model.busy) model.open(document) }, shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 18.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Thumbnail(document, model, Modifier.size(70.dp, 98.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("CONTINUAR LEITURA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Text(document.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Página ${document.page + 1}${if (document.pages > 0) " de ${document.pages}" else ""}",
                    style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { (document.page + 1).toFloat() / document.pages.coerceAtLeast(document.page + 1) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .10f))
            }
            DocumentMenu(document, model, onRemove)
        }
    }
}

@Composable
private fun DocumentMenu(document: LibraryDocument, model: ReaderModel, onRemove: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = !model.busy) { Glyph(R.drawable.ic_more, "Opções de ${document.name}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(if (document.favorite) "Desfavoritar" else "Favoritar") },
                leadingIcon = { Glyph(R.drawable.ic_heart, null) },
                onClick = { expanded = false; model.favorite(document) })
            DropdownMenuItem(text = { Text("Compartilhar") }, leadingIcon = { Glyph(R.drawable.ic_share, null) },
                onClick = { expanded = false; share(context, model, document) })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Remover cópia") }, leadingIcon = { Glyph(R.drawable.ic_trash, null) },
                onClick = { expanded = false; onRemove() })
        }
    }
}

@Composable
private fun Thumbnail(document: LibraryDocument, model: ReaderModel, modifier: Modifier) {
    val image by produceState<android.graphics.Bitmap?>(null, document.id) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(model.library.thumbnail(document.id).absolutePath) }
    }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        if (image != null) Image(image!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Glyph(R.drawable.ic_document, null, tint = Color(0xFF698070))
    }
}

@Composable
private fun metadata(document: LibraryDocument): String {
    val context = LocalContext.current
    return listOfNotNull("PDF", document.pages.takeIf { it > 0 }?.let { "$it páginas" },
        document.bytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }).joinToString(" · ")
}

private fun share(context: Context, model: ReaderModel, document: LibraryDocument) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", model.library.file(document.id), document.name)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(document.name, uri)
        }, "Compartilhar PDF"))
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "Nenhum aplicativo disponível para compartilhar.", android.widget.Toast.LENGTH_LONG).show()
    }
}
