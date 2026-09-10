package com.esa.moneytracker.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.esa.moneytracker.data.attachment.AttachmentFiles
import com.esa.moneytracker.data.model.Attachment
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.Images
import java.io.File

/**
 * Lampiran: take a photo, pick one, or hand over a PDF.
 *
 * Three ways in rather than one, because they are genuinely three different
 * moments. The camera is for the struk that is in your hand right now, the
 * gallery for the transfer receipt you screenshotted an hour ago, and the PDF
 * for the invoice that arrived by email. What comes out is the same in all three
 * cases: a picture, already shrunk, already on disk.
 *
 * None of them asks for a permission. The camera writes through a `FileProvider`
 * into the app's own cache, the gallery goes through the system photo picker,
 * and the PDF through the ordinary document picker — every one of them hands
 * back exactly the file the user chose and nothing else.
 */
@Composable
fun AttachmentPicker(
    attachments: List<Attachment>,
    /** True while a picked file is being shrunk or a PDF rendered. */
    busy: Boolean,
    onPicked: (Uri) -> Unit,
    onCaptured: (File) -> Unit,
    onRemove: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    /** Said under the buttons when the page needs to explain itself. */
    footnote: String? = null,
) {
    val context = LocalContext.current
    val colors = MoneyTheme.colors
    val full = attachments.size >= Attachment.MAX_PER_TRANSACTION

    // Where the camera is about to write. Remembered rather than passed to the
    // launcher and forgotten, because the result only says whether a picture was
    // taken — the file it went into is ours to keep track of.
    var pending by remember { mutableStateOf<File?>(null) }

    // Tapping a tile shows it large without leaving the form. The proper viewer
    // is reached from the history list, and needs a saved note to point at.
    var previewing by remember { mutableStateOf<Attachment?>(null) }
    previewing?.let { attachment ->
        AttachmentPreviewDialog(attachment) { previewing = null }
    }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { taken ->
        val file = pending
        pending = null
        if (taken && file != null) onCaptured(file) else file?.delete()
    }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onPicked(uri) }

    val document = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onPicked(uri) }

    Column(modifier.fillMaxWidth()) {
        if (attachments.isNotEmpty()) {
            // Scrollable, because ten of these are wider than any phone.
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                attachments.forEach { attachment ->
                    PickedTile(
                        attachment = attachment,
                        onOpen = { previewing = attachment },
                        onRemove = { onRemove(attachment) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceButton(
                icon = Icons.Rounded.PhotoCamera,
                label = "Kamera",
                enabled = !busy && !full,
                modifier = Modifier.weight(1f),
            ) {
                val file = AttachmentFiles.newCaptureFile(context)
                pending = file
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file,
                )
                // A phone with no camera app at all throws rather than returning
                // an empty result, and that is not worth crashing over.
                runCatching { camera.launch(uri) }.onFailure {
                    pending = null
                    file.delete()
                }
            }
            SourceButton(
                icon = Icons.Rounded.PhotoLibrary,
                label = "Galeri",
                enabled = !busy && !full,
                modifier = Modifier.weight(1f),
            ) {
                gallery.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            SourceButton(
                icon = Icons.Rounded.PictureAsPdf,
                label = "PDF",
                enabled = !busy && !full,
                modifier = Modifier.weight(1f),
            ) {
                document.launch(arrayOf("application/pdf"))
            }
        }

        if (busy) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Menyiapkan lampiran…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val note = when {
            full -> "Sudah " + Attachment.MAX_PER_TRANSACTION +
                " lampiran — hapus salah satu dulu kalau mau menambah."
            else -> footnote
        }
        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
    }
}

/**
 * One picked picture, filling the screen until it is tapped away.
 *
 * Fitted rather than zoomable: this exists to answer "did that come out
 * readable?" before the note is even saved, and the viewer with the gestures in
 * it is one tap away from the history row afterwards.
 */
@Composable
private fun AttachmentPreviewDialog(attachment: Attachment, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = remember(attachment.id) { AttachmentFiles.full(context, attachment) }
    val image = rememberDecodedImage(file, Images.FULL_MAX_EDGE)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = attachment.displayLabel,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
            }
            Text(
                text = attachment.displayLabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
            )
        }
    }
}

/** One picked picture, with the way to take it back off again. */
@Composable
private fun PickedTile(
    attachment: Attachment,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Box {
        AttachmentThumbnail(
            attachment = attachment,
            size = 72.dp,
            corner = 14.dp,
            onClick = onOpen,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Hapus lampiran",
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun SourceButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MoneyTheme.colors
    val tint = if (enabled) MaterialTheme.colorScheme.primary else colors.muted

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairline, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}
