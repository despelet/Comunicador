package com.comunic

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

/*
    crea una carpeta solo la primera vez y guarda el id en shared preferences
    reutiliza la misma carpeta en futuras subidas
    muestra logs en logcat para depuracion
* */

class DriveServiceHelper(private val context: Context, val driveService: Drive) {

    companion object {
        private const val PREFS_NAME = "DrivePreferences"
        private const val KEY_FOLDER_ID = "folderId"
    }

    fun uploadFile(fileUri: Uri, fileName: String, mimeType: String): Task<String> {
        return Tasks.call(Executors.newSingleThreadExecutor()) {
            val folderId = getOrCreateFolder("BiCom - carpeta de imágenes")  // Nombre de la carpeta en Drive

            val fileMetadata = File().apply {
                name = fileName
                parents = listOf(folderId)
            }

            val inputStream = context.contentResolver.openInputStream(fileUri) ?: throw Exception("No se pudo abrir el archivo")
            val fileContent = InputStreamContent(mimeType, inputStream)

            val file = driveService.files().create(fileMetadata, fileContent)
                .setFields("id")
                .execute()

            file.id
        }
    }

    private fun getOrCreateFolder(folderName: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedFolderId = prefs.getString(KEY_FOLDER_ID, null)

        if (savedFolderId != null) {
            Log.d("GoogleDrive", "Usando carpeta existente: $savedFolderId")
            return savedFolderId
        }

        val folderMetadata = File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
        }

        val folder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()

        val folderId = folder.id
        prefs.edit().putString(KEY_FOLDER_ID, folderId).apply()

        Log.d("GoogleDrive", "Carpeta creada: $folderId")
        return folderId
    }

    // Función para cargar imágenes desde Drive
    fun loadImagesFromDrive(onComplete: (List<String>) -> Unit) {
        Tasks.call(Executors.newSingleThreadExecutor()) {
            val folderId = getOrCreateFolder("BiCom - carpeta de imágenes")
            val query = "mimeType = 'image/jpeg' and trashed = false and '$folderId' in parents"

            val request = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, mimeType)")

            val files = request.execute()?.files ?: emptyList()

            // Ordenar los archivos por timestamp en el nombre
            val sortedFiles = files.sortedBy { file ->
                val nameParts = file.name.split("_")
                nameParts.getOrNull(1)?.replace(".jpg", "")?.toLongOrNull() ?: Long.MAX_VALUE
            }

            // Descargamos cada archivo
            val fileIds = mutableListOf<String>()
            for (file in sortedFiles) {
                Log.d("GoogleDrive", "Imagen en Drive: ${file.name}, ID: ${file.id}")
                downloadFileFromDrive(file.id)
                fileIds.add(file.id)
            }

            // ✅ ACTUALIZAR UI en el hilo principal
            Handler(Looper.getMainLooper()).post {
                onComplete(fileIds)  // Llamamos a la función callback con los IDs de los archivos
            }
        }
    }

    // Función para descargar un archivo desde Drive
    fun downloadFileFromDrive(fileId: String) {
        // Lógica para descargar el archivo
        val file = driveService.files().get(fileId).execute()
        // Hacer lo necesario con el archivo descargado, como guardarlo en la app
    }

    fun obtenerUriDeArchivo(fileId: String): Task<Uri> {
        return Tasks.call(Executors.newSingleThreadExecutor()) {
            val outputFile = java.io.File(context.cacheDir, "${fileId}_${System.currentTimeMillis()}.jpg") // Nombre único

            FileOutputStream(outputFile).use { outputStream ->
                driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            }

            // 🚨 Verificación: si el archivo no se descargó bien, devolver error
            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw IOException("Error al descargar archivo con ID: $fileId")
            }

            Log.d("GoogleDrive", "Archivo descargado correctamente: $outputFile")

            Uri.fromFile(outputFile) // Retorna el URI del archivo descargado
        }
    }



}

