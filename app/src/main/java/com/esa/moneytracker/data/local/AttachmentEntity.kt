package com.esa.moneytracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.esa.moneytracker.data.model.Attachment
import com.esa.moneytracker.data.model.AttachmentSource
import java.time.Instant

/**
 * The on-disk shape of one lampiran.
 *
 * Flat primitives and no foreign key, like every other table here — the row is
 * a *description* of a file, and the file it describes lives in the app's
 * `attachments` folder rather than in this column. A row with no file behind it
 * is not a crash, it is a picture that failed to come back; the UI draws a
 * placeholder and the housekeeping sweep tidies the row away.
 */
@Entity(
    tableName = "attachments",
    indices = [
        Index("transaction_id"),
        Index("position"),
    ],
)
data class AttachmentEntity(
    /** Client-generated UUID, and also the file's name on disk. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "transaction_id")
    val transactionId: String,

    /** [AttachmentSource.id] */
    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    /** The name of the file it was made from; empty for a camera shot. */
    @ColumnInfo(name = "label")
    val label: String = "",

    /** 1-based page of the PDF it was rendered from, or null. */
    @ColumnInfo(name = "page")
    val page: Int? = null,

    @ColumnInfo(name = "width")
    val width: Int,

    @ColumnInfo(name = "height")
    val height: Int,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    @ColumnInfo(name = "position")
    val position: Int,

    /** Epoch millis, UTC — when the picture was attached. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

fun AttachmentEntity.toDomain(): Attachment = Attachment(
    id = id,
    transactionId = transactionId,
    source = AttachmentSource.fromId(source),
    fileName = fileName,
    label = label,
    page = page,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    position = position,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    transactionId = transactionId,
    source = source.id,
    fileName = fileName,
    label = label,
    page = page,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    position = position,
    createdAt = createdAt.toEpochMilli(),
)
