@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.worldtv.app.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

fun buildPlayer(
    context: Context,
    audioOnly: Boolean,
    trackSelector: DefaultTrackSelector? = null,
): ExoPlayer {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Zenvu/1.0 (Android) ExoPlayer")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(12_000)
        .setReadTimeoutMs(20_000)
    val builder = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
    if (trackSelector != null) builder.setTrackSelector(trackSelector)
    return builder.build()
        .apply {
            val contentType = if (audioOnly) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_MOVIE
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(contentType)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            if (audioOnly) setWakeMode(C.WAKE_MODE_NETWORK)
        }
}
