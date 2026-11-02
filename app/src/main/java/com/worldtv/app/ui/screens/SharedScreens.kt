package com.worldtv.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.worldtv.app.BuildConfig
import com.worldtv.app.R
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.LanguageNames
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.ThemeChoice
import com.worldtv.app.domain.UserSettings
import com.worldtv.app.ui.catalog.CatalogUiState
import com.worldtv.app.ui.components.ChannelCard
import com.worldtv.app.ui.components.ChannelSkeletonGrid
import com.worldtv.app.ui.components.CountryRow
import com.worldtv.app.ui.components.EmptyState
import com.worldtv.app.ui.components.ErrorState
import com.worldtv.app.ui.components.FilterChipButton
import com.worldtv.app.ui.components.ModeSwitch
import com.worldtv.app.ui.components.StatusBanner
import com.worldtv.app.ui.components.WtSearchField
import com.worldtv.app.ui.components.flagFor
import com.worldtv.app.ui.theme.Space

@Composable
fun CatalogPane(
    state: CatalogUiState,
    television: Boolean,
    onKind: (ContentKind) -> Unit,
    onQuery: (String) -> Unit,
    onLanguage: (String?) -> Unit,
    onFavoritesOnly: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenCountries: () -> Unit,
    onOpen: (List<MediaEntry>, MediaEntry) -> Unit,
    onToggleFavorite: (MediaEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var languageDialog by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = if (television) Space.xl else Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }
        ModeSwitch(selected = state.kind, television = television, onSelect = onKind)
        Spacer(Modifier.height(Space.md))
        WtSearchField(
            value = state.channelQuery,
            onValueChange = onQuery,
            placeholder = stringResource(R.string.search_channels),
        )
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            val selectedCountries = state.countries.filter { it.code in state.selectedCodes }
            val countryLabel = when {
                selectedCountries.isEmpty() -> stringResource(R.string.select_country)
                selectedCountries.size == 1 -> "${flagFor(selectedCountries.first().code)} ${selectedCountries.first().name}"
                else -> stringResource(R.string.countries_selected, selectedCountries.size)
            }
            FilterChipButton(countryLabel, selected = true, onClick = onOpenCountries)
            val languageLabel = state.language?.let { LanguageNames.label(it) } ?: stringResource(R.string.language_all)
            FilterChipButton(languageLabel, selected = state.language != null, onClick = { languageDialog = true })
            FilterChipButton(
                label = stringResource(R.string.favorites_only),
                selected = state.favoritesOnly,
                onClick = { onFavoritesOnly(!state.favoritesOnly) },
            )
        }
        Spacer(Modifier.height(Space.md))
        val banner = state.notice ?: when {
            state.offline && (state.fromCache || state.channels.isNotEmpty()) -> stringResource(R.string.offline_banner)
            state.fromCache -> stringResource(R.string.cached_banner)
            else -> null
        }
        if (banner != null) {
            StatusBanner(banner, offline = state.offline)
            Spacer(Modifier.height(Space.md))
        }
        when {
            state.loadingCountries || state.loadingChannels -> ChannelSkeletonGrid()
            state.countriesError != null && state.countries.isEmpty() -> ErrorState(state.countriesError, onRefresh)
            state.channelsError != null && state.channels.isEmpty() -> ErrorState(state.channelsError, onRefresh)
            state.selectedCodes.isEmpty() -> EmptyState(
                title = stringResource(R.string.select_country),
                body = stringResource(R.string.empty_country),
                actionLabel = stringResource(R.string.nav_countries),
                onAction = onOpenCountries,
            )
            state.visibleChannels.isEmpty() -> EmptyState(
                title = stringResource(R.string.empty_channels_title),
                body = if (state.channelQuery.isNotBlank() || state.favoritesOnly || state.language != null) {
                    stringResource(R.string.empty_search)
                } else {
                    stringResource(R.string.empty_country)
                },
            )
            else -> {
                Text(
                    pluralStringResource(R.plurals.channel_count, state.visibleChannels.size, state.visibleChannels.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.sm))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val minCard = if (television) 320.dp else 160.dp
                    val columns = (maxWidth / minCard).toInt().coerceIn(if (television) 2 else 2, if (television) 4 else 6)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(bottom = if (television) Space.xxxl else 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        items(state.visibleChannels, key = { it.key }) { entry ->
                            ChannelCard(
                                entry = entry,
                                countryName = state.countryName(entry.countryCode),
                                favorite = entry.key in state.favoriteKeys,
                                television = television,
                                onOpen = { onOpen(state.visibleChannels, entry) },
                                onToggleFavorite = { onToggleFavorite(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(stringResource(R.string.language_filter)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { onLanguage(null); languageDialog = false }) {
                        Text(stringResource(R.string.language_all))
                    }
                    state.languages.forEach { code ->
                        TextButton(onClick = { onLanguage(code); languageDialog = false }) {
                            Text(LanguageNames.label(code))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
fun CountryPane(
    state: CatalogUiState,
    television: Boolean,
    onQuery: (String) -> Unit,
    onApply: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var multi by remember(state.kind) { mutableStateOf(state.selectedCodes.size > 1) }
    var draft by remember(state.kind, state.selectedCodes) { mutableStateOf(state.selectedCodes) }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = if (television) Space.xl else Space.lg)) {
        Text(stringResource(R.string.nav_countries), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.sm))
        Text(stringResource(R.string.country_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.md))
        WtSearchField(state.countryQuery, onQuery, stringResource(R.string.search_countries))
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.multiple_countries), modifier = Modifier.weight(1f))
            Switch(checked = multi, onCheckedChange = { multi = it })
        }
        Spacer(Modifier.height(Space.sm))
        if (multi) {
            TextButton(onClick = { onApply(draft) }) {
                Text(stringResource(R.string.apply))
            }
            Spacer(Modifier.height(Space.sm))
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            contentPadding = PaddingValues(bottom = Space.xxxl),
        ) {
            items(state.visibleCountries, key = { it.code }) { country ->
                val detail = if (country.channelCount == 0) {
                    stringResource(R.string.no_channels)
                } else {
                    pluralStringResource(R.plurals.channel_count, country.channelCount, country.channelCount)
                }
                CountryRow(
                    flag = flagFor(country.code),
                    name = country.name,
                    detail = detail,
                    selected = country.code in draft,
                    television = television,
                    onClick = {
                        if (!multi) {
                            onApply(setOf(country.code))
                        } else {
                            draft = if (country.code in draft) {
                                if (draft.size == 1) draft else draft - country.code
                            } else {
                                draft + country.code
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun FavoritesPane(
    state: CatalogUiState,
    television: Boolean,
    onOpen: (List<MediaEntry>, MediaEntry) -> Unit,
    onToggleFavorite: (MediaEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = ContentKind.entries.map { kind -> kind to state.favorites.filter { it.kind == kind } }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = if (television) Space.xl else Space.lg)) {
        Text(stringResource(R.string.nav_favorites), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.md))
        if (state.favorites.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_favorites_title),
                body = stringResource(R.string.empty_favorites_body),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.md),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                grouped.forEach { (kind, entries) ->
                    if (entries.isEmpty()) return@forEach
                    item(key = "header-${kind.name}") {
                        Text(kind.title, style = MaterialTheme.typography.titleLarge)
                    }
                    items(entries, key = { it.key }) { entry ->
                        ChannelCard(
                            entry = entry,
                            countryName = state.countryName(entry.countryCode),
                            favorite = true,
                            television = television,
                            onOpen = { onOpen(entries, entry) },
                            onToggleFavorite = { onToggleFavorite(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPane(
    settings: UserSettings,
    onTheme: (ThemeChoice) -> Unit,
    onFallback: (Boolean) -> Unit,
    onBackgroundRadio: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl, vertical = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleLarge)
        ThemeChoice.entries.forEach { choice ->
            val label = when (choice) {
                ThemeChoice.DARK -> stringResource(R.string.theme_dark)
                ThemeChoice.LIGHT -> stringResource(R.string.theme_light)
                ThemeChoice.SYSTEM -> stringResource(R.string.theme_system)
            }
            TextButton(onClick = { onTheme(choice) }) {
                Text(if (settings.theme == choice) "● $label" else label)
            }
        }
        Text(stringResource(R.string.settings_playback), style = MaterialTheme.typography.titleLarge)
        SettingSwitch(stringResource(R.string.settings_fallback), settings.autoFallbackStreams, onFallback)
        SettingSwitch(stringResource(R.string.settings_background), settings.backgroundRadio, onBackgroundRadio)
        Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.settings_clear_cache)) }
        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.dataset_credit), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onPrivacy) { Text(stringResource(R.string.privacy)) }
        Text(
            stringResource(R.string.version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_cache)) },
            text = { Text(stringResource(R.string.settings_clear_cache_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearCache() }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun PrivacyPane(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(stringResource(R.string.privacy), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyLarge)
    }
}
