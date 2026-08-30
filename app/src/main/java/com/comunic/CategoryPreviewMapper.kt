package com.comunic

import com.comunic.CategoryPreviewRow

object CategoryPreviewMapper {

    fun build(rows: List<CategoryPreviewRow>): List<CategoryPreview> {
        val map = LinkedHashMap<String, Pair<String, MutableList<String>>>()

        for (r in rows) {
            val entry = map.getOrPut(r.categoryId) { r.name to mutableListOf() }
            val list = entry.second
            if (list.size < 4 && r.imageUri.isNotBlank()) list.add(r.imageUri)        }

        return map.map { (categoryId, pair) ->
            CategoryPreview(categoryId, pair.first, pair.second)
        }
    }
}