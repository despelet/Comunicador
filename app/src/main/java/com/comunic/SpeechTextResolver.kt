package com.comunic

import com.comunic.data.db.AppDatabase

import android.util.Log

object SpeechTextResolver {

    suspend fun resolve(
        db: AppDatabase,
        itemKeyOrId: String
    ): String {

        val raw = itemKeyOrId.trim()
        Log.d( "MEDIA_MIGRATION", "resolve() recibido = $raw")

        return when {
            raw.startsWith(ItemKey.MED_PREFIX) -> {

                val value = raw.removePrefix(ItemKey.MED_PREFIX).trim()

                Log.d("MEDIA_MIGRATION","MED detectado -> $value" )
                val mediaById = db.mediaDao().getById(value)

                if (mediaById != null) {
                    Log.d("MEDIA_MIGRATION","Encontrado por UUID -> ${mediaById.displayName}" )
                    mediaById.displayName.trim()
                } else {
                    Log.d("MEDIA_MIGRATION", "No encontrado por UUID, buscando por nombre")
                    val mediaByName =db.mediaDao().getActiveByDisplayName(value)
                    if (mediaByName != null) {
                        Log.d("MEDIA_MIGRATION","Encontrado por nombre -> ${mediaByName.displayName}" )
                    } else {
                        Log.d("MEDIA_MIGRATION","No encontrado en Room, fallback -> $value")
                    }
                    mediaByName?.displayName?.trim() ?: value
                }
            }

            raw.startsWith(ItemKey.PIC_PREFIX) -> {
                val pictogramId =raw.removePrefix(ItemKey.PIC_PREFIX).trim()
                Log.d( "MEDIA_MIGRATION","PIC detectado -> $pictogramId")
                val picto =db.pictogramDao().getPictoById( pictogramId)
                picto?.label?.trim()  ?: pictogramId
            }
            else -> {
                Log.d( "MEDIA_MIGRATION","Formato legacy -> $raw" )
                val picto = db.pictogramDao().getPictoById(raw)

                picto?.label?.trim() ?: raw
            }
        }
    }
}

//
//object SpeechTextResolver {
//    suspend fun resolve(db: AppDatabase, itemKeyOrId: String): String {
//        val raw = itemKeyOrId.trim()
//        return when {
//            raw.startsWith("MED:") -> raw.removePrefix("MED:").trim()
//            raw.startsWith("PIC:") -> {
//                val id = raw.removePrefix("PIC:").trim()
//                val picto = db.pictogramDao().getPictoById(id)
//                (picto?.label ?: id).trim()
//            }
//            else -> {
//                val picto = db.pictogramDao().getPictoById(raw)
//                (picto?.label ?: raw).trim()
//            }
//        }
//    }
//}