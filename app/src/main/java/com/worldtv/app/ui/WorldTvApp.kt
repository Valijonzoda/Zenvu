package com.worldtv.app.ui

import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldtv.app.AppContainer
import com.worldtv.app.R
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.ui.components.labelRes
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.ThemeChoice
import com.worldtv.app.domain.UserSettings
import com.worldtv.app.ui.account.AccountHost
import com.worldtv.app.ui.account.AccountViewModel
import com.worldtv.app.ui.account.LocalAccountModel
import com.worldtv.app.ui.catalog.CatalogViewModel
import com.worldtv.app.ui.components.WorldBottomBar
import com.worldtv.app.ui.player.FloatingPlayer
import com.worldtv.app.ui.player.PlaybackCommands
import com.worldtv.app.ui.player.PlaybackStage
import com.worldtv.app.ui.player.PlayerScreen
import com.worldtv.app.ui.screens.AllChannelsPane
import com.worldtv.app.ui.screens.CountryChannelsPane
import com.worldtv.app.ui.screens.FavoritesPane
import com.worldtv.app.ui.screens.HomePane
import com.worldtv.app.ui.screens.PrivacyPane
import com.worldtv.app.ui.screens.SettingsPane
import com.worldtv.app.ui.theme.LocalWt
import com.worldtv.app.ui.theme.LocalAppFont
import com.worldtv.app.ui.theme.Space
import com.worldtv.app.ui.theme.WorldTvTheme
import kotlinx.coroutines.launch

