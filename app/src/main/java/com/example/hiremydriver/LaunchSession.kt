package com.example.hiremydriver

import android.content.Context

internal enum class LaunchRole { CUSTOMER, DRIVER, NONE }

/** Routing hints only. Firebase retains and validates the actual sign-in credentials. */
internal object LaunchSession {
    private const val PREFS = "launch_session"
    fun remember(context: Context, role: LaunchRole, uid: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("role", role.name).putString("uid", uid).commit()
    }

    fun choose(savedRole: String?, savedUid: String?, customerUid: String?, driverUid: String?,
               existingDriver: Boolean): LaunchRole {
        if (savedRole != null) return when {
            savedRole == LaunchRole.CUSTOMER.name && savedUid != null && savedUid == customerUid -> LaunchRole.CUSTOMER
            savedRole == LaunchRole.DRIVER.name && savedUid != null && savedUid == driverUid -> LaunchRole.DRIVER
            else -> LaunchRole.NONE
        }
        // Upgrade existing installations without mistaking an unfinished registration for a login.
        return when {
            driverUid != null && existingDriver -> LaunchRole.DRIVER
            customerUid != null -> LaunchRole.CUSTOMER
            else -> LaunchRole.NONE
        }
    }

    fun role(context: Context): LaunchRole {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val customer = CustomerAuth.currentUser(context)?.takeUnless { it.isAnonymous }?.uid
        val driver = DriverAuth.currentUser(context)?.takeUnless { it.isAnonymous }?.uid
        val existingDriver = driver != null && context.getSharedPreferences(
            "${DriverDocumentStore.PREFERENCES}_$driver", Context.MODE_PRIVATE).contains("licenseNumber")
        return choose(prefs.getString("role", null), prefs.getString("uid", null), customer, driver, existingDriver)
    }

    fun logout(context: Context) {
        RegisteredDriverDirectory.clearCache()
        com.example.hiremydriver.recommendation.NearbyRecommendations.clearCache()
        com.example.hiremydriver.notifications.AppNotifications.unregister(context)
        // Record explicit logout first so an older role can never become the launch fallback.
        remember(context, LaunchRole.NONE, "")
        CustomerAuth.signOut(context)
        DriverAuth.signOut(context)
    }
}
