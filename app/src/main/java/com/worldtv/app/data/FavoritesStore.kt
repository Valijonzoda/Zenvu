package com.worldtv.app.data

import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.FavoritesRepository
import com.worldtv.app.domain.MediaEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FileFavoritesRepository(private val file: File) : FavoritesRepository {
    private val mutex = Mutex()
    private val state = MutableStateFlow(readFile())

    override val favorites: Flow<List<MediaEntry>> = state.asStateFlow()

    override suspend fun toggle(entry: MediaEntry) {
        mutex.withLock {
            val current = state.value
            val updated = if (current.any { it.key == entry.key }) {
                current.filterNot { it.key == entry.key }
            } else {
                listOf(entry) + current
            }
            writeFile(updated)
            state.value = updated
        }
    }

    override suspend fun isFavorite(key: String): Boolean = state.value.any { it.key == key }

    private fun readFile(): List<MediaEntry> {
        if (!file.isFile) return emptyList()
        return runCatching { FavoritesCodec.decode(file.readText()) }.getOrDefault(emptyList())
    }

    private fun writeFile(entries: List<MediaEntry>) {
        file.parentFile?.mkdirs()
        file.writeText(FavoritesCodec.encode(entries))
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

    fun decode(json: String): List<MediaEntry> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        val entries = ArrayList<MediaEntry>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val kind = runCatching { ContentKind.valueOf(item.optString("kind")) }.getOrNull() ?: continue
            val id = item.optString("id")
            val name = item.optString("name")
            if (id.isBlank() || name.isBlank()) continue
            entries += MediaEntry(
                id = id,
                kind = kind,
                name = name,
                countryCode = item.optString("countryCode").lowercase(),
                languages = jsonStrings(item.optJSONArray("languages")),
                geoBlocked = item.optBoolean("geoBlocked", false),
                streams = jsonStrings(item.optJSONArray("streams")),
                youtube = jsonStrings(item.optJSONArray("youtube")),
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
