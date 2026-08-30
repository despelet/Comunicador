package com.comunic.data.db

import android.content.Context
import android.net.Uri
import com.comunic.data.entity.MediaEntity
import com.comunic.utils.FileHash
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

            val esImagen =
                file.extension.equals("jpg", ignoreCase = true)

            val esVideo =
                file.extension.equals("mp4", ignoreCase = true)

            if (!esImagen && !esVideo) {
                return@forEach
            }

            val displayName =
                file.nameWithoutExtension.trim()

            if (displayName.isBlank()) {
                return@forEach
            }

            // -------------------------------------------------
            // IDENTIFICAR EL ARCHIVO POR SU URI, NO POR EL NOMBRE
            // -------------------------------------------------
            //
            // El nombre visible puede cambiar mediante edición.
            // La URI identifica el archivo físico que ya está
            // registrado en Room.
            //
            val localUri =
                Uri.fromFile(file).toString()

            val existente =
                db.mediaDao().getAnyByLocalUri(localUri)

            if (existente != null) {

                // Si el registro existe pero todavía no tiene
                // hash, lo calculamos una sola vez.
                if (existente.contentHash.isBlank()) {

                    db.mediaDao().updateContentHash(
                        existente.mediaId,
                        FileHash.sha256(file)
                    )
                }

                return@forEach
            }

            // -------------------------------------------------
            // ARCHIVO NUEVO
            // -------------------------------------------------

            val now =
                System.currentTimeMillis()

            val media = MediaEntity(
                mediaId = UUID.randomUUID().toString(),
                displayName = displayName,
                localUri = localUri,
                mediaType = if (esImagen) {
                    "image"
                } else {
                    "video"
                },
                createdAt =
                file.lastModified()
                    .takeIf { it > 0L }
                    ?: now,
                updatedAt = now,
                isDeleted = false
            )

            db.mediaDao().upsert(media)
        }
    }
}