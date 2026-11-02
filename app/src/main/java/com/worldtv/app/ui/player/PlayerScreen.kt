@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.worldtv.app.ui.player

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.PlaybackPlan
import com.worldtv.app.domain.PlaybackPlanner
import com.worldtv.app.playback.buildPlayer
import com.worldtv.app.ui.components.ChannelCard
import com.worldtv.app.ui.components.artBrush
import com.worldtv.app.ui.components.artIndex
import com.worldtv.app.ui.theme.LocalWt
import com.worldtv.app.ui.theme.Outfit
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    container: AppContainer,
    television: Boolean,
    favorite: Boolean,
    countryName: String,
    autoFallback: Boolean,
    fullscreen: Boolean,
    onToggleFavorite: (MediaEntry) -> Unit,
    onBack: () -> Unit,
    onToggleOrientation: () -> Unit,
    channels: List<MediaEntry>,
    favoriteKeys: Set<String>,
    onSelect: (MediaEntry) -> Unit,
) {
    val queue by container.queue.state.collectAsStateWithLifecycle()
    val entry = queue.current
    if (entry == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.empty_channels_title))
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        return
    }
    val palette = LocalWt.current
    Column(Modifier.fillMaxSize().background(if (fullscreen) Color.Black else palette.bg)) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (fullscreen) Modifier.weight(1f) else Modifier.aspectRatio(if (entry.kind == ContentKind.RADIO) 1f / 0.8f else 16f / 10f)),
        ) {
            PlaybackStage(
                container = container,
                entry = entry,
                countryName = countryName,
                favorite = favorite,
                autoFallback = autoFallback,
                fullscreen = fullscreen || television,
                onToggleFavorite = { onToggleFavorite(entry) },
                onBack = onBack,
                onToggleOrientation = onToggleOrientation,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!fullscreen) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(channels, key = { it.key }, contentType = { "channel" }) { item ->
                    ChannelCard(
                        entry = item,
                        favorite = item.key in favoriteKeys,
                        playing = item.key == entry.key,
                        favoriteTags = true,
                        onOpen = { onSelect(item) },
                        onToggleFavorite = { onToggleFavorite(item) },
                    )
                }
            }
        }
    }
}

@Composable
fun PlaybackStage(
    container: AppContainer,
    entry: MediaEntry,
    countryName: String,
    favorite: Boolean,
    autoFallback: Boolean,
    fullscreen: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entry.kind == ContentKind.RADIO) {
        RadioStage(
            container = container,
            entry = entry,
            countryName = countryName,
            favorite = favorite,
            fullscreen = fullscreen,
            onToggleFavorite = onToggleFavorite,
            onBack = onBack,
            modifier = modifier,
        )
    } else {
        VideoStage(
            container = container,
            entry = entry,
            countryName = countryName,
            favorite = favorite,
            autoFallback = autoFallback,
            fullscreen = fullscreen,
            onToggleFavorite = onToggleFavorite,
            onBack = onBack,
            onToggleOrientation = onToggleOrientation,
            modifier = modifier,
        )
    }
}

