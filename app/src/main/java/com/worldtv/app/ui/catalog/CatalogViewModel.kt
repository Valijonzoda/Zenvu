package com.worldtv.app.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.app.data.FavoriteCountryStore
import com.worldtv.app.domain.CatalogQuery
import com.worldtv.app.domain.CatalogRepository
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.Country
import com.worldtv.app.domain.CountrySelection
import com.worldtv.app.domain.FavoriteResult
import com.worldtv.app.domain.FavoritesRepository
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.SettingsRepository
import com.worldtv.app.domain.UserNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
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
    val countriesError: UserNotice? = null,
    val channelsError: UserNotice? = null,
    val notice: UserNotice? = null,
    val noticeArg: String? = null,
    val offline: Boolean = false,
    val fromCache: Boolean = false,
    val favoriteKeys: Set<String> = emptySet(),
    val favorites: List<MediaEntry> = emptyList(),
    val catalogChannels: List<MediaEntry> = emptyList(),
    val catalogKind: ContentKind? = null,
    val loadingCatalog: Boolean = false,
    val catalogError: UserNotice? = null,
    val searchHits: List<MediaEntry> = emptyList(),
    val allQuery: String = "",
    val allHits: List<MediaEntry> = emptyList(),
    val favoriteCountryKeys: Set<String> = emptySet(),
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

    fun countryName(code: String): String {
        val fallback = countries.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name ?: code.uppercase()
        return CountrySelection.displayName(code, fallback)
    }
}

