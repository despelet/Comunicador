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
    @Query("""
SELECT * FROM categories
WHERE isDeleted = 0
AND ownerUserId = :userId
ORDER BY orderIndex ASC
""")
    suspend fun getAll(
        userId: String
    ): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)


    // ===== Category items (placements) =====

    // Traer itemKeys de una categoría para armar el recycler del detalle
    //categorías del sistema → ignoran ownerUserId
    //categorías del usuario → siguen filtrando por usuario
    @Query("""
SELECT ci.itemKey
FROM category_items ci
JOIN categories c
    ON c.categoryId = ci.categoryId
WHERE ci.categoryId = :categoryId
  AND ci.isDeleted = 0
  AND (
        c.isSystem = 1
        OR ci.ownerUserId = :userId
      )
ORDER BY ci.orderIndex ASC
""")
    suspend fun getItemKeysForCategory(
        categoryId: String,
        userId: String
    ): List<String>

    // Para calcular el próximo orderIndex al agregar
    @Query("""
SELECT COALESCE(MAX(ci.orderIndex), -1)
FROM category_items ci
JOIN categories c
    ON c.categoryId = ci.categoryId
WHERE ci.categoryId = :categoryId
  AND ci.isDeleted = 0
  AND (
        c.isSystem = 1
        OR ci.ownerUserId = :userId
      )
""")
    suspend fun getMaxOrderIndex(
        categoryId: String,
        userId: String
    ): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM categories")
    suspend fun getMaxCategoryOrderIndex(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryItem(item: CategoryItemEntity)

    @Query("""
    UPDATE category_items
    SET isDeleted = 1,
        updatedAt = :updatedAt
    WHERE placementId = :placementId
""")
    suspend fun softDeletePlacement(
        placementId: String,
        updatedAt: Long
    )


    // Para mostrar el nombre de la categoría aunque no tenga items, trayendo el itemKey del primer item para usarlo en el detalle de la categoría
    @Query("""
SELECT ci.placementId
FROM category_items ci
JOIN categories c
    ON c.categoryId = ci.categoryId
WHERE ci.categoryId = :categoryId
  AND ci.itemKey = :itemKey
  AND ci.isDeleted = 0
  AND (
        c.isSystem = 1
        OR ci.ownerUserId = :userId
      )
LIMIT 1
""")
    suspend fun findPlacementId(
        categoryId: String,
        itemKey: String,
        userId: String
    ): String?

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
    SELECT 1
    FROM category_items ci
    JOIN categories c
        ON c.categoryId = ci.categoryId
    WHERE ci.categoryId = :categoryId
      AND ci.itemKey = :itemKey
      AND ci.isDeleted = 0
      AND (
            c.isSystem = 1
            OR ci.ownerUserId = :userId
          )
)
""")
    suspend fun existsItemInCategory(
        categoryId: String,
        itemKey: String,
        userId: String
    ): Boolean

    data class CategoryMiniRow(
        val categoryId: String,
        val name: String
    )


    // para que e actualice en el momento el litado de listas al agregar o quitar un item
    @Query("""
SELECT DISTINCT
    c.categoryId,
    c.name
FROM categories c
JOIN category_items ci
    ON ci.categoryId = c.categoryId
WHERE ci.itemKey = :itemKey
  AND ci.isDeleted = 0
  AND c.isDeleted = 0
  AND (
        c.isSystem = 1
        OR ci.ownerUserId = :userId
      )
ORDER BY c.orderIndex
""")
    fun observeCategoriesForItemKey(
        itemKey: String,
        userId: String
    ): Flow<List<CategoryMiniRow>>

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
    WHERE ci.categoryId = c.categoryId AND ci.isDeleted = 0
    ORDER BY ci.orderIndex ASC
    LIMIT 1 OFFSET :offset
  ) AS itemKey,
  COALESCE(c.packId, '') AS packId,
  c.isSystem AS isSystem,
  COALESCE(ip.enabled, 1) AS packEnabled
FROM categories c
LEFT JOIN installed_packs ip ON ip.packId = c.packId
WHERE (c.isSystem = 1)
   OR (
        c.isSystem = 0
        AND c.isDeleted = 0
        AND c.ownerUserId = :userId
      )
ORDER BY
  CASE WHEN c.isSystem = 1 AND COALESCE(ip.enabled, 1) = 0 THEN 1 ELSE 0 END ASC,
  c.orderIndex ASC
""")
    fun getCategoryPreviewKeyRowsForListScreen(offset: Int, userId: String): Flow<List<CategoryPreviewKeyRowList>>

    // Home: SOLO categorías activas (system pack enabled, user no deleted)
