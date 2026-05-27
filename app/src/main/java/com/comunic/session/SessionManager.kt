package com.comunic.session

import android.content.Context

class SessionManager(
    context: Context
) {

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


    companion object {
        private const val KEY_USER_MODE = "user_mode"
        private const val KEY_TUTOR_AUTHENTICATED = "tutor_authenticated"
        private const val KEY_LAST_TUTOR_ACCESS = "last_tutor_access"

//        const val TUTOR_TIMEOUT_MS =
//            5 * 60 * 1000L // 5 min
    const val TUTOR_TIMEOUT_MS =
        10 * 60 * 1000L
    }
}