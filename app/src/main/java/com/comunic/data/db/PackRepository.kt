package com.comunic.data.db

import android.content.Context
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.entity.InstalledPackEntity
import com.comunic.data.entity.PictogramEntity
import java.io.File
import java.util.UUID

class PackRepository(
    private val context: Context,
    private val db: AppDatabase
) {

    suspend fun ensureBasicPackInstalled() {
        val installed = db.installedPackDao().get("basic")
        if (installed != null && installed.version >= 1) return

        val now = System.currentTimeMillis()

        // 1) Crear categoría Básico (id estable)
        db.categoryDao().upsert(
            CategoryEntity(
                categoryId = "basic_core",
                name = "Básico",
                orderIndex = 0,
                createdAt = now
            )
        )

        // 2) Copiar imágenes desde assets a filesDir (una vez)
        val baseDir = File(context.filesDir, "packs/basic/images")
        baseDir.mkdirs()

        fun copyAssetToFile(assetPath: String, outFile: File) {
            context.assets.open(assetPath).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        // Lista Pack Básico v1
        val basicItems = listOf(
            Triple("basic_yes", "Sí", "si.png"),
            Triple("basic_no", "No", "no.png"),
            Triple("basic_hello", "Hola", "hola.png"),
            Triple("basic_bye", "Chau", "chau.png"),
            Triple("basic_help", "Ayuda", "ayuda.png"),
            Triple("basic_stop", "Basta", "basta.png"),
            Triple("basic_good", "Bien", "bien.png"),
            Triple("basic_bad", "Mal", "mal.png"),
            Triple("basic_want", "Quiero", "quiero.png")
        )

        val pictos = basicItems.map { (id, label, filename) ->
            val outFile = File(baseDir, filename)
            if (!outFile.exists()) {
                copyAssetToFile("packs/basic/images/$filename", outFile)
            }
            PictogramEntity(
                pictogramId = id,
                packId = "basic",
                baseLabel = label,
                baseImageUri = outFile.absolutePath, // o "file://${outFile.absolutePath}"
                createdAt = now
            )
        }

        db.pictogramDao().insertAll(pictos)

        val placements = basicItems.mapIndexed { index, (id, _, _) ->
            CategoryItemEntity(
                placementId = UUID.randomUUID().toString(),
                categoryId = "basic_core",
                pictogramId = id,
                orderIndex = index
            )
        }

        db.pictogramDao().insertPlacements(placements)

        db.installedPackDao().upsert(
            InstalledPackEntity(packId = "basic", version = 1, installedAt = now)
        )
    }
}
