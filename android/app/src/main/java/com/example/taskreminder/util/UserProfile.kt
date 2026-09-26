package com.example.taskreminder.util

import android.content.Context
import java.util.UUID

/** Remembers what the user typed into the forms so they don't retype it, plus a random install id for rate limiting. */
object UserProfile {
    private const val PREFS = "user_profile"
    private const val KEY_NAME = "name"
    private const val KEY_EMAIL = "email"
    private const val KEY_LOCATION = "location"
    private const val KEY_INSTALL_ID = "install_id"

    data class Profile(val name: String, val email: String, val location: String)

    fun load(context: Context): Profile {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Profile(
            name = prefs.getString(KEY_NAME, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            location = prefs.getString(KEY_LOCATION, "") ?: ""
        )
    }

    fun save(context: Context, name: String? = null, email: String? = null, location: String? = null) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            name?.let { putString(KEY_NAME, it.trim()) }
            email?.let { putString(KEY_EMAIL, it.trim()) }
            location?.let { putString(KEY_LOCATION, it.trim()) }
        }.apply()
    }

    /** Random, app-scoped id (not tied to the device or account); lets the relay rate-limit per install. */
    fun installId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_INSTALL_ID, it).apply()
        }
    }
}
