package com.worldtv.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldtv.app.R
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.CountryFlags
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.ui.theme.LocalWt
import com.worldtv.app.ui.theme.LocalAppFont
import kotlin.math.abs

fun ContentKind.labelRes(): Int = when (this) {
    ContentKind.TV -> R.string.mode_tv
    ContentKind.RADIO -> R.string.mode_radio
    ContentKind.WEBCAM -> R.string.mode_webcam
}

private val ArtGradients = listOf(
    Color(0xFFFF512F) to Color(0xFFF09819),
    Color(0xFF11998E) to Color(0xFF38EF7D),
    Color(0xFFFC466B) to Color(0xFF3F5EFD),
    Color(0xFF00C6FF) to Color(0xFF0072FF),
    Color(0xFFF953C6) to Color(0xFFB91D73),
    Color(0xFFFDC830) to Color(0xFFF37335),
    Color(0xFF7F00FF) to Color(0xFFE100FF),
    Color(0xFF00B09B) to Color(0xFF96C93D),
)

private val ArtIcons = listOf(
    Icons.Outlined.Public,
    Icons.Outlined.SportsSoccer,
    Icons.Outlined.Face,
    Icons.Outlined.MusicNote,
    Icons.Outlined.Movie,
    Icons.Outlined.AccountBalance,
    Icons.Outlined.WbSunny,
    Icons.Outlined.Videocam,
)

fun artIndex(key: String): Int = abs(key.hashCode()) % ArtGradients.size

fun artBrush(index: Int): Brush {
    val pair = ArtGradients[index % ArtGradients.size]
    return Brush.linearGradient(listOf(pair.first, pair.second))
}

fun artEnd(index: Int): Color = ArtGradients[index % ArtGradients.size].second

fun artIcon(index: Int): ImageVector = ArtIcons[index % ArtIcons.size]

fun ContentKind.glyph(): ImageVector = when (this) {
    ContentKind.TV -> Icons.Outlined.Tv
    ContentKind.RADIO -> Icons.Outlined.Radio
    ContentKind.WEBCAM -> Icons.Outlined.Videocam
}

fun Modifier.wtShadow(shape: RoundedCornerShape, elevation: Dp = 8.dp, dark: Boolean): Modifier {
    val color = if (dark) Color(0x40000000) else Color(0x1A465AA0)
    return shadow(elevation, shape, clip = false, ambientColor = color, spotColor = color)
}

