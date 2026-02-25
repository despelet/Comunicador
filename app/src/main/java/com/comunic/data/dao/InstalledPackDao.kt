package com.comunic.data.dao

import androidx.room.*
import com.comunic.data.entity.InstalledPackEntity

@Dao
interface InstalledPackDao {
    @Query("SELECT * FROM installed_packs WHERE packId = :packId LIMIT 1")
    suspend fun get(packId: String): InstalledPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pack: InstalledPackEntity)

    @Query("UPDATE installed_packs SET enabled = :enabled WHERE packId = :packId")
    suspend fun setEnabled(packId: String, enabled: Boolean)

    @Query("SELECT enabled FROM installed_packs WHERE packId = :packId LIMIT 1")
    suspend fun isEnabled(packId: String): Boolean?
}