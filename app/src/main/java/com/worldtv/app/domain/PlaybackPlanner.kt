package com.worldtv.app.domain

import java.net.URI

object PlaybackPlanner {
    fun plan(entry: MediaEntry): PlaybackPlan {
        val direct = entry.streams.map { it.trim() }.filter { it.isNotEmpty() && !isEmbedUrl(it) }
        val embeds = (entry.youtube + entry.streams.filter { isEmbedUrl(it) })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { normalizeEmbed(it) }
            .distinct()
        return when {
            direct.isNotEmpty() -> PlaybackPlan.Direct(direct, embeds.firstOrNull())
            embeds.isNotEmpty() -> PlaybackPlan.Embed(embeds.first())
            else -> PlaybackPlan.Unsupported(
                "This listing has no playable address. It may have been removed from the dataset.",
            )
        }
    }

    fun isEmbedUrl(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host?.lowercase() }.getOrNull() ?: return false
        return host == "youtu.be" ||
            host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" ||
            host.endsWith(".youtube-nocookie.com")
    }

    fun youtubeId(url: String): String? {
        val embed = EMBED.find(url)?.groupValues?.getOrNull(1)
        if (!embed.isNullOrBlank()) return embed
        val watch = WATCH.find(url)?.groupValues?.getOrNull(1)
        if (!watch.isNullOrBlank()) return watch
        val short = SHORT.find(url)?.groupValues?.getOrNull(1)
        return short?.takeIf { it.isNotBlank() }
    }

    fun normalizeEmbed(url: String): String {
        val id = youtubeId(url) ?: return url.trim()
        return "https://www.youtube-nocookie.com/embed/$id?autoplay=1&rel=0&modestbranding=1&playsinline=1"
    }

    fun failureMessage(entry: MediaEntry?): String {
        val region = if (entry?.geoBlocked == true) {
            " This listing is marked as region locked."
        } else {
            ""
        }
        return "This stream didn't start. It may be offline, restricted where you are, or use a format this device can't play.$region"
    }

    private val EMBED = Regex("""youtube(?:-nocookie)?\.com/embed/([\w-]{6,})""")
    private val WATCH = Regex("""[?&]v=([\w-]{6,})""")
    private val SHORT = Regex("""youtu\.be/([\w-]{6,})""")
}
