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
            UserMode.valueOf(saved ?: UserMode.PATIENT.name)
        }.getOrDefault(UserMode.PATIENT)
    }

    fun setUserMode(mode: UserMode) {
        prefs.edit()
            .putString(KEY_USER_MODE, mode.name)
            .apply()
    }

    fun isPatient(): Boolean =
        getUserMode() == UserMode.PATIENT

    fun isTutor(): Boolean =
        getUserMode() == UserMode.TUTOR

//    fun isProfessional(): Boolean =
//        getUserMode() == UserMode.PROFESSIONAL

    companion object {
        private const val KEY_USER_MODE = "user_mode"
    }
}