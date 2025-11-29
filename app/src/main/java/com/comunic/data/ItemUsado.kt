package com.comunic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items_usados")
data class ItemUsado(
    @PrimaryKey val nombreArchivo: String,
    var cantidadDeUsos: Int = 0,
    var ultimaFechaUso: Long = System.currentTimeMillis()
)

