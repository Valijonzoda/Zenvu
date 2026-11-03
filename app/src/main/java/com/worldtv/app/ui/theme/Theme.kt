package com.worldtv.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.worldtv.app.R
import com.worldtv.app.data.LocaleApplier
import com.worldtv.app.domain.AppLanguages
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.ThemeChoice

@Immutable
data class WtPalette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val heading: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val soft: Color,
    val accentFrom: Color,
    val fav: Color,
    val live: Color,
    val liveSoft: Color,
    val warnBg: Color,
    val warnText: Color,
    val regionText: Color,
)

val LocalWt = staticCompositionLocalOf {
    palette(dark = false, kind = ContentKind.TV)
}

private val LightBg = Color(0xFFEEF2FB)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurface2 = Color(0xFFF5F7FD)
private val LightHeading = Color(0xFF3A4673)
private val LightText = Color(0xFF6C779B)
private val LightMuted = Color(0xFF9AA4C4)
private val LightLine = Color(0xFFE4E9F6)

private val DarkBg = Color(0xFF1B2033)
private val DarkSurface = Color(0xFF242B45)
private val DarkSurface2 = Color(0xFF2C3453)
private val DarkHeading = Color(0xFFE6EAFB)
private val DarkText = Color(0xFFB3BCDD)
private val DarkMuted = Color(0xFF8791B8)
private val DarkLine = Color(0xFF343D5F)

fun ContentKind.accentFrom(): Color = when (this) {
    ContentKind.TV -> Color(0xFF6D94FF)
    ContentKind.RADIO -> Color(0xFFFF8FA9)
    ContentKind.WEBCAM -> Color(0xFF4FD6C4)
}

fun ContentKind.accent(): Color = when (this) {
    ContentKind.TV -> Color(0xFF3D6BFF)
    ContentKind.RADIO -> Color(0xFFF2557E)
    ContentKind.WEBCAM -> Color(0xFF12A594)
}

fun ContentKind.soft(): Color = when (this) {
    ContentKind.TV -> Color(0xFFE6EDFF)
    ContentKind.RADIO -> Color(0xFFFFE8EE)
    ContentKind.WEBCAM -> Color(0xFFDDF6F2)
}

fun palette(dark: Boolean, kind: ContentKind) = WtPalette(
    dark = dark,
    bg = if (dark) DarkBg else LightBg,
    surface = if (dark) DarkSurface else LightSurface,
    surface2 = if (dark) DarkSurface2 else LightSurface2,
    heading = if (dark) DarkHeading else LightHeading,
    text = if (dark) DarkText else LightText,
    muted = if (dark) DarkMuted else LightMuted,
    line = if (dark) DarkLine else LightLine,
    accent = kind.accent(),
    soft = kind.soft(),
    accentFrom = kind.accentFrom(),
    fav = Color(0xFFF2557E),
    live = Color(0xFFE0365F),
    liveSoft = Color(0xFFFFE3E8),
    warnBg = Color(0xFFFFF1D6),
    warnText = Color(0xFF9A6410),
    regionText = Color(0xFFB07410),
)

private fun outfit(weight: FontWeight) = Font(
    resId = R.font.outfit,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Outfit = FontFamily(
    outfit(FontWeight.Normal),
    outfit(FontWeight.Medium),
    outfit(FontWeight.SemiBold),
    outfit(FontWeight.Bold),
)

val LocalAppFont = staticCompositionLocalOf { Outfit }

private fun type(television: Boolean): Typography {
    val scale = if (television) 1.12f else 1f
    fun size(value: Float) = (value * scale).sp
    return Typography(
        headlineMedium = TextStyle(
            fontFamily = Outfit,
            fontWeight = FontWeight.Bold,
            fontSize = size(30f),
            lineHeight = size(36f),
            letterSpacing = (-0.02).em,
        ),
        titleLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = size(18f), lineHeight = size(24f)),
        titleMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = size(16f), lineHeight = size(22f)),
        bodyLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = size(16f), lineHeight = size(22f)),
        bodyMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = size(14f), lineHeight = size(20f)),
        labelLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = size(13f), lineHeight = size(18f)),
        labelMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = size(12f), lineHeight = size(16f)),
        labelSmall = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = size(12f), lineHeight = size(16f)),
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
    kind: ContentKind,
    content: @Composable () -> Unit,
) {
    val dark = when (choice) {
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = palette(dark, kind)
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            primaryContainer = colors.soft,
            onPrimaryContainer = colors.accent,
            background = colors.bg,
            onBackground = colors.heading,
            surface = colors.surface,
            onSurface = colors.heading,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.muted,
            outline = colors.line,
            error = colors.live,
            secondary = colors.regionText,
            secondaryContainer = colors.warnBg,
            onSecondaryContainer = colors.warnText,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            primaryContainer = colors.soft,
            onPrimaryContainer = colors.accent,
            background = colors.bg,
            onBackground = colors.heading,
            surface = colors.surface,
            onSurface = colors.heading,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.muted,
            outline = colors.line,
            error = colors.live,
            secondary = colors.regionText,
            secondaryContainer = colors.warnBg,
            onSecondaryContainer = colors.warnText,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.bg.toArgb()
            window.navigationBarColor = colors.bg.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    val appFont = if (AppLanguages.usesOutfit(LocaleApplier.read(LocalContext.current))) {
        Outfit
    } else {
        FontFamily.Default
    }
    CompositionLocalProvider(LocalWt provides colors, LocalAppFont provides appFont) {
        MaterialTheme(
            colorScheme = scheme,
            typography = type(television),
            content = content,
        )
    }
}
