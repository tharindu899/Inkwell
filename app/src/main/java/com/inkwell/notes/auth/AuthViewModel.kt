package com.inkwell.notes.auth

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val manager = AuthManager(app)

    val state: StateFlow<AuthUiState> = manager.state
    val hasClientId: Boolean get() = manager.hasClientId

    fun signIn(activity: Activity) {
        viewModelScope.launch { manager.signIn(activity) }
    }

    fun signOut() {
        viewModelScope.launch { manager.signOut(getApplication()) }
    }
}
