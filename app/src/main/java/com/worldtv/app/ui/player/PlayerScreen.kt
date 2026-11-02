@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.worldtv.app.ui.player

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.worldtv.app.ui.components.ChannelArt
import com.worldtv.app.ui.components.IconTile
import com.worldtv.app.ui.components.artBrush
import com.worldtv.app.ui.components.artIcon
import com.worldtv.app.ui.components.artIndex
import com.worldtv.app.ui.theme.LocalWt
import com.worldtv.app.ui.theme.Outfit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class WatchMode { Page, Vertical, Landscape }

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
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.empty_channels_title))
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        return
    }
    var mode by remember(entry.kind) { mutableStateOf(WatchMode.Page) }
    BackHandler(enabled = mode != WatchMode.Page) { mode = WatchMode.Page }
    ApplyWatchMode(mode, television, enabled = entry.kind != ContentKind.RADIO)
    if (entry.kind != ContentKind.RADIO) KeepScreenOn()

    val others = queue.entries.filter { it.key != entry.key }
    val stage = remember {
        movableContentOf { args: StageArgs ->
            VideoStage(
                container = args.container,
                entry = args.entry,
                autoFallback = args.autoFallback,
                immersive = args.immersive,
                onVertical = args.onVertical,
                onFullscreen = args.onFullscreen,
                modifier = args.modifier,
                showChrome = args.showChrome,
                onSurfaceClick = args.onSurfaceClick,
                landscape = args.landscape,
            )
        }
    }
    when {
        entry.kind == ContentKind.RADIO -> RadioPage(
            container, entry, countryName, favorite, others, onBack, onToggleFavorite,
            container.queue::previous, container.queue::next,
        ) { container.queue.select(it.key) }
        mode == WatchMode.Page -> VideoPage(
            stage = stage,
            container = container,
            entry = entry,
            countryName = countryName,
            favorite = favorite,
            autoFallback = autoFallback,
            others = others,
            onBack = onBack,
            onToggleFavorite = { onToggleFavorite(entry) },
            onSelect = { container.queue.select(it.key) },
            onVertical = { mode = WatchMode.Vertical },
            onFullscreen = { mode = WatchMode.Landscape },
        )
        else -> ImmersiveVideo(
            stage = stage,
            container = container,
            entry = entry,
            countryName = countryName,
            favorite = favorite,
            autoFallback = autoFallback,
            mode = mode,
            onBack = { mode = WatchMode.Page },
            onToggleFavorite = { onToggleFavorite(entry) },
            onVertical = { mode = if (mode == WatchMode.Vertical) WatchMode.Page else WatchMode.Vertical },
            onFullscreen = { mode = if (mode == WatchMode.Landscape) WatchMode.Page else WatchMode.Landscape },
        )
    }
}

private data class StageArgs(
    val container: AppContainer,
    val entry: MediaEntry,
    val autoFallback: Boolean,
    val immersive: Boolean,
    val onVertical: () -> Unit,
    val onFullscreen: () -> Unit,
    val modifier: Modifier,
    val showChrome: Boolean = true,
    val onSurfaceClick: () -> Unit = {},
    val landscape: Boolean = false,
)

