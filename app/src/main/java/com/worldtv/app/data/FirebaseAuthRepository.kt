package com.worldtv.app.data

import com.worldtv.app.domain.AuthError
import com.worldtv.app.domain.AuthInput
import com.worldtv.app.domain.AuthRepository
import com.worldtv.app.domain.AuthResult
import com.worldtv.app.domain.User
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.IOException

class FirebaseAuthRepository(
    private val google: CredentialGoogleSignIn,
    private val cloud: FavoriteCloud,
) : AuthRepository {
    private val firebase = FirebaseAuth.getInstance()
    private val state = MutableStateFlow(firebase.currentUser?.toDomain())

    override val currentUser: StateFlow<User?> = state.asStateFlow()
    override val accountsEnabled: Boolean = true
    override val googleSignInAvailable: Boolean = google.available

    init {
        firebase.addAuthStateListener { state.value = it.currentUser?.toDomain() }
    }

    override suspend fun signInEmail(email: String, password: String): AuthResult {
        val trimmed = email.trim()
        AuthInput.validate(trimmed, password, register = false)?.let { return AuthResult.Failure(it) }
        val failure = runAuth { firebase.signInWithEmailAndPassword(trimmed, password).await() }
        if (failure != null) return failure
        publish()
        return AuthResult.Success
    }

    override suspend fun signUpEmail(email: String, password: String): AuthResult {
        val trimmed = email.trim()
        AuthInput.validate(trimmed, password, register = true)?.let { return AuthResult.Failure(it) }
        val failure = runAuth { firebase.createUserWithEmailAndPassword(trimmed, password).await() }
        if (failure != null) return failure
        publish()
        return AuthResult.Success
    }

    override suspend fun signInGoogle(): AuthResult {
        if (!googleSignInAvailable) return AuthResult.Failure(AuthError.UNKNOWN)
        return when (val token = google.requestToken()) {
            is GoogleToken.Value -> {
                val credential = GoogleAuthProvider.getCredential(token.token, null)
                val failure = runAuth { firebase.signInWithCredential(credential).await() }
                if (failure != null) return failure
                publish()
                AuthResult.Success
            }
            GoogleToken.Cancelled -> AuthResult.Failure(AuthError.CANCELLED)
            GoogleToken.Unavailable -> AuthResult.Failure(AuthError.UNKNOWN)
        }
    }

    override suspend fun sendPasswordReset(email: String): AuthResult {
        val trimmed = email.trim()
        AuthInput.validate(trimmed, password = null, register = false)?.let { return AuthResult.Failure(it) }
        val failure = runAuth { firebase.sendPasswordResetEmail(trimmed).await() }
        if (failure != null) return failure
        return AuthResult.Success
    }

    override suspend fun signOut() {
        firebase.signOut()
        state.value = null
    }

    override suspend fun deleteAccount(password: String?): AuthResult {
        val firebaseUser = firebase.currentUser ?: return AuthResult.Failure(AuthError.USER_NOT_FOUND)
        if (!password.isNullOrEmpty()) {
            val email = firebaseUser.email ?: return AuthResult.Failure(AuthError.WRONG_PASSWORD)
            val credential = EmailAuthProvider.getCredential(email, password)
            val failure = runAuth { firebaseUser.reauthenticate(credential).await() }
            if (failure != null) return failure
        }
        val uid = firebaseUser.uid
        try {
            cloud.deleteAll(uid)
        } catch (error: Exception) {
            accountLog("delete favorites failed: ${error.javaClass.simpleName}")
            val network = generateSequence<Throwable>(error) { it.cause }.any { it is IOException }
            return AuthResult.Failure(if (network) AuthError.NETWORK else AuthError.UNKNOWN)
        }
        val failure = runAuth { firebaseUser.delete().await() }
        if (failure != null) return failure
        state.value = null
        return AuthResult.Success
    }

    private fun publish() {
        state.value = firebase.currentUser?.toDomain()
    }

    private inline fun runAuth(block: () -> Unit): AuthResult? = try {
        block()
        null
    } catch (error: Exception) {
        accountLog("auth failed: ${error.javaClass.simpleName}")
        AuthResult.Failure(mapAuth(error))
    }
}

private fun FirebaseUser.toDomain(): User = User(
    uid = uid,
    email = email,
    displayName = displayName?.takeIf { it.isNotBlank() },
    passwordProvider = providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID },
)

private fun mapAuth(error: Throwable): AuthError {
    var current: Throwable? = error
    while (current != null) {
        if (current is FirebaseAuthException) return AuthInput.fromCode(current.errorCode)
        if (current is FirebaseNetworkException || current is IOException) return AuthError.NETWORK
        current = current.cause
    }
    return AuthError.UNKNOWN
}
