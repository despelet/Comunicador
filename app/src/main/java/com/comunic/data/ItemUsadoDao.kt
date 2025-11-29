package com.comunic.data

import androidx.room.*

@Dao
interface ItemUsadoDao {

    @Query("SELECT * FROM items_usados")
    suspend fun getAllItemsUsados(): List<ItemUsado>

    @Query("SELECT * FROM items_usados ORDER BY cantidadDeUsos DESC")
    suspend fun obtenerMasUsados(): List<ItemUsado>

    @Query("SELECT * FROM items_usados ORDER BY cantidadDeUsos DESC LIMIT 6")
    suspend fun getTop6ItemsUsados(): List<ItemUsado>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(item: ItemUsado)

    @Query("SELECT * FROM items_usados WHERE nombreArchivo = :nombre")
    suspend fun obtenerPorNombre(nombre: String): ItemUsado?

    @Query("DELETE FROM items_usados")
    suspend fun borrarTodo()
}
