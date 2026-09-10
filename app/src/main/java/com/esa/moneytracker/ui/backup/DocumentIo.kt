package com.esa.moneytracker.ui.backup

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/**
 * Reading and writing the file the user picked.
 *
 * The app never chooses a location itself: the system picker hands back a
 * document [Uri] the user chose, which is why no storage permission is needed
 * and why the file can land in Downloads, Drive, or anywhere else they like.
 *
 * Streams rather than strings wherever a backup may carry pictures. A `.zip`
 * with thirty receipts in it is tens of megabytes, and reading one into a
 * `String` first would mean holding the whole thing in memory twice over.
 */
object DocumentIo {

    /** Null when the document cannot be opened. Close it when you are done. */
    fun openInput(context: Context, uri: Uri): InputStream? = runCatching {
        context.contentResolver.openInputStream(uri)
    }.getOrNull()

    /**
     * A stream over the chosen document, truncated first.
     *
     * "wt" is what does the truncating — without it, writing a shorter file over
     * a longer one leaves the tail of the old content behind.
     */
    fun openOutput(context: Context, uri: Uri): OutputStream? = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")
    }.getOrNull()

    /** Writes [text] over whatever the document held. */
    fun writeText(context: Context, uri: Uri, text: String): Boolean = runCatching {
        openOutput(context, uri)?.use {
            it.write(text.toByteArray())
            true
        } ?: false
    }.getOrDefault(false)
}
