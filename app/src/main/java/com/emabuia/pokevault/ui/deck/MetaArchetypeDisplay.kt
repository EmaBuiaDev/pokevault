package com.emabuia.pokevault.ui.deck

import com.emabuia.pokevault.data.model.MetaArchetype

/**
 * Come si leggono i numeri della classifica degli archetipi.
 *
 * Funzioni pure, fuori dai composable, perche' le soglie sono scelte che vanno
 * tenute ferme da test: fino alla 3.1.8 il win rate era verde sopra il 65% e
 * giallo sopra il 50%, ma su deck arrivati nei top 32 quasi tutti superano il
 * 50%, e i colori non distinguevano niente.
 */

/**
 * Sotto queste partite il win rate non si mostra: con un deck solo e sei
 * partite un 5-1 diventa 83%, e in classifica sembra il mazzo migliore.
 */
internal const val MIN_GAMES_FOR_WIN_RATE = 20

/** Punti di quota oltre i quali la freccia del trend sale o scende. */
internal const val TREND_THRESHOLD = 2.0

internal enum class WinRateLevel { FEW_DATA, HIGH, AVERAGE, LOW }

/** Intorno al 50% e' nella media; il colore dice solo quando se ne allontana. */
internal fun MetaArchetype.winRateLevel(): WinRateLevel = when {
    games < MIN_GAMES_FOR_WIN_RATE -> WinRateLevel.FEW_DATA
    avgWinrate >= 0.55 -> WinRateLevel.HIGH
    avgWinrate <= 0.45 -> WinRateLevel.LOW
    else -> WinRateLevel.AVERAGE
}

internal enum class TrendDirection { UP, DOWN, FLAT }

/** Null quando il trend non si puo' calcolare (un torneo solo, o una meta' vuota). */
internal fun MetaArchetype.trendDirection(): TrendDirection? = trend?.let {
    when {
        it >= TREND_THRESHOLD -> TrendDirection.UP
        it <= -TREND_THRESHOLD -> TrendDirection.DOWN
        else -> TrendDirection.FLAT
    }
}

/**
 * Le quote dei primi [count] archetipi per presenza, piu' "altri" con il
 * resto: la barra in cima alla classifica.
 */
internal fun List<MetaArchetype>.topShares(count: Int = 8): Pair<List<MetaArchetype>, Double> {
    val top = sortedByDescending { it.metaShare }.take(count)
    val others = (100.0 - top.sumOf { it.metaShare }).coerceAtLeast(0.0)
    return top to others
}

/** Ordinamento per win rate: chi ha pochi dati va in fondo, non in cima. */
internal fun List<MetaArchetype>.sortedByWinRate(): List<MetaArchetype> =
    sortedWith(
        compareBy<MetaArchetype> { if (it.winRateLevel() == WinRateLevel.FEW_DATA) 1 else 0 }
            .thenByDescending { it.avgWinrate }
    )
