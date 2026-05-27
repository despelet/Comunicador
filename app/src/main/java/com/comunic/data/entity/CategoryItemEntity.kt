package com.comunic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_items",
    indices = [
        Index(value = ["categoryId", "orderIndex"]),
        Index(value = ["itemKey"]),
        Index(value = ["isDeleted"])
    ]
)
data class CategoryItemEntity(
    @PrimaryKey
    val placementId: String,
    val categoryId: String,
    val itemKey: String,
    val orderIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "'local_user'")
    val ownerUserId: String = "local_user"
)

