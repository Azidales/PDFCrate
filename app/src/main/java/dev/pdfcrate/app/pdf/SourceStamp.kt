package dev.pdfcrate.app.pdf

import android.os.ParcelFileDescriptor
import android.system.Os

/**
 * File metadata captured when a document is opened, used to refuse replacing an original
 * that changed outside the app. Providers may report zero for fields they do not know;
 * unknown fields never count as a change, so detection is best effort by design.
 */
data class SourceStamp(val size: Long, val modified: Long, val inode: Long) {
    fun differsFrom(now: SourceStamp): Boolean {
        if (size <= 0 || now.size <= 0) return false
        return size != now.size ||
            (modified > 0 && now.modified > 0 && modified != now.modified) ||
            (inode > 0 && now.inode > 0 && inode != now.inode)
    }

    companion object {
        fun of(descriptor: ParcelFileDescriptor): SourceStamp? = runCatching {
            Os.fstat(descriptor.fileDescriptor).let { SourceStamp(it.st_size, it.st_mtime, it.st_ino) }
        }.getOrNull()
    }
}
