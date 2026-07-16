package com.comunic.session

import android.content.Context

class SessionManager(
    context: Context
) {


    // Gurrda estado local de la sesión, como el modo de usuario (paciente o tutor), autenticación del tutor, último acceso del tutor, etc.
    /* currentUserId
        userMode
        lastSyncAt
        legacyMigrationDone*/

    private val prefs =
        context.getSharedPreferences(
            "session_prefs",
            Context.MODE_PRIVATE
        )

    fun getUserMode(): UserMode {
        val saved =
            prefs.getString(
                KEY_USER_MODE,
                UserMode.PATIENT.name
            )

        return runCatching {
            UserMode.valueOf(
                saved ?: UserMode.PATIENT.name
            )
        }.getOrDefault(UserMode.PATIENT)
    }

    fun setUserMode(mode: UserMode) {
        prefs.edit()
            .putString(KEY_USER_MODE, mode.name)
            .apply()
    }

    fun activateTutorMode() {
        prefs.edit()
            .putString(KEY_USER_MODE, UserMode.TUTOR.name)
            .putBoolean(KEY_TUTOR_AUTHENTICATED, true)
            .putLong(KEY_LAST_TUTOR_ACCESS, System.currentTimeMillis())
            .apply()
    }

    fun deactivateTutorMode() {
        prefs.edit()
            .putString(KEY_USER_MODE, UserMode.PATIENT.name)
            .putBoolean(KEY_TUTOR_AUTHENTICATED, false)
            .putLong(KEY_LAST_TUTOR_ACCESS, 0L)
            .apply()
    }

    fun isTutorAuthenticated(): Boolean {
        return prefs.getBoolean(
            KEY_TUTOR_AUTHENTICATED,
            false
        )
    }

    fun getLastTutorAccess(): Long {
        return prefs.getLong(
            KEY_LAST_TUTOR_ACCESS,
            0L
        )
    }

    fun touchTutorAccess() {
        if (getUserMode() == UserMode.TUTOR) {
            prefs.edit()
                .putLong(
                    KEY_LAST_TUTOR_ACCESS,
                    System.currentTimeMillis()
                )
                .apply()
        }
    }

    fun isPatient(): Boolean =
        getUserMode() == UserMode.PATIENT

    fun isTutor(): Boolean =
        getUserMode() == UserMode.TUTOR

    fun isTutorSessionExpired(): Boolean {

        if (!isTutor()) return false
        val lastAccess = getLastTutorAccess()

        if (lastAccess == 0L) return true

        val elapsed =  System.currentTimeMillis() - lastAccess

        return elapsed > TUTOR_TIMEOUT_MS
    }

    fun getCurrentUserId(): String {
        return prefs.getString(
            KEY_CURRENT_USER_ID,
            LOCAL_USER_A
        ) ?: LOCAL_USER_A
    }

    fun setCurrentUserId(
        userId: String
    ) {

        prefs.edit()
            .putString(
                KEY_CURRENT_USER_ID,
                userId
            )
            .apply()
    }

    fun getLastSyncAt(): Long {
        return prefs.getLong(
            KEY_LAST_SYNC_AT,
            0L
        )
    }

    fun setLastSyncAt(
        timestamp: Long
    ) {
        prefs.edit()
            .putLong(
                KEY_LAST_SYNC_AT,
                timestamp
            )
            .apply()
    }

    fun isLegacyMediaKeyMigrationDone(): Boolean {
        return prefs.getBoolean(
            KEY_LEGACY_MEDIA_KEY_MIGRATED,
            false
        )
    }

    fun setLegacyMediaKeyMigrationDone() {
        prefs.edit()
            .putBoolean(
                KEY_LEGACY_MEDIA_KEY_MIGRATED,
                true
            )
            .apply()
    }

    fun getDisplayName(): String? {
        return prefs.getString(
            KEY_DISPLAY_NAME,
            null
        )
    }

    fun setDisplayName(
        name: String
    ) {
        prefs.edit()
            .putString(
                KEY_DISPLAY_NAME,
                name
            )
            .apply()
    }

    fun clearDisplayName() {
        prefs.edit()
            .remove(KEY_DISPLAY_NAME)
            .apply()
    }

    //para migrar las imágenes a un tamaño más pequeño y optimizado, para que no ocupen tanto espacio
    fun isImageOptimizationDone(): Boolean =
        prefs.getBoolean(KEY_IMAGE_OPTIMIZATION_DONE, false)

    fun setImageOptimizationDone() =
        prefs.edit()
            .putBoolean(KEY_IMAGE_OPTIMIZATION_DONE, true)
            .apply()


    companion object {
        private const val KEY_USER_MODE = "user_mode"
        private const val KEY_TUTOR_AUTHENTICATED = "tutor_authenticated"
        private const val KEY_LAST_TUTOR_ACCESS = "last_tutor_access"

        const val TUTOR_TIMEOUT_MS = 10 * 60 * 1000L
        private const val KEY_CURRENT_USER_ID = "current_user_id"

        const val LOCAL_USER_A = "local_user"
        const val LOCAL_USER_B = "local_user_b"

        private const val KEY_LAST_SYNC_AT =   "last_sync_at"
        private const val KEY_LEGACY_MEDIA_KEY_MIGRATED =  "legacy_media_key_migrated"
        private const val KEY_DISPLAY_NAME = "display_name"

        private const val KEY_IMAGE_OPTIMIZATION_DONE =  "image_optimization_done"

        }

}