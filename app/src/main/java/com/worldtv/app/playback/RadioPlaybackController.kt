@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.worldtv.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.PlaybackPlan
import com.worldtv.app.domain.PlaybackPlanner
import com.worldtv.app.domain.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class RadioPlaybackState(
    val entry: MediaEntry? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
)

class RadioPlaybackController(
    private val appContext: Context,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateFlow = MutableStateFlow(RadioPlaybackState())
    val state: StateFlow<RadioPlaybackState> = stateFlow.asStateFlow()

    private var controller: MediaController? = null
    private var urls: List<String> = emptyList()
    private var urlIndex: Int = 0
    private var autoFallback: Boolean = true

    init {
        scope.launch {
            settings.settings.collect { autoFallback = it.autoFallbackStreams }
        }
    }

    fun play(entry: MediaEntry, restart: Boolean = false) {
        val current = stateFlow.value
        if (!restart && current.entry?.key == entry.key && current.error == null && controller != null) return
        val plan = PlaybackPlanner.plan(entry)
        val direct = (plan as? PlaybackPlan.Direct)?.urls.orEmpty()
        if (direct.isEmpty()) {
            stateFlow.value = RadioPlaybackState(
                entry = entry,
                error = (plan as? PlaybackPlan.Unsupported)?.reason
                    ?: "This station has no direct audio stream.",
            )
            return
        }
        scope.launch {
            try {
                val mediaController = connect()
                urls = direct
                urlIndex = 0
                stateFlow.value = RadioPlaybackState(entry = entry, buffering = true)
                start(mediaController, entry)
            } catch (error: SecurityException) {
                stateFlow.value = RadioPlaybackState(
                    entry = entry,
                    error = "Allow notifications so radio can keep a playback control on screen.",
                )
            } catch (error: Exception) {
                stateFlow.value = RadioPlaybackState(
                    entry = entry,
                    error = PlaybackPlanner.failureMessage(entry),
                )
            }
        }
    }

    fun toggle() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun stop() {
        controller?.let { mediaController ->
            mediaController.pause()
            mediaController.stop()
            mediaController.clearMediaItems()
        }
        urls = emptyList()
        urlIndex = 0
        stateFlow.value = RadioPlaybackState()
    }

    fun retry() {
        val entry = stateFlow.value.entry ?: return
        play(entry, restart = true)
    }

    private suspend fun connect(): MediaController {
        controller?.let { return it }
        val token = SessionToken(appContext, ComponentName(appContext, RadioPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        val connected = suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    try {
                        continuation.resume(future.get())
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                },
                ContextCompat.getMainExecutor(appContext),
            )
            continuation.invokeOnCancellation { future.cancel(true) }
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

            override fun onPlaybackStateChanged(playbackState: Int) = publish()

            override fun onPlayerError(error: PlaybackException) {
                val entry = stateFlow.value.entry
                if (autoFallback && urlIndex + 1 < urls.size && entry != null) {
                    urlIndex += 1
                    start(connected, entry)
                } else {
                    stateFlow.value = stateFlow.value.copy(
                        playing = false,
                        buffering = false,
                        error = PlaybackPlanner.failureMessage(entry),
                    )
                }
            }
        }
        connected.addListener(listener)
        controller = connected
        return connected
    }

    private fun start(mediaController: MediaController, entry: MediaEntry) {
        val url = urls.getOrNull(urlIndex) ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(entry.name)
            .setArtist(entry.countryCode.uppercase())
            .build()
        mediaController.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setMediaId(entry.key)
                .setMediaMetadata(metadata)
                .build(),
        )
        mediaController.prepare()
        mediaController.play()
        publish()
    }

    private fun publish() {
        val mediaController = controller ?: return
        val entry = stateFlow.value.entry
        stateFlow.value = RadioPlaybackState(
            entry = entry,
            playing = mediaController.isPlaying,
            buffering = mediaController.playbackState == Player.STATE_BUFFERING ||
                (mediaController.playbackState == Player.STATE_IDLE && mediaController.playWhenReady),
            error = null,
        )
    }
}
