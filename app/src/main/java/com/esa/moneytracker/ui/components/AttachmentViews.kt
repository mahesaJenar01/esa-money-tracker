package com.esa.moneytracker.ui.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.attachment.AttachmentFiles
import com.esa.moneytracker.data.model.Attachment
import com.esa.moneytracker.data.model.AttachmentSource
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The small copies, kept in memory between one scroll and the next.
 *
 * Bounded by bytes rather than by count, because that is the thing that runs
 * out: a thumbnail is four bytes a pixel once decoded, so a few dozen of them
 * are megabytes. Six is enough for every row of a week's history and small
 * enough that the app never has to argue with the rest of the phone for it.
 *
 * Only the small copies are cached. A full-size picture is a viewer away and is
 * measured in tens of megabytes, so it is decoded when it is opened and let go
 * of when it is closed.
 */
private object ThumbnailCache : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/**
 * Decodes [file] off the main thread and hands back the picture when it is
 * ready, or null while it is not — and for good if the file has gone.
 *
 * A missing file is an ordinary answer, not a fault. Pictures can be lost to a
 * restore that carried the notes without them, and a row that outlives its file
 * should draw a placeholder rather than take the screen down with it.
 */
@Composable
fun rememberDecodedImage(
    file: File?,
    maxEdge: Int,
    /** Only ever true for the small copies; see [ThumbnailCache]. */
    cached: Boolean = false,
): ImageBitmap? {
    val path = file?.path
    // The stamp is part of the key so a rewritten file is never served from the
    // cache — a redrawn thumbnail after an import lands on the same name.
    val stamp = remember(path) { file?.lastModified() ?: 0L }
    val key = remember(path, stamp, maxEdge) { path + "@" + stamp + "@" + maxEdge }

    return produceState<ImageBitmap?>(
        initialValue = if (cached) ThumbnailCache.get(key) else null,
        key1 = key,
    ) {
        if (file == null) {
            value = null
            return@produceState
        }
        if (cached) {
            ThumbnailCache.get(key)?.let {
                value = it
                return@produceState
            }
        }
        val decoded = withContext(Dispatchers.IO) {
            Images.decode(file, maxEdge)?.asImageBitmap()
        }
        if (decoded != null && cached) ThumbnailCache.put(key, decoded)
        value = decoded
    }.value
}

/**
 * One lampiran as a small square.
 *
 * Cropped rather than fitted: a receipt is a long, narrow thing, and fitting one
 * into a square leaves it as a sliver with air above and below. A square crop of
 * the top of the receipt is what makes a row of them readable at a glance.
 */
@Composable
fun AttachmentThumbnail(
    attachment: Attachment,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    corner: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val file = remember(attachment.id) { AttachmentFiles.thumbnail(context, attachment) }
    val image = rememberDecodedImage(file, Images.THUMBNAIL_MAX_EDGE, cached = true)
    val colors = MoneyTheme.colors

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(colors.surfaceElevated)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = attachment.displayLabel,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.HideImage,
                contentDescription = "Lampiran tidak ditemukan",
                tint = colors.muted,
                modifier = Modifier.size(size * 0.45f),
            )
        }

        // A rendered invoice page and a photo of a struk look alike at this
        // size; the corner mark is what tells them apart without opening either.
        if (attachment.source == AttachmentSource.PDF && size >= 44.dp) {
            Icon(
                imageVector = Icons.Rounded.PictureAsPdf,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                    .padding(2.dp)
                    .size(11.dp),
            )
        }
    }
}

/**
 * The mark a history row carries when its note has pictures.
 *
 * Deliberately the picture itself rather than a paperclip icon: the whole point
 * of putting it in the list is being able to spot *which* note has the receipt
 * you are looking for without opening any of them. When there are several, the
 * first one is shown with a count over the corner.
 */
@Composable
fun AttachmentBadge(
    attachments: List<Attachment>,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    onOpen: ((Int) -> Unit)? = null,
) {
    val first = attachments.firstOrNull() ?: return

    Box(modifier) {
        AttachmentThumbnail(
            attachment = first,
            size = size,
            corner = 10.dp,
            onClick = onOpen?.let { open -> { open(0) } },
        )
        if (attachments.size > 1) {
            Text(
                text = "+" + (attachments.size - 1),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * Every picture on one note, side by side.
 *
 * Shown where there is room to be useful — an opened history row, the edit page
 * — at a size where a struk's shop name is already readable, so most of the time
 * the full-screen viewer is not needed at all.
 */
@Composable
fun AttachmentStrip(
    attachments: List<Attachment>,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onOpen: ((Int) -> Unit)? = null,
) {
    if (attachments.isEmpty()) return

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEachIndexed { index, attachment ->
            AttachmentThumbnail(
                attachment = attachment,
                size = size,
                onClick = onOpen?.let { open -> { open(index) } },
            )
        }
    }
}
