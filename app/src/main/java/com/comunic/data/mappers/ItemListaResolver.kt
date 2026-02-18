package com.comunic.data.mappers

import android.content.Context
import android.net.Uri
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.data.db.AppDatabase
import java.io.File

// ===============================
// Resolver principal
// ===============================
suspend fun resolveItemKeyToItemLista(
    context: Context,
    itemKey: String
): ItemLista? {

    val db = AppDatabase.getDatabase(context)



    return when {

        ItemKey.isPicto(itemKey) -> {
            val id = ItemKey.pictoId(itemKey)
//            val row = db.pictogramDao().getPictoUiById(id)
//            row?.toItemLista(timestamp = 0L)
            val row = db.pictogramDao().getPictoUiById(id)
            val item = row?.toItemLista(timestamp = 0L)
            item?.copy(id = itemKey)
        }

        ItemKey.isMedia(itemKey) -> {
            val base = ItemKey.mediaBase(itemKey)
            val file = findMediaFileByBaseName(context, base) ?: return null

            val ext = file.extension.lowercase()

            val esImagen = ext in listOf("jpg","jpeg","png","webp")
            val esVideo  = ext in listOf("mp4","mkv","avi","mov","webm")

            if (!esImagen && !esVideo) return null

            ItemLista(
//                nombre = base,
                id = itemKey,
                nombre = base,
                uri = Uri.fromFile(file),
                esImagen = esImagen,
                timestamp = file.lastModified()
            )
        }

        else -> null
    }
}


// ===============================
// Helper interno (SOLO usado acá)
// ===============================
private fun findMediaFileByBaseName(
    context: Context,
    base: String
): File? {

    val mediaDir = File(context.filesDir, "media")

    if (!mediaDir.exists()) return null

    return mediaDir.listFiles()
        ?.firstOrNull { it.nameWithoutExtension == base }
}