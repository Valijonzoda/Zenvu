package com.worldtv.app

import android.content.Context
import com.worldtv.app.data.AndroidNetworkStatus
import com.worldtv.app.data.CatalogRepositoryImpl
import com.worldtv.app.data.DataStoreSettingsRepository
import com.worldtv.app.data.FileFavoritesRepository
import com.worldtv.app.data.FileTextCache
import com.worldtv.app.data.OkHttpDatasetClient
import com.worldtv.app.domain.CatalogRepository
import com.worldtv.app.domain.FavoritesRepository
import com.worldtv.app.domain.SettingsRepository
import com.worldtv.app.playback.RadioPlaybackController
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val catalog: CatalogRepository = CatalogRepositoryImpl(
        client = OkHttpDatasetClient(baseUrl = BuildConfig.DATASET_BASE),
        cache = FileTextCache(File(appContext.filesDir, "catalog-cache")),
        online = AndroidNetworkStatus(appContext),
    )
    val favorites: FavoritesRepository = FileFavoritesRepository(
        File(appContext.filesDir, "favorites.json"),
    )
    val settings: SettingsRepository = DataStoreSettingsRepository(appContext)
    val queue = PlaybackQueue()
    val radio = RadioPlaybackController(appContext, settings)
}
