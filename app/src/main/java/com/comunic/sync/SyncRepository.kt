package com.comunic.sync

import android.content.Context
import android.util.Log
import com.comunic.data.db.AppDatabase
import com.comunic.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getDatabase(context)
) {

    private val sessionManager =   SessionManager(context)

    suspend fun syncNow() {
        withContext(Dispatchers.IO) {

            val userId = sessionManager.getCurrentUserId()
            val lastSync =  sessionManager.getLastSyncAt()
            Log.d(  TAG,"Última sync: $lastSync" )

            Log.d( TAG,"syncNow() iniciado para userId=$userId"  )

            pushPendingChanges(userId)
            pullRemoteChanges(userId)

            sessionManager.setLastSyncAt(
                System.currentTimeMillis()
            )

            Log.d(TAG, "syncNow() finalizado para userId=$userId" )


        }

    }

    private suspend fun pushPendingChanges(
        userId: String
    ) {
        Log.d( TAG,"pushPendingChanges() pendiente de implementar userId=$userId" )

        // Futuro:
        // 1. Buscar cambios locales con updatedAt > lastSyncAt
        // 2. Subir metadata a Firestore
        // 3. Subir archivos media a Firebase Storage
    }

    private suspend fun pullRemoteChanges(
        userId: String
    ) {
        Log.d(TAG, "pullRemoteChanges() pendiente de implementar userId=$userId" )

        // Futuro:
        // 1. Leer Firestore por ownerUserId == userId
        // 2. Comparar updatedAt remoto vs local
        // 3. Insertar/actualizar Room
        // 4. Descargar archivos faltantes desde Storage
    }

    companion object {
        private const val TAG = "SYNC_REPOSITORY"
    }
}