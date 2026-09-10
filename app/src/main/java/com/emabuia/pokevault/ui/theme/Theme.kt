package com.emabuia.pokevault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette corrente. Si legge tramite [AppColors].
 *
 * staticCompositionLocalOf perche' cambia solo al passaggio fra tema chiaro e
 * scuro: quando cambia va ricomposto tutto, ed e' esattamente cio' che serve.
 */
val LocalPokeVaultColors = staticCompositionLocalOf { DarkPokeVaultColors }

/**
 * Punto di accesso ai token di colore dell'app.
 *
 * Da usare al posto delle vecchie costanti top-level: quelle erano valori
 * fissi, quindi il tema chiaro non poteva esistere.
 */
object AppColors {
    val background: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.background
    val surface: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.surface
    val card: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.card
    val searchBar: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.searchBar

    val textPrimary: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.textPrimary
    val textSecondary: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.textSecondary
    val textMuted: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.textMuted

    val blue: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.accentBlue
    val purple: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.accentPurple
    val green: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.accentGreen
    val yellow: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.accentYellow
    val red: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.accentRed
    val lavender: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.accentLavender
    val orange: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.accentOrange
    val gold: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.accentGold

    val onAccent: Color
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.onAccent

    val isLight: Boolean
        @Composable @ReadOnlyComposable get() = LocalPokeVaultColors.current.isLight
}

/**
 * Schema Material3 derivato dagli stessi token.
 *
 * Prima Theme.kt valorizzava 11 slot e lasciava tutto il resto ai default, per
 * cui i componenti M3 non stilizzati a mano (OutlinedButton, DropdownMenu,
 * TabRow) stonavano visibilmente con il resto dell'interfaccia. Qui gli slot
 * sono riempiti tutti a partire dalla palette.
 */
private fun PokeVaultColors.toMaterialScheme() = if (isLight) {
    lightColorScheme(
        primary = accentBlue,
        onPrimary = onAccent,
        primaryContainer = accentBlue.copy(alpha = 0.14f),
        onPrimaryContainer = textPrimary,
        secondary = accentPurple,
        onSecondary = onAccent,
        secondaryContainer = accentPurple.copy(alpha = 0.14f),
        onSecondaryContainer = textPrimary,
        tertiary = accentGreen,
        onTertiary = onAccent,
        tertiaryContainer = accentGreen.copy(alpha = 0.14f),
        onTertiaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = card,
        onSurfaceVariant = textSecondary,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
        surfaceContainerLow = surface,
        surfaceContainerLowest = surface,
        error = accentRed,
        onError = onAccent,
        errorContainer = accentRed.copy(alpha = 0.14f),
        onErrorContainer = textPrimary,
        outline = textMuted,
        outlineVariant = textMuted.copy(alpha = 0.4f),
        scrim = Color.Black.copy(alpha = 0.5f)
    )
} else {
    darkColorScheme(
        primary = accentBlue,
        onPrimary = onAccent,
        primaryContainer = accentBlue.copy(alpha = 0.24f),
        onPrimaryContainer = textPrimary,
        secondary = accentPurple,
        onSecondary = onAccent,
        secondaryContainer = accentPurple.copy(alpha = 0.24f),
        onSecondaryContainer = textPrimary,
        tertiary = accentGreen,
        onTertiary = onAccent,
        tertiaryContainer = accentGreen.copy(alpha = 0.24f),
        onTertiaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = card,
        onSurfaceVariant = textSecondary,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background,
        error = accentRed,
        onError = onAccent,
        errorContainer = accentRed.copy(alpha = 0.24f),
        onErrorContainer = textPrimary,
        outline = textMuted,
        outlineVariant = textMuted.copy(alpha = 0.4f),
        scrim = Color.Black.copy(alpha = 0.6f)
    )
}

@Composable
fun PokeVaultTheme(
    darkTheme: Boolean = when (ThemePreference.current) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkPokeVaultColors else LightPokeVaultColors

    CompositionLocalProvider(LocalPokeVaultColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = Typography,
            content = content
        )
    }
}
