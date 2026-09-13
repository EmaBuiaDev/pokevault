package com.emabuia.pokevault.data.italian

/**
 * Il confronto fra il totale scritto in una ricerca -- il 217 di "066/217" --
 * e i totali che conosciamo per quell'espansione.
 *
 * Di totali ne abbiamo piu' d'uno e nessuno e' affidabile da solo: l'override
 * manuale esiste proprio dove il dato ereditato sbaglia, il totale del set
 * mergiato e' quello vero solo quando l'aggancio al set inglese e' riuscito
 * (altrimenti ripiega sul conteggio delle carte, che include le segrete e
 * sovrastima), e il conteggio del manifest sovrastima sempre per lo stesso
 * motivo.
 *
 * Sceglierne uno solo per priorita' -- com'era prima -- vuol dire che se il
 * preferito e' quello gonfiato la ricerca per ID non trova la sua espansione e
 * ricade su tutte le altre: e' il motivo per cui "066/217" restituiva la carta
 * numero 66 di un'espansione qualsiasi. Qui si confrontano tutti, e basta che
 * uno combaci.
 */
object ItalianPrintedTotals {

    /** Il totale scritto combacia esattamente con uno di quelli noti. */
    const val SCORE_EXACT = 100

    /** Combacia a meno di due carte: una cifra letta o ricordata male. */
    const val SCORE_NEAR = 50

    /** Nessuna corrispondenza, o nessun totale da confrontare. */
    const val SCORE_NONE = 0

    private const val NEAR_TOLERANCE = 2

    /**
     * Quanto il totale scritto somiglia a quelli noti dell'espansione.
     *
     * Torna [SCORE_NONE] anche quando non c'e' niente da confrontare: chi
     * chiama deve trattare "non lo so" come "non discrimina", mai come
     * "non e' questa".
     */
    fun matchScore(knownTotals: Collection<Int>, typedTotal: Int?): Int {
        if (typedTotal == null || typedTotal <= 0) return SCORE_NONE
        val totals = knownTotals.filter { it > 0 }
        if (totals.isEmpty()) return SCORE_NONE

        if (totals.any { it == typedTotal }) return SCORE_EXACT
        if (totals.any { kotlin.math.abs(it - typedTotal) <= NEAR_TOLERANCE }) return SCORE_NEAR
        return SCORE_NONE
    }

    /**
     * Tiene solo gli elementi del gruppo che ha il punteggio migliore.
     *
     * Se il totale scritto individua delle espansioni, le altre non servono:
     * chi cerca "066/217" vuole quella carta, non le altre cento carte numero
     * 66 del catalogo. Ma se non individua niente -- totale sbagliato, promo,
     * espansione che non conosciamo -- si tiene tutto: meglio una lista da
     * scorrere che uno schermo vuoto.
     */
    fun <T> keepBestMatches(scored: List<Pair<T, Int>>): List<T> {
        val best = scored.maxOfOrNull { it.second } ?: return emptyList()
        if (best <= SCORE_NONE) return scored.map { it.first }
        return scored.filter { it.second == best }.map { it.first }
    }
}
