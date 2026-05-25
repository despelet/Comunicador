package com.comunic.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.comunic.data.entity.MediaEntity

@Dao
interface MediaDao {

    @Query("""
        SELECT * FROM media_items
        WHERE isDeleted = 0
        ORDER BY createdAt DESC
    """)
    suspend fun getActiveMedia(): List<MediaEntity> // Trae solo los medios que no están marcados como eliminados, ordenados por fecha de creación (más recientes primero)

    @Query("""
        SELECT * FROM media_items
        WHERE mediaId = :mediaId
        LIMIT 1
    """)
    suspend fun getById(mediaId: String): MediaEntity? // Trae un medio específico por su ID, sin importar si está marcado como eliminado o no

    @Query("""
        SELECT * FROM media_items
        WHERE displayName = :displayName
        AND isDeleted = 0
        LIMIT 1
    """)
    suspend fun getActiveByDisplayName(displayName: String): MediaEntity? // Trae un medio específico por su nombre para mostrar, pero solo si no está marcado como eliminado

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(media: MediaEntity) // Trae un medio específico por su nombre para mostrar, pero solo si no está marcado como eliminado

    @Query("""
        UPDATE media_items
        SET isDeleted = 1,
            updatedAt = :updatedAt
        WHERE mediaId = :mediaId
    """)
    suspend fun softDelete(
        mediaId: String,
        updatedAt: Long
    ) // Trae un medio específico por su nombre para mostrar, pero solo si no está marcado como eliminado



}