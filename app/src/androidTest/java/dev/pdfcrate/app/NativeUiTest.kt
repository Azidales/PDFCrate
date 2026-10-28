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
            compose.onNodeWithText("Senha do PDF").performTextInput("reader-secret")
            compose.onNode(hasText("Abrir") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("1 / 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Ferramentas do PDF").performClick()
            compose.onNodeWithText("Texto desta página").performClick()
            compose.waitUntil(20_000) { compose.onAllNodes(hasText("PDFCrate native text", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Copiar texto").assertExists()
            compose.onNode(hasText("Fechar") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithContentDescription("Ferramentas do PDF").performClick()
            compose.onNodeWithText("Buscar texto").performClick()
            compose.onNodeWithText("Buscar no PDF").performTextInput("native")
            compose.onNodeWithText("Buscar", substring = false).performClick()
            compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("search-count") and hasText("1 / 1")).fetchSemanticsNodes().isNotEmpty() }
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
}
