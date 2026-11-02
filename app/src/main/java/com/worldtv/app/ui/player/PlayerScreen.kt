@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.worldtv.app.ui.player

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.worldtv.app.AppContainer
import com.worldtv.app.R
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.LanguageNames
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.PlaybackPlan
import com.worldtv.app.domain.PlaybackPlanner
import com.worldtv.app.playback.buildPlayer
import com.worldtv.app.ui.components.Artwork
import com.worldtv.app.ui.theme.FavoriteColor
import com.worldtv.app.ui.theme.Space
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    container: AppContainer,
    television: Boolean,
    favorite: Boolean,
    countryName: String,
    autoFallback: Boolean,
    onToggleFavorite: (MediaEntry) -> Unit,
    onBack: () -> Unit,
) {
    val queue by container.queue.state.collectAsStateWithLifecycle()
    val entry = queue.current
    if (entry == null) {
        Column(Modifier.fillMaxSize().padding(Space.xl), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.empty_channels_title), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        return
    }
    if (!television && entry.kind != ContentKind.RADIO) {
        LockLandscape()
        KeepScreenOn()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (entry.kind == ContentKind.RADIO) {
            RadioStage(container, entry, countryName)
        } else {
            VisualStage(container, entry, autoFallback, television)
        }
        PlayerChrome(
            entry = entry,
            countryName = countryName,
            favorite = favorite,
            television = television,
            onBack = onBack,
            onPrevious = container.queue::previous,
            onNext = container.queue::next,
            onToggleFavorite = { onToggleFavorite(entry) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun RadioStage(container: AppContainer, entry: MediaEntry, countryName: String) {
    val radio by container.radio.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember {
        mutableStateOf(notificationsGranted(context))
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted || Build.VERSION.SDK_INT < 33
    }
    LaunchedEffect(entry.key) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(entry.key, notificationsAllowed) {
        if (notificationsGranted(context) || notificationsAllowed || Build.VERSION.SDK_INT < 33) {
            container.radio.play(entry)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(Space.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(entry.name, Modifier.size(180.dp))
        Spacer(Modifier.height(Space.xl))
        Text(entry.name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(countryName, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
        val languages = LanguageNames.labels(entry.languages)
        if (languages.isNotBlank()) {
            Text(languages, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.75f))
        }
        Spacer(Modifier.height(Space.lg))
        when {
            Build.VERSION.SDK_INT >= 33 && !notificationsAllowed && !notificationsGranted(context) -> {
                Text(stringResource(R.string.radio_notification_denied), color = Color.White)
                TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text(stringResource(R.string.retry))
                }
            }
            radio.buffering -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            radio.error != null -> {
                Text(radio.error.orEmpty(), color = Color.White)
                TextButton(onClick = container.radio::retry) { Text(stringResource(R.string.retry)) }
            }
            else -> {
                IconButton(onClick = container.radio::toggle, modifier = Modifier.size(72.dp)) {
                    Icon(
                        imageVector = if (radio.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (radio.playing) R.string.pause else R.string.play),
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualStage(
    container: AppContainer,
    entry: MediaEntry,
    autoFallback: Boolean,
    television: Boolean,
) {
    val context = LocalContext.current
    val plan = PlaybackPlanner.plan(entry)
    var useEmbed by remember(entry.key) { mutableStateOf(plan is PlaybackPlan.Embed) }
    var urlIndex by remember(entry.key) { mutableIntStateOf(0) }
    var retryToken by remember(entry.key) { mutableIntStateOf(0) }
    var buffering by remember(entry.key) { mutableStateOf(plan is PlaybackPlan.Direct) }
    var playing by remember(entry.key) { mutableStateOf(false) }
    var error by remember(entry.key) { mutableStateOf<String?>(null) }
    var controls by remember(entry.key) { mutableStateOf(true) }
    val player = remember(context) { buildPlayer(context.applicationContext, audioOnly = false) }
    DisposableEffect(player) { onDispose { player.release() } }
    DisposableEffect(player, entry.key) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) error = null
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                val direct = PlaybackPlanner.plan(entry) as? PlaybackPlan.Direct
                if (autoFallback && direct != null && urlIndex + 1 < direct.urls.size) {
                    urlIndex += 1
                } else {
                    buffering = false
                    error = PlaybackPlanner.failureMessage(entry)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(entry.key, urlIndex, retryToken, useEmbed) {
        if (useEmbed) return@LaunchedEffect
        val direct = PlaybackPlanner.plan(entry) as? PlaybackPlan.Direct ?: return@LaunchedEffect
        val url = direct.urls.getOrNull(urlIndex) ?: return@LaunchedEffect
        error = null
        buffering = true
        player.setMediaItem(MediaItem.Builder().setUri(url).setMediaId(entry.key).build())
        player.prepare()
        player.play()
    }
    if (!television) {
        LaunchedEffect(controls, error, entry.key) {
            if (controls && error == null) {
                delay(4_000)
                controls = false
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .then(if (!television) Modifier.clickable { controls = !controls } else Modifier),
    ) {
        when {
            plan is PlaybackPlan.Unsupported -> Message(plan.reason)
            useEmbed -> {
                val embedUrl = when (plan) {
                    is PlaybackPlan.Embed -> plan.url
                    is PlaybackPlan.Direct -> plan.embedFallback
                    else -> null
                }
                if (embedUrl == null) {
                    Message(stringResource(R.string.unsupported_title))
                } else {
                    EmbedView(embedUrl)
                    Text(
                        stringResource(R.string.embed_note),
                        color = Color.White,
                        modifier = Modifier.align(Alignment.TopCenter).padding(Space.lg),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = false
                            this.player = player
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        }
                    },
                    update = { it.player = player },
                )
            }
        }
        if (buffering && error == null && !useEmbed && plan !is PlaybackPlan.Unsupported) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
        }
        if (error != null) {
            Column(Modifier.align(Alignment.Center).padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_error_title), color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Space.sm))
                Text(error.orEmpty(), color = Color.White)
                TextButton(onClick = {
                    error = null
                    if (urlIndex == 0) retryToken += 1 else urlIndex = 0
                }) { Text(stringResource(R.string.retry)) }
                val fallback = (plan as? PlaybackPlan.Direct)?.embedFallback
                if (fallback != null && !useEmbed) {
                    TextButton(onClick = { useEmbed = true; error = null }) {
                        Text(stringResource(R.string.try_embed))
                    }
                }
            }
        }
        if (entry.geoBlocked && error == null) {
            Text(
                stringResource(R.string.geo_note),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(Space.lg),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EmbedView(url: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            if (webView.tag != url) {
                webView.tag = url
                webView.loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, modifier = Modifier.padding(Space.xl))
    }
}

@Composable
private fun PlayerChrome(
    entry: MediaEntry,
    countryName: String,
    favorite: Boolean,
    television: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
            .padding(if (television) Space.xl else Space.lg),
    ) {
        Text(entry.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(countryName, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        if (entry.geoBlocked) {
            Text(stringResource(R.string.region_locked), color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous), tint = Color.White)
            }
            if (entry.kind == ContentKind.RADIO) {
                Spacer(Modifier.width(Space.sm))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                    tint = if (favorite) FavoriteColor else Color.White,
                )
            }
        }
    }
}

@Composable
private fun LockLandscape() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = previous }
    }
}

@Composable
private fun KeepScreenOn() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private fun notificationsGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}
