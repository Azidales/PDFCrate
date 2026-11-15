package dev.pdfcrate.app.reader

import android.content.Context
import androidx.core.content.edit

/** Reading preferences kept on the device (excluded from backup with the other app data). */
class ReaderPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("reader_preferences", Context.MODE_PRIVATE)

    /** Continuous vertical scrolling (default) or one page at a time. */
    var continuous: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS, true)
        set(value) = prefs.edit { putBoolean(KEY_CONTINUOUS, value) }

    private companion object {
        const val KEY_CONTINUOUS = "continuous_scroll"
    }
}
