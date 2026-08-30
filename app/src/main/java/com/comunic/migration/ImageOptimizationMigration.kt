package com.comunic.migration

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.comunic.utils.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


// clase para optimizar imágenes existentes en la carpeta /files/media
// asi quedarian optimizadas las nuevas y las viejas, las viejas solo por unica vez

/*
Busca todas las imágenes en files/media.
Ignora las menores de 300 KB.
Optimiza las grandes.
Calcula cuánto espacio se liberó.
Devuelve true solo si no hubo errores.
*/


class ImageOptimizationMigration(
    private val context: Context
) {

    suspend fun optimizeImages(): Boolean = withContext(Dispatchers.IO) {

        try {

            val mediaDir = File(context.filesDir, "media")

            if (!mediaDir.exists()) {
                Log.d(TAG, "No existe carpeta media.")
                return@withContext true
            }

            val images = mediaDir.listFiles()
                ?.filter {
                    it.isFile &&
                            (
                                    it.extension.equals("jpg", true) ||
                                            it.extension.equals("jpeg", true) ||
                                            it.extension.equals("png", true) ||
                                            it.extension.equals("webp", true)
                                    )
                }
                ?: emptyList()

            var optimized = 0
            var skipped = 0
            var errors = 0

            var originalBytes = 0L
            var optimizedBytes = 0L

            images.forEach { file ->

                originalBytes += file.length()

                // Saltar imágenes que ya están optimizadas (menores a 1024px de ancho o alto)
                if (!needsOptimization(file)) {
                    skipped++
                    optimizedBytes += file.length()
                    return@forEach
                }

                val ok = ImageCompressor.optimizeExistingImage(
                    context = context,
                    imageFile = file
                )

                if (ok) {
                    optimized++
                    optimizedBytes += file.length()
                } else {
                    errors++
                    optimizedBytes += file.length()
                    Log.e(TAG, "No se pudo optimizar ${file.name}")
                }
            }

            val savedMB =
                (originalBytes - optimizedBytes) / 1024f / 1024f

            Log.d(
                TAG,
                """
                ========= MIGRACIÓN DE IMÁGENES =========
                Total imágenes : ${images.size}
                Optimizadas    : $optimized
                Omitidas       : $skipped
                Errores        : $errors
                Espacio liberado: ${"%.2f".format(savedMB)} MB
                ========================================
                """.trimIndent()
            )

            return@withContext errors == 0

        } catch (e: Exception) {

            Log.e(TAG, "Error general", e)

            return@withContext false
        }

    }

    private fun needsOptimization(file: File, maxSize: Int = 1024): Boolean {

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeFile(
            file.absolutePath,
            options
        )

        val width = options.outWidth
        val height = options.outHeight

        if (width <= 0 || height <= 0) {
            return false
        }

        return maxOf(width, height) > maxSize
    }

    companion object {
        private const val TAG = "IMAGE_MIGRATION"
    }
}