@Composable
private fun VideoPage(
    stage: @Composable (StageArgs) -> Unit,
    container: AppContainer,
    entry: MediaEntry,
    countryName: String,
    favorite: Boolean,
    autoFallback: Boolean,
    others: List<MediaEntry>,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelect: (MediaEntry) -> Unit,
    onVertical: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val palette = LocalWt.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(palette.bg),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
    ) {
        item {
            PlayerTitle(entry.name, countryName, favorite, onBack, onToggleFavorite)
            Spacer(Modifier.height(4.dp))
            stage(
                StageArgs(
                    container = container,
                    entry = entry,
                    autoFallback = autoFallback,
                    immersive = false,
                    onVertical = onVertical,
                    onFullscreen = onFullscreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .shadow(18.dp, RoundedCornerShape(26.dp), spotColor = palette.accent.copy(alpha = 0.45f), ambientColor = palette.accent.copy(alpha = 0.25f))
                        .clip(RoundedCornerShape(26.dp)),
                ),
            )
            Spacer(Modifier.height(14.dp))
            VolumeRow()
            if (entry.geoBlocked) {
                Spacer(Modifier.height(14.dp))
                GeoNote()
            }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text(
                    stringResource(R.string.more_in, countryName),
                    color = palette.muted,
                    fontSize = 14.sp,
                    fontFamily = Outfit,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                )
            }
        }
        items(others, key = { it.key }) { other ->
            MoreRow(other) { onSelect(other) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun RadioPage(
    container: AppContainer,
    entry: MediaEntry,
    countryName: String,
    favorite: Boolean,
    others: List<MediaEntry>,
    onBack: () -> Unit,
    onToggleFavorite: (MediaEntry) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (MediaEntry) -> Unit,
) {
    val palette = LocalWt.current
    val radio by container.radio.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(notificationsGranted(context)) }
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
    val playing = radio.playing
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(palette.bg),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
    ) {
        item {
            PlayerTitle(entry.name, countryName, favorite, onBack) { onToggleFavorite(entry) }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 0.8f)
                    .shadow(18.dp, RoundedCornerShape(26.dp), spotColor = palette.accent.copy(alpha = 0.45f))
                    .clip(RoundedCornerShape(26.dp))
                    .background(artBrush(artIndex(entry.key))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    artIcon(artIndex(entry.key)),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(96.dp).align(Alignment.BottomEnd).padding(16.dp),
                )
                Equalizer(playing = playing, modifier = Modifier.height(80.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Filled.SkipPrevious, stringResource(R.string.previous), onPrevious)
                Spacer(Modifier.width(22.dp))
                PlayOrb(playing = playing, large = true, onClick = container.radio::toggle)
                Spacer(Modifier.width(22.dp))
                IconTile(Icons.Filled.SkipNext, stringResource(R.string.next), onNext)
            }
            when {
                Build.VERSION.SDK_INT >= 33 && !notificationsAllowed && !notificationsGranted(context) -> {
                    Text(stringResource(R.string.radio_notification_denied), color = palette.heading, fontFamily = Outfit)
                    TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
                radio.buffering -> CircularProgressIndicator(color = palette.accent, modifier = Modifier.padding(top = 8.dp))
                radio.error != null -> {
                    Text(radio.error.orEmpty(), color = palette.heading, fontFamily = Outfit)
                    TextButton(onClick = container.radio::retry) { Text(stringResource(R.string.retry)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            VolumeRow()
            if (entry.geoBlocked) {
                Spacer(Modifier.height(14.dp))
                GeoNote()
            }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text(
                    stringResource(R.string.more_in, countryName),
                    color = palette.muted,
                    fontSize = 14.sp,
                    fontFamily = Outfit,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                )
            }
        }
        items(others, key = { it.key }) { other ->
            MoreRow(other) { onSelect(other) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ImmersiveVideo(
    stage: @Composable (StageArgs) -> Unit,
    container: AppContainer,
    entry: MediaEntry,
    countryName: String,
    favorite: Boolean,
    autoFallback: Boolean,
    mode: WatchMode,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onVertical: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val palette = LocalWt.current
    var chrome by remember(entry.key) { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        stage(
            StageArgs(
                container = container,
                entry = entry,
                autoFallback = autoFallback,
                immersive = true,
                onVertical = onVertical,
                onFullscreen = onFullscreen,
                modifier = Modifier.fillMaxSize(),
                showChrome = chrome,
                onSurfaceClick = { chrome = !chrome },
                landscape = mode == WatchMode.Landscape,
            ),
        )
        if (chrome) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1)
                    Text(countryName, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontFamily = Outfit)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .clickable(onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                        tint = if (favorite) palette.fav else palette.heading,
                    )
                }
            }
            if (mode == WatchMode.Landscape) {
                Text(
                    stringResource(R.string.full_screen),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = Outfit,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoStage(
    container: AppContainer,
    entry: MediaEntry,
    autoFallback: Boolean,
    immersive: Boolean,
    onVertical: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    showChrome: Boolean = true,
    onSurfaceClick: () -> Unit = {},
    landscape: Boolean = false,
) {
    val palette = LocalWt.current
    val context = LocalContext.current
    val plan = PlaybackPlanner.plan(entry)
    var useEmbed by remember(entry.key) { mutableStateOf(plan is PlaybackPlan.Embed) }
    var urlIndex by remember(entry.key) { mutableIntStateOf(0) }
    var retryToken by remember(entry.key) { mutableIntStateOf(0) }
    var buffering by remember(entry.key) { mutableStateOf(plan is PlaybackPlan.Direct) }
    var playing by remember(entry.key) { mutableStateOf(false) }
    var error by remember(entry.key) { mutableStateOf<String?>(null) }
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
    val direct = plan is PlaybackPlan.Direct && !useEmbed
    val stageModifier = if (direct) {
        modifier.background(Color.Black)
    } else {
        modifier.background(artBrush(artIndex(entry.key)))
    }
    Box(
        if (immersive) stageModifier.clickable(onClick = onSurfaceClick) else stageModifier,
    ) {
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
        if (!immersive && entry.kind != ContentKind.RADIO) {
            LiveBadge(Modifier.align(Alignment.TopStart).padding(14.dp))
        }
        if (immersive && showChrome && entry.kind != ContentKind.RADIO) {
            LiveBadge(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 72.dp))
        }
        if (buffering && error == null && direct) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        if (error != null) {
            Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_error_title), color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(error.orEmpty(), color = Color.White, fontFamily = Outfit, fontSize = 14.sp)
                TextButton(onClick = {
                    error = null
                    if (urlIndex == 0) retryToken += 1 else urlIndex = 0
                }) { Text(stringResource(R.string.retry)) }
                val fallback = (plan as? PlaybackPlan.Direct)?.embedFallback
                if (fallback != null && !useEmbed) {
                    TextButton(onClick = { useEmbed = true; error = null }) { Text(stringResource(R.string.try_embed)) }
                }
            }
        } else if (direct && (!immersive || showChrome)) {
            PlayOrb(playing = playing, large = false, onClick = {
                if (player.isPlaying) player.pause() else player.play()
            }, modifier = Modifier.align(Alignment.Center))
        }
        if (!immersive || showChrome) {
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StageButton(Icons.Filled.StayCurrentPortrait, stringResource(R.string.watch_vertical), onVertical)
                StageButton(
                    if (landscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    stringResource(if (landscape) R.string.exit_full_screen else R.string.full_screen),
                    onFullscreen,
                )
            }
        }
    }
}

@Composable
private fun PlayerTitle(
    name: String,
    countryName: String,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val palette = LocalWt.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 18.dp)) {
        IconTile(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = palette.heading, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1)
            Text(countryName, color = palette.muted, fontSize = 14.sp, fontFamily = Outfit, maxLines = 1)
        }
        Box(
            Modifier
                .size(44.dp)
                .shadow(8.dp, RoundedCornerShape(15.dp), spotColor = if (palette.dark) Color(0x40000000) else Color(0x1A465AA0))
                .clip(RoundedCornerShape(15.dp))
                .background(palette.surface)
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                tint = if (favorite) palette.fav else palette.heading,
            )
        }
    }
}

@Composable
private fun MoreRow(entry: MediaEntry, onClick: () -> Unit) {
    val palette = LocalWt.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, spotColor = if (palette.dark) Color(0x33000000) else Color(0x0F465AA0))
            .clip(shape)
            .background(palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelArt(artIndex(entry.key), Modifier.size(48.dp), iconSize = 26.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, color = palette.heading, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
            val lang = entry.languages.firstOrNull()?.lowercase().orEmpty()
            if (lang.isNotEmpty()) Text(lang, color = palette.muted, fontSize = 13.sp, fontFamily = Outfit)
        }
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    val reduce = reducedMotion()
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(1f, 0.3f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "blink")
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.95f))
            .padding(start = 9.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(Color(0xFFE0365F).copy(alpha = if (reduce) 1f else alpha), CircleShape),
        )
        Text(stringResource(R.string.live), color = Color(0xFFE0365F), fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun PlayOrb(playing: Boolean, large: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalWt.current
    val size = if (large) 72.dp else 68.dp
    Box(
        modifier = modifier
            .size(size)
            .shadow(if (large) 14.dp else 10.dp, CircleShape, spotColor = if (large) palette.accent else Color.Black.copy(alpha = 0.18f))
            .clip(CircleShape)
            .background(if (large) Brush.linearGradient(listOf(palette.accentFrom, palette.accent)) else Brush.linearGradient(listOf(Color.White.copy(0.95f), Color.White.copy(0.95f))))
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
private fun StageButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color(0xFF3A4673), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun VolumeRow() {
    val palette = LocalWt.current
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f) }
    var level by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = if (palette.dark) Color(0x40000000) else Color(0x1A465AA0))
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = stringResource(R.string.volume), tint = palette.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Slider(
            value = level.coerceIn(0f, max),
            onValueChange = { value ->
                level = value
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, value.roundToInt(), 0)
            },
            valueRange = 0f..max,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.line,
            ),
        )
    }
}

@Composable
private fun GeoNote() {
    val palette = LocalWt.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.warnBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = palette.warnText, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.geo_note), color = palette.warnText, fontSize = 14.sp, fontFamily = Outfit)
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
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
private fun ApplyWatchMode(mode: WatchMode, television: Boolean, enabled: Boolean) {
    val context = LocalContext.current
    if (television || !enabled) return
    val activity = context as? Activity ?: return
    DisposableEffect(mode) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = when (mode) {
            WatchMode.Page, WatchMode.Vertical -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WatchMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (mode == WatchMode.Page) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity.requestedOrientation = previous
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
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