@Composable
fun WorldTvApp(container: AppContainer, television: Boolean) {
    val navigator: AppNavigator = viewModel()
    val catalogModel: CatalogViewModel = viewModel(factory = viewModelFactory {
        CatalogViewModel(container.catalog, container.favorites, container.settings, container.favoriteCountries)
    })
    val accountModel: AccountViewModel = viewModel(factory = viewModelFactory {
        AccountViewModel(container.auth, container.favorites)
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
    DisposableEffect(activity) {
        if (activity != null) container.attachActivity(activity)
        onDispose { container.detachActivity(activity) }
    }
    val cacheCleared = stringResource(R.string.cache_cleared)
    var heldLandscape by remember { mutableStateOf(false) }
    var landscapeOrientation by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) }
    var preferPortrait by remember { mutableStateOf(false) }
    var forceLandscape by remember { mutableStateOf(false) }
    var minimized by remember { mutableStateOf(false) }
    var tvFullscreen by remember { mutableStateOf(false) }
    val screen = navigator.current
    val currentEntry = queue.current
    val inline = !minimized &&
        screen == AppScreen.Countries &&
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
                onSwipeDown = request.onSwipeDown,
                onSwipeUp = request.onSwipeUp,
                onClose = request.onClose,
                television = request.television,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    DisposableEffect(context) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var pending: Runnable? = null
        var pendingWant: Boolean? = null
        var stray = 0
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(angle: Int) {
                if (angle == ORIENTATION_UNKNOWN) return
                val want = when {
                    angle in 80..100 || angle in 260..280 -> true
                    angle <= 28 || angle >= 332 || angle in 155..205 -> false
                    else -> null
                }
                if (want == true && !heldLandscape && !forceLandscape) {
                    landscapeOrientation = landscapeSide(angle)
                }
                if (want == null) {
                    stray += 1
                    if (stray < 4) return
                    pending?.let(handler::removeCallbacks)
                    pending = null
                    pendingWant = null
                    return
                }
                stray = 0
                if (want == heldLandscape) {
                    pending?.let(handler::removeCallbacks)
                    pending = null
                    pendingWant = null
                    return
                }
                if (pending != null && pendingWant == want) return
                pending?.let(handler::removeCallbacks)
                val job = Runnable {
                    pending = null
                    pendingWant = null
                    if (want) landscapeOrientation = landscapeSide(angle)
                    heldLandscape = want
                }
                pending = job
                pendingWant = want
                handler.postDelayed(job, 1200)
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
    DisposableEffect(playerOpen, preferPortrait, forceLandscape, heldLandscape, landscapeOrientation, television, fullscreen) {
        val host = activity
        if (host != null) {
            if (!television) {
                val landscapeVideo = playerOpen && !preferPortrait && (forceLandscape || heldLandscape)
                host.requestedOrientation = if (landscapeVideo) {
                    landscapeOrientation
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
            val bars = WindowCompat.getInsetsController(host.window, host.window.decorView)
            if (fullscreen) {
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                bars.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
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
        minimized = false
        if (navigator.current == AppScreen.Player) navigator.back()
    }

    fun expandPlayback() {
        minimized = false
        val entry = container.queue.state.value.current ?: return
        if (catalog.kind != entry.kind) catalogModel.selectKind(entry.kind)
        catalogModel.setCountries(setOf(entry.countryCode))
        if (heldLandscape) {
            preferPortrait = false
            forceLandscape = true
        } else {
            forceLandscape = false
            preferPortrait = false
        }
        navigator.openCountries()
    }

    fun collapsePlayer() {
        if (fullscreen) {
            preferPortrait = true
            forceLandscape = false
            minimized = false
            tvFullscreen = false
            return
        }
        if (navigator.current == AppScreen.Player) {
            closePlayer()
            return
        }
        minimized = true
    }

    fun goLandscape() {
        if (television) {
            tvFullscreen = true
            return
        }
        if (fullscreen) return
        minimized = false
        preferPortrait = false
        forceLandscape = true
    }

    fun showCountries() {
        minimized = false
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
        minimized = false
        val index = entries.indexOfFirst { it.key == entry.key }.coerceAtLeast(0)
        container.queue.open(entries, index)
        if (entry.kind != ContentKind.RADIO) container.radio.pause()
    }

    BackHandler {
        when {
            television && tvFullscreen -> tvFullscreen = false
            television && playerOpen -> dismissPlayback()
            fullscreen && !television -> {
                preferPortrait = true
                forceLandscape = false
            }
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
                    onSwipeDown = ::collapsePlayer,
                    onSwipeUp = ::goLandscape,
                    onClose = ::dismissPlayback,
                    television = television,
                ),
            )
        }
    }
    val cover = fullscreen && !inline && !inPlayer

    WorldTvTheme(choice = settings.theme, television = television, kind = catalog.kind) {
    if (com.worldtv.app.data.LocaleApplier.read(context) == null) {
        com.worldtv.app.ui.language.LanguagePicker { tag ->
            com.worldtv.app.ui.language.applyAppLanguage(context, tag)
        }
        return@WorldTvTheme
    }
    CompositionLocalProvider(LocalAccountModel provides accountModel) {
    Box(Modifier.fillMaxSize().background(LocalWt.current.bg)) {
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
                onShowCountries = ::showCountries,
                onRadioToggle = container.radio::toggle,
                onRadioStop = container.radio::stop,
                television = television,
            )
        AccountHost(
            catalogModel = catalogModel,
            accountModel = accountModel,
            favorites = container.favorites,
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
    onShowCountries: () -> Unit,
    onRadioToggle: () -> Unit,
    onRadioStop: () -> Unit,
    television: Boolean,
) {
    val screen = navigator.current
    val showBar = !television && !fullscreen && screen != AppScreen.Player
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                WorldBottomBar(
                    explore = screen == AppScreen.Catalog || screen == AppScreen.Countries,
                    allChannels = screen == AppScreen.AllChannels,
                    favorites = screen == AppScreen.Favorites,
                    settings = screen == AppScreen.Settings || screen == AppScreen.Privacy,
                    onExplore = { navigator.openSection(AppScreen.Catalog) },
                    onAllChannels = { navigator.openSection(AppScreen.AllChannels) },
                    onFavorites = { navigator.openSection(AppScreen.Favorites) },
                    onSettings = { navigator.openSection(AppScreen.Settings) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().then(if (fullscreen) Modifier else Modifier.padding(padding))) {
            ScreenBody(
                screen = screen,
                television = television,
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
                onCountries = onShowCountries,
                onFavorites = { navigator.openSection(AppScreen.Favorites) },
                onAllChannels = { navigator.openSection(AppScreen.AllChannels) },
                onSettings = { navigator.openSection(AppScreen.Settings) },
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
                    modifier = if (nowPlaying.kind == ContentKind.RADIO) {
                        Modifier.align(Alignment.BottomCenter)
                    } else {
                        Modifier.fillMaxSize()
                    },
                )
            }
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
    onFavorites: () -> Unit,
    onAllChannels: () -> Unit,
    onSettings: () -> Unit,
    onPrivacy: () -> Unit,
    embedPlayback: Boolean,
    stage: @Composable () -> Unit,
) {
    val queue by container.queue.state.collectAsStateWithLifecycle()
    val playing = queue.current?.takeIf { embedPlayback }
    val scope = rememberCoroutineScope()
    when (screen) {
        AppScreen.Catalog -> HomePane(
            state = catalog,
            onKind = catalogModel::selectKind,
            onQuery = catalogModel::setCountryQuery,
            onRefresh = catalogModel::refresh,
            onOpenCountry = { code ->
                catalogModel.rememberCountry(code)
                catalogModel.setCountries(setOf(code))
                onCountries()
            },
            onOpenChannel = { entry ->
                catalogModel.rememberCountry(entry.countryCode)
                catalogModel.setCountries(setOf(entry.countryCode))
                onPlayInList(listOf(entry), entry)
                onCountries()
            },
            onToggleFavorite = catalogModel::toggleFavorite,
            onFavorites = if (television) onFavorites else null,
            onAllChannels = if (television) onAllChannels else null,
            onSettings = if (television) onSettings else null,
            onRandom = {
                scope.launch {
                    val pick = catalogModel.randomEntry() ?: return@launch
                    catalogModel.openCountry(pick.kind, pick.countryCode)
                    onPlayInList(listOf(pick), pick)
                    onCountries()
                }
            },
            onToggleCountry = { code -> catalogModel.toggleCountry(catalog.kind, code) },
            listIndex = catalogModel.countryListIndex,
            listOffset = catalogModel.countryListOffset,
            onSaveListPosition = { index, offset ->
                catalogModel.countryListIndex = index
                catalogModel.countryListOffset = offset
            },
            returnCountryCode = catalogModel.returnCountryCode,
            onReturnConsumed = catalogModel::consumeReturnCountry,
        )
        AppScreen.Countries -> CountryChannelsPane(
            state = catalog,
            onBack = onBack,
            onQuery = catalogModel::setChannelQuery,
            onRefresh = catalogModel::refresh,
            onOpen = onPlayInList,
            onToggleFavorite = catalogModel::toggleFavorite,
            playingKey = playing?.key,
            fullscreen = fullscreen && playing != null,
            player = stage,
            listIndex = catalogModel.channelScroll(catalog.selectedCodes.firstOrNull().orEmpty()).first,
            listOffset = catalogModel.channelScroll(catalog.selectedCodes.firstOrNull().orEmpty()).second,
            onSaveListPosition = catalogModel::saveChannelScroll,
        )
        AppScreen.AllChannels -> AllChannelsPane(
            state = catalog,
            onKind = catalogModel::selectKind,
            onQuery = catalogModel::setAllQuery,
            onRefresh = catalogModel::prepareCatalog,
            onRetry = catalogModel::reloadCatalog,
            onOpen = { entry ->
                catalogModel.openCountry(entry.kind, entry.countryCode)
                onPlayInList(listOf(entry), entry)
                onCountries()
            },
            onToggleFavorite = catalogModel::toggleFavorite,
            onRandom = {
                scope.launch {
                    val pick = catalogModel.randomEntry() ?: return@launch
                    catalogModel.openCountry(pick.kind, pick.countryCode)
                    onPlayInList(listOf(pick), pick)
                    onCountries()
                }
            },
        )
        AppScreen.Favorites -> FavoritesPane(
            state = catalog,
            onOpen = { entries, entry ->
                if (catalog.kind != entry.kind) catalogModel.selectKind(entry.kind)
                onOpen(entries, entry)
            },
            onToggleFavorite = catalogModel::toggleFavorite,
            onOpenCountry = { kind, code ->
                catalogModel.openCountry(kind, code)
                onCountries()
            },
            onToggleCountry = catalogModel::toggleCountry,
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

private fun landscapeSide(angle: Int): Int =
    if (angle >= 180) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
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
    val onSwipeDown: () -> Unit,
    val onSwipeUp: () -> Unit,
    val onClose: () -> Unit,
    val television: Boolean,
)
