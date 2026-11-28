package com.inkwell.notes.auth

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `getToken()` from AuthContext.jsx: the saved access token while it is valid for another
 * 2 minutes, otherwise a fresh one from Google Play services (no popup for a signed-in account).
 */
class TokenProvider(context: Context) {
    private val app = context.applicationContext
    private val store = AuthStore(app)

    /** Throws when signed out or Google refuses (e.g. consent needed → sign in again). */
    suspend fun getToken(forceRefresh: Boolean = false): String {
        val user = store.load() ?: error("Not signed in")
        if (!forceRefresh && user.accessToken.isNotBlank() && user.expiry > System.currentTimeMillis() + 2 * 60 * 1000L) {
            return user.accessToken
        }
        return withContext(Dispatchers.IO) {
            val account = GoogleSignIn.getLastSignedInAccount(app)?.account
                ?: error("Google session expired — sign in again")
            if (user.accessToken.isNotBlank()) {
                runCatching { GoogleAuthUtil.clearToken(app, user.accessToken) }
            }
            val token = GoogleAuthUtil.getToken(app, account, TOKEN_SCOPE)
            val now = System.currentTimeMillis()
            store.save(user.copy(accessToken = token, expiry = now + 3600 * 1000L, savedAt = now))
            token
        }
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val TOKEN_SCOPE = "oauth2:profile email $DRIVE_APPDATA_SCOPE"
    }
}
