package com.worldtv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.worldtv.app.BuildConfig
import com.worldtv.app.R
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.ThemeChoice
import com.worldtv.app.domain.UserSettings
import com.worldtv.app.ui.catalog.CatalogUiState
import com.worldtv.app.ui.components.ChannelCard
import com.worldtv.app.ui.components.CountryRow
import com.worldtv.app.ui.components.CountrySearchField
import com.worldtv.app.ui.components.EmptyBlock
import com.worldtv.app.ui.components.FlagBadge
import com.worldtv.app.ui.components.IconTile
import com.worldtv.app.ui.components.ModeSwitch
import com.worldtv.app.ui.components.StatusBanner
import com.worldtv.app.ui.components.WtSwitch
import com.worldtv.app.ui.components.wtShadow
import com.worldtv.app.ui.theme.LocalWt
import com.worldtv.app.ui.theme.Outfit
import kotlin.math.roundToInt

@Composable
fun HomePane(
    state: CatalogUiState,
    onKind: (ContentKind) -> Unit,
    onQuery: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenCountry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWt.current
    val query = state.countryQuery.trim()
    val countries = remember(state.countries, query) {
        if (query.isEmpty()) state.countries
        else state.countries.filter { it.name.contains(query, ignoreCase = true) }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.ic_zenvu_logo),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.padding(start = 16.dp))
                Text(
                    stringResource(R.string.app_name),
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = palette.heading,
                    letterSpacing = (-0.03).em,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.home_subtitle), color = palette.muted, fontSize = 14.sp, fontFamily = Outfit)
            Spacer(Modifier.height(18.dp))
            ModeSwitch(selected = state.kind, onSelect = onKind)
            Spacer(Modifier.height(14.dp))
            CountrySearchField(state.countryQuery, onQuery)
        }
        if (state.notice != null || state.offline) {
            item {
                StatusBanner(
                    state.notice ?: stringResource(R.string.offline_banner),
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.section_countries), color = palette.muted, fontSize = 14.sp, fontFamily = Outfit)
                Text(countries.size.toString(), color = palette.muted, fontSize = 14.sp, fontFamily = Outfit)
            }
        }
        when {
            state.loadingCountries && state.countries.isEmpty() -> item { LoadingRows() }
            state.countriesError != null && state.countries.isEmpty() -> item {
                EmptyBlock(Icons.Outlined.Search, state.countriesError)
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
            }
            countries.isEmpty() -> item {
                EmptyBlock(Icons.Outlined.Search, stringResource(R.string.empty_country_search))
            }
            else -> items(countries, key = { it.code }) { country ->
                CountryRow(
                    code = country.code,
                    name = country.name,
                    capital = country.capital,
                    countLabel = kindCount(state.kind, country.channelCount),
                    onClick = { onOpenCountry(country.code) },
                )
            }
        }
    }
}

