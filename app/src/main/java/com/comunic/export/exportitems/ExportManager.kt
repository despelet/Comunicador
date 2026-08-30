package com.comunic.export.exportitems

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.R
import com.comunic.data.db.AppDatabase
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.mappers.resolveItemKeyToItemLista
import com.comunic.export.exportlista.ExportCategoryItem
import com.comunic.export.exportlista.ExportCategoryMetadata
import com.comunic.export.exportlista.ExportMediaItem
import com.comunic.export.exportlista.ExportMediaMetadata
import com.comunic.session.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// manejo de exportacion de elementos desde cualquier fragment

object ExportManager {

    fun exportarElementos( fragment: Fragment,elementos: List<ItemLista>  ) {
        if (elementos.isEmpty()) {
            Toast.makeText(
                fragment.requireContext(),
                "No seleccionaste elementos",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val uris = ArrayList<Uri>()

        for (item in elementos) {
            val uri = if (item.uri.scheme == "content") {
                item.uri
            } else {
                val file = File(item.uri.path!!)
                FileProvider.getUriForFile(
                    fragment.requireContext(),
                    "com.comunic.fileprovider",
                    file
                )
            }

            uris.add(uri)
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        fragment.startActivity(
            Intent.createChooser(intent, "Compartir archivos")
        )
    }


fun exportarElementosComoZip(
    fragment: Fragment,
    elementos: List<ItemLista>,
    nombreZip: String
) {
    if (elementos.isEmpty()) {
        Toast.makeText(
            fragment.requireContext(),
            "No seleccionaste elementos",
            Toast.LENGTH_SHORT
        ).show()
        return
    }

    val context = fragment.requireContext()

    val zipFile = File(
        context.cacheDir,
        nombreZip
    )

    try {

        val gson = Gson()

        /*
         * ============================================================
         * 1. Construir metadata de los elementos
         * ============================================================
         */

        val exportItems = elementos.mapNotNull { item ->

            if (!ItemKey.isMedia(item.id)) {
                null
            } else {

                val mediaId = ItemKey.mediaId(item.id)

                ExportMediaItem(
                    mediaId = mediaId,
                    displayName = item.nombre,
                    mediaType = if (item.esImagen) "image" else "video"
                )
            }
        }

        val metadata = ExportMediaMetadata(
            version = 2,
            items = exportItems
        )

        val metadataJson = gson.toJson(metadata)

        /*
         * ============================================================
         * 2. Crear ZIP
         * ============================================================
         */

        ZipOutputStream(
            BufferedOutputStream(
                FileOutputStream(zipFile)
            )
        ).use { zos ->

            /*
             * --------------------------------------------------------
             * metadata.json
             * --------------------------------------------------------
             */

            val metadataEntry = ZipEntry("metadata.json")

            zos.putNextEntry(metadataEntry)
            zos.write(metadataJson.toByteArray())
            zos.closeEntry()

            /*
             * --------------------------------------------------------
             * Archivos multimedia
             * --------------------------------------------------------
             */

            for (item in elementos) {

                if (!ItemKey.isMedia(item.id)) {
                    continue
                }

                val mediaId = ItemKey.mediaId(item.id)

                val inputStream =
                    context.contentResolver
                        .openInputStream(item.uri)
                        ?: continue

                var extension = File(
                    item.uri.path ?: ""
                )
                    .extension
                    .lowercase(Locale.ROOT)

                /*
                 * Si la URI no tiene extensión, intentamos obtenerla
                 * mediante el MIME type.
                 */
                if (extension.isBlank()) {

                    val mimeType =
                        context.contentResolver
                            .getType(item.uri)

                    extension =
                        MimeTypeMap
                            .getSingleton()
                            .getExtensionFromMimeType(mimeType)
                            ?.lowercase(Locale.ROOT)
                            ?: ""
                }

                if (extension.isBlank()) {
                    inputStream.close()
                    continue
                }

                /*
                 * El archivo se identifica por UUID, NO por displayName.
                 *
                 * Ejemplo:
                 * MED_550e8400-e29b-41d4-a716-446655440000.jpg
                 */
                val fileName =
                    "MED_${mediaId}.$extension"

                val mediaEntry =
                    ZipEntry("media/$fileName")

                zos.putNextEntry(mediaEntry)

                inputStream.copyTo(zos)

                zos.closeEntry()
                inputStream.close()
            }
        }

        /*
         * ============================================================
         * 3. Compartir ZIP
         * ============================================================
         */

        val uriZip =
            FileProvider.getUriForFile(
                context,
                "com.comunic.fileprovider",
                zipFile
            )

        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"

                putExtra(
                    Intent.EXTRA_STREAM,
                    uriZip
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        fragment.startActivity(
            Intent.createChooser(
                intent,
                "Compartir ZIP"
            )
        )

    } catch (e: Exception) {

        e.printStackTrace()

        Toast.makeText(
            context,
            "Error al crear ZIP",
            Toast.LENGTH_SHORT
        ).show()
    }
}

    fun pedirNombreZip(
        fragment: Fragment,
        onNombreListo: (String) -> Unit
    ) {

        val dialogView = fragment.layoutInflater.inflate(
            R.layout.exp_dialogo_nombre_zip,
            null
        )

        val editText =
            dialogView.findViewById<TextInputEditText>(
                R.id.editTextNombreZip
            )

        val btnContinuar =
            dialogView.findViewById<MaterialButton>(
                R.id.btnContinuar
            )

        val btnCancelar =
            dialogView.findViewById<MaterialButton>(
                R.id.btnCancelar
            )

        val fecha = SimpleDateFormat(
            "yyyy_MM_dd",
            Locale.getDefault()
        ).format(Date())

        editText.setText("bicom_$fecha")

        val dialog = MaterialAlertDialogBuilder(
            fragment.requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setView(dialogView)
            .create()

        btnContinuar.setOnClickListener {

            val texto = editText.text
                ?.toString()
                ?.trim()
                .orEmpty()

            val nombreBase =
                if (texto.isBlank()) {
                    "bicom_$fecha"
                } else {
                    sanitizarNombreArchivo(texto)
                }

            val nombreFinal =
                if (nombreBase.endsWith(".zip")) {
                    nombreBase
                } else {
                    "$nombreBase.zip"
                }

            dialog.dismiss()

            onNombreListo(nombreFinal)
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    fun sanitizarNombreArchivo(nombre: String): String {
        return nombre.replace(
            Regex("[^a-zA-Z0-9._-]"),
            "_"
        )
    }

    suspend fun exportarListasComoZip(
        fragment: Fragment,
        categorias: List<CategoryEntity>
    ) {
        val context = fragment.requireContext()
        val db = AppDatabase.getDatabase(context)

        val userId =
            SessionManager(context)
                .getCurrentUserId()

        val zipFile = File(
            context.cacheDir,
            "listas_export.zip"
        )

        val gson = Gson()

        ZipOutputStream(
            BufferedOutputStream(
                FileOutputStream(zipFile)
            )
        ).use { zos ->

            for (categoria in categorias) {

                /*
                 * ============================================================
                 * 1. OBTENER ELEMENTOS DE LA CATEGORÍA
                 * ============================================================
                 */

                val itemKeys =
                    db.categoryDao()
                        .getItemKeysForCategory(
                            categoria.categoryId,
                            userId
                        )

                /*
                 * Resolver cada itemKey una sola vez.
                 *
                 * Esto nos permite obtener:
                 * - displayName
                 * - mediaType
                 * - URI
                 *
                 * y reutilizar esa información después.
                 */

                val resolvedItems =
                    itemKeys.mapNotNull { key ->

                        val item =
                            resolveItemKeyToItemLista(
                                context,
                                key
                            )

                        if (item != null) {
                            key to item
                        } else {
                            null
                        }
                    }.toMap()

                /*
                 * ============================================================
                 * 2. CONSTRUIR METADATA
                 * ============================================================
                 */

                val exportItems =
                    itemKeys.mapIndexedNotNull { index, key ->

                        /*
                         * Los pictogramas no tienen MediaEntity,
                         * por lo que no tienen displayName/mediaType
                         * multimedia.
                         *
                         * Los conservamos igualmente.
                         */

                        val item =
                            resolvedItems[key]

                        ExportCategoryItem(
                            itemKey = key,
                            orderIndex = index,
                            displayName = item?.nombre,
                            mediaType =
                            if (item == null) {
                                null
                            } else if (item.esImagen) {
                                "image"
                            } else {
                                "video"
                            }
                        )
                    }

                val metadata =
                    ExportCategoryMetadata(
                        categoryId = categoria.categoryId,
                        name = categoria.name,
                        createdAt = categoria.createdAt,
                        items = exportItems,
                        version = 2
                    )

                val metadataJson =
                    gson.toJson(metadata)

                val safeFolderName =
                    sanitizarNombreArchivo(
                        categoria.name
                    )

                /*
                 * ============================================================
                 * 3. GUARDAR metadata.json
                 * ============================================================
                 */

                val metadataEntry =
                    ZipEntry(
                        "$safeFolderName/metadata.json"
                    )

                zos.putNextEntry(metadataEntry)

                zos.write(
                    metadataJson.toByteArray()
                )

                zos.closeEntry()

                /*
                 * ============================================================
                 * 4. GUARDAR ARCHIVOS MULTIMEDIA
                 * ============================================================
                 */

                for (itemKey in itemKeys) {

                    /*
                     * Solo los MED: tienen archivo multimedia.
                     */
                    if (!ItemKey.isMedia(itemKey)) {
                        continue
                    }

                    val item =
                        resolvedItems[itemKey]
                            ?: continue

                    val inputStream =
                        context.contentResolver
                            .openInputStream(item.uri)
                            ?: continue

                    var extension =
                        File(
                            item.uri.path ?: ""
                        )
                            .extension
                            .lowercase(Locale.ROOT)

                    /*
                     * Si la URI no tiene extensión,
                     * intentar obtenerla mediante MIME type.
                     */
                    if (extension.isBlank()) {

                        val mimeType =
                            context.contentResolver
                                .getType(item.uri)

                        extension =
                            MimeTypeMap
                                .getSingleton()
                                .getExtensionFromMimeType(
                                    mimeType
                                )
                                ?.lowercase(Locale.ROOT)
                                ?: ""
                    }

                    if (extension.isBlank()) {
                        inputStream.close()
                        continue
                    }

                    /*
                     * El archivo conserva el UUID.
                     *
                     * MED:UUID
                     *      ↓
                     * MED_UUID.ext
                     */

                    val fileName =
                        itemKey
                            .replace(":", "_") +
                                ".$extension"

                    val mediaEntry =
                        ZipEntry(
                            "$safeFolderName/media/$fileName"
                        )

                    zos.putNextEntry(
                        mediaEntry
                    )

                    inputStream.copyTo(zos)

                    zos.closeEntry()
                    inputStream.close()
                }
            }
        }

        /*
         * ================================================================
         * 5. COMPARTIR ZIP
         * ================================================================
         */

        val uriZip =
            FileProvider.getUriForFile(
                context,
                "com.comunic.fileprovider",
                zipFile
            )

        val intent =
            Intent(Intent.ACTION_SEND).apply {

                type = "application/zip"

                putExtra(
                    Intent.EXTRA_STREAM,
                    uriZip
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        fragment.startActivity(
            Intent.createChooser(
                intent,
                "Compartir ZIP"
            )
        )
    }
}