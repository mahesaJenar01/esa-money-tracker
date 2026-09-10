package com.esa.moneytracker.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The archive is the one part of the lampiran feature that is pure JVM, and it
 * is also the part with the most to lose: it is what a backup restores from, so
 * a round trip that quietly drops a picture would only be noticed on a new
 * phone, long after the old one was wiped.
 */
class BackupArchiveTest {

    @Test
    fun `round trips the document and every picture`() {
        val json = """{"app":"esa-money-tracker","format_version":5}"""
        val first = byteArrayOf(1, 2, 3)
        val second = byteArrayOf(9, 8)

        val packed = pack(
            json,
            listOf("a.jpg" to first, "b.jpg" to second),
        )

        val unpacked = LinkedHashMap<String, ByteArray>()
        val readBack = BackupArchive.read(ByteArrayInputStream(packed)) { name, entry ->
            unpacked[name] = entry.readBytes()
        }

        assertEquals(json, readBack)
        assertEquals(listOf("a.jpg", "b.jpg"), unpacked.keys.toList())
        assertArrayEquals(first, unpacked.getValue("a.jpg"))
        assertArrayEquals(second, unpacked.getValue("b.jpg"))
    }

    /**
     * A picture that has gone missing is skipped, not fatal. Losing one receipt
     * is worth far less than losing the backup that carries every other one.
     */
    @Test
    fun `skips a picture that cannot be opened`() {
        val packed = ByteArrayOutputStream().also { out ->
            BackupArchive.write(
                out,
                "{}",
                listOf(
                    BackupArchive.Entry("gone.jpg") { null },
                    BackupArchive.Entry("here.jpg") { ByteArrayInputStream(byteArrayOf(7)) },
                ),
            )
        }.toByteArray()

        val unpacked = mutableListOf<String>()
        val readBack = BackupArchive.read(ByteArrayInputStream(packed)) { name, _ ->
            unpacked += name
        }

        assertEquals("{}", readBack)
        assertEquals(listOf("here.jpg"), unpacked)
    }

    /** An archive with no backup in it is not a backup, and says so. */
    @Test
    fun `answers null when the archive holds no document`() {
        val packed = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("stray.jpg"))
                zip.write(byteArrayOf(4))
                zip.closeEntry()
            }
        }.toByteArray()

        assertNull(BackupArchive.read(ByteArrayInputStream(packed)) { _, _ -> })
    }

    /**
     * The four bytes are how an import tells the two shapes apart without being
     * told which one it was handed.
     */
    @Test
    fun `recognises an archive by its first four bytes`() {
        val packed = pack("{}", emptyList())

        assertTrue(BackupArchive.looksLikeArchive(packed))
        assertFalse(BackupArchive.looksLikeArchive("""{"app":"x"}""".toByteArray()))
        assertFalse(BackupArchive.looksLikeArchive(byteArrayOf(0x50, 0x4B)))
    }

    private fun pack(json: String, files: List<Pair<String, ByteArray>>): ByteArray =
        ByteArrayOutputStream().also { out ->
            BackupArchive.write(
                out,
                json,
                files.map { (name, bytes) ->
                    BackupArchive.Entry(name) { ByteArrayInputStream(bytes) }
                },
            )
        }.toByteArray()

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.toList(), actual.toList())
    }
}
