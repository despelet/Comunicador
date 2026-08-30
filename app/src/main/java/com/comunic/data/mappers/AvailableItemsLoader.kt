package com.comunic.data.mappers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.data.db.AppDatabase
import com.comunic.session.SessionManager

suspend fun loadAllAvailableItems(
    context: Context,
    db: AppDatabase
): List<ItemLista> {

    val items = mutableListOf<ItemLista>()

    // 1) media del usuario desde Room
    //val mediaItems = db.mediaDao().getActiveMedia()
    val userId = SessionManager(context).getCurrentUserId()

    val mediaItems = db.mediaDao().getActiveMediaForUser(userId)

    Log.d("MEDIA_DB", "========== MEDIA (${mediaItems.size}) ==========")

    mediaItems.forEach { media ->
        Log.d(
            "MEDIA_DB",
            """
        id=${media.mediaId}
        nombre=${media.displayName}
        uri=${media.localUri}
        owner=${media.ownerUserId}
        ------------------------
        """.trimIndent()
        )

        val key = ItemKey.media(media.mediaId)
        items.add(
            ItemLista(
                id = key, // MED:uuid
                nombre = media.displayName,
                uri = Uri.parse(media.localUri),
                esImagen = media.mediaType == "image",
                timestamp = media.createdAt
            )
        )
    }

    // 2) pictos habilitados
    val pictos = db.pictogramDao().getEnabledPictosUi()
    pictos.forEach { row ->
        val key = ItemKey.picto(row.pictogramId)
        items.add(
            row.toItemLista(timestamp = 0L)
                .copy(id = key)
        )
    }

    return items
}

//package com.comunic.data.mappers
//
//import android.content.Context
//import android.net.Uri
//import com.comunic.ItemKey
//import com.comunic.ItemLista
//import com.comunic.data.db.AppDatabase
//import java.io.File
//
//suspend fun loadAllAvailableItems(context: Context, db: AppDatabase): List<ItemLista> {
//    val items = mutableListOf<ItemLista>()
//
//    // 1) media del usuario en /files/media
//    val mediaDir = File(context.filesDir, "media")
//    if (mediaDir.exists()) {
//        mediaDir.listFiles()?.forEach { file ->
//            val ext = file.extension.lowercase()
//            val esImagen = ext in listOf("jpg","jpeg","png","webp")
//            val esVideo  = ext in listOf("mp4","mkv","avi","mov","webm")
//            if (!esImagen && !esVideo) return@forEach
//
//            val base = file.nameWithoutExtension
//            val key = ItemKey.media(base)
//
//            items.add(
//                ItemLista(
//                    id = key,                 // ✅ itemKey
//                    nombre = base,
//                    uri = Uri.fromFile(file),
//                    esImagen = esImagen,
//                    timestamp = file.lastModified()
//                )
//            )
//        }
//    }
//
//    // 2) pictos del pack básico (por packId)
//    val pictos = db.pictogramDao().getPictosUiForPack("basic")
//    pictos.forEach { row ->
//        val key = ItemKey.picto(row.pictogramId)
//        items.add(
//            row.toItemLista(timestamp = 0L).copy(id = key) // ✅ id=itemKey
//        )
//    }
//
//    return items
//}