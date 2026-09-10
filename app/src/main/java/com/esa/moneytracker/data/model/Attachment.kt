package com.esa.moneytracker.data.model

import java.time.Instant

/** Where a lampiran came from, which is the only thing the two kinds differ in. */
enum class AttachmentSource(val id: String) {
    /** A camera shot or a picture out of the gallery. */
    PHOTO("photo"),

    /** One page of a PDF, rendered to a picture when it was picked. */
    PDF("pdf"),
    ;

    companion object {
        fun fromId(id: String): AttachmentSource =
            entries.firstOrNull { it.id == id } ?: PHOTO
    }
}

/**
 * A picture kept alongside a note — a struk, an invoice, a transfer receipt.
 *
 * Always a picture, whatever was picked. A PDF is rendered page by page the
 * moment it is chosen and the pages are stored as ordinary images, so there is
 * one storage format, one viewer and one thumbnail in the whole app rather than
 * a second code path that only invoices ever take.
 *
 * The bytes do not live in the database. [fileName] names a file in the app's
 * own `attachments` folder, which keeps Room rows small and keeps a 400 KB photo
 * out of every query that only wanted a rupiah figure.
 */
data class Attachment(
    val id: String,
    val transactionId: String,
    val source: AttachmentSource,

    /** "<id>.jpg", inside the app's attachments folder. */
    val fileName: String,

    /**
     * What the file was called when it was picked, or empty for a camera shot.
     *
     * Worth keeping for a PDF above all: "invoice-agustus.pdf • hal. 2" says
     * far more about a rendered page than the picture of it can.
     */
    val label: String,

    /** 1-based page this came from, or null when it was never a PDF. */
    val page: Int?,

    val width: Int,
    val height: Int,
    val sizeBytes: Long,

    /** The order the user put them in; ties are broken by [createdAt]. */
    val position: Int,
    val createdAt: Instant,
) {
    /** "thumb/<id>.jpg" — the small copy the list rows draw. */
    val thumbnailName: String get() = THUMBNAIL_DIR + "/" + fileName

    /** What the viewer writes under the picture. */
    val displayLabel: String
        get() = when {
            label.isBlank() && page == null -> "Foto"
            label.isBlank() -> "Halaman " + page
            page == null -> label
            else -> label + " • hal. " + page
        }

    companion object {
        /** Sits inside the attachments folder, so one sweep cleans up both. */
        const val THUMBNAIL_DIR = "thumb"

        /** As many pictures as one note can carry; a struk never needs more. */
        const val MAX_PER_TRANSACTION = 10
    }
}
