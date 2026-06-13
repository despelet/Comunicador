package com.comunic.data.dao

import androidx.room.*
import com.comunic.data.entity.ItemUsadoBucket

@Dao
interface ItemUsadoBucketDao {

    @Query("""
        SELECT * FROM items_usados_bucket 
        WHERE bucketId = :bucketId
        AND ownerUserId = :userId
        ORDER BY cantidadDeUsos DESC 
        LIMIT :limit
    """)
    suspend fun getTopForBucket(
        bucketId: Int,
        limit: Int,
        userId: String
    ): List<ItemUsadoBucket>

    @Query("""
        SELECT * FROM items_usados_bucket 
        WHERE nombreArchivo = :nombre
        AND bucketId = :bucketId
        AND ownerUserId = :userId
        LIMIT 1
    """)
    suspend fun obtenerPorNombreYBucket(
        nombre: String,
        bucketId: Int,
        userId: String
    ): ItemUsadoBucket?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(item: ItemUsadoBucket)

    @Query("""
        DELETE FROM items_usados_bucket
        WHERE ownerUserId = :userId
    """)
    suspend fun borrarTodo(
        userId: String
    )

    @Query("""
    UPDATE items_usados_bucket
    SET ownerUserId = :newUserId
    WHERE ownerUserId = :oldUserId
""")
    suspend fun adoptItemsUsadosBucketToUser(
        oldUserId: String,
        newUserId: String
    ) // Para transferir los items usados bucket de un usuario a otro (ej: al cambiar de cuenta, para no perder el historial de uso reciente)

}