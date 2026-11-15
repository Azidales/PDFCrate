package dev.pdfcrate.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import dev.pdfcrate.app.library.DocumentLibrary
import dev.pdfcrate.app.pdf.NativePdfSupport

@RunWith(AndroidJUnit4::class)
class NativeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun protectedDocumentPromptsRetriesAndExposesNativeText() {
        assumeTrue(NativePdfSupport.advanced)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Uri.parse("content://dev.pdfcrate.app.test.source/fixture.pdf")
        val fixture = nativeFixture(context, encrypted = true)
        context.contentResolver.call(source, "seed", null, Bundle().apply { putByteArray("pdf", fixture.readBytes()) })
        fixture.delete()
        val activity = compose.activity
        val initialIntent = activity.intent
        try {
            activity.runOnUiThread { activity.startActivity(Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java)
                .setDataAndType(source, "application/pdf").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Senha do PDF").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Senha do PDF").performTextInput("wrong")
            compose.onNode(hasText("Abrir") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Senha incorreta ou proteção não compatível.").fetchSemanticsNodes().isNotEmpty() }
            compose.saveScreenshot("password.png")
            compose.onNodeWithText("Senha do PDF").performTextInput("reader-secret")
            compose.onNode(hasText("Abrir") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("1 / 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Mais opções").performClick()
            compose.onNodeWithText("Texto desta página").performClick()
            compose.waitUntil(20_000) { compose.onAllNodes(hasText("PDFCrate native text", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Copiar texto").assertExists()
            compose.saveScreenshot("page-text.png")
            compose.onNodeWithContentDescription("Fechar painel").performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Copiar texto").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithContentDescription("Editar e anotar").performClick()
            compose.onNodeWithText("Formulário").performScrollTo().performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Formulários desta página").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Preencher").assertExists()
            compose.onNodeWithContentDescription("Fechar painel").assertExists()
            compose.saveScreenshot("forms.png")
            compose.onNodeWithContentDescription("Fechar painel").performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Formulários desta página").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithContentDescription("Buscar texto").performClick()
            compose.onNodeWithTag("search-field").performTextInput("native")
            compose.onNodeWithTag("search-field").performImeAction()
            compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("search-count") and hasText("1 / 1")).fetchSemanticsNodes().isNotEmpty() }
            compose.saveScreenshot("search.png")
            assertFalse(context.getSharedPreferences("recents_v1", 0).getString("documents", "")!!.contains("reader-secret"))
            compose.onNodeWithContentDescription("Fechar busca").performClick()
            compose.onNodeWithContentDescription("Voltar").performClick()
            compose.onNodeWithContentDescription("Opções de Arquivo externo.pdf").performClick()
            compose.onNodeWithText("Remover dos recentes").performClick()
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.intent = initialIntent }
            DocumentLibrary(context).documents().filter { it.uri == source.toString() }.forEach { DocumentLibrary(context).remove(it) }
            context.contentResolver.call(source, "delete", null, null)
        }
    }

    @Test fun editModeOffersDeviceToolsAndBackLeavesStatesInOrder() {
        assumeTrue(NativePdfSupport.advanced)
        compose.onNodeWithContentDescription("Sobre o PDFCrate").performClick()
        compose.onNode(hasText("Experimentar exemplo") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("1 / 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Editar e anotar").performClick()
        compose.onNodeWithText("Selecionar").assertExists()
        compose.onNodeWithText("Formulário").assertExists()
        compose.onNodeWithText("Concluir").assertExists()
        compose.saveScreenshot("edit-palette.png")
        if (NativePdfSupport.annotations) {
            compose.onNodeWithText("Nota").assertExists()
            compose.onNodeWithText("Desenhar").assertExists()
            compose.onNodeWithText("Desfazer").assertIsNotEnabled()
        } else compose.onNodeWithText("Desenhar").assertDoesNotExist()
        compose.onNodeWithText("Selecionar").performClick()
        compose.onNodeWithText("Arraste sobre o texto para selecionar.").assertExists()
        compose.saveScreenshot("tool-hint.png")
        back()
        compose.onNodeWithText("Arraste sobre o texto para selecionar.").assertDoesNotExist()
        compose.onNodeWithText("Concluir").assertExists()
        back()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Concluir").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("1 / 3").assertExists()
        back()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Recentes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Opções de Exemplo PDFCrate.pdf").performClick()
        compose.onNodeWithText("Remover dos recentes").performClick()
    }

    private fun back() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
