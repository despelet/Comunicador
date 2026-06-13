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


    @Query("""
    SELECT * FROM media_items
    WHERE displayName = :displayName
    LIMIT 1
""")
    suspend fun getAnyByDisplayName(displayName: String): MediaEntity? // Hace una búsqueda sin importar si está eliminado o no. para no crear duplicado

    @Query("""
    SELECT * FROM media_items
    WHERE isDeleted = 1
    ORDER BY updatedAt DESC
""")
    suspend fun getDeletedMedia(): List<MediaEntity>

    @Query("""
    UPDATE media_items
    SET isDeleted = 0,
        updatedAt = :updatedAt
    WHERE mediaId = :mediaId
""")
    suspend fun restore(
        mediaId: String,
        updatedAt: Long
    )

    @Query("""
    SELECT * FROM media_items
    WHERE isDeleted = 0
    AND ownerUserId = :userId
    ORDER BY createdAt DESC
""")
    suspend fun getActiveMediaForUser(userId: String): List<MediaEntity>
    @Query("""
    SELECT * FROM media_items
    WHERE isDeleted = 1
    AND ownerUserId = :userId
    ORDER BY updatedAt DESC
""")
    suspend fun getDeletedMediaForUser(userId: String): List<MediaEntity>

    @Query("""
    UPDATE media_items
    SET ownerUserId = :newUserId,
        updatedAt = :updatedAt
    WHERE ownerUserId = :oldUserId
""")
    suspend fun adoptMediaToUser(
        oldUserId: String,
        newUserId: String,
        updatedAt: Long
    ) // Cuando un usuario se registra, adoptamos los medios que haya creado antes de registrarse (que estaban asociados a su userId temporal) y los asociamos a su nuevo userId permanente. Esto permite que el usuario no pierda acceso a los medios que creó antes de registrarse, y que esos medios ahora estén correctamente asociados a su cuenta de usuario.

}