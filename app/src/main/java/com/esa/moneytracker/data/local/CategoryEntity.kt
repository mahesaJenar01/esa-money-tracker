package com.esa.moneytracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.TransactionType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val label: String,
    val type: String,
    @ColumnInfo(name = "archived", defaultValue = "0") val archived: Boolean = false,
) {
    fun toDomain() = Category(id, label, TransactionType.fromId(type), archived)
}
