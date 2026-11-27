package com.inkwell.notes.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.inkwell.notes.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UserSession(
    val email: String,
    val name: String,
    val photoUrl: String?,
)

data class AuthUiState(
    val user: UserSession? = null,
    val signingIn: Boolean = false,
    val error: String? = null,
)

/**
 * Native Google sign-in (replaces the Capacitor GoogleAuth plugin).
 * Uses the same Web client ID as before. The Drive "appdata" scope is
 * requested later, in the sync part.
 */
class AuthManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("inkwell_session", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(AuthUiState(user = load()))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val hasClientId: Boolean get() = BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()

    suspend fun signIn(activity: Activity) {
        if (_state.value.signingIn) return
        if (!hasClientId) {
            _state.update { it.copy(error = "Setup required: GOOGLE_CLIENT_ID is missing.") }
            return
        }
        _state.update { it.copy(signingIn = true, error = null) }
        try {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential &&
                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                val user = UserSession(
                    email = g.id,
                    name = g.displayName ?: g.id.substringBefore('@'),
                    photoUrl = g.profilePictureUri?.toString(),
                )
                save(user)
                _state.value = AuthUiState(user = user)
            } else {
                _state.update { it.copy(error = "Unexpected sign-in response.") }
            }
        } catch (e: GetCredentialCancellationException) {
            // user closed the account picker — no error
        } catch (e: NoCredentialException) {
            _state.update {
                it.copy(error = "No Google account found on this phone. Add one in Settings, then try again.")
            }
        } catch (e: GetCredentialException) {
            _state.update { it.copy(error = e.message?.take(160) ?: "Sign-in failed. Please try again.") }
        } catch (e: GoogleIdTokenParsingException) {
            _state.update { it.copy(error = "Could not read Google account details.") }
        } finally {
            if (_state.value.signingIn) _state.update { it.copy(signingIn = false) }
        }
    }

    suspend fun signOut(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
        prefs.edit().clear().apply()
        _state.value = AuthUiState()
    }

    private fun load(): UserSession? {
        val email = prefs.getString("email", null) ?: return null
        return UserSession(
            email = email,
            name = prefs.getString("name", null) ?: email.substringBefore('@'),
            photoUrl = prefs.getString("photo", null),
        )
    }

    private fun save(user: UserSession) {
        prefs.edit()
            .putString("email", user.email)
            .putString("name", user.name)
            .putString("photo", user.photoUrl)
            .putLong("savedAt", System.currentTimeMillis())
            .apply()
    }
}
