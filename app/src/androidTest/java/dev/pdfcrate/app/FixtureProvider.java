package dev.pdfcrate.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Owns fixture PDFs in the test APK, outside the reader's private storage. */
public class FixtureProvider extends ContentProvider {
    private File source() { return new File(getContext().getFilesDir(), "fixture.pdf"); }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/pdf"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        if (!"r".equals(mode)) throw new java.io.FileNotFoundException("Read-only fixture");
        return ParcelFileDescriptor.open(source(), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        String[] columns = projection != null ? projection : new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add("Arquivo externo.pdf");
            else if (OpenableColumns.SIZE.equals(column)) row.add(source().length());
            else row.add(null);
        }
        return cursor;
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        try {
            if ("seed".equals(method)) {
                File staging = new File(getContext().getFilesDir(), "fixture.new");
                try (FileOutputStream output = new FileOutputStream(staging)) {
                    output.write(extras.getByteArray("pdf"));
                    output.getFD().sync();
                }
                if (!staging.renameTo(source())) throw new IOException("Atomic fixture replacement failed");
            } else if ("delete".equals(method)) {
                source().delete();
            } else if ("bytes".equals(method)) {
                result.putByteArray("pdf", java.nio.file.Files.readAllBytes(source().toPath()));
            } else throw new IllegalArgumentException("Unknown fixture operation");
        } catch (IOException failure) { throw new IllegalStateException(failure); }
        return result;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
