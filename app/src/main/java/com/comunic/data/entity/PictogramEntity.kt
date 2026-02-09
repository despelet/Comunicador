package com.comunic.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pictograms",
    indices = [Index(value = ["packId"])]
)
data class PictogramEntity(
    @PrimaryKey val pictogramId: String,
    val packId: String,
    val baseLabel: String,
    val baseImageUri: String,
    val createdAt: Long
)
