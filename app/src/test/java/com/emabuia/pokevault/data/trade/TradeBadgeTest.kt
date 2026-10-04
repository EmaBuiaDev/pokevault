package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.data.trade.dto.TradeProposal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Il numero sul tasto TradeRadar della barra: deve essere lo stesso della tab
 * Proposte, quindi la regola e' una sola (TradeBadge.countOf).
 */
class TradeBadgeTest {

    private fun proposal(id: String, status: String, myTurn: Boolean = false, actionNeeded: Boolean = false) =
        TradeProposal(id = id, status = status, myTurn = myTurn, actionNeeded = actionNeeded)

    @Test
    fun `conta le proposte in cui tocca a me e gli scambi chiusi senza riepilogo`() {
        val proposals = listOf(
            proposal("a", "open", myTurn = true),              // tocca a me
            proposal("b", "open"),                              // tocca all'altro
            proposal("c", "scheduled", actionNeeded = true),    // "Scambio fatto" o voto
            proposal("d", "done"),                              // chiuso, riepilogo da fare
            proposal("e", "done"),                              // chiuso, riepilogo gia' fatto
            proposal("f", "declined"),
        )
        assertEquals(3, TradeBadge.countOf(proposals, appliedClosings = setOf("e")))
    }

    @Test
    fun `nessuna proposta, nessun numero`() {
        assertEquals(0, TradeBadge.countOf(emptyList(), emptySet()))
    }

    @Test
    fun `update non va mai sotto zero`() {
        TradeBadge.update(-3)
        assertEquals(0, TradeBadge.pending)
        TradeBadge.update(4)
        assertEquals(4, TradeBadge.pending)
        TradeBadge.update(0)
    }
}
