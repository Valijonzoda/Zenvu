package com.worldtv.app.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.app.domain.CatalogQuery
import com.worldtv.app.domain.CatalogRepository
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.Country
import com.worldtv.app.domain.CountrySelection
import com.worldtv.app.domain.FavoriteResult
import com.worldtv.app.domain.FavoritesRepository
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class CatalogUiState(
    val kind: ContentKind = ContentKind.TV,
    val countries: List<Country> = emptyList(),
    val countryQuery: String = "",
    val selectedCodes: Set<String> = emptySet(),
    val channels: List<MediaEntry> = emptyList(),
    val channelQuery: String = "",
    val language: String? = null,
    val favoritesOnly: Boolean = false,
    val loadingCountries: Boolean = true,
    val loadingChannels: Boolean = false,
    val countriesError: String? = null,
    val channelsError: String? = null,
    val notice: String? = null,
    val offline: Boolean = false,
    val fromCache: Boolean = false,
    val favoriteKeys: Set<String> = emptySet(),
    val favorites: List<MediaEntry> = emptyList(),
) {
    val visibleCountries: List<Country>
        get() = countries.filter { CountrySelection.matches(it, countryQuery) }

    val languages: List<String>
        get() = channels.asSequence()
            .flatMap { it.languages.asSequence() }
            .map { it.lowercase() }
            .distinct()
            .sorted()
            .toList()

    val visibleChannels: List<MediaEntry>
        get() = CatalogQuery.filter(channels, channelQuery, language, favoritesOnly, favoriteKeys)

    fun countryName(code: String): String =
        countries.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name ?: code.uppercase()
}

class CatalogViewModel(
    private val catalog: CatalogRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val stateFlow = kotlinx.coroutines.flow.MutableStateFlow(CatalogUiState())
    val state = stateFlow
    private val authPrompts = MutableSharedFlow<MediaEntry>(extraBufferCapacity = 1)
    val favoriteAuthPrompt: SharedFlow<MediaEntry> = authPrompts.asSharedFlow()

    private var generation = 0
    private var channelJob: Job? = null

    init {
        viewModelScope.launch {
            favoritesRepository.favorites.collect { entries ->
                stateFlow.value = stateFlow.value.copy(
                    favorites = entries,
                    favoriteKeys = entries.map { it.key }.toSet(),
                )
            }
        }
        val initial = nextGeneration()
        viewModelScope.launch { loadKind(ContentKind.TV, force = false, generation = initial) }
    }

    fun selectKind(kind: ContentKind) {
        if (stateFlow.value.kind == kind) return
        channelJob?.cancel()
        val generation = nextGeneration()
        stateFlow.value = stateFlow.value.copy(
            kind = kind,
            countries = emptyList(),
            channels = emptyList(),
            channelQuery = "",
            language = null,
            favoritesOnly = false,
            loadingCountries = true,
            loadingChannels = true,
            countriesError = null,
            channelsError = null,
            notice = null,
        )
        viewModelScope.launch { loadKind(kind, force = false, generation = generation) }
    }

    fun setChannelQuery(query: String) {
        stateFlow.value = stateFlow.value.copy(channelQuery = query)
    }

    fun setCountryQuery(query: String) {
        stateFlow.value = stateFlow.value.copy(countryQuery = query)
    }

    fun setLanguage(language: String?) {
        stateFlow.value = stateFlow.value.copy(language = language)
    }

    fun setFavoritesOnly(enabled: Boolean) {
        stateFlow.value = stateFlow.value.copy(favoritesOnly = enabled)
    }

    fun setCountries(codes: Set<String>) {
        val normalized = codes.map { it.lowercase() }.toSet()
        val generation = stateGeneration()
        stateFlow.value = stateFlow.value.copy(
            selectedCodes = normalized,
            language = null,
            loadingChannels = true,
            channelsError = null,
        )
        viewModelScope.launch {
            settings.setSelectedCountries(stateFlow.value.kind, normalized)
        }
        loadChannels(stateFlow.value.kind, normalized, force = false, generation = generation)
    }

    fun refresh() {
        channelJob?.cancel()
        val generation = nextGeneration()
        val kind = stateFlow.value.kind
        stateFlow.value = stateFlow.value.copy(loadingCountries = true, loadingChannels = true, notice = null)
        viewModelScope.launch { loadKind(kind, force = true, generation = generation) }
    }

    fun toggleFavorite(entry: MediaEntry) {
        viewModelScope.launch {
            if (favoritesRepository.toggle(entry) == FavoriteResult.AuthRequired) {
                authPrompts.emit(entry)
            }
        }
    }

    private suspend fun loadKind(kind: ContentKind, force: Boolean, generation: Int) {
        val countriesResult = catalog.loadCountries(kind, force)
        if (!isCurrent(generation)) return
        val countries = countriesResult.value.orEmpty().sortedBy { it.name.lowercase() }
        val saved = settings.selectedCountries(kind)
        val selected = CountrySelection.choose(saved, countries, Locale.getDefault().country)
        if (selected != saved) settings.setSelectedCountries(kind, selected)
        if (!isCurrent(generation)) return
        stateFlow.value = stateFlow.value.copy(
            kind = kind,
            countries = countries,
            selectedCodes = selected,
            loadingCountries = false,
            countriesError = if (countries.isEmpty()) countriesResult.errorMessage else null,
            notice = if (countries.isNotEmpty()) countriesResult.errorMessage else null,
            offline = countriesResult.offline,
            fromCache = countriesResult.fromCache,
        )
        loadChannels(kind, selected, force, generation)
    }

    private fun loadChannels(
        kind: ContentKind,
        codes: Set<String>,
        force: Boolean,
        generation: Int,
    ) {
        channelJob?.cancel()
        if (codes.isEmpty()) {
            stateFlow.value = stateFlow.value.copy(channels = emptyList(), loadingChannels = false, channelsError = null)
            return
        }
        channelJob = viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(loadingChannels = true)
            val result = catalog.loadChannels(kind, codes, force)
            if (!isCurrent(generation) || stateFlow.value.kind != kind) return@launch
            val channels = result.value.orEmpty()
            val language = stateFlow.value.language
            val languageStillValid = language == null || channels.any { entry ->
                entry.languages.any { it.equals(language, ignoreCase = true) }
            }
            stateFlow.value = stateFlow.value.copy(
                channels = channels,
                loadingChannels = false,
                channelsError = if (channels.isEmpty()) result.errorMessage else null,
                notice = result.errorMessage ?: stateFlow.value.notice,
                offline = stateFlow.value.offline || result.offline,
                fromCache = stateFlow.value.fromCache || result.fromCache,
                language = if (languageStillValid) language else null,
            )
        }
    }

    private fun nextGeneration(): Int {
        generation += 1
        return generation
    }

    private fun stateGeneration(): Int = generation

    private fun isCurrent(generation: Int): Boolean = generation == this.generation
}
