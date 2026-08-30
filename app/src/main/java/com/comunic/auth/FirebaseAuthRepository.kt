package com.comunic.auth

import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.UserProfileChangeRequest

class FirebaseAuthRepository {

    /*
    *  FirebaseAuth es seguro para usar en múltiples hilos y se recomienda mantener una sola instancia en toda la aplicación.
    * register()
        login()
        logout()
        getCurrentUid()
        *
        *
        * ejemplo:
        *           usuario: delfi@gmail.com
                password: 123456
                ↓
                FirebaseAuthRepository.login()
                ↓
                Firebase devuelve
                ↓
                uid = AbC123XyZ...
    * */

        private val auth: FirebaseAuth = FirebaseAuth.getInstance()

        suspend fun login(
            email: String,
            password: String
        ): String {
            val result =
                auth.signInWithEmailAndPassword(
                    email,
                    password
                ).await()

            return result.user?.uid
                ?: error("No se pudo obtener UID")
        }

    suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): String {
        val result =
            auth.createUserWithEmailAndPassword(
                email,
                password
            ).await()

        val user =
            result.user ?: error("No se pudo obtener usuario")

        val profileUpdates =
            UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()

        user.updateProfile(profileUpdates).await()

        return user.uid
    }
    fun getCurrentDisplayName(): String? {
        return auth.currentUser?.displayName
    }

    suspend fun sendPasswordReset(
        email: String
    ) {
        val actionCodeSettings =
            ActionCodeSettings.newBuilder()
                .setUrl(
                    "https://bicom-7b58c.firebaseapp.com/finishPasswordReset"
                )
                .setHandleCodeInApp(true)
                .setAndroidPackageName(
                    "com.comunic",
                    true,
                    null
                )
                .build()

        auth.sendPasswordResetEmail(
            email,
            actionCodeSettings
        ).await()
    }

    suspend fun verifyPasswordResetCode(
        code: String
    ): String {
        return auth
            .verifyPasswordResetCode(code)
            .await()
    }
    suspend fun confirmPasswordReset(
        code: String,
        newPassword: String
    ) {
        auth
            .confirmPasswordReset(
                code,
                newPassword
            )
            .await()
    }

        fun getCurrentUid(): String? {
            return auth.currentUser?.uid
        }

        fun getCurrentEmail(): String? {
            return auth.currentUser?.email
        }

        fun logout() {
            auth.signOut()
        }
    }