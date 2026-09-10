package com.esa.moneytracker.data.export

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup that carries pictures: the same JSON as always, plus the lampiran
 * beside it in one ZIP.
 *
 *     esa-money-tracker-20260910-1830.zip
 *       data.json           <- exactly the file a .json backup would have been
 *       lampiran/<id>.jpg   <- one entry per picture
 *
 * The JSON is unchanged and still readable on its own, which is the point: a
 * backup made before this feature imports as it always did, and an archive
 * opened on a computer shows the receipts as ordinary image files rather than
 * as base64 buried in a text file.
 *
 * Only the full-size pictures travel. The small copies the history rows draw are
 * redrawn from them after an import — they are derived, so shipping them would
 * be paying twice for the same photo.
 */
object BackupArchive {

    /** The backup document itself, under the name it would have had alone. */
    const val JSON_ENTRY = "data.json"

    /** Everything under here is a picture, named by the row that claims it. */
    const val ATTACHMENT_DIR = "lampiran"

    /** The four bytes every ZIP opens with — what tells the two formats apart. */
    private val SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /**
     * A stream of pictures to write, in the order they should be stored.
     *
     * A supplier rather than the bytes themselves, so a backup with thirty
     * receipts never holds thirty photos in memory at once.
     */
    data class Entry(val name: String, val open: () -> InputStream?)

    fun looksLikeArchive(bytes: ByteArray): Boolean =
        bytes.size >= SIGNATURE.size && SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }

    /**
     * Writes the whole backup into [out], which is closed when this returns.
     *
     * A picture that has gone missing is skipped rather than failing the export:
     * losing one receipt is worth far less than losing the backup that would
     * have carried every other one.
     */
    fun write(out: OutputStream, json: String, files: List<Entry>) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.toByteArray())
            zip.closeEntry()

            files.forEach { entry ->
                val stream = runCatching { entry.open() }.getOrNull() ?: return@forEach
                stream.use { input ->
                    zip.putNextEntry(ZipEntry(ATTACHMENT_DIR + "/" + entry.name))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Reads an archive back, handing each picture to [onFile] as it arrives.
     *
     * Streamed rather than loaded: an archive is opened straight into the app's
     * attachment folder, so a backup holding a hundred megabytes of receipts
     * never has to fit in memory. The JSON is small enough to keep, and comes
     * back as the return value — null when the archive holds no backup at all.
     *
     * [onFile] is handed a stream it must not close; the ZIP owns it, and
     * closing it would end the whole archive rather than the one entry.
     */
    fun read(input: InputStream, onFile: (String, InputStream) -> Unit): String? {
        var json: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    entry.isDirectory -> Unit

                    name == JSON_ENTRY -> json = zip.readBytes().decodeToString()

                    name.startsWith(ATTACHMENT_DIR + "/") ->
                        onFile(name.substringAfterLast('/'), zip)
                }
                zip.closeEntry()
            }
        }
        return json
    }
}
