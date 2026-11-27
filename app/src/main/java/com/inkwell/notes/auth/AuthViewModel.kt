package com.inkwell.notes.auth

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.inkwell.notes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthState(
    val user: AuthUser? = null,
    val signingIn: Boolean = false,
    val error: String? = null,
)

/**
 * Kotlin port of `src/auth/AuthContext.jsx` (native branch).
 * Uses the same Google Sign-In SDK the Capacitor plugin wrapped, with the same
 * scopes and the same persisted user object.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AuthStore(application)

    private val _state = MutableStateFlow(AuthState(user = store.load()))
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** False → Login shows the "Setup required" card instead of the button. */
    val hasClientId: Boolean = BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()

    private val signInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .build()
        GoogleSignIn.getClient(application, options)
    }

    /**
     * `signIn('select_account')`: sign out of the cached Google account first so
     * the account chooser is always shown, then hand the intent to the UI layer.
     */
    fun startSignIn(launch: (Intent) -> Unit) {
        if (!hasClientId || _state.value.signingIn) return
        _state.update { it.copy(error = null, signingIn = true) }
        signInClient.signOut().addOnCompleteListener {
            launch(signInClient.signInIntent)
        }
    }

    fun onSignInResult(data: Intent?) {
        // Chooser dismissed with the back button → not an error.
        if (data == null) {
            _state.update { it.copy(signingIn = false) }
            return
        }
        viewModelScope.launch {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
                val androidAccount = account.account ?: error("Google account missing")

                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(getApplication<Application>(), androidAccount, TOKEN_SCOPE)
                }

                val now = System.currentTimeMillis()
                val user = AuthUser(
                    name = account.displayName ?: account.email ?: "",
                    email = account.email ?: "",
                    picture = account.photoUrl?.toString() ?: "",
                    accessToken = token,
                    expiry = now + 3600 * 1000L,   // same 1 h fallback as the web app
                    savedAt = now,
                )
                store.save(user)
                _state.update { it.copy(user = user, signingIn = false, error = null) }
            } catch (e: ApiException) {
                Log.e(TAG, "Sign-in failed, status=${e.statusCode}", e)
                val cancelled = e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED ||
                    e.statusCode == CommonStatusCodes.CANCELED
                _state.update {
                    it.copy(
                        signingIn = false,
                        error = if (cancelled) null else SIGN_IN_FAILED,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sign-in failed", e)
                _state.update { it.copy(signingIn = false, error = SIGN_IN_FAILED) }
            }
        }
    }

    fun signOut() {
        runCatching { signInClient.signOut() }
        store.clear()
        _state.update { AuthState() }
    }

    private companion object {
        const val TAG = "InkwellAuth"
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        // Identical to what the GitHub Actions patch gave the Capacitor plugin.
        const val TOKEN_SCOPE = "oauth2:profile email $DRIVE_APPDATA_SCOPE"
        const val SIGN_IN_FAILED = "Sign-in failed. Please try again."
    }
}
