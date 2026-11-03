package com.worldtv.app

import com.worldtv.app.data.CatalogRepositoryImpl
import com.worldtv.app.data.DatasetException
import com.worldtv.app.data.DatasetParser
import com.worldtv.app.data.DatasetClient
import com.worldtv.app.data.FavoritesCodec
import com.worldtv.app.data.FileTextCache
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.Country
import com.worldtv.app.domain.CountryFlags
import com.worldtv.app.domain.CountrySelection
import com.worldtv.app.domain.CatalogQuery
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.PlaybackPlan
import com.worldtv.app.domain.PlaybackPlanner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DatasetParserTest {
    @Test
    fun parsesExampleChannel() {
        val json = """
            {
              "nanoid": "mG0AeH9doLAPIb",
              "name": "KK TV",
              "sources": { "streams": ["https://example.com/playlist.m3u8"] },
              "languages": ["por"],
              "country": "ao",
              "isGeoBlocked": false
            }
        """.trimIndent()
        val entries = DatasetParser.parseChannels("[$json]", ContentKind.TV)
        assertEquals(1, entries.size)
        assertEquals("KK TV", entries[0].name)
        assertEquals("ao", entries[0].countryCode)
        assertEquals(listOf("por"), entries[0].languages)
        assertEquals(listOf("https://example.com/playlist.m3u8"), entries[0].streams)
        assertTrue(entries[0].youtube.isEmpty())
    }

    @Test
    fun parsesRealFixtures() {
        val tv = DatasetParser.parseChannels(read("fixtures/tv_ad.json"), ContentKind.TV)
        assertTrue(tv.any { it.name == "Andorra TV" && it.streams.isNotEmpty() })
        assertTrue(tv.any { it.youtube.isNotEmpty() })
        val radio = DatasetParser.parseChannels(read("fixtures/radio_ad.json"), ContentKind.RADIO)
        assertEquals("Flaix FM Andorra", radio.first().name)
        val cams = DatasetParser.parseChannels(read("fixtures/webcams_ae.json"), ContentKind.WEBCAM)
        assertTrue(cams.all { it.streams.isEmpty() && it.youtube.isNotEmpty() })
        val countries = DatasetParser.parseCountries(read("fixtures/tv_countries_metadata.json"))
        val uk = countries.first { it.code == "uk" }
        assertEquals("United Kingdom", uk.name)
        assertTrue(uk.hasChannels)
        assertTrue(uk.channelCount > 0)
    }

    private fun read(path: String): String =
        javaClass.classLoader!!.getResource(path)!!.readText()
}

class PlaybackPlannerTest {
    @Test
    fun prefersDirectStreamAndKeepsEmbedFallback() {
        val plan = PlaybackPlanner.plan(entry(streams = listOf("https://cdn.example/live.m3u8"), youtube = listOf("https://www.youtube-nocookie.com/embed/abc12345678")))
        val direct = plan as PlaybackPlan.Direct
        assertEquals(listOf("https://cdn.example/live.m3u8"), direct.urls)
        assertTrue(direct.embedFallback!!.contains("abc12345678"))
    }

    @Test
    fun webcamEmbedIsNormalized() {
        val plan = PlaybackPlanner.plan(entry(kind = ContentKind.WEBCAM, youtube = listOf("https://www.youtube-nocookie.com/embed/TmVRslhKB4c")))
        val embed = plan as PlaybackPlan.Embed
        assertTrue(embed.url.startsWith("https://www.youtube-nocookie.com/embed/TmVRslhKB4c"))
        assertTrue(embed.url.contains("autoplay=1"))
        assertFalse(embed.url.contains("fs=0"))
        assertFalse(embed.url.contains("origin="))
    }

    @Test
    fun youtubeLinkInsideStreamsIsNotSentToExoPlayer() {
        val plan = PlaybackPlanner.plan(entry(streams = listOf("https://www.youtube.com/watch?v=abcdefghijk")))
        assertTrue(plan is PlaybackPlan.Embed)
    }

