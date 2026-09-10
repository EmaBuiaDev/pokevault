package com.emabuia.pokevault.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Token di colore dell'app.
 *
 * Prima i colori erano costanti top-level (DarkBackground, TextWhite, ...)
 * usate direttamente in ogni schermo: MaterialTheme.colorScheme non compariva
 * mai in tutto il progetto, quindi il tema esisteva ma non governava nulla e
 * non c'era modo di avere un tema chiaro.
 *
 * Ora la palette e' un valore fornito via CompositionLocal, e si legge da
 * [AppColors]. I nomi sono semantici (background, card, textPrimary) invece che
 * descrittivi del colore, perche' cambiano valore fra tema chiaro e scuro.
 */
@Immutable
data class PokeVaultColors(
    // Superfici
    val background: Color,
    val surface: Color,
    val card: Color,
    val searchBar: Color,

    // Testo
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,

    // Accenti, usati sia come sfondo di tile (con testo bianco sopra) sia come
    // colore di accento del testo: i valori chiari sono scelti per reggere
    // entrambi gli usi con contrasto sufficiente.
    val accentBlue: Color,
    val accentPurple: Color,
    val accentGreen: Color,
    val accentYellow: Color,
    val accentRed: Color,
    val accentLavender: Color,
    val accentOrange: Color,
    val accentGold: Color,

    /** Testo da usare SOPRA un accento pieno. */
    val onAccent: Color,

    val isLight: Boolean
)

val DarkPokeVaultColors = PokeVaultColors(
    background = Color(0xFF1A1A2E),
    surface = Color(0xFF232342),
    card = Color(0xFF2A2A4A),
    searchBar = Color(0xFF2A2A4A),

    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB0B0C0),
    textMuted = Color(0xFF8B8BA8),

    accentBlue = Color(0xFF4A6CF7),
    accentPurple = Color(0xFF8B5CF6),
    accentGreen = Color(0xFF22C55E),
    accentYellow = Color(0xFFEAB308),
    accentRed = Color(0xFFEF4444),
    accentLavender = Color(0xFF9B8EC4),
    accentOrange = Color(0xFFE87A35),
    accentGold = Color(0xFFFFD700),

    onAccent = Color(0xFFFFFFFF),
    isLight = false
)

/**
 * Gli accenti chiari sono scuriti rispetto ai corrispondenti scuri: gli stessi
 * valori (per esempio il verde 0xFF22C55E) su fondo chiaro non raggiungono il
 * contrasto minimo quando sono usati come colore del testo.
 */
val LightPokeVaultColors = PokeVaultColors(
    background = Color(0xFFF4F4F9),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    searchBar = Color(0xFFECECF4),

    textPrimary = Color(0xFF16162A),
    textSecondary = Color(0xFF4A4A66),
    textMuted = Color(0xFF5E5E7A),

    accentBlue = Color(0xFF3A57D6),
    accentPurple = Color(0xFF7141E0),
    accentGreen = Color(0xFF15803D),
    accentYellow = Color(0xFFA16207),
    accentRed = Color(0xFFDC2626),
    accentLavender = Color(0xFF6F62A0),
    accentOrange = Color(0xFFC2560F),
    accentGold = Color(0xFFB7950B),

    onAccent = Color(0xFFFFFFFF),
    isLight = true
)
