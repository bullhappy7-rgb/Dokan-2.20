package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.UserProfile
import com.example.data.repository.StoreRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object Unauthenticated : AuthUiState
    data class AuthenticatedNoStore(val user: FirebaseUser) : AuthUiState
    data class AuthenticatedWithStore(val user: FirebaseUser, val storeId: String) : AuthUiState
    data class DemoMode(val storeId: String = "demo_store") : AuthUiState
}

class AuthViewModel(
    private val repository: StoreRepository
) : ViewModel() {

    private val auth: FirebaseAuth? = try {
        Firebase.auth
    } catch (e: Exception) {
        Log.w("AuthViewModel", "Firebase Auth not available: ${e.message}")
        null
    }

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (_uiState.value is AuthUiState.DemoMode) return@AuthStateListener
        val user = firebaseAuth.currentUser
        if (user == null) {
            _uiState.value = AuthUiState.Unauthenticated
        } else {
            resolveUserStore(user)
        }
    }

    init {
        try {
            if (auth != null) {
                auth.addAuthStateListener(authListener)
            } else {
                _uiState.value = AuthUiState.Unauthenticated
            }
        } catch (e: Exception) {
            _uiState.value = AuthUiState.Unauthenticated
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            auth?.removeAuthStateListener(authListener)
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun launchDemoMode() {
        clearMessages()
        _uiState.value = AuthUiState.DemoMode()
    }

    fun resolveUserStore(user: FirebaseUser) {
        viewModelScope.launch {
            try {
                val profile = repository.getUserProfileOnce(user.uid)
                if (profile != null && profile.storeId.isNotBlank()) {
                    _uiState.value = AuthUiState.AuthenticatedWithStore(user, profile.storeId)
                } else {
                    _uiState.value = AuthUiState.AuthenticatedNoStore(user)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to resolve store for user ${user.uid}", e)
                _uiState.value = AuthUiState.AuthenticatedNoStore(user)
            }
        }
    }

    // -------------------------------------------------------------
    // GOOGLE SIGN-IN VIA CREDENTIAL MANAGER
    // -------------------------------------------------------------

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun signInWithGoogle(context: Context) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            _errorMessage.value = "Firebase services not configured. You can use 'Explore Demo Store' below."
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else ""
        }

        val activity = context.findActivity()
        if (activity == null) {
            _errorMessage.value = "Activity context is required for Google Sign-In"
            return
        }

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    firebaseAuth.signInWithCredential(authCredential).await()
                } else {
                    _errorMessage.value = "Unexpected credential format"
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthViewModel", "Google Sign-In dismissed: ${e.message}")
                _errorMessage.value = "Sign-in cancelled. Please select your Google account to continue."
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Google Sign-In failed", e)
                val msg = e.localizedMessage ?: "Google Sign-In failed"
                _errorMessage.value = msg
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // EMAIL & PASSWORD AUTHENTICATION
    // -------------------------------------------------------------

    fun signInWithEmail(email: String, pass: String) {
        _errorMessage.value = "Google Sign-In is the active authentication provider for this store. Please tap 'Sign in with Google' above or 'Explore Demo Store'."
    }

    fun signUpWithEmail(email: String, pass: String, confirmPass: String) {
        _errorMessage.value = "Google Sign-In is the active authentication provider for this store. Please tap 'Sign in with Google' above or 'Explore Demo Store'."
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _errorMessage.value = "Please enter your registered email address"
            return
        }

        val firebaseAuth = auth
        if (firebaseAuth == null) {
            _errorMessage.value = "Firebase services not configured."
            return
        }

        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                firebaseAuth.sendPasswordResetEmail(email.trim()).await()
                _successMessage.value = "Password reset email sent to $email"
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Reset password failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to send reset email"
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // STORE CREATION (FIRST LAUNCH ONBOARDING)
    // -------------------------------------------------------------

    fun createStore(name: String, phone: String, address: String) {
        if (name.isBlank() || phone.isBlank() || address.isBlank()) {
            _errorMessage.value = "Please fill in all store fields"
            return
        }

        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val newStoreId = repository.createStore(name, phone, address)
                val user = auth?.currentUser
                if (user != null) {
                    _uiState.value = AuthUiState.AuthenticatedWithStore(user, newStoreId)
                } else {
                    _uiState.value = AuthUiState.DemoMode(newStoreId)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Store creation failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to create store"
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // LOG OUT
    // -------------------------------------------------------------

    fun signOut(context: Context) {
        val credentialManager = CredentialManager.create(context)
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("AuthViewModel", "Failed to clear credential state", e)
                } finally {
                    try {
                        auth?.signOut()
                    } catch (e: Exception) {
                        Log.w("AuthViewModel", "Sign out note: ${e.message}")
                    }
                    _uiState.value = AuthUiState.Unauthenticated
                }
            }
        }
    }
}
