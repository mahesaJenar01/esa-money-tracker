package com.esa.moneytracker.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.Attachment
import com.esa.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ViewerUiState(
    val loading: Boolean = true,
    val attachments: List<Attachment> = emptyList(),
) {
    /** True once the note's last picture is deleted from under the viewer. */
    val empty: Boolean get() = !loading && attachments.isEmpty()
}

/**
 * The pictures of one note, for looking at full screen.
 *
 * Read straight from the database rather than being handed down the navigation
 * argument, so removing a lampiran from the edit page behind it takes it out of
 * here as well instead of leaving a page that draws a file no longer there.
 */
class AttachmentViewerViewModel(
    repository: TransactionRepository,
    transactionId: String,
) : ViewModel() {

    val state: StateFlow<ViewerUiState> =
        repository.observeAttachmentsFor(transactionId)
            .map { ViewerUiState(loading = false, attachments = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ViewerUiState(),
            )

    companion object {
        fun factory(transactionId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                AttachmentViewerViewModel(app.repository, transactionId)
            }
        }
    }
}
