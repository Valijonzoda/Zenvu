package com.worldtv.app.domain

object CatalogQuery {
    fun filter(
        entries: List<MediaEntry>,
        nameQuery: String,
        languageCode: String?,
        favoritesOnly: Boolean,
        favoriteKeys: Set<String>,
    ): List<MediaEntry> {
        val query = nameQuery.trim()
        return entries.filter { entry ->
            val nameMatches = query.isEmpty() || entry.name.contains(query, ignoreCase = true)
            val languageMatches = languageCode == null ||
                entry.languages.any { it.equals(languageCode, ignoreCase = true) }
            val favoriteMatches = !favoritesOnly || entry.key in favoriteKeys
            nameMatches && languageMatches && favoriteMatches
        }
    }
}

object CountrySelection {
    fun choose(
        saved: Set<String>,
        countries: List<Country>,
        localeCountry: String,
    ): Set<String> {
        val byCode = countries.associateBy { it.code.lowercase() }
        val validSaved = saved.map { it.lowercase() }.filter { it in byCode }.toSet()
        if (validSaved.isNotEmpty()) return validSaved

        val locale = localeCountry.lowercase()
        val preferred = when {
            locale == "gb" && byCode["uk"]?.hasChannels == true -> "uk"
            byCode[locale]?.hasChannels == true -> locale
            byCode["us"]?.hasChannels == true -> "us"
            else -> countries.firstOrNull { it.hasChannels }?.code ?: countries.firstOrNull()?.code
        }
        return preferred?.let { setOf(it) } ?: emptySet()
    }

    fun matches(country: Country, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return country.name.contains(needle, ignoreCase = true) ||
            country.code.contains(needle, ignoreCase = true) ||
            country.capital.contains(needle, ignoreCase = true)
    }
}
