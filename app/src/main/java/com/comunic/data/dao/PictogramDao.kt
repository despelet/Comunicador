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
        val imageUri: String,
        val packId: String
    )

//    @Query("""
//    SELECT
//      p.pictogramId AS pictogramId,
//      COALESCE(o.customLabel, p.baseLabel) AS label,
//      COALESCE(o.customImageUri, p.baseImageUri) AS imageUri
//    FROM category_items ci
//    JOIN pictograms p ON p.pictogramId = ci.pictogramId
//    LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
//    WHERE ci.categoryId = :categoryId
//    ORDER BY ci.orderIndex ASC
//""")
//    suspend fun getPictosForCategory(categoryId: String): List<PictogramUiRow>


    // traer pictos por lista de ids
    @Query("""
    SELECT
      p.pictogramId AS pictogramId,
      COALESCE(o.customLabel, p.baseLabel) AS label,
      COALESCE(o.customImageUri, p.baseImageUri) AS imageUri,
      p.packId AS packId
    FROM pictograms p
    LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
    WHERE p.pictogramId IN (:ids)
""")
    suspend fun getPictosByIds(ids: List<String>): List<PictogramUiRow>

    // label para palabraaudio
    @Query("""
    SELECT
      p.pictogramId AS pictogramId,
      COALESCE(o.customLabel, p.baseLabel) AS label,
      COALESCE(o.customImageUri, p.baseImageUri) AS imageUri,
      p.packId AS packId
    FROM pictograms p
    LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
    WHERE p.pictogramId = :id
    LIMIT 1
""")
    suspend fun getPictoById(id: String): PictogramUiRow?

    @Query("""
    SELECT * FROM pictograms
    WHERE packId = :packId
    ORDER BY createdAt ASC
""")
    suspend fun getPictosForPack(packId: String): List<PictogramEntity>

    @Query("""
SELECT
    p.pictogramId AS pictogramId,
    COALESCE(o.customLabel, p.baseLabel) AS label,
    COALESCE(o.customImageUri, p.baseImageUri) AS imageUri,
    p.packId AS packId
FROM pictograms p
LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
JOIN installed_packs ip ON ip.packId = p.packId
WHERE p.packId = :packId
  AND ip.enabled = 1
ORDER BY p.createdAt ASC
""")
    suspend fun getPictosUiForPack(packId: String): List<PictogramUiRow>

    @Query("""
SELECT
  p.pictogramId AS pictogramId,
  COALESCE(o.customLabel, p.baseLabel) AS label,
  COALESCE(o.customImageUri, p.baseImageUri) AS imageUri,
  p.packId AS packId
FROM pictograms p
LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
LEFT JOIN installed_packs ip ON ip.packId = p.packId
WHERE p.pictogramId = :id
  AND (p.packId = 'user' OR COALESCE(ip.enabled, 1) = 1)
LIMIT 1
""")
    suspend fun getPictoUiById(id: String): PictogramUiRow?

    @Query("""
SELECT
  p.pictogramId AS pictogramId,
  COALESCE(o.customLabel, p.baseLabel) AS label,
  COALESCE(o.customImageUri, p.baseImageUri) AS imageUri,
  p.packId AS packId
FROM pictograms p
LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
JOIN installed_packs ip ON ip.packId = p.packId
WHERE ip.enabled = 1
ORDER BY p.createdAt ASC
""")
    suspend fun getEnabledPictosUi(): List<PictogramUiRow>

    data class PictogramUiRowNoPack(
        val pictogramId: String,
        val label: String,
        val imageUri: String
    )

    @Query("""
    SELECT
      p.pictogramId AS pictogramId,
      COALESCE(o.customLabel, p.baseLabel) AS label,
      COALESCE(o.customImageUri, p.baseImageUri) AS imageUri
    FROM pictograms p
    LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
    LEFT JOIN installed_packs ip ON ip.packId = p.packId
    WHERE p.pictogramId = :id
      AND COALESCE(ip.enabled, 1) = 1
    LIMIT 1
    """)
    suspend fun getPictoUiEnabledById_NoPack(id: String): PictogramUiRowNoPack?

    data class PictoUiMiniRow(
        val pictogramId: String,
        val label: String,
        val imageUri: String
    )

    @Query("""
SELECT p.pictogramId AS pictogramId,
       COALESCE(o.customLabel, p.baseLabel) AS label,
       COALESCE(o.customImageUri, p.baseImageUri) AS imageUri
FROM pictograms p
LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
LEFT JOIN installed_packs ip ON ip.packId = p.packId
WHERE p.pictogramId = :id
  AND COALESCE(ip.enabled, 1) = 1
LIMIT 1
""")
    suspend fun getPictoUiEnabledById(id: String): PictoUiMiniRow?

    @Query("SELECT packId, COUNT(*) AS c FROM pictograms GROUP BY packId")
    suspend fun debugCountPictosByPack(): List<PackCountRow>

    data class PackCountRow(val packId: String, val c: Int)

    // aumque ete dehabiliatado puedo ver la imagen y el nombre
    @Query("""
SELECT p.pictogramId AS pictogramId,
       COALESCE(o.customLabel, p.baseLabel) AS label,
       COALESCE(o.customImageUri, p.baseImageUri) AS imageUri
FROM pictograms p
LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
WHERE p.pictogramId = :id
LIMIT 1
""")
    suspend fun getPictoUiById_NoPackFilter(id: String): PictoUiMiniRow?

}



