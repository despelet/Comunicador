package com.comunic.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_items",
    indices = [
        Index(value = ["categoryId", "orderIndex"]),
        Index(value = ["pictogramId"])
    ]
)
data class CategoryItemEntity(
    @PrimaryKey val placementId: String,
    val categoryId: String,
    val pictogramId: String,
    val orderIndex: Int
)

