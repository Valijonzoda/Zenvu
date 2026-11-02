package com.worldtv.app.ui

import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldtv.app.AppContainer
import com.worldtv.app.R
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.ThemeChoice
import com.worldtv.app.domain.UserSettings
import com.worldtv.app.ui.catalog.CatalogViewModel
import com.worldtv.app.ui.components.WorldBottomBar
import com.worldtv.app.ui.player.FloatingPlayer
import com.worldtv.app.ui.player.PlaybackCommands
import com.worldtv.app.ui.player.PlaybackStage
import com.worldtv.app.ui.player.PlayerScreen
import com.worldtv.app.ui.screens.CountryChannelsPane
import com.worldtv.app.ui.screens.FavoritesPane
import com.worldtv.app.ui.screens.HomePane
import com.worldtv.app.ui.screens.PrivacyPane
import com.worldtv.app.ui.screens.SettingsPane
import com.worldtv.app.ui.theme.LocalWt
import com.worldtv.app.ui.theme.Space
import com.worldtv.app.ui.theme.WorldTvTheme
import kotlinx.coroutines.launch

@Composable
fun WorldTvApp(container: AppContainer, television: Boolean) {
    val navigator: AppNavigator = viewModel()
    val catalogModel: CatalogViewModel = viewModel(factory = viewModelFactory {
        CatalogViewModel(container.catalog, container.favorites, container.settings)
    })
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val catalog by catalogModel.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = UserSettings(),
        lifecycle = lifecycle,
    )
    val radio by container.radio.state.collectAsStateWithLifecycle()
    val queue by container.queue.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val cacheCleared = stringResource(R.string.cache_cleared)
    var heldLandscape by remember { mutableStateOf(false) }
    var preferPortrait by remember { mutableStateOf(false) }
    var forceLandscape by remember { mutableStateOf(false) }
    var tvFullscreen by remember { mutableStateOf(false) }
    val screen = navigator.current
    val currentEntry = queue.current
    val inline = screen == AppScreen.Countries &&
        currentEntry != null &&
        catalog.selectedCodes.any { it.equals(currentEntry.countryCode, ignoreCase = true) }
    val inPlayer = screen == AppScreen.Player && currentEntry != null
    val playerOpen = currentEntry != null
    val fullscreen = when {
        television -> tvFullscreen && playerOpen
        !playerOpen || preferPortrait -> false
        else -> heldLandscape || forceLandscape
    }
    val floating = playerOpen && !inline && !inPlayer && !fullscreen
    val commands = remember { PlaybackCommands() }
    val sharedStage = remember(container, commands) {
        movableContentOf { request: StageRequest ->
            PlaybackStage(
                container = container,
                entry = request.entry,
                countryName = request.countryName,
                favorite = request.favorite,
                autoFallback = request.autoFallback,
                fullscreen = request.fullscreen,
                compact = request.compact,
                commands = commands,
                onToggleFavorite = request.onToggleFavorite,
                onBack = request.onBack,
                onToggleOrientation = request.onToggleOrientation,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    DisposableEffect(context) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var pending: Runnable? = null
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(angle: Int) {
                if (angle == ORIENTATION_UNKNOWN) return
                val want = when {
                    angle in 70..110 || angle in 250..290 -> true
                    angle <= 20 || angle >= 340 || angle in 160..200 -> false
                    else -> null
                }
                if (want == null || want == heldLandscape) {
                    pending?.let(handler::removeCallbacks)
                    pending = null
                    return
                }
                if (pending != null) return
                val job = Runnable {
                    pending = null
                    heldLandscape = want
                }
                pending = job
                handler.postDelayed(job, 450)
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose {
            listener.disable()
            pending?.let(handler::removeCallbacks)
        }
    }
    LaunchedEffect(heldLandscape) {
        if (!heldLandscape) preferPortrait = false
        if (heldLandscape) forceLandscape = false
    }
    DisposableEffect(playerOpen, preferPortrait, forceLandscape, heldLandscape, television, fullscreen) {
        val host = activity
        if (host != null && !television) {
            val landscapeVideo = playerOpen && !preferPortrait && (forceLandscape || heldLandscape)
            host.requestedOrientation = if (landscapeVideo) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            val bars = WindowCompat.getInsetsController(host.window, host.window.decorView)
            if (fullscreen) {
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                bars.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (host != null && !television) {
                WindowCompat.getInsetsController(host.window, host.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun closePlayer() {
        tvFullscreen = false
        preferPortrait = false
        forceLandscape = false
        navigator.back()
    }

    fun dismissPlayback() {
        container.radio.stop()
        container.queue.clear()
        tvFullscreen = false
        forceLandscape = false
        preferPortrait = false
    }

    fun expandPlayback() {
        val entry = container.queue.state.value.current ?: return
        catalogModel.selectKind(entry.kind)
        catalogModel.setCountries(setOf(entry.countryCode))
        navigator.openCountries()
    }

    fun toggleOrientation() {
        if (television) {
            tvFullscreen = !tvFullscreen
            return
        }
        if (fullscreen) {
            preferPortrait = true
            forceLandscape = false
        } else {
            preferPortrait = false
            forceLandscape = true
        }
    }

    fun openEntry(entries: List<MediaEntry>, entry: MediaEntry) {
        val index = entries.indexOfFirst { it.key == entry.key }.coerceAtLeast(0)
        container.queue.open(entries, index)
        if (entry.kind != ContentKind.RADIO) container.radio.pause()
        navigator.openPlayer()
    }

    fun playInList(entries: List<MediaEntry>, entry: MediaEntry) {
        val index = entries.indexOfFirst { it.key == entry.key }.coerceAtLeast(0)
        container.queue.open(entries, index)
        if (entry.kind != ContentKind.RADIO) container.radio.pause()
    }

    BackHandler {
        when {
            fullscreen && !television -> {
                preferPortrait = true
                forceLandscape = false
            }
            television && tvFullscreen -> tvFullscreen = false
            navigator.current == AppScreen.Player -> closePlayer()
            !navigator.back() -> activity?.finish()
        }
    }

    val stage: @Composable () -> Unit = {
        val shown = queue.current
        if (shown != null) {
            sharedStage(
                StageRequest(
                    entry = shown,
                    countryName = catalog.countryName(shown.countryCode),
                    favorite = shown.key in catalog.favoriteKeys,
                    autoFallback = settings.autoFallbackStreams,
                    fullscreen = fullscreen && !floating,
                    compact = floating,
                    onToggleFavorite = { catalogModel.toggleFavorite(shown) },
                    onBack = ::toggleOrientation,
                    onToggleOrientation = ::toggleOrientation,
                ),
            )
        }
    }
    val cover = fullscreen && !inline && !inPlayer

    WorldTvTheme(choice = settings.theme, television = television, kind = catalog.kind) {
    Box(Modifier.fillMaxSize().background(LocalWt.current.bg)) {
        if (television) {
            TelevisionShell(
                navigator = navigator,
                catalog = catalog,
                catalogModel = catalogModel,
                settings = settings,
                container = container,
                snackbar = snackbar,
                onTheme = { scope.launch { container.settings.setTheme(it) } },
                onFallback = { scope.launch { container.settings.setAutoFallback(it) } },
                onBackground = { scope.launch { container.settings.setBackgroundRadio(it) } },
                onClearCache = {
                    scope.launch {
                        container.catalog.clearCache()
                        catalogModel.refresh()
                        snackbar.showSnackbar(cacheCleared)
                    }
                },
                onOpen = ::openEntry,
                onPlayInList = ::playInList,
                onClosePlayer = ::closePlayer,
                fullscreen = fullscreen,
                onToggleOrientation = ::toggleOrientation,
                floating = floating,
                cover = cover,
                nowPlaying = currentEntry,
                commands = commands,
                onExpandPlayback = ::expandPlayback,
                onDismissPlayback = ::dismissPlayback,
                stage = stage,
                embedPlayback = inline,
            )
        } else {
            PhoneShell(
                navigator = navigator,
                catalog = catalog,
                catalogModel = catalogModel,
                settings = settings,
                radioEntry = radio.entry,
                radioPlaying = radio.playing,
                container = container,
                snackbar = snackbar,
                onTheme = { scope.launch { container.settings.setTheme(it) } },
                onFallback = { scope.launch { container.settings.setAutoFallback(it) } },
                onBackground = { scope.launch { container.settings.setBackgroundRadio(it) } },
                onClearCache = {
                    scope.launch {
                        container.catalog.clearCache()
                        catalogModel.refresh()
                        snackbar.showSnackbar(cacheCleared)
                    }
                },
                onOpen = ::openEntry,
                onClosePlayer = ::closePlayer,
                onPlayInList = ::playInList,
                fullscreen = fullscreen,
                onToggleOrientation = ::toggleOrientation,
                floating = floating,
                cover = cover,
                nowPlaying = currentEntry,
                commands = commands,
                onExpandPlayback = ::expandPlayback,
                onDismissPlayback = ::dismissPlayback,
                stage = stage,
                embedPlayback = inline,
                onRadioToggle = container.radio::toggle,
                onRadioStop = container.radio::stop,
            )
        }
    }
    }
}

@Composable
private fun PhoneShell(
    navigator: AppNavigator,
    catalog: com.worldtv.app.ui.catalog.CatalogUiState,
    catalogModel: CatalogViewModel,
    settings: com.worldtv.app.domain.UserSettings,
    radioEntry: MediaEntry?,
    radioPlaying: Boolean,
    container: AppContainer,
    snackbar: SnackbarHostState,
    onTheme: (ThemeChoice) -> Unit,
    onFallback: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onOpen: (List<MediaEntry>, MediaEntry) -> Unit,
    onClosePlayer: () -> Unit,
    onPlayInList: (List<MediaEntry>, MediaEntry) -> Unit,
    fullscreen: Boolean,
    onToggleOrientation: () -> Unit,
    floating: Boolean,
    cover: Boolean,
    nowPlaying: MediaEntry?,
    commands: PlaybackCommands,
    onExpandPlayback: () -> Unit,
    onDismissPlayback: () -> Unit,
    stage: @Composable () -> Unit,
    embedPlayback: Boolean,
    onRadioToggle: () -> Unit,
    onRadioStop: () -> Unit,
) {
    val screen = navigator.current
    val showBar = !fullscreen && screen != AppScreen.Player
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                WorldBottomBar(
                    explore = screen == AppScreen.Catalog || screen == AppScreen.Countries,
                    favorites = screen == AppScreen.Favorites,
                    settings = screen == AppScreen.Settings || screen == AppScreen.Privacy,
                    onExplore = { navigator.openSection(AppScreen.Catalog) },
                    onFavorites = { navigator.openSection(AppScreen.Favorites) },
                    onSettings = { navigator.openSection(AppScreen.Settings) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().then(if (fullscreen) Modifier else Modifier.padding(padding))) {
            ScreenBody(
                screen = screen,
                television = false,
                fullscreen = fullscreen,
                catalog = catalog,
                catalogModel = catalogModel,
                settings = settings,
                container = container,
                onTheme = onTheme,
                onFallback = onFallback,
                onBackground = onBackground,
                onClearCache = onClearCache,
                onOpen = onOpen,
                onPlayInList = onPlayInList,
                onClosePlayer = onClosePlayer,
                onToggleOrientation = onToggleOrientation,
                onBack = navigator::back,
                onCountries = navigator::openCountries,
                onPrivacy = navigator::openPrivacy,
                embedPlayback = embedPlayback,
                stage = if (floating || cover) {
                    {}
                } else {
                    stage
                },
            )
            if (cover) {
                Box(Modifier.fillMaxSize().background(Color.Black)) { stage() }
            }
            if (floating && nowPlaying != null) {
                FloatingPlayer(
                    entry = nowPlaying,
                    countryName = catalog.countryName(nowPlaying.countryCode),
                    commands = commands,
                    onExpand = onExpandPlayback,
                    onClose = onDismissPlayback,
                    preview = stage,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun TelevisionShell(
    navigator: AppNavigator,
    catalog: com.worldtv.app.ui.catalog.CatalogUiState,
    catalogModel: CatalogViewModel,
    settings: com.worldtv.app.domain.UserSettings,
    container: AppContainer,
    snackbar: SnackbarHostState,
    onTheme: (ThemeChoice) -> Unit,
    onFallback: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onOpen: (List<MediaEntry>, MediaEntry) -> Unit,
    onPlayInList: (List<MediaEntry>, MediaEntry) -> Unit,
    onClosePlayer: () -> Unit,
    fullscreen: Boolean,
    onToggleOrientation: () -> Unit,
    floating: Boolean,
    cover: Boolean,
    nowPlaying: MediaEntry?,
    commands: PlaybackCommands,
    onExpandPlayback: () -> Unit,
    onDismissPlayback: () -> Unit,
    stage: @Composable () -> Unit,
    embedPlayback: Boolean,
) {
    val screen = navigator.current
    Row(Modifier.fillMaxSize()) {
        if (screen != AppScreen.Player) {
            TvRail(
                current = screen,
                kind = catalog.kind,
                onCatalog = { navigator.openSection(AppScreen.Catalog) },
                onCountries = navigator::openCountries,
                onFavorites = { navigator.openSection(AppScreen.Favorites) },
                onSettings = { navigator.openSection(AppScreen.Settings) },
                onKind = catalogModel::selectKind,
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            ScreenBody(
                screen = screen,
                television = true,
                fullscreen = fullscreen,
                catalog = catalog,
                catalogModel = catalogModel,
                settings = settings,
                container = container,
                onTheme = onTheme,
                onFallback = onFallback,
                onBackground = onBackground,
                onClearCache = onClearCache,
                onOpen = onOpen,
                onPlayInList = onPlayInList,
                onClosePlayer = onClosePlayer,
                onToggleOrientation = onToggleOrientation,
                onBack = navigator::back,
                onCountries = navigator::openCountries,
                onPrivacy = navigator::openPrivacy,
                embedPlayback = embedPlayback,
                stage = if (floating || cover) {
                    {}
                } else {
                    stage
                },
            )
            if (cover) {
                Box(Modifier.fillMaxSize().background(Color.Black)) { stage() }
            }
            if (floating && nowPlaying != null) {
                FloatingPlayer(
                    entry = nowPlaying,
                    countryName = catalog.countryName(nowPlaying.countryCode),
                    commands = commands,
                    onExpand = onExpandPlayback,
                    onClose = onDismissPlayback,
                    preview = stage,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(Space.lg))
        }
    }
}

@Composable
private fun ScreenBody(
    screen: AppScreen,
    television: Boolean,
    fullscreen: Boolean,
    catalog: com.worldtv.app.ui.catalog.CatalogUiState,
    catalogModel: CatalogViewModel,
    settings: com.worldtv.app.domain.UserSettings,
    container: AppContainer,
    onTheme: (ThemeChoice) -> Unit,
    onFallback: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onOpen: (List<MediaEntry>, MediaEntry) -> Unit,
    onPlayInList: (List<MediaEntry>, MediaEntry) -> Unit,
    onClosePlayer: () -> Unit,
    onToggleOrientation: () -> Unit,
    onBack: () -> Unit,
    onCountries: () -> Unit,
    onPrivacy: () -> Unit,
    embedPlayback: Boolean,
    stage: @Composable () -> Unit,
) {
    val queue by container.queue.state.collectAsStateWithLifecycle()
    val playing = queue.current?.takeIf { embedPlayback }
    when (screen) {
        AppScreen.Catalog -> HomePane(
            state = catalog,
            onKind = catalogModel::selectKind,
            onQuery = catalogModel::setCountryQuery,
            onRefresh = catalogModel::refresh,
            onOpenCountry = { code ->
                catalogModel.setCountries(setOf(code))
                onCountries()
            },
        )
        AppScreen.Countries -> CountryChannelsPane(
            state = catalog,
            onBack = onBack,
            onRefresh = catalogModel::refresh,
            onOpen = onPlayInList,
            onToggleFavorite = catalogModel::toggleFavorite,
            playingKey = playing?.key,
            fullscreen = fullscreen && playing != null,
            player = stage,
        )
        AppScreen.Favorites -> FavoritesPane(
            state = catalog,
            onOpen = { entries, entry ->
                if (catalog.kind != entry.kind) catalogModel.selectKind(entry.kind)
                onOpen(entries, entry)
            },
            onToggleFavorite = catalogModel::toggleFavorite,
        )
        AppScreen.Settings -> SettingsPane(
            settings = settings,
            onTheme = onTheme,
            onFallback = onFallback,
            onBackgroundRadio = onBackground,
            onClearCache = onClearCache,
            onPrivacy = onPrivacy,
        )
        AppScreen.Privacy -> PrivacyPane()
        AppScreen.Player -> PlayerScreen(
            container = container,
            television = television,
            favorite = queue.current?.key in catalog.favoriteKeys,
            countryName = queue.current?.let { catalog.countryName(it.countryCode) }.orEmpty(),
            autoFallback = settings.autoFallbackStreams,
            fullscreen = fullscreen,
            onToggleFavorite = catalogModel::toggleFavorite,
            onBack = if (fullscreen && !television) onToggleOrientation else onClosePlayer,
            onToggleOrientation = onToggleOrientation,
            channels = queue.entries,
            favoriteKeys = catalog.favoriteKeys,
            onSelect = { entry -> container.queue.select(entry.key) },
            stage = stage,
        )
    }
}

@Composable
private fun TvRail(
    current: AppScreen,
    kind: ContentKind,
    onCatalog: () -> Unit,
    onCountries: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    onKind: (ContentKind) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Space.md))
        ContentKind.entries.forEach { item ->
            RailButton(
                label = item.title,
                selected = current == AppScreen.Catalog && kind == item,
                onClick = { onKind(item); onCatalog() },
            )
        }
        Spacer(Modifier.height(Space.md))
        RailButton(stringResource(R.string.nav_discover), current == AppScreen.Catalog, onCatalog, Icons.Filled.Home)
        RailButton(stringResource(R.string.nav_countries), current == AppScreen.Countries, onCountries, Icons.Filled.Public)
        RailButton(stringResource(R.string.nav_favorites), current == AppScreen.Favorites, onFavorites, Icons.Filled.Favorite)
        RailButton(stringResource(R.string.nav_settings), current == AppScreen.Settings || current == AppScreen.Privacy, onSettings, Icons.Filled.Settings)
    }
}

@Composable
private fun RailButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    focused -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                },
                RoundedCornerShape(16.dp),
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(Space.md))
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

private data class StageRequest(
    val entry: MediaEntry,
    val countryName: String,
    val favorite: Boolean,
    val autoFallback: Boolean,
    val fullscreen: Boolean,
    val compact: Boolean,
    val onToggleFavorite: () -> Unit,
    val onBack: () -> Unit,
    val onToggleOrientation: () -> Unit,
)
