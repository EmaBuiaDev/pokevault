package com.emabuia.pokevault.ui.graded

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.GradeTier
import com.emabuia.pokevault.util.GradedLab

/**
 * Come si vede un voto, ovunque nell'app.
 *
 * Stavano dentro GradedCardsScreen perche' la slab la disegnava solo lei. Da
 * quando anche il dettaglio di una carta mostra il blocco dell'ente — stessa
 * etichetta, stesso colore, stesso modo di scrivere il voto — le due schermate
 * devono pescare dallo stesso posto, o la carta toccata nella griglia e quella
 * che si apre sono due oggetti diversi.
 *
 * I conti restano in [GradedLab], che e' testato: qui c'e' solo il colore.
 */

/**
 * Il colore di una fascia.
 *
 * Oro solo per il 10, poi verde, blu, arancio e rosso: e' la stessa scala che
 * l'app usa per il completamento di un set, quindi si legge senza legenda.
 */
@Composable
internal fun tierColor(tier: GradeTier): Color = when (tier) {
    GradeTier.GEM -> AppColors.gold
    GradeTier.MINT -> AppColors.green
    GradeTier.NEAR_MINT -> AppColors.blue
    GradeTier.EXCELLENT -> AppColors.orange
    GradeTier.PLAYED -> AppColors.red
    GradeTier.UNGRADED -> AppColors.textMuted
}

internal fun tierLabel(tier: GradeTier): String = when (tier) {
    GradeTier.GEM -> AppLocale.gradedTierGem
    GradeTier.MINT -> AppLocale.gradedTierMint
    GradeTier.NEAR_MINT -> AppLocale.gradedTierNearMint
    GradeTier.EXCELLENT -> AppLocale.gradedTierExcellent
    GradeTier.PLAYED -> AppLocale.gradedTierPlayed
    GradeTier.UNGRADED -> AppLocale.gradedTierUngraded
}

internal fun companyLabel(key: String): String =
    if (key == GradedLab.UNKNOWN_COMPANY) AppLocale.gradedNoCompany else key

/**
 * Testo leggibile sopra un accento pieno.
 *
 * Deciso dalla luminanza e non scritto a mano: l'oro del tema scuro (0xFFFFD700)
 * vuole testo nero, quello del tema chiaro (0xFFB7950B) lo vuole bianco, e il
 * nero fisso di prima spariva su meta' degli accenti.
 */
internal fun onAccentColor(accent: Color): Color =
    if (accent.luminance() > 0.45f) Color.Black else Color.White
