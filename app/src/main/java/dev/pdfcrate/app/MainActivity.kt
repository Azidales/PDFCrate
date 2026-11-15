package dev.pdfcrate.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pdfcrate.app.library.LibraryDocument
import dev.pdfcrate.app.library.LibraryScreen
import dev.pdfcrate.app.reader.AboutDialog
import dev.pdfcrate.app.reader.GlobalDialogs
import dev.pdfcrate.app.reader.ReaderModel
import dev.pdfcrate.app.reader.ReaderScreen
import dev.pdfcrate.app.ui.theme.PdfCrateTheme

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PdfCrateTheme {
                val model: ReaderModel = viewModel()
                LaunchedEffect(incoming) { incoming?.let { model.open(it); incoming = null } }
                PdfCrateApp(model)
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
private fun PdfCrateApp(model: ReaderModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.message) {
        val text = model.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        model.consumeMessage()
    }
    val openContract = remember {
        object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent =
                super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }
    val picker = rememberLauncherForActivityResult(openContract) { uri ->
        if (uri != null) model.openSelected(uri) else model.cancelLocate()
    }
    var exportingBackup by rememberSaveable { mutableStateOf(false) }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        if (uri != null) { if (exportingBackup) model.exportRecoveryBackup(uri) else model.saveCopy(uri) }
        exportingBackup = false
    }
    val defaultName = context.getString(R.string.default_document_name)
    val openFile = { picker.launch(arrayOf(PDF_MIME)) }
    val saveCopy = { exportingBackup = false; savePicker.launch(model.current?.name ?: defaultName) }
    var about by remember { mutableStateOf(false) }

    val document = model.current
    if (document == null) LibraryScreen(model, snackbar, onOpenFile = openFile,
        onExportBackup = { exportingBackup = true; savePicker.launch("Original-${model.recovery?.name ?: defaultName}") },
        onAbout = { about = true })
    else ReaderScreen(model, document, snackbar, onSaveCopy = saveCopy)

    if (about) AboutDialog(model, onClose = { about = false })
    GlobalDialogs(model, onLocate = { model.beginLocate(); openFile() }, onSaveCopy = saveCopy)
}

internal const val PDF_MIME = "application/pdf"

/** Shares the provider's document with a temporary read grant. Unsaved edits must be saved first. */
internal fun shareDocument(context: Context, model: ReaderModel, document: LibraryDocument) {
    if (model.current?.id == document.id && model.dirty) {
        model.notify(context.getString(R.string.share_save_first))
        return
    }
    try {
        val uri = model.library.uri(document)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(document.name, uri)
        }, context.getString(R.string.share_chooser)))
    } catch (_: Exception) {
        Toast.makeText(context, R.string.share_unavailable, Toast.LENGTH_LONG).show()
    }
}
