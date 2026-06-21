package com.comunic.data.entity;

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
        tableName = "media_items",
        indices = [
                Index(value = ["isDeleted"]),
                Index(value = ["createdAt"]),
                Index(value = ["displayName"])
        ]
            )
data class MediaEntity(
        @PrimaryKey val mediaId: String,
        val displayName: String,
        val localUri: String,
        val mediaType: String,
        val createdAt: Long,
        val updatedAt: Long,

        @ColumnInfo(defaultValue = "0")
        val isDeleted: Boolean = false,

        @ColumnInfo(defaultValue = "'local_user'")
        val ownerUserId: String = "local_user",

        @ColumnInfo(
                name = "storagePath",
                defaultValue = "''"
        )
        val storagePath: String = ""
)