package com.esa.moneytracker.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turning whatever the user picked into a picture the app can keep.
 *
 * Everything a lampiran is made of goes through here, and it all ends up as one
 * thing: a JPEG no longer than [FULL_MAX_EDGE] on its long side. A modern phone
 * camera hands over a 12-megapixel file of four megabytes or more, which is
 * absurd for a photo of a receipt — at 2000 pixels the writing on a struk is
 * still perfectly readable when zoomed, and the file lands somewhere around
 * 300 KB, small enough that a backup with thirty of them is still a file worth
 * sending over WhatsApp.
 *
 * Decoding is sampled, never full-size: a bitmap is four bytes a pixel once it
 * is in memory, so decoding that same camera file whole would cost 48 MB and
 * take the app past its heap limit on a cheaper phone.
 */
object Images {

    /** Long edge of the picture that is kept and shown full screen. */
    const val FULL_MAX_EDGE = 2000

    /** Long edge of the small copy the history rows draw. */
    const val THUMBNAIL_MAX_EDGE = 320

    private const val FULL_QUALITY = 85
    private const val THUMBNAIL_QUALITY = 78

    /**
     * Reads [file] back at no more than [maxEdge] pixels on its long side,
     * turned the right way up.
     *
     * Null when the file is missing or is not an image the phone can decode —
     * an ordinary answer here, since these files can be removed by a restore, a
     * cleaner app, or a backup that arrived without them.
     */
    fun decode(file: File, maxEdge: Int = FULL_MAX_EDGE): Bitmap? {
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null
        return scaleDown(turnUpright(decoded, file), maxEdge)
    }

    /** Writes [bitmap] out as a JPEG and answers how many bytes that took. */
    fun writeJpeg(bitmap: Bitmap, target: File, quality: Int = FULL_QUALITY): Long {
        target.parentFile?.mkdirs()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return target.length()
    }

    /**
     * Writes the small copy a list row draws.
     *
     * Kept as a separate file rather than scaled down from the full one on every
     * frame: the history list can hold a dozen rows with pictures, and decoding
     * a 2000-pixel JPEG for each of them is the difference between a list that
     * scrolls and one that stutters.
     */
    fun writeThumbnail(source: File, target: File): Boolean {
        val bitmap = decode(source, THUMBNAIL_MAX_EDGE) ?: return false
        return try {
            writeJpeg(bitmap, target, THUMBNAIL_QUALITY) > 0L
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * The largest power of two that gets the picture under [maxEdge].
     *
     * A power of two is not a preference, it is what the decoder honours — it
     * rounds anything else down to one, and the memory saving is the whole point
     * of asking.
     */
    private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * Applies the rotation the camera recorded in the file instead of applying
     * it to the pixels.
     *
     * Skipping this is what makes a photo taken in portrait sit on its side
     * everywhere the app draws it, since nothing below Compose reads EXIF.
     */
    private fun turnUpright(bitmap: Bitmap, file: File): Bitmap {
        val degrees = runCatching {
            when (
                ExifInterface(file.path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val turned = runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrNull() ?: return bitmap
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }

    /** Exact final size, after the sampled decode has got it roughly there. */
    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap

        val ratio = maxEdge.toFloat() / longest.toFloat()
        val width = max(1, (bitmap.width * ratio).roundToInt())
        val height = max(1, (bitmap.height * ratio).roundToInt())
        val scaled = runCatching {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }.getOrNull() ?: return bitmap
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
