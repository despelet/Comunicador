package com.comunic

/*

esto + getCategoriesForListScreen  te da exactamente lo que necesitás:
system disabled → packEnabled=false y quedan al final
en el adapter: si row.isSystem && !row.packEnabled → gris + botón “Activar”
si row.isSystem && row.packEnabled → normal + botón “Desactivar”

*/
data class CategoryListRow(
    val categoryId: String,
    val name: String,
    val orderIndex: Int,
    val packId: String,
    val isSystem: Boolean,
    val packEnabled: Boolean
)