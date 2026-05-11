package com.comunic.adapters

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.comunic.ItemLista
import com.comunic.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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

    fun exportarElementosComoZip( fragment: Fragment, elementos: List<ItemLista>, nombreZip: String   ) {
        if (elementos.isEmpty()) {
            Toast.makeText(
                fragment.requireContext(),
                "No seleccionaste elementos",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val zipFile = File(
            fragment.requireContext().cacheDir,
            nombreZip
        )

        try {

            ZipOutputStream(
                BufferedOutputStream(
                    FileOutputStream(zipFile)
                )
            ).use { zos ->

                for (item in elementos) {

                    val inputStream = fragment.requireContext()
                        .contentResolver
                        .openInputStream(item.uri)
                        ?: continue

                    var extension = File(item.uri.path ?: "")
                        .extension
                        .lowercase(Locale.ROOT)

                    if (extension.isBlank()) {

                        val mimeType = fragment.requireContext()
                            .contentResolver
                            .getType(item.uri)

                        extension = MimeTypeMap
                            .getSingleton()
                            .getExtensionFromMimeType(mimeType)
                            ?.lowercase(Locale.ROOT)
                            ?: ""
                    }

                    val fileName =
                        if (
                            extension.isNotBlank() &&
                            !item.nombre.endsWith(".$extension")
                        ) {
                            "${item.nombre}.$extension"
                        } else {
                            item.nombre
                        }

                    val entry = ZipEntry(fileName)

                    zos.putNextEntry(entry)

                    inputStream.copyTo(zos)

                    zos.closeEntry()
                    inputStream.close()
                }
            }

            val uriZip = FileProvider.getUriForFile(
                fragment.requireContext(),
                "com.comunic.fileprovider",
                zipFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uriZip)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            fragment.startActivity(
                Intent.createChooser(intent, "Compartir ZIP")
            )

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                fragment.requireContext(),
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
}