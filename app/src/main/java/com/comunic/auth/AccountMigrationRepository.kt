package com.comunic.auth

import android.content.Context
import android.util.Log
import com.comunic.data.db.AppDatabase
import com.comunic.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccountMigrationRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getDatabase(context)
) {

    private val sessionManager =
        SessionManager(context)

    suspend fun adoptLocalDataToUser(
        newUserId: String
    ) {
        withContext(Dispatchers.IO) {

            val oldUserId =
                sessionManager.getCurrentUserId()

            Log.d(
                TAG,
                "oldUserId=$oldUserId newUserId=$newUserId"
            )

            if (oldUserId == newUserId) {
                Log.d(TAG, "No se requiere adopción: oldUserId == newUserId")
                return@withContext
            }

            val now =
                System.currentTimeMillis()

            Log.d(
                TAG,
                "Migrando media"
            )
            db.mediaDao().adoptMediaToUser(
                oldUserId = oldUserId,
                newUserId = newUserId,
                updatedAt = now
            )

            db.categoryDao().adoptCategoriesToUser(
                oldUserId = oldUserId,
                newUserId = newUserId,
                updatedAt = now
            )

            db.categoryDao().adoptCategoryItemsToUser(
                oldUserId = oldUserId,
                newUserId = newUserId,
                updatedAt = now
            )

            db.itemUsadoDao().adoptItemsUsadosToUser(
                oldUserId = oldUserId,
                newUserId = newUserId
            )

            db.itemUsadoBucketDao().adoptItemsUsadosBucketToUser(
                oldUserId = oldUserId,
                newUserId = newUserId
            )

            sessionManager.setCurrentUserId(newUserId)

            Log.d(
                TAG,
                "Datos locales adoptados: $oldUserId -> $newUserId"
            )
        }
    }

    companion object {
        private const val TAG =
            "ACCOUNT_MIGRATION"
    }
}