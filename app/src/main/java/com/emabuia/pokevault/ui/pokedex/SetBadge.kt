package com.emabuia.pokevault.ui.pokedex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.emabuia.pokevault.data.remote.TcgSet

/**
 * Simbolo e sigla dell'espansione di una carta, per l'angolo della tessera.
 *
 * Il simbolo viene da /sets/{SETCODE}/symbol (R2, caricato da
 * backfill-set-symbols-tcgdex.mjs), la sigla da /v1/expansions.
 */
@Immutable
data class SetBadge(
    val symbolUrl: String?,
    val abbreviation: String?,
    /**
     * Da Scarlatto e Violetto il simbolo E' la sigla in un riquadro ("PAF"):
     * scriverla accanto la ripeterebbe. Prima e' un disegnino, e la sigla
     * aiuta a riconoscerlo.
     */
    val symbolHasLetters: Boolean
) {
    /**
     * Se scrivere la sigla accanto al simbolo. Sempre quando il simbolo non
     * c'e' o non si e' caricato: altrimenti in quel caso il badge sparirebbe.
     */
    fun showsAbbreviation(symbolLoaded: Boolean): Boolean = when {
        abbreviation == null -> false
        symbolUrl == null || !symbolLoaded -> true
        else -> !symbolHasLetters
    }

    companion object {
        /** Primo set col simbolo-sigla: Scarlatto e Violetto, marzo 2023. */
        const val LETTERED_SYMBOLS_SINCE = "2023-03-01"

        private val SET_LOGO_URL = Regex("""^(.*/sets/[^/?]+)/image(?:\?.*)?$""")

        /**
         * Il simbolo sta accanto al logo: stessa cartella, rotta /symbol. Null
         * per i set che non passano dal nostro Worker.
         */
        fun symbolUrlFor(set: TcgSet): String? =
            SET_LOGO_URL.find(set.images.logo.trim())?.groupValues?.get(1)?.let { "$it/symbol" }

        fun forSet(set: TcgSet): SetBadge? {
            val symbolUrl = symbolUrlFor(set)
            val abbreviation = set.abbreviation.trim().ifEmpty { null }
            if (symbolUrl == null && abbreviation == null) return null
            val releaseDate = set.releaseDate.trim()
            return SetBadge(
                symbolUrl = symbolUrl,
                abbreviation = abbreviation,
                symbolHasLetters = releaseDate.isNotEmpty() && releaseDate >= LETTERED_SYMBOLS_SINCE
            )
        }

        /** Per id dell'espansione, come `TcgCard.set.id`. */
        fun byId(sets: List<TcgSet>): Map<String, SetBadge> =
            sets.mapNotNull { set -> forSet(set)?.let { set.id to it } }.toMap()
    }
}

/**
 * Il badge: simbolo e, se serve, sigla su una pillola chiara. Chiara in tutti
 * e due i temi apposta: i simboli sono neri, e su una carta scura o sul fondo
 * del tema scuro sparirebbero.
 */
@Composable
fun SetBadgeChip(badge: SetBadge, modifier: Modifier = Modifier) {
    var symbolLoaded by remember(badge.symbolUrl) { mutableStateOf(badge.symbolUrl != null) }
    val showText = badge.showsAbbreviation(symbolLoaded)
    if (!showText && !symbolLoaded) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        if (badge.symbolUrl != null && symbolLoaded) {
            AsyncImage(
                model = badge.symbolUrl,
                contentDescription = badge.abbreviation,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(11.dp).widthIn(max = 30.dp),
                onError = { symbolLoaded = false }
            )
        }
        if (showText) {
            Text(
                text = badge.abbreviation.orEmpty(),
                color = Color(0xFF111111),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
