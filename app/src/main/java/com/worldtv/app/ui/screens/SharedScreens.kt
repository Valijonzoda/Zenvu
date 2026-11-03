package com.worldtv.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.app.ui.account.AccountSettingsCard
import com.worldtv.app.ui.account.LocalAccountModel
import com.worldtv.app.BuildConfig
import com.worldtv.app.R
import com.worldtv.app.data.LocaleApplier
import com.worldtv.app.domain.AppLanguages
import com.worldtv.app.domain.CountrySelection
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.UserNotice
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.ThemeChoice
import com.worldtv.app.domain.UserSettings
import com.worldtv.app.ui.catalog.CatalogUiState
import com.worldtv.app.ui.language.LanguagePicker
import com.worldtv.app.ui.language.applyAppLanguage
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
import com.worldtv.app.ui.theme.LocalAppFont
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
        val filtered = if (query.isEmpty()) state.countries else state.countries.filter { CountrySelection.matches(it, query) }
        filtered.sortedBy { CountrySelection.displayName(it.code, it.name).lowercase() }
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
                    fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = palette.heading,
                    letterSpacing = (-0.03).em,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.home_subtitle), color = palette.muted, fontSize = 14.sp, fontFamily = LocalAppFont.current)
            Spacer(Modifier.height(18.dp))
            ModeSwitch(selected = state.kind, onSelect = onKind)
            Spacer(Modifier.height(14.dp))
            CountrySearchField(state.countryQuery, onQuery)
        }
        if (state.notice != null || state.offline) {
            item {
                StatusBanner(
                    state.notice?.let { noticeText(it, state.noticeArg) }
                        ?: stringResource(R.string.offline_banner),
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.section_countries), color = palette.muted, fontSize = 14.sp, fontFamily = LocalAppFont.current)
                Text(countries.size.toString(), color = palette.muted, fontSize = 14.sp, fontFamily = LocalAppFont.current)
            }
        }
        when {
            state.loadingCountries && state.countries.isEmpty() -> item { LoadingRows() }
            state.countriesError != null && state.countries.isEmpty() -> item {
                EmptyBlock(Icons.Outlined.Search, noticeText(state.countriesError, state.noticeArg))
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
            }
            countries.isEmpty() -> item {
                EmptyBlock(Icons.Outlined.Search, stringResource(R.string.empty_country_search))
            }
            else -> items(countries, key = { it.code }) { country ->
                CountryRow(
                    code = country.code,
                    name = CountrySelection.displayName(country.code, country.name),
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
                        Text(CountrySelection.displayName(country.code, country.name), color = palette.heading, fontFamily = LocalAppFont.current, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        val count = if (state.loadingChannels) country.channelCount else channels.size
                        Text(
                            "${kindCount(state.kind, count)} · ${country.capital}",
                            color = palette.muted,
                            fontSize = 14.sp,
                            fontFamily = LocalAppFont.current,
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
                        EmptyBlock(Icons.Outlined.Search, noticeText(state.channelsError, state.noticeArg))
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
    val account = LocalAccountModel.current
    val user by account.user.collectAsStateWithLifecycle()
    val signedIn = user != null
    val subtitle = when {
        !account.accountsEnabled -> R.string.accounts_disabled
        signedIn -> R.string.favorites_subtitle_account
        else -> R.string.auth_required_body
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.nav_favorites), fontFamily = LocalAppFont.current, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = palette.heading, letterSpacing = (-0.6).sp)
            Text(stringResource(subtitle), color = palette.muted, fontSize = 14.sp, fontFamily = FontFamily.Default)
            if (!signedIn && account.accountsEnabled) {
                TextButton(onClick = account::openSignIn) {
                    Text(stringResource(R.string.account_sign_in), fontFamily = FontFamily.Default, color = palette.accent)
                }
                TextButton(onClick = account::openRegister) {
                    Text(stringResource(R.string.account_sign_up), fontFamily = FontFamily.Default, color = palette.accent)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (signedIn && state.favorites.isEmpty()) {
            item { EmptyBlock(Icons.Outlined.FavoriteBorder, stringResource(R.string.empty_favorites_title) + "\n" + stringResource(R.string.empty_favorites_body)) }
        } else if (signedIn) {
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
    var choosingLanguage by remember { mutableStateOf(false) }
    if (choosingLanguage) {
        BackHandler { choosingLanguage = false }
        LanguagePicker { tag ->
            choosingLanguage = false
            applyAppLanguage(context, tag)
        }
        return
    }
    var cacheToken by remember { mutableIntStateOf(0) }
    val cacheLabel = remember(cacheToken) { cacheSizeLabel(context) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
    ) {
        Text(stringResource(R.string.nav_settings), fontFamily = LocalAppFont.current, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = palette.heading, letterSpacing = (-0.6).sp)
        SettingCard {
            SettingLine(showDivider = false, onClick = { choosingLanguage = true }) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_language), color = palette.heading, fontSize = 16.sp, fontFamily = LocalAppFont.current)
                    Text(
                        AppLanguages.find(LocaleApplier.read(context))?.nativeName ?: stringResource(R.string.language_choose),
                        color = palette.muted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Default,
                    )
                }
            }
        }
        AccountSettingsCard(LocalAccountModel.current)
        SettingCard {
            SettingLine {
                Text(stringResource(R.string.settings_theme), color = palette.heading, fontSize = 16.sp, fontFamily = LocalAppFont.current)
                ThemeMini(settings.theme, onTheme)
            }
            SettingLine(showDivider = true) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_fallback_title), color = palette.heading, fontSize = 16.sp, fontFamily = LocalAppFont.current)
                    Text(stringResource(R.string.settings_fallback), color = palette.muted, fontSize = 13.sp, fontFamily = LocalAppFont.current)
                }
                WtSwitch(settings.autoFallbackStreams, stringResource(R.string.settings_fallback_title), onFallback)
            }
            SettingLine(showDivider = false) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_background_title), color = palette.heading, fontSize = 16.sp, fontFamily = LocalAppFont.current)
                    Text(stringResource(R.string.settings_background), color = palette.muted, fontSize = 13.sp, fontFamily = LocalAppFont.current)
                }
                WtSwitch(settings.backgroundRadio, stringResource(R.string.settings_background_title), onBackgroundRadio)
            }
        }
        SettingCard {
            SettingLine(onClick = { confirmClear = true }) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_clear_cache), color = palette.heading, fontSize = 16.sp, fontFamily = LocalAppFont.current)
                    Text(stringResource(R.string.clear_cache_hint), color = palette.muted, fontSize = 13.sp, fontFamily = LocalAppFont.current)
                }
                Text(
                    cacheLabel,
                    color = palette.accent,
                    fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(palette.soft)
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }
            SettingLine(showDivider = true, onClick = onPrivacy) {
                Text(stringResource(R.string.privacy), color = palette.heading, fontSize = 16.sp, fontFamily = LocalAppFont.current)
            }
            SettingLine(showDivider = false) {
                Column {
                    Text(stringResource(R.string.app_name), color = palette.heading, fontSize = 16.sp, fontFamily = LocalAppFont.current)
                    Text(
                        stringResource(R.string.settings_version_body, BuildConfig.VERSION_NAME),
                        color = palette.muted,
                        fontSize = 13.sp,
                        fontFamily = LocalAppFont.current,
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
                fontFamily = LocalAppFont.current,
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
        Text(stringResource(R.string.privacy), fontFamily = LocalAppFont.current, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = palette.heading)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.privacy_body), color = palette.text, fontSize = 16.sp, fontFamily = LocalAppFont.current, lineHeight = 24.sp)
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
fun noticeText(notice: UserNotice?, arg: String?): String {
    if (notice == null) return ""
    return when (notice) {
        UserNotice.OFFLINE_UNSAVED -> stringResource(R.string.error_offline_unsaved)
        UserNotice.NO_SAVED_COPY -> stringResource(R.string.error_no_saved_copy)
        UserNotice.SAVED_UNREADABLE -> stringResource(R.string.error_saved_unreadable)
        UserNotice.DATASET_EMPTY -> stringResource(R.string.error_dataset_empty)
        UserNotice.NO_INTERNET -> stringResource(R.string.error_no_internet)
        UserNotice.TIMEOUT -> stringResource(R.string.error_timeout)
        UserNotice.DOWNLOAD_INTERRUPTED -> stringResource(R.string.error_download_interrupted)
        UserNotice.LISTING_MISSING -> stringResource(R.string.error_listing_missing)
        UserNotice.HOST_REFUSED -> stringResource(R.string.error_host_refused, arg.orEmpty())
        UserNotice.HOST_PROBLEM -> stringResource(R.string.error_host_problem, arg.orEmpty())
        UserNotice.REQUEST_FAILED -> stringResource(R.string.error_request_failed, arg.orEmpty())
        UserNotice.COUNTRY_LIST -> stringResource(R.string.error_country_list)
        UserNotice.CHANNEL_FORMAT -> stringResource(R.string.error_channel_format)
        UserNotice.CHANNEL_READ -> stringResource(R.string.error_channel_read)
        UserNotice.DATASET_LOAD -> stringResource(R.string.error_dataset_load)
        UserNotice.DATASET_READ -> stringResource(R.string.error_dataset_read)
        UserNotice.NO_DIRECT_AUDIO -> stringResource(R.string.error_no_direct_audio)
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
