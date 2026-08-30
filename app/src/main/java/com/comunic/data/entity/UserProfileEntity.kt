package com.comunic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(

    @PrimaryKey
    val userId: String,
    val displayName: String,
    val role: String,
    val createdAt: Long,

    @ColumnInfo(defaultValue = "1")
    val isActive: Boolean = true
)