class CatalogViewModel(
    private val catalog: CatalogRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settings: SettingsRepository,
    private val favoriteCountries: FavoriteCountryStore,
) : ViewModel() {
    private val stateFlow = kotlinx.coroutines.flow.MutableStateFlow(CatalogUiState())
    val state = stateFlow
    private val authPrompts = MutableSharedFlow<MediaEntry>(extraBufferCapacity = 1)
    val favoriteAuthPrompt: SharedFlow<MediaEntry> = authPrompts.asSharedFlow()

    private var generation = 0
    private var channelJob: Job? = null
    private var catalogJob: Job? = null
    private var searchJob: Job? = null
    private var allSearchJob: Job? = null
    private var pendingCountry: String? = null
    var countryListIndex: Int = 0
    var countryListOffset: Int = 0
    var returnCountryCode: String? = null
    private val channelScroll = HashMap<String, Pair<Int, Int>>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = favoriteCountries.read()
            stateFlow.value = stateFlow.value.copy(favoriteCountryKeys = saved)
        }
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
        catalogJob?.cancel()
        searchJob?.cancel()
        allSearchJob?.cancel()
        countryListIndex = 0
        countryListOffset = 0
        returnCountryCode = null
        val generation = nextGeneration()
        val query = stateFlow.value.countryQuery
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
            catalogChannels = emptyList(),
            catalogKind = null,
            loadingCatalog = false,
            catalogError = null,
            searchHits = emptyList(),
            allHits = emptyList(),
        )
        viewModelScope.launch { loadKind(kind, force = false, generation = generation) }
        if (query.trim().length >= 2) ensureCatalog(kind)
    }

    fun setChannelQuery(query: String) {
        stateFlow.value = stateFlow.value.copy(channelQuery = query)
    }

    fun setCountryQuery(query: String) {
        val current = stateFlow.value
        stateFlow.value = current.copy(countryQuery = query)
        if (query.trim().length >= 2) ensureCatalog(current.kind)
        publishSearch(current.catalogChannels, query, current.kind)
    }

    fun setLanguage(language: String?) {
        stateFlow.value = stateFlow.value.copy(language = language)
    }

    fun setFavoritesOnly(enabled: Boolean) {
        stateFlow.value = stateFlow.value.copy(favoritesOnly = enabled)
    }

    fun setAllQuery(query: String) {
        val current = stateFlow.value
        stateFlow.value = current.copy(allQuery = query)
        publishAll(current.catalogChannels, query, current.kind)
    }

    fun prepareCatalog() {
        ensureCatalog(stateFlow.value.kind)
    }

    fun reloadCatalog() {
        ensureCatalog(stateFlow.value.kind, force = true)
    }

    fun rememberCountry(code: String) {
        returnCountryCode = code.lowercase()
    }

    fun consumeReturnCountry() {
        returnCountryCode = null
    }

    fun channelScroll(code: String): Pair<Int, Int> = channelScroll[code.lowercase()] ?: (0 to 0)

    fun saveChannelScroll(code: String, index: Int, offset: Int) {
        if (code.isBlank()) return
        channelScroll[code.lowercase()] = index to offset
    }

    fun openCountry(kind: ContentKind, code: String) {
        val normalized = code.lowercase()
        returnCountryCode = normalized
        if (stateFlow.value.kind == kind) {
            setCountries(setOf(normalized))
            return
        }
        pendingCountry = normalized
        selectKind(kind)
        returnCountryCode = normalized
    }

    fun toggleCountry(kind: ContentKind, code: String) {
        val key = "${kind.name}:${code.lowercase()}"
        val current = stateFlow.value.favoriteCountryKeys
        val next = if (key in current) current - key else current + key
        stateFlow.value = stateFlow.value.copy(favoriteCountryKeys = next)
        viewModelScope.launch(Dispatchers.IO) { favoriteCountries.write(next) }
    }

    suspend fun randomEntry(): MediaEntry? {
        val kind = stateFlow.value.kind
        val ready = stateFlow.value.catalogChannels.takeIf {
            stateFlow.value.catalogKind == kind && it.isNotEmpty()
        }
        val pool = ready ?: loadCatalogNow(kind)
        return pool.randomOrNull()
    }

    fun setCountries(codes: Set<String>) {
        val normalized = codes.map { it.lowercase() }.toSet()
        val generation = stateGeneration()
        stateFlow.value = stateFlow.value.copy(
            selectedCodes = normalized,
            language = null,
            channelQuery = "",
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
        catalogJob?.cancel()
        val generation = nextGeneration()
        val kind = stateFlow.value.kind
        val reloadCatalog = stateFlow.value.countryQuery.trim().length >= 2 || stateFlow.value.catalogChannels.isNotEmpty()
        stateFlow.value = stateFlow.value.copy(loadingCountries = true, loadingChannels = true, notice = null)
        viewModelScope.launch { loadKind(kind, force = true, generation = generation) }
        if (reloadCatalog) ensureCatalog(kind, force = true)
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
        val countries = CountrySelection.withListings(countriesResult.value.orEmpty())
            .sortedBy { it.name.lowercase() }
        val saved = settings.selectedCountries(kind)
        val pending = pendingCountry
        pendingCountry = null
        val selected = if (pending != null && countries.any { it.code.equals(pending, ignoreCase = true) }) {
            setOf(pending)
        } else {
            CountrySelection.choose(saved, countries, Locale.getDefault().country)
        }
        if (selected != saved) settings.setSelectedCountries(kind, selected)
        if (!isCurrent(generation)) return
        stateFlow.value = stateFlow.value.copy(
            kind = kind,
            countries = countries,
            selectedCodes = selected,
            loadingCountries = false,
            countriesError = if (countries.isEmpty()) countriesResult.notice else null,
            notice = if (countries.isNotEmpty()) countriesResult.notice else null,
            noticeArg = countriesResult.noticeArg,
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
                channelsError = if (channels.isEmpty()) result.notice else null,
                notice = result.notice ?: stateFlow.value.notice,
                noticeArg = result.noticeArg ?: stateFlow.value.noticeArg,
                offline = stateFlow.value.offline || result.offline,
                fromCache = stateFlow.value.fromCache || result.fromCache,
                language = if (languageStillValid) language else null,
            )
        }
    }

    private fun ensureCatalog(kind: ContentKind, force: Boolean = false) {
        val current = stateFlow.value
        if (!force && current.catalogKind == kind && (current.catalogChannels.isNotEmpty() || current.catalogError != null || current.loadingCatalog)) {
            return
        }
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(loadingCatalog = true, catalogKind = kind, catalogError = null)
            val result = catalog.loadAllChannels(kind, force)
            if (stateFlow.value.kind != kind) return@launch
            val channels = result.value
            val loaded = channels ?: stateFlow.value.catalogChannels
            val query = stateFlow.value.countryQuery
            val allQuery = stateFlow.value.allQuery
            val hits = withContext(Dispatchers.Default) { CatalogQuery.search(loaded, query) }
            val allHits = withContext(Dispatchers.Default) { filterAll(loaded, allQuery) }
            if (stateFlow.value.kind != kind || stateFlow.value.countryQuery != query) return@launch
            stateFlow.value = stateFlow.value.copy(
                catalogKind = kind,
                catalogChannels = loaded,
                loadingCatalog = false,
                catalogError = if (channels == null) result.notice else null,
                searchHits = hits,
                allHits = allHits,
            )
        }
    }

    private suspend fun loadCatalogNow(kind: ContentKind): List<MediaEntry> {
        stateFlow.value = stateFlow.value.copy(loadingCatalog = true, catalogKind = kind, catalogError = null)
        val result = catalog.loadAllChannels(kind, false)
        if (stateFlow.value.kind != kind) return emptyList()
        val loaded = result.value ?: stateFlow.value.catalogChannels
        stateFlow.value = stateFlow.value.copy(
            catalogKind = kind,
            catalogChannels = loaded,
            loadingCatalog = false,
            catalogError = if (result.value == null) result.notice else null,
            allHits = filterAll(loaded, stateFlow.value.allQuery),
        )
        return loaded
    }

    private fun publishAll(entries: List<MediaEntry>, query: String, kind: ContentKind) {
        allSearchJob?.cancel()
        allSearchJob = viewModelScope.launch {
            val hits = withContext(Dispatchers.Default) { filterAll(entries, query) }
            val current = stateFlow.value
            if (current.kind == kind && current.allQuery == query) {
                stateFlow.value = current.copy(allHits = hits)
            }
        }
    }

    private fun filterAll(entries: List<MediaEntry>, query: String): List<MediaEntry> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return entries.filter { it.name.contains(needle, ignoreCase = true) }
    }

    private fun publishSearch(entries: List<MediaEntry>, query: String, kind: ContentKind) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val hits = withContext(Dispatchers.Default) { CatalogQuery.search(entries, query) }
            val current = stateFlow.value
            if (current.kind == kind && current.countryQuery == query) {
                stateFlow.value = current.copy(searchHits = hits)
            }
        }
    }

    private fun nextGeneration(): Int {
        generation += 1
        return generation
    }

    private fun stateGeneration(): Int = generation

    private fun isCurrent(generation: Int): Boolean = generation == this.generation
}
