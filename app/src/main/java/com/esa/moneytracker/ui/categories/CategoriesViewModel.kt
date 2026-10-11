package com.esa.moneytracker.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val revision: Int = 0,
)

class CategoriesViewModel(private val repository: TransactionRepository) : ViewModel() {
    private val form = MutableStateFlow(CategoriesUiState())
    val state = combine(form, repository.observeCategories()) { current, categories ->
        current.copy(categories = categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun dismissError() { form.value = form.value.copy(error = null) }

    fun save(label: String, type: TransactionType, id: String?) = mutate {
        repository.saveCategory(label, type, id)
    }

    fun archive(category: Category) = mutate { repository.archiveCategory(category.id, !category.archived) }

    private fun mutate(block: suspend () -> Unit) {
        if (form.value.busy) return
        form.value = form.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                block()
                form.value = form.value.copy(busy = false, revision = form.value.revision + 1)
            } catch (error: IllegalArgumentException) {
                form.value = form.value.copy(busy = false, error = error.message)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { CategoriesViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp).repository) }
        }
    }
}
