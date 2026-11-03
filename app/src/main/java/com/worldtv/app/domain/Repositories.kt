package com.worldtv.app.domain

import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    suspend fun loadCountries(kind: ContentKind, force: Boolean): CatalogResult<List<Country>>

    suspend fun loadChannels(
        kind: ContentKind,
        countryCodes: Set<String>,
        force: Boolean,
    ): CatalogResult<List<MediaEntry>>

    suspend fun clearCache()
}

interface FavoritesRepository {
    val favorites: Flow<List<MediaEntry>>

    suspend fun toggle(entry: MediaEntry): FavoriteResult

    suspend fun add(entry: MediaEntry): FavoriteResult

    suspend fun isFavorite(key: String): Boolean
}

sealed interface FavoriteResult {
    data object Changed : FavoriteResult
    data object AuthRequired : FavoriteResult
}

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setTheme(theme: ThemeChoice)

    suspend fun setAutoFallback(enabled: Boolean)

    suspend fun setBackgroundRadio(enabled: Boolean)

    suspend fun selectedCountries(kind: ContentKind): Set<String>

    suspend fun setSelectedCountries(kind: ContentKind, codes: Set<String>)
}
