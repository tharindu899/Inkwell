package com.inkwell.notes.auth

import android.content.Context
import com.inkwell.notes.data.AppPrefs
import org.json.JSONObject

/** Same shape as the object the web app kept in localStorage `iw_gauth`. */
data class AuthUser(
    val name: String,
    val email: String,
    val picture: String,
    val accessToken: String,
    val expiry: Long,
    val savedAt: Long,
)

class AuthStore(context: Context) {
    private val prefs = AppPrefs.of(context)

    fun load(): AuthUser? = try {
        val raw = prefs.getString(AppPrefs.KEY_AUTH, null)
        if (raw.isNullOrEmpty()) {
            null
        } else {
            val o = JSONObject(raw)
            val name = o.optString("name", "")
            val email = o.optString("email", "")
            if (name.isEmpty() && email.isEmpty()) {
                null
            } else {
                AuthUser(
                    name = name,
                    email = email,
                    picture = o.optString("picture", ""),
                    accessToken = o.optString("accessToken", ""),
                    expiry = o.optLong("expiry", 0L),
                    savedAt = o.optLong("savedAt", System.currentTimeMillis()),
                )
            }
        }
    } catch (_: Exception) {
        null
    }

    fun save(user: AuthUser) {
        val o = JSONObject()
            .put("name", user.name)
            .put("email", user.email)
            .put("picture", user.picture)
            .put("accessToken", user.accessToken)
            .put("expiry", user.expiry)
            .put("savedAt", user.savedAt)
        prefs.edit().putString(AppPrefs.KEY_AUTH, o.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(AppPrefs.KEY_AUTH).apply()
    }
}
