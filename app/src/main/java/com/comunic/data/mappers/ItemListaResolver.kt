package com.comunic.data.mappers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.data.db.AppDatabase
import com.comunic.session.SessionManager
import java.io.File

// ===============================
// Resolver principal
// ===============================
//suspend fun resolveItemKeyToItemLista(
//    context: Context,
//    itemKey: String
//): ItemLista? {
//
//    val TAG = "RESOLVE_KEY"
//    val db = AppDatabase.getDatabase(context)
//
//    Log.d(TAG, "resolve start itemKey='$itemKey'")
//
//    return when {
//        ItemKey.isMedia(itemKey) -> {
//            val base = ItemKey.mediaBase(itemKey)
//            val file = findMediaFileByBaseName(context, base)
//
//            Log.d(TAG, "MEDIA itemKey='$itemKey' base='$base' file=${file?.absolutePath} exists=${file?.exists()}")
//
//            if (file == null) {
//                val mediaDir = File(context.filesDir, "media")
//                Log.d(TAG, "MEDIA file NOT FOUND. mediaDir=${mediaDir.absolutePath} exists=${mediaDir.exists()}")
//
//                // lista algunos archivos para ver mismatches
//                mediaDir.listFiles()
//                    ?.take(30)
//                    ?.forEach { f ->
//                        Log.d(TAG, "  mediaDir file='${f.name}' noExt='${f.nameWithoutExtension}'")
//                    }
//
//                return null
//            }
//
//            val ext = file.extension.lowercase()
//            val esImagen = ext in listOf("jpg", "jpeg", "png", "webp")
//            val esVideo = ext in listOf("mp4", "mkv", "avi", "mov", "webm")
//
//            Log.d(TAG, "MEDIA file ext='$ext' esImagen=$esImagen esVideo=$esVideo size=${file.length()}")
//
//            if (!esImagen && !esVideo) return null
//
//            val uri = Uri.fromFile(file)
//            Log.d(TAG, "MEDIA resolved uri=$uri scheme=${uri.scheme} lastMod=${file.lastModified()}")
//
//            ItemLista(
//                id = itemKey,
//                nombre = base,
//                uri = uri,
//                esImagen = esImagen,
//                timestamp = file.lastModified()
//            )
//        }
//
//        ItemKey.isPicto(itemKey) -> {
//            val id = ItemKey.pictoId(itemKey)
//            Log.d(TAG, "PICTO itemKey='$itemKey' id='$id'")
//
//            val row = db.pictogramDao().getPictoUiEnabledById(id)
//            Log.d(TAG, "PICTO rowFound=${row != null}")
//
//            val item = row?.toItemLista(timestamp = 0L)
//            val out = item?.copy(id = itemKey)
//
//            Log.d(TAG, "PICTO resolved item=${out != null} uri=${out?.uri} scheme=${out?.uri?.scheme}")
//            out
//        }
//
//        else -> {
//            Log.d(TAG, "UNKNOWN key format itemKey='$itemKey'")
//            null
//        }
//    }
//}


    suspend fun resolveItemKeyToItemLista(
        context: Context,
        itemKey: String
    ): ItemLista? {

        val TAG = "RESOLVE_KEY"
        val db = AppDatabase.getDatabase(context)

      // Log.d(TAG, "resolve start itemKey='$itemKey'")

        return when {

            ItemKey.isMedia(itemKey) -> {

                val value = itemKey.removePrefix(ItemKey.MED_PREFIX).trim()

              //  Log.d(TAG, "MEDIA itemKey='$itemKey' value='$value'")

                // 1) Intentar formato nuevo: MED:mediaId
              //  val mediaById = db.mediaDao().getById(value)

                val userId = SessionManager(context).getCurrentUserId()
                val mediaById =
                    db.mediaDao().getByIdForUser(
                        mediaId = value,
                        userId = userId
                    )

                if (mediaById != null && !mediaById.isDeleted) {

//                    Log.d(
//                        TAG,
//                        "MEDIA resolved by UUID displayName='${mediaById.displayName}' uri='${mediaById.localUri}'"
//                    )

                    return ItemLista(
                        id = itemKey,
                        nombre = mediaById.displayName,
                        uri = Uri.parse(mediaById.localUri),
                        esImagen = mediaById.mediaType == "image",
                        timestamp = mediaById.createdAt
                    )
                }

                // 2) Fallback formato viejo: MED:nombre
              //  Log.d(TAG, "MEDIA not found by UUID, trying legacy name")

                //val mediaByName = db.mediaDao().getActiveByDisplayName(value)
                val mediaByName =
                    db.mediaDao().getActiveByDisplayNameForUser(
                        displayName = value,
                        userId = userId
                    )

                if (mediaByName != null) {

                  //  Log.d(
//                        TAG,
//                        "MEDIA resolved by displayName displayName='${mediaByName.displayName}' uri='${mediaByName.localUri}'"
//                    )

                    return ItemLista(
                        id = itemKey,
                        nombre = mediaByName.displayName,
                        uri = Uri.parse(mediaByName.localUri),
                        esImagen = mediaByName.mediaType == "image",
                        timestamp = mediaByName.createdAt
                    )
                }

                // 3) Último fallback: buscar archivo por nombre como antes
                val file = findMediaFileByBaseName(context, value)

//                Log.d(
//                    TAG,
//                    "MEDIA fallback file search value='$value' file=${file?.absolutePath} exists=${file?.exists()}"
//                )

                if (file == null) {
                    return null
                }

                val ext = file.extension.lowercase()
                val esImagen = ext in listOf("jpg", "jpeg", "png", "webp")
                val esVideo = ext in listOf("mp4", "mkv", "avi", "mov", "webm")

                if (!esImagen && !esVideo) {
                    return null
                }

                ItemLista(
                    id = itemKey,
                    nombre = value,
                    uri = Uri.fromFile(file),
                    esImagen = esImagen,
                    timestamp = file.lastModified()
                )
            }

            ItemKey.isPicto(itemKey) -> {

                val id = ItemKey.pictoId(itemKey)

                //Log.d(TAG, "PICTO itemKey='$itemKey' id='$id'")

                val row = db.pictogramDao().getPictoUiEnabledById(id)

                val item = row?.toItemLista(timestamp = 0L)

                item?.copy(id = itemKey)
            }

            else -> {
                Log.d(TAG, "UNKNOWN key format itemKey='$itemKey'")
                null
            }
        }
    }


    suspend fun resolveItemKeyToItemListaAllowDisabled(
        context: Context,
        itemKey: String
    ): ItemLista? {
        val db = AppDatabase.getDatabase(context)

        return when {
            ItemKey.isPicto(itemKey) -> {
                val id = ItemKey.pictoId(itemKey)
                val row = db.pictogramDao().getPictoUiById_NoPackFilter(id)
                val item = row?.toItemLista(timestamp = 0L)
                item?.copy(id = itemKey) // mantiene "PIC:xxx"
            }

            ItemKey.isMedia(itemKey) -> {
                // para media, dejás igual que tu resolver normal
                resolveItemKeyToItemLista(context, itemKey)
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
            ?.firstOrNull { file ->
                file.nameWithoutExtension.trim().equals(base.trim(), ignoreCase = true)
            }
    }