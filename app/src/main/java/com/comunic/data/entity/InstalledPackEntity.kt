package com.comunic.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_packs")
data class InstalledPackEntity(
    @PrimaryKey val packId: String,
    val version: Int,
    val installedAt: Long
)
