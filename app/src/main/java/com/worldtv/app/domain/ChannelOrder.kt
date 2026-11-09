package com.worldtv.app.domain

object ChannelCategories {
    private val broad = setOf("general", "public", "relax")

    private val names = mapOf(
        ContentKind.TV to listOf(
            "animation", "auto", "business", "classic", "comedy", "cooking", "culture",
            "documentary", "education", "entertainment", "family", "general", "interactive",
            "kids", "legislative", "lifestyle", "movies", "music", "news", "outdoor",
            "public", "relax", "religious", "science", "series", "shop", "show", "sports",
            "top-news", "travel", "weather",
        ),
        ContentKind.RADIO to listOf(
            "70s", "80s", "90s", "blues", "chill", "christmas", "classical", "country",
            "easy-listening", "electronic", "folk", "hip-hop", "hits", "indie", "jazz",
            "latin", "lgbtq", "metal", "news", "oldies", "politics", "pop", "reggae",
            "religious", "rock", "schlager", "soul", "sports", "talk",
        ),
        ContentKind.WEBCAM to listOf(
            "airport", "animals", "beach", "city", "construction", "harbor", "lake",
            "landmark", "mountain", "nature", "park", "river", "ski", "space", "sports",
            "traffic", "train", "underwater", "volcano", "weather",
        ),
    )

    fun ordered(kind: ContentKind): List<String> {
        val values = names.getValue(kind)
        return values.filter { it !in broad }.sorted() + values.filter { it in broad }.sorted()
    }
}

object ChannelCategoryIndex {
    fun build(orderedNames: List<String>, idsByCategory: Map<String, List<String>>): Map<String, String> {
        val index = HashMap<String, String>()
        for (name in orderedNames) {
            for (id in idsByCategory[name].orEmpty()) {
                index.putIfAbsent(id, name)
            }
        }
        return index
    }
}

object ChannelOrder {
    fun sort(entries: List<MediaEntry>, categoryOf: (MediaEntry) -> String): List<MediaEntry> =
        entries.sortedWith(
            compareBy(
                { it.kind.ordinal },
                { categoryOf(it).ifEmpty { "\uFFFF" } },
                { it.name.lowercase() },
            ),
        )
}
