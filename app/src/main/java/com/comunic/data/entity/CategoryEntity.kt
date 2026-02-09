package com.comunic.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val categoryId: String,
    val name: String,
    val orderIndex: Int,
    val createdAt: Long
)
