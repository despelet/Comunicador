package com.comunic.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository {

    private val auth: FirebaseAuth =
        FirebaseAuth.getInstance()

    suspend fun login(  email: String,password: String   ): String {
        val result =
            auth.signInWithEmailAndPassword(
                email,
                password
            ).await()

        return result.user?.uid
            ?: error("No se pudo obtener UID")
    }

    suspend fun register(email: String,password: String ): String {
        val result =
            auth.createUserWithEmailAndPassword(
                email,
                password
            ).await()

        return result.user?.uid
            ?: error("No se pudo obtener UID")
    }

    fun getCurrentUid(): String? {
        return auth.currentUser?.uid
    }

    fun logout() {
        auth.signOut()
    }
}