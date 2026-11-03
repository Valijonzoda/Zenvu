package com.worldtv.app.data

import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.Country
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.UserNotice
import org.json.JSONArray
import org.json.JSONObject

class DatasetException(
    message: String,
    cause: Throwable? = null,
    val notice: UserNotice? = null,
    val noticeArg: String? = null,
) : Exception(message, cause)

/**
 * Paths verified from https://github.com/Valijonzoda/famelack-data
 * Raw JSON is used. The compressed copies are gzip payloads with a .json name.
 */
object DatasetPaths {
    const val BASE = "https://raw.githubusercontent.com/Valijonzoda/famelack-data/main/"

    fun countriesMetadata(kind: ContentKind): String =
        "${kind.directory}/raw/countries_metadata.json"

    fun countryChannels(kind: ContentKind, code: String): String =
        "${kind.directory}/raw/countries/${code.lowercase()}.json"
}

object DatasetParser {
    fun parseCountries(json: String): List<Country> {
        val root = try {
            JSONObject(json.trim())
        } catch (error: Exception) {
            throw DatasetException("The country list could not be read.", error, UserNotice.COUNTRY_LIST)
        }
        val countries = ArrayList<Country>(root.length())
        val keys = root.keys()
        while (keys.hasNext()) {
            val code = keys.next()
            val item = root.optJSONObject(code) ?: continue
            countries += Country(
                code = code.lowercase(),
                name = item.optString("country", code).ifBlank { code },
                capital = item.optString("capital", ""),
                timeZone = item.optString("timeZone", ""),
                hasChannels = item.optBoolean("hasChannels", false),
                channelCount = item.optInt("channelCount", 0),
            )
        }
        return countries
    }

    fun parseChannels(json: String, kind: ContentKind): List<MediaEntry> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[")) {
            throw DatasetException("The channel list was not in the expected format.", notice = UserNotice.CHANNEL_FORMAT)
        }
        val array = try {
            JSONArray(trimmed)
        } catch (error: Exception) {
            throw DatasetException("The channel list could not be read.", error, UserNotice.CHANNEL_READ)
        }
        val entries = ArrayList<MediaEntry>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("nanoid").trim()
            val name = item.optString("name").trim()
            if (id.isEmpty() || name.isEmpty()) continue
            val sources = item.optJSONObject("sources")
            entries += MediaEntry(
                id = id,
                kind = kind,
                name = name,
                countryCode = item.optString("country").trim().lowercase(),
                languages = stringList(item, "languages"),
                geoBlocked = item.optBoolean("isGeoBlocked", false),
                streams = stringList(sources, "streams"),
                youtube = stringList(sources, "youtube"),
            )
        }
        return entries
    }

    private fun stringList(source: JSONObject?, key: String): List<String> {
        if (source == null) return emptyList()
        val array = source.optJSONArray(key) ?: return emptyList()
        val values = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            if (array.isNull(index)) continue
            val value = array.optString(index).trim()
            if (value.isNotEmpty() && value != "null") values += value
        }
        return values
    }
}
