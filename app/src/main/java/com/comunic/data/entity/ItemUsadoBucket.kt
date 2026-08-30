package com.comunic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

// entidad nueva en room
@Entity(
    tableName = "items_usados_bucket",
    primaryKeys = ["nombreArchivo", "bucketId", "ownerUserId"]
)
data class ItemUsadoBucket(
    val nombreArchivo: String,
    val bucketId: Int,
    var cantidadDeUsos: Int = 0,
    var ultimaFechaUso: Long = System.currentTimeMillis(),

    @ColumnInfo(defaultValue = "'local_user'")
    val ownerUserId: String = "local_user"
)
