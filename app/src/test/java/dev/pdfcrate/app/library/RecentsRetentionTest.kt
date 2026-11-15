package dev.pdfcrate.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsRetentionTest {
    private fun doc(id: String, uri: String = "content://provider/$id") = LibraryDocument(id = id, name = "$id.pdf", uri = uri)

    @Test fun latestComesFirstAndReplacesSameIdOrUri() {
        val existing = listOf(doc("a"), doc("b"), doc("c", uri = "content://provider/shared"))
        val latest = doc("b").copy(page = 7)
        val (kept, dropped) = retainRecents(latest, existing)
        assertEquals(listOf("b", "a", "c"), kept.map { it.id })
        assertEquals(7, kept.first().page)
        assertTrue(dropped.isEmpty())

        val relocated = doc("d", uri = "content://provider/shared")
        val (afterMove, released) = retainRecents(relocated, existing)
        assertEquals(listOf("d", "a", "b"), afterMove.map { it.id })
        // The URI is still referenced by the new entry, so its permission must not be released.
        assertTrue(released.isEmpty())
    }

    @Test fun overflowIsCappedAndReportedForPermissionRelease() {
        val existing = (1..MAX_RECENTS).map { doc("old$it") }
        val (kept, dropped) = retainRecents(doc("new"), existing)
        assertEquals(MAX_RECENTS, kept.size)
        assertEquals("new", kept.first().id)
        assertEquals(listOf("old$MAX_RECENTS"), dropped.map { it.id })
    }
}