// y por cada offset devolvemos el itemKey #0..#3 para armar el mosaico 2x2.
    @Query("""
SELECT
  c.categoryId AS categoryId,
  c.name AS name,
  (
    SELECT ci.itemKey
    FROM category_items ci
    WHERE ci.categoryId = c.categoryId AND ci.isDeleted = 0
    ORDER BY ci.orderIndex ASC
    LIMIT 1 OFFSET :offset
  ) AS itemKey,
  c.isSystem AS isSystem,
  c.packId AS packId,
  ip.enabled AS packEnabled
FROM categories c
LEFT JOIN installed_packs ip ON ip.packId = c.packId
WHERE c.isDeleted = 0
  AND (
      c.isSystem = 1
      OR c.ownerUserId = :userId
  )
ORDER BY
  CASE
    WHEN c.isSystem = 1
     AND COALESCE(ip.enabled, 1) = 0
    THEN 1
    ELSE 0
  END ASC,
  c.orderIndex ASC""")
    suspend fun getHomeActiveCategoryPreviewKeyRows(
        offset: Int,
        userId: String
    ): List<CategoryPreviewKeyRow>

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
AND ownerUserId = :userId
LIMIT 1
""")
    suspend fun getCategoryByName(
        name: String,
        userId: String
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

    @Query("""
SELECT * FROM categories
WHERE isSystem = 0
  AND isDeleted = 0
  AND ownerUserId = :userId
ORDER BY orderIndex ASC
""")
    suspend fun getUserActiveForUser(
        userId: String
    ): List<CategoryEntity>

    @Query("""
    UPDATE categories
    SET ownerUserId = :newUserId,
        updatedAt = :updatedAt
    WHERE ownerUserId = :oldUserId
""")
    suspend fun adoptCategoriesToUser(
        oldUserId: String,
        newUserId: String,
        updatedAt: Long
    ) // Para adoptar las categorías de un usuario al iniciar sesión con otro usuario, o al eliminar la cuenta (en este caso se asignan a un userId genérico "deleted_user" para no perder la info de categorías creadas por el usuario eliminado y que podrían ser útiles si inicia sesión nuevamente o para otros usuarios si eran categorías compartidas)

    @Query("""
    UPDATE category_items
    SET ownerUserId = :newUserId,
        updatedAt = :updatedAt
    WHERE ownerUserId = :oldUserId
""")
    suspend fun adoptCategoryItemsToUser(
        oldUserId: String,
        newUserId: String,
        updatedAt: Long
    ) // Para adoptar los items de las categorías de un usuario al iniciar sesión con otro usuario, o al eliminar la cuenta (en este caso se asignan a un userId genérico "deleted_user" para no perder la info de categorías creadas por el usuario eliminado y que podrían ser útiles si inicia sesión nuevamente o para otros usuarios si eran categorías compartidas)

    // migracion nombre->uuid a lo ya existente
    @Query("""
    SELECT *
    FROM category_items
    WHERE itemKey LIKE 'MED:%'
      AND isDeleted = 0
""")
    suspend fun getActiveMediaPlacements(): List<CategoryItemEntity>

    @Query("""
    UPDATE category_items
    SET itemKey = :newItemKey,
        updatedAt = :updatedAt
    WHERE placementId = :placementId
""")
    suspend fun updatePlacementItemKey(
        placementId: String,
        newItemKey: String,
        updatedAt: Long
    )

    ////////////// funciones para sync
    @Query("""
        SELECT *
        FROM categories
        WHERE ownerUserId = :userId
        """) suspend fun  getAllCategoriesForSync(
        userId: String
        ):List<CategoryEntity>

    @Query("""
        SELECT *
        FROM category_items
        WHERE ownerUserId = :userId

        """) suspend fun  getAllCategoryItemsForSync(
        userId: String
    ):List<CategoryItemEntity>

//    @Query("""
//SELECT *
//FROM category_items
//WHERE categoryId = :categoryId
//""")
//    suspend fun debugCategoryItems(
//        categoryId: String
//    ): List<CategoryItemEntity>

//    @Query("""
//SELECT itemKey
//FROM category_items
//WHERE categoryId = :categoryId
//ORDER BY orderIndex
//""")
//    suspend fun getItemKeysForCategoryDebug(
//        categoryId: String
//    ): List<String>
}