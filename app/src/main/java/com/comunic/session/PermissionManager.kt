package com.comunic.session

class PermissionManager(
    private val sessionManager: SessionManager
) {

    fun canDeleteMedia(): Boolean {
        return when (sessionManager.getUserMode()) {
            UserMode.PATIENT -> false
            UserMode.TUTOR -> true
            //UserMode.PROFESSIONAL -> false
        }
    }

    fun canEditMedia(): Boolean {
        return when (sessionManager.getUserMode()) {
            UserMode.PATIENT -> false
            UserMode.TUTOR -> true
            //UserMode.PROFESSIONAL -> false
        }
    }

    fun canCreateMedia(): Boolean {
        return when (sessionManager.getUserMode()) {
            UserMode.PATIENT -> true
            UserMode.TUTOR -> true
            //UserMode.PROFESSIONAL -> true
        }
    }

    fun canImportExport(): Boolean {
        return when (sessionManager.getUserMode()) {
            UserMode.PATIENT -> true
            UserMode.TUTOR -> true
           // UserMode.PROFESSIONAL -> true
        }
    }

    fun canAccessTrash(): Boolean {
        return when (sessionManager.getUserMode()) {
            UserMode.PATIENT -> false
            UserMode.TUTOR -> true
            //UserMode.PROFESSIONAL -> false
        }
    }

    fun canManageProfiles(): Boolean {
        return when (sessionManager.getUserMode()) {
            UserMode.PATIENT -> true
            UserMode.TUTOR -> true
            //UserMode.PROFESSIONAL -> false
        }
    }
}