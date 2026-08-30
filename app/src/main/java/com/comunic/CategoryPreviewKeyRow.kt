package com.comunic

data class CategoryPreviewKeyRow(
    val categoryId: String,
    val name: String,
    val itemKey: String?,
    val isSystem: Boolean,
    val packId: String,
    val packEnabled: Boolean
)