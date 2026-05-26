package com.comunic.data.db

import android.content.Context
import android.net.Uri
import com.comunic.data.entity.MediaEntity
import java.io.File
import java.util.UUID

class MediaRepository(
    private val context: Context,
    private val db: AppDatabase
) {

    suspend fun ensureLocalMediaIndexed() {
        val mediaDir = File(context.filesDir, "media")

        if (!mediaDir.exists()) {
            mediaDir.mkdirs()
            return
        }

        mediaDir.listFiles()?.forEach { file ->

            val esImagen = file.extension.equals("jpg", ignoreCase = true)
            val esVideo = file.extension.equals("mp4", ignoreCase = true)

            if (!esImagen && !esVideo) {
                return@forEach
            }

            val displayName = file.nameWithoutExtension.trim()

            if (displayName.isBlank()) {
                return@forEach
            }

//            val existente = db.mediaDao()
//                .getActiveByDisplayName(displayName)
            val existente = db.mediaDao()
                .getAnyByDisplayName(displayName)

            if (existente != null) {
                return@forEach
            }

            val now = System.currentTimeMillis()

            val media = MediaEntity(
                mediaId = UUID.randomUUID().toString(),
                displayName = displayName,
                localUri = Uri.fromFile(file).toString(),
                mediaType = if (esImagen) "image" else "video",
                createdAt = file.lastModified().takeIf { it > 0L } ?: now,
                updatedAt = now,
                isDeleted = false
            )

            db.mediaDao().upsert(media)
        }
    }
}