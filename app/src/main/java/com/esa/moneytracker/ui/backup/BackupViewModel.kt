package com.esa.moneytracker.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.export.BackupDocument
import com.esa.moneytracker.data.export.ExportFormat
import com.esa.moneytracker.data.model.OpeningBalances
import com.esa.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The outcome of the last export or import, shown as a banner. */
data class BackupMessage(val text: String, val ok: Boolean)

data class BackupUiState(
    val busy: Boolean = false,
    /** Live notes that an export would carry. */
    val recordCount: Int = 0,
    val binCount: Int = 0,
    val openingBalances: OpeningBalances = OpeningBalances(),
    /** Open banks an export would carry. */
    val bankCount: Int = 0,
    /**
     * The starting point a restore would reproduce: the cash figure plus what
     * each bank was said to hold before any note was written against it.
     */
    val openingTotal: Long = 0L,
    val message: BackupMessage? = null,
)

/**
 * Export and import, both driven by a file the user picks in the system picker.
 *
 * Neither direction touches the filesystem itself: the screen supplies a reader
 * or a writer bound to the chosen document, and this view model only decides
 * what goes in it and what comes out.
 */
class BackupViewModel(
    private val repository: TransactionRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val local = MutableStateFlow(BackupUiState())

    val state: StateFlow<BackupUiState> =
        combine(
            repository.observeAll(),
            repository.observeDeleted(),
            repository.observeOpeningBalances(),
            repository.observeBanks(),
            local,
        ) { transactions, deleted, opening, banks, current ->
            val open = banks.filterNot { it.archived }
            current.copy(
                recordCount = transactions.size,
                binCount = deleted.size,
                openingBalances = opening,
                bankCount = open.size,
                openingTotal = opening.cash + open.sumOf { it.baseBalance },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BackupUiState(),
        )

    /** A sensible name to suggest in the save dialog. */
    fun suggestedFileName(format: ExportFormat): String =
        format.fileName(STAMP.format(LocalDateTime.now(zone)))

    /**
     * Renders the whole app into [format] and hands it to [sink], which writes
     * it to the document the user chose.
     */
    fun export(format: ExportFormat, sink: (String) -> Boolean) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val document = repository.exportBackup(zone)
            val body = format.render(document)
            // The document can live on a slow provider (Drive, a network share),
            // so the write never happens on the main thread.
            val written = withContext(Dispatchers.IO) { sink(body) }
            local.update {
                it.copy(
                    busy = false,
                    message = if (written) {
                        BackupMessage(
                            text = document.transactions.size.toString() +
                                " catatan tersimpan ke berkas " + format.extension.uppercase() + ".",
                            ok = true,
                        )
                    } else {
                        BackupMessage("Berkas gagal ditulis. Coba pilih lokasi lain.", ok = false)
                    },
                )
            }
        }
    }

    /** Reads a backup from [source] and merges it in. */
    fun importBackup(source: () -> String?) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            local.update { it.copy(busy = false, message = runImport(repository, source)) }
        }
    }

    companion object {
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                BackupViewModel(app.repository)
            }
        }
    }
}

/**
 * The one import path, shared by the backup screen and the first-run screen.
 *
 * Every failure is a message rather than an exception: the file came from
 * outside the app, so being handed the wrong one is an ordinary thing to say out
 * loud, not a crash.
 */
suspend fun runImport(
    repository: TransactionRepository,
    source: () -> String?,
): BackupMessage {
    val text = withContext(Dispatchers.IO) { source() }
        ?: return BackupMessage("Berkas tidak bisa dibuka.", ok = false)

    val document = BackupDocument.parse(text)
        ?: return BackupMessage(
            text = "Berkas ini bukan cadangan yang bisa dibaca. Pilih berkas .json " +
                "hasil ekspor aplikasi ini.",
            ok = false,
        )

    if (!document.recognised) {
        return BackupMessage(
            text = "Berkas ini dibuat aplikasi lain atau versi yang lebih baru.",
            ok = false,
        )
    }

    if (document.transactions.isEmpty() && document.openingBalance == null) {
        return BackupMessage("Berkas cadangan ini kosong.", ok = false)
    }

    val result = repository.importBackup(document)
    val parts = buildList {
        if (result.added > 0) add(result.added.toString() + " catatan ditambahkan")
        if (result.updated > 0) add(result.updated.toString() + " catatan diperbarui")
        if (result.banksImported > 0) add(result.banksImported.toString() + " bank dipulihkan")
        if (result.checksImported > 0) {
            add(result.checksImported.toString() + " penanda cek saldo dipulihkan")
        }
        if (result.openingBalanceApplied) add("saldo awal dipulihkan")
        if (result.skipped > 0) add(result.skipped.toString() + " baris dilewati")
        // A file from before banks existed is not refused, it is converted — and
        // the user is told, because a bank they never made has just appeared.
        result.foldedIntoBank?.let { bank ->
            add("saldo online berkas lama dikumpulkan ke bank " + bank)
        }
    }

    return BackupMessage(
        text = if (parts.isEmpty()) {
            "Tidak ada yang perlu diimpor."
        } else {
            parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
        },
        ok = true,
    )
}
