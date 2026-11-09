package com.worldtv.app.data

import com.worldtv.app.domain.CachedFavorite
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.MediaEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface LocalFavoriteCache {
    val entries: StateFlow<List<CachedFavorite>>

    suspend fun write(entries: List<CachedFavorite>)

    suspend fun clear()
}

interface LegacyFavoriteStore {
    fun read(): List<CachedFavorite>

    fun clearLegacy()
}

class DeviceFavoriteCache(private val file: File) : LocalFavoriteCache {
    private val lock = Any()
    private val state = MutableStateFlow(readStored())
    override val entries: StateFlow<List<CachedFavorite>> = state.asStateFlow()

    override suspend fun write(entries: List<CachedFavorite>) {
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.writeText(FavoritesCodec.encodeCached(entries))
            state.value = entries
        }
    }

    override suspend fun clear() {
        write(emptyList())
    }

    private fun readStored(): List<CachedFavorite> {
        synchronized(lock) {
            if (!file.isFile) return emptyList()
            return runCatching { FavoritesCodec.decodeCached(file.readText()) }.getOrDefault(emptyList())
        }
    }
}

class FileFavoriteCache(private val file: File) : LocalFavoriteCache, LegacyFavoriteStore {
    private val directory = file.parentFile ?: error("Favorites file needs a directory")
    private val legacyFile = File(directory, "favorites-legacy.json")
    private val marker = File(directory, "favorites-legacy.marker")
    private val lock = Any()

    init {
        captureLegacy()
    }

    private val state = MutableStateFlow(readActive())
    override val entries: StateFlow<List<CachedFavorite>> = state.asStateFlow()

    override suspend fun write(entries: List<CachedFavorite>) {
        val text = FavoritesCodec.encodeCached(entries)
        synchronized(lock) {
            directory.mkdirs()
            file.writeText(text)
            state.value = entries
        }
    }

    override suspend fun clear() {
        write(emptyList())
    }

    override fun read(): List<CachedFavorite> = synchronized(lock) { readStored(legacyFile) }

    override fun clearLegacy() {
        synchronized(lock) {
            if (legacyFile.exists()) legacyFile.delete()
        }
    }

    private fun captureLegacy() {
        synchronized(lock) {
            if (marker.exists()) return
            if (file.isFile && file.length() > 0L && !legacyFile.exists()) {
                directory.mkdirs()
                file.copyTo(legacyFile)
            }
            if (file.exists()) file.delete()
            directory.mkdirs()
            marker.writeText("1")
        }
    }

    private fun readActive(): List<CachedFavorite> = synchronized(lock) { readStored(file) }

    private fun readStored(source: File): List<CachedFavorite> {
        if (!source.isFile) return emptyList()
        return runCatching { FavoritesCodec.decodeCached(source.readText()) }.getOrDefault(emptyList())
    }
}

object FavoritesCodec {
    fun encode(entries: List<MediaEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("kind", entry.kind.name)
                    .put("name", entry.name)
                    .put("countryCode", entry.countryCode)
                    .put("languages", JSONArray(entry.languages))
                    .put("geoBlocked", entry.geoBlocked)
                    .put("streams", JSONArray(entry.streams))
                    .put("youtube", JSONArray(entry.youtube)),
            )
        }
        return array.toString()
    }

    fun encodeCached(entries: List<CachedFavorite>): String {
        val array = JSONArray()
        entries.forEach { cached ->
            val entry = cached.entry
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("kind", entry.kind.name)
                    .put("name", entry.name)
                    .put("countryCode", entry.countryCode)
                    .put("languages", JSONArray(entry.languages))
                    .put("geoBlocked", entry.geoBlocked)
                    .put("streams", JSONArray(entry.streams))
                    .put("youtube", JSONArray(entry.youtube))
                    .put("addedAt", cached.addedAtMillis),
            )
        }
        return array.toString()
    }

    fun decode(json: String): List<MediaEntry> = decodeCached(json).map { it.entry }

    fun decodeCached(json: String): List<CachedFavorite> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        val entries = ArrayList<CachedFavorite>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val kind = runCatching { ContentKind.valueOf(item.optString("kind")) }.getOrNull() ?: continue
            val id = item.optString("id")
            val name = item.optString("name")
            if (id.isBlank() || name.isBlank()) continue
            entries += CachedFavorite(
                entry = MediaEntry(
                    id = id,
                    kind = kind,
                    name = name,
                    countryCode = item.optString("countryCode").lowercase(),
                    languages = jsonStrings(item.optJSONArray("languages")),
                    geoBlocked = item.optBoolean("geoBlocked", false),
                    streams = jsonStrings(item.optJSONArray("streams")),
                    youtube = jsonStrings(item.optJSONArray("youtube")),
                ),
                addedAtMillis = item.optLong("addedAt", 0L),
            )
        }
        return entries
    }

    private fun jsonStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }
}

class FavoriteCountryStore(private val file: File) {
    fun read(): Set<String> {
        if (!file.isFile) return emptySet()
        return file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun write(keys: Set<String>) {
        file.parentFile?.mkdirs()
        file.writeText(keys.joinToString("\n"))
    }
}
