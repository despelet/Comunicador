package com.comunic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "installed_packs",
    indices = [Index(value = ["enabled"], name = "index_installed_packs_enabled")]
)
data class InstalledPackEntity(
    @PrimaryKey val packId: String,
    val version: Int,
    val installedAt: Long,

    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,

    @ColumnInfo(defaultValue = "0")
    val isSystem: Boolean = false
)
