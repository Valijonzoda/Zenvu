package com.worldtv.app.data

import com.worldtv.app.domain.AuthError
import com.worldtv.app.domain.AuthRepository
import com.worldtv.app.domain.AuthResult
import com.worldtv.app.domain.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DisabledAuthRepository : AuthRepository {
    override val currentUser: StateFlow<User?> = MutableStateFlow(null)
    override val accountsEnabled: Boolean = false
    override val googleSignInAvailable: Boolean = false

    override suspend fun signInEmail(email: String, password: String): AuthResult = disabled()

    override suspend fun signUpEmail(email: String, password: String): AuthResult = disabled()

    override suspend fun signInGoogle(): AuthResult = disabled()

    override suspend fun sendPasswordReset(email: String): AuthResult = disabled()

    override suspend fun signOut() = Unit

    override suspend fun deleteAccount(password: String?): AuthResult = disabled()

    private fun disabled() = AuthResult.Failure(AuthError.DISABLED)
}
