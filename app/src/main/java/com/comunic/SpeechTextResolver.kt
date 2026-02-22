package com.comunic

import com.comunic.data.db.AppDatabase

object SpeechTextResolver {
    suspend fun resolve(db: AppDatabase, itemKeyOrId: String): String {
        val raw = itemKeyOrId.trim()
        return when {
            raw.startsWith("MED:") -> raw.removePrefix("MED:").trim()
            raw.startsWith("PIC:") -> {
                val id = raw.removePrefix("PIC:").trim()
                val picto = db.pictogramDao().getPictoById(id)
                (picto?.label ?: id).trim()
            }
            else -> {
                val picto = db.pictogramDao().getPictoById(raw)
                (picto?.label ?: raw).trim()
            }
        }
    }
}