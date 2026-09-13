package com.emabuia.pokevault.data.italian

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Il confronto fra il totale digitato in una ricerca per ID e quelli noti per
 * un'espansione.
 *
 * E' il punto in cui "066/217" sceglie la sua espansione: sbagliarlo vuol dire
 * restituire la carta numero 66 di un'espansione qualsiasi, che e' esattamente
 * il bug che questa classe e' nata per chiudere.
 */
class ItalianPrintedTotalsTest {

    @Test
    fun `un totale che combacia con uno di quelli noti vince`() {
        assertEquals(
            ItalianPrintedTotals.SCORE_EXACT,
            ItalianPrintedTotals.matchScore(listOf(245, 217), 217)
        )
    }

    @Test
    fun `basta che combaci uno solo, anche se non e il preferito`() {
        // Il caso reale: il totale del set mergiato e' gonfiato dalle segrete
        // (245) e il manifest ha quello stampato (217). Prima si guardava solo
        // il primo, e l'espansione giusta non si trovava mai.
        assertEquals(
            ItalianPrintedTotals.SCORE_EXACT,
            ItalianPrintedTotals.matchScore(setOf(245, 217, 191), 217)
        )
    }

    @Test
    fun `a meno di due carte di scarto vale come quasi`() {
        assertEquals(ItalianPrintedTotals.SCORE_NEAR, ItalianPrintedTotals.matchScore(listOf(219), 217))
        assertEquals(ItalianPrintedTotals.SCORE_NEAR, ItalianPrintedTotals.matchScore(listOf(215), 217))
    }

    @Test
    fun `oltre lo scarto non vale niente`() {
        assertEquals(ItalianPrintedTotals.SCORE_NONE, ItalianPrintedTotals.matchScore(listOf(191), 217))
    }

    @Test
    fun `senza totali noti non si discrimina, invece di escludere`() {
        assertEquals(ItalianPrintedTotals.SCORE_NONE, ItalianPrintedTotals.matchScore(emptyList(), 217))
        assertEquals(ItalianPrintedTotals.SCORE_NONE, ItalianPrintedTotals.matchScore(listOf(0), 217))
    }

    @Test
    fun `senza totale digitato non si discrimina`() {
        assertEquals(ItalianPrintedTotals.SCORE_NONE, ItalianPrintedTotals.matchScore(listOf(217), null))
        assertEquals(ItalianPrintedTotals.SCORE_NONE, ItalianPrintedTotals.matchScore(listOf(217), 0))
    }

    @Test
    fun `si tiene solo il gruppo col punteggio migliore`() {
        val scored = listOf(
            "giusta" to ItalianPrintedTotals.SCORE_EXACT,
            "quasi" to ItalianPrintedTotals.SCORE_NEAR,
            "altra" to ItalianPrintedTotals.SCORE_NONE
        )
        assertEquals(listOf("giusta"), ItalianPrintedTotals.keepBestMatches(scored))
    }

    @Test
    fun `col solo quasi si tiene il quasi`() {
        val scored = listOf(
            "quasi" to ItalianPrintedTotals.SCORE_NEAR,
            "altra" to ItalianPrintedTotals.SCORE_NONE
        )
        assertEquals(listOf("quasi"), ItalianPrintedTotals.keepBestMatches(scored))
    }

    @Test
    fun `se nessuno combacia si tiene tutto, invece di svuotare lo schermo`() {
        val scored = listOf(
            "una" to ItalianPrintedTotals.SCORE_NONE,
            "altra" to ItalianPrintedTotals.SCORE_NONE
        )
        assertEquals(listOf("una", "altra"), ItalianPrintedTotals.keepBestMatches(scored))
    }

    @Test
    fun `da una lista vuota non esce niente`() {
        assertEquals(emptyList<String>(), ItalianPrintedTotals.keepBestMatches(emptyList<Pair<String, Int>>()))
    }
}
