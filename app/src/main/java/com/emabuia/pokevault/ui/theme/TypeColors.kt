package com.emabuia.pokevault.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Colori dei tipi Pokemon, in un punto solo.
 *
 * Gli stessi valori erano duplicati in almeno tre posti: getTypeColorForTcg in
 * CardDetailBottomSheet, getThemeColors in AlbumListScreen e vari Color(0x...)
 * sparsi. Divergevano gia' fra loro.
 *
 * I tipi sono colori di marca: il fuoco deve restare rosso in entrambi i temi.
 * Cambia solo la luminosita', perche' su fondo chiaro le versioni sature non
 * reggono il contrasto quando il colore e' usato per il testo invece che come
 * sfondo pieno.
 */
private val DarkTypeColors = mapOf(
    "fire" to Color(0xFFEF4444),
    "water" to Color(0xFF3B82F6),
    "grass" to Color(0xFF22C55E),
    "lightning" to Color(0xFFEAB308),
    "psychic" to Color(0xFF8B5CF6),
    "fighting" to Color(0xFFF97316),
    "darkness" to Color(0xFF6366F1),
    "metal" to Color(0xFF6B7280),
    "dragon" to Color(0xFF7C3AED),
    "fairy" to Color(0xFFEC4899),
    "colorless" to Color(0xFF9CA3AF)
)

private val LightTypeColors = mapOf(
    "fire" to Color(0xFFC62828),
    "water" to Color(0xFF1D4ED8),
    "grass" to Color(0xFF15803D),
    "lightning" to Color(0xFFA16207),
    "psychic" to Color(0xFF6D28D9),
    "fighting" to Color(0xFFC2410C),
    "darkness" to Color(0xFF4338CA),
    "metal" to Color(0xFF4B5563),
    "dragon" to Color(0xFF5B21B6),
    "fairy" to Color(0xFFBE185D),
    "colorless" to Color(0xFF6B7280)
)

private val DarkTypeFallback = Color(0xFF6B7280)
private val LightTypeFallback = Color(0xFF4B5563)

object TypeColors {

    /** Colore del tipo, adattato al tema corrente. */
    @Composable
    @ReadOnlyComposable
    fun of(type: String?): Color {
        val key = type?.lowercase()?.trim().orEmpty()
        return if (AppColors.isLight) {
            LightTypeColors[key] ?: LightTypeFallback
        } else {
            DarkTypeColors[key] ?: DarkTypeFallback
        }
    }

    /**
     * Coppia di colori per i gradienti a tema degli album.
     * La seconda tinta e' la variante "compagna" storicamente usata.
     */
    @Composable
    @ReadOnlyComposable
    fun gradientFor(theme: String): List<Color> = when (theme) {
        "fire" -> listOf(of("fire"), of("fighting"))
        "water" -> listOf(of("water"), Color(0xFF06B6D4))
        "grass" -> listOf(of("grass"), Color(0xFF84CC16))
        "electric" -> listOf(of("lightning"), Color(0xFFFBBF24))
        "dark" -> listOf(Color(0xFF6B21A8), Color(0xFF4C1D95))
        "psychic" -> listOf(Color(0xFFD946EF), of("psychic"))
        else -> listOf(AppColors.orange, AppColors.orange.copy(alpha = 0.7f))
    }
}
