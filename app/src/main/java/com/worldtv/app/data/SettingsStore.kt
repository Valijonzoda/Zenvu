package com.worldtv.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.SettingsRepository
import com.worldtv.app.domain.ThemeChoice
import com.worldtv.app.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "world_tv_settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val store = context.applicationContext.settingsDataStore

    override val settings: Flow<UserSettings> = store.data.map { prefs ->
        UserSettings(
            theme = prefs[Keys.THEME]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() }
                ?: ThemeChoice.SYSTEM,
            autoFallbackStreams = prefs[Keys.AUTO_FALLBACK] ?: true,
            backgroundRadio = prefs[Keys.BACKGROUND_RADIO] ?: true,
        )
    }

    override suspend fun setTheme(theme: ThemeChoice) {
        store.edit { it[Keys.THEME] = theme.name }
    }

    override suspend fun setAutoFallback(enabled: Boolean) {
        store.edit { it[Keys.AUTO_FALLBACK] = enabled }
    }

    override suspend fun setBackgroundRadio(enabled: Boolean) {
        store.edit { it[Keys.BACKGROUND_RADIO] = enabled }
    }

    override suspend fun selectedCountries(kind: ContentKind): Set<String> {
        val prefs = store.data.first()
        return prefs[Keys.countries(kind)].orEmpty()
    }

    override suspend fun setSelectedCountries(kind: ContentKind, codes: Set<String>) {
        store.edit { prefs ->
            prefs[Keys.countries(kind)] = codes.map { it.lowercase() }.toSet()
        }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val AUTO_FALLBACK = booleanPreferencesKey("auto_fallback")
        val BACKGROUND_RADIO = booleanPreferencesKey("background_radio")

        fun countries(kind: ContentKind) = stringSetPreferencesKey("countries_${kind.name}")
    }
}
