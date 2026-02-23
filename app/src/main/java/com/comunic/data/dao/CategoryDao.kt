package com.comunic.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.comunic.CategoryPreviewKeyRow
import com.comunic.CategoryPreviewRow
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity

@Dao
interface CategoryDao {

    // ===== Categories =====
    @Query("SELECT * FROM categories ORDER BY orderIndex ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)


    // ===== Category items (placements) =====

    // Traer itemKeys de una categoría para armar el recycler del detalle
    @Query("""
        SELECT itemKey
        FROM category_items
        WHERE categoryId = :categoryId
        ORDER BY orderIndex ASC
    """)
    suspend fun getItemKeysForCategory(categoryId: String): List<String>

    // Para calcular el próximo orderIndex al agregar
    @Query("""
        SELECT COALESCE(MAX(orderIndex), -1)
        FROM category_items
        WHERE categoryId = :categoryId
    """)
    suspend fun getMaxOrderIndex(categoryId: String): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM categories")
    suspend fun getMaxCategoryOrderIndex(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategoryItem(item: CategoryItemEntity)

    @Query("DELETE FROM category_items WHERE placementId = :placementId")
    suspend fun deletePlacement(placementId: String)


    // ===== Preview de categorías =====
    @Query("""
    SELECT
        c.categoryId AS categoryId,
        c.name AS name,
        COALESCE(o.customImageUri, p.baseImageUri) AS imageUri
    FROM categories c
    JOIN category_items ci
        ON ci.categoryId = c.categoryId
    JOIN pictograms p
        ON p.pictogramId = SUBSTR(ci.itemKey, 5)
    LEFT JOIN pictogram_overrides o
        ON o.pictogramId = p.pictogramId
    WHERE ci.itemKey LIKE 'PIC:%'
      AND ci.orderIndex = (
          SELECT MIN(ci2.orderIndex)
          FROM category_items ci2
          WHERE ci2.categoryId = c.categoryId
            AND ci2.itemKey LIKE 'PIC:%'
      )
    ORDER BY c.orderIndex ASC
""")
    suspend fun getAllCategoryPreviewRows(): List<CategoryPreviewRow>

    @Query("""
    SELECT placementId
    FROM category_items
    WHERE categoryId = :categoryId
      AND itemKey = :itemKey
    ORDER BY orderIndex ASC
    LIMIT 1
""")
    suspend fun findPlacementId(categoryId: String, itemKey: String): String?

    @Query("""
SELECT
  c.categoryId AS categoryId,
  c.name AS name,
  COALESCE((
      SELECT COALESCE(o.customImageUri, p.baseImageUri)
      FROM category_items ci
      JOIN pictograms p
        ON p.pictogramId = SUBSTR(ci.itemKey, 5)
      LEFT JOIN pictogram_overrides o
        ON o.pictogramId = p.pictogramId
      WHERE ci.categoryId = c.categoryId
        AND ci.itemKey LIKE 'PIC:%'
      ORDER BY ci.orderIndex ASC
      LIMIT 1
  ), '') AS imageUri
FROM categories c
ORDER BY c.orderIndex ASC
""")
    suspend fun getAllCategoryPreviewRowsIncludingEmpty(): List<CategoryPreviewRow>

    @Query("""
SELECT
  c.categoryId AS categoryId,
  c.name AS name,
  (
    SELECT ci.itemKey
    FROM category_items ci
    WHERE ci.categoryId = c.categoryId
    ORDER BY ci.orderIndex ASC
    LIMIT 1 OFFSET :offset
  ) AS itemKey
FROM categories c
ORDER BY c.orderIndex ASC
""")
    suspend fun getCategoryPreviewKeyRows(offset: Int): List<CategoryPreviewKeyRow>

    @Query("""
    SELECT EXISTS(
        SELECT 1 FROM category_items
        WHERE categoryId = :categoryId AND itemKey = :itemKey
        LIMIT 1
    )
""")
    suspend fun existsItemInCategory(categoryId: String, itemKey: String): Boolean

    data class CategoryMiniRow(
        val categoryId: String,
        val name: String
    )

    @Query("""
    SELECT c.categoryId AS categoryId,
           c.name       AS name
    FROM categories c
    INNER JOIN category_items ci ON ci.categoryId = c.categoryId
    WHERE ci.itemKey = :itemKey
    ORDER BY c.orderIndex ASC
""")
    suspend fun getCategoriesForItemKey(itemKey: String): List<CategoryMiniRow>

}