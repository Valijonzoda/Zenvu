package com.worldtv.app

import android.app.Activity
import android.content.Context
import com.worldtv.app.data.AccountFavoritesRepository
import com.worldtv.app.data.AndroidNetworkStatus
import com.worldtv.app.data.CatalogRepositoryImpl
import com.worldtv.app.data.CredentialGoogleSignIn
import com.worldtv.app.data.DataStoreSettingsRepository
import com.worldtv.app.data.DeviceFavoriteCache
import com.worldtv.app.data.DisabledAuthRepository
import com.worldtv.app.data.FavoriteCloud
import com.worldtv.app.data.FileFavoriteCache
import com.worldtv.app.data.FileTextCache
import com.worldtv.app.data.FirebaseAuthRepository
import com.worldtv.app.data.FirestoreFavoriteCloud
import com.worldtv.app.data.OkHttpDatasetClient
import com.worldtv.app.data.UnavailableFavoriteCloud
import com.worldtv.app.data.accountLog
import com.worldtv.app.domain.Accounts
import com.worldtv.app.domain.AuthRepository
import com.worldtv.app.domain.CatalogRepository
import com.worldtv.app.domain.SettingsRepository
import com.worldtv.app.playback.RadioPlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val googleSignIn = CredentialGoogleSignIn(appContext, BuildConfig.GOOGLE_WEB_CLIENT_ID)
    private val wiring = createWiring()
    private val favoriteCache = FileFavoriteCache(File(appContext.filesDir, "favorites.json"))

    val catalog: CatalogRepository = CatalogRepositoryImpl(
        client = OkHttpDatasetClient(baseUrl = BuildConfig.DATASET_BASE),
        cache = FileTextCache(File(appContext.filesDir, "catalog-cache")),
        online = AndroidNetworkStatus(appContext),
    )
    val auth: AuthRepository = wiring.auth
    val favorites: AccountFavoritesRepository = AccountFavoritesRepository(
        local = favoriteCache,
        legacy = favoriteCache,
        guest = DeviceFavoriteCache(File(appContext.filesDir, "favorites-device.json")),
        cloud = wiring.cloud,
        auth = auth,
    )
    val settings: SettingsRepository = DataStoreSettingsRepository(appContext)
    val queue = PlaybackQueue()
    val radio = RadioPlaybackController(appContext, settings)

    init {
        favorites.start(scope)
    }

    fun attachActivity(activity: Activity) = googleSignIn.attach(activity)

    fun detachActivity(activity: Activity?) = googleSignIn.detach(activity)

    private fun createWiring(): Wiring {
        if (!BuildConfig.ACCOUNTS_ENABLED) {
            accountLog(Accounts.DISABLED_MESSAGE)
            return Wiring(DisabledAuthRepository(), UnavailableFavoriteCloud())
        }
        return try {
            val cloud = FirestoreFavoriteCloud()
            Wiring(FirebaseAuthRepository(googleSignIn, cloud), cloud)
        } catch (error: Exception) {
            accountLog("Accounts are disabled: Firebase did not start (${error.javaClass.simpleName}).")
            Wiring(DisabledAuthRepository(), UnavailableFavoriteCloud())
        }
    }

    private data class Wiring(
        val auth: AuthRepository,
        val cloud: FavoriteCloud,
    )
}
