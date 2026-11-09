@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.worldtv.app.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

fun buildPlayer(context: Context, audioOnly: Boolean): ExoPlayer {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Zenvu/1.0 (Android) ExoPlayer")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(12_000)
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            if (audioOnly) 2_000 else 1_500,
            if (audioOnly) 12_000 else 8_000,
            if (audioOnly) 400 else 600,
            if (audioOnly) 1_000 else 1_200,
        )
        .setBackBuffer(0, false)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .setLoadControl(loadControl)
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
