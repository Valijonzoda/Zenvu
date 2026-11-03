package com.worldtv.app.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.app.data.AccountFavoritesRepository
import com.worldtv.app.domain.AuthError
import com.worldtv.app.domain.AuthRepository
import com.worldtv.app.domain.AuthResult
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.User
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class AuthScreen { SIGN_IN, REGISTER }

enum class AuthBusy { SUBMIT, GOOGLE, RESET }

class AccountViewModel(
    private val auth: AuthRepository,
    private val favorites: AccountFavoritesRepository,
) : ViewModel() {
    val user: StateFlow<User?> = auth.currentUser
    val accountsEnabled: Boolean = auth.accountsEnabled
    val googleSignInAvailable: Boolean = auth.googleSignInAvailable

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var registerMode by mutableStateOf(false)
    var screen by mutableStateOf<AuthScreen?>(null)
        private set
    var busy by mutableStateOf<AuthBusy?>(null)
        private set
    var error by mutableStateOf<AuthError?>(null)
        private set
    var resetSent by mutableStateOf(false)
        private set
    var pendingFavorite by mutableStateOf<MediaEntry?>(null)
        private set
    var deleteOpen by mutableStateOf(false)
        private set
    var needsPassword by mutableStateOf(false)
        private set
    var deletePassword by mutableStateOf("")
    var deleteError by mutableStateOf<AuthError?>(null)
        private set
    var deleting by mutableStateOf(false)
        private set

    fun prompt(entry: MediaEntry) {
        pendingFavorite = entry
    }

    fun dismissPrompt() {
        pendingFavorite = null
    }

    fun consumePending() {
        pendingFavorite = null
    }

    fun openSignIn() {
        registerMode = false
        error = null
        resetSent = false
        screen = AuthScreen.SIGN_IN
    }

    fun openRegister() {
        registerMode = true
        error = null
        resetSent = false
        screen = AuthScreen.REGISTER
    }

    fun closeScreen() {
        screen = null
    }

    fun switchMode() {
        registerMode = !registerMode
        error = null
        resetSent = false
        screen = if (registerMode) AuthScreen.REGISTER else AuthScreen.SIGN_IN
    }

    fun submit() {
        if (busy != null) return
        viewModelScope.launch {
            busy = AuthBusy.SUBMIT
            error = null
            resetSent = false
            val result = if (registerMode) {
                auth.signUpEmail(email, password)
            } else {
                auth.signInEmail(email, password)
            }
            busy = null
            when (result) {
                is AuthResult.Success -> screen = null
                is AuthResult.Failure -> if (result.error != AuthError.CANCELLED) error = result.error
            }
        }
    }

    fun signInWithGoogle() {
        if (busy != null || !googleSignInAvailable) return
        viewModelScope.launch {
            busy = AuthBusy.GOOGLE
            error = null
            when (val result = auth.signInGoogle()) {
                is AuthResult.Success -> screen = null
                is AuthResult.Failure -> if (result.error != AuthError.CANCELLED) error = result.error
            }
            busy = null
        }
    }

    fun sendReset() {
        if (busy != null) return
        viewModelScope.launch {
            busy = AuthBusy.RESET
            error = null
            resetSent = false
            when (val result = auth.sendPasswordReset(email)) {
                is AuthResult.Success -> resetSent = true
                is AuthResult.Failure -> error = result.error
            }
            busy = null
        }
    }

    fun savePending(entry: MediaEntry) {
        viewModelScope.launch { favorites.add(entry) }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            screen = null
            pendingFavorite = null
            password = ""
        }
    }

    fun startDelete() {
        deletePassword = ""
        deleteError = null
        needsPassword = false
        deleteOpen = true
    }

    fun cancelDelete() {
        if (deleting) return
        deleteOpen = false
    }

    fun confirmDelete() {
        if (deleting) return
        if (needsPassword && deletePassword.isEmpty()) {
            deleteError = AuthError.WRONG_PASSWORD
            return
        }
        viewModelScope.launch {
            deleting = true
            deleteError = null
            var result = auth.deleteAccount(if (needsPassword) deletePassword else null)
            val current = auth.currentUser.value
            if (result is AuthResult.Failure &&
                result.error == AuthError.RECENT_LOGIN_REQUIRED &&
                !needsPassword &&
                current?.passwordProvider == false &&
                googleSignInAvailable
            ) {
                when (val google = auth.signInGoogle()) {
                    is AuthResult.Success -> result = auth.deleteAccount(null)
                    is AuthResult.Failure -> {
                        if (google.error != AuthError.CANCELLED) deleteError = google.error
                        deleting = false
                        return@launch
                    }
                }
            }
            when (val finalResult = result) {
                is AuthResult.Success -> {
                    deleteOpen = false
                    needsPassword = false
                    password = ""
                }
                is AuthResult.Failure -> {
                    if (finalResult.error == AuthError.RECENT_LOGIN_REQUIRED && !needsPassword) {
                        needsPassword = true
                    } else if (finalResult.error != AuthError.CANCELLED) {
                        deleteError = finalResult.error
                    }
                }
            }
            deleting = false
        }
    }
}
