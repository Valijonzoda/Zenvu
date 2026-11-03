@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.worldtv.app.ui.player

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class PlaybackCommands {
    val playing = MutableStateFlow(false)
    var toggle: () -> Unit = {}
}

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
    stage: @Composable () -> Unit,
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
            stage()
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
    onSwipeDown: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    compact: Boolean = false,
    commands: PlaybackCommands? = null,
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
            onSwipeDown = onSwipeDown,
            onSwipeUp = onSwipeUp,
            compact = compact,
            commands = commands,
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
            onSwipeDown = onSwipeDown,
            onSwipeUp = onSwipeUp,
            compact = compact,
            commands = commands,
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
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit,
    compact: Boolean,
    commands: PlaybackCommands?,
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
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    DisposableEffect(player, useEmbed) {
        if (!useEmbed) {
            commands?.toggle = {
                if (player.isPlaying) player.pause() else player.play()
            }
        }
        onDispose {
            if (!useEmbed) commands?.toggle = {}
        }
    }
    DisposableEffect(player, entry.key, urlIndex, autoFallback) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                commands?.playing?.value = isPlaying
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
        if (useEmbed) {
            player.stop()
            player.clearMediaItems()
            return@LaunchedEffect
        }
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
    if (!compact && entry.kind != ContentKind.RADIO) KeepScreenOn()
    val stageModifier = (if (direct) modifier.background(Color.Black) else modifier.background(artBrush(artIndex(entry.key)))).clipToBounds()
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
                    EmbedView(embedUrl, commands)
                }
            }
            else -> {
                key(compact, fullscreen) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            (LayoutInflater.from(viewContext).inflate(R.layout.texture_player_view, null) as PlayerView).apply {
                                this.player = player
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            }
                        },
                        update = { view ->
                            view.player = player
                            view.layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            view.requestLayout()
                        },
                        onRelease = { it.player = null },
                    )
                }
            }
        }
        if (!compact) {
            GestureLayer(
                onTap = {
                    chrome = !chrome
                    if (chrome) chromeTick += 1
                },
                onSwipeDown = onSwipeDown,
                onSwipeUp = onSwipeUp,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!compact && buffering && error == null && direct && chrome) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        if (!compact && error != null) {
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
        } else if (!compact && direct && chrome) {
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
        if (!compact && chrome) {
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
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit,
    compact: Boolean,
    commands: PlaybackCommands?,
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
    LaunchedEffect(radio.playing) { commands?.playing?.value = radio.playing }
    DisposableEffect(container) {
        commands?.toggle = container.radio::toggle
        onDispose {
            commands?.toggle = {}
            commands?.playing?.value = false
        }
    }
    val granted = notificationsGranted(context) || notificationsAllowed || Build.VERSION.SDK_INT < 33
    Box(modifier.background(artBrush(artIndex(entry.key)))) {
        if (!compact) {
            GestureLayer(
                onTap = {},
                onSwipeDown = onSwipeDown,
                onSwipeUp = onSwipeUp,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (compact) {
            Equalizer(playing = radio.playing, modifier = Modifier.align(Alignment.Center).height(36.dp))
            return@Box
        }
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
    onTap: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.pointerInput(Unit) {
            val threshold = 72.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                var dragX = 0f
                var dragY = 0f
                var dragged = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (!dragged) onTap()
                        else if (abs(dragY) > abs(dragX) && abs(dragY) > threshold) {
                            if (dragY > 0f) onSwipeDown() else onSwipeUp()
                        }
                        break
                    }
                    val delta = change.positionChange()
                    dragX += delta.x
                    dragY += delta.y
                    if (abs(dragY) > viewConfiguration.touchSlop && abs(dragY) > abs(dragX)) {
                        dragged = true
                        change.consume()
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
private fun EmbedView(url: String, commands: PlaybackCommands?) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.userAgentString = settings.userAgentString
                    .replace("; wv", "")
                    .replace("Version/4.0 ", "")
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        callback?.onCustomViewHidden()
                    }
                }
                webViewClient = WebViewClient()
            }
        },
        update = { view ->
            webView = view
            view.layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            view.requestLayout()
            if (view.tag != url) {
                view.tag = url
                view.loadDataWithBaseURL(
                    "https://www.youtube-nocookie.com",
                    embedDocument(url),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        onRelease = { it.destroy() },
    )
    DisposableEffect(webView, commands) {
        val view = webView
        val toggle = if (view != null && commands != null) {
            commands.playing.value = true
            {
                if (commands.playing.value) {
                    view.onPause()
                    commands.playing.value = false
                } else {
                    view.onResume()
                    commands.playing.value = true
                }
            }
        } else {
            null
        }
        if (toggle != null && commands != null) commands.toggle = toggle
        onDispose {
            if (toggle != null && commands?.toggle === toggle) commands.toggle = {}
        }
    }
}

private fun embedDocument(url: String): String {
    val source = url.replace("\"", "&quot;")
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}
          iframe{position:absolute;inset:0;width:100%;height:100%;border:0}
        </style>
        </head>
        <body>
        <iframe src="$source" referrerpolicy="strict-origin-when-cross-origin"
          allow="autoplay; encrypted-media; picture-in-picture"></iframe>
        </body>
        </html>
    """.trimIndent()
}

@Composable
fun FloatingPlayer(
    entry: MediaEntry,
    countryName: String,
    commands: PlaybackCommands,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entry.kind == ContentKind.RADIO) {
        RadioFloat(entry, countryName, commands, onExpand, onClose, preview, modifier)
    } else {
        VideoFloat(commands, onExpand, onClose, preview, modifier)
    }
}

@Composable
private fun VideoFloat(
    commands: PlaybackCommands,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing by commands.playing.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    var drag by remember { mutableStateOf(Offset.Zero) }
    var moved by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(modifier) {
        val marginPx = with(density) { 12.dp.toPx() }
        val widthPx = with(density) { (maxWidth * 0.78f).toPx() }
        val heightPx = widthPx * 9f / 16f
        val parentW = with(density) { maxWidth.toPx() }
        val parentH = with(density) { maxHeight.toPx() }
        val maxX = (parentW - widthPx - marginPx).coerceAtLeast(marginPx)
        val maxY = (parentH - heightPx - marginPx).coerceAtLeast(marginPx)
        val x = if (moved) drag.x.coerceIn(marginPx, maxX) else maxX
        val y = if (moved) drag.y.coerceIn(marginPx, maxY) else maxY
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(maxWidth * 0.78f)
                .aspectRatio(16f / 9f)
                .shadow(18.dp, shape)
                .clip(shape)
                .background(Color.Black),
        ) {
            preview()
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(maxX, maxY, marginPx) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var dragging = false
                            var nextX = if (moved) drag.x else maxX
                            var nextY = if (moved) drag.y else maxY
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!dragging) onExpand()
                                    break
                                }
                                val delta = change.positionChange()
                                if (!dragging && (abs(delta.x) > slop || abs(delta.y) > slop)) dragging = true
                                if (dragging) {
                                    change.consume()
                                    nextX = (nextX + delta.x).coerceIn(marginPx, maxX)
                                    nextY = (nextY + delta.y).coerceIn(marginPx, maxY)
                                    drag = Offset(nextX, nextY)
                                    moved = true
                                }
                            }
                        }
                    },
            )
            FloatIcon(
                icon = Icons.Filled.OpenInFull,
                description = stringResource(R.string.full_screen),
                onClick = onExpand,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )
            FloatIcon(
                icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                description = stringResource(if (playing) R.string.pause else R.string.play),
                onClick = commands.toggle,
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
            FloatIcon(
                icon = Icons.Filled.Close,
                description = stringResource(R.string.stop),
                onClick = onClose,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
    }
}

@Composable
private fun RadioFloat(
    entry: MediaEntry,
    countryName: String,
    commands: PlaybackCommands,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWt.current
    val playing by commands.playing.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(palette.surface)
            .clickable(onClick = onExpand),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(108.dp).fillMaxHeight().background(Color.Black)) { preview() }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                entry.name,
                color = palette.heading,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                countryName,
                color = palette.muted,
                fontFamily = Outfit,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FloatIcon(
            icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = stringResource(if (playing) R.string.pause else R.string.play),
            onClick = commands.toggle,
            scrim = false,
        )
        FloatIcon(Icons.Filled.Close, stringResource(R.string.stop), onClose, scrim = false)
    }
}

@Composable
private fun FloatIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color? = null,
    scrim: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWt.current
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (scrim) Color.Black.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint ?: palette.heading, modifier = Modifier.size(20.dp))
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
