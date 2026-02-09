package com.comunic.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pictogram_overrides")
data class PictogramOverrideEntity(
    @PrimaryKey val pictogramId: String,
    val customLabel: String?,
    val customImageUri: String?,
    val updatedAt: Long
)

