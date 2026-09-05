package com.esa.moneytracker.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.export.ExportFormat
import com.esa.moneytracker.ui.components.Hairline
import com.esa.moneytracker.ui.components.IconBadge
import com.esa.moneytracker.ui.components.SectionHeader
import com.esa.moneytracker.ui.components.SoftCard
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter

/**
 * Data & cadangan: write the whole app out to a file, or read one back in.
 *
 * The app never picks a location itself — every button opens the system file
 * picker, so the backup lands wherever the user keeps their files and no storage
 * permission is ever asked for.
 */
@Composable
fun BackupScreen(
    state: BackupUiState,
    suggestedFileName: (ExportFormat) -> String,
    onExport: (ExportFormat, (String) -> Boolean) -> Unit,
    onImport: (() -> String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // One launcher per format: a contract's MIME type is fixed when it is
    // created, so a single launcher cannot serve both.
    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mimeType),
    ) { uri ->
        if (uri != null) onExport(ExportFormat.JSON) { DocumentIo.writeText(context, uri, it) }
    }

    val saveSpreadsheet = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType),
    ) { uri ->
        if (uri != null) onExport(ExportFormat.CSV) { DocumentIo.writeText(context, uri, it) }
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImport { DocumentIo.readText(context, uri) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            BackupTopBar(onBack)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                SummaryCard(state)

                state.message?.let { message ->
                    Spacer(Modifier.height(14.dp))
                    MessageBanner(message)
                }

                Spacer(Modifier.height(24.dp))

                SectionHeader(
                    title = "Ekspor",
                    subtitle = "Simpan datamu ke sebuah berkas",
                )
                Spacer(Modifier.height(12.dp))

                ActionRow(
                    icon = Icons.Rounded.Download,
                    title = "Simpan cadangan (.json)",
                    subtitle = "Berisi semua catatan dan saldo awal. Berkas inilah " +
                        "yang bisa diimpor kembali.",
                    enabled = !state.busy,
                    onClick = { saveBackup.launch(suggestedFileName(ExportFormat.JSON)) },
                )
                Spacer(Modifier.height(10.dp))
                ActionRow(
                    icon = Icons.Rounded.TableChart,
                    title = "Simpan spreadsheet (.csv)",
                    subtitle = "Satu baris per catatan, untuk dibuka di Excel atau " +
                        "Google Sheets. Tidak bisa diimpor kembali.",
                    enabled = !state.busy,
                    onClick = { saveSpreadsheet.launch(suggestedFileName(ExportFormat.CSV)) },
                )

                Spacer(Modifier.height(24.dp))

                SectionHeader(
                    title = "Impor",
                    subtitle = "Muat kembali dari berkas cadangan",
                )
                Spacer(Modifier.height(12.dp))

                ActionRow(
                    icon = Icons.Rounded.CloudUpload,
                    title = "Pilih berkas cadangan",
                    subtitle = "Catatan dengan id yang sama akan diperbarui, sisanya " +
                        "ditambahkan. Saldo awal ikut dipulihkan dari berkas.",
                    enabled = !state.busy,
                    onClick = { openBackup.launch(arrayOf(ExportFormat.JSON.mimeType, "*/*")) },
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Mengimpor berkas yang sama dua kali tidak menggandakan " +
                        "catatan. Catatan di tempat sampah tidak ikut diekspor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BackupTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Kembali",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                text = "Data & cadangan",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Ekspor dan impor catatanmu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(state: BackupUiState) {
    SoftCard {
        SummaryLine(
            label = "Catatan tersimpan",
            value = state.recordCount.toString() + " catatan",
        )
        Spacer(Modifier.height(10.dp))
        Hairline()
        Spacer(Modifier.height(10.dp))
        SummaryLine(
            label = "Saldo awal",
            value = CurrencyFormatter.rupiah(state.openingTotal),
        )
        Spacer(Modifier.height(10.dp))
        Hairline()
        Spacer(Modifier.height(10.dp))
        SummaryLine(
            label = "Bank terdaftar",
            value = state.bankCount.toString() + " bank",
        )
        if (state.binCount > 0) {
            Spacer(Modifier.height(10.dp))
            Hairline()
            Spacer(Modifier.height(10.dp))
            SummaryLine(
                label = "Di tempat sampah",
                value = state.binCount.toString() + " catatan",
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun MessageBanner(message: BackupMessage, modifier: Modifier = Modifier) {
    val colors = MoneyTheme.colors
    val tint = if (message.ok) colors.income else colors.expense

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MoneyTheme.colors
    val tint = if (enabled) MaterialTheme.colorScheme.primary else colors.muted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairline, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = icon, tint = tint, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (enabled) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.muted)
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.muted,
            )
        }
    }
}
