package com.inkwell.notes.auth

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.inkwell.notes.BuildConfig
import com.inkwell.notes.data.AuthException
import com.inkwell.notes.data.AuthProvider
import com.inkwell.notes.model.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await

/**
 * Google Sign-In (Credential Manager) + Drive appData access token (Authorization API).
 * Create it in MainActivity.onCreate so the consent launcher is registered early enough.
 */
class GoogleAuth(private val activity: ComponentActivity) : AuthProvider {
    private val driveScope = "https://www.googleapis.com/auth/drive.appdata"
    private var consent: CompletableDeferred<AuthorizationResult?>? = null

    private val consentLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { res ->
        val waiting = consent
        consent = null
        if (waiting == null) return@registerForActivityResult
        val data = res.data
        if (res.resultCode == Activity.RESULT_OK && data != null) {
            try {
                waiting.complete(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data))
            } catch (e: ApiException) {
                waiting.complete(null)
            }
        } else {
            waiting.complete(null)
        }
    }

    override val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    override suspend fun signIn(): UserProfile {
        if (!isConfigured) throw AuthException("Setup required: GOOGLE_WEB_CLIENT_ID is missing")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
            .build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw AuthException("Sign-in cancelled")
        } catch (e: NoCredentialException) {
            throw AuthException("No Google account found on this device")
        } catch (e: GetCredentialException) {
            throw AuthException(e.message ?: "Sign-in failed")
        }
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = try {
                GoogleIdTokenCredential.createFrom(cred.data)
            } catch (e: GoogleIdTokenParsingException) {
                throw AuthException("Could not read your Google account")
            }
            return UserProfile(
                name = google.displayName ?: google.givenName ?: google.id.substringBefore('@'),
                email = google.id,
                picture = google.profilePictureUri?.toString(),
            )
        }
        throw AuthException("Unexpected sign-in result")
    }

    override suspend fun driveToken(): String? {
        val client = Identity.getAuthorizationClient(activity)
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(driveScope))).build()
        var result: AuthorizationResult = try {
            client.authorize(request).await()
        } catch (e: Exception) {
            return null
        }
        if (result.hasResolution()) {
            val pending = result.pendingIntent ?: return null
            val waiting = CompletableDeferred<AuthorizationResult?>()
            consent = waiting
            consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            result = waiting.await() ?: return null
        }
        return result.accessToken
    }

    override suspend fun signOut() {
        try {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Signing out locally is enough even if clearing the credential state fails.
        }
    }
}