@Composable
fun CountryChannelsPane(
    state: CatalogUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (List<MediaEntry>, MediaEntry) -> Unit,
    onToggleFavorite: (MediaEntry) -> Unit,
    playingKey: String?,
    fullscreen: Boolean,
    player: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWt.current
    val country = state.countries.firstOrNull { it.code in state.selectedCodes }
    val channels = state.channels
    Column(modifier.fillMaxSize().background(palette.bg)) {
        if (playingKey != null && fullscreen) {
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) { player() }
        } else {
            if (playingKey != null) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clipToBounds()) { player() }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = if (playingKey == null) 20.dp else 12.dp, bottom = 8.dp),
            ) {
                IconTile(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
                Spacer(Modifier.padding(start = 12.dp))
                if (country != null) {
                    FlagBadge(country.code, width = 48.dp, height = 34.dp)
                    Spacer(Modifier.padding(start = 12.dp))
                    Column {
                        Text(country.name, color = palette.heading, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        val count = if (state.loadingChannels) country.channelCount else channels.size
                        Text(
                            "${kindCount(state.kind, count)} · ${country.capital}",
                            color = palette.muted,
                            fontSize = 14.sp,
                            fontFamily = Outfit,
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.loadingChannels && channels.isEmpty() -> item { LoadingRows(card = true) }
                    state.channelsError != null && channels.isEmpty() -> item {
                        EmptyBlock(Icons.Outlined.Search, state.channelsError)
                        TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                    }
                    channels.isEmpty() -> item {
                        EmptyBlock(Icons.Outlined.Search, stringResource(R.string.empty_country))
                    }
                    else -> items(channels, key = { it.key }, contentType = { "channel" }) { entry ->
                        ChannelCard(
                            entry = entry,
                            favorite = entry.key in state.favoriteKeys,
                            playing = entry.key == playingKey,
                            onOpen = { onOpen(channels, entry) },
                            onToggleFavorite = { onToggleFavorite(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesPane(
    state: CatalogUiState,
    onOpen: (List<MediaEntry>, MediaEntry) -> Unit,
    onToggleFavorite: (MediaEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWt.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.nav_favorites), fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = palette.heading, letterSpacing = (-0.6).sp)
            Text(stringResource(R.string.favorites_subtitle), color = palette.muted, fontSize = 14.sp, fontFamily = Outfit)
            Spacer(Modifier.height(8.dp))
        }
        if (state.favorites.isEmpty()) {
            item { EmptyBlock(Icons.Outlined.FavoriteBorder, stringResource(R.string.empty_favorites_title) + "\n" + stringResource(R.string.empty_favorites_body)) }
        } else {
            items(state.favorites, key = { it.key }, contentType = { "favorite" }) { entry ->
                ChannelCard(
                    entry = entry,
                    favorite = true,
                    favoriteTags = true,
                    onOpen = { onOpen(state.favorites.filter { it.kind == entry.kind }, entry) },
                    onToggleFavorite = { onToggleFavorite(entry) },
                )
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
    val palette = LocalWt.current
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var cacheToken by remember { mutableIntStateOf(0) }
    val cacheLabel = remember(cacheToken) { cacheSizeLabel(context) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
    ) {
        Text(stringResource(R.string.nav_settings), fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = palette.heading, letterSpacing = (-0.6).sp)
        SettingCard {
            SettingLine {
                Text(stringResource(R.string.settings_theme), color = palette.heading, fontSize = 16.sp, fontFamily = Outfit)
                ThemeMini(settings.theme, onTheme)
            }
            SettingLine(showDivider = true) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_fallback_title), color = palette.heading, fontSize = 16.sp, fontFamily = Outfit)
                    Text(stringResource(R.string.settings_fallback), color = palette.muted, fontSize = 13.sp, fontFamily = Outfit)
                }
                WtSwitch(settings.autoFallbackStreams, stringResource(R.string.settings_fallback_title), onFallback)
            }
            SettingLine(showDivider = false) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_background_title), color = palette.heading, fontSize = 16.sp, fontFamily = Outfit)
                    Text(stringResource(R.string.settings_background), color = palette.muted, fontSize = 13.sp, fontFamily = Outfit)
                }
                WtSwitch(settings.backgroundRadio, stringResource(R.string.settings_background_title), onBackgroundRadio)
            }
        }
        SettingCard {
            SettingLine(onClick = { confirmClear = true }) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_clear_cache), color = palette.heading, fontSize = 16.sp, fontFamily = Outfit)
                    Text(stringResource(R.string.clear_cache_hint), color = palette.muted, fontSize = 13.sp, fontFamily = Outfit)
                }
                Text(
                    cacheLabel,
                    color = palette.accent,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(palette.soft)
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }
            SettingLine(showDivider = true, onClick = onPrivacy) {
                Text(stringResource(R.string.privacy), color = palette.heading, fontSize = 16.sp, fontFamily = Outfit)
            }
            SettingLine(showDivider = false) {
                Column {
                    Text(stringResource(R.string.app_name), color = palette.heading, fontSize = 16.sp, fontFamily = Outfit)
                    Text(
                        stringResource(R.string.settings_version_body, BuildConfig.VERSION_NAME),
                        color = palette.muted,
                        fontSize = 13.sp,
                        fontFamily = Outfit,
                    )
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_cache)) },
            text = { Text(stringResource(R.string.settings_clear_cache_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearCache()
                    cacheToken += 1
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    val palette = LocalWt.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .wtShadow(shape, dark = palette.dark)
            .clip(shape)
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingLine(
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = LocalWt.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            content()
        }
        if (showDivider) {
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
        }
    }
}

@Composable
private fun ThemeMini(selected: ThemeChoice, onTheme: (ThemeChoice) -> Unit) {
    val palette = LocalWt.current
    val options = listOf(
        ThemeChoice.SYSTEM to stringResource(R.string.theme_system),
        ThemeChoice.LIGHT to stringResource(R.string.theme_light),
        ThemeChoice.DARK to stringResource(R.string.theme_dark),
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface2)
            .padding(3.dp),
    ) {
        options.forEach { (choice, label) ->
            val on = choice == selected
            Text(
                label,
                color = if (on) palette.accent else palette.muted,
                fontFamily = Outfit,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) palette.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onTheme(choice) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun PrivacyPane(modifier: Modifier = Modifier) {
    val palette = LocalWt.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
    ) {
        Text(stringResource(R.string.privacy), fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = palette.heading)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.privacy_body), color = palette.text, fontSize = 16.sp, fontFamily = Outfit, lineHeight = 24.sp)
    }
}

@Composable
private fun LoadingRows(card: Boolean = false) {
    val palette = LocalWt.current
    Column(verticalArrangement = Arrangement.spacedBy(if (card) 12.dp else 10.dp)) {
        repeat(6) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(if (card) 96.dp else 66.dp)
                    .clip(RoundedCornerShape(if (card) 22.dp else 20.dp))
                    .background(palette.surface2),
            )
        }
    }
}

@Composable
fun kindCount(kind: ContentKind, count: Int): String = when (kind) {
    ContentKind.TV -> pluralStringResource(R.plurals.channel_count, count, count)
    ContentKind.RADIO -> pluralStringResource(R.plurals.station_count, count, count)
    ContentKind.WEBCAM -> pluralStringResource(R.plurals.camera_count, count, count)
}

private fun cacheSizeLabel(context: android.content.Context): String {
    val dir = context.filesDir.resolve("catalog-cache")
    val bytes = if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1.0) "<1 MB" else "${mb.roundToInt()} MB"
}
