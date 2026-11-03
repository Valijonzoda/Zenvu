package com.worldtv.app.domain

/**
 * Live directory kinds published by the Famelack dataset.
 * [directory] is the repository folder name.
 */
enum class ContentKind(val directory: String, val title: String) {
    TV("tv", "TV"),
    RADIO("radio", "Radio"),
    WEBCAM("webcams", "Webcams"),
}

data class Country(
    val code: String,
    val name: String,
    val capital: String,
    val timeZone: String,
    val hasChannels: Boolean,
    val channelCount: Int,
)

data class MediaEntry(
    val id: String,
    val kind: ContentKind,
    val name: String,
    val countryCode: String,
    val languages: List<String>,
    val geoBlocked: Boolean,
    val streams: List<String>,
    val youtube: List<String>,
) {
    val key: String get() = "${kind.name}:$id"
}

enum class ThemeChoice {
    DARK,
    LIGHT,
    SYSTEM,
}

data class UserSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val autoFallbackStreams: Boolean = true,
    val backgroundRadio: Boolean = true,
)

enum class UserNotice {
    OFFLINE_UNSAVED,
    NO_SAVED_COPY,
    SAVED_UNREADABLE,
    DATASET_EMPTY,
    NO_INTERNET,
    TIMEOUT,
    DOWNLOAD_INTERRUPTED,
    LISTING_MISSING,
    HOST_REFUSED,
    HOST_PROBLEM,
    REQUEST_FAILED,
    COUNTRY_LIST,
    CHANNEL_FORMAT,
    CHANNEL_READ,
    DATASET_LOAD,
    DATASET_READ,
    NO_DIRECT_AUDIO,
}

data class CatalogResult<T>(
    val value: T?,
    val fromCache: Boolean,
    val offline: Boolean,
    val errorMessage: String?,
    val notice: UserNotice? = null,
    val noticeArg: String? = null,
)

sealed interface PlaybackPlan {
    data class Direct(
        val urls: List<String>,
        val embedFallback: String?,
    ) : PlaybackPlan

    data class Embed(val url: String) : PlaybackPlan

    data class Unsupported(val reason: String) : PlaybackPlan
}
