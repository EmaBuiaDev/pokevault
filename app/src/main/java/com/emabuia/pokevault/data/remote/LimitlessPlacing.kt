package com.emabuia.pokevault.data.remote

/**
 * I piazzamenti di Limitless, con quelli che mancano messi al loro posto.
 *
 * Un giocatore ritirato arriva con `"placing": null`, che nel DTO diventa 0.
 * Ordinare per piazzamento crescente metteva quegli 0 in testa: i "top 32"
 * di un torneo erano in buona parte ritirati, e il miglior piazzamento di
 * ogni archetipo risultava 0 -- a schermo "Top 0" e "#0" su tutti, con meta
 * share e win rate calcolati su giocatori sbagliati.
 */
internal object LimitlessPlacing {

    /** Un piazzamento vero: 1, 2, 3... Lo 0 e i negativi vogliono dire "non c'e'". */
    fun isKnown(placing: Int): Boolean = placing > 0

    /** Prima i piazzamenti veri in ordine crescente, poi quelli mancanti. */
    fun <T> ranked(items: List<T>, placing: (T) -> Int): List<T> =
        items.sortedWith(
            compareBy<T> { if (isKnown(placing(it))) 0 else 1 }
                .thenBy { placing(it) }
        )

    /** Il miglior piazzamento vero, o 0 se nessuno ne ha uno. */
    fun best(placings: List<Int>): Int = placings.filter(::isKnown).minOrNull() ?: 0
}
