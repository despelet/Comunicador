package com.comunic.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.comunic.CategoryListRow
import com.comunic.CategoryPreviewKeyRow
import com.comunic.CategoryPreviewRow
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import kotlinx.coroutines.flow.Flow

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



    // Para mostrar el nombre de la categoría aunque no tenga items, trayendo el itemKey del primer item para usarlo en el detalle de la categoría
    @Query("""
    SELECT placementId
    FROM category_items
    WHERE categoryId = :categoryId
      AND itemKey = :itemKey
    ORDER BY orderIndex ASC
    LIMIT 1
""")
    suspend fun findPlacementId(categoryId: String, itemKey: String): String?

    // Para mostrar el nombre de la categoría aunque no tenga items, trayendo la imagen del primer item si existe
    @Query("""
SELECT
  c.categoryId AS categoryId,
  c.name AS name,
  COALESCE((
      SELECT COALESCE(o.customImageUri, p.baseImageUri)
      FROM category_items ci
      JOIN pictograms p ON p.pictogramId = SUBSTR(ci.itemKey, 5)
      LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
      WHERE ci.categoryId = c.categoryId
        AND ci.itemKey LIKE 'PIC:%'
      ORDER BY ci.orderIndex ASC
      LIMIT 1
  ), '') AS imageUri
FROM categories c
LEFT JOIN installed_packs ip ON ip.packId = c.packId
WHERE c.isDeleted = 0
  AND (c.isSystem = 0 OR COALESCE(ip.enabled, 1) = 1)
ORDER BY c.orderIndex ASC
""")
    suspend fun getAllCategoryPreviewRowsIncludingEmpty(): List<CategoryPreviewRow>


    // Para mostrar el ícono de check en el detalle del item si pertenece a la categoría
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


    // para que e actualice en el momento el litado de listas al agregar o quitar un item
    @Query("""
SELECT c.categoryId AS categoryId,
       c.name       AS name
FROM categories c
LEFT JOIN installed_packs ip ON ip.packId = c.packId
JOIN category_items ci ON ci.categoryId = c.categoryId
WHERE ci.itemKey = :itemKey
  AND c.isDeleted = 0
  AND (c.isSystem = 0 OR COALESCE(ip.enabled, 1) = 1)
ORDER BY c.orderIndex ASC
""")
    fun observeCategoriesForItemKey(itemKey: String): Flow<List<CategoryMiniRow>>

    @Query("""
  SELECT * FROM categories
  WHERE isSystem = 0 AND isDeleted = 0
  ORDER BY orderIndex ASC
""")
    suspend fun getUserActive(): List<CategoryEntity>

    @Query("UPDATE categories SET isDeleted = 1 WHERE categoryId = :categoryId AND isSystem = 0")
    suspend fun softDeleteUserCategory(categoryId: String)

    data class CategoryPreviewKeyRowList(
        val categoryId: String,
        val name: String,
        val itemKey: String?,
        val packId: String,
        val isSystem: Boolean,
        val packEnabled: Boolean
    )

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
  ) AS itemKey,
  COALESCE(c.packId, '') AS packId,
  c.isSystem AS isSystem,
  COALESCE(ip.enabled, 1) AS packEnabled
FROM categories c
LEFT JOIN installed_packs ip ON ip.packId = c.packId
WHERE (c.isSystem = 1)
   OR (c.isSystem = 0 AND c.isDeleted = 0)
ORDER BY
  CASE WHEN c.isSystem = 1 AND COALESCE(ip.enabled, 1) = 0 THEN 1 ELSE 0 END ASC,
  c.orderIndex ASC
""")
    fun getCategoryPreviewKeyRowsForListScreen(offset: Int): Flow<List<CategoryPreviewKeyRowList>>

    // Home: SOLO categorías activas (system pack enabled, user no deleted)
// y por cada offset devolvemos el itemKey #0..#3 para armar el mosaico 2x2.
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
  ) AS itemKey,
  c.isSystem AS isSystem,
  c.packId AS packId,
  ip.enabled AS packEnabled
FROM categories c
LEFT JOIN installed_packs ip ON ip.packId = c.packId
WHERE c.isDeleted = 0
  AND (c.isSystem = 0 OR COALESCE(ip.enabled, 1) = 1)
ORDER BY c.orderIndex ASC
""")
    suspend fun getHomeActiveCategoryPreviewKeyRows(offset: Int): List<CategoryPreviewKeyRow>

    @Query("SELECT * FROM categories WHERE categoryId = :id LIMIT 1")
    suspend fun getCategoryById(id: String): CategoryEntity?

    data class CategoryStatusRow(
        val categoryId: String,
        val isSystem: Boolean,
        val packId: String,
        val packEnabled: Boolean
    )

    @Query("""
SELECT c.categoryId AS categoryId,
       c.isSystem AS isSystem,
       c.packId AS packId,
       COALESCE(ip.enabled, 1) AS packEnabled
FROM categories c
LEFT JOIN installed_packs ip ON ip.packId = c.packId
WHERE c.categoryId = :categoryId
LIMIT 1
""")
    suspend fun getCategoryStatus(categoryId: String): CategoryStatusRow?

    @Query("""
SELECT * FROM categories
WHERE name = :name
AND isDeleted = 0
LIMIT 1
""")
    suspend fun getCategoryByName(
        name: String
    ): CategoryEntity?


    @Query("""
UPDATE categories
SET isDeleted = 1
WHERE categoryId = :categoryId
""")
    suspend fun softDeleteCategory(
        categoryId: String
    )

    @Query("""
SELECT * FROM categories
WHERE isDeleted = 0
ORDER BY orderIndex
""")
    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>>
}