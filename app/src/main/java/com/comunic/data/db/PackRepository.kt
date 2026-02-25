package com.comunic.data.db

import android.content.Context
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.entity.InstalledPackEntity
import com.comunic.data.entity.PictogramEntity
import java.io.File
import java.util.UUID
import com.comunic.ItemKey

class PackRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    suspend fun ensureBasicPackInstalled() {
        val now = System.currentTimeMillis()

        ensurePackRow("basic_core", now)
        ensurePackRow("basic_food", now)

//        // ✅ 0) Asegurar fila installed_packs SIN pisar enabled
//        val installed = db.installedPackDao().get("basic")
//        if (installed == null) {
//            db.installedPackDao().upsert(
//                InstalledPackEntity(
//                    packId = "basic",
//                    version = 3,
//                    installedAt = now,
//                    enabled = true,   // primera vez sí
//                    isSystem = true
//                )
//            )
//        } else {
//            // NO tocar enabled: respetar lo que eligió el usuario
//            db.installedPackDao().upsert(
//                installed.copy(
//                    isSystem = true
//                )
//            )
//        }

        // ✅ 1) REPAIR SIEMPRE: categorías del pack basic deben ser system + packId=basic
        db.categoryDao().upsert(
            CategoryEntity(
                categoryId = "basic_core",
                name = "Básico",
                orderIndex = 0,
                createdAt = now,
                packId = "basic_core",
                isSystem = true,
                isDeleted = false
            )
        )

        db.categoryDao().upsert(
            CategoryEntity(
                categoryId = "basic_food",
                name = "Comida",
                orderIndex = 1,
                createdAt = now,
                packId = "basic_food",
                isSystem = true,
                isDeleted = false
            )
        )

        // ✅ 2) Si ya está instalado en versión 3+, acá podés cortar para no reinsertar pictos/placements
        val installed2 = db.installedPackDao().get("basic")
        if (installed2 != null && installed2.version >= 3) return

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
                packId = "basic_core",   // 👈 antes "basic"
                baseLabel = label,
                baseImageUri = outFile.absolutePath,
                createdAt = now
            )
        }

        db.pictogramDao().insertAll(pictos)

        val placements = basicItems.mapIndexed { index, (id, _, _) ->
            CategoryItemEntity(
                placementId = "basic_core__$id",   // ✅ estable
                categoryId = "basic_core",
//                pictogramId = id,
                itemKey = ItemKey.picto(id),
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
                packId = "basic_food",   // 👈 antes "basic"
                baseLabel = label,
                baseImageUri = outFile.absolutePath,
                createdAt = now
            )
        }
        db.pictogramDao().insertAll(foodPictos)

// placements dentro de la categoría "basic_food"
        val foodPlacements = foodItems.mapIndexed { index, (id, _, _) ->
            CategoryItemEntity(
                placementId = "basic_food__$id",   // ✅ estable
                categoryId = "basic_food",
                //pictogramId = id,
                itemKey = ItemKey.picto(id),
                orderIndex = index
            )
        }
        db.pictogramDao().insertPlacements(foodPlacements)

//        db.installedPackDao().upsert(
//            InstalledPackEntity(
//                packId = "basic",
//                version = 3,
//                installedAt = installed?.installedAt ?: now,
//                enabled = installed?.enabled ?: true,  // ✅ respetar
//                isSystem = true
//            )
//        )
    }

    private suspend fun ensurePackRow(packId: String, now: Long) {
        val dao = db.installedPackDao()
        val existing = dao.get(packId)
        if (existing == null) {
            dao.upsert(
                InstalledPackEntity(
                    packId = packId,
                    version = 1,
                    installedAt = now,
                    enabled = true,
                    isSystem = true
                )
            )
        } else {
            dao.upsert(existing.copy(isSystem = true)) // no lo fuerces a enabled=true acá
        }
    }
}