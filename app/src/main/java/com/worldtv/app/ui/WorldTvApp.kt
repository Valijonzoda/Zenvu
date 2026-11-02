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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.Brush
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
import com.worldtv.app.ui.player.PlayerScreen
import com.worldtv.app.ui.screens.CatalogPane
import com.worldtv.app.ui.screens.CountryPane
import com.worldtv.app.ui.screens.FavoritesPane
import com.worldtv.app.ui.screens.PrivacyPane
import com.worldtv.app.ui.screens.SettingsPane
import com.worldtv.app.ui.theme.Space
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), Color.Transparent),
                    ),
                ),
        )
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
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == AppScreen.Catalog || screen == AppScreen.Countries,
                        onClick = { navigator.openSection(AppScreen.Catalog) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_discover)) },
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.Favorites,
                        onClick = { navigator.openSection(AppScreen.Favorites) },
                        icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_favorites)) },
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.Settings || screen == AppScreen.Privacy,
                        onClick = { navigator.openSection(AppScreen.Settings) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                    )
                }
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
            if (radioEntry != null && screen != AppScreen.Player) {
                MiniPlayer(
                    entry = radioEntry,
                    playing = radioPlaying,
                    onOpen = { onOpen(listOf(radioEntry), radioEntry) },
                    onToggle = onRadioToggle,
                    onStop = onRadioStop,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(Space.md),
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
        AppScreen.Catalog -> CatalogPane(
            state = catalog,
            television = television,
            onKind = catalogModel::selectKind,
            onQuery = catalogModel::setChannelQuery,
            onLanguage = catalogModel::setLanguage,
            onFavoritesOnly = catalogModel::setFavoritesOnly,
            onRefresh = catalogModel::refresh,
            onOpenCountries = onCountries,
            onOpen = onOpen,
            onToggleFavorite = catalogModel::toggleFavorite,
        )
        AppScreen.Countries -> CountryPane(
            state = catalog,
            television = television,
            onQuery = catalogModel::setCountryQuery,
            onApply = { codes ->
                catalogModel.setCountries(codes)
                onBack()
            },
        )
        AppScreen.Favorites -> FavoritesPane(
            state = catalog,
            television = television,
            onOpen = onOpen,
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

@Composable
private fun MiniPlayer(
    entry: MediaEntry,
    playing: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(stringResource(R.string.mode_radio), style = MaterialTheme.typography.labelMedium)
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
            )
        }
        IconButton(onClick = onStop) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.stop))
        }
    }
}
