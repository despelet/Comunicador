package com.comunic.sync

import android.content.Context
import android.util.Log
import com.comunic.data.db.AppDatabase
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.entity.MediaEntity
import com.comunic.session.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.net.Uri
import com.comunic.dialog.SyncProgressDialog
import com.google.firebase.storage.FirebaseStorage
import java.io.File

class CloudSyncRepository(
    private val context: Context
) {

    private val db = AppDatabase.getDatabase(context)
    private val sessionManager = SessionManager(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()



    suspend fun uploadAll(syncDialog: SyncProgressDialog? = null) {

        val firebaseUser =
            auth.currentUser ?: run {
                Log.w(TAG, "No hay usuario Firebase conectado")
                return
            }

        val uid = firebaseUser.uid
        val userId = sessionManager.getCurrentUserId()

        Log.d(TAG, "uploadAll uid=$uid userId=$userId")

        uploadProfile(
            uid = uid,
            displayName = firebaseUser.displayName
                ?: sessionManager.getDisplayName()
                ?: "",
            email = firebaseUser.email ?: ""
        )

        uploadCategories(userId)
        uploadCategoryItems(userId)
        uploadMedia(userId = userId, syncDialog = syncDialog)
        firestore.collection("users").document(uid).update(
                mapOf(
                    "lastSync" to System.currentTimeMillis()
                )
            )
            .await()
        sessionManager.setLastSyncAt(System.currentTimeMillis())

        Log.d(TAG, "uploadAll finalizado")
    }



    private suspend fun uploadProfile(uid: String, displayName: String, email: String) {

        val ref = firestore.collection("users").document(uid)
        ref.set(
            mapOf(
                "uid" to uid,
                "displayName" to displayName,
                "email" to email,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
        Log.d(TAG, "Perfil subido")
    }

    private suspend fun uploadCategories(userId: String) {

        val categories = db.categoryDao().getAllCategoriesForSync(userId)

        val uid = auth.currentUser?.uid ?: return
        val batch = firestore.batch()

        categories.forEach { category ->
            val ref =
                firestore
                    .collection("users")
                    .document(uid)
                    .collection("categories")
                    .document(category.categoryId)

            batch.set(
                ref,
                mapOf(
                    "categoryId" to category.categoryId,
                    "name" to category.name,
                    "orderIndex" to category.orderIndex,
                    "createdAt" to category.createdAt,
                    "updatedAt" to category.updatedAt,
                    "packId" to category.packId,
                    "isSystem" to category.isSystem,
                    "isDeleted" to category.isDeleted,
                    "ownerUserId" to category.ownerUserId
                )
            )
        }

        batch.commit().await()

        Log.d(TAG, "Categorías subidas: ${categories.size}")
    }

    private suspend fun uploadCategoryItems(userId: String) {
        val items = db.categoryDao().getAllCategoryItemsForSync(userId)
        val uid = auth.currentUser?.uid ?: return
        val batch = firestore.batch()

        items.forEach { item ->
            val ref = firestore.collection("users").document(uid).collection("category_items").document(item.placementId)

            batch.set(
                ref,
                mapOf(
                    "placementId" to item.placementId,
                    "categoryId" to item.categoryId,
                    "itemKey" to item.itemKey,
                    "orderIndex" to item.orderIndex,
                    "createdAt" to item.createdAt,
                    "updatedAt" to item.updatedAt,
                    "isDeleted" to item.isDeleted,
                    "ownerUserId" to item.ownerUserId
                )
            )
        }

        batch.commit().await()
        Log.d(TAG, "CategoryItems subidos: ${items.size}")
    }

    private suspend fun uploadMedia(
        userId: String,
        syncDialog: SyncProgressDialog?
    ) {
        verificarMediaLocal(userId)
        val mediaItems = db.mediaDao().getAllMediaForSync(userId)
        val uid = auth.currentUser?.uid ?: return
        val batch = firestore.batch()
        val total = mediaItems.size
        mediaItems.forEachIndexed { index, media ->
            syncDialog?.updateProgress(
                current = index,
                total = total,
                estado = "Subiendo archivos",
                detalle = media.displayName
            )

            val storagePath =
                uploadMediaFile(
                    uid = uid,
                    mediaId = media.mediaId,
                    localUri = media.localUri,
                    mediaType = media.mediaType
                )
            val ref =
                firestore
                    .collection("users")
                    .document(uid)
                    .collection("media_items")
                    .document(media.mediaId)

            batch.set(
                ref,
                mapOf(
                    "mediaId" to media.mediaId,
                    "displayName" to media.displayName,
                    "localUri" to media.localUri,
                    "mediaType" to media.mediaType,
                    "storagePath" to storagePath,
                    "createdAt" to media.createdAt,
                    "updatedAt" to media.updatedAt,
                    "isDeleted" to media.isDeleted,
                    "ownerUserId" to media.ownerUserId
                )
            )
            syncDialog?.updateProgress(
                current = index + 1,
                total = total,
                estado = "Subiendo archivos",
                detalle = media.displayName
            )
        }

        batch.commit().await()
        Log.d(TAG, "Media subidos: ${mediaItems.size}")
    }

    suspend fun downloadAll(
        syncDialog: SyncProgressDialog? = null
    ) {
        //    downloadAll() es la función principal. Verifica que haya usuario Firebase, toma su uid,
        //    guarda ese uid como currentUserId, y llama en orden a las descargas: perfil, categorías,
        //    elementos de categorías y media.

            val firebaseUser =
                auth.currentUser ?: run {
                    Log.w(TAG, "No hay usuario Firebase conectado")
                    return
                }

            val uid = firebaseUser.uid

            try {
                sessionManager.setCurrentUserId(uid)

                downloadProfile(uid)
                downloadCategories(uid)
                downloadCategoryItems(uid)
                downloadMedia(
                    uid = uid,
                    syncDialog = syncDialog
                )
                sessionManager.setLastSyncAt(System.currentTimeMillis())

                Log.d(TAG, "downloadAll finalizado")

            } catch (e: Exception) {
                Log.e(TAG, "Error en downloadAll", e)
                throw e
            }
        }

    private suspend fun downloadProfile(uid: String) {

        //downloadProfile(uid) lee users/{uid} en Firestore.
        // Si encuentra displayName, lo guarda en SessionManager, para que aparezca
        // “¡Hola, nombre!” aunque sea un dispositivo nuevo.
        val doc =
            firestore
                .collection("users")
                .document(uid)
                .get()
                .await()

        val displayName = doc.getString("displayName")

        if (!displayName.isNullOrBlank()) {
            sessionManager.setDisplayName(displayName)
        }

        Log.d(TAG, "Perfil descargado")
    }

    private suspend fun downloadCategories(uid: String) {

//        downloadCategories(uid) lee users/{uid}/categories.
        //        Por cada documento crea un CategoryEntity y lo guarda en Room con upsert().
        //        Eso reconstruye las listas/categorías en el dispositivo.

        val snapshot =
            firestore
                .collection("users")
                .document(uid)
                .collection("categories")
                .whereEqualTo("isDeleted", false)
                .get()
                .await()

        snapshot.documents.forEach { doc ->

            val category =
                CategoryEntity(
                    categoryId = doc.getString("categoryId") ?: doc.id,
                    name = doc.getString("name") ?: "",
                    orderIndex = (doc.getLong("orderIndex") ?: 0L).toInt(),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                    packId = doc.getString("packId") ?: "user",
                    isSystem = doc.getBoolean("isSystem") ?: false,
                    isDeleted = doc.getBoolean("isDeleted") ?: false,
                    ownerUserId = uid
                )

            db.categoryDao().upsert(category)
        }

        Log.d(TAG, "Categorías descargadas: ${snapshot.size()}")
    }

    private suspend fun downloadCategoryItems(uid: String) {

    //    downloadCategoryItems(uid) lee users/{uid}/category_items.
        //    Por cada documento crea un CategoryItemEntity. Esto reconstruye qué elementos
        //    hay dentro de cada lista/categoría.

        val snapshot =
            firestore
                .collection("users")
                .document(uid)
                .collection("category_items")
                .whereEqualTo("isDeleted", false)
                .get()
                .await()

        snapshot.documents.forEach { doc ->

            val item =
                CategoryItemEntity(
                    placementId = doc.getString("placementId") ?: doc.id,
                    categoryId = doc.getString("categoryId") ?: "",
                    itemKey = doc.getString("itemKey") ?: "",
                    orderIndex = (doc.getLong("orderIndex") ?: 0L).toInt(),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                    isDeleted = doc.getBoolean("isDeleted") ?: false,
                    ownerUserId = uid
                )

            db.categoryDao().insertCategoryItem(item)
        }

        Log.d(TAG, "CategoryItems descargados: ${snapshot.size()}")
    }

    private suspend fun downloadMedia(
        uid: String,
        syncDialog: SyncProgressDialog?
    ) {

        val snapshot =
            firestore
                .collection("users")
                .document(uid)
                .collection("media_items")
                .whereEqualTo("isDeleted", false)
                .get()
                .await()

        val total = snapshot.size()

        snapshot.documents.forEachIndexed { index, doc ->

            val mediaId = doc.getString("mediaId") ?: doc.id
            val displayName = doc.getString("displayName") ?: ""
            val mediaType = doc.getString("mediaType") ?: "image"
            val storagePath = doc.getString("storagePath") ?: ""

            syncDialog?.updateProgress(
                current = index,
                total = total,
                estado = "Descargando archivos",
                detalle = displayName
            )

            Log.d(TAG, "MEDIA_DOWNLOAD mediaId=$mediaId storagePath=$storagePath")

            val localUri =
                if (storagePath.isNotBlank()) {
                    downloadMediaFile(
                        uid = uid,
                        mediaId = mediaId,
                        displayName = displayName,
                        mediaType = mediaType,
                        storagePath = storagePath
                    )
                } else {
                    doc.getString("localUri") ?: ""
                }

            val media =
                MediaEntity(
                    mediaId = mediaId,
                    displayName = displayName,
                    localUri = localUri,
                    mediaType = mediaType,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                    isDeleted = doc.getBoolean("isDeleted") ?: false,
                    ownerUserId = uid,
                    storagePath = storagePath
                )

            db.mediaDao().upsert(media)

            syncDialog?.updateProgress(
                current = index + 1,
                total = total,
                estado = "Descargando archivos",
                detalle = displayName
            )
        }
        Log.d(TAG, "Media descargados: ${snapshot.size()}")
    }

    private suspend fun downloadMediaFile(
        uid: String,
        mediaId: String,
        displayName: String,
        mediaType: String,
        storagePath: String
    ): String {

        val dir = File(context.filesDir, "media")

        if (!dir.exists()) {
            dir.mkdirs()
        }

        val extension =
            if (mediaType == "video") {
                "mp4"
            } else {
                "jpg"
            }

//        val safeName =
//            if (displayName.isNotBlank()) {
//                displayName
//            } else {
//                mediaId
//            }
//
//        val file =
//            File(dir, "$safeName.$extension")
        val file =
            File(
                dir,
                "$mediaId.$extension"
            )

        if (file.exists()) {
            Log.d(
                TAG,
                "Archivo ya existe localmente: ${file.absolutePath}"
            )

            return Uri.fromFile(file).toString()
        }

        storage
            .reference
            .child(storagePath)
            .getFile(file)
            .await()

        Log.d(
            TAG,
            "Archivo descargado: $storagePath -> ${file.absolutePath}"
        )

        return Uri.fromFile(file).toString()
    }

    private suspend fun uploadMediaFile(
        uid: String,
        mediaId: String,
        localUri: String,
        mediaType: String
    ): String {

        val uri = Uri.parse(localUri)
        val file = File(uri.path ?: "")

        if (!file.exists()) {
            Log.w(
                TAG,
                "Archivo local no existe para mediaId=$mediaId uri=$localUri"
            )
            return ""
        }

        val extension =
            if (mediaType == "video") {
                "mp4"
            } else {
                "jpg"
            }

        val storagePath = "users/$uid/media/$mediaId.$extension"

        val storageRef =
            storage.reference.child(storagePath)

        try {

            storageRef.metadata.await()

            Log.d(
                TAG,
                "Archivo ya existe en Storage: $storagePath"
            )

            return storagePath

        } catch (_: Exception) {

            storageRef
                .putFile(Uri.fromFile(file))
                .await()

            Log.d(
                TAG,
                "Archivo subido: $storagePath"
            )

            return storagePath
        }
    }

    private suspend fun verificarMediaLocal(
        userId: String
    ) {
        val mediaItems =
            db.mediaDao()
                .getAllMediaForSync(userId)

        var existentes = 0
        var faltantes = 0

        mediaItems.forEach { media ->

            val uri =
                Uri.parse(media.localUri)

            val file =
                File(uri.path ?: "")

            if (file.exists()) {
                existentes++
            } else {
                faltantes++

                Log.w(
                    TAG,
                    "Falta archivo mediaId=${media.mediaId} nombre=${media.displayName} uri=${media.localUri}"
                )
            }
        }

        Log.d(
            TAG,
            "Verificación media local: total=${mediaItems.size}, existentes=$existentes, faltantes=$faltantes"
        )
    }


    companion object {
        private const val TAG =
            "CLOUD_SYNC"
    }
}