package com.comunic.migration

import android.content.Context
import android.util.Log
import com.comunic.ItemKey
import com.comunic.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class LegacyMediaKeyMigrationRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getDatabase(context)
) {

    suspend fun migrateLegacyMediaKeys() {
        withContext(Dispatchers.IO) {

            val placements =
                db.categoryDao()
                    .getActiveMediaPlacements()

            var migrados = 0
            var yaUuid = 0
            var noEncontrados = 0

            placements.forEach { placement ->

                val itemKey =
                    placement.itemKey

                if (!ItemKey.isMedia(itemKey)) {
                    return@forEach
                }

                val value =
                    ItemKey.mediaBase(itemKey)
                        .trim()

                if (isUuid(value)) {
                    yaUuid++
                    return@forEach
                }

                val media =
                    db.mediaDao()
                        .getActiveByDisplayName(value)

                if (media == null) {
                    Log.w(
                        TAG,
                        "No se encontró media para itemKey=$itemKey placementId=${placement.placementId}"
                    )
                    noEncontrados++
                    return@forEach
                }

                val newItemKey =
                    ItemKey.media(media.mediaId)

                db.categoryDao()
                    .updatePlacementItemKey(
                        placementId = placement.placementId,
                        newItemKey = newItemKey,
                        updatedAt = System.currentTimeMillis()
                    )

                Log.d(
                    TAG,
                    "Migrado $itemKey -> $newItemKey"
                )

                migrados++
            }

            Log.d(
                TAG,
                "Resultado: migrados=$migrados yaUuid=$yaUuid noEncontrados=$noEncontrados total=${placements.size}"
            )
        }
    }

    private fun isUuid(value: String): Boolean {
        return try {
            UUID.fromString(value)
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG =
            "LEGACY_MEDIA_KEY_MIGRATION"
    }
}