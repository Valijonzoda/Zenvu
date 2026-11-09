@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.worldtv.app.ui.player

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.graphics.Outline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.webkit.CookieManager
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.shadow
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
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.worldtv.app.AppContainer
import com.worldtv.app.R
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.PlaybackPlan
import com.worldtv.app.domain.PlaybackPlanner
import com.worldtv.app.ui.components.focusAlpha
import com.worldtv.app.playback.StreamChoices
import com.worldtv.app.playback.applyAudioLanguage
import com.worldtv.app.playback.applyAutoQuality
import com.worldtv.app.playback.applyFixedQuality
import com.worldtv.app.playback.applyTextLanguage
import com.worldtv.app.playback.buildPlayer
import com.worldtv.app.playback.nextLowerQuality
import com.worldtv.app.playback.readChoices
import com.worldtv.app.playback.shouldDropQuality
import com.worldtv.app.ui.components.ChannelCard
import com.worldtv.app.ui.screens.FocusJump
import com.worldtv.app.ui.screens.playbackStripHeight
import com.worldtv.app.ui.components.artBrush
import com.worldtv.app.ui.components.artIndex
import com.worldtv.app.ui.theme.LocalWt
import com.worldtv.app.ui.theme.LocalAppFont
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class PlaybackCommands {
    val playing = MutableStateFlow(false)
    val buffering = MutableStateFlow(false)
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
    val cover = fullscreen && !television
    BoxWithConstraints(Modifier.fillMaxSize().background(if (cover || (fullscreen && television)) Color.Black else palette.bg)) {
        val width = maxWidth
        val height = maxHeight
        val split = !cover && !fullscreen && width > height
        when {
            cover || fullscreen -> {
                Box(Modifier.fillMaxSize()) { stage() }
            }
            split -> {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1.2f).fillMaxHeight()) { stage() }
                    Column(Modifier.weight(0.9f).fillMaxHeight()) {
                        FocusJump(
                            stringResource(R.string.to_player),
                            FocusDirection.Left,
                            Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp),
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
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
            else -> {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(playbackStripHeight(width, height))) { stage() }
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
    onClose: () -> Unit = {},
    television: Boolean = false,
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
            onClose = onClose,
            onToggleOrientation = onToggleOrientation,
            television = television,
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
            onClose = onClose,
            television = television,
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
    onClose: () -> Unit,
    television: Boolean,
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit,
    compact: Boolean,
    commands: PlaybackCommands?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val plan = remember(entry.key) { PlaybackPlanner.plan(entry) }
    var useEmbed by remember(entry.key) { mutableStateOf(plan is PlaybackPlan.Embed) }
    var urlIndex by remember(entry.key) { mutableIntStateOf(0) }
    var retryToken by remember(entry.key) { mutableIntStateOf(0) }
    var embedReady by remember(entry.key) { mutableStateOf(false) }
    var buffering by remember(entry.key) { mutableStateOf(plan is PlaybackPlan.Direct || plan is PlaybackPlan.Embed) }
    var pausedByUser by remember(entry.key) { mutableStateOf(false) }
    var playing by remember(entry.key) { mutableStateOf(false) }
    var error by remember(entry.key) { mutableStateOf<String?>(null) }
    var chrome by remember(entry.key) { mutableStateOf(true) }
    var chromeTick by remember(entry.key) { mutableIntStateOf(0) }
    val trackSelector = remember(context) { DefaultTrackSelector(context.applicationContext) }
    val player = remember(context) { buildPlayer(context.applicationContext, audioOnly = false, trackSelector) }
    val qualityGate = remember(entry.key, urlIndex) { QualityGate() }
    var choices by remember(entry.key, urlIndex) { mutableStateOf(StreamChoices()) }
    var autoCap by remember(entry.key, urlIndex) { mutableIntStateOf(Int.MAX_VALUE) }
    var fixedKey by remember(entry.key, urlIndex) { mutableStateOf<Int?>(null) }
    var audioLanguage by remember(entry.key) { mutableStateOf<String?>(null) }
    var textLanguage by remember(entry.key) { mutableStateOf<String?>(null) }
    var optionsOpen by remember(entry.key) { mutableStateOf(false) }
    qualityGate.auto = fixedKey == null
    qualityGate.cap = autoCap
    qualityGate.fixed = fixedKey
    qualityGate.audio = audioLanguage
    qualityGate.text = textLanguage
    qualityGate.video = choices.video
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    DisposableEffect(player, useEmbed) {
        if (!useEmbed) {
            commands?.toggle = {
                if (player.isPlaying) {
                    pausedByUser = true
                    player.pause()
                } else {
                    pausedByUser = false
                    player.play()
                }
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
                buffering = playbackState == Player.STATE_BUFFERING ||
                    (playbackState == Player.STATE_IDLE && player.playWhenReady)
                if (playbackState == Player.STATE_READY) error = null
                if (playbackState == Player.STATE_BUFFERING && player.playWhenReady) {
                    if (qualityGate.since == 0L) qualityGate.since = SystemClock.elapsedRealtime()
                } else {
                    qualityGate.since = 0L
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val read = readChoices(tracks)
                choices = read
                qualityGate.video = read.video
                if (qualityGate.applying) return
                qualityGate.applying = true
                applyStreamChoices(trackSelector, tracks, qualityGate)
                qualityGate.applying = false
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                if (playbackError.shouldDropQuality() && dropQuality(player, trackSelector, qualityGate) { autoCap = it }) {
                    return
                }
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
    LaunchedEffect(player, entry.key, urlIndex) {
        while (true) {
            delay(1_000)
            if (!qualityGate.auto) continue
            val since = qualityGate.since
            if (since == 0L) continue
            val now = SystemClock.elapsedRealtime()
            if (now - since < 7_000) continue
            dropQuality(player, trackSelector, qualityGate) { autoCap = it }
        }
    }
    LaunchedEffect(fullscreen) {
        if (fullscreen) optionsOpen = false
    }
    BackHandler(enabled = optionsOpen) { optionsOpen = false }
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
        pausedByUser = false
        playing = false
        chrome = true
        player.setMediaItem(MediaItem.Builder().setUri(url).setMediaId(entry.key).build())
        player.prepare()
        player.play()
    }
    val direct = plan is PlaybackPlan.Direct && !useEmbed
    val waiting = error == null && !pausedByUser && !playing && buffering
    LaunchedEffect(waiting) {
        commands?.buffering?.value = waiting
    }
    LaunchedEffect(chrome, chromeTick, playing, error, useEmbed, television) {
        if (television || !chrome || error != null) return@LaunchedEffect
        if (direct && !playing) return@LaunchedEffect
        delay(3_000)
        chrome = false
    }
    val showChrome = !fullscreen && (television || chrome)
    if (!compact && entry.kind != ContentKind.RADIO) KeepScreenOn()
    val density = LocalDensity.current
    val cornerPx = with(density) { 18.dp.toPx() }
    val stageModifier = (if (direct) modifier.background(Color.Black) else modifier.background(artBrush(artIndex(entry.key))))
        .then(if (compact) Modifier else Modifier.clipToBounds())
    Box(stageModifier) {
        when {
            plan is PlaybackPlan.Unsupported -> {
                Text(stringResource(R.string.unsupported_body), color = Color.White, modifier = Modifier.align(Alignment.Center).padding(20.dp), fontFamily = LocalAppFont.current)
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
                    EmbedView(
                        embedUrl,
                        commands,
                        onReady = {
                            if (!embedReady) {
                                embedReady = true
                                buffering = false
                                commands?.playing?.value = true
                            }
                        },
                    )
                }
            }
            else -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        (LayoutInflater.from(viewContext).inflate(R.layout.texture_player_view, null) as PlayerView).apply {
                            this.player = player
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            isClickable = false
                            isFocusable = false
                            setOnTouchListener { _, _ -> false }
                        }
                    },
                    update = { view ->
                        view.player = player
                        view.layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        view.roundSurface(if (compact) cornerPx else 0f)
                        view.requestLayout()
                    },
                    onRelease = { it.player = null },
                )
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
        if (!compact && !fullscreen && error != null) {
            Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_error_title), color = Color.White, fontFamily = LocalAppFont.current, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(if (entry.geoBlocked) R.string.playback_failed_region else R.string.playback_failed),
                    color = Color.White,
                    fontFamily = LocalAppFont.current,
                    fontSize = 14.sp,
                )
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
        } else if (!compact && !fullscreen && waiting) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        } else if (!compact && direct && showChrome && !television) {
            PlayOrb(
                playing = playing,
                large = false,
                onClick = {
                    chromeTick += 1
                    if (player.isPlaying) {
                        pausedByUser = true
                        player.pause()
                    } else {
                        pausedByUser = false
                        player.play()
                    }
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (!compact && showChrome && television) {
            if (optionsOpen) {
                StreamOptionsPanel(
                    choices = choices,
                    auto = fixedKey == null,
                    cap = autoCap,
                    fixedKey = fixedKey,
                    audioLanguage = audioLanguage,
                    textLanguage = textLanguage,
                    onAuto = {
                        fixedKey = null
                        autoCap = Int.MAX_VALUE
                        qualityGate.auto = true
                        qualityGate.cap = Int.MAX_VALUE
                        qualityGate.fixed = null
                        applyAutoQuality(trackSelector, Int.MAX_VALUE)
                    },
                    onQuality = { key ->
                        fixedKey = key
                        qualityGate.auto = false
                        qualityGate.fixed = key
                        applyFixedQuality(trackSelector, player.currentTracks, key)
                    },
                    onAudio = { language ->
                        audioLanguage = language
                        qualityGate.audio = language
                        applyAudioLanguage(trackSelector, language)
                    },
                    onText = { language ->
                        textLanguage = language
                        qualityGate.text = language
                        qualityGate.textTouched = true
                        applyTextLanguage(trackSelector, language)
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 76.dp),
                )
            }
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StageButton(
                    Icons.Filled.Settings,
                    stringResource(R.string.player_settings),
                    { optionsOpen = !optionsOpen },
                )
                StageButton(
                    Icons.Filled.OpenInFull,
                    stringResource(R.string.full_screen),
                    onToggleOrientation,
                )
            }
        }
        if (!compact && showChrome && !television) {
            if (direct) {
                StageButton(
                    Icons.Filled.Settings,
                    stringResource(R.string.player_settings),
                    {
                        chromeTick += 1
                        optionsOpen = !optionsOpen
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                )
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
        if (!compact && showChrome && !television && optionsOpen && direct) {
            StreamOptionsPanel(
                choices = choices,
                auto = fixedKey == null,
                cap = autoCap,
                fixedKey = fixedKey,
                audioLanguage = audioLanguage,
                textLanguage = textLanguage,
                onAuto = {
                    fixedKey = null
                    autoCap = Int.MAX_VALUE
                    qualityGate.auto = true
                    qualityGate.cap = Int.MAX_VALUE
                    qualityGate.fixed = null
                    applyAutoQuality(trackSelector, Int.MAX_VALUE)
                },
                onQuality = { key ->
                    fixedKey = key
                    qualityGate.auto = false
                    qualityGate.fixed = key
                    applyFixedQuality(trackSelector, player.currentTracks, key)
                },
                onAudio = { language ->
                    audioLanguage = language
                    qualityGate.audio = language
                    applyAudioLanguage(trackSelector, language)
                },
                onText = { language ->
                    textLanguage = language
                    qualityGate.text = language
                    qualityGate.textTouched = true
                    applyTextLanguage(trackSelector, language)
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 64.dp),
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
    onClose: () -> Unit,
    onToggleOrientation: () -> Unit,
    television: Boolean,
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit,
    compact: Boolean,
    commands: PlaybackCommands?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWt.current
    val focusManager = LocalFocusManager.current
    var radioOptions by remember { mutableStateOf(false) }
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
    LaunchedEffect(radio.playing, radio.buffering, radio.error) {
        commands?.playing?.value = radio.playing
        commands?.buffering?.value = radio.error == null && radio.buffering
    }
    DisposableEffect(container) {
        commands?.toggle = container.radio::toggle
        onDispose {
            commands?.toggle = {}
            commands?.playing?.value = false
            commands?.buffering?.value = false
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
        if (fullscreen) {
            Equalizer(playing = radio.playing, modifier = Modifier.align(Alignment.Center).height(80.dp))
            return@Box
        }
        if (television) {
            BackHandler(enabled = radioOptions) { radioOptions = false }
            Equalizer(playing = radio.playing, modifier = Modifier.align(Alignment.Center).height(80.dp))
            if (radioOptions) {
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 76.dp)
                        .width(280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(12.dp),
                ) {
                    Text(stringResource(R.string.stream_options), color = Color.White, fontFamily = LocalAppFont.current, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.stream_options_empty), color = Color.White.copy(alpha = 0.8f), fontFamily = LocalAppFont.current, fontSize = 14.sp)
                }
            }
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StageButton(Icons.Filled.Settings, stringResource(R.string.player_settings), onClick = { radioOptions = !radioOptions })
                StageButton(Icons.Filled.OpenInFull, stringResource(R.string.full_screen), onToggleOrientation)
            }
            return@Box
        }
        Equalizer(playing = radio.playing, modifier = Modifier.align(Alignment.Center).height(80.dp))
        if (!granted) {
            Text(
                stringResource(R.string.radio_notification_denied),
                color = Color.White,
                fontFamily = LocalAppFont.current,
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
            if (radio.buffering && radio.error == null) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                }
            } else {
                PlayOrb(playing = radio.playing, large = true, onClick = container.radio::toggle)
            }
            StageButton(Icons.Filled.SkipNext, stringResource(R.string.next), container.queue::next)
        }
        if (radio.error != null) {
            Column(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    radioFailureText(entry, radio.noDirectAudio),
                    color = Color.White,
                    fontFamily = LocalAppFont.current,
                    fontSize = 14.sp,
                )
                TextButton(onClick = container.radio::retry) { Text(stringResource(R.string.retry)) }
            }
        }
        if (fullscreen || television) {
            StageButton(
                if (television) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(if (television) R.string.stop else R.string.back),
                if (television) onClose else onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (television) {
                    StageButton(
                        if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.OpenInFull,
                        stringResource(if (fullscreen) R.string.exit_full_screen else R.string.full_screen),
                        onToggleOrientation,
                    )
                }
                if (television && !fullscreen) {
                    StageButton(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        stringResource(R.string.to_channels),
                        { focusManager.moveFocus(FocusDirection.Right) },
                    )
                }
            }
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
            fontFamily = LocalAppFont.current,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp, start = 64.dp, end = 64.dp),
        )
        Text(
            countryName,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontFamily = LocalAppFont.current,
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
            val threshold = 156.dp.toPx()
            val arm = 64.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                var dragX = 0f
                var dragY = 0f
                var dragged = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        val swipe = abs(dragY) > abs(dragX) * 1.8f && abs(dragY) > threshold
                        if (swipe) {
                            if (dragY > 0f) onSwipeDown() else onSwipeUp()
                        } else {
                            onTap()
                        }
                        break
                    }
                    val delta = change.positionChange()
                    dragX += delta.x
                    dragY += delta.y
                    if (!dragged && abs(dragY) > arm && abs(dragY) > abs(dragX) * 1.8f) {
                        dragged = true
                        change.consume()
                    } else if (dragged) {
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
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(size)
            .border(if (focused) 3.dp else 0.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .background(if (large) Brush.linearGradient(listOf(palette.accentFrom, palette.accent)) else Brush.linearGradient(listOf(Color.White, Color.White)))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
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

private class QualityGate {
    var auto: Boolean = true
    var cap: Int = Int.MAX_VALUE
    var fixed: Int? = null
    var audio: String? = null
    var text: String? = null
    var textTouched: Boolean = false
    var applying: Boolean = false
    var video: List<com.worldtv.app.playback.QualityOption> = emptyList()
    var since: Long = 0L
    var lastDrop: Long = 0L
}

private fun dropQuality(
    player: ExoPlayer,
    selector: DefaultTrackSelector,
    gate: QualityGate,
    publish: (Int) -> Unit,
): Boolean {
    if (!gate.auto) return false
    val now = SystemClock.elapsedRealtime()
    if (now - gate.lastDrop < 5_000) return false
    val next = nextLowerQuality(gate.video, gate.cap) ?: return false
    gate.lastDrop = now
    gate.since = now
    gate.cap = next
    publish(next)
    applyAutoQuality(selector, next)
    if (player.playerError != null || player.playbackState == Player.STATE_IDLE) {
        player.prepare()
        player.play()
    } else if (player.isCurrentMediaItemLive) {
        player.seekToDefaultPosition()
    } else {
        player.seekTo(player.currentPosition.coerceAtLeast(0L))
    }
    return true
}

private fun applyStreamChoices(selector: DefaultTrackSelector, tracks: Tracks, gate: QualityGate) {
    val fixed = gate.fixed
    if (gate.auto) applyAutoQuality(selector, gate.cap)
    else if (fixed != null) applyFixedQuality(selector, tracks, fixed)
    if (gate.audio != null) applyAudioLanguage(selector, gate.audio)
    if (gate.textTouched) applyTextLanguage(selector, gate.text)
}

@Composable
private fun StreamOptionsPanel(
    choices: StreamChoices,
    auto: Boolean,
    cap: Int,
    fixedKey: Int?,
    audioLanguage: String?,
    textLanguage: String?,
    onAuto: () -> Unit,
    onQuality: (Int) -> Unit,
    onAudio: (String) -> Unit,
    onText: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val capLabel = choices.video.firstOrNull { it.key == cap }?.label()
    val autoLabel = if (!auto || cap == Int.MAX_VALUE || capLabel == null) {
        stringResource(R.string.quality_auto)
    } else {
        stringResource(R.string.quality_auto_up_to, capLabel)
    }
    Column(
        modifier
            .width(280.dp)
            .heightIn(max = 420.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.stream_options),
            color = Color.White,
            fontFamily = LocalAppFont.current,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        if (choices.video.size >= 2) {
            OptionLabel(stringResource(R.string.quality_title))
            OptionRow(autoLabel, auto, onAuto)
            choices.video.forEach { option ->
                OptionRow(option.label(), !auto && fixedKey == option.key) { onQuality(option.key) }
            }
        }
        if (choices.audio.size >= 2) {
            OptionLabel(stringResource(R.string.audio_language))
            choices.audio.forEach { option ->
                OptionRow(option.label, audioLanguage == option.language) { onAudio(option.language) }
            }
        }
        if (choices.text.isNotEmpty()) {
            OptionLabel(stringResource(R.string.subtitles))
            OptionRow(stringResource(R.string.subtitles_off), textLanguage == null) { onText(null) }
            choices.text.forEach { option ->
                OptionRow(option.label, textLanguage == option.language) { onText(option.language) }
            }
        }
        if (choices.video.size < 2 && choices.audio.size < 2 && choices.text.isEmpty()) {
            Text(
                stringResource(R.string.stream_options_empty),
                color = Color.White.copy(alpha = 0.8f),
                fontFamily = LocalAppFont.current,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.7f),
        fontFamily = LocalAppFont.current,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pulse = focusAlpha(focused)
    val shape = RoundedCornerShape(12.dp)
    Text(
        label,
        color = Color.White,
        fontFamily = LocalAppFont.current,
        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected || focused) Color(0xFF3D7EFF).copy(alpha = if (focused) pulse else 0.85f) else Color.White.copy(alpha = 0.08f))
            .border(if (focused) 3.dp else 0.dp, Color.White.copy(alpha = pulse), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun StageButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF3A4673),
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pulse = focusAlpha(focused)
    Box(
        modifier
            .size(48.dp)
            .border(if (focused) 4.dp else 0.dp, Color.White.copy(alpha = pulse), CircleShape)
            .clip(CircleShape)
            .background(if (focused) Color(0xFF3D7EFF) else Color.White.copy(alpha = 0.92f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (focused) Color.White else tint, modifier = Modifier.size(22.dp))
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

private fun youtubeShell(id: String): String {
    val src = "https://www.youtube-nocookie.com/embed/$id?autoplay=1&rel=0&modestbranding=1&playsinline=1&enablejsapi=1"
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{position:fixed;inset:0;width:100%;height:100%;border:0}</style>
        </head>
        <body><iframe src="$src" allow="autoplay; encrypted-media; fullscreen; picture-in-picture" allowfullscreen></iframe></body>
        </html>
    """.trimIndent()
}

@Composable
private fun EmbedView(url: String, commands: PlaybackCommands?, onReady: () -> Unit = {}) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val ready = rememberUpdatedState(onReady)
    AndroidView(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
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
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        ready.value()
                    }
                }
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
                val id = PlaybackPlanner.youtubeId(url)
                if (id == null) {
                    view.loadUrl(url)
                } else {
                    view.loadDataWithBaseURL(
                        "https://www.youtube-nocookie.com",
                        youtubeShell(id),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            }
        },
        onRelease = { it.destroy() },
    )
    DisposableEffect(webView, commands) {
        val view = webView
        val toggle = if (view != null && commands != null) {
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
    val buffering by commands.buffering.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val spot = remember { FloatSpot() }
    var frame by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(modifier) {
        val marginPx = with(density) { 12.dp.toPx() }
        val widthPx = with(density) { (maxWidth * 0.78f).toPx() }
        val heightPx = widthPx * 9f / 16f
        val parentW = with(density) { maxWidth.toPx() }
        val parentH = with(density) { maxHeight.toPx() }
        val maxX = (parentW - widthPx - marginPx).coerceAtLeast(marginPx)
        val maxY = (parentH - heightPx - marginPx).coerceAtLeast(marginPx)
        val placedX = if (spot.moved) spot.x.coerceIn(marginPx, maxX) else maxX
        val placedY = if (spot.moved) spot.y.coerceIn(marginPx, maxY) else maxY
        val windowOffset = remember(placedX, placedY, frame) {
            IntOffset(placedX.roundToInt(), placedY.roundToInt())
        }
        Box(
            Modifier
                .offset { windowOffset }
                .width(maxWidth * 0.78f)
                .aspectRatio(16f / 9f)
                .shadow(18.dp, shape, clip = false)
                .background(Color.Black),
        ) {
            preview()
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(maxX, maxY, marginPx) {
                        val slop = 32.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var dragging = false
                            var totalX = 0f
                            var totalY = 0f
                            var nextX = if (spot.moved) spot.x else maxX
                            var nextY = if (spot.moved) spot.y else maxY
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!dragging) onExpand()
                                    break
                                }
                                val delta = change.positionChange()
                                totalX += delta.x
                                totalY += delta.y
                                val passedSlop = abs(totalX) > slop || abs(totalY) > slop
                                if (!dragging && passedSlop) {
                                    dragging = true
                                    nextX = (nextX + totalX).coerceIn(marginPx, maxX)
                                    nextY = (nextY + totalY).coerceIn(marginPx, maxY)
                                } else if (dragging) {
                                    nextX = (nextX + delta.x).coerceIn(marginPx, maxX)
                                    nextY = (nextY + delta.y).coerceIn(marginPx, maxY)
                                }
                                if (dragging) {
                                    change.consume()
                                    spot.x = nextX
                                    spot.y = nextY
                                    spot.moved = true
                                    frame += 1
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
            if (buffering) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(6.dp).size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            } else {
                FloatIcon(
                    icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    description = stringResource(if (playing) R.string.pause else R.string.play),
                    onClick = commands.toggle,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }
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
    val buffering by commands.buffering.collectAsStateWithLifecycle()
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
                fontFamily = LocalAppFont.current,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                countryName,
                color = palette.muted,
                fontFamily = LocalAppFont.current,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (buffering) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        } else {
            FloatIcon(
                icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                description = stringResource(if (playing) R.string.pause else R.string.play),
                onClick = commands.toggle,
                scrim = false,
            )
        }
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
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .size(44.dp)
            .border(if (focused) 3.dp else 0.dp, (if (scrim) Color.White else palette.accent).copy(alpha = focusAlpha(focused)), CircleShape)
            .clip(CircleShape)
            .background(if (scrim) Color.Black.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint ?: palette.heading, modifier = Modifier.size(20.dp))
    }
}

private fun PlayerView.roundSurface(radiusPx: Float) {
    val surface = videoSurfaceView ?: return
    if (radiusPx <= 0f) {
        surface.clipToOutline = false
        return
    }
    if (surface.width == 0 || surface.height == 0) {
        surface.post { roundSurface(radiusPx) }
        return
    }
    surface.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
        }
    }
    surface.clipToOutline = true
}

@Composable
private fun radioFailureText(entry: com.worldtv.app.domain.MediaEntry, noDirectAudio: Boolean): String = when {
    noDirectAudio -> stringResource(R.string.error_no_direct_audio)
    entry.geoBlocked -> stringResource(R.string.playback_failed_region)
    else -> stringResource(R.string.playback_failed)
}

private class FloatSpot {
    var x = Float.NaN
    var y = Float.NaN
    var moved = false
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
