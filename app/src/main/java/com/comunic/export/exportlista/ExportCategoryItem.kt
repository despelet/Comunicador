package com.comunic.export.exportlista

data class ExportCategoryItem(
    val itemKey: String,
    val orderIndex: Int,
    val displayName: String? = null,
    val mediaType: String? = null
)