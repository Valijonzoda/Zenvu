package com.worldtv.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldtv.app.R
import com.worldtv.app.domain.ThemeChoice

private val Ink = Color(0xFF0B0E14)
private val InkRaised = Color(0xFF151B24)
private val InkHigh = Color(0xFF1E2633)
private val Signal = Color(0xFF3DDCFF)
private val SignalDeep = Color(0xFF08343C)
private val Amber = Color(0xFFFFB45E)
private val Favorite = Color(0xFFFF5D73)
private val Mist = Color(0xFFE8EEF7)
private val MistDim = Color(0xFF93A0B5)
private val Line = Color(0xFF2C3648)
private val Paper = Color(0xFFF4F7FB)
private val PaperCard = Color(0xFFFFFFFF)
private val InkText = Color(0xFF14202E)

val FavoriteColor = Favorite

private val DarkColors = darkColorScheme(
    primary = Signal,
    onPrimary = Color(0xFF042126),
    primaryContainer = SignalDeep,
    onPrimaryContainer = Color(0xFFB6F4FF),
    secondary = Amber,
    onSecondary = Color(0xFF2C1C04),
    secondaryContainer = Color(0xFF3A2A12),
    onSecondaryContainer = Color(0xFFFFE2BF),
    tertiary = Color(0xFF8B9CFF),
    background = Ink,
    onBackground = Mist,
    surface = InkRaised,
    onSurface = Mist,
    surfaceVariant = InkHigh,
    onSurfaceVariant = MistDim,
    outline = Line,
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF2A060C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF007C8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F6FF),
    onPrimaryContainer = Color(0xFF003840),
    secondary = Color(0xFF8A5A12),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3C2),
    onSecondaryContainer = Color(0xFF2C1C04),
    tertiary = Color(0xFF3D4CB5),
    background = Paper,
    onBackground = InkText,
    surface = PaperCard,
    onSurface = InkText,
    surfaceVariant = Color(0xFFE7EDF5),
    onSurfaceVariant = Color(0xFF516070),
    outline = Color(0xFFD3DCE8),
    error = Color(0xFFBA1A3A),
    onError = Color.White,
)

private fun outfit(weight: FontWeight) = Font(
    resId = R.font.outfit,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Outfit = FontFamily(
    outfit(FontWeight.Normal),
    outfit(FontWeight.Medium),
    outfit(FontWeight.SemiBold),
    outfit(FontWeight.Bold),
)

private fun type(television: Boolean): Typography {
    val scale = if (television) 1.18f else 1f
    fun size(value: Float) = (value * scale).sp
    return Typography(
        displaySmall = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = size(36f), lineHeight = size(42f)),
        headlineMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = size(28f), lineHeight = size(34f)),
        headlineSmall = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = size(22f), lineHeight = size(28f)),
        titleLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = size(20f), lineHeight = size(26f)),
        titleMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = size(16f), lineHeight = size(22f)),
        bodyLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = size(16f), lineHeight = size(24f)),
        bodyMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = size(14f), lineHeight = size(20f)),
        labelLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = size(14f), lineHeight = size(18f)),
        labelMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = size(12f), lineHeight = size(16f)),
    )
}

object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

@Composable
fun WorldTvTheme(
    choice: ThemeChoice,
    television: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (choice) {
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = type(television),
        content = content,
    )
}