@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(150), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun ModeSwitch(
    selected: ContentKind,
    onSelect: (ContentKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWt.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wtShadow(shape, dark = palette.dark)
            .background(palette.surface, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ContentKind.entries.forEach { kind ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val active = kind == selected
            val itemShape = RoundedCornerShape(17.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(itemShape)
                    .then(
                        if (active) {
                            Modifier.shadow(
                                8.dp,
                                itemShape,
                                ambientColor = palette.accent.copy(alpha = 0.45f),
                                spotColor = palette.accent.copy(alpha = 0.55f),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .background(if (active) Brush.linearGradient(listOf(palette.accentFrom, palette.accent)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .border(if (focused) 3.dp else 0.dp, palette.accent, itemShape)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(kind) }
                    .padding(top = 12.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    kind.glyph(),
                    contentDescription = null,
                    tint = if (active) Color.White else palette.muted,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    stringResource(kind.labelRes()),
                    color = if (active) Color.White else palette.muted,
                    fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
fun CountrySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWt.current
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .wtShadow(shape, dark = palette.dark)
            .then(if (focused) Modifier.border(2.dp, palette.accent, shape) else Modifier)
            .height(52.dp)
            .clip(shape)
            .background(palette.surface),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.heading),
        singleLine = true,
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.muted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(stringResource(R.string.search_country), color = palette.muted, style = MaterialTheme.typography.bodyLarge)
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
fun CountryRow(
    code: String,
    name: String,
    capital: String,
    countLabel: String,
    onClick: () -> Unit,
) {
    val palette = LocalWt.current
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .wtShadow(shape, elevation = 2.dp, dark = palette.dark)
            .clip(shape)
            .background(palette.surface)
            .border(if (focused) 3.dp else 0.dp, palette.accent, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlagBadge(code, width = 54.dp, height = 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = palette.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(capital, color = palette.muted, fontSize = 13.sp, fontFamily = LocalAppFont.current, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            countLabel,
            color = palette.accent,
            fontFamily = LocalAppFont.current,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(CircleShape)
                .background(palette.soft)
                .padding(horizontal = 11.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun FlagBadge(code: String, width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .size(width, height)
            .shadow(6.dp, RoundedCornerShape(9.dp), clip = false, ambientColor = Color(0x4D3C50A0), spotColor = Color(0x4D3C50A0))
            .clip(RoundedCornerShape(9.dp))
            .background(LocalWt.current.surface2)
            .border(1.dp, Color(0x1F3C50A0), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(CountryFlags.emoji(code), fontSize = 22.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ChannelCard(
    entry: MediaEntry,
    favorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    favoriteTags: Boolean = false,
    playing: Boolean = false,
) {
    val palette = LocalWt.current
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val cardColor = when {
        !playing -> palette.surface
        palette.dark -> lerp(palette.surface, palette.accent, 0.32f)
        else -> palette.soft
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(shape)
            .background(cardColor)
            .border(if (focused || playing) 2.dp else 0.dp, if (playing || focused) palette.accent else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = palette.heading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (playing) MetaTag(stringResource(R.string.now_playing), TagKind.Live)
                else if (entry.kind == ContentKind.TV && !favoriteTags) MetaTag(stringResource(R.string.tag_live), TagKind.Live)
                if (favoriteTags) {
                    MetaTag(stringResource(entry.kind.labelRes()), TagKind.Plain)
                    MetaTag(entry.countryCode.uppercase(), TagKind.Plain)
                } else {
                    entry.languages.firstOrNull()?.let { MetaTag(it.lowercase(), TagKind.Plain) }
                }
            }
        }
        HeartButton(favorite, onToggleFavorite)
    }
}

enum class TagKind { Plain, Region, Live }

@Composable
fun MetaTag(label: String, kind: TagKind) {
    val palette = LocalWt.current
    val bg = when (kind) {
        TagKind.Plain -> palette.surface2
        TagKind.Region -> palette.warnBg
        TagKind.Live -> palette.liveSoft
    }
    val fg = when (kind) {
        TagKind.Plain -> palette.text
        TagKind.Region -> palette.regionText
        TagKind.Live -> palette.live
    }
    Text(
        label,
        color = fg,
        fontFamily = LocalAppFont.current,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
fun ChannelArt(index: Int, modifier: Modifier = Modifier, iconSize: Dp = 38.dp) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = modifier
            .shadow(12.dp, shape, clip = false, ambientColor = artEnd(index).copy(alpha = 0.45f), spotColor = artEnd(index))
            .clip(shape)
            .background(artBrush(index)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 18.dp, y = 22.dp)
                .size(70.dp)
                .background(Color.White.copy(alpha = 0.22f), CircleShape),
        )
        Icon(artIcon(index), contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun HeartButton(favorite: Boolean, onToggle: () -> Unit) {
    val palette = LocalWt.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
            tint = if (favorite) palette.fav else palette.muted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun IconTile(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val palette = LocalWt.current
    val shape = RoundedCornerShape(15.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(44.dp)
            .wtShadow(shape, dark = palette.dark)
            .clip(shape)
            .background(palette.surface)
            .border(if (focused) 3.dp else 0.dp, palette.accent, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint ?: palette.heading, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun EmptyBlock(icon: ImageVector, message: String) {
    val palette = LocalWt.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = palette.muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(message, color = palette.muted, fontSize = 14.sp, fontFamily = LocalAppFont.current, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatusBanner(text: String, modifier: Modifier = Modifier) {
    val palette = LocalWt.current
    Text(
        text,
        color = palette.warnText,
        fontSize = 14.sp,
        fontFamily = LocalAppFont.current,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.warnBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
fun WtSwitch(checked: Boolean, label: String, onChecked: (Boolean) -> Unit) {
    val palette = LocalWt.current
    val knob by animateDpAsState(if (checked) 23.dp else 3.dp, tween(200), label = "knob")
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 30.dp)
            .border(if (focused) 3.dp else 0.dp, palette.accent, CircleShape)
            .clip(CircleShape)
            .background(if (checked) palette.accent else palette.line)
            .clickable(interactionSource = interaction, indication = null) { onChecked(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 3.dp)
                .size(24.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
fun WorldBottomBar(
    explore: Boolean,
    favorites: Boolean,
    settings: Boolean,
    onExplore: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
) {
    val palette = LocalWt.current
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = if (palette.dark) Color(0x66000000) else Color(0x14465AA0), spotColor = if (palette.dark) Color(0x66000000) else Color(0x14465AA0))
            .background(palette.surface, shape)
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
    ) {
        NavItem(stringResource(R.string.nav_discover), Icons.Outlined.Explore, explore, onExplore, Modifier.weight(1f))
        NavItem(stringResource(R.string.nav_favorites), Icons.Outlined.FavoriteBorder, favorites, onFavorites, Modifier.weight(1f))
        NavItem(stringResource(R.string.nav_settings), Icons.Outlined.Tune, settings, onSettings, Modifier.weight(1f))
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWt.current
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) palette.soft else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) palette.accent else palette.muted, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            color = if (selected) palette.accent else palette.muted,
            fontFamily = LocalAppFont.current,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
    }
}
