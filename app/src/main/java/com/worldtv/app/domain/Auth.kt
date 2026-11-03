package com.worldtv.app.domain

import kotlinx.coroutines.flow.StateFlow

data class User(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val passwordProvider: Boolean = false,
)

enum class AuthError {
    INVALID_EMAIL,
    WRONG_PASSWORD,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    USER_NOT_FOUND,
    NETWORK,
    RECENT_LOGIN_REQUIRED,
    CANCELLED,
    DISABLED,
    UNKNOWN,
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

interface AuthRepository {
    val currentUser: StateFlow<User?>
    val accountsEnabled: Boolean
    val googleSignInAvailable: Boolean

    suspend fun signInEmail(email: String, password: String): AuthResult

    suspend fun signUpEmail(email: String, password: String): AuthResult

    suspend fun signInGoogle(): AuthResult

    suspend fun sendPasswordReset(email: String): AuthResult

    suspend fun signOut()

    suspend fun deleteAccount(password: String?): AuthResult
}

object AuthInput {
    private val emailPattern = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)

    fun validate(email: String, password: String?, register: Boolean): AuthError? {
        if (!emailPattern.matches(email.trim())) return AuthError.INVALID_EMAIL
        if (password == null) return null
        if (register && password.length < 8) return AuthError.WEAK_PASSWORD
        if (password.isEmpty()) return AuthError.WRONG_PASSWORD
        return null
    }

    fun fromCode(code: String?): AuthError {
        val normalized = code.orEmpty().removePrefix("ERROR_").lowercase().replace('_', '-')
        return when (normalized) {
            "invalid-email" -> AuthError.INVALID_EMAIL
            "wrong-password", "invalid-credential", "invalid-login-credentials" -> AuthError.WRONG_PASSWORD
            "email-already-in-use" -> AuthError.EMAIL_IN_USE
            "weak-password" -> AuthError.WEAK_PASSWORD
            "user-not-found" -> AuthError.USER_NOT_FOUND
            "network-request-failed" -> AuthError.NETWORK
            "requires-recent-login" -> AuthError.RECENT_LOGIN_REQUIRED
            "cancelled", "canceled", "web-context-canceled" -> AuthError.CANCELLED
            "disabled" -> AuthError.DISABLED
            else -> AuthError.UNKNOWN
        }
    }
}

object Accounts {
    const val DISABLED_MESSAGE =
        "Accounts are disabled: app/google-services.json was not present for this build. Sign-in is off until you add that file and rebuild."
    const val DELETION_URL =
        "https://valijonzoda.github.io/Zenvu/delete-account/"
}
