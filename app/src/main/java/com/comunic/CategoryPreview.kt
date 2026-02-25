package com.comunic


data class CategoryPreview(
    val categoryId: String,
    val name: String,
    val previewUris: List<String>,
    val isSystem: Boolean = false,
    val packId: String = "",
    val packEnabled: Boolean = true
)