package com.inkwell.notes.data

import com.inkwell.notes.model.UserProfile

class AuthException(message: String) : Exception(message)

/** The store only knows this interface. GoogleAuth (auth/GoogleAuth.kt) is the real implementation. */
interface AuthProvider {
    /** false when GOOGLE_WEB_CLIENT_ID was not provided at build time. */
    val isConfigured: Boolean
    suspend fun signIn(): UserProfile
    /** OAuth access token for the Drive appData scope, or null if the user declined. */
    suspend fun driveToken(): String?
    suspend fun signOut()
}
