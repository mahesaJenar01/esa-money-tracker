package com.esa.moneytracker.data.export

import com.esa.moneytracker.data.attachment.AttachmentStore
import com.esa.moneytracker.data.local.AttachmentEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The wire shape of one lampiran.
 *
 * Only the description travels in the JSON; the picture itself is a separate
 * entry in the archive, named by [file]. That is the whole reason a backup with
 * lampiran is a `.zip` rather than a `.json` — a photo written into JSON has to
 * be base64, which inflates it by a third and turns a fifty-kilobyte backup into
 * a ten-megabyte one that is slow to write and slower to parse.
 *
 * [transaction] is the note this belongs to. A record whose note is not in the
 * same file is dropped on import rather than restored as a picture belonging to
 * nothing.
 */
@Serializable
data class AttachmentExportRecord(
    @SerialName("id") val id: String,
    @SerialName("transaction") val transaction: String,
    /** "photo" or "pdf" — see [com.esa.moneytracker.data.model.AttachmentSource]. */
    @SerialName("source") val source: String = "photo",
    /** The entry's name inside the archive's lampiran folder. */
    @SerialName("file") val file: String,
    /** What the file was called when it was picked, for a human reading this. */
    @SerialName("label") val label: String = "",
    /** 1-based page of the PDF it was rendered from, or 0 for a photo. */
    @SerialName("page") val page: Int = 0,
    @SerialName("width") val width: Int = 0,
    @SerialName("height") val height: Int = 0,
    @SerialName("size_bytes") val sizeBytes: Long = 0L,
    @SerialName("position") val position: Int = 0,
    @SerialName("created_iso") val createdIso: String = "",
) {
    /**
     * The row as a database record, or null when it is not usable.
     *
     * The file name is checked here as well as when the bytes are written: a
     * row naming a path instead of a file would survive the unpacking and point
     * the app at something outside its own folder for good.
     */
    fun toEntity(): AttachmentEntity? {
        if (id.isBlank() || transaction.isBlank()) return null
        if (!AttachmentStore.isSafeName(file)) return null
        return AttachmentEntity(
            id = id,
            transactionId = transaction,
            source = source,
            fileName = file,
            label = label,
            page = page.takeIf { it > 0 },
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            position = position,
            createdAt = (parse(createdIso) ?: Instant.now()).toEpochMilli(),
        )
    }

    companion object {
        fun from(entity: AttachmentEntity, zone: ZoneId): AttachmentExportRecord =
            AttachmentExportRecord(
                id = entity.id,
                transaction = entity.transactionId,
                source = entity.source,
                file = entity.fileName,
                label = entity.label,
                page = entity.page ?: 0,
                width = entity.width,
                height = entity.height,
                sizeBytes = entity.sizeBytes,
                position = entity.position,
                createdIso = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .format(Instant.ofEpochMilli(entity.createdAt).atZone(zone)),
            )

        private fun parse(value: String): Instant? =
            runCatching { Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value)) }
                .getOrNull()
    }
}
