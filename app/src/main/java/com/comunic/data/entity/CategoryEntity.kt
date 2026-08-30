package com.comunic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["isDeleted"], name = "index_categories_isDeleted"),
        Index(value = ["packId"], name = "index_categories_packId")
    ]
)
data class CategoryEntity(
    @PrimaryKey val categoryId: String,
    val name: String,
    val orderIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,

    @ColumnInfo(defaultValue = "'user'")
    val packId: String = "user",

    @ColumnInfo(defaultValue = "0")
    val isSystem: Boolean = false,

    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,

    @ColumnInfo(defaultValue = "'local_user'")
    val ownerUserId: String = "local_user"
)