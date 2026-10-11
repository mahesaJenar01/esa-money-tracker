package com.esa.moneytracker.data.export

import com.esa.moneytracker.data.local.CategoryEntity
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.TransactionType
import kotlinx.serialization.Serializable

@Serializable
data class CategoryExportRecord(
    val id: String,
    val label: String,
    val type: String,
    val archived: Boolean = false,
) {
    fun toEntity(): CategoryEntity? {
        if (id.isBlank() || Category.fromId(id) != null || label.isBlank() || label.trim().length > 50) return null
        if (TransactionType.entries.none { it.id == type }) return null
        return CategoryEntity(id, label.trim(), type, archived)
    }
}