    @Test
    fun emptySourcesAreUnsupported() {
        val plan = PlaybackPlanner.plan(entry())
        assertTrue(plan is PlaybackPlan.Unsupported)
    }

    @Test
    fun geoBlockedStreamIsStillDirect() {
        val plan = PlaybackPlanner.plan(entry(streams = listOf("https://example.com/a.m3u8"), geoBlocked = true))
        assertTrue(plan is PlaybackPlan.Direct)
        assertTrue(PlaybackPlanner.failureMessage(entry(geoBlocked = true)).contains("region"))
    }

    private fun entry(
        kind: ContentKind = ContentKind.TV,
        streams: List<String> = emptyList(),
        youtube: List<String> = emptyList(),
        geoBlocked: Boolean = false,
    ) = MediaEntry(
        id = "id",
        kind = kind,
        name = "Name",
        countryCode = "us",
        languages = listOf("eng"),
        geoBlocked = geoBlocked,
        streams = streams,
        youtube = youtube,
    )
}

class CatalogBehaviorTest {
    @Test
    fun filtersByNameLanguageAndFavorites() {
        val eng = sample("1", "News One", "eng")
        val por = sample("2", "KK TV", "por")
        val all = listOf(eng, por)
        assertEquals(listOf(por), CatalogQuery.filter(all, "kk", null, false, emptySet()))
        assertEquals(listOf(eng), CatalogQuery.filter(all, "", "eng", false, emptySet()))
        assertEquals(listOf(eng), CatalogQuery.filter(all, "", null, true, setOf(eng.key)))
    }

    @Test
    fun remembersSavedCountryAndMapsGbToUk() {
        val countries = listOf(
            Country("gb", "United Kingdom", "London", "Europe/London", false, 0),
            Country("uk", "United Kingdom", "London", "Europe/London", true, 10),
            Country("us", "United States", "Washington", "America/New_York", true, 4),
        )
        assertEquals(setOf("uk"), CountrySelection.choose(setOf("UK"), countries, "us"))
        assertEquals(setOf("uk"), CountrySelection.choose(emptySet(), countries, "GB"))
        assertEquals(setOf("us"), CountrySelection.choose(emptySet(), countries.filter { it.code != "uk" }, "fr"))
    }

    @Test
    fun ukFlagUsesGbGlyph() {
        assertEquals(CountryFlags.emoji("gb"), CountryFlags.emoji("uk"))
        assertTrue(CountryFlags.emoji("us").isNotBlank())
    }

    @Test
    fun favoritesRoundTrip() {
        val entry = sample("abc", "Station", "cat")
        val decoded = FavoritesCodec.decode(FavoritesCodec.encode(listOf(entry)))
        assertEquals(listOf(entry), decoded)
    }

    @Test
    fun repositoryUsesCacheWhenNetworkFails() = runTest {
        val cache = FileTextCache(File.createTempFile("cache", "").apply { delete(); mkdirs() })
        var online = true
        var calls = 0
        val client = object : DatasetClient {
            override suspend fun fetchText(relativePath: String): String {
                calls += 1
                if (!online) throw DatasetException("offline")
                return read("fixtures/tv_countries_metadata.json")
            }
        }
        val repository = CatalogRepositoryImpl(client, cache, online = { online })
        val first = repository.loadCountries(ContentKind.TV, force = true)
        assertTrue(first.value!!.any { it.code == "ad" })
        online = false
        val second = repository.loadCountries(ContentKind.TV, force = false)
        assertTrue(second.fromCache)
        assertTrue(second.offline)
        assertEquals(first.value, second.value)
        assertEquals(1, calls)
    }

    private fun sample(id: String, name: String, language: String) = MediaEntry(
        id = id,
        kind = ContentKind.TV,
        name = name,
        countryCode = "ao",
        languages = listOf(language),
        geoBlocked = false,
        streams = listOf("https://example.com/$id.m3u8"),
        youtube = emptyList(),
    )

    private fun read(path: String): String =
        javaClass.classLoader!!.getResource(path)!!.readText()
}
