package com.esa.moneytracker.data.attachment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.esa.moneytracker.data.model.Attachment
import com.esa.moneytracker.data.model.AttachmentSource
import com.esa.moneytracker.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A picture that is already on disk but does not belong to a note yet.
 *
 * The Catat flow needs this shape because the note it is about to be attached
 * to has no id until the form is submitted. Abandoning the form leaves the file
 * behind, which is what [AttachmentStore.sweep] is for.
 */
data class AttachmentDraft(
    val id: String,
    val source: AttachmentSource,
    val fileName: String,
    val label: String,
    val page: Int?,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

/**
 * The draft seen as an [Attachment], so one thumbnail draws both.
 *
 * It names no note and has no position, because it has neither yet — everything
 * that puts a picture on screen only ever asks for the file name and the label.
 */
fun AttachmentDraft.asPreview(): Attachment = Attachment(
    id = id,
    transactionId = "",
    source = source,
    fileName = fileName,
    label = label,
    page = page,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    position = 0,
    createdAt = java.time.Instant.EPOCH,
)

/** What came of picking a file: pictures, or a reason there are none. */
sealed interface AttachmentImport {
    /**
     * [pagesLeftOut] is non-zero for a PDF longer than the app will render, so
     * the screen can say so rather than quietly dropping the rest.
     */
    data class Added(
        val drafts: List<AttachmentDraft>,
        val pagesLeftOut: Int = 0,
    ) : AttachmentImport

    data class Failed(val message: String) : AttachmentImport
}

/**
 * The files behind the lampiran, and everything that turns a picked document
 * into one.
 *
 * Two folders, both private to the app: `attachments/` holds the picture that is
 * kept, and `attachments/thumb/` the small copy the history rows draw. Nothing
 * here needs a storage permission — the app only ever writes inside its own data
 * directory, and reads the one file the user chose in the system picker.
 *
 * A PDF is not stored as a PDF. Every page is rendered to a picture the moment
 * it is picked, through the [PdfRenderer] that has been part of Android since
 * long before this app's minimum version. No library, no extra megabytes in the
 * APK — and nothing downstream, not the thumbnail, the viewer or the backup,
 * ever has to know that a particular picture was once an invoice.
 */
class AttachmentStore(private val context: Context) {

    /** Where the kept pictures live. Made on demand, so a fresh install is clean. */
    private val directory: File
        get() = AttachmentFiles.directory(context).also { it.mkdirs() }

    private val thumbnailDirectory: File
        get() = AttachmentFiles.thumbnailDirectory(context).also { it.mkdirs() }

    fun file(fileName: String): File = File(directory, fileName)

    fun file(attachment: Attachment): File = file(attachment.fileName)

    fun thumbnail(attachment: Attachment): File = File(thumbnailDirectory, attachment.fileName)

    /**
     * Takes whatever the user picked and turns it into pictures on disk.
     *
     * [allowance] is how many more this note may hold. A selection longer than
     * that is cut rather than refused, because somebody who picked six pages of
     * an invoice would rather have the first few than nothing at all.
     */
    suspend fun stage(uri: Uri, allowance: Int): AttachmentImport = withContext(Dispatchers.IO) {
        if (allowance <= 0) {
            return@withContext AttachmentImport.Failed(
                "Satu catatan hanya bisa membawa " + Attachment.MAX_PER_TRANSACTION + " lampiran.",
            )
        }

        val label = displayNameOf(uri)
        val temporary = runCatching { copyToCache(uri) }.getOrNull()
            ?: return@withContext AttachmentImport.Failed(
                "Berkas tidak bisa dibaca. Coba pilih ulang.",
            )

        try {
            when {
                temporary.length() > MAX_SOURCE_BYTES ->
                    AttachmentImport.Failed("Berkasnya terlalu besar.")

                looksLikePdf(temporary) -> renderPdf(temporary, label, allowance)

                else -> importImage(temporary, label)
            }
        } finally {
            temporary.delete()
        }
    }

    /**
     * Takes a picture the camera has just written into the cache.
     *
     * Separate from [stage] only because the file is already ours: there is no
     * provider to ask for a name, and the original is deleted afterwards rather
     * than left in the cache to be swept up eventually.
     */
    suspend fun stageCapture(capture: File): AttachmentImport = withContext(Dispatchers.IO) {
        try {
            importImage(capture, label = "")
        } finally {
            capture.delete()
        }
    }

    /** Draws the small copy, if it is not already there. Cheap to call often. */
    suspend fun ensureThumbnail(attachment: Attachment) {
        withContext(Dispatchers.IO) {
            val thumbnail = thumbnail(attachment)
            if (!thumbnail.exists() || thumbnail.length() == 0L) {
                Images.writeThumbnail(file(attachment), thumbnail)
            }
        }
    }

    /** Both copies of one picture, gone for good. */
    fun remove(fileName: String) {
        File(directory, fileName).delete()
        File(thumbnailDirectory, fileName).delete()
    }

    /**
     * Deletes every file that no row claims.
     *
     * This is what lets the Catat flow write a picture before the note exists:
     * picking a photo and then backing out of the form leaves a file with
     * nothing pointing at it, and the next launch takes it away. It catches what
     * a crash mid-save would strand, too.
     */
    suspend fun sweep(keep: Set<String>): Int = withContext(Dispatchers.IO) {
        var removed = 0
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) return@forEach
            if (file.name !in keep && file.delete()) removed++
        }
        // A thumbnail is worthless without the picture it was drawn from, so the
        // same list decides both.
        thumbnailDirectory.listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
        removed
    }

    /** The bytes of one picture, for writing into a backup archive. */
    fun openForBackup(fileName: String): InputStream? =
        File(directory, fileName).takeIf { it.exists() }?.inputStream()

    /**
     * Writes one picture that came out of a backup archive.
     *
     * The name is checked rather than trusted: an archive is a file from outside
     * the app, and an entry calling itself `../databases/money_tracker.db` would
     * otherwise be written exactly where it asked to be.
     */
    fun writeFromBackup(fileName: String, input: InputStream): Boolean {
        if (!isSafeName(fileName)) return false
        return runCatching {
            File(directory, fileName).outputStream().use { input.copyTo(it) }
            true
        }.getOrDefault(false)
    }

    // ----------------------------------------------------------------- images

    private fun importImage(source: File, label: String): AttachmentImport {
        val bitmap = Images.decode(source, Images.FULL_MAX_EDGE)
            ?: return AttachmentImport.Failed("Berkas ini bukan foto atau PDF yang bisa dibaca.")

        return try {
            AttachmentImport.Added(
                listOf(keep(bitmap, AttachmentSource.PHOTO, label, page = null)),
            )
        } catch (error: Exception) {
            AttachmentImport.Failed("Foto gagal disimpan. Ruang penyimpanan mungkin penuh.")
        } finally {
            bitmap.recycle()
        }
    }

    // -------------------------------------------------------------------- pdf

    /**
     * Renders a PDF page by page.
     *
     * One page at a time, and each bitmap is released before the next is made:
     * an A4 page at this resolution is about sixteen megabytes in memory, so
     * holding a whole invoice at once is how a cheaper phone runs out of heap.
     */
    private fun renderPdf(source: File, label: String, allowance: Int): AttachmentImport {
        return try {
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { handle ->
                PdfRenderer(handle).use { renderer ->
                    if (renderer.pageCount == 0) {
                        return AttachmentImport.Failed("PDF ini tidak punya halaman.")
                    }

                    val wanted = minOf(renderer.pageCount, MAX_PDF_PAGES, allowance)
                    val drafts = ArrayList<AttachmentDraft>(wanted)
                    for (index in 0 until wanted) {
                        renderer.openPage(index).use { page ->
                            val bitmap = rasterise(page)
                            try {
                                drafts += keep(
                                    bitmap = bitmap,
                                    source = AttachmentSource.PDF,
                                    label = label,
                                    page = index + 1,
                                )
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    AttachmentImport.Added(drafts, renderer.pageCount - wanted)
                }
            }
        } catch (error: SecurityException) {
            // PdfRenderer refuses anything it would have to decrypt, and there is
            // no password to offer it — so say that, rather than "gagal dibaca".
            AttachmentImport.Failed("PDF ini terkunci kata sandi, jadi tidak bisa dibuka.")
        } catch (error: Exception) {
            AttachmentImport.Failed("PDF ini tidak bisa dibaca.")
        }
    }

    /**
     * One PDF page as a picture.
     *
     * Painted white first: a PDF page is transparent wherever nothing is drawn,
     * and saving that as a JPEG — which has no transparency — would turn every
     * blank part of the invoice solid black.
     */
    private fun rasterise(page: PdfRenderer.Page): Bitmap {
        val scale = Images.FULL_MAX_EDGE.toFloat() / max(page.width, page.height).toFloat()
        val width = max(1, (page.width * scale).roundToInt())
        val height = max(1, (page.height * scale).roundToInt())

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    // ----------------------------------------------------------------- shared

    /** Writes one finished picture and its small copy, and describes them. */
    private fun keep(
        bitmap: Bitmap,
        source: AttachmentSource,
        label: String,
        page: Int?,
    ): AttachmentDraft {
        val id = UUID.randomUUID().toString()
        val fileName = id + ".jpg"
        val target = File(directory, fileName)
        val size = Images.writeJpeg(bitmap, target)
        Images.writeThumbnail(target, File(thumbnailDirectory, fileName))

        return AttachmentDraft(
            id = id,
            source = source,
            fileName = fileName,
            label = label,
            page = page,
            width = bitmap.width,
            height = bitmap.height,
            sizeBytes = size,
        )
    }

    /**
     * Copies the picked document into the cache before anything reads it.
     *
     * A `content://` stream can only be read once and cannot be rewound, while
     * both the image decoder and [PdfRenderer] need to move around inside the
     * file — the decoder reads the header before the pixels, and a PDF keeps its
     * table of contents at the very end.
     */
    private fun copyToCache(uri: Uri): File {
        val target = File.createTempFile("lampiran", null, context.cacheDir)
        val stream = context.contentResolver.openInputStream(uri)
        if (stream == null) {
            target.delete()
            error("tidak ada isi di " + uri)
        }
        stream.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    /** The file's own name, when the provider is willing to say. */
    private fun displayNameOf(uri: Uri): String = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }
            .orEmpty()
    }.getOrDefault("")

    /** The five bytes every PDF opens with, which beat a MIME type that lies. */
    private fun looksLikePdf(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(5)
            input.read(header) == 5 && header.decodeToString() == "%PDF-"
        }
    }.getOrDefault(false)

    companion object {
        /**
         * How many pages of one PDF are rendered.
         *
         * A struk is one page and an invoice a handful. A hundred-page statement
         * is not what this is for, and rendering one would take minutes and fill
         * the phone.
         */
        const val MAX_PDF_PAGES = 12

        /** Past this it is not a receipt, and copying it would hurt. */
        private const val MAX_SOURCE_BYTES = 64L * 1024L * 1024L

        /** A backup entry may name a file, never a path. */
        fun isSafeName(name: String): Boolean =
            name.isNotBlank() &&
                !name.contains('/') &&
                !name.contains('\\') &&
                name != "." &&
                name != ".."
    }
}
