@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.worldtv.app.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

fun buildPlayer(context: Context, audioOnly: Boolean): ExoPlayer {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("WorldTV/1.0 (Android) ExoPlayer")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(12_000)
        .setReadTimeoutMs(20_000)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .build()
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
