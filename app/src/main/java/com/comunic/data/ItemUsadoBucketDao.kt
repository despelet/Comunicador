package com.comunic.data

import androidx.room.*

@Dao
interface ItemUsadoBucketDao {

    @Query("""
        SELECT * FROM items_usados_bucket 
        WHERE bucketId = :bucketId 
        ORDER BY cantidadDeUsos DESC 
        LIMIT :limit
    """)
    suspend fun getTopForBucket(bucketId: Int, limit: Int): List<ItemUsadoBucket>

    @Query("""
        SELECT * FROM items_usados_bucket 
        WHERE nombreArchivo = :nombre AND bucketId = :bucketId
        LIMIT 1
    """)
    suspend fun obtenerPorNombreYBucket(nombre: String, bucketId: Int): ItemUsadoBucket?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(item: ItemUsadoBucket)

    // NUEVO: vaciar la tabla bucket
    @Query("DELETE FROM items_usados_bucket")
    suspend fun borrarTodo()
}
