package com.esa.moneytracker.ui.viewer

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.esa.moneytracker.data.attachment.AttachmentFiles
import com.esa.moneytracker.data.model.Attachment
import com.esa.moneytracker.ui.components.AttachmentThumbnail
import com.esa.moneytracker.ui.components.rememberDecodedImage
import com.esa.moneytracker.util.Images

/**
 * One lampiran, as large as the screen will allow.
 *
 * Pinch to zoom, drag to move, double-tap to go back to the whole picture — the
 * three gestures a photo of a receipt actually needs, and the reason a struk is
 * worth keeping as a picture at all: the total at the bottom is legible when it
 * is six times its size on a phone screen.
 *
 * Pictures are stepped through with the rail underneath rather than by swiping.
 * A swipe already means "move the zoomed picture", and giving it a second
 * meaning is how a viewer ends up fighting the finger that is using it.
 */
@Composable
fun AttachmentViewerScreen(
    state: ViewerUiState,
    initialIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var index by remember { mutableStateOf(initialIndex) }

    // The list is live: deleting the picture being looked at moves the viewer to
    // whatever is left rather than leaving it staring at a gap.
    LaunchedEffect(state.attachments.size) {
        if (index >= state.attachments.size) index = (state.attachments.size - 1).coerceAtLeast(0)
    }
    LaunchedEffect(state.empty) {
        if (state.empty) onBack()
    }

    val current = state.attachments.getOrNull(index)

    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ViewerTopBar(
                title = current?.displayLabel.orEmpty(),
                position = if (state.attachments.size > 1) {
                    (index + 1).toString() + " dari " + state.attachments.size
                } else {
                    null
                },
                onBack = onBack,
                onShare = current?.let { attachment ->
                    {
                        share(context, attachment)
                    }
                },
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.loading -> CircularProgressIndicator(color = Color.White)
                    current != null -> ZoomableAttachment(current)
                }
            }

            if (state.attachments.size > 1) {
                ThumbnailRail(
                    attachments = state.attachments,
                    selected = index,
                    onSelect = { index = it },
                )
            }
        }
    }
}

/**
 * The picture, with the three gestures.
 *
 * The zoom is capped at six because past that a JPEG that is two thousand pixels
 * across is only showing its own compression, and the pan is only allowed once
 * there is something outside the screen to move to.
 */
@Composable
private fun ZoomableAttachment(attachment: Attachment) {
    val context = LocalContext.current
    val file = remember(attachment.id) { AttachmentFiles.full(context, attachment) }
    val image = rememberDecodedImage(file, Images.FULL_MAX_EDGE)

    var scale by remember(attachment.id) { mutableFloatStateOf(1f) }
    var offset by remember(attachment.id) { mutableStateOf(Offset.Zero) }

    // The centroid is ignored on purpose: the picture is zoomed about its own
    // middle, which is where a receipt's writing is and where the drag that
    // follows can reach everything else from anyway.
    val transform = rememberTransformableState { _, zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        offset = if (scale <= MIN_SCALE) Offset.Zero else offset + panChange
    }

    when (image) {
        null -> Text(
            text = "Lampiran ini tidak ada lagi di ponsel ini.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )

        else -> Image(
            bitmap = image,
            contentDescription = attachment.displayLabel,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transform)
                .pointerInput(attachment.id) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > MIN_SCALE) {
                                scale = MIN_SCALE
                                offset = Offset.Zero
                            } else {
                                scale = DOUBLE_TAP_SCALE
                            }
                        },
                    )
                },
        )
    }
}

@Composable
private fun ViewerTopBar(
    title: String,
    position: String?,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Tutup",
                tint = Color.White,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
            )
            if (position != null) {
                Text(
                    text = position,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        if (onShare != null) {
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Bagikan",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ThumbnailRail(
    attachments: List<Attachment>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEachIndexed { index, attachment ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (index == selected) 2.dp else 0.dp,
                        color = if (index == selected) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(2.dp),
            ) {
                AttachmentThumbnail(
                    attachment = attachment,
                    size = 48.dp,
                    corner = 10.dp,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

/**
 * Hands the picture to whatever the user picks in the share sheet.
 *
 * Through the `FileProvider`, because the file lives in the app's own folder
 * where no other app can reach it — the grant travels with the intent and lasts
 * only as long as the app it was handed to needs it.
 */
private fun share(context: android.content.Context, attachment: Attachment) {
    val file = AttachmentFiles.full(context, attachment)
    if (!file.exists()) return

    val uri = runCatching {
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }.getOrNull() ?: return

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Bagikan lampiran")) }
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f
