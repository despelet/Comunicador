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
        if (installed != null && installed.version >= 2) return

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
        // 1b) Crear categoría Comida
        db.categoryDao().upsert(
            CategoryEntity(
                categoryId = "basic_food",
                name = "Comida",
                orderIndex = 1, // poné 1 para que quede debajo de Básico (0)
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

        // Lista Pack Básico v2
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
        // Lista Comida (Pack Básico v2)
        val foodItems = listOf(
            Triple("food_chocolate", "chocolate", "chocolate.png"),
            Triple("food_ensalada", "ensalada", "ensalada.png"),
            Triple("food_huevo", "huevo", "huevo.png"),
            Triple("food_hamburguesa", "hamburguesa", "hamburguesa.png"),
            Triple("food_pan", "pan", "pan.png"),
            Triple("food_sopa", "sopa", "sopa.png"),
            Triple("food_torta", "torta", "torta.png"),
            Triple("food_yogurt", "yogurt", "yogurt.png")
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
                placementId = "basic_core__$id",   // ✅ estable
                categoryId = "basic_core",
                pictogramId = id,
                orderIndex = index
            )
        }

        db.pictogramDao().insertPlacements(placements)

        // =====================
// COMIDA: copiar imágenes + insertar pictos
// =====================
        val foodPictos = foodItems.map { (id, label, filename) ->
            val outFile = File(baseDir, filename)
            if (!outFile.exists()) {
                copyAssetToFile("packs/basic/images/$filename", outFile)
            }

            PictogramEntity(
                pictogramId = id,
                packId = "basic",
                baseLabel = label,
                baseImageUri = outFile.absolutePath, // consistente con lo que ya hacés
                createdAt = now
            )
        }
        db.pictogramDao().insertAll(foodPictos)

// placements dentro de la categoría "basic_food"
        val foodPlacements = foodItems.mapIndexed { index, (id, _, _) ->
            CategoryItemEntity(
                placementId = "basic_food__$id",   // ✅ estable
                categoryId = "basic_food",
                pictogramId = id,
                orderIndex = index
            )
        }
        db.pictogramDao().insertPlacements(foodPlacements)

        db.installedPackDao().upsert(
            InstalledPackEntity(packId = "basic", version = 2, installedAt = now)
        )
    }
}
