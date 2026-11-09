@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.worldtv.app.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.util.Locale

data class QualityOption(val height: Int, val bitrate: Int) {
    val key: Int get() = if (height > 0) height else bitrate.coerceAtLeast(1)

    fun label(): String = when {
        height >= 100 -> "${height}p"
        bitrate > 0 -> "${bitrate / 1000} kbps"
        else -> "SD"
    }
}

data class LanguageOption(val language: String, val label: String)

data class StreamChoices(
    val video: List<QualityOption> = emptyList(),
    val audio: List<LanguageOption> = emptyList(),
    val text: List<LanguageOption> = emptyList(),
)

fun readChoices(tracks: Tracks): StreamChoices {
    val video = LinkedHashMap<Int, QualityOption>()
    val audio = LinkedHashMap<String, LanguageOption>()
    val text = LinkedHashMap<String, LanguageOption>()
    for (group in tracks.groups) {
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val format = group.getTrackFormat(index)
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    val option = QualityOption(format.height, format.bitrate)
                    if (option.height <= 0 && option.bitrate <= 0) continue
                    val previous = video[option.key]
                    if (previous == null || option.bitrate > previous.bitrate) video[option.key] = option
                }
                C.TRACK_TYPE_AUDIO -> {
                    val code = format.language?.lowercase().orEmpty()
                    if (code.isBlank() || code == "und") continue
                    audio.putIfAbsent(code, LanguageOption(code, languageLabel(code)))
                }
                C.TRACK_TYPE_TEXT -> {
                    val code = format.language?.lowercase().orEmpty()
                    if (code.isBlank() || code == "und") continue
                    text.putIfAbsent(code, LanguageOption(code, languageLabel(code)))
                }
            }
        }
    }
    return StreamChoices(
        video = video.values.sortedByDescending { it.key },
        audio = audio.values.toList(),
        text = text.values.toList(),
    )
}

fun nextLowerQuality(options: List<QualityOption>, cap: Int): Int? {
    if (options.size < 2) return null
    val sorted = options.map { it.key }.distinct().sortedDescending()
    val current = if (cap == Int.MAX_VALUE) 0 else sorted.indexOfFirst { it <= cap }.let { if (it < 0) 0 else it }
    return sorted.getOrNull(current + 1)
}

fun applyAutoQuality(selector: DefaultTrackSelector, cap: Int) {
    val builder = selector.buildUponParameters()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .setAllowVideoNonSeamlessAdaptiveness(true)
    if (cap == Int.MAX_VALUE) {
        builder
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .setExceedVideoConstraintsIfNecessary(true)
    } else if (cap >= 100) {
        builder
            .setMaxVideoSize(Int.MAX_VALUE, cap)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .setExceedVideoConstraintsIfNecessary(false)
    } else {
        builder
            .setMaxVideoBitrate(cap)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setExceedVideoConstraintsIfNecessary(false)
    }
    selector.parameters = builder.build()
}

fun applyFixedQuality(selector: DefaultTrackSelector, tracks: Tracks, key: Int) {
    val builder = selector.buildUponParameters()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        .setMaxVideoBitrate(Int.MAX_VALUE)
        .setExceedVideoConstraintsIfNecessary(true)
    val override = findVideoOverride(tracks, key)
    if (override != null) {
        builder.setOverrideForType(override)
    } else if (key >= 100) {
        builder.setMaxVideoSize(Int.MAX_VALUE, key).setExceedVideoConstraintsIfNecessary(false)
    } else {
        builder.setMaxVideoBitrate(key).setExceedVideoConstraintsIfNecessary(false)
    }
    selector.parameters = builder.build()
}

fun applyAudioLanguage(selector: DefaultTrackSelector, language: String?) {
    val builder = selector.buildUponParameters()
    if (language.isNullOrBlank()) builder.setPreferredAudioLanguages()
    else builder.setPreferredAudioLanguage(language)
    selector.parameters = builder.build()
}

fun applyTextLanguage(selector: DefaultTrackSelector, language: String?) {
    val builder = selector.buildUponParameters()
    if (language == null) {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        builder
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(language)
    }
    selector.parameters = builder.build()
}

fun PlaybackException.shouldDropQuality(): Boolean {
    return errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
        errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
        errorCode == PlaybackException.ERROR_CODE_TIMEOUT ||
        errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
}

private fun findVideoOverride(tracks: Tracks, key: Int): TrackSelectionOverride? {
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        var best = -1
        var bestBitrate = -1
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val format = group.getTrackFormat(index)
            val option = QualityOption(format.height, format.bitrate)
            if (option.key != key) continue
            if (format.bitrate > bestBitrate) {
                bestBitrate = format.bitrate
                best = index
            }
        }
        if (best >= 0) return TrackSelectionOverride(group.mediaTrackGroup, best)
    }
    return null
}

private fun languageLabel(code: String): String {
    val locale = Locale.forLanguageTag(code.substringBefore('-'))
    return locale.displayLanguage.replaceFirstChar { letter ->
        if (letter.isLowerCase()) letter.titlecase(locale) else letter.toString()
    }.ifBlank { code }
}
