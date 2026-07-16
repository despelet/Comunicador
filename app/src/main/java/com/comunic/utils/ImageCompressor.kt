package com.comunic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageCompressor {

    /**
     * Guarda una versión optimizada de una imagen.
     *
     * - Corrige orientación EXIF
     * - Reduce resolución
     * - Comprime JPEG
     *
     * Imagen original
     *         ↓
     * Lee EXIF
     *         ↓
     * Calcula tamaño
     *         ↓
     * Decodifica reducida
     *         ↓
     * Corrige rotación
     *         ↓
     * JPEG 85%
     *         ↓
     * Guarda archivo
     *
     *
     */
    fun saveOptimizedImage(context: Context, sourceUri: Uri, destination: File, maxSize: Int = 1024, quality: Int = 85): Uri {

        val bitmap = decodeScaledBitmap(context, sourceUri, maxSize) ?: throw IllegalArgumentException("No se pudo leer la imagen")

        val rotated = rotateIfRequired(context, sourceUri, bitmap)

        FileOutputStream(destination).use { out ->
            rotated.compress(
                Bitmap.CompressFormat.JPEG,
                quality,
                out
            )
        }

        if (rotated != bitmap) {
            bitmap.recycle()
        }

        rotated.recycle()

        return Uri.fromFile(destination)
    }

    /**
     * Decodifica la imagen ya reducida para no consumir memoria innecesaria.
     */
    private fun decodeScaledBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap? {

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        val width = bounds.outWidth
        val height = bounds.outHeight

        var sample = 1
        while (
            width / sample > maxSize * 2 ||
            height / sample > maxSize * 2
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }

        val bitmap = context.contentResolver
            .openInputStream(uri)
            ?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            ?: return null

        val scale = maxSize.toFloat() / max(bitmap.width, bitmap.height)

        if (scale >= 1f) { return bitmap }

        val newWidth = (bitmap.width * scale).roundToInt()
        val newHeight = (bitmap.height * scale).roundToInt()
        val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        bitmap.recycle()
        return resized
    }

    /**
     * Corrige la orientación de la foto según EXIF.
     */
    private fun rotateIfRequired(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {

        val input = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(input)

        input.close()

        val rotation = when (
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {

            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f

            else -> return bitmap
        }

        val matrix = android.graphics.Matrix()

        matrix.postRotate(rotation)

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    // funcion para optimizar una imagen existente en el almacenamiento del dispositivo
    // asi quedan optimizadas todas, no solo las nuevas
    fun optimizeExistingImage(context: Context, imageFile: File, maxSize: Int = 1024, quality: Int = 85
    ): Boolean {
        return try {

            val sourceUri = Uri.fromFile(imageFile)

            val bitmap = decodeScaledBitmap(context, sourceUri, maxSize) ?: return false

            val rotated = rotateIfRequired(context, sourceUri, bitmap)

            val tempFile = File(imageFile.parentFile, imageFile.name + ".tmp")

            FileOutputStream(tempFile).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, quality, out)

            }

            if (rotated != bitmap) {
                bitmap.recycle()
            }

            rotated.recycle()

            if (!imageFile.delete()) {
                tempFile.delete()
                return false
            }

            if (!tempFile.renameTo(imageFile)) {
                tempFile.delete()
                return false
            }

            true

        } catch (e: Exception) {

            e.printStackTrace()
            false

        }

    }


}