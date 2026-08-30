package com.comunic.data.dao

import androidx.room.*
import com.comunic.data.entity.ItemUsado

@Dao
interface ItemUsadoDao {

    @Query("""
        SELECT * FROM items_usados
        WHERE ownerUserId = :userId
    """)
    suspend fun getAllItemsUsados(
        userId: String
    ): List<ItemUsado>

    @Query("""
        SELECT * FROM items_usados
        WHERE ownerUserId = :userId
        ORDER BY cantidadDeUsos DESC
    """)
    suspend fun obtenerMasUsados(
        userId: String
    ): List<ItemUsado>

    @Query("""
        SELECT * FROM items_usados
        WHERE ownerUserId = :userId
        ORDER BY cantidadDeUsos DESC
        LIMIT 6
    """)
    suspend fun getTop6ItemsUsados(
        userId: String
    ): List<ItemUsado>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(item: ItemUsado)

    @Query("""
        SELECT * FROM items_usados
        WHERE nombreArchivo = :nombre
        AND ownerUserId = :userId
        LIMIT 1
    """)
    suspend fun obtenerPorNombre(
        nombre: String,
        userId: String
    ): ItemUsado?

    @Query("""
        DELETE FROM items_usados
        WHERE ownerUserId = :userId
    """)
    suspend fun borrarTodo(
        userId: String
    )

    @Query("""
    UPDATE items_usados
    SET ownerUserId = :newUserId
    WHERE ownerUserId = :oldUserId
""")
    suspend fun adoptItemsUsadosToUser(
        oldUserId: String,
        newUserId: String
    ) // Para adoptar los items usados de un usuario a otro, por ejemplo al migrar de un usuario anónimo a uno registrado

}