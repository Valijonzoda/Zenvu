package com.worldtv.app.data

import com.worldtv.app.domain.CatalogRepository
import com.worldtv.app.domain.CatalogResult
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.Country
import com.worldtv.app.domain.MediaEntry

class CatalogRepositoryImpl(
    private val client: DatasetClient,
    private val cache: TextCache,
    private val online: () -> Boolean,
) : CatalogRepository {
    override suspend fun loadCountries(kind: ContentKind, force: Boolean): CatalogResult<List<Country>> =
        loadParsed(DatasetPaths.countriesMetadata(kind), force) { body ->
            DatasetParser.parseCountries(body)
        }

    override suspend fun loadChannels(
        kind: ContentKind,
        countryCodes: Set<String>,
        force: Boolean,
    ): CatalogResult<List<MediaEntry>> {
        if (countryCodes.isEmpty()) {
            return CatalogResult(emptyList(), fromCache = false, offline = !online(), errorMessage = null)
        }
        val merged = mutableListOf<MediaEntry>()
        val errors = mutableListOf<String>()
        var fromCache = false
        var offline = false
        for (code in countryCodes.map { it.lowercase() }.distinct()) {
            val result = loadParsed(DatasetPaths.countryChannels(kind, code), force) { body ->
                DatasetParser.parseChannels(body, kind)
            }
            fromCache = fromCache || result.fromCache
            offline = offline || result.offline
            val value = result.value
            if (value != null) {
                merged += value
            } else if (result.errorMessage != null) {
                errors += result.errorMessage
            }
        }
        val message = errors.distinct().joinToString(" ").ifBlank { null }
        val deduped = merged.distinctBy { it.key }
        return if (deduped.isEmpty() && message != null) {
            CatalogResult(null, fromCache, offline, message)
        } else {
            CatalogResult(deduped, fromCache, offline, message)
        }
    }

    override suspend fun clearCache() {
        cache.clear()
    }

    private suspend fun <T> loadParsed(
        path: String,
        force: Boolean,
        parse: (String) -> T,
    ): CatalogResult<T> {
        if (!force && !online()) {
            return readCached(path, offline = true, parse)
        }
        return try {
            val body = client.fetchText(path)
            val parsed = parse(body)
            cache.write(path, body)
            CatalogResult(parsed, fromCache = false, offline = false, errorMessage = null)
        } catch (error: Exception) {
            val cached = readCached(path, offline = !online(), parse)
            if (cached.value != null) {
                cached.copy(errorMessage = error.friendly())
            } else {
                CatalogResult(null, fromCache = false, offline = !online(), errorMessage = error.friendly())
            }
        }
    }

    private fun <T> readCached(
        path: String,
        offline: Boolean,
        parse: (String) -> T,
    ): CatalogResult<T> {
        val cached = cache.read(path) ?: return CatalogResult(
            value = null,
            fromCache = false,
            offline = offline,
            errorMessage = if (offline) {
                "You're offline, and this list hasn't been saved on the device yet."
            } else {
                "No saved copy of this list is available."
            },
        )
        return try {
            CatalogResult(parse(cached.body), fromCache = true, offline = offline, errorMessage = null)
        } catch (error: Exception) {
            CatalogResult(
                value = null,
                fromCache = true,
                offline = offline,
                errorMessage = "A saved listing could not be read. Clear saved listings in Settings and try again.",
            )
        }
    }

    private fun Exception.friendly(): String = when (this) {
        is DatasetException -> message ?: "Couldn't load the dataset."
        else -> message?.takeIf { it.isNotBlank() } ?: "Couldn't read the dataset."
    }
}
