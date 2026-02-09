package com.comunic.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.entity.PictogramEntity
import com.comunic.data.entity.PictogramOverrideEntity

@Dao
interface PictogramDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(pictograms: List<PictogramEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlacements(items: List<CategoryItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(override: PictogramOverrideEntity)

    @Query("DELETE FROM category_items WHERE placementId = :placementId")
    suspend fun deletePlacement(placementId: String)

    data class PictogramUiRow(
        val pictogramId: String,
        val label: String,
        val imageUri: String
    )

    @Query("""
    SELECT 
      p.pictogramId AS pictogramId,
      COALESCE(o.customLabel, p.baseLabel) AS label,
      COALESCE(o.customImageUri, p.baseImageUri) AS imageUri
    FROM category_items ci
    JOIN pictograms p ON p.pictogramId = ci.pictogramId
    LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
    WHERE ci.categoryId = :categoryId
    ORDER BY ci.orderIndex ASC
""")
    suspend fun getPictosForCategory(categoryId: String): List<PictogramUiRow>
}