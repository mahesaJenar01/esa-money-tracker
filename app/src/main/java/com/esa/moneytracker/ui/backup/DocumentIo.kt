package com.esa.moneytracker.ui.backup

import android.content.Context
import android.net.Uri

/**
 * Reading and writing the file the user picked.
 *
 * The app never chooses a location itself: the system picker hands back a
 * document [Uri] the user chose, which is why no storage permission is needed
 * and why the file can land in Downloads, Drive, or anywhere else they like.
 */
object DocumentIo {

    /** Null when the document cannot be opened or is not text. */
    fun readText(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.getOrNull()

    /**
     * Writes [text] over whatever the document held.
     *
     * The "wt" mode truncates first — without it, writing a shorter file over a
     * longer one leaves the tail of the old content behind.
     */
    fun writeText(context: Context, uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(text.toByteArray())
            true
        } ?: false
    }.getOrDefault(false)
}
