package com.comunic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items_usados")
data class ItemUsado(
    @PrimaryKey val nombreArchivo: String,
    var cantidadDeUsos: Int = 0,
    var ultimaFechaUso: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'local_user'")
    val ownerUserId: String = "local_user"
)

