package dev.pdfcrate.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incoming = documentUri(intent)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme(
                primary = Color(0xFFA6D5C5), secondary = Color(0xFFC3C9BF)
            ) else lightColorScheme(
                primary = Color(0xFF265E54), secondary = Color(0xFF52645A),
                background = Color(0xFFF7F8F4), surface = Color(0xFFF7F8F4)
            )
            MaterialTheme(colorScheme = colors) {
                val model: ReaderModel = viewModel()
                LaunchedEffect(incoming) {
                    incoming?.let { model.open(it); incoming = null }
                }
                PDFCrate(model)
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PDFCrate(model: ReaderModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::open)
    }
    val document = model.current
    BackHandler(enabled = document != null) { model.home() }
    var goToPage by remember { mutableStateOf(false) }
    val pageView = remember(context) { ZoomPageView(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(document?.name ?: "PDFCrate", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold)
                        if (document == null) Text("Seu espaço de leitura", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = {
                    if (document != null) TextButton(onClick = model::home, enabled = !model.busy) { Text("Voltar") }
                },
                actions = {
                    if (document != null) TextButton(onClick = {
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files",
                                model.library.file(document.id), document.name)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = android.content.ClipData.newRawUri(document.name, uri)
                            }
                            context.startActivity(Intent.createChooser(share, "Compartilhar PDF"))
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(context, "Nenhum aplicativo disponível para compartilhar.",
                                android.widget.Toast.LENGTH_LONG).show()
                        }
                    }, enabled = !model.busy) { Text("Enviar") }
                }
            )
        },
        bottomBar = {
            if (document != null) Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { pageView.zoomBy(1f / 1.5f) }) { Text("− Zoom") }
                        TextButton(onClick = pageView::fit) { Text("Ajustar") }
                        TextButton(onClick = { pageView.zoomBy(1.5f) }) { Text("+ Zoom") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { model.page(document.page - 1) },
                            enabled = !model.busy && document.page > 0) { Text("Anterior") }
                        TextButton(onClick = { goToPage = true }, enabled = !model.busy) {
                            Text("${document.page + 1} / ${model.count}")
                        }
                        TextButton(onClick = { model.page(document.page + 1) },
                            enabled = !model.busy && document.page + 1 < model.count) { Text("Próxima") }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (document == null) LibraryScreen(model,
                onOpen = { picker.launch(arrayOf("application/pdf")) })
            else AndroidView(
                factory = { pageView },
                modifier = Modifier.fillMaxSize(),
                update = {
                    it.show(model.bitmap)
                    it.contentDescription = "Página ${document.page + 1} de ${model.count}. Use os botões de zoom para ampliar."
                }
            )
            if (model.busy) Surface(Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.medium,
                shadowElevation = 4.dp) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Abrindo página…")
                }
            }
        }
    }

    if (goToPage && document != null) {
        var target by remember { mutableStateOf("") }
        val number = target.toIntOrNull()
        AlertDialog(onDismissRequest = { goToPage = false },
            title = { Text("Ir para página") },
            text = {
                OutlinedTextField(value = target, onValueChange = { target = it.filter(Char::isDigit).take(8) },
                    label = { Text("Página de 1 a ${model.count}") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            },
            confirmButton = {
                TextButton(enabled = number != null && number in 1..model.count,
                    onClick = { model.page(number!! - 1); goToPage = false }) { Text("Ir") }
            },
            dismissButton = { TextButton(onClick = { goToPage = false }) { Text("Cancelar") } })
    }
    model.error?.let { message ->
        AlertDialog(onDismissRequest = model::dismissError,
            title = { Text("Não foi possível concluir") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = model::dismissError) { Text("Entendi") } })
    }
}

@Composable
private fun LibraryScreen(model: ReaderModel, onOpen: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<LibraryDocument?>(null) }
    val filtered = model.documents.filter {
        (!favorites || it.favorite) && it.name.contains(search, ignoreCase = true)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Abra. Leia. Continue.", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Seus PDFs ficam neste aparelho, sem anúncios e sem conta.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onOpen, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir PDF", Modifier.padding(6.dp))
            }
            TextButton(onClick = model::sample, enabled = !model.busy) { Text("Experimentar exemplo") }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = search, onValueChange = { search = it },
                label = { Text("Buscar na biblioteca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !favorites, onClick = { favorites = false }, label = { Text("Recentes") })
                FilterChip(selected = favorites, onClick = { favorites = true }, label = { Text("Favoritos") })
            }
        }
        if (filtered.isEmpty()) item {
            Text(if (model.documents.isEmpty()) "Sua biblioteca começa com o primeiro PDF."
                else "Nenhum documento encontrado.", Modifier.padding(vertical = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(filtered, key = { it.id }) { document ->
            ElevatedCard(onClick = { if (!model.busy) model.open(document) },
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(document.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Continuar na página ${document.page + 1}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { model.favorite(document) }, enabled = !model.busy) {
                            Text(if (document.favorite) "Desfavoritar" else "Favoritar")
                        }
                        TextButton(onClick = { removing = document }, enabled = !model.busy) { Text("Remover cópia") }
                    }
                }
            }
        }
        item {
            Text("Os arquivos abertos são copiados para a biblioteca local. O original permanece intacto. " +
                "Esta versão inicial oferece leitura; anotações e formulários ainda estão em desenvolvimento.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    removing?.let { document ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remover cópia local?") },
            text = { Text("${document.name} será removido desta biblioteca. O arquivo original não será apagado.") },
            confirmButton = { TextButton(onClick = { model.remove(document); removing = null }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } })
    }
}
