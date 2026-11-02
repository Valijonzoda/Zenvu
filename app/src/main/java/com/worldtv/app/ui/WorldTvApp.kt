package com.worldtv.app.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? android.app.Activity
    val cacheCleared = stringResource(R.string.cache_cleared)

    fun closePlayer() {
        val entry = container.queue.state.value.current
        if (entry?.kind == ContentKind.RADIO && !settings.backgroundRadio) container.radio.stop()
        navigator.back()
    }

    fun openEntry(entries: List<MediaEntry>, entry: MediaEntry) {
        val index = entries.indexOfFirst { it.key == entry.key }.coerceAtLeast(0)
        container.queue.open(entries, index)
        if (entry.kind != ContentKind.RADIO) container.radio.pause()
        navigator.openPlayer()
    }

    BackHandler {
        if (navigator.current == AppScreen.Player) closePlayer()
        else if (!navigator.back()) activity?.finish()
    }

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
                onClosePlayer = ::closePlayer,
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
    onRadioToggle: () -> Unit,
    onRadioStop: () -> Unit,
) {
    val screen = navigator.current
    val showBar = screen != AppScreen.Player
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            ScreenBody(
                screen = screen,
                television = false,
                catalog = catalog,
                catalogModel = catalogModel,
                settings = settings,
                container = container,
                onTheme = onTheme,
                onFallback = onFallback,
                onBackground = onBackground,
                onClearCache = onClearCache,
                onOpen = onOpen,
                onClosePlayer = onClosePlayer,
                onBack = navigator::back,
                onCountries = navigator::openCountries,
                onPrivacy = navigator::openPrivacy,
            )
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
    onClosePlayer: () -> Unit,
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
                catalog = catalog,
                catalogModel = catalogModel,
                settings = settings,
                container = container,
                onTheme = onTheme,
                onFallback = onFallback,
                onBackground = onBackground,
                onClearCache = onClearCache,
                onOpen = onOpen,
                onClosePlayer = onClosePlayer,
                onBack = navigator::back,
                onCountries = navigator::openCountries,
                onPrivacy = navigator::openPrivacy,
            )
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(Space.lg))
        }
    }
}

@Composable
private fun ScreenBody(
    screen: AppScreen,
    television: Boolean,
    catalog: com.worldtv.app.ui.catalog.CatalogUiState,
    catalogModel: CatalogViewModel,
    settings: com.worldtv.app.domain.UserSettings,
    container: AppContainer,
    onTheme: (ThemeChoice) -> Unit,
    onFallback: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onOpen: (List<MediaEntry>, MediaEntry) -> Unit,
    onClosePlayer: () -> Unit,
    onBack: () -> Unit,
    onCountries: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val queue by container.queue.state.collectAsStateWithLifecycle()
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
            onOpen = onOpen,
            onToggleFavorite = catalogModel::toggleFavorite,
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
            onToggleFavorite = catalogModel::toggleFavorite,
            onBack = onClosePlayer,
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
