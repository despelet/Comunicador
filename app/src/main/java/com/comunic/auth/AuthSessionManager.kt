package com.comunic.auth

    import android.content.Context
    import android.util.Log
    import com.comunic.session.SessionManager
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.withContext

    class AuthSessionManager(
        private val context: Context,
        private val authRepository: FirebaseAuthRepository = FirebaseAuthRepository()
    ) {

        /*
Orquesta el login
        FirebaseAuthRepository
            ↓
            crea usuario
            ↓
            obtiene uid
            ↓
            AccountMigrationRepository
            ↓
            mueve datos al uid
            ↓
            SessionManager
            ↓
            guarda currentUserId

*/
        private val sessionManager =
            SessionManager(context)

        suspend fun registerAndAdoptLocalData(
            email: String,
            password: String,
            displayName: String
        ): String {
            return withContext(Dispatchers.IO) {

                val uid =
                    authRepository.register(
                        email,
                        password,
                        displayName
                    )

                AccountMigrationRepository(context)
                    .adoptLocalDataToUser(uid)

                sessionManager.setCurrentUserId(uid)

                sessionManager.setDisplayName(
                    displayName
                )

                sessionManager.deactivateTutorMode()

                uid
            }
        }

        fun getCurrentDisplayName(): String? {
            return authRepository.getCurrentDisplayName()
        }

        suspend fun loginAndAdoptLocalData(
            email: String,
            password: String
        ): String {
            return withContext(Dispatchers.IO) {

                val uid =
                    authRepository.login(
                        email,
                        password
                    )

                AccountMigrationRepository(context)
                    .adoptLocalDataToUser(uid)

                sessionManager.setCurrentUserId(uid)
                sessionManager.deactivateTutorMode()

                Log.d(TAG, "Login correcto uid=$uid")

                uid
            }
        }

        suspend fun loginAndUseAccount(
            email: String,
            password: String
        ): String {
            return withContext(Dispatchers.IO) {

                val uid =
                    authRepository.login(email, password)

                sessionManager.setCurrentUserId(uid)

                sessionManager.setDisplayName(
                    authRepository.getCurrentDisplayName().orEmpty()
                )

                sessionManager.deactivateTutorMode()

                uid
            }
        }

        suspend fun sendPasswordReset(
            email: String
        ) {
            withContext(Dispatchers.IO) {
                authRepository.sendPasswordReset(email)
            }
        }

        suspend fun verifyPasswordResetCode(
            code: String
        ): String {
            return withContext(Dispatchers.IO) {
                authRepository.verifyPasswordResetCode(code)
            }
        }

        suspend fun confirmPasswordReset(
            code: String,
            newPassword: String
        ) {
            withContext(Dispatchers.IO) {
                authRepository.confirmPasswordReset(
                    code,
                    newPassword
                )
            }
        }

        fun restoreFirebaseSessionIfExists(): String? {
            val uid =
                authRepository.getCurrentUid()
                    ?: return null

            sessionManager.setCurrentUserId(uid)

            Log.d(TAG, "Sesión Firebase restaurada uid=$uid")

            return uid
        }

        fun getCurrentEmail(): String? {
            return authRepository.getCurrentEmail()
        }

        fun logout() {
            authRepository.logout()
            sessionManager.deactivateTutorMode()
            Log.d(TAG, "Cuenta desconectada. Se conserva currentUserId=${sessionManager.getCurrentUserId()}")
        }

        companion object {
            private const val TAG = "AUTH_SESSION"
        }
    }