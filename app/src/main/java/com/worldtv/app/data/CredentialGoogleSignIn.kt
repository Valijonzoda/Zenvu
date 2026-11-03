package com.worldtv.app.data

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.lang.ref.WeakReference

sealed interface GoogleToken {
    data class Value(val token: String) : GoogleToken
    data object Cancelled : GoogleToken
    data object Unavailable : GoogleToken
}

class CredentialGoogleSignIn(
    context: Context,
    private val webClientId: String,
) {
    private val appContext = context.applicationContext
    private var activity = WeakReference<Activity>(null)

    val available: Boolean = supportsGoogleSignIn(appContext, webClientId)

    fun attach(host: Activity) {
        activity = WeakReference(host)
    }

    fun detach(host: Activity?) {
        if (host == null || activity.get() === host) activity.clear()
    }

    suspend fun requestToken(): GoogleToken {
        if (!available) return GoogleToken.Unavailable
        val host = activity.get() ?: return GoogleToken.Unavailable
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        return try {
            val result = CredentialManager.create(host).getCredential(host, request)
            if (result.credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleToken.Unavailable
            } else {
                val token = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                if (token.isBlank()) GoogleToken.Unavailable else GoogleToken.Value(token)
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleToken.Cancelled
        } catch (_: NoCredentialException) {
            GoogleToken.Cancelled
        } catch (_: GetCredentialProviderConfigurationException) {
            GoogleToken.Unavailable
        } catch (error: Exception) {
            accountLog("Google sign-in failed: ${error.javaClass.simpleName}")
            GoogleToken.Unavailable
        }
    }

    companion object {
        fun supportsGoogleSignIn(context: Context, webClientId: String): Boolean {
            if (webClientId.isBlank()) return false
            val television = context.getSystemService(UiModeManager::class.java)
                ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            if (!television) return true
            return Build.VERSION.SDK_INT >= 34 &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_CREDENTIALS)
        }
    }
}
