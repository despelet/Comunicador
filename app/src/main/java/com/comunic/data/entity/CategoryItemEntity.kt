package com.comunic.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_items",
    indices = [
        Index(value = ["categoryId", "orderIndex"]),
       // Index(value = ["pictogramId"])
        Index(value = ["itemKey"])

    ]
)
data class CategoryItemEntity(
    @PrimaryKey val placementId: String,
    val categoryId: String,
    val itemKey: String, // pictos o imagenes
    val orderIndex: Int
)

