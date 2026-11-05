package com.example.hiremydriver

import android.content.Context

/** Existing notification choices, respected by booking and message delivery. */
class CustomerNotificationPreferences(context: Context) {
    data class Choices(val bookingUpdates: Boolean = true, val chatMessages: Boolean = true)
    private val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load() = Choices(prefs.getBoolean("booking_updates", true), prefs.getBoolean("chat_messages", true))

    fun save(choices: Choices) {
        prefs.edit().putBoolean("booking_updates", choices.bookingUpdates)
            .putBoolean("chat_messages", choices.chatMessages).apply()
    }

    companion object { const val PREFERENCES = "customer_notification_preferences" }
}
