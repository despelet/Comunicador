package com.comunic.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.comunic.CategoryPreviewRow
import com.comunic.data.entity.CategoryEntity

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY orderIndex ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Query("""
SELECT 
  c.categoryId AS categoryId,
  c.name AS name,
  COALESCE(o.customImageUri, p.baseImageUri) AS imageUri
FROM categories c
JOIN category_items ci ON ci.categoryId = c.categoryId
JOIN pictograms p ON p.pictogramId = ci.pictogramId
LEFT JOIN pictogram_overrides o ON o.pictogramId = p.pictogramId
ORDER BY c.orderIndex ASC, ci.orderIndex ASC
""")
    suspend fun getAllCategoryPreviewRows(): List<CategoryPreviewRow>
}