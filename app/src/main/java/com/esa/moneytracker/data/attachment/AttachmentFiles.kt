package com.esa.moneytracker.data.attachment

import android.content.Context
import com.esa.moneytracker.data.model.Attachment
import java.io.File

/**
 * Where a lampiran's pictures are, given nothing but a [Context].
 *
 * Split out of [AttachmentStore] so that drawing one costs no plumbing: a
 * history row is four composables deep and has no repository, and threading a
 * file-resolving lambda from the view model down to it would put a parameter on
 * every one of them for the sake of a path that is always the same.
 *
 * This is the only place that knows the layout on disk. Both the store and the
 * screens ask it rather than each spelling the folder out.
 */
object AttachmentFiles {

    private const val DIRECTORY = "attachments"
    private const val CAPTURE_DIRECTORY = "captures"

    /** The folder holding the full-size pictures. */
    fun directory(context: Context): File = File(context.filesDir, DIRECTORY)

    /** The folder holding the small copies the list rows draw. */
    fun thumbnailDirectory(context: Context): File =
        File(directory(context), Attachment.THUMBNAIL_DIR)

    fun full(context: Context, attachment: Attachment): File =
        File(directory(context), attachment.fileName)

    fun thumbnail(context: Context, attachment: Attachment): File =
        File(thumbnailDirectory(context), attachment.fileName)

    /**
     * A place for the camera to write its picture.
     *
     * In the cache rather than beside the kept pictures: a shot that is taken
     * and then cancelled is rubbish, and the system is welcome to clear it out.
     */
    fun newCaptureFile(context: Context): File {
        val captures = File(context.cacheDir, CAPTURE_DIRECTORY).also { it.mkdirs() }
        return File(captures, java.util.UUID.randomUUID().toString() + ".jpg")
    }
}
