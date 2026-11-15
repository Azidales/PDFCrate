package dev.pdfcrate.app.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the rule that refuses to overwrite an original changed outside the app. */
class SourceStampTest {
    private val opened = SourceStamp(size = 1_000, modified = 1_700_000_000, inode = 42)

    @Test fun identicalMetadataIsNotAChange() = assertFalse(opened.differsFrom(opened.copy()))

    @Test fun sizeModificationTimeOrInodeChangeIsDetected() {
        assertTrue(opened.differsFrom(opened.copy(size = 1_001)))
        assertTrue(opened.differsFrom(opened.copy(modified = 1_700_000_001)))
        assertTrue(opened.differsFrom(opened.copy(inode = 43)))
    }

    @Test fun unknownFieldsNeverCountAsChange() {
        assertFalse(opened.differsFrom(opened.copy(modified = 0, inode = 0)))
        assertFalse(opened.copy(modified = 0, inode = 0).differsFrom(opened.copy(modified = 5, inode = 7)))
    }

    @Test fun unknownSizeDisablesDetection() {
        assertFalse(opened.copy(size = 0).differsFrom(opened.copy(modified = 1)))
        assertFalse(opened.differsFrom(SourceStamp(0, 99, 99)))
    }
}