@Composable
private fun VideoStage(
    container: AppContainer,
    entry: MediaEntry,
    countryName: String,
    favorite: Boolean,
    autoFallback: Boolean,
    fullscreen: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWt.current
    val context = LocalContext.current
    val plan = remember(entry.key) { PlaybackPlanner.plan(entry) }
    var useEmbed by remember(entry.key) { mutableStateOf(plan is PlaybackPlan.Embed) }
    var urlIndex by remember(entry.key) { mutableIntStateOf(0) }
    var retryToken by remember(entry.key) { mutableIntStateOf(0) }
    var buffering by remember(entry.key) { mutableStateOf(plan is PlaybackPlan.Direct) }
    var playing by remember(entry.key) { mutableStateOf(false) }
    var error by remember(entry.key) { mutableStateOf<String?>(null) }
    var chrome by remember(entry.key) { mutableStateOf(true) }
    var chromeTick by remember(entry.key) { mutableIntStateOf(0) }
    val player = remember(context) { buildPlayer(context.applicationContext, audioOnly = false) }
    DisposableEffect(player) { onDispose { player.release() } }
    DisposableEffect(player, entry.key, urlIndex, autoFallback) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) error = null
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                val direct = plan as? PlaybackPlan.Direct
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
        val direct = plan as? PlaybackPlan.Direct ?: return@LaunchedEffect
        val url = direct.urls.getOrNull(urlIndex) ?: return@LaunchedEffect
        error = null
        buffering = true
        playing = false
        chrome = true
        player.setMediaItem(MediaItem.Builder().setUri(url).setMediaId(entry.key).build())
        player.prepare()
        player.play()
    }
    val direct = plan is PlaybackPlan.Direct && !useEmbed
    LaunchedEffect(chrome, chromeTick, playing, error, useEmbed) {
        if (!chrome || error != null) return@LaunchedEffect
        if (direct && !playing) return@LaunchedEffect
        delay(3_000)
        chrome = false
    }
    if (entry.kind != ContentKind.RADIO) KeepScreenOn()
    val stageModifier = if (direct) modifier.background(Color.Black) else modifier.background(artBrush(artIndex(entry.key)))
    Box(stageModifier) {
        when {
            plan is PlaybackPlan.Unsupported -> {
                Text(plan.reason, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(20.dp), fontFamily = Outfit)
            }
            useEmbed -> {
                val embedUrl = when (plan) {
                    is PlaybackPlan.Embed -> plan.url
                    is PlaybackPlan.Direct -> plan.embedFallback
                    else -> null
                }
                if (embedUrl == null) {
                    Text(stringResource(R.string.unsupported_title), color = Color.White, modifier = Modifier.align(Alignment.Center).padding(20.dp))
                } else {
                    EmbedView(embedUrl)
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
        if (!useEmbed || !chrome) {
            GestureLayer(
                fullscreen = fullscreen,
                onTap = {
                    chrome = !chrome
                    if (chrome) chromeTick += 1
                },
                onSwipeDown = onToggleOrientation,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (buffering && error == null && direct && chrome) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        if (error != null) {
            Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_error_title), color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(error.orEmpty(), color = Color.White, fontFamily = Outfit, fontSize = 14.sp)
                TextButton(onClick = {
                    error = null
                    chrome = true
                    if (urlIndex == 0) retryToken += 1 else urlIndex = 0
                }) { Text(stringResource(R.string.retry)) }
                val fallback = (plan as? PlaybackPlan.Direct)?.embedFallback
                if (fallback != null && !useEmbed) {
                    TextButton(onClick = { useEmbed = true; error = null }) { Text(stringResource(R.string.try_embed)) }
                }
            }
        } else if (direct && chrome) {
            PlayOrb(
                playing = playing,
                large = false,
                onClick = {
                    chromeTick += 1
                    if (player.isPlaying) player.pause() else player.play()
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (chrome) {
            if (fullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StageButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1)
                        Text(countryName, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontFamily = Outfit, maxLines = 1)
                    }
                    StageButton(
                        if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                        onToggleFavorite,
                        tint = if (favorite) palette.fav else Color(0xFF3A4673),
                    )
                }
            }
            StageButton(
                icon = if (fullscreen) Icons.Filled.StayCurrentPortrait else Icons.Filled.StayCurrentLandscape,
                description = stringResource(if (fullscreen) R.string.watch_vertical else R.string.watch_landscape),
                onClick = {
                    chromeTick += 1
                    onToggleOrientation()
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            )
        }
        if (entry.geoBlocked && chrome) {
            GeoNote(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 64.dp, bottom = 12.dp))
        }
    }
}

@Composable
private fun RadioStage(
    container: AppContainer,
    entry: MediaEntry,
    countryName: String,
    favorite: Boolean,
    fullscreen: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWt.current
    val radio by container.radio.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(notificationsGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted || Build.VERSION.SDK_INT < 33
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(entry.key) { container.radio.play(entry) }
    val granted = notificationsGranted(context) || notificationsAllowed || Build.VERSION.SDK_INT < 33
    Box(modifier.background(artBrush(artIndex(entry.key)))) {
        Equalizer(playing = radio.playing, modifier = Modifier.align(Alignment.Center).height(80.dp))
        if (!granted) {
            Text(
                stringResource(R.string.radio_notification_denied),
                color = Color.White,
                fontFamily = Outfit,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center).padding(top = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StageButton(Icons.Filled.SkipPrevious, stringResource(R.string.previous), container.queue::previous)
            PlayOrb(playing = radio.playing, large = true, onClick = container.radio::toggle)
            StageButton(Icons.Filled.SkipNext, stringResource(R.string.next), container.queue::next)
        }
        if (radio.buffering) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        if (radio.error != null) {
            Column(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(radio.error.orEmpty(), color = Color.White, fontFamily = Outfit, fontSize = 14.sp)
                TextButton(onClick = container.radio::retry) { Text(stringResource(R.string.retry)) }
            }
        }
        if (fullscreen) {
            StageButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.back),
                onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }
        if (favorite) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = palette.fav,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )
        }
        Text(
            entry.name,
            color = Color.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp, start = 64.dp, end = 64.dp),
        )
        Text(
            countryName,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontFamily = Outfit,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
        )
    }
}

@Composable
private fun GestureLayer(
    fullscreen: Boolean,
    onTap: () -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.pointerInput(fullscreen) {
            awaitEachGesture {
                val down = awaitFirstDown()
                var dragY = 0f
                var dragged = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (!dragged) onTap()
                        else if (fullscreen && dragY > 72.dp.toPx()) onSwipeDown()
                        break
                    }
                    val delta = change.positionChange().y
                    if (delta != 0f) {
                        dragY += delta
                        if (abs(dragY) > viewConfiguration.touchSlop) {
                            dragged = true
                            change.consume()
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun PlayOrb(playing: Boolean, large: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalWt.current
    val size = if (large) 72.dp else 68.dp
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (large) Brush.linearGradient(listOf(palette.accentFrom, palette.accent)) else Brush.linearGradient(listOf(Color.White, Color.White)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
            tint = if (large) Color.White else palette.accent,
            modifier = Modifier.size(if (large) 34.dp else 30.dp),
        )
    }
}

@Composable
private fun StageButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF3A4673),
) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun GeoNote(modifier: Modifier = Modifier) {
    val palette = LocalWt.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.warnBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = palette.warnText, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.geo_note), color = palette.warnText, fontSize = 13.sp, fontFamily = Outfit)
    }
}

@Composable
private fun Equalizer(playing: Boolean, modifier: Modifier = Modifier) {
    val reduce = reducedMotion()
    val transition = rememberInfiniteTransition(label = "eq")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val resting = listOf(0.35f, 0.75f, 1f, 0.55f, 0.82f)
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(5) { index ->
            val fraction = if (!playing || reduce) {
                resting[index]
            } else {
                val shifted = (phase + index * 0.2f) % 1f
                0.25f + 0.75f * abs(sin(shifted * Math.PI)).toFloat()
            }
            Box(
                Modifier
                    .width(8.dp)
                    .height(80.dp * fraction)
                    .background(Color.White, RoundedCornerShape(4.dp)),
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
private fun KeepScreenOn() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
private fun reducedMotion(): Boolean {
    val context = LocalContext.current
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}

private fun notificationsGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}
