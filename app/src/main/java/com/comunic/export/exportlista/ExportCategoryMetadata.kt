package com.comunic.export.exportlista

data class ExportCategoryMetadata(
    val categoryId: String,
    val name: String,
    val createdAt: Long,
    val items: List<ExportCategoryItem>